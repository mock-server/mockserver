import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useState } from 'react';
import { useDashboardStore } from '../store';
import type { DebugMismatchExpectationResult } from '../types';

function scoreColor(matched: number, total: number): 'success' | 'warning' | 'error' {
  const ratio = matched / total;
  if (ratio >= 0.8) return 'success';
  if (ratio >= 0.5) return 'warning';
  return 'error';
}

function ExpectationResultRow({ result, isClosest }: { result: DebugMismatchExpectationResult; isClosest: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const hasDiffs = result.differences && Object.keys(result.differences).length > 0;

  return (
    <Box
      sx={{
        borderBottom: 1,
        borderColor: 'divider',
        '&:last-child': { borderBottom: 0 },
        ...(isClosest && {
          borderLeft: 3,
          borderLeftColor: 'warning.main',
          pl: 1,
        }),
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          py: 0.75,
          px: 1,
          cursor: hasDiffs ? 'pointer' : 'default',
          '&:hover': hasDiffs ? { bgcolor: 'action.hover' } : {},
        }}
        onClick={() => hasDiffs && setExpanded((prev) => !prev)}
      >
        {hasDiffs && (
          <IconButton size="small" sx={{ p: 0, '& .MuiSvgIcon-root': { fontSize: '1rem' } }}>
            {expanded ? <ExpandMoreIcon /> : <ChevronRightIcon />}
          </IconButton>
        )}
        {result.matches ? (
          <CheckCircleIcon sx={{ fontSize: '1rem', color: 'success.main' }} />
        ) : (
          <CancelIcon sx={{ fontSize: '1rem', color: 'error.main' }} />
        )}
        <Chip
          label={`${result.matchedFieldCount}/${result.totalFieldCount}`}
          size="small"
          color={scoreColor(result.matchedFieldCount, result.totalFieldCount)}
          variant="outlined"
          sx={{ fontFamily: 'monospace', fontSize: '0.75rem', height: 20, minWidth: 48 }}
        />
        <Box component="span" sx={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'text.secondary' }}>
          {result.expectationMethod && result.expectationPath
            ? `${result.expectationMethod} ${result.expectationPath}`
            : result.expectationId ?? 'unknown'}
        </Box>
        {isClosest && (
          <Chip label="closest" size="small" color="warning" sx={{ fontSize: '0.7rem', height: 18 }} />
        )}
      </Box>
      {expanded && hasDiffs && (
        <Box sx={{ pl: 5, pb: 1, pr: 1 }}>
          {Object.entries(result.differences!).map(([field, diffs]) => (
            <Box key={field} sx={{ mb: 0.5 }}>
              <Typography
                variant="caption"
                sx={{ fontFamily: 'monospace', fontWeight: 600, color: 'error.main', display: 'block' }}
              >
                {field}
              </Typography>
              {diffs.map((diff, i) => (
                <Typography
                  key={i}
                  variant="caption"
                  sx={{
                    fontFamily: 'monospace',
                    display: 'block',
                    pl: 2,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    color: 'text.secondary',
                    lineHeight: 1.4,
                  }}
                >
                  {diff}
                </Typography>
              ))}
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}

export default function DebugMismatchDialog() {
  const open = useDashboardStore((s) => s.debugMismatchOpen);
  const result = useDashboardStore((s) => s.debugMismatchResult);
  const loading = useDashboardStore((s) => s.debugMismatchLoading);
  const error = useDashboardStore((s) => s.debugMismatchError);
  const close = useDashboardStore((s) => s.closeDebugMismatch);

  return (
    <Dialog open={open} onClose={close} maxWidth="md" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pr: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          Why Didn&apos;t This Match?
          {result && (
            <Chip
              label={`${result.totalExpectations} expectation${result.totalExpectations !== 1 ? 's' : ''}`}
              size="small"
              variant="outlined"
              sx={{ fontSize: '0.75rem' }}
            />
          )}
        </Box>
        <IconButton size="small" onClick={close}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ p: 0 }}>
        {loading && (
          <Typography sx={{ p: 3, textAlign: 'center' }} color="text.secondary">
            Analyzing match results...
          </Typography>
        )}
        {error && (
          <Typography sx={{ p: 3, textAlign: 'center' }} color="error">
            {error}
          </Typography>
        )}
        {result && !loading && (
          <>
            {result.truncated && (
              <Typography variant="caption" sx={{ display: 'block', px: 2, py: 0.5, bgcolor: 'warning.dark', color: 'warning.contrastText' }}>
                Showing first {result.maxExpectationsEvaluated} of {result.totalExpectations} expectations
              </Typography>
            )}
            {result.results.length === 0 ? (
              <Typography sx={{ p: 3, textAlign: 'center' }} color="text.secondary">
                No active expectations
              </Typography>
            ) : (
              result.results.map((r, i) => (
                <ExpectationResultRow
                  key={r.expectationId ?? i}
                  result={r}
                  isClosest={result.closestMatch?.expectationId === r.expectationId}
                />
              ))
            )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={close} size="small">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}
