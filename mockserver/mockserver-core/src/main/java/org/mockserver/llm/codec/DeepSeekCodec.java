package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.model.Provider;
import org.mockserver.model.StreamingPhysics;
import org.mockserver.model.Usage;

/**
 * Codec for DeepSeek ({@code api.deepseek.com}). The OpenAI Chat Completions shape, plus
 * the reasoning text in {@code reasoning_content}, the prompt-cache split in
 * {@code usage.prompt_cache_hit_tokens} and {@code usage.prompt_cache_miss_tokens}, and what
 * DeepSeek's API reference requires: {@code logprobs} (null) on each choice, a
 * {@code system_fingerprint} on the response and on every chunk, and
 * {@code usage.prompt_tokens_details.cached_tokens} (equal to the cache hits) in every usage.
 * A stream always carries {@code usage} on its {@code finish_reason} chunk, with no separate
 * usage chunk; {@code stream_options.include_usage} only adds {@code "usage": null} to the others.
 */
public class DeepSeekCodec extends OpenAiCompatibleChatCodec {

    @Override
    public Provider provider() {
        return Provider.DEEPSEEK;
    }

    @Override
    protected boolean emitsChoiceLogprobs() {
        return true;
    }

    @Override
    protected String reasoningField() {
        return "reasoning_content";
    }

    @Override
    protected boolean streamsUsageOnFinishChunk() {
        return true;
    }

    @Override
    protected String newSystemFingerprint() {
        return "fp_" + randomId(10);
    }

    @Override
    protected void addUsageFields(ObjectNode usage, Usage completionUsage, String model, StreamingPhysics physics) {
        int promptTokens = usage.path("prompt_tokens").asInt();
        int cacheHit = Math.min(promptTokens, usage.path("prompt_tokens_details").path("cached_tokens").asInt());
        usage.putObject("prompt_tokens_details").put("cached_tokens", cacheHit);
        usage.put("prompt_cache_hit_tokens", cacheHit);
        usage.put("prompt_cache_miss_tokens", promptTokens - cacheHit);
    }
}
