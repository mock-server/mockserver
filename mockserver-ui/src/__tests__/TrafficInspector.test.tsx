import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import TrafficInspector, {
  maskSecretValue,
  maskSecretsInValue,
  mcpErrorInfo,
  isUnmatchedResponse,
  buildRequestCurl,
} from '../components/TrafficInspector';
import { summarizeTraffic, type McpParsed } from '../lib/llmTraffic';
import { DebugMismatchContext } from '../hooks/DebugMismatchContext';
import { GenerateStubContext } from '../hooks/GenerateStubContext';
import { useDashboardStore } from '../store';
import * as trafficLib from '../lib/traffic';

function renderTrafficInspector() {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <TrafficInspector />
    </ThemeProvider>,
  );
}

function renderWithMismatchContexts(
  debugMismatch: (r: Record<string, unknown>) => Promise<void>,
  generateStub: (r: Record<string, unknown>) => Promise<void>,
) {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <DebugMismatchContext.Provider value={debugMismatch}>
        <GenerateStubContext.Provider value={generateStub}>
          <TrafficInspector />
        </GenerateStubContext.Provider>
      </DebugMismatchContext.Provider>
    </ThemeProvider>,
  );
}

describe('TrafficInspector — ScriptedTurnsPanel wiring', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  it('shows Scripted Turns tab when LLM request is selected and conversation expectations exist', async () => {
    const user = userEvent.setup();

    // Set up a proxied LLM request
    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-1',
          value: {
            httpRequest: {
              method: 'POST',
              path: '/v1/messages',
              headers: [{ name: 'host', values: ['api.anthropic.com'] }],
              body: {
                type: 'JSON',
                json: JSON.stringify({
                  model: 'claude-sonnet-4-20250514',
                  messages: [{ role: 'user', content: 'Hello' }],
                }),
              },
            },
            httpResponse: {
              statusCode: 200,
              body: {
                type: 'JSON',
                json: JSON.stringify({
                  content: [{ type: 'text', text: 'Hi there!' }],
                  usage: { input_tokens: 5, output_tokens: 3 },
                }),
              },
            },
          },
        },
      ],
      // Two-turn conversation expectations sharing a scenarioName
      activeExpectations: [
        {
          key: 'exp-turn0',
          value: {
            // scenarioName/scenarioState/newScenarioState are top-level Expectation fields
            scenarioName: 'weather_conversation',
            scenarioState: 'Started',
            newScenarioState: 'turn_1',
            httpLlmResponse: {
              provider: 'ANTHROPIC',
              model: 'claude-sonnet-4-20250514',
              conversationPredicates: {
                turnIndex: 0,
              },
              completion: {
                text: '',
                toolCalls: [{ name: 'get_weather', arguments: '{"city":"London"}' }],
                stopReason: 'tool_use',
              },
            },
          },
        },
        {
          key: 'exp-turn1',
          value: {
            scenarioName: 'weather_conversation',
            scenarioState: 'turn_1',
            newScenarioState: '__done',
            httpLlmResponse: {
              provider: 'ANTHROPIC',
              model: 'claude-sonnet-4-20250514',
              conversationPredicates: {
                turnIndex: 1,
                containsToolResultFor: 'get_weather',
              },
              completion: {
                text: 'The weather in London is sunny, 22C.',
                stopReason: 'end_turn',
              },
            },
          },
        },
      ],
    });

    renderTrafficInspector();

    // Click on the proxied request row to select it
    // The path shows as "api.anthropic.com/v1/messages" in a single text node
    const row = screen.getByText(/\/v1\/messages/);
    await user.click(row);

    // The Scripted Turns tab should be visible
    expect(screen.getByRole('tab', { name: 'Scripted Turns' })).toBeInTheDocument();

    // Click on the Scripted Turns tab
    await user.click(screen.getByRole('tab', { name: 'Scripted Turns' }));

    // The scripted turns content should be visible
    expect(screen.getByText('Scripted Conversation Turns')).toBeInTheDocument();
    expect(screen.getByText('Turn 0')).toBeInTheDocument();
    expect(screen.getByText('Turn 1')).toBeInTheDocument();
    expect(screen.getByText('Started')).toBeInTheDocument();
    expect(screen.getByText('__done')).toBeInTheDocument();
  });

  it('does not show Scripted Turns tab when no conversation expectations exist', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-1',
          value: {
            httpRequest: {
              method: 'POST',
              path: '/v1/messages',
              body: {
                type: 'JSON',
                json: JSON.stringify({
                  model: 'claude-sonnet-4-20250514',
                  messages: [{ role: 'user', content: 'Hello' }],
                }),
              },
            },
            httpResponse: {
              statusCode: 200,
              body: {
                type: 'JSON',
                json: JSON.stringify({
                  content: [{ type: 'text', text: 'Hi!' }],
                  usage: { input_tokens: 3, output_tokens: 1 },
                }),
              },
            },
          },
        },
      ],
      activeExpectations: [],
    });

    renderTrafficInspector();

    const row = screen.getByText(/\/v1\/messages/);
    await user.click(row);

    // No Scripted Turns tab when no conversation expectations
    expect(screen.queryByRole('tab', { name: 'Scripted Turns' })).not.toBeInTheDocument();
  });
});

