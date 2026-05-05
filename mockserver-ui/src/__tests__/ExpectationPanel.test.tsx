import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ExpectationPanel from '../components/ExpectationPanel';
import { useDashboardStore } from '../store';

describe('ExpectationPanel', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      activeExpectations: [],
      expectationSearch: '',
    });
  });

  it('shows empty state when no expectations', () => {
    render(<ExpectationPanel />);
    expect(screen.getByText('No active expectations')).toBeInTheDocument();
  });

  it('renders expectations with descriptions', () => {
    useDashboardStore.setState({
      activeExpectations: [
        {
          key: 'exp1',
          description: 'GET /api/users',
          value: { httpRequest: { method: 'GET', path: '/api/users' } },
        },
      ],
    });

    render(<ExpectationPanel />);
    expect(screen.getByText('GET /api/users')).toBeInTheDocument();
  });

  it('filters expectations by search', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      activeExpectations: [
        { key: 'exp1', description: 'GET /users', value: { httpRequest: { path: '/users' } } },
        { key: 'exp2', description: 'POST /orders', value: { httpRequest: { path: '/orders' } } },
      ],
    });

    render(<ExpectationPanel />);
    const searchInput = screen.getByPlaceholderText('Search...');
    await user.type(searchInput, 'orders');

    expect(screen.queryByText('GET /users')).not.toBeInTheDocument();
    expect(screen.getByText('POST /orders')).toBeInTheDocument();
  });

  it('shows count badge with correct number', () => {
    useDashboardStore.setState({
      activeExpectations: [
        { key: 'exp1', value: {} },
        { key: 'exp2', value: {} },
        { key: 'exp3', value: {} },
      ],
    });

    render(<ExpectationPanel />);
    const chip = document.querySelector('.MuiChip-label');
    expect(chip).toHaveTextContent('3');
  });
});
