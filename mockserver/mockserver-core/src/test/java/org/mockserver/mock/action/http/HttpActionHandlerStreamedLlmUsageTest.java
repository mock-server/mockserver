package org.mockserver.mock.action.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.llm.client.LlmProviderSniffer;
import org.mockserver.llm.client.ProviderStreamFixtures;
import org.mockserver.llm.cost.LlmPricing;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.metrics.Metrics;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.crud.CrudDispatcher;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.StreamingBody;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.telemetry.GenAiSpanExporter;
import org.mockserver.time.FixedTime;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.Delay.milliseconds;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * A forwarded LLM response that arrives as a stream is counted: the GenAI span carries its tokens
 * and its cost goes towards {@code llmCostBudgetUsd}. Each test forwards a stream in the shape the
 * provider documents through the production forward path and reads back the span and the cost.
 * <p>
 * Mutates process-wide state (the GenAI tracer, the cost budget monitor, configuration properties),
 * so it runs in the sequential surefire phase.
 */
public class HttpActionHandlerStreamedLlmUsageTest {

    private static final double USD_TOLERANCE = 0.000002;

    private Scheduler scheduler;
    @Mock
    private HttpForwardActionHandler mockHttpForwardActionHandler;
    @Mock
    private ResponseWriter mockResponseWriter;
    @Mock
    private MockServerLogger mockServerLogger;
    @Mock
    private NettyHttpClient mockNettyHttpClient;
    private HttpState mockHttpStateHandler;
    @InjectMocks
    private HttpActionHandler actionHandler;
    private volatile HttpForward nextForward;
    private volatile HttpForwardActionResult nextForwardResult;

    private InMemorySpanExporter spans;
    private GenAiSpanExporter exporter;
    private double originalBudget;
    private String originalLlmBaseUrl;

    @ClassRule
    public static final FixedTime fixedTime = new FixedTime();

    @Before
    public void setupMocks() {
        originalBudget = ConfigurationProperties.llmCostBudgetUsd();
        originalLlmBaseUrl = ConfigurationProperties.llmBaseUrl();
        ConfigurationProperties.llmCostBudgetUsd(100.0);
        LlmProviderSniffer.resetConfiguration();
        Metrics.resetAdditionalMetricsForTesting();
        LlmCostBudgetMonitor.getInstance().reset();
        Configuration configuration = configuration().logLevel(Level.INFO).metricsEnabled(true);

        mockHttpStateHandler = mock(HttpState.class);
        scheduler = new Scheduler(configuration, mockServerLogger);
        when(mockHttpStateHandler.getScheduler()).thenReturn(scheduler);
        when(mockHttpStateHandler.getUniqueLoopPreventionHeaderValue()).thenReturn("MockServer_" + UUIDService.getUUID());
        when(mockHttpStateHandler.getCrudDispatcher()).thenReturn(new CrudDispatcher());
        actionHandler = new HttpActionHandler(configuration, null, mockHttpStateHandler, null, null);

        openMocks(this);
        when(mockServerLogger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        // stubbed once, before any forward: a forward's drift analysis calls mockHttpStateHandler from a
        // scheduler thread, and a stubbing made while it does can attach to that thread's invocation
        when(mockHttpStateHandler.firstMatchingExpectation(any(HttpRequest.class)))
            .thenAnswer(invocation -> new Expectation(invocation.getArgument(0, HttpRequest.class)).thenForward(nextForward));
        when(mockHttpForwardActionHandler.handle(any(HttpForward.class), any(HttpRequest.class)))
            .thenAnswer(invocation -> nextForwardResult);

        spans = InMemorySpanExporter.create();
        exporter = GenAiSpanExporter.startWithProcessor(SimpleSpanProcessor.create(spans));
    }

    @After
    public void restoreOriginals() {
        scheduler.shutdown();
        exporter.stop();
        ConfigurationProperties.llmCostBudgetUsd(originalBudget);
        ConfigurationProperties.llmBaseUrl(originalLlmBaseUrl == null ? "" : originalLlmBaseUrl);
        LlmCostBudgetMonitor.getInstance().reset();
    }

    // ---- driving the forward path ----

    private void forwardResponse(String host, String path, String requestBody, HttpResponse upstreamResponse) {
        HttpRequest request = request(path).withMethod("POST").withBody(requestBody);
        HttpRequest forwardedRequest = request(path).withMethod("POST").withHeader("Host", host).withBody(requestBody);
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        future.complete(upstreamResponse);
        HttpForwardActionResult forwardResult = new HttpForwardActionResult(forwardedRequest, future, null, new InetSocketAddress(1234));
        nextForward = forward().withHost(host).withPort(443).withScheme(HttpForward.Scheme.HTTPS);
        nextForwardResult = forwardResult;

        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);
    }

