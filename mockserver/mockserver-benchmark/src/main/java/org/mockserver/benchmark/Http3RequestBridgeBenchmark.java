package org.mockserver.benchmark;

import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3Headers;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpRequestToMockServerHttpRequest;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Protocol;
import org.mockserver.netty.http3.Http3RequestBridge;
import org.mockserver.netty.unification.LenientInboundHttp2StreamFrameCodec;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * A within-run A/B of the <b>HTTP/3 request bridge</b> against the <b>HTTP/2
 * equivalent</b>, both converting one inbound request (headers frame + optional body frame)
 * into a MockServer {@link HttpRequest} model object exactly as the production pipelines do.
 *
 * <p>The question this answers is the one a central deployment needs, and only the ratio
 * answers it: <em>is the QUIC path allocating an order of magnitude more per request than
 * HTTP/2?</em> A standalone HTTP/3 number means nothing; the value is entirely in the ratio
 * measured under identical conditions in the same run. This follows
 * {@code CandidateIndexBenchmark}'s within-run A/B pattern (a {@code protocol} @Param selects
 * the two real code paths, isolating the change to a single variable), which cancels almost
 * all environmental noise. The deterministic, machine-independent signal is
 * {@code gc.alloc.rate.norm} (bytes/op); run with {@code -prof gc}.
 *
 * <p><b>How much confidence the answer carries.</b> The conclusion rests on the
 * <em>allocation ratio</em>, not the timing rows: allocation per op is deterministic and
 * fork-count-independent, so it survives the difference between a local JMH config and CI's.
 * It is a <em>floor-confidence</em> result rather than a point estimate, because the two
 * residual framing biases both run <em>against</em> HTTP/3 and neither is corrected here:
 * the HTTP/2 arm reuses one {@link io.netty.channel.embedded.EmbeddedChannel} across ops, so
 * it omits the per-stream child-channel, codec and mapper construction real HTTP/2 pays per
 * request (understating HTTP/2); and the HTTP/3 arm composites {@code Unpooled} heap buffers
 * where production uses {@code ctx.alloc()} pooled/direct buffers that largely do not land on
 * the heap (overstating HTTP/3). So the true ratio is no worse than measured, and the absolute
 * HTTP/3 figure is NOT production-representative — only the direction and magnitude are.
 *
 * <p><b>Both arms are the genuine production conversions, not mocks of them.</b>
 * <ul>
 *   <li><b>HTTP3</b>: a real {@link DefaultHttp3HeadersFrame} (+ {@link DefaultHttp3DataFrame})
 *       from {@code io.netty.handler.codec.http3} is passed through the real
 *       {@link Http3RequestBridge} — {@code parseHeaders} → {@code accumulateBody} into a
 *       {@link CompositeByteBuf} → {@code readAccumulatedBody} → {@code toHttpRequest} — the
 *       exact call sequence {@code Http3MockServerHandler} performs per request. The bridge is
 *       pure Java (no native QUIC transport is loaded), so this runs on any platform.</li>
 *   <li><b>HTTP2</b>: a real {@link DefaultHttp2HeadersFrame} (+ {@link DefaultHttp2DataFrame})
 *       is driven through the production {@link LenientInboundHttp2StreamFrameCodec} +
 *       {@link HttpObjectAggregator} chain (an {@link EmbeddedChannel}, as the multiplex child
 *       pipeline assembles it) to a {@link FullHttpRequest}, then mapped by the same
 *       {@link FullHttpRequestToMockServerHttpRequest} the HTTP/2 path's
 *       {@code MockServerHttpServerCodec} uses. This is the real HTTP/2 request bridge; the
 *       architectural asymmetry (H3 has a hand-written bridge, H2 reuses Netty's generic
 *       codec+aggregator then the HTTP/1.1 mapper) IS the thing being measured, not a flaw.</li>
 * </ul>
 *
 * <p>{@code @Setup} asserts both arms produce a model request with the SAME method, path and
 * body length, and that the HTTP/3 arm's request carries {@link Protocol#HTTP_3} — a
 * fail-loud guard against a silently-degraded arm (prove the benchmark exercises what its name
 * claims). If a codec silently dropped the body, or the
 * bridge failed to tag the protocol, setup throws before any measurement is taken.
 *
 * <pre>./run.sh -prof gc Http3RequestBridgeBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Http3RequestBridgeBenchmark {

    /** The A/B axis: the two real inbound request-conversion paths, compared in the same run. */
    @Param({"HTTP2", "HTTP3"})
    public String protocol;

    /** Request body size in bytes (0 = no body, a bare HEADERS request). */
    @Param({"0", "1024", "16384"})
    public int bodySize;

    private static final String PATH = "/api/orders?page=2&size=50";
    private static final String AUTHORITY = "example.com:8443";

    private String method;
    private byte[] payload;

    // HTTP/2 arm: a reusable embedded pipeline mirroring the multiplex child chain
    // (codec -> aggregator) plus the shared model mapper. Reused across ops so per-op
    // allocation reflects the conversion, not channel construction — the InboundDecodeBenchmark
    // convention. The inbound FRAMES, however, are freshly built per op (symmetric with the
    // HTTP/3 arm, which builds fresh frames per op too).
    private EmbeddedChannel h2Channel;
    private FullHttpRequestToMockServerHttpRequest h2Mapper;

    @Setup(Level.Trial)
    public void setup() {
        ConfigurationProperties.logLevel("WARN");
        method = bodySize > 0 ? "POST" : "GET";
        payload = bodySize > 0 ? buildJsonPayload(bodySize) : new byte[0];

        Configuration configuration = Configuration.configuration();
        h2Mapper = new FullHttpRequestToMockServerHttpRequest(
            configuration, new MockServerLogger(), true, null, 1080);
        h2Channel = new EmbeddedChannel(
            new LenientInboundHttp2StreamFrameCodec(),
            new HttpObjectAggregator(configuration.maxRequestBodySize()));

        // --- Genuineness guard ------------------------------------------------
        // Prove BOTH arms actually convert the SAME logical request into a model
        // HttpRequest, and that the HTTP/3 arm is really on the QUIC bridge (its result
        // is tagged Protocol.HTTP_3). If a codec silently dropped the body, or the bridge
        // stopped tagging the protocol, this throws before any measurement is taken.
        HttpRequest h2 = mapViaHttp2();
        HttpRequest h3 = mapViaHttp3();
        if (h3.getProtocol() != Protocol.HTTP_3) {
            throw new IllegalStateException("HTTP/3 arm did not exercise the QUIC bridge: protocol=" + h3.getProtocol());
        }
        if (!method.equals(h2.getMethod().getValue()) || !method.equals(h3.getMethod().getValue())) {
            throw new IllegalStateException("method mismatch: h2=" + h2.getMethod() + " h3=" + h3.getMethod() + " expected=" + method);
        }
        int h2Len = h2.getBodyAsRawBytes() == null ? 0 : h2.getBodyAsRawBytes().length;
        int h3Len = h3.getBodyAsRawBytes() == null ? 0 : h3.getBodyAsRawBytes().length;
        // Both arms must preserve the SAME body verbatim (payload.length, which for a
        // non-empty body is >= the nominal bodySize because buildJsonPayload rounds up to a
        // valid JSON document). Comparing to payload.length — not bodySize — is the real
        // invariant: it proves neither codec silently truncated or padded the body.
        if (h2Len != payload.length || h3Len != payload.length) {
            throw new IllegalStateException("body length mismatch: h2=" + h2Len + " h3=" + h3Len + " expected=" + payload.length);
        }
        if (!h2.getPath().getValue().equals(h3.getPath().getValue())) {
            throw new IllegalStateException("path mismatch: h2=" + h2.getPath() + " h3=" + h3.getPath());
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (h2Channel != null) {
            h2Channel.finishAndReleaseAll();
        }
    }

    @Benchmark
    public HttpRequest mapRequest() {
        return "HTTP3".equals(protocol) ? mapViaHttp3() : mapViaHttp2();
    }

    /**
     * The genuine HTTP/3 request bridge: real Http3 frames → {@link Http3RequestBridge} →
     * model {@link HttpRequest}, the exact sequence {@code Http3MockServerHandler} runs.
     */
    private HttpRequest mapViaHttp3() {
        Http3Headers headers = buildHttp3Headers();
        DefaultHttp3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame(headers);
        Http3RequestBridge.ParsedHeaders parsed = Http3RequestBridge.parseHeaders(headersFrame);

        CompositeByteBuf accumulator = Unpooled.compositeBuffer();
        try {
            if (payload.length > 0) {
                DefaultHttp3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(payload));
                Http3RequestBridge.accumulateBody(accumulator, dataFrame);
                dataFrame.release();
            }
            // Pass the accumulated buffer straight through, which is the sequence
            // Http3MockServerHandler now runs. The older byte[] overload is still public API and
            // still covered by its own tests, but it is no longer the production path, so
            // benchmarking it would measure code the server does not execute.
            return Http3RequestBridge.toHttpRequest(
                parsed.method(), parsed.path(), parsed.scheme(), parsed.authority(), parsed.headers(), accumulator);
        } finally {
            accumulator.release();
        }
    }

    /**
     * The genuine HTTP/2 request bridge: real Http2 frames → production codec+aggregator →
     * {@link FullHttpRequest} → the shared {@link FullHttpRequestToMockServerHttpRequest}.
     */
    private HttpRequest mapViaHttp2() {
        Http2Headers headers = buildHttp2Headers();
        boolean hasBody = payload.length > 0;
        h2Channel.writeInbound(new DefaultHttp2HeadersFrame(headers, !hasBody));
        if (hasBody) {
            h2Channel.writeInbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(payload), true));
        }
        FullHttpRequest fullHttpRequest = h2Channel.readInbound();
        if (fullHttpRequest == null) {
            throw new IllegalStateException("HTTP/2 codec+aggregator produced no FullHttpRequest");
        }
        try {
            return h2Mapper.mapFullHttpRequestToMockServerRequest(fullHttpRequest, null, null, null, Protocol.HTTP_2);
        } finally {
            fullHttpRequest.release();
        }
    }

    private Http3Headers buildHttp3Headers() {
        Http3Headers headers = new io.netty.handler.codec.http3.DefaultHttp3Headers(false);
        headers.method(method);
        headers.path(PATH);
        headers.scheme("https");
        headers.authority(AUTHORITY);
        headers.add("user-agent", "mockserver-benchmark/1.0");
        headers.add("accept", "application/json");
        if (bodySize > 0) {
            headers.add("content-type", "application/json");
            headers.add("content-length", Integer.toString(bodySize));
        }
        return headers;
    }

    private Http2Headers buildHttp2Headers() {
        Http2Headers headers = new DefaultHttp2Headers(false);
        headers.method(method);
        headers.path(PATH);
        headers.scheme("https");
        headers.authority(AUTHORITY);
        headers.add("user-agent", "mockserver-benchmark/1.0");
        headers.add("accept", "application/json");
        if (bodySize > 0) {
            headers.add("content-type", "application/json");
            headers.add("content-length", Integer.toString(bodySize));
        }
        return headers;
    }

    /** Builds a syntactically-valid JSON document of at least {@code size} bytes (the JSON body branch). */
    private static byte[] buildJsonPayload(int size) {
        StringBuilder sb = new StringBuilder(size + 64);
        sb.append("{\"items\":[");
        int i = 0;
        while (sb.length() < size) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(i)
                .append(",\"name\":\"record-").append(i)
                .append("\",\"payload\":\"").append("y".repeat(16)).append("\"}");
            i++;
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
