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
      description: { json: false, first: '10:00:00', second: 'FORWARDED_REQUEST' },
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

  it('renders group copy button', () => {
    const { container } = render(<LogGroup group={mockGroup} />);
    const groupCopyBtn = container.querySelector('.group-copy-btn button');
    expect(groupCopyBtn).toBeInTheDocument();
  });

  it('renders group copy button for description-only header', () => {
    const descOnlyGroup: LogGroupType = {
      key: 'g2',
      group: {
        key: 'g2_summary',
        value: {
          description: { json: false, first: '10:05:00', second: 'NO_MATCH_RESPONSE' },
        },
      },
      value: [
        { key: 'c1', value: { messageParts: [{ key: 'c1_msg', value: 'no match found' }] } },
      ],
    };

    const { container } = render(<LogGroup group={descOnlyGroup} />);
    const groupCopyBtn = container.querySelector('.group-copy-btn button');
    expect(groupCopyBtn).toBeInTheDocument();
  });
});
