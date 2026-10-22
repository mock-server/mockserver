package org.mockserver.netty.integration.authenticatedcontrolplane;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.basic.PasswordHash;
import org.mockserver.client.ClientException;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * With control-plane HTTP Basic authentication on, MockServerClient sends its Basic credentials on its HTTP
 * requests and on the callback and breakpoint WebSocket upgrades, over TLS; credentials sent without TLS, wrong
 * credentials and no credentials are refused. The server pins its settings on its own Configuration.
 */
public class AuthenticatedControlPlaneUsingBasicIntegrationTest {

    private static ClientAndServer server;
    private final List<ClientOnly> clients = new ArrayList<>();

    @BeforeClass
    public static void startServer() {
        server = ClientAndServer.startClientAndServer(withBundledCa(configuration())
            .logLevel("INFO")
            .controlPlaneTLSMutualAuthenticationRequired(false)
            .controlPlaneJWTAuthenticationRequired(false)
            .controlPlaneOidcAuthenticationRequired(false)
            .controlPlaneBasicAuthenticationRequired(true)
            .controlPlaneBasicAuthenticationCredentials("admin:" + PasswordHash.bcrypt("s3cret", 4)));
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(server);
    }

    @After
    public void releaseClients() {
        clients.forEach(ClientOnly::release);
    }

    @Test
    public void controlPlaneRequestsShouldAuthenticateWithBasicOverTls() throws Exception {
        MockServerClient client = client(true).withControlPlaneBasicAuthentication("admin", "s3cret");

        client.when(request("/basic-expectation")).respond(response().withBody("from-expectation"));

        assertThat(send("/basic-expectation").body(), is("from-expectation"));
    }

    @Test
    public void callbackWebSocketShouldSendTheBasicCredentials() throws Exception {
        MockServerClient client = client(true).withControlPlaneBasicAuthentication("admin", "s3cret");

        client.when(request("/basic-callback")).respond(httpRequest -> response().withBody("from-callback"));

        assertThat(send("/basic-callback").body(), is("from-callback"));
    }

    @Test
    public void breakpointWebSocketShouldSendTheBasicCredentials() {
        MockServerClient client = client(true).withControlPlaneBasicAuthentication("admin", "s3cret");

        String breakpointId = client.addBreakpoint(request("/basic-breakpoint"), httpRequest -> null);
        try {
            assertThat(breakpointId, not(emptyOrNullString()));
        } finally {
            client.removeBreakpointMatcher(breakpointId);
        }
    }

    @Test
    public void callbackWebSocketShouldBeRefusedWithoutCredentials() {
        MockServerClient client = client(true);

        ClientException refused = assertThrows(ClientException.class, () -> client.when(request("/no-basic-callback")).respond(httpRequest -> response()));

        assertThat("refused at the WebSocket upgrade, before any expectation is sent",
            refused.getMessage(), allOf(containsString("handshake failure"), containsString("401")));
    }

    @Test
    public void wrongCredentialsShouldBeRefused() {
        MockServerClient client = client(true).withControlPlaneBasicAuthentication("admin", "guess");

        AuthenticationException refused = assertThrows(AuthenticationException.class, () -> client.when(request("/wrong")).respond(response()));

        assertThat(refused.getMessage(), containsString("failed basic authentication"));
    }

    @Test
    public void credentialsSentWithoutTlsShouldBeRefused() {
        MockServerClient client = client(false).withControlPlaneBasicAuthentication("admin", "s3cret");

        AuthenticationException refused = assertThrows(AuthenticationException.class, () -> client.when(request("/plain-text")).respond(response()));

        assertThat(refused.getMessage(), containsString("basic authentication requires TLS"));
    }

    @Test
    public void mcpShouldAuthenticateWithBasicOverTlsAndRefuseItWithout() throws Exception {
        String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";

        assertThat(mcp("https", server.getPort(), basic("admin", "s3cret"), initialize).statusCode(), is(200));
        HttpResponse<String> plainText = mcp("http", server.getPort(), basic("admin", "s3cret"), initialize);
        assertThat(plainText.statusCode(), is(401));
        assertThat("a plain-text 401 does not invite a browser to send a password", plainText.headers().firstValue("WWW-Authenticate").isPresent(), is(false));
    }

