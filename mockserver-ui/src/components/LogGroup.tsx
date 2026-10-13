import { memo, useState, useMemo } from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Button from '@mui/material/Button';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import type { LogGroup as LogGroupType } from '../types';
import LogEntry from './LogEntry';
import { entryToText } from '../lib/logEntryText';
import CopyButton from './CopyButton';
import { monospaceFontFamily } from '../theme';
import { isExplainableGroup } from '../lib/explainRequest';

interface LogGroupProps {
  group: LogGroupType;
  /**
   * Controlled open state, lifted to the panel so it survives the group being
   * unmounted while scrolled out of a virtualized list. When omitted the group
   * falls back to its own internal state.
   */
  open?: boolean;
  /**
   * Receives the group's key so the panel can pass a single stable callback
   * (`expansion.toggle`) for every row instead of a fresh per-row closure —
   * which would otherwise defeat this component's `memo`.
   */
  onToggleOpen?: (key: string) => void;
  /**
   * Opens the Explain dialog for this group's correlation ID. The panel owns the dialog,
   * because a virtualized row unmounts when it scrolls out of the window. No button without it.
   */
  onExplain?: (correlationId: string) => void;
}

function LogGroup({ group, open: openProp, onToggleOpen, onExplain }: LogGroupProps) {
  const [internalOpen, setInternalOpen] = useState(false);
  const open = openProp ?? internalOpen;
  const handleToggleOpen = () => {
    if (onToggleOpen) onToggleOpen(group.key);
    else setInternalOpen((prev) => !prev);
  };
  const correlationId = group.correlationId ?? null;
  const explainable = useMemo(() => isExplainableGroup(group), [group]);

  const groupText = useMemo(() => {
    const parts = [entryToText(group.group.value)];
    for (const item of group.value) {
      parts.push(entryToText(item.value));
    }
    return parts.filter(Boolean).join('\n\n');
  }, [group]);

  return (
    <Box
      sx={{
        position: 'relative',
        borderLeft: 2,
        borderColor: 'rgb(222, 147, 95)',
        borderBottom: 1,
        borderBottomColor: 'divider',
        '&:last-child': { borderBottom: 0 },
        '&:hover .group-copy-btn': { opacity: 1 },
        ml: 0.5,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start' }}>
        <IconButton
          size="small"
          onClick={handleToggleOpen}
          aria-label={open ? 'Collapse' : 'Expand'}
          aria-expanded={open}
          sx={{ color: 'rgb(222, 147, 95)', mt: 0.25 }}
        >
          {open ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
        </IconButton>
        <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Box sx={{ flex: 1 }}>
            <LogEntry entry={group.group.value} />
          </Box>
          {correlationId && (
            <Tooltip title={`Correlation ID: ${correlationId} (click to copy)`}>
              <Chip
                label={correlationId.substring(0, 8)}
                size="small"
                variant="outlined"
                onClick={(e) => {
                  e.stopPropagation();
                  void navigator.clipboard.writeText(correlationId);
                }}
                sx={{ fontFamily: monospaceFontFamily, typography: 'caption', height: 18, cursor: 'pointer', flexShrink: 0 }}
              />
            </Tooltip>
          )}
          {correlationId && explainable && onExplain && (
            <Tooltip describeChild title="Explain which expectation would serve this request now, and why others were skipped">
              <Button
                size="small"
                variant="outlined"
                onClick={(e) => {
                  e.stopPropagation();
                  onExplain(correlationId);
                }}
                sx={{ typography: 'caption', height: 18, minWidth: 0, px: 0.75, py: 0, flexShrink: 0, textTransform: 'none' }}
              >
                Explain
              </Button>
            </Tooltip>
          )}
        </Box>
      </Box>
      <Box className="group-copy-btn" sx={{ position: 'absolute', top: 2, right: 2, opacity: 0 }}>
        <CopyButton text={groupText} />
      </Box>
      <Collapse in={open} unmountOnExit>
        <Box
          sx={{
            ml: 4,
            mr: 0.5,
            my: 1,
            pl: 0.5,
            borderLeft: 1,
            borderStyle: 'dashed',
            borderColor: 'divider',
          }}
        >
          {group.value.map((item) => (
            <LogEntry key={item.key} entry={item.value} indent divider />
          ))}
        </Box>
      </Collapse>
    </Box>
  );
}

// Memoized: LogPanel re-renders on every ~1/sec WebSocket snapshot, but a group
// whose content is unchanged keeps a stable `group` reference (preserved by the
// store's reconcileByKey) and a stable `onToggleOpen`, so it can skip the
// (non-trivial) entryToText re-computation over all its child entries.
export default memo(LogGroup);
