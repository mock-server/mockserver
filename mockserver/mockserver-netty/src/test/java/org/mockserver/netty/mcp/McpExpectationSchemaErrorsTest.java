package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * The errors create_expectation and raw_expectation return name the exact field to change, so an assistant following
 * the create_mock_from_description prompt can correct its call instead of losing a detail or guessing; a call that
 * fails creates nothing.
 */
public class McpExpectationSchemaErrorsTest {

    private HttpState httpState;
    private McpToolRegistry toolRegistry;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        LifeCycle server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        httpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class));
        toolRegistry = new McpToolRegistry(httpState, server);
        objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    }

    @After
    public void stopHttpState() {
        httpState.stop();
    }

    @Test
    public void createExpectationNamesUnknownParametersWithTheIntendedNameAndCreatesNothing() {
        ObjectNode params = objectMapper.createObjectNode()
            .put("method", "GET")
            .put("path", "/health")
            .put("body", "ok")
            .put("queryParameters", "verbose=true");
        params.putObject("headers").put("Content-Type", "text/plain");

        JsonNode result = toolRegistry.callTool("create_expectation", params);

        assertThat(result.toString(), result.path("error").asBoolean(), is(true));
        assertThat(texts(result.path("unknownParameters")), contains("body", "queryParameters", "headers"));
        assertThat(result.path("suggestions").path("body").asText(), is("responseBody"));
        assertThat(result.path("suggestions").path("headers").asText(), is("responseHeaders"));
        assertThat(result.path("suggestions").has("queryParameters"), is(false));
        assertThat(texts(result.path("acceptedParameters")), hasItem("responseBody"));
        String message = result.path("message").asText();
        assertThat(message, startsWith("create_expectation does not accept: 'body' (did you mean 'responseBody'?), 'queryParameters', 'headers'"));
        assertThat(message, containsString("use raw_expectation"));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void createExpectationSucceedsOnceTheSuggestedNamesAreUsed() {
        ObjectNode params = objectMapper.createObjectNode()
            .put("method", "GET")
            .put("path", "/health")
            .put("responseBody", "ok");
        params.putObject("responseHeaders").put("Content-Type", "text/plain");

        JsonNode result = toolRegistry.callTool("create_expectation", params);

        assertThat(result.toString(), result.path("status").asText(), is("created"));
        JsonNode response = listedExpectations().get(0).path("httpResponse");
        assertThat(response.toString(), response.path("body").asText(), is("ok"));
        assertThat(response.toString(), response.path("headers").path("Content-Type").get(0).asText(), is("text/plain"));
    }

    @Test
    public void createExpectationLetsUnderscorePrefixedMetadataKeysThrough() {
        ObjectNode params = objectMapper.createObjectNode().put("method", "GET").put("path", "/health");
        params.putObject("_meta").put("progressToken", "abc");
        params.putObject("chaos").put("errorStatus", 503).put("_trace", "x");

        JsonNode result = toolRegistry.callTool("create_expectation", params);

        assertThat(result.toString(), result.path("status").asText(), is("created"));
        assertThat(activeExpectationCount(), is(1));
    }

    @Test
    public void createExpectationMatchesAParameterNameWrittenInAnotherCase() {
        JsonNode result = toolRegistry.callTool("create_expectation", objectMapper.createObjectNode()
            .put("method", "GET").put("path", "/health").put("status_code", 204));

        assertThat(result.path("suggestions").path("status_code").asText(), is("statusCode"));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void createExpectationRejectsResponseHeadersThatAreNotAnObject() {
        JsonNode result = toolRegistry.callTool("create_expectation", objectMapper.createObjectNode()
            .put("method", "GET").put("path", "/health").put("responseHeaders", "Content-Type: text/plain"));

        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), startsWith("'responseHeaders' must be an object"));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void createExpectationRejectsAHeaderValueThatIsAnObject() {
        ObjectNode params = objectMapper.createObjectNode().put("method", "GET").put("path", "/health");
        params.putObject("responseHeaders").putObject("X-Trace").put("id", "1");

        JsonNode result = toolRegistry.callTool("create_expectation", params);

        assertThat(result.path("message").asText(), is("'responseHeaders.X-Trace' must be a string or an array of strings"));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void createExpectationKeepsEveryValueOfAMultiValueHeader() {
        ObjectNode params = objectMapper.createObjectNode().put("method", "GET").put("path", "/health");
        params.putObject("responseHeaders").putArray("Set-Cookie").add("a=1").add("b=2");

        JsonNode result = toolRegistry.callTool("create_expectation", params);

        assertThat(result.toString(), result.path("status").asText(), is("created"));
        JsonNode values = listedExpectations().get(0).path("httpResponse").path("headers").path("Set-Cookie");
        assertThat(texts(values), contains("a=1", "b=2"));
    }

    @Test
    public void createExpectationRejectsAMethodThatIsNotAString() {
        JsonNode result = toolRegistry.callTool("create_expectation", objectMapper.createObjectNode()
            .put("method", 1).put("path", "/health"));

        assertThat(result.path("message").asText(), is("'method' must be a string, for example \"GET\""));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void createExpectationNamesUnknownChaosFields() {
        ObjectNode params = objectMapper.createObjectNode().put("method", "GET").put("path", "/health");
        params.putObject("chaos").put("error_status", 503);

        JsonNode result = toolRegistry.callTool("create_expectation", params);

        assertThat(result.path("message").asText(), startsWith("create_expectation 'chaos' does not accept: 'error_status' (did you mean 'errorStatus'?)"));
        assertThat(texts(result.path("unknownParameters")), contains("error_status"));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void createExpectationRejectsChaosThatIsNotAnObject() {
        JsonNode result = toolRegistry.callTool("create_expectation", objectMapper.createObjectNode()
            .put("method", "GET").put("path", "/health").put("chaos", "503"));

        assertThat(result.path("message").asText(), startsWith("'chaos' must be an object"));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void rawExpectationListsEachSchemaErrorByJsonPath() {
        ObjectNode expectation = objectMapper.createObjectNode();
        expectation.putObject("httpRequest").put("path", "/health").put("extra_field", "x");
        expectation.putObject("httpResponse").put("statusCode", "two hundred");

        JsonNode result = toolRegistry.callTool("raw_expectation", objectMapper.createObjectNode().set("expectation", expectation));

        assertThat(result.toString(), result.path("error").asBoolean(), is(true));
        List<String> errors = texts(result.path("validationErrors"));
        assertThat(errors.toString(), errors, hasItem("$.httpRequest.extra_field: is not defined in the schema and the schema does not allow additional properties"));
        assertThat(errors.toString(), errors.stream().anyMatch(error -> error.startsWith("$.httpResponse.statusCode: ")), is(true));
        assertThat(result.path("message").asText(), containsString("validationErrors"));
        assertThat(result.toString(), not(containsString("Exception")));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void rawExpectationNamesTheFailingEntryOfAnArrayAndCreatesNone() {
        ArrayNode expectations = objectMapper.createArrayNode();
        ObjectNode valid = expectations.addObject();
        valid.putObject("httpRequest").put("path", "/one");
        valid.putObject("httpResponse").put("statusCode", 200);
        ObjectNode invalid = expectations.addObject();
        invalid.putObject("httpRequest").put("paht", "/two");
        invalid.putObject("httpResponse").put("statusCode", 200);

        JsonNode result = toolRegistry.callTool("raw_expectation", objectMapper.createObjectNode().set("expectation", expectations));

        List<String> errors = texts(result.path("validationErrors"));
        assertThat(errors.toString(), errors, hasItem("expectation 2: $.httpRequest.paht: is not defined in the schema and the schema does not allow additional properties"));
        assertThat(errors.toString(), errors.stream().noneMatch(error -> error.startsWith("expectation 1:")), is(true));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void rawExpectationExplainsTheExpectedShapeWhenGivenAString() {
        JsonNode result = toolRegistry.callTool("raw_expectation", objectMapper.createObjectNode().put("expectation", "GET /health returns ok"));

        assertThat(result.path("error").asBoolean(), is(true));
        assertThat(result.path("message").asText(), startsWith("'expectation' must be a JSON object in MockServer expectation format"));
        assertThat(activeExpectationCount(), is(0));
    }

    @Test
    public void rawExpectationStillCreatesAValidExpectation() {
        ObjectNode expectation = objectMapper.createObjectNode();
        expectation.putObject("httpRequest").put("path", "/health");
        expectation.putObject("httpResponse").put("statusCode", 200);

        JsonNode result = toolRegistry.callTool("raw_expectation", objectMapper.createObjectNode().set("expectation", expectation));

        assertThat(result.toString(), result.path("status").asText(), is("created"));
        assertThat(activeExpectationCount(), is(1));
    }

    private JsonNode listedExpectations() {
        return toolRegistry.callTool("list_expectations", objectMapper.createObjectNode()).path("expectations");
    }

    private int activeExpectationCount() {
        return httpState.getRequestMatchers().retrieveActiveExpectations(null).size();
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
