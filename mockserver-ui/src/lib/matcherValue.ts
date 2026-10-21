/**
 * Header, query-string, cookie and path-parameter matcher VALUES.
 *
 * The Composer edits these values as `Name: value` / `name=value` text lines. A value is
 * either a plain string (the server's NottableString syntax: a leading `!` negates, `?`
 * marks optional) or one of the server's object forms (stringOrJsonSchema.json): the
 * nottable form `{"not":..,"value":..}`, the schema form `{"schema":{..}}`, or the
 * date-time form `{"after":"now -1 hours", ...}`. An object form is written on its line
 * as compact JSON, so it survives a load / save round trip unchanged.
 */

/** A matcher value as it appears on the wire. */
export type WireMatcherValue = string | Record<string, unknown>;

/** Every key the server accepts in the object form of a matcher value. */
const OBJECT_FORM_KEYS: ReadonlySet<string> = new Set([
  'not', 'optional', 'value', 'schema', 'parameterStyle', 'schemaType',
  'before', 'after', 'equalToDateTime',
  'actualFormat', 'truncateExpected', 'truncateActual', 'applyTruncationLast', 'zone',
]);

/** Keys that make an object a matcher (each object form carries exactly one of these). */
const OBJECT_FORM_ANCHORS = ['value', 'schema', 'before', 'after', 'equalToDateTime'];

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return !!v && typeof v === 'object' && !Array.isArray(v);
}

/** Whether `v` has the shape of a server matcher-value object. */
export function isMatcherObject(v: unknown): v is Record<string, unknown> {
  if (!isPlainObject(v)) return false;
  const keys = Object.keys(v);
  return keys.every((k) => OBJECT_FORM_KEYS.has(k)) && OBJECT_FORM_ANCHORS.some((k) => k in v);
}

