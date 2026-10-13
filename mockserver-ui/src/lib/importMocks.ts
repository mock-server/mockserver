/**
 * Client helpers for bulk-importing expectations into MockServer from various
 * formats:
 *
 * - **Expectation JSON**: `PUT /mockserver/expectation` (single or array)
 * - **HAR (HTTP Archive)** and **Postman collection**: `PUT /mockserver/import`
 *   with a `?format=har|postman` query parameter (raw collection JSON body).
 * - **Recording (NDJSON)**: `PUT /mockserver/import?format=recording` reloads a
 *   persisted NDJSON archive of recorded request/response traffic (the
 *   disk-offload capture) back into the event log — from the request body, or
 *   from the server's configured `persistedRecordedRequestsPath` via
 *   `?format=recording&source=disk`.
 *
 * OpenAPI and WSDL have their own dedicated libs (openapiImport.ts, wsdlImport.ts).
 */
import { buildBaseUrl } from './mcpClient';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import { lintWarningCount } from './lint';

/** Collection formats accepted by the PUT /mockserver/import endpoint. */
export type ImportCollectionFormat = 'har' | 'postman';

/** The expectations an import created, and the lint warnings the server counted for them. */
export interface ImportedExpectations {
  created: unknown[];
  /** `x-mockserver-lint-warnings`, or 0 when the header is absent. */
  lintWarnings: number;
}

/**
 * Import raw expectation JSON (a single object or JSON array) via
 * `PUT /mockserver/expectation`. Returns the created expectations.
 *
 * @throws Error with the server's message on a non-2xx response.
 */
export async function importExpectationJson(
  params: ConnectionParams,
  jsonPayload: string,
): Promise<ImportedExpectations> {
  const parsed: unknown = JSON.parse(jsonPayload);
  const body = Array.isArray(parsed) ? parsed : [parsed];
  const res = await fetch(`${buildBaseUrl(params)}/mockserver/expectation`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error((await res.text()) || `HTTP ${res.status} ${res.statusText}`);
  }
  const created = (await res.json()) as unknown;
  return { created: Array.isArray(created) ? created : [], lintWarnings: lintWarningCount(res) };
}

/**
 * Import a recorded-traffic collection (HAR HTTP Archive or Postman collection)
 * via `PUT /mockserver/import?format=<format>`. The server converts the
 * collection into expectations and returns them (201).
 *
 * @throws Error with the server's message on a non-2xx response (e.g. 400 on
 *         parse failure or an unrecognised collection).
 */
export async function importCollection(
  params: ConnectionParams,
  payload: string,
  format: ImportCollectionFormat,
): Promise<unknown[]> {
  const res = await fetch(`${buildBaseUrl(params)}/mockserver/import?format=${format}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: payload,
  });
  if (!res.ok) {
    throw new Error((await res.text()) || `HTTP ${res.status} ${res.statusText}`);
  }
  const created = (await res.json()) as unknown;
  return Array.isArray(created) ? created : [];
}

/**
 * Result of reloading a recorded-traffic NDJSON archive.
 */
export interface RecordingImportResult {
  /** Number of request/response pairs reloaded into the event log. */
  count: number;
  /** Number of crash-truncated / malformed NDJSON lines the server skipped. */
  skipped: number;
}

/**
 * Reload a persisted NDJSON archive of recorded request/response traffic via
 * `PUT /mockserver/import?format=recording`.
 *
 * - When `ndjson` has content it is sent as the request body.
 * - When `fromDisk` is true (or `ndjson` is blank) the request is sent with
 *   `&source=disk` and an empty body, so the server reads its configured
 *   `persistedRecordedRequestsPath` archive instead.
 *
 * The server responds `201` with a JSON array of the reloaded pairs and, when
 * any malformed lines were skipped, an `x-mockserver-recorded-requests-skipped`
 * header carrying the skipped-line count.
 *
 * @throws Error with the server's message on a non-2xx response (e.g. 400 when
 *         no disk archive exists or the NDJSON cannot be parsed).
 */
export async function importRecording(
  params: ConnectionParams,
  ndjson: string | null,
  opts: { fromDisk?: boolean } = {},
): Promise<RecordingImportResult> {
  const fromDisk = opts.fromDisk === true || !ndjson || ndjson.trim() === '';
  const url = `${buildBaseUrl(params)}/mockserver/import?format=recording${fromDisk ? '&source=disk' : ''}`;
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: fromDisk ? '' : ndjson!,
  });
  if (!res.ok) {
    throw new Error((await res.text()) || `HTTP ${res.status} ${res.statusText}`);
  }
  const skippedHeader = res.headers?.get?.('x-mockserver-recorded-requests-skipped');
  const skipped = skippedHeader != null ? Number(skippedHeader) : 0;
  const reloaded = (await res.json()) as unknown;
  return {
    count: Array.isArray(reloaded) ? reloaded.length : 0,
    skipped: Number.isFinite(skipped) ? skipped : 0,
  };
}
