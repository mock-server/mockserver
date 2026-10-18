import org.mockserver.configuration.ConfigurationProperties;
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
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * IN-JVM shape: N {@link ClientAndServer} instances started
 * in ONE test JVM — the {@code MockServerExtension} case a real suite hits. Unlike N containers,
 * this JVM has no cgroup, so every instance sizes its pools off the whole machine's
 * {@code availableProcessors()} and its stores off the live free heap at the moment of construction.
 *
 * <p>For each launch order the harness records, per instance:
 * <ul>
 *   <li>the {@code maxLogEntries} / {@code maxExpectations} the store bakes in — captured from
 *       {@link ConfigurationProperties} BEFORE the start and again AFTER it returns, bracketing the
 *       construction moment so the recorded capacity is provably the one the store used (the two
 *       agree when no GC intervened) — this is the store-sizing ORDER DEPENDENCE measurement;</li>
 *   <li>the free-heap figures that derive it ({@code heapAvailableInKB}, used heap);</li>
 *   <li>startup ready time (launch to first {@code PUT /mockserver/status} == 200 — a listening port
 *       is NOT readiness);</li>
 *   <li>the LIVE JVM thread count after the instance is up (counted, never computed).</li>
 * </ul>
 *
 * <p>After all N are up it records aggregate RSS (via {@code ps}), the live thread count and a
 * name-prefix histogram (proving how many of the theoretical pool threads are actually alive), the
 * JVM's held TCP socket count (via {@code lsof} — the ephemeral-port proxy), and used/committed heap.
 * Then it drives a light per-instance load (a warm-up burst discarded, then a measured burst per
 * instance, run concurrently across instances) and reports the DISTRIBUTION of per-instance p95, not
 * a mean.
 *
 * <p>Usage: {@code java -cp <jar-with-deps> InJvmParallelBench.java --counts 1,4,8,16,32
 * [--basePort 26000] [--devMode true] [--loadWarmup 50] [--loadMeasured 300] [--label injvm]}
 */
public class InJvmParallelBench {

    static final String JVM_PID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

    /** Heap held for the whole run when --preconsumeHeapMb is set (never GC'd). */
    static Object heapBallast;

    public static void main(String[] args) throws Exception {
        int[] counts = intList(strOpt(args, "--counts", "1,4,8,16,32"));
        int basePort = intOpt(args, "--basePort", 26000);
        boolean devMode = Boolean.parseBoolean(strOpt(args, "--devMode", "false"));
        int loadWarmup = intOpt(args, "--loadWarmup", 50);
        int loadMeasured = intOpt(args, "--loadMeasured", 300);
        int preconsumeHeapMb = intOpt(args, "--preconsumeHeapMb", 0);
        String label = strOpt(args, "--label", devMode ? "injvm-dev" : "injvm");

        System.setProperty("mockserver.logLevel", "WARN");
        System.setProperty("mockserver.disableSystemOut", "true");
        if (devMode) {
            System.setProperty("mockserver.devMode", "true");
        }

        // Reproduce the store-sizing FREEZE swing: hold this much heap BEFORE the first instance
        // (and thus before the first maxLogEntries()/maxExpectations() read), so the JVM-wide
        // property cache freezes a smaller heap-based default. maxLog/maxExp then stay at that
        // frozen value for every instance regardless of later heap — visible as maxLogFirst falling
        // as --preconsumeHeapMb rises across separate runs. Not used by devMode (fixed 1000/1000).
        if (preconsumeHeapMb > 0) {
            byte[][] hog = new byte[preconsumeHeapMb][];
            for (int i = 0; i < preconsumeHeapMb; i++) hog[i] = new byte[1024 * 1024];
            heapBallast = hog;
            System.err.printf(Locale.ROOT, "   pre-consumed %d MB of heap before first store read%n", preconsumeHeapMb);
        }

        System.err.printf(Locale.ROOT,
            "== %s: in-JVM parallel, counts=%s devMode=%b  (pid %s, availableProcessors=%d, Xmx=%d MB)%n",
            label, java.util.Arrays.toString(counts), devMode, JVM_PID,
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory() / (1024 * 1024));

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build();

        StringBuilder json = new StringBuilder();
        json.append("{\"label\":\"").append(label).append("\",\"devMode\":").append(devMode)
            .append(",\"availableProcessors\":").append(Runtime.getRuntime().availableProcessors())
            .append(",\"xmxMb\":").append(Runtime.getRuntime().maxMemory() / (1024 * 1024))
            .append(",\"runs\":[");

        int baseThreads = liveThreadCount();
        System.err.printf(Locale.ROOT, "   baseline JVM threads before any instance: %d%n", baseThreads);

        int port = basePort;
        boolean firstRun = true;
        for (int n : counts) {
            if (!firstRun) json.append(",");
            firstRun = false;
            String r = runOne(http, label, n, port, baseThreads, loadWarmup, loadMeasured);
            json.append(r);
            port += n + 5; // disjoint port band per N
            System.gc();
            Thread.sleep(400);
        }
        json.append("]}");
        System.out.println("BENCH_JSON " + json);
    }

