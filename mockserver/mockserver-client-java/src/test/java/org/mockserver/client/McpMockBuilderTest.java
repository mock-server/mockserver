package org.mockserver.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockserver.client.McpMockBuilder.mcpMock;

public class McpMockBuilderTest {

    @Test
    public void shouldBuildMinimalMcpMock() {
        Expectation[] expectations = mcpMock().build();
        assertThat(expectations.length, is(3));
    }

    @Test
    public void shouldBuildWithCustomPath() {
        Expectation[] expectations = mcpMock("/custom/mcp").build();
        assertThat(expectations.length, is(3));
        for (Expectation exp : expectations) {
            HttpRequest request = (HttpRequest) exp.getHttpRequest();
            assertThat(request.getPath().getValue(), is("/custom/mcp"));
        }
    }

    @Test
    public void shouldBuildWithTools() {
        Expectation[] expectations = mcpMock()
            .withTool("get_weather")
                .withDescription("Get weather for a city")
                .withInputSchema("{\"type\": \"object\", \"properties\": {\"city\": {\"type\": \"string\"}}}")
                .respondingWith("72F and sunny")
                .and()
            .withTool("search")
                .withDescription("Search the knowledge base")
                .respondingWith("No results found")
                .and()
            .build();

        assertThat(expectations.length, is(6));
    }

    @Test
    public void shouldBuildWithResources() {
        Expectation[] expectations = mcpMock()
            .withResource("config://app")
                .withName("App Config")
                .withDescription("Application configuration")
                .withMimeType("application/json")
                .withContent("{\"debug\": true}")
                .and()
            .build();

        assertThat(expectations.length, is(5));
    }

    @Test
    public void shouldBuildWithPrompts() {
        Expectation[] expectations = mcpMock()
            .withPrompt("code_review")
                .withDescription("Review code changes")
                .withArgument("language", "Programming language", true)
                .respondingWith("user", "Please review this code")
                .respondingWith("assistant", "I'll review the code now.")
                .and()
            .build();

        assertThat(expectations.length, is(5));
    }

    @Test
    public void shouldBuildWithAllCapabilities() {
        Expectation[] expectations = mcpMock("/mcp")
            .withServerName("TestServer")
            .withServerVersion("2.0.0")
            .withProtocolVersion("2025-03-26")
            .withTool("tool1")
                .withDescription("First tool")
                .respondingWith("tool1 result")
                .and()
            .withTool("tool2")
                .withDescription("Second tool")
                .respondingWith("tool2 result", true)
                .and()
            .withResource("res://data")
                .withName("Data")
                .withContent("some data")
                .and()
            .withPrompt("prompt1")
                .withDescription("A prompt")
                .respondingWith("user", "hello")
                .and()
            .build();

        assertThat(expectations.length, is(10));
    }