    @Test
    public void mcpShouldThrottleGuessesByTheClientsAddress() throws Exception {
        ClientAndServer throttled = ClientAndServer.startClientAndServer(withBundledCa(configuration())
            .controlPlaneTLSMutualAuthenticationRequired(false)
            .controlPlaneJWTAuthenticationRequired(false)
            .controlPlaneOidcAuthenticationRequired(false)
            .controlPlaneBasicAuthenticationRequired(true)
            .controlPlaneBasicAuthenticationCredentials("admin:" + PasswordHash.bcrypt("s3cret", 4)));
        try {
            String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
            for (int i = 0; i < 5; i++) {
                assertThat(mcp("https", throttled.getPort(), basic("admin", "guess-" + i), initialize).statusCode(), is(401));
            }

            assertThat("the guesser's address is blocked, so even the right password waits",
                mcp("https", throttled.getPort(), basic("admin", "s3cret"), initialize).statusCode(), is(401));
            ClientOnly fromTheSameAddress = new ClientOnly(withBundledCa(configuration()), throttled.getPort());
            clients.add(fromTheSameAddress);
            fromTheSameAddress.withSecure(true).withControlPlaneBasicAuthentication("admin", "s3cret");
            assertThat("an ordinary control-plane request from that address is blocked too",
                assertThrows(AuthenticationException.class, () -> fromTheSameAddress.retrieveActiveExpectations(null)).getMessage(),
                containsString("too many failed basic authentication attempts"));
        } finally {
            stopQuietly(throttled);
        }
    }

    @Test
    public void successfulControlPlaneCallsShouldNotLeaveTheirCredentialsInTheLogs() throws Exception {
        String admin = basic("admin", "s3cret");

        assertThat(controlPlane("PUT", "/mockserver/configuration", admin, "{}").statusCode(), is(200));
        assertThat(controlPlane("PUT", "/mockserver/scenario/log-redaction", admin, "{\"state\":\"Started\"}").statusCode(), is(200));
        HttpResponse<String> logs = controlPlane("PUT", "/mockserver/retrieve?type=LOGS&format=LOG_ENTRIES", admin, "");

        assertThat(logs.statusCode(), is(200));
        assertThat(logs.body(), containsString("/mockserver/configuration"));
        assertThat(logs.body(), not(containsString(admin.substring("Basic ".length()))));
        assertThat(logs.body(), not(containsString("s3cret")));
    }

    @Test
    public void aCutShortControlPlaneUploadShouldNotLeaveItsCredentialsInTheLogsInAnyRequestTargetForm() throws Exception {
        String admin = basic("admin", "s3cret");
        java.util.List<String> targets = java.util.Arrays.asList(
            "/mockserver/expectation",
            "http://localhost:" + server.getPort() + "/mockserver/expectation",
            "/%6Dockserver/expectation",
            "/MockServer/expectation"
        );
        String countBefore = logEntries(admin);
        int cutShortBefore = org.apache.commons.lang3.StringUtils.countMatches(countBefore, CUT_SHORT);
        for (String target : targets) {
            rawRequest(target, admin, "[{\"httpRequest\":", 100_000, false);
        }

        String body = "";
        long deadline = System.currentTimeMillis() + 20_000;
        while (org.apache.commons.lang3.StringUtils.countMatches(body, CUT_SHORT) < cutShortBefore + targets.size() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            body = logEntries(admin);
        }

        assertThat("every cut-short upload was logged", org.apache.commons.lang3.StringUtils.countMatches(body, CUT_SHORT), is(cutShortBefore + targets.size()));
        assertThat(body, not(containsString(admin.substring("Basic ".length()))));
        assertThat(body, not(containsString("s3cret")));
    }

