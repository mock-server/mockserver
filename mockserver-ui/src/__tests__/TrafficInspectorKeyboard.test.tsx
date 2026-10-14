import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import TrafficInspector from '../components/TrafficInspector';
import { useDashboardStore } from '../store';
import type { JsonListItem } from '../types';

function renderTrafficInspector() {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <TrafficInspector />
    </ThemeProvider>,
  );
}

function recorded(path: string, response: Record<string, unknown> | null = { statusCode: 200 }): JsonListItem {
  const value: Record<string, unknown> = {
    httpRequest: { method: 'GET', path, headers: [{ name: 'host', values: ['example.com'] }] },
  };
  if (response) value['httpResponse'] = response;
  return { key: `${path}_request`, value };
}

// Newest last, as the list shows them.
function rowButtons(): HTMLElement[] {
  return screen.getAllByRole('button').filter((el) => el.hasAttribute('data-traffic-row-key'));
}

describe('TrafficInspector — rows from the keyboard', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      // The store holds the newest first; the list shows the newest last.
      recordedRequests: [recorded('/kbd/three'), recorded('/kbd/two'), recorded('/kbd/one')],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
      serverConfiguration: null,
    });
  });

  it('shows no row count in the header, since the list is a capped window', () => {
    renderTrafficInspector();
    const chips = within(screen.getByTestId('traffic-toolbar')).queryAllByText(/^\d+\+?$/);
    expect(chips).toEqual([]);
  });

  it('gives exactly one row a tab stop, the newest, and makes every row a button', () => {
    renderTrafficInspector();
    const rows = rowButtons();
    expect(rows).toHaveLength(3);
    expect(rows.map((row) => row.tabIndex)).toEqual([-1, -1, 0]);
    expect(rows[2]).toHaveTextContent('/kbd/three');
    expect(rows[2]).toHaveAttribute('aria-pressed', 'false');
  });

  it('opens a row with Enter and closes it with Space', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();
    const newest = rowButtons()[2]!;
    newest.focus();

    await user.keyboard('{Enter}');
    expect(useDashboardStore.getState().selectedTrafficKey).toBe('/kbd/three_request');
    expect(rowButtons()[2]).toHaveAttribute('aria-pressed', 'true');

    rowButtons()[2]!.focus();
    await user.keyboard(' ');
    expect(useDashboardStore.getState().selectedTrafficKey).toBeNull();
  });

  it('moves focus and the tab stop between rows with the arrow keys', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();
    rowButtons()[2]!.focus();

    await user.keyboard('{ArrowUp}');
    expect(rowButtons()[1]).toHaveFocus();
    await waitFor(() => expect(rowButtons().map((row) => row.tabIndex)).toEqual([-1, 0, -1]));

    await user.keyboard('{ArrowUp}{ArrowUp}');
    expect(rowButtons()[0]).toHaveFocus();

    await user.keyboard('{ArrowDown}');
    expect(rowButtons()[1]).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(useDashboardStore.getState().selectedTrafficKey).toBe('/kbd/two_request');
  });

  it('keeps the rows out of the tab order in compare mode, where their checkboxes are the controls', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();
    await user.click(screen.getByRole('button', { name: /compare/i }));
    expect(rowButtons()).toEqual([]);
    expect(screen.getAllByRole('checkbox', { name: /Select GET \/kbd\/.* to compare/ })).toHaveLength(3);
  });

  it('shows a row path tooltip that cannot take the pointer from the row below', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();
    await user.hover(screen.getAllByTestId('traffic-row-path')[0]!);
    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip).toHaveTextContent('example.com/kbd/one');
    expect(tooltip.className).not.toMatch(/popperInteractive/);
  });
});

describe('TrafficInspector — log level above INFO', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [recorded('/warn/unmatched', null), recorded('/warn/matched')],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  it('explains why unmatched requests carry no status, and can be dismissed', async () => {
    useDashboardStore.setState({ serverConfiguration: { logLevel: 'WARN' } });
    const user = userEvent.setup();
    renderTrafficInspector();
    const hint = screen.getByTestId('traffic-log-level-hint');
    expect(hint).toHaveTextContent('MockServer is logging at WARN');
    expect(hint).toHaveTextContent('need the log level INFO');

    await user.click(within(hint).getByRole('button', { name: /close/i }));
    expect(screen.queryByTestId('traffic-log-level-hint')).toBeNull();
  });

  it('says nothing at INFO', () => {
    useDashboardStore.setState({ serverConfiguration: { logLevel: 'INFO' } });
    renderTrafficInspector();
    expect(screen.queryByTestId('traffic-log-level-hint')).toBeNull();
  });

  it('says nothing for a recorded DNS query or binary message, which never has an HTTP response', () => {
    useDashboardStore.setState({
      serverConfiguration: { logLevel: 'WARN' },
      recordedRequests: [
        { key: 'dns_request', value: { httpRequest: { dnsName: 'api.example.com', dnsType: 'A' } } },
        { key: 'binary_request', value: { httpRequest: { binaryData: 'AAEC' } } },
        recorded('/warn/matched'),
      ],
    });
    renderTrafficInspector();
    expect(screen.queryByTestId('traffic-log-level-hint')).toBeNull();
  });

  it('says nothing when every request has its response', () => {
    useDashboardStore.setState({ serverConfiguration: { logLevel: 'WARN' }, recordedRequests: [recorded('/warn/matched')] });
    renderTrafficInspector();
    expect(screen.queryByTestId('traffic-log-level-hint')).toBeNull();
  });
});
