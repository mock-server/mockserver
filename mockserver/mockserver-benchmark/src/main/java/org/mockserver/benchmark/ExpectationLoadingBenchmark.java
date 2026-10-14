package org.mockserver.benchmark;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.scheduler.Scheduler;
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

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Expectation-loading / matcher-registration benchmark.
 *
 * <p>Measures the cost of registering a whole fixture of expectations in one batch via
 * {@link RequestMatchers#update(Expectation[], org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause)}
 * — the path a JSON/OpenAPI {@code initializationJsonPath} takes at startup. Each measured
 * invocation registers a fresh, empty matcher store (so every expectation is an add), which is
 * exactly what a server does when it loads an initializer file.
 *
 * <p><b>Why sweep {@code n}.</b> The regression this guards against is super-linear registration:
 * an O(n) {@code ConcurrentLinkedQueue.size()} in {@code CircularPriorityQueue.evictExcess()} on
 * every add makes a batch load O(n^2), so the queue keeps an O(1) size counter. Sweeping {@code n}
 * across a small case (1/5/100) and a large case (1000/5000/10000) makes both guarantees
 * observable in one run: the small case must not regress, and the large case must stay
 * proportional to {@code n} — if registration time grows faster than {@code n}, an O(n^2) path has
 * crept back in. This mirrors the small-and-large A/B intent of
 * {@link CandidateIndexBenchmark}.
 *
 * <pre>./run.sh ExpectationLoadingBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(1)
public class ExpectationLoadingBenchmark {

    @Param({"1", "5", "100", "1000", "5000", "10000"})
    public int n;

    private Expectation[] expectations;
    private RequestMatchers requestMatchers;

    @Setup(Level.Trial)
    public void setupTrial() {
        ConfigurationProperties.logLevel("WARN");
        ConfigurationProperties.detailedMatchFailures(false);
        // Build the fixture once — this benchmark measures registration, not construction of the
        // expectation objects. Reusing the same objects across invocations is safe: each measured
        // op registers them into a brand-new, empty matcher store, so they are always fresh adds.
        expectations = new Expectation[n];
        for (int i = 0; i < n; i++) {
            expectations[i] = new Expectation(request().withMethod("GET").withPath("/exact/path-" + i))
                .withId("expectation-id-" + i)
                .thenRespond(response().withBody("e" + i));
        }
    }

    @Setup(Level.Invocation)
    public void freshStore() {
        // A fresh, empty matcher store per invocation so every expectation is an add — the
        // startup-load shape. maxExpectations is raised above the largest n so the batch is
        // measured without eviction interfering.
        Configuration configuration = Configuration.configuration().maxExpectations(20000);
        requestMatchers = new RequestMatchers(
            configuration,
            new MockServerLogger(),
            mock(Scheduler.class),
            mock(WebSocketClientRegistry.class)
        );
    }

    @Benchmark
    public RequestMatchers loadExpectations() {
        requestMatchers.update(expectations, API);
        return requestMatchers;
    }
}
