import { useEffect, useRef, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Switch from '@mui/material/Switch';
import FormControlLabel from '@mui/material/FormControlLabel';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import {
  describeTarget,
  isCrossOrigin,
  probeTarget,
  urlConnectionParams,
  validateConnection,
  type WorkspaceConnection,
} from '../lib/connectionTarget';

/**
 * Edit which MockServer one workspace targets. Saving checks the target answers the
 * dashboard's control-plane calls first, and says why when it does not; the user can still
 * save an unreachable target (for a server not started yet) with "Save anyway".
 */
export default function WorkspaceTargetDialog({
  workspaceName,
  connection,
  onSave,
  onClose,
}: {
  workspaceName: string;
  connection: WorkspaceConnection | null;
  onSave: (connection: WorkspaceConnection | null) => void;
  onClose: () => void;
}) {
  const [urlTarget] = useState(urlConnectionParams);
  const start = connection ?? { ...urlTarget, basePath: urlTarget.basePath ?? '' };
  const [host, setHost] = useState(start.host);
  const [port, setPort] = useState(start.port);
  const [secure, setSecure] = useState(start.secure);
  const [basePath, setBasePath] = useState(start.basePath);
  const [checking, setChecking] = useState(false);
  const [probeFailure, setProbeFailure] = useState<string | null>(null);
  // A check still running when the dialog closes must not save the target afterwards.
  const openRef = useRef(true);
  useEffect(() => {
    openRef.current = true;
    return () => { openRef.current = false; };
  }, []);
  const close = () => {
    openRef.current = false;
    onClose();
  };

  const validation = validateConnection({ host, port, secure, basePath });
  const fieldError = (field: 'host' | 'port' | 'basePath') =>
    !validation.ok && validation.field === field ? validation.message : undefined;
  const crossOrigin = validation.ok && isCrossOrigin(validation.value);

  const edit = (apply: () => void) => {
    apply();
    setProbeFailure(null);
  };

  const save = async () => {
    if (!validation.ok) return;
    if (probeFailure !== null) {
      onSave(validation.value);
      return;
    }
    setChecking(true);
    const result = await probeTarget(validation.value);
    if (!openRef.current) return;
    setChecking(false);
    if (result.ok) {
      onSave(validation.value);
    } else {
      setProbeFailure(result.message);
    }
  };

  return (
    <Dialog open onClose={close} maxWidth="sm" fullWidth aria-labelledby="workspace-target-title">
      <DialogTitle id="workspace-target-title">MockServer for {workspaceName}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Every view, action and live update in this workspace goes to this server. Changing it
          clears what the workspace showed from the previous one. The other workspaces keep their
          own targets, and breakpoints set on a server stay active while any workspace targets it.
        </Typography>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 1 }}>
          <TextField
            size="small"
            label="Host"
            value={host}
            onChange={(e) => edit(() => setHost(e.target.value))}
            error={!!fieldError('host')}
            helperText={fieldError('host')}
            sx={{ flex: 2, minWidth: 200 }}
            autoFocus
          />
          <TextField
            size="small"
            label="Port"
            value={port}
            onChange={(e) => edit(() => setPort(e.target.value))}
            error={!!fieldError('port')}
            helperText={fieldError('port')}
            sx={{ flex: 1, minWidth: 100 }}
          />
        </Box>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center', mt: 1.5 }}>
          <TextField
            size="small"
            label="Base path (optional)"
            placeholder="/mock"
            value={basePath}
            onChange={(e) => edit(() => setBasePath(e.target.value))}
            error={!!fieldError('basePath')}
            helperText={fieldError('basePath')}
            sx={{ flex: 1, minWidth: 200 }}
          />
          <FormControlLabel
            control={<Switch checked={secure} onChange={(e) => edit(() => setSecure(e.target.checked))} />}
            label="Use https"
          />
        </Box>
        {crossOrigin && probeFailure === null && (
          <Alert severity="info" sx={{ mt: 2 }}>
            This is a different origin from the dashboard, so the browser applies CORS to every call.
            MockServer answers CORS for its control plane unless <code>corsAllowOrigin</code> excludes
            this origin.
          </Alert>
        )}
        {probeFailure !== null && (
          <Alert severity="warning" sx={{ mt: 2 }} data-testid="workspace-target-probe-failure">
            {probeFailure}
          </Alert>
        )}
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
          The dashboard URL targets {describeTarget(urlTarget)}.
        </Typography>
      </DialogContent>
      <DialogActions>
        {connection !== null && (
          <Button onClick={() => onSave(null)} sx={{ mr: 'auto' }}>
            Use the dashboard URL&apos;s server
          </Button>
        )}
        <Button onClick={close}>Cancel</Button>
        <Button variant="contained" onClick={() => { void save(); }} disabled={!validation.ok || checking}>
          {checking ? 'Checking…' : probeFailure !== null ? 'Save anyway' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
