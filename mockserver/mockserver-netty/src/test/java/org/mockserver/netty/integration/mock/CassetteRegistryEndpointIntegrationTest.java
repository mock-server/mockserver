package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * End-to-end tests for the {@code GET/PUT/DELETE /mockserver/cassettes} control-plane endpoints
 * (and their bare {@code /cassettes} aliases), driving a real MockServer over a real socket.
 * <p>
 * The endpoints expose the process-wide {@link org.mockserver.mock.CassetteRegistry} — lightweight
 * metadata about recorded/loaded cassette fixture files — so the dashboard can surface them across
 * page reloads and browsers. These tests pin the documented contract:
 * <ul>
 *     <li>PUT registers a cassette and echoes it back as {@code 201 Created},</li>
 *     <li>GET lists cassettes most-recently-used first with the documented body shape,</li>
 *     <li>re-registering (touching) a cassette moves it to the front of the MRU order,</li>
 *     <li>DELETE removes a cassette so a subsequent GET no longer lists it,</li>
 *     <li>a missing body (PUT) or a missing path (PUT and DELETE) is rejected as {@code 400},</li>
 *     <li>the bare {@code /cassettes} alias behaves identically to the prefixed path,</li>
 *     <li>responses carry CORS headers so the dashboard can call them cross-origin,</li>
 *     <li>server reset empties the registry,</li>
 *     <li>and every verb is refused when control-plane authentication is required.</li>
 * </ul>
 */
public class CassetteRegistryEndpointIntegrationTest {

