package org.mockserver.netty.integration;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.netty.MockServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A control-plane request carrying a browser Origin that controlPlaneCorsAllowOrigin does not allow
 * is refused with 403 before any handler or expectation runs, whatever answered its preflight (an
 * expectation, a respondBeforeBody expectation, a cached preflight) and whether or not it needed one.
 */
public class ControlPlaneOriginEnforcementIntegrationTest {

    private static final String FOREIGN = "https://attacker.example";
    private static final String CATCH_ALL = "{\"httpRequest\":{},\"httpResponse\":{\"statusCode\":200,\"body\":\"MOCKED\"}}";
    private static final String PERMISSIVE_OPTIONS = "{\"httpRequest\":{\"method\":\"OPTIONS\"},\"httpResponse\":{\"statusCode\":204,\"headers\":{"
        + "\"Access-Control-Allow-Origin\":[\"*\"],\"Access-Control-Allow-Methods\":[\"GET, POST, PUT, DELETE, OPTIONS\"],"
        + "\"Access-Control-Allow-Headers\":[\"*\"],\"Access-Control-Max-Age\":[\"3600\"]}}}";
    private static final String EARLY_PERMISSIVE_OPTIONS = "{\"httpRequest\":{\"method\":\"OPTIONS\",\"respondBeforeBody\":true},\"httpResponse\":{\"statusCode\":204,\"headers\":{"
        + "\"Access-Control-Allow-Origin\":[\"*\"],\"Access-Control-Allow-Methods\":[\"GET, POST, PUT, DELETE, OPTIONS\"],"
        + "\"Access-Control-Allow-Headers\":[\"*\"]}}}";

    private MockServer mockServer;

    @After
    public void stopServer() {
        stopQuietly(mockServer);
    }

    private int start(Configuration configuration) {
        mockServer = new MockServer(configuration);
        return mockServer.getLocalPort();
    }

    @Test
    public void shouldRefuseACrossSiteResetAfterAnExpectationApprovedAnotherMethodsPreflight() throws IOException {
        // CacheProbe A: a cached preflight for POST /reset lists PUT, so the browser sends PUT /reset unpreflighted
        int port = start(configuration().enableCORSForAllResponses(true).corsMaxAgeInSeconds(600));
        putExpectation(port, CATCH_ALL);

        String cachedPreflight = preflight(port, "/reset", "POST");
        String crossSiteReset = send(port, "PUT /reset", FOREIGN, "text/plain", "");

        assertThat(cachedPreflight.toLowerCase(), containsString("access-control-max-age: 600"));
        assertThat(crossSiteReset, startsWith("HTTP/1.1 403"));
        assertThat("the reset did not run", activeExpectations(port), is(1));
    }

    @Test
    public void shouldRefuseACrossSiteResetAfterAUserOptionsExpectationApprovedItsPreflight() throws IOException {
        // CacheProbe B: a user OPTIONS expectation with permissive headers and Max-Age
        int port = start(configuration());
        putExpectation(port, PERMISSIVE_OPTIONS);

        String crossSiteReset = send(port, "PUT /reset", FOREIGN, "text/plain", "");
        String crossSiteExpectation = send(port, "PUT /expectation", FOREIGN, "text/plain", CATCH_ALL);

        assertThat(crossSiteReset, startsWith("HTTP/1.1 403"));
        assertThat(crossSiteExpectation, startsWith("HTTP/1.1 403"));
        assertThat("neither the reset nor the expectation ran", activeExpectations(port), is(1));
    }

    @Test
    public void shouldNotLetARespondBeforeBodyExpectationAnswerAControlPlanePreflightOrRequest() throws IOException {
        // CacheProbe C: the early (respondBeforeBody) path ran before the control plane
        int port = start(configuration());
        putExpectation(port, EARLY_PERMISSIVE_OPTIONS);

        String resetPreflight = preflight(port, "/reset", "PUT");
        String configurationPreflight = preflight(port, "/configuration", "PUT");
        String crossSiteReset = send(port, "PUT /reset", FOREIGN, null, "");

        assertThat(resetPreflight, startsWith("HTTP/1.1 200"));
        assertThat(resetPreflight.toLowerCase(), not(containsString("access-control-allow-origin")));
        assertThat(configurationPreflight.toLowerCase(), not(containsString("access-control-allow-origin")));
        assertThat(crossSiteReset, startsWith("HTTP/1.1 403"));
        assertThat("the reset did not run", activeExpectations(port), is(1));
    }

