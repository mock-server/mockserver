package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.JsonBody;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Pins the diagnostics an UNMATCHED request emits at INFO when the candidate index narrows the scan.
 *
 * <p>When no candidate matches, {@link RequestMatchers} walks the full list again so the closest-match
 * entry is identical to an un-indexed scan. That second walk must not evaluate (and so must not log)
 * an expectation the first walk already evaluated: each expectation produces exactly one
 * {@code EXPECTATION_NOT_MATCHED} / {@code EXPECTATION_MATCHED} entry per request, the same multiset of
 * rendered entries as the un-indexed scan, and the same closest-match entry.
 *
 * <p>The corpus covers the dimensions the reconciliation touches: a shared (method, path) bucket
 * missing on headers, on a JSON body and on a query parameter; an empty bucket (fallthrough-only
 * candidates); regex, blank-method and notted fallthroughs; an exhausted {@code once()} expectation
 * that is still in the list; an expectation whose properties match but whose scenario state rejects
 * it; a namespaced expectation; a higher-priority expectation; and a non-ASCII path that bypasses the
 * index. Uses instance configuration and a capturing logger only, so it runs in the parallel phase.
 */
public class RequestMatchersUnmatchedDiagnosticsTest {

    private static final int INDEXED = 2;
    private static final int UNINDEXED = 1_000_000;

    static final class CapturingLogger extends MockServerLogger {
        final List<String> rendered = new ArrayList<>();

        CapturingLogger(Configuration configuration) {
            super(configuration, LoggerFactory.getLogger(CapturingLogger.class));
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            if (isEnabledForInstance(logEntry.getLogLevel())) {
                rendered.add(logEntry.getLogLevel() + " " + logEntry.getType() + "\n" + logEntry.getMessage());
            }
        }
    }

