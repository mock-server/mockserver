import { useRef, useEffect, type ReactNode } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Chip from '@mui/material/Chip';
import InputAdornment from '@mui/material/InputAdornment';
import SearchIcon from '@mui/icons-material/Search';
import { useDashboardStore } from '../store';

interface PanelProps {
  title: string;
  count: number;
  searchValue: string;
  onSearchChange: (value: string) => void;
  searchInputRef?: React.RefObject<HTMLInputElement | null>;
  children: ReactNode;
}

export default function Panel({
  title,
  count,
  searchValue,
  onSearchChange,
  searchInputRef,
  children,
}: PanelProps) {
  const autoScroll = useDashboardStore((s) => s.autoScroll);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (autoScroll && scrollRef.current) {
      scrollRef.current.scrollTop = 0;
    }
  }, [count, autoScroll]);

  return (
    <Paper
      variant="outlined"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        overflow: 'hidden',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 1,
          py: 0.25,
          borderBottom: 1,
          borderColor: 'divider',
          flexShrink: 0,
        }}
      >
        <Typography variant="subtitle2" sx={{ fontWeight: 600, fontSize: '0.79rem' }}>
          {title}
        </Typography>
        {count > 0 && (
          <Chip label={count > 999 ? '999+' : count} color="primary" size="small" sx={{ height: 18, fontSize: '0.65rem', '& .MuiChip-label': { px: 0.75 } }} />
        )}
        <TextField
          id={`${title.toLowerCase().replace(/\s+/g, '-')}-search`}
          size="small"
          placeholder="Search..."
          value={searchValue}
          onChange={(e) => onSearchChange(e.target.value)}
          inputRef={searchInputRef}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
          sx={{ ml: 'auto', maxWidth: 200, '& .MuiInputBase-root': { height: 28, fontSize: '0.75rem' }, '& .MuiSvgIcon-root': { fontSize: '0.875rem' } }}
        />
      </Box>
      <Box
        ref={scrollRef}
        sx={{
          flex: 1,
          overflowY: 'auto',
          bgcolor: 'background.default',
          p: 0.5,
        }}
      >
        {children}
      </Box>
    </Paper>
  );
}
