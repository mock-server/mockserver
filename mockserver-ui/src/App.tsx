import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Snackbar from '@mui/material/Snackbar';
import { useDashboardStore, coerceView, ALL_VIEWS } from './store';
import { buildTheme } from './theme';
import { useConnectionParams } from './hooks/useConnectionParams';
import { useWebSocket } from './hooks/useWebSocket';
import { useDebugMismatch } from './hooks/useDebugMismatch';
import { DebugMismatchContext } from './hooks/DebugMismatchContext';
import { useGenerateStub } from './hooks/useGenerateStub';
import { GenerateStubContext } from './hooks/GenerateStubContext';
import { SetBreakpointContext } from './hooks/SetBreakpointContext';
import { useKeyboardShortcuts } from './hooks/useKeyboardShortcuts';
import AppBar, { NAV_TAB_DESCRIPTIONS } from './components/AppBar';
import WorkspaceTabBar from './components/WorkspaceTabBar';
import FilterPanel from './components/FilterPanel';
import DashboardGrid from './components/DashboardGrid';
import DebugMismatchDialog from './components/DebugMismatchDialog';
import GenerateStubDialog from './components/GenerateStubDialog';
import ConfirmDialog from './components/ConfirmDialog';
import ShortcutsDialog from './components/ShortcutsDialog';
import ErrorBoundary from './components/ErrorBoundary';
import AnalyticsBanner from './components/AnalyticsBanner';
import LogPressureBanner from './components/LogPressureBanner';
import FrameLimitBanner from './components/FrameLimitBanner';
import { getConfiguration } from './lib/configuration';
import { initAnalytics, trackView } from './lib/analytics';
import { CLEAR_LOGS_CONFIRM_MESSAGE, CLEAR_LOGS_CONFIRM_TITLE } from './lib/clearServerText';
import { requestLogSearchFocus } from './lib/logSearchFocus';
import { connectionKey, effectiveConnection, urlConnectionParams } from './lib/connectionTarget';
import { releaseBreakpointCallbackClientsExcept } from './lib/breakpointCallbackClient';
import type { RequestFilter } from './types';

// Lazy-loaded so the @mui/x-charts bundle only loads when the Metrics tab is
// opened, keeping it off the initial dashboard load.
const MetricsView = lazy(() => import('./components/MetricsView'));

// Lazy-loaded so the Monaco editor (multi-MB) bundle only loads when the
// Composer tab is opened, keeping it off the initial dashboard load.
const ComposerView = lazy(() => import('./components/ComposerView'));

// Lazy-loaded so the LLM Optimise screen's report rendering stays off the initial
// dashboard load (it is only needed when the LLM Optimise tab is opened).
const OptimiseView = lazy(() => import('./components/OptimiseView'));

// Lazy-loaded so the MCP server health panel stays off the initial dashboard
// load (it is only needed when the MCP Health tab is opened).
const McpServerHealthPanel = lazy(() => import('./components/McpServerHealthPanel'));

// Lazy-loaded so the @mui/x-charts bundle (shared with Metrics) only loads when
// the Performance tab is opened, keeping it off the initial dashboard load.
const LoadScenarioPanel = lazy(() => import('./components/LoadScenarioPanel'));

