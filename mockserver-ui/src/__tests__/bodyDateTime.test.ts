/// <reference types="vite/client" />
/**
 * A JSON Path or XPath body's date-time comparison (`dateTime`): it survives the Composer's
 * load → save round trip unchanged, and every language tab renders it with that client's builder.
 */
import { describe, it, expect } from 'vitest';
import { matcherFromExpectation } from '../components/ComposerView';
import {
  buildExpectationJson,
  standardToJava, standardToNode, standardToPython,
  standardToGo, standardToCsharp, standardToRuby, standardToRust,
  type StandardActionPayload, type StandardMatcher,
} from '../lib/standardCodegen';
import { bodyDateTimeError, bodyDateTimeFromText } from '../lib/matcherValue';

const fixtureModules = import.meta.glob('../../../test-fixtures/expectations/matcher_body_*_date_time.json', {
  eager: true,
  import: 'default',
}) as Record<string, Record<string, unknown>>;

const action: StandardActionPayload = {
  type: 'static',
  static: { statusCode: 200, body: 'dated', contentType: '' },
};

function load(httpRequest: Record<string, unknown>): StandardMatcher {
  return matcherFromExpectation({ key: 'k', value: { httpRequest } } as never);
}

function savedBody(matcher: StandardMatcher): unknown {
  return (buildExpectationJson(matcher, action)['httpRequest'] as Record<string, unknown>)['body'];
}

function matcher(overrides: Partial<StandardMatcher>): StandardMatcher {
  return {
    id: '', method: 'POST', path: '/orders', headers: '', queryString: '', cookies: '',
    pathParams: '', body: '$.order.created', bodyBinary: false, bodyMatcherType: 'json-path',
    priority: 0, times: 0, ...overrides,
  };
}

const jsonPath = matcher({ bodyDateTime: '{"after":"now -1 days","zone":"Europe/London"}' });
const xpath = matcher({ bodyMatcherType: 'xpath', body: '/order/@expires', bodyDateTime: '{"not":true,"before":"2026-01-01"}' });

describe('body date-time comparison survives the Composer', () => {
  it('round-trips both shared fixtures exactly', () => {
    expect(Object.keys(fixtureModules)).toHaveLength(2);
    for (const fixture of Object.values(fixtureModules)) {
      const request = fixture['httpRequest'] as Record<string, unknown>;
      expect(savedBody(load(request))).toEqual(request['body']);
    }
  });

  it('writes dateTime only for a JSON Path or XPath body', () => {
    expect(savedBody(jsonPath)).toEqual({ type: 'JSON_PATH', jsonPath: '$.order.created', dateTime: { after: 'now -1 days', zone: 'Europe/London' } });
    expect(savedBody(xpath)).toEqual({ type: 'XPATH', xpath: '/order/@expires', dateTime: { not: true, before: '2026-01-01' } });
    expect(savedBody(matcher({ bodyMatcherType: 'regex', body: '.*', bodyDateTime: '{"after":"now"}' }))).toEqual({ type: 'REGEX', regex: '.*' });
    expect(savedBody(matcher({ bodyDateTime: '' }))).toEqual({ type: 'JSON_PATH', jsonPath: '$.order.created' });
  });

  it('flags text that is not a date-time comparison', () => {
    expect(bodyDateTimeError('')).toBeUndefined();
    expect(bodyDateTimeError('{"after":"now"}')).toBeUndefined();
    expect(bodyDateTimeError('{"after":"now","before":"now"}')).toMatch(/one of before, after or equalToDateTime/);
    expect(bodyDateTimeError('after now')).toMatch(/date-time matcher/);
    // a JSON object is still sent, so the server rejects it loudly rather than the comparison vanishing
    expect(bodyDateTimeFromText('{"zone":"UTC"}')).toEqual({ zone: 'UTC' });
    expect(bodyDateTimeFromText('after now')).toBeUndefined();
  });
});

describe('every language tab renders the body date-time comparison', () => {
  const base = 'http://localhost:1080';

  it('java', () => {
    const java = standardToJava(jsonPath, action);
    expect(java).toContain('.withBody(jsonPath("$.order.created", NottableDateTimeString.after("now -1 days").withZone("Europe/London")))');
    expect(java).toContain('import org.mockserver.model.NottableDateTimeString;');
    expect(standardToJava(xpath, action)).toContain('.withBody(xpath("/order/@expires", NottableDateTimeString.before("2026-01-01").withNot(true)))');
  });

  it('python', () => {
    const python = standardToPython(jsonPath, action, base);
    expect(python).toContain('JsonPathBody(json_path="$.order.created", date_time=DateTimeMatcher.after("now -1 days").with_zone("Europe/London"))');
    expect(python).toMatch(/^\s+DateTimeMatcher,$/m);
    expect(standardToPython(xpath, action, base)).toContain('XPathBody(xpath="/order/@expires", date_time=DateTimeMatcher.before("2026-01-01").with_not(True))');
  });

  it('ruby', () => {
    const ruby = standardToRuby(jsonPath, action, base);
    expect(ruby).toContain('date_time: MockServer::DateTimeMatcher.after("now -1 days").with_zone("Europe/London")');
    expect(standardToRuby(xpath, action, base)).toContain('date_time: MockServer::DateTimeMatcher.before("2026-01-01").with_not(true)');
  });

  it('go', () => {
    expect(standardToGo(jsonPath, action, base)).toContain('mockserver.JSONPathBody("$.order.created").WithDateTime(mockserver.After("now -1 days").WithZone("Europe/London"))');
    expect(standardToGo(xpath, action, base)).toContain('mockserver.XPathBody("/order/@expires").WithDateTime(mockserver.Before("2026-01-01").WithNot(true))');
  });

  it('rust', () => {
    expect(standardToRust(jsonPath, action, base)).toContain('Body::json_path("$.order.created").with_date_time(MatcherValue::after("now -1 days").with_zone("Europe/London"))');
    expect(standardToRust(xpath, action, base)).toContain('Body::xpath("/order/@expires").with_date_time(MatcherValue::before("2026-01-01").with_not(true))');
  });

  it('csharp', () => {
    expect(standardToCsharp(jsonPath, action, base)).toContain('Body.OfJsonPath("$.order.created").WithDateTime(MatcherValue.After("now -1 days").WithZone("Europe/London"))');
    expect(standardToCsharp(xpath, action, base)).toContain('Body.OfXPath("/order/@expires").WithDateTime(MatcherValue.Before("2026-01-01").WithNot(true))');
  });

  it('node sends the wire form', () => {
    expect(standardToNode(jsonPath, action, base)).toContain('"dateTime": {');
  });

  it('keeps a dateTime the builders cannot express rather than dropping it', () => {
    const odd = matcher({ bodyDateTime: '{"after":"now","parameterStyle":"FORM"}' });
    expect(savedBody(odd)).toEqual({ type: 'JSON_PATH', jsonPath: '$.order.created', dateTime: { after: 'now', parameterStyle: 'FORM' } });
    expect(standardToGo(odd, action, base)).not.toContain('WithDateTime');
    expect(standardToGo(odd, action, base)).toContain('"parameterStyle"');
    expect(standardToRust(odd, action, base)).not.toContain('with_date_time');
    expect(standardToCsharp(odd, action, base)).not.toContain('WithDateTime');
    expect(standardToRuby(odd, action, base)).toContain('date_time: {');
    expect(standardToJava(odd, action)).not.toContain('NottableDateTimeString');
    expect(standardToJava(odd, action)).toContain('httpRequest.body.dateTime');
  });
});
