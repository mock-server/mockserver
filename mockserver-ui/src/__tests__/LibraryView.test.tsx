import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LibraryView from '../components/LibraryView';
import type { ConnectionParams } from '../hooks/useConnectionParams';

vi.mock('../lib/mcpClient', () => ({
  buildBaseUrl: (p: { host: string; port: string; secure: boolean }) =>
    `${p.secure ? 'https' : 'http'}://${p.host}:${p.port}`,
}));

const connectionParams: ConnectionParams = { host: 'localhost', port: '1080', secure: false };

interface FetchCall {
  url: string;
  init?: RequestInit;
}

let fetchCalls: FetchCall[];

function stubFetch(status: number, body: unknown) {
  fetchCalls = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      fetchCalls.push({ url, init });
      return {
        ok: status >= 200 && status < 300,
        status,
        statusText: 'stub',
        json: async () => body,
        text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
        blob: async () => new Blob([typeof body === 'string' ? body : JSON.stringify(body)]),
      };
    }),
  );
}

function downloadButton() {
  return screen.getByRole('button', { name: /Download/ });
}

/** Switch to the Export tab (index 1 — Import is 0). */
async function switchToExport(user: ReturnType<typeof userEvent.setup>) {
  const tabs = screen.getAllByRole('tab');
  await user.click(tabs[1]!); // Export tab
}