    /**
     * Forward a response relayed as a stream: the body arrives in 7-byte chunks after the head has
     * been written, and only its first 16 bytes are captured for the log.
     */
    private StreamingBody forwardStream(String host, String path, String requestBody, byte[] stream) {
        StreamingBody streamingBody = startStream(host, path, requestBody);
        feed(streamingBody, stream);
        streamingBody.complete();
        return streamingBody;
    }

    private StreamingBody startStream(String host, String path, String requestBody) {
        StreamingBody streamingBody = new StreamingBody(16);
        forwardResponse(host, path, requestBody, response()
            .withStatusCode(200)
            .withHeader("Content-Type", "text/event-stream")
            .withStreamingBody(streamingBody)
            .withDelay(milliseconds(0)));
        return streamingBody;
    }

    private static void feed(StreamingBody streamingBody, byte[] stream) {
        for (int offset = 0; offset < stream.length; offset += 7) {
            ByteBuf chunk = Unpooled.wrappedBuffer(stream, offset, Math.min(7, stream.length - offset));
            streamingBody.addChunk(chunk);
            chunk.release();
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // ---- reading back what was recorded ----

    private List<SpanData> genAiSpans() {
        return spans.getFinishedSpanItems().stream()
            .filter(span -> span.getAttributes().get(stringKey("gen_ai.system")) != null)
            .collect(Collectors.toList());
    }

    private SpanData singleGenAiSpan() {
        List<SpanData> genAiSpans = genAiSpans();
        assertThat("one GenAI span for the forwarded call", genAiSpans, hasSize(1));
        return genAiSpans.get(0);
    }

    private static void assertTokens(SpanData span, String system, String model, Long input, Long output) {
        assertThat(span.getAttributes().get(stringKey("gen_ai.system")), is(system));
        assertThat(span.getAttributes().get(stringKey("gen_ai.request.model")), is(model));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(input));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.output_tokens")), is(output));
    }

    private static double recordedCostUsd() {
        return LlmCostBudgetMonitor.getInstance().getCumulativeCostUsd();
    }

    private static void assertCost(Provider provider, String model, long input, long output) {
        Double expected = LlmPricing.estimateCostUsd(provider, model, input, output);
        assertThat("the fixture's model has a price", expected, greaterThan(0.0));
        assertThat(recordedCostUsd(), closeTo(expected, USD_TOLERANCE));
    }

    private List<LogEntry> usageLogEntries() {
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(mockServerLogger, atLeast(0)).logEvent(entries.capture());
        return entries.getAllValues().stream()
            .filter(entry -> entry.getMessageFormat() != null && entry.getMessageFormat().contains("llmCostBudgetUsd"))
            .collect(Collectors.toList());
    }

    // ---- each provider's streamed shape ----

    @Test
    public void shouldCountOpenAiChatStreamSentWithIncludeUsage() {
        forwardStream("api.openai.com", "/v1/chat/completions",
            "{\"model\":\"gpt-4o\",\"stream\":true,\"stream_options\":{\"include_usage\":true},\"messages\":[]}",
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITH_USAGE));

        SpanData span = singleGenAiSpan();
        assertTokens(span, "openai", "gpt-4o-2024-08-06", 19L, 10L);
        assertThat(span.getAttributes().get(longKey("mockserver.gen_ai.usage.cached_input_tokens")), is(4L));
        assertThat(span.getAttributes().get(longKey("mockserver.gen_ai.usage.reasoning_tokens")), is(3L));
        assertThat(span.getAttributes().get(stringArrayKey("gen_ai.response.finish_reasons")), contains("stop"));
        assertCost(Provider.OPENAI, "gpt-4o-2024-08-06", 19, 10);
        assertThat(usageLogEntries(), is(empty()));
    }