// Per-view code splitting: every remaining top-level view is lazy-loaded too, so
// only the shell (AppBar + FilterPanel + DashboardGrid) and the view the user is
// actually looking at land in the initial chunk. Each is wrapped in its own
// Suspense fallback below, following the pattern of the views above. DashboardGrid
// stays eagerly imported as the primary data view rendered by the default shell.
const OnboardingPanel = lazy(() => import('./components/OnboardingPanel'));
const TrafficInspector = lazy(() => import('./components/TrafficInspector'));
const SessionInspector = lazy(() => import('./components/SessionInspector'));
const LibraryView = lazy(() => import('./components/LibraryView'));
const ServiceChaosPanel = lazy(() => import('./components/ServiceChaosPanel'));
const DriftPanel = lazy(() => import('./components/DriftPanel'));
const SloPanel = lazy(() => import('./components/SloPanel'));
const VerificationView = lazy(() => import('./components/VerificationView'));
const AsyncApiPanel = lazy(() => import('./components/AsyncApiPanel'));
const GrpcServicesPanel = lazy(() => import('./components/GrpcServicesPanel'));
const BreakpointsPanel = lazy(() => import('./components/BreakpointsPanel'));
const ContractTestPanel = lazy(() => import('./components/ContractTestPanel'));
const ClusterPanel = lazy(() => import('./components/ClusterPanel'));
// Standalone Scenarios view — renders the same ScenarioPanel that the Mocks
// composer surfaces as a tab, giving the scenario state-machine a first-class
// nav entry (two access paths, one component).
const ScenarioPanel = lazy(() => import('./components/ScenarioPanel'));
// Audit trail view (GET /mockserver/audit) — read-only control-plane mutation log.
const AuditPanel = lazy(() => import('./components/AuditPanel'));

// How long the WebSocket must stay down before the persistent connection-loss
// banner appears. Brief reconnects (the common case under StrictMode remount or
// a server restart) clear well before this, so the banner only nags on a real,
// sustained outage.
const CONNECTION_LOSS_BANNER_DELAY_MS = 8000;

