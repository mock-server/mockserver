import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { connectionLostMessage, useWebSocket } from '../hooks/useWebSocket';
import { useDashboardStore } from '../store';

class MockWebSocket {
  static instances: MockWebSocket[] = [];
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  url: string;
  readyState = 0;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  sentMessages: string[] = [];
  closed = false;

  CONNECTING = 0;
  OPEN = 1;
  CLOSING = 2;
  CLOSED = 3;

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
  }

  send(data: string) {
    this.sentMessages.push(data);
  }

  close() {
    this.closed = true;
    this.onclose?.();
  }

  simulateOpen() {
    this.readyState = 1;
    this.onopen?.();
  }

  simulateMessage(data: object) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }

  simulateError() {
    this.onerror?.();
  }
}

/** Force document.hidden for the visibility-gating tests (jsdom defaults to false). */
function setHidden(hidden: boolean): void {
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
}

describe('useWebSocket', () => {
  const defaultParams = { host: 'localhost', port: '1080', secure: false };

  beforeEach(() => {
    MockWebSocket.instances = [];
    vi.stubGlobal('WebSocket', MockWebSocket);
    setHidden(false);
    useDashboardStore.setState({
      connectionStatus: 'disconnected',
      error: null,
      errorSource: null,
      logMessages: [],
      activeExpectations: [],
      recordedRequests: [],
      proxiedRequests: [],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setHidden(false);
  });

  it('connects to the correct WebSocket URL', () => {
    const { result } = renderHook(() => useWebSocket(defaultParams));

    act(() => {
      result.current.connect({});
    });

    expect(MockWebSocket.instances).toHaveLength(1);
    expect(MockWebSocket.instances[0]!.url).toBe('ws://localhost:1080/_mockserver_ui_websocket');
  });

  it('uses wss for secure connections', () => {
    const { result } = renderHook(() =>
      useWebSocket({ host: 'secure.host', port: '443', secure: true }),
    );

    act(() => {
      result.current.connect({});
    });

    expect(MockWebSocket.instances[0]!.url).toBe('wss://secure.host:443/_mockserver_ui_websocket');
  });

  it('sets connection status to connecting then connected on open', () => {
    const { result } = renderHook(() => useWebSocket(defaultParams));

    act(() => {
      result.current.connect({});
    });
    expect(useDashboardStore.getState().connectionStatus).toBe('connecting');

    act(() => {
      MockWebSocket.instances[0]!.simulateOpen();
    });
    expect(useDashboardStore.getState().connectionStatus).toBe('connected');
  });

  it('sends the filter as JSON on open', () => {
    const { result } = renderHook(() => useWebSocket(defaultParams));
    const filter = { method: 'GET', path: '/api' };

    act(() => {
      result.current.connect(filter);
    });

    act(() => {
      MockWebSocket.instances[0]!.simulateOpen();
    });

    expect(MockWebSocket.instances[0]!.sentMessages).toHaveLength(1);
    expect(JSON.parse(MockWebSocket.instances[0]!.sentMessages[0]!)).toEqual(filter);
  });

  it('applies incoming WebSocket messages to the store', () => {
    const { result } = renderHook(() => useWebSocket(defaultParams));

    act(() => {
      result.current.connect({});
    });
    act(() => {
      MockWebSocket.instances[0]!.simulateOpen();
    });

    const message = {
      logMessages: [{ key: 'log1', value: {} }],
      activeExpectations: [{ key: 'exp1', value: {} }],
      recordedRequests: [],
      proxiedRequests: [],
    };

    act(() => {
      MockWebSocket.instances[0]!.simulateMessage(message);
    });

    expect(useDashboardStore.getState().logMessages).toHaveLength(1);
    expect(useDashboardStore.getState().activeExpectations).toHaveLength(1);
  });

  it('sets error status on WebSocket error', () => {
    const { result } = renderHook(() => useWebSocket(defaultParams));

    act(() => {
      result.current.connect({});
    });
    act(() => {
      MockWebSocket.instances[0]!.simulateError();
    });

    expect(useDashboardStore.getState().connectionStatus).toBe('error');
  });

  it('sendFilter sends message on open socket', () => {
    const { result } = renderHook(() => useWebSocket(defaultParams));

    act(() => {
      result.current.connect({});
    });
    act(() => {
      MockWebSocket.instances[0]!.simulateOpen();
    });
    act(() => {
      result.current.sendFilter({ method: 'POST' });
    });

    expect(MockWebSocket.instances[0]!.sentMessages).toHaveLength(2);
    expect(JSON.parse(MockWebSocket.instances[0]!.sentMessages[1]!)).toEqual({ method: 'POST' });
  });

  it('disconnect closes the socket', () => {
    const { result } = renderHook(() => useWebSocket(defaultParams));

    act(() => {
      result.current.connect({});
    });
    act(() => {
      MockWebSocket.instances[0]!.simulateOpen();
    });

    act(() => {
      result.current.disconnect();
    });

    expect(MockWebSocket.instances[0]!.closed).toBe(true);
    expect(useDashboardStore.getState().connectionStatus).toBe('disconnected');
  });

  it('shows an auth-required message when the dashboard probe returns 401 on close', async () => {
    // A rejected WebSocket upgrade surfaces only as an abnormal close (the browser hides the
    // handshake status), so the hook probes the dashboard HTTP endpoint to detect control-plane
    // auth and show an actionable message rather than the generic "server down" banner.
    const fetchMock = vi.fn().mockResolvedValue({ status: 401 } as Response);
    vi.stubGlobal('fetch', fetchMock);

    const { result } = renderHook(() => useWebSocket(defaultParams));
    act(() => {
      result.current.connect({});
    });

    await act(async () => {
      MockWebSocket.instances[0]!.close();
      // let the fire-and-forget probe promise resolve
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/mockserver/dashboard'),
      expect.objectContaining({ method: 'GET' }),
    );
    expect(useDashboardStore.getState().error).toContain('requires authentication');
    expect(useDashboardStore.getState().error).toContain('401');
  });

  it('treats a 403 dashboard probe as auth-required', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 403 } as Response);
    vi.stubGlobal('fetch', fetchMock);

    const { result } = renderHook(() => useWebSocket(defaultParams));
    act(() => {
      result.current.connect({});
    });

    await act(async () => {
      MockWebSocket.instances[0]!.close();
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(useDashboardStore.getState().error).toContain('requires authentication');
    expect(useDashboardStore.getState().error).toContain('403');
  });

  it('disconnect while still connecting defers close until the socket opens', () => {
    // Closing a CONNECTING socket triggers the browser's "WebSocket is closed before the
    // connection is established" warning (seen under React StrictMode's dev double-mount), so the
    // close is deferred until the socket finishes opening.
    const { result } = renderHook(() => useWebSocket(defaultParams));

    act(() => {
      result.current.connect({});
    });
    const ws = MockWebSocket.instances[0]!;
    // socket is still CONNECTING (readyState 0)
    act(() => {
      result.current.disconnect();
    });
    expect(ws.closed).toBe(false);

    // once it finishes connecting it is closed cleanly (no close-before-established)
    act(() => {
      ws.simulateOpen();
    });
    expect(ws.closed).toBe(true);
  });

  describe('hidden-tab message buffering', () => {
    it('buffers messages while hidden and applies only the newest on visible', () => {
      const { result } = renderHook(() => useWebSocket(defaultParams));
      act(() => {
        result.current.connect({});
      });
      act(() => {
        MockWebSocket.instances[0]!.simulateOpen();
      });

      setHidden(true);

      // Two full-state pushes arrive while hidden — neither touches the store.
      act(() => {
        MockWebSocket.instances[0]!.simulateMessage({
          logMessages: [{ key: 'l1', value: {} }],
          activeExpectations: [],
          recordedRequests: [],
          proxiedRequests: [],
        });
        MockWebSocket.instances[0]!.simulateMessage({
          logMessages: [{ key: 'l2', value: {} }, { key: 'l3', value: {} }],
          activeExpectations: [],
          recordedRequests: [],
          proxiedRequests: [],
        });
      });
      expect(useDashboardStore.getState().logMessages).toHaveLength(0);
      // The socket stays open while hidden — no reconnect churn.
      expect(MockWebSocket.instances).toHaveLength(1);
      expect(MockWebSocket.instances[0]!.closed).toBe(false);

      // Becoming visible replays only the NEWEST buffered message (full state).
      act(() => {
        setHidden(false);
        document.dispatchEvent(new Event('visibilitychange'));
      });
      expect(useDashboardStore.getState().logMessages).toHaveLength(2);
    });

    it('applies a buffered error-only message with push semantics on visible', () => {
      const { result } = renderHook(() => useWebSocket(defaultParams));
      act(() => {
        result.current.connect({});
      });
      act(() => {
        MockWebSocket.instances[0]!.simulateOpen();
      });

      setHidden(true);
      act(() => {
        MockWebSocket.instances[0]!.simulateMessage({ error: 'invalid filter' });
      });
      // Not applied while hidden.
      expect(useDashboardStore.getState().error).toBeNull();

      act(() => {
        setHidden(false);
        document.dispatchEvent(new Event('visibilitychange'));
      });
      expect(useDashboardStore.getState().error).toBe('invalid filter');
      // Applied late, it still carries push semantics so a later clean push clears it.
      expect(useDashboardStore.getState().errorSource).toBe('push');
    });

    it('does nothing on visible when no message was buffered', () => {
      const { result } = renderHook(() => useWebSocket(defaultParams));
      act(() => {
        result.current.connect({});
      });
      act(() => {
        MockWebSocket.instances[0]!.simulateOpen();
      });

      act(() => {
        setHidden(false);
        document.dispatchEvent(new Event('visibilitychange'));
      });
      expect(useDashboardStore.getState().logMessages).toHaveLength(0);
      expect(useDashboardStore.getState().error).toBeNull();
    });

    it('processes messages live once the tab is visible again', () => {
      const { result } = renderHook(() => useWebSocket(defaultParams));
      act(() => {
        result.current.connect({});
      });
      act(() => {
        MockWebSocket.instances[0]!.simulateOpen();
      });

      setHidden(true);
      act(() => {
        MockWebSocket.instances[0]!.simulateMessage({
          logMessages: [{ key: 'stale', value: {} }],
          activeExpectations: [],
          recordedRequests: [],
          proxiedRequests: [],
        });
      });
      act(() => {
        setHidden(false);
        document.dispatchEvent(new Event('visibilitychange'));
      });
      // A subsequent live message applies immediately (no buffering).
      act(() => {
        MockWebSocket.instances[0]!.simulateMessage({
          logMessages: [{ key: 'a', value: {} }, { key: 'b', value: {} }, { key: 'c', value: {} }],
          activeExpectations: [],
          recordedRequests: [],
          proxiedRequests: [],
        });
      });
      expect(useDashboardStore.getState().logMessages).toHaveLength(3);
    });
  });

  describe('workspace target change', () => {
    it('reconnects to the new target with the last filter, closing the old socket first', () => {
      const filter = { method: 'GET', path: '/api' };
      const { result, rerender } = renderHook(({ params }) => useWebSocket(params), {
        initialProps: { params: defaultParams },
      });
      act(() => result.current.start());
      act(() => result.current.sendFilter(filter));
      const first = MockWebSocket.instances[MockWebSocket.instances.length - 1]!;

      rerender({ params: { host: 'other', port: '1081', secure: false } });
      act(() => result.current.start());

      const second = MockWebSocket.instances[MockWebSocket.instances.length - 1]!;
      expect(first.onmessage).toBeNull();
      act(() => first.simulateOpen());
      expect(first.closed).toBe(true);
      expect(second.url).toBe('ws://other:1081/_mockserver_ui_websocket');
      act(() => second.simulateOpen());
      expect(JSON.parse(second.sentMessages[0]!)).toEqual(filter);
    });

    it('ignores a push the old socket delivers after the store has switched target', () => {
      const { result } = renderHook(() => useWebSocket(defaultParams));
      act(() => result.current.connect({}));
      const ws = MockWebSocket.instances[0]!;
      act(() => ws.simulateOpen());

      act(() => {
        useDashboardStore.setState((s) => ({ connectionEpoch: s.connectionEpoch + 1 }));
        ws.simulateMessage({ logMessages: [{ key: 'from-old-server', value: {} }] });
      });
      expect(useDashboardStore.getState().logMessages).toEqual([]);
    });

    it('names the workspace target and CORS in the connection-lost message for a cross-origin target', () => {
      expect(connectionLostMessage({ host: 'far-away', port: '1081', secure: false }, true))
        .toMatch(/this workspace's target is correct.*different origin.*CORS/);
      expect(connectionLostMessage({ host: globalThis.location.hostname, port: globalThis.location.port, secure: false }, false))
        .toMatch(/\?host=&port= in the URL\) are correct\.$/);
    });
  });
});