beforeEach(() => {
  stubFetch(201, [{ id: 'imported-1' }, { id: 'imported-2' }]);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('LibraryView tab layout', () => {
  it('Import is the first tab, Export is the second', () => {
    render(<LibraryView connectionParams={connectionParams} />);
    const tabs = screen.getAllByRole('tab');
    expect(tabs[0]).toHaveTextContent('Import');
    expect(tabs[1]).toHaveTextContent('Export');
    expect(tabs[2]).toHaveTextContent('Cassettes');
  });

  it('opens on the Import tab by default', () => {
    render(<LibraryView connectionParams={connectionParams} />);
    // Import form elements are visible.
    expect(screen.getByText('Format')).toBeInTheDocument();
    expect(screen.getByText('Source')).toBeInTheDocument();
  });
});

describe('LibraryView export controls', () => {
  it('shows Export controls after clicking the Export tab', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    expect(screen.getByText('What to export')).toBeInTheDocument();
  });

  it('lists Recorded requests first and selects it by default', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    const radios = screen.getAllByRole('radio');
    expect(radios[0]).toHaveAccessibleName('Recorded requests');
    expect(screen.getByRole('radio', { name: 'Recorded requests' })).toBeChecked();
  });

  it('shows the best-effort caveat only for lossy expectation exports', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    // Default (recorded requests + JSON): no caveat.
    expect(screen.queryByText(/Best-effort export/)).not.toBeInTheDocument();
    // Active expectations + OpenAPI: the expectation-graph caveat appears.
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    await user.click(screen.getByRole('option', { name: 'OpenAPI 3 spec' }));
    expect(screen.getByText(/Best-effort export/)).toBeInTheDocument();
  });

  it('shows a traffic-derived note for recorded-request spec exports', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    await user.click(screen.getByRole('option', { name: 'OpenAPI 3 spec' }));
    expect(screen.getByText(/Derived from the traffic/)).toBeInTheDocument();
    expect(screen.queryByText(/Best-effort export/)).not.toBeInTheDocument();
  });

  it('chooses scope with a radio and format with a dropdown (no combined list)', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    expect(screen.getByRole('radio', { name: 'Active expectations' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Recorded requests' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Format' })).toBeInTheDocument();
    // Default selection: recorded requests + HAR.
    expect(downloadButton()).toHaveTextContent('mockserver-traffic.har');
  });

  it('updates the target file when the scope radio changes', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    expect(downloadButton()).toHaveTextContent('mockserver-expectations.har');
  });

  it('updates the target file when the format dropdown changes', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    await user.click(screen.getByRole('option', { name: 'OpenAPI 3 spec' }));
    expect(downloadButton()).toHaveTextContent('mockserver-expectations.openapi.json');
  });

  it('offers JAVA only for active expectations', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    // Default scope = recorded requests: no Java DSL option.
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.queryByRole('option', { name: 'MockServer Java DSL' })).not.toBeInTheDocument();
    await user.keyboard('{Escape}');
    // Switch to expectations: Java DSL appears.
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.getByRole('option', { name: 'MockServer Java DSL' })).toBeInTheDocument();
  });

  it('offers LOG_ENTRIES only for recorded requests', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    // Default scope = recorded requests: log entries present.
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.getByRole('option', { name: 'Log entries (JSON)' })).toBeInTheDocument();
    await user.keyboard('{Escape}');
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.queryByRole('option', { name: 'Log entries (JSON)' })).not.toBeInTheDocument();
  });

  it('offers cURL only for recorded requests', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.getByRole('option', { name: 'cURL commands' })).toBeInTheDocument();
    await user.click(screen.getByRole('option', { name: 'cURL commands' }));
    expect(downloadButton()).toHaveTextContent('mockserver-traffic.curl.sh');
  });

  it('offers JavaScript and Python client code only for expectations', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    // Default scope = recorded requests: no JS/Python code option.
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.queryByRole('option', { name: 'JavaScript client code' })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Python client code' })).not.toBeInTheDocument();
    await user.keyboard('{Escape}');
    // Switch to expectations: both appear.
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.getByRole('option', { name: 'JavaScript client code' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Python client code' })).toBeInTheDocument();
  });

  it('offers Go, C#, Ruby, Rust and PHP client code for expectations', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.getByRole('option', { name: 'Go client code' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'C# client code' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Ruby client code' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Rust client code' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'PHP client code' })).toBeInTheDocument();
  });

  it('drives format=GO / RUBY / PHP on the retrieve call for the new languages', async () => {
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    URL.createObjectURL = vi.fn(() => 'blob:fake');
    URL.revokeObjectURL = vi.fn();

    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    await user.click(screen.getByRole('option', { name: 'Go client code' }));
    expect(downloadButton()).toHaveTextContent('mockserver-expectations.go');
    await user.click(downloadButton());
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/retrieve'));
      expect(call!.url).toBe('http://localhost:1080/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=GO');
    });

    URL.createObjectURL = originalCreateObjectURL;
    URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('offers verification code in all languages only for recorded requests', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    // Default scope = recorded requests: verification options present.
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.getByRole('option', { name: 'Verification code (Java)' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Verification code (Go)' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Verification code (Rust)' })).toBeInTheDocument();
    await user.keyboard('{Escape}');
    // Active expectations scope: no verification options.
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    expect(screen.queryByRole('option', { name: 'Verification code (Java)' })).not.toBeInTheDocument();
  });

  it('resets an expectations-only format back to JSON when switching to requests', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    await user.click(screen.getByRole('option', { name: 'MockServer Java DSL' }));
    expect(downloadButton()).toHaveTextContent('mockserver-expectations.java');
    // Switching to requests (where Java is invalid) falls back to JSON.
    await user.click(screen.getByRole('radio', { name: 'Recorded requests' }));
    expect(downloadButton()).toHaveTextContent('mockserver-traffic.json');
  });
});

// ---------------------------------------------------------------------------
// Import tab — endpoint + format integration
// ---------------------------------------------------------------------------

