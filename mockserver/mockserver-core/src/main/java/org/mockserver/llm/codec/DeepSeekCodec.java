package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.model.Provider;
import org.mockserver.model.StreamingPhysics;
import org.mockserver.model.Usage;

/**
 * Codec for DeepSeek ({@code api.deepseek.com}). The OpenAI Chat Completions shape, plus
 * the reasoning text in {@code reasoning_content} and the prompt-cache split in
 * {@code usage.prompt_cache_hit_tokens} and {@code usage.prompt_cache_miss_tokens}.
 */
public class DeepSeekCodec extends OpenAiCompatibleChatCodec {

    @Override
    public Provider provider() {
        return Provider.DEEPSEEK;
    }

    @Override
    protected String reasoningField() {
        return "reasoning_content";
    }

    @Override
    protected void addUsageFields(ObjectNode usage, Usage completionUsage, String model, StreamingPhysics physics) {
        int promptTokens = usage.path("prompt_tokens").asInt();
        int cacheHit = Math.min(promptTokens, usage.path("prompt_tokens_details").path("cached_tokens").asInt());
        usage.put("prompt_cache_hit_tokens", cacheHit);
        usage.put("prompt_cache_miss_tokens", promptTokens - cacheHit);
    }
}