describe('TrafficInspector — per-request timing display', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  it('shows timing chip in master list when timing data is present on a proxied request', () => {
    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-timed',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/data',
              headers: [{ name: 'host', values: ['example.com'] }],
            },
            httpResponse: {
              statusCode: 200,
              timing: {
                connectionTimeInMillis: 12,
                timeToFirstByteInMillis: 85,
                totalTimeInMillis: 142,
                requestStartedMillis: 1700000000000,
                connectionEstablishedMillis: 1700000000012,
                responseReceivedMillis: 1700000000142,
              },
            },
          },
        },
      ],
    });

    renderTrafficInspector();

    // The compact timing label should be visible in the master list row
    expect(screen.getByText('142ms')).toBeInTheDocument();
  });

  it('does not show timing chip when timing data is absent (mocked response)', () => {
    useDashboardStore.setState({
      recordedRequests: [
        {
          key: 'req-mocked',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/mocked',
            },
            httpResponse: {
              statusCode: 200,
            },
          },
        },
      ],
    });

    renderTrafficInspector();

    // No timing chip should be present
    expect(screen.queryByText(/\d+ms/)).not.toBeInTheDocument();
  });

  it('shows timing waterfall in detail pane when a timed request is selected', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-timed-detail',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/data',
              headers: [{ name: 'host', values: ['example.com'] }],
            },
            httpResponse: {
              statusCode: 200,
              timing: {
                connectionTimeInMillis: 15,
                timeToFirstByteInMillis: 90,
                totalTimeInMillis: 200,
                requestStartedMillis: 1700000000000,
                connectionEstablishedMillis: 1700000000015,
                responseReceivedMillis: 1700000000200,
              },
            },
          },
        },
      ],
    });

    renderTrafficInspector();

    // Click on the row to select it
    const row = screen.getByText(/\/api\/data/);
    await user.click(row);

    // The timing waterfall should appear in the detail pane
    expect(screen.getByTestId('timing-waterfall')).toBeInTheDocument();
    // Timing breakdown chips should be visible
    expect(screen.getByText('connect 15ms')).toBeInTheDocument();
    expect(screen.getByText('TTFB 90ms')).toBeInTheDocument();
    expect(screen.getByText('total 200ms')).toBeInTheDocument();
    // Waterfall bar should be present
    expect(screen.getByTestId('timing-bar')).toBeInTheDocument();
  });

  it('does not show timing waterfall for requests without timing data', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-no-timing',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/data',
            },
            httpResponse: {
              statusCode: 200,
            },
          },
        },
      ],
    });

    renderTrafficInspector();

    const row = screen.getByText(/\/api\/data/);
    await user.click(row);

    // No timing waterfall should be shown
    expect(screen.queryByTestId('timing-waterfall')).not.toBeInTheDocument();
  });

  it('shows timing alongside LLM usage detail for proxied LLM traffic', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-llm-timed',
          value: {
            httpRequest: {
              method: 'POST',
              path: '/v1/messages',
              headers: [{ name: 'host', values: ['api.anthropic.com'] }],
              body: {
                type: 'JSON',
                json: JSON.stringify({
                  model: 'claude-sonnet-4-20250514',
                  messages: [{ role: 'user', content: 'Hello' }],
                }),
              },
            },
            httpResponse: {
              statusCode: 200,
              body: {
                type: 'JSON',
                json: JSON.stringify({
                  content: [{ type: 'text', text: 'Hi!' }],
                  usage: { input_tokens: 5, output_tokens: 2 },
                }),
              },
              timing: {
                connectionTimeInMillis: 8,
                timeToFirstByteInMillis: 1200,
                totalTimeInMillis: 1500,
                requestStartedMillis: 1700000000000,
                connectionEstablishedMillis: 1700000000008,
                responseReceivedMillis: 1700000001500,
              },
            },
          },
        },
      ],
    });

    renderTrafficInspector();

    // Master list should show both the timing chip and the token summary
    expect(screen.getByText('1500ms')).toBeInTheDocument();
    expect(screen.getByText('5 in / 2 out')).toBeInTheDocument();

    // Click to open detail pane
    const row = screen.getByText(/\/v1\/messages/);
    await user.click(row);

    // Both LLM usage and timing waterfall should be visible
    expect(screen.getByTestId('timing-waterfall')).toBeInTheDocument();
    expect(screen.getByText('connect 8ms')).toBeInTheDocument();
    expect(screen.getByText('TTFB 1200ms')).toBeInTheDocument();
    expect(screen.getByText('total 1500ms')).toBeInTheDocument();
  });

  it('renders injected chaos-latency segment and the injected/real legend for a proxied response', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-chaos-timed',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/data',
              headers: [{ name: 'host', values: ['example.com'] }],
            },
            httpResponse: {
              statusCode: 200,
              timing: {
                connectionTimeInMillis: 10,
                timeToFirstByteInMillis: 60,
                totalTimeInMillis: 100,
                requestStartedMillis: 1700000000000,
                connectionEstablishedMillis: 1700000000010,
                responseReceivedMillis: 1700000000100,
                injectedChaosLatencyMillis: 500,
                injectedDelayMillis: null,
                breakpointHeldMillis: null,
              },
            },
          },
        },
      ],
    });

    renderTrafficInspector();
    await user.click(screen.getByText(/\/api\/data/));

    // Real segments (proxied) plus the injected chaos-latency segment are rendered
    expect(screen.getByTestId('timing-waterfall')).toBeInTheDocument();
    expect(screen.getByTestId('timing-segment-connect')).toBeInTheDocument();
    expect(screen.getByTestId('timing-segment-injected-chaos')).toBeInTheDocument();
    // Grouped legend distinguishes injected from real
    expect(screen.getByTestId('timing-legend-injected')).toBeInTheDocument();
    expect(screen.getByText('Injected by MockServer:')).toBeInTheDocument();
    expect(screen.getByText('Chaos latency')).toBeInTheDocument();
    // Injected total chip surfaces the injected sum
    expect(screen.getByText('injected 500ms')).toBeInTheDocument();
  });

  it('renders a waterfall with a processing segment and injected delay for a mock-served response', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      recordedRequests: [
        {
          key: 'req-mock-timed',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/mock-delayed',
            },
            // Mock-served: no connect/TTFB, total already includes the injected delay
            httpResponse: {
              statusCode: 200,
              timing: {
                connectionTimeInMillis: null,
                timeToFirstByteInMillis: null,
                totalTimeInMillis: 205,
                requestStartedMillis: null,
                connectionEstablishedMillis: null,
                responseReceivedMillis: null,
                injectedChaosLatencyMillis: null,
                injectedDelayMillis: 200,
                breakpointHeldMillis: null,
              },
            },
          },
        },
      ],
    });

    renderTrafficInspector();
    await user.click(screen.getByText(/\/api\/mock-delayed/));

    // Mock-served entries now render a waterfall too (previously proxy-only)
    expect(screen.getByTestId('timing-waterfall')).toBeInTheDocument();
    // No connect segment on a mock; a real "processing" segment plus the injected delay segment
    expect(screen.queryByTestId('timing-segment-connect')).not.toBeInTheDocument();
    expect(screen.getByTestId('timing-segment-processing')).toBeInTheDocument();
    expect(screen.getByTestId('timing-segment-injected-delay')).toBeInTheDocument();
    expect(screen.getByText('Response delay')).toBeInTheDocument();
    expect(screen.getByText('injected 200ms')).toBeInTheDocument();
  });
});

// Bedrock InvokeModel bodies of the non-Anthropic families, from the AWS model-parameters
// pages (model-parameters-meta.html, model-parameters-mistral-text-completion.html under
// https://docs.aws.amazon.com/bedrock/latest/userguide/). Mistral reports token counts
// only in the x-amzn-bedrock-*-token-count response headers.
describe('TrafficInspector — Bedrock InvokeModel on other model families', () => {
  beforeEach(() => {
    useDashboardStore.setState({ proxiedRequests: [], activeExpectations: [], trafficSearch: '', selectedTrafficKey: null });
  });

  function bedrockInvoke(key: string, modelId: string, request: unknown, response: unknown, headers: unknown[] = []) {
    return {
      key,
      value: {
        httpRequest: {
          method: 'POST',
          path: `/model/${modelId}/invoke`,
          headers: [{ name: 'host', values: ['bedrock-runtime.us-east-1.amazonaws.com'] }],
          body: { type: 'JSON', json: JSON.stringify(request) },
        },
        httpResponse: {
          statusCode: 200,
          headers: [{ name: 'content-type', values: ['application/json'] }, ...headers],
          body: { type: 'JSON', json: JSON.stringify(response) },
        },
      },
    };
  }

  it('shows the reply and token counts of a Llama call', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      proxiedRequests: [
        bedrockInvoke(
          'llama-1',
          'meta.llama3-70b-instruct-v1:0',
          { prompt: 'Describe a hello world program.', max_gen_len: 512 },
          { generation: 'It prints a greeting.', prompt_token_count: 12, generation_token_count: 4, stop_reason: 'stop' },
        ),
      ],
    });
    renderTrafficInspector();
    expect(screen.getByText('12 in / 4 out')).toBeInTheDocument();
    await user.click(screen.getAllByText(/meta\.llama3-70b-instruct/)[0]!);
    await user.click(screen.getByRole('tab', { name: 'Conversation' }));
    expect(screen.getByText('It prints a greeting.')).toBeInTheDocument();
  });

  it('shows the reply of a Mistral call and its token counts from the response headers', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      proxiedRequests: [
        bedrockInvoke(
          'mistral-1',
          'mistral.mistral-7b-instruct-v0:2',
          { prompt: '<s>[INST] Name a condiment [/INST]', max_tokens: 400 },
          { outputs: [{ text: 'Mustard.', stop_reason: 'stop' }] },
          [
            { name: 'X-Amzn-Bedrock-Input-Token-Count', values: ['11'] },
            { name: 'X-Amzn-Bedrock-Output-Token-Count', values: ['3'] },
          ],
        ),
      ],
    });
    renderTrafficInspector();
    expect(screen.getByText('11 in / 3 out')).toBeInTheDocument();
    await user.click(screen.getAllByText(/mistral\.mistral-7b-instruct/)[0]!);
    await user.click(screen.getByRole('tab', { name: 'Conversation' }));
    expect(screen.getByText('Mustard.')).toBeInTheDocument();
  });
});

