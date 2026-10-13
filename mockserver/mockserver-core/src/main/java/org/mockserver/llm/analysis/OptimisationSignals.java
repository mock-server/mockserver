package org.mockserver.llm.analysis;

import org.mockserver.llm.analysis.LlmOptimisationReport.Call;
import org.mockserver.llm.analysis.LlmOptimisationReport.Fix;
import org.mockserver.llm.analysis.LlmOptimisationReport.Signal;
import org.mockserver.llm.analysis.LlmOptimisationReport.ToolCall;
import org.mockserver.llm.cost.LlmPricing;
import org.mockserver.model.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic optimisation-signal detectors over the assembled calls of an
 * {@link LlmOptimisationReport}. Pure: same calls in, same signals out — no
 * network, no LLM, no randomness.
 * <p>
 * Each detector emits at most one {@link Signal} (per id) and gives the user's
 * downstream LLM a concrete lever: prompt caching, retrieval tools, replacing an
 * LLM-mediated step with a direct endpoint, trimming tool output, constraining
 * output, or removing duplicate retries. Thresholds are v1 constants (see §6 of
 * the contract — no new config beyond the max-calls bound applied upstream).
 * Cost figures defer to {@link org.mockserver.llm.cost.LlmPricing} via the
 * already-computed per-call cost, scaled by the wasted-token fraction.
 */
public class OptimisationSignals {

    /** A system/context block of this many tokens or more is "large" when resent. */
    static final long LARGE_CONTEXT_TOKEN_THRESHOLD = 2_000;

    /** A tool result of this many tokens or more is "oversized". */
    static final long OVERSIZED_TOOL_RESULT_TOKEN_THRESHOLD = 1_000;

    /** Output tokens at or above this absolute count are "bloated". */
    static final long OUTPUT_BLOAT_ABSOLUTE_THRESHOLD = 1_500;

    /** Output tokens this many times the median (or more) are "bloated". */
    static final double OUTPUT_BLOAT_MEDIAN_MULTIPLE = 3.0;

    /** Below this cache hit ratio, a repeated cacheable prefix is under-cached. */
    static final double CACHE_HIT_TARGET = 0.5;

    /** Discount applied to cacheable-input savings (cached reads are not free). */
    static final double CACHE_DISCOUNT = 0.9;

    /** A call producing fewer than this many output tokens is "trivial" work. */
    static final long TRIVIAL_OUTPUT_TOKENS = 256;

    /** MODEL_OVERSPEND fires only above this saving fraction. */
    static final double MODEL_OVERSPEND_MIN_SAVING_FRACTION = 0.30;

    /** UNUSED_TOOL_SCHEMA escalates to MEDIUM at/above this many wasted tokens. */
    static final long UNUSED_TOOL_MEDIUM_TOKEN_THRESHOLD = 1_000;

    public List<Signal> detect(List<Call> calls) {
        return detect(calls, Collections.emptyList(), 0L);
    }

    public List<Signal> detect(List<Call> calls, List<String> providers, long cachedInputTokens) {
        List<Signal> signals = new ArrayList<>();
        if (calls == null || calls.isEmpty()) {
            return signals;
        }
        int callCount = calls.size();
        List<String> providerList = providers != null ? providers : Collections.emptyList();
        addIfPresent(signals, repeatedSystemPrompt(calls));
        addIfPresent(signals, largeStaticContextResent(calls));
        addIfPresent(signals, deterministicToolCall(calls));
        addIfPresent(signals, oversizedToolResult(calls));
        addIfPresent(signals, outputTokenBloat(calls));
        addIfPresent(signals, duplicateConsecutiveCall(calls));
        addIfPresent(signals, lowCacheHitRate(calls, providerList, cachedInputTokens));
        addIfPresent(signals, modelOverspend(calls));
        addIfPresent(signals, unusedToolSchema(calls));

        // Populate fix + urgency on every signal.
        for (Signal signal : signals) {
            signal.setUrgency(urgency(signal, callCount));
            if (signal.getFix() == null) {
                signal.setFix(fixFor(signal, calls, providerList));
            }
        }

        // Sort by urgency descending; tie-break by severity (HIGH<MEDIUM<LOW) then
        // insertion order. A stable sort preserves insertion order on full ties.
        signals.sort(Comparator
            .comparingDouble((Signal s) -> s.getUrgency()).reversed()
            .thenComparingInt(s -> severityRank(s.getSeverity())));
        return signals;
    }

