/**
 * Editing an expectation never silently drops a delay distribution or chunked-response
 * options the form cannot show, the Java preview matches what is saved, and New / clear
 * leaves no state from the expectation loaded before it.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
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
const logNormalDelay = { timeUnit: 'MILLISECONDS', value: 0, distribution: { type: 'LOG_NORMAL', median: 50, p99: 200 } };
const templatedDelay = { timeUnit: 'MILLISECONDS', value: 10, templateType: 'MUSTACHE', template: '{{ request.headers.x-delay.0 }}' };
const chunked = { chunkSize: 5, chunkDelay: { timeUnit: 'MILLISECONDS', value: 100 } };

function load(expectation: Record<string, unknown>) {
  // Listed too, so the existing-mocks list (with its New / clear button) is shown.
  useDashboardStore.setState({
    activeExpectations: [{ key: String(expectation['id']), value: expectation }] as never,
    pendingEditExpectation: expectation as never,
    view: 'composer',
  });
  render(
    <ThemeProvider theme={buildTheme('dark')}>
      <ComposerView connectionParams={params} />
    </ThemeProvider>,
  );
}

async function save(fetchMock: ReturnType<typeof vi.spyOn>): Promise<Record<string, unknown>> {
  const user = userEvent.setup({ delay: null });
  await user.click(screen.getByRole('button', { name: /Update expectation/ }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  return JSON.parse(String((fetchMock.mock.calls[0]![1] as RequestInit).body)) as Record<string, unknown>;
}

describe('composer edits keep what the form cannot show', () => {
  let fetchMock: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    useDashboardStore.setState({ activeExpectations: [], pendingEditExpectation: null, view: 'composer' });
    try { globalThis.sessionStorage?.clear(); } catch { /* noop */ }
    fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 201 }));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('a delay distribution locks the fixed delay field, so it cannot be overwritten', async () => {
    load({ id: 'dist', httpRequest: { path: '/d' }, httpResponse: { statusCode: 200, body: 'b', delay: logNormalDelay } });
    const delayField = await screen.findByLabelText('Response delay');
    expect(delayField).toBeDisabled();
    expect(screen.getByText(/delay uses a distribution, or a template/)).toBeInTheDocument();
    const sent = await save(fetchMock);
    expect((sent['httpResponse'] as Record<string, unknown>)['delay']).toEqual(logNormalDelay);
  });

  it.each([
    ['an error delay with a distribution', 'Pre-action delay', { httpError: { dropConnection: true, delay: logNormalDelay } }, 'httpError', logNormalDelay],
    ['a response delay with a template', 'Response delay', { httpResponse: { statusCode: 200, body: 'b', delay: templatedDelay } }, 'httpResponse', templatedDelay],
  ])('%s locks its fixed delay field and is saved unchanged', async (_label, field, action, actionKey, expected) => {
    load({ id: 'locked', httpRequest: { path: '/l' }, ...action });
    expect(await screen.findByLabelText(field)).toBeDisabled();
    const sent = await save(fetchMock);
    expect((sent[actionKey] as Record<string, unknown>)['delay']).toEqual(expected);
  });

  it('removing the kept delay unlocks the fixed delay field', async () => {
    const user = userEvent.setup({ delay: null });
    load({ id: 'dist2', httpRequest: { path: '/d' }, httpResponse: { statusCode: 200, body: 'b', delay: logNormalDelay } });
    const panel = await screen.findByTestId('kept-action-fields');
    await user.click(within(panel).getByRole('button', { name: 'Remove delay' }));
    const delayField = screen.getByLabelText('Response delay');
    expect(delayField).toBeEnabled();
    await user.clear(delayField);
    await user.type(delayField, '30');
    const sent = await save(fetchMock);
    expect((sent['httpResponse'] as Record<string, unknown>)['delay']).toEqual({ timeUnit: 'MILLISECONDS', value: 30 });
  });

  it('setting a connection option keeps the chunk size and chunk delay', async () => {
    const user = userEvent.setup({ delay: null });
    load({ id: 'chunks', httpRequest: { path: '/c' }, httpResponse: { statusCode: 200, body: 'b', connectionOptions: chunked } });
    const field = await screen.findByLabelText('Content-Length override');
    await user.type(field, '20');
    const sent = await save(fetchMock);
    expect((sent['httpResponse'] as Record<string, unknown>)['connectionOptions']).toEqual({ ...chunked, contentLengthHeaderOverride: 20 });
  });

  it('the Java preview adds no Content-Type header that the save does not send', async () => {
    const user = userEvent.setup({ delay: null });
    load({ id: 'nohdr', httpRequest: { path: '/n' }, httpResponse: { statusCode: 200, body: 'plain text' } });
    await user.click(await screen.findByRole('tab', { name: 'Java' }));
    expect(await screen.findByText(/\.withBody\("plain text"\)/)).toBeInTheDocument();
    expect(screen.queryByText(/withHeader\("Content-Type"/)).not.toBeInTheDocument();
    const sent = await save(fetchMock);
    expect(sent['httpResponse']).toEqual({ statusCode: 200, body: 'plain text' });
  });
});

