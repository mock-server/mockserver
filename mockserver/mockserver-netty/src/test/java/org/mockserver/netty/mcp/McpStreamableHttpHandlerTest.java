package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.After;
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
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;

public class McpStreamableHttpHandlerTest {

    private final List<HttpState> httpStates = new ArrayList<>();

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

    @After
    public void stopHttpStates() {
        httpState.stop();
        httpStates.forEach(HttpState::stop);
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
    public void shouldIncludeAgentInstructionsInInitializeResult() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"clientInfo\":{\"name\":\"test\"}}}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.OK));

        JsonNode json = parseResponse(response);
        String instructions = json.path("result").path("instructions").asText();
        assertThat(instructions, containsString("MockServer is an HTTP(S) mock server and proxy"));
        assertThat(instructions, containsString("explain_unmatched_requests"));
        assertThat(instructions, containsString("explain_request"));
        assertThat(instructions, containsString("run_contract_test"));
        assertThat(instructions, containsString("run_resiliency_test"));
        assertThat(instructions, containsString("record_llm_fixtures"));

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
    public void shouldNegotiateLatestProtocolVersionWhenClientRequestsIt() throws Exception {
        // A client that requests 2025-06-18 gets 2025-06-18 back.
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"clientInfo\":{\"name\":\"test\"}}}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.headers().get("Mcp-Session-Id"), notNullValue());
        JsonNode json = parseResponse(response);
        assertThat(json.path("result").path("protocolVersion").asText(), is("2025-06-18"));

        response.release();
    }

    @Test
    public void shouldNegotiateOlderProtocolVersionForBackwardCompatibleClient() throws Exception {
        // A client still on 2025-03-26 keeps getting 2025-03-26 (back-compat).
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"clientInfo\":{\"name\":\"test\"}}}";
        FullHttpResponse response = sendPost(requestBody);

        JsonNode json = parseResponse(response);
        assertThat(json.path("result").path("protocolVersion").asText(), is("2025-03-26"));

        response.release();
    }

    @Test
    public void shouldFallBackToLatestProtocolVersionForUnsupportedRequest() throws Exception {
        // An unsupported/future revision falls back to the server's latest supported revision.
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\",\"clientInfo\":{\"name\":\"test\"}}}";
        FullHttpResponse response = sendPost(requestBody);

        JsonNode json = parseResponse(response);
        assertThat(json.path("result").path("protocolVersion").asText(), is("2025-06-18"));

        response.release();
    }

    @Test
    public void shouldDefaultToLatestProtocolVersionWhenClientOmitsIt() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody);

        JsonNode json = parseResponse(response);
        assertThat(json.path("result").path("protocolVersion").asText(), is("2025-06-18"));

