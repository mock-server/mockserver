import { useEffect } from 'react';

interface ShortcutHandlers {
  onSearch: () => void;
  onClear: () => void;
  onToggleFilter: () => void;
}

export function useKeyboardShortcuts(handlers: ShortcutHandlers) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      const mod = e.metaKey || e.ctrlKey;

      if (mod && e.key === 'k') {
        e.preventDefault();
        handlers.onSearch();
      }

      if (mod && e.key === 'l') {
        e.preventDefault();
        handlers.onClear();
      }

      if (e.key === 'Escape') {
        handlers.onToggleFilter();
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handlers]);
}
