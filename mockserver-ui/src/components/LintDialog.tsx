import { useCallback, useEffect, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Link from '@mui/material/Link';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import { lintActiveExpectations, type LintIssue, type LintReport, type LintSeverity } from '../lib/lint';
import { fetchExpectation } from '../lib/expectations';
import { humanizeError, type HumanError } from '../lib/errorMessage';
import { useDashboardStore } from '../store';
import { monospaceFontFamily } from '../theme';
import HumanErrorAlert from './HumanErrorAlert';

const SEVERITY_LABELS: Record<LintSeverity, string> = { ERROR: 'Error', WARNING: 'Warning', INFO: 'Info' };
const SEVERITY_COLORS: Record<LintSeverity, 'error' | 'warning' | 'info'> = { ERROR: 'error', WARNING: 'warning', INFO: 'info' };

function ExpectationLink({ id, onOpen, disabled }: { id: string; onOpen: (id: string) => void; disabled: boolean }) {
  return (
    <Tooltip title="Open in the Mocks composer">
      <Link
        component="button"
        type="button"
        disabled={disabled}
        onClick={() => onOpen(id)}
        aria-label={`Open expectation ${id}`}
        sx={{ fontFamily: monospaceFontFamily, fontSize: '0.75rem', verticalAlign: 'baseline' }}
      >
        {id}
      </Link>
    </Tooltip>
  );
}

function IssueRow({ issue, onOpen, opening }: { issue: LintIssue; onOpen: (id: string) => void; opening: boolean }) {
  return (
    <Box data-testid="lint-issue" sx={{ pl: 1.5, py: 0.75, borderLeft: 2, borderColor: `${SEVERITY_COLORS[issue.severity] ?? 'info'}.main`, mb: 0.75 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Chip size="small" color={SEVERITY_COLORS[issue.severity] ?? 'default'} label={SEVERITY_LABELS[issue.severity] ?? issue.severity} />
        <Chip size="small" variant="outlined" label={issue.rule} sx={{ fontFamily: monospaceFontFamily }} />
        {issue.position !== undefined && (
          <Typography variant="caption" color="text.secondary">#{issue.position}</Typography>
        )}
        {issue.summary && (
          <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily }}>{issue.summary}</Typography>
        )}
        {issue.expectationId && <ExpectationLink id={issue.expectationId} onOpen={onOpen} disabled={opening} />}
      </Box>
      <Typography variant="body2" sx={{ mt: 0.5, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
        {issue.field && <b>{issue.field}: </b>}
        {issue.message}
      </Typography>
      {(issue.relatedExpectationId || issue.relatedSummary) && (
        <Box data-testid="lint-related" sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', mt: 0.25 }}>
          <Typography variant="caption" color="text.secondary">
            {issue.rule === 'SHADOWED' ? 'Shadowed by' : 'Related to'}
            {issue.relatedPosition !== undefined && ` #${issue.relatedPosition}`}
          </Typography>
          {issue.relatedSummary && (
            <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily }}>{issue.relatedSummary}</Typography>
          )}
          {issue.relatedExpectationId && <ExpectationLink id={issue.relatedExpectationId} onOpen={onOpen} disabled={opening} />}
        </Box>
      )}
    </Box>
  );
}

/**
 * Lints the active expectations with `PUT /mockserver/lint`: each finding's rule, severity and
 * message, and for a shadowed expectation a link to the one that always matches first. An
 * expectation id opens that expectation, whole, in the Mocks composer.
 */
export default function LintDialog({
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
  const editExpectation = useDashboardStore((s) => s.editExpectation);
  const [report, setReport] = useState<LintReport | null>(null);
  const [error, setError] = useState<HumanError | null>(null);
  const [openError, setOpenError] = useState<HumanError | null>(null);
  const [opening, setOpening] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);

  const reset = useCallback(() => {
    setReport(null);
    setError(null);
    setOpenError(null);
  }, []);

  const refresh = useCallback(() => {
    reset();
    setRefreshTick((t) => t + 1);
  }, [reset]);

  // Clear the result on close so reopening never shows a stale lint first.
  const close = useCallback(() => {
    reset();
    onClose();
  }, [reset, onClose]);

  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    lintActiveExpectations(connectionParams, controller.signal)
      .then((next) => setReport(next))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setError(humanizeError(e));
      });
    return () => controller.abort();
  }, [open, connectionParams, refreshTick]);

  const openExpectation = useCallback(async (id: string) => {
    setOpening(true);
    setOpenError(null);
    try {
      const expectation = await fetchExpectation(connectionParams, id);
      editExpectation(expectation);
      close();
    } catch (e) {
      setOpenError(humanizeError(e));
    } finally {
      setOpening(false);
    }
  }, [connectionParams, editExpectation, close]);

  const loading = open && report === null && error === null;

  return (
    <Dialog open={open} onClose={close} maxWidth="md" fullWidth fullScreen={fullScreen} aria-labelledby="lint-dialog-title">
      <DialogTitle id="lint-dialog-title">Lint Expectations</DialogTitle>
      <DialogContent dividers>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Finds active expectations that can never match, or will not match the way they read. Lint is
          advisory: it never changes an expectation.
        </Typography>
        {error && <HumanErrorAlert error={error} sx={{ mb: 1.5 }} />}
        {openError && <HumanErrorAlert error={openError} sx={{ mb: 1.5 }} onClose={() => setOpenError(null)} />}
        {loading && <Box sx={{ display: 'flex', justifyContent: 'center', p: 2 }}><CircularProgress size={24} /></Box>}
        {report && (
          <>
            <Box data-testid="lint-summary" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 1.5 }}>
              <Chip size="small" variant="outlined" label={`${report.expectationsChecked} Checked`} />
              <Chip size="small" variant="outlined" color={report.errorCount > 0 ? 'error' : 'default'} label={`${report.errorCount} ${report.errorCount === 1 ? 'Error' : 'Errors'}`} />
              <Chip size="small" variant="outlined" color={report.warningCount > 0 ? 'warning' : 'default'} label={`${report.warningCount} ${report.warningCount === 1 ? 'Warning' : 'Warnings'}`} />
              <Chip size="small" variant="outlined" label={`${report.infoCount} Info`} />
              {report.inactiveExpectationsSkipped > 0 && (
                <Chip size="small" variant="outlined" label={`${report.inactiveExpectationsSkipped} Inactive Skipped`} />
              )}
            </Box>
            {report.shadowCheckTruncated && (
              <Alert severity="info" sx={{ mb: 1 }}>
                The shadowing check stopped at its comparison budget, so some shadowed expectations may not be listed.
              </Alert>
            )}
            {report.issuesTruncated && (
              <Alert severity="info" sx={{ mb: 1 }}>
                Only the first {report.issues.length} findings are listed.
              </Alert>
            )}
            {report.issues.length === 0
              ? <Typography variant="body2" data-testid="lint-clean">No problems found.</Typography>
              : report.issues.map((issue, i) => (
                <IssueRow key={`${issue.rule}-${issue.expectationId ?? ''}-${i}`} issue={issue} onOpen={(id) => { void openExpectation(id); }} opening={opening} />
              ))}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={refresh} disabled={loading}>Refresh</Button>
        <Button onClick={close}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
