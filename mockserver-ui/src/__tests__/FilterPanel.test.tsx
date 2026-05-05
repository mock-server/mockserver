import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import FilterPanel from '../components/FilterPanel';
import { useDashboardStore } from '../store';

function renderFilterPanel(onFilterChange = vi.fn()) {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <FilterPanel onFilterChange={onFilterChange} />
    </ThemeProvider>,
  );
}

describe('FilterPanel', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      filterEnabled: false,
      filterExpanded: false,
    });
  });

  it('renders the filter header', () => {
    renderFilterPanel();
    expect(screen.getByText('Request Filter')).toBeInTheDocument();
  });

  it('expands when header is clicked', async () => {
    const user = userEvent.setup();
    renderFilterPanel();

    await user.click(screen.getByText('Request Filter'));
    expect(screen.getByText('Enabled')).toBeInTheDocument();
  });

  it('shows method, path, and toggle fields when expanded', async () => {
    const user = userEvent.setup();
    renderFilterPanel();

    await user.click(screen.getByText('Request Filter'));

    expect(screen.getByLabelText('Enabled')).toBeInTheDocument();
    expect(screen.getByLabelText('Path')).toBeInTheDocument();
    expect(screen.getByLabelText('Secure')).toBeInTheDocument();
    expect(screen.getByLabelText('Keep-Alive')).toBeInTheDocument();
  });

  it('calls onFilterChange with empty filter when disabled', async () => {
    const onFilterChange = vi.fn();
    renderFilterPanel(onFilterChange);

    await waitFor(() => {
      expect(onFilterChange).toHaveBeenCalledWith({});
    });
  });

  it('includes method and path in filter when enabled', async () => {
    const user = userEvent.setup();
    const onFilterChange = vi.fn();
    useDashboardStore.setState({ filterExpanded: true });
    renderFilterPanel(onFilterChange);

    await user.click(screen.getByLabelText('Enabled'));

    const pathInput = screen.getByLabelText('Path');
    await user.type(pathInput, '/api');

    await waitFor(() => {
      const lastCall = onFilterChange.mock.calls[onFilterChange.mock.calls.length - 1]![0];
      expect(lastCall).toHaveProperty('path', '/api');
    });
  });
});
