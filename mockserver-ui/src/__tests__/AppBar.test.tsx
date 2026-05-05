import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import AppBar from '../components/AppBar';
import { useDashboardStore } from '../store';

function renderAppBar(overrides = {}) {
  const defaults = {
    onClearServer: vi.fn().mockResolvedValue(undefined),
    onClearLogs: vi.fn().mockResolvedValue(undefined),
    onClearExpectations: vi.fn().mockResolvedValue(undefined),
  };
  const props = { ...defaults, ...overrides };
  return {
    ...render(
      <ThemeProvider theme={buildTheme('dark')}>
        <AppBar {...props} />
      </ThemeProvider>,
    ),
    props,
  };
}

describe('AppBar', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      connectionStatus: 'connected',
      themeMode: 'dark',
      autoScroll: true,
    });
  });

  it('displays the MockServer title', () => {
    renderAppBar();
    expect(screen.getByText('MockServer')).toBeInTheDocument();
  });

  it('shows connection status chip', () => {
    renderAppBar();
    expect(screen.getByText('connected')).toBeInTheDocument();
  });

  it('shows different connection statuses', () => {
    useDashboardStore.setState({ connectionStatus: 'error' });
    renderAppBar();
    expect(screen.getByText('error')).toBeInTheDocument();
  });

  it('toggles theme when theme button is clicked', async () => {
    const user = userEvent.setup();
    renderAppBar();

    const themeButtons = screen.getAllByRole('button');
    const themeButton = themeButtons.find((b) => b.getAttribute('aria-label')?.includes('light') || b.querySelector('[data-testid="LightModeIcon"]'));

    if (themeButton) {
      await user.click(themeButton);
      expect(useDashboardStore.getState().themeMode).toBe('light');
    }
  });

  it('opens clear menu and calls clear server on reset', async () => {
    const user = userEvent.setup();
    const { props } = renderAppBar();

    const clearButton = screen.getAllByRole('button').find(
      (b) => b.querySelector('[data-testid="DeleteSweepIcon"]'),
    );
    expect(clearButton).toBeDefined();

    await user.click(clearButton!);
    expect(screen.getByText('Reset server (all)')).toBeInTheDocument();

    await user.click(screen.getByText('Reset server (all)'));
    expect(props.onClearServer).toHaveBeenCalledOnce();
  });

  it('clears UI only without calling server', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      logMessages: [{ key: 'l', value: {} }],
    });
    const { props } = renderAppBar();

    const clearButton = screen.getAllByRole('button').find(
      (b) => b.querySelector('[data-testid="DeleteSweepIcon"]'),
    );
    await user.click(clearButton!);
    await user.click(screen.getByText('Clear UI only'));

    expect(useDashboardStore.getState().logMessages).toEqual([]);
    expect(props.onClearServer).not.toHaveBeenCalled();
  });
});
