import { useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import {
  dateTimeMatcherToWire,
  matcherFieldSeparator,
  matcherValueLine,
  type DateTimeOperator,
  type MatcherValueField,
} from '../lib/matcherValue';

const FIELDS: { value: MatcherValueField; label: string }[] = [
  { value: 'headers', label: 'Header' },
  { value: 'queryString', label: 'Query Parameter' },
  { value: 'cookies', label: 'Cookie' },
  { value: 'pathParams', label: 'Path Parameter' },
];

const OPERATORS: { value: DateTimeOperator; label: string }[] = [
  { value: 'before', label: 'Before' },
  { value: 'after', label: 'After' },
  { value: 'equalToDateTime', label: 'Equal To Date-Time' },
];

interface Draft {
  field: MatcherValueField;
  name: string;
  operator: DateTimeOperator;
  expected: string;
  actualFormat: string;
  truncateExpected: string;
  truncateActual: string;
  zone: string;
  applyTruncationLast: boolean;
  not: boolean;
}

function emptyDraft(field: MatcherValueField): Draft {
  return {
    field, name: '', operator: 'after', expected: '', actualFormat: '', truncateExpected: '',
    truncateActual: '', zone: '', applyTruncationLast: false, not: false,
  };
}

/**
 * Builds one date-time matcher line (e.g. `X-Issued-At: {"after":"now -1 hours"}`) and hands it
 * to `onAdd` for the chosen header / query / cookie / path-parameter field. Given `onAddBody`
 * instead, it builds the date-time comparison for a JSON Path / XPath body and hands over its
 * compact JSON (e.g. `{"after":"now -1 days"}`); there is then no field or name to choose.
 */
export default function DateTimeMatcherDialog({
  open,
  onClose,
  onAdd,
  onAddBody,
}: {
  open: boolean;
  onClose: () => void;
  onAdd?: (field: MatcherValueField, line: string) => void;
  onAddBody?: (json: string) => void;
}) {
  const [draft, setDraft] = useState<Draft>(() => emptyDraft('headers'));
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => setDraft((d) => ({ ...d, [key]: value }));
  const forBody = !!onAddBody;

  const name = draft.name.trim();
  const nameError = name.includes(matcherFieldSeparator(draft.field))
    ? `Name cannot contain "${matcherFieldSeparator(draft.field)}"`
    : undefined;
  const canAdd = (forBody || (name !== '' && !nameError)) && draft.expected.trim() !== '';

  const add = () => {
    const value = dateTimeMatcherToWire({
      operator: draft.operator,
      expected: draft.expected.trim(),
      actualFormat: draft.actualFormat.trim(),
      truncateExpected: draft.truncateExpected.trim(),
      truncateActual: draft.truncateActual.trim(),
      zone: draft.zone.trim(),
      applyTruncationLast: draft.applyTruncationLast || undefined,
      not: draft.not,
    });
    if (onAddBody) onAddBody(JSON.stringify(value));
    else onAdd?.(draft.field, matcherValueLine(draft.field, name, value));
    setDraft(emptyDraft(draft.field));
    onClose();
  };

  const optional = (key: 'actualFormat' | 'truncateExpected' | 'truncateActual' | 'zone', label: string, placeholder: string) => (
    <TextField
      label={label}
      size="small"
      value={draft[key]}
      onChange={(e) => set(key, e.target.value)}
      placeholder={placeholder}
      sx={{ flex: 1, minWidth: 180 }}
    />
  );

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth aria-labelledby="date-time-matcher-title">
      <DialogTitle id="date-time-matcher-title">{forBody ? 'Body Date-Time Comparison' : 'Add Date-Time Matcher'}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Match a value by comparing it as a date-time instead of as text. The expected value
          is <code>now</code>, <code>now -1 hours</code>, or a date-time such as <code>2026-10-21T15:00:00Z</code>.
        </Typography>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, pt: 0.5 }}>
          {!forBody && <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <TextField
              label="Field"
              size="small"
              select
              value={draft.field}
              onChange={(e) => set('field', e.target.value as MatcherValueField)}
              sx={{ minWidth: 170 }}
            >
              {FIELDS.map((f) => <MenuItem key={f.value} value={f.value}>{f.label}</MenuItem>)}
            </TextField>
            <TextField
              label="Name"
              size="small"
              value={draft.name}
              onChange={(e) => set('name', e.target.value)}
              placeholder="X-Issued-At"
              error={!!nameError}
              helperText={nameError}
              sx={{ flex: 1, minWidth: 180 }}
            />
          </Box>}
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <TextField
              label="Comparison"
              size="small"
              select
              value={draft.operator}
              onChange={(e) => set('operator', e.target.value as DateTimeOperator)}
              sx={{ minWidth: 170 }}
            >
              {OPERATORS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
            </TextField>
            <TextField
              label="Expected Value"
              size="small"
              value={draft.expected}
              onChange={(e) => set('expected', e.target.value)}
              placeholder="now -1 hours"
              sx={{ flex: 1, minWidth: 180 }}
            />
          </Box>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {optional('actualFormat', 'Actual Format (optional)', 'rfc1123, epochSeconds, dd/MM/yyyy')}
            {optional('zone', 'Zone (optional)', 'Europe/London')}
          </Box>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {optional('truncateExpected', 'Truncate Expected (optional)', 'startOfDay')}
            {optional('truncateActual', 'Truncate Actual (optional)', 'first day of month')}
          </Box>
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <FormControlLabel
              control={<Checkbox size="small" checked={draft.applyTruncationLast} onChange={(e) => set('applyTruncationLast', e.target.checked)} />}
              label="Apply Truncation Last"
            />
            <FormControlLabel
              control={<Checkbox size="small" checked={draft.not} onChange={(e) => set('not', e.target.checked)} />}
              label="Not (Negate the Match)"
            />
          </Box>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!canAdd} onClick={add}>{forBody ? 'Set' : 'Add'}</Button>
      </DialogActions>
    </Dialog>
  );
}
