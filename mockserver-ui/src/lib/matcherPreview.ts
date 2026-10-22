/**
 * Client-side matcher preview for the dashboard "matcher test playground" (WS5.5).
 *
 * MockServer has no server endpoint that evaluates a *candidate* (not-yet-registered)
 * expectation against a hypothetical request — `PUT /mockserver/debugMismatch` only
 * tests against expectations that are already registered, and we deliberately do not
 * register-then-deregister (that mutates shared server state and races with live
 * traffic). So this module re-implements MockServer's request-matching for the common
 * matcher types entirely on the client, purely as a *preview*. It is intentionally a
 * subset and is always labelled as such in the UI.
 *
 * Supported (high-fidelity) matcher fields:
 *   - method  — exact, regex, NottableString `!` negation
 *   - path    — exact, regex, NottableString `!` negation
 *   - queryStringParameters — key presence + value (exact / regex / negation)
 *   - headers — key presence + value (exact / regex / negation), case-insensitive keys
 *   - body    — STRING (exact/SUB_STRING), REGEX, and JSON "contains" (non-strict)
 *
 * Anything the expectation requires that this engine does not understand is reported as
 * an `unsupported` field so the user is never silently told "matches" when the real
 * server might disagree.
 */

export type FieldVerdict = 'match' | 'mismatch' | 'unsupported';

export interface FieldResult {
  /** Human field name, e.g. "method", "path", `header "Accept"`. */
  field: string;
  verdict: FieldVerdict;
  /** What the matcher required (display string). */
  expected: string;
  /** What the sample request supplied (display string). */
  actual: string;
  /** One-line explanation, present for mismatch/unsupported. */
  reason?: string;
}

export interface PreviewResult {
  /** True only when every evaluated field matched AND no field was unsupported. */
  matches: boolean;
  /**
   * True when at least one matcher field could not be evaluated by this client
   * preview. When set, `matches` is forced to false and the UI must warn that the
   * verdict is incomplete — the real server may decide differently.
   */
  hasUnsupported: boolean;
  results: FieldResult[];
}

/** The hypothetical request the user types into the playground. */
export interface SampleRequest {
  method: string;
  path: string;
  /** "Name: value" per line. */
  headers: string;
  /** "name=value" per line. */
  queryString: string;
  body: string;
}

// ---------------------------------------------------------------------------
// NottableString handling
// ---------------------------------------------------------------------------

interface Nottable {
  value: string;
  not: boolean;
}

/**
 * Sentinel returned by {@link toNottable} when the matcher carries a constraint the
 * client preview cannot evaluate (currently the JSON-schema NottableString form
 * `{ "schema": { ... } }`). It must NOT be silently dropped: doing so would leave an
 * empty matcher list that {@link evaluateKeyValues} would treat as a pure key-presence
 * check and (wrongly) report WOULD MATCH even when the server's schema would reject the
 * value. Callers must surface it as an `'unsupported'` field instead.
 */
export const UNSUPPORTED_MATCHER = Symbol('unsupported-matcher');

/**
 * Normalise the several shapes MockServer accepts for a single string matcher into
 * `{ value, not }`. Accepts a bare string (optionally with a leading `!` to negate),
 * or a NottableString object `{ value, not }` / `{ not, value }`. Returns `null` when
 * the input carries no constraint (absent / empty), or {@link UNSUPPORTED_MATCHER} when
 * the matcher is a form the preview cannot evaluate (e.g. JSON schema).
 */
