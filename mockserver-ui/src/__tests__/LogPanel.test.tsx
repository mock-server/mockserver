import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LogPanel from '../components/LogPanel';
import { useDashboardStore } from '../store';

describe('LogPanel', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      logMessages: [],
      logSearch: '',
      autoScroll: true,
      logShowForwarded: true,
    });
  });

  it('shows empty state when no log messages', () => {
    render(<LogPanel />);
    expect(screen.getByText(/No log messages/)).toBeInTheDocument();
  });

  it('renders log entries', () => {
    useDashboardStore.setState({
      logMessages: [
        { key: 'log1', value: { messageParts: [{ key: 'msg', value: 'test log entry' }] } },
      ],
    });
    render(<LogPanel />);
    expect(screen.getByText('test log entry')).toBeInTheDocument();
  });

  it('filters log messages by search term', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      logMessages: [
        { key: 'log1', value: { messageParts: [{ key: 'msg1', value: 'error occurred' }] } },
        { key: 'log2', value: { messageParts: [{ key: 'msg2', value: 'request received' }] } },
      ],
    });

    render(<LogPanel />);

    const searchInput = screen.getByLabelText('Search');
    await user.type(searchInput, 'error');

    expect(screen.getByText('error occurred')).toBeInTheDocument();
    expect(screen.queryByText('request received')).not.toBeInTheDocument();
  });

  it('shows "no matching" message when search matches nothing', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      logMessages: [
        { key: 'log1', value: { messageParts: [{ key: 'msg1', value: 'test entry' }] } },
      ],
    });

    render(<LogPanel />);
    const searchInput = screen.getByLabelText('Search');
    await user.type(searchInput, 'xyz-nonexistent');

    expect(screen.getByText('No matching log messages')).toBeInTheDocument();
  });

  it('explains an operator it cannot satisfy instead of silently showing nothing', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      logMessages: [
        { key: 'log1', value: { messageParts: [{ key: 'msg1', value: 'error occurred' }] } },
      ],
    });

    render(<LogPanel />);
    // Log rows have no request/response, so `status:` can never match. The panel
    // must say so rather than leaving a bare "No matching log messages".
    await user.type(screen.getByLabelText('Search'), 'status:>=400 error');

    expect(screen.getByText(/No field operators are supported here/)).toBeInTheDocument();
    expect(screen.getByLabelText('Search')).toHaveAttribute('aria-invalid', 'true');
  });

  it('does not advertise field operators it cannot satisfy', () => {
    render(<LogPanel />);
    const placeholder = screen.getByLabelText('Search').getAttribute('placeholder') ?? '';
    expect(placeholder).toContain('/regex/');
    for (const operator of ['status:', 'method:', 'path:', 'host:', 'operation:']) {
      expect(placeholder).not.toContain(operator);
    }
  });

  it('hides forwarded request entries when "Show forwarded" is off', () => {
    useDashboardStore.setState({
      logShowForwarded: false,
      logMessages: [
        {
          key: 'fwd',
          value: {
            style: { color: 'rgb(152, 208, 255)' },
            messageParts: [{ key: 'm', value: 'forwarded entry' }],
          },
        },
        { key: 'normal', value: { messageParts: [{ key: 'm', value: 'received entry' }] } },
      ],
    });

    render(<LogPanel />);
    expect(screen.queryByText('forwarded entry')).not.toBeInTheDocument();
    expect(screen.getByText('received entry')).toBeInTheDocument();
  });

  it('shows forwarded entries when "Show forwarded" is on', () => {
    useDashboardStore.setState({
      logShowForwarded: true,
      logMessages: [
        {
          key: 'fwd',
          value: {
            style: { color: 'rgb(152, 208, 255)' },
            messageParts: [{ key: 'm', value: 'forwarded entry' }],
          },
        },
      ],
    });

    render(<LogPanel />);
    expect(screen.getByText('forwarded entry')).toBeInTheDocument();
  });

  it('renders log groups', () => {
    useDashboardStore.setState({
      logMessages: [
        {
          key: 'group1_log_group',
          group: {
            key: 'group1_summary',
            value: { messageParts: [{ key: 'summary', value: 'group summary' }] },
          },
          value: [
            { key: 'child1', value: { messageParts: [{ key: 'c1', value: 'child entry' }] } },
          ],
        },
      ],
    });

    render(<LogPanel />);
    expect(screen.getByText('group summary')).toBeInTheDocument();
  });
});

describe('LogPanel Explain dialog', () => {
  const requestGroup = {
    key: 'entry-1_log_group',
    correlationId: 'corr-1',
    group: { key: 'entry-1_log', value: { description: { json: false as const, first: '10:00:00', second: 'RECEIVED_REQUEST' }, messageParts: [{ key: 'm', value: 'received request' }] } },
    value: [{ key: 'entry-2_log', value: { messageParts: [{ key: 'm2', value: 'returning response' }] } }],
  };

  beforeEach(() => {
    useDashboardStore.setState({ logMessages: [requestGroup], logSearch: '', autoScroll: true, logShowForwarded: true });
  });

  it('keeps the dialog open when the explained row leaves the live window', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      correlationId: 'corr-1',
      request: { method: 'GET', path: '/orders' },
      atArrival: { outcome: 'MATCHED', expectationId: 'orders' },
      matchedExpectation: { expectationId: 'orders' },
      skippedExpectations: [],
      shadowedExpectations: [],
      totalExpectations: 1,
      evaluatedExpectations: 1,
    }), { status: 200 })));
    try {
      const user = userEvent.setup();
      render(<LogPanel />);

      await user.click(screen.getByRole('button', { name: 'Explain' }));
      expect(await screen.findByRole('dialog', { name: 'Explain Request' })).toBeInTheDocument();
      // Explaining stops the panel following, as opening a row does.
      expect(screen.getByText('Follow')).toBeInTheDocument();

      // Resume following through the toolbar's master switch, so the panel no longer holds
      // evicted rows, then let newer traffic push the explained group out of the window.
      act(() => useDashboardStore.setState({ autoScroll: false }));
      act(() => useDashboardStore.setState({ autoScroll: true }));
      act(() => {
        useDashboardStore.setState({
          logMessages: [{ key: 'newer_log', value: { messageParts: [{ key: 'n', value: 'newer traffic' }] } }],
        });
      });

      expect(screen.getByText('newer traffic')).toBeInTheDocument();
      expect(screen.queryByText('received request')).not.toBeInTheDocument();
      expect(screen.getByRole('dialog', { name: 'Explain Request' })).toBeInTheDocument();
      expect(await screen.findByText(/Now it would be served by expectation/)).toBeInTheDocument();
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