    private static double urgency(Signal signal, int callCount) {
        double severityWeight;
        if ("HIGH".equals(signal.getSeverity())) {
            severityWeight = 1.0;
        } else if ("MEDIUM".equals(signal.getSeverity())) {
            severityWeight = 0.6;
        } else {
            severityWeight = 0.3;
        }
        double callShare = callCount > 0
            ? Math.min(1.0, (double) signal.getAffectedCalls().size() / callCount)
            : 0.0;
        return LlmOptimisationReportBuilder.round4(severityWeight * callShare);
    }

    private static void addIfPresent(List<Signal> signals, Signal signal) {
        if (signal != null) {
            signals.add(signal);
        }
    }

    private static int severityRank(String severity) {
        if ("HIGH".equals(severity)) {
            return 0;
        }
        if ("MEDIUM".equals(severity)) {
            return 1;
        }
        return 2;
    }

    // --- REPEATED_SYSTEM_PROMPT ---

    private Signal repeatedSystemPrompt(List<Call> calls) {
        // Group calls by non-null system prompt fingerprint.
        Map<String, List<Integer>> byFingerprint = new LinkedHashMap<>();
        Map<String, Long> tokensByFingerprint = new LinkedHashMap<>();
        for (Call call : calls) {
            String fp = call.getSystemPromptFingerprint();
            if (fp == null) {
                continue;
            }
            byFingerprint.computeIfAbsent(fp, k -> new ArrayList<>()).add(call.getIndex());
            tokensByFingerprint.put(fp, call.getSystemPromptTokens());
        }
        String worstFp = null;
        for (Map.Entry<String, List<Integer>> e : byFingerprint.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            if (worstFp == null || wastedTokens(tokensByFingerprint, byFingerprint, e.getKey())
                > wastedTokens(tokensByFingerprint, byFingerprint, worstFp)) {
                worstFp = e.getKey();
            }
        }
        if (worstFp == null) {
            return null;
        }
        List<Integer> affected = byFingerprint.get(worstFp);
        long promptTokens = tokensByFingerprint.get(worstFp);
        int repeats = affected.size();
        long wasted = promptTokens * (repeats - 1L);
        Double saving = savingForWastedInput(calls, affected, wasted);

        return new Signal()
            .setId("REPEATED_SYSTEM_PROMPT")
            .setSeverity(repeats >= 3 && promptTokens >= 1_000 ? "HIGH" : "MEDIUM")
            .setTitle("Identical " + formatTokens(promptTokens) + "-token system prompt resent on all "
                + repeats + " calls")
            .setDetail("The same system prompt (fingerprint " + worstFp + ") was sent on " + repeats + "/"
                + calls.size() + " calls, re-paying for the same input tokens each turn.")
            .setAffectedCalls(affected)
            .setEstimatedWastedInputTokens(wasted)
            .setEstimatedSavingUsd(saving)
            .setRecommendation("Enable provider prompt caching, or move the static context into a retrieval tool so it is not resent every turn.");
    }

    private static long wastedTokens(Map<String, Long> tokensByFp, Map<String, List<Integer>> byFp, String fp) {
        return tokensByFp.get(fp) * (byFp.get(fp).size() - 1L);
    }

    // --- LARGE_STATIC_CONTEXT_RESENT ---

