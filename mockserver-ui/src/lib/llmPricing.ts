/**
 * Estimated cost calculation for LLM API usage.
 *
 * Pure function that consults a pricing table keyed on (provider, model)
 * with inputPerMillion and outputPerMillion rates in USD.
 *
 * Returns null for unknown models so the UI can display "—".
 *
 * Pricing source / freshness
 * --------------------------
 * Rates below are public provider list prices captured 2026-06 (newer
 * families) / 2025-Q4 (original entries). They WILL drift; treat the
 * dashboard total as an estimate, not an invoice.
 * Refresh procedure: update the relevant `*_PRICING` array entry and
 * append the source URL + capture date to its inline comment.
 *
 * IMPORTANT — companion source of truth:
 * This table is a parallel copy of the Java pricing table at
 * `mockserver/mockserver-core/src/main/java/org/mockserver/llm/cost/LlmPricing.java`,
 * which is the source of truth. When refreshing rates or adding/removing
 * models, UPDATE BOTH `llmPricing.ts` AND `LlmPricing.java` TOGETHER so the
 * dashboard estimate matches the server-side cost figures. The drift guard in
 * `llmPricing.test.ts` encodes the canonical expected prices and will fail if
 * this table drifts from them.
 *
 * Sources:
 *   - Anthropic: https://www.anthropic.com/pricing
 *   - OpenAI:    https://openai.com/api/pricing
 *   - Gemini:    https://ai.google.dev/pricing
 *   - Vertex AI: https://cloud.google.com/vertex-ai/generative-ai/pricing
 */

// ---------------------------------------------------------------------------
// Pricing table
// ---------------------------------------------------------------------------

interface PricingEntry {
  inputPerMillion: number;
  outputPerMillion: number;
}

/**
 * Pricing entries keyed by a model-name prefix. The lookup walks this list
 * in order and uses the first entry whose key is a prefix of the model id
 * (case-insensitive). More-specific prefixes must appear before less-specific
 * ones so that e.g. "claude-opus-4-8" matches the 5/25 entry before the
 * generic "claude-opus-4" 15/75 catch-all, and "gpt-4o-mini" before "gpt-4o".
 *
 * Mirrors `LlmPricing.java` (the source of truth) — keep both in sync.
 */
const ANTHROPIC_PRICING: Array<[string, PricingEntry]> = [
  // Current Claude families (public list prices, anthropic.com/pricing,
  // captured 2026-06). More-specific dotted prefixes (claude-opus-4-8) come
  // before the generic claude-opus-4 so they win the prefix walk.
  ['claude-fable-5', { inputPerMillion: 10.0, outputPerMillion: 50.0 }],
  ['claude-opus-4-8', { inputPerMillion: 5.0, outputPerMillion: 25.0 }],
  ['claude-opus-4-7', { inputPerMillion: 5.0, outputPerMillion: 25.0 }],
  ['claude-opus-4-6', { inputPerMillion: 5.0, outputPerMillion: 25.0 }],
  ['claude-opus-4-5', { inputPerMillion: 5.0, outputPerMillion: 25.0 }],
  ['claude-sonnet-4-6', { inputPerMillion: 3.0, outputPerMillion: 15.0 }],
  ['claude-sonnet-4-5', { inputPerMillion: 3.0, outputPerMillion: 15.0 }],
  ['claude-haiku-4-5', { inputPerMillion: 1.0, outputPerMillion: 5.0 }],
  // Original Claude 4.0/4.1 families (claude-opus-4-0/4-1, claude-sonnet-4-0).
  ['claude-opus-4', { inputPerMillion: 15.0, outputPerMillion: 75.0 }],
  ['claude-sonnet-4', { inputPerMillion: 3.0, outputPerMillion: 15.0 }],
  ['claude-haiku-4', { inputPerMillion: 0.8, outputPerMillion: 4.0 }],
];

