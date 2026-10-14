import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TemplateTestDialog from '../components/TemplateTestDialog';
import { testTemplate, TemplateTestRefused, type TemplateTestReport } from '../lib/templateTest';
import { useDashboardStore } from '../store';

vi.mock('../lib/templateTest', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../lib/templateTest')>();
  return { ...actual, testTemplate: vi.fn() };
});

const connectionParams = { host: 'localhost', port: '1080', secure: false };
const mockTest = vi.mocked(testTemplate);

const TEMPLATE = '{\n  "statusCode": 200,\n  #if($request.path)\n  "body": "$request.nope"\n}';

function renderDialog(props: Partial<Parameters<typeof TemplateTestDialog>[0]> = {}) {
  return render(
    <TemplateTestDialog
      open
      onClose={() => {}}
      connectionParams={connectionParams}
      templateType="VELOCITY"
      template={TEMPLATE}
      actionType="RESPONSE_TEMPLATE"
      {...props}
    />,
  );
}

function report(overrides: Partial<TemplateTestReport>): TemplateTestReport {
  return { valid: true, timedOut: false, warnings: [], ...overrides };
}

beforeEach(() => {
  vi.clearAllMocks();
  useDashboardStore.setState({ recordedRequests: [], fullMessages: {} });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('TemplateTestDialog', () => {
  it('sends the template with the default sample request and shows the rendered response', async () => {
    const user = userEvent.setup();
    mockTest.mockResolvedValue(report({ durationMillis: 4, renderedResponse: { statusCode: 200, body: 'hello' } }));
    renderDialog();

    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    await waitFor(() => expect(screen.getByText('Valid Response')).toBeInTheDocument());
    expect(mockTest).toHaveBeenCalledWith(
      connectionParams,
      { templateType: 'VELOCITY', template: TEMPLATE, actionType: 'RESPONSE_TEMPLATE', request: { method: 'GET', path: '/' } },
      expect.any(AbortSignal),
    );
    expect(screen.getByRole('heading', { name: 'Rendered Response' })).toBeInTheDocument();
    expect(screen.getByText('4 ms')).toBeInTheDocument();
  });

  it('sends a chosen recorded request', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      recordedRequests: [
        { key: 'r1_request', timestamp: '2026-10-13 10:00:00.000', value: { httpRequest: { method: 'POST', path: '/orders', body: 'abc' } } },
      ],
    });
    mockTest.mockResolvedValue(report({ renderedResponse: { statusCode: 201 } }));
    renderDialog();

    await user.click(screen.getByRole('combobox', { name: 'Sample request' }));
    await user.click(await screen.findByRole('option', { name: /POST \/orders/ }));
    await waitFor(() => expect((screen.getByLabelText('Sample request JSON') as HTMLTextAreaElement).value).toContain('/orders'));
    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    await waitFor(() => expect(mockTest).toHaveBeenCalled());
    expect(mockTest.mock.calls[0]![1].request).toEqual({ method: 'POST', path: '/orders', body: 'abc' });
  });

  it('offers only HTTP requests, not recorded DNS queries or binary messages', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      recordedRequests: [
        { key: 'dns_request', timestamp: '2026-10-13 10:00:02.000', value: { httpRequest: { dnsName: 'api.example.com', dnsType: 'A' } } },
        { key: 'binary_request', timestamp: '2026-10-13 10:00:01.000', value: { httpRequest: { binaryData: 'AAEC' } } },
        { key: 'r1_request', timestamp: '2026-10-13 10:00:00.000', value: { httpRequest: { method: 'POST', path: '/orders' } } },
      ],
    });
    renderDialog();

    await user.click(screen.getByRole('combobox', { name: 'Sample request' }));

    const options = await screen.findAllByRole('option');
    expect(options).toHaveLength(2);
    expect(options[1]).toHaveTextContent('POST /orders');
    expect(screen.queryByRole('option', { name: /10:00:02/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /10:00:01/ })).not.toBeInTheDocument();
  });

  it('loads the whole body of a recorded request the server shortened before using it', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ httpRequest: { method: 'PUT', path: '/big', body: 'the whole body' } })));
    vi.stubGlobal('fetch', fetchMock);
    useDashboardStore.setState({
      recordedRequests: [{
        key: 'r2_request',
        value: { httpRequest: { method: 'PUT', path: '/big', body: 'the who' } },
        truncatedBodies: { httpRequest: { logEntryId: 'log-2', part: 'request', originalLength: 14, shownLength: 7 } },
      }],
    });
    mockTest.mockResolvedValue(report({}));
    renderDialog();

    await user.click(screen.getByRole('combobox', { name: 'Sample request' }));
    await user.click(await screen.findByRole('option', { name: /PUT \/big/ }));
    await waitFor(() => expect((screen.getByLabelText('Sample request JSON') as HTMLTextAreaElement).value).toContain('the whole body'));
    expect(String(fetchMock.mock.calls[0]![0])).toContain('/mockserver/logEntryBody?id=log-2&part=request');
    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    await waitFor(() => expect(mockTest).toHaveBeenCalled());
    expect(mockTest.mock.calls[0]![1].request).toEqual({ method: 'PUT', path: '/big', body: 'the whole body' });
  });

  it('keeps the previous sample request when the whole body of the chosen row cannot be loaded', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: 'log entry evicted' }), { status: 404 })));
    useDashboardStore.setState({
      recordedRequests: [{
        key: 'r3_request',
        value: { httpRequest: { method: 'PUT', path: '/gone', body: 'the who' } },
        truncatedBodies: { httpRequest: { logEntryId: 'log-3', part: 'request', originalLength: 14, shownLength: 7 } },
      }],
      notification: null,
    });
    renderDialog();

    await user.click(screen.getByRole('combobox', { name: 'Sample request' }));
    await user.click(await screen.findByRole('option', { name: /PUT \/gone/ }));

    await waitFor(() => expect(useDashboardStore.getState().notification?.message).toContain('log entry evicted'));
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Sample request' })).not.toHaveAttribute('aria-disabled'));
    expect(screen.getByRole('combobox', { name: 'Sample request' }).textContent).toContain('Default sample (GET /)');
    expect(JSON.parse((screen.getByLabelText('Sample request JSON') as HTMLTextAreaElement).value)).toEqual({ method: 'GET', path: '/' });
  });

  it('shows the error with its template position, the failing line with a caret, the hint and warnings', async () => {
    const user = userEvent.setup();
    mockTest.mockResolvedValue(report({
      valid: false,
      error: { message: 'Encountered "<EOF>" at line 5, column 1', line: 3, column: 3, hint: 'a #if needs a matching #end' },
      warnings: [{ message: '$request.nope did not resolve', line: 4, column: 12 }],
    }));
    renderDialog();

    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    await waitFor(() => expect(screen.getByText('Failed')).toBeInTheDocument());
    expect(screen.getByText('At template line 3, column 3')).toBeInTheDocument();
    expect(screen.getByText(/Encountered "<EOF>"/)).toBeInTheDocument();
    expect(screen.getByText('Hint: a #if needs a matching #end')).toBeInTheDocument();
    const excerpt = screen.getByTestId('template-error-excerpt');
    expect(excerpt.textContent).toContain('> 3 |   #if($request.path)');
    expect(within(excerpt).getByRole('note', { name: 'error at column 3' }).textContent).toBe('    |   ^');
    expect(excerpt.textContent).toContain('  1 | {');
    expect(screen.getByTestId('template-test-warning').textContent).toBe('line 4, column 12: $request.nope did not resolve');
  });

  it('lines the caret up under a column after a tab, counting a Velocity column in tab stops of 8', async () => {
    const user = userEvent.setup();
    // "\t#if(" : Velocity puts the "#" at column 9, the first column after the tab stop.
    mockTest.mockResolvedValue(report({ valid: false, error: { message: 'Encountered', line: 2, column: 9 } }));
    renderDialog({ template: '{\n\t#if(\n}' });

    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    const excerpt = await screen.findByTestId('template-error-excerpt');
    expect(excerpt.textContent).toContain('> 2 |         #if(');
    expect(within(excerpt).getByRole('note', { name: 'error at column 9' }).textContent).toBe('    |         ^');
  });

  it('lines the caret up under a JavaScript column, which counts a tab as one character', async () => {
    const user = userEvent.setup();
    mockTest.mockResolvedValue(report({ valid: false, error: { message: 'missingThing is not defined', line: 1, column: 2 } }));
    renderDialog({ templateType: 'JAVASCRIPT', template: '\tmissingThing;' });

    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    const excerpt = await screen.findByTestId('template-error-excerpt');
    expect(excerpt.textContent).toContain('> 1 |         missingThing;');
    expect(within(excerpt).getByRole('note', { name: 'error at column 2' }).textContent).toBe('    |         ^');
  });

  it('shows validation errors and the raw output when the template renders something that is not a response', async () => {
    const user = userEvent.setup();
    mockTest.mockResolvedValue(report({
      valid: false,
      renderedOutput: 'not json at all',
      renderedOutputTruncated: true,
      error: { message: 'the template output is not a valid response', validationErrors: 'incorrect response json format' },
    }));
    renderDialog();

    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    await waitFor(() => expect(screen.getByTestId('template-rendered-output').textContent).toBe('not json at all'));
    expect(screen.getByText(/Not a valid response: incorrect response json format/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Rendered Output (shortened by the server)' })).toBeInTheDocument();
  });

  it('shows a timed-out run as timed out', async () => {
    const user = userEvent.setup();
    mockTest.mockResolvedValue(report({ valid: false, timedOut: true, error: { message: 'the template did not finish within 10000ms' } }));
    renderDialog();

    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    await waitFor(() => expect(screen.getByText('Timed Out')).toBeInTheDocument());
    expect(screen.getByText(/did not finish within 10000ms/)).toBeInTheDocument();
  });

  it('shows the server reason when too many dry runs are running (429)', async () => {
    const user = userEvent.setup();
    mockTest.mockRejectedValue(new TemplateTestRefused('2 template dry runs are already running; retry when one finishes', 429));
    renderDialog();

    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    const alert = await screen.findByTestId('template-test-failure');
    expect(alert.textContent).toContain('2 template dry runs are already running');
    expect(alert.className).toContain('Warning');
    expect(screen.getByRole('button', { name: 'Run Test' })).toBeEnabled();
  });

  it('aborts a run that is still going when the dialog is closed', async () => {
    const user = userEvent.setup();
    let signal: AbortSignal | undefined;
    mockTest.mockImplementation((_params, _input, s) => {
      signal = s;
      return new Promise(() => {});
    });
    const onClose = vi.fn();
    renderDialog({ onClose });

    await user.click(screen.getByRole('button', { name: 'Run Test' }));
    expect(screen.getByText('Rendering the template…')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Close' }));

    expect(signal?.aborted).toBe(true);
    expect(onClose).toHaveBeenCalled();
  });

  it('does not send a sample request that is not a JSON object', async () => {
    const user = userEvent.setup();
    renderDialog();

    const field = screen.getByLabelText('Sample request JSON');
    await user.clear(field);
    await user.type(field, '[[1]');
    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    expect(screen.getByText('The sample request must be a JSON object')).toBeInTheDocument();
    expect(mockTest).not.toHaveBeenCalled();
  });

  it('labels a forward template result as a request', async () => {
    const user = userEvent.setup();
    mockTest.mockResolvedValue(report({ renderedRequest: { method: 'GET', path: '/upstream' } }));
    renderDialog({ actionType: 'FORWARD_TEMPLATE', templateType: 'JAVASCRIPT', template: 'return { path: "/upstream" };' });

    await user.click(screen.getByRole('button', { name: 'Run Test' }));

    await waitFor(() => expect(screen.getByText('Valid Request')).toBeInTheDocument());
    expect(screen.getByRole('heading', { name: 'Rendered Request' })).toBeInTheDocument();
    expect(mockTest.mock.calls[0]![1].actionType).toBe('FORWARD_TEMPLATE');
  });
});
