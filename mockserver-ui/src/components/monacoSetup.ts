import { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
// Vite ?worker imports — types declared in monaco-workers.d.ts.
// monaco-editor 0.56.0 added an `exports` map ("./*" -> "./esm/vs/*.js"), so
// subpaths are resolved relative to esm/vs and must NOT include that prefix
// themselves (the old "esm/vs/..." form double-prefixes to "esm/vs/esm/vs/...").
import EditorWorker from 'monaco-editor/editor/editor.worker?worker';
import JsonWorker from 'monaco-editor/language/json/json.worker?worker';

// Imported for its side effects by every Monaco-backed component (JsonEditor,
// JsonDiffViewer), so whichever editor a page opens first finds Monaco wired up.
// Without the worker factory, Monaco falls back to a worker URL the bundle does
// not contain and the diff editor never computes a diff.

// Use the locally bundled `monaco-editor` instead of letting
// `@monaco-editor/react` fetch Monaco from a CDN at runtime: the dashboard is
// served from the (often air-gapped) mock server itself.
loader.config({ monaco });

// Only the JSON language ships a dedicated worker in monaco core; every other
// language, and the diff computation, use the generic editor worker. The typeof
// guard avoids a hard reference to `self` in any non-browser context.
if (typeof self !== 'undefined') {
  (self as unknown as { MonacoEnvironment?: monaco.Environment }).MonacoEnvironment = {
    getWorker(_workerId: string, label: string) {
      if (label === 'json') {
        return new JsonWorker();
      }
      return new EditorWorker();
    },
  };
}
