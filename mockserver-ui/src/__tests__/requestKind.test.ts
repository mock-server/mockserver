import { describe, it, expect } from 'vitest';
import { isNonHttpRequest } from '../lib/requestKind';

describe('isNonHttpRequest', () => {
  it('recognises a recorded DNS query and binary message', () => {
    expect(isNonHttpRequest({ dnsName: 'api.example.com', dnsType: 'A' })).toBe(true);
    expect(isNonHttpRequest({ binaryData: 'AAEC', socketAddress: { host: 'localhost', port: 1080 } })).toBe(true);
    expect(isNonHttpRequest({ binaryData: '' })).toBe(true);
  });

  it('treats an HTTP request, and anything that is not a request, as not DNS or binary', () => {
    expect(isNonHttpRequest({ method: 'GET', path: '/' })).toBe(false);
    expect(isNonHttpRequest({})).toBe(false);
    expect(isNonHttpRequest(undefined)).toBe(false);
    expect(isNonHttpRequest(['dnsName'])).toBe(false);
    expect(isNonHttpRequest('dnsName')).toBe(false);
  });
});
