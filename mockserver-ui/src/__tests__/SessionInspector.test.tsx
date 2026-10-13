import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import SessionInspector from '../components/SessionInspector';
import { useDashboardStore } from '../store';
import { AUTO_LOAD_MAX_CHARACTERS } from '../hooks/useLoadFullRow';
import type { JsonListItem } from '../types';

function renderInspector() {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <SessionInspector connectionParams={{ host: 'localhost', port: '1080', secure: false }} />
    </ThemeProvider>,
  );
}

function makeAnthropicRequest(
  key: string,
  agentId?: string,
  messages: unknown[] = [{ role: 'user', content: 'Hello' }],
): JsonListItem {
  const headers: Array<{ name: string; values: string[] }> = [
    { name: 'host', values: ['api.anthropic.com'] },
  ];
  if (agentId) {
    headers.push({ name: 'x-agent-id', values: [agentId] });
  }
  return {
    key,
    value: {
      httpRequest: {
        method: 'POST',
        path: '/v1/messages',
        headers,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            model: 'claude-sonnet-4-20250514',
            messages,
          }),
        },
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            model: 'claude-sonnet-4-20250514',
            content: [{ type: 'text', text: 'Hi!' }],
            usage: { input_tokens: 10, output_tokens: 5 },
            stop_reason: 'end_turn',
          }),
        },
      },
    },
  };
}

function makeOpenAiRequest(key: string, host = 'api.openai.com'): JsonListItem {
  return {
    key,
    value: {
      httpRequest: {
        method: 'POST',
        path: '/v1/chat/completions',
        headers: [{ name: 'host', values: [host] }],
        body: {
          type: 'JSON',
          json: JSON.stringify({
            model: 'gpt-4o',
            messages: [{ role: 'user', content: 'Where is my order?' }],
          }),
        },
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            model: 'gpt-4o',
            choices: [{ message: { role: 'assistant', content: 'Checking now.' } }],
            usage: { prompt_tokens: 12, completion_tokens: 4 },
          }),
        },
      },
    },
  };
}

// Bedrock Converse wire shape: https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
function makeBedrockConverseRequest(key: string, text: string, agentId?: string): JsonListItem {
  const headers: Array<{ name: string; values: string[] }> = [
    { name: 'host', values: ['bedrock-runtime.us-east-1.amazonaws.com'] },
  ];
  if (agentId) headers.push({ name: 'x-agent-id', values: [agentId] });
  return {
    key,
    value: {
      httpRequest: {
        method: 'POST',
        path: '/model/anthropic.claude-3-5-haiku-20241022-v1:0/converse',
        headers,
        body: { type: 'JSON', json: JSON.stringify({ messages: [{ role: 'user', content: [{ text }] }] }) },
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            output: { message: { role: 'assistant', content: [{ text: 'Sure.' }] } },
            stopReason: 'end_turn',
            usage: { inputTokens: 9, outputTokens: 2, totalTokens: 11 },
          }),
        },
      },
    },
  };
}

function makeIsolatedExpectation(scenarioName: string): JsonListItem {
  return {
    key: `exp-${scenarioName}`,
    value: {
      // scenarioName lives at the top level of the expectation payload,
      // matching the real MockServer active-expectation shape.
      scenarioName,
      scenarioState: 'Started',
      newScenarioState: 'turn_1',
      httpLlmResponse: {
        provider: 'ANTHROPIC',
        model: 'claude-sonnet-4-20250514',
        conversationPredicates: { turnIndex: 0 },
        completion: { text: 'Hello!', stopReason: 'end_turn' },
      },
    },
  };
}

