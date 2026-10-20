/**
 * Pure parsing helpers for classifying and extracting LLM / MCP traffic
 * from MockServer proxied request/response pairs.
 *
 * All functions are defensive — they never throw on malformed input.
 */

import { decodeAwsEventStream, type AwsEventStreamMessage } from './awsEventStream';

// ---------------------------------------------------------------------------
// SSE types
// ---------------------------------------------------------------------------

export interface SseEvent {
  event?: string;
  data: string;
}

// ---------------------------------------------------------------------------
// Anthropic types
// ---------------------------------------------------------------------------

export interface AnthropicContentBlock {
  type: string;
  text?: string;
  id?: string;
  name?: string;
  input?: unknown;
}

export interface AnthropicUsage {
  input_tokens?: number;
  output_tokens?: number;
  // Prompt-caching counters (agentic CLIs lean heavily on prompt caching).
  // Without these the input side is materially undercounted.
  cache_creation_input_tokens?: number;
  cache_read_input_tokens?: number;
}

export interface AnthropicParsed {
  kind: 'anthropic';
  model: string | null;
  stream: boolean;
  messages: unknown[];
  system: unknown | null;
  tools: unknown[] | null;
  maxTokens: number | null;
  responseContent: AnthropicContentBlock[];
  usage: AnthropicUsage | null;
  stopReason: string | null;
  sseEvents: SseEvent[] | null;
  streamed: boolean;
  streamTruncated: boolean;
  /**
   * Set for AWS Bedrock Runtime traffic. Both Bedrock APIs are normalised into this
   * Anthropic shape: InvokeModel on Claude already uses it, and Converse blocks,
   * usage and stream events are mapped onto it.
   */
  platform?: 'bedrock';
}

// ---------------------------------------------------------------------------
// OpenAI types
// ---------------------------------------------------------------------------

export interface OpenAiUsage {
  prompt_tokens?: number;
  completion_tokens?: number;
  total_tokens?: number;
}

/**
 * The OpenAI Responses API reports token usage with input_tokens / output_tokens
 * (like Anthropic), NOT the Chat Completions prompt_tokens / completion_tokens.
 */
export interface OpenAiResponsesUsage {
  input_tokens?: number;
  output_tokens?: number;
  total_tokens?: number;
}

export interface OpenAiChoice {
  message?: {
    role?: string;
    content?: string | null;
    tool_calls?: unknown[];
  };
  finish_reason?: string | null;
}

export interface OpenAiParsed {
  kind: 'openai';
  model: string | null;
  stream: boolean;
  messages: unknown[];
  tools: unknown[] | null;
  choices: OpenAiChoice[];
  usage: OpenAiUsage | null;
  sseEvents: SseEvent[] | null;
  streamed: boolean;
  streamTruncated: boolean;
}

// ---------------------------------------------------------------------------
// MCP JSON-RPC types
// ---------------------------------------------------------------------------

export interface McpParsed {
  kind: 'mcp';
  method: string | null;
  id: unknown;
  params: unknown | null;
  result: unknown | null;
  error: unknown | null;
  isResponse: boolean;
}

// ---------------------------------------------------------------------------
// OpenAI Responses API types
// ---------------------------------------------------------------------------

export interface OpenAiResponsesParsed {
  kind: 'openai_responses';
  model: string | null;
  stream: boolean;
  input: unknown[];
  tools: unknown[] | null;
  output: unknown[];
  usage: OpenAiResponsesUsage | null;
  sseEvents: SseEvent[] | null;
  streamed: boolean;
  streamTruncated: boolean;
}

// ---------------------------------------------------------------------------
// Gemini types
// ---------------------------------------------------------------------------

export interface GeminiParsed {
  kind: 'gemini';
  model: string | null;
  stream: boolean;
  contents: unknown[];
  tools: unknown[] | null;
  candidates: unknown[];
  usage: GeminiUsage | null;
  sseEvents: SseEvent[] | null;
  streamed: boolean;
  streamTruncated: boolean;
  /** The captured request went to a Vertex AI host, which bills at Vertex AI's rates. */
  vertexAi?: boolean;
}

/** Gemini usageMetadata. Thinking models report thoughtsTokenCount apart from candidatesTokenCount. */
export interface GeminiUsage {
  promptTokenCount?: number;
  candidatesTokenCount?: number;
  thoughtsTokenCount?: number;
}

/**
 * Output tokens of a Gemini reply: candidates plus thoughts, as Gemini bills thinking tokens
 * at the output price. Undefined when neither count is present.
 */
export function geminiOutputTokens(usage: GeminiUsage | null | undefined): number | undefined {
  if (!usage || (usage.candidatesTokenCount == null && usage.thoughtsTokenCount == null)) return undefined;
  return (usage.candidatesTokenCount ?? 0) + (usage.thoughtsTokenCount ?? 0);
}

function geminiUsageFrom(usageMeta: Record<string, unknown>): GeminiUsage {
  return {
    promptTokenCount: getNumber(usageMeta, 'promptTokenCount') ?? undefined,
    candidatesTokenCount: getNumber(usageMeta, 'candidatesTokenCount') ?? undefined,
    thoughtsTokenCount: getNumber(usageMeta, 'thoughtsTokenCount') ?? undefined,
  };
}

// ---------------------------------------------------------------------------
// Ollama types
// ---------------------------------------------------------------------------

export interface OllamaParsed {
  kind: 'ollama';
  model: string | null;
  stream: boolean;
  messages: unknown[];
  tools: unknown[] | null;
  responseMessage: unknown | null;
  done: boolean;
  usage: { prompt_eval_count?: number; eval_count?: number } | null;
  sseEvents: SseEvent[] | null;
  streamed: boolean;
  streamTruncated: boolean;
  /**
   * Set for /api/generate traffic. Its system and prompt fields are shown as messages and its
   * response, thinking and tool_calls fields as the assistant reply; absent for /api/chat.
   */
  endpoint?: 'generate';
}

// ---------------------------------------------------------------------------
// Generic fallback
// ---------------------------------------------------------------------------

export interface GenericParsed {
  kind: 'generic';
  method: string | null;
  path: string | null;
  statusCode: number | null;
}

// ---------------------------------------------------------------------------
// ConversationPredicates — matches Java ConversationPredicates model
// ---------------------------------------------------------------------------

export interface PromptNormalization {
  collapseWhitespace?: boolean;
  lowercase?: boolean;
  sortJsonKeys?: boolean;
  dropBuiltInVolatileFields?: boolean;
  dropVolatileFields?: string[];
}

export interface ConversationPredicates {
  turnIndex?: number;
  latestMessageContains?: string;
  latestMessageMatches?: string;
  latestMessageRole?: 'USER' | 'ASSISTANT' | 'TOOL' | 'SYSTEM';
  containsToolResultFor?: string;
  semanticMatchAgainst?: string;
  normalization?: PromptNormalization;
}

// ---------------------------------------------------------------------------
// Discriminated union
// ---------------------------------------------------------------------------

export type ParsedTraffic =
  | AnthropicParsed
  | OpenAiParsed
  | OpenAiResponsesParsed
  | GeminiParsed
  | OllamaParsed
  | McpParsed
  | GenericParsed;

// ---------------------------------------------------------------------------
// Per-request timing (populated by the server for forwarded/proxied traffic)
// ---------------------------------------------------------------------------

export interface RequestTiming {
  connectionTimeInMillis: number | null;
  timeToFirstByteInMillis: number | null;
  totalTimeInMillis: number | null;
  requestStartedMillis: number | null;
  connectionEstablishedMillis: number | null;
  responseReceivedMillis: number | null;
  // Injected-vs-real latency waterfall (additive; may be absent on older servers): the portion of
  // totalTimeInMillis that MockServer deliberately injected, split by source. "Real" time (connect,
  // processing, upstream) is derived as total minus the sum of these. Optional so older payloads and
  // callers that predate these fields remain valid.
  injectedChaosLatencyMillis?: number | null;
  injectedDelayMillis?: number | null;
  breakpointHeldMillis?: number | null;
}

// ---------------------------------------------------------------------------
// Summary for master list display
// ---------------------------------------------------------------------------

export interface TrafficSummary {
  host: string | null;
  method: string | null;
  path: string | null;
  statusCode: number | null;
  parsed: ParsedTraffic;
  timing: RequestTiming | null;
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

function safeParseJson(input: unknown): unknown {
  if (typeof input === 'string') {
    try {
      return JSON.parse(input);
    } catch {
      return undefined;
    }
  }
  if (typeof input === 'object' && input !== null) {
    return input;
  }
  return undefined;
}

function getString(obj: unknown, key: string): string | null {
  if (typeof obj !== 'object' || obj === null) return null;
  const val = (obj as Record<string, unknown>)[key];
  return typeof val === 'string' ? val : null;
}

function getNumber(obj: unknown, key: string): number | null {
  if (typeof obj !== 'object' || obj === null) return null;
  const val = (obj as Record<string, unknown>)[key];
  return typeof val === 'number' ? val : null;
}

function getBoolean(obj: unknown, key: string): boolean | null {
  if (typeof obj !== 'object' || obj === null) return null;
  const val = (obj as Record<string, unknown>)[key];
  return typeof val === 'boolean' ? val : null;
}

function getArray(obj: unknown, key: string): unknown[] | null {
  if (typeof obj !== 'object' || obj === null) return null;
  const val = (obj as Record<string, unknown>)[key];
  return Array.isArray(val) ? val : null;
}

function getObject(obj: unknown, key: string): Record<string, unknown> | null {
  if (typeof obj !== 'object' || obj === null) return null;
  const val = (obj as Record<string, unknown>)[key];
  if (typeof val === 'object' && val !== null && !Array.isArray(val)) {
    return val as Record<string, unknown>;
  }
  return null;
}

/**
 * Extract a header value from MockServer's header format.
 * Headers can be either an object `{ name: [values] }` or an array
 * `[{ name: "x", values: ["y"] }]`.
 */
function getHeaderValue(headers: unknown, headerName: string): string | null {
  if (!headers) return null;
  const lowerName = headerName.toLowerCase();

  if (Array.isArray(headers)) {
    for (const h of headers) {
      if (typeof h === 'object' && h !== null) {
        const entry = h as Record<string, unknown>;
        const name = entry['name'];
        if (typeof name === 'string' && name.toLowerCase() === lowerName) {
          const values = entry['values'];
          if (Array.isArray(values) && values.length > 0) {
            return String(values[0]);
          }
        }
      }
    }
    return null;
  }

  if (typeof headers === 'object' && headers !== null) {
    const map = headers as Record<string, unknown>;
    for (const key of Object.keys(map)) {
      if (key.toLowerCase() === lowerName) {
        const val = map[key];
        if (Array.isArray(val) && val.length > 0) return String(val[0]);
        if (typeof val === 'string') return val;
      }
    }
  }

  return null;
}

// ---------------------------------------------------------------------------
// SSE parsing
// ---------------------------------------------------------------------------

export function parseSseStream(text: string): SseEvent[] {
  const events: SseEvent[] = [];
  // SSE on the wire is CRLF-terminated; normalise CRLF/CR to LF first so the
  // `[DONE]` sentinel comparison and reassembled text don't carry stray `\r`.
  const lines = text.replace(/\r\n?/g, '\n').split('\n');
  let currentEvent: string | undefined;
  let currentData: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      currentEvent = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      currentData.push(line.slice(5).trimStart());
    } else if (line.trim() === '' && currentData.length > 0) {
      events.push({
        event: currentEvent,
        data: currentData.join('\n'),
      });
      currentEvent = undefined;
      currentData = [];
    }
  }

  // Handle trailing data without a final blank line
  if (currentData.length > 0) {
    events.push({
      event: currentEvent,
      data: currentData.join('\n'),
    });
  }

  return events;
}

// ---------------------------------------------------------------------------
// Anthropic SSE reassembly
// ---------------------------------------------------------------------------

interface ReassembledAnthropic {
  content: AnthropicContentBlock[];
  usage: AnthropicUsage;
  model: string | null;
  stopReason: string | null;
}

