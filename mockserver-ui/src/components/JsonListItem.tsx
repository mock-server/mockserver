import { useState } from 'react';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import type { JsonListItem as JsonListItemType } from '../types';
import JsonViewer from './JsonViewer';
import DescriptionDisplay from './DescriptionDisplay';

interface JsonListItemProps {
  item: JsonListItemType;
  index: number;
}

export default function JsonListItem({ item, index }: JsonListItemProps) {
  const [expanded, setExpanded] = useState(false);

  return (
    <Box
      sx={{
        position: 'relative',
        py: 0.5,
        px: 1,
        borderBottom: 1,
        borderColor: 'divider',
        '&:hover .copy-btn': { opacity: 1 },
        '&:last-child': { borderBottom: 0 },
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          cursor: 'pointer',
          userSelect: 'none',
        }}
        onClick={() => setExpanded((prev) => !prev)}
      >
        <IconButton size="small" sx={{ p: 0, '& .MuiSvgIcon-root': { fontSize: '1rem' } }}>
          {expanded ? <ExpandMoreIcon /> : <ChevronRightIcon />}
        </IconButton>
        <Box
          component="span"
          sx={{ fontFamily: 'monospace', fontSize: '0.8em', color: 'text.secondary', minWidth: 24 }}
        >
          {index}
        </Box>
        {item.description && <DescriptionDisplay description={item.description} />}
      </Box>
      {expanded && (
        <Box sx={{ pl: 3.5, pt: 0.5 }}>
          <JsonViewer data={item.value} collapsed={1} enableClipboard={true} />
        </Box>
      )}
    </Box>
  );
}
