package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.ProviderCodec;
import org.mockserver.llm.ProviderCodecRegistry;
import org.mockserver.llm.client.LlmClientRegistry;
import org.mockserver.llm.client.LlmStreamUsageScanner;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpLlmResponseActionHandler;
import org.mockserver.model.Completion;
import org.mockserver.model.EmbeddingResponse;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.SseEvent;
import org.mockserver.model.Usage;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.Delay.milliseconds;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StreamingPhysics.streamingPhysics;
import static org.mockserver.model.ToolUse.toolUse;
import static org.mockserver.model.Usage.usage;

/**
 * The OpenAI-compatible providers' additions to the OpenAI Chat Completions shape, and the
 * configured tool-call id, asserted against each provider's published shape rather than codec
 * output:
 * DeepSeek (api-docs.deepseek.com/api/create-chat-completion and the {@code @ai-sdk/deepseek}
 * schema), xAI (docs.x.ai/developers/rest-api-reference/inference/chat-completions),
 * Groq ({@code groq-python} {@code ChatCompletionChunk}, {@code XGroq}, {@code CompletionUsage}),
 * Mistral ({@code client-python} {@code CompletionChunk}, which has no {@code stream_options}),
 * OpenRouter (openrouter.ai/docs usage accounting and reasoning tokens), Gemini
 * ({@code FunctionCall.id} in the v1beta discovery document) and Ollama ({@code api.ToolCall.ID}).
 */
public class OpenAiCompatibleProviderWireShapeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpRequest INCLUDE_USAGE = request()
        .withBody("{\"stream\":true,\"stream_options\":{\"include_usage\":true},\"messages\":[]}");
    private static final HttpRequest NO_OPT_IN = request().withBody("{\"stream\":true,\"messages\":[]}");

    private static ProviderCodec codec(Provider provider) {
        return ProviderCodecRegistry.getInstance().lookup(provider).orElseThrow(AssertionError::new);
    }

    private static JsonNode encode(Provider provider, Completion completion, String model) throws Exception {
        return OBJECT_MAPPER.readTree(codec(provider).encode(completion, model).getBodyAsString());
    }

    private static List<JsonNode> stream(Provider provider, Completion completion, String model, HttpRequest request) throws Exception {
        List<JsonNode> chunks = new ArrayList<>();
        List<SseEvent> events = codec(provider).encodeStreaming(completion, model, null, request);
        assertThat(events.get(events.size() - 1).getData(), is("[DONE]"));
        for (SseEvent event : events.subList(0, events.size() - 1)) {
            chunks.add(OBJECT_MAPPER.readTree(event.getData()));
        }
        return chunks;
    }

    private static String deltaText(List<JsonNode> chunks, String field) {
        StringBuilder text = new StringBuilder();
        for (JsonNode chunk : chunks) {
            JsonNode value = chunk.path("choices").path(0).path("delta").path(field);
            if (value.isTextual()) {
                text.append(value.asText());
            }
        }
        return text.toString();
    }

    private static JsonNode finishChunk(List<JsonNode> chunks) {
        for (JsonNode chunk : chunks) {
            if (chunk.path("choices").path(0).path("finish_reason").isTextual()) {
                return chunk;
            }
        }
        throw new AssertionError("no finish_reason chunk");
    }

    private static Completion reasoning() {
        return completion().withText("The answer is 4.").withReasoningText("2 plus 2 is 4")
            .withUsage(usage().withInputTokens(20).withOutputTokens(30).withCachedInputTokens(5).withReasoningTokens(12));
    }

    // ---- DeepSeek ----

    @Test
    public void deepSeekReturnsReasoningContentAndThePromptCacheSplit() throws Exception {
        JsonNode root = encode(Provider.DEEPSEEK, reasoning(), "deepseek-reasoner");
        JsonNode message = root.path("choices").path(0).path("message");
        assertThat(message.path("reasoning_content").asText(), is("2 plus 2 is 4"));
        assertThat(message.path("content").asText(), is("The answer is 4."));
        JsonNode usage = root.path("usage");
        assertThat(usage.path("prompt_cache_hit_tokens").asInt(-1), is(5));
        assertThat(usage.path("prompt_cache_miss_tokens").asInt(-1), is(15));
        assertThat(usage.path("prompt_tokens").asInt(-1), is(20));
        assertThat(usage.path("completion_tokens_details").path("reasoning_tokens").asInt(-1), is(12));

        JsonNode noCache = encode(Provider.DEEPSEEK, completion().withText("x").withUsage(usage().withInputTokens(7).withOutputTokens(1)), "deepseek-chat").path("usage");
        assertThat(noCache.path("prompt_cache_hit_tokens").asInt(-1), is(0));
        assertThat(noCache.path("prompt_cache_miss_tokens").asInt(-1), is(7));
    }

    @Test
    public void deepSeekStreamsReasoningContentDeltasBeforeTheContent() throws Exception {
        List<JsonNode> chunks = stream(Provider.DEEPSEEK, reasoning(), "deepseek-reasoner", INCLUDE_USAGE);
        assertThat(deltaText(chunks, "reasoning_content"), is("2 plus 2 is 4"));
        assertThat(deltaText(chunks, "content"), is("The answer is 4."));
        int lastReasoning = -1;
        int firstContent = -1;
        for (int i = 0; i < chunks.size(); i++) {
            JsonNode delta = chunks.get(i).path("choices").path(0).path("delta");
            if (delta.has("reasoning_content")) {
                lastReasoning = i;
            }
            if (firstContent < 0 && delta.path("content").asText().length() > 0) {
                firstContent = i;
            }
        }
        assertThat(lastReasoning < firstContent, is(true));
        JsonNode usageChunk = chunks.get(chunks.size() - 1);
        assertThat(usageChunk.path("choices").size(), is(0));
        assertThat(usageChunk.path("usage").path("prompt_cache_hit_tokens").asInt(-1), is(5));
        assertThat(usageChunk.path("usage").path("prompt_cache_miss_tokens").asInt(-1), is(15));
    }

    // ---- xAI ----

    @Test
    public void xaiReportsReasoningTokensOutsideCompletionTokensAsTheApiReferenceExampleDoes() throws Exception {
        // the docs.x.ai example: prompt 32 (6 cached), completion 9, reasoning 94, total 135
        Completion completion = completion().withText("101 multiplied by 3 is 303.").withReasoningText("101 * 3")
            .withUsage(usage().withInputTokens(32).withOutputTokens(103).withCachedInputTokens(6).withReasoningTokens(94));
        JsonNode root = encode(Provider.XAI, completion, "grok-3-mini");
        assertThat(root.path("choices").path(0).path("message").path("reasoning_content").asText(), is("101 * 3"));
        JsonNode usage = root.path("usage");
        assertThat(usage.path("prompt_tokens").asInt(-1), is(32));
        assertThat(usage.path("completion_tokens").asInt(-1), is(9));
        assertThat(usage.path("total_tokens").asInt(-1), is(135));
        assertThat(usage.path("prompt_tokens_details"), is(OBJECT_MAPPER.readTree(
            "{\"text_tokens\":32,\"audio_tokens\":0,\"image_tokens\":0,\"cached_tokens\":6}")));
        assertThat(usage.path("completion_tokens_details"), is(OBJECT_MAPPER.readTree(
            "{\"reasoning_tokens\":94,\"audio_tokens\":0,\"accepted_prediction_tokens\":0,\"rejected_prediction_tokens\":0}")));
        assertThat(usage.path("num_sources_used").asInt(-1), is(0));

        Usage parsed = LlmClientRegistry.getInstance().lookup(Provider.XAI).orElseThrow(AssertionError::new).parseUsage(usage);
        assertThat(parsed.getInputTokens(), is(32));
        assertThat(parsed.getOutputTokens(), is(103));
        assertThat(parsed.getReasoningTokens(), is(94));
    }

    @Test
    public void xaiStreamsReasoningContentDeltas() throws Exception {
        List<JsonNode> chunks = stream(Provider.XAI, reasoning(), "grok-3-mini", INCLUDE_USAGE);
        assertThat(deltaText(chunks, "reasoning_content"), is("2 plus 2 is 4"));
        assertThat(chunks.get(chunks.size() - 1).path("usage").path("completion_tokens").asInt(-1), is(18));
    }

    // ---- Groq ----

    @Test
    public void groqReturnsReasoningTimingUsageAndTheXGroqRequestId() throws Exception {
        Completion completion = reasoning().withStreamingPhysics(streamingPhysics()
            .withTimeToFirstToken(milliseconds(250)).withTokensPerSecond(100));
        JsonNode root = encode(Provider.GROQ, completion, "qwen/qwen3-32b");
        assertThat(root.path("choices").path(0).path("message").path("reasoning").asText(), is("2 plus 2 is 4"));
        assertThat(root.path("x_groq").path("id").asText(), startsWith("req_"));
        JsonNode usage = root.path("usage");
        assertThat(usage.path("queue_time").isNumber(), is(true));
        assertThat(usage.path("prompt_time").asDouble(), closeTo(0.25, 1e-9));
        assertThat(usage.path("completion_time").asDouble(), closeTo(0.30, 1e-9));
        assertThat(usage.path("total_time").asDouble(), closeTo(0.55, 1e-9));
        assertThat(usage.path("prompt_tokens").asInt(-1), is(20));
        assertThat(usage.path("completion_tokens").asInt(-1), is(30));

        JsonNode noPhysics = encode(Provider.GROQ, completion().withText("x"), "llama-3.3-70b-versatile").path("usage");
        for (String field : new String[]{"queue_time", "prompt_time", "completion_time", "total_time"}) {
            assertThat(field, noPhysics.path(field).isNumber(), is(true));
        }
    }

    @Test
    public void groqStreamsUsageInXGroqOnTheFinishChunkWithoutAnOptIn() throws Exception {
        List<JsonNode> chunks = stream(Provider.GROQ, reasoning(), "qwen/qwen3-32b", NO_OPT_IN);
        assertThat(deltaText(chunks, "reasoning"), is("2 plus 2 is 4"));
        String requestId = chunks.get(0).path("x_groq").path("id").asText();
        assertThat(requestId, startsWith("req_"));
        JsonNode finish = chunks.get(chunks.size() - 1);
        assertThat(finish.path("choices").path(0).path("finish_reason").asText(), is("stop"));
        assertThat(finish.path("x_groq").path("id").asText(), is(requestId));
        JsonNode usage = finish.path("x_groq").path("usage");
        assertThat(usage.path("prompt_tokens").asInt(-1), is(20));
        assertThat(usage.path("completion_tokens").asInt(-1), is(30));
        assertThat(usage.path("total_tokens").asInt(-1), is(50));
        assertThat(usage.path("total_time").isNumber(), is(true));
        for (JsonNode chunk : chunks) {
            assertThat(chunk.has("usage"), is(false));
        }
    }

    // ---- Mistral ----

    @Test
    public void mistralStreamsUsageOnTheFinishChunkWithoutAnOptIn() throws Exception {
        for (HttpRequest request : new HttpRequest[]{NO_OPT_IN, INCLUDE_USAGE, null}) {
            List<JsonNode> chunks = request == null
                ? streamWithoutRequest(Provider.MISTRAL)
                : stream(Provider.MISTRAL, completion().withText("hello there").withUsage(usage().withInputTokens(9).withOutputTokens(3)), "mistral-small-latest", request);
            JsonNode finish = chunks.get(chunks.size() - 1);
            assertThat(finish.path("choices").path(0).path("finish_reason").asText(), is("stop"));
            assertThat(finish.path("usage").path("prompt_tokens").asInt(-1), is(9));
            assertThat(finish.path("usage").path("completion_tokens").asInt(-1), is(3));
            assertThat(finish.path("usage").path("total_tokens").asInt(-1), is(12));
            for (JsonNode chunk : chunks.subList(0, chunks.size() - 1)) {
                assertThat(chunk.has("usage"), is(false));
            }
        }
    }

    private static List<JsonNode> streamWithoutRequest(Provider provider) throws Exception {
        List<JsonNode> chunks = new ArrayList<>();
        List<SseEvent> events = codec(provider).encodeStreaming(
            completion().withText("hello there").withUsage(usage().withInputTokens(9).withOutputTokens(3)), "mistral-small-latest", null);
        for (SseEvent event : events.subList(0, events.size() - 1)) {
            chunks.add(OBJECT_MAPPER.readTree(event.getData()));
        }
        return chunks;
    }

    // ---- OpenRouter ----

    @Test
    public void openRouterReturnsReasoningAndItsUsageAccounting() throws Exception {
        JsonNode root = encode(Provider.OPENROUTER, reasoning(), "openai/gpt-4o");
        assertThat(root.path("choices").path(0).path("message").path("reasoning").asText(), is("2 plus 2 is 4"));
        JsonNode usage = root.path("usage");
        assertThat(usage.path("cost").isNumber(), is(true));
        assertThat(usage.path("cost").asDouble() > 0, is(true));
        assertThat(usage.path("is_byok").isBoolean() && !usage.path("is_byok").asBoolean(), is(true));
        assertThat(usage.path("prompt_tokens_details").path("cached_tokens").asInt(-1), is(5));
        assertThat(usage.path("completion_tokens_details").path("reasoning_tokens").asInt(-1), is(12));

        JsonNode unpriced = encode(Provider.OPENROUTER, completion().withText("x"), "someone/unknown-model").path("usage");
        assertThat(unpriced.has("cost"), is(false));
        assertThat(unpriced.path("prompt_tokens_details").path("cached_tokens").asInt(-1), is(0));
        assertThat(unpriced.path("completion_tokens_details").path("reasoning_tokens").asInt(-1), is(0));
    }

    @Test
    public void openRouterStreamsTheUsageChunkWithoutAnOptIn() throws Exception {
        List<JsonNode> chunks = stream(Provider.OPENROUTER, reasoning(), "openai/gpt-4o", NO_OPT_IN);
        assertThat(deltaText(chunks, "reasoning"), is("2 plus 2 is 4"));
        JsonNode usageChunk = chunks.get(chunks.size() - 1);
        assertThat(usageChunk.path("choices").size(), is(1));
        assertThat(usageChunk.path("usage").path("prompt_tokens").asInt(-1), is(20));
        assertThat(usageChunk.path("usage").path("completion_tokens").asInt(-1), is(30));
    }

    @Test
    public void openRouterChoiceCarriesTheUpstreamNativeFinishReasonBesideTheNormalisedOne() throws Exception {
        String[][] cases = {
            // stopReason, finish_reason, native_finish_reason
            {null, "stop", "stop"},
            {"end_turn", "stop", "end_turn"},
            {"STOP", "stop", "STOP"},
            {"MAX_TOKENS", "length", "MAX_TOKENS"},
            {"max_tokens", "length", "max_tokens"},
            {"SAFETY", "content_filter", "SAFETY"},
            {"completed", "stop", "completed"},
            {"custom_reason", "custom_reason", "custom_reason"},
        };
        for (String[] c : cases) {
            Completion completion = completion().withText("Hello").withStopReason(c[0]);
            JsonNode choice = encode(Provider.OPENROUTER, completion, "google/gemini-2.5-flash").path("choices").path(0);
            assertThat(c[0] + " finish_reason", choice.path("finish_reason").asText(), is(c[1]));
            assertThat(c[0] + " native_finish_reason", choice.path("native_finish_reason").isTextual(), is(true));
            assertThat(c[0] + " native_finish_reason", choice.path("native_finish_reason").asText(), is(c[2]));

            JsonNode last = finishChunk(stream(Provider.OPENROUTER, completion, "google/gemini-2.5-flash", NO_OPT_IN)).path("choices").path(0);
            assertThat(c[0] + " streamed finish_reason", last.path("finish_reason").asText(), is(c[1]));
            assertThat(c[0] + " streamed native_finish_reason", last.path("native_finish_reason").asText(), is(c[2]));
        }

        JsonNode toolChoice = encode(Provider.OPENROUTER, toolCall("call_1"), "mistralai/mistral-small").path("choices").path(0);
        assertThat(toolChoice.path("finish_reason").asText(), is("tool_calls"));
        assertThat(toolChoice.path("native_finish_reason").asText(), is("tool_calls"));
        JsonNode forced = encode(Provider.OPENROUTER, toolCall("call_1").withStopReason("end_turn").withToolChoice("required"),
            "anthropic/claude-sonnet-4.5").path("choices").path(0);
        assertThat(forced.path("finish_reason").asText(), is("tool_calls"));
        assertThat(forced.path("native_finish_reason").asText(), is("tool_calls"));
    }

    @Test
    public void openRouterStreamCarriesNativeFinishReasonOnEveryChoiceAndRepeatsItOnTheUsageChunk() throws Exception {
        List<JsonNode> chunks = stream(Provider.OPENROUTER, reasoning().withStopReason("end_turn"), "anthropic/claude-sonnet-4.5", NO_OPT_IN);
        JsonNode finish = finishChunk(chunks);
        int finishIndex = chunks.indexOf(finish);
        for (int i = 0; i < finishIndex; i++) {
            JsonNode choice = chunks.get(i).path("choices").path(0);
            assertThat("chunk " + i + " has native_finish_reason", choice.has("native_finish_reason"), is(true));
            assertThat("chunk " + i + " native_finish_reason", choice.get("native_finish_reason").isNull(), is(true));
        }
        assertThat(finish.path("choices").path(0).path("finish_reason").asText(), is("stop"));
        assertThat(finish.path("choices").path(0).path("native_finish_reason").asText(), is("end_turn"));

        JsonNode usageChunk = chunks.get(chunks.size() - 1);
        assertThat(finishIndex, is(chunks.size() - 2));
        JsonNode usageChoice = usageChunk.path("choices").path(0);
        assertThat(usageChoice.path("index").asInt(-1), is(0));
        assertThat(usageChoice.path("delta").path("content").asText("x"), is(""));
        assertThat(usageChoice.path("delta").path("role").asText(), is("assistant"));
        assertThat(usageChoice.path("finish_reason").asText(), is("stop"));
        assertThat(usageChoice.path("native_finish_reason").asText(), is("end_turn"));
        assertThat(usageChunk.path("usage").path("total_tokens").asInt(-1), is(50));
    }

    @Test
    public void onlyOpenRouterAddsNativeFinishReasonOrAUsageChunkChoice() throws Exception {
        for (Provider provider : new Provider[]{Provider.OPENAI, Provider.AZURE_OPENAI, Provider.MISTRAL, Provider.XAI,
            Provider.DEEPSEEK, Provider.GROQ, Provider.ORCAROUTER}) {
            Completion completion = completion().withText("Hello").withStopReason("STOP");
            JsonNode choice = encode(provider, completion, "m").path("choices").path(0);
            assertThat(provider + " native_finish_reason", choice.has("native_finish_reason"), is(false));
            assertThat(provider + " finish_reason", choice.path("finish_reason").asText(), is("STOP"));
            int finishReasons = 0;
            for (JsonNode chunk : stream(provider, completion, "m", INCLUDE_USAGE)) {
                for (JsonNode streamed : chunk.path("choices")) {
                    assertThat(provider + " streamed native_finish_reason", streamed.has("native_finish_reason"), is(false));
                    finishReasons += streamed.path("finish_reason").isTextual() ? 1 : 0;
                }
            }
            assertThat(provider + " chunks carrying finish_reason", finishReasons, is(1));
        }
    }

    @Test
    public void usageStreamedWithoutAnOptInIsReadByTheProxyUsageScanner() {
        for (Provider provider : new Provider[]{Provider.GROQ, Provider.MISTRAL, Provider.OPENROUTER}) {
            StringBuilder body = new StringBuilder();
            for (SseEvent event : codec(provider).encodeStreaming(
                completion().withText("hello there").withUsage(usage().withInputTokens(9).withOutputTokens(3)), "m", null, NO_OPT_IN)) {
                body.append("data: ").append(event.getData()).append("\n\n");
            }
            LlmStreamUsageScanner scanner = new LlmStreamUsageScanner();
            scanner.accept(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Usage usage = LlmClientRegistry.getInstance().lookup(provider).orElseThrow(AssertionError::new).parseUsage(scanner.usage());
            assertThat(provider + " input", usage.getInputTokens(), is(9));
            assertThat(provider + " output", usage.getOutputTokens(), is(3));
        }
    }

    // ---- choice logprobs ----

    @Test
    public void deepSeekGroqAndOpenRouterChoicesCarryNullLogprobsAndTheOthersOmitIt() throws Exception {
        // DeepSeek's reference marks it required (nullable); Groq's example and OpenRouter's
        // ChatChoice example and recorded responses send it; Mistral, xAI and OrcaRouter do not
        Completion[] completions = {
            completion().withText("Hello"),
            completion().withToolCall(toolUse("get_weather").withArguments("{}"))
        };
        for (Provider provider : Provider.values()) {
            if (!(codec(provider) instanceof OpenAiCompatibleChatCodec)) {
                continue;
            }
            boolean expected = provider == Provider.DEEPSEEK || provider == Provider.GROQ || provider == Provider.OPENROUTER;
            for (Completion completion : completions) {
                JsonNode choice = encode(provider, completion, "m").path("choices").path(0);
                assertThat(provider + " logprobs present", choice.has("logprobs"), is(expected));
                if (expected) {
                    assertThat(provider + " logprobs null", choice.get("logprobs").isNull(), is(true));
                }
            }
        }
    }

    // ---- OpenAI itself is unchanged ----

    @Test
    public void openAiChatCompletionsHasNoReasoningTextFieldAndStillNeedsTheOptIn() throws Exception {
        for (Provider provider : new Provider[]{Provider.OPENAI, Provider.AZURE_OPENAI, Provider.ORCAROUTER}) {
            JsonNode message = encode(provider, reasoning(), "gpt-4o").path("choices").path(0).path("message");
            assertThat(provider + " fields", OBJECT_MAPPER.convertValue(message, java.util.Map.class).keySet(),
                is(new java.util.LinkedHashSet<>(java.util.Arrays.asList("role", "content"))));
            List<JsonNode> chunks = stream(provider, reasoning(), "gpt-4o", NO_OPT_IN);
            for (JsonNode chunk : chunks) {
                assertThat(provider + " usage", chunk.has("usage"), is(false));
                assertThat(provider + " x_groq", chunk.has("x_groq"), is(false));
            }
            assertThat(deltaText(chunks, "content"), is("The answer is 4."));
        }
    }

    // ---- configured tool-call id ----

    private static Completion toolCall(String id) {
        return completion().withToolCall(toolUse("get_weather").withId(id).withArguments("{\"city\":\"London\"}"))
            .withUsage(usage().withInputTokens(10).withOutputTokens(5));
    }

    @Test
    public void openAiFamilyUsesTheConfiguredToolCallId() throws Exception {
        for (Provider provider : new Provider[]{Provider.OPENAI, Provider.AZURE_OPENAI, Provider.MISTRAL, Provider.XAI,
            Provider.DEEPSEEK, Provider.GROQ, Provider.OPENROUTER, Provider.ORCAROUTER}) {
            assertThat(provider + " encode", encode(provider, toolCall("call_\"fixed\\123"), "m")
                .path("choices").path(0).path("message").path("tool_calls").path(0).path("id").asText(), is("call_\"fixed\\123"));
            String streamed = null;
            for (JsonNode chunk : stream(provider, toolCall("call_\"fixed\\123"), "m", NO_OPT_IN)) {
                JsonNode id = chunk.path("choices").path(0).path("delta").path("tool_calls").path(0).path("id");
                if (id.isTextual()) {
                    streamed = id.asText();
                }
            }
            assertThat(provider + " stream", streamed, is("call_\"fixed\\123"));
            assertThat(provider + " generated", encode(provider, toolCall(null), "m")
                .path("choices").path(0).path("message").path("tool_calls").path(0).path("id").asText(), startsWith("call_"));
        }
    }

    @Test
    public void anthropicAndBedrockUseTheConfiguredToolUseId() throws Exception {
        for (Provider provider : new Provider[]{Provider.ANTHROPIC, Provider.BEDROCK}) {
            JsonNode content = encode(provider, toolCall("toolu_\"fixed"), "claude-sonnet-4-5").path("content");
            assertThat(provider + " encode", content.path(0).path("id").asText(), is("toolu_\"fixed"));
            assertThat(provider + " generated", encode(provider, toolCall(""), "claude-sonnet-4-5")
                .path("content").path(0).path("id").asText(), startsWith("toolu_"));
        }
        String streamedId = null;
        for (SseEvent event : codec(Provider.ANTHROPIC).encodeStreaming(toolCall("toolu_\"fixed"), "claude-sonnet-4-5", null)) {
            if ("content_block_start".equals(event.getEvent())) {
                streamedId = OBJECT_MAPPER.readTree(event.getData()).path("content_block").path("id").asText();
            }
        }
        assertThat(streamedId, is("toolu_\"fixed"));
    }

    @Test
    public void geminiSendsFunctionCallIdOnlyWhenConfigured() throws Exception {
        JsonNode call = encode(Provider.GEMINI, toolCall("fc-\"1"), "gemini-2.5-flash")
            .path("candidates").path(0).path("content").path("parts").path(0).path("functionCall");
        assertThat(call.path("id").asText(), is("fc-\"1"));
        assertThat(call.path("name").asText(), is("get_weather"));
        assertThat(encode(Provider.GEMINI, toolCall(null), "gemini-2.5-flash")
            .path("candidates").path(0).path("content").path("parts").path(0).path("functionCall").has("id"), is(false));
        String streamedId = null;
        for (SseEvent event : codec(Provider.GEMINI).encodeStreaming(toolCall("fc-\"1"), "gemini-2.5-flash", null)) {
            JsonNode functionCall = OBJECT_MAPPER.readTree(event.getData()).path("candidates").path(0).path("content").path("parts").path(0).path("functionCall");
            if (functionCall.isObject()) {
                streamedId = functionCall.path("id").asText();
                assertThat(functionCall.path("name").asText(), is("get_weather"));
            }
        }
        assertThat(streamedId, is("fc-\"1"));
    }

    @Test
    public void ollamaSendsToolCallIdOnlyWhenConfigured() throws Exception {
        JsonNode call = encode(Provider.OLLAMA, toolCall("call_abc"), "llama3.2").path("message").path("tool_calls").path(0);
        assertThat(call.path("id").asText(), is("call_abc"));
        assertThat(call.path("function").path("name").asText(), is("get_weather"));
        assertThat(encode(Provider.OLLAMA, toolCall(null), "llama3.2").path("message").path("tool_calls").path(0).has("id"), is(false));
        String streamedId = null;
        for (SseEvent event : codec(Provider.OLLAMA).encodeStreaming(toolCall("call_abc"), "llama3.2", null)) {
            JsonNode toolCalls = OBJECT_MAPPER.readTree(event.getData()).path("message").path("tool_calls");
            if (toolCalls.isArray() && toolCalls.size() > 0) {
                streamedId = toolCalls.path(0).path("id").asText();
            }
        }
        assertThat(streamedId, is("call_abc"));
    }

    // ---- empty OpenAI embedding input ----

    private final HttpLlmResponseActionHandler handler = new HttpLlmResponseActionHandler(new MockServerLogger());

    private HttpResponse embed(Provider provider, String path, String body) {
        return handler.handle(
            llmResponse().withProvider(provider).withEmbedding(EmbeddingResponse.embedding().withDimensions(4)),
            request().withMethod("POST").withPath(path).withBody(body));
    }

    @Test
    public void openAiFamilyRejectsAnEmptyEmbeddingInputWithAnInvalidRequestError() throws Exception {
        String[] emptyInputs = {"\"\"", "[]", "[\"\"]", "[\"a\",\"\"]", "[[]]", "[[1,2],[]]"};
        for (Provider provider : new Provider[]{Provider.OPENAI, Provider.AZURE_OPENAI, Provider.MISTRAL}) {
            for (String input : emptyInputs) {
                HttpResponse response = embed(provider, "/v1/embeddings", "{\"model\":\"text-embedding-3-small\",\"input\":" + input + "}");
                String ctx = provider + " " + input;
                assertThat(ctx, response.getStatusCode(), is(400));
                JsonNode error = OBJECT_MAPPER.readTree(response.getBodyAsString()).path("error");
                assertThat(ctx, error.path("type").asText(), is("invalid_request_error"));
                assertThat(ctx, error.path("param").isNull(), is(true));
                assertThat(ctx, error.path("message").asText().isEmpty(), is(false));
            }
            for (String input : new String[]{"\"x\"", "[\"a\",\"b\"]", "[1,2,3]", "[[1],[2]]"}) {
                assertThat(provider + " " + input, embed(provider, "/v1/embeddings", "{\"input\":" + input + "}").getStatusCode(), is(200));
            }
        }
    }

    @Test
    public void ollamaStillAcceptsAnEmptyEmbedInput() throws Exception {
        HttpResponse response = embed(Provider.OLLAMA, "/api/embed", "{\"model\":\"all-minilm\",\"input\":\"\"}");
        assertThat(response.getStatusCode(), is(200));
    }
}
