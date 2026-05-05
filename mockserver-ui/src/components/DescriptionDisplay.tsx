import Box from '@mui/material/Box';
import type { Description } from '../types';
import JsonViewer from './JsonViewer';

interface DescriptionDisplayProps {
  description: Description;
}

export default function DescriptionDisplay({ description }: DescriptionDisplayProps) {
  if (typeof description === 'string') {
    return (
      <Box
        component="span"
        sx={{ fontFamily: 'monospace', fontSize: '0.9em', whiteSpace: 'pre' }}
      >
        {description}
      </Box>
    );
  }

  if (description.json) {
    return (
      <Box sx={{ display: 'inline-flex', alignItems: 'baseline', gap: 0.5, flexWrap: 'wrap' }}>
        <Box component="span" sx={{ fontFamily: 'monospace', fontSize: '0.9em' }}>
          {description.first}
        </Box>
        <Box sx={{ display: 'inline-block', maxWidth: 300 }}>
          <JsonViewer data={description.object} collapsed={0} enableClipboard={false} />
        </Box>
        <Box component="span" sx={{ fontFamily: 'monospace', fontSize: '0.9em' }}>
          {description.second}
        </Box>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'inline-flex', gap: 0.5, fontFamily: 'monospace', fontSize: '0.9em' }}>
      <span>{description.first}</span>
      <span>{description.second}</span>
    </Box>
  );
}
