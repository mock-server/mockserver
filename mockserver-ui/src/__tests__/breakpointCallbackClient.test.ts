import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  BreakpointCallbackClient,
  _resetBreakpointCallbackClient,
  getBreakpointCallbackClient,
  releaseBreakpointCallbackClientsExcept,
  type PausedItem,
  type CallbackClientState,
  type WsEnvelope,
  type BreakpointReleaseNotice,
  type StoredPausedItem,
} from '../lib/breakpointCallbackClient';

// ---------------------------------------------------------------------------
// MockWebSocket
// ---------------------------------------------------------------------------

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
    this.readyState = 3;
    this.onclose?.();
  }

  simulateOpen() {
    this.readyState = 1;
    this.onopen?.();
  }

  simulateMessage(data: unknown) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }

  simulateClose() {
    this.readyState = 3;
    this.onclose?.();
  }
}

const params = { host: '127.0.0.1', port: '1080', secure: false };

beforeEach(() => {
  MockWebSocket.instances = [];
  vi.stubGlobal('WebSocket', MockWebSocket as unknown as typeof WebSocket);
  _resetBreakpointCallbackClient();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('BreakpointCallbackClient', () => {
  it('connects to the callback websocket endpoint', () => {
    const client = new BreakpointCallbackClient();
    client.connect(params);

    expect(MockWebSocket.instances).toHaveLength(1);
    expect(MockWebSocket.instances[0]!.url).toBe('ws://127.0.0.1:1080/_mockserver_callback_websocket?capabilities=breakpointReleased');
    client.disconnect();
  });

  it('uses wss for secure connections', () => {
    const client = new BreakpointCallbackClient();
    client.connect({ ...params, secure: true });

    expect(MockWebSocket.instances[0]!.url).toBe('wss://127.0.0.1:1080/_mockserver_callback_websocket?capabilities=breakpointReleased');
    client.disconnect();
  });

  it('includes basePath in the URL', () => {
    const client = new BreakpointCallbackClient();
    client.connect({ ...params, basePath: '/prefix' });

    expect(MockWebSocket.instances[0]!.url).toBe('ws://127.0.0.1:1080/prefix/_mockserver_callback_websocket?capabilities=breakpointReleased');
    client.disconnect();
  });

  it('reads clientId from WebSocketClientIdDTO and transitions to connected', () => {
    const client = new BreakpointCallbackClient();
    const states: CallbackClientState[] = [];
    client.onStateChange((s) => states.push(s));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();

    // Send clientId DTO
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'test-client-123' }),
    });

    expect(client.clientId).toBe('test-client-123');
    expect(client.state).toBe('connected');
    expect(states).toContain('connecting');
    expect(states).toContain('connected');
    client.disconnect();
  });

  it('dispatches REQUEST phase paused items', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    // Simulate a REQUEST-phase push
    ws.simulateMessage({
      type: 'org.mockserver.model.HttpRequest',
      value: JSON.stringify({
        method: 'GET',
        path: '/api/test',
        headers: {
          'WebSocketCorrelationId': ['corr-123'],
          'X-MockServer-BreakpointId': ['bp-456'],
        },
      }),
    });

    expect(items).toHaveLength(1);
    expect(items[0]!.phase).toBe('REQUEST');
    if (items[0]!.phase === 'REQUEST') {
      expect(items[0]!.correlationId).toBe('corr-123');
      expect(items[0]!.breakpointId).toBe('bp-456');
      expect(items[0]!.request.method).toBe('GET');
      expect(items[0]!.request.path).toBe('/api/test');
    }
    client.disconnect();
  });

  it('dispatches RESPONSE phase paused items', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    ws.simulateMessage({
      type: 'org.mockserver.model.HttpRequestAndHttpResponse',
      value: JSON.stringify({
        httpRequest: {
          method: 'POST',
          path: '/api/data',
          headers: {
            'WebSocketCorrelationId': ['corr-789'],
            'X-MockServer-BreakpointId': ['bp-abc'],
          },
        },
        httpResponse: {
          statusCode: 200,
          reasonPhrase: 'OK',
          body: '{"data":"test"}',
        },
      }),
    });

    expect(items).toHaveLength(1);
    expect(items[0]!.phase).toBe('RESPONSE');
    if (items[0]!.phase === 'RESPONSE') {
      expect(items[0]!.correlationId).toBe('corr-789');
      expect(items[0]!.breakpointId).toBe('bp-abc');
      expect(items[0]!.response?.statusCode).toBe(200);
    }
    client.disconnect();
  });

  it('dispatches FRAME phase paused items', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.PausedStreamFrameDTO',
      value: JSON.stringify({
        correlationId: 'frame-corr-1',
        streamId: 'stream-1',
        sequenceNumber: 0,
        direction: 'OUTBOUND',
        phase: 'RESPONSE_STREAM',
        body: btoa('data: hello'),
        requestMethod: 'GET',
        requestPath: '/events',
        breakpointId: 'bp-frame',
      }),
    });

    expect(items).toHaveLength(1);
    expect(items[0]!.phase).toBe('RESPONSE_STREAM');
    if (items[0]!.phase === 'RESPONSE_STREAM') {
      expect(items[0]!.frame.correlationId).toBe('frame-corr-1');
      expect(items[0]!.frame.streamId).toBe('stream-1');
      expect(items[0]!.breakpointId).toBe('bp-frame');
    }
    client.disconnect();
  });

  it('resolves a REQUEST phase by sending HttpRequest envelope', () => {
    const client = new BreakpointCallbackClient();
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    client.resolveRequest('corr-123', { method: 'POST', path: '/modified' });

    expect(ws.sentMessages).toHaveLength(1);
    const envelope = JSON.parse(ws.sentMessages[0]!) as WsEnvelope;
    expect(envelope.type).toBe('org.mockserver.model.HttpRequest');

    const inner = JSON.parse(envelope.value) as Record<string, unknown>;
    expect(inner.method).toBe('POST');
    expect(inner.path).toBe('/modified');
    // Correlation id should be echoed in headers
    const headers = inner.headers as Record<string, string[]>;
    expect(headers['WebSocketCorrelationId']).toEqual(['corr-123']);

    client.disconnect();
  });

  it('resolves a REQUEST phase abort by sending HttpResponse envelope', () => {
    const client = new BreakpointCallbackClient();
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    client.resolveRequest('corr-456', { statusCode: 503, body: 'Aborted' });

    const envelope = JSON.parse(ws.sentMessages[0]!) as WsEnvelope;
    expect(envelope.type).toBe('org.mockserver.model.HttpResponse');

    const inner = JSON.parse(envelope.value) as Record<string, unknown>;
    expect(inner.statusCode).toBe(503);

    client.disconnect();
  });

  it('resolves a RESPONSE phase by sending HttpResponse envelope', () => {
    const client = new BreakpointCallbackClient();
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    client.resolveResponse('corr-789', { statusCode: 200, body: '{"modified":true}' });

    const envelope = JSON.parse(ws.sentMessages[0]!) as WsEnvelope;
    expect(envelope.type).toBe('org.mockserver.model.HttpResponse');

    const inner = JSON.parse(envelope.value) as Record<string, unknown>;
    expect(inner.statusCode).toBe(200);
    const headers = inner.headers as Record<string, string[]>;
    expect(headers['WebSocketCorrelationId']).toEqual(['corr-789']);

    client.disconnect();
  });

  it('resolves a stream frame with a decision', () => {
    const client = new BreakpointCallbackClient();
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    client.resolveFrame({ correlationId: 'frame-1', action: 'MODIFY', body: btoa('modified') });

    const envelope = JSON.parse(ws.sentMessages[0]!) as WsEnvelope;
    expect(envelope.type).toBe('org.mockserver.serialization.model.StreamFrameDecisionDTO');

    const inner = JSON.parse(envelope.value) as Record<string, unknown>;
    expect(inner.correlationId).toBe('frame-1');
    expect(inner.action).toBe('MODIFY');
    expect(inner.body).toBe(btoa('modified'));

    client.disconnect();
  });

  it('transitions to disconnected on WS close and clears clientId', () => {
    const client = new BreakpointCallbackClient();
    const states: CallbackClientState[] = [];
    client.onStateChange((s) => states.push(s));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    expect(client.state).toBe('connected');
    expect(client.clientId).toBe('c1');

    ws.simulateClose();

    expect(client.state).toBe('disconnected');
    expect(client.clientId).toBeNull();
    client.disconnect();
  });

  it('does not send when WS is not open', () => {
    const client = new BreakpointCallbackClient();
    client.connect(params);

    // WS is still in CONNECTING state (readyState=0)
    client.resolveRequest('corr-1', { method: 'GET' });

    const ws = MockWebSocket.instances[0]!;
    expect(ws.sentMessages).toHaveLength(0);
    client.disconnect();
  });

  it('ignores messages with unknown type', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    ws.simulateMessage({
      type: 'org.mockserver.model.UnknownType',
      value: '{}',
    });

    expect(items).toHaveLength(0);
    client.disconnect();
  });

  it('ignores malformed messages', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();

    // Simulate raw non-JSON message
    ws.onmessage?.({ data: 'not json' });

    expect(items).toHaveLength(0);
    client.disconnect();
  });

  it('dispatches INBOUND_STREAM frame items', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.PausedStreamFrameDTO',
      value: JSON.stringify({
        correlationId: 'frame-inbound-1',
        streamId: 'ws-inbound-1',
        sequenceNumber: 0,
        direction: 'INBOUND',
        phase: 'INBOUND_STREAM',
        body: btoa('client message'),
        requestMethod: 'GET',
        requestPath: '/ws',
        breakpointId: 'bp-inbound',
      }),
    });

    expect(items).toHaveLength(1);
    expect(items[0]!.phase).toBe('INBOUND_STREAM');
    if (items[0]!.phase === 'INBOUND_STREAM') {
      expect(items[0]!.frame.direction).toBe('INBOUND');
    }
    client.disconnect();
  });

  // ---- requestTimestamp extraction ----

  it('extracts requestTimestamp from REQUEST phase header', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    ws.simulateMessage({
      type: 'org.mockserver.model.HttpRequest',
      value: JSON.stringify({
        method: 'GET',
        path: '/api/ts-test',
        headers: {
          'WebSocketCorrelationId': ['corr-ts-1'],
          'X-MockServer-BreakpointId': ['bp-1'],
          'X-MockServer-RequestTimestamp': ['1717000000000'],
        },
      }),
    });

    expect(items).toHaveLength(1);
    expect(items[0]!.phase).toBe('REQUEST');
    if (items[0]!.phase === 'REQUEST') {
      expect(items[0]!.requestTimestamp).toBe(1717000000000);
    }
    client.disconnect();
  });

  it('extracts requestTimestamp from RESPONSE phase header', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    ws.simulateMessage({
      type: 'org.mockserver.model.HttpRequestAndHttpResponse',
      value: JSON.stringify({
        httpRequest: {
          method: 'POST',
          path: '/api/resp-ts',
          headers: {
            'WebSocketCorrelationId': ['corr-ts-2'],
            'X-MockServer-BreakpointId': ['bp-2'],
            'X-MockServer-RequestTimestamp': ['1717000000123'],
          },
        },
        httpResponse: { statusCode: 200 },
      }),
    });

    expect(items).toHaveLength(1);
    expect(items[0]!.phase).toBe('RESPONSE');
    if (items[0]!.phase === 'RESPONSE') {
      expect(items[0]!.requestTimestamp).toBe(1717000000123);
    }
    client.disconnect();
  });

  it('sets requestTimestamp to null when header is missing', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    ws.simulateMessage({
      type: 'org.mockserver.model.HttpRequest',
      value: JSON.stringify({
        method: 'GET',
        path: '/api/no-ts',
        headers: {
          'WebSocketCorrelationId': ['corr-no-ts'],
        },
      }),
    });

    expect(items).toHaveLength(1);
    if (items[0]!.phase === 'REQUEST') {
      expect(items[0]!.requestTimestamp).toBeNull();
    }
    client.disconnect();
  });

  it('parses requestTimestamp on PausedStreamFrame from server', () => {
    const client = new BreakpointCallbackClient();
    const items: PausedItem[] = [];
    client.onPausedItem((item) => items.push(item));
    client.connect(params);

    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.PausedStreamFrameDTO',
      value: JSON.stringify({
        correlationId: 'frame-ts-1',
        streamId: 'stream-ts-1',
        sequenceNumber: 0,
        direction: 'OUTBOUND',
        phase: 'RESPONSE_STREAM',
        body: btoa('hello'),
        requestMethod: 'GET',
        requestPath: '/events',
        breakpointId: 'bp-frame-ts',
        requestTimestamp: 1717000000789,
      }),
    });

    expect(items).toHaveLength(1);
    if (items[0]!.phase === 'RESPONSE_STREAM') {
      expect(items[0]!.frame.requestTimestamp).toBe(1717000000789);
    }
    client.disconnect();
  });

  // ---- L3: resolution must not mutate the held item's headers ----

  it('does not mutate caller-supplied object-form headers when echoing the correlation id', () => {
    const client = new BreakpointCallbackClient();
    client.connect(params);
    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    // This object is still referenced by React state in the real app; it must
    // not be mutated in place.
    const heldHeaders: Record<string, string[]> = { Existing: ['v'] };
    client.resolveRequest('corr-x', { method: 'GET', headers: heldHeaders });

    expect(heldHeaders).toEqual({ Existing: ['v'] });
    expect(heldHeaders).not.toHaveProperty('WebSocketCorrelationId');

    const inner = JSON.parse(JSON.parse(ws.sentMessages[0]!).value) as { headers: Record<string, string[]> };
    expect(inner.headers.WebSocketCorrelationId).toEqual(['corr-x']);
    expect(inner.headers.Existing).toEqual(['v']);
    client.disconnect();
  });

  it('handles list-form headers without mutating them and echoes the correlation id', () => {
    const client = new BreakpointCallbackClient();
    client.connect(params);
    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });

    const heldHeaders = [{ name: 'Existing', values: ['v'] }];
    client.resolveResponse('corr-y', { statusCode: 200, headers: heldHeaders });

    // Original list is untouched (no correlation-id entry appended in place).
    expect(heldHeaders).toEqual([{ name: 'Existing', values: ['v'] }]);

    const inner = JSON.parse(JSON.parse(ws.sentMessages[0]!).value) as {
      headers: Array<{ name: string; values: string[] }>;
    };
    expect(Array.isArray(inner.headers)).toBe(true);
    const corr = inner.headers.find((h) => h.name === 'WebSocketCorrelationId');
    expect(corr?.values).toEqual(['corr-y']);
    expect(inner.headers.find((h) => h.name === 'Existing')?.values).toEqual(['v']);
    client.disconnect();
  });

  // ---- L2: reconnect indefinitely with capped backoff ----

  it('keeps reconnecting past the old fixed attempt cap', () => {
    vi.useFakeTimers();
    try {
      const client = new BreakpointCallbackClient();
      client.connect(params);

      // Drive many disconnect -> reconnect cycles. The pre-fix client gave up
      // after 20 attempts (no further sockets); the fix retries indefinitely.
      for (let i = 0; i < 25; i++) {
        const ws = MockWebSocket.instances[MockWebSocket.instances.length - 1]!;
        ws.simulateClose(); // onclose -> scheduleReconnect
        vi.advanceTimersByTime(15000); // capped backoff -> _connect fires
      }

      expect(MockWebSocket.instances.length).toBeGreaterThan(21);
      client.disconnect();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('BreakpointCallbackClient release notices', () => {
  const RELEASED = 'org.mockserver.serialization.model.BreakpointReleasedDTO';

  function connectedClient() {
    const client = new BreakpointCallbackClient();
    client.connect(params);
    const ws = MockWebSocket.instances[0]!;
    ws.simulateOpen();
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
      value: JSON.stringify({ clientId: 'c1' }),
    });
    return { client, ws };
  }

  function pauseRequest(ws: MockWebSocket, correlationId: string) {
    ws.simulateMessage({
      type: 'org.mockserver.model.HttpRequest',
      value: JSON.stringify({ method: 'GET', path: '/bp', headers: { WebSocketCorrelationId: [correlationId] } }),
    });
  }

  function release(ws: MockWebSocket, correlationId: string, reason = 'TIMEOUT') {
    ws.simulateMessage({
      type: RELEASED,
      value: JSON.stringify({ correlationId, reason, message: 'continued after the timeout' }),
    });
  }

  it('drops a paused request the server released and tells listeners', () => {
    const { client, ws } = connectedClient();
    const notices: BreakpointReleaseNotice[] = [];
    let items: StoredPausedItem[] = [];
    client.subscribeReleaseNotices((n) => notices.push(n));
    client.subscribePausedItems((i) => { items = i; });
    pauseRequest(ws, 'corr-1');
    pauseRequest(ws, 'corr-2');

    release(ws, 'corr-1');

    expect(items.map((i) => (i.phase === 'REQUEST' ? i.correlationId : ''))).toEqual(['corr-2']);
    expect(client.isPaused('corr-1')).toBe(false);
    expect(notices).toHaveLength(1);
    expect(notices[0]!.actionIgnored).toBe(false);
    expect(notices[0]!.reason).toBe('TIMEOUT');
    expect(notices[0]!.message).toBe('continued after the timeout');
    expect(notices[0]!.item?.phase).toBe('REQUEST');
    client.disconnect();
  });

  it('drops a paused stream frame the server released', () => {
    const { client, ws } = connectedClient();
    const notices: BreakpointReleaseNotice[] = [];
    client.subscribeReleaseNotices((n) => notices.push(n));
    ws.simulateMessage({
      type: 'org.mockserver.serialization.model.PausedStreamFrameDTO',
      value: JSON.stringify({ correlationId: 'frame-1', streamId: 's', sequenceNumber: 0, direction: 'OUTBOUND', phase: 'RESPONSE_STREAM', body: 'YQ==' }),
    });
    expect(client.isPaused('frame-1')).toBe(true);

    release(ws, 'frame-1', 'STREAM_ENDED');

    expect(client.getPausedItems()).toHaveLength(0);
    expect(notices[0]!.reason).toBe('STREAM_ENDED');
    client.disconnect();
  });

  it('reports a decision the user sent after the server had already released the item', () => {
    const { client, ws } = connectedClient();
    const notices: BreakpointReleaseNotice[] = [];
    client.subscribeReleaseNotices((n) => notices.push(n));
    pauseRequest(ws, 'corr-late');
    const [item] = client.getPausedItems();
    client.resolveRequest('corr-late', { method: 'GET', path: '/bp' });
    client.removePausedItem(item!.key);

    release(ws, 'corr-late');

    expect(notices).toHaveLength(1);
    expect(notices[0]!.actionIgnored).toBe(true);
    expect(notices[0]!.item).toBeNull();
    client.disconnect();
  });

  it('ignores a release notice for an item it never held or resolved', () => {
    const { client, ws } = connectedClient();
    const notices: BreakpointReleaseNotice[] = [];
    client.subscribeReleaseNotices((n) => notices.push(n));

    release(ws, 'unknown');

    expect(notices).toHaveLength(0);
    client.disconnect();
  });
});

describe('one callback client per MockServer target', () => {
  const other = { host: 'other-host', port: '1081', secure: false, basePath: '' };
  const clientIdMessage = (id: string) => ({
    type: 'org.mockserver.serialization.model.WebSocketClientIdDTO',
    value: JSON.stringify({ clientId: id }),
  });

  it('gives each target its own client and socket, so switching keeps the other server’s session', () => {
    const a = getBreakpointCallbackClient(params);
    const b = getBreakpointCallbackClient(other);
    expect(a).not.toBe(b);
    expect(getBreakpointCallbackClient({ ...params })).toBe(a);

    a.connect(params);
    b.connect(other);
    expect(MockWebSocket.instances.map((ws) => ws.url)).toEqual([
      'ws://127.0.0.1:1080/_mockserver_callback_websocket?capabilities=breakpointReleased',
      'ws://other-host:1081/_mockserver_callback_websocket?capabilities=breakpointReleased',
    ]);
    MockWebSocket.instances[0]!.simulateOpen();
    MockWebSocket.instances[0]!.simulateMessage(clientIdMessage('client-on-a'));
    MockWebSocket.instances[1]!.simulateOpen();
    MockWebSocket.instances[1]!.simulateMessage(clientIdMessage('client-on-b'));
    expect(a.clientId).toBe('client-on-a');
    expect(b.clientId).toBe('client-on-b');
  });

  it('releasing a target no workspace uses closes only that session', () => {
    const a = getBreakpointCallbackClient(params);
    const b = getBreakpointCallbackClient(other);
    a.connect(params);
    b.connect(other);
    MockWebSocket.instances.forEach((ws) => ws.simulateOpen());

    releaseBreakpointCallbackClientsExcept(new Set(['http://127.0.0.1:1080']));

    expect(MockWebSocket.instances[0]!.readyState).toBe(1);
    expect(MockWebSocket.instances[1]!.readyState).toBe(3);
    expect(b.state).toBe('disconnected');
    expect(getBreakpointCallbackClient(other)).not.toBe(b);
    expect(getBreakpointCallbackClient(params)).toBe(a);
  });

  it('keeps the session when the same target is given with another host spelling', () => {
    const client = getBreakpointCallbackClient(other);
    client.connect(other);
    MockWebSocket.instances[0]!.simulateOpen();
    MockWebSocket.instances[0]!.simulateMessage(clientIdMessage('kept'));

    client.connect({ ...other, host: 'OTHER-HOST' });

    expect(MockWebSocket.instances).toHaveLength(1);
    expect(client.clientId).toBe('kept');
    expect(getBreakpointCallbackClient({ ...other, host: 'Other-Host' })).toBe(client);
  });
});
