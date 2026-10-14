package org.mockserver.mock.action.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.matchers.GraphQLAstMatcher;
import org.mockserver.mock.breakpoint.PausedStreamFrame;
import org.mockserver.mock.breakpoint.StreamFrameBreakpointRegistry;
import org.mockserver.model.Delay;
import org.mockserver.model.GraphQLBody;
import org.mockserver.model.SelectionSetMatchType;
import org.mockserver.model.WebSocketMessage;
import org.mockserver.socket.ChannelReadPause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">graphql-transport-ws</a>
 * protocol over an already-established WebSocket connection.
 * <p>
 * Installed after the WebSocket handshake when the negotiated subprotocol is
 * {@code graphql-transport-ws} or the legacy {@code graphql-ws}.
 * <p>
 * Protocol messages handled:
 * <ul>
 *   <li>{@code connection_init} - replies {@code connection_ack}</li>
 *   <li>{@code ping} - replies {@code pong}</li>
 *   <li>{@code subscribe} - AST-matches the query against the configured subscription expectation;
 *       on match pushes a scripted sequence of {@code next} messages then {@code complete};
 *       on no match sends {@code error}</li>
 *   <li>{@code complete} (client) - cancels that subscription's pending messages</li>
 * </ul>
 *
 * <p><b>Inbound breakpoints (A1e):</b> when an INBOUND_STREAM breakpoint matcher is registered and
 * inbound stream ID is configured, incoming WebSocket frames are parked in the
 * {@link StreamFrameBreakpointRegistry} before protocol dispatch. The frame text is copied
 * to {@code byte[]} (UTF-8) at park time and the original {@link TextWebSocketFrame} is
 * released immediately. On resume, the text is reconstructed from the captured/modified bytes.
 * Backpressure is applied via {@code autoRead=false} while a frame is parked.
 */
