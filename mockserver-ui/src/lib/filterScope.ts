/**
 * Translate a `lib/filterDSL` scope term into MockServer request-matcher fields.
 *
 * The filter DSL is a **client-side predicate over observed items**; a breakpoint
 * matcher and a verification matcher are **server-side `httpRequest` matchers**.
 * The two only agree if the translation is exact, so this module is deliberately
 * conservative: it converts the three operators whose semantics survive the round
 * trip (`method:`, `path:`, `host:`) and refuses everything else with a reason,
 * rather than emitting a matcher that quietly matches the wrong traffic.
 *
 * **Why `path:`/`host:` need translating at all.** MockServer matches `path` and
 * header values as *exact-or-regex* (full match). The DSL's `path:`/`host:` are
 * *globs* where only `*` is special. Passing the glob through untouched would be
 * the classic silent mismatch: `path:/api/*` as a regex means "/api" followed by
 * zero-or-more "/" — it would NOT match `/api/orders`, and nothing would tell the
 * user. So a glob is compiled to the equivalent anchored regex source
 * (`/api/*` -> `/api/.*`), with every other regex metacharacter escaped so a
 * literal term stays literal (`/api/v1.0` -> `/api/v1\.0`).
 *
 * **Why the vocabulary stops where it does.**
 * - `status:` lands in the breakpoint form ({@link BREAKPOINT_SCOPE_FIELDS}),
 *   whose matcher carries an inclusive `responseStatusCodeMin`/`Max` range. Every
 *   comparator the search box accepts maps exactly onto that range, and repeated
 *   `status:` operators intersect just as the search box ANDs them. The
 *   verification response matcher ({@link VERIFICATION_SCOPE_FIELDS}) takes an
 *   exact `statusCode` or ONE `statusCodeRange` expression, so
 *   {@link verificationStatusFor} refuses a range only two comparisons express.
 * - `operation:` resolves from a decoded JSON request body's `operationName`.
 *   Neither matcher has a JSON-member body matcher wired to it here, so it is out.
 *
 * Callers pass the same field list to `OperatorSearchField` and to
 * {@link parseRequestScope}, so the placeholder, the help tooltip, the input's
 * error state and this translation cannot drift apart.
 */
import { parseSearchTerm } from './filterDSL';
import type { KeyToMultiValue } from '../types';

/**
 * The filter-DSL operators a server-side `httpRequest` matcher can genuinely
 * honour. Passed to `OperatorSearchField`'s `fields` prop so anything else is
 * flagged in the input instead of being silently dropped.
 */
export const REQUEST_SCOPE_FIELDS = ['method', 'path', 'host'] as const;

/** The breakpoint form also has a response status range for `status:` to fill. */
export const BREAKPOINT_SCOPE_FIELDS = [...REQUEST_SCOPE_FIELDS, 'status'] as const;

/** The verification form's response matcher has a status code and a status range for `status:`. */
export const VERIFICATION_SCOPE_FIELDS = BREAKPOINT_SCOPE_FIELDS;

/** Matcher fields derived from a scope term. `path`/`host` are regex sources. */
export interface RequestScope {
  method?: string;
  path?: string;
  host?: string;
  /** Inclusive response status bounds, from `status:` (breakpoint scope only). */
  statusMin?: number;
  statusMax?: number;
}

/** Status codes are three digits; a bound outside 0-999 can select no response. */
const STATUS_FLOOR = 0;
const STATUS_CEILING = 999;

/**
 * The inclusive bounds one `status:` operator selects. Status codes are integers,
 * so `>N` is `>=N+1` and `<N` is `<=N-1`; a bare or `=` value is one exact code.
 */
function statusBounds(comparator: string | undefined, value: number): { min?: number; max?: number } {
  switch (comparator) {
    case '>=': return { min: value };
    case '>': return { min: value + 1 };
    case '<=': return { max: value };
    case '<': return { max: value - 1 };
    default: return { min: value, max: value };
  }
}

export interface ParsedScope {
  /** The matcher fields the term contributes. Empty when nothing is applicable. */
  scope: RequestScope;
  /**
   * Registered operators outside {@link REQUEST_SCOPE_FIELDS}. `OperatorSearchField`
   * already explains these in its own helper text, so a caller should not repeat
   * the message — it only needs to refuse to apply.
   */
  unsupportedFields: string[];
  /**
   * Any *other* reason the term cannot be applied (leftover free text, a numeric
   * comparator, a `*` on the non-glob `method:`). Null when the term is clean.
   */
  error: string | null;
}

