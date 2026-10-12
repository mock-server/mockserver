package org.mockserver.llm;

import org.mockserver.model.Provider;

/**
 * Pure, deterministic helper that produces the <strong>provider-specific</strong>
 * JSON error body real LLM providers return for overload / rate-limit / server
 * errors. Client SDKs (the OpenAI Python SDK, Anthropic SDK, Google GenAI SDK, …)
 * parse the {@code error.type} / {@code error.code} fields in these bodies to drive
 * their retry/backoff logic, so emitting the correct shape lets MockServer exercise
 * that logic faithfully against a mock.
 *
 * <p>Used by {@code HttpLlmResponseActionHandler} when injecting a chaos error
 * (probabilistic error or a stateful quota breach) on the LLM response path. When
 * the provider is {@code null} or unknown, callers fall back to the generic
 * {@code {"error":{"type":..,"message":..}}} body.
 *
 * <h3>Error kinds</h3>
 * The mapping is keyed by a coarse {@link Kind} derived from the HTTP status and the
 * caller's intent:
 * <ul>
 *   <li>{@link Kind#OVERLOADED} — the provider is temporarily overloaded
 *       (Anthropic 529 {@code overloaded_error}; OpenAI 503 {@code server_error}; …).</li>
 *   <li>{@link Kind#RATE_LIMIT} — a quota / rate limit was exceeded
 *       (429; OpenAI {@code rate_limit_exceeded}, Anthropic {@code rate_limit_error}, …).</li>
 *   <li>{@link Kind#SERVER_ERROR} — a generic upstream 5xx
 *       (OpenAI {@code server_error}, Anthropic {@code api_error}, …).</li>
 * </ul>
 *
 * <h3>Provider body reference</h3>
 * <ul>
 *   <li><strong>ANTHROPIC</strong> (Anthropic Messages API errors) —
 *       {@code {"type":"error","error":{"type":"overloaded_error"|"rate_limit_error"|"api_error","message":..}}}.</li>
 *   <li><strong>BEDROCK</strong> (AWS Bedrock Runtime errors, the same for InvokeModel and Converse) —
 *       {@code {"__type":"ThrottlingException"|"ServiceUnavailableException"|"InternalServerException"|..,"message":..}},
 *       the exception named by the status where Bedrock documents one.</li>
 *   <li><strong>OPENAI / OPENAI_RESPONSES / AZURE_OPENAI</strong> (OpenAI error envelope) —
 *       {@code {"error":{"message":..,"type":"server_error"|"rate_limit_exceeded","code":..,"param":null}}}.</li>
 *   <li><strong>GEMINI</strong> (Google API error envelope) —
 *       {@code {"error":{"code":..,"message":..,"status":"UNAVAILABLE"|"RESOURCE_EXHAUSTED"|"INTERNAL"}}}.</li>
 *   <li><strong>OLLAMA</strong> — {@code {"error":".."}} (a plain message string).</li>
 * </ul>
 *
 * <p>All output is a JSON string; the helper is static, deterministic, and performs
 * no I/O. Messages are short, fixed, benign test fixtures.
 */
public final class LlmErrorBodies {

    private LlmErrorBodies() {
    }

    public enum Kind {
        /** The provider is temporarily overloaded (e.g. Anthropic 529). */
        OVERLOADED,
        /** A rate limit / quota was exceeded (429). */
        RATE_LIMIT,
        /** A generic upstream server error (5xx). */
        SERVER_ERROR
    }

    /**
     * Derive the {@link Kind} from an HTTP status code. 429 → {@link Kind#RATE_LIMIT};
     * 529 → {@link Kind#OVERLOADED}; any other 5xx → {@link Kind#SERVER_ERROR}; any
     * 4xx (other than 429) → {@link Kind#SERVER_ERROR} (treated as a generic provider
     * error envelope). A {@code null} status defaults to {@link Kind#SERVER_ERROR}.
     */
    public static Kind kindForStatus(Integer status) {
        if (status == null) {
            return Kind.SERVER_ERROR;
        }
        if (status == 429) {
            return Kind.RATE_LIMIT;
        }
        if (status == 529) {
            return Kind.OVERLOADED;
        }
        return Kind.SERVER_ERROR;
    }

