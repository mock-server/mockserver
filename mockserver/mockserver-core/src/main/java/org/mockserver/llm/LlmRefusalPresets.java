package org.mockserver.llm;

import org.mockserver.llm.codec.AnthropicCodec;
import org.mockserver.model.Completion;

/**
 * Presets that emit provider-style <em>refusals</em> — the response an aligned
 * model returns when it declines to answer on safety grounds. Mocking a refusal
 * lets an agent's refusal-handling path (retry with a softer prompt, surface a
 * user-facing message, fall back to a tool) be exercised deterministically.
 *
 * <p>The flagship is Anthropic's refusal shape: a normal HTTP 200 message whose
 * {@code stop_reason} is {@code "refusal"} (introduced alongside Claude's
 * safety-refusal stop reason) rather than {@code end_turn}. The
 * {@link #anthropicRefusal()} preset builds a {@link Completion} carrying that stop
 * reason, which the Anthropic codec encodes into the wire body; the raw wire body
 * is also exposed as {@link #ANTHROPIC_REFUSAL_BODY} for the chaos content-filter
 * block path.
 *
 * <p>All factories are static and pure; each call returns a fresh {@link Completion}.
 */
public final class LlmRefusalPresets {

    /** The Anthropic {@code stop_reason} value signalling a safety refusal. */
    public static final String ANTHROPIC_REFUSAL_STOP_REASON = "refusal";

    /**
     * A fixed Anthropic refusal wire body (empty content, {@code stop_reason:"refusal"} and its
     * {@code stop_details}),
     * used by {@link LlmContentFilterBodies} for a content-filter block. Deterministic
     * test fixture — the id is fixed rather than random.
     */
    public static final String ANTHROPIC_REFUSAL_BODY = refusalBody(",\"diagnostics\":null");

    /** As {@link #ANTHROPIC_REFUSAL_BODY} without {@code diagnostics}, which Bedrock InvokeModel does not send. */
    public static final String BEDROCK_INVOKE_REFUSAL_BODY = refusalBody("");

    private LlmRefusalPresets() {
    }

    private static String refusalBody(String diagnostics) {
        return "{\"id\":\"msg_refusal\",\"type\":\"message\",\"role\":\"assistant\","
            + "\"model\":\"claude\",\"content\":[],\"container\":null,\"stop_reason\":\"refusal\","
            + "\"stop_sequence\":null,\"stop_details\":" + AnthropicCodec.REFUSAL_STOP_DETAILS + ","
            + "\"usage\":{\"input_tokens\":0,\"output_tokens\":0}" + diagnostics + "}";
    }

    /**
     * An Anthropic-style refusal: a completion with {@code stop_reason:"refusal"} and
     * no text content (matching Anthropic's empty-content refusal), which the
     * Anthropic codec encodes as an HTTP 200 refusal message.
     */
    public static Completion anthropicRefusal() {
        return Completion.completion().withStopReason(ANTHROPIC_REFUSAL_STOP_REASON);
    }

    /**
     * An Anthropic-style refusal carrying a short refusal message as its text content
     * (some refusals include a brief explanation), still with {@code stop_reason:"refusal"}.
     */
    public static Completion anthropicRefusal(String message) {
        return Completion.completion()
            .withStopReason(ANTHROPIC_REFUSAL_STOP_REASON)
            .withText(message);
    }
}