describe('TrafficInspector — compare two requests (diff)', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [
        {
          key: 'req-a',
          value: {
            httpRequest: { method: 'GET', path: '/api/users', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 200 },
          },
        },
        {
          key: 'req-b',
          value: {
            httpRequest: { method: 'POST', path: '/api/users', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 201 },
          },
        },
      ],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('lets the user pick two requests and diffs them via PUT /mockserver/diff', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        diffCount: 1,
        identical: false,
        diffs: [{ field: 'method', expectedValue: 'GET', actualValue: 'POST', diffType: 'CHANGED' }],
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    renderTrafficInspector();

    // No compare checkboxes until compare mode is enabled.
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Compare requests/i }));

    expect(screen.getAllByRole('checkbox')).toHaveLength(2);

    // Diff button disabled until exactly two are picked.
    const diffButton = screen.getByRole('button', { name: /Diff \(/ });
    expect(diffButton).toBeDisabled();

    // Pick by request identity, not row position: the list renders in console
    // order (oldest first, newest at the bottom), so a positional checkbox[0]/[1]
    // pick would select a different pair than intended. The diff maps the FIRST
    // pick to `expected` and the SECOND to `actual`, so pick req-a (GET) first,
    // then req-b (POST), to assert that ordering below.
    const checkboxForMethod = (method: string) => {
      const row = screen.getByText(method).closest('div');
      return within(row!).getByRole('checkbox');
    };

    await user.click(checkboxForMethod('GET'));
    expect(screen.getByRole('button', { name: /Diff \(1\/2\)/ })).toBeDisabled();
    await user.click(checkboxForMethod('POST'));

    const ready = screen.getByRole('button', { name: /Diff \(2\/2\)/ });
    expect(ready).toBeEnabled();
    await user.click(ready);

    // Dialog opens pre-populated; Compare submits the two picked requests to the diff endpoint.
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Compare' }));

    expect(await within(dialog).findByText('Request Diff')).toBeInTheDocument();

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toMatch(/\/mockserver\/diff$/);
    expect((init as RequestInit).method).toBe('PUT');
    const body = JSON.parse((init as RequestInit).body as string);
    // First pick (req-a) maps to expected, second pick (req-b) to actual, sending the httpRequest definitions.
    expect(body.expected).toMatchObject({ method: 'GET', path: '/api/users' });
    expect(body.actual).toMatchObject({ method: 'POST', path: '/api/users' });
  });

  it('caps the selection at two requests', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      recordedRequests: [
        { key: 'r1', value: { httpRequest: { method: 'GET', path: '/a' }, httpResponse: { statusCode: 200 } } },
        { key: 'r2', value: { httpRequest: { method: 'GET', path: '/b' }, httpResponse: { statusCode: 200 } } },
        { key: 'r3', value: { httpRequest: { method: 'GET', path: '/c' }, httpResponse: { statusCode: 200 } } },
      ],
    });

    renderTrafficInspector();
    await user.click(screen.getByRole('button', { name: /Compare requests/i }));

    const checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[0]!);
    await user.click(checkboxes[1]!);

    // Once two are picked, the remaining unchecked checkbox is disabled.
    expect(checkboxes[2]!).toBeDisabled();
    // Already-checked ones remain interactive so the user can deselect.
    expect(checkboxes[0]!).toBeEnabled();
  });
});

describe('TrafficInspector — Diff Pool', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [
        {
          key: 'pool-a',
          value: {
            httpRequest: { method: 'GET', path: '/api/alpha', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 200 },
          },
        },
        {
          key: 'pool-b',
          value: {
            httpRequest: { method: 'POST', path: '/api/beta', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 201 },
          },
        },
      ],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  async function addToPool(user: ReturnType<typeof userEvent.setup>, pathText: RegExp) {
    await user.click(screen.getByText(pathText));
    await user.click(screen.getByRole('button', { name: /Add to Diff Pool/i }));
  }

  it('adds the selected request to the pool and shows the header chip count', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    // No chip before anything is staged.
    expect(screen.queryByRole('button', { name: /Diff Pool \(/i })).not.toBeInTheDocument();

    await addToPool(user, /\/api\/alpha/);

    // Chip appears with a count of one; the action flips to the "already in pool" state.
    expect(screen.getByRole('button', { name: /Diff Pool \(1\)/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /In Diff Pool/i })).toBeDisabled();

    await addToPool(user, /\/api\/beta/);
    expect(screen.getByRole('button', { name: /Diff Pool \(2\)/i })).toBeInTheDocument();
  });

  it('removes a single entry and clears the whole pool from the popover', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    await addToPool(user, /\/api\/alpha/);
    await addToPool(user, /\/api\/beta/);

    // Open the pool popover from the header chip.
    await user.click(screen.getByRole('button', { name: /Diff Pool \(2\)/i }));
    // Both entries are listed.
    expect(screen.getByRole('button', { name: /Remove GET \/api\/alpha from Diff Pool/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Remove POST \/api\/beta from Diff Pool/i })).toBeInTheDocument();

    // Remove one entry -> only the other remains listed.
    await user.click(screen.getByRole('button', { name: /Remove GET \/api\/alpha from Diff Pool/i }));
    expect(screen.queryByRole('button', { name: /Remove GET \/api\/alpha from Diff Pool/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Remove POST \/api\/beta from Diff Pool/i })).toBeInTheDocument();

    // Clear All empties the pool, closes the popover, and the header chip disappears.
    await user.click(screen.getByRole('button', { name: /Clear All/i }));
    expect(screen.queryByRole('button', { name: /Diff Pool \(/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Remove POST \/api\/beta from Diff Pool/i })).not.toBeInTheDocument();
  });

  it('picks two pooled entries and diffs them via the shared dialog + PUT /mockserver/diff', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        diffCount: 1,
        identical: false,
        diffs: [{ field: 'method', expectedValue: 'GET', actualValue: 'POST', diffType: 'CHANGED' }],
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    renderTrafficInspector();
    await addToPool(user, /\/api\/alpha/);
    await addToPool(user, /\/api\/beta/);

    await user.click(screen.getByRole('button', { name: /Diff Pool \(2\)/i }));

    // "Diff Selected" is disabled until exactly two are picked.
    const diffSelected = screen.getByRole('button', { name: /Diff Selected/i });
    expect(diffSelected).toBeDisabled();

    await user.click(screen.getByRole('checkbox', { name: /Pick GET \/api\/alpha/i }));
    await user.click(screen.getByRole('checkbox', { name: /Pick POST \/api\/beta/i }));
    expect(diffSelected).toBeEnabled();
    await user.click(diffSelected);

    // The shared diff dialog opens seeded with the two pooled requests.
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Compare' }));
    expect(await within(dialog).findByText('Request Diff')).toBeInTheDocument();

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toMatch(/\/mockserver\/diff$/);
    const body = JSON.parse((init as RequestInit).body as string);
    // First pick (alpha) -> expected, second pick (beta) -> actual, sending httpRequest definitions.
    expect(body.expected).toMatchObject({ method: 'GET', path: '/api/alpha' });
    expect(body.actual).toMatchObject({ method: 'POST', path: '/api/beta' });
  });

  it('caps the popover selection at two entries', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      recordedRequests: [
        { key: 'p1', value: { httpRequest: { method: 'GET', path: '/one' }, httpResponse: { statusCode: 200 } } },
        { key: 'p2', value: { httpRequest: { method: 'GET', path: '/two' }, httpResponse: { statusCode: 200 } } },
        { key: 'p3', value: { httpRequest: { method: 'GET', path: '/three' }, httpResponse: { statusCode: 200 } } },
      ],
    });
    renderTrafficInspector();

    await addToPool(user, /\/one/);
    await addToPool(user, /\/two/);
    await addToPool(user, /\/three/);

    await user.click(screen.getByRole('button', { name: /Diff Pool \(3\)/i }));
    const picks = screen.getAllByRole('checkbox');
    await user.click(picks[0]!);
    await user.click(picks[1]!);
    // The third checkbox is disabled once two are picked; picked ones stay interactive.
    expect(picks[2]!).toBeDisabled();
    expect(picks[0]!).toBeEnabled();
  });
});

