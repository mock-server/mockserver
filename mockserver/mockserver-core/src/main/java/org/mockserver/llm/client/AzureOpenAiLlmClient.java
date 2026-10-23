package org.mockserver.llm.client;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Provider;

/**
 * Runtime client for Azure OpenAI. Wire-compatible with OpenAI Chat Completions
 * for request body and response parsing, but uses the per-deployment URL shape
 * ({@code {baseUrl}/openai/deployments/{model}/chat/completions?api-version=…})
 * and the {@code api-key} header instead of {@code Authorization: Bearer}.
 * Response parsing is inherited from {@link OpenAiLlmClient}.
 */
public class AzureOpenAiLlmClient extends OpenAiLlmClient {

    static final String DEFAULT_API_VERSION = "2024-02-15-preview";

    @Override
    public Provider provider() {
        return Provider.AZURE_OPENAI;
    }

    @Override
    public HttpRequest buildCompletionRequest(LlmBackend backend, ParsedConversation prompt) {
        // baseUrl is required for Azure (per-resource, e.g. https://{resource}.openai.azure.com).
        // There is deliberately no default host: anyone owning a default resource name would be
        // sent the api-key. Callers treat the exception fail-closed.
        if (backend.baseUrl() == null || backend.baseUrl().isEmpty()) {
            throw new IllegalStateException("an Azure OpenAI backend needs a baseUrl (https://{resource}.openai.azure.com); none is configured, so nothing was sent");
        }
        String baseUrl = backend.baseUrl();
        String deployment = resolveModel(backend, "gpt-4o-mini");
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("temperature", 0);
        body.put("seed", 0);
        ArrayNode messages = body.putArray("messages");
        appendRoleContentMessages(messages, prompt, "system", "user", "assistant", "tool");

        HttpRequest request = postJson(backend, baseUrl,
            "/openai/deployments/" + deployment + "/chat/completions", writeJson(body));
        request.withQueryStringParameter("api-version", DEFAULT_API_VERSION);
        if (backend.hasApiKey()) {
            request.withHeader("api-key", backend.apiKey());
        }
        return request;
    }
}
