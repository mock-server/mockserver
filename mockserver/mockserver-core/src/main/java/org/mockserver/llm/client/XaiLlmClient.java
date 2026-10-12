package org.mockserver.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.mockserver.model.Provider;
import org.mockserver.model.Usage;

/**
 * Runtime client for xAI Grok. OpenAI-chat-compatible, so it inherits request
 * building and response parsing from {@link OpenAiLlmClient}; the provider, the
 * default base URL ({@code https://api.x.ai}) and the usage mapping differ.
 */
public class XaiLlmClient extends OpenAiLlmClient {

    @Override
    public Provider provider() {
        return Provider.XAI;
    }

    @Override
    protected String defaultBaseUrl() {
        return "https://api.x.ai";
    }

    /**
     * xAI's {@code completion_tokens} leaves out the reasoning tokens it bills as output, so
     * they are added back to give output tokens that include reasoning, as for OpenAI.
     */
    @Override
    public Usage parseUsage(JsonNode usageNode) {
        Usage usage = super.parseUsage(usageNode);
        if (usage != null && usage.getReasoningTokens() != null) {
            usage.withOutputTokens((usage.getOutputTokens() != null ? usage.getOutputTokens() : 0) + usage.getReasoningTokens());
        }
        return usage;
    }
}