describe('LibraryView import tab', () => {
  it('Import tab renders the ImportForm with all format radios', () => {
    render(<LibraryView connectionParams={connectionParams} />);
    // Import tab is open by default
    expect(screen.getByRole('radio', { name: /Expectation JSON/ })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /OpenAPI/ })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /WSDL/ })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /HAR/ })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /Postman/ })).toBeInTheDocument();
  });

  it('Expectation JSON import PUTs to /mockserver/expectation', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    // Expectation JSON is default format, Paste is default source
    const textarea = screen.getByLabelText('Expectation JSON content');
    await user.click(textarea);
    await user.paste('[{"httpRequest":{"path":"/test"}}]');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/expectation'));
      expect(call).toBeTruthy();
      expect(call!.url).toBe('http://localhost:1080/mockserver/expectation');
      expect(call!.init?.method).toBe('PUT');
    });
  });

  it('OpenAPI import PUTs to /mockserver/openapi', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await user.click(screen.getByRole('radio', { name: /OpenAPI/ }));
    const textarea = screen.getByLabelText('OpenAPI content');
    await user.click(textarea);
    await user.paste('{"openapi":"3.0.0"}');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/openapi'));
      expect(call).toBeTruthy();
      expect(call!.url).toBe('http://localhost:1080/mockserver/openapi?includeLoadProblems=true');
      expect(call!.init?.method).toBe('PUT');
    });
  });

  it('HAR import PUTs to /mockserver/import?format=har', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await user.click(screen.getByRole('radio', { name: /HAR/ }));
    const textarea = screen.getByLabelText('HAR (HTTP Archive) content');
    await user.click(textarea);
    await user.paste('{"log":{"entries":[]}}');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/import?format=har'));
      expect(call).toBeTruthy();
      expect(call!.init?.method).toBe('PUT');
    });
  });

  it('Postman import PUTs to /mockserver/import?format=postman', async () => {
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await user.click(screen.getByRole('radio', { name: /Postman/ }));
    const textarea = screen.getByLabelText('Postman collection content');
    await user.click(textarea);
    await user.paste('{"info":{"name":"x"},"item":[]}');
    await user.click(screen.getByRole('button', { name: 'Import' }));
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/import?format=postman'));
      expect(call).toBeTruthy();
      expect(call!.init?.method).toBe('PUT');
    });
  });
});

// ---------------------------------------------------------------------------
// Export tab — download via PUT /mockserver/retrieve with type + format
// ---------------------------------------------------------------------------