    private Signal largeStaticContextResent(List<Call> calls) {
        // A large system prompt repeated across >= 2 calls is a large static context resent.
        Map<String, List<Integer>> byFingerprint = new LinkedHashMap<>();
        Map<String, Long> tokensByFingerprint = new LinkedHashMap<>();
        for (Call call : calls) {
            String fp = call.getSystemPromptFingerprint();
            if (fp == null || call.getSystemPromptTokens() < LARGE_CONTEXT_TOKEN_THRESHOLD) {
                continue;
            }
            byFingerprint.computeIfAbsent(fp, k -> new ArrayList<>()).add(call.getIndex());
            tokensByFingerprint.put(fp, call.getSystemPromptTokens());
        }
        for (Map.Entry<String, List<Integer>> e : byFingerprint.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            List<Integer> affected = e.getValue();
            long tokens = tokensByFingerprint.get(e.getKey());
            long wasted = tokens * (affected.size() - 1L);
            return new Signal()
                .setId("LARGE_STATIC_CONTEXT_RESENT")
                .setSeverity("HIGH")
                .setTitle("Large " + formatTokens(tokens) + "-token static context resent across "
                    + affected.size() + " calls")
                .setDetail("A " + formatTokens(tokens) + "-token context block (fingerprint " + e.getKey()
                    + ") was resent on " + affected.size() + " calls instead of being cached or retrieved on demand.")
                .setAffectedCalls(affected)
                .setEstimatedWastedInputTokens(wasted)
                .setEstimatedSavingUsd(savingForWastedInput(calls, affected, wasted))
                .setRecommendation("Move the large static context into a retrieval tool or enable prompt caching so it is sent once, not every turn.");
        }
        return null;
    }

    // --- DETERMINISTIC_TOOL_CALL ---

    private Signal deterministicToolCall(List<Call> calls) {
        // Same tool name + same args fingerprint across >= 2 calls => deterministic.
        Map<String, List<Integer>> byKey = new LinkedHashMap<>();
        Map<String, String> nameByKey = new LinkedHashMap<>();
        for (Call call : calls) {
            for (ToolCall tc : call.getToolCalls()) {
                if (tc.getName() == null || tc.getArgsFingerprint() == null) {
                    continue;
                }
                String key = tc.getName() + "|" + tc.getArgsFingerprint();
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(call.getIndex());
                nameByKey.put(key, tc.getName());
            }
        }
        for (Map.Entry<String, List<Integer>> e : byKey.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            List<Integer> affected = dedupeSorted(e.getValue());
            return new Signal()
                .setId("DETERMINISTIC_TOOL_CALL")
                .setSeverity("MEDIUM")
                .setTitle("Tool '" + nameByKey.get(e.getKey()) + "' called with identical arguments "
                    + e.getValue().size() + " times")
                .setDetail("The tool '" + nameByKey.get(e.getKey()) + "' was invoked with the same arguments across "
                    + affected.size() + " calls, so its result is deterministic and the round-trip through the model is avoidable.")
                .setAffectedCalls(affected)
                .setEstimatedWastedInputTokens(null)
                .setEstimatedSavingUsd(null)
                .setRecommendation("Replace this LLM-mediated step with a direct HTTP or MCP endpoint call and feed the result back deterministically.");
        }
        return null;
    }

    // --- OVERSIZED_TOOL_RESULT ---

    private Signal oversizedToolResult(List<Call> calls) {
        List<Integer> affected = new ArrayList<>();
        long maxResult = 0;
        String worstTool = null;
        for (Call call : calls) {
            for (ToolCall tc : call.getToolCalls()) {
                if (tc.getResultTokens() >= OVERSIZED_TOOL_RESULT_TOKEN_THRESHOLD) {
                    if (!affected.contains(call.getIndex())) {
                        affected.add(call.getIndex());
                    }
                    if (tc.getResultTokens() > maxResult) {
                        maxResult = tc.getResultTokens();
                        worstTool = tc.getName();
                    }
                }
            }
        }
        if (affected.isEmpty()) {
            return null;
        }
        return new Signal()
            .setId("OVERSIZED_TOOL_RESULT")
            .setSeverity("MEDIUM")
            .setTitle("Oversized tool result (" + formatTokens(maxResult) + " tokens"
                + (worstTool != null ? " from '" + worstTool + "'" : "") + ") inflating downstream input")
            .setDetail("A tool returned " + formatTokens(maxResult) + " tokens, which are re-sent as input on every"
                + " subsequent turn, inflating cost.")
            .setAffectedCalls(affected)
            .setEstimatedWastedInputTokens(null)
            .setEstimatedSavingUsd(null)
            .setRecommendation("Trim or summarise the tool output before returning it to the model so only the relevant fields are sent.");
    }

