package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static org.mockserver.model.HttpRequest.request;

public class LlmConversationMatcherTest {

    // --- No predicates ---

    @Test
    public void shouldMatchWhenNoPredicatesSet() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC);

        HttpRequest request = request().withBody("{\"messages\": [{\"role\": \"user\", \"content\": \"hello\"}]}");

        assertThat("should match when no predicates set", matcher.matches(request), is(true));
    }

    // --- turnIndex predicate ---

    @Test
    public void shouldMatchTurnIndex() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withTurnIndex(1);

        // One assistant message = turn index 1
        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"hello\"},\n" +
            "    {\"role\": \"assistant\", \"content\": [{\"type\": \"text\", \"text\": \"hi\"}]}\n" +
            "  ]\n" +
            "}");

        assertThat("turnIndex=1 should match conversation with 1 assistant message", matcher.matches(request), is(true));
    }

    @Test
    public void shouldNotMatchTurnIndexWhenDifferent() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withTurnIndex(2);

        // Only one assistant message = turn index 1, but we require 2
        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"hello\"},\n" +
            "    {\"role\": \"assistant\", \"content\": [{\"type\": \"text\", \"text\": \"hi\"}]}\n" +
            "  ]\n" +
            "}");

        assertThat("turnIndex=2 should not match conversation with only 1 assistant message", matcher.matches(request), is(false));
    }

    @Test
    public void shouldMatchTurnIndexZero() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withTurnIndex(0);

        // No assistant messages = turn index 0
        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"hello\"}\n" +
            "  ]\n" +
            "}");

        assertThat("turnIndex=0 should match conversation with no assistant messages", matcher.matches(request), is(true));
    }

    // --- latestMessageContains predicate ---

    @Test
    public void shouldMatchLatestMessageContains() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageContains("weather");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"What is the weather?\"}\n" +
            "  ]\n" +
            "}");

        assertThat("latestMessageContains('weather') should match message containing 'weather'", matcher.matches(request), is(true));
    }

    @Test
    public void shouldNotMatchLatestMessageContainsWhenAbsent() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageContains("weather");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"Hello world\"}\n" +
            "  ]\n" +
            "}");

        assertThat("latestMessageContains('weather') should not match 'Hello world'", matcher.matches(request), is(false));
    }

    // --- latestMessageMatches predicate ---

    @Test
    public void shouldReturnFalseOnReDoSLatestMessageMatchesPatternRatherThanHanging() {
        long previousTimeout = ConfigurationProperties.regexMatchingTimeoutMillis();
        try {
            ConfigurationProperties.regexMatchingTimeoutMillis(200L);
            LlmConversationMatcher matcher = new LlmConversationMatcher()
                .withProvider(Provider.ANTHROPIC)
                .withLatestMessageMatches(Pattern.compile("(a+)+$"));

            HttpRequest request = request().withBody("{\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\"}\n" +
                "  ]\n" +
                "}");

            long startMillis = System.currentTimeMillis();
            boolean matched = matcher.matches(request);
            long elapsedMillis = System.currentTimeMillis() - startMillis;

            assertThat("ReDoS latestMessageMatches pattern must not match", matched, is(false));
            assertThat("regex evaluation should be bounded by the timeout but took " + elapsedMillis + "ms", elapsedMillis < 2_000L, is(true));
        } finally {
            ConfigurationProperties.regexMatchingTimeoutMillis(previousTimeout);
        }
    }

    @Test
    public void shouldMatchLatestMessageRegex() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageMatches(Pattern.compile("\\d+C"));

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"It is 18C today\"}\n" +
            "  ]\n" +
            "}");

        assertThat("latestMessageMatches(\\d+C) should match '18C'", matcher.matches(request), is(true));
    }

    @Test
    public void shouldNotMatchLatestMessageRegexWhenNoMatch() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageMatches(Pattern.compile("^exact$"));

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"not exact at all\"}\n" +
            "  ]\n" +
            "}");

        assertThat("latestMessageMatches(^exact$) should not match 'not exact at all'", matcher.matches(request), is(false));
    }

    // --- latestMessageRole predicate ---

    @Test
    public void shouldMatchLatestMessageRole() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageRole(ParsedMessage.Role.USER);

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"hello\"}\n" +
            "  ]\n" +
            "}");

        assertThat("latestMessageRole(USER) should match user message", matcher.matches(request), is(true));
    }

    @Test
    public void shouldNotMatchLatestMessageRoleWhenDifferent() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageRole(ParsedMessage.Role.ASSISTANT);

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"hello\"}\n" +
            "  ]\n" +
            "}");

        assertThat("latestMessageRole(ASSISTANT) should not match user message", matcher.matches(request), is(false));
    }

    // --- containsToolResultFor predicate ---

    @Test
    public void shouldMatchContainsToolResultFor() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withContainsToolResultFor("get_weather");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"weather?\"},\n" +
            "    {\"role\": \"assistant\", \"content\": [\n" +
            "      {\"type\": \"tool_use\", \"id\": \"toolu_1\", \"name\": \"get_weather\", \"input\": {}}\n" +
            "    ]},\n" +
            "    {\"role\": \"user\", \"content\": [\n" +
            "      {\"type\": \"tool_result\", \"tool_use_id\": \"toolu_1\", \"content\": \"sunny\"}\n" +
            "    ]}\n" +
            "  ]\n" +
            "}");

        assertThat("containsToolResultFor('get_weather') should match when tool_result present for get_weather", matcher.matches(request), is(true));
    }

    @Test
    public void shouldNotMatchContainsToolResultForWhenNoToolResult() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withContainsToolResultFor("get_weather");

        // Has tool_use but no tool_result
        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"weather?\"},\n" +
            "    {\"role\": \"assistant\", \"content\": [\n" +
            "      {\"type\": \"tool_use\", \"id\": \"toolu_1\", \"name\": \"get_weather\", \"input\": {}}\n" +
            "    ]}\n" +
            "  ]\n" +
            "}");

        assertThat("containsToolResultFor should not match when no tool_result present", matcher.matches(request), is(false));
    }

    @Test
    public void shouldNotMatchContainsToolResultForWhenWrongToolName() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withContainsToolResultFor("search");

        // Tool call is get_weather, not search
        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"weather?\"},\n" +
            "    {\"role\": \"assistant\", \"content\": [\n" +
            "      {\"type\": \"tool_use\", \"id\": \"toolu_1\", \"name\": \"get_weather\", \"input\": {}}\n" +
            "    ]},\n" +
            "    {\"role\": \"user\", \"content\": [\n" +
            "      {\"type\": \"tool_result\", \"tool_use_id\": \"toolu_1\", \"content\": \"sunny\"}\n" +
            "    ]}\n" +
            "  ]\n" +
            "}");

        assertThat("containsToolResultFor('search') should not match when tool call was 'get_weather'", matcher.matches(request), is(false));
    }

    @Test
    public void shouldNotMatchContainsToolResultForWhenResultBelongsToDifferentTool() {
        // Anthropic: tool_use is "get_weather" with id "toolu_1", but tool_result references "toolu_1"
        // and we ask for containsToolResultFor("search") — should NOT match because
        // the result for toolu_1 correlates to get_weather, not search
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withContainsToolResultFor("search");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"weather?\"},\n" +
            "    {\"role\": \"assistant\", \"content\": [\n" +
            "      {\"type\": \"tool_use\", \"id\": \"toolu_1\", \"name\": \"get_weather\", \"input\": {}}\n" +
            "    ]},\n" +
            "    {\"role\": \"user\", \"content\": [\n" +
            "      {\"type\": \"tool_result\", \"tool_use_id\": \"toolu_1\", \"content\": \"sunny\"}\n" +
            "    ]}\n" +
            "  ]\n" +
            "}");

        assertThat("containsToolResultFor('search') should not match when result correlates to get_weather", matcher.matches(request), is(false));
    }

    @Test
    public void shouldMatchContainsToolResultForWithCorrectCorrelation() {
        // Two tool calls: search (toolu_1) and get_weather (toolu_2).
        // Only toolu_1 has a result. containsToolResultFor("search") should match.
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withContainsToolResultFor("search");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"search and weather\"},\n" +
            "    {\"role\": \"assistant\", \"content\": [\n" +
            "      {\"type\": \"tool_use\", \"id\": \"toolu_1\", \"name\": \"search\", \"input\": {}},\n" +
            "      {\"type\": \"tool_use\", \"id\": \"toolu_2\", \"name\": \"get_weather\", \"input\": {}}\n" +
            "    ]},\n" +
            "    {\"role\": \"user\", \"content\": [\n" +
            "      {\"type\": \"tool_result\", \"tool_use_id\": \"toolu_1\", \"content\": \"found it\"}\n" +
            "    ]}\n" +
            "  ]\n" +
            "}");

        assertThat("containsToolResultFor('search') should match when toolu_1 correlates to search", matcher.matches(request), is(true));
    }

    @Test
    public void shouldNotMatchContainsToolResultForWhenResultIsForOtherToolOnly() {
        // Two tool calls: search (toolu_1) and get_weather (toolu_2).
        // Only toolu_2 (get_weather) has a result. containsToolResultFor("search") should NOT match.
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withContainsToolResultFor("search");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"search and weather\"},\n" +
            "    {\"role\": \"assistant\", \"content\": [\n" +
            "      {\"type\": \"tool_use\", \"id\": \"toolu_1\", \"name\": \"search\", \"input\": {}},\n" +
            "      {\"type\": \"tool_use\", \"id\": \"toolu_2\", \"name\": \"get_weather\", \"input\": {}}\n" +
            "    ]},\n" +
            "    {\"role\": \"user\", \"content\": [\n" +
            "      {\"type\": \"tool_result\", \"tool_use_id\": \"toolu_2\", \"content\": \"sunny\"}\n" +
            "    ]}\n" +
            "  ]\n" +
            "}");

        assertThat("containsToolResultFor('search') should not match when only get_weather has a result", matcher.matches(request), is(false));
    }

    // --- containsToolResultFor with OpenAI ---

    @Test
    public void shouldMatchContainsToolResultForOpenAi() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.OPENAI)
            .withContainsToolResultFor("search");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"find X\"},\n" +
            "    {\"role\": \"assistant\", \"content\": null, \"tool_calls\": [\n" +
            "      {\"id\": \"call_1\", \"type\": \"function\", \"function\": {\"name\": \"search\", \"arguments\": \"{}\"}}\n" +
            "    ]},\n" +
            "    {\"role\": \"tool\", \"tool_call_id\": \"call_1\", \"content\": \"result\"}\n" +
            "  ]\n" +
            "}");

        assertThat("OpenAI containsToolResultFor('search') should match correlated tool result", matcher.matches(request), is(true));
    }

    @Test
    public void shouldNotMatchContainsToolResultForOpenAiWhenResultBelongsToDifferentTool() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.OPENAI)
            .withContainsToolResultFor("search");

        // Tool call is get_weather with id call_1; result references call_1
        // but we ask for "search" — should not match
        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"find X\"},\n" +
            "    {\"role\": \"assistant\", \"content\": null, \"tool_calls\": [\n" +
            "      {\"id\": \"call_1\", \"type\": \"function\", \"function\": {\"name\": \"get_weather\", \"arguments\": \"{}\"}}\n" +
            "    ]},\n" +
            "    {\"role\": \"tool\", \"tool_call_id\": \"call_1\", \"content\": \"result\"}\n" +
            "  ]\n" +
            "}");

        assertThat("OpenAI containsToolResultFor('search') should not match when tool call was get_weather", matcher.matches(request), is(false));
    }

    // --- AND composition ---

    @Test
    public void shouldMatchWithMultiplePredicatesAndComposition() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withTurnIndex(0)
            .withLatestMessageContains("weather");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"What is the weather?\"}\n" +
            "  ]\n" +
            "}");

        assertThat("AND composition of turnIndex=0 and latestMessageContains('weather') should match", matcher.matches(request), is(true));
    }

    @Test
    public void shouldFailAndCompositionWhenOneFails() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withTurnIndex(1)  // requires 1 assistant message
            .withLatestMessageContains("weather");

        // No assistant message, so turnIndex=1 fails
        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"What is the weather?\"}\n" +
            "  ]\n" +
            "}");

        assertThat("AND composition should fail when turnIndex predicate fails", matcher.matches(request), is(false));
    }

    // --- Fail-closed: body too large ---

    @Test
    public void shouldNotMatchWhenBodyExceedsMaxSize() {
        // Set a very small max size for this test
        int originalMax = org.mockserver.configuration.ConfigurationProperties.maxLlmConversationBodySize();
        try {
            org.mockserver.configuration.ConfigurationProperties.maxLlmConversationBodySize(16384);

            LlmConversationMatcher matcher = new LlmConversationMatcher()
                .withProvider(Provider.ANTHROPIC)
                .withLatestMessageContains("hello");

            // Create a body larger than 16384 bytes
            StringBuilder largeContent = new StringBuilder();
            for (int i = 0; i < 20000; i++) {
                largeContent.append('x');
            }
            HttpRequest request = request().withBody("{\"messages\": [{\"role\": \"user\", \"content\": \"" + largeContent.toString() + "\"}]}");

            assertThat("fail-closed: oversized body (>16384 bytes) should not match", matcher.matches(request), is(false));
        } finally {
            org.mockserver.configuration.ConfigurationProperties.maxLlmConversationBodySize(originalMax);
        }
    }

    // --- Fail-closed: malformed body ---

    @Test
    public void shouldNotMatchWithMalformedBody() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageContains("hello");

        HttpRequest request = request().withBody("this is not json");

        assertThat("fail-closed: malformed (non-JSON) body should not match", matcher.matches(request), is(false));
    }

    // --- Fail-closed: no codec registered ---

    @Test
    public void shouldNotMatchWhenNoCodecRegistered() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.GEMINI)  // No Gemini codec registered yet
            .withLatestMessageContains("hello");

        HttpRequest request = request().withBody("{\"messages\": [{\"role\": \"user\", \"content\": \"hello\"}]}");

        assertThat("fail-closed: GEMINI provider with no codec should not match", matcher.matches(request), is(false));
    }

    // --- Fail-closed: no provider set ---

    @Test
    public void shouldNotMatchWhenNoProviderSet() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withLatestMessageContains("hello");

        HttpRequest request = request().withBody("{\"messages\": [{\"role\": \"user\", \"content\": \"hello\"}]}");

        assertThat("fail-closed: no provider set should not match", matcher.matches(request), is(false));
    }

    // --- Fail-closed: empty parse result ---

    @Test
    public void shouldNotMatchWhenParseReturnsEmptyButPredicatesSet() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageContains("hello");

        // Wrong-shape body (no messages array)
        HttpRequest request = request().withBody("{\"model\": \"claude-sonnet-4-20250514\"}");

        assertThat("fail-closed: body without messages array should not match", matcher.matches(request), is(false));
    }

    // --- Provider inheritance verification ---

    @Test
    public void shouldMatchWithOpenAiProvider() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.OPENAI)
            .withLatestMessageContains("hello");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"hello world\"}\n" +
            "  ]\n" +
            "}");

        assertThat("OpenAI provider should match latestMessageContains('hello')", matcher.matches(request), is(true));
    }

    // --- Never throws ---

    @Test
    public void shouldNotThrowOnNullRequest() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageContains("hello");

        assertThat("null request should not match (fail-closed)", matcher.matches(null), is(false));
    }

    // --- Gemini containsToolResultFor ---

    @Test
    public void shouldMatchContainsToolResultForGemini() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.GEMINI)
            .withContainsToolResultFor("get_weather");

        HttpRequest request = request().withBody("{\"contents\":["
            + "{\"role\":\"user\",\"parts\":[{\"text\":\"What is the weather?\"}]},"
            + "{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"get_weather\",\"args\":{\"city\":\"Paris\"}}}]},"
            + "{\"role\":\"user\",\"parts\":[{\"functionResponse\":{\"name\":\"get_weather\",\"response\":\"18C and sunny\"}}]}"
            + "]}");

        assertThat("Gemini containsToolResultFor('get_weather') should match functionResponse", matcher.matches(request), is(true));
    }

    @Test
    public void shouldMatchContainsToolResultForGeminiWithJsonBody() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.GEMINI)
            .withContainsToolResultFor("get_weather");

        String json = "{\"contents\":["
            + "{\"role\":\"user\",\"parts\":[{\"text\":\"What is the weather?\"}]},"
            + "{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"get_weather\",\"args\":{\"city\":\"Paris\"}}}]},"
            + "{\"role\":\"user\",\"parts\":[{\"functionResponse\":{\"name\":\"get_weather\",\"response\":\"18C and sunny\"}}]}"
            + "]}";

        // Use JsonBody to simulate what MockServer Netty pipeline does
        HttpRequest request = request().withBody(
            new org.mockserver.model.JsonBody(json)
        );

        assertThat("Gemini containsToolResultFor with JsonBody should match functionResponse", matcher.matches(request), is(true));
    }

    // --- Ollama containsToolResultFor ---

    @Test
    public void shouldMatchContainsToolResultForOllama() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.OLLAMA)
            .withContainsToolResultFor("get_weather");

        HttpRequest request = request().withBody("{\"messages\":["
            + "{\"role\":\"user\",\"content\":\"What is the weather?\"},"
            + "{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":["
            + "{\"function\":{\"name\":\"get_weather\",\"arguments\":{\"city\":\"Paris\"}}}"
            + "]},"
            + "{\"role\":\"tool\",\"content\":\"18C and sunny\"}"
            + "]}");

        assertThat("Ollama containsToolResultFor('get_weather') should match single tool result", matcher.matches(request), is(true));
    }

    @Test
    public void shouldNotMatchOllamaContainsToolResultForWhenMultipleToolsCalled() {
        // Ollama tool results are keyed by empty string (no tool_call_id), so the
        // positional fallback cannot disambiguate between calls. When the assistant
        // turn called BOTH "search" and "calculate", a containsToolResultFor("search")
        // predicate must fail closed because the TOOL message could be the response
        // for either tool.
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.OLLAMA)
            .withContainsToolResultFor("search");

        HttpRequest request = request().withBody("{\"messages\":["
            + "{\"role\":\"user\",\"content\":\"search and compute\"},"
            + "{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":["
            + "{\"function\":{\"name\":\"search\",\"arguments\":{\"q\":\"x\"}}},"
            + "{\"function\":{\"name\":\"calculate\",\"arguments\":{\"expr\":\"2+2\"}}}"
            + "]},"
            + "{\"role\":\"tool\",\"content\":\"42\"}"
            + "]}");

        assertThat("Ollama fail-closed: ambiguous multi-tool result should not match", matcher.matches(request), is(false));
    }

    // --- Gap 2: Regex compile error at setter time ---

    @Test(expected = PatternSyntaxException.class)
    public void shouldThrowPatternSyntaxExceptionForInvalidRegexViaPatternCompile() {
        // Documenting the contract: Pattern.compile with an invalid regex throws
        // PatternSyntaxException immediately, so users who pass an invalid Pattern
        // to whenLatestMessageContains(Pattern) get a clear error at the call site.
        Pattern.compile("[invalid");
    }

    @Test
    public void shouldRejectInvalidRegexStringEagerlyAtSetterTime() {
        // §2.7: withLatestMessageMatches(String) with an invalid regex must throw
        // IllegalArgumentException at the setter call, not at match time.
        try {
            new LlmConversationMatcher()
                .withLatestMessageMatches("[invalid");
            fail("Expected IllegalArgumentException for invalid regex");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("[invalid"));
            assertThat("cause should be PatternSyntaxException", e.getCause() instanceof PatternSyntaxException, is(true));
        }
    }

    @Test
    public void shouldAcceptValidRegexStringAtSetterTime() {
        // Valid regex should not throw and should match at runtime
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageMatches("\\d+C");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"It is 18C today\"}\n" +
            "  ]\n" +
            "}");

        assertThat("valid regex string '\\d+C' should match at runtime", matcher.matches(request), is(true));
    }

    @Test
    public void shouldAcceptNullRegexString() {
        // Null regex should be accepted (no-op) without throwing
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withLatestMessageMatches((String) null);

        assertThat(matcher.getLatestMessageMatchesSource(), is((String) null));
        assertThat(matcher.getLatestMessageMatches(), is((Pattern) null));
    }

    @Test
    public void shouldMatchWithStringRegexSameAsPatternRegex() {
        // Verify string-based and Pattern-based setters produce identical matching
        LlmConversationMatcher stringMatcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageMatches("weather.*paris");

        LlmConversationMatcher patternMatcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageMatches(Pattern.compile("weather.*paris"));

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"what is the weather in paris\"}\n" +
            "  ]\n" +
            "}");

        assertThat("string-based regex matcher should match 'weather in paris'", stringMatcher.matches(request), is(true));
        assertThat("Pattern-based regex matcher should match 'weather in paris'", patternMatcher.matches(request), is(true));
    }

    // --- normalised prompt matching ---

    @Test
    public void shouldMatchContainsAfterWhitespaceAndCaseNormalisation() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageContains("the weather")
            .withNormalization(org.mockserver.model.NormalizationOptions.normalizationOptions().withLowercase(true));

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"What is   THE    Weather?\"}\n" +
            "  ]\n" +
            "}");

        assertThat("normalised contains should match case-insensitively with collapsed whitespace", matcher.matches(request), is(true));
    }

    @Test
    public void shouldNotMatchContainsWithoutNormalisationWhenWhitespaceDiffers() {
        // exact behaviour preserved when normalisation is not set
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageContains("the weather");

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"What is   the    weather?\"}\n" +
            "  ]\n" +
            "}");

        assertThat("without normalisation, extra whitespace should cause mismatch", matcher.matches(request), is(false));
    }

    @Test
    public void shouldMatchRegexAgainstNormalisedSubjectText() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageMatches("weather in paris")
            .withNormalization(org.mockserver.model.NormalizationOptions.normalizationOptions().withLowercase(true));

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"WEATHER   IN   PARIS\"}\n" +
            "  ]\n" +
            "}");

        assertThat("regex should match after lowercase+whitespace normalisation", matcher.matches(request), is(true));
    }

    @Test
    public void shouldMatchContainsAfterDroppingBuiltInVolatileFields() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageContains("order status request")
            .withNormalization(org.mockserver.model.NormalizationOptions.normalizationOptions()
                .withDropBuiltInVolatileFields(true));

        HttpRequest request = request().withBody("{\n" +
            "  \"messages\": [\n" +
            "    {\"role\": \"user\", \"content\": \"order status request req_abc123def at 2026-05-29T10:15:30Z\"}\n" +
            "  ]\n" +
            "}");

        assertThat("volatile-field normalisation should strip req_id and timestamp before matching", matcher.matches(request), is(true));
    }

    // --- semantic matching (opt-in) ---

    @org.junit.After
    public void clearSemanticMatching() {
        org.mockserver.llm.semantic.SemanticMatching.clear();
    }

    private static HttpRequest weatherRequest() {
        return request().withBody("{\"messages\":[{\"role\":\"user\",\"content\":\"what is the weather?\"}]}");
    }

    @Test
    public void shouldIgnoreSemanticMatchWhenDisabled() {
        // default: semantic matching not installed → predicate ignored, deterministic fallback
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withSemanticMatchAgainst("the user is asking about the weather");
        assertThat("semantic predicate should be ignored when no judge installed (deterministic fallback)", matcher.matches(weatherRequest()), is(true));
    }

    @Test
    public void shouldEvaluateSemanticMatchWhenEnabled() {
        installJudge("yes");
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withSemanticMatchAgainst("about weather");
        assertThat("semantic judge returning 'yes' should match", matcher.matches(weatherRequest()), is(true));
    }

    @Test
    public void shouldNotMatchWhenSemanticJudgeSaysNo() {
        installJudge("no");
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withSemanticMatchAgainst("about cooking");
        assertThat("semantic judge returning 'no' should not match", matcher.matches(weatherRequest()), is(false));
    }

    private static void installJudge(String verdict) {
        org.mockserver.llm.client.LlmTransport transport = (req, timeout) ->
            org.mockserver.model.HttpResponse.response().withStatusCode(200)
                .withBody("{\"message\":{\"content\":\"" + verdict + "\"}}");
        org.mockserver.llm.semantic.SemanticMatching.install(
            new org.mockserver.llm.semantic.SemanticPromptMatcher(
                new org.mockserver.llm.client.LlmCompletionService(transport),
                org.mockserver.llm.client.LlmBackend.of(Provider.OLLAMA, null)));
    }

    // --- thread-safety: a shared matcher lazily compiles its regex on the concurrent match path ---

    @Test
    public void shouldMatchConsistentlyUnderConcurrentMatchesOnSharedMatcher() throws Exception {
        // given — a single matcher whose regex pattern is compiled lazily (from source) on first
        // use, shared across many threads. This exercises the volatile latestMessageMatches field
        // (FIX 1): concurrent matchers() must never throw and must agree on the result.
        final LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withLatestMessageMatches("weather.*paris");
        final HttpRequest matching = request().withBody(
            "{\"messages\":[{\"role\":\"user\",\"content\":\"the weather in paris today\"}]}");
        final HttpRequest notMatching = request().withBody(
            "{\"messages\":[{\"role\":\"user\",\"content\":\"the weather in london today\"}]}");

        final int threads = 8;
        final int iterations = 50;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Boolean>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final boolean useMatching = (t % 2 == 0);
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    boolean result = matcher.matches(useMatching ? matching : notMatching);
                    if (result != useMatching) {
                        return false;
                    }
                }
                return true;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<Boolean> f : futures) {
            assertThat(f.get(30, java.util.concurrent.TimeUnit.SECONDS), is(true));
        }
        pool.shutdownNow();
    }

    @Test
    public void shouldLazilyBuildConversationMatcherSafelyUnderConcurrency() throws Exception {
        // given — a shared HttpLlmResponse carrying conversation predicates only; the
        // LlmConversationMatcher is reconstructed lazily from those predicates on the concurrent
        // match path. This exercises the volatile conversationMatcher field (FIX 1).
        final org.mockserver.model.HttpLlmResponse llmResponse = org.mockserver.model.HttpLlmResponse.llmResponse()
            .withProvider(Provider.ANTHROPIC)
            .withConversationPredicates(new org.mockserver.model.ConversationPredicates()
                .withLatestMessageMatches("weather.*paris"));
        final HttpRequest matching = request().withBody(
            "{\"messages\":[{\"role\":\"user\",\"content\":\"the weather in paris today\"}]}");

        final int threads = 8;
        final int iterations = 50;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Boolean>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    LlmConversationMatcher m = llmResponse.getConversationMatcher();
                    if (m == null || !m.matches(matching)) {
                        return false;
                    }
                }
                return true;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<Boolean> f : futures) {
            assertThat(f.get(30, java.util.concurrent.TimeUnit.SECONDS), is(true));
        }
        pool.shutdownNow();
    }

    // --- request shapes other than a messages array ---

    @Test
    public void shouldMatchLatestMessageOfOllamaGenerateRequest() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.OLLAMA)
            .withLatestMessageContains("sky blue");

        HttpRequest request = request().withPath("/api/generate")
            .withBody("{\"model\": \"llama3.2\", \"prompt\": \"Why is the sky blue?\"}");

        assertThat(matcher.matches(request), is(true));
    }

    @Test
    public void shouldCountGeminiSystemInstructionAsLeadingMessage() {
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.GEMINI)
            .withLatestMessageRole(ParsedMessage.Role.SYSTEM);

        HttpRequest request = request()
            .withBody("{\"systemInstruction\": {\"parts\": [{\"text\": \"Be brief.\"}]}, \"contents\": []}");

        assertThat(matcher.matches(request), is(true));
    }

    @Test
    public void shouldEscapeLineBreaksInTheLoggedBodySample() {
        String marker = "forged-llm-entry";
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.ANTHROPIC)
            .withTurnIndex(0);
        HttpRequest request = request().withBody("{\"messages\": [],\r\n\"2026-01-01 INFO " + marker + "\": 1}");
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(LlmConversationMatcher.class.getName());
        List<String> captured = new ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                String message = String.valueOf(record.getMessage());
                if (message.contains(marker)) {
                    synchronized (captured) {
                        captured.add(message);
                    }
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Level originalLevel = julLogger.getLevel();
        julLogger.addHandler(handler);
        julLogger.setLevel(java.util.logging.Level.ALL);
        try {
            assertThat(matcher.matches(request), is(false));
        } finally {
            julLogger.removeHandler(handler);
            julLogger.setLevel(originalLevel);
        }

        assertThat(captured, not(empty()));
        for (String message : captured) {
            assertThat(message, not(containsString("\r")));
            assertThat(message, not(containsString("\n")));
            assertThat(message, containsString("\\r\\n\"2026-01-01 INFO " + marker));
        }
    }
}
