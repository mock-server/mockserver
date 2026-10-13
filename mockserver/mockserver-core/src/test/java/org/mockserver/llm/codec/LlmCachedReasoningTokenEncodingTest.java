package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.model.*;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.Usage.usage;

/**
 * Verifies that each provider codec ENCODES the optional cached-input,
 * cache-creation, and reasoning token counts ({@link Usage}) into its response
 * usage block using the provider's native key names — and, crucially, OMITS
 * them entirely (byte-identical to before) when those fields are unset or zero,
 * so existing fixtures/golden files are unchanged. Anthropic is the exception: the
 * real Messages API always sends both cache counts, so they are 0 when unset.
 *
 * <p>Covers both the non-streaming {@code encode()} and the streaming
 * {@code encodeStreaming()} usage paths where the provider emits usage.
 */
public class LlmCachedReasoningTokenEncodingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JsonNode parse(HttpResponse response) throws Exception {
        return OBJECT_MAPPER.readTree(response.getBodyAsString());
    }

    // -------------------------------------------------------------------
    // Anthropic — cache_read_input_tokens / cache_creation_input_tokens
    // -------------------------------------------------------------------

    @Test
    public void anthropicEncodesCachedAndCacheCreationTokensWhenSet() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(20)
                .withCachedInputTokens(80).withCacheCreationTokens(10));

        JsonNode usage = parse(new AnthropicCodec().encode(completion, "claude-sonnet-4-20250514")).get("usage");

        assertThat(usage.get("input_tokens").asInt(), is(100));
        assertThat(usage.get("output_tokens").asInt(), is(20));
        assertThat(usage.get("cache_read_input_tokens").asInt(), is(80));
        assertThat(usage.get("cache_creation_input_tokens").asInt(), is(10));
    }

    @Test
    public void anthropicSendsZeroCachedAndCacheCreationTokensWhenUnset() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(20));

        JsonNode usage = parse(new AnthropicCodec().encode(completion, "claude-sonnet-4-20250514")).get("usage");

        assertThat(usage.get("cache_read_input_tokens").isInt(), is(true));
        assertThat(usage.get("cache_read_input_tokens").asInt(), is(0));
        assertThat(usage.get("cache_creation_input_tokens").isInt(), is(true));
        assertThat(usage.get("cache_creation_input_tokens").asInt(), is(0));
    }

    @Test
    public void anthropicSendsZeroCachedTokensWhenZero() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(20)
                .withCachedInputTokens(0).withCacheCreationTokens(0));

        JsonNode usage = parse(new AnthropicCodec().encode(completion, "claude-sonnet-4-20250514")).get("usage");

        assertThat(usage.get("cache_read_input_tokens").isInt(), is(true));
        assertThat(usage.get("cache_read_input_tokens").asInt(), is(0));
        assertThat(usage.get("cache_creation_input_tokens").isInt(), is(true));
        assertThat(usage.get("cache_creation_input_tokens").asInt(), is(0));
    }

    @Test
    public void anthropicStreamingEncodesCachedTokensInMessageStart() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(20)
                .withCachedInputTokens(80).withCacheCreationTokens(10));

        List<SseEvent> events = new AnthropicCodec().encodeStreaming(completion, "claude-sonnet-4-20250514", null);

        JsonNode messageStart = OBJECT_MAPPER.readTree(events.get(0).getData());
        JsonNode usage = messageStart.get("message").get("usage");
        assertThat(usage.get("cache_read_input_tokens").asInt(), is(80));
        assertThat(usage.get("cache_creation_input_tokens").asInt(), is(10));
    }

    @Test
    public void anthropicStreamingSendsZeroCachedTokensWhenUnset() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(20));

        List<SseEvent> events = new AnthropicCodec().encodeStreaming(completion, "claude-sonnet-4-20250514", null);

        JsonNode usage = OBJECT_MAPPER.readTree(events.get(0).getData()).get("message").get("usage");
        assertThat(usage.get("cache_read_input_tokens").isInt(), is(true));
        assertThat(usage.get("cache_read_input_tokens").asInt(), is(0));
        assertThat(usage.get("cache_creation_input_tokens").isInt(), is(true));
        assertThat(usage.get("cache_creation_input_tokens").asInt(), is(0));
    }

    // -------------------------------------------------------------------
    // OpenAI Chat Completions — prompt_tokens_details / completion_tokens_details
    // -------------------------------------------------------------------

    @Test
    public void openAiEncodesCachedAndReasoningTokensWhenSet() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40)
                .withCachedInputTokens(64).withReasoningTokens(30));

        JsonNode usage = parse(new OpenAiChatCompletionsCodec().encode(completion, "gpt-4o")).get("usage");

        assertThat(usage.get("prompt_tokens_details").get("cached_tokens").asInt(), is(64));
        assertThat(usage.get("completion_tokens_details").get("reasoning_tokens").asInt(), is(30));
    }

    @Test
    public void openAiOmitsDetailsWhenUnset() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40));

        JsonNode usage = parse(new OpenAiChatCompletionsCodec().encode(completion, "gpt-4o")).get("usage");

        assertThat(usage.has("prompt_tokens_details"), is(false));
        assertThat(usage.has("completion_tokens_details"), is(false));
    }

    @Test
    public void azureOpenAiDelegatesCachedAndReasoningTokenEncoding() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40)
                .withCachedInputTokens(64).withReasoningTokens(30));

        JsonNode usage = parse(new AzureOpenAiCodec().encode(completion, "gpt-4o")).get("usage");

        assertThat(usage.get("prompt_tokens_details").get("cached_tokens").asInt(), is(64));
        assertThat(usage.get("completion_tokens_details").get("reasoning_tokens").asInt(), is(30));
    }

    // -------------------------------------------------------------------
    // OpenAI Responses — input_tokens_details / output_tokens_details
    // -------------------------------------------------------------------

    @Test
    public void openAiResponsesEncodesCachedAndReasoningTokensWhenSet() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40)
                .withCachedInputTokens(64).withReasoningTokens(30));

        JsonNode usage = parse(new OpenAiResponsesCodec().encode(completion, "gpt-4o")).get("usage");

        assertThat(usage.get("input_tokens_details").get("cached_tokens").asInt(), is(64));
        assertThat(usage.get("output_tokens_details").get("reasoning_tokens").asInt(), is(30));
    }

    @Test
    public void openAiResponsesEmitsZeroDetailsWhenUnset() throws Exception {
        // ResponseUsage declares both details objects as required, so they are always present.
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40));

        JsonNode usage = parse(new OpenAiResponsesCodec().encode(completion, "gpt-4o")).get("usage");

        assertThat(usage.get("input_tokens_details").get("cached_tokens").asInt(), is(0));
        assertThat(usage.get("input_tokens_details").get("cache_write_tokens").asInt(), is(0));
        assertThat(usage.get("output_tokens_details").get("reasoning_tokens").asInt(), is(0));
    }

    @Test
    public void openAiResponsesEncodesCacheCreationAsCacheWriteTokens() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40).withCacheCreationTokens(25));

        JsonNode usage = parse(new OpenAiResponsesCodec().encode(completion, "gpt-4o")).get("usage");

        assertThat(usage.get("input_tokens_details").get("cache_write_tokens").asInt(), is(25));
    }

    @Test
    public void openAiResponsesStreamingEncodesCachedAndReasoningTokensInCompleted() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40)
                .withCachedInputTokens(64).withReasoningTokens(30));

        List<SseEvent> events = new OpenAiResponsesCodec().encodeStreaming(completion, "gpt-4o", null);

        JsonNode completed = null;
        for (SseEvent event : events) {
            if ("response.completed".equals(event.getEvent())) {
                completed = OBJECT_MAPPER.readTree(event.getData());
            }
        }
        assertThat(completed, is(notNullValue()));
        JsonNode usage = completed.get("response").get("usage");
        assertThat(usage.get("input_tokens_details").get("cached_tokens").asInt(), is(64));
        assertThat(usage.get("output_tokens_details").get("reasoning_tokens").asInt(), is(30));
    }

    // -------------------------------------------------------------------
    // Gemini — usageMetadata.cachedContentTokenCount / thoughtsTokenCount
    // -------------------------------------------------------------------

    @Test
    public void geminiEncodesCachedAndThoughtsTokensWhenSet() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40)
                .withCachedInputTokens(64).withReasoningTokens(30));

        JsonNode usage = parse(new GeminiCodec().encode(completion, "gemini-1.5-pro")).get("usageMetadata");

        assertThat(usage.get("cachedContentTokenCount").asInt(), is(64));
        assertThat(usage.get("thoughtsTokenCount").asInt(), is(30));
    }

    @Test
    public void geminiOmitsCachedAndThoughtsTokensWhenUnset() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40));

        JsonNode usage = parse(new GeminiCodec().encode(completion, "gemini-1.5-pro")).get("usageMetadata");

        assertThat(usage.has("cachedContentTokenCount"), is(false));
        assertThat(usage.has("thoughtsTokenCount"), is(false));
    }

    @Test
    public void geminiStreamingEncodesCachedAndThoughtsTokensInFinalChunk() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(40)
                .withCachedInputTokens(64).withReasoningTokens(30));

        List<SseEvent> events = new GeminiCodec().encodeStreaming(completion, "gemini-1.5-pro", null);

        JsonNode finalChunk = OBJECT_MAPPER.readTree(events.get(events.size() - 1).getData());
        JsonNode usage = finalChunk.get("usageMetadata");
        assertThat(usage.get("cachedContentTokenCount").asInt(), is(64));
        assertThat(usage.get("thoughtsTokenCount").asInt(), is(30));
    }

    // -------------------------------------------------------------------
    // Bedrock — delegates to Anthropic (cache_read / cache_creation)
    // -------------------------------------------------------------------

    @Test
    public void bedrockDelegatesCachedTokenEncoding() throws Exception {
        Completion completion = completion()
            .withText("hi")
            .withUsage(usage().withInputTokens(100).withOutputTokens(20)
                .withCachedInputTokens(80).withCacheCreationTokens(10));

        JsonNode usage = parse(new BedrockCodec().encode(completion, "anthropic.claude-sonnet-4-20250514-v1:0")).get("usage");

        assertThat(usage.get("cache_read_input_tokens").asInt(), is(80));
        assertThat(usage.get("cache_creation_input_tokens").asInt(), is(10));
    }
}
