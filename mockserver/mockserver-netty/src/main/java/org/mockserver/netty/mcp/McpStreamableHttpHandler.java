package org.mockserver.netty.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.JDKCertificateToMockServerX509Certificate;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.socket.tls.SniHandler;
import org.mockserver.version.Version;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static org.mockserver.exception.ExceptionHandling.connectionClosedException;

@ChannelHandler.Sharable
public class McpStreamableHttpHandler extends ChannelInboundHandlerAdapter {

    private static final String MCP_PATH = "/mockserver/mcp";
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String SERVER_NAME = "MockServer";
    private static final String SERVER_VERSION = Version.getVersion();

    private final HttpState httpState;
    private final LifeCycle server;
    private final McpSessionManager sessionManager;
    private final McpToolRegistry toolRegistry;
    private final McpResourceRegistry resourceRegistry;
    private final ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;

    public McpStreamableHttpHandler(HttpState httpState, LifeCycle server, McpSessionManager sessionManager) {
        this.httpState = httpState;
        this.server = server;
        this.sessionManager = sessionManager;
        this.toolRegistry = new McpToolRegistry(httpState, server);
        this.resourceRegistry = new McpResourceRegistry(httpState);
        this.objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
        this.mockServerLogger = httpState.getMockServerLogger();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        boolean release = true;
        try {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest request = (FullHttpRequest) msg;
                String uri = request.uri();
                if (uri.equals(MCP_PATH) || uri.startsWith(MCP_PATH + "?") || uri.startsWith(MCP_PATH + "/")) {
                    handleMcpRequest(ctx, request);
                    return;
                }
            }
            release = false;
            ctx.fireChannelRead(msg);
        } finally {
            if (release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (connectionClosedException(cause)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception caught by MCP handler")
                    .setThrowable(cause)
            );
        }
        ctx.close();
    }

