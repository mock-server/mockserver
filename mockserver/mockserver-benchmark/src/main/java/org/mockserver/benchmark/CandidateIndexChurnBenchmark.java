package org.mockserver.benchmark;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.model.ExpectationId;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.mock;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * The cost of {@link RequestMatchers#firstMatchingExpectation} when the
 * expectation store is being MUTATED (churned), versus a STATIC store.
 *
 * <p><b>The mechanism under test.</b> {@code CircularPriorityQueue.toSortedList()} caches
 * the sorted matcher snapshot and INVALIDATES it on every structural mutation; in parallel the
 * {@link RequestMatchers} modification counter bumps on every mutation and
 * {@code CandidateIndex} rebuilds whenever its generation is stale. So a single mutation
 * forces the NEXT request to rebuild both the full sorted list and (when engaged) the
 * candidate index. Because the serving path itself schedules lazy removal of inactive
 * ({@code once()} / limited-{@code Times}) matchers, a workload of consumable expectations
 * mutates the store continuously — every request then pays a rebuild.
 *
 * <p><b>What this benchmark measures.</b> The per-request cost and per-request allocation of
 * {@code firstMatchingExpectation} for a HIT, in four arms crossed with size {@code n}:
 * <ul>
 *   <li>{@code mode=STATIC} — the store is built once and never mutated: every request reuses
 *       the cached sorted list (and, in INDEX mode, the built buckets). This is the baseline.</li>
 *   <li>{@code mode=CHURN} — a SINGLE background writer thread continuously removes and re-adds a
 *       dedicated churn expectation through the real {@code clear(id)} / {@code add} control-plane
 *       API. Each such mutation invalidates the cached sorted list (bumping its generation) and
 *       bumps the {@link RequestMatchers} modification counter exactly as the serving path's lazy
 *       removal does — so reader requests find the cache invalidated and rebuild. A single writer honours the {@code CircularPriorityQueue}
 *       single-writer contract; the reader threads use only the (concurrency-safe) read path.</li>
 *   <li>{@code indexMode=SCAN} — candidate index disabled (threshold above n): the reader
 *       rebuilds only the full sorted list under churn.</li>
 *   <li>{@code indexMode=INDEX} — candidate index engaged (threshold 2): the reader rebuilds the
 *       full sorted list AND the candidate index under churn.</li>
 * </ul>
 *
 * <p><b>Threads.</b> The reader thread count is set on the command line ({@code -t 1}, {@code -t 4},
 * {@code -t 8}). The finding claims the cost WORSENS with more cores because
 * {@code toSortedList()}'s cache is lock-free: each concurrent reader that finds the cache
 * invalidated rebuilds the whole list independently (duplicate work, never a stale result). Only a multi-thread run can show that.
 *
 * <p><b>What it does NOT measure.</b> It fixes {@code outcome=HIT} (the rebuild happens before
 * the scan, so a MISS tells the same rebuild story with a longer SCAN tail — omitted to bound
 * the matrix). It does not model a partially-warm cache (the writer churns full-tilt, i.e. the
 * WORST-CASE continuous churn a high-throughput {@code once()} workload produces); a
 * rate-limited writer would interpolate between STATIC and this worst case. Absolute
 * magnitudes are from a contended laptop — read the STATIC-vs-CHURN and 1-vs-N-thread RATIOS,
 * not the raw microseconds.
 *
 * <p><b>Proof the churn arm actually rebuilds</b> (not assumed) lives in
 * {@code org.mockserver.mock.CandidateIndexChurnRebuildProof} — a package-private verifier that
 * shows (a) the writer's mutation bumps the modification counter and (b) it makes
 * {@code toSortedList()} return a fresh instance, while a static store returns the same
 * instance. The {@code [churn]} line printed at trial teardown reports how many writer
 * mutations landed during the run.
 *
 * <pre>./run.sh CandidateIndexChurnBenchmark -prof gc -f 1 -wi 3 -i 5 -t 1</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CandidateIndexChurnBenchmark {

    private static final String CHURN_ID = "g1-churn-expectation";

    @Param({"100", "1000", "15000"})
    public int n;

    @Param({"STATIC", "CHURN"})
    public String mode;

    @Param({"SCAN", "INDEX"})
    public String indexMode;

    private RequestMatchers requestMatchers;
    private HttpRequest probe;

    private volatile boolean churnRunning;
    private Thread churnThread;
    private final AtomicLong writerMutations = new AtomicLong();
    // Baseline of the STORE-SIDE modification counter, sampled at the end of setup. The delta at
    // teardown is the number of mutations the STORE itself observed during the trial — a stronger
    // check than writerMutations (which counts loop iterations, so it would still climb if
    // clear(id) had silently stopped removing anything).
    private long storeModificationsAtSetup;

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
        // SCAN: threshold above the largest n so the index never engages (the reader rebuilds
        // only the sorted list under churn). INDEX: threshold 2 so the index engages for every n
        // (the reader rebuilds the sorted list AND the index). Set on the instance, not a JVM
        // property, so it is deterministic across forks (see CandidateIndexBenchmark).
        requestMatchers.withCandidateIndexThreshold("SCAN".equals(indexMode) ? Integer.MAX_VALUE : 2);

        for (int i = 0; i < n; i++) {
            requestMatchers.add(
                new Expectation(request().withMethod("GET").withPath("/exact/path-" + i))
                    .thenRespond(response().withBody("e" + i)),
                API
            );
        }
        // A HIT in the middle of the insertion/priority order — representative of a real match,
        // not the full-miss worst case. The rebuild (the subject here) happens BEFORE the scan,
        // so the HIT/MISS choice does not change whether a rebuild occurs.
        probe = request().withMethod("GET").withPath("/exact/path-" + (n / 2));
        if (requestMatchers.firstMatchingExpectation(probe) == null) {
            throw new IllegalStateException("probe did not match — benchmark misconfigured");
        }

        storeModificationsAtSetup = storeModifications();

        if ("CHURN".equals(mode)) {
            // The churn expectation is a bucketable literal on its OWN (method,path), so in INDEX
            // mode it sits in a bucket the probe never reads — the reader never evaluates it, and
            // remove+add (never in-place update) means a reader can never observe it mid-mutation.
            requestMatchers.add(newChurnExpectation(), API);
            churnRunning = true;
            churnThread = new Thread(() -> {
                while (churnRunning) {
                    // clear(id) removes the matcher (invalidates the sorted cache, bumps the
                    // modification counter); add re-inserts it (same again). One writer only, so the CPQ
                    // single-writer contract holds; readers race only on the read/rebuild path.
                    requestMatchers.clear(ExpectationId.expectationId(CHURN_ID), "g1-churn");
                    requestMatchers.add(newChurnExpectation(), API);
                    writerMutations.addAndGet(2);
                }
            }, "g1-churn-writer");
            churnThread.setDaemon(true);
            churnThread.start();
        }
    }

    private static Expectation newChurnExpectation() {
        return new Expectation(request().withMethod("GET").withPath("/churn/only"))
            .thenRespond(response().withBody("c"))
            .withId(CHURN_ID);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
        // Sampled BEFORE the writer is stopped, so it sees the live churn. STATIC must report 0; a
        // CHURN arm reporting 0 means the cache stopped being invalidated and every number in it is
        // worthless. The sampler yields to the writer up to the deadline so a starved writer cannot
        // read 0 spuriously; a healthy arm exits on the first observed rebuild.
        int sortedListRebuildsObserved = sampleSortedListRebuilds(TimeUnit.MILLISECONDS.toNanos(500));
        long storeModificationsDuringTrial = storeModifications() - storeModificationsAtSetup;
        // Also sampled while the writer is still running: the measured call must still RETURN the
        // match. An index that silently started returning an empty candidate set would look fast
        // and allocate little — indistinguishable from "fixed" — so assert the subject is real.
        boolean stillMatchesUnderChurn = requestMatchers.firstMatchingExpectation(probe) != null;

        churnRunning = false;
        if (churnThread != null) {
            churnThread.join(2000);
        }
        // Reported so the write-up can state how many invalidations landed during the trial —
        // a CHURN trial with zero writer mutations would be a silent false negative.
        System.out.println("[churn] mode=" + mode + " indexMode=" + indexMode + " n=" + n
            + " writerMutations=" + writerMutations.get()
            + " storeModifications=" + storeModificationsDuringTrial
            + " sortedListRebuildsObserved=" + sortedListRebuildsObserved
            + " stillMatchesUnderChurn=" + stillMatchesUnderChurn);

        // FAIL-CLOSED here, the only place that can see whether CHURN churned: a dying daemon writer
        // is silent, degrading CHURN to STATIC and landing the ratio near 1.0, under the CI gate's 1.5
        // floor, so the build would go green having measured nothing. A @TearDown exception exits JMH
        // non-zero, which reds the step. STATIC is exempt; -1 (hook unavailable) fails via `<= 0`. The
        // checks are OR-combined on purpose: the rebuild sample catches mutations that still count
        // but no longer invalidate the cache, which the store-modification count cannot see.
        if ("CHURN".equals(mode)
            && (storeModificationsDuringTrial <= 0 || sortedListRebuildsObserved <= 0 || !stillMatchesUnderChurn)) {
            throw new IllegalStateException("CHURN arm did not actually churn (n=" + n
                + " indexMode=" + indexMode + "): storeModifications=" + storeModificationsDuringTrial
                + " sortedListRebuildsObserved=" + sortedListRebuildsObserved
                + " stillMatchesUnderChurn=" + stillMatchesUnderChurn
                + " — the background writer thread died OR the sorted-snapshot cache stopped being"
                + " invalidated on mutation (the G1 regression shape), silently degrading CHURN to a"
                + " STATIC measurement. Refusing to emit a green-looking ratio from a run that did"
                + " not churn.");
        }
    }

    /**
     * Reads {@code RequestMatchers.matchersModificationCountForTesting()} — the counter the store
     * bumps on EVERY structural mutation. Package-private in {@code org.mockserver.mock}, so
     * reached reflectively. Returns -1 if unavailable (reported, never silently treated as 0).
     */
    private long storeModifications() {
        try {
            Method method = RequestMatchers.class.getDeclaredMethod("matchersModificationCountForTesting");
            method.setAccessible(true);
            return (Long) method.invoke(requestMatchers);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Samples whether the sorted-snapshot cache is being invalidated (rebuilt) under the live
     * writer, ROBUSTLY against CI scheduling noise. Repeatedly reads {@code toSortedList()} and
     * counts how many reads returned a different LIST INSTANCE from the previous one — the
     * identity check that proves a rebuild happened rather than a cache hit — {@code Thread.yield()}ing
     * between reads so the SINGLE daemon writer is actually scheduled, and stopping at the FIRST
     * observed rebuild or when {@code maxNanos} expires.
     *
     * <p>WHY yield and a deadline, not a tight fixed-count loop: a back-to-back loop can starve the
     * writer on a busy CI agent and read ZERO while the arm churns heavily, a false RED. Do not
     * instead ignore a zero whenever mutations are counting: this sample is the only detector for
     * mutations that no longer invalidate the cache.
     *
     * <p>Returns 0 if no rebuild was observed within the deadline, the observed rebuild count
     * (>=1) otherwise, or -1 if the backing queue cannot be reached (reported, never silently 0).
     */
    @SuppressWarnings("unchecked")
    private int sampleSortedListRebuilds(long maxNanos) {
        try {
            Field field = RequestMatchers.class.getDeclaredField("httpRequestMatchers");
            field.setAccessible(true);
            Object queue = field.get(requestMatchers);
            Method toSortedList = queue.getClass().getMethod("toSortedList");
            List<?> previous = (List<?>) toSortedList.invoke(queue);
            int rebuilds = 0;
            long deadline = System.nanoTime() + maxNanos;
            while (rebuilds == 0 && System.nanoTime() < deadline) {
                // Hand the CPU to the single daemon writer so it can invalidate the cache between
                // our reads — a starved writer is the false-RED race this sampler exists to remove.
                Thread.yield();
                List<?> current = (List<?>) toSortedList.invoke(queue);
                if (current != previous) {
                    rebuilds++;
                }
                previous = current;
            }
            return rebuilds;
        } catch (Exception e) {
            return -1;
        }
    }

    @Benchmark
    public Expectation match() {
        return requestMatchers.firstMatchingExpectation(probe);
    }
}
