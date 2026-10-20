import { useCallback, useEffect, useRef } from 'react';
import type { ConnectionParams } from './useConnectionParams';
import type { ClearType, RequestFilter, WebSocketMessage } from '../types';
import { useDashboardStore } from '../store';
import { buildBaseUrl } from '../lib/mcpClient';
import { CLEAR_LOGS_DONE_MESSAGE } from '../lib/clearServerText';
import { isCrossOrigin, isMixedContent } from '../lib/connectionTarget';

const currentEpoch = (): number => useDashboardStore.getState().connectionEpoch;

/** What to check when the live connection to `params` keeps failing. */
export function connectionLostMessage(params: ConnectionParams, workspaceTarget: boolean): string {
  const where = `${params.host}:${params.port}`;
  const check = workspaceTarget
    ? "Check the server is running and this workspace's target is correct."
    : 'Check the server is running and the host/port (?host=&port= in the URL) are correct.';
  let message = `Connection lost to ${where} — retrying automatically. ${check}`;
  if (isMixedContent(params)) {
    message += ' This dashboard is served over https, so the browser blocks calls to an http target: use https for it.';
  } else if (isCrossOrigin(params)) {
    message += ` ${where} is a different origin from this dashboard, so its control-plane calls are cross-origin:` +
      ' it must answer CORS for this origin (by default MockServer does for a dashboard on localhost; otherwise' +
      ' list this origin in its controlPlaneCorsAllowOrigin, and check no proxy in front of it strips the CORS headers).';
  }
  return message;
}

const RECONNECT_DELAY_MS = 3000;

/**
 * Close a WebSocket we're discarding (reconnect or unmount) without side effects. Calling
 * `close()` on a socket that is still CONNECTING makes the browser log a noisy
 * "WebSocket is closed before the connection is established" warning — which happens routinely
 * under React StrictMode's dev double-mount (connect, then immediate cleanup). We detach the
 * handlers first (so this intentional close neither updates state nor schedules a reconnect),
 * then close immediately if already open/closing, or defer the close until it finishes opening.
 */
function closeWebSocket(ws: WebSocket): void {
  ws.onmessage = null;
  ws.onerror = null;
  ws.onclose = null;
  if (ws.readyState === WebSocket.CONNECTING) {
    ws.onopen = () => ws.close();
  } else {
    ws.onopen = null;
    ws.close();
  }
}

