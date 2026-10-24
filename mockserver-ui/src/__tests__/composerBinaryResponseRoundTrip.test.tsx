/**
 * Edit round-trip for a binaryResponse that carries `delay` and `primary`.
 *
 * The composer's binary response form models only `binaryData`, and the action
 * slot is replaced wholesale by the form's output on save. Without carrying the
 * other two fields through, opening such an expectation and saving it untouched
 * silently dropped them.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
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

async function saveUntouched(expectation: Record<string, unknown>): Promise<Record<string, unknown>> {
  const user = userEvent.setup({ delay: null });
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 201 }));
  useDashboardStore.setState({
    activeExpectations: [],
    pendingEditExpectation: expectation as never,
    view: 'composer',
  });
  render(
    <ThemeProvider theme={buildTheme('dark')}>
      <ComposerView connectionParams={params} />
    </ThemeProvider>,
  );
  await waitFor(() => expect(screen.getByText('Expectation kind')).toBeInTheDocument());
  await user.click(screen.getByRole('button', { name: /Update expectation/ }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  const init = fetchMock.mock.calls[0]![1];
  expect(init?.method).toBe('PUT');
  return JSON.parse(String(init?.body)) as Record<string, unknown>;
}

describe('editing a binaryResponse expectation', () => {
  beforeEach(() => {
    useDashboardStore.setState({ activeExpectations: [], pendingEditExpectation: null, view: 'composer' });
    try { globalThis.sessionStorage?.clear(); } catch { /* noop */ }
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('saves delay and primary back unchanged', async () => {
    const original = {
      id: 'bin-delay-primary',
      httpRequest: { path: '/bin', body: { type: 'BINARY', base64Bytes: 'AAEC' } },
      binaryResponse: {
        binaryData: 'SGVsbG8=',
        delay: { timeUnit: 'MILLISECONDS', value: 250 },
        primary: true,
      },
    };
    const sent = await saveUntouched(original);
    expect(sent['binaryResponse']).toEqual(original.binaryResponse);
    expect(sent).toEqual(original);
  });

  it('saves upstream, delay and primary back unchanged', async () => {
    const original = {
      id: 'bin-upstream',
      httpRequest: { path: '/bin', body: { type: 'BINARY', base64Bytes: 'AAEC' } },
      binaryResponse: {
        binaryData: 'SGVsbG8=',
        upstream: 'FORWARD_AND_REPLACE',
        delay: { timeUnit: 'MILLISECONDS', value: 250 },
        primary: true,
      },
    };
    expect(await saveUntouched(original)).toEqual(original);
  });

  it('resetting upstream to the default keeps delay and primary', async () => {
    const user = userEvent.setup({ delay: null });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 201 }));
    useDashboardStore.setState({
      activeExpectations: [],
      pendingEditExpectation: {
        id: 'bin-reset-upstream',
        httpRequest: { path: '/bin', body: { type: 'BINARY', base64Bytes: 'AAEC' } },
        binaryResponse: { binaryData: 'SGVsbG8=', upstream: 'ANSWER_AND_FORWARD', delay: { timeUnit: 'SECONDS', value: 2 }, primary: true },
      } as never,
      view: 'composer',
    });
    render(
      <ThemeProvider theme={buildTheme('dark')}>
        <ComposerView connectionParams={params} />
      </ThemeProvider>,
    );
    await user.click(await screen.findByRole('combobox', { name: 'Upstream' }));
    await user.click(await screen.findByRole('option', { name: /Answer only/ }));
    await user.click(screen.getByRole('button', { name: /Update expectation/ }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const sent = JSON.parse(String(fetchMock.mock.calls[0]![1]?.body)) as Record<string, unknown>;
    expect(sent['binaryResponse']).toEqual({ binaryData: 'SGVsbG8=', delay: { timeUnit: 'SECONDS', value: 2 }, primary: true });
  });

  it('shows the carried fields so the user knows they are kept', async () => {
    useDashboardStore.setState({
      activeExpectations: [],
      pendingEditExpectation: {
        id: 'bin-shown',
        httpRequest: { path: '/bin', body: { type: 'BINARY', base64Bytes: 'AAEC' } },
        binaryResponse: { binaryData: 'SGVsbG8=', delay: { timeUnit: 'SECONDS', value: 2 }, primary: true },
      } as never,
      view: 'composer',
    });
    render(
      <ThemeProvider theme={buildTheme('dark')}>
        <ComposerView connectionParams={params} />
      </ThemeProvider>,
    );
    const note = await screen.findByTestId('kept-action-fields');
    expect(note).toHaveTextContent('delay 2 SECONDS');
    expect(note).toHaveTextContent('primary');
  });

  it('New / clear does not carry the fields into the next expectation', async () => {
    const user = userEvent.setup({ delay: null });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 201 }));
    const old = {
      id: 'bin-old',
      httpRequest: { path: '/bin', body: { type: 'BINARY', base64Bytes: 'AAEC' } },
      binaryResponse: { binaryData: 'SGVsbG8=', delay: { timeUnit: 'SECONDS', value: 2 }, primary: true },
    };
    // Listed, so the composer shows the "New / clear" control for it.
    useDashboardStore.setState({
      activeExpectations: [{ key: 'bin-old', value: old }] as never,
      pendingEditExpectation: old as never,
      view: 'composer',
    });
    render(
      <ThemeProvider theme={buildTheme('dark')}>
        <ComposerView connectionParams={params} />
      </ThemeProvider>,
    );
    await screen.findByTestId('kept-action-fields');
    await user.click(screen.getByRole('button', { name: /New \/ clear/ }));
    expect(screen.queryByTestId('kept-action-fields')).not.toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /^Static HTTP response/ })).toBeChecked();
    await user.type(screen.getByLabelText('Path'), '/fresh');
    await user.click(screen.getByRole('radio', { name: /^Binary response/ }));
    expect(screen.getByLabelText(/Binary data/)).toHaveValue('');
    await user.type(screen.getByLabelText(/Binary data/), 'QQ==');
    await user.click(screen.getByRole('button', { name: /Register expectation/ }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const sent = JSON.parse(String(fetchMock.mock.calls[0]![1]?.body)) as Record<string, unknown>;
    // Neither the loaded data nor its delay and primary reach the next binary response.
    expect(sent['binaryResponse']).toEqual({ binaryData: 'QQ==' });
    expect(sent['httpResponse']).toBeUndefined();
  });

  it.each([['listed', true], ['not listed', false]])('loading a different %s expectation does not carry the fields into it', async (_label, listed) => {
    const user = userEvent.setup({ delay: null });
    useDashboardStore.setState({
      activeExpectations: [],
      pendingEditExpectation: {
        id: 'bin-first',
        httpRequest: { path: '/bin', body: { type: 'BINARY', base64Bytes: 'AAEC' } },
        binaryResponse: { binaryData: 'SGVsbG8=', delay: { timeUnit: 'SECONDS', value: 2 }, primary: true },
      } as never,
      view: 'composer',
    });
    render(
      <ThemeProvider theme={buildTheme('dark')}>
        <ComposerView connectionParams={params} />
      </ThemeProvider>,
    );
    await screen.findByTestId('kept-action-fields');
    const second = {
      id: 'static-second',
      httpRequest: { method: 'GET', path: '/static' },
      httpResponse: { statusCode: 200 },
    };
    act(() => {
      useDashboardStore.setState({
        activeExpectations: (listed ? [{ key: 'static-second', value: second }] : []) as never,
        pendingEditExpectation: second as never,
      });
    });
    await waitFor(() => expect((screen.getByLabelText('Path') as HTMLInputElement).value).toBe('/static'));
    await user.click(screen.getByRole('radio', { name: /Binary response/ }));
    expect(screen.getByLabelText('Binary data (base64)')).toBeInTheDocument();
    expect(screen.queryByTestId('kept-action-fields')).not.toBeInTheDocument();
  });

  it('an expectation without delay or primary does not gain them', async () => {
    const original = {
      id: 'bin-plain',
      httpRequest: { path: '/bin', body: { type: 'BINARY', base64Bytes: 'AAEC' } },
      binaryResponse: { binaryData: 'SGVsbG8=' },
    };
    const sent = await saveUntouched(original);
    expect(sent['binaryResponse']).toEqual(original.binaryResponse);
    expect(screen.queryByTestId('kept-action-fields')).not.toBeInTheDocument();
  });
});
