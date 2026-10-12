package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.model.Provider;
import org.mockserver.model.StreamingPhysics;
import org.mockserver.model.Usage;

/**
 * Codec for xAI Grok ({@code api.x.ai}). The OpenAI Chat Completions shape, plus the
 * reasoning text in {@code reasoning_content} and xAI's usage object. xAI reports reasoning
 * tokens outside {@code completion_tokens}: the configured output tokens (which include
 * reasoning) are split into {@code completion_tokens} and
 * {@code completion_tokens_details.reasoning_tokens}, and {@code total_tokens} adds both.
 */
public class XaiCodec extends OpenAiCompatibleChatCodec {

    @Override
    public Provider provider() {
        return Provider.XAI;
    }

    @Override
    protected String reasoningField() {
        return "reasoning_content";
    }

    @Override
    protected void addUsageFields(ObjectNode usage, Usage completionUsage, String model, StreamingPhysics physics) {
        int promptTokens = usage.path("prompt_tokens").asInt();
        int outputTokens = usage.path("completion_tokens").asInt();
        int reasoningTokens = usage.path("completion_tokens_details").path("reasoning_tokens").asInt();
        int cachedTokens = usage.path("prompt_tokens_details").path("cached_tokens").asInt();
        int completionTokens = Math.max(0, outputTokens - reasoningTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens + reasoningTokens);
        ObjectNode promptDetails = usage.putObject("prompt_tokens_details");
        promptDetails.put("text_tokens", promptTokens);
        promptDetails.put("audio_tokens", 0);
        promptDetails.put("image_tokens", 0);
        promptDetails.put("cached_tokens", cachedTokens);
        ObjectNode completionDetails = usage.putObject("completion_tokens_details");
        completionDetails.put("reasoning_tokens", reasoningTokens);
        completionDetails.put("audio_tokens", 0);
        completionDetails.put("accepted_prediction_tokens", 0);
        completionDetails.put("rejected_prediction_tokens", 0);
        usage.put("num_sources_used", 0);
    }
}
