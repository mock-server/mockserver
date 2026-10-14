/**
 * Preview and apply a fix that `PUT /mockserver/explainUnmatched` suggested for an expectation. The server only
 * suggests the patch; applying it is a separate, explicit step that sends the patched expectation to the ordinary
 * `PUT /mockserver/expectation`, which updates the expectation with the same id.
 */
import type { ConnectionParams } from '../hooks/useConnectionParams';
import { fetchExpectation } from './expectations';
import { registerExpectation } from './generateStub';
import { applyJsonPatch, type JsonPatchOperation } from './jsonPatch';

export interface ExpectationFixPreview {
  /** The expectation as it is registered now. */
  before: Record<string, unknown>;
  /** The expectation with the patch applied, exactly as Apply would send it. */
  after: Record<string, unknown>;
}

/**
 * Reads the expectation as it is registered now and applies the patch to a copy. Throws
 * `JsonPatchTestFailedError` when the expectation changed since the patch was suggested.
 */
export async function previewExpectationFix(
  params: ConnectionParams,
  expectationId: string,
  patch: readonly JsonPatchOperation[],
  signal?: AbortSignal,
): Promise<ExpectationFixPreview> {
  const before = await fetchExpectation(params, expectationId, signal);
  return { before, after: applyJsonPatch(before, patch) };
}

export interface AppliedExpectationFix {
  /** The patched expectation, as sent. */
  expectation: Record<string, unknown>;
  /** The lint warning count the server reported for the updated expectation. */
  lintWarnings: number;
}

/**
 * Re-reads the expectation, so a change made after the preview makes the patch's test operations fail rather
 * than be overwritten, and sends the patched expectation to `PUT /mockserver/expectation`.
 */
export async function applyExpectationFix(
  params: ConnectionParams,
  expectationId: string,
  patch: readonly JsonPatchOperation[],
): Promise<AppliedExpectationFix> {
  const { after } = await previewExpectationFix(params, expectationId, patch);
  const lintWarnings = await registerExpectation(params, after);
  return { expectation: after, lintWarnings };
}

export interface PatchChange {
  /** The changed field, relative to the expectation's `httpRequest`, e.g. `headers.X-Tenant`. */
  field: string;
  kind: 'add' | 'remove' | 'replace';
  before?: unknown;
  after?: unknown;
}

/** One entry per changed value, each with the value its `test` operation guards as `before`. */
export function describePatch(patch: readonly JsonPatchOperation[]): PatchChange[] {
  const tested = new Map<string, unknown>();
  const changes: PatchChange[] = [];
  for (const operation of patch) {
    if (operation.op === 'test') {
      tested.set(operation.path, operation.value);
      continue;
    }
    const field = operation.path
      .replace(/^\/httpRequest(\/|$)/, '')
      .split('/')
      .map((token) => token.replace(/~1/g, '/').replace(/~0/g, '~'))
      .join('.');
    changes.push({
      field: field || 'httpRequest',
      kind: operation.op,
      ...(tested.has(operation.path) ? { before: tested.get(operation.path) } : {}),
      ...(operation.op === 'remove' ? {} : { after: operation.value }),
    });
  }
  return changes;
}
