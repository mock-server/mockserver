package org.mockserver.llm.cost;

import org.junit.Test;
import org.mockserver.model.Provider;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LlmPricingTest {

    private static final double EPS = 1e-9;

    @Test
    public void shouldPriceAnthropicSonnet() {
        // 1M input @ $3, 1M output @ $15 = $18
        Double cost = LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "claude-sonnet-4-20250514", 1_000_000, 1_000_000);
        assertThat(cost, is(closeTo(18.0, EPS)));
    }

    @Test
    public void shouldPriceAnthropicOpusAndHaiku() {
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "claude-opus-4-1", 1_000_000, 0),
            is(closeTo(15.0, EPS)));
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "claude-haiku-4", 0, 1_000_000),
            is(closeTo(4.0, EPS)));
    }

    @Test
    public void shouldDistinguishGpt4oMiniFromGpt4o() {
        // ordering matters: gpt-4o-mini must NOT bill at gpt-4o's rate
        assertThat(LlmPricing.estimateCostUsd(Provider.OPENAI, "gpt-4o-mini", 1_000_000, 0),
            is(closeTo(0.15, EPS)));
        assertThat(LlmPricing.estimateCostUsd(Provider.OPENAI, "gpt-4o", 1_000_000, 0),
            is(closeTo(2.5, EPS)));
    }

    @Test
    public void shouldPriceO3() {
        assertThat(LlmPricing.estimateCostUsd(Provider.OPENAI, "o3", 0, 1_000_000),
            is(closeTo(60.0, EPS)));
    }

    @Test
    public void shouldPriceGemini() {
        assertThat(LlmPricing.estimateCostUsd(Provider.GEMINI, "gemini-2.0-flash", 1_000_000, 1_000_000),
            is(closeTo(0.5, EPS)));
    }

    @Test
    public void shouldPriceOllamaAsFree() {
        assertThat(LlmPricing.estimateCostUsd(Provider.OLLAMA, "llama3", 5_000_000, 5_000_000),
            is(closeTo(0.0, EPS)));
    }

    @Test
    public void shouldPriceBedrockViaAnthropicAfterStrippingPrefix() {
        assertThat(LlmPricing.estimateCostUsd(Provider.BEDROCK, "anthropic.claude-sonnet-4-20250514", 1_000_000, 0),
            is(closeTo(3.0, EPS)));
    }

    @Test
    public void shouldBeCaseInsensitive() {
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "CLAUDE-SONNET-4", 1_000_000, 0),
            is(closeTo(3.0, EPS)));
    }

    @Test
    public void shouldReturnZeroForZeroTokensOnKnownModel() {
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "claude-sonnet-4", 0, 0),
            is(closeTo(0.0, EPS)));
    }

    @Test
    public void shouldPriceCurrentAnthropicModels() {
        // claude-opus-4-8: $5 input / $25 output per 1M
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "claude-opus-4-8", 1_000_000, 1_000_000),
            is(closeTo(30.0, EPS)));
        // claude-haiku-4-5: $1 input / $5 output per 1M
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "claude-haiku-4-5", 1_000_000, 0),
            is(closeTo(1.0, EPS)));
        // claude-fable-5: $10 input / $50 output per 1M
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "claude-fable-5", 0, 1_000_000),
            is(closeTo(50.0, EPS)));
    }

    @Test
    public void shouldPriceNewerModelsAheadOfGenericPrefix() {
        // the more-specific claude-opus-4-8 ($5) must win over the generic
        // claude-opus-4 ($15) entry that follows it in the table
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "claude-opus-4-8-20260101", 1_000_000, 0),
            is(closeTo(5.0, EPS)));
        // an original claude-opus-4-0 still falls through to the generic $15 tier
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, "claude-opus-4-0", 1_000_000, 0),
            is(closeTo(15.0, EPS)));
    }

    @Test
    public void shouldPriceCurrentOpenAiAndGeminiModels() {
        // gpt-4.1: $2 input / $8 output per 1M
        assertThat(LlmPricing.estimateCostUsd(Provider.OPENAI, "gpt-4.1", 1_000_000, 0),
            is(closeTo(2.0, EPS)));
        // gpt-4.1-mini must win over gpt-4.1 in the prefix walk
        assertThat(LlmPricing.estimateCostUsd(Provider.OPENAI, "gpt-4.1-mini", 1_000_000, 0),
            is(closeTo(0.4, EPS)));
        // gemini-2.5-pro: $1.25 input / $10 output per 1M
        assertThat(LlmPricing.estimateCostUsd(Provider.GEMINI, "gemini-2.5-pro", 1_000_000, 1_000_000),
            is(closeTo(11.25, EPS)));
    }

    @Test
    public void shouldResolveApproximatedGpt5ModelsToNonNull() {
        // gpt-5* prices are approximated placeholders — they must still resolve
        // to SOMETHING (not null) so a recognised model is priced, not dropped.
        assertThat(LlmPricing.getPricing(Provider.OPENAI, "gpt-5"), is(notNullValue()));
        assertThat(LlmPricing.getPricing(Provider.OPENAI, "gpt-5-mini"), is(notNullValue()));
    }

    @Test
    public void shouldFlagGpt5PlaceholderRatesAsApproximate() {
        // gpt-5* are admitted placeholders — flagged approximate so callers present any derived
        // cost as an estimate, not fact.
        assertTrue(LlmPricing.isApproximateRate(Provider.OPENAI, "gpt-5"));
        assertTrue(LlmPricing.isApproximateRate(Provider.OPENAI, "gpt-5-mini"));
        assertTrue(LlmPricing.getPricing(Provider.OPENAI, "gpt-5").isApproximate());
    }

    @Test
    public void shouldNotFlagVerifiedRatesAsApproximate() {
        assertFalse(LlmPricing.isApproximateRate(Provider.OPENAI, "gpt-4o"));
        assertFalse(LlmPricing.isApproximateRate(Provider.ANTHROPIC, "claude-sonnet-4-5"));
        // an unpriced model is not "approximate" — it is simply unknown
        assertFalse(LlmPricing.isApproximateRate(Provider.OPENAI, "some-future-model"));
        assertFalse(LlmPricing.isApproximateRate(Provider.AZURE_OPENAI, "my-deployment"));
    }

    @Test
    public void shouldReturnNullForUnknownModel() {
        assertThat(LlmPricing.estimateCostUsd(Provider.OPENAI, "some-future-model", 100, 100), is(nullValue()));
    }

    @Test
    public void shouldReturnNullForAzureDeploymentName() {
        // Azure deployments are user-defined names, not canonical model ids
        assertThat(LlmPricing.estimateCostUsd(Provider.AZURE_OPENAI, "my-gpt4o-prod", 100, 100), is(nullValue()));
    }

    @Test
    public void shouldReturnNullForNullModel() {
        assertThat(LlmPricing.estimateCostUsd(Provider.ANTHROPIC, null, 100, 100), is(nullValue()));
    }

    @Test
    public void shouldReturnNullForNullProvider() {
        assertThat(LlmPricing.estimateCostUsd(null, "claude-sonnet-4", 100, 100), is(nullValue()));
    }

    // --- embedding models never inherit a chat rate ---

    @Test
    public void shouldNotPriceCodestralEmbedAtTheCodestralChatRate() {
        for (String model : new String[]{"codestral-embed", "codestral-embed-2505", "Codestral-Embed"}) {
            assertThat(model, LlmPricing.getPricing(Provider.MISTRAL, model), is(nullValue()));
            assertThat(model, LlmPricing.estimateCostUsd(Provider.MISTRAL, model, 1_000_000, 0), is(nullValue()));
            assertThat(model, LlmPricing.blendedPerMillion(Provider.MISTRAL, model), is(nullValue()));
            assertFalse(model, LlmPricing.isApproximateRate(Provider.MISTRAL, model));
        }
        // the codestral chat model itself is still priced
        assertThat(LlmPricing.estimateCostUsd(Provider.MISTRAL, "codestral-2508", 1_000_000, 1_000_000),
            is(closeTo(1.2, EPS)));
    }

    @Test
    public void shouldNotPriceCodestralEmbedThroughOpenRouter() {
        assertThat(LlmPricing.getPricing(Provider.OPENROUTER, "mistralai/codestral-embed-2505"), is(nullValue()));
        assertThat(LlmPricing.getPricing(Provider.ORCAROUTER, "mistral/codestral-embed"), is(nullValue()));
        assertThat(LlmPricing.estimateCostUsd(Provider.OPENROUTER, "mistralai/codestral-2508", 1_000_000, 0),
            is(closeTo(0.3, EPS)));
    }

    @Test
    public void shouldLeaveEveryEmbeddingModelUnpriced() {
        assertThat(LlmPricing.getPricing(Provider.MISTRAL, "mistral-embed"), is(nullValue()));
        assertThat(LlmPricing.getPricing(Provider.OPENAI, "text-embedding-3-small"), is(nullValue()));
        assertThat(LlmPricing.getPricing(Provider.AZURE_OPENAI, "text-embedding-3-large"), is(nullValue()));
        assertThat(LlmPricing.getPricing(Provider.GEMINI, "gemini-embedding-001"), is(nullValue()));
        assertThat(LlmPricing.getPricing(Provider.BEDROCK, "amazon.titan-embed-text-v2:0"), is(nullValue()));
        assertThat(LlmPricing.getPricing(Provider.OPENROUTER, "openai/text-embedding-3-small"), is(nullValue()));
    }

    @Test
    public void shouldStillPriceOllamaEmbeddingModelsAsFree() {
        assertThat(LlmPricing.estimateCostUsd(Provider.OLLAMA, "nomic-embed-text", 1_000_000, 0),
            is(closeTo(0.0, EPS)));
    }

    @Test
    public void shouldKeepEveryIntendedPrefixMatchForDatedAndSuffixedModelIds() {
        Object[][] cases = {
            {Provider.ANTHROPIC, "claude-fable-5", 10.0, 50.0},
            {Provider.ANTHROPIC, "claude-opus-4-8-20260101", 5.0, 25.0},
            {Provider.ANTHROPIC, "claude-opus-4-7", 5.0, 25.0},
            {Provider.ANTHROPIC, "claude-opus-4-6", 5.0, 25.0},
            {Provider.ANTHROPIC, "claude-opus-4-5-20251101", 5.0, 25.0},
            {Provider.ANTHROPIC, "claude-sonnet-4-6", 3.0, 15.0},
            {Provider.ANTHROPIC, "claude-sonnet-4-5-20250929", 3.0, 15.0},
            {Provider.ANTHROPIC, "claude-haiku-4-5-20251001", 1.0, 5.0},
            {Provider.ANTHROPIC, "claude-opus-4-1-20250805", 15.0, 75.0},
            {Provider.ANTHROPIC, "claude-sonnet-4-20250514", 3.0, 15.0},
            {Provider.ANTHROPIC, "claude-haiku-4", 0.8, 4.0},
            {Provider.BEDROCK, "anthropic.claude-sonnet-4-20250514-v1:0", 3.0, 15.0},
            {Provider.OPENAI, "gpt-4o-mini-2024-07-18", 0.15, 0.6},
            {Provider.OPENAI, "gpt-4o-2024-08-06", 2.5, 10.0},
            {Provider.OPENAI, "gpt-4.1-mini-2025-04-14", 0.4, 1.6},
            {Provider.OPENAI, "gpt-4.1-nano-2025-04-14", 0.1, 0.4},
            {Provider.OPENAI, "gpt-4.1-2025-04-14", 2.0, 8.0},
            {Provider.OPENAI, "o4-mini-2025-04-16", 1.1, 4.4},
            {Provider.OPENAI, "o3-mini-2025-01-31", 1.1, 4.4},
            {Provider.OPENAI, "o3-2025-04-16", 15.0, 60.0},
            {Provider.OPENAI, "gpt-5-mini-2025-08-07", 0.15, 0.6},
            {Provider.OPENAI, "gpt-5-nano-2025-08-07", 0.15, 0.6},
            {Provider.OPENAI, "gpt-5-2025-08-07", 2.5, 10.0},
            {Provider.OPENAI_RESPONSES, "gpt-4o-2024-08-06", 2.5, 10.0},
            {Provider.GEMINI, "gemini-2.5-flash-lite-preview-06-17", 0.1, 0.4},
            {Provider.GEMINI, "gemini-2.5-flash-preview-05-20", 0.3, 2.5},
            {Provider.GEMINI, "gemini-2.5-pro-preview-06-05", 1.25, 10.0},
            {Provider.GEMINI, "gemini-2.0-flash-lite-001", 0.075, 0.3},
            {Provider.GEMINI, "gemini-2.0-flash-001", 0.1, 0.4},
            {Provider.MISTRAL, "mistral-large-latest", 2.0, 6.0},
            {Provider.MISTRAL, "mistral-medium-2505", 0.4, 2.0},
            {Provider.MISTRAL, "mistral-small-2503", 0.1, 0.3},
            {Provider.MISTRAL, "codestral-latest", 0.3, 0.9},
            {Provider.MISTRAL, "ministral-8b-2410", 0.1, 0.1},
            {Provider.MISTRAL, "ministral-3b-latest", 0.04, 0.04},
            {Provider.MISTRAL, "open-mistral-nemo-2407", 0.15, 0.15},
            {Provider.XAI, "grok-4-0709", 3.0, 15.0},
            {Provider.XAI, "grok-3-mini-beta", 0.3, 0.5},
            {Provider.XAI, "grok-3-latest", 3.0, 15.0},
            {Provider.XAI, "grok-2-1212", 2.0, 10.0},
            {Provider.XAI, "grok-beta", 5.0, 15.0},
            {Provider.DEEPSEEK, "deepseek-reasoner", 0.55, 2.19},
            {Provider.DEEPSEEK, "deepseek-chat", 0.27, 1.1},
            {Provider.GROQ, "llama-3.3-70b-versatile", 0.59, 0.79},
            {Provider.GROQ, "llama-3.1-8b-instant", 0.05, 0.08},
            {Provider.GROQ, "llama-3.1-70b-versatile", 0.59, 0.79},
            {Provider.GROQ, "llama-3.1-405b-reasoning", 1.79, 1.79},
            {Provider.GROQ, "mixtral-8x7b-32768", 0.24, 0.24},
            {Provider.GROQ, "gemma2-9b-it", 0.2, 0.2},
            {Provider.OPENROUTER, "openai/gpt-4o-2024-08-06", 2.5, 10.0},
            {Provider.OPENROUTER, "anthropic/claude-sonnet-4-5-20250929", 3.0, 15.0},
            {Provider.OPENROUTER, "google/gemini-2.5-pro-preview-06-05", 1.25, 10.0},
        };
        for (Object[] c : cases) {
            Provider provider = (Provider) c[0];
            String model = (String) c[1];
            LlmPricing.PricingEntry pricing = LlmPricing.getPricing(provider, model);
            assertThat(provider + " " + model, pricing, is(notNullValue()));
            assertThat(provider + " " + model + " input", pricing.getInputPerMillion(), is(closeTo((Double) c[2], EPS)));
            assertThat(provider + " " + model + " output", pricing.getOutputPerMillion(), is(closeTo((Double) c[3], EPS)));
        }
    }

    // --- blendedPerMillion ---

    @Test
    public void blendedPerMillionSumsInputAndOutputRates() {
        // gpt-4o: 2.5 + 10.0 = 12.5
        assertThat(LlmPricing.blendedPerMillion(Provider.OPENAI, "gpt-4o"), is(closeTo(12.5, EPS)));
        // claude-sonnet-4: 3.0 + 15.0 = 18.0
        assertThat(LlmPricing.blendedPerMillion(Provider.ANTHROPIC, "claude-sonnet-4"), is(closeTo(18.0, EPS)));
    }

    @Test
    public void blendedPerMillionNullForUnknownModel() {
        assertThat(LlmPricing.blendedPerMillion(Provider.OPENAI, "some-future-model"), is(nullValue()));
        assertThat(LlmPricing.blendedPerMillion(null, "gpt-4o"), is(nullValue()));
    }

    // --- cheapestModel ---

    @Test
    public void cheapestModelForOpenAiIsTheLowestBlendedEntry() {
        // gpt-4.1-nano (0.1 + 0.4 = 0.5) is the cheapest OpenAI entry.
        LlmPricing.ModelOption cheapest = LlmPricing.cheapestModel(Provider.OPENAI);
        assertThat(cheapest, is(notNullValue()));
        assertThat(cheapest.getLabel(), is("gpt-4.1-nano"));
        assertThat(cheapest.getBlendedPerMillion(), is(closeTo(0.5, EPS)));
    }

    @Test
    public void cheapestModelForAnthropic() {
        // claude-haiku-4 (0.8 + 4.0 = 4.8) is the cheapest Anthropic entry.
        LlmPricing.ModelOption cheapest = LlmPricing.cheapestModel(Provider.ANTHROPIC);
        assertThat(cheapest, is(notNullValue()));
        assertThat(cheapest.getLabel(), is("claude-haiku-4"));
        assertThat(cheapest.getBlendedPerMillion(), is(closeTo(4.8, EPS)));
    }

    @Test
    public void cheapestModelForGemini() {
        // gemini-2.0-flash-lite (0.075 + 0.3 = 0.375) is the cheapest Gemini entry.
        LlmPricing.ModelOption cheapest = LlmPricing.cheapestModel(Provider.GEMINI);
        assertThat(cheapest, is(notNullValue()));
        assertThat(cheapest.getLabel(), is("gemini-2.0-flash-lite"));
        assertThat(cheapest.getBlendedPerMillion(), is(closeTo(0.375, EPS)));
    }

    @Test
    public void cheapestModelNullForOllamaAndUnknownAndNull() {
        assertThat(LlmPricing.cheapestModel(Provider.OLLAMA), is(nullValue()));
        assertThat(LlmPricing.cheapestModel(Provider.AZURE_OPENAI), is(nullValue()));
        assertThat(LlmPricing.cheapestModel(null), is(nullValue()));
    }
}