/**
 * Compile a glob (only `*` is special) to a regex *source* string that MockServer
 * will full-match. Mirrors `filterDSL`'s internal `globToRegExp` escaping exactly,
 * so a term matches the same set in a search box and in a matcher.
 */
export function globToRegexSource(glob: string): string {
  return glob.replace(/[.*+?^${}()|[\]\\]/g, (c) => (c === '*' ? '.*' : `\\${c}`));
}

/**
 * Parse a scope term into matcher fields, or into the reason it cannot be applied.
 * Never returns a partially-applied scope alongside an error — a refused term
 * contributes nothing.
 */
export function parseRequestScope(
  term: string,
  fields: readonly string[] = REQUEST_SCOPE_FIELDS,
): ParsedScope {
  const parsed = parseSearchTerm(term, { fields });
  const scope: RequestScope = {};

  if (parsed.text.length > 0) {
    return {
      scope: {},
      unsupportedFields: parsed.unsupportedFields,
      error: `"${parsed.text}" is not a scope operator - use ${fields.map((f) => `${f}:`).join(', ')}, or type it into the fields below.`,
    };
  }

  for (const op of parsed.operators) {
    if (op.unsupported) continue; // reported via unsupportedFields
    if (op.field === 'status') {
      const shown = `status:${op.comparator ?? ''}${op.expr}`;
      if (!/^\d{1,3}$/.test(op.expr)) {
        return {
          scope: {},
          unsupportedFields: parsed.unsupportedFields,
          error: `${shown} - a status is a whole number from 0 to 999, e.g. status:>=400 or status:404.`,
        };
      }
      const { min, max } = statusBounds(op.comparator, Number(op.expr));
      // Repeated status: operators AND in the search box, so they intersect here.
      if (min != null) scope.statusMin = Math.max(scope.statusMin ?? min, min);
      if (max != null) scope.statusMax = Math.min(scope.statusMax ?? max, max);
      continue;
    }
    if (op.comparator && op.comparator !== '=') {
      return {
        scope: {},
        unsupportedFields: parsed.unsupportedFields,
        error: `${op.field}:${op.comparator}${op.expr} - comparators (>= <= > <) need a numeric operator, and this scope has none.`,
      };
    }
    if (op.expr.length === 0) {
      return {
        scope: {},
        unsupportedFields: parsed.unsupportedFields,
        error: `${op.field}: has no value.`,
      };
    }
    // The search box ANDs repeated operators, so `path:/a path:/b` selects
    // nothing there. A matcher has one field per name, so the best this could do
    // is silently keep the last — which would mean the same term scopes the box
    // and the matcher differently. Refuse instead.
    if (scope[op.field as keyof RequestScope] != null) {
      return {
        scope: {},
        unsupportedFields: parsed.unsupportedFields,
        error: `${op.field}: appears more than once - a matcher has a single ${op.field}.`,
      };
    }
    if (op.field === 'method') {
      if (op.expr.includes('*')) {
        return {
          scope: {},
          unsupportedFields: parsed.unsupportedFields,
          error: 'method: matches one exact method, so * is not supported there.',
        };
      }
      scope.method = op.expr.toUpperCase();
    } else if (op.field === 'path') {
      scope.path = globToRegexSource(op.expr);
    } else if (op.field === 'host') {
      scope.host = globToRegexSource(op.expr);
    }
  }

  if (scope.statusMin != null || scope.statusMax != null) {
    const min = Math.max(scope.statusMin ?? STATUS_FLOOR, STATUS_FLOOR);
    const max = Math.min(scope.statusMax ?? STATUS_CEILING, STATUS_CEILING);
    if (min > max) {
      return {
        scope: {},
        unsupportedFields: parsed.unsupportedFields,
        error: 'status: selects no status code - check the comparators.',
      };
    }
  }

  return { scope, unsupportedFields: parsed.unsupportedFields, error: null };
}

/** True when the term is clean AND contributes at least one matcher field. */
export function isApplicableScope(parsed: ParsedScope): boolean {
  return (
    parsed.error == null &&
    parsed.unsupportedFields.length === 0 &&
    Object.keys(parsed.scope).length > 0
  );
}

