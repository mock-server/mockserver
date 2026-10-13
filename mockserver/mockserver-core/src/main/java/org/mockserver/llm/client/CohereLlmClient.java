package org.mockserver.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.Usage;

/**
 * Reads the usage of a forwarded Cohere call: chat ({@code /v1/chat}, {@code /v2/chat}), embed and
 * rerank. Cohere reports it in an {@code ApiMeta} object, {@code meta} on embed, rerank and v1 chat
 * and {@code usage} on v2 chat and on the v2 stream's {@code message-end} event, holding
 * {@code billed_units} (what Cohere bills), {@code tokens} (what the model used) and
 * {@code cached_tokens}. A rerank is billed in search units, not tokens, so it carries no token counts.
 * <p>
 * Not a runtime LLM backend: it builds no requests.
 */
public class CohereLlmClient extends AbstractLlmClient {

    @Override
    public Provider provider() {
        return Provider.COHERE;
    }

    @Override
    public HttpRequest buildCompletionRequest(LlmBackend backend, ParsedConversation prompt) {
        throw new UnsupportedOperationException("COHERE is read for proxied usage only and is not supported as a runtime LLM backend");
    }

    @Override
    public Completion parseCompletionResponse(HttpResponse response) {
        JsonNode root = readBody(response);
        Completion completion = Completion.completion();
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("message").path("content")) {
            if ("text".equals(block.path("type").asText()) && block.path("text").isTextual()) {
                text.append(block.path("text").asText());
            }
        }
        if (text.length() == 0 && root.path("text").isTextual()) {
            text.append(root.path("text").asText());
        }
        if (text.length() > 0) {
            completion.withText(text.toString());
        }
        if (root.path("finish_reason").isTextual()) {
            completion.withStopReason(root.path("finish_reason").asText());
        }
        Usage usage = parseUsage(root.path("usage"));
        if (usage == null) {
            usage = parseUsage(root.path("meta"));
        }
        if (usage != null) {
            completion.withUsage(usage);
        }
        return completion;
    }

    /**
     * Maps an {@code ApiMeta} or v2 {@code Usage} object, preferring the billed count of each side
     * (which the cost estimate needs) and falling back to the count the model used.
     *
     * @return the usage, or null when neither side has a token count
     */
    @Override
    public Usage parseUsage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return null;
        }
        JsonNode billed = usageNode.path("billed_units");
        JsonNode used = usageNode.path("tokens");
        JsonNode input = count(billed, used, "input_tokens");
        JsonNode output = count(billed, used, "output_tokens");
        if (input == null && output == null) {
            return null;
        }
        Usage usage = Usage.usage();
        if (input != null) {
            usage.withInputTokens(input.asInt());
        }
        if (output != null) {
            usage.withOutputTokens(output.asInt());
        }
        if (usageNode.hasNonNull("cached_tokens")) {
            usage.withCachedInputTokens(usageNode.path("cached_tokens").asInt());
        }
        return usage;
    }

    private static JsonNode count(JsonNode billed, JsonNode used, String field) {
        if (billed.hasNonNull(field)) {
            return billed.path(field);
        }
        return used.hasNonNull(field) ? used.path(field) : null;
    }
}
