import { describe, it, expect, vi, afterEach } from 'vitest';
import { testTemplate, templateExcerpt, recordedRequestOf, expandTabs, caretOffset, TemplateTestRefused } from '../lib/templateTest';

const params = { host: 'localhost', port: '1080', secure: false };

function stubFetch(body: string, status = 200) {
  const fetchMock = vi.fn().mockResolvedValue(new Response(body, { status }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('testTemplate', () => {
  it('PUTs the template, action type and sample request to /mockserver/template/test', async () => {
    const fetchMock = stubFetch(JSON.stringify({ valid: true, renderedResponse: { statusCode: 200 } }));

    await testTemplate(params, {
      templateType: 'MUSTACHE',
      template: '{ "statusCode": 200 }',
      actionType: 'FORWARD_TEMPLATE',
      request: { method: 'POST', path: '/orders' },
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('http://localhost:1080/mockserver/template/test');
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body as string)).toEqual({
      templateType: 'MUSTACHE',
      template: '{ "statusCode": 200 }',
      actionType: 'FORWARD_TEMPLATE',
      request: { method: 'POST', path: '/orders' },
    });
  });

  it('leaves the request out when there is none, so the server uses its default', async () => {
    const fetchMock = stubFetch(JSON.stringify({ valid: true }));

    await testTemplate(params, { templateType: 'VELOCITY', template: 'x', actionType: 'RESPONSE_TEMPLATE' });

    const init = fetchMock.mock.calls[0]![1] as RequestInit;
    expect(JSON.parse(init.body as string)).not.toHaveProperty('request');
  });

  it('returns the report with the error position and warnings, defaulting what the server left out', async () => {
    stubFetch(JSON.stringify({
      valid: false,
      durationMillis: 3,
      error: { message: 'Encountered "#end"', line: 2, column: 5, hint: 'close the block' },
      warnings: [{ message: '$request.nope did not resolve', line: 1, column: 9 }],
    }));

    const report = await testTemplate(params, { templateType: 'VELOCITY', template: 'x', actionType: 'RESPONSE_TEMPLATE' });

    expect(report.valid).toBe(false);
    expect(report.timedOut).toBe(false);
    expect(report.error).toEqual({ message: 'Encountered "#end"', line: 2, column: 5, hint: 'close the block' });
    expect(report.warnings).toEqual([{ message: '$request.nope did not resolve', line: 1, column: 9 }]);
  });

  it('defaults warnings to an empty list', async () => {
    stubFetch(JSON.stringify({ valid: true, timedOut: true }));

    const report = await testTemplate(params, { templateType: 'VELOCITY', template: 'x', actionType: 'RESPONSE_TEMPLATE' });

    expect(report.warnings).toEqual([]);
    expect(report.timedOut).toBe(true);
  });

  it('turns a 429 into a refusal that carries the server reason', async () => {
    stubFetch(JSON.stringify({ error: '2 template dry runs are already running; retry when one finishes' }), 429);

    const call = testTemplate(params, { templateType: 'VELOCITY', template: 'x', actionType: 'RESPONSE_TEMPLATE' });

    await expect(call).rejects.toBeInstanceOf(TemplateTestRefused);
    await expect(call).rejects.toMatchObject({ status: 429, message: '2 template dry runs are already running; retry when one finishes' });
  });

  it('turns a 400 with a reason into a refusal', async () => {
    stubFetch(JSON.stringify({ error: 'template is required, as a string' }), 400);

    await expect(testTemplate(params, { templateType: 'VELOCITY', template: ' ', actionType: 'RESPONSE_TEMPLATE' }))
      .rejects.toMatchObject({ name: 'TemplateTestRefused', status: 400, message: 'template is required, as a string' });
  });

  it('reports any other failure with its status and body', async () => {
    stubFetch('boom', 500);

    await expect(testTemplate(params, { templateType: 'VELOCITY', template: 'x', actionType: 'RESPONSE_TEMPLATE' }))
      .rejects.toThrow('MockServer returned 500: boom');
  });
});

describe('templateExcerpt', () => {
  const template = 'one\ntwo\nthree\nfour\nfive\nsix';

  it('returns the failing line with two lines either side', () => {
    expect(templateExcerpt(template, 4)).toEqual([
      { number: 2, text: 'two', failing: false },
      { number: 3, text: 'three', failing: false },
      { number: 4, text: 'four', failing: true },
      { number: 5, text: 'five', failing: false },
      { number: 6, text: 'six', failing: false },
    ]);
  });

  it('stops at the start of the template and handles CRLF line ends', () => {
    expect(templateExcerpt('a\r\nb\r\nc', 1)).toEqual([
      { number: 1, text: 'a', failing: true },
      { number: 2, text: 'b', failing: false },
      { number: 3, text: 'c', failing: false },
    ]);
  });

  it('is empty for a line outside the template', () => {
    expect(templateExcerpt(template, 7)).toEqual([]);
    expect(templateExcerpt(template, 0)).toEqual([]);
  });
});

describe('recordedRequestOf', () => {
  it('returns the httpRequest of a recorded row', () => {
    expect(recordedRequestOf({ httpRequest: { path: '/a' }, httpResponse: { statusCode: 200 } })).toEqual({ path: '/a' });
  });

  it('is undefined for a row without one', () => {
    expect(recordedRequestOf({ httpResponse: { statusCode: 200 } })).toBeUndefined();
    expect(recordedRequestOf({ httpRequest: ['x'] })).toBeUndefined();
  });
});

describe('expandTabs and caretOffset', () => {
  it('expands each tab to the next multiple-of-8 column', () => {
    expect(expandTabs('\tx')).toBe('        x');
    expect(expandTabs('ab\tc\td')).toBe('ab      c       d');
  });

  it('uses a Velocity column as it is, because Velocity already counts tab stops', () => {
    expect(caretOffset('\t#if(', 9, 'VELOCITY')).toBe(8);
  });

  it('converts a character column of other engines to the expanded offset, past the end too', () => {
    expect(caretOffset('\tmissing', 2, 'JAVASCRIPT')).toBe(8);
    expect(caretOffset('ab', 4, 'JAVASCRIPT')).toBe(3);
  });
});