// Upper bound on the number of distinct SSE content-block indices we will
// materialise. A hostile or buggy stream can carry an arbitrarily large
// `index` (e.g. 100000000); without a cap the `while (length <= index)` growth
// loop allocates a gigantic array and freezes the tab. Real responses have a
// handful of blocks, so a few thousand is far above any legitimate ceiling.
const MAX_ANTHROPIC_CONTENT_BLOCKS = 4096;

function reassembleAnthropicSse(events: SseEvent[]): ReassembledAnthropic {
  const result: ReassembledAnthropic = {
    content: [],
    usage: {},
    model: null,
    stopReason: null,
  };

  const textParts: Map<number, string[]> = new Map();
  const toolInputParts: Map<number, string[]> = new Map();

  for (const evt of events) {
    const parsed = safeParseJson(evt.data);
    if (!parsed || typeof parsed !== 'object') continue;
    const data = parsed as Record<string, unknown>;

    if (evt.event === 'message_start') {
      const message = getObject(data, 'message');
      if (message) {
        result.model = getString(message, 'model');
        const usage = getObject(message, 'usage');
        if (usage) {
          const inputTokens = getNumber(usage, 'input_tokens');
          if (inputTokens !== null) result.usage.input_tokens = inputTokens;
          const cacheCreation = getNumber(usage, 'cache_creation_input_tokens');
          if (cacheCreation !== null) result.usage.cache_creation_input_tokens = cacheCreation;
          const cacheRead = getNumber(usage, 'cache_read_input_tokens');
          if (cacheRead !== null) result.usage.cache_read_input_tokens = cacheRead;
        }
      }
    } else if (evt.event === 'content_block_start') {
      const block = getObject(data, 'content_block');
      const index = getNumber(data, 'index');
      // Ignore out-of-range / hostile indices so the growth loop below stays bounded.
      if (block && index !== null && index >= 0 && index <= MAX_ANTHROPIC_CONTENT_BLOCKS) {
        const type = getString(block, 'type') ?? 'text';
        const contentBlock: AnthropicContentBlock = { type };
        if (type === 'tool_use') {
          contentBlock.id = getString(block, 'id') ?? undefined;
          contentBlock.name = getString(block, 'name') ?? undefined;
        }
        // Ensure the content array is large enough
        while (result.content.length <= index) {
          result.content.push({ type: 'text' });
        }
        result.content[index] = contentBlock;
      }
    } else if (evt.event === 'content_block_delta') {
      const delta = getObject(data, 'delta');
      const index = getNumber(data, 'index');
      if (delta && index !== null) {
        const deltaType = getString(delta, 'type');
        if (deltaType === 'text_delta') {
          const text = getString(delta, 'text');
          if (text !== null) {
            if (!textParts.has(index)) textParts.set(index, []);
            textParts.get(index)!.push(text);
          }
        } else if (deltaType === 'input_json_delta') {
          const partial = getString(delta, 'partial_json');
          if (partial !== null) {
            if (!toolInputParts.has(index)) toolInputParts.set(index, []);
            toolInputParts.get(index)!.push(partial);
          }
        }
      }
    } else if (evt.event === 'message_delta') {
      const delta = getObject(data, 'delta');
      if (delta) {
        result.stopReason = getString(delta, 'stop_reason');
      }
      const usage = getObject(data, 'usage');
      if (usage) {
        const outputTokens = getNumber(usage, 'output_tokens');
        if (outputTokens !== null) result.usage.output_tokens = outputTokens;
      }
    }
  }

  // Apply accumulated text and tool input
  for (const [index, parts] of textParts) {
    if (result.content[index]) {
      result.content[index] = { ...result.content[index]!, text: parts.join('') };
    }
  }
  for (const [index, parts] of toolInputParts) {
    if (result.content[index]) {
      const joinedJson = parts.join('');
      const parsedInput = safeParseJson(joinedJson);
      result.content[index] = { ...result.content[index]!, input: parsedInput ?? joinedJson };
    }
  }

  return result;
}

// ---------------------------------------------------------------------------
// Detection helpers
// ---------------------------------------------------------------------------

function isAnthropicPath(path: string | null): boolean {
  return path !== null && path.includes('/v1/messages');
}

function isOpenAiPath(path: string | null): boolean {
  return path !== null && path.includes('/chat/completions');
}

// Mirrors the server's OpenAI Responses path pattern: /v1/responses (also Azure's
// /openai/v1/responses), Azure's dated-version /openai/responses, and the OpenAI Codex
// backend used by coding CLIs (chatgpt.com/backend-api/codex/responses). The codex/ and
// openai/ arms end at a path-segment boundary so /mycodex/responses cannot match.
const OPENAI_RESPONSES_PATH = /\/v1\/responses|(^|\/)(codex|openai)\/responses(?:[/?]|$)/i;

function isOpenAiResponsesPath(path: string | null): boolean {
  return path !== null && OPENAI_RESPONSES_PATH.test(path);
}

/**
 * An Azure OpenAI endpoint, as the server's LlmProviderSniffer.isAzureOpenAiHost: any
 * *.openai.azure.com host, or the *.services.ai.azure.com Foundry host on an /openai/ path
 * (that host also serves non-OpenAI model APIs).
 */
function isAzureOpenAiHost(host: string, path: string | null): boolean {
  return host.endsWith('.openai.azure.com')
    || (host.endsWith('.services.ai.azure.com') && path !== null && path.toLowerCase().startsWith('/openai/'));
}

// Global, regional (<region>-aiplatform) or multi-region (aiplatform.<us|eu>.rep) host, matched
// whole so an arbitrary host cannot be glued on (no label has a dot).
const VERTEX_AI_HOST = /^(?:(?:[a-z0-9-]+-)?aiplatform\.googleapis\.com|aiplatform\.[a-z0-9-]+\.rep\.googleapis\.com)$/;

// Vertex AI's OpenAI-compatible APIs: endpoints/openapi for Model Garden and partner models,
// endpoints/{id} for a self-deployed model. Matched against the lower-cased path.
const VERTEX_OPENAI_COMPATIBLE_PATH = /\/endpoints\/[^/]+\/(chat\/completions|responses)(?:[/?]|$)/;

/**
 * Whether a captured request went to a Vertex AI host, as the server's
 * LlmProviderSniffer.isVertexAiHost decides it: the socket address's host, else the Host header.
 */
function isVertexAiHost(httpRequest: Record<string, unknown> | null): boolean {
  if (!httpRequest) return false;
  const host = getString(getObject(httpRequest, 'socketAddress'), 'host') || getHeaderValue(httpRequest['headers'], 'host');
  return host !== null && VERTEX_AI_HOST.test(host.toLowerCase().replace(/:\d+$/, ''));
}

function isGeminiPath(path: string | null): boolean {
  // Gemini uses /v1beta/ (literal beta) or /v1/ followed by a model name that
  // starts with "gemini-". The earlier `/v1(beta)?/` form misclassified generic
  // /v1/models/* paths from other providers as Gemini.
  if (path === null) return false;
  return /\/v1beta\/models\/[^/]+:(generateContent|streamGenerateContent)/.test(path)
    || /\/v1\/models\/gemini-[^/]+:(generateContent|streamGenerateContent)/.test(path)
    || /\/publishers\/google\/models\/[^/]+:(generateContent|streamGenerateContent)/.test(path);
}

// Claude on Vertex AI: .../publishers/anthropic/models/{model}:rawPredict or
// :streamRawPredict, carrying the Anthropic Messages wire shape.
const VERTEX_ANTHROPIC_PATH = /\/publishers\/anthropic\/models\/([^/:?]+):(?:rawPredict|streamRawPredict)/;

// A Vertex AI partner model's path: .../publishers/{publisher}/models/{model}:{method}
const VERTEX_PUBLISHER_MODEL_PATH = /\/publishers\/[^/]+\/models\/([^/:?]+):/;

// Bedrock Runtime: /model/{modelId}/{operation} for every model family. The model
// id may be an inference-profile id (us.anthropic...), or a URL-encoded ARN.
const BEDROCK_RUNTIME_PATH = /\/model\/(.+?)\/(invoke-with-response-stream|invoke|converse-stream|converse)(?=[/?]|$)/i;

function bedrockPathParts(path: string | null): { modelId: string; operation: string } | null {
  const m = path === null ? null : path.match(BEDROCK_RUNTIME_PATH);
  if (!m) return null;
  let modelId = m[1]!;
  try {
    modelId = decodeURIComponent(modelId);
  } catch {
    // keep the raw segment when it is not valid percent-encoding
  }
  return { modelId, operation: m[2]!.toLowerCase() };
}

function isAzureOpenAiPath(path: string | null): boolean {
  return path !== null && /\/openai\/deployments\//.test(path) && path.includes('/chat/completions');
}

function isOllamaPath(path: string | null): boolean {
  // Anchor to `/api/chat` as a complete path segment so we don't misclassify
  // `/api/chatbot`, `/api/chats`, or any generic chat endpoint.
  if (path === null) return false;
  return /(^|\/)api\/chat(?:\/?$|\?)/.test(path);
}

function isOllamaGeneratePath(path: string | null): boolean {
  return path !== null && /(^|\/)api\/generate(?:\/?$|\?)/.test(path);
}

/**
 * Host-based provider detection, mirroring the server's LlmProviderSniffer.
 * A request to a well-known provider host is classified as that provider
 * regardless of the path, so the dashboard agrees with the server even for
 * non-chat paths (e.g. /v1/models) to a known host. Returns the parser kind to
 * use, or null when the host is unknown (callers then fall back to path / body
 * shape). Maps to the parsers the dashboard implements: Azure -> openai (same
 * wire format) or openai_responses on a Responses path, Vertex -> gemini, or the
 * parser its OpenAI-compatible endpoint or partner-model path speaks, Bedrock ->
 * the Bedrock parser, which yields the anthropic shape.
 *
 * Note: embeddings-only hosts (api.cohere.com, api.voyageai.com) are recognised
 * by the server but have no dedicated dashboard parser, so they are not mapped
 * here and fall through to generic.
 */
function detectByHost(
  host: string | null,
  path: string | null,
): 'anthropic' | 'openai' | 'openai_responses' | 'gemini' | 'bedrock' | null {
  if (!host) return null;
  // Strip any port suffix before matching (Host headers may include :443).
  const h = host.toLowerCase().replace(/:\d+$/, '');

  if (h === 'api.openai.com') {
    // Distinguish the Responses API (/responses) from Chat Completions.
    return path !== null && /\/responses/.test(path.toLowerCase()) ? 'openai_responses' : 'openai';
  }
  if (isAzureOpenAiHost(h, path)) return isOpenAiResponsesPath(path) ? 'openai_responses' : 'openai';
  if (h === 'api.anthropic.com') return 'anthropic';
  // OpenAI Codex backend used by coding CLIs (opencode): chatgpt.com serves the
  // Responses API. Path-gated so non-LLM chatgpt.com traffic stays generic.
  if (h === 'chatgpt.com' || h.endsWith('.chatgpt.com')) {
    return isOpenAiResponsesPath(path) ? 'openai_responses' : null;
  }
  if (h === 'generativelanguage.googleapis.com') return 'gemini';
  // Vertex AI: the parser is the wire API the path speaks, Gemini's unless the
  // path says otherwise, as LlmProviderSniffer.sniffByHostAndPath decides it.
  if (VERTEX_AI_HOST.test(h)) {
    const p = (path ?? '').toLowerCase();
    const openAiCompatible = p.match(VERTEX_OPENAI_COMPATIBLE_PATH);
    if (openAiCompatible) return openAiCompatible[1] === 'responses' ? 'openai_responses' : 'openai';
    if (p.includes('/publishers/anthropic/')) return 'anthropic';
    // Mistral and AI21 Jamba chat replies carry OpenAI's choices and usage shape
    if (p.includes('/publishers/mistralai/') || p.includes('/publishers/ai21/')) return 'openai';
    return 'gemini';
  }
  // Bedrock: bedrock*.amazonaws.com (InvokeModel and Converse).
  if (h.endsWith('.amazonaws.com') && (h.startsWith('bedrock') || h.includes('.bedrock'))) return 'bedrock';

  return null;
}

