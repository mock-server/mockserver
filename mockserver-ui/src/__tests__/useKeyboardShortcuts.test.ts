import { describe, it, expect, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useKeyboardShortcuts } from '../hooks/useKeyboardShortcuts';

function fireKey(key: string, options: Partial<KeyboardEvent> = {}) {
  window.dispatchEvent(
    new KeyboardEvent('keydown', { key, bubbles: true, ...options }),
  );
}

describe('useKeyboardShortcuts', () => {
  it('calls onSearch on Ctrl+K', () => {
    const handlers = { onSearch: vi.fn(), onClear: vi.fn(), onToggleFilter: vi.fn() };
    renderHook(() => useKeyboardShortcuts(handlers));

    fireKey('k', { ctrlKey: true });
    expect(handlers.onSearch).toHaveBeenCalledOnce();
    expect(handlers.onClear).not.toHaveBeenCalled();
  });

  it('calls onSearch on Meta+K (macOS)', () => {
    const handlers = { onSearch: vi.fn(), onClear: vi.fn(), onToggleFilter: vi.fn() };
    renderHook(() => useKeyboardShortcuts(handlers));

    fireKey('k', { metaKey: true });
    expect(handlers.onSearch).toHaveBeenCalledOnce();
  });

  it('calls onClear on Ctrl+L', () => {
    const handlers = { onSearch: vi.fn(), onClear: vi.fn(), onToggleFilter: vi.fn() };
    renderHook(() => useKeyboardShortcuts(handlers));

    fireKey('l', { ctrlKey: true });
    expect(handlers.onClear).toHaveBeenCalledOnce();
  });

  it('calls onToggleFilter on Escape', () => {
    const handlers = { onSearch: vi.fn(), onClear: vi.fn(), onToggleFilter: vi.fn() };
    renderHook(() => useKeyboardShortcuts(handlers));

    fireKey('Escape');
    expect(handlers.onToggleFilter).toHaveBeenCalledOnce();
  });

  it('does not call handlers for unrelated keys', () => {
    const handlers = { onSearch: vi.fn(), onClear: vi.fn(), onToggleFilter: vi.fn() };
    renderHook(() => useKeyboardShortcuts(handlers));

    fireKey('a');
    fireKey('Enter');
    fireKey('k'); // no modifier
    expect(handlers.onSearch).not.toHaveBeenCalled();
    expect(handlers.onClear).not.toHaveBeenCalled();
    expect(handlers.onToggleFilter).not.toHaveBeenCalled();
  });
});
