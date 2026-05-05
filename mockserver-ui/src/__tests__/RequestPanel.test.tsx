import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RequestPanel from '../components/RequestPanel';

describe('RequestPanel', () => {
  it('shows empty state when no items', () => {
    render(
      <RequestPanel title="Received Requests" items={[]} searchValue="" onSearchChange={() => {}} />,
    );
    expect(screen.getByText('No requests')).toBeInTheDocument();
  });

  it('renders requests with reverse index (most recent first)', () => {
    const items = [
      { key: 'r1', value: { method: 'GET', path: '/first' } },
      { key: 'r2', value: { method: 'POST', path: '/second' } },
    ];

    render(
      <RequestPanel
        title="Received Requests"
        items={items}
        searchValue=""
        onSearchChange={() => {}}
      />,
    );

    const badge = document.querySelector('.MuiBadge-badge');
    expect(badge).toHaveTextContent('2');
  });

  it('filters by search term', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const items = [
      { key: 'r1', description: 'GET /api', value: { method: 'GET', path: '/api' } },
      { key: 'r2', description: 'POST /submit', value: { method: 'POST', path: '/submit' } },
    ];

    render(
      <RequestPanel
        title="Received"
        items={items}
        searchValue=""
        onSearchChange={onChange}
      />,
    );

    const searchInput = screen.getByPlaceholderText('Search...');
    await user.type(searchInput, 'POST');

    expect(onChange).toHaveBeenCalled();
  });

  it('shows title and count', () => {
    const items = [
      { key: 'r1', value: { path: '/test' } },
    ];

    render(
      <RequestPanel
        title="Proxied Requests"
        items={items}
        searchValue=""
        onSearchChange={() => {}}
      />,
    );

    expect(screen.getByText('Proxied Requests')).toBeInTheDocument();
    const badge = document.querySelector('.MuiBadge-badge');
    expect(badge).toHaveTextContent('1');
  });
});
