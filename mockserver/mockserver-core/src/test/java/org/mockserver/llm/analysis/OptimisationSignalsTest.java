package org.mockserver.llm.analysis;

import org.junit.Test;
import org.mockserver.llm.analysis.LlmOptimisationReport.Call;
import org.mockserver.llm.analysis.LlmOptimisationReport.Signal;
import org.mockserver.llm.analysis.LlmOptimisationReport.ToolCall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OptimisationSignalsTest {

    private final OptimisationSignals signals = new OptimisationSignals();

    private static Call call(int index) {
        return new Call().setIndex(index).setModel("gpt-4o-2024-08-06").setPath("/v1/chat/completions");
    }

    private static Optional<Signal> findSignal(List<Signal> list, String id) {
        return list.stream().filter(s -> id.equals(s.getId())).findFirst();
    }

    // --- REPEATED_SYSTEM_PROMPT ---

    @Test
    public void repeatedSystemPromptDetectedWhenFingerprintRepeats() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(call(i).setSystemPromptFingerprint("a1b2c3d4").setSystemPromptTokens(5400)
                .setInputTokens(8000).setEstimatedCostUsd(0.02));
        }
        Optional<Signal> signal = findSignal(signals.detect(calls), "REPEATED_SYSTEM_PROMPT");
        assertTrue(signal.isPresent());
        assertEquals("HIGH", signal.get().getSeverity());
        assertThat(signal.get().getAffectedCalls(), contains(0, 1, 2, 3, 4, 5));
        // wasted = 5400 * (6-1)
        assertEquals(Long.valueOf(27000), signal.get().getEstimatedWastedInputTokens());
        assertNotNull(signal.get().getEstimatedSavingUsd());
    }

    @Test
    public void repeatedSystemPromptNotDetectedForDistinctPrompts() {
        List<Call> calls = Arrays.asList(
            call(0).setSystemPromptFingerprint("aaaa1111").setSystemPromptTokens(100),
            call(1).setSystemPromptFingerprint("bbbb2222").setSystemPromptTokens(100));
        assertNull(findSignal(signals.detect(calls), "REPEATED_SYSTEM_PROMPT").orElse(null));
    }

    // --- LARGE_STATIC_CONTEXT_RESENT ---

    @Test
    public void largeStaticContextResentDetected() {
        List<Call> calls = Arrays.asList(
            call(0).setSystemPromptFingerprint("big00001").setSystemPromptTokens(5000).setInputTokens(6000).setEstimatedCostUsd(0.01),
            call(1).setSystemPromptFingerprint("big00001").setSystemPromptTokens(5000).setInputTokens(6000).setEstimatedCostUsd(0.01));
        Optional<Signal> signal = findSignal(signals.detect(calls), "LARGE_STATIC_CONTEXT_RESENT");
        assertTrue(signal.isPresent());
        assertEquals("HIGH", signal.get().getSeverity());
    }

    @Test
    public void smallContextDoesNotTriggerLargeStaticContext() {
        List<Call> calls = Arrays.asList(
            call(0).setSystemPromptFingerprint("sm000001").setSystemPromptTokens(100),
            call(1).setSystemPromptFingerprint("sm000001").setSystemPromptTokens(100));
        assertNull(findSignal(signals.detect(calls), "LARGE_STATIC_CONTEXT_RESENT").orElse(null));
    }

    // --- DETERMINISTIC_TOOL_CALL ---

    @Test
    public void deterministicToolCallDetectedForSameNameAndArgs() {
        List<Call> calls = new ArrayList<>();
        calls.add(call(0).setToolCalls(Collections.singletonList(
            new ToolCall().setName("get_weather").setArgsFingerprint("e5f6"))));
        calls.add(call(1).setToolCalls(Collections.singletonList(
            new ToolCall().setName("get_weather").setArgsFingerprint("e5f6"))));
        Optional<Signal> signal = findSignal(signals.detect(calls), "DETERMINISTIC_TOOL_CALL");
        assertTrue(signal.isPresent());
        assertThat(signal.get().getAffectedCalls(), contains(0, 1));
    }

    @Test
    public void differentArgsDoesNotTriggerDeterministicToolCall() {
        List<Call> calls = Arrays.asList(
            call(0).setToolCalls(Collections.singletonList(new ToolCall().setName("get_weather").setArgsFingerprint("aaaa"))),
            call(1).setToolCalls(Collections.singletonList(new ToolCall().setName("get_weather").setArgsFingerprint("bbbb"))));
        assertNull(findSignal(signals.detect(calls), "DETERMINISTIC_TOOL_CALL").orElse(null));
    }

    // --- OVERSIZED_TOOL_RESULT ---

    @Test
    public void oversizedToolResultDetected() {
        List<Call> calls = Collections.singletonList(
            call(0).setToolCalls(Collections.singletonList(
                new ToolCall().setName("fetch_docs").setArgsFingerprint("c1c1").setResultTokens(5000))));
        Optional<Signal> signal = findSignal(signals.detect(calls), "OVERSIZED_TOOL_RESULT");
        assertTrue(signal.isPresent());
        assertThat(signal.get().getAffectedCalls(), contains(0));
    }

    @Test
    public void smallToolResultDoesNotTrigger() {
        List<Call> calls = Collections.singletonList(
            call(0).setToolCalls(Collections.singletonList(
                new ToolCall().setName("fetch_docs").setResultTokens(30))));
        assertNull(findSignal(signals.detect(calls), "OVERSIZED_TOOL_RESULT").orElse(null));
    }

    // --- OUTPUT_TOKEN_BLOAT ---

    @Test
    public void outputTokenBloatDetectedAbsolute() {
        List<Call> calls = Collections.singletonList(call(0).setOutputTokens(5000));
        Optional<Signal> signal = findSignal(signals.detect(calls), "OUTPUT_TOKEN_BLOAT");
        assertTrue(signal.isPresent());
    }

    @Test
    public void outputTokenBloatDetectedAboveMedian() {
        List<Call> calls = Arrays.asList(
            call(0).setOutputTokens(100),
            call(1).setOutputTokens(100),
            call(2).setOutputTokens(400)); // 4x median of 100
        Optional<Signal> signal = findSignal(signals.detect(calls), "OUTPUT_TOKEN_BLOAT");
        assertTrue(signal.isPresent());
        assertThat(signal.get().getAffectedCalls(), contains(2));
    }

    @Test
    public void evenOutputDoesNotTriggerBloat() {
        List<Call> calls = Arrays.asList(call(0).setOutputTokens(100), call(1).setOutputTokens(120));
        assertNull(findSignal(signals.detect(calls), "OUTPUT_TOKEN_BLOAT").orElse(null));
    }

    // --- DUPLICATE_CONSECUTIVE_CALL ---

    @Test
    public void duplicateConsecutiveCallDetected() {
        Call a = call(0).setMessageCount(3).setSystemPromptFingerprint("dup00001").setInputTokens(500);
        Call b = call(1).setMessageCount(3).setSystemPromptFingerprint("dup00001").setInputTokens(500);
        Optional<Signal> signal = findSignal(signals.detect(Arrays.asList(a, b)), "DUPLICATE_CONSECUTIVE_CALL");
        assertTrue(signal.isPresent());
        assertThat(signal.get().getAffectedCalls(), contains(0, 1));
    }

    @Test
    public void duplicateWastedTokensCountedPerGroupNotAcrossGroups() {
        // Two independent duplicate runs: (0,1) and (3,4), with a distinct call 2 between.
        Call a0 = call(0).setMessageCount(3).setSystemPromptFingerprint("g1").setInputTokens(500);
        Call a1 = call(1).setMessageCount(3).setSystemPromptFingerprint("g1").setInputTokens(500);
        Call b2 = call(2).setMessageCount(7).setSystemPromptFingerprint("mid").setInputTokens(999);
        Call c3 = call(3).setMessageCount(4).setSystemPromptFingerprint("g2").setInputTokens(700);
        Call c4 = call(4).setMessageCount(4).setSystemPromptFingerprint("g2").setInputTokens(700);
        Signal signal = findSignal(signals.detect(Arrays.asList(a0, a1, b2, c3, c4)),
            "DUPLICATE_CONSECUTIVE_CALL").orElseThrow(AssertionError::new);
        assertThat(signal.getAffectedCalls(), contains(0, 1, 3, 4));
        // wasted = the retry of each group (call 1 + call 4), NOT call 3.
        assertEquals(Long.valueOf(1200), signal.getEstimatedWastedInputTokens());
    }

    @Test
    public void nonDuplicateConsecutiveDoesNotTrigger() {
        Call a = call(0).setMessageCount(3).setInputTokens(500);
        Call b = call(1).setMessageCount(5).setInputTokens(900);
        assertNull(findSignal(signals.detect(Arrays.asList(a, b)), "DUPLICATE_CONSECUTIVE_CALL").orElse(null));
    }

    // --- ordering + empty ---

    @Test
    public void signalsAreSortedHighSeverityFirst() {
        List<Call> calls = new ArrayList<>();
        // Repeated system prompt (HIGH) + output bloat (LOW) on a separate call.
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setSystemPromptFingerprint("hi000001").setSystemPromptTokens(2000)
                .setInputTokens(3000).setEstimatedCostUsd(0.01).setOutputTokens(100));
        }
        calls.add(call(3).setOutputTokens(9000)); // LOW bloat
        List<Signal> result = signals.detect(calls);
        assertTrue(result.size() >= 2);
        // first must be HIGH
        assertEquals("HIGH", result.get(0).getSeverity());
    }

    @Test
    public void noCallsYieldNoSignals() {
        assertThat(signals.detect(Collections.emptyList()), is(empty()));
        assertThat(signals.detect(null), is(empty()));
    }

    // --- LOW_CACHE_HIT_RATE ---

    @Test
    public void lowCacheHitRateDetectedWhenRepeatedPrefixUncached() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            calls.add(call(i).setSystemPromptFingerprint("cache001").setSystemPromptTokens(3000)
                .setInputTokens(4000).setEstimatedCostUsd(0.02));
        }
        // cachedInputTokens 0 → ratio 0 < 0.5; notYetCached = 3000*(4-1) = 9000 >= 2000 and ratio<0.2 → HIGH
        Optional<Signal> signal = findSignal(signals.detect(calls, Arrays.asList("OPENAI"), 0L), "LOW_CACHE_HIT_RATE");
        assertTrue(signal.isPresent());
        assertEquals("HIGH", signal.get().getSeverity());
        assertEquals(Long.valueOf(9000), signal.get().getEstimatedWastedInputTokens());
        assertThat(signal.get().getAffectedCalls(), contains(0, 1, 2, 3));
        // OpenAI → prefix-caching advice, no config snippet
        assertNotNull(signal.get().getFix());
        assertNull(signal.get().getFix().getConfigSnippet());
    }

    @Test
    public void lowCacheHitRateProvidesAnthropicCacheControlSnippet() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setProvider("ANTHROPIC").setSystemPromptFingerprint("cache002")
                .setSystemPromptTokens(2500).setInputTokens(3000).setEstimatedCostUsd(0.02));
        }
        Optional<Signal> signal = findSignal(signals.detect(calls, Arrays.asList("ANTHROPIC"), 0L), "LOW_CACHE_HIT_RATE");
        assertTrue(signal.isPresent());
        assertNotNull(signal.get().getFix().getConfigSnippet());
        assertTrue(signal.get().getFix().getConfigSnippet().contains("cache_control"));
    }

    @Test
    public void lowCacheHitRateNotFiredWhenAlreadyWellCached() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setSystemPromptFingerprint("cache003").setSystemPromptTokens(1000)
                .setInputTokens(2000).setEstimatedCostUsd(0.01));
        }
        // cached well above target → ratio 0.8 >= 0.5 → no signal
        assertNull(findSignal(signals.detect(calls, Arrays.asList("OPENAI"), 4800L), "LOW_CACHE_HIT_RATE").orElse(null));
    }

    @Test
    public void lowCacheHitRateNotFiredWithoutRepeatedPrefix() {
        List<Call> calls = Arrays.asList(
            call(0).setSystemPromptFingerprint("uniq0001").setSystemPromptTokens(3000).setInputTokens(4000).setEstimatedCostUsd(0.02),
            call(1).setSystemPromptFingerprint("uniq0002").setSystemPromptTokens(3000).setInputTokens(4000).setEstimatedCostUsd(0.02));
        assertNull(findSignal(signals.detect(calls, Arrays.asList("OPENAI"), 0L), "LOW_CACHE_HIT_RATE").orElse(null));
    }

    // --- MODEL_OVERSPEND ---

    @Test
    public void modelOverspendDetectedForTrivialCallsOnExpensiveModel() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setProvider("OPENAI").setModel("gpt-4o")
                .setOutputTokens(50).setInputTokens(200).setEstimatedCostUsd(0.01));
        }
        Optional<Signal> signal = findSignal(signals.detect(calls), "MODEL_OVERSPEND");
        assertTrue(signal.isPresent());
        assertEquals("LOW", signal.get().getSeverity());
        assertThat(signal.get().getAffectedCalls(), contains(0, 1, 2));
        assertNotNull(signal.get().getEstimatedSavingUsd());
        assertNull(signal.get().getEstimatedWastedInputTokens());
    }

    @Test
    public void modelOverspendForAVertexAiGeminiCallSuggestsAVertexAiGeminiModel() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setProvider("OPENAI").setModel("google/gemini-2.5-pro")
                .setOutputTokens(50).setInputTokens(200).setEstimatedCostUsd(0.01));
        }
        Optional<Signal> signal = findSignal(signals.detect(calls), "MODEL_OVERSPEND");
        assertTrue(signal.isPresent());
        assertTrue(signal.get().getRecommendation(), signal.get().getRecommendation().contains("`gemini-2.0-flash-lite`"));
    }

    // Gemini 2.0 Flash is 0.15 / 0.60 per million tokens on Vertex AI and 0.10 / 0.40 on the Gemini API;
    // Flash-Lite is 0.075 / 0.30 on both, so moving saves 50% on Vertex AI but only 25% on the Gemini API
    @Test
    public void modelOverspendForANativeVertexAiGeminiCallUsesVertexAiRates() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setProvider("GEMINI").setModel("gemini-2.0-flash-001").setVertexAi(true)
                .setOutputTokens(50).setInputTokens(200).setEstimatedCostUsd(0.01));
        }
        Optional<Signal> signal = findSignal(signals.detect(calls), "MODEL_OVERSPEND");
        assertTrue(signal.isPresent());
        assertTrue(signal.get().getRecommendation(), signal.get().getRecommendation().contains("`gemini-2.0-flash-lite` would likely suffice at ~50% lower cost"));
        assertEquals(0.015, signal.get().getEstimatedSavingUsd(), 0.00001);
    }

    @Test
    public void modelOverspendNotFiredForAGeminiApiCallWhoseSavingIsTooSmall() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setProvider("GEMINI").setModel("gemini-2.0-flash-001")
                .setOutputTokens(50).setInputTokens(200).setEstimatedCostUsd(0.01));
        }
        assertNull(findSignal(signals.detect(calls), "MODEL_OVERSPEND").orElse(null));
    }

    @Test
    public void modelOverspendNotFiredForTheOnlyPricedVertexAiLlamaModel() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setProvider("OPENAI").setModel("meta/llama-3.3-70b-instruct-maas")
                .setOutputTokens(50).setInputTokens(200).setEstimatedCostUsd(0.01));
        }
        assertNull(findSignal(signals.detect(calls), "MODEL_OVERSPEND").orElse(null));
    }

    @Test
    public void modelOverspendNotFiredForSingleCandidate() {
        List<Call> calls = Arrays.asList(
            call(0).setProvider("OPENAI").setModel("gpt-4o").setOutputTokens(50).setInputTokens(200).setEstimatedCostUsd(0.01),
            call(1).setProvider("OPENAI").setModel("gpt-4o").setOutputTokens(900).setInputTokens(200).setEstimatedCostUsd(0.01));
        // only one trivial candidate (call 1 output >= 256) → no fire
        assertNull(findSignal(signals.detect(calls), "MODEL_OVERSPEND").orElse(null));
    }

    @Test
    public void modelOverspendNotFiredWhenSavingFractionTooSmall() {
        // gpt-4.1-nano (0.1+0.4=0.5 blended) vs cheapest gpt-4.1-nano itself → savingFraction 0 → no fire.
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setProvider("OPENAI").setModel("gpt-4.1-nano")
                .setOutputTokens(50).setInputTokens(200).setEstimatedCostUsd(0.001));
        }
        assertNull(findSignal(signals.detect(calls), "MODEL_OVERSPEND").orElse(null));
    }

    @Test
    public void modelOverspendNotFiredWhenCallsHaveTools() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setProvider("OPENAI").setModel("gpt-4o").setOutputTokens(50).setInputTokens(200)
                .setEstimatedCostUsd(0.01).setToolCalls(Collections.singletonList(new ToolCall().setName("x").setArgsFingerprint("aa"))));
        }
        assertNull(findSignal(signals.detect(calls), "MODEL_OVERSPEND").orElse(null));
    }

    // --- UNUSED_TOOL_SCHEMA ---

    @Test
    public void unusedToolSchemaDetectedWhenToolsNeverInvoked() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setInputTokens(2000).setEstimatedCostUsd(0.02)
                .setDefinedToolNames(Arrays.asList("get_weather", "get_news")).setDefinedToolTokens(900));
        }
        Optional<Signal> signal = findSignal(signals.detect(calls), "UNUSED_TOOL_SCHEMA");
        assertTrue(signal.isPresent());
        assertThat(signal.get().getAffectedCalls(), contains(0, 1, 2));
        // 3 calls * round(900 * 2/2) = 2700 wasted >= 1000 → MEDIUM
        assertEquals("MEDIUM", signal.get().getSeverity());
        assertEquals(Long.valueOf(2700), signal.get().getEstimatedWastedInputTokens());
    }

    @Test
    public void unusedToolSchemaSeverityLowForSmallWaste() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            calls.add(call(i).setInputTokens(2000).setEstimatedCostUsd(0.02)
                .setDefinedToolNames(Arrays.asList("unused_tool")).setDefinedToolTokens(100));
        }
        Optional<Signal> signal = findSignal(signals.detect(calls), "UNUSED_TOOL_SCHEMA");
        assertTrue(signal.isPresent());
        // 2 * 100 = 200 < 1000 → LOW
        assertEquals("LOW", signal.get().getSeverity());
    }

    @Test
    public void unusedToolSchemaNotFiredWhenAllToolsInvoked() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setDefinedToolNames(Arrays.asList("get_weather")).setDefinedToolTokens(400)
                .setToolCalls(Collections.singletonList(new ToolCall().setName("get_weather").setArgsFingerprint("aa"))));
        }
        assertNull(findSignal(signals.detect(calls), "UNUSED_TOOL_SCHEMA").orElse(null));
    }

    @Test
    public void unusedToolSchemaNotFiredForSingleAffectedCall() {
        List<Call> calls = Arrays.asList(
            call(0).setDefinedToolNames(Arrays.asList("unused")).setDefinedToolTokens(900).setInputTokens(2000).setEstimatedCostUsd(0.01),
            call(1).setDefinedToolNames(Collections.emptyList()).setDefinedToolTokens(0).setInputTokens(2000));
        assertNull(findSignal(signals.detect(calls), "UNUSED_TOOL_SCHEMA").orElse(null));
    }

    // --- urgency ordering ---

    @Test
    public void urgencyIsComputedFromSeverityAndCallShare() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            calls.add(call(i).setSystemPromptFingerprint("urg00001").setSystemPromptTokens(3000)
                .setInputTokens(4000).setEstimatedCostUsd(0.02).setOutputTokens(100));
        }
        List<Signal> result = signals.detect(calls, Arrays.asList("OPENAI"), 0L);
        Signal repeated = findSignal(result, "REPEATED_SYSTEM_PROMPT").orElseThrow(AssertionError::new);
        // HIGH severityWeight 1.0 * callShare 1.0 (4/4) = 1.0
        assertEquals(1.0, repeated.getUrgency(), 1e-9);
    }

    @Test
    public void signalsSortedByUrgencyDescending() {
        List<Call> calls = new ArrayList<>();
        // 4 calls all share a HIGH repeated prompt (urgency 1.0); add one isolated LOW bloat call.
        for (int i = 0; i < 4; i++) {
            calls.add(call(i).setSystemPromptFingerprint("ord00001").setSystemPromptTokens(3000)
                .setInputTokens(4000).setEstimatedCostUsd(0.02).setOutputTokens(100));
        }
        calls.add(call(4).setOutputTokens(9000)); // LOW bloat, callShare 1/5
        List<Signal> result = signals.detect(calls, Arrays.asList("OPENAI"), 0L);
        // urgency must be non-increasing across the list
        for (int i = 1; i < result.size(); i++) {
            assertTrue("urgency should be descending",
                result.get(i - 1).getUrgency() >= result.get(i).getUrgency());
        }
        assertEquals("HIGH", result.get(0).getSeverity());
    }

    @Test
    public void everySignalHasAFix() {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(call(i).setProvider("OPENAI").setModel("gpt-4o")
                .setSystemPromptFingerprint("fix00001").setSystemPromptTokens(3000)
                .setInputTokens(4000).setEstimatedCostUsd(0.02).setOutputTokens(50)
                .setDefinedToolNames(Arrays.asList("unused_tool")).setDefinedToolTokens(900));
        }
        List<Signal> result = signals.detect(calls, Arrays.asList("OPENAI"), 0L);
        assertTrue(result.size() >= 3);
        for (Signal s : result) {
            assertNotNull("signal " + s.getId() + " must have a fix", s.getFix());
            assertNotNull(s.getFix().getSummary());
        }
    }
}
