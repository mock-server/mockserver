import { useCallback, useEffect, useMemo, useState } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import { humanizeError, type HumanError } from '../lib/errorMessage';
import { monospaceFontFamily } from '../theme';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import { fetchAuditEntries, type AuditEntry } from '../lib/audit';
import { getConfiguration, updateConfiguration, type Configuration } from '../lib/configuration';
import HumanErrorAlert from './HumanErrorAlert';

interface AuditPanelProps {
  connectionParams: ConnectionParams;
}

/**
 * The audit endpoint returns no JSON error envelope on 404, so a missing
 * endpoint surfaces as the status-line message or the humanized "isn't
 * available" copy. Detect both so the panel shows the "not available on an
 * older server" branch rather than a generic error.
 */
function isUnavailable(message: string): boolean {
  return (
    message.includes('404') ||
    message.includes('Not Found') ||
    message.includes('isn’t available')
  );
}

/** Colour the outcome chip: authorized = success, denied = warning/error. */
function outcomeColor(outcome: string): 'success' | 'warning' | 'error' | 'default' {
  const o = outcome.toUpperCase();
  if (o === 'AUTHORIZED' || o === 'ALLOWED') return 'success';
  if (o === 'FORBIDDEN') return 'error';
  if (o === 'UNAUTHENTICATED') return 'warning';
  return 'default';
}

function formatTime(epochTimeMs: number): string {
  if (!Number.isFinite(epochTimeMs)) return '';
  return new Date(epochTimeMs).toLocaleString();
}

/**
 * Case-insensitive substring match across the human-readable fields of an entry.
 * Audit entries are control-plane mutations, not request/response traffic, so the
 * traffic-oriented status:/method:/path: operators of OperatorSearchField don't
 * map cleanly here — a plain substring filter is the honest, trivially-applicable
 * option.
 */
function matchesSearch(entry: AuditEntry, needle: string): boolean {
  const haystack = [
    entry.method,
    entry.path,
    entry.operation,
    entry.sourceAddress,
    entry.principal ?? '',
    entry.principalSource ?? '',
    entry.outcome,
    entry.summary,
  ]
    .join(' ')
    .toLowerCase();
  return haystack.includes(needle);
}

