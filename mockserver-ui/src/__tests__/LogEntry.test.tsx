import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import LogEntry from '../components/LogEntry';
import type { LogEntryValue } from '../types';

function renderInStore(entry: LogEntryValue, indent = false) {
  return render(<LogEntry entry={entry} indent={indent} />);
}

describe('LogEntry', () => {
  it('renders a text-only message part', () => {
    const entry: LogEntryValue = {
      messageParts: [
        { key: 'msg_0', value: 'received request' },
      ],
    };
    renderInStore(entry);
    expect(screen.getByText('received request')).toBeInTheDocument();
  });

  it('renders multiple message parts', () => {
    const entry: LogEntryValue = {
      messageParts: [
        { key: 'msg_0', value: 'creating expectation' },
        { key: 'msg_1', value: ' for path ', argument: false },
      ],
    };
    renderInStore(entry);
    expect(screen.getByText('creating expectation')).toBeInTheDocument();
    expect(screen.getByText(/for path/)).toBeInTheDocument();
  });

  it('renders with description text', () => {
    const entry: LogEntryValue = {
      description: 'GET /api/test',
      messageParts: [{ key: 'msg_0', value: 'matched' }],
    };
    renderInStore(entry);
    expect(screen.getByText('GET /api/test')).toBeInTheDocument();
  });

  it('passes style color via sx prop', () => {
    const entry: LogEntryValue = {
      style: { color: 'rgb(245, 95, 105)' },
      messageParts: [{ key: 'msg_0', value: 'warning message' }],
    };
    const { container } = renderInStore(entry);
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
    renderInStore(entry);
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', 'https://example.com/docs');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('renders indented entry with MUI classes', () => {
    const entry: LogEntryValue = {
      messageParts: [{ key: 'msg_0', value: 'indented' }],
    };
    const { container } = renderInStore(entry, true);
    const outerBox = container.firstChild as HTMLElement;
    expect(outerBox).toBeTruthy();
    expect(outerBox.classList.length).toBeGreaterThan(0);
  });
});
