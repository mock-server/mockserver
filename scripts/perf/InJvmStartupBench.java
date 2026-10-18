import org.mockserver.integration.ClientAndServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The IN-JVM startup cost.
 *
 * <p>{@code MockServerExtension} / a plain JUnit test starts MockServer by calling
 * {@link ClientAndServer#startClientAndServer(Integer...)} inside the ALREADY-RUNNING test JVM —
 * there is no container and no fresh {@code java} process. A suite of 500 test classes pays this
 * in-JVM start cost 500 times and the {@code docker run} cost zero times, so this — not the
 * container path — is the number the "500 test classes" mandate actually asks for.
 *
 * <p>Readiness is defined the same way the pipeline defines it: a successful
 * {@code PUT /mockserver/status} (200), NOT an open TCP port. MockServer accepts a connection and
 * then resets it during initialisation, so port-open would measure the wrong thing.
 *
 * <p>Reports, in milliseconds:
 * <ul>
 *   <li>{@code call}  — {@code startClientAndServer(...)} returns (the API's own notion of started)</li>
 *   <li>{@code ready} — launch to first {@code PUT /mockserver/status} == 200</li>
 * </ul>
 * The FIRST launch is a cold, class-loading-dominated launch — a suite pays it once per test JVM,
 * so it is reported separately as the cold figure. The per-start number a large suite actually pays
 * 499 more times is the median of the warm launches, taken after discarding one warm-up launch —
 * the same discard-one, median-of-nine shape as 8a.
 *
 * <p>Usage: {@code java -cp <jar-with-dependencies> InJvmStartupBench.java [--port P] [--warmups N]
 * [--runs N] [--init /path/to/expectations.json] [--label NAME]}
 */
public class InJvmStartupBench {

    public static void main(String[] args) throws Exception {
        int port = intOpt(args, "--port", 24080);
        int warmups = intOpt(args, "--warmups", 1);
        int runs = intOpt(args, "--runs", 9);
        String init = strOpt(args, "--init", null);
        String label = strOpt(args, "--label", init == null ? "injvm" : "injvm-init");

        // Match the pipeline's quiet launch: WARN logging, no metrics endpoint. Set BEFORE any
        // MockServer class initialises so ConfigurationProperties picks it up.
        System.setProperty("mockserver.logLevel", "WARN");
        System.setProperty("mockserver.disableSystemOut", "true");
        if (init != null) {
            System.setProperty("mockserver.initializationJsonPath", init);
        }

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build();

        int total = warmups + runs;
        List<Double> callSamples = new ArrayList<>();
        List<Double> readySamples = new ArrayList<>();
        double firstCall = Double.NaN, firstReady = Double.NaN;

        System.err.printf("== %s: %d warm-up + %d measured in-JVM launches (init=%s)%n",
                label, warmups, runs, init == null ? "none" : init);

        for (int i = 0; i < total; i++) {
            int p = port + i; // fresh port per launch avoids TIME_WAIT / rebind races
            long t0 = System.nanoTime();
            ClientAndServer server = ClientAndServer.startClientAndServer(p);
            double callMs = (System.nanoTime() - t0) / 1_000_000.0;
            // Readiness by /mockserver/status == 200, exactly as perf-test-run.sh probes it.
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            double readyMs = Double.NaN;
            while (System.nanoTime() < deadline) {
                if (status200(http, p)) {
                    readyMs = (System.nanoTime() - t0) / 1_000_000.0;
                    break;
                }
                Thread.sleep(1);
            }
            server.stop();
            // Let the port and event loops unwind so the next launch is clean.
            Thread.sleep(150);

            if (Double.isNaN(readyMs)) {
                throw new IllegalStateException("launch " + i + " never became ready (status 200)");
            }
            boolean warm = i >= warmups;
            String tag = warm ? String.format("measured %d", i - warmups + 1) : "warm-up";
            System.err.printf("   launch %2d (%-10s): call %6.1f ms, ready %6.1f ms%n", i + 1, tag, callMs, readyMs);
            if (i == 0) { // the VERY FIRST in-JVM start — cold class-loading, paid once per suite JVM
                firstCall = callMs;
                firstReady = readyMs;
            }
            if (warm) {
                callSamples.add(callMs);
                readySamples.add(readyMs);
            }
        }

        double callMed = median(callSamples), readyMed = median(readySamples);
        System.err.printf("%n%s: measured=%d  first(cold) call %.1f / ready %.1f ms  |  median call %.1f ms  ready %.1f ms  [%.1f..%.1f]%n",
                label, readySamples.size(), firstCall, firstReady, callMed, readyMed,
                Collections.min(readySamples), Collections.max(readySamples));

        // Emit a machine-readable line the orchestrator scrapes (prefixed so it is unambiguous).
        System.out.printf("BENCH_JSON {\"label\":\"%s\",\"measured\":%d,\"first_call_ms\":%.1f,\"first_ready_ms\":%.1f," +
                        "\"call_median_ms\":%.1f,\"ready_median_ms\":%.1f,\"ready_min_ms\":%.1f,\"ready_max_ms\":%.1f}%n",
                label, readySamples.size(), firstCall, firstReady, callMed, readyMed,
                Collections.min(readySamples), Collections.max(readySamples));
    }

    private static boolean status200(HttpClient http, int port) {
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

    private static double median(List<Double> xs) {
        List<Double> s = new ArrayList<>(xs);
        Collections.sort(s);
        int n = s.size();
        if (n == 0) return Double.NaN;
        return (n % 2 == 1) ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    private static int intOpt(String[] a, String k, int def) {
        String v = strOpt(a, k, null);
        return v == null ? def : Integer.parseInt(v);
    }

    private static String strOpt(String[] a, String k, String def) {
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals(k)) return a[i + 1];
        return def;
    }
}
