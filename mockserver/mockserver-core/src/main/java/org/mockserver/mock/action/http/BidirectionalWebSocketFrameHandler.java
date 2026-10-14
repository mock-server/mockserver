package org.mockserver.mock.action.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.mock.breakpoint.PausedStreamFrame;
import org.mockserver.mock.breakpoint.StreamFrameBreakpointRegistry;
import org.mockserver.model.WebSocketFrameType;
import org.mockserver.model.WebSocketMessage;
import org.mockserver.model.WebSocketMessageMatcher;
import org.mockserver.socket.ChannelReadPause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * Installed after a WebSocket handshake when the HttpWebSocketResponse has matchers.
 * Evaluates incoming frames against the matcher list in order; first match sends its responses.
 *
 * <p><b>Inbound breakpoints (A1e):</b> when an INBOUND_STREAM breakpoint matcher is registered and
 * inbound stream ID is configured, incoming WebSocket frames are parked in the
 * {@link StreamFrameBreakpointRegistry} before matcher evaluation. The frame bytes are copied
 * to {@code byte[]} at park time and the original {@link WebSocketFrame} is released immediately
 * (this handler uses {@code super(false)} — no auto-release, so we manage the lifecycle). On
 * resume, a new frame is reconstructed from the captured/modified bytes.
 *
 * <p><b>Backpressure:</b> while a frame is parked the channel's reads are paused (a
 * {@link ChannelReadPause} hold), preventing further inbound frames from being read; the hold is
 * released when the decision arrives. Pending delayed replies can hold the same channel's reads
 * too, so reading resumes only once neither needs it paused.
 */
