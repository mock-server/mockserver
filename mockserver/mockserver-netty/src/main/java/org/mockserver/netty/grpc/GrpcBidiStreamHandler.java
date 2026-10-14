package org.mockserver.netty.grpc;

import com.google.protobuf.Descriptors;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.grpc.GrpcException;
import org.mockserver.grpc.GrpcBidiRuleMatcher;
import org.mockserver.grpc.GrpcFrameCodec;
import org.mockserver.grpc.GrpcJsonMessageConverter;
import org.mockserver.grpc.GrpcStatusMapper;
import org.mockserver.grpc.GrpcStreamDeadline;
import org.mockserver.grpc.GrpcTimeout;
import org.mockserver.grpc.GrpcStreamMessageTemplateRenderer;
import org.mockserver.grpc.IncrementalGrpcFrameDecoder;
import org.mockserver.mock.breakpoint.PausedStreamFrame;
import org.mockserver.mock.breakpoint.StreamFrameBreakpointRegistry;
import org.mockserver.mock.breakpoint.StreamFrameDecision;
import org.mockserver.model.Delay;
import org.mockserver.model.GrpcBidiResponse;
import org.mockserver.model.GrpcBidiRule;
import org.mockserver.model.GrpcStreamMessage;
import org.mockserver.model.Header;
import org.mockserver.model.Headers;
import org.mockserver.model.NottableString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Per-stream handler for true bidirectional gRPC streaming. NOT {@code @Sharable} --
 * holds per-stream state (the incremental frame decoder, finished guard).
 * <p>
 * <strong>Phase 3b behaviour (rule-driven via GrpcBidiResponse):</strong>
 * <ul>
 *   <li>On {@link Http2HeadersFrame}: applies the top-level action delay (if configured)
 *       before writing the initial response HEADERS ({@code :status=200},
 *       {@code content-type=application/grpc}, plus any configured headers from the
 *       GrpcBidiResponse, {@code endStream=false}). Then writes any EAGER messages from
 *       the response, honouring per-message {@link GrpcStreamMessage#getDelay()} via
 *       event-loop scheduling (messages are chained so ordering is preserved and the
 *       trailing grpc-status is written only after all scheduled messages complete).</li>
 *   <li>On {@link Http2DataFrame}: feeds content bytes to {@link IncrementalGrpcFrameDecoder};
 *       for each complete inbound message, converts to JSON via the converter, evaluates
 *       rules in order (first match emits its responses as DATA frames). If no rule matches,
 *       no response is emitted. If the frame has {@code endStream=true}, calls
 *       {@link #finish(ChannelHandlerContext)}.</li>
 *   <li>On a second {@link Http2HeadersFrame}, the request's trailers: ends the request as END_STREAM on a DATA
 *       frame does.</li>
 *   <li>{@code finish()}: writes trailing HEADERS with configured grpc-status and
 *       {@code endStream=true}. Guarded to run at most once.</li>
 * </ul>
 * <p>
 * The handler supports two modes:
 * <ul>
 *   <li><strong>Phase 3b (GrpcBidiResponse-driven):</strong> Constructed with a
 *       {@link GrpcBidiResponse} config; eager messages, rules, status, and headers come
 *       from the config.</li>
 *   <li><strong>Phase 3a (legacy responder function):</strong> Constructed with a
 *       {@link Function} responder for backward compatibility with existing tests.</li>
 * </ul>
 * <p>
 * Flow control: the channel's autoRead is set to {@code false} when this handler is
 * added; after processing each inbound frame, {@code ctx.read()} is called to request
 * the next frame. If the decoder's buffer cap is exceeded, a RESOURCE_EXHAUSTED trailing
 * status is written and the stream is finished.
 * <p>
 * Error handling: exceptions during channelRead are caught and result in an INTERNAL
 * grpc-status trailer, never an uncaught exception propagating up the pipeline.
 */
public class GrpcBidiStreamHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcBidiStreamHandler.class);

    private final Descriptors.MethodDescriptor methodDescriptor;
    private final GrpcJsonMessageConverter converter;
    private final IncrementalGrpcFrameDecoder decoder;
    /**
     * Terminal guard for the stream. An {@link AtomicBoolean} rather than a {@code volatile
     * boolean}: the previous check-then-set was not atomic, so two terminal paths (normal finish,
     * error trailer, and now an elapsed deadline) could each observe {@code false} and both write a
     * terminal HEADERS frame. The CAS makes exactly one win.
     */
    private final AtomicBoolean finished = new AtomicBoolean();

    /**
     * Enforces the client's grpc-timeout for this streaming RPC, terminating it mid-stream with
     * DEADLINE_EXCEEDED. Scheduled on this stream's own child channel, so it is naturally
     * per-stream and dies with the stream.
     */
    private final GrpcStreamDeadline deadline = new GrpcStreamDeadline();

    /**
     * Set once the initial response HEADERS are on the wire. The initial HEADERS are deferred by
     * {@code config.getDelay()}, so a {@code grpc-timeout} shorter than that delay can fire first --
     * and a terminal frame written before them must then be a complete Trailers-Only response
     * (carrying {@code :status}), not a bare trailing HEADERS frame with only {@code grpc-status}.
     * Mirrors {@code Http3GrpcResponseWriter}'s stream-state handling.
     */
    private final AtomicBoolean initialHeadersWritten = new AtomicBoolean();

    // confined to the stream's event loop
    private boolean requestHeadersRead;

    // Phase 3a mode: function-based responder
    private final Function<String, List<String>> responder;

    // Phase 3b mode: GrpcBidiResponse-driven
    private final GrpcBidiResponse config;

    // Renders bidi response messages, applying optional per-message response templating against
    // the matched inbound message. Static (no templateType) messages pass through unchanged.
    private final GrpcStreamMessageTemplateRenderer templateRenderer;

    // Completion callback: invoked exactly once when the stream finishes (normal or error)
    // or when the channel becomes inactive (client disconnect / abandoned stream).
    // Clears responseInProgress on the matched expectation so a times-limited expectation
    // is not left stuck. May be null (e.g. in Phase 3a / testing without HttpState).
    private final Runnable completionCallback;

    // Self-guard for invokeCompletionCallback: ensures the callback runs exactly once
    // across ALL terminal paths (finish, writeTrailer error, channelInactive, exceptionCaught).
    // Separate from the 'finished' flag because channelInactive can fire on an abandoned
    // stream that never reached finish() or writeTrailer().
    private final AtomicBoolean callbackInvoked = new AtomicBoolean(false);

    // Inbound breakpoint fields — null when inbound breakpoints are disabled
    private final Configuration configuration;
    private final String inboundStreamId;
    // Pre-computed inbound breakpoint WS dispatch fields (CPX-01)
    private final boolean inboundUseWsDispatch;
    private final String inboundBreakpointClientId;
    private final String inboundBreakpointId;
    private final WebSocketClientRegistry webSocketClientRegistry;

    /**
     * Phase 3a constructor: function-based responder (backward compatible).
     *
     * @param methodDescriptor the resolved gRPC method descriptor
     * @param converter        JSON/protobuf converter for the method's message types
     * @param responder        maps an inbound message JSON string to a list of response
     *                         JSON strings; for 3a the default is echo: returns {@code [inboundJson]}
     */
    public GrpcBidiStreamHandler(
        Descriptors.MethodDescriptor methodDescriptor,
        GrpcJsonMessageConverter converter,
        Function<String, List<String>> responder
    ) {
        this(methodDescriptor, converter, responder, null, new IncrementalGrpcFrameDecoder(), null, null, null, null, null, null);
    }

    /**
     * Phase 3b constructor: GrpcBidiResponse-driven (without completion callback).
     *
     * @param methodDescriptor the resolved gRPC method descriptor
     * @param converter        JSON/protobuf converter for the method's message types
     * @param config           the GrpcBidiResponse configuration from the matched expectation
     */
    public GrpcBidiStreamHandler(
        Descriptors.MethodDescriptor methodDescriptor,
        GrpcJsonMessageConverter converter,
        GrpcBidiResponse config
    ) {
        this(methodDescriptor, converter, null, config, new IncrementalGrpcFrameDecoder(), null, null, null, null, null, null);
    }

    /**
     * Phase 3b constructor: GrpcBidiResponse-driven with completion callback.
     * The completion callback is invoked exactly once when the stream finishes (or errors),
     * clearing {@code responseInProgress} on the matched expectation.
     *
     * @param methodDescriptor   the resolved gRPC method descriptor
     * @param converter          JSON/protobuf converter for the method's message types
     * @param config             the GrpcBidiResponse configuration from the matched expectation
     * @param completionCallback invoked once on stream finish to clear responseInProgress
     */
    public GrpcBidiStreamHandler(
        Descriptors.MethodDescriptor methodDescriptor,
        GrpcJsonMessageConverter converter,
        GrpcBidiResponse config,
        Runnable completionCallback
    ) {
        this(methodDescriptor, converter, null, config, new IncrementalGrpcFrameDecoder(), completionCallback, null, null, null, null, null);
    }

    /**
     * Phase 3b constructor: GrpcBidiResponse-driven with completion callback and inbound breakpoints.
     *
     * @param methodDescriptor        the resolved gRPC method descriptor
     * @param converter               JSON/protobuf converter for the method's message types
     * @param config                  the GrpcBidiResponse configuration from the matched expectation
     * @param completionCallback      invoked once on stream finish to clear responseInProgress
     * @param configuration           the active server configuration (null to disable inbound breakpoints)
     * @param inboundStreamId         the stream ID for inbound breakpoints (null to disable)
     */
    public GrpcBidiStreamHandler(
        Descriptors.MethodDescriptor methodDescriptor,
        GrpcJsonMessageConverter converter,
        GrpcBidiResponse config,
        Runnable completionCallback,
        Configuration configuration,
        String inboundStreamId
    ) {
        this(methodDescriptor, converter, null, config, new IncrementalGrpcFrameDecoder(configuration), completionCallback, configuration, inboundStreamId, null, null, null);
    }

    /**
     * Phase 3b constructor: GrpcBidiResponse-driven with completion callback, inbound breakpoints,
     * per-server WebSocket registry, and pre-resolved breakpoint identity from the outer caller.
     *
     * @param methodDescriptor           the resolved gRPC method descriptor
     * @param converter                  JSON/protobuf converter for the method's message types
     * @param config                     the GrpcBidiResponse configuration from the matched expectation
     * @param completionCallback         invoked once on stream finish to clear responseInProgress
     * @param configuration              the active server configuration (null to disable inbound breakpoints)
     * @param inboundStreamId            the stream ID for inbound breakpoints (null to disable)
     * @param webSocketClientRegistry    the per-server WS registry for callback dispatch (null to disable)
     * @param inboundBreakpointClientId  the matched inbound breakpoint's owning clientId (from outer caller)
     * @param inboundBreakpointId        the matched inbound breakpoint's id (from outer caller)
     */
    public GrpcBidiStreamHandler(
        Descriptors.MethodDescriptor methodDescriptor,
        GrpcJsonMessageConverter converter,
        GrpcBidiResponse config,
        Runnable completionCallback,
        Configuration configuration,
        String inboundStreamId,
        WebSocketClientRegistry webSocketClientRegistry,
        String inboundBreakpointClientId,
        String inboundBreakpointId
    ) {
        this(methodDescriptor, converter, null, config, new IncrementalGrpcFrameDecoder(configuration), completionCallback,
            configuration, inboundStreamId, webSocketClientRegistry, inboundBreakpointClientId, inboundBreakpointId);
    }

    /**
     * Visible-for-testing constructor that accepts a custom decoder (e.g. with a small cap).
     */
    GrpcBidiStreamHandler(
        Descriptors.MethodDescriptor methodDescriptor,
        GrpcJsonMessageConverter converter,
        Function<String, List<String>> responder,
        GrpcBidiResponse config,
        IncrementalGrpcFrameDecoder decoder,
        Runnable completionCallback,
        Configuration configuration,
        String inboundStreamId,
        WebSocketClientRegistry webSocketClientRegistry,
        String inboundBreakpointClientId,
        String inboundBreakpointId
    ) {
        this.methodDescriptor = methodDescriptor;
        this.converter = converter;
        this.responder = responder;
        this.config = config;
        this.decoder = decoder;
        this.completionCallback = completionCallback;
        this.configuration = configuration;
        this.templateRenderer = new GrpcStreamMessageTemplateRenderer(null, configuration);
        this.inboundStreamId = inboundStreamId;
        this.webSocketClientRegistry = webSocketClientRegistry;
        this.finished.set(false);
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
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.channel().config().setAutoRead(false);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            if (msg instanceof Http2HeadersFrame && requestHeadersRead) {
                handleTrailers(ctx, (Http2HeadersFrame) msg);
            } else if (msg instanceof Http2HeadersFrame) {
                requestHeadersRead = true;
                handleHeaders(ctx, (Http2HeadersFrame) msg);
            } else if (msg instanceof Http2DataFrame) {
                handleData(ctx, (Http2DataFrame) msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        } catch (GrpcException e) {
            if (e.getMessage() != null && e.getMessage().contains("exceeded maximum")) {
                writeTrailer(ctx, GrpcStatusMapper.GrpcStatusCode.RESOURCE_EXHAUSTED, e.getMessage());
            } else {
                writeTrailer(ctx, GrpcStatusMapper.GrpcStatusCode.INTERNAL, e.getMessage());
            }
        } catch (Exception e) {
            writeTrailer(ctx, GrpcStatusMapper.GrpcStatusCode.INTERNAL,
                e.getMessage() != null ? e.getMessage() : "internal error");
        }
    }

    private void handleHeaders(ChannelHandlerContext ctx, Http2HeadersFrame headersFrame) {
        // Honour the client's grpc-timeout for the whole bidi RPC. writeTrailer's CAS makes the
        // deadline and any normal termination mutually exclusive, and writeGrpcMessage refuses to
        // emit once finished, so no message can follow the terminal trailer.
        CharSequence grpcTimeout = headersFrame.headers().get(GrpcTimeout.GRPC_TIMEOUT_HEADER);
        if (grpcTimeout != null) {
            deadline.schedule(
                ctx,
                org.mockserver.model.HttpRequest.request()
                    .withHeader(GrpcTimeout.GRPC_TIMEOUT_HEADER, grpcTimeout.toString()),
                () -> writeTrailer(ctx, GrpcStatusMapper.GrpcStatusCode.DEADLINE_EXCEEDED,
                    deadline.deadlineExceededMessage())
            );
        }
        // Apply the top-level action delay (if configured) before writing initial HEADERS.
        // This mirrors how HttpActionHandler applies action.getDelay() via the Scheduler.
        Delay actionDelay = (config != null) ? config.getDelay() : null;
        long actionDelayMillis = (actionDelay != null) ? actionDelay.sampleValueMillis() : 0;

        Runnable writeInitialResponse = () -> {
            // The action delay can outlast the client's grpc-timeout, in which case the deadline
            // already terminated the stream -- writing :status HEADERS (and then DATA) onto an
            // ended stream is a protocol violation.
            if (finished.get()) {
                return;
            }
            initialHeadersWritten.set(true);
            // Write initial response headers
            DefaultHttp2Headers responseHeaders = new DefaultHttp2Headers();
            responseHeaders.status("200");
            responseHeaders.set("content-type", GrpcStatusMapper.GRPC_CONTENT_TYPE);

            // Add configured headers from GrpcBidiResponse
            if (config != null && config.getHeaders() != null) {
                Headers configHeaders = config.getHeaders();
                for (Header entry : configHeaders.getEntries()) {
                    for (NottableString value : entry.getValues()) {
                        responseHeaders.add(entry.getName().getValue().toLowerCase(), value.getValue());
                    }
                }
            }

            ctx.writeAndFlush(new DefaultHttp2HeadersFrame(responseHeaders, false));

            // Send eager messages if configured (Phase 3b), honouring per-message delay.
            // Eager messages have no matched inbound message, so templating (if enabled on an
            // eager message) renders against an empty request body.
            if (config != null && config.getMessages() != null && !config.getMessages().isEmpty()) {
                scheduleMessages(config.getMessages(), 0, ctx, null, () -> {
                    if (headersFrame.isEndStream()) {
                        finish(ctx);
                    } else {
                        ctx.read();
                    }
                });
            } else {
                if (headersFrame.isEndStream()) {
                    finish(ctx);
                } else {
                    ctx.read();
                }
            }
        };

        if (actionDelayMillis > 0) {
            ctx.executor().schedule(writeInitialResponse, actionDelayMillis, TimeUnit.MILLISECONDS);
        } else {
            writeInitialResponse.run();
        }
    }

    /**
     * A HEADERS frame after the request's headers is its trailers, which carry END_STREAM: they end the request as
     * END_STREAM on a DATA frame does. A frame is read only once the last one's responses are written, so none is
     * still pending.
     */
    private void handleTrailers(ChannelHandlerContext ctx, Http2HeadersFrame trailersFrame) {
        if (trailersFrame.isEndStream()) {
            finish(ctx);
        } else {
            ctx.read();
        }
    }

    private void handleData(ChannelHandlerContext ctx, Http2DataFrame dataFrame) {
        try {
            byte[] bytes = new byte[dataFrame.content().readableBytes()];
            dataFrame.content().readBytes(bytes);
            boolean endStream = dataFrame.isEndStream();

            // --- Inbound breakpoint interception ---
            // The Http2DataFrame is released in the finally block BEFORE any breakpoint
            // parking delay, which immediately refunds the HTTP/2 flow-control window
            // (both stream-level and connection-level). This is the key safety property:
            // holding a breakpointed frame does NOT consume the connection flow-control
            // window, so other streams on the same connection are not stalled.
            //
            // Backpressure while parked is provided by the existing pull-based model:
            // we simply do not call ctx.read() until the breakpoint decision resolves.
            // Because autoRead is already false (set in handlerAdded), Netty will not
            // deliver the next DATA frame until we explicitly request it.
            if (inboundStreamId != null) {

                // WS-callback dispatch (clientId is always present — required since 7b)
                final java.util.concurrent.CompletableFuture<StreamFrameDecision> decisionFuture;
                int seq = StreamFrameBreakpointRegistry.getInstance().nextSequenceNumber(inboundStreamId);
                java.util.concurrent.CompletableFuture<StreamFrameDecision> wsFuture =
                    org.mockserver.mock.breakpoint.StreamFrameCallbackDispatcher.getInstance().dispatchFrame(
                        inboundBreakpointClientId, inboundBreakpointId, inboundStreamId, seq, PausedStreamFrame.Direction.INBOUND,
                        org.mockserver.mock.breakpoint.BreakpointPhase.INBOUND_STREAM,
                        bytes, "GRPC-INBOUND", methodDescriptor.getFullName(), null, webSocketClientRegistry, configuration, null);
                if (wsFuture != null) {
                    decisionFuture = wsFuture;
                } else {
                    processDataBytes(ctx, bytes, endStream);
                    return;
                }

                // Park: chain the decision onto the channel's event loop (NEVER block)
                final byte[] capturedBytes = bytes;
                decisionFuture.thenAccept(decision ->
                    ctx.channel().eventLoop().execute(() -> {
                        try {
                            if (!ctx.channel().isActive()) {
                                return;
                            }

                            switch (decision.getAction()) {
                                case CONTINUE ->
                                    processDataBytes(ctx, capturedBytes, endStream);
                                case MODIFY ->
                                    processDataBytes(ctx, decision.getReplacementBody(), endStream);
                                case DROP -> {
                                    // Discard: just request the next frame (or finish)
                                    if (endStream) {
                                        finish(ctx);
                                    } else {
                                        ctx.read();
                                    }
                                }
                                case INJECT -> {
                                    // Process original, then also process the injected bytes
                                    processDataBytes(ctx, capturedBytes, false);
                                    processDataBytes(ctx, decision.getInjectedBody(), endStream);
                                }
                                case CLOSE -> {
                                    StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
                                    writeTrailer(ctx, GrpcStatusMapper.GrpcStatusCode.CANCELLED, "stream closed by breakpoint");
                                }
                            }
                        } catch (Exception e) {
                            LOG.warn("error processing inbound breakpoint decision for gRPC stream {}",
                                inboundStreamId, e);
                            writeTrailer(ctx, GrpcStatusMapper.GrpcStatusCode.INTERNAL,
                                e.getMessage() != null ? e.getMessage() : "breakpoint resume error");
                        }
                    })
                ).exceptionally(ex -> {
                    LOG.debug("inbound breakpoint decision callback failed for gRPC stream {}: {}",
                        inboundStreamId, ex.getMessage());
                    // Resume reading on failure so the stream is not stuck
                    ctx.channel().eventLoop().execute(() -> ctx.read());
                    return null;
                });
                return;
            }

            // --- Default path (no inbound breakpoints) ---
            processDataBytes(ctx, bytes, endStream);

        } finally {
            dataFrame.release();
        }
    }

    /**
     * Process a DATA frame's bytes through the gRPC frame decoder and rule engine.
     * Extracted so it can be called from both the default path and the breakpoint resume path.
     */
    private void processDataBytes(ChannelHandlerContext ctx, byte[] bytes, boolean endStream) {
        List<byte[]> completedMessages = decoder.feed(bytes);

        // The continuation to run after all inbound messages from this DATA frame
        // have had their responses (possibly delayed) written.
        Runnable afterAllMessages = () -> {
            if (endStream) {
                finish(ctx);
            } else {
                ctx.read();
            }
        };

        if (completedMessages.isEmpty()) {
            afterAllMessages.run();
            return;
        }

        // Process inbound messages sequentially, chaining the continuation through
        // delayed rule responses so that finish/read happens only after all scheduled
        // messages are written (preserving interleaving and finish ordering).
        processInboundMessages(ctx, completedMessages, 0, afterAllMessages);
    }

    /**
     * Recursively processes decoded inbound messages, chaining rule-response scheduling
     * so that the {@code afterAll} continuation runs only after the last message's responses
     * (including any per-message delays) have been written.
     */
    private void processInboundMessages(ChannelHandlerContext ctx, List<byte[]> messages, int index, Runnable afterAll) {
        if (index >= messages.size()) {
            afterAll.run();
            return;
        }

        String inboundJson = converter.toJson(messages.get(index), methodDescriptor.getInputType());
        Runnable processNext = () -> processInboundMessages(ctx, messages, index + 1, afterAll);

        if (config != null) {
            // Phase 3b: rule-driven matching with per-message delay
            processWithRules(ctx, inboundJson, processNext);
        } else if (responder != null) {
            // Phase 3a: function-based responder (no delay support)
            List<String> responseJsons = responder.apply(inboundJson);
            if (responseJsons != null) {
                for (String responseJson : responseJsons) {
                    writeGrpcMessage(ctx, responseJson);
                }
            }
            processNext.run();
        } else {
            processNext.run();
        }
    }

    private void processWithRules(ChannelHandlerContext ctx, String inboundJson, Runnable afterResponses) {
        if (config.getRules() == null) {
            afterResponses.run();
            return;
        }
        for (GrpcBidiRule rule : config.getRules()) {
            if (matchesRule(rule, inboundJson)) {
                if (rule.getResponses() != null && !rule.getResponses().isEmpty()) {
                    scheduleMessages(rule.getResponses(), 0, ctx, inboundJson, afterResponses);
                } else {
                    afterResponses.run();
                }
                return; // first match wins
            }
        }
        // no match -> no response (documented behaviour)
        afterResponses.run();
    }

    /**
     * Matches the inbound message JSON against the rule's matchJson pattern.
     * Delegates to {@link GrpcBidiRuleMatcher#matches(GrpcBidiRule, String)}: exact string
     * match first, then a DOTALL regex match (so {@code '.'} matches the newlines that
     * protobuf's {@code JsonFormat.printer()} emits), with {@code NottableString} negation.
     * Retained as a package-private method so existing unit tests keep exercising it.
     */
    boolean matchesRule(GrpcBidiRule rule, String inboundJson) {
        // Delegate to the transport-neutral matcher so HTTP/2 and HTTP/3 bidi share
        // identical rule-matching semantics (exact, then DOTALL regex, with negation).
        return GrpcBidiRuleMatcher.matches(rule, inboundJson);
    }

    /**
     * Schedule a list of {@link GrpcStreamMessage} for writing, honouring per-message
     * {@link Delay} via the channel's event-loop executor. Messages are chained so each
     * writes only after the previous completes (preserving order even with varying delays).
     * The {@code afterAll} continuation runs after the last message is written.
     * <p>
     * Mirrors the recursive scheduling pattern of
     * {@link org.mockserver.mock.action.http.GrpcStreamResponseActionHandler#scheduleMessages}.
     * <p>
     * {@code inboundJson} is the matched inbound message (null for eager messages); it is passed to
     * the template renderer so a templated response can echo/derive fields from the request.
     */
    private void scheduleMessages(List<GrpcStreamMessage> messages, int index, ChannelHandlerContext ctx, String inboundJson, Runnable afterAll) {
        if (index >= messages.size()) {
            afterAll.run();
            return;
        }
        GrpcStreamMessage message = messages.get(index);
        Delay delay = message.getDelay();
        long delayMillis = (delay != null) ? delay.sampleValueMillis() : 0;

        Runnable writeAndContinue = () -> {
            writeGrpcMessage(ctx, message, inboundJson);
            scheduleMessages(messages, index + 1, ctx, inboundJson, afterAll);
        };

        if (delayMillis > 0) {
            // Netty does NOT route exceptions thrown by a scheduled task to exceptionCaught,
            // so a render/toProtobuf failure here would otherwise escape silently and leave the
            // stream hanging with no grpc-status trailer. Wrap the scheduled body so any failure
            // terminates the stream cleanly with an INTERNAL trailer (which also runs the
            // completion-callback bookkeeping via writeTrailer).
            ctx.executor().schedule(() -> {
                try {
                    writeAndContinue.run();
                } catch (Exception e) {
                    writeTrailer(ctx, GrpcStatusMapper.GrpcStatusCode.INTERNAL,
                        e.getMessage() != null ? e.getMessage() : "internal error");
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } else {
            writeAndContinue.run();
        }
    }

    private void writeGrpcMessage(ChannelHandlerContext ctx, GrpcStreamMessage message, String inboundJson) {
        writeGrpcMessage(ctx, templateRenderer.render(message, inboundJson));
    }

    private void writeGrpcMessage(ChannelHandlerContext ctx, String json) {
        if (finished.get()) {
            // the stream already carries a terminal trailer (normal finish or an elapsed deadline);
            // a per-message delay or breakpoint resume can still land here afterwards
            return;
        }
        byte[] responseProto = converter.toProtobuf(json, methodDescriptor.getOutputType());
        byte[] framedResponse = GrpcFrameCodec.encode(responseProto);
        ctx.writeAndFlush(new DefaultHttp2DataFrame(
            Unpooled.wrappedBuffer(framedResponse), false));
    }

    private void finish(ChannelHandlerContext ctx) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        deadline.cancel();

        // Determine grpc-status from config
        String statusCode = "0";
        String statusMessage = null;
        if (config != null) {
            if (config.getStatusName() != null) {
                GrpcStatusMapper.GrpcStatusCode code = GrpcStatusMapper.fromName(config.getStatusName());
                statusCode = String.valueOf(code.getCode());
            }
            statusMessage = config.getStatusMessage();
        }

        DefaultHttp2Headers trailers = new DefaultHttp2Headers();
        trailers.set(GrpcStatusMapper.GRPC_STATUS_HEADER, statusCode);
        if (statusMessage != null) {
            trailers.set(GrpcStatusMapper.GRPC_MESSAGE_HEADER,
                GrpcStatusMapper.percentEncodeMessage(statusMessage));
        }
        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(trailers, true));

        // Clear responseInProgress on the matched expectation so a times-limited
        // expectation is not left stuck after the bidi stream completes.
        invokeCompletionCallback();

        // Close connection if configured
        if (config != null && Boolean.TRUE.equals(config.getCloseConnection())) {
            ctx.close();
        }
    }

    private void writeTrailer(ChannelHandlerContext ctx, GrpcStatusMapper.GrpcStatusCode statusCode, String message) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        deadline.cancel();
        DefaultHttp2Headers trailers = new DefaultHttp2Headers();
        if (!initialHeadersWritten.get()) {
            // Nothing has been written yet, so this is the FIRST frame on the stream and must be a
            // valid Trailers-Only response: :status and content-type alongside grpc-status. Emitting
            // a bare trailing HEADERS frame here produced a response with no :status at all.
            trailers.status("200");
            trailers.set("content-type", GrpcStatusMapper.GRPC_CONTENT_TYPE);
        }
        trailers.set(GrpcStatusMapper.GRPC_STATUS_HEADER, String.valueOf(statusCode.getCode()));
        if (message != null) {
            trailers.set(GrpcStatusMapper.GRPC_MESSAGE_HEADER,
                GrpcStatusMapper.percentEncodeMessage(message));
        }
        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(trailers, true));

        // Clear responseInProgress on error path too
        invokeCompletionCallback();
    }

    /**
     * Invokes the completion callback exactly once, guarded by an {@link AtomicBoolean} CAS.
     * This is safe to call from any terminal path (finish, writeTrailer, channelInactive,
     * exceptionCaught) -- the callback will execute on the first invocation and be a no-op
     * on all subsequent calls, regardless of which path fires first.
     * <p>
     * Clears {@code responseInProgress} on the matched expectation so it can be removed
     * when its Times are exhausted.
     */
    private void invokeCompletionCallback() {
        if (completionCallback != null && callbackInvoked.compareAndSet(false, true)) {
            try {
                completionCallback.run();
            } catch (Exception ignored) {
                // Best-effort: don't let post-processing failure break the stream teardown
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        writeTrailer(ctx, GrpcStatusMapper.GrpcStatusCode.INTERNAL,
            cause.getMessage() != null ? cause.getMessage() : "internal error");
    }

    /**
     * Handles channel deactivation (client disconnect, connection closed, abandoned stream).
     * If the stream has not already finished via {@link #finish} or {@link #writeTrailer},
     * the completion callback is invoked to clear {@code responseInProgress} on the matched
     * expectation. This prevents a times-limited expectation from being stuck forever when
     * a bidi stream is abandoned without a clean END_STREAM.
     * <p>
     * The callback is self-guarded by {@link #callbackInvoked} (AtomicBoolean CAS), so it
     * runs exactly once across all terminal paths: normal finish, error trailer, channel
     * inactive, and exception caught.
     * <p>
     * Also evicts any held inbound breakpoint frames to prevent resource leaks and hanging futures.
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Evict any held inbound frames on channel close to prevent leaks
        if (inboundStreamId != null) {
            StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
        }
        // a deadline timer must not outlive the stream that scheduled it
        deadline.cancel();
        invokeCompletionCallback();
        super.channelInactive(ctx);
    }
}
