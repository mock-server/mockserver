/**
 * Client for MockServer's OpenAPI import endpoint (`PUT /mockserver/openapi`).
 * Generates expectations from an OpenAPI v3 spec (URL or inline JSON/YAML).
 * Import is incremental: re-importing the same spec (by title) updates the
 * generated expectations in place and prunes operations no longer present.
 */
import { buildBaseUrl } from './mcpClient';
import type { ConnectionParams } from '../hooks/useConnectionParams';

/**
 * A named example a user can pick for one operation/response of a spec.
 * `statusCode` is the response code the named examples were found under (e.g.
 * "200"); `exampleNames` are the keys of that response's `content.*.examples`
 * map — exactly the values the server accepts as `exampleName`.
 */
export interface OperationExamples {
  operationId: string;
  statusCode: string;
  exampleNames: string[];
}

/**
 * The user's chosen example per operation, keyed by operationId. The value pairs
 * the response `statusCode` the example belongs to with the chosen `exampleName`.
 */
export type ExampleSelections = Record<string, { statusCode: string; exampleName: string }>;

const HTTP_METHODS = ['get', 'put', 'post', 'delete', 'patch', 'options', 'head', 'trace'];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * Discover the named examples a spec exposes, so the UI can offer a picker.
 *
 * Only inline JSON specs are inspected — a URL or YAML payload is sent to the
 * server untouched and yields an empty list (the picker is hidden). For each
 * operation that declares two or more named examples on the FIRST media type of
 * a response body, an {@link OperationExamples} entry is returned (the first
 * response status with named examples wins). Operations with zero or one named
 * example are omitted, since there is nothing meaningful to choose.
 *
 * Only the first media type is inspected because the server's OpenAPIConverter
 * itself selects the first content entry when building the example response — a
 * named example declared on a second media type cannot be honoured, so offering
 * it in the picker would be a false promise.
 *
 * Limitation: examples reached via a `$ref` (e.g. a `$ref`-ed response or a
 * `$ref`-ed example object) are not resolved here, since this walks the raw
 * pasted JSON rather than a fully-dereferenced spec. Such operations simply show
 * no picker; the server still applies its own default example for them.
 */
export function discoverNamedExamples(specOrUrl: string): OperationExamples[] {
  const trimmed = specOrUrl.trim();
  if (!trimmed.startsWith('{')) {
    return [];
  }
  let spec: unknown;
  try {
    spec = JSON.parse(trimmed);
  } catch {
    return [];
  }
  if (!isRecord(spec) || !isRecord(spec.paths)) {
    return [];
  }
  const results: OperationExamples[] = [];
  for (const pathItem of Object.values(spec.paths)) {
    if (!isRecord(pathItem)) {
      continue;
    }
    for (const method of HTTP_METHODS) {
      const operation = pathItem[method];
      if (!isRecord(operation) || typeof operation.operationId !== 'string') {
        continue;
      }
      const found = firstResponseWithNamedExamples(operation.responses);
      if (found && found.exampleNames.length >= 2) {
        results.push({ operationId: operation.operationId, ...found });
      }
    }
  }
  return results;
}

/**
 * Discover every operationId the server will use for an inline JSON spec, in
 * document order — including the ids it SYNTHESIZES for operations that declare
 * no explicit `operationId`.
 *
 * Mirrors {@link discoverNamedExamples} in input handling: only an inline JSON
 * payload is inspected (a URL or YAML payload is sent untouched and yields an
 * empty list). Unlike that function this returns ALL operations, not only those
 * with two or more named examples.
 *
 * This is needed because the server treats `operationsAndResponses` as an
 * operation FILTER (OpenAPIConverter only emits an expectation for an operation
 * whose id is a key of that map). So when the user pins a named example for one
 * operation, every other operation must still appear in the map — with a
 * default-preserving value — or it would be silently dropped from the import.
 *
 * For an operation WITHOUT an explicit `operationId`, the server synthesizes one
 * (see `OpenAPIParser.synthesizeOperationIds`) of the exact form
 * `"<UPPERCASE_METHOD> <path>"`, e.g. `"GET /pets"`. We replicate that format
 * byte-for-byte (see {@link synthesizeOperationId}) so the synthesized key is a
 * key of `operationsAndResponses` too — otherwise an id-less operation would be
 * filtered out and silently dropped whenever ANY example is pinned.
 *
 * Limitation: the server disambiguates colliding ids by appending ` (n)` (see
 * `OpenAPIParser.ensureUnique`). We do not replicate that here — a synthesized
 * id can only collide with an authored id that happens to equal `"METHOD path"`,
 * or with another synthesized id for the same method+path (impossible within one
 * pathItem). In the unlikely collision case the server renames its synthesized
 * id and that operation falls back to the server default — never dropped — so
 * the data-loss path stays closed.
 */
export function discoverAllOperationIds(specOrUrl: string): string[] {
  const trimmed = specOrUrl.trim();
  if (!trimmed.startsWith('{')) {
    return [];
  }
  let spec: unknown;
  try {
    spec = JSON.parse(trimmed);
  } catch {
    return [];
  }
  if (!isRecord(spec) || !isRecord(spec.paths)) {
    return [];
  }
  const operationIds: string[] = [];
  for (const [path, pathItem] of Object.entries(spec.paths)) {
    if (!isRecord(pathItem)) {
      continue;
    }
    for (const method of HTTP_METHODS) {
      const operation = pathItem[method];
      if (!isRecord(operation)) {
        continue;
      }
      const operationId =
        typeof operation.operationId === 'string' && operation.operationId.trim() !== ''
          ? operation.operationId
          : synthesizeOperationId(method, path);
      operationIds.push(operationId);
    }
  }
  return operationIds;
}