export function toNottable(raw: unknown): Nottable | null | typeof UNSUPPORTED_MATCHER {
  if (raw == null) return null;
  if (typeof raw === 'string') {
    if (raw.length === 0) return null;
    if (raw.startsWith('!')) return { value: raw.slice(1), not: true };
    return { value: raw, not: false };
  }
  if (typeof raw === 'object') {
    const obj = raw as Record<string, unknown>;
    // JSON-schema NottableString form `{ "schema": { ... } }` (optionally with `not`):
    // the server validates the actual value against the schema, which this preview
    // cannot replicate — never claim a match. Flag as unsupported.
    if ('schema' in obj && obj['schema'] != null) {
      return UNSUPPORTED_MATCHER;
    }
    // Date-time form `{ "after": "now -1 hours", ... }`: a date comparison, not a string match.
    if ('before' in obj || 'after' in obj || 'equalToDateTime' in obj) {
      return UNSUPPORTED_MATCHER;
    }
    const value = obj['value'];
    if (typeof value === 'string') {
      return { value, not: obj['not'] === true };
    }
  }
  return null;
}

/**
 * Does `actual` satisfy the NottableString matcher? A matcher value is treated as a
 * regex when it is not a plain literal: we first try an exact (case-sensitive) compare,
 * and if that fails we try a full-string regex match. Invalid regexes fall back to the
 * exact compare result (so a literal like `a(b` still behaves literally).
 */
function nottableMatches(matcher: Nottable, actual: string): boolean {
  const exact = matcher.value === actual;
  let matched = exact;
  if (!exact) {
    try {
      matched = new RegExp(`^(?:${matcher.value})$`).test(actual);
    } catch {
      matched = false;
    }
  }
  return matcher.not ? !matched : matched;
}

function describe(matcher: Nottable): string {
  return matcher.not ? `not(${matcher.value})` : matcher.value;
}

/** Build an `'unsupported'` FieldResult for a top-level string field (method/path). */
function unsupportedStringField(field: string): FieldResult {
  return {
    field,
    verdict: 'unsupported',
    expected: '(set)',
    actual: '(not evaluated)',
    reason: `${field} matcher uses a JSON schema, which the client preview cannot evaluate`,
  };
}

// ---------------------------------------------------------------------------
// Sample request parsing
// ---------------------------------------------------------------------------

/** Parse "Name: value" lines into ordered pairs (later duplicates kept). */
export function parseHeaderLines(text: string): { name: string; value: string }[] {
  return text
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l.length > 0)
    .map((line) => {
      const idx = line.indexOf(':');
      if (idx < 0) return { name: line, value: '' };
      return { name: line.slice(0, idx).trim(), value: line.slice(idx + 1).trim() };
    });
}

/** Parse "name=value" lines into ordered pairs. */
export function parseQueryLines(text: string): { name: string; value: string }[] {
  return text
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l.length > 0)
    .map((line) => {
      const idx = line.indexOf('=');
      if (idx < 0) return { name: line, value: '' };
      return { name: line.slice(0, idx).trim(), value: line.slice(idx + 1).trim() };
    });
}

// ---------------------------------------------------------------------------
// Key/value (headers, query) matching
// ---------------------------------------------------------------------------

/**
 * A normalised header/query-param requirement. `unsupported`, when set, means the
 * preview cannot evaluate this key (negated key, or a value matcher in a form — e.g.
 * JSON schema — the preview does not understand) and the caller must emit an
 * `'unsupported'` field rather than silently treat it as a presence check.
 */
interface KeyRequirement {
  key: string;
  matchers: Nottable[];
  unsupported?: string;
}

/**
 * Collapse a list of value-matcher results from {@link toNottable} into either a clean
 * `Nottable[]` or an unsupported reason if any value used a form the preview can't
 * evaluate (e.g. JSON schema). `null` entries (no constraint) are dropped.
 */
function collectValueMatchers(
  values: unknown[],
): { matchers: Nottable[] } | { unsupported: string } {
  const matchers: Nottable[] = [];
  for (const v of values) {
    const m = toNottable(v);
    if (m === UNSUPPORTED_MATCHER) {
      return { unsupported: 'value uses a JSON schema or date-time matcher, which the client preview cannot evaluate' };
    }
    if (m) matchers.push(m);
  }
  return { matchers };
}

