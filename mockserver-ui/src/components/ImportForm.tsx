/**
 * ImportForm — bulk-import expectations from Expectation JSON, OpenAPI, WSDL,
 * HAR (HTTP Archive), or a Postman collection. Renders as the entire form body
 * when kind === 'import' in the Composer. Each format maps to a MockServer
 * import endpoint (HAR and Postman share PUT /mockserver/import?format=...).
 */
import { useState, useCallback, useRef } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import RadioGroup from '@mui/material/RadioGroup';
import FormControlLabel from '@mui/material/FormControlLabel';
import Radio from '@mui/material/Radio';
import Button from '@mui/material/Button';
import Snackbar from '@mui/material/Snackbar';
import CircularProgress from '@mui/material/CircularProgress';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import { importExpectationJson, importCollection, importRecording, type RecordingImportResult } from '../lib/importMocks';
import { importOpenApi, type OpenApiLoadProblem } from '../lib/openapiImport';
import { importWsdl } from '../lib/wsdlImport';
import { humanizeError, type HumanError } from '../lib/errorMessage';
import { monospaceFontFamily } from '../theme';
import HumanErrorAlert from './HumanErrorAlert';
import OpenApiLoadProblemsAlert from './OpenApiLoadProblemsAlert';
import LintWarningsBadge from './LintWarningsBadge';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type ImportFormat = 'json' | 'openapi' | 'wsdl' | 'har' | 'postman' | 'recording';

type ImportSource = 'paste' | 'url' | 'file';

interface FormatMeta {
  value: ImportFormat;
  label: string;
  description: string;
  /** Whether the URL source is supported for this format. */
  urlSupported: boolean;
}

const FORMATS: FormatMeta[] = [
  { value: 'json', label: 'Expectation JSON', description: 'A single expectation object or a JSON array of expectations.', urlSupported: false },
  { value: 'openapi', label: 'OpenAPI', description: 'An OpenAPI v3 spec (JSON/YAML) or a URL pointing to one.', urlSupported: true },
  { value: 'wsdl', label: 'WSDL / SOAP', description: 'A WSDL 1.1 document. SOAP operations are converted to expectations.', urlSupported: false },
  { value: 'har', label: 'HAR (HTTP Archive)', description: 'A HAR JSON file captured from browser DevTools or a proxy.', urlSupported: false },
  { value: 'postman', label: 'Postman collection', description: 'A Postman collection (v2.x) JSON export; requests are converted to expectations.', urlSupported: false },
  { value: 'recording', label: 'Recording (NDJSON)', description: 'A persisted NDJSON archive of recorded request/response traffic (disk-offload capture); reloaded into the event log. Leave blank and use "Reload From Server Disk" to read the server\'s configured archive.', urlSupported: false },
];

