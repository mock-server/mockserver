import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import Typography from '@mui/material/Typography';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Alert from '@mui/material/Alert';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { monospaceFontFamily } from '../theme';
import { useDashboardStore } from '../store';
import type { ParsedTraffic } from '../lib/llmTraffic';
import {
  PROVIDERS,
  MATCHER_PRECISIONS,
  extractExpectationFromCapture,
  type ExpectationDraft,
  type LlmExpectationDraft,
  type GenericExpectationDraft,
  type ProviderName,
  type ToolCallDraft,
  type MatcherPrecision,
} from '../lib/expectationFromCapture';
import {
  expectationToJson,
  expectationToJava,
  expectationToMcpArgs,
  expectationToJsonObject,
} from '../lib/llmExpectationCodegen';
import { callMcpTool, buildBaseUrl } from '../lib/mcpClient';
import { humanizeError, type HumanError } from '../lib/errorMessage';
import { lintWarningCount } from '../lib/lint';
import CopyButton from './CopyButton';
import HumanErrorAlert from './HumanErrorAlert';
import JsonDiffViewer from './JsonDiffViewerLazy';
import LintWarningsBadge from './LintWarningsBadge';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface CaptureAsMockDialogProps {
  open: boolean;
  onClose: () => void;
  parsed: ParsedTraffic;
  path: string;
  connectionParams: { host: string; port: string; secure: boolean };
  /** The raw item value — needed for generic HTTP extraction. */
  itemValue?: Record<string, unknown>;
}

// ---------------------------------------------------------------------------
// Matcher precision labels
// ---------------------------------------------------------------------------

const PRECISION_LABELS: Record<MatcherPrecision, string> = {
  exact: 'Exact',
  moderate: 'Moderate',
  loose: 'Loose',
};

