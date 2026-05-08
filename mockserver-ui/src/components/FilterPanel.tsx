import { useCallback, useEffect, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Collapse from '@mui/material/Collapse';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutlined';
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutlined';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import FilterListIcon from '@mui/icons-material/FilterList';
import type { KeyToMultiValue, KeyToValue, RequestFilter } from '../types';
import { useDashboardStore } from '../store';

const HTTP_METHODS = ['', 'CONNECT', 'DELETE', 'GET', 'HEAD', 'OPTIONS', 'PATCH', 'POST', 'PUT', 'TRACE'];

interface MultiValueFieldProps {
  label: string;
  items: KeyToMultiValue[];
  onChange: (items: KeyToMultiValue[]) => void;
  disabled: boolean;
}

function MultiValueField({ label, items, onChange, disabled }: MultiValueFieldProps) {
  const addRow = () => onChange([...items, { name: '', values: [''] }]);
  const removeRow = (i: number) => onChange(items.filter((_, idx) => idx !== i));
  const setName = (i: number, name: string) =>
    onChange(items.map((it, idx) => (idx === i ? { ...it, name } : it)));
  const setValue = (i: number, vi: number, val: string) =>
    onChange(
      items.map((it, idx) =>
        idx === i ? { ...it, values: it.values.map((v, j) => (j === vi ? val : v)) } : it,
      ),
    );
  const addValue = (i: number) =>
    onChange(items.map((it, idx) => (idx === i ? { ...it, values: [...it.values, ''] } : it)));
  const removeValue = (i: number, vi: number) =>
    onChange(
      items.map((it, idx) =>
        idx === i ? { ...it, values: it.values.filter((_, j) => j !== vi) } : it,
      ),
    );

  return (
    <Box sx={{ mb: 1 }}>
      <Typography variant="caption" color="primary" sx={{ mb: 0.5, display: 'block' }}>
        {label}
      </Typography>
      {items.map((item, i) => (
        <Box key={i} sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', mb: 0.5, flexWrap: 'wrap' }}>
          <TextField
            size="small"
            label="Name"
            value={item.name}
            onChange={(e) => setName(i, e.target.value)}
            disabled={disabled}
            sx={{ width: 140 }}
          />
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', flex: 1 }}>
            {item.values.map((val, vi) => (
              <Box key={vi} sx={{ display: 'flex', alignItems: 'center' }}>
                <TextField
                  size="small"
                  label="Value"
                  value={val}
                  onChange={(e) => setValue(i, vi, e.target.value)}
                  disabled={disabled}
                  sx={{ width: 120 }}
                />
                {vi > 0 && (
                  <IconButton size="small" disabled={disabled} onClick={() => removeValue(i, vi)}>
                    <RemoveCircleOutlineIcon fontSize="small" />
                  </IconButton>
                )}
              </Box>
            ))}
            <IconButton size="small" disabled={disabled} onClick={() => addValue(i)}>
              <AddCircleOutlineIcon fontSize="small" />
            </IconButton>
          </Box>
          {i > 0 && (
            <IconButton size="small" disabled={disabled} onClick={() => removeRow(i)}>
              <RemoveCircleOutlineIcon fontSize="small" />
            </IconButton>
          )}
        </Box>
      ))}
      <IconButton size="small" disabled={disabled} onClick={addRow}>
        <AddCircleOutlineIcon fontSize="small" />
      </IconButton>
    </Box>
  );
}

interface SingleValueFieldProps {
  label: string;
  items: KeyToValue[];
  onChange: (items: KeyToValue[]) => void;
  disabled: boolean;
}

function SingleValueField({ label, items, onChange, disabled }: SingleValueFieldProps) {
  const addRow = () => onChange([...items, { name: '', value: '' }]);
  const removeRow = (i: number) => onChange(items.filter((_, idx) => idx !== i));
  const setField = (i: number, field: 'name' | 'value', val: string) =>
    onChange(items.map((it, idx) => (idx === i ? { ...it, [field]: val } : it)));

  return (
    <Box sx={{ mb: 1 }}>
      <Typography variant="caption" color="primary" sx={{ mb: 0.5, display: 'block' }}>
        {label}
      </Typography>
      {items.map((item, i) => (
        <Box key={i} sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 0.5 }}>
          <TextField
            size="small"
            label="Name"
            value={item.name}
            onChange={(e) => setField(i, 'name', e.target.value)}
            disabled={disabled}
            sx={{ width: 140 }}
          />
          <TextField
            size="small"
            label="Value"
            value={item.value}
            onChange={(e) => setField(i, 'value', e.target.value)}
            disabled={disabled}
            sx={{ width: 180 }}
          />
          {i > 0 && (
            <IconButton size="small" disabled={disabled} onClick={() => removeRow(i)}>
              <RemoveCircleOutlineIcon fontSize="small" />
            </IconButton>
          )}
        </Box>
      ))}
      <IconButton size="small" disabled={disabled} onClick={addRow}>
        <AddCircleOutlineIcon fontSize="small" />
      </IconButton>
    </Box>
  );
}

