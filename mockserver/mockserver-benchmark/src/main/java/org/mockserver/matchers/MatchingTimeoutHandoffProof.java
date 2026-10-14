package org.mockserver.matchers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mechanism proof (NOT a JMH benchmark) — a package-private verifier that
 * {@link MatchingTimeoutExecutor#callWithTimeout} really hands the match to the shared thread pool
 * on the default path and really bypasses it when the timeout is disabled. It lives in
 * {@code org.mockserver.matchers} (not {@code org.mockserver.benchmark}) precisely so it can read
 * the package-private {@link MatchingTimeoutExecutor#submittedTaskCount()} counter that the executor
 * documents as "exposed for tests"; the JMH benchmark reads that same counter through the
 * {@link #submittedTaskCount()} bridge below.
 *
 * <p>Two independent checks — a ratio between two arms that both take the same path would be
 * meaningless, so each arm is proven to take the path it claims:
 *
 * <ol>
 *   <li><b>Pool on/off the path.</b> A call with the default {@code timeoutMillis=5000} increments
 *       the submitted-task counter (the task went to the pool); a call with {@code timeoutMillis=0}
 *       does <em>not</em> (it ran inline on the calling thread via the {@code timeoutMillis <= 0}
 *       short-circuit at the top of {@code MatchingTimeoutExecutor.callWithTimeout}). This is the exact
 *       distinction the benchmark's POOL vs INLINE arms rely on.</li>
 *   <li><b>Saturation reachability.</b> The executor caps live evaluator threads at
 *       {@code max(64, availableProcessors*16)} and, once saturated, runs the excess submissions
 *       <em>inline</em> without incrementing the counter. Launching {@code cap/2} simultaneous
 *       blocking submissions leaves the counter matching the submission count exactly (nothing ran
 *       inline); launching {@code cap + headroom} simultaneous blocking submissions leaves the
 *       counter <em>below</em> the submission count (the shortfall ran inline). That shortfall is
 *       the only direct, exposed observation that the AbortPolicy inline fallback fired — there is
 *       no rejection counter. It quantifies the "effectively unreachable under realistic
 *       concurrency" claim: reachable, but only above the (generous) cap.</li>
 * </ol>
 *
 * <p>Run it standalone (prints {@code PROOF: PASS} / {@code PROOF: FAIL} and exits non-zero on
 * failure):
 * <pre>java -cp "target/classes:$(cat target/classpath.txt)" org.mockserver.matchers.MatchingTimeoutHandoffProof</pre>
 */
public final class MatchingTimeoutHandoffProof {

    private MatchingTimeoutHandoffProof() {
    }

    /**
     * Bridge so the JMH benchmark (in {@code org.mockserver.benchmark}) can read the executor's
     * package-private submitted-task counter to report, per trial, whether the pool was actually
     * engaged (POOL arm) or bypassed (INLINE arm).
     */
    public static long submittedTaskCount() {
        return MatchingTimeoutExecutor.submittedTaskCount();
    }

    public static void main(String[] args) throws Exception {
        boolean ok = true;
        ok &= provePoolOnAndOffPath();
        ok &= proveSaturationReachability();
        System.out.println(ok ? "PROOF: PASS" : "PROOF: FAIL");
        if (!ok) {
            System.exit(1);
        }
    }

    /**
     * Check 1: timeout=5000 submits to the pool; timeout=0 runs inline (no submission).
     */
    private static boolean provePoolOnAndOffPath() throws Exception {
        long before = MatchingTimeoutExecutor.submittedTaskCount();

        Boolean poolResult = MatchingTimeoutExecutor.callWithTimeout(() -> Boolean.TRUE, 5000L, Boolean.FALSE, null);
        long afterPool = MatchingTimeoutExecutor.submittedTaskCount();

        Boolean inlineResult = MatchingTimeoutExecutor.callWithTimeout(() -> Boolean.TRUE, 0L, Boolean.FALSE, null);
        long afterInline = MatchingTimeoutExecutor.submittedTaskCount();

        long poolDelta = afterPool - before;
        long inlineDelta = afterInline - afterPool;
        boolean pass = Boolean.TRUE.equals(poolResult)
            && Boolean.TRUE.equals(inlineResult)
            && poolDelta == 1
            && inlineDelta == 0;

        System.out.println("[g6:path] timeout=5000 submittedDelta=" + poolDelta + " (expected 1, pool engaged)"
            + " | timeout=0 submittedDelta=" + inlineDelta + " (expected 0, ran inline) -> "
            + (pass ? "PASS" : "FAIL"));
        return pass;
    }

    /**
     * Check 2: the inline-on-saturation fallback is reachable above the cap and unreached below it.
     */
    private static boolean proveSaturationReachability() throws Exception {
        int cap = Math.max(64, Runtime.getRuntime().availableProcessors() * 16);

        // Below the cap: every submission must get a pool thread — nothing runs inline.
        SaturationResult below = runConcurrentBlockingSubmissions(cap / 2);
        boolean belowPass = below.ranInline == 0;
        System.out.println("[g6:sat] submitters=" + below.submitters + " cap=" + cap
            + " submittedToPool=" + below.submittedToPool + " ranInline=" + below.ranInline
            + " (expected 0 inline below cap) -> " + (belowPass ? "PASS" : "FAIL"));

        // Above the cap: the excess must be rejected and run inline (the AbortPolicy fallback).
        int aboveCount = cap + Math.max(64, cap / 4);
        SaturationResult above = runConcurrentBlockingSubmissions(aboveCount);
        boolean abovePass = above.ranInline > 0;
        System.out.println("[g6:sat] submitters=" + above.submitters + " cap=" + cap
            + " submittedToPool=" + above.submittedToPool + " ranInline=" + above.ranInline
            + " (expected >0 inline above cap) -> " + (abovePass ? "PASS" : "FAIL"));

        return belowPass && abovePass;
    }

    private static final class SaturationResult {
        final int submitters;
        final long submittedToPool;
        final long ranInline;

        SaturationResult(int submitters, long submittedToPool) {
            this.submitters = submitters;
            this.submittedToPool = submittedToPool;
            this.ranInline = submitters - submittedToPool;
        }
    }

    /**
     * Launch {@code submitters} threads that all call {@code callWithTimeout} simultaneously with a
     * task that blocks until every task has started, then measure how many submissions the pool
     * accepted (via the exposed counter). A submission the pool rejects runs the same task inline on
     * the submitter thread — so all {@code submitters} tasks start regardless, but only the accepted
     * ones increment the counter. {@code submitters - accepted} therefore ran inline.
     */
    private static SaturationResult runConcurrentBlockingSubmissions(int submitters) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch allStarted = new CountDownLatch(submitters);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        long before = MatchingTimeoutExecutor.submittedTaskCount();

        Thread[] threads = new Thread[submitters];
        for (int i = 0; i < submitters; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startGate.await();
                    // Large timeout so future.get never fires during the probe; the task blocks
                    // (whether on a pool thread or inline on this thread) until released.
                    MatchingTimeoutExecutor.callWithTimeout(() -> {
                        allStarted.countDown();
                        release.await();
                        return Boolean.TRUE;
                    }, 60_000L, Boolean.FALSE, null);
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }, "g6-sat-submitter-" + i);
            threads[i].setDaemon(true);
            threads[i].start();
        }

        startGate.countDown();
        // Wait until every task (pool + inline) has entered its body, so all in-flight work is
        // simultaneously live and the counter reflects the peak accepted-by-pool count.
        if (!allStarted.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("g6 saturation probe: not all " + submitters + " tasks started");
        }
        long accepted = MatchingTimeoutExecutor.submittedTaskCount() - before;
        release.countDown();
        for (Thread t : threads) {
            t.join(TimeUnit.SECONDS.toMillis(30));
        }
        if (failures.get() != 0) {
            throw new IllegalStateException("g6 saturation probe: " + failures.get() + " submitter(s) threw");
        }
        return new SaturationResult(submitters, accepted);
    }
}