    // --- OUTPUT_TOKEN_BLOAT ---

    private Signal outputTokenBloat(List<Call> calls) {
        List<Long> outputs = new ArrayList<>();
        for (Call call : calls) {
            outputs.add(call.getOutputTokens());
        }
        double median = median(outputs);
        List<Integer> affected = new ArrayList<>();
        long maxOutput = 0;
        for (Call call : calls) {
            long out = call.getOutputTokens();
            boolean aboveAbsolute = out >= OUTPUT_BLOAT_ABSOLUTE_THRESHOLD;
            boolean aboveMedian = median > 0 && out >= median * OUTPUT_BLOAT_MEDIAN_MULTIPLE;
            if (aboveAbsolute || aboveMedian) {
                affected.add(call.getIndex());
                maxOutput = Math.max(maxOutput, out);
            }
        }
        if (affected.isEmpty()) {
            return null;
        }
        return new Signal()
            .setId("OUTPUT_TOKEN_BLOAT")
            .setSeverity("LOW")
            .setTitle("Output token bloat: up to " + formatTokens(maxOutput) + " output tokens on a single call")
            .setDetail("One or more calls produced far more output than the median (" + formatTokens((long) median)
                + " tokens), which is expensive and often unnecessary.")
            .setAffectedCalls(affected)
            .setEstimatedWastedInputTokens(null)
            .setEstimatedSavingUsd(null)
            .setRecommendation("Constrain output with max_tokens or a stricter response_format/JSON schema so the model returns only what is needed.");
    }

    // --- DUPLICATE_CONSECUTIVE_CALL ---

    private Signal duplicateConsecutiveCall(List<Call> calls) {
        // Near-identical consecutive calls: same path, model, message count and system prompt
        // fingerprint suggest a retry of the same request. A call is "wasted" only when it
        // duplicates its immediate predecessor (so the first call of each duplicate run is not
        // counted), summed correctly across multiple independent runs.
        List<Integer> affected = new ArrayList<>();
        java.util.Set<Integer> affectedSet = new java.util.LinkedHashSet<>();
        long wasted = 0;
        for (int i = 1; i < calls.size(); i++) {
            Call prev = calls.get(i - 1);
            Call cur = calls.get(i);
            if (sameish(prev, cur)) {
                if (affectedSet.add(prev.getIndex())) {
                    affected.add(prev.getIndex());
                }
                if (affectedSet.add(cur.getIndex())) {
                    affected.add(cur.getIndex());
                }
                // cur is a retry of prev within this run, so its input tokens are wasted.
                wasted += cur.getInputTokens();
            }
        }
        if (affected.isEmpty()) {
            return null;
        }
        return new Signal()
            .setId("DUPLICATE_CONSECUTIVE_CALL")
            .setSeverity("MEDIUM")
            .setTitle("Duplicate consecutive calls detected (likely retries)")
            .setDetail("Consecutive calls with near-identical request shape were observed, suggesting retries that re-pay for the same work.")
            .setAffectedCalls(affected)
            .setEstimatedWastedInputTokens(wasted > 0 ? wasted : null)
            .setEstimatedSavingUsd(null)
            .setRecommendation("De-duplicate or cache identical requests, and only retry on genuine transient errors with backoff.");
    }

