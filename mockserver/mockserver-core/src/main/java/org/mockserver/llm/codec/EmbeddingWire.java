package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.model.EmbeddingResponse;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import static org.mockserver.model.HttpResponse.response;

/**
 * Request parsing and vector formatting shared by the provider embedding codecs. Each codec
 * reads its own provider's input field(s) with these helpers and returns one vector per input;
 * every vector comes from {@link EmbeddingVectors}, so the same text, seed and dimensions give
 * the same vector whichever provider shape wraps it.
 */
public final class EmbeddingWire {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EmbeddingWire() {
    }

    /** The request body as JSON, or an empty object when it is absent or not JSON. */
    public static JsonNode body(HttpRequest request) {
        if (request != null && request.getBody() != null) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(request.getBodyAsText());
                if (node != null && node.isObject()) {
                    return node;
                }
            } catch (Exception e) {
                // not JSON: treated as an empty request
            }
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    /** The lower-cased request path, or an empty string. */
    public static String path(HttpRequest request) {
        if (request == null || request.getPath() == null || request.getPath().getValue() == null) {
            return "";
        }
        return request.getPath().getValue().toLowerCase(Locale.ROOT);
    }

    /**
     * The single text a codec without its own request parsing embeds: a top-level
     * {@code input} string, or the JSON text of a non-string {@code input}.
     */
    public static String legacyInputText(HttpRequest request) {
        JsonNode input = body(request).get("input");
        if (input == null || input.isNull()) {
            return "";
        }
        return input.isTextual() ? input.asText() : input.toString();
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value != null && value.isTextual() && !value.asText().isEmpty() ? value.asText() : null;
    }

    /** The largest vector length a request may ask for. */
    public static final int MAX_REQUESTED_DIMENSIONS = 8192;

    /** The most inputs one request may embed (OpenAI's limit). */
    public static final int MAX_INPUTS = 2048;

    /**
     * The default for the most values one response may hold as JSON numbers, across every input
     * and type ({@code llmEmbeddingMaxResponseValues}). It keeps the largest response body under
     * the default {@code maxRequestBodySize}, the largest body an expectation can be given by
     * default. Base64 float32 vectors take a quarter of the characters, so four times as many
     * values are allowed in that form.
     */
    public static final int DEFAULT_MAX_TOTAL_VALUES = 262_144;

    /**
     * The default for the longest input text, in characters, that a {@code deterministicFromInput}
     * embedding hashes ({@code llmEmbeddingMaxInputCharacters}). Hashing allocates in proportion
     * to the text, so this bounds the memory one input costs. It is above the largest input a real
     * provider accepts (Cohere Embed v4's 128k tokens at 8 characters a token), so no request a
     * provider would take is rejected.
     */
    public static final int DEFAULT_MAX_INPUT_CHARACTERS = 1_048_576;

    /** The limits one request is checked against. */
    public static final class Limits {
        private final long maxTotalValues;
        private final int maxInputCharacters;

        public Limits(long maxTotalValues, int maxInputCharacters) {
            this.maxTotalValues = maxTotalValues;
            this.maxInputCharacters = maxInputCharacters;
        }
    }

    private static final Limits DEFAULT_LIMITS = new Limits(DEFAULT_MAX_TOTAL_VALUES, DEFAULT_MAX_INPUT_CHARACTERS);

    private static final ThreadLocal<Limits> LIMITS = new ThreadLocal<>();

    /**
     * Runs {@code encode} with {@code limits} in force on this thread. The action handler builds
     * them from the server's configuration; without them the defaults apply.
     */
    public static <T> T withLimits(Limits limits, Supplier<T> encode) {
        Limits previous = LIMITS.get();
        LIMITS.set(limits);
        try {
            return encode.get();
        } finally {
            if (previous != null) {
                LIMITS.set(previous);
            } else {
                LIMITS.remove();
            }
        }
    }

