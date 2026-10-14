import type { JsonListItem } from '../types';
import { isNonHttpRequestRow } from './requestKind';

/**
 * One list of captured traffic, newest first, with one row per request.
 *
 * The server sends two overlapping windows: `recordedRequests` holds every
 * request it received (proxied ones included, for the Received Requests panel)
 * and `proxiedRequests` holds every forwarded exchange. Simply concatenating
 * them shows a proxied request twice: once without a response, once with the
 * upstream's. A received row whose `correlationId` matches a proxied row is
 * dropped in favour of the proxied one, which carries the response. A received
 * row of a request still in flight (no proxied row yet) is kept. DNS queries
 * and binary messages are left out: they are listed only in Received Requests.
 *
 * Both inputs are newest first; the result interleaves them by `timestamp`
 * (the server's `yyyy-MM-dd HH:mm:ss.SSS`, which sorts as text), keeping each
 * input's own order where timestamps are missing or equal.
 */
export function combineTraffic(
  proxied: readonly JsonListItem[],
  received: readonly JsonListItem[],
): JsonListItem[] {
  const forwarded = new Set<string>();
  for (const item of proxied) {
    if (item.correlationId) forwarded.add(item.correlationId);
  }
  const receivedOnly = received.filter((item) =>
    !isNonHttpRequestRow(item) && (!item.correlationId || !forwarded.has(item.correlationId)));

  const result: JsonListItem[] = [];
  let p = 0;
  let r = 0;
  while (p < proxied.length && r < receivedOnly.length) {
    const pt = proxied[p]!.timestamp;
    const rt = receivedOnly[r]!.timestamp;
    if (pt && rt && rt > pt) {
      result.push(receivedOnly[r++]!);
    } else {
      result.push(proxied[p++]!);
    }
  }
  while (p < proxied.length) result.push(proxied[p++]!);
  while (r < receivedOnly.length) result.push(receivedOnly[r++]!);
  return result;
}
