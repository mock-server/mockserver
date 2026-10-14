import { useMemo, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import {
  importOpenApi,
  discoverNamedExamples,
  type ExampleSelections,
  type OpenApiLoadProblem,
} from '../lib/openapiImport';
import { humanizeError, type HumanError } from '../lib/errorMessage';
import HumanErrorAlert from './HumanErrorAlert';
import OpenApiLoadProblemsAlert from './OpenApiLoadProblemsAlert';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import { trackFeature } from '../lib/analytics';

interface OpenApiImportDialogProps {
  open: boolean;
  onClose: () => void;
  connectionParams: ConnectionParams;
}

/** Sentinel value for the "use the spec default" option in an example picker. */
const DEFAULT_EXAMPLE = '';

/**
 * Dialog to import an OpenAPI v3 spec (URL or inline JSON/YAML) via
 * PUT /mockserver/openapi, generating one expectation per operation. Import is
 * incremental — re-importing the same spec updates in place and prunes removed
 * operations.
 *
 * When an inline JSON spec declares multiple named examples for an operation's
 * response body, a per-operation picker is shown so the generated mock can use a
 * chosen example. Leaving a picker on "Default" sends no override for that
 * operation, preserving the server's default example selection.
 */
export default function OpenApiImportDialog({ open, onClose, connectionParams }: OpenApiImportDialogProps) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const [spec, setSpec] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<HumanError | null>(null);
  const [createdCount, setCreatedCount] = useState<number | null>(null);
  const [loadProblems, setLoadProblems] = useState<OpenApiLoadProblem[]>([]);
  // operationId -> chosen exampleName ('' = spec default).
  const [chosenExamples, setChosenExamples] = useState<Record<string, string>>({});

  const operationExamples = useMemo(() => discoverNamedExamples(spec), [spec]);
  // The load problems are then the whole result, so no success message is shown.
  const noExpectationsCreated = createdCount === 0 && loadProblems.length > 0;

  // Editing the spec invalidates any prior example choices (operationIds may now
  // mean a different operation), so reset selections whenever the spec changes.
  const handleSpecChange = (next: string) => {
    setSpec(next);
    setChosenExamples({});
  };

  const handleImport = async () => {
    setBusy(true);
    setError(null);
    setCreatedCount(null);
    setLoadProblems([]);
    try {
      const selections: ExampleSelections = {};
      for (const op of operationExamples) {
        const chosen = chosenExamples[op.operationId];
        if (chosen && chosen !== DEFAULT_EXAMPLE) {
          selections[op.operationId] = { statusCode: op.statusCode, exampleName: chosen };
        }
      }
      const result = await importOpenApi(connectionParams, spec.trim(), selections);
      trackFeature('openapi_imported');
      setCreatedCount(result.expectations.length);
      setLoadProblems(result.loadProblems);
    } catch (e) {
      setError(humanizeError(e));
    } finally {
      setBusy(false);
    }
  };

  const handleClose = () => {
    setSpec('');
    setError(null);
    setCreatedCount(null);
    setLoadProblems([]);
    setChosenExamples({});
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth fullScreen={fullScreen} aria-labelledby="openapi-dialog-title">
      <DialogTitle id="openapi-dialog-title">Import OpenAPI</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Paste an OpenAPI v3 spec (JSON or YAML) or a spec URL. MockServer generates one
          expectation per operation. Re-importing the same spec updates the expectations in place
          and prunes operations that have been removed.
        </Typography>
        <TextField
          value={spec}
          onChange={(e) => handleSpecChange(e.target.value)}
          label="OpenAPI spec or URL"
          multiline
          minRows={10}
          fullWidth
          spellCheck={false}
        />
        {operationExamples.length > 0 && (
          <Box sx={{ mt: 2 }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Named Examples
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Some operations declare multiple named response examples. Pick one to use for the
              generated mock, or leave it as the spec default.
            </Typography>
            {operationExamples.map((op) => (
              <TextField
                key={op.operationId}
                label={`${op.operationId} (${op.statusCode})`}
                select
                fullWidth
                size="small"
                sx={{ mb: 1 }}
                value={chosenExamples[op.operationId] ?? DEFAULT_EXAMPLE}
                onChange={(e) =>
                  setChosenExamples((prev) => ({ ...prev, [op.operationId]: e.target.value }))
                }
              >
                <MenuItem value={DEFAULT_EXAMPLE}>Default</MenuItem>
                {op.exampleNames.map((name) => (
                  <MenuItem key={name} value={name}>
                    {name}
                  </MenuItem>
                ))}
              </TextField>
            ))}
          </Box>
        )}
        {error !== null && (
          <HumanErrorAlert error={error} sx={{ mt: 1 }} />
        )}
        {createdCount !== null && !noExpectationsCreated && (
          <Alert severity="success" sx={{ mt: 1 }}>
            Imported {createdCount} expectation{createdCount === 1 ? '' : 's'}.
          </Alert>
        )}
        <OpenApiLoadProblemsAlert problems={loadProblems} noExpectationsCreated={noExpectationsCreated} sx={{ mt: 1 }} />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Close</Button>
        <Button
          variant="contained"
          onClick={() => void handleImport()}
          disabled={busy || spec.trim().length === 0}
        >
          {busy ? 'Importing…' : 'Import'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
