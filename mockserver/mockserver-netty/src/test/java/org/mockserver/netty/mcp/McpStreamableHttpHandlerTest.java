package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;

public class McpStreamableHttpHandlerTest {

    private EmbeddedChannel channel;
    private HttpState httpState;
    private LifeCycle server;
    private ObjectMapper objectMapper;
    private McpSessionManager sessionManager;

    @Before
    public void setUp() {
        server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        when(server.getLocalPorts()).thenReturn(Arrays.asList(1080));
        when(server.isRunning()).thenReturn(true);

        httpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class));
        sessionManager = new McpSessionManager(httpState.getMockServerLogger());
        McpStreamableHttpHandler handler = new McpStreamableHttpHandler(httpState, server, sessionManager);
        channel = new EmbeddedChannel(handler);
        objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    }

    private FullHttpResponse sendPost(String body) {
        return sendPost(body, null);
    }

    private FullHttpResponse sendPost(String body, String sessionId) {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/mockserver/mcp",
            Unpooled.copiedBuffer(body, StandardCharsets.UTF_8)
        );
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        if (sessionId != null) {
            request.headers().set("Mcp-Session-Id", sessionId);
        }
        channel.writeInbound(request);
        return awaitOutbound();
    }

    /**
     * Waits for a response to appear on the default channel's outbound queue. MCP POST processing
     * runs on a separate executor ({@code MCP_EXECUTOR}), so the response may not be available immediately.
     */
    private FullHttpResponse awaitOutbound() {
        return awaitOutboundFrom(channel);
    }

    /**
     * Waits for a response to appear on the given channel's outbound queue.
     */
    private FullHttpResponse awaitOutboundFrom(EmbeddedChannel ch) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            FullHttpResponse response = ch.readOutbound();
            if (response != null) {
                return response;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private FullHttpResponse sendGet() {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/mockserver/mcp"
        );
        channel.writeInbound(request);
        return channel.readOutbound();
    }

    private FullHttpResponse sendDelete(String sessionId) {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.DELETE,
            "/mockserver/mcp"
        );
        if (sessionId != null) {
            request.headers().set("Mcp-Session-Id", sessionId);
        }
        channel.writeInbound(request);
        return channel.readOutbound();
    }

    private JsonNode parseResponse(FullHttpResponse response) throws Exception {
        String content = response.content().toString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }

    @Test
    public void shouldHandleInitialize() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"clientInfo\":{\"name\":\"test\"}}}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.headers().get("Mcp-Session-Id"), notNullValue());

        JsonNode json = parseResponse(response);
        assertThat(json.path("jsonrpc").asText(), is("2.0"));
        assertThat(json.path("id").asInt(), is(1));
        assertThat(json.path("result").path("protocolVersion").asText(), is("2025-03-26"));
        assertThat(json.path("result").path("serverInfo").path("name").asText(), is("MockServer"));
        assertThat(json.path("result").path("capabilities").path("tools").path("listChanged").asBoolean(), is(false));

        response.release();
    }

    @Test
    public void shouldReturnSessionIdOnInitialize() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody);

        String sessionId = response.headers().get("Mcp-Session-Id");
        assertThat(sessionId, notNullValue());
        assertThat(sessionId.length() > 0, is(true));

        response.release();
    }

    @Test
    public void shouldRequireSessionIdForNonInitializeMethods() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("message").asText(), containsString("Missing or invalid Mcp-Session-Id"));

        response.release();
    }

    @Test
    public void shouldHandleToolsList() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        JsonNode tools = json.path("result").path("tools");
        assertThat(tools.isArray(), is(true));
        assertThat(tools.size() > 0, is(true));

        boolean foundCreateExpectation = false;
        for (JsonNode tool : tools) {
            if ("create_expectation".equals(tool.path("name").asText())) {
                foundCreateExpectation = true;
                assertThat(tool.path("inputSchema").path("type").asText(), is("object"));
                break;
            }
        }
        assertThat(foundCreateExpectation, is(true));

        response.release();
    }

    @Test
    public void shouldHandleToolsCallCreateExpectation() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "create_expectation");
        ObjectNode args = params.putObject("arguments");
        args.put("method", "GET");
        args.put("path", "/test");
        args.put("statusCode", 201);
        args.put("responseBody", "hello");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 3);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        JsonNode result = json.path("result");
        assertThat(result.path("content").isArray(), is(true));
        assertThat(result.path("isError").asBoolean(), is(false));

        String contentText = result.path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat(toolResult.path("status").asText(), is("created"));
        assertThat(toolResult.path("count").asInt(), is(1));

        response.release();
    }

    @Test
    public void shouldHandleToolsCallVerifyRequest() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "verify_request");
        ObjectNode args = params.putObject("arguments");
        args.put("method", "GET");
        args.put("path", "/nonexistent");
        args.put("atLeast", 0);
        args.put("atMost", 0);

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 4);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        String contentText = json.path("result").path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat(toolResult.path("verified").asBoolean(), is(true));

        response.release();
    }

    @Test
    public void shouldHandleToolsCallReset() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "reset");
        params.putObject("arguments");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 5);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        String contentText = json.path("result").path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat(toolResult.path("status").asText(), is("reset"));

        response.release();
    }

    @Test
    public void shouldHandleToolsCallGetStatus() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "get_status");
        params.putObject("arguments");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 6);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        String contentText = json.path("result").path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat(toolResult.path("running").asBoolean(), is(true));
        assertThat(toolResult.path("ports").get(0).asInt(), is(1080));

        response.release();
    }

    @Test
    public void shouldHandleResourcesList() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"resources/list\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        JsonNode resources = json.path("result").path("resources");
        assertThat(resources.isArray(), is(true));
        assertThat(resources.size(), is(4));

        response.release();
    }

    @Test
    public void shouldHandleResourcesRead() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "mockserver://expectations");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 8);
        rpcRequest.put("method", "resources/read");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        JsonNode contents = json.path("result").path("contents");
        assertThat(contents.isArray(), is(true));
        assertThat(contents.size(), is(1));
        assertThat(contents.get(0).path("uri").asText(), is("mockserver://expectations"));

        response.release();
    }

    @Test
    public void shouldHandleUnknownMethod() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"unknown/method\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.METHOD_NOT_FOUND));

        response.release();
    }

    @Test
    public void shouldHandleUnknownTool() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "nonexistent_tool");
        params.putObject("arguments");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 10);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.METHOD_NOT_FOUND));

        response.release();
    }

    @Test
    public void shouldHandleParseError() throws Exception {
        FullHttpResponse response = sendPost("invalid json{{{");

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.PARSE_ERROR));

        response.release();
    }

    @Test
    public void shouldHandleEmptyBody() throws Exception {
        FullHttpResponse response = sendPost("");

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.PARSE_ERROR));

        response.release();
    }

    @Test
    public void shouldHandleNotification() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.ACCEPTED));

        response.release();
    }

    @Test
    public void shouldHandlePing() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"ping\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("result").isObject(), is(true));
        assertThat(json.path("id").asInt(), is(11));

        response.release();
    }

    @Test
    public void shouldReturnMethodNotAllowedForGetRequest() throws Exception {
        FullHttpResponse response = sendGet();

        assertThat(response.status(), is(HttpResponseStatus.METHOD_NOT_ALLOWED));

        response.release();
    }

    @Test
    public void shouldHandleDeleteRequest() throws Exception {
        String sessionId = initializeAndGetSessionId();

        FullHttpResponse response = sendDelete(sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));

        response.release();
    }

    @Test
    public void shouldHandleBatchRequest() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "[" +
            "{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"ping\",\"params\":{}}," +
            "{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"ping\",\"params\":{}}" +
            "]";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.isArray(), is(true));
        assertThat(json.size(), is(2));

        response.release();
    }

    @Test
    public void shouldNotIncludeCorsHeaders() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN), is((String) null));
        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS), is((String) null));

        response.release();
    }

    @Test
    public void shouldPassNonMcpRequestToNextHandler() {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/some/other/path"
        );
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        assertThat(response, is((FullHttpResponse) null));
    }

    @Test
    public void shouldHandleToolsCallClearExpectations() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "clear_expectations");
        ObjectNode args = params.putObject("arguments");
        args.put("type", "ALL");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 12);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        String contentText = json.path("result").path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat(toolResult.path("status").asText(), is("cleared"));

        response.release();
    }

    @Test
    public void shouldHandleToolsCallRetrieveRecordedRequests() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "retrieve_recorded_requests");
        params.putObject("arguments");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 13);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("result").path("isError").asBoolean(), is(false));

        response.release();
    }

    @Test
    public void shouldHandleCreateExpectationWithResponseHeaders() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "create_expectation");
        ObjectNode args = params.putObject("arguments");
        args.put("method", "POST");
        args.put("path", "/api/data");
        args.put("statusCode", 200);
        args.put("responseBody", "{\"key\":\"value\"}");
        ObjectNode headers = args.putObject("responseHeaders");
        headers.put("Content-Type", "application/json");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 14);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        String contentText = json.path("result").path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat(toolResult.path("status").asText(), is("created"));

        response.release();
    }

    @Test
    public void shouldHandleCreateForwardExpectation() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "create_forward_expectation");
        ObjectNode args = params.putObject("arguments");
        args.put("path", "/api/proxy");
        args.put("host", "backend.example.com");
        args.put("port", 8080);
        args.put("scheme", "HTTP");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 15);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        String contentText = json.path("result").path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat(toolResult.path("status").asText(), is("created"));
        assertThat(toolResult.path("forwardHost").asText(), is("backend.example.com"));
        assertThat(toolResult.path("forwardPort").asInt(), is(8080));

        response.release();
    }

    @Test
    public void shouldHandleDebugRequestMismatch() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode createParams = objectMapper.createObjectNode();
        createParams.put("name", "create_expectation");
        ObjectNode createArgs = createParams.putObject("arguments");
        createArgs.put("method", "POST");
        createArgs.put("path", "/specific/path");
        createArgs.put("statusCode", 200);

        ObjectNode createRpc = objectMapper.createObjectNode();
        createRpc.put("jsonrpc", "2.0");
        createRpc.put("id", 100);
        createRpc.put("method", "tools/call");
        createRpc.set("params", createParams);
        FullHttpResponse createResponse = sendPost(objectMapper.writeValueAsString(createRpc), sessionId);
        createResponse.release();

        ObjectNode debugParams = objectMapper.createObjectNode();
        debugParams.put("name", "debug_request_mismatch");
        ObjectNode debugArgs = debugParams.putObject("arguments");
        debugArgs.put("method", "GET");
        debugArgs.put("path", "/wrong/path");

        ObjectNode debugRpc = objectMapper.createObjectNode();
        debugRpc.put("jsonrpc", "2.0");
        debugRpc.put("id", 101);
        debugRpc.put("method", "tools/call");
        debugRpc.set("params", debugParams);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(debugRpc), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        String contentText = json.path("result").path("content").get(0).path("text").asText();
        JsonNode toolResult = objectMapper.readTree(contentText);
        assertThat(toolResult.path("totalExpectations").asInt(), is(1));
        assertThat(toolResult.path("results").get(0).path("matches").asBoolean(), is(false));

        response.release();
    }

    @Test
    public void shouldHandleUnknownResource() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "mockserver://nonexistent");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 16);
        rpcRequest.put("method", "resources/read");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_PARAMS));

        response.release();
    }

    @Test
    public void shouldReturnNotFoundForDeleteWithInvalidSession() throws Exception {
        FullHttpResponse response = sendDelete("nonexistent-session-id");

        assertThat(response.status(), is(HttpResponseStatus.NOT_FOUND));

        response.release();
    }

    @Test
    public void shouldReturnNotFoundForDeleteWithNullSession() throws Exception {
        FullHttpResponse response = sendDelete(null);

        assertThat(response.status(), is(HttpResponseStatus.NOT_FOUND));

        response.release();
    }

    @Test
    public void shouldRejectUnauthenticatedPostWhenAuthEnabled() throws Exception {
        AuthenticationHandler authHandler = request -> false;
        httpState.setControlPlaneAuthenticationHandler(authHandler);

        EmbeddedChannel authChannel = new EmbeddedChannel(new McpStreamableHttpHandler(httpState, server, sessionManager));

        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/mockserver/mcp",
            Unpooled.copiedBuffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", StandardCharsets.UTF_8)
        );
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        authChannel.writeInbound(request);
        FullHttpResponse response = authChannel.readOutbound();

        assertThat(response.status(), is(HttpResponseStatus.UNAUTHORIZED));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("message").asText(), containsString("Unauthorized"));

        response.release();
        authChannel.close();
    }

    @Test
    public void shouldRejectUnauthenticatedGetWhenAuthEnabled() throws Exception {
        AuthenticationHandler authHandler = request -> false;
        httpState.setControlPlaneAuthenticationHandler(authHandler);

        EmbeddedChannel authChannel = new EmbeddedChannel(new McpStreamableHttpHandler(httpState, server, sessionManager));

        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/mockserver/mcp"
        );
        authChannel.writeInbound(request);
        FullHttpResponse response = authChannel.readOutbound();

        assertThat(response.status(), is(HttpResponseStatus.UNAUTHORIZED));

        response.release();
        authChannel.close();
    }

    @Test
    public void shouldRejectUnauthenticatedDeleteWhenAuthEnabled() throws Exception {
        AuthenticationHandler authHandler = request -> false;
        httpState.setControlPlaneAuthenticationHandler(authHandler);

        EmbeddedChannel authChannel = new EmbeddedChannel(new McpStreamableHttpHandler(httpState, server, sessionManager));

        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.DELETE,
            "/mockserver/mcp"
        );
        request.headers().set("Mcp-Session-Id", "some-session");
        authChannel.writeInbound(request);
        FullHttpResponse response = authChannel.readOutbound();

        assertThat(response.status(), is(HttpResponseStatus.UNAUTHORIZED));

        response.release();
        authChannel.close();
    }

    @Test
    public void shouldHandleAuthenticationException() throws Exception {
        AuthenticationHandler authHandler = request -> {
            throw new AuthenticationException("Invalid token");
        };
        httpState.setControlPlaneAuthenticationHandler(authHandler);

        EmbeddedChannel authChannel = new EmbeddedChannel(new McpStreamableHttpHandler(httpState, server, sessionManager));

        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/mockserver/mcp",
            Unpooled.copiedBuffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", StandardCharsets.UTF_8)
        );
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        authChannel.writeInbound(request);
        FullHttpResponse response = authChannel.readOutbound();

        assertThat(response.status(), is(HttpResponseStatus.UNAUTHORIZED));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("message").asText(), containsString("Unauthorized for control plane"));

        response.release();
        authChannel.close();
    }

    @Test
    public void shouldAllowAuthenticatedRequest() throws Exception {
        AuthenticationHandler authHandler = request -> true;
        httpState.setControlPlaneAuthenticationHandler(authHandler);

        EmbeddedChannel authChannel = new EmbeddedChannel(new McpStreamableHttpHandler(httpState, server, sessionManager));

        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/mockserver/mcp",
            Unpooled.copiedBuffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", StandardCharsets.UTF_8)
        );
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        authChannel.writeInbound(request);
        FullHttpResponse response = awaitOutboundFrom(authChannel);

        assertThat(response, notNullValue());
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.headers().get("Mcp-Session-Id"), notNullValue());

        response.release();
        authChannel.close();
    }

    @Test
    public void shouldReturnMethodNotAllowedForOptions() throws Exception {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.OPTIONS,
            "/mockserver/mcp"
        );
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        assertThat(response.status(), is(HttpResponseStatus.METHOD_NOT_ALLOWED));

        response.release();
    }

    @Test
    public void shouldEvictOldestSessionWhenMaxReached() throws Exception {
        // MAX_SESSIONS is 100 — send 101 initialize requests
        String[] sessionIds = new String[101];
        for (int i = 0; i < 101; i++) {
            String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":" + (i + 1) + ",\"method\":\"initialize\",\"params\":{}}";
            FullHttpResponse response = sendPost(requestBody);
            assertThat(response.status(), is(HttpResponseStatus.OK));
            sessionIds[i] = response.headers().get("Mcp-Session-Id");
            assertThat(sessionIds[i], notNullValue());
            response.release();
        }

        // Session manager should have evicted one session, so exactly 100 remain
        assertThat(sessionManager.size(), is(100));

        // At least one early session should have been evicted
        String toolsListRequest = "{\"jsonrpc\":\"2.0\",\"id\":200,\"method\":\"tools/list\",\"params\":{}}";
        int evictedCount = 0;
        for (int i = 0; i < 2; i++) {
            if (!sessionManager.isValidSession(sessionIds[i])) {
                evictedCount++;
            }
        }
        assertThat("at least one of the first two sessions should have been evicted", evictedCount > 0, is(true));

        String notificationBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        FullHttpResponse notifResponse = sendPost(notificationBody, sessionIds[100]);
        notifResponse.release();

        FullHttpResponse acceptedResponse = sendPost(toolsListRequest, sessionIds[100]);
        assertThat(acceptedResponse.status(), is(HttpResponseStatus.OK));
        JsonNode acceptedJson = parseResponse(acceptedResponse);
        assertThat(acceptedJson.path("result").path("tools").isArray(), is(true));
        acceptedResponse.release();
    }

    @Test
    public void shouldRejectInitializeInsideBatch() throws Exception {
        // Initialize first to get a valid session
        String sessionId = initializeAndGetSessionId();

        // Send a batch that includes an initialize request — should be rejected
        String requestBody = "[" +
            "{\"jsonrpc\":\"2.0\",\"id\":40,\"method\":\"initialize\",\"params\":{}}," +
            "{\"jsonrpc\":\"2.0\",\"id\":41,\"method\":\"ping\",\"params\":{}}" +
            "]";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.isArray(), is(true));
        assertThat(json.size(), is(2));

        // First element should be an error for initialize-in-batch
        assertThat(json.get(0).path("error").path("message").asText(), containsString("must be sent as a single request"));
        // Second element should succeed (valid session)
        assertThat(json.get(1).path("result").isObject(), is(true));

        response.release();
    }

    @Test
    public void shouldRejectBatchRequestWithInvalidSession() throws Exception {
        // Send a batch request without a valid session ID — non-initialize requests should fail
        String requestBody = "[" +
            "{\"jsonrpc\":\"2.0\",\"id\":30,\"method\":\"tools/list\",\"params\":{}}," +
            "{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"ping\",\"params\":{}}" +
            "]";
        FullHttpResponse response = sendPost(requestBody, "nonexistent-session");

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.isArray(), is(true));
        assertThat(json.size(), is(2));

        // Both should be errors due to invalid session
        for (int i = 0; i < json.size(); i++) {
            assertThat(json.get(i).path("error").path("message").asText(), containsString("Missing or invalid Mcp-Session-Id"));
        }

        response.release();
    }

    @Test
    public void shouldRejectBatchRequestWithNoSession() throws Exception {
        // Send a batch request without any session header
        String requestBody = "[" +
            "{\"jsonrpc\":\"2.0\",\"id\":32,\"method\":\"ping\",\"params\":{}}" +
            "]";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.isArray(), is(true));
        assertThat(json.size(), is(1));
        assertThat(json.get(0).path("error").path("message").asText(), containsString("Missing or invalid Mcp-Session-Id"));

        response.release();
    }

    @Test
    public void shouldReturnServiceUnavailableWhenExecutorShutdown() throws Exception {
        // Create a new session manager, shut it down, then attempt a POST
        McpSessionManager shutdownManager = new McpSessionManager(httpState.getMockServerLogger());
        shutdownManager.shutdown();
        // Wait for the executor to finish shutting down
        Thread.sleep(100);

        McpStreamableHttpHandler shutdownHandler = new McpStreamableHttpHandler(httpState, server, shutdownManager);
        EmbeddedChannel shutdownChannel = new EmbeddedChannel(shutdownHandler);

        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/mockserver/mcp",
            Unpooled.copiedBuffer(requestBody, StandardCharsets.UTF_8)
        );
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        shutdownChannel.writeInbound(request);

        FullHttpResponse response = awaitOutboundFrom(shutdownChannel);
        assertThat(response, notNullValue());
        assertThat(response.status(), is(HttpResponseStatus.SERVICE_UNAVAILABLE));

        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("message").asText(), containsString("Server is busy"));

        response.release();
        shutdownChannel.close();
    }

    @Test
    public void shouldRejectMissingJsonrpcVersion() throws Exception {
        String requestBody = "{\"method\":\"tools/list\",\"id\":1}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_REQUEST));

        response.release();
    }

    @Test
    public void shouldRejectWrongJsonrpcVersion() throws Exception {
        String requestBody = "{\"jsonrpc\":\"1.0\",\"method\":\"tools/list\",\"id\":1}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_REQUEST));

        response.release();
    }

    @Test
    public void shouldRejectMissingMethod() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_REQUEST));

        response.release();
    }

    @Test
    public void shouldRejectEmptyBatch() throws Exception {
        String requestBody = "[]";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_REQUEST));
        assertThat(json.path("error").path("message").asText(), containsString("batch must not be empty"));

        response.release();
    }

    @Test
    public void shouldRespondToRequestWithNullId() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":null}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.has("result"), is(true));
        assertThat(json.get("id").isNull(), is(true));

        response.release();
    }

    @Test
    public void shouldTreatMissingIdAsNotification() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.ACCEPTED));
        String content = response.content().toString(StandardCharsets.UTF_8);
        assertThat(content.isEmpty(), is(true));

        response.release();
    }

    @Test
    public void shouldRejectToolCallBeforeInitialized() throws Exception {
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        FullHttpResponse initResponse = sendPost(initBody);
        String sessionId = initResponse.headers().get("Mcp-Session-Id");
        initResponse.release();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("message").asText(), containsString("Missing or invalid Mcp-Session-Id"));

        response.release();
    }

    @Test
    public void shouldRejectNotificationWithoutSession() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));

        response.release();
    }

    @Test
    public void shouldRejectNotificationWithInvalidSession() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, "invalid-id");

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));

        response.release();
    }

    @Test
    public void shouldRejectArbitraryNotificationBeforeInitialized() throws Exception {
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        FullHttpResponse initResponse = sendPost(initBody);
        String sessionId = initResponse.headers().get("Mcp-Session-Id");
        initResponse.release();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));

        response.release();
    }

    private String initializeAndGetSessionId() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody);
        String sessionId = response.headers().get("Mcp-Session-Id");
        response.release();

        String notificationBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        FullHttpResponse notifResponse = sendPost(notificationBody, sessionId);
        notifResponse.release();

        return sessionId;
    }
}
