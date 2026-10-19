import { useCallback, useEffect } from 'react';
import { useConnectionParams } from './useConnectionParams';
import { useDashboardStore } from '../store';
import { fetchFullMessages, hasTruncatedBodies, withFullMessages } from '../lib/fullBody';
import { humanizeError } from '../lib/errorMessage';
import { cachedParseTraffic } from '../lib/llmTraffic';
import type { JsonListItem } from '../types';

/**
 * Loads the whole request and response of a row whose bodies the server shortened, keeps them in the
 * store (so the row shows them from then on) and returns the row with them. A row sent whole is
 * returned as it is, without a request. Every action that sends, matches, copies, compares or builds
 * a mock from a row calls this first and does nothing if it fails, so none acts on a shortened body.
 */
export function useLoadFullRow(): (item: JsonListItem) => Promise<JsonListItem> {
  const params = useConnectionParams();
  const applyFullMessages = useDashboardStore((s) => s.applyFullMessages);
  return useCallback(
    async (item: JsonListItem) => {
      if (!hasTruncatedBodies(item)) return item;
      const epoch = useDashboardStore.getState().connectionEpoch;
      const full = await fetchFullMessages(params, item);
      if (useDashboardStore.getState().connectionEpoch !== epoch) {
        throw new Error('The dashboard switched to another MockServer while the body was loading');
      }
      applyFullMessages(item.key, full);
      return withFullMessages(item, full);
    },
    [params, applyFullMessages],
  );
}

export function notifyFullBodyLoadFailed(e: unknown): void {
  useDashboardStore.getState().setNotification({
    message: `Could not load the full body, so nothing was done: ${humanizeError(e).message}`,
    severity: 'error',
  });
}

/** Run `task` for each item, at most `limit` at a time; resolves with how many failed. */
export async function forEachLimited<T>(items: T[], limit: number, task: (item: T) => Promise<unknown>): Promise<number> {
  let next = 0;
  let failed = 0;
  const worker = async () => {
    while (next < items.length) {
      const item = items[next++]!;
      try {
        await task(item);
      } catch {
        failed++;
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return failed;
}

// Shared by every mounted LLM view, so each shortened message is fetched at most once per page.
const autoLoadAttempted = new Set<string>();
let autoLoadEpoch = 0;
const AUTO_LOAD_CONCURRENCY = 2;
const AUTO_LOAD_MEMORY = 1000;
/** A message longer than this (in characters) is left for the user to load with Load Full Body. */
export const AUTO_LOAD_MAX_CHARACTERS = 4 * 1024 * 1024;

function tooLargeToAutoLoad(item: JsonListItem): boolean {
  const markers = item.truncatedBodies ?? {};
  return [markers.httpRequest, markers.httpResponse].some((m) => !!m && m.originalLength > AUTO_LOAD_MAX_CHARACTERS);
}

function loadIdOf(item: JsonListItem): string {
  const markers = item.truncatedBodies ?? {};
  return [markers.httpRequest, markers.httpResponse].map((m) => (m ? `${m.logEntryId}:${m.part}` : '')).join('|');
}

/**
 * LLM views (conversations, sessions, token counts, MCP health) parse whole request and response
 * bodies, so a shortened one is misread. This loads, once, every shortened row that is recognisably
 * LLM or MCP traffic from what is shown (host, path, headers or the start of the body), unless a
 * message is over AUTO_LOAD_MAX_CHARACTERS.
 */
export function useAutoLoadLlmRows(): void {
  const proxiedRequests = useDashboardStore((s) => s.proxiedRequests);
  const recordedRequests = useDashboardStore((s) => s.recordedRequests);
  const loadFullRow = useLoadFullRow();
  useEffect(() => {
    const rows = [...proxiedRequests, ...recordedRequests];
    const epoch = useDashboardStore.getState().connectionEpoch;
    if (epoch !== autoLoadEpoch) {
      autoLoadAttempted.clear();
      autoLoadEpoch = epoch;
    }
    if (autoLoadAttempted.size > AUTO_LOAD_MEMORY) {
      const present = new Set(rows.map(loadIdOf));
      for (const id of autoLoadAttempted) if (!present.has(id)) autoLoadAttempted.delete(id);
    }
    const pending: JsonListItem[] = [];
    for (const item of rows) {
      if (!hasTruncatedBodies(item)) continue;
      const id = loadIdOf(item);
      if (autoLoadAttempted.has(id)) continue;
      if (tooLargeToAutoLoad(item)) continue;
      if (cachedParseTraffic(item.value).kind === 'generic') continue;
      autoLoadAttempted.add(id);
      pending.push(item);
    }
    // A failed load leaves the row shortened, with its Load Full Body button; it is not retried.
    if (pending.length > 0) void forEachLimited(pending, AUTO_LOAD_CONCURRENCY, loadFullRow);
  }, [proxiedRequests, recordedRequests, loadFullRow]);
}
