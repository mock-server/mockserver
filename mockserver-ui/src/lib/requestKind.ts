import type { JsonListItem } from '../types';

/**
 * Whether a recorded request is a DNS query or a binary message rather than an
 * HTTP request. The server records both alongside HTTP requests; they have no
 * method, path, headers or body, so HTTP tools must leave them out.
 */
export function isNonHttpRequest(request: unknown): boolean {
  if (!request || typeof request !== 'object' || Array.isArray(request)) return false;
  const fields = request as Record<string, unknown>;
  return typeof fields['dnsName'] === 'string' || 'binaryData' in fields;
}

/** Whether a request row holds a DNS query or a binary message. */
export function isNonHttpRequestRow(item: JsonListItem): boolean {
  return isNonHttpRequest(item.value['httpRequest']);
}
