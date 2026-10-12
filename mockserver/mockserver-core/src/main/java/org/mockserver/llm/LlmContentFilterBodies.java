package org.mockserver.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.codec.BedrockCodec;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.LlmContentFilter;
import org.mockserver.model.Provider;
import org.mockserver.model.SseEvent;
import org.mockserver.model.StreamingPhysics;

import java.util.List;

/**
 * Pure, deterministic helper for content-filter / guardrail simulation across
 * providers. Two concerns, both provider-correct so an agent's filtering-detection
 * and refusal-handling logic can be exercised faithfully against a mock:
 *
 * <ul>
 *   <li><strong>Azure annotations</strong> ({@link #azureFilterResults}) — the
 *       {@code content_filter_results} / {@code prompt_filter_results} object Azure
 *       OpenAI attaches to every completion, one entry per harm category with a
 *       {@code filtered} flag and {@code severity}.</li>
 *   <li><strong>Content-filter block</strong> ({@link #blockFor}) — the response a
 *       provider returns when it refuses to answer on safety grounds. The shape is
 *       provider-specific: OpenAI-family → HTTP 400 with a {@code content_filter}
 *       error; Azure → HTTP 400 whose {@code innererror} carries the filter result;
 *       Anthropic and Bedrock InvokeModel → HTTP 200 with {@code stop_reason:"refusal"};
 *       Bedrock Converse → HTTP 200 with {@code stopReason:"content_filtered"}; Gemini →
 *       HTTP 200 with {@code finishReason:"SAFETY"}; otherwise a generic 400. A streaming
 *       request gets the HTTP 200 blocks as an event stream ({@link #streamedBlockOrNull}).</li>
 * </ul>
 *
 * <p>This is the content-filter counterpart to {@link LlmErrorBody} (overload /
 * rate-limit / server errors): content-filter is a distinct wire shape, so it lives
 * in a sibling helper rather than being shoehorned into {@link LlmErrorBody.Kind}.
 * All methods are static and pure — no clocks, no randomness, no shared state.
 */
public final class LlmContentFilterBodies {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** A default block filter (hate at {@code high}) used when no {@link LlmContentFilter} is supplied. */
    private static final LlmContentFilter DEFAULT_BLOCK_FILTER =
        LlmContentFilter.llmContentFilter().withHate(LlmContentFilter.HIGH);

    private LlmContentFilterBodies() {
    }

    /** Immutable (status, jsonBody) pair for a content-filter block response. */
    public static final class Block {
        private final int statusCode;
        private final String jsonBody;

        Block(int statusCode, String jsonBody) {
            this.statusCode = statusCode;
            this.jsonBody = jsonBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getJsonBody() {
            return jsonBody;
        }
    }

    /**
     * Build the Azure {@code content_filter_results} / {@code prompt_filter_results}
     * object for the four core harm categories. Each entry is
     * {@code {"filtered":bool,"severity":".."}}; a null {@code filter} yields an
     * all-{@code safe}, unfiltered object.
     */
    public static ObjectNode azureFilterResults(LlmContentFilter filter) {
        ObjectNode results = OBJECT_MAPPER.createObjectNode();
        addCategory(results, "hate", filter == null ? null : filter.getHate());
        addCategory(results, "self_harm", filter == null ? null : filter.getSelfHarm());
        addCategory(results, "sexual", filter == null ? null : filter.getSexual());
        addCategory(results, "violence", filter == null ? null : filter.getViolence());
        return results;
    }

    private static void addCategory(ObjectNode parent, String key, String severity) {
        ObjectNode category = parent.putObject(key);
        category.put("filtered", LlmContentFilter.isFilteredSeverity(severity));
        category.put("severity", LlmContentFilter.severityOrSafe(severity));
    }

