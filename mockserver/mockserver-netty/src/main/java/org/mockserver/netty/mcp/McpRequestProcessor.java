package org.mockserver.netty.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.authentication.ControlPlaneAuthentication;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.metrics.Metrics;
import org.mockserver.mock.HttpState;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.version.Version;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;

/**
 * Transport-neutral MCP (Model Context Protocol) JSON-RPC request processor.
 * <p>
 * This class contains all the MCP protocol logic (JSON-RPC parsing, session
 * management, tool/resource dispatch, response construction) without any
 * dependency on a specific transport (Netty HTTP/1.1, HTTP/2, or HTTP/3).
 * <p>
 * Both {@link McpStreamableHttpHandler} (TCP path) and the HTTP/3 MCP
 * dispatch in {@code Http3MockServerHandler} delegate to this processor.
 */
public class McpRequestProcessor {

    static final String MCP_PATH = "/mockserver/mcp";

    /**
     * Latest MCP spec revision this server advertises. Used as the default when a client
     * omits {@code protocolVersion} or requests a revision this server does not support.
     */
    static final String LATEST_PROTOCOL_VERSION = "2025-06-18";

    /**
     * First MCP spec revision that introduced structured tool output ({@code structuredContent}
     * / {@code outputSchema}) and resource links in tool results. Sessions that negotiate this
     * revision (or later) receive those fields; older sessions do not.
     */
    static final String STRUCTURED_OUTPUT_MIN_VERSION = "2025-06-18";

    /**
     * MCP spec revisions this server can negotiate. The client sends its preferred revision in
     * {@code initialize}; the server echoes it when supported (back-compat for 2025-03-26 and
     * 2024-11-05 clients), otherwise replies with {@link #LATEST_PROTOCOL_VERSION}.
     */
    private static final java.util.Set<String> SUPPORTED_PROTOCOL_VERSIONS =
        java.util.Set.of("2025-06-18", "2025-03-26", "2024-11-05");

    private static final String SERVER_NAME = "MockServer";
    private static final String SERVER_VERSION = Version.getVersion();

    /**
     * Sent in the 'initialize' result so an AI agent knows, up front, which MockServer tools to reach for.
     */
    private static final String SERVER_INSTRUCTIONS =
        "MockServer is an HTTP(S) mock server and proxy for testing. Use these tools to mock APIs, " +
            "debug failing requests, and verify implementations:\n" +
            "- Mock APIs: 'create_expectation', 'create_expectation_from_openapi', and " +
            "'create_expectations_from_recorded_traffic' (turn traffic already recorded via the proxy into mocks).\n" +
            "- Debug a request that did not match: call 'explain_unmatched_requests' after a failed test run to see, " +
            "for each request that hit the server, the closest expectations ranked by similarity with field-level " +
            "diffs and a remediation hint — no need to reconstruct the request. 'debug_request_mismatch' does the " +
            "same for a request you supply. 'explain_request' takes one received request's correlation id (from " +
            "'retrieve_logs') and says which expectation serves it now and why each one ahead of it lost; " +
            "'diagnose_config' shows which settings are in effect and where they came from.\n" +
            "- Verify an implementation: 'verify_request' / 'verify_request_sequence' check requests were made; " +
            "'verify_traffic_against_openapi' checks recorded traffic conforms to an OpenAPI contract; " +
            "'run_contract_test' sends spec-derived example requests to a running service and checks the responses; " +
            "'run_resiliency_test' sends malformed and boundary-case requests and reports which inputs the service " +
            "failed to handle gracefully.\n" +
            "- Deterministic LLM testing: 'record_llm_fixtures' snapshots LLM/MCP traffic recorded through the proxy " +
            "into a committable, secret-free fixture file; 'load_expectations_from_file' replays it.\n" +
            "Readable resources expose live state: mockserver://expectations, mockserver://requests, " +
            "mockserver://logs, mockserver://unmatched, mockserver://configuration.";

