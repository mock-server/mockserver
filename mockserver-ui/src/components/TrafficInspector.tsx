import { useMemo, useState, useCallback, useRef, useLayoutEffect, memo } from 'react';
import { useTransientFlag } from '../hooks/useTransientFlag';
import type { KeyboardEvent as ReactKeyboardEvent, ReactNode } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Divider from '@mui/material/Divider';
import Button from '@mui/material/Button';
import Tooltip from '@mui/material/Tooltip';
import Checkbox from '@mui/material/Checkbox';
import ToggleButton from '@mui/material/ToggleButton';
import Popover from '@mui/material/Popover';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import SaveAltIcon from '@mui/icons-material/SaveAlt';
import ReplayIcon from '@mui/icons-material/Replay';
import RepeatIcon from '@mui/icons-material/Repeat';
import TerminalIcon from '@mui/icons-material/Terminal';
import HelpOutlinedIcon from '@mui/icons-material/HelpOutlined';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import CheckIcon from '@mui/icons-material/Check';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import ChecklistIcon from '@mui/icons-material/Checklist';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import AutoAwesomeMotionIcon from '@mui/icons-material/AutoAwesomeMotion';
import LibraryAddIcon from '@mui/icons-material/LibraryAdd';
import LibraryAddCheckIcon from '@mui/icons-material/LibraryAddCheck';
import CloseIcon from '@mui/icons-material/Close';
import DnsIcon from '@mui/icons-material/Dns';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useDashboardStore } from '../store';
import { useConnectionParams } from '../hooks/useConnectionParams';
import { useDragResize } from '../hooks/useDragResize';
import { useDebugMismatchContext } from '../hooks/DebugMismatchContext';
import { useGenerateStubContext } from '../hooks/GenerateStubContext';
import { useSetBreakpointContext } from '../hooks/SetBreakpointContext';
import { CreateFromMenu, buildLaunchpadActions, GraphqlOperationPill } from './LogEntry';
import JsonViewer from './JsonViewer';
import ErrorBoundary from './ErrorBoundary';
import ConfirmDialog from './ConfirmDialog';
import CaptureAsMockDialog from './CaptureAsMockDialog';
import DiffRequestsDialog from './DiffRequestsDialog';
import ExplainUnmatchedDialog from './ExplainUnmatchedDialog';
import PromoteRecordingsDialog from './PromoteRecordingsDialog';
import RepeatAdvancedDialog from './RepeatAdvancedDialog';
import OperatorSearchField from './OperatorSearchField';
import ProgressiveList from './ProgressiveList';
import { useHeldItems } from '../hooks/useHeldItems';

// Stable key accessor for useHeldItems — module scope, so its identity never changes.
const trafficKeyOf = (e: { item: { key: string } }) => e.item.key;
import CopyButton from './CopyButton';
import { clearLoggedRequest, requestDefinitionOf } from '../lib/traffic';
import { parseSearchTerm, matchesItemSearch } from '../lib/searchMatcher';
import { graphqlOperationOf, type GraphqlOperation } from '../lib/graphqlOperation';
import LlmUsageDetail from './LlmUsageDetail';
import {
  AnthropicConversationView,
  OpenAiConversationView,
  GeminiConversationView,
  OllamaConversationView,
  OpenAiResponsesConversationView,
  ScriptedTurnsPanel,
} from './ConversationView';
import type { ScriptedTurn } from './ConversationView';
import type { JsonListItem, TruncatedBody } from '../types';
import TruncatedBodyNotice from './TruncatedBodyNotice';
import { useLoadFullRow, useAutoLoadLlmRows, notifyFullBodyLoadFailed, forEachLimited } from '../hooks/useLoadFullRow';
import { hasTruncatedBodies } from '../lib/fullBody';
import { isCapturableTraffic } from '../lib/expectationFromCapture';
import type { CreateFromMenuAction } from './LogEntry';
import { replayRequests } from '../lib/replay';
import { humanizeError, type HumanError } from '../lib/errorMessage';
import HumanErrorAlert from './HumanErrorAlert';
import { monospaceFontFamily, transitions } from '../theme';
import { formatRowTime } from '../lib/logEntryTime';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import { useFollow } from '../hooks/useFollow';
import { useTailFollow } from '../hooks/useTailFollow';
import { combineTraffic } from '../lib/combineTraffic';
import { isNonHttpRequestRow } from '../lib/requestKind';
import {
  summarizeTraffic,
  extractBodyContent,
  getModelLabel,
  getTokenSummary,
  getTimingLabel,
  type TrafficSummary,
  type ParsedTraffic,
  type RequestTiming,
  type AnthropicParsed,
  type OpenAiParsed,
  type OpenAiResponsesParsed,
  type GeminiParsed,
  type OllamaParsed,
  type McpParsed,
  type SseEvent,
} from '../lib/llmTraffic';

// ---------------------------------------------------------------------------
// Helper: status color
// ---------------------------------------------------------------------------

function statusColor(code: number | null): 'success' | 'error' | 'warning' | 'default' {
  if (code === null) return 'default';
  if (code >= 200 && code < 300) return 'success';
  if (code >= 400) return 'error';
  if (code >= 300) return 'warning';
  return 'default';
}

function kindLabel(parsed: ParsedTraffic): string {
  switch (parsed.kind) {
    case 'anthropic': return parsed.platform === 'bedrock' ? 'Bedrock' : 'Anthropic';
    case 'openai': return 'OpenAI';
    case 'openai_responses': return 'OpenAI Resp';
    case 'gemini': return 'Gemini';
    case 'ollama': return 'Ollama';
    case 'mcp': return 'MCP';
    case 'generic': return 'HTTP';
  }
}

function kindColor(parsed: ParsedTraffic): 'primary' | 'secondary' | 'info' | 'default' {
  switch (parsed.kind) {
    case 'anthropic': return 'primary';
    case 'openai': return 'secondary';
    case 'openai_responses': return 'secondary';
    case 'gemini': return 'info';
    case 'ollama': return 'info';
    case 'mcp': return 'info';
    case 'generic': return 'default';
  }
}

// ---------------------------------------------------------------------------
// Secret-header masking (UI guard)
// ---------------------------------------------------------------------------

// Header names whose values carry credentials. Captured traffic is shown verbatim
// in the Raw JSON / Diff views (and flows into Replay), so mask these known secret
// headers before rendering. This is a UI guard only — server-side redaction is a
// separate concern. Matched case-insensitively.
const SECRET_HEADER_NAMES = new Set([
  'authorization',
  'proxy-authorization',
  'cookie',
  'set-cookie',
  'x-api-key',
  'api-key',
  'apikey',
  'anthropic-api-key',
  'openai-api-key',
  'x-goog-api-key',
  'x-api-token',
]);

/** Mask a bare token, keeping the last 4 chars for correlation when long enough. */
function maskToken(token: string): string {
  const trimmed = token.trim();
  if (trimmed.length <= 4) return '••••';
  return `••••${trimmed.slice(-4)}`;
}

/**
 * Mask a secret header value. Preserves a leading auth scheme (Bearer/Basic/…) so
 * the kind of credential stays visible, e.g. `Bearer sk-abc…1234` → `Bearer ••••1234`.
 * Non-scheme values (raw API keys, cookies) become `••••1234` / `••••`.
 */
export function maskSecretValue(raw: unknown): string {
  if (typeof raw !== 'string' || raw.length === 0) return typeof raw === 'string' ? raw : '••••';
  const schemeMatch = /^(\S+)\s+(.+)$/.exec(raw);
  if (schemeMatch && /^(bearer|basic|digest|token)$/i.test(schemeMatch[1]!)) {
    return `${schemeMatch[1]} ${maskToken(schemeMatch[2]!)}`;
  }
  return maskToken(raw);
}

/**
 * Return a copy of MockServer-format headers with any secret header values masked.
 * Handles both the array form `[{name, values:[…]}]` and the object form
 * `{name: [values]}`. Returns the original reference when nothing was masked so
 * callers can cheaply detect "no change".
 */
function maskHeaders(headers: unknown): unknown {
  if (Array.isArray(headers)) {
    let changed = false;
    const out = headers.map((h) => {
      if (h && typeof h === 'object' && !Array.isArray(h)) {
        const entry = h as Record<string, unknown>;
        const name = entry['name'];
        if (typeof name === 'string' && SECRET_HEADER_NAMES.has(name.toLowerCase())) {
          const values = entry['values'];
          if (Array.isArray(values)) {
            changed = true;
            return { ...entry, values: values.map((v) => maskSecretValue(String(v))) };
          }
        }
      }
      return h;
    });
    return changed ? out : headers;
  }
  if (headers && typeof headers === 'object') {
    const map = headers as Record<string, unknown>;
    let changed = false;
    const out: Record<string, unknown> = {};
    for (const key of Object.keys(map)) {
      const v = map[key];
      if (SECRET_HEADER_NAMES.has(key.toLowerCase())) {
        changed = true;
        if (Array.isArray(v)) out[key] = v.map((x) => maskSecretValue(String(x)));
        else if (typeof v === 'string') out[key] = maskSecretValue(v);
        else out[key] = v;
      } else {
        out[key] = v;
      }
    }
    return changed ? out : headers;
  }
  return headers;
}

/**
 * Return a shallow copy of a captured request value with secret headers masked on
 * its httpRequest / httpResponse / forwarded-request sections. Only the touched
 * paths are cloned; everything else shares references. Returns the original
 * reference when nothing was masked.
 */
export function maskSecretsInValue(value: Record<string, unknown>): Record<string, unknown> {
  let changed = false;
  const out: Record<string, unknown> = { ...value };
  for (const section of ['httpRequest', 'httpResponse', 'httpOverrideForwardedRequest'] as const) {
    const sec = value[section];
    if (sec && typeof sec === 'object' && !Array.isArray(sec)) {
      const secObj = sec as Record<string, unknown>;
      if ('headers' in secObj) {
        const maskedHeaders = maskHeaders(secObj['headers']);
        if (maskedHeaders !== secObj['headers']) {
          out[section] = { ...secObj, headers: maskedHeaders };
          changed = true;
        }
      }
    }
  }
  return changed ? out : value;
}

/**
 * Extract the decoded (non-stream) response body text from a captured value, or
 * undefined when the body parsed cleanly as JSON / is streamed / is absent. Used
 * to flag a truncated-or-malformed non-stream response in the conversation view.
 */
function nonStreamResponseBodyText(
  value: Record<string, unknown>,
  parsed: ParsedTraffic,
): string | undefined {
  if ('streamed' in parsed && parsed.streamed) return undefined;
  if ('sseEvents' in parsed && parsed.sseEvents) return undefined;
  const httpResponse = value['httpResponse'];
  if (!httpResponse || typeof httpResponse !== 'object') return undefined;
  const body = (httpResponse as Record<string, unknown>)['body'];
  const content = extractBodyContent(body);
  // A string here means MockServer stored a non-JSON (or unparsed) body; an object
  // means the body already parsed cleanly, so no truncation warning is warranted.
  return typeof content === 'string' ? content : undefined;
}

/**
 * Classify an MCP JSON-RPC exchange as an error. An error is present when the
 * JSON-RPC `error` object exists, or the HTTP status is non-2xx. Exposes the
 * numeric JSON-RPC error code and message when available.
 */
export function mcpErrorInfo(
  parsed: McpParsed,
  statusCode: number | null,
): { isError: boolean; code: number | null; message: string | null } {
  const errObj =
    parsed.error && typeof parsed.error === 'object' && !Array.isArray(parsed.error)
      ? (parsed.error as Record<string, unknown>)
      : null;
  const code = errObj && typeof errObj['code'] === 'number' ? (errObj['code'] as number) : null;
  const message = errObj && typeof errObj['message'] === 'string' ? (errObj['message'] as string) : null;
  const isError =
    parsed.error != null || (statusCode != null && (statusCode < 200 || statusCode >= 300));
  return { isError, code, message };
}

// ---------------------------------------------------------------------------
// Scripted turns extraction from active expectations
// ---------------------------------------------------------------------------

import { scenarioStateSortKey } from '../lib/scenarioState';

/**
 * Gather scripted turns from expectations sharing the same scenarioName.
 * Each expectation with httpLlmResponse + conversationPredicates within the
 * same scenario constitutes one turn.
 */
function gatherScriptedTurns(expectations: JsonListItem[]): ScriptedTurn[] {
  // Group expectations by scenarioName. Only LLM expectations are considered;
  // a single shared scenarioName forms one conversation.
  const scenarioGroups = new Map<string, JsonListItem[]>();
  for (const exp of expectations) {
    const llm = exp.value['httpLlmResponse'] as Record<string, unknown> | undefined;
    if (!llm) continue;
    // scenarioName is a top-level Expectation field (ExpectationDTO), not nested inside
    // httpLlmResponse — read it from the top level (consistent with sessionGrouping).
    const scenarioName = exp.value['scenarioName'] as string | undefined;
    if (!scenarioName) continue;
    if (!scenarioGroups.has(scenarioName)) {
      scenarioGroups.set(scenarioName, []);
    }
    scenarioGroups.get(scenarioName)!.push(exp);
  }

  // Collect turns from EVERY eligible scenario group. The previous version
  // returned the first matching group and silently dropped the rest, which
  // produced incorrect output when multiple conversations were loaded
  // simultaneously. We now flatten across groups; the ScriptedTurnsPanel
  // displays a separator between conversations via the scenarioName field.
  const allTurns: ScriptedTurn[] = [];
  for (const [scenarioName, group] of scenarioGroups) {
    const hasPredicates = group.some((e) => {
      const llm = e.value['httpLlmResponse'] as Record<string, unknown> | undefined;
      return llm && 'conversationPredicates' in llm;
    });
    if (!hasPredicates && group.length < 2) continue;

    // Sort by scenario state transition order. scenarioState is a top-level Expectation field.
    const sorted = [...group].sort((a, b) => {
      const aState = (a.value['scenarioState'] as string | undefined) ?? 'Started';
      const bState = (b.value['scenarioState'] as string | undefined) ?? 'Started';
      return scenarioStateSortKey(aState) - scenarioStateSortKey(bState);
    });

    const turns: ScriptedTurn[] = sorted.map((exp, i) => {
      const llm = exp.value['httpLlmResponse'] as Record<string, unknown>;
      const predicates = (llm['conversationPredicates'] as Record<string, unknown>) ?? {};
      const completion = (llm['completion'] as Record<string, unknown>) ?? {};
      const toolCalls = (completion['toolCalls'] as Array<{ name: string; arguments?: string }>) ?? [];

      return {
        turnIndex: (predicates['turnIndex'] as number | undefined) ?? i,
        predicates: Object.fromEntries(
          Object.entries(predicates).filter(([, v]) => v != null),
        ),
        response: {
          text: (completion['text'] as string | undefined),
          toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
          stopReason: (completion['stopReason'] as string | undefined)
            ?? (llm['stopReason'] as string | undefined),
          // streaming lives inside completion (Completion.streaming), not at the llm top level.
          streaming: (completion['streaming'] as boolean | undefined),
        },
        // scenarioState / newScenarioState are top-level Expectation fields.
        scenarioState: (exp.value['scenarioState'] as string | undefined) ?? 'Started',
        newScenarioState: (exp.value['newScenarioState'] as string | undefined) ?? '__done',
        scenarioName,
      };
    });

    allTurns.push(...turns);
  }
  return allTurns;
}

// ---------------------------------------------------------------------------
// Search match helper
// ---------------------------------------------------------------------------

// Per-item parse cache. `summarizeTraffic` fully parses + reassembles SSE streams
// and base64-decodes bodies; the store replaces the traffic arrays on every
// WebSocket push but preserves each unchanged item's `value` reference
// (reconcileByKey), so caching on that reference avoids re-parsing every captured
// request on every push and on every search keystroke. The WeakMap lets entries
// be collected once the item object is gone.
const summaryCache = new WeakMap<Record<string, unknown>, TrafficSummary>();

