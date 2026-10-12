package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.LlmContentFilterBodies;
import org.mockserver.llm.LlmErrorBodies;
import org.mockserver.llm.client.GeminiLlmClient;
import org.mockserver.llm.client.OllamaLlmClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpLlmResponseActionHandler;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpLlmResponse;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.LlmChaosProfile;
import org.mockserver.model.Provider;
import org.mockserver.model.SseEvent;
import org.mockserver.model.Usage;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.ToolUse.toolUse;
import static org.mockserver.model.Usage.usage;

/**
 * Wire shapes checked against each provider's published API:
 * <ul>
 *   <li>Gemini {@code UsageMetadata}: {@code totalTokenCount} is prompt + thoughts + candidates,
 *       so {@code candidatesTokenCount} excludes thoughts (ai.google.dev/api/generate-content).</li>
 *   <li>Ollama {@code ChatResponse.done_reason}: {@code stop} or {@code length} on the final
 *       message (ollama api/types.go, llm/server.go).</li>
 *   <li>Anthropic {@code StopReason}: end_turn, max_tokens, stop_sequence, tool_use, pause_turn,
 *       refusal, model_context_window_exceeded (anthropic-sdk-python types/stop_reason.py).</li>
 *   <li>Bedrock Runtime errors are AWS exceptions on InvokeModel and Converse alike, and a Converse
 *       response carries {@code stopReason} {@code content_filtered} (Bedrock API reference).</li>
 * </ul>
 */
public class ProviderStopReasonAndUsageWireShapeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JsonNode json(String body) throws Exception {
        return OBJECT_MAPPER.readTree(body);
    }

    private static JsonNode last(List<SseEvent> events) throws Exception {
        return json(events.get(events.size() - 1).getData());
    }

    // ---- Gemini: thoughts are not part of candidatesTokenCount ----

    private static final Completion GEMINI_THINKING = completion().withText("hi")
        .withUsage(usage().withInputTokens(8).withOutputTokens(39).withReasoningTokens(27));

    @Test
    public void geminiSplitsOutputIntoCandidatesAndThoughts() throws Exception {
        JsonNode usageMetadata = json(new GeminiCodec().encode(GEMINI_THINKING, "gemini-2.5-flash").getBodyAsString()).path("usageMetadata");

        assertThat(usageMetadata.path("promptTokenCount").asInt(-1), is(8));
        assertThat(usageMetadata.path("candidatesTokenCount").asInt(-1), is(12));
        assertThat(usageMetadata.path("thoughtsTokenCount").asInt(-1), is(27));
        assertThat(usageMetadata.path("totalTokenCount").asInt(-1), is(47));
    }

    @Test
    public void geminiStreamSplitsOutputIntoCandidatesAndThoughts() throws Exception {
        JsonNode usageMetadata = last(new GeminiCodec().encodeStreaming(GEMINI_THINKING, "gemini-2.5-flash", null)).path("usageMetadata");

        assertThat(usageMetadata.path("candidatesTokenCount").asInt(-1), is(12));
        assertThat(usageMetadata.path("thoughtsTokenCount").asInt(-1), is(27));
        assertThat(usageMetadata.path("totalTokenCount").asInt(-1), is(47));
    }

    @Test
    public void geminiWithoutThoughtsKeepsAllOutputAsCandidates() throws Exception {
        JsonNode usageMetadata = json(new GeminiCodec().encode(completion().withText("hi")
            .withUsage(usage().withInputTokens(8).withOutputTokens(12)), "gemini-2.5-flash").getBodyAsString()).path("usageMetadata");

        assertThat(usageMetadata.path("candidatesTokenCount").asInt(-1), is(12));
        assertThat(usageMetadata.path("totalTokenCount").asInt(-1), is(20));
        assertThat(usageMetadata.has("thoughtsTokenCount"), is(false));
    }

    @Test
    public void geminiClientCountsThoughtsAsOutput() {
        // usageMetadata recorded from gemini-2.5-flash: 8 prompt, 12 candidates, 27 thoughts, 47 total
        Usage parsed = new GeminiLlmClient().parseCompletionResponse(response().withBody(
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hi\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}],"
                + "\"usageMetadata\":{\"promptTokenCount\":8,\"candidatesTokenCount\":12,\"totalTokenCount\":47,\"thoughtsTokenCount\":27}}")).getUsage();

        assertThat(parsed.getInputTokens(), is(8));
        assertThat(parsed.getOutputTokens(), is(39));
        assertThat(parsed.getReasoningTokens(), is(27));
    }

    @Test
    public void geminiUsageRoundTripsThroughCodecAndClient() {
        Usage parsed = new GeminiLlmClient().parseCompletionResponse(new GeminiCodec().encode(GEMINI_THINKING, "gemini-2.5-flash")).getUsage();

        assertThat(parsed.getInputTokens(), is(8));
        assertThat(parsed.getOutputTokens(), is(39));
        assertThat(parsed.getReasoningTokens(), is(27));
    }

    // ---- Ollama: done_reason on the final message ----

    @Test
    public void ollamaWritesDoneReasonStopByDefault() throws Exception {
        assertThat(json(new OllamaCodec().encode(completion().withText("hi"), "llama3.1").getBodyAsString()).path("done_reason").asText(), is("stop"));
    }

    @Test
    public void ollamaWritesDoneReasonStopForToolCalls() throws Exception {
        Completion toolCall = completion().withToolCall(toolUse("get_weather").withArguments("{\"city\":\"London\"}"));

        assertThat(json(new OllamaCodec().encode(toolCall, "llama3.1").getBodyAsString()).path("done_reason").asText(), is("stop"));
        assertThat(json(new OllamaCodec().encode(toolCall.withStopReason("tool_calls"), "llama3.1").getBodyAsString()).path("done_reason").asText(), is("stop"));
    }

    @Test
    public void ollamaMapsMaxTokenStopReasonsToLength() throws Exception {
        for (String stopReason : new String[]{"length", "max_tokens", "MAX_TOKENS"}) {
            JsonNode root = json(new OllamaCodec().encode(completion().withText("hi").withStopReason(stopReason), "llama3.1").getBodyAsString());
            assertThat(stopReason, root.path("done_reason").asText(), is("length"));
        }
    }

    @Test
    public void ollamaMapsOtherProvidersEndOfTurnToStop() throws Exception {
        for (String stopReason : new String[]{"end_turn", "STOP", "stop_sequence", "content_filter"}) {
            JsonNode root = json(new OllamaCodec().encode(completion().withText("hi").withStopReason(stopReason), "llama3.1").getBodyAsString());
            assertThat(stopReason, root.path("done_reason").asText(), is("stop"));
        }
    }

    @Test
    public void ollamaStreamWritesDoneReasonOnlyOnTheFinalLine() throws Exception {
        List<SseEvent> events = new OllamaCodec().encodeStreaming(completion().withText("hello there").withStopReason("length"), "llama3.1", null);

        for (int i = 0; i < events.size() - 1; i++) {
            assertThat(json(events.get(i).getData()).has("done_reason"), is(false));
        }
        JsonNode finalLine = last(events);
        assertThat(finalLine.path("done").asBoolean(false), is(true));
        assertThat(finalLine.path("done_reason").asText(), is("length"));
    }

    @Test
    public void ollamaClientReadsTheEncodedDoneReason() {
        Completion parsed = new OllamaLlmClient().parseCompletionResponse(
            new OllamaCodec().encode(completion().withText("hi").withStopReason("max_tokens"), "llama3.1"));

        assertThat(parsed.getStopReason(), is("length"));
    }

    // ---- Anthropic (and Bedrock InvokeModel): stop_reason in Anthropic's enum ----

    @Test
    public void anthropicTranslatesOtherProvidersStopReasons() throws Exception {
        String[][] cases = {
            {"stop", "end_turn"}, {"STOP", "end_turn"},
            {"length", "max_tokens"}, {"MAX_TOKENS", "max_tokens"},
            {"tool_calls", "tool_use"}, {"function_call", "tool_use"},
            {"content_filter", "refusal"}, {"content_filtered", "refusal"}, {"guardrail_intervened", "refusal"}, {"SAFETY", "refusal"},
        };
        for (String[] c : cases) {
            Completion completion = completion().withText("hi").withStopReason(c[0]);
            assertThat(c[0], json(new AnthropicCodec().encode(completion, "claude").getBodyAsString()).path("stop_reason").asText(), is(c[1]));
            assertThat(c[0] + " stream", messageDeltaStopReason(new AnthropicCodec().encodeStreaming(completion, "claude", null)), is(c[1]));
        }
    }

    @Test
    public void anthropicPassesItsOwnStopReasonsThrough() throws Exception {
        for (String stopReason : new String[]{"end_turn", "max_tokens", "stop_sequence", "tool_use", "pause_turn", "refusal", "model_context_window_exceeded"}) {
            Completion completion = completion().withText("hi").withStopReason(stopReason);
            assertThat(stopReason, json(new AnthropicCodec().encode(completion, "claude").getBodyAsString()).path("stop_reason").asText(), is(stopReason));
        }
    }

    @Test
    public void bedrockInvokeModelTranslatesStopReasonsLikeAnthropic() throws Exception {
        Completion completion = completion().withText("hi").withStopReason("length");
        HttpRequest invoke = request().withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke");

        assertThat(json(new BedrockCodec().encode(completion, "claude", invoke).getBodyAsString()).path("stop_reason").asText(), is("max_tokens"));
        assertThat(messageDeltaStopReason(new BedrockCodec().encodeStreaming(completion, "claude", null, invoke)), is("max_tokens"));
    }

    private static String messageDeltaStopReason(List<SseEvent> events) throws Exception {
        for (SseEvent event : events) {
            if ("message_delta".equals(event.getEvent())) {
                return json(event.getData()).path("delta").path("stop_reason").asText();
            }
        }
        throw new AssertionError("no message_delta event");
    }

    // ---- Bedrock: AWS error envelopes and the Converse content-filter block ----

    @Test
    public void bedrockErrorsUseTheAwsExceptionForTheStatus() throws Exception {
        Object[][] cases = {
            {429, LlmErrorBodies.Kind.RATE_LIMIT, "ThrottlingException"},
            {503, LlmErrorBodies.Kind.SERVER_ERROR, "ServiceUnavailableException"},
            {529, LlmErrorBodies.Kind.OVERLOADED, "ServiceUnavailableException"},
            {500, LlmErrorBodies.Kind.SERVER_ERROR, "InternalServerException"},
            {502, LlmErrorBodies.Kind.SERVER_ERROR, "InternalServerException"},
            {400, LlmErrorBodies.Kind.SERVER_ERROR, "ValidationException"},
            {403, LlmErrorBodies.Kind.SERVER_ERROR, "AccessDeniedException"},
            {404, LlmErrorBodies.Kind.SERVER_ERROR, "ResourceNotFoundException"},
            {408, LlmErrorBodies.Kind.SERVER_ERROR, "ModelTimeoutException"},
            {424, LlmErrorBodies.Kind.SERVER_ERROR, "ModelErrorException"},
        };
        for (Object[] c : cases) {
            JsonNode body = json(LlmErrorBodies.bodyFor(Provider.BEDROCK, (LlmErrorBodies.Kind) c[1], (Integer) c[0], "say \"hi\""));
            assertThat(c[0] + " __type", body.path("__type").asText(), is(c[2]));
            assertThat(c[0] + " message", body.path("message").asText(), is("say \"hi\""));
            assertThat(c[0] + " no Anthropic envelope", body.has("error") || body.has("type"), is(false));
        }
    }

    @Test
    public void bedrockConverseContentFilterBlockIsAConverseResponse() throws Exception {
        LlmContentFilterBodies.Block block = LlmContentFilterBodies.blockFor(Provider.BEDROCK, null,
            request().withPath("/model/amazon.nova-pro-v1:0/converse"));

        assertThat(block.getStatusCode(), is(200));
        JsonNode body = json(block.getJsonBody());
        assertThat(body.path("stopReason").asText(), is("content_filtered"));
        assertThat(body.at("/output/message/role").asText(), is("assistant"));
        assertThat(body.at("/output/message/content").isArray(), is(true));
        assertThat(body.at("/usage/inputTokens").isInt(), is(true));
        assertThat(body.at("/usage/outputTokens").isInt(), is(true));
        assertThat(body.at("/usage/totalTokens").isInt(), is(true));
        assertThat(body.at("/metrics/latencyMs").isInt(), is(true));
    }

    @Test
    public void bedrockConverseStreamAndBodyShapedRequestsGetTheConverseBlock() throws Exception {
        HttpRequest stream = request().withPath("/model/amazon.nova-pro-v1:0/converse-stream");
        HttpRequest bodyShaped = request().withPath("/gateway/bedrock").withBody("{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"hi\"}]}],\"inferenceConfig\":{}}");

        assertThat(json(LlmContentFilterBodies.blockFor(Provider.BEDROCK, null, stream).getJsonBody()).path("stopReason").asText(), is("content_filtered"));
        assertThat(json(LlmContentFilterBodies.blockFor(Provider.BEDROCK, null, bodyShaped).getJsonBody()).path("stopReason").asText(), is("content_filtered"));
    }

    @Test
    public void bedrockInvokeModelContentFilterBlockStaysAnAnthropicRefusal() throws Exception {
        for (HttpRequest invoke : new HttpRequest[]{request().withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke"), null}) {
            LlmContentFilterBodies.Block block = LlmContentFilterBodies.blockFor(Provider.BEDROCK, null, invoke);
            assertThat(block.getStatusCode(), is(200));
            assertThat(json(block.getJsonBody()).path("stop_reason").asText(), is("refusal"));
        }
    }

    @Test
    public void chaosContentFilterOnAConverseRequestReturnsTheConverseBlock() throws Exception {
        HttpResponse response = new HttpLlmResponseActionHandler(new MockServerLogger()).chaosErrorResponseOrNull(
            HttpLlmResponse.llmResponse()
                .withProvider(Provider.BEDROCK)
                .withCompletion(completion().withText("hi"))
                .withChaos(LlmChaosProfile.llmChaosProfile().withContentFilterBlockProbability(1.0)),
            request().withPath("/model/amazon.nova-pro-v1:0/converse"));

        assertThat(response, is(notNullValue()));
        assertThat(response.getStatusCode(), is(200));
        assertThat(json(response.getBodyAsString()).path("stopReason").asText(), is("content_filtered"));
    }

    @Test
    public void bedrockEnforcementErrorIsAnAwsException() throws Exception {
        HttpResponse response = new HttpLlmResponseActionHandler(new MockServerLogger()).enforcementErrorResponseOrNull(
            HttpLlmResponse.llmResponse()
                .withProvider(Provider.BEDROCK)
                .withCompletion(completion().withText("{\"name\":\"Ada\"}")
                    .withOutputSchema("{\"type\":\"object\",\"required\":[\"age\"]}")
                    .enforceOutputSchema()),
            request().withPath("/model/amazon.nova-pro-v1:0/converse"));

        assertThat(response, is(notNullValue()));
        JsonNode body = json(response.getBodyAsString());
        assertThat(body.path("__type").asText(), is("InternalServerException"));
        assertThat(body.has("error"), is(false));
    }
}