const OPENAI_PRICING: Array<[string, PricingEntry]> = [
  // ORDERING MATTERS — the lookup uses startsWith, so the longer
  // `gpt-4o-mini` MUST appear before `gpt-4o`. Reversing the order would
  // cause every `gpt-4o-mini` model to silently bill at gpt-4o's rate.
  ['gpt-4o-mini', { inputPerMillion: 0.15, outputPerMillion: 0.6 }],
  ['gpt-4o', { inputPerMillion: 2.5, outputPerMillion: 10.0 }],
  // gpt-4.1 family: list prices captured 2026-06 (openai.com/api/pricing).
  ['gpt-4.1-mini', { inputPerMillion: 0.4, outputPerMillion: 1.6 }],
  ['gpt-4.1-nano', { inputPerMillion: 0.1, outputPerMillion: 0.4 }],
  ['gpt-4.1', { inputPerMillion: 2.0, outputPerMillion: 8.0 }],
  // o-series reasoning models. o4-mini / o3-mini before o3 in the walk.
  ['o4-mini', { inputPerMillion: 1.1, outputPerMillion: 4.4 }],
  ['o3-mini', { inputPerMillion: 1.1, outputPerMillion: 4.4 }],
  ['o3', { inputPerMillion: 15.0, outputPerMillion: 60.0 }],
  // APPROXIMATE: gpt-5* prices are NOT verified — mapped to the nearest known
  // tier (gpt-4o flagship / gpt-4o-mini cheap tier) as a placeholder so a
  // recognised model resolves to SOMETHING rather than null. Confirm against
  // the provider price list before relying on these figures.
  ['gpt-5-mini', { inputPerMillion: 0.15, outputPerMillion: 0.6 }], // ~gpt-4o-mini tier (approx, TBC)
  ['gpt-5-nano', { inputPerMillion: 0.15, outputPerMillion: 0.6 }], // ~gpt-4o-mini tier (approx, TBC)
  ['gpt-5', { inputPerMillion: 2.5, outputPerMillion: 10.0 }], // ~gpt-4o flagship tier (approx, TBC)
];

const GEMINI_PRICING: Array<[string, PricingEntry]> = [
  // gemini-2.5 families: list prices captured 2026-06 (ai.google.dev/pricing).
  // More-specific 2.5-flash-lite before 2.5-flash before 2.5-pro in the walk.
  ['gemini-2.5-flash-lite', { inputPerMillion: 0.1, outputPerMillion: 0.4 }],
  ['gemini-2.5-flash', { inputPerMillion: 0.3, outputPerMillion: 2.5 }],
  ['gemini-2.5-pro', { inputPerMillion: 1.25, outputPerMillion: 10.0 }],
  ['gemini-2.0-flash-lite', { inputPerMillion: 0.075, outputPerMillion: 0.3 }],
  ['gemini-2.0-flash', { inputPerMillion: 0.1, outputPerMillion: 0.4 }],
];

// A native Gemini call to a Vertex AI host is billed at Vertex AI's list prices
// (cloud.google.com/vertex-ai/generative-ai/pricing, captured 2026-10), which differ
// from the Gemini API's only for gemini-2.0-flash. Mirrors VERTEX_GEMINI_PRICING in LlmPricing.java.
const VERTEX_GEMINI_PRICING: Array<[string, PricingEntry]> = [
  ['gemini-2.5-flash-lite', { inputPerMillion: 0.1, outputPerMillion: 0.4 }],
  ['gemini-2.5-flash', { inputPerMillion: 0.3, outputPerMillion: 2.5 }],
  ['gemini-2.5-pro', { inputPerMillion: 1.25, outputPerMillion: 10.0 }],
  ['gemini-2.0-flash-lite', { inputPerMillion: 0.075, outputPerMillion: 0.3 }],
  ['gemini-2.0-flash', { inputPerMillion: 0.15, outputPerMillion: 0.6 }],
];

// Llama on Vertex AI's OpenAI-compatible endpoint. The -maas suffix is part of the key, so a
// self-deployed meta/llama-3.3-70b-instruct is not billed at the managed rate. Mirrors VERTEX_LLAMA_PRICING.
const VERTEX_LLAMA_PRICING: Array<[string, PricingEntry]> = [
  ['llama-3.3-70b-instruct-maas', { inputPerMillion: 0.72, outputPerMillion: 0.72 }],
];

// The Vertex AI table for each publisher MockServer prices on the OpenAI-compatible endpoint.
const VERTEX_PUBLISHER_PRICING = new Map<string, Array<[string, PricingEntry]>>([
  ['google', VERTEX_GEMINI_PRICING],
  ['meta', VERTEX_LLAMA_PRICING],
]);

// Ollama is always free (local models)
const OLLAMA_PRICING: PricingEntry = { inputPerMillion: 0, outputPerMillion: 0 };

