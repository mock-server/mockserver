import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import { describeTarget, pageOriginDashboardUrl, resolveUrlConnection, type UrlTargetField } from '../lib/connectionTarget';

const FIELD_NAMES: Record<UrlTargetField, string> = { host: 'host', port: 'port', basePath: 'base path' };

function listOf(names: string[]): string {
  return names.length <= 1 ? names.join('') : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}

/**
 * Says when the dashboard link points the dashboard at a server on another host, and which
 * `?host=`, `?port=` or `?basePath=` values in it were invalid and so ignored, so a link never
 * silently retargets the dashboard.
 */
export default function UrlTargetNotice() {
  const [{ params, rejected, page, foreignHost }] = useState(resolveUrlConnection);
  const [dismissed, setDismissed] = useState(false);
  const fields = listOf(rejected.map((field) => FIELD_NAMES[field]));
  return (
    <>
      {foreignHost && (
        <Alert
          severity="warning"
          sx={{ mx: 1, mt: 1, flexShrink: 0 }}
          data-testid="url-target-foreign-host"
          action={(
            <Button color="inherit" size="small" href={pageOriginDashboardUrl()}>
              Use {describeTarget(page)}
            </Button>
          )}
        >
          This dashboard link points at {describeTarget(params)}, not the server that served this page. Workspaces
          that follow the link show that server&apos;s data, and what you create or upload in them is sent there.
        </Alert>
      )}
      {rejected.length > 0 && !dismissed && (
        <Alert
          severity="warning"
          onClose={() => setDismissed(true)}
          sx={{ mx: 1, mt: 1, flexShrink: 0 }}
          data-testid="url-target-notice"
        >
          The {fields} in this dashboard link {rejected.length === 1 ? 'is' : 'are'} not valid, so the dashboard ignored{' '}
          {rejected.length === 1 ? 'it' : 'them'}; workspaces that follow the link use {describeTarget(params)}.
        </Alert>
      )}
    </>
  );
}
