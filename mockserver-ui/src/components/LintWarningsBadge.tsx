import { useState } from 'react';
import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import type { ConnectionParams } from '../hooks/useConnectionParams';
import LintDialog from './LintDialog';

/**
 * The `x-mockserver-lint-warnings` count from creating an expectation, as a chip that opens the
 * Lint Expectations dialog. Renders nothing for a count of 0.
 */
export default function LintWarningsBadge({ count, connectionParams }: { count: number; connectionParams: ConnectionParams }) {
  const [open, setOpen] = useState(false);
  if (count <= 0) return null;
  return (
    <>
      <Tooltip describeChild title="The expectation was stored, but lint found problems that may stop it matching. Click to see them.">
        <Chip
          size="small"
          color="warning"
          icon={<WarningAmberIcon />}
          label={`${count} Lint ${count === 1 ? 'Warning' : 'Warnings'}`}
          onClick={() => setOpen(true)}
          data-testid="lint-warnings-badge"
        />
      </Tooltip>
      {open && <LintDialog open onClose={() => setOpen(false)} connectionParams={connectionParams} />}
    </>
  );
}