/**
 * Resilient fallback: infer the provider from the request/response BODY SHAPE
 * alone — no host or path required. The wire format is the provider's API
 * contract and changes far more slowly than the host/path a given CLI uses, so
 * recognising LLM traffic by its body keeps the Traffic / LLM Traces / LLM
 * Optimise views working when a tool moves to a new endpoint or a new tool
 * appears. Mirrors `LlmProviderSniffer.sniffByBodyShape` on the server. Stays
 * conservative (returns null rather than guess) so non-LLM traffic is not
 * mis-classified.
 */
function toBodyString(content: unknown): string | null {
  if (content === null || content === undefined) return null;
  if (typeof content === 'string') return content.length > 0 ? content : null;
  try {
    return JSON.stringify(content);
  } catch {
    return null;
  }
}

function detectByBodyShape(
  reqContent: unknown,
  resContent: unknown,
  requestHeaders: unknown,
): 'anthropic' | 'openai' | 'openai_responses' | 'gemini' | null {
  // Response markers are the most distinctive — check them first. Each pattern
  // is anchored to a JSON key/value or SSE-field boundary so a hostile message
  // body that merely contains the substring (e.g. a user pasting "message_start"
  // into a prompt) cannot force a misclassification. Real provider responses
  // always carry these tokens as `"type":"…"` / `"object":"…"` values or as
  // `event:` field names.
  const res = toBodyString(resContent);
  if (res) {
    // OpenAI Chat Completions: object "chat.completion" / "chat.completion.chunk".
    if (/"object"\s*:\s*"chat\.completion/.test(res)) return 'openai';
    // OpenAI Responses: object "response" or any `"type":"response.*"` SSE event.
    if (
      /"type"\s*:\s*"response\./.test(res) ||
      /"object"\s*:\s*"response"/.test(res)
    ) {
      return 'openai_responses';
    }
    // Anthropic: streamed `content_block_*` / `message_start` / `message_delta`
    // event types (as `"type":"…"` values or `event:` field names), or a
    // non-streamed message envelope (`"type":"message"` with a stop_reason).
    if (
      /"type"\s*:\s*"(content_block|message_start|message_delta)/.test(res) ||
      /(^|\n)event:\s*(content_block|message_start|message_delta)/.test(res) ||
      (/"type"\s*:\s*"message"/.test(res) && res.includes('stop_reason'))
    ) {
      return 'anthropic';
    }
    // Gemini: a `candidates` array alongside `usageMetadata` (both as keys).
    if (/"candidates"\s*:/.test(res) && /"usageMetadata"\s*:/.test(res)) return 'gemini';
  }
  // Anthropic sends the anthropic-version header on every request.
  if (getHeaderValue(requestHeaders, 'anthropic-version')) return 'anthropic';
  // Request markers — used when the response is absent or unrecognised.
  const req = toBodyString(reqContent);
  if (req) {
    const hasModel = req.includes('"model"');
    // OpenAI Responses hallmark: top-level "input" array (Chat Completions and
    // Anthropic both use "messages").
    if (hasModel && req.includes('"input"') && !req.includes('"messages"')) return 'openai_responses';
    if (req.includes('"contents"') && (req.includes('"parts"') || req.includes('generationConfig'))) return 'gemini';
    if (hasModel && req.includes('"messages"')) return 'openai';
  }
  return null;
}

function isMcpJsonRpc(body: unknown): boolean {
  if (typeof body !== 'object' || body === null) return false;
  const obj = body as Record<string, unknown>;
  return obj['jsonrpc'] === '2.0' && (typeof obj['method'] === 'string' || 'result' in obj || 'error' in obj);
}

/**
 * Find the first JSON-RPC 2.0 message in a body, looking through three shapes
 * that modern MCP transports use:
 *   1. a single JSON-RPC object,
 *   2. a JSON-RPC batch (top-level array of objects), and
 *   3. JSON-RPC carried over SSE (`event: message\ndata: {jsonrpc...}`), as the
 *      Streamable HTTP transport returns.
 * Returns the first matching message, or null when the body is not MCP traffic.
 */
function firstJsonRpcMessage(bodyContent: unknown): Record<string, unknown> | null {
  const parsed = safeParseJson(bodyContent);
  if (Array.isArray(parsed)) {
    const found = parsed.find(isMcpJsonRpc);
    if (found) return found as Record<string, unknown>;
  } else if (parsed && isMcpJsonRpc(parsed)) {
    return parsed as Record<string, unknown>;
  }

  // SSE-framed JSON-RPC (Streamable HTTP transport).
  if (typeof bodyContent === 'string' && /(^|\n)data:/.test(bodyContent.replace(/\r\n?/g, '\n'))) {
    for (const evt of parseSseStream(bodyContent)) {
      const data = safeParseJson(evt.data);
      if (Array.isArray(data)) {
        const found = data.find(isMcpJsonRpc);
        if (found) return found as Record<string, unknown>;
      } else if (data && isMcpJsonRpc(data)) {
        return data as Record<string, unknown>;
      }
    }
  }

  return null;
}

function isStreamResponse(responseHeaders: unknown, responseBody: unknown): boolean {
  const contentType = getHeaderValue(responseHeaders, 'content-type');
  if (contentType && contentType.includes('text/event-stream')) return true;

  // Check if the body looks like SSE. OpenAI Chat Completions and Gemini emit
  // `data:`-only frames (no `event:` line), so requiring BOTH markers dropped
  // those streams. A line that STARTS with `data:` or `event:` (after CR/LF
  // normalisation) is the SSE field grammar — a JSON value like `{"data":...}`
  // never begins a line with `data:`, so this stays conservative.
  if (typeof responseBody === 'string') {
    const head = responseBody.slice(0, 500).replace(/\r\n?/g, '\n');
    return /(^|\n)data:/.test(head) || /(^|\n)event:/.test(head);
  }

  return false;
}

function hasStreamingHeaders(responseHeaders: unknown): { streamed: boolean; truncated: boolean } {
  const streamed = getHeaderValue(responseHeaders, 'x-mockserver-streamed') === 'true';
  const truncated = getHeaderValue(responseHeaders, 'x-mockserver-stream-truncated') === 'true';
  return { streamed, truncated };
}

// ---------------------------------------------------------------------------
// Main parse function
// ---------------------------------------------------------------------------

function parseAnthropicRequest(
  requestBody: unknown,
  responseBody: unknown,
  responseHeaders: unknown,
  pathModel: string | null = null,
): AnthropicParsed {
  const req = safeParseJson(requestBody) as Record<string, unknown> | undefined;
  const { streamed, truncated } = hasStreamingHeaders(responseHeaders);
  // Trust the server's `x-mockserver-streamed` signal as well as body sniffing:
  // a no-content-type SSE body (common for OpenAI-chat / Gemini) otherwise gets
  // parsed as non-stream JSON, fails, and renders empty.
  const isStream = streamed || isStreamResponse(responseHeaders, responseBody);

  const result: AnthropicParsed = {
    kind: 'anthropic',
    // Vertex and Bedrock carry the model in the URL, not the body.
    model: (req ? getString(req, 'model') : null) ?? pathModel,
    stream: req ? (getBoolean(req, 'stream') ?? false) : false,
    messages: req ? (getArray(req, 'messages') ?? []) : [],
    system: req ? (req['system'] ?? null) : null,
    tools: req ? (getArray(req, 'tools') ?? null) : null,
    maxTokens: req ? (getNumber(req, 'max_tokens') ?? null) : null,
    responseContent: [],
    usage: null,
    stopReason: null,
    sseEvents: null,
    streamed,
    streamTruncated: truncated,
  };

  if (isStream && typeof responseBody === 'string') {
    const events = parseSseStream(responseBody);
    result.sseEvents = events;
    const reassembled = reassembleAnthropicSse(events);
    result.responseContent = reassembled.content;
    result.usage = reassembled.usage;
    result.stopReason = reassembled.stopReason;
    if (reassembled.model && !result.model) {
      result.model = reassembled.model;
    }
  } else {
    const res = safeParseJson(responseBody) as Record<string, unknown> | undefined;
    if (res) {
      result.responseContent = (getArray(res, 'content') ?? []) as AnthropicContentBlock[];
      result.usage = getObject(res, 'usage') as AnthropicUsage | null;
      result.stopReason = getString(res, 'stop_reason');
      if (!result.model) {
        result.model = getString(res, 'model');
      }
    }
  }

  return result;
}

function parseOpenAiRequest(
  requestBody: unknown,
  responseBody: unknown,
  responseHeaders: unknown,
  pathModel: string | null = null,
): OpenAiParsed {
  const req = safeParseJson(requestBody) as Record<string, unknown> | undefined;
  const { streamed, truncated } = hasStreamingHeaders(responseHeaders);
  // Trust the server's `x-mockserver-streamed` signal too — OpenAI Chat
  // Completions streams emit only `data:` frames with no content-type, which
  // would otherwise be parsed as non-stream JSON and render empty.
  const isStream = streamed || isStreamResponse(responseHeaders, responseBody);

  const result: OpenAiParsed = {
    kind: 'openai',
    model: req ? getString(req, 'model') : null,
    stream: req ? (getBoolean(req, 'stream') ?? false) : false,
    messages: req ? (getArray(req, 'messages') ?? []) : [],
    tools: req ? (getArray(req, 'tools') ?? null) : null,
    choices: [],
    usage: null,
    sseEvents: null,
    streamed,
    streamTruncated: truncated,
  };

  if (isStream && typeof responseBody === 'string') {
    const events = parseSseStream(responseBody);
    result.sseEvents = events;
    // Reassemble streamed OpenAI response
    const contentParts: string[] = [];
    const toolCalls: Map<number, { id?: string; type?: string; function?: { name: string; arguments: string } }> = new Map();
    let finishReason: string | null = null;
    let model: string | null = null;

    for (const evt of events) {
      if (evt.data === '[DONE]') continue;
      const parsed = safeParseJson(evt.data) as Record<string, unknown> | undefined;
      if (!parsed) continue;
      if (!model) model = getString(parsed, 'model');
      const choices = getArray(parsed, 'choices');
      if (choices) {
        for (const choice of choices) {
          const choiceObj = choice as Record<string, unknown>;
          const delta = getObject(choiceObj, 'delta');
          if (delta) {
            const content = getString(delta, 'content');
            if (content !== null) contentParts.push(content);
            const toolCallsArr = getArray(delta, 'tool_calls');
            if (toolCallsArr) {
              for (const tc of toolCallsArr) {
                const tcObj = tc as Record<string, unknown>;
                const index = getNumber(tcObj, 'index') ?? 0;
                const existing = toolCalls.get(index) ?? {};
                const id = getString(tcObj, 'id');
                if (id) existing.id = id;
                const type = getString(tcObj, 'type');
                if (type) existing.type = type;
                const fn = getObject(tcObj, 'function');
                if (fn) {
                  if (!existing.function) {
                    existing.function = { name: '', arguments: '' };
                  }
                  const name = getString(fn, 'name');
                  if (name) existing.function.name = name;
                  const args = getString(fn, 'arguments');
                  if (args !== null) existing.function.arguments += args;
                }
                toolCalls.set(index, existing);
              }
            }
          }
          const fr = getString(choiceObj, 'finish_reason');
          if (fr) finishReason = fr;
        }
      }
      const usage = getObject(parsed, 'usage');
      if (usage) {
        result.usage = usage as OpenAiUsage;
      }
    }

    if (model && !result.model) result.model = model;

    const reassembledToolCalls = toolCalls.size > 0
      ? Array.from(toolCalls.entries())
          .sort(([a], [b]) => a - b)
          .map(([, tc]) => tc)
      : undefined;

    result.choices = [{
      message: {
        role: 'assistant',
        content: contentParts.length > 0 ? contentParts.join('') : null,
        tool_calls: reassembledToolCalls,
      },
      finish_reason: finishReason,
    }];
  } else {
    const res = safeParseJson(responseBody) as Record<string, unknown> | undefined;
    if (res) {
      result.choices = (getArray(res, 'choices') ?? []) as OpenAiChoice[];
      result.usage = getObject(res, 'usage') as OpenAiUsage | null;
      if (!result.model) {
        result.model = getString(res, 'model');
      }
    }
  }
  // AI21 Jamba on Vertex AI names its model only in the path
  if (!result.model) result.model = pathModel;

  return result;
}

function parseMcpRequest(
  req: Record<string, unknown> | null,
  res: Record<string, unknown> | null,
): McpParsed {
  // Could be a request or a response in JSON-RPC
  const isResponse = req ? !('method' in req) : false;
  const primary = isResponse ? res : req;
  const secondary = isResponse ? req : res;

  return {
    kind: 'mcp',
    method: primary ? getString(primary, 'method') : null,
    id: primary ? primary['id'] ?? null : null,
    params: primary ? (primary['params'] ?? null) : null,
    result: secondary ? (secondary['result'] ?? null) : (primary ? (primary['result'] ?? null) : null),
    error: secondary ? (secondary['error'] ?? null) : (primary ? (primary['error'] ?? null) : null),
    isResponse,
  };
}

// ---------------------------------------------------------------------------
// OpenAI Responses API parser
// ---------------------------------------------------------------------------

function parseOpenAiResponsesRequest(
  requestBody: unknown,
  responseBody: unknown,
  responseHeaders: unknown,
): OpenAiResponsesParsed {
  const req = safeParseJson(requestBody) as Record<string, unknown> | undefined;
  const { streamed, truncated } = hasStreamingHeaders(responseHeaders);
  const isStream = streamed || isStreamResponse(responseHeaders, responseBody);

  const result: OpenAiResponsesParsed = {
    kind: 'openai_responses',
    model: req ? getString(req, 'model') : null,
    stream: req ? (getBoolean(req, 'stream') ?? false) : false,
    input: req ? (getArray(req, 'input') ?? []) : [],
    tools: req ? (getArray(req, 'tools') ?? null) : null,
    output: [],
    usage: null,
    sseEvents: null,
    streamed,
    streamTruncated: truncated,
  };

  if (isStream && typeof responseBody === 'string') {
    const events = parseSseStream(responseBody);
    result.sseEvents = events;
    const reassembled = reassembleResponsesSse(events);
    result.output = reassembled.output;
    result.usage = reassembled.usage;
    if (reassembled.model && !result.model) result.model = reassembled.model;
    return result;
  }

  const res = safeParseJson(responseBody) as Record<string, unknown> | undefined;
  if (res) {
    result.output = getArray(res, 'output') ?? [];
    result.usage = getObject(res, 'usage') as OpenAiResponsesUsage | null;
    if (!result.model) {
      result.model = getString(res, 'model');
    }
  }

  return result;
}

/**
 * Reassemble a streamed OpenAI Responses reply. Text comes from response.output_text.delta
 * events; function calls from response.output_item.done items; usage/model from the
 * response.completed (or earlier response.*) event.
 */
function reassembleResponsesSse(events: SseEvent[]): { output: unknown[]; usage: OpenAiResponsesUsage | null; model: string | null } {
  const functionCalls: unknown[] = [];
  let text = '';
  let usage: OpenAiResponsesUsage | null = null;
  let model: string | null = null;
  for (const ev of events) {
    const data = safeParseJson(ev.data) as Record<string, unknown> | undefined;
    if (!data) continue;
    const type = data['type'];
    if (type === 'response.output_text.delta' && typeof data['delta'] === 'string') {
      text += data['delta'];
    } else if (type === 'response.output_item.done') {
      const item = data['item'] as Record<string, unknown> | undefined;
      if (item && item['type'] === 'function_call') functionCalls.push(item);
    } else if (typeof type === 'string' && type.startsWith('response.')) {
      const resp = data['response'] as Record<string, unknown> | undefined;
      if (resp) {
        if (!model && typeof resp['model'] === 'string') model = resp['model'] as string;
        const u = resp['usage'] as Record<string, unknown> | undefined;
        if (u) {
          usage = {
            input_tokens: getNumber(u, 'input_tokens') ?? undefined,
            output_tokens: getNumber(u, 'output_tokens') ?? undefined,
            total_tokens: getNumber(u, 'total_tokens') ?? undefined,
          };
        }
      }
    }
  }
  const output: unknown[] = [];
  if (text) output.push({ type: 'message', content: [{ type: 'output_text', text }] });
  output.push(...functionCalls);
  return { output, usage, model };
}

// ---------------------------------------------------------------------------
// Gemini parser
// ---------------------------------------------------------------------------

/**
 * Reassemble a streamed Gemini response (SSE of partial `candidates` chunks) into a single
 * candidate with the concatenated parts plus the final usageMetadata / finishReason.
 */
function reassembleGeminiSse(events: SseEvent[]): { candidates: unknown[]; usage: GeminiParsed['usage']; model: string | null } {
  const parts: unknown[] = [];
  let finishReason: string | undefined;
  let usage: GeminiParsed['usage'] = null;
  let model: string | null = null;
  for (const ev of events) {
    const data = safeParseJson(ev.data) as Record<string, unknown> | undefined;
    if (!data) continue;
    const candidates = getArray(data, 'candidates');
    if (candidates && candidates.length > 0) {
      const c0 = candidates[0] as Record<string, unknown>;
      const content = c0['content'] as Record<string, unknown> | undefined;
      if (content && Array.isArray(content['parts'])) {
        for (const p of content['parts'] as unknown[]) parts.push(p);
      }
      if (typeof c0['finishReason'] === 'string') finishReason = c0['finishReason'];
    }
    const usageMeta = getObject(data, 'usageMetadata');
    if (usageMeta) usage = geminiUsageFrom(usageMeta);
    if (!model) model = getString(data, 'modelVersion') ?? getString(data, 'model');
  }
  const candidates = parts.length > 0 || finishReason
    ? [{ content: { parts, role: 'model' }, ...(finishReason ? { finishReason } : {}) }]
    : [];
  return { candidates, usage, model };
}

/**
 * Extract the Gemini model from the request path, e.g.
 * `/v1beta/models/gemini-2.5-pro:generateContent` -> `gemini-2.5-pro`.
 * The model is in the URL for Gemini, so it is available even when the response
 * body omits `modelVersion` (notably on streamed or error responses).
 */
function modelFromGeminiPath(path: string | null): string | null {
  if (!path) return null;
  const m = path.match(/\/models\/([^/:?]+):(?:streamGenerateContent|generateContent)/);
  return m ? m[1]! : null;
}

function parseGeminiRequest(
  requestBody: unknown,
  responseBody: unknown,
  responseHeaders: unknown,
  path: string | null,
  vertexAi: boolean,
): GeminiParsed {
  const req = safeParseJson(requestBody) as Record<string, unknown> | undefined;
  const { streamed, truncated } = hasStreamingHeaders(responseHeaders);
  const isStream = streamed || isStreamResponse(responseHeaders, responseBody);
  const pathModel = modelFromGeminiPath(path);

  const result: GeminiParsed = {
    kind: 'gemini',
    model: null,
    // Gemini selects streaming by method in the URL, not by a body field.
    stream: path !== null && path.includes(':streamGenerateContent'),
    contents: req ? (getArray(req, 'contents') ?? []) : [],
    tools: req ? (getArray(req, 'tools') ?? null) : null,
    candidates: [],
    usage: null,
    sseEvents: null,
    streamed,
    streamTruncated: truncated,
    vertexAi,
  };

  if (isStream && typeof responseBody === 'string') {
    const events = parseSseStream(responseBody);
    result.sseEvents = events;
    const reassembled = reassembleGeminiSse(events);
    result.candidates = reassembled.candidates;
    result.usage = reassembled.usage;
    if (reassembled.model && !result.model) result.model = reassembled.model;
    if (!result.model) result.model = pathModel;
    return result;
  }

  const res = safeParseJson(responseBody) as Record<string, unknown> | undefined;
  if (Array.isArray(res)) {
    // streamGenerateContent without ?alt=sse returns a JSON array of response chunks.
    const events = res.map((chunk) => ({ data: JSON.stringify(chunk) }));
    result.sseEvents = events;
    const reassembled = reassembleGeminiSse(events);
    result.candidates = reassembled.candidates;
    result.usage = reassembled.usage;
    result.model = reassembled.model ?? pathModel;
    return result;
  }
  if (res) {
    result.candidates = getArray(res, 'candidates') ?? [];
    const usageMeta = getObject(res, 'usageMetadata');
    if (usageMeta) result.usage = geminiUsageFrom(usageMeta);
    if (!result.model) {
      result.model = getString(res, 'modelVersion') ?? getString(res, 'model');
    }
  }

  if (!result.model) result.model = pathModel;

  return result;
}

// ---------------------------------------------------------------------------
// Ollama parser
// ---------------------------------------------------------------------------

/** Parse newline-delimited JSON (application/x-ndjson), skipping lines that are not JSON objects. */
function parseNdjsonLines(text: string): { line: string; value: Record<string, unknown> }[] {
  const out: { line: string; value: Record<string, unknown> }[] = [];
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) continue;
    const value = safeParseJson(line);
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      out.push({ line, value: value as Record<string, unknown> });
    }
  }
  return out;
}