    static List<Expectation> corpusExpectations() {
        List<Expectation> expectations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expectations.add(expectation("e-hdr-" + i, request().withMethod("GET").withPath("/api/items")
                .withHeader("X-Tenant", "tenant-" + i)));
        }
        for (int i = 0; i < 3; i++) {
            expectations.add(expectation("e-json-" + i, request().withMethod("POST").withPath("/api/items")
                .withBody(new JsonBody("{\"id\": " + i + ", \"name\": \"item-" + i + "\"}"))));
        }
        for (int i = 0; i < 2; i++) {
            expectations.add(expectation("e-query-" + i, request().withMethod("GET").withPath("/api/search")
                .withQueryStringParameter("q", "term-" + i)));
        }
        expectations.add(expectation("e-regex", request().withMethod("GET").withPath("/api/regex-[0-9]+")));
        expectations.add(expectation("e-anymethod", request().withPath("/anymethod")));
        expectations.add(expectation("e-notdelete", request().withMethod("!DELETE").withPath("/guarded")));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/api/items").withHeader("X-Once", "yes"),
            Times.once(), TimeToLive.unlimited(), 0).withId("e-once").thenRespond(response().withBody("e-once")));
        expectations.add(expectation("e-scenario", request().withMethod("GET").withPath("/api/items")
            .withHeader("X-Scenario", "s")).withScenarioName("unmatched-diagnostics").withScenarioState("NotTheStartState"));
        expectations.add(expectation("e-namespaced", request().withMethod("GET").withPath("/api/items")
            .withHeader("X-Tenant", "tenant-none")).withNamespace("team-a"));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/api/items").withHeader("X-Priority", "p"),
            Times.unlimited(), TimeToLive.unlimited(), 10).withId("e-priority").thenRespond(response().withBody("e-priority")));
        // a Content-Type without charset (drives the charset hint) and a body carrying every line
        // terminator the log indentation recognises
        expectations.add(expectation("e-ctype", request().withMethod("POST").withPath("/api/upload")
            .withHeader("Content-Type", "application/json")));
        expectations.add(expectation("e-body", request().withMethod("POST").withPath("/api/upload")
            .withBody("line1\r\nline2\u2028line3\rline4\u0085line5\u2029line6\n")));
        return expectations;
    }

    static List<HttpRequest> corpusProbes() {
        List<HttpRequest> probes = new ArrayList<>();
        probes.add(request().withMethod("GET").withPath("/api/items")
            .withHeaders(header("X-Tenant", "tenant-none"), header("Accept", "application/json"), header("User-Agent", "probe/1.0")));
        probes.add(request().withMethod("POST").withPath("/api/items")
            .withBody(new JsonBody("{\"id\": 99, \"name\": \"missing\"}")));
        probes.add(request().withMethod("GET").withPath("/api/search").withQueryStringParameter("q", "nope"));
        probes.add(request().withMethod("GET").withPath("/nowhere"));
        probes.add(request().withMethod("GET").withPath("/api/regex-abc"));
        probes.add(request().withMethod("DELETE").withPath("/guarded"));
        probes.add(request().withMethod("GET").withPath("/api/items").withHeader("X-Scenario", "s"));
        probes.add(request().withMethod("GET").withPath("/api/items").withHeader("X-Once", "yes"));
        probes.add(request().withMethod("GET").withPath("/api/items")
            .withHeaders(header("X-Tenant", "tenant-none"), header("X-MockServer-Namespace", "team-b")));
        probes.add(request().withMethod("GET").withPath("/Apİ/items"));
        probes.add(request().withMethod("POST").withPath("/api/upload")
            .withHeader("Content-Type", "application/json; charset=utf-8")
            .withBody("other\r\nbody\u2028with\rterminators\n"));
        return probes;
    }

    private static Expectation expectation(String id, HttpRequest httpRequest) {
        return new Expectation(httpRequest).withId(id).thenRespond(response().withBody(id));
    }

    static RequestMatchers matchers(Configuration configuration, MockServerLogger logger, int threshold) {
        RequestMatchers matchers = new RequestMatchers(configuration, logger, mock(Scheduler.class), mock(WebSocketClientRegistry.class))
            .withCandidateIndexThreshold(threshold);
        for (Expectation expectation : corpusExpectations()) {
            matchers.add(expectation, API);
        }
        // exhaust the once() expectation; its lazy removal goes to the mock scheduler, so it stays
        // in the list as an inactive matcher every later scan still walks
        assertThat(matchers.firstMatchingExpectation(request().withMethod("GET").withPath("/api/items").withHeader("X-Once", "yes")).getId(), is("e-once"));
        return matchers;
    }

    /**
     * Rendered entries for every corpus probe, one list per probe, in emission order.
     */
    static List<List<String>> render(String logLevel, boolean detailedMatchFailures, int threshold) {
        Configuration configuration = configuration().logLevel(logLevel).detailedMatchFailures(detailedMatchFailures);
        CapturingLogger logger = new CapturingLogger(configuration);
        RequestMatchers matchers = matchers(configuration, logger, threshold);
        List<List<String>> perProbe = new ArrayList<>();
        for (HttpRequest probe : corpusProbes()) {
            logger.rendered.clear();
            assertThat(matchers.firstMatchingExpectation(probe.clone()), nullValue());
            perProbe.add(new ArrayList<>(logger.rendered));
        }
        return perProbe;
    }

    @Test
    public void indexedMissEmitsTheSameEntriesAsTheUnindexedScan() {
        for (String logLevel : new String[]{"INFO", "TRACE"}) {
            for (boolean detailed : new boolean[]{false, true}) {
                List<List<String>> indexed = render(logLevel, detailed, INDEXED);
                List<List<String>> unindexed = render(logLevel, detailed, UNINDEXED);
                List<HttpRequest> probes = corpusProbes();
                for (int i = 0; i < probes.size(); i++) {
                    String label = logLevel + " detailed=" + detailed + " probe=" + probes.get(i).getMethod().getValue() + " " + probes.get(i).getPath().getValue();
                    assertThat(label, indexed.get(i).isEmpty(), is(false));
                    assertThat(label + " (entry multiset)", counts(indexed.get(i)), is(counts(unindexed.get(i))));
                    assertThat(label + " (closest-match entry)", closestMatch(indexed.get(i)), is(closestMatch(unindexed.get(i))));
                }
            }
        }
    }

    @Test
    public void indexedMissEvaluatesEachExpectationOnlyOnce() {
        List<List<String>> indexed = render("INFO", true, INDEXED);
        List<HttpRequest> probes = corpusProbes();
        for (int i = 0; i < probes.size(); i++) {
            Set<String> seen = new HashSet<>();
            List<String> duplicates = new ArrayList<>();
            for (String entry : indexed.get(i)) {
                if (!seen.add(entry)) {
                    duplicates.add(entry);
                }
            }
            assertThat("probe " + probes.get(i).getPath().getValue(), duplicates, is(empty()));
        }
    }

    @Test
    public void bucketMissOnHeadersReportsEveryBucketedExpectationOnceAndTheClosestMatch() {
        // the first probe shares GET /api/items with nine expectations (the candidate bucket) and
        // misses every one; the full list holds the other nine
        List<String> entries = render("INFO", true, INDEXED).get(0);
        for (Expectation expectation : corpusExpectations()) {
            String id = "\"id\" : \"" + expectation.getId() + "\"";
            long mentions = entries.stream().filter(e -> e.contains("EXPECTATION_NOT_MATCHED") && !e.contains("closest expectation") && e.contains(id)).count();
            assertThat(expectation.getId(), mentions, is(expectation.getId().equals("e-namespaced") ? 0L : 1L));
        }
        String closest = closestMatch(entries);
        assertThat(closest, notNullValue());
        // the used-up e-once fails with no differences but is never the closest; e-priority sorts first
        assertThat(closest, containsString("\"id\" : \"e-priority\""));
    }

    @Test
    public void propertiesMatchRejectedByScenarioIsLoggedOnce() {
        List<String> entries = render("INFO", false, INDEXED).get(6);
        long matched = entries.stream().filter(e -> e.startsWith("INFO EXPECTATION_MATCHED")).count();
        assertThat(matched, is(1L));
        assertThat(entries.stream().filter(e -> e.startsWith("INFO EXPECTATION_MATCHED")).findFirst().orElseThrow(), containsString("\"id\" : \"e-scenario\""));
    }

    @Test
    public void contentTypeCharsetHintIsReportedForTheHeaderMismatch() {
        for (int threshold : new int[]{INDEXED, UNINDEXED}) {
            for (boolean detailed : new boolean[]{false, true}) {
                List<String> entries = render("INFO", detailed, threshold).get(10);
                long hints = entries.stream()
                    .filter(e -> e.contains("HINT: Content-Type base type 'application/json' matches but received value includes charset: 'application/json; charset=utf-8'"))
                    .count();
                assertThat("threshold=" + threshold + " detailed=" + detailed, hints, is(1L));
            }
        }
    }

    @Test
    public void warnEmitsNothingForAMiss() {
        for (boolean detailed : new boolean[]{false, true}) {
            for (List<String> entries : render("WARN", detailed, INDEXED)) {
                assertThat(entries, hasSize(0));
            }
        }
    }

    private static Map<String, Integer> counts(List<String> entries) {
        Map<String, Integer> counts = new HashMap<>();
        for (String entry : entries) {
            counts.merge(entry, 1, Integer::sum);
        }
        return counts;
    }

    private static String closestMatch(List<String> entries) {
        return entries.stream().filter(e -> e.contains("closest expectation")).reduce((a, b) -> a + "\n---\n" + b).orElse(null);
    }
}
