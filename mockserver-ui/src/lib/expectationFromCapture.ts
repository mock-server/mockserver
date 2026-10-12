/**
 * Pure function that extracts a mock expectation draft from a captured
 * request/response pair.
 *
 * Supports two modes:
 * - **LLM mode** — for recognised LLM provider traffic (Anthropic, OpenAI, etc.)
 *   the draft targets `httpLlmResponse` with provider-specific fields.
 * - **Generic HTTP mode** — for any other traffic the draft targets a plain
 *   `httpResponse` with status, headers and body extracted from the capture.
 *
 * A **matcher precision** setting controls how tightly the generated
 * `httpRequest` matcher is bound:
 * - `exact` — method + path + queryString + headers + body
 * - `moderate` — method + path + queryString + body  (default)
 * - `loose` — method + path only
 */

import type {
  ParsedTraffic,
  AnthropicParsed,
  OpenAiParsed,
  OpenAiResponsesParsed,
  GeminiParsed,
  OllamaParsed,
} from './llmTraffic';

// ---------------------------------------------------------------------------
// Matcher precision
// ---------------------------------------------------------------------------

export const MATCHER_PRECISIONS = ['exact', 'moderate', 'loose'] as const;

export type MatcherPrecision = (typeof MATCHER_PRECISIONS)[number];

// ---------------------------------------------------------------------------
// Provider enum (matches Java org.mockserver.model.Provider)
// ---------------------------------------------------------------------------

// AZURE_OPENAI is user-selectable-only: Azure traffic parses as kind 'openai'.
// BEDROCK is detected from the Anthropic-shaped parse's `platform` marker.
export const PROVIDERS = [
  'ANTHROPIC',
  'OPENAI',
  'OPENAI_RESPONSES',
  'GEMINI',
  'BEDROCK',
  'AZURE_OPENAI',
  'OLLAMA',
] as const;

export type ProviderName = (typeof PROVIDERS)[number];

// ---------------------------------------------------------------------------
// Expectation draft — the editable shape surfaced in the dialog
// ---------------------------------------------------------------------------

export interface ToolCallDraft {
  name: string;
  arguments?: string;
}

/** Header as used in the draft (matches MockServer's { name, values } shape). */
export interface HeaderDraft {
  name: string;
  values: string[];
}

/** Query-string parameter in the draft. */
export interface QueryParamDraft {
  name: string;
  values: string[];
}

/**
 * LLM expectation draft — for recognised LLM provider traffic.
 */
export interface LlmExpectationDraft {
  kind: 'llm';
  path: string;
  provider: ProviderName;
  model: string;
  text: string;
  toolCalls: ToolCallDraft[];
  stopReason: string;
  streaming: boolean;
}

/**
 * Generic HTTP expectation draft — for arbitrary HTTP/gRPC/GraphQL traffic.
 */
export interface GenericExpectationDraft {
  kind: 'generic';
  method: string;
  path: string;
  queryStringParameters: QueryParamDraft[];
  headers: HeaderDraft[];
  body: string;
  /** Response fields */
  responseStatusCode: number;
  responseHeaders: HeaderDraft[];
  responseBody: string;
  /** Controls how much of the request is matched. */
  matcherPrecision: MatcherPrecision;
}

export type ExpectationDraft = LlmExpectationDraft | GenericExpectationDraft;

// ---------------------------------------------------------------------------
// Provider detection from ParsedTraffic kind
// ---------------------------------------------------------------------------

function kindToProvider(parsed: ParsedTraffic): ProviderName {
  switch (parsed.kind) {
    case 'anthropic':
      return parsed.platform === 'bedrock' ? 'BEDROCK' : 'ANTHROPIC';
    case 'openai':
      return 'OPENAI';
    case 'openai_responses':
      return 'OPENAI_RESPONSES';
    case 'gemini':
      return 'GEMINI';
    case 'ollama':
      return 'OLLAMA';
    default:
      return 'ANTHROPIC'; // safe fallback
  }
}

// ---------------------------------------------------------------------------
// Per-provider extraction
// ---------------------------------------------------------------------------

