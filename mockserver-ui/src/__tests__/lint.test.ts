import { describe, it, expect, vi, afterEach } from 'vitest';
import { lintActiveExpectations, lintWarningCount } from '../lib/lint';

const params = { host: '127.0.0.1', port: '1080', secure: false };

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('lintActiveExpectations', () => {
  it('PUTs an empty body to /mockserver/lint and returns the report', async () => {
    const report = {
      expectationsChecked: 2,
      inactiveExpectationsSkipped: 0,
      issueCount: 1,
      errorCount: 0,
      warningCount: 1,
      infoCount: 0,
      issuesTruncated: false,
      shadowCheckTruncated: false,
      issues: [{ rule: 'SHADOWED', severity: 'WARNING', position: 2, message: 'never served', relatedExpectationId: 'a' }],
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(report), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await lintActiveExpectations(params);

    expect(fetchMock.mock.calls[0]![0]).toBe('http://127.0.0.1:1080/mockserver/lint');
    expect(fetchMock.mock.calls[0]![1]).toMatchObject({ method: 'PUT' });
    expect(fetchMock.mock.calls[0]![1]?.body).toBeUndefined();
    expect(result.warningCount).toBe(1);
    expect(result.issues[0]!.relatedExpectationId).toBe('a');
  });

  it('defaults missing fields so the dialog can render a partial report', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 200 })));
    const result = await lintActiveExpectations(params);
    expect(result.issues).toEqual([]);
    expect(result.expectationsChecked).toBe(0);
    expect(result.issuesTruncated).toBe(false);
  });

  it('throws in the MockServer returned shape on a non-2xx response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"error":"failed to lint"}', { status: 400 })));
    await expect(lintActiveExpectations(params)).rejects.toThrow('MockServer returned 400: {"error":"failed to lint"}');
  });
});

describe('lintWarningCount', () => {
  const withHeader = (value?: string) =>
    new Response('', { status: 201, headers: value === undefined ? {} : { 'x-mockserver-lint-warnings': value } });

  it('reads the header count', () => {
    expect(lintWarningCount(withHeader('3'))).toBe(3);
  });

  it('is 0 when the header is absent or not a positive number', () => {
    expect(lintWarningCount(withHeader())).toBe(0);
    expect(lintWarningCount(withHeader('none'))).toBe(0);
    expect(lintWarningCount(withHeader('0'))).toBe(0);
  });
});
