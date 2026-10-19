package org.mockserver.configuration;

import org.junit.Test;
import org.mockserver.netty.MockServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * Layer C of the configuration-reachability guard: one end-to-end proof that a value set over
 * {@code PUT /mockserver/configuration} actually reaches its ENFORCEMENT site and changes observable
 * behaviour.
 *
 * <p>Layers A and B are static guards — they prove call sites read through the {@link Configuration}
 * instance, and that every risky property has been consciously classified. Neither actually drives the
 * REST endpoint. This test closes that gap by exercising the whole route for three representatives
 * chosen to span the different ways a property can be consumed:
 *
 * <table>
 *   <caption>representatives and the consumption shape each covers</caption>
 *   <tr><th>property</th><th>consumption shape</th><th>observable effect asserted</th></tr>
 *   <tr><td>{@code maxRequestBodySize}</td><td>read when the Netty pipeline is built per connection</td>
 *       <td>an oversized request body is rejected rather than served</td></tr>
 *   <tr><td>{@code wasmEnabled}</td><td>read per match attempt by a matcher</td>
 *       <td>a WASM-matched expectation stops matching</td></tr>
 *   <tr><td>{@code redactSecretsInLog}</td><td>read when a log entry is rendered for retrieval</td>
 *       <td>{@code Authorization} is masked in the retrieved log</td></tr>
 * </table>
 *
 * <p>Each assertion is made BEFORE and AFTER the configuration change, so the test proves the change
 * caused the difference rather than asserting a value that happened to be the default.
 *
 * <p>A failure here is a real defect, not a flaky expectation: it means a documented, DTO-carried
 * property is silently unreachable from the REST control plane. Do not weaken the assertions.
 */
public class ConfigurationRestApiEnforcementIntegrationTest {

    private static final String SENSITIVE_CREDENTIAL = "Bearer super-secret-token-value";

    @Test
    public void shouldEnforceMaxRequestBodySizeSetOverTheConfigurationEndpoint() throws Exception {
        MockServer mockServer = null;
        try {
            mockServer = new MockServer(configuration(), 0);
            int port = mockServer.getLocalPort();

            createExpectation(port, "{"
                + "\"httpRequest\": {\"method\": \"POST\", \"path\": \"/body-size-probe\"},"
                + "\"httpResponse\": {\"statusCode\": 200, \"body\": \"accepted\"}"
                + "}");

            String largeBody = repeat('x', 8192);

            // BEFORE: the default limit comfortably accommodates an 8 KiB body
            Response before = send(port, "POST", "/body-size-probe", largeBody, null);
            assertThat("baseline: an 8KiB body is accepted under the default maxRequestBodySize",
                before.statusCode, is(200));

            // apply a limit far below the body size, over the REST control plane only
            applyConfiguration(port, "{\"maxRequestBodySize\": 128}");

            // AFTER: a new connection builds a pipeline from the updated configuration and rejects it.
            // maxRequestBodySize is consumed when the HTTP aggregator is installed per connection; the
            // aggregator answers 413 (closing through LingeringClose, so a reset cannot drop it).
            Response after = send(port, "POST", "/body-size-probe", largeBody, null);
            assertThat("maxRequestBodySize set via PUT /mockserver/configuration must reject an oversized "
                    + "body — a 200 here means the value round-tripped through the DTO but never reached "
                    + "the Netty pipeline (instance-unreachable enforcement)",
                after.statusCode, is(413));
        } finally {
            stop(mockServer);
        }
    }

    @Test
    public void shouldEnforceWasmEnabledSetOverTheConfigurationEndpoint() throws Exception {
        MockServer mockServer = null;
        try {
            // start with WASM on so the module can be uploaded and the expectation can match
            mockServer = new MockServer(configuration().wasmEnabled(true), 0);
            int port = mockServer.getLocalPort();

            byte[] module = readResource("/org/mockserver/wasm/match-request.wasm");
            Response upload = sendBytes(port, "PUT", "/mockserver/wasm/modules?name=orders", module, null);
            assertThat("WASM module upload should succeed while wasmEnabled=true", upload.statusCode, is(201));

            // the module matches POST /orders carrying header X-Tenant: acme
            createExpectation(port, "{"
                + "\"httpRequest\": {\"method\": \"POST\", \"path\": \"/orders\", "
                + "\"body\": {\"type\": \"WASM\", \"moduleName\": \"orders\"}},"
                + "\"httpResponse\": {\"statusCode\": 200, \"body\": \"wasm-matched\"}"
                + "}");

            // BEFORE: the WASM-matched expectation matches
            Response matched = send(port, "POST", "/orders", "{}", "acme");
            assertThat("baseline: the WASM-matched expectation should match while wasmEnabled=true",
                matched.statusCode, is(200));
            assertThat(matched.body, containsString("wasm-matched"));

            // disable WASM over the REST control plane only
            applyConfiguration(port, "{\"wasmEnabled\": false}");

            // AFTER: the matcher consults the instance per match attempt, so it must stop matching
            Response notMatched = send(port, "POST", "/orders", "{}", "acme");
            assertThat("wasmEnabled=false set via PUT /mockserver/configuration must stop the WASM-matched "
                    + "expectation from matching — a 200 here means the security control is unreachable "
                    + "from the REST API",
                notMatched.statusCode, is(404));
        } finally {
            stop(mockServer);
        }
    }

