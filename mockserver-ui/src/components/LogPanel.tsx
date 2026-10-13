import { useCallback, memo, useEffect, useRef, useMemo, useState } from 'react';
import Typography from '@mui/material/Typography';
import { useDashboardStore } from '../store';
import { isLogGroup } from '../types';
import Panel from './Panel';
import LogEntry from './LogEntry';
import LogGroup from './LogGroup';
import ProgressiveList from './ProgressiveList';
import ExplainRequestDialog from './ExplainRequestDialog';
import { useConnectionParams } from '../hooks/useConnectionParams';
import { useExpansion } from '../hooks/useExpansion';
import { useHeldItems } from '../hooks/useHeldItems';
import { useFollow } from '../hooks/useFollow';
import { matchesLogSearch, isForwardedLogEntry } from '../lib/searchMatcher';
import { LOG_FILTER_OPTIONS } from '../lib/filterDSL';
import { registerLogSearchInput } from '../lib/logSearchFocus';

// Log rows carry no httpRequest/httpResponse, so `matchesLogSearch` can satisfy
// no field operator at all. Declaring that to the search box (rather than
// leaving the full vocabulary advertised) is what turns a typed `status:>=400`
// into a visible "not supported here" instead of a silent empty list.
const LOG_SEARCH_FIELDS = LOG_FILTER_OPTIONS.fields ?? [];

// Stable key accessor for useHeldItems — module scope, so its identity never changes.
const keyOf = (e: { key: string }) => e.key;

function LogPanel() {
  const logMessages = useDashboardStore((s) => s.logMessages);
  const search = useDashboardStore((s) => s.logSearch);
  const setSearch = useDashboardStore((s) => s.setLogSearch);
  const showForwarded = useDashboardStore((s) => s.logShowForwarded);
  const searchRef = useRef<HTMLInputElement>(null);
  // Cmd/Ctrl+K (handled in App) focuses this field through the registry.
  useEffect(() => {
    const el = searchRef.current;
    return el ? registerLogSearchInput(el) : undefined;
  }, []);

  const filtered = useMemo(() => {
    let rows = logMessages;
    if (!showForwarded) rows = rows.filter((m) => !isForwardedLogEntry(m));
    if (search) rows = rows.filter((m) => matchesLogSearch(m, search));
    return rows;
  }, [logMessages, search, showForwarded]);

  const expansion = useExpansion();
  // Hold what the reader is reading. The live window is capped at 100 rows and
  // drops the oldest as new ones arrive, so under load an open or scrolled-to row
  // is DELETED from the feed within seconds. While the reader is mid-read those
  // rows are held; new rows still arrive and prepend above them.
  // Console order: oldest first, newest appended at the BOTTOM.
  const displayOrder = useMemo(() => [...filtered].reverse(), [filtered]);
  const [follow, setFollow] = useFollow();
  const shown = useHeldItems(displayOrder, keyOf, !follow);

  // Opening a row means the reader has stopped watching and started reading, so
  // stop following. That single flag defuses BOTH hazards at once: the tail pin
  // in Panel is gated on `follow`, so it can no longer scroll the opened row
  // away, and `useHeldItems` is gated on `!follow`, so the row is held once the
  // server stops sending it. Suppressing only the scroll would leave the row to
  // be evicted; holding only the rows would leave it to be scrolled off screen.
  //
  // It also keeps the Follow control honest. Quietly not-following while the chip
  // still reads "Following" would be one more instrument saying it does something
  // it does not -- which is the defect this whole change exists to remove. The
  // chip flips to "Follow", and one click resumes.
  const openRow = useCallback(
    (key: string) => {
      if (!expansion.isExpanded(key)) setFollow(false);
      expansion.toggle(key);
    },
    [expansion, setFollow],
  );

  // The Explain dialog lives here, not in the row: a row unmounts when it leaves the
  // virtualized window, which would close the dialog mid-read. Explaining is reading too.
  const params = useConnectionParams();
  const [explaining, setExplaining] = useState<string | null>(null);
  const explain = useCallback(
    (correlationId: string) => {
      setFollow(false);
      setExplaining(correlationId);
    },
    [setFollow],
  );

  return (
    <Panel
      title="Log Messages"
      // No count: the server sends a capped window, so logMessages.length pins at
      // the cap and stops being a count.
      filteredCount={undefined}
      searchValue={search}
      onSearchChange={setSearch}
      follow={follow}
      onFollowChange={setFollow}
      searchInputRef={searchRef}
      searchFields={LOG_SEARCH_FIELDS}
      liveRegion
    >
      {shown.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
          {logMessages.length === 0 ? 'No log messages yet — server activity appears here as requests are handled.' : 'No matching log messages'}
        </Typography>
      ) : (
        <ProgressiveList
          pinnedToTail={follow}
          count={shown.length}
          getKey={(i) => shown[i]!.key}
          renderRow={(i) => {
            const message = shown[i]!;
            return isLogGroup(message) ? (
              <LogGroup
                group={message}
                open={expansion.isExpanded(message.key)}
                onToggleOpen={openRow}
                onExplain={explain}
              />
            ) : (
              <LogEntry
                entry={message.value}
                entryKey={message.key}
                expanded={expansion.isExpanded(message.key)}
                onToggleExpand={openRow}
                divider
                collapsible
              />
            );
          }}
        />
      )}
      {explaining && (
        <ExplainRequestDialog
          open
          onClose={() => setExplaining(null)}
          correlationId={explaining}
          connectionParams={params}
        />
      )}
    </Panel>
  );
}

// Memoized because `DashboardGrid` subscribes to `recordedRequests` and
// `proxiedRequests` in order to pass them to its two `RequestPanel` children,
// so the GRID re-renders whenever EITHER traffic array changes — which
// re-renders all four panels, including the ones whose own data did not change.
// Each panel already subscribes to (or is handed) exactly the state it needs,
// so a parent-driven re-render is pure waste. `memo` makes the panel skip it;
// its own Zustand subscriptions still re-render it whenever ITS data changes,
// so nothing displayed changes. Measured in
// `src/__tests__/perf-panelIsolation.test.tsx`.
export default memo(LogPanel);
