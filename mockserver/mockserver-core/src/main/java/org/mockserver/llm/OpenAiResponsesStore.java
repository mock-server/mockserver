package org.mockserver.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.SseEvent;
import org.mockserver.model.ToolUse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockserver.model.HttpResponse.response;

/**
 * Process-wide server-side state for the OpenAI Responses API
 * ({@code POST /v1/responses}). The Responses API is stateful in a way the Chat
 * Completions and Anthropic Messages APIs are not: a client may send only the new
 * turn's input plus a {@code previous_response_id}, and the server reconstructs the
 * full conversation from the stored prior turn. This registry models that so agents
 * that chain turns via {@code previous_response_id} can run against the mock.
 *
 * <p>Three behaviours, all opt-in-by-data and back-compatible (a request with no
 * {@code previous_response_id} and the default {@code store:true} behaves exactly as
 * before, only additionally recording the issued response for later chaining/retrieval):
 * <ul>
 *   <li><b>Chaining</b> — {@code OpenAiResponsesCodec.decode} prepends the stored prior
 *       conversation when the inbound request carries a {@code previous_response_id}, so
 *       conversation matchers and usage inference see the full dialogue.</li>
 *   <li><b>Store flag</b> — honours the {@code store} field (default {@code true}); a
 *       {@code store:false} response is neither retrievable nor chainable.</li>
 *   <li><b>Retrieval</b> — {@code GET /v1/responses/{id}} returns the stored response
 *       body verbatim.</li>
 * </ul>
 *
 * <p>Bounded (LRU) by count and by an estimated byte budget, which
 * {@link org.mockserver.mock.HttpState} sets from {@code maxOpenAiResponsesSizeInBytes}, so a
 * long-running proxy cannot grow it without limit; thread-safe, and cleared on
 * {@link org.mockserver.mock.HttpState#reset()} for test isolation.
 * Mirrors the singleton shape of {@link LlmQuotaRegistry}.
 */
