package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.UpstreamConnectFailure;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.log.model.LogEntryMessages;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.NO_MATCH_RESPONSE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.badGatewayResponse;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * The MCP failure-diagnosis tools each return what their REST endpoint or core logic returns: {@code explain_forward_failure}
 * the recorded failed forwards with the reason their 502 gave, {@code test_template} the template dry run,
 * {@code lint_expectations} the expectation lint and {@code suggest_expectation_fix} the guarded JSON patches, with
 * output redacted as the event log is and bounded.
 */
public class McpFailureDiagnosisToolsTest {

    private static final String SECRET = "MCP-FORWARD-SECRET-7";

    private final ObjectMapper objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    private final List<HttpState> httpStates = new ArrayList<>();

    @After
    public void stopHttpStates() {
        httpStates.forEach(HttpState::stop);
    }

    // ---- explain_forward_failure ----

    @Test
    public void shouldReturnTheRecentFailedForwardsNewestFirstWithTheReasonTheir502Gave() throws Exception {
        HttpState httpState = newHttpState(configuration().logLevel(Level.INFO));
        logForwardFailure(httpState, "fwd-1", request().withMethod("GET").withPath("/first"), "upstream refused the connection: check the upstream is running", true);
        logPlainNotFound(httpState, "not-a-forward", request().withMethod("GET").withPath("/missing"));
        logForwardFailure(httpState, "fwd-2", request().withMethod("POST").withPath("/second"), "failed to connect to remote socket while forwarding request", false);
        McpToolRegistry registry = new McpToolRegistry(httpState, server());

        JsonNode result = awaitTool(registry, "explain_forward_failure", objectMapper.createObjectNode(), r -> r.path("forwardFailureCount").asInt() == 2);

        JsonNode newest = result.path("forwardFailures").get(0);
        assertThat(newest.path("correlationId").asText(), is("fwd-2"));
        assertThat(newest.path("method").asText(), is("POST"));
        assertThat(newest.path("path").asText(), is("/second"));
        assertThat(newest.path("statusCode").asInt(), is(502));
        assertThat(newest.path("reason").asText(), is("failed to connect to remote socket while forwarding request"));
        assertThat(newest.path("causeNamed").asBoolean(), is(false));
        assertThat(newest.path("hint").asText(), containsString("retrieve_logs"));
        JsonNode older = result.path("forwardFailures").get(1);
        assertThat(older.path("correlationId").asText(), is("fwd-1"));
        assertThat(older.path("reason").asText(), is("upstream refused the connection: check the upstream is running"));
        assertThat(older.path("causeNamed").asBoolean(), is(true));
        assertThat(older.path("forwardLoopSuspected").asBoolean(), is(false));
        assertThat("a plain 404 is not a failed forward", result.toString(), not(containsString("not-a-forward")));
    }

    @Test
    public void shouldReturnOneFailedForwardByCorrelationIdAndRespectTheLimit() throws Exception {
        HttpState httpState = newHttpState(configuration().logLevel(Level.INFO));
        for (int i = 0; i < 3; i++) {
            logForwardFailure(httpState, "fwd-" + i, request().withPath("/p" + i), "reason " + i, true);
        }
        McpToolRegistry registry = new McpToolRegistry(httpState, server());
        awaitTool(registry, "explain_forward_failure", objectMapper.createObjectNode().put("limit", 20), r -> r.path("forwardFailureCount").asInt() == 3);

        JsonNode one = callTool(registry, "explain_forward_failure", objectMapper.createObjectNode().put("correlationId", "fwd-1"));
        JsonNode limited = callTool(registry, "explain_forward_failure", objectMapper.createObjectNode().put("limit", 2));

        assertThat(one.path("forwardFailureCount").asInt(), is(1));
        assertThat(one.path("forwardFailures").get(0).path("reason").asText(), is("reason 1"));
        assertThat(limited.path("forwardFailureCount").asInt(), is(2));
        assertThat(limited.path("forwardFailures").get(0).path("correlationId").asText(), is("fwd-2"));
    }

