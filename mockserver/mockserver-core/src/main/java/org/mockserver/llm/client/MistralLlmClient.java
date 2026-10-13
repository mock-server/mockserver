package org.mockserver.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;

/**
 * Runtime client for Mistral AI. OpenAI-chat-compatible, so it inherits request
 * building and response parsing from {@link OpenAiLlmClient}; only the provider and
 * default base URL ({@code https://api.mistral.ai}) differ, and a Conversations API response
 * ({@code conversation.response}), which names its model on each output entry rather than at the top.
 */
public class MistralLlmClient extends OpenAiLlmClient {

    @Override
    public Provider provider() {
        return Provider.MISTRAL;
    }

    @Override
    protected String defaultBaseUrl() {
        return "https://api.mistral.ai";
    }

    @Override
    public Completion parseCompletionResponse(HttpResponse response) {
        Completion completion = super.parseCompletionResponse(response);
        if (completion.getModel() == null) {
            JsonNode outputs = readBody(response).path("outputs");
            for (int i = outputs.size() - 1; i >= 0; i--) {
                if (outputs.path(i).path("model").isTextual()) {
                    return completion.withModel(outputs.path(i).path("model").asText());
                }
            }
        }
        return completion;
    }
}
