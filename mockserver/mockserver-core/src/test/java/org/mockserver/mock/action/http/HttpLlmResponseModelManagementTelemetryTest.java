package org.mockserver.mock.action.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.metrics.Metrics;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpLlmResponse;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.SseEvent;
import org.mockserver.model.Usage;
import org.mockserver.telemetry.GenAiSpanExporter;
import org.slf4j.event.Level;

import java.util.List;
import java.util.stream.Collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;

/**
 * A request that only loads or unloads an Ollama model ({@code done_reason} {@code load} or
 * {@code unload}) generates nothing on a real Ollama server, so a mocked one records no GenAI
 * span, no token metrics, no inferred usage and no structured-output verdict, streamed or not.
 * Each case is paired with a generation request served by the same expectation, which is counted.
 * <p>
 * Mutates process-wide state (the GenAI tracer and the LLM metrics), so it runs in the sequential
 * surefire phase.
 */
public class HttpLlmResponseModelManagementTelemetryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODEL = "llama3.2";
    private static final String PERSON_SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\"}},\"required\":[\"name\",\"age\"]}";
    private static final String SCHEMA_WARNING = "does not conform to its declared outputSchema";

    private InMemorySpanExporter spans;
    private GenAiSpanExporter exporter;
    private MockServerLogger logger;
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
        Configuration configuration = new Configuration().metricsEnabled(true).llmMetricsEnabled(true).llmInferUsageEnabled(true);
        new Metrics(configuration);
        assertThat(Metrics.isLlmMetricsActive(), is(true));
        spans = InMemorySpanExporter.create();
        exporter = GenAiSpanExporter.startWithProcessor(SimpleSpanProcessor.create(spans));
        logger = mock(MockServerLogger.class);
        when(logger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        handler = new HttpLlmResponseActionHandler(logger, configuration);
    }

    @After
    public void tearDown() {
        exporter.stop();
        ConfigurationProperties.metricsEnabled(originalMetricsEnabled);
        ConfigurationProperties.llmMetricsEnabled(originalLlmMetricsEnabled);
        Metrics.resetAdditionalMetricsForTesting();
    }

    @Test
    public void shouldNotCountAGenerateLoadOrUnload() throws Exception {
        assertNotCounted(handler.handle(declaredUsage(false), generate("{\"model\":\"llama3.2\"}")), "load");
        assertNotCounted(handler.handle(declaredUsage(false), generate("{\"model\":\"llama3.2\",\"prompt\":\"\",\"keep_alive\":0}")), "unload");

        handler.handle(declaredUsage(false), generate("{\"model\":\"llama3.2\",\"prompt\":\"why is the sky blue\"}"));
        assertCountedOnce(12L, 34L);
    }

    @Test
    public void shouldNotCountAChatLoadOrUnload() throws Exception {
        assertNotCounted(handler.handle(declaredUsage(false), chat("{\"model\":\"llama3.2\",\"messages\":[]}")), "load");
        assertNotCounted(handler.handle(declaredUsage(false), chat("{\"model\":\"llama3.2\",\"messages\":[],\"keep_alive\":\"0s\"}")), "unload");

        handler.handle(declaredUsage(false), chat("{\"model\":\"llama3.2\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"));
        assertCountedOnce(12L, 34L);
    }

    @Test
    public void shouldNotCountAStreamedLoadOrUnload() throws Exception {
        assertNotCounted(handler.handleStreaming(declaredUsage(true), generate("{\"model\":\"llama3.2\",\"stream\":true}")), "load");
        assertNotCounted(handler.handleStreaming(declaredUsage(true), chat("{\"model\":\"llama3.2\",\"keep_alive\":0}")), "unload");

        handler.handleStreaming(declaredUsage(true), chat("{\"model\":\"llama3.2\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"));
        assertCountedOnce(12L, 34L);
    }

    @Test
    public void shouldNotInferUsageForALoad() throws Exception {
        HttpLlmResponse inferred = llmResponse().withProvider(Provider.OLLAMA).withModel(MODEL)
            .withCompletion(completion().withText("The sky is blue because of Rayleigh scattering."));

        assertNotCounted(handler.handle(inferred, generate("{\"model\":\"llama3.2\"}")), "load");
        assertNotCounted(handler.handleStreaming(withStreaming(inferred), chat("{\"model\":\"llama3.2\"}")), "load");

        handler.handle(inferred, generate("{\"model\":\"llama3.2\",\"prompt\":\"why is the sky blue\"}"));
        assertThat(genAiSpans(), hasSize(1));
        assertThat(Metrics.getLlmOutputTokens(Provider.OLLAMA.name(), MODEL) > 0, is(true));
    }

    @Test
    public void shouldNotCheckStructuredOutputForALoadOrUnload() throws Exception {
        HttpLlmResponse violating = llmResponse().withProvider(Provider.OLLAMA).withModel(MODEL)
            .withCompletion(completion().withText("{\"name\":\"Ada\"}").withOutputSchema(PERSON_SCHEMA));

        HttpResponse load = handler.handle(violating, generate("{\"model\":\"llama3.2\"}"));
        assertNotCounted(load, "load");
        assertThat(load.getFirstHeader(HttpLlmResponseActionHandler.STRUCTURED_OUTPUT_INVALID_HEADER), is(""));
        assertNotCounted(handler.handleStreaming(withStreaming(violating), chat("{\"model\":\"llama3.2\",\"keep_alive\":0}")), "unload");
        assertThat(schemaWarnings(), is(0L));

        HttpResponse generation = handler.handle(violating, generate("{\"model\":\"llama3.2\",\"prompt\":\"who is Ada\"}"));
        assertThat(generation.getFirstHeader(HttpLlmResponseActionHandler.STRUCTURED_OUTPUT_INVALID_HEADER), containsString("age"));
        reset(logger);
        when(logger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        handler.handleStreaming(withStreaming(violating), chat("{\"model\":\"llama3.2\",\"messages\":[{\"role\":\"user\",\"content\":\"who is Ada\"}]}"));
        assertThat(schemaWarnings(), is(1L));
    }

    @Test
    public void shouldNotEnforceStructuredOutputForALoadOrUnload() {
        HttpLlmResponse strict = llmResponse().withProvider(Provider.OLLAMA).withModel(MODEL)
            .withCompletion(completion().withText("{\"name\":\"Ada\"}").withOutputSchema(PERSON_SCHEMA).withEnforceOutputSchema(true));

        assertThat(handler.enforcementErrorResponseOrNull(strict, generate("{\"model\":\"llama3.2\"}")), nullValue());
        assertThat(handler.enforcementErrorResponseOrNull(strict, chat("{\"model\":\"llama3.2\",\"messages\":[],\"keep_alive\":0}")), nullValue());

        HttpResponse generation = handler.enforcementErrorResponseOrNull(strict, generate("{\"model\":\"llama3.2\",\"prompt\":\"who is Ada\"}"));
        assertThat(generation, notNullValue());
        assertThat(generation.getStatusCode(), is(HttpLlmResponseActionHandler.STRUCTURED_OUTPUT_ENFORCEMENT_STATUS));
    }

    private static HttpLlmResponse declaredUsage(boolean streaming) {
        return llmResponse().withProvider(Provider.OLLAMA).withModel(MODEL)
            .withCompletion(completion().withText("The sky is blue.").withStreaming(streaming)
                .withUsage(Usage.usage().withInputTokens(12).withOutputTokens(34)));
    }

    private static HttpLlmResponse withStreaming(HttpLlmResponse action) {
        Completion source = action.getCompletion();
        return llmResponse().withProvider(action.getProvider()).withModel(action.getModel())
            .withCompletion(completion().withText(source.getText()).withOutputSchema(source.getOutputSchema()).withStreaming(true));
    }

    private static HttpRequest generate(String body) {
        return request("/api/generate").withMethod("POST").withBody(body);
    }

    private static HttpRequest chat(String body) {
        return request("/api/chat").withMethod("POST").withBody(body);
    }

    private void assertNotCounted(HttpResponse served, String doneReason) throws Exception {
        assertThat(served.getStatusCode(), is(200));
        assertNotCounted(served.getBodyAsString(), doneReason);
    }

    private void assertNotCounted(List<SseEvent> streamed, String doneReason) throws Exception {
        assertThat(streamed, hasSize(1));
        assertNotCounted(streamed.get(0).getData(), doneReason);
    }

    private void assertNotCounted(String servedBody, String doneReason) throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(servedBody);
        assertThat(body.path("done_reason").asText(), is(doneReason));
        assertThat(body.has("eval_count"), is(false));
        assertThat(genAiSpans(), hasSize(0));
        assertThat(Metrics.getLlmInputTokens(Provider.OLLAMA.name(), MODEL), is(0L));
        assertThat(Metrics.getLlmOutputTokens(Provider.OLLAMA.name(), MODEL), is(0L));
    }

    private void assertCountedOnce(long inputTokens, long outputTokens) {
        assertThat(genAiSpans(), hasSize(1));
        assertThat(Metrics.getLlmInputTokens(Provider.OLLAMA.name(), MODEL), is(inputTokens));
        assertThat(Metrics.getLlmOutputTokens(Provider.OLLAMA.name(), MODEL), is(outputTokens));
    }

    private long schemaWarnings() {
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeast(0)).logEvent(entries.capture());
        return entries.getAllValues().stream()
            .filter(entry -> entry.getMessageFormat() != null && entry.getMessageFormat().contains(SCHEMA_WARNING))
            .count();
    }

    private List<SpanData> genAiSpans() {
        return spans.getFinishedSpanItems().stream()
            .filter(span -> span.getAttributes().get(stringKey("gen_ai.system")) != null)
            .collect(Collectors.toList());
    }
}
