import { describe, it, expect } from 'vitest';
import { estimateCostUsd, PRICING_MODEL_PREFIXES } from '../lib/llmPricing';

describe('estimateCostUsd', () => {
  // -------------------------------------------------------------------------
  // Anthropic models
  // -------------------------------------------------------------------------

  it('returns expected cost for claude-sonnet-4 model', () => {
    const cost = estimateCostUsd('anthropic', 'claude-sonnet-4-20250514', 1_000_000, 1_000_000);
    // 3.00 input + 15.00 output = 18.00
    expect(cost).toBeCloseTo(18.0, 2);
  });

  it('returns expected cost for the original claude-opus-4 (4.0/4.1) model', () => {
    const cost = estimateCostUsd('anthropic', 'claude-opus-4-20250514', 1_000_000, 1_000_000);
    // 15.00 input + 75.00 output = 90.00
    expect(cost).toBeCloseTo(90.0, 2);
  });

  it('returns the cheaper claude-opus-4-8 rate (5/25), not the generic opus-4 15/75', () => {
    const cost = estimateCostUsd('anthropic', 'claude-opus-4-8-20260601', 1_000_000, 1_000_000);
    // 5.00 input + 25.00 output = 30.00 — the more-specific prefix must win
    expect(cost).toBeCloseTo(30.0, 2);
  });

  it('returns the claude-fable-5 rate (10/50)', () => {
    const cost = estimateCostUsd('anthropic', 'claude-fable-5-20260601', 1_000_000, 1_000_000);
    // 10.00 input + 50.00 output = 60.00
    expect(cost).toBeCloseTo(60.0, 2);
  });

  it('returns the claude-sonnet-4-5 rate (3/15)', () => {
    const cost = estimateCostUsd('anthropic', 'claude-sonnet-4-5-20260601', 1_000_000, 1_000_000);
    expect(cost).toBeCloseTo(18.0, 2);
  });

  it('returns the claude-haiku-4-5 rate (1/5), distinct from the older haiku-4 (0.8/4)', () => {
    const cost = estimateCostUsd('anthropic', 'claude-haiku-4-5-20260601', 1_000_000, 1_000_000);
    // 1.00 input + 5.00 output = 6.00
    expect(cost).toBeCloseTo(6.0, 2);
  });

  it('returns expected cost for the original claude-haiku-4 model', () => {
    const cost = estimateCostUsd('anthropic', 'claude-haiku-4-20250514', 1_000_000, 1_000_000);
    // 0.80 input + 4.00 output = 4.80
    expect(cost).toBeCloseTo(4.8, 2);
  });

  // -------------------------------------------------------------------------
  // OpenAI models
  // -------------------------------------------------------------------------

  it('returns expected cost for gpt-4o', () => {
    const cost = estimateCostUsd('openai', 'gpt-4o', 1_000_000, 1_000_000);
    // 2.50 input + 10.00 output = 12.50
    expect(cost).toBeCloseTo(12.5, 2);
  });

  it('returns expected cost for gpt-4o-mini', () => {
    const cost = estimateCostUsd('openai', 'gpt-4o-mini', 1_000_000, 1_000_000);
    // 0.15 input + 0.60 output = 0.75
    expect(cost).toBeCloseTo(0.75, 2);
  });

  it('returns expected cost for gpt-4.1', () => {
    const cost = estimateCostUsd('openai', 'gpt-4.1-2025-04-14', 1_000_000, 1_000_000);
    // 2.00 input + 8.00 output = 10.00
    expect(cost).toBeCloseTo(10.0, 2);
  });

  it('returns the gpt-4.1-mini rate, not the generic gpt-4.1', () => {
    const cost = estimateCostUsd('openai', 'gpt-4.1-mini', 1_000_000, 1_000_000);
    // 0.40 input + 1.60 output = 2.00
    expect(cost).toBeCloseTo(2.0, 2);
  });

  it('returns the gpt-4.1-nano rate', () => {
    const cost = estimateCostUsd('openai', 'gpt-4.1-nano', 1_000_000, 1_000_000);
    // 0.10 input + 0.40 output = 0.50
    expect(cost).toBeCloseTo(0.5, 2);
  });

  it('returns the o4-mini rate', () => {
    const cost = estimateCostUsd('openai', 'o4-mini-2025-04-16', 1_000_000, 1_000_000);
    // 1.10 input + 4.40 output = 5.50
    expect(cost).toBeCloseTo(5.5, 2);
  });

  it('returns the o3-mini rate, not the generic o3', () => {
    const cost = estimateCostUsd('openai', 'o3-mini-2025-01-31', 1_000_000, 1_000_000);
    // 1.10 input + 4.40 output = 5.50
    expect(cost).toBeCloseTo(5.5, 2);
  });

  it('returns expected cost for o3 (OpenAI Responses)', () => {
    const cost = estimateCostUsd('openai_responses', 'o3-2025-04-16', 1_000_000, 1_000_000);
    // 15.00 input + 60.00 output = 75.00
    expect(cost).toBeCloseTo(75.0, 2);
  });

  // -------------------------------------------------------------------------
  // Gemini
  // -------------------------------------------------------------------------

  it('returns expected cost for gemini-2.0-flash', () => {
    const cost = estimateCostUsd('gemini', 'gemini-2.0-flash', 1_000_000, 1_000_000);
    // 0.10 input + 0.40 output = 0.50
    expect(cost).toBeCloseTo(0.5, 2);
  });

  it('returns the gemini-2.5-flash-lite rate, not the generic 2.5-flash', () => {
    const cost = estimateCostUsd('gemini', 'gemini-2.5-flash-lite', 1_000_000, 1_000_000);
    // 0.10 input + 0.40 output = 0.50
    expect(cost).toBeCloseTo(0.5, 2);
  });

  it('returns the gemini-2.5-flash rate', () => {
    const cost = estimateCostUsd('gemini', 'gemini-2.5-flash', 1_000_000, 1_000_000);
    // 0.30 input + 2.50 output = 2.80
    expect(cost).toBeCloseTo(2.8, 2);
  });

  it('returns the Vertex AI rate for gemini-2.0-flash on a Vertex AI host', () => {
    // cloud.google.com/vertex-ai/generative-ai/pricing: 0.15 input + 0.60 output = 0.75
    expect(estimateCostUsd('gemini', 'gemini-2.0-flash-001', 1_000_000, 1_000_000, true)).toBeCloseTo(0.75, 6);
    expect(estimateCostUsd('gemini', 'gemini-2.0-flash-001', 1_000_000, 1_000_000, false)).toBeCloseTo(0.5, 6);
  });

  it('prices a non-Gemini call on a Vertex AI host as its own provider', () => {
    expect(estimateCostUsd('anthropic', 'claude-sonnet-4@20250514', 1_000_000, 1_000_000, true)).toBeCloseTo(18.0, 6);
  });

  it('prices a <publisher>/<model> id from Vertex AI\'s OpenAI-compatible endpoint at Vertex AI rates', () => {
    // cloud.google.com/vertex-ai/generative-ai/pricing: Gemini 2.0 Flash 0.15 + 0.60, Llama 3.3 70B 0.72 + 0.72
    expect(estimateCostUsd('openai', 'google/gemini-2.0-flash-001', 1_000_000, 1_000_000)).toBeCloseTo(0.75, 6);
    expect(estimateCostUsd('openai', 'google/gemini-2.5-flash@001', 1_000_000, 1_000_000)).toBeCloseTo(2.8, 6);
    expect(estimateCostUsd('openai_responses', 'google/gemini-2.5-pro', 1_000_000, 1_000_000)).toBeCloseTo(11.25, 6);
    expect(estimateCostUsd('openai', 'meta/llama-3.3-70b-instruct-maas', 1_000_000, 1_000_000)).toBeCloseTo(1.44, 6);
  });

  it('leaves a publisher id with no Vertex AI price unpriced rather than using the OpenAI table', () => {
    expect(estimateCostUsd('openai', 'meta/llama-3.3-70b-instruct', 1_000_000, 1_000_000)).toBeNull();
    expect(estimateCostUsd('openai', 'deepseek-ai/deepseek-v3.1-maas', 1_000_000, 1_000_000)).toBeNull();
    expect(estimateCostUsd('openai', 'openai/gpt-4o', 1_000_000, 1_000_000)).toBeNull();
    expect(estimateCostUsd('openai', 'constructor/gpt-4o', 1_000_000, 1_000_000)).toBeNull();
  });

  it('returns null on a Vertex AI host for a Gemini model with no Vertex AI price', () => {
    expect(estimateCostUsd('gemini', 'gemini-1.5-pro', 1_000_000, 1_000_000, true)).toBeNull();
  });

  it('returns the gemini-2.5-pro rate', () => {
    const cost = estimateCostUsd('gemini', 'gemini-2.5-pro', 1_000_000, 1_000_000);
    // 1.25 input + 10.00 output = 11.25
    expect(cost).toBeCloseTo(11.25, 2);
  });

  // -------------------------------------------------------------------------
  // Bedrock defers to Anthropic pricing
  // -------------------------------------------------------------------------

  it('returns Anthropic pricing for Bedrock anthropic.claude-sonnet-4 models', () => {
    const cost = estimateCostUsd('bedrock', 'anthropic.claude-sonnet-4-20250514-v1:0', 1_000_000, 1_000_000);
    expect(cost).toBeCloseTo(18.0, 2);
  });

  // -------------------------------------------------------------------------
  // Azure OpenAI defers to OpenAI pricing
  // -------------------------------------------------------------------------

  it('returns OpenAI pricing for Azure OpenAI gpt-4o', () => {
    const cost = estimateCostUsd('azure_openai', 'gpt-4o', 1_000_000, 1_000_000);
    expect(cost).toBeCloseTo(12.5, 2);
  });

  // -------------------------------------------------------------------------
  // Ollama is always free
  // -------------------------------------------------------------------------

  it('returns 0 for Ollama models regardless of token count', () => {
    const cost = estimateCostUsd('ollama', 'llama3:latest', 10_000, 5_000);
    expect(cost).toBe(0);
  });

  it('returns 0 for Ollama with zero tokens', () => {
    const cost = estimateCostUsd('ollama', 'anything', 0, 0);
    expect(cost).toBe(0);
  });

  // -------------------------------------------------------------------------
  // Unknown model
  // -------------------------------------------------------------------------

  it('returns null for an unknown Anthropic model', () => {
    expect(estimateCostUsd('anthropic', 'claude-ancient-1', 1000, 500)).toBeNull();
  });

  it('returns null for an unknown OpenAI model', () => {
    expect(estimateCostUsd('openai', 'gpt-3.5-turbo', 1000, 500)).toBeNull();
  });

  it('returns null for a completely unknown provider', () => {
    expect(estimateCostUsd('mistral', 'mistral-large', 1000, 500)).toBeNull();
  });

  // -------------------------------------------------------------------------
  // Zero tokens
  // -------------------------------------------------------------------------

  it('returns 0 for zero tokens on a known model', () => {
    expect(estimateCostUsd('anthropic', 'claude-sonnet-4-20250514', 0, 0)).toBe(0);
  });

  // -------------------------------------------------------------------------
  // Fractional token counts (small usage)
  // -------------------------------------------------------------------------

  it('computes fractional costs for small token counts', () => {
    const cost = estimateCostUsd('anthropic', 'claude-sonnet-4-20250514', 100, 50);
    // (100/1M)*3.00 + (50/1M)*15.00 = 0.0003 + 0.00075 = 0.00105
    expect(cost).toBeCloseTo(0.00105, 6);
  });
});