describe('TrafficInspector — Capture as mock for standard HTTP (WS5.2)', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('shows the "Capture as mock" button when a standard (non-LLM) HTTP request is selected', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-plain-http',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/widgets',
              headers: [{ name: 'host', values: ['example.com'] }],
            },
            httpResponse: {
              statusCode: 200,
              body: { type: 'JSON', json: '{"widgets":[]}' },
            },
          },
        },
      ],
    });

    renderTrafficInspector();

    // Select the standard HTTP row
    const row = screen.getByText(/\/api\/widgets/);
    await user.click(row);

    // The capture button must be offered for plain HTTP traffic, not just LLM traffic.
    expect(
      screen.getByRole('button', { name: /Capture as mock/i }),
    ).toBeInTheDocument();
  });

  it('opens the capture dialog and registers a generic expectation via PUT /mockserver/expectation', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-capture-http',
          value: {
            httpRequest: {
              method: 'POST',
              path: '/api/orders',
              headers: [{ name: 'host', values: ['example.com'] }],
              body: { type: 'JSON', json: '{"sku":"A1"}' },
            },
            httpResponse: {
              statusCode: 201,
              body: { type: 'JSON', json: '{"id":7}' },
            },
          },
        },
      ],
    });

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      text: async () => '',
    });
    vi.stubGlobal('fetch', fetchMock);

    renderTrafficInspector();

    const row = screen.getByText(/\/api\/orders/);
    await user.click(row);

    await user.click(screen.getByRole('button', { name: /Capture as mock/i }));

    // Dialog opens in generic-HTTP mode pre-populated from the captured request.
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Capture as Mock')).toBeInTheDocument();

    // Register the expectation.
    await user.click(within(dialog).getByRole('button', { name: 'Register' }));

    // The generic path uses PUT /mockserver/expectation with the captured method/path/status/body.
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/mockserver/expectation'),
      expect.objectContaining({ method: 'PUT' }),
    );
    const [, init] = fetchMock.mock.calls[0]!;
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.httpRequest).toMatchObject({ method: 'POST', path: '/api/orders' });
    expect(body.httpResponse).toMatchObject({ statusCode: 201 });
    expect(body.httpResponse.body).toBe('{"id":7}');
  });
});

describe('TrafficInspector — Replay button', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows Replay button in detail pane for a selected generic request', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-replay',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/test',
              headers: [{ name: 'host', values: ['example.com'] }],
            },
            httpResponse: {
              statusCode: 200,
              body: { type: 'STRING', string: 'original response' },
            },
          },
        },
      ],
    });

    renderTrafficInspector();

    // Click on the row to select it
    const row = screen.getByText(/\/api\/test/);
    await user.click(row);

    // The Replay button should be visible in the detail pane
    expect(screen.getByRole('button', { name: /Replay/i })).toBeInTheDocument();
  });

  it('opens replay dialog and calls PUT /mockserver/replay on click', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-replay-2',
          value: {
            httpRequest: {
              method: 'POST',
              path: '/api/submit',
              headers: [{ name: 'host', values: ['example.com'] }],
              body: { type: 'JSON', json: '{"data":"test"}' },
            },
            httpResponse: {
              statusCode: 200,
            },
          },
        },
      ],
    });

    // Mock fetch to intercept the replay call
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ statusCode: 200, body: 'replayed OK' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    renderTrafficInspector();

    // Select the request
    const row = screen.getByText(/\/api\/submit/);
    await user.click(row);

    // Click the Replay button in the detail pane
    const replayBtn = screen.getByRole('button', { name: /Replay/i });
    await user.click(replayBtn);

    // The replay dialog should open
    expect(screen.getByText('Replay Request')).toBeInTheDocument();

    // Click the Replay button inside the dialog
    const dialogReplayBtn = within(screen.getByRole('dialog')).getByRole('button', { name: /Replay/i });
    await user.click(dialogReplayBtn);

    // Verify fetch was called with the correct URL and method
    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/mockserver/replay'),
      expect.objectContaining({
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    // The response should be displayed
    expect(await screen.findByText('Upstream Response')).toBeInTheDocument();
  });

  it('shows error alert and clears loading spinner when replay returns a server error', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-replay-err',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/failing',
              headers: [{ name: 'host', values: ['example.com'] }],
            },
            httpResponse: { statusCode: 200 },
          },
        },
      ],
    });

    // Stub fetch to return a 503 error response
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 503,
      text: async () => 'Service Unavailable',
    } as Response);

    renderTrafficInspector();

    // Select the request
    const row = screen.getByText(/\/api\/failing/);
    await user.click(row);

    // Open the replay dialog
    const replayBtn = screen.getByRole('button', { name: /Replay/i });
    await user.click(replayBtn);

    const dialog = screen.getByRole('dialog');

    // Click the Replay button inside the dialog
    const dialogReplayBtn = within(dialog).getByRole('button', { name: /Replay/i });
    await user.click(dialogReplayBtn);

    // An error Alert (severity="error") should appear. The replay failure is now
    // routed through humanizeError, so a 503 surfaces the friendly internal-error
    // message (the raw "503: Service Unavailable" text is kept for a Details pane,
    // not shown inline by this Alert).
    const errorAlert = await within(dialog).findByRole('alert');
    expect(errorAlert).toBeInTheDocument();
    expect(errorAlert).toHaveTextContent(/internal error/i);

    // The loading spinner should be gone (dialog replay button should be re-enabled)
    expect(within(dialog).getByRole('button', { name: /Replay/i })).toBeEnabled();
    expect(within(dialog).queryByRole('progressbar')).not.toBeInTheDocument();
  });
});