    @Test
    public void shouldBlockTheNextLlmForwardOnceAStreamedCallHasSpentTheBudget() {
        ConfigurationProperties.llmCostBudgetUsd(0.0001);

        forwardStream("api.openai.com", "/v1/chat/completions", "{\"model\":\"gpt-4o\",\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITH_USAGE));
        forwardResponse("api.openai.com", "/v1/chat/completions", "{\"model\":\"gpt-4o\",\"stream\":true}",
            response().withStatusCode(200).withDelay(milliseconds(0)));

        ArgumentCaptor<HttpResponse> written = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockResponseWriter, times(2)).writeResponse(any(HttpRequest.class), written.capture(), eq(false));
        assertThat(written.getAllValues().get(0).getStatusCode(), is(200));
        assertThat(written.getAllValues().get(1).getStatusCode(), is(429));
        assertThat(written.getAllValues().get(1).getBodyAsString(), containsString("cost_budget_exceeded"));
        verify(mockHttpForwardActionHandler, times(1)).handle(any(HttpForward.class), any(HttpRequest.class));
    }

    @Test
    public void shouldCountNothingAndWarnWhenAnOpenAiStreamCarriesNoUsage() {
        forwardStream("api.openai.com", "/v1/chat/completions", "{\"model\":\"gpt-4o\",\"stream\":true,\"messages\":[]}",
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITHOUT_USAGE));

