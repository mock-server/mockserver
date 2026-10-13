/**
 * Tests for the Import kind in the Mocks Composer:
 * - Import kind appears in the kind selector and renders its form
 * - Format and source pickers render correctly
 * - Each format calls the correct endpoint with the correct payload
 * - Success shows the count; error shows an alert
 * - Switching to/from Import does not crash the Composer
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import ComposerView from '../components/ComposerView';
import { useDashboardStore } from '../store';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock('../lib/mcpClient', () => ({
  buildBaseUrl: (p: { host: string; port: string; secure: boolean }) =>
    `${p.secure ? 'https' : 'http'}://${p.host}:${p.port}`,
  callMcpTool: vi.fn().mockResolvedValue({ ok: true, result: { tools: [], count: 0 } }),
}));

vi.mock('../lib/conversationCodegen', () => ({
  listConversationScenarios: () => [],
}));

const params = { host: '127.0.0.1', port: '1080', secure: false };

interface FetchCall {
  url: string;
  init?: RequestInit;
}

let fetchCalls: FetchCall[];

function stubFetch(status: number, body: unknown, headers: Record<string, string> = {}) {
  fetchCalls = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      fetchCalls.push({ url, init });
      return {
        ok: status >= 200 && status < 300,
        status,
        statusText: 'stub',
        headers: { get: (name: string) => headers[name.toLowerCase()] ?? null },
        json: async () => body,
        text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
      };
    }),
  );
}

function renderComposer() {
  // The composer now defaults to a minimal "Quick mock" view; these tests need
  // the full Advanced form (kind selector incl. Import). Seed the session
  // preference read by getInitialMode on mount.
  try { globalThis.sessionStorage?.setItem('mockserver-composer-mode', 'advanced'); } catch { /* noop */ }
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <ComposerView connectionParams={params} />
    </ThemeProvider>,
  );
}

/** Select a format radio inside the Import form. Format radios have complex
 *  labels (Box with bold label + caption) so getByLabelText does not work;
 *  use getByRole('radio', { name: /.../ }) which matches the accessible name
 *  (concatenated text content of the entire label). */
function getFormatRadio(name: RegExp) {
  return screen.getByRole('radio', { name });
}

/** Type text into a field using paste to avoid userEvent special-char escaping
 *  issues with curly braces and brackets in JSON content. */
async function pasteInto(user: ReturnType<typeof userEvent.setup>, element: HTMLElement, text: string) {
  await user.click(element);
  await user.paste(text);
}

beforeEach(() => {
  useDashboardStore.setState({ activeExpectations: [] });
  stubFetch(201, [{ id: 'imported-1' }, { id: 'imported-2' }]);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Kind selector
// ---------------------------------------------------------------------------

describe('Import kind in Composer', () => {
  it('shows the Import radio in the kind selector', () => {
    renderComposer();
    expect(screen.getByLabelText('Import')).toBeInTheDocument();
  });

  it('selecting Import hides the matcher/action steps', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    // The standard steps should not be visible
    expect(screen.queryByText(/1 · Match a request/)).not.toBeInTheDocument();
    expect(screen.queryByText(/2 · Respond with/)).not.toBeInTheDocument();
    expect(screen.queryByText(/3 ·/)).not.toBeInTheDocument();
  });

  it('selecting Import shows format and source pickers', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    expect(screen.getByText('Format')).toBeInTheDocument();
    expect(screen.getByText('Source')).toBeInTheDocument();
    // Format radios have complex labels -- match by role + accessible name pattern
    expect(getFormatRadio(/Expectation JSON/)).toBeInTheDocument();
    expect(getFormatRadio(/OpenAPI/)).toBeInTheDocument();
    expect(getFormatRadio(/WSDL/)).toBeInTheDocument();
    expect(getFormatRadio(/HAR/)).toBeInTheDocument();
    expect(getFormatRadio(/Recording \(NDJSON\)/)).toBeInTheDocument();
  });

  it('switching from Import to HTTP restores the matcher/action steps', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    expect(screen.queryByText(/1 · Match a request/)).not.toBeInTheDocument();
    await user.click(screen.getByLabelText('HTTP'));
    expect(screen.getByText(/1 · Match a request/)).toBeInTheDocument();
    expect(screen.getByText(/2 · Respond with/)).toBeInTheDocument();
  });

  it('switching from HTTP to Import and back does not crash', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(screen.getByLabelText('HTTP'));
    await user.click(screen.getByLabelText('Import'));
    expect(screen.getByText('Format')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Source pickers
// ---------------------------------------------------------------------------

describe('Import source pickers', () => {
  it('shows Paste and File sources for Expectation JSON (no URL)', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    // Should have Paste and File but NOT URL
    const sourceSection = screen.getByText('Source').closest('[class*="MuiPaper"]')!;
    expect(within(sourceSection as HTMLElement).getByLabelText('Paste')).toBeInTheDocument();
    expect(within(sourceSection as HTMLElement).getByLabelText('File')).toBeInTheDocument();
    expect(within(sourceSection as HTMLElement).queryByLabelText('URL')).not.toBeInTheDocument();
  });

  it('shows URL source for OpenAPI format', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/OpenAPI/));
    const sourceSection = screen.getByText('Source').closest('[class*="MuiPaper"]')!;
    expect(within(sourceSection as HTMLElement).getByLabelText('URL')).toBeInTheDocument();
  });

  it('switching from OpenAPI URL to HAR resets source to Paste', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/OpenAPI/));
    const sourceSection = screen.getByText('Source').closest('[class*="MuiPaper"]')!;
    await user.click(within(sourceSection as HTMLElement).getByLabelText('URL'));
    // Now switch to HAR which does not support URL
    await user.click(getFormatRadio(/HAR/));
    // Paste should now be selected (URL is not available)
    const pasteRadio = within(sourceSection as HTMLElement).getByLabelText('Paste');
    expect(pasteRadio).toBeChecked();
  });
});

