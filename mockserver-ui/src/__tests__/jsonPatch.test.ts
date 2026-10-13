import { describe, it, expect } from 'vitest';
import { applyJsonPatch, jsonEqual, JsonPatchTestFailedError } from '../lib/jsonPatch';

describe('applyJsonPatch', () => {
  const expectation = {
    id: 'exp-1',
    httpRequest: { path: '/order', headers: { 'X-Tenant': ['blue'], 'a/b~c': ['1'] }, body: { sku: 'A1', gift: true } },
    httpResponse: { statusCode: 200 },
  };

  it('applies guarded replace, remove and add operations without changing its input', () => {
    const snapshot = JSON.stringify(expectation);

    const patched = applyJsonPatch(expectation, [
      { op: 'test', path: '/httpRequest/path', value: '/order' },
      { op: 'replace', path: '/httpRequest/path', value: '/orders' },
      { op: 'test', path: '/httpRequest/body/gift', value: true },
      { op: 'remove', path: '/httpRequest/body/gift' },
      { op: 'add', path: '/httpRequest/method', value: 'GET' },
      { op: 'replace', path: '/httpRequest/headers/a~1b~0c', value: ['2'] },
    ]);

    expect(patched).toEqual({
      id: 'exp-1',
      httpRequest: { path: '/orders', method: 'GET', headers: { 'X-Tenant': ['blue'], 'a/b~c': ['2'] }, body: { sku: 'A1' } },
      httpResponse: { statusCode: 200 },
    });
    expect(JSON.stringify(expectation)).toBe(snapshot);
  });

  it('fails a test operation when the value changed since the patch was made', () => {
    expect(() => applyJsonPatch(expectation, [
      { op: 'test', path: '/httpRequest/headers/X-Tenant', value: ['green'] },
      { op: 'replace', path: '/httpRequest/headers/X-Tenant', value: ['red'] },
    ])).toThrow(JsonPatchTestFailedError);
  });

  it('fails a test operation on a value that is no longer there', () => {
    expect(() => applyJsonPatch(expectation, [{ op: 'test', path: '/httpRequest/cookies', value: {} }]))
      .toThrow(JsonPatchTestFailedError);
  });

  it('rejects replacing or removing a missing value rather than creating it', () => {
    expect(() => applyJsonPatch(expectation, [{ op: 'replace', path: '/httpRequest/missing', value: 1 }])).toThrow('Path not found');
    expect(() => applyJsonPatch(expectation, [{ op: 'remove', path: '/nope/deeper' }])).toThrow('Path not found');
  });

  it('indexes into arrays and appends with -', () => {
    expect(applyJsonPatch({ list: [1, 2] }, [
      { op: 'test', path: '/list/1', value: 2 },
      { op: 'replace', path: '/list/0', value: 9 },
      { op: 'add', path: '/list/-', value: 3 },
      { op: 'remove', path: '/list/1' },
    ])).toEqual({ list: [9, 3] });
  });
});

describe('jsonEqual', () => {
  it('ignores object key order but not array order', () => {
    expect(jsonEqual({ a: 1, b: [1, 2] }, { b: [1, 2], a: 1 })).toBe(true);
    expect(jsonEqual([1, 2], [2, 1])).toBe(false);
    expect(jsonEqual({ a: 1 }, { a: 1, b: undefined })).toBe(false);
    expect(jsonEqual(null, {})).toBe(false);
  });
});
