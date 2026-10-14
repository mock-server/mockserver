import { describe, it, expect, vi, afterEach } from 'vitest';
import { applyExpectationFix, describePatch, previewExpectationFix } from '../lib/expectationFix';
import { JsonPatchTestFailedError, type JsonPatchOperation } from '../lib/jsonPatch';

const params = { host: '127.0.0.1', port: '1080', secure: false };
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

const registered = {
  id: 'exp-1',
  priority: 0,
  httpRequest: { method: 'GET', path: '/order' },
  httpResponse: { statusCode: 200, body: 'ok' },
  times: { unlimited: true },
};

const patch: JsonPatchOperation[] = [
  { op: 'test', path: '/httpRequest/path', value: '/order' },
  { op: 'replace', path: '/httpRequest/path', value: '/orders' },
];

function stubServer(current: Record<string, unknown>, lintWarnings?: string) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url.includes('/mockserver/retrieve')) {
      return { ok: true, status: 200, statusText: 'OK', json: async () => [current] } as unknown as Response;
    }
    const headers = new Headers(lintWarnings === undefined ? {} : { 'x-mockserver-lint-warnings': lintWarnings });
    return { ok: true, status: 201, statusText: 'Created', headers, text: async () => '', json: async () => JSON.parse(String(init?.body)) } as unknown as Response;
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('previewExpectationFix', () => {
  it('reads the registered expectation by id and patches a copy, without writing anything', async () => {
    const fetchMock = stubServer(registered);

    const preview = await previewExpectationFix(params, 'exp-1', patch);

    expect(preview.before).toEqual(registered);
    expect(preview.after).toEqual({ ...registered, httpRequest: { method: 'GET', path: '/orders' } });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('http://127.0.0.1:1080/mockserver/retrieve?type=active_expectations&format=json');
    expect(JSON.parse(String(init?.body))).toEqual({ id: 'exp-1' });
  });
});

describe('applyExpectationFix', () => {
  it('re-reads the expectation and sends the patched whole expectation to PUT /mockserver/expectation', async () => {
    const fetchMock = stubServer(registered);

    await applyExpectationFix(params, 'exp-1', patch);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [url, init] = fetchMock.mock.calls[1]!;
    expect(url).toBe('http://127.0.0.1:1080/mockserver/expectation');
    expect(init?.method).toBe('PUT');
    expect(JSON.parse(String(init?.body))).toEqual({ ...registered, httpRequest: { method: 'GET', path: '/orders' } });
  });

  it('returns the patched expectation and the lint warning count the server reported', async () => {
    stubServer(registered, '2');

    const result = await applyExpectationFix(params, 'exp-1', patch);

    expect(result).toEqual({ expectation: { ...registered, httpRequest: { method: 'GET', path: '/orders' } }, lintWarnings: 2 });
  });

  it('reports no lint warnings when the server sends no lint header', async () => {
    stubServer(registered);

    expect((await applyExpectationFix(params, 'exp-1', patch)).lintWarnings).toBe(0);
  });

  it('writes nothing when the expectation changed since the fix was suggested', async () => {
    const fetchMock = stubServer({ ...registered, httpRequest: { method: 'GET', path: '/edited-meanwhile' } });

    await expect(applyExpectationFix(params, 'exp-1', patch)).rejects.toBeInstanceOf(JsonPatchTestFailedError);
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/mockserver/expectation'))).toHaveLength(0);
  });
});

describe('describePatch', () => {
  it('lists each change with the value its test operation guards', () => {
    expect(describePatch([
      ...patch,
      { op: 'test', path: '/httpRequest/headers/X~1Tenant', value: ['blue'] },
      { op: 'remove', path: '/httpRequest/headers/X~1Tenant' },
      { op: 'add', path: '/httpRequest/secure', value: false },
    ])).toEqual([
      { field: 'path', kind: 'replace', before: '/order', after: '/orders' },
      { field: 'headers.X/Tenant', kind: 'remove', before: ['blue'] },
      { field: 'secure', kind: 'add', after: false },
    ]);
  });
});