    private static MockServer mockServer;
    private static NettyHttpClient httpClient;
    private static EventLoopGroup clientEventLoopGroup;
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    @BeforeClass
    public static void startServerAndClient() {
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(CassetteRegistryEndpointIntegrationTest.class.getSimpleName() + "-eventLoop"));
        httpClient = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false);
        mockServer = new MockServer();
    }

    @AfterClass
    public static void stopServerAndClient() {
        // belt-and-braces: never leak the global control-plane auth toggle to sibling suites
        ConfigurationProperties.controlPlaneJWTAuthenticationRequired(false);
        stopQuietly(mockServer);
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Before
    public void resetBefore() throws Exception {
        // ensure auth is off before the reset call itself - the toggle is process-wide, so an
        // upstream suite that failed mid-test could otherwise leave it on and turn every test in
        // this class into an unattributable 401
        ConfigurationProperties.controlPlaneJWTAuthenticationRequired(false);
        reset();
    }

    @After
    public void resetAfter() throws Exception {
        // ensure auth is off (a failed auth test could otherwise leave it on) before resetting
        ConfigurationProperties.controlPlaneJWTAuthenticationRequired(false);
        reset();
    }

    private void reset() throws Exception {
        HttpResponse response = send("PUT", "/mockserver/reset", null);
        // fail here rather than leaving a broken reset to surface later as an unrelated assertion
        assertThat("reset must succeed so each test starts from an empty registry", response.getStatusCode(), is(200));
    }

    private HttpResponse send(String method, String path, String body) throws Exception {
        return send(method, path, body, null, null);
    }

    private HttpResponse send(String method, String path, String body, String headerName, String headerValue) throws Exception {
        org.mockserver.model.HttpRequest httpRequest = request()
            .withMethod(method)
            .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
            .withPath(path);
        if (headerName != null) {
            httpRequest = httpRequest.withHeader(headerName, headerValue);
        }
        if (body != null) {
            httpRequest = httpRequest.withBody(body);
        }
        return httpClient.sendRequest(httpRequest).get(15, TimeUnit.SECONDS);
    }

    private static JsonNode json(HttpResponse response) throws Exception {
        return OBJECT_MAPPER.readTree(response.getBodyAsString());
    }

    /** Distinct wall-clock milliseconds between two registrations so MRU ordering is deterministic. */
    private static void tick() throws InterruptedException {
        Thread.sleep(10);
    }

    // ------------------------------------------------------------------
    // PUT registration contract
    // ------------------------------------------------------------------

    @Test
    public void shouldRegisterCassetteAndEchoStoredEntry() throws Exception {
        // when
        HttpResponse response = send("PUT", "/mockserver/cassettes",
            "{\"path\":\"/tmp/fixtures/checkout.json\",\"filename\":\"checkout.json\",\"expectationCount\":3,\"origin\":\"recorded\"}");

        // then - a registration is reported as 201 Created with the stored entry echoed back
        assertThat(response.getStatusCode(), is(201));
        assertThat(response.getFirstHeader("content-type"), containsString("application/json"));
        JsonNode body = json(response);
        assertThat(body.get("path").asText(), is("/tmp/fixtures/checkout.json"));
        assertThat(body.get("filename").asText(), is("checkout.json"));
        assertThat(body.get("expectationCount").asInt(), is(3));
        assertThat(body.get("origin").asText(), is("recorded"));
        assertThat("a registered cassette must carry a lastUsed timestamp", body.get("lastUsed").asLong() > 0L, is(true));
    }

    @Test
    public void shouldDeriveFilenameFromPathAndDefaultOriginWhenOmitted() throws Exception {
        // when - only the required path is supplied
        HttpResponse response = send("PUT", "/mockserver/cassettes", "{\"path\":\"/data/recordings/login.json\"}");

        // then - filename is derived from the path and origin defaults to "loaded"
        assertThat(response.getStatusCode(), is(201));
        JsonNode body = json(response);
        assertThat(body.get("filename").asText(), is("login.json"));
        assertThat(body.get("origin").asText(), is("loaded"));
    }

    @Test
    public void shouldRejectPutWithoutPath() throws Exception {
        // when
        HttpResponse response = send("PUT", "/mockserver/cassettes", "{\"filename\":\"orphan.json\"}");

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(json(response).get("error").asText(), containsString("'path' field is required"));
    }

    @Test
    public void shouldRejectPutWithoutBody() throws Exception {
        // when - no request body at all, rather than a body missing the path field
        HttpResponse response = send("PUT", "/mockserver/cassettes", null);

        // then - the distinct "missing body" diagnostic, not the "missing path field" one
        assertThat(response.getStatusCode(), is(400));
        assertThat(json(response).get("error").asText(), containsString("request body is required with a 'path' field"));

        // then - nothing was registered
        assertThat(json(send("GET", "/mockserver/cassettes", null)).get("cassettes").size(), is(0));
    }

    // ------------------------------------------------------------------
    // GET list + MRU ordering
    // ------------------------------------------------------------------

    @Test
    public void shouldListRegisteredCassettesMostRecentlyUsedFirst() throws Exception {
        // given - three cassettes registered in a known order with distinct timestamps
        assertThat(send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/alpha.json\"}").getStatusCode(), is(201));
        tick();
        assertThat(send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/beta.json\"}").getStatusCode(), is(201));
        tick();
        assertThat(send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/gamma.json\"}").getStatusCode(), is(201));

        // when
        HttpResponse listResponse = send("GET", "/mockserver/cassettes", null);

        // then - the documented body shape { "cassettes": [ ... ] }, most-recently-used first
        assertThat(listResponse.getStatusCode(), is(200));
        assertThat(listResponse.getFirstHeader("content-type"), containsString("application/json"));
        JsonNode cassettes = json(listResponse).get("cassettes");
        assertThat(cassettes.isArray(), is(true));
        assertThat(cassettes.size(), is(3));
        assertThat(cassettes.get(0).get("path").asText(), is("/c/gamma.json"));
        assertThat(cassettes.get(1).get("path").asText(), is("/c/beta.json"));
        assertThat(cassettes.get(2).get("path").asText(), is("/c/alpha.json"));

        // when - the oldest cassette is re-registered (touched)
        tick();
        assertThat(send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/alpha.json\"}").getStatusCode(), is(201));

        // then - touching updates lastUsed, moving it to the front of the MRU order (no duplicate)
        JsonNode reordered = json(send("GET", "/mockserver/cassettes", null)).get("cassettes");
        assertThat("re-registering must update in place, not duplicate", reordered.size(), is(3));
        assertThat(reordered.get(0).get("path").asText(), is("/c/alpha.json"));
        assertThat(reordered.get(1).get("path").asText(), is("/c/gamma.json"));
        assertThat(reordered.get(2).get("path").asText(), is("/c/beta.json"));
    }

    @Test
    public void shouldReturnEmptyListWhenNoCassettesRegistered() throws Exception {
        // when
        HttpResponse response = send("GET", "/mockserver/cassettes", null);

        // then
        assertThat(response.getStatusCode(), is(200));
        assertThat(json(response).get("cassettes").size(), is(0));
    }

    // ------------------------------------------------------------------
    // DELETE removal
    // ------------------------------------------------------------------

    @Test
    public void shouldRemoveCassetteViaBodyPathSoItIsNoLongerListed() throws Exception {
        // given
        send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/keep.json\"}");
        send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/drop.json\"}");
        assertThat(json(send("GET", "/mockserver/cassettes", null)).get("cassettes").size(), is(2));

        // when - deleting via the JSON body { "path": ... }
        HttpResponse deleteResponse = send("DELETE", "/mockserver/cassettes", "{\"path\":\"/c/drop.json\"}");

        // then - the endpoint reports the removal
        assertThat(deleteResponse.getStatusCode(), is(200));
        assertThat(json(deleteResponse).get("removed").asBoolean(), is(true));

        // then - a subsequent GET no longer lists the removed cassette but keeps the other
        JsonNode remaining = json(send("GET", "/mockserver/cassettes", null)).get("cassettes");
        assertThat(remaining.size(), is(1));
        assertThat(remaining.get(0).get("path").asText(), is("/c/keep.json"));
    }

    @Test
    public void shouldRemoveCassetteViaQueryParameter() throws Exception {
        // given
        send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/viaquery.json\"}");

        // when - deleting via the ?path= query parameter
        HttpResponse deleteResponse = send("DELETE", "/mockserver/cassettes?path=/c/viaquery.json", null);

        // then
        assertThat(deleteResponse.getStatusCode(), is(200));
        assertThat(json(deleteResponse).get("removed").asBoolean(), is(true));
        assertThat(json(send("GET", "/mockserver/cassettes", null)).get("cassettes").size(), is(0));
    }

    @Test
    public void shouldReportRemovedFalseForUnknownCassette() throws Exception {
        // when - deleting a cassette that was never registered
        HttpResponse deleteResponse = send("DELETE", "/mockserver/cassettes?path=/c/never-here.json", null);

        // then - the call succeeds but reports nothing was removed
        assertThat(deleteResponse.getStatusCode(), is(200));
        assertThat(json(deleteResponse).get("removed").asBoolean(), is(false));
    }

    @Test
    public void shouldRejectDeleteWithoutPathQueryParameterOrBodyField() throws Exception {
        // given - a registered cassette that must survive a malformed delete
        assertThat(send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/survivor.json\"}").getStatusCode(), is(201));

        // when - neither ?path= nor a body path is supplied
        HttpResponse response = send("DELETE", "/mockserver/cassettes", null);

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(json(response).get("error").asText(), containsString("'path' is required (query parameter or body field)"));

        // then - the rejected delete removed nothing
        assertThat(json(send("GET", "/mockserver/cassettes", null)).get("cassettes").size(), is(1));
    }

    // ------------------------------------------------------------------
    // registry lifecycle
    // ------------------------------------------------------------------

    @Test
    public void shouldEmptyRegistryOnServerReset() throws Exception {
        // given
        send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/one.json\"}");
        send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/two.json\"}");
        assertThat(json(send("GET", "/mockserver/cassettes", null)).get("cassettes").size(), is(2));

        // when
        reset();

        // then - the registry is empty
        assertThat(json(send("GET", "/mockserver/cassettes", null)).get("cassettes").size(), is(0));
    }

    // ------------------------------------------------------------------
    // bare alias — GET/PUT/DELETE /cassettes must behave identically to /mockserver/cassettes
    // ------------------------------------------------------------------

    @Test
    public void shouldServeAllCassetteVerbsViaBareAlias() throws Exception {
        // when - registering through the bare alias
        HttpResponse registration = send("PUT", "/cassettes", "{\"path\":\"/c/bare.json\",\"expectationCount\":2,\"origin\":\"recorded\"}");

        // then - identical to the prefixed form
        assertThat("PUT /cassettes (bare alias) must register a cassette", registration.getStatusCode(), is(201));
        JsonNode registrationBody = json(registration);
        assertThat(registrationBody.get("path").asText(), is("/c/bare.json"));
        assertThat(registrationBody.get("filename").asText(), is("bare.json"));
        assertThat(registrationBody.get("expectationCount").asInt(), is(2));
        assertThat(registrationBody.get("origin").asText(), is("recorded"));

        // then - the bare alias lists from the same registry the prefixed form writes to
        HttpResponse bareList = send("GET", "/cassettes", null);
        assertThat("GET /cassettes (bare alias) must list cassettes", bareList.getStatusCode(), is(200));
        JsonNode bareCassettes = json(bareList).get("cassettes");
        assertThat(bareCassettes.size(), is(1));
        assertThat(bareCassettes.get(0).get("path").asText(), is("/c/bare.json"));
        assertThat("prefixed and bare paths must share one registry",
            json(send("GET", "/mockserver/cassettes", null)).get("cassettes").size(), is(1));

        // when - deleting through the bare alias
        HttpResponse bareDelete = send("DELETE", "/cassettes?path=/c/bare.json", null);

        // then
        assertThat("DELETE /cassettes (bare alias) must remove a cassette", bareDelete.getStatusCode(), is(200));
        assertThat(json(bareDelete).get("removed").asBoolean(), is(true));
        assertThat(json(send("GET", "/mockserver/cassettes", null)).get("cassettes").size(), is(0));
    }

    // ------------------------------------------------------------------
    // cross-origin access for the dashboard
    // ------------------------------------------------------------------

    @Test
    public void shouldReturnCORSHeadersSoDashboardCanCallCassetteEndpointsCrossOrigin() throws Exception {
        String origin = "http://localhost:5173";

        // when - each verb is called with an Origin header, as a browser would
        HttpResponse putResponse = send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/cors.json\"}", "origin", origin);
        HttpResponse getResponse = send("GET", "/mockserver/cassettes", null, "origin", origin);
        HttpResponse deleteResponse = send("DELETE", "/mockserver/cassettes?path=/c/cors.json", null, "origin", origin);

        // then - the requesting origin is reflected and the cassette verbs are advertised as allowed
        assertThat(putResponse.getStatusCode(), is(201));
        assertThat(getResponse.getStatusCode(), is(200));
        assertThat(deleteResponse.getStatusCode(), is(200));
        for (HttpResponse response : new HttpResponse[]{putResponse, getResponse, deleteResponse}) {
            assertThat("cassette responses must reflect the requesting Origin",
                response.getFirstHeader("access-control-allow-origin"), is(origin));
            String allowMethods = response.getFirstHeader("access-control-allow-methods");
            assertThat("cassette responses must advertise the cassette verbs", allowMethods, containsString("GET"));
            assertThat("cassette responses must advertise the cassette verbs", allowMethods, containsString("PUT"));
            assertThat("cassette responses must advertise the cassette verbs", allowMethods, containsString("DELETE"));
        }
    }

    @Test
    public void shouldRefuseAForeignOriginWithoutCORSHeaders() throws Exception {
        HttpResponse response = send("GET", "/mockserver/cassettes", null, "origin", "https://attacker.example");

        assertThat(response.getStatusCode(), is(403));
        assertThat(response.containsHeader("access-control-allow-origin"), is(false));
    }

    // ------------------------------------------------------------------
    // control-plane authentication gate
    // ------------------------------------------------------------------

    @Test
    public void shouldRejectUnauthenticatedCassetteRequestsWhenControlPlaneAuthenticationRequired() throws Exception {
        try {
            // given - control-plane authentication is required and the caller presents no credentials
            ConfigurationProperties.controlPlaneJWTAuthenticationRequired(true);

            // then - every cassette verb is refused rather than served
            assertThat("GET /mockserver/cassettes must be refused when control-plane auth is required",
                send("GET", "/mockserver/cassettes", null).getStatusCode(), is(401));
            assertThat("PUT /mockserver/cassettes must be refused when control-plane auth is required",
                send("PUT", "/mockserver/cassettes", "{\"path\":\"/c/should-not-store.json\"}").getStatusCode(), is(401));
            assertThat("DELETE /mockserver/cassettes must be refused when control-plane auth is required",
                send("DELETE", "/mockserver/cassettes?path=/c/should-not-store.json", null).getStatusCode(), is(401));
        } finally {
            // restore the open control plane so sibling tests (and other suites in this fork) are unaffected
            ConfigurationProperties.controlPlaneJWTAuthenticationRequired(false);
        }

        // then - the refused PUT stored nothing: the registry is still empty once auth is off again
        assertThat(json(send("GET", "/mockserver/cassettes", null)).get("cassettes").size(), is(0));
    }
}
