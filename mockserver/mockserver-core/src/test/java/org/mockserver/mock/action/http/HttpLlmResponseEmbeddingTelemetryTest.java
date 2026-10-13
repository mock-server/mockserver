package org.mockserver.mock.action.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.metrics.Metrics;
import org.mockserver.model.EmbeddingResponse;
import org.mockserver.model.HttpLlmResponse;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.RerankResponse;
import org.mockserver.telemetry.GenAiSpanExporter;

import java.util.List;
import java.util.stream.Collectors;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;

/**
 * A mocked embeddings or rerank response is counted like a forwarded one: one GenAI span with the
 * {@code embeddings} or {@code rerank} operation, and token metrics carrying the input tokens the
 * provider's codec put in the served body, read here from that provider's own usage field, or for a
 * Bedrock body with none from the InvokeModel input token count header.
 * <p>
 * Mutates process-wide state (the GenAI tracer and the LLM metrics), so it runs in the sequential
 * surefire phase.
 */
public class HttpLlmResponseEmbeddingTelemetryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private InMemorySpanExporter spans;
    private GenAiSpanExporter exporter;
    private HttpLlmResponseActionHandler handler;
    private boolean originalMetricsEnabled;
    private boolean originalLlmMetricsEnabled;

    @Before
    public void setUp() {
        originalMetricsEnabled = ConfigurationProperties.metricsEnabled();
        originalLlmMetricsEnabled = ConfigurationProperties.llmMetricsEnabled();
        Metrics.resetAdditionalMetricsForTesting();
        ConfigurationProperties.metricsEnabled(true);
        ConfigurationProperties.llmMetricsEnabled(true);
        Configuration configuration = new Configuration().metricsEnabled(true).llmMetricsEnabled(true);
        new Metrics(configuration);
        assertThat(Metrics.isLlmMetricsActive(), is(true));
        spans = InMemorySpanExporter.create();
        exporter = GenAiSpanExporter.startWithProcessor(SimpleSpanProcessor.create(spans));
        handler = new HttpLlmResponseActionHandler(new MockServerLogger(HttpLlmResponseEmbeddingTelemetryTest.class), configuration);
    }

    @After
    public void tearDown() {
        exporter.stop();
        ConfigurationProperties.metricsEnabled(originalMetricsEnabled);
        ConfigurationProperties.llmMetricsEnabled(originalLlmMetricsEnabled);
        Metrics.resetAdditionalMetricsForTesting();
    }

    @Test
    public void shouldCountOpenAiEmbeddingsFromUsagePromptTokens() throws Exception {
        HttpResponse served = serve(embedding(Provider.OPENAI), request("/v1/embeddings").withMethod("POST")
            .withBody("{\"model\":\"text-embedding-3-small\",\"input\":[\"the quick brown fox\",\"jumps over the lazy dog\"]}"));

        assertCounted(served, "embeddings", "openai", Provider.OPENAI, "text-embedding-3-small", "/usage/prompt_tokens");
    }

    @Test
    public void shouldCountEveryOpenAiCompatibleProvidersEmbeddingsFromUsagePromptTokens() throws Exception {
        Provider[] providers = {Provider.AZURE_OPENAI, Provider.MISTRAL, Provider.XAI, Provider.DEEPSEEK,
            Provider.GROQ, Provider.OPENROUTER, Provider.ORCAROUTER};
        for (Provider provider : providers) {
            spans.reset();
            String model = "embed-model-" + provider.name().toLowerCase();
            HttpResponse served = serve(embedding(provider), request("/v1/embeddings").withMethod("POST")
                .withBody("{\"model\":\"" + model + "\",\"input\":\"a sentence long enough to cost several tokens\"}"));

            assertCounted(served, "embeddings", expectedSystem(provider), provider, model, "/usage/prompt_tokens");
        }
    }

    @Test
    public void shouldCountGeminiEmbedContentFromUsageMetadataWithTheModelFromThePath() throws Exception {
        HttpResponse served = serve(embedding(Provider.GEMINI), request("/v1beta/models/text-embedding-004:embedContent").withMethod("POST")
            .withBody("{\"content\":{\"parts\":[{\"text\":\"what is the meaning of life\"}]}}"));

        assertCounted(served, "embeddings", "gcp.gemini", Provider.GEMINI, "text-embedding-004", "/usageMetadata/promptTokenCount");
    }

    @Test
    public void shouldCountGeminiBatchEmbedContentsFromUsageMetadata() throws Exception {
        HttpResponse served = serve(embedding(Provider.GEMINI), request("/v1beta/models/gemini-embedding-001:batchEmbedContents").withMethod("POST")
            .withBody("{\"requests\":[{\"content\":{\"parts\":[{\"text\":\"first input text\"}]}},"
                + "{\"content\":{\"parts\":[{\"text\":\"second input text\"}]}}]}"));

        assertCounted(served, "embeddings", "gcp.gemini", Provider.GEMINI, "gemini-embedding-001", "/usageMetadata/promptTokenCount");
    }

    @Test
    public void shouldCountOllamaEmbedFromPromptEvalCount() throws Exception {
        HttpResponse served = serve(embedding(Provider.OLLAMA), request("/api/embed").withMethod("POST")
            .withBody("{\"model\":\"all-minilm\",\"input\":[\"why is the sky blue\",\"why is the grass green\"]}"));

        assertCounted(served, "embeddings", "ollama", Provider.OLLAMA, "all-minilm", "/prompt_eval_count");
    }

    @Test
    public void shouldRecordASpanWithNoTokensForTheLegacyOllamaEmbeddingsEndpoint() throws Exception {
        HttpResponse served = serve(embedding(Provider.OLLAMA), request("/api/embeddings").withMethod("POST")
            .withBody("{\"model\":\"all-minilm\",\"prompt\":\"why is the sky blue\"}"));

        assertCountedWithoutTokens(served, "embeddings", "ollama", Provider.OLLAMA, "all-minilm");
    }

    @Test
    public void shouldCountBedrockTitanEmbeddingsFromInputTextTokenCountWithTheModelFromThePath() throws Exception {
        HttpResponse served = serve(embedding(Provider.BEDROCK), request("/model/amazon.titan-embed-text-v2:0/invoke").withMethod("POST")
            .withBody("{\"inputText\":\"a sentence for titan to embed\"}"));

        assertCounted(served, "embeddings", "aws.bedrock", Provider.BEDROCK, "amazon.titan-embed-text-v2:0", "/inputTextTokenCount");
    }

    @Test
    public void shouldCountBedrockCohereEmbeddingsFromTheInputTokenCountHeader() {
        HttpResponse served = serve(embedding(Provider.BEDROCK), request("/model/cohere.embed-english-v3/invoke").withMethod("POST")
            .withBody("{\"texts\":[\"a sentence for cohere to embed\",\"and another one\"],\"input_type\":\"search_document\"}"));

        assertThat(served.getStatusCode(), is(200));
        assertThat(served.getBodyAsString(), not(containsString("token")));
        long tokens = Long.parseLong(served.getFirstHeader("X-Amzn-Bedrock-Input-Token-Count"));
        assertThat(tokens, greaterThan(0L));
        SpanData span = singleSpan("embeddings", "aws.bedrock", "cohere.embed-english-v3");
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(tokens));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.output_tokens")), nullValue());
        assertThat(Metrics.getLlmInputTokens(Provider.BEDROCK.name(), "cohere.embed-english-v3"), is(tokens));
        assertThat(Metrics.getLlmOutputTokens(Provider.BEDROCK.name(), "cohere.embed-english-v3"), is(0L));
    }

    @Test
    public void shouldCountVoyageRerankFromUsageTotalTokens() throws Exception {
        HttpResponse served = serve(rerank(Provider.VOYAGE), request("/v1/rerank").withMethod("POST")
            .withBody("{\"model\":\"rerank-2-lite\",\"query\":\"which document mentions foxes\","
                + "\"documents\":[\"the quick brown fox\",\"a lazy dog sleeps\"]}"));

        assertCounted(served, "rerank", "voyage", Provider.VOYAGE, "rerank-2-lite", "/usage/total_tokens");
    }

    @Test
    public void shouldRecordARerankSpanWithNoTokensForCohereWhichBillsSearchUnits() throws Exception {
        HttpResponse served = serve(rerank(Provider.COHERE), request("/v2/rerank").withMethod("POST")
            .withBody("{\"model\":\"rerank-v3.5\",\"query\":\"which document mentions foxes\","
                + "\"documents\":[\"the quick brown fox\",\"a lazy dog sleeps\"]}"));

        assertCountedWithoutTokens(served, "rerank", "cohere", Provider.COHERE, "rerank-v3.5");
    }

    @Test
    public void shouldNameTheSpanAfterTheExpectationModelWhenNeitherResponseNorRequestNamesOne() throws Exception {
        HttpResponse served = serve(rerank(Provider.COHERE).withModel("rerank-english-v3.0"), request("/v1/rerank").withMethod("POST")
            .withBody("{\"query\":\"which document mentions foxes\",\"documents\":[\"the quick brown fox\"]}"));

        assertCountedWithoutTokens(served, "rerank", "cohere", Provider.COHERE, "rerank-english-v3.0");
    }

    @Test
    public void shouldNotCountARequestTheProviderRejects() {
        HttpResponse served = serve(embedding(Provider.OPENAI), request("/v1/embeddings").withMethod("POST")
            .withBody("{\"model\":\"text-embedding-3-small\",\"input\":\"\"}"));

        assertThat(served.getStatusCode(), is(400));
        assertThat(genAiSpans(), hasSize(0));
        assertThat(Metrics.getLlmInputTokens(Provider.OPENAI.name(), "text-embedding-3-small"), is(0L));
    }

    @Test
    public void shouldNotCountAnEmbeddingTheProviderHasNoEndpointFor() {
        HttpResponse served = serve(embedding(Provider.ANTHROPIC).withModel("claude-sonnet-4-5"), request("/v1/embeddings").withMethod("POST")
            .withBody("{\"input\":\"some text\"}"));

        assertThat(served.getStatusCode(), is(400));
        assertThat(genAiSpans(), hasSize(0));
    }

    private HttpResponse serve(HttpLlmResponse action, HttpRequest request) {
        return handler.handle(action, request);
    }

    private static HttpLlmResponse embedding(Provider provider) {
        return llmResponse().withProvider(provider).withEmbedding(EmbeddingResponse.embedding());
    }

    private static HttpLlmResponse rerank(Provider provider) {
        return llmResponse().withProvider(provider).withRerank(RerankResponse.rerank());
    }

    private static String expectedSystem(Provider provider) {
        return provider == Provider.AZURE_OPENAI ? "az.ai.openai" : provider.name().toLowerCase();
    }

    private void assertCounted(HttpResponse served, String operation, String system, Provider provider, String model, String usagePointer) throws Exception {
        assertThat(served.getStatusCode(), is(200));
        JsonNode usage = OBJECT_MAPPER.readTree(served.getBodyAsString()).at(usagePointer);
        assertThat(provider + " served body must carry " + usagePointer, usage.isIntegralNumber(), is(true));
        long tokens = usage.asLong();
        assertThat(tokens, greaterThan(0L));

        SpanData span = singleSpan(operation, system, model);
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(tokens));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.output_tokens")), nullValue());
        assertThat(Metrics.getLlmInputTokens(provider.name(), model), is(tokens));
        assertThat(Metrics.getLlmOutputTokens(provider.name(), model), is(0L));
    }

    private void assertCountedWithoutTokens(HttpResponse served, String operation, String system, Provider provider, String model) {
        assertThat(served.getStatusCode(), is(200));
        SpanData span = singleSpan(operation, system, model);
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), nullValue());
        assertThat(Metrics.getLlmInputTokens(provider.name(), model), is(0L));
    }

    private SpanData singleSpan(String operation, String system, String model) {
        List<SpanData> genAiSpans = genAiSpans();
        assertThat(genAiSpans, hasSize(1));
        SpanData span = genAiSpans.get(0);
        assertThat(span.getName(), is(operation + " " + model));
        assertThat(span.getAttributes().get(stringKey("gen_ai.operation.name")), is(operation));
        assertThat(span.getAttributes().get(stringKey("gen_ai.system")), is(system));
        assertThat(span.getAttributes().get(stringKey("gen_ai.request.model")), is(model));
        return span;
    }

    private List<SpanData> genAiSpans() {
        return spans.getFinishedSpanItems().stream()
            .filter(span -> span.getAttributes().get(stringKey("gen_ai.system")) != null)
            .collect(Collectors.toList());
    }
}
