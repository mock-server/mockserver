package org.mockserver.benchmark;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.codec.MockServerHttpToNettyHttpResponseEncoder;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
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
 * Allocation micro-benchmark for the OUTBOUND response path entered at
 * {@link ResponseWriter#writeResponse(HttpRequest, HttpResponse, boolean)} — the layer above the
 * pure encoder that {@link ResponseWriteBenchmark} measures. Where {@code ResponseWriteBenchmark}
 * enters at {@code channel.writeOutbound(response)} (encoder only), this drives a real
 * {@link ResponseWriter}, so the per-response work the encoder never sees is measured too: the CORS
 * pass, the default-header stamp, the Content-Length diagnostic, the defensive clone in
 * {@code addConnectionHeader}, and the {@code Connection} header synthesis. The per-merge allocation
 * gate covers only the encoder-only {@code ResponseWriteBenchmark}, so it cannot see this layer.
 *
 * <p>This is a SEPARATE class from {@code ResponseWriteBenchmark} deliberately: the per-merge
 * allocation gate and the daily microbench both select benchmarks by explicit class-name include,
 * so a new method on the gated class would shift its pinned row count while a new class does not.
 *
 * <p>The response carries a handful of headers and two cookies — a representative mock-response
 * shape — so the header clone and the cookie clone that {@code addConnectionHeader} performs are
 * both exercised. The concrete
 * {@link ResponseWriter} subclass encodes each response to the wire through the same two handlers the
 * server wires ({@link MockServerHttpToNettyHttpResponseEncoder} + {@link HttpResponseEncoder}) and
 * the benchmark drains and releases every produced {@code ByteBuf}, so the {@code gc.alloc.rate.norm}
 * (bytes/op) column is the full model&rarr;wire allocation, not a leaked-buffer artefact. The
 * {@link EmbeddedChannel} and writer are built once per trial and reused.
 *
 * <p>The log level is fixed at the shipped default (INFO). Gating the Content-Length diagnostic on
 * the log level avoids a scan/parse, not an allocation, so this bytes/op instrument does not see it.
 *
 * <pre>./run.sh -prof gc ResponseWriterAllocBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ResponseWriterAllocBenchmark {

    /** Response body size in bytes. */
    @Param({"1024", "16384"})
    public int responseSize;

    private EmbeddedChannel channel;
    private EncodingResponseWriter writer;
    private HttpRequest request;
    private String payload;

    /**
     * A minimal {@link ResponseWriter} whose {@code sendResponse} encodes the response to the wire
     * through the real outbound handlers, so the benchmark measures writeResponse + encode.
     */
    private static final class EncodingResponseWriter extends ResponseWriter {
        private final EmbeddedChannel channel;

        private EncodingResponseWriter(Configuration configuration, MockServerLogger mockServerLogger, EmbeddedChannel channel) {
            super(configuration, mockServerLogger);
            this.channel = channel;
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            channel.writeOutbound(response);
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        channel = new EmbeddedChannel(
            new HttpResponseEncoder(),
            new MockServerHttpToNettyHttpResponseEncoder(new MockServerLogger())
        );
        Configuration configuration = Configuration.configuration();
        writer = new EncodingResponseWriter(configuration, new MockServerLogger(configuration, ResponseWriterAllocBenchmark.class), channel);
        request = HttpRequest.request("/some/path").withKeepAlive(true);
        payload = new String(buildJsonPayload(responseSize), StandardCharsets.UTF_8);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        Object out;
        while ((out = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(out);
        }
        channel.finishAndReleaseAll();
    }

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
    public void writeThroughResponseWriter(Blackhole blackhole) {
        // The mock-response hot path: apiResponse=false, so this is the version-header-free shape a
        // mocked API returns millions of times, here carrying a few headers and cookies.
        HttpResponse response = HttpResponse.response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withHeader("cache-control", "no-cache")
            .withHeader("x-request-id", "b1c2d3e4-f5a6-7890-abcd-ef0123456789")
            .withCookie("session", "abc123")
            .withCookie("tracking", "xyz789")
            .withBody(payload);

        writer.writeResponse(request, response, false);

        Object out;
        while ((out = channel.readOutbound()) != null) {
            if (out instanceof ByteBuf) {
                blackhole.consume(((ByteBuf) out).readableBytes());
            }
            ReferenceCountUtil.release(out);
        }
    }
}
