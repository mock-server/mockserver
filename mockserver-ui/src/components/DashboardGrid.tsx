import Box from '@mui/material/Box';
import { useDashboardStore } from '../store';
import LogPanel from './LogPanel';
import ExpectationPanel from './ExpectationPanel';
import RequestPanel from './RequestPanel';

export default function DashboardGrid() {
  const recordedRequests = useDashboardStore((s) => s.recordedRequests);
  const proxiedRequests = useDashboardStore((s) => s.proxiedRequests);
  const receivedSearch = useDashboardStore((s) => s.receivedSearch);
  const proxiedSearch = useDashboardStore((s) => s.proxiedSearch);
  const setReceivedSearch = useDashboardStore((s) => s.setReceivedSearch);
  const setProxiedSearch = useDashboardStore((s) => s.setProxiedSearch);

  return (
    <Box
      sx={{
        flex: 1,
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gridTemplateRows: '1fr 1fr',
        gap: 1,
        p: 1,
        overflow: 'hidden',
        minHeight: 0,
      }}
    >
      <Box sx={{ minHeight: 0, overflow: 'hidden' }}>
        <LogPanel />
      </Box>
      <Box sx={{ minHeight: 0, overflow: 'hidden' }}>
        <ExpectationPanel />
      </Box>
      <Box sx={{ minHeight: 0, overflow: 'hidden' }}>
        <RequestPanel
          title="Received Requests"
          items={recordedRequests}
          searchValue={receivedSearch}
          onSearchChange={setReceivedSearch}
        />
      </Box>
      <Box sx={{ minHeight: 0, overflow: 'hidden' }}>
        <RequestPanel
          title="Proxied Requests"
          items={proxiedRequests}
          searchValue={proxiedSearch}
          onSearchChange={setProxiedSearch}
        />
      </Box>
    </Box>
  );
}