/**
 * MockServer represents headers/query params as `{ name: [values...] }` or the legacy
 * `[{ name, values }]` array form. Normalise both to a flat list of {@link KeyRequirement}.
 * Negated keys and JSON-schema value matchers are surfaced as `unsupported` so the
 * preview never reports a false match (it cannot replicate key-negation or schema
 * semantics, which the server applies).
 */
function normaliseKeyValues(raw: unknown): KeyRequirement[] {
  const out: KeyRequirement[] = [];
  if (raw == null || typeof raw !== 'object') return out;

  if (Array.isArray(raw)) {
    for (const entry of raw) {
      if (entry && typeof entry === 'object') {
        const e = entry as Record<string, unknown>;
        const key = toNottable(e['name']);
        if (!key || key === UNSUPPORTED_MATCHER) continue;
        if (key.not) {
          out.push({ key: key.value, matchers: [], unsupported: 'negated key matching is not supported by the client preview' });
          continue;
        }
        const values = e['values'];
        if (Array.isArray(values)) {
          const collected = collectValueMatchers(values);
          if ('unsupported' in collected) {
            out.push({ key: key.value, matchers: [], unsupported: collected.unsupported });
          } else {
            out.push({ key: key.value, matchers: collected.matchers });
          }
        } else {
          out.push({ key: key.value, matchers: [] });
        }
      }
    }
    return out;
  }

  for (const [rawKey, values] of Object.entries(raw as Record<string, unknown>)) {
    // Object-form keys may themselves be notted, e.g. `"!X-Internal"`. Key negation
    // semantics are non-trivial; flag rather than mis-evaluate (the `!` would otherwise
    // be treated as a literal part of the header name).
    if (rawKey.startsWith('!')) {
      out.push({ key: rawKey.slice(1), matchers: [], unsupported: 'negated key matching is not supported by the client preview' });
      continue;
    }
    const collected = collectValueMatchers(Array.isArray(values) ? values : [values]);
    if ('unsupported' in collected) {
      out.push({ key: rawKey, matchers: [], unsupported: collected.unsupported });
    } else {
      out.push({ key: rawKey, matchers: collected.matchers });
    }
  }
  return out;
}

function evaluateKeyValues(
  label: 'header' | 'query parameter',
  raw: unknown,
  actualPairs: { name: string; value: string }[],
  caseInsensitiveKey: boolean,
): FieldResult[] {
  const required = normaliseKeyValues(raw);
  const results: FieldResult[] = [];

  for (const { key, matchers, unsupported } of required) {
    if (unsupported) {
      results.push({
        field: `${label} "${key}"`,
        verdict: 'unsupported',
        expected: '(set)',
        actual: '(not evaluated)',
        reason: unsupported,
      });
      continue;
    }
    const keyEq = (a: string) =>
      caseInsensitiveKey ? a.toLowerCase() === key.toLowerCase() : a === key;
    const present = actualPairs.filter((p) => keyEq(p.name));
    const expectedDisplay =
      matchers.length === 0 ? '(present)' : matchers.map(describe).join(', ');

    if (present.length === 0) {
      results.push({
        field: `${label} "${key}"`,
        verdict: 'mismatch',
        expected: expectedDisplay,
        actual: '(absent)',
        reason: `request has no ${label} named "${key}"`,
      });
      continue;
    }

    if (matchers.length === 0) {
      results.push({
        field: `${label} "${key}"`,
        verdict: 'match',
        expected: '(present)',
        actual: present.map((p) => p.value).join(', '),
      });
      continue;
    }

    // Every required value matcher must be satisfied by at least one supplied value.
    const unsatisfied = matchers.filter(
      (m) => !present.some((p) => nottableMatches(m, p.value)),
    );
    if (unsatisfied.length === 0) {
      results.push({
        field: `${label} "${key}"`,
        verdict: 'match',
        expected: expectedDisplay,
        actual: present.map((p) => p.value).join(', '),
      });
    } else {
      results.push({
        field: `${label} "${key}"`,
        verdict: 'mismatch',
        expected: expectedDisplay,
        actual: present.map((p) => p.value).join(', '),
        reason: `no supplied value satisfies ${unsatisfied.map(describe).join(', ')}`,
      });
    }
  }

  return results;
}

