package org.mockserver.benchmark;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.model.HttpRequest;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * The cost of {@link RequestMatchers#clear(org.mockserver.model.RequestDefinition)}
 * as the expectation store grows, with the candidate-index clear fast path ON vs OFF.
 *
 * <p><b>The cost under test.</b> Without the fast path {@code clear(RequestDefinition)} runs a full
 * reverse match against EVERY registered expectation — O(n) in store size — and a per-test teardown
 * does three of them. PATH-ONLY clears (a path but no method) cannot be narrowed by the (method,path)
 * index; the path-only index dimension can. The lever is the NUMBER of comparisons, not their
 * individual cost, so this benchmark isolates exactly that.
 *
 * <p><b>Arms.</b>
 * <ul>
 *   <li>{@code indexMode=SCAN} — clear fast path disabled (threshold above n): the clear iterates
 *       all n expectations. This is the un-indexed baseline.</li>
 *   <li>{@code indexMode=INDEX} — clear fast path engaged (threshold 2): a path-only literal clear
 *       consults one path bucket plus the (here empty) path fallthrough.</li>
 * </ul>
 * crossed with store size {@code n}. All seeded expectations are literal {@code GET /exact/path-i}
 * on DISTINCT literal paths, so each lands in its own path bucket — the realistic large-store
 * shape.
 *
 * <p><b>Two methods, two questions.</b>
 * <ul>
 *   <li>{@link #clearNoMatch} clears a path that matches NOTHING, so the store is not mutated and
 *       each invocation is pure candidate-enumeration cost: SCAN pays n reverse matches, INDEX pays
 *       one empty-bucket lookup. This isolates the number-of-comparisons lever with zero rebuild
 *       noise, and is the cleanest read of the win.</li>
 *   <li>{@link #clearOneAndRestore} clears ONE existing path (removing it) and immediately re-adds
 *       it, so the store size is invariant across invocations. This is the realistic per-teardown
 *       clear: both arms pay the same single remove+add of index maintenance; the delta is again
 *       the scan (n vs 1). It is noisier (it mutates) but faithful to a teardown.</li>
 * </ul>
 *
 * <p><b>Reading it.</b> A single-thread, average-time microbenchmark; read the SCAN-vs-INDEX RATIO
 * at each n and how SCAN grows with n while INDEX stays flat — not the raw microseconds (a contended
 * laptop inflates absolutes). A NEGATIVE result (INDEX not faster, or slower at small n from the
 * union allocation) is a valid outcome and should be reported as such.
 *
 * <pre>./run.sh CandidateClearBenchmark -f 1 -wi 3 -i 5 -t 1</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CandidateClearBenchmark {

    @Param({"100", "1000", "5000", "15000"})
    public int n;

    @Param({"SCAN", "INDEX"})
    public String indexMode;

    private RequestMatchers requestMatchers;
    private HttpRequest clearExistingPath;
    private Expectation restoreExpectation;
    private HttpRequest clearMissingPath;

    @Setup(Level.Trial)
    public void setup() {
        ConfigurationProperties.logLevel("WARN");
        ConfigurationProperties.detailedMatchFailures(false);

        Configuration configuration = Configuration.configuration();
        requestMatchers = new RequestMatchers(
            configuration,
            new MockServerLogger(),
            mock(Scheduler.class),
            mock(WebSocketClientRegistry.class)
        );
        // SCAN: threshold above the largest n so the clear fast path never engages (full linear
        // scan). INDEX: threshold 2 so it engages for every n. Set on the instance, not a JVM
        // property, so it is deterministic across forks.
        requestMatchers.withCandidateIndexThreshold("SCAN".equals(indexMode) ? Integer.MAX_VALUE : 2);

        for (int i = 0; i < n; i++) {
            requestMatchers.add(
                new Expectation(request().withMethod("GET").withPath("/exact/path-" + i))
                    .thenRespond(response().withBody("e" + i)),
                API
            );
        }

        // A path-only clear (no method) of a path in the MIDDLE of the store.
        int mid = n / 2;
        clearExistingPath = request().withPath("/exact/path-" + mid);
        restoreExpectation = new Expectation(request().withMethod("GET").withPath("/exact/path-" + mid))
            .thenRespond(response().withBody("e" + mid));
        // A path-only clear of a path that is not present — removes nothing, mutates nothing.
        clearMissingPath = request().withPath("/exact/no-such-path");
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        System.out.println("[clear] indexMode=" + indexMode + " n=" + n
            + " size=" + requestMatchers.size());
    }

    /**
     * Pure candidate-enumeration cost: the clear matches nothing, so the store is unchanged and the
     * only work is the reverse-match loop (SCAN: n) or the bucket lookup (INDEX: 1 empty bucket).
     */
    @Benchmark
    public void clearNoMatch() {
        requestMatchers.clear(clearMissingPath.clone());
    }

    /**
     * Realistic per-teardown clear: remove one existing path then restore it so store size is
     * invariant across invocations. Both arms pay the same single remove+add; the delta is the scan.
     */
    @Benchmark
    public void clearOneAndRestore() {
        requestMatchers.clear(clearExistingPath.clone());
        requestMatchers.add(restoreExpectation.clone(), API);
    }
}
