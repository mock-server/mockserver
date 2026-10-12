import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import {
  AnthropicConversationView,
  GeminiConversationView,
  OpenAiConversationView,
  ScriptedTurnsPanel,
  responseLooksUnparseable,
} from '../components/ConversationView';
import type { ScriptedTurn } from '../components/ConversationView';
import type { AnthropicParsed, GeminiParsed, OpenAiParsed } from '../lib/llmTraffic';
import { useDashboardStore } from '../store';

function wrap(ui: React.ReactElement) {
  useDashboardStore.setState({ themeMode: 'dark' });
  return render(<ThemeProvider theme={buildTheme('dark')}>{ui}</ThemeProvider>);
}

function makeAnthropicParsed(overrides: Partial<AnthropicParsed> = {}): AnthropicParsed {
  return {
    kind: 'anthropic',
    model: 'claude-sonnet-4-20250514',
    stream: false,
    messages: [],
    system: null,
    tools: null,
    maxTokens: 1024,
    responseContent: [],
    usage: null,
    stopReason: null,
    sseEvents: null,
    streamed: false,
    streamTruncated: false,
    ...overrides,
  };
}

function makeOpenAiParsed(overrides: Partial<OpenAiParsed> = {}): OpenAiParsed {
  return {
    kind: 'openai',
    model: 'gpt-4',
    stream: false,
    messages: [],
    tools: null,
    choices: [],
    usage: null,
    sseEvents: null,
    streamed: false,
    streamTruncated: false,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Anthropic ConversationView
// ---------------------------------------------------------------------------

describe('AnthropicConversationView', () => {
  it('renders the model chip in the metadata strip', () => {
    wrap(<AnthropicConversationView parsed={makeAnthropicParsed()} />);
    expect(screen.getByText('claude-sonnet-4-20250514')).toBeInTheDocument();
  });

  it('renders token usage chips when usage is present', () => {
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({
          usage: { input_tokens: 100, output_tokens: 50 },
        })}
      />,
    );
    expect(screen.getByText('In: 100')).toBeInTheDocument();
    expect(screen.getByText('Out: 50')).toBeInTheDocument();
  });

  it('renders the system banner when system prompt is present', () => {
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({ system: 'You are a helpful assistant.' })}
      />,
    );
    expect(screen.getByText('System')).toBeInTheDocument();
    expect(screen.getByText('You are a helpful assistant.')).toBeInTheDocument();
  });

  it('renders user and assistant message bubbles', () => {
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({
          messages: [
            { role: 'user', content: 'Hello there' },
            { role: 'assistant', content: 'Hi, how can I help?' },
          ],
          responseContent: [{ type: 'text', text: 'Final response text' }],
        })}
      />,
    );
    expect(screen.getByText('Hello there')).toBeInTheDocument();
    expect(screen.getByText('Hi, how can I help?')).toBeInTheDocument();
    expect(screen.getByText('Final response text')).toBeInTheDocument();
  });

  it('renders tool_use blocks in the response', () => {
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({
          responseContent: [
            { type: 'tool_use', name: 'get_weather', input: { city: 'London' } },
          ],
        })}
      />,
    );
    expect(screen.getByText('get_weather')).toBeInTheDocument();
  });

  it('renders streamed and truncated chips', () => {
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({
          streamed: true,
          streamTruncated: true,
          usage: { input_tokens: 10, output_tokens: 5 },
        })}
      />,
    );
    expect(screen.getByText('Streamed')).toBeInTheDocument();
    expect(screen.getByText('Truncated')).toBeInTheDocument();
  });

  it('renders stop reason chip', () => {
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({
          stopReason: 'end_turn',
          usage: { input_tokens: 10, output_tokens: 5 },
        })}
      />,
    );
    expect(screen.getByText('Stop: end_turn')).toBeInTheDocument();
  });

  it('renders empty state when there are no messages or response', () => {
    wrap(<AnthropicConversationView parsed={makeAnthropicParsed()} />);
    expect(screen.getByText('No conversation content')).toBeInTheDocument();
  });

  it('renders an [image] placeholder for image content blocks', () => {
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({
          messages: [
            { role: 'user', content: [{ type: 'image', source: {} }] },
          ],
        })}
      />,
    );
    expect(screen.getByText('[image]')).toBeInTheDocument();
  });

  it('renders content that is an array of text blocks', () => {
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({
          messages: [
            { role: 'user', content: [{ type: 'text', text: 'Block one' }, { type: 'text', text: 'Block two' }] },
          ],
        })}
      />,
    );
    expect(screen.getByText('Block one')).toBeInTheDocument();
    expect(screen.getByText('Block two')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// OpenAI ConversationView
// ---------------------------------------------------------------------------

describe('OpenAiConversationView', () => {
  it('renders the model chip in the metadata strip', () => {
    wrap(<OpenAiConversationView parsed={makeOpenAiParsed()} />);
    expect(screen.getByText('gpt-4')).toBeInTheDocument();
  });

  it('renders token usage chips when usage is present', () => {
    wrap(
      <OpenAiConversationView
        parsed={makeOpenAiParsed({
          usage: { prompt_tokens: 20, completion_tokens: 10 },
        })}
      />,
    );
    expect(screen.getByText('In: 20')).toBeInTheDocument();
    expect(screen.getByText('Out: 10')).toBeInTheDocument();
  });

  it('renders system message as a banner', () => {
    wrap(
      <OpenAiConversationView
        parsed={makeOpenAiParsed({
          messages: [{ role: 'system', content: 'You are helpful.' }],
        })}
      />,
    );
    expect(screen.getByText('System')).toBeInTheDocument();
    expect(screen.getByText('You are helpful.')).toBeInTheDocument();
  });

  it('renders user and assistant message bubbles', () => {
    wrap(
      <OpenAiConversationView
        parsed={makeOpenAiParsed({
          messages: [
            { role: 'user', content: 'Hello' },
            { role: 'assistant', content: 'Hi!' },
          ],
          choices: [
            { message: { role: 'assistant', content: 'Final answer' }, finish_reason: 'stop' },
          ],
        })}
      />,
    );
    expect(screen.getByText('Hello')).toBeInTheDocument();
    expect(screen.getByText('Hi!')).toBeInTheDocument();
    expect(screen.getByText('Final answer')).toBeInTheDocument();
  });

  it('renders tool_calls in response choices', () => {
    wrap(
      <OpenAiConversationView
        parsed={makeOpenAiParsed({
          choices: [
            {
              message: {
                role: 'assistant',
                content: null,
                tool_calls: [
                  { id: 'call_1', type: 'function', function: { name: 'search', arguments: '{"q":"test"}' } },
                ],
              },
              finish_reason: 'tool_calls',
            },
          ],
        })}
      />,
    );
    expect(screen.getByText('search')).toBeInTheDocument();
  });

  it('renders finish reason as stop reason chip', () => {
    wrap(
      <OpenAiConversationView
        parsed={makeOpenAiParsed({
          choices: [{ message: { role: 'assistant', content: 'Done' }, finish_reason: 'stop' }],
        })}
      />,
    );
    expect(screen.getByText('Stop: stop')).toBeInTheDocument();
  });

  it('renders empty state when there are no messages or choices', () => {
    wrap(<OpenAiConversationView parsed={makeOpenAiParsed({ model: null })} />);
    expect(screen.getByText('No conversation content')).toBeInTheDocument();
  });

  it('renders streamed and truncated chips', () => {
    wrap(
      <OpenAiConversationView
        parsed={makeOpenAiParsed({
          streamed: true,
          streamTruncated: true,
          usage: { prompt_tokens: 5, completion_tokens: 3 },
        })}
      />,
    );
    expect(screen.getByText('Streamed')).toBeInTheDocument();
    expect(screen.getByText('Truncated')).toBeInTheDocument();
  });

  it('renders tool messages on the left side', () => {
    wrap(
      <OpenAiConversationView
        parsed={makeOpenAiParsed({
          messages: [
            { role: 'tool', content: 'Tool result content' },
          ],
        })}
      />,
    );
    expect(screen.getByText('tool')).toBeInTheDocument();
    expect(screen.getByText('Tool result content')).toBeInTheDocument();
  });

  it('renders tool_calls attached to request messages', () => {
    wrap(
      <OpenAiConversationView
        parsed={makeOpenAiParsed({
          messages: [
            {
              role: 'assistant',
              content: 'Let me search for that.',
              tool_calls: [
                { id: 'call_1', type: 'function', function: { name: 'web_search', arguments: '{"query":"test"}' } },
              ],
            },
          ],
        })}
      />,
    );
    expect(screen.getByText('Let me search for that.')).toBeInTheDocument();
    expect(screen.getByText('web_search')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// ScriptedTurnsPanel
// ---------------------------------------------------------------------------

describe('ScriptedTurnsPanel', () => {
  it('renders a two-turn scripted conversation', () => {
    const turns: ScriptedTurn[] = [
      {
        turnIndex: 0,
        predicates: { turnIndex: 0 },
        response: {
          toolCalls: [{ name: 'search', arguments: '{"query":"test"}' }],
          stopReason: 'tool_use',
        },
        scenarioState: 'Started',
        newScenarioState: 'turn_1',
      },
      {
        turnIndex: 1,
        predicates: { containsToolResultFor: 'search' },
        response: {
          text: 'The answer is 42.',
          stopReason: 'end_turn',
        },
        scenarioState: 'turn_1',
        newScenarioState: '__done',
      },
    ];

    wrap(<ScriptedTurnsPanel turns={turns} />);

    // Header
    expect(screen.getByText('Scripted Conversation Turns')).toBeInTheDocument();

    // Turn indices
    expect(screen.getByText('Turn 0')).toBeInTheDocument();
    expect(screen.getByText('Turn 1')).toBeInTheDocument();

    // State transitions
    expect(screen.getByText('Started')).toBeInTheDocument();
    expect(screen.getAllByText('turn_1').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('__done')).toBeInTheDocument();

    // Predicates
    expect(screen.getByText(/turnIndex: 0/)).toBeInTheDocument();
    expect(screen.getByText(/containsToolResultFor: search/)).toBeInTheDocument();

    // Responses
    expect(screen.getByText('search')).toBeInTheDocument();
    expect(screen.getByText('The answer is 42.')).toBeInTheDocument();
    expect(screen.getByText('Stop: tool_use')).toBeInTheDocument();
    expect(screen.getByText('Stop: end_turn')).toBeInTheDocument();
  });

  it('renders empty when no turns provided', () => {
    const { container } = wrap(<ScriptedTurnsPanel turns={[]} />);
    expect(container.textContent).toBe('');
  });
});

// ---------------------------------------------------------------------------
// Truncated / malformed non-stream response notice
// ---------------------------------------------------------------------------

describe('responseLooksUnparseable', () => {
  it('returns false for empty / absent bodies', () => {
    expect(responseLooksUnparseable(undefined)).toBe(false);
    expect(responseLooksUnparseable(null)).toBe(false);
    expect(responseLooksUnparseable('')).toBe(false);
    expect(responseLooksUnparseable('   ')).toBe(false);
  });

  it('returns false for a body that parses cleanly as JSON', () => {
    expect(responseLooksUnparseable('{"content":[{"type":"text","text":"hi"}]}')).toBe(false);
  });

  it('returns true for a truncated / malformed JSON body', () => {
    expect(responseLooksUnparseable('{"content":[{"type":"text","text":"hi')).toBe(true);
  });
});

describe('ConversationView — truncated non-stream response warning', () => {
  it('shows a warning chip and reveals the raw body for an unparseable response', async () => {
    const user = userEvent.setup();
    const rawResponseBody = '{"content":[{"type":"text","text":"partial respon';
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({ messages: [{ role: 'user', content: 'Hello' }] })}
        rawResponseBody={rawResponseBody}
      />,
    );

    expect(screen.getByTestId('truncated-response-notice')).toBeInTheDocument();
    expect(screen.getByText(/could not be parsed/i)).toBeInTheDocument();
    // The "No conversation content" empty-state must NOT show — there IS content.
    expect(screen.queryByText('No conversation content')).not.toBeInTheDocument();

    // The raw body is revealed on demand.
    expect(screen.queryByText(rawResponseBody)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /show raw body/i }));
    expect(screen.getByText(rawResponseBody)).toBeInTheDocument();
  });

  it('does not show the warning when the response parsed cleanly', () => {
    wrap(
      <AnthropicConversationView
        parsed={makeAnthropicParsed({ responseContent: [{ type: 'text', text: 'done' }] })}
        rawResponseBody={'{"content":[{"type":"text","text":"done"}]}'}
      />,
    );
    expect(screen.queryByTestId('truncated-response-notice')).not.toBeInTheDocument();
  });
});

describe('GeminiConversationView token strip', () => {
  function makeGeminiParsed(usage: GeminiParsed['usage']): GeminiParsed {
    return {
      kind: 'gemini',
      model: 'gemini-2.5-pro',
      stream: false,
      contents: [{ role: 'user', parts: [{ text: 'Why is the sky blue?' }] }],
      tools: null,
      candidates: [{ content: { role: 'model', parts: [{ text: 'Rayleigh scattering.' }] }, finishReason: 'STOP' }],
      usage,
      sseEvents: null,
      streamed: false,
      streamTruncated: false,
    };
  }

  it('shows candidates plus thinking tokens as the output count', () => {
    wrap(<GeminiConversationView parsed={makeGeminiParsed({ promptTokenCount: 12, candidatesTokenCount: 40, thoughtsTokenCount: 300 })} />);
    expect(screen.getByText('In: 12')).toBeInTheDocument();
    expect(screen.getByText('Out: 340')).toBeInTheDocument();
  });

  it('shows only the candidates count when the reply has no thinking', () => {
    wrap(<GeminiConversationView parsed={makeGeminiParsed({ promptTokenCount: 12, candidatesTokenCount: 40 })} />);
    expect(screen.getByText('Out: 40')).toBeInTheDocument();
  });
});