/** The object a value line holds, or undefined when the line is a plain string. */
function parseObjectText(text: string): Record<string, unknown> | undefined {
  const t = text.trim();
  if (!t.startsWith('{') || !t.endsWith('}')) return undefined;
  try {
    const parsed: unknown = JSON.parse(t);
    return isMatcherObject(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

/** The wire value of one Composer value text: a matcher object written as JSON, else the text itself. */
export function matcherValueFromText(text: string): WireMatcherValue {
  return parseObjectText(text) ?? text;
}

/**
 * The Composer text for one wire value. A plain string is shown as-is, except one that
 * would itself read back as a matcher object: that is shown in the nottable object form,
 * which the server reads verbatim. Any object is shown as compact JSON.
 */
export function matcherValueToText(value: unknown): string {
  if (typeof value === 'string') {
    return parseObjectText(value) ? JSON.stringify({ value }) : value;
  }
  if (isPlainObject(value)) return JSON.stringify(value);
  return value == null ? '' : String(value);
}

/**
 * Parse `key<separator>value` lines into a multi-value map whose values are matcher
 * values (see {@link matcherValueFromText}). Same line rules as `parseKeyValueLines`.
 */
export function parseMatcherKeyValueLines(
  text: string,
  separator: ':' | '=',
): Record<string, WireMatcherValue[]> | undefined {
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  if (lines.length === 0) return undefined;
  const out: Record<string, WireMatcherValue[]> = {};
  for (const line of lines) {
    const idx = line.indexOf(separator);
    if (idx < 0) continue;
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1).trim();
    if (!key) continue;
    const arr = out[key] ?? [];
    arr.push(matcherValueFromText(value));
    out[key] = arr;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

// ---------------------------------------------------------------------------
// Date-time matcher
// ---------------------------------------------------------------------------

export type DateTimeOperator = 'before' | 'after' | 'equalToDateTime';

export const DATE_TIME_OPERATORS: readonly DateTimeOperator[] = ['before', 'after', 'equalToDateTime'];

/** A date-time comparison, the typed form of `{"after":"now -1 hours", ...}`. */
export interface DateTimeMatcher {
  operator: DateTimeOperator;
  expected: string;
  actualFormat?: string;
  truncateExpected?: string;
  truncateActual?: string;
  applyTruncationLast?: boolean;
  zone?: string;
  not?: boolean;
}

type DateTimeOption = 'actualFormat' | 'truncateExpected' | 'truncateActual' | 'applyTruncationLast' | 'zone' | 'not';

/** Option fields in the order the Java serializer and every client builder apply them. */
const DATE_TIME_OPTIONS: readonly DateTimeOption[] = [
  'actualFormat', 'truncateExpected', 'truncateActual', 'applyTruncationLast', 'zone', 'not',
];

const BOOLEAN_OPTIONS: ReadonlySet<string> = new Set(['applyTruncationLast', 'not']);

/**
 * The date-time matcher `v` holds, or undefined when `v` is anything else. Strict: exactly
 * one operator, only date-time keys, and each key with its schema type — so an object a
 * client builder could not reproduce exactly (one also carrying `parameterStyle`, say) is
 * not treated as one.
 */
export function dateTimeMatcherOf(v: unknown): DateTimeMatcher | undefined {
  if (!isPlainObject(v)) return undefined;
  const operators = DATE_TIME_OPERATORS.filter((op) => op in v);
  if (operators.length !== 1) return undefined;
  const operator = operators[0]!;
  const expected = v[operator];
  if (typeof expected !== 'string') return undefined;
  const dt: DateTimeMatcher = { operator, expected };
  for (const [key, value] of Object.entries(v)) {
    if (key === operator) continue;
    if (!(DATE_TIME_OPTIONS as readonly string[]).includes(key)) return undefined;
    if (BOOLEAN_OPTIONS.has(key) ? typeof value !== 'boolean' : typeof value !== 'string') return undefined;
    (dt as unknown as Record<string, unknown>)[key] = value;
  }
  return dt;
}

/** The wire object for `dt`, in the Java serializer's field order. `not` is written only when true. */
export function dateTimeMatcherToWire(dt: DateTimeMatcher): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  if (dt.not) out['not'] = true;
  out[dt.operator] = dt.expected;
  for (const key of DATE_TIME_OPTIONS) {
    if (key === 'not') continue;
    const value = dt[key];
    if (value !== undefined && value !== '') out[key] = value;
  }
  return out;
}

/** How one client language spells the date-time builder. */
export interface DateTimeBuilderStyle {
  /** What precedes the constructor name, e.g. `NottableDateTimeString.` or `MatcherValue::`. */
  head: string;
  naming: 'camel' | 'snake' | 'pascal';
  str: (s: string) => string;
  bool: (b: boolean) => string;
}

function snake(name: string): string {
  return name.replace(/[A-Z]/g, (c) => '_' + c.toLowerCase());
}

function methodName(name: string, naming: DateTimeBuilderStyle['naming']): string {
  if (naming === 'snake') return snake(name);
  if (naming === 'pascal') return name.charAt(0).toUpperCase() + name.slice(1);
  return name;
}

/**
 * The builder expression for `dt`, e.g. `NottableDateTimeString.after("now -1 hours").withZone("UTC")`.
 * Every option present on the wire is applied, in {@link DATE_TIME_OPTIONS} order.
 */
export function dateTimeBuilderExpr(dt: DateTimeMatcher, style: DateTimeBuilderStyle): string {
  let expr = `${style.head}${methodName(dt.operator, style.naming)}(${style.str(dt.expected)})`;
  for (const key of DATE_TIME_OPTIONS) {
    const value = dt[key];
    if (value === undefined) continue;
    const method = methodName('with' + key.charAt(0).toUpperCase() + key.slice(1), style.naming);
    expr += `.${method}(${typeof value === 'boolean' ? style.bool(value) : style.str(value)})`;
  }
  return expr;
}

/**
 * The plain-string reading of a value, as the server's NottableString.string(..) parses it:
 * a leading `?` (optional), then `!` (not), then `?` again. A blank value carries no markers.
 */
export function parsePlainMatcher(s: string): { value: string; not: boolean; optional: boolean } {
  let rest = s;
  let not = false;
  let optional = false;
  if (rest.trim() !== '') {
    if (rest.startsWith('?')) { optional = true; rest = rest.slice(1); }
    if (rest.startsWith('!')) { not = true; rest = rest.slice(1); }
    if (rest.startsWith('?')) { optional = true; rest = rest.slice(1); }
  }
  return { value: rest, not, optional };
}

/** The nottable object form `{value, not?, optional?}` as typed fields, or undefined for any other shape. */
export function nottableObjectOf(v: unknown): { value: string; not: boolean; optional: boolean } | undefined {
  if (!isPlainObject(v) || typeof v['value'] !== 'string') return undefined;
  for (const [k, val] of Object.entries(v)) {
    if (k === 'value') continue;
    if ((k !== 'not' && k !== 'optional') || typeof val !== 'boolean') return undefined;
  }
  return { value: v['value'] as string, not: v['not'] === true, optional: v['optional'] === true };
}

// ---------------------------------------------------------------------------
// Composer lines
// ---------------------------------------------------------------------------

/** The Composer text fields that hold matcher values (MatcherState keys). */
export type MatcherValueField = 'headers' | 'queryString' | 'cookies' | 'pathParams';

/** The key / value separator a field's lines use. */
export function matcherFieldSeparator(field: MatcherValueField): ':' | '=' {
  return field === 'headers' ? ':' : '=';
}

/** One Composer line for `name` holding `value`, in the field's own line format. */
export function matcherValueLine(field: MatcherValueField, name: string, value: WireMatcherValue): string {
  const sep = matcherFieldSeparator(field);
  return `${name.trim()}${sep === ':' ? ': ' : '='}${matcherValueToText(value)}`;
}

/** `text` with `line` appended on a line of its own. */
export function appendLine(text: string, line: string): string {
  const trimmed = text.replace(/\s+$/, '');
  return trimmed ? `${trimmed}\n${line}` : line;
}
