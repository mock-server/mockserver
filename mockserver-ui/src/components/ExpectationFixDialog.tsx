import { useEffect, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import type { SuggestedFix } from '../lib/explainUnmatched';
import { applyExpectationFix, describePatch, previewExpectationFix, type ExpectationFixPreview } from '../lib/expectationFix';
import { JsonPatchTestFailedError } from '../lib/jsonPatch';
import { monospaceFontFamily } from '../theme';
import { humanizeError, type HumanError } from '../lib/errorMessage';
import HumanErrorAlert from './HumanErrorAlert';
import LintWarningsBadge from './LintWarningsBadge';

const CHANGED_SINCE = 'This expectation has changed since the explanation was computed, so the fix was not applied. Refresh the explanation and preview the fix again.';

function RequestMatcherPane({ title, value, testId }: { title: string; value: unknown; testId: string }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="caption" color="text.secondary">{title}</Typography>
      <Box
        component="pre"
        data-testid={testId}
        sx={{ m: 0, p: 1, border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'auto', maxHeight: 280, fontFamily: monospaceFontFamily, fontSize: '0.75rem' }}
      >
        {JSON.stringify(value ?? {}, null, 2)}
      </Box>
    </Box>
  );
}

function formatValue(value: unknown): string {
  return value === undefined ? '' : JSON.stringify(value);
}

function errorFor(e: unknown): HumanError | string {
  return e instanceof JsonPatchTestFailedError ? CHANGED_SINCE : humanizeError(e);
}

function ErrorAlert({ error }: { error: HumanError | string }) {
  return typeof error === 'string'
    ? <Alert severity="warning" sx={{ mb: 1.5 }}>{error}</Alert>
    : <HumanErrorAlert error={error} sx={{ mb: 1.5 }} />;
}

/**
 * Preview, then apply on an explicit click, a fix the server suggested for an expectation that a recent request
 * did not match. Nothing is sent to the server until Apply Fix is selected.
 */
export default function ExpectationFixDialog({
  open,
  onClose,
  onApplied,
  connectionParams,
  expectationId,
  requestLabel,
  fix,
}: {
  open: boolean;
  onClose: () => void;
  onApplied: () => void;
  connectionParams: ConnectionParams;
  expectationId: string;
  requestLabel: string;
  fix: SuggestedFix;
}) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const [preview, setPreview] = useState<ExpectationFixPreview | null>(null);
  const [previewError, setPreviewError] = useState<HumanError | string | null>(null);
  const [applying, setApplying] = useState(false);
  const [applyError, setApplyError] = useState<HumanError | string | null>(null);
  const [applied, setApplied] = useState(false);
  const [lintWarnings, setLintWarnings] = useState(0);

  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    previewExpectationFix(connectionParams, expectationId, fix.patch, controller.signal)
      .then((next) => { if (!controller.signal.aborted) setPreview(next); })
      .catch((e: unknown) => { if (!controller.signal.aborted) setPreviewError(errorFor(e)); });
    return () => controller.abort();
  }, [open, connectionParams, expectationId, fix.patch]);

  async function apply(): Promise<void> {
    setApplying(true);
    setApplyError(null);
    try {
      const result = await applyExpectationFix(connectionParams, expectationId, fix.patch);
      setLintWarnings(result.lintWarnings);
      setApplied(true);
      onApplied();
    } catch (e) {
      setApplyError(errorFor(e));
    } finally {
      setApplying(false);
    }
  }

  const changes = describePatch(fix.patch);
  const loading = preview === null && previewError === null;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth fullScreen={fullScreen} aria-labelledby="expectation-fix-dialog-title">
      <DialogTitle id="expectation-fix-dialog-title">Preview Fix</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          The smallest change to expectation{' '}
          <Box component="span" sx={{ fontFamily: monospaceFontFamily }}>{expectationId}</Box>{' '}
          that lets it match <Box component="span" sx={{ fontFamily: monospaceFontFamily }}>{requestLabel}</Box>.
          Nothing changes until you select Apply Fix.
        </Typography>
        {!fix.matchesAfterPatch && (
          <Alert severity="warning" sx={{ mb: 1.5 }}>
            This fix alone does not make the expectation match the request: the fields listed below still need editing by hand.
          </Alert>
        )}
        {fix.unpatchedFields.length > 0 && (
          <Alert severity="info" sx={{ mb: 1.5 }}>
            <Typography variant="body2" sx={{ mb: 0.5 }}>Not changed by this fix:</Typography>
            {fix.unpatchedFields.map((u) => (
              <Typography key={u.field} variant="body2" component="div">
                <b>{u.field}</b>: {u.reason}
              </Typography>
            ))}
          </Alert>
        )}
        <Typography variant="subtitle2" sx={{ mb: 0.5 }}>Changes</Typography>
        <Box component="ul" sx={{ mt: 0, mb: 1.5, pl: 2.5 }} data-testid="expectation-fix-changes">
          {changes.map((change) => (
            <Typography key={`${change.kind}:${change.field}`} component="li" variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: '0.8rem' }}>
              {change.kind === 'remove' && <>remove <b>{change.field}</b> {formatValue(change.before)}</>}
              {change.kind === 'add' && <>add <b>{change.field}</b> {formatValue(change.after)}</>}
              {change.kind === 'replace' && <><b>{change.field}</b>: {formatValue(change.before)} → {formatValue(change.after)}</>}
            </Typography>
          ))}
        </Box>
        {previewError && <ErrorAlert error={previewError} />}
        {loading && <Box sx={{ display: 'flex', justifyContent: 'center', p: 2 }}><CircularProgress size={24} /></Box>}
        {preview && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.5 }}>
            <RequestMatcherPane title="Request matcher now" value={preview.before['httpRequest']} testId="expectation-fix-before" />
            <RequestMatcherPane title="Request matcher after the fix" value={preview.after['httpRequest']} testId="expectation-fix-after" />
          </Box>
        )}
        {applyError && <Box sx={{ mt: 1.5 }}><ErrorAlert error={applyError} /></Box>}
        {applied && (
          <Alert severity="success" sx={{ mt: 1.5 }}>
            Expectation updated.
            {lintWarnings > 0 && (
              <Box sx={{ mt: 1 }}>
                <LintWarningsBadge count={lintWarnings} connectionParams={connectionParams} />
              </Box>
            )}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{applied ? 'Close' : 'Cancel'}</Button>
        {!applied && (
          <Button variant="contained" onClick={() => { void apply(); }} disabled={preview === null || applying}>
            {applying ? 'Applying…' : 'Apply Fix'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
