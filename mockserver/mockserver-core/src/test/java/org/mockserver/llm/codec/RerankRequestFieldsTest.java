package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpLlmResponseActionHandler;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.RerankResponse;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;

/**
 * Rerank requests as the real APIs define them, sent through the action handler:
 * Cohere v2 (https://docs.cohere.com/reference/rerank), Cohere v1
 * (https://docs.cohere.com/v1/reference/rerank) and Voyage
 * (https://docs.voyageai.com/reference/reranker-api). A rejected request gets Cohere's
 * {@code {"message":...}} or Voyage's {@code {"detail":...}} error body.
 */
public class RerankRequestFieldsTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DOCUMENTS = "[\"carson city\",\"washington dc\",\"capital punishment\",\"saipan\",\"north dakota\"]";

    private final HttpLlmResponseActionHandler handler = new HttpLlmResponseActionHandler(new MockServerLogger());

    private HttpResponse post(Provider provider, RerankResponse rerank, String path, String body) {
        return handler.handle(
            llmResponse().withProvider(provider).withRerank(rerank),
            request().withMethod("POST").withPath(path).withBody(body)
        );
    }

    private static JsonNode ok(HttpResponse response) throws Exception {
        assertThat(response.getBodyAsString(), response.getStatusCode(), is(200));
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
        return OBJECT_MAPPER.readTree(response.getBodyAsString());
    }

    private static List<String> documents() throws Exception {
        List<String> documents = new ArrayList<>();
        OBJECT_MAPPER.readTree(DOCUMENTS).forEach(d -> documents.add(d.asText()));
        return documents;
    }

    // ---- Cohere v2 ----

    @Test
    public void shouldReturnTheCohereV2ShapeCappedToTheRequestsTopN() throws Exception {
        JsonNode root = ok(post(Provider.COHERE, RerankResponse.rerank(), "/v2/rerank",
            "{\"model\":\"rerank-v3.5\",\"query\":\"capital of the US\",\"documents\":" + DOCUMENTS + ",\"top_n\":3}"));

        JsonNode results = root.get("results");
        assertThat(results.size(), is(3));
        for (JsonNode result : results) {
            assertThat(result.get("index").isInt(), is(true));
            assertThat(result.get("relevance_score").isDouble(), is(true));
            assertThat(result.has("document"), is(false));
        }
        assertThat(root.get("id").asText(), not(emptyString()));
        assertThat(root.get("meta").get("api_version").get("version").asText(), is("2"));
        assertThat(root.get("meta").get("billed_units").get("search_units").asInt(), is(1));
        assertThat("Cohere's rerank response carries no model", root.has("model"), is(false));
    }

    @Test
    public void shouldNotReturnDocumentsOnCohereV2() throws Exception {
        JsonNode root = ok(post(Provider.COHERE, RerankResponse.rerank(), "/v2/rerank",
            "{\"model\":\"rerank-v3.5\",\"query\":\"q\",\"documents\":" + DOCUMENTS + ",\"return_documents\":true}"));
        assertThat(root.get("results").size(), is(5));
        root.get("results").forEach(result -> assertThat(result.has("document"), is(false)));
    }

    @Test
    public void shouldReturnEveryDocumentWhenTopNIsAbsentOrLarger() throws Exception {
        assertThat(ok(post(Provider.COHERE, RerankResponse.rerank(), "/v2/rerank",
            "{\"query\":\"q\",\"documents\":" + DOCUMENTS + "}")).get("results").size(), is(5));
        assertThat(ok(post(Provider.COHERE, RerankResponse.rerank(), "/v2/rerank",
            "{\"query\":\"q\",\"documents\":" + DOCUMENTS + ",\"top_n\":50}")).get("results").size(), is(5));
    }

    @Test
    public void shouldPreferTheExpectationsTopNToTheRequests() throws Exception {
        JsonNode root = ok(post(Provider.COHERE, RerankResponse.rerank().withTopN(2), "/v2/rerank",
            "{\"query\":\"q\",\"documents\":" + DOCUMENTS + ",\"top_n\":4}"));
        assertThat(root.get("results").size(), is(2));
    }

    @Test
    public void shouldRejectATopNThatIsNotAPositiveInteger() throws Exception {
        for (String topN : new String[]{"0", "-1", "2.5", "\"3\"", "true"}) {
            HttpResponse response = post(Provider.COHERE, RerankResponse.rerank(), "/v2/rerank",
                "{\"query\":\"q\",\"documents\":" + DOCUMENTS + ",\"top_n\":" + topN + "}");
            assertThat(topN, response.getStatusCode(), is(400));
            assertThat(topN, OBJECT_MAPPER.readTree(response.getBodyAsString()).get("message").asText(), containsString("top_n"));
        }
    }

    @Test
    public void shouldBillOneSearchUnitPerStartedHundredDocuments() throws Exception {
        StringBuilder documents = new StringBuilder("[");
        for (int i = 0; i < 201; i++) {
            documents.append(i > 0 ? "," : "").append("\"doc ").append(i).append('"');
        }
        documents.append(']');
        JsonNode root = ok(post(Provider.COHERE, RerankResponse.rerank(), "/v2/rerank",
            "{\"query\":\"q\",\"documents\":" + documents + ",\"top_n\":1}"));
        assertThat(root.get("meta").get("billed_units").get("search_units").asInt(), is(3));
    }

    // ---- Cohere v1 ----

    @Test
    public void shouldReturnEachDocumentOnCohereV1WhenAsked() throws Exception {
        List<String> documents = documents();
        JsonNode root = ok(post(Provider.COHERE, RerankResponse.rerank().withDeterministicFromInput(true), "/v1/rerank",
            "{\"query\":\"q\",\"documents\":" + DOCUMENTS + ",\"top_n\":2,\"return_documents\":true}"));

        JsonNode results = root.get("results");
        assertThat(results.size(), is(2));
        for (JsonNode result : results) {
            assertThat(result.get("document").get("text").asText(), is(documents.get(result.get("index").asInt())));
        }
        assertThat(root.get("meta").get("api_version").get("version").asText(), is("1"));
    }

    @Test
    public void shouldReturnEachStructuredCohereV1DocumentWithAllItsFields() throws Exception {
        JsonNode root = ok(post(Provider.COHERE, RerankResponse.rerank(), "/v1/rerank",
            "{\"query\":\"q\",\"documents\":[{\"text\":\"carson city\",\"title\":\"Nevada\"},\"washington dc\","
                + "{\"title\":\"Saipan\",\"body\":\"island\"}],\"rank_fields\":[\"title\",\"body\"],\"return_documents\":true}"));

        JsonNode[] byIndex = new JsonNode[3];
        root.get("results").forEach(result -> byIndex[result.get("index").asInt()] = result.get("document"));
        assertThat(byIndex[0].toString(), is("{\"text\":\"carson city\",\"title\":\"Nevada\"}"));
        assertThat(byIndex[1].toString(), is("{\"text\":\"washington dc\"}"));
        assertThat(byIndex[2].toString(), is("{\"title\":\"Saipan\",\"body\":\"island\"}"));
    }

    @Test
    public void shouldOmitDocumentsOnCohereV1ByDefault() throws Exception {
        JsonNode root = ok(post(Provider.COHERE, RerankResponse.rerank(), "/v1/rerank",
            "{\"query\":\"q\",\"documents\":" + DOCUMENTS + "}"));
        root.get("results").forEach(result -> assertThat(result.has("document"), is(false)));
    }

    // ---- Voyage ----

    @Test
    public void shouldCapVoyageResultsToTheRequestsTopKAndEchoItsModel() throws Exception {
        JsonNode root = ok(post(Provider.VOYAGE, RerankResponse.rerank(), "/v1/rerank",
            "{\"query\":\"q\",\"documents\":" + DOCUMENTS + ",\"model\":\"rerank-2.5-lite\",\"top_k\":2}"));
        assertThat(root.get("object").asText(), is("list"));
        assertThat(root.get("data").size(), is(2));
        assertThat(root.get("model").asText(), is("rerank-2.5-lite"));
        assertThat(root.get("usage").get("total_tokens").isInt(), is(true));
        root.get("data").forEach(result -> assertThat(result.has("document"), is(false)));
    }

    @Test
    public void shouldReturnEachVoyageDocumentAsAStringWhenAsked() throws Exception {
        List<String> documents = documents();
        JsonNode root = ok(post(Provider.VOYAGE, RerankResponse.rerank(), "/v1/rerank",
            "{\"query\":\"q\",\"documents\":" + DOCUMENTS + ",\"model\":\"rerank-2.5\",\"return_documents\":true}"));
        JsonNode data = root.get("data");
        assertThat(data.size(), is(5));
        for (JsonNode result : data) {
            assertThat(result.get("document").isTextual(), is(true));
            assertThat(result.get("document").asText(), is(documents.get(result.get("index").asInt())));
        }
    }

    @Test
    public void shouldCountVoyageTokensAsQueryTokensPerDocumentPlusDocumentTokens() throws Exception {
        String query = "what is the capital of the united states";
        List<String> documents = documents();
        int expected = EmbeddingVectors.approximateTokens(query) * documents.size();
        for (String document : documents) {
            expected += EmbeddingVectors.approximateTokens(document);
        }
        JsonNode root = ok(post(Provider.VOYAGE, RerankResponse.rerank(), "/v1/rerank",
            "{\"query\":\"" + query + "\",\"documents\":" + DOCUMENTS + ",\"model\":\"rerank-2.5\",\"top_k\":1}"));

        assertThat(root.get("data").size(), is(1));
        assertThat(root.get("usage").get("total_tokens").isIntegralNumber(), is(true));
        assertThat(root.get("usage").get("total_tokens").asInt(), is(expected));
        assertThat(expected, is(10 * 5 + 2 + 3 + 4 + 1 + 3));
    }

    @Test
    public void shouldRejectAVoyageTopKThatIsNotAPositiveInteger() throws Exception {
        HttpResponse response = post(Provider.VOYAGE, RerankResponse.rerank(), "/v1/rerank",
            "{\"query\":\"q\",\"documents\":" + DOCUMENTS + ",\"model\":\"rerank-2.5\",\"top_k\":0}");
        assertThat(response.getStatusCode(), is(400));
        assertThat(OBJECT_MAPPER.readTree(response.getBodyAsString()).get("detail").asText(), containsString("top_k"));
    }
}