    @Test
    public void shouldReturnAToolErrorForACorrelationIdWithNoFailedForward() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        JsonNode unknown = callTool(registry, "explain_forward_failure", objectMapper.createObjectNode().put("correlationId", "never-forwarded"));
        JsonNode none = callTool(registry, "explain_forward_failure", objectMapper.createObjectNode());
        JsonNode badLimit = callTool(registry, "explain_forward_failure", objectMapper.createObjectNode().put("limit", 21));

        assertThat(unknown.path("error").asBoolean(false), is(true));
        assertThat(unknown.path("message").asText(), containsString("no failed forward is recorded with this correlationId"));
        assertThat(unknown.path("correlationId").asText(), is("never-forwarded"));
        assertThat(none.path("forwardFailureCount").asInt(), is(0));
        assertThat(none.path("note").asText(), containsString("logLevel INFO"));
        assertThat(badLimit.path("message").asText(), is("'limit' must be between 1 and 20"));
    }

    @Test
    public void shouldRedactAForwardFailureReasonAsTheEventLogDoes() throws Exception {
        String redacted = forwardFailureQuotingACredential(configuration().logLevel(Level.INFO).redactSecretsInLog(true));
        String unredacted = forwardFailureQuotingACredential(configuration().logLevel(Level.INFO).redactSecretsInLog(false));

        assertThat(redacted, containsString("upstream rejected"));
        assertThat(redacted, not(containsString(SECRET)));
        assertThat("the log redaction setting, not the tool, decides", unredacted, containsString(SECRET));
    }

    @Test
    public void shouldCapALongReasonAndFlagALoopBackToMockServer() throws Exception {
        HttpState httpState = newHttpState(configuration().logLevel(Level.INFO));
        logForwardFailure(httpState, "long", request().withPath("/long"), "x".repeat(5000), false);
        logForwardFailure(httpState, "loop", request().withPath("/loop"), "failed to connect; " + UpstreamConnectFailure.FORWARD_LOOP, true);
        McpToolRegistry registry = new McpToolRegistry(httpState, server());

        JsonNode result = awaitTool(registry, "explain_forward_failure", objectMapper.createObjectNode(), r -> r.path("forwardFailureCount").asInt() == 2);

        JsonNode loop = result.path("forwardFailures").get(0);
        JsonNode longReason = result.path("forwardFailures").get(1);
        assertThat(loop.path("forwardLoopSuspected").asBoolean(), is(true));
        assertThat(longReason.path("reason").asText().length(), is(2048));
        assertThat(longReason.path("reasonTruncated").asBoolean(), is(true));
        assertThat(longReason.path("forwardLoopSuspected").asBoolean(), is(false));
    }

    // ---- test_template ----

    @Test
    public void shouldDryRunATemplateAgainstASampleRequest() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());
        ObjectNode params = objectMapper.createObjectNode()
            .put("templateType", "VELOCITY")
            .put("template", "{ \"statusCode\": 201, \"body\": \"$request.path\" }");
        params.putObject("request").put("path", "/sample");

        JsonNode result = callTool(registry, "test_template", params);

        assertThat(result.toString(), result.path("valid").asBoolean(), is(true));
        assertThat(result.path("renderedResponse").path("statusCode").asInt(), is(201));
        assertThat(result.path("renderedResponse").path("body").asText(), is("/sample"));
    }

    @Test
    public void shouldReportATemplateFailureWithItsPosition() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        JsonNode result = callTool(registry, "test_template", objectMapper.createObjectNode()
            .put("templateType", "VELOCITY")
            .put("template", "{\n  \"statusCode\": 200,\n  #if {\n}"));

        assertThat(result.toString(), result.path("error").isObject(), is(true));
        assertThat(result.path("valid").asBoolean(), is(false));
        assertThat(result.path("error").path("line").asInt(), is(3));
        assertThat(result.path("error").path("column").asInt(), is(7));
    }

    @Test
    public void shouldReturnTheDryRunsInputErrorAsAToolError() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        JsonNode result = callTool(registry, "test_template", objectMapper.createObjectNode().put("template", "x"));

        assertThat(result.path("error").asBoolean(false), is(true));
        assertThat(result.path("message").asText(), is("templateType must be VELOCITY, MUSTACHE or JAVASCRIPT"));
    }

    // ---- lint_expectations ----

    @Test
    public void shouldLintTheActiveExpectations() throws Exception {
        HttpState httpState = newHttpState(configuration());
        httpState.add(
            new Expectation(request().withPath("/orders")).withId("broad").withPriority(10).thenRespond(response()),
            new Expectation(request().withMethod("GET").withPath("/orders")).withId("narrow").thenRespond(response())
        );

        JsonNode result = callTool(new McpToolRegistry(httpState, server()), "lint_expectations", objectMapper.createObjectNode());

        assertThat(result.toString(), result.path("expectationsChecked").asInt(), is(2));
        JsonNode shadowed = issue(result, "SHADOWED");
        assertThat(shadowed.path("expectationId").asText(), is("narrow"));
        assertThat(shadowed.path("relatedExpectationId").asText(), is("broad"));
    }

    @Test
    public void shouldLintExpectationsPassedInWithoutStoringThem() throws Exception {
        HttpState httpState = newHttpState(configuration());
        ObjectNode params = objectMapper.createObjectNode();
        params.putArray("expectations").add(objectMapper.readTree("{\"httpRequest\":{\"path\":\"orders\"},\"httpResponse\":{}}"));

        JsonNode result = callTool(new McpToolRegistry(httpState, server()), "lint_expectations", params);

        assertThat(result.toString(), issue(result, "PATH_MISSING_LEADING_SLASH").path("index").asInt(), is(0));
        assertThat("linting must not store the expectations", httpState.getRequestMatchers().retrieveActiveExpectations(null).size(), is(0));
    }

    @Test
    public void shouldReturnAtMostMaxIssuesWithErrorsFirstAndTheFullCounts() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());
        ObjectNode params = objectMapper.createObjectNode().put("maxIssues", 1);
        params.putArray("expectations")
            .add(objectMapper.readTree("{\"httpRequest\":{\"path\":\"orders\"},\"httpResponse\":{}}"))
            .add(objectMapper.readTree("{\"httpRequest\":{\"path\":\"items\"},\"httpResponse\":{}}"))
            .add(objectMapper.readTree("{\"httpRequest\":{\"path\":\"/x\"}}"));

        JsonNode result = callTool(registry, "lint_expectations", params);

        assertThat(result.toString(), result.path("issues").size(), is(1));
        assertThat("the error, found last, is returned first", result.path("issues").get(0).path("rule").asText(), is("INVALID_EXPECTATION"));
        assertThat(result.path("errorCount").asInt(), is(1));
        assertThat(result.path("warningCount").asInt(), is(2));
        assertThat(result.path("issueCount").asInt(), is(3));
        assertThat(result.path("issuesReturned").asInt(), is(1));
        assertThat(result.path("issuesOmitted").asInt(), is(2));
    }

    @Test
    public void shouldRejectExpectationsThatAreNotAnObjectOrArray() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        JsonNode result = callTool(registry, "lint_expectations", objectMapper.createObjectNode().put("expectations", "GET /orders"));

        assertThat(result.path("error").asBoolean(false), is(true));
        assertThat(result.path("message").asText(), containsString("'expectations' must be"));
    }

    // ---- suggest_expectation_fix ----

    @Test
    public void shouldSuggestAGuardedPatchForTheCloseExpectationsOnly() throws Exception {
        HttpState httpState = newHttpState(configuration());
        httpState.add(new Expectation(request().withMethod("GET").withPath("/api/order").withHeader("X-Tenant", "blue"))
            .withId("near").thenRespond(response()));
        for (int i = 0; i < 6; i++) {
            httpState.add(new Expectation(request().withMethod("POST").withPath("/other" + i).withHeader("X-Other", "v"))
                .withId("far" + i).thenRespond(response()));
        }
        logUnmatched(httpState, request().withMethod("GET").withPath("/api/orders").withHeader("X-Tenant", "green"));
        McpToolRegistry registry = new McpToolRegistry(httpState, server());

        JsonNode result = awaitTool(registry, "suggest_expectation_fix", objectMapper.createObjectNode(), r -> r.path("unmatchedRequestCount").asInt() >= 1);

        JsonNode closest = result.path("unmatchedRequests").get(0).path("closestExpectations");
        assertThat(result.toString(), closest.size(), is(5));
        assertThat(result.path("suggestedFixCount").asInt(), is(5));
        JsonNode nearest = closest.get(0);
        assertThat(nearest.path("expectationId").asText(), is("near"));
        assertThat(nearest.path("suggestedFix").path("matchesAfterPatch").asBoolean(), is(true));
        assertThat(nearest.path("suggestedFix").path("patch").get(0).path("op").asText(), is("test"));
        for (JsonNode expectation : closest) {
            assertThat(expectation.toString(), expectation.has("suggestedFix"), is(true));
        }
        assertThat("suggesting must never apply the fix", httpState.firstMatchingExpectation(request().withMethod("GET").withPath("/api/orders").withHeader("X-Tenant", "green")), is((Expectation) null));
    }

    @Test
    public void shouldKeepOnlyTheFixesForTheNamedExpectation() throws Exception {
        HttpState httpState = newHttpState(configuration());
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/api/order")).withId("near").thenRespond(response()),
            new Expectation(request().withMethod("GET").withPath("/api/item")).withId("other").thenRespond(response())
        );
        logUnmatched(httpState, request().withMethod("GET").withPath("/api/orders"));
        McpToolRegistry registry = new McpToolRegistry(httpState, server());
        awaitTool(registry, "suggest_expectation_fix", objectMapper.createObjectNode(), r -> r.path("unmatchedRequestCount").asInt() >= 1);

        JsonNode named = callTool(registry, "suggest_expectation_fix", objectMapper.createObjectNode().put("expectationId", "other"));
        JsonNode missing = callTool(registry, "suggest_expectation_fix", objectMapper.createObjectNode().put("expectationId", "absent"));

        JsonNode closest = named.path("unmatchedRequests").get(0).path("closestExpectations");
        assertThat(named.toString(), closest.size(), is(1));
        assertThat(closest.get(0).path("expectationId").asText(), is("other"));
        assertThat(missing.path("unmatchedRequestCount").asInt(), is(0));
        assertThat(missing.path("note").asText(), containsString("'absent'"));
    }

    @Test
    public void shouldRejectAnExpectationIdThatIsNotANonBlankString() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        JsonNode blank = callTool(registry, "suggest_expectation_fix", objectMapper.createObjectNode().put("expectationId", " "));
        JsonNode number = callTool(registry, "suggest_expectation_fix", objectMapper.createObjectNode().put("expectationId", 7));

        assertThat(blank.path("error").asBoolean(false), is(true));
        assertThat(blank.path("message").asText(), is("'expectationId' must be a non-blank string"));
        assertThat(number.path("error").asBoolean(false), is(true));
        assertThat(number.path("message").asText(), is("'expectationId' must be a non-blank string"));
    }

    @Test
    public void shouldNotCopyARedactedRequestValueIntoASuggestedFix() throws Exception {
        HttpState httpState = newHttpState(configuration().redactSecretsInLog(true));
        httpState.add(new Expectation(request().withMethod("GET").withPath("/api").withHeader("Authorization", "Bearer expected-value"))
            .withId("auth").thenRespond(response()));
        logUnmatched(httpState, request().withMethod("GET").withPath("/api").withHeader("Authorization", "Bearer " + SECRET));
        McpToolRegistry registry = new McpToolRegistry(httpState, server());

        JsonNode result = awaitTool(registry, "suggest_expectation_fix", objectMapper.createObjectNode(), r -> r.path("unmatchedRequestCount").asInt() >= 1);

        assertThat(result.toString(), result.path("suggestedFixCount").asInt(), is(1));
        assertThat(result.toString(), not(containsString(SECRET)));
    }

    // ---- classification ----

    @Test
    public void shouldClassifyTheReadOnlyDiagnosisToolsAsReadsAndTheTemplateDryRunAsAMutation() {
        assertThat(McpToolRegistry.isMutatingTool("explain_forward_failure"), is(false));
        assertThat(McpToolRegistry.isMutatingTool("lint_expectations"), is(false));
        assertThat(McpToolRegistry.isMutatingTool("suggest_expectation_fix"), is(false));
        assertThat("it runs the template code it is sent, as PUT /mockserver/template/test does", McpToolRegistry.isMutatingTool("test_template"), is(true));
    }

    // ---- helpers ----

    private String forwardFailureQuotingACredential(Configuration configuration) throws Exception {
        HttpState httpState = newHttpState(configuration);
        logForwardFailure(httpState, "fwd-secret", request().withMethod("GET").withPath("/secured").withHeader("Authorization", "Bearer " + SECRET),
            "upstream rejected Bearer " + SECRET, false);
        McpToolRegistry registry = new McpToolRegistry(httpState, server());
        return awaitTool(registry, "explain_forward_failure", objectMapper.createObjectNode().put("correlationId", "fwd-secret"), r -> r.path("forwardFailureCount").asInt() == 1).toString();
    }

    private HttpState newHttpState(Configuration configuration) {
        MockServerLogger logger = new MockServerLogger(configuration, McpFailureDiagnosisToolsTest.class);
        HttpState httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
        httpStates.add(httpState);
        return httpState;
    }

    private static LifeCycle server() {
        LifeCycle server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        when(server.getLocalPorts()).thenReturn(List.of(1080));
        when(server.isRunning()).thenReturn(true);
        return server;
    }

    /** As the forward action logs a failed forward: the request, the error and the 502, whose body is the reason when the cause is named. */
    private static void logForwardFailure(HttpState httpState, String correlationId, HttpRequest request, String error, boolean causeNamed) {
        HttpResponse badGateway = causeNamed ? badGatewayResponse().withBody(error) : badGatewayResponse();
        httpState.log(new LogEntry().setType(NO_MATCH_RESPONSE).setLogLevel(Level.INFO).setCorrelationId(correlationId)
            .setHttpRequest(request).setHttpResponse(badGateway)
            .setMessageFormat(LogEntryMessages.FORWARD_FAILURE_MESSAGE_FORMAT).setArguments(request, error, badGateway));
    }

    private static void logPlainNotFound(HttpState httpState, String correlationId, HttpRequest request) {
        httpState.log(new LogEntry().setType(NO_MATCH_RESPONSE).setLogLevel(Level.INFO).setCorrelationId(correlationId)
            .setHttpRequest(request).setHttpResponse(notFoundResponse())
            .setMessageFormat(LogEntryMessages.NO_MATCH_RESPONSE_NO_EXPECTATION_MESSAGE_FORMAT).setArguments(request, notFoundResponse()));
    }

    private static void logUnmatched(HttpState httpState, HttpRequest request) {
        httpState.log(new LogEntry().setType(NO_MATCH_RESPONSE).setLogLevel(Level.INFO)
            .setHttpRequest(request).setHttpResponse(notFoundResponse())
            .setMessageFormat(LogEntryMessages.NO_MATCH_RESPONSE_NO_EXPECTATION_MESSAGE_FORMAT).setArguments(request, notFoundResponse()));
    }

    /** The event log is written asynchronously: call again until {@code ready} holds, within a deadline. */
    private JsonNode awaitTool(McpToolRegistry registry, String name, JsonNode params, Predicate<JsonNode> ready) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        JsonNode result = callTool(registry, name, params);
        while (!ready.test(result) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            result = callTool(registry, name, params);
        }
        assertThat(result.toString(), ready.test(result), is(true));
        return result;
    }

    private JsonNode callTool(McpToolRegistry registry, String name, JsonNode params) {
        return registry.callTool(name, params);
    }

    private static JsonNode issue(JsonNode lintResult, String rule) {
        for (JsonNode issue : lintResult.path("issues")) {
            if (rule.equals(issue.path("rule").asText())) {
                return issue;
            }
        }
        throw new AssertionError("no " + rule + " issue in " + lintResult);
    }
}