describe('TrafficInspector — Repeat Advanced', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-repeat',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/repeat-me',
              headers: [{ name: 'host', values: ['example.com'] }],
            },
            httpResponse: { statusCode: 200 },
          },
        },
      ],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a Repeat… button and opens the dialog on click', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    await user.click(screen.getByText(/\/api\/repeat-me/));

    const repeatBtn = screen.getByRole('button', { name: /^Repeat/i });
    await user.click(repeatBtn);

    expect(screen.getByText('Repeat Request')).toBeInTheDocument();
    // Default iterations = 10, concurrency = 1, delay = 0.
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByLabelText('Iterations')).toHaveValue(10);
    expect(within(dialog).getByLabelText('Concurrency')).toHaveValue(1);
    expect(within(dialog).getByLabelText('Delay (ms)')).toHaveValue(0);
  });

  it('issues exactly N replay calls and reports the summary', async () => {
    const user = userEvent.setup();
    // A fresh Response per call — a Response body stream can only be read once.
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ statusCode: 200 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    renderTrafficInspector();
    await user.click(screen.getByText(/\/api\/repeat-me/));
    await user.click(screen.getByRole('button', { name: /^Repeat/i }));

    const dialog = screen.getByRole('dialog');
    const iterationsInput = within(dialog).getByLabelText('Iterations');
    await user.clear(iterationsInput);
    await user.type(iterationsInput, '3');

    await user.click(within(dialog).getByRole('button', { name: /Start/i }));

    // Completion summary appears once all three calls settle.
    expect(await within(dialog).findByText(/3 succeeded, 0 failed/i)).toBeInTheDocument();

    const replayCalls = fetchSpy.mock.calls.filter(([url]) =>
      typeof url === 'string' && url.includes('/mockserver/replay'),
    );
    expect(replayCalls).toHaveLength(3);
    expect(replayCalls[0]![1]).toEqual(
      expect.objectContaining({ method: 'PUT', headers: { 'Content-Type': 'application/json' } }),
    );
  });

  it('counts failed replays and shows a warning summary', async () => {
    const user = userEvent.setup();
    // Every replay returns a 502 → each counts as failed.
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 502,
      text: async () => 'Bad Gateway',
    } as Response);

    renderTrafficInspector();
    await user.click(screen.getByText(/\/api\/repeat-me/));
    await user.click(screen.getByRole('button', { name: /^Repeat/i }));

    const dialog = screen.getByRole('dialog');
    const iterationsInput = within(dialog).getByLabelText('Iterations');
    await user.clear(iterationsInput);
    await user.type(iterationsInput, '2');

    await user.click(within(dialog).getByRole('button', { name: /Start/i }));

    expect(await within(dialog).findByText(/0 succeeded, 2 failed/i)).toBeInTheDocument();
  });

  it('View Them seeds the traffic search with the request path', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ statusCode: 200 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    renderTrafficInspector();
    await user.click(screen.getByText(/\/api\/repeat-me/));
    await user.click(screen.getByRole('button', { name: /^Repeat/i }));

    const dialog = screen.getByRole('dialog');
    const iterationsInput = within(dialog).getByLabelText('Iterations');
    await user.clear(iterationsInput);
    await user.type(iterationsInput, '1');
    await user.click(within(dialog).getByRole('button', { name: /Start/i }));

    const viewBtn = await within(dialog).findByRole('button', { name: /View Them/i });
    await user.click(viewBtn);

    expect(useDashboardStore.getState().trafficSearch).toBe('path:/api/repeat-me');
  });
});

/**
 * Master/detail resize-divider tests.
 *
 * The vertical drag handle between the master list and the detail pane appears
 * ONLY in the side-by-side case (not stacked, an entry selected, not comparing).
 * jsdom has no layout, so we assert handle presence/absence and accessibility,
 * not pixel widths — the default master width renders without measurement.
 */
function stubMatchMedia(matches: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })) as unknown as typeof window.matchMedia;
}

const simpleRequest = {
  key: 'req-resize',
  value: {
    httpRequest: {
      method: 'GET',
      path: '/api/widgets',
      headers: [{ name: 'host', values: ['example.com'] }],
    },
    httpResponse: { statusCode: 200 },
  },
};

describe('TrafficInspector — search filtering', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [
        {
          key: 'req-users',
          value: {
            httpRequest: { method: 'GET', path: '/api/users', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 200 },
          },
        },
        {
          key: 'req-orders',
          value: {
            httpRequest: { method: 'POST', path: '/api/orders', headers: [{ name: 'host', values: ['shop.example.com'] }] },
            httpResponse: { statusCode: 201, body: { type: 'JSON', json: '{"sku":"WIDGET-42"}' } },
          },
        },
      ],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  it('filters rows by a field match (path) as the user types', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    // Both rows visible initially.
    expect(screen.getByText(/\/api\/users/)).toBeInTheDocument();
    expect(screen.getByText(/\/api\/orders/)).toBeInTheDocument();

    const search = screen.getByRole('textbox', { name: 'Search' });
    await user.type(search, 'orders');

    expect(screen.queryByText(/\/api\/users/)).not.toBeInTheDocument();
    expect(screen.getByText(/\/api\/orders/)).toBeInTheDocument();
  });

  it('filters rows by a full-text body match (the cached fallback path)', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    const search = screen.getByRole('textbox', { name: 'Search' });
    // "WIDGET-42" only appears inside the response body, exercising the cached
    // JSON.stringify fallback rather than the field-level match.
    await user.type(search, 'widget-42');

    expect(screen.getByText(/\/api\/orders/)).toBeInTheDocument();
    expect(screen.queryByText(/\/api\/users/)).not.toBeInTheDocument();
  });

  it('shows the empty-state message when nothing matches', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    const search = screen.getByRole('textbox', { name: 'Search' });
    await user.type(search, 'nonexistent-term-xyz');

    expect(screen.getByText('No matching requests')).toBeInTheDocument();
  });
});

describe('TrafficInspector — master/detail resize divider', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  afterEach(() => {
    // @ts-expect-error allow deleting the optional stub
    delete window.matchMedia;
  });

  it('renders the resize divider when an entry is selected side-by-side (desktop)', () => {
    useDashboardStore.setState({
      recordedRequests: [simpleRequest],
      selectedTrafficKey: 'req-resize',
    });
    renderTrafficInspector();
    const handle = screen.getByTestId('traffic-master-resizer');
    expect(handle).toBeInTheDocument();
    expect(handle).toHaveAttribute('role', 'separator');
    expect(handle).toHaveAttribute('aria-orientation', 'vertical');
  });

  it('does NOT render the divider when nothing is selected', () => {
    useDashboardStore.setState({
      recordedRequests: [simpleRequest],
      selectedTrafficKey: null,
    });
    renderTrafficInspector();
    expect(screen.queryByTestId('traffic-master-resizer')).not.toBeInTheDocument();
  });

  it('does NOT render the divider on a stacked (small-screen) viewport even with a selection', () => {
    stubMatchMedia(true);
    useDashboardStore.setState({
      recordedRequests: [simpleRequest],
      selectedTrafficKey: 'req-resize',
    });
    renderTrafficInspector();
    expect(screen.queryByTestId('traffic-master-resizer')).not.toBeInTheDocument();
  });
});

describe('TrafficInspector — secret-header masking (pure helper)', () => {
  it('preserves the auth scheme and keeps the last 4 chars of a Bearer token', () => {
    expect(maskSecretValue('Bearer sk-secret-abcd1234')).toBe('Bearer ••••1234');
    expect(maskSecretValue('Basic dXNlcjpXXYY')).toBe('Basic ••••XXYY');
  });

  it('masks a raw token entirely when shorter than 5 chars', () => {
    expect(maskSecretValue('abcd')).toBe('••••');
    expect(maskSecretValue('')).toBe('');
  });

  it('masks an api-key value without a scheme, keeping the last 4 chars', () => {
    expect(maskSecretValue('sk-ant-api03-XYZ9876')).toBe('••••9876');
  });

  it('masks secret headers in array form on httpRequest, leaving non-secrets intact', () => {
    const value = {
      httpRequest: {
        method: 'POST',
        path: '/v1/messages',
        headers: [
          { name: 'host', values: ['api.anthropic.com'] },
          { name: 'authorization', values: ['Bearer sk-live-TOPSECRET4321'] },
          { name: 'x-api-key', values: ['anthropic-key-ABCDEFGH'] },
        ],
      },
    };
    const masked = maskSecretsInValue(value) as typeof value;
    const headers = masked.httpRequest.headers;
    expect(headers[0]).toEqual({ name: 'host', values: ['api.anthropic.com'] });
    expect(headers[1]!.values).toEqual(['Bearer ••••4321']);
    expect(headers[2]!.values).toEqual(['••••EFGH']);
    // The original object must not be mutated.
    expect(value.httpRequest.headers[1]!.values[0]).toBe('Bearer sk-live-TOPSECRET4321');
  });

  it('masks secret headers in object form on httpResponse (e.g. Set-Cookie)', () => {
    const value = {
      httpResponse: {
        statusCode: 200,
        headers: { 'Set-Cookie': ['session=SUPERSECRETVALUE'], 'content-type': ['application/json'] },
      },
    };
    const masked = maskSecretsInValue(value) as typeof value;
    expect(masked.httpResponse.headers['Set-Cookie']).toEqual(['••••ALUE']);
    expect(masked.httpResponse.headers['content-type']).toEqual(['application/json']);
  });

  it('returns the original reference when there is nothing to mask', () => {
    const value = { httpRequest: { method: 'GET', headers: [{ name: 'host', values: ['x'] }] } };
    expect(maskSecretsInValue(value)).toBe(value);
  });
});

