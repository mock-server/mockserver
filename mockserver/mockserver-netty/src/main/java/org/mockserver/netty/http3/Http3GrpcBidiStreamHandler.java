package org.mockserver.netty.http3;

import com.google.protobuf.Descriptors;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.quic.QuicStreamChannel;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.grpc.GrpcBidiRuleMatcher;
import org.mockserver.grpc.GrpcException;
import org.mockserver.grpc.GrpcFrameCodec;
import org.mockserver.grpc.GrpcJsonMessageConverter;
import org.mockserver.grpc.GrpcStatusMapper;
import org.mockserver.grpc.GrpcStreamDeadline;
import org.mockserver.grpc.GrpcStreamMessageTemplateRenderer;
import org.mockserver.grpc.IncrementalGrpcFrameDecoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.breakpoint.BreakpointPhase;
import org.mockserver.mock.breakpoint.PausedStreamFrame;
import org.mockserver.mock.breakpoint.StreamFrameBreakpointRegistry;
import org.mockserver.mock.breakpoint.StreamFrameCallbackDispatcher;
import org.mockserver.mock.breakpoint.StreamFrameDecision;
import org.mockserver.model.Delay;
import org.mockserver.model.GrpcBidiResponse;
import org.mockserver.model.GrpcBidiRule;
import org.mockserver.model.GrpcStreamMessage;
import org.mockserver.model.Header;
import org.mockserver.model.NottableString;
import org.slf4j.event.Level;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;

/**
 * Drives true bidirectional gRPC streaming over a single QUIC bidirectional stream
 * (HTTP/3). It is the HTTP/3 analogue of
 * {@link org.mockserver.netty.grpc.GrpcBidiStreamHandler} but, instead of being a
 * Netty pipeline handler, it is a plain helper driven by {@link Http3MockServerHandler}
 * (which already extends Netty's {@code Http3RequestStreamInboundHandler} and receives
 * frames incrementally via {@code channelRead} / {@code channelInputClosed}).
 * <p>
 * A QUIC stream is full-duplex, so the server can write response frames while the client
 * is still sending request frames. The lifecycle is:
 * <ul>
 *   <li>{@link #start()} -- write the initial response HEADERS ({@code :status=200},
 *       {@code content-type=application/grpc}, plus any configured headers) and any EAGER
 *       messages from the {@link GrpcBidiResponse} (honouring per-message delays);</li>
 *   <li>{@link #onData(byte[])} -- feed inbound bytes to the incremental gRPC frame decoder;
 *       for each complete inbound message, convert protobuf to JSON, evaluate rules in order,
 *       and emit the first matching rule's responses as DATA frames;</li>
 *   <li>{@link #onInputClosed()} -- the client half-closed (FIN); once all scheduled
 *       response writes have drained, write the trailing HEADERS carrying {@code grpc-status}
 *       and shut the QUIC stream output;</li>
 *   <li>{@link #onChannelInactive()} -- the stream/connection was torn down; clears
 *       {@code responseInProgress} on the matched expectation via the completion callback.</li>
 * </ul>
 * <p>
 * All methods run on the QUIC stream's single event-loop thread, so the {@code activeChains}
 * counter that orders the trailing HEADERS after all (possibly delayed) response writes needs
 * no synchronization. The completion callback is guarded by an {@link AtomicBoolean} so it runs
 * exactly once across every terminal path.
 * <p>
 * <strong>Inbound (client-to-server) breakpoints.</strong> When an {@code INBOUND_STREAM}
 * breakpoint matcher matches this bidi stream at stream onset, the driver passes a non-null
 * {@code inboundStreamId} (plus the matched breakpoint's clientId/id and the WS registry). Each
 * inbound gRPC DATA frame is then parked in the {@link StreamFrameBreakpointRegistry} /
 * {@link StreamFrameCallbackDispatcher} before being decoded, and resumed (CONTINUE / MODIFY /
 * DROP / INJECT / CLOSE) by a dashboard or callback client. This is the HTTP/3 analogue of the
 * inbound interception in {@link org.mockserver.netty.grpc.GrpcBidiStreamHandler}.
 * <p>
 * <strong>Ordering &amp; flow control.</strong> The QUIC driver
 * ({@link Http3MockServerHandler}) copies each DATA frame's bytes to a {@code byte[]}
 * before calling {@link #onData(byte[])}, then
 * releases the {@code Http3DataFrame} in {@code channelRead}'s {@code finally} block immediately
 * after {@code onData} returns. Since {@code onData} dispatches asynchronously, no
 * {@code ByteBuf} is retained across a hold and the QUIC stream/connection flow-control window is
 * never pinned by a parked frame. To preserve per-frame ordering while a frame is parked, at most
 * one inbound frame is dispatched at a time; any frames the client sends while a frame is held are
 * buffered in {@link #heldInboundFrames} and drained in order when the held frame resolves. That
 * buffer is bounded by the driver's existing {@code maxRequestBodySize} enforcement (the driver
 * caps total accumulated inbound bytes before calling {@code onData}). When inbound breakpoints
 * are inactive the frame takes a byte-for-byte-identical default path with zero added work.
 */
