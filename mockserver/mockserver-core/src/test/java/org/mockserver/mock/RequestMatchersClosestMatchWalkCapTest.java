package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.RequestMatchersUnmatchedDiagnosticsTest.CapturingLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Pins the evaluation cap on the INFO closest-match walk an unmatched request takes when the candidate
 * index narrowed its scan: at most {@code closestMatchMaxEvaluations} expectations beyond the request's
 * candidates are evaluated (and logged), the closest-match line says when expectations were left
 * unchecked, and lazy removal and the namespace-skip count still cover every expectation.
 *
 * <p>The differential oracle is the un-indexed serving scan (the walk's reference behaviour) over a store
 * holding only the expectations the capped walk checks, plus every foreign-namespace one: its entries must
 * equal the capped walk's, apart from the closest-match line's "not checked" note. Instance configuration
 * and a capturing logger only, so it runs in the parallel phase.
 */
public class RequestMatchersClosestMatchWalkCapTest {

    private static final int INDEXED = 2;
    private static final int UNINDEXED = 1_000_000;
    private static final String NAMESPACE_HEADER = "X-MockServer-Namespace";
    private static final Pattern NOT_CHECKED_NOTE = Pattern.compile("only (\\d+) of (\\d+) expectations were checked, so a closer one may exist$");

    /** Queues lazy removals so a test decides when they run. */
    private static final class QueueingScheduler extends Scheduler {
        private final List<Runnable> submitted = new ArrayList<>();

        QueueingScheduler(Configuration configuration, MockServerLogger logger) {
            super(configuration, logger, true);
        }

        @Override
        public void submit(Runnable command) {
            submitted.add(command);
        }

        void runAll() {
            List<Runnable> commands = new ArrayList<>(submitted);
            submitted.clear();
            commands.forEach(Runnable::run);
        }
    }

    private record Spec(String id, String method, String path, String namespace, boolean once, int priority, long created) {
        Expectation build() {
            HttpRequest httpRequest = request().withMethod(method).withPath(path);
            if (id.startsWith("c-hdr")) {
                httpRequest.withHeader("X-Tenant", "tenant-" + id.substring(id.length() - 1));
            } else if (id.startsWith("f-") && method.equals("POST")) {
                // shares the POST /probe bucket with a probe, which it must not match
                httpRequest.withHeader("X-Kind", "post-filler");
            }
            Expectation expectation = new Expectation(httpRequest, once ? Times.once() : Times.unlimited(), TimeToLive.unlimited(), priority)
                .withId(id)
                .withCreated(created)
                .thenRespond(response().withBody(id));
            return namespace == null ? expectation : expectation.withNamespace(namespace);
        }

        HttpRequest exhaustingRequest() {
            HttpRequest httpRequest = request().withMethod(method).withPath(path);
            return method.equals("POST") ? httpRequest.withHeader("X-Kind", "post-filler") : httpRequest;
        }

        boolean isCandidateFor(HttpRequest probe) {
            return id.equals("c-regex") || (method.equals(probe.getMethod().getValue()) && path.equals(probe.getPath().getValue()));
        }

        boolean inNamespace(String requestNamespace) {
            return namespace == null || namespace.equals(requestNamespace);
        }
    }

    /**
     * Three header-mismatch expectations and a regex fallthrough that are candidates for the probes, then
     * {@code fillers} non-candidates with a mix of used-up {@code once()}, foreign-namespace, higher-priority
     * and other-bucket expectations.
     */
    private static List<Spec> corpus(int fillers) {
        List<Spec> specs = new ArrayList<>();
        long created = 1;
        for (int i = 0; i < 3; i++) {
            specs.add(new Spec("c-hdr-" + i, "GET", "/probe", null, false, 0, created++));
        }
        specs.add(new Spec("c-regex", "GET", "/re-[0-9]+", null, false, 0, created++));
        for (int i = 0; i < fillers; i++) {
            String namespace = i % 7 == 4 ? "team-a" : null;
            boolean once = i % 5 == 3 && namespace == null;
            int priority = i % 11 == 6 ? 5 : 0;
            String method = i % 13 == 9 ? "POST" : "GET";
            String path = i % 13 == 9 ? "/probe" : "/filler/" + i;
            specs.add(new Spec(String.format("f-%04d", i), method, path, namespace, once, priority, created++));
        }
        return specs;
    }

    private static List<HttpRequest> probes() {
        List<HttpRequest> probes = new ArrayList<>();
        probes.add(request().withMethod("GET").withPath("/probe").withHeader("X-Tenant", "tenant-none"));
        probes.add(request().withMethod("GET").withPath("/nowhere"));
        probes.add(request().withMethod("GET").withPath("/probe").withHeader("X-Tenant", "tenant-none").withHeader(NAMESPACE_HEADER, "team-b"));
        probes.add(request().withMethod("GET").withPath("/filler/x").withHeader(NAMESPACE_HEADER, "team-a"));
        probes.add(request().withMethod("POST").withPath("/probe"));
        return probes;
    }

    private static final class Run {
        final List<String> rendered;
        final Set<String> remainingIds;
        final List<String> servingOrder;

        Run(List<String> rendered, Set<String> remainingIds, List<String> servingOrder) {
            this.rendered = rendered;
            this.remainingIds = remainingIds;
            this.servingOrder = servingOrder;
        }
    }

    // every did-not-match entry is logged (no maxLoggedMatchFailures limit), so the entries show what the walk evaluated
    private static Configuration configurationFor(String logLevel, boolean detailed, boolean failFast) {
        return configuration().logLevel(logLevel).detailedMatchFailures(detailed).matchersFailFast(failFast).matchNamespaceHeader(NAMESPACE_HEADER)
            .maxLoggedMatchFailures(-1);
    }

    /**
     * Loads the specs, uses up every {@code once()} one, sends the probe and returns its entries, the ids
     * left once its lazy removals have run, and the serving order it saw.
     */
    private static Run run(Configuration configuration, List<Spec> specs, int threshold, int cap, HttpRequest probe) {
        CapturingLogger logger = new CapturingLogger(configuration);
        QueueingScheduler scheduler = new QueueingScheduler(configuration, logger);
        try {
            RequestMatchers matchers = new RequestMatchers(configuration, logger, scheduler, mock(WebSocketClientRegistry.class))
                .withCandidateIndexThreshold(threshold)
                .withClosestMatchMaxEvaluations(cap);
            for (Spec spec : specs) {
                matchers.add(spec.build(), API);
            }
            for (Spec spec : specs) {
                if (spec.once()) {
                    assertThat(matchers.firstMatchingExpectation(spec.exhaustingRequest()).getId(), is(spec.id()));
                }
            }
            // a served response ends its "in progress" mark; only then can a used-up matcher be removed
            matchers.sortedRequestMatchers().forEach(matcher -> matcher.setResponseInProgress(false));
            List<String> servingOrder = ids(matchers.sortedRequestMatchers());
            scheduler.submitted.clear();
            logger.rendered.clear();
            assertThat(matchers.firstMatchingExpectation(probe.clone()), nullValue());
            List<String> rendered = new ArrayList<>(logger.rendered);
            scheduler.runAll();
            return new Run(rendered, new HashSet<>(ids(matchers.sortedRequestMatchers())), servingOrder);
        } finally {
            scheduler.shutdown();
        }
    }

    private static List<String> ids(List<HttpRequestMatcher> matchers) {
        return matchers.stream().map(m -> m.getExpectation().getId()).collect(Collectors.toList());
    }

    @Test
    public void cappedWalkEqualsTheUncappedScanOverTheExpectationsItChecked() {
        int cap = 6;
        int casesWithUncheckedExpectations = 0;
        int casesRemovingPastTheCap = 0;
        for (String logLevel : new String[]{"INFO", "TRACE"}) {
            for (boolean detailed : new boolean[]{false, true}) {
                for (boolean failFast : new boolean[]{true, false}) {
                    Configuration configuration = configurationFor(logLevel, detailed, failFast);
                    for (int fillers : new int[]{0, 1, 3, 5, 6, 7, 8, 9, 10, 11, 14, 40}) {
                        List<Spec> specs = corpus(fillers);
                        Map<String, Spec> byId = specs.stream().collect(Collectors.toMap(Spec::id, s -> s));
                        for (HttpRequest probe : probes()) {
                            String label = logLevel + " detailed=" + detailed + " failFast=" + failFast + " fillers=" + fillers + " probe=" + probe.getMethod().getValue() + " " + probe.getPath().getValue() + " ns=" + probe.getFirstHeader(NAMESPACE_HEADER);
                            String requestNamespace = probe.getFirstHeader(NAMESPACE_HEADER);
                            requestNamespace = requestNamespace.isEmpty() ? null : requestNamespace;
                            Run capped = run(configuration, specs, INDEXED, cap, probe);

                            // the expectations the capped walk evaluates: every candidate, then the first cap others
                            Set<String> kept = new HashSet<>();
                            int evaluated = 0;
                            int checked = 0;
                            int notChecked = 0;
                            boolean inactivePastTheCap = false;
                            for (String id : capped.servingOrder) {
                                Spec spec = byId.get(id);
                                if (!spec.inNamespace(requestNamespace)) {
                                    kept.add(id);
                                } else if (spec.isCandidateFor(probe)) {
                                    kept.add(id);
                                    checked++;
                                } else if (evaluated < cap) {
                                    kept.add(id);
                                    evaluated++;
                                    checked++;
                                } else {
                                    notChecked++;
                                    inactivePastTheCap |= spec.once();
                                }
                            }
                            List<Spec> keptSpecs = specs.stream().filter(s -> kept.contains(s.id())).collect(Collectors.toList());
                            Run reference = run(configuration, keptSpecs, UNINDEXED, cap, probe);

                            String cappedClosest = closestMatch(capped.rendered);
                            List<String> cappedWithoutNote = new ArrayList<>(capped.rendered);
                            if (notChecked > 0) {
                                casesWithUncheckedExpectations++;
                                assertThat(label, cappedClosest, notNullValue());
                                Matcher note = NOT_CHECKED_NOTE.matcher(cappedClosest);
                                assertThat(label + " (note)", note.find(), is(true));
                                assertThat(label + " (checked)", Integer.parseInt(note.group(1)), is(checked));
                                assertThat(label + " (total)", Integer.parseInt(note.group(2)), is(checked + notChecked));
                                cappedWithoutNote.replaceAll(entry -> entry.equals(cappedClosest) ? entry.substring(0, note.start()) : entry);
                            } else if (cappedClosest != null) {
                                assertThat(label + " (no note)", NOT_CHECKED_NOTE.matcher(cappedClosest).find(), is(false));
                            }
                            List<String> referenceEntries = withClosestMatchTrimmed(reference.rendered);
                            cappedWithoutNote = withClosestMatchTrimmed(cappedWithoutNote);
                            assertThat(label + " (entries only in the capped walk)", surplus(cappedWithoutNote, referenceEntries), is(empty()));
                            assertThat(label + " (entries only in the reference)", surplus(referenceEntries, cappedWithoutNote), is(empty()));

                            // lazy removal covers the whole store, as the un-indexed scan's does
                            Run fullReference = run(configuration, specs, UNINDEXED, cap, probe);
                            assertThat(label + " (lazy removal)", capped.remainingIds, is(fullReference.remainingIds));
                            if (inactivePastTheCap) {
                                casesRemovingPastTheCap++;
                                assertThat(label + " (removals happened)", fullReference.remainingIds.size(), lessThan(capped.servingOrder.size()));
                            }
                            // the namespace-skip count covers the whole store too
                            assertThat(label + " (namespace line)", namespaceLine(capped.rendered), is(namespaceLine(fullReference.rendered)));
                        }
                    }
                }
            }
        }
        assertThat(casesWithUncheckedExpectations, greaterThan(0));
        assertThat(casesRemovingPastTheCap, greaterThan(0));
    }

    @Test
    public void defaultCapBoundsTheEntriesOfAMissAndNamesWhatWasNotChecked() {
        Configuration configuration = configurationFor("INFO", true, true);
        HttpRequest probe = request().withMethod("GET").withPath("/nowhere");
        for (int stored : new int[]{RequestMatchers.DEFAULT_CLOSEST_MATCH_MAX_EVALUATIONS, RequestMatchers.DEFAULT_CLOSEST_MATCH_MAX_EVALUATIONS + 250}) {
            List<Spec> specs = new ArrayList<>();
            for (int i = 0; i < stored; i++) {
                specs.add(new Spec(String.format("f-%04d", i), "GET", "/filler/" + i, null, false, 0, i + 1));
            }
            Run run = run(configuration, specs, INDEXED, RequestMatchers.DEFAULT_CLOSEST_MATCH_MAX_EVALUATIONS, probe);
            long notMatched = run.rendered.stream().filter(e -> e.startsWith("INFO EXPECTATION_NOT_MATCHED") && !e.contains("closest expectation")).count();
            assertThat("stored=" + stored, notMatched, is((long) RequestMatchers.DEFAULT_CLOSEST_MATCH_MAX_EVALUATIONS));
            String closest = closestMatch(run.rendered);
            assertThat(closest, containsString("\"id\" : \"f-0000\""));
            if (stored > RequestMatchers.DEFAULT_CLOSEST_MATCH_MAX_EVALUATIONS) {
                assertThat(closest, containsString("only 1000 of 1250 expectations were checked, so a closer one may exist"));
            } else {
                assertThat(closest, not(containsString("expectations were checked")));
            }
        }
    }

    @Test
    public void closestMatchPastTheCapIsNotReportedAndTheLineSaysSo() {
        // without fail-fast, the early expectations differ on method and path, the late one on path only
        Configuration configuration = configurationFor("INFO", true, false);
        HttpRequest probe = request().withMethod("GET").withPath("/nowhere");
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            specs.add(new Spec("early-" + i, "POST", "/early/" + i, null, false, 0, i + 1));
        }
        specs.add(new Spec("late", "GET", "/late", null, false, 0, 100));
        String uncapped = closestMatch(run(configuration, specs, INDEXED, 100, probe).rendered);
        assertThat(uncapped, containsString("\"id\" : \"late\""));
        assertThat(uncapped, not(containsString("expectations were checked")));
        String capped = closestMatch(run(configuration, specs, INDEXED, 5, probe).rendered);
        assertThat(capped, containsString("\"id\" : \"early-0\""));
        assertThat(capped, containsString("only 5 of 6 expectations were checked, so a closer one may exist"));
    }

    @Test
    public void candidatePastTheCapIsStillReportedAsTheClosestMatch() {
        // the candidate (same method and path, differing on one header) sorts last, after the capped walk stops
        Configuration configuration = configurationFor("INFO", true, false);
        HttpRequest probe = request().withMethod("GET").withPath("/probe").withHeader("X-Tenant", "tenant-none");
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            specs.add(new Spec("early-" + i, "POST", "/early/" + i, null, false, 0, i + 1));
        }
        specs.add(new Spec("c-hdr-0", "GET", "/probe", null, false, -1, 100));
        String closest = closestMatch(run(configuration, specs, INDEXED, 2, probe).rendered);
        assertThat(closest, containsString("\"id\" : \"c-hdr-0\""));
        assertThat(closest, containsString("only 3 of 6 expectations were checked, so a closer one may exist"));
    }

    /** The note is appended after the request, so the line it is removed from may differ in trailing whitespace. */
    private static List<String> withClosestMatchTrimmed(List<String> entries) {
        return entries.stream().map(e -> e.contains("closest expectation") ? e.stripTrailing() : e).collect(Collectors.toList());
    }

    /** The entries of {@code entries} left after removing one occurrence per entry of {@code others}. */
    private static List<String> surplus(List<String> entries, List<String> others) {
        Map<String, Integer> remaining = new HashMap<>();
        for (String other : others) {
            remaining.merge(other, 1, Integer::sum);
        }
        List<String> surplus = new ArrayList<>();
        for (String entry : entries) {
            if (remaining.merge(entry, -1, Integer::sum) < 0) {
                surplus.add(entry);
            }
        }
        return surplus;
    }

    private static String closestMatch(List<String> entries) {
        return single(entries, e -> e.contains("closest expectation"));
    }

    private static String namespaceLine(List<String> entries) {
        return single(entries, e -> e.contains("skipped due to namespace mismatch"));
    }

    private static String single(List<String> entries, Predicate<String> predicate) {
        List<String> matching = entries.stream().filter(predicate).collect(Collectors.toList());
        assertThat(matching.size() <= 1, is(true));
        return matching.isEmpty() ? null : matching.get(0);
    }
}