/**
 * Synthesize the operationId the server generates for an operation that declares
 * no explicit `operationId`. Matches `OpenAPIParser.synthesizeOperationIds`
 * byte-for-byte: the UPPERCASE HTTP method, a single space, then the path —
 * e.g. `synthesizeOperationId('get', '/pets')` returns `"GET /pets"`.
 */
function synthesizeOperationId(method: string, path: string): string {
  return `${method.toUpperCase()} ${path}`;
}

/**
 * Find the first response status whose first media type declares named examples.
 * Only the first media type is considered, matching the server's content
 * selection (see {@link discoverNamedExamples}).
 */
function firstResponseWithNamedExamples(
  responses: unknown,
): { statusCode: string; exampleNames: string[] } | null {
  if (!isRecord(responses)) {
    return null;
  }
  for (const [statusCode, response] of Object.entries(responses)) {
    if (!isRecord(response) || !isRecord(response.content)) {
      continue;
    }
    const firstMediaType = Object.values(response.content)[0];
    if (isRecord(firstMediaType) && isRecord(firstMediaType.examples)) {
      const exampleNames = Object.keys(firstMediaType.examples);
      if (exampleNames.length > 0) {
        return { statusCode, exampleNames };
      }
    }
  }
  return null;
}

/**
 * Import an OpenAPI spec. `specOrUrl` may be a URL or an inline JSON/YAML spec.
 * Returns the created/updated expectations and the spec's load problems: places
 * where a spec that still loaded breaks the OpenAPI rules.
 *
 * `exampleSelections` optionally pins a named example per operation. Each entry
 * is sent to the server as an `operationsAndResponses` value of the form
 * `{ statusCode, exampleName }`, so the generated response uses that example.
 *
 * IMPORTANT: the server treats `operationsAndResponses` as an operation FILTER —
 * `OpenAPIConverter` only generates an expectation for operations whose id is a
 * key of that map. So when one or more examples are pinned, every OTHER operation
 * in the spec must still be present in the map with a default-preserving value
 * (an empty-string entry, which the converter resolves to the operation's first
 * response and no named example) — otherwise those operations would be silently
 * dropped and only the pinned one(s) imported. When no example is pinned the map
 * is omitted entirely, leaving the server to import every operation with its
 * defaults (unchanged behaviour).
 *
 * @throws Error with the server's message on a non-2xx response.
 */
export async function importOpenApi(
  params: ConnectionParams,
  specOrUrl: string,
  exampleSelections?: ExampleSelections,
): Promise<OpenApiImportResult> {
  const expectation: Record<string, unknown> = { specUrlOrPayload: specOrUrl };
  if (exampleSelections && Object.keys(exampleSelections).length > 0) {
    // Default-preserving entry for non-pinned operations. The converter reads a
    // string value as the response status key; an empty string is blank, so it
    // selects the first defined response and applies no named example — exactly
    // the server default for an unspecified operation.
    const operationsAndResponses: Record<string, string | { statusCode: string; exampleName: string }> = {};
    for (const operationId of discoverAllOperationIds(specOrUrl)) {
      operationsAndResponses[operationId] = '';
    }
    for (const [operationId, selection] of Object.entries(exampleSelections)) {
      operationsAndResponses[operationId] = {
        statusCode: selection.statusCode,
        exampleName: selection.exampleName,
      };
    }
    expectation.operationsAndResponses = operationsAndResponses;
  }
  const body = JSON.stringify([expectation]);
  const res = await fetch(`${buildBaseUrl(params)}/mockserver/openapi?includeLoadProblems=true`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body,
  });
  if (!res.ok) {
    throw new Error((await res.text()) || `HTTP ${res.status} ${res.statusText}`);
  }
  return parseImportResponse((await res.json()) as unknown);
}

/**
 * One problem in a spec that loaded but breaks the OpenAPI rules, which may stop
 * parts of it working as expected. `location` is a JSON pointer such as
 * `#/paths/~1pets/get`, absent when the parser named no location.
 */
export interface OpenApiLoadProblem {
  specIndex?: number;
  kind: string;
  kindLabel: string;
  location?: string;
  description: string;
  hint?: string;
}

export interface OpenApiImportResult {
  expectations: unknown[];
  loadProblems: OpenApiLoadProblem[];
}

/**
 * Reads `{ expectations, loadProblems }`, or a bare array of expectations from a
 * server that predates load problems.
 */
function parseImportResponse(body: unknown): OpenApiImportResult {
  if (Array.isArray(body)) {
    return { expectations: body, loadProblems: [] };
  }
  if (!isRecord(body)) {
    return { expectations: [], loadProblems: [] };
  }
  return {
    expectations: Array.isArray(body.expectations) ? body.expectations : [],
    loadProblems: Array.isArray(body.loadProblems) ? body.loadProblems.filter(isLoadProblem) : [],
  };
}

function isLoadProblem(value: unknown): value is OpenApiLoadProblem {
  return isRecord(value) && typeof value.description === 'string' && typeof value.kindLabel === 'string';
}
