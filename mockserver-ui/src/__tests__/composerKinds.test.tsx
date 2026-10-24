/**
 * Tests for the Mocks (Composer) page restructure:
 * - Kind selector shows HTTP, LLM Conversation, gRPC, DNS, MCP, Import
 * - Each kind shows only its valid action types (no cross-kind leakage)
 * - Switching kind resets actionType to a valid default
 * - Loading an existing expectation infers the correct kind
 * - MCP kind embeds the derived-tools section
 * - gRPC / DNS kind shows the info banner
 * - Store: 'mcp-tools' view migrates to 'composer'
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import ComposerView from '../components/ComposerView';
import { useDashboardStore } from '../store';

vi.mock('../lib/mcpClient', () => ({
  buildBaseUrl: () => 'http://127.0.0.1:1080',
  callMcpTool: vi.fn().mockResolvedValue({ ok: true, result: { tools: [], count: 0 } }),
}));

vi.mock('../lib/conversationCodegen', () => ({
  listConversationScenarios: () => [],
}));

const params = { host: '127.0.0.1', port: '1080', secure: false };

function renderComposer() {
  // These tests exercise the full ("Advanced") form — the kind selector, action
  // radios, existing-mocks list, and cross-cutting panels. The composer now
  // defaults to a minimal "Quick mock" view, so seed the session preference to
  // Advanced (read by getInitialMode on mount) before rendering.
  try { globalThis.sessionStorage?.setItem('mockserver-composer-mode', 'advanced'); } catch { /* noop */ }
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <ComposerView connectionParams={params} />
    </ThemeProvider>,
  );
}

/**
 * Helper: get the bold label text for each action-type radio inside the
 * "2 . Respond with" section. The rendered DOM uses a Typography with
 * fontWeight 600 for the label and a lighter caption for the description.
 * We grab only the first text node (the bold label) via the inner structure.
 */
function getActionRadioLabels(): string[] {
  const respondHeading = screen.getByText(/2 · Respond with/);
  const respondSection = respondHeading.closest('[class*="MuiPaper"]')!;
  const radios = within(respondSection as HTMLElement).getAllByRole('radio');
  return radios.map((r) => {
    const label = r.closest('label');
    if (!label) return '';
    // The action label is the FIRST <p> / <span> with fontWeight 600 (MUI body2).
    // Simplest: grab the first child text within the label's Box wrapper.
    // The Box contains two Typography elements: the bold label and the caption.
    // Use the aria-label or the first element child's text.
    const box = label.querySelector('[class*="MuiBox"]');
    if (box && box.firstElementChild) {
      return box.firstElementChild.textContent?.trim() ?? '';
    }
    return '';
  });
}

function respondRadio(label: RegExp): HTMLElement {
  const respondSection = screen.getByText(/2 · Respond with/).closest('[class*="MuiPaper"]')!;
  return within(respondSection as HTMLElement).getByLabelText(label);
}