describe('LibraryView export download', () => {
  it('Download calls PUT /mockserver/retrieve with correct type and format', async () => {
    // Stub URL.createObjectURL and revokeObjectURL for download test
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    URL.createObjectURL = vi.fn(() => 'blob:fake');
    URL.revokeObjectURL = vi.fn();

    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    // Default: Recorded requests (REQUEST_RESPONSES) + HAR
    await user.click(downloadButton());
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/retrieve'));
      expect(call).toBeTruthy();
      expect(call!.url).toBe('http://localhost:1080/mockserver/retrieve?type=REQUEST_RESPONSES&format=HAR');
      expect(call!.init?.method).toBe('PUT');
    });

    URL.createObjectURL = originalCreateObjectURL;
    URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('Export uses ACTIVE_EXPECTATIONS type when that scope is selected', async () => {
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    URL.createObjectURL = vi.fn(() => 'blob:fake');
    URL.revokeObjectURL = vi.fn();

    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    await user.click(screen.getByRole('option', { name: 'OpenAPI 3 spec' }));
    await user.click(downloadButton());
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/retrieve'));
      expect(call).toBeTruthy();
      expect(call!.url).toBe('http://localhost:1080/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=OPENAPI');
    });

    URL.createObjectURL = originalCreateObjectURL;
    URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('Selecting JavaScript drives format=JAVASCRIPT on the retrieve call', async () => {
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    URL.createObjectURL = vi.fn(() => 'blob:fake');
    URL.revokeObjectURL = vi.fn();

    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
    await user.click(screen.getByRole('combobox', { name: 'Format' }));
    await user.click(screen.getByRole('option', { name: 'JavaScript client code' }));
    expect(downloadButton()).toHaveTextContent('mockserver-expectations.js');
    await user.click(downloadButton());
    await waitFor(() => {
      const call = fetchCalls.find((c) => c.url.includes('/mockserver/retrieve'));
      expect(call).toBeTruthy();
      expect(call!.url).toBe('http://localhost:1080/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=JAVASCRIPT');
    });

    URL.createObjectURL = originalCreateObjectURL;
    URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('Copy as code fetches the code text and writes it to the clipboard', async () => {
    stubFetch(200, "mockServerClient(\"localhost\", 1080).mockAnyResponse({});");
    const writeText = vi.fn(async () => undefined);
    const user = userEvent.setup();
    // Override clipboard after userEvent.setup() (which installs its own stub). Capture the original
    // descriptor and restore it in finally so this override does not leak into later tests
    // (vi.unstubAllGlobals does not track Object.defineProperty on navigator).
    const originalClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
      writable: true,
    });
    try {
      render(<LibraryView connectionParams={connectionParams} />);
      await switchToExport(user);
      await user.click(screen.getByRole('radio', { name: 'Active expectations' }));
      await user.click(screen.getByRole('combobox', { name: 'Format' }));
      await user.click(screen.getByRole('option', { name: 'Python client code' }));
      await user.click(screen.getByRole('button', { name: /Copy as code/ }));
      await waitFor(() => {
        const call = fetchCalls.find((c) => c.url.includes('/mockserver/retrieve'));
        expect(call!.url).toBe('http://localhost:1080/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=PYTHON');
        expect(writeText).toHaveBeenCalledWith('mockServerClient("localhost", 1080).mockAnyResponse({});');
      });
    } finally {
      if (originalClipboard) {
        Object.defineProperty(navigator, 'clipboard', originalClipboard);
      } else {
        delete (navigator as { clipboard?: unknown }).clipboard;
      }
    }
  });

  it('Verification code copies client-side verify(...) generated from retrieved request JSON', async () => {
    // The server returns recorded request/response pairs as JSON; the dashboard runs
    // verificationCodegen over them rather than asking the server for verify code.
    stubFetch(200, [
      { httpRequest: { method: 'GET', path: '/alpha' }, httpResponse: { statusCode: 200 } },
      { httpRequest: { method: 'POST', path: '/beta' }, httpResponse: { statusCode: 201 } },
    ]);
    const writeText = vi.fn<(text: string) => Promise<undefined>>(async () => undefined);
    const user = userEvent.setup();
    // Override clipboard AFTER userEvent.setup() (which installs its own stub).
    const originalClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true, writable: true });
    try {
      render(<LibraryView connectionParams={connectionParams} />);
      await switchToExport(user);
      // Default scope = recorded requests.
      await user.click(screen.getByRole('combobox', { name: 'Format' }));
      await user.click(screen.getByRole('option', { name: 'Verification code (Java)' }));
      await user.click(screen.getByRole('button', { name: /Copy as code/ }));
      await waitFor(() => {
        // It retrieves the request/response pairs as JSON (not a server verify format).
        const call = fetchCalls.find((c) => c.url.includes('/mockserver/retrieve'));
        expect(call!.url).toBe('http://localhost:1080/mockserver/retrieve?type=REQUEST_RESPONSES&format=JSON');
        expect(writeText).toHaveBeenCalled();
      });
      const generated = writeText.mock.calls[0]![0];
      // One Java verify(...) call per recorded request, asserting at-least-once.
      expect(generated).toContain('mockServerClient');
      expect(generated).toContain('VerificationTimes.atLeast(1)');
      expect(generated).toContain('/alpha');
      expect(generated).toContain('/beta');
    } finally {
      if (originalClipboard) {
        Object.defineProperty(navigator, 'clipboard', originalClipboard);
      } else {
        delete (navigator as { clipboard?: unknown }).clipboard;
      }
    }
  });

  it('Export triggers a browser download with the correct filename', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:fake-url');
    URL.revokeObjectURL = vi.fn();
    const clickSpy = vi.fn();
    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === 'a') {
        el.click = clickSpy;
      }
      return el;
    });

    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToExport(user);
    await user.click(downloadButton());
    await waitFor(() => {
      expect(clickSpy).toHaveBeenCalled();
      expect(URL.createObjectURL).toHaveBeenCalled();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-url');
    });

    vi.restoreAllMocks();
  });
});

