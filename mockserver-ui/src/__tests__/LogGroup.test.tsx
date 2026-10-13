import { describe, it, expect, vi } from 'vitest';
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

  it('does not render child entries by default', () => {
    // Collapsed groups unmount their children entirely (Collapse unmountOnExit)
    // rather than keeping them in the DOM hidden — so a dashboard full of
    // collapsed groups does not pay the DOM/heap cost of every nested entry.
    render(<LogGroup group={mockGroup} />);
    expect(screen.queryByText('checking headers')).not.toBeInTheDocument();
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

describe('LogGroup Explain action', () => {
  const requestGroup: LogGroupType = {
    ...mockGroup,
    key: 'entry-id_log_group',
    correlationId: 'corr-123456789',
    group: {
      key: 'entry-id_log',
      value: { description: { json: false, first: '10:00:00', second: 'RECEIVED_REQUEST' }, messageParts: [{ key: 'm', value: 'received request' }] },
    },
  };

  it('copies the group correlation ID, not the log entry id in its key', () => {
    render(<LogGroup group={requestGroup} />);
    expect(screen.getByText('corr-123')).toBeInTheDocument();
    expect(screen.queryByText('entry-id')).not.toBeInTheDocument();
  });

  it('offers Explain on a request group and hands the panel its correlation ID', async () => {
    const onExplain = vi.fn();
    const user = userEvent.setup();
    render(<LogGroup group={requestGroup} onExplain={onExplain} />);

    await user.click(screen.getByRole('button', { name: 'Explain' }));

    expect(onExplain).toHaveBeenCalledWith('corr-123456789');
  });

  it('offers no Explain without a correlation ID, a request entry or a panel to open it', () => {
    const onExplain = vi.fn();
    const { rerender } = render(<LogGroup group={{ ...requestGroup, correlationId: undefined }} onExplain={onExplain} />);
    expect(screen.queryByRole('button', { name: 'Explain' })).not.toBeInTheDocument();
    rerender(<LogGroup group={{ ...mockGroup, correlationId: 'corr-123456789', group: { key: 'g', value: { description: { json: false, first: '10:00:00', second: 'CREATED_EXPECTATION' } } } }} onExplain={onExplain} />);
    expect(screen.queryByRole('button', { name: 'Explain' })).not.toBeInTheDocument();
    rerender(<LogGroup group={requestGroup} />);
    expect(screen.queryByRole('button', { name: 'Explain' })).not.toBeInTheDocument();
  });
});
