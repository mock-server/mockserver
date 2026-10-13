import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import Editor from '@monaco-editor/react';
import type { OnChange, OnMount, OnValidate } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import { monospaceFontFamily } from '../theme';
import './monacoSetup';

// The top-level `monaco-editor` package marks `languages.json` as a deprecated
// stub type, so the real json-language defaults API is described locally below.
interface JsonSchemaEntry {
  uri: string;
  fileMatch?: string[];
  schema?: object;
}
interface JsonLanguageDefaults {
  diagnosticsOptions: { schemas?: JsonSchemaEntry[] };
  setDiagnosticsOptions(options: {
    validate: boolean;
    allowComments: boolean;
    schemas: JsonSchemaEntry[];
  }): void;
}

export interface JsonEditorMarker {
  message: string;
  startLineNumber: number;
}

export interface JsonEditorProps {
  /** Current editor text. Controlled. */
  value: string;
  /** Called with the new text on every edit. */
  onChange: (value: string) => void;
  /**
   * Monaco language id. `json` enables syntax highlighting + well-formedness
   * validation (and JSON Schema validation when `schema` is supplied). Other
   * languages (xml, plaintext, …) get highlighting only.
   */
  language?: string;
  /**
   * Optional JSON Schema. When supplied (and language is `json`) the editor
   * validates the document against it live, surfacing schema violations as
   * inline markers in addition to well-formedness errors.
   */
  schema?: object;
  /** Accessible label / field title rendered above the editor. */
  label?: string;
  placeholder?: string;
  /** Editor height in px. */
  height?: number;
  ariaLabel?: string;
  /** Test seam — receives validation markers whenever Monaco re-validates. */
  onMarkers?: (markers: JsonEditorMarker[]) => void;
}

/**
 * A reusable Monaco-backed code editor with syntax highlighting and live,
 * inline validation. For `json` documents invalid JSON (and, when a schema is
 * supplied, schema violations) are flagged with red squiggles in the gutter
 * and summarised beneath the editor so the problem is visible before submit.
 */
