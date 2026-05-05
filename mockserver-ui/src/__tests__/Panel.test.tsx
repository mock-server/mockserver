import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Panel from '../components/Panel';

describe('Panel', () => {
  it('renders title and count badge', () => {
    render(
      <Panel title="Test Panel" count={42} searchValue="" onSearchChange={() => {}}>
        <div>content</div>
      </Panel>,
    );
    expect(screen.getByText('Test Panel')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
  });

  it('renders children', () => {
    render(
      <Panel title="Test" count={0} searchValue="" onSearchChange={() => {}}>
        <div>child content</div>
      </Panel>,
    );
    expect(screen.getByText('child content')).toBeInTheDocument();
  });

  it('renders search input with current value', () => {
    render(
      <Panel title="Test" count={0} searchValue="hello" onSearchChange={() => {}}>
        <div />
      </Panel>,
    );
    const input = screen.getByPlaceholderText('Search...');
    expect(input).toHaveValue('hello');
  });

  it('calls onSearchChange when typing in search', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(
      <Panel title="Test" count={0} searchValue="" onSearchChange={onChange}>
        <div />
      </Panel>,
    );

    const input = screen.getByPlaceholderText('Search...');
    await user.type(input, 'abc');

    expect(onChange).toHaveBeenCalledTimes(3);
    expect(onChange).toHaveBeenLastCalledWith('c');
  });
});