/**
 * Fold a streamed Ollama /api/chat reply into one response object: message
 * content, thinking and tool_calls accumulate across chunks; top-level fields
 * (done, done_reason, prompt_eval_count, eval_count, model) take the last value.
 */
function mergeOllamaChunks(chunks: Record<string, unknown>[]): Record<string, unknown> {
  const merged: Record<string, unknown> = {};
  let role = 'assistant';
  let content = '';
  let thinking = '';
  const toolCalls: unknown[] = [];
  for (const chunk of chunks) {
    for (const [key, value] of Object.entries(chunk)) {
      if (key !== 'message') merged[key] = value;
    }
    const message = getObject(chunk, 'message');
    if (message) {
      role = getString(message, 'role') ?? role;
      content += getString(message, 'content') ?? '';
      thinking += getString(message, 'thinking') ?? '';
      toolCalls.push(...(getArray(message, 'tool_calls') ?? []));
    }
  }
  const message: Record<string, unknown> = { role, content };
  if (thinking) message['thinking'] = thinking;
  if (toolCalls.length > 0) message['tool_calls'] = toolCalls;
  merged['message'] = message;
  return merged;
}

/**
 * Fold a streamed Ollama /api/generate reply into one response object: response and
 * thinking accumulate across chunks; other fields take the last value.
 */
function mergeOllamaGenerateChunks(chunks: Record<string, unknown>[]): Record<string, unknown> {
  const merged: Record<string, unknown> = {};
  let response = '';
  let thinking = '';
  for (const chunk of chunks) {
    Object.assign(merged, chunk);
    response += getString(chunk, 'response') ?? '';
    thinking += getString(chunk, 'thinking') ?? '';
  }
  merged['response'] = response;
  if (thinking) merged['thinking'] = thinking;
  else delete merged['thinking'];
  return merged;
}

/** The system and prompt fields of an /api/generate request, as chat-style messages. */
function ollamaGenerateMessages(req: Record<string, unknown>): unknown[] {
  const messages: unknown[] = [];
  const system = getString(req, 'system');
  if (system) messages.push({ role: 'system', content: system });
  const prompt = getString(req, 'prompt');
  const images = getArray(req, 'images');
  if (prompt !== null || images) {
    messages.push({ role: 'user', content: prompt ?? '', ...(images ? { images } : {}) });
  }
  return messages;
}

