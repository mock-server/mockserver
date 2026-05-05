import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useWebSocket } from '../hooks/useWebSocket';
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

describe('useWebSocket', () => {
  const defaultParams = { host: 'localhost', port: '1080', secure: false };

  beforeEach(() => {
    MockWebSocket.instances = [];
    vi.stubGlobal('WebSocket', MockWebSocket);
    useDashboardStore.setState({
      connectionStatus: 'disconnected',
      error: null,
      logMessages: [],
      activeExpectations: [],
      recordedRequests: [],
      proxiedRequests: [],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
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
});
