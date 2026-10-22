package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.fixture.SensitiveValueMatcher;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.AnyOfRequestDefinition.requestAnyOf;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * An anyOf expectation is never placed in a literal (method, path) bucket of the candidate index, so it is found for
 * every request whichever branch matches and, at equal priority, keeps its place in the global order.
 */
public class RequestMatchersAnyOfTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    private RequestMatchers newMatchers(int threshold) {
        return new RequestMatchers(configuration(), new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class))
            .withCandidateIndexThreshold(threshold);
    }

    private static Expectation expectation(RequestDefinition requestDefinition, String body, int priority) {
        return new Expectation(requestDefinition, Times.unlimited(), TimeToLive.unlimited(), priority).thenRespond(response().withBody(body));
    }

    private static void addLiterals(RequestMatchers matchers) {
        for (int i = 0; i < 50; i++) {
            matchers.add(expectation(request().withMethod("GET").withPath("/exact/path-" + i), "literal-" + i, 0), API);
        }
    }

    private static String bodyOf(Expectation expectation) {
        return expectation == null ? null : expectation.getHttpResponse().getBodyAsString();
    }

    @Test
    public void shouldFindAnAnyOfExpectationByEveryBranchWithAndWithoutTheCandidateIndex() {
        for (int threshold : new int[]{4, Integer.MAX_VALUE}) {
            RequestMatchers matchers = newMatchers(threshold);
            addLiterals(matchers);
            matchers.add(expectation(requestAnyOf(request().withMethod("GET").withPath("/x"), request().withMethod("POST").withPath("/y")), "any-of", 0), API);

            assertThat("threshold " + threshold, bodyOf(matchers.firstMatchingExpectation(request().withMethod("GET").withPath("/x"))), is("any-of"));
            assertThat("threshold " + threshold, bodyOf(matchers.firstMatchingExpectation(request().withMethod("POST").withPath("/y"))), is("any-of"));
            assertThat("threshold " + threshold, matchers.firstMatchingExpectation(request().withMethod("GET").withPath("/y")), is(nullValue()));
            assertThat("threshold " + threshold, bodyOf(matchers.firstMatchingExpectation(request().withMethod("GET").withPath("/exact/path-7"))), is("literal-7"));
        }
    }

    @Test
    public void shouldKeepGlobalPriorityOrderBetweenAnAnyOfAndABucketedLiteral() {
        for (int threshold : new int[]{4, Integer.MAX_VALUE}) {
            RequestMatchers higherAnyOf = newMatchers(threshold);
            addLiterals(higherAnyOf);
            higherAnyOf.add(expectation(requestAnyOf(request().withPath("/exact/path-3"), request().withPath("/other")), "any-of", 10), API);
            assertThat("threshold " + threshold, bodyOf(higherAnyOf.firstMatchingExpectation(request().withMethod("GET").withPath("/exact/path-3"))), is("any-of"));

            RequestMatchers lowerAnyOf = newMatchers(threshold);
            addLiterals(lowerAnyOf);
            lowerAnyOf.add(expectation(requestAnyOf(request().withPath("/exact/path-3"), request().withPath("/other")), "any-of", -10), API);
            assertThat("threshold " + threshold, bodyOf(lowerAnyOf.firstMatchingExpectation(request().withMethod("GET").withPath("/exact/path-3"))), is("literal-3"));
            assertThat("threshold " + threshold, bodyOf(lowerAnyOf.firstMatchingExpectation(request().withMethod("GET").withPath("/other"))), is("any-of"));
        }
    }

    @Test
    public void shouldExplainWhichBranchCameClosest() {
        RequestMatchers matchers = newMatchers(Integer.MAX_VALUE);
        Expectation anyOf = expectation(requestAnyOf(
            request().withMethod("POST").withPath("/orders").withHeader("X-Tenant", "a"),
            request().withMethod("GET").withPath("/orders").withHeader("X-Tenant", "b")
        ), "any-of", 0).withId("any-of");
        matchers.add(anyOf, API);
        HttpRequestMatcher matcher = matchers.sortedRequestMatchers().get(0);
        HttpRequest received = request().withMethod("GET").withPath("/orders").withHeader("X-Tenant", "c");

        ObjectNode comparison = RequestMatchExplainer.compareFields(objectMapper, matcher, anyOf, received, SensitiveValueMatcher.of(Collections.emptyList()));

        assertThat(comparison.path("matches").asBoolean(), is(false));
        assertThat("the top-level differences are the closest branch's", comparison.path("differingFieldCount").asInt(), is(1));
        assertThat(comparison.path("differences").has("headers"), is(true));
        JsonNode branches = comparison.path("anyOf");
        assertThat(branches.path("closestBranch").asInt(), is(1));
        assertThat(branches.path("branches").size(), is(2));
        assertThat(branches.path("branches").get(0).path("differingFieldCount").asInt(), is(2));
        assertThat(branches.path("branches").get(1).path("differingFields").get(0).asText(), is("headers"));
    }

    @Test
    public void shouldNotAddBranchDetailForAMatchOrAPlainExpectation() {
        RequestMatchers matchers = newMatchers(Integer.MAX_VALUE);
        Expectation anyOf = expectation(requestAnyOf(request().withPath("/a"), request().withPath("/b")), "any-of", 0).withId("any-of");
        matchers.add(anyOf, API);
        HttpRequestMatcher matcher = matchers.sortedRequestMatchers().get(0);

        ObjectNode matched = RequestMatchExplainer.compareFields(objectMapper, matcher, anyOf, request().withPath("/b"), SensitiveValueMatcher.of(Collections.emptyList()));
        assertThat(matched.path("matches").asBoolean(), is(true));
        assertThat(matched.has("anyOf"), is(false));

        RequestMatchers plain = newMatchers(Integer.MAX_VALUE);
        Expectation literal = expectation(request().withPath("/a"), "plain", 0).withId("plain");
        plain.add(literal, API);
        ObjectNode unmatched = RequestMatchExplainer.compareFields(objectMapper, plain.sortedRequestMatchers().get(0), literal, request().withPath("/b"), SensitiveValueMatcher.of(Collections.emptyList()));
        assertThat(unmatched.has("anyOf"), is(false));
        assertThat(unmatched.path("differences"), is(notNullValue()));
    }
}