function extractAnthropic(parsed: AnthropicParsed): Partial<LlmExpectationDraft> {
  const textParts: string[] = [];
  const toolCalls: ToolCallDraft[] = [];

  for (const block of parsed.responseContent) {
    if (block.type === 'text' && block.text) {
      textParts.push(block.text);
    }
    if (block.type === 'tool_use' && block.name) {
      const tc: ToolCallDraft = { name: block.name };
      if (block.input != null) {
        tc.arguments = typeof block.input === 'string'
          ? block.input
          : JSON.stringify(block.input);
      }
      toolCalls.push(tc);
    }
  }

  return {
    model: parsed.model ?? '',
    text: textParts.join(''),
    toolCalls,
    stopReason: parsed.stopReason ?? '',
    streaming: parsed.stream,
  };
}

function extractOpenAi(parsed: OpenAiParsed): Partial<LlmExpectationDraft> {
  const textParts: string[] = [];
  const toolCalls: ToolCallDraft[] = [];
  let stopReason = '';

  for (const choice of parsed.choices) {
    if (choice.message?.content) {
      textParts.push(choice.message.content);
    }
    if (choice.message?.tool_calls) {
      for (const tc of choice.message.tool_calls) {
        const tcObj = tc as Record<string, unknown>;
        const fn = tcObj['function'] as Record<string, unknown> | undefined;
        if (fn && typeof fn['name'] === 'string') {
          const draft: ToolCallDraft = { name: fn['name'] };
          if (typeof fn['arguments'] === 'string') {
            draft.arguments = fn['arguments'];
          }
          toolCalls.push(draft);
        }
      }
    }
    if (choice.finish_reason) {
      stopReason = choice.finish_reason;
    }
  }

  return {
    model: parsed.model ?? '',
    text: textParts.join(''),
    toolCalls,
    stopReason,
    streaming: parsed.stream,
  };
}

function extractOpenAiResponses(parsed: OpenAiResponsesParsed): Partial<LlmExpectationDraft> {
  const textParts: string[] = [];
  const toolCalls: ToolCallDraft[] = [];

  for (const item of parsed.output) {
    const obj = item as Record<string, unknown>;
    if (obj['type'] === 'message') {
      const content = obj['content'];
      if (Array.isArray(content)) {
        for (const c of content) {
          const co = c as Record<string, unknown>;
          if (co['type'] === 'output_text' && typeof co['text'] === 'string') {
            textParts.push(co['text']);
          }
        }
      }
    }
    if (obj['type'] === 'function_call') {
      const name = obj['name'];
      if (typeof name === 'string') {
        const draft: ToolCallDraft = { name };
        if (typeof obj['arguments'] === 'string') {
          draft.arguments = obj['arguments'];
        }
        toolCalls.push(draft);
      }
    }
  }

  return {
    model: parsed.model ?? '',
    text: textParts.join(''),
    toolCalls,
    stopReason: '',
    streaming: parsed.stream,
  };
}

function extractGemini(parsed: GeminiParsed): Partial<LlmExpectationDraft> {
  const textParts: string[] = [];
  const toolCalls: ToolCallDraft[] = [];

  for (const candidate of parsed.candidates) {
    const cObj = candidate as Record<string, unknown>;
    const content = cObj['content'] as Record<string, unknown> | undefined;
    if (content && Array.isArray(content['parts'])) {
      for (const part of content['parts'] as Record<string, unknown>[]) {
        if (typeof part['text'] === 'string') {
          textParts.push(part['text']);
        }
        const fnCall = part['functionCall'] as Record<string, unknown> | undefined;
        if (fnCall && typeof fnCall['name'] === 'string') {
          const draft: ToolCallDraft = { name: fnCall['name'] };
          if (fnCall['args'] != null) {
            draft.arguments = typeof fnCall['args'] === 'string'
              ? fnCall['args']
              : JSON.stringify(fnCall['args']);
          }
          toolCalls.push(draft);
        }
      }
    }
  }

  return {
    model: parsed.model ?? '',
    text: textParts.join(''),
    toolCalls,
    stopReason: '',
    streaming: parsed.stream,
  };
}