describe('TrafficInspector — masked secrets flow into the Diff/Compare view', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [
        {
          key: 'req-secret',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/secure',
              headers: [
                { name: 'host', values: ['example.com'] },
                { name: 'authorization', values: ['Bearer sk-secret-abcd1234'] },
              ],
            },
            httpResponse: { statusCode: 200 },
          },
        },
        {
          key: 'req-plain',
          value: {
            httpRequest: { method: 'GET', path: '/api/plain', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 200 },
          },
        },
      ],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('seeds the diff editor with the MASKED Authorization value, not the raw token', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    await user.click(screen.getByRole('button', { name: /Compare requests/i }));
    const checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[0]!); // console order (oldest first) — order not important here, both editors are checked below
    await user.click(checkboxes[1]!);
    await user.click(screen.getByRole('button', { name: /Diff \(2\/2\)/ }));

    const dialog = await screen.findByRole('dialog');
    // The masked token must appear in one of the seeded editors; the raw token never does.
    const expected = within(dialog).getByLabelText('Expected request (JSON)') as HTMLTextAreaElement;
    const actual = within(dialog).getByLabelText('Actual request (JSON)') as HTMLTextAreaElement;
    const combined = `${expected.value}\n${actual.value}`;
    expect(combined).toContain('••••1234');
    expect(combined).not.toContain('sk-secret-abcd1234');
  });
});

describe('TrafficInspector — search indexes decoded BINARY body text', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  it('matches a word that only exists in the DECODED base64 body', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      recordedRequests: [
        {
          key: 'req-binary',
          value: {
            httpRequest: { method: 'POST', path: '/api/binary', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: {
              statusCode: 200,
              // base64 of {"prompt":"PINEAPPLE-TOKEN"} — searching the base64 string would not match.
              body: { type: 'BINARY', base64Bytes: btoa('{"prompt":"PINEAPPLE-TOKEN"}') },
            },
          },
        },
        {
          key: 'req-other',
          value: {
            httpRequest: { method: 'GET', path: '/api/other', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 200 },
          },
        },
      ],
    });

    renderTrafficInspector();
    const search = screen.getByRole('textbox', { name: 'Search' });
    await user.type(search, 'pineapple-token');

    expect(screen.getByText(/\/api\/binary/)).toBeInTheDocument();
    expect(screen.queryByText(/\/api\/other/)).not.toBeInTheDocument();
  });
});

describe('TrafficInspector — MCP JSON-RPC error classification (pure helper)', () => {
  const base: McpParsed = {
    kind: 'mcp', method: 'tools/call', id: 1, params: {}, result: null, error: null, isResponse: true,
  };

  it('flags a JSON-RPC error object and exposes its numeric code + message', () => {
    const info = mcpErrorInfo({ ...base, error: { code: -32601, message: 'Method not found' } }, 200);
    expect(info.isError).toBe(true);
    expect(info.code).toBe(-32601);
    expect(info.message).toBe('Method not found');
  });

  it('flags a non-2xx HTTP status even without a JSON-RPC error object', () => {
    expect(mcpErrorInfo(base, 500).isError).toBe(true);
    expect(mcpErrorInfo(base, 400).isError).toBe(true);
  });

  it('treats a clean 2xx result as not an error', () => {
    const info = mcpErrorInfo({ ...base, result: { ok: true } }, 200);
    expect(info.isError).toBe(false);
    expect(info.code).toBeNull();
  });
});

describe('TrafficInspector — bulk select + clear', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [
        { key: 'r1', value: { httpRequest: { method: 'GET', path: '/a' }, httpResponse: { statusCode: 200 } } },
        { key: 'r2', value: { httpRequest: { method: 'GET', path: '/b' }, httpResponse: { statusCode: 200 } } },
        { key: 'r3', value: { httpRequest: { method: 'GET', path: '/c' }, httpResponse: { statusCode: 200 } } },
      ],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
      notification: null,
    });
  });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('reveals uncapped select checkboxes and a Clear button in select mode', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    // No checkboxes until a mode is enabled.
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Select requests/i }));

    // Per-row checkboxes (3) + the header "select all" checkbox = 4, none capped/disabled.
    // Per-row labels are keyed on request identity (method + path), e.g.
    // "Select GET /a", not a live-window ordinal — so exclude the "select all" one.
    const rowCheckboxes = screen.getAllByRole('checkbox').filter((c) => {
      const label = c.getAttribute('aria-label');
      return !!label && label.startsWith('Select ') && label !== 'Select all requests';
    });
    expect(rowCheckboxes).toHaveLength(3);
    await user.click(rowCheckboxes[0]!);
    await user.click(rowCheckboxes[1]!);
    await user.click(rowCheckboxes[2]!);
    // No two-item cap — all three stay enabled and checked.
    rowCheckboxes.forEach((c) => expect(c).toBeEnabled());
    expect(screen.getByRole('button', { name: /Clear \(3\)/ })).toBeEnabled();
  });

  it('bulk-clears selected requests via clearLoggedRequest and drops the rows', async () => {
    const user = userEvent.setup();
    const spy = vi.spyOn(trafficLib, 'clearLoggedRequest').mockResolvedValue(undefined);
    renderTrafficInspector();

    await user.click(screen.getByRole('button', { name: /Select requests/i }));
    await user.click(screen.getByLabelText('Select all requests'));
    await user.click(screen.getByRole('button', { name: /Clear \(3\)/ }));

    // Confirmation gate — nothing cleared until confirmed.
    expect(spy).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Clear selected' }));

    await vi.waitFor(() => expect(spy).toHaveBeenCalledTimes(3));
    // Each call sends the request definition (the httpRequest) for a selected row.
    const paths = spy.mock.calls.map((c) => (c[1] as { path: string }).path).sort();
    expect(paths).toEqual(['/a', '/b', '/c']);

    await vi.waitFor(() =>
      expect(useDashboardStore.getState().recordedRequests).toHaveLength(0),
    );
    expect(useDashboardStore.getState().notification).toMatchObject({ severity: 'success' });
  });

  it('leaving compare mode is not broken by select mode (mutually exclusive)', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    await user.click(screen.getByRole('button', { name: /Compare requests/i }));
    expect(screen.getByRole('button', { name: /Diff \(/ })).toBeInTheDocument();

    // Entering select mode exits compare mode (Diff button gone).
    await user.click(screen.getByRole('button', { name: /Select requests/i }));
    expect(screen.queryByRole('button', { name: /Diff \(/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Clear \(0\)/ })).toBeDisabled();
  });
});

// ---------------------------------------------------------------------------
// Copy as curl — buildRequestCurl pure helper
// ---------------------------------------------------------------------------

