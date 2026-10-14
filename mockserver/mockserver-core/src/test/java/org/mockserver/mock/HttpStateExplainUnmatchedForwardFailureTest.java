package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.log.model.LogEntryMessages;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.NO_MATCH_RESPONSE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.badGatewayResponse;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpForward.forward;

/**
 * A failed forward's {@code 502} is logged with the same type as an unmatched request's {@code 404}, but the request
 * matched its forward expectation: {@code PUT /mockserver/explainUnmatched} must not list it as unmatched, nor suggest
 * patching another expectation to catch it, nor let it take a place within {@code limit}.
 */
public class HttpStateExplainUnmatchedForwardFailureTest {

    private HttpState httpState;
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Before
    public void setUp() {
        Configuration configuration = configuration();
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateExplainUnmatchedForwardFailureTest.class);
        httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
    }

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    @Test
    public void shouldNotListARequestWhoseForwardFailedOrSuggestPatchingAnotherExpectationForIt() throws Exception {
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/stock")).withId("forward-stock")
                .thenForward(forward().withHost("127.0.0.1").withPort(1)),
            new Expectation(request().withMethod("GET").withPath("/stock").withHeader("X-Tenant", "blue")).withId("tenant-stock")
                .thenRespond(response().withBody("blue stock"))
        );
        logForwardFailure("fwd-1", request().withMethod("GET").withPath("/stock"));
        awaitForwardFailures(1);

        JsonNode result = explainUnmatched("{\"limit\":10,\"suggestFixes\":true}");

        assertThat(result.toString(), result.path("unmatchedRequestCount").asInt(), is(0));
        assertThat(result.toString(), result.path("unmatchedRequests").size(), is(0));
    }

    @Test
    public void shouldCountOnlyGenuineMissesTowardsTheLimit() throws Exception {
        httpState.add(new Expectation(request().withMethod("GET").withPath("/api/order")).withId("near").thenRespond(response()));
        logUnmatched("miss-1", request().withMethod("GET").withPath("/api/orders"));
        logForwardFailure("fwd-1", request().withMethod("GET").withPath("/stock"));
        logForwardFailure("fwd-2", request().withMethod("GET").withPath("/prices"));
        awaitForwardFailures(2);

        JsonNode result = explainUnmatched("{\"limit\":1}");

        assertThat(result.toString(), result.path("unmatchedRequestCount").asInt(), is(1));
        assertThat(result.toString(), result.path("unmatchedRequests").get(0).path("path").asText(), is("/api/orders"));
        assertThat(result.toString(), result.path("unmatchedRequests").get(0).path("closestExpectations").get(0).path("expectationId").asText(), is("near"));
    }

    @Test
    public void shouldStillListAGenuineMissOnThePathOfAFailedForward() throws Exception {
        httpState.add(new Expectation(request().withMethod("GET").withPath("/stock")).withId("forward-stock")
            .thenForward(forward().withHost("127.0.0.1").withPort(1)));
        logUnmatched("miss-1", request().withMethod("POST").withPath("/stock"));
        logForwardFailure("fwd-1", request().withMethod("GET").withPath("/stock"));
        awaitForwardFailures(1);

        JsonNode result = explainUnmatched("{\"limit\":10}");

        assertThat(result.toString(), result.path("unmatchedRequestCount").asInt(), is(1));
        JsonNode miss = result.path("unmatchedRequests").get(0);
        assertThat(result.toString(), miss.path("method").asText(), is("POST"));
        assertThat(result.toString(), miss.path("path").asText(), is("/stock"));
        assertThat(result.toString(), miss.path("closestExpectations").get(0).path("expectationId").asText(), is("forward-stock"));
    }

    // ---- helpers ----

    /** As the forward action logs a failed forward: the request, the error and the 502. */
    private void logForwardFailure(String correlationId, HttpRequest request) {
        HttpResponse badGateway = badGatewayResponse();
        httpState.log(new LogEntry().setType(NO_MATCH_RESPONSE).setLogLevel(Level.INFO).setCorrelationId(correlationId)
            .setHttpRequest(request).setHttpResponse(badGateway)
            .setMessageFormat(LogEntryMessages.FORWARD_FAILURE_MESSAGE_FORMAT).setArguments(request, "upstream refused the connection", badGateway));
    }

    private void logUnmatched(String correlationId, HttpRequest request) {
        httpState.log(new LogEntry().setType(NO_MATCH_RESPONSE).setLogLevel(Level.INFO).setCorrelationId(correlationId)
            .setHttpRequest(request).setHttpResponse(notFoundResponse())
            .setMessageFormat(LogEntryMessages.NO_MATCH_RESPONSE_NO_EXPECTATION_MESSAGE_FORMAT).setArguments(request, notFoundResponse()));
    }

    /** The event log is written asynchronously: wait, within a deadline, until the failed forwards logged last are recorded. */
    private void awaitForwardFailures(int count) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (objectMapper.readTree(httpState.explainForwardFailures(null, 20).getBodyAsString()).path("forwardFailureCount").asInt() < count) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting for " + count + " failed forwards to be recorded");
            }
            Thread.sleep(20);
        }
    }

    private JsonNode explainUnmatched(String body) throws Exception {
        HttpResponse response = httpState.explainUnmatched(request().withMethod("PUT").withBody(body));
        assertThat(response.getBodyAsString(), response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }
}
