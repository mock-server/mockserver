import { useCallback, useEffect, useRef } from 'react';
import type { ConnectionParams } from './useConnectionParams';
import type { ClearType, RequestFilter, WebSocketMessage } from '../types';
import { useDashboardStore } from '../store';

const RECONNECT_DELAY_MS = 3000;
const MAX_RECONNECT_ATTEMPTS = 10;

export function useWebSocket(params: ConnectionParams) {
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectCountRef = useRef(0);
  const lastFilterRef = useRef<RequestFilter>({});
  const connectRef = useRef<(filter: RequestFilter) => void>(() => {});

  const applyMessage = useDashboardStore((s) => s.applyMessage);
  const setConnectionStatus = useDashboardStore((s) => s.setConnectionStatus);
  const setError = useDashboardStore((s) => s.setError);

  const scheduleReconnect = useCallback(
    (filter: RequestFilter) => {
      if (reconnectCountRef.current >= MAX_RECONNECT_ATTEMPTS) {
        setError('Max reconnection attempts reached. Refresh the page to retry.');
        return;
      }
      reconnectCountRef.current += 1;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      const delay = RECONNECT_DELAY_MS * Math.min(reconnectCountRef.current, 5);
      reconnectTimerRef.current = setTimeout(() => {
        connectRef.current(filter);
      }, delay);
    },
    [setError],
  );

  const connect = useCallback(
    (filter: RequestFilter) => {
      lastFilterRef.current = filter;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      if (socketRef.current) {
        socketRef.current.onclose = null;
        socketRef.current.onerror = null;
        socketRef.current.close();
        socketRef.current = null;
      }

      setConnectionStatus('connecting');
      const protocol = params.secure ? 'wss' : 'ws';
      const url = `${protocol}://${params.host}:${params.port}/_mockserver_ui_websocket`;

      const ws = new WebSocket(url);
      socketRef.current = ws;

      ws.onopen = () => {
        reconnectCountRef.current = 0;
        setConnectionStatus('connected');
        setError(null);
        ws.send(JSON.stringify(filter));
      };

      ws.onmessage = (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data as string) as WebSocketMessage;
          applyMessage(data);
        } catch {
          setError('Failed to parse WebSocket message');
        }
      };

      ws.onclose = () => {
        setConnectionStatus('disconnected');
        socketRef.current = null;
        scheduleReconnect(filter);
      };

      ws.onerror = () => {
        setConnectionStatus('error');
      };
    },
    [params, applyMessage, setConnectionStatus, setError, scheduleReconnect],
  );

  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

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
      socketRef.current.onclose = null;
      socketRef.current.close();
      socketRef.current = null;
    }
    setConnectionStatus('disconnected');
  }, [setConnectionStatus]);

  const clearServer = useCallback(
    async (type: ClearType = 'all') => {
      const protocol = params.secure ? 'https' : 'http';
      const base = `${protocol}://${params.host}:${params.port}`;
      try {
        const url =
          type === 'all'
            ? `${base}/mockserver/reset`
            : `${base}/mockserver/clear?type=${encodeURIComponent(type)}`;
        const response = await fetch(url, { method: 'PUT' });
        if (!response.ok) {
          setError(`Clear failed: ${response.status} ${response.statusText}`);
          return;
        }
        useDashboardStore.getState().clearUI();
        if (type === 'all') {
          connect(lastFilterRef.current);
        }
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

  return { connect, disconnect, sendFilter, clearServer };
}