// ---------------------------------------------------------------------------
// Host -> Host-header matcher
// ---------------------------------------------------------------------------
//
// Neither the breakpoint matcher endpoint nor the verification endpoint takes a
// host: both take a MockServer `httpRequest`, whose only representation of a host
// is the `Host` header. `host:` therefore lands in the form's existing headers
// control, where it stays visible and editable rather than becoming hidden state.

/**
 * Set the `Host` header row to `hostRegex` in a `KeyToMultiValue[]` headers
 * control: replacing an existing (case-insensitive) Host row, otherwise reusing
 * the first blank row, otherwise appending.
 */
export function withHostHeaderRow(rows: KeyToMultiValue[], hostRegex: string): KeyToMultiValue[] {
  const existing = rows.findIndex((r) => r.name.trim().toLowerCase() === 'host');
  if (existing >= 0) {
    return rows.map((r, i) => (i === existing ? { name: r.name, values: [hostRegex] } : r));
  }
  const blank = rows.findIndex((r) => r.name.trim() === '' && r.values.every((v) => v.trim() === ''));
  const row: KeyToMultiValue = { name: 'Host', values: [hostRegex] };
  if (blank >= 0) return rows.map((r, i) => (i === blank ? row : r));
  return [...rows, row];
}

/**
 * Set the `Host` header in a `Name: value` per-line headers textarea, replacing an
 * existing (case-insensitive) Host line and preserving every other line and their
 * order. Matches `standardCodegen.parseKeyValueLines`, which is what the
 * verification form parses this textarea with.
 */
export function withHostHeaderLine(text: string, hostRegex: string): string {
  const line = `Host: ${hostRegex}`;
  const lines = text.split(/\r?\n/);
  const existing = lines.findIndex((l) => {
    const idx = l.indexOf(':');
    return idx > 0 && l.slice(0, idx).trim().toLowerCase() === 'host';
  });
  if (existing >= 0) return lines.map((l, i) => (i === existing ? line : l)).join('\n');
  // Append symmetrically with the replace branch: keep the user's lines as they are
  // and only drop the trailing blank an empty/newline-terminated textarea leaves.
  const kept = [...lines];
  while (kept.length > 0 && kept[kept.length - 1]!.trim() === '') kept.pop();
  return [...kept, line].join('\n');
}

/** Where a `status:` scope lands in a verification response matcher. */
export type VerificationStatus =
  | { statusCode: string; statusCodeRange: '' }
  | { statusCode: ''; statusCodeRange: string };

/**
 * Translate inclusive status bounds into the verification response matcher: an exact code, a
 * class such as `4XX`, or one comparison such as `>= 400` (the forms `StatusCodeMatcher`
 * accepts). A range that needs two comparisons, such as 400-449, has no single expression, so
 * it is refused rather than widened.
 */
export function verificationStatusFor(scope: RequestScope): VerificationStatus | { error: string } | null {
  const min = scope.statusMin != null && scope.statusMin > STATUS_FLOOR ? scope.statusMin : undefined;
  const max = scope.statusMax != null && scope.statusMax < STATUS_CEILING ? scope.statusMax : undefined;
  if (scope.statusMin == null && scope.statusMax == null) return null;
  if (min != null && max != null) {
    if (min === max) return { statusCode: String(min), statusCodeRange: '' };
    if (min % 100 === 0 && max === min + 99) return { statusCode: '', statusCodeRange: `${min / 100}XX` };
    return {
      error: `status: selects ${min}-${max}, which needs two comparisons; a verification response matcher takes one ` +
        '(e.g. status:>=400), an exact code, or a whole class (status:>=400 status:<500 is 4XX).',
    };
  }
  if (min != null) return { statusCode: '', statusCodeRange: `>= ${min}` };
  if (max != null) return { statusCode: '', statusCodeRange: `<= ${max}` };
  return { statusCode: '', statusCodeRange: '>= 0' };
}

const STATUS_CLASS = /^\s*[0-9][xX]{2}\s*$/;
const STATUS_COMPARISON = /^\s*(>=|<=|==|>|<)\s*-?\d+(?:\.\d+)?\s*$/;

/** Why a typed status range would never match (the server treats it as a non-match), or null when it is valid. */
export function statusRangeProblem(range: string): string | null {
  if (range.trim() === '' || STATUS_CLASS.test(range) || STATUS_COMPARISON.test(range)) return null;
  return 'Use a class such as 4XX or one comparison such as >= 400 (>, >=, <, <=, ==).';
}
