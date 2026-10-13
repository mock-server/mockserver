package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * {@code POST /mockserver/debugMismatch} must report the <b>closest</b> expectation, ranked by how
 * much of the request actually matched — not by registration order.
 *
 * <p><b>Fixture design.</b> In these tests the closest expectation is deliberately <b>not</b> the first
 * one registered — <i>except</i> in {@link #shouldReportTheSameClosestExpectationRegardlessOfRegistrationOrder()},
 * which is a declared control and must stay as it is. That ordering is the whole point: request matching
 * fails fast on the first non-matching field, so each mismatched expectation records exactly one differing
 * field and every candidate ties on the raw difference count. A tie is resolved by whichever was seen
 * first, so a fixture whose closest expectation happens to be registered first passes whether the ranking
 * works or not.
 *
 * <p>Against the unfixed ranking, the four ranking tests score <b>3 failures / 1 pass</b>, and the single pass
 * is that control — a standing demonstration of why fixture ordering decides whether these tests prove
 * anything.
 */
public class HttpStateDebugMismatchClosestMatchTest {

    private HttpState httpState;
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Before
    public void setUp() {
        Configuration configuration = configuration();
        Scheduler scheduler = new Scheduler(configuration,
            new MockServerLogger(configuration, HttpStateDebugMismatchClosestMatchTest.class), true);
        httpState = new HttpState(configuration,
            new MockServerLogger(configuration, HttpStateDebugMismatchClosestMatchTest.class), scheduler);
    }

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    /**
     * The far expectation differs on method, path and header; the near one differs on the header
     * alone. The near one is registered second, so registration order and closeness disagree.
     */
    @Test
    public void shouldReportTheClosestExpectationNotTheFirstRegistered() throws Exception {
        httpState.add(
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("/completely/different")
                    .withHeader("X-Token", "expected-token"))
                .withId("far")
                .thenRespond(response().withBody("far")),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("/api/orders")
                    .withHeader("X-Token", "expected-token"))
                .withId("near")
                .thenRespond(response().withBody("near"))
        );

        JsonNode result = debugMismatch();

        assertThat("the expectation differing only on a header is closer than one differing on "
                + "method, path and header",
            result.get("closestMatch").get("expectationId").asText(), is("near"));
    }

    /**
     * Registration order reversed, same expectations. Both orders must agree on the answer —
     * if they disagree, the ranking is still being decided by position.
     *
     * <p><b>Declared control — do not reorder.</b> This is the one test here that registers the closest
     * expectation <b>first</b>, and it therefore <b>passes against the unfixed implementation</b>: with
     * every candidate tied on a difference count of one, "first registered" and "closest" coincide, so it
     * cannot distinguish a working ranking from a broken one on its own. It is kept deliberately, paired
     * with {@link #shouldReportTheClosestExpectationNotTheFirstRegistered()} which uses the same two
     * expectations in the opposite order: together they show the answer must not depend on position.
     * Swapping this order to "make it consistent" with the rest of the class would delete the control and
     * leave nothing recording why the ordering matters.
     */
    @Test
    public void shouldReportTheSameClosestExpectationRegardlessOfRegistrationOrder() throws Exception {
        httpState.add(
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("/api/orders")
                    .withHeader("X-Token", "expected-token"))
                .withId("near")
                .thenRespond(response().withBody("near")),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("/completely/different")
                    .withHeader("X-Token", "expected-token"))
                .withId("far")
                .thenRespond(response().withBody("far"))
        );

        JsonNode result = debugMismatch();

        assertThat(result.get("closestMatch").get("expectationId").asText(), is("near"));
    }

    /**
     * With three candidates the closest sits last, so neither "first registered" nor "last
     * registered" can produce the right answer by accident.
     */
    @Test
    public void shouldRankAcrossMoreThanTwoCandidates() throws Exception {
        httpState.add(
            new Expectation(
                request().withMethod("DELETE").withPath("/nowhere").withHeader("X-Token", "expected-token"))
                .withId("furthest").thenRespond(response()),
            new Expectation(
                request().withMethod("GET").withPath("/api/other").withHeader("X-Token", "expected-token"))
                .withId("middle").thenRespond(response()),
            new Expectation(
                request().withMethod("GET").withPath("/api/orders").withHeader("X-Token", "expected-token"))
                .withId("closest").thenRespond(response())
        );

        JsonNode result = debugMismatch();

        assertThat(result.get("closestMatch").get("expectationId").asText(), is("closest"));
    }

    /**
     * {@code matchedFieldCount} must reflect how much actually matched. Under fail-fast every
     * mismatched expectation records exactly one differing field, so an implementation deriving it
     * from that count reports {@code totalFields - 1} for every expectation however badly it missed.
     */
    @Test
    public void shouldReportDifferentMatchedFieldCountsForDifferentlyCloseExpectations() throws Exception {
        httpState.add(
            new Expectation(
                request().withMethod("POST").withPath("/completely/different").withHeader("X-Token", "expected-token"))
                .withId("far").thenRespond(response()),
            new Expectation(
                request().withMethod("GET").withPath("/api/orders").withHeader("X-Token", "expected-token"))
                .withId("near").thenRespond(response())
        );

        JsonNode result = debugMismatch();

        int farMatched = matchedFieldCountOf(result, "far");
        int nearMatched = matchedFieldCountOf(result, "near");
        assertThat("an expectation that missed on three fields cannot have matched as much as one "
                + "that missed on a single header",
            nearMatched > farMatched, is(true));
    }

    @Test
    public void shouldLeaveOutAUsedUpExpectationWhoseLastResponseIsStillInProgress() throws Exception {
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/once"), Times.once(), TimeToLive.unlimited(), 10)
                .withId("once").thenRespond(response()),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("/api/orders")
                    .withHeader("X-Token", "expected-token"))
                .withId("near")
                .thenRespond(response().withBody("near"))
        );
        assertThat(httpState.getRequestMatchers().firstMatchingExpectation(request().withMethod("GET").withPath("/once")).getId(), is("once"));
        // a delayed response holds the used-up expectation in the store while it is written
        httpState.getRequestMatchers().sortedRequestMatchers().stream()
            .filter(matcher -> "once".equals(matcher.getExpectation().getId()))
            .findFirst().orElseThrow()
            .setResponseInProgress(true);

        JsonNode result = debugMismatch();

        assertThat(result.path("closestMatch").path("expectationId").asText(), is("near"));
        assertThat(result.get("results").size(), is(1));
    }

    // ---- helpers ----

    /** The request under test: matches "near" on everything except the token header. */
    private JsonNode debugMismatch() throws Exception {
        HttpResponse response = httpState.debugMismatch(
            request()
                .withMethod("POST")
                .withPath("/mockserver/debugMismatch")
                .withBody("{"
                    + "\"method\":\"GET\","
                    + "\"path\":\"/api/orders\","
                    + "\"headers\":{\"X-Token\":[\"actual-token\"]}"
                    + "}"));
        assertThat(response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }

    private static int matchedFieldCountOf(JsonNode result, String expectationId) {
        for (JsonNode entry : result.get("results")) {
            if (entry.has("expectationId") && expectationId.equals(entry.get("expectationId").asText())) {
                return entry.get("matchedFieldCount").asInt();
            }
        }
        throw new AssertionError("no result recorded for expectation '" + expectationId + "'");
    }
}
