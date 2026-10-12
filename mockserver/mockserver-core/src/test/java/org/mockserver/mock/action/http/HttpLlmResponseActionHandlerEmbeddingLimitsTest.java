package org.mockserver.mock.action.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.EmbeddingResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;

import java.util.Base64;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;

/**
 * Proves {@code llmEmbeddingMaxResponseValues} and {@code llmEmbeddingMaxInputCharacters} are
 * enforced from the server's {@link Configuration} instance, so a value set over
 * {@code PUT /mockserver/configuration} takes effect. Never touches the static store.
 */
public class HttpLlmResponseActionHandlerEmbeddingLimitsTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** A LangChain {@code OpenAIEmbeddings} batch at its default chunk size: 1,000 inputs, base64, 1,536 dimensions. */
    private static final String LANGCHAIN_BATCH = "{\"model\":\"text-embedding-3-small\",\"encoding_format\":\"base64\",\"input\":" + inputs(1000) + "}";

    private static HttpResponse post(Configuration configuration, EmbeddingResponse embedding, String body) {
        HttpLlmResponseActionHandler handler = new HttpLlmResponseActionHandler(new MockServerLogger(), configuration);
        return handler.handle(
            llmResponse().withProvider(Provider.OPENAI).withEmbedding(embedding),
            request().withMethod("POST").withPath("/v1/embeddings").withBody(body)
        );
    }

    private static String inputs(int count) {
        StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            array.append(i > 0 ? "," : "").append("\"doc ").append(i).append('"');
        }
        return array.append(']').toString();
    }

    @Test
    public void shouldRejectALangChainDefaultBatchAtTheDefaultLimit() throws Exception {
        HttpResponse response = post(Configuration.configuration(), EmbeddingResponse.embedding(), LANGCHAIN_BATCH);

        assertThat(response.getStatusCode(), is(400));
        assertThat(OBJECT_MAPPER.readTree(response.getBodyAsString()).get("error").get("message").asText(),
            containsString("the limit is 1048576"));
    }

    @Test
    public void shouldEnforceLlmEmbeddingMaxResponseValuesFromConfigurationInstance() throws Exception {
        // 1,000 x 1,536 = 1,536,000 base64 values, under 4 x 384,000
        HttpResponse raised = post(Configuration.configuration().llmEmbeddingMaxResponseValues(384_000), EmbeddingResponse.embedding(), LANGCHAIN_BATCH);
        assertThat(raised.getStatusCode(), is(200));
        JsonNode data = OBJECT_MAPPER.readTree(raised.getBodyAsString()).get("data");
        assertThat(data.size(), is(1000));
        assertThat(Base64.getDecoder().decode(data.get(999).get("embedding").asText()).length, is(1536 * Float.BYTES));

        HttpResponse lowered = post(Configuration.configuration().llmEmbeddingMaxResponseValues(1_535), EmbeddingResponse.embedding(), "{\"input\":\"x\"}");
        assertThat(lowered.getStatusCode(), is(400));
        assertThat(OBJECT_MAPPER.readTree(lowered.getBodyAsString()).get("error").get("message").asText(),
            is("embedding response would hold 1536 values; the limit is 1535. Send fewer inputs or ask for fewer dimensions"));
        assertThat(post(Configuration.configuration().llmEmbeddingMaxResponseValues(1_536), EmbeddingResponse.embedding(), "{\"input\":\"x\"}")
            .getStatusCode(), is(200));
    }

    @Test
    public void shouldEnforceLlmEmbeddingMaxInputCharactersFromConfigurationInstance() throws Exception {
        EmbeddingResponse deterministic = EmbeddingResponse.embedding().withDeterministicFromInput(true);
        Configuration configuration = Configuration.configuration().llmEmbeddingMaxInputCharacters(10);

        assertThat(post(configuration, deterministic, "{\"input\":\"ten chars!\"}").getStatusCode(), is(200));
        HttpResponse rejected = post(configuration, deterministic, "{\"input\":\"eleven char\"}");
        assertThat(rejected.getStatusCode(), is(400));
        assertThat(OBJECT_MAPPER.readTree(rejected.getBodyAsString()).get("error").get("message").asText(),
            is("an input has 11 characters; the limit is 10. Send shorter inputs"));
        assertThat("the default limit lets the same input through",
            post(Configuration.configuration(), deterministic, "{\"input\":\"eleven char\"}").getStatusCode(), is(200));
    }

    @Test
    public void shouldTreatALimitBelowOneAsOne() throws Exception {
        Configuration configuration = Configuration.configuration().llmEmbeddingMaxResponseValues(0).llmEmbeddingMaxInputCharacters(-5);
        EmbeddingResponse deterministic = EmbeddingResponse.embedding().withDeterministicFromInput(true).withDimensions(1);

        assertThat(post(configuration, deterministic, "{\"input\":\"x\"}").getStatusCode(), is(200));
        assertThat(post(configuration, deterministic, "{\"input\":\"xy\"}").getStatusCode(), is(400));
        assertThat(post(configuration, deterministic, "{\"input\":[\"x\",\"y\"]}").getStatusCode(), is(400));
    }
}
