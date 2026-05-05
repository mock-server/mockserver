import { useCallback, useEffect, useMemo, useRef } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';
import Alert from '@mui/material/Alert';
import { useDashboardStore } from './store';
import { buildTheme } from './theme';
import { useConnectionParams } from './hooks/useConnectionParams';
import { useWebSocket } from './hooks/useWebSocket';
import { useKeyboardShortcuts } from './hooks/useKeyboardShortcuts';
import AppBar from './components/AppBar';
import FilterPanel from './components/FilterPanel';
import DashboardGrid from './components/DashboardGrid';
import type { RequestFilter } from './types';

export default function App() {
  const themeMode = useDashboardStore((s) => s.themeMode);
  const error = useDashboardStore((s) => s.error);
  const clearUI = useDashboardStore((s) => s.clearUI);
  const theme = useMemo(() => buildTheme(themeMode), [themeMode]);

  const params = useConnectionParams();
  const { connect, sendFilter, clearServer } = useWebSocket(params);
  const initialConnectDone = useRef(false);

  useEffect(() => {
    if (!initialConnectDone.current) {
      initialConnectDone.current = true;
      connect({});
    }
  }, [connect]);

  const handleFilterChange = useCallback(
    (filter: RequestFilter) => {
      sendFilter(filter);
    },
    [sendFilter],
  );

  const logSearchInputRef = useRef<HTMLInputElement>(null);

  const shortcutHandlers = useMemo(
    () => ({
      onSearch: () => {
        logSearchInputRef.current?.focus();
      },
      onClear: () => {
        clearUI();
      },
      onToggleFilter: () => {
        useDashboardStore.getState().toggleFilterExpanded();
      },
    }),
    [clearUI],
  );

  useKeyboardShortcuts(shortcutHandlers);

  const handleClearServer = useCallback(async () => {
    await clearServer('all');
  }, [clearServer]);

  const handleClearLogs = useCallback(async () => {
    await clearServer('log');
  }, [clearServer]);

  const handleClearExpectations = useCallback(async () => {
    await clearServer('expectations');
  }, [clearServer]);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
        <AppBar
          onClearServer={handleClearServer}
          onClearLogs={handleClearLogs}
          onClearExpectations={handleClearExpectations}
        />
        <FilterPanel onFilterChange={handleFilterChange} />
        {error && (
          <Alert severity="error" sx={{ mx: 1, mt: 1, flexShrink: 0 }}>
            {error}
          </Alert>
        )}
        <DashboardGrid />
      </Box>
    </ThemeProvider>
  );
}
