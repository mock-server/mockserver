import JsonView from '@uiw/react-json-view';
import { darkTheme } from '@uiw/react-json-view/dark';
import { lightTheme } from '@uiw/react-json-view/light';
import Box from '@mui/material/Box';
import { useDashboardStore } from '../store';
import CopyButton from './CopyButton';

interface JsonViewerProps {
  data: Record<string, unknown> | unknown[];
  collapsed?: number;
  enableClipboard?: boolean;
}

export default function JsonViewer({
  data,
  collapsed = 1,
  enableClipboard = true,
}: JsonViewerProps) {
  const themeMode = useDashboardStore((s) => s.themeMode);

  return (
    <Box sx={{ position: 'relative', display: 'inline-block', width: '100%' }}>
      {enableClipboard && (
        <Box sx={{ position: 'absolute', top: 0, right: 0, zIndex: 1 }}>
          <CopyButton text={JSON.stringify(data, null, 2)} />
        </Box>
      )}
      <JsonView
        value={data as object}
        style={themeMode === 'dark' ? darkTheme : lightTheme}
        collapsed={collapsed}
        displayObjectSize={false}
        displayDataTypes={false}
        enableClipboard={enableClipboard}
      />
    </Box>
  );
}