    /** A Converse response whose output was removed by a content filter. */
    static final String BEDROCK_CONVERSE_CONTENT_FILTERED_BODY =
        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[]}},"
            + "\"stopReason\":\"content_filtered\","
            + "\"usage\":{\"inputTokens\":0,\"outputTokens\":0,\"totalTokens\":0},"
            + "\"metrics\":{\"latencyMs\":0}}";

    /**
     * As {@link #blockFor(Provider, LlmContentFilter)}, but picks the Bedrock wire API from the
     * request: a Converse request gets a Converse body, not an Anthropic one.
     */
    public static Block blockFor(Provider provider, LlmContentFilter filter, HttpRequest request) {
        if (provider == Provider.BEDROCK && BedrockCodec.isConverse(request)) {
            return new Block(200, BEDROCK_CONVERSE_CONTENT_FILTERED_BODY);
        }
        return blockFor(provider, filter);
    }

    /**
     * The provider-correct content-filter block response. {@code filter} shapes the
     * Azure {@code content_filter_result}; a null {@code filter} uses a sensible
     * default (hate at {@code high}).
     */
    public static Block blockFor(Provider provider, LlmContentFilter filter) {
        LlmContentFilter effective = filter != null ? filter : DEFAULT_BLOCK_FILTER;
        if (provider == null) {
            return genericBlock();
        }
        switch (provider) {
            case ANTHROPIC:
            case BEDROCK:
                return new Block(200, LlmRefusalPresets.ANTHROPIC_REFUSAL_BODY);
            case AZURE_OPENAI:
                return azureBlock(effective);
            case OPENAI:
            case OPENAI_RESPONSES:
            case MISTRAL:
            case XAI:
            case DEEPSEEK:
            case GROQ:
            case OPENROUTER:
            case ORCAROUTER:
                return openAiBlock();
            case GEMINI:
                return geminiBlock();
            default:
                return genericBlock();
        }
    }

    /**
     * The block as the provider's event stream, for a streaming request: real providers answer a
     * streamed request with HTTP 200 and a stream that ends with the filtered stop reason. Anthropic
     * and Bedrock (InvokeModel and Converse) stream an empty message whose stop reason is the
     * refusal, through {@code codec}; Gemini streams the block body as its only chunk. Returns
     * {@code null} for a block that is an HTTP 400, which providers send before any stream starts.
     */
    public static List<SseEvent> streamedBlockOrNull(Provider provider, Block block, ProviderCodec codec,
                                                     String model, StreamingPhysics physics, HttpRequest request) {
        if (provider == null || block == null || block.getStatusCode() != 200) {
            return null;
        }
        switch (provider) {
            case ANTHROPIC:
            case BEDROCK:
                if (codec == null) {
                    return null;
                }
                Completion refusal = Completion.completion()
                    .withStopReason(LlmRefusalPresets.ANTHROPIC_REFUSAL_STOP_REASON)
                    .withStreaming(true);
                return codec.encodeStreaming(refusal, model, physics, request);
            case GEMINI:
                return StreamingPhysicsExpander.applyPhysics(List.of(SseEvent.sseEvent().withData(block.getJsonBody())), physics);
            default:
                return null;
        }
    }

    private static Block openAiBlock() {
        // OpenAI returns HTTP 400 with an invalid_request_error whose code is
        // content_filter when a prompt (or generated content) trips the content policy.
        return new Block(400,
            "{\"error\":{\"message\":\"The response was filtered due to the prompt triggering "
                + "content management policy. Please modify your prompt and retry.\","
                + "\"type\":\"invalid_request_error\",\"param\":\"prompt\",\"code\":\"content_filter\"}}");
    }

    private static Block azureBlock(LlmContentFilter filter) {
        // Azure returns HTTP 400 whose innererror carries the ResponsibleAI policy
        // violation and the per-category content_filter_result.
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode error = root.putObject("error");
        error.put("message", "The response was filtered due to the prompt triggering Azure OpenAI's "
            + "content management policy. Please modify your prompt and retry.");
        error.putNull("type");
        error.put("param", "prompt");
        error.put("code", "content_filter");
        error.put("status", 400);
        ObjectNode innererror = error.putObject("innererror");
        innererror.put("code", "ResponsibleAIPolicyViolation");
        innererror.set("content_filter_result", azureFilterResults(filter));
        try {
            return new Block(400, OBJECT_MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            return genericBlock();
        }
    }

    private static Block geminiBlock() {
        // Gemini blocks with HTTP 200: candidates carry finishReason SAFETY and the
        // top-level promptFeedback carries blockReason SAFETY.
        return new Block(200,
            "{\"candidates\":[{\"finishReason\":\"SAFETY\",\"index\":0,"
                + "\"safetyRatings\":[{\"category\":\"HARM_CATEGORY_HATE_SPEECH\",\"probability\":\"HIGH\","
                + "\"blocked\":true}]}],\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
    }

    private static Block genericBlock() {
        return new Block(400,
            "{\"error\":{\"type\":\"content_filter\",\"message\":\"content blocked by content filter\"}}");
    }
}
