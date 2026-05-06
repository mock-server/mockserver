package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Arrays;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;

public class McpToolRegistryTest {

    private McpToolRegistry toolRegistry;
    private HttpState httpState;
    private LifeCycle server;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        when(server.getLocalPorts()).thenReturn(Arrays.asList(1080));
        when(server.isRunning()).thenReturn(true);

        httpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class));
        toolRegistry = new McpToolRegistry(httpState, server);
        objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    }

    @Test
    public void shouldRegisterAllTools() {
        Map<String, McpToolRegistry.ToolDefinition> tools = toolRegistry.getTools();
        assertThat(tools.containsKey("create_expectation"), is(true));
        assertThat(tools.containsKey("verify_request"), is(true));
        assertThat(tools.containsKey("retrieve_recorded_requests"), is(true));
        assertThat(tools.containsKey("clear_expectations"), is(true));
        assertThat(tools.containsKey("reset"), is(true));
        assertThat(tools.containsKey("get_status"), is(true));
        assertThat(tools.containsKey("verify_request_sequence"), is(true));
        assertThat(tools.containsKey("retrieve_request_responses"), is(true));
        assertThat(tools.containsKey("create_forward_expectation"), is(true));
        assertThat(tools.containsKey("debug_request_mismatch"), is(true));
        assertThat(tools.containsKey("create_expectation_from_openapi"), is(true));
        assertThat(tools.containsKey("stop_server"), is(true));
        assertThat(tools.containsKey("raw_expectation"), is(true));
        assertThat(tools.containsKey("raw_retrieve"), is(true));
        assertThat(tools.containsKey("raw_verify"), is(true));
        assertThat(tools.size(), is(15));
    }

    @Test
    public void shouldHaveToolDefinitionsWithSchemas() {
        for (McpToolRegistry.ToolDefinition tool : toolRegistry.getTools().values()) {
            assertThat(tool.getName(), notNullValue());
            assertThat(tool.getDescription(), notNullValue());
            assertThat(tool.getInputSchema(), notNullValue());
            assertThat(tool.getInputSchema().path("type").asText(), is("object"));
        }
    }

    @Test
    public void shouldCreateExpectation() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/hello");
        params.put("statusCode", 200);
        params.put("responseBody", "world");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("status").asText(), is("created"));
        assertThat(result.path("count").asInt(), is(1));
        assertThat(result.has("id"), is(true));
    }

    @Test
    public void shouldCreateExpectationWithTimes() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/limited");
        params.put("statusCode", 200);
        params.put("times", 5);

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("status").asText(), is("created"));
    }

    @Test
    public void shouldRejectNonNumericTimes() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/invalid-times");
        params.put("statusCode", 200);
        params.put("times", "not-a-number");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'times' must be an integer"));
    }

    @Test
    public void shouldVerifyRequestNotFound() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/not-called");
        params.put("atLeast", 1);

        JsonNode result = toolRegistry.callTool("verify_request", params);
        assertThat(result.path("verified").asBoolean(), is(false));
    }

    @Test
    public void shouldVerifyZeroRequests() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/not-called");
        params.put("atLeast", 0);
        params.put("atMost", 0);

        JsonNode result = toolRegistry.callTool("verify_request", params);
        assertThat(result.path("verified").asBoolean(), is(true));
    }

    @Test
    public void shouldRetrieveEmptyRequests() {
        ObjectNode params = objectMapper.createObjectNode();

        JsonNode result = toolRegistry.callTool("retrieve_recorded_requests", params);
        assertThat(result.path("total").asInt(), is(0));
        assertThat(result.path("requests").isArray(), is(true));
    }

    @Test
    public void shouldClearExpectations() {
        ObjectNode createParams = objectMapper.createObjectNode();
        createParams.put("method", "GET");
        createParams.put("path", "/to-clear");
        createParams.put("statusCode", 200);
        toolRegistry.callTool("create_expectation", createParams);

        ObjectNode clearParams = objectMapper.createObjectNode();
        clearParams.put("type", "ALL");

        JsonNode result = toolRegistry.callTool("clear_expectations", clearParams);
        assertThat(result.path("status").asText(), is("cleared"));
    }

    @Test
    public void shouldReset() {
        JsonNode result = toolRegistry.callTool("reset", objectMapper.createObjectNode());
        assertThat(result.path("status").asText(), is("reset"));
    }

    @Test
    public void shouldGetStatus() {
        JsonNode result = toolRegistry.callTool("get_status", objectMapper.createObjectNode());
        assertThat(result.path("running").asBoolean(), is(true));
        assertThat(result.path("ports").get(0).asInt(), is(1080));
    }

    @Test
    public void shouldVerifyRequestSequence() {
        ObjectNode params = objectMapper.createObjectNode();
        params.putArray("requests");

        JsonNode result = toolRegistry.callTool("verify_request_sequence", params);
        assertThat(result.path("error").asBoolean(), is(true));
    }

    @Test
    public void shouldCreateForwardExpectation() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("path", "/proxy");
        params.put("host", "example.com");
        params.put("port", 8080);
        params.put("scheme", "HTTP");

        JsonNode result = toolRegistry.callTool("create_forward_expectation", params);
        assertThat(result.path("status").asText(), is("created"));
        assertThat(result.path("forwardHost").asText(), is("example.com"));
    }

    @Test
    public void shouldDebugRequestMismatchWithNoExpectations() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/test");

        JsonNode result = toolRegistry.callTool("debug_request_mismatch", params);
        assertThat(result.path("totalExpectations").asInt(), is(0));
        assertThat(result.path("results").isArray(), is(true));
    }

    @Test
    public void shouldDebugRequestMismatchWithExpectation() {
        ObjectNode createParams = objectMapper.createObjectNode();
        createParams.put("method", "POST");
        createParams.put("path", "/expected");
        createParams.put("statusCode", 200);
        toolRegistry.callTool("create_expectation", createParams);

        ObjectNode debugParams = objectMapper.createObjectNode();
        debugParams.put("method", "GET");
        debugParams.put("path", "/wrong");

        JsonNode result = toolRegistry.callTool("debug_request_mismatch", debugParams);
        assertThat(result.path("totalExpectations").asInt(), is(1));
        assertThat(result.path("results").get(0).path("matches").asBoolean(), is(false));
        assertThat(result.path("results").get(0).has("differences"), is(true));
    }

    @Test
    public void shouldReturnNullForUnknownTool() {
        JsonNode result = toolRegistry.callTool("unknown_tool", objectMapper.createObjectNode());
        assertThat(result == null, is(true));
    }

    @Test
    public void shouldHandleRawRetrieve() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "ACTIVE_EXPECTATIONS");
        params.put("format", "JSON");

        JsonNode result = toolRegistry.callTool("raw_retrieve", params);
        assertThat(result.has("data"), is(true));
    }

    @Test
    public void shouldRetrieveRequestResponses() {
        ObjectNode params = objectMapper.createObjectNode();

        JsonNode result = toolRegistry.callTool("retrieve_request_responses", params);
        assertThat(result.path("total").asInt(), is(0));
        assertThat(result.path("requestResponses").isArray(), is(true));
    }

    @Test
    public void shouldHandleRetrieveRecordedRequestsWithFilter() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/filtered");
        params.put("limit", 10);

        JsonNode result = toolRegistry.callTool("retrieve_recorded_requests", params);
        assertThat(result.path("total").asInt(), is(0));
    }

    @Test
    public void shouldRejectNegativeLimit() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("limit", -1);

        JsonNode result = toolRegistry.callTool("retrieve_recorded_requests", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'limit' must be between 1 and 500"));
    }

    @Test
    public void shouldRejectZeroLimit() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("limit", 0);

        JsonNode result = toolRegistry.callTool("retrieve_recorded_requests", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'limit' must be between 1 and 500"));
    }

    @Test
    public void shouldRejectLimitAboveMaximum() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("limit", 501);

        JsonNode result = toolRegistry.callTool("retrieve_recorded_requests", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'limit' must be between 1 and 500"));
    }

    @Test
    public void shouldAcceptLimitAtMaximum() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("limit", 500);

        JsonNode result = toolRegistry.callTool("retrieve_recorded_requests", params);
        assertThat(result.path("total").asInt(), is(0));
        assertThat(result.path("requests").isArray(), is(true));
    }

    @Test
    public void shouldRejectNegativeLimitForRequestResponses() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("limit", -5);

        JsonNode result = toolRegistry.callTool("retrieve_request_responses", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'limit' must be between 1 and 500"));
    }

    @Test
    public void shouldRejectLimitAboveMaximumForRequestResponses() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("limit", 501);

        JsonNode result = toolRegistry.callTool("retrieve_request_responses", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'limit' must be between 1 and 500"));
    }

    @Test
    public void shouldRejectInvalidStatusCodeTooLow() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/bad-status");
        params.put("statusCode", 99);

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'statusCode' must be between 100 and 999"));
    }

    @Test
    public void shouldRejectInvalidStatusCodeTooHigh() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/bad-status");
        params.put("statusCode", 1000);

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'statusCode' must be between 100 and 999"));
    }

    @Test
    public void shouldAcceptValidStatusCodeBoundary() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/good-status-100");
        params.put("statusCode", 100);

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("status").asText(), is("created"));

        ObjectNode params2 = objectMapper.createObjectNode();
        params2.put("method", "GET");
        params2.put("path", "/good-status-999");
        params2.put("statusCode", 999);

        JsonNode result2 = toolRegistry.callTool("create_expectation", params2);
        assertThat(result2.path("status").asText(), is("created"));
    }

    @Test
    public void shouldRejectMalformedTimeToLive() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/ttl-test");
        params.put("timeToLive", "INVALID");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'timeToLive' must be in format '<number> <UNIT>' (e.g., '60 SECONDS')"));
    }

    @Test
    public void shouldRejectFloatTimes() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/float-times");
        params.put("statusCode", 200);
        params.put("times", 3.5);

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'times' must be an integer"));
    }

    @Test
    public void shouldHandleClearWithFilter() {
        ObjectNode createParams = objectMapper.createObjectNode();
        createParams.put("method", "GET");
        createParams.put("path", "/keep");
        createParams.put("statusCode", 200);
        toolRegistry.callTool("create_expectation", createParams);

        ObjectNode createParams2 = objectMapper.createObjectNode();
        createParams2.put("method", "POST");
        createParams2.put("path", "/remove");
        createParams2.put("statusCode", 201);
        toolRegistry.callTool("create_expectation", createParams2);

        ObjectNode clearParams = objectMapper.createObjectNode();
        clearParams.put("method", "POST");
        clearParams.put("path", "/remove");
        clearParams.put("type", "EXPECTATIONS");

        JsonNode result = toolRegistry.callTool("clear_expectations", clearParams);
        assertThat(result.path("status").asText(), is("cleared"));
    }

    @Test
    public void shouldRejectBlankMethod() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "");
        params.put("path", "/test");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'method' is required and must not be blank"));
    }

    @Test
    public void shouldRejectMissingMethod() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("path", "/test");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'method' is required and must not be blank"));
    }

    @Test
    public void shouldRejectBlankPath() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "   ");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'path' is required and must not be blank"));
    }

    @Test
    public void shouldRejectMissingPath() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'path' is required and must not be blank"));
    }

    @Test
    public void shouldRejectNegativeTimes() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/negative-times");
        params.put("statusCode", 200);
        params.put("times", -1);

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'times' must be a non-negative integer"));
    }

    @Test
    public void shouldAcceptZeroTimes() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/zero-times");
        params.put("statusCode", 200);
        params.put("times", 0);

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("status").asText(), is("created"));
    }

    @Test
    public void shouldRejectNonNumericTimeToLiveValue() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/ttl-nan");
        params.put("timeToLive", "abc SECONDS");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'timeToLive' value must be a number"));
    }

    @Test
    public void shouldRejectNegativeTimeToLiveValue() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/ttl-negative");
        params.put("timeToLive", "-5 SECONDS");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'timeToLive' value must be positive"));
    }

    @Test
    public void shouldRejectZeroTimeToLiveValue() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/ttl-zero");
        params.put("timeToLive", "0 SECONDS");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'timeToLive' value must be positive"));
    }

    @Test
    public void shouldRejectInvalidTimeToLiveUnit() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/ttl-bad-unit");
        params.put("timeToLive", "60 FORTNIGHTS");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), containsString("'timeToLive' unit must be one of:"));
    }

    @Test
    public void shouldAcceptValidTimeToLive() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/ttl-valid");
        params.put("timeToLive", "60 SECONDS");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("status").asText(), is("created"));
    }

    @Test
    public void shouldRejectStringStatusCode() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/string-status");
        params.put("statusCode", "abc");

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'statusCode' must be an integer"));
    }

    @Test
    public void shouldNotExposeExceptionDetailsInErrorMessages() {
        // raw_expectation with invalid JSON should return a generic error, not expose internals
        ObjectNode params = objectMapper.createObjectNode();
        params.put("expectation", "not valid json");

        JsonNode result = toolRegistry.callTool("raw_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        String message = result.path("message").asText();
        assertThat(message, is("Failed to create raw expectation"));
        // Should NOT contain any Java exception class names or stack trace details
        assertThat(message, not(containsString("Exception")));
        assertThat(message, not(containsString("at org.")));
    }

    // --- verify_request atLeast/atMost validation ---

    @Test
    public void shouldRejectNonIntegerAtLeast() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/test");
        params.put("atLeast", "abc");

        JsonNode result = toolRegistry.callTool("verify_request", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'atLeast' must be an integer"));
    }

    @Test
    public void shouldRejectNonIntegerAtMost() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/test");
        params.put("atMost", 3.5);

        JsonNode result = toolRegistry.callTool("verify_request", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'atMost' must be an integer"));
    }

    @Test
    public void shouldRejectNegativeAtLeast() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/test");
        params.put("atLeast", -1);

        JsonNode result = toolRegistry.callTool("verify_request", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'atLeast' must be non-negative"));
    }

    @Test
    public void shouldRejectAtMostLessThanAtLeast() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/test");
        params.put("atLeast", 5);
        params.put("atMost", 2);

        JsonNode result = toolRegistry.callTool("verify_request", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'atMost' must be >= 'atLeast'"));
    }

    // --- clear_expectations type validation ---

    @Test
    public void shouldRejectInvalidClearType() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "INVALID");

        JsonNode result = toolRegistry.callTool("clear_expectations", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'type' must be one of: ALL, LOG, EXPECTATIONS"));
    }

    @Test
    public void shouldAcceptValidClearTypes() {
        for (String type : new String[]{"ALL", "LOG", "EXPECTATIONS"}) {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("type", type);

            JsonNode result = toolRegistry.callTool("clear_expectations", params);
            assertThat("type " + type + " should be accepted", result.path("status").asText(), is("cleared"));
        }
    }

    // --- create_forward_expectation validation ---

    @Test
    public void shouldRejectBlankPathForForwardExpectation() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("path", "");
        params.put("host", "example.com");

        JsonNode result = toolRegistry.callTool("create_forward_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'path' is required and must not be blank"));
    }

    @Test
    public void shouldRejectBlankHostForForwardExpectation() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("path", "/proxy");
        params.put("host", "  ");

        JsonNode result = toolRegistry.callTool("create_forward_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'host' is required and must not be blank"));
    }

    @Test
    public void shouldRejectMissingHostForForwardExpectation() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("path", "/proxy");

        JsonNode result = toolRegistry.callTool("create_forward_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'host' is required and must not be blank"));
    }

    @Test
    public void shouldRejectNonIntegerPortForForwardExpectation() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("path", "/proxy");
        params.put("host", "example.com");
        params.put("port", "abc");

        JsonNode result = toolRegistry.callTool("create_forward_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'port' must be an integer"));
    }

    @Test
    public void shouldRejectPortZeroForForwardExpectation() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("path", "/proxy");
        params.put("host", "example.com");
        params.put("port", 0);

        JsonNode result = toolRegistry.callTool("create_forward_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'port' must be between 1 and 65535"));
    }

    @Test
    public void shouldRejectPortAboveMax() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("path", "/proxy");
        params.put("host", "example.com");
        params.put("port", 65536);

        JsonNode result = toolRegistry.callTool("create_forward_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'port' must be between 1 and 65535"));
    }

    @Test
    public void shouldAcceptValidPortBoundaries() {
        ObjectNode params1 = objectMapper.createObjectNode();
        params1.put("path", "/proxy1");
        params1.put("host", "example.com");
        params1.put("port", 1);

        JsonNode result1 = toolRegistry.callTool("create_forward_expectation", params1);
        assertThat(result1.path("status").asText(), is("created"));
        assertThat(result1.path("forwardPort").asInt(), is(1));

        ObjectNode params2 = objectMapper.createObjectNode();
        params2.put("path", "/proxy2");
        params2.put("host", "example.com");
        params2.put("port", 65535);

        JsonNode result2 = toolRegistry.callTool("create_forward_expectation", params2);
        assertThat(result2.path("status").asText(), is("created"));
        assertThat(result2.path("forwardPort").asInt(), is(65535));
    }

    @Test
    public void shouldRejectInvalidSchemeForForwardExpectation() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("path", "/proxy");
        params.put("host", "example.com");
        params.put("scheme", "FTP");

        JsonNode result = toolRegistry.callTool("create_forward_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'scheme' must be HTTP or HTTPS"));
    }

    // --- raw_retrieve type/format validation ---

    @Test
    public void shouldRejectInvalidRawRetrieveType() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "INVALID");

        JsonNode result = toolRegistry.callTool("raw_retrieve", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'type' must be one of: REQUESTS, REQUEST_RESPONSES, RECORDED_EXPECTATIONS, ACTIVE_EXPECTATIONS, LOGS"));
    }

    @Test
    public void shouldRejectInvalidRawRetrieveFormat() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "REQUESTS");
        params.put("format", "XML");

        JsonNode result = toolRegistry.callTool("raw_retrieve", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'format' must be one of: JSON, JAVA, LOG_ENTRIES"));
    }

    @Test
    public void shouldAcceptAllValidRawRetrieveTypes() {
        for (String type : new String[]{"REQUESTS", "REQUEST_RESPONSES", "RECORDED_EXPECTATIONS", "ACTIVE_EXPECTATIONS", "LOGS"}) {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("type", type);
            params.put("format", "JSON");

            JsonNode result = toolRegistry.callTool("raw_retrieve", params);
            assertThat("type " + type + " should be accepted", result.has("data"), is(true));
        }
    }

    // --- verify_request_sequence element validation ---

    @Test
    public void shouldRejectNonObjectElementsInRequestSequence() {
        ObjectNode params = objectMapper.createObjectNode();
        params.putArray("requests").add("not-an-object");

        JsonNode result = toolRegistry.callTool("verify_request_sequence", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("Each element of 'requests' must be an object"));
    }

    // --- create_expectation timeToLive non-textual ---

    @Test
    public void shouldRejectNonTextualTimeToLive() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/ttl-int");
        params.put("timeToLive", 60);

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'timeToLive' must be a string in format '<number> <UNIT>' (e.g., '60 SECONDS')"));
    }

    @Test
    public void shouldRejectBooleanTimeToLive() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("method", "GET");
        params.put("path", "/ttl-bool");
        params.put("timeToLive", true);

        JsonNode result = toolRegistry.callTool("create_expectation", params);
        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), is("'timeToLive' must be a string in format '<number> <UNIT>' (e.g., '60 SECONDS')"));
    }
}
