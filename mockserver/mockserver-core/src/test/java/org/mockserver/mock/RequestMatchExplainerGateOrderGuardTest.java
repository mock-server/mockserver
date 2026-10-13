package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.fixture.SensitiveValueMatcher;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.LlmConversationMatcher;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.Not.not;

/**
 * Ties the explainer's gate order to the serving path, so the explanation of a request cannot drift from how
 * {@link RequestMatchers#firstMatchingExpectation} actually serves it. The source checks fail when a gate is added
 * to, removed from, reordered in or reworded in either loop (including a new {@code || condition} inside an existing
 * gate); the differential checks fail when the two disagree on a winner. Limit: a change inside a request matcher's
 * {@code matches()} is not seen here, but needs no guard, because the explainer calls the same {@code matches()}.
 */
public class RequestMatchExplainerGateOrderGuardTest {

    /**
     * Each gate, in serving order: the call that applies it in the serving loop, and in the explainer. The active
     * check has no call of its own in the serving loop: it runs first inside the request matcher's matches().
     */
    private static final List<String[]> GATES = Arrays.asList(
        new String[]{"NAMESPACE", "matchesNamespace(", "matchesNamespace("},
        new String[]{"INACTIVE", "httpRequestMatcher.matches(", "isActive()"},
        new String[]{"FIELDS_DIFFER", "httpRequestMatcher.matches(", "compareFields("},
        new String[]{"CONVERSATION", "convMatcher.matches(", "conversationMatches("},
        new String[]{"SCENARIO_STATE", "scenarioManager.matchesState(", "scenarioStateMatches("},
        new String[]{"PERCENTAGE", "matchesByPercentage()", "getPercentage()"}
    );
    private static final int SERVING_GATE_SKIPS = 4;

    /** Every condition in the serving loop before an expectation is committed, verbatim. */
    private static final List<String> SERVING_CONDITIONS = Arrays.asList(
        "if (!matchesNamespace(httpRequestMatcher.getExpectation(), requestNamespace)) {",
        "if (requestNamespace != null) {",
        "if (httpRequestMatcher.matches(matchDifference, requestDefinition)) {",
        "if (llmResponse != null) {",
        "if (convMatcher != null && convMatcher.hasPredicates()) {",
        "if (requestDefinition instanceof HttpRequest) {",
        "if (!convMatcher.matches((HttpRequest) requestDefinition, configuration)) {",
        "if (expectation.getScenarioName() != null && expectation.getScenarioState() != null) {",
        "if (!scenarioManager.matchesState(expectation.getScenarioName(), isolationKey, expectation.getScenarioState())) {",
        "if (!expectation.matchesByPercentage()) {"
    );

    /** Every condition in the explainer loop, verbatim. */
    private static final List<String> EXPLAINER_CONDITIONS = Arrays.asList(
        "if (expectation == null) {",
        "if (evaluated >= maxEvaluations) {",
        "if (!RequestMatchers.matchesNamespace(expectation, requestNamespace)) {",
        "} else if (!expectation.isActive()) {",
        "if (!node.path(\"matches\").asBoolean()) {",
        "if (differingFields.isEmpty() && expectation.getHttpRequest() != null && expectation.getHttpRequest().isNot()) {",
        "if (winner == null && failures < closestFailures) {",
        "} else if (!conversationMatches(expectation, request, configuration)) {",
        "} else if (!scenarioStateMatches(requestMatchers, expectation, request)) {",
        "} else if (expectation.getPercentage() != null && expectation.getPercentage() < 100) {"
    );

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private Configuration configuration;
    private HttpState httpState;

    @Before
    public void setUp() {
        configuration = configuration().logLevel(Level.INFO);
        Scheduler scheduler = new Scheduler(configuration, new MockServerLogger(configuration, RequestMatchExplainerGateOrderGuardTest.class), true);
        httpState = new HttpState(configuration, new MockServerLogger(configuration, RequestMatchExplainerGateOrderGuardTest.class), scheduler);
    }

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    @Test
    public void servingLoopAndExplainerApplyTheSameGatesInTheSameOrder() throws Exception {
        String servingLoop = between(source("RequestMatchers.java"),
            "for (HttpRequestMatcher httpRequestMatcher : scanList) {", "httpRequestMatcher.setResponseInProgress(true);");
        String explainerLoop = between(source("RequestMatchExplainer.java"),
            "for (HttpRequestMatcher matcher : matchers) {", "node.put(\"position\", position);");

        int servingPosition = -1;
        int explainerPosition = -1;
        for (String[] gate : GATES) {
            int serving = servingLoop.indexOf(gate[1]);
            int explainer = explainerLoop.indexOf(gate[2]);
            assertThat("the serving loop no longer applies the " + gate[0] + " gate with " + gate[1], serving >= 0, is(true));
            assertThat("the explainer no longer applies the " + gate[0] + " gate with " + gate[2], explainer >= 0, is(true));
            assertThat("the serving loop applies the " + gate[0] + " gate out of order", serving >= servingPosition, is(true));
            assertThat("the explainer applies the " + gate[0] + " gate out of order", explainer, greaterThan(explainerPosition));
            servingPosition = serving;
            explainerPosition = explainer;
        }
        assertThat("the serving loop skips an expectation at a gate the explainer does not model: add the gate to "
            + "RequestMatchExplainer.explain and to GATES", occurrences(servingLoop, "continue;"), is(SERVING_GATE_SKIPS));
        assertThat("a serving-loop condition changed: mirror it in RequestMatchExplainer.explain, then update SERVING_CONDITIONS",
            conditions(servingLoop), is(SERVING_CONDITIONS));
        assertThat("an explainer condition changed: check it still mirrors the serving loop, then update EXPLAINER_CONDITIONS",
            conditions(explainerLoop), is(EXPLAINER_CONDITIONS));
    }