// ---------------------------------------------------------------------------
// Import button + endpoint calls
// ---------------------------------------------------------------------------

describe('Import per-format endpoint calls', () => {
  it('Expectation JSON calls PUT /mockserver/expectation', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    // Expectation JSON is the default format; Paste is the default source
    const textarea = screen.getByLabelText('Expectation JSON content');
    await pasteInto(user, textarea, '[{"httpRequest":{"path":"/test"}}]');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      expect(fetchCalls.length).toBeGreaterThanOrEqual(1);
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/expectation'));
      expect(call).toBeTruthy();
      expect(call!.init?.method).toBe('PUT');
    });
  });

  it('OpenAPI calls PUT /mockserver/openapi', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/OpenAPI/));
    const textarea = screen.getByLabelText('OpenAPI content');
    await pasteInto(user, textarea, '{"openapi":"3.0.0"}');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/openapi'));
      expect(call).toBeTruthy();
      expect(call!.init?.method).toBe('PUT');
    });
  });

  it('OpenAPI lists the load problems of a spec that loaded until the format changes', async () => {
    stubFetch(201, {
      expectations: [{ id: 'openapi:pets:listPets' }],
      loadProblems: [
        {
          specIndex: 0,
          kind: 'MISSING',
          kindLabel: 'missing required field',
          location: '#/info',
          description: "required field 'version' is missing",
        },
      ],
    });
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/OpenAPI/));
    await pasteInto(user, screen.getByLabelText('OpenAPI content'), 'openapi: 3.0.0');
    await user.click(screen.getByRole('button', { name: 'Import' }));

    const alert = await screen.findByTestId('openapi-load-problems');
    expect(within(alert).getByText('Spec Loaded With 1 Problem')).toBeInTheDocument();
    expect(alert).toHaveTextContent("#/info: required field 'version' is missing");
    expect(fetchCalls.find((c) => c.url.includes('/mockserver/openapi'))!.url).toContain('includeLoadProblems=true');

    await user.click(getFormatRadio(/WSDL/));
    expect(screen.queryByTestId('openapi-load-problems')).not.toBeInTheDocument();
  });

  it('OpenAPI URL source calls PUT /mockserver/openapi with the URL', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/OpenAPI/));
    const sourceSection = screen.getByText('Source').closest('[class*="MuiPaper"]')!;
    await user.click(within(sourceSection as HTMLElement).getByLabelText('URL'));
    const urlInput = screen.getByLabelText('Spec URL');
    await pasteInto(user, urlInput, 'https://example.com/spec.yaml');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/openapi'));
      expect(call).toBeTruthy();
    });
  });

  it('WSDL calls PUT /mockserver/wsdl', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/WSDL/));
    const textarea = screen.getByLabelText('WSDL / SOAP content');
    await pasteInto(user, textarea, '<definitions/>');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/wsdl'));
      expect(call).toBeTruthy();
      expect(call!.init?.method).toBe('PUT');
    });
  });

  it('HAR calls PUT /mockserver/import?format=har', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/HAR/));
    const textarea = screen.getByLabelText('HAR (HTTP Archive) content');
    await pasteInto(user, textarea, '{"log":{"entries":[]}}');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/import?format=har'));
      expect(call).toBeTruthy();
      expect(call!.init?.method).toBe('PUT');
    });
  });

  it('Postman calls PUT /mockserver/import?format=postman', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/Postman/));
    const textarea = screen.getByLabelText('Postman collection content');
    await pasteInto(user, textarea, '{"info":{"name":"x"},"item":[]}');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/import?format=postman'));
      expect(call).toBeTruthy();
      expect(call!.init?.method).toBe('PUT');
    });
  });

  it('Recording (NDJSON) paste calls PUT /mockserver/import?format=recording with the body', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/Recording/));
    const textarea = screen.getByLabelText('Recording (NDJSON) content');
    await pasteInto(user, textarea, '{"httpRequest":{"path":"/a"},"httpResponse":{"statusCode":200}}');
    await user.click(screen.getByRole('button', { name: 'Reload' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/import?format=recording'));
      expect(call).toBeTruthy();
      expect(call!.url).not.toContain('source=disk');
      expect(call!.init?.method).toBe('PUT');
    });
  });

  it('Reload From Server Disk calls ?format=recording&source=disk with no body', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await user.click(getFormatRadio(/Recording/));
    await user.click(screen.getByRole('button', { name: 'Reload From Server Disk' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('source=disk'));
      expect(call).toBeTruthy();
      expect(call!.url).toContain('format=recording');
      expect(call!.init?.body).toBe('');
    });
  });
});

