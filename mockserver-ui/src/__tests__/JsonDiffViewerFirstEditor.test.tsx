import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createElement } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';

// Distinguishable worker stand-ins, so the test can tell which bundled worker
// Monaco would be handed for each label.
vi.mock('monaco-editor/editor/editor.worker?worker', () => ({
  default: class EditorWorkerStub {
    readonly kind = 'editor';
  },
}));
vi.mock('monaco-editor/language/json/json.worker?worker', () => ({
  default: class JsonWorkerStub {
    readonly kind = 'json';
  },
}));

type MonacoEnvironmentLike = { getWorker(workerId: string, label: string): { kind?: string } };

// The diff editor computes its diff in Monaco's editor worker. When the diff
// viewer is the first editor a page opens (Capture as Mock -> Preview Diff),
// nothing else has wired the bundled workers, so it must do so itself. This file
// never imports JsonEditor, and vitest gives each file a fresh module registry.
describe('JsonDiffViewer opened before any other editor', () => {
  it('wires Monaco to the bundled workers', async () => {
    const env = self as unknown as { MonacoEnvironment?: MonacoEnvironmentLike };
    expect(env.MonacoEnvironment).toBeUndefined();

    const { default: JsonDiffViewerLazy } = await import('../components/JsonDiffViewerLazy');
    render(
      createElement(
        ThemeProvider,
        { theme: buildTheme('light') },
        createElement(JsonDiffViewerLazy, { original: '{}', modified: '{"a":1}', ariaLabel: 'Mock to be created' }),
      ),
    );
    expect(await screen.findByTestId('monaco-diff')).toBeInTheDocument();

    expect(env.MonacoEnvironment, 'MonacoEnvironment is set').toBeDefined();
    expect(env.MonacoEnvironment?.getWorker('workerMain.js', 'editorWorkerService').kind).toBe('editor');
    expect(env.MonacoEnvironment?.getWorker('workerMain.js', 'json').kind).toBe('json');
  });
});
