import { useState, useEffect, useCallback, useMemo } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import FormControlLabel from '@mui/material/FormControlLabel';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Chip from '@mui/material/Chip';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import { useDashboardStore } from '../store';
import { humanizeError, type HumanError } from '../lib/errorMessage';
import { monospaceFontFamily } from '../theme';
import HumanErrorAlert from './HumanErrorAlert';
import CopyButton from './CopyButton';
import {
  getConfiguration,
  updateConfiguration,
  getEffectiveConfiguration,
  getServerStatus,
  getProxyConfiguration,
  bindAdditionalPort,
  CONFIG_SOURCE_LABELS,
  LOG_LEVELS,
  EDITABLE_PROPERTIES,
  type Configuration,
  type EditablePropertyDescriptor,
  type EffectiveConfigProperty,
  type ServerStatus,
  type ProxyConfiguration,
} from '../lib/configuration';

function valueToText(v: unknown): string {
  if (v == null) return '';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

/** Group the descriptor list by `group` field, preserving insertion order. */
function groupDescriptors(descriptors: readonly EditablePropertyDescriptor[]): Map<string, EditablePropertyDescriptor[]> {
  const groups = new Map<string, EditablePropertyDescriptor[]>();
  for (const d of descriptors) {
    const list = groups.get(d.group);
    if (list) {
      list.push(d);
    } else {
      groups.set(d.group, [d]);
    }
  }
  return groups;
}

export default function ConfigurationDialog({
  open,
  onClose,
  connectionParams,
}: {
  open: boolean;
  onClose: () => void;
  connectionParams: ConnectionParams;
}) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const [config, setConfig] = useState<Configuration | null>(null);
  const [error, setError] = useState<HumanError | null>(null);
  const [busy, setBusy] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);

  // Server Info tab (read-only): effective configuration with source tiers and
  // the bound ports. Fetched lazily when the tab is first opened.
  const [tab, setTab] = useState<'settings' | 'info'>('settings');
  const [effectiveConfig, setEffectiveConfig] = useState<EffectiveConfigProperty[] | null>(null);
  const [serverStatus, setServerStatus] = useState<ServerStatus | null>(null);
  const [infoError, setInfoError] = useState<HumanError | null>(null);
  // Proxy setup is fetched independently so a proxy failure does not hide the
  // effective-config / bound-ports panel.
  const [proxyConfig, setProxyConfig] = useState<ProxyConfiguration | null>(null);
  const [proxyError, setProxyError] = useState<HumanError | null>(null);

  const refresh = useCallback(() => setRefreshTick((t) => t + 1), []);

  // Reset to the Settings tab and drop any loaded Server Info each time the
  // dialog transitions to open, so every open starts clean and re-fetches. This
  // uses the "adjust state during render on a prop change" pattern (React's
  // endorsed alternative to a setState-in-effect) rather than an effect.
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) {
      setTab('settings');
      setEffectiveConfig(null);
      setServerStatus(null);
      setInfoError(null);
      setProxyConfig(null);
      setProxyError(null);
    }
  }

  // Fetch the effective configuration + bound ports when the Server Info tab is
  // opened (once per open — the data is read-only). Both calls run together.
  useEffect(() => {
    if (!open || tab !== 'info' || effectiveConfig !== null) return;
    let cancelled = false;
    const controller = new AbortController();
    async function loadInfo(): Promise<void> {
      try {
        const [cfg, status] = await Promise.all([
          getEffectiveConfiguration(connectionParams, controller.signal),
          getServerStatus(connectionParams, controller.signal),
        ]);
        if (cancelled) return;
        setEffectiveConfig(cfg);
        setServerStatus(status);
        setInfoError(null);
      } catch (e) {
        if (!cancelled) setInfoError(humanizeError(e));
      }
    }
    void loadInfo();
    // Proxy setup — best-effort, own error surface (a proxy failure must not
    // hide the effective-config / bound-ports panel).
    getProxyConfiguration(connectionParams, controller.signal)
      .then((proxy) => { if (!cancelled) { setProxyConfig(proxy); setProxyError(null); } })
      .catch((e) => { if (!cancelled) setProxyError(humanizeError(e)); });
    return () => { cancelled = true; controller.abort(); };
  }, [open, tab, effectiveConfig, connectionParams]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    async function load(): Promise<void> {
      try {
        const next = await getConfiguration(connectionParams);
        if (cancelled) return;
        setConfig(next);
        useDashboardStore.getState().setServerConfiguration(next);
        setError(null);
      } catch (e) {
        if (!cancelled) setError(humanizeError(e));
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [open, connectionParams, refreshTick]);

  const apply = useCallback(async (partial: Configuration) => {
    setBusy(true);
    setError(null);
    try {
      await updateConfiguration(connectionParams, partial);
      refresh();
    } catch (e) {
      setError(humanizeError(e));
    } finally {
      setBusy(false);
    }
  }, [connectionParams, refresh]);

  const logLevel = typeof config?.['logLevel'] === 'string' ? (config['logLevel'] as string) : 'INFO';
  const detailed = config?.['detailedMatchFailures'] === true;
  const metrics = config?.['metricsEnabled'] === true;

  const editableKeys = useMemo(() => new Set(EDITABLE_PROPERTIES.map((d) => d.key)), []);
  const grouped = useMemo(() => groupDescriptors(EDITABLE_PROPERTIES), []);

  // Read-only table excludes the three bespoke controls AND all descriptor-driven keys
  const entries = config
    ? Object.entries(config).filter(
        ([k, v]) =>
          v != null &&
          valueToText(v) !== '' &&
          !editableKeys.has(k) &&
          k !== 'logLevel' &&
          k !== 'detailedMatchFailures' &&
          k !== 'metricsEnabled',
      )
    : [];

  const disabled = busy || !config;

  const handleClose = useCallback(() => {
    setError(null);
    onClose();
  }, [onClose]);

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth fullScreen={fullScreen} aria-labelledby="configuration-dialog-title">
      <DialogTitle id="configuration-dialog-title">Server Configuration</DialogTitle>
      <DialogContent>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v as 'settings' | 'info')}
          sx={{ mb: 1.5, minHeight: 36 }}
        >
          <Tab value="settings" label="Settings" sx={{ minHeight: 36, textTransform: 'none' }} />
          <Tab value="info" label="Server Info" sx={{ minHeight: 36, textTransform: 'none' }} />
        </Tabs>

        {tab === 'settings' && (
          <>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Inspect the running server configuration and change common runtime settings. Changes apply
          immediately to this server.
        </Typography>
        {error && <HumanErrorAlert error={error} sx={{ mb: 1.5 }} />}

        {/* --- Original bespoke controls --- */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1, flexWrap: 'wrap' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2">Log level</Typography>
            <Select size="small" value={logLevel} disabled={disabled}
              onChange={(e) => void apply({ logLevel: e.target.value })} sx={{ width: 120 }}>
              {LOG_LEVELS.map((l) => <MenuItem key={l} value={l}>{l}</MenuItem>)}
            </Select>
          </Box>
          <FormControlLabel
            control={<Switch size="small" checked={detailed} disabled={disabled}
              onChange={(e) => void apply({ detailedMatchFailures: e.target.checked })} />}
            label={<Typography variant="body2">Detailed match failures</Typography>}
          />
          <FormControlLabel
            control={<Switch size="small" checked={metrics} disabled={disabled}
              onChange={(e) => void apply({ metricsEnabled: e.target.checked })} />}
            label={<Typography variant="body2">Metrics enabled</Typography>}
          />
        </Box>

        {/* --- Descriptor-driven editable controls (grouped) --- */}
        {Array.from(grouped.entries()).map(([group, descriptors]) => (
          <Box key={group} sx={{ mt: 2 }}>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5, fontWeight: 600 }}>
              {group}
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
              {descriptors.map((d) => (
                <EditableControl
                  key={d.key}
                  descriptor={d}
                  value={config?.[d.key]}
                  disabled={disabled}
                  onApply={apply}
                />
              ))}
            </Box>
          </Box>
        ))}

        {/* --- Read-only table for everything else --- */}
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2, mb: 0.5 }}>
          All other settings (read-only)
        </Typography>
        <Box sx={{ maxHeight: 320, overflow: 'auto', border: 1, borderColor: 'divider', borderRadius: 1 }}>
          <Table size="small" stickyHeader>
            <TableBody>
              {entries.map(([k, v]) => (
                <TableRow key={k}>
                  <TableCell sx={{ typography: 'subtitle2', fontWeight: 400, fontFamily: monospaceFontFamily, width: '45%', verticalAlign: 'top' }}>{k}</TableCell>
                  <TableCell sx={{ typography: 'subtitle2', fontWeight: 400, fontFamily: monospaceFontFamily, wordBreak: 'break-all' }}>{valueToText(v)}</TableCell>
                </TableRow>
              ))}
              {entries.length === 0 && (
                <TableRow><TableCell colSpan={2}><Typography variant="body2" color="text.secondary">No configuration loaded.</Typography></TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        </Box>
          </>
        )}

        {tab === 'info' && (
          <ServerInfoPanel
            effectiveConfig={effectiveConfig}
            serverStatus={serverStatus}
            error={infoError}
            proxyConfig={proxyConfig}
            proxyError={proxyError}
            connectionParams={connectionParams}
            onPortsBound={(ports) => setServerStatus((s) => (s ? { ...s, ports } : { ports }))}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Server Info tab — read-only effective configuration + bound ports
// ---------------------------------------------------------------------------

/** Preferred display order of the source tiers (ignored keys, then most specific override first). */
const SOURCE_ORDER = [
  'unrecognised',
  'runtime-set',
  'system-property',
  'environment-variable',
  'properties-file',
  'default',
] as const;

/** Group the effective config by source tier, in SOURCE_ORDER (unknowns last). */
function groupBySource(props: EffectiveConfigProperty[]): [string, EffectiveConfigProperty[]][] {
  const bySource = new Map<string, EffectiveConfigProperty[]>();
  for (const p of props) {
    const list = bySource.get(p.source);
    if (list) list.push(p);
    else bySource.set(p.source, [p]);
  }
  const ordered: [string, EffectiveConfigProperty[]][] = [];
  for (const source of SOURCE_ORDER) {
    const list = bySource.get(source);
    if (list) { ordered.push([source, list]); bySource.delete(source); }
  }
  // Any source not in the known order (forward-compat) appended in insertion order.
  for (const [source, list] of bySource) ordered.push([source, list]);
  return ordered;
}

function ServerInfoPanel({
  effectiveConfig,
  serverStatus,
  error,
  proxyConfig,
  proxyError,
  connectionParams,
  onPortsBound,
}: {
  effectiveConfig: EffectiveConfigProperty[] | null;
  serverStatus: ServerStatus | null;
  error: HumanError | null;
  proxyConfig: ProxyConfiguration | null;
  proxyError: HumanError | null;
  connectionParams: ConnectionParams;
  onPortsBound: (ports: number[]) => void;
}) {
  const grouped = useMemo(
    () => (effectiveConfig ? groupBySource(effectiveConfig) : []),
    [effectiveConfig],
  );

  if (error) {
    return <HumanErrorAlert error={error} sx={{ mb: 1.5 }} />;
  }

  if (effectiveConfig === null) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
        Loading server info…
      </Typography>
    );
  }

  return (
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        The effective configuration this server resolved at startup, with the source of each value.
        Sensitive values are redacted by the server. This view is read-only.
      </Typography>

      {/* Bound ports + build info */}
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5, fontWeight: 600 }}>
        Bound ports
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap', mb: 0.5 }}>
        {serverStatus && serverStatus.ports.length > 0 ? (
          serverStatus.ports.map((port) => (
            <Chip key={port} size="small" label={String(port)} variant="outlined" sx={{ fontFamily: monospaceFontFamily }} />
          ))
        ) : (
          <Typography variant="body2" color="text.secondary">No bound ports reported.</Typography>
        )}
      </Box>
      <BindPortControl connectionParams={connectionParams} onPortsBound={onPortsBound} />
      {serverStatus?.version && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, mb: 1.5 }}>
          Version {serverStatus.version}
          {serverStatus.gitHash ? ` (${serverStatus.gitHash})` : ''}
        </Typography>
      )}

      {/* Proxy setup */}
      <ProxySetupSection proxyConfig={proxyConfig} proxyError={proxyError} />

      {/* Effective configuration grouped by source tier */}
      {grouped.map(([source, props]) => (
        <Box key={source} sx={{ mt: 1.5 }}>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5, fontWeight: 600 }}>
            {CONFIG_SOURCE_LABELS[source] ?? source} ({props.length})
          </Typography>
          <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
            <Table size="small">
              <TableBody>
                {props.map((p) => (
                  <TableRow key={p.name}>
                    <TableCell sx={{ typography: 'subtitle2', fontWeight: 400, fontFamily: monospaceFontFamily, width: '55%', verticalAlign: 'top' }}>
                      {p.name}
                    </TableCell>
                    <TableCell sx={{ typography: 'subtitle2', fontWeight: 400, fontFamily: monospaceFontFamily, wordBreak: 'break-all' }}>
                      {p.value}
                      {p.suggestions && p.suggestions.length > 0 && ` did you mean ${p.suggestions.join(' or ')}?`}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        </Box>
      ))}
      {grouped.length === 0 && (
        <Typography variant="body2" color="text.secondary">No configuration reported.</Typography>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Bind additional port
// ---------------------------------------------------------------------------

function BindPortControl({
  connectionParams,
  onPortsBound,
}: {
  connectionParams: ConnectionParams;
  onPortsBound: (ports: number[]) => void;
}) {
  const [port, setPort] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<HumanError | null>(null);

  const bind = useCallback(async () => {
    const parsed = Number(port);
    if (!Number.isInteger(parsed) || parsed < 0 || parsed > 65535) {
      setError({ message: 'Enter a valid port number (0–65535).' });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const ports = await bindAdditionalPort(connectionParams, parsed);
      onPortsBound(ports);
      setPort('');
    } catch (e) {
      setError(humanizeError(e));
    } finally {
      setBusy(false);
    }
  }, [port, connectionParams, onPortsBound]);

  return (
    <Box sx={{ mt: 0.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <TextField
          size="small"
          type="number"
          label="Bind additional port"
          value={port}
          disabled={busy}
          sx={{ width: 200 }}
          slotProps={{ htmlInput: { min: 0, max: 65535 } }}
          onChange={(e) => setPort(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') void bind(); }}
        />
        <Button size="small" variant="outlined" disabled={busy || port.trim() === ''} onClick={() => void bind()}>
          Bind
        </Button>
      </Box>
      {error && <HumanErrorAlert error={error} sx={{ mt: 1 }} />}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Proxy setup section
// ---------------------------------------------------------------------------

function ProxySetupSection({
  proxyConfig,
  proxyError,
}: {
  proxyConfig: ProxyConfiguration | null;
  proxyError: HumanError | null;
}) {
  const downloadCa = useCallback(() => {
    if (!proxyConfig?.caCertificatePem) return;
    const blob = new Blob([proxyConfig.caCertificatePem], { type: 'application/x-pem-file' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'mockserver-ca.pem';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, [proxyConfig]);

  return (
    <Box sx={{ mt: 1.5 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5, fontWeight: 600 }}>
        Proxy setup
      </Typography>
      {proxyError && <HumanErrorAlert error={proxyError} sx={{ mb: 1 }} />}
      {!proxyError && !proxyConfig && (
        <Typography variant="body2" color="text.secondary">Loading proxy setup…</Typography>
      )}
      {proxyConfig && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {proxyConfig.warning && <HumanErrorAlert message={proxyConfig.warning} severity="warning" />}

          {/* HTTPS proxy URL */}
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
              HTTPS proxy URL
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <Typography variant="body2" sx={{ fontFamily: monospaceFontFamily, wordBreak: 'break-all' }}>
                {proxyConfig.httpsProxy || '(unavailable)'}
              </Typography>
              {proxyConfig.httpsProxy && <CopyButton text={proxyConfig.httpsProxy} />}
            </Box>
          </Box>

          {/* Environment-variable snippets */}
          {([
            ['Unix / macOS shell', proxyConfig.environmentVariables.unix],
            ['PowerShell', proxyConfig.environmentVariables.powershell],
          ] as const).filter(([, snippet]) => snippet).map(([label, snippet]) => (
            <Box key={label}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <Typography variant="caption" color="text.secondary">{label}</Typography>
                <CopyButton text={snippet} />
              </Box>
              <Box
                component="pre"
                sx={{
                  m: 0, p: 1, borderRadius: 1, border: 1, borderColor: 'divider',
                  fontFamily: monospaceFontFamily, fontSize: '0.72rem',
                  overflowX: 'auto', whiteSpace: 'pre', bgcolor: 'action.hover',
                }}
              >
                {snippet}
              </Box>
            </Box>
          ))}

          {/* CA certificate download */}
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
              CA certificate {proxyConfig.usingDefaultCa ? '(built-in default)' : '(custom)'}
            </Typography>
            <Button size="small" variant="outlined" disabled={!proxyConfig.caCertificatePem} onClick={downloadCa}>
              Download CA (mockserver-ca.pem)
            </Button>
          </Box>
        </Box>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Generic editable-control renderer driven by a single descriptor
// ---------------------------------------------------------------------------

function EditableControl({
  descriptor,
  value,
  disabled,
  onApply,
}: {
  descriptor: EditablePropertyDescriptor;
  value: unknown;
  disabled: boolean;
  onApply: (partial: Configuration) => Promise<void>;
}) {
  const { key, label, type, help } = descriptor;

  if (type === 'boolean') {
    const checked = value === true;
    return (
      <Tooltip title={help} arrow>
        <FormControlLabel
          control={
            <Switch
              size="small"
              checked={checked}
              disabled={disabled}
              onChange={(e) => void onApply({ [key]: e.target.checked })}
            />
          }
          label={<Typography variant="body2">{label}</Typography>}
        />
      </Tooltip>
    );
  }

  if (type === 'number') {
    return <NumberField configKey={key} label={label} help={help} value={value} disabled={disabled} onApply={onApply} />;
  }

  // type === 'string'
  return <StringField configKey={key} label={label} help={help} value={value} disabled={disabled} onApply={onApply} />;
}

/**
 * Number field with local draft state.  Resets from the server value when the
 * prop changes (using the "store previous props" pattern — no useEffect needed).
 */
function NumberField({
  configKey, label, help, value, disabled, onApply,
}: {
  configKey: string; label: string; help: string; value: unknown;
  disabled: boolean; onApply: (partial: Configuration) => Promise<void>;
}) {
  const serverValue = typeof value === 'number' ? String(value) : '';
  const [prev, setPrev] = useState(serverValue);
  const [draft, setDraft] = useState(serverValue);

  // Reset draft when the server value changes (after a PUT + refresh).
  if (serverValue !== prev) {
    setPrev(serverValue);
    setDraft(serverValue);
  }

  const commit = useCallback(() => {
    if (draft.trim() === '') return;          // empty → don't PUT (Number('') === 0)
    const parsed = Number(draft);
    if (!Number.isNaN(parsed)) {
      void onApply({ [configKey]: parsed });
    }
  }, [draft, configKey, onApply]);

  return (
    <Tooltip title={help} arrow>
      <TextField
        size="small"
        type="number"
        label={label}
        value={draft}
        disabled={disabled}
        sx={{ width: 160 }}
        slotProps={{ htmlInput: { min: 0 } }}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => { if (e.key === 'Enter') commit(); }}
      />
    </Tooltip>
  );
}

/**
 * String text field with local draft state (same pattern as NumberField).
 */
function StringField({
  configKey, label, help, value, disabled, onApply,
}: {
  configKey: string; label: string; help: string; value: unknown;
  disabled: boolean; onApply: (partial: Configuration) => Promise<void>;
}) {
  const serverValue = typeof value === 'string' ? value : '';
  const [prev, setPrev] = useState(serverValue);
  const [draft, setDraft] = useState(serverValue);

  if (serverValue !== prev) {
    setPrev(serverValue);
    setDraft(serverValue);
  }

  const commit = useCallback(() => {
    void onApply({ [configKey]: draft });
  }, [draft, configKey, onApply]);

  return (
    <Tooltip title={help} arrow>
      <TextField
        size="small"
        label={label}
        value={draft}
        disabled={disabled}
        sx={{ minWidth: 240, flexGrow: 1 }}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => { if (e.key === 'Enter') commit(); }}
      />
    </Tooltip>
  );
}
