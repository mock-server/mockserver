package org.mockserver.netty.http3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.configuration.Configuration;
import org.mockserver.mock.HttpState;
import org.mockserver.netty.MockServer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.testing.socket.Ipv4DatagramChannelFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;

/**
 * Integration tests for MCP (Model Context Protocol) over HTTP/3.
 * <p>
 * Verifies that MCP JSON-RPC requests sent over QUIC to {@code /mockserver/mcp}
 * are processed correctly, including session management (initialize, session id,
 * tool listing, tool calls, resource listing, ping, and DELETE).
 * <p>
 * These tests require the native QUIC transport (BoringSSL) and skip gracefully
 * on platforms where it is not available.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3McpIntegrationTest {

    private MockServer mockServer;
    private NioEventLoopGroup clientGroup;
    private final ObjectMapper objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();

    @Before
    public void setUp() {
        assumeQuicAvailable();
    }

    @After
    public void tearDown() {
        if (mockServer != null) {
            mockServer.stop();
            mockServer = null;
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully();
            clientGroup = null;
        }
    }

    @Test
    public void shouldInitializeMcpSessionOverHttp3() throws Exception {
        startMockServer();

        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"clientInfo\":{\"name\":\"test-h3\"}}}";

        Http3ResponseCapture result = sendMcpRequest("POST", initBody, null);

        assertThat("status should be 200", result.status, is("200"));
        JsonNode json = objectMapper.readTree(result.body);
        assertThat("should have jsonrpc field", json.path("jsonrpc").asText(), is("2.0"));
        assertThat("should have id=1", json.path("id").asInt(), is(1));
        assertThat("should have protocolVersion", json.path("result").path("protocolVersion").asText(), is("2025-03-26"));
        assertThat("should have server name", json.path("result").path("serverInfo").path("name").asText(), is("MockServer"));
        assertThat("should have instructions", json.path("result").path("instructions").asText(), containsString("MockServer is an HTTP(S) mock server"));
        assertThat("should have session id in response header", result.headers.get("mcp-session-id"), is(notNullValue()));
    }

    @Test
    public void shouldListToolsOverHttp3() throws Exception {
        startMockServer();

        // initialize first
        String sessionId = initializeSession();

        // list tools
        String listBody = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        Http3ResponseCapture result = sendMcpRequest("POST", listBody, sessionId);

        assertThat("status should be 200", result.status, is("200"));
        JsonNode json = objectMapper.readTree(result.body);
        JsonNode tools = json.path("result").path("tools");
        assertThat("tools should be an array", tools.isArray(), is(true));
        assertThat("should have at least one tool", tools.size(), greaterThan(0));

        // verify create_expectation is among the tools
        boolean foundCreateExpectation = false;
        for (JsonNode tool : tools) {
            if ("create_expectation".equals(tool.path("name").asText())) {
                foundCreateExpectation = true;
                break;
            }
        }
        assertThat("should have create_expectation tool", foundCreateExpectation, is(true));
    }

    @Test
    public void shouldCallToolOverHttp3() throws Exception {
        startMockServer();

        String sessionId = initializeSession();

        // call the 'reset' tool (simple, no dependencies)
        String callBody = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"reset\",\"arguments\":{}}}";
        Http3ResponseCapture result = sendMcpRequest("POST", callBody, sessionId);

        assertThat("status should be 200", result.status, is("200"));
        JsonNode json = objectMapper.readTree(result.body);
        JsonNode resultNode = json.path("result");
        assertThat("should have content array", resultNode.path("content").isArray(), is(true));
        assertThat("should not be error", resultNode.path("isError").asBoolean(), is(false));

        String contentText = resultNode.path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat("should report reset status", toolResult.path("status").asText(), is("reset"));
    }

    @Test
    public void shouldListResourcesOverHttp3() throws Exception {
        startMockServer();

        String sessionId = initializeSession();

        String listBody = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"resources/list\",\"params\":{}}";
        Http3ResponseCapture result = sendMcpRequest("POST", listBody, sessionId);

        assertThat("status should be 200", result.status, is("200"));
        JsonNode json = objectMapper.readTree(result.body);
        JsonNode resources = json.path("result").path("resources");
        assertThat("resources should be an array", resources.isArray(), is(true));
        assertThat("should have resources", resources.size(), greaterThan(0));
    }

    @Test
    public void shouldHandlePingOverHttp3() throws Exception {
        startMockServer();

        String sessionId = initializeSession();

        String pingBody = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\",\"params\":{}}";
        Http3ResponseCapture result = sendMcpRequest("POST", pingBody, sessionId);

        assertThat("status should be 200", result.status, is("200"));
        JsonNode json = objectMapper.readTree(result.body);
        assertThat("should have result object", json.path("result").isObject(), is(true));
        assertThat("should have id=5", json.path("id").asInt(), is(5));
    }

    @Test
    public void shouldRejectToolsListWithoutSession() throws Exception {
        startMockServer();

        String listBody = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        Http3ResponseCapture result = sendMcpRequest("POST", listBody, null);

        // previously asserted 200; a request omitting a required Mcp-Session-Id is a 400 per
        // MCP 2025-06-18 basic/transports, and HTTP/3 must answer the same as HTTP/1.1
        assertThat("status should be 400", result.status, is("400"));
        JsonNode json = objectMapper.readTree(result.body);
        assertThat("should have error about session", json.path("error").path("message").asText(),
            containsString("Missing Mcp-Session-Id"));
    }

    @Test
    public void shouldHandleDeleteSessionOverHttp3() throws Exception {
        startMockServer();

        // initialize first
        String sessionId = initializeSession();

        // DELETE the session
        Http3ResponseCapture result = sendMcpRequest("DELETE", null, sessionId);

        assertThat("DELETE should return 200", result.status, is("200"));

        // verify the session is gone -- tools/list should now fail
        String listBody = "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/list\",\"params\":{}}";
        Http3ResponseCapture afterDelete = sendMcpRequest("POST", listBody, sessionId);

        // previously asserted only the error message; a terminated session MUST be 404 per
        // MCP 2025-06-18 basic/transports so the client knows to start a new session
        assertThat("terminated session should return 404", afterDelete.status, is("404"));
        JsonNode json = objectMapper.readTree(afterDelete.body);
        assertThat("should reject request after session deletion",
            json.path("error").path("message").asText(),
            containsString("Unknown or terminated Mcp-Session-Id"));
    }

    @Test
    public void shouldReturnMethodNotAllowedForGetOverHttp3() throws Exception {
        startMockServer();

        Http3ResponseCapture result = sendMcpRequest("GET", null, null);

        assertThat("GET should return 405", result.status, is("405"));
    }

    @Test
    public void shouldHandleParseErrorOverHttp3() throws Exception {
        startMockServer();

        Http3ResponseCapture result = sendMcpRequest("POST", "invalid json{{{", null);

        assertThat("should return 400 for parse error", result.status, is("400"));
        JsonNode json = objectMapper.readTree(result.body);
        assertThat("should have parse error code", json.path("error").path("code").asInt(), is(-32700));
    }

    @Test
    public void shouldCreateExpectationOverHttp3() throws Exception {
        startMockServer();

        String sessionId = initializeSession();

        String callBody = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"create_expectation\",\"arguments\":{\"method\":\"GET\",\"path\":\"/h3-test\",\"statusCode\":201,\"responseBody\":\"hello-h3\"}}}";
        Http3ResponseCapture result = sendMcpRequest("POST", callBody, sessionId);

        assertThat("status should be 200", result.status, is("200"));
        JsonNode json = objectMapper.readTree(result.body);
        String contentText = json.path("result").path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat("should report created status", toolResult.path("status").asText(), is("created"));
        assertThat("should have created 1 expectation", toolResult.path("count").asInt(), is(1));
    }

    @Test
    public void shouldHandleBatchRequestOverHttp3() throws Exception {
        startMockServer();

        String sessionId = initializeSession();

        String batchBody = "[" +
            "{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"ping\",\"params\":{}}," +
            "{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"ping\",\"params\":{}}" +
            "]";
        Http3ResponseCapture result = sendMcpRequest("POST", batchBody, sessionId);

        assertThat("status should be 200", result.status, is("200"));
        JsonNode json = objectMapper.readTree(result.body);
        assertThat("should return array", json.isArray(), is(true));
        assertThat("should have 2 responses", json.size(), is(2));
    }

    @Test
    public void shouldHandleNotificationOverHttp3() throws Exception {
        startMockServer();

        // initialize (get session)
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        Http3ResponseCapture initResult = sendMcpRequest("POST", initBody, null);
        String sessionId = initResult.headers.get("mcp-session-id");
        assertThat("should have session id", sessionId, is(notNullValue()));

        // send notifications/initialized notification
        String notifBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        Http3ResponseCapture notifResult = sendMcpRequest("POST", notifBody, sessionId);

        assertThat("notification should return 202", notifResult.status, is("202"));
    }

    // ---- control-plane authentication over HTTP/3 ----

    @Test
    public void shouldRejectUnauthenticatedMcpPostOverHttp3() throws Exception {
        startMockServer();

        // install an auth handler that rejects all requests
        HttpState httpState = getHttpState(mockServer);
        httpState.setControlPlaneAuthenticationHandler(request -> false);

        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        Http3ResponseCapture result = sendMcpRequest("POST", initBody, null);

        assertThat("should return 401 when auth fails", result.status, is("401"));
        JsonNode json = objectMapper.readTree(result.body);
        assertThat("should have error about unauthorized",
            json.path("error").path("message").asText(), containsString("Unauthorized"));
    }

    @Test
    public void shouldAcceptAuthenticatedMcpPostOverHttp3() throws Exception {
        startMockServer();

        // install an auth handler that accepts all requests
        HttpState httpState = getHttpState(mockServer);
        httpState.setControlPlaneAuthenticationHandler(request -> true);

        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        Http3ResponseCapture result = sendMcpRequest("POST", initBody, null);

        assertThat("should return 200 when auth succeeds", result.status, is("200"));
        assertThat("should have session id", result.headers.get("mcp-session-id"), is(notNullValue()));
    }

    @Test
    public void shouldRejectUnauthenticatedMcpGetOverHttp3() throws Exception {
        startMockServer();

        HttpState httpState = getHttpState(mockServer);
        httpState.setControlPlaneAuthenticationHandler(request -> false);

        Http3ResponseCapture result = sendMcpRequest("GET", null, null);

        assertThat("GET should return 401 when auth fails", result.status, is("401"));
    }

    @Test
    public void shouldRejectUnauthenticatedMcpDeleteOverHttp3() throws Exception {
        startMockServer();

        HttpState httpState = getHttpState(mockServer);
        httpState.setControlPlaneAuthenticationHandler(request -> false);

        Http3ResponseCapture result = sendMcpRequest("DELETE", null, "some-session");

        assertThat("DELETE should return 401 when auth fails", result.status, is("401"));
    }

    @Test
    public void shouldAllowOptionsWithoutAuthOverHttp3() throws Exception {
        startMockServer();

        // install an auth handler that rejects all requests
        HttpState httpState = getHttpState(mockServer);
        httpState.setControlPlaneAuthenticationHandler(request -> false);

        // OPTIONS with Origin should still succeed (CORS preflight exempt from auth)
        Map<String, String> extraHeaders = new ConcurrentHashMap<>();
        extraHeaders.put("origin", "http://localhost:3000");
        extraHeaders.put("access-control-request-method", "POST");
        Http3ResponseCapture result = doSendHttp3Request(
            mockServer.getHttp3Port(), "OPTIONS", "/mockserver/mcp", null, extraHeaders);

        assertThat("OPTIONS should return 200 even with auth enabled", result.status, is("200"));
    }

    // ---- control-plane authorization over HTTP/3 ----

    @Test
    public void shouldRejectMutatingToolWhenAuthorizationEnabledAndCallerLacksMutateRoleOverHttp3() throws Exception {
        // mirror the TCP-path test (McpStreamableHttpHandlerTest
        // .shouldRejectMutatingToolWhenAuthorizationEnabledAndCallerLacksMutateRole) over HTTP/3:
        // a verified READ-only principal calling a MUTATE tool must be denied.
        java.util.Map<String, org.mockserver.authentication.authorization.ControlPlaneRole> mapping = new java.util.LinkedHashMap<>();
        mapping.put("readers", org.mockserver.authentication.authorization.ControlPlaneRole.READ);
        mapping.put("mutators", org.mockserver.authentication.authorization.ControlPlaneRole.MUTATE);
        startMockServer(configuration()
            .controlPlaneAuthorizationEnabled(true)
            .controlPlaneScopeMapping(mapping));

        HttpState httpState = getHttpState(mockServer);
        httpState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(org.mockserver.model.HttpRequest request) {
                return true;
            }

            @Override
            public org.mockserver.authentication.AuthenticationResult authenticate(org.mockserver.model.HttpRequest request) {
                return org.mockserver.authentication.AuthenticationResult.authenticated(
                    "principal", "verified-oidc", java.util.Map.of(), java.util.Set.of("readers"));
            }
        });

        String sessionId = initializeSession();

        String callBody = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"create_expectation\",\"arguments\":{\"method\":\"GET\",\"path\":\"/h3-forbidden\",\"statusCode\":201}}}";
        Http3ResponseCapture result = sendMcpRequest("POST", callBody, sessionId);

        assertThat("status should be 200 (JSON-RPC error transport)", result.status, is("200"));
        JsonNode json = objectMapper.readTree(result.body);
        assertThat("denied mutating tool returns the forbidden JSON-RPC error",
            json.path("error").path("message").asText(), containsString("Forbidden for control plane"));
        assertThat("denied tool call produces no result",
            json.path("result").isMissingNode() || json.path("result").isNull(), is(true));

        // and the expectation was NOT created — a read-role principal could read it back, but here
        // we assert the mutating call did not take effect by confirming no expectation matches
        assertThat("the forbidden tool must not have mutated state",
            httpState.firstMatchingExpectation(org.mockserver.model.HttpRequest.request().withMethod("GET").withPath("/h3-forbidden")),
            is(nullValue()));
    }

    @Test
    public void shouldAuthorizeToolCallFromTheSettingsItWasAuthenticatedUnderOverHttp3() throws Exception {
        // the same window as McpToolAuthorizationSnapshotTest over TCP: a PUT disabling authorization lands
        // after the tool call is authenticated and before it is authorized
        startMockServer(configuration()
            .controlPlaneAuthorizationEnabled(true)
            .controlPlaneScopeMapping(java.util.Map.of("readers", org.mockserver.authentication.authorization.ControlPlaneRole.READ)));

        HttpState httpState = getHttpState(mockServer);
        Configuration serverConfiguration = httpState.getConfiguration();
        java.util.concurrent.atomic.AtomicBoolean putDuringNextAuthentication = new java.util.concurrent.atomic.AtomicBoolean();
        httpState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(org.mockserver.model.HttpRequest request) {
                return true;
            }

            @Override
            public org.mockserver.authentication.AuthenticationResult authenticate(org.mockserver.model.HttpRequest request) {
                if (putDuringNextAuthentication.getAndSet(false)) {
                    serverConfiguration.controlPlaneAuthorizationEnabled(false);
                }
                return org.mockserver.authentication.AuthenticationResult.authenticated(
                    "principal", "verified-oidc", java.util.Map.of(), java.util.Set.of("readers"));
            }
        });

        String sessionId = initializeSession();
        putDuringNextAuthentication.set(true);
        String callBody = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"create_expectation\",\"arguments\":{\"method\":\"GET\",\"path\":\"/h3-during-put\",\"statusCode\":201}}}";
        Http3ResponseCapture result = sendMcpRequest("POST", callBody, sessionId);

        assertThat("the PUT ran between authentication and authorization", serverConfiguration.controlPlaneAuthorizationEnabled(), is(false));
        assertThat("status should be 200 (JSON-RPC error transport)", result.status, is("200"));
        assertThat("the tool is authorized from the settings it was authenticated under, which forbid it",
            objectMapper.readTree(result.body).path("error").path("message").asText(), containsString("Forbidden for control plane"));
        assertThat(httpState.firstMatchingExpectation(org.mockserver.model.HttpRequest.request().withMethod("GET").withPath("/h3-during-put")),
            is(nullValue()));
    }

    // ---- CORS headers over HTTP/3 ----

    @Test
    public void shouldIncludeCorsHeadersWhenOriginPresent() throws Exception {
        startMockServer();

        Map<String, String> extraHeaders = new ConcurrentHashMap<>();
        extraHeaders.put("content-type", "application/json");
        extraHeaders.put("origin", "http://localhost:3000");
        extraHeaders.put("access-control-request-headers", "content-type, mcp-session-id");

        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        Http3ResponseCapture result = doSendHttp3Request(
            mockServer.getHttp3Port(), "POST", "/mockserver/mcp",
            initBody.getBytes(StandardCharsets.UTF_8), extraHeaders);

        assertThat("should return 200", result.status, is("200"));
        assertThat("should echo origin",
            result.headers.get("access-control-allow-origin"), is("http://localhost:3000"));
        assertThat("should have allow-methods",
            result.headers.get("access-control-allow-methods"), containsString("POST"));
        assertThat("should expose Mcp-Session-Id",
            result.headers.get("access-control-expose-headers"), containsString("Mcp-Session-Id"));
        assertThat("should have session id", result.headers.get("mcp-session-id"), is(notNullValue()));
    }

    @Test
    public void shouldNotIncludeCorsHeadersForAForeignOrNullOrigin() throws Exception {
        startMockServer();

        for (String origin : new String[]{"https://attacker.example", "null"}) {
            Map<String, String> preflightHeaders = new ConcurrentHashMap<>();
            preflightHeaders.put("origin", origin);
            preflightHeaders.put("access-control-request-method", "POST");
            preflightHeaders.put("access-control-request-headers", "content-type, mcp-session-id");
            Http3ResponseCapture preflight = doSendHttp3Request(
                mockServer.getHttp3Port(), "OPTIONS", "/mockserver/mcp", null, preflightHeaders);

            Map<String, String> postHeaders = new ConcurrentHashMap<>();
            postHeaders.put("content-type", "application/json");
            postHeaders.put("origin", origin);
            String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
            Http3ResponseCapture post = doSendHttp3Request(
                mockServer.getHttp3Port(), "POST", "/mockserver/mcp",
                initBody.getBytes(StandardCharsets.UTF_8), postHeaders);

            assertThat(origin, preflight.status, is("200"));
            assertThat(origin, preflight.headers.keySet().stream().anyMatch(name -> name.startsWith("access-control-")), is(false));
            assertThat(origin, post.status, is("403"));
            assertThat(origin, post.headers.containsKey("access-control-allow-origin"), is(false));
            assertThat(origin, post.headers.containsKey("mcp-session-id"), is(false));
        }
    }

    @Test
    public void shouldNotIncludeCorsHeadersWhenNoOrigin() throws Exception {
        startMockServer();

        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        Http3ResponseCapture result = sendMcpRequest("POST", initBody, null);

        assertThat("should return 200", result.status, is("200"));
        assertThat("should NOT have access-control-allow-origin",
            result.headers.containsKey("access-control-allow-origin"), is(false));
    }

    // ---- helper methods ----

    /**
     * Access the protected httpState field from the running MockServer via reflection.
     * Required for integration tests that need to install a control-plane auth handler
     * on a live server instance.
     */
    @Test
    public void shouldAuthenticateMcpWithBasicOverHttp3AndTrackGuessesByPeer() throws Exception {
        startMockServer(configuration()
            .controlPlaneBasicAuthenticationRequired(true)
            .controlPlaneBasicAuthenticationCredentials("admin:" + org.mockserver.authentication.basic.PasswordHash.bcrypt("s3cret", 4)));
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        String correct = basic("admin", "s3cret");

        assertThat("HTTP/3 is always TLS, so Basic is accepted", sendMcpRequest("POST", initBody, null, correct).status, is("200"));
        for (int i = 0; i < 5; i++) {
            assertThat(sendMcpRequest("POST", initBody, null, basic("admin", "near-miss-" + i)).status, is("401"));
        }
        org.mockserver.authentication.AuthenticationHandler handler = getHttpState(mockServer).getControlPlaneAuthenticationHandler();
        java.lang.reflect.Method trackedSourceKeys = handler.getClass().getDeclaredMethod("trackedSourceKeys");
        trackedSourceKeys.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> tracked = (java.util.Set<String>) trackedSourceKeys.invoke(handler);
        assertThat("the guesses were recorded under one source", tracked.size(), is(1));
        String source = tracked.iterator().next();
        assertThat("the guesses were recorded against the client's address, not the shared record for requests with no peer", source, org.hamcrest.Matchers.not("unknown"));
        assertThat("the recorded source is the client's loopback address", java.net.InetAddress.getByName(source).isLoopbackAddress(), is(true));
        assertThat("an already verified credential is not locked out", sendMcpRequest("POST", initBody, null, correct).status, is("200"));

        java.util.concurrent.CompletableFuture<java.util.List<org.mockserver.log.model.LogEntry>> logged = new java.util.concurrent.CompletableFuture<>();
        getHttpState(mockServer).getMockServerLog().retrieveMessageLogEntries(null, logged::complete);
        for (org.mockserver.log.model.LogEntry entry : logged.get(30, java.util.concurrent.TimeUnit.SECONDS)) {
            String text = entry.getMessage() + java.util.Arrays.toString(entry.getHttpRequests());
            assertThat(text, org.hamcrest.Matchers.not(containsString("near-miss")));
            assertThat(text, org.hamcrest.Matchers.not(containsString(basic("admin", "near-miss-0").substring("Basic ".length()))));
            assertThat(text, org.hamcrest.Matchers.not(containsString(correct.substring("Basic ".length()))));
        }
    }

    private static String basic(String username, String password) {
        return "Basic " + java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private Http3ResponseCapture sendMcpRequest(String method, String body, String sessionId, String authorization) throws Exception {
        Map<String, String> extraHeaders = new ConcurrentHashMap<>();
        extraHeaders.put("content-type", "application/json");
        extraHeaders.put("authorization", authorization);
        return doSendHttp3Request(mockServer.getHttp3Port(), method, "/mockserver/mcp", body.getBytes(StandardCharsets.UTF_8), extraHeaders);
    }

    private static HttpState getHttpState(MockServer server) {
        try {
            java.lang.reflect.Field field = server.getClass().getSuperclass().getDeclaredField("httpState");
            field.setAccessible(true);
            return (HttpState) field.get(server);
        } catch (Exception e) {
            throw new RuntimeException("failed to access httpState via reflection", e);
        }
    }

    private void startMockServer() {
        startMockServer(configuration());
    }

    /**
     * Start an HTTP/3 MockServer with the given (partially configured) {@link Configuration};
     * the HTTP/3 port and proxy-disable settings are always applied. Used by the control-plane
     * authorization tests, which need the server to run with authorization enabled and a scope
     * mapping.
     */
    private void startMockServer(Configuration config) {
        config.http3MaxIdleTimeout(30000L)
            .attemptToProxyIfNoMatchingExpectation(false);
        mockServer = startWithHttp3(config);
    }

    /**
     * Initialize an MCP session and return the session ID. Sends both the
     * 'initialize' request and the 'notifications/initialized' notification
     * (required before tools/list etc. are accepted).
     */
    private String initializeSession() throws Exception {
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        Http3ResponseCapture initResult = sendMcpRequest("POST", initBody, null);
        String sessionId = initResult.headers.get("mcp-session-id");

        // send notifications/initialized to mark the session as initialized
        String notifBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        sendMcpRequest("POST", notifBody, sessionId);

        return sessionId;
    }

    private Http3ResponseCapture sendMcpRequest(String method, String body, String sessionId) throws Exception {
        Map<String, String> extraHeaders = new ConcurrentHashMap<>();
        if (sessionId != null) {
            extraHeaders.put("mcp-session-id", sessionId);
        }
        if (body != null) {
            extraHeaders.put("content-type", "application/json");
        }
        return doSendHttp3Request(
            mockServer.getHttp3Port(), method, "/mockserver/mcp",
            body != null ? body.getBytes(StandardCharsets.UTF_8) : null,
            extraHeaders
        );
    }

    static class Http3ResponseCapture {
        final String status;
        final String body;
        final Map<String, String> headers;

        Http3ResponseCapture(String status, String body, Map<String, String> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }
    }

    private Http3ResponseCapture doSendHttp3Request(
        int port, String method, String path, byte[] body,
        Map<String, String> extraHeaders
    ) throws Exception {
        if (clientGroup != null) {
            clientGroup.shutdownGracefully().sync();
        }
        clientGroup = new NioEventLoopGroup(1);

        QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();

        Channel clientChannel = new Bootstrap()
            .group(clientGroup)
            .channelFactory(Ipv4DatagramChannelFactory.INSTANCE)
            .handler(Http3.newQuicClientCodecBuilder()
                .sslContext(clientSslContext)
                .maxIdleTimeout(30000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamsBidirectional(100)
                .build())
            .bind(0)
            .sync()
            .channel();

        QuicChannel quicChannel = QuicChannel.newBootstrap(clientChannel)
            .handler(new Http3ClientConnectionHandler())
            .remoteAddress(new InetSocketAddress("127.0.0.1", port))
            .connect()
            .get(15, TimeUnit.SECONDS);

        BlockingQueue<String> statusQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> bodyQueue = new LinkedBlockingQueue<>();
        Map<String, String> responseHeaders = new ConcurrentHashMap<>();

        QuicStreamChannel requestStream = Http3.newRequestStream(
            quicChannel,
            new Http3RequestStreamInboundHandler() {
                private final StringBuilder bodyBuilder = new StringBuilder();

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
                    CharSequence status = headersFrame.headers().status();
                    statusQueue.offer(status != null ? status.toString() : "null");
                    headersFrame.headers().forEach(entry -> {
                        String name = entry.getKey().toString();
                        if (!name.startsWith(":")) {
                            responseHeaders.put(name, entry.getValue().toString());
                        }
                    });
                }

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
                    ByteBuf content = dataFrame.content();
                    bodyBuilder.append(content.toString(StandardCharsets.UTF_8));
                    content.release();
                }

                @Override
                protected void channelInputClosed(ChannelHandlerContext ctx) {
                    bodyQueue.offer(bodyBuilder.toString());
                    ctx.close();
                }
            }
        ).sync().getNow();

        // send request headers
        DefaultHttp3HeadersFrame requestHeaders = new DefaultHttp3HeadersFrame();
        requestHeaders.headers().method(method);
        requestHeaders.headers().path(path);
        requestHeaders.headers().scheme("https");
        requestHeaders.headers().authority("127.0.0.1:" + port);

        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                requestHeaders.headers().add(entry.getKey(), entry.getValue());
            }
        }

        if (body != null && body.length > 0) {
            requestStream.writeAndFlush(requestHeaders).sync();
            requestStream.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(body)))
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                .sync();
        } else {
            requestStream.writeAndFlush(requestHeaders)
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                .sync();
        }

        String status = statusQueue.poll(20, TimeUnit.SECONDS);
        String responseBody = bodyQueue.poll(20, TimeUnit.SECONDS);

        quicChannel.close().sync();
        clientChannel.close().sync();

        return new Http3ResponseCapture(
            status != null ? status : "null",
            responseBody != null ? responseBody : "",
            responseHeaders
        );
    }

    private static void assumeQuicAvailable() {
        try {
            boolean available = io.netty.handler.codec.quic.Quic.isAvailable();
            Assume.assumeTrue(
                "native QUIC transport not available on this platform -- skipping HTTP/3 MCP test",
                available
            );
        } catch (Throwable t) {
            Assume.assumeNoException(
                "native QUIC transport failed to load -- skipping HTTP/3 MCP test",
                t
            );
        }
    }
}
