package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.model.Delay;
import org.mockserver.model.Provider;
import org.mockserver.model.StreamingPhysics;
import org.mockserver.model.Usage;

/**
 * Codec for Groq ({@code api.groq.com}, {@code /openai/v1/chat/completions}). The OpenAI
 * Chat Completions shape, plus the reasoning text in {@code reasoning}, Groq's timing fields
 * in {@code usage} (seconds, from the expectation's streaming physics when set, else 0), and
 * the {@code x_groq} object: its request {@code id} on the response and on a stream's first
 * chunk, and its {@code usage} on the chunk carrying {@code finish_reason}, which Groq sends
 * without {@code stream_options.include_usage}.
 */
public class GroqCodec extends OpenAiCompatibleChatCodec {

    @Override
    public Provider provider() {
        return Provider.GROQ;
    }

    @Override
    protected String reasoningField() {
        return "reasoning";
    }

    @Override
    protected String newProviderRequestId() {
        return "req_" + randomId(26);
    }

    @Override
    protected void addUsageFields(ObjectNode usage, Usage completionUsage, String model, StreamingPhysics physics) {
        double promptTime = 0;
        double completionTime = 0;
        if (physics != null) {
            Delay timeToFirstToken = physics.getTimeToFirstToken();
            if (timeToFirstToken != null && timeToFirstToken.getTimeUnit() != null) {
                promptTime = timeToFirstToken.getTimeUnit().toMillis(timeToFirstToken.getValue()) / 1000.0;
            }
            Integer tokensPerSecond = physics.getTokensPerSecond();
            if (tokensPerSecond != null && tokensPerSecond > 0) {
                completionTime = usage.path("completion_tokens").asInt() / (double) tokensPerSecond;
            }
        }
        usage.put("queue_time", 0.0);
        usage.put("prompt_time", promptTime);
        usage.put("completion_time", completionTime);
        usage.put("total_time", promptTime + completionTime);
    }

    @Override
    protected void addResponseFields(ObjectNode root, ObjectNode usage, String providerRequestId) {
        root.putObject("x_groq").put("id", providerRequestId);
    }

    @Override
    protected String firstChunkFields(String providerRequestId) {
        return ",\"x_groq\":{\"id\":\"" + providerRequestId + "\"}";
    }

    @Override
    protected String finishChunkFields(String providerRequestId, ObjectNode usage) {
        return ",\"x_groq\":{\"id\":\"" + providerRequestId + "\",\"usage\":" + toJson(usage) + "}";
    }
}
