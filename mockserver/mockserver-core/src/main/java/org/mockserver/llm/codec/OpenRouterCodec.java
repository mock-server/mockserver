package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.JsonEscape;
import org.mockserver.llm.cost.LlmPricing;
import org.mockserver.model.Completion;
import org.mockserver.model.Provider;
import org.mockserver.model.StreamingPhysics;
import org.mockserver.model.Usage;

/**
 * Codec for OpenRouter ({@code openrouter.ai}). The OpenAI Chat Completions shape
 * ({@code /api/v1/chat/completions}), plus the reasoning text in {@code reasoning} and
 * OpenRouter's usage accounting: {@code cost} (when the model's rate is known),
 * {@code is_byok}, and both token-details objects, and the {@code logprobs} (null) on each
 * non-streaming choice. OpenRouter always reports usage, so a
 * stream ends with the usage chunk whether or not the request set
 * {@code stream_options.include_usage}. That chunk carries one content-free choice repeating the
 * stream's {@code finish_reason}, as OpenRouter's does.
 * <p>
 * Each choice carries {@code native_finish_reason}, the upstream model's own value beside
 * OpenRouter's normalised {@code finish_reason} ({@code stop}, {@code length},
 * {@code tool_calls}, {@code content_filter} or {@code error}). It is the configured
 * {@code stopReason} when that normalises to the {@code finish_reason} sent (so {@code STOP}
 * gives {@code "stop"} and {@code "STOP"}), else the {@code finish_reason} itself; it is null on
 * every streamed chunk before the last.
 */
public class OpenRouterCodec extends OpenAiCompatibleChatCodec {

    @Override
    public Provider provider() {
        return Provider.OPENROUTER;
    }

    @Override
    protected boolean emitsChoiceLogprobs() {
        return true;
    }

    @Override
    protected String reasoningField() {
        return "reasoning";
    }

    @Override
    protected boolean streamsUsageChunk(boolean includeUsageRequested) {
        return true;
    }

    @Override
    protected void addUsageFields(ObjectNode usage, Usage completionUsage, String model, StreamingPhysics physics) {
        int promptTokens = usage.path("prompt_tokens").asInt();
        int completionTokens = usage.path("completion_tokens").asInt();
        Double cost = LlmPricing.estimateCostUsd(Provider.OPENROUTER, model, promptTokens, completionTokens);
        if (cost != null) {
            usage.put("cost", cost);
        }
        usage.put("is_byok", false);
        int cachedTokens = usage.path("prompt_tokens_details").path("cached_tokens").asInt();
        int reasoningTokens = usage.path("completion_tokens_details").path("reasoning_tokens").asInt();
        usage.putObject("prompt_tokens_details").put("cached_tokens", cachedTokens);
        usage.putObject("completion_tokens_details").put("reasoning_tokens", reasoningTokens);
    }

    @Override
    protected String finishReason(Completion completion, boolean hasToolCalls) {
        return normalise(super.finishReason(completion, hasToolCalls));
    }

    @Override
    protected void addChoiceFields(ObjectNode choice, Completion completion, String finishReason) {
        choice.put("native_finish_reason", nativeFinishReason(completion, finishReason));
    }

    @Override
    protected String streamChoiceFields(Completion completion, String finishReason) {
        if (finishReason == null) {
            return ",\"native_finish_reason\":null";
        }
        return ",\"native_finish_reason\":\"" + JsonEscape.escape(nativeFinishReason(completion, finishReason)) + "\"";
    }

    @Override
    protected String usageChunkChoices(Completion completion, String finishReason) {
        return "[{\"index\":0,\"delta\":{\"content\":\"\",\"role\":\"assistant\"},\"finish_reason\":\""
            + JsonEscape.escape(finishReason) + "\"" + streamChoiceFields(completion, finishReason) + "}]";
    }

    private static String nativeFinishReason(Completion completion, String finishReason) {
        String configured = completion.getStopReason();
        return configured != null && !configured.isEmpty() && normalise(configured).equals(finishReason) ? configured : finishReason;
    }

    /** Maps the other providers' stop reasons onto OpenRouter's values; any other value passes through. */
    private static String normalise(String reason) {
        if (reason == null) {
            return null;
        }
        switch (reason) {
            case "end_turn":
            case "stop_sequence":
            case "STOP":
            case "completed":
                return "stop";
            case "max_tokens":
            case "MAX_TOKENS":
            case "model_context_window_exceeded":
                return "length";
            case "tool_use":
            case "function_call":
                return "tool_calls";
            case "refusal":
            case "content_filtered":
            case "guardrail_intervened":
            case "SAFETY":
            case "RECITATION":
            case "BLOCKLIST":
            case "PROHIBITED_CONTENT":
            case "SPII":
                return "content_filter";
            default:
                return reason;
        }
    }
}