// ---------------------------------------------------------------------------
// Body matching
// ---------------------------------------------------------------------------

/**
 * Deep "contains" used for non-strict JSON body matching: every key/value in
 * `expected` must be present in `actual` (arrays compared by element containment).
 */
function jsonContains(expected: unknown, actual: unknown): boolean {
  if (expected === null || typeof expected !== 'object') {
    return expected === actual;
  }
  if (Array.isArray(expected)) {
    if (!Array.isArray(actual)) return false;
    return expected.every((ev) => actual.some((av) => jsonContains(ev, av)));
  }
  if (actual === null || typeof actual !== 'object' || Array.isArray(actual)) return false;
  const a = actual as Record<string, unknown>;
  return Object.entries(expected as Record<string, unknown>).every(
    ([k, v]) => k in a && jsonContains(v, a[k]),
  );
}

function evaluateBody(raw: unknown, actualBody: string): FieldResult | null {
  if (raw == null) return null;

  // Bare string body matcher → SUB_STRING-ish exact/contains.
  if (typeof raw === 'string') {
    if (raw.length === 0) return null;
    const matched = actualBody.includes(raw);
    return {
      field: 'body',
      verdict: matched ? 'match' : 'mismatch',
      expected: `contains "${raw}"`,
      actual: actualBody || '(empty)',
      reason: matched ? undefined : 'request body does not contain the expected string',
    };
  }

  if (typeof raw !== 'object') return null;
  const body = raw as Record<string, unknown>;
  const type = typeof body['type'] === 'string' ? (body['type'] as string) : undefined;

  switch (type) {
    case 'STRING': {
      const expected = typeof body['string'] === 'string' ? (body['string'] as string) : '';
      const subString = body['subString'] === true;
      const matched = subString ? actualBody.includes(expected) : actualBody === expected;
      return {
        field: 'body',
        verdict: matched ? 'match' : 'mismatch',
        expected: subString ? `contains "${expected}"` : `equals "${expected}"`,
        actual: actualBody || '(empty)',
        reason: matched ? undefined : 'request body does not match the expected string',
      };
    }
    case 'REGEX': {
      const pattern = typeof body['regex'] === 'string' ? (body['regex'] as string) : '';
      let matched: boolean;
      try {
        matched = new RegExp(pattern).test(actualBody);
      } catch {
        return {
          field: 'body',
          verdict: 'unsupported',
          expected: `regex ${pattern}`,
          actual: actualBody || '(empty)',
          reason: 'body regex is not a valid JavaScript regular expression',
        };
      }
      return {
        field: 'body',
        verdict: matched ? 'match' : 'mismatch',
        expected: `regex ${pattern}`,
        actual: actualBody || '(empty)',
        reason: matched ? undefined : 'request body does not match the regex',
      };
    }
    case 'JSON': {
      const strict = body['matchType'] === 'STRICT';
      let expectedJson: unknown;
      let actualJson: unknown;
      const expectedRaw = body['json'];
      try {
        expectedJson =
          typeof expectedRaw === 'string' ? JSON.parse(expectedRaw) : expectedRaw;
        actualJson = JSON.parse(actualBody);
      } catch {
        return {
          field: 'body',
          verdict: 'mismatch',
          expected: 'valid JSON body',
          actual: actualBody || '(empty)',
          reason: 'request body is not valid JSON',
        };
      }
      const matched = strict
        ? JSON.stringify(expectedJson) === JSON.stringify(actualJson)
        : jsonContains(expectedJson, actualJson);
      return {
        field: 'body',
        verdict: matched ? 'match' : 'mismatch',
        expected: `${strict ? 'strict ' : ''}JSON ${JSON.stringify(expectedJson)}`,
        actual: actualBody || '(empty)',
        reason: matched
          ? undefined
          : strict
            ? 'request JSON is not strictly equal to the expected JSON'
            : 'request JSON does not contain the expected JSON',
      };
    }
    default:
      return {
        field: 'body',
        verdict: 'unsupported',
        expected: type ? `body type ${type}` : 'body matcher',
        actual: actualBody || '(empty)',
        reason: `body matcher type ${type ?? '(unknown)'} is not supported by the client preview`,
      };
  }
}

