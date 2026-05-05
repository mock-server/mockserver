import Box from '@mui/material/Box';
import type { JsonListItem as JsonListItemType } from '../types';
import JsonViewer from './JsonViewer';
import DescriptionDisplay from './DescriptionDisplay';

interface JsonListItemProps {
  item: JsonListItemType;
  index: number;
}

export default function JsonListItem({ item, index }: JsonListItemProps) {
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
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mb: 0.5 }}>
        <Box
          component="span"
          sx={{ fontFamily: 'monospace', fontSize: '0.8em', color: 'text.secondary', minWidth: 24 }}
        >
          {index}
        </Box>
        {item.description && <DescriptionDisplay description={item.description} />}
      </Box>
      <JsonViewer data={item.value} collapsed={1} enableClipboard={true} />
    </Box>
  );
}