export function useWebSocket(params: ConnectionParams) {
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectCountRef = useRef(0);
  const lastFilterRef = useRef<RequestFilter>({});
  const connectRef = useRef<(filter: RequestFilter) => void>(() => {});
  // While the tab is hidden we don't parse/reconcile/re-render on every ~1/sec
  // push — we stash only the NEWEST raw message here (each push is full state)
  // and replay it when the tab becomes visible again. The socket stays open so
  // the server keeps us current without reconnect churn. `null` = nothing buffered.
  const hiddenMessageRef = useRef<string | null>(null);
  // Set when a dashboard HTTP probe returns 401/403 (control-plane auth is enabled but this
  // client is unauthenticated). Suppresses the generic "server down" banner so the actionable
  // auth-required message is shown instead. Cleared on a successful open.
  const authRequiredRef = useRef(false);

  const applyMessage = useDashboardStore((s) => s.applyMessage);
  const setConnectionStatus = useDashboardStore((s) => s.setConnectionStatus);
  const setError = useDashboardStore((s) => s.setError);

  // Parse + apply a raw WebSocket payload. Shared by the live onmessage path and
  // the deferred visibility-resume path so error/parse-failure and the store's
  // errorSource semantics behave identically whether a message is applied live
  // or replayed late.
  const applyRawMessage = useCallback(
    (raw: string) => {
      try {
        const data = JSON.parse(raw) as WebSocketMessage;
        applyMessage(data);
      } catch {
        setError('Failed to parse WebSocket message');
      }
    },
    [applyMessage, setError],
  );

  const probeAuthRequired = useCallback(async () => {
    // The browser WebSocket API does not expose the handshake HTTP status: a rejected upgrade
    // (401/403 when control-plane auth is enabled) and an unreachable server both surface as an
    // abnormal close / onerror with no readable status. Probe the dashboard HTTP endpoint — which
    // is gated by the SAME control-plane auth as the WebSocket upgrade — to tell the two apart and
    // show an actionable auth-required message instead of the misleading "server down" banner.
    try {
      const epoch = currentEpoch();
      const base = buildBaseUrl(params);
      const response = await fetch(`${base}/mockserver/dashboard`, { method: 'GET' });
      if (currentEpoch() !== epoch) return;
      if (response.status === 401 || response.status === 403) {
        authRequiredRef.current = true;
        setError(
          `Dashboard requires authentication (HTTP ${response.status}) on ${params.host}:${params.port}. ` +
            `Control-plane authentication is enabled. Browsers cannot attach a bearer token to a WebSocket, so serve ` +
            `the dashboard through an authenticating proxy that adds the Authorization header (or use mutual TLS).`,
        );
        return;
      }
      authRequiredRef.current = false;
    } catch {
      // Ignore probe failures — fall back to the generic connection-lost handling below.
    }
  }, [params, setError]);

  const scheduleReconnect = useCallback(
    () => {
      // Keep retrying with a capped backoff rather than permanently giving up — a server that
      // is down longer than the first few attempts (a deploy/restart) should still reconnect
      // automatically once it comes back. onopen resets the counter and clears the error.
      reconnectCountRef.current += 1;
      // Surface the banner early (after the 2nd failed attempt, ~a few seconds) so a user pointed
      // at the wrong server isn't left guessing. Include the host/port so the cause is actionable —
      // they come from the ?host=/?port= URL params. onopen resets the counter and clears the error.
      // Suppressed when a probe has determined the failure is an auth rejection, so the auth-required
      // message (set by probeAuthRequired) is not clobbered by the generic banner.
      if (reconnectCountRef.current === 2 && !authRequiredRef.current) {
        setError(connectionLostMessage(params, useDashboardStore.getState().connection !== null));
      }
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      const delay = RECONNECT_DELAY_MS * Math.min(reconnectCountRef.current, 5); // capped at 15s
      reconnectTimerRef.current = setTimeout(() => {
        // Reconnect with the LAST filter actually requested — read live from the ref, never a
        // stale closure value. A filter set via sendFilter() while the socket was OPEN only
        // updates lastFilterRef (no reconnect), so a closure-captured filter would resurrect the
        // pre-filter state on reconnect and silently stream unfiltered data to the panels.
        connectRef.current(lastFilterRef.current);
      }, delay);
    },
    [setError, params],
  );

  const connect = useCallback(
    (filter: RequestFilter) => {
      lastFilterRef.current = filter;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      if (socketRef.current) {
        closeWebSocket(socketRef.current);
        socketRef.current = null;
      }

      hiddenMessageRef.current = null;
      setConnectionStatus('connecting');
      const protocol = params.secure ? 'wss' : 'ws';
      const url = `${protocol}://${params.host}:${params.port}${params.basePath ?? ''}/_mockserver_ui_websocket`;

      const ws = new WebSocket(url);
      socketRef.current = ws;
      const epoch = currentEpoch();

      ws.onopen = () => {
        reconnectCountRef.current = 0;
        authRequiredRef.current = false;
        setConnectionStatus('connected');
        setError(null);
        ws.send(JSON.stringify(filter));
      };

      ws.onmessage = (event: MessageEvent) => {
        // The store drops the old server's data before React re-renders with the new target,
        // so a push from the old socket in between must not repopulate it.
        if (currentEpoch() !== epoch) return;
        const raw = event.data as string;
        // Hidden tab: buffer only the newest payload and skip all work until the
        // tab is shown again (a background dashboard otherwise parses/reconciles/
        // re-renders 1/sec forever). onopen/onclose/onerror are NOT gated, so the
        // connection banner still updates correctly while hidden.
        if (typeof document !== 'undefined' && document.hidden) {
          hiddenMessageRef.current = raw;
          return;
        }
        applyRawMessage(raw);
      };

      ws.onclose = () => {
        setConnectionStatus('disconnected');
        socketRef.current = null;
        // Fire-and-forget: distinguish an auth rejection from a down server so the right message
        // is shown. Never blocks or defers the reconnect (probe failures are swallowed internally).
        void probeAuthRequired();
        scheduleReconnect();
      };

      ws.onerror = () => {
        setConnectionStatus('error');
      };
    },
    [params, applyRawMessage, setConnectionStatus, setError, scheduleReconnect, probeAuthRequired],
  );

  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

  /**
   * Open the socket to the current target with the last filter requested. Re-created when the
   * target changes, so calling it from an effect reconnects on a workspace target change;
   * `connect` closes the previous socket first, which keeps a single live socket.
   */
  const start = useCallback(() => {
    reconnectCountRef.current = 0;
    authRequiredRef.current = false;
    connect(lastFilterRef.current);
  }, [connect]);

  // When the tab becomes visible again, replay the newest message buffered while
  // it was hidden so the panels are current immediately (rather than waiting for
  // the next ~1/sec push). Applying it goes through the same path as a live
  // message, so a buffered error-only payload and errorSource semantics behave
  // identically to a live one.
  useEffect(() => {
    if (typeof document === 'undefined') return;
    const onVisibilityChange = (): void => {
      if (document.hidden) return;
      const buffered = hiddenMessageRef.current;
      if (buffered !== null) {
        hiddenMessageRef.current = null;
        applyRawMessage(buffered);
      }
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => document.removeEventListener('visibilitychange', onVisibilityChange);
  }, [applyRawMessage]);

  const sendFilter = useCallback(
    (filter: RequestFilter) => {
      lastFilterRef.current = filter;
      const ws = socketRef.current;
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(filter));
      } else {
        connect(filter);
      }
    },
    [connect],
  );

  const disconnect = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    if (socketRef.current) {
      closeWebSocket(socketRef.current);
      socketRef.current = null;
    }
    // Drop any message buffered while hidden so it can't be replayed after an
    // intentional disconnect.
    hiddenMessageRef.current = null;
    setConnectionStatus('disconnected');
  }, [setConnectionStatus]);

  const clearServer = useCallback(
    async (type: ClearType = 'all') => {
      const base = buildBaseUrl(params);
      const epoch = currentEpoch();
      try {
        const url =
          type === 'all'
            ? `${base}/mockserver/reset`
            : `${base}/mockserver/clear?type=${encodeURIComponent(type)}`;
        const response = await fetch(url, { method: 'PUT' });
        if (currentEpoch() !== epoch) return;
        if (!response.ok) {
          setError(`Clear failed: ${response.status} ${response.statusText}`);
          return;
        }
        if (type === 'all') {
          useDashboardStore.getState().clearUI();
          connect(lastFilterRef.current);
        } else if (type === 'log') {
          // Recorded and proxied requests live in the same server event log, so
          // the log clear removed them too; only expectations remain.
          useDashboardStore.setState({ logMessages: [], recordedRequests: [], proxiedRequests: [] });
        } else if (type === 'expectations') {
          useDashboardStore.setState({ activeExpectations: [] });
        }
        const what = type === 'all' ? 'Server reset — all expectations, logs and recorded traffic cleared' : type === 'log' ? CLEAR_LOGS_DONE_MESSAGE : 'Expectations cleared';
        useDashboardStore.getState().setNotification({ message: what, severity: 'success' });
      } catch {
        setError('Failed to clear server');
      }
    },
    [params, setError, connect],
  );

  useEffect(() => {
    return () => {
      disconnect();
    };
  }, [disconnect]);

  return { connect, start, disconnect, sendFilter, clearServer };
}
