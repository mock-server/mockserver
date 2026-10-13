package org.mockserver.mock.action.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Usage;

/**
 * Reads the model and input-token count back out of a served embeddings or rerank response body,
 * so the call is counted with exactly the usage the provider codec put on the wire. The fields are
 * each provider's own: {@code usage.prompt_tokens} (OpenAI and its compatibles), {@code usage.total_tokens}
 * (Voyage rerank), {@code usageMetadata.promptTokenCount} (Gemini), {@code prompt_eval_count}
 * (Ollama) and {@code inputTextTokenCount} (Bedrock Titan). A streaming parse skips the vectors,
 * which can hold millions of values, without building a tree.
 */
final class EncodedLlmCallUsage {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private EncodedLlmCallUsage() {
    }

    /**
     * @return a completion carrying the response's top-level {@code model} (or null) and a usage
     * with only input tokens (or no usage when the body reports none); null when the body is not
     * a JSON object
     */
    static Completion read(HttpResponse encoded) {
        String body = encoded != null ? encoded.getBodyAsString() : null;
        if (body == null || body.isEmpty()) {
            return null;
        }
        try (JsonParser parser = JSON_FACTORY.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            String model = null;
            Long inputTokens = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (field) {
                    case "model":
                        if (value == JsonToken.VALUE_STRING && !parser.getText().isEmpty()) {
                            model = parser.getText();
                        }
                        break;
                    case "prompt_eval_count":
                    case "inputTextTokenCount":
                        if (value == JsonToken.VALUE_NUMBER_INT) {
                            inputTokens = parser.getLongValue();
                        }
                        break;
                    case "usage":
                        if (value == JsonToken.START_OBJECT) {
                            inputTokens = firstCount(parser, "prompt_tokens", "total_tokens");
                        }
                        break;
                    case "usageMetadata":
                        if (value == JsonToken.START_OBJECT) {
                            inputTokens = firstCount(parser, "promptTokenCount", null);
                        }
                        break;
                    default:
                        break;
                }
                parser.skipChildren();
            }
            Completion completion = Completion.completion();
            if (model != null) {
                completion.withModel(model);
            }
            if (inputTokens != null) {
                completion.withUsage(Usage.usage().withInputTokens((int) Math.min(Integer.MAX_VALUE, Math.max(0L, inputTokens))));
            }
            return completion;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads the object the parser has just entered to its end, returning the integer {@code preferred}
     * field, else the integer {@code fallback} field, else null.
     */
    private static Long firstCount(JsonParser parser, String preferred, String fallback) throws java.io.IOException {
        Long preferredValue = null;
        Long fallbackValue = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            if (value == JsonToken.VALUE_NUMBER_INT) {
                if (field.equals(preferred)) {
                    preferredValue = parser.getLongValue();
                } else if (field.equals(fallback)) {
                    fallbackValue = parser.getLongValue();
                }
            }
            parser.skipChildren();
        }
        return preferredValue != null ? preferredValue : fallbackValue;
    }
}
