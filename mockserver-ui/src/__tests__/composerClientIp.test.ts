/**
 * The Composer models `httpRequest.clientIp` (an IP address or CIDR range with an optional leading `!`): it loads
 * from an existing expectation, re-emits unchanged, is omitted when blank, and every language emitter writes its
 * client's typed setter rather than dropping it. The negated form is the fixture because a `!` that went missing
 * would invert the matcher while still looking present.
 */
import { describe, it, expect } from 'vitest';
import { matcherFromExpectation } from '../components/ComposerView';
import {
  buildExpectationJson, mergeUnmodeledFields,
  standardToNode, standardToPython, standardToGo, standardToCsharp,
  standardToRuby, standardToRust, standardToJava, standardToCurl,
  type StandardActionPayload, type StandardMatcher,
} from '../lib/standardCodegen';

const action: StandardActionPayload = {
  type: 'static',
  static: { statusCode: 200, body: '', contentType: 'application/json' },
};

const base: StandardMatcher = {
  id: '', method: 'GET', path: '/api', headers: '', queryString: '', cookies: '',
  pathParams: '', body: '', bodyBinary: false, bodyMatcherType: 'string',
  priority: 0, times: 0,
};

function roundTrip(clientIp: string | undefined) {
  const httpRequest: Record<string, unknown> = { method: 'GET', path: '/api' };
  if (clientIp !== undefined) httpRequest['clientIp'] = clientIp;
  const loaded = matcherFromExpectation({ value: { httpRequest } } as never);
  const emitted = buildExpectationJson(loaded, action);
  return { loaded, request: emitted['httpRequest'] as Record<string, unknown> };
}

describe('clientIp round-trip through the Composer', () => {
  it('loads and re-emits a negated range unchanged', () => {
    const { loaded, request } = roundTrip('!10.0.0.0/8');
    expect(loaded.clientIp).toBe('!10.0.0.0/8');
    expect(request['clientIp']).toBe('!10.0.0.0/8');
  });

  it('leaves an absent clientIp absent', () => {
    const { loaded, request } = roundTrip(undefined);
    expect(loaded.clientIp).toBe('');
    expect(Object.prototype.hasOwnProperty.call(request, 'clientIp')).toBe(false);
  });

  it('omits a blank value and trims surrounding whitespace', () => {
    expect(buildExpectationJson({ ...base, clientIp: '   ' }, action)['httpRequest']).not.toHaveProperty('clientIp');
    expect((buildExpectationJson({ ...base, clientIp: ' 2001:db8::/32 ' }, action)['httpRequest'] as Record<string, unknown>)['clientIp'])
      .toBe('2001:db8::/32');
  });

  it('is form-owned on edit: a cleared field removes it, other unmodelled fields survive', () => {
    const original = { httpRequest: { method: 'GET', path: '/api', clientIp: '10.0.0.0/8', keepAlive: true } };
    const cleared = mergeUnmodeledFields(original, buildExpectationJson(base, action));
    expect(cleared['httpRequest']).not.toHaveProperty('clientIp');
    expect((cleared['httpRequest'] as Record<string, unknown>)['keepAlive']).toBe(true);
    const changed = mergeUnmodeledFields(original, buildExpectationJson({ ...base, clientIp: '!192.168.0.0/16' }, action));
    expect((changed['httpRequest'] as Record<string, unknown>)['clientIp']).toBe('!192.168.0.0/16');
  });
});

describe('clientIp reaches every language emitter as its typed setter', () => {
  const matcher: StandardMatcher = { ...base, clientIp: '!10.0.0.0/8' };
  const url = 'http://localhost:1080';
  type Emitter = (m: StandardMatcher, a: StandardActionPayload, u: string) => string;

  const cases: Array<[string, Emitter, string]> = [
    ['java', standardToJava, '.withClientIp("!10.0.0.0/8")'],
    ['curl', standardToCurl, '"clientIp":"!10.0.0.0/8"'],
    ['node', standardToNode, '"clientIp": "!10.0.0.0/8"'],
    ['python', standardToPython, 'client_ip="!10.0.0.0/8"'],
    ['go', standardToGo, 'ClientIP("!10.0.0.0/8")'],
    ['csharp', standardToCsharp, 'ClientIp = "!10.0.0.0/8"'],
    ['ruby', standardToRuby, 'client_ip: "!10.0.0.0/8"'],
    ['rust', standardToRust, '.client_ip("!10.0.0.0/8")'],
  ];

  it.each(cases)('%s emits the clientIp literal', (_lang, emit, literal) => {
    expect(emit(matcher, action, url)).toContain(literal);
  });

  it('no emitter writes clientIp when it is unset', () => {
    for (const [lang, emit] of cases) {
      expect(emit(base, action, url), lang).not.toMatch(/client_?ip/i);
    }
  });
});
