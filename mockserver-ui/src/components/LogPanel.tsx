import { useRef, useMemo } from 'react';
import Typography from '@mui/material/Typography';
import { useDashboardStore } from '../store';
import { isLogGroup } from '../types';
import type { LogMessage } from '../types';
import Panel from './Panel';
import LogEntry from './LogEntry';
import LogGroup from './LogGroup';

function matchesSearch(message: LogMessage, term: string): boolean {
  const lower = term.toLowerCase();
  const json = JSON.stringify(message).toLowerCase();
  return json.includes(lower);
}

export default function LogPanel() {
  const logMessages = useDashboardStore((s) => s.logMessages);
  const search = useDashboardStore((s) => s.logSearch);
  const setSearch = useDashboardStore((s) => s.setLogSearch);
  const searchRef = useRef<HTMLInputElement>(null);

  const filtered = useMemo(
    () => (search ? logMessages.filter((m) => matchesSearch(m, search)) : logMessages),
    [logMessages, search],
  );

  return (
    <Panel
      title="Log Messages"
      count={logMessages.length}
      searchValue={search}
      onSearchChange={setSearch}
      searchInputRef={searchRef}
    >
      {filtered.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
          {logMessages.length === 0 ? 'No log messages' : 'No matching log messages'}
        </Typography>
      ) : (
        filtered.map((message) =>
          isLogGroup(message) ? (
            <LogGroup key={message.key} group={message} />
          ) : (
            <LogEntry key={message.key} entry={message.value} divider collapsible />
          ),
        )
      )}
    </Panel>
  );
}
