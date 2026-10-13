/**
 * Client for MockServer's PUT /mockserver/explainUnmatched — for each recent request that matched
 * no expectation, the server returns the closest expectations ranked by how many match fields they
 * differ on, with per-field differences and remediation hints. Answers "why did nothing match?".
 */
import { buildBaseUrl } from './mcpClient';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import type { JsonPatchOperation } from './jsonPatch';

/**
 * A suggested fix the server computed (only when asked with `suggestFixes`): a JSON patch of the expectation as
 * `PUT /mockserver/retrieve?type=active_expectations` returns it. The server never applies it.
 */
export interface SuggestedFix {
  patch: JsonPatchOperation[];
  patchedFields: string[];
  unpatchedFields: { field: string; reason: string }[];
  /** Whether the patched expectation matches the recorded request (checked on the server). */
  matchesAfterPatch: boolean;
}

export interface ClosestExpectation {
  expectationId?: string;
  expectationPath?: string;
  expectationMethod?: string;
  matches: boolean;
  matchedFieldCount: number;
  totalFieldCount: number;
  differingFieldCount: number;
  differences?: Record<string, string[]>;
  remediation?: Record<string, string>;
  suggestedFix?: SuggestedFix;
}

export interface UnmatchedRequest {
  timestamp?: string;
  method: string;
  path: string;
  totalExpectationsEvaluated: number;
  closestExpectations: ClosestExpectation[];
}

export interface ExplainUnmatchedResult {
  correlationId: string;
  timestamp: string;
  unmatchedRequestCount: number;
  truncated: boolean;
  unmatchedRequests: UnmatchedRequest[];
}

export async function explainUnmatched(
  params: ConnectionParams,
  limit = 10,
  signal?: AbortSignal,
  suggestFixes = false,
): Promise<ExplainUnmatchedResult> {
  const res = await fetch(`${buildBaseUrl(params)}/mockserver/explainUnmatched`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(suggestFixes ? { limit, suggestFixes } : { limit }),
    signal,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `explainUnmatched failed (HTTP ${res.status} ${res.statusText})`);
  }
  const body = (await res.json()) as Partial<ExplainUnmatchedResult>;
  return {
    correlationId: body.correlationId ?? '',
    timestamp: body.timestamp ?? '',
    unmatchedRequestCount: body.unmatchedRequestCount ?? 0,
    truncated: body.truncated ?? false,
    unmatchedRequests: Array.isArray(body.unmatchedRequests) ? body.unmatchedRequests : [],
  };
}