function parseOllamaRequest(
  requestBody: unknown,
  responseBody: unknown,
  responseHeaders: unknown,
  endpoint: 'chat' | 'generate' = 'chat',
): OllamaParsed {
  const generate = endpoint === 'generate';
  const req = safeParseJson(requestBody) as Record<string, unknown> | undefined;
  const { streamed, truncated } = hasStreamingHeaders(responseHeaders);

  const result: OllamaParsed = {
    kind: 'ollama',
    model: req ? getString(req, 'model') : null,
    // Ollama streams unless the request sets "stream": false.
    stream: req ? (getBoolean(req, 'stream') ?? true) : false,
    messages: req ? (generate ? ollamaGenerateMessages(req) : (getArray(req, 'messages') ?? [])) : [],
    tools: req && !generate ? (getArray(req, 'tools') ?? null) : null,
    responseMessage: null,
    done: false,
    usage: null,
    sseEvents: null,
    streamed,
    streamTruncated: truncated,
    ...(generate ? { endpoint: 'generate' as const } : {}),
  };

  let res = safeParseJson(responseBody) as Record<string, unknown> | undefined;
  if (!res && typeof responseBody === 'string') {
    const lines = parseNdjsonLines(responseBody);
    if (lines.length > 0) {
      const chunks = lines.map((l) => l.value);
      res = generate ? mergeOllamaGenerateChunks(chunks) : mergeOllamaChunks(chunks);
      result.sseEvents = lines.map((l) => ({ data: l.line }));
    }
  }
  if (res) {
    if (generate) {
      const response = getString(res, 'response');
      const thinking = getString(res, 'thinking');
      // Ollama puts generate tool calls at the top level of the final object.
      const toolCalls = getArray(res, 'tool_calls');
      result.responseMessage = response !== null || thinking || toolCalls
        ? {
            role: 'assistant',
            content: response ?? '',
            ...(thinking ? { thinking } : {}),
            ...(toolCalls ? { tool_calls: toolCalls } : {}),
          }
        : null;
    } else {
      result.responseMessage = res['message'] ?? null;
    }
    result.done = getBoolean(res, 'done') ?? false;
    if (!result.model) {
      result.model = getString(res, 'model');
    }
    const promptEval = getNumber(res, 'prompt_eval_count');
    const evalCount = getNumber(res, 'eval_count');
    if (promptEval !== null || evalCount !== null) {
      result.usage = {
        prompt_eval_count: promptEval ?? undefined,
        eval_count: evalCount ?? undefined,
      };
    }
  }

  return result;
}

// ---------------------------------------------------------------------------
// Bedrock parser (InvokeModel and Converse, normalised to the Anthropic shape)
// ---------------------------------------------------------------------------

const ANTHROPIC_STREAM_EVENTS = new Set([
  'message_start', 'content_block_start', 'content_block_delta', 'content_block_stop', 'message_delta', 'message_stop',
]);

const CONVERSE_STREAM_EVENTS = [
  'messageStart', 'contentBlockStart', 'contentBlockDelta', 'contentBlockStop', 'messageStop', 'metadata',
];

function base64ToBytes(base64: string): Uint8Array | null {
  try {
    return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  } catch {
    return null;
  }
}

/** Raw bytes of a MockServer BINARY body, which is how binary event-stream captures are stored. */
function extractBinaryBodyBytes(body: unknown): Uint8Array | null {
  if (typeof body !== 'object' || body === null) return null;
  const obj = body as Record<string, unknown>;
  if (obj['type'] !== 'BINARY' || typeof obj['base64Bytes'] !== 'string') return null;
  return base64ToBytes(obj['base64Bytes']);
}

function converseBlockToAnthropic(block: unknown): AnthropicContentBlock {
  if (typeof block !== 'object' || block === null) return { type: 'unknown' };
  const b = block as Record<string, unknown>;
  if (typeof b['text'] === 'string') return { type: 'text', text: b['text'] };
  const toolUse = getObject(b, 'toolUse');
  if (toolUse) {
    return {
      type: 'tool_use',
      id: getString(toolUse, 'toolUseId') ?? undefined,
      name: getString(toolUse, 'name') ?? undefined,
      input: toolUse['input'],
    };
  }
  const toolResult = getObject(b, 'toolResult');
  if (toolResult) {
    const content = getArray(toolResult, 'content') ?? [];
    const allText = content.length > 0 && content.every((c) => getString(c, 'text') !== null);
    return {
      type: 'tool_result',
      tool_use_id: getString(toolResult, 'toolUseId') ?? undefined,
      content: allText ? content.map((c) => getString(c, 'text')).join('') : content,
    } as AnthropicContentBlock;
  }
  const reasoning = getObject(getObject(b, 'reasoningContent'), 'reasoningText');
  if (reasoning) {
    return { type: 'thinking', thinking: getString(reasoning, 'text') ?? '' } as AnthropicContentBlock;
  }
  // Other ContentBlock union members (image, document, cachePoint, ...) keep their name.
  return { type: Object.keys(b)[0] ?? 'unknown' };
}

function converseUsageToAnthropic(usage: Record<string, unknown> | null): AnthropicUsage | null {
  if (!usage) return null;
  const out: AnthropicUsage = {};
  const input = getNumber(usage, 'inputTokens');
  if (input !== null) out.input_tokens = input;
  const output = getNumber(usage, 'outputTokens');
  if (output !== null) out.output_tokens = output;
  const cacheRead = getNumber(usage, 'cacheReadInputTokens');
  if (cacheRead !== null) out.cache_read_input_tokens = cacheRead;
  const cacheWrite = getNumber(usage, 'cacheWriteInputTokens');
  if (cacheWrite !== null) out.cache_creation_input_tokens = cacheWrite;
  return out;
}

function converseToolToAnthropic(tool: unknown): unknown {
  const spec = getObject(tool, 'toolSpec');
  if (!spec) return tool;
  return {
    name: getString(spec, 'name'),
    description: getString(spec, 'description') ?? undefined,
    input_schema: getObject(spec, 'inputSchema')?.['json'],
  };
}

/** Reassemble ConverseStream events (event name in `event`, event payload in `data`). */
function reassembleConverseStream(events: SseEvent[]): ReassembledAnthropic {
  const result: ReassembledAnthropic = { content: [], usage: {}, model: null, stopReason: null };
  const text = new Map<number, string>();
  const toolInput = new Map<number, string>();
  const thinking = new Map<number, string>();
  const blockAt = (index: number | null): boolean => {
    if (index === null || index < 0 || index > MAX_ANTHROPIC_CONTENT_BLOCKS) return false;
    while (result.content.length <= index) result.content.push({ type: 'text' });
    return true;
  };
  const append = (map: Map<number, string>, index: number, part: string) => map.set(index, (map.get(index) ?? '') + part);

  for (const evt of events) {
    const data = safeParseJson(evt.data);
    if (!data || typeof data !== 'object') continue;
    const index = getNumber(data, 'contentBlockIndex');
    if (evt.event === 'contentBlockStart') {
      const toolUse = getObject(getObject(data, 'start'), 'toolUse');
      if (toolUse && blockAt(index)) {
        result.content[index!] = {
          type: 'tool_use',
          id: getString(toolUse, 'toolUseId') ?? undefined,
          name: getString(toolUse, 'name') ?? undefined,
        };
      }
    } else if (evt.event === 'contentBlockDelta') {
      const delta = getObject(data, 'delta');
      if (!delta || !blockAt(index)) continue;
      const deltaText = getString(delta, 'text');
      if (deltaText !== null) append(text, index!, deltaText);
      const inputPart = getString(getObject(delta, 'toolUse'), 'input');
      if (inputPart !== null) append(toolInput, index!, inputPart);
      const reasoningPart = getString(getObject(delta, 'reasoningContent'), 'text');
      if (reasoningPart !== null) append(thinking, index!, reasoningPart);
    } else if (evt.event === 'messageStop') {
      result.stopReason = getString(data, 'stopReason');
    } else if (evt.event === 'metadata') {
      result.usage = converseUsageToAnthropic(getObject(data, 'usage')) ?? {};
    }
  }

  for (const [index, value] of text) result.content[index] = { ...result.content[index]!, text: value };
  for (const [index, value] of thinking) {
    result.content[index] = { type: 'thinking', thinking: value } as AnthropicContentBlock;
  }
  for (const [index, value] of toolInput) {
    result.content[index] = { ...result.content[index]!, input: safeParseJson(value) ?? value };
  }
  return result;
}

/**
 * Name a Bedrock stream event. Anthropic chunks (InvokeModel on Claude) carry
 * their name in `type`; ConverseStream frames carry it in `:event-type`, and
 * InvokeModel on Nova wraps the payload in a single key named after the event.
 */
function normaliseBedrockEvent(name: string | undefined, data: string): SseEvent {
  const obj = safeParseJson(data);
  const type = getString(obj, 'type');
  if (type && (!name || name === 'chunk')) return { event: type, data };
  if (name && name !== 'chunk') return { event: name, data };
  const key = CONVERSE_STREAM_EVENTS.find((k) => getObject(obj, k) !== null);
  return key ? { event: key, data: JSON.stringify(getObject(obj, key)) } : { event: name, data };
}

function bedrockEventStreamEvents(frames: AwsEventStreamMessage[]): SseEvent[] {
  const decoder = new TextDecoder();
  return frames.map((frame) => {
    const name = frame.headers[':event-type'] ?? frame.headers[':exception-type'];
    let data = decoder.decode(frame.payload);
    if (name === 'chunk') {
      // InvokeModelWithResponseStream: the model's own chunk is base64 in `bytes`.
      const encoded = getString(safeParseJson(data), 'bytes');
      const bytes = encoded ? base64ToBytes(encoded) : null;
      if (bytes) data = decoder.decode(bytes);
    }
    return normaliseBedrockEvent(name, data);
  });
}

function tokenUsage(input: number | null, output: number | null): AnthropicUsage | null {
  if (input === null && output === null) return null;
  const usage: AnthropicUsage = {};
  if (input !== null) usage.input_tokens = input;
  if (output !== null) usage.output_tokens = output;
  return usage;
}

/** Bedrock reports InvokeModel token counts in response headers for every model family. */
function bedrockHeaderUsage(responseHeaders: unknown): AnthropicUsage | null {
  const count = (name: string): number | null => {
    const value = getHeaderValue(responseHeaders, name);
    const n = value === null ? NaN : Number(value);
    return Number.isFinite(n) ? n : null;
  };
  return tokenUsage(count('x-amzn-bedrock-input-token-count'), count('x-amzn-bedrock-output-token-count'));
}

function textBlocks(items: unknown[], key: string): AnthropicContentBlock[] {
  return items.flatMap((item) => {
    const text = getString(item, key);
    return text === null ? [] : [{ type: 'text', text }];
  });
}

interface NativeInvokeReply {
  content: AnthropicContentBlock[];
  usage: AnthropicUsage | null;
  stopReason: string | null;
}

/**
 * The native InvokeModel response bodies of the non-Anthropic, non-Nova model
 * families: Llama `generation`, Titan Text `results`, Mistral `outputs`, Cohere
 * Command `generations` and Cohere Command R `text`.
 */
function nativeInvokeReply(res: unknown): NativeInvokeReply | null {
  const generation = getString(res, 'generation');
  if (generation !== null) {
    return {
      content: [{ type: 'text', text: generation }],
      usage: tokenUsage(getNumber(res, 'prompt_token_count'), getNumber(res, 'generation_token_count')),
      stopReason: getString(res, 'stop_reason'),
    };
  }
  const results = getArray(res, 'results');
  if (results) {
    const outputTokens = results.reduce<number | null>((sum, r) => {
      const n = getNumber(r, 'tokenCount');
      return n === null ? sum : (sum ?? 0) + n;
    }, null);
    return {
      content: textBlocks(results, 'outputText'),
      usage: tokenUsage(getNumber(res, 'inputTextTokenCount'), outputTokens),
      stopReason: getString(results[0], 'completionReason'),
    };
  }
  const outputs = getArray(res, 'outputs');
  if (outputs) {
    return { content: textBlocks(outputs, 'text'), usage: null, stopReason: getString(outputs[0], 'stop_reason') };
  }
  const generations = getArray(res, 'generations');
  if (generations) {
    return { content: textBlocks(generations, 'text'), usage: null, stopReason: getString(generations[0], 'finish_reason') };
  }
  const text = getString(res, 'text');
  if (text !== null) {
    const toolCalls = (getArray(res, 'tool_calls') ?? []).map((call) => ({
      type: 'tool_use',
      name: getString(call, 'name') ?? undefined,
      input: getObject(call, 'parameters') ?? undefined,
    }));
    const billed = getObject(getObject(res, 'meta'), 'billed_units');
    return {
      content: [...(text.length > 0 ? [{ type: 'text', text }] : []), ...toolCalls],
      usage: tokenUsage(getNumber(billed, 'input_tokens'), getNumber(billed, 'output_tokens')),
      stopReason: getString(res, 'finish_reason'),
    };
  }
  return null;
}

/**
 * Reassemble InvokeModelWithResponseStream chunks of the native model families.
 * Each chunk carries the next piece of text in its family's reply field; the last
 * chunk carries `amazon-bedrock-invocationMetrics` with the token counts.
 */
