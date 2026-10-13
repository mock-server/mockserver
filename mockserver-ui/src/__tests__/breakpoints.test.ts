import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  registerBreakpointMatcher,
  listBreakpointMatchers,
  removeBreakpointMatcher,
  clearBreakpointMatchers,
  formatStatusRange,
  type BreakpointMatcherListResponse,
} from '../lib/breakpoints';

const params = { host: '127.0.0.1', port: '1080', secure: false };

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Breakpoint matcher management (the surviving REST surface)
// ---------------------------------------------------------------------------

describe('registerBreakpointMatcher', () => {
  it('sends PUT to /mockserver/breakpoint/matcher with httpRequest, phases, and clientId', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ id: 'match-1', phases: ['REQUEST'] }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await registerBreakpointMatcher(
      params,
      { method: 'GET', path: '/api/.*' },
      ['REQUEST'],
      'my-client-id',
    );

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('http://127.0.0.1:1080/mockserver/breakpoint/matcher');
    expect(init.method).toBe('PUT');
    const body = JSON.parse(init.body as string);
    expect(body.httpRequest).toEqual({ method: 'GET', path: '/api/.*' });
    expect(body.phases).toEqual(['REQUEST']);
    expect(body.clientId).toBe('my-client-id');
    expect(body.skipCount).toBeUndefined();
    expect(result.id).toBe('match-1');
  });

  it('includes skipCount in the body when a positive value is provided', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ id: 'match-2', phases: ['REQUEST'] }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await registerBreakpointMatcher(
      params,
      { path: '/api/.*' },
      ['REQUEST'],
      'my-client-id',
      2,
    );

    const [, init] = fetchMock.mock.calls[0]!;
    const body = JSON.parse(init.body as string);
    expect(body.skipCount).toBe(2);
  });

  it('sends a response status range as responseStatusCodeMin/Max', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ id: 'match-s', phases: ['RESPONSE'] }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await registerBreakpointMatcher(params, { path: '.*' }, ['RESPONSE'], 'c', undefined, { min: 400, max: 499 });
    await registerBreakpointMatcher(params, { path: '.*' }, ['RESPONSE'], 'c', undefined, { min: 500 });
    await registerBreakpointMatcher(params, { path: '.*' }, ['RESPONSE'], 'c');

    const bodies = fetchMock.mock.calls.map(([, init]) => JSON.parse((init as RequestInit).body as string));
    expect(bodies[0]).toMatchObject({ responseStatusCodeMin: 400, responseStatusCodeMax: 499 });
    expect(bodies[1].responseStatusCodeMin).toBe(500);
    expect(bodies[1]).not.toHaveProperty('responseStatusCodeMax');
    expect(bodies[2]).not.toHaveProperty('responseStatusCodeMin');
    expect(bodies[2]).not.toHaveProperty('responseStatusCodeMax');
  });

  it('omits skipCount when it is zero', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ id: 'match-3', phases: ['REQUEST'] }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await registerBreakpointMatcher(
      params,
      { path: '/api/.*' },
      ['REQUEST'],
      'my-client-id',
      0,
    );

    const [, init] = fetchMock.mock.calls[0]!;
    const body = JSON.parse(init.body as string);
    expect(body.skipCount).toBeUndefined();
  });
});

describe('listBreakpointMatchers', () => {
  it('fetches from GET /mockserver/breakpoint/matchers', async () => {
    const body: BreakpointMatcherListResponse = {
      matchers: [
        { id: 'm1', httpRequest: { path: '/a' }, phases: ['REQUEST'], clientId: 'c1' },
      ],
    };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => body,
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await listBreakpointMatchers(params);

    expect(fetchMock).toHaveBeenCalledOnce();
    const url = fetchMock.mock.calls[0]![0] as string;
    expect(url).toBe('http://127.0.0.1:1080/mockserver/breakpoint/matchers');
    expect(result.matchers).toHaveLength(1);
    expect(result.matchers[0]!.id).toBe('m1');
  });
});

describe('removeBreakpointMatcher', () => {
  it('sends PUT to /mockserver/breakpoint/matcher/remove with id', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200 });
    vi.stubGlobal('fetch', fetchMock);

    await removeBreakpointMatcher(params, 'match-1');

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('http://127.0.0.1:1080/mockserver/breakpoint/matcher/remove');
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body as string)).toEqual({ id: 'match-1' });
  });
});

describe('clearBreakpointMatchers', () => {
  it('sends PUT to /mockserver/breakpoint/matcher/clear', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200 });
    vi.stubGlobal('fetch', fetchMock);

    await clearBreakpointMatchers(params);

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('http://127.0.0.1:1080/mockserver/breakpoint/matcher/clear');
    expect(init.method).toBe('PUT');
  });
});

describe('formatStatusRange', () => {
  it('describes each shape of range, and nothing when there is none', () => {
    expect(formatStatusRange(400, 499)).toBe('status 400-499');
    expect(formatStatusRange(404, 404)).toBe('status 404');
    expect(formatStatusRange(500)).toBe('status >=500');
    expect(formatStatusRange(undefined, 399)).toBe('status <=399');
    expect(formatStatusRange()).toBeNull();
  });
});