describe('SessionInspector', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      proxiedRequests: [],
      activeExpectations: [],
    });
  });

  it('renders empty state when no proxied LLM requests exist', () => {
    renderInspector();
    expect(screen.getByText('No LLM traffic captured yet')).toBeInTheDocument();
    expect(screen.getByText(/proxy through MockServer/)).toBeInTheDocument();
  });

  it('renders sessions when LLM traffic with isolation is present', () => {
    useDashboardStore.setState({
      proxiedRequests: [
        makeAnthropicRequest('req-1', 'agent-A'),
        makeAnthropicRequest('req-2', 'agent-B'),
        makeAnthropicRequest('req-3', 'agent-A'),
      ],
      activeExpectations: [
        makeIsolatedExpectation('__llm_conv_chat__iso=header:x-agent-id'),
      ],
    });

    renderInspector();

    // Should show "Active traces: 2"
    expect(screen.getByText('Active traces: 2')).toBeInTheDocument();

    // Session lanes should be visible
    expect(screen.getByText(/chat \/ agent-A/)).toBeInTheDocument();
    expect(screen.getByText(/chat \/ agent-B/)).toBeInTheDocument();
  });

  it('expanding a chip reveals request detail', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        makeAnthropicRequest('req-1', 'agent-A'),
      ],
      activeExpectations: [
        makeIsolatedExpectation('__llm_conv_chat__iso=header:x-agent-id'),
      ],
    });

    renderInspector();

    // Find the request chip and click it
    const chip = screen.getByText(/\[0\] POST \/v1\/messages/);
    await user.click(chip);

    // After expanding, should see conversation content. The AnthropicConversationView
    // shows user messages as bubbles. Check for the content.
    expect(screen.getByText('Hello')).toBeInTheDocument();
  });

  it('search box filters sessions', async () => {
    const user = userEvent.setup();

    useDashboardStore.setState({
      proxiedRequests: [
        makeAnthropicRequest('req-1', 'agent-A'),
        makeAnthropicRequest('req-2', 'agent-B'),
      ],
      activeExpectations: [
        makeIsolatedExpectation('__llm_conv_chat__iso=header:x-agent-id'),
      ],
    });

    renderInspector();

    // Both sessions visible initially
    expect(screen.getByText(/chat \/ agent-A/)).toBeInTheDocument();
    expect(screen.getByText(/chat \/ agent-B/)).toBeInTheDocument();

    // Type in the search box
    const searchInput = screen.getByPlaceholderText('Filter traces...');
    await user.type(searchInput, 'agent-A');

    // Only agent-A session should remain
    expect(screen.getByText(/chat \/ agent-A/)).toBeInTheDocument();
    expect(screen.queryByText(/chat \/ agent-B/)).not.toBeInTheDocument();
  });

  it('exposes a Compare tab that switches to the trace comparison view', async () => {
    const user = userEvent.setup();
    renderInspector();

    // The Trace page now has exactly two tabs: Traces and Compare.
    // (Scenarios moved to the Mocks page.)
    const tabs = screen.getAllByRole('tab');
    expect(tabs).toHaveLength(2);
    expect(tabs[0]).toHaveTextContent('Traces');
    expect(tabs[1]).toHaveTextContent('Compare');
    expect(screen.queryByRole('tab', { name: 'Scenarios' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Compare' }));

    // The Compare (trace comparison) view exposes Trace A / Trace B selectors.
    expect(screen.getByLabelText('Trace A')).toBeInTheDocument();
    expect(screen.getByLabelText('Trace B')).toBeInTheDocument();
  });

  it('renders unscoped session with upstream host for proxy traffic without isolation', () => {
    useDashboardStore.setState({
      proxiedRequests: [
        makeAnthropicRequest('req-1'), // no agent id header, but has host: api.anthropic.com
      ],
      activeExpectations: [
        makeIsolatedExpectation('__llm_conv_chat__iso=header:x-agent-id'),
      ],
    });

    renderInspector();

    // The host header provides the upstream host for unscoped proxy traffic
    expect(screen.getByText('Unscoped requests (api.anthropic.com)')).toBeInTheDocument();
  });

  // Vertex AI bills Gemini 2.0 Flash at 0.15 / 0.60 USD per million input / output tokens, the
  // Gemini API at 0.10 / 0.40 (cloud.google.com/vertex-ai/generative-ai/pricing, ai.google.dev/gemini-api/docs/pricing)
  function makeGeminiFlashRequest(key: string, host: string, path: string): JsonListItem {
    return {
      key,
      value: {
        httpRequest: {
          method: 'POST',
          path,
          headers: [{ name: 'host', values: [host] }],
          body: { type: 'JSON', json: '{"contents":[{"role":"user","parts":[{"text":"Hi"}]}]}' },
        },
        httpResponse: {
          statusCode: 200,
          body: {
            type: 'JSON',
            json: JSON.stringify({
              candidates: [{ content: { role: 'model', parts: [{ text: 'Hello' }] }, finishReason: 'STOP' }],
              usageMetadata: { promptTokenCount: 100_000, candidatesTokenCount: 100_000, totalTokenCount: 200_000 },
              modelVersion: 'gemini-2.0-flash-001',
            }),
          },
        },
      },
    };
  }

  it('totals a session of native Gemini calls to a Vertex AI host at Vertex AI rates', () => {
    useDashboardStore.setState({
      proxiedRequests: [
        makeGeminiFlashRequest('req-v', 'us-central1-aiplatform.googleapis.com',
          '/v1/projects/my-project/locations/us-central1/publishers/google/models/gemini-2.0-flash-001:generateContent'),
      ],
      activeExpectations: [],
    });

    renderInspector();

    expect(screen.getByText('$0.0750')).toBeInTheDocument();
  });

  it('totals a session of native Gemini API calls at Gemini API rates', () => {
    useDashboardStore.setState({
      proxiedRequests: [
        makeGeminiFlashRequest('req-g', 'generativelanguage.googleapis.com', '/v1beta/models/gemini-2.0-flash-001:generateContent'),
      ],
      activeExpectations: [],
    });

    renderInspector();

    expect(screen.getByText('$0.0500')).toBeInTheDocument();
  });

  it('renders plain "Unscoped requests" when no host header is present', () => {
    // Build a request with no host header so isolationKey falls back to <unscoped>
    const noHostRequest: JsonListItem = {
      key: 'req-no-host',
      value: {
        httpRequest: {
          method: 'POST',
          path: '/v1/messages',
          headers: [], // no host header
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
              model: 'claude-sonnet-4-20250514',
              content: [{ type: 'text', text: 'Hi!' }],
              usage: { input_tokens: 10, output_tokens: 5 },
              stop_reason: 'end_turn',
            }),
          },
        },
      },
    };

    useDashboardStore.setState({
      proxiedRequests: [noHostRequest],
      activeExpectations: [],
    });

    renderInspector();

    // With no host header, isolationKey is the <unscoped> sentinel,
    // so the label should be plain "Unscoped requests" without a parenthetical.
    expect(screen.getByText('Unscoped requests')).toBeInTheDocument();
  });

  it('flags a mixed unscoped lane and hides the agent-run graph', async () => {
    const user = userEvent.setup();
    // Two unrelated providers sharing a host → one <unscoped> lane with a mix.
    const anthropic: JsonListItem = {
      key: 'a1',
      value: {
        httpRequest: {
          method: 'POST',
          path: '/v1/messages',
          headers: [{ name: 'host', values: ['localhost:1080'] }],
          body: {
            type: 'JSON',
            json: JSON.stringify({
              model: 'claude-sonnet-4-20250514',
              messages: [{ role: 'user', content: 'Hi' }],
            }),
          },
        },
        httpResponse: {
          statusCode: 200,
          body: {
            type: 'JSON',
            json: JSON.stringify({
              model: 'claude-sonnet-4-20250514',
              content: [{ type: 'text', text: 'Hello' }],
              usage: { input_tokens: 10, output_tokens: 5 },
              stop_reason: 'end_turn',
            }),
          },
        },
      },
    };
    useDashboardStore.setState({
      proxiedRequests: [anthropic, makeOpenAiRequest('o1', 'localhost:1080')],
      activeExpectations: [],
    });

    renderInspector();

    // No correlated agent-run graph for the heterogeneous catch-all.
    expect(screen.queryByText('Show graph')).not.toBeInTheDocument();
    // Two different providers cannot be a growing prefix of one another, so the
    // grouping yields two separate conversation threads.
    const convBtn = screen.getByText(/Conversations \(2\)/);
    await user.click(convBtn);
    expect(
      screen.getByText(/2 separate conversation threads/),
    ).toBeInTheDocument();
  });

  it('shows the agent-run graph for a scoped single-provider session', () => {
    useDashboardStore.setState({
      proxiedRequests: [makeAnthropicRequest('r1', 'agent-A')],
      activeExpectations: [makeIsolatedExpectation('__llm_conv_chat__iso=header:x-agent-id')],
    });

    renderInspector();

    expect(screen.getByText('Show graph')).toBeInTheDocument();
    // A single-provider scoped lane uses the plain "Conversation" label (no "latest of N").
    expect(screen.getByText('Conversation')).toBeInTheDocument();
    expect(screen.queryByText(/latest of/)).not.toBeInTheDocument();
  });

  it('renders two unrelated same-provider requests as separate threads', async () => {
    const user = userEvent.setup();
    // Two unrelated Anthropic requests (different content), same host, no isolation.
    // Neither is a prefix of the other, so they do NOT collapse into one thread.
    const r1 = makeAnthropicRequest('u1', undefined, [{ role: 'user', content: 'First question' }]);
    const r2 = makeAnthropicRequest('u2', undefined, [{ role: 'user', content: 'Totally separate' }]);
    useDashboardStore.setState({ proxiedRequests: [r1, r2], activeExpectations: [] });

    renderInspector();

    const convBtn = screen.getByText(/Conversations \(2\)/);
    await user.click(convBtn);
    expect(screen.getByText(/2 separate conversation threads/)).toBeInTheDocument();
  });

  it('labels Bedrock threads Bedrock, not Anthropic', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      proxiedRequests: [makeBedrockConverseRequest('b1', 'First question'), makeBedrockConverseRequest('b2', 'Totally separate')],
      activeExpectations: [],
    });

    renderInspector();

    await user.click(screen.getByText(/Conversations \(2\)/));
    expect(screen.getByText('Bedrock thread 1')).toBeInTheDocument();
    expect(screen.getByText('Bedrock thread 2')).toBeInTheDocument();
    expect(screen.queryByText(/Anthropic thread/)).not.toBeInTheDocument();
  });

  it('asks for the call graph of a Bedrock session with the BEDROCK provider', async () => {
    const user = userEvent.setup();
    const toolCalls: Array<Record<string, unknown>> = [];
    vi.stubGlobal('fetch', vi.fn(async (_input: unknown, init?: RequestInit) => {
      const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : {};
      if (body['method'] === 'initialize') {
        return new Response('{}', { status: 200, headers: { 'Mcp-Session-Id': 'bedrock-graph-session' } });
      }
      if (body['method'] === 'tools/call') toolCalls.push(body['params'] as Record<string, unknown>);
      return new Response(JSON.stringify({ jsonrpc: '2.0', id: 1, error: { code: -32000, message: 'none' } }), { status: 200 });
    }));
    try {
      useDashboardStore.setState({
        proxiedRequests: [makeBedrockConverseRequest('bg1', 'Plan a trip', 'agent-B')],
        activeExpectations: [makeIsolatedExpectation('__llm_conv_chat__iso=header:x-agent-id')],
      });

      renderInspector();

      await user.click(screen.getByText('Show graph'));
      await waitFor(() => expect(toolCalls).toHaveLength(1));
      expect(toolCalls[0]!['name']).toBe('explain_agent_run');
      expect((toolCalls[0]!['arguments'] as Record<string, unknown>)['provider']).toBe('BEDROCK');
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('collapses a growing multi-turn conversation into one thread of turn deltas', async () => {
    const user = userEvent.setup();
    // A stateless CLI resends the whole history each turn — the message list grows
    // as a prefix. Three such requests collapse into one growing thread.
    const turn0 = makeAnthropicRequest('g0', 'agent-A', [
      { role: 'user', content: 'turn-zero-question' },
    ]);
    const turn1 = makeAnthropicRequest('g1', 'agent-A', [
      { role: 'user', content: 'turn-zero-question' },
      { role: 'assistant', content: 'reply-zero' },
      { role: 'user', content: 'turn-one-followup' },
    ]);
    const turn2 = makeAnthropicRequest('g2', 'agent-A', [
      { role: 'user', content: 'turn-zero-question' },
      { role: 'assistant', content: 'reply-zero' },
      { role: 'user', content: 'turn-one-followup' },
      { role: 'assistant', content: 'reply-one' },
      { role: 'user', content: 'turn-two-followup' },
    ]);
    useDashboardStore.setState({
      proxiedRequests: [turn0, turn1, turn2],
      activeExpectations: [makeIsolatedExpectation('__llm_conv_chat__iso=header:x-agent-id')],
    });

    renderInspector();

    // One growing thread, labelled with its turn count (not three full transcripts).
    const convBtn = screen.getByText(/Conversation \(3 turns\)/);
    await user.click(convBtn);

    // The grouped growing-thread view is rendered (not a single flat transcript).
    expect(screen.getByTestId('grouped-conversation')).toBeInTheDocument();
    // Each turn's NEW content is shown exactly once — the later follow-ups appear,
    // and the initial question is not repeated for every turn.
    expect(screen.getByText('turn-one-followup')).toBeInTheDocument();
    expect(screen.getByText('turn-two-followup')).toBeInTheDocument();
    expect(screen.getAllByText('turn-zero-question')).toHaveLength(1);
  });
});

describe('SessionInspector with a body too large to load by itself', () => {
  const logEntryId = 'huge-openai-entry';

  // The server sends only the first 64 KiB of a long body, as a plain string, and marks the row.
  function shortenedOpenAiRequest(): { row: JsonListItem; fullRequest: Record<string, unknown> } {
    const whole = makeOpenAiRequest('huge-1');
    const fullRequest = whole.value.httpRequest as Record<string, unknown>;
    const prefix = '{"model":"gpt-4o","messages":[{"role":"user","content":"Where is my order? ' + 'x'.repeat(200);
    return {
      fullRequest,
      row: {
        ...whole,
        value: { ...whole.value, httpRequest: { ...fullRequest, body: prefix } },
        truncatedBodies: {
          httpRequest: { logEntryId, part: 'request', originalLength: AUTO_LOAD_MAX_CHARACTERS + 1, shownLength: 65536 },
        },
      },
    };
  }

  function serveFullRequest(fullRequest: Record<string, unknown>) {
    const fetchMock = vi.fn(async (input: unknown) => {
      const url = String(input);
      if (url.includes(`/mockserver/logEntryBody?id=${logEntryId}&part=request`)) {
        return { ok: true, status: 200, json: async () => ({ httpRequest: fullRequest }) };
      }
      return { ok: false, status: 404, statusText: 'Not Found', json: async () => ({}) };
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  beforeEach(() => {
    useDashboardStore.setState({ proxiedRequests: [], recordedRequests: [], activeExpectations: [], fullMessages: {} });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the shortened-body notice instead of a conversation for the request, until the body is loaded', async () => {
    const user = userEvent.setup();
    const { row, fullRequest } = shortenedOpenAiRequest();
    const fetchMock = serveFullRequest(fullRequest);
    useDashboardStore.setState({ proxiedRequests: [row] });

    renderInspector();
    await user.click(screen.getByText(/\[0\] POST \/v1\/chat\/completions/));

    const detail = screen.getByTestId('trace-request-shortened');
    expect(within(detail).getByTestId('truncated-body-notice')).toHaveTextContent('Body shortened: showing the first 64 KiB of 4.0 MiB');
    expect(within(detail).getByText(/conversation is shown once the full body is loaded/)).toBeInTheDocument();
    // The whole response would otherwise render as a complete conversation with no prompt.
    expect(screen.queryByText('Checking now.')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await user.click(within(detail).getByTestId('load-full-body'));

    await waitFor(() => expect(screen.getByText('Where is my order?')).toBeInTheDocument());
    expect(screen.getByText('Checking now.')).toBeInTheDocument();
    expect(screen.queryByTestId('truncated-body-notice')).not.toBeInTheDocument();
  });

  it('leaves a shortened request out of the trace conversation and offers to load it', async () => {
    const user = userEvent.setup();
    const { row, fullRequest } = shortenedOpenAiRequest();
    serveFullRequest(fullRequest);
    useDashboardStore.setState({ proxiedRequests: [row] });

    renderInspector();
    await user.click(screen.getByRole('button', { name: 'Conversation' }));

    const shortened = screen.getByTestId('trace-conversation-shortened');
    expect(shortened).toHaveTextContent('[0] POST /v1/chat/completions is left out of the conversation until its full body is loaded.');
    expect(within(shortened).getByTestId('truncated-body-notice')).toBeInTheDocument();
    expect(screen.queryByText('Checking now.')).not.toBeInTheDocument();

    await user.click(within(shortened).getByTestId('load-full-body'));

    await waitFor(() => expect(screen.getByText('Where is my order?')).toBeInTheDocument());
    expect(screen.getByText('Checking now.')).toBeInTheDocument();
    expect(screen.queryByTestId('trace-conversation-shortened')).not.toBeInTheDocument();
  });
});