    private final HttpState httpState;
    private final LifeCycle server;
    private final McpSessionManager sessionManager;
    private final McpToolRegistry toolRegistry;
    private final McpResourceRegistry resourceRegistry;
    private final McpPromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;

    /** Default model name reported by {@code sampling/createMessage} when the request omits a preferred model. */
    private static final String DEFAULT_SAMPLING_MODEL = "mock-llm";
    /** Stop reason reported in the {@code sampling/createMessage} result (MCP server-side field, not a request field). */
    private static final String SAMPLING_STOP_REASON = "endTurn";

    public McpRequestProcessor(HttpState httpState, LifeCycle server, McpSessionManager sessionManager) {
        this(httpState, server, sessionManager, new McpToolRegistry(httpState, server));
    }

    /**
     * Constructor taking an explicit {@link McpToolRegistry}, so tests can supply a registry
     * whose tools fail in a controlled way (for example to prove that a throwing tool still
     * produces a JSON-RPC error response rather than no response at all).
     */
    McpRequestProcessor(HttpState httpState, LifeCycle server, McpSessionManager sessionManager, McpToolRegistry toolRegistry) {
        this.httpState = httpState;
        this.server = server;
        this.sessionManager = sessionManager;
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = new McpResourceRegistry(httpState);
        this.promptRegistry = new McpPromptRegistry();
        this.objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    }

    /**
     * Returns the session manager used by this processor.
     */
    public McpSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Check if a path matches the MCP endpoint.
     */
    public static boolean isMcpPath(String path) {
        return path != null && (
            path.equals(MCP_PATH)
                || path.startsWith(MCP_PATH + "?")
                || path.startsWith(MCP_PATH + "/")
        );
    }

    // ---- response types ----

    /**
     * Result of processing an MCP request. Transport handlers translate this
     * into the appropriate wire format (HTTP/1.1, HTTP/3 frames, etc.).
     */
    public static class McpResult {
        private final int statusCode;
        private final byte[] body;
        private final String sessionId;

        public McpResult(int statusCode, byte[] body, String sessionId) {
            this.statusCode = statusCode;
            this.body = body;
            this.sessionId = sessionId;
        }

        public int getStatusCode() {
            return statusCode;
        }

        /** JSON body bytes, or empty for no-body responses. */
        public byte[] getBody() {
            return body;
        }

        /** Non-null only for initialize responses. */
        public String getSessionId() {
            return sessionId;
        }

        public boolean hasBody() {
            return body != null && body.length > 0;
        }
    }

    // ---- HTTP method dispatch (transport-neutral) ----

    /**
     * Process an MCP POST request. The request is authenticated by the transport handler
     * BEFORE this is called; its authentication is passed in so per-tool
     * control-plane authorization can be enforced for {@code tools/call} (a mutating tool
     * requires the MUTATE role, a reading tool the READ role). Equivalent to
     * {@link #handlePost(String, String, ControlPlaneAuthentication)} with no scopes and the
     * current settings, used by callers that do not carry an authenticated result.
     *
     * @param requestBody the raw JSON body
     * @param mcpSessionId the Mcp-Session-Id header value (may be null)
     * @return the result to write back
     */
    public McpResult handlePost(String requestBody, String mcpSessionId) {
        return handlePost(requestBody, mcpSessionId, ControlPlaneAuthentication.of(httpState.controlPlaneAuthenticationSettings(), null));
    }

