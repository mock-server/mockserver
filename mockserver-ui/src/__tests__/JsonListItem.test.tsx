import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import JsonListItem from '../components/JsonListItem';

describe('JsonListItem', () => {
  it('renders index and description', () => {
    render(
      <JsonListItem
        item={{ key: 'r1', description: 'GET /api/users', value: { method: 'GET', path: '/api/users' } }}
        index={3}
      />,
    );
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('GET /api/users')).toBeInTheDocument();
  });

  it('does not show JSON body by default', () => {
    const { container } = render(
      <JsonListItem
        item={{ key: 'r1', description: 'GET /test', value: { method: 'GET', path: '/test' } }}
        index={1}
      />,
    );
    expect(container.querySelector('.w-rjv')).not.toBeInTheDocument();
  });

  it('shows JSON body after clicking the header row', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <JsonListItem
        item={{ key: 'r1', description: 'POST /submit', value: { method: 'POST', path: '/submit' } }}
        index={1}
      />,
    );

    await user.click(screen.getByText('POST /submit'));
    expect(container.querySelector('.w-rjv')).toBeInTheDocument();
  });

  it('collapses JSON body when clicked again', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <JsonListItem
        item={{ key: 'r1', description: 'DELETE /item', value: { method: 'DELETE' } }}
        index={1}
      />,
    );

    await user.click(screen.getByText('DELETE /item'));
    expect(container.querySelector('.w-rjv')).toBeInTheDocument();

    await user.click(screen.getByText('DELETE /item'));
    expect(container.querySelector('.w-rjv')).not.toBeInTheDocument();
  });

  it('renders without description', () => {
    render(
      <JsonListItem
        item={{ key: 'r1', value: { path: '/no-desc' } }}
        index={5}
      />,
    );
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('shows chevron right icon when collapsed and expand icon when expanded', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <JsonListItem
        item={{ key: 'r1', description: 'GET /toggle', value: { method: 'GET' } }}
        index={1}
      />,
    );

    expect(container.querySelector('[data-testid="ChevronRightIcon"]')).toBeInTheDocument();
    expect(container.querySelector('[data-testid="ExpandMoreIcon"]')).not.toBeInTheDocument();

    await user.click(screen.getByText('GET /toggle'));
    expect(container.querySelector('[data-testid="ExpandMoreIcon"]')).toBeInTheDocument();
    expect(container.querySelector('[data-testid="ChevronRightIcon"]')).not.toBeInTheDocument();
  });
});
