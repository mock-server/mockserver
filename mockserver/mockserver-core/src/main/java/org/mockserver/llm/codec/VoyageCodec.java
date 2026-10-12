package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.ProviderCodec;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.RerankResponse;

import java.util.List;

/**
 * Codec for Voyage AI's rerank endpoint ({@code POST /v1/rerank}). Voyage is a rerank-only
 * provider in MockServer; it does not participate in the chat/completion encode/decode paths.
 * The response is the list envelope
 * {@code {"object":"list","data":[{"index":N,"relevance_score":F}, ...],"model":...,"usage":{"total_tokens":N}}},
 * sorted by descending relevance and capped to the request's {@code top_k}. The request's
 * {@code model} is echoed, {@code return_documents: true} adds each {@code document}, and
 * {@code total_tokens} is estimated from the query and documents.
 */
public class VoyageCodec implements ProviderCodec {

    private static final String DEFAULT_MODEL = "rerank-2";

    @Override
    public Provider provider() {
        return Provider.VOYAGE;
    }

    @Override
    public String apiVersion() {
        return "voyage-rerank-2";
    }

    @Override
    public HttpResponse encodeRerank(RerankResponse rerank, List<String> documents) {
        return encodeRerank(rerank, documents, null);
    }

    @Override
    public HttpResponse encodeRerank(RerankResponse rerank, List<String> documents, HttpRequest request) {
        JsonNode body = EmbeddingWire.body(request);
        List<RerankScoring.Scored> scored = RerankScoring.score(rerank, documents, RerankScoring.requestedTopN(body.get("top_k"), "top_k"));
        boolean returnDocuments = RerankScoring.returnDocuments(body);

        ObjectNode root = RerankScoring.object();
        root.put("object", "list");
        ArrayNode data = root.putArray("data");
        for (RerankScoring.Scored s : scored) {
            ObjectNode result = data.addObject();
            result.put("index", s.index);
            result.put("relevance_score", s.score);
            if (returnDocuments) {
                result.put("document", documents.get(s.index));
            }
        }
        root.put("model", EmbeddingWire.firstNonBlank(EmbeddingWire.text(body, "model"), DEFAULT_MODEL));
        root.putObject("usage").put("total_tokens", totalTokens(EmbeddingWire.text(body, "query"), documents));
        return RerankScoring.json(root);
    }

    /**
     * Sized as Voyage documents a rerank request's token limit: query tokens times the number of
     * documents, plus every document's tokens (https://docs.voyageai.com/reference/reranker-api),
     * each estimated as the embedding codecs do.
     */
    static long totalTokens(String query, List<String> documents) {
        if (documents == null) {
            return 0;
        }
        long total = (long) EmbeddingVectors.approximateTokens(query) * documents.size();
        for (String document : documents) {
            total += EmbeddingVectors.approximateTokens(document);
        }
        return total;
    }
}
