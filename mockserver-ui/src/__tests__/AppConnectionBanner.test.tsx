/**
 * UIA1 — the sustained connection-loss banner.
 *
 * The banner appears only after the WebSocket has been down (disconnected /
 * error) continuously past CONNECTION_LOSS_BANNER_DELAY_MS, is dismissable, and
 * clears automatically once the connection is restored. The heavy App hooks
 * (WebSocket, debug-mismatch, generate-stub) and lazy views are mocked so the
 * test exercises only the banner logic.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { useDashboardStore } from '../store';

vi.mock('../hooks/useWebSocket', () => ({
  useWebSocket: () => ({
    connect: vi.fn(),
    start: vi.fn(),
    sendFilter: vi.fn(),
    clearServer: vi.fn().mockResolvedValue(undefined),
  }),
}));
vi.mock('../hooks/useDebugMismatch', () => ({ useDebugMismatch: () => ({ debugMismatch: null }) }));
vi.mock('../hooks/useGenerateStub', () => ({ useGenerateStub: () => ({ generateStub: null }) }));
// The AppBar measures real DOM widths and pulls in many dialogs; stub it.
// NAV_TAB_DESCRIPTIONS is re-exported from AppBar and read by App for the
// per-view description bar; provide an empty map so the import resolves.
vi.mock('../components/AppBar', () => ({ default: () => null, NAV_TAB_DESCRIPTIONS: {} }));
vi.mock('../components/OnboardingPanel', () => ({ default: () => null }));

import App from '../App';

function setStatus(status: 'connected' | 'disconnected' | 'error' | 'connecting') {
  act(() => {
    useDashboardStore.setState({ connectionStatus: status });
  });
}

describe('App connection-loss banner', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    useDashboardStore.setState({ connectionStatus: 'connected', view: 'get-started', error: null });
  });
  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
  });

  it('shows the banner only after a sustained outage', () => {
    render(<App />);
    expect(screen.queryByTestId('connection-loss-banner')).not.toBeInTheDocument();

    setStatus('disconnected');
    // Not yet — the delay has not elapsed.
    act(() => { vi.advanceTimersByTime(5000); });
    expect(screen.queryByTestId('connection-loss-banner')).not.toBeInTheDocument();

    // Past the threshold — the banner appears.
    act(() => { vi.advanceTimersByTime(4000); });
    expect(screen.getByTestId('connection-loss-banner')).toBeInTheDocument();
    expect(screen.getByText('Connection lost')).toBeInTheDocument();
  });

  it('clears the banner when the connection is restored', () => {
    render(<App />);
    setStatus('error');
    act(() => { vi.advanceTimersByTime(9000); });
    expect(screen.getByTestId('connection-loss-banner')).toBeInTheDocument();

    setStatus('connected');
    expect(screen.queryByTestId('connection-loss-banner')).not.toBeInTheDocument();
  });

  it('stays dismissed until the next outage once the user closes it', () => {
    render(<App />);
    setStatus('disconnected');
    act(() => { vi.advanceTimersByTime(9000); });

    const closeButton = screen.getByLabelText('Close');
    act(() => { closeButton.click(); });
    expect(screen.queryByTestId('connection-loss-banner')).not.toBeInTheDocument();

    // Still down, but dismissed — it does not reappear on its own.
    act(() => { vi.advanceTimersByTime(9000); });
    expect(screen.queryByTestId('connection-loss-banner')).not.toBeInTheDocument();

    // Reconnect then drop again — the banner re-arms.
    setStatus('connected');
    setStatus('disconnected');
    act(() => { vi.advanceTimersByTime(9000); });
    expect(screen.getByTestId('connection-loss-banner')).toBeInTheDocument();
  });

  // Each reconnect attempt passes through "connecting" (the back-off is 3/6/9/12/15 s).
  // An attempt must not restart the 8 s clock, hide the banner, or re-arm a dismissal.
  function failedRetry() {
    setStatus('connecting');
    setStatus('disconnected');
  }

  it('appears 8 s into an outage even though reconnect attempts keep cycling through connecting', () => {
    render(<App />);
    setStatus('disconnected');
    act(() => { vi.advanceTimersByTime(3000); });
    failedRetry();
    act(() => { vi.advanceTimersByTime(3000); });
    failedRetry();
    act(() => { vi.advanceTimersByTime(2100); });
    expect(screen.getByTestId('connection-loss-banner')).toBeInTheDocument();
  });

  it('stays visible while a retry is in flight', () => {
    render(<App />);
    setStatus('disconnected');
    act(() => { vi.advanceTimersByTime(9000); });
    setStatus('connecting');
    expect(screen.getByTestId('connection-loss-banner')).toBeInTheDocument();
    setStatus('disconnected');
    expect(screen.getByTestId('connection-loss-banner')).toBeInTheDocument();
  });

  it('stays dismissed through later retries of the same outage', () => {
    render(<App />);
    setStatus('disconnected');
    act(() => { vi.advanceTimersByTime(9000); });
    act(() => { screen.getByLabelText('Close').click(); });
    for (let i = 0; i < 4; i++) {
      failedRetry();
      act(() => { vi.advanceTimersByTime(9000); });
      expect(screen.queryByTestId('connection-loss-banner')).not.toBeInTheDocument();
    }
  });

  it('shows the banner when the server is unreachable from the first page load', () => {
    useDashboardStore.setState({ connectionStatus: 'connecting' });
    render(<App />);
    setStatus('disconnected');
    act(() => { vi.advanceTimersByTime(3000); });
    failedRetry();
    act(() => { vi.advanceTimersByTime(5100); });
    expect(screen.getByTestId('connection-loss-banner')).toBeInTheDocument();
  });

  it('does not show for a brief connecting phase that ends connected', () => {
    useDashboardStore.setState({ connectionStatus: 'connecting' });
    render(<App />);
    act(() => { vi.advanceTimersByTime(500); });
    setStatus('connected');
    act(() => { vi.advanceTimersByTime(20000); });
    expect(screen.queryByTestId('connection-loss-banner')).not.toBeInTheDocument();
  });
});
