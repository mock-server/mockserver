import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CopyButton from '../components/CopyButton';

const writeTextMock = vi.fn().mockResolvedValue(undefined);

Object.defineProperty(navigator, 'clipboard', {
  value: { writeText: writeTextMock },
  writable: true,
  configurable: true,
});

describe('CopyButton', () => {
  beforeEach(() => {
    writeTextMock.mockClear();
  });

  it('renders a button', () => {
    render(<CopyButton text="hello" />);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('calls clipboard API when clicked', async () => {
    render(<CopyButton text="test content" />);

    fireEvent.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(writeTextMock).toHaveBeenCalledWith('test content');
    });
  });
});