// ---------------------------------------------------------------------------
// Lookup helpers
// ---------------------------------------------------------------------------

function findPricing(table: Array<[string, PricingEntry]>, model: string): PricingEntry | null {
  const lower = model.toLowerCase();
  for (const [prefix, entry] of table) {
    if (lower.startsWith(prefix.toLowerCase())) {
      return entry;
    }
  }
  return null;
}

/**
 * Look up pricing for a given provider and model.
 * Returns null if the model is not recognised.
 */
function getPricing(provider: string, model: string, vertexAi: boolean): PricingEntry | null {
  const kind = provider.toLowerCase();

  if (kind === 'anthropic' || kind === 'bedrock') {
    // parseTraffic classifies Bedrock Claude traffic as kind 'anthropic', and its model
    // ids are prefixed (e.g. "anthropic.claude-sonnet-4-…" or, with inference profiles,
    // "us.anthropic.claude-…"). Strip up to and including "anthropic." so the claude-*
    // pricing keys match; a bare "claude-…" id is unaffected.
    const stripped = model.replace(/^.*anthropic\./, '');
    return findPricing(ANTHROPIC_PRICING, stripped);
  }
  const slash = model.indexOf('/');
  if ((kind === 'openai' || kind === 'openai_responses') && slash > 0 && slash < model.length - 1) {
    // No OpenAI model id has a slash: Vertex AI's OpenAI-compatible endpoint names a model
    // <publisher>/<model> and bills it at Vertex AI's rates, as LlmPricing.vertexPublisherPricing does.
    const table = VERTEX_PUBLISHER_PRICING.get(model.slice(0, slash).toLowerCase());
    return table ? findPricing(table, model.slice(slash + 1)) : null;
  }
  if (kind === 'openai' || kind === 'azure_openai') {
    // Azure OpenAI traffic uses *deployment names* (user-defined, e.g.
    // "my-gpt4o-prod") rather than canonical OpenAI model ids, so this
    // lookup will return null for most real Azure deployments. The UI
    // surfaces this as "—" — document that limitation if you wire an
    // Azure-detection path into `parseTraffic`.
    return findPricing(OPENAI_PRICING, model);
  }
  if (kind === 'openai_responses') {
    return findPricing(OPENAI_PRICING, model);
  }
  if (kind === 'gemini') {
    return findPricing(vertexAi ? VERTEX_GEMINI_PRICING : GEMINI_PRICING, model);
  }
  if (kind === 'ollama') {
    return OLLAMA_PRICING;
  }

  return null;
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Estimate the cost in USD for a given provider, model, and token counts.
 *
 * @param vertexAi whether the captured request went to a Vertex AI host, which
 *        bills a native Gemini call at Vertex AI's rates (as the server does)
 * @returns The estimated cost in USD, or `null` if the model is unknown.
 *          Returns `0` for zero tokens even on a known model.
 */
export function estimateCostUsd(
  provider: string,
  model: string,
  inputTokens: number,
  outputTokens: number,
  vertexAi = false,
): number | null {
  const pricing = getPricing(provider, model, vertexAi);
  if (pricing === null) return null;

  if (inputTokens === 0 && outputTokens === 0) return 0;

  return (
    (inputTokens / 1_000_000) * pricing.inputPerMillion +
    (outputTokens / 1_000_000) * pricing.outputPerMillion
  );
}

// ---------------------------------------------------------------------------
// Drift-guard support
// ---------------------------------------------------------------------------

/**
 * The ordered model-id prefixes present in each provider's pricing table.
 * Exported solely so the drift-guard test (`llmPricing.test.ts`) can assert
 * this UI table stays a superset of the canonical model list mirrored from
 * `LlmPricing.java`. Not part of the runtime UI surface.
 */
export const PRICING_MODEL_PREFIXES: Record<string, string[]> = {
  anthropic: ANTHROPIC_PRICING.map(([prefix]) => prefix),
  openai: OPENAI_PRICING.map(([prefix]) => prefix),
  gemini: GEMINI_PRICING.map(([prefix]) => prefix),
  vertex_gemini: VERTEX_GEMINI_PRICING.map(([prefix]) => prefix),
  vertex_llama: VERTEX_LLAMA_PRICING.map(([prefix]) => prefix),
};
