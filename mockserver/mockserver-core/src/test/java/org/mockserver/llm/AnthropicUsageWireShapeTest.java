package org.mockserver.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.codec.AnthropicCodec;
import org.mockserver.llm.codec.BedrockCodec;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.SseEvent;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.Usage.usage;

/**
 * The usage objects the Claude API and Bedrock InvokeModel send, field for field and in their order, as
 * recorded from the live APIs on 2026-09-28/29 in pydantic-ai's cassettes (Claude API messages and
 * streams, Bedrock invoke-with-response-stream) and typed as Usage and MessageDeltaUsage in the Messages
 * API reference. Bedrock sends no {@code inference_geo}; {@code output_tokens_details} appears only for a
 * model that reports reasoning, which a mock expresses by setting {@code reasoningTokens}.
 */
public class AnthropicUsageWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpRequest INVOKE = request().withPath("/model/anthropic.claude-opus-4-8/invoke");
    private static final HttpRequest INVOKE_STREAM = request().withPath("/model/anthropic.claude-opus-4-8/invoke-with-response-stream");

    private static final String CACHE_ZERO = "\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":0,"
        + "\"cache_creation\":{\"ephemeral_5m_input_tokens\":0,\"ephemeral_1h_input_tokens\":0}";

    @Test
    public void claudeApiMessageSendsEveryUsageFieldWithZeroCacheCounts() throws Exception {
        JsonNode body = message(new AnthropicCodec().encode(plain(), "m").getBodyAsString());

        assertThat(body.get("usage").toString(), is("{\"input_tokens\":22," + CACHE_ZERO
            + ",\"output_tokens\":5,\"service_tier\":\"standard\",\"inference_geo\":\"global\"}"));
    }

    @Test
    public void claudeApiMessageSendsCacheCountsAndThinkingTokens() throws Exception {
        JsonNode body = message(new AnthropicCodec().encode(cachedAndThinking(), "m").getBodyAsString());

        assertThat(body.get("usage").toString(), is("{\"input_tokens\":3,\"cache_creation_input_tokens\":2269,"
            + "\"cache_read_input_tokens\":9511,\"cache_creation\":{\"ephemeral_5m_input_tokens\":2269,\"ephemeral_1h_input_tokens\":0},"
            + "\"output_tokens\":63,\"output_tokens_details\":{\"thinking_tokens\":57},"
            + "\"service_tier\":\"standard\",\"inference_geo\":\"global\"}"));
    }

    @Test
    public void claudeApiStreamSendsMessageStartAndCumulativeMessageDeltaUsage() throws Exception {
        List<SseEvent> events = new AnthropicCodec().encodeStreaming(plain().withStreaming(true), "m", null);

        assertThat(event("message_start", events).at("/message/usage").toString(), is("{\"input_tokens\":22," + CACHE_ZERO
            + ",\"output_tokens\":1,\"service_tier\":\"standard\",\"inference_geo\":\"global\"}"));
        assertThat(event("message_delta", events).get("usage").toString(), is("{\"input_tokens\":22,"
            + "\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":0,\"output_tokens\":5}"));
    }

    @Test
    public void claudeApiStreamSendsThinkingTokensOnlyOnMessageDelta() throws Exception {
        List<SseEvent> events = new AnthropicCodec().encodeStreaming(cachedAndThinking().withStreaming(true), "m", null);

        assertThat(event("message_start", events).at("/message/usage").toString(), is("{\"input_tokens\":3,"
            + "\"cache_creation_input_tokens\":2269,\"cache_read_input_tokens\":9511,"
            + "\"cache_creation\":{\"ephemeral_5m_input_tokens\":2269,\"ephemeral_1h_input_tokens\":0},"
            + "\"output_tokens\":1,\"service_tier\":\"standard\",\"inference_geo\":\"global\"}"));
        assertThat(event("message_delta", events).get("usage").toString(), is("{\"input_tokens\":3,"
            + "\"cache_creation_input_tokens\":2269,\"cache_read_input_tokens\":9511,\"output_tokens\":63,"
            + "\"output_tokens_details\":{\"thinking_tokens\":57}}"));
    }

    @Test
    public void reasoningTokensOfZeroStillSendsTheBreakdown() throws Exception {
        Completion completion = completion().withText("hi").withUsage(usage().withInputTokens(68).withOutputTokens(422).withReasoningTokens(0));

        JsonNode body = message(new AnthropicCodec().encode(completion, "m").getBodyAsString());
        List<SseEvent> events = new AnthropicCodec().encodeStreaming(completion.withStreaming(true), "m", null);

        assertThat(body.at("/usage/output_tokens_details").toString(), is("{\"thinking_tokens\":0}"));
        assertThat(event("message_delta", events).at("/usage/output_tokens_details").toString(), is("{\"thinking_tokens\":0}"));
    }

    @Test
    public void completionWithoutUsageSendsZeroCounts() throws Exception {
        JsonNode body = message(new AnthropicCodec().encode(completion().withText("hi"), "m").getBodyAsString());

        assertThat(body.get("usage").toString(), is("{\"input_tokens\":0," + CACHE_ZERO
            + ",\"output_tokens\":0,\"service_tier\":\"standard\",\"inference_geo\":\"global\"}"));
    }

    @Test
    public void bedrockInvokeModelMessageSendsUsageWithoutInferenceGeo() throws Exception {
        JsonNode body = message(new BedrockCodec().encode(plain(), "m", INVOKE).getBodyAsString());

        assertThat(body.get("usage").toString(), is("{\"input_tokens\":22," + CACHE_ZERO
            + ",\"output_tokens\":5,\"service_tier\":\"standard\"}"));
    }

    @Test
    public void bedrockInvokeModelStreamSendsUsageWithoutInferenceGeoAndThinkingTokensOnMessageDelta() throws Exception {
        Completion completion = completion().withText("hi").withStreaming(true)
            .withUsage(usage().withInputTokens(68).withOutputTokens(422).withReasoningTokens(0));

        List<SseEvent> events = new BedrockCodec().encodeStreaming(completion, "m", null, INVOKE_STREAM);

        assertThat(event("message_start", events).at("/message/usage").toString(), is("{\"input_tokens\":68," + CACHE_ZERO
            + ",\"output_tokens\":1,\"service_tier\":\"standard\"}"));
        assertThat(event("message_delta", events).get("usage").toString(), is("{\"input_tokens\":68,"
            + "\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":0,\"output_tokens\":422,"
            + "\"output_tokens_details\":{\"thinking_tokens\":0}}"));
    }

    private static Completion plain() {
        return completion().withText("391").withUsage(usage().withInputTokens(22).withOutputTokens(5));
    }

    private static Completion cachedAndThinking() {
        return completion().withText("391").withReasoningText("17 * 23 = 391").withReasoningSignature("sig")
            .withUsage(usage().withInputTokens(3).withOutputTokens(63).withReasoningTokens(57)
                .withCachedInputTokens(9511).withCacheCreationTokens(2269));
    }

    private static JsonNode message(String body) throws Exception {
        return MAPPER.readTree(body);
    }

    private static JsonNode event(String type, List<SseEvent> events) throws Exception {
        for (SseEvent event : events) {
            if (type.equals(event.getEvent())) {
                return MAPPER.readTree(event.getData());
            }
        }
        throw new AssertionError("no " + type + " event in " + events);
    }
}
