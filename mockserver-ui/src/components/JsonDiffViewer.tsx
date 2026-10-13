import { useCallback, useId, useLayoutEffect, useRef } from 'react';
import { DiffEditor } from '@monaco-editor/react';
import type { DiffOnMount } from '@monaco-editor/react';
import type * as monaco from 'monaco-editor';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import { monospaceFontFamily } from '../theme';
import './monacoSetup';

export interface JsonDiffViewerProps {
  /** The "before" document (e.g. the existing expectation as stored). */
  original: string;
  /** The "after" document (e.g. the JSON that will be PUT). */
  modified: string;
  /** Monaco language id — defaults to `json`. */
  language?: string;
  /** Accessible label / field title rendered above the diff. */
  label?: string;
  ariaLabel?: string;
  /** Diff height in px. */
  height?: number;
  /**
   * When true (the default) the original pane sits to the left of the modified
   * pane (side-by-side). When false the diff is rendered inline.
   */
  sideBySide?: boolean;
}

/**
 * A reusable Monaco-backed read-only DIFF view that shows a clear before→after
 * comparison of two JSON documents. Used before creating a mock (capture flow)
 * and before updating an existing expectation (Composer) so the user sees
 * exactly what will change before they submit.
 */
export default function JsonDiffViewer({
  original,
  modified,
  language = 'json',
  label,
  ariaLabel,
  height = 260,
  sideBySide = true,
}: JsonDiffViewerProps) {
  const theme = useTheme();
  const summaryId = useId();
  const slug = summaryId.replace(/[^a-zA-Z0-9]/g, '');
  const isDark = theme.palette.mode === 'dark';
  const originalModelPath = `inmemory://mockserver/${slug}-original.json`;
  const modifiedModelPath = `inmemory://mockserver/${slug}-modified.json`;
  const name = ariaLabel ?? label;

  // @monaco-editor/react disposes the models while the diff widget still holds
  // them, which Monaco reports as an uncaught "TextModel got disposed before
  // DiffEditorWidget model got reset". So the wrapper keeps the models, and this
  // layout-effect cleanup (it runs before the wrapper's passive one) detaches
  // them from the live widget first and then disposes them.
  const editorRef = useRef<{ setModel?: (model: null) => void } | null>(null);
  const monacoRef = useRef<typeof monaco | null>(null);
  const handleMount: DiffOnMount = useCallback((editor, monacoInstance) => {
    editorRef.current = editor;
    monacoRef.current = monacoInstance as typeof monaco;
  }, []);
  useLayoutEffect(() => {
    return () => {
      const editor = editorRef.current;
      const monacoInstance = monacoRef.current;
      editorRef.current = null;
      if (!editor || !monacoInstance) return;
      editor.setModel?.(null);
      for (const path of [originalModelPath, modifiedModelPath]) {
        const model = monacoInstance.editor.getModel(monacoInstance.Uri.parse(path));
        if (model && !model.isDisposed()) model.dispose();
      }
    };
  }, [originalModelPath, modifiedModelPath]);

  return (
    <Box>
      {label && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
          {label}
        </Typography>
      )}
      <Box
        sx={{
          border: 1,
          borderColor: 'divider',
          borderRadius: 1,
          overflow: 'hidden',
        }}
        data-testid="json-diff-viewer"
        aria-label={ariaLabel ?? label}
      >
        <DiffEditor
          height={height}
          language={language}
          theme={isDark ? 'vs-dark' : 'vs'}
          original={original}
          modified={modified}
          originalModelPath={originalModelPath}
          modifiedModelPath={modifiedModelPath}
          keepCurrentOriginalModel
          keepCurrentModifiedModel
          onMount={handleMount}
          options={{
            readOnly: true,
            renderSideBySide: sideBySide,
            fontFamily: monospaceFontFamily,
            fontSize: 12.5,
            minimap: { enabled: false },
            lineNumbers: 'on',
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            automaticLayout: true,
            renderLineHighlight: 'none',
            scrollbar: { alwaysConsumeMouseWheel: false },
            tabFocusMode: true,
            ...(name ? { originalAriaLabel: `${name} (before)`, modifiedAriaLabel: `${name} (after)` } : {}),
          }}
        />
      </Box>
    </Box>
  );
}
