import org.mockserver.integration.ClientAndServer;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SUITE-LEVEL decomposition: the cost a JUnit suite actually
 * pays when it creates a MockServer per test method or per test class, IN ONE test JVM — the
 * {@code MockServerExtension} / {@code MockServerRule} profile. The parallel-instance profile already established that
 * per-instance <em>start</em> does not degrade with N (cold first launch dominates; warm launches
 * are flat), and the dominant per-method cost turned out to be per-instance
 * {@code stop()}, which no committed harness measured. This harness measures both ends of every
 * instance's life — construction-to-ready AND stop — for the two suite shapes, so each candidate
 * saving is accepted or rejected against a measured number rather than a plausible mechanism.
 *
 * <h2>WHAT ONE MEASUREMENT IS OF (be exact — this is what makes the harness correct)</h2>
 *
 * <p>Three timed regions, each a wall-clock interval around a single operation on ONE instance:
 * <ul>
 *   <li><b>call</b> — {@code System.nanoTime()} bracketing {@link ClientAndServer#startClientAndServer(Integer...)}
 *       ONLY. This is the API's own notion of "started": the constructor has run, the pipeline is
 *       built, and the listener socket is bound (port bind happens INSIDE this call and is not
 *       separately observable in-JVM, because the factory method blocks until the server is up — so
 *       "port bind" is reported as a component of {@code call}, not as its own number).</li>
 *   <li><b>ready</b> — from the same {@code t0} just BEFORE {@code startClientAndServer(...)} to the
 *       first {@code PUT /mockserver/status} that returns 200. This is the ONLY honest definition of
 *       ready in this repo: MockServer accepts a TCP connection and then RESETS it during
 *       initialisation, so a listening port is NOT readiness (documented trap — see
 *       {@code docs/code/startup-performance.md} and {@code InJvmStartupBench.java}). Anything that
 *       waits on the port alone measures the wrong instant, so this harness never does.</li>
 *   <li><b>stop</b> — {@code System.nanoTime()} bracketing {@code server.stop()} ONLY. A per-method
 *       instance is torn down per method, so a slow shutdown is suite time just as surely as a slow
 *       start. This was the dominant cost before the {@code LifeCycle} quiet-period fix;
 *       this region is exactly what proves the fix landed and stays landed.</li>
 * </ul>
 *
 * <h2>FIRST vs SUBSEQUENT (never conflated)</h2>
 * The very first instance in a JVM pays one-time class loading and static init; every later instance
 * does not. Conflating them is the obvious way to get a meaningless average, so this harness measures
 * the cold first instance <b>on its own, before anything else</b> (phase COLD), reports it as its own
 * figure, and every subsequent phase runs only warm instances whose classes are already loaded.
 *
 * <h2>SEQUENTIAL vs CONCURRENT (both, because the profile is parallel tests)</h2>
 * <ul>
 *   <li><b>SEQUENTIAL</b> (phase SEQ) — the per-method shape with serial test execution: one instance
 *       alive at a time, {@code start -> ready -> stop} repeated {@code --seq} times. This isolates
 *       the pure per-method start+stop cost with no contention.</li>
 *   <li><b>CONCURRENT</b> (phase CONC) — the per-method shape with PARALLEL test execution, which is
 *       the stated profile: {@code --conc} instances started concurrently (each on its own thread and
 *       port), all alive at once, then stopped concurrently. This is where "how it degrades as
 *       instances accumulate within one JVM" is actually measured, and it reports the peak live thread
 *       count while all are up.</li>
 * </ul>
 *
 * <h2>PER-CLASS shape</h2>
 * Per-class is start-once/stop-once for the whole class, so its primitive IS the cold first instance
 * plus (optionally) one warm cycle; the suite projection below multiplies the measured primitives to
 * give the per-method and per-class suite totals for a stated (classes x methods) shape without having
 * to run a whole synthetic suite.
 *
 * <h2>NO LEAK BETWEEN MEASUREMENTS (this harness must not manufacture its own degradation)</h2>
 * {@code DashboardWebSocketHandlerTest} was found leaking ~363 threads per class run because instances
 * were never stopped; a harness that creates many instances and does not stop them would measure its
 * own leak as "degradation with N". So EVERY instance created here is stopped (in {@code finally}),
 * and between phases the harness forces GC, lets the event loops unwind, and re-reads the live thread
 * count via {@link java.lang.management.ThreadMXBean}. The residual thread delta over baseline is
 * emitted in the JSON as {@code residualThreads} — evidence that the curve reported is MockServer's,
 * not the harness's.
 *
 * <p>Usage: {@code java -Xmx2g -cp <jar-with-dependencies> InJvmSuiteBench.java
 * [--seq 16] [--conc 16] [--basePort 27000] [--devMode false]
 * [--projectClasses 100 --projectMethods 10] [--label suite]}
 *
 * <p><b>This build establishes correctness only.</b> Take headline numbers on a quiet machine; a
 * figure taken while other work contends for cores is worse than no figure.
 */
public class InJvmSuiteBench {

    static final String JVM_PID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

    public static void main(String[] args) throws Exception {
        int seq = intOpt(args, "--seq", 16);
        int conc = intOpt(args, "--conc", 16);
        int basePort = intOpt(args, "--basePort", 27000);
        boolean devMode = Boolean.parseBoolean(strOpt(args, "--devMode", "false"));
        int projectClasses = intOpt(args, "--projectClasses", 100);
        int projectMethods = intOpt(args, "--projectMethods", 10);
        String label = strOpt(args, "--label", devMode ? "suite-dev" : "suite");

        // Match the pipeline's quiet launch; set BEFORE any MockServer class initialises.
        System.setProperty("mockserver.logLevel", "WARN");
        System.setProperty("mockserver.disableSystemOut", "true");
        if (devMode) {
            System.setProperty("mockserver.devMode", "true");
        }

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build();

        int baseThreads = liveThreadCount();
        System.err.printf(Locale.ROOT,
            "== %s: suite decomposition  (pid %s, availableProcessors=%d, Xmx=%d MB, devMode=%b)%n"
                + "   baseline JVM threads before any instance: %d%n",
            label, JVM_PID, Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory() / (1024 * 1024), devMode, baseThreads);

        int port = basePort;

        // ---- PHASE COLD: the one-time first instance (pays class load + static init) ----
        System.err.printf(Locale.ROOT, "%n-- COLD first instance (paid once per test JVM) --%n");
        double[] cold = oneCycle(http, port++);   // {callMs, readyMs, stopMs}
        System.err.printf(Locale.ROOT,
            "   cold: call %6.1f ms | ready %6.1f ms | stop %6.1f ms%n", cold[0], cold[1], cold[2]);

        // SETTLED baseline: threads after the cold instance is created, torn down and fully reaped.
        // Creating+using one instance forces the shared JDK HttpClient and every lazily-started JVM
        // housekeeping daemon into existence, so this — not the too-early pre-instance count — is the
        // true steady-state reference. A non-zero residual over THIS after later phases is a real leak.
        int settledBase = settleThreads();

        // ---- PHASE SEQ: per-method, serial execution (one instance alive at a time, all warm) ----
        System.err.printf(Locale.ROOT, "%n-- SEQUENTIAL per-method x%d (warm; one alive at a time) --%n", seq);
        List<Double> seqReady = new ArrayList<>();
        List<Double> seqStop = new ArrayList<>();
        long seqWall0 = System.nanoTime();
        for (int i = 0; i < seq; i++) {
            double[] c = oneCycle(http, port++);
            seqReady.add(c[1]);
            seqStop.add(c[2]);
        }
        double seqWallMs = (System.nanoTime() - seqWall0) / 1e6;
        System.err.printf(Locale.ROOT,
            "   warm ready ms  min %.1f / median %.1f / max %.1f%n"
                + "   warm stop  ms  min %.1f / median %.1f / max %.1f   (wall %.1f ms for %d cycles)%n",
            min(seqReady), median(seqReady), max(seqReady),
            min(seqStop), median(seqStop), max(seqStop), seqWallMs, seq);

        int afterSeqThreads = settleThreads();

        // ---- PHASE CONC: per-method, PARALLEL execution (all warm, all alive at once) ----
        System.err.printf(Locale.ROOT, "%n-- CONCURRENT per-method x%d (warm; all alive at once) --%n", conc);
        ConcResult cr = concurrentBatch(http, port, conc, settledBase);
        port += conc;
        System.err.printf(Locale.ROOT,
            "   ready ms  min %.1f / median %.1f / max %.1f%n"
                + "   stop  ms  min %.1f / median %.1f / max %.1f%n"
                + "   peak live threads while all %d up: %d (settledBase %d, +%d = %.1f/instance) | start-wall %.1f ms, stop-wall %.1f ms%n",
            min(cr.ready), median(cr.ready), max(cr.ready),
            min(cr.stop), median(cr.stop), max(cr.stop),
            conc, cr.peakThreads, settledBase, cr.peakThreads - settledBase,
            (cr.peakThreads - settledBase) / (double) conc, cr.startWallMs, cr.stopWallMs);

        int afterConcThreads = settleThreads();
        int residual = afterConcThreads - settledBase;
        int msLeak = mockserverLiveThreads(); // authoritative: MockServer-owned threads still alive
        System.err.printf(Locale.ROOT,
            "%n-- LEAK CHECK --%n"
                + "   MockServer-owned live threads after teardown+settle: %d  (MUST be 0 — this is the real leak signal)%n"
                + "   total threads: rawBase %d | settledBase %d -> afterSeq %d -> afterConc %d (residual %+d, all JDK HttpClient/JVM daemons — the harness's own probe pool, not MockServer)%n",
            msLeak, baseThreads, settledBase, afterSeqThreads, afterConcThreads, residual);
        if (msLeak != 0) {
            System.err.printf(Locale.ROOT,
                "   WARNING: %d MockServer-owned thread(s) survived teardown — a real instance leak; later figures are suspect.%n", msLeak);
        }

        // ---- SUITE PROJECTION from measured primitives (no synthetic suite needed) ----
        // Per-method suite: every test JVM pays cold once, then (classes*methods - 1) warm start+stop.
        // Per-class suite:  every test JVM pays cold once, then (classes - 1) warm start+stop; the
        //                   methods within a class reuse the one instance and pay neither.
        double warmCycle = median(seqReady) + median(seqStop);      // one warm start+stop, serial
        double coldCycle = cold[1] + cold[2];
        long perMethodInstances = (long) projectClasses * projectMethods;
        double perMethodSuiteMs = coldCycle + Math.max(0, perMethodInstances - 1) * warmCycle;
        double perClassSuiteMs = coldCycle + Math.max(0, projectClasses - 1) * warmCycle;
        System.err.printf(Locale.ROOT,
            "%n-- PROJECTION for %d classes x %d methods (in-JVM start+stop only; excludes JVM fork & test-body time) --%n"
                + "   per-method: %.0f ms   per-class: %.0f ms   (warm cycle %.1f ms, cold cycle %.1f ms)%n",
            projectClasses, projectMethods, perMethodSuiteMs, perClassSuiteMs, warmCycle, coldCycle);

        // ---- machine-readable line (same BENCH_JSON convention as the other harnesses) ----
        StringBuilder json = new StringBuilder();
        json.append("{\"label\":\"").append(label).append("\",\"shape\":\"suite-decomposition\"")
            .append(",\"devMode\":").append(devMode)
            .append(",\"availableProcessors\":").append(Runtime.getRuntime().availableProcessors())
            .append(",\"xmxMb\":").append(Runtime.getRuntime().maxMemory() / (1024 * 1024))
            .append(",\"cold\":{\"callMs\":").append(f1(cold[0])).append(",\"readyMs\":").append(f1(cold[1]))
            .append(",\"stopMs\":").append(f1(cold[2])).append("}")
            .append(",\"sequential\":{\"n\":").append(seq)
            .append(",\"warmReadyMinMs\":").append(f1(min(seqReady))).append(",\"warmReadyMedianMs\":").append(f1(median(seqReady)))
            .append(",\"warmReadyMaxMs\":").append(f1(max(seqReady)))
            .append(",\"warmStopMinMs\":").append(f1(min(seqStop))).append(",\"warmStopMedianMs\":").append(f1(median(seqStop)))
            .append(",\"warmStopMaxMs\":").append(f1(max(seqStop))).append(",\"wallMs\":").append(f1(seqWallMs)).append("}")
            .append(",\"concurrent\":{\"n\":").append(conc)
            .append(",\"readyMinMs\":").append(f1(min(cr.ready))).append(",\"readyMedianMs\":").append(f1(median(cr.ready)))
            .append(",\"readyMaxMs\":").append(f1(max(cr.ready)))
            .append(",\"stopMinMs\":").append(f1(min(cr.stop))).append(",\"stopMedianMs\":").append(f1(median(cr.stop)))
            .append(",\"stopMaxMs\":").append(f1(max(cr.stop)))
            .append(",\"startWallMs\":").append(f1(cr.startWallMs)).append(",\"stopWallMs\":").append(f1(cr.stopWallMs))
            .append(",\"peakThreads\":").append(cr.peakThreads)
            .append(",\"threadsPerInstance\":").append(f1((cr.peakThreads - settledBase) / (double) conc)).append("}")
            .append(",\"leak\":{\"rawBaseThreads\":").append(baseThreads)
            .append(",\"settledBaseThreads\":").append(settledBase)
            .append(",\"afterSeqThreads\":").append(afterSeqThreads)
            .append(",\"afterConcThreads\":").append(afterConcThreads)
            .append(",\"residualThreads\":").append(residual)
            .append(",\"mockserverOwnedThreadsAfterTeardown\":").append(msLeak).append("}")
            .append(",\"projection\":{\"classes\":").append(projectClasses).append(",\"methodsPerClass\":").append(projectMethods)
            .append(",\"perMethodSuiteMs\":").append(f1(perMethodSuiteMs))
            .append(",\"perClassSuiteMs\":").append(f1(perClassSuiteMs)).append("}}");
        System.out.println("BENCH_JSON " + json);
    }

    /** One full instance life on one port: start, wait for /mockserver/status 200, stop. */
    static double[] oneCycle(HttpClient http, int port) throws Exception {
        ClientAndServer server = null;
        try {
            long t0 = System.nanoTime();
            server = ClientAndServer.startClientAndServer(port);
            double callMs = (System.nanoTime() - t0) / 1e6;
            double readyMs = awaitReady(http, port, t0);
            long s0 = System.nanoTime();
            server.stop();
            double stopMs = (System.nanoTime() - s0) / 1e6;
            server = null; // stopped cleanly; nothing for finally to reclaim
            return new double[]{callMs, readyMs, stopMs};
        } finally {
            // Never leave an instance running — a leaked instance would load the JVM for every
            // later measurement and turn the harness's own leak into a fake degradation curve.
            if (server != null) {
                try { server.stop(); } catch (Exception ignore) { }
            }
        }
    }

    static final class ConcResult {
        final List<Double> ready;
        final List<Double> stop;
        final double startWallMs;
        final double stopWallMs;
        final int peakThreads;
        ConcResult(List<Double> ready, List<Double> stop, double startWallMs, double stopWallMs, int peakThreads) {
            this.ready = ready; this.stop = stop;
            this.startWallMs = startWallMs; this.stopWallMs = stopWallMs; this.peakThreads = peakThreads;
        }
    }

    /**
     * Start {@code n} warm instances concurrently (each own thread + port), record each one's ready
     * time, sample the peak live thread count while all are up, then stop all concurrently and record
     * each stop time. Every instance is stopped even if a start failed.
     */
    static ConcResult concurrentBatch(HttpClient http, int basePort, int n, int baseThreads) throws Exception {
        final ClientAndServer[] servers = new ClientAndServer[n];
        final double[] readyMs = new double[n];
        final double[] stopMs = new double[n];
        final CountDownLatch started = new CountDownLatch(n);
        final AtomicInteger failures = new AtomicInteger();

        long start0 = System.nanoTime();
        Thread[] up = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final int p = basePort + i;
            up[i] = new Thread(() -> {
                try {
                    long t0 = System.nanoTime();
                    servers[idx] = ClientAndServer.startClientAndServer(p);
                    readyMs[idx] = awaitReady(http, p, t0);
                } catch (Exception e) {
                    readyMs[idx] = Double.NaN;
                    failures.incrementAndGet();
                    System.err.println("   concurrent start failed on instance " + idx + ": " + e);
                } finally {
                    started.countDown();
                }
            }, "conc-up-" + i);
            up[i].start();
        }
        // Sample peak threads while the batch comes up and all are momentarily alive.
        int peak = baseThreads;
        while (started.getCount() > 0) {
            peak = Math.max(peak, liveThreadCount());
            Thread.sleep(2);
        }
        for (Thread t : up) t.join();
        peak = Math.max(peak, liveThreadCount()); // all up now
        double startWallMs = (System.nanoTime() - start0) / 1e6;

        long stop0 = System.nanoTime();
        Thread[] down = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            down[i] = new Thread(() -> {
                if (servers[idx] == null) { stopMs[idx] = Double.NaN; return; }
                long s0 = System.nanoTime();
                try { servers[idx].stop(); } catch (Exception ignore) { }
                stopMs[idx] = (System.nanoTime() - s0) / 1e6;
            }, "conc-down-" + i);
            down[i].start();
        }
        for (Thread t : down) t.join();
        double stopWallMs = (System.nanoTime() - stop0) / 1e6;

        if (failures.get() > 0) {
            throw new IllegalStateException(failures.get() + " concurrent instance(s) never started");
        }
        return new ConcResult(toList(readyMs), toList(stopMs), startWallMs, stopWallMs, peak);
    }

    /** Poll {@code PUT /mockserver/status} until 200; return ms from {@code t0}. Throws if never ready. */
    static double awaitReady(HttpClient http, int port, long t0) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (status200(http, port)) {
                return (System.nanoTime() - t0) / 1e6;
            }
            Thread.sleep(1);
        }
        throw new IllegalStateException("instance on port " + port + " never became ready (status 200)");
    }

    static boolean status200(HttpClient http, int port) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mockserver/status"))
                .timeout(Duration.ofMillis(250))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Force GC, let event loops unwind, and return the live thread count once it is genuinely stable.
     * Netty's shutdown is asynchronous and reaps in waves — a measured probe saw 43 threads fall to a
     * flat 13 over a full ~3-4 s, in steps with roughly one-second pauses between them. A short
     * stability window therefore returns mid-descent on a between-wave pause and reports a phantom
     * leak (an earlier 750 ms window did exactly that). So this waits until the count holds EQUAL for
     * a continuous 2.5 s (ten consecutive 250 ms samples) — longer than the observed between-wave
     * pause — with a 15 s ceiling. What remains once truly stable is JDK {@code HttpClient} threads
     * and JVM housekeeping daemons, never MockServer/Netty threads (verified by histogram), so a
     * non-zero residual over the settled baseline would be a real MockServer leak.
     */
    static int settleThreads() throws Exception {
        System.gc();
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        int last = liveThreadCount();
        int stable = 0;
        while (System.nanoTime() < deadline) {
            Thread.sleep(250);
            int now = liveThreadCount();
            if (now == last) {
                if (++stable >= 10) break; // unchanged for a continuous 2.5 s
            } else {
                stable = 0;
                last = now;
            }
        }
        return last;
    }

    static int liveThreadCount() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }

    /**
     * Count live threads a MockServer instance owns — every one is named with the {@code MockServer-}
     * prefix (boss/worker event loops, the acceptor, the event log, the scheduler). This is the
     * AUTHORITATIVE leak signal: after teardown + settle it must be 0. The raw total thread count is
     * not, because it also includes the harness's own shared JDK {@code HttpClient} selector/worker
     * pool, which grows lazily under the concurrent phase and legitimately persists (it is the
     * measurement instrument, not MockServer). Matching by owner-name isolates MockServer's own leak.
     */
    static int mockserverLiveThreads() {
        int c = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName() != null && t.getName().startsWith("MockServer")) c++;
        }
        return c;
    }

    // ---- stats / formatting ----

    static List<Double> toList(double[] xs) {
        List<Double> r = new ArrayList<>(xs.length);
        for (double x : xs) r.add(x);
        return r;
    }

    static double pctile(List<Double> xs, double pct) {
        List<Double> s = new ArrayList<>();
        for (double x : xs) if (!Double.isNaN(x)) s.add(x);
        if (s.isEmpty()) return Double.NaN;
        Collections.sort(s);
        int idx = (int) Math.ceil(pct / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(idx, s.size() - 1)));
    }

    static double median(List<Double> xs) { return pctile(xs, 50); }
    static double min(List<Double> xs) {
        double m = Double.POSITIVE_INFINITY;
        for (double x : xs) if (!Double.isNaN(x)) m = Math.min(m, x);
        return Double.isInfinite(m) ? Double.NaN : m;
    }
    static double max(List<Double> xs) {
        double m = Double.NEGATIVE_INFINITY;
        for (double x : xs) if (!Double.isNaN(x)) m = Math.max(m, x);
        return Double.isInfinite(m) ? Double.NaN : m;
    }

    /** JSON number, or the literal null for NaN (a bare NaN token is invalid strict JSON). */
    static String f1(double v) { return Double.isNaN(v) ? "null" : String.format(Locale.ROOT, "%.1f", v); }

    // ---- args ----

    static int intOpt(String[] a, String k, int def) {
        String v = strOpt(a, k, null);
        return v == null ? def : Integer.parseInt(v);
    }

    static String strOpt(String[] a, String k, String def) {
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals(k)) return a[i + 1];
        return def;
    }
}
