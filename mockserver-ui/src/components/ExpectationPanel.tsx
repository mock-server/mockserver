import { useMemo } from 'react';
import Typography from '@mui/material/Typography';
import { useDashboardStore } from '../store';
import type { JsonListItem } from '../types';
import Panel from './Panel';
import JsonListItemComponent from './JsonListItem';

function matchesSearch(item: JsonListItem, term: string): boolean {
  return JSON.stringify(item).toLowerCase().includes(term.toLowerCase());
}

export default function ExpectationPanel() {
  const expectations = useDashboardStore((s) => s.activeExpectations);
  const search = useDashboardStore((s) => s.expectationSearch);
  const setSearch = useDashboardStore((s) => s.setExpectationSearch);

  const filtered = useMemo(
    () => (search ? expectations.filter((e) => matchesSearch(e, search)) : expectations),
    [expectations, search],
  );

  return (
    <Panel
      title="Active Expectations"
      count={expectations.length}
      searchValue={search}
      onSearchChange={setSearch}
    >
      {filtered.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
          {expectations.length === 0 ? 'No active expectations' : 'No matching expectations'}
        </Typography>
      ) : (
        filtered.map((item, index) => (
          <JsonListItemComponent key={item.key} item={item} index={index + 1} />
        ))
      )}
    </Panel>
  );
}
