package org.mockserver.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.Usage;

/**
 * Reads the usage of a forwarded Voyage AI embeddings, multimodal or contextualized embeddings, or
 * rerank call: each response names its {@code model} and reports {@code usage.total_tokens}, all of
 * them input tokens.
 * <p>
 * Not a runtime LLM backend: Voyage has no chat API, so it builds no requests.
 */
public class VoyageLlmClient extends AbstractLlmClient {

    @Override
    public Provider provider() {
        return Provider.VOYAGE;
    }

    @Override
    public HttpRequest buildCompletionRequest(LlmBackend backend, ParsedConversation prompt) {
        throw new UnsupportedOperationException("VOYAGE is read for proxied usage only and is not supported as a runtime LLM backend");
    }

    @Override
    public Completion parseCompletionResponse(HttpResponse response) {
        JsonNode root = readBody(response);
        Completion completion = Completion.completion();
        if (root.path("model").isTextual()) {
            completion.withModel(root.path("model").asText());
        }
        Usage usage = parseUsage(root.path("usage"));
        if (usage != null) {
            completion.withUsage(usage);
        }
        return completion;
    }

    @Override
    public Usage parseUsage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.hasNonNull("total_tokens")) {
            return null;
        }
        return Usage.usage().withInputTokens(usageNode.path("total_tokens").asInt());
    }
}
