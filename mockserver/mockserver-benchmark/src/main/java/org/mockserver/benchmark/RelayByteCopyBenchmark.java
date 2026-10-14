package org.mockserver.benchmark;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.mockserver.codec.StreamingAwareHttpObjectAggregator;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
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
 * Byte-cost micro-benchmark for the <b>CONNECT/relay response leg</b> — the proxy path the matcher
 * backstop says nothing about. A relayed message is decoded off the
 * downstream (server-facing) socket, re-aggregated, and re-encoded onto the upstream (client-facing)
 * socket; this benchmark drives exactly those codec steps in an {@link EmbeddedChannel} so that
 * <b>bytes/s</b> (from the {@code AverageTime} score) and <b>allocation per relayed KB</b> (from the
 * {@code -prof gc} {@code gc.alloc.rate.norm} bytes/op column) are derivable across a
 * {@link #bodySize} × {@link #readSize} sweep.
 *
 * <p><b>What is actually measured — the real relay components, not an imitation.</b> The two handlers
 * that appear in the CONNECT relay pipeline ({@code RelayConnectHandler#configurePipelines}) —
 * {@code UpstreamProxyRelayHandler} / {@code DownstreamProxyRelayHandler} — do <i>no</i> byte copying:
 * they {@code writeAndFlush} the already-decoded message by reference. The per-message work of the relay
 * lives in the codecs that flank them. This benchmark drives that real codec pair per relayed response:
 * <ul>
 *   <li><b>inbound (downstream server → MockServer):</b> {@link HttpResponseDecoder} (the decoder half
 *       of the {@code HttpClientCodec} on the loopback pipeline) feeding MockServer's own
 *       {@link StreamingAwareHttpObjectAggregator} in {@code relayOnly} mode — the exact aggregator
 *       instance the relay installs. A plain {@code application/octet-stream} response is used so the
 *       aggregator takes its standard (non-SSE) aggregation branch, which is the relayed-body path;</li>
 *   <li><b>outbound (MockServer → upstream client):</b> {@link HttpResponseEncoder} (the encoder half
 *       of the {@code HttpServerCodec} on the client-facing pipeline) re-serialising the aggregated
 *       {@link FullHttpResponse} back to wire bytes.</li>
 * </ul>
 *
 * <p><b>The body is NOT copied — the cost is per-fragment, not per-byte.</b>
 * With {@code Content-Length} framing the decoder emits {@code HttpContent} objects that are
 * <i>retained slices</i> of the input buffer (no copy) and the aggregator assembles them into a
 * {@link CompositeByteBuf} whose components are added <i>by reference</i> (no copy). {@code setup()}
 * confirms this at runtime by printing the runtime class of {@code aggregated.content()} (a
 * {@code CompositeByteBuf}) once, outside the measured region. Consequently the body bytes are never
 * memcpy'd by the relay codecs, and the allocation that {@code gc.alloc.rate.norm} reports is
 * <b>proportional to the number of fragments</b> (one wrapper {@code ByteBuf}, one {@code HttpContent},
 * and one composite-component entry per fragment), plus a fixed per-message overhead (status/header
 * Strings, the {@code HttpResponse} objects, the encoder's header buffer). Fragment count is linear in
 * {@code bodySize} only when {@code readSize} is held fixed, so a fixed-fragment sweep <i>looks</i> like
 * byte-proportional copying. The decisive control is the {@link #readSize} sweep at a fixed
 * {@code bodySize}: halving the fragment size doubles the fragment count and (if the cost is
 * per-fragment) roughly doubles alloc/op. The {@link #fragmentWrappersOnly} control isolates how much of that
 * per-fragment allocation is the benchmark's own wrapper {@code ByteBuf}s versus the codec objects.
 *
 * <p><b>Fed like a socket, on purpose.</b> A large HTTP response never arrives in one read; the kernel
 * hands it to Netty in bounded segments and the decoder emits a slice per segment. Feeding the response
 * in one giant buffer would collapse the fragment count to one and understate the per-message object
 * churn a real relay pays. So the raw bytes are fed in {@link #readSize}-byte fragments, and
 * {@code readSize} is swept so the per-fragment nature of the cost is visible rather than hidden. The
 * channels are reused across invocations, modelling a keep-alive CONNECT tunnel relaying many responses.
 *
 * <p><b>What this does NOT cover</b> (be explicit — do not read more into the number than is here):
 * <ul>
 *   <li><b>The socket read copy is omitted entirely.</b> In production the kernel→userspace socket read
 *       copies every body byte into a (pooled) receive buffer before the decoder ever sees it — a real,
 *       unavoidable per-byte relay cost. This benchmark hands the decoder {@code wrappedBuffer} views
 *       over a pre-existing array, so that per-byte copy is <b>not measured at all</b>. The number here
 *       is the relay's <i>object/aggregation</i> cost above the socket, not its total per-byte cost;</li>
 *   <li><b>The wrapper {@code ByteBuf}s are themselves counted allocations.</b> Each fragment is wrapped
 *       in an {@code Unpooled.wrappedBuffer} view, and those wrapper objects are per-fragment allocations
 *       included in {@code gc.alloc.rate.norm}. They are a faithful stand-in for the per-read receive
 *       buffer a socket relay allocates (see above), not an artefact to be subtracted; the
 *       {@link #fragmentWrappersOnly} control measures their share on the machine it runs on, and
 *       that figure is a LOWER BOUND: in the control the wrappers do not escape, so the JIT may
 *       scalar-replace them, whereas in {@link #relayResponse} each one escapes into the decoder;</li>
 *   <li>no sockets, no TLS, no event-loop hand-off, no flow-control/backpressure — a single-threaded,
 *       in-JVM codec measurement, not an end-to-end throughput figure;</li>
 *   <li>only the HTTP/1.1 aggregating relay leg. Out of scope: the SSE/streaming relay
 *       ({@code StreamingResponseRelayHandler}), the HTTP/2 relay, raw CONNECT-tunnel byte pumping
 *       (opaque TLS bytes, no HTTP codec at all), SOCKS, and content de/recompression;</li>
 *   <li>the request leg (client → server) is not separately measured; the response leg is representative
 *       and carries the large bodies.</li>
 * </ul>
 *
 * <pre>./run.sh -prof gc RelayByteCopyBenchmark
 * # decisive per-fragment control (fixed body, swept fragment size):
 * ./run.sh -prof gc RelayByteCopyBenchmark -p bodySize=262144 -p readSize=365,730,1460,5840</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class RelayByteCopyBenchmark {

    /** Relayed response body size in bytes. */
    @Param({"1024", "16384", "262144"})
    public int bodySize;

    /**
     * Fragment size the raw response is fed in, mimicking bounded socket reads. Swept as a {@code @Param}
     * so the per-fragment nature of the allocation is measurable: at a fixed {@code bodySize}, halving
     * this doubles the fragment count. If alloc/op tracks fragment count, the cost is per-fragment
     * object churn, not a per-byte copy.
     */
    @Param({"365", "730", "1460", "5840"})
    public int readSize;

    /** The complete raw HTTP/1.1 response (status line + headers + body) as it would arrive off the wire. */
    private byte[] rawResponse;

    /** Number of {@link #readSize}-byte fragments the response is fed in (= wrapper ByteBufs per op). */
    private int fragmentCount;

    /** Configured aggregation ceiling — named in the null-guard message if aggregation ever fails. */
    private int maxRequestBodySize;

    /** Inbound leg: decoder + MockServer's real relay aggregator (downstream server → MockServer). */
    private EmbeddedChannel inbound;

    /** Outbound leg: response encoder re-serialising to the wire (MockServer → upstream client). */
    private EmbeddedChannel outbound;

    @Setup(Level.Trial)
    public void setup() {
        Configuration configuration = Configuration.configuration();
        MockServerLogger mockServerLogger = new MockServerLogger();
        maxRequestBodySize = configuration.maxRequestBodySize();

        rawResponse = buildRawResponse(bodySize);
        fragmentCount = (rawResponse.length + readSize - 1) / readSize;

        // Diagnostic (outside the measured region): drive one relay through a throwaway pipeline and
        // print the runtime class of the aggregated content. A CompositeByteBuf here proves the body is
        // assembled from components added BY REFERENCE — i.e. never memcpy'd by the relay codecs.
        EmbeddedChannel probe = new EmbeddedChannel(
            new HttpResponseDecoder(configuration.maxInitialLineLength(), configuration.maxHeaderSize(), configuration.maxChunkSize()),
            new StreamingAwareHttpObjectAggregator(maxRequestBodySize, configuration, mockServerLogger, true)
        );
        feedFragmented(probe);
        FullHttpResponse probed = probe.readInbound();
        ByteBuf probedContent = probed.content();
        System.out.println("[RelayByteCopyBenchmark] bodySize=" + bodySize + " readSize=" + readSize
            + " fragments=" + fragmentCount
            + " aggregated.content()=" + probedContent.getClass().getName()
            + " isCompositeByteBuf=" + (probedContent instanceof CompositeByteBuf)
            + " readableBytes=" + probedContent.readableBytes());
        // readInbound() transferred ownership of this message to us, so finishAndReleaseAll() below
        // cannot reach it — it only drains what is STILL QUEUED inside the channel. Release it here or
        // Netty reports a leak once per (bodySize x readSize) combination.
        probed.release();
        probe.finishAndReleaseAll();

        inbound = new EmbeddedChannel(
            new HttpResponseDecoder(configuration.maxInitialLineLength(), configuration.maxHeaderSize(), configuration.maxChunkSize()),
            // relayOnly = true — the exact mode RelayConnectHandler installs on the loopback pipeline.
            new StreamingAwareHttpObjectAggregator(maxRequestBodySize, configuration, mockServerLogger, true)
        );
        outbound = new EmbeddedChannel(new HttpResponseEncoder());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // finishAndReleaseAll drains and releases anything still queued, so no ByteBuf leaks between trials.
        inbound.finishAndReleaseAll();
        outbound.finishAndReleaseAll();
    }

    /**
     * One relayed response: fragment the raw bytes into the decoder, let the real relay aggregator
     * produce the {@link FullHttpResponse}, then re-encode it on the upstream leg and drain the wire
     * bytes. Returns the encoded byte total as the sink so nothing is dead-code eliminated.
     */
    @Benchmark
    public long relayResponse() {
        feedFragmented(inbound);

        FullHttpResponse aggregated = inbound.readInbound();
        if (aggregated == null) {
            // The aggregator emits nothing only if the response failed to aggregate — most plausibly the
            // body exceeded the configured ceiling (HttpObjectAggregator raises TooLongFrameException).
            throw new IllegalStateException("relay aggregation produced no FullHttpResponse for bodySize="
                + bodySize + " (readSize=" + readSize + ", maxRequestBodySize=" + maxRequestBodySize + ")");
        }

        // Outbound: the relay hands the aggregated message to the client-facing HttpResponseEncoder,
        // which re-serialises it to wire bytes. writeOutbound takes ownership of and releases the
        // aggregated message.
        outbound.writeOutbound(aggregated);

        long encodedBytes = 0;
        ByteBuf encoded;
        while ((encoded = outbound.readOutbound()) != null) {
            encodedBytes += encoded.readableBytes();
            encoded.release();
        }
        return encodedBytes;
    }

    /**
     * Control experiment: allocate and release exactly the per-fragment wrapper {@link ByteBuf}s that
     * {@link #relayResponse} feeds the decoder, and nothing else. Its {@code gc.alloc.rate.norm} is the
     * portion of the relay's per-op allocation attributable to the benchmark's own fragment wrappers
     * (the socket-read-buffer stand-in), so {@code fragmentWrappersOnly ÷ relayResponse} is that share.
     */
    @Benchmark
    public long fragmentWrappersOnly() {
        long readable = 0;
        int offset = 0;
        while (offset < rawResponse.length) {
            int length = Math.min(readSize, rawResponse.length - offset);
            ByteBuf wrapper = Unpooled.wrappedBuffer(rawResponse, offset, length);
            readable += wrapper.readableBytes();
            wrapper.release();
            offset += length;
        }
        return readable;
    }

    /** Feed the whole raw response into {@code channel} as {@link #readSize}-byte inbound fragments. */
    private void feedFragmented(EmbeddedChannel channel) {
        int offset = 0;
        while (offset < rawResponse.length) {
            int length = Math.min(readSize, rawResponse.length - offset);
            // wrappedBuffer over a region shares the backing array (no payload copy) — the body-byte copy
            // a real socket read performs is therefore NOT included here (see class-level "does NOT cover").
            channel.writeInbound(Unpooled.wrappedBuffer(rawResponse, offset, length));
            offset += length;
        }
    }

    /**
     * Builds a complete, well-formed HTTP/1.1 response with a fixed {@code Content-Length} body of
     * exactly {@code size} bytes and a non-streaming content type, so the relay aggregator takes its
     * standard aggregation branch (the large-body relay path).
     */
    private static byte[] buildRawResponse(int size) {
        byte[] body = new byte[size];
        // Fill with printable bytes so the payload is representative and not a run of zeros the JIT or
        // allocator might treat specially.
        for (int i = 0; i < size; i++) {
            body[i] = (byte) ('a' + (i % 26));
        }
        String head =
            "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Length: " + size + "\r\n" +
                "\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.US_ASCII);
        byte[] response = new byte[headBytes.length + body.length];
        System.arraycopy(headBytes, 0, response, 0, headBytes.length);
        System.arraycopy(body, 0, response, headBytes.length, body.length);
        return response;
    }
}