    /**
     * Near-identical request shape: same path, model, message count, system-prompt
     * fingerprint and input-token count. Package-private and shared with
     * {@link LlmOptimisationReportBuilder}'s windowed retry counting so the two stay
     * in lock-step (one definition, two callers).
     */
    static boolean sameish(Call a, Call b) {
        return java.util.Objects.equals(a.getPath(), b.getPath())
            && java.util.Objects.equals(a.getModel(), b.getModel())
            && a.getMessageCount() == b.getMessageCount()
            && java.util.Objects.equals(a.getSystemPromptFingerprint(), b.getSystemPromptFingerprint())
            && a.getInputTokens() == b.getInputTokens();
    }

    // --- LOW_CACHE_HIT_RATE ---

    private Signal lowCacheHitRate(List<Call> calls, List<String> providers, long cachedInputTokens) {
        long totalInput = 0;
        for (Call call : calls) {
            totalInput += call.getInputTokens();
        }
        double cacheHitRatio = totalInput > 0 ? (double) cachedInputTokens / totalInput : 0.0;
        if (cacheHitRatio >= CACHE_HIT_TARGET) {
            return null;
        }
        // Group calls by repeated non-null system-prompt fingerprint (a cacheable prefix).
        Map<String, List<Integer>> byFingerprint = new LinkedHashMap<>();
        Map<String, Long> tokensByFingerprint = new LinkedHashMap<>();
        for (Call call : calls) {
            String fp = call.getSystemPromptFingerprint();
            if (fp == null) {
                continue;
            }
            byFingerprint.computeIfAbsent(fp, k -> new ArrayList<>()).add(call.getIndex());
            tokensByFingerprint.put(fp, call.getSystemPromptTokens());
        }
        long cacheableRepeatedTokens = 0;
        Set<Integer> affectedSet = new TreeSet<>();
        boolean repeatedExists = false;
        for (Map.Entry<String, List<Integer>> e : byFingerprint.entrySet()) {
            int repeats = e.getValue().size();
            if (repeats < 2) {
                continue;
            }
            repeatedExists = true;
            cacheableRepeatedTokens += tokensByFingerprint.get(e.getKey()) * (repeats - 1L);
            affectedSet.addAll(e.getValue());
        }
        if (!repeatedExists) {
            return null;
        }
        long notYetCached = Math.max(0, cacheableRepeatedTokens - cachedInputTokens);
        if (notYetCached <= 0) {
            return null;
        }
        List<Integer> affected = new ArrayList<>(affectedSet);
        Double saving = savingForWastedInput(calls, affected, notYetCached);
        if (saving != null) {
            saving = LlmOptimisationReportBuilder.round4(saving * CACHE_DISCOUNT);
        }
        boolean high = notYetCached >= LARGE_CONTEXT_TOKEN_THRESHOLD && cacheHitRatio < 0.2;
        Signal signal = new Signal()
            .setId("LOW_CACHE_HIT_RATE")
            .setSeverity(high ? "HIGH" : "MEDIUM")
            .setTitle("Low cache hit rate (" + formatPercent(cacheHitRatio) + ") on a repeated "
                + formatTokens(notYetCached) + "-token cacheable prefix")
            .setDetail("A cacheable prompt prefix is resent across calls but only "
                + formatPercent(cacheHitRatio) + " of input tokens were served from cache; "
                + formatTokens(notYetCached) + " repeated tokens are not yet cached.")
            .setAffectedCalls(affected)
            .setEstimatedWastedInputTokens(notYetCached)
            .setEstimatedSavingUsd(saving)
            .setRecommendation("Enable prompt caching for the repeated static prefix so it is billed once, not on every call.");
        signal.setFix(cacheFix(providers));
        return signal;
    }

    // --- MODEL_OVERSPEND ---

