/// <reference types="vite/client" />
/**
 * Header / query / cookie / path-parameter matcher values in object form — the date-time
 * matcher (`{"after":"now -1 hours"}`), the schema form and the nottable object form.
 *
 * Two guarantees: a loaded object value survives the Composer's load → save round trip unchanged
 * (it used to be flattened to "" or "[object Object]"), and every language tab renders a date-time
 * value with that client's own builder.
 */
import { describe, it, expect } from 'vitest';
import { matcherFromExpectation } from '../components/ComposerView';
import {
  buildExpectationJson,
  standardToJava, standardToJson, standardToCurl, standardToNode, standardToPython,
  standardToGo, standardToCsharp, standardToRuby, standardToRust,
  type StandardActionPayload, type StandardMatcher,
} from '../lib/standardCodegen';
import {
  appendLine,
  dateTimeBuilderExpr,
  dateTimeMatcherOf,
  dateTimeMatcherToWire,
  matcherValueFromText,
  matcherValueLine,
  matcherValueToText,
  parseMatcherKeyValueLines,
} from '../lib/matcherValue';

const fixtureModules = import.meta.glob('../../../test-fixtures/expectations/matcher_request_date_time.json', {
  eager: true,
  import: 'default',
}) as Record<string, Record<string, unknown>>;
const fixture = Object.values(fixtureModules)[0]!;

const action: StandardActionPayload = {
  type: 'static',
  static: { statusCode: 200, body: 'dated', contentType: '' },
};

function load(httpRequest: Record<string, unknown>): StandardMatcher {
  return matcherFromExpectation({ key: 'k', value: { httpRequest } } as never);
}

function saved(httpRequest: Record<string, unknown>): Record<string, unknown> {
  return buildExpectationJson(load(httpRequest), action)['httpRequest'] as Record<string, unknown>;
}

const fixtureRequest = fixture['httpRequest'] as Record<string, unknown>;

/** The fixture's request matcher, as the Composer holds it after loading. */
const dateTimeMatcher = load(fixtureRequest);

const ALL_TABS: [string, (m: StandardMatcher) => string][] = [
  ['java', (m) => standardToJava(m, action)],
  ['json', (m) => standardToJson(m, action)],
  ['curl', (m) => standardToCurl(m, action, 'http://localhost:1080')],
  ['node', (m) => standardToNode(m, action, 'http://localhost:1080')],
  ['python', (m) => standardToPython(m, action, 'http://localhost:1080')],
  ['go', (m) => standardToGo(m, action, 'http://localhost:1080')],
  ['csharp', (m) => standardToCsharp(m, action, 'http://localhost:1080')],
  ['ruby', (m) => standardToRuby(m, action, 'http://localhost:1080')],
  ['rust', (m) => standardToRust(m, action, 'http://localhost:1080')],
];

describe('object matcher values survive a Composer load and save unchanged', () => {
  it('round-trips the shared date-time fixture exactly', () => {
    const out = saved(fixtureRequest);
    expect(out['headers']).toEqual(fixtureRequest['headers']);
    expect(out['queryStringParameters']).toEqual(fixtureRequest['queryStringParameters']);
    expect(out['cookies']).toEqual(fixtureRequest['cookies']);
  });

  it('round-trips date-time path parameters', () => {
    const pathParameters = { id: [{ before: 'now', zone: 'UTC' }] };
    expect(saved({ path: '/items/{id}', pathParameters })['pathParameters']).toEqual(pathParameters);
  });

  it('round-trips the schema form and the nottable object form', () => {
    const headers = {
      'X-Schema': [{ schema: { type: 'string', pattern: '^[0-9]+$' } }],
      'X-Literal-Bang': [{ not: false, value: '!literal' }],
      'X-Optional': [{ optional: true, value: 'maybe' }],
    };
    const pathParameters = { id: [{ parameterStyle: 'MATRIX', value: '5' }] };
    const cookies = { session: { not: true, schema: { type: 'string' } } };
    const out = saved({ path: '/p/{id}', headers, pathParameters, cookies });
    expect(out['headers']).toEqual(headers);
    expect(out['pathParameters']).toEqual(pathParameters);
    expect(out['cookies']).toEqual(cookies);
  });

  it('never writes [object Object] into the Composer text', () => {
    for (const text of [dateTimeMatcher.headers, dateTimeMatcher.queryString, dateTimeMatcher.cookies]) {
      expect(text).not.toContain('[object Object]');
    }
    expect(dateTimeMatcher.headers).toContain('X-Issued-At: {"after":"now -1 hours"}');
  });

  it('keeps a plain string that only looks like a date-time prefix a plain string', () => {
    expect(dateTimeMatcher.headers).toContain('X-Plain: before:2026-01-01');
    expect((saved(fixtureRequest)['headers'] as Record<string, unknown>)['X-Plain']).toEqual(['before:2026-01-01']);
  });

  it('keeps a plain string that is itself matcher-shaped JSON a literal value', () => {
    const literal = '{"after":"now"}';
    const out = saved({ path: '/a', headers: { 'X-Json': [literal] } });
    // shown and saved in the nottable object form, which the server reads verbatim
    expect(out['headers']).toEqual({ 'X-Json': [{ value: literal }] });
  });

  it('survives the edit overlay merge too', () => {
    const original = { httpRequest: fixtureRequest, httpResponse: { statusCode: 200, body: 'dated' } };
    const out = buildExpectationJson(dateTimeMatcher, { ...action, editOriginal: original, editActionModeled: true });
    expect(out['httpRequest']).toEqual(expect.objectContaining({
      headers: fixtureRequest['headers'],
      queryStringParameters: fixtureRequest['queryStringParameters'],
      cookies: fixtureRequest['cookies'],
    }));
  });
});

