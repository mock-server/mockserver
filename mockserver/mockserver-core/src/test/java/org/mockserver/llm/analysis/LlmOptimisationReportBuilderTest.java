package org.mockserver.llm.analysis;

import org.junit.Test;
import org.mockserver.llm.analysis.LlmOptimisationReportBuilder.CapturedExchange;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class LlmOptimisationReportBuilderTest {

    private final LlmOptimisationReportBuilder builder = new LlmOptimisationReportBuilder();

    private static HttpRequest openAiRequest(String model, String systemPrompt, String userText) {
        return request()
            .withMethod("POST")
            .withPath("/v1/chat/completions")
            .withHeader("Host", "api.openai.com")
            .withBody("{\"model\":\"" + model + "\",\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + systemPrompt + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + userText + "\"}]}");
    }

    private static HttpResponse openAiUsageResponse(String model, int inputTokens, int outputTokens, String finishReason) {
        return response()
            .withStatusCode(200)
            .withBody("{\"model\":\"" + model + "\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},"
                + "\"finish_reason\":\"" + finishReason + "\"}],"
                + "\"usage\":{\"prompt_tokens\":" + inputTokens + ",\"completion_tokens\":" + outputTokens + "}}");
    }

    private static CapturedExchange exchange(HttpRequest req, HttpResponse resp, Long latency) {
        return new CapturedExchange(req, resp, latency);
    }

    private static List<String> headers() {
        return Arrays.asList("authorization", "x-api-key");
    }

    // --- empty / non-LLM ---

    @Test
    public void emptyExchangesYieldEmptyReport() {
        LlmOptimisationReport report = builder.build(Collections.emptyList(), "host:none",
            LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        assertThat(report.getCalls(), is(empty()));
        assertThat(report.getSignals(), is(empty()));
        assertEquals(0, report.getTotals().getCallCount());
        assertEquals(LlmOptimisationReport.SCHEMA_VERSION, report.getSchemaVersion());
        assertEquals("mockserver", report.getGeneratedBy());
    }

    @Test
    public void nonLlmTrafficIsIgnored() {
        HttpRequest notLlm = request().withMethod("GET").withPath("/api/users").withHeader("Host", "example.com");
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(notLlm, response().withBody("[]"), 10L)),
            "host:example.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        assertThat(report.getCalls(), is(empty()));
    }

    // --- session / grouping basis ---

    @Test
    public void sessionCarriesGroupingBasisProvidersAndModels() {
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(
                openAiRequest("gpt-4o-2024-08-06", "you are helpful", "hello"),
                openAiUsageResponse("gpt-4o-2024-08-06", 100, 20, "stop"), 1200L)),
            "host:api.openai.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());

        assertEquals("host:api.openai.com", report.getSession().getKey());
        assertEquals(LlmOptimisationReport.GroupingBasis.PROXY_HOST, report.getSession().getGroupingBasis());
        assertThat(report.getSession().getProviders(), contains("OPENAI"));
        assertThat(report.getSession().getModels(), contains("gpt-4o-2024-08-06"));
    }

    @Test
    public void groupingBasisDefaultsToProxyHostWhenNull() {
        LlmOptimisationReport report = builder.build(Collections.emptyList(), "k", null, headers(), null);
        assertEquals(LlmOptimisationReport.GroupingBasis.PROXY_HOST, report.getSession().getGroupingBasis());
    }

    // --- cost math: real usage (costIsEstimated false) ---

    @Test
    public void realUsageGivesExactTokensAndCostNotEstimated() {
        // gpt-4o pricing: 2.5 in / 10.0 out per million. 8120 in, 540 out.
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(
                openAiRequest("gpt-4o-2024-08-06", "sys", "hi"),
                openAiUsageResponse("gpt-4o-2024-08-06", 8120, 540, "tool_calls"), 2300L)),
            "host:api.openai.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());

        LlmOptimisationReport.Call call = report.getCalls().get(0);
        assertEquals(8120, call.getInputTokens());
        assertEquals(540, call.getOutputTokens());
        assertFalse(call.isCostIsEstimated());
        assertEquals("tool_calls", call.getFinishReason());
        assertEquals(2300, call.getLatencyMs());
        double expected = (8120 / 1_000_000.0) * 2.5 + (540 / 1_000_000.0) * 10.0;
        assertEquals(LlmOptimisationReportBuilder.round4(expected), call.getEstimatedCostUsd(), 0.00001);
        assertFalse(report.getTotals().isCostIsEstimated());
        assertEquals(call.getEstimatedCostUsd(), report.getTotals().getEstimatedCostUsd(), 0.00001);
    }

    // --- cost math: estimated (no usage in response) ---

    @Test
    public void absentUsageEstimatesTokensAndFlagsEstimated() {
        HttpResponse noUsage = response().withStatusCode(200)
            .withBody("{\"model\":\"gpt-4o-2024-08-06\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"a longer answer here\"},\"finish_reason\":\"stop\"}]}");
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(
                openAiRequest("gpt-4o-2024-08-06", "system context that is moderately long", "user question text"),
                noUsage, 900L)),
            "host:api.openai.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());

        LlmOptimisationReport.Call call = report.getCalls().get(0);
        assertTrue(call.isCostIsEstimated());
        assertThat(call.getInputTokens(), greaterThan(0L));
        assertTrue(report.getTotals().isCostIsEstimated());
    }

    @Test
    public void unpricedModelIsFlaggedNotShownAsConfidentZero() {
        // An unrecognised model (e.g. an Azure deployment name) cannot be priced. The call must
        // be flagged costUnknown so consumers don't render a confident $0.00; estimatedCostUsd is
        // held at 0.0 only so aggregation stays well-defined.
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(
                openAiRequest("some-unpriced-model", "sys", "hi"),
                openAiUsageResponse("some-unpriced-model", 1000, 100, "stop"), 100L)),
            "host:api.openai.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        LlmOptimisationReport.Call call = report.getCalls().get(0);
        assertTrue(call.isCostUnknown());
        assertFalse(call.isApproximateRate());
        assertEquals(0.0, call.getEstimatedCostUsd(), 0.00001);
        assertTrue(report.getTotals().isCostUnknown());
    }

    @Test
    public void pricedModelIsNotFlaggedCostUnknown() {
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(
                openAiRequest("gpt-4o-2024-08-06", "sys", "hi"),
                openAiUsageResponse("gpt-4o-2024-08-06", 1000, 100, "stop"), 100L)),
            "host:api.openai.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        assertFalse(report.getCalls().get(0).isCostUnknown());
        assertFalse(report.getTotals().isCostUnknown());
    }

    // --- approximate (placeholder) rates force an estimate flag ---

    @Test
    public void approximatePlaceholderRateForcesEstimatedFlag() {
        // gpt-5* prices are admitted placeholders — even with real usage reported, the derived
        // cost must NOT be presented as fact: costIsEstimated and approximateRate are both set.
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(
                openAiRequest("gpt-5", "sys", "hi"),
                openAiUsageResponse("gpt-5", 1000, 100, "stop"), 100L)),
            "host:api.openai.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        LlmOptimisationReport.Call call = report.getCalls().get(0);
        assertTrue(call.isApproximateRate());
        assertTrue(call.isCostIsEstimated());
        assertFalse(call.isCostUnknown());
        assertThat(call.getEstimatedCostUsd(), greaterThan(0.0));
        assertTrue(report.getTotals().isCostIsEstimated());
    }

    // --- provider detection uses the response-shape signal ---

    @Test
    public void anthropicByResponseBodyShapeClassifiedAnthropicNotOpenAi() {
        // A header-less Anthropic call (model+messages body) routed through an unknown host/path:
        // the request alone looks like OpenAI, but the response shape (type:message + stop_reason)
        // identifies Anthropic. The builder must pass the response to the detector.
        HttpRequest anthropicLike = request()
            .withMethod("POST")
            .withPath("/generate")
            .withHeader("Host", "internal-gateway.example.com")
            .withBody("{\"model\":\"claude-sonnet-4-5\",\"messages\":["
                + "{\"role\":\"user\",\"content\":\"hello\"}]}");
        HttpResponse anthropicResponse = response().withStatusCode(200)
            .withBody("{\"type\":\"message\",\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"hi there\"}],"
                + "\"stop_reason\":\"end_turn\","
                + "\"usage\":{\"input_tokens\":100,\"output_tokens\":20}}");
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(anthropicLike, anthropicResponse, 100L)),
            "host:internal-gateway.example.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST,
            headers(), Collections.emptyList());

        assertEquals(1, report.getCalls().size());
        assertEquals("ANTHROPIC", report.getCalls().get(0).getProvider());
        assertThat(report.getSession().getProviders(), contains("ANTHROPIC"));
    }

    // --- fingerprinting ---

    @Test
    public void fingerprintIsEightHexCharsAndStable() {
        String fp1 = LlmOptimisationReportBuilder.fingerprint("hello world");
        String fp2 = LlmOptimisationReportBuilder.fingerprint("hello world");
        assertEquals(8, fp1.length());
        assertEquals(fp1, fp2);
        assertTrue(fp1.matches("[0-9a-f]{8}"));
        assertFalse(fp1.equals(LlmOptimisationReportBuilder.fingerprint("different")));
    }

    @Test
    public void shortFingerprintIsFourHexChars() {
        String fp = LlmOptimisationReportBuilder.shortFingerprint("{\"city\":\"Paris\"}");
        assertEquals(4, fp.length());
        assertTrue(fp.matches("[0-9a-f]{4}"));
    }

    @Test
    public void systemPromptFingerprintPopulatedFromRequest() {
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(
                openAiRequest("gpt-4o-2024-08-06", "you are a helpful assistant", "hi"),
                openAiUsageResponse("gpt-4o-2024-08-06", 50, 10, "stop"), 100L)),
            "host:api.openai.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        LlmOptimisationReport.Call call = report.getCalls().get(0);
        assertThat(call.getSystemPromptFingerprint(), notNullValue());
        assertThat(call.getSystemPromptTokens(), greaterThan(0L));
    }

    // --- redaction metadata ---

    @Test
    public void redactionMetadataIsCarriedThrough() {
        LlmOptimisationReport report = builder.build(Collections.emptyList(), "k",
            LlmOptimisationReport.GroupingBasis.PROXY_HOST,
            Arrays.asList("authorization", "x-api-key"), Arrays.asList("apiKey"));
        assertTrue(report.getRedaction().isApplied());
        assertThat(report.getRedaction().getRedactedHeaders(), contains("authorization", "x-api-key"));
        assertThat(report.getRedaction().getRedactedBodyFields(), contains("apiKey"));
    }

    // --- totals aggregation ---

    @Test
    public void totalsAggregateAcrossCalls() {
        List<CapturedExchange> exchanges = new ArrayList<>();
        exchanges.add(exchange(openAiRequest("gpt-4o-2024-08-06", "sys", "a"),
            openAiUsageResponse("gpt-4o-2024-08-06", 100, 10, "stop"), 100L));
        exchanges.add(exchange(openAiRequest("gpt-4o-2024-08-06", "sys", "b"),
            openAiUsageResponse("gpt-4o-2024-08-06", 200, 20, "stop"), 200L));
        LlmOptimisationReport report = builder.build(exchanges, "host:api.openai.com",
            LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());

        assertEquals(2, report.getTotals().getCallCount());
        assertEquals(300, report.getTotals().getInputTokens());
        assertEquals(30, report.getTotals().getOutputTokens());
        assertEquals(300, report.getTotals().getTotalLatencyMs());
    }

    // --- verdict ---

    @Test
    public void emptyReportStillHasGradeAVerdict() {
        LlmOptimisationReport report = builder.build(Collections.emptyList(), "host:none",
            LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        LlmOptimisationReport.Verdict v = report.getVerdict();
        assertThat(v, notNullValue());
        assertEquals("A", v.getGrade());
        assertEquals("No optimisation opportunities detected.", v.getRationale());
        assertEquals(0.0, v.getTotalEstimatedSavingUsd(), 0.0);
        assertEquals(0, v.getTotalWastedInputTokens());
        assertEquals(0.0, v.getSavingFractionOfSpend(), 0.0);
    }

    @Test
    public void verdictTotalSavingNeverExceedsSpend() {
        // Many repeated calls so several overlapping signals fire; the verdict
        // headline must remain clamped to total spend.
        List<CapturedExchange> exchanges = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            exchanges.add(exchange(openAiRequest("gpt-4o-2024-08-06", "a long static system brief repeated every turn", "q" + i),
                openAiUsageResponse("gpt-4o-2024-08-06", 4000, 50, "stop"), 100L));
        }
        LlmOptimisationReport report = builder.build(exchanges, "host:api.openai.com",
            LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        LlmOptimisationReport.Verdict v = report.getVerdict();
        assertThat(v.getTotalEstimatedSavingUsd(),
            is(lessThanOrEqualTo(report.getTotals().getEstimatedCostUsd())));
        assertThat(v.getSavingFractionOfSpend(), is(lessThanOrEqualTo(1.0)));
        assertThat(v.getGrade(), notNullValue());
    }

    @Test
    public void verdictSeverityFloorPreventsGradeAWithHighSignal() {
        // A large repeated context fires a HIGH signal; even if the saving fraction
        // is tiny, grade must not be "A".
        List<CapturedExchange> exchanges = new ArrayList<>();
        StringBuilder bigSystem = new StringBuilder();
        for (int i = 0; i < 9000; i++) {
            bigSystem.append('x'); // ~2250 tokens, above LARGE_CONTEXT_TOKEN_THRESHOLD
        }
        for (int i = 0; i < 2; i++) {
            exchanges.add(exchange(openAiRequest("gpt-4o-2024-08-06", bigSystem.toString(), "q" + i),
                openAiUsageResponse("gpt-4o-2024-08-06", 100000, 50, "stop"), 100L));
        }
        LlmOptimisationReport report = builder.build(exchanges, "host:api.openai.com",
            LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        assertThat(report.getVerdict().getHighCount(), is(greaterThan(0)));
        assertThat(report.getVerdict().getGrade(), is(not("A")));
    }

    // --- KPIs ---

    @Test
    public void cacheHitRatioComputedFromCachedInputTokens() {
        HttpResponse cached = response().withStatusCode(200)
            .withBody("{\"model\":\"gpt-4o-2024-08-06\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":1000,\"completion_tokens\":10,\"prompt_tokens_details\":{\"cached_tokens\":250}}}");
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(openAiRequest("gpt-4o-2024-08-06", "sys", "hi"), cached, 100L)),
            "host:api.openai.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        assertEquals(250, report.getTotals().getCachedInputTokens());
        assertEquals(0.25, report.getTotals().getCacheHitRatio(), 1e-9);
    }

    @Test
    public void cacheHitRatioZeroWhenNoInput() {
        LlmOptimisationReport report = builder.build(Collections.emptyList(), "k",
            LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        assertEquals(0.0, report.getTotals().getCacheHitRatio(), 0.0);
    }

    @Test
    public void retryCallCountAndOneShotRateWindowed() {
        // 3 identical consecutive calls → 2 retries (calls 1 and 2 each match a prior within window).
        List<CapturedExchange> exchanges = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            exchanges.add(exchange(openAiRequest("gpt-4o-2024-08-06", "same system", "same question"),
                openAiUsageResponse("gpt-4o-2024-08-06", 500, 20, "stop"), 100L));
        }
        LlmOptimisationReport report = builder.build(exchanges, "host:api.openai.com",
            LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        assertEquals(2, report.getTotals().getRetryCallCount());
        // oneShotRate = 1 - 2/3 = 0.3333
        assertEquals(0.3333, report.getTotals().getOneShotRate(), 1e-4);
    }

    @Test
    public void noRetriesGivesOneShotRateOne() {
        List<CapturedExchange> exchanges = new ArrayList<>();
        exchanges.add(exchange(openAiRequest("gpt-4o-2024-08-06", "sys", "a different question"),
            openAiUsageResponse("gpt-4o-2024-08-06", 500, 20, "stop"), 100L));
        exchanges.add(exchange(openAiRequest("gpt-4o-2024-08-06", "sys", "another different one"),
            openAiUsageResponse("gpt-4o-2024-08-06", 700, 20, "stop"), 100L));
        LlmOptimisationReport report = builder.build(exchanges, "host:api.openai.com",
            LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        assertEquals(0, report.getTotals().getRetryCallCount());
        assertEquals(1.0, report.getTotals().getOneShotRate(), 0.0);
    }

    // --- defined tools capture ---

    @Test
    public void definedToolsCapturedFromOpenAiRequest() {
        HttpRequest withTools = request().withMethod("POST").withPath("/v1/chat/completions")
            .withHeader("Host", "api.openai.com")
            .withBody("{\"model\":\"gpt-4o-2024-08-06\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                + "\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"description\":\"Get weather\"}}]}");
        LlmOptimisationReport report = builder.build(
            Collections.singletonList(exchange(withTools, openAiUsageResponse("gpt-4o-2024-08-06", 100, 10, "stop"), 100L)),
            "host:api.openai.com", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        LlmOptimisationReport.Call call = report.getCalls().get(0);
        assertThat(call.getDefinedToolNames(), contains("get_weather"));
        assertThat(call.getDefinedToolTokens(), greaterThan(0L));
    }

    // --- Vertex AI pricing ---

    private static HttpRequest geminiRequest(String host, String path) {
        return request().withMethod("POST").withPath(path).withHeader("Host", host)
            .withBody("{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"hi\"}]}]}");
    }

    // https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference
    private static HttpResponse geminiUsageResponse(String modelVersion, int inputTokens, int outputTokens) {
        return response().withStatusCode(200).withBody(
            "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Hello\"}]},\"finishReason\":\"STOP\"}],"
                + "\"usageMetadata\":{\"promptTokenCount\":" + inputTokens + ",\"candidatesTokenCount\":" + outputTokens
                + ",\"totalTokenCount\":" + (inputTokens + outputTokens) + "},\"modelVersion\":\"" + modelVersion + "\"}");
    }

    private LlmOptimisationReport.Call singleCall(HttpRequest request, HttpResponse response) {
        LlmOptimisationReport report = builder.build(Collections.singletonList(exchange(request, response, 100L)),
            "host:any", LlmOptimisationReport.GroupingBasis.PROXY_HOST, headers(), Collections.emptyList());
        assertThat(report.getCalls().size(), is(1));
        return report.getCalls().get(0);
    }

    // Vertex AI bills Gemini 2.0 Flash at 0.15 / 0.60 USD per million input / output tokens
    // (https://cloud.google.com/vertex-ai/generative-ai/pricing), the Gemini API at 0.10 / 0.40
    @Test
    public void nativeVertexAiGeminiCallIsPricedAtVertexAiRates() {
        LlmOptimisationReport.Call call = singleCall(
            geminiRequest("us-central1-aiplatform.googleapis.com",
                "/v1/projects/my-project/locations/us-central1/publishers/google/models/gemini-2.0-flash-001:generateContent"),
            geminiUsageResponse("gemini-2.0-flash-001", 1_000_000, 1_000_000));

        assertEquals("GEMINI", call.getProvider());
        assertEquals("gemini-2.0-flash-001", call.getModel());
        assertFalse(call.isCostUnknown());
        assertFalse(call.isCostIsEstimated());
        assertEquals(0.75, call.getEstimatedCostUsd(), 0.00001);
    }

    @Test
    public void nativeVertexAiGeminiCallThroughAMultiRegionHostIsPricedAtVertexAiRates() {
        LlmOptimisationReport.Call call = singleCall(
            geminiRequest("aiplatform.eu.rep.googleapis.com:443",
                "/v1/projects/my-project/locations/eu/publishers/google/models/gemini-2.0-flash-001:streamGenerateContent"),
            geminiUsageResponse("gemini-2.0-flash-001", 1_000_000, 0));

        assertEquals(0.15, call.getEstimatedCostUsd(), 0.00001);
    }

    @Test
    public void nativeGeminiApiCallIsPricedAtGeminiApiRates() {
        LlmOptimisationReport.Call call = singleCall(
            geminiRequest("generativelanguage.googleapis.com", "/v1beta/models/gemini-2.0-flash:generateContent"),
            geminiUsageResponse("gemini-2.0-flash", 1_000_000, 1_000_000));

        assertEquals("gemini-2.0-flash", call.getModel());
        assertFalse(call.isCostUnknown());
        assertEquals(0.5, call.getEstimatedCostUsd(), 0.00001);
    }

    @Test
    public void vertexAiPricingContextIsNotSerialisedIntoTheReport() throws Exception {
        LlmOptimisationReport.Call call = singleCall(
            geminiRequest("aiplatform.googleapis.com",
                "/v1/projects/my-project/locations/global/publishers/google/models/gemini-2.0-flash-001:generateContent"),
            geminiUsageResponse("gemini-2.0-flash-001", 10, 10));

        assertTrue(call.isVertexAi());
        String json = org.mockserver.serialization.ObjectMapperFactory.createObjectMapper().writeValueAsString(call);
        assertThat(json, not(containsString("vertexAi")));
    }
}