    private Signal modelOverspend(List<Call> calls) {
        // Group trivial-work candidate calls by model.
        Map<String, List<Integer>> candidatesByModel = new LinkedHashMap<>();
        Map<String, Double> costByModel = new LinkedHashMap<>();
        Map<String, Provider> providerByModel = new LinkedHashMap<>();
        Map<String, Double> savingFractionByModel = new LinkedHashMap<>();
        Map<String, LlmPricing.ModelOption> cheapestByModel = new LinkedHashMap<>();
        for (Call call : calls) {
            boolean trivial = call.getOutputTokens() < TRIVIAL_OUTPUT_TOKENS
                && call.getToolCalls().isEmpty()
                && call.getReasoningTokens() == 0;
            if (!trivial) {
                continue;
            }
            Provider provider = parseProvider(call.getProvider());
            if (provider == null || call.getModel() == null) {
                continue;
            }
            Double currentBlended = call.isVertexAi()
                ? LlmPricing.vertexAiBlendedPerMillion(provider, call.getModel())
                : LlmPricing.blendedPerMillion(provider, call.getModel());
            LlmPricing.ModelOption cheapest = call.isVertexAi()
                ? LlmPricing.cheapestVertexAiModel(provider, call.getModel())
                : LlmPricing.cheapestModel(provider, call.getModel());
            if (currentBlended == null || cheapest == null) {
                continue;
            }
            if (currentBlended <= cheapest.getBlendedPerMillion()) {
                continue; // already on (or below) the cheapest model
            }
            String model = call.getModel();
            candidatesByModel.computeIfAbsent(model, k -> new ArrayList<>()).add(call.getIndex());
            costByModel.merge(model, call.getEstimatedCostUsd(), Double::sum);
            providerByModel.put(model, provider);
            cheapestByModel.put(model, cheapest);
            savingFractionByModel.put(model,
                clamp(1.0 - cheapest.getBlendedPerMillion() / currentBlended, 0.0, 1.0));
        }
        // Pick the model with the most candidate calls; tie -> highest affected cost.
        String bestModel = null;
        for (Map.Entry<String, List<Integer>> e : candidatesByModel.entrySet()) {
            if (bestModel == null) {
                bestModel = e.getKey();
                continue;
            }
            int sizeCmp = Integer.compare(e.getValue().size(), candidatesByModel.get(bestModel).size());
            if (sizeCmp > 0
                || (sizeCmp == 0 && costByModel.get(e.getKey()) > costByModel.get(bestModel))) {
                bestModel = e.getKey();
            }
        }
        if (bestModel == null) {
            return null;
        }
        List<Integer> affected = dedupeSorted(candidatesByModel.get(bestModel));
        double savingFraction = savingFractionByModel.get(bestModel);
        if (affected.size() < 2 || savingFraction <= MODEL_OVERSPEND_MIN_SAVING_FRACTION) {
            return null;
        }
        double affectedCost = costByModel.get(bestModel);
        double saving = LlmOptimisationReportBuilder.round4(affectedCost * savingFraction);
        LlmPricing.ModelOption cheapest = cheapestByModel.get(bestModel);
        int percent = (int) Math.round(savingFraction * 100);
        String recommendation = "These " + affected.size() + " calls on `" + bestModel
            + "` produced <256-token outputs with no tools or reasoning — a smaller model such as `"
            + cheapest.getLabel() + "` would likely suffice at ~" + percent + "% lower cost.";
        return new Signal()
            .setId("MODEL_OVERSPEND")
            .setSeverity("LOW")
            .setTitle("Over-powered model on " + affected.size() + " trivial calls (`" + bestModel
                + "` → `" + cheapest.getLabel() + "`)")
            .setDetail(recommendation)
            .setAffectedCalls(affected)
            .setEstimatedWastedInputTokens(null)
            .setEstimatedSavingUsd(saving)
            .setRecommendation(recommendation);
    }

    // --- UNUSED_TOOL_SCHEMA ---

