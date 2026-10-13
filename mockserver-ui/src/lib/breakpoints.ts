/**
 * Client for MockServer's breakpoint matcher management endpoints.
 * Registration, list, remove, and clear operations for breakpoint matchers.
 * Resolution is done via the callback WebSocket, not REST endpoints.
 */
import { buildBaseUrl } from './mcpClient';
import type { ConnectionParams } from '../hooks/useConnectionParams';

async function ensureOk(res: Response): Promise<void> {
  if (res.ok) return;
  let message = `HTTP ${res.status} ${res.statusText}`;
  try {
    const body = (await res.json()) as { error?: unknown };
    if (body && typeof body.error === 'string') message = body.error;
  } catch {
    // non-JSON body — keep the status-line message
  }
  throw new Error(message);
}

// ---------------------------------------------------------------------------
// Breakpoint matcher management (registration, list, remove, clear)
// ---------------------------------------------------------------------------

export type MatcherPhase = 'REQUEST' | 'RESPONSE' | 'RESPONSE_STREAM' | 'INBOUND_STREAM';

export interface BreakpointMatcherEntry {
  id: string;
  httpRequest?: Record<string, unknown>;
  phases: MatcherPhase[];
  clientId?: string;
  /** Optional Nth-hit / skip-count: pause only after this many matching hits. */
  skipCount?: number;
  /** Inclusive response status range; checked at the RESPONSE phase only. */
  responseStatusCodeMin?: number;
  responseStatusCodeMax?: number;
}

/** An inclusive response status range; either bound may be open. */
export interface StatusRange {
  min?: number;
  max?: number;
}

/** A matcher's status range as shown in the matcher list, or null when it has none. */
export function formatStatusRange(min?: number, max?: number): string | null {
  if (min != null && max != null) return min === max ? `status ${min}` : `status ${min}-${max}`;
  if (min != null) return `status >=${min}`;
  if (max != null) return `status <=${max}`;
  return null;
}

export interface BreakpointMatcherListResponse {
  matchers: BreakpointMatcherEntry[];
}

function matcherEndpoint(params: ConnectionParams): string {
  return `${buildBaseUrl(params)}/mockserver/breakpoint/matcher`;
}

/** Register a breakpoint matcher. Returns the server-assigned entry. */
export async function registerBreakpointMatcher(
  params: ConnectionParams,
  httpRequest: Record<string, unknown>,
  phases: MatcherPhase[],
  clientId?: string,
  skipCount?: number,
  statusRange?: StatusRange,
): Promise<{ id: string; phases: MatcherPhase[] }> {
  const body: Record<string, unknown> = { httpRequest, phases };
  if (clientId) body.clientId = clientId;
  if (typeof skipCount === 'number' && skipCount > 0) body.skipCount = skipCount;
  if (statusRange?.min != null) body.responseStatusCodeMin = statusRange.min;
  if (statusRange?.max != null) body.responseStatusCodeMax = statusRange.max;
  const res = await fetch(matcherEndpoint(params), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  await ensureOk(res);
  return res.json() as Promise<{ id: string; phases: MatcherPhase[] }>;
}

/** List all registered breakpoint matchers. */
export async function listBreakpointMatchers(
  params: ConnectionParams,
  signal?: AbortSignal,
): Promise<BreakpointMatcherListResponse> {
  const res = await fetch(`${matcherEndpoint(params)}s`, { signal });
  await ensureOk(res);
  return res.json() as Promise<BreakpointMatcherListResponse>;
}

/** Remove a breakpoint matcher by id. */
export async function removeBreakpointMatcher(
  params: ConnectionParams,
  id: string,
): Promise<void> {
  const res = await fetch(`${matcherEndpoint(params)}/remove`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id }),
  });
  await ensureOk(res);
}

/** Clear all breakpoint matchers. */
export async function clearBreakpointMatchers(
  params: ConnectionParams,
): Promise<void> {
  const res = await fetch(`${matcherEndpoint(params)}/clear`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
  });
  await ensureOk(res);
}
