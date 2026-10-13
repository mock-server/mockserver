package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpLlmResponseActionHandler;
import org.mockserver.model.EmbeddingResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;

/**
 * Embedding requests in each provider's real request shape, through the action handler, against
 * the response shapes in each provider's API reference: OpenAI {@code CreateEmbeddingResponse},
 * Gemini {@code embedContent}/{@code batchEmbedContents}, Bedrock Titan G1/V2 and Cohere Embed
 * v3/v4, and Ollama {@code /api/embed} and {@code /api/embeddings}. The expected shapes are
 * written from those references, not from codec output.
 */
public class EmbeddingProviderWireShapeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static EmbeddingResponse deterministic() {
        return EmbeddingResponse.embedding().withDeterministicFromInput(true).withSeed(7L);
    }

    private final HttpLlmResponseActionHandler handler = new HttpLlmResponseActionHandler(new MockServerLogger());

    private JsonNode post(Provider provider, String model, EmbeddingResponse embedding, String path, String body) throws Exception {
        HttpResponse response = handler.handle(
            llmResponse().withProvider(provider).withModel(model).withEmbedding(embedding),
            request().withMethod("POST").withPath(path).withBody(body)
        );
        assertThat(response.getBodyAsString(), response.getStatusCode(), is(200));
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
        return OBJECT_MAPPER.readTree(response.getBodyAsString());
    }

    private static List<Double> floats(JsonNode array) {
        List<Double> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asDouble()));
        return values;
    }

    // ---- OpenAI family ----

    @Test
    public void shouldReturnOneOpenAiEmbeddingPerInputInInputOrder() throws Exception {
        JsonNode root = post(Provider.OPENAI, "text-embedding-3-small", deterministic(), "/v1/embeddings",
            "{\"model\":\"text-embedding-3-large\",\"input\":[\"the cat sat on the mat\",\"quarterly financial report\",\"the cat sat on the mat\"]}");

        assertThat(root.get("object").asText(), is("list"));
        assertThat("the request model is echoed", root.get("model").asText(), is("text-embedding-3-large"));
        JsonNode data = root.get("data");
        assertThat(data.size(), is(3));
        for (int i = 0; i < 3; i++) {
            assertThat(data.get(i).get("object").asText(), is("embedding"));
            assertThat(data.get(i).get("index").asInt(), is(i));
            assertThat(data.get(i).get("embedding").size(), is(1536));
        }
        assertThat(floats(data.get(0).get("embedding")), is(floats(data.get(2).get("embedding"))));
        assertThat(floats(data.get(0).get("embedding")), is(not(floats(data.get(1).get("embedding")))));
        int expectedTokens = EmbeddingVectors.approximateTokens("the cat sat on the mat") * 2
            + EmbeddingVectors.approximateTokens("quarterly financial report");
        assertThat(root.get("usage").get("prompt_tokens").asInt(), is(expectedTokens));
        assertThat(root.get("usage").get("total_tokens").asInt(), is(expectedTokens));
    }

    @Test
    public void shouldFallBackToExpectationModelThenDefaultForOpenAi() throws Exception {
        assertThat(post(Provider.OPENAI, "text-embedding-ada-002", deterministic(), "/v1/embeddings", "{\"input\":\"x\"}")
            .get("model").asText(), is("text-embedding-ada-002"));
        assertThat(post(Provider.OPENAI, null, deterministic(), "/v1/embeddings", "{\"input\":\"x\"}")
            .get("model").asText(), is("text-embedding-3-small"));
    }

    @Test
    public void shouldTreatOpenAiTokenArraysAsInputs() throws Exception {
        assertThat("an array of token integers is one input",
            post(Provider.OPENAI, null, deterministic(), "/v1/embeddings", "{\"input\":[1212,318,257]}").get("data").size(), is(1));
        JsonNode data = post(Provider.OPENAI, null, deterministic(), "/v1/embeddings", "{\"input\":[[1212,318],[257,1332]]}").get("data");
        assertThat("an array of token arrays is one input per array", data.size(), is(2));
        assertThat(floats(data.get(0).get("embedding")), is(not(floats(data.get(1).get("embedding")))));
    }

    @Test
    public void shouldHonourRequestedDimensionsUnlessExpectationSetsThem() throws Exception {
        String body = "{\"model\":\"text-embedding-3-small\",\"input\":\"hello\",\"dimensions\":256}";
        assertThat(post(Provider.OPENAI, null, deterministic(), "/v1/embeddings", body)
            .get("data").get(0).get("embedding").size(), is(256));
        assertThat(post(Provider.OPENAI, null, EmbeddingResponse.embedding().withDimensions(8), "/v1/embeddings", body)
            .get("data").get(0).get("embedding").size(), is(8));
    }

    @Test
    public void shouldReturnBase64Float32WhenOpenAiRequestAsksForIt() throws Exception {
        JsonNode floatRoot = post(Provider.OPENAI, null, deterministic().withDimensions(16), "/v1/embeddings",
            "{\"input\":[\"alpha\",\"beta\"]}");
        JsonNode base64Root = post(Provider.OPENAI, null, deterministic().withDimensions(16), "/v1/embeddings",
            "{\"input\":[\"alpha\",\"beta\"],\"encoding_format\":\"base64\"}");

        for (int i = 0; i < 2; i++) {
            JsonNode encoded = base64Root.get("data").get(i).get("embedding");
            assertThat(encoded.isTextual(), is(true));
            ByteBuffer bytes = ByteBuffer.wrap(Base64.getDecoder().decode(encoded.asText())).order(ByteOrder.LITTLE_ENDIAN);
            JsonNode expected = floatRoot.get("data").get(i).get("embedding");
            assertThat(bytes.remaining(), is(16 * Float.BYTES));
            for (JsonNode value : expected) {
                assertThat((double) bytes.getFloat(), closeTo(value.asDouble(), 1e-6));
            }
        }
    }

    @Test
    public void shouldReturnOpenAiShapeForAzureAndOpenAiCompatibleProviders() throws Exception {
        JsonNode azure = post(Provider.AZURE_OPENAI, "text-embedding-3-small", deterministic(),
            "/openai/deployments/embed/embeddings", "{\"input\":[\"a\",\"b\",\"c\"]}");
        assertThat(azure.get("data").size(), is(3));
        assertThat(azure.get("model").asText(), is("text-embedding-3-small"));

        JsonNode mistral = post(Provider.MISTRAL, null, deterministic(), "/v1/embeddings",
            "{\"model\":\"mistral-embed\",\"input\":[\"a\",\"b\"]}");
        assertThat(mistral.get("data").size(), is(2));
        assertThat(mistral.get("model").asText(), is("mistral-embed"));
    }

    // ---- Gemini ----

    @Test
    public void shouldEmbedGeminiContentPartsAndMatchOtherProvidersForTheSameText() throws Exception {
        EmbeddingResponse embedding = deterministic().withDimensions(32);
        JsonNode gemini = post(Provider.GEMINI, "text-embedding-004", embedding, "/v1beta/models/text-embedding-004:embedContent",
            "{\"model\":\"models/text-embedding-004\",\"content\":{\"parts\":[{\"text\":\"the cat sat on the mat\"}]}}");
        JsonNode openAi = post(Provider.OPENAI, null, embedding, "/v1/embeddings", "{\"input\":\"the cat sat on the mat\"}");

        assertThat(gemini.get("embedding").get("values").size(), is(32));
        assertThat("same text, seed and dimensions give the same vector on every provider",
            floats(gemini.get("embedding").get("values")), is(floats(openAi.get("data").get(0).get("embedding"))));
        assertThat(gemini.get("usageMetadata").get("promptTokenCount").asInt(), is(EmbeddingVectors.approximateTokens("the cat sat on the mat")));
    }

    @Test
    public void shouldGiveDifferentGeminiDocumentsDifferentVectors() throws Exception {
        String path = "/v1beta/models/text-embedding-004:embedContent";
        JsonNode first = post(Provider.GEMINI, null, deterministic(), path, "{\"content\":{\"parts\":[{\"text\":\"first document\"}]}}");
        JsonNode second = post(Provider.GEMINI, null, deterministic(), path, "{\"content\":{\"parts\":[{\"text\":\"an unrelated second text\"}]}}");
        assertThat(floats(first.get("embedding").get("values")), is(not(floats(second.get("embedding").get("values")))));
    }

    @Test
    public void shouldReturnOneGeminiEmbeddingPerBatchRequest() throws Exception {
        String body = "{\"requests\":["
            + "{\"model\":\"models/text-embedding-004\",\"content\":{\"parts\":[{\"text\":\"alpha\"}]}},"
            + "{\"model\":\"models/text-embedding-004\",\"content\":{\"parts\":[{\"text\":\"beta\"}]},\"outputDimensionality\":64},"
            + "{\"model\":\"models/text-embedding-004\",\"content\":{\"parts\":[{\"text\":\"alpha\"}]},\"embedContentConfig\":{\"outputDimensionality\":768}},"
            + "{\"model\":\"models/text-embedding-004\",\"content\":{\"parts\":[{\"text\":\"beta\"}]}}]}";
        JsonNode root = post(Provider.GEMINI, null, deterministic(), "/v1beta/models/text-embedding-004:batchEmbedContents", body);

        assertThat(root.has("embedding"), is(false));
        JsonNode embeddings = root.get("embeddings");
        assertThat(embeddings.size(), is(4));
        assertThat(embeddings.get(0).get("values").size(), is(768));
        assertThat("per-request outputDimensionality", embeddings.get(1).get("values").size(), is(64));
        assertThat(floats(embeddings.get(0).get("values")), is(floats(embeddings.get(2).get("values"))));
        assertThat("each request's own content is embedded",
            floats(embeddings.get(0).get("values")), is(not(floats(embeddings.get(3).get("values")))));
        assertThat(root.get("usageMetadata").get("promptTokenCount").asInt(), greaterThan(0));

        JsonNode byBody = post(Provider.GEMINI, null, deterministic(), "/gateway/gemini", body);
        assertThat("a requests array selects the batch shape on any path", byBody.get("embeddings").size(), is(4));
    }

    // ---- Ollama ----

    @Test
    public void shouldReturnOneOllamaEmbeddingPerApiEmbedInput() throws Exception {
        JsonNode root = post(Provider.OLLAMA, "nomic-embed-text", deterministic(), "/api/embed",
            "{\"model\":\"all-minilm\",\"input\":[\"Why is the sky blue?\",\"Why is the grass green?\",\"Why is the sky blue?\"]}");
        assertThat(root.get("model").asText(), is("all-minilm"));
        JsonNode embeddings = root.get("embeddings");
        assertThat(embeddings.size(), is(3));
        assertThat(floats(embeddings.get(0)), is(floats(embeddings.get(2))));
        assertThat(floats(embeddings.get(0)), is(not(floats(embeddings.get(1)))));
        assertThat(root.get("prompt_eval_count").asInt(), greaterThan(0));
        assertThat(root.get("total_duration").isNumber(), is(true));
        assertThat(root.get("load_duration").isNumber(), is(true));

        assertThat("a string input is one embedding",
            post(Provider.OLLAMA, null, deterministic(), "/api/embed", "{\"model\":\"all-minilm\",\"input\":\"one\"}").get("embeddings").size(), is(1));
    }

    @Test
    public void shouldReturnLegacyOllamaShapeForApiEmbeddingsPrompt() throws Exception {
        JsonNode first = post(Provider.OLLAMA, null, deterministic(), "/api/embeddings",
            "{\"model\":\"all-minilm\",\"prompt\":\"Here is an article about llamas\"}");
        JsonNode second = post(Provider.OLLAMA, null, deterministic(), "/api/embeddings",
            "{\"model\":\"all-minilm\",\"prompt\":\"Quarterly results for the finance team\"}");
        assertThat(first.size(), is(1));
        assertThat(first.get("embedding").size(), is(768));
        assertThat("prompt is read, so different prompts differ",
            floats(first.get("embedding")), is(not(floats(second.get("embedding")))));
        assertThat("the path alone selects the legacy shape",
            post(Provider.OLLAMA, null, deterministic(), "/api/embeddings", "{\"model\":\"all-minilm\"}").has("embedding"), is(true));
    }

    // ---- Bedrock Titan ----

    @Test
    public void shouldReturnTitanG1ShapeForTitanV1() throws Exception {
        JsonNode root = post(Provider.BEDROCK, null, deterministic(), "/model/amazon.titan-embed-text-v1/invoke",
            "{\"inputText\":\"What are the different services that you offer?\"}");
        assertThat(root.get("embedding").size(), is(1536));
        assertThat(root.get("inputTextTokenCount").asInt(), is(EmbeddingVectors.approximateTokens("What are the different services that you offer?")));
        assertThat(root.has("embeddingsByType"), is(false));
    }

    @Test
    public void shouldReturnTitanV2ShapeWithEmbeddingsByType() throws Exception {
        String path = "/model/amazon.titan-embed-text-v2:0/invoke";
        JsonNode root = post(Provider.BEDROCK, null, deterministic(), path, "{\"inputText\":\"hello titan\",\"dimensions\":256}");
        assertThat(root.get("embedding").size(), is(256));
        assertThat(floats(root.get("embeddingsByType").get("float")), is(floats(root.get("embedding"))));
        assertThat(root.get("embeddingsByType").has("binary"), is(false));

        JsonNode other = post(Provider.BEDROCK, null, deterministic(), path, "{\"inputText\":\"a different document\",\"dimensions\":256}");
        assertThat("inputText is read, so different texts differ", floats(other.get("embedding")), is(not(floats(root.get("embedding")))));

        JsonNode binaryOnly = post(Provider.BEDROCK, null, deterministic(), path, "{\"inputText\":\"hello titan\",\"embeddingTypes\":[\"binary\"]}");
        assertThat("embedding is omitted when only binary is requested", binaryOnly.has("embedding"), is(false));
        JsonNode binary = binaryOnly.get("embeddingsByType").get("binary");
        assertThat(binary.size(), is(1024));
        binary.forEach(bit -> assertThat(bit.asInt(), anyOf(is(0), is(1))));

        JsonNode both = post(Provider.BEDROCK, null, deterministic(), path, "{\"inputText\":\"hello titan\",\"embeddingTypes\":[\"float\",\"binary\"]}");
        assertThat(both.get("embeddingsByType").has("float"), is(true));
        assertThat(both.get("embeddingsByType").has("binary"), is(true));
        assertThat(both.has("embedding"), is(true));
    }

    // ---- Bedrock Cohere ----

    @Test
    public void shouldReturnCohereV3ShapeWithOneVectorPerText() throws Exception {
        JsonNode root = post(Provider.BEDROCK, null, deterministic(), "/model/cohere.embed-english-v3/invoke",
            "{\"texts\":[\"hello world\",\"this is a test\",\"hello world\"],\"input_type\":\"search_document\"}");
        assertThat(root.get("id").asText(), not(emptyString()));
        assertThat(root.get("response_type").asText(), is("embeddings_floats"));
        assertThat(root.get("texts").size(), is(3));
        assertThat(root.get("texts").get(1).asText(), is("this is a test"));
        JsonNode embeddings = root.get("embeddings");
        assertThat(embeddings.size(), is(3));
        assertThat(embeddings.get(0).size(), is(1024));
        assertThat(floats(embeddings.get(0)), is(floats(embeddings.get(2))));
        assertThat(floats(embeddings.get(0)), is(not(floats(embeddings.get(1)))));
    }

    @Test
    public void shouldRecogniseRegionPrefixedAndEncodedCohereModelIds() throws Exception {
        for (String path : new String[]{"/model/us.cohere.embed-v4:0/invoke", "/model/eu.cohere.embed-v4%3A0/invoke"}) {
            JsonNode root = post(Provider.BEDROCK, null, deterministic(), path, "{\"texts\":[\"a\",\"b\"],\"input_type\":\"search_query\"}");
            assertThat(path, root.get("response_type").asText(), is("embeddings_floats"));
            assertThat(path, root.get("embeddings").get(0).size(), is(1536));
        }
        JsonNode fromExpectation = post(Provider.BEDROCK, "us.cohere.embed-english-v3", deterministic(), "/bedrock/embed",
            "{\"texts\":[\"a\"],\"input_type\":\"search_query\"}");
        assertThat(fromExpectation.get("embeddings").get(0).size(), is(1024));
    }

    @Test
    public void shouldReturnCohereEmbeddingsByTypeWhenEmbeddingTypesRequested() throws Exception {
        JsonNode root = post(Provider.BEDROCK, null, deterministic(), "/model/cohere.embed-v4:0/invoke",
            "{\"texts\":[\"hello world\",\"this is a test\"],\"input_type\":\"search_document\","
                + "\"embedding_types\":[\"float\",\"int8\",\"uint8\",\"binary\",\"ubinary\"],\"output_dimension\":256}");
        assertThat(root.get("response_type").asText(), is("embeddings_by_type"));
        JsonNode embeddings = root.get("embeddings");
        assertThat(embeddings.isObject(), is(true));
        for (String type : new String[]{"float", "int8", "uint8", "binary", "ubinary"}) {
            assertThat(type, embeddings.get(type).size(), is(2));
        }
        assertThat(embeddings.get("float").get(0).size(), is(256));
        assertThat(embeddings.get("int8").get(0).size(), is(256));
        assertThat("packed binary is one eighth of the float length", embeddings.get("ubinary").get(0).size(), is(32));
        embeddings.get("int8").get(0).forEach(v -> assertThat(v.asInt(), allOf(greaterThanOrEqualTo(-128), lessThanOrEqualTo(127))));
        embeddings.get("uint8").get(0).forEach(v -> assertThat(v.asInt(), allOf(greaterThanOrEqualTo(0), lessThanOrEqualTo(255))));
        embeddings.get("binary").get(0).forEach(v -> assertThat(v.asInt(), allOf(greaterThanOrEqualTo(-128), lessThanOrEqualTo(127))));
        embeddings.get("ubinary").get(0).forEach(v -> assertThat(v.asInt(), allOf(greaterThanOrEqualTo(0), lessThanOrEqualTo(255))));
    }

    @Test
    public void shouldEmbedCohereV4InputsAndEchoThem() throws Exception {
        JsonNode root = post(Provider.BEDROCK, null, deterministic(), "/model/cohere.embed-v4:0/invoke",
            "{\"input_type\":\"search_document\",\"inputs\":["
                + "{\"content\":[{\"type\":\"text\",\"text\":\"first page\"}]},"
                + "{\"content\":[{\"type\":\"text\",\"text\":\"second page\"}]}]}");
        assertThat(root.get("embeddings").size(), is(2));
        assertThat(root.get("inputs").size(), is(2));
        assertThat(root.has("texts"), is(false));
        assertThat(floats(root.get("embeddings").get(0)), is(not(floats(root.get("embeddings").get(1)))));
    }

    @Test
    public void shouldPickBedrockFamilyFromBodyWhenNoEmbeddingModelIsNamed() throws Exception {
        JsonNode cohere = post(Provider.BEDROCK, null, deterministic(), "/bedrock/embed", "{\"texts\":[\"a\",\"b\"]}");
        assertThat(cohere.get("embeddings").size(), is(2));
        assertThat(cohere.get("response_type").asText(), is("embeddings_floats"));

        JsonNode titan = post(Provider.BEDROCK, null, deterministic(), "/bedrock/embed", "{\"inputText\":\"a\"}");
        assertThat(titan.get("embedding").size(), is(1024));
        assertThat(titan.has("embeddingsByType"), is(true));
    }

    private static final String PNG = "\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAfbLI3wAAAABJRU5ErkJggg==\"";
    private static final String JPEG = "\"data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=\"";

    @Test
    public void shouldReturnOneCohereV3VectorForAnImageWithEmptyTexts() throws Exception {
        // Bedrock Cohere Embed v3: one image per call; "texts" comes back empty
        // https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed-v3.html
        String path = "/model/cohere.embed-english-v3/invoke";
        JsonNode root = post(Provider.BEDROCK, null, deterministic(), path,
            "{\"images\":[" + PNG + "],\"input_type\":\"image\",\"embedding_types\":[\"int8\",\"float\"]}");
        assertThat(root.get("response_type").asText(), is("embeddings_by_type"));
        assertThat(root.get("embeddings").get("float").size(), is(1));
        assertThat(root.get("embeddings").get("float").get(0).size(), is(1024));
        assertThat(root.get("embeddings").get("int8").size(), is(1));
        assertThat(root.get("texts").isArray(), is(true));
        assertThat(root.get("texts").size(), is(0));

        JsonNode floats = post(Provider.BEDROCK, null, deterministic(), path, "{\"images\":[" + PNG + "],\"input_type\":\"image\"}");
        assertThat(floats.get("response_type").asText(), is("embeddings_floats"));
        assertThat(floats.get("embeddings").size(), is(1));
        assertThat("the same image gives the same vector", floats(floats.get("embeddings").get(0)), is(floats(root.get("embeddings").get("float").get(0))));

        JsonNode error = rejected(postRaw(Provider.BEDROCK, null, deterministic(), path,
            "{\"images\":[" + PNG + "," + JPEG + "],\"input_type\":\"image\"}"));
        assertThat(error.get("message").asText(), is("images must have at most 1 entries, got 2"));
    }

    @Test
    public void shouldReturnOneCohereV4VectorPerImage() throws Exception {
        // Bedrock Cohere Embed v4: up to 96 images per call
        // https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed-v4.html
        JsonNode root = post(Provider.BEDROCK, null, deterministic(), "/model/cohere.embed-v4:0/invoke",
            "{\"images\":[" + PNG + "," + JPEG + "," + PNG + "],\"input_type\":\"search_document\",\"output_dimension\":256}");
        JsonNode embeddings = root.get("embeddings");
        assertThat(embeddings.size(), is(3));
        assertThat(embeddings.get(0).size(), is(256));
        assertThat(floats(embeddings.get(0)), is(floats(embeddings.get(2))));
        assertThat(floats(embeddings.get(0)), is(not(floats(embeddings.get(1)))));
        assertThat(root.get("texts").size(), is(0));
    }

    @Test
    public void shouldPickCohereForAnImagesBodyWithNoEmbeddingModelNamed() throws Exception {
        JsonNode root = post(Provider.BEDROCK, null, deterministic(), "/bedrock/embed", "{\"images\":[" + PNG + "]}");
        assertThat(root.get("embeddings").size(), is(1));
        assertThat(root.get("embeddings").get(0).size(), is(1024));
    }

    @Test
    public void shouldRejectCohereTextsAndImagesTogether() throws Exception {
        HttpResponse response = postRaw(Provider.BEDROCK, null, deterministic(), "/model/cohere.embed-v4:0/invoke",
            "{\"texts\":[\"a\"],\"images\":[" + PNG + "]}");
        assertThat(rejected(response).get("message").asText(), is("send either texts or images, not both"));
        assertThat(response.getFirstHeader("x-amzn-ErrorType"), is("ValidationException"));
    }

    @Test
    public void shouldRejectAnInputTooLongToHashWithTheProvidersError() throws Exception {
        String atLimit = "{\"input\":[\"" + words(EmbeddingWire.DEFAULT_MAX_INPUT_CHARACTERS) + "\"]}";
        String overLimit = "{\"input\":[\"short\",\"" + words(EmbeddingWire.DEFAULT_MAX_INPUT_CHARACTERS + 1) + "\"]}";
        assertThat(post(Provider.OPENAI, null, deterministic(), "/v1/embeddings", atLimit).get("data").size(), is(1));

        JsonNode error = rejected(postRaw(Provider.OPENAI, null, deterministic(), "/v1/embeddings", overLimit)).get("error");
        assertThat(error.get("type").asText(), is("invalid_request_error"));
        int over = EmbeddingWire.DEFAULT_MAX_INPUT_CHARACTERS + 1;
        assertThat(error.get("message").asText(), is("an input has " + over + " characters; the limit is " + EmbeddingWire.DEFAULT_MAX_INPUT_CHARACTERS + ". Send shorter inputs"));
        assertThat(rejected(postRaw(Provider.GEMINI, null, deterministic(), "/v1beta/models/text-embedding-004:embedContent",
            "{\"content\":{\"parts\":[{\"text\":\"" + words(over) + "\"}]}}")).get("error").get("status").asText(), is("INVALID_ARGUMENT"));
        assertThat(rejected(postRaw(Provider.OLLAMA, null, deterministic(), "/api/embed", overLimit)).get("error").asText(), containsString(String.valueOf(over)));
        assertThat(rejected(postRaw(Provider.BEDROCK, null, deterministic(), "/model/amazon.titan-embed-text-v2:0/invoke",
            "{\"inputText\":\"" + words(over) + "\"}")).get("message").asText(), containsString(String.valueOf(over)));

        assertThat("a random vector does not read the text, so it has no length limit",
            post(Provider.OPENAI, null, EmbeddingResponse.embedding(), "/v1/embeddings", overLimit).get("data").size(), is(2));
    }

    @Test
    public void shouldFailAnExpectationWithDimensionsBelowOneAsAMockFault() throws Exception {
        for (int dimensions : new int[]{0, -1}) {
            for (EmbeddingResponse embedding : new EmbeddingResponse[]{deterministic().withDimensions(dimensions), EmbeddingResponse.embedding().withDimensions(dimensions)}) {
                for (String[] call : new String[][]{
                    {"OPENAI", "/v1/embeddings", "{\"input\":\"x\"}"},
                    {"GEMINI", "/v1beta/models/text-embedding-004:embedContent", "{\"content\":{\"parts\":[{\"text\":\"x\"}]}}"},
                    {"OLLAMA", "/api/embeddings", "{\"prompt\":\"x\"}"},
                    {"BEDROCK", "/model/cohere.embed-v4:0/invoke", "{\"texts\":[\"x\"]}"}}) {
                    HttpResponse response = postRaw(Provider.valueOf(call[0]), null, embedding, call[1], call[2]);
                    assertThat(call[0] + " " + dimensions, response.getStatusCode(), is(502));
                }
            }
        }
    }

    /** Text of exactly {@code length} characters, a few words repeated so hashing it stays cheap. */
    private static String words(int length) {
        StringBuilder text = new StringBuilder(length + 8);
        for (int i = 0; text.length() < length; i++) {
            text.append('w').append(i % 10).append(' ');
        }
        text.setLength(length);
        return text.toString();
    }

    // ---- Request limits ----

    private HttpResponse postRaw(Provider provider, String model, EmbeddingResponse embedding, String path, String body) {
        return handler.handle(
            llmResponse().withProvider(provider).withModel(model).withEmbedding(embedding),
            request().withMethod("POST").withPath(path).withBody(body)
        );
    }

    private JsonNode rejected(HttpResponse response) throws Exception {
        assertThat(response.getBodyAsString(), response.getStatusCode(), is(400));
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
        return OBJECT_MAPPER.readTree(response.getBodyAsString());
    }

    private static String inputs(int count) {
        StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            array.append(i > 0 ? "," : "").append("\"doc ").append(i).append('"');
        }
        return array.append(']').toString();
    }

    @Test
    public void shouldRejectRequestedDimensionsOutsideTheLimitWithAnOpenAiError() throws Exception {
        for (String dimensions : new String[]{"8193", "5000000", "2147483647", "0", "-1", "1.5", "\"256\""}) {
            JsonNode error = rejected(postRaw(Provider.OPENAI, null, deterministic(), "/v1/embeddings",
                "{\"input\":\"x\",\"dimensions\":" + dimensions + "}")).get("error");
            assertThat(dimensions, error.get("type").asText(), is("invalid_request_error"));
            assertThat(dimensions, error.get("param").asText(), is("dimensions"));
            assertThat(dimensions, error.get("code").isNull(), is(true));
        }
        assertThat(post(Provider.OPENAI, null, deterministic(), "/v1/embeddings", "{\"input\":\"x\",\"dimensions\":8192}")
            .get("data").get(0).get("embedding").size(), is(8192));
    }

    @Test
    public void shouldRejectMoreThan2048OpenAiInputs() throws Exception {
        EmbeddingResponse small = deterministic().withDimensions(4);
        assertThat(post(Provider.OPENAI, null, small, "/v1/embeddings", "{\"input\":" + inputs(2048) + "}").get("data").size(), is(2048));
        JsonNode error = rejected(postRaw(Provider.OPENAI, null, small, "/v1/embeddings", "{\"input\":" + inputs(2049) + "}")).get("error");
        assertThat(error.get("param").asText(), is("input"));
        rejected(postRaw(Provider.AZURE_OPENAI, null, small, "/openai/deployments/e/embeddings", "{\"input\":" + inputs(2049) + "}"));
        rejected(postRaw(Provider.MISTRAL, null, small, "/v1/embeddings", "{\"input\":" + inputs(2049) + "}"));
    }

    /** {@code count} texts of {@code characters} characters each, as a JSON array. */
    private static String texts(int count, int characters) {
        StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            array.append(i > 0 ? "," : "").append('"');
            for (int c = 0; c < characters; c++) {
                array.append('a');
            }
            array.append('"');
        }
        return array.append(']').toString();
    }

    /** Token arrays of the given lengths, as a JSON array of arrays. */
    private static String tokenArrays(int... lengths) {
        StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < lengths.length; i++) {
            array.append(i > 0 ? ",[" : "[");
            for (int t = 0; t < lengths[i]; t++) {
                array.append(t > 0 ? "," : "").append(1212);
            }
            array.append(']');
        }
        return array.append(']').toString();
    }

    /** Token arrays totalling {@code total} tokens, as a JSON array of arrays of at most 8,100 tokens each. */
    private static String tokenArraysTotalling(int total) {
        int[] lengths = new int[(total + 8_099) / 8_100];
        Arrays.fill(lengths, 8_100);
        lengths[lengths.length - 1] = total - 8_100 * (lengths.length - 1);
        return tokenArrays(lengths);
    }

    /** {@code inputs}, a JSON array, with {@code texts} more appended. */
    private static String plus(String inputs, String... texts) {
        StringBuilder array = new StringBuilder(inputs.substring(0, inputs.length() - 1));
        for (String text : texts) {
            array.append(",\"").append(text).append('"');
        }
        return array.append(']').toString();
    }

    @Test
    public void shouldRejectOpenAiTokenArraysTotallingMoreThan300000TokensWithOpenAisError() throws Exception {
        EmbeddingResponse small = EmbeddingResponse.embedding().withDimensions(4);
        JsonNode atLimit = post(Provider.OPENAI, null, small, "/v1/embeddings", "{\"input\":" + tokenArraysTotalling(300_000) + "}");
        assertThat(atLimit.get("data").size(), is(38));
        assertThat(atLimit.get("usage").get("prompt_tokens").asInt(), is(300_000));
        assertThat(atLimit.get("usage").get("total_tokens").asInt(), is(300_000));

        String overLimit = "{\"input\":" + tokenArraysTotalling(300_001) + "}";
        JsonNode error = rejected(postRaw(Provider.OPENAI, null, small, "/v1/embeddings", overLimit)).get("error");
        assertThat(error.get("message").asText(), is("Requested 300001 tokens, max 300000 tokens per request"));
        assertThat(error.get("type").asText(), is("max_tokens_per_request"));
        assertThat(error.get("code").asText(), is("max_tokens_per_request"));
        assertThat(error.get("param").isNull(), is(true));

        JsonNode azure = rejected(postRaw(Provider.AZURE_OPENAI, null, small, "/openai/deployments/e/embeddings", overLimit)).get("error");
        assertThat(azure.get("type").asText(), is("max_tokens_per_request"));
        assertThat("the limit is OpenAI's own, not every OpenAI-compatible provider's",
            post(Provider.MISTRAL, null, small, "/v1/embeddings", overLimit).get("data").size(), is(38));
    }

    @Test
    public void shouldNotRejectOpenAiTextsWhoseEstimateIsOverTheTokenLimit() throws Exception {
        EmbeddingResponse small = EmbeddingResponse.embedding().withDimensions(4);
        // 300 texts of 4,000 characters and one of 4 estimate 300,001 tokens, but a tokeniser
        // may well fit them in 300,000, so the real API could accept them
        JsonNode accepted = post(Provider.OPENAI, null, small, "/v1/embeddings", "{\"input\":" + plus(texts(300, 4000), "abcd") + "}");
        assertThat(accepted.get("data").size(), is(301));
        assertThat(accepted.get("usage").get("prompt_tokens").asInt(), is(300_001));
    }

    @Test
    public void shouldCountEachOpenAiTextAsAtLeastOneTokenTowardsTheLimit() throws Exception {
        EmbeddingResponse small = EmbeddingResponse.embedding().withDimensions(4);
        String arrays = tokenArraysTotalling(299_999);
        // one text makes at least 300,000 tokens, which fits; a second makes at least 300,001
        JsonNode accepted = post(Provider.OPENAI, null, small, "/v1/embeddings", "{\"input\":" + plus(arrays, "abcdefgh") + "}");
        assertThat(accepted.get("data").size(), is(39));
        assertThat("usage counts the text at the codec's estimate", accepted.get("usage").get("prompt_tokens").asInt(), is(300_001));

        JsonNode error = rejected(postRaw(Provider.OPENAI, null, small, "/v1/embeddings",
            "{\"input\":" + plus(arrays, "abcdefgh", "abcdefgh") + "}")).get("error");
        assertThat(error.get("message").asText(), is("Requested 300003 tokens, max 300000 tokens per request"));
    }

    @Test
    public void shouldCountASingleOpenAiTokenArrayByItsLength() throws Exception {
        assertThat(post(Provider.OPENAI, null, EmbeddingResponse.embedding().withDimensions(4), "/v1/embeddings",
            "{\"input\":[100257,100258,100259]}").get("usage").get("prompt_tokens").asInt(), is(3));
    }

    /** One token array of {@code length} tokens, as a JSON array of integers. */
    private static String tokenArray(int length) {
        String nested = tokenArrays(length);
        return nested.substring(1, nested.length() - 1);
    }

    private static final String OVER_INPUT_LIMIT_MESSAGE = "This model's maximum context length is 8192 tokens, however you requested 8193 tokens"
        + " (8193 in your prompt; 0 for the completion). Please reduce your prompt; or completion length.";

    @Test
    public void shouldRejectASingleOpenAiTokenArrayOverTheInputLimitWithOpenAisError() throws Exception {
        EmbeddingResponse small = EmbeddingResponse.embedding().withDimensions(4);
        JsonNode atLimit = post(Provider.OPENAI, null, small, "/v1/embeddings", "{\"input\":" + tokenArray(8192) + "}");
        assertThat(atLimit.get("data").size(), is(1));
        assertThat(atLimit.get("usage").get("prompt_tokens").asInt(), is(8192));

        String overLimit = "{\"input\":" + tokenArray(8193) + "}";
        JsonNode error = rejected(postRaw(Provider.OPENAI, null, small, "/v1/embeddings", overLimit)).get("error");
        assertThat(error.get("message").asText(), is(OVER_INPUT_LIMIT_MESSAGE));
        assertThat(error.get("type").asText(), is("invalid_request_error"));
        assertThat(error.get("param").isNull(), is(true));
        assertThat(error.get("code").isNull(), is(true));

        JsonNode azure = rejected(postRaw(Provider.AZURE_OPENAI, null, small, "/openai/deployments/e/embeddings", overLimit)).get("error");
        assertThat(azure.get("message").asText(), is(OVER_INPUT_LIMIT_MESSAGE));
        assertThat("the limit is OpenAI's own, not every OpenAI-compatible provider's",
            post(Provider.MISTRAL, null, small, "/v1/embeddings", overLimit).get("data").size(), is(1));
    }

    @Test
    public void shouldRejectTheFirstOpenAiTokenArrayInABatchOverTheInputLimit() throws Exception {
        EmbeddingResponse small = EmbeddingResponse.embedding().withDimensions(4);
        assertThat(post(Provider.OPENAI, null, small, "/v1/embeddings", "{\"input\":" + plus(tokenArrays(8192, 3, 8192), "abc") + "}")
            .get("data").size(), is(4));

        JsonNode error = rejected(postRaw(Provider.OPENAI, null, small, "/v1/embeddings",
            "{\"input\":" + plus(tokenArrays(3, 8193, 9000), "abc") + "}")).get("error");
        assertThat(error.get("message").asText(), is(OVER_INPUT_LIMIT_MESSAGE));
        assertThat(error.get("type").asText(), is("invalid_request_error"));
    }

    @Test
    public void shouldNotRejectAnOpenAiTextWhoseEstimateIsOverTheInputLimit() throws Exception {
        // 40,000 characters estimate 10,000 tokens, but only a tokeniser could tell the real count
        JsonNode accepted = post(Provider.OPENAI, null, EmbeddingResponse.embedding().withDimensions(4), "/v1/embeddings",
            "{\"input\":" + texts(1, 40_000) + "}");
        assertThat(accepted.get("data").size(), is(1));
        assertThat(accepted.get("usage").get("prompt_tokens").asInt(), is(10_000));
    }

    @Test
    public void shouldRejectResponsesOverTheTotalValueLimit() throws Exception {
        JsonNode error = rejected(postRaw(Provider.OPENAI, null, deterministic(), "/v1/embeddings",
            "{\"input\":" + inputs(1000) + ",\"dimensions\":8192}")).get("error");
        assertThat(error.get("message").asText(), containsString("limit"));
        rejected(postRaw(Provider.OPENAI, null, deterministic().withDimensions(10_000_000), "/v1/embeddings", "{\"input\":\"x\"}"));
        rejected(postRaw(Provider.OLLAMA, null, deterministic().withDimensions(10_000), "/api/embed", "{\"input\":" + inputs(500) + "}"));
    }

    @Test
    public void shouldAcceptExactlyTheTotalValueLimitAndRejectOneDimensionOver() throws Exception {
        // 2048 inputs x 128 dimensions = 262,144 values
        JsonNode atLimit = post(Provider.OPENAI, null, deterministic(), "/v1/embeddings", "{\"input\":" + inputs(2048) + ",\"dimensions\":128}");
        assertThat(atLimit.get("data").size(), is(2048));
        assertThat(atLimit.get("data").get(2047).get("embedding").size(), is(128));
        JsonNode error = rejected(postRaw(Provider.OPENAI, null, deterministic(), "/v1/embeddings",
            "{\"input\":" + inputs(2048) + ",\"dimensions\":129}")).get("error");
        assertThat(error.get("type").asText(), is("invalid_request_error"));
        assertThat(error.get("message").asText(), allOf(containsString("264192"), containsString("262144")));

        // 32 inputs x 8192 dimensions is the limit too; a 33rd input is over it
        assertThat(post(Provider.OPENAI, null, deterministic(), "/v1/embeddings", "{\"input\":" + inputs(32) + ",\"dimensions\":8192}")
            .get("data").size(), is(32));
        rejected(postRaw(Provider.OPENAI, null, deterministic(), "/v1/embeddings", "{\"input\":" + inputs(33) + ",\"dimensions\":8192}"));
    }

    @Test
    public void shouldApplyTheTotalValueLimitToExpectationDimensions() throws Exception {
        assertThat(post(Provider.OPENAI, null, deterministic().withDimensions(262_144), "/v1/embeddings", "{\"input\":\"x\"}")
            .get("data").get(0).get("embedding").size(), is(262_144));
        rejected(postRaw(Provider.OPENAI, null, deterministic().withDimensions(262_145), "/v1/embeddings", "{\"input\":\"x\"}"));

        String gemini = "/v1beta/models/text-embedding-004:embedContent";
        assertThat(post(Provider.GEMINI, null, deterministic().withDimensions(262_144), gemini, "{\"content\":{\"parts\":[{\"text\":\"x\"}]}}")
            .get("embedding").get("values").size(), is(262_144));
        rejected(postRaw(Provider.GEMINI, null, deterministic().withDimensions(262_145), gemini, "{\"content\":{\"parts\":[{\"text\":\"x\"}]}}"));
        rejected(postRaw(Provider.OLLAMA, null, deterministic().withDimensions(262_145), "/api/embeddings", "{\"prompt\":\"x\"}"));
        rejected(postRaw(Provider.BEDROCK, null, deterministic().withDimensions(262_145), "/model/amazon.titan-embed-text-v1/invoke", "{\"inputText\":\"x\"}"));
    }

    @Test
    public void shouldAllowFourTimesAsManyBase64ValuesAsJsonNumbers() throws Exception {
        // 2048 inputs x 512 dimensions = 1,048,576 values
        String atBase64Limit = "{\"input\":" + inputs(2048) + ",\"dimensions\":512";
        JsonNode base64 = post(Provider.OPENAI, null, deterministic(), "/v1/embeddings", atBase64Limit + ",\"encoding_format\":\"base64\"}");
        assertThat(base64.get("data").size(), is(2048));
        assertThat(Base64.getDecoder().decode(base64.get("data").get(2047).get("embedding").asText()).length, is(512 * Float.BYTES));

        JsonNode error = rejected(postRaw(Provider.OPENAI, null, deterministic(), "/v1/embeddings",
            "{\"input\":" + inputs(2048) + ",\"dimensions\":513,\"encoding_format\":\"base64\"}")).get("error");
        assertThat(error.get("message").asText(), allOf(containsString("1050624"), containsString("1048576")));
        rejected(postRaw(Provider.OPENAI, null, deterministic(), "/v1/embeddings", atBase64Limit + "}"));
        rejected(postRaw(Provider.OPENAI, null, deterministic(), "/v1/embeddings", atBase64Limit + ",\"encoding_format\":\"float\"}"));
    }

    /** A batch of {@code count} requests asking for {@code dimensions} each (0 for none), the last for {@code lastDimensions}. */
    private static String geminiRequests(int count, int dimensions, int lastDimensions) {
        StringBuilder requests = new StringBuilder("{\"requests\":[");
        for (int i = 0; i < count; i++) {
            int size = i == count - 1 ? lastDimensions : dimensions;
            requests.append(i > 0 ? "," : "").append("{\"content\":{\"parts\":[{\"text\":\"d").append(i).append("\"}]}")
                .append(size > 0 ? ",\"outputDimensionality\":" + size : "").append('}');
        }
        return requests.append("]}").toString();
    }

    @Test
    public void shouldRejectGeminiLimitsWithAGoogleError() throws Exception {
        String embedContent = "/v1beta/models/text-embedding-004:embedContent";
        assertThat(post(Provider.GEMINI, null, deterministic(), embedContent,
            "{\"content\":{\"parts\":[{\"text\":\"x\"}]},\"outputDimensionality\":8192}").get("embedding").get("values").size(), is(8192));
        JsonNode error = rejected(postRaw(Provider.GEMINI, null, deterministic(), embedContent,
            "{\"content\":{\"parts\":[{\"text\":\"x\"}]},\"outputDimensionality\":8193}")).get("error");
        assertThat(error.get("code").asInt(), is(400));
        assertThat(error.get("status").asText(), is("INVALID_ARGUMENT"));
        assertThat(error.get("message").asText(), containsString("outputDimensionality"));
        rejected(postRaw(Provider.GEMINI, null, deterministic(), embedContent,
            "{\"content\":{\"parts\":[{\"text\":\"x\"}]},\"embedContentConfig\":{\"outputDimensionality\":2147483647}}"));

        String batch = "/v1beta/models/m:batchEmbedContents";
        assertThat(post(Provider.GEMINI, null, deterministic().withDimensions(4), batch, geminiRequests(100, 0, 0)).get("embeddings").size(), is(100));
        JsonNode tooMany = rejected(postRaw(Provider.GEMINI, null, deterministic().withDimensions(4), batch, geminiRequests(101, 0, 0))).get("error");
        assertThat(tooMany.get("code").asInt(), is(400));
        assertThat(tooMany.get("status").asText(), is("INVALID_ARGUMENT"));
        assertThat(tooMany.get("message").asText(), is("* BatchEmbedContentsRequest.requests: at most 100 requests can be in one batch"));
    }

    @Test
    public void shouldSumEachGeminiBatchRequestsDimensionsAgainstTheTotalValueLimit() throws Exception {
        String batch = "/v1beta/models/m:batchEmbedContents";
        // 99 requests at 2,621 dimensions and one at 2,665 = 262,144 values; the last at 2,666 makes 262,145
        JsonNode embeddings = post(Provider.GEMINI, null, deterministic(), batch, geminiRequests(100, 2621, 2665)).get("embeddings");
        assertThat(embeddings.size(), is(100));
        assertThat(embeddings.get(99).get("values").size(), is(2665));

        JsonNode error = rejected(postRaw(Provider.GEMINI, null, deterministic(), batch, geminiRequests(100, 2621, 2666))).get("error");
        assertThat(error.get("message").asText(), allOf(containsString("262145"), containsString("262144")));
    }

    @Test
    public void shouldRejectOllamaLimitsWithAnOllamaError() throws Exception {
        assertThat(post(Provider.OLLAMA, null, deterministic(), "/api/embed", "{\"input\":\"x\",\"dimensions\":8192}")
            .get("embeddings").get(0).size(), is(8192));
        JsonNode error = rejected(postRaw(Provider.OLLAMA, null, deterministic(), "/api/embed", "{\"input\":\"x\",\"dimensions\":8193}"));
        assertThat(error.get("error").asText(), containsString("dimensions"));
        rejected(postRaw(Provider.OLLAMA, null, deterministic(), "/api/embeddings", "{\"prompt\":\"x\",\"dimensions\":8193}"));

        assertThat(post(Provider.OLLAMA, null, deterministic(), "/api/embed", "{\"input\":" + inputs(2048) + ",\"dimensions\":128}")
            .get("embeddings").size(), is(2048));
        rejected(postRaw(Provider.OLLAMA, null, deterministic(), "/api/embed", "{\"input\":" + inputs(2048) + ",\"dimensions\":129}"));
        rejected(postRaw(Provider.OLLAMA, null, deterministic().withDimensions(4), "/api/embed", "{\"input\":" + inputs(2049) + "}"));
    }

    @Test
    public void shouldApplyCohereLimitsWithABedrockValidationError() throws Exception {
        String path = "/model/cohere.embed-v4:0/invoke";
        assertThat(post(Provider.BEDROCK, null, deterministic().withDimensions(4), path, "{\"texts\":" + inputs(96) + "}")
            .get("embeddings").size(), is(96));
        HttpResponse tooMany = postRaw(Provider.BEDROCK, null, deterministic().withDimensions(4), path, "{\"texts\":" + inputs(97) + "}");
        assertThat(rejected(tooMany).get("message").asText(), containsString("texts"));
        assertThat(tooMany.getFirstHeader("x-amzn-ErrorType"), is("ValidationException"));

        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 97; i++) {
            items.append(i > 0 ? "," : "").append("{\"content\":[{\"type\":\"text\",\"text\":\"d\"}]}");
        }
        rejected(postRaw(Provider.BEDROCK, null, deterministic().withDimensions(4), path, "{\"inputs\":[" + items + "]}"));

        rejected(postRaw(Provider.BEDROCK, null, deterministic(), path, "{\"texts\":[\"a\"],\"output_dimension\":300}"));
        assertThat(post(Provider.BEDROCK, null, deterministic(), path, "{\"texts\":[\"a\"],\"output_dimension\":512}")
            .get("embeddings").get(0).size(), is(512));
    }

    @Test
    public void shouldCountEveryCohereEmbeddingTypeAgainstTheTotalValueLimit() throws Exception {
        String path = "/model/cohere.embed-v4:0/invoke";
        // 96 texts x 1024 dimensions = 98,304 values per type: two types fit, three do not
        String batch = "{\"texts\":" + inputs(96) + ",\"output_dimension\":1024,\"embedding_types\":";
        JsonNode twoTypes = post(Provider.BEDROCK, null, deterministic(), path, batch + "[\"float\",\"int8\"]}").get("embeddings");
        assertThat(twoTypes.get("int8").size(), is(96));
        assertThat(twoTypes.get("int8").get(95).size(), is(1024));
        HttpResponse threeTypes = postRaw(Provider.BEDROCK, null, deterministic(), path, batch + "[\"float\",\"int8\",\"uint8\"]}");
        assertThat(rejected(threeTypes).get("message").asText(), allOf(containsString("294912"), containsString("262144")));
        assertThat(threeTypes.getFirstHeader("x-amzn-ErrorType"), is("ValidationException"));

        // a repeated type is one type
        assertThat(post(Provider.BEDROCK, null, deterministic(), path, batch + "[\"float\",\"float\",\"float\",\"int8\"]}").get("embeddings").size(), is(2));
        // the largest Cohere batch at the default 1536 dimensions fits as one type
        assertThat(post(Provider.BEDROCK, null, deterministic(), path, "{\"texts\":" + inputs(96) + "}").get("embeddings").get(95).size(), is(1536));
    }

    @Test
    public void shouldRejectUnknownEmbeddingTypes() throws Exception {
        String cohere = "/model/cohere.embed-v4:0/invoke";
        for (String types : new String[]{"[\"float\",\"float16\"]", "[1]", "[null]", "[[\"float\"]]"}) {
            HttpResponse response = postRaw(Provider.BEDROCK, null, deterministic(), cohere, "{\"texts\":[\"a\"],\"embedding_types\":" + types + "}");
            assertThat(types, rejected(response).get("message").asText(), containsString("embedding_types"));
            assertThat(types, response.getBodyAsString(), not(containsString("float16")));
        }
        String titan = "/model/amazon.titan-embed-text-v2:0/invoke";
        assertThat(rejected(postRaw(Provider.BEDROCK, null, deterministic(), titan, "{\"inputText\":\"a\",\"embeddingTypes\":[\"int8\"]}"))
            .get("message").asText(), containsString("embeddingTypes"));
    }

    @Test
    public void shouldCountEveryTitanV2ArrayAgainstTheTotalValueLimit() throws Exception {
        String path = "/model/amazon.titan-embed-text-v2:0/invoke";
        // float is returned twice (embedding and embeddingsByType.float): 2 x 131,072 = 262,144 values
        JsonNode atLimit = post(Provider.BEDROCK, null, deterministic().withDimensions(131_072), path, "{\"inputText\":\"x\"}");
        assertThat(atLimit.get("embedding").size(), is(131_072));
        assertThat(atLimit.get("embeddingsByType").get("float").size(), is(131_072));
        rejected(postRaw(Provider.BEDROCK, null, deterministic().withDimensions(131_073), path, "{\"inputText\":\"x\"}"));
        rejected(postRaw(Provider.BEDROCK, null, deterministic().withDimensions(131_072), path,
            "{\"inputText\":\"x\",\"embeddingTypes\":[\"float\",\"binary\"]}"));
        assertThat(post(Provider.BEDROCK, null, deterministic().withDimensions(262_144), path,
            "{\"inputText\":\"x\",\"embeddingTypes\":[\"binary\"]}").get("embeddingsByType").get("binary").size(), is(262_144));
    }

    @Test
    public void shouldOnlyAcceptTitanV2Dimensions256512Or1024() throws Exception {
        String path = "/model/amazon.titan-embed-text-v2:0/invoke";
        for (int dimensions : new int[]{256, 512, 1024}) {
            assertThat(post(Provider.BEDROCK, null, deterministic(), path, "{\"inputText\":\"x\",\"dimensions\":" + dimensions + "}")
                .get("embedding").size(), is(dimensions));
        }
        for (String dimensions : new String[]{"300", "2048", "5000000"}) {
            rejected(postRaw(Provider.BEDROCK, null, deterministic(), path, "{\"inputText\":\"x\",\"dimensions\":" + dimensions + "}"));
        }
    }

    @Test
    public void shouldReturnTitanMultimodalShapeForTitanEmbedImage() throws Exception {
        String path = "/model/amazon.titan-embed-image-v1/invoke";
        JsonNode root = post(Provider.BEDROCK, null, deterministic(), path, "{\"inputText\":\"a red bicycle\"}");
        assertThat(root.get("embedding").size(), is(1024));
        assertThat(root.get("inputTextTokenCount").asInt(), is(EmbeddingVectors.approximateTokens("a red bicycle")));
        assertThat(root.has("embeddingsByType"), is(false));
        assertThat(post(Provider.BEDROCK, null, deterministic(), path,
            "{\"inputText\":\"a red bicycle\",\"embeddingConfig\":{\"outputEmbeddingLength\":384}}").get("embedding").size(), is(384));
        rejected(postRaw(Provider.BEDROCK, null, deterministic(), path,
            "{\"inputText\":\"x\",\"embeddingConfig\":{\"outputEmbeddingLength\":512}}"));
    }
}
