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
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * End-to-end tests for the {@code PUT /mockserver/crud} control-plane endpoint (and its bare
 * {@code PUT /crud} alias), driving a real MockServer over a real socket.
 * <p>
 * These tests deliberately go beyond asserting the registration status code: after registering a
 * CRUD resource they exercise the registered base path on the data plane (POST/GET/PUT/PATCH/DELETE)
 * to prove the dispatcher was actually wired up and that the backing store behaves as advertised.
 */
public class ControlPlaneCrudIntegrationTest {

    private static MockServer mockServer;
    private static NettyHttpClient httpClient;
    private static EventLoopGroup clientEventLoopGroup;
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    @BeforeClass
    public static void startServerAndClient() {
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(ControlPlaneCrudIntegrationTest.class.getSimpleName() + "-eventLoop"));
        httpClient = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false);
        mockServer = new MockServer();
    }

    @AfterClass
    public static void stopServerAndClient() {
        stopQuietly(mockServer);
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Before
    public void resetBefore() throws Exception {
        reset();
    }

    @After
    public void resetAfter() throws Exception {
        reset();
    }

    private void reset() throws Exception {
        send("PUT", "/mockserver/reset", null);
    }

    private HttpResponse send(String method, String path, String body) throws Exception {
        org.mockserver.model.HttpRequest httpRequest = request()
            .withMethod(method)
            .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
            .withPath(path);
        if (body != null) {
            httpRequest = httpRequest.withBody(body);
        }
        return httpClient.sendRequest(httpRequest).get(15, TimeUnit.SECONDS);
    }

    private static JsonNode json(HttpResponse response) throws Exception {
        return OBJECT_MAPPER.readTree(response.getBodyAsString());
    }

    // ------------------------------------------------------------------
    // happy path — registration then a full data-plane lifecycle
    // ------------------------------------------------------------------

    @Test
    public void shouldRegisterCrudResourceAndServeFullLifecycleOnDataPlane() throws Exception {
        // given - a CRUD resource with one pre-seeded item
        HttpResponse registration = send("PUT", "/mockserver/crud",
            "{" +
                "\"basePath\":\"/crudLifecycle/users\"," +
                "\"initialData\":[{\"id\":1,\"name\":\"seeded\"}]" +
                "}");

        // then - registration is reported accurately
        assertThat(registration.getStatusCode(), is(201));
        assertThat(registration.getFirstHeader("content-type"), containsString("application/json"));
        JsonNode registrationBody = json(registration);
        assertThat(registrationBody.get("basePath").asText(), is("/crudLifecycle/users"));
        assertThat(registrationBody.get("idField").asText(), is("id"));
        assertThat(registrationBody.get("idStrategy").asText(), is("AUTO_INCREMENT"));
        assertThat("itemCount must reflect the seeded initialData", registrationBody.get("itemCount").asInt(), is(1));

        // then - the seeded item is visible on the data plane
        HttpResponse initialList = send("GET", "/crudLifecycle/users", null);
        assertThat(initialList.getStatusCode(), is(200));
        JsonNode initialItems = json(initialList);
        assertThat(initialItems.size(), is(1));
        assertThat(initialItems.get(0).get("id").asInt(), is(1));
        assertThat(initialItems.get(0).get("name").asText(), is("seeded"));

        // when - creating items, ids auto-increment beyond the highest seeded id
        HttpResponse createdAlice = send("POST", "/crudLifecycle/users", "{\"name\":\"alice\"}");
        assertThat(createdAlice.getStatusCode(), is(201));
        JsonNode alice = json(createdAlice);
        assertThat("auto-increment must continue past the seeded id 1", alice.get("id").asInt(), is(2));
        assertThat(alice.get("name").asText(), is("alice"));

        HttpResponse createdBob = send("POST", "/crudLifecycle/users", "{\"name\":\"bob\"}");
        assertThat(createdBob.getStatusCode(), is(201));
        assertThat(json(createdBob).get("id").asInt(), is(3));

        // then - individual items are retrievable by id
        HttpResponse getAlice = send("GET", "/crudLifecycle/users/2", null);
        assertThat(getAlice.getStatusCode(), is(200));
        assertThat(json(getAlice).get("name").asText(), is("alice"));

        // then - a full update replaces the item but preserves the id
        HttpResponse updateAlice = send("PUT", "/crudLifecycle/users/2", "{\"name\":\"alice-updated\",\"role\":\"admin\"}");
        assertThat(updateAlice.getStatusCode(), is(200));
        JsonNode updatedAlice = json(updateAlice);
        assertThat(updatedAlice.get("id").asInt(), is(2));
        assertThat(updatedAlice.get("name").asText(), is("alice-updated"));
        assertThat(updatedAlice.get("role").asText(), is("admin"));

        // then - a patch merges fields, leaving untouched fields intact
        HttpResponse patchAlice = send("PATCH", "/crudLifecycle/users/2", "{\"role\":\"reader\"}");
        assertThat(patchAlice.getStatusCode(), is(200));
        JsonNode patchedAlice = json(patchAlice);
        assertThat("patch must not clobber unrelated fields", patchedAlice.get("name").asText(), is("alice-updated"));
        assertThat(patchedAlice.get("role").asText(), is("reader"));
        assertThat(patchedAlice.get("id").asInt(), is(2));

        // then - listing reflects all three items in insertion order
        JsonNode listBeforeDelete = json(send("GET", "/crudLifecycle/users", null));
        assertThat(listBeforeDelete.size(), is(3));
        assertThat(listBeforeDelete.get(0).get("id").asInt(), is(1));
        assertThat(listBeforeDelete.get(1).get("id").asInt(), is(2));
        assertThat(listBeforeDelete.get(2).get("id").asInt(), is(3));

        // then - delete removes the item and is reflected in subsequent reads
        HttpResponse deleteAlice = send("DELETE", "/crudLifecycle/users/2", null);
        assertThat(deleteAlice.getStatusCode(), is(204));

        assertThat(send("GET", "/crudLifecycle/users/2", null).getStatusCode(), is(404));

        JsonNode listAfterDelete = json(send("GET", "/crudLifecycle/users", null));
        assertThat(listAfterDelete.size(), is(2));
        assertThat(listAfterDelete.get(0).get("id").asInt(), is(1));
        assertThat(listAfterDelete.get(1).get("id").asInt(), is(3));

        // then - deleting an unknown id is a 404
        assertThat(send("DELETE", "/crudLifecycle/users/9999", null).getStatusCode(), is(404));
    }

    // ------------------------------------------------------------------
    // bare alias — PUT /crud must behave identically to PUT /mockserver/crud
    // ------------------------------------------------------------------

    @Test
    public void shouldRegisterCrudResourceViaBareAliasWithCustomIdFieldAndUuidStrategy() throws Exception {
        // when - registering through the bare alias with non-default id settings
        HttpResponse registration = send("PUT", "/crud",
            "{" +
                "\"basePath\":\"/crudBareAlias/items\"," +
                "\"idField\":\"key\"," +
                "\"idStrategy\":\"UUID\"" +
                "}");

        // then
        assertThat("PUT /crud (bare alias) must register a CRUD resource", registration.getStatusCode(), is(201));
        JsonNode registrationBody = json(registration);
        assertThat(registrationBody.get("basePath").asText(), is("/crudBareAlias/items"));
        assertThat(registrationBody.get("idField").asText(), is("key"));
        assertThat(registrationBody.get("idStrategy").asText(), is("UUID"));
        assertThat(registrationBody.get("itemCount").asInt(), is(0));

        // then - the resource registered via the bare alias actually serves traffic
        HttpResponse created = send("POST", "/crudBareAlias/items", "{\"label\":\"widget\"}");
        assertThat(created.getStatusCode(), is(201));
        JsonNode createdItem = json(created);
        assertThat("custom idField must be used", createdItem.get("key"), is(not(nullValue())));
        assertThat("UUID strategy must not produce a numeric id", createdItem.get("key").asText().length(), is(36));
        assertThat(createdItem.get("label").asText(), is("widget"));

        String generatedId = createdItem.get("key").asText();
        HttpResponse fetched = send("GET", "/crudBareAlias/items/" + generatedId, null);
        assertThat(fetched.getStatusCode(), is(200));
        assertThat(json(fetched).get("label").asText(), is("widget"));
    }

    // ------------------------------------------------------------------
    // error paths
    // ------------------------------------------------------------------

    @Test
    public void shouldRejectRegistrationWithoutBasePath() throws Exception {
        // when
        HttpResponse response = send("PUT", "/mockserver/crud", "{\"idField\":\"id\"}");

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getFirstHeader("content-type"), containsString("text/plain"));
        assertThat(response.getBodyAsString(), is("basePath is required"));
    }

    @Test
    public void shouldRejectRegistrationWithEmptyBasePathViaBareAlias() throws Exception {
        // when
        HttpResponse response = send("PUT", "/crud", "{\"basePath\":\"\"}");

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getFirstHeader("content-type"), containsString("text/plain"));
        assertThat(response.getBodyAsString(), is("basePath is required"));
    }

    @Test
    public void shouldRejectBasePathThatOverlapsTheControlPlane() throws Exception {
        // when
        HttpResponse response = send("PUT", "/mockserver/crud", "{\"basePath\":\"/mockserver/hijack\"}");

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getFirstHeader("content-type"), containsString("text/plain"));
        assertThat(response.getBodyAsString(), containsString("failed to register CRUD resource: "));
        assertThat(response.getBodyAsString(), containsString("must not overlap with the /mockserver control plane"));

        // then - nothing was registered, so the path is not served
        assertThat(send("GET", "/mockserver/hijack", null).getStatusCode(), is(404));
    }

    @Test
    public void shouldRejectBasePathContainingPathTraversal() throws Exception {
        // when
        HttpResponse response = send("PUT", "/mockserver/crud", "{\"basePath\":\"/crudTraversal/../etc\"}");

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("failed to register CRUD resource: "));
        assertThat(response.getBodyAsString(), containsString("path traversal"));
    }

    @Test
    public void shouldRejectMalformedRegistrationJson() throws Exception {
        // when
        HttpResponse response = send("PUT", "/mockserver/crud", "{ this is not json ");

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getFirstHeader("content-type"), containsString("text/plain"));
        assertThat(response.getBodyAsString(), containsString("failed to register CRUD resource: "));
    }

    // ------------------------------------------------------------------
    // registration lifecycle
    // ------------------------------------------------------------------

    @Test
    public void shouldClearRegisteredCrudResourcesOnReset() throws Exception {
        // given
        assertThat(send("PUT", "/mockserver/crud", "{\"basePath\":\"/crudReset/things\"}").getStatusCode(), is(201));
        assertThat(send("GET", "/crudReset/things", null).getStatusCode(), is(200));

        // when
        reset();

        // then - the base path is no longer served by the CRUD dispatcher
        assertThat(send("GET", "/crudReset/things", null).getStatusCode(), is(404));
    }

    @Test
    public void shouldReplaceRegistrationWhenSameBasePathRegisteredTwice() throws Exception {
        // given
        assertThat(send("PUT", "/mockserver/crud",
            "{\"basePath\":\"/crudReplace/items\",\"initialData\":[{\"id\":7,\"name\":\"first\"}]}").getStatusCode(), is(201));
        assertThat(json(send("GET", "/crudReplace/items", null)).get(0).get("name").asText(), is("first"));

        // when - re-registering the same base path through the bare alias
        HttpResponse reRegistration = send("PUT", "/crud",
            "{\"basePath\":\"/crudReplace/items\",\"initialData\":[{\"id\":9,\"name\":\"second\"}]}");

        // then
        assertThat(reRegistration.getStatusCode(), is(201));
        JsonNode list = json(send("GET", "/crudReplace/items", null));
        assertThat("re-registration must replace the previous store", list.size(), is(1));
        assertThat(list.get(0).get("id").asInt(), is(9));
        assertThat(list.get(0).get("name").asText(), is("second"));
    }
}