function extractOllama(parsed: OllamaParsed): Partial<LlmExpectationDraft> {
  let text = '';
  const toolCalls: ToolCallDraft[] = [];

  if (parsed.responseMessage != null) {
    const msg = parsed.responseMessage as Record<string, unknown>;
    if (typeof msg['content'] === 'string') {
      text = msg['content'];
    }
    if (Array.isArray(msg['tool_calls'])) {
      for (const tc of msg['tool_calls'] as Record<string, unknown>[]) {
        const fn = tc['function'] as Record<string, unknown> | undefined;
        if (fn && typeof fn['name'] === 'string') {
          const draft: ToolCallDraft = { name: fn['name'] };
          if (fn['arguments'] != null) {
            draft.arguments = typeof fn['arguments'] === 'string'
              ? fn['arguments']
              : JSON.stringify(fn['arguments']);
          }
          toolCalls.push(draft);
        }
      }
    }
  }

  return {
    model: parsed.model ?? '',
    text,
    toolCalls,
    stopReason: '',
    streaming: parsed.stream,
  };
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Returns `true` if the parsed traffic is LLM traffic (not generic or MCP)
 * and can therefore be converted into an LLM mock expectation.
 */
export function isLlmTraffic(parsed: ParsedTraffic): boolean {
  return (
    parsed.kind === 'anthropic' ||
    parsed.kind === 'openai' ||
    parsed.kind === 'openai_responses' ||
    parsed.kind === 'gemini' ||
    parsed.kind === 'ollama'
  );
}

/**
 * Returns `true` if the parsed traffic can be captured as any kind of
 * mock expectation (LLM or generic HTTP).
 */
export function isCapturableTraffic(parsed: ParsedTraffic): boolean {
  return parsed.kind !== 'mcp'; // MCP JSON-RPC not yet supported
}

/**
 * Extract an editable LLM expectation draft from a captured and parsed
 * LLM request/response pair.
 *
 * @param parsed   The parsed traffic from `parseTraffic()`.
 * @param path     The captured request path.
 * @returns A `LlmExpectationDraft` with defaults populated from the capture.
 */
export function extractLlmExpectationFromCapture(
  parsed: ParsedTraffic,
  path: string,
): LlmExpectationDraft {
  const provider = kindToProvider(parsed);
  let extracted: Partial<LlmExpectationDraft> = {};

  switch (parsed.kind) {
    case 'anthropic':
      extracted = extractAnthropic(parsed);
      break;
    case 'openai':
      extracted = extractOpenAi(parsed);
      break;
    case 'openai_responses':
      extracted = extractOpenAiResponses(parsed);
      break;
    case 'gemini':
      extracted = extractGemini(parsed);
      break;
    case 'ollama':
      extracted = extractOllama(parsed);
      break;
  }

  return {
    kind: 'llm',
    path,
    provider,
    model: extracted.model ?? '',
    text: extracted.text ?? '',
    toolCalls: extracted.toolCalls ?? [],
    stopReason: extracted.stopReason ?? '',
    streaming: extracted.streaming ?? false,
  };
}

// ---------------------------------------------------------------------------
// Helpers for extracting raw body/headers from a captured request value
// ---------------------------------------------------------------------------

/** Extract headers from MockServer's array-of-{name,values} format. */
function extractHeaders(headers: unknown): HeaderDraft[] {
  if (!Array.isArray(headers)) return [];
  const result: HeaderDraft[] = [];
  for (const h of headers) {
    if (typeof h === 'object' && h !== null) {
      const entry = h as Record<string, unknown>;
      const name = entry['name'];
      const values = entry['values'];
      if (typeof name === 'string' && Array.isArray(values)) {
        result.push({ name, values: values.map(String) });
      }
    }
  }
  return result;
}

/** Extract query-string parameters from MockServer's format. */
function extractQueryParams(params: unknown): QueryParamDraft[] {
  if (!Array.isArray(params)) return [];
  const result: QueryParamDraft[] = [];
  for (const p of params) {
    if (typeof p === 'object' && p !== null) {
      const entry = p as Record<string, unknown>;
      const name = entry['name'];
      const values = entry['values'];
      if (typeof name === 'string' && Array.isArray(values)) {
        result.push({ name, values: values.map(String) });
      }
    }
  }
  return result;
}

/** Unwrap a MockServer body wrapper to a string. */
function bodyToString(body: unknown): string {
  if (typeof body === 'string') return body;
  if (typeof body !== 'object' || body === null) return '';
  const obj = body as Record<string, unknown>;
  if ('string' in obj && typeof obj['string'] === 'string') return obj['string'];
  if ('json' in obj) {
    const json = obj['json'];
    return typeof json === 'string' ? json : JSON.stringify(json, null, 2);
  }
  if ('xml' in obj && typeof obj['xml'] === 'string') return obj['xml'];
  if (obj['type'] === 'BINARY' && typeof obj['base64Bytes'] === 'string') {
    try {
      const bytes = atob(obj['base64Bytes'] as string);
      return new TextDecoder().decode(Uint8Array.from(bytes, (c) => c.charCodeAt(0)));
    } catch { return obj['base64Bytes'] as string; }
  }
  // Fall back to stringifying the body object
  return JSON.stringify(body, null, 2);
}

/**
 * Well-known hop-by-hop and infrastructure headers to exclude from captured expectations
 * (every `proxy-*` header too), matching the server's rule for recorded traffic.
 */
const EXCLUDED_HEADERS = new Set([
  'host', 'connection', 'content-length', 'transfer-encoding',
  'accept-encoding', 'keep-alive', 'te', 'trailer', 'upgrade',
  'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host',
]);

/** Filter out hop-by-hop and infrastructure headers. */
function filterSignificantHeaders(headers: HeaderDraft[]): HeaderDraft[] {
  return headers.filter((h) => {
    const name = h.name.toLowerCase();
    return !EXCLUDED_HEADERS.has(name) && !name.startsWith('proxy-');
  });
}

/**
 * Extract a generic HTTP expectation draft from a captured request/response
 * item (the raw `JsonListItem.value`).
 */
export function extractGenericExpectationFromCapture(
  itemValue: Record<string, unknown>,
  defaultPrecision: MatcherPrecision = 'moderate',
): GenericExpectationDraft {
  const httpRequest = (typeof itemValue['httpRequest'] === 'object' && itemValue['httpRequest'] !== null)
    ? itemValue['httpRequest'] as Record<string, unknown>
    : {};
  const httpResponse = (typeof itemValue['httpResponse'] === 'object' && itemValue['httpResponse'] !== null)
    ? itemValue['httpResponse'] as Record<string, unknown>
    : {};

  const method = typeof httpRequest['method'] === 'string' ? httpRequest['method'] : 'GET';
  const path = typeof httpRequest['path'] === 'string' ? httpRequest['path'] : '/';
  const queryStringParameters = extractQueryParams(httpRequest['queryStringParameters']);
  const headers = filterSignificantHeaders(extractHeaders(httpRequest['headers']));
  const body = bodyToString(httpRequest['body']);

  const responseStatusCode = typeof httpResponse['statusCode'] === 'number'
    ? httpResponse['statusCode']
    : 200;
  const responseHeaders = filterSignificantHeaders(extractHeaders(httpResponse['headers']));
  const responseBody = bodyToString(httpResponse['body']);

  return {
    kind: 'generic',
    method,
    path,
    queryStringParameters,
    headers,
    body,
    responseStatusCode,
    responseHeaders,
    responseBody,
    matcherPrecision: defaultPrecision,
  };
}

/**
 * Extract an editable expectation draft from a captured request/response pair.
 * Dispatches to LLM or generic extraction based on the parsed traffic kind.
 *
 * @param parsed     The parsed traffic from `parseTraffic()`.
 * @param path       The captured request path.
 * @param itemValue  The raw `JsonListItem.value` (needed for generic extraction).
 * @returns An `ExpectationDraft` (either LLM or generic) with defaults populated from the capture.
 */
export function extractExpectationFromCapture(
  parsed: ParsedTraffic,
  path: string,
  itemValue?: Record<string, unknown>,
): ExpectationDraft {
  if (isLlmTraffic(parsed)) {
    return extractLlmExpectationFromCapture(parsed, path);
  }
  // Generic HTTP traffic — need the raw item value
  return extractGenericExpectationFromCapture(
    itemValue ?? {},
    'moderate',
  );
}
