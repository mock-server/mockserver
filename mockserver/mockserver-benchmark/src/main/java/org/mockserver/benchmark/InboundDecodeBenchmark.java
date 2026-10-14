package org.mockserver.benchmark;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpRequestToMockServerHttpRequest;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Protocol;
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
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

/**
 * Hot-path micro-benchmark for {@link FullHttpRequestToMockServerHttpRequest#mapFullHttpRequestToMockServerRequest}
 * — the inbound decode of a request-with-body into the MockServer {@code HttpRequest} model.
 *
 * <p>The mapper materialises the body once into a {@code byte[]} (the non-destructive
 * {@code getBytes} for the original-raw-body diff) and hands those bytes straight to
 * {@code bytesToBody}; going through {@code BodyDecoderEncoder.byteBufToBody} instead would copy the
 * body a second time. This benchmark drives that path with a JSON body of varying size so the
 * {@code -prof gc} {@code gc.alloc.rate.norm} (bytes/op) column shows how many body-size copies are made.
 *
 * <pre>./run.sh -prof gc InboundDecodeBenchmark</pre>
 *
 * <p>Each invocation builds a fresh {@link FullHttpRequest} wrapping a shared, pre-built payload
 * ({@code Unpooled.wrappedBuffer} does NOT copy the payload — so the only body-size {@code byte[]}
 * allocations measured are the ones the mapper itself performs) and maps it once. Uses the default
 * {@link Configuration} (the common case).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class InboundDecodeBenchmark {

    /** Body size in bytes (the per-op copy(s) the optimisation targets scale with this). */
    @Param({"1024", "16384", "262144"})
    public int bodySize;

    private FullHttpRequestToMockServerHttpRequest mapper;

    /** Pre-built JSON payload of {@code bodySize} bytes — wrapped (not copied) per op. */
    private byte[] payload;

    @Setup(Level.Trial)
    public void setup() {
        mapper = new FullHttpRequestToMockServerHttpRequest(
            Configuration.configuration(),
            new MockServerLogger(),
            false,
            null,
            1080
        );
        payload = buildJsonPayload(bodySize);
    }

    /**
     * Builds a syntactically-valid JSON document of (at least) {@code size} bytes so the mapper takes
     * the JSON body branch — representative of the dominant request-with-JSON-body inbound path.
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
    public HttpRequest mapRequestWithBody() {
        // wrappedBuffer shares the payload array (no copy), so the body-size byte[] allocations
        // measured here are exactly those the mapper performs internally.
        FullHttpRequest nettyRequest = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/api/orders",
            Unpooled.wrappedBuffer(payload)
        );
        nettyRequest.headers().set(CONTENT_TYPE, "application/json");
        try {
            return mapper.mapFullHttpRequestToMockServerRequest(nettyRequest, null, null, null, Protocol.HTTP_1_1);
        } finally {
            nettyRequest.release();
        }
    }
}