public final class OpenAiResponsesStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Cap retained responses so an unbounded proxy stream cannot leak memory. LRU by
    // access order; the least recently used entry is evicted past either bound.
    static final int MAX_RESPONSES = 10_000;

    // Per-entry (record, map node, conversation list) and per-message costs on top of the
    // characters held, on the same basis as the expectation and event-log weighers.
    static final long ENTRY_OVERHEAD_BYTES = 256;
    static final long MESSAGE_OVERHEAD_BYTES = 128;

    // GET /v1/responses/{id} (and the codex-backend equivalent .../responses/{id}). The
    // id segment must be non-empty and not itself contain a slash/query/fragment.
    private static final Pattern RETRIEVAL_PATH = Pattern.compile("(?:^|/)responses/([^/?#]+)$");

    private static final OpenAiResponsesStore INSTANCE = new OpenAiResponsesStore(MAX_RESPONSES);

    private final Object lock = new Object();
    private final LinkedHashMap<String, StoredResponse> responses = new LinkedHashMap<>(64, 0.75f, true);
    private final int maxResponses;
    private long totalBytes;
    private long maxSizeInBytes;

    OpenAiResponsesStore(int maxResponses) {
        this.maxResponses = maxResponses;
    }

    public static OpenAiResponsesStore getInstance() {
        return INSTANCE;
    }

    /** An issued, stored Responses-API response: its id, the full conversation as of this turn, and the raw response body. */
    public static final class StoredResponse {
        private final String id;
        private final ParsedConversation conversation;
        private final String responseBodyJson;
        private long estimatedSize;

        public StoredResponse(String id, ParsedConversation conversation, String responseBodyJson) {
            this.id = id;
            this.conversation = conversation;
            this.responseBodyJson = responseBodyJson;
        }

        public String getId() {
            return id;
        }

        /** The full conversation (prior chained turns + this turn's input + this turn's assistant output). */
        public ParsedConversation getConversation() {
            return conversation;
        }

        public String getResponseBodyJson() {
            return responseBodyJson;
        }
    }

    public void put(StoredResponse record) {
        put(record, null);
    }

    /**
     * Stores {@code record}, charging the byte budget only for the messages it does not share with
     * {@code chainedFrom}: a chained turn's conversation reuses the prior turn's message objects.
     */
    private void put(StoredResponse record, StoredResponse chainedFrom) {
        if (record == null || record.getId() == null || record.getId().isEmpty()) {
            return;
        }
        record.estimatedSize = estimatedSize(record, chainedFrom);
        synchronized (lock) {
            StoredResponse replaced = responses.put(record.getId(), record);
            if (replaced != null) {
                totalBytes -= replaced.estimatedSize;
            }
            totalBytes += record.estimatedSize;
            evictOverBounds();
        }
    }

    public Optional<StoredResponse> get(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        synchronized (lock) {
            return Optional.ofNullable(responses.get(id));
        }
    }

    /**
     * Bounds the total estimated size of the stored responses; {@code 0} leaves only the count
     * bound. A lower budget evicts least recently used responses at once.
     */
    public void setMaxSizeInBytes(long maxSizeInBytes) {
        synchronized (lock) {
            this.maxSizeInBytes = Math.max(0L, maxSizeInBytes);
            evictOverBounds();
        }
    }

    public long getMaxSizeInBytes() {
        synchronized (lock) {
            return maxSizeInBytes;
        }
    }

    /** The estimated bytes held by the stored responses, as charged against the byte budget. */
    public long getTotalBytes() {
        synchronized (lock) {
            return totalBytes;
        }
    }

    public int size() {
        synchronized (lock) {
            return responses.size();
        }
    }

    /** Clear all stored responses. Called on server reset and for test isolation. */
    public void reset() {
        synchronized (lock) {
            responses.clear();
            totalBytes = 0;
        }
    }

    // The most recently stored response is kept even when it alone exceeds the byte budget,
    // so the turn a client chains from next is never the one evicted.
    private void evictOverBounds() {
        Iterator<StoredResponse> leastRecentlyUsed = responses.values().iterator();
        while (responses.size() > 1
            && (responses.size() > maxResponses || (maxSizeInBytes > 0 && totalBytes > maxSizeInBytes))) {
            totalBytes -= leastRecentlyUsed.next().estimatedSize;
            leastRecentlyUsed.remove();
        }
    }

    static long estimatedSize(StoredResponse record, StoredResponse chainedFrom) {
        long size = ENTRY_OVERHEAD_BYTES + length(record.getResponseBodyJson());
        if (record.getConversation() == null) {
            return size;
        }
        Set<ParsedMessage> shared = Collections.newSetFromMap(new IdentityHashMap<>());
        if (chainedFrom != null && chainedFrom.getConversation() != null) {
            shared.addAll(chainedFrom.getConversation().getMessages());
        }
        for (ParsedMessage message : record.getConversation().getMessages()) {
            size += Long.BYTES;
            if (message == null || shared.contains(message)) {
                continue;
            }
            size += MESSAGE_OVERHEAD_BYTES + length(message.getTextContent());
            for (ToolUse toolCall : message.getToolCalls()) {
                size += length(toolCall.getId()) + length(toolCall.getName()) + length(toolCall.getArguments());
            }
            for (Map.Entry<String, String> toolResult : message.getToolResults().entrySet()) {
                size += length(toolResult.getKey()) + length(toolResult.getValue());
            }
        }
        return size;
    }

    private static long length(String value) {
        return value != null ? value.length() : 0;
    }

    // --- chaining ------------------------------------------------------------

    /**
     * If {@code requestBody} carries a {@code previous_response_id} whose response is
     * stored, return the prior conversation's messages so the codec can prepend them to
     * the current turn's messages; otherwise an empty list. Fail-soft: an unparseable
     * body or unknown id yields an empty list (no chaining), never an error.
     */
    public List<ParsedMessage> priorMessagesFor(String requestBody) {
        String previousId = extractPreviousResponseId(requestBody);
        if (previousId == null) {
            return Collections.emptyList();
        }
        return get(previousId)
            .map(stored -> new ArrayList<>(stored.getConversation().getMessages()))
            .orElseGet(ArrayList::new);
    }

    private String extractPreviousResponseId(String requestBody) {
        if (requestBody == null || requestBody.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(requestBody);
            if (root != null && root.hasNonNull("previous_response_id")) {
                String id = root.get("previous_response_id").asText("");
                return id.isEmpty() ? null : id;
            }
        } catch (Exception ignored) {
            // fail-soft: not parseable → no chaining
        }
        return null;
    }

    // --- registration --------------------------------------------------------

    /**
     * Record the response just issued for a {@code POST /v1/responses} turn so it can be
     * chained (via {@code previous_response_id}) or retrieved (via {@code GET
     * /v1/responses/{id}}). No-op unless the request opts in to storage
     * ({@code store} defaults to {@code true}; {@code store:false} skips it).
     *
     * <p>The stored conversation is the fully-chained decode of this request (which, when
     * the request carried a {@code previous_response_id}, already includes the prior
     * turns) plus this turn's assistant output — so a subsequent turn referencing this
     * id reconstructs the entire dialogue. Fail-soft: any parsing error simply skips
     * registration and never affects the served response.
     *
     * @param chainedConversation the codec's decode of this request (already chained)
     * @param completion          the assistant completion that was encoded
     * @param encodedBody         the response body that was returned to the client
     */
    public void recordIfStored(String requestBody, ParsedConversation chainedConversation,
                               Completion completion, String encodedBody) {
        try {
            if (!isStoreEnabled(requestBody)) {
                return;
            }
            String id = extractResponseId(encodedBody);
            if (id == null) {
                return;
            }
            List<ParsedMessage> messages = new ArrayList<>();
            if (chainedConversation != null) {
                messages.addAll(chainedConversation.getMessages());
                dropLeadingInstructions(requestBody, messages);
            }
            messages.add(assistantMessage(completion, encodedBody));
            String previousId = extractPreviousResponseId(requestBody);
            put(new StoredResponse(id, ParsedConversation.of(messages), encodedBody),
                previousId != null ? get(previousId).orElse(null) : null);
        } catch (Exception ignored) {
            // fail-soft: state recording must never affect the served response
        }
    }

    /**
     * As {@link #recordIfStored} for a streamed turn: the stored body is the response object carried
     * by the stream's {@code response.completed} event, which is what {@code GET /v1/responses/{id}}
     * returns for it. No-op when the events carry no such event.
     */
    public void recordStreamedIfStored(String requestBody, ParsedConversation chainedConversation,
                                       Completion completion, List<SseEvent> events) {
        try {
            if (events == null) {
                return;
            }
            // response.completed ends the stream, so search from the end and parse only candidates
            for (int i = events.size() - 1; i >= 0; i--) {
                SseEvent event = events.get(i);
                if (event == null || event.getData() == null || !event.getData().contains("response.completed")) {
                    continue;
                }
                JsonNode data = OBJECT_MAPPER.readTree(event.getData());
                if (data != null && "response.completed".equals(data.path("type").asText(""))
                    && data.path("response").isObject()) {
                    recordIfStored(requestBody, chainedConversation, completion,
                        OBJECT_MAPPER.writeValueAsString(data.get("response")));
                    return;
                }
            }
        } catch (Exception ignored) {
            // fail-soft: state recording must never affect the served stream
        }
    }

    /** The {@code store} flag from the request body — default {@code true} when absent/unparseable. */
    boolean isStoreEnabled(String requestBody) {
        if (requestBody == null || requestBody.isEmpty()) {
            return true;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(requestBody);
            if (root != null && root.has("store") && root.get("store").isBoolean()) {
                return root.get("store").asBoolean();
            }
        } catch (Exception ignored) {
            // fail-soft: default to storing
        }
        return true;
    }

    private String extractResponseId(String encodedBody) {
        if (encodedBody == null || encodedBody.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(encodedBody);
            if (root != null && root.hasNonNull("id")) {
                String id = root.get("id").asText("");
                return id.isEmpty() ? null : id;
            }
        } catch (Exception ignored) {
            // fail-soft
        }
        return null;
    }

    /**
     * The codec decodes a request's {@code instructions} as a leading SYSTEM message, but the
     * real API does not carry instructions over to a chained turn, so they are not stored.
     */
    private static void dropLeadingInstructions(String requestBody, List<ParsedMessage> messages) {
        if (messages.isEmpty() || messages.get(0).getRole() != ParsedMessage.Role.SYSTEM) {
            return;
        }
        try {
            JsonNode instructions = OBJECT_MAPPER.readTree(requestBody).get("instructions");
            if (instructions != null && instructions.isTextual()
                && instructions.asText("").equals(messages.get(0).getTextContent())) {
                messages.remove(0);
            }
        } catch (Exception ignored) {
            // fail-soft: keep the conversation as decoded
        }
    }

    /**
     * This turn's assistant message. Tool calls carry the {@code call_id} issued in the encoded
     * body, so a chained turn's {@code function_call_output} correlates to them.
     */
    private static ParsedMessage assistantMessage(Completion completion, String encodedBody) {
        String text = completion != null && completion.getText() != null ? completion.getText() : "";
        List<ToolUse> toolCalls = completion != null ? completion.getToolCalls() : null;
        List<ToolUse> issued = issuedToolCalls(encodedBody);
        return new ParsedMessage(ParsedMessage.Role.ASSISTANT, text, issued.isEmpty() ? toolCalls : issued, null);
    }

    private static List<ToolUse> issuedToolCalls(String encodedBody) {
        List<ToolUse> toolCalls = new ArrayList<>();
        try {
            for (JsonNode item : OBJECT_MAPPER.readTree(encodedBody).path("output")) {
                if ("function_call".equals(item.path("type").asText(""))) {
                    ToolUse toolUse = ToolUse.toolUse(item.path("name").asText(""))
                        .withArguments(item.path("arguments").asText("{}"));
                    String callId = item.path("call_id").asText("");
                    if (!callId.isEmpty()) {
                        toolUse.withId(callId);
                    }
                    toolCalls.add(toolUse);
                }
            }
        } catch (Exception ignored) {
            // fail-soft: fall back to the completion's tool calls
        }
        return toolCalls;
    }

    // --- retrieval -----------------------------------------------------------

    /**
     * If {@code request} is a {@code GET /v1/responses/{id}} whose id is stored, return
     * the stored response body as a {@code 200 application/json} response; otherwise
     * {@code null} so the caller falls through to normal handling. Returning {@code null}
     * for an unknown id keeps the mock non-intrusive — a user-configured expectation or
     * the standard 404 path still applies.
     */
    public HttpResponse retrievalResponseOrNull(HttpRequest request) {
        if (request == null || request.getMethod() == null) {
            return null;
        }
        if (!"GET".equalsIgnoreCase(request.getMethod().getValue())) {
            return null;
        }
        String path = request.getPath() != null ? request.getPath().getValue() : null;
        if (path == null) {
            return null;
        }
        Matcher matcher = RETRIEVAL_PATH.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        String id = matcher.group(1);
        Optional<StoredResponse> stored = get(id);
        if (stored.isEmpty()) {
            return null;
        }
        return response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withBody(stored.get().getResponseBodyJson());
    }
}