function cachedSummarize(value: Record<string, unknown>): TrafficSummary {
  const hit = summaryCache.get(value);
  if (hit) return hit;
  const summary = summarizeTraffic(value);
  summaryCache.set(value, summary);
  return summary;
}

// Per-item lowercased full-text search index. The fallback branch of
// `matchesSearch` previously re-ran `JSON.stringify(item.value).toLowerCase()`
// for every non-field-matching row on every search keystroke. Caching the
// stringified+lowercased text on the item's `value` reference (preserved across
// WebSocket pushes by reconcileByKey, like `summaryCache`) means each item is
// stringified at most once regardless of how many keystrokes the user types.
const searchTextCache = new WeakMap<Record<string, unknown>, string>();

function cachedSearchText(value: Record<string, unknown>): string {
  const hit = searchTextCache.get(value);
  if (hit !== undefined) return hit;
  // Index the raw JSON AND the decoded request/response body text. A BINARY/base64
  // body is stored (and JSON.stringify'd) as base64, so without the decoded text a
  // search for a prompt/response word would never match. extractBodyContent
  // base64-decodes BINARY bodies and unwraps STRING/JSON bodies.
  const parts = [JSON.stringify(value)];
  for (const section of ['httpRequest', 'httpResponse'] as const) {
    const sec = value[section];
    if (sec && typeof sec === 'object') {
      const content = extractBodyContent((sec as Record<string, unknown>)['body']);
      if (typeof content === 'string') {
        parts.push(content);
      } else if (content && typeof content === 'object') {
        try { parts.push(JSON.stringify(content)); } catch { /* ignore unserialisable */ }
      }
    }
  }
  const text = parts.join(' ').toLowerCase();
  searchTextCache.set(value, text);
  return text;
}

function matchesSearch(item: JsonListItem, summary: TrafficSummary, term: string): boolean {
  // Honour the shared search operators (status:/method:/path:/`/regex/`) via
  // lib/searchMatcher.ts so Traffic behaves the same as the dashboard panels.
  const parsed = parseSearchTerm(term);

  // Every field operator must match (AND semantics). Delegate to the shared
  // matcher with an operator-only query so the comparison logic stays in one place.
  if (parsed.operators.length > 0) {
    const operatorQuery = parsed.operators
      .map((op) => `${op.field}:${op.comparator ?? ''}${op.expr}`)
      .join(' ');
    if (!matchesItemSearch(item.value, operatorQuery)) return false;
  }

  // No free text left → the operators alone decide the match.
  if (parsed.text.length === 0) return true;

  // Free text: a `/regex/` term and the shared searchable fields are handled by
  // the shared matcher; fall back to the richer local index (summary-derived
  // fields plus decoded request/response body text, incl. base64 bodies).
  if (matchesItemSearch(item.value, parsed.text)) return true;

  const lower = parsed.text.toLowerCase();
  const parts = [
    summary.host,
    summary.method,
    summary.path,
    summary.statusCode?.toString(),
    getModelLabel(summary.parsed),
    kindLabel(summary.parsed),
  ].filter(Boolean);
  if (parts.some((p) => p!.toLowerCase().includes(lower))) return true;
  return cachedSearchText(item.value).includes(lower);
}

// ---------------------------------------------------------------------------
// Host focus — group captured traffic by upstream host and pin one into the
// search as a `host:` operator (Charles / Proxyman style noise control).
//
// The pin is expressed ENTIRELY as a `host:` token in the existing traffic
// search term rather than as a separate piece of state: the search term is
// already store-backed and persisted to localStorage (store/index.ts
// `setTrafficSearch`), so a pinned host survives a view switch and a reload for
// free, stays visible/editable in the search box, and composes with the other
// operators instead of fighting them.
// ---------------------------------------------------------------------------

/** One distinct upstream host in the captured traffic, with its request count. */
interface HostCount {
  host: string;
  count: number;
}

/**
 * Distinct hosts across the captured traffic, busiest first then alphabetical.
 *
 * Rows with no `Host` header are omitted — there is nothing to pin for them, and
 * a synthetic "(no host)" bucket would not be expressible as a `host:` operator.
 * Hosts are read from the already-computed row summary (`TrafficSummary.host`),
 * which reads the first `Host` header case-insensitively — the same value the
 * shared `host:` filter operator resolves — so a pinned host always selects
 * exactly the rows counted here.
 *
 * That invariant only holds for hosts the search DSL can round-trip, so values
 * outside the RFC 3986 host/port character set are omitted rather than offered
 * as a broken pin. A client may send anything in a `Host` header: a space would
 * tokenise into `host:foo` plus stray free text (selecting nothing, and
 * appending another stray token on every further click), and a `*` would pin
 * `host:*`, whose glob matches every row while the facet claims one.
 */
const PINNABLE_HOST = /^[A-Za-z0-9._:[\]-]+$/;

function hostCounts(rows: readonly { summary: TrafficSummary }[]): HostCount[] {
  const counts = new Map<string, number>();
  for (const { summary } of rows) {
    if (!summary.host || !PINNABLE_HOST.test(summary.host)) continue;
    counts.set(summary.host, (counts.get(summary.host) ?? 0) + 1);
  }
  return [...counts]
    .map(([host, count]) => ({ host, count }))
    .sort((a, b) => b.count - a.count || a.host.localeCompare(b.host));
}

/** The host currently pinned by a `host:` operator in a search term, or null. */
function pinnedHostOf(term: string): string | null {
  const op = parseSearchTerm(term).operators.find((o) => o.field === 'host');
  return op && op.expr.length > 0 ? op.expr : null;
}

/**
 * Replace — or, with `host === null`, remove — the `host:` operator in a search
 * term, leaving every other token (free text and other operators) untouched, so
 * pinning a host narrows an existing search rather than replacing it.
 */
function pinHostInSearch(term: string, host: string | null): string {
  const tokens = term
    .trim()
    .split(/\s+/)
    .filter((t) => t.length > 0 && !/^host:/i.test(t));
  if (host) tokens.push(`host:${host}`);
  return tokens.join(' ');
}

// ---------------------------------------------------------------------------
// Unmatched detection + "copy as curl"
// ---------------------------------------------------------------------------

/**
 * True when a captured response indicates the request matched no expectation.
 * MockServer answers an unmatched request with `404 Not Found`, so a recorded
 * (non-proxied) request carrying that response reached no mock. Proxied requests
 * are excluded by the caller since their 404 comes from the real upstream.
 */
export function isUnmatchedResponse(value: Record<string, unknown>): boolean {
  const res = value['httpResponse'];
  if (!res || typeof res !== 'object' || Array.isArray(res)) return false;
  const r = res as Record<string, unknown>;
  if (r['statusCode'] !== 404) return false;
  const rp = r['reasonPhrase'];
  return typeof rp === 'string' && rp.trim().toLowerCase() === 'not found';
}

/** The keyboard-operable Traffic rows mounted in the list's scroll region, in list order. */
function rowElements(region: Element | null): HTMLElement[] {
  return Array.from(region?.querySelectorAll<HTMLElement>('[data-traffic-row-key][role="button"]') ?? []);
}

/** Single-quote a string for a POSIX shell, escaping embedded single quotes. */
function shellQuote(s: string): string {
  return `'${s.replace(/'/g, `'\\''`)}'`;
}

/** Flatten MockServer-format headers ([{name,values}] or {name:[values]}) into [name, value] pairs. */
function headerPairs(headers: unknown): Array<[string, string]> {
  const out: Array<[string, string]> = [];
  if (Array.isArray(headers)) {
    for (const h of headers) {
      if (h && typeof h === 'object' && !Array.isArray(h)) {
        const name = (h as Record<string, unknown>)['name'];
        const values = (h as Record<string, unknown>)['values'];
        if (typeof name === 'string' && Array.isArray(values)) {
          for (const v of values) out.push([name, String(v)]);
        }
      }
    }
  } else if (headers && typeof headers === 'object') {
    for (const [k, v] of Object.entries(headers as Record<string, unknown>)) {
      if (Array.isArray(v)) for (const x of v) out.push([k, String(x)]);
      else if (typeof v === 'string') out.push([k, v]);
    }
  }
  return out;
}

/** Read the Host header value from either header shape. */
function hostFromHeaders(headers: unknown): string {
  for (const [name, value] of headerPairs(headers)) {
    if (name.toLowerCase() === 'host') return value;
  }
  return '';
}

/** Build a `?a=1&b=2` query string from MockServer-format query parameters. */
function queryStringForCurl(params: unknown): string {
  const pairs: string[] = [];
  if (Array.isArray(params)) {
    for (const p of params) {
      if (p && typeof p === 'object') {
        const name = (p as Record<string, unknown>)['name'];
        const values = (p as Record<string, unknown>)['values'];
        if (typeof name === 'string' && Array.isArray(values)) {
          for (const v of values) pairs.push(`${encodeURIComponent(name)}=${encodeURIComponent(String(v))}`);
        }
      }
    }
  } else if (params && typeof params === 'object') {
    for (const [k, v] of Object.entries(params as Record<string, unknown>)) {
      if (Array.isArray(v)) for (const x of v) pairs.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(x))}`);
      else if (typeof v === 'string') pairs.push(`${encodeURIComponent(k)}=${encodeURIComponent(v)}`);
    }
  }
  return pairs.length > 0 ? `?${pairs.join('&')}` : '';
}

/**
 * Build a `curl` command that re-issues the captured request against its original
 * upstream target, so a user can reproduce it from a terminal. Secret headers are
 * masked (consistent with the Raw JSON / Diff views) — substitute a real
 * credential before running. Returns an empty string when there is no request.
 */
export function buildRequestCurl(value: Record<string, unknown>, summary: TrafficSummary): string {
  const masked = maskSecretsInValue(value);
  const req = masked['httpRequest'];
  if (!req || typeof req !== 'object' || Array.isArray(req)) return '';
  const r = req as Record<string, unknown>;

  const method = typeof r['method'] === 'string' && r['method'] ? (r['method'] as string) : 'GET';
  const scheme = r['secure'] === true ? 'https' : 'http';
  const host = hostFromHeaders(r['headers']) || summary.host || 'localhost';
  const path = typeof r['path'] === 'string' ? (r['path'] as string) : '/';
  const query = path.includes('?') ? '' : queryStringForCurl(r['queryStringParameters']);
  const url = `${scheme}://${host}${path}${query}`;

  const lines = [`curl -X ${shellQuote(method)} ${shellQuote(url)}`];
  for (const [name, val] of headerPairs(r['headers'])) {
    const lower = name.toLowerCase();
    // Host is carried in the URL; Content-Length is recomputed by curl.
    if (lower === 'host' || lower === 'content-length') continue;
    lines.push(`  -H ${shellQuote(`${name}: ${val}`)}`);
  }

  const body = extractBodyContent(r['body']);
  const bodyStr = typeof body === 'string'
    ? body
    : body != null
      ? (() => { try { return JSON.stringify(body); } catch { return ''; } })()
      : '';
  if (bodyStr) lines.push(`  --data-raw ${shellQuote(bodyStr)}`);

  return lines.join(' \\\n');
}

// ---------------------------------------------------------------------------
// Master list row
// ---------------------------------------------------------------------------

interface TrafficRowProps {
  summary: TrafficSummary;
  /**
   * Parsed GraphQL operation for this request, or null when it is not GraphQL.
   * Supplied by the parent (from the memoised `graphqlOperationOf`, whose result
   * is reference-stable per request) so this stays a memo-friendly prop.
   */
  graphql?: GraphqlOperation | null;
  /** Stable identity of the request this row renders; passed back to the handlers. */
  itemKey: string;
  /**
   * When this request was recorded (server log timestamp). Shown as the row's
   * leading column in place of an ordinal: this list is a capped live window, so
   * a position within it renumbers on every push and refers to nothing, whereas
   * the timestamp is stable and lines up with the Log Messages panel.
   */
  timestamp?: string;
  /**
   * Fallback ordinal, used only when `timestamp` is absent. NOT part of the
   * row's accessible name — a value that renumbers on every push makes an
   * unstable label — so identity-based aria labels are derived from the summary
   * instead.
   */
  index: number;
  selected: boolean;
  /** Stable handler — receives this row's `itemKey`. */
  onSelect: (key: string) => void;
  /** When set, a comparison checkbox is rendered and reflects this checked state. */
  compareMode?: boolean;
  compareChecked?: boolean;
  compareDisabled?: boolean;
  /** Stable handler — receives this row's `itemKey`. */
  onCompareToggle?: (key: string) => void;
  /** When set, a bulk-select checkbox is rendered (no two-row cap, unlike compare). */
  selectMode?: boolean;
  selectChecked?: boolean;
  /** Stable handler — receives this row's `itemKey`. */
  onSelectToggle?: (key: string) => void;
  /** The one row the Tab key reaches (roving tab index); the arrow keys move between rows. */
  tabbable?: boolean;
  /** Stable handler — receives this row's element and the direction an arrow key moves focus. */
  onMoveFocus?: (row: HTMLElement, delta: 1 | -1) => void;
  /** Stable handler — receives this row's `itemKey` when the row takes focus. */
  onRowFocus?: (key: string) => void;
}