public class Http3GrpcBidiStreamHandler {

    private final ChannelHandlerContext ctx;
    private final Descriptors.MethodDescriptor methodDescriptor;
    private final GrpcJsonMessageConverter converter;
    private final GrpcBidiResponse config;
    private final Runnable completionCallback;
    private final MockServerLogger mockServerLogger;
    private final IncrementalGrpcFrameDecoder decoder;
    // Renders bidi response messages, applying optional per-message response templating against
    // the matched inbound message. Static (no templateType) messages pass through unchanged.
    private final GrpcStreamMessageTemplateRenderer templateRenderer;

    /**
     * Terminal guard. An {@link java.util.concurrent.atomic.AtomicBoolean} rather than a plain
     * boolean: normal finish, the error trailer and an elapsed deadline are all terminal paths, and
     * the previous check-then-set could let two of them each write a terminal HEADERS frame.
     */
    private final java.util.concurrent.atomic.AtomicBoolean finishedGuard = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Enforces the client's grpc-timeout for this bidi RPC, terminating it mid-stream. The QUIC
     * stream is its own channel, so the timer is naturally per-stream.
     */
    private final GrpcStreamDeadline deadline = new GrpcStreamDeadline();
    private boolean inputClosed;
    // Number of in-progress message-emission chains (startup eager chain + one per inbound
    // message's rule responses). The trailing HEADERS frame is written only once the client
    // has half-closed AND no chain is still draining, so response frames always precede it.
    private int activeChains;
    private final AtomicBoolean callbackInvoked = new AtomicBoolean(false);

    // --- Inbound breakpoint state (all null/false when inbound breakpoints are disabled) ---
    private final Configuration configuration;
    private final String inboundStreamId;
    private final String inboundBreakpointClientId;
    private final String inboundBreakpointId;
    private final WebSocketClientRegistry webSocketClientRegistry;
    private final boolean inboundBreakpointsActive;
    // Inbound frames received while an earlier frame is parked at a breakpoint; drained in
    // order when the held frame resolves so per-frame ordering is preserved. Bounded by the
    // driver's maxRequestBodySize enforcement.
    private final ArrayDeque<byte[]> heldInboundFrames = new ArrayDeque<>();
    // True while exactly one inbound frame is parked awaiting a breakpoint decision.
    private boolean inboundFrameInFlight;

    /**
     * Constructs a bidi handler without inbound breakpoint support (default path).
     */
    public Http3GrpcBidiStreamHandler(
        ChannelHandlerContext ctx,
        Descriptors.MethodDescriptor methodDescriptor,
        GrpcJsonMessageConverter converter,
        GrpcBidiResponse config,
        Runnable completionCallback,
        MockServerLogger mockServerLogger
    ) {
        this(ctx, methodDescriptor, converter, config, completionCallback, mockServerLogger,
            null, null, null, null, null);
    }