function reassembleNativeInvokeStream(events: SseEvent[]): NativeInvokeReply {
  let text = '';
  let stopReason: string | null = null;
  let usage: AnthropicUsage | null = null;
  for (const evt of events) {
    if (evt.event !== undefined && evt.event !== 'chunk') continue;
    const chunk = safeParseJson(evt.data);
    if (!chunk || typeof chunk !== 'object') continue;
    const outputs = getArray(chunk, 'outputs');
    const eventType = getString(chunk, 'event_type');
    const cohereText = getBoolean(chunk, 'is_finished') !== true && (eventType === null || eventType === 'text-generation')
      ? getString(chunk, 'text') : null;
    text += getString(chunk, 'generation') ?? getString(chunk, 'outputText')
      ?? (outputs ? textBlocks(outputs, 'text').map((b) => b.text).join('') : null) ?? cohereText ?? '';
    stopReason = getString(chunk, 'stop_reason') ?? getString(chunk, 'completionReason')
      ?? getString(outputs?.[0], 'stop_reason') ?? getString(chunk, 'finish_reason') ?? stopReason;
    const metrics = getObject(chunk, 'amazon-bedrock-invocationMetrics');
    if (metrics) usage = tokenUsage(getNumber(metrics, 'inputTokenCount'), getNumber(metrics, 'outputTokenCount'));
  }
  return { content: text.length > 0 ? [{ type: 'text', text }] : [], usage, stopReason };
}

function parseBedrockRequest(
  requestBody: unknown,
  responseBody: unknown,
  responseHeaders: unknown,
  path: string | null,
  responseBytes: Uint8Array | null,
): AnthropicParsed {
  const parts = bedrockPathParts(path);
  const operation = parts?.operation ?? null;
  const req = safeParseJson(requestBody) as Record<string, unknown> | undefined;
  const { streamed, truncated } = hasStreamingHeaders(responseHeaders);

  const result: AnthropicParsed = {
    kind: 'anthropic',
    platform: 'bedrock',
    model: (req ? getString(req, 'model') : null) ?? parts?.modelId ?? null,
    stream: operation === 'invoke-with-response-stream' || operation === 'converse-stream'
      || (req ? (getBoolean(req, 'stream') ?? false) : false),
    messages: [],
    system: null,
    tools: null,
    maxTokens: null,
    responseContent: [],
    usage: null,
    stopReason: null,
    sseEvents: null,
    streamed,
    streamTruncated: truncated,
  };

  if (req) {
    const converseRequest = operation?.startsWith('converse') || 'inferenceConfig' in req || 'toolConfig' in req;
    if (converseRequest) {
      result.messages = (getArray(req, 'messages') ?? []).map((m) => {
        const content = getArray(m, 'content');
        return content ? { role: getString(m, 'role'), content: content.map(converseBlockToAnthropic) } : m;
      });
      const system = getArray(req, 'system');
      result.system = system ? system.map(converseBlockToAnthropic) : null;
      const tools = getArray(getObject(req, 'toolConfig'), 'tools');
      result.tools = tools ? tools.map(converseToolToAnthropic) : null;
      result.maxTokens = getNumber(getObject(req, 'inferenceConfig'), 'maxTokens');
    } else {
      result.messages = getArray(req, 'messages') ?? nativeInvokePrompt(req);
      result.system = req['system'] ?? req['preamble'] ?? null;
      result.tools = getArray(req, 'tools');
      result.maxTokens = getNumber(req, 'max_tokens') ?? getNumber(req, 'max_gen_len')
        ?? getNumber(getObject(req, 'textGenerationConfig'), 'maxTokenCount');
    }
  }

  const frames = responseBytes ? decodeAwsEventStream(responseBytes) : [];
  let events: SseEvent[] | null = null;
  if (frames.length > 0) {
    events = bedrockEventStreamEvents(frames);
  } else if ((streamed || isStreamResponse(responseHeaders, responseBody)) && typeof responseBody === 'string') {
    // SSE, as MockServer emits when streamingResponsesEnabled is off.
    events = parseSseStream(responseBody).map((e) => normaliseBedrockEvent(e.event, e.data));
  }

  if (events) {
    result.sseEvents = events;
    const converseStream = events.some((e) => e.event !== undefined && CONVERSE_STREAM_EVENTS.includes(e.event));
    // Chunks with no Anthropic `type` and no Converse event key are a native model family's. An
    // exception frame (named by its :exception-type) may end such a stream; it stays in sseEvents.
    const nativeStream = !converseStream
      && events.some((e) => e.event === undefined || e.event === 'chunk')
      && !events.some((e) => e.event !== undefined && ANTHROPIC_STREAM_EVENTS.has(e.event));
    if (nativeStream) {
      const reassembled = reassembleNativeInvokeStream(events);
      result.responseContent = reassembled.content;
      result.usage = reassembled.usage ?? bedrockHeaderUsage(responseHeaders);
      result.stopReason = reassembled.stopReason;
      return result;
    }
    const reassembled = converseStream ? reassembleConverseStream(events) : reassembleAnthropicSse(events);
    result.responseContent = reassembled.content;
    result.usage = reassembled.usage;
    result.stopReason = reassembled.stopReason;
    if (!result.model) result.model = reassembled.model;
    return result;
  }

  const res = safeParseJson(responseBody);
  const message = getObject(getObject(res, 'output'), 'message');
  const native = message || getArray(res, 'content') ? null : nativeInvokeReply(res);
  if (message) {
    result.responseContent = (getArray(message, 'content') ?? []).map(converseBlockToAnthropic);
    result.usage = converseUsageToAnthropic(getObject(res, 'usage'));
    result.stopReason = getString(res, 'stopReason');
  } else if (native) {
    result.responseContent = native.content;
    result.usage = native.usage ?? bedrockHeaderUsage(responseHeaders);
    result.stopReason = native.stopReason;
  } else if (res && typeof res === 'object') {
    result.responseContent = (getArray(res, 'content') ?? []) as AnthropicContentBlock[];
    result.usage = getObject(res, 'usage') as AnthropicUsage | null;
    result.stopReason = getString(res, 'stop_reason');
    if (!result.model) result.model = getString(res, 'model');
  }
  return result;
}