    /**
     * Produce the provider-specific JSON error body for the given provider and kind,
     * or {@code null} when the provider is {@code null} or has no specific shape (the
     * caller then uses the generic fallback body). The {@code message} is escaped
     * for embedding in a JSON string. {@code status} feeds the numeric {@code code}
     * field for providers (OpenAI Gemini) that echo the HTTP status in the body.
     *
     * @param provider the LLM provider (may be {@code null})
     * @param kind     the coarse error kind
     * @param status   the HTTP status being returned (used for body {@code code} fields)
     * @param message  a human-readable error message
     * @return the JSON error body string, or {@code null} for an unknown/null provider
     */
    public static String bodyFor(Provider provider, Kind kind, int status, String message) {
        if (provider == null) {
            return null;
        }
        String safeMessage = escape(message);
        switch (provider) {
            case ANTHROPIC:
                return anthropicBody(kind, safeMessage);
            case BEDROCK:
                return bedrockBody(kind, status, safeMessage);
            case OPENAI:
            case OPENAI_RESPONSES:
            case AZURE_OPENAI:
                return openAiBody(kind, status, safeMessage);
            case GEMINI:
                return geminiBody(kind, status, safeMessage);
            case OLLAMA:
                return ollamaBody(safeMessage);
            default:
                return null;
        }
    }

    private static String anthropicBody(Kind kind, String message) {
        String type;
        switch (kind) {
            case OVERLOADED:
                type = "overloaded_error";
                break;
            case RATE_LIMIT:
                type = "rate_limit_error";
                break;
            case SERVER_ERROR:
            default:
                type = "api_error";
                break;
        }
        return "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message + "\"}}";
    }

    /** Bedrock Runtime error names by HTTP status, from the InvokeModel and Converse API references. */
    private static String bedrockBody(Kind kind, int status, String message) {
        String type;
        switch (status) {
            case 400:
                type = "ValidationException";
                break;
            case 403:
                type = "AccessDeniedException";
                break;
            case 404:
                type = "ResourceNotFoundException";
                break;
            case 408:
                type = "ModelTimeoutException";
                break;
            case 424:
                type = "ModelErrorException";
                break;
            case 429:
                type = "ThrottlingException";
                break;
            case 503:
                type = "ServiceUnavailableException";
                break;
            default:
                type = kind == Kind.OVERLOADED ? "ServiceUnavailableException"
                    : kind == Kind.RATE_LIMIT ? "ThrottlingException" : "InternalServerException";
                break;
        }
        return "{\"__type\":\"" + type + "\",\"message\":\"" + message + "\"}";
    }

    private static String openAiBody(Kind kind, int status, String message) {
        String type;
        String code;
        switch (kind) {
            case RATE_LIMIT:
                type = "rate_limit_exceeded";
                code = "\"rate_limit_exceeded\"";
                break;
            case OVERLOADED:
            case SERVER_ERROR:
            default:
                type = "server_error";
                code = String.valueOf(status);
                break;
        }
        return "{\"error\":{\"message\":\"" + message + "\",\"type\":\"" + type + "\",\"param\":null,\"code\":" + code + "}}";
    }

    private static String geminiBody(Kind kind, int status, String message) {
        String googleStatus;
        switch (kind) {
            case OVERLOADED:
                googleStatus = "UNAVAILABLE";
                break;
            case RATE_LIMIT:
                googleStatus = "RESOURCE_EXHAUSTED";
                break;
            case SERVER_ERROR:
            default:
                googleStatus = "INTERNAL";
                break;
        }
        return "{\"error\":{\"code\":" + status + ",\"message\":\"" + message + "\",\"status\":\"" + googleStatus + "\"}}";
    }

    private static String ollamaBody(String message) {
        return "{\"error\":\"" + message + "\"}";
    }

    /**
     * Minimal JSON string escaping for the short, fixed messages used here
     * (quotes, backslashes, and control characters). Not a general-purpose JSON
     * encoder — sufficient for the benign literal messages produced by chaos.
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