    private static Limits limits() {
        Limits limits = LIMITS.get();
        return limits != null ? limits : DEFAULT_LIMITS;
    }

    /** The most values one response may hold as JSON numbers. */
    public static long maxTotalValues() {
        return limits().maxTotalValues;
    }

    /** The most values one response may hold as base64 float32 vectors. */
    public static long maxTotalBase64Values() {
        return 4 * maxTotalValues();
    }

    /** The longest input text a {@code deterministicFromInput} embedding hashes. */
    public static int maxInputCharacters() {
        return limits().maxInputCharacters;
    }

    /**
     * A request the real provider would reject, or that would make an unbounded response.
     * The action handler turns it into a 400 in the provider's error shape.
     */
    public static final class InvalidEmbeddingRequestException extends RuntimeException {
        private final String param;

        public InvalidEmbeddingRequestException(String param, String message) {
            super(message);
            this.param = param;
        }

        public String getParam() {
            return param;
        }
    }

    /**
     * The vector length a request asks for in {@code param}, or {@code null} when absent. A
     * value that is not an integer from 1 to {@link #MAX_REQUESTED_DIMENSIONS}, or not one of
     * {@code allowed} when any are given, is rejected.
     */
    public static Integer requestedDimensions(JsonNode node, String param, int... allowed) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.canConvertToInt() || !node.isIntegralNumber() || node.asInt() < 1 || node.asInt() > MAX_REQUESTED_DIMENSIONS) {
            throw new InvalidEmbeddingRequestException(param, param + " must be an integer from 1 to " + MAX_REQUESTED_DIMENSIONS);
        }
        int value = node.asInt();
        if (allowed.length > 0) {
            for (int candidate : allowed) {
                if (candidate == value) {
                    return value;
                }
            }
            throw new InvalidEmbeddingRequestException(param, param + " must be one of " + Arrays.toString(allowed));
        }
        return value;
    }

    /** Rejects a request with more than {@code max} inputs in {@code param}. */
    public static void checkInputCount(int count, int max, String param) {
        if (count > max) {
            throw new InvalidEmbeddingRequestException(param, param + " must have at most " + max + " entries, got " + count);
        }
    }

    /**
     * Rejects a response that would hold more than {@link #maxTotalValues()} values: {@code arrays}
     * arrays of the vector length the expectation or request resolves to.
     */
    public static void checkTotalValues(EmbeddingResponse embedding, Integer requestedDimensions, int providerDefault, long arrays) {
        checkTotalValues(dimensions(embedding, requestedDimensions, providerDefault) * arrays, maxTotalValues());
    }

    public static void checkTotalValues(long totalValues, long limit) {
        if (totalValues > limit) {
            throw new InvalidEmbeddingRequestException(null,
                "embedding response would hold " + totalValues + " values; the limit is " + limit
                    + ". Send fewer inputs or ask for fewer dimensions");
        }
    }

    /**
     * The distinct entries of a request's embedding-types array, in request order. An entry
     * that is not one of {@code allowed} is rejected, which also bounds the list.
     */
    public static List<String> requestedTypes(JsonNode types, String param, String... allowed) {
        Set<String> requested = new LinkedHashSet<>();
        if (types != null && types.isArray()) {
            List<String> known = Arrays.asList(allowed);
            for (JsonNode type : types) {
                if (!type.isTextual() || !known.contains(type.asText())) {
                    throw new InvalidEmbeddingRequestException(param, param + " must be one or more of " + known);
                }
                requested.add(type.asText());
            }
        }
        return new ArrayList<>(requested);
    }

    /** The vector length used: the expectation's, then the request's, then the provider default. */
    public static int dimensions(EmbeddingResponse embedding, Integer requestedDimensions, int providerDefault) {
        checkExpectationDimensions(embedding);
        return embedding.getDimensions() != null ? embedding.getDimensions()
            : requestedDimensions != null ? requestedDimensions : providerDefault;
    }

    /**
     * Rejects an expectation whose {@code dimensions} is below 1. The expectation schema refuses
     * one; this covers an expectation built without it. The handler answers {@code 502}, because
     * the fault is in the mock, not in the client's request.
     */
    private static void checkExpectationDimensions(EmbeddingResponse embedding) {
        if (embedding.getDimensions() != null && embedding.getDimensions() < 1) {
            throw new IllegalArgumentException("the expectation's embedding dimensions must be at least 1, got " + embedding.getDimensions());
        }
    }

    /**
     * Rejects an input too long to hash for a {@code deterministicFromInput} embedding. A random
     * vector does not read the text, so it has no such limit.
     */
    private static void checkInputLength(EmbeddingResponse embedding, String input) {
        if (input != null && Boolean.TRUE.equals(embedding.getDeterministicFromInput()) && input.length() > maxInputCharacters()) {
            throw new InvalidEmbeddingRequestException(null,
                "an input has " + input.length() + " characters; the limit is " + maxInputCharacters()
                    + ". Send shorter inputs");
        }
    }

    /**
     * A 400 in the provider's own invalid-request error shape: the OpenAI error envelope
     * (also Azure and the OpenAI-compatible providers), Google's {@code INVALID_ARGUMENT},
     * Bedrock's {@code ValidationException}, Cohere's {@code {"message":...}}, Voyage's
     * {@code {"detail":...}}, Ollama's {@code {"error":...}}, and otherwise a plain
     * {@code {"error":...}}.
     */
    public static HttpResponse invalidRequest(Provider provider, InvalidEmbeddingRequestException e) {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        HttpResponse response = response().withStatusCode(400).withHeader("content-type", "application/json");
        switch (provider) {
            case GEMINI:
                ObjectNode error = body.putObject("error");
                error.put("code", 400);
                error.put("message", e.getMessage());
                error.put("status", "INVALID_ARGUMENT");
                break;
            case BEDROCK:
                body.put("message", e.getMessage());
                response.withHeader("x-amzn-ErrorType", "ValidationException");
                break;
            case COHERE:
                body.put("message", e.getMessage());
                break;
            case VOYAGE:
                body.put("detail", e.getMessage());
                break;
            case OLLAMA:
            case ANTHROPIC:
            case OPENAI_RESPONSES:
                body.put("error", e.getMessage());
                break;
            default:
                ObjectNode openAiError = body.putObject("error");
                openAiError.put("message", e.getMessage());
                openAiError.put("type", "invalid_request_error");
                openAiError.put("param", e.getParam());
                openAiError.putNull("code");
                break;
        }
        return response.withBody(body.toString());
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Inputs in the OpenAI form, also used by Ollama {@code /api/embed} and the OpenAI-compatible
     * providers: a string is one input; an array of strings or of token arrays is one input per
     * element; an array of token integers is one input. A missing input embeds one empty text.
     */
    public static List<String> stringOrArrayInputs(JsonNode input) {
        if (input == null || input.isNull()) {
            return Collections.singletonList("");
        }
        if (!input.isArray()) {
            return Collections.singletonList(input.isTextual() ? input.asText() : input.toString());
        }
        if (input.size() > 0 && allNumbers(input)) {
            return Collections.singletonList(input.toString());
        }
        checkInputCount(input.size(), MAX_INPUTS, "input");
        List<String> inputs = new ArrayList<>(input.size());
        for (JsonNode element : input) {
            inputs.add(element.isTextual() ? element.asText() : element.toString());
        }
        return inputs;
    }

    private static boolean allNumbers(JsonNode array) {
        for (JsonNode element : array) {
            if (!element.isNumber()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The text a vector for an image input is built from: a digest of the image's data URI, so
     * the same image always gets the same {@code deterministicFromInput} vector and a large image
     * costs no more to hash than a short text.
     */
    public static String imageInput(JsonNode image) {
        String uri = image == null ? "" : image.isTextual() ? image.asText() : image.toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(uri.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** The text of a Gemini {@code Content}: its {@code parts[].text} values joined by newlines. */
    public static String geminiContentText(JsonNode content) {
        return joinTextParts(content != null ? content.get("parts") : null, null);
    }

    /**
     * Joins the {@code text} of each part, skipping parts without text. When {@code type} is
     * non-null only parts with that {@code type} are used (Cohere v4 {@code inputs[].content[]}).
     */
    public static String joinTextParts(JsonNode parts, String type) {
        if (parts == null || !parts.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (type != null && !type.equals(text(part, "type"))) {
                continue;
            }
            JsonNode value = part.get("text");
            if (value != null && value.isTextual()) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(value.asText());
            }
        }
        return text.toString();
    }

    /**
     * The vector for one input. The expectation's {@code dimensions} wins, then the size the
     * request asked for, then the provider default. A vector longer than
     * {@link #maxTotalValues()} is rejected.
     */
    public static double[] vector(EmbeddingResponse embedding, String input, Integer requestedDimensions, int providerDefault) {
        checkTotalValues(embedding, requestedDimensions, providerDefault, 1);
        return build(embedding, input, requestedDimensions, providerDefault);
    }

    /**
     * As {@link #vector}, for a caller that has already checked the whole response's size. An
     * input longer than {@link #maxInputCharacters()} is rejected when the vector is hashed from it.
     */
    public static double[] build(EmbeddingResponse embedding, String input, Integer requestedDimensions, int providerDefault) {
        checkExpectationDimensions(embedding);
        checkInputLength(embedding, input);
        return EmbeddingVectors.build(embedding, input, requestedDimensions != null ? requestedDimensions : providerDefault);
    }

    public static int tokens(List<String> inputs) {
        int total = 0;
        for (String input : inputs) {
            total += EmbeddingVectors.approximateTokens(input);
        }
        return total;
    }

    public static ArrayNode addFloats(ArrayNode target, double[] vector) {
        for (double v : vector) {
            target.add(v);
        }
        return target;
    }

    /** OpenAI {@code encoding_format: "base64"}: the vector as little-endian float32 bytes, base64-encoded. */
    public static String base64Float32(double[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (double v : vector) {
            buffer.putFloat((float) v);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * One vector in a Cohere embedding type: {@code float}, {@code int8} (-128..127),
     * {@code uint8} (0..255), or the bit-packed {@code ubinary} (0..255) and {@code binary}
     * (ubinary - 128), which are one eighth of the float length.
     */
    public static ArrayNode cohereTyped(ArrayNode target, double[] vector, String type) {
        switch (type) {
            case "int8":
                for (double v : vector) {
                    target.add((int) Math.max(-128, Math.min(127, Math.round(v * 127))));
                }
                return target;
            case "uint8":
                for (double v : vector) {
                    target.add((int) Math.max(0, Math.min(255, Math.round((v + 1) * 127.5))));
                }
                return target;
            case "binary":
            case "ubinary":
                int offset = "binary".equals(type) ? 128 : 0;
                for (int start = 0; start < vector.length; start += 8) {
                    int packed = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int i = start + bit;
                        packed = (packed << 1) | (i < vector.length && vector[i] > 0 ? 1 : 0);
                    }
                    target.add(packed - offset);
                }
                return target;
            default:
                return addFloats(target, vector);
        }
    }

    /** Titan v2 {@code binary}: one 0 or 1 per dimension. */
    public static ArrayNode titanBinary(ArrayNode target, double[] vector) {
        for (double v : vector) {
            target.add(v > 0 ? 1 : 0);
        }
        return target;
    }

    public static ObjectNode object() {
        return OBJECT_MAPPER.createObjectNode();
    }

    public static HttpResponse json(ObjectNode root, String provider) {
        try {
            return response()
                .withStatusCode(200)
                .withHeader("content-type", "application/json")
                .withBody(OBJECT_MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode " + provider + " embedding response", e);
        }
    }
}
