/**
 * An anyOf (logical OR) request matcher is not shown field by field in the Composer: editing an expectation that has
 * one keeps it exactly as loaded on save, and every language emitter writes its branches rather than dropping them.
 * The match preview evaluates each branch.
 */
import { describe, it, expect } from 'vitest';
import { matcherFromExpectation } from '../components/ComposerView';
import {
  buildExpectationJson, isCompositeRequestMatcher, mergeUnmodeledFields,
  standardToNode, standardToPython, standardToGo, standardToCsharp,
  standardToRuby, standardToRust, standardToJava, standardToCurl,
  type StandardActionPayload, type StandardMatcher,
} from '../lib/standardCodegen';
import { previewMatch } from '../lib/matcherPreview';

const anyOfRequest = {
  anyOf: [
    { method: 'GET', path: '/pets' },
    { method: 'POST', path: '/pets', headers: { 'X-Tenant': ['acme'] } },
    { not: true, anyOf: [{ path: '/admin/.*' }] },
  ],
};

const original = { id: 'any-of', httpRequest: anyOfRequest, httpResponse: { statusCode: 200, body: 'either' } };

function editAction(): StandardActionPayload {
  return { type: 'static', static: { statusCode: 200, body: 'either', contentType: '' }, editOriginal: original };
}

function loaded(): StandardMatcher {
  return matcherFromExpectation({ value: original } as never) as unknown as StandardMatcher;
}

describe('anyOf request matcher in the Composer', () => {
  it('is recognised as a composite matcher, as a conditional is', () => {
    expect(isCompositeRequestMatcher(anyOfRequest)).toBe(true);
    expect(isCompositeRequestMatcher({ if: { method: 'GET' } })).toBe(true);
    expect(isCompositeRequestMatcher({ method: 'GET', path: '/pets' })).toBe(false);
  });

  it('saves an untouched anyOf expectation exactly as loaded', () => {
    expect(buildExpectationJson(loaded(), editAction())['httpRequest']).toEqual(anyOfRequest);
  });

  it('never writes form fields beside anyOf, which the server would reject', () => {
    const edited = { ...loaded(), method: 'DELETE', path: '/typed-into-the-form', clientIp: '10.0.0.0/8' };
    expect(buildExpectationJson(edited, editAction())['httpRequest']).toEqual(anyOfRequest);
  });

  it('still overlays the form on a plain request matcher', () => {
    const plain = { httpRequest: { method: 'GET', path: '/old', keepAlive: true } };
    const merged = mergeUnmodeledFields(plain, { httpRequest: { method: 'GET', path: '/new' } });
    expect(merged['httpRequest']).toEqual({ method: 'GET', path: '/new', keepAlive: true });
  });
});

describe('anyOf reaches every language emitter', () => {
  const url = 'http://localhost:1080';
  type Emitter = (m: StandardMatcher, a: StandardActionPayload, u: string) => string;
  const cases: Array<[string, Emitter, string[]]> = [
    ['node', standardToNode, ['"anyOf"', '"/pets"', '"X-Tenant"', '"/admin/.*"']],
    ['curl', standardToCurl, ['"anyOf":[', '"/admin/.*"']],
    ['python', standardToPython, ['AnyOfRequestDefinition(', 'any_of=[', 'not_any_of=True', 'path="/admin/.*"']],
    ['ruby', standardToRuby, ['MockServer::AnyOfRequestDefinition.new(', 'any_of: [', 'not_any_of: true', '"/admin/.*"']],
    ['go', standardToGo, ['AnyOf: []*mockserver.HttpRequest{', 'Path: "/pets"', 'Path: "/admin/.*"']],
    ['csharp', standardToCsharp, ['anyOf', '/admin/.*']],
    ['rust', standardToRust, ['"anyOf"', '"/admin/.*"']],
    ['java', standardToJava, ['requestAnyOf(', 'not(requestAnyOf(', '.withPath("/admin/.*")', '.withHeader("X-Tenant", "acme")',
      'import static org.mockserver.model.AnyOfRequestDefinition.requestAnyOf;', 'import static org.mockserver.model.Not.not;']],
  ];

  it.each(cases)('%s writes every branch', (_lang, emit, literals) => {
    const code = emit(loaded(), editAction(), url);
    for (const literal of literals) expect(code).toContain(literal);
    expect(code).not.toMatch(/omits field\(s\)[^\n]*anyOf|"anyOf" has no typed/);
  });

  it('the Java tab names a branch field it cannot write instead of dropping it silently', () => {
    const withJsonBody = { ...original, httpRequest: { anyOf: [{ path: '/a', body: { type: 'JSON', json: { a: 1 } } }] } };
    const code = standardToJava(loaded(), { ...editAction(), editOriginal: withJsonBody });
    expect(code).toContain('.withPath("/a")');
    expect(code).toContain('httpRequest.anyOf[0].body');
  });
});

describe('anyOf in the match preview', () => {
  const sample = (method: string, path: string) => ({ method, path, headers: '', queryString: '', body: '' });

  it('matches when any branch matches and names the branches', () => {
    const result = previewMatch({ httpRequest: { anyOf: [{ path: '/a' }, { path: '/b' }] } }, sample('GET', '/b'));
    expect(result.matches).toBe(true);
    expect(result.results.map((r) => [r.field, r.verdict])).toEqual([['anyOf branch 0', 'mismatch'], ['anyOf branch 1', 'match']]);
  });

  it('does not match when no branch does, and inverts with not', () => {
    const anyOf = { anyOf: [{ path: '/a' }, { path: '/b' }] };
    expect(previewMatch({ httpRequest: anyOf }, sample('GET', '/c')).matches).toBe(false);
    expect(previewMatch({ httpRequest: { ...anyOf, not: true } }, sample('GET', '/c')).matches).toBe(true);
    expect(previewMatch({ httpRequest: { ...anyOf, not: true } }, sample('GET', '/a')).matches).toBe(false);
  });

  it('is undecided when no branch matches and one cannot be evaluated', () => {
    const result = previewMatch({ httpRequest: { anyOf: [{ path: '/a' }, { path: '/c', cookies: { s: 'x' } }] } }, sample('GET', '/c'));
    expect(result.matches).toBe(false);
    expect(result.hasUnsupported).toBe(true);
  });
});