    @Test
    public void shouldEnforceRedactSecretsInLogSetOverTheConfigurationEndpoint() throws Exception {
        MockServer mockServer = null;
        try {
            mockServer = new MockServer(configuration(), 0);
            int port = mockServer.getLocalPort();

            createExpectation(port, "{"
                + "\"httpRequest\": {\"method\": \"POST\", \"path\": \"/redaction-probe\"},"
                + "\"httpResponse\": {\"statusCode\": 200, \"body\": \"ok\"}"
                + "}");

            // BEFORE: with redaction off (the default) the credential is retrievable verbatim
            sendWithAuthorization(port, "/redaction-probe");
            String logBefore = retrieveRecordedRequests(port);
            assertThat("baseline: with redactSecretsInLog off the Authorization value is retrievable verbatim",
                logBefore, containsString(SENSITIVE_CREDENTIAL));

            // enable redaction over the REST control plane only
            applyConfiguration(port, "{\"redactSecretsInLog\": true}");

            // AFTER: a newly recorded request must render with the credential masked
            sendWithAuthorization(port, "/redaction-probe");
            String logAfter = retrieveRecordedRequests(port);
            assertThat("redactSecretsInLog=true set via PUT /mockserver/configuration must mask the "
                    + "Authorization header in the retrieved log — leaking the credential here means the "
                    + "security control is unreachable from the REST API",
                logAfter, not(containsString(SENSITIVE_CREDENTIAL)));
            assertThat("the masked placeholder should be present in place of the credential",
                logAfter, containsString("***REDACTED***"));
        } finally {
            stop(mockServer);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // control-plane and data-plane helpers (deliberately raw HTTP so the test exercises the REST API
    // exactly as an external client would, rather than any in-process client convenience path)
    // ---------------------------------------------------------------------------------------------

    private static void applyConfiguration(int port, String configurationJson) throws IOException {
        Response response = send(port, "PUT", "/mockserver/configuration", configurationJson, null);
        assertThat("PUT /mockserver/configuration should be accepted, body: " + response.body,
            response.statusCode, is(200));
    }

    private static void createExpectation(int port, String expectationJson) throws IOException {
        Response response = send(port, "PUT", "/mockserver/expectation", expectationJson, null);
        assertThat("expectation creation should be accepted, body: " + response.body,
            response.statusCode, is(201));
    }

    private static String retrieveRecordedRequests(int port) throws IOException {
        return send(port, "PUT", "/mockserver/retrieve?type=REQUESTS&format=JSON", "", null).body;
    }

    private static void sendWithAuthorization(int port, String path) throws IOException {
        Response response = send(port, "POST", path, "{}", null, SENSITIVE_CREDENTIAL);
        assertThat("the probe request should be served so it is recorded in the log",
            response.statusCode, is(200));
    }

    private static Response send(int port, String method, String path, String body, String tenantHeader) throws IOException {
        return send(port, method, path, body, tenantHeader, null);
    }

    private static Response send(int port, String method, String path, String body, String tenantHeader, String authorization) throws IOException {
        return sendBytes(port, method, path, body.getBytes(StandardCharsets.UTF_8), tenantHeader, authorization);
    }

    private static Response sendBytes(int port, String method, String path, byte[] body, String tenantHeader) throws IOException {
        return sendBytes(port, method, path, body, tenantHeader, null);
    }

    private static Response sendBytes(int port, String method, String path, byte[] body, String tenantHeader, String authorization) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            if (tenantHeader != null) {
                connection.setRequestProperty("X-Tenant", tenantHeader);
            }
            if (authorization != null) {
                connection.setRequestProperty("Authorization", authorization);
            }
            if (body.length > 0) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                    out.flush();
                }
            }
            int statusCode;
            try {
                statusCode = connection.getResponseCode();
            } catch (IOException e) {
                // no status line arrived; -1 makes the failing assertion report the cause readably
                return new Response(-1, "connection failed: " + e.getMessage());
            }
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(statusCode, stream == null ? "" : new String(drain(stream), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readResource(String resource) throws IOException {
        try (InputStream in = ConfigurationRestApiEnforcementIntegrationTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing test resource " + resource);
            }
            return drain(in);
        }
    }

    private static byte[] drain(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static void stop(MockServer mockServer) {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    private static final class Response {
        final int statusCode;
        final String body;

        Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