    private Signal unusedToolSchema(List<Call> calls) {
        // Tool names that were actually invoked anywhere in the session.
        Set<String> invoked = new LinkedHashSet<>();
        for (Call call : calls) {
            for (ToolCall tc : call.getToolCalls()) {
                if (tc.getName() != null) {
                    invoked.add(tc.getName());
                }
            }
        }
        // Union of all defined tool names.
        Set<String> definedUnion = new LinkedHashSet<>();
        for (Call call : calls) {
            definedUnion.addAll(call.getDefinedToolNames());
        }
        Set<String> unusedGlobal = new LinkedHashSet<>(definedUnion);
        unusedGlobal.removeAll(invoked);
        if (unusedGlobal.isEmpty()) {
            return null;
        }
        List<Integer> affected = new ArrayList<>();
        long totalWasted = 0;
        for (Call call : calls) {
            List<String> defined = call.getDefinedToolNames();
            if (defined.isEmpty() || call.getDefinedToolTokens() <= 0) {
                continue;
            }
            int unusedInCall = 0;
            for (String name : defined) {
                if (unusedGlobal.contains(name)) {
                    unusedInCall++;
                }
            }
            if (unusedInCall == 0) {
                continue;
            }
            affected.add(call.getIndex());
            totalWasted += Math.round(call.getDefinedToolTokens() * (double) unusedInCall / defined.size());
        }
        if (affected.size() < 2) {
            return null;
        }
        affected = dedupeSorted(affected);
        Double saving = savingForWastedInput(calls, affected, totalWasted);
        List<String> unusedNames = new ArrayList<>(unusedGlobal);
        List<String> shown = unusedNames.subList(0, Math.min(5, unusedNames.size()));
        String recommendation = "Tools [" + String.join(", ", shown) + "] are defined on every request but"
            + " never called — remove them from `tools` to stop re-sending ~" + formatTokens(totalWasted)
            + " tokens of unused schema each call.";
        return new Signal()
            .setId("UNUSED_TOOL_SCHEMA")
            .setSeverity(totalWasted >= UNUSED_TOOL_MEDIUM_TOKEN_THRESHOLD ? "MEDIUM" : "LOW")
            .setTitle("Unused tool schema re-sent on " + affected.size() + " calls ("
                + formatTokens(totalWasted) + " wasted tokens)")
            .setDetail("Tool definitions [" + String.join(", ", shown) + "] are sent on every request but never"
                + " invoked, so their schema is paid for as input on each call.")
            .setAffectedCalls(affected)
            .setEstimatedWastedInputTokens(totalWasted > 0 ? totalWasted : null)
            .setEstimatedSavingUsd(saving)
            .setRecommendation(recommendation);
    }

    // --- shared helpers ---

    /**
     * Scale the total session cost by the wasted-input fraction of the affected
     * calls to get a rough USD saving. Returns null when cost cannot be attributed.
     */
    private static Double savingForWastedInput(List<Call> calls, List<Integer> affected, long wastedInputTokens) {
        if (wastedInputTokens <= 0) {
            return null;
        }
        // Sum the affected calls' input tokens and their cost; attribute the saving proportionally.
        long affectedInput = 0;
        double affectedCost = 0.0;
        for (Call call : calls) {
            if (affected.contains(call.getIndex())) {
                affectedInput += call.getInputTokens();
                affectedCost += call.getEstimatedCostUsd();
            }
        }
        if (affectedInput <= 0 || affectedCost <= 0.0) {
            return null;
        }
        double fraction = Math.min(1.0, (double) wastedInputTokens / affectedInput);
        return LlmOptimisationReportBuilder.round4(affectedCost * fraction);
    }

    // --- fix builders ---

    private static final String DOCS_BASE = "https://www.mock-server.com/mock_server/ai_optimisation.html";