    @Test
    public void shouldUseVelocityTemplatesForJsonRpcResponses() {
        Expectation[] expectations = mcpMock().build();

        Expectation initExp = expectations[0];
        assertNotNull(initExp.getHttpResponseTemplate());
        assertThat(initExp.getHttpResponseTemplate().getTemplateType(), is(HttpTemplate.TemplateType.VELOCITY));
        String template = initExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("jsonRpcRawId"));
        assertThat(template, containsString("statusCode"));
    }

    @Test
    public void shouldUseStaticResponseForNotificationsInitialized() {
        Expectation[] expectations = mcpMock().build();

        Expectation notifExp = expectations[2];
        assertNotNull(notifExp.getHttpResponse());
        assertThat(notifExp.getHttpResponse().getStatusCode(), is(200));
    }

    @Test
    public void shouldAutoEnableCapabilityWhenToolAdded() {
        Expectation[] expectations = mcpMock()
            .withTool("myTool")
                .respondingWith("result")
                .and()
            .build();

        List<String> templates = Arrays.stream(expectations)
            .filter(e -> e.getHttpResponseTemplate() != null)
            .map(e -> e.getHttpResponseTemplate().getTemplate())
            .collect(Collectors.toList());

        boolean hasToolsList = templates.stream().anyMatch(t -> t.contains("tools"));
        assertTrue("Should have tools/list expectation", hasToolsList);
    }

    @Test
    public void shouldIncludeToolsCapabilityWithoutTools() {
        Expectation[] expectations = mcpMock()
            .withToolsCapability()
            .build();

        assertThat(expectations.length, is(4));
    }

    @Test
    public void shouldBuildToolWithErrorResponse() {
        Expectation[] expectations = mcpMock()
            .withTool("failing_tool")
                .respondingWith("Something went wrong", true)
                .and()
            .build();

        Expectation toolCallExp = expectations[expectations.length - 1];
        String template = toolCallExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("isError"));
    }

    @Test
    public void shouldSetServerInfoInInitializeResponse() {
        Expectation[] expectations = mcpMock()
            .withServerName("MyServer")
            .withServerVersion("3.0.0")
            .withProtocolVersion("2024-11-05")
            .build();

        Expectation initExp = expectations[0];
        String template = initExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("MyServer"));
        assertThat(template, containsString("3.0.0"));
        assertThat(template, containsString("2024-11-05"));
    }

    @Test
    public void shouldEscapeVelocityMetacharactersInToolName() {
        Expectation[] expectations = mcpMock()
            .withTool("get_$price")
                .withDescription("Gets price with $currency #formatting")
                .respondingWith("$100 #result")
                .and()
            .build();

        Expectation toolsListExp = expectations[3];
        String template = toolsListExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("get_${esc.d}price"));
        assertThat(template, containsString("${esc.d}currency ${esc.h}formatting"));

        Expectation toolCallExp = expectations[4];
        String callTemplate = toolCallExp.getHttpResponseTemplate().getTemplate();
        assertThat(callTemplate, containsString("${esc.d}100 ${esc.h}result"));
    }

    @Test
    public void shouldEscapeVelocityMetacharactersInResourceContent() {
        Expectation[] expectations = mcpMock()
            .withResource("config://pricing")
                .withName("$Pricing #Config")
                .withDescription("Contains $variables and #directives")
                .withContent("price=$99 #comment")
                .and()
            .build();

        Expectation resListExp = expectations[3];
        String listTemplate = resListExp.getHttpResponseTemplate().getTemplate();
        assertThat(listTemplate, containsString("${esc.d}Pricing ${esc.h}Config"));
        assertThat(listTemplate, containsString("${esc.d}variables"));

        Expectation resReadExp = expectations[4];
        String readTemplate = resReadExp.getHttpResponseTemplate().getTemplate();
        assertThat(readTemplate, containsString("price=${esc.d}99 ${esc.h}comment"));
    }

    @Test
    public void shouldEscapeVelocityMetacharactersInPromptContent() {
        Expectation[] expectations = mcpMock()
            .withPrompt("template_test")
                .withDescription("Uses $variables and #macros")
                .withArgument("$arg", "A $special #argument", true)
                .respondingWith("user", "Show me $price for #item")
                .and()
            .build();

        Expectation promptsListExp = expectations[3];
        String listTemplate = promptsListExp.getHttpResponseTemplate().getTemplate();
        assertThat(listTemplate, containsString("${esc.d}variables"));
        assertThat(listTemplate, containsString("${esc.d}special ${esc.h}argument"));

        Expectation promptGetExp = expectations[4];
        String getTemplate = promptGetExp.getHttpResponseTemplate().getTemplate();
        assertThat(getTemplate, containsString("${esc.d}price"));
        assertThat(getTemplate, containsString("${esc.h}item"));
    }

    @Test
    public void shouldEscapeBackslashesBeforeVelocityMetacharacters() {
        // shared cross-client table, asserted identically in all eight client builders
        String[][] cases = {
            {"a\\$b", "a${esc.b}${esc.d}b"},
            {"a\\#b", "a${esc.b}${esc.h}b"},
            {"end\\", "end${esc.b}"},
            {"\\\\$", "${esc.b}${esc.b}${esc.d}"},
            {"${esc.b}", "${esc.d}{esc.b}"},
            {"é\\$ü", "é${esc.b}${esc.d}ü"},
            {"a\\nb \\\"q\\\"", "a\\nb \\\"q\\\""},
            {"\\\\x", "\\\\x"},
            {"$#", "${esc.d}${esc.h}"},
            {"", ""},
        };
        for (String[] testCase : cases) {
            assertThat("input: " + testCase[0], McpMockBuilder.escapeVelocity(testCase[0]), is(testCase[1]));
        }

        // builder-level form of the same inputs (escapeVelocity(escapeJson(input))), mirrored in the Node tests
        String[][] builderCases = {
            {"a\\$b", "a${esc.b}${esc.b}${esc.d}b"},
            {"a\\#b", "a${esc.b}${esc.b}${esc.h}b"},
            {"end\\", "end${esc.b}${esc.b}"},
            {"\\\\$", "${esc.b}${esc.b}${esc.b}${esc.b}${esc.d}"},
            {"${esc.b}", "${esc.d}{esc.b}"},
            {"é\\$ü", "é${esc.b}${esc.b}${esc.d}ü"},
            {"\\\\x", "\\\\\\\\x"},
            {"$#", "${esc.d}${esc.h}"},
        };
        for (String[] testCase : builderCases) {
            String builtTemplate = mcpMock().withTool("t").respondingWith(testCase[0]).and().build()[4]
                .getHttpResponseTemplate().getTemplate();
            assertThat("input: " + testCase[0], builtTemplate, containsString("\"text\": \"" + testCase[1] + "\""));
        }

        Expectation[] expectations = mcpMock()
            .withTool("escapes")
                .respondingWith("a\\$b")
                .and()
            .build();

        String template = expectations[4].getHttpResponseTemplate().getTemplate();
        // JSON-escaped "a\\$b": both backslashes precede '$', so all are escaped for Velocity
        assertThat(template, containsString("a${esc.b}${esc.b}${esc.d}b"));
    }

    @Test
    public void shouldEscapeSingleQuotesInToolNameForJsonPath() {
        Expectation[] expectations = mcpMock()
            .withTool("tool'name")
                .respondingWith("result")
                .and()
            .build();

        Expectation toolCallExp = expectations[4];
        HttpRequest request = (HttpRequest) toolCallExp.getHttpRequest();
        String body = request.getBody().toString();
        assertThat(body, containsString("tool\\\\'name"));
    }

    @Test
    public void shouldEscapeSingleQuotesInResourceUriForJsonPath() {
        Expectation[] expectations = mcpMock()
            .withResource("config://it's")
                .withContent("data")
                .and()
            .build();

        Expectation resReadExp = expectations[4];
        HttpRequest request = (HttpRequest) resReadExp.getHttpRequest();
        String body = request.getBody().toString();
        assertThat(body, containsString("it\\\\'s"));
    }

    @Test
    public void shouldEscapeSingleQuotesInPromptNameForJsonPath() {
        Expectation[] expectations = mcpMock()
            .withPrompt("user's_prompt")
                .respondingWith("user", "hello")
                .and()
            .build();

        Expectation promptGetExp = expectations[4];
        HttpRequest request = (HttpRequest) promptGetExp.getHttpRequest();
        String body = request.getBody().toString();
        assertThat(body, containsString("user\\\\'s_prompt"));
    }

    @Test
    public void shouldEscapeBackslashesBeforeSingleQuotesForJsonPath() {
        String[][] cases = {
            {"plain", "plain"},
            {"o'brien", "o\\'brien"},
            {"x\\", "x\\\\"},
            {"a\\b", "a\\\\b"},
            {"a\\'b", "a\\\\\\'b"},
            {"\\\\", "\\\\\\\\"},
        };
        for (String[] testCase : cases) {
            assertThat("input: " + testCase[0], McpMockBuilder.escapeJsonPath(testCase[0]), is(testCase[1]));
        }
    }

    @Test
    public void shouldEscapeBackslashesInToolResourceAndPromptNamesForJsonPath() {
        Expectation[] expectations = mcpMock()
            .withTool("tool\\")
                .respondingWith("ok")
                .and()
            .withResource("file:///c:\\dir\\")
                .withContent("data")
                .and()
            .withPrompt("p\\'q")
                .respondingWith("user", "hi")
                .and()
            .build();

        assertThat(jsonPathFor(expectations, "tools/call"), endsWith("@.params.name == 'tool\\\\')]"));
        assertThat(jsonPathFor(expectations, "resources/read"), endsWith("@.params.uri == 'file:///c:\\\\dir\\\\')]"));
        assertThat(jsonPathFor(expectations, "prompts/get"), endsWith("@.params.name == 'p\\\\\\'q')]"));
    }

    @Test
    public void shouldBuildJsonPathThatMatchesNamesWithBackslashesAndQuotes() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        for (String name : new String[]{"a\\b", "o'brien", "a\\'b", "x\\"}) {
            String jsonPath = jsonPathFor(mcpMock().withTool(name).respondingWith("ok").and().build(), "tools/call");
            ObjectNode requestBody = objectMapper.createObjectNode().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call");
            requestBody.putObject("params").put("name", name);
            List<?> matching = JsonPath.compile(jsonPath).read(objectMapper.writeValueAsString(requestBody));
            assertThat("name: " + name, matching, is(not(empty())));
            requestBody.putObject("params").put("name", name + "x");
            List<?> notMatching = JsonPath.compile(jsonPath).read(objectMapper.writeValueAsString(requestBody));
            assertThat("name: " + name + "x", notMatching, is(empty()));
        }
    }

    private static String jsonPathFor(Expectation[] expectations, String method) {
        return Arrays.stream(expectations)
            .map(expectation -> ((HttpRequest) expectation.getHttpRequest()).getBody())
            .filter(body -> body instanceof JsonPathBody)
            .map(body -> ((JsonPathBody) body).getValue())
            .filter(jsonPath -> jsonPath.contains("'" + method + "'"))
            .findFirst()
            .orElseThrow(() -> new AssertionError(method + " expectation not found"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidInputSchema() {
        mcpMock()
            .withTool("bad_tool")
                .withInputSchema("not valid json {{{")
                .respondingWith("result")
                .and()
            .build();
    }

    @Test
    public void shouldAcceptValidInputSchemaAndReserialize() {
        Expectation[] expectations = mcpMock()
            .withTool("valid_tool")
                .withInputSchema("{ \"type\" : \"object\" , \"properties\" : { \"x\" : { \"type\" : \"string\" } } }")
                .respondingWith("result")
                .and()
            .build();

        Expectation toolsListExp = expectations[3];
        String template = toolsListExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("\"type\":\"object\""));
        assertThat(template, containsString("inputSchema"));
    }

    @Test
    public void shouldDefaultToLatest2025_06_18ProtocolVersion() {
        Expectation[] expectations = mcpMock().build();

        Expectation initExp = expectations[0];
        String template = initExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("2025-06-18"));
    }

    @Test
    public void shouldAdvertiseOutputSchemaInToolsList() {
        Expectation[] expectations = mcpMock()
            .withTool("get_weather")
                .withDescription("Get weather")
                .withOutputSchema("{\"type\": \"object\", \"properties\": {\"tempF\": {\"type\": \"number\"}}}")
                .respondingWithStructured("72F and sunny", "{\"tempF\": 72}")
                .and()
            .build();

        Expectation toolsListExp = expectations[3];
        String listTemplate = toolsListExp.getHttpResponseTemplate().getTemplate();
        assertThat(listTemplate, containsString("outputSchema"));
        assertThat(listTemplate, containsString("tempF"));
    }

    @Test
    public void shouldEmitStructuredContentInToolCallResult() {
        Expectation[] expectations = mcpMock()
            .withTool("get_weather")
                .respondingWithStructured("72F and sunny", "{\"tempF\": 72, \"conditions\": \"sunny\"}")
                .and()
            .build();

        Expectation toolCallExp = expectations[expectations.length - 1];
        String template = toolCallExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("structuredContent"));
        assertThat(template, containsString("\"tempF\":72"));
        // the human-readable text block is preserved alongside the structured output
        assertThat(template, containsString("72F and sunny"));
    }

    @Test
    public void shouldEmitResourceLinkInToolCallResult() {
        Expectation[] expectations = mcpMock()
            .withTool("generate_report")
                .respondingWith("Report generated")
                .respondingWithResourceLink("file:///reports/q3.pdf", "Q3 Report", "Quarterly report", "application/pdf")
                .and()
            .build();

        Expectation toolCallExp = expectations[expectations.length - 1];
        String template = toolCallExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("resource_link"));
        assertThat(template, containsString("file:///reports/q3.pdf"));
        assertThat(template, containsString("Q3 Report"));
        assertThat(template, containsString("application/pdf"));
        // text block still present
        assertThat(template, containsString("Report generated"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidStructuredContentJson() {
        mcpMock()
            .withTool("bad")
                .respondingWithStructured("text", "not valid json {{{")
                .and()
            .build();
    }

    @Test
    public void shouldEscapeSpecialJsonCharactersInContent() {
        Expectation[] expectations = mcpMock()
            .withTool("test_tool")
                .respondingWith("line1\nline2\ttab\b\fspecial")
                .and()
            .build();

        assertThat(expectations.length, is(5));
        Expectation toolCallExp = expectations[4];
        String template = toolCallExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("line1\\nline2\\ttab\\b\\fspecial"));
    }
}