// ---------------------------------------------------------------------------
// Top-level entry point
// ---------------------------------------------------------------------------

/**
 * Evaluate a candidate expectation's `httpRequest` matcher against a sample request,
 * entirely on the client. `expectation` may be the whole expectation `{ httpRequest }`
 * or a bare `httpRequest` object.
 */
export function previewMatch(expectation: unknown, sample: SampleRequest): PreviewResult {
  const root = (expectation ?? {}) as Record<string, unknown>;
  const httpRequest = (
    root['httpRequest'] && typeof root['httpRequest'] === 'object'
      ? root['httpRequest']
      : root
  ) as Record<string, unknown>;

  const results: FieldResult[] = [];

  // method
  const methodMatcher = toNottable(httpRequest['method']);
  if (methodMatcher === UNSUPPORTED_MATCHER) {
    results.push(unsupportedStringField('method'));
  } else if (methodMatcher) {
    const matched = nottableMatches(methodMatcher, sample.method.trim());
    results.push({
      field: 'method',
      verdict: matched ? 'match' : 'mismatch',
      expected: describe(methodMatcher),
      actual: sample.method.trim() || '(empty)',
      reason: matched ? undefined : 'request method does not match',
    });
  }

  // path
  const pathMatcher = toNottable(httpRequest['path']);
  if (pathMatcher === UNSUPPORTED_MATCHER) {
    results.push(unsupportedStringField('path'));
  } else if (pathMatcher) {
    const matched = nottableMatches(pathMatcher, sample.path.trim());
    results.push({
      field: 'path',
      verdict: matched ? 'match' : 'mismatch',
      expected: describe(pathMatcher),
      actual: sample.path.trim() || '(empty)',
      reason: matched ? undefined : 'request path does not match',
    });
  }

  // query string parameters (case-sensitive keys)
  results.push(
    ...evaluateKeyValues(
      'query parameter',
      httpRequest['queryStringParameters'],
      parseQueryLines(sample.queryString),
      false,
    ),
  );

  // headers (case-insensitive keys, per HTTP semantics)
  results.push(
    ...evaluateKeyValues(
      'header',
      httpRequest['headers'],
      parseHeaderLines(sample.headers),
      true,
    ),
  );

  // body
  const bodyResult = evaluateBody(httpRequest['body'], sample.body);
  if (bodyResult) results.push(bodyResult);

  // Flag matcher fields this preview does not understand, so we never claim a
  // false "matches". pathParameters and cookies are common; treat presence as
  // unsupported rather than ignoring them.
  for (const [field, label] of [
    ['pathParameters', 'path parameters'],
    ['cookies', 'cookies'],
    ['socketAddress', 'socket address'],
    ['secure', 'secure (TLS) flag'],
    ['keepAlive', 'keep-alive flag'],
    ['protocol', 'protocol'],
    ['clientIp', 'client IP'],
  ] as const) {
    const v = httpRequest[field];
    const isPresent =
      v != null &&
      !(typeof v === 'object' && !Array.isArray(v) && Object.keys(v as object).length === 0) &&
      !(Array.isArray(v) && v.length === 0);
    if (isPresent) {
      results.push({
        field: label,
        verdict: 'unsupported',
        expected: '(set)',
        actual: '(not evaluated)',
        reason: `${label} matching is not supported by the client preview`,
      });
    }
  }

  const hasUnsupported = results.some((r) => r.verdict === 'unsupported');
  const allMatch = results.every((r) => r.verdict === 'match');
  // An expectation with no matcher fields at all matches every request in MockServer.
  return {
    matches: allMatch && !hasUnsupported,
    hasUnsupported,
    results,
  };
}