describe('matcher value text', () => {
  it('reads only matcher-shaped JSON objects as objects', () => {
    expect(matcherValueFromText('{"after":"now"}')).toEqual({ after: 'now' });
    expect(matcherValueFromText('{"value":"x","not":true}')).toEqual({ value: 'x', not: true });
    expect(matcherValueFromText('{"foo":1}')).toBe('{"foo":1}');
    expect(matcherValueFromText('{not json}')).toBe('{not json}');
    expect(matcherValueFromText('!foo')).toBe('!foo');
  });

  it('parses lines into matcher values', () => {
    expect(parseMatcherKeyValueLines('A: x\nB: {"before":"now"}\nA: !y', ':')).toEqual({
      A: ['x', '!y'],
      B: [{ before: 'now' }],
    });
  });

  it('writes plain strings as-is and objects as compact JSON', () => {
    expect(matcherValueToText('plain')).toBe('plain');
    expect(matcherValueToText({ after: 'now', zone: 'UTC' })).toBe('{"after":"now","zone":"UTC"}');
  });

  it('builds a field line in that field\'s format', () => {
    expect(matcherValueLine('headers', 'X-At', { after: 'now' })).toBe('X-At: {"after":"now"}');
    expect(matcherValueLine('cookies', 'seen', { before: 'now' })).toBe('seen={"before":"now"}');
    expect(appendLine('A: b\n', 'C: d')).toBe('A: b\nC: d');
    expect(appendLine('', 'C: d')).toBe('C: d');
  });
});

describe('dateTimeMatcherOf', () => {
  it('accepts exactly one operator with date-time options', () => {
    expect(dateTimeMatcherOf({ not: true, after: 'now', applyTruncationLast: false, zone: 'UTC' })).toEqual({
      operator: 'after', expected: 'now', not: true, applyTruncationLast: false, zone: 'UTC',
    });
  });

  it('rejects other shapes', () => {
    expect(dateTimeMatcherOf('now')).toBeUndefined();
    expect(dateTimeMatcherOf({ before: 'a', after: 'b' })).toBeUndefined();
    expect(dateTimeMatcherOf({ zone: 'UTC' })).toBeUndefined();
    expect(dateTimeMatcherOf({ after: 'now', value: 'x' })).toBeUndefined();
    expect(dateTimeMatcherOf({ after: 'now', parameterStyle: 'SIMPLE' })).toBeUndefined();
    expect(dateTimeMatcherOf({ after: 'now', applyTruncationLast: 'yes' })).toBeUndefined();
  });

  it('writes only the fields that are set, and not only when true', () => {
    expect(dateTimeMatcherToWire({ operator: 'before', expected: 'now', actualFormat: '', not: false })).toEqual({ before: 'now' });
    expect(Object.keys(dateTimeMatcherToWire({
      operator: 'equalToDateTime', expected: 'now', zone: 'UTC', truncateActual: 'startOfDay', not: true,
    }))).toEqual(['not', 'equalToDateTime', 'truncateActual', 'zone']);
  });

  it('spells the builder in each naming style', () => {
    const dt = dateTimeMatcherOf({ equalToDateTime: 'now', applyTruncationLast: true })!;
    const str = (s: string) => JSON.stringify(s);
    const bool = String;
    expect(dateTimeBuilderExpr(dt, { head: 'X.', naming: 'camel', str, bool })).toBe('X.equalToDateTime("now").withApplyTruncationLast(true)');
    expect(dateTimeBuilderExpr(dt, { head: 'X::', naming: 'snake', str, bool })).toBe('X::equal_to_date_time("now").with_apply_truncation_last(true)');
    expect(dateTimeBuilderExpr(dt, { head: 'x.', naming: 'pascal', str, bool })).toBe('x.EqualToDateTime("now").WithApplyTruncationLast(true)');
  });
});