export default function JsonEditor({
  value,
  onChange,
  language = 'json',
  schema,
  label,
  placeholder,
  height = 180,
  ariaLabel,
  onMarkers,
}: JsonEditorProps) {
  const theme = useTheme();
  const summaryId = useId();
  const [markers, setMarkers] = useState<JsonEditorMarker[]>([]);
  // The Monaco instance captured at mount, so json-language defaults can be
  // re-applied when the language/schema props change after mount.
  const monacoRef = useRef<typeof monaco | null>(null);

  // A unique URI per editor instance so per-model schema association does not
  // leak across editors that share the same Monaco global.
  const modelUri = useMemo(
    () => `inmemory://mockserver/${summaryId.replace(/[^a-zA-Z0-9]/g, '')}.json`,
    [summaryId],
  );

  const configureJsonDefaults = useCallback(
    (monacoInstance: typeof monaco) => {
      const jsonLang = (monacoInstance?.languages as { json?: { jsonDefaults?: JsonLanguageDefaults } } | undefined)?.json;
      if (language !== 'json' || !jsonLang?.jsonDefaults) {
        return;
      }
      const defaults = jsonLang.jsonDefaults;
      const existing = defaults.diagnosticsOptions.schemas ?? [];
      const withoutThis = existing.filter(
        (s: JsonSchemaEntry) => s.fileMatch?.[0] !== modelUri && s.uri !== modelUri,
      );
      defaults.setDiagnosticsOptions({
        validate: true,
        allowComments: false,
        schemas: schema
          ? [...withoutThis, { uri: modelUri, fileMatch: [modelUri], schema }]
          : withoutThis,
      });
    },
    [language, schema, modelUri],
  );

  // Tab moves focus by default (no keyboard trap). Monaco's own toggle flips a global
  // flag that the per-editor option overrides, so this editor binds the same chord —
  // Ctrl+M, or Ctrl+Shift+M on macOS — to an action scoped to itself. The flag lives
  // in state because the wrapper re-applies `options` on every render.
  const [tabMovesFocus, setTabMovesFocus] = useState(true);
  const tabToggleRef = useRef<{ dispose(): void } | null>(null);

  const handleMount: OnMount = useCallback(
    (editor, monacoInstance) => {
      monacoRef.current = monacoInstance as typeof monaco;
      configureJsonDefaults(monacoInstance as typeof monaco);
      if (typeof editor?.addAction === 'function' && monacoInstance?.KeyMod) {
        const { KeyMod, KeyCode } = monacoInstance;
        const mac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.userAgent);
        tabToggleRef.current?.dispose();
        tabToggleRef.current = editor.addAction({
          id: 'mockserver.toggleTabMovesFocus',
          label: 'Toggle Tab Key Moves Focus',
          keybindings: [mac ? KeyMod.WinCtrl | KeyMod.Shift | KeyCode.KeyM : KeyMod.CtrlCmd | KeyCode.KeyM],
          keybindingContext: 'editorTextFocus',
          run: () => setTabMovesFocus((current) => !current),
        });
      }
    },
    [configureJsonDefaults],
  );

  useEffect(() => () => {
    tabToggleRef.current?.dispose();
    tabToggleRef.current = null;
  }, []);

  // Re-apply json-language defaults when language or schema change after mount
  // (e.g. the user switches the body matcher type from JSON to JSON Schema).
  useEffect(() => {
    if (monacoRef.current) {
      configureJsonDefaults(monacoRef.current);
    }
  }, [configureJsonDefaults]);

  // On unmount, remove THIS editor's schema entry from the shared, GLOBAL monaco
  // jsonDefaults. `configureJsonDefaults` appends an entry per mounted editor but
  // never removed it on unmount, so entries accumulated across Composer open/close
  // cycles (each open mounts a fresh JsonEditor with a new modelUri). `modelUri`
  // is stable for the lifetime of an instance, so this effect's cleanup runs only
  // on unmount.
  useEffect(() => {
    return () => {
      const monacoInstance = monacoRef.current;
      const jsonLang = (monacoInstance?.languages as { json?: { jsonDefaults?: JsonLanguageDefaults } } | undefined)?.json;
      if (!jsonLang?.jsonDefaults) return;
      const defaults = jsonLang.jsonDefaults;
      const existing = defaults.diagnosticsOptions.schemas ?? [];
      const withoutThis = existing.filter(
        (s: JsonSchemaEntry) => s.fileMatch?.[0] !== modelUri && s.uri !== modelUri,
      );
      // Only rewrite when this mount actually contributed an entry.
      if (withoutThis.length !== existing.length) {
        defaults.setDiagnosticsOptions({
          validate: true,
          allowComments: false,
          schemas: withoutThis,
        });
      }
    };
  }, [modelUri]);

  const handleChange: OnChange = useCallback(
    (next) => {
      onChange(next ?? '');
    },
    [onChange],
  );

  const handleValidate: OnValidate = useCallback(
    (rawMarkers) => {
      const mapped = rawMarkers
        .filter((m) => m.severity >= monaco.MarkerSeverity.Warning)
        .map((m) => ({ message: m.message, startLineNumber: m.startLineNumber }));
      setMarkers(mapped);
      onMarkers?.(mapped);
    },
    [onMarkers],
  );

  const isDark = theme.palette.mode === 'dark';

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
          borderColor: markers.length > 0 ? 'error.main' : 'divider',
          borderRadius: 1,
          overflow: 'hidden',
          // Monaco's own placeholder grey is about 3:1 on its dark background.
          '& .monaco-editor .editorPlaceholder': { color: 'text.secondary' },
        }}
        data-testid="json-editor"
        role="group"
        aria-label={ariaLabel ?? label}
      >
        <Editor
          height={height}
          language={language}
          theme={isDark ? 'vs-dark' : 'vs'}
          value={value}
          path={modelUri}
          onChange={handleChange}
          onMount={handleMount}
          onValidate={handleValidate}
          options={{
            fontFamily: monospaceFontFamily,
            fontSize: 12.5,
            minimap: { enabled: false },
            lineNumbers: 'on',
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            automaticLayout: true,
            tabSize: 2,
            renderLineHighlight: 'none',
            placeholder,
            scrollbar: { alwaysConsumeMouseWheel: false },
            // Tab and Shift+Tab move focus like any other form field, so the
            // editor is never a keyboard trap (WCAG 2.1.2). The focused input is
            // announced by the field's own name rather than "Editor content".
            tabFocusMode: tabMovesFocus,
            ariaLabel: ariaLabel ?? label,
          }}
        />
      </Box>
      {markers.length > 0 && markers[0] && (
        <Typography
          id={summaryId}
          role="alert"
          variant="caption"
          color="error"
          sx={{ display: 'block', mt: 0.5, fontFamily: monospaceFontFamily, fontSize: '0.72rem' }}
          data-testid="json-editor-errors"
        >
          {markers.length === 1
            ? `Line ${markers[0].startLineNumber}: ${markers[0].message}`
            : `${markers.length} problems — line ${markers[0].startLineNumber}: ${markers[0].message}`}
        </Typography>
      )}
    </Box>
  );
}