    private static List<String> conditions(String loop) {
        List<String> conditions = new java.util.ArrayList<>();
        for (String line : loop.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("if (") || trimmed.startsWith("} else if (")) {
                conditions.add(trimmed);
            }
        }
        return conditions;
    }

    @Test
    public void reportsTheFirstGateTheServingPathAppliesWhenSeveralFail() throws Exception {
        LlmConversationMatcher neverMatches = new LlmConversationMatcher().withLatestMessageContains("never");
        httpState.add(
            new Expectation(request().withPath("/other")).withId("namespaceAndFields").withPriority(60).withNamespace("team-a").thenRespond(response()),
            new Expectation(request().withPath("/other"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MILLISECONDS, 1L), 50)
                .withId("inactiveAndFields").thenRespond(response()),
            new Expectation(request().withPath("/other")).withId("fieldsAndScenario").withPriority(40)
                .withScenarioName("s").withScenarioState("Elsewhere").thenRespond(response()),
            new Expectation(request().withPath("/gates")).withId("conversationAndScenario").withPriority(30)
                .withScenarioName("s").withScenarioState("Elsewhere").thenRespondWithLlm(llmResponse().withConversationMatcher(neverMatches)),
            new Expectation(request().withPath("/gates")).withId("scenarioAndPercentage").withPriority(20).withPercentage(0)
                .withScenarioName("s").withScenarioState("Elsewhere").thenRespond(response()),
            new Expectation(request().withPath("/gates")).withId("percentage").withPriority(10).withPercentage(0).thenRespond(response()),
            new Expectation(request().withPath("/gates")).withId("served").thenRespond(response())
        );
        Thread.sleep(5);
        HttpRequest received = request().withMethod("GET").withPath("/gates");

        JsonNode explanation = explainThenServe(received, "served");

        JsonNode skipped = explanation.path("skippedExpectations");
        assertThat(reasonOf(skipped, "namespaceAndFields"), is("NAMESPACE"));
        assertThat(reasonOf(skipped, "inactiveAndFields"), is("INACTIVE"));
        assertThat(reasonOf(skipped, "fieldsAndScenario"), is("FIELDS_DIFFER"));
        assertThat(reasonOf(skipped, "conversationAndScenario"), is("CONVERSATION"));
        assertThat(reasonOf(skipped, "scenarioAndPercentage"), is("SCENARIO_STATE"));
        assertThat(reasonOf(skipped, "percentage"), is("PERCENTAGE"));
        assertThat(skipped.size(), is(6));
    }

    @Test
    public void agreesWithServingForANotMatcherTheRequestEscapes() throws Exception {
        httpState.add(
            new Expectation(not(request().withPath("/admin"))).withId("notAdmin").withPriority(10).thenRespond(response()),
            new Expectation(request().withPath("/public")).withId("public").thenRespond(response())
        );

        JsonNode explanation = explainThenServe(request().withMethod("GET").withPath("/public"), "notAdmin");

        assertThat(explanation.path("shadowedExpectations").get(0).path("expectationId").asText(), is("public"));
    }

    @Test
    public void explainsANotMatcherThatExcludesTheRequest() throws Exception {
        httpState.add(
            new Expectation(not(request().withPath("/admin"))).withId("notAdmin").withPriority(10).thenRespond(response()),
            new Expectation(request().withPath("/admin").withHeader("X-Role", "root")).withId("root").thenRespond(response())
        );

        JsonNode explanation = explainThenServe(request().withMethod("GET").withPath("/admin"), null);

        JsonNode notAdmin = entry(explanation.path("skippedExpectations"), "notAdmin");
        assertThat(notAdmin.path("reason").asText(), is("NOT_OPERATOR"));
        assertThat(notAdmin.path("explanation").asText(), containsString("not("));
        assertThat(explanation.path("closestExpectationId").asText(), is("root"));
    }

    /** Explains before serving, because serving consumes Times and advances scenarios. */
    private JsonNode explainThenServe(HttpRequest received, String expectedWinner) {
        RequestMatchers requestMatchers = httpState.getRequestMatchers();
        JsonNode explanation = RequestMatchExplainer.explain(objectMapper, requestMatchers, configuration, received, SensitiveValueMatcher.of(null), HttpState.EXPLAIN_REQUEST_EVALUATION_BUDGET);
        Expectation served = requestMatchers.firstMatchingExpectation(received);

        String explainedWinner = explanation.has("matchedExpectation") ? explanation.path("matchedExpectation").path("expectationId").asText() : null;
        assertThat("serving path winner", served == null ? null : served.getId(), is(expectedWinner));
        assertThat("explainer winner, which must equal the serving winner", explainedWinner, is(expectedWinner));
        return explanation;
    }

    private static String reasonOf(JsonNode nodes, String expectationId) {
        return entry(nodes, expectationId).path("reason").asText();
    }

    private static JsonNode entry(JsonNode nodes, String expectationId) {
        for (JsonNode node : nodes) {
            if (expectationId.equals(node.path("expectationId").asText())) {
                return node;
            }
        }
        throw new AssertionError("no entry for expectation '" + expectationId + "' in " + nodes);
    }

    private static String source(String fileName) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src", "main", "java", "org", "mockserver", "mock", fileName)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat("marker not found: " + start, from >= 0, is(true));
        int to = source.indexOf(end, from);
        assertThat("marker not found: " + end, to > from, is(true));
        return source.substring(from, to);
    }

    private static int occurrences(String text, String token) {
        Matcher matcher = Pattern.compile(Pattern.quote(token)).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
