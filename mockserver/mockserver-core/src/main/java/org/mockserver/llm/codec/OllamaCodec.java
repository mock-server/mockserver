package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.JsonEscape;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.llm.ProviderCodec;
import org.mockserver.llm.StreamingFormat;
import org.mockserver.llm.StreamingPhysicsExpander;
import org.mockserver.llm.TokenCounter;
import org.mockserver.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.SseEvent.sseEvent;

/**
 * Codec for Ollama {@code /api/chat} and {@code /api/generate} endpoints (version ollama-2025).
 * <p>
 * Ollama uses a distinct JSON shape with a top-level {@code message} object
 * (not an array of choices). Token counts are exposed as {@code prompt_eval_count}
 * and {@code eval_count}. A request to {@code /api/generate} gets that endpoint's
 * shape instead: the text in a top-level {@code response} string (see
 * {@link #encode(Completion, String, HttpRequest)}).
 * <p>
 * <strong>Streaming format:</strong> Ollama's native wire format is NDJSON
 * (newline-delimited JSON, {@code application/x-ndjson}), not SSE. Each
 * streaming chunk is a single JSON object followed by a newline character
 * ({@code <json>\n}). This codec internally represents chunks as
 * {@link SseEvent} objects (reusing the existing data/delay model), but
 * declares {@link StreamingFormat#NDJSON} via {@link #streamingFormat()} so
 * that the downstream write handler emits raw NDJSON framing instead of SSE
 * {@code data:} prefixes.
 * <p>
 * Tool calls use Ollama 0.3+ format where {@code arguments} is a JSON object
 * (not a JSON-as-string like OpenAI).
 */