    /**
     * Process an MCP POST request, enforcing per-tool control-plane authorization for
     * {@code tools/call} using the authenticated principal's verified scopes.
     * <p>
     * Authorization is delegated to {@link HttpState#controlPlaneToolAuthorized} — the SAME
     * authorizer/role model as the HTTP control plane — and is gated by
     * {@code controlPlaneAuthorizationEnabled}: when that is off (the default), authorization
     * always passes and behaviour is unchanged. It uses the settings snapshot the caller was
     * authenticated under, never a fresh one.
     *
     * @param requestBody the raw JSON body
     * @param mcpSessionId the Mcp-Session-Id header value (may be null)
     * @param authentication the caller's authentication and the settings snapshot it was made under
     * @return the result to write back
     */
    public McpResult handlePost(String requestBody, String mcpSessionId, ControlPlaneAuthentication authentication) {
        java.util.Objects.requireNonNull(authentication, "authentication");
        if (requestBody == null || requestBody.isEmpty()) {
            return jsonResponse(400,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.PARSE_ERROR, "Empty request body"), null);
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(requestBody);

            if (jsonNode.isArray()) {
                return handleBatchRequest(jsonNode, mcpSessionId, authentication);
            } else if (jsonNode.isObject()) {
                return handleSingleRequest(jsonNode, mcpSessionId, authentication);
            } else {
                return jsonResponse(400,
                    JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.PARSE_ERROR, "Invalid JSON-RPC message"), null);
            }
        } catch (JsonProcessingException e) {
            return jsonResponse(400,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.PARSE_ERROR, "Parse error"), null);
        }
    }

    /**
     * Process an MCP DELETE request.
     *
     * @param mcpSessionId the Mcp-Session-Id header value
     * @return the result to write back
     */
    public McpResult handleDelete(String mcpSessionId) {
        if (mcpSessionId == null || sessionManager.removeSession(mcpSessionId) == null) {
            return emptyResponse(404);
        }
        return emptyResponse(200);
    }

    /**
     * Process an MCP GET request (currently not supported -- returns 405).
     *
     * @return the result to write back
     */
    public McpResult handleGet() {
        return emptyResponse(405);
    }

    /**
     * Process an MCP OPTIONS request (CORS preflight or method-not-allowed).
     *
     * @param hasOrigin whether the request has an Origin header
     * @return the result to write back
     */
    public McpResult handleOptions(boolean hasOrigin) {
        if (hasOrigin) {
            return emptyResponse(200);
        }
        return jsonResponse(405,
            JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST, "Method not allowed"), null);
    }

    // ---- private request handling ----

    /**
     * Decide the HTTP status for a POST whose MCP session cannot be used, per the MCP Streamable
     * HTTP transport spec (2025-06-18, {@code basic/transports}): a request that omits a required
     * {@code Mcp-Session-Id} is a 400 Bad Request, while one naming a session the server does not --
     * or no longer -- recognise MUST be answered 404 Not Found. The 404 is load-bearing: it is the
     * signal a conformant client uses to know it must start a new session. Answering 200 with a
     * JSON-RPC error (as this previously did) gives the client no way to tell a dead session from an
     * application-level error, so it can never perform the mandated recovery and instead loops
     * against a session that will never work again. DELETE already answers 404 for an unknown
     * session; this brings POST into line with it.
     *
     * @return the rejection status, or null when the session is usable
     */
    private Integer sessionRejectionStatus(String mcpSessionId) {
        // Blank counts as absent, not as an unknown session. The transports disagree on how they
        // report a missing header -- the HTTP/1.1 and HTTP/2 handlers read it straight off the Netty
        // headers and get null, while the HTTP/3 handler uses HttpRequest.getFirstHeader, which
        // returns "" by MockServer convention. Testing only for null therefore answered 400 on
        // HTTP/1.1 and 404 on HTTP/3 for the identical request, which is both a spec violation on
        // one of the two and exactly the kind of per-transport divergence this class exists to avoid.
        if (mcpSessionId == null || mcpSessionId.trim().isEmpty()) {
            return 400;
        }
        if (!sessionManager.isValidSession(mcpSessionId)) {
            return 404;
        }
        return null;
    }

    private static String sessionRejectionMessage(int status) {
        return status == 404
            ? "Unknown or terminated Mcp-Session-Id. Start a new session by calling 'initialize'."
            : "Missing Mcp-Session-Id header. Call 'initialize' first.";
    }

    /**
     * Whether a request method is exempt from the "session must have completed initialization"
     * precondition. Only {@code ping} is: the MCP lifecycle spec (2025-06-18, {@code basic/lifecycle})
     * explicitly permits a client to send a ping before it has sent {@code notifications/initialized},
     * so a live-but-not-yet-initialized session must still be able to answer one. Gating it (as this
     * previously did) meant a client following the spec's own recommendation -- ping to check liveness
     * during a slow handshake -- got an error instead of a pong. This exemption is only from the
     * initialized precondition; the session-id checks ({@link #sessionRejectionStatus}) run first, so a
     * ping with no session or an unknown session is still rejected 400/404 like any other method, and
     * this does not widen access for any method other than ping.
     */
    private static boolean isExemptFromInitializedPrecondition(String method) {
        return "ping".equals(method);
    }

    private McpResult handleBatchRequest(JsonNode batchNode, String mcpSessionId, ControlPlaneAuthentication authentication) {
        if (batchNode.size() == 0) {
            return jsonResponse(400,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST, "Invalid Request: batch must not be empty"), null);
        }

        // The session is a property of the POST, not of an individual element, so it is validated
        // once for the whole batch. This also means a batch made up entirely of notifications sent
        // against a dead session is now rejected with 404 rather than silently accepted with 202.
        Integer batchRejectionStatus = sessionRejectionStatus(mcpSessionId);
        if (batchRejectionStatus != null) {
            return jsonResponse(batchRejectionStatus,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST,
                    sessionRejectionMessage(batchRejectionStatus)), null);
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
                // the session is already known valid; notifications/initialized is what marks the
                // session initialized, so it alone is exempt from the initialized precondition
                if ("notifications/initialized".equals(rpcRequest.getMethod())) {
                    processNotification(rpcRequest, mcpSessionId);
                } else {
                    McpSession session = sessionManager.getSession(mcpSessionId);
                    if (session != null && session.isInitialized()) {
                        processNotification(rpcRequest, mcpSessionId);
                    }
                }
            } else {
                allNotifications = false;
                McpSession session = sessionManager.getSession(mcpSessionId);
                if (!isExemptFromInitializedPrecondition(rpcRequest.getMethod())
                    && (session == null || !session.isInitialized())) {
                    responses.add(objectMapper.valueToTree(
                        JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_REQUEST,
                            "MCP session has not completed initialization. Send the 'notifications/initialized' notification first.")));
                    continue;
                }
                JsonRpcMessage.JsonRpcResponse response = processRequest(rpcRequest, mcpSessionId, authentication);
                responses.add(objectMapper.valueToTree(response));
            }
        }

        if (allNotifications) {
            return emptyResponse(202);
        }
        return rawJsonResponse(200, responses, null);
    }

    private McpResult handleSingleRequest(JsonNode jsonNode, String mcpSessionId, ControlPlaneAuthentication authentication) {
        JsonRpcMessage.JsonRpcRequest rpcRequest = parseJsonRpcRequest(jsonNode);
        if (rpcRequest == null) {
            return jsonResponse(400,
                JsonRpcMessage.JsonRpcResponse.error(null, JsonRpcMessage.INVALID_REQUEST, "Invalid JSON-RPC request"), null);
        }

        if (rpcRequest.isNotification()) {
            Integer notificationRejectionStatus = sessionRejectionStatus(mcpSessionId);
            if (notificationRejectionStatus != null) {
                return emptyResponse(notificationRejectionStatus);
            }
            // notifications/initialized is what marks the session initialized, so it alone is
            // exempt from the initialized precondition
            if (!"notifications/initialized".equals(rpcRequest.getMethod())) {
                McpSession session = sessionManager.getSession(mcpSessionId);
                if (session == null || !session.isInitialized()) {
                    return emptyResponse(400);
                }
            }
            processNotification(rpcRequest, mcpSessionId);
            return emptyResponse(202);
        }

        if ("initialize".equals(rpcRequest.getMethod())) {
            InitializeResult initResult = handleInitialize(rpcRequest);
            return jsonResponse(200, initResult.response, initResult.sessionId);
        }

        Integer rejectionStatus = sessionRejectionStatus(mcpSessionId);
        if (rejectionStatus != null) {
            return jsonResponse(rejectionStatus,
                JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_REQUEST,
                    sessionRejectionMessage(rejectionStatus)), null);
        }

        McpSession initializedSession = sessionManager.getSession(mcpSessionId);
        if (!isExemptFromInitializedPrecondition(rpcRequest.getMethod())
            && (initializedSession == null || !initializedSession.isInitialized())) {
            return jsonResponse(400,
                JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_REQUEST,
                    "MCP session has not completed initialization. Send the 'notifications/initialized' notification first."), null);
        }

        JsonRpcMessage.JsonRpcResponse response = processRequest(rpcRequest, mcpSessionId, authentication);
        return jsonResponse(200, response, null);
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

    private void processNotification(JsonRpcMessage.JsonRpcRequest rpcRequest, String mcpSessionId) {
        if ("notifications/initialized".equals(rpcRequest.getMethod())) {
            if (mcpSessionId != null) {
                McpSession session = sessionManager.getSession(mcpSessionId);
                if (session != null) {
                    session.markInitialized();
                }
            }
        }
    }

    private JsonRpcMessage.JsonRpcResponse processRequest(JsonRpcMessage.JsonRpcRequest rpcRequest, String mcpSessionId, ControlPlaneAuthentication authentication) {
        String method = rpcRequest.getMethod();
        if (method == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_REQUEST, "Missing method");
        }

        // A handler (most likely a tool) that throws must still yield a JSON-RPC error envelope:
        // an exception escaping here propagates out of the MCP executor task with no response ever
        // written, leaving the client blocked until its own timeout. Catching per request also means
        // one failing entry in a batch does not discard the responses to the entries around it.
        // The exception detail is logged but deliberately not returned -- tool arguments and internal
        // state routinely appear in exception messages and this is a control-plane response.
        try {
            switch (method) {
                case "initialize":
                    return handleInitialize(rpcRequest).response;
                case "tools/list":
                    return handleToolsList(rpcRequest);
                case "tools/call":
                    return handleToolsCall(rpcRequest, mcpSessionId, authentication);
                case "resources/list":
                    return handleResourcesList(rpcRequest);
                case "resources/read":
                    return handleResourcesRead(rpcRequest);
                case "prompts/list":
                    return handlePromptsList(rpcRequest);
                case "prompts/get":
                    return handlePromptsGet(rpcRequest);
                case "sampling/createMessage":
                    return handleSamplingCreateMessage(rpcRequest);
                case "ping":
                    return handlePing(rpcRequest);
                default:
                    return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.METHOD_NOT_FOUND,
                        "Method not found: " + method);
            }
        } catch (Throwable throwable) {
            httpState.getMockServerLogger().logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception handling MCP method \"{}\"")
                    .setArguments(method)
                    .setThrowable(throwable)
            );
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INTERNAL_ERROR,
                "Internal error");
        }
    }

    static class InitializeResult {
        final JsonRpcMessage.JsonRpcResponse response;
        final String sessionId;

        InitializeResult(JsonRpcMessage.JsonRpcResponse response, String sessionId) {
            this.response = response;
            this.sessionId = sessionId;
        }
    }

    /**
     * Negotiate the MCP protocol version for a session. Per the MCP lifecycle the client sends its
     * preferred revision in {@code initialize}; the server echoes it when supported, otherwise
     * replies with its own latest supported revision and lets the client decide whether to proceed.
     *
     * @param requestedVersion the client's requested {@code protocolVersion} (may be null/blank)
     * @return the version to advertise back to the client
     */
    static String negotiateProtocolVersion(String requestedVersion) {
        if (requestedVersion != null && SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)) {
            return requestedVersion;
        }
        return LATEST_PROTOCOL_VERSION;
    }

    private InitializeResult handleInitialize(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        McpSession session = sessionManager.createSession();
        String sessionId = session.getSessionId();

        String requestedVersion = null;
        JsonNode initParams = rpcRequest.getParams();
        if (initParams != null) {
            requestedVersion = initParams.path("protocolVersion").asText(null);
        }
        String negotiatedVersion = negotiateProtocolVersion(requestedVersion);
        session.setProtocolVersion(negotiatedVersion);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", negotiatedVersion);

        ObjectNode capabilities = result.putObject("capabilities");
        ObjectNode toolsCap = capabilities.putObject("tools");
        toolsCap.put("listChanged", false);
        ObjectNode resourcesCap = capabilities.putObject("resources");
        resourcesCap.put("subscribe", false);
        resourcesCap.put("listChanged", false);
        ObjectNode promptsCap = capabilities.putObject("prompts");
        promptsCap.put("listChanged", false);
        capabilities.putObject("sampling");

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);

        result.put("instructions", SERVER_INSTRUCTIONS);

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

    private JsonRpcMessage.JsonRpcResponse handleToolsCall(JsonRpcMessage.JsonRpcRequest rpcRequest, String mcpSessionId, ControlPlaneAuthentication authentication) {
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

        // Control-plane authorization: a mutating tool requires the MUTATE role, a reading
        // tool the READ role. Delegates to the SAME authorizer/role model as the HTTP control
        // plane and is a no-op (always allowed) when controlPlaneAuthorizationEnabled is off
        // (the default), so default behaviour is unchanged. The per-tool read/mutate split is
        // required because all tool calls arrive as a single tools/call POST.
        boolean isRead = !McpToolRegistry.isMutatingTool(toolName);
        if (!httpState.controlPlaneToolAuthorized(authentication.settings(), authentication.scopes(), isRead, toolName)) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_REQUEST,
                "Forbidden for control plane");
        }

        JsonNode arguments = params.path("arguments");
        JsonNode toolResult = toolRegistry.callTool(toolName, arguments.isMissingNode() ? null : arguments);
        Metrics.incrementMcpToolCall(toolName);

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

        // Structured tool output (MCP 2025-06-18): when the tool returns a JSON object and the
        // session negotiated 2025-06-18 (or later), also surface the object as machine-readable
        // 'structuredContent' alongside the human-readable text block. Sessions on older revisions
        // (2025-03-26 / 2024-11-05) do not receive this field, keeping their responses unchanged.
        if (structuredOutputNegotiated(mcpSessionId) && toolResult != null && toolResult.isObject()) {
            result.set("structuredContent", toolResult);
        }

        boolean isError = toolResult != null && toolResult.has("error") && toolResult.path("error").asBoolean(false);
        result.put("isError", isError);

        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), result);
    }

    /**
     * Whether the given session negotiated an MCP revision that supports structured tool output
     * (2025-06-18 or later). Revisions are ISO-8601 dates, so lexical comparison is a valid ordering.
     */
    private boolean structuredOutputNegotiated(String mcpSessionId) {
        if (mcpSessionId == null) {
            return false;
        }
        McpSession session = sessionManager.getSession(mcpSessionId);
        String version = session == null ? null : session.getProtocolVersion();
        return version != null && version.compareTo(STRUCTURED_OUTPUT_MIN_VERSION) >= 0;
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

    private JsonRpcMessage.JsonRpcResponse handlePromptsList(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode promptsArray = result.putArray("prompts");

        for (McpPromptRegistry.PromptDefinition prompt : promptRegistry.getPrompts().values()) {
            ObjectNode promptNode = objectMapper.createObjectNode();
            promptNode.put("name", prompt.getName());
            promptNode.put("description", prompt.getDescription());
            ArrayNode argumentsArray = promptNode.putArray("arguments");
            for (McpPromptRegistry.PromptArgument argument : prompt.getArguments()) {
                ObjectNode argumentNode = objectMapper.createObjectNode();
                argumentNode.put("name", argument.getName());
                argumentNode.put("description", argument.getDescription());
                argumentNode.put("required", argument.isRequired());
                argumentsArray.add(argumentNode);
            }
            promptsArray.add(promptNode);
        }

        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), result);
    }

    private JsonRpcMessage.JsonRpcResponse handlePromptsGet(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        JsonNode params = rpcRequest.getParams();
        if (params == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS, "Missing params");
        }

        String name = params.path("name").asText(null);
        if (name == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS, "Missing prompt name");
        }

        McpPromptRegistry.PromptDefinition promptDef = promptRegistry.getPrompts().get(name);
        if (promptDef == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS,
                "Unknown prompt: " + name);
        }

        JsonNode arguments = params.path("arguments");
        ArrayNode messages = promptRegistry.getMessages(name, arguments.isMissingNode() ? null : arguments);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("description", promptDef.getDescription());
        result.set("messages", messages);

        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), result);
    }

    /**
     * Mocked {@code sampling/createMessage} — returns a deterministic mocked model completion in the
     * MCP sampling result shape ({@code role}, {@code content}, {@code model}, {@code stopReason}).
     * The completion text is taken from the (optional) {@code mockResponse} param, the model echoes the
     * client's preferred model when supplied, and the stop reason defaults to {@code endTurn}.
     */
    private JsonRpcMessage.JsonRpcResponse handleSamplingCreateMessage(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        JsonNode params = rpcRequest.getParams();
        if (params == null) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS, "Missing params");
        }

        JsonNode messagesNode = params.path("messages");
        if (!messagesNode.isArray() || messagesNode.size() == 0) {
            return JsonRpcMessage.JsonRpcResponse.error(rpcRequest.getId(), JsonRpcMessage.INVALID_PARAMS,
                "'messages' must be a non-empty array");
        }

        // The mocked completion text: an explicit 'mockResponse' override, else a deterministic echo.
        String text = params.path("mockResponse").asText(null);
        if (text == null) {
            text = "This is a mocked completion from MockServer's sampling/createMessage handler.";
        }

        // Echo the client's preferred model when supplied, else a default model name.
        String model = DEFAULT_SAMPLING_MODEL;
        JsonNode preferences = params.path("modelPreferences");
        JsonNode hints = preferences.path("hints");
        if (hints.isArray() && hints.size() > 0) {
            String hintName = hints.get(0).path("name").asText(null);
            if (hintName != null && !hintName.isEmpty()) {
                model = hintName;
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("role", "assistant");
        ObjectNode content = result.putObject("content");
        content.put("type", "text");
        content.put("text", text);
        result.put("model", model);
        result.put("stopReason", SAMPLING_STOP_REASON);

        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), result);
    }

    private JsonRpcMessage.JsonRpcResponse handlePing(JsonRpcMessage.JsonRpcRequest rpcRequest) {
        return JsonRpcMessage.JsonRpcResponse.success(rpcRequest.getId(), objectMapper.createObjectNode());
    }

    // ---- response construction helpers ----

    private McpResult jsonResponse(int statusCode, JsonRpcMessage.JsonRpcResponse rpcResponse, String sessionId) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(rpcResponse);
            return new McpResult(statusCode, jsonBytes, sessionId);
        } catch (JsonProcessingException e) {
            byte[] fallback = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"},\"id\":null}".getBytes(StandardCharsets.UTF_8);
            return new McpResult(statusCode, fallback, sessionId);
        }
    }

    private McpResult rawJsonResponse(int statusCode, JsonNode jsonNode, String sessionId) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(jsonNode);
            return new McpResult(statusCode, jsonBytes, sessionId);
        } catch (JsonProcessingException e) {
            byte[] fallback = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"},\"id\":null}".getBytes(StandardCharsets.UTF_8);
            return new McpResult(statusCode, fallback, sessionId);
        }
    }

    private McpResult emptyResponse(int statusCode) {
        return new McpResult(statusCode, new byte[0], null);
    }
}
