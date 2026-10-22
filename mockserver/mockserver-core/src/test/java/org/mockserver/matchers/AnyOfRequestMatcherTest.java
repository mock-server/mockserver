package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.AnyOfRequestDefinition;
import org.mockserver.model.HttpRequest;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.AnyOfRequestDefinition.requestAnyOf;
import static org.mockserver.model.ConditionalRequestDefinition.requestIf;
import static org.mockserver.model.HttpRequest.request;

public class AnyOfRequestMatcherTest {

    private final Configuration configuration = configuration();
    private final MockServerLogger mockServerLogger = new MockServerLogger(AnyOfRequestMatcherTest.class);

    private HttpRequestMatcher dataPlane(AnyOfRequestDefinition anyOf) {
        return new MatcherBuilder(configuration, mockServerLogger).transformsToMatcher(new Expectation(anyOf));
    }

    private static HttpRequest get(String path) {
        return request().withMethod("GET").withPath(path);
    }

    @Test
    public void shouldBuildAnAnyOfMatcherForBothPlanes() {
        AnyOfRequestDefinition anyOf = requestAnyOf(request().withPath("/a"), request().withPath("/b"));
        assertThat(dataPlane(anyOf), instanceOf(AnyOfRequestMatcher.class));
        assertThat(new MatcherBuilder(configuration, mockServerLogger).transformsToMatcher(anyOf), instanceOf(AnyOfRequestMatcher.class));
    }

    @Test
    public void shouldMatchWhenAnyBranchMatches() {
        HttpRequestMatcher matcher = dataPlane(requestAnyOf(
            request().withMethod("GET").withPath("/a"),
            request().withMethod("POST").withPath("/b"),
            request().withPath("/c").withHeader("X-Flag", "on")
        ));

        assertThat(matcher.matches(null, get("/a")), is(true));
        assertThat(matcher.matches(null, request().withMethod("POST").withPath("/b")), is(true));
        assertThat(matcher.matches(null, get("/c").withHeader("X-Flag", "on")), is(true));
    }

    @Test
    public void shouldNotMatchWhenNoBranchMatches() {
        HttpRequestMatcher matcher = dataPlane(requestAnyOf(
            request().withMethod("GET").withPath("/a"),
            request().withMethod("POST").withPath("/b"),
            request().withPath("/c").withHeader("X-Flag", "on")
        ));

        assertThat(matcher.matches(null, get("/b")), is(false));
        assertThat(matcher.matches(null, get("/c")), is(false));
        assertThat(matcher.matches(null, get("/d")), is(false));
    }

    @Test
    public void shouldStopAtTheFirstMatchingBranch() {
        HttpRequest received = get("/a");
        // detailed but not diagnostic-only: every evaluated branch that fails records its differences here
        MatchDifference firstMatches = new MatchDifference(true, received);
        assertThat(dataPlane(requestAnyOf(request().withPath("/a"), request().withPath("/never"))).matches(firstMatches, received), is(true));
        assertThat(firstMatches.getAllDifferences().keySet(), empty());

        MatchDifference secondMatches = new MatchDifference(true, received);
        assertThat(dataPlane(requestAnyOf(request().withPath("/never"), request().withPath("/a"))).matches(secondMatches, received), is(true));
        assertThat(secondMatches.getAllDifferences().keySet(), contains(MatchDifference.Field.PATH));
    }

    @Test
    public void shouldMatchNothingWithoutBranches() {
        assertThat(dataPlane(requestAnyOf()).matches(null, get("/a")), is(false));
    }

    @Test
    public void shouldMatchWhenNoBranchMatchesIfNegated() {
        HttpRequestMatcher matcher = dataPlane((AnyOfRequestDefinition) requestAnyOf(request().withPath("/a"), request().withPath("/b")).withNot(true));

        assertThat(matcher.matches(null, get("/a")), is(false));
        assertThat(matcher.matches(null, get("/b")), is(false));
        assertThat(matcher.matches(null, get("/c")), is(true));
    }

    @Test
    public void shouldComposeWithNestedAnyOfAndConditionalBranches() {
        HttpRequestMatcher matcher = dataPlane(requestAnyOf(
            requestAnyOf(request().withPath("/x"), request().withPath("/y")),
            requestIf(request().withMethod("POST"), request().withPath("/orders"), request().withPath("/never"))
        ));

        assertThat(matcher.matches(null, get("/y")), is(true));
        assertThat(matcher.matches(null, request().withMethod("POST").withPath("/orders")), is(true));
        assertThat(matcher.matches(null, request().withMethod("POST").withPath("/other")), is(false));
        assertThat(matcher.matches(null, get("/never")), is(true));
        assertThat(matcher.matches(null, get("/z")), is(false));
    }