describe('ComposerView kinds', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      activeExpectations: [],
    });
  });

  it('shows the page title as "Mocks"', () => {
    renderComposer();
    expect(screen.getByText('Mocks')).toBeInTheDocument();
  });

  it('shows all six kind options including DNS and Import', () => {
    renderComposer();
    expect(screen.getByLabelText('HTTP')).toBeInTheDocument();
    expect(screen.getByLabelText('LLM Conversation')).toBeInTheDocument();
    expect(screen.getByLabelText('gRPC')).toBeInTheDocument();
    expect(screen.getByLabelText('DNS')).toBeInTheDocument();
    expect(screen.getByLabelText('MCP')).toBeInTheDocument();
    expect(screen.getByLabelText('Import')).toBeInTheDocument();
  });

  it('defaults to HTTP kind', () => {
    renderComposer();
    const httpRadio = screen.getByLabelText('HTTP');
    expect(httpRadio).toBeChecked();
  });

  it('switches to gRPC kind and shows info banner', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('gRPC'));
    expect(screen.getByText(/gRPC requests are transcoded to HTTP/i)).toBeInTheDocument();
  });

  it('switches to DNS kind and shows info banner', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('DNS'));
    expect(screen.getByText(/DNS expectations are served by the DNS handler/i)).toBeInTheDocument();
    // DNS kind shows DNS matcher fields instead of HTTP matcher fields
    expect(screen.getByLabelText('DNS name')).toBeInTheDocument();
    expect(screen.getByLabelText('Record type')).toBeInTheDocument();
    expect(screen.getByLabelText('Record class')).toBeInTheDocument();
    expect(screen.queryByLabelText('Method')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Path')).not.toBeInTheDocument();
  });

  it('switches to MCP kind and shows info banner', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('MCP'));
    expect(screen.getByText(/MCP tools are generated automatically/i)).toBeInTheDocument();
  });

  it('MCP kind embeds the MCP tools section', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('MCP'));
    // McpToolsPanel renders an "MCP Tools" heading
    expect(screen.getByText('MCP Tools')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Action-type filtering per kind
// ---------------------------------------------------------------------------

describe('ComposerView action-type filtering', () => {
  beforeEach(() => {
    useDashboardStore.setState({ activeExpectations: [] });
  });

  it('HTTP kind does NOT list dns_response or grpc_stream', () => {
    renderComposer();
    const labels = getActionRadioLabels();
    expect(labels).toContain('Static HTTP response');
    expect(labels).toContain('Forward to upstream');
    expect(labels).not.toContain('DNS response');
    expect(labels).not.toContain('gRPC stream response');
  });

  it('HTTP kind lists all HTTP action types', () => {
    renderComposer();
    const labels = getActionRadioLabels();
    const expected = [
      'Static HTTP response', 'Forward to upstream', 'Forward with override',
      'Forward with fallback', 'Class callback', 'Response template',
      'Error / fault injection', 'WebSocket response', 'SSE response',
      'Binary response', 'Forward template', 'Forward class callback',
    ];
    for (const l of expected) {
      expect(labels).toContain(l);
    }
    expect(labels).toHaveLength(expected.length);
  });

  it('gRPC kind lists grpc_stream and static only', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('gRPC'));
    const labels = getActionRadioLabels();
    expect(labels).toContain('gRPC stream response');
    expect(labels).toContain('Static HTTP response');
    expect(labels).toHaveLength(2);
    // Must not have HTTP-only or DNS types
    expect(labels).not.toContain('Forward to upstream');
    expect(labels).not.toContain('DNS response');
  });

  it('DNS kind lists dns_response only', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('DNS'));
    const labels = getActionRadioLabels();
    expect(labels).toContain('DNS response');
    expect(labels).toHaveLength(1);
  });

  it('MCP kind lists static only', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('MCP'));
    const labels = getActionRadioLabels();
    expect(labels).toContain('Static HTTP response');
    expect(labels).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// Kind-change resets actionType to a valid default
// ---------------------------------------------------------------------------

