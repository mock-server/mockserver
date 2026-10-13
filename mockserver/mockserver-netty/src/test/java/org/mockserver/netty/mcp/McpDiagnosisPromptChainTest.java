package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;

/**
 * Runs the tool chains that the why_did_verify_fail and fix_my_expectation prompts tell an assistant to follow, and
 * checks that each tool returns the facts, under the field names, that the prompt says it does.
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
    public void fixChainFindsTheClosestExpectationAndReplacesItInPlaceById() throws Exception {
        String id = toolRegistry.callTool("create_expectation", objectMapper.createObjectNode()
            .put("method", "POST").put("path", "/api/users").put("statusCode", 201)).path("id").asText();

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

        JsonNode afterFix = toolRegistry.callTool("list_expectations", objectMapper.createObjectNode());
        assertThat(afterFix.path("count").asInt(), is(1));
        assertThat(afterFix.path("expectations").get(0).path("id").asText(), is(id));
        JsonNode recheck = toolRegistry.callTool("debug_request_mismatch", objectMapper.createObjectNode()
            .put("method", "GET").put("path", "/api/users"));
        assertThat(recheck.toString(), recheck.path("results").get(0).path("matches").asBoolean(), is(true));
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
