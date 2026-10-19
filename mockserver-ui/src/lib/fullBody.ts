/**
 * Loading bodies the server shortened in dashboard updates. Each update cuts a long request or
 * response body and marks it (TruncatedBody); GET /mockserver/logEntryBody returns that log entry's
 * request or response with its body whole.
 */
import { buildBaseUrl } from './mcpClient';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import type { JsonListItem, TruncatedBody } from '../types';

export type FullMessages = { httpRequest?: Record<string, unknown>; httpResponse?: Record<string, unknown> };

export async function fetchFullMessage(
  params: ConnectionParams,
  marker: TruncatedBody,
  signal?: AbortSignal,
): Promise<Record<string, unknown>> {
  if (!isLoadable(marker)) {
    throw new Error('This shortened body cannot be loaded from its log entry');
  }
  const query = `id=${encodeURIComponent(marker.logEntryId)}&part=${encodeURIComponent(marker.part)}`;
  const res = await fetch(`${buildBaseUrl(params)}/mockserver/logEntryBody?${query}`, { signal });
  const body = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok) {
    throw new Error(typeof body.error === 'string' ? body.error : `HTTP ${res.status} ${res.statusText}`);
  }
  const field = marker.part === 'request' ? 'httpRequest' : 'httpResponse';
  const message = body[field];
  if (!message || typeof message !== 'object' || Array.isArray(message)) {
    throw new Error(`MockServer returned no ${marker.part} for this log entry`);
  }
  return message as Record<string, unknown>;
}

export function isLoadable(marker: TruncatedBody): boolean {
  return marker.part !== 'expectation' && marker.loadable !== false;
}

export function hasTruncatedBodies(item: JsonListItem | undefined | null): boolean {
  return !!item?.truncatedBodies && (!!item.truncatedBodies.httpRequest || !!item.truncatedBodies.httpResponse);
}

/** Fetch every shortened message of a row, whole. */
export async function fetchFullMessages(params: ConnectionParams, item: JsonListItem): Promise<FullMessages> {
  const markers = item.truncatedBodies ?? {};
  const [httpRequest, httpResponse] = await Promise.all([
    markers.httpRequest ? fetchFullMessage(params, markers.httpRequest) : Promise.resolve(undefined),
    markers.httpResponse ? fetchFullMessage(params, markers.httpResponse) : Promise.resolve(undefined),
  ]);
  return { httpRequest, httpResponse };
}

/** The row with loaded messages in place of shortened ones, and their markers removed. */
export function withFullMessages(item: JsonListItem, full: FullMessages): JsonListItem {
  if (!item.truncatedBodies || (!full.httpRequest && !full.httpResponse)) return item;
  const value = { ...item.value };
  const remaining = { ...item.truncatedBodies };
  if (full.httpRequest && remaining.httpRequest) {
    value.httpRequest = full.httpRequest;
    delete remaining.httpRequest;
  }
  if (full.httpResponse && remaining.httpResponse) {
    value.httpResponse = full.httpResponse;
    delete remaining.httpResponse;
  }
  const next: JsonListItem = { ...item, value };
  if (remaining.httpRequest || remaining.httpResponse) {
    next.truncatedBodies = remaining;
  } else {
    delete next.truncatedBodies;
  }
  return next;
}

export function formatCharacters(length: number): string {
  if (length >= 1024 * 1024) return `${(length / (1024 * 1024)).toFixed(1)} MiB`;
  if (length >= 1024) return `${Math.round(length / 1024)} KiB`;
  return `${length} characters`;
}
