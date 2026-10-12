package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.RerankResponse;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class CohereCodecTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final CohereCodec codec = new CohereCodec();

    @Test
    public void shouldReturnCohereProviderAndVersion() {
        assertThat(codec.provider(), is(Provider.COHERE));
        assertThat(codec.apiVersion(), is("cohere-rerank-v3"));
    }

    @Test
    public void shouldEncodeRerankResultsShape() throws Exception {
        // given
        List<String> documents = Arrays.asList("doc a", "doc b", "doc c");

        // when — Cohere /v1/rerank shape: {"results":[{"index":N,"relevance_score":F}, ...]}
        HttpResponse response = codec.encodeRerank(
            RerankResponse.rerank().withDeterministicFromInput(true), documents);

        // then
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode results = root.get("results");
        assertThat(results.isArray(), is(true));
        assertThat(results.size(), is(3));

        double previous = Double.MAX_VALUE;
        for (JsonNode result : results) {
            int index = result.get("index").asInt();
            double score = result.get("relevance_score").asDouble();
            assertThat(index, is(both(greaterThanOrEqualTo(0)).and(lessThan(3))));
            assertThat(score, is(both(greaterThan(0.0)).and(lessThanOrEqualTo(1.0))));
            // descending order
            assertThat(score, is(lessThanOrEqualTo(previous)));
            previous = score;
        }
    }

    @Test
    public void shouldCapResultsToTopN() throws Exception {
        // given
        List<String> documents = Arrays.asList("a", "b", "c", "d", "e");
        RerankResponse rerank = RerankResponse.rerank().withTopN(2);

        // when
        HttpResponse response = codec.encodeRerank(rerank, documents);

        // then
        JsonNode results = OBJECT_MAPPER.readTree(response.getBodyAsString()).get("results");
        assertThat(results.size(), is(2));
    }

    @Test
    public void shouldProduceDeterministicScoresForSameDocumentsAndSeed() throws Exception {
        // given — determinism is opt-in via deterministicFromInput (matches embeddings)
        List<String> documents = Arrays.asList("alpha", "beta", "gamma");
        RerankResponse rerank = RerankResponse.rerank().withDeterministicFromInput(true).withSeed(99L);

        // when
        HttpResponse r1 = codec.encodeRerank(rerank, documents);
        HttpResponse r2 = codec.encodeRerank(rerank, documents);

        // then — the scores repeat; each response has its own id
        assertThat(OBJECT_MAPPER.readTree(r1.getBodyAsString()).get("results"), is(OBJECT_MAPPER.readTree(r2.getBodyAsString()).get("results")));
    }

    @Test
    public void shouldHandleEmptyDocuments() throws Exception {
        // when
        HttpResponse response = codec.encodeRerank(RerankResponse.rerank(), java.util.Collections.emptyList());

        // then
        JsonNode results = OBJECT_MAPPER.readTree(response.getBodyAsString()).get("results");
        assertThat(results.isArray(), is(true));
        assertThat(results.size(), is(0));
    }
}
