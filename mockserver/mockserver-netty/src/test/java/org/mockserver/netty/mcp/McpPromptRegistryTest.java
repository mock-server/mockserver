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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThrows;

public class McpPromptRegistryTest {

    private static final List<String> DIAGNOSIS_PROMPTS = List.of(
        "debug_unmatched_request", "why_did_verify_fail", "fix_my_expectation", "why_forward_failed");
    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile("'([A-Za-z0-9_]+)'");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_]+)}}");

    private McpPromptRegistry promptRegistry;
    private ObjectMapper objectMapper;
    private HttpState httpState;

    @Before
    public void setUp() {
        promptRegistry = new McpPromptRegistry();
        objectMapper = ObjectMapperFactory.createObjectMapper();
    }

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    @Test
    public void shouldRegisterPrompts() {
        Map<String, McpPromptRegistry.PromptDefinition> prompts = promptRegistry.getPrompts();
        assertThat(prompts.isEmpty(), is(false));
        assertThat(prompts.containsKey("debug_unmatched_request"), is(true));
        assertThat(prompts.containsKey("create_mock_from_description"), is(true));
    }

    @Test
    public void shouldHavePromptDefinitionsWithMetadata() {
        for (McpPromptRegistry.PromptDefinition prompt : promptRegistry.getPrompts().values()) {
            assertThat(prompt.getName(), notNullValue());
            assertThat(prompt.getDescription(), notNullValue());
            assertThat(prompt.getArguments(), notNullValue());
            assertThat(prompt.getRole(), notNullValue());
            assertThat(prompt.getTemplate(), notNullValue());
        }
    }

    @Test
    public void shouldSubstituteArgumentsIntoMessages() {
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("method", "GET");
        arguments.put("path", "/users/1");

        ArrayNode messages = promptRegistry.getMessages("debug_unmatched_request", arguments);

        assertThat(messages, notNullValue());
        assertThat(messages.size(), is(1));
        assertThat(messages.get(0).path("role").asText(), is("user"));
        String text = messages.get(0).path("content").path("text").asText();
        assertThat(text.contains("GET"), is(true));
        assertThat(text.contains("/users/1"), is(true));
        assertThat(text.contains("{{method}}"), is(false));
        assertThat(text.contains("{{path}}"), is(false));
    }

    @Test
    public void shouldRejectMissingRequiredArgumentsNamingEachOne() {
        McpPromptRegistry.MissingPromptArgumentsException exception = assertThrows(
            McpPromptRegistry.MissingPromptArgumentsException.class,
            () -> promptRegistry.getMessages("debug_unmatched_request", null)
        );

        assertThat(exception.getMissingArguments(), contains("method", "path"));
        assertThat(exception.getMessage(), is("Missing required arguments for prompt 'debug_unmatched_request': method, path"));
    }

    @Test
    public void shouldRejectRequiredArgumentThatIsAbsentOrNull() {
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.putNull("method");
        arguments.put("unrelated", "GET");

        McpPromptRegistry.MissingPromptArgumentsException exception = assertThrows(
            McpPromptRegistry.MissingPromptArgumentsException.class,
            () -> promptRegistry.getMessages("debug_unmatched_request", arguments)
        );

        assertThat(exception.getMissingArguments(), contains("method", "path"));
    }

    @Test
    public void shouldNameOnlyTheRequiredArgumentThatIsMissing() {
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("method", "GET");

        McpPromptRegistry.MissingPromptArgumentsException exception = assertThrows(
            McpPromptRegistry.MissingPromptArgumentsException.class,
            () -> promptRegistry.getMessages("debug_unmatched_request", arguments)
        );

        assertThat(exception.getMissingArguments(), contains("path"));
        assertThat(exception.getMessage(), is("Missing required argument for prompt 'debug_unmatched_request': path"));
    }

    @Test
    public void shouldRejectEveryPromptCalledWithoutItsRequiredArguments() {
        for (McpPromptRegistry.PromptDefinition prompt : promptRegistry.getPrompts().values()) {
            List<String> required = new ArrayList<>();
            for (McpPromptRegistry.PromptArgument argument : prompt.getArguments()) {
                if (argument.isRequired()) {
                    required.add(argument.getName());
                }
            }
            if (required.isEmpty()) {
                continue;
            }
            McpPromptRegistry.MissingPromptArgumentsException exception = assertThrows(
                McpPromptRegistry.MissingPromptArgumentsException.class,
                () -> promptRegistry.getMessages(prompt.getName(), objectMapper.createObjectNode())
            );
            assertThat(prompt.getName(), exception.getMissingArguments(), is(required));
        }
    }

    @Test
    public void shouldNotRequireOptionalArguments() {
        promptRegistry.getPrompts().put("with_optional", new McpPromptRegistry.PromptDefinition(
            "with_optional",
            "a prompt with one required and one optional argument",
            List.of(
                new McpPromptRegistry.PromptArgument("subject", "required", true),
                new McpPromptRegistry.PromptArgument("detail", "optional", false)
            ),
            "user",
            "About {{subject}}."
        ));
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("subject", "orders");

        ArrayNode messages = promptRegistry.getMessages("with_optional", arguments);

        assertThat(messages.get(0).path("content").path("text").asText(), is("About orders."));
    }

    @Test
    public void shouldNotReSubstitutePlaceholdersIntroducedByArgumentValues() {
        // A value that itself contains another argument's placeholder must be inserted literally,
        // not re-scanned and replaced (no cascade substitution).
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("method", "{{path}}");
        arguments.put("path", "/secret");

        ArrayNode messages = promptRegistry.getMessages("debug_unmatched_request", arguments);

        String text = messages.get(0).path("content").path("text").asText();
        assertThat(text.contains("{{path}}"), is(true));
        assertThat(text.contains("/secret"), is(true));
    }

    @Test
    public void shouldReturnNullMessagesForUnknownPrompt() {
        ArrayNode messages = promptRegistry.getMessages("nonexistent", objectMapper.createObjectNode());
        assertThat(messages, nullValue());
    }

    @Test
    public void shouldNarrowMessageContentToTextType() {
        ArrayNode messages = promptRegistry.getMessages("create_mock_from_description",
            objectMapper.createObjectNode().put("description", "GET /health returns 200"));
        JsonNode content = messages.get(0).path("content");
        assertThat(content.path("type").asText(), is("text"));
    }

    @Test
    public void shouldRegisterDiagnosisPrompts() {
        for (String name : DIAGNOSIS_PROMPTS) {
            assertThat(name, promptRegistry.getPrompts().containsKey(name), is(true));
        }
    }

    @Test
    public void shouldNameOnlyToolsThatAreRegistered() {
        httpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class));
        LifeCycle server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        Set<String> tools = new McpToolRegistry(httpState, server).getTools().keySet();

        for (McpPromptRegistry.PromptDefinition prompt : promptRegistry.getPrompts().values()) {
            Set<String> named = quotedIdentifiers(prompt.getTemplate());
            assertThat(prompt.getName() + " names no tool", named.isEmpty(), is(false));
            List<String> unknown = new ArrayList<>();
            for (String tool : named) {
                if (!tools.contains(tool)) {
                    unknown.add(tool);
                }
            }
            assertThat(prompt.getName() + " names tools that do not exist", unknown, empty());
        }
    }

    @Test
    public void shouldDeclareExactlyTheArgumentsEachTemplateUses() {
        for (McpPromptRegistry.PromptDefinition prompt : promptRegistry.getPrompts().values()) {
            Set<String> placeholders = new LinkedHashSet<>();
            Matcher matcher = PLACEHOLDER.matcher(prompt.getTemplate());
            while (matcher.find()) {
                placeholders.add(matcher.group(1));
            }
            Set<String> declared = new LinkedHashSet<>();
            for (McpPromptRegistry.PromptArgument argument : prompt.getArguments()) {
                declared.add(argument.getName());
                assertThat(prompt.getName() + "." + argument.getName(), argument.isRequired(), is(true));
            }
            assertThat(prompt.getName(), placeholders, is(declared));
        }
    }

    @Test
    public void shouldKeepFactsApartFromInterpretationInEveryDiagnosisPrompt() {
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("method", "GET");
        arguments.put("path", "/orders");

        for (String name : DIAGNOSIS_PROMPTS) {
            assertThat(name, promptRegistry.getPrompts().get(name).getTemplate(), endsWith(McpPromptRegistry.FACTS_THEN_INTERPRETATION));
            String text = promptRegistry.getMessages(name, arguments).get(0).path("content").path("text").asText();
            assertThat(name, text, containsString("\"Facts from MockServer\""));
            assertThat(name, text, containsString("\"Interpretation\""));
            assertThat(name, text, containsString("GET"));
            assertThat(name, text, containsString("/orders"));
            assertThat(name, text.contains("{{"), is(false));
        }
    }

    @Test
    public void shouldChainTheToolsEachDiagnosisNeeds() {
        assertThat(quotedIdentifiers(template("why_did_verify_fail")),
            is(Set.of("verify_request", "raw_verify", "retrieve_recorded_requests", "get_status", "retrieve_logs")));
        assertThat(quotedIdentifiers(template("fix_my_expectation")),
            is(Set.of("debug_request_mismatch", "explain_unmatched_requests", "list_expectations", "raw_expectation")));
        assertThat(quotedIdentifiers(template("why_forward_failed")),
            is(Set.of("retrieve_request_responses", "retrieve_logs", "list_expectations")));
    }

    @Test
    public void shouldNotApplyAFixBeforeTheUserAgrees() {
        String template = template("fix_my_expectation");
        assertThat(template, containsString("Do not apply it until the user agrees"));
        assertThat(template.indexOf("Do not apply it until the user agrees") < template.indexOf("call 'raw_expectation'"), is(true));
        assertThat(template, containsString("keeping its \"id\""));
    }

    @Test
    public void shouldTellTheAssistantHowToActOnCreateExpectationErrors() {
        String text = promptRegistry.getMessages("create_mock_from_description",
            objectMapper.createObjectNode().put("description", "GET /health returns 200 with body ok"))
            .get(0).path("content").path("text").asText();

        assertThat(text, containsString("GET /health returns 200 with body ok"));
        assertThat(quotedIdentifiers(text), is(Set.of("create_expectation", "raw_expectation", "list_expectations")));
        assertThat(text, containsString("\"unknownParameters\""));
        assertThat(text, containsString("\"validationErrors\""));
        assertThat(text, containsString("every detail you assumed"));
    }

    private String template(String name) {
        return promptRegistry.getPrompts().get(name).getTemplate();
    }

    private static Set<String> quotedIdentifiers(String text) {
        Set<String> identifiers = new LinkedHashSet<>();
        Matcher matcher = QUOTED_IDENTIFIER.matcher(text);
        while (matcher.find()) {
            identifiers.add(matcher.group(1));
        }
        return identifiers;
    }
}
