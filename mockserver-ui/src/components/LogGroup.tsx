import { useState, useMemo } from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import type { LogGroup as LogGroupType } from '../types';
import LogEntry, { entryToText } from './LogEntry';
import CopyButton from './CopyButton';

interface LogGroupProps {
  group: LogGroupType;
}

function extractCorrelationId(group: LogGroupType): string | null {
  const key = group.group.key;
  if (key) {
    const match = key.match(/^(.+?)_log/);
    if (match) return match[1]!;
  }
  return null;
}

export default function LogGroup({ group }: LogGroupProps) {
  const [open, setOpen] = useState(false);
  const correlationId = useMemo(() => extractCorrelationId(group), [group]);

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
          onClick={() => setOpen(!open)}
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
                sx={{ fontFamily: 'monospace', fontSize: '0.65rem', height: 18, cursor: 'pointer', flexShrink: 0 }}
              />
            </Tooltip>
          )}
        </Box>
      </Box>
      <Box className="group-copy-btn" sx={{ position: 'absolute', top: 2, right: 2, opacity: 0 }}>
        <CopyButton text={groupText} />
      </Box>
      <Collapse in={open}>
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