    /**
     * Constructs a bidi handler with optional inbound (client-to-server) breakpoint support.
     * Inbound breakpoints are active only when {@code inboundStreamId},
     * {@code inboundBreakpointClientId} and {@code webSocketClientRegistry} are all non-null
     * (i.e. an {@code INBOUND_STREAM} breakpoint matcher matched this stream at onset).
     *
     * @param configuration             active server configuration (timeout / max-held rails); nullable
     * @param inboundStreamId           unique stream id for inbound frame parking; null disables inbound breakpoints
     * @param inboundBreakpointClientId the matched inbound breakpoint's owning callback clientId; nullable
     * @param inboundBreakpointId       the matched inbound breakpoint's id; nullable
     * @param webSocketClientRegistry   per-server WS registry for callback dispatch; null disables inbound breakpoints
     */
    public Http3GrpcBidiStreamHandler(
        ChannelHandlerContext ctx,
        Descriptors.MethodDescriptor methodDescriptor,
        GrpcJsonMessageConverter converter,
        GrpcBidiResponse config,
        Runnable completionCallback,
        MockServerLogger mockServerLogger,
        Configuration configuration,
        String inboundStreamId,
        String inboundBreakpointClientId,
        String inboundBreakpointId,
        WebSocketClientRegistry webSocketClientRegistry
    ) {
        this.ctx = ctx;
        this.methodDescriptor = methodDescriptor;
        this.converter = converter;
        this.config = config;
        this.completionCallback = completionCallback;
        this.mockServerLogger = mockServerLogger;
        this.configuration = configuration;
        // live configuration reaches the streaming decoder too, so maxGrpcMessageSize set on an
        // instance / via the DTO / PUT /mockserver/config applies on the bidi path as well
        this.decoder = new IncrementalGrpcFrameDecoder(configuration);
        this.templateRenderer = new GrpcStreamMessageTemplateRenderer(mockServerLogger, configuration);
        this.inboundStreamId = inboundStreamId;
        this.inboundBreakpointClientId = inboundBreakpointClientId;
        this.inboundBreakpointId = inboundBreakpointId;
        this.webSocketClientRegistry = webSocketClientRegistry;
        this.inboundBreakpointsActive =
            inboundStreamId != null && inboundBreakpointClientId != null && webSocketClientRegistry != null;
    }

    /**
     * Write the initial response HEADERS and any eager messages. The top-level action delay
     * ({@link GrpcBidiResponse#getDelay()}), if configured, delays the eager message stream
     * (the HEADERS are sent promptly so inbound DATA frames never race ahead of them).
     */
    public void start(org.mockserver.model.HttpRequest request) {
        // Honour the client's grpc-timeout for the whole bidi RPC. writeErrorTrailer's CAS makes
        // the deadline and any normal termination mutually exclusive, and the finishedGuard checks
        // on the emission paths stop messages following the terminal trailer.
        deadline.schedule(ctx, request, () -> writeErrorTrailer(
            GrpcStatusMapper.GrpcStatusCode.DEADLINE_EXCEEDED, deadline.deadlineExceededMessage()));

        writeInitialHeaders();

        long actionDelayMillis = (config.getDelay() != null) ? config.getDelay().sampleValueMillis() : 0;
        activeChains++; // startup (eager) chain in progress
        // Eager messages have no matched inbound message, so templating (if enabled on an eager
        // message) renders against an empty request body.
        Runnable emitEager = () -> emitSequential(config.getMessages(), 0, null, () -> {
            activeChains--;
            maybeFinish();
        });
        if (actionDelayMillis > 0) {
            // A scheduled task's exception is NOT routed to exceptionCaught by Netty, so guard the
            // scheduled eager emit: on failure write an INTERNAL trailer and still run the chain
            // bookkeeping (activeChains--) so the stream terminates instead of hanging.
            ctx.executor().schedule(() -> {
                try {
                    emitEager.run();
                } catch (Exception e) {
                    writeErrorTrailer(GrpcStatusMapper.GrpcStatusCode.INTERNAL,
                        e.getMessage() != null ? e.getMessage() : "internal error");
                    activeChains--;
                    maybeFinish();
                }
            }, actionDelayMillis, TimeUnit.MILLISECONDS);
        } else {
            emitEager.run();
        }
    }