interface FilterPanelProps {
  onFilterChange: (filter: RequestFilter) => void;
}

export default function FilterPanel({ onFilterChange }: FilterPanelProps) {
  const expanded = useDashboardStore((s) => s.filterExpanded);
  const toggleExpanded = useDashboardStore((s) => s.toggleFilterExpanded);
  const filterEnabled = useDashboardStore((s) => s.filterEnabled);
  const setFilterEnabled = useDashboardStore((s) => s.setFilterEnabled);

  const [method, setMethod] = useState('');
  const [path, setPath] = useState('');
  const [secure, setSecure] = useState(false);
  const [keepAlive, setKeepAlive] = useState(false);
  const [headers, setHeaders] = useState<KeyToMultiValue[]>([{ name: '', values: [''] }]);
  const [queryParams, setQueryParams] = useState<KeyToMultiValue[]>([{ name: '', values: [''] }]);
  const [cookies, setCookies] = useState<KeyToValue[]>([{ name: '', value: '' }]);

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const emitFilter = useCallback(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      if (!filterEnabled) {
        onFilterChange({});
        return;
      }
      const filter: RequestFilter = {};
      if (method) filter.method = method;
      if (path) filter.path = path;
      if (keepAlive) filter.keepAlive = true;
      if (secure) filter.secure = true;

      const validHeaders = headers.filter(
        (h) => h.name && h.values.some((v) => v),
      );
      if (validHeaders.length > 0) filter.headers = validHeaders;

      const validParams = queryParams.filter(
        (p) => p.name && p.values.some((v) => v),
      );
      if (validParams.length > 0) filter.queryStringParameters = validParams;

      const validCookies = cookies.filter((c) => c.name && c.value);
      if (validCookies.length > 0) filter.cookies = validCookies;

      onFilterChange(filter);
    }, 300);
  }, [filterEnabled, method, path, secure, keepAlive, headers, queryParams, cookies, onFilterChange]);

  useEffect(() => {
    emitFilter();
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [emitFilter]);

  const disabled = !filterEnabled;

  return (
    <Card variant="outlined" sx={{ mx: 1, mt: 1, flexShrink: 0 }}>
      <Box
        onClick={toggleExpanded}
        sx={{
          display: 'flex',
          alignItems: 'center',
          px: 2,
          py: 1,
          cursor: 'pointer',
          bgcolor: filterEnabled ? 'primary.main' : 'action.hover',
          color: filterEnabled ? 'primary.contrastText' : 'text.primary',
          '&:hover': { opacity: 0.9 },
        }}
      >
        <FilterListIcon sx={{ mr: 1 }} fontSize="small" />
        <Typography variant="subtitle2" sx={{ flex: 1 }}>
          Request Filter
        </Typography>
        {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
      </Box>
      <Collapse in={expanded}>
        <CardContent>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
            <Box sx={{ minWidth: 100 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={filterEnabled}
                    onChange={(e) => setFilterEnabled(e.target.checked)}
                  />
                }
                label="Enabled"
              />
            </Box>
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', flex: 1 }}>
              <TextField
                select
                size="small"
                label="Method"
                value={method}
                onChange={(e) => setMethod(e.target.value)}
                disabled={disabled}
                sx={{ width: 130 }}
              >
                {HTTP_METHODS.map((m) => (
                  <MenuItem key={m} value={m}>
                    {m || '(any)'}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                size="small"
                label="Path"
                value={path}
                onChange={(e) => setPath(e.target.value)}
                disabled={disabled}
                sx={{ width: 200 }}
              />
              <FormControlLabel
                control={
                  <Switch
                    size="small"
                    checked={secure}
                    onChange={(e) => setSecure(e.target.checked)}
                    disabled={disabled}
                  />
                }
                label="Secure"
              />
              <FormControlLabel
                control={
                  <Switch
                    size="small"
                    checked={keepAlive}
                    onChange={(e) => setKeepAlive(e.target.checked)}
                    disabled={disabled}
                  />
                }
                label="Keep-Alive"
              />
            </Box>
          </Box>
          <Box sx={{ mt: 2 }}>
            <MultiValueField label="Headers" items={headers} onChange={setHeaders} disabled={disabled} />
            <SingleValueField label="Cookies" items={cookies} onChange={setCookies} disabled={disabled} />
            <MultiValueField label="Query Parameters" items={queryParams} onChange={setQueryParams} disabled={disabled} />
          </Box>
        </CardContent>
      </Collapse>
    </Card>
  );
}