    /** One N: launch N instances, measure footprint, drive light load, tear down. */
    static String runOne(HttpClient http, String label, int n, int basePort, int baseThreads,
                         int loadWarmup, int loadMeasured) throws Exception {
        System.err.printf(Locale.ROOT, "%n-- N=%d --------------------------------------------------%n", n);
        List<ClientAndServer> servers = new ArrayList<>();
        List<Integer> ports = new ArrayList<>();
        List<int[]> storeSizes = new ArrayList<>();   // [idx, maxLogPre, maxLogPost, maxExpPre, maxExpPost, heapAvailKb]
        List<Double> startReady = new ArrayList<>();
        List<Integer> threadsAfter = new ArrayList<>();
        double coldReadyMs = Double.NaN;

        for (int i = 0; i < n; i++) {
            int p = basePort + i;
            int maxLogPre = ConfigurationProperties.maxLogEntries();
            int maxExpPre = ConfigurationProperties.maxExpectations();
            long heapAvailKb = ConfigurationProperties.heapAvailableInKB();

            long t0 = System.nanoTime();
            ClientAndServer server = ClientAndServer.startClientAndServer(p);
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            double readyMs = Double.NaN;
            while (System.nanoTime() < deadline) {
                if (status200(http, p)) { readyMs = (System.nanoTime() - t0) / 1e6; break; }
                Thread.sleep(1);
            }
            if (Double.isNaN(readyMs)) throw new IllegalStateException("instance " + i + " never ready");
            int maxLogPost = ConfigurationProperties.maxLogEntries();
            int maxExpPost = ConfigurationProperties.maxExpectations();

            servers.add(server);
            ports.add(p);
            storeSizes.add(new int[]{i, maxLogPre, maxLogPost, maxExpPre, maxExpPost, (int) Math.min(heapAvailKb, Integer.MAX_VALUE)});
            startReady.add(readyMs);
            int liveNow = liveThreadCount();
            threadsAfter.add(liveNow);
            if (i == 0) coldReadyMs = readyMs;

            if (i == 0 || i == n - 1 || n <= 4) {
                System.err.printf(Locale.ROOT,
                    "   inst %2d: ready %6.1f ms | maxLog %6d->%6d maxExp %6d->%6d | heapAvail %8d KB | liveThreads %d%n",
                    i, readyMs, maxLogPre, maxLogPost, maxExpPre, maxExpPost, heapAvailKb, liveNow);
            }
        }

        // ---- footprint with all N up ----
        System.gc();
        Thread.sleep(300);
        int totalThreads = liveThreadCount();
        Map<String, Integer> hist = threadHistogram();
        long rssKb = rssKb();
        int sockets = tcpSocketCount();
        Runtime rt = Runtime.getRuntime();
        long usedHeapMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long committedHeapMb = rt.totalMemory() / (1024 * 1024);

        System.err.printf(Locale.ROOT,
            "   ALL %d UP: liveThreads %d (base %d, +%d for %d instances = %.1f/instance) | RSS %d MB | TCP sockets %d | heapUsed %d MB%n",
            n, totalThreads, baseThreads, totalThreads - baseThreads, n,
            (totalThreads - baseThreads) / (double) n, rssKb / 1024, sockets, usedHeapMb);
        System.err.printf(Locale.ROOT, "   thread histogram: %s%n", hist);

        // ---- per-instance light load, run concurrently across instances ----
        double[][] perInstancePct = lightLoad(servers, ports, loadWarmup, loadMeasured);
        // perInstancePct[i] = {p50,p95,p99,max} for instance i
        List<Double> p95s = new ArrayList<>();
        List<Double> p99s = new ArrayList<>();
        for (double[] pc : perInstancePct) { p95s.add(pc[1]); p99s.add(pc[2]); }
        System.err.printf(Locale.ROOT,
            "   light load per-instance p95 across %d instances: min %.2f / median %.2f / max %.2f ms  (p99 median %.2f, max %.2f)%n",
            n, min(p95s), median(p95s), max(p95s), median(p99s), max(p99s));

        // ---- teardown ----
        for (ClientAndServer s : servers) {
            try { s.stop(); } catch (Exception ignore) { }
        }
        Thread.sleep(200 + 30L * n);

        // ---- serialise this N ----
        List<Double> warmReady = new ArrayList<>(startReady.subList(Math.min(1, startReady.size()), startReady.size()));
        StringBuilder store = new StringBuilder("[");
        for (int i = 0; i < storeSizes.size(); i++) {
            int[] s = storeSizes.get(i);
            if (i > 0) store.append(",");
            store.append(String.format(Locale.ROOT,
                "{\"i\":%d,\"maxLogPre\":%d,\"maxLogPost\":%d,\"maxExpPre\":%d,\"maxExpPost\":%d,\"heapAvailKb\":%d,\"readyMs\":%.1f}",
                s[0], s[1], s[2], s[3], s[4], s[5], startReady.get(i)));
        }
        store.append("]");

        // warmReady is empty at N=1 (the single launch is the cold one), so median/max are NaN;
        // emit JSON null there — a bare NaN token is invalid strict JSON and jq rejects the run.
        return String.format(Locale.ROOT,
            "{\"n\":%d,\"coldReadyMs\":%.1f,\"warmReadyMedianMs\":%s,\"warmReadyMaxMs\":%s,"
                + "\"totalThreads\":%d,\"baseThreads\":%d,\"threadsPerInstance\":%.2f,"
                + "\"rssMb\":%d,\"rssMbPerInstance\":%.1f,\"tcpSockets\":%d,\"socketsPerInstance\":%.1f,"
                + "\"heapUsedMb\":%d,\"heapCommittedMb\":%d,"
                + "\"maxLogFirst\":%d,\"maxLogLast\":%d,\"maxExpFirst\":%d,\"maxExpLast\":%d,"
                + "\"loadP95MinMs\":%.2f,\"loadP95MedianMs\":%.2f,\"loadP95MaxMs\":%.2f,"
                + "\"loadP99MedianMs\":%.2f,\"loadP99MaxMs\":%.2f,"
                + "\"threadHistogram\":\"%s\",\"perInstance\":%s}",
            n, coldReadyMs, jnum(median(warmReady)), jnum(max(warmReady)),
            totalThreads, baseThreads, (totalThreads - baseThreads) / (double) n,
            rssKb / 1024, (rssKb / 1024.0) / n, sockets, sockets / (double) n,
            usedHeapMb, committedHeapMb,
            storeSizes.get(0)[1], storeSizes.get(storeSizes.size() - 1)[1],
            storeSizes.get(0)[3], storeSizes.get(storeSizes.size() - 1)[3],
            min(p95s), median(p95s), max(p95s), median(p99s), max(p99s),
            histString(hist), store.toString());
    }

