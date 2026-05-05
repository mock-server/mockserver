import Box from '@mui/material/Box';
import type { LogEntryValue, MessagePart } from '../types';
import JsonViewer from './JsonViewer';
import BecauseSection from './BecauseSection';
import CopyButton from './CopyButton';

interface LogEntryProps {
  entry: LogEntryValue;
  indent?: boolean;
}

function addLinks(value: string) {
  const urlMatch = value.match(/(https?:\/\/[^\s]*)/);
  if (urlMatch) {
    const matchedUrl = urlMatch[0]!;
    const idx = value.indexOf(matchedUrl);
    return (
      <span>
        {value.substring(0, idx)}
        <a
          href={matchedUrl}
          target="_blank"
          rel="noopener noreferrer"
          style={{ textDecoration: 'underline', color: 'rgb(95, 113, 245)' }}
        >
          {matchedUrl}
        </a>
        {value.substring(idx + matchedUrl.length)}
      </span>
    );
  }
  return value;
}

function renderMessagePart(part: MessagePart) {
  if (part.value === undefined || part.value === null) return null;

  if (!part.argument) {
    return (
      <Box key={part.key} component="span" sx={{ fontFamily: 'monospace' }}>
        {addLinks(String(part.value))}
      </Box>
    );
  }

  if (part.because && Array.isArray(part.value)) {
    return <BecauseSection key={part.key} reasons={part.value as string[]} />;
  }

  if (part.multiline && Array.isArray(part.value)) {
    return <BecauseSection key={part.key} reasons={part.value as string[]} />;
  }

  if (part.json) {
    if (typeof part.value === 'object' && part.value !== null) {
      return (
        <Box key={part.key} sx={{ display: 'inline-block', pl: 0.5 }}>
          <JsonViewer
            data={part.value as Record<string, unknown>}
            collapsed={0}
            enableClipboard={true}
          />
        </Box>
      );
    }
    return (
      <Box key={part.key} component="span" sx={{ fontFamily: 'monospace', pl: 0.5 }}>
        {String(part.value)}
      </Box>
    );
  }

  return (
    <Box
      key={part.key}
      component="span"
      sx={{ fontFamily: 'monospace', pl: 0.5, letterSpacing: '0.08em', whiteSpace: 'pre' }}
    >
      {addLinks(String(part.value))}
    </Box>
  );
}

function entryToText(entry: LogEntryValue): string {
  if (!entry.messageParts) return '';
  return entry.messageParts
    .map((p) => {
      if (typeof p.value === 'string') return p.value;
      if (Array.isArray(p.value)) return p.value.join('\n');
      if (typeof p.value === 'object') return JSON.stringify(p.value, null, 2);
      return String(p.value);
    })
    .join(' ');
}

export default function LogEntry({ entry, indent = false }: LogEntryProps) {
  const style = entry.style ?? {};

  return (
    <Box
      sx={{
        pl: indent ? 4 : 0.5,
        pr: 0.5,
        pb: '4px',
        pt: '4px',
        fontSize: indent ? '0.9em' : '1.0em',
        whiteSpace: style['style.whiteSpace'] || style['whiteSpace'] || 'nowrap',
        overflow: 'auto',
        color: style.color ?? 'inherit',
        position: 'relative',
        '&:hover .copy-btn': { opacity: 1 },
      }}
    >
      {entry.description && (
        <Box
          sx={{
            whiteSpace: 'pre',
            fontFamily: 'monospace',
            display: 'block',
          }}
        >
          {typeof entry.description === 'string'
            ? entry.description
            : entry.description.json === false
              ? `${entry.description.first} ${entry.description.second}`
              : entry.description.first}
        </Box>
      )}
      {entry.messageParts?.map(renderMessagePart)}
      <Box className="copy-btn" sx={{ position: 'absolute', top: 2, right: 2, opacity: 0 }}>
        <CopyButton text={entryToText(entry)} />
      </Box>
    </Box>
  );
}
