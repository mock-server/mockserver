import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LogEntry, { entryToText } from '../components/LogEntry';
import type { LogEntryValue } from '../types';

describe('LogEntry', () => {
  it('renders a text-only message part', () => {
    const entry: LogEntryValue = {
      messageParts: [
        { key: 'msg_0', value: 'received request' },
      ],
    };
    render(<LogEntry entry={entry} />);
    expect(screen.getByText('received request')).toBeInTheDocument();
  });

  it('renders multiple message parts', () => {
    const entry: LogEntryValue = {
      messageParts: [
        { key: 'msg_0', value: 'creating expectation' },
        { key: 'msg_1', value: ' for path ', argument: false },
      ],
    };
    render(<LogEntry entry={entry} />);
    expect(screen.getByText('creating expectation')).toBeInTheDocument();
    expect(screen.getByText(/for path/)).toBeInTheDocument();
  });

  it('renders with description text', () => {
    const entry: LogEntryValue = {
      description: 'GET /api/test',
      messageParts: [{ key: 'msg_0', value: 'matched' }],
    };
    render(<LogEntry entry={entry} />);
    expect(screen.getByText('GET /api/test')).toBeInTheDocument();
  });

  it('passes style color via sx prop', () => {
    const entry: LogEntryValue = {
      style: { color: 'rgb(245, 95, 105)' },
      messageParts: [{ key: 'msg_0', value: 'warning message' }],
    };
    const { container } = render(<LogEntry entry={entry} />);
    const outerBox = container.firstChild as HTMLElement;
    expect(outerBox).toBeTruthy();
    expect(outerBox.classList.length).toBeGreaterThan(0);
  });

  it('renders links in message text', () => {
    const entry: LogEntryValue = {
      messageParts: [
        { key: 'msg_0', value: 'see https://example.com/docs for details' },
      ],
    };
    render(<LogEntry entry={entry} />);
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', 'https://example.com/docs');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('renders indented entry with MUI classes', () => {
    const entry: LogEntryValue = {
      messageParts: [{ key: 'msg_0', value: 'indented' }],
    };
    const { container } = render(<LogEntry entry={entry} indent />);
    const outerBox = container.firstChild as HTMLElement;
    expect(outerBox).toBeTruthy();
    expect(outerBox.classList.length).toBeGreaterThan(0);
  });
});

describe('LogEntry collapsible', () => {
  it('shows description and summary but hides body when collapsed', () => {
    const entry: LogEntryValue = {
      description: '05-05 10:57:18.895 INFO',
      messageParts: [
        { key: 'msg_0', value: 'loaded CA private key from path' },
      ],
    };
    render(<LogEntry entry={entry} collapsible />);
    expect(screen.getByText('05-05 10:57:18.895 INFO')).toBeInTheDocument();
    expect(screen.getByText('loaded CA private key from path')).toBeInTheDocument();
  });

  it('shows body when expanded', async () => {
    const user = userEvent.setup();
    const entry: LogEntryValue = {
      description: '10:00:00 INFO',
      messageParts: [
        { key: 'msg_0', value: 'server started on port 1080' },
      ],
    };
    render(<LogEntry entry={entry} collapsible />);

    await user.click(screen.getByText('10:00:00 INFO'));
    expect(screen.getByText('server started on port 1080')).toBeInTheDocument();
  });

  it('collapses body when clicked again', async () => {
    const user = userEvent.setup();
    const entry: LogEntryValue = {
      description: '10:00:00 DEBUG',
      messageParts: [
        { key: 'msg_0', value: 'debug info' },
      ],
    };
    render(<LogEntry entry={entry} collapsible />);

    await user.click(screen.getByText('10:00:00 DEBUG'));
    expect(screen.getByTestId('ExpandMoreIcon')).toBeInTheDocument();

    await user.click(screen.getByText('10:00:00 DEBUG'));
    expect(screen.getByTestId('ChevronRightIcon')).toBeInTheDocument();
    expect(screen.queryByTestId('ExpandMoreIcon')).not.toBeInTheDocument();
  });

  it('shows SYSTEM_MESSAGE when no description', () => {
    const entry: LogEntryValue = {
      messageParts: [
        { key: 'msg_0', value: 'some orphan message' },
      ],
    };
    render(<LogEntry entry={entry} collapsible />);
    expect(screen.getByText('SYSTEM_MESSAGE')).toBeInTheDocument();
  });

  it('truncates summary to 80 characters', () => {
    const longText = 'A'.repeat(100);
    const entry: LogEntryValue = {
      description: 'INFO',
      messageParts: [{ key: 'msg_0', value: longText }],
    };
    render(<LogEntry entry={entry} collapsible />);
    expect(screen.getByText('A'.repeat(80) + '\u2026')).toBeInTheDocument();
  });

  it('does not collapse when collapsible but no messageParts', () => {
    const entry: LogEntryValue = {
      description: '10:00:00 INFO',
    };
    render(<LogEntry entry={entry} collapsible />);
    expect(screen.getByText('10:00:00 INFO')).toBeInTheDocument();
    expect(screen.queryByTestId('ChevronRightIcon')).not.toBeInTheDocument();
  });
});

describe('entryToText', () => {
  it('includes string description', () => {
    const entry: LogEntryValue = {
      description: 'GET /api/test',
      messageParts: [{ key: 'msg_0', value: 'matched' }],
    };
    expect(entryToText(entry)).toBe('GET /api/test matched');
  });

  it('includes non-json description parts', () => {
    const entry: LogEntryValue = {
      description: { json: false, first: '10:00:00', second: 'FORWARDED_REQUEST' },
    };
    expect(entryToText(entry)).toBe('10:00:00 FORWARDED_REQUEST');
  });

  it('includes json description first part', () => {
    const entry: LogEntryValue = {
      description: { json: true, first: 'spec ', object: { openapi: '3.0' }, second: 'op1' },
    };
    expect(entryToText(entry)).toBe('spec');
  });

  it('returns empty string for empty entry', () => {
    expect(entryToText({})).toBe('');
  });

  it('serializes object messageParts as JSON', () => {
    const entry: LogEntryValue = {
      messageParts: [
        { key: 'msg_0', value: { method: 'GET' }, json: true, argument: true },
      ],
    };
    expect(entryToText(entry)).toContain('"method": "GET"');
  });

  it('joins array messageParts with newlines', () => {
    const entry: LogEntryValue = {
      messageParts: [
        { key: 'msg_0', value: ['line1', 'line2'], because: true, argument: true },
      ],
    };
    expect(entryToText(entry)).toBe('line1\nline2');
  });
});