public class OllamaCodec implements ProviderCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Provider provider() {
        return Provider.OLLAMA;
    }

    @Override
    public String apiVersion() {
        return "ollama-2025";
    }

    @Override
    public StreamingFormat streamingFormat() {
        return StreamingFormat.NDJSON;
    }

    @Override
    public HttpResponse encode(Completion completion, String model) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", model != null ? model : "unknown");
        root.put("created_at", Instant.now().toString());

        ObjectNode message = root.putObject("message");
        message.put("role", "assistant");

        String text = completion.getText();
        message.put("content", text != null ? text : "");

        // Reasoning ("thinking") content. Ollama 0.5+ surfaces a reasoning-capable model's chain of
        // thought as message.thinking (a sibling string to content). Emitted only when reasoningText
        // is set so existing fixtures stay byte-identical. Ollama has NO native cached/reasoning
        // *token-count* field, so the Usage.cachedInputTokens / reasoningTokens counts are not
        // encoded here — only the reasoning text is representable on the wire.
        String reasoningText = completion.getReasoningText();
        if (reasoningText != null && !reasoningText.isEmpty()) {
            message.put("thinking", reasoningText);
        }

        // Tool calls
        List<ToolUse> toolCalls = completion.getToolCalls();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        if (hasToolCalls) {
            writeToolCalls(message, toolCalls);
        }

        root.put("done", true);
        root.put("done_reason", mapDoneReason(completion.getStopReason()));
        putMetrics(root, completion);
        return jsonResponse(root);
    }

    /**
     * Chooses the wire API by path: {@code /api/generate} (with any prefix, any case) gets the
     * generate shape, everything else the {@code /api/chat} shape.
     */
    @Override
    public HttpResponse encode(Completion completion, String model, HttpRequest request) {
        return isGenerate(request) ? encodeGenerate(completion, model, request) : encode(completion, model);
    }

    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics, HttpRequest request) {
        return isGenerate(request) ? encodeGenerateStreaming(completion, model, physics, request) : encodeStreaming(completion, model, physics);
    }

    private static boolean isGenerate(HttpRequest request) {
        return EmbeddingWire.path(request).endsWith("/api/generate");
    }

    /**
     * {@code POST /api/generate} reply, as {@code GenerateResponse} in Ollama's {@code api/types.go}:
     * {@code response} holds the text (top level, no {@code message}), {@code thinking} the
     * reasoning text, and the final object adds {@code done_reason}, {@code context}, the metrics
     * and any {@code tool_calls}. {@code context} is an opaque token encoding that clients only
     * send back, so a fixed placeholder is returned; Ollama omits it for a {@code raw} prompt.
     */
    private HttpResponse encodeGenerate(Completion completion, String model, HttpRequest request) {
        ObjectNode root = generateObject(model, completion.getText());
        String reasoningText = completion.getReasoningText();
        if (reasoningText != null && !reasoningText.isEmpty()) {
            root.put("thinking", reasoningText);
        }
        putGenerateFinalFields(root, completion, request);
        return jsonResponse(root);
    }

    /**
     * Streamed {@code /api/generate}: a {@code thinking} piece when reasoning is set, one
     * {@code response} piece per token, then a final object with an empty {@code response}.
     * Ollama emits generate tool calls only on that final object ({@code server/routes.go}).
     */
    private List<SseEvent> encodeGenerateStreaming(Completion completion, String model, StreamingPhysics physics, HttpRequest request) {
        List<SseEvent> events = new ArrayList<>();
        String reasoningText = completion.getReasoningText();
        if (reasoningText != null && !reasoningText.isEmpty()) {
            ObjectNode thinkingChunk = generateObject(model, "");
            thinkingChunk.put("thinking", reasoningText);
            thinkingChunk.put("done", false);
            events.add(sseEvent().withData(toJson(thinkingChunk)));
        }
        String text = completion.getText();
        if (text != null && !text.isEmpty()) {
            for (String token : TokenCounter.streamingTextTokens(text, physics)) {
                if (!token.isEmpty()) {
                    ObjectNode chunk = generateObject(model, token);
                    chunk.put("done", false);
                    events.add(sseEvent().withData(toJson(chunk)));
                }
            }
        }
        ObjectNode finalChunk = generateObject(model, "");
        putGenerateFinalFields(finalChunk, completion, request);
        events.add(sseEvent().withData(toJson(finalChunk)));
        return StreamingPhysicsExpander.applyPhysics(events, physics);
    }

    private static ObjectNode generateObject(String model, String response) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("model", model != null ? model : "unknown");
        node.put("created_at", Instant.now().toString());
        node.put("response", response != null ? response : "");
        return node;
    }

    private static void putGenerateFinalFields(ObjectNode node, Completion completion, HttpRequest request) {
        node.put("done", true);
        node.put("done_reason", mapDoneReason(completion.getStopReason()));
        if (!EmbeddingWire.body(request).path("raw").asBoolean(false)) {
            node.putArray("context").add(1).add(2).add(3);
        }
        putMetrics(node, completion);
        List<ToolUse> toolCalls = completion.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            writeToolCalls(node, toolCalls);
        }
    }

    /** The durations and token counts Ollama puts on a final ({@code done: true}) object. */
    private static void putMetrics(ObjectNode node, Completion completion) {
        Usage completionUsage = completion.getUsage();
        int promptEvalCount = completionUsage != null && completionUsage.getInputTokens() != null ? completionUsage.getInputTokens() : 0;
        int evalCount = completionUsage != null && completionUsage.getOutputTokens() != null ? completionUsage.getOutputTokens() : 0;
        node.put("total_duration", 1234567890L);
        node.put("load_duration", 12345L);
        node.put("prompt_eval_count", promptEvalCount);
        node.put("prompt_eval_duration", 67890L);
        node.put("eval_count", evalCount);
        node.put("eval_duration", 12345L);
    }

    private static HttpResponse jsonResponse(ObjectNode root) {
        return response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withBody(toJson(root));
    }

    private static String toJson(ObjectNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode Ollama response", e);
        }
    }

    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics) {
        List<SseEvent> events = new ArrayList<>();
        String modelName = model != null ? model : "unknown";

        String text = completion.getText();

        // Reasoning ("thinking") delta chunk (before content) when reasoning is set. Ollama streams
        // a reasoning model's chain of thought as message.thinking deltas ahead of the content
        // deltas. Additive — absent unless reasoningText is set.
        String reasoningText = completion.getReasoningText();
        if (reasoningText != null && !reasoningText.isEmpty()) {
            String thinkingChunk = "{\"model\":\"" + escapeJson(modelName) +
                "\",\"created_at\":\"" + Instant.now().toString() +
                "\",\"message\":{\"role\":\"assistant\",\"content\":\"\",\"thinking\":\"" + escapeJson(reasoningText) +
                "\"},\"done\":false}";
            events.add(sseEvent().withData(thinkingChunk));
        }

        // Text delta chunks
        if (text != null && !text.isEmpty()) {
            List<String> tokens = TokenCounter.streamingTextTokens(text, physics);
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    String chunkData = "{\"model\":\"" + escapeJson(modelName) +
                        "\",\"created_at\":\"" + Instant.now().toString() +
                        "\",\"message\":{\"role\":\"assistant\",\"content\":\"" + escapeJson(token) +
                        "\"},\"done\":false}";
                    events.add(sseEvent().withData(chunkData));
                }
            }
        }

        // Ollama streams tool calls in a done:false message, then ends with an empty done:true
        // message carrying done_reason and the counts (docs/api.md "Streaming with tools").
        List<ToolUse> toolCalls = completion.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            ObjectNode toolCallChunk = OBJECT_MAPPER.createObjectNode();
            toolCallChunk.put("model", modelName);
            toolCallChunk.put("created_at", Instant.now().toString());
            ObjectNode toolCallMessage = toolCallChunk.putObject("message");
            toolCallMessage.put("role", "assistant");
            toolCallMessage.put("content", "");
            writeToolCalls(toolCallMessage, toolCalls);
            toolCallChunk.put("done", false);
            try {
                events.add(sseEvent().withData(OBJECT_MAPPER.writeValueAsString(toolCallChunk)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode Ollama tool call streaming chunk", e);
            }
        }

        ObjectNode finalChunk = OBJECT_MAPPER.createObjectNode();
        finalChunk.put("model", modelName);
        finalChunk.put("created_at", Instant.now().toString());
        ObjectNode message = finalChunk.putObject("message");
        message.put("role", "assistant");
        message.put("content", "");
        finalChunk.put("done", true);
        finalChunk.put("done_reason", mapDoneReason(completion.getStopReason()));
        putMetrics(finalChunk, completion);

        try {
            events.add(sseEvent().withData(OBJECT_MAPPER.writeValueAsString(finalChunk)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode Ollama final streaming chunk", e);
        }

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
            JsonNode messagesNode = root.get("messages");
            if (messagesNode == null || !messagesNode.isArray()) {
                return decodeGenerate(root);
            }

            List<ParsedMessage> parsed = new ArrayList<>();
            for (JsonNode msgNode : messagesNode) {
                String rawRole = msgNode.has("role") ? msgNode.get("role").asText("") : "";
                String textContent = "";
                List<ToolUse> toolCalls = new ArrayList<>();
                Map<String, String> toolResults = new LinkedHashMap<>();

                // Parse content
                JsonNode contentNode = msgNode.get("content");
                if (contentNode != null && contentNode.isTextual()) {
                    textContent = contentNode.asText("");
                }

                // Parse tool_calls on assistant messages
                JsonNode toolCallsNode = msgNode.get("tool_calls");
                if (toolCallsNode != null && toolCallsNode.isArray()) {
                    for (JsonNode tcNode : toolCallsNode) {
                        JsonNode functionNode = tcNode.get("function");
                        if (functionNode != null) {
                            String name = functionNode.has("name") ? functionNode.get("name").asText("") : "";
                            String arguments = "{}";
                            if (functionNode.has("arguments")) {
                                JsonNode argsNode = functionNode.get("arguments");
                                if (argsNode.isTextual()) {
                                    arguments = argsNode.asText("");
                                } else {
                                    arguments = argsNode.toString();
                                }
                            }
                            // Ollama tool messages don't have tool_call_id — no ID to set
                            ToolUse tu = ToolUse.toolUse(name).withArguments(arguments);
                            toolCalls.add(tu);
                        }
                    }
                }

                // Tool role messages: correlation by name only
                if ("tool".equalsIgnoreCase(rawRole)) {
                    // Ollama tool messages don't have tool_call_id
                    // Use empty string as the key for correlation by content
                    toolResults.put("", textContent);
                }

                ParsedMessage.Role role = mapOllamaRole(rawRole);

                parsed.add(new ParsedMessage(
                    role,
                    textContent,
                    toolCalls.isEmpty() ? null : toolCalls,
                    toolResults.isEmpty() ? null : toolResults
                ));
            }

            return ParsedConversation.of(parsed);
        } catch (Exception e) {
            return ParsedConversation.empty();
        }
    }

    /** {@code POST /api/generate}: an optional {@code system} prompt and one {@code prompt}, no messages. */
    private static ParsedConversation decodeGenerate(JsonNode root) {
        JsonNode prompt = root.get("prompt");
        if (prompt == null || !prompt.isTextual()) {
            return ParsedConversation.empty();
        }
        List<ParsedMessage> parsed = new ArrayList<>();
        JsonNode system = root.get("system");
        if (system != null && system.isTextual() && !system.asText().isEmpty()) {
            parsed.add(new ParsedMessage(ParsedMessage.Role.SYSTEM, system.asText(), null, null));
        }
        parsed.add(new ParsedMessage(ParsedMessage.Role.USER, prompt.asText(), null, null));
        return ParsedConversation.of(parsed);
    }

    private static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text";
    private static final int DEFAULT_EMBEDDING_DIMENSIONS = 768;

    /**
     * Encodes an Ollama {@code /api/embed} response for a single input. The default
     * dimensionality ({@code nomic-embed-text}) is 768.
     */
    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input) {
        return encodeEmbedding(embedding, input, null);
    }

    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input, String model) {
        return encodeEmbed(embedding, Collections.singletonList(input), model != null ? model : DEFAULT_EMBEDDING_MODEL, null);
    }

    /**
     * Encodes the response for the request's endpoint:
     * <ul>
     *   <li>{@code POST /api/embed}: {@code input} is a string or an array of strings, and the
     *       response is {@code {"model":...,"embeddings":[[...], ...],"total_duration":N,
     *       "load_duration":N,"prompt_eval_count":N}} with one vector per input.</li>
     *   <li>{@code POST /api/embeddings} (deprecated): {@code prompt} is one string, and the
     *       response is {@code {"embedding":[...]}}.</li>
     * </ul>
     * On another path a body with {@code prompt} and no {@code input} selects the legacy shape.
     */
    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, HttpRequest request, String model) {
        JsonNode body = EmbeddingWire.body(request);
        String path = EmbeddingWire.path(request);
        Integer dimensions = EmbeddingWire.requestedDimensions(body.get("dimensions"), "dimensions");
        boolean legacy = path.endsWith("/api/embeddings")
            || (!path.endsWith("/api/embed") && body.has("prompt") && !body.has("input"));
        if (legacy) {
            JsonNode prompt = body.get("prompt");
            String text = prompt != null && prompt.isTextual() ? prompt.asText() : "";
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            EmbeddingWire.addFloats(root.putArray("embedding"), EmbeddingWire.vector(embedding, text, dimensions, DEFAULT_EMBEDDING_DIMENSIONS));
            return EmbeddingWire.json(root, "Ollama");
        }
        return encodeEmbed(
            embedding,
            EmbeddingWire.stringOrArrayInputs(body.get("input")),
            EmbeddingWire.firstNonBlank(EmbeddingWire.text(body, "model"), model, DEFAULT_EMBEDDING_MODEL),
            dimensions
        );
    }

    private HttpResponse encodeEmbed(EmbeddingResponse embedding, List<String> inputs, String model, Integer dimensions) {
        EmbeddingWire.checkTotalValues(embedding, dimensions, DEFAULT_EMBEDDING_DIMENSIONS, inputs.size());
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", model);
        ArrayNode embeddings = root.putArray("embeddings");
        for (String input : inputs) {
            EmbeddingWire.addFloats(embeddings.addArray(), EmbeddingWire.vector(embedding, input, dimensions, DEFAULT_EMBEDDING_DIMENSIONS));
        }
        root.put("total_duration", 14143917L);
        root.put("load_duration", 1019500L);
        root.put("prompt_eval_count", EmbeddingWire.tokens(inputs));
        return EmbeddingWire.json(root, "Ollama");
    }

    /**
     * Ollama's {@code done_reason} is {@code stop} or {@code length} for a generation ({@code load}
     * and {@code unload} for model management), with no separate value for tool calls. Native
     * values pass through; other providers' max-token values map to {@code length}, and
     * everything else, including tool-call values, to {@code stop}.
     */
    static String mapDoneReason(String stopReason) {
        if (stopReason == null) {
            return "stop";
        }
        switch (stopReason) {
            case "stop":
            case "length":
            case "load":
            case "unload":
                return stopReason;
            case "max_tokens":
            case "MAX_TOKENS":
            case "model_context_window_exceeded":
                return "length";
            default:
                return "stop";
        }
    }

    private static ParsedMessage.Role mapOllamaRole(String rawRole) {
        if (rawRole == null) {
            return ParsedMessage.Role.USER;
        }
        switch (rawRole.toLowerCase()) {
            case "assistant":
                return ParsedMessage.Role.ASSISTANT;
            case "user":
                return ParsedMessage.Role.USER;
            case "tool":
                return ParsedMessage.Role.TOOL;
            case "system":
                return ParsedMessage.Role.SYSTEM;
            default:
                return ParsedMessage.Role.USER;
        }
    }

    /** Ollama sends tool-call arguments as a JSON object, so a non-object value is wrapped as {"value": ...}. */
    private static void writeToolCalls(ObjectNode parent, List<ToolUse> toolCalls) {
        ArrayNode toolCallsArray = parent.putArray("tool_calls");
        for (ToolUse toolCall : toolCalls) {
            ObjectNode tc = toolCallsArray.addObject();
            if (toolCall.getId() != null && !toolCall.getId().isEmpty()) {
                tc.put("id", toolCall.getId());
            }
            ObjectNode function = tc.putObject("function");
            function.put("name", toolCall.getName());
            String args = toolCall.getArguments();
            if (args != null) {
                try {
                    JsonNode parsed = OBJECT_MAPPER.readTree(args);
                    if (parsed != null && parsed.isObject()) {
                        function.set("arguments", parsed);
                    } else {
                        function.putObject("arguments").put("value", args);
                    }
                } catch (Exception e) {
                    function.putObject("arguments").put("value", args);
                }
            } else {
                function.putObject("arguments");
            }
        }
    }

    private static String escapeJson(String value) {
        return JsonEscape.escape(value);
    }
}
