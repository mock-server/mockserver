package org.mockserver.benchmark;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.mockserver.codec.BodyDecoderEncoder;
import org.mockserver.codec.MockServerHttpToNettyHttpResponseEncoder;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
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
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Hot-path micro-benchmark for the OUTBOUND response-serialisation-and-write path — the mirror of
 * {@link InboundDecodeBenchmark}. It exercises the two handlers a mock response actually passes
 * through on its way to the wire, in the pipeline order the server wires them
 * ({@code MockServerHttpServerCodec} + {@code HttpServerCodec}):
 * <ol>
 *   <li>{@link MockServerHttpToNettyHttpResponseEncoder} — maps the MockServer {@link HttpResponse}
 *       model into Netty {@code DefaultFullHttpResponse} objects (via
 *       {@code MockServerHttpResponseToFullHttpResponse}: body materialisation, header/cookie/
 *       Content-Length synthesis, HTTP/2 stream-id stamping);</li>
 *   <li>{@link HttpResponseEncoder} — serialises that Netty response object all the way to the wire
 *       {@code ByteBuf} (status line, header block, and body bytes).</li>
 * </ol>
 *
 * <p>This is deliberately NOT a proxy for the path: driving both handlers through an
 * {@link EmbeddedChannel} and draining the produced wire {@code ByteBuf}(s) measures the real
 * model&rarr;object&rarr;bytes allocation, exactly the response-write cost {@code MatchingBenchmark}
 * (matching only) and {@code InboundDecodeBenchmark} (request decode only) do not touch. It is the
 * response-write arm the allocation backstop needs to stop a regression that merely moves bytes out
 * of the matcher and into the response writer reading as an improvement.
 *
 * <pre>./run.sh -prof gc ResponseWriteBenchmark</pre>
 *
 * <p>The {@code gc.alloc.rate.norm} (bytes/op) column is the headline number. The
 * {@link EmbeddedChannel} and its pipeline are built once per trial and REUSED across invocations
 * (a real server reuses the channel per connection), so the only per-op allocation measured is the
 * serialise-and-encode work itself, not channel/pipeline setup. Each invocation drains and releases
 * every produced outbound {@code ByteBuf} so the measured allocation is not inflated by leaked
 * buffers and the channel returns to a clean state for the next op.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ResponseWriteBenchmark {

    /** Response body size in bytes — the per-op body materialisation + encode scales with this. */
    @Param({"1024", "16384", "262144"})
    public int responseSize;

    /**
     * Whether the response body declares its own charset.
     * <ul>
     *   <li>{@code false} — {@code withBody(String)}: the body has no declared charset, so its
     *       materialised bytes use the ISO-8859-1 default while the JSON wire charset is UTF-8. The
     *       outbound path must re-encode; this is the implicit-charset arm.</li>
     *   <li>{@code true} — {@code withBody(String, APPLICATION_JSON_UTF_8)}: the body declares UTF-8,
     *       which is exactly the wire charset, so the outbound path may reuse the already-materialised
     *       bytes with no second encode. This is the explicit-charset arm the reuse optimisation targets
     *       ({@code withBody(json, JSON_UTF_8)} / {@code json(str, JSON_UTF_8)} / {@code withBody(str,
     *       charset)} usage).</li>
     * </ul>
     * Both arms emit the identical UTF-8 wire body and Content-Length, so the only difference measured
     * is whether the body-materialisation copy happens once or twice.
     */
    @Param({"false", "true"})
    public boolean declareBodyCharset;

    /** The two-handler outbound pipeline (MockServer response encoder + Netty HTTP encoder), reused per op. */
    private EmbeddedChannel channel;

    /** Pre-built JSON response body of {@code responseSize} bytes. */
    private String payload;

    @Setup(Level.Trial)
    public void setup() {
        // Handlers are added head-first: writeOutbound injects at the tail and propagates toward the
        // head, so the LAST-added handler runs FIRST on the outbound path. HttpResponseEncoder is
        // added first (nearer the head, runs second: object -> wire bytes); the MockServer encoder is
        // added last (nearer the tail, runs first: HttpResponse model -> Netty response object) —
        // matching the real server, where the mock-response encoder sits above the HTTP codec.
        channel = new EmbeddedChannel(
            new HttpResponseEncoder(),
            new MockServerHttpToNettyHttpResponseEncoder(new MockServerLogger())
        );
        payload = new String(buildJsonPayload(responseSize), StandardCharsets.UTF_8);
        assertArmMeasuresTheShapeItClaims();
    }

    /**
     * Fails the trial unless each arm actually exercises the path it is named for. Without this a
     * change to how the encoder resolves a content type would leave both arms silently measuring
     * the same shape, and the benchmark would keep reporting a difference it was no longer
     * measuring. Identity of the backing array is the observable signal: the outbound buffer wraps
     * the body's own {@code rawBytes} when reuse fires, and a freshly-encoded array when it does not.
     */
    private void assertArmMeasuresTheShapeItClaims() {
        HttpResponse probe = HttpResponse.response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json");
        if (declareBodyCharset) {
            probe.withBody(payload, MediaType.APPLICATION_JSON_UTF_8);
        } else {
            probe.withBody(payload);
        }

        byte[] materialised = probe.getBody().getRawBytes();
        ByteBuf wire = new BodyDecoderEncoder().bodyToByteBuf(probe.getBody(), "application/json");
        boolean reused;
        try {
            reused = wire.hasArray() && wire.array() == materialised;
        } finally {
            io.netty.util.ReferenceCountUtil.release(wire);
        }

        if (declareBodyCharset && !reused) {
            throw new IllegalStateException(
                "declareBodyCharset=true arm is NOT reusing the body's materialised bytes -- it is "
                    + "measuring the re-encode path, so its numbers do not describe the optimisation");
        }
        if (!declareBodyCharset && reused) {
            throw new IllegalStateException(
                "declareBodyCharset=false arm IS reusing the body's materialised bytes -- the "
                    + "implicit-charset control is no longer a control");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Drain any residue, then finish the channel so no buffered outbound ByteBuf leaks.
        Object out;
        while ((out = channel.readOutbound()) != null) {
            io.netty.util.ReferenceCountUtil.release(out);
        }
        channel.finishAndReleaseAll();
    }

    /**
     * Builds a syntactically-valid JSON document of (at least) {@code size} bytes — representative of
     * the dominant JSON mock-response body.
     */
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

    @Benchmark
    public void writeResponseToWire(Blackhole blackhole) {
        // A fresh HttpResponse per op (as the action handler produces one per request), with a JSON
        // body and content type — the common mock-response shape. Content-Length is synthesised by
        // the mapper from the materialised body.
        HttpResponse response = HttpResponse.response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json");
        if (declareBodyCharset) {
            // Body declares UTF-8 (== the wire charset): eligible for zero-copy reuse of its bytes.
            response.withBody(payload, MediaType.APPLICATION_JSON_UTF_8);
        } else {
            // Body has no declared charset: the outbound path must re-encode (implicit-charset arm).
            response.withBody(payload);
        }

        channel.writeOutbound(response);

        // Drain and release every produced wire ByteBuf. Consuming its readableBytes through the
        // Blackhole keeps the encode from being dead-code-eliminated and returns the channel to a
        // clean outbound state for the next invocation.
        Object out;
        while ((out = channel.readOutbound()) != null) {
            if (out instanceof io.netty.buffer.ByteBuf) {
                blackhole.consume(((io.netty.buffer.ByteBuf) out).readableBytes());
            }
            io.netty.util.ReferenceCountUtil.release(out);
        }
    }
}