const PRECISION_DESCRIPTIONS: Record<MatcherPrecision, string> = {
  exact: 'Method + Path + Query + Headers + Body',
  moderate: 'Method + Path + Query + Body',
  loose: 'Method + Path only',
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export default function CaptureAsMockDialog({
  open,
  onClose,
  parsed,
  path,
  connectionParams,
  itemValue,
}: CaptureAsMockDialogProps) {
  // Extract default draft from the capture
  const defaultDraft = useMemo(
    () => extractExpectationFromCapture(parsed, path, itemValue),
    [parsed, path, itemValue],
  );

  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const setNotification = useDashboardStore((s) => s.setNotification);
  const editExpectation = useDashboardStore((s) => s.editExpectation);

  // Editable state
  const [draft, setDraft] = useState<ExpectationDraft>(defaultDraft);
  const [tab, setTab] = useState(0);
  const [registering, setRegistering] = useState(false);
  const [error, setError] = useState<HumanError | null>(null);
  // Lint warnings the server reported for the registered mock; non-zero keeps the dialog open to show them.
  const [registeredLintWarnings, setRegisteredLintWarnings] = useState(0);
  const registered = registeredLintWarnings > 0;
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (registered) closeButtonRef.current?.focus();
  }, [registered]);

  // Reset all transient state (draft edits, error/details, registering flag,
  // snackbar) whenever the captured path changes OR the dialog transitions from
  // closed to open. Resetting on the open transition is what makes reopening the
  // SAME captured item start clean: without it, a failed register would leave
  // the stale error Alert and prior edits visible the next time it opened.
  // This is the React-idiomatic "reset state on prop change" pattern (a
  // synchronous setState during render), not an effect, so no cascading render.
  const [prevPath, setPrevPath] = useState(path);
  const [prevOpen, setPrevOpen] = useState(open);
  if (path !== prevPath || (open && !prevOpen)) {
    setPrevPath(path);
    setPrevOpen(open);
    setDraft(defaultDraft);
    setTab(0);
    setError(null);
    setRegistering(false);
    setRegisteredLintWarnings(0);
  } else if (open !== prevOpen) {
    setPrevOpen(open);
  }

  // ---- LLM draft helpers ----
  const updateLlmDraft = useCallback(
    (partial: Partial<LlmExpectationDraft>) => setDraft((d) => {
      if (d.kind !== 'llm') return d;
      return { ...d, ...partial };
    }),
    [],
  );

  const updateToolCall = useCallback(
    (index: number, partial: Partial<ToolCallDraft>) =>
      setDraft((d) => {
        if (d.kind !== 'llm') return d;
        return {
          ...d,
          toolCalls: d.toolCalls.map((tc, i) => (i === index ? { ...tc, ...partial } : tc)),
        };
      }),
    [],
  );

  const addToolCall = useCallback(
    () =>
      setDraft((d) => {
        if (d.kind !== 'llm') return d;
        return {
          ...d,
          toolCalls: [...d.toolCalls, { name: '', arguments: '' }],
        };
      }),
    [],
  );

  const removeToolCall = useCallback(
    (index: number) =>
      setDraft((d) => {
        if (d.kind !== 'llm') return d;
        return {
          ...d,
          toolCalls: d.toolCalls.filter((_, i) => i !== index),
        };
      }),
    [],
  );

  // ---- Generic draft helpers ----
  const updateGenericDraft = useCallback(
    (partial: Partial<GenericExpectationDraft>) => setDraft((d) => {
      if (d.kind !== 'generic') return d;
      return { ...d, ...partial };
    }),
    [],
  );

  // Codegen outputs
  const jsonOutput = useMemo(() => expectationToJson(draft), [draft]);
  const javaOutput = useMemo(() => expectationToJava(draft), [draft]);

  // Register handler
  const handleRegister = useCallback(async () => {
    setRegistering(true);
    setError(null);
    try {
      const baseUrl = buildBaseUrl(connectionParams);

      if (draft.kind === 'llm') {
        // LLM: use MCP tool
        const args = expectationToMcpArgs(draft);
        const result = await callMcpTool(baseUrl, 'mock_llm_completion', args);
        if (result.ok) {
          setNotification({ message: 'Mock expectation registered successfully', severity: 'success' });
          onClose();
        } else {
          const raw =
            typeof result.error === 'string'
              ? result.error
              : JSON.stringify(result.error, null, 2);
          setError(humanizeError(new Error(raw)));
        }
      } else {
        // Generic HTTP: use PUT /mockserver/expectation
        const body = JSON.stringify(expectationToJsonObject(draft));
        const response = await fetch(`${baseUrl}/mockserver/expectation`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body,
        });
        if (response.ok) {
          const lintWarnings = lintWarningCount(response);
          if (lintWarnings > 0) {
            setRegisteredLintWarnings(lintWarnings);
          } else {
            setNotification({ message: 'Mock expectation registered successfully', severity: 'success' });
            onClose();
          }
        } else {
          const text = await response.text();
          // Use the `MockServer returned <status>: <body>` shape so humanizeError
          // can map the status to a friendly message and keep the raw body.
          setError(humanizeError(new Error(`MockServer returned ${response.status}: ${text}`)));
        }
      }
    } catch (err) {
      setError(humanizeError(err));
    } finally {
      setRegistering(false);
    }
  }, [connectionParams, draft, onClose, setNotification]);

  // Hand the captured expectation off to the Composer for refinement instead of
  // registering it directly here — this makes Capture and Composer one coherent
  // creation flow rather than two divergent engines. Only the generic HTTP draft
  // maps cleanly onto the Composer's expectation form.
  const handleRefineInComposer = useCallback(() => {
    if (draft.kind !== 'generic') return;
    editExpectation(expectationToJsonObject(draft) as Record<string, unknown>);
    onClose();
  }, [draft, editExpectation, onClose]);

  // Determine if Register button should be disabled
  const registerDisabled = useMemo(() => {
    if (registering || registered) return true;
    if (draft.kind === 'llm') return !draft.path || !draft.provider;
    return !draft.path;
  }, [registering, registered, draft]);

  const tabLabels = ['Edit', 'Preview Diff', 'Copy as JSON', 'Copy as Java'];

  return (
    <>
      <Dialog
        open={open}
        onClose={onClose}
        maxWidth="md"
        fullWidth
        fullScreen={fullScreen}
        aria-labelledby="capture-as-mock-title"
      >
        <DialogTitle id="capture-as-mock-title">Capture as Mock</DialogTitle>
        <DialogContent dividers>
          {registered && (
            <Alert severity="success" sx={{ mb: 2 }} data-testid="capture-registered">
              Mock expectation registered successfully.
              <Box sx={{ mt: 1 }}>
                <LintWarningsBadge count={registeredLintWarnings} connectionParams={connectionParams} />
              </Box>
            </Alert>
          )}
          <Tabs
            value={tab}
            onChange={(_, v: number) => setTab(v)}
            sx={{ mb: 2, minHeight: 32, '& .MuiTab-root': { minHeight: 32, py: 0.5, typography: 'body2' } }}
          >
            {tabLabels.map((label) => (
              <Tab key={label} label={label} />
            ))}
          </Tabs>

          {/* Tab 0: Editable fields — LLM mode */}
          {tab === 0 && draft.kind === 'llm' && (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <TextField
                label="Path"
                size="small"
                fullWidth
                value={draft.path}
                onChange={(e) => updateLlmDraft({ path: e.target.value })}
              />
              <TextField
                label="Provider"
                size="small"
                select
                fullWidth
                value={draft.provider}
                onChange={(e) => updateLlmDraft({ provider: e.target.value as ProviderName })}
              >
                {PROVIDERS.map((p) => (
                  <MenuItem key={p} value={p}>
                    {p}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label="Model"
                size="small"
                fullWidth
                value={draft.model}
                onChange={(e) => updateLlmDraft({ model: e.target.value })}
              />
              <TextField
                label="Text"
                size="small"
                fullWidth
                multiline
                minRows={2}
                maxRows={8}
                value={draft.text}
                onChange={(e) => updateLlmDraft({ text: e.target.value })}
              />

              {/* Tool calls */}
              <Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                  <Typography variant="subtitle2">Tool Calls</Typography>
                  <IconButton size="small" onClick={addToolCall} aria-label="Add tool call">
                    <AddIcon fontSize="small" />
                  </IconButton>
                </Box>
                {draft.toolCalls.map((tc, i) => (
                  <Box key={i} sx={{ display: 'flex', gap: 1, mb: 1, alignItems: 'flex-start' }}>
                    <TextField
                      label="Name"
                      size="small"
                      value={tc.name}
                      onChange={(e) => updateToolCall(i, { name: e.target.value })}
                      sx={{ flex: 1 }}
                    />
                    <TextField
                      label="Arguments"
                      size="small"
                      value={tc.arguments ?? ''}
                      onChange={(e) => updateToolCall(i, { arguments: e.target.value })}
                      sx={{ flex: 2 }}
                    />
                    <IconButton
                      size="small"
                      onClick={() => removeToolCall(i)}
                      aria-label="Remove tool call"
                    >
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Box>
                ))}
              </Box>

              <TextField
                label="Stop Reason"
                size="small"
                fullWidth
                value={draft.stopReason}
                onChange={(e) => updateLlmDraft({ stopReason: e.target.value })}
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={draft.streaming}
                    onChange={(e) => updateLlmDraft({ streaming: e.target.checked })}
                  />
                }
                label="Streaming"
              />
            </Box>
          )}

          {/* Tab 0: Editable fields — Generic HTTP mode */}
          {tab === 0 && draft.kind === 'generic' && (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {/* Matcher precision toggle */}
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                  Matcher Precision
                </Typography>
                <ToggleButtonGroup
                  value={draft.matcherPrecision}
                  exclusive
                  onChange={(_, v: MatcherPrecision | null) => {
                    if (v !== null) updateGenericDraft({ matcherPrecision: v });
                  }}
                  size="small"
                  aria-label="Matcher precision"
                  disabled={registered}
                >
                  {MATCHER_PRECISIONS.map((p) => (
                    <Tooltip key={p} title={PRECISION_DESCRIPTIONS[p]} arrow>
                      <ToggleButton value={p} aria-label={`${PRECISION_LABELS[p]} precision`}>
                        {PRECISION_LABELS[p]}
                      </ToggleButton>
                    </Tooltip>
                  ))}
                </ToggleButtonGroup>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                  {PRECISION_DESCRIPTIONS[draft.matcherPrecision]}
                </Typography>
              </Box>

              {/* Request fields */}
              <Typography variant="subtitle2" sx={{ mt: 1, mb: -1 }}>Request Matcher</Typography>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <TextField
                  label="Method"
                  size="small"
                  value={draft.method}
                  onChange={(e) => updateGenericDraft({ method: e.target.value })}
                  slotProps={{ input: { readOnly: registered } }}
                  sx={{ width: 120 }}
                />
                <TextField
                  label="Path"
                  size="small"
                  fullWidth
                  value={draft.path}
                  onChange={(e) => updateGenericDraft({ path: e.target.value })}
                  slotProps={{ input: { readOnly: registered } }}
                />
              </Box>
              {draft.matcherPrecision !== 'loose' && (
                <TextField
                  label="Request Body"
                  size="small"
                  fullWidth
                  multiline
                  minRows={2}
                  maxRows={6}
                  value={draft.body ?? ''}
                  onChange={(e) => updateGenericDraft({ body: e.target.value })}
                  slotProps={{ input: { readOnly: registered } }}
                  helperText={
                    !draft.body
                      ? 'No body was captured — add one to match on the request body.'
                      : undefined
                  }
                />
              )}

              {/* Response fields */}
              <Typography variant="subtitle2" sx={{ mt: 1, mb: -1 }}>Response</Typography>
              <TextField
                label="Status Code"
                size="small"
                type="number"
                value={draft.responseStatusCode}
                onChange={(e) => { const v = parseInt(e.target.value, 10); updateGenericDraft({ responseStatusCode: isNaN(v) ? 200 : v }); }}
                slotProps={{ input: { readOnly: registered } }}
                sx={{ width: 120 }}
              />
              <TextField
                label="Response Body"
                size="small"
                fullWidth
                multiline
                minRows={2}
                maxRows={8}
                value={draft.responseBody}
                onChange={(e) => updateGenericDraft({ responseBody: e.target.value })}
                slotProps={{ input: { readOnly: registered } }}
              />
            </Box>
          )}

          {/* Tab 1: Preview diff — shows exactly what mock will be created from
              the captured request. Capture is a creation, so the "before" is an
              empty object and the diff highlights every field being added. */}
          {tab === 1 && (
            <Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                The mock below will be created from this captured request. Lines added on
                the right are the expectation that will be registered.
              </Typography>
              <JsonDiffViewer
                ariaLabel="Mock to be created"
                original="{}"
                modified={jsonOutput}
                height={400}
              />
            </Box>
          )}

          {/* Tab 2: JSON */}
          {tab === 2 && (
            <Box sx={{ position: 'relative' }}>
              <Box sx={{ position: 'absolute', top: 0, right: 0 }}>
                <CopyButton text={jsonOutput} />
              </Box>
              <Box
                component="pre"
                sx={{
                  fontFamily: monospaceFontFamily,
                  fontSize: '0.75rem',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                  m: 0,
                  p: 1,
                  bgcolor: 'action.hover',
                  borderRadius: 1,
                  overflow: 'auto',
                  maxHeight: 400,
                }}
              >
                {jsonOutput}
              </Box>
            </Box>
          )}

          {/* Tab 3: Java */}
          {tab === 3 && (
            <Box sx={{ position: 'relative' }}>
              <Box sx={{ position: 'absolute', top: 0, right: 0 }}>
                <CopyButton text={javaOutput} />
              </Box>
              <Box
                component="pre"
                sx={{
                  fontFamily: monospaceFontFamily,
                  fontSize: '0.75rem',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                  m: 0,
                  p: 1,
                  bgcolor: 'action.hover',
                  borderRadius: 1,
                  overflow: 'auto',
                  maxHeight: 400,
                }}
              >
                {javaOutput}
              </Box>
            </Box>
          )}

          {error && (
            <HumanErrorAlert error={error} sx={{ mt: 2 }} />
          )}
        </DialogContent>
        <DialogActions>
          <Button ref={closeButtonRef} onClick={onClose}>{registered ? 'Close' : 'Cancel'}</Button>
          {draft.kind === 'generic' && (
            <Button
              onClick={handleRefineInComposer}
              startIcon={<EditOutlinedIcon sx={{ fontSize: '0.875rem' }} />}
              disabled={registering || registered || !draft.path}
            >
              Refine in Composer
            </Button>
          )}
          <Button
            variant="contained"
            onClick={() => void handleRegister()}
            disabled={registerDisabled}
          >
            {registering ? 'Registering...' : registered ? 'Registered' : 'Register'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