describe('estimateCostUsd — Bedrock-prefixed Claude model ids', () => {
  it('resolves anthropic.<model> to the matching Claude pricing', () => {
    const cost = estimateCostUsd('anthropic', 'anthropic.claude-sonnet-4-20250514-v1:0', 1_000_000, 1_000_000);
    expect(cost).toBeCloseTo(18.0, 2);
  });

  it('resolves a region inference-profile prefix (us.anthropic.<model>)', () => {
    // Bare "claude-opus-4" (no -8/-7/... segment) → generic opus-4 15/75 = 90.00
    const cost = estimateCostUsd('anthropic', 'us.anthropic.claude-opus-4-v1:0', 1_000_000, 1_000_000);
    expect(cost).toBeCloseTo(90.0, 2);
  });

  it('resolves a region inference-profile prefix to the newer opus-4-8 rate', () => {
    const cost = estimateCostUsd('anthropic', 'us.anthropic.claude-opus-4-8-v1:0', 1_000_000, 1_000_000);
    // claude-opus-4-8 → 5/25 = 30.00
    expect(cost).toBeCloseTo(30.0, 2);
  });

  it('still resolves a bare claude-* id unchanged', () => {
    const cost = estimateCostUsd('anthropic', 'claude-haiku-4-20250514', 1_000_000, 1_000_000);
    expect(cost).toBeCloseTo(4.8, 2);
  });
});

