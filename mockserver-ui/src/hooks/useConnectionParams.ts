import { useMemo } from 'react';
import { useDashboardStore } from '../store';
import { effectiveConnection, urlConnectionParams, type ConnectionParams } from '../lib/connectionTarget';

export type { ConnectionParams } from '../lib/connectionTarget';

/**
 * The MockServer instance every REST call and WebSocket of the dashboard targets: the active
 * workspace's own target when it has one, else the one named by the dashboard URL. Every
 * consumer reads it here, so a workspace switch retargets all of them at once.
 */
export function useConnectionParams(): ConnectionParams {
  const base = useMemo(() => urlConnectionParams(), []);
  const override = useDashboardStore((s) => s.connection);
  return useMemo(() => effectiveConnection(override, base), [override, base]);
}
