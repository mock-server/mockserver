package org.mockserver.llm;

import org.mockserver.llm.codec.EmbeddingWire;
import org.mockserver.model.*;

import java.util.List;

public interface ProviderCodec {

    Provider provider();

    String apiVersion();

    default HttpResponse encode(Completion completion, String model) {
        throw new UnsupportedOperationException("encode not implemented for provider " + provider());
    }

    default List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics) {
        throw new UnsupportedOperationException("encodeStreaming not implemented for provider " + provider());
    }

    /**
     * Request-aware encode, for providers that expose more than one wire API under one
     * {@link Provider} and pick between them from the inbound request (Bedrock chooses
     * InvokeModel or Converse by path). Defaults to {@link #encode(Completion, String)}.
     */
    default HttpResponse encode(Completion completion, String model, HttpRequest request) {
        return encode(completion, model);
    }

    /** Request-aware streaming encode; see {@link #encode(Completion, String, HttpRequest)}. */
    default List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics, HttpRequest request) {
        return encodeStreaming(completion, model, physics);
    }

    /**
     * Whether the request only loads or unloads a model, which the provider answers without
     * generating anything (Ollama), so there is no completion to trace, count or check.
     */
    default boolean isModelManagementRequest(HttpRequest request) {
        return false;
    }

    /**
     * The wire format this provider uses for streaming responses.
     * Defaults to {@link StreamingFormat#SSE}; override for providers
     * that use a different format (e.g. Ollama uses NDJSON).
     */
    default StreamingFormat streamingFormat() {
        return StreamingFormat.SSE;
    }

    /** Request-aware streaming format; see {@link #encode(Completion, String, HttpRequest)}. */
    default StreamingFormat streamingFormat(HttpRequest request) {
        return streamingFormat();
    }

    default HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input) {
        throw new UnsupportedOperationException("encodeEmbedding not implemented for provider " + provider());
    }

    /**
     * Model-aware embedding encode. Most providers have a single embedding wire
     * shape and ignore the model, so the default delegates to
     * {@link #encodeEmbedding(EmbeddingResponse, String)}. Providers whose
     * embedding shape varies by model family (e.g. Bedrock Titan vs Cohere)
     * override this to branch on {@code model}.
     */
    default HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input, String model) {
        return encodeEmbedding(embedding, input);
    }

    /**
     * Request-aware embedding encode, the one the action handler calls. Codecs override it to
     * read their provider's own input field(s) and return one vector per input. The default
     * embeds a top-level {@code input} as one text, for codecs without their own parsing.
     */
    default HttpResponse encodeEmbedding(EmbeddingResponse embedding, HttpRequest request, String model) {
        return encodeEmbedding(embedding, EmbeddingWire.legacyInputText(request), model);
    }

    /**
     * Encode a rerank response for providers that expose a rerank endpoint
     * (e.g. Cohere {@code /v1/rerank}, Voyage {@code /v1/rerank}). Each result is
     * a {@code {"index":N,"relevance_score":F}} entry, one per candidate document,
     * sorted by descending relevance. The surrounding envelope is provider-specific
     * (Cohere uses a top-level {@code results} array; Voyage uses an OpenAI-style
     * {@code data} list with an {@code object}/{@code usage} wrapper).
     */
    default HttpResponse encodeRerank(RerankResponse rerank, java.util.List<String> documents) {
        throw new UnsupportedOperationException("encodeRerank not implemented for provider " + provider());
    }

    /**
     * As {@link #encodeRerank(RerankResponse, java.util.List)}, for a codec that also honours the
     * request's own fields, such as how many results to return and whether to echo the documents.
     */
    default HttpResponse encodeRerank(RerankResponse rerank, java.util.List<String> documents, HttpRequest request) {
        return encodeRerank(rerank, documents);
    }

    default ParsedConversation decode(HttpRequest request) {
        throw new UnsupportedOperationException("decode not implemented for provider " + provider());
    }
}