export default function App() {
  const themeMode = useDashboardStore((s) => s.themeMode);
  const view = useDashboardStore((s) => s.view);
  const error = useDashboardStore((s) => s.error);
  const setError = useDashboardStore((s) => s.setError);
  const connectionStatus = useDashboardStore((s) => s.connectionStatus);
  const theme = useMemo(() => buildTheme(themeMode), [themeMode]);

  // Persistent connection-loss banner: shown once the socket has not been
  // connected for CONNECTION_LOSS_BANNER_DELAY_MS, and dismissable by the user.
  // An outage spans every reconnect attempt: each retry passes through
  // "connecting", which must neither restart the timer nor re-arm a dismissed
  // banner. Only a successful reconnect ends the outage and re-arms it.
  const isUp = connectionStatus === 'connected';
  const [outage, setOutage] = useState({ up: isUp, overdue: false, dismissed: false });
  if (outage.up !== isUp) {
    setOutage({ up: isUp, overdue: false, dismissed: false });
  }
  useEffect(() => {
    if (isUp) return undefined;
    const timer = setTimeout(
      () => setOutage((o) => (o.up ? o : { ...o, overdue: true })),
      CONNECTION_LOSS_BANNER_DELAY_MS,
    );
    return () => clearTimeout(timer);
  }, [isUp]);
  const connectionLost = !isUp && outage.overdue;
  const bannerDismissed = outage.dismissed;
  const dismissBanner = useCallback(() => setOutage((o) => ({ ...o, dismissed: true })), []);

  const generateStubOpen = useDashboardStore((s) => s.generateStubOpen);
  const generateStubSuggestions = useDashboardStore((s) => s.generateStubSuggestions);
  const generateStubConfidence = useDashboardStore((s) => s.generateStubConfidence);
  const closeGenerateStub = useDashboardStore((s) => s.closeGenerateStub);
  const notification = useDashboardStore((s) => s.notification);
  const setNotification = useDashboardStore((s) => s.setNotification);

  const params = useConnectionParams();
  const { start, sendFilter, clearServer } = useWebSocket(params);
  // The app bar and everything below the workspace bar remount when the target changes, so no
  // component keeps local state (a list, an open dialog, a held row) from the server being left.
  const targetKey = connectionKey(params);
  const { debugMismatch } = useDebugMismatch(params);
  const { generateStub } = useGenerateStub(params);
  // "Set breakpoint" from a log row: seed the matcher form + switch to the
  // Breakpoints view. The store action is stable, so this provider value is too.
  const setBreakpointPrefill = useDashboardStore((s) => s.setBreakpointPrefill);

  // Open the WebSocket on mount. `connect` is stable (its deps — params and the
  // Zustand actions — are all stable), so this runs once per mount rather than
  // on every render, and useWebSocket tears the socket down on unmount. Crucially
  // there is NO once-only ref guard here: under React StrictMode the initial
  // mount is immediately unmounted and remounted, and the unmount disconnects the
  // socket — a guard would skip the remount's reconnect and leave the connection
  // dead (status stuck until a later action lazily reconnects).
  useEffect(() => {
    start();
  }, [start]);

  // Another browser tab changed the workspaces: adopt its edits for every workspace but ours.
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === 'mockserver-workspaces') {
        useDashboardStore.getState().adoptWorkspacesFromOtherTab(event.newValue);
      }
    };
    globalThis.addEventListener?.('storage', onStorage);
    return () => globalThis.removeEventListener?.('storage', onStorage);
  }, []);

  // A breakpoint session (and the matchers the server holds for it) lives while a workspace
  // targets its server; closing or retargeting the last such workspace releases it.
  const workspaces = useDashboardStore((s) => s.workspaces);
  const liveConnection = useDashboardStore((s) => s.connection);
  const activeWorkspaceId = useDashboardStore((s) => s.activeWorkspaceId);
  useEffect(() => {
    const base = urlConnectionParams();
    const keys = new Set(workspaces.map((w) =>
      connectionKey(effectiveConnection(w.id === activeWorkspaceId ? liveConnection : w.connection, base))));
    releaseBreakpointCallbackClientsExcept(keys);
  }, [workspaces, liveConnection, activeWorkspaceId]);

  // Keep the active view in sync with the URL hash so browser back/forward and
  // manually-edited deep links (e.g. `#/contract`) navigate the dashboard. The
  // store's own setView writes the hash; here we react to externally-driven hash
  // changes. Invalid hashes are ignored (coerceView returns null).
  const setView = useDashboardStore((s) => s.setView);
  useEffect(() => {
    const onHashChange = () => {
      const next = coerceView(globalThis.location?.hash?.replace(/^#\/?/, '') ?? '');
      if (next && next !== useDashboardStore.getState().view) {
        setView(next);
      }
    };
    globalThis.addEventListener?.('hashchange', onHashChange);
    return () => globalThis.removeEventListener?.('hashchange', onHashChange);
  }, [setView]);

  // Fetch the server configuration once at startup and hand it to the analytics
  // module, which runs all privacy gates and (only if every gate passes) lazily
  // loads posthog-js and emits `app_open`. We pass the valid-view whitelist
  // (ALL_VIEWS) so trackView can drop any non-view value, and the current view
  // so the analytics module emits the initial `view_change` exactly once when
  // activation completes (activation is async — the `[view]` effect below has
  // already fired-and-dropped by then). initAnalytics is idempotent (one-shot
  // decision), so a duplicate call under StrictMode remount is a no-op. Failure
  // to fetch config simply means analytics stays off — never surfaced. The
  // configuration is also kept in the store for views that depend on it.
  useEffect(() => {
    const controller = new AbortController();
    useDashboardStore.getState().setServerConfiguration(null);
    void getConfiguration(params, controller.signal)
      .then((config) => {
        if (!controller.signal.aborted) {
          useDashboardStore.getState().setServerConfiguration(config);
          // Only the dashboard URL's server decides analytics, never a workspace's other target.
          if (connectionKey(params) === connectionKey(urlConnectionParams())) {
            initAnalytics(config, {
              validViews: ALL_VIEWS,
              initialView: useDashboardStore.getState().view,
            });
          }
        }
      })
      .catch(() => {
        // Analytics stays off; views that depend on the configuration fall back.
        if (!controller.signal.aborted) useDashboardStore.getState().setServerConfigurationUnavailable();
      });
    return () => controller.abort();
  }, [params]);

  // Report subsequent navigations to analytics. The store stays pure —
  // navigation is tracked here, at the single place that observes the selected
  // `view`. The INITIAL view is emitted by the analytics module at activation
  // time (see above), so this effect skips its first run to avoid double-counting
  // the starting tab; it then emits every real view change. trackView is a no-op
  // unless analytics is active.
  const initialViewTracked = useRef(false);
  useEffect(() => {
    if (!initialViewTracked.current) {
      initialViewTracked.current = true;
      return;
    }
    trackView(view);
  }, [view]);

  const handleFilterChange = useCallback(
    (filter: RequestFilter) => {
      sendFilter(filter);
    },
    [sendFilter],
  );

  const [clearLogsConfirm, setClearLogsConfirm] = useState(false);
  // The keyboard-shortcuts help dialog is owned here so both the `?` shortcut and
  // the AppBar keyboard-icon button open the same single instance.
  const [shortcutsOpen, setShortcutsOpen] = useState(false);

  const shortcutHandlers = useMemo(
    () => ({
      onSearch: () => {
        // The log search field lives in the Dashboard view; from any other view,
        // switch there and the field takes focus as soon as it mounts.
        if (!requestLogSearchFocus()) useDashboardStore.getState().setView('dashboard');
      },
      onClear: () => {
        setClearLogsConfirm(true);
      },
      onToggleFilter: () => {
        useDashboardStore.getState().toggleFilterExpanded();
      },
      onShowShortcuts: () => {
        setShortcutsOpen(true);
      },
    }),
    [],
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
      <DebugMismatchContext.Provider value={debugMismatch}>
      <GenerateStubContext.Provider value={generateStub}>
      <SetBreakpointContext.Provider value={setBreakpointPrefill}>
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
          <AppBar
            key={`appbar-${targetKey}`}
            onClearServer={handleClearServer}
            onClearLogs={handleClearLogs}
            onClearExpectations={handleClearExpectations}
            onShowShortcuts={() => setShortcutsOpen(true)}
          />
          {/* Workspace switcher: its own row below the app bar so the tabs never
              compete with the navigation for horizontal space. Renders nothing
              until a second workspace exists. Kept OUTSIDE the ErrorBoundary
              below, like the AppBar, so a crashing view can still be escaped by
              switching workspace. */}
          <WorkspaceTabBar />
          {NAV_TAB_DESCRIPTIONS[view] && (
            <Typography
              variant="body2"
              data-testid="view-description"
              sx={{
                flexShrink: 0,
                px: 1.5,
                py: 0.5,
                color: 'text.secondary',
                bgcolor: 'action.hover',
                borderBottom: 1,
                borderColor: 'divider',
                fontSize: '0.8rem',
              }}
            >
              {NAV_TAB_DESCRIPTIONS[view]}
            </Typography>
          )}
          {connectionLost && !bannerDismissed && (
            <Alert
              severity="warning"
              role="alert"
              aria-live="assertive"
              onClose={dismissBanner}
              sx={{ mx: 1, mt: 1, flexShrink: 0 }}
              data-testid="connection-loss-banner"
            >
              <AlertTitle>Connection lost</AlertTitle>
              The dashboard has lost its live connection to MockServer and is trying to
              reconnect. Displayed data may be stale until the connection is restored.
            </Alert>
          )}
          {/* Log-pressure warning: only the traffic/log views (Dashboard grid +
              Traffic inspector) render request/log data that silent ring-buffer
              eviction would make incomplete, so the banner is scoped to them —
              which also avoids polling metrics from unrelated tabs. */}
          {(view === 'dashboard' || view === 'traffic') && (
            <LogPressureBanner key={`pressure-${targetKey}`} connectionParams={params} />
          )}
          {(view === 'dashboard' || view === 'traffic') && <FrameLimitBanner view={view} />}
          <AnalyticsBanner />
          {(view === 'dashboard' || view === 'traffic' || view === 'sessions') && (
            <FilterPanel onFilterChange={handleFilterChange} />
          )}
          {error && (
            <Alert
              severity="error"
              role="alert"
              onClose={() => setError(null)}
              sx={{ mx: 1, mt: 1, flexShrink: 0 }}
            >
              {error}
            </Alert>
          )}
          {/*
            One ErrorBoundary around the whole view-switching region, reset on
            `view`. A crash in any single view (including a failed lazy() chunk
            import for Metrics) shows the fallback panel instead of blanking the
            entire app, and clears automatically when the user switches tabs. The
            AppBar above stays OUTSIDE the boundary so navigation always works.
          */}
          <ErrorBoundary key={`view-${targetKey}`} label="this view" resetKeys={[view]}>
            {view === 'get-started' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading…</Box>}>
                <OnboardingPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'dashboard' && <DashboardGrid />}
            {view === 'traffic' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading traffic…</Box>}>
                <TrafficInspector />
              </Suspense>
            )}
            {view === 'sessions' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading trace…</Box>}>
                <SessionInspector connectionParams={params} />
              </Suspense>
            )}
            {view === 'composer' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading composer…</Box>}>
                <ComposerView connectionParams={params} />
              </Suspense>
            )}
            {view === 'library' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading library…</Box>}>
                <LibraryView connectionParams={params} />
              </Suspense>
            )}
            {view === 'metrics' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading metrics…</Box>}>
                <MetricsView connectionParams={params} />
              </Suspense>
            )}
            {view === 'optimise' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading LLM Optimise…</Box>}>
                <OptimiseView connectionParams={params} />
              </Suspense>
            )}
            {view === 'mcp-health' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading MCP Health…</Box>}>
                <McpServerHealthPanel />
              </Suspense>
            )}
            {view === 'chaos' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading chaos…</Box>}>
                <ServiceChaosPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'performance' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading performance…</Box>}>
                <LoadScenarioPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'drift' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading drift…</Box>}>
                <DriftPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'verification' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading verify…</Box>}>
                <VerificationView connectionParams={params} />
              </Suspense>
            )}
            {view === 'slo' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading SLO…</Box>}>
                <SloPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'async' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading async…</Box>}>
                <AsyncApiPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'grpc' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading gRPC…</Box>}>
                <GrpcServicesPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'breakpoints' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading breakpoints…</Box>}>
                <BreakpointsPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'contract' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading contract…</Box>}>
                <ContractTestPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'cluster' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading cluster…</Box>}>
                <ClusterPanel connectionParams={params} />
              </Suspense>
            )}
            {view === 'scenarios' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading scenarios…</Box>}>
                <Box sx={{ flex: 1, overflow: 'auto', p: 1.5 }}>
                  <ScenarioPanel connectionParams={params} />
                </Box>
              </Suspense>
            )}
            {view === 'audit' && (
              <Suspense fallback={<Box sx={{ p: 2 }}>Loading audit…</Box>}>
                <AuditPanel connectionParams={params} />
              </Suspense>
            )}
          </ErrorBoundary>
        </Box>
        <Snackbar
          open={notification !== null}
          autoHideDuration={4000}
          onClose={() => setNotification(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        >
          {notification ? (
            <Alert
              severity={notification.severity}
              variant="filled"
              role="alert"
              onClose={() => setNotification(null)}
              sx={{ width: '100%' }}
            >
              {notification.message}
            </Alert>
          ) : undefined}
        </Snackbar>
        <DebugMismatchDialog key={`debug-${targetKey}`} connectionParams={params} />
        <GenerateStubDialog
          key={`stub-${targetKey}`}
          open={generateStubOpen}
          onClose={closeGenerateStub}
          suggestions={generateStubSuggestions}
          confidence={generateStubConfidence}
          connectionParams={params}
        />
        <ConfirmDialog
          open={clearLogsConfirm}
          title={CLEAR_LOGS_CONFIRM_TITLE}
          message={CLEAR_LOGS_CONFIRM_MESSAGE}
          confirmLabel="Clear logs"
          onConfirm={() => { void clearServer('log'); }}
          onClose={() => setClearLogsConfirm(false)}
        />
        <ShortcutsDialog open={shortcutsOpen} onClose={() => setShortcutsOpen(false)} />
      </SetBreakpointContext.Provider>
      </GenerateStubContext.Provider>
      </DebugMismatchContext.Provider>
    </ThemeProvider>
  );
}
