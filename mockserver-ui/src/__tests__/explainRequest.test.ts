import { describe, it, expect, vi, afterEach } from 'vitest';
import { explainRequest, ExplainRequestError, isExplainableGroup } from '../lib/explainRequest';
import type { LogGroup } from '../types';

const params = { host: 'localhost', port: '1080', secure: false, basePath: '/ctx' };

function respond(status: number, body: string) {
  const fetchMock = vi.fn().mockResolvedValue(new Response(body, { status }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('explainRequest', () => {
  it('PUTs the encoded correlation ID to /mockserver/explain under the base path', async () => {
    const fetchMock = respond(200, JSON.stringify({
      correlationId: 'a b',
      request: { method: 'GET', path: '/orders' },
      atArrival: { outcome: 'MATCHED', expectationId: 'low' },
      expectationsChangedSince: false,
      matchedExpectation: { expectationId: 'low' },
      skippedExpectations: [{ expectationId: 'high', reason: 'FIELDS_DIFFER' }],
      shadowedExpectations: [],
      totalExpectations: 2,
      evaluatedExpectations: 2,
      truncated: false,
    }));

    const result = await explainRequest(params, 'a b');

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:1080/ctx/mockserver/explain?correlationId=a%20b', expect.objectContaining({ method: 'PUT' }));
    expect(result.matchedExpectation?.expectationId).toBe('low');
    expect(result.skippedExpectations[0]?.reason).toBe('FIELDS_DIFFER');
    expect(result.atArrival.outcome).toBe('MATCHED');
  });

  it('defaults the lists and counts a partial body leaves out', async () => {
    respond(200, JSON.stringify({ correlationId: 'c' }));

    const result = await explainRequest(params, 'c');

    expect(result.skippedExpectations).toEqual([]);
    expect(result.shadowedExpectations).toEqual([]);
    expect(result.atArrival.outcome).toBe('UNKNOWN');
    expect(result.truncated).toBe(false);
  });

  it("raises the server's own words for an unknown or evicted correlation ID", async () => {
    respond(404, JSON.stringify({ error: 'no request is recorded with this correlationId' }));

    const failure = await explainRequest(params, 'gone').catch((e: unknown) => e);

    expect(failure).toBeInstanceOf(ExplainRequestError);
    expect((failure as ExplainRequestError).message).toBe('no request is recorded with this correlationId');
    expect((failure as ExplainRequestError).status).toBe(404);
  });

  it('raises a server-error shape for any other failure', async () => {
    respond(500, 'boom');

    const failure = await explainRequest(params, 'c').catch((e: unknown) => e);

    expect(failure).not.toBeInstanceOf(ExplainRequestError);
    expect((failure as Error).message).toBe('MockServer returned 500: boom');
  });
});

function group(correlationId: string | undefined, ...types: string[]): LogGroup {
  const [first, ...rest] = types;
  return {
    key: 'k_log_group',
    correlationId,
    group: { key: 'k_log', value: { description: { json: false, first: '10:00:00', second: first ?? '' } } },
    value: rest.map((type, i) => ({ key: `c${i}`, value: { description: { json: false, first: '10:00:00', second: `${type}   ` } } })),
  };
}

describe('isExplainableGroup', () => {
  it('is true when an entry holds a request the server can explain', () => {
    expect(isExplainableGroup(group('c', 'EXPECTATION_MATCHED', 'EXPECTATION_RESPONSE'))).toBe(true);
    expect(isExplainableGroup(group('c', 'RECEIVED_REQUEST'))).toBe(true);
    expect(isExplainableGroup(group('c', 'FORWARDED_REQUEST'))).toBe(true);
    expect(isExplainableGroup(group('c', 'EXPECTATION_NOT_MATCHED', 'NO_MATCH_RESPONSE'))).toBe(true);
  });

  it('is false without a correlation ID or without a request entry', () => {
    expect(isExplainableGroup(group(undefined, 'RECEIVED_REQUEST'))).toBe(false);
    expect(isExplainableGroup(group('c', 'CREATED_EXPECTATION', 'INFO'))).toBe(false);
  });
});