// `TrafficRow` is wrapped in `React.memo` (see the `export`/assignment below) so
// that re-rendering the parent (e.g. a search keystroke, a selection change, or a
// WebSocket-driven list refresh) only re-renders the rows whose props actually
// changed. For that to hold, the parent passes STABLE handlers that take the
// row's key (`onSelect`/`onCompareToggle`) rather than fresh per-row arrow
// closures; the per-row DOM callbacks are re-derived here with `useCallback`
// keyed on `itemKey`, mirroring the `entryKey`/`onToggleExpand` pattern used by
// the memoized `LogEntry`.
function TrafficRowImpl({
  summary,
  graphql,
  itemKey,
  timestamp,
  index,
  selected,
  onSelect,
  compareMode,
  compareChecked,
  compareDisabled,
  onCompareToggle,
  selectMode,
  selectChecked,
  onSelectToggle,
  tabbable = false,
  onMoveFocus,
  onRowFocus,
}: TrafficRowProps) {
  const model = getModelLabel(summary.parsed);
  const tokens = getTokenSummary(summary.parsed);
  const timingLabel = getTimingLabel(summary.timing);

  const handleClick = useCallback(() => onSelect(itemKey), [onSelect, itemKey]);
  const handleCompareToggle = useCallback(
    () => onCompareToggle?.(itemKey),
    [onCompareToggle, itemKey],
  );
  const handleSelectToggle = useCallback(
    () => onSelectToggle?.(itemKey),
    [onSelectToggle, itemKey],
  );
  // In compare and select mode the row's checkbox is the control, so the row
  // itself is not a button (a button may not contain another control).
  const keyboardRow = !compareMode && !selectMode;
  const handleKeyDown = useCallback(
    (e: ReactKeyboardEvent<HTMLDivElement>) => {
      if (e.target !== e.currentTarget) return;
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        onSelect(itemKey);
      } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        onMoveFocus?.(e.currentTarget, e.key === 'ArrowDown' ? 1 : -1);
      }
    },
    [onSelect, onMoveFocus, itemKey],
  );
  const handleFocus = useCallback(() => onRowFocus?.(itemKey), [onRowFocus, itemKey]);

  // Accessible name keyed on request IDENTITY (method + path), not the row
  // ordinal: the list is a capped live window that renumbers on every push, so an
  // ordinal makes an unstable label that names a different request moment to
  // moment. Falls back to the stable item key when the summary has neither.
  const identityLabel =
    [summary.method, summary.path].filter(Boolean).join(' ').trim() || itemKey;

  return (
    <Box
      onClick={handleClick}
      role={keyboardRow ? 'button' : undefined}
      tabIndex={keyboardRow ? (tabbable ? 0 : -1) : undefined}
      aria-pressed={keyboardRow ? selected : undefined}
      onKeyDown={keyboardRow ? handleKeyDown : undefined}
      onFocus={keyboardRow ? handleFocus : undefined}
      data-traffic-row-key={itemKey}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.75,
        px: 1,
        py: 0.5,
        cursor: 'pointer',
        '&:focus-visible': { outline: 2, outlineColor: 'primary.main', outlineOffset: -2 },
        bgcolor: selected ? 'action.selected' : 'transparent',
        transition: transitions.forProps(['background-color']),
        '&:hover': { bgcolor: selected ? 'action.selected' : 'action.hover' },
        borderBottom: 1,
        borderColor: 'divider',
        minHeight: 36,
        // One line per row: the path truncates (its tooltip has the whole of it)
        // and the chips keep their place instead of wrapping under the time.
        flexWrap: 'nowrap',
        overflow: 'hidden',
      }}
    >
      {compareMode && (
        <Checkbox
          size="small"
          checked={!!compareChecked}
          disabled={!compareChecked && compareDisabled}
          onClick={(e) => e.stopPropagation()}
          onChange={handleCompareToggle}
          slotProps={{ input: { 'aria-label': `Select ${identityLabel} to compare` } }}
          sx={{ p: 0.25, flexShrink: 0 }}
        />
      )}
      {selectMode && (
        <Checkbox
          size="small"
          checked={!!selectChecked}
          onClick={(e) => e.stopPropagation()}
          onChange={handleSelectToggle}
          slotProps={{ input: { 'aria-label': `Select ${identityLabel}` } }}
          sx={{ p: 0.25, flexShrink: 0 }}
        />
      )}
      <Typography
        variant="caption"
        title={timestamp ?? undefined}
        sx={{ fontFamily: monospaceFontFamily, color: 'text.secondary', minWidth: 24, flexShrink: 0, whiteSpace: 'nowrap' }}
      >
        {/* The time it happened, not a position in the list. This section is a
            capped live window, so an ordinal renumbers on every push and refers
            to nothing; the timestamp is stable and lines up with the Log Messages
            panel. Falls back to the ordinal only when no timestamp is present. */}
        {timestamp ? formatRowTime(timestamp) : index}
      </Typography>
      <Chip
        label={kindLabel(summary.parsed)}
        size="small"
        color={kindColor(summary.parsed)}
        variant="outlined"
        sx={{ height: 18, fontSize: '0.6rem', flexShrink: 0, '& .MuiChip-label': { px: 0.5 } }}
      />
      <Typography
        variant="caption"
        sx={{
          fontFamily: monospaceFontFamily,
          fontWeight: 600,
          color: 'primary.main',
          flexShrink: 0,
        }}
      >
        {summary.method ?? '?'}
      </Typography>
      <Tooltip title={`${summary.host ?? ''}${summary.path ?? ''}`} disableInteractive>
        <Typography
          variant="caption"
          noWrap
          data-testid="traffic-row-path"
          sx={{
            fontFamily: monospaceFontFamily,
            flex: '1 1 0',
            minWidth: 40,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {summary.host ? `${summary.host}` : ''}{summary.path ?? ''}
        </Typography>
      </Tooltip>
      {graphql && <GraphqlOperationPill operation={graphql} />}
      {summary.statusCode !== null && (
        <Chip
          label={summary.statusCode}
          size="small"
          color={statusColor(summary.statusCode)}
          sx={{ height: 18, fontSize: '0.6rem', flexShrink: 0, '& .MuiChip-label': { px: 0.5 } }}
        />
      )}
      {model && (
        <Chip
          label={model}
          size="small"
          variant="outlined"
          title={model}
          sx={{ height: 18, fontSize: '0.6rem', minWidth: 0, '& .MuiChip-label': { px: 0.5 } }}
        />
      )}
      {tokens && (
        // Least important on a narrow row, so it gives up its space first.
        <Typography variant="caption" color="text.secondary" noWrap sx={{ fontSize: '0.6rem', flexShrink: 1000, minWidth: 0 }}>
          {tokens}
        </Typography>
      )}
      {timingLabel && (
        <Chip
          label={timingLabel}
          size="small"
          variant="outlined"
          color="info"
          sx={{ height: 18, fontSize: '0.6rem', flexShrink: 0, '& .MuiChip-label': { px: 0.5 } }}
        />
      )}
    </Box>
  );
}

const TrafficRow = memo(TrafficRowImpl);

// ---------------------------------------------------------------------------
// Host tree — collapsible host facet docked at the top of the master list
// ---------------------------------------------------------------------------

interface HostTreeProps {
  hosts: readonly HostCount[];
  /** Host currently pinned into the search term, or null when none is. */
  pinnedHost: string | null;
  /** Pin a host (or unpin, with null). */
  onPin: (host: string | null) => void;
}

/**
 * Collapsible host facet for the traffic list: every distinct upstream host with
 * its request count, clicking one pins `host:<value>` into the search (clicking
 * the pinned host again unpins it).
 *
 * VISIBILITY: the caller renders this ONLY when more than one distinct host
 * appears in the captured traffic. In mock-only mode every request targets the
 * same MockServer host, so a single-host facet would partition nothing while
 * permanently costing a strip of the (already narrow, resizable) master list —
 * it earns its space exactly when there is more than one thing to choose
 * between. It therefore appears by itself as soon as proxied traffic spans
 * several upstreams, and disappears again when it stops being useful.
 *
 * It is docked as a collapsible strip at the top of the master list rather than
 * as a side column because the master/detail split is already user-resized down
 * to ~260px; a second vertical column inside it would leave too little for the
 * rows themselves.
 */
