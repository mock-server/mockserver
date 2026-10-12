package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.ProviderCodec;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.RerankResponse;
import org.mockserver.uuid.UUIDService;

import java.util.List;

/**
 * Codec for Cohere's rerank endpoint, {@code POST /v2/rerank} and the older
 * {@code POST /v1/rerank}. Cohere is a rerank-only provider in MockServer; it does not
 * participate in the chat/completion encode/decode paths. The response is
 * {@code {"results":[{"index":N,"relevance_score":F}, ...],"id":...,"meta":{"api_version":
 * {"version":...},"billed_units":{"search_units":N}}}}, sorted by descending relevance and
 * capped to the request's {@code top_n}. On v1, {@code return_documents: true} adds each
 * document as sent, a string as {@code {"text":...}}; v2 has no such option. v1's
 * {@code rank_fields} only changes real relevance scores, which a mock never reproduces,
 * so it is not read.
 */
public class CohereCodec implements ProviderCodec {

    /** Cohere bills one search unit per query for each started batch of this many documents. */
    private static final int DOCUMENTS_PER_SEARCH_UNIT = 100;

    @Override
    public Provider provider() {
        return Provider.COHERE;
    }

    @Override
    public String apiVersion() {
        return "cohere-rerank-v3";
    }

    @Override
    public HttpResponse encodeRerank(RerankResponse rerank, List<String> documents) {
        return encodeRerank(rerank, documents, null);
    }

    @Override
    public HttpResponse encodeRerank(RerankResponse rerank, List<String> documents, HttpRequest request) {
        JsonNode body = EmbeddingWire.body(request);
        boolean v2 = EmbeddingWire.path(request).contains("/v2/rerank");
        List<RerankScoring.Scored> scored = RerankScoring.score(rerank, documents, RerankScoring.requestedTopN(body.get("top_n"), "top_n"));
        boolean returnDocuments = !v2 && RerankScoring.returnDocuments(body);
        JsonNode requestDocuments = body.path("documents");

        ObjectNode root = RerankScoring.object();
        ArrayNode results = root.putArray("results");
        for (RerankScoring.Scored s : scored) {
            ObjectNode result = results.addObject();
            result.put("index", s.index);
            result.put("relevance_score", s.score);
            if (returnDocuments) {
                JsonNode requestDocument = requestDocuments.path(s.index);
                if (requestDocument.isObject()) {
                    result.set("document", requestDocument.deepCopy());
                } else {
                    result.putObject("document").put("text", documents.get(s.index));
                }
            }
        }
        root.put("id", UUIDService.getNonSecureUUID());
        ObjectNode meta = root.putObject("meta");
        meta.putObject("api_version").put("version", v2 ? "2" : "1");
        int documentCount = documents != null ? documents.size() : 0;
        meta.putObject("billed_units").put("search_units", Math.max(1, (documentCount + DOCUMENTS_PER_SEARCH_UNIT - 1) / DOCUMENTS_PER_SEARCH_UNIT));
        return RerankScoring.json(root);
    }
}
