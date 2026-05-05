import { useMemo } from 'react';
import Typography from '@mui/material/Typography';
import type { JsonListItem } from '../types';
import Panel from './Panel';
import JsonListItemComponent from './JsonListItem';

interface RequestPanelProps {
  title: string;
  items: JsonListItem[];
  searchValue: string;
  onSearchChange: (value: string) => void;
}

function matchesSearch(item: JsonListItem, term: string): boolean {
  return JSON.stringify(item).toLowerCase().includes(term.toLowerCase());
}

export default function RequestPanel({
  title,
  items,
  searchValue,
  onSearchChange,
}: RequestPanelProps) {
  const filtered = useMemo(
    () => (searchValue ? items.filter((e) => matchesSearch(e, searchValue)) : items),
    [items, searchValue],
  );

  return (
    <Panel
      title={title}
      count={items.length}
      searchValue={searchValue}
      onSearchChange={onSearchChange}
    >
      {filtered.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
          {items.length === 0 ? 'No requests' : 'No matching requests'}
        </Typography>
      ) : (
        filtered.map((item, index) => (
          <JsonListItemComponent key={item.key} item={item} index={items.length - index} />
        ))
      )}
    </Panel>
  );
}
