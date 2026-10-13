package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.function.BooleanSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.NO_MATCH_RESPONSE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

/**
 * {@code PUT /mockserver/explainUnmatched} with {@code "suggestFixes": true} adds a JSON patch to each of the closest
 * non-matching expectations. The patch is only a suggestion: explaining changes no expectation, and the patch,
 * applied to the expectation as the retrieve endpoint returns it and sent back through the ordinary expectation
 * update, makes the expectation serve the request.
 */
public class HttpStateExplainUnmatchedSuggestedFixTest {

    private HttpState httpState;
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    @Test
    public void shouldSuggestAPatchThatTheExpectationUpdateApiAppliesWithoutApplyingItItself() throws Exception {
        start(configuration());
        httpState.add(new Expectation(request().withMethod("GET").withPath("/api/order").withHeader("X-Tenant", "blue"))
            .withId("near").thenRespond(response().withBody("near")));
        HttpRequest unmatched = request().withMethod("GET").withPath("/api/orders").withHeader("X-Tenant", "green");
        logUnmatched(unmatched);
        String before = retrieveExpectation("near");

        JsonNode closest = explainUnmatched("{\"limit\":10,\"suggestFixes\":true}")
            .path("unmatchedRequests").get(0).path("closestExpectations").get(0);
        JsonNode fix = closest.path("suggestedFix");

        assertThat(closest.path("expectationId").asText(), is("near"));
        assertThat(fix.path("patchedFields").toString(), is("[\"path\",\"headers\"]"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertThat("explaining must never apply the fix", retrieveExpectation("near"), is(before));
        assertThat(httpState.firstMatchingExpectation(unmatched.clone()), is((Expectation) null));

        JsonNode retrieved = objectMapper.readTree(before).get(0);
        JsonNode patched = JsonPatch.fromJson(fix.path("patch")).apply(retrieved);
        httpState.add(new ExpectationSerializer(new MockServerLogger(), configuration()).deserialize(objectMapper.writeValueAsString(patched)));

        Expectation served = httpState.firstMatchingExpectation(unmatched.clone());
        assertThat(served == null ? null : served.getId(), is("near"));
        assertThat(httpState.retrieve(request().withQueryStringParameter("type", "active_expectations")).getBodyAsString(),
            not(containsString("/api/order\"")));
    }

    @Test
    public void shouldLeaveTheOutputUnchangedWithoutSuggestFixes() throws Exception {
        start(configuration());
        httpState.add(new Expectation(request().withMethod("GET").withPath("/api/order")).withId("near").thenRespond(response()));
        logUnmatched(request().withMethod("GET").withPath("/api/orders"));

        JsonNode closest = explainUnmatched("{\"limit\":10}")
            .path("unmatchedRequests").get(0).path("closestExpectations").get(0);

        assertThat(closest.has("suggestedFix"), is(false));
    }

    @Test
    public void shouldNotPutARedactedRequestValueInThePatch() throws Exception {
        start(configuration().redactSecretsInLog(true));
        httpState.add(new Expectation(request().withMethod("GET").withPath("/api").withHeader("Authorization", "Bearer expected-value"))
            .withId("auth").thenRespond(response()));
        logUnmatched(request().withMethod("GET").withPath("/api").withHeader("Authorization", "Bearer s3cr3t-request-token"));

        JsonNode fix = explainUnmatched("{\"limit\":10,\"suggestFixes\":true}")
            .path("unmatchedRequests").get(0).path("closestExpectations").get(0).path("suggestedFix");

        assertThat(fix.toString(), not(containsString("s3cr3t-request-token")));
        assertThat(fix.path("patch").size(), is(0));
        assertThat(fix.path("unpatchedFields").get(0).path("field").asText(), is("headers"));
    }

    @Test
    public void shouldNotPutARedactedNumericBodyValueInThePatch() throws Exception {
        start(configuration().redactSecretsInLog(true).fixtureBodyRedactFields("card"));
        httpState.add(new Expectation(request().withMethod("POST").withPath("/pay").withBody(json("{\"card\":1111222233334444,\"amount\":1}")))
            .withId("pay").thenRespond(response()));
        logUnmatched(request().withMethod("POST").withPath("/pay").withBody(json("{\"card\":4929123412341234,\"amount\":1}")));

        JsonNode closest = explainUnmatched("{\"limit\":10,\"suggestFixes\":true}")
            .path("unmatchedRequests").get(0).path("closestExpectations").get(0);

        assertThat("the explanation redacts the value", closest.path("differences").toString(), not(containsString("4929123412341234")));
        assertThat(closest.path("suggestedFix").toString(), not(containsString("4929123412341234")));
        assertThat(closest.path("suggestedFix").path("unpatchedFields").get(0).path("field").asText(), is("body"));
    }

    @Test
    public void shouldSuggestFixesForAtMostTheFiveClosestExpectations() throws Exception {
        start(configuration());
        for (int i = 0; i < 7; i++) {
            httpState.add(new Expectation(request().withMethod("GET").withPath("/api/other" + i)).withId("e" + i).thenRespond(response()));
        }
        logUnmatched(request().withMethod("GET").withPath("/api/orders"));

        JsonNode closestExpectations = explainUnmatched("{\"limit\":10,\"suggestFixes\":true}")
            .path("unmatchedRequests").get(0).path("closestExpectations");

        int withFix = 0;
        for (JsonNode closest : closestExpectations) {
            withFix += closest.has("suggestedFix") ? 1 : 0;
        }
        assertThat(closestExpectations.size(), is(7));
        assertThat(withFix, is(HttpState.EXPLAIN_UNMATCHED_FIXES_PER_REQUEST));
    }

    // ---- helpers ----

    private void start(Configuration configuration) {
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateExplainUnmatchedSuggestedFixTest.class);
        httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
    }

    private void logUnmatched(HttpRequest unmatched) throws Exception {
        httpState.log(new LogEntry()
            .setType(NO_MATCH_RESPONSE)
            .setLogLevel(org.slf4j.event.Level.INFO)
            .setHttpRequest(unmatched)
            .setHttpResponse(response().withStatusCode(404))
            .setMessageFormat("no expectation for:{}returning response:{}")
            .setArguments(unmatched, response().withStatusCode(404))
        );
        pollUntilTrue(() -> {
            try {
                return explainUnmatched("{\"limit\":10}").path("unmatchedRequestCount").asInt() >= 1;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private String retrieveExpectation(String id) {
        HttpResponse response = httpState.retrieve(request().withMethod("PUT")
            .withQueryStringParameter("type", "active_expectations")
            .withBody("{\"id\":\"" + id + "\"}"));
        assertThat(response.getStatusCode(), is(200));
        return response.getBodyAsString();
    }

    private JsonNode explainUnmatched(String body) throws Exception {
        HttpResponse response = httpState.explainUnmatched(request().withMethod("PUT").withBody(body));
        assertThat(response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }

    private static void pollUntilTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting for the unmatched request to be recorded");
            }
            Thread.sleep(50);
        }
    }
}