/** The prompt of a native InvokeModel request (Llama, Titan, Mistral, Cohere) as messages. */
function nativeInvokePrompt(req: Record<string, unknown>): unknown[] {
  const message = getString(req, 'message');
  if (message !== null) {
    // Cohere Command R: earlier turns are in chat_history, with roles USER and CHATBOT.
    const history = (getArray(req, 'chat_history') ?? []).map((turn) => ({
      role: getString(turn, 'role') === 'CHATBOT' ? 'assistant' : 'user',
      content: getString(turn, 'message') ?? '',
    }));
    return [...history, { role: 'user', content: message }];
  }
  const prompt = getString(req, 'prompt') ?? getString(req, 'inputText');
  return prompt === null ? [] : [{ role: 'user', content: prompt }];
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Parse a proxied request/response pair into a typed ParsedTraffic object.
 *
 * @param value - The `JsonListItem.value` from the store's `proxiedRequests`.
 *   Expected shape: `{ httpRequest: {...}, httpResponse: {...} }`.
 */
export function parseTraffic(value: Record<string, unknown>): ParsedTraffic {
  try {
    const httpRequest = getObject(value, 'httpRequest');
    const httpResponse = getObject(value, 'httpResponse');

    const path = httpRequest ? getString(httpRequest, 'path') : null;
    const requestBody = httpRequest ? (getObject(httpRequest, 'body') ?? httpRequest['body']) : null;
    const responseBody = httpResponse ? (getObject(httpResponse, 'body') ?? httpResponse['body']) : null;
    const responseHeaders = httpResponse ? httpResponse['headers'] : null;

    // Extract body string or object — MockServer can encode body as { type, string } or { type, json }
    const reqBodyContent = extractBodyContent(requestBody);
    const resBodyContent = extractBodyContent(responseBody);
    const requestHeaders = httpRequest ? httpRequest['headers'] : null;

    // Host-based detection FIRST, mirroring the server (LlmProviderSniffer):
    // a call to a well-known provider host is that provider regardless of path,
    // so non-chat paths to a known host classify consistently with the server.
    const host = getHeaderValue(requestHeaders, 'host');
    const vertexAnthropicModel = path?.match(VERTEX_ANTHROPIC_PATH)?.[1] ?? null;
    switch (detectByHost(host, path)) {
      case 'anthropic':
        return parseAnthropicRequest(reqBodyContent, resBodyContent, responseHeaders, vertexAnthropicModel);
      case 'bedrock':
        return parseBedrockRequest(reqBodyContent, resBodyContent, responseHeaders, path, extractBinaryBodyBytes(responseBody));
      case 'openai':
        return parseOpenAiRequest(reqBodyContent, resBodyContent, responseHeaders, path?.match(VERTEX_PUBLISHER_MODEL_PATH)?.[1] ?? null);
      case 'openai_responses':
        return parseOpenAiResponsesRequest(reqBodyContent, resBodyContent, responseHeaders);
      case 'gemini':
        return parseGeminiRequest(reqBodyContent, resBodyContent, responseHeaders, path, isVertexAiHost(httpRequest));
    }

    if (isAnthropicPath(path)) {
      return parseAnthropicRequest(reqBodyContent, resBodyContent, responseHeaders);
    }

    // Azure OpenAI uses the same wire format as OpenAI Chat Completions but
    // has a distinctive path (/openai/deployments/…/chat/completions).
    // Check before generic OpenAI path to avoid ambiguity.
    if (isAzureOpenAiPath(path)) {
      return parseOpenAiRequest(reqBodyContent, resBodyContent, responseHeaders);
    }

    if (bedrockPathParts(path) !== null) {
      return parseBedrockRequest(reqBodyContent, resBodyContent, responseHeaders, path, extractBinaryBodyBytes(responseBody));
    }

    if (vertexAnthropicModel !== null) {
      return parseAnthropicRequest(reqBodyContent, resBodyContent, responseHeaders, vertexAnthropicModel);
    }

    // OpenAI Responses API (/v1/responses) — must be checked before
    // generic OpenAI chat completions path.
    if (isOpenAiResponsesPath(path)) {
      return parseOpenAiResponsesRequest(reqBodyContent, resBodyContent, responseHeaders);
    }

    if (isOpenAiPath(path)) {
      return parseOpenAiRequest(reqBodyContent, resBodyContent, responseHeaders);
    }

    if (isGeminiPath(path)) {
      return parseGeminiRequest(reqBodyContent, resBodyContent, responseHeaders, path, isVertexAiHost(httpRequest));
    }

    if (isOllamaPath(path)) {
      return parseOllamaRequest(reqBodyContent, resBodyContent, responseHeaders);
    }

    if (isOllamaGeneratePath(path)) {
      return parseOllamaRequest(reqBodyContent, resBodyContent, responseHeaders, 'generate');
    }

    // Check MCP on request or response body. Handles a single JSON-RPC object, a
    // JSON-RPC batch (top-level array), and JSON-RPC carried over SSE.
    const reqRpc = firstJsonRpcMessage(reqBodyContent);
    const resRpc = firstJsonRpcMessage(resBodyContent);
    if (reqRpc || resRpc) {
      return parseMcpRequest(reqRpc, resRpc);
    }

    // Resilient fallback: recognise LLM traffic by its body shape when the host/
    // path was not a known LLM endpoint (e.g. a coding CLI on a private gateway
    // or a renamed endpoint). The body is the slowest-moving signal.
    switch (detectByBodyShape(reqBodyContent, resBodyContent, requestHeaders)) {
      case 'anthropic':
        return parseAnthropicRequest(reqBodyContent, resBodyContent, responseHeaders);
      case 'openai':
        return parseOpenAiRequest(reqBodyContent, resBodyContent, responseHeaders);
      case 'openai_responses':
        return parseOpenAiResponsesRequest(reqBodyContent, resBodyContent, responseHeaders);
      case 'gemini':
        return parseGeminiRequest(reqBodyContent, resBodyContent, responseHeaders, path, isVertexAiHost(httpRequest));
    }

    const statusCode = httpResponse ? getNumber(httpResponse, 'statusCode') : null;
    const method = httpRequest ? getString(httpRequest, 'method') : null;

    return { kind: 'generic', method, path, statusCode };
  } catch {
    return { kind: 'generic', method: null, path: null, statusCode: null };
  }
}

/**
 * Per-object parse cache for {@link parseTraffic}.
 *
 * `parseTraffic` fully classifies a request/response pair and, for streamed
 * responses, reassembles the entire SSE body and base64-decodes binary bodies —
 * expensive to re-run for every captured request on every ~1/sec WebSocket push
 * (and every render that groups or summarises traffic). The store's
 * `reconcileByKey` preserves each unchanged item's `value` reference across
 * pushes, so a WeakMap keyed on that reference returns the previously-parsed
 * result until the underlying object actually changes — a changed item is
 * delivered as a fresh reference and so misses the cache and re-parses. The
 * WeakMap lets entries be collected once the item object is unreachable.
 *
 * This mirrors the `summaryCache` / `searchTextCache` pattern already used in
 * TrafficInspector, and is shared by `summarizeTraffic` here and by the Trace
 * view's session grouping so both reuse a single parse per item.
 */
const parseTrafficCache = new WeakMap<Record<string, unknown>, ParsedTraffic>();

export function cachedParseTraffic(value: Record<string, unknown>): ParsedTraffic {
  const hit = parseTrafficCache.get(value);
  if (hit !== undefined) return hit;
  const parsed = parseTraffic(value);
  parseTrafficCache.set(value, parsed);
  return parsed;
}

/**
 * Extract the actual body content from MockServer's body representation.
 * MockServer bodies can be:
 * - A plain string
 * - An object with { type: "STRING", string: "..." }
 * - An object with { type: "JSON", json: "..." } or { type: "JSON", json: {...} }
 * - An object with { type: "BINARY", base64Bytes: "..." }
 * - An object directly (already parsed JSON)
 */
export function extractBodyContent(body: unknown): unknown {
  if (typeof body === 'string') return body;
  if (typeof body !== 'object' || body === null) return body;

  const obj = body as Record<string, unknown>;

  // MockServer body wrapper: { type: "STRING"|"JSON"|"BINARY", string|json|base64Bytes: ... }
  if ('type' in obj) {
    if ('string' in obj) return obj['string'];
    if ('json' in obj) return obj['json'];
    if (obj['type'] === 'BINARY' && typeof obj['base64Bytes'] === 'string') {
      try {
        const bytes = atob(obj['base64Bytes'] as string);
        return new TextDecoder().decode(
          Uint8Array.from(bytes, (c) => c.charCodeAt(0)),
        );
      } catch {
        // Fall through to return the original object if decoding fails
        return body;
      }
    }
  }

  // Already a plain object (e.g., already-parsed JSON body)
  return body;
}

/**
 * Build a summary for the master list from a proxied request item.
 */
export function summarizeTraffic(value: Record<string, unknown>): TrafficSummary {
  const httpRequest = getObject(value, 'httpRequest');
  const httpResponse = getObject(value, 'httpResponse');

  const method = httpRequest ? getString(httpRequest, 'method') : null;
  const path = httpRequest ? getString(httpRequest, 'path') : null;
  const statusCode = httpResponse ? getNumber(httpResponse, 'statusCode') : null;

  // Try to extract host from headers
  let host: string | null = null;
  if (httpRequest) {
    const headers = httpRequest['headers'];
    host = getHeaderValue(headers, 'host');
    if (!host) {
      // Try the Host header with capital H
      host = getHeaderValue(headers, 'Host');
    }
  }

  const parsed = cachedParseTraffic(value);

  // Extract per-request timing from the forwarded response (if present)
  let timing: RequestTiming | null = null;
  if (httpResponse) {
    const timingObj = getObject(httpResponse, 'timing');
    if (timingObj) {
      timing = {
        connectionTimeInMillis: getNumber(timingObj, 'connectionTimeInMillis'),
        timeToFirstByteInMillis: getNumber(timingObj, 'timeToFirstByteInMillis'),
        totalTimeInMillis: getNumber(timingObj, 'totalTimeInMillis'),
        requestStartedMillis: getNumber(timingObj, 'requestStartedMillis'),
        connectionEstablishedMillis: getNumber(timingObj, 'connectionEstablishedMillis'),
        responseReceivedMillis: getNumber(timingObj, 'responseReceivedMillis'),
        injectedChaosLatencyMillis: getNumber(timingObj, 'injectedChaosLatencyMillis'),
        injectedDelayMillis: getNumber(timingObj, 'injectedDelayMillis'),
        breakpointHeldMillis: getNumber(timingObj, 'breakpointHeldMillis'),
      };
    }
  }

  return {
    host,
    method,
    path,
    statusCode,
    parsed,
    timing,
  };
}

/**
 * Get a display label for the model from parsed traffic.
 */
export function getModelLabel(parsed: ParsedTraffic): string | null {
  if (
    parsed.kind === 'anthropic' ||
    parsed.kind === 'openai' ||
    parsed.kind === 'openai_responses' ||
    parsed.kind === 'gemini' ||
    parsed.kind === 'ollama'
  ) {
    return parsed.model;
  }
  return null;
}

/**
 * Get token usage summary string.
 */
export function getTokenSummary(parsed: ParsedTraffic): string | null {
  if (parsed.kind === 'anthropic' && parsed.usage) {
    const parts: string[] = [];
    if (parsed.usage.input_tokens != null) parts.push(`${parsed.usage.input_tokens} in`);
    if (parsed.usage.output_tokens != null) parts.push(`${parsed.usage.output_tokens} out`);
    // Surface prompt-cache counters so agentic CLI traffic isn't undercounted.
    if (parsed.usage.cache_creation_input_tokens != null) parts.push(`${parsed.usage.cache_creation_input_tokens} cache write`);
    if (parsed.usage.cache_read_input_tokens != null) parts.push(`${parsed.usage.cache_read_input_tokens} cache read`);
    return parts.length > 0 ? parts.join(' / ') : null;
  }
  if (parsed.kind === 'openai' && parsed.usage) {
    const parts: string[] = [];
    if (parsed.usage.prompt_tokens != null) parts.push(`${parsed.usage.prompt_tokens} in`);
    if (parsed.usage.completion_tokens != null) parts.push(`${parsed.usage.completion_tokens} out`);
    return parts.length > 0 ? parts.join(' / ') : null;
  }
  if (parsed.kind === 'openai_responses' && parsed.usage) {
    // Responses API uses input_tokens / output_tokens.
    const parts: string[] = [];
    if (parsed.usage.input_tokens != null) parts.push(`${parsed.usage.input_tokens} in`);
    if (parsed.usage.output_tokens != null) parts.push(`${parsed.usage.output_tokens} out`);
    return parts.length > 0 ? parts.join(' / ') : null;
  }
  if (parsed.kind === 'gemini' && parsed.usage) {
    const parts: string[] = [];
    if (parsed.usage.promptTokenCount != null) parts.push(`${parsed.usage.promptTokenCount} in`);
    const output = geminiOutputTokens(parsed.usage);
    if (output != null) {
      const thoughts = parsed.usage.thoughtsTokenCount;
      parts.push(thoughts ? `${output} out (${thoughts} thinking)` : `${output} out`);
    }
    return parts.length > 0 ? parts.join(' / ') : null;
  }
  if (parsed.kind === 'ollama' && parsed.usage) {
    const parts: string[] = [];
    if (parsed.usage.prompt_eval_count != null) parts.push(`${parsed.usage.prompt_eval_count} in`);
    if (parsed.usage.eval_count != null) parts.push(`${parsed.usage.eval_count} out`);
    return parts.length > 0 ? parts.join(' / ') : null;
  }
  return null;
}

/**
 * Extract numeric input/output tokens from parsed traffic.
 * Returns { inputTokens, outputTokens } or null if the parsed traffic
 * has no usage data. Used by the Sessions view to compute per-session totals.
 */
export function getNumericTokens(parsed: ParsedTraffic): { inputTokens: number; outputTokens: number } | null {
  if (parsed.kind === 'anthropic' && parsed.usage) {
    return {
      inputTokens: parsed.usage.input_tokens ?? 0,
      outputTokens: parsed.usage.output_tokens ?? 0,
    };
  }
  if (parsed.kind === 'openai' && parsed.usage) {
    return {
      inputTokens: parsed.usage.prompt_tokens ?? 0,
      outputTokens: parsed.usage.completion_tokens ?? 0,
    };
  }
  if (parsed.kind === 'openai_responses' && parsed.usage) {
    return {
      inputTokens: parsed.usage.input_tokens ?? 0,
      outputTokens: parsed.usage.output_tokens ?? 0,
    };
  }
  if (parsed.kind === 'gemini' && parsed.usage) {
    return {
      inputTokens: parsed.usage.promptTokenCount ?? 0,
      outputTokens: geminiOutputTokens(parsed.usage) ?? 0,
    };
  }
  if (parsed.kind === 'ollama' && parsed.usage) {
    return {
      inputTokens: parsed.usage.prompt_eval_count ?? 0,
      outputTokens: parsed.usage.eval_count ?? 0,
    };
  }
  return null;
}

/**
 * Get a compact timing label for the master list (e.g. "142ms").
 * Returns null when timing is not available.
 */
export function getTimingLabel(timing: RequestTiming | null): string | null {
  if (!timing) return null;
  if (timing.totalTimeInMillis !== null) return `${timing.totalTimeInMillis}ms`;
  return null;
}

/**
 * Build a detailed timing breakdown string (e.g. "connect 12ms / TTFB 85ms / total 142ms").
 * Returns null when timing is not available.
 */
export function getTimingBreakdown(timing: RequestTiming | null): string | null {
  if (!timing) return null;
  const parts: string[] = [];
  if (timing.connectionTimeInMillis !== null) parts.push(`connect ${timing.connectionTimeInMillis}ms`);
  if (timing.timeToFirstByteInMillis !== null) parts.push(`TTFB ${timing.timeToFirstByteInMillis}ms`);
  if (timing.totalTimeInMillis !== null) parts.push(`total ${timing.totalTimeInMillis}ms`);
  return parts.length > 0 ? parts.join(' / ') : null;
}

// ---------------------------------------------------------------------------
// MCP server health aggregation
//
// When a coding-assistant CLI is proxied through MockServer its MCP servers
// (chrome-devtools, devbot, …) are frequently the real latency bottleneck — the
// MCP server stalls for tens of seconds while MockServer's own part is a couple
// of seconds. The dashboard surfaces per-MCP-server health so the user can SEE
// which server is slow or erroring instead of guessing.
//
// This is a PURE aggregation over the same `summarizeTraffic` classification the
// Traffic view uses (no duplicated MCP detection): it groups MCP JSON-RPC
// exchanges by upstream host and derives call counts, error rate, latency
// percentiles, the slowest method, and a slow flag. Kept React-free so it is
// unit-testable.
// ---------------------------------------------------------------------------

/**
 * Latency (ms) at or above which a server is flagged `slow`. A real
 * chrome-devtools MCP exchange that stalled ~32s sits far above this; a healthy
 * tool call returns in well under a second, so 5s cleanly separates the two.
 */
export const MCP_SLOW_THRESHOLD_MS = 5000;

export interface McpServerHealth {
  /** Upstream host the MCP server was reached on, or a placeholder when absent. */
  server: string;
  /** Total MCP JSON-RPC exchanges seen for this server. */
  callCount: number;
  /** Exchanges that carried a JSON-RPC `error` or a non-2xx HTTP status. */
  errorCount: number;
  /** errorCount / callCount, in 0..1. */
  errorRate: number;
  /** Slowest single exchange (ms), or null when no exchange had timing. */
  maxLatencyMs: number | null;
  /** 95th-percentile latency (ms, nearest-rank), or null when no timing. */
  p95LatencyMs: number | null;
  /** Median latency (ms, nearest-rank), or null when no timing. */
  medianLatencyMs: number | null;
  /** JSON-RPC method of the single slowest exchange, or null when unknown. */
  slowestMethod: string | null;
  /** True when p95 (or max, when p95 is unavailable) is at/over the threshold. */
  slow: boolean;
}

const UNKNOWN_MCP_SERVER = '(unknown host)';

/**
 * Nearest-rank percentile over an already-ascending-sorted array. Returns null
 * for an empty input. q is clamped to 0..1.
 */
function nearestRankPercentile(sortedAsc: number[], q: number): number | null {
  if (sortedAsc.length === 0) return null;
  const clamped = q < 0 ? 0 : q > 1 ? 1 : q;
  const rank = Math.ceil(clamped * sortedAsc.length);
  const index = Math.min(Math.max(rank, 1), sortedAsc.length) - 1;
  return sortedAsc[index] ?? null;
}

interface McpServerAccumulator {
  callCount: number;
  errorCount: number;
  latencies: number[];
  maxLatencyMs: number | null;
  slowestMethod: string | null;
}

/**
 * Total response time in milliseconds for a recorded exchange, preferring the rich
 * forward-path {@link RequestTiming} (`timing.totalTimeInMillis`, present on
 * transparent-proxy traffic) and falling back to MockServer's measured
 * `x-mockserver-response-time-ms` response header — present on recorded / replayed /
 * expectation-forwarded responses that carry no full timing object. Null when neither
 * is available.
 */
function exchangeLatencyMs(summary: TrafficSummary, value: Record<string, unknown>): number | null {
  const fromTiming = summary.timing?.totalTimeInMillis ?? null;
  if (fromTiming != null && Number.isFinite(fromTiming)) return fromTiming;
  // An externally-measured latency hint on the REQUEST, used when MockServer did not itself time the
  // call: the stdio-MCP capture bridge (scripts/llm-proxy-capture/mcp-stdio-capture.mjs) times the real
  // out-of-band MCP exchange and forwards it as `x-mcp-latency-ms`, since MockServer's own response time
  // would otherwise reflect only its tiny processing time for that injected record. Preferred over the
  // response-time header so a slow stdio MCP server shows its true latency in the panel.
  const httpRequest = getObject(value, 'httpRequest');
  if (httpRequest) {
    const ext = getHeaderValue(httpRequest['headers'], 'x-mcp-latency-ms');
    const extMs = ext != null ? Number(ext) : NaN;
    if (Number.isFinite(extMs)) return extMs;
  }
  const httpResponse = getObject(value, 'httpResponse');
  if (!httpResponse) return null;
  const raw = getHeaderValue(httpResponse['headers'], 'x-mockserver-response-time-ms');
  if (raw == null) return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

/**
 * Aggregate captured proxied/recorded request values into per-MCP-server health.
 *
 * @param values - The `JsonListItem.value` objects from the store's
 *   `proxiedRequests` / `recordedRequests`. Non-MCP traffic is ignored.
 * @param slowThresholdMs - Latency at/above which a server is flagged slow.
 * @returns One entry per MCP server, sorted worst-first (errors, then latency).
 */
export function aggregateMcpServerHealth(
  values: Array<Record<string, unknown>>,
  slowThresholdMs: number = MCP_SLOW_THRESHOLD_MS,
): McpServerHealth[] {
  const byServer = new Map<string, McpServerAccumulator>();

  for (const value of values) {
    if (!value || typeof value !== 'object') continue;
    let summary: TrafficSummary;
    try {
      summary = summarizeTraffic(value);
    } catch {
      continue;
    }
    if (summary.parsed.kind !== 'mcp') continue;
    const parsed = summary.parsed;

    const server = summary.host ?? UNKNOWN_MCP_SERVER;
    let acc = byServer.get(server);
    if (!acc) {
      acc = { callCount: 0, errorCount: 0, latencies: [], maxLatencyMs: null, slowestMethod: null };
      byServer.set(server, acc);
    }

    acc.callCount += 1;

    // Error rule mirrors the Traffic view (mcpErrorInfo): a JSON-RPC `error`
    // object OR a non-2xx HTTP status counts as a failed exchange.
    const status = summary.statusCode;
    const isError = parsed.error != null || (status != null && (status < 200 || status >= 300));
    if (isError) acc.errorCount += 1;

    const latency = exchangeLatencyMs(summary, value);
    if (latency != null && Number.isFinite(latency)) {
      acc.latencies.push(latency);
      if (acc.maxLatencyMs == null || latency > acc.maxLatencyMs) {
        acc.maxLatencyMs = latency;
        acc.slowestMethod = parsed.method;
      }
    }
  }

  const result: McpServerHealth[] = [];
  for (const [server, acc] of byServer) {
    const sorted = [...acc.latencies].sort((a, b) => a - b);
    const p95 = nearestRankPercentile(sorted, 0.95);
    const median = nearestRankPercentile(sorted, 0.5);
    const slowBasis = p95 ?? acc.maxLatencyMs;
    result.push({
      server,
      callCount: acc.callCount,
      errorCount: acc.errorCount,
      errorRate: acc.callCount > 0 ? acc.errorCount / acc.callCount : 0,
      maxLatencyMs: acc.maxLatencyMs,
      p95LatencyMs: p95,
      medianLatencyMs: median,
      slowestMethod: acc.slowestMethod,
      slow: slowBasis != null && slowBasis >= slowThresholdMs,
    });
  }

  // Worst-first: most errors, then highest error rate, then slowest (p95 or max),
  // then busiest, then host name for a stable, deterministic order.
  result.sort((a, b) => {
    if (b.errorCount !== a.errorCount) return b.errorCount - a.errorCount;
    if (b.errorRate !== a.errorRate) return b.errorRate - a.errorRate;
    const aLat = a.p95LatencyMs ?? a.maxLatencyMs ?? -1;
    const bLat = b.p95LatencyMs ?? b.maxLatencyMs ?? -1;
    if (bLat !== aLat) return bLat - aLat;
    if (b.callCount !== a.callCount) return b.callCount - a.callCount;
    return a.server.localeCompare(b.server);
  });

  return result;
}

// ---------------------------------------------------------------------------
// Growing-conversation grouping
//
// A stateless coding-assistant CLI (e.g. opencode → OpenAI Codex) resends its
// ENTIRE growing conversation on every turn: 10 consecutive requests to the same
// endpoint each carry a message list that is a growing SUPERSET of the previous
// one. Rendered one-per-request that reads as endless near-duplicate history.
//
// `groupConversationTurns` is a PURE, provider-agnostic, deterministic walk that
// collapses a run of consecutive entries into ONE conversation whenever each
// entry's request-message list is a prefix (leading subset) of the next entry's.
// Within a group, each turn exposes only the NEW messages it added (the delta
// beyond the previous turn); the final assistant response is carried on the last
// turn's `parsed`. Entries that are not part of a growing run stay as their own
// single-turn group. Grouping is non-destructive: every turn keeps its original
// `parsed` and opaque `data` payload, so the raw per-request data stays reachable.
// ---------------------------------------------------------------------------

/** Provider kinds that carry a request-side conversation message list. */
const CONVERSATION_KINDS: ReadonlySet<ParsedTraffic['kind']> = new Set([
  'anthropic',
  'openai',
  'openai_responses',
  'gemini',
  'ollama',
]);

/**
 * The ordered request-side message list for a parsed entry, or null when the
 * kind has no conversation history (mcp / generic). Each provider stores the
 * growing history under a different field; this normalises them to one array of
 * raw provider message objects without altering their shape.
 */
function conversationMessageList(parsed: ParsedTraffic): unknown[] | null {
  switch (parsed.kind) {
    case 'anthropic':
    case 'openai':
    case 'ollama':
      return parsed.messages;
    case 'openai_responses':
      return parsed.input;
    case 'gemini':
      return parsed.contents;
    default:
      return null;
  }
}

/**
 * Stable text signature of a single message, used only for the prefix
 * comparison. The CLI resends byte-identical history, so JSON.stringify yields
 * identical strings for unchanged leading messages. Total — never throws.
 */
function messageSignature(message: unknown): string {
  try {
    const json = JSON.stringify(message);
    return json === undefined ? `\0${String(message)}` : json;
  } catch {
    return `\0${String(message)}`;
  }
}

/**
 * True when `prev` is a leading prefix of `next` (equality allowed, so an exact
 * resend still counts as the same conversation). A single differing or missing
 * leading message means the histories diverged (edited history) — not a prefix.
 */
function isSignaturePrefix(prev: readonly string[], next: readonly string[]): boolean {
  if (prev.length > next.length) return false;
  for (let i = 0; i < prev.length; i++) {
    if (prev[i] !== next[i]) return false;
  }
  return true;
}

/** One entry to be grouped: its parsed traffic, upstream host, and an opaque payload. */
export interface ConversationEntryInput<T> {
  parsed: ParsedTraffic;
  /** Upstream host (from the Host header). Two different hosts never group together. */
  host: string | null;
  /** Opaque per-entry payload returned untouched on the resulting turn (e.g. the request item). */
  data: T;
}

/** One turn within a grouped conversation. */
export interface ConversationTurn<T> {
  /** 0-based position of this turn within its group. */
  turnIndex: number;
  parsed: ParsedTraffic;
  data: T;
  /**
   * The messages this turn added relative to the previous turn (the full initial
   * history for turn 0). Raw provider message objects, in order. Empty when a
   * turn re-sent an identical history (an exact duplicate resend).
   */
  newMessages: unknown[];
}

/** A run of consecutive entries that form one growing conversation (or a lone entry). */
export interface ConversationGroup<T> {
  /** Stable, unique key for React lists (deterministic given input order). */
  key: string;
  host: string | null;
  kind: ParsedTraffic['kind'];
  /** The opaque payloads of every entry in this group, in order. */
  entries: T[];
  turns: ConversationTurn<T>[];
  /** True when the group collapses more than one entry into a single thread. */
  collapsed: boolean;
}

/**
 * Group a chronological (oldest-first) list of LLM entries into growing
 * conversation threads. See the section comment above for the collapse rule.
 *
 * Conservative by construction: a group only extends while the next entry's
 * request-message list is a genuine prefix-extension of the current one AND the
 * provider kind and host match. Any divergence starts a fresh group, so
 * unrelated or edited-history requests are never merged. Non-conversation
 * traffic (mcp / generic) is always its own single-turn group.
 */
export function groupConversationTurns<T>(
  entries: ReadonlyArray<ConversationEntryInput<T>>,
): ConversationGroup<T>[] {
  const groups: ConversationGroup<T>[] = [];
  let counter = 0;

  // Build state for the group currently being extended.
  let current:
    | { group: ConversationGroup<T>; lastSignatures: string[]; lastLength: number }
    | null = null;

  const flush = () => {
    if (current) {
      current.group.collapsed = current.group.turns.length > 1;
      groups.push(current.group);
      current = null;
    }
  };

  for (const entry of entries) {
    const messages = conversationMessageList(entry.parsed);

    // Non-conversation traffic never groups — always a standalone single-turn group.
    if (messages === null) {
      flush();
      groups.push({
        key: `${entry.parsed.kind}:${counter++}`,
        host: entry.host,
        kind: entry.parsed.kind,
        entries: [entry.data],
        turns: [{ turnIndex: 0, parsed: entry.parsed, data: entry.data, newMessages: [] }],
        collapsed: false,
      });
      continue;
    }

    const signatures = messages.map(messageSignature);
    const canExtend =
      current !== null &&
      current.group.kind === entry.parsed.kind &&
      current.group.host === entry.host &&
      isSignaturePrefix(current.lastSignatures, signatures);

    if (canExtend && current) {
      current.group.turns.push({
        turnIndex: current.group.turns.length,
        parsed: entry.parsed,
        data: entry.data,
        newMessages: messages.slice(current.lastLength),
      });
      current.group.entries.push(entry.data);
      current.lastSignatures = signatures;
      current.lastLength = messages.length;
    } else {
      flush();
      const group: ConversationGroup<T> = {
        key: `${entry.parsed.kind}:${counter++}`,
        host: entry.host,
        kind: entry.parsed.kind,
        entries: [entry.data],
        // Turn 0's delta is the full initial history.
        turns: [{ turnIndex: 0, parsed: entry.parsed, data: entry.data, newMessages: messages }],
        collapsed: false,
      };
      current = { group, lastSignatures: signatures, lastLength: messages.length };
    }
  }

  flush();
  return groups;
}

export { CONVERSATION_KINDS };
