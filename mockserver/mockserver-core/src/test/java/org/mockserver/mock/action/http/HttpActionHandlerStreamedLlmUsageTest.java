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
import org.mockserver.closurecallback.websocketregistry.WebSocketRequestCallback;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.breakpoint.BreakpointCallbackDispatcher;
import org.mockserver.mock.breakpoint.BreakpointMatcherRegistry;
import org.mockserver.mock.breakpoint.BreakpointPhase;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.crud.CrudDispatcher;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.ProxyPassMapping;
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
import java.util.EnumSet;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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
    private String originalLlmProvider;
    private boolean originalMetricsEnabled;
    private boolean originalLlmMetricsEnabled;
    private Configuration configuration;
    @Mock
    private WebSocketClientRegistry mockWebSocketClientRegistry;

    @ClassRule
    public static final FixedTime fixedTime = new FixedTime();

    @Before
    public void setupMocks() {
        originalBudget = ConfigurationProperties.llmCostBudgetUsd();
        originalLlmBaseUrl = ConfigurationProperties.llmBaseUrl();
        originalLlmProvider = ConfigurationProperties.llmProvider();
        originalMetricsEnabled = ConfigurationProperties.metricsEnabled();
        originalLlmMetricsEnabled = ConfigurationProperties.llmMetricsEnabled();
        ConfigurationProperties.llmCostBudgetUsd(100.0);
        LlmProviderSniffer.resetConfiguration();
        Metrics.resetAdditionalMetricsForTesting();
        LlmCostBudgetMonitor.getInstance().reset();
        configuration = configuration().logLevel(Level.INFO).metricsEnabled(true).breakpointTimeoutMillis(30000L).breakpointMaxHeld(50);

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
            .thenAnswer(invocation -> nextForward == null ? null : new Expectation(invocation.getArgument(0, HttpRequest.class)).thenForward(nextForward));
        when(mockHttpStateHandler.getWebSocketClientRegistry()).thenReturn(mockWebSocketClientRegistry);
        when(mockWebSocketClientRegistry.sendClientMessage(anyString(), any(HttpRequest.class), any())).thenReturn(true);
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
        ConfigurationProperties.llmProvider(originalLlmProvider == null ? "" : originalLlmProvider);
        ConfigurationProperties.metricsEnabled(originalMetricsEnabled);
        ConfigurationProperties.llmMetricsEnabled(originalLlmMetricsEnabled);
        Metrics.resetAdditionalMetricsForTesting();
        LlmCostBudgetMonitor.getInstance().reset();
        BreakpointMatcherRegistry.getInstance().clear();
        BreakpointCallbackDispatcher.getInstance().reset();
    }

    // ---- driving the forward path ----

    private void forwardResponse(String host, String path, String requestBody, HttpResponse upstreamResponse) {
        forwardResponse("POST", host, path, requestBody, upstreamResponse);
    }

    private void forwardResponse(String method, String host, String path, String requestBody, HttpResponse upstreamResponse) {
        HttpRequest request = request(path).withMethod(method).withBody(requestBody);
        HttpRequest forwardedRequest = request(path).withMethod(method).withHeader("Host", host).withBody(requestBody);
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
        return startStream("POST", host, path, requestBody);
    }

    private StreamingBody startStream(String method, String host, String path, String requestBody) {
        StreamingBody streamingBody = new StreamingBody(16);
        forwardResponse(method, host, path, requestBody, response()
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
        assertTokens(span, "aws.bedrock", "anthropic.claude-sonnet-4-5-20250929-v1:0", 25L, 15L);
        assertThat(span.getAttributes().get(longKey("mockserver.gen_ai.usage.cached_input_tokens")), is(6L));
        assertCost(Provider.BEDROCK, "anthropic.claude-sonnet-4-5-20250929-v1:0", 25, 15);
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
    public void shouldNotTreatANonCompletionEventStreamFromAnLlmHostAsACompletion() {
        forwardStream("api.openai.com", "/v1/realtime/events", "{}", utf8(""
            + "event: metric\ndata: {\"host\":\"web-1\",\"usage\":\"87%\",\"cpu\":{\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":6}}}\n\n"
            + "event: metric\ndata: {\"hosts\":[{\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":8}}],\"usage\":{\"cpu\":0.87}}\n\n"));

        assertThat(genAiSpans(), is(empty()));
        assertThat(recordedCostUsd(), is(0.0));
        assertThat(usageLogEntries(), is(empty()));
    }

    @Test
    public void shouldGiveNoSpanOrMissingUsageWarningToAnOllamaPullProgressStream() {
        ConfigurationProperties.llmBaseUrl("http://ollama.internal:11434");
        String pullProgress = ""
            + "{\"status\":\"pulling manifest\"}\n"
            + "{\"status\":\"pulling dde5aa3fc5ff\",\"digest\":\"sha256:dde5aa3fc5ffc17176b5e8bdc82f587b24b2678c6c66101bf7da77af9f7ccdff\",\"total\":2019377376,\"completed\":241970}\n"
            + "{\"status\":\"verifying sha256 digest\"}\n"
            + "{\"status\":\"success\"}\n";

        forwardStream("ollama.internal:11434", "/api/pull", "{\"model\":\"llama3.2\"}", utf8(pullProgress));
        // a model already pulled streams a single line, captured whole for the log, which reads as one JSON document
        StreamingBody alreadyPulled = new StreamingBody(1024);
        forwardResponse("ollama.internal:11434", "/api/pull", "{\"model\":\"llama3.2\"}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/x-ndjson")
            .withStreamingBody(alreadyPulled)
            .withDelay(milliseconds(0)));
        feed(alreadyPulled, utf8("{\"status\":\"success\"}\n"));
        alreadyPulled.complete();
        forwardResponse("ollama.internal:11434", "/api/pull", "{\"model\":\"llama3.2\"}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/x-ndjson")
            .withBody(pullProgress)
            .withDelay(milliseconds(0)));

        assertThat(genAiSpans(), is(empty()));
        assertThat(usageLogEntries(), is(empty()));
        // a completion stream from the same host is still counted
        forwardStream("ollama.internal:11434", "/api/chat", "{\"model\":\"llama3.2\",\"messages\":[]}",
            utf8(ProviderStreamFixtures.OLLAMA_CHAT));
        assertTokens(singleGenAiSpan(), "ollama", "llama3.2", 26L, 282L);
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

    // ---- only a call to a model is accounted: completions, embeddings and rerank ----

    private static HttpResponse json(String body) {
        return response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
            .withDelay(milliseconds(0));
    }

    private static void enableLlmTokenMetrics() {
        // the handler built in setup registered the metrics without the LLM counters
        Metrics.resetAdditionalMetricsForTesting();
        ConfigurationProperties.metricsEnabled(true);
        ConfigurationProperties.llmMetricsEnabled(true);
        new Metrics(configuration().metricsEnabled(true).llmMetricsEnabled(true));
    }

    private static void assertOperation(SpanData span, String operation, String spanName) {
        assertThat(span.getAttributes().get(stringKey("gen_ai.operation.name")), is(operation));
        assertThat(span.getName(), is(spanName));
    }

    @Test
    public void shouldNotAccountAWholeResponseFromAnEndpointOfAnLlmHostThatDoesNotCallAModel() {
        enableLlmTokenMetrics();
        ConfigurationProperties.llmBaseUrl("http://ollama.internal:11434");
        forwardResponse("GET", "api.openai.com", "/v1/models", "", json(
            "{\"object\":\"list\",\"data\":[{\"id\":\"gpt-4o\",\"object\":\"model\",\"created\":1686935002,\"owned_by\":\"openai\"}]}"));
        // listing stored chat completions, on the path a completion is created on
        forwardResponse("GET", "api.openai.com", "/v1/chat/completions", "", json(
            "{\"object\":\"list\",\"data\":[{\"object\":\"chat.completion\",\"id\":\"chatcmpl-AyPNinnUqUDYo9SAdA52NobMflmj2\","
                + "\"model\":\"gpt-4o-2024-08-06\",\"created\":1738960610,\"request_id\":\"req_ded8ab984ec4bf840f37566c1011c417\","
                + "\"tool_choice\":null,\"usage\":{\"total_tokens\":31,\"completion_tokens\":18,\"prompt_tokens\":13},"
                + "\"seed\":4944116822809979520,\"top_p\":1.0,\"temperature\":1.0,\"presence_penalty\":0.0,\"frequency_penalty\":0.0,"
                + "\"system_fingerprint\":\"fp_50cad350e4\",\"input_user\":null,\"service_tier\":\"default\",\"tools\":null,"
                + "\"metadata\":{},\"choices\":[{\"index\":0,\"message\":{\"content\":\"Mind of circuits hum\",\"role\":\"assistant\","
                + "\"tool_calls\":null,\"function_call\":null},\"finish_reason\":\"stop\",\"logprobs\":null}],\"response_format\":null}],"
                + "\"first_id\":\"chatcmpl-AyPNinnUqUDYo9SAdA52NobMflmj2\",\"last_id\":\"chatcmpl-AyPNinnUqUDYo9SAdA52NobMflmj2\",\"has_more\":false}"));
        // retrieving a stored response would count its tokens a second time
        forwardResponse("GET", "api.openai.com", "/v1/responses/resp_67cb71b351908190a308f3859487620d", "", json(
            "{\"id\":\"resp_67cb71b351908190a308f3859487620d\",\"object\":\"response\",\"created_at\":1741386163,\"status\":\"completed\","
                + "\"model\":\"gpt-4o-2024-08-06\",\"output\":[{\"type\":\"message\",\"id\":\"msg_67cb71b3c2b0819084d481baaaf148f2\","
                + "\"status\":\"completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hi\",\"annotations\":[]}]}],"
                + "\"usage\":{\"input_tokens\":328,\"output_tokens\":52,\"total_tokens\":380}}"));
        forwardResponse("POST", "api.anthropic.com", "/v1/messages/count_tokens",
            "{\"model\":\"claude-sonnet-4-5\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello, world\"}]}",
            json("{\"input_tokens\":14}"));
        forwardResponse("GET", "ollama.internal:11434", "/api/tags", "", json(
            "{\"models\":[{\"name\":\"llama3.2:latest\",\"model\":\"llama3.2:latest\",\"size\":2019393189}]}"));
        // a stored response replayed as a stream is not a new call either
        StreamingBody replay = startStream("GET", "api.openai.com", "/v1/responses/resp_67cb71b351908190a308f3859487620d", "");
        feed(replay, utf8(ProviderStreamFixtures.OPENAI_RESPONSES));
        replay.complete();

        assertThat(genAiSpans(), is(empty()));
        assertThat(recordedCostUsd(), is(0.0));
        assertThat(Metrics.getLlmInputTokens("OPENAI_RESPONSES", "gpt-4o-2024-08-06"), is(0L));
        assertThat(usageLogEntries(), is(empty()));
    }

    @Test
    public void shouldCountAnOpenAiEmbeddingsResponseAsAnEmbeddingsCall() {
        enableLlmTokenMetrics();
        forwardResponse("api.openai.com", "/v1/embeddings", "{\"model\":\"text-embedding-3-small\",\"input\":\"The food was delicious\"}", json(
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[0.0023064255,-0.009327292],\"index\":0}],"
                + "\"model\":\"text-embedding-3-small\",\"usage\":{\"prompt_tokens\":8,\"total_tokens\":8}}"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "embeddings", "embeddings text-embedding-3-small");
        assertTokens(span, "openai", "text-embedding-3-small", 8L, null);
        assertThat(Metrics.getLlmInputTokens("OPENAI", "text-embedding-3-small"), is(8L));
    }

    @Test
    public void shouldCountAnAzureOpenAiEmbeddingsResponseAsAnEmbeddingsCall() {
        enableLlmTokenMetrics();
        forwardResponse("my-resource.openai.azure.com", "/openai/deployments/text-embedding-3-small/embeddings", "{\"input\":[\"hello\"]}", json(
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[0.0023064255,-0.009327292],\"index\":0}],"
                + "\"model\":\"text-embedding-3-small\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "embeddings", "embeddings text-embedding-3-small");
        assertTokens(span, "az.ai.openai", "text-embedding-3-small", 1L, null);
        assertThat(Metrics.getLlmInputTokens("AZURE_OPENAI", "text-embedding-3-small"), is(1L));
    }

    @Test
    public void shouldCountAMistralEmbeddingsResponseAsAnEmbeddingsCall() {
        enableLlmTokenMetrics();
        forwardResponse("api.mistral.ai", "/v1/embeddings", "{\"model\":\"mistral-embed\",\"input\":[\"Embed this sentence.\"]}", json(
            "{\"id\":\"embd-aad6fc62b17349b192ef09225058bc45\",\"object\":\"list\",\"data\":[{\"object\":\"embedding\","
                + "\"embedding\":[-0.016632080078125,0.0701904296875],\"index\":0}],\"model\":\"mistral-embed\","
                + "\"usage\":{\"prompt_tokens\":15,\"total_tokens\":15,\"completion_tokens\":0}}"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "embeddings", "embeddings mistral-embed");
        assertTokens(span, "mistral", "mistral-embed", 15L, 0L);
        assertThat(Metrics.getLlmInputTokens("MISTRAL", "mistral-embed"), is(15L));
    }

    @Test
    public void shouldCountAGeminiEmbeddingsResponseByTheModelInItsPath() {
        enableLlmTokenMetrics();
        forwardResponse("generativelanguage.googleapis.com", "/v1beta/models/gemini-embedding-001:embedContent",
            "{\"content\":{\"parts\":[{\"text\":\"What is the meaning of life?\"}]}}", json(
                "{\"embedding\":{\"values\":[-0.022374554,-0.004560777]},"
                    + "\"usageMetadata\":{\"promptTokenCount\":7,\"promptTokenDetails\":[{\"modality\":\"TEXT\",\"tokenCount\":7}]}}"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "embeddings", "embeddings gemini-embedding-001");
        assertTokens(span, "gcp.gemini", "gemini-embedding-001", 7L, null);
        assertThat(Metrics.getLlmInputTokens("GEMINI", "gemini-embedding-001"), is(7L));
    }

    @Test
    public void shouldCountAnOllamaEmbedResponseAsAnEmbeddingsCall() {
        enableLlmTokenMetrics();
        ConfigurationProperties.llmBaseUrl("http://ollama.internal:11434");
        forwardResponse("ollama.internal:11434", "/api/embed", "{\"model\":\"all-minilm\",\"input\":\"Why is the sky blue?\"}", json(
            "{\"model\":\"all-minilm\",\"embeddings\":[[0.010071029,-0.0017594862]],"
                + "\"total_duration\":14143917,\"load_duration\":1019500,\"prompt_eval_count\":8}"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "embeddings", "embeddings all-minilm");
        assertTokens(span, "ollama", "all-minilm", 8L, null);
        assertThat(Metrics.getLlmInputTokens("OLLAMA", "all-minilm"), is(8L));
    }

    @Test
    public void shouldNameABedrockEmbeddingOrRerankModelInvocationByItsOperation() {
        enableLlmTokenMetrics();
        // Titan reports its input tokens in the body
        forwardResponse("bedrock-runtime.us-east-1.amazonaws.com", "/model/amazon.titan-embed-text-v2%3A0/invoke", "{\"inputText\":\"Hello\"}", json(
            "{\"embedding\":[0.1,0.2],\"embeddingsByType\":{\"float\":[0.1,0.2]},\"inputTextTokenCount\":2}"));
        // any other model's body has none, so its counts are read from the InvokeModel token-count headers
        forwardResponse("bedrock-runtime.us-east-1.amazonaws.com", "/model/cohere.rerank-v3-5%3A0/invoke",
            "{\"query\":\"What is the capital?\",\"documents\":[\"Paris\",\"Berlin\"],\"api_version\":2}", json(
                "{\"id\":\"5ee5c1c8-b3e0-4b1c-9b1a-1c2f3e4d5a6b\",\"results\":[{\"index\":0,\"relevance_score\":0.9}]}")
                .withHeader("X-Amzn-Bedrock-Input-Token-Count", "14")
                .withHeader("X-Amzn-Bedrock-Output-Token-Count", "0"));
        forwardResponse("bedrock-runtime.us-east-1.amazonaws.com", "/model/cohere.embed-english-v3/invoke",
            "{\"texts\":[\"hello world\"],\"input_type\":\"search_document\"}", json(
                "{\"embeddings\":[[0.01,-0.02]],\"id\":\"6b2c4a6e-1e9f-4f3c-9d25-4c2a1f0e8b7d\",\"response_type\":\"embeddings_floats\","
                    + "\"texts\":[\"hello world\"]}")
                .withHeader("X-Amzn-Bedrock-Input-Token-Count", "3")
                .withHeader("X-Amzn-Bedrock-Output-Token-Count", "0"));
        // an Agent Runtime rerank names its model only in rerankingConfiguration, and reports no usage
        forwardResponse("bedrock-agent-runtime.us-west-2.amazonaws.com", "/rerank",
            "{\"queries\":[{\"type\":\"TEXT\",\"textQuery\":{\"text\":\"What is the capital?\"}}],"
                + "\"rerankingConfiguration\":{\"type\":\"BEDROCK_RERANKING_MODEL\",\"bedrockRerankingConfiguration\":{"
                + "\"numberOfResults\":1,\"modelConfiguration\":{\"modelArn\":\"arn:aws:bedrock:us-west-2::foundation-model/amazon.rerank-v1:0\"}}},"
                + "\"sources\":[{\"type\":\"INLINE\",\"inlineDocumentSource\":{\"type\":\"TEXT\",\"textDocument\":{\"text\":\"Paris\"}}}]}", json(
                "{\"results\":[{\"index\":0,\"relevanceScore\":0.86}]}"));

        List<SpanData> genAiSpans = genAiSpans();
        assertThat(genAiSpans, hasSize(4));
        assertOperation(genAiSpans.get(0), "embeddings", "embeddings amazon.titan-embed-text-v2:0");
        assertTokens(genAiSpans.get(0), "aws.bedrock", "amazon.titan-embed-text-v2:0", 2L, null);
        assertOperation(genAiSpans.get(1), "rerank", "rerank cohere.rerank-v3-5:0");
        assertTokens(genAiSpans.get(1), "aws.bedrock", "cohere.rerank-v3-5:0", 14L, 0L);
        assertOperation(genAiSpans.get(2), "embeddings", "embeddings cohere.embed-english-v3");
        assertTokens(genAiSpans.get(2), "aws.bedrock", "cohere.embed-english-v3", 3L, 0L);
        assertOperation(genAiSpans.get(3), "rerank", "rerank amazon.rerank-v1:0");
        assertTokens(genAiSpans.get(3), "aws.bedrock", "amazon.rerank-v1:0", null, null);
        assertThat(Metrics.getLlmInputTokens("BEDROCK", "amazon.titan-embed-text-v2:0"), is(2L));
        assertThat(Metrics.getLlmInputTokens("BEDROCK", "cohere.embed-english-v3"), is(3L));
    }

    @Test
    public void shouldCountABedrockInvocationWhoseBodyUsageIsNotAnthropicsFromItsHeaders() {
        enableLlmTokenMetrics();
        forwardResponse("bedrock-runtime.us-west-2.amazonaws.com", "/model/openai.gpt-oss-20b-1%3A0/invoke",
            "{\"messages\":[{\"role\":\"user\",\"content\":\"What is the weather like today?\"}],\"max_completion_tokens\":150}", json(
                "{\"id\":\"chatcmpl-1f2e3d4c\",\"object\":\"chat.completion\",\"created\":1754400000,\"model\":\"openai.gpt-oss-20b-1:0\","
                    + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Sunny.\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":21,\"completion_tokens\":37,\"total_tokens\":58}}")
                .withHeader("X-Amzn-Bedrock-Input-Token-Count", "21")
                .withHeader("X-Amzn-Bedrock-Output-Token-Count", "37"));
        forwardResponse("bedrock-runtime.us-east-1.amazonaws.com", "/model/amazon.titan-text-express-v1/invoke",
            "{\"inputText\":\"User: What is the capital of France?\\nBot:\"}", json(
                "{\"inputTextTokenCount\":11,\"results\":[{\"tokenCount\":8,\"outputText\":\"\\nParis.\",\"completionReason\":\"FINISH\"}]}")
                .withHeader("X-Amzn-Bedrock-Input-Token-Count", "11")
                .withHeader("X-Amzn-Bedrock-Output-Token-Count", "8"));

        List<SpanData> genAiSpans = genAiSpans();
        assertThat(genAiSpans, hasSize(2));
        assertTokens(genAiSpans.get(0), "aws.bedrock", "openai.gpt-oss-20b-1:0", 21L, 37L);
        assertTokens(genAiSpans.get(1), "aws.bedrock", "amazon.titan-text-express-v1", 11L, 8L);
        assertThat(Metrics.getLlmOutputTokens("BEDROCK", "amazon.titan-text-express-v1"), is(8L));
    }

    // https://docs.aws.amazon.com/bedrock/latest/userguide/inference-chat-completions.html
    @Test
    public void shouldCountABedrockOpenAiChatCompletionWholeOrStreamedWithoutTokenCountHeaders() {
        enableLlmTokenMetrics();
        forwardResponse("bedrock-runtime.us-west-2.amazonaws.com", "/openai/v1/chat/completions",
            "{\"model\":\"openai.gpt-oss-20b-1:0\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello!\"}]}", json(
                "{\"id\":\"chatcmpl-1f2e3d4c\",\"object\":\"chat.completion\",\"created\":1754400000,\"model\":\"openai.gpt-oss-20b-1:0\","
                    + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Hi.\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":21,\"completion_tokens\":37,\"total_tokens\":58}}"));
        forwardStream("bedrock-runtime.us-west-2.amazonaws.com", "/openai/v1/chat/completions",
            "{\"model\":\"openai.gpt-oss-120b-1:0\",\"stream\":true,\"stream_options\":{\"include_usage\":true},"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"Tell me a story\"}]}",
            utf8("data: {\"id\":\"chatcmpl-9a8b7c6d\",\"object\":\"chat.completion.chunk\",\"created\":1754400000,"
                + "\"model\":\"openai.gpt-oss-120b-1:0\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Once\"},"
                + "\"finish_reason\":null}],\"usage\":null}\n\n"
                + "data: {\"id\":\"chatcmpl-9a8b7c6d\",\"object\":\"chat.completion.chunk\",\"created\":1754400000,"
                + "\"model\":\"openai.gpt-oss-120b-1:0\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":null}\n\n"
                + "data: {\"id\":\"chatcmpl-9a8b7c6d\",\"object\":\"chat.completion.chunk\",\"created\":1754400000,"
                + "\"model\":\"openai.gpt-oss-120b-1:0\",\"choices\":[],"
                + "\"usage\":{\"prompt_tokens\":14,\"completion_tokens\":120,\"total_tokens\":134}}\n\n"
                + "data: [DONE]\n\n"));

        List<SpanData> genAiSpans = genAiSpans();
        assertThat(genAiSpans, hasSize(2));
        assertTokens(genAiSpans.get(0), "aws.bedrock", "openai.gpt-oss-20b-1:0", 21L, 37L);
        assertTokens(genAiSpans.get(1), "aws.bedrock", "openai.gpt-oss-120b-1:0", 14L, 120L);
        assertThat(Metrics.getLlmInputTokens("BEDROCK", "openai.gpt-oss-120b-1:0"), is(14L));
        assertThat(Metrics.getLlmOutputTokens("BEDROCK", "openai.gpt-oss-120b-1:0"), is(120L));
        assertThat(usageLogEntries(), is(empty()));
    }

    // Cohere: https://docs.cohere.com/reference/embed, /reference/chat, /reference/rerank and the
    // cohere-python types ApiMeta, Usage and ChatMessageEndEventDelta
    @Test
    public void shouldCountCohereEmbedAndChatByWhatCohereBillsAndARerankWithoutTokens() {
        enableLlmTokenMetrics();
        forwardResponse("api.cohere.com", "/v2/embed",
            "{\"model\":\"embed-v4.0\",\"texts\":[\"hello\",\"goodbye\"],\"input_type\":\"classification\",\"embedding_types\":[\"float\"]}", json(
                "{\"id\":\"da6e531f-54c6-4a73-bf92-f60566d8d753\",\"embeddings\":{\"float\":[[0.016296387,-0.008354187],[-0.04663086,0.0118255615]]},"
                    + "\"texts\":[\"hello\",\"goodbye\"],\"meta\":{\"api_version\":{\"version\":\"2\",\"is_experimental\":true},"
                    + "\"billed_units\":{\"input_tokens\":2}}}"));
        forwardResponse("api.cohere.com", "/v2/chat",
            "{\"model\":\"command-a-03-2025\",\"messages\":[{\"role\":\"user\",\"content\":\"Tell me about LLMs\"}]}", json(
                "{\"id\":\"c14c80c3-18eb-4519-9460-6c92edd8cfb4\",\"finish_reason\":\"COMPLETE\",\"message\":{\"role\":\"assistant\","
                    + "\"content\":[{\"type\":\"text\",\"text\":\"LLMs stand for Large Language Models.\"}]},"
                    + "\"usage\":{\"billed_units\":{\"input_tokens\":5,\"output_tokens\":418},\"tokens\":{\"input_tokens\":71,\"output_tokens\":418}}}"));
        forwardResponse("api.cohere.com", "/v2/rerank",
            "{\"model\":\"rerank-v3.5\",\"query\":\"What is the capital of the United States?\",\"documents\":[\"Carson City\",\"Washington, D.C.\"]}", json(
                "{\"results\":[{\"index\":1,\"relevance_score\":0.999071},{\"index\":0,\"relevance_score\":0.32713068}],"
                    + "\"id\":\"07734bd2-2473-4f07-94e1-0d9f0e6843cf\",\"meta\":{\"api_version\":{\"version\":\"2\",\"is_experimental\":false},"
                    + "\"billed_units\":{\"search_units\":1}}}"));

        List<SpanData> genAiSpans = genAiSpans();
        assertThat(genAiSpans, hasSize(3));
        assertOperation(genAiSpans.get(0), "embeddings", "embeddings embed-v4.0");
        assertTokens(genAiSpans.get(0), "cohere", "embed-v4.0", 2L, null);
        assertOperation(genAiSpans.get(1), "chat", "chat command-a-03-2025");
        assertTokens(genAiSpans.get(1), "cohere", "command-a-03-2025", 5L, 418L);
        assertOperation(genAiSpans.get(2), "rerank", "rerank rerank-v3.5");
        assertTokens(genAiSpans.get(2), "cohere", "rerank-v3.5", null, null);
        assertThat(Metrics.getLlmInputTokens("COHERE", "embed-v4.0"), is(2L));
        assertThat(Metrics.getLlmOutputTokens("COHERE", "command-a-03-2025"), is(418L));
    }

    @Test
    public void shouldCountACohereV2ChatStreamFromItsMessageEndEvent() {
        forwardStream("api.cohere.com", "/v2/chat",
            "{\"model\":\"command-a-03-2025\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}", utf8(""
                + "event: message-start\n"
                + "data: {\"id\":\"29f14a5a-11de-4cae-9800-25e4747408ea\",\"type\":\"message-start\",\"delta\":{\"message\":{\"role\":\"assistant\","
                + "\"content\":[],\"tool_plan\":\"\",\"tool_calls\":[],\"citations\":[]}}}\n\n"
                + "event: content-start\n"
                + "data: {\"type\":\"content-start\",\"index\":0,\"delta\":{\"message\":{\"content\":{\"type\":\"text\",\"text\":\"\"}}}}\n\n"
                + "event: content-delta\n"
                + "data: {\"type\":\"content-delta\",\"index\":0,\"delta\":{\"message\":{\"content\":{\"text\":\"Hello\"}}}}\n\n"
                + "event: content-end\n"
                + "data: {\"type\":\"content-end\",\"index\":0}\n\n"
                + "event: message-end\n"
                + "data: {\"type\":\"message-end\",\"delta\":{\"finish_reason\":\"COMPLETE\",\"usage\":{\"billed_units\":{\"input_tokens\":3,"
                + "\"output_tokens\":9},\"tokens\":{\"input_tokens\":69,\"output_tokens\":9}}}}\n\n"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "chat", "chat command-a-03-2025");
        assertTokens(span, "cohere", "command-a-03-2025", 3L, 9L);
        assertThat(span.getAttributes().get(stringArrayKey("gen_ai.response.finish_reasons")), contains("COMPLETE"));
        assertThat(usageLogEntries(), is(empty()));
    }

    // Voyage: https://docs.voyageai.com/reference/embeddings-api and /reranker-api
    @Test
    public void shouldCountVoyageEmbeddingsAndRerankByTheirTotalTokens() {
        enableLlmTokenMetrics();
        forwardResponse("api.voyageai.com", "/v1/embeddings", "{\"model\":\"voyage-3.5\",\"input\":[\"Sample text 1\"]}", json(
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[-0.013,0.024],\"index\":0}],"
                + "\"model\":\"voyage-3.5\",\"usage\":{\"total_tokens\":4}}"));
        forwardResponse("api.voyageai.com", "/v1/rerank",
            "{\"model\":\"rerank-2.5\",\"query\":\"Sample query\",\"documents\":[\"Sample document 1\",\"Sample document 2\"]}", json(
                "{\"object\":\"list\",\"data\":[{\"relevance_score\":0.4375,\"index\":0},{\"relevance_score\":0.421875,\"index\":1}],"
                    + "\"model\":\"rerank-2.5\",\"usage\":{\"total_tokens\":26}}"));

        List<SpanData> genAiSpans = genAiSpans();
        assertThat(genAiSpans, hasSize(2));
        assertOperation(genAiSpans.get(0), "embeddings", "embeddings voyage-3.5");
        assertTokens(genAiSpans.get(0), "voyage", "voyage-3.5", 4L, null);
        assertOperation(genAiSpans.get(1), "rerank", "rerank rerank-2.5");
        assertTokens(genAiSpans.get(1), "voyage", "rerank-2.5", 26L, null);
        assertThat(Metrics.getLlmInputTokens("VOYAGE", "rerank-2.5"), is(26L));
    }

    // https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/text-embeddings-api
    @Test
    public void shouldCountAVertexAiPredictedEmbeddingByTheModelInItsPath() {
        enableLlmTokenMetrics();
        forwardResponse("us-central1-aiplatform.googleapis.com",
            "/v1/projects/my-project/locations/us-central1/publishers/google/models/text-embedding-005:predict",
            "{\"instances\":[{\"task_type\":\"RETRIEVAL_DOCUMENT\",\"content\":\"What is life?\"},{\"content\":\"Hello\"}]}", json(
                "{\"predictions\":[{\"embeddings\":{\"statistics\":{\"truncated\":false,\"token_count\":4},\"values\":[0.01,-0.02]}},"
                    + "{\"embeddings\":{\"statistics\":{\"truncated\":false,\"token_count\":1},\"values\":[0.03,0.04]}}]}"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "embeddings", "embeddings text-embedding-005");
        assertTokens(span, "gcp.gemini", "text-embedding-005", 5L, null);
        assertThat(Metrics.getLlmInputTokens("GEMINI", "text-embedding-005"), is(5L));
    }

    // https://docs.cloud.google.com/vertex-ai/generative-ai/docs/partner-models/mistral
    @Test
    public void shouldCountMistralOnVertexAiAsMistral() {
        String path = "/v1/projects/my-project/locations/us-central1/publishers/mistralai/models/mistral-small-2503";
        forwardResponse("us-central1-aiplatform.googleapis.com", path + ":rawPredict",
            "{\"model\":\"mistral-small-2503\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}", json(
                "{\"id\":\"cmpl-8a1b2c3d4e5f\",\"object\":\"chat.completion\",\"model\":\"mistral-small-2503\",\"created\":1741010000,"
                    + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Hello!\",\"tool_calls\":null},"
                    + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3,\"total_tokens\":8}}"));
        forwardStream("us-central1-aiplatform.googleapis.com", path + ":streamRawPredict",
            "{\"model\":\"mistral-small-2503\",\"stream\":true,\"messages\":[]}", utf8(ProviderStreamFixtures.MISTRAL_CHAT));

        List<SpanData> genAiSpans = genAiSpans();
        assertThat(genAiSpans, hasSize(2));
        assertTokens(genAiSpans.get(0), "mistral", "mistral-small-2503", 5L, 3L);
        assertTokens(genAiSpans.get(1), "mistral", "mistral-small-latest", 16L, 18L);
        assertThat(recordedCostUsd(), closeTo(LlmPricing.estimateCostUsd(Provider.MISTRAL, "mistral-small-2503", 5, 3)
            + LlmPricing.estimateCostUsd(Provider.MISTRAL, "mistral-small-latest", 16, 18), USD_TOLERANCE));
        assertThat(recordedCostUsd(), greaterThan(0.0));
    }

    private List<SpanData> genAiSpansWithTokens() {
        return genAiSpans().stream()
            .filter(span -> span.getAttributes().get(longKey("gen_ai.usage.input_tokens")) != null)
            .collect(Collectors.toList());
    }

    @Test
    public void shouldCountABackgroundResponseOnceWhenAPollFindsItFinished() {
        String responseId = "resp_68a1c0b1f0f88193a5b9e0e5c0a1b2c3";
        forwardResponse("api.openai.com", "/v1/responses",
            "{\"model\":\"o3\",\"input\":\"Write a very long novel about otters in space.\",\"background\":true}", json(
                "{\"id\":\"" + responseId + "\",\"object\":\"response\",\"created_at\":1755282609,\"status\":\"queued\",\"background\":true,"
                    + "\"error\":null,\"incomplete_details\":null,\"model\":\"o3-2025-04-16\",\"output\":[],\"usage\":null}"));
        String polled = "{\"id\":\"" + responseId + "\",\"object\":\"response\",\"created_at\":1755282609,\"status\":\"%s\",\"background\":true,"
            + "\"error\":null,\"incomplete_details\":null,\"model\":\"o3-2025-04-16\",\"output\":[%s],\"usage\":%s}";
        forwardResponse("GET", "api.openai.com", "/v1/responses/" + responseId, "", json(String.format(polled, "in_progress", "", "null")));
        String finished = String.format(polled, "completed",
            "{\"type\":\"message\",\"id\":\"msg_1\",\"status\":\"completed\",\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"output_text\",\"text\":\"Once upon a time...\",\"annotations\":[]}]}",
            "{\"input_tokens\":18,\"input_tokens_details\":{\"cached_tokens\":0},\"output_tokens\":5000,"
                + "\"output_tokens_details\":{\"reasoning_tokens\":1200},\"total_tokens\":5018}");
        // the client polls again, or fetches the finished response a second time
        forwardResponse("GET", "api.openai.com", "/v1/responses/" + responseId, "", json(finished));
        forwardResponse("GET", "api.openai.com", "/v1/responses/" + responseId, "", json(finished));

        List<SpanData> counted = genAiSpansWithTokens();
        assertThat(counted, hasSize(1));
        assertTokens(counted.get(0), "openai", "o3-2025-04-16", 18L, 5000L);
        assertCost(Provider.OPENAI_RESPONSES, "o3-2025-04-16", 18, 5000);
    }

    @Test
    public void shouldNotCountABackgroundResponseAgainWhenItWasCountedFromItsStream() {
        String responseId = "resp_67c9fdcecf488190bdd9a0409de3a1ec07b8b0ad4e5eb654";
        forwardStream("api.openai.com", "/v1/responses", "{\"model\":\"gpt-4o\",\"input\":\"Hello\",\"background\":true,\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENAI_RESPONSES));
        forwardResponse("GET", "api.openai.com", "/v1/responses/" + responseId, "", json(
            "{\"id\":\"" + responseId + "\",\"object\":\"response\",\"created_at\":1741290958,\"status\":\"completed\",\"background\":true,"
                + "\"model\":\"gpt-4o-2024-08-06\",\"output\":[{\"type\":\"message\",\"id\":\"msg_67c9fdcf37fc8190ba82116e33fb28c507b8b0ad4e5eb654\","
                + "\"status\":\"completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hello there\",\"annotations\":[]}]}],"
                + "\"usage\":{\"input_tokens\":37,\"input_tokens_details\":{\"cached_tokens\":5},\"output_tokens\":11,"
                + "\"output_tokens_details\":{\"reasoning_tokens\":2},\"total_tokens\":48}}"));

        List<SpanData> counted = genAiSpansWithTokens();
        assertThat(counted, hasSize(1));
        assertCost(Provider.OPENAI_RESPONSES, "gpt-4o-2024-08-06", 37, 11);
    }

    @Test
    public void shouldCountAFinishedBackgroundResponseOnALaterPollWhenAnEarlierOneHadNoCounts() {
        String responseId = "resp_68a1c0b1f0f88193a5b9e0e5c0a1b2c4";
        String finished = "{\"id\":\"" + responseId + "\",\"object\":\"response\",\"created_at\":1755282609,\"status\":\"completed\","
            + "\"background\":true,\"model\":\"o3-2025-04-16\",\"output\":[],\"usage\":%s}";
        // a finished response whose usage object carries no counts is not counted, and does not use up its id
        forwardResponse("GET", "api.openai.com", "/v1/responses/" + responseId, "", json(String.format(finished, "{}")));
        forwardResponse("GET", "api.openai.com", "/v1/responses/" + responseId, "", json(String.format(finished,
            "{\"input_tokens\":18,\"output_tokens\":500,\"total_tokens\":518}")));
        forwardResponse("GET", "api.openai.com", "/v1/responses/" + responseId, "", json(String.format(finished,
            "{\"input_tokens\":18,\"output_tokens\":500,\"total_tokens\":518}")));

        assertThat(genAiSpans(), hasSize(1));
        assertTokens(singleGenAiSpan(), "openai", "o3-2025-04-16", 18L, 500L);
        assertCost(Provider.OPENAI_RESPONSES, "o3-2025-04-16", 18, 500);
    }

    @Test
    public void shouldCountAnXaiDeferredCompletionWhenItIsFetched() {
        String requestId = "f15c114e-f47d-40ca-8d5c-8c23d656eeb6";
        forwardResponse("api.x.ai", "/v1/chat/completions",
            "{\"model\":\"grok-4\",\"messages\":[{\"role\":\"user\",\"content\":\"What is 101*3?\"}],\"deferred\":true}",
            json("{\"request_id\":\"" + requestId + "\"}"));
        forwardResponse("GET", "api.x.ai", "/v1/chat/deferred-completion/" + requestId, "", response().withStatusCode(202).withDelay(milliseconds(0)));
        String done = "{\"id\":\"a3d1008e-4544-40d4-d075-11527e794e4a\",\"object\":\"chat.completion\",\"created\":1743770624,"
            + "\"model\":\"grok-4-0709\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"303\","
            + "\"refusal\":null},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":14,\"completion_tokens\":3,"
            + "\"total_tokens\":17},\"system_fingerprint\":\"fp_156d35dcaa\"}";
        forwardResponse("GET", "api.x.ai", "/v1/chat/deferred-completion/" + requestId, "", json(done));
        forwardResponse("GET", "api.x.ai", "/v1/chat/deferred-completion/" + requestId, "", json(done));

        List<SpanData> counted = genAiSpansWithTokens();
        assertThat(counted, hasSize(1));
        assertTokens(counted.get(0), "xai", "grok-4-0709", 14L, 3L);
        assertCost(Provider.XAI, "grok-4-0709", 14, 3);
    }

    @Test
    public void shouldCountEachMistralConversationsCallByTheModelOnItsOutputs() {
        enableLlmTokenMetrics();
        String conversation = "{\"object\":\"conversation.response\",\"conversation_id\":\"conv_06835b9dc0c7749180001ed2adbbc4e2\","
            + "\"outputs\":[{\"object\":\"entry\",\"type\":\"message.output\",\"created_at\":\"2025-05-27T13:02:21.290Z\","
            + "\"completed_at\":\"2025-05-27T13:02:22.580Z\",\"id\":\"msg_06835b9dd4a97a6e80002c8d3c5bd5d8\",\"agent_id\":null,"
            + "\"model\":\"mistral-medium-2505\",\"role\":\"assistant\",\"content\":\"Albert Einstein was a physicist.\"}],"
            + "\"usage\":{\"prompt_tokens\":%d,\"completion_tokens\":%d,\"total_tokens\":%d,\"connector_tokens\":null,\"connectors\":null}}";
        forwardResponse("api.mistral.ai", "/v1/conversations", "{\"model\":\"mistral-medium-latest\",\"inputs\":\"Who is Albert Einstein?\",\"stream\":false}",
            json(String.format(conversation, 7, 12, 19)));
        forwardResponse("api.mistral.ai", "/v1/conversations/conv_06835b9dc0c7749180001ed2adbbc4e2", "{\"inputs\":\"Translate to French.\",\"stream\":false}",
            json(String.format(conversation, 30, 14, 44)));
        forwardResponse("api.mistral.ai", "/v1/conversations/conv_06835b9dc0c7749180001ed2adbbc4e2/restart",
            "{\"inputs\":\"Translate to German.\",\"from_entry_id\":\"msg_06835b9dd4a97a6e80002c8d3c5bd5d8\",\"stream\":false}",
            json(String.format(conversation, 30, 15, 45)));

        List<SpanData> genAiSpans = genAiSpans();
        assertThat(genAiSpans, hasSize(3));
        assertTokens(genAiSpans.get(1), "mistral", "mistral-medium-2505", 30L, 14L);
        assertThat(Metrics.getLlmInputTokens("MISTRAL", "mistral-medium-2505"), is(67L));
        assertThat(Metrics.getLlmOutputTokens("MISTRAL", "mistral-medium-2505"), is(41L));
    }

    @Test
    public void shouldCountClaudeOnVertexAiButNotItsTokenCount() {
        String path = "/v1/projects/my-project/locations/us-east5/publishers/anthropic/models/claude-sonnet-4-5@20250929";
        forwardResponse("us-east5-aiplatform.googleapis.com", path + ":rawPredict",
            "{\"anthropic_version\":\"vertex-2023-10-16\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}],\"max_tokens\":256}", json(
                "{\"id\":\"msg_vrtx_01AbCdEfGhIjKlMnOpQrStUv\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-sonnet-4-5-20250929\","
                    + "\"content\":[{\"type\":\"text\",\"text\":\"Hi there\"}],\"stop_reason\":\"end_turn\",\"stop_sequence\":null,"
                    + "\"usage\":{\"input_tokens\":12,\"output_tokens\":6}}"));
        forwardResponse("us-east5-aiplatform.googleapis.com", "/v1/projects/my-project/locations/us-east5/publishers/anthropic/models/count-tokens:rawPredict",
            "{\"model\":\"claude-sonnet-4-5@20250929\",\"messages\":[{\"role\":\"user\",\"content\":\"how many tokens are in this request?\"}]}",
            json("{\"input_tokens\":14}"));
        forwardStream("us-east5-aiplatform.googleapis.com", path + ":streamRawPredict",
            "{\"anthropic_version\":\"vertex-2023-10-16\",\"stream\":true,\"messages\":[],\"max_tokens\":256}",
            utf8(ProviderStreamFixtures.ANTHROPIC_MESSAGES));

        List<SpanData> genAiSpans = genAiSpans();
        assertThat(genAiSpans, hasSize(2));
        assertTokens(genAiSpans.get(0), "anthropic", "claude-sonnet-4-5-20250929", 12L, 6L);
        assertTokens(genAiSpans.get(1), "anthropic", "claude-sonnet-4-5-20250929", 25L, 15L);
    }

    @Test
    public void shouldKeepNamingACompletionAChatCall() {
        forwardResponse("api.openai.com", "/v1/chat/completions", "{\"model\":\"gpt-4o\",\"messages\":[]}", json(
            "{\"id\":\"chatcmpl-123\",\"object\":\"chat.completion\",\"model\":\"gpt-4o-2024-08-06\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}"));

        assertOperation(singleGenAiSpan(), "chat", "chat gpt-4o-2024-08-06");
    }

    // ---- semconv well-known operation names: generate_content and text_completion ----
    // https://github.com/open-telemetry/semantic-conventions-genai/blob/main/docs/gen-ai/gen-ai-spans.md

    @Test
    public void shouldNameAGeminiGenerateContentCallGenerateContent() {
        forwardResponse("generativelanguage.googleapis.com", "/v1beta/models/gemini-2.5-flash:generateContent", "{\"contents\":[]}", json(
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}],\"role\":\"model\"},\"finishReason\":\"STOP\",\"index\":0}],"
                + "\"usageMetadata\":{\"promptTokenCount\":8,\"candidatesTokenCount\":12,\"totalTokenCount\":20},"
                + "\"modelVersion\":\"gemini-2.5-flash\",\"responseId\":\"mAitaLmkHPPlz7IPvtfUqQ4\"}"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "generate_content", "generate_content gemini-2.5-flash");
        assertTokens(span, "gcp.gemini", "gemini-2.5-flash", 8L, 12L);
    }

    @Test
    public void shouldNameARelayedGeminiStreamGenerateContentCallGenerateContent() {
        forwardStream("generativelanguage.googleapis.com", "/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse", "{\"contents\":[]}",
            utf8(ProviderStreamFixtures.GEMINI_SSE));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "generate_content", "generate_content gemini-2.5-flash");
        assertTokens(span, "gcp.gemini", "gemini-2.5-flash", 8L, 39L);
    }

    @Test
    public void shouldNameALegacyOpenAiCompletionTextCompletion() {
        // https://platform.openai.com/docs/api-reference/completions/object
        forwardResponse("api.openai.com", "/v1/completions", "{\"model\":\"gpt-3.5-turbo-instruct\",\"prompt\":\"Say this is a test\"}", json(
            "{\"id\":\"cmpl-uqkvlQyYK7bGYrRHQ0eXlWi7\",\"object\":\"text_completion\",\"created\":1589478378,\"model\":\"gpt-3.5-turbo-instruct\","
                + "\"system_fingerprint\":\"fp_44709d6fcb\",\"choices\":[{\"text\":\"\\n\\nThis is indeed a test\",\"index\":0,\"logprobs\":null,"
                + "\"finish_reason\":\"length\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":7,\"total_tokens\":12}}"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "text_completion", "text_completion gpt-3.5-turbo-instruct");
        assertTokens(span, "openai", "gpt-3.5-turbo-instruct", 5L, 7L);
    }

    @Test
    public void shouldNameAMistralFillInTheMiddleCompletionTextCompletion() {
        // https://docs.mistral.ai/api/endpoint/fim
        forwardResponse("api.mistral.ai", "/v1/fim/completions", "{\"model\":\"codestral-2508\",\"prompt\":\"def f(\",\"suffix\":\"return a + b\"}", json(
            "{\"id\":\"447e3e0d457e42e98248b5d2ef52a2a3\",\"object\":\"chat.completion\",\"model\":\"codestral-2508\","
                + "\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":91,\"total_tokens\":99},\"created\":1759496862,"
                + "\"choices\":[{\"index\":0,\"message\":{\"content\":\"a, b):\\n    \",\"tool_calls\":null,\"prefix\":false,\"role\":\"assistant\"},"
                + "\"finish_reason\":\"stop\"}]}"));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "text_completion", "text_completion codestral-2508");
        assertTokens(span, "mistral", "codestral-2508", 8L, 91L);
    }

    @Test
    public void shouldNameARelayedOllamaGenerateStreamTextCompletion() {
        ConfigurationProperties.llmBaseUrl("http://ollama.internal:11434");
        // https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-completion
        String generateStream = ""
            + "{\"model\":\"llama3.2\",\"created_at\":\"2023-08-04T08:52:19.385406455-07:00\",\"response\":\"The\",\"done\":false}\n"
            + "{\"model\":\"llama3.2\",\"created_at\":\"2023-08-04T19:22:45.499127Z\",\"response\":\"\",\"done\":true,\"done_reason\":\"stop\","
            + "\"context\":[1,2,3],\"total_duration\":10706818083,\"load_duration\":6338219291,\"prompt_eval_count\":26,"
            + "\"prompt_eval_duration\":130079000,\"eval_count\":259,\"eval_duration\":4232710000}\n";

        forwardStream("ollama.internal:11434", "/api/generate", "{\"model\":\"llama3.2\",\"prompt\":\"Why is the sky blue?\"}", utf8(generateStream));

        SpanData span = singleGenAiSpan();
        assertOperation(span, "text_completion", "text_completion llama3.2");
        assertTokens(span, "ollama", "llama3.2", 26L, 259L);
    }

    // ---- a Bedrock call names its model in the request path ----

    @Test
    public void shouldPriceABedrockConverseCallByTheModelInItsPath() {
        forwardResponse("bedrock-runtime.us-east-1.amazonaws.com", "/model/us.anthropic.claude-sonnet-4-5-20250929-v1%3A0/converse",
            "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"Hello\"}]}]}", response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"Hi there\"}]}},"
                    + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":25,\"outputTokens\":15,\"totalTokens\":40},"
                    + "\"metrics\":{\"latencyMs\":312}}")
                .withDelay(milliseconds(0)));

        assertTokens(singleGenAiSpan(), "aws.bedrock", "anthropic.claude-sonnet-4-5-20250929-v1:0", 25L, 15L);
        assertCost(Provider.BEDROCK, "anthropic.claude-sonnet-4-5-20250929-v1:0", 25, 15);
    }

    // ---- an LLM gateway reached through the configured provider ----

    @Test
    public void shouldCountAGeminiStreamFromAGatewayWhenGeminiIsTheConfiguredProvider() {
        ConfigurationProperties.llmProvider("GEMINI");

        forwardStream("llm-gateway.internal", "/v1beta/models/gemini-2.5-flash:streamGenerateContent", "{\"contents\":[]}",
            utf8(ProviderStreamFixtures.GEMINI_SSE));

        assertTokens(singleGenAiSpan(), "gcp.gemini", "gemini-2.5-flash", 8L, 39L);
        assertCost(Provider.GEMINI, "gemini-2.5-flash", 8, 39);
    }

    @Test
    public void shouldCountABedrockStreamFromAPrivateEndpointWhenBedrockIsTheConfiguredProvider() {
        ConfigurationProperties.llmProvider("BEDROCK");

        forwardResponse("bedrock.vpce.internal", "/model/anthropic.claude-sonnet-4-5-20250929-v1%3A0/converse-stream", "{\"messages\":[]}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/vnd.amazon.eventstream")
            .withBody(ProviderStreamFixtures.bedrockConverseStream())
            .withDelay(milliseconds(0)));

        assertTokens(singleGenAiSpan(), "aws.bedrock", "anthropic.claude-sonnet-4-5-20250929-v1:0", 25L, 15L);
        assertCost(Provider.BEDROCK, "anthropic.claude-sonnet-4-5-20250929-v1:0", 25, 15);
    }

    // ---- a proxyPassMappings route ----

    private void proxyPass(String pathPrefix, String targetUri, String path, String requestBody, HttpResponse upstreamResponse) {
        configuration.proxyPassMappings(List.of(ProxyPassMapping.proxyPass(pathPrefix, targetUri).withPreserveHost(true)));
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        future.complete(upstreamResponse);
        when(mockNettyHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(future);

        // the inbound Host names MockServer, so only the mapping's target says this is LLM traffic
        actionHandler.processAction(request(path).withMethod("POST").withHeader("Host", "localhost:1080").withBody(requestBody),
            mockResponseWriter, null, new HashSet<>(), false, true);
    }

    @Test
    public void shouldCountAWholeResponseForwardedThroughAProxyPassMapping() {
        proxyPass("/claude/", "https://api.anthropic.com/", "/claude/v1/messages", "{\"model\":\"claude-sonnet-4-5\",\"messages\":[]}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"id\":\"msg_01\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-sonnet-4-5-20250929\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}],\"stop_reason\":\"end_turn\",\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":25,\"output_tokens\":15}}"));

        verify(mockResponseWriter).writeResponse(any(HttpRequest.class), any(HttpResponse.class), eq(false));
        assertTokens(singleGenAiSpan(), "anthropic", "claude-sonnet-4-5-20250929", 25L, 15L);
        assertCost(Provider.ANTHROPIC, "claude-sonnet-4-5-20250929", 25, 15);
    }

    @Test
    public void shouldCountAStreamForwardedThroughAProxyPassMappingAndThenEnforceTheBudget() {
        ConfigurationProperties.llmCostBudgetUsd(0.0001);
        StreamingBody streamingBody = new StreamingBody(16);
        proxyPass("/openai/", "https://api.openai.com/", "/openai/v1/chat/completions",
            "{\"model\":\"gpt-4o\",\"stream\":true,\"stream_options\":{\"include_usage\":true}}", response()
                .withStatusCode(200)
                .withHeader("Content-Type", "text/event-stream")
                .withStreamingBody(streamingBody));
        feed(streamingBody, utf8(ProviderStreamFixtures.OPENAI_CHAT_WITH_USAGE));
        streamingBody.complete();

        assertTokens(singleGenAiSpan(), "openai", "gpt-4o-2024-08-06", 19L, 10L);
        assertCost(Provider.OPENAI, "gpt-4o-2024-08-06", 19, 10);

        proxyPass("/openai/", "https://api.openai.com/", "/openai/v1/chat/completions", "{\"model\":\"gpt-4o\"}",
            response().withStatusCode(200));
        ArgumentCaptor<HttpResponse> written = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockResponseWriter, times(2)).writeResponse(any(HttpRequest.class), written.capture(), eq(false));
        assertThat(written.getAllValues().get(1).getStatusCode(), is(429));
        verify(mockNettyHttpClient, times(1)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
    }

    @Test
    public void shouldNotCountAProxyPassToAHostThatIsNotAnLlmProvider() {
        StreamingBody streamingBody = new StreamingBody(16);
        proxyPass("/events/", "https://events.example.com/", "/events/v1/chat/completions", "{\"stream\":true}", response()
            .withStatusCode(200)
            .withHeader("Content-Type", "text/event-stream")
            .withStreamingBody(streamingBody));
        feed(streamingBody, utf8(ProviderStreamFixtures.OPENAI_CHAT_WITH_USAGE));
        streamingBody.complete();

        assertThat(genAiSpans(), is(empty()));
        assertThat(recordedCostUsd(), is(0.0));
    }

    // ---- the uncounted-call warning is rate limited per provider and model ----

    @Test
    public void shouldRepeatAMissingUsageWarningOnceItsKeyIsClearedToBoundTheRateLimiter() {
        String unseenModel = "gpt-4o-2024-08-06";
        for (int model = 0; model < 256; model++) {
            forwardStream("api.openai.com", "/v1/chat/completions", "{\"stream\":true}",
                utf8(ProviderStreamFixtures.OPENAI_CHAT_WITHOUT_USAGE.replace(unseenModel, "model-" + model)));
        }
        assertThat(usageLogEntries(), hasSize(256));

        // the clock does not move, so a repeat is held back while its key is still kept
        forwardStream("api.openai.com", "/v1/chat/completions", "{\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITHOUT_USAGE.replace(unseenModel, "model-0")));
        assertThat(usageLogEntries(), hasSize(256));

        // a 257th key clears the 256 kept, so the first model's warning can be given again
        forwardStream("api.openai.com", "/v1/chat/completions", "{\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITHOUT_USAGE.replace(unseenModel, "model-256")));
        forwardStream("api.openai.com", "/v1/chat/completions", "{\"stream\":true}",
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITHOUT_USAGE.replace(unseenModel, "model-0")));
        List<LogEntry> logged = usageLogEntries();
        assertThat(logged, hasSize(258));
        assertThat(Arrays.asList(logged.get(257).getArguments()), contains(Provider.OPENAI, "model-0"));
    }

    // ---- a stream forwarded after a request breakpoint is continued ----

    @Test
    public void shouldCountAStreamForwardedAfterARequestBreakpointIsContinued() throws InterruptedException {
        BreakpointMatcherRegistry.getInstance().register(
            request().withPath("/v1/messages"), EnumSet.of(BreakpointPhase.REQUEST), "dashboard-client-1", configuration, mockServerLogger);
        StreamingBody streamingBody = new StreamingBody(16);
        CompletableFuture<HttpResponse> upstream = new CompletableFuture<>();
        upstream.complete(response().withStatusCode(200).withHeader("Content-Type", "text/event-stream").withStreamingBody(streamingBody));
        when(mockNettyHttpClient.sendRequest(any(HttpRequest.class), any(), any())).thenReturn(upstream);

        actionHandler.processAction(request("/v1/messages").withMethod("POST").withHeader("Host", "api.anthropic.com")
                .withBody("{\"model\":\"claude-sonnet-4-5\",\"stream\":true,\"messages\":[]}"),
            mockResponseWriter, null, new HashSet<>(), true, false);

        ArgumentCaptor<WebSocketRequestCallback> paused = ArgumentCaptor.forClass(WebSocketRequestCallback.class);
        verify(mockWebSocketClientRegistry, timeout(5000)).registerForwardCallbackHandler(anyString(), paused.capture());
        verify(mockNettyHttpClient, never()).sendRequest(any(HttpRequest.class), any(), any());
        // the client replies without a modified request, which continues the original
        paused.getValue().handleError(response());

        verify(mockResponseWriter, timeout(5000)).writeResponse(any(HttpRequest.class), any(HttpResponse.class), eq(false));
        feed(streamingBody, utf8(ProviderStreamFixtures.ANTHROPIC_MESSAGES));
        streamingBody.complete();

        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (genAiSpans().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTokens(singleGenAiSpan(), "anthropic", "claude-sonnet-4-5-20250929", 25L, 15L);
        assertCost(Provider.ANTHROPIC, "claude-sonnet-4-5-20250929", 25, 15);
    }
}
