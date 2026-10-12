package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.ProviderCodec;
import org.mockserver.model.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.ToolUse.toolUse;

public class OpenAiChatCompletionsCodecTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final OpenAiChatCompletionsCodec codec = new OpenAiChatCompletionsCodec();

    @Test
    public void shouldReturnOpenAiProviderAndVersion() {
        assertThat(codec.provider(), is(Provider.OPENAI));
        assertThat(codec.apiVersion(), is("2025-01"));
    }

    @Test
    public void shouldEncodeTextOnlyCompletion() throws Exception {
        // given
        Completion completion = completion()
            .withText("Hello, world!")
            .withUsage(Usage.usage().withInputTokens(10).withOutputTokens(3));

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        assertThat(response.getStatusCode(), is(200));
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("id").asText(), startsWith("chatcmpl-"));
        assertThat(root.get("object").asText(), is("chat.completion"));
        assertThat(root.has("created"), is(true));
        assertThat(root.get("model").asText(), is("gpt-4o"));

        JsonNode choices = root.get("choices");
        assertThat(choices.size(), is(1));
        JsonNode choice = choices.get(0);
        assertThat(choice.get("index").asInt(), is(0));
        assertThat(choice.get("message").get("role").asText(), is("assistant"));
        assertThat(choice.get("message").get("content").asText(), is("Hello, world!"));
        assertThat(choice.get("finish_reason").asText(), is("stop"));

        // tool_calls should be omitted
        assertThat(choice.get("message").has("tool_calls"), is(false));

        JsonNode usage = root.get("usage");
        assertThat(usage.get("prompt_tokens").asInt(), is(10));
        assertThat(usage.get("completion_tokens").asInt(), is(3));
        assertThat(usage.get("total_tokens").asInt(), is(13));
    }

    @Test
    public void everyChoiceCarriesTheSpecRequiredNullLogprobs() throws Exception {
        Completion[] completions = {
            completion().withText("Hello"),
            completion().withToolCall(toolUse("get_weather").withArguments("{}"))
        };
        ProviderCodec[] codecs = {codec, new AzureOpenAiCodec()};
        for (ProviderCodec providerCodec : codecs) {
            for (Completion completion : completions) {
                JsonNode choice = OBJECT_MAPPER.readTree(providerCodec.encode(completion, "gpt-4o").getBodyAsString())
                    .path("choices").path(0);
                assertThat(providerCodec.provider() + " logprobs present", choice.has("logprobs"), is(true));
                assertThat(providerCodec.provider() + " logprobs null", choice.get("logprobs").isNull(), is(true));
            }
        }
    }

    @Test
    public void shouldOmitToolCallsWhenNone() throws Exception {
        // given
        Completion completion = completion().withText("test");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode message = root.get("choices").get(0).get("message");
        assertThat(message.has("tool_calls"), is(false));
    }

    @Test
    public void shouldSetContentToNullWithToolCallsOnly() throws Exception {
        // given
        Completion completion = completion()
            .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Paris\"}"));

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode message = root.get("choices").get(0).get("message");
        assertThat(message.get("content").isNull(), is(true));
        assertThat(message.has("tool_calls"), is(true));
        assertThat(message.get("tool_calls").size(), is(1));
    }

    @Test
    public void shouldMapFinishReasonEndTurnToStop() throws Exception {
        // given
        Completion completion = completion()
            .withText("test")
            .withStopReason("end_turn");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("stop"));
    }

    @Test
    public void shouldMapFinishReasonToolUseToToolCalls() throws Exception {
        // given
        Completion completion = completion()
            .withToolCall(toolUse("tool").withArguments("{}"))
            .withStopReason("tool_use");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("tool_calls"));
    }

    @Test
    public void shouldMapFinishReasonMaxTokensToLength() throws Exception {
        // given
        Completion completion = completion()
            .withText("test")
            .withStopReason("max_tokens");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("length"));
    }

    @Test
    public void shouldPassThroughValidOpenAiFinishReasons() throws Exception {
        for (String reason : new String[]{"stop", "length", "tool_calls", "content_filter"}) {
            Completion completion = completion()
                .withText("test")
                .withStopReason(reason);

            HttpResponse response = codec.encode(completion, "gpt-4o");

            JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
            assertThat(root.get("choices").get(0).get("finish_reason").asText(), is(reason));
        }
    }

    @Test
    public void shouldDefaultFinishReasonToStopForTextOnly() throws Exception {
        // given — no stop reason set, no tool calls
        Completion completion = completion().withText("test");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("stop"));
    }

    @Test
    public void shouldDefaultFinishReasonToToolCallsWhenToolCallsPresent() throws Exception {
        // given — no stop reason set, but has tool calls
        Completion completion = completion()
            .withToolCall(toolUse("search").withArguments("{}"));

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("tool_calls"));
    }

    @Test
    public void shouldEncodeToolCallArgumentsAsString() throws Exception {
        // given — OpenAI's tool_calls[].function.arguments is always a JSON-as-string
        Completion completion = completion()
            .withToolCall(toolUse("fn").withArguments("{\"key\":\"value\"}"));

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode toolCall = root.get("choices").get(0).get("message").get("tool_calls").get(0);
        assertThat(toolCall.get("id").asText(), startsWith("call_"));
        assertThat(toolCall.get("type").asText(), is("function"));
        assertThat(toolCall.get("function").get("name").asText(), is("fn"));
        // arguments should be a string, not a parsed object
        assertThat(toolCall.get("function").get("arguments").isTextual(), is(true));
        assertThat(toolCall.get("function").get("arguments").asText(), is("{\"key\":\"value\"}"));
    }

    @Test
    public void shouldSetContentTypeHeader() throws Exception {
        // given
        Completion completion = completion().withText("test");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
    }

    @Test
    public void shouldDefaultUsageToZeros() throws Exception {
        // given
        Completion completion = completion().withText("test");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode usage = root.get("usage");
        assertThat(usage.get("prompt_tokens").asInt(), is(0));
        assertThat(usage.get("completion_tokens").asInt(), is(0));
        assertThat(usage.get("total_tokens").asInt(), is(0));
    }

    @Test
    public void shouldForceToolCallsFinishReasonWhenToolChoiceRequired() throws Exception {
        // given — tool_choice=required forces tool_calls even if a stop reason says otherwise
        Completion completion = completion()
            .withToolCall(toolUse("fn").withArguments("{}"))
            .withStopReason("end_turn")
            .withToolChoice("required");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("tool_calls"));
    }

    private String concatStreamedContent(java.util.List<SseEvent> events) throws Exception {
        StringBuilder text = new StringBuilder();
        for (SseEvent e : events) {
            if ("[DONE]".equals(e.getData())) {
                continue;
            }
            JsonNode delta = OBJECT_MAPPER.readTree(e.getData()).get("choices").get(0).get("delta");
            if (delta != null && delta.has("content")) {
                // role-only first chunk carries content:"" so appending it is harmless
                text.append(delta.get("content").asText());
            }
        }
        return text.toString();
    }

    @Test
    public void shouldEmitSubwordDeltasByDefaultAndWholeWordWhenDisabled() throws Exception {
        // given
        Completion completion = completion().withText("MockServer streams tokens quickly");

        // when — explicit whole-word (opt-out) vs subword (the default, selected either
        // by an explicit flag or simply by a null physics)
        java.util.List<SseEvent> wholeWord = codec.encodeStreaming(completion, "gpt-4o",
            StreamingPhysics.streamingPhysics().withSubwordStreaming(false));
        java.util.List<SseEvent> subword = codec.encodeStreaming(completion, "gpt-4o",
            StreamingPhysics.streamingPhysics().withSubwordStreaming(true));
        java.util.List<SseEvent> defaultMode = codec.encodeStreaming(completion, "gpt-4o", null);

        // then — subword mode produces strictly more events (finer deltas) than whole-word ...
        assertThat(subword.size(), is(greaterThan(wholeWord.size())));
        // ... and the default (null physics) now matches the subword granularity
        assertThat(defaultMode.size(), is(subword.size()));
        // ... but the reconstructed text is identical in all modes
        assertThat(concatStreamedContent(subword), is("MockServer streams tokens quickly"));
        assertThat(concatStreamedContent(wholeWord), is("MockServer streams tokens quickly"));
        assertThat(concatStreamedContent(defaultMode), is("MockServer streams tokens quickly"));
    }

    @Test
    public void shouldForceToolCallsFinishReasonWhenToolChoiceRequiredInStreaming() throws Exception {
        // given
        Completion completion = completion()
            .withText("ignored")
            .withToolCall(toolUse("fn").withArguments("{}"))
            .withStopReason("end_turn")
            .withToolChoice("required");

        // when
        java.util.List<SseEvent> events = codec.encodeStreaming(completion, "gpt-4o", null);

        // then — the final non-[DONE] chunk carries finish_reason tool_calls
        String lastChunk = null;
        for (SseEvent e : events) {
            if (!"[DONE]".equals(e.getData())) {
                lastChunk = e.getData();
            }
        }
        JsonNode root = OBJECT_MAPPER.readTree(lastChunk);
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("tool_calls"));
    }

    @Test
    public void shouldIgnoreToolChoiceRequiredWhenNoToolCalls() throws Exception {
        // given — required but no tool call configured: behaviour unchanged (stop)
        Completion completion = completion().withText("hi").withToolChoice("required");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("stop"));
    }

    @Test
    public void shouldLeaveFinishReasonUnchangedWhenToolChoiceAbsent() throws Exception {
        // given — back-compat: no tool_choice, a stop reason is honoured as before
        Completion completion = completion()
            .withToolCall(toolUse("fn").withArguments("{}"))
            .withStopReason("end_turn");

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then — "end_turn" maps to "stop", unchanged from prior behaviour
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("stop"));
    }

    @Test
    public void shouldDefaultToToolCallsFinishReasonWhenToolCallsPresentAndNoStopReasonOrToolChoice() throws Exception {
        // given — back-compat: tool calls present, no stopReason, no toolChoice
        Completion completion = completion()
            .withToolCall(toolUse("fn").withArguments("{}"));

        // when
        HttpResponse response = codec.encode(completion, "gpt-4o");

        // then — defaults to tool_calls exactly as before this change
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("choices").get(0).get("finish_reason").asText(), is("tool_calls"));
    }
}