function HostTree({ hosts, pinnedHost, onPin }: HostTreeProps) {
  const [expanded, setExpanded] = useState(true);

  return (
    <Box sx={{ borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}>
      <Box
        component="button"
        type="button"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        aria-label={expanded ? 'Collapse hosts' : 'Expand hosts'}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          width: '100%',
          px: 1,
          py: 0.25,
          border: 0,
          bgcolor: 'transparent',
          color: 'inherit',
          font: 'inherit',
          textAlign: 'left',
          cursor: 'pointer',
          '&:hover': { bgcolor: 'action.hover' },
        }}
      >
        {expanded
          ? <ExpandMoreIcon sx={{ fontSize: '0.9rem' }} />
          : <ChevronRightIcon sx={{ fontSize: '0.9rem' }} />}
        <DnsIcon sx={{ fontSize: '0.85rem', color: 'text.secondary' }} />
        <Typography variant="caption" sx={{ fontWeight: 600 }}>
          Hosts
        </Typography>
        {pinnedHost && (
          <Typography
            component="span"
            variant="caption"
            sx={{ fontFamily: monospaceFontFamily, color: 'primary.main', ml: 0.5, overflow: 'hidden', textOverflow: 'ellipsis' }}
          >
            pinned: {pinnedHost}
          </Typography>
        )}
      </Box>
      <Collapse in={expanded} unmountOnExit>
        <Box sx={{ maxHeight: 140, overflowY: 'auto', pb: 0.5 }}>
          {hosts.map(({ host }) => {
            const active = host === pinnedHost;
            return (
              <Box
                key={host}
                component="button"
                type="button"
                aria-pressed={active}
                // No request count here either. It would be the count within the
                // at-most-100-row update window, not the host's traffic, so it pins at
                // the cap under load -- and a wrong number read out to a screen reader is
                // no better for being invisible on screen.
                aria-label={`Filter traffic by host ${host}`}
                onClick={() => onPin(active ? null : host)}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  width: '100%',
                  px: 1,
                  py: 0.25,
                  border: 0,
                  bgcolor: active ? 'action.selected' : 'transparent',
                  color: 'inherit',
                  font: 'inherit',
                  textAlign: 'left',
                  cursor: 'pointer',
                  '&:hover': { bgcolor: active ? 'action.selected' : 'action.hover' },
                }}
              >
                <Typography
                  variant="caption"
                  noWrap
                  sx={{
                    fontFamily: monospaceFontFamily,
                    fontWeight: active ? 600 : 400,
                    flex: 1,
                    minWidth: 0,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {host}
                </Typography>

              </Box>
            );
          })}
        </Box>
      </Collapse>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Messages panel: Anthropic
// ---------------------------------------------------------------------------

function AnthropicMessagesPanel({ parsed }: { parsed: AnthropicParsed }) {
  return (
    <Box>
      {parsed.system != null && (
        <Box sx={{ mb: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>System</Typography>
          <Box sx={{ mt: 0.5 }}>
            {typeof parsed.system === 'string' ? (
              <Typography variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: '0.75rem', whiteSpace: 'pre-wrap' }}>
                {parsed.system}
              </Typography>
            ) : (
              <JsonViewer data={parsed.system as Record<string, unknown>} collapsed={2} />
            )}
          </Box>
        </Box>
      )}
      {parsed.messages.length > 0 && (
        <Box>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Messages ({parsed.messages.length})</Typography>
          {parsed.messages.map((msg, i) => (
            <Box key={i} sx={{ mt: 0.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
              <JsonViewer data={msg as Record<string, unknown>} collapsed={1} />
            </Box>
          ))}
        </Box>
      )}
      {parsed.tools && parsed.tools.length > 0 && (
        <Box sx={{ mt: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Tools ({parsed.tools.length})</Typography>
          <JsonViewer data={parsed.tools as unknown[]} collapsed={1} />
        </Box>
      )}
      <Box sx={{ mt: 1, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        {parsed.model && <Chip label={`Model: ${parsed.model}`} size="small" variant="outlined" />}
        {parsed.maxTokens !== null && <Chip label={`Max tokens: ${parsed.maxTokens}`} size="small" variant="outlined" />}
        {parsed.stream && <Chip label="Streaming" size="small" color="info" variant="outlined" />}
      </Box>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Messages panel: OpenAI
// ---------------------------------------------------------------------------

function OpenAiMessagesPanel({ parsed }: { parsed: OpenAiParsed }) {
  return (
    <Box>
      {parsed.messages.length > 0 && (
        <Box>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Messages ({parsed.messages.length})</Typography>
          {parsed.messages.map((msg, i) => (
            <Box key={i} sx={{ mt: 0.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
              <JsonViewer data={msg as Record<string, unknown>} collapsed={1} />
            </Box>
          ))}
        </Box>
      )}
      {parsed.tools && parsed.tools.length > 0 && (
        <Box sx={{ mt: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Tools ({parsed.tools.length})</Typography>
          <JsonViewer data={parsed.tools as unknown[]} collapsed={1} />
        </Box>
      )}
      <Box sx={{ mt: 1, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        {parsed.model && <Chip label={`Model: ${parsed.model}`} size="small" variant="outlined" />}
        {parsed.stream && <Chip label="Streaming" size="small" color="info" variant="outlined" />}
      </Box>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// MCP panel (content only, no wrapping tabs)
// ---------------------------------------------------------------------------

function McpDetailPanel({ parsed, statusCode }: { parsed: McpParsed; statusCode: number | null }) {
  const { isError, code, message } = mcpErrorInfo(parsed, statusCode);
  return (
    <Box>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 1 }}>
        <Chip
          label="MCP JSON-RPC"
          size="small"
          color={isError ? 'error' : 'info'}
          variant={isError ? 'filled' : 'outlined'}
        />
        {parsed.method && <Chip label={`Method: ${parsed.method}`} size="small" variant="outlined" />}
        {parsed.id != null && <Chip label={`ID: ${String(parsed.id)}`} size="small" variant="outlined" />}
        {isError && (
          <Chip
            label={code != null ? `Error ${code}` : 'Error'}
            size="small"
            color="error"
            variant="outlined"
          />
        )}
      </Box>
      {parsed.params != null && (
        <Box sx={{ mb: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Params</Typography>
          <JsonViewer data={parsed.params as Record<string, unknown>} collapsed={2} />
        </Box>
      )}
      {parsed.result != null && (
        <Box sx={{ mb: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Result</Typography>
          <JsonViewer data={parsed.result as Record<string, unknown>} collapsed={2} />
        </Box>
      )}
      {parsed.error != null && (
        <Box sx={{ mb: 1 }}>
          <Typography variant="caption" color="error" sx={{ fontWeight: 600 }}>
            Error{code != null ? ` (code ${code})` : ''}{message ? `: ${message}` : ''}
          </Typography>
          <JsonViewer data={parsed.error as Record<string, unknown>} collapsed={2} />
        </Box>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Messages panel: OpenAI Responses API
// ---------------------------------------------------------------------------

function OpenAiResponsesMessagesPanel({ parsed }: { parsed: OpenAiResponsesParsed }) {
  return (
    <Box>
      {parsed.input.length > 0 && (
        <Box>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Input ({parsed.input.length})</Typography>
          {parsed.input.map((item, i) => (
            <Box key={i} sx={{ mt: 0.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
              <JsonViewer data={item as Record<string, unknown>} collapsed={1} />
            </Box>
          ))}
        </Box>
      )}
      {parsed.output.length > 0 && (
        <Box sx={{ mt: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Output ({parsed.output.length})</Typography>
          {parsed.output.map((item, i) => (
            <Box key={i} sx={{ mt: 0.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
              <JsonViewer data={item as Record<string, unknown>} collapsed={1} />
            </Box>
          ))}
        </Box>
      )}
      <Box sx={{ mt: 1, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        {parsed.model && <Chip label={`Model: ${parsed.model}`} size="small" variant="outlined" />}
        {parsed.stream && <Chip label="Streaming" size="small" color="info" variant="outlined" />}
      </Box>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Messages panel: Gemini
// ---------------------------------------------------------------------------

function GeminiMessagesPanel({ parsed }: { parsed: GeminiParsed }) {
  return (
    <Box>
      {parsed.contents.length > 0 && (
        <Box>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Contents ({parsed.contents.length})</Typography>
          {parsed.contents.map((item, i) => (
            <Box key={i} sx={{ mt: 0.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
              <JsonViewer data={item as Record<string, unknown>} collapsed={1} />
            </Box>
          ))}
        </Box>
      )}
      {parsed.candidates.length > 0 && (
        <Box sx={{ mt: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Candidates ({parsed.candidates.length})</Typography>
          {parsed.candidates.map((item, i) => (
            <Box key={i} sx={{ mt: 0.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
              <JsonViewer data={item as Record<string, unknown>} collapsed={1} />
            </Box>
          ))}
        </Box>
      )}
      <Box sx={{ mt: 1, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        {parsed.model && <Chip label={`Model: ${parsed.model}`} size="small" variant="outlined" />}
      </Box>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Messages panel: Ollama
// ---------------------------------------------------------------------------

function OllamaMessagesPanel({ parsed }: { parsed: OllamaParsed }) {
  return (
    <Box>
      {parsed.messages.length > 0 && (
        <Box>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Messages ({parsed.messages.length})</Typography>
          {parsed.messages.map((msg, i) => (
            <Box key={i} sx={{ mt: 0.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
              <JsonViewer data={msg as Record<string, unknown>} collapsed={1} />
            </Box>
          ))}
        </Box>
      )}
      {parsed.responseMessage != null && (
        <Box sx={{ mt: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>Response</Typography>
          <Box sx={{ mt: 0.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
            <JsonViewer data={parsed.responseMessage as Record<string, unknown>} collapsed={1} />
          </Box>
        </Box>
      )}
      <Box sx={{ mt: 1, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        {parsed.model && <Chip label={`Model: ${parsed.model}`} size="small" variant="outlined" />}
        {parsed.stream && <Chip label="Streaming" size="small" color="info" variant="outlined" />}
        {parsed.done && <Chip label="Done" size="small" color="success" variant="outlined" />}
      </Box>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// SSE Timeline
// ---------------------------------------------------------------------------

function SseTimeline({ events }: { events: SseEvent[] }) {
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);

  return (
    <Box>
      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, mb: 0.5, display: 'block' }}>
        SSE Events ({events.length})
      </Typography>
      {events.map((evt, i) => {
        const parsedData = (() => {
          if (evt.data === '[DONE]') return null;
          try { return JSON.parse(evt.data); } catch { return null; }
        })();

        return (
          <Box
            key={i}
            sx={{
              borderLeft: 2,
              borderColor: evt.event === 'error' ? 'error.main' : 'primary.main',
              pl: 1,
              py: 0.25,
              mb: 0.25,
              cursor: 'pointer',
              '&:hover': { bgcolor: 'action.hover' },
            }}
            onClick={() => setExpandedIndex(expandedIndex === i ? null : i)}
          >
            <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
              <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, color: 'text.secondary', minWidth: 24 }}>
                {i + 1}
              </Typography>
              {evt.event && (
                <Chip
                  label={evt.event}
                  size="small"
                  variant="outlined"
                  sx={{ height: 16, fontSize: '0.6rem', '& .MuiChip-label': { px: 0.5 } }}
                />
              )}
              <Typography
                variant="caption"
                noWrap
                sx={{ fontFamily: monospaceFontFamily, fontSize: '0.65rem', color: 'text.secondary', flex: 1 }}
              >
                {evt.data.length > 80 ? evt.data.slice(0, 80) + '...' : evt.data}
              </Typography>
            </Box>
            {expandedIndex === i && parsedData && (
              <Box sx={{ mt: 0.5, ml: 3 }}>
                <JsonViewer data={parsedData} collapsed={2} />
              </Box>
            )}
            {expandedIndex === i && !parsedData && (
              <Typography variant="body2" sx={{ mt: 0.5, ml: 3, fontFamily: monospaceFontFamily, fontSize: '0.7rem', whiteSpace: 'pre-wrap' }}>
                {evt.data}
              </Typography>
            )}
          </Box>
        );
      })}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Per-request timing waterfall
// ---------------------------------------------------------------------------

// Distinct colours for the injected-vs-real waterfall. Real segments (connect/wait/receive/processing)
// reuse the pre-existing warning/info/success palette; injected segments get a separate warm/purple set
// so "latency MockServer added" reads at a glance as different from real network/upstream time. Explicit
// hex keeps the injected colours stable and distinct in both light and dark themes.
const INJECTED_DELAY_COLOR = '#7e57c2'; // deep purple — configured action/response delay
const INJECTED_CHAOS_COLOR = '#e53935'; // red — chaos-profile latency fault
const INJECTED_BREAKPOINT_COLOR = '#d81b60'; // pink — held at a response breakpoint

interface WaterfallSegment {
  key: string;
  label: string;
  ms: number;
  color: string;
  injected: boolean;
}

function TimingWaterfall({ timing }: { timing: RequestTiming }) {
  const total = timing.totalTimeInMillis;
  if (total === null || total === 0) return null;

  // Injected segments: latency MockServer deliberately added (absent/zero on older servers or plain mocks).
  const injectedChaos = timing.injectedChaosLatencyMillis ?? 0;
  const injectedDelay = timing.injectedDelayMillis ?? 0;
  const injectedBreakpoint = timing.breakpointHeldMillis ?? 0;
  const injectedSum = injectedChaos + injectedDelay + injectedBreakpoint;

  // Real segments. Proxied flows carry connect/TTFB and `total` is the real upstream round-trip, so injected
  // latency (applied after the upstream call) is additional. Mock flows have no connect/TTFB and `total` is
  // measured wall time that already includes the injected delays, so real processing = total - injected.
  const isProxied = timing.connectionTimeInMillis !== null || timing.timeToFirstByteInMillis !== null;
  const connect = timing.connectionTimeInMillis ?? 0;
  const ttfb = timing.timeToFirstByteInMillis ?? 0;
  const waitMs = Math.max(0, ttfb - connect);
  const receiveMs = Math.max(0, total - ttfb);
  const realProcessing = Math.max(0, total - injectedSum);

  const realSegments: WaterfallSegment[] = isProxied
    ? [
        { key: 'connect', label: 'Connect', ms: connect, color: 'warning.main', injected: false },
        { key: 'wait', label: 'Wait (TTFB)', ms: waitMs, color: 'info.main', injected: false },
        { key: 'receive', label: 'Receive', ms: receiveMs, color: 'success.main', injected: false },
      ]
    : [{ key: 'processing', label: 'Processing', ms: realProcessing, color: 'success.main', injected: false }];

  const injectedSegments: WaterfallSegment[] = [
    { key: 'injected-delay', label: 'Response delay', ms: injectedDelay, color: INJECTED_DELAY_COLOR, injected: true },
    { key: 'injected-chaos', label: 'Chaos latency', ms: injectedChaos, color: INJECTED_CHAOS_COLOR, injected: true },
    { key: 'injected-breakpoint', label: 'Breakpoint hold', ms: injectedBreakpoint, color: INJECTED_BREAKPOINT_COLOR, injected: true },
  ];

  // Order: real network/processing first, then the injected latency stacked after it.
  const segments = [...realSegments, ...injectedSegments].filter((s) => s.ms > 0);
  const barTotal = segments.reduce((sum, s) => sum + s.ms, 0) || total;

  return (
    <Box
      data-testid="timing-waterfall"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
        px: 1,
        py: 0.5,
        borderBottom: 1,
        borderColor: 'divider',
        bgcolor: 'action.hover',
        flexShrink: 0,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, flexWrap: 'wrap' }}>
        <Typography variant="caption" sx={{ fontSize: '0.65rem', fontWeight: 600, color: 'text.primary' }}>
          Timing
        </Typography>
        {timing.connectionTimeInMillis !== null && (
          <Chip
            label={`connect ${timing.connectionTimeInMillis}ms`}
            size="small"
            variant="outlined"
            sx={{ height: 18, fontSize: '0.6rem', '& .MuiChip-label': { px: 0.5 } }}
          />
        )}
        {timing.timeToFirstByteInMillis !== null && (
          <Chip
            label={`TTFB ${timing.timeToFirstByteInMillis}ms`}
            size="small"
            variant="outlined"
            sx={{ height: 18, fontSize: '0.6rem', '& .MuiChip-label': { px: 0.5 } }}
          />
        )}
        {injectedSum > 0 && (
          <Chip
            data-testid="timing-injected-chip"
            label={`injected ${injectedSum}ms`}
            size="small"
            variant="outlined"
            sx={{ height: 18, fontSize: '0.6rem', color: INJECTED_CHAOS_COLOR, borderColor: INJECTED_CHAOS_COLOR, '& .MuiChip-label': { px: 0.5 } }}
          />
        )}
        <Chip
          label={`total ${total}ms`}
          size="small"
          variant="outlined"
          color="info"
          sx={{ height: 18, fontSize: '0.6rem', '& .MuiChip-label': { px: 0.5 } }}
        />
        {barTotal > total && (
          <Tooltip title="Injected latency is applied in addition to the recorded upstream total — this is the combined wall time the client experienced" placement="top" arrow>
            <Chip
              data-testid="timing-wall-chip"
              label={`wall ${barTotal}ms`}
              size="small"
              variant="outlined"
              sx={{ height: 18, fontSize: '0.6rem', '& .MuiChip-label': { px: 0.5 } }}
            />
          </Tooltip>
        )}
      </Box>
      {/* Inline waterfall bar: real network/processing then injected latency, each segment tooltipped */}
      <Box
        data-testid="timing-bar"
        sx={{
          display: 'flex',
          height: 8,
          borderRadius: 1,
          overflow: 'hidden',
          bgcolor: 'background.default',
        }}
      >
        {segments.map((segment) => (
          <Tooltip
            key={segment.key}
            title={`${segment.injected ? 'Injected by MockServer' : 'Real'} — ${segment.label}: ${segment.ms}ms${segment.injected ? ' (configured value; random-distribution delays are approximate)' : ''}`}
            placement="top"
            arrow
          >
            <Box
              data-testid={`timing-segment-${segment.key}`}
              sx={{
                width: `${(segment.ms / barTotal) * 100}%`,
                bgcolor: segment.color,
                minWidth: 2,
              }}
            />
          </Tooltip>
        ))}
      </Box>
      {/* Legend grouped into Real vs Injected so the differentiator is explicit */}
      <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'flex-start', flexWrap: 'wrap' }}>
        <Box data-testid="timing-legend-real" sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <Typography variant="caption" sx={{ fontSize: '0.55rem', fontWeight: 600, color: 'text.secondary' }}>Real:</Typography>
          {realSegments.map((segment) => (
            <Box key={segment.key} sx={{ display: 'flex', alignItems: 'center', gap: 0.25 }}>
              <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: segment.color }} />
              <Typography variant="caption" sx={{ fontSize: '0.55rem', color: 'text.secondary' }}>{segment.label}</Typography>
            </Box>
          ))}
        </Box>
        {injectedSum > 0 && (
          <Box data-testid="timing-legend-injected" sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
            <Typography variant="caption" sx={{ fontSize: '0.55rem', fontWeight: 600, color: 'text.secondary' }}>Injected by MockServer:</Typography>
            {injectedSegments.filter((s) => s.ms > 0).map((segment) => (
              <Box key={segment.key} sx={{ display: 'flex', alignItems: 'center', gap: 0.25 }}>
                <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: segment.color }} />
                <Typography variant="caption" sx={{ fontSize: '0.55rem', color: 'text.secondary' }}>{segment.label}</Typography>
              </Box>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// ReplayDialog — replays a request via PUT /mockserver/replay and shows result
// ---------------------------------------------------------------------------

interface ReplayDialogProps {
  open: boolean;
  onClose: () => void;
  item: JsonListItem;
  connectionParams: ConnectionParams;
}

function ReplayDialog({ open, onClose, item, connectionParams }: ReplayDialogProps) {
  const [loading, setLoading] = useState(false);
  const [replayResponse, setReplayResponse] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState<HumanError | null>(null);
  const loadFullRow = useLoadFullRow();

  const handleReplay = useCallback(async () => {
    setLoading(true);
    setError(null);
    setReplayResponse(null);
    try {
      const fullItem = await loadFullRow(item);
      const httpRequest = (fullItem.value['httpRequest'] as Record<string, unknown> | undefined) ?? {};
      const result = await replayRequests(connectionParams, httpRequest);
      setReplayResponse(result);
    } catch (err) {
      // Route both server rejections (ReplayError) and network failures through
      // the shared humaniser so the displayed message is consistent and actionable,
      // with the raw server body kept behind a Details expander.
      setError(humanizeError(err));
    } finally {
      setLoading(false);
    }
  }, [connectionParams, item, loadFullRow]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ fontSize: '0.95rem' }}>Replay Request</DialogTitle>
      <DialogContent dividers>
        {!replayResponse && !error && !loading && (() => {
          const httpRequest = (item.value['httpRequest'] as Record<string, unknown> | undefined) ?? {};
          const method = typeof httpRequest['method'] === 'string' ? httpRequest['method'] : 'GET';
          const isNonGet = method !== 'GET';
          return (
            <>
              <Alert severity="warning" sx={{ mb: 1 }}>
                This will make a real outbound HTTP request to the original upstream target.
                {isNonGet && ` The method is ${method}, which may mutate state or incur costs (e.g. LLM API charges).`}
                {!isNonGet && ' It may incur costs if the target is a paid API (e.g. LLM provider).'}
              </Alert>
            </>
          );
        })()}
        {loading && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 2 }}>
            <CircularProgress size={20} />
            <Typography variant="body2">Sending request...</Typography>
          </Box>
        )}
        {error && <HumanErrorAlert error={error} sx={{ mb: 1 }} />}
        {replayResponse && (
          <Box sx={{ mt: 1 }}>
            <Typography variant="subtitle2" sx={{ mb: 0.5, fontWeight: 600 }}>
              Upstream Response
            </Typography>
            <JsonViewer data={replayResponse} collapsed={3} />
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} size="small">Close</Button>
        <Button
          onClick={handleReplay}
          disabled={loading}
          variant="contained"
          size="small"
          startIcon={<ReplayIcon sx={{ fontSize: '0.875rem' }} />}
        >
          {replayResponse ? 'Replay Again' : 'Replay'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Structured Request / Response inspector (generic HTTP traffic)
//
// Competitor traffic inspectors (Fiddler / Charles / Proxyman) present a
// structured Request / Response view rather than a raw JSON tree. For generic
// (non-LLM) HTTP traffic the detail pane defaults to these structured tabs and
// keeps the raw JSON tree as the last tab. All sub-panels read the already
// secret-masked value, so credentials never render verbatim here either.
// ---------------------------------------------------------------------------

/** Small bold section label used inside the structured panels. */
function SectionLabel({ children }: { children: ReactNode }) {
  return (
    <Typography
      variant="caption"
      color="text.secondary"
      sx={{ fontWeight: 600, display: 'block', mt: 1.5, mb: 0.5 }}
    >
      {children}
    </Typography>
  );
}

/** Two-column table of MockServer-format headers / query parameters. */
function KeyValueTable({ pairs, emptyLabel }: { pairs: Array<[string, string]>; emptyLabel: string }) {
  if (pairs.length === 0) {
    return (
      <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
        {emptyLabel}
      </Typography>
    );
  }
  return (
    <Box
      component="table"
      sx={{ borderCollapse: 'collapse', width: '100%', tableLayout: 'fixed' }}
    >
      <Box component="tbody">
        {pairs.map(([name, value], i) => (
          <Box
            component="tr"
            key={`${name}-${i}`}
            sx={{ '&:nth-of-type(odd)': { bgcolor: 'action.hover' } }}
          >
            <Box
              component="td"
              sx={{
                fontFamily: monospaceFontFamily,
                fontSize: '0.7rem',
                fontWeight: 600,
                color: 'text.secondary',
                verticalAlign: 'top',
                p: 0.5,
                width: '34%',
                wordBreak: 'break-word',
              }}
            >
              {name}
            </Box>
            <Box
              component="td"
              sx={{
                fontFamily: monospaceFontFamily,
                fontSize: '0.7rem',
                p: 0.5,
                wordBreak: 'break-word',
                whiteSpace: 'pre-wrap',
              }}
            >
              {value}
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
}

/**
 * Render a request/response body: pretty-printed with the JSON viewer when the
 * body is (or parses cleanly to) JSON, otherwise as raw wrapped monospace text.
 * `extractBodyContent` unwraps MockServer's body DTO and base64-decodes BINARY.
 */
function BodyView({ body }: { body: unknown }) {
  const content = extractBodyContent(body);
  if (content == null || content === '') {
    return (
      <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
        No body
      </Typography>
    );
  }
  if (typeof content === 'object') {
    return <JsonViewer data={content as Record<string, unknown>} collapsed={2} />;
  }
  if (typeof content === 'string') {
    // Parse OUTSIDE of the JSX return so no component is constructed inside the
    // try/catch (React does not surface such render errors — lint enforces this).
    const asJson = tryParseJsonObject(content);
    if (asJson) {
      return <JsonViewer data={asJson} collapsed={2} />;
    }
    return (
      <Typography
        component="pre"
        sx={{
          fontFamily: monospaceFontFamily,
          fontSize: '0.72rem',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          m: 0,
        }}
      >
        {content}
      </Typography>
    );
  }
  return <Typography variant="body2">{String(content)}</Typography>;
}

/** Parse a string to a JSON object/array, or return null when it is not JSON. */
function tryParseJsonObject(text: string): Record<string, unknown> | null {
  const trimmed = text.trim();
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null;
  try {
    const json = JSON.parse(trimmed) as unknown;
    return json && typeof json === 'object' ? (json as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/** Structured Request tab: method/path/query prominently, headers table, body. */
function StructuredRequestPanel({ value, truncated, onLoadFull }: {
  value: Record<string, unknown>;
  truncated?: TruncatedBody;
  onLoadFull?: () => Promise<void>;
}) {
  const req =
    value['httpRequest'] && typeof value['httpRequest'] === 'object' && !Array.isArray(value['httpRequest'])
      ? (value['httpRequest'] as Record<string, unknown>)
      : null;
  if (!req) {
    return (
      <Typography variant="body2" color="text.secondary">
        No request captured.
      </Typography>
    );
  }
  const method = typeof req['method'] === 'string' && req['method'] ? (req['method'] as string) : 'GET';
  const path = typeof req['path'] === 'string' ? (req['path'] as string) : '/';
  const queryPairs = headerPairs(req['queryStringParameters']);
  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, flexWrap: 'wrap' }}>
        <Chip label={method} size="small" color="primary" sx={{ fontFamily: monospaceFontFamily }} />
        <Typography
          sx={{ fontFamily: monospaceFontFamily, fontSize: '0.8rem', fontWeight: 600, wordBreak: 'break-all' }}
        >
          {path}
        </Typography>
      </Box>
      <SectionLabel>Query Parameters</SectionLabel>
      <KeyValueTable pairs={queryPairs} emptyLabel="No query parameters" />
      <SectionLabel>Headers</SectionLabel>
      <KeyValueTable pairs={headerPairs(req['headers'])} emptyLabel="No headers" />
      <SectionLabel>Body</SectionLabel>
      {truncated && <TruncatedBodyNotice marker={truncated} onLoad={onLoadFull} />}
      <BodyView body={req['body']} />
    </Box>
  );
}

/** Structured Response tab: status/reason prominently, headers table, body. */
function StructuredResponsePanel({ value, truncated, onLoadFull }: {
  value: Record<string, unknown>;
  truncated?: TruncatedBody;
  onLoadFull?: () => Promise<void>;
}) {
  const res =
    value['httpResponse'] && typeof value['httpResponse'] === 'object' && !Array.isArray(value['httpResponse'])
      ? (value['httpResponse'] as Record<string, unknown>)
      : null;
  if (!res) {
    return (
      <Typography variant="body2" color="text.secondary">
        No response captured for this request.
      </Typography>
    );
  }
  const status = typeof res['statusCode'] === 'number' ? (res['statusCode'] as number) : null;
  const reason = typeof res['reasonPhrase'] === 'string' ? (res['reasonPhrase'] as string) : '';
  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        {status !== null && <Chip label={status} size="small" color={statusColor(status)} />}
        {reason && (
          <Typography sx={{ fontFamily: monospaceFontFamily, fontSize: '0.8rem', fontWeight: 600 }}>
            {reason}
          </Typography>
        )}
      </Box>
      <SectionLabel>Headers</SectionLabel>
      <KeyValueTable pairs={headerPairs(res['headers'])} emptyLabel="No headers" />
      <SectionLabel>Body</SectionLabel>
      {truncated && <TruncatedBodyNotice marker={truncated} onLoad={onLoadFull} />}
      <BodyView body={res['body']} />
    </Box>
  );
}

/**
 * Compact at-a-glance header for the structured (generic) detail pane. Surfaces
 * only data that the captured item actually carries — method, host, path, and
 * response status/reason. Per-request latency is intentionally NOT shown here:
 * both proxied and mock-served traffic can carry a `timing` block (rendered by
 * TimingWaterfall, which splits injected vs real time), and the dashboard
 * WebSocket does not push a per-item capture timestamp, so we do not invent one.
 */
function GenericSummaryHeader({ summary }: { summary: TrafficSummary }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.75,
        px: 1,
        py: 0.5,
        borderBottom: 1,
        borderColor: 'divider',
        flexShrink: 0,
        flexWrap: 'wrap',
      }}
    >
      <Typography
        variant="caption"
        sx={{ fontFamily: monospaceFontFamily, fontWeight: 600, color: 'primary.main' }}
      >
        {summary.method ?? '?'}
      </Typography>
      <Tooltip title={`${summary.host ?? ''}${summary.path ?? ''}`} disableInteractive>
        <Typography
          variant="caption"
          noWrap
          sx={{ fontFamily: monospaceFontFamily, flex: 1, minWidth: 80, overflow: 'hidden', textOverflow: 'ellipsis' }}
        >
          {summary.host ?? ''}{summary.path ?? ''}
        </Typography>
      </Tooltip>
      {summary.statusCode !== null && (
        <Chip
          label={summary.statusCode}
          size="small"
          color={statusColor(summary.statusCode)}
          sx={{ height: 18, fontSize: '0.6rem', '& .MuiChip-label': { px: 0.5 } }}
        />
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Detail pane wrapper — single-level, adaptive tab row
// ---------------------------------------------------------------------------

interface DetailPaneProps {
  item: JsonListItem;
  summary: TrafficSummary;
  scriptedTurns: ScriptedTurn[];
  onCaptureAsMock?: () => void;
  onReplay?: () => void;
  onRepeat?: () => void;
  onAddToDiffPool?: () => void;
  inDiffPool?: boolean;
  /** When true, this entry matched no expectation — show mismatch-debugging actions. */
  unmatched?: boolean;
}

// ---------------------------------------------------------------------------
// Detail-pane action buttons — shared across the generic and tabbed layouts so
// both header rows offer the same actions (Copy as curl, Why?, Generate Stub,
// Replay, Capture as mock) in the same order and style.
// ---------------------------------------------------------------------------

interface DetailActionsProps {
  item: JsonListItem;
  summary: TrafficSummary;
  canCapture: boolean;
  unmatched: boolean;
  onCaptureAsMock?: () => void;
  onReplay?: () => void;
  onRepeat?: () => void;
  /** Add this request to the persistent Diff Pool (see the header chip). */
  onAddToDiffPool?: () => void;
  /** True when this request is already in the Diff Pool — the action shows as done. */
  inDiffPool?: boolean;
}

const detailActionSx = {
  fontSize: '0.7rem',
  textTransform: 'none',
  whiteSpace: 'nowrap',
  flexShrink: 0,
  mr: 0.5,
} as const;

function DetailActions({ item, summary, canCapture, unmatched, onCaptureAsMock, onReplay, onRepeat, onAddToDiffPool, inDiffPool }: DetailActionsProps) {
  const debugMismatch = useDebugMismatchContext();
  const generateStub = useGenerateStubContext();
  const setBreakpoint = useSetBreakpointContext();
  const [curlCopied, flashCurlCopied] = useTransientFlag(false);

  const httpRequest = useMemo(() => {
    const req = item.value['httpRequest'];
    return req && typeof req === 'object' && !Array.isArray(req)
      ? (req as Record<string, unknown>)
      : null;
  }, [item.value]);

  // "Create From This…" launchpad — the same fan-out (mock / breakpoint /
  // verify / chaos) offered on log rows, seeded from the selected flow. Pass
  // the whole captured value so Create Mock reuses the generic extraction, and
  // method/host/path from the parsed summary. Only shown when a request exists.
  const loadFullRow = useLoadFullRow();
  const launchpadActions = useMemo<CreateFromMenuAction[]>(
    () => buildLaunchpadActions(
      {
        itemValue: httpRequest ? item.value : undefined,
        loadItemValue: hasTruncatedBodies(item) ? async () => (await loadFullRow(item)).value : undefined,
        method: summary.method ?? undefined,
        path: summary.path ?? undefined,
        host: summary.host ?? undefined,
      },
      setBreakpoint,
    ),
    [httpRequest, item, loadFullRow, summary.method, summary.path, summary.host, setBreakpoint],
  );

  // Why Didn't This Match? and Generate Stub match the request, so they need its whole body.
  const withFullRequest = useCallback((act: (request: Record<string, unknown>) => Promise<void>) => {
    if (!httpRequest) return;
    if (!hasTruncatedBodies(item)) {
      void act(httpRequest);
      return;
    }
    loadFullRow(item).then((full) => {
      const request = full.value['httpRequest'];
      return request && typeof request === 'object' && !Array.isArray(request)
        ? act(request as Record<string, unknown>)
        : undefined;
    }, notifyFullBodyLoadFailed);
  }, [httpRequest, item, loadFullRow]);
  const handleCopyCurl = useCallback(async () => {
    let fullItem: JsonListItem;
    try {
      fullItem = await loadFullRow(item);
    } catch (e) {
      notifyFullBodyLoadFailed(e);
      return;
    }
    const curl = buildRequestCurl(fullItem.value, summary);
    if (!curl) return;
    try {
      await navigator.clipboard.writeText(curl);
      flashCurlCopied(true, false, 1500);
    } catch {
      // Clipboard denied (insecure context / permissions) — silently no-op,
      // consistent with the shared CopyButton's failure handling.
    }
  }, [item, loadFullRow, summary, flashCurlCopied]);

  return (
    <>
      {unmatched && debugMismatch && httpRequest && (
        <Button
          size="small"
          color="warning"
          startIcon={<HelpOutlinedIcon sx={{ fontSize: '0.875rem' }} />}
          onClick={() => withFullRequest(debugMismatch)}
          sx={detailActionSx}
        >
          Why Didn't This Match?
        </Button>
      )}
      {unmatched && generateStub && httpRequest && (
        <Button
          size="small"
          color="info"
          startIcon={<AutoFixHighIcon sx={{ fontSize: '0.875rem' }} />}
          onClick={() => withFullRequest(generateStub)}
          sx={detailActionSx}
        >
          Generate Stub
        </Button>
      )}
      {httpRequest && (
        <Button
          size="small"
          startIcon={curlCopied ? <CheckIcon sx={{ fontSize: '0.875rem' }} /> : <TerminalIcon sx={{ fontSize: '0.875rem' }} />}
          onClick={() => { void handleCopyCurl(); }}
          sx={detailActionSx}
        >
          {curlCopied ? 'Copied!' : 'Copy as curl'}
        </Button>
      )}
      {onReplay && (
        <Button
          size="small"
          startIcon={<ReplayIcon sx={{ fontSize: '0.875rem' }} />}
          onClick={onReplay}
          sx={detailActionSx}
        >
          Replay
        </Button>
      )}
      {onRepeat && (
        <Button
          size="small"
          startIcon={<RepeatIcon sx={{ fontSize: '0.875rem' }} />}
          onClick={onRepeat}
          sx={detailActionSx}
        >
          Repeat…
        </Button>
      )}
      {canCapture && onCaptureAsMock && (
        <Button
          size="small"
          startIcon={<SaveAltIcon sx={{ fontSize: '0.875rem' }} />}
          onClick={onCaptureAsMock}
          sx={detailActionSx}
        >
          Capture as mock
        </Button>
      )}
      {httpRequest && onAddToDiffPool && (
        <Tooltip title={inDiffPool ? 'Already in the Diff Pool' : 'Add this request to the Diff Pool to compare it against another later'}>
          <span>
            <Button
              size="small"
              disabled={inDiffPool}
              startIcon={inDiffPool
                ? <LibraryAddCheckIcon sx={{ fontSize: '0.875rem' }} />
                : <LibraryAddIcon sx={{ fontSize: '0.875rem' }} />}
              onClick={onAddToDiffPool}
              sx={detailActionSx}
            >
              {inDiffPool ? 'In Diff Pool' : 'Add to Diff Pool'}
            </Button>
          </span>
        </Tooltip>
      )}
      {httpRequest && (
        <CreateFromMenu actions={launchpadActions} iconColor="text.secondary" iconFontSize="1.1rem" />
      )}
    </>
  );
}

/** Build the tab list dynamically from the traffic kind. */
function buildTabs(parsed: ParsedTraffic, hasScriptedTurns: boolean): string[] {
  switch (parsed.kind) {
    case 'anthropic':
    case 'openai':
    case 'openai_responses':
    case 'gemini':
    case 'ollama': {
      const tabs = ['Messages', 'Conversation'];
      if (hasScriptedTurns) tabs.push('Scripted Turns');
      if (parsed.sseEvents) tabs.push('SSE Timeline');
      tabs.push('Raw JSON');
      return tabs;
    }
    case 'mcp':
      return ['MCP', 'Raw JSON'];
    case 'generic':
      // Structured Request / Response inspector by default, with the raw JSON
      // tree kept as the last tab (competitor-parity with Fiddler / Charles).
      return ['Request', 'Response', 'Raw JSON'];
  }
}

function DetailPane({ item, summary, scriptedTurns, onCaptureAsMock, onReplay, onRepeat, onAddToDiffPool, inDiffPool, unmatched = false }: DetailPaneProps) {
  const tabs = buildTabs(summary.parsed, scriptedTurns.length > 0);
  const [detailTab, setDetailTab] = useState(0);
  const canCapture = isCapturableTraffic(summary.parsed);
  // Mask known secret headers before rendering the Raw JSON view so credentials
  // (Authorization, x-api-key, Cookie, …) are not shown verbatim.
  const maskedValue = useMemo(() => maskSecretsInValue(item.value), [item.value]);
  const loadFullRow = useLoadFullRow();
  const loadFull = useCallback(async () => {
    await loadFullRow(item);
  }, [loadFullRow, item]);
  const rowTruncation = item.truncatedBodies?.httpRequest ?? item.truncatedBodies?.httpResponse;
  // Decoded non-stream response body text, used to flag a truncated/malformed
  // response in the conversation view (undefined when the body parsed cleanly).
  const rawResponseBody = useMemo(
    () => nonStreamResponseBodyText(item.value, summary.parsed),
    [item.value, summary.parsed],
  );

  // For generic traffic, render Raw JSON directly — no tab bar needed
  if (tabs.length === 0) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
        <LlmUsageDetail parsed={summary.parsed} />
        {summary.timing && <TimingWaterfall timing={summary.timing} />}
        <Box sx={{ display: 'flex', alignItems: 'center', px: 1, py: 0.5, flexShrink: 0 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, fontSize: '0.75rem', flexGrow: 1 }}>
            Raw JSON
          </Typography>
          <DetailActions
            item={item}
            summary={summary}
            canCapture={canCapture}
            unmatched={unmatched}
            onCaptureAsMock={onCaptureAsMock}
            onReplay={onReplay}
            onRepeat={onRepeat}
            onAddToDiffPool={onAddToDiffPool}
            inDiffPool={inDiffPool}
          />
        </Box>
        <Divider />
        <Box sx={{ flex: 1, overflowY: 'auto', p: 1 }}>
          {rowTruncation && <TruncatedBodyNotice marker={rowTruncation} onLoad={loadFull} />}
          <JsonViewer data={maskedValue} collapsed={2} />
        </Box>
      </Box>
    );
  }

  // Clamp tab index defensively (e.g. if tabs array shrank before the effect runs)
  const safeTab = Math.min(detailTab, tabs.length - 1);
  const activeLabel = tabs[safeTab];

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
      <LlmUsageDetail parsed={summary.parsed} />
      {summary.parsed.kind === 'generic' && <GenericSummaryHeader summary={summary} />}
      {summary.timing && <TimingWaterfall timing={summary.timing} />}
      {/* The actions wrap onto their own line rather than squeezing the tabs,
          whose labels would otherwise be cut behind a scroll arrow. */}
      <Box data-testid="traffic-detail-header" sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', flexShrink: 0 }}>
        <Tabs
          value={safeTab}
          onChange={(_, v: number) => setDetailTab(v)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ flex: '1 1 auto', minWidth: 0, minHeight: 32, '& .MuiTab-root': { minHeight: 32, py: 0.5, fontSize: '0.75rem' } }}
        >
          {tabs.map((label) => (
            <Tab key={label} label={label} />
          ))}
        </Tabs>
        <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', justifyContent: 'flex-end', ml: 'auto' }}>
          <DetailActions
            item={item}
            summary={summary}
            canCapture={canCapture}
            unmatched={unmatched}
            onCaptureAsMock={onCaptureAsMock}
            onReplay={onReplay}
            onRepeat={onRepeat}
            onAddToDiffPool={onAddToDiffPool}
            inDiffPool={inDiffPool}
          />
        </Box>
      </Box>
      <Divider />
      <Box sx={{ flex: 1, overflowY: 'auto', p: 1, minHeight: 0 }}>
        {rowTruncation && activeLabel !== 'Request' && activeLabel !== 'Response' && (
          <TruncatedBodyNotice marker={rowTruncation} onLoad={loadFull} />
        )}
        {activeLabel === 'Request' && (
          <StructuredRequestPanel value={maskedValue} truncated={item.truncatedBodies?.httpRequest} onLoadFull={loadFull} />
        )}
        {activeLabel === 'Response' && (
          <StructuredResponsePanel value={maskedValue} truncated={item.truncatedBodies?.httpResponse} onLoadFull={loadFull} />
        )}
        {activeLabel === 'Messages' && summary.parsed.kind === 'anthropic' && (
          <AnthropicMessagesPanel parsed={summary.parsed} />
        )}
        {activeLabel === 'Messages' && summary.parsed.kind === 'openai' && (
          <OpenAiMessagesPanel parsed={summary.parsed} />
        )}
        {activeLabel === 'Messages' && summary.parsed.kind === 'openai_responses' && (
          <OpenAiResponsesMessagesPanel parsed={summary.parsed} />
        )}
        {activeLabel === 'Messages' && summary.parsed.kind === 'gemini' && (
          <GeminiMessagesPanel parsed={summary.parsed} />
        )}
        {activeLabel === 'Messages' && summary.parsed.kind === 'ollama' && (
          <OllamaMessagesPanel parsed={summary.parsed} />
        )}
        {activeLabel === 'Conversation' && summary.parsed.kind === 'anthropic' && (
          <AnthropicConversationView parsed={summary.parsed} rawResponseBody={rawResponseBody} />
        )}
        {activeLabel === 'Conversation' && summary.parsed.kind === 'openai' && (
          <OpenAiConversationView parsed={summary.parsed} rawResponseBody={rawResponseBody} />
        )}
        {activeLabel === 'Conversation' && summary.parsed.kind === 'openai_responses' && (
          <OpenAiResponsesConversationView parsed={summary.parsed} rawResponseBody={rawResponseBody} />
        )}
        {activeLabel === 'Conversation' && summary.parsed.kind === 'gemini' && (
          <GeminiConversationView parsed={summary.parsed} rawResponseBody={rawResponseBody} />
        )}
        {activeLabel === 'Conversation' && summary.parsed.kind === 'ollama' && (
          <OllamaConversationView parsed={summary.parsed} rawResponseBody={rawResponseBody} />
        )}
        {activeLabel === 'Scripted Turns' && scriptedTurns.length > 0 && (
          <ScriptedTurnsPanel turns={scriptedTurns} />
        )}
        {activeLabel === 'SSE Timeline' && 'sseEvents' in summary.parsed && summary.parsed.sseEvents && (
          <SseTimeline events={summary.parsed.sseEvents} />
        )}
        {activeLabel === 'MCP' && summary.parsed.kind === 'mcp' && (
          <McpDetailPanel parsed={summary.parsed} statusCode={summary.statusCode} />
        )}
        {activeLabel === 'Raw JSON' && (
          <JsonViewer data={maskedValue} collapsed={2} />
        )}
      </Box>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Diff Pool — a persistent, cross-selection set of requests staged for diffing
// (Proxyman's "Add to Diff pool" / Fiddler "Compare"). Kept as lightweight refs
// so it survives selection changes and WebSocket refreshes without pinning the
// full store. The captured `value` is retained so the diff payload can be built
// on demand (and re-masked) the same way the two-pick Compare flow does.
// ---------------------------------------------------------------------------

interface DiffPoolEntry {
  key: string;
  method: string | null;
  path: string | null;
  status: number | null;
  value: Record<string, unknown>;
}

/**
 * Convert a captured request's value into the request JSON the diff endpoint
 * expects — the `httpRequest` sub-object if present, otherwise the whole value —
 * with secret headers masked first so credentials aren't shown verbatim in the
 * diff editor (and don't flow into a replay from there). Shared by the two-pick
 * Compare flow and the Diff Pool so both produce identical payloads.
 */
function requestJsonForDiff(value: Record<string, unknown>): string {
  const masked = maskSecretsInValue(value);
  const request = (masked['httpRequest'] as Record<string, unknown> | undefined) ?? masked;
  return JSON.stringify(request, null, 2);
}

// ---------------------------------------------------------------------------
// Main TrafficInspector component
// ---------------------------------------------------------------------------

export default function TrafficInspector() {
  // Show every captured request, regardless of whether it was proxied through to
  // a real upstream or matched a registered mock expectation. The MockServer
  // backend logs the two cases into separate arrays (proxiedRequests vs
  // recordedRequests); the user thinks of them both as "traffic".
  const proxiedRequests = useDashboardStore((s) => s.proxiedRequests);
  const recordedRequests = useDashboardStore((s) => s.recordedRequests);
  const activeExpectations = useDashboardStore((s) => s.activeExpectations);
  const trafficSearch = useDashboardStore((s) => s.trafficSearch);
  const setTrafficSearch = useDashboardStore((s) => s.setTrafficSearch);
  const selectedKey = useDashboardStore((s) => s.selectedTrafficKey);
  const setSelectedKey = useDashboardStore((s) => s.setSelectedTrafficKey);
  const connectionParams = useConnectionParams();
  const loadFullRow = useLoadFullRow();
  useAutoLoadLlmRows();
  // A capture or repeat built from a shortened body would be wrong: open it only once the body is whole.
  const loadFullRowThen = useCallback(async (item: JsonListItem, open: () => void) => {
    try {
      await loadFullRow(item);
    } catch (e) {
      notifyFullBodyLoadFailed(e);
      return;
    }
    open();
  }, [loadFullRow]);
  const theme = useTheme();
  // On narrow screens the side-by-side master/detail split squashes the detail
  // pane to a sliver; stack master-over-detail (column) on small screens.
  const stacked = useMediaQuery(theme.breakpoints.down('md'));

  // Side-by-side master/detail split width (pixels), drag-resizable via a
  // vertical divider between the master list and the detail pane. Persisted so
  // the chosen width survives reloads. Min keeps the list usable; max is a
  // generous cap (the detail pane stays flex:1 and re-clamps if the row is
  // narrower, since computeFromPointer clamps to the live container width).
  const rowRef = useRef<HTMLDivElement | null>(null);
  const MASTER_MIN = 260;
  const MASTER_MAX = 900;
  const masterWidth = useDragResize({
    orientation: 'vertical',
    initial: 380,
    min: MASTER_MIN,
    max: MASTER_MAX,
    step: 16,
    storageKey: 'mockserver-traffic-master-width',
    ariaLabel: 'Resize traffic list',
    computeFromPointer: (event) => {
      const rect = rowRef.current?.getBoundingClientRect();
      if (!rect) return 380;
      // Clamp the upper bound to leave at least ~360px for the detail pane.
      const next = event.clientX - rect.left;
      const dynamicMax = Math.max(MASTER_MIN, rect.width - 360);
      return Math.min(next, dynamicMax);
    },
  });

  const [captureDialogOpen, setCaptureDialogOpen] = useState(false);
  const [replayDialogOpen, setReplayDialogOpen] = useState(false);
  const [repeatDialogOpen, setRepeatDialogOpen] = useState(false);
  const [explainOpen, setExplainOpen] = useState(false);
  const [promoteOpen, setPromoteOpen] = useState(false);

  // Compare mode: pick two requests from the list and diff them field-by-field via the shared
  // DiffRequestsDialog (PUT /mockserver/diff). compareKeys holds the (max two) selected item keys.
  const [compareMode, setCompareMode] = useState(false);
  const [compareKeys, setCompareKeys] = useState<string[]>([]);

  // Diff Pool (Proxyman-style): a persistent set of staged requests independent
  // of the current selection or the two-pick Compare mode. Held as lightweight
  // refs so it survives WebSocket refreshes. The popover lets the user pick any
  // two pooled entries and open the SAME shared diff dialog on them.
  const [diffPool, setDiffPool] = useState<DiffPoolEntry[]>([]);
  const [poolAnchorEl, setPoolAnchorEl] = useState<HTMLElement | null>(null);
  const [poolPicks, setPoolPicks] = useState<string[]>([]);
  const [poolDiffPair, setPoolDiffPair] = useState<[string, string] | null>(null);

  const addToDiffPool = useCallback((entry: DiffPoolEntry) => {
    setDiffPool((prev) => (prev.some((e) => e.key === entry.key) ? prev : [...prev, entry]));
  }, []);

  const removeFromDiffPool = useCallback((key: string) => {
    setDiffPool((prev) => {
      const next = prev.filter((e) => e.key !== key);
      // The header chip (the popover anchor) unmounts when the pool empties, so
      // close the popover too rather than leaving it anchored to a detached node.
      if (next.length === 0) setPoolAnchorEl(null);
      return next;
    });
    setPoolPicks((prev) => prev.filter((k) => k !== key));
  }, []);

  const clearDiffPool = useCallback(() => {
    setDiffPool([]);
    setPoolPicks([]);
    setPoolAnchorEl(null);
  }, []);

  const togglePoolPick = useCallback((key: string) => {
    setPoolPicks((prev) => {
      if (prev.includes(key)) return prev.filter((k) => k !== key);
      if (prev.length >= 2) return prev; // cap at two; further checkboxes disabled
      return [...prev, key];
    });
  }, []);

  // Bulk-select mode: pick any number of requests and clear them in one action.
  // Kept separate from (and mutually exclusive with) compare mode, which caps at
  // two and drives the diff dialog.
  const [selectMode, setSelectMode] = useState(false);
  const [selectedKeys, setSelectedKeys] = useState<ReadonlySet<string>>(() => new Set());
  const [bulkClearConfirm, setBulkClearConfirm] = useState(false);
  const clearingRef = useRef(false);

  const toggleCompareMode = useCallback(() => {
    setCompareMode((prev) => {
      // Leaving compare mode clears any pending selection.
      if (prev) setCompareKeys([]);
      // Entering compare mode exits the mutually-exclusive select mode.
      else { setSelectMode(false); setSelectedKeys(new Set()); }
      return !prev;
    });
  }, []);

  const toggleCompareKey = useCallback((key: string) => {
    setCompareKeys((prev) => {
      if (prev.includes(key)) return prev.filter((k) => k !== key);
      if (prev.length >= 2) return prev; // cap at two; row checkbox is disabled past this
      return [...prev, key];
    });
  }, []);

  const toggleSelectMode = useCallback(() => {
    setSelectMode((prev) => {
      if (prev) setSelectedKeys(new Set()); // leaving select mode clears the picks
      else { setCompareMode(false); setCompareKeys([]); } // entering exits compare mode
      return !prev;
    });
  }, []);

  const toggleSelectKey = useCallback((key: string) => {
    setSelectedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  // Gather scripted turns from active expectations
  const scriptedTurns = useMemo(
    () => gatherScriptedTurns(activeExpectations),
    [activeExpectations],
  );

  // Above INFO the server does not log the 404 it returns for a request that
  // matched nothing (it is an INFO entry), so such rows have no response and
  // nothing marks them unmatched. Say so rather than silently showing less.
  const logLevel = useDashboardStore((s) => s.serverConfiguration?.['logLevel']);
  const [logLevelHintDismissed, setLogLevelHintDismissed] = useState(false);
  const showLogLevelHint = !logLevelHintDismissed
    && (logLevel === 'WARN' || logLevel === 'ERROR' || logLevel === 'OFF')
    && recordedRequests.some((item) => !item.value['httpResponse'] && !isNonHttpRequestRow(item));

  // Build summaries for every captured request (proxied + mocked), one row per
  // request, newest first.
  const allRequests = useMemo(
    () => combineTraffic(proxiedRequests, recordedRequests),
    [proxiedRequests, recordedRequests],
  );
  // Tag each row with whether it matched no expectation. Only received (non-proxied)
  // requests can be "unmatched" — a proxied 404 comes from the real upstream, not MockServer.
  const summaries = useMemo(() => {
    const proxiedKeys = new Set(proxiedRequests.map((item) => item.key));
    return allRequests.map((item) => ({
      item,
      summary: cachedSummarize(item.value),
      unmatched: !proxiedKeys.has(item.key) && isUnmatchedResponse(item.value),
    }));
  }, [allRequests, proxiedRequests]);

  // Count of captured requests that matched no expectation, surfaced as a header
  // badge that opens the Explain-Unmatched dialog.
  const unmatchedCount = useMemo(() => summaries.filter((s) => s.unmatched).length, [summaries]);

  // Only recorded (proxied/forwarded) traffic can be promoted into mocks — the
  // server's promote endpoint retrieves FORWARDED_REQUEST exchanges — so gate the
  // "Promote to Mocks" action on there being at least one proxied request.
  const promotableCount = proxiedRequests.length;

  // Pre-fill the promote dialog's method / path filter from the current search's
  // method:/path: operators, where the user has expressed one, so the promotion
  // scope matches what they are already looking at.
  const promotePrefill = useMemo(() => {
    const { operators } = parseSearchTerm(trafficSearch);
    const method = operators.find((op) => op.field === 'method')?.expr;
    const rawPath = operators.find((op) => op.field === 'path')?.expr;
    // The search's path: operator treats * as a glob, but the server filter is a
    // regex matcher — translate so the prefill scopes to what is on screen.
    const path = rawPath?.includes('*')
      ? rawPath.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*')
      : rawPath;
    return { method: method || undefined, path: path || undefined };
  }, [trafficSearch]);

  // Search reads only what is shown, so a body the server shortened is searched only in part until
  // it is loaded. Loading every one automatically could hold hundreds of megabytes, so it is offered.
  const shortenedRows = useMemo(() => allRequests.filter(hasTruncatedBodies), [allRequests]);
  const [searchingFull, setSearchingFull] = useState(false);
  const searchFullBodies = useCallback(async () => {
    setSearchingFull(true);
    try {
      const failed = await forEachLimited(shortenedRows, 4, loadFullRow);
      if (failed > 0) {
        useDashboardStore.getState().setNotification({
          message: `${failed} of ${shortenedRows.length} full bodies could not be loaded, so those requests are searched only in part`,
          severity: 'warning',
        });
      }
    } finally {
      setSearchingFull(false);
    }
  }, [shortenedRows, loadFullRow]);

  // Filter by search
  const filteredLive = useMemo(
    () =>
      trafficSearch
        ? summaries.filter(({ item, summary }) => matchesSearch(item, summary, trafficSearch))
        : summaries,
    [summaries, trafficSearch],
  );

  // Hold what the reader is working with. This list is fed by the same
  // server-capped live window as the dashboard panels — at most 100 rows, oldest
  // dropped as new ones arrive — so under load a row being read, selected, or
  // compared is DELETED from the feed within seconds. Selection and compare make
  // it worse here than on the panels: a selected key that leaves the data takes
  // the detail pane with it. Held while the reader is scrolled away from the top,
  // has a row selected, or has rows checked for compare.
  // Console order: oldest first, newest appended at the BOTTOM, so an arriving row
  // lands below the viewport and nothing being read moves.
  const displayOrder = useMemo(() => [...filteredLive].reverse(), [filteredLive]);
  // Following = watching the tail. Not following, or holding a selection, means
  // reading — and then rows evicted from the top must be held or the list
  // collapses upward. Selection matters here more than on the panels: a selected
  // row leaving the feed takes the detail pane with it.
  const [follow, setFollow] = useFollow();
  const interacting = !follow || selectedKey !== null || selectedKeys.size > 0;
  const filtered = useHeldItems(displayOrder, trafficKeyOf, interacting);

  // Console tail-following: pin the scroll container to the BOTTOM, where the
  // newest row is, whenever the reader is following. This mirrors Panel.tsx's
  // tail pin exactly — and is the piece that was missing here. Without it the
  // container sat at scrollTop 0 (the OLDEST row in console order) with nothing
  // ever writing scrollTop, so a reader in the default following state watched
  // the top rows evict and the list collapse upward: the original bug, on this
  // page. `onScroll` below is only a READER of scroll position; this is the
  // WRITER. The scroll Box (ProgressiveList's scroll ancestor) rolls its own
  // container here rather than going through Panel, so the effect lives here too.
  //
  // Gated on `follow` alone, like Panel. Selecting a row opens a SEPARATE detail
  // pane (not an inline expansion), so pinning the list to the tail never moves
  // what the reader is reading; and selection already sets `interacting`, which
  // holds the rows so nothing the reader might re-find in the list is evicted.
  // There is therefore no "the row I opened scrolled away" hazard to suppress —
  // the concern that made Panel's inline-expansion world need an open-item signal
  // does not arise here.
  // Same machinery as Panel, for the same reasons: a bare `scroll` handler cannot
  // tell a reader's gesture from the virtualizer re-measuring, and an unguarded
  // pin undoes the reader's scroll. See useTailFollow.
  const { scrollRef, handleScroll, pinToTail } = useTailFollow(follow, setFollow);
  useLayoutEffect(() => {
    pinToTail();
  }, [filtered, follow, pinToTail]);

  // Host facet. Derived from the UNFILTERED summaries so pinning a host does not
  // collapse the facet to the single host just pinned (which would leave no way
  // back to the others without hand-editing the search box).
  const hosts = useMemo(() => hostCounts(summaries), [summaries]);
  const pinnedHost = useMemo(() => pinnedHostOf(trafficSearch), [trafficSearch]);
  const handlePinHost = useCallback(
    (host: string | null) => setTrafficSearch(pinHostInSearch(trafficSearch, host)),
    [trafficSearch, setTrafficSearch],
  );

  // Select by stable item key, not array position: the store fully replaces the traffic lists
  // on every WebSocket refresh and `filtered` is re-derived on each search keystroke, so a
  // positional index would point at a different (or missing) request after any update.
  const selectedEntry = useMemo(() => {
    if (selectedKey === null) return null;
    return filtered.find(({ item }) => item.key === selectedKey) ?? null;
  }, [filtered, selectedKey]);

  const handleRowClick = useCallback(
    (key: string) => {
      setSelectedKey(selectedKey === key ? null : key);
    },
    [selectedKey, setSelectedKey],
  );

  // Single stable per-row select handler passed to the memoized TrafficRow. In
  // compare mode a click toggles the comparison selection; otherwise it selects
  // the row for the detail pane. Keeping this stable (rather than a fresh arrow
  // per row in the map below) is what lets React.memo skip unchanged rows.
  const handleRowSelect = useCallback(
    (key: string) => {
      if (compareMode) {
        toggleCompareKey(key);
      } else if (selectMode) {
        toggleSelectKey(key);
      } else {
        handleRowClick(key);
      }
    },
    [compareMode, selectMode, toggleCompareKey, toggleSelectKey, handleRowClick],
  );

  // Roving tab index: Tab reaches one row (the last one focused, else the open
  // one, else the newest) and the arrow keys move between rows. Neighbours are
  // found in the DOM, where the windowed list keeps the rows near the viewport.
  const [rovingKey, setRovingKey] = useState<string | null>(null);
  const tabbableKey = useMemo(() => {
    const present = (key: string | null) => key !== null && filtered.some(({ item }) => item.key === key);
    if (present(rovingKey)) return rovingKey;
    if (present(selectedKey)) return selectedKey;
    return filtered.length > 0 ? filtered[filtered.length - 1]!.item.key : null;
  }, [filtered, rovingKey, selectedKey]);
  const handleMoveFocus = useCallback((row: HTMLElement, delta: 1 | -1) => {
    const rows = rowElements(row.closest('[data-testid="traffic-scroll-region"]'));
    const next = rows[rows.indexOf(row) + delta];
    if (!next) return;
    setRovingKey(next.dataset.trafficRowKey ?? null);
    next.focus();
  }, []);
  // When the reader scrolls the Tab-reachable row out of the window, hand the
  // tab stop to the first row in view so Tab can still reach the list.
  const keepTabStopInView = useCallback((region: HTMLElement) => {
    const rows = rowElements(region);
    if (rows.length === 0 || rows.some((row) => row.tabIndex === 0)) return;
    const top = region.getBoundingClientRect().top;
    const inView = rows.find((row) => row.getBoundingClientRect().bottom > top) ?? rows[0]!;
    setRovingKey(inView.dataset.trafficRowKey ?? null);
  }, []);

  // Whether the currently-selected detail entry is already staged in the pool,
  // and a handler to stage it. Extracted from the selected summary so the pooled
  // ref stays lightweight (method / path / status) while keeping the value for
  // the diff payload.
  const selectedInDiffPool = selectedEntry ? diffPool.some((e) => e.key === selectedEntry.item.key) : false;

  // The pool keeps the whole request: by the time it is diffed the row may have left the server's log.
  const handleAddSelectedToDiffPool = useCallback(async () => {
    if (!selectedEntry) return;
    let full: JsonListItem;
    try {
      full = await loadFullRow(selectedEntry.item);
    } catch (e) {
      notifyFullBodyLoadFailed(e);
      return;
    }
    addToDiffPool({
      key: selectedEntry.item.key,
      method: selectedEntry.summary.method,
      path: selectedEntry.summary.path,
      status: selectedEntry.summary.statusCode,
      value: full.value,
    });
  }, [selectedEntry, addToDiffPool, loadFullRow]);

  // The two pooled entries picked in the popover, in pick order (first =
  // "expected", second = "actual"), and their diff payloads.
  const canDiffPool = poolPicks.length === 2;
  const openPoolDiff = useCallback(() => {
    if (poolPicks.length !== 2) return;
    const pair = poolPicks.map((key) => {
      const entry = diffPool.find((e) => e.key === key);
      return entry ? requestJsonForDiff(entry.value) : '';
    });
    setPoolDiffPair([pair[0] ?? '', pair[1] ?? '']);
    setPoolAnchorEl(null);
  }, [poolPicks, diffPool]);

  // Resolve the two selected requests to the JSON the diff endpoint expects (the request
  // definition — `httpRequest` if present, otherwise the whole captured value). Preserve the
  // user's pick order: the first selected is "expected", the second "actual".
  // Selected keys whose request still exists (a WebSocket refresh can remove one),
  // so a stale selection can't produce an empty/invalid diff payload.
  const validCompareKeys = useMemo(
    () => compareKeys.filter((key) => allRequests.some((item) => item.key === key)),
    [compareKeys, allRequests],
  );

  const canDiff = validCompareKeys.length === 2;
  const [compareDiffPair, setCompareDiffPair] = useState<[string, string] | null>(null);
  const openCompareDiff = useCallback(async () => {
    const picked = validCompareKeys.flatMap((key) => allRequests.filter((item) => item.key === key));
    if (picked.length !== 2) return;
    let full: JsonListItem[];
    try {
      full = await Promise.all(picked.map((item) => loadFullRow(item)));
    } catch (e) {
      notifyFullBodyLoadFailed(e);
      return;
    }
    setCompareDiffPair([requestJsonForDiff(full[0]!.value), requestJsonForDiff(full[1]!.value)]);
  }, [validCompareKeys, allRequests, loadFullRow]);

  // Selected keys whose request still exists (a WebSocket refresh can drop one),
  // resolved against the currently-filtered rows so "select all" and the count
  // track exactly what is visible.
  const filteredKeys = useMemo(() => filtered.map(({ item }) => item.key), [filtered]);
  const validSelectedKeys = useMemo(
    () => filteredKeys.filter((key) => selectedKeys.has(key)),
    [filteredKeys, selectedKeys],
  );
  const allSelected = filteredKeys.length > 0 && validSelectedKeys.length === filteredKeys.length;
  const someSelected = validSelectedKeys.length > 0 && !allSelected;

  const toggleSelectAll = useCallback(() => {
    setSelectedKeys((prev) => {
      const allPicked = filteredKeys.length > 0 && filteredKeys.every((key) => prev.has(key));
      return allPicked ? new Set() : new Set(filteredKeys);
    });
  }, [filteredKeys]);

  const handleBulkClear = useCallback(async () => {
    if (clearingRef.current) return;
    const targets = allRequests.filter((item) => validSelectedKeys.includes(item.key));
    if (targets.length === 0) return;
    clearingRef.current = true;
    try {
      // No bulk endpoint exists — batch one clear-by-request-matcher call per
      // selected row (allSettled so one failure doesn't abort the rest).
      const results = await Promise.allSettled(
        targets.map(async (item) => clearLoggedRequest(connectionParams, requestDefinitionOf((await loadFullRow(item)).value))),
      );
      const clearedKeys = new Set(
        targets.filter((_, i) => results[i]?.status === 'fulfilled').map((item) => item.key),
      );
      const failures = results.filter(
        (r): r is PromiseRejectedResult => r.status === 'rejected',
      );
      // Optimistically drop the cleared rows; the next WebSocket push reconciles.
      if (clearedKeys.size > 0) {
        useDashboardStore.setState((s) => ({
          recordedRequests: s.recordedRequests.filter((i) => !clearedKeys.has(i.key)),
          proxiedRequests: s.proxiedRequests.filter((i) => !clearedKeys.has(i.key)),
        }));
      }
      setSelectedKeys((prev) => {
        const next = new Set(prev);
        for (const key of clearedKeys) next.delete(key);
        return next;
      });
      const setNotification = useDashboardStore.getState().setNotification;
      if (failures.length === 0) {
        setNotification({ message: `Cleared ${clearedKeys.size} request${clearedKeys.size === 1 ? '' : 's'}`, severity: 'success' });
      } else if (clearedKeys.size === 0) {
        setNotification({ message: humanizeError(failures[0]?.reason ?? 'Clear failed').message, severity: 'error' });
      } else {
        setNotification({ message: `Cleared ${clearedKeys.size}, ${failures.length} failed`, severity: 'warning' });
      }
    } finally {
      clearingRef.current = false;
    }
  }, [allRequests, validSelectedKeys, connectionParams, loadFullRow]);

  // The side-by-side master/detail split is user-resizable only when the detail
  // pane is actually shown (not stacked, an entry selected, not comparing/selecting).
  const resizableSplit = !stacked && Boolean(selectedEntry) && !compareMode && !selectMode;

  return (
    <Box
      ref={rowRef}
      sx={{
        flex: 1,
        display: 'flex',
        // Stack master-over-detail on small screens, side-by-side on md+.
        flexDirection: stacked ? 'column' : 'row',
        gap: 1,
        p: 1,
        overflow: 'hidden',
        minHeight: 0,
      }}
    >
      {/* Master list */}
      <Paper
        variant="outlined"
        sx={{
          display: 'flex',
          flexDirection: 'column',
          // When stacked, the list takes full width and a bounded share of the
          // height so the detail pane below it stays usable. Side-by-side with a
          // detail pane the width is user-resizable (pixels); otherwise full width.
          width: stacked
            ? '100%'
            : resizableSplit
              ? masterWidth.value
              : '100%',
          flexShrink: stacked
            ? selectedEntry && !compareMode && !selectMode
              ? 0
              : undefined
            : resizableSplit
              ? 0
              : undefined,
          minWidth: stacked ? 0 : 300,
          // Explicit because 'clip', unlike 'hidden', keeps the content-sized minimum.
          minHeight: 0,
          height: stacked && selectedEntry && !compareMode && !selectMode ? '45%' : undefined,
          // 'clip', not 'hidden': a hidden box can still be scrolled, so focusing a
          // control past its edge (a popover's anchor) shifted every row sideways.
          overflow: 'clip',
          // Disable the width transition while actively dragging so the pane
          // tracks the pointer 1:1.
          transition:
            stacked || masterWidth.dragging ? undefined : 'width 0.2s ease',
        }}
      >
        <Box
          data-testid="traffic-toolbar"
          sx={{
            display: 'flex',
            alignItems: 'center',
            // Wraps onto a second line when the detail pane narrows the list, so
            // every control stays visible and reachable.
            flexWrap: 'wrap',
            columnGap: 1,
            rowGap: 0.5,
            px: 1,
            py: 0.25,
            borderBottom: 1,
            borderColor: 'divider',
            flexShrink: 0,
            // Size container so the Promote button's label can collapse when the
            // master/detail split squeezes this pane (see the @container queries below).
            containerType: 'inline-size',
          }}
        >
          <Typography variant="subtitle2" sx={{ fontWeight: 600, fontSize: '0.79rem' }}>
            Traffic
          </Typography>
          {/* No row count: the list is the server's capped live window, so a count
              pins at the cap however much traffic ran. */}
          {/* Follow control, mirroring Panel's. A reader who scrolls up to read
              turns following off (via onScroll below) and needs a visible way to
              resume; a reader parked at the tail sees it lit. Clicking it jumps
              back to the newest rows and resumes following (the tail-pin effect
              runs on the next render). */}
          <Chip
            label={follow ? 'Following' : 'Follow'}
            size="small"
            color={follow ? 'primary' : 'default'}
            variant={follow ? 'filled' : 'outlined'}
            clickable
            onClick={() => setFollow((prev) => !prev)}
            title={
              follow
                ? 'Following the newest requests. Scroll up to stop and read — nothing will move.'
                : 'Paused while you read. Click to jump to the newest requests and resume following.'
            }
            sx={{ height: 18, fontSize: '0.65rem', '& .MuiChip-label': { px: 0.75 } }}
          />
          {unmatchedCount > 0 && (
            <Tooltip title="Show why these requests didn't match any expectation">
              <Chip
                // No number: unmatchedCount is over the capped live window, not over
                // captured traffic, so it silently becomes the cap rather than a count.
                label="unmatched"
                color="warning"
                size="small"
                onClick={() => setExplainOpen(true)}
                sx={{ height: 18, fontSize: '0.65rem', cursor: 'pointer', '& .MuiChip-label': { px: 0.75 } }}
              />
            </Tooltip>
          )}
          <OperatorSearchField
            id="traffic-inspector-search"
            value={trafficSearch}
            onChange={setTrafficSearch}
            maxWidth={200}
          />
          <Tooltip title="Pick two requests to diff field-by-field">
            <ToggleButton
              value="compare"
              size="small"
              selected={compareMode}
              onChange={toggleCompareMode}
              aria-label="Compare requests"
              sx={{ height: 28, px: 1, fontSize: '0.7rem', textTransform: 'none', flexShrink: 0 }}
            >
              <CompareArrowsIcon sx={{ fontSize: '0.95rem', mr: 0.5 }} />
              Compare
            </ToggleButton>
          </Tooltip>
          {compareMode && (
            <Button
              size="small"
              variant="contained"
              disabled={!canDiff}
              onClick={() => { void openCompareDiff(); }}
              sx={{ height: 28, px: 1, fontSize: '0.7rem', textTransform: 'none', flexShrink: 0 }}
            >
              Diff ({validCompareKeys.length}/2)
            </Button>
          )}
          {diffPool.length > 0 && (
            <Tooltip title="Requests staged for comparison — pick any two to diff">
              <Chip
                icon={<LibraryAddCheckIcon sx={{ fontSize: '0.9rem' }} />}
                label={`Diff Pool (${diffPool.length})`}
                color="secondary"
                size="small"
                onClick={(e) => setPoolAnchorEl(e.currentTarget)}
                aria-label={`Diff Pool (${diffPool.length})`}
                sx={{ height: 22, fontSize: '0.65rem', cursor: 'pointer', flexShrink: 0, '& .MuiChip-label': { px: 0.75 } }}
              />
            </Tooltip>
          )}
          <Tooltip title="Select multiple requests to clear at once">
            <ToggleButton
              value="select"
              size="small"
              selected={selectMode}
              onChange={toggleSelectMode}
              aria-label="Select requests"
              sx={{ height: 28, px: 1, fontSize: '0.7rem', textTransform: 'none', flexShrink: 0 }}
            >
              <ChecklistIcon sx={{ fontSize: '0.95rem', mr: 0.5 }} />
              Select
            </ToggleButton>
          </Tooltip>
          {selectMode && (
            <>
              <Tooltip title="Select all / none">
                <Checkbox
                  size="small"
                  checked={allSelected}
                  indeterminate={someSelected}
                  disabled={filteredKeys.length === 0}
                  onChange={toggleSelectAll}
                  slotProps={{ input: { 'aria-label': 'Select all requests' } }}
                  sx={{ p: 0.25, flexShrink: 0 }}
                />
              </Tooltip>
              <Button
                size="small"
                color="error"
                variant="outlined"
                disabled={validSelectedKeys.length === 0}
                startIcon={<DeleteOutlineIcon sx={{ fontSize: '0.95rem' }} />}
                onClick={() => setBulkClearConfirm(true)}
                sx={{ height: 28, px: 1, fontSize: '0.7rem', textTransform: 'none', flexShrink: 0 }}
              >
                Clear ({validSelectedKeys.length})
              </Button>
            </>
          )}
          <Box sx={{ flexGrow: 1 }} />
          <Tooltip
            title={
              promotableCount > 0
                ? 'Turn recorded (proxied) traffic into active mock expectations'
                : 'Proxy some traffic through MockServer first — recorded requests can then be promoted to mocks'
            }
          >
            <span>
              <Button
                size="small"
                variant="outlined"
                disabled={promotableCount === 0}
                startIcon={<AutoAwesomeMotionIcon sx={{ fontSize: '0.95rem' }} />}
                onClick={() => setPromoteOpen(true)}
                sx={{ height: 28, px: 1, fontSize: '0.7rem', textTransform: 'none', flexShrink: 0, whiteSpace: 'nowrap' }}
              >
                {/* The full label wraps to two lines when the master/detail split squeezes
                    the list pane, so collapse to a short label below the container breakpoint
                    (the header Box has containerType: 'inline-size'). */}
                <Box component="span" sx={{ display: 'inline', '@container (max-width: 720px)': { display: 'none' } }}>Promote to Mocks</Box>
                <Box component="span" sx={{ display: 'none', '@container (max-width: 720px)': { display: 'inline' } }}>Mocks</Box>
              </Button>
            </span>
          </Tooltip>
        </Box>
        {showLogLevelHint && (
          <Alert
            severity="info"
            data-testid="traffic-log-level-hint"
            onClose={() => setLogLevelHintDismissed(true)}
            sx={{ mx: 1, my: 0.5, py: 0, fontSize: '0.75rem' }}
          >
            MockServer is logging at {String(logLevel)}, so it does not record the response to a request
            that matches no expectation. Those requests show no status here, and the unmatched badge, Why
            Didn&apos;t This Match? and Generate Stub need the log level INFO (set it in Configuration).
          </Alert>
        )}
        {trafficSearch && shortenedRows.length > 0 && (
          <Alert
            severity="info"
            sx={{ mx: 1, my: 0.5, py: 0, fontSize: '0.75rem' }}
            action={(
              <Button size="small" color="inherit" disabled={searchingFull} onClick={() => { void searchFullBodies(); }}>
                {searchingFull ? 'Loading…' : 'Search Full Bodies'}
              </Button>
            )}
          >
            {shortenedRows.length === 1 ? '1 request has' : `${shortenedRows.length} requests have`} a body
            longer than shown; the search covers only the part shown.
          </Alert>
        )}
        {/* Only worth the vertical space once the traffic actually spans more
            than one upstream — see HostTree's docstring. */}
        {hosts.length > 1 && (
          <HostTree hosts={hosts} pinnedHost={pinnedHost} onPin={handlePinHost} />
        )}
        <Box
          ref={scrollRef}
          data-testid="traffic-scroll-region"
          sx={{ flex: 1, overflowY: 'auto', bgcolor: 'background.default' }}
          onScroll={(e) => {
            handleScroll(e.currentTarget);
            keepTabStopInView(e.currentTarget);
          }}
        >
          {filtered.length === 0 ? (
            allRequests.length === 0 ? (
              <Box sx={{ p: 2, textAlign: 'center', color: 'text.secondary' }}>
                <Typography variant="body2" sx={{ mb: 1 }}>No traffic captured yet.</Typography>
                <Typography variant="caption" component="div" sx={{ mb: 1 }}>
                  Send a request through MockServer — as a proxy or to a mock — and it appears here. For example:
                </Typography>
                <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, maxWidth: '100%' }}>
                  <Box
                    component="code"
                    sx={{
                      fontFamily: monospaceFontFamily,
                      fontSize: '0.72rem',
                      px: 1,
                      py: 0.5,
                      bgcolor: 'action.hover',
                      borderRadius: 1,
                      overflowX: 'auto',
                      textAlign: 'left',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {`curl -x http://${connectionParams.host}:${connectionParams.port} http://example.com`}
                  </Box>
                  <CopyButton text={`curl -x http://${connectionParams.host}:${connectionParams.port} http://example.com`} />
                </Box>
                <Typography variant="caption" component="div" sx={{ mt: 1 }}>
                  New to proxying? See the Get Started tab for proxy setup.
                </Typography>
              </Box>
            ) : (
              <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
                No matching requests
              </Typography>
            )
          ) : (
            // Windowed like LogPanel: only the rows in (or near) the scroll
            // viewport — the ancestor Box above — are mounted, so the DOM stays
            // bounded regardless of how many requests were captured. Selection,
            // compare and bulk-select are all driven by the DATA arrays
            // (`filtered`, `selectedKeys`, `validCompareKeys`) keyed by item key,
            // not by mounted rows, so a selected row scrolled out of view keeps
            // its state. Guarded by `perf-domWeight.test.tsx`.
            <ProgressiveList
          pinnedToTail={follow}
              count={filtered.length}
              estimateSize={40}
              getKey={(i) => filtered[i]!.item.key}
              renderRow={(i) => {
                const { item, summary } = filtered[i]!;
                return (
                  <TrafficRow
                    itemKey={item.key}
                    summary={summary}
                    timestamp={item.timestamp}
                    // Memoised on the request reference, so this is a stable prop
                    // and the row stays skippable by React.memo.
                    graphql={graphqlOperationOf(item.value)}
                    // Oldest-first ordinal (matches RequestPanel), used only as a
                    // fallback when a row carries no timestamp. The timestamp is
                    // the primary label — see TrafficRow.
                    index={i + 1}
                    selected={
                      compareMode
                        ? validCompareKeys.includes(item.key)
                        : selectMode
                          ? selectedKeys.has(item.key)
                          : selectedKey === item.key
                    }
                    onSelect={handleRowSelect}
                    compareMode={compareMode}
                    compareChecked={validCompareKeys.includes(item.key)}
                    compareDisabled={validCompareKeys.length >= 2}
                    onCompareToggle={toggleCompareKey}
                    selectMode={selectMode}
                    selectChecked={selectedKeys.has(item.key)}
                    onSelectToggle={toggleSelectKey}
                    tabbable={item.key === tabbableKey}
                    onMoveFocus={handleMoveFocus}
                    onRowFocus={setRovingKey}
                  />
                );
              }}
            />
          )}
        </Box>
      </Paper>

      {/* Drag handle between master list and detail pane (side-by-side only). */}
      {resizableSplit && (
        <Box
          data-testid="traffic-master-resizer"
          {...masterWidth.getHandleProps()}
          sx={{
            flexShrink: 0,
            width: 8,
            mx: -0.5,
            borderRadius: 1,
            backgroundColor: 'transparent',
            transition: theme.transitions.create('background-color', {
              duration: theme.transitions.duration.shorter,
            }),
            '&:hover, &:focus-visible, &:active': {
              backgroundColor: theme.palette.primary.main,
              opacity: 0.55,
            },
            '&:focus-visible': { outline: 'none' },
            cursor: 'col-resize',
          }}
        />
      )}

      {/* Detail pane (hidden while picking requests to compare or bulk-select) */}
      {selectedEntry && !compareMode && !selectMode && (
        <Paper
          variant="outlined"
          sx={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            minWidth: 0,
          }}
        >
          <ErrorBoundary key={selectedEntry.item.key} label="detail pane">
            <DetailPane
              item={selectedEntry.item}
              summary={selectedEntry.summary}
              scriptedTurns={scriptedTurns}
              onCaptureAsMock={() => {
                void loadFullRowThen(selectedEntry.item, () => setCaptureDialogOpen(true));
              }}
              onReplay={() => setReplayDialogOpen(true)}
              onRepeat={() => {
                void loadFullRowThen(selectedEntry.item, () => setRepeatDialogOpen(true));
              }}
              onAddToDiffPool={handleAddSelectedToDiffPool}
              inDiffPool={selectedInDiffPool}
              unmatched={selectedEntry.unmatched}
            />
          </ErrorBoundary>
        </Paper>
      )}

      {/* Capture as mock dialog */}
      {selectedEntry && isCapturableTraffic(selectedEntry.summary.parsed) && (
        <CaptureAsMockDialog
          open={captureDialogOpen}
          onClose={() => setCaptureDialogOpen(false)}
          parsed={selectedEntry.summary.parsed}
          path={selectedEntry.summary.path ?? ''}
          connectionParams={connectionParams}
          itemValue={selectedEntry.item.value}
        />
      )}

      {/* Replay dialog — re-issue a captured request to its original target */}
      {selectedEntry && (
        <ReplayDialog
          open={replayDialogOpen}
          onClose={() => setReplayDialogOpen(false)}
          item={selectedEntry.item}
          connectionParams={connectionParams}
        />
      )}

      {/* Repeat Advanced dialog — re-issue a captured request N times with a
          bounded concurrency and inter-request delay (client-driven fan-out of
          the single-shot replay endpoint). Mount only while open so each run
          starts from fresh state. */}
      {selectedEntry && repeatDialogOpen && (
        <RepeatAdvancedDialog
          open
          onClose={() => setRepeatDialogOpen(false)}
          item={selectedEntry.item}
          connectionParams={connectionParams}
          onViewResults={(path) => setTrafficSearch(`path:${path}`)}
        />
      )}

      {/* Diff two picked requests, reusing the shared dialog + PUT /mockserver/diff endpoint.
          Mount only while open and key on the selection so the dialog seeds fresh inputs each time. */}
      {compareDiffPair && (
        <DiffRequestsDialog
          key={compareKeys.join('|')}
          open
          onClose={() => setCompareDiffPair(null)}
          connectionParams={connectionParams}
          initialExpected={compareDiffPair[0]}
          initialActual={compareDiffPair[1]}
        />
      )}

      {/* Diff Pool popover — the staged requests, each removable, with a Clear All
          and a two-pick "Diff Selected" that opens the same shared diff dialog. */}
      <Popover
        open={Boolean(poolAnchorEl)}
        anchorEl={poolAnchorEl}
        onClose={() => setPoolAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        slotProps={{ paper: { sx: { width: 360, maxWidth: '90vw' } } }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 1, borderBottom: 1, borderColor: 'divider' }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, flexGrow: 1 }}>
            Diff Pool ({diffPool.length})
          </Typography>
          <Button
            size="small"
            color="inherit"
            onClick={clearDiffPool}
            disabled={diffPool.length === 0}
            sx={{ fontSize: '0.7rem', textTransform: 'none' }}
          >
            Clear All
          </Button>
        </Box>
        <Box sx={{ maxHeight: 320, overflowY: 'auto' }}>
          {diffPool.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
              No requests staged. Use “Add to Diff Pool” in the detail pane.
            </Typography>
          ) : (
            diffPool.map((entry) => {
              const picked = poolPicks.includes(entry.key);
              return (
                <Box
                  key={entry.key}
                  sx={{ display: 'flex', alignItems: 'center', gap: 0.5, px: 1, py: 0.25, borderBottom: 1, borderColor: 'divider' }}
                >
                  <Checkbox
                    size="small"
                    checked={picked}
                    disabled={!picked && poolPicks.length >= 2}
                    onChange={() => togglePoolPick(entry.key)}
                    slotProps={{ input: { 'aria-label': `Pick ${entry.method ?? ''} ${entry.path ?? entry.key}` } }}
                    sx={{ p: 0.25, flexShrink: 0 }}
                  />
                  <Typography variant="caption" sx={{ fontWeight: 700, flexShrink: 0, minWidth: 40 }}>
                    {entry.method ?? '—'}
                  </Typography>
                  <Typography
                    variant="caption"
                    sx={{ fontFamily: monospaceFontFamily, flexGrow: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                    title={entry.path ?? undefined}
                  >
                    {entry.path ?? entry.key}
                  </Typography>
                  {entry.status !== null && (
                    <Chip
                      label={entry.status}
                      color={statusColor(entry.status)}
                      size="small"
                      sx={{ height: 18, fontSize: '0.6rem', flexShrink: 0, '& .MuiChip-label': { px: 0.6 } }}
                    />
                  )}
                  <Tooltip title="Remove from Diff Pool">
                    <IconButton
                      size="small"
                      onClick={() => removeFromDiffPool(entry.key)}
                      aria-label={`Remove ${entry.method ?? ''} ${entry.path ?? entry.key} from Diff Pool`}
                      sx={{ p: 0.25, flexShrink: 0 }}
                    >
                      <CloseIcon sx={{ fontSize: '0.9rem' }} />
                    </IconButton>
                  </Tooltip>
                </Box>
              );
            })
          )}
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 1, borderTop: 1, borderColor: 'divider' }}>
          <Typography variant="caption" color="text.secondary" sx={{ flexGrow: 1 }}>
            {poolPicks.length}/2 picked
          </Typography>
          <Button
            size="small"
            variant="contained"
            disabled={!canDiffPool}
            onClick={openPoolDiff}
            sx={{ fontSize: '0.7rem', textTransform: 'none' }}
          >
            Diff Selected
          </Button>
        </Box>
      </Popover>

      {/* Diff two pooled requests — same shared dialog + endpoint as the two-pick
          Compare flow, seeded from the pool selection. Keyed so it re-seeds per pair. */}
      {poolDiffPair && (
        <DiffRequestsDialog
          key={`pool:${poolPicks.join('|')}`}
          open
          onClose={() => setPoolDiffPair(null)}
          connectionParams={connectionParams}
          initialExpected={poolDiffPair[0]}
          initialActual={poolDiffPair[1]}
        />
      )}

      {/* Explain unmatched dialog — opened from the "unmatched" header badge.
          Queries PUT /mockserver/explainUnmatched for the closest expectations. */}
      <ExplainUnmatchedDialog
        open={explainOpen}
        onClose={() => setExplainOpen(false)}
        connectionParams={connectionParams}
      />

      {/* Promote recordings to mocks — bulk-activate expectations from recorded
          (proxied) traffic via PUT /mockserver/recordings/promote. Mount only
          while open, keyed on the prefill so it re-seeds when the search changes. */}
      {promoteOpen && (
        <PromoteRecordingsDialog
          key={`${promotePrefill.method ?? ''}|${promotePrefill.path ?? ''}`}
          open
          onClose={() => setPromoteOpen(false)}
          connectionParams={connectionParams}
          recordedCount={promotableCount}
          initialFilter={promotePrefill}
        />
      )}

      <ConfirmDialog
        open={bulkClearConfirm}
        title={`Clear ${validSelectedKeys.length} request${validSelectedKeys.length === 1 ? '' : 's'}?`}
        message={`Remove the ${validSelectedKeys.length} selected request${validSelectedKeys.length === 1 ? '' : 's'} from the log. Identical requests captured alongside them may also be cleared. Expectations are kept. This cannot be undone.`}
        confirmLabel="Clear selected"
        onConfirm={() => { void handleBulkClear(); }}
        onClose={() => setBulkClearConfirm(false)}
      />

    </Box>
  );
}
