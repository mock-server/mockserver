/**
 * Composer "Test Template": the response and forward template steps open the template dry run with
 * the template in the editor, and post it to PUT /mockserver/template/test.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
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
let fetchMock: ReturnType<typeof vi.fn>;

function renderComposer() {
  try { globalThis.sessionStorage?.setItem('mockserver-composer-mode', 'advanced'); } catch { /* noop */ }
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <ComposerView connectionParams={params} />
    </ThemeProvider>,
  );
}

function templateTestCalls() {
  return fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/mockserver/template/test'));
}

beforeEach(() => {
  useDashboardStore.setState({ activeExpectations: [], recordedRequests: [] });
  fetchMock = vi.fn(async (url: string) => {
    if (String(url).endsWith('/mockserver/template/test')) {
      return new Response(JSON.stringify({ valid: true, renderedResponse: { statusCode: 200, body: '/' } }), { status: 200 });
    }
    return new Response(JSON.stringify({ ports: [1080] }), { status: 200 });
  });
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('Composer — Test Template', () => {
  it('posts the response template in the editor to the template dry run', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText(/Response template/));

    expect(screen.getByRole('button', { name: 'Test Template' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Template body'), { target: { value: '{ "statusCode": 200, "body": "$!request.path" }' } });
    await user.click(screen.getByRole('button', { name: 'Test Template' }));
    expect(screen.getByRole('dialog', { name: /Test Template/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    await waitFor(() => expect(screen.getByText('Valid Response')).toBeInTheDocument());
    const calls = templateTestCalls();
    expect(calls).toHaveLength(1);
    const init = calls[0]![1] as RequestInit;
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body as string)).toEqual({
      templateType: 'VELOCITY',
      template: '{ "statusCode": 200, "body": "$!request.path" }',
      actionType: 'RESPONSE_TEMPLATE',
      request: { method: 'GET', path: '/' },
    });
  });

  it('posts a forward template as a FORWARD_TEMPLATE', async () => {
    const user = userEvent.setup();
    renderComposer();
    await user.click(screen.getByLabelText(/Forward template/));

    fireEvent.change(screen.getByLabelText('Template body'), { target: { value: 'return { path: "/upstream" };' } });
    await user.click(screen.getByRole('button', { name: 'Test Template' }));
    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    await waitFor(() => expect(templateTestCalls()).toHaveLength(1));
    const sent = JSON.parse((templateTestCalls()[0]![1] as RequestInit).body as string) as Record<string, unknown>;
    expect(sent.actionType).toBe('FORWARD_TEMPLATE');
    expect(sent.template).toBe('return { path: "/upstream" };');
  });
});