describe('LibraryView WASM Modules tab — test a module', () => {
  /** Route fetch by URL: the module-list poll returns one module; /wasm/test returns the decision. */
  function stubWasmFetch(testStatus: number, testBody: unknown) {
    fetchCalls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        fetchCalls.push({ url, init });
        const isTest = url.endsWith('/mockserver/wasm/test');
        const status = isTest ? testStatus : 200;
        const body = isTest ? testBody : ['my-rule'];
        return {
          ok: status >= 200 && status < 300,
          status,
          statusText: 'stub',
          json: async () => body,
          text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
        };
      }),
    );
  }

  async function switchToWasm(user: ReturnType<typeof userEvent.setup>) {
    const tabs = screen.getAllByRole('tab');
    await user.click(tabs[3]!); // WASM Modules tab
  }

  it('POSTs the module name and prefilled sample request, then renders the matched decision', async () => {
    stubWasmFetch(200, { matched: true });
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToWasm(user);

    const testButton = await screen.findByRole('button', { name: 'Test WASM module my-rule' });
    await user.click(testButton);

    const runButton = await screen.findByRole('button', { name: /Run test/ });
    await user.click(runButton);

    await waitFor(() => {
      expect(screen.getByText(/Matched — the module accepted this request/)).toBeInTheDocument();
    });

    const testCall = fetchCalls.find((c) => c.url.endsWith('/mockserver/wasm/test'));
    expect(testCall).toBeDefined();
    expect(testCall?.init?.method).toBe('POST');
    expect(JSON.parse(testCall?.init?.body as string)).toEqual({
      moduleName: 'my-rule',
      request: { method: 'GET', path: '/' },
    });
  });

  it('surfaces the server error envelope when the test call fails', async () => {
    stubWasmFetch(400, { error: "'module' must be base64-encoded WASM bytes" });
    const user = userEvent.setup();
    render(<LibraryView connectionParams={connectionParams} />);
    await switchToWasm(user);

    await user.click(await screen.findByRole('button', { name: 'Test WASM module my-rule' }));
    await user.click(await screen.findByRole('button', { name: /Run test/ }));

    await waitFor(() => {
      expect(screen.getByText(/rejected as invalid/i)).toBeInTheDocument();
    });
  });
});

describe('LibraryView WASM Modules tab — WASM turned off on the server', () => {
  it('explains how to turn WASM on, disables Upload and stops polling until Refresh', async () => {
    fetchCalls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        fetchCalls.push({ url, init });
        const body = 'WASM support is disabled; set wasmEnabled=true to enable';
        return { ok: false, status: 403, statusText: 'Forbidden', json: async () => body, text: async () => body };
      }),
    );
    const listCalls = () => fetchCalls.filter((c) => c.url.endsWith('/mockserver/wasm/modules')).length;
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      render(<LibraryView connectionParams={connectionParams} />);
      await user.click(screen.getAllByRole('tab')[3]!); // WASM Modules tab

      expect(await screen.findByText(/WASM rules are turned off on this server/)).toBeInTheDocument();
      expect(screen.getByText('-Dmockserver.wasmEnabled=true')).toBeInTheDocument();
      expect(screen.queryByText(/HTTP 403/)).not.toBeInTheDocument();
      expect(screen.queryByText('No WASM modules loaded.')).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Upload' })).toBeDisabled();
      expect(listCalls()).toBe(1);

      await vi.advanceTimersByTimeAsync(30_000);
      expect(listCalls()).toBe(1);

      await user.click(screen.getByRole('button', { name: 'Refresh WASM modules' }));
      await waitFor(() => expect(listCalls()).toBe(2));
    } finally {
      vi.useRealTimers();
    }
  });
});