// ---------------------------------------------------------------------------
// Success / error feedback
// ---------------------------------------------------------------------------

describe('Import success and error feedback', () => {
  it('shows success count after import', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    const textarea = screen.getByLabelText('Expectation JSON content');
    await pasteInto(user, textarea, '[{"httpRequest":{"path":"/test"}}]');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      expect(screen.getByText('Imported 2 expectations')).toBeInTheDocument();
    });
  });

  it('shows a lint warnings badge when the server counted lint warnings', async () => {
    stubFetch(201, [{ id: 'imported-1' }], { 'x-mockserver-lint-warnings': '3' });
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await pasteInto(user, screen.getByLabelText('Expectation JSON content'), '[{"httpRequest":{"path":"users"}}]');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    expect(await screen.findByTestId('lint-warnings-badge')).toHaveTextContent('3 Lint Warnings');
  });

  it('shows no lint warnings badge when the header is absent', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    await pasteInto(user, screen.getByLabelText('Expectation JSON content'), '[{"httpRequest":{"path":"/test"}}]');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await screen.findByText('Imported 2 expectations');
    expect(screen.queryByTestId('lint-warnings-badge')).not.toBeInTheDocument();
  });

  it('shows an honest "Import succeeded" when the server returns no created list', async () => {
    // A 2xx body that is not a JSON array of created expectations — the import
    // helpers normalise this to [], which previously rendered "Imported 0
    // expectations". It should now report success without a misleading count.
    stubFetch(200, { status: 'ok' });
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    const textarea = screen.getByLabelText('Expectation JSON content');
    await pasteInto(user, textarea, '[{"httpRequest":{"path":"/test"}}]');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      expect(screen.getByText('Import succeeded')).toBeInTheDocument();
    });
    expect(screen.queryByText('Imported 0 expectations')).not.toBeInTheDocument();
  });

  it('shows error alert on failure', async () => {
    stubFetch(400, 'invalid payload format');
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    const textarea = screen.getByLabelText('Expectation JSON content');
    await pasteInto(user, textarea, '[{}]');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      expect(screen.getByText('invalid payload format')).toBeInTheDocument();
    });
  });

  it('Import button is disabled when payload is empty', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText('Import'));
    const importButton = screen.getByRole('button', { name: 'Import' });
    expect(importButton).toBeDisabled();
  });
});
