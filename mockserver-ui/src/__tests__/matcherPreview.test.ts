import { describe, it, expect } from 'vitest';
import { previewMatch, toNottable, type SampleRequest } from '../lib/matcherPreview';

const baseSample: SampleRequest = {
  method: 'GET',
  path: '/api/users',
  headers: 'Accept: application/json',
  queryString: 'page=1',
  body: '',
};

describe('toNottable', () => {
  it('parses bare strings, ! negation and NottableString objects', () => {
    expect(toNottable('GET')).toEqual({ value: 'GET', not: false });
    expect(toNottable('!GET')).toEqual({ value: 'GET', not: true });
    expect(toNottable({ value: 'GET', not: true })).toEqual({ value: 'GET', not: true });
    expect(toNottable('')).toBeNull();
    expect(toNottable(undefined)).toBeNull();
  });
});

describe('previewMatch', () => {
  it('reports WOULD MATCH for an exact method + regex path + regex query', () => {
    const exp = {
      httpRequest: {
        method: 'GET',
        path: '/api/.*',
        queryStringParameters: { page: ['[0-9]+'] },
      },
    };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(true);
    expect(res.hasUnsupported).toBe(false);
    expect(res.results.every((r) => r.verdict === 'match')).toBe(true);
  });

  it('reports WOULD NOT MATCH when the path regex does not match', () => {
    const exp = { httpRequest: { method: 'GET', path: '/orders/.*' } };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(false);
    const pathRow = res.results.find((r) => r.field === 'path')!;
    expect(pathRow.verdict).toBe('mismatch');
  });

  it('honours method negation', () => {
    const exp = { httpRequest: { method: '!POST' } };
    expect(previewMatch(exp, baseSample).matches).toBe(true);
    expect(previewMatch(exp, { ...baseSample, method: 'POST' }).matches).toBe(false);
  });

  it('matches a bare httpRequest object (no wrapper)', () => {
    const res = previewMatch({ method: 'GET', path: '/api/users' }, baseSample);
    expect(res.matches).toBe(true);
  });

  it('flags a missing required header as a mismatch', () => {
    const exp = { httpRequest: { headers: { 'X-Token': ['abc'] } } };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(false);
    const row = res.results.find((r) => r.field === 'header "X-Token"')!;
    expect(row.verdict).toBe('mismatch');
    expect(row.actual).toBe('(absent)');
  });

  it('matches header values case-insensitively on the key', () => {
    const exp = { httpRequest: { headers: { accept: ['application/json'] } } };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(true);
  });

  it('treats header value as regex', () => {
    const exp = { httpRequest: { headers: { Accept: ['application/.*'] } } };
    expect(previewMatch(exp, baseSample).matches).toBe(true);
  });

  it('matches a JSON body by non-strict containment', () => {
    const exp = { httpRequest: { body: { type: 'JSON', json: '{"a":1}' } } };
    const sample = { ...baseSample, body: '{"a":1,"b":2}' };
    expect(previewMatch(exp, sample).matches).toBe(true);
  });

  it('rejects a JSON body that lacks the expected key', () => {
    const exp = { httpRequest: { body: { type: 'JSON', json: '{"a":1}' } } };
    const sample = { ...baseSample, body: '{"b":2}' };
    expect(previewMatch(exp, sample).matches).toBe(false);
  });

  it('matches a STRING subString body', () => {
    const exp = { httpRequest: { body: { type: 'STRING', string: 'hello', subString: true } } };
    expect(previewMatch(exp, { ...baseSample, body: 'well hello there' }).matches).toBe(true);
    expect(previewMatch(exp, { ...baseSample, body: 'goodbye' }).matches).toBe(false);
  });

  it('flags unsupported matcher fields and forces an inconclusive verdict', () => {
    const exp = { httpRequest: { method: 'GET', path: '/api/users', cookies: { session: 'abc' } } };
    const res = previewMatch(exp, baseSample);
    expect(res.hasUnsupported).toBe(true);
    expect(res.matches).toBe(false);
    const cookieRow = res.results.find((r) => r.field === 'cookies')!;
    expect(cookieRow.verdict).toBe('unsupported');
  });

  it('an expectation with no matcher fields matches every request', () => {
    const res = previewMatch({ httpRequest: {} }, baseSample);
    expect(res.results.length).toBe(0);
    expect(res.matches).toBe(true);
  });

  it('reports an invalid body regex as unsupported rather than crashing', () => {
    const exp = { httpRequest: { body: { type: 'REGEX', regex: '(' } } };
    const res = previewMatch(exp, baseSample);
    expect(res.hasUnsupported).toBe(true);
  });

  it('does NOT report a false match for a JSON-schema header value the sample violates (COR-01)', () => {
    // The schema requires the Accept header to be a 3-char uppercase token; the sample
    // sends "application/json" which would be REJECTED by the server. The preview must
    // not silently treat the empty-matcher list as a presence check and claim a match.
    const exp = {
      httpRequest: {
        headers: { Accept: [{ schema: { type: 'string', pattern: '^[A-Z]{3}$' } }] },
      },
    };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(false);
    expect(res.hasUnsupported).toBe(true);
    const row = res.results.find((r) => r.field === 'header "Accept"')!;
    expect(row.verdict).toBe('unsupported');
  });

  it('flags a JSON-schema query-parameter value as unsupported (COR-01)', () => {
    const exp = {
      httpRequest: {
        queryStringParameters: { page: [{ schema: { type: 'integer' } }] },
      },
    };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(false);
    expect(res.hasUnsupported).toBe(true);
    const row = res.results.find((r) => r.field === 'query parameter "page"')!;
    expect(row.verdict).toBe('unsupported');
  });

  it('flags a date-time header value as unsupported, not a presence check', () => {
    const exp = { httpRequest: { headers: { Accept: [{ after: 'now -1 hours' }] } } };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(false);
    expect(res.hasUnsupported).toBe(true);
    const row = res.results.find((r) => r.field === 'header "Accept"')!;
    expect(row.verdict).toBe('unsupported');
  });

  it('flags an object-form negated header key as unsupported, not a false match', () => {
    const exp = { httpRequest: { headers: { '!X-Internal': ['true'] } } };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(false);
    expect(res.hasUnsupported).toBe(true);
    const row = res.results.find((r) => r.field === 'header "X-Internal"')!;
    expect(row.verdict).toBe('unsupported');
  });

  it('flags an array-form notted key matcher as unsupported', () => {
    const exp = {
      httpRequest: {
        headers: [{ name: { value: 'X-Internal', not: true }, values: ['true'] }],
      },
    };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(false);
    expect(res.hasUnsupported).toBe(true);
    const row = res.results.find((r) => r.field === 'header "X-Internal"')!;
    expect(row.verdict).toBe('unsupported');
  });

  it('flags a JSON-schema method matcher as unsupported rather than crashing', () => {
    const exp = { httpRequest: { method: { schema: { type: 'string', enum: ['GET'] } } } };
    const res = previewMatch(exp, baseSample);
    expect(res.matches).toBe(false);
    expect(res.hasUnsupported).toBe(true);
    const row = res.results.find((r) => r.field === 'method')!;
    expect(row.verdict).toBe('unsupported');
  });
});