describe('ComposerView kind-change default reset', () => {
  beforeEach(() => {
    useDashboardStore.setState({ activeExpectations: [], pendingEditExpectation: null });
  });

  it('switching a fresh form to gRPC selects the gRPC default (grpc_stream)', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    // The untouched HTTP default ('static') is replaced by the gRPC default,
    // matching what New / clear gives in the gRPC kind.
    await user.click(screen.getByLabelText('gRPC'));
    expect(respondRadio(/gRPC stream response/)).toBeChecked();
    expect(respondRadio(/Static HTTP response/)).not.toBeChecked();
  });

  it('switching from HTTP (forward selected) to DNS resets to dns_response', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    // Select "Forward to upstream" on HTTP kind
    await user.click(screen.getByLabelText(/Forward to upstream/));
    // Now switch to DNS — "forward" is not valid for DNS, should reset to dns_response
    await user.click(screen.getByLabelText('DNS'));
    const respondSection = screen.getByText(/2 · Respond with/).closest('[class*="MuiPaper"]')!;
    const dnsRadio = within(respondSection as HTMLElement).getByLabelText(/DNS response/);
    expect(dnsRadio).toBeChecked();
  });

  it('a user-picked static response survives switching HTTP to gRPC', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    // Explicitly pick Static (via another action, since re-clicking the checked radio is a no-op).
    await user.click(screen.getByLabelText(/Forward to upstream/));
    await user.click(screen.getByLabelText(/Static HTTP response/));
    await user.click(screen.getByLabelText('gRPC'));
    expect(respondRadio(/Static HTTP response/)).toBeChecked();
  });

  it('a static response picked in gRPC survives a round trip through MCP', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('gRPC'));
    await user.click(screen.getByLabelText(/Static HTTP response/));
    await user.click(screen.getByLabelText('MCP'));
    expect(respondRadio(/Static HTTP response/)).toBeChecked();
    await user.click(screen.getByLabelText('gRPC'));
    expect(respondRadio(/Static HTTP response/)).toBeChecked();
  });

  it('an untouched default follows each kind switch', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('MCP'));
    expect(respondRadio(/Static HTTP response/)).toBeChecked();
    await user.click(screen.getByLabelText('gRPC'));
    expect(respondRadio(/gRPC stream response/)).toBeChecked();
    await user.click(screen.getByLabelText('DNS'));
    expect(respondRadio(/DNS response/)).toBeChecked();
    await user.click(screen.getByLabelText('HTTP'));
    expect(respondRadio(/Static HTTP response/)).toBeChecked();
  });

  it('switching a fresh form from HTTP to MCP keeps static', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('MCP'));
    expect(respondRadio(/Static HTTP response/)).toBeChecked();
  });

  it('a user-picked HTTP action not valid in MCP resets to static', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText(/Forward to upstream/));
    await user.click(screen.getByLabelText('MCP'));
    expect(respondRadio(/Static HTTP response/)).toBeChecked();
  });

  it('a static action loaded from the mocks list survives switching to gRPC', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'loaded-static-001',
        value: {
          id: 'loaded-static-001',
          httpRequest: { method: 'POST', path: '/pkg.Svc/M' },
          httpResponse: { statusCode: 200, body: 'x' },
        },
      }],
    });
    renderComposer();
    await user.click(within(screen.getByTestId('existing-mocks-list')).getByText(/\/pkg\.Svc\/M/));
    await user.click(screen.getByLabelText('gRPC'));
    expect(respondRadio(/Static HTTP response/)).toBeChecked();
  });

  it('a static action handed off for edit survives switching to gRPC', async () => {
    const user = userEvent.setup({ delay: null });
    // Not in activeExpectations, so the hand-off takes its direct-load path.
    useDashboardStore.setState({
      pendingEditExpectation: {
        id: 'handoff-static-001',
        httpRequest: { method: 'POST', path: '/pkg.Svc/M' },
        httpResponse: { statusCode: 200, body: 'x' },
      },
    });
    renderComposer();
    await waitFor(() => expect(useDashboardStore.getState().pendingEditExpectation).toBeNull());
    expect((screen.getByLabelText('Path') as HTMLInputElement).value).toBe('/pkg.Svc/M');
    await user.click(screen.getByLabelText('gRPC'));
    expect(respondRadio(/Static HTTP response/)).toBeChecked();
  });

  it('switching from gRPC (grpc_stream selected) to HTTP resets to static', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('gRPC'));
    // Select grpc_stream
    await user.click(screen.getByLabelText(/gRPC stream response/));
    // Switch to HTTP — grpc_stream is not valid for HTTP
    await user.click(screen.getByLabelText('HTTP'));
    const respondSection = screen.getByText(/2 · Respond with/).closest('[class*="MuiPaper"]')!;
    const staticRadio = within(respondSection as HTMLElement).getByLabelText(/Static HTTP response/);
    expect(staticRadio).toBeChecked();
  });

  it('leaving gRPC clears the gRPC-shaped matcher path/method when not customised', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    // Selecting gRPC pre-shapes the matcher to POST /package.Service/Method
    await user.click(screen.getByLabelText('gRPC'));
    expect((screen.getByLabelText('Path') as HTMLInputElement).value).toBe('/package.Service/Method');
    // Switching to DNS shows the DNS matcher panel (no Path field) — verify
    // the DNS name field is shown instead.
    await user.click(screen.getByLabelText('DNS'));
    expect(screen.getByLabelText('DNS name')).toBeInTheDocument();
    expect(screen.queryByLabelText('Path')).not.toBeInTheDocument();
    // Switching from DNS to HTTP shows the cleared path (gRPC default was removed)
    await user.click(screen.getByLabelText('HTTP'));
    expect((screen.getByLabelText('Path') as HTMLInputElement).value).toBe('');
    // And switching to MCP from gRPC likewise clears it
    await user.click(screen.getByLabelText('gRPC'));
    await user.click(screen.getByLabelText('MCP'));
    expect((screen.getByLabelText('Path') as HTMLInputElement).value).toBe('');
  });

  it('preserves a user-customised path when leaving gRPC', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('gRPC'));
    const pathField = screen.getByLabelText('Path') as HTMLInputElement;
    await user.clear(pathField);
    await user.type(pathField, '/my.custom.Service/Call');
    await user.click(screen.getByLabelText('HTTP'));
    expect((screen.getByLabelText('Path') as HTMLInputElement).value).toBe('/my.custom.Service/Call');
  });
});

