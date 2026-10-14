import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor, act, fireEvent, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import LogPressureBanner from '../components/LogPressureBanner';
import { useDashboardStore } from '../store';
import type { ConnectionParams } from '../hooks/useConnectionParams';

const params: ConnectionParams = { host: 'localhost', port: '1080', secure: false };

function scrape({ ringFull = 0, inFlightBytes = 0, evicted = 0 }: { ringFull?: number; inFlightBytes?: number; evicted?: number }): string {
  return [
    '# TYPE mock_server_dropped_log_events_total counter',
    `mock_server_dropped_log_events_total{reason="in_flight_bytes"} ${inFlightBytes}.0`,
    `mock_server_dropped_log_events_total{reason="ring_full"} ${ringFull}.0`,
    '# TYPE mock_server_evicted_log_entries_total counter',
    `mock_server_evicted_log_entries_total ${evicted}.0`,
  ].join('\n');
}

function mockMetrics(response: { status?: number; body?: string }): void {
  const status = response.status ?? 200;
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => ({
      status,
      ok: status >= 200 && status < 300,
      text: async () => response.body ?? '',
    })),
  );
}

// Each call to fetch returns the next body (the last one repeats).
function mockMetricsSequence(bodies: string[]): void {
  let call = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => {
      const body = bodies[Math.min(call, bodies.length - 1)];
      call += 1;
      return { status: 200, ok: true, text: async () => body };
    }),
  );
}

function renderBanner() {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <LogPressureBanner connectionParams={params} />
    </ThemeProvider>,
  );
}

