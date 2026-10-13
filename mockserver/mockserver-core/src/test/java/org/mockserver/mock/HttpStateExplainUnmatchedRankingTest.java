package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.function.BooleanSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.NO_MATCH_RESPONSE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * {@code PUT /mockserver/explainUnmatched} sorts its per-expectation results by
 * {@code differingFieldCount}, "closest match first". That ranking is only meaningful if every
 * differing field is actually counted.
 *
 * <p><b>Fixture design.</b> As in {@link HttpStateDebugMismatchClosestMatchTest}, the closest
 * expectation is registered <b>last</b>. Request matching fails fast on the first non-matching
 * field, so without an explicit opt-out every mismatched expectation reports exactly one differing
 * field, every entry ties, and a stable sort simply preserves registration order — producing a
 * "ranked" list that is really registration order. A fixture whose closest expectation is already
 * first cannot tell the two apart.
 */
public class HttpStateExplainUnmatchedRankingTest {

    private HttpState httpState;
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Before
    public void setUp() {
        Configuration configuration = configuration();
        Scheduler scheduler = new Scheduler(configuration,
            new MockServerLogger(configuration, HttpStateExplainUnmatchedRankingTest.class), true);
        httpState = new HttpState(configuration,
            new MockServerLogger(configuration, HttpStateExplainUnmatchedRankingTest.class), scheduler);
    }

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    @Test
    public void shouldRankClosestExpectationFirstRegardlessOfRegistrationOrder() throws Exception {
        httpState.add(
            new Expectation(
                request().withMethod("POST").withPath("/completely/different")
                    .withHeader("X-Token", "expected-token"))
                .withId("far").thenRespond(response()),
            new Expectation(
                request().withMethod("GET").withPath("/api/orders")
                    .withHeader("X-Token", "expected-token"))
                .withId("near").thenRespond(response())
        );

        logUnmatched();

        JsonNode result = explainUnmatched();
        JsonNode closestExpectations = result.path("unmatchedRequests").get(0).path("closestExpectations");

        assertThat("the expectation differing only on a header must rank ahead of one differing on "
                + "method, path and header",
            closestExpectations.get(0).path("expectationId").asText(), is("near"));
    }

    @Test
    public void shouldReportDifferingFieldCountsThatDistinguishCandidates() throws Exception {
        httpState.add(
            new Expectation(
                request().withMethod("POST").withPath("/completely/different")
                    .withHeader("X-Token", "expected-token"))
                .withId("far").thenRespond(response()),
            new Expectation(
                request().withMethod("GET").withPath("/api/orders")
                    .withHeader("X-Token", "expected-token"))
                .withId("near").thenRespond(response())
        );

        logUnmatched();

        JsonNode closestExpectations = explainUnmatched()
            .path("unmatchedRequests").get(0).path("closestExpectations");

        int nearCount = differingFieldCountOf(closestExpectations, "near");
        int farCount = differingFieldCountOf(closestExpectations, "far");
        assertThat("an expectation missing on method, path and header cannot report the same "
                + "differing-field count as one missing on a single header",
            nearCount < farCount, is(true));
    }

    @Test
    public void shouldLeaveOutAUsedUpExpectationWhoseLastResponseIsStillInProgress() throws Exception {
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/once"), Times.once(), TimeToLive.unlimited(), 10)
                .withId("once").thenRespond(response()),
            new Expectation(
                request().withMethod("GET").withPath("/api/orders")
                    .withHeader("X-Token", "expected-token"))
                .withId("near").thenRespond(response())
        );
        assertThat(httpState.getRequestMatchers().firstMatchingExpectation(request().withMethod("GET").withPath("/once")).getId(), is("once"));
        // a delayed response holds the used-up expectation in the store while it is written
        httpState.getRequestMatchers().sortedRequestMatchers().stream()
            .filter(matcher -> "once".equals(matcher.getExpectation().getId()))
            .findFirst().orElseThrow()
            .setResponseInProgress(true);

        logUnmatched();

        JsonNode closestExpectations = explainUnmatched()
            .path("unmatchedRequests").get(0).path("closestExpectations");
        assertThat(closestExpectations.size(), is(1));
        assertThat(closestExpectations.get(0).path("expectationId").asText(), is("near"));
    }

    // ---- helpers ----

    /** The unmatched request: matches "near" on everything except the token header. */
    private void logUnmatched() throws Exception {
        httpState.log(new LogEntry()
            .setType(NO_MATCH_RESPONSE)
            .setLogLevel(org.slf4j.event.Level.INFO)
            .setHttpRequest(request().withMethod("GET").withPath("/api/orders")
                .withHeader("X-Token", "actual-token"))
            .setHttpResponse(response().withStatusCode(404))
            .setMessageFormat("no expectation for:{}returning response:{}")
            .setArguments(request().withMethod("GET").withPath("/api/orders"),
                response().withStatusCode(404))
        );
        pollUntilTrue(() -> {
            try {
                return explainUnmatched().path("unmatchedRequestCount").asInt() >= 1;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private JsonNode explainUnmatched() throws Exception {
        HttpResponse response = httpState.explainUnmatched(
            request().withMethod("PUT").withBody("{\"limit\":10}"));
        assertThat(response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }

    private static int differingFieldCountOf(JsonNode closestExpectations, String expectationId) {
        for (JsonNode entry : closestExpectations) {
            if (expectationId.equals(entry.path("expectationId").asText())) {
                return entry.path("differingFieldCount").asInt();
            }
        }
        throw new AssertionError("no ranked entry for expectation '" + expectationId + "'");
    }

    private static void pollUntilTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting for the unmatched request to be recorded");
            }
            Thread.sleep(50);
        }
    }
}
