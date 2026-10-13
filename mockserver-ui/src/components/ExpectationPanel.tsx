import { memo, useMemo, useRef, useState, useCallback } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Typography from '@mui/material/Typography';
import Link from '@mui/material/Link';
import Tooltip from '@mui/material/Tooltip';
import ToggleButton from '@mui/material/ToggleButton';
import SortIcon from '@mui/icons-material/Sort';
import ChecklistIcon from '@mui/icons-material/Checklist';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import { useDashboardStore } from '../store';
import Panel from './Panel';
import JsonListItemComponent, { extractPriority } from './JsonListItem';
import ProgressiveList from './ProgressiveList';
import ConfirmDialog from './ConfirmDialog';
import MatcherPlaygroundDialog from './MatcherPlaygroundDialog';
import OpenApiImportDialog from './OpenApiImportDialog';
import LintDialog from './LintDialog';
import { useExpansion } from '../hooks/useExpansion';
import { useHeldItems } from '../hooks/useHeldItems';
import { useConnectionParams } from '../hooks/useConnectionParams';
import { useWithWholeExpectation } from '../hooks/useWithWholeExpectation';
import { applyClientFilters } from '../lib/clientFilters';
import { matchesItemSearch } from '../lib/searchMatcher';
import { buildTurnPositionMap } from '../lib/scenarioState';
import { deleteExpectation } from '../lib/expectations';
import { humanizeError } from '../lib/errorMessage';
import type { JsonListItem } from '../types';

/** Pull the expectation id out of a row value, when present. */
function expectationIdOf(item: JsonListItem): string | null {
  const id = item.value['id'];
  return typeof id === 'string' ? id : null;
}

/**
 * Build a duplicate of an expectation suitable for the Composer's create flow:
 * a deep-ish copy of the value with its `id` removed so that saving it through
 * the normal create path mints a brand-new expectation rather than overwriting
 * the original. Priority and every other field are preserved. Purely
 * client-side — no backend call.
 */
export function duplicateValueWithoutId(value: Record<string, unknown>): Record<string, unknown> {
  const copy: Record<string, unknown> = structuredClone(value);
  delete copy['id'];
  return copy;
}

// Stable key accessor for useHeldItems — module scope, so its identity never changes.
const keyOf = (e: { key: string }) => e.key;

