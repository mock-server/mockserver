package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * An indexed miss reconciles the closest match over a fresh full list after the narrowed scan, so the
 * store can change in between, e.g. when the lazy removal of an expired candidate, scheduled by the
 * narrowed scan, completes first. The synchronous {@link Scheduler} used here runs that removal inline,
 * so it always lands between the two scans.
 *
 * <p>Fixture: bucket {@code GET /a} holds A, X (expired) and B in that order, and N ({@code GET /b}) is
 * a non-candidate; the request is {@code GET /a} with a header none of them expects. Each active
 * expectation fails on one field under fail-fast, so the first active one in serving order is closest;
 * X is inactive and never closest. A scheduler hook can apply one more mutation when the request
 * schedules its first lazy removal (X's), e.g. removing the closest candidate A.
 */
public class RequestMatchersUnmatchedDiagnosticsStoreChangeTest {

    private static final HttpRequest PROBE = request().withMethod("GET").withPath("/a").withHeader("h", "9");
    private static final int INDEXED = 2;
    private static final int UNINDEXED = 1_000_000;

    private static final class HookedScheduler extends Scheduler {
        private Runnable hook;

        HookedScheduler(Configuration configuration, MockServerLogger logger) {
            super(configuration, logger, true);
        }

        @Override
        public void submit(Runnable command) {
            super.submit(command);
            Runnable once = hook;
            hook = null;
            if (once != null) {
                once.run();
            }
        }
    }

    private static List<String> run(int threshold, List<Expectation> expectations, Consumer<RequestMatchers> mutationDuringScan) {
        Configuration configuration = configuration().logLevel("INFO").detailedMatchFailures(true);
        RequestMatchersUnmatchedDiagnosticsTest.CapturingLogger logger = new RequestMatchersUnmatchedDiagnosticsTest.CapturingLogger(configuration);
        HookedScheduler scheduler = new HookedScheduler(configuration, logger);
        try {
            RequestMatchers matchers = new RequestMatchers(configuration, logger, scheduler, mock(WebSocketClientRegistry.class))
                .withCandidateIndexThreshold(threshold);
            for (Expectation expectation : expectations) {
                matchers.add(expectation, API);
            }
            logger.rendered.clear();
            // armed only now, so it fires on the first lazy removal of this request, not during setup
            scheduler.hook = mutationDuringScan == null ? null : () -> mutationDuringScan.accept(matchers);
            assertThat(matchers.firstMatchingExpectation(PROBE.clone()), nullValue());
            return logger.rendered;
        } finally {
            scheduler.shutdown();
        }
    }

    private static List<Expectation> fixture() {
        List<Expectation> expectations = new ArrayList<>();
        expectations.add(expectation("A", "/a", "1", 0));
        expectations.add(expired("X", "/a", "2"));
        expectations.add(expectation("B", "/a", "3", 0));
        expectations.add(expectation("N", "/b", "4", 0));
        return expectations;
    }

    private static Expectation expectation(String id, String path, String header, int priority) {
        return new Expectation(request().withMethod("GET").withPath(path).withHeader("h", header),
            Times.unlimited(), TimeToLive.unlimited(), priority).withId(id).thenRespond(response().withBody(id));
    }

    private static Expectation expired(String id, String path, String header) {
        return new Expectation(request().withMethod("GET").withPath(path).withHeader("h", header),
            Times.unlimited(), TimeToLive.exactly(TimeUnit.SECONDS, 60L).setEndDate(1L), 0).withId(id).thenRespond(response().withBody(id));
    }

    private static void removeA(RequestMatchers matchers) {
        matchers.clear(new ExpectationId().withId("A"), null);
    }

    private static boolean isClosestMatchEntry(String entry) {
        return entry.contains("closest expectation");
    }

    private static long matchEvaluationEntries(List<String> entries, String id) {
        return entries.stream()
            .filter(e -> e.startsWith("INFO EXPECTATION_NOT_MATCHED") || e.startsWith("INFO EXPECTATION_MATCHED"))
            .filter(e -> !isClosestMatchEntry(e))
            .filter(e -> e.contains("\"id\" : \"" + id + "\""))
            .count();
    }

    private static String closest(List<String> entries) {
        List<String> closest = entries.stream().filter(RequestMatchersUnmatchedDiagnosticsStoreChangeTest::isClosestMatchEntry).collect(Collectors.toList());
        assertThat("exactly one closest-match entry", closest.size(), is(1));
        return closest.get(0);
    }

    private static List<String> sorted(List<String> entries) {
        List<String> copy = new ArrayList<>(entries);
        Collections.sort(copy);
        return copy;
    }

    private static void assertEvaluatedOnce(List<String> entries, String... ids) {
        for (String id : ids) {
            assertThat("match evaluations of " + id, matchEvaluationEntries(entries, id), is(1L));
        }
    }

    @Test
    public void expiredCandidateRemovedByTheNarrowedScanGivesTheUnindexedDiagnostics() {
        List<String> indexed = run(INDEXED, fixture(), null);
        List<String> unindexed = run(UNINDEXED, fixture(), null);

        assertEvaluatedOnce(indexed, "A", "X", "B", "N");
        assertThat(closest(indexed), containsString("\"id\" : \"A\""));
        // the same entries, including X's removal; only the order differs (candidates first)
        assertThat(sorted(indexed), is(sorted(unindexed)));
    }

    @Test
    public void closestCandidateRemovedBetweenTheScansStillGivesTheUnindexedDiagnostics() {
        List<String> indexed = run(INDEXED, fixture(), RequestMatchersUnmatchedDiagnosticsStoreChangeTest::removeA);
        List<String> unindexed = run(UNINDEXED, fixture(), RequestMatchersUnmatchedDiagnosticsStoreChangeTest::removeA);

        assertEvaluatedOnce(indexed, "A", "X", "B", "N");
        assertThat(closest(indexed), containsString("\"id\" : \"A\""));
        assertThat(sorted(indexed), is(sorted(unindexed)));
    }

    @Test
    public void removedClosestCandidateDoesNotJumpAheadOfAnEarlierEqualNonCandidate() {
        // E (GET /b) sorts before A and ties it on failures, so the un-indexed scan reports E
        List<Expectation> expectations = new ArrayList<>();
        expectations.add(expectation("E", "/b", "5", 0));
        expectations.add(expectation("A", "/a", "1", 0));
        expectations.add(expired("X", "/a", "2"));
        expectations.add(expectation("B", "/a", "3", 0));
        expectations.add(expectation("N", "/b", "4", 0));

        List<String> indexed = run(INDEXED, expectations, RequestMatchersUnmatchedDiagnosticsStoreChangeTest::removeA);
        List<String> unindexed = run(UNINDEXED, expectations, RequestMatchersUnmatchedDiagnosticsStoreChangeTest::removeA);

        assertEvaluatedOnce(indexed, "A", "E", "X", "B", "N");
        assertThat(closest(unindexed), containsString("\"id\" : \"E\""));
        assertThat(sorted(indexed), is(sorted(unindexed)));
    }

    @Test
    public void removedClosestCandidateStillBeatsALaterEqualNonCandidate() {
        // F (GET /b) sorts after A and ties it on failures, so the un-indexed scan reports A
        List<Expectation> expectations = new ArrayList<>();
        expectations.add(expectation("A", "/a", "1", 0));
        expectations.add(expired("X", "/a", "2"));
        expectations.add(expectation("F", "/b", "5", 0));
        expectations.add(expectation("B", "/a", "3", 0));
        expectations.add(expectation("N", "/b", "4", 0));

        List<String> indexed = run(INDEXED, expectations, RequestMatchersUnmatchedDiagnosticsStoreChangeTest::removeA);
        List<String> unindexed = run(UNINDEXED, expectations, RequestMatchersUnmatchedDiagnosticsStoreChangeTest::removeA);

        assertEvaluatedOnce(indexed, "A", "X", "B", "F", "N");
        assertThat(closest(unindexed), containsString("\"id\" : \"A\""));
        assertThat(sorted(indexed), is(sorted(unindexed)));
    }

    @Test
    public void expectationAddedAheadOfTheCandidatesBetweenTheScansIsEvaluatedOnce() {
        List<String> indexed = run(INDEXED, fixture(), matchers -> matchers.add(expectation("Y", "/a", "6", 5), API));

        assertEvaluatedOnce(indexed, "A", "X", "B", "N", "Y");
        assertThat(closest(indexed), containsString("\"id\" : \"Y\""));
    }

    @Test
    public void expectationAddedAfterTheCandidatesBetweenTheScansIsEvaluatedOnce() {
        List<String> indexed = run(INDEXED, fixture(), matchers -> matchers.add(expectation("Y", "/a", "6", -5), API));

        assertEvaluatedOnce(indexed, "A", "X", "B", "N", "Y");
        assertThat(closest(indexed), containsString("\"id\" : \"A\""));
    }

    @Test
    public void nonCandidateRemovedBetweenTheScansIsNotEvaluated() {
        List<String> indexed = run(INDEXED, fixture(), matchers -> matchers.clear(request().withMethod("GET").withPath("/b")));

        assertEvaluatedOnce(indexed, "A", "X", "B");
        assertThat(matchEvaluationEntries(indexed, "N"), is(0L));
        assertThat(closest(indexed), containsString("\"id\" : \"A\""));
    }

    @Test
    public void candidateMovedAheadBetweenTheScansIsNotEvaluatedAgain() {
        List<String> indexed = run(INDEXED, fixture(), matchers -> matchers.add(expectation("B", "/a", "3", 5), API));

        assertEvaluatedOnce(indexed, "A", "X", "B", "N");
        assertThat(closest(indexed), containsString("\"id\" : \"A\""));
    }

    @Test
    public void candidateMovedBehindANonCandidateBetweenTheScansIsNotEvaluatedAgain() {
        List<String> indexed = run(INDEXED, fixture(), matchers -> matchers.add(expectation("A", "/a", "1", -5), API));

        assertEvaluatedOnce(indexed, "A", "X", "B", "N");
        // the stand-in A is offered at its matcher's new position, behind N, which ties it
        assertThat(closest(indexed), containsString("\"id\" : \"N\""));
    }

    @Test
    public void nonCandidateReprioritisedDuringTheFullScanDoesNotCauseCandidatesToBeEvaluatedAgain() {
        // the full scan's lazy removal of the expired non-candidate E moves N behind every candidate
        // while the scan is still walking its snapshot
        List<Expectation> expectations = new ArrayList<>();
        expectations.add(expired("E", "/c", "5"));
        expectations.add(expectation("N", "/b", "4", 0));
        expectations.add(expectation("A", "/a", "1", 0));
        expectations.add(expectation("B", "/a", "3", 0));

        List<String> indexed = run(INDEXED, expectations, matchers -> matchers.add(expectation("N", "/b", "4", -5), API));

        assertEvaluatedOnce(indexed, "E", "N", "A", "B");
        // E is inactive; N, still in the walked snapshot ahead of the candidates, ties A and comes first
        assertThat(closest(indexed), containsString("\"id\" : \"N\""));
    }

    @Test
    public void storeResetBetweenTheScansStillReportsTheClosestCandidate() {
        List<String> indexed = run(INDEXED, fixture(), RequestMatchers::reset);

        assertEvaluatedOnce(indexed, "A", "X", "B");
        assertThat(matchEvaluationEntries(indexed, "N"), is(0L));
        assertThat(closest(indexed), containsString("\"id\" : \"A\""));
    }
}
