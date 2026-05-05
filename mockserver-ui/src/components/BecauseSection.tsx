import { useState } from 'react';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Collapse from '@mui/material/Collapse';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import { becauseColors } from '../theme';

interface BecauseSectionProps {
  reasons: string[];
}

function reasonColor(reason: string): string {
  if (reason.includes('matched') && !reason.includes("didn't match")) {
    return becauseColors.matched;
  }
  if (reason.includes("didn't match")) {
    return becauseColors.didntMatch;
  }
  return becauseColors.neutral;
}

export default function BecauseSection({ reasons }: BecauseSectionProps) {
  const [open, setOpen] = useState(false);

  return (
    <Box sx={{ pl: 1 }}>
      <IconButton size="small" onClick={() => setOpen(!open)} sx={{ color: 'rgb(222, 147, 95)' }}>
        {open ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
      </IconButton>
      {!open && (
        <Box component="span" sx={{ color: 'rgb(222, 147, 95)', fontFamily: 'monospace' }}>
          ...
        </Box>
      )}
      <Collapse in={open}>
        {reasons.map((reason, i) => (
          <Box
            key={i}
            sx={{
              color: reasonColor(reason),
              fontSize: '0.95em',
              lineHeight: '1.5em',
              whiteSpace: 'pre',
              pl: 3,
              pb: 1,
              fontFamily: 'monospace',
            }}
          >
            {reason}
          </Box>
        ))}
      </Collapse>
    </Box>
  );
}