    /**
     * Build the structured {@link Fix} for a signal by its id. The cache fix is
     * provider-aware and supplied directly by the cache detector, so this covers
     * the other (provider-agnostic) signals.
     */
    private Fix fixFor(Signal signal, List<Call> calls, List<String> providers) {
        switch (signal.getId()) {
            case "REPEATED_SYSTEM_PROMPT":
                return cacheFix(providers);
            case "LARGE_STATIC_CONTEXT_RESENT":
                return new Fix()
                    .setSummary("Cache or retrieve static context")
                    .setAction("Move the large static context into a retrieval tool, or enable prompt caching so it is sent once, not on every turn.")
                    .setDocsUrl(DOCS_BASE + "#repeated-context");
            case "DETERMINISTIC_TOOL_CALL":
                return new Fix()
                    .setSummary("Replace tool with direct call")
                    .setAction("Call the deterministic endpoint directly (HTTP or MCP) and feed the result back, instead of routing it through the model each time.")
                    .setExampleExpectation("{\n  \"httpRequest\": { \"path\": \"/tool/lookup\" },\n  \"httpResponse\": { \"body\": { \"result\": \"...\" } }\n}")
                    .setDocsUrl(DOCS_BASE + "#deterministic-tools");
            case "OVERSIZED_TOOL_RESULT":
                return new Fix()
                    .setSummary("Trim tool output")
                    .setAction("Summarise or project the tool result to the fields the model actually needs before returning it, so it is not re-sent in full on every turn.")
                    .setDocsUrl(DOCS_BASE + "#oversized-tool-results");
            case "OUTPUT_TOKEN_BLOAT":
                return new Fix()
                    .setSummary("Constrain output length")
                    .setAction("Set max_tokens and a strict response_format / JSON schema so the model returns only what is required.")
                    .setConfigSnippet("{\n  \"max_tokens\": 512,\n  \"response_format\": { \"type\": \"json_object\" }\n}")
                    .setDocsUrl(DOCS_BASE + "#output-bloat");
            case "DUPLICATE_CONSECUTIVE_CALL":
                return new Fix()
                    .setSummary("De-duplicate and back off retries")
                    .setAction("Cache identical requests and only retry on genuine transient errors with exponential backoff.")
                    .setDocsUrl(DOCS_BASE + "#duplicate-calls");
            case "MODEL_OVERSPEND":
                return new Fix()
                    .setSummary("Use a smaller model")
                    .setAction(signal.getRecommendation())
                    .setDocsUrl(DOCS_BASE + "#model-overspend");
            case "UNUSED_TOOL_SCHEMA":
                return new Fix()
                    .setSummary("Remove unused tools")
                    .setAction(signal.getRecommendation())
                    .setDocsUrl(DOCS_BASE + "#unused-tools");
            default:
                return null;
        }
    }

    /**
     * Provider-aware caching fix. Anthropic gets a concrete {@code cache_control}
     * snippet; OpenAI/Gemini/Azure get automatic-prefix-caching advice with no
     * snippet (caching is implicit there).
     */
    private static Fix cacheFix(List<String> providers) {
        boolean anthropic = providers != null && providers.contains(Provider.ANTHROPIC.name());
        if (anthropic) {
            return new Fix()
                .setSummary("Enable prompt caching")
                .setAction("Add `cache_control:{type:ephemeral}` to the static system block so Anthropic serves the repeated prefix from cache.")
                .setConfigSnippet("{\n  \"system\": [\n    {\n      \"type\": \"text\",\n      \"text\": \"<static system prompt>\",\n      \"cache_control\": { \"type\": \"ephemeral\" }\n    }\n  ]\n}")
                .setDocsUrl(DOCS_BASE + "#prompt-caching");
        }
        return new Fix()
            .setSummary("Enable prompt caching")
            .setAction("Automatic prefix caching — keep the static prefix byte-identical and first; do not interleave volatile content before it.")
            .setDocsUrl(DOCS_BASE + "#prompt-caching");
    }

    private static Provider parseProvider(String name) {
        if (name == null) {
            return null;
        }
        try {
            return Provider.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatPercent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    private static List<Integer> dedupeSorted(List<Integer> in) {
        List<Integer> out = new ArrayList<>(new java.util.LinkedHashSet<>(in));
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static double median(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static String formatTokens(long tokens) {
        return String.format(Locale.ROOT, "%,d", tokens);
    }
}
