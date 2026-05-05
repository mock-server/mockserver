import { useState } from 'react';
import Box from '@mui/material/Box';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import type { LogGroup as LogGroupType } from '../types';
import LogEntry from './LogEntry';

interface LogGroupProps {
  group: LogGroupType;
}

export default function LogGroup({ group }: LogGroupProps) {
  const [open, setOpen] = useState(false);

  return (
    <Box
      sx={{
        borderLeft: 2,
        borderColor: 'rgb(222, 147, 95)',
        ml: 0.5,
        mb: 0.5,
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
        <Box sx={{ flex: 1 }}>
          <LogEntry entry={group.group.value} />
        </Box>
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
            <LogEntry key={item.key} entry={item.value} indent />
          ))}
        </Box>
      </Collapse>
    </Box>
  );
}