        response.release();
    }

    @Test
    public void shouldEmitStructuredContentForToolsCallWhen2025_06_18Negotiated() throws Exception {
        // Initialize negotiating 2025-06-18, then call a tool that returns a JSON object.
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}";
        FullHttpResponse initResponse = sendPost(initBody);
        String sessionId = initResponse.headers().get("Mcp-Session-Id");
        initResponse.release();
        sendPost("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", sessionId).release();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "create_expectation");
        ObjectNode args = params.putObject("arguments");
        args.put("method", "GET");
        args.put("path", "/structured");
        args.put("statusCode", 200);
        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 3);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);
        JsonNode result = parseResponse(response).path("result");

        // The text content block is still present (back-compat)...
        JsonNode structured = result.path("structuredContent");
        assertThat(structured.isObject(), is(true));
        assertThat(structured.path("status").asText(), is("created"));
        // ...and structuredContent mirrors the parsed text content block.
        JsonNode fromText = objectMapper.readTree(result.path("content").get(0).path("text").asText());
        assertThat(structured.path("status").asText(), is(fromText.path("status").asText()));

        response.release();
    }

    @Test
    public void shouldNotEmitStructuredContentForToolsCallWhen2025_03_26Negotiated() throws Exception {
        // A 2025-03-26 session must not receive the 2025-06-18-only structuredContent field.
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}";
        FullHttpResponse initResponse = sendPost(initBody);
        String sessionId = initResponse.headers().get("Mcp-Session-Id");
        initResponse.release();
        sendPost("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", sessionId).release();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "create_expectation");
        ObjectNode args = params.putObject("arguments");
        args.put("method", "GET");
        args.put("path", "/legacy");
        args.put("statusCode", 200);
        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 3);
        rpcRequest.put("method", "tools/call");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);
        JsonNode result = parseResponse(response).path("result");

        assertThat(result.has("structuredContent"), is(false));
        assertThat(result.path("content").isArray(), is(true));

        response.release();
    }

    @Test
    public void shouldRequireSessionIdForNonInitializeMethods() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody);

        // previously asserted HTTP 200 with a JSON-RPC error; a request omitting a required
        // Mcp-Session-Id is a malformed request, so MCP 2025-06-18 basic/transports makes it a 400
        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("message").asText(), containsString("Missing Mcp-Session-Id"));

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
        assertThat(resources.size(), is(5));

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
    public void shouldAdvertisePromptsAndSamplingCapabilities() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        JsonNode capabilities = json.path("result").path("capabilities");
        assertThat(capabilities.path("prompts").path("listChanged").asBoolean(), is(false));
        assertThat(capabilities.has("sampling"), is(true));

        response.release();
    }

    @Test
    public void shouldHandlePromptsList() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"prompts/list\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        JsonNode prompts = json.path("result").path("prompts");
        assertThat(prompts.isArray(), is(true));
        assertThat(prompts.size() > 0, is(true));

        boolean foundDebugPrompt = false;
        for (JsonNode prompt : prompts) {
            if ("debug_unmatched_request".equals(prompt.path("name").asText())) {
                foundDebugPrompt = true;
                assertThat(prompt.path("description").asText().isEmpty(), is(false));
                assertThat(prompt.path("arguments").isArray(), is(true));
                assertThat(prompt.path("arguments").size(), is(2));
                assertThat(prompt.path("arguments").get(0).path("name").asText(), is("method"));
                assertThat(prompt.path("arguments").get(0).path("required").asBoolean(), is(true));
            }
        }
        assertThat(foundDebugPrompt, is(true));

        response.release();
    }

    @Test
    public void shouldHandlePromptsGetWithArgumentSubstitution() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("method", "POST");
        arguments.put("path", "/orders");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "debug_unmatched_request");
        params.set("arguments", arguments);

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 21);
        rpcRequest.put("method", "prompts/get");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        JsonNode result = json.path("result");
        assertThat(result.path("description").asText().isEmpty(), is(false));
        JsonNode messages = result.path("messages");
        assertThat(messages.isArray(), is(true));
        assertThat(messages.size(), is(1));
        assertThat(messages.get(0).path("role").asText(), is("user"));
        String text = messages.get(0).path("content").path("text").asText();
        assertThat(text, containsString("POST"));
        assertThat(text, containsString("/orders"));
        assertThat(text.contains("{{method}}"), is(false));

        response.release();
    }

    @Test
    public void shouldReturnErrorForUnknownPrompt() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "nonexistent_prompt");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 22);
        rpcRequest.put("method", "prompts/get");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_PARAMS));
        assertThat(json.path("error").path("message").asText(), containsString("Unknown prompt"));

        response.release();
    }

    @Test
    public void shouldReturnInvalidParamsNamingMissingRequiredPromptArgument() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "debug_unmatched_request");
        params.putObject("arguments").put("method", "GET");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 23);
        rpcRequest.put("method", "prompts/get");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("id").asInt(), is(23));
        assertThat(json.has("result"), is(false));
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_PARAMS));
        assertThat(json.path("error").path("message").asText(),
            is("Missing required argument for prompt 'debug_unmatched_request': path"));
        assertThat(json.path("error").path("data").path("missingArguments").toString(), is("[\"path\"]"));

        response.release();
    }

    @Test
    public void shouldHandleSamplingCreateMessage() throws Exception {
        String sessionId = initializeAndGetSessionId();

        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "user");
        ObjectNode content = message.putObject("content");
        content.put("type", "text");
        content.put("text", "Summarise the build failure.");

        ObjectNode hint = objectMapper.createObjectNode();
        hint.put("name", "claude-sonnet-4");
        ObjectNode modelPreferences = objectMapper.createObjectNode();
        modelPreferences.putArray("hints").add(hint);

        ObjectNode params = objectMapper.createObjectNode();
        params.putArray("messages").add(message);
        params.set("modelPreferences", modelPreferences);
        params.put("mockResponse", "The build failed because of a missing dependency.");

        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", "2.0");
        rpcRequest.put("id", 23);
        rpcRequest.put("method", "sampling/createMessage");
        rpcRequest.set("params", params);

        FullHttpResponse response = sendPost(objectMapper.writeValueAsString(rpcRequest), sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        JsonNode result = json.path("result");
        assertThat(result.path("role").asText(), is("assistant"));
        assertThat(result.path("content").path("type").asText(), is("text"));
        assertThat(result.path("content").path("text").asText(), is("The build failed because of a missing dependency."));
        assertThat(result.path("model").asText(), is("claude-sonnet-4"));
        assertThat(result.path("stopReason").asText(), is("endTurn"));

        response.release();
    }

    @Test
    public void shouldReturnErrorForSamplingWithoutMessages() throws Exception {
        String sessionId = initializeAndGetSessionId();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":24,\"method\":\"sampling/createMessage\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_PARAMS));
        assertThat(json.path("error").path("message").asText(), containsString("messages"));

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

        // previously asserted HTTP 200 with a per-element JSON-RPC error for each entry. The session
        // is a property of the POST, not of an individual element, and MCP 2025-06-18
        // basic/transports requires 404 for a session id the server does not recognise, so the whole
        // batch is now rejected with a single 404 rather than a 200 carrying an array of errors.
        assertThat(response.status(), is(HttpResponseStatus.NOT_FOUND));
        JsonNode json = parseResponse(response);
        assertThat(json.isArray(), is(false));
        assertThat(json.path("error").path("message").asText(), containsString("Unknown or terminated Mcp-Session-Id"));

        response.release();
    }

    @Test
    public void shouldRejectBatchRequestWithNoSession() throws Exception {
        // Send a batch request without any session header
        String requestBody = "[" +
            "{\"jsonrpc\":\"2.0\",\"id\":32,\"method\":\"ping\",\"params\":{}}" +
            "]";
        FullHttpResponse response = sendPost(requestBody);

        // previously asserted HTTP 200 with a one-element array of JSON-RPC errors; a batch with no
        // session id at all is a malformed request, so it is now a single 400 (see
        // shouldRejectBatchRequestWithInvalidSession for the unknown-session 404 counterpart)
        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.isArray(), is(false));
        assertThat(json.path("error").path("message").asText(), containsString("Missing Mcp-Session-Id"));

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

        // previously asserted HTTP 200 with "Missing or invalid Mcp-Session-Id". The session here is
        // neither missing nor unknown -- it exists and is live, it has simply not completed the
        // handshake -- so 404 would be wrong (it would tell the client to discard a usable session).
        // This is a protocol-sequence error against a valid session: 400, with a message that says so.
        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("message").asText(),
            containsString("has not completed initialization"));

        response.release();
    }

    @Test
    public void shouldAnswerPingBeforeInitializedNotification() throws Exception {
        // ping is the explicit carve-out from the pre-init restriction (MCP 2025-06-18
        // basic/lifecycle): a client may ping a session it has opened with initialize but not yet
        // confirmed with notifications/initialized. So set up exactly that state -- initialize, but
        // do NOT send notifications/initialized -- and expect a pong, not the "has not completed
        // initialization" error that shouldRejectToolCallBeforeInitialized asserts for tools/list.
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        FullHttpResponse initResponse = sendPost(initBody);
        String sessionId = initResponse.headers().get("Mcp-Session-Id");
        initResponse.release();

        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, sessionId);

        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat("ping before init must return a result, not an error", json.path("result").isObject(), is(true));
        assertThat("ping before init must not carry an error", json.has("error"), is(false));
        assertThat(json.path("id").asInt(), is(9));

        response.release();
    }

    @Test
    public void shouldStillRequireSessionIdForPing() throws Exception {
        // the ping exemption is only from the initialized precondition, not from the session-id
        // requirement: a ping with no session id at all is still a malformed request (400)
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody);

        assertThat(response.status(), is(HttpResponseStatus.BAD_REQUEST));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("message").asText(), containsString("Missing Mcp-Session-Id"));

        response.release();
    }

    @Test
    public void shouldReturnNotFoundForPingOnUnknownSession() throws Exception {
        // and a ping naming an unknown session is still a 404, exactly like any other method
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\",\"params\":{}}";
        FullHttpResponse response = sendPost(requestBody, "never-issued");

        assertThat(response.status(), is(HttpResponseStatus.NOT_FOUND));

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

        // The session id is present but unknown, which MCP 2025-06-18
        // basic/transports makes a 404 regardless of whether the message is a request or a
        // notification -- the client needs the same "start a new session" signal either way.
        // The sibling shouldRejectNotificationWithoutSession still asserts 400 (id absent).
        assertThat(response.status(), is(HttpResponseStatus.NOT_FOUND));

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

    @Test
    public void shouldAnswerCorsPreflightForCrossOriginDashboard() {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/mockserver/mcp");
        request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000");
        request.headers().set(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.headers().set(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, mcp-session-id");
        channel.writeInbound(request);

        FullHttpResponse response = awaitOutbound();
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:3000"));
        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS), containsString("POST"));
        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS), containsString("mcp-session-id"));
        response.release();
    }

    @Test
    public void shouldEchoCorsOriginOnMcpResponse() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}";
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.POST, "/mockserver/mcp",
            Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000");
        channel.writeInbound(request);

        FullHttpResponse response = awaitOutbound();
        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:3000"));
        // The browser must be able to read the session id returned by initialize.
        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS), containsString("Mcp-Session-Id"));
        assertThat(response.headers().get("Mcp-Session-Id"), notNullValue());
        response.release();
    }

    @Test
    public void shouldAnswerAForeignOriginPreflightWithoutCorsHeaders() {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/mockserver/mcp");
        request.headers().set(HttpHeaderNames.HOST, "localhost:1080");
        request.headers().set(HttpHeaderNames.ORIGIN, "https://attacker.example");
        request.headers().set(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.headers().set(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, mcp-session-id");
        channel.writeInbound(request);

        FullHttpResponse response = awaitOutbound();
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.headers().contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN), is(false));
        assertThat(response.headers().contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS), is(false));
        assertThat(response.headers().contains(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS), is(false));
        response.release();
    }

    @Test
    public void shouldNotEchoANullOriginOnMcpResponse() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}";
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.POST, "/mockserver/mcp",
            Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        request.headers().set(HttpHeaderNames.ORIGIN, "null");
        channel.writeInbound(request);

        FullHttpResponse response = awaitOutbound();
        assertThat(response.headers().contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN), is(false));
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

    // ---- control-plane authorization for MCP tool calls ----

    /**
     * Builds a fresh handler stack whose HttpState has control-plane authorization configured
     * (a verified principal authenticated with the given scopes, mapped to read/mutate/admin
     * roles), and returns a channel plus an already-initialised session over it. The
     * authentication SPI returns a verified result carrying {@code scopes} so the SAME
     * authorizer/role model the HTTP control plane uses applies to tool calls.
     */
    private AuthzFixture newAuthorizedFixture(boolean authorizationEnabled, java.util.Set<String> scopes) throws Exception {
        java.util.Map<String, org.mockserver.authentication.authorization.ControlPlaneRole> mapping = new java.util.LinkedHashMap<>();
        mapping.put("admins", org.mockserver.authentication.authorization.ControlPlaneRole.ADMIN);
        mapping.put("mutators", org.mockserver.authentication.authorization.ControlPlaneRole.MUTATE);
        mapping.put("readers", org.mockserver.authentication.authorization.ControlPlaneRole.READ);

        org.mockserver.configuration.Configuration cfg = configuration()
            .controlPlaneAuthorizationEnabled(authorizationEnabled)
            .controlPlaneScopeMapping(mapping);
        HttpState authzState = new HttpState(cfg, new MockServerLogger(), mock(Scheduler.class));
        httpStates.add(authzState);
        authzState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return true;
            }

            @Override
            public org.mockserver.authentication.AuthenticationResult authenticate(HttpRequest request) {
                return org.mockserver.authentication.AuthenticationResult.authenticated("principal", "verified-oidc", java.util.Map.of(), scopes);
            }
        });
        McpSessionManager authzSessions = new McpSessionManager(authzState.getMockServerLogger());
        EmbeddedChannel authzChannel = new EmbeddedChannel(new McpStreamableHttpHandler(authzState, server, authzSessions));

        // initialize + notifications/initialized over the authz channel
        FullHttpResponse init = postOn(authzChannel, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", null);
        String sessionId = init.headers().get("Mcp-Session-Id");
        init.release();
        FullHttpResponse notif = postOn(authzChannel, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", sessionId);
        if (notif != null) {
            notif.release();
        }
        return new AuthzFixture(authzChannel, sessionId, authzState);
    }

    private FullHttpResponse postOn(EmbeddedChannel ch, String body, String sessionId) {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.POST, "/mockserver/mcp",
            Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        if (sessionId != null) {
            request.headers().set("Mcp-Session-Id", sessionId);
        }
        ch.writeInbound(request);
        return awaitOutboundFrom(ch);
    }

    private FullHttpResponse callTool(EmbeddedChannel ch, String sessionId, String toolName, ObjectNode arguments) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", arguments != null ? arguments : objectMapper.createObjectNode());
        ObjectNode rpc = objectMapper.createObjectNode();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", 99);
        rpc.put("method", "tools/call");
        rpc.set("params", params);
        return postOn(ch, rpc.toString(), sessionId);
    }

    private static class AuthzFixture {
        final EmbeddedChannel channel;
        final String sessionId;
        final HttpState httpState;

        AuthzFixture(EmbeddedChannel channel, String sessionId, HttpState httpState) {
            this.channel = channel;
            this.sessionId = sessionId;
            this.httpState = httpState;
        }
    }

    @Test
    public void shouldRejectMutatingToolWhenAuthorizationEnabledAndCallerLacksMutateRole() throws Exception {
        AuthzFixture fixture = newAuthorizedFixture(true, java.util.Set.of("readers"));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("method", "GET");
        args.put("path", "/x");
        FullHttpResponse response = callTool(fixture.channel, fixture.sessionId, "create_expectation", args);

        assertThat(response, notNullValue());
        JsonNode json = parseResponse(response);
        // a denied tool call returns a JSON-RPC error mirroring the HTTP 403 ("Forbidden for control plane")
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_REQUEST));
        assertThat(json.path("error").path("message").asText(), containsString("Forbidden for control plane"));
        // and no result (no expectation created)
        assertThat(json.path("result").isMissingNode() || json.path("result").isNull(), is(true));

        response.release();
        fixture.channel.close();
    }

    @Test
    public void shouldAllowReadToolWhenAuthorizationEnabledAndCallerHasReadRole() throws Exception {
        AuthzFixture fixture = newAuthorizedFixture(true, java.util.Set.of("readers"));

        FullHttpResponse response = callTool(fixture.channel, fixture.sessionId, "get_status", null);

        assertThat(response, notNullValue());
        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").isMissingNode() || json.path("error").isNull(), is(true));
        assertThat(json.path("result").path("isError").asBoolean(false), is(false));

        response.release();
        fixture.channel.close();
    }

    @Test
    public void shouldAllowMutatingToolWhenAuthorizationEnabledAndCallerHasMutateRole() throws Exception {
        AuthzFixture fixture = newAuthorizedFixture(true, java.util.Set.of("mutators"));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("method", "GET");
        args.put("path", "/x");
        FullHttpResponse response = callTool(fixture.channel, fixture.sessionId, "create_expectation", args);

        assertThat(response, notNullValue());
        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").isMissingNode() || json.path("error").isNull(), is(true));
        // the create_expectation tool ran and reported success
        assertThat(json.path("result").path("isError").asBoolean(false), is(false));

        response.release();
        fixture.channel.close();
    }

    @Test
    public void shouldRejectSetOperatingModeWhenAuthorizationEnabledAndCallerLacksMutateRole() throws Exception {
        // set_operating_mode is a newly-added MUTATE authoring/control tool: a READ-only principal
        // must be denied and the mode must not change.
        AuthzFixture fixture = newAuthorizedFixture(true, java.util.Set.of("readers"));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("mode", "SPY");
        FullHttpResponse response = callTool(fixture.channel, fixture.sessionId, "set_operating_mode", args);

        assertThat(response, notNullValue());
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_REQUEST));
        assertThat(json.path("error").path("message").asText(), containsString("Forbidden for control plane"));
        assertThat(json.path("result").isMissingNode() || json.path("result").isNull(), is(true));

        response.release();
        fixture.channel.close();
    }

    @Test
    public void shouldAllowListExpectationsWhenAuthorizationEnabledAndCallerHasReadRole() throws Exception {
        // list_expectations is a newly-added READ authoring tool: a READ principal may call it.
        AuthzFixture fixture = newAuthorizedFixture(true, java.util.Set.of("readers"));

        FullHttpResponse response = callTool(fixture.channel, fixture.sessionId, "list_expectations", null);

        assertThat(response, notNullValue());
        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").isMissingNode() || json.path("error").isNull(), is(true));
        assertThat(json.path("result").path("isError").asBoolean(false), is(false));

        response.release();
        fixture.channel.close();
    }

    @Test
    public void shouldRejectReadToolWhenAuthorizationEnabledAndCallerHasNoMappedRole() throws Exception {
        // fail-closed: a verified principal whose scopes map to NO role is denied even reads
        AuthzFixture fixture = newAuthorizedFixture(true, java.util.Set.of("unmapped-scope"));

        FullHttpResponse response = callTool(fixture.channel, fixture.sessionId, "get_status", null);

        assertThat(response, notNullValue());
        JsonNode json = parseResponse(response);
        assertThat(json.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_REQUEST));
        assertThat(json.path("error").path("message").asText(), containsString("Forbidden for control plane"));

        response.release();
        fixture.channel.close();
    }

    @Test
    public void shouldNotEnforceAuthorizationForAnyToolWhenAuthorizationDisabled() throws Exception {
        // authorization disabled (the default) -> behaviour unchanged: a mutating tool runs even
        // with no mapped role / empty scopes
        AuthzFixture fixture = newAuthorizedFixture(false, java.util.Set.of());

        ObjectNode args = objectMapper.createObjectNode();
        args.put("method", "GET");
        args.put("path", "/x");
        FullHttpResponse mutate = callTool(fixture.channel, fixture.sessionId, "create_expectation", args);
        assertThat(mutate, notNullValue());
        assertThat(mutate.status(), is(HttpResponseStatus.OK));
        JsonNode mutateJson = parseResponse(mutate);
        assertThat(mutateJson.path("error").isMissingNode() || mutateJson.path("error").isNull(), is(true));
        assertThat(mutateJson.path("result").path("isError").asBoolean(false), is(false));
        mutate.release();

        FullHttpResponse read = callTool(fixture.channel, fixture.sessionId, "get_status", null);
        assertThat(read, notNullValue());
        assertThat(read.status(), is(HttpResponseStatus.OK));
        JsonNode readJson = parseResponse(read);
        assertThat(readJson.path("error").isMissingNode() || readJson.path("error").isNull(), is(true));
        read.release();

        fixture.channel.close();
    }

    @Test
    public void shouldRejectMutatingToolInBatchWhenAuthorizationEnabledAndCallerLacksMutateRole() throws Exception {
        // A batch tools/call is dispatched element-by-element through the same authorization
        // check, so a mutating tool inside a batch must be denied for a READ-only principal and
        // must not mutate state — while a read tool in the same batch still succeeds.
        AuthzFixture fixture = newAuthorizedFixture(true, java.util.Set.of("readers"));

        String batch = "[" +
            // element 1: a permitted READ tool
            "{\"jsonrpc\":\"2.0\",\"id\":40,\"method\":\"tools/call\",\"params\":{\"name\":\"get_status\",\"arguments\":{}}}," +
            // element 2: a forbidden MUTATE tool
            "{\"jsonrpc\":\"2.0\",\"id\":41,\"method\":\"tools/call\",\"params\":{\"name\":\"create_expectation\",\"arguments\":{\"method\":\"GET\",\"path\":\"/batch-forbidden\",\"statusCode\":201}}}" +
            "]";
        FullHttpResponse response = postOn(fixture.channel, batch, fixture.sessionId);

        assertThat(response, notNullValue());
        assertThat(response.status(), is(HttpResponseStatus.OK));
        JsonNode json = parseResponse(response);
        assertThat(json.isArray(), is(true));
        assertThat(json.size(), is(2));

        // locate each response by its JSON-RPC id (batch responses may be unordered)
        JsonNode readResponse = null;
        JsonNode mutateResponse = null;
        for (JsonNode element : json) {
            if (element.path("id").asInt() == 40) {
                readResponse = element;
            } else if (element.path("id").asInt() == 41) {
                mutateResponse = element;
            }
        }
        assertThat(readResponse, notNullValue());
        assertThat(mutateResponse, notNullValue());

        // the READ tool element succeeded
        assertThat(readResponse.path("error").isMissingNode() || readResponse.path("error").isNull(), is(true));
        assertThat(readResponse.path("result").path("isError").asBoolean(false), is(false));

        // the MUTATE tool element was forbidden
        assertThat(mutateResponse.path("error").path("code").asInt(), is(JsonRpcMessage.INVALID_REQUEST));
        assertThat(mutateResponse.path("error").path("message").asText(), containsString("Forbidden for control plane"));
        assertThat(mutateResponse.path("result").isMissingNode() || mutateResponse.path("result").isNull(), is(true));

        // and the expectation was NOT created (the forbidden batch element did not mutate state)
        assertThat(fixture.httpState.firstMatchingExpectation(
                HttpRequest.request().withMethod("GET").withPath("/batch-forbidden")),
            is(nullValue()));

        response.release();
        fixture.channel.close();
    }

    /**
     * MCP over HTTP/2 gets no reply unless the response carries the request's stream id.
     * <p>
     * This handler builds a raw Netty response and writes it straight to the channel, so it never
     * reaches the response mapper — the only other place that emits {@code x-http2-stream-id}.
     * Without it the HTTP/2 codec routes the reply onto a fresh server-initiated stream and the MCP
     * client hangs. Same defect class as GitHub #2419 (server-streaming gRPC delivering zero
     * messages) and the SSE / streaming-body / metrics siblings.
     */
    @Test
    public void shouldSendMcpResponseDownTheRequestHttp2Stream() {
        // given - a POST that arrived on HTTP/2 stream 7
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/mockserver/mcp",
            Unpooled.copiedBuffer("{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"params\":{},\"id\":1}", StandardCharsets.UTF_8)
        );
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        request.headers().set(org.mockserver.mappers.Http2StreamIds.STREAM_ID_HEADER, 7);

        // when
        channel.writeInbound(request);
        FullHttpResponse response = awaitOutbound();

        // then - the reply is routed back onto stream 7
        assertThat(response, notNullValue());
        assertThat(response.headers().getInt(org.mockserver.mappers.Http2StreamIds.STREAM_ID_HEADER), is(7));
        response.release();
    }

    @Test
    public void shouldNotAddStreamIdHeaderToMcpResponseOnHttp1() {
        // given - an HTTP/1.1 MCP POST has no stream id
        FullHttpResponse response = sendPost("{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"params\":{},\"id\":1}");

        // then - no HTTP/2 extension header leaks onto an HTTP/1.1 response
        assertThat(response, notNullValue());
        assertThat(response.headers().get(org.mockserver.mappers.Http2StreamIds.STREAM_ID_HEADER), nullValue());
        response.release();
    }
}