public class BidirectionalWebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger LOG = LoggerFactory.getLogger(BidirectionalWebSocketFrameHandler.class);

    private final List<WebSocketMessageMatcher> matchers;
    private final FrameSender frameSender;

    // Inbound breakpoint fields — null when inbound breakpoints are disabled
    private final Configuration configuration;
    private final String inboundStreamId;
    // Pre-computed inbound breakpoint WS dispatch fields (CPX-01)
    private final boolean inboundUseWsDispatch;
    private final String inboundBreakpointClientId;
    private final String inboundBreakpointId;
    private final WebSocketClientRegistry webSocketClientRegistry;

    /**
     * Callback interface for sending response frames to the client.
     */
    public interface FrameSender {
        void send(ChannelHandlerContext ctx, WebSocketMessage message);

        /**
         * Send a matcher's whole reply set. The default sends each message independently; an implementation
         * that delays frames overrides it to keep the set whole and in order.
         */
        default void sendAll(ChannelHandlerContext ctx, List<WebSocketMessage> messages) {
            for (WebSocketMessage message : messages) {
                send(ctx, message);
            }
        }
    }

    /**
     * Original constructor — no inbound breakpoint support (backward compatible).
     */
    public BidirectionalWebSocketFrameHandler(List<WebSocketMessageMatcher> matchers, FrameSender frameSender) {
        this(matchers, frameSender, null, null, null, null, null);
    }

    /**
     * Constructor with inbound breakpoint support and pre-resolved breakpoint identity.
     *
     * @param matchers                   the matcher list for bidirectional matching
     * @param frameSender                callback for sending response frames
     * @param configuration              the active server configuration (null to disable inbound breakpoints)
     * @param inboundStreamId            the stream ID for inbound breakpoints (null to disable)
     * @param webSocketClientRegistry    the per-server WS registry for callback dispatch (null to disable WS dispatch)
     * @param inboundBreakpointClientId  the matched inbound breakpoint's owning clientId (from outer caller)
     * @param inboundBreakpointId        the matched inbound breakpoint's id (from outer caller)
     */
    public BidirectionalWebSocketFrameHandler(List<WebSocketMessageMatcher> matchers, FrameSender frameSender,
                                              Configuration configuration, String inboundStreamId,
                                              WebSocketClientRegistry webSocketClientRegistry,
                                              String inboundBreakpointClientId, String inboundBreakpointId) {
        super(false); // don't auto-release frames — retain for pass-through if unmatched
        this.matchers = matchers;
        this.frameSender = frameSender;
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

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // --- Inbound breakpoint interception ---
        if (inboundStreamId != null) {

            // Copy frame bytes BEFORE parking — the frame's ByteBuf is pooled and must be
            // released after this method returns (we use super(false), so WE own the release).
            byte[] frameBytes = copyFrameBytes(frame);
            boolean isBinary = frame instanceof BinaryWebSocketFrame;
            boolean isText = frame instanceof TextWebSocketFrame;
            boolean isPing = frame instanceof PingWebSocketFrame;
            boolean isPong = frame instanceof PongWebSocketFrame;

            // Release the original frame immediately — we have a byte[] copy.
            // This is safe because we NEVER touch the frame after this point.
            frame.release();

            // Use pre-computed WS dispatch decision (CPX-01: per-stream, not per-frame)
            final java.util.concurrent.CompletableFuture<org.mockserver.mock.breakpoint.StreamFrameDecision> decisionFuture;
            if (inboundUseWsDispatch) {
                int seq = StreamFrameBreakpointRegistry.getInstance().nextSequenceNumber(inboundStreamId);
                java.util.concurrent.CompletableFuture<org.mockserver.mock.breakpoint.StreamFrameDecision> wsFuture =
                    org.mockserver.mock.breakpoint.StreamFrameCallbackDispatcher.getInstance().dispatchFrame(
                        inboundBreakpointClientId, inboundBreakpointId, inboundStreamId, seq, PausedStreamFrame.Direction.INBOUND,
                        org.mockserver.mock.breakpoint.BreakpointPhase.INBOUND_STREAM,
                        frameBytes, "WS-INBOUND", "/", null, webSocketClientRegistry, configuration, null);
                if (wsFuture != null) {
                    decisionFuture = wsFuture;
                } else {
                    // WS dispatch rejected (cap/not connected) -- process immediately
                    processInboundFrame(ctx, frameBytes, isBinary, isText, isPing, isPong);
                    return;
                }
            } else {
                PausedStreamFrame paused = StreamFrameBreakpointRegistry.getInstance()
                    .pauseFrame(inboundStreamId, frameBytes, "WS-INBOUND", "/",
                        configuration, PausedStreamFrame.Direction.INBOUND);
                if (paused == null) {
                    // Cap reached — process immediately with the copied bytes
                    processInboundFrame(ctx, frameBytes, isBinary, isText, isPing, isPong);
                    return;
                }
                decisionFuture = paused.getDecisionFuture();
            }

            // Apply backpressure: stop reading more inbound frames
            ChannelReadPause.pause(ctx.channel());

            // Chain the decision onto the channel's event loop (NEVER block the event loop)
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
                                processInboundFrame(ctx, frameBytes, isBinary, isText, isPing, isPong);
                            case MODIFY ->
                                processInboundFrame(ctx, decision.getReplacementBody(), isBinary, isText, isPing, isPong);
                            case DROP -> {
                                // Discard — do not process
                            }
                            case INJECT -> {
                                // Process the original frame, then also process the injected frame
                                processInboundFrame(ctx, frameBytes, isBinary, isText, isPing, isPong);
                                processInboundFrame(ctx, decision.getInjectedBody(), isBinary, isText, isPing, isPong);
                            }
                            case CLOSE -> {
                                StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
                                ctx.close();
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("error processing inbound breakpoint decision for stream {}", inboundStreamId, e);
                    }
                })
            ).exceptionally(ex -> {
                LOG.debug("inbound breakpoint decision callback failed for stream {}: {}", inboundStreamId, ex.getMessage());
                // Release the hold on failure so the channel is not stuck
                ChannelReadPause.resume(ctx.channel());
                return null;
            });
            return;
        }

        // --- Default path (no inbound breakpoints) ---
        for (WebSocketMessageMatcher matcher : matchers) {
            if (matches(matcher, frame)) {
                if (matcher.getResponses() != null) {
                    frameSender.sendAll(ctx, matcher.getResponses());
                }
                frame.release();
                return; // first match wins
            }
        }
        // No matcher matched — pass frame through to next handler
        ctx.fireChannelRead(frame);
    }

    /**
     * Process an inbound frame from byte[] (after breakpoint resolution): reconstruct the
     * WebSocketFrame, run matcher evaluation, and either send responses or pass through.
     */
    private void processInboundFrame(ChannelHandlerContext ctx, byte[] frameBytes,
                                     boolean isBinary, boolean isText, boolean isPing, boolean isPong) {
        WebSocketFrame reconstructed = reconstructFrame(frameBytes, isBinary, isText, isPing, isPong);
        try {
            for (WebSocketMessageMatcher matcher : matchers) {
                if (matches(matcher, reconstructed)) {
                    if (matcher.getResponses() != null) {
                        frameSender.sendAll(ctx, matcher.getResponses());
                    }
                    return; // first match wins
                }
            }
            // No matcher matched — pass frame through (ownership transfers to pipeline)
            ctx.fireChannelRead(reconstructed.retain());
        } finally {
            reconstructed.release();
        }
    }

    /**
     * Copy the readable bytes from a WebSocketFrame's content to a byte[].
     * The original ByteBuf's reader index is not advanced.
     */
    private static byte[] copyFrameBytes(WebSocketFrame frame) {
        byte[] bytes = new byte[frame.content().readableBytes()];
        frame.content().getBytes(frame.content().readerIndex(), bytes);
        return bytes;
    }

    /**
     * Reconstruct a WebSocketFrame from byte[] and type flags.
     * Returns a new frame backed by Unpooled.wrappedBuffer (no pooled ByteBuf).
     */
    private static WebSocketFrame reconstructFrame(byte[] bytes, boolean isBinary, boolean isText,
                                                   boolean isPing, boolean isPong) {
        if (isBinary) {
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(bytes));
        } else if (isPing) {
            return new PingWebSocketFrame(Unpooled.wrappedBuffer(bytes));
        } else if (isPong) {
            return new PongWebSocketFrame(Unpooled.wrappedBuffer(bytes));
        } else {
            // Default to text
            return new TextWebSocketFrame(Unpooled.wrappedBuffer(bytes));
        }
    }

    boolean matches(WebSocketMessageMatcher matcher, WebSocketFrame frame) {
        // Check frame type
        WebSocketFrameType expected = matcher.getFrameType();
        if (expected != null && expected != WebSocketFrameType.ANY) {
            if (expected == WebSocketFrameType.TEXT && !(frame instanceof TextWebSocketFrame)) {
                return false;
            }
            if (expected == WebSocketFrameType.BINARY && !(frame instanceof BinaryWebSocketFrame)) {
                return false;
            }
            if (expected == WebSocketFrameType.PING && !(frame instanceof PingWebSocketFrame)) {
                return false;
            }
            if (expected == WebSocketFrameType.PONG && !(frame instanceof PongWebSocketFrame)) {
                return false;
            }
        }
        // Check text content if matcher has a text matcher
        if (matcher.getTextMatcher() != null) {
            String pattern = matcher.getTextMatcher().getValue();
            if (pattern != null && !pattern.isEmpty()) {
                // A text matcher can only be satisfied by a text frame. Previously a non-text
                // frame simply skipped this block and fell through to `return true`, so a
                // matcher configured with text content matched every PING, PONG and BINARY
                // frame as well -- and its responses fired on client keepalive traffic.
                //
                // This applies in BOTH polarities: a negated text matcher does not vacuously
                // match a frame that carries no text either. A matcher expressed in terms of
                // text content simply does not apply to frames that have none, and treating
                // `not("x")` as true for a PING would reopen the same defect for negated
                // matchers -- responses firing on client keepalive traffic.
                if (!(frame instanceof TextWebSocketFrame textFrame)) {
                    return false;
                }
                String text = textFrame.text();
                boolean matched;
                // Try exact match first, then regex
                if (text.equals(pattern)) {
                    matched = true;
                } else {
                    try {
                        matched = text.matches(pattern);
                    } catch (PatternSyntaxException e) {
                        matched = false;
                    }
                }
                // honour the negation flag, as GrpcBidiRuleMatcher does for its equivalent rule
                // matcher — without this a negated text matcher is accepted and silently ignored
                return matcher.getTextMatcher().isNot() != matched;
            }
        }
        return true;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Evict any held inbound frames on channel close to prevent leaks
        if (inboundStreamId != null) {
            StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
        }
        super.channelInactive(ctx);
    }
}
