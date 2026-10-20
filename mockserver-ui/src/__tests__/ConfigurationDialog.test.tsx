import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import ConfigurationDialog from '../components/ConfigurationDialog';
import { EDITABLE_PROPERTIES } from '../lib/configuration';

const params = { host: '127.0.0.1', port: '1080', secure: false };

function renderDialog() {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <ConfigurationDialog open connectionParams={params} onClose={vi.fn()} />
    </ThemeProvider>,
  );
}

/** Build a full config object that includes all editable properties. */
function fullConfig() {
  return {
    logLevel: 'INFO',
    detailedMatchFailures: true,
    metricsEnabled: false,
    devMode: false,
    generateRealisticExampleValues: true,
    attachMismatchDiagnosticToResponse: false,
    validateProxyOpenAPISpec: 'https://example.com/spec.yaml',
    validateProxyEnforce: true,
    chaosAutoHaltEnabled: false,
    chaosAutoHaltErrorThreshold: 50,
    chaosAutoHaltWindowMillis: 30000,
    matchersFailFast: false,
    attemptToProxyIfNoMatchingExpectation: false,
    maximumNumberOfRequestToReturnInVerificationFailure: 10,
    disableLogging: false,
    compactLogFormat: false,
    enableCORSForAPI: false,
    enableCORSForAllResponses: false,
    maxLogEntries: 1000,
    maxExpectations: 500,
  };
}

