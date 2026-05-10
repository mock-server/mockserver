import { create } from 'zustand';
import type {
  ConnectionStatus,
  DebugMismatchResult,
  JsonListItem,
  LogMessage,
  RequestFilter,
  ThemeMode,
  WebSocketMessage,
} from '../types';

interface DashboardState {
  logMessages: LogMessage[];
  activeExpectations: JsonListItem[];
  recordedRequests: JsonListItem[];
  proxiedRequests: JsonListItem[];

  requestFilter: RequestFilter;
  filterEnabled: boolean;
  filterExpanded: boolean;

  connectionStatus: ConnectionStatus;
  themeMode: ThemeMode;
  autoScroll: boolean;

  logSearch: string;
  expectationSearch: string;
  receivedSearch: string;
  proxiedSearch: string;

  error: string | null;

  debugMismatchOpen: boolean;
  debugMismatchLoading: boolean;
  debugMismatchResult: DebugMismatchResult | null;
  debugMismatchError: string | null;

  applyMessage: (message: WebSocketMessage) => void;
  clearUI: () => void;
  setRequestFilter: (filter: RequestFilter) => void;
  setFilterEnabled: (enabled: boolean) => void;
  setFilterExpanded: (expanded: boolean) => void;
  toggleFilterExpanded: () => void;
  setConnectionStatus: (status: ConnectionStatus) => void;
  setThemeMode: (mode: ThemeMode) => void;
  toggleThemeMode: () => void;
  setAutoScroll: (enabled: boolean) => void;
  toggleAutoScroll: () => void;
  setLogSearch: (term: string) => void;
  setExpectationSearch: (term: string) => void;
  setReceivedSearch: (term: string) => void;
  setProxiedSearch: (term: string) => void;
  setError: (error: string | null) => void;
  openDebugMismatch: (result: DebugMismatchResult) => void;
  closeDebugMismatch: () => void;
  setDebugMismatchLoading: (loading: boolean) => void;
  setDebugMismatchError: (error: string | null) => void;
}

function getInitialTheme(): ThemeMode {
  try {
    const stored = globalThis.localStorage?.getItem('mockserver-theme');
    if (stored === 'dark' || stored === 'light') return stored;
  } catch {
    // localStorage may not be available in test/SSR environments
  }
  return 'dark';
}

export const useDashboardStore = create<DashboardState>()((set) => ({
  logMessages: [],
  activeExpectations: [],
  recordedRequests: [],
  proxiedRequests: [],

  requestFilter: {},
  filterEnabled: false,
  filterExpanded: false,

  connectionStatus: 'disconnected',
  themeMode: getInitialTheme(),
  autoScroll: true,

  logSearch: '',
  expectationSearch: '',
  receivedSearch: '',
  proxiedSearch: '',

  error: null,

  debugMismatchOpen: false,
  debugMismatchLoading: false,
  debugMismatchResult: null,
  debugMismatchError: null,

  applyMessage: (message) =>
    set({
      logMessages: message.logMessages ?? [],
      activeExpectations: message.activeExpectations ?? [],
      recordedRequests: message.recordedRequests ?? [],
      proxiedRequests: message.proxiedRequests ?? [],
      error: message.error ?? null,
    }),

  clearUI: () =>
    set({
      logMessages: [],
      activeExpectations: [],
      recordedRequests: [],
      proxiedRequests: [],
      error: null,

      debugMismatchOpen: false,
      debugMismatchLoading: false,
      debugMismatchResult: null,
      debugMismatchError: null,
    }),

  setRequestFilter: (filter) => set({ requestFilter: filter }),
  setFilterEnabled: (enabled) => set({ filterEnabled: enabled }),
  setFilterExpanded: (expanded) => set({ filterExpanded: expanded }),
  toggleFilterExpanded: () => set((s) => ({ filterExpanded: !s.filterExpanded })),
  setConnectionStatus: (status) => set({ connectionStatus: status }),
  setThemeMode: (mode) => {
    try { globalThis.localStorage?.setItem('mockserver-theme', mode); } catch { /* noop */ }
    set({ themeMode: mode });
  },
  toggleThemeMode: () =>
    set((s) => {
      const next = s.themeMode === 'dark' ? 'light' : 'dark';
      try { globalThis.localStorage?.setItem('mockserver-theme', next); } catch { /* noop */ }
      return { themeMode: next };
    }),
  setAutoScroll: (enabled) => set({ autoScroll: enabled }),
  toggleAutoScroll: () => set((s) => ({ autoScroll: !s.autoScroll })),
  setLogSearch: (term) => set({ logSearch: term }),
  setExpectationSearch: (term) => set({ expectationSearch: term }),
  setReceivedSearch: (term) => set({ receivedSearch: term }),
  setProxiedSearch: (term) => set({ proxiedSearch: term }),
  setError: (error) => set({ error }),
  openDebugMismatch: (result) =>
    set({ debugMismatchOpen: true, debugMismatchResult: result, debugMismatchLoading: false, debugMismatchError: null }),
  closeDebugMismatch: () =>
    set({ debugMismatchOpen: false, debugMismatchResult: null, debugMismatchLoading: false, debugMismatchError: null }),
  setDebugMismatchLoading: (loading) => set({ debugMismatchLoading: loading }),
  setDebugMismatchError: (error) =>
    set({ debugMismatchError: error, debugMismatchLoading: false }),
}));
