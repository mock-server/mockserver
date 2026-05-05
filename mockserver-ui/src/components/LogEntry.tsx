import { useState } from 'react';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import type { LogEntryValue, MessagePart } from '../types';
import JsonViewer from './JsonViewer';
import BecauseSection from './BecauseSection';
import CopyButton from './CopyButton';

interface LogEntryProps {
  entry: LogEntryValue;
  indent?: boolean;
  divider?: boolean;
  collapsible?: boolean;
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

export function entryToText(entry: LogEntryValue): string {
  const parts: string[] = [];
  if (entry.description) {
    if (typeof entry.description === 'string') {
      parts.push(entry.description);
    } else if (entry.description.json === false) {
      parts.push(`${entry.description.first} ${entry.description.second}`);
    } else {
      parts.push(entry.description.first);
    }
  }
  if (entry.messageParts) {
    for (const p of entry.messageParts) {
      if (typeof p.value === 'string') parts.push(p.value);
      else if (Array.isArray(p.value)) parts.push(p.value.join('\n'));
      else if (typeof p.value === 'object') parts.push(JSON.stringify(p.value, null, 2));
      else parts.push(String(p.value));
    }
  }
  return parts.join(' ').trim();
}

function getSummary(entry: LogEntryValue): string {
  if (!entry.messageParts || entry.messageParts.length === 0) return '';
  const firstTextPart = entry.messageParts.find(
    (p) => typeof p.value === 'string' && p.value.trim().length > 0,
  );
  if (!firstTextPart) return '';
  const text = String(firstTextPart.value).trim().split('\n')[0]!;
  return text.length > 80 ? text.substring(0, 80) + '…' : text;
}

function descriptionText(entry: LogEntryValue): string {
  if (!entry.description) return '';
  if (typeof entry.description === 'string') return entry.description;
  if (entry.description.json === false) {
    return `${entry.description.first} ${entry.description.second}`;
  }
  return entry.description.first;
}

export default function LogEntry({ entry, indent = false, divider = false, collapsible = false }: LogEntryProps) {
  const style = entry.style ?? {};
  const hasBody = entry.messageParts && entry.messageParts.length > 0;
  const canCollapse = collapsible && hasBody;
  const [expanded, setExpanded] = useState(false);

  return (
    <Box
      sx={{
        pl: indent ? 4 : 0.5,
        pr: 0.5,
        py: 0.5,
        fontSize: indent ? '0.8em' : '0.85em',
        whiteSpace: style['style.whiteSpace'] || style['whiteSpace'] || 'nowrap',
        overflow: 'auto',
        color: style.color ?? 'inherit',
        position: 'relative',
        '&:hover .copy-btn': { opacity: 1 },
        ...(divider && {
          borderBottom: 1,
          borderColor: 'divider',
          '&:last-child': { borderBottom: 0 },
        }),
      }}
    >
      {canCollapse ? (
        <>
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
              sx={{ whiteSpace: 'pre', fontFamily: 'monospace' }}
            >
              {descriptionText(entry) || 'SYSTEM_MESSAGE'}
            </Box>
            {!expanded && (
              <Box
                component="span"
                sx={{ fontFamily: 'monospace', color: 'text.secondary', ml: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}
              >
                {getSummary(entry)}
              </Box>
            )}
          </Box>
          {expanded && (
            <Box sx={{ pl: 2.5, pt: 0.5 }}>
              {entry.messageParts?.map(renderMessagePart)}
            </Box>
          )}
        </>
      ) : (
        <>
          {entry.description && (
            <Box
              sx={{
                whiteSpace: 'pre',
                fontFamily: 'monospace',
                display: 'block',
              }}
            >
              {descriptionText(entry)}
            </Box>
          )}
          {entry.messageParts?.map(renderMessagePart)}
        </>
      )}
      <Box className="copy-btn" sx={{ position: 'absolute', top: 2, right: 2, opacity: 0 }}>
        <CopyButton text={entryToText(entry)} />
      </Box>
    </Box>
  );
}