async function nextPoll(ms: number): Promise<void> {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe('LogPressureBanner', () => {
  beforeEach(() => {
    vi.useRealTimers();
    useDashboardStore.setState({ serverConfiguration: { metricsEnabled: true }, serverConfigurationUnavailable: false });
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('ring-full drops: warns and points at the log level, not at raising retention', async () => {
    mockMetrics({ body: scrape({ ringFull: 128 }) });
    renderBanner();

    const banner = await screen.findByTestId('log-pressure-banner');
    expect(banner).toHaveTextContent('Log Events Dropped');
    expect(banner.className).toMatch(/Warning/);
    expect(banner).toHaveAttribute('role', 'alert');
    const reason = within(banner).getByTestId('log-pressure-ring-full');
    expect(reason).toHaveTextContent('128 events were dropped');
    expect(reason).toHaveTextContent(/lower the log level/);
    expect(reason).toHaveTextContent(/keep the log level and set maxLoggedMatchFailures \(e\.g\. 10\) to cap the did-not-match entries/);
    expect(reason).toHaveTextContent(/ringBufferSize only absorbs short bursts/);
    expect(banner).not.toHaveTextContent(/maxLogEntries/);
    expect(within(banner).queryByTestId('log-pressure-in-flight-bytes')).not.toBeInTheDocument();
    expect(within(banner).queryByTestId('log-pressure-evicted')).not.toBeInTheDocument();
    expect(screen.getByTestId('log-pressure-banner-learn-more')).toHaveAttribute(
      'href',
      'https://www.mock-server.com/mock_server/performance.html',
    );
  });

  it('in-flight-byte drops: warns about large bodies and the in-flight cap', async () => {
    mockMetrics({ body: scrape({ inFlightBytes: 1 }) });
    renderBanner();

    const banner = await screen.findByTestId('log-pressure-banner');
    expect(banner).toHaveTextContent('Log Events Dropped');
    expect(banner.className).toMatch(/Warning/);
    const reason = within(banner).getByTestId('log-pressure-in-flight-bytes');
    expect(reason).toHaveTextContent('1 event was dropped');
    expect(reason).toHaveTextContent(/bodies waiting to be logged exceeded the in-flight memory cap/);
    expect(reason).toHaveTextContent(/maxEventLogSizeInBytes/);
    expect(banner).not.toHaveTextContent(/ringBufferSize/);
    expect(banner).not.toHaveTextContent(/maxLoggedMatchFailures/);
    expect(within(banner).queryByTestId('log-pressure-ring-full')).not.toBeInTheDocument();
  });

  it('eviction only: informs and points at the retention bounds', async () => {
    mockMetrics({ body: scrape({ evicted: 1500 }) });
    renderBanner();

    const banner = await screen.findByTestId('log-pressure-banner');
    expect(banner).toHaveTextContent('Log Events Evicted');
    expect(banner.className).toMatch(/Info/);
    expect(banner).toHaveAttribute('role', 'status');
    const reason = within(banner).getByTestId('log-pressure-evicted');
    expect(reason).toHaveTextContent('1,500 of the oldest entries were evicted');
    expect(reason).toHaveTextContent(/maxLogEntries/);
    expect(reason).toHaveTextContent(/maxEventLogSizeInBytes/);
    expect(banner).not.toHaveTextContent(/ringBufferSize/);
    expect(banner).not.toHaveTextContent(/dropped/);
  });

  it('lists every cause present, under the drop title', async () => {
    mockMetrics({ body: scrape({ ringFull: 2, inFlightBytes: 3, evicted: 4 }) });
    renderBanner();

    const banner = await screen.findByTestId('log-pressure-banner');
    expect(banner).toHaveTextContent('Log Events Dropped');
    expect(within(banner).getByTestId('log-pressure-ring-full')).toHaveTextContent('2 events were dropped');
    expect(within(banner).getByTestId('log-pressure-in-flight-bytes')).toHaveTextContent('3 events were dropped');
    expect(within(banner).getByTestId('log-pressure-evicted')).toHaveTextContent('4 of the oldest entries were evicted');
  });

  it('shows drops from a server without the reason label as unattributed', async () => {
    mockMetrics({ body: 'mock_server_dropped_log_events_total 5.0' });
    renderBanner();

    const banner = await screen.findByTestId('log-pressure-banner');
    expect(banner).toHaveTextContent('Log Events Dropped');
    expect(within(banner).getByTestId('log-pressure-unattributed')).toHaveTextContent('5 events were dropped');
  });

  it('renders nothing when nothing has been dropped or evicted', async () => {
    mockMetrics({ body: `${scrape({})}\nrequests_received_count 3.0` });
    const { container } = renderBanner();

    // Give the poll a chance to resolve, then assert the banner never appears.
    await waitFor(() => expect(fetch).toHaveBeenCalled());
    expect(screen.queryByTestId('log-pressure-banner')).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing when metrics are disabled (404)', async () => {
    mockMetrics({ status: 404 });
    renderBanner();

    await waitFor(() => expect(fetch).toHaveBeenCalled());
    expect(screen.queryByTestId('log-pressure-banner')).not.toBeInTheDocument();
  });

  it('never requests metrics from a server whose configuration says metrics are off', async () => {
    useDashboardStore.setState({ serverConfiguration: { metricsEnabled: false } });
    mockMetrics({ status: 404 });
    renderBanner();

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20));
    });
    expect(fetch).not.toHaveBeenCalled();
    expect(screen.queryByTestId('log-pressure-banner')).toBeNull();
  });

  it('waits for the configuration before requesting metrics, then polls once it says metrics are on', async () => {
    useDashboardStore.setState({ serverConfiguration: null });
    mockMetrics({ body: scrape({ ringFull: 3 }) });
    renderBanner();

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20));
    });
    expect(fetch).not.toHaveBeenCalled();

    act(() => useDashboardStore.setState({ serverConfiguration: { metricsEnabled: true } }));
    expect(await screen.findByTestId('log-pressure-banner')).toHaveTextContent('3 events were dropped');
    expect(fetch).toHaveBeenCalledWith('http://localhost:1080/mockserver/metrics', expect.anything());
  });

  it('probes metrics when the configuration could not be loaded, so the warning still works', async () => {
    useDashboardStore.setState({ serverConfiguration: null, serverConfigurationUnavailable: true });
    mockMetrics({ body: scrape({ ringFull: 5 }) });
    renderBanner();

    expect(await screen.findByTestId('log-pressure-banner')).toHaveTextContent('5 events were dropped');
  });

  it('can be dismissed', async () => {
    mockMetrics({ body: scrape({ ringFull: 5 }) });
    const user = userEvent.setup();
    renderBanner();

    await screen.findByTestId('log-pressure-banner');
    await user.click(screen.getByRole('button', { name: /close/i }));
    expect(screen.queryByTestId('log-pressure-banner')).not.toBeInTheDocument();
  });

  it('after dismissal, re-shows for new drops but not for further eviction alone', async () => {
    vi.useFakeTimers();
    mockMetricsSequence([
      scrape({ evicted: 100 }),
      scrape({ evicted: 900 }),
      scrape({ evicted: 950, inFlightBytes: 2 }),
    ]);
    renderBanner();

    // (fireEvent is synchronous — userEvent's internal delays deadlock under fake timers.)
    await nextPoll(0);
    expect(screen.getByTestId('log-pressure-banner')).toHaveTextContent('Log Events Evicted');
    fireEvent.click(screen.getByRole('button', { name: /close/i }));

    await nextPoll(15000);
    expect(screen.queryByTestId('log-pressure-banner')).not.toBeInTheDocument();

    await nextPoll(15000);
    const banner = screen.getByTestId('log-pressure-banner');
    expect(banner).toHaveTextContent('Log Events Dropped');
    expect(within(banner).getByTestId('log-pressure-in-flight-bytes')).toHaveTextContent('2 events were dropped');

    vi.useRealTimers();
  });

  it('re-shows after a server restart resets the counters below the dismissed values', async () => {
    // First poll reports 128 drops; after a server restart the counter resets
    // and the next poll reports only 3 — fewer than the dismissed total.
    vi.useFakeTimers();
    mockMetricsSequence([scrape({ ringFull: 128 }), scrape({ ringFull: 3 })]);
    renderBanner();

    await nextPoll(0);
    expect(screen.getByTestId('log-pressure-banner')).toHaveTextContent('128');
    fireEvent.click(screen.getByRole('button', { name: /close/i }));
    expect(screen.queryByTestId('log-pressure-banner')).not.toBeInTheDocument();

    // Next poll (after the 15s interval) returns 3 — a regression below the
    // dismissed total of 128, i.e. a restarted server with fresh drops. The
    // banner must reappear rather than stay hidden until it climbs past 128.
    await nextPoll(15000);
    expect(screen.getByTestId('log-pressure-ring-full')).toHaveTextContent('3 events were dropped');

    vi.useRealTimers();
  });

  it('re-shows an eviction notice after a server restart resets eviction below the dismissed value', async () => {
    vi.useFakeTimers();
    mockMetricsSequence([scrape({ evicted: 100 }), scrape({ evicted: 5 })]);
    renderBanner();

    await nextPoll(0);
    expect(screen.getByTestId('log-pressure-banner')).toHaveTextContent('Log Events Evicted');
    fireEvent.click(screen.getByRole('button', { name: /close/i }));
    expect(screen.queryByTestId('log-pressure-banner')).not.toBeInTheDocument();

    // No drops on either side of the restart, so only the eviction count's
    // regression (100 -> 5) can tell that the dismissal no longer applies.
    await nextPoll(15000);
    const banner = screen.getByTestId('log-pressure-banner');
    expect(banner).toHaveTextContent('Log Events Evicted');
    expect(within(banner).getByTestId('log-pressure-evicted')).toHaveTextContent('5 of the oldest entries were evicted');

    vi.useRealTimers();
  });
});
