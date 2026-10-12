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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.SseEvent.sseEvent;

/**
 * Codec for Anthropic Messages API (version 2024-10-22).
 * Encodes MockServer Completion objects into Anthropic-format HTTP responses
 * for both non-streaming and streaming (SSE) paths.
 */
public class AnthropicCodec implements ProviderCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Provider provider() {
        return Provider.ANTHROPIC;
    }

    @Override
    public String apiVersion() {
        return "2024-10-22";
    }

    @Override
    public HttpResponse encode(Completion completion, String model) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("id", "msg_" + randomBase62(24));
        root.put("type", "message");
        root.put("role", "assistant");
        root.put("model", model != null ? model : "unknown");

        ArrayNode content = root.putArray("content");

        // Thinking/reasoning block (prepended before the text block when reasoning is set).
        // Anthropic encodes extended-thinking output as a leading
        // {"type":"thinking","thinking":"...","signature":"..."} content block. Additive: absent
        // unless Completion.reasoningText is set, so existing fixtures are unchanged.
        String reasoningText = completion.getReasoningText();
        boolean hasReasoning = reasoningText != null && !reasoningText.isEmpty();
        if (hasReasoning) {
            ObjectNode thinkingBlock = content.addObject();
            thinkingBlock.put("type", "thinking");
            thinkingBlock.put("thinking", reasoningText);
            String signature = completion.getReasoningSignature();
            if (signature != null && !signature.isEmpty()) {
                thinkingBlock.put("signature", signature);
            }
        }

        // Text block (if text is non-null and non-empty)
        String text = completion.getText();
        boolean hasText = text != null && !text.isEmpty();
        if (hasText) {
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", text);
        }

        // Tool use blocks
        List<ToolUse> toolCalls = completion.getToolCalls();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        if (hasToolCalls) {
            for (ToolUse toolCall : toolCalls) {
                ObjectNode toolBlock = content.addObject();
                toolBlock.put("type", "tool_use");
                toolBlock.put("id", "toolu_" + randomBase62(24));
                toolBlock.put("name", toolCall.getName());
                // Try to parse arguments as JSON; otherwise emit as raw string
                String args = toolCall.getArguments();
                if (args != null) {
                    try {
                        JsonNode parsed = OBJECT_MAPPER.readTree(args);
                        toolBlock.set("input", parsed);
                    } catch (Exception e) {
                        toolBlock.put("input", args);
                    }
                } else {
                    toolBlock.putObject("input");
                }
            }
        }

        root.put("stop_reason", mapStopReason(completion.getStopReason(), hasToolCalls));
        root.putNull("stop_sequence");

        // usage
        ObjectNode usage = root.putObject("usage");
        Usage completionUsage = completion.getUsage();
        int inputTokens = completionUsage != null && completionUsage.getInputTokens() != null ? completionUsage.getInputTokens() : 0;
        int outputTokens = completionUsage != null && completionUsage.getOutputTokens() != null ? completionUsage.getOutputTokens() : 0;
        usage.put("input_tokens", inputTokens);
        usage.put("output_tokens", outputTokens);
        // Cached/cache-creation tokens are emitted only when set (non-null, non-zero) so existing
        // fixtures stay byte-identical when these optional fields are unset. Native Anthropic keys.
        if (completionUsage != null) {
            Integer cachedInputTokens = completionUsage.getCachedInputTokens();
            if (cachedInputTokens != null && cachedInputTokens != 0) {
                usage.put("cache_read_input_tokens", cachedInputTokens);
            }
            Integer cacheCreationTokens = completionUsage.getCacheCreationTokens();
            if (cacheCreationTokens != null && cacheCreationTokens != 0) {
                usage.put("cache_creation_input_tokens", cacheCreationTokens);
            }
        }

        try {
            String json = OBJECT_MAPPER.writeValueAsString(root);
            return response()
                .withStatusCode(200)
                .withHeader("content-type", "application/json")
                .withBody(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode Anthropic response", e);
        }
    }

    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics) {
        List<SseEvent> events = new ArrayList<>();
        String messageId = "msg_" + randomBase62(24);
        String modelName = model != null ? model : "unknown";

        Usage completionUsage = completion.getUsage();
        int inputTokens = completionUsage != null && completionUsage.getInputTokens() != null ? completionUsage.getInputTokens() : 0;
        int outputTokens = completionUsage != null && completionUsage.getOutputTokens() != null ? completionUsage.getOutputTokens() : 0;

        // 1. message_start — mirror cached/cache-creation tokens into the message_start usage when
        // set (non-null, non-zero), matching the non-streaming encode(). Omitted when unset so
        // existing streaming fixtures stay byte-identical.
        StringBuilder messageStartUsage = new StringBuilder();
        messageStartUsage.append("{\"input_tokens\":").append(inputTokens).append(",\"output_tokens\":1");
        if (completionUsage != null) {
            Integer cachedInputTokens = completionUsage.getCachedInputTokens();
            if (cachedInputTokens != null && cachedInputTokens != 0) {
                messageStartUsage.append(",\"cache_read_input_tokens\":").append(cachedInputTokens);
            }
            Integer cacheCreationTokens = completionUsage.getCacheCreationTokens();
            if (cacheCreationTokens != null && cacheCreationTokens != 0) {
                messageStartUsage.append(",\"cache_creation_input_tokens\":").append(cacheCreationTokens);
            }
        }
        messageStartUsage.append("}");
        String messageStartData = "{\"type\":\"message_start\",\"message\":{\"id\":\"" + messageId +
            "\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"" + escapeJson(modelName) +
            "\",\"stop_reason\":null,\"stop_sequence\":null,\"usage\":" + messageStartUsage + "}}";
        events.add(sseEvent().withEvent("message_start").withData(messageStartData));

        int contentIndex = 0;

        // 2a. Thinking/reasoning content block (before the text block) when reasoning is set.
        // Emits content_block_start (thinking), a thinking_delta with the text, an optional
        // signature_delta, then content_block_stop. Additive — absent unless reasoningText is set.
        String reasoningText = completion.getReasoningText();
        if (reasoningText != null && !reasoningText.isEmpty()) {
            String thinkingStartData = "{\"type\":\"content_block_start\",\"index\":" + contentIndex +
                ",\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}";
            events.add(sseEvent().withEvent("content_block_start").withData(thinkingStartData));

            String thinkingDeltaData = "{\"type\":\"content_block_delta\",\"index\":" + contentIndex +
                ",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"" + escapeJson(reasoningText) + "\"}}";
            events.add(sseEvent().withEvent("content_block_delta").withData(thinkingDeltaData));

            String signature = completion.getReasoningSignature();
            if (signature != null && !signature.isEmpty()) {
                String signatureDeltaData = "{\"type\":\"content_block_delta\",\"index\":" + contentIndex +
                    ",\"delta\":{\"type\":\"signature_delta\",\"signature\":\"" + escapeJson(signature) + "\"}}";
                events.add(sseEvent().withEvent("content_block_delta").withData(signatureDeltaData));
            }

            String thinkingStopData = "{\"type\":\"content_block_stop\",\"index\":" + contentIndex + "}";
            events.add(sseEvent().withEvent("content_block_stop").withData(thinkingStopData));
            contentIndex++;
        }

        // 2. Text content (if present)
        String text = completion.getText();
        if (text != null && !text.isEmpty()) {
            // content_block_start
            String blockStartData = "{\"type\":\"content_block_start\",\"index\":" + contentIndex +
                ",\"content_block\":{\"type\":\"text\",\"text\":\"\"}}";
            events.add(sseEvent().withEvent("content_block_start").withData(blockStartData));

            // Split text into streaming deltas: subword-sized by default, or whole words
            // when streamingPhysics.subwordStreaming is explicitly false.
            List<String> tokens = TokenCounter.streamingTextTokens(text, physics);
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    String deltaData = "{\"type\":\"content_block_delta\",\"index\":" + contentIndex +
                        ",\"delta\":{\"type\":\"text_delta\",\"text\":\"" + escapeJson(token) + "\"}}";
                    events.add(sseEvent().withEvent("content_block_delta").withData(deltaData));
                }
            }

            // content_block_stop
            String blockStopData = "{\"type\":\"content_block_stop\",\"index\":" + contentIndex + "}";
            events.add(sseEvent().withEvent("content_block_stop").withData(blockStopData));
            contentIndex++;
        }

        // 3. Tool calls
        List<ToolUse> toolCalls = completion.getToolCalls();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        if (hasToolCalls) {
            for (ToolUse toolCall : toolCalls) {
                String toolId = "toolu_" + randomBase62(24);

                // content_block_start for tool_use
                String toolStartData = "{\"type\":\"content_block_start\",\"index\":" + contentIndex +
                    ",\"content_block\":{\"type\":\"tool_use\",\"id\":\"" + toolId +
                    "\",\"name\":\"" + escapeJson(toolCall.getName()) + "\",\"input\":{}}}";
                events.add(sseEvent().withEvent("content_block_start").withData(toolStartData));

                // content_block_delta for tool arguments
                String args = toolCall.getArguments() != null ? toolCall.getArguments() : "{}";
                String toolDeltaData = "{\"type\":\"content_block_delta\",\"index\":" + contentIndex +
                    ",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"" + escapeJson(args) + "\"}}";
                events.add(sseEvent().withEvent("content_block_delta").withData(toolDeltaData));

                // content_block_stop
                String toolStopData = "{\"type\":\"content_block_stop\",\"index\":" + contentIndex + "}";
                events.add(sseEvent().withEvent("content_block_stop").withData(toolStopData));
                contentIndex++;
            }
        }

        // 4. message_delta
        String stopReason = mapStopReason(completion.getStopReason(), hasToolCalls);
        String messageDeltaData = "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + escapeJson(stopReason) +
            "\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":" + outputTokens + "}}";
        events.add(sseEvent().withEvent("message_delta").withData(messageDeltaData));

        // 5. message_stop
        events.add(sseEvent().withEvent("message_stop").withData("{\"type\":\"message_stop\"}"));

        // Apply streaming physics
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

            // Anthropic carries the system prompt in a top-level "system" field (string or an
            // array of text content blocks), NOT as a message in the "messages" array. Map it to a
            // leading SYSTEM message so system-prompt-aware consumers (conversation matching,
            // optimisation signals) see it the same way they do for OpenAI-shaped traffic.
            String systemText = systemPromptText(root.get("system"));
            if (!systemText.isEmpty()) {
                parsed.add(new ParsedMessage(ParsedMessage.Role.SYSTEM, systemText, null, null, null));
            }

            for (JsonNode msgNode : messagesNode) {
                String rawRole = msgNode.has("role") ? msgNode.get("role").asText("") : "";
                JsonNode contentNode = msgNode.get("content");

                String textContent = "";
                List<ToolUse> toolCalls = new ArrayList<>();
                Map<String, String> toolResults = new LinkedHashMap<>();
                List<ParsedMessage.ImagePart> images = new ArrayList<>();
                boolean hasToolResult = false;

                if (contentNode != null) {
                    if (contentNode.isTextual()) {
                        textContent = contentNode.asText("");
                    } else if (contentNode.isArray()) {
                        StringBuilder textBuilder = new StringBuilder();
                        for (JsonNode block : contentNode) {
                            String blockType = block.has("type") ? block.get("type").asText("") : "";
                            if ("text".equals(blockType)) {
                                String text = block.has("text") ? block.get("text").asText("") : "";
                                textBuilder.append(text);
                            } else if ("image".equals(blockType)) {
                                // Anthropic image block: {"type":"image","source":{"type":"base64","media_type":"image/png","data":"..."}}
                                JsonNode source = block.path("source");
                                String mediaType = source.has("media_type") ? source.path("media_type").asText(null) : null;
                                images.add(new ParsedMessage.ImagePart(mediaType));
                            } else if ("tool_use".equals(blockType)) {
                                String toolId = block.has("id") ? block.get("id").asText("") : null;
                                String name = block.has("name") ? block.get("name").asText("") : "";
                                String inputStr = "";
                                if (block.has("input")) {
                                    JsonNode inputNode = block.get("input");
                                    if (inputNode.isTextual()) {
                                        inputStr = inputNode.asText("");
                                    } else {
                                        inputStr = inputNode.toString();
                                    }
                                }
                                ToolUse tu = ToolUse.toolUse(name).withArguments(inputStr);
                                if (toolId != null && !toolId.isEmpty()) {
                                    tu.withId(toolId);
                                }
                                toolCalls.add(tu);
                            } else if ("tool_result".equals(blockType)) {
                                hasToolResult = true;
                                String toolUseId = block.has("tool_use_id") ? block.get("tool_use_id").asText("") : "";
                                String resultContent = "";
                                if (block.has("content")) {
                                    JsonNode resultContentNode = block.get("content");
                                    if (resultContentNode.isTextual()) {
                                        resultContent = resultContentNode.asText("");
                                    } else if (resultContentNode.isArray()) {
                                        StringBuilder resultBuilder = new StringBuilder();
                                        for (JsonNode part : resultContentNode) {
                                            if ("text".equals(part.path("type").asText(""))) {
                                                resultBuilder.append(part.path("text").asText(""));
                                            }
                                        }
                                        resultContent = resultBuilder.toString();
                                    } else {
                                        resultContent = resultContentNode.toString();
                                    }
                                }
                                toolResults.put(toolUseId, resultContent);
                            }
                        }
                        textContent = textBuilder.toString();
                    }
                }

                // Determine role: if content has tool_result blocks, role is TOOL for matcher purposes
                ParsedMessage.Role role;
                if (hasToolResult) {
                    role = ParsedMessage.Role.TOOL;
                } else {
                    role = mapRole(rawRole);
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

    /**
     * Extracts the system prompt text from the Anthropic top-level "system" field, which may be a
     * plain string or an array of content blocks ({@code [{"type":"text","text":"..."}]}). Text from
     * the blocks is concatenated; non-text blocks are ignored. Returns "" when absent or empty.
     */
    private static String systemPromptText(JsonNode systemNode) {
        if (systemNode == null || systemNode.isNull()) {
            return "";
        }
        if (systemNode.isTextual()) {
            return systemNode.asText("");
        }
        if (systemNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode block : systemNode) {
                if ("text".equals(block.path("type").asText(""))) {
                    builder.append(block.path("text").asText(""));
                }
            }
            return builder.toString();
        }
        return "";
    }

    private static ParsedMessage.Role mapRole(String rawRole) {
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
            default:
                return ParsedMessage.Role.USER;
        }
    }

    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input) {
        throw new UnsupportedOperationException("Anthropic does not expose an embeddings endpoint");
    }

    private static String randomBase62(int length) {
        String uuid = UUIDService.getNonSecureUUID().replace("-", "");
        // Pad if needed by appending another UUID
        while (uuid.length() < length) {
            uuid = uuid + UUIDService.getNonSecureUUID().replace("-", "");
        }
        return uuid.substring(0, length);
    }

    /**
     * Anthropic's {@code stop_reason} is one of {@code end_turn}, {@code max_tokens},
     * {@code stop_sequence}, {@code tool_use}, {@code pause_turn}, {@code refusal} and
     * {@code model_context_window_exceeded}. Other providers' values, from an expectation shared
     * across providers, are translated; native and unrecognised values pass through.
     */
    static String mapStopReason(String stopReason, boolean hasToolCalls) {
        if (stopReason == null) {
            return hasToolCalls ? "tool_use" : "end_turn";
        }
        switch (stopReason) {
            case "stop":
            case "STOP":
                return "end_turn";
            case "length":
            case "MAX_TOKENS":
                return "max_tokens";
            case "tool_calls":
            case "function_call":
                return "tool_use";
            case "content_filter":
            case "content_filtered":
            case "guardrail_intervened":
            case "SAFETY":
                return "refusal";
            default:
                return stopReason;
        }
    }

    private static String escapeJson(String value) {
        return JsonEscape.escape(value);
    }
}
