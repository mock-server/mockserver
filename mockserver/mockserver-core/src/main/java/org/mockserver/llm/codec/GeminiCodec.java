package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.JsonEscape;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.llm.ProviderCodec;
import org.mockserver.llm.StreamingPhysicsExpander;
import org.mockserver.llm.TokenCounter;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.SseEvent.sseEvent;

/**
 * Codec for Google Gemini generateContent API (version v1beta-2025).
 * Encodes MockServer Completion objects into Gemini-format HTTP responses
 * for both non-streaming and streaming (SSE) paths.
 * <p>
 * Gemini uses {@code candidates} with {@code content.parts} instead of
 * the OpenAI {@code choices} structure. Streaming sends SSE {@code data:}
 * chunks each containing a partial {@code candidates} array.
 */
public class GeminiCodec implements ProviderCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Provider provider() {
        return Provider.GEMINI;
    }

    @Override
    public String apiVersion() {
        return "v1beta-2025";
    }

    @Override
    public HttpResponse encode(Completion completion, String model) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();

        ArrayNode candidates = root.putArray("candidates");
        ObjectNode candidate = candidates.addObject();

        ObjectNode content = candidate.putObject("content");
        ArrayNode parts = content.putArray("parts");

        // Thought part (before the text part) when reasoning is set. Gemini flags reasoning content
        // with a {"text":"...","thought":true} part. Additive — absent unless reasoningText is set.
        String reasoningText = completion.getReasoningText();
        if (reasoningText != null && !reasoningText.isEmpty()) {
            ObjectNode thoughtPart = parts.addObject();
            thoughtPart.put("text", reasoningText);
            thoughtPart.put("thought", true);
        }

        // Text part
        String text = completion.getText();
        boolean hasText = text != null && !text.isEmpty();
        if (hasText) {
            ObjectNode textPart = parts.addObject();
            textPart.put("text", text);
        }

        // Function call parts
        List<ToolUse> toolCalls = completion.getToolCalls();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        if (hasToolCalls) {
            for (ToolUse toolCall : toolCalls) {
                ObjectNode fcPart = parts.addObject();
                ObjectNode functionCall = fcPart.putObject("functionCall");
                functionCall.put("name", toolCall.getName());
                // args as a JSON object
                String args = toolCall.getArguments();
                if (args != null) {
                    try {
                        JsonNode parsed = OBJECT_MAPPER.readTree(args);
                        if (parsed.isObject()) {
                            functionCall.set("args", parsed);
                        } else {
                            ObjectNode argsObj = functionCall.putObject("args");
                            argsObj.put("value", args);
                        }
                    } catch (Exception e) {
                        ObjectNode argsObj = functionCall.putObject("args");
                        argsObj.put("value", args);
                    }
                } else {
                    functionCall.putObject("args");
                }
            }
        }

        content.put("role", "model");

        // finishReason
        candidate.put("finishReason", mapFinishReason(completion.getStopReason(), hasToolCalls));
        candidate.put("index", 0);

        // usageMetadata
        Usage completionUsage = completion.getUsage();
        ObjectNode usageMetadata = root.putObject("usageMetadata");
        usageMetadata.put("promptTokenCount", promptTokens(completionUsage));
        usageMetadata.put("candidatesTokenCount", candidatesTokens(completionUsage));
        usageMetadata.put("totalTokenCount", totalTokens(completionUsage));
        // Cached-content and thoughts counts are emitted only when set (non-null, non-zero),
        // so existing fixtures stay byte-identical.
        int cachedInputTokens = cachedInputTokens(completionUsage);
        if (cachedInputTokens != 0) {
            usageMetadata.put("cachedContentTokenCount", cachedInputTokens);
        }
        int thoughtsTokens = thoughtsTokens(completionUsage);
        if (thoughtsTokens != 0) {
            usageMetadata.put("thoughtsTokenCount", thoughtsTokens);
        }

        root.put("modelVersion", model != null ? model : "unknown");

        try {
            String json = OBJECT_MAPPER.writeValueAsString(root);
            return response()
                .withStatusCode(200)
                .withHeader("content-type", "application/json")
                .withBody(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode Gemini response", e);
        }
    }

    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics) {
        List<SseEvent> events = new ArrayList<>();
        String modelName = model != null ? model : "unknown";

        Usage completionUsage = completion.getUsage();

        String text = completion.getText();
        List<ToolUse> toolCalls = completion.getToolCalls();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();

        // Thought chunk (before text) when reasoning is set — a single part flagged thought:true.
        // Additive — absent unless reasoningText is set.
        String reasoningText = completion.getReasoningText();
        if (reasoningText != null && !reasoningText.isEmpty()) {
            String thoughtData = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escapeJson(reasoningText) +
                "\",\"thought\":true}],\"role\":\"model\"},\"index\":0}],\"modelVersion\":\"" + escapeJson(modelName) + "\"}";
            events.add(sseEvent().withData(thoughtData));
        }

        // Text delta chunks
        if (text != null && !text.isEmpty()) {
            List<String> tokens = TokenCounter.streamingTextTokens(text, physics);
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    String chunkData = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escapeJson(token) +
                        "\"}],\"role\":\"model\"},\"index\":0}],\"modelVersion\":\"" + escapeJson(modelName) + "\"}";
                    events.add(sseEvent().withData(chunkData));
                }
            }
        }

        // Tool call chunks
        if (hasToolCalls) {
            for (ToolUse toolCall : toolCalls) {
                String args = toolCall.getArguments() != null ? toolCall.getArguments() : "{}";
                // Validate + re-serialise args so a non-JSON or syntactically broken
                // argument string cannot corrupt the streamed JSON chunk. Mirrors the
                // non-streaming encode() path.
                String safeArgs;
                try {
                    JsonNode parsedArgs = OBJECT_MAPPER.readTree(args);
                    if (parsedArgs != null && parsedArgs.isObject()) {
                        safeArgs = OBJECT_MAPPER.writeValueAsString(parsedArgs);
                    } else {
                        safeArgs = "{\"value\":\"" + escapeJson(args) + "\"}";
                    }
                } catch (Exception e) {
                    safeArgs = "{\"value\":\"" + escapeJson(args) + "\"}";
                }
                String chunkData = "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"" +
                    escapeJson(toolCall.getName()) + "\",\"args\":" + safeArgs +
                    "}}],\"role\":\"model\"},\"index\":0}],\"modelVersion\":\"" + escapeJson(modelName) + "\"}";
                events.add(sseEvent().withData(chunkData));
            }
        }

        // Final chunk with finishReason and usage — mirror cached/thoughts token counts into the
        // final usageMetadata when set (non-null, non-zero), matching the non-streaming encode().
        // Omitted when unset so existing streaming fixtures stay byte-identical.
        String finishReason = mapFinishReason(completion.getStopReason(), hasToolCalls);
        StringBuilder finalUsage = new StringBuilder();
        finalUsage.append("{\"promptTokenCount\":").append(promptTokens(completionUsage))
            .append(",\"candidatesTokenCount\":").append(candidatesTokens(completionUsage))
            .append(",\"totalTokenCount\":").append(totalTokens(completionUsage));
        int cachedInputTokens = cachedInputTokens(completionUsage);
        if (cachedInputTokens != 0) {
            finalUsage.append(",\"cachedContentTokenCount\":").append(cachedInputTokens);
        }
        int thoughtsTokens = thoughtsTokens(completionUsage);
        if (thoughtsTokens != 0) {
            finalUsage.append(",\"thoughtsTokenCount\":").append(thoughtsTokens);
        }
        finalUsage.append("}");
        String finalData = "{\"candidates\":[{\"content\":{\"parts\":[],\"role\":\"model\"},\"finishReason\":\"" +
            escapeJson(finishReason) + "\",\"index\":0}],\"usageMetadata\":" + finalUsage +
            ",\"modelVersion\":\"" + escapeJson(modelName) + "\"}";
        events.add(sseEvent().withData(finalData));

        return StreamingPhysicsExpander.applyPhysics(events, physics);
    }

    @Override
    public ParsedConversation decode(HttpRequest request) {
        try {
            String body = request != null ? request.getBodyAsText() : null;
            if (body == null || body.isEmpty()) {
                return ParsedConversation.empty();
            }
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (root == null || !root.isObject()) {
                return ParsedConversation.empty();
            }
            JsonNode contentsNode = root.get("contents");
            if (contentsNode == null || !contentsNode.isArray()) {
                return ParsedConversation.empty();
            }

            List<ParsedMessage> parsed = new ArrayList<>();
            for (JsonNode contentNode : contentsNode) {
                String rawRole = contentNode.has("role") ? contentNode.get("role").asText("") : "user";
                JsonNode partsNode = contentNode.get("parts");

                String textContent = "";
                List<ToolUse> toolCalls = new ArrayList<>();
                Map<String, String> toolResults = new LinkedHashMap<>();
                List<ParsedMessage.ImagePart> images = new ArrayList<>();
                boolean hasToolResult = false;

                if (partsNode != null && partsNode.isArray()) {
                    StringBuilder textBuilder = new StringBuilder();
                    int partIndex = 0;
                    for (JsonNode part : partsNode) {
                        if (part.has("text")) {
                            textBuilder.append(part.get("text").asText(""));
                        } else if (part.has("inline_data") || part.has("inlineData")) {
                            // Gemini image part: {"inline_data":{"mime_type":"image/png","data":"..."}}
                            // (REST snake_case or SDK camelCase). Recognise images only.
                            JsonNode inline = part.has("inline_data") ? part.get("inline_data") : part.get("inlineData");
                            String mimeType = inline.has("mime_type") ? inline.path("mime_type").asText(null)
                                : (inline.has("mimeType") ? inline.path("mimeType").asText(null) : null);
                            if (mimeType == null || mimeType.startsWith("image/")) {
                                images.add(new ParsedMessage.ImagePart(mimeType));
                            }
                        } else if (part.has("functionCall")) {
                            JsonNode fc = part.get("functionCall");
                            String name = fc.has("name") ? fc.get("name").asText("") : "";
                            String argsStr = "";
                            if (fc.has("args")) {
                                JsonNode argsNode = fc.get("args");
                                if (argsNode.isTextual()) {
                                    argsStr = argsNode.asText("");
                                } else {
                                    argsStr = argsNode.toString();
                                }
                            }
                            // Gemini doesn't have tool call IDs; synthesize from name + index
                            String syntheticId = name + "_" + partIndex;
                            ToolUse tu = ToolUse.toolUse(name).withArguments(argsStr).withId(syntheticId);
                            toolCalls.add(tu);
                        } else if (part.has("functionResponse")) {
                            hasToolResult = true;
                            JsonNode fr = part.get("functionResponse");
                            String name = fr.has("name") ? fr.get("name").asText("") : "";
                            String resultStr = "";
                            if (fr.has("response")) {
                                JsonNode responseNode = fr.get("response");
                                if (responseNode.isTextual()) {
                                    resultStr = responseNode.asText("");
                                } else {
                                    resultStr = responseNode.toString();
                                }
                            }
                            toolResults.put(name, resultStr);
                        }
                        partIndex++;
                    }
                    textContent = textBuilder.toString();
                }

                ParsedMessage.Role role;
                if (hasToolResult) {
                    role = ParsedMessage.Role.TOOL;
                } else {
                    role = mapGeminiRole(rawRole);
                }

                parsed.add(new ParsedMessage(
                    role,
                    textContent,
                    toolCalls.isEmpty() ? null : toolCalls,
                    toolResults.isEmpty() ? null : toolResults,
                    images.isEmpty() ? null : images
                ));
            }

            return ParsedConversation.of(parsed);
        } catch (Exception e) {
            return ParsedConversation.empty();
        }
    }

    private static final int DEFAULT_EMBEDDING_DIMENSIONS = 768;

    /**
     * Encodes a Gemini {@code embedContent} response ({@code {"embedding":{"values":[...]}}})
     * for a single input. The default dimensionality ({@code text-embedding-004}) is 768.
     */
    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        EmbeddingWire.addFloats(root.putObject("embedding").putArray("values"), EmbeddingWire.vector(embedding, input, null, DEFAULT_EMBEDDING_DIMENSIONS));
        root.putObject("usageMetadata").put("promptTokenCount", EmbeddingVectors.approximateTokens(input));
        return EmbeddingWire.json(root, "Gemini");
    }

    /**
     * Encodes the response for the request's API:
     * <ul>
     *   <li>{@code models/{model}:embedContent} embeds {@code content.parts[].text} as one
     *       vector: {@code {"embedding":{"values":[...]}}}.</li>
     *   <li>{@code models/{model}:batchEmbedContents} (or any body with a {@code requests}
     *       array) embeds each {@code requests[i].content} in order:
     *       {@code {"embeddings":[{"values":[...]}, ...]}}.</li>
     * </ul>
     * Both carry {@code usageMetadata.promptTokenCount}. Each request's
     * {@code outputDimensionality} (top level or in {@code embedContentConfig}) is honoured
     * unless the expectation sets dimensions.
     */
    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, HttpRequest request, String model) {
        JsonNode body = EmbeddingWire.body(request);
        JsonNode requests = body.get("requests");
        boolean batch = EmbeddingWire.path(request).endsWith(":batchembedcontents") || (requests != null && requests.isArray());
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        List<String> inputs = new ArrayList<>();
        if (batch) {
            ArrayNode embeddings = root.putArray("embeddings");
            if (requests != null && requests.isArray()) {
                EmbeddingWire.checkInputCount(requests.size(), EmbeddingWire.MAX_INPUTS, "requests");
                long totalValues = 0;
                for (JsonNode embedRequest : requests) {
                    totalValues += EmbeddingWire.dimensions(embedding, outputDimensionality(embedRequest), DEFAULT_EMBEDDING_DIMENSIONS);
                }
                EmbeddingWire.checkTotalValues(totalValues, EmbeddingWire.maxTotalValues());
                for (JsonNode embedRequest : requests) {
                    String text = EmbeddingWire.geminiContentText(embedRequest.get("content"));
                    inputs.add(text);
                    EmbeddingWire.addFloats(embeddings.addObject().putArray("values"),
                        EmbeddingWire.vector(embedding, text, outputDimensionality(embedRequest), DEFAULT_EMBEDDING_DIMENSIONS));
                }
            }
        } else {
            String text = EmbeddingWire.geminiContentText(body.get("content"));
            inputs.add(text);
            EmbeddingWire.addFloats(root.putObject("embedding").putArray("values"),
                EmbeddingWire.vector(embedding, text, outputDimensionality(body), DEFAULT_EMBEDDING_DIMENSIONS));
        }
        root.putObject("usageMetadata").put("promptTokenCount", EmbeddingWire.tokens(inputs));
        return EmbeddingWire.json(root, "Gemini");
    }

    private static Integer outputDimensionality(JsonNode embedRequest) {
        Integer topLevel = EmbeddingWire.requestedDimensions(embedRequest.get("outputDimensionality"), "outputDimensionality");
        if (topLevel != null) {
            return topLevel;
        }
        JsonNode config = embedRequest.get("embedContentConfig");
        return config != null ? EmbeddingWire.requestedDimensions(config.get("outputDimensionality"), "embedContentConfig.outputDimensionality") : null;
    }

    private static ParsedMessage.Role mapGeminiRole(String rawRole) {
        if (rawRole == null) {
            return ParsedMessage.Role.USER;
        }
        switch (rawRole.toLowerCase()) {
            case "model":
                return ParsedMessage.Role.ASSISTANT;
            case "user":
                return ParsedMessage.Role.USER;
            default:
                return ParsedMessage.Role.USER;
        }
    }

    private static int promptTokens(Usage usage) {
        return usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0;
    }

    private static int cachedInputTokens(Usage usage) {
        return usage != null && usage.getCachedInputTokens() != null ? usage.getCachedInputTokens() : 0;
    }

    private static int thoughtsTokens(Usage usage) {
        return usage != null && usage.getReasoningTokens() != null ? usage.getReasoningTokens() : 0;
    }

    /**
     * {@code Usage.outputTokens} includes reasoning tokens, but Gemini's {@code candidatesTokenCount}
     * excludes them: thoughts are reported separately in {@code thoughtsTokenCount} and
     * {@code totalTokenCount} is prompt + candidates + thoughts.
     */
    private static int candidatesTokens(Usage usage) {
        int outputTokens = usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
        return Math.max(0, outputTokens - thoughtsTokens(usage));
    }

    private static int totalTokens(Usage usage) {
        return promptTokens(usage) + candidatesTokens(usage) + thoughtsTokens(usage);
    }

    private static String mapFinishReason(String stopReason, boolean hasToolCalls) {
        if (stopReason == null) {
            // Gemini has no dedicated tool-call finish reason; tool use is signalled
            // by the presence of functionCall parts, so STOP is the right default
            // for both text-only and tool-call completions when no explicit stop reason was set.
            return "STOP";
        }
        switch (stopReason) {
            case "end_turn":
            case "stop":
                return "STOP";
            case "max_tokens":
            case "length":
                return "MAX_TOKENS";
            case "tool_use":
            case "tool_calls":
                // Gemini doesn't have a separate tool-call finish reason
                return "STOP";
            case "STOP":
            case "MAX_TOKENS":
            case "SAFETY":
            case "RECITATION":
            case "OTHER":
                return stopReason;
            default:
                return stopReason;
        }
    }


    private static String escapeJson(String value) {
        return JsonEscape.escape(value);
    }
}
