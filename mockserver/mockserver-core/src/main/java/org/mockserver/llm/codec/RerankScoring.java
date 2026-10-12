package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RerankResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockserver.model.HttpResponse.response;

/**
 * Shared rerank scoring used by the rerank codecs (Cohere, Voyage): one result per
 * candidate document, each scored with a relevance score in {@code (0, 1)} (reproducible
 * when {@code deterministicFromInput} is set), sorted descending, then capped to the
 * number of results asked for. Each codec wraps the results in its provider's envelope.
 */
public final class RerankScoring {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RerankScoring() {
    }

    /** One scored, original-index-tagged document. */
    public static final class Scored {
        public final int index;
        public final double score;

        Scored(int index, double score) {
            this.index = index;
            this.score = score;
        }
    }

    /**
     * Score each document and return them sorted by descending relevance,
     * capped to the expectation's {@code topN} when set.
     */
    public static List<Scored> score(RerankResponse rerank, List<String> documents) {
        return score(rerank, documents, null);
    }

    /**
     * Score each document and return them sorted by descending relevance, capped to the
     * expectation's {@code topN} when set, else to {@code requestedTopN}, the number of results
     * the request asked for.
     */
    public static List<Scored> score(RerankResponse rerank, List<String> documents, Integer requestedTopN) {
        // Mirror EmbeddingVectors: deterministic only when explicitly opted in
        // (deterministicFromInput == true), otherwise random scores.
        boolean deterministic = rerank != null && Boolean.TRUE.equals(rerank.getDeterministicFromInput());
        long seed = rerank != null && rerank.getSeed() != null ? rerank.getSeed() : 0L;

        List<Scored> scored = new ArrayList<>();
        List<String> docs = documents != null ? documents : new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            double score = deterministic
                ? deterministicScore(docs.get(i), seed)
                : ThreadLocalRandom.current().nextDouble();
            scored.add(new Scored(i, score));
        }
        // Sort by descending score; ties broken by original index for stability.
        scored.sort(Comparator.<Scored>comparingDouble(s -> s.score).reversed()
            .thenComparingInt(s -> s.index));

        Integer topN = rerank != null && rerank.getTopN() != null ? rerank.getTopN() : requestedTopN;
        if (topN != null && topN >= 0 && topN < scored.size()) {
            return new ArrayList<>(scored.subList(0, topN));
        }
        return scored;
    }

    /**
     * The number of results a request asks for in {@code param} (Cohere {@code top_n}, Voyage
     * {@code top_k}), or {@code null} when absent. A value that is not a positive integer is
     * rejected.
     */
    public static Integer requestedTopN(JsonNode node, String param) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() < 1) {
            throw new EmbeddingWire.InvalidEmbeddingRequestException(param, param + " must be a positive integer");
        }
        return node.asInt();
    }

    /** Whether the request sets {@code return_documents} to {@code true}. */
    public static boolean returnDocuments(JsonNode body) {
        JsonNode value = body != null ? body.get("return_documents") : null;
        return value != null && value.isBoolean() && value.asBoolean();
    }

    public static ObjectNode object() {
        return OBJECT_MAPPER.createObjectNode();
    }

    public static HttpResponse json(ObjectNode root) {
        try {
            return response()
                .withStatusCode(200)
                .withHeader("content-type", "application/json")
                .withBody(OBJECT_MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode rerank response", e);
        }
    }

    /** Deterministic relevance score in {@code (0, 1)} from document + seed. */
    static double deterministicScore(String document, long seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.valueOf(seed).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update((document != null ? document : "").getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            long bits = ByteBuffer.wrap(hash, 0, 8).getLong() & Long.MAX_VALUE;
            // Map to (0, 1); avoid exactly 0 or 1 so it always reads as a fractional score.
            double score = (bits % 1_000_000L + 1) / 1_000_001.0;
            // Round to 4 decimal places, matching real rerank API precision.
            return Math.round(score * 10_000.0) / 10_000.0;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