describe('New / clear', () => {
  beforeEach(() => {
    useDashboardStore.setState({ activeExpectations: [], pendingEditExpectation: null, view: 'composer' });
    try { globalThis.sessionStorage?.clear(); } catch { /* noop */ }
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('resets the error action, so a loaded reset-connection does not carry over', async () => {
    const user = userEvent.setup({ delay: null });
    load({ id: 'reset', httpRequest: { path: '/r' }, httpError: { resetConnection: true } });
    await waitFor(() => expect((screen.getByLabelText('Path') as HTMLInputElement).value).toBe('/r'));
    await user.click(screen.getByRole('button', { name: /New \/ clear/ }));
    expect(screen.getByRole('radio', { name: /^Static HTTP response/ })).toBeChecked();
    await user.click(screen.getByRole('radio', { name: /^Error \/ fault injection/ }));
    expect(screen.getByRole('switch', { name: /Reset connection/ })).not.toBeChecked();
  });

  it('in the DNS kind, New / clear and Add another start a blank DNS response', async () => {
    const user = userEvent.setup({ delay: null });
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 201 }));
    load({ id: 'dns', httpRequest: { dnsName: 'example.com', dnsType: 'A' }, dnsResponse: { responseCode: 'NXDOMAIN', answerRecords: [{ name: 'example.com', type: 'A', value: '1.2.3.4' }] } });
    await waitFor(() => expect((screen.getByLabelText('DNS name') as HTMLInputElement).value).toBe('example.com'));
    await user.click(screen.getByRole('button', { name: /New \/ clear/ }));
    expect(screen.getByRole('radio', { name: /^DNS response/ })).toBeChecked();
    expect(screen.getByLabelText('DNS name')).toHaveValue('');
    expect(screen.getByLabelText(/Answer records/)).toHaveValue('');
    await user.type(screen.getByLabelText('DNS name'), 'fresh.example');
    await user.click(screen.getByRole('button', { name: /Register expectation/ }));
    await user.click(await screen.findByRole('button', { name: /Add another/ }));
    expect(screen.getByRole('radio', { name: /^DNS response/ })).toBeChecked();
    expect(screen.getByLabelText('DNS name')).toHaveValue('');
  });

  it('in the gRPC kind, New / clear starts a blank gRPC stream on the default method', async () => {
    const user = userEvent.setup({ delay: null });
    load({ id: 'grpc', httpRequest: { method: 'POST', path: '/pkg.Svc/Method' }, grpcStreamResponse: { statusName: 'NOT_FOUND', messages: [{ json: '{"a":1}' }] } });
    await waitFor(() => expect((screen.getByLabelText('Path') as HTMLInputElement).value).toBe('/pkg.Svc/Method'));
    await user.click(screen.getByRole('button', { name: /New \/ clear/ }));
    expect(screen.getByRole('radio', { name: /^gRPC stream response/ })).toBeChecked();
    expect(screen.getByLabelText('Path')).toHaveValue('/package.Service/Method');
    expect(screen.getByLabelText('Status name')).toHaveValue('');
  });

  it('resets the response template, including its parameters', async () => {
    const user = userEvent.setup({ delay: null });
    load({ id: 'tpl', httpRequest: { path: '/t' }, httpResponseTemplate: { templateType: 'MUSTACHE', template: '{"statusCode":200}', parameters: { greeting: 'hello' } } });
    await waitFor(() => expect((screen.getByLabelText('Path') as HTMLInputElement).value).toBe('/t'));
    await user.click(screen.getByRole('button', { name: /New \/ clear/ }));
    await user.click(screen.getByRole('radio', { name: /^Response template/ }));
    expect((screen.getByLabelText('Template body') as HTMLTextAreaElement).value).toBe('');
    expect((screen.getByLabelText(/^Parameters/) as HTMLTextAreaElement).value).toBe('');
  });
});
