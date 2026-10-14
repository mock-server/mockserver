package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.log.model.LogEntryMessages;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.NO_MATCH_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.badGatewayResponse;
import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * Runs the tool chains that the why_did_verify_fail, fix_my_expectation and why_forward_failed prompts tell an assistant
 * to follow, including their fallbacks, and checks that each tool returns the facts, under the field names, that the
 * prompt says it does.
 */
public class McpDiagnosisPromptChainTest {

    private HttpState httpState;
    private McpToolRegistry toolRegistry;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        LifeCycle server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        when(server.getLocalPorts()).thenReturn(Arrays.asList(1080));
        when(server.isRunning()).thenReturn(true);
        httpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class));
        toolRegistry = new McpToolRegistry(httpState, server);
        objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    }

    @After
    public void stopHttpState() {
        httpState.stop();
    }

    @Test
    public void verifyChainReportsTheCountsTheReceivedRequestAndTheClosestMatchDiff() throws Exception {
        httpState.log(new LogEntry()
            .setType(RECEIVED_REQUEST)
            .setLogLevel(org.slf4j.event.Level.INFO)
            .setHttpRequest(request().withMethod("GET").withPath("/api/user"))
            .setMessageFormat("received request:{}")
            .setArguments(request().withMethod("GET").withPath("/api/user"))
        );
        pollUntil(() -> toolRegistry.callTool("retrieve_recorded_requests", objectMapper.createObjectNode().put("method", "GET"))
            .path("requests").size() >= 1);

        JsonNode verify = toolRegistry.callTool("verify_request", objectMapper.createObjectNode()
            .put("method", "GET").put("path", "/api/users").put("atLeast", 1));

        assertThat(verify.toString(), verify.path("verified").asBoolean(), is(false));
        String message = verify.path("message").asText();
        assertThat(message, containsString("Request not found at least once"));
        assertThat(message, containsString("/api/user"));
        assertThat(message, containsString("closest match diff"));
        assertThat(message, containsString("path"));

        JsonNode received = toolRegistry.callTool("retrieve_recorded_requests", objectMapper.createObjectNode().put("method", "GET"));
        assertThat(received.toString(), received.path("requests").get(0).path("path").asText(), is("/api/user"));
    }

    @Test
    public void fixChainAppliesTheSuggestedPatchToTheListedExpectationAndReplacesItInPlaceById() throws Exception {
        String id = toolRegistry.callTool("create_expectation", objectMapper.createObjectNode()
            .put("method", "POST").put("path", "/api/users").put("statusCode", 201)).path("id").asText();
        logUnmatched(request().withMethod("GET").withPath("/api/users"));

        JsonNode suggested = awaitTool("suggest_expectation_fix", objectMapper.createObjectNode().put("expectationId", id),
            result -> result.path("suggestedFixCount").asInt() >= 1);
        JsonNode unmatched = suggested.path("unmatchedRequests").get(0);
        assertThat(suggested.toString(), unmatched.path("method").asText(), is("GET"));
        assertThat(unmatched.path("path").asText(), is("/api/users"));
        JsonNode closest = unmatched.path("closestExpectations").get(0);
        assertThat(closest.path("expectationId").asText(), is(id));
        JsonNode fix = closest.path("suggestedFix");
        assertThat(fix.toString(), fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertThat(fix.path("unpatchedFields").size(), is(0));
        assertThat("each change is guarded by a test of the current value", fix.path("patch").get(0).path("op").asText(), is("test"));

        JsonNode listed = toolRegistry.callTool("list_expectations", objectMapper.createObjectNode());
        JsonNode expectation = listed.path("expectations").get(0);
        assertThat(expectation.path("id").asText(), is(id));
        JsonNode patched = JsonPatch.fromJson(fix.path("patch")).apply(expectation);
        assertThat(patched.toString(), patched.path("httpRequest").path("method").asText(), is("GET"));

        JsonNode applied = toolRegistry.callTool("raw_expectation", objectMapper.createObjectNode().set("expectation", patched));
        assertThat(applied.toString(), applied.path("error").asBoolean(), is(false));

        assertReplacedInPlaceAndNowMatches(id);
    }

    @Test
    public void fixChainFallsBackToAHandMadeChangeWhenNoFixIsSuggested() throws Exception {
        String id = toolRegistry.callTool("create_expectation", objectMapper.createObjectNode()
            .put("method", "POST").put("path", "/api/users").put("statusCode", 201)).path("id").asText();

        JsonNode suggested = toolRegistry.callTool("suggest_expectation_fix", objectMapper.createObjectNode());
        assertThat(suggested.toString(), suggested.path("error").asBoolean(), is(false));
        assertThat(suggested.path("suggestedFixCount").asInt(), is(0));
        assertThat(suggested.path("note").asText().isEmpty(), is(false));

        JsonNode debug = toolRegistry.callTool("debug_request_mismatch", objectMapper.createObjectNode()
            .put("method", "GET").put("path", "/api/users"));
        JsonNode closest = debug.path("results").get(0);
        assertThat(debug.toString(), closest.path("expectationId").asText(), is(id));
        assertThat(closest.path("matches").asBoolean(), is(false));
        assertThat(closest.path("differences").has("method"), is(true));
        assertThat(closest.path("remediation").has("method"), is(true));

        JsonNode listed = toolRegistry.callTool("list_expectations", objectMapper.createObjectNode());
        ObjectNode expectation = (ObjectNode) listed.path("expectations").get(0).deepCopy();
        assertThat(expectation.path("id").asText(), is(id));
        ((ObjectNode) expectation.path("httpRequest")).put("method", "GET");

        JsonNode applied = toolRegistry.callTool("raw_expectation", objectMapper.createObjectNode().set("expectation", expectation));
        assertThat(applied.toString(), applied.path("error").asBoolean(), is(false));

        assertReplacedInPlaceAndNowMatches(id);
    }

    @Test
    public void forwardChainFallsBackToTheRequestLifecycleWhenTheCauseIsNotNamed() throws Exception {
        HttpRequest forwarded = request().withMethod("GET").withPath("/inventory");
        httpState.log(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(Level.INFO).setCorrelationId("fwd-raw")
            .setHttpRequest(forwarded).setMessageFormat("received request:{}").setArguments(forwarded));
        httpState.log(new LogEntry().setType(NO_MATCH_RESPONSE).setLogLevel(Level.INFO).setCorrelationId("fwd-raw")
            .setHttpRequest(forwarded).setHttpResponse(badGatewayResponse())
            .setMessageFormat(LogEntryMessages.FORWARD_FAILURE_MESSAGE_FORMAT)
            .setArguments(forwarded, "failed to connect to remote socket while forwarding request", badGatewayResponse()));

        JsonNode failures = awaitTool("explain_forward_failure", objectMapper.createObjectNode().put("limit", 20),
            result -> result.path("forwardFailureCount").asInt() == 1);
        JsonNode failure = failures.path("forwardFailures").get(0);
        assertThat(failure.path("method").asText(), is("GET"));
        assertThat(failure.path("path").asText(), is("/inventory"));
        assertThat(failure.path("statusCode").asInt(), is(502));
        assertThat(failure.path("causeNamed").asBoolean(), is(false));
        assertThat(failure.path("reason").asText(), is("failed to connect to remote socket while forwarding request"));

        JsonNode lifecycle = awaitTool("retrieve_logs", objectMapper.createObjectNode().put("correlationId", failure.path("correlationId").asText()),
            result -> result.path("logEntries").size() == 2);
        for (JsonNode entry : lifecycle.path("logEntries")) {
            assertThat(entry.toString(), entry.path("correlationId").asText(), is("fwd-raw"));
        }
    }

    private void assertReplacedInPlaceAndNowMatches(String id) {
        JsonNode afterFix = toolRegistry.callTool("list_expectations", objectMapper.createObjectNode());
        assertThat(afterFix.path("count").asInt(), is(1));
        assertThat(afterFix.path("expectations").get(0).path("id").asText(), is(id));
        JsonNode recheck = toolRegistry.callTool("debug_request_mismatch", objectMapper.createObjectNode()
            .put("method", "GET").put("path", "/api/users"));
        assertThat(recheck.toString(), recheck.path("results").get(0).path("matches").asBoolean(), is(true));
    }

    private void logUnmatched(HttpRequest unmatched) {
        httpState.log(new LogEntry().setType(NO_MATCH_RESPONSE).setLogLevel(Level.INFO)
            .setHttpRequest(unmatched).setHttpResponse(notFoundResponse())
            .setMessageFormat(LogEntryMessages.NO_MATCH_RESPONSE_NO_EXPECTATION_MESSAGE_FORMAT).setArguments(unmatched, notFoundResponse()));
    }

    /** The event log is written asynchronously: call again until {@code ready} holds, within a deadline. */
    private JsonNode awaitTool(String name, JsonNode params, Predicate<JsonNode> ready) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        JsonNode result = toolRegistry.callTool(name, params);
        while (!ready.test(result) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            result = toolRegistry.callTool(name, params);
        }
        assertThat(result.toString(), ready.test(result), is(true));
        return result;
    }

    private static void pollUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within 10 seconds");
            }
            Thread.sleep(50);
        }
    }
}