    private void handleMcpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        HttpMethod method = request.method();
        if (method.equals(HttpMethod.POST)) {
            handlePost(ctx, request);
        } else if (method.equals(HttpMethod.GET)) {
            handleGet(ctx, request);
        } else if (method.equals(HttpMethod.DELETE)) {
            handleDelete(ctx, request);
        } else {
            writeJsonResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST, "Method not allowed"), null);
        }
    }

    private boolean authenticateRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        AuthenticationHandler authHandler = httpState.getControlPlaneAuthenticationHandler();
        if (authHandler != null) {
            try {
                HttpRequest mockRequest = HttpRequest.request()
                    .withMethod(request.method().name())
                    .withPath(request.uri());
                mockRequest.withLogCorrelationId(org.mockserver.uuid.UUIDService.getUUID());
                for (Map.Entry<String, String> header : request.headers()) {
                    mockRequest.withHeader(header.getKey(), header.getValue());
                }
                Certificate[] clientCertificates = SniHandler.retrieveClientCertificates(mockServerLogger, ctx);
                if (clientCertificates != null) {
                    new JDKCertificateToMockServerX509Certificate(mockServerLogger)
                        .setClientCertificates(mockRequest, clientCertificates);
                }
                if (!authHandler.controlPlaneRequestAuthenticated(mockRequest)) {
                    writeJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                        JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST, "Unauthorized for control plane"), null);
                    return false;
                }
            } catch (AuthenticationException e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("MCP authentication failed: {}")
                        .setArguments(e.getMessage())
                        .setThrowable(e)
                );
                writeJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                    JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST, "Unauthorized for control plane"), null);
                return false;
            }
        }
        return true;
    }

    private void handlePost(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!authenticateRequest(ctx, request)) {
            return;
        }
        // Retain the request before handing off to the MCP executor since Netty will release
        // the buffer after channelRead returns. The finally block ensures it is always released.
        request.retain();
        try {
            sessionManager.getExecutor().execute(() -> {
                try {
                    handlePostInternal(ctx, request);
                } finally {
                    request.release();
                }
            });
        } catch (RejectedExecutionException e) {
            request.release();
            writeJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INTERNAL_ERROR, "Server is busy, try again later"), null);
        }
    }

    private void handlePostInternal(ChannelHandlerContext ctx, FullHttpRequest request) {
        String body = request.content().toString(StandardCharsets.UTF_8);
        if (body.isEmpty()) {
            writeJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.PARSE_ERROR, "Empty request body"), null);
            return;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(body);

            if (jsonNode.isArray()) {
                handleBatchRequest(ctx, request, jsonNode);
            } else if (jsonNode.isObject()) {
                handleSingleRequest(ctx, request, jsonNode);
            } else {
                writeJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.PARSE_ERROR, "Invalid JSON-RPC message"), null);
            }
        } catch (JsonProcessingException e) {
            writeJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.PARSE_ERROR, "Parse error"), null);
        }
    }

    private boolean isSessionValid(FullHttpRequest httpRequest, String method) {
        if ("initialize".equals(method)) {
            return true;
        }
        String sessionId = httpRequest.headers().get("Mcp-Session-Id");
        if (!sessionManager.isValidSession(sessionId)) {
            return false;
        }
        if ("notifications/initialized".equals(method)) {
            return true;
        }
        McpSession session = sessionManager.getSession(sessionId);
        return session != null && session.isInitialized();
    }

    private void handleBatchRequest(ChannelHandlerContext ctx, FullHttpRequest request, JsonNode batchNode) {
        if (batchNode.size() == 0) {
            writeJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST, "Invalid Request: batch must not be empty"), null);
            return;
        }

        ArrayNode responses = objectMapper.createArrayNode();
        boolean allNotifications = true;

        for (JsonNode element : batchNode) {
            JsonRpcMessage.JsonRpcRequest rpcRequest = parseJsonRpcRequest(element);
            if (rpcRequest == null) {
                responses.add(objectMapper.valueToTree(
                    JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST, "Invalid JSON-RPC request")));
                allNotifications = false;
                continue;
            }

            if ("initialize".equals(rpcRequest.getMethod())) {
                responses.add(objectMapper.valueToTree(
                    JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_REQUEST,
                        "The 'initialize' method must be sent as a single request, not inside a batch.")));
                allNotifications = false;
                continue;
            }

            if (rpcRequest.isNotification()) {
                String sessionId = request.headers().get("Mcp-Session-Id");
                boolean sessionValid = sessionId != null && sessionManager.isValidSession(sessionId);
                if ("notifications/initialized".equals(rpcRequest.getMethod())) {
                    if (sessionValid) {
                        processNotification(rpcRequest, request);
                    }
                } else {
                    if (sessionValid) {
                        McpSession session = sessionManager.getSession(sessionId);
                        if (session != null && session.isInitialized()) {
                            processNotification(rpcRequest, request);
                        }
                    }
                }
            } else {
                allNotifications = false;
                if (!isSessionValid(request, rpcRequest.getMethod())) {
                    responses.add(objectMapper.valueToTree(
                        JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_REQUEST,
                            "Missing or invalid Mcp-Session-Id header. Call 'initialize' first.")));
                    continue;
                }
                JsonRpcMessage.JsonRpcResponse response = processRequest(rpcRequest, request);
                responses.add(objectMapper.valueToTree(response));
            }
        }

        if (allNotifications) {
            writeEmptyResponse(ctx, HttpResponseStatus.ACCEPTED);
        } else {
            writeRawJsonResponse(ctx, HttpResponseStatus.OK, responses, null);
        }
    }

    private void handleSingleRequest(ChannelHandlerContext ctx, FullHttpRequest request, JsonNode jsonNode) {
        JsonRpcMessage.JsonRpcRequest rpcRequest = parseJsonRpcRequest(jsonNode);
        if (rpcRequest == null) {
            writeJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST, "Invalid JSON-RPC request"), null);
            return;
        }

        if (rpcRequest.isNotification()) {
            String sessionId = request.headers().get("Mcp-Session-Id");
            if ("notifications/initialized".equals(rpcRequest.getMethod())) {
                if (sessionId == null || !sessionManager.isValidSession(sessionId)) {
                    writeEmptyResponse(ctx, HttpResponseStatus.BAD_REQUEST);
                    return;
                }
            } else {
                if (sessionId == null || !sessionManager.isValidSession(sessionId)) {
                    writeEmptyResponse(ctx, HttpResponseStatus.BAD_REQUEST);
                    return;
                }
                McpSession session = sessionManager.getSession(sessionId);
                if (session == null || !session.isInitialized()) {
                    writeEmptyResponse(ctx, HttpResponseStatus.BAD_REQUEST);
                    return;
                }
            }
            processNotification(rpcRequest, request);
            writeEmptyResponse(ctx, HttpResponseStatus.ACCEPTED);
            return;
        }

        if ("initialize".equals(rpcRequest.getMethod())) {
            InitializeResult initResult = handleInitialize(rpcRequest);
            writeJsonResponse(ctx, HttpResponseStatus.OK, initResult.response, initResult.sessionId);
            return;
        }

        if (!isSessionValid(request, rpcRequest.getMethod())) {
            writeJsonResponse(ctx, HttpResponseStatus.OK,
                JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_REQUEST,
                    "Missing or invalid Mcp-Session-Id header. Call 'initialize' first."), null);
            return;
        }

        JsonRpcMessage.JsonRpcResponse response = processRequest(rpcRequest, request);
        writeJsonResponse(ctx, HttpResponseStatus.OK, response, null);
    }

    private JsonRpcMessage.JsonRpcRequest parseJsonRpcRequest(JsonNode node) {
        try {
            JsonRpcMessage.JsonRpcRequest request = objectMapper.treeToValue(node, JsonRpcMessage.JsonRpcRequest.class);
            if (request == null) {
                return null;
            }
            request.setIdPresent(node.has("id"));
            if (!"2.0".equals(request.getJsonrpc())) {
                return null;
            }
            if (request.getMethod() == null || request.getMethod().isEmpty()) {
                return null;
            }
            Object id = request.getId();
            if (id != null && !(id instanceof String) && !(id instanceof Integer) && !(id instanceof Long)) {
                return null;
            }
            return request;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void processNotification(JsonRpcMessage.JsonRpcRequest rpcRequest, FullHttpRequest httpRequest) {
        if ("notifications/initialized".equals(rpcRequest.getMethod())) {
            String sessionId = httpRequest.headers().get("Mcp-Session-Id");
            if (sessionId != null) {
                McpSession session = sessionManager.getSession(sessionId);
                if (session != null) {
                    session.markInitialized();
                }
            }
        }
    }

    private JsonRpcMessage.JsonRpcResponse processRequest(JsonRpcMessage.JsonRpcRequest rpcRequest, FullHttpRequest httpRequest) {
        String method = rpcRequest.getMethod();
        if (method == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_REQUEST, "Missing method");
        }

        switch (method) {
            case "initialize":
                return handleInitialize(rpcRequest).response;
            case "tools/list":
                return handleToolsList(rpcRequest);
            case "tools/call":
                return handleToolsCall(rpcRequest);
            case "resources/list":
                return handleResourcesList(rpcRequest);
            case "resources/read":
                return handleResourcesRead(rpcRequest);
            case "ping":
                return handlePing(rpcRequest);
            default:
                return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.METHOD_NOT_FOUND,
                    "Method not found: " + method);
        }
    }

    private static class InitializeResult {
        final JsonRpcMessage.JsonRpcResponse response;
        final String sessionId;

        InitializeResult(JsonRpcMessage.JsonRpcResponse response, String sessionId) {
            this.response = response;
            this.sessionId = sessionId;
        }
    }

    private InitializeResult handleInitialize(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        McpSession session = sessionManager.createSession();
        String sessionId = session.getSessionId();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode capabilities = result.putObject("capabilities");
        ObjectNode toolsCap = capabilities.putObject("tools");
        toolsCap.put("listChanged", false);
        ObjectNode resourcesCap = capabilities.putObject("resources");
        resourcesCap.put("subscribe", false);
        resourcesCap.put("listChanged", false);

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);

        return new InitializeResult(JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), result), sessionId);
    }

    private JsonRpcMessage.JsonRpcResponse handleToolsList(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode toolsArray = result.putArray("tools");

        for (McpToolRegistry.ToolDefinition tool : toolRegistry.getTools().values()) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("name", tool.getName());
            toolNode.put("description", tool.getDescription());
            toolNode.set("inputSchema", tool.getInputSchema());
            toolsArray.add(toolNode);
        }

        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), result);
    }

    private JsonRpcMessage.JsonRpcResponse handleToolsCall(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        JsonNode params = rpcRequest.getParams();
        if (params == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS, "Missing params");
        }

        String toolName = params.path("name").asText(null);
        if (toolName == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS, "Missing tool name");
        }

        if (!toolRegistry.getTools().containsKey(toolName)) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.METHOD_NOT_FOUND,
                "Unknown tool: " + toolName);
        }

        JsonNode arguments = params.path("arguments");
        JsonNode toolResult = toolRegistry.callTool(toolName, arguments.isMissingNode() ? null : arguments);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        try {
            textContent.put("text", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toolResult));
        } catch (JsonProcessingException e) {
            textContent.put("text", toolResult.toString());
        }
        content.add(textContent);

        boolean isError = toolResult != null && toolResult.has("error") && toolResult.path("error").asBoolean(false);
        result.put("isError", isError);

        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), result);
    }

    private JsonRpcMessage.JsonRpcResponse handleResourcesList(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode resourcesArray = result.putArray("resources");

        for (McpResourceRegistry.ResourceDefinition resource : resourceRegistry.getResources().values()) {
            ObjectNode resourceNode = objectMapper.createObjectNode();
            resourceNode.put("uri", resource.getUri());
            resourceNode.put("name", resource.getName());
            resourceNode.put("description", resource.getDescription());
            resourceNode.put("mimeType", resource.getMimeType());
            resourcesArray.add(resourceNode);
        }

        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), result);
    }

    private JsonRpcMessage.JsonRpcResponse handleResourcesRead(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        JsonNode params = rpcRequest.getParams();
        if (params == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS, "Missing params");
        }

        String uri = params.path("uri").asText(null);
        if (uri == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS, "Missing resource URI");
        }

        if (!resourceRegistry.getResources().containsKey(uri)) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS,
                "Unknown resource: " + uri);
        }

        McpResourceRegistry.ResourceDefinition resourceDef = resourceRegistry.getResources().get(uri);
        JsonNode resourceContent = resourceRegistry.readResource(uri);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode contents = result.putArray("contents");
        ObjectNode contentEntry = objectMapper.createObjectNode();
        contentEntry.put("uri", uri);
        contentEntry.put("mimeType", resourceDef.getMimeType());

        try {
            contentEntry.put("text", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resourceContent));
        } catch (JsonProcessingException e) {
            contentEntry.put("text", resourceContent.toString());
        }

        contents.add(contentEntry);

        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), result);
    }

    private JsonRpcMessage.JsonRpcResponse handlePing(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), objectMapper.createObjectNode());
    }

    private void handleGet(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!authenticateRequest(ctx, request)) {
            return;
        }

        writeEmptyResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
    }

    private void handleDelete(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!authenticateRequest(ctx, request)) {
            return;
        }

        String sessionId = request.headers().get("Mcp-Session-Id");
        if (sessionId == null || sessionManager.removeSession(sessionId) == null) {
            writeEmptyResponse(ctx, HttpResponseStatus.NOT_FOUND);
        } else {
            writeEmptyResponse(ctx, HttpResponseStatus.OK);
        }
    }

    private void writeJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                                   JsonRpcMessage.JsonRpcResponse rpcResponse, String sessionId) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(rpcResponse);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(jsonBytes)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            HttpUtil.setContentLength(response, jsonBytes.length);
            if (sessionId != null) {
                response.headers().set("Mcp-Session-Id", sessionId);
            }
            ctx.writeAndFlush(response);
        } catch (JsonProcessingException e) {
            byte[] fallback = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"},\"id\":null}".getBytes(StandardCharsets.UTF_8);
            DefaultFullHttpResponse fallbackResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(fallback)
            );
            fallbackResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            HttpUtil.setContentLength(fallbackResponse, fallback.length);
            ctx.writeAndFlush(fallbackResponse);
        }
    }

    private void writeRawJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                                      JsonNode jsonNode, String sessionId) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(jsonNode);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(jsonBytes)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            HttpUtil.setContentLength(response, jsonBytes.length);
            if (sessionId != null) {
                response.headers().set("Mcp-Session-Id", sessionId);
            }
            ctx.writeAndFlush(response);
        } catch (JsonProcessingException e) {
            byte[] fallback = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"},\"id\":null}".getBytes(StandardCharsets.UTF_8);
            DefaultFullHttpResponse fallbackResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(fallback)
            );
            fallbackResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            HttpUtil.setContentLength(fallbackResponse, fallback.length);
            ctx.writeAndFlush(fallbackResponse);
        }
    }

    private void writeEmptyResponse(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            status,
            Unpooled.EMPTY_BUFFER
        );
        HttpUtil.setContentLength(response, 0);
        ctx.writeAndFlush(response);
    }
}
