package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.cost.LlmPricing;
import org.mockserver.model.Provider;
import org.mockserver.model.StreamingPhysics;
import org.mockserver.model.Usage;

/**
 * Codec for OpenRouter ({@code openrouter.ai}). The OpenAI Chat Completions shape
 * ({@code /api/v1/chat/completions}), plus the reasoning text in {@code reasoning} and
 * OpenRouter's usage accounting: {@code cost} (when the model's rate is known),
 * {@code is_byok}, and both token-details objects. OpenRouter always reports usage, so a
 * stream ends with the usage chunk whether or not the request set
 * {@code stream_options.include_usage}.
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
}
