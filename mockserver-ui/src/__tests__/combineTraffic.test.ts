import { describe, it, expect } from 'vitest';
import { combineTraffic } from '../lib/combineTraffic';
import type { JsonListItem } from '../types';

function row(key: string, timestamp: string | undefined, correlationId?: string): JsonListItem {
  return { key, timestamp, correlationId, value: { httpRequest: { path: `/${key}` } } };
}

describe('combineTraffic', () => {
  it('shows a proxied request once, as its proxied row (E2E-LIB-5)', () => {
    const proxied = [row('p1', '2026-10-10 10:00:01.000', 'c1')];
    const received = [row('r1', '2026-10-10 10:00:00.900', 'c1')];

    expect(combineTraffic(proxied, received).map((r) => r.key)).toEqual(['p1']);
  });

  it('keeps received rows that were not forwarded, and in-flight ones with no proxied row yet', () => {
    const proxied = [row('p1', '2026-10-10 10:00:01.000', 'c1')];
    const received = [
      row('mocked', '2026-10-10 10:00:03.000', 'c3'),
      row('inflight', '2026-10-10 10:00:02.000', 'c2'),
      row('r1', '2026-10-10 10:00:00.900', 'c1'),
      row('nocorrelation', '2026-10-10 10:00:00.500'),
    ];

    expect(combineTraffic(proxied, received).map((r) => r.key)).toEqual(['mocked', 'inflight', 'p1', 'nocorrelation']);
  });

  it('leaves out recorded DNS queries and binary messages, which only Received Requests lists', () => {
    const received: JsonListItem[] = [
      { key: 'dns', timestamp: '2026-10-10 10:00:03.000', value: { httpRequest: { dnsName: 'api.example.com', dnsType: 'A' } } },
      { key: 'binary', timestamp: '2026-10-10 10:00:02.000', value: { httpRequest: { binaryData: 'AAEC' } } },
      row('http', '2026-10-10 10:00:01.000'),
    ];

    expect(combineTraffic([], received).map((r) => r.key)).toEqual(['http']);
    expect(combineTraffic([row('p1', '2026-10-10 10:00:00.000', 'c1')], received).map((r) => r.key)).toEqual(['http', 'p1']);
  });

  it('interleaves the two lists newest first by timestamp', () => {
    const proxied = [row('p3', '2026-10-10 10:00:03.000', 'a'), row('p1', '2026-10-10 10:00:01.000', 'b')];
    const received = [row('r4', '2026-10-10 10:00:04.000', 'x'), row('r2', '2026-10-10 10:00:02.000', 'y'), row('r0', '2026-10-10 10:00:00.000', 'z')];

    expect(combineTraffic(proxied, received).map((r) => r.key)).toEqual(['r4', 'p3', 'r2', 'p1', 'r0']);
  });

  it('keeps each list in its own order when timestamps are missing', () => {
    const proxied = [row('p1', undefined), row('p2', undefined)];
    const received = [row('r1', undefined)];

    expect(combineTraffic(proxied, received).map((r) => r.key)).toEqual(['p1', 'p2', 'r1']);
  });

  it('returns the received rows themselves when nothing was proxied', () => {
    const received = [row('r1', '2026-10-10 10:00:00.000', 'c1')];
    const combined = combineTraffic([], received);
    expect(combined).toEqual(received);
    expect(combined[0]).toBe(received[0]);
  });
});