    @Test
    public void shouldBeUsableInsideAConditionalBranch() {
        HttpRequestMatcher matcher = new MatcherBuilder(configuration, mockServerLogger).transformsToMatcher(new Expectation(
            requestIf(request().withMethod("GET"), requestAnyOf(request().withPath("/a"), request().withPath("/b")), request().withPath("/post-only"))
        ));

        assertThat(matcher.matches(null, get("/b")), is(true));
        assertThat(matcher.matches(null, get("/post-only")), is(false));
        assertThat(matcher.matches(null, request().withMethod("POST").withPath("/post-only")), is(true));
    }

    @Test
    public void shouldFilterRecordedRequestsOnTheControlPlane() {
        HttpRequestMatcher filter = new MatcherBuilder(configuration, mockServerLogger)
            .transformsToMatcher(requestAnyOf(request().withPath("/a"), request().withPath("/b")));

        assertThat(filter.matches(get("/a")), is(true));
        assertThat(filter.matches(get("/b")), is(true));
        assertThat(filter.matches(get("/c")), is(false));
    }

    @Test
    public void shouldPickUpAnUpdatedDefinition() {
        HttpRequestMatcher matcher = dataPlane(requestAnyOf(request().withPath("/a")));
        assertThat(matcher.matches(null, get("/b")), is(false));

        matcher.update(new Expectation(requestAnyOf(request().withPath("/a"), request().withPath("/b"))));

        assertThat(matcher.matches(null, get("/b")), is(true));
    }

    @Test
    public void shouldExposeEveryBranchRequest() {
        HttpRequestMatcher matcher = dataPlane(requestAnyOf(request().withPath("/a"), request().withPath("/b")));

        assertThat(matcher.getHttpRequests(), contains(request().withPath("/a"), request().withPath("/b")));
    }

    @Test
    public void shouldKeepOnlyTheClosestBranchDifferencesInADiagnosticContext() {
        HttpRequestMatcher matcher = dataPlane(requestAnyOf(
            request().withMethod("POST").withPath("/other").withHeader("X-Flag", "on"),
            request().withMethod("GET").withPath("/a").withHeader("X-Flag", "on")
        ));
        HttpRequest received = get("/a").withHeader("X-Flag", "off");
        MatchDifference diagnostic = new MatchDifference(true, received).suppressMatchResultLogging().collectAllDifferences();

        assertThat(matcher.matches(diagnostic, received), is(false));

        // the second branch differs only on its header; the first also on method and path
        assertThat(diagnostic.getAllDifferences().keySet(), contains(MatchDifference.Field.HEADERS));
    }

    @Test
    public void shouldRecordNoDifferencesInADiagnosticContextWhenABranchMatches() {
        HttpRequestMatcher matcher = dataPlane(requestAnyOf(request().withPath("/other"), request().withPath("/a")));
        HttpRequest received = get("/a");
        MatchDifference diagnostic = new MatchDifference(true, received).suppressMatchResultLogging();

        assertThat(matcher.matches(diagnostic, received), is(true));
        assertThat(diagnostic.getAllDifferences().keySet(), empty());
    }

    @Test
    public void shouldCompareEveryBranchAndNameTheClosest() {
        AnyOfRequestMatcher matcher = (AnyOfRequestMatcher) dataPlane(requestAnyOf(
            request().withMethod("POST").withPath("/other"),
            request().withMethod("GET").withPath("/other"),
            request().withMethod("POST").withPath("/a")
        ));

        List<AnyOfRequestMatcher.BranchComparison> comparisons = matcher.compareBranches(get("/a"), true);

        assertThat(comparisons.size(), is(3));
        assertThat(comparisons.get(0).getDifferences().keySet(), containsInAnyOrder(MatchDifference.Field.METHOD, MatchDifference.Field.PATH));
        assertThat(comparisons.get(1).getDifferences(), hasKey(MatchDifference.Field.PATH));
        assertThat(comparisons.get(1).getDifferences(), not(hasKey(MatchDifference.Field.METHOD)));
        assertThat(comparisons.get(2).getDifferences(), hasKey(MatchDifference.Field.METHOD));
        // a tie on one differing field goes to the earlier branch
        assertThat(AnyOfRequestMatcher.closestBranch(comparisons), is(1));
        assertThat(AnyOfRequestMatcher.closestBranch(matcher.compareBranches(request().withMethod("POST").withPath("/a"), true)), is(2));
    }
}
