package org.mockserver.benchmark;

import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.event.Level;

import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark for the per-request configuration-resolution hot paths. Unlike {@link ResponseWriterAllocBenchmark}, whose writer (and therefore its CORS
 * resolution) is built once per trial in {@code @Setup}, this benchmark builds the writer INSIDE the
 * measured op — matching production, where {@code HttpRequestHandler} constructs a fresh
 * {@code NettyResponseWriter} for every request.
 *
 * <ul>
 *   <li>{@link #logLevelResolution()} — {@code Configuration.logLevel()}, called 5-10x/request by
 *       {@code MockServerLogger.isEnabledForInstance};</li>
 *   <li>{@link #maxLoggedBodyBytesResolution()} — {@code ConfigurationProperties.maxLoggedBodyBytes()},
 *       read per request on the log path;</li>
 *   <li>{@link #perRequestWriterConstruction()} — a per-request {@link ResponseWriter}, which resolves
 *       the CORS headers (five configuration lookups plus a String concat) even though CORS headers
 *       are unused for default mock traffic;</li>
 *   <li>{@link #perRequestFlagResolution()} — the five flags every mocked request reads once or more
 *       (metricsEnabled, dataPlaneAuthenticationRequired, otelPropagateTraceContext,
 *       validateRequestsAgainstOpenApiSpec, defaultResponseHeaders), falling through to the JVM-wide
 *       defaults as a server started without overrides does.</li>
 * </ul>
 *
 * The {@code gc.alloc.rate.norm} (bytes/op) column from {@code -prof gc} is the relevant signal.
 *
 * <pre>./run.sh -prof gc PerRequestConfigResolutionBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PerRequestConfigResolutionBenchmark {

    private Configuration configuration;
    private MockServerLogger mockServerLogger;

    private static final class ConstructionOnlyResponseWriter extends ResponseWriter {
        private ConstructionOnlyResponseWriter(Configuration configuration, MockServerLogger mockServerLogger) {
            super(configuration, mockServerLogger);
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            // unused: this benchmark measures per-request construction, not the send path
        }
    }

    @Setup
    public void setup() {
        configuration = Configuration.configuration();
        mockServerLogger = new MockServerLogger(configuration, PerRequestConfigResolutionBenchmark.class);
    }

    @Benchmark
    public Level logLevelResolution() {
        return configuration.logLevel();
    }

    @Benchmark
    public int maxLoggedBodyBytesResolution() {
        return ConfigurationProperties.maxLoggedBodyBytes();
    }

    @Benchmark
    public void perRequestFlagResolution(Blackhole blackhole) {
        blackhole.consume(configuration.metricsEnabled());
        blackhole.consume(configuration.dataPlaneAuthenticationRequired());
        blackhole.consume(configuration.otelPropagateTraceContext());
        blackhole.consume(configuration.validateRequestsAgainstOpenApiSpec());
        blackhole.consume(configuration.defaultResponseHeaders());
    }

    @Benchmark
    public ResponseWriter perRequestWriterConstruction() {
        return new ConstructionOnlyResponseWriter(configuration, mockServerLogger);
    }
}
