/**
 * Cmd/Ctrl+K focuses the Log Messages search field (the shortcut the help dialog
 * and the docs advertise). From a view without that field it switches to the
 * Dashboard and focuses the field once it mounts.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within, cleanup } from '@testing-library/react';
import { useDashboardStore } from '../store';
import LogPanel from '../components/LogPanel';
import { resetLogSearchFocus } from '../lib/logSearchFocus';

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
vi.mock('../components/AppBar', () => ({ default: () => null, NAV_TAB_DESCRIPTIONS: {} }));
vi.mock('../components/OnboardingPanel', () => ({ default: () => <div>get started</div> }));
// Only the Log Messages panel matters here; the rest of the grid is noise.
vi.mock('../components/DashboardGrid', () => ({ default: () => <LogPanel /> }));
vi.mock('../components/FilterPanel', () => ({ default: () => null }));
vi.mock('../components/LogPressureBanner', () => ({ default: () => null }));

import App from '../App';

function logSearchField(): HTMLElement {
  return within(screen.getByRole('log').closest('.MuiCard-root') ?? document.body).getByLabelText('Search');
}

describe('Cmd/Ctrl+K', () => {
  beforeEach(() => {
    resetLogSearchFocus();
    useDashboardStore.setState({ connectionStatus: 'connected', error: null, logMessages: [], logSearch: '' });
  });
  afterEach(() => {
    cleanup();
  });

  it('focuses the Log Messages search field on the Dashboard', async () => {
    useDashboardStore.setState({ view: 'dashboard' });
    render(<App />);
    const field = await waitFor(() => logSearchField());
    expect(field).not.toHaveFocus();

    fireEvent.keyDown(window, { key: 'k', ctrlKey: true });
    expect(field).toHaveFocus();
  });

  it('works with Cmd on macOS too', async () => {
    useDashboardStore.setState({ view: 'dashboard' });
    render(<App />);
    const field = await waitFor(() => logSearchField());
    fireEvent.keyDown(window, { key: 'k', metaKey: true });
    expect(field).toHaveFocus();
  });

  it('switches to the Dashboard from another view and focuses the field', async () => {
    useDashboardStore.setState({ view: 'get-started' });
    render(<App />);
    await screen.findByText('get started');

    fireEvent.keyDown(window, { key: 'k', ctrlKey: true });
    expect(useDashboardStore.getState().view).toBe('dashboard');
    await waitFor(() => expect(logSearchField()).toHaveFocus());
  });
});