/** Build a user-facing message from a recording reload result. */
function recordingMessage(result: RecordingImportResult): string {
  const base =
    result.count > 0
      ? `Reloaded ${result.count} recorded request${result.count === 1 ? '' : 's'}`
      : 'No recorded requests reloaded';
  return result.skipped > 0
    ? `${base} (${result.skipped} malformed line${result.skipped === 1 ? '' : 's'} skipped)`
    : base;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export interface ImportFormProps {
  connectionParams: ConnectionParams;
}

export default function ImportForm({ connectionParams }: ImportFormProps) {
  const [format, setFormat] = useState<ImportFormat>('json');
  const [source, setSource] = useState<ImportSource>('paste');
  const [payload, setPayload] = useState('');
  const [urlValue, setUrlValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<HumanError | null>(null);
  const [snackMessage, setSnackMessage] = useState<string | null>(null);
  const [loadProblems, setLoadProblems] = useState<OpenApiLoadProblem[]>([]);
  // x-mockserver-lint-warnings from the last Expectation JSON import.
  const [lintWarnings, setLintWarnings] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const currentFormatMeta = FORMATS.find((f) => f.value === format)!;

  // When switching format, reset source to 'paste' if URL is not supported.
  const handleFormatChange = useCallback((newFormat: ImportFormat) => {
    setFormat(newFormat);
    setLoadProblems([]);
    const meta = FORMATS.find((f) => f.value === newFormat)!;
    if (!meta.urlSupported && source === 'url') {
      setSource('paste');
    }
    setError(null);
  }, [source]);

  const handleFileSelect = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setPayload(reader.result);
      }
    };
    reader.readAsText(file);
    // Reset the input so the same file can be re-selected.
    e.target.value = '';
  }, []);

  const handleImport = useCallback(async () => {
    setBusy(true);
    setError(null);
    setLoadProblems([]);
    setLintWarnings(0);
    try {
      const input = source === 'url' ? urlValue.trim() : payload.trim();
      if (!input) {
        throw new Error(source === 'url' ? 'Please enter a URL.' : 'Please provide content to import.');
      }

      // Recording archives reload into the event log (not into expectations), so
      // they report a reloaded-pair count + skipped-line count rather than a
      // created-expectation list.
      if (format === 'recording') {
        const result = await importRecording(connectionParams, input, { fromDisk: false });
        setSnackMessage(recordingMessage(result));
        setPayload('');
        setUrlValue('');
        return;
      }

      let created: unknown[];
      switch (format) {
        case 'json': {
          const imported = await importExpectationJson(connectionParams, input);
          created = imported.created;
          setLintWarnings(imported.lintWarnings);
          break;
        }
        case 'openapi': {
          const result = await importOpenApi(connectionParams, input);
          created = result.expectations;
          setLoadProblems(result.loadProblems);
          break;
        }
        case 'wsdl':
          created = await importWsdl(connectionParams, input);
          break;
        case 'har':
          created = await importCollection(connectionParams, input, 'har');
          break;
        case 'postman':
          created = await importCollection(connectionParams, input, 'postman');
          break;
      }
      const count = created.length;
      // The import helpers normalise an unparseable / non-array 2xx body to [],
      // so a count of 0 is ambiguous (it could be a genuinely empty result or an
      // unexpected success shape). Only report a number when we actually have
      // one; otherwise confirm success without a misleading "Imported 0".
      setSnackMessage(
        count > 0
          ? `Imported ${count} expectation${count === 1 ? '' : 's'}`
          : 'Import succeeded',
      );
      // Clear the payload after a successful import so the user can import more.
      setPayload('');
      setUrlValue('');
    } catch (e) {
      setError(humanizeError(e));
    } finally {
      setBusy(false);
    }
  }, [format, source, payload, urlValue, connectionParams]);

  // Reload the recording archive the server persisted to disk (source=disk),
  // no request body required.
  const handleReloadFromDisk = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await importRecording(connectionParams, null, { fromDisk: true });
      setSnackMessage(recordingMessage(result));
    } catch (e) {
      setError(humanizeError(e));
    } finally {
      setBusy(false);
    }
  }, [connectionParams]);

  return (
    <>
      {/* Format picker */}
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2" sx={{ mb: 1, textTransform: 'uppercase', letterSpacing: 0.5, color: 'text.secondary' }}>
          Format
        </Typography>
        <RadioGroup
          value={format}
          onChange={(e) => handleFormatChange(e.target.value as ImportFormat)}
        >
          {FORMATS.map((f) => (
            <FormControlLabel
              key={f.value}
              value={f.value}
              // Zero vertical padding on the radio so its circle top-aligns with the title row
              // rather than centring on the whole title+description block.
              control={<Radio size="small" sx={{ py: 0 }} />}
              label={
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {f.label}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {f.description}
                  </Typography>
                </Box>
              }
              sx={{ alignItems: 'flex-start', mb: 0.5 }}
            />
          ))}
        </RadioGroup>
      </Paper>

      {/* Source picker + content input */}
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2" sx={{ mb: 1, textTransform: 'uppercase', letterSpacing: 0.5, color: 'text.secondary' }}>
          Source
        </Typography>
        <RadioGroup
          row
          value={source}
          onChange={(e) => {
            setSource(e.target.value as ImportSource);
            setError(null);
          }}
        >
          <FormControlLabel
            value="paste"
            control={<Radio size="small" />}
            label={<Typography variant="body2">Paste</Typography>}
          />
          {currentFormatMeta.urlSupported && (
            <FormControlLabel
              value="url"
              control={<Radio size="small" />}
              label={<Typography variant="body2">URL</Typography>}
            />
          )}
          <FormControlLabel
            value="file"
            control={<Radio size="small" />}
            label={<Typography variant="body2">File</Typography>}
          />
        </RadioGroup>

        <Box sx={{ mt: 1.5 }}>
          {source === 'url' && (
            <TextField
              label="Spec URL"
              size="small"
              fullWidth
              value={urlValue}
              onChange={(e) => setUrlValue(e.target.value)}
              placeholder="https://petstore3.swagger.io/api/v3/openapi.json"
              slotProps={{ input: { sx: { fontFamily: monospaceFontFamily, fontSize: '0.78rem' } } }}
            />
          )}
          {(source === 'paste' || source === 'file') && (
            <>
              {source === 'file' && (
                <Box sx={{ mb: 1 }}>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    Choose file
                  </Button>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".json,.yaml,.yml,.xml,.wsdl,.har,.ndjson"
                    style={{ display: 'none' }}
                    onChange={handleFileSelect}
                    data-testid="import-file-input"
                  />
                  <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                    The file contents will be loaded into the text area below.
                  </Typography>
                </Box>
              )}
              <TextField
                label={`${currentFormatMeta.label} content`}
                multiline
                minRows={8}
                maxRows={24}
                fullWidth
                value={payload}
                onChange={(e) => setPayload(e.target.value)}
                placeholder={
                  format === 'json'
                    ? '[\n  {\n    "httpRequest": { "method": "GET", "path": "/api/test" },\n    "httpResponse": { "statusCode": 200, "body": "{}" }\n  }\n]'
                    : format === 'openapi'
                      ? '{ "openapi": "3.0.0", "info": { "title": "Petstore", "version": "1.0" }, ... }'
                      : format === 'wsdl'
                        ? '<definitions xmlns="http://schemas.xmlsoap.org/wsdl/" ...>'
                        : format === 'postman'
                          ? '{ "info": { "name": "My API", ... }, "item": [ ... ] }'
                          : format === 'recording'
                            ? '{"httpRequest":{...},"httpResponse":{...}}\n{"httpRequest":{...},"httpResponse":{...}}'
                            : '{ "log": { "entries": [ ... ] } }'
                }
                slotProps={{ input: { sx: { fontFamily: monospaceFontFamily, fontSize: '0.78rem' } } }}
              />
            </>
          )}
        </Box>
      </Paper>

      {/* Import button */}
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <Button
            variant="contained"
            size="small"
            onClick={() => void handleImport()}
            disabled={busy || (source === 'url' ? !urlValue.trim() : !payload.trim())}
            startIcon={busy ? <CircularProgress size={16} color="inherit" /> : undefined}
          >
            {busy ? 'Importing...' : format === 'recording' ? 'Reload' : 'Import'}
          </Button>
          {format === 'recording' && (
            <Button
              variant="outlined"
              size="small"
              onClick={() => void handleReloadFromDisk()}
              disabled={busy}
            >
              Reload From Server Disk
            </Button>
          )}
          <Typography variant="caption" color="text.secondary">
            {format === 'recording'
              ? 'The recorded request/response traffic will be reloaded into the event log.'
              : `${currentFormatMeta.label} will be sent to MockServer and converted to expectations.`}
          </Typography>
        </Box>
      </Paper>

      {lintWarnings > 0 && (
        <Box>
          <LintWarningsBadge count={lintWarnings} connectionParams={connectionParams} />
        </Box>
      )}

      {error && (
        <HumanErrorAlert error={error} variant="outlined" />
      )}

      <OpenApiLoadProblemsAlert problems={loadProblems} />

      <Snackbar
        open={snackMessage !== null}
        autoHideDuration={4000}
        onClose={() => setSnackMessage(null)}
        message={snackMessage ?? ''}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </>
  );
}
