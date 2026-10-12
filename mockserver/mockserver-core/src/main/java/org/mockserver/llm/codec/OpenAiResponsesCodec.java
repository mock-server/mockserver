package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.OpenAiResponsesStore;
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
import java.util.Locale;
import java.util.Map;

import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.SseEvent.sseEvent;

/**
 * Codec for OpenAI Responses API (version 2025-03).
 * The Responses API uses an {@code output} array of blocks (text, function_call)
 * instead of the Chat Completions {@code choices} array.
 * <p>
 * Streaming uses named SSE events (e.g. {@code response.created},
 * {@code response.output_text.delta}, {@code response.completed}).
 */
public class OpenAiResponsesCodec implements ProviderCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Provider provider() {
        return Provider.OPENAI_RESPONSES;
    }

    @Override
    public String apiVersion() {
        return "2025-03";
    }

    @Override
    public HttpResponse encode(Completion completion, String model) {
        return encode(completion, model, (JsonNode) null);
    }

    @Override
    public HttpResponse encode(Completion completion, String model, HttpRequest request) {
        return encode(completion, model, requestBody(request));
    }

    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics) {
        return encodeStreaming(completion, model, physics, (JsonNode) null);
    }

    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics, HttpRequest request) {
        return encodeStreaming(completion, model, physics, requestBody(request));
    }

    private static JsonNode requestBody(HttpRequest request) {
        try {
            String body = request != null ? request.getBodyAsText() : null;
            JsonNode root = body != null && !body.isEmpty() ? OBJECT_MAPPER.readTree(body) : null;
            return root != null && root.isObject() ? root : null;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpResponse encode(Completion completion, String model, JsonNode requestBody) {
        String modelName = model != null ? model : "unknown";
        long createdAt = System.currentTimeMillis() / 1000;
        ArrayNode output = OBJECT_MAPPER.createArrayNode();
        for (OutputItem item : outputItems(completion)) {
            output.add(item.completed());
        }
        ObjectNode root = responseObject(responseId(), createdAt, modelName, "completed",
            output, usageNode(completion.getUsage()), completion.getToolChoice(), requestBody);
        return response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withBody(toJson(root));
    }

    private List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics, JsonNode requestBody) {
        String responseId = responseId();
        String modelName = model != null ? model : "unknown";
        long createdAt = System.currentTimeMillis() / 1000;
        String toolChoice = completion.getToolChoice();
        EventSink sink = new EventSink();

        ObjectNode created = sink.event("response.created");
        created.set("response", responseObject(responseId, createdAt, modelName, "in_progress",
            OBJECT_MAPPER.createArrayNode(), null, toolChoice, requestBody));
        sink.emit(created);
        ObjectNode inProgress = sink.event("response.in_progress");
        inProgress.set("response", responseObject(responseId, createdAt, modelName, "in_progress",
            OBJECT_MAPPER.createArrayNode(), null, toolChoice, requestBody));
        sink.emit(inProgress);

        ArrayNode output = OBJECT_MAPPER.createArrayNode();
        int outputIndex = 0;
        for (OutputItem item : outputItems(completion)) {
            ObjectNode added = sink.event("response.output_item.added");
            added.put("output_index", outputIndex);
            added.set("item", item.inProgress());
            sink.emit(added);
            item.streamBody(sink, outputIndex, physics);
            ObjectNode done = sink.event("response.output_item.done");
            done.put("output_index", outputIndex);
            done.set("item", item.completed());
            sink.emit(done);
            output.add(item.completed());
            outputIndex++;
        }

        ObjectNode completed = sink.event("response.completed");
        completed.set("response", responseObject(responseId, createdAt, modelName, "completed",
            output, usageNode(completion.getUsage()), toolChoice, requestBody));
        sink.emit(completed);

        return StreamingPhysicsExpander.applyPhysics(sink.events, physics);
    }

    /**
     * The Response object as the real API returns it on the non-streaming body and on the
     * response.created / in_progress / completed events; the Agents SDK reads its final turn
     * from response.completed's output. Request fields the real API echoes are copied from the
     * request body when there is one; the spec's required nullable fields are null otherwise.
     */
    private static ObjectNode responseObject(String id, long createdAt, String model, String status,
                                             ArrayNode output, ObjectNode usage, String toolChoice,
                                             JsonNode requestBody) {
        JsonNode echo = requestBody != null ? requestBody : OBJECT_MAPPER.createObjectNode();
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("id", id);
        root.put("object", "response");
        root.putNull("access_programs");
        root.put("created_at", createdAt);
        root.put("status", status);
        if ("completed".equals(status)) {
            root.put("completed_at", createdAt);
        }
        root.putNull("error");
        root.putNull("incomplete_details");
        root.set("instructions", echo.path("instructions").isTextual() ? echo.get("instructions") : NullNode.getInstance());
        root.put("model", model);
        root.set("output", output);
        root.put("parallel_tool_calls", !echo.path("parallel_tool_calls").isBoolean() || echo.get("parallel_tool_calls").booleanValue());
        root.set("previous_response_id", echo.path("previous_response_id").isTextual() ? echo.get("previous_response_id") : NullNode.getInstance());
        boolean echoToolChoice = (toolChoice == null || toolChoice.trim().isEmpty())
            && (echo.path("tool_choice").isTextual() || echo.path("tool_choice").isObject());
        root.set("tool_choice", echoToolChoice ? echo.get("tool_choice").deepCopy() : toolChoiceNode(toolChoice));
        root.set("tools", echo.path("tools").isArray() ? echo.get("tools").deepCopy() : OBJECT_MAPPER.createArrayNode());
        root.set("temperature", echo.path("temperature").isNumber() ? echo.get("temperature") : NullNode.getInstance());
        root.set("top_p", echo.path("top_p").isNumber() ? echo.get("top_p") : NullNode.getInstance());
        if (usage != null) {
            root.set("usage", usage);
        } else {
            root.putNull("usage");
        }
        root.set("metadata", echo.path("metadata").isObject() ? echo.get("metadata").deepCopy() : OBJECT_MAPPER.createObjectNode());
        return root;
    }

    private static JsonNode toolChoiceNode(String toolChoice) {
        if (toolChoice == null || toolChoice.trim().isEmpty()) {
            return OBJECT_MAPPER.getNodeFactory().textNode("auto");
        }
        String trimmed = toolChoice.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("auto".equals(lower) || "none".equals(lower) || "required".equals(lower)) {
            return OBJECT_MAPPER.getNodeFactory().textNode(lower);
        }
        ObjectNode named = OBJECT_MAPPER.createObjectNode();
        named.put("type", "function");
        named.put("name", trimmed);
        return named;
    }

    private static ObjectNode usageNode(Usage completionUsage) {
        int inputTokens = intOrZero(completionUsage != null ? completionUsage.getInputTokens() : null);
        int outputTokens = intOrZero(completionUsage != null ? completionUsage.getOutputTokens() : null);
        ObjectNode usage = OBJECT_MAPPER.createObjectNode();
        usage.put("input_tokens", inputTokens);
        ObjectNode inputDetails = usage.putObject("input_tokens_details");
        inputDetails.put("cached_tokens", intOrZero(completionUsage != null ? completionUsage.getCachedInputTokens() : null));
        inputDetails.put("cache_write_tokens", intOrZero(completionUsage != null ? completionUsage.getCacheCreationTokens() : null));
        usage.put("output_tokens", outputTokens);
        usage.putObject("output_tokens_details")
            .put("reasoning_tokens", intOrZero(completionUsage != null ? completionUsage.getReasoningTokens() : null));
        usage.put("total_tokens", inputTokens + outputTokens);
        return usage;
    }

    private static int intOrZero(Integer value) {
        return value != null ? value : 0;
    }

    /** Output items in API order: optional reasoning, then the assistant message, then one per tool call. */
    private static List<OutputItem> outputItems(Completion completion) {
        List<OutputItem> items = new ArrayList<>();
        String reasoningText = completion.getReasoningText();
        if (reasoningText != null && !reasoningText.isEmpty()) {
            items.add(new ReasoningItem("rs_" + randomId(24), reasoningText));
        }
        String text = completion.getText();
        if (text != null && !text.isEmpty()) {
            items.add(new MessageItem("msg_" + randomId(24), text));
        }
        List<ToolUse> toolCalls = completion.getToolCalls();
        if (toolCalls != null) {
            for (ToolUse toolCall : toolCalls) {
                // call_id is what a client echoes back in function_call_output; id is the item id.
                // A configured ToolUse id becomes the call_id so a test can answer it by a known value.
                String callId = toolCall.getId() != null && !toolCall.getId().isEmpty() ? toolCall.getId() : "call_" + randomId(24);
                items.add(new FunctionCallItem("fc_" + randomId(24), callId,
                    toolCall.getName(), toolCall.getArguments() != null ? toolCall.getArguments() : "{}"));
            }
        }
        return items;
    }

    /** Assigns every streamed event its type and a sequence_number counting from 0. */
    private static final class EventSink {
        private final List<SseEvent> events = new ArrayList<>();
        private int sequenceNumber;

        ObjectNode event(String type) {
            ObjectNode data = OBJECT_MAPPER.createObjectNode();
            data.put("type", type);
            return data;
        }

        void emit(ObjectNode data) {
            data.put("sequence_number", sequenceNumber++);
            events.add(sseEvent().withEvent(data.get("type").asText()).withData(toJson(data)));
        }
    }

    private abstract static class OutputItem {
        final String id;

        OutputItem(String id) {
            this.id = id;
        }

        abstract ObjectNode inProgress();

        abstract ObjectNode completed();

        abstract void streamBody(EventSink sink, int outputIndex, StreamingPhysics physics);

        ObjectNode itemEvent(EventSink sink, String type, int outputIndex) {
            ObjectNode data = sink.event(type);
            data.put("item_id", id);
            data.put("output_index", outputIndex);
            return data;
        }
    }

    private static final class ReasoningItem extends OutputItem {
        private final String text;

        ReasoningItem(String id, String text) {
            super(id);
            this.text = text;
        }

        @Override
        ObjectNode inProgress() {
            ObjectNode item = OBJECT_MAPPER.createObjectNode();
            item.put("id", id);
            item.put("type", "reasoning");
            item.putArray("summary");
            return item;
        }

        @Override
        ObjectNode completed() {
            ObjectNode item = inProgress();
            ((ArrayNode) item.get("summary")).add(summaryPart(text));
            return item;
        }

        @Override
        void streamBody(EventSink sink, int outputIndex, StreamingPhysics physics) {
            ObjectNode partAdded = itemEvent(sink, "response.reasoning_summary_part.added", outputIndex);
            partAdded.put("summary_index", 0);
            partAdded.set("part", summaryPart(""));
            sink.emit(partAdded);
            ObjectNode delta = itemEvent(sink, "response.reasoning_summary_text.delta", outputIndex);
            delta.put("summary_index", 0);
            delta.put("delta", text);
            sink.emit(delta);
            ObjectNode textDone = itemEvent(sink, "response.reasoning_summary_text.done", outputIndex);
            textDone.put("summary_index", 0);
            textDone.put("text", text);
            sink.emit(textDone);
            ObjectNode partDone = itemEvent(sink, "response.reasoning_summary_part.done", outputIndex);
            partDone.put("summary_index", 0);
            partDone.set("part", summaryPart(text));
            sink.emit(partDone);
        }

        private static ObjectNode summaryPart(String text) {
            ObjectNode part = OBJECT_MAPPER.createObjectNode();
            part.put("type", "summary_text");
            part.put("text", text);
            return part;
        }
    }

    private static final class MessageItem extends OutputItem {
        private final String text;

        MessageItem(String id, String text) {
            super(id);
            this.text = text;
        }

        @Override
        ObjectNode inProgress() {
            return message("in_progress");
        }

        @Override
        ObjectNode completed() {
            ObjectNode item = message("completed");
            ((ArrayNode) item.get("content")).add(outputText(text));
            return item;
        }

        private ObjectNode message(String status) {
            ObjectNode item = OBJECT_MAPPER.createObjectNode();
            item.put("id", id);
            item.put("type", "message");
            item.put("status", status);
            item.put("role", "assistant");
            item.putArray("content");
            return item;
        }

        @Override
        void streamBody(EventSink sink, int outputIndex, StreamingPhysics physics) {
            ObjectNode partAdded = itemEvent(sink, "response.content_part.added", outputIndex);
            partAdded.put("content_index", 0);
            partAdded.set("part", outputText(""));
            sink.emit(partAdded);
            for (String token : TokenCounter.streamingTextTokens(text, physics)) {
                if (!token.isEmpty()) {
                    ObjectNode delta = itemEvent(sink, "response.output_text.delta", outputIndex);
                    delta.put("content_index", 0);
                    delta.put("delta", token);
                    delta.putArray("logprobs");
                    sink.emit(delta);
                }
            }
            ObjectNode textDone = itemEvent(sink, "response.output_text.done", outputIndex);
            textDone.put("content_index", 0);
            textDone.put("text", text);
            textDone.putArray("logprobs");
            sink.emit(textDone);
            ObjectNode partDone = itemEvent(sink, "response.content_part.done", outputIndex);
            partDone.put("content_index", 0);
            partDone.set("part", outputText(text));
            sink.emit(partDone);
        }

        private static ObjectNode outputText(String text) {
            ObjectNode part = OBJECT_MAPPER.createObjectNode();
            part.put("type", "output_text");
            part.put("text", text);
            part.putArray("annotations");
            part.putArray("logprobs");
            return part;
        }
    }

    private static final class FunctionCallItem extends OutputItem {
        private final String callId;
        private final String name;
        private final String arguments;

        FunctionCallItem(String id, String callId, String name, String arguments) {
            super(id);
            this.callId = callId;
            this.name = name;
            this.arguments = arguments;
        }

        @Override
        ObjectNode inProgress() {
            return functionCall("in_progress", "");
        }

        @Override
        ObjectNode completed() {
            return functionCall("completed", arguments);
        }

        private ObjectNode functionCall(String status, String args) {
            ObjectNode item = OBJECT_MAPPER.createObjectNode();
            item.put("id", id);
            item.put("type", "function_call");
            item.put("status", status);
            item.put("arguments", args);
            item.put("call_id", callId);
            item.put("name", name);
            return item;
        }

        @Override
        void streamBody(EventSink sink, int outputIndex, StreamingPhysics physics) {
            for (String chunk : TokenCounter.streamingTextTokens(arguments, physics)) {
                ObjectNode delta = itemEvent(sink, "response.function_call_arguments.delta", outputIndex);
                delta.put("delta", chunk);
                sink.emit(delta);
            }
            ObjectNode done = itemEvent(sink, "response.function_call_arguments.done", outputIndex);
            done.put("arguments", arguments);
            sink.emit(done);
        }
    }

    private static String toJson(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode OpenAI Responses response", e);
        }
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

            JsonNode inputNode = root.get("input");
            if (inputNode == null) {
                return ParsedConversation.empty();
            }

            List<ParsedMessage> parsed = new ArrayList<>();
            ParsedMessage instructions = instructionsMessage(root);

            if (inputNode.isTextual()) {
                // Single string input treated as user message
                parsed.add(new ParsedMessage(
                    ParsedMessage.Role.USER,
                    inputNode.asText(""),
                    null,
                    null
                ));
            } else if (inputNode.isArray()) {
                for (JsonNode item : inputNode) {
                    String type = item.has("type") ? item.get("type").asText("") : "";

                    if ("function_call_output".equals(type)) {
                        // Tool result
                        String callId = item.has("call_id") ? item.get("call_id").asText("") : "";
                        String output = item.has("output") ? item.get("output").asText("") : "";
                        Map<String, String> toolResults = new LinkedHashMap<>();
                        toolResults.put(callId, output);
                        parsed.add(new ParsedMessage(
                            ParsedMessage.Role.TOOL,
                            output,
                            null,
                            toolResults
                        ));
                    } else if ("function_call".equals(type)) {
                        // Tool call from prior response (assistant turn). function_call_output
                        // references it by call_id, not by the fc_ item id; the id fallback keeps
                        // inputs that omit call_id correlatable.
                        String callId = item.path("call_id").asText("");
                        if (callId.isEmpty()) {
                            callId = item.path("id").asText("");
                        }
                        String name = item.has("name") ? item.get("name").asText("") : "";
                        String arguments = item.has("arguments") ? item.get("arguments").asText("") : "{}";
                        ToolUse tu = ToolUse.toolUse(name).withArguments(arguments);
                        if (!callId.isEmpty()) {
                            tu.withId(callId);
                        }
                        List<ToolUse> toolCalls = new ArrayList<>();
                        toolCalls.add(tu);
                        parsed.add(new ParsedMessage(
                            ParsedMessage.Role.ASSISTANT,
                            "",
                            toolCalls,
                            null
                        ));
                    } else {
                        // Standard message
                        String rawRole = item.has("role") ? item.get("role").asText("") : "user";
                        String textContent = "";
                        JsonNode contentNode = item.get("content");
                        if (contentNode != null) {
                            if (contentNode.isTextual()) {
                                textContent = contentNode.asText("");
                            } else if (contentNode.isArray()) {
                                StringBuilder sb = new StringBuilder();
                                for (JsonNode part : contentNode) {
                                    String partType = part.has("type") ? part.get("type").asText("") : "";
                                    if ("output_text".equals(partType) || "input_text".equals(partType) || "text".equals(partType)) {
                                        sb.append(part.has("text") ? part.get("text").asText("") : "");
                                    }
                                }
                                textContent = sb.toString();
                            }
                        }

                        ParsedMessage.Role role = mapRole(rawRole);
                        parsed.add(new ParsedMessage(role, textContent, null, null));
                    }
                }
            } else {
                return ParsedConversation.empty();
            }

            // Server-side state chaining: when the request carries a previous_response_id,
            // prepend the stored prior conversation so matchers and usage inference see the
            // full dialogue (the Responses API sends only the new turn's input plus the id).
            // No-op when there is no previous_response_id or the id is unknown/not stored.
            List<ParsedMessage> priorMessages =
                OpenAiResponsesStore.getInstance().priorMessagesFor(body);
            List<ParsedMessage> conversation = new ArrayList<>();
            if (instructions != null) {
                conversation.add(instructions);
            }
            conversation.addAll(priorMessages);
            conversation.addAll(parsed);
            return ParsedConversation.of(conversation);
        } catch (Exception e) {
            return ParsedConversation.empty();
        }
    }

    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input) {
        throw new UnsupportedOperationException("OpenAI Responses API does not expose an embeddings endpoint");
    }

    /**
     * The request's top-level {@code instructions} as a leading SYSTEM message, or null when
     * absent. Instructions apply to this request only and are not carried over by
     * {@code previous_response_id}, so {@link OpenAiResponsesStore} does not store it.
     */
    static ParsedMessage instructionsMessage(JsonNode root) {
        JsonNode instructions = root.get("instructions");
        if (instructions == null || !instructions.isTextual() || instructions.asText("").isEmpty()) {
            return null;
        }
        return new ParsedMessage(ParsedMessage.Role.SYSTEM, instructions.asText(""), null, null);
    }

    private static ParsedMessage.Role mapRole(String rawRole) {
        if (rawRole == null) {
            return ParsedMessage.Role.USER;
        }
        switch (rawRole.toLowerCase(Locale.ROOT)) {
            case "assistant":
                return ParsedMessage.Role.ASSISTANT;
            case "user":
                return ParsedMessage.Role.USER;
            case "system":
            case "developer":
                return ParsedMessage.Role.SYSTEM;
            case "tool":
                return ParsedMessage.Role.TOOL;
            default:
                return ParsedMessage.Role.USER;
        }
    }

    /**
     * A resp_ id is the only guard on GET /v1/responses/{id}, which returns the request's echoed
     * instructions, tools and metadata, so it comes from the secure generator.
     */
    private static String responseId() {
        return "resp_" + UUIDService.getUUID().replace("-", "").substring(0, 24);
    }

    private static String randomId(int length) {
        String uuid = UUIDService.getNonSecureUUID().replace("-", "");
        while (uuid.length() < length) {
            uuid = uuid + UUIDService.getNonSecureUUID().replace("-", "");
        }
        return uuid.substring(0, length);
    }

}