describe('buildRequestCurl', () => {
  it('reproduces the captured request against its original target', () => {
    const value = {
      httpRequest: {
        method: 'POST',
        path: '/v1/chat',
        secure: true,
        headers: [
          { name: 'Host', values: ['api.example.com'] },
          { name: 'Content-Type', values: ['application/json'] },
          { name: 'Authorization', values: ['Bearer sk-secret-1234'] },
        ],
        queryStringParameters: [{ name: 'q', values: ['1'] }],
        body: { type: 'STRING', string: '{"hello":"world"}' },
      },
      httpResponse: { statusCode: 200 },
    };

    const curl = buildRequestCurl(value, summarizeTraffic(value));

    // Method + full URL (scheme from `secure`, host from the Host header, query appended).
    expect(curl).toContain("curl -X 'POST' 'https://api.example.com/v1/chat?q=1'");
    // Non-secret header carried through.
    expect(curl).toContain("-H 'Content-Type: application/json'");
    // Body carried through as --data-raw.
    expect(curl).toContain(`--data-raw '{"hello":"world"}'`);
    // Host header is not duplicated (it is in the URL).
    expect(curl).not.toMatch(/-H 'Host:/i);
    // Secret header value is masked, not leaked verbatim.
    expect(curl).toContain('1234');
    expect(curl).not.toContain('sk-secret-1234');
  });

  it('defaults method to GET and http scheme, and omits the body when absent', () => {
    const value = {
      httpRequest: {
        path: '/api/ping',
        headers: [{ name: 'host', values: ['svc.local'] }],
      },
      httpResponse: { statusCode: 200 },
    };
    const curl = buildRequestCurl(value, summarizeTraffic(value));
    expect(curl).toContain("curl -X 'GET' 'http://svc.local/api/ping'");
    expect(curl).not.toContain('--data-raw');
  });

  it('returns an empty string when there is no request', () => {
    const value = { httpResponse: { statusCode: 200 } };
    expect(buildRequestCurl(value, summarizeTraffic(value))).toBe('');
  });
});

// ---------------------------------------------------------------------------
// Unmatched detection
// ---------------------------------------------------------------------------

describe('isUnmatchedResponse', () => {
  it('is true for a 404 Not Found response', () => {
    expect(isUnmatchedResponse({ httpResponse: { statusCode: 404, reasonPhrase: 'Not Found' } })).toBe(true);
    // reasonPhrase comparison is case-insensitive.
    expect(isUnmatchedResponse({ httpResponse: { statusCode: 404, reasonPhrase: 'not found' } })).toBe(true);
  });

  it('is false for a matched response or a 404 without the Not Found phrase', () => {
    expect(isUnmatchedResponse({ httpResponse: { statusCode: 200, reasonPhrase: 'OK' } })).toBe(false);
    expect(isUnmatchedResponse({ httpResponse: { statusCode: 404 } })).toBe(false);
    expect(isUnmatchedResponse({})).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// Operator-aware search in TrafficInspector
// ---------------------------------------------------------------------------

describe('TrafficInspector — operator search', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [
        {
          key: 'ok',
          value: {
            httpRequest: { method: 'GET', path: '/api/ok', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 200 },
          },
        },
        {
          key: 'err',
          value: {
            httpRequest: { method: 'POST', path: '/api/err', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 500 },
          },
        },
      ],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  it('filters by status comparator operator', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    expect(screen.getByText(/\/api\/ok/)).toBeInTheDocument();
    expect(screen.getByText(/\/api\/err/)).toBeInTheDocument();

    await user.type(screen.getByRole('textbox', { name: 'Search' }), 'status:>=400');

    expect(screen.queryByText(/\/api\/ok/)).not.toBeInTheDocument();
    expect(screen.getByText(/\/api\/err/)).toBeInTheDocument();
  });

  it('filters by method operator', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    await user.type(screen.getByRole('textbox', { name: 'Search' }), 'method:POST');

    expect(screen.queryByText(/\/api\/ok/)).not.toBeInTheDocument();
    expect(screen.getByText(/\/api\/err/)).toBeInTheDocument();
  });

  it('filters by path glob operator', async () => {
    const user = userEvent.setup();
    renderTrafficInspector();

    await user.type(screen.getByRole('textbox', { name: 'Search' }), 'path:/api/o*');

    expect(screen.getByText(/\/api\/ok/)).toBeInTheDocument();
    expect(screen.queryByText(/\/api\/err/)).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Unmatched badge + mismatch-debugging actions
// ---------------------------------------------------------------------------

describe('TrafficInspector — unmatched requests', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [
        {
          key: 'missed',
          value: {
            httpRequest: { method: 'GET', path: '/missing', headers: [{ name: 'host', values: ['localhost'] }] },
            httpResponse: { statusCode: 404, reasonPhrase: 'Not Found' },
          },
        },
        {
          key: 'hit',
          value: {
            httpRequest: { method: 'GET', path: '/found', headers: [{ name: 'host', values: ['localhost'] }] },
            httpResponse: { statusCode: 200, reasonPhrase: 'OK' },
          },
        },
      ],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('shows an "unmatched" badge that opens the Explain Unmatched dialog', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ unmatchedRequests: [] }),
    }));

    renderTrafficInspector();

    // The badge carries no count: the unmatched tally is derived from the capped
    // live window, so a number there would pin at the cap rather than count
    // captured traffic. It's just a labelled entry point to the explain dialog.
    const badge = screen.getByText('unmatched');
    expect(badge).toBeInTheDocument();

    await user.click(badge);
    expect(await screen.findByText('Explain Unmatched Requests')).toBeInTheDocument();
  });

  it('offers "Why Didn\'t This Match?" and "Generate Stub" on an unmatched detail pane', async () => {
    const user = userEvent.setup();
    const debugMismatch = vi.fn().mockResolvedValue(undefined);
    const generateStub = vi.fn().mockResolvedValue(undefined);

    renderWithMismatchContexts(debugMismatch, generateStub);

    await user.click(screen.getByText(/\/missing/));

    const whyButton = screen.getByRole('button', { name: /Why Didn't This Match/i });
    expect(whyButton).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Generate Stub/i })).toBeInTheDocument();

    await user.click(whyButton);
    expect(debugMismatch).toHaveBeenCalledTimes(1);
    expect(debugMismatch.mock.calls[0]![0]).toMatchObject({ method: 'GET', path: '/missing' });
  });

  it('does not offer mismatch actions on a matched request', async () => {
    const user = userEvent.setup();
    const debugMismatch = vi.fn().mockResolvedValue(undefined);
    const generateStub = vi.fn().mockResolvedValue(undefined);

    renderWithMismatchContexts(debugMismatch, generateStub);

    await user.click(screen.getByText(/\/found/));

    expect(screen.queryByRole('button', { name: /Why Didn't This Match/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Generate Stub/i })).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Actionable empty state
// ---------------------------------------------------------------------------

describe('TrafficInspector — empty state', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  it('shows guidance with a copyable proxy curl example when no traffic exists', () => {
    renderTrafficInspector();
    expect(screen.getByText('No traffic captured yet.')).toBeInTheDocument();
    expect(screen.getByText(/curl -x http:\/\/.+ http:\/\/example\.com/)).toBeInTheDocument();
    expect(screen.getByText(/Get Started tab/i)).toBeInTheDocument();
  });
});

describe('TrafficInspector — Structured detail pane (generic HTTP)', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  it('defaults to structured Request/Response tabs with Raw JSON kept last', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-structured',
          value: {
            httpRequest: {
              method: 'POST',
              path: '/api/orders',
              headers: [
                { name: 'host', values: ['example.com'] },
                { name: 'content-type', values: ['application/json'] },
              ],
              queryStringParameters: [{ name: 'page', values: ['2'] }],
              body: { type: 'JSON', json: '{"sku":"A1"}' },
            },
            httpResponse: {
              statusCode: 201,
              reasonPhrase: 'Created',
              headers: [{ name: 'x-trace', values: ['abc123'] }],
              body: { type: 'JSON', json: '{"id":7}' },
            },
          },
        },
      ],
    });

    renderTrafficInspector();
    await user.click(screen.getByText(/\/api\/orders/));

    // Structured tabs render, Raw JSON is present as the last tab.
    const requestTab = screen.getByRole('tab', { name: 'Request' });
    expect(requestTab).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Response' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Raw JSON' })).toBeInTheDocument();
    // Request tab is selected by default.
    expect(requestTab).toHaveAttribute('aria-selected', 'true');

    // Request tab surfaces method/path, a query-parameter row, and a header row.
    expect(screen.getByText('/api/orders')).toBeInTheDocument();
    expect(screen.getByText('page')).toBeInTheDocument();
    expect(screen.getByText('content-type')).toBeInTheDocument();

    // Response tab surfaces status + reason and its own header.
    await user.click(screen.getByRole('tab', { name: 'Response' }));
    expect(screen.getByText('Created')).toBeInTheDocument();
    expect(screen.getByText('x-trace')).toBeInTheDocument();

    // Raw JSON tab still renders the raw tree.
    await user.click(screen.getByRole('tab', { name: 'Raw JSON' }));
    expect(screen.getByText('httpRequest')).toBeInTheDocument();
  });

  it('renders a non-JSON request body as raw text (fallback)', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-plaintext',
          value: {
            httpRequest: {
              method: 'POST',
              path: '/api/upload',
              headers: [{ name: 'host', values: ['example.com'] }],
              body: { type: 'STRING', string: 'just plain text, not json' },
            },
            httpResponse: { statusCode: 200 },
          },
        },
      ],
    });

    renderTrafficInspector();
    await user.click(screen.getByText(/\/api\/upload/));

    // The plain-text body is shown verbatim under the Request tab (no JSON tree).
    expect(screen.getByText('just plain text, not json')).toBeInTheDocument();
  });
});