// ---------------------------------------------------------------------------
// Loading existing expectations infers the correct kind
// ---------------------------------------------------------------------------

describe('ComposerView load-existing via list', () => {
  it('clicking a DNS mock row on the DNS kind populates DNS matcher fields', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'dns-exp-001',
        value: {
          id: 'dns-exp-001',
          httpRequest: { dnsName: 'example.com', dnsType: 'A', dnsClass: 'IN' },
          dnsResponse: { responseCode: 'NOERROR', answerRecords: [] },
        },
      }],
    });
    renderComposer();
    // Switch to DNS kind first — the list is scoped per kind
    await user.click(screen.getByLabelText('DNS'));
    // The list should show the DNS expectation
    const listEl = screen.getByTestId('existing-mocks-list');
    const row = within(listEl).getByText(/example\.com/);
    await user.click(row);
    // Kind stays DNS
    expect(screen.getByLabelText('DNS')).toBeChecked();
    // Action type should be dns_response
    const respondSection = screen.getByText(/2 · Respond with/).closest('[class*="MuiPaper"]')!;
    const dnsActionRadio = within(respondSection as HTMLElement).getByLabelText(/DNS response/);
    expect(dnsActionRadio).toBeChecked();
    // DNS matcher fields should be populated
    expect((screen.getByLabelText('DNS name') as HTMLInputElement).value).toBe('example.com');
  });

  it('clicking a gRPC mock row on the gRPC kind loads it', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'grpc-exp-001',
        value: {
          id: 'grpc-exp-001',
          httpRequest: { method: 'POST', path: '/pkg.Service/Method' },
          grpcStreamResponse: { statusName: 'OK', messages: [] },
        },
      }],
    });
    renderComposer();
    await user.click(screen.getByLabelText('gRPC'));
    const listEl = screen.getByTestId('existing-mocks-list');
    const row = within(listEl).getByText(/\/pkg\.Service\/Method/);
    await user.click(row);
    expect(screen.getByLabelText('gRPC')).toBeChecked();
  });

  it('clicking an HTTP mock row on the HTTP kind loads it', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'http-exp-001',
        value: {
          id: 'http-exp-001',
          httpRequest: { method: 'GET', path: '/api/test' },
          httpResponse: { statusCode: 200, body: '{}' },
        },
      }],
    });
    renderComposer();
    // HTTP is the default kind — the list should show the HTTP expectation
    const listEl = screen.getByTestId('existing-mocks-list');
    const row = within(listEl).getByText(/GET \/api\/test/);
    await user.click(row);
    expect(screen.getByLabelText('HTTP')).toBeChecked();
    // Editing indicator should appear
    expect(screen.getByText(/Editing http-exp-/)).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// DNS kind — dedicated matcher panel (not HTTP matcher)
// ---------------------------------------------------------------------------

describe('ComposerView DNS matcher panel', () => {
  beforeEach(() => {
    useDashboardStore.setState({ activeExpectations: [] });
  });

  it('DNS kind shows DNS name/type/class fields and does NOT show HTTP method/path/body fields', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('DNS'));
    // DNS-specific fields present
    expect(screen.getByLabelText('DNS name')).toBeInTheDocument();
    expect(screen.getByLabelText('Record type')).toBeInTheDocument();
    expect(screen.getByLabelText('Record class')).toBeInTheDocument();
    // HTTP-specific fields absent
    expect(screen.queryByLabelText('Method')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Path')).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Headers/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Cookies/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Body type/)).not.toBeInTheDocument();
    // Generic expectation-level fields still present
    expect(screen.getByLabelText(/Expectation ID/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Priority/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Times/)).toBeInTheDocument();
  });

  it('switching from DNS to HTTP shows HTTP matcher fields again', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    await user.click(screen.getByLabelText('DNS'));
    expect(screen.queryByLabelText('Method')).not.toBeInTheDocument();
    await user.click(screen.getByLabelText('HTTP'));
    expect(screen.getByLabelText('Method')).toBeInTheDocument();
    expect(screen.getByLabelText('Path')).toBeInTheDocument();
    expect(screen.queryByLabelText('DNS name')).not.toBeInTheDocument();
  });

  it('loading an existing DNS expectation via list populates DNS matcher fields', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'dns-full-001',
        value: {
          id: 'dns-full-001',
          httpRequest: { dnsName: 'api.example.com', dnsType: 'AAAA', dnsClass: 'CH' },
          dnsResponse: { responseCode: 'NOERROR', answerRecords: [] },
        },
      }],
    });
    renderComposer();
    // Switch to DNS kind to see DNS expectations in the list
    await user.click(screen.getByLabelText('DNS'));
    const listEl = screen.getByTestId('existing-mocks-list');
    const row = within(listEl).getByText(/api\.example\.com/);
    await user.click(row);

    // Kind should be DNS
    expect(screen.getByLabelText('DNS')).toBeChecked();
    // DNS matcher populated
    expect((screen.getByLabelText('DNS name') as HTMLInputElement).value).toBe('api.example.com');
  });
});

