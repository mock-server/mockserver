package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.HttpClientHandler;
import org.mockserver.httpclient.StreamingResponseRelayHandler;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.NettyAllocator;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * An {@link HttpObjectAggregator} that can recognise streaming responses — specifically
 * Server-Sent Events ({@code Content-Type: text/event-stream}) — so that, when
 * MockServer is acting as a proxy, such responses can be relayed to the client
 * incrementally instead of being fully buffered in memory before being forwarded.
 * <p>
 * When streaming is detected and enabled, this handler removes itself from the pipeline
 * and installs a {@link StreamingResponseRelayHandler} that processes the unaggregated
 * {@link HttpObject}s. The {@link HttpClientHandler} is also removed to prevent double
 * completion of the response future.
 * <p>
 * When streaming is not detected (or not enabled), the handler delegates to
 * {@link HttpObjectAggregator} so the non-streaming path stays byte-identical.
 * <p>
 * An event-stream content type (see {@link #isStreamingResponse}) or a request that asked for a
 * stream triggers streaming detection. Ordinary chunked responses (e.g. Tomcat/servlet responses that use chunked
 * transfer-encoding without {@code Content-Length}) are aggregated normally.
 */
public class StreamingAwareHttpObjectAggregator extends CoalescingHttpObjectAggregator {

    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;
    private final boolean relayOnly;

    /**
     * Create an aggregator with streaming awareness for the {@link NettyHttpClient} forward path.
     * When a streaming response is detected, it installs a {@link StreamingResponseRelayHandler}
     * that completes the {@code RESPONSE_FUTURE} at head time.
     *
     * @param maxContentLength the maximum content length for non-streaming responses
     * @param configuration    the MockServer configuration (for streaming properties)
     * @param mockServerLogger the logger
     */
    public StreamingAwareHttpObjectAggregator(int maxContentLength, Configuration configuration, MockServerLogger mockServerLogger) {
        this(maxContentLength, configuration, mockServerLogger, false);
    }

    /**
     * Create an aggregator with streaming awareness.
     *
     * @param maxContentLength the maximum content length for non-streaming responses
     * @param configuration    the MockServer configuration (for streaming properties)
     * @param mockServerLogger the logger
     * @param relayOnly        if true, when a streaming response is detected the aggregator simply
     *                         removes itself from the pipeline so that unaggregated {@link HttpObject}s
     *                         flow through to the next handler. This mode is used on the loopback
     *                         pipeline where there is no {@code RESPONSE_FUTURE} to complete.
     */
    public StreamingAwareHttpObjectAggregator(int maxContentLength, Configuration configuration, MockServerLogger mockServerLogger, boolean relayOnly) {
        super(maxContentLength);
        HttpObjectAggregators.limitComponents(this);
        mergeNewComponentsOnly();
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
        this.relayOnly = relayOnly;
    }

    /**
     * Backwards-compatible constructor for use without streaming support (e.g. in relay
     * pipelines where Configuration is not yet threaded through). Aggregates every response,
     * as a plain {@link HttpObjectAggregator} does, merging only new components past the limit.
     *
     * @param maxContentLength the maximum content length
     */
    public StreamingAwareHttpObjectAggregator(int maxContentLength) {
        super(maxContentLength);
        HttpObjectAggregators.limitComponents(this);
        mergeNewComponentsOnly();
        this.configuration = null;
        this.mockServerLogger = null;
        this.relayOnly = false;
    }

    /**
     * A response should be relayed as a stream rather than aggregated when its content type is a
     * stream of events: Server-Sent Events ({@code text/event-stream}, used by Anthropic, OpenAI and
     * MCP streamable-HTTP), the AWS event stream ({@code application/vnd.amazon.eventstream}, used by
     * Bedrock's streaming APIs) or newline-delimited JSON ({@code application/x-ndjson}, used by Ollama).
     * <p>
     * Ordinary chunked responses (e.g. those produced by servlet containers like Tomcat, which strip
     * {@code Content-Length} and use chunked transfer-encoding for all responses) are aggregated normally.
     *
     * @param response the response head (status line and headers)
     * @return true when the response body should be streamed through incrementally
     */
    public static boolean isStreamingResponse(HttpResponse response) {
        String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null) {
            return false;
        }
        String lowerContentType = contentType.toLowerCase(Locale.US);
        return lowerContentType.contains("text/event-stream")
            || lowerContentType.contains("application/vnd.amazon.eventstream")
            || lowerContentType.contains("application/x-ndjson");
    }

    /**
     * Whether the request path names Gemini's streaming method, whose response is a JSON array sent
     * an element at a time with {@code Content-Type: application/json} unless {@code alt=sse} is set.
     */
    public static boolean isStreamingRequestPath(String path) {
        return path != null && path.toLowerCase(Locale.US).contains(":streamgeneratecontent");
    }

    /**
     * Channel attribute key set by {@code NettyHttpClient} when streaming must be disabled
     * for a particular request (e.g. FORWARD_REPLACE with a response override). When set
     * to {@code true}, this aggregator always delegates to the standard
     * {@link HttpObjectAggregator} path regardless of response content type.
     */
    private static final AttributeKey<Boolean> DISABLE_RESPONSE_STREAMING = AttributeKey.valueOf("DISABLE_RESPONSE_STREAMING");

    /**
     * Channel attribute set by {@code NettyHttpClient} (forward leg) or
     * {@code UpstreamProxyRelayHandler} (CONNECT-proxy loopback leg) when the OUTGOING request asked
     * for a streamed response (Accept: text/event-stream, or a JSON body with {@code "stream": true}).
     * When set, the response is relayed as a stream even if it omits
     * {@code Content-Type: text/event-stream} — covering streaming backends that do not send it,
     * such as the OpenAI Codex backend used by the opencode CLI.
     * <p>
     * Set per-request and consumed when the matching response head arrives, so a keep-alive CONNECT
     * tunnel carrying many requests applies the intent to the correct response only.
     */
    public static final AttributeKey<Boolean> EXPECT_STREAMING_RESPONSE = AttributeKey.valueOf("EXPECT_STREAMING_RESPONSE");

    /**
     * Diagnostic-only channel attribute recording {@code System.nanoTime()} at the moment the request
     * was forwarded to the upstream on the CONNECT-proxy loopback relay path (set by
     * {@code UpstreamProxyRelayHandler}). Used purely to compute a time-to-first-byte (TTFB) for the
     * DEBUG streaming-decision diagnostic when the matching response head arrives; absent on non-relay
     * paths, in which case the TTFB fields are simply omitted. Behaviour-preserving.
     */
    public static final AttributeKey<Long> REQUEST_FORWARDED_NANOS = AttributeKey.valueOf("REQUEST_FORWARDED_NANOS");

    /**
     * Diagnostic-only channel attribute recording the request line ({@code METHOD uri}) of the request
     * forwarded on the CONNECT-proxy loopback relay path (set by {@code UpstreamProxyRelayHandler}),
     * so the DEBUG streaming-decision diagnostic can identify which request a response belongs to.
     * Absent on non-relay paths. Behaviour-preserving.
     */
    public static final AttributeKey<String> REQUEST_LINE = AttributeKey.valueOf("REQUEST_LINE");

    /**
     * A JSON request body that turns on streaming, e.g. {@code {"stream": true}} (OpenAI/Anthropic/Codex).
     * Mirrors the detection in {@code NettyHttpClient} for the forward leg.
     */
    private static final Pattern STREAM_TRUE_IN_BODY = Pattern.compile("\"stream\"\\s*:\\s*true");

    /**
     * Whether the OUTGOING request asks for a streamed (Server-Sent Events style) response, so the
     * response should be relayed incrementally even if the upstream omits
     * {@code Content-Type: text/event-stream} (e.g. the OpenAI Codex backend used by the opencode CLI,
     * whose SSE response carries no content-type at all). Detected from the client's own intent: an
     * {@code Accept: text/event-stream} header, or a JSON request body containing {@code "stream": true}.
     * <p>
     * This is the netty-{@link HttpRequest} equivalent of {@code NettyHttpClient.requestExpectsStreamingResponse}
     * and is used on the CONNECT-proxy loopback relay path, where the request is a netty {@link FullHttpRequest}
     * rather than a MockServer {@code HttpRequest}. Reading the body via {@link ByteBuf#toString(java.nio.charset.Charset)}
     * is non-destructive (it does not advance the reader index), so the relayed request body is unaffected.
     * A body with a {@code Content-Encoding} is scanned as it decompresses, in slices, without changing or keeping the
     * request's bytes, and only up to {@code maxDecodedSize} decoded bytes: the relay
     * forwards the body still compressed, so MockServer decodes it again when it receives it.
     *
     * @param request        the netty request head (and, for body inspection, the {@link FullHttpRequest})
     * @param maxDecodedSize the most decoded bytes scanned, normally {@code maxRequestBodySize}
     * @return true when the response to this request should be relayed as a stream
     */
    public static boolean requestExpectsStreamingResponse(HttpRequest request, int maxDecodedSize) {
        if (request == null) {
            return false;
        }
        String accept = request.headers().get(HttpHeaderNames.ACCEPT);
        if (accept != null && accept.toLowerCase(Locale.US).contains("text/event-stream")) {
            return true;
        }
        if (isStreamingRequestPath(requestPath(request.uri()))) {
            return true;
        }
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null && contentType.toLowerCase(Locale.US).contains("json")
            && request instanceof FullHttpRequest) {
            ByteBuf content = ((FullHttpRequest) request).content();
            if (content != null && content.isReadable()) {
                if (request.headers().contains(HttpHeaderNames.CONTENT_ENCODING)) {
                    return decodedBodyRequestsStreaming(request.headers().getAll(HttpHeaderNames.CONTENT_ENCODING), content, maxDecodedSize);
                }
                String body = content.toString(StandardCharsets.UTF_8);
                if (STREAM_TRUE_IN_BODY.matcher(body).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    // a malformed percent-escape must not stop the request being relayed, so fall back to the raw path
    private static String requestPath(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        try {
            return decoder.path();
        } catch (IllegalArgumentException malformedEscape) {
            return decoder.rawPath();
        }
    }

    private static final int SCAN_SLICE_BYTES = 1024;

    // A coding the decompressor does not decode passes through unchanged, and is scanned as it is, as before.
    private static boolean decodedBodyRequestsStreaming(List<String> contentEncodings, ByteBuf content, int maxDecodedSize) {
        StreamTrueScanner scanner = new StreamTrueScanner(maxDecodedSize);
        EmbeddedChannel channel = new EmbeddedChannel(new MockServerHttpContentDecompressor(maxDecodedSize), scanner);
        NettyAllocator.pin(channel);
        try {
            DefaultHttpRequest head = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", DefaultHttpHeadersFactory.headersFactory().withValidation(false));
            for (String contentEncoding : contentEncodings) {
                head.headers().add(HttpHeaderNames.CONTENT_ENCODING, contentEncoding);
            }
            channel.writeInbound(head);
            int index = content.readerIndex();
            int end = content.writerIndex();
            while (index < end && !scanner.done) {
                int length = Math.min(SCAN_SLICE_BYTES, end - index);
                channel.writeInbound(new DefaultHttpContent(content.retainedSlice(index, length)));
                index += length;
            }
            if (!scanner.done) {
                channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
            }
            return scanner.found;
        } catch (RuntimeException corrupt) {
            // MockServer rejects a body that cannot be decompressed when it receives it; no streaming signal
            return scanner.found;
        } finally {
            try {
                channel.finishAndReleaseAll();
            } catch (RuntimeException ignored) {
                // a decoder abandoned mid-body may fail as it closes
            }
        }
    }

    /**
     * Looks for {@code "stream": true} across the decoded pieces, keeping only a short tail of the previous piece so a
     * match split between two pieces is still found.
     */
    private static final class StreamTrueScanner extends ChannelInboundHandlerAdapter {

        private static final int TAIL_CHARS = 256;

        private final long maxDecodedSize;
        private String tail = "";
        private long scanned;
        private boolean found;
        private boolean done;

        StreamTrueScanner(long maxDecodedSize) {
            this.maxDecodedSize = maxDecodedSize;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (!done && msg instanceof HttpContent) {
                    ByteBuf piece = ((HttpContent) msg).content();
                    scanned += piece.readableBytes();
                    // ISO-8859-1 maps each byte to one char, and the pattern is ASCII, so a UTF-8 body matches as it would decoded
                    String window = tail + piece.toString(StandardCharsets.ISO_8859_1);
                    if (STREAM_TRUE_IN_BODY.matcher(window).find()) {
                        found = true;
                        done = true;
                    } else if (scanned >= maxDecodedSize) {
                        done = true;
                    } else {
                        tail = window.substring(Math.max(0, window.length() - TAIL_CHARS));
                    }
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpResponse && !(msg instanceof FullHttpResponse)) {
            HttpResponse response = (HttpResponse) msg;
            // Diagnostic only — observe the decision and log it at DEBUG without changing the decision.
            logStreamingDecision(ctx, response);
            if (isStreamingEnabled() && !isStreamingDisabledOnChannel(ctx)
                && (isStreamingResponse(response) || isStreamingExpectedByRequest(ctx))) {
                switchToStreamingMode(ctx, response);
                return;
            }
        }
        // Non-streaming: delegate to super (standard aggregation)
        super.channelRead(ctx, msg);
    }

    /**
     * Emits one DEBUG diagnostic LogEntry describing whether this response head will be relayed as a
     * {@code STREAM} or {@code AGGREGATE}d, the response status, the content-type (or {@code <none>}),
     * which condition triggered streaming ({@code sse-content-type} / {@code request-expected-streaming}
     * / {@code none}), and — when available on the (relay) channel — the time-to-first-byte and the
     * request line. Purely observational: it recomputes the same booleans the decision uses but does
     * not change control flow. Gated so it is silent unless DEBUG is enabled and a logger is present
     * (the relay/backwards-compatible constructors pass a {@code null} logger, in which case nothing
     * is emitted).
     */
    private void logStreamingDecision(ChannelHandlerContext ctx, HttpResponse response) {
        if (!MockServerLogger.isEnabled(Level.DEBUG) || mockServerLogger == null) {
            return;
        }
        boolean sseContentType = isStreamingResponse(response);
        boolean expectedByRequest = isStreamingExpectedByRequest(ctx);
        boolean willStream = isStreamingEnabled() && !isStreamingDisabledOnChannel(ctx)
            && (sseContentType || expectedByRequest);
        String decision = willStream ? "STREAM" : "AGGREGATE";
        String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        String contentTypeDescription = contentType != null ? contentType : "<none>";
        String trigger = sseContentType
            ? "sse-content-type"
            : (expectedByRequest ? "request-expected-streaming" : "none");
        Long forwardedNanos = ctx.channel().attr(REQUEST_FORWARDED_NANOS).get();
        if (forwardedNanos != null) {
            long ttfbMs = (System.nanoTime() - forwardedNanos) / 1_000_000L;
            String requestLine = ctx.channel().attr(REQUEST_LINE).get();
            if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.DEBUG)
                        .setMessageFormat("streaming decision:{} status:{} content-type:{} trigger:{} ttfbMs:{} request:{}")
                        .setArguments(decision, response.status().code(), contentTypeDescription, trigger, ttfbMs, requestLine != null ? requestLine : "<unknown>")
                );
            }
        } else {
            if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.DEBUG)
                        .setMessageFormat("streaming decision:{} status:{} content-type:{} trigger:{}")
                        .setArguments(decision, response.status().code(), contentTypeDescription, trigger)
                );
            }
        }
    }

    private boolean isStreamingEnabled() {
        return configuration != null && Boolean.TRUE.equals(configuration.streamingResponsesEnabled());
    }

    private boolean isStreamingDisabledOnChannel(ChannelHandlerContext ctx) {
        Boolean disabled = ctx.channel().attr(DISABLE_RESPONSE_STREAMING).get();
        return Boolean.TRUE.equals(disabled);
    }

    private boolean isStreamingExpectedByRequest(ChannelHandlerContext ctx) {
        return Boolean.TRUE.equals(ctx.channel().attr(EXPECT_STREAMING_RESPONSE).get());
    }

    private void switchToStreamingMode(ChannelHandlerContext ctx, HttpResponse responseHead) {
        if (MockServerLogger.isEnabled(Level.DEBUG) && mockServerLogger != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.DEBUG)
                    .setMessageFormat("switching response to streaming relay (relayOnly={})")
                    .setArguments(relayOnly)
            );
        }
        ChannelPipeline pipeline = ctx.pipeline();

        if (relayOnly) {
            // Relay-only mode (loopback pipeline): just remove the aggregator so HttpObjects
            // flow directly to the next handler (e.g. DownstreamProxyRelayHandler).
            // ctx.fireChannelRead still works after remove because the context retains its
            // position in the pipeline chain.
            pipeline.remove(this);
            ctx.fireChannelRead(responseHead);
            return;
        }

        // Install the streaming relay handler; the response's bytes not yet written are bounded as its aggregated total would be
        StreamingResponseRelayHandler relayHandler = new StreamingResponseRelayHandler(configuration, mockServerLogger, maxContentLength());

        // Remove HttpClientHandler to prevent double completion of RESPONSE_FUTURE
        if (pipeline.get(HttpClientHandler.class) != null) {
            pipeline.remove(HttpClientHandler.class);
        }

        // Remove MockServerHttpClientCodec since it expects FullHttpResponse and would
        // fail on unaggregated HttpObject messages
        if (pipeline.get(MockServerHttpClientCodec.class) != null) {
            pipeline.remove(MockServerHttpClientCodec.class);
        }

        // Swap the per-request socket read timeout for a stream-appropriate idle bound.
        //
        // The socket read timeout (maxSocketTimeout, default 20s) armed on non-pooled channels
        // measures the gap between reads, which during streaming is the gap between chunks — a
        // streaming LLM response can legitimately pause far longer than that between chunks (model
        // reasoning), so it would kill a healthy stream. streamIdleTimeoutSeconds is documented to
        // REPLACE that socket timeout for streaming responses, so once streaming begins the socket
        // timeout is ALWAYS removed — otherwise streamIdleTimeoutSeconds=0 (disabled) would
        // paradoxically leave the 20s socket timeout armed and truncate long-paused streams.
        if (pipeline.get(ReadTimeoutHandler.class) != null) {
            pipeline.remove(ReadTimeoutHandler.class);
        }
        int idleTimeout = configuration.streamIdleTimeoutSeconds();
        if (idleTimeout > 0) {
            // Bound the stream by the stream-appropriate idle timeout (default 60s).
            pipeline.addBefore(ctx.name(), "streamIdleStateHandler", new IdleStateHandler(0, 0, idleTimeout, TimeUnit.SECONDS));
            pipeline.addAfter("streamIdleStateHandler", "streamIdleTimeoutHandler", new StreamIdleTimeoutHandler(mockServerLogger, idleTimeout, relayHandler::isAwaitingClient));
        }
        // idleTimeout == 0 explicitly disables the stream idle bound: the stream runs unbounded
        // (the socket timeout has been removed above so a healthy long-paused stream is not cut).

        // An invalid message must fail the stream before a decompressor can turn its failed last content into a clean end
        ChannelHandlerContext decompressorCtx = pipeline.context(HttpContentDecoder.class);
        pipeline.addBefore(decompressorCtx != null ? decompressorCtx.name() : ctx.name(), "streamedResponseDecoderResultGuard", new StreamedResponseDecoderResultGuard());

        // Replace this aggregator with the streaming relay handler
        pipeline.replace(this, "streamingResponseRelayHandler", relayHandler);

        // Fire the response head through the new handler using its own context
        ChannelHandlerContext relayCtx = pipeline.context(relayHandler);
        try {
            relayHandler.channelRead(relayCtx, responseHead);
        } catch (Exception e) {
            relayCtx.fireExceptionCaught(e);
        }
    }
}