    @Test
    public void anAbsoluteFormControlPlaneCallShouldBeAuthenticatedAndLoggedWithoutItsCredentials() throws Exception {
        String admin = basic("admin", "s3cret");
        String expectation = "[{\"httpRequest\":{\"path\":\"/absolute-form\"},\"httpResponse\":{\"statusCode\":200}}]";

        assertThat(rawRequest("http://localhost:" + server.getPort() + "/mockserver/expectation", admin, expectation, expectation.length(), true), containsString(" 201 "));
        String dataPlane = basic("data", "plane");
        assertThat("a percent-encoded or mixed-case path is not the control plane, as the router decides, so it is mock traffic",
            rawRequest("/%6Dockserver/expectation", dataPlane, expectation, expectation.length(), true), containsString(" 404 "));
        assertThat(rawRequest("/MockServer/expectation", dataPlane, expectation, expectation.length(), true), containsString(" 404 "));

        String body = logEntries(admin);
        assertThat(body, containsString("/absolute-form"));
        assertThat(body, not(containsString(admin.substring("Basic ".length()))));
        assertThat("mock traffic is still logged exactly as it was sent", body, containsString(dataPlane.substring("Basic ".length())));
    }

    // the entry's format, once per entry (its rendered message repeats the words)
    private static final String CUT_SHORT = "\"messageFormat\" : \"HTTP/1.1 request from:{}ended with its connection before it was complete:{}\"";

    private static String logEntries(String authorization) throws Exception {
        return controlPlane("PUT", "/mockserver/retrieve?type=LOGS&format=LOG_ENTRIES", authorization, "").body();
    }

    /**
     * Sends one HTTP/1.1 request over TLS with any request-target form: when {@code complete} is false, the body is cut
     * short and the connection closed; otherwise the status line of the response is returned.
     */
    private static String rawRequest(String target, String authorization, String body, int contentLength, boolean complete) throws Exception {
        javax.net.ssl.SSLSocketFactory sockets = org.mockserver.netty.MockServerCaTrustTestSupport.caTrustingSslContext().getSocketFactory();
        try (java.net.Socket socket = sockets.createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(30_000);
            socket.getOutputStream().write((
                "PUT " + target + " HTTP/1.1\r\n"
                    + "Host: localhost:" + server.getPort() + "\r\n"
                    + "Authorization: " + authorization + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + contentLength + "\r\n"
                    + (complete ? "Connection: close\r\n" : "")
                    + "\r\n"
                    + body
            ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            if (!complete) {
                return "";
            }
            return new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)).readLine();
        }
    }

    private static HttpResponse<String> controlPlane(String method, String path, String authorization, String body) throws Exception {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(org.mockserver.netty.MockServerCaTrustTestSupport.caTrustingSslContext())
            .connectTimeout(Duration.ofSeconds(10))
            .build()
            .send(
                HttpRequest.newBuilder(URI.create("https://localhost:" + server.getPort() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", authorization)
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
    }

    private static String basic(String username, String password) {
        return "Basic " + java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> mcp(String scheme, int port, String authorization, String body) throws Exception {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(org.mockserver.netty.MockServerCaTrustTestSupport.caTrustingSslContext())
            .connectTimeout(Duration.ofSeconds(10))
            .build()
            .send(
                HttpRequest.newBuilder(URI.create(scheme + "://localhost:" + port + "/mockserver/mcp"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
    }

    private MockServerClient client(boolean secure) {
        ClientOnly client = new ClientOnly(withBundledCa(configuration()).maxFutureTimeoutInMillis(20_000L), server.getPort());
        clients.add(client);
        client.withSecure(secure);
        return client;
    }

    private static Configuration withBundledCa(Configuration configuration) {
        return configuration
            .dynamicallyCreateCertificateAuthorityCertificate(false)
            .certificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE)
            .certificateAuthorityPrivateKey(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY);
    }

    private static HttpResponse<String> send(String path) throws Exception {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
    }

    /**
     * A client whose clean-up releases only its own event loops: MockServerClient.stop() would stop the
     * shared server it points at.
     */
    private static class ClientOnly extends MockServerClient {
        ClientOnly(Configuration configuration, int port) {
            super(configuration, "localhost", port);
        }

        void release() {
            releaseEventLoops();
        }
    }
}