public class GraphQLSubscriptionHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLSubscriptionHandler.class);

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** The modern protocol — <a href="https://github.com/enisdenjo/graphql-ws">graphql-ws</a> library. */
    static final String MODERN_SUBPROTOCOL = "graphql-transport-ws";
    /** The legacy protocol — {@code subscriptions-transport-ws}, still what Apollo Client defaults to. */
    static final String LEGACY_SUBPROTOCOL = "graphql-ws";

    /**
     * True when the negotiated subprotocol is the legacy {@code graphql-ws}
     * (subscriptions-transport-ws) protocol, whose vocabulary differs from graphql-transport-ws:
     * the client subscribes with {@code start} rather than {@code subscribe}, cancels with
     * {@code stop} rather than {@code complete}, and the server pushes {@code data} rather
     * than {@code next}.
     */
    private final boolean legacyProtocol;

    private final GraphQLAstMatcher astMatcher;
    private final List<WebSocketMessage> subscriptionPayloads;
    private final FrameSender frameSender;
    private final WebSocketServerHandshaker handshaker;

    // Inbound breakpoint fields — null when inbound breakpoints are disabled
    private final Configuration configuration;
    private final String inboundStreamId;
    // Pre-computed inbound breakpoint WS dispatch fields (CPX-01)
    private final boolean inboundUseWsDispatch;
    private final String inboundBreakpointClientId;
    private final String inboundBreakpointId;
    private final WebSocketClientRegistry webSocketClientRegistry;

    /**
     * Tracks active subscription IDs so that a client {@code complete} message
     * can signal cancellation. The handler checks this set before each scheduled
     * {@code next} push.
     */
    private final Set<String> activeSubscriptions = ConcurrentHashMap.newKeySet();

    private boolean connectionInitialised = false;

    /**
     * Callback interface for sending text frames to the client, with optional delay support.
     */
    public interface FrameSender {
        /**
         * Send a text frame, optionally after a delay, then run {@code onSent}.
         * <p>
         * {@code onSent} MUST be invoked only once the frame has actually been written to the
         * wire (or has been deliberately discarded), never before the delay has elapsed. The
         * subscription sequencer chains the next {@code next} message — and ultimately the
         * terminal {@code complete} — off this callback, so an implementation that runs it
         * eagerly re-introduces the defect where {@code complete} overtakes the delayed
         * {@code next} frames and every message is discarded by the client as post-terminal.
         *
         * @param ctx    the channel context
         * @param text   the text to send
         * @param delay  optional delay before sending (may be null)
         * @param onSent continuation invoked after the frame has been written or discarded
         */
        void send(ChannelHandlerContext ctx, String text, Delay delay, Runnable onSent);
    }

    /**
     * Original constructor — no inbound breakpoint support (backward compatible).
     *
     * @param expectedSubscriptionQuery a GraphQLBody describing the subscription query to match
     * @param subscriptionPayloads      the sequence of payloads to push as {@code next} messages
     * @param frameSender               callback for sending text frames with optional delays
     * @param handshaker                the WebSocket handshaker for closing the connection
     */
    public GraphQLSubscriptionHandler(
        GraphQLBody expectedSubscriptionQuery,
        List<WebSocketMessage> subscriptionPayloads,
        FrameSender frameSender,
        WebSocketServerHandshaker handshaker
    ) {
        this(expectedSubscriptionQuery, subscriptionPayloads, frameSender, handshaker, null, null, null, null, null);
    }

    /**
     * Constructor with inbound breakpoint support and pre-resolved breakpoint identity.
     *
     * @param expectedSubscriptionQuery  a GraphQLBody describing the subscription query to match
     * @param subscriptionPayloads       the sequence of payloads to push as {@code next} messages
     * @param frameSender                callback for sending text frames with optional delays
     * @param handshaker                 the WebSocket handshaker for closing the connection
     * @param configuration              the active server configuration (null to disable inbound breakpoints)
     * @param inboundStreamId            the stream ID for inbound breakpoints (null to disable)
     * @param webSocketClientRegistry    the per-server WS registry for callback dispatch (null to disable WS dispatch)
     * @param inboundBreakpointClientId  the matched inbound breakpoint's owning clientId (from outer caller)
     * @param inboundBreakpointId        the matched inbound breakpoint's id (from outer caller)
     */
    public GraphQLSubscriptionHandler(
        GraphQLBody expectedSubscriptionQuery,
        List<WebSocketMessage> subscriptionPayloads,
        FrameSender frameSender,
        WebSocketServerHandshaker handshaker,
        Configuration configuration,
        String inboundStreamId,
        WebSocketClientRegistry webSocketClientRegistry,
        String inboundBreakpointClientId,
        String inboundBreakpointId
    ) {
        this(expectedSubscriptionQuery, subscriptionPayloads, frameSender, handshaker, configuration,
            inboundStreamId, webSocketClientRegistry, inboundBreakpointClientId, inboundBreakpointId, null);
    }

    /**
     * Constructor taking the subprotocol actually negotiated during the handshake.
     *
     * @param negotiatedSubprotocol the negotiated WebSocket subprotocol; when this is the legacy
     *                              {@code graphql-ws} (subscriptions-transport-ws) protocol the handler
     *                              speaks that protocol's vocabulary ({@code start}/{@code stop}/{@code data})
     *                              instead of the graphql-transport-ws vocabulary
     */
    public GraphQLSubscriptionHandler(
        GraphQLBody expectedSubscriptionQuery,
        List<WebSocketMessage> subscriptionPayloads,
        FrameSender frameSender,
        WebSocketServerHandshaker handshaker,
        Configuration configuration,
        String inboundStreamId,
        WebSocketClientRegistry webSocketClientRegistry,
        String inboundBreakpointClientId,
        String inboundBreakpointId,
        String negotiatedSubprotocol
    ) {
        super(false); // don't auto-release frames
        this.legacyProtocol = LEGACY_SUBPROTOCOL.equals(negotiatedSubprotocol);
        this.astMatcher = createMatcher(expectedSubscriptionQuery);
        this.subscriptionPayloads = subscriptionPayloads != null ? subscriptionPayloads : Collections.emptyList();
        this.frameSender = frameSender;
        this.handshaker = handshaker;
        this.configuration = configuration;
        this.inboundStreamId = inboundStreamId;
        this.webSocketClientRegistry = webSocketClientRegistry;
        // Use the matched breakpoint's clientId and id passed from the outer caller
        // (avoids re-matching with null request which can pick the wrong breakpoint)
        if (inboundStreamId != null && inboundBreakpointClientId != null && webSocketClientRegistry != null) {
            this.inboundUseWsDispatch = true;
            this.inboundBreakpointClientId = inboundBreakpointClientId;
            this.inboundBreakpointId = inboundBreakpointId;
        } else {
            this.inboundUseWsDispatch = false;
            this.inboundBreakpointClientId = null;
            this.inboundBreakpointId = null;
        }
    }

    private static GraphQLAstMatcher createMatcher(GraphQLBody body) {
        return new GraphQLAstMatcher(normaliseSubscriptionBody(body));
    }

    /**
     * Normalises the subscription filter body: when no {@code selectionSetMatchType}
     * is supplied, default to {@code AST_SUBSET} (forgiving) while PRESERVING any
     * explicitly-configured {@code fields}. Package-private for testing.
     */
    static GraphQLBody normaliseSubscriptionBody(GraphQLBody body) {
        if (body.getSelectionSetMatchType() == null) {
            // Capture the original fields BEFORE reassigning `body`, otherwise they are lost.
            List<String> originalFields = body.getFields();
            body = new GraphQLBody(body.getQuery(), body.getOperationName(), body.getVariablesSchema())
                .withSelectionSetMatchType(SelectionSetMatchType.AST_SUBSET);
            if (originalFields != null) {
                body.withFields(originalFields);
            }
        }
        return body;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (!(frame instanceof TextWebSocketFrame textFrame)) {
            frame.release();
            return;
        }

        String text = textFrame.text();
        frame.release();

        // --- Inbound breakpoint interception ---
        if (inboundStreamId != null) {

            byte[] frameBytes = text.getBytes(StandardCharsets.UTF_8);

            // WS-callback dispatch (clientId is always present — required since 7b)
            final java.util.concurrent.CompletableFuture<org.mockserver.mock.breakpoint.StreamFrameDecision> decisionFuture;
            int seq = StreamFrameBreakpointRegistry.getInstance().nextSequenceNumber(inboundStreamId);
            java.util.concurrent.CompletableFuture<org.mockserver.mock.breakpoint.StreamFrameDecision> wsFuture =
                org.mockserver.mock.breakpoint.StreamFrameCallbackDispatcher.getInstance().dispatchFrame(
                    inboundBreakpointClientId, inboundBreakpointId, inboundStreamId, seq, PausedStreamFrame.Direction.INBOUND,
                    org.mockserver.mock.breakpoint.BreakpointPhase.INBOUND_STREAM,
                    frameBytes, "GQL-INBOUND", "/", null, webSocketClientRegistry, configuration, null);
            if (wsFuture != null) {
                decisionFuture = wsFuture;
            } else {
                processText(ctx, text);
                return;
            }

            // Apply backpressure
            ChannelReadPause.pause(ctx.channel());

            decisionFuture.thenAccept(decision ->
                ctx.channel().eventLoop().execute(() -> {
                    try {
                        // release this frame's hold; reading resumes once no other hold remains
                        ChannelReadPause.resume(ctx.channel());
                        if (!ctx.channel().isActive()) {
                            return;
                        }

                        switch (decision.getAction()) {
                            case CONTINUE ->
                                processText(ctx, new String(frameBytes, StandardCharsets.UTF_8));
                            case MODIFY ->
                                processText(ctx, new String(decision.getReplacementBody(), StandardCharsets.UTF_8));
                            case DROP -> {
                                // Discard — do not process
                            }
                            case INJECT -> {
                                processText(ctx, new String(frameBytes, StandardCharsets.UTF_8));
                                processText(ctx, new String(decision.getInjectedBody(), StandardCharsets.UTF_8));
                            }
                            case CLOSE -> {
                                StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
                                closeConnection(ctx);
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("error processing inbound breakpoint decision for GraphQL stream {}", inboundStreamId, e);
                    }
                })
            ).exceptionally(ex -> {
                LOG.debug("inbound breakpoint decision callback failed for GraphQL stream {}: {}", inboundStreamId, ex.getMessage());
                ChannelReadPause.resume(ctx.channel());
                return null;
            });
            return;
        }

        // --- Default path (no inbound breakpoints) ---
        processText(ctx, text);
    }

    /**
     * Process a text message through the graphql-transport-ws protocol state machine.
     * Extracted so it can be called from both the default path and the breakpoint resume path.
     */
    private void processText(ChannelHandlerContext ctx, String text) {
        try {
            JsonNode message = OBJECT_MAPPER.readTree(text);
            String type = message.has("type") ? message.get("type").asText() : "";

            switch (type) {
                case "connection_init" -> handleConnectionInit(ctx);
                case "ping" -> handlePing(ctx);
                // graphql-transport-ws vocabulary
                case "subscribe" -> handleSubscribe(ctx, message);
                case "complete" -> handleClientComplete(message);
                // legacy graphql-ws (subscriptions-transport-ws) vocabulary
                case "start" -> handleSubscribe(ctx, message);
                case "stop" -> handleClientComplete(message);
                case "connection_terminate" -> closeConnection(ctx);
                default -> {
                    // Unknown message type -- ignore per the protocol spec
                }
            }
        } catch (JsonProcessingException e) {
            // Malformed JSON -- close the connection per the protocol spec
            closeConnection(ctx);
        }
    }

    private void handleConnectionInit(ChannelHandlerContext ctx) {
        connectionInitialised = true;
        sendImmediate(ctx, "{\"type\":\"connection_ack\"}");
    }

    private void handlePing(ChannelHandlerContext ctx) {
        sendImmediate(ctx, "{\"type\":\"pong\"}");
    }

    void handleSubscribe(ChannelHandlerContext ctx, JsonNode message) {
        // Per graphql-transport-ws, a client must not send subscribe before the
        // connection_init/connection_ack handshake; close the connection if it does.
        if (!connectionInitialised) {
            closeConnection(ctx);
            return;
        }

        String id = message.has("id") ? message.get("id").asText() : null;
        if (id == null || id.isEmpty()) {
            closeConnection(ctx);
            return;
        }

        // Check for duplicate subscription ID
        if (activeSubscriptions.contains(id)) {
            closeConnection(ctx);
            return;
        }

        // Extract the query from the subscribe payload
        JsonNode payload = message.get("payload");
        String query = null;
        if (payload != null && payload.has("query")) {
            query = payload.get("query").asText();
        }

        if (query == null || query.isEmpty()) {
            sendError(ctx, id, "No query provided in subscribe message");
            return;
        }

        // Match against the configured subscription expectation
        if (!astMatcher.matches(query)) {
            sendError(ctx, id, "No matching subscription expectation found for query: " + query);
            return;
        }

        // Matched -- push the configured payloads as 'next' messages, then 'complete'
        activeSubscriptions.add(id);
        pushNextSequence(ctx, id, 0);
    }

    private void pushNextSequence(ChannelHandlerContext ctx, String subscriptionId, int index) {
        if (!ctx.channel().isActive() || !activeSubscriptions.contains(subscriptionId)) {
            activeSubscriptions.remove(subscriptionId);
            return;
        }

        if (index >= subscriptionPayloads.size()) {
            // All payloads sent -- send 'complete'
            sendComplete(ctx, subscriptionId);
            activeSubscriptions.remove(subscriptionId);
            return;
        }

        WebSocketMessage payload = subscriptionPayloads.get(index);
        String nextJson = buildNextMessage(subscriptionId, payload);

        if (nextJson == null) {
            // Skip payloads with no text content
            pushNextSequence(ctx, subscriptionId, index + 1);
            return;
        }

        // Chain strictly off the send completion: message index + 1 is only pushed once
        // index has actually reached the wire, and `complete` is only written after the
        // final `next`. Recursing eagerly here (the previous behaviour) let the terminal
        // `complete` overtake every delayed `next`, and both Apollo and graphql-ws discard
        // messages received after `complete` -- so a subscription with any delay set
        // delivered nothing at all.
        frameSender.send(ctx, nextJson, payload.getDelay(),
            () -> pushNextSequence(ctx, subscriptionId, index + 1));
    }

    /**
     * Build a {@code next} protocol message wrapping the given payload.
     * The payload text is interpreted as JSON if possible (for proper nesting),
     * otherwise embedded as a string.
     */
    String buildNextMessage(String subscriptionId, WebSocketMessage payload) {
        String data = payload.getText();
        if (data == null) {
            return null;
        }

        ObjectNode nextMessage = OBJECT_MAPPER.createObjectNode();
        nextMessage.put("id", subscriptionId);
        // legacy subscriptions-transport-ws calls this message `data`, not `next`
        nextMessage.put("type", legacyProtocol ? "data" : "next");

        ObjectNode payloadWrapper = OBJECT_MAPPER.createObjectNode();
        try {
            JsonNode payloadNode = OBJECT_MAPPER.readTree(data);
            payloadWrapper.set("data", payloadNode);
        } catch (JsonProcessingException e) {
            payloadWrapper.put("data", data);
        }
        nextMessage.set("payload", payloadWrapper);

        try {
            return OBJECT_MAPPER.writeValueAsString(nextMessage);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void handleClientComplete(JsonNode message) {
        String id = message.has("id") ? message.get("id").asText() : null;
        if (id != null) {
            activeSubscriptions.remove(id);
        }
    }

    private void sendImmediate(ChannelHandlerContext ctx, String text) {
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(new TextWebSocketFrame(text));
        }
    }

    private void sendError(ChannelHandlerContext ctx, String subscriptionId, String errorMessage) {
        ObjectNode errorMsg = OBJECT_MAPPER.createObjectNode();
        errorMsg.put("id", subscriptionId);
        errorMsg.put("type", "error");
        ObjectNode errorPayload = OBJECT_MAPPER.createObjectNode();
        errorPayload.put("message", errorMessage);
        if (legacyProtocol) {
            // legacy subscriptions-transport-ws carries a single error object, not a list
            errorMsg.set("payload", errorPayload);
        } else {
            ArrayNode errorArray = OBJECT_MAPPER.createArrayNode();
            errorArray.add(errorPayload);
            errorMsg.set("payload", errorArray);
        }
        try {
            sendImmediate(ctx, OBJECT_MAPPER.writeValueAsString(errorMsg));
        } catch (JsonProcessingException e) {
            closeConnection(ctx);
        }
    }

    private void sendComplete(ChannelHandlerContext ctx, String subscriptionId) {
        ObjectNode completeMsg = OBJECT_MAPPER.createObjectNode();
        completeMsg.put("id", subscriptionId);
        completeMsg.put("type", "complete");
        try {
            sendImmediate(ctx, OBJECT_MAPPER.writeValueAsString(completeMsg));
        } catch (JsonProcessingException e) {
            // Best effort
        }
    }

    /**
     * A 1000 NORMAL_CLOSURE frame — an empty close frame leaves the client reporting
     * close code 1005 "no status received".
     */
    private static CloseWebSocketFrame normalClosure() {
        return new CloseWebSocketFrame(io.netty.handler.codec.http.websocketx.WebSocketCloseStatus.NORMAL_CLOSURE);
    }

    private void closeConnection(ChannelHandlerContext ctx) {
        if (handshaker != null && ctx.channel().isActive()) {
            handshaker.close(ctx.channel(), normalClosure());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        closeConnection(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Evict any held inbound frames on channel close to prevent leaks
        if (inboundStreamId != null) {
            StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
        }
        super.channelInactive(ctx);
    }

    /**
     * Check whether the given subprotocol string indicates a graphql-transport-ws
     * or legacy graphql-ws protocol.
     */
    public static boolean isGraphQLWebSocketProtocol(String subprotocol) {
        return MODERN_SUBPROTOCOL.equals(subprotocol) || LEGACY_SUBPROTOCOL.equals(subprotocol);
    }
}