function stubFetchWithConfig(cfg: Record<string, unknown>) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_url: string, init?: RequestInit) => {
      if (init?.method === 'PUT') {
        return { ok: true, text: async () => '' };
      }
      // GET
      return { ok: true, json: async () => cfg };
    }),
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('ConfigurationDialog', () => {
  it('renders the original controls (logLevel, detailedMatchFailures, metricsEnabled)', async () => {
    stubFetchWithConfig(fullConfig());
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('Log level')).toBeInTheDocument();
    });
    expect(screen.getByText('Detailed match failures')).toBeInTheDocument();
    expect(screen.getByText('Metrics enabled')).toBeInTheDocument();
  });

  it('renders a Switch for each boolean descriptor property', async () => {
    stubFetchWithConfig(fullConfig());
    renderDialog();

    const booleanDescriptors = EDITABLE_PROPERTIES.filter((d) => d.type === 'boolean');
    await waitFor(() => {
      expect(screen.getByText(booleanDescriptors[0]!.label)).toBeInTheDocument();
    });

    for (const d of booleanDescriptors) {
      expect(screen.getByText(d.label)).toBeInTheDocument();
    }

    // Verify that devMode renders as an unchecked switch
    const devModeLabel = screen.getByText('Matchers fail fast');
    const formControl = devModeLabel.closest('label')!;
    const switchInput = within(formControl).getByRole('switch') as HTMLInputElement;
    expect(switchInput.checked).toBe(false);
  });

  it('toggling a boolean descriptor PUTs the right partial config', async () => {
    stubFetchWithConfig(fullConfig());
    const user = userEvent.setup();
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('Matchers fail fast')).toBeInTheDocument();
    });

    const devModeLabel = screen.getByText('Matchers fail fast');
    const formControl = devModeLabel.closest('label')!;
    const switchInput = within(formControl).getByRole('switch');
    await user.click(switchInput);

    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    const putCalls = fetchMock.mock.calls.filter(
      (c: unknown[]) => (c[1] as RequestInit | undefined)?.method === 'PUT',
    );
    expect(putCalls.length).toBeGreaterThanOrEqual(1);
    const body = JSON.parse((putCalls[0]![1] as RequestInit).body as string);
    expect(body).toEqual({ matchersFailFast: true });
  });

  it('renders validateProxyOpenAPISpec as a text field with the current value', async () => {
    stubFetchWithConfig(fullConfig());
    renderDialog();

    await waitFor(() => {
      expect(screen.getByLabelText('OpenAPI spec (URL / path)')).toBeInTheDocument();
    });

    const input = screen.getByLabelText('OpenAPI spec (URL / path)') as HTMLInputElement;
    expect(input.value).toBe('https://example.com/spec.yaml');
  });

  it('editing validateProxyOpenAPISpec and pressing Enter PUTs the updated value', async () => {
    stubFetchWithConfig(fullConfig());
    const user = userEvent.setup();
    renderDialog();

    await waitFor(() => {
      expect(screen.getByLabelText('OpenAPI spec (URL / path)')).toBeInTheDocument();
    });

    const input = screen.getByLabelText('OpenAPI spec (URL / path)') as HTMLInputElement;
    // Select all existing text and type over it (avoids the blur triggered by user.clear)
    await user.tripleClick(input);
    await user.keyboard('https://new.example.com/api.yaml{Enter}');

    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    const putCalls = fetchMock.mock.calls.filter(
      (c: unknown[]) => (c[1] as RequestInit | undefined)?.method === 'PUT',
    );
    const bodies = putCalls.map((c: unknown[]) => JSON.parse((c[1] as RequestInit).body as string));
    expect(bodies).toContainEqual({ validateProxyOpenAPISpec: 'https://new.example.com/api.yaml' });
  });

  it('renders number fields for chaos auto-halt thresholds', async () => {
    stubFetchWithConfig(fullConfig());
    renderDialog();

    await waitFor(() => {
      expect(screen.getByLabelText('Error threshold')).toBeInTheDocument();
    });

    const thresholdInput = screen.getByLabelText('Error threshold') as HTMLInputElement;
    expect(thresholdInput.value).toBe('50');
    expect(thresholdInput.type).toBe('number');

    const windowInput = screen.getByLabelText('Window (ms)') as HTMLInputElement;
    expect(windowInput.value).toBe('30000');
    expect(windowInput.type).toBe('number');
  });

  it('editing a number field and pressing Enter PUTs the parsed number', async () => {
    stubFetchWithConfig(fullConfig());
    const user = userEvent.setup();
    renderDialog();

    await waitFor(() => {
      expect(screen.getByLabelText('Error threshold')).toBeInTheDocument();
    });

    const input = screen.getByLabelText('Error threshold') as HTMLInputElement;
    // Select all and type new value, commit with Enter
    await user.tripleClick(input);
    await user.keyboard('100{Enter}');

    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    const putCalls = fetchMock.mock.calls.filter(
      (c: unknown[]) => (c[1] as RequestInit | undefined)?.method === 'PUT',
    );
    const bodies = putCalls.map((c: unknown[]) => JSON.parse((c[1] as RequestInit).body as string));
    expect(bodies).toContainEqual({ chaosAutoHaltErrorThreshold: 100 });
  });

  it('clearing a number field does NOT issue a PUT with 0', async () => {
    stubFetchWithConfig(fullConfig());
    const user = userEvent.setup();
    renderDialog();

    await waitFor(() => {
      expect(screen.getByLabelText('Error threshold')).toBeInTheDocument();
    });

    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    // Record the call count after initial GET(s) so we only inspect new PUTs.
    const callsBefore = fetchMock.mock.calls.length;

    const input = screen.getByLabelText('Error threshold') as HTMLInputElement;
    // Select all and delete, then press Enter to commit the empty value.
    await user.tripleClick(input);
    await user.keyboard('{Backspace}{Enter}');

    const newCalls = fetchMock.mock.calls.slice(callsBefore);
    const putCalls = newCalls.filter(
      (c: unknown[]) => (c[1] as RequestInit | undefined)?.method === 'PUT',
    );
    // No PUT should have been issued — an empty number field must not send 0.
    expect(putCalls).toHaveLength(0);
  });

  it('renders the newly-editable runtime-safe boolean toggles', async () => {
    stubFetchWithConfig(fullConfig());
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('Matchers fail fast')).toBeInTheDocument();
    });
    for (const label of [
      'Matchers fail fast',
      'Proxy unmatched requests',
      'Disable logging',
      'Compact log format',
      'Answer CORS preflights on every path',
      'CORS for mocked responses',
    ]) {
      const formControl = screen.getByText(label).closest('label')!;
      expect(within(formControl).getByRole('switch')).toBeInTheDocument();
    }
  });

  it('toggling matchersFailFast PUTs the right partial config', async () => {
    stubFetchWithConfig(fullConfig());
    const user = userEvent.setup();
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('Matchers fail fast')).toBeInTheDocument();
    });

    const formControl = screen.getByText('Matchers fail fast').closest('label')!;
    await user.click(within(formControl).getByRole('switch'));

    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    const putCalls = fetchMock.mock.calls.filter(
      (c: unknown[]) => (c[1] as RequestInit | undefined)?.method === 'PUT',
    );
    const bodies = putCalls.map((c: unknown[]) => JSON.parse((c[1] as RequestInit).body as string));
    expect(bodies).toContainEqual({ matchersFailFast: true });
  });

  it('renders maximumNumberOfRequestToReturnInVerificationFailure as a number field and PUTs the parsed value', async () => {
    stubFetchWithConfig(fullConfig());
    const user = userEvent.setup();
    renderDialog();

    await waitFor(() => {
      expect(screen.getByLabelText('Max requests in verification failure')).toBeInTheDocument();
    });

    const input = screen.getByLabelText('Max requests in verification failure') as HTMLInputElement;
    expect(input.type).toBe('number');
    expect(input.value).toBe('10');

    await user.tripleClick(input);
    await user.keyboard('25{Enter}');

    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    const putCalls = fetchMock.mock.calls.filter(
      (c: unknown[]) => (c[1] as RequestInit | undefined)?.method === 'PUT',
    );
    const bodies = putCalls.map((c: unknown[]) => JSON.parse((c[1] as RequestInit).body as string));
    expect(bodies).toContainEqual({ maximumNumberOfRequestToReturnInVerificationFailure: 25 });
  });

  it('keeps startup-only maxExpectations read-only (not an editable control)', async () => {
    stubFetchWithConfig(fullConfig());
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('All other settings (read-only)')).toBeInTheDocument();
    });

    // maxExpectations sizes a ring buffer at construction time → must stay read-only.
    expect(screen.getByText('maxExpectations')).toBeInTheDocument();
    // It must not be rendered as an editable control with a label.
    expect(screen.queryByLabelText('maxExpectations')).not.toBeInTheDocument();
  });

  it('renders group headers for descriptor groups', async () => {
    stubFetchWithConfig(fullConfig());
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('Developer / data')).toBeInTheDocument();
    });
    expect(screen.getByText('Validation proxy')).toBeInTheDocument();
    expect(screen.getByText('Chaos auto-halt')).toBeInTheDocument();
    expect(screen.getByText('Matching & proxying')).toBeInTheDocument();
    expect(screen.getByText('Logging')).toBeInTheDocument();
    expect(screen.getByText('CORS')).toBeInTheDocument();
  });

  it('excludes editable keys from the read-only table', async () => {
    stubFetchWithConfig(fullConfig());
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('All other settings (read-only)')).toBeInTheDocument();
    });

    // maxLogEntries is NOT in the editable list — should appear in the read-only table
    expect(screen.getByText('maxLogEntries')).toBeInTheDocument();
    expect(screen.getByText('1000')).toBeInTheDocument();

    // devMode is now read-only (startup-only) — it SHOULD appear in the read-only table
    const readOnlyTable = screen.getByText('All other settings (read-only)').parentElement!;
    const tableEl = readOnlyTable.querySelector('table')!;
    const cellTexts = Array.from(tableEl.querySelectorAll('td')).map((td) => td.textContent);
    expect(cellTexts).toContain('devMode');
    expect(cellTexts).not.toContain('logLevel');
    expect(cellTexts).not.toContain('validateProxyOpenAPISpec');
  });

  it('opens the Server Info tab, fetches effective config + status, and renders source tiers', async () => {
    const effective = [
      { name: 'mockserver.logLevel', value: 'INFO', source: 'system-property' },
      { name: 'mockserver.maxExpectations', value: '(default)', source: 'default' },
      { name: 'mockserver.proxyAuthenticationPassword', value: '***REDACTED***', source: 'environment-variable' },
    ];
    const status = { ports: [1080, 1443], version: '7.3.0', gitHash: 'abc1234' };
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/mockserver/config')) return { ok: true, json: async () => effective };
      if (url.endsWith('/mockserver/status') && init?.method === 'PUT') return { ok: true, json: async () => status };
      // GET /mockserver/configuration (settings tab)
      return { ok: true, json: async () => fullConfig() };
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('Log level')).toBeInTheDocument();
    });

    await user.click(screen.getByRole('tab', { name: 'Server Info' }));

    // Both endpoints are fetched when the tab opens.
    await waitFor(() => {
      const urls = fetchMock.mock.calls.map((c) => String(c[0]));
      expect(urls.some((u) => u.endsWith('/mockserver/config'))).toBe(true);
      expect(urls.some((u) => u.endsWith('/mockserver/status'))).toBe(true);
    });

    // Source tier group labels are shown, values grouped under them.
    await waitFor(() => {
      expect(screen.getByText(/System property/)).toBeInTheDocument();
    });
    expect(screen.getByText(/Default/)).toBeInTheDocument();
    expect(screen.getByText(/Environment variable/)).toBeInTheDocument();
    expect(screen.getByText('mockserver.logLevel')).toBeInTheDocument();
    expect(screen.getByText('***REDACTED***')).toBeInTheDocument();
    // Bound ports rendered as chips.
    expect(screen.getByText('1080')).toBeInTheDocument();
    expect(screen.getByText('1443')).toBeInTheDocument();
  });

  it('shows the built-in default beside an overridden property, and none for one at its default', async () => {
    const effective = [
      { name: 'mockserver.maxExpectations', value: '4242', source: 'system-property', default: '5000' },
      { name: 'mockserver.llmApiKey', value: '***REDACTED***', source: 'environment-variable', default: '***REDACTED***' },
      { name: 'mockserver.metricsEnabled', value: 'false', source: 'default' },
    ];
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/mockserver/config')) return { ok: true, json: async () => effective };
      if (url.endsWith('/mockserver/status') && init?.method === 'PUT') return { ok: true, json: async () => ({ ports: [1080] }) };
      return { ok: true, json: async () => fullConfig() };
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    renderDialog();
    await waitFor(() => { expect(screen.getByText('Log level')).toBeInTheDocument(); });
    await user.click(screen.getByRole('tab', { name: 'Server Info' }));

    await waitFor(() => { expect(screen.getByText('mockserver.maxExpectations')).toBeInTheDocument(); });
    expect(screen.getByText('mockserver.maxExpectations').closest('tr')!.textContent).toContain('4242(default: 5000)');
    expect(screen.getByText('mockserver.llmApiKey').closest('tr')!.textContent).toContain('***REDACTED***(default: ***REDACTED***)');
    expect(screen.getByText('mockserver.metricsEnabled').closest('tr')!.textContent).not.toContain('default:');
  });

  it('lists unrecognised configuration keys first, with the key each was probably meant to be', async () => {
    const effective = [
      { name: 'mockserver.logLevel', value: 'INFO', source: 'system-property' },
      { name: 'MOCKSERVER_METRICS_ENABLE', value: '(ignored)', source: 'unrecognised', setAs: 'environment variable', suggestions: ['MOCKSERVER_METRICS_ENABLED'] },
      { name: 'mockserver.typoOne', value: '(ignored)', source: 'unrecognised', setAs: 'system property', suggestions: [] },
    ];
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/mockserver/config')) return { ok: true, json: async () => effective };
      if (url.endsWith('/mockserver/status') && init?.method === 'PUT') return { ok: true, json: async () => ({ ports: [1080] }) };
      return { ok: true, json: async () => fullConfig() };
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    renderDialog();
    await waitFor(() => { expect(screen.getByText('Log level')).toBeInTheDocument(); });
    await user.click(screen.getByRole('tab', { name: 'Server Info' }));

    await waitFor(() => { expect(screen.getByText('Unrecognised (ignored) (2)')).toBeInTheDocument(); });
    const typoRow = screen.getByText('MOCKSERVER_METRICS_ENABLE').closest('tr')!;
    expect(typoRow.textContent).toContain('(ignored) did you mean MOCKSERVER_METRICS_ENABLED?');
    const unsuggestedRow = screen.getByText('mockserver.typoOne').closest('tr')!;
    expect(unsuggestedRow.textContent).not.toContain('did you mean');
    // the ignored keys are listed before every resolved source tier
    const unrecognisedLabel = screen.getByText('Unrecognised (ignored) (2)');
    const systemPropertyLabel = screen.getByText(/System property \(1\)/);
    expect(unrecognisedLabel.compareDocumentPosition(systemPropertyLabel) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('renders the Proxy setup section and binds an additional port on the Server Info tab', async () => {
    const effective = [{ name: 'mockserver.logLevel', value: 'INFO', source: 'system-property' }];
    const proxy = {
      caCertificatePath: '/data/ca.pem',
      caCertificatePem: '-----BEGIN CERTIFICATE-----\nabc\n-----END CERTIFICATE-----',
      httpsProxy: 'http://127.0.0.1:1080',
      environmentVariables: { unix: 'export https_proxy=http://127.0.0.1:1080', powershell: '$env:HTTPS_PROXY="http://127.0.0.1:1080"' },
      usingDefaultCa: true,
      warning: null,
    };
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/mockserver/config')) return { ok: true, json: async () => effective };
      if (url.endsWith('/mockserver/proxyConfiguration')) return { ok: true, json: async () => proxy };
      if (url.endsWith('/mockserver/bind') && init?.method === 'PUT') return { ok: true, json: async () => ({ ports: [1080, 2020] }) };
      if (url.endsWith('/mockserver/status') && init?.method === 'PUT') return { ok: true, json: async () => ({ ports: [1080] }) };
      return { ok: true, json: async () => fullConfig() };
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    renderDialog();
    await waitFor(() => { expect(screen.getByText('Log level')).toBeInTheDocument(); });
    await user.click(screen.getByRole('tab', { name: 'Server Info' }));

    // Proxy setup section renders the HTTPS proxy URL + env snippets.
    await waitFor(() => { expect(screen.getByText('Proxy setup')).toBeInTheDocument(); });
    expect(screen.getByText('http://127.0.0.1:1080')).toBeInTheDocument();
    expect(screen.getByText(/export https_proxy=/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Download CA/ })).toBeInTheDocument();

    // Bind an additional port — chips refresh to include the new port.
    const bindInput = screen.getByLabelText('Bind additional port');
    await user.type(bindInput, '2020');
    await user.click(screen.getByRole('button', { name: 'Bind' }));

    await waitFor(() => {
      const bindCall = fetchMock.mock.calls.find(
        (c) => String(c[0]).endsWith('/mockserver/bind') && (c[1] as RequestInit | undefined)?.method === 'PUT',
      );
      expect(bindCall).toBeTruthy();
      expect(JSON.parse((bindCall![1] as RequestInit).body as string)).toEqual({ ports: [2020] });
    });
    await waitFor(() => { expect(screen.getByText('2020')).toBeInTheDocument(); });
  });

  it('shows a proxy-setup error without hiding the effective config', async () => {
    const effective = [{ name: 'mockserver.logLevel', value: 'INFO', source: 'system-property' }];
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/mockserver/config')) return { ok: true, json: async () => effective };
      if (url.endsWith('/mockserver/proxyConfiguration')) return { ok: false, status: 400, statusText: 'Bad Request', text: async () => '{"error":"failed to get proxy configuration"}' };
      if (url.endsWith('/mockserver/status') && init?.method === 'PUT') return { ok: true, json: async () => ({ ports: [1080] }) };
      return { ok: true, json: async () => fullConfig() };
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    renderDialog();
    await waitFor(() => { expect(screen.getByText('Log level')).toBeInTheDocument(); });
    await user.click(screen.getByRole('tab', { name: 'Server Info' }));

    // The effective config still renders even though the proxy fetch failed.
    await waitFor(() => { expect(screen.getByText('mockserver.logLevel')).toBeInTheDocument(); });
    expect(screen.getByText('Proxy setup')).toBeInTheDocument();
  });

  it('shows an error on the Server Info tab when the config fetch fails', async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/mockserver/config')) return { ok: false, status: 500, statusText: 'Internal Server Error', text: async () => 'boom' };
      if (url.endsWith('/mockserver/status') && init?.method === 'PUT') return { ok: true, json: async () => ({ ports: [1080] }) };
      return { ok: true, json: async () => fullConfig() };
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('Log level')).toBeInTheDocument();
    });

    await user.click(screen.getByRole('tab', { name: 'Server Info' }));

    // The Server Info panel surfaces a humanized error rather than the config tables.
    await waitFor(() => {
      expect(screen.queryByText('mockserver.logLevel')).not.toBeInTheDocument();
    });
    expect(screen.queryByText('Loading server info…')).not.toBeInTheDocument();
  });

  it('shows the error alert when a PUT fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === 'PUT') {
          return { ok: false, status: 400, statusText: 'Bad Request', text: async () => 'invalid value' };
        }
        return { ok: true, json: async () => fullConfig() };
      }),
    );
    const user = userEvent.setup();
    renderDialog();

    await waitFor(() => {
      expect(screen.getByText('Matchers fail fast')).toBeInTheDocument();
    });

    const devModeLabel = screen.getByText('Matchers fail fast');
    const formControl = devModeLabel.closest('label')!;
    const switchInput = within(formControl).getByRole('switch');
    await user.click(switchInput);

    await waitFor(() => {
      expect(screen.getByText('invalid value')).toBeInTheDocument();
    });
  });
});