// ---------------------------------------------------------------------------
// Existing mocks list — replaces the old dropdown
// ---------------------------------------------------------------------------

describe('Existing mocks list', () => {
  beforeEach(() => {
    useDashboardStore.setState({ activeExpectations: [] });
  });

  it('the old "Edit existing or add new" dropdown is gone', () => {
    renderComposer();
    expect(screen.queryByLabelText(/Edit existing or add new/)).not.toBeInTheDocument();
  });

  it('shows per-kind list with rows scoped to the selected kind', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [
        {
          key: 'http-001',
          value: {
            id: 'http-001',
            httpRequest: { method: 'GET', path: '/users' },
            httpResponse: { statusCode: 200, body: '[]' },
          },
        },
        {
          key: 'dns-001',
          value: {
            id: 'dns-001',
            httpRequest: { dnsName: 'test.local', dnsType: 'A' },
            dnsResponse: { responseCode: 'NOERROR', answerRecords: [] },
          },
        },
        {
          key: 'grpc-001',
          value: {
            id: 'grpc-001',
            httpRequest: { method: 'POST', path: '/svc.Foo/Bar' },
            grpcStreamResponse: { statusName: 'OK', messages: [] },
          },
        },
      ],
    });
    renderComposer();

    // On HTTP kind: should see the HTTP mock, not DNS or gRPC
    const list = screen.getByTestId('existing-mocks-list');
    expect(within(list).getByText(/GET \/users/)).toBeInTheDocument();
    expect(within(list).queryByText(/test\.local/)).not.toBeInTheDocument();
    expect(within(list).queryByText(/\/svc\.Foo\/Bar/)).not.toBeInTheDocument();

    // Switch to DNS — should see DNS mock only
    await user.click(screen.getByLabelText('DNS'));
    const dnsList = screen.getByTestId('existing-mocks-list');
    expect(within(dnsList).getByText(/test\.local/)).toBeInTheDocument();
    expect(within(dnsList).queryByText(/GET \/users/)).not.toBeInTheDocument();

    // Switch to gRPC — should see gRPC mock only
    await user.click(screen.getByLabelText('gRPC'));
    const grpcList = screen.getByTestId('existing-mocks-list');
    expect(within(grpcList).getByText(/\/svc\.Foo\/Bar/)).toBeInTheDocument();
    expect(within(grpcList).queryByText(/test\.local/)).not.toBeInTheDocument();
  });

  it('clicking a row loads the expectation and shows "Editing" indicator', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'http-load-001',
        value: {
          id: 'http-load-001',
          httpRequest: { method: 'POST', path: '/submit' },
          httpResponse: { statusCode: 201, body: '{"ok":true}' },
        },
      }],
    });
    renderComposer();
    const list = screen.getByTestId('existing-mocks-list');
    await user.click(within(list).getByText(/POST \/submit/));
    // Editing indicator
    expect(screen.getByText(/Editing http-load/)).toBeInTheDocument();
    // Form should be populated with the loaded expectation
    expect((screen.getByLabelText(/Expectation ID/) as HTMLInputElement).value).toBe('http-load-001');
  });

  it('"New / clear" button resets the form and deselects', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'http-clear-001',
        value: {
          id: 'http-clear-001',
          httpRequest: { method: 'DELETE', path: '/remove' },
          httpResponse: { statusCode: 204 },
        },
      }],
    });
    renderComposer();
    const list = screen.getByTestId('existing-mocks-list');
    // Click to load
    await user.click(within(list).getByText(/DELETE \/remove/));
    expect(screen.getByText(/Editing http-clear/)).toBeInTheDocument();
    // Click "New / clear"
    await user.click(screen.getByText('New / clear'));
    // Editing indicator gone
    expect(screen.queryByText(/Editing http-clear/)).not.toBeInTheDocument();
    // Form should be reset
    expect((screen.getByLabelText(/Expectation ID/) as HTMLInputElement).value).toBe('');
  });

  it('shows empty state when no mocks of the selected kind exist', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'http-only-001',
        value: {
          id: 'http-only-001',
          httpRequest: { method: 'GET', path: '/data' },
          httpResponse: { statusCode: 200 },
        },
      }],
    });
    renderComposer();
    // HTTP list shows the expectation
    expect(screen.getByText(/GET \/data/)).toBeInTheDocument();
    // Switch to DNS — no DNS expectations exist
    await user.click(screen.getByLabelText('DNS'));
    expect(screen.getByText(/No DNS mocks yet/)).toBeInTheDocument();
  });

  it('MCP kind shows both the editable list and the McpToolsPanel', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'mcp-http-001',
        value: {
          id: 'mcp-http-001',
          httpRequest: { method: 'GET', path: '/tool-endpoint' },
          httpResponse: { statusCode: 200, body: '{"result":"ok"}' },
        },
      }],
    });
    renderComposer();
    await user.click(screen.getByLabelText('MCP'));
    // Editable list shows the HTTP response expectation
    const list = screen.getByTestId('existing-mocks-list');
    expect(within(list).getByText(/GET \/tool-endpoint/)).toBeInTheDocument();
    // McpToolsPanel is also rendered
    expect(screen.getByText('MCP Tools')).toBeInTheDocument();
  });

  it('clicking an MCP list row loads it WITHOUT switching away from the MCP kind', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'mcp-http-002',
        value: {
          id: 'mcp-http-002',
          httpRequest: { method: 'GET', path: '/tool-endpoint' },
          httpResponse: { statusCode: 200, body: '{"result":"ok"}' },
        },
      }],
    });
    renderComposer();
    await user.click(screen.getByLabelText('MCP'));
    const list = screen.getByTestId('existing-mocks-list');
    await user.click(within(list).getByText(/GET \/tool-endpoint/));
    // The MCP kind must stay selected (MCP is a view over standard HTTP responses)
    expect(screen.getByLabelText('MCP')).toBeChecked();
    // McpToolsPanel remains visible
    expect(screen.getByText('MCP Tools')).toBeInTheDocument();
  });

  it('per-kind scoping: HTTP expectation does NOT appear in DNS list and vice versa', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [
        {
          key: 'http-scope-001',
          value: {
            id: 'http-scope-001',
            httpRequest: { method: 'GET', path: '/health' },
            httpResponse: { statusCode: 200 },
          },
        },
        {
          key: 'dns-scope-001',
          value: {
            id: 'dns-scope-001',
            httpRequest: { dnsName: 'ns.example.com', dnsType: 'MX' },
            dnsResponse: { responseCode: 'NOERROR', answerRecords: [] },
          },
        },
      ],
    });
    renderComposer();

    // On HTTP kind: only HTTP mock visible
    const httpList = screen.getByTestId('existing-mocks-list');
    expect(within(httpList).getByText(/GET \/health/)).toBeInTheDocument();
    expect(within(httpList).queryByText(/ns\.example\.com/)).not.toBeInTheDocument();
    expect(screen.getByText(/Existing HTTP mocks/)).toBeInTheDocument();

    // On DNS kind: only DNS mock visible
    await user.click(screen.getByLabelText('DNS'));
    const dnsList = screen.getByTestId('existing-mocks-list');
    expect(within(dnsList).getByText(/ns\.example\.com/)).toBeInTheDocument();
    expect(within(dnsList).queryByText(/GET \/health/)).not.toBeInTheDocument();
    expect(screen.getByText(/Existing DNS mocks/)).toBeInTheDocument();
  });

  // The header used to carry a count of the filtered mocks. It was removed because
  // the dashboard WebSocket sends at most EXPECTATION_UPDATE_ITEM_LIMIT (100)
  // expectations and drops the rest, so the number was the size of the transport
  // window rather than the number of mocks on the server -- it pinned at 100 and
  // stopped moving. What the count was standing in for is asserted directly here
  // instead: every mock in the window is listed, none silently dropped.
  it('lists every mock of the selected kind', () => {
    useDashboardStore.setState({
      activeExpectations: [
        {
          key: 'http-count-001',
          value: {
            id: 'http-count-001',
            httpRequest: { method: 'GET', path: '/a' },
            httpResponse: { statusCode: 200 },
          },
        },
        {
          key: 'http-count-002',
          value: {
            id: 'http-count-002',
            httpRequest: { method: 'POST', path: '/b' },
            httpResponse: { statusCode: 201, body: '' },
          },
        },
        {
          key: 'http-count-003',
          value: {
            id: 'http-count-003',
            httpRequest: { method: 'PUT', path: '/c' },
            httpResponse: { statusCode: 200 },
          },
        },
      ],
    });
    renderComposer();
    const list = screen.getByTestId('existing-mocks-list');
    expect(within(list).getByText(/GET \/a/)).toBeInTheDocument();
    expect(within(list).getByText(/POST \/b/)).toBeInTheDocument();
    expect(within(list).getByText(/PUT \/c/)).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Side-effects (before & after actions) panel
// ---------------------------------------------------------------------------

describe('ComposerView side-effects panel', () => {
  beforeEach(() => {
    useDashboardStore.setState({ activeExpectations: [] });
  });

  it('shows the "Before & after actions" toggle on the HTTP kind', () => {
    renderComposer();
    expect(screen.getByText(/Before & after actions/)).toBeInTheDocument();
  });

  it('toggling on shows the side-effects panel with "Add action" button', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    // The panel content is collapsed by default
    expect(screen.queryByTestId('add-side-effect')).not.toBeInTheDocument();
    // Toggle it on
    const toggle = screen.getByText(/Before & after actions/).closest('label')!.querySelector('input')!;
    await user.click(toggle);
    expect(screen.getByTestId('add-side-effect')).toBeInTheDocument();
  });

  it('adding a row shows the side-effect form fields', async () => {
    const user = userEvent.setup({ delay: null });
    renderComposer();
    // Toggle on
    const toggle = screen.getByText(/Before & after actions/).closest('label')!.querySelector('input')!;
    await user.click(toggle);
    // Click "Add action"
    await user.click(screen.getByTestId('add-side-effect'));
    // The row should appear with Position, Method, Path fields
    expect(screen.getByTestId('side-effect-row')).toBeInTheDocument();
    expect(screen.getByLabelText('Position')).toBeInTheDocument();
    // Before-only fields should show since default position is "before"
    expect(screen.getByLabelText('Blocking')).toBeInTheDocument();
    expect(screen.getByLabelText('Failure policy')).toBeInTheDocument();
  });

  it('loading an expectation with beforeActions populates the side-effects panel', async () => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [{
        key: 'se-test-001',
        value: {
          id: 'se-test-001',
          httpRequest: { method: 'GET', path: '/api/with-actions' },
          httpResponse: { statusCode: 200 },
          beforeActions: [
            {
              httpRequest: { method: 'GET', path: '/auth', host: 'auth.svc:8080' },
              blocking: true,
              timeout: { timeUnit: 'SECONDS', value: 2 },
              failurePolicy: 'FAIL_FAST',
            },
          ],
          afterActions: [
            { httpRequest: { method: 'POST', path: '/audit' } },
          ],
        },
      }],
    });
    renderComposer();
    const list = screen.getByTestId('existing-mocks-list');
    await user.click(within(list).getByText(/GET \/api\/with-actions/));
    // The side-effects toggle should be enabled
    const sideEffectRows = screen.getAllByTestId('side-effect-row');
    expect(sideEffectRows).toHaveLength(2);
  });
});

describe('Store view migration', () => {
  it('maps legacy "mcp-tools" view to "composer"', () => {
    // setView accepts string at runtime even though the type has changed
    useDashboardStore.getState().setView('mcp-tools' as never);
    expect(useDashboardStore.getState().view).toBe('composer');
  });

  it('does not change valid views', () => {
    useDashboardStore.getState().setView('dashboard');
    expect(useDashboardStore.getState().view).toBe('dashboard');
    useDashboardStore.getState().setView('composer');
    expect(useDashboardStore.getState().view).toBe('composer');
  });
});