        SpanData span = singleGenAiSpan();
        assertTokens(span, "openai", "gpt-4o-2024-08-06", null, null);
        assertThat(recordedCostUsd(), is(0.0));
        List<LogEntry> logged = usageLogEntries();
        assertThat(logged, hasSize(1));
        assertThat(logged.get(0).getLogLevel(), is(Level.WARN));
        assertThat(logged.get(0).getMessageFormat(), containsString("no token usage in the streamed {} response for model {}"));
        assertThat(logged.get(0).getMessageFormat(), containsString("stream_options.include_usage"));
        assertThat(Arrays.asList(logged.get(0).getArguments()), contains(Provider.OPENAI, "gpt-4o-2024-08-06"));
    }

    @Test
    public void shouldReportMissingUsageAtInfoWhenNoBudgetIsConfigured() {
        ConfigurationProperties.llmCostBudgetUsd(-1.0);

        forwardStream("api.anthropic.com", "/v1/messages", "{\"model\":\"claude-sonnet-4-5\",\"stream\":true}",
            utf8("event: ping\ndata: {\"type\": \"ping\"}\n\n"));

        List<LogEntry> logged = usageLogEntries();
        assertThat(logged, hasSize(1));
        assertThat(logged.get(0).getLogLevel(), is(Level.INFO));
        assertThat(logged.get(0).getMessageFormat(), not(containsString("include_usage")));
    }

    @Test
    public void shouldCountUsageOpenAiCompatibleProvidersStreamWithoutOptIn() {
        forwardStream("api.mistral.ai", "/v1/chat/completions", "{\"model\":\"mistral-small-latest\",\"stream\":true}",
            utf8(ProviderStreamFixtures.MISTRAL_CHAT));
        assertTokens(singleGenAiSpan(), "mistral", "mistral-small-latest", 16L, 18L);
        assertCost(Provider.MISTRAL, "mistral-small-latest", 16, 18);

        spans.reset();
        forwardStream("api.groq.com", "/openai/v1/chat/completions", "{\"model\":\"llama-3.3-70b-versatile\",\"stream\":true}",
            utf8(ProviderStreamFixtures.GROQ_CHAT));
        assertTokens(singleGenAiSpan(), "groq", "llama-3.3-70b-versatile", 18L, 25L);

        spans.reset();
        LlmCostBudgetMonitor.getInstance().reset();
        forwardStream("openrouter.ai", "/api/v1/chat/completions", "{\"model\":\"openai/gpt-4o\",\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENROUTER_CHAT));
        assertTokens(singleGenAiSpan(), "openrouter", "openai/gpt-4o", 14L, 9L);
        assertCost(Provider.OPENROUTER, "openai/gpt-4o", 14, 9);
        assertThat(usageLogEntries(), is(empty()));
    }

    @Test
    public void shouldCountOpenAiResponsesStreamFromResponseCompleted() {
        forwardStream("chatgpt.com", "/backend-api/codex/responses", "{\"model\":\"gpt-4o\",\"stream\":true,\"input\":[]}",
            utf8(ProviderStreamFixtures.OPENAI_RESPONSES));

        SpanData span = singleGenAiSpan();
        assertTokens(span, "openai", "gpt-4o-2024-08-06", 37L, 11L);
        assertThat(span.getAttributes().get(longKey("mockserver.gen_ai.usage.cached_input_tokens")), is(5L));
        assertThat(span.getAttributes().get(longKey("mockserver.gen_ai.usage.reasoning_tokens")), is(2L));
        assertCost(Provider.OPENAI_RESPONSES, "gpt-4o-2024-08-06", 37, 11);
    }

    @Test
    public void shouldCountAnthropicStreamFromMessageStartAndMessageDelta() {
        forwardStream("api.anthropic.com", "/v1/messages", "{\"model\":\"claude-sonnet-4-5\",\"stream\":true,\"messages\":[]}",
            utf8(ProviderStreamFixtures.ANTHROPIC_MESSAGES));

        SpanData span = singleGenAiSpan();
        assertTokens(span, "anthropic", "claude-sonnet-4-5-20250929", 25L, 15L);
        assertThat(span.getAttributes().get(longKey("mockserver.gen_ai.usage.cached_input_tokens")), is(7L));
        assertThat(span.getAttributes().get(longKey("mockserver.gen_ai.usage.cache_creation_tokens")), is(3L));
        assertThat(span.getAttributes().get(stringArrayKey("gen_ai.response.finish_reasons")), contains("end_turn"));
        assertCost(Provider.ANTHROPIC, "claude-sonnet-4-5-20250929", 25, 15);
        assertThat(usageLogEntries(), is(empty()));
    }

    @Test
    public void shouldCountOnlyWhatWasReportedWhenAnAnthropicStreamIsCutOff() {
        StreamingBody streamingBody = startStream("api.anthropic.com", "/v1/messages", "{\"model\":\"claude-sonnet-4-5\",\"stream\":true}");
        feed(streamingBody, utf8(ProviderStreamFixtures.ANTHROPIC_MESSAGES_CUT_OFF));
        streamingBody.error(new StreamingBody.StreamAbortedException("upstream closed mid-stream"));

        assertTokens(singleGenAiSpan(), "anthropic", "claude-sonnet-4-5-20250929", 25L, 1L);
        assertCost(Provider.ANTHROPIC, "claude-sonnet-4-5-20250929", 25, 1);
        List<LogEntry> logged = usageLogEntries();
        assertThat(logged, hasSize(1));
        assertThat(logged.get(0).getLogLevel(), is(Level.WARN));
        assertThat(logged.get(0).getMessageFormat(), containsString("ended before its final usage event"));
    }

    @Test
    public void shouldCountNothingWhenAStreamIsCutOffBeforeItsOnlyUsageEvent() {
        StreamingBody streamingBody = startStream("chatgpt.com", "/backend-api/codex/responses", "{\"model\":\"gpt-4o\",\"stream\":true}");
        feed(streamingBody, utf8(ProviderStreamFixtures.OPENAI_RESPONSES_CUT_OFF));
        streamingBody.error(new StreamingBody.StreamAbortedException("upstream closed mid-stream"));

        assertTokens(singleGenAiSpan(), "openai", "gpt-4o-2024-08-06", null, null);
        assertThat(recordedCostUsd(), is(0.0));
        assertThat(usageLogEntries(), hasSize(1));
    }

    @Test
    public void shouldCountGeminiStreamFromItsLastUsageMetadata() {
        forwardStream("generativelanguage.googleapis.com", "/v1beta/models/gemini-2.5-flash:streamGenerateContent", "{\"contents\":[]}",
            utf8(ProviderStreamFixtures.GEMINI_SSE));

        SpanData span = singleGenAiSpan();
        assertTokens(span, "gcp.gemini", "gemini-2.5-flash", 8L, 39L);
        assertThat(span.getAttributes().get(longKey("mockserver.gen_ai.usage.reasoning_tokens")), is(27L));
        assertCost(Provider.GEMINI, "gemini-2.5-flash", 8, 39);
        assertThat(usageLogEntries(), is(empty()));
    }

    @Test
    public void shouldSayWhenAGeminiStreamIsCutOffBeforeItsFinishReason() {
        String stream = ProviderStreamFixtures.GEMINI_SSE;
        StreamingBody streamingBody = startStream("generativelanguage.googleapis.com", "/v1beta/models/gemini-2.5-flash:streamGenerateContent", "{\"contents\":[]}");
        feed(streamingBody, utf8(stream.substring(0, stream.indexOf("\r\n\r\n") + 4)));
        streamingBody.error(new StreamingBody.StreamAbortedException("upstream closed mid-stream"));

        assertTokens(singleGenAiSpan(), "gcp.gemini", "gemini-2.5-flash", 8L, null);
        List<LogEntry> logged = usageLogEntries();
        assertThat(logged, hasSize(1));
        assertThat(logged.get(0).getMessageFormat(), containsString("ended before its final usage event"));
    }

    @Test
    public void shouldReportMissingUsageOnceForRepeatedCallsToTheSameModel() {
        for (int call = 0; call < 3; call++) {
            forwardStream("api.openai.com", "/v1/chat/completions", "{\"model\":\"gpt-4o\",\"stream\":true}",
                utf8(ProviderStreamFixtures.OPENAI_CHAT_WITHOUT_USAGE));
        }
        forwardStream("api.openai.com", "/v1/chat/completions", "{\"model\":\"gpt-4o-mini\",\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITHOUT_USAGE.replace("gpt-4o-2024-08-06", "gpt-4o-mini-2024-07-18")));

        List<LogEntry> logged = usageLogEntries();
        assertThat(genAiSpans(), hasSize(4));
        assertThat(logged, hasSize(2));
        assertThat(Arrays.asList(logged.get(0).getArguments()), contains(Provider.OPENAI, "gpt-4o-2024-08-06"));
        assertThat(Arrays.asList(logged.get(1).getArguments()), contains(Provider.OPENAI, "gpt-4o-mini-2024-07-18"));
    }

    @Test
    public void shouldSuggestIncludeUsageForAzureChatCompletionsButNotForAzureResponses() {
        forwardStream("contoso.openai.azure.com", "/openai/deployments/gpt-4o/chat/completions", "{\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITHOUT_USAGE));
        forwardStream("contoso.openai.azure.com", "/openai/v1/responses", "{\"model\":\"gpt-4o\",\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENAI_RESPONSES_CUT_OFF.replace("gpt-4o-2024-08-06", "gpt-4.1-2025-04-14")));

        List<LogEntry> logged = usageLogEntries();
        assertThat(logged, hasSize(2));
        assertThat(logged.get(0).getMessageFormat(), containsString("stream_options.include_usage"));
        assertThat(logged.get(1).getMessageFormat(), not(containsString("include_usage")));
    }

    @Test
    public void shouldCountOllamaStreamFromItsDoneLine() {
        ConfigurationProperties.llmBaseUrl("http://ollama.internal:11434");

        forwardStream("ollama.internal:11434", "/api/chat", "{\"model\":\"llama3.2\",\"stream\":true,\"messages\":[]}",
            utf8(ProviderStreamFixtures.OLLAMA_CHAT));

        assertTokens(singleGenAiSpan(), "ollama", "llama3.2", 26L, 282L);
        assertThat(usageLogEntries(), is(empty()));
    }

    // ---- a stream that was aggregated instead of relayed ----

    @Test
    public void shouldCountAnOllamaStreamThatWasAggregated() {
        ConfigurationProperties.llmBaseUrl("http://ollama.internal:11434");

        forwardResponse("ollama.internal:11434", "/api/chat", "{\"model\":\"llama3.2\",\"messages\":[]}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/x-ndjson")
            .withBody(ProviderStreamFixtures.OLLAMA_CHAT)
            .withDelay(milliseconds(0)));

        assertTokens(singleGenAiSpan(), "ollama", "llama3.2", 26L, 282L);
    }

    @Test
    public void shouldCountAServerSentEventStreamThatWasAggregated() {
        forwardResponse("api.anthropic.com", "/v1/messages", "{\"model\":\"claude-sonnet-4-5\",\"stream\":true}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "text/event-stream")
            .withBody(ProviderStreamFixtures.ANTHROPIC_MESSAGES)
            .withDelay(milliseconds(0)));

        assertTokens(singleGenAiSpan(), "anthropic", "claude-sonnet-4-5-20250929", 25L, 15L);
        assertCost(Provider.ANTHROPIC, "claude-sonnet-4-5-20250929", 25, 15);
    }

    @Test
    public void shouldCountAGeminiJsonArrayStream() {
        forwardResponse("generativelanguage.googleapis.com", "/v1beta/models/gemini-2.5-flash:streamGenerateContent", "{\"contents\":[]}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json; charset=UTF-8")
            .withBody(ProviderStreamFixtures.GEMINI_JSON_ARRAY)
            .withDelay(milliseconds(0)));

        assertTokens(singleGenAiSpan(), "gcp.gemini", "gemini-2.5-flash", 8L, 39L);
        assertCost(Provider.GEMINI, "gemini-2.5-flash", 8, 39);
    }

    @Test
    public void shouldCountBedrockConverseStreamFromItsMetadataEvent() {
        forwardResponse("bedrock-runtime.us-east-1.amazonaws.com", "/model/anthropic.claude-sonnet-4-5-20250929-v1:0/converse-stream", "{\"messages\":[]}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/vnd.amazon.eventstream")
            .withBody(ProviderStreamFixtures.bedrockConverseStream())
            .withDelay(milliseconds(0)));

        SpanData span = singleGenAiSpan();
        assertThat(span.getAttributes().get(stringKey("gen_ai.system")), is("aws.bedrock"));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(25L));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.output_tokens")), is(15L));
        assertThat(span.getAttributes().get(longKey("mockserver.gen_ai.usage.cached_input_tokens")), is(6L));
        assertThat(usageLogEntries(), is(empty()));
    }

    @Test
    public void shouldCountBedrockInvokeWithResponseStreamFromItsWrappedEvents() {
        forwardResponse("bedrock-runtime.us-east-1.amazonaws.com", "/model/anthropic.claude-sonnet-4-5-20250929-v1:0/invoke-with-response-stream", "{\"anthropic_version\":\"bedrock-2023-05-31\",\"messages\":[]}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/vnd.amazon.eventstream")
            .withBody(ProviderStreamFixtures.bedrockInvokeWithResponseStream())
            .withDelay(milliseconds(0)));

        assertTokens(singleGenAiSpan(), "aws.bedrock", "claude-sonnet-4-5-20250929", 25L, 15L);
        assertCost(Provider.BEDROCK, "claude-sonnet-4-5-20250929", 25, 15);
    }

    @Test
    public void shouldStillCountAWholeJsonResponseAsBefore() {
        forwardResponse("api.openai.com", "/v1/chat/completions", "{\"model\":\"gpt-4o\",\"messages\":[]}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"id\":\"chatcmpl-123\",\"object\":\"chat.completion\",\"model\":\"gpt-4o-2024-08-06\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}")
            .withDelay(milliseconds(0)));

        assertTokens(singleGenAiSpan(), "openai", "gpt-4o-2024-08-06", 12L, 8L);
        assertCost(Provider.OPENAI, "gpt-4o-2024-08-06", 12, 8);
        assertThat(usageLogEntries(), is(empty()));
    }

    @Test
    public void shouldNotReportMissingUsageForAWholeJsonResponseOrAnErrorStream() {
        forwardResponse("api.openai.com", "/v1/models", "", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"object\":\"list\",\"data\":[{\"id\":\"gpt-4o\",\"object\":\"model\"}]}")
            .withDelay(milliseconds(0)));
        StreamingBody errorStream = new StreamingBody(1024);
        forwardResponse("api.openai.com", "/v1/chat/completions", "{\"model\":\"gpt-4o\",\"stream\":true}", response()
            .withStatusCode(429)
            .withHeader("Content-Type", "text/event-stream")
            .withStreamingBody(errorStream)
            .withDelay(milliseconds(0)));
        feed(errorStream, utf8("data: {\"error\":{\"message\":\"Rate limit reached\",\"type\":\"rate_limit_error\"}}\n\n"));
        errorStream.complete();

        assertThat(usageLogEntries(), is(empty()));
        assertThat(recordedCostUsd(), is(0.0));
    }

    // ---- streams that are not LLM responses, or are hostile ----

    @Test
    public void shouldNotCountAStreamForwardedToAHostThatIsNotAnLlmProvider() {
        forwardStream("events.example.com", "/v1/chat/completions", "{\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITH_USAGE));

        assertThat(genAiSpans(), is(empty()));
        assertThat(recordedCostUsd(), is(0.0));
        assertThat(usageLogEntries(), is(empty()));
    }

    @Test
    public void shouldCountNoTokensForANonLlmEventStreamFromAnLlmHost() {
        forwardStream("api.openai.com", "/v1/realtime/events", "{}", utf8(""
            + "event: metric\ndata: {\"host\":\"web-1\",\"usage\":\"87%\",\"cpu\":{\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":6}}}\n\n"
            + "event: metric\ndata: {\"hosts\":[{\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":8}}],\"usage\":{\"cpu\":0.87}}\n\n"));

        SpanData span = singleGenAiSpan();
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(nullValue()));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.output_tokens")), is(nullValue()));
        assertThat(recordedCostUsd(), is(0.0));
    }

    @Test
    public void shouldRelayAHostileStreamUnchangedAndCountNothing() {
        Random random = new Random(7);
        byte[] noise = new byte[32 * 1024];
        random.nextBytes(noise);
        ByteArrayOutputStream hostile = new ByteArrayOutputStream();
        hostile.writeBytes(utf8("data: {\"usage\":{\"prompt_tokens\":" + "[".repeat(3000) + "\n\n"));
        hostile.writeBytes(utf8("data: {\"usage\":{\"prompt_tokens\":\"" + "9".repeat(20000) + "\"}}\n\n"));
        hostile.writeBytes(noise);
        byte[] stream = hostile.toByteArray();

        StreamingBody streamingBody = startStream("api.openai.com", "/v1/chat/completions", "{\"model\":\"gpt-4o\",\"stream\":true}");
        ByteArrayOutputStream relayed = new ByteArrayOutputStream();
        boolean[] completed = {false};
        // the response writer is a mock, so subscribe as it would
        streamingBody.subscribe(chunk -> {
            byte[] bytes = new byte[chunk.readableBytes()];
            chunk.getBytes(chunk.readerIndex(), bytes);
            relayed.writeBytes(bytes);
        }, () -> completed[0] = true, error -> { });
        feed(streamingBody, stream);
        streamingBody.complete();

        assertThat(Arrays.equals(relayed.toByteArray(), stream), is(true));
        assertThat(completed[0], is(true));
        assertThat(streamingBody.getError(), is(nullValue()));
        assertThat(recordedCostUsd(), is(0.0));
        assertTokens(singleGenAiSpan(), "openai", "gpt-4o", null, null);
    }
}