    /**
     * Feed inbound request bytes. When inbound breakpoints are inactive this decodes complete
     * gRPC frames and emits matching rule responses immediately (byte-for-byte default path).
     * When active, the frame is parked at a breakpoint first (see class javadoc); frames that
     * arrive while one is held are buffered in order.
     */
    public void onData(byte[] bytes) {
        if (finishedGuard.get()) {
            return;
        }
        if (!inboundBreakpointsActive) {
            processInboundBytes(bytes);
            return;
        }
        // Inbound breakpoints active: dispatch one frame at a time, buffering the rest in order.
        if (inboundFrameInFlight) {
            heldInboundFrames.add(bytes);
            return;
        }
        pumpInbound(bytes);
    }

    /**
     * Decode complete gRPC frames from {@code bytes}, convert each to JSON, and emit the first
     * matching rule's responses. Extracted so it can be invoked from both the default path and
     * the breakpoint-resume path.
     */
    private void processInboundBytes(byte[] bytes) {
        try {
            List<byte[]> completedMessages = decoder.feed(bytes);
            for (byte[] message : completedMessages) {
                String inboundJson = converter.toJson(message, methodDescriptor.getInputType());
                List<GrpcStreamMessage> responses = firstMatchingResponses(inboundJson);
                if (responses != null && !responses.isEmpty()) {
                    activeChains++;
                    emitSequential(responses, 0, inboundJson, () -> {
                        activeChains--;
                        maybeFinish();
                    });
                }
            }
        } catch (GrpcException e) {
            GrpcStatusMapper.GrpcStatusCode code = (e.getMessage() != null && e.getMessage().contains("exceeded maximum"))
                ? GrpcStatusMapper.GrpcStatusCode.RESOURCE_EXHAUSTED
                : GrpcStatusMapper.GrpcStatusCode.INTERNAL;
            writeErrorTrailer(code, e.getMessage());
        } catch (Exception e) {
            writeErrorTrailer(GrpcStatusMapper.GrpcStatusCode.INTERNAL,
                e.getMessage() != null ? e.getMessage() : "internal error");
        }
    }

    /**
     * Park {@code first} at an inbound breakpoint, then keep draining {@link #heldInboundFrames}
     * synchronously for as long as the dispatcher declines to park (cap reached / client not
     * connected). The first frame the client actually holds parks asynchronously; its decision
     * resumes via {@link #handleInboundDecision}. Looping (rather than recursing) on the decline
     * path keeps the call stack bounded even if the whole queue is processed inline.
     */
    private void pumpInbound(byte[] first) {
        byte[] current = first;
        while (current != null) {
            if (finishedGuard.get() || !ctx.channel().isActive()) {
                heldInboundFrames.clear();
                StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
                return;
            }
            int seq = StreamFrameBreakpointRegistry.getInstance().nextSequenceNumber(inboundStreamId);
            CompletableFuture<StreamFrameDecision> decisionFuture =
                StreamFrameCallbackDispatcher.getInstance().dispatchFrame(
                    inboundBreakpointClientId, inboundBreakpointId, inboundStreamId, seq,
                    PausedStreamFrame.Direction.INBOUND, BreakpointPhase.INBOUND_STREAM,
                    current, "GRPC-INBOUND", methodDescriptor.getFullName(),
                    null, webSocketClientRegistry, configuration, null);
            if (decisionFuture == null) {
                // Not parked (cap reached or client not connected): process inline, keep draining.
                processInboundBytes(current);
                if (finishedGuard.get()) {
                    heldInboundFrames.clear();
                    return;
                }
                current = heldInboundFrames.poll();
                continue;
            }
            // Parked: resolve asynchronously on the QUIC stream's event loop.
            inboundFrameInFlight = true;
            final byte[] parked = current;
            decisionFuture
                .thenAccept(decision -> ctx.executor().execute(() -> handleInboundDecision(decision, parked)))
                .exceptionally(ex -> {
                    ctx.executor().execute(() -> {
                        inboundFrameInFlight = false;
                        if (!finishedGuard.get() && ctx.channel().isActive()) {
                            processInboundBytes(parked);
                        }
                        resumeNextInboundOrFinish();
                    });
                    return null;
                });
            return;
        }
        // Whole queue drained inline with nothing parked.
        maybeFinish();
    }

