import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LogGroup from '../components/LogGroup';
import type { LogGroup as LogGroupType } from '../types';

const mockGroup: LogGroupType = {
  key: 'group1_log_group',
  group: {
    key: 'group1_summary',
    value: {
      messageParts: [{ key: 'summary_msg', value: 'request matched expectation' }],
      style: { color: 'rgb(117, 185, 186)' },
    },
  },
  value: [
    {
      key: 'child1',
      value: {
        messageParts: [{ key: 'c1_msg', value: 'checking headers' }],
      },
    },
    {
      key: 'child2',
      value: {
        messageParts: [{ key: 'c2_msg', value: 'checking path' }],
      },
    },
  ],
};

describe('LogGroup', () => {
  it('renders the summary log entry', () => {
    render(<LogGroup group={mockGroup} />);
    expect(screen.getByText('request matched expectation')).toBeInTheDocument();
  });

  it('does not show child entries by default', () => {
    render(<LogGroup group={mockGroup} />);
    expect(screen.queryByText('checking headers')).not.toBeVisible();
  });

  it('expands to show child entries when clicked', async () => {
    const user = userEvent.setup();
    render(<LogGroup group={mockGroup} />);

    const buttons = screen.getAllByRole('button');
    const expandButton = buttons[0]!;
    await user.click(expandButton);

    expect(screen.getByText('checking headers')).toBeVisible();
    expect(screen.getByText('checking path')).toBeVisible();
  });
});