// ---------------------------------------------------------------------------
// DRIFT GUARD
//
// This dashboard pricing table (`mockserver-ui/src/lib/llmPricing.ts`) is a
// hand-maintained parallel copy of the Java source of truth at
// `mockserver/mockserver-core/src/main/java/org/mockserver/llm/cost/LlmPricing.java`.
// There is no automated cross-language check, so the canonical prices and
// model list are encoded below as a snapshot of the Java table.
//
// >>> WHEN PRICES OR MODELS CHANGE, UPDATE BOTH llmPricing.ts AND
// >>> LlmPricing.java TOGETHER, then update CANONICAL_PRICING / CANONICAL_MODELS
// >>> below to match. A real CI cross-language drift check (parsing the Java
// >>> file or generating one table from the other) would be a stronger guard
// >>> and is a recommended follow-up.
// ---------------------------------------------------------------------------

describe('LLM pricing drift guard (mirrors LlmPricing.java)', () => {
  // Canonical (provider, model, input/million, output/million) snapshot copied
  // from LlmPricing.java. One representative model id per prefix; the combined
  // figure is input+output for a 1M/1M request.
  const CANONICAL_PRICING: Array<{
    provider: string;
    model: string;
    input: number;
    output: number;
    vertexAi?: boolean;
  }> = [
    // Anthropic — current families
    { provider: 'anthropic', model: 'claude-fable-5-x', input: 10.0, output: 50.0 },
    { provider: 'anthropic', model: 'claude-opus-4-8-x', input: 5.0, output: 25.0 },
    { provider: 'anthropic', model: 'claude-opus-4-7-x', input: 5.0, output: 25.0 },
    { provider: 'anthropic', model: 'claude-opus-4-6-x', input: 5.0, output: 25.0 },
    { provider: 'anthropic', model: 'claude-opus-4-5-x', input: 5.0, output: 25.0 },
    { provider: 'anthropic', model: 'claude-sonnet-4-6-x', input: 3.0, output: 15.0 },
    { provider: 'anthropic', model: 'claude-sonnet-4-5-x', input: 3.0, output: 15.0 },
    { provider: 'anthropic', model: 'claude-haiku-4-5-x', input: 1.0, output: 5.0 },
    // Anthropic — original 4.0/4.1 families
    { provider: 'anthropic', model: 'claude-opus-4-0-x', input: 15.0, output: 75.0 },
    { provider: 'anthropic', model: 'claude-sonnet-4-0-x', input: 3.0, output: 15.0 },
    { provider: 'anthropic', model: 'claude-haiku-4-0-x', input: 0.8, output: 4.0 },
    // OpenAI
    { provider: 'openai', model: 'gpt-4o-mini', input: 0.15, output: 0.6 },
    { provider: 'openai', model: 'gpt-4o', input: 2.5, output: 10.0 },
    { provider: 'openai', model: 'gpt-4.1-mini', input: 0.4, output: 1.6 },
    { provider: 'openai', model: 'gpt-4.1-nano', input: 0.1, output: 0.4 },
    { provider: 'openai', model: 'gpt-4.1', input: 2.0, output: 8.0 },
    { provider: 'openai', model: 'o4-mini', input: 1.1, output: 4.4 },
    { provider: 'openai', model: 'o3-mini', input: 1.1, output: 4.4 },
    { provider: 'openai', model: 'o3', input: 15.0, output: 60.0 },
    // gpt-5* — APPROXIMATE placeholder rates (NOT verified, mirrors the TBC
    // comment in LlmPricing.java). A passing assertion here only confirms the
    // UI matches the Java placeholder, NOT that the price is correct.
    { provider: 'openai', model: 'gpt-5-mini', input: 0.15, output: 0.6 },
    { provider: 'openai', model: 'gpt-5-nano', input: 0.15, output: 0.6 },
    { provider: 'openai', model: 'gpt-5', input: 2.5, output: 10.0 },
    // Gemini
    { provider: 'gemini', model: 'gemini-2.5-flash-lite', input: 0.1, output: 0.4 },
    { provider: 'gemini', model: 'gemini-2.5-flash', input: 0.3, output: 2.5 },
    { provider: 'gemini', model: 'gemini-2.5-pro', input: 1.25, output: 10.0 },
    { provider: 'gemini', model: 'gemini-2.0-flash-lite', input: 0.075, output: 0.3 },
    { provider: 'gemini', model: 'gemini-2.0-flash', input: 0.1, output: 0.4 },
    // Vertex AI Gemini (VERTEX_GEMINI_PRICING), for a native Gemini call on a Vertex AI host
    { provider: 'gemini', model: 'gemini-2.5-flash-lite', input: 0.1, output: 0.4, vertexAi: true },
    { provider: 'gemini', model: 'gemini-2.5-flash', input: 0.3, output: 2.5, vertexAi: true },
    { provider: 'gemini', model: 'gemini-2.5-pro', input: 1.25, output: 10.0, vertexAi: true },
    { provider: 'gemini', model: 'gemini-2.0-flash-lite', input: 0.075, output: 0.3, vertexAi: true },
    { provider: 'gemini', model: 'gemini-2.0-flash', input: 0.15, output: 0.6, vertexAi: true },
  ];

  it.each(CANONICAL_PRICING)(
    'UI table matches canonical Java price for $provider/$model (Vertex AI: $vertexAi)',
    ({ provider, model, input, output, vertexAi }) => {
      const cost = estimateCostUsd(provider, model, 1_000_000, 1_000_000, vertexAi === true);
      expect(cost).not.toBeNull();
      // 1M input + 1M output → input/million + output/million combined.
      expect(cost).toBeCloseTo(input + output, 6);
    },
  );

  // The set of model-id prefixes in the UI table must be a SUPERSET of (or equal
  // to) the canonical model list from LlmPricing.java. New Java models without a
  // UI counterpart fail here. (UI-only prefixes are tolerated.)
  const CANONICAL_MODELS: Record<string, string[]> = {
    anthropic: [
      'claude-fable-5',
      'claude-opus-4-8',
      'claude-opus-4-7',
      'claude-opus-4-6',
      'claude-opus-4-5',
      'claude-sonnet-4-6',
      'claude-sonnet-4-5',
      'claude-haiku-4-5',
      'claude-opus-4',
      'claude-sonnet-4',
      'claude-haiku-4',
    ],
    openai: [
      'gpt-4o-mini',
      'gpt-4o',
      'gpt-4.1-mini',
      'gpt-4.1-nano',
      'gpt-4.1',
      'o4-mini',
      'o3-mini',
      'o3',
      'gpt-5-mini',
      'gpt-5-nano',
      'gpt-5',
    ],
    gemini: [
      'gemini-2.5-flash-lite',
      'gemini-2.5-flash',
      'gemini-2.5-pro',
      'gemini-2.0-flash-lite',
      'gemini-2.0-flash',
    ],
    vertex_gemini: [
      'gemini-2.5-flash-lite',
      'gemini-2.5-flash',
      'gemini-2.5-pro',
      'gemini-2.0-flash-lite',
      'gemini-2.0-flash',
    ],
    vertex_llama: ['llama-3.3-70b-instruct-maas'],
  };

  it.each(Object.keys(CANONICAL_MODELS))(
    'UI %s prefixes are a superset of the canonical Java model list',
    (provider) => {
      const uiPrefixes = new Set(PRICING_MODEL_PREFIXES[provider] ?? []);
      for (const canonical of CANONICAL_MODELS[provider] ?? []) {
        expect(uiPrefixes.has(canonical)).toBe(true);
      }
    },
  );

  // Specificity ordering: a more-specific prefix must appear before its generic
  // parent so the prefix-walk resolves the cheaper/newer rate. Guards against a
  // future edit that reorders claude-opus-4 ahead of claude-opus-4-8 (which would
  // silently bill opus-4-8 at the old 15/75 rate).
  it('places more-specific prefixes before their generic parents', () => {
    const idx = (prefixes: string[] | undefined, p: string) => (prefixes ?? []).indexOf(p);
    const anthropic = PRICING_MODEL_PREFIXES.anthropic;
    expect(idx(anthropic, 'claude-opus-4-8')).toBeLessThan(idx(anthropic, 'claude-opus-4'));
    expect(idx(anthropic, 'claude-sonnet-4-5')).toBeLessThan(idx(anthropic, 'claude-sonnet-4'));
    expect(idx(anthropic, 'claude-haiku-4-5')).toBeLessThan(idx(anthropic, 'claude-haiku-4'));

    const openai = PRICING_MODEL_PREFIXES.openai;
    expect(idx(openai, 'gpt-4o-mini')).toBeLessThan(idx(openai, 'gpt-4o'));
    expect(idx(openai, 'gpt-4.1-mini')).toBeLessThan(idx(openai, 'gpt-4.1'));
    expect(idx(openai, 'gpt-4.1-nano')).toBeLessThan(idx(openai, 'gpt-4.1'));
    expect(idx(openai, 'o3-mini')).toBeLessThan(idx(openai, 'o3'));
    expect(idx(openai, 'gpt-5-mini')).toBeLessThan(idx(openai, 'gpt-5'));
    expect(idx(openai, 'gpt-5-nano')).toBeLessThan(idx(openai, 'gpt-5'));

    const gemini = PRICING_MODEL_PREFIXES.gemini;
    expect(idx(gemini, 'gemini-2.5-flash-lite')).toBeLessThan(idx(gemini, 'gemini-2.5-flash'));
    expect(idx(gemini, 'gemini-2.0-flash-lite')).toBeLessThan(idx(gemini, 'gemini-2.0-flash'));

    const vertexGemini = PRICING_MODEL_PREFIXES.vertex_gemini;
    expect(idx(vertexGemini, 'gemini-2.5-flash-lite')).toBeLessThan(idx(vertexGemini, 'gemini-2.5-flash'));
    expect(idx(vertexGemini, 'gemini-2.0-flash-lite')).toBeLessThan(idx(vertexGemini, 'gemini-2.0-flash'));
  });
});