function ExpectationPanel() {
  const params = useConnectionParams();
  const expectations = useDashboardStore((s) => s.activeExpectations);
  // The server's TOTAL, not the length of the capped page it sent.
  const activeExpectationsTotal = useDashboardStore((s) => s.activeExpectationsTotal);
  const search = useDashboardStore((s) => s.expectationSearch);
  const setSearch = useDashboardStore((s) => s.setExpectationSearch);
  const filterEnabled = useDashboardStore((s) => s.filterEnabled);
  const actionTypeFilter = useDashboardStore((s) => s.actionTypeFilter);
  const llmProviderFilter = useDashboardStore((s) => s.llmProviderFilter);
  const setNotification = useDashboardStore((s) => s.setNotification);
  const editExpectation = useDashboardStore((s) => s.editExpectation);
  const setView = useDashboardStore((s) => s.setView);

  // Pending single-expectation delete, awaiting confirmation. Null when no
  // delete is in flight.
  const [pendingDelete, setPendingDelete] = useState<JsonListItem | null>(null);

  // The expectation whose matcher is being dry-run in the playground (null when
  // closed). Seeds MatcherPlaygroundDialog with the row's exact JSON.
  const [testExpectation, setTestExpectation] = useState<JsonListItem | null>(null);

  // Empty-state "import a spec" action opens the OpenAPI import dialog inline.
  const [openApiOpen, setOpenApiOpen] = useState(false);

  const [lintOpen, setLintOpen] = useState(false);

  // When true, rows are sorted by match priority (descending), which mirrors
  // the order MockServer evaluates equally-specific mocks. Off by default so
  // the list keeps its natural (insertion) order.
  const [sortByPriority, setSortByPriority] = useState(false);

  // Bulk-select mode: when on, each row shows a checkbox and a toolbar offers
  // "delete selected". `selectedKeys` holds the chosen expectation row keys.
  const [selectMode, setSelectMode] = useState(false);
  const [selectedKeys, setSelectedKeys] = useState<ReadonlySet<string>>(() => new Set());
  const [bulkDeleteConfirm, setBulkDeleteConfirm] = useState(false);

  // Guards the confirm handler against re-entrancy: the ConfirmDialog button
  // fires onConfirm() before the close re-render lands, so a fast double-click
  // could otherwise dispatch two DELETE requests for the same expectation.
  const deletingRef = useRef(false);

  // Compute turn N of M across the FULL set (not the filtered subset) so the
  // total stays meaningful even when search hides siblings.
  const turnPositions = useMemo(() => buildTurnPositionMap(expectations), [expectations]);

  const clientFiltered = useMemo(
    () => filterEnabled ? applyClientFilters(expectations, actionTypeFilter, llmProviderFilter) : expectations,
    [expectations, filterEnabled, actionTypeFilter, llmProviderFilter],
  );

  const searched = useMemo(
    () => (search ? clientFiltered.filter((e) => matchesItemSearch(e.value, search)) : clientFiltered),
    [clientFiltered, search],
  );

  // When priority sorting is on, order by priority descending (higher wins =
  // matched first). Use a stable sort over a copy and treat a missing priority
  // as 0 so non-expectation-shaped rows don't jump around. Insertion order is
  // preserved when the toggle is off.
  const filtered = useMemo(() => {
    if (!sortByPriority) return searched;
    return [...searched].sort(
      (a, b) => (extractPriority(b.value) ?? 0) - (extractPriority(a.value) ?? 0),
    );
  }, [searched, sortByPriority]);

  const expansion = useExpansion();
  // Hold what the reader is reading. The live window is capped at 100 rows and
  // drops the oldest as new ones arrive, so under load an open or scrolled-to row
  // is DELETED from the feed within seconds. While the reader is mid-read those
  // rows are held; new rows still arrive and prepend above them.
  // Active Expectations is a SET of registered mocks, not a time-ordered stream,
  // so it keeps its order and offers no Follow control. It still needs holding:
  // the server sends a capped page and drops entries from it, which would delete
  // rows under a reader.
  const [scrolledAway, setScrolledAway] = useState(false);
  const shown = useHeldItems(filtered, keyOf, scrolledAway || expansion.anyExpanded);

  const withWholeExpectation = useWithWholeExpectation();

  const handleEdit = useCallback(
    (item: JsonListItem) => {
      withWholeExpectation(item, (whole) => editExpectation(whole.value));
    },
    [editExpectation, withWholeExpectation],
  );

  // Open the matcher playground seeded with this row's exact expectation JSON.
  const handleTest = useCallback((item: JsonListItem) => {
    withWholeExpectation(item, setTestExpectation);
  }, [withWholeExpectation]);

  // Duplicate: load a COPY of the expectation into the Composer with its id
  // stripped so saving creates a new expectation. Reuses the same Composer
  // hand-off as Edit; the id-less value makes the Composer treat it as a fresh
  // draft rather than an update.
  const handleDuplicate = useCallback(
    (item: JsonListItem) => {
      withWholeExpectation(item, (whole) => editExpectation(duplicateValueWithoutId(whole.value)));
    },
    [editExpectation, withWholeExpectation],
  );

  const handleConfirmDelete = useCallback(async () => {
    if (!pendingDelete) return;
    const id = expectationIdOf(pendingDelete);
    if (!id) return;
    // No-op if a delete is already in flight (re-entrant double-click).
    if (deletingRef.current) return;
    deletingRef.current = true;
    try {
      await deleteExpectation(params, id);
      // Optimistically drop the row; the next WebSocket push will reconcile.
      useDashboardStore.setState((s) => ({
        activeExpectations: s.activeExpectations.filter((e) => e.key !== pendingDelete.key),
      }));
      setNotification({ message: `Expectation ${id} deleted`, severity: 'success' });
    } catch (e) {
      setNotification({ message: humanizeError(e).message, severity: 'error' });
    } finally {
      deletingRef.current = false;
    }
  }, [pendingDelete, params, setNotification]);

  // Rows eligible for bulk selection: only expectations carrying an id can be
  // deleted through the per-id clear endpoint.
  const selectableItems = useMemo(
    () => filtered.filter((item) => expectationIdOf(item) !== null),
    [filtered],
  );
  const selectableKeys = useMemo(
    () => new Set(selectableItems.map((item) => item.key)),
    [selectableItems],
  );
  // Selected keys intersected with what is currently selectable — a WebSocket
  // refresh can remove a row after it was ticked, so never act on a stale key.
  const effectiveSelected = useMemo(
    () => selectableItems.filter((item) => selectedKeys.has(item.key)),
    [selectableItems, selectedKeys],
  );
  const allSelected = selectableItems.length > 0 && effectiveSelected.length === selectableItems.length;
  const someSelected = effectiveSelected.length > 0 && !allSelected;

  const toggleSelectMode = useCallback(() => {
    setSelectMode((prev) => {
      if (prev) setSelectedKeys(new Set()); // leaving select mode clears the picks
      return !prev;
    });
  }, []);

  const toggleSelectKey = useCallback((key: string) => {
    setSelectedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  const toggleSelectAll = useCallback(() => {
    setSelectedKeys((prev) => {
      // If every selectable row is already picked, clear; otherwise select all.
      const allPicked = selectableItems.length > 0 && selectableItems.every((item) => prev.has(item.key));
      return allPicked ? new Set() : new Set(selectableItems.map((item) => item.key));
    });
  }, [selectableItems]);

  const handleBulkDelete = useCallback(async () => {
    if (deletingRef.current) return;
    const targets = effectiveSelected
      .map((item) => ({ key: item.key, id: expectationIdOf(item) }))
      .filter((t): t is { key: string; id: string } => t.id !== null);
    if (targets.length === 0) return;
    deletingRef.current = true;
    try {
      // Batch the per-id clears client-side (no bulk endpoint exists). Use
      // allSettled so one failure does not abort the rest.
      const results = await Promise.allSettled(
        targets.map((t) => deleteExpectation(params, t.id)),
      );
      const succeededKeys = new Set(
        targets.filter((_, i) => results[i]?.status === 'fulfilled').map((t) => t.key),
      );
      const failures = results.filter((r) => r.status === 'rejected');
      // Optimistically drop the successfully-deleted rows; the next WebSocket
      // push reconciles the rest.
      if (succeededKeys.size > 0) {
        useDashboardStore.setState((s) => ({
          activeExpectations: s.activeExpectations.filter((e) => !succeededKeys.has(e.key)),
        }));
      }
      setSelectedKeys((prev) => {
        const next = new Set(prev);
        for (const key of succeededKeys) next.delete(key);
        return next;
      });
      if (failures.length === 0) {
        setNotification({ message: `Deleted ${succeededKeys.size} expectation${succeededKeys.size === 1 ? '' : 's'}`, severity: 'success' });
      } else if (succeededKeys.size === 0) {
        setNotification({ message: `Failed to delete ${failures.length} expectation${failures.length === 1 ? '' : 's'}`, severity: 'error' });
      } else {
        setNotification({ message: `Deleted ${succeededKeys.size}, ${failures.length} failed`, severity: 'warning' });
      }
    } finally {
      deletingRef.current = false;
    }
  }, [effectiveSelected, params, setNotification]);

  return (
    <>
      <Panel
        title="Active Expectations"
        count={activeExpectationsTotal || expectations.length}
        filteredCount={filtered.length !== expectations.length ? filtered.length : undefined}
        searchValue={search}
        onSearchChange={setSearch}
        onScrolledAwayChange={setScrolledAway}

        headerActions={
          <>
            <Tooltip title="Sort by match priority (highest first)">
              <ToggleButton
                value="priority"
                selected={sortByPriority}
                onChange={() => setSortByPriority((prev) => !prev)}
                size="small"
                aria-label="Sort by priority"
                sx={{ height: 24, px: 0.75, py: 0, textTransform: 'none', gap: 0.25 }}
              >
                <SortIcon sx={{ fontSize: '1rem' }} />
                <Typography variant="caption">Priority</Typography>
              </ToggleButton>
            </Tooltip>
            <Tooltip title="Select multiple expectations to delete at once">
              <ToggleButton
                value="select"
                selected={selectMode}
                onChange={toggleSelectMode}
                size="small"
                aria-label="Select expectations"
                sx={{ height: 24, px: 0.75, py: 0, textTransform: 'none', gap: 0.25 }}
              >
                <ChecklistIcon sx={{ fontSize: '1rem' }} />
                <Typography variant="caption">Select</Typography>
              </ToggleButton>
            </Tooltip>
            <Tooltip title="Lint: find expectations that can never match, or will not match as written">
              <Button
                size="small"
                variant="outlined"
                color="inherit"
                onClick={() => setLintOpen(true)}
                aria-label="Lint expectations"
                startIcon={<FactCheckIcon sx={{ fontSize: '1rem' }} />}
                sx={{ height: 24, minWidth: 0, px: 0.75, py: 0, textTransform: 'none', borderColor: 'divider', '& .MuiButton-startIcon': { mr: 0.25 } }}
              >
                <Typography variant="caption">Lint</Typography>
              </Button>
            </Tooltip>
          </>
        }
      >
        {selectMode && (
          // Bulk-actions toolbar — a select-all checkbox, the running count and
          // a "delete selected" action. Only shown while select mode is on.
          <Box
            data-testid="expectation-bulk-toolbar"
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.5,
              px: 0.5,
              py: 0.25,
              borderBottom: 1,
              borderColor: 'divider',
              flexShrink: 0,
            }}
          >
            <Checkbox
              size="small"
              checked={allSelected}
              indeterminate={someSelected}
              disabled={selectableItems.length === 0}
              onChange={toggleSelectAll}
              slotProps={{ input: { 'aria-label': 'Select all expectations' } }}
              sx={{ p: 0.25 }}
            />
            <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>
              {effectiveSelected.length} selected
            </Typography>
            <Button
              size="small"
              color="error"
              variant="outlined"
              disabled={effectiveSelected.length === 0}
              startIcon={<DeleteOutlineIcon sx={{ fontSize: '1rem' }} />}
              onClick={() => setBulkDeleteConfirm(true)}
              sx={{ height: 24, py: 0, textTransform: 'none' }}
            >
              Delete selected
            </Button>
          </Box>
        )}
        {shown.length === 0 ? (
          expectations.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
              No active expectations —{' '}
              <Link component="button" type="button" onClick={() => setView('composer')} sx={{ verticalAlign: 'baseline' }}>
                add one in the Mocks composer
              </Link>
              , or{' '}
              <Link component="button" type="button" onClick={() => setOpenApiOpen(true)} sx={{ verticalAlign: 'baseline' }}>
                import an OpenAPI spec
              </Link>
              .
            </Typography>
          ) : (
            <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
              No matching expectations
            </Typography>
          )
        ) : (
          <ProgressiveList
            count={shown.length}
            getKey={(i) => shown[i]!.key}
            renderRow={(i) => {
              const item = shown[i]!;
              // Per-row actions are only meaningful for expectations that carry
              // an id (every Active Expectations row does); guard anyway so a
              // malformed row never offers a no-op delete.
              const hasId = expectationIdOf(item) !== null;
              return (
                <JsonListItemComponent
                  item={item}
                  index={i + 1}
                  turnPosition={turnPositions.get(item.key)}
                  expanded={expansion.isExpanded(item.key)}
                  onToggleExpand={expansion.toggle}
                  onTest={hasId ? handleTest : undefined}
                  onEdit={hasId ? handleEdit : undefined}
                  onDuplicate={hasId ? handleDuplicate : undefined}
                  onDelete={hasId ? setPendingDelete : undefined}
                  onSelectToggle={selectMode && hasId ? toggleSelectKey : undefined}
                  selected={selectMode && selectableKeys.has(item.key) && selectedKeys.has(item.key)}
                />
              );
            }}
          />
        )}
      </Panel>
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this expectation?"
        message={
          pendingDelete
            ? `Remove expectation ${expectationIdOf(pendingDelete) ?? ''} from the server. Recorded requests and logs are kept. This cannot be undone.`
            : ''
        }
        confirmLabel="Delete"
        onConfirm={() => { void handleConfirmDelete(); }}
        onClose={() => setPendingDelete(null)}
      />
      <ConfirmDialog
        open={bulkDeleteConfirm}
        title={`Delete ${effectiveSelected.length} expectation${effectiveSelected.length === 1 ? '' : 's'}?`}
        message={`Remove the ${effectiveSelected.length} selected expectation${effectiveSelected.length === 1 ? '' : 's'} from the server. Recorded requests and logs are kept. This cannot be undone.`}
        confirmLabel="Delete selected"
        onConfirm={() => { void handleBulkDelete(); }}
        onClose={() => setBulkDeleteConfirm(false)}
      />
      {/* Per-row "Test expectation" — dry-run what a registered expectation
          matches. Mounted only while open so it re-seeds from the chosen row. */}
      {testExpectation && (
        <MatcherPlaygroundDialog
          open
          onClose={() => setTestExpectation(null)}
          initialExpectation={JSON.stringify(testExpectation.value, null, 2)}
        />
      )}
      <OpenApiImportDialog
        open={openApiOpen}
        onClose={() => setOpenApiOpen(false)}
        connectionParams={params}
      />
      {lintOpen && <LintDialog open onClose={() => setLintOpen(false)} connectionParams={params} />}
    </>
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
export default memo(ExpectationPanel);