describe('TrafficInspector — Promote to Mocks', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
      view: 'traffic',
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function seedProxied() {
    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-promote',
          value: {
            httpRequest: { method: 'GET', path: '/api/users', headers: [{ name: 'host', values: ['example.com'] }] },
            httpResponse: { statusCode: 200, body: { type: 'JSON', json: '{"ok":true}' } },
          },
        },
      ],
    });
  }

  it('disables the Promote button when there is no proxied traffic', () => {
    renderTrafficInspector();
    expect(screen.getByRole('button', { name: /Promote to Mocks/i })).toBeDisabled();
  });

  it('calls the promote endpoint with the search-derived filter and reports the created count', async () => {
    const user = userEvent.setup();
    seedProxied();
    useDashboardStore.setState({ trafficSearch: 'method:GET path:/api/users' });

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => [{ id: 'e1' }, { id: 'e2' }],
    });
    vi.stubGlobal('fetch', fetchMock);

    renderTrafficInspector();
    await user.click(screen.getByRole('button', { name: /Promote to Mocks/i }));

    const dialog = await screen.findByRole('dialog');
    // Filter prefilled from the search operators.
    expect(within(dialog).getByLabelText('Method filter')).toHaveValue('GET');
    expect(within(dialog).getByLabelText('Path filter')).toHaveValue('/api/users');

    await user.click(within(dialog).getByRole('button', { name: 'Run' }));

    // Hits the promote endpoint with the filter as the request-matcher body.
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/mockserver/recordings/promote'),
      expect.objectContaining({ method: 'PUT' }),
    );
    const [, init] = fetchMock.mock.calls[0]!;
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toMatchObject({ method: 'GET', path: '/api/users' });

    // Success message reports the number of created expectations.
    expect(await within(dialog).findByText(/Created 2 expectations/i)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: /View Expectations/i })).toBeInTheDocument();
  });

  it('surfaces the server error envelope when the promote call fails', async () => {
    const user = userEvent.setup();
    seedProxied();

    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      text: async () => 'no recorded traffic to promote',
    });
    vi.stubGlobal('fetch', fetchMock);

    renderTrafficInspector();
    await user.click(screen.getByRole('button', { name: /Promote to Mocks/i }));

    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Run' }));

    // The shared HumanErrorAlert shows a humanised 400 message...
    expect(await within(dialog).findByText(/rejected as invalid/i)).toBeInTheDocument();
    // ...and keeps the raw server body behind a Details toggle.
    await user.click(within(dialog).getByRole('button', { name: /details/i }));
    expect(within(dialog).getByText(/no recorded traffic to promote/i)).toBeInTheDocument();
  });
});

describe('TrafficInspector — "Create From This…" launchpad menu', () => {
  const MENU_LABEL = 'Create from this request…';

  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'req-http-1',
          value: {
            httpRequest: {
              method: 'GET',
              path: '/api/orders',
              headers: [{ name: 'host', values: ['api.example.com'] }],
            },
            httpResponse: { statusCode: 200 },
          },
        },
      ],
      recordedRequests: [],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
      view: 'traffic',
      pendingEditExpectation: null,
      pendingVerificationDraft: null,
      pendingChaosDraft: null,
    });
  });

  afterEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      pendingEditExpectation: null,
      pendingVerificationDraft: null,
      pendingChaosDraft: null,
    });
  });

  async function selectRowAndOpenMenu(user: ReturnType<typeof userEvent.setup>) {
    renderTrafficInspector();
    await user.click(screen.getByText(/\/api\/orders/));
    await user.click(screen.getByRole('button', { name: MENU_LABEL }));
  }

  it('offers the menu with all four actions in the detail pane', async () => {
    const user = userEvent.setup();
    await selectRowAndOpenMenu(user);
    expect(screen.getByRole('menuitem', { name: 'Create Mock' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Set Breakpoint' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Verify This Request' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Add Chaos For This Host/Path' })).toBeInTheDocument();
  });

  it('"Verify This Request" seeds the verification draft and navigates', async () => {
    const user = userEvent.setup();
    await selectRowAndOpenMenu(user);
    await user.click(screen.getByRole('menuitem', { name: 'Verify This Request' }));
    const state = useDashboardStore.getState();
    expect(state.pendingVerificationDraft).toEqual({ method: 'GET', path: '/api/orders' });
    expect(state.view).toBe('verification');
  });

  it('"Add Chaos For This Host/Path" seeds the chaos draft and navigates', async () => {
    const user = userEvent.setup();
    await selectRowAndOpenMenu(user);
    await user.click(screen.getByRole('menuitem', { name: 'Add Chaos For This Host/Path' }));
    const state = useDashboardStore.getState();
    expect(state.pendingChaosDraft).toEqual({ host: 'api.example.com', path: '/api/orders' });
    expect(state.view).toBe('chaos');
  });

  it('"Create Mock" loads a draft expectation into the Composer', async () => {
    const user = userEvent.setup();
    await selectRowAndOpenMenu(user);
    await user.click(screen.getByRole('menuitem', { name: 'Create Mock' }));
    const state = useDashboardStore.getState();
    expect(state.view).toBe('composer');
    const req = state.pendingEditExpectation?.['httpRequest'] as Record<string, unknown>;
    expect(req?.['method']).toBe('GET');
    expect(req?.['path']).toBe('/api/orders');
  });
});

describe('TrafficInspector — one row per proxied request (E2E-LIB-5)', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [
        {
          key: 'fwd_proxied',
          correlationId: 'corr-proxied',
          timestamp: '2026-10-10 10:00:01.000',
          value: { httpRequest: { method: 'GET', path: '/api/proxied' }, httpResponse: { statusCode: 200 } },
        },
      ],
      recordedRequests: [
        {
          key: 'mocked_request',
          correlationId: 'corr-mocked',
          timestamp: '2026-10-10 10:00:02.000',
          value: { httpRequest: { method: 'GET', path: '/api/mocked' }, httpResponse: { statusCode: 201 } },
        },
        {
          key: 'received_request',
          correlationId: 'corr-proxied',
          timestamp: '2026-10-10 10:00:00.900',
          value: { httpRequest: { method: 'GET', path: '/api/proxied' } },
        },
      ],
      activeExpectations: [],
      trafficSearch: '',
      selectedTrafficKey: null,
    });
  });

  it('lists a proxied request once, with its upstream response, beside a mocked one', () => {
    renderTrafficInspector();

    expect(screen.getAllByText('/api/proxied')).toHaveLength(1);
    expect(screen.getAllByText('/api/mocked')).toHaveLength(1);
  });
});