    /**
     * Per-instance light load run concurrently across instances: each instance gets its own client
     * thread that seeds one expectation, fires a warm-up burst (discarded — warm-up is bias, not
     * noise), then a measured burst whose latency DISTRIBUTION is returned.
     */
    static double[][] lightLoad(List<ClientAndServer> servers, List<Integer> ports,
                                int warmup, int measured) throws Exception {
        int n = servers.size();
        double[][] pct = new double[n][4];
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final ClientAndServer server = servers.get(i);
            final int port = ports.get(i);
            threads[i] = new Thread(() -> {
                try {
                    server.when(request().withMethod("GET").withPath("/probe"))
                        .respond(response().withStatusCode(200).withBody("ok"));
                    HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
                    HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/probe"))
                        .timeout(Duration.ofSeconds(2)).GET().build();
                    for (int w = 0; w < warmup; w++) c.send(req, HttpResponse.BodyHandlers.discarding());
                    List<Double> lat = new ArrayList<>(measured);
                    for (int m = 0; m < measured; m++) {
                        long t0 = System.nanoTime();
                        c.send(req, HttpResponse.BodyHandlers.discarding());
                        lat.add((System.nanoTime() - t0) / 1e6);
                    }
                    pct[idx] = new double[]{pctile(lat, 50), pctile(lat, 95), pctile(lat, 99), max(lat)};
                } catch (Exception e) {
                    pct[idx] = new double[]{-1, -1, -1, -1};
                    System.err.println("   load failed on instance " + idx + ": " + e);
                }
            }, "load-" + i);
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        return pct;
    }