export default function AuditPanel({ connectionParams }: AuditPanelProps) {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null);
  const [config, setConfig] = useState<Configuration | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [updateError, setUpdateError] = useState<HumanError | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [search, setSearch] = useState('');
  const [refreshTick, setRefreshTick] = useState(0);

  // Fetch on mount and whenever the user hits Refresh (refreshTick) — no polling
  // (the audit trail is a control-plane history, not live traffic; the user pulls
  // updates explicitly). The live configuration is fetched alongside the entries
  // so the On/Off status is accurate even when the list is empty. Both calls run
  // together via allSettled so a failure of one does not blank the other: a config
  // failure still lets the (possibly empty) list render, and an audit-list failure
  // still lets the status chip render. State is only ever set after the awaited
  // call resolves, never synchronously inside the effect.
  useEffect(() => {
    const controller = new AbortController();
    async function load(): Promise<void> {
      const [cfgResult, entriesResult] = await Promise.allSettled([
        getConfiguration(connectionParams, controller.signal),
        fetchAuditEntries(connectionParams, { signal: controller.signal }),
      ]);
      if (controller.signal.aborted) return;
      if (cfgResult.status === 'fulfilled') {
        setConfig(cfgResult.value);
      }
      if (entriesResult.status === 'fulfilled') {
        setEntries(entriesResult.value);
        setLoadError(null);
      } else {
        setLoadError(humanizeError(entriesResult.reason).message);
      }
      setLoading(false);
    }
    void load();
    return () => controller.abort();
  }, [connectionParams, refreshTick]);

  // Loading is toggled here (an event handler, not the effect) so the refresh
  // spinner shows without a synchronous setState inside the effect.
  const refresh = useCallback(() => {
    setLoading(true);
    setRefreshTick((t) => t + 1);
  }, []);

  // Apply a single runtime-config change (only controlPlaneAuditReads: the audit
  // trail itself is fixed at startup) and, on success, refetch config + entries.
  // A failed PUT (e.g. control-plane auth rejecting the write) surfaces via the
  // HumanErrorAlert rather than silently — mirrors ConfigurationDialog's apply().
  const applyConfig = useCallback(
    async (partial: Configuration): Promise<void> => {
      setBusy(true);
      setUpdateError(null);
      try {
        await updateConfiguration(connectionParams, partial);
        refresh();
      } catch (e) {
        setUpdateError(humanizeError(e));
      } finally {
        setBusy(false);
      }
    },
    [connectionParams, refresh],
  );

  const auditEnabled = config?.['controlPlaneAuditEnabled'] === true;
  const auditReads = config?.['controlPlaneAuditReads'] === true;
  const configLoaded = config != null;

  const needle = search.trim().toLowerCase();
  const filtered = useMemo(() => {
    const all = entries ?? [];
    if (!needle) return all;
    return all.filter((e) => matchesSearch(e, needle));
  }, [entries, needle]);

  return (
    <Box sx={{ flex: 1, overflow: 'auto', p: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5, flexWrap: 'wrap' }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
          Audit Trail
        </Typography>
        {configLoaded && (
          <Chip
            size="small"
            label={auditEnabled ? 'Audit Trail: On' : 'Audit Trail: Off'}
            color={auditEnabled ? 'success' : 'default'}
            variant={auditEnabled ? 'filled' : 'outlined'}
          />
        )}
        {entries && (
          <Chip
            size="small"
            label={`${entries.length} ${entries.length === 1 ? 'entry' : 'entries'}`}
            variant="outlined"
          />
        )}
        <Box sx={{ flex: 1 }} />
        <TextField
          size="small"
          placeholder="Search audit entries"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          slotProps={{
            htmlInput: { 'aria-label': 'Search audit entries' },
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
          sx={{ maxWidth: 260, '& .MuiInputBase-root': { height: 28 } }}
        />
        <Button
          size="small"
          onClick={refresh}
          disabled={loading}
          startIcon={<RefreshIcon fontSize="small" />}
          sx={{ textTransform: 'none' }}
        >
          Refresh
        </Button>
      </Box>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        The most recent control-plane changes to this server — mutations to expectations,
        configuration, and server state (who changed what, and whether it was allowed). This is a
        separate trail from the data-plane event log of received requests and responses. Newest
        first. Request headers and bodies are never recorded.
      </Typography>

      {configLoaded && (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 2,
            mb: 1.5,
            flexWrap: 'wrap',
          }}
        >
          {!auditEnabled && (
            <Typography variant="body2" color="text.secondary">
              The audit trail is off. It can only be switched on when MockServer starts, so a
              control-plane request cannot switch it off to hide the changes that follow.
            </Typography>
          )}
          <Tooltip
            title="Also record read operations (e.g. GET /mockserver/expectation), not just mutations."
            arrow
          >
            <FormControlLabel
              control={
                <Checkbox
                  size="small"
                  checked={auditReads}
                  disabled={busy || !auditEnabled}
                  onChange={(e) => void applyConfig({ controlPlaneAuditReads: e.target.checked })}
                  slotProps={{ input: { 'aria-label': 'Also record reads' } }}
                />
              }
              label={<Typography variant="body2">Also record reads</Typography>}
            />
          </Tooltip>
        </Box>
      )}

      {updateError && <HumanErrorAlert error={updateError} sx={{ mb: 1.5 }} />}

      {auditEnabled && (
        <Alert severity="info" sx={{ mb: 1.5 }}>
          Recording is on. The most recent control-plane changes since the server started appear
          here; a reset clears them.
        </Alert>
      )}

      {loadError && (
        <Alert
          severity={isUnavailable(loadError) ? 'info' : 'error'}
          sx={{ mb: 1.5 }}
          action={
            <IconButton color="inherit" size="small" onClick={refresh} aria-label="Retry">
              <RefreshIcon fontSize="small" />
            </IconButton>
          }
        >
          <AlertTitle>
            {isUnavailable(loadError) ? 'Audit trail not available' : 'Could not load audit trail'}
          </AlertTitle>
          {isUnavailable(loadError)
            ? 'The connected server does not expose an audit trail. This feature requires a newer version of MockServer.'
            : loadError}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 0 }}>
        {filtered.length === 0 ? (
          entries != null && entries.length === 0 ? (
            // Genuinely-empty case. The server records entries only when the
            // audit trail is switched on (controlPlaneAuditEnabled, off by
            // default) — recording is independent of authentication. It is fixed
            // at startup, so when it is off the empty state says how to start the
            // server with it; when it is on it explains what will appear.
            <Box sx={{ p: 2 }}>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                No audit entries recorded.
              </Typography>
              {auditEnabled ? (
                <Typography variant="body2" color="text.secondary">
                  Recording is on — make a control-plane change (register or clear an expectation,
                  reset, or change configuration) and it will appear here.
                </Typography>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  The control-plane audit trail is off by default. To record control-plane
                  mutations — changes to expectations, configuration, and server state — here, start
                  MockServer with{' '}
                  <Box component="code" sx={{ fontFamily: monospaceFontFamily }}>
                    controlPlaneAuditEnabled=true
                  </Box>{' '}
                  (system property{' '}
                  <Box component="code" sx={{ fontFamily: monospaceFontFamily }}>
                    -Dmockserver.controlPlaneAuditEnabled=true
                  </Box>{' '}
                  or environment variable{' '}
                  <Box component="code" sx={{ fontFamily: monospaceFontFamily }}>
                    MOCKSERVER_CONTROL_PLANE_AUDIT_ENABLED=true
                  </Box>
                  , plus{' '}
                  <Box component="code" sx={{ fontFamily: monospaceFontFamily }}>
                    controlPlaneAuditReads=true
                  </Box>{' '}
                  to include reads).
                </Typography>
              )}
            </Box>
          ) : (
            <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
              {entries == null ? 'Loading audit trail…' : 'No entries match your search.'}
            </Typography>
          )
        ) : (
          <TableContainer>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>Time</TableCell>
                  <TableCell>Operation</TableCell>
                  <TableCell>Method</TableCell>
                  <TableCell>Path</TableCell>
                  <TableCell>Source</TableCell>
                  <TableCell>Principal</TableCell>
                  <TableCell>Outcome</TableCell>
                  <TableCell>Summary</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((entry, i) => (
                  <TableRow key={`${entry.epochTimeMs}-${i}`}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily }}>
                        {formatTime(entry.epochTimeMs)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily }}>
                        {entry.operation}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily }}>
                        {entry.method}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, wordBreak: 'break-all' }}>
                        {entry.path}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily }}>
                        {entry.sourceAddress}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {entry.principal ? (
                        <Tooltip title={entry.principalSource ? `via ${entry.principalSource}` : ''}>
                          <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily }}>
                            {entry.principal}
                          </Typography>
                        </Tooltip>
                      ) : (
                        <Typography variant="caption" color="text.secondary">
                          —
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={entry.outcome}
                        color={outcomeColor(entry.outcome)}
                        variant="outlined"
                        sx={{ height: 20, fontSize: '0.65rem' }}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption">{entry.summary}</Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>
    </Box>
  );
}
