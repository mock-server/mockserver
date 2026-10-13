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
import org.mockserver.uuid.UUIDService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.SseEvent.sseEvent;

/**
 * Codec for OpenAI Chat Completions API (version 2025-01).
 * Encodes MockServer Completion objects into OpenAI-format HTTP responses
 * for both non-streaming and streaming (SSE) paths.
 * Also handles OpenAI Embeddings API responses.
 */
public class OpenAiChatCompletionsCodec implements ProviderCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Provider provider() {
        return Provider.OPENAI;
    }

    @Override
    public String apiVersion() {
        return "2025-01";
    }

    @Override
    public HttpResponse encode(Completion completion, String model) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("id", "chatcmpl-" + randomId(29));
        root.put("object", "chat.completion");
        root.put("created", System.currentTimeMillis() / 1000);
        root.put("model", model != null ? model : "unknown");
        String systemFingerprint = newSystemFingerprint();
        if (systemFingerprint != null) {
            root.put("system_fingerprint", systemFingerprint);
        }

        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);

        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");

        String text = completion.getText();
        List<ToolUse> toolCalls = completion.getToolCalls();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();

        // content: null if text is null AND there are tool calls; otherwise use text
        if (text == null && hasToolCalls) {
            message.putNull("content");
        } else {
            message.put("content", text != null ? text : "");
        }
        String reasoningText = completion.getReasoningText();
        if (reasoningField() != null && reasoningText != null && !reasoningText.isEmpty()) {
            message.put(reasoningField(), reasoningText);
        }

        // tool_calls: omitted entirely when no tool calls; never emit empty array
        if (hasToolCalls) {
            ArrayNode toolCallsArray = message.putArray("tool_calls");
            for (ToolUse toolCall : toolCalls) {
                ObjectNode tc = toolCallsArray.addObject();
                tc.put("id", toolCallId(toolCall));
                tc.put("type", "function");
                ObjectNode function = tc.putObject("function");
                function.put("name", toolCall.getName());
                // arguments is always a JSON-as-string (OpenAI's quirk)
                function.put("arguments", toolCall.getArguments() != null ? toolCall.getArguments() : "{}");
            }
        }

        if (emitsChoiceLogprobs()) {
            choice.putNull("logprobs");
        }
        // finish_reason mapping
        String finishReason = finishReason(completion, hasToolCalls);
        choice.put("finish_reason", finishReason);
        addChoiceFields(choice, completion, finishReason);

        ObjectNode usage = usageNode(completion.getUsage(), model, completion.getStreamingPhysics());
        root.set("usage", usage);
        addResponseFields(root, usage, newProviderRequestId());

        try {
            String json = OBJECT_MAPPER.writeValueAsString(root);
            return response()
                .withStatusCode(200)
                .withHeader("content-type", "application/json")
                .withBody(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode OpenAI response", e);
        }
    }

    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics) {
        return encodeStreamingChunks(completion, model, physics, false);
    }

    /**
     * Streams the usage chunk only when the request opts in with
     * {@code stream_options.include_usage: true}, as the OpenAI and Azure OpenAI APIs do:
     * every chunk then carries {@code "usage": null}, and one extra chunk with
     * {@code "choices": []} and the token usage precedes {@code [DONE]}. Without the
     * opt-in no chunk carries a {@code usage} key, even when the expectation sets usage.
     */
    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics, HttpRequest request) {
        return encodeStreamingChunks(completion, model, physics, includeUsageRequested(request));
    }

    private List<SseEvent> encodeStreamingChunks(Completion completion, String model, StreamingPhysics physics, boolean includeUsageRequested) {
        List<SseEvent> events = new ArrayList<>();
        boolean includeUsage = streamsUsageChunk(includeUsageRequested);
        String id = "chatcmpl-" + randomId(29);
        String providerRequestId = newProviderRequestId();
        String systemFingerprint = newSystemFingerprint();
        long created = System.currentTimeMillis() / 1000;
        String modelName = model != null ? model : "unknown";
        String prefix = chunkPrefix(id, created, modelName, systemFingerprint);
        ObjectNode usage = usageNode(completion.getUsage(), model, physics != null ? physics : completion.getStreamingPhysics());
        String openChoiceFields = streamChoiceFields(completion, null);

        // 1. First chunk: role-only
        events.add(sseEvent().withData(buildChunk(prefix,
            "{\"role\":\"assistant\",\"content\":\"\"}", null, openChoiceFields, includeUsage, firstChunkFields(providerRequestId))));

        // 2a. Reasoning chunks, for providers that stream reasoning text in the delta
        String reasoningText = completion.getReasoningText();
        if (reasoningField() != null && reasoningText != null && !reasoningText.isEmpty()) {
            for (String token : TokenCounter.streamingTextTokens(reasoningText, physics)) {
                if (!token.isEmpty()) {
                    events.add(sseEvent().withData(buildChunk(prefix,
                        "{\"" + reasoningField() + "\":\"" + escapeJson(token) + "\"}", null, openChoiceFields, includeUsage, "")));
                }
            }
        }

        // 2. Text content chunks
        String text = completion.getText();
        if (text != null && !text.isEmpty()) {
            List<String> tokens = TokenCounter.streamingTextTokens(text, physics);
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    events.add(sseEvent().withData(buildChunk(prefix,
                        "{\"content\":\"" + escapeJson(token) + "\"}", null, openChoiceFields, includeUsage, "")));
                }
            }
        }

        // 3. Tool call chunks
        List<ToolUse> toolCalls = completion.getToolCalls();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        if (hasToolCalls) {
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolUse toolCall = toolCalls.get(i);
                String toolCallId = toolCallId(toolCall);
                String args = toolCall.getArguments() != null ? toolCall.getArguments() : "{}";
                String delta = "{\"tool_calls\":[{\"index\":" + i + ",\"id\":\"" + escapeJson(toolCallId) +
                    "\",\"type\":\"function\",\"function\":{\"name\":\"" + escapeJson(toolCall.getName()) +
                    "\",\"arguments\":\"" + escapeJson(args) + "\"}}]}";
                events.add(sseEvent().withData(buildChunk(prefix, delta, null, openChoiceFields, includeUsage, "")));
            }
        }

        // 4. Final chunk with finish_reason
        String finishReason = finishReason(completion, hasToolCalls);
        events.add(sseEvent().withData(buildChunk(prefix, "{}", finishReason,
            streamChoiceFields(completion, finishReason), includeUsage, finishChunkFields(providerRequestId, usage))));

        // 5. Usage chunk (when the request set stream_options.include_usage, or the provider always sends it)
        if (includeUsage) {
            events.add(sseEvent().withData(buildUsageChunk(prefix, usageChunkChoices(completion, finishReason), usage)));
        }

        // 6. [DONE] sentinel
        events.add(sseEvent().withData("[DONE]"));

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
                return ParsedConversation.empty();
            }

            List<ParsedMessage> parsed = new ArrayList<>();
            for (JsonNode msgNode : messagesNode) {
                String rawRole = msgNode.has("role") ? msgNode.get("role").asText("") : "";
                JsonNode contentNode = msgNode.get("content");

                String textContent = "";
                List<ToolUse> toolCalls = new ArrayList<>();
                Map<String, String> toolResults = new LinkedHashMap<>();
                List<ParsedMessage.ImagePart> images = new ArrayList<>();
                List<ParsedMessage.AudioPart> audio = new ArrayList<>();

                // Parse text content
                if (contentNode != null) {
                    if (contentNode.isTextual()) {
                        textContent = contentNode.asText("");
                    } else if (contentNode.isArray()) {
                        StringBuilder textBuilder = new StringBuilder();
                        for (JsonNode part : contentNode) {
                            String partType = part.has("type") ? part.get("type").asText("") : "";
                            if ("text".equals(partType)) {
                                String text = part.has("text") ? part.get("text").asText("") : "";
                                textBuilder.append(text);
                            } else if ("image_url".equals(partType)) {
                                // OpenAI image part: {"type":"image_url","image_url":{"url":"data:image/png;base64,..."}}
                                images.add(new ParsedMessage.ImagePart(mediaTypeFromDataUrl(part.path("image_url").path("url").asText(""))));
                            } else if ("input_audio".equals(partType)) {
                                // OpenAI audio part: {"type":"input_audio","input_audio":{"data":"<base64>","format":"wav"}}
                                String format = part.path("input_audio").path("format").asText("");
                                audio.add(new ParsedMessage.AudioPart(format.isEmpty() ? null : format));
                            }
                        }
                        textContent = textBuilder.toString();
                    } else if (contentNode.isNull()) {
                        textContent = "";
                    }
                }

                // Parse tool_calls on assistant messages
                JsonNode toolCallsNode = msgNode.get("tool_calls");
                if (toolCallsNode != null && toolCallsNode.isArray()) {
                    for (JsonNode tcNode : toolCallsNode) {
                        String callId = tcNode.has("id") ? tcNode.get("id").asText("") : null;
                        JsonNode functionNode = tcNode.get("function");
                        if (functionNode != null) {
                            String name = functionNode.has("name") ? functionNode.get("name").asText("") : "";
                            String arguments = functionNode.has("arguments") ? functionNode.get("arguments").asText("") : "{}";
                            ToolUse tu = ToolUse.toolUse(name).withArguments(arguments);
                            if (callId != null && !callId.isEmpty()) {
                                tu.withId(callId);
                            }
                            toolCalls.add(tu);
                        }
                    }
                }

                // Parse tool role messages with tool_call_id
                if ("tool".equalsIgnoreCase(rawRole)) {
                    String toolCallId = msgNode.has("tool_call_id") ? msgNode.get("tool_call_id").asText("") : "";
                    String toolContent = textContent;
                    if (!toolCallId.isEmpty()) {
                        toolResults.put(toolCallId, toolContent);
                    }
                }

                // Map role
                ParsedMessage.Role role = mapOpenAiRole(rawRole);

                parsed.add(new ParsedMessage(
                    role,
                    textContent,
                    toolCalls.isEmpty() ? null : toolCalls,
                    toolResults.isEmpty() ? null : toolResults,
                    images.isEmpty() ? null : images,
                    audio.isEmpty() ? null : audio
                ));
            }

            return ParsedConversation.of(parsed);
        } catch (Exception e) {
            return ParsedConversation.empty();
        }
    }

    /**
     * Extract the media type from an OpenAI image data URL
     * ({@code data:image/png;base64,...}), or {@code null} for a remote https URL.
     */
    private static String mediaTypeFromDataUrl(String url) {
        if (url != null && url.startsWith("data:")) {
            int semi = url.indexOf(';');
            int comma = url.indexOf(',');
            int end = semi >= 0 ? semi : (comma >= 0 ? comma : -1);
            if (end > 5) {
                return url.substring(5, end);
            }
        }
        return null;
    }

    private static ParsedMessage.Role mapOpenAiRole(String rawRole) {
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
            case "developer":
                return ParsedMessage.Role.SYSTEM;
            default:
                return ParsedMessage.Role.USER;
        }
    }

    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final int DEFAULT_EMBEDDING_DIMENSIONS = 1536;

    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input) {
        return encodeEmbeddings(embedding, Collections.singletonList(input), DEFAULT_EMBEDDING_MODEL, null, false);
    }

    /**
     * Encodes an OpenAI {@code POST /v1/embeddings} response with one {@code data[i]} entry per
     * input, in input order. The request's {@code input} may be a string, an array of strings, an
     * array of token integers (one input) or an array of token arrays. The request's
     * {@code model} is echoed, its {@code dimensions} is honoured unless the expectation sets
     * dimensions, and {@code encoding_format: "base64"} returns each vector as base64
     * little-endian float32, which the OpenAI SDKs request by default. An empty {@code input}
     * ({@code ""}, {@code []}, or an empty string or token array inside the array) is rejected
     * with a 400, as the real API does.
     */
    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, HttpRequest request, String model) {
        JsonNode body = EmbeddingWire.body(request);
        rejectEmptyEmbeddingInput(body.get("input"));
        return encodeEmbeddings(
            embedding,
            EmbeddingWire.stringOrArrayInputs(body.get("input")),
            EmbeddingWire.firstNonBlank(EmbeddingWire.text(body, "model"), model, DEFAULT_EMBEDDING_MODEL),
            EmbeddingWire.requestedDimensions(body.get("dimensions"), "dimensions"),
            "base64".equals(EmbeddingWire.text(body, "encoding_format"))
        );
    }

    private static void rejectEmptyEmbeddingInput(JsonNode input) {
        if (input == null || input.isNull()) {
            return;
        }
        boolean empty = isEmptyInput(input);
        if (!empty && input.isArray()) {
            for (JsonNode element : input) {
                if (isEmptyInput(element)) {
                    empty = true;
                    break;
                }
            }
        }
        if (empty) {
            // the real API reports this error with param null
            throw new EmbeddingWire.InvalidEmbeddingRequestException(null,
                "input must not be empty: send a non-empty string, or an array of non-empty strings or token arrays");
        }
    }

    private static boolean isEmptyInput(JsonNode input) {
        return (input.isTextual() && input.asText().isEmpty()) || (input.isArray() && input.isEmpty());
    }

    private HttpResponse encodeEmbeddings(EmbeddingResponse embedding, List<String> inputs, String model, Integer requestedDimensions, boolean base64) {
        EmbeddingWire.checkTotalValues(
            EmbeddingWire.dimensions(embedding, requestedDimensions, DEFAULT_EMBEDDING_DIMENSIONS) * (long) inputs.size(),
            base64 ? EmbeddingWire.maxTotalBase64Values() : EmbeddingWire.maxTotalValues()
        );
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("object", "list");
        ArrayNode data = root.putArray("data");
        for (int i = 0; i < inputs.size(); i++) {
            double[] vector = EmbeddingWire.build(embedding, inputs.get(i), requestedDimensions, DEFAULT_EMBEDDING_DIMENSIONS);
            ObjectNode embeddingObj = data.addObject();
            embeddingObj.put("object", "embedding");
            embeddingObj.put("index", i);
            if (base64) {
                embeddingObj.put("embedding", EmbeddingWire.base64Float32(vector));
            } else {
                EmbeddingWire.addFloats(embeddingObj.putArray("embedding"), vector);
            }
        }
        root.put("model", model);
        int approxTokens = EmbeddingWire.tokens(inputs);
        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", approxTokens);
        usage.put("total_tokens", approxTokens);
        return EmbeddingWire.json(root, "OpenAI");
    }

    static double[] generateDeterministicVector(String input, int dimensions, long seed) {
        return EmbeddingVectors.generateDeterministicVector(input, dimensions, seed);
    }

    static void normalizeL2(double[] vector) {
        EmbeddingVectors.normalizeL2(vector);
    }

    private String buildChunk(String prefix, String deltaJson, String finishReason, String choiceFields, boolean includeUsage, String extraFields) {
        StringBuilder sb = new StringBuilder(prefix);
        sb.append("\"choices\":[{\"index\":0,\"delta\":").append(deltaJson).append(",");
        if (finishReason != null) {
            sb.append("\"finish_reason\":\"").append(escapeJson(finishReason)).append("\"");
        } else {
            sb.append("\"finish_reason\":null");
        }
        sb.append(choiceFields).append("}]");
        if (includeUsage) {
            sb.append(",\"usage\":null");
        }
        sb.append(extraFields).append("}");
        return sb.toString();
    }

    private String buildUsageChunk(String prefix, String choices, ObjectNode usage) {
        try {
            return new StringBuilder(prefix)
                .append("\"choices\":").append(choices).append(",\"usage\":")
                .append(OBJECT_MAPPER.writeValueAsString(usage))
                .append("}")
                .toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode OpenAI streaming usage chunk", e);
        }
    }

    private String chunkPrefix(String id, long created, String model, String systemFingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"").append(id).append("\",");
        sb.append("\"object\":\"chat.completion.chunk\",");
        sb.append("\"created\":").append(created).append(",");
        sb.append("\"model\":\"").append(escapeJson(model)).append("\",");
        if (systemFingerprint != null) {
            sb.append("\"system_fingerprint\":\"").append(escapeJson(systemFingerprint)).append("\",");
        }
        return sb.toString();
    }

    /**
     * Whether the request body sets {@code stream_options.include_usage} to JSON {@code true}.
     * Fail-soft: an absent, unparseable or non-boolean value means no usage chunk.
     */
    private static boolean includeUsageRequested(HttpRequest request) {
        try {
            String body = request != null ? request.getBodyAsText() : null;
            if (body == null || body.isEmpty()) {
                return false;
            }
            JsonNode includeUsage = OBJECT_MAPPER.readTree(body).path("stream_options").path("include_usage");
            return includeUsage.isBoolean() && includeUsage.booleanValue();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The Chat Completions {@code usage} object, shared by the non-streaming body and the
     * streaming usage chunk. Cached-input and reasoning token details are emitted only when
     * set (non-null, non-zero), under OpenAI's native nested-details keys, so existing
     * fixtures stay byte-identical when these optional fields are unset.
     */
    private ObjectNode usageNode(Usage completionUsage, String model, StreamingPhysics physics) {
        ObjectNode usage = OBJECT_MAPPER.createObjectNode();
        int promptTokens = completionUsage != null && completionUsage.getInputTokens() != null ? completionUsage.getInputTokens() : 0;
        int completionTokens = completionUsage != null && completionUsage.getOutputTokens() != null ? completionUsage.getOutputTokens() : 0;
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens);
        if (completionUsage != null) {
            Integer cachedInputTokens = completionUsage.getCachedInputTokens();
            if (cachedInputTokens != null && cachedInputTokens != 0) {
                usage.putObject("prompt_tokens_details").put("cached_tokens", cachedInputTokens);
            }
            Integer reasoningTokens = completionUsage.getReasoningTokens();
            if (reasoningTokens != null && reasoningTokens != 0) {
                usage.putObject("completion_tokens_details").put("reasoning_tokens", reasoningTokens);
            }
        }
        addUsageFields(usage, completionUsage, model, physics);
        return usage;
    }

    /**
     * Whether each non-streaming choice carries {@code "logprobs": null}, which the OpenAI and
     * Azure OpenAI specs require (nullable) on every choice. OpenAI-compatible providers opt in
     * individually, because not all of them send it.
     */
    protected boolean emitsChoiceLogprobs() {
        return true;
    }

    // Hooks for the OpenAI-compatible providers' additions to the OpenAI shape; OpenAI adds nothing.

    /** The message and delta field that carries {@link Completion#getReasoningText()}, or null for none. */
    protected String reasoningField() {
        return null;
    }

    /** Whether a stream ends with the {@code choices: []} usage chunk, given the request's opt-in. */
    protected boolean streamsUsageChunk(boolean includeUsageRequested) {
        return includeUsageRequested;
    }

    /** A provider-assigned request id echoed in the response and stream, or null for none. */
    protected String newProviderRequestId() {
        return null;
    }

    /** The {@code system_fingerprint} of a response, or of every chunk of one stream; null for none. */
    protected String newSystemFingerprint() {
        return null;
    }

    protected void addUsageFields(ObjectNode usage, Usage completionUsage, String model, StreamingPhysics physics) {
    }

    protected void addResponseFields(ObjectNode root, ObjectNode usage, String providerRequestId) {
    }

    /** JSON members, each with a leading comma, added to the first chunk of a stream. */
    protected String firstChunkFields(String providerRequestId) {
        return "";
    }

    /** JSON members, each with a leading comma, added to the chunk carrying {@code finish_reason}. */
    protected String finishChunkFields(String providerRequestId, ObjectNode usage) {
        return "";
    }

    /** The choice's {@code finish_reason}, whole or on the last streamed chunk. */
    protected String finishReason(Completion completion, boolean hasToolCalls) {
        return mapFinishReason(completion.getStopReason(), hasToolCalls, completion.getToolChoice());
    }

    /** Members added to the non-streaming choice after {@code finish_reason}. */
    protected void addChoiceFields(ObjectNode choice, Completion completion, String finishReason) {
    }

    /**
     * JSON members, each with a leading comma, added after {@code finish_reason} to the choice of
     * every streamed chunk; {@code finishReason} is null on every chunk but the one that carries it.
     */
    protected String streamChoiceFields(Completion completion, String finishReason) {
        return "";
    }

    /** The {@code choices} array of the usage chunk that ends a stream. */
    protected String usageChunkChoices(Completion completion, String finishReason) {
        return "[]";
    }

    static String toJson(ObjectNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode OpenAI-compatible response field", e);
        }
    }

    /** The configured {@link ToolUse#getId()}, so a test can fix the id its client sends back; else a generated one. */
    private static String toolCallId(ToolUse toolCall) {
        String configured = toolCall.getId();
        return configured != null && !configured.isEmpty() ? configured : "call_" + randomId(24);
    }

    private static String mapFinishReason(String stopReason, boolean hasToolCalls, String toolChoice) {
        // When the request forces a tool call (tool_choice=required) and a tool is available,
        // OpenAI returns finish_reason "tool_calls" regardless of any configured stop reason.
        if (hasToolCalls && "required".equalsIgnoreCase(toolChoice)) {
            return "tool_calls";
        }
        if (stopReason == null) {
            return hasToolCalls ? "tool_calls" : "stop";
        }
        switch (stopReason) {
            case "end_turn":
                return "stop";
            case "tool_use":
                return "tool_calls";
            case "max_tokens":
                return "length";
            case "stop":
            case "length":
            case "tool_calls":
            case "content_filter":
                return stopReason;
            default:
                return stopReason;
        }
    }

    static String randomId(int length) {
        String uuid = UUIDService.getNonSecureUUID().replace("-", "");
        while (uuid.length() < length) {
            uuid = uuid + UUIDService.getNonSecureUUID().replace("-", "");
        }
        return uuid.substring(0, length);
    }

    private static String escapeJson(String value) {
        return JsonEscape.escape(value);
    }
}