    // ---- OS-level probes (counted, not computed) ----

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

    static int liveThreadCount() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }

    static Map<String, Integer> threadHistogram() {
        Map<String, Integer> hist = new TreeMap<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            hist.merge(prefixOf(t.getName()), 1, Integer::sum);
        }
        return hist;
    }

    static String prefixOf(String name) {
        // Collapse the numeric suffix so MockServer-workerEventLoop3 and ...4 fold together.
        String p = name.replaceAll("[0-9]+$", "#");
        // Netty loops look like "nioEventLoopGroup-3-2"; fold both numbers.
        p = p.replaceAll("-[0-9#]+-[0-9#]+$", "-#-#");
        return p;
    }

    static String histString(Map<String, Integer> hist) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> e : hist.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString().replace("\"", "'");
    }

    static long rssKb() {
        try {
            Process p = new ProcessBuilder("ps", "-o", "rss=", "-p", JVM_PID).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return out.isEmpty() ? -1 : Long.parseLong(out.split("\\s+")[0]);
        } catch (Exception e) { return -1; }
    }

    /** Count TCP sockets the JVM holds (listen + established) — the ephemeral-port proxy. */
    static int tcpSocketCount() {
        try {
            Process p = new ProcessBuilder("lsof", "-nP", "-p", JVM_PID, "-a", "-i", "tcp").start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            int lines = 0;
            for (String l : out.split("\n")) if (l.contains("TCP")) lines++;
            return lines;
        } catch (Exception e) { return -1; }
    }

    // ---- stats ----

    static double pctile(List<Double> xs, double pct) {
        if (xs.isEmpty()) return Double.NaN;
        List<Double> s = new ArrayList<>(xs);
        Collections.sort(s);
        int idx = (int) Math.ceil(pct / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(idx, s.size() - 1)));
    }

    /** JSON number, or the literal null for NaN (a bare NaN token is invalid strict JSON). */
    static String jnum(double v) { return Double.isNaN(v) ? "null" : String.format(Locale.ROOT, "%.1f", v); }

    static double median(List<Double> xs) { return pctile(xs, 50); }
    static double min(List<Double> xs) { return xs.isEmpty() ? Double.NaN : Collections.min(xs); }
    static double max(List<Double> xs) { return xs.isEmpty() ? Double.NaN : Collections.max(xs); }

    // ---- args ----

    static int[] intList(String csv) {
        String[] parts = csv.split(",");
        int[] r = new int[parts.length];
        for (int i = 0; i < parts.length; i++) r[i] = Integer.parseInt(parts[i].trim());
        return r;
    }

    static int intOpt(String[] a, String k, int def) {
        String v = strOpt(a, k, null);
        return v == null ? def : Integer.parseInt(v);
    }

    static String strOpt(String[] a, String k, String def) {
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals(k)) return a[i + 1];
        return def;
    }
}