    /**
     * Apply a resolved inbound breakpoint decision to the parked frame, then drain the next
     * buffered frame (if any). Runs on the QUIC stream's event-loop thread.
     */
    private void handleInboundDecision(StreamFrameDecision decision, byte[] originalBytes) {
        inboundFrameInFlight = false;
        if (finishedGuard.get() || !ctx.channel().isActive()) {
            heldInboundFrames.clear();
            StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
            return;
        }
        try {
            switch (decision.getAction()) {
                case CONTINUE -> processInboundBytes(originalBytes);
                case MODIFY -> processInboundBytes(decision.getReplacementBody());
                case DROP -> {
                    // Discard this frame entirely.
                }
                case INJECT -> {
                    processInboundBytes(originalBytes);
                    processInboundBytes(decision.getInjectedBody());
                }
                case CLOSE -> {
                    heldInboundFrames.clear();
                    StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
                    writeErrorTrailer(GrpcStatusMapper.GrpcStatusCode.CANCELLED, "stream closed by breakpoint");
                    return;
                }
            }
        } catch (Exception e) {
            writeErrorTrailer(GrpcStatusMapper.GrpcStatusCode.INTERNAL,
                e.getMessage() != null ? e.getMessage() : "breakpoint resume error");
            return;
        }
        resumeNextInboundOrFinish();
    }

    /**
     * Drain the next buffered inbound frame through the breakpoint pump, or — if the buffer is
     * empty — re-check whether the stream can now finish (the client may have half-closed while
     * frames were parked).
     */
    private void resumeNextInboundOrFinish() {
        if (finishedGuard.get()) {
            heldInboundFrames.clear();
            return;
        }
        byte[] next = heldInboundFrames.poll();
        if (next != null) {
            pumpInbound(next);
        } else {
            maybeFinish();
        }
    }

    /**
     * The client half-closed (END_STREAM). Finish once all scheduled responses have drained.
     */
    public void onInputClosed() {
        inputClosed = true;
        maybeFinish();
    }

    /**
     * The QUIC stream / connection was torn down. Evict any held inbound breakpoint frames
     * (releasing their futures and the per-stream registry entry) and clear responseInProgress
     * so a times-limited expectation is not left stuck when a bidi stream is abandoned without
     * a clean END_STREAM.
     */
    public void onChannelInactive() {
        // a deadline timer must not outlive the QUIC stream that scheduled it -- an abandoned
        // stream with a very long grpc-timeout would otherwise leave a ScheduledFutureTask in the
        // shared event-loop queue indefinitely
        deadline.cancel();
        if (inboundStreamId != null) {
            StreamFrameBreakpointRegistry.getInstance().evictStream(inboundStreamId);
        }
        heldInboundFrames.clear();
        invokeCompletionCallback();
    }

    private List<GrpcStreamMessage> firstMatchingResponses(String inboundJson) {
        if (config.getRules() == null) {
            return null;
        }
        for (GrpcBidiRule rule : config.getRules()) {
            if (GrpcBidiRuleMatcher.matches(rule, inboundJson)) {
                return rule.getResponses(); // first match wins (may be null/empty -> no response)
            }
        }
        return null; // no match -> no response (documented behaviour)
    }

    private void writeInitialHeaders() {
        DefaultHttp3HeadersFrame headersFrame = GrpcHttp3Adapter.buildInitialHeadersFrame();
        if (config.getHeaders() != null) {
            for (Header entry : config.getHeaders().getEntries()) {
                for (NottableString value : entry.getValues()) {
                    headersFrame.headers().add(entry.getName().getValue().toLowerCase(Locale.ROOT), value.getValue());
                }
            }
        }
        ctx.write(headersFrame);
        ctx.flush();
    }