    @Test
    public void shouldRefuseABlindCrossSiteExpectationThatWouldExfiltrateRecordedTraffic() throws IOException {
        // ExfilProbe: a simple text/plain PUT /expectation forwarding to the server's own /mockserver/retrieve
        int port = start(configuration());
        String exfil = "{\"httpRequest\":{\"path\":\"/exfil\"},\"httpOverrideForwardedRequest\":{\"httpRequest\":{\"method\":\"PUT\","
            + "\"path\":\"/mockserver/retrieve\",\"queryStringParameters\":{\"type\":[\"REQUESTS\"]},\"headers\":{\"Host\":[\"localhost:" + port + "\"]}}},"
            + "\"httpResponse\":{\"headers\":{\"Access-Control-Allow-Origin\":[\"*\"]}}}";

        String blind = send(port, "PUT /mockserver/expectation", FOREIGN, "text/plain", exfil);
        String blindBareAlias = send(port, "PUT /expectation", FOREIGN, "text/plain", exfil);

        assertThat(blind, startsWith("HTTP/1.1 403"));
        assertThat(blindBareAlias, startsWith("HTTP/1.1 403"));
        assertThat(activeExpectations(port), is(0));
    }

    @Test
    public void shouldRefuseSimpleCrossSiteRequestsToControlPlanePaths() throws IOException {
        int port = start(configuration());

        assertThat(send(port, "POST /mockserver/chaosExperiment/apply/latency", FOREIGN, "text/plain", "{}"), startsWith("HTTP/1.1 403"));
        assertThat(send(port, "POST /mockserver/wasm/test", FOREIGN, "text/plain", "{}"), startsWith("HTTP/1.1 403"));
        assertThat(send(port, "GET /mockserver/retrieve", FOREIGN, null, ""), startsWith("HTTP/1.1 403"));
        assertThat(send(port, "PUT /mockserver/reset", "null", "text/plain", ""), startsWith("HTTP/1.1 403"));
        assertThat(send(port, "POST /mockserver/mcp", FOREIGN, "text/plain", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"), startsWith("HTTP/1.1 403"));
    }

    @Test
    public void shouldAllowNonBrowserLoopbackAndAllowListedOriginsAndLeaveMockPathsAlone() throws IOException {
        int port = start(configuration().controlPlaneCorsAllowOrigin("https://app.example.com"));
        putExpectation(port, "{\"httpRequest\":{\"path\":\"/api/orders\"},\"httpResponse\":{\"statusCode\":200,\"body\":\"MOCKED\"}}");

        assertThat(send(port, "PUT /mockserver/retrieve?type=ACTIVE_EXPECTATIONS", null, null, ""), startsWith("HTTP/1.1 200"));
        assertThat(send(port, "PUT /mockserver/retrieve?type=ACTIVE_EXPECTATIONS", "https://app.example.com", null, ""), startsWith("HTTP/1.1 200"));
        assertThat(send(port, "PUT /mockserver/retrieve?type=ACTIVE_EXPECTATIONS", "http://localhost:" + port, null, ""), startsWith("HTTP/1.1 200"));
        assertThat("loopback is only the default, an explicit list replaces it",
            send(port, "PUT /mockserver/retrieve?type=ACTIVE_EXPECTATIONS", "http://localhost:3000", null, ""), startsWith("HTTP/1.1 403"));
        assertThat("a mocked path is not control plane", send(port, "POST /api/orders", FOREIGN, "text/plain", "{}"), startsWith("HTTP/1.1 200"));
        assertThat("a bare alias with a method the control plane does not serve there is not control plane",
            send(port, "GET /status", FOREIGN, null, ""), startsWith("HTTP/1.1 404"));
    }

    // ---- helpers ----

    private static void putExpectation(int port, String expectation) throws IOException {
        assertThat(send(port, "PUT /mockserver/expectation", null, "application/json", expectation), startsWith("HTTP/1.1 201"));
    }

    private static int activeExpectations(int port) throws IOException {
        String response = send(port, "PUT /mockserver/retrieve?type=ACTIVE_EXPECTATIONS", null, null, "");
        return response.split("\"httpRequest\"", -1).length - 1;
    }

    private static String preflight(int port, String path, String method) throws IOException {
        return exchange(port,
            "OPTIONS " + path + " HTTP/1.1\r\n" +
                "Host: localhost:" + port + "\r\n" +
                "Origin: " + FOREIGN + "\r\n" +
                "Access-Control-Request-Method: " + method + "\r\n" +
                "Connection: close\r\n\r\n");
    }

    private static String send(int port, String requestLine, String origin, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder request = new StringBuilder(requestLine).append(" HTTP/1.1\r\n")
            .append("Host: localhost:").append(port).append("\r\n");
        if (origin != null) {
            request.append("Origin: ").append(origin).append("\r\n");
        }
        if (contentType != null) {
            request.append("Content-Type: ").append(contentType).append("\r\n");
        }
        request.append("Content-Length: ").append(bytes.length).append("\r\n")
            .append("Connection: close\r\n\r\n")
            .append(body);
        return exchange(port, request.toString());
    }

    private static String exchange(int port, String request) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 5_000);
            socket.setSoTimeout(30_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            int read;
            try {
                while ((read = in.read(buffer)) > 0) {
                    response.write(buffer, 0, read);
                }
            } catch (SocketTimeoutException ignored) {
                // a keep-alive response that ignored Connection: close; what arrived is enough
            }
            return response.toString(StandardCharsets.UTF_8);
        }
    }
}