describe('every language tab renders a date-time value with its client builder', () => {
  it('no tab flattens an object value', () => {
    for (const [lang, emit] of ALL_TABS) {
      expect(emit(dateTimeMatcher), lang).not.toContain('[object Object]');
    }
  });

  it('json, curl and node carry the plain object', () => {
    for (const [lang, emit] of ALL_TABS.filter(([l]) => ['json', 'node'].includes(l))) {
      const code = emit(dateTimeMatcher);
      expect(code, lang).toContain('"after": "now -1 hours"');
      expect(code, lang).toContain('"actualFormat": "rfc1123"');
    }
    expect(standardToCurl(dateTimeMatcher, action, 'http://localhost:1080')).toContain('"after":"now -1 hours"');
  });

  it('java uses NottableDateTimeString with a string(..) name', () => {
    const code = standardToJava(dateTimeMatcher, action);
    expect(code).toContain('import org.mockserver.model.NottableDateTimeString;');
    expect(code).toContain('import static org.mockserver.model.NottableString.string;');
    expect(code).toContain('.withHeader(string("X-Issued-At"), NottableDateTimeString.after("now -1 hours"))');
    expect(code).toContain('.withHeader(string("If-Modified-Since"), NottableDateTimeString.before("2026-10-21T00:00:00Z").withActualFormat("rfc1123"))');
    expect(code).toContain('.withHeader("X-Plain", "before:2026-01-01")');
    expect(code).toContain('.withQueryStringParameter(string("from"), NottableDateTimeString.after("2026-01-01").withActualFormat("dd/MM/yyyy").withZone("Europe/London"))');
    expect(code).toContain('.withCookie(string("seen"), NottableDateTimeString.equalToDateTime("now +1 days").withTruncateExpected("first day of month").withTruncateActual("startOfDay").withApplyTruncationLast(true).withNot(true))');
  });

  it('python uses DateTimeMatcher', () => {
    const code = standardToPython(dateTimeMatcher, action, 'http://localhost:1080');
    expect(code).toMatch(/from mockserver import \([\s\S]*DateTimeMatcher,/);
    expect(code).toContain('KeyToMultiValue(name="X-Issued-At", values=[DateTimeMatcher.after("now -1 hours")])');
    expect(code).toContain('KeyToMultiValue(name="X-Plain", values=["before:2026-01-01"])');
    expect(code).toContain('KeyToMultiValue(name="seen", values=[DateTimeMatcher.equal_to_date_time("now +1 days").with_truncate_expected("first day of month").with_truncate_actual("startOfDay").with_apply_truncation_last(True).with_not(True)])');
  });

  it('ruby uses MockServer::DateTimeMatcher', () => {
    const code = standardToRuby(dateTimeMatcher, action, 'http://localhost:1080');
    expect(code).toContain('MockServer::KeyToMultiValue.new(name: "X-Issued-At", values: [MockServer::DateTimeMatcher.after("now -1 hours")])');
    expect(code).toContain('MockServer::KeyToMultiValue.new(name: "from", values: [MockServer::DateTimeMatcher.after("2026-01-01").with_actual_format("dd/MM/yyyy").with_zone("Europe/London")])');
  });

  it('go uses the *Matcher builders after every plain call', () => {
    const code = standardToGo(dateTimeMatcher, action, 'http://localhost:1080');
    expect(code).toContain('HeaderMatcher("X-Issued-At", mockserver.After("now -1 hours"))');
    expect(code).toContain('HeaderMatcher("If-Modified-Since", mockserver.Before("2026-10-21T00:00:00Z").WithActualFormat("rfc1123"))');
    expect(code).toContain('QueryStringParameterMatcher("from", mockserver.After("2026-01-01").WithActualFormat("dd/MM/yyyy").WithZone("Europe/London"))');
    expect(code).toContain('CookieMatcher("seen", mockserver.EqualToDateTime("now +1 days").WithTruncateExpected("first day of month").WithTruncateActual("startOfDay").WithApplyTruncationLast(true).WithNot(true))');
    // HeaderMatcher carries the plain map over only when first called
    expect(code.indexOf('Header("X-Plain", "before:2026-01-01")')).toBeGreaterThan(-1);
    expect(code.indexOf('Header("X-Plain"')).toBeLessThan(code.indexOf('HeaderMatcher('));
  });

  it('c# uses MatcherValue in the *Matchers properties', () => {
    const code = standardToCsharp(dateTimeMatcher, action, 'http://localhost:1080');
    expect(code).toContain('HeaderMatchers = new Dictionary<string, List<MatcherValue>>');
    expect(code).toContain('["X-Issued-At"] = new() { MatcherValue.After("now -1 hours") }');
    expect(code).toContain('["X-Plain"] = new() { "before:2026-01-01" }');
    expect(code).toContain('QueryStringParameterMatchers = new Dictionary<string, List<MatcherValue>>');
    expect(code).toContain('CookieMatchers = new Dictionary<string, MatcherValue>');
    expect(code).toContain('["seen"] = MatcherValue.EqualToDateTime("now +1 days").WithTruncateExpected("first day of month").WithTruncateActual("startOfDay").WithApplyTruncationLast(true).WithNot(true)');
    expect(code).not.toContain('Headers = new Dictionary<string, List<string>>');
  });

  it('rust uses MatcherValue with the *_matcher methods after every plain call', () => {
    const code = standardToRust(dateTimeMatcher, action, 'http://localhost:1080');
    expect(code).toContain('.header_matcher("X-Issued-At", MatcherValue::after("now -1 hours"))');
    expect(code).toContain('.query_param_matcher("from", MatcherValue::after("2026-01-01").with_actual_format("dd/MM/yyyy").with_zone("Europe/London"))');
    expect(code).toContain('.cookie_matcher("seen", MatcherValue::equal_to_date_time("now +1 days").with_truncate_expected("first day of month").with_truncate_actual("startOfDay").with_apply_truncation_last(true).with_not(true))');
    expect(code.indexOf('.header("X-Plain", "before:2026-01-01")')).toBeGreaterThan(-1);
    expect(code.indexOf('.header("X-Plain"')).toBeLessThan(code.indexOf('.header_matcher('));
  });

  it('a path parameter date-time uses each client\'s path-parameter matcher', () => {
    const m = load({ path: '/items/{id}', pathParameters: { id: [{ before: 'now' }] } });
    expect(standardToJava(m, action)).toContain('.withPathParameter(string("id"), NottableDateTimeString.before("now"))');
    expect(standardToGo(m, action, 'http://localhost:1080')).toContain('PathParameterMatcher("id", mockserver.Before("now"))');
    expect(standardToRust(m, action, 'http://localhost:1080')).toContain('.path_param_matcher("id", MatcherValue::before("now"))');
    expect(standardToCsharp(m, action, 'http://localhost:1080')).toContain('PathParameterMatchers = new Dictionary<string, List<MatcherValue>>');
    expect(standardToPython(m, action, 'http://localhost:1080')).toContain('values=[DateTimeMatcher.before("now")]');
  });

  it('a key mixing plain and date-time values keeps the plain value\'s meaning', () => {
    const m = load({ path: '/a', headers: { 'X-Mixed': ['!foo', { after: 'now' }] } });
    expect(standardToJava(m, action)).toContain('.withHeader(string("X-Mixed"), string("!foo"), NottableDateTimeString.after("now"))');
    expect(standardToGo(m, action, 'http://localhost:1080')).toContain('HeaderMatcher("X-Mixed", mockserver.NotLiteral("foo"), mockserver.After("now"))');
    expect(standardToCsharp(m, action, 'http://localhost:1080')).toContain('["X-Mixed"] = new() { "!foo", MatcherValue.After("now") }');
    const rust = standardToRust(m, action, 'http://localhost:1080');
    expect(rust).toContain('.header_matcher("X-Mixed", MatcherValue::from("!foo"))');
    expect(rust).toContain('.header_matcher("X-Mixed", MatcherValue::after("now"))');
  });

  it('a schema value falls back to each tab\'s raw form or an honest note', () => {
    const m = load({ path: '/a', headers: { 'X-S': [{ schema: { type: 'string' } }] } });
    expect(standardToJava(m, action)).toContain('.withHeader(string("X-S"), schemaString("{\\"type\\":\\"string\\"}", false))');
    expect(standardToPython(m, action, 'http://localhost:1080')).toContain('values=[{"schema": {"type": "string"}}]');
    expect(standardToRuby(m, action, 'http://localhost:1080')).toContain('values: [{ "schema" => { "type" => "string" } }]');
    expect(standardToCsharp(m, action, 'http://localhost:1080')).toContain('JsonSerializer.Deserialize<HttpRequest>(');
    expect(standardToRust(m, action, 'http://localhost:1080')).toContain('.extra.insert("headers".to_string()');
    expect(standardToGo(m, action, 'http://localhost:1080')).toContain('// NOTE: matcher value(s) MatcherValue cannot hold are omitted: httpRequest.headers.X-S');
  });
});