    /**
     * Emit a list of messages sequentially, honouring per-message {@link Delay}, then run
     * {@code onDone}. Chaining preserves ordering even when delays differ.
     */
    private void emitSequential(List<GrpcStreamMessage> messages, int index, String inboundJson, Runnable onDone) {
        if (messages == null || index >= messages.size() || !ctx.channel().isActive()) {
            onDone.run();
            return;
        }
        GrpcStreamMessage message = messages.get(index);
        long delayMillis = (message.getDelay() != null) ? message.getDelay().sampleValueMillis() : 0;
        Runnable writeNext = () -> {
            writeResponseMessage(templateRenderer.render(message, inboundJson));
            emitSequential(messages, index + 1, inboundJson, onDone);
        };
        if (delayMillis > 0) {
            // Netty does NOT route exceptions thrown by a scheduled task to exceptionCaught,
            // so a render/toProtobuf failure here would otherwise escape silently and leave the
            // stream hanging with no grpc-status trailer (and with activeChains never decremented).
            // Wrap the scheduled body so any failure writes an INTERNAL trailer and still runs the
            // chain bookkeeping (onDone -> activeChains--), terminating the stream cleanly.
            ctx.executor().schedule(() -> {
                try {
                    writeNext.run();
                } catch (Exception e) {
                    writeErrorTrailer(GrpcStatusMapper.GrpcStatusCode.INTERNAL,
                        e.getMessage() != null ? e.getMessage() : "internal error");
                    onDone.run();
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } else {
            writeNext.run();
        }
    }

    private void writeResponseMessage(String json) {
        byte[] responseProto = converter.toProtobuf(json, methodDescriptor.getOutputType());
        byte[] framedResponse = GrpcFrameCodec.encode(responseProto);
        ctx.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(framedResponse)));
    }

    private void maybeFinish() {
        // Do not finish while an inbound frame is parked at a breakpoint or buffered behind one:
        // the trailing HEADERS must come after every (possibly held) inbound frame is resolved
        // and its responses drained.
        if (inputClosed && activeChains == 0 && !finishedGuard.get()
            && !inboundFrameInFlight && heldInboundFrames.isEmpty()) {
            finish();
        }
    }

    private void finish() {
        if (!finishedGuard.compareAndSet(false, true)) {
            return;
        }
        deadline.cancel();

        String statusCode = "0";
        String statusMessage = null;
        if (config.getStatusName() != null) {
            statusCode = String.valueOf(GrpcStatusMapper.fromName(config.getStatusName()).getCode());
        }
        if (config.getStatusMessage() != null && !config.getStatusMessage().isEmpty()) {
            statusMessage = config.getStatusMessage();
        }

        DefaultHttp3HeadersFrame trailers = GrpcHttp3Adapter.buildTrailingHeadersFrame(statusCode, statusMessage);
        ctx.writeAndFlush(trailers).addListener(future -> {
            if (Boolean.TRUE.equals(config.getCloseConnection())) {
                ctx.close();
            } else if (ctx.channel() instanceof QuicStreamChannel) {
                ((QuicStreamChannel) ctx.channel()).shutdownOutput();
            }
        });

        invokeCompletionCallback();
    }

    private void writeErrorTrailer(GrpcStatusMapper.GrpcStatusCode statusCode, String message) {
        if (!finishedGuard.compareAndSet(false, true)) {
            return;
        }
        deadline.cancel();
        if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setMessageFormat("gRPC bidi stream error over HTTP/3:{}")
                    .setArguments(message)
            );
        }
        DefaultHttp3HeadersFrame trailers = GrpcHttp3Adapter.buildTrailingHeadersFrame(
            String.valueOf(statusCode.getCode()), message
        );
        ctx.writeAndFlush(trailers).addListener(future -> {
            if (ctx.channel() instanceof QuicStreamChannel) {
                ((QuicStreamChannel) ctx.channel()).shutdownOutput();
            }
        });
        invokeCompletionCallback();
    }

    private void invokeCompletionCallback() {
        if (completionCallback != null && callbackInvoked.compareAndSet(false, true)) {
            try {
                completionCallback.run();
            } catch (Exception ignored) {
                // best-effort teardown
            }
        }
    }
}
