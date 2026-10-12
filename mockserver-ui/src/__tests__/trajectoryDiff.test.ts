import { describe, it, expect } from 'vitest';
import {
  extractTrajectory,
  compareTrajectories,
  type TrajectorySkeleton,
} from '../lib/trajectoryDiff';
import type { Session, SessionRequest } from '../lib/sessionGrouping';
import type { AnthropicParsed, OpenAiParsed, GenericParsed } from '../lib/llmTraffic';
import type { JsonListItem } from '../types';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeItem(key: string): JsonListItem {
  return { key, value: {} };
}

function makeAnthropicRequest(overrides: Partial<{
  toolCalls: Array<{ name: string; id?: string }>;
  stopReason: string | null;
  statusCode: number;
  inputTokens: number;
  outputTokens: number;
}> = {}): SessionRequest {
  const {
    toolCalls = [],
    stopReason = 'end_turn',
    statusCode = 200,
    inputTokens = 100,
    outputTokens = 50,
  } = overrides;

  const responseContent = toolCalls.length > 0
    ? toolCalls.map((tc) => ({
        type: 'tool_use' as const,
        name: tc.name,
        id: tc.id ?? `tool_${tc.name}`,
        input: {},
      }))
    : [{ type: 'text' as const, text: 'response' }];

  const parsed: AnthropicParsed = {
    kind: 'anthropic',
    model: 'claude-sonnet-4-20250514',
    stream: false,
    messages: [],
    system: null,
    tools: null,
    maxTokens: null,
    responseContent,
    usage: { input_tokens: inputTokens, output_tokens: outputTokens },
    stopReason,
    sseEvents: null,
    streamed: false,
    streamTruncated: false,
  };

  return {
    item: makeItem(`req-${Math.random()}`),
    parsed,
    path: '/v1/messages',
    method: 'POST',
    statusCode,
    timestamp: 0,
  };
}

function makeOpenAiRequest(overrides: Partial<{
  toolCalls: Array<{ name: string; id?: string }>;
  finishReason: string | null;
  statusCode: number;
  promptTokens: number;
  completionTokens: number;
}> = {}): SessionRequest {
  const {
    toolCalls = [],
    finishReason = 'stop',
    statusCode = 200,
    promptTokens = 100,
    completionTokens = 50,
  } = overrides;

  const message: Record<string, unknown> = {
    role: 'assistant',
    content: 'response',
  };

  if (toolCalls.length > 0) {
    message['tool_calls'] = toolCalls.map((tc) => ({
      id: tc.id ?? `call_${tc.name}`,
      type: 'function',
      function: { name: tc.name, arguments: '{}' },
    }));
  }

  const parsed: OpenAiParsed = {
    kind: 'openai',
    model: 'gpt-4o',
    stream: false,
    messages: [],
    tools: null,
    choices: [{ message: message as OpenAiParsed['choices'][0]['message'], finish_reason: finishReason }],
    usage: { prompt_tokens: promptTokens, completion_tokens: completionTokens },
    sseEvents: null,
    streamed: false,
    streamTruncated: false,
  };

  return {
    item: makeItem(`req-${Math.random()}`),
    parsed,
    path: '/chat/completions',
    method: 'POST',
    statusCode,
    timestamp: 0,
  };
}

function makeErrorRequest(statusCode: number): SessionRequest {
  const parsed: GenericParsed = {
    kind: 'generic',
    method: 'POST',
    path: '/v1/messages',
    statusCode,
  };

  return {
    item: makeItem(`req-${Math.random()}`),
    parsed,
    path: '/v1/messages',
    method: 'POST',
    statusCode,
    timestamp: 0,
  };
}

function makeSession(requests: SessionRequest[]): Session {
  return {
    scenarioName: 'test-scenario',
    isolationKey: 'test-key',
    requests,
  };
}

// ---------------------------------------------------------------------------
// extractTrajectory tests
// ---------------------------------------------------------------------------

describe('extractTrajectory', () => {
  it('extracts tool call names from Anthropic responses', () => {
    const session = makeSession([
      makeAnthropicRequest({ toolCalls: [{ name: 'search' }] }),
      makeAnthropicRequest({ toolCalls: [{ name: 'calculate' }] }),
      makeAnthropicRequest(),
    ]);

    const traj = extractTrajectory(session);
    expect(traj.turns).toHaveLength(3);
    expect(traj.turns[0]?.toolCalls).toEqual(['search']);
    expect(traj.turns[1]?.toolCalls).toEqual(['calculate']);
    expect(traj.turns[2]?.toolCalls).toEqual([]);
  });

  it('extracts tool call names from OpenAI responses', () => {
    const session = makeSession([
      makeOpenAiRequest({ toolCalls: [{ name: 'web_search' }, { name: 'read_file' }] }),
    ]);

    const traj = extractTrajectory(session);
    expect(traj.turns).toHaveLength(1);
    expect(traj.turns[0]?.toolCalls).toEqual(['web_search', 'read_file']);
  });

  it('collects error codes', () => {
    const session = makeSession([
      makeAnthropicRequest(),
      makeErrorRequest(429),
      makeErrorRequest(500),
      makeAnthropicRequest(),
    ]);

    const traj = extractTrajectory(session);
    expect(traj.errorCodes).toEqual([429, 500]);
  });

  it('extracts token usage from Anthropic', () => {
    const session = makeSession([
      makeAnthropicRequest({ inputTokens: 100, outputTokens: 50 }),
    ]);

    const traj = extractTrajectory(session);
    expect(traj.turns[0]?.inputTokens).toBe(100);
    expect(traj.turns[0]?.outputTokens).toBe(50);
  });

  it('extracts token usage from OpenAI', () => {
    const session = makeSession([
      makeOpenAiRequest({ promptTokens: 200, completionTokens: 75 }),
    ]);

    const traj = extractTrajectory(session);
    expect(traj.turns[0]?.inputTokens).toBe(200);
    expect(traj.turns[0]?.outputTokens).toBe(75);
  });
});

// ---------------------------------------------------------------------------
// compareTrajectories tests
// ---------------------------------------------------------------------------

describe('compareTrajectories', () => {
  it('reports identical for two runs with the same structure', () => {
    const a: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 100, outputTokens: 50 },
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 150, outputTokens: 80 },
      ],
      errorCodes: [],
    };
    const b: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 110, outputTokens: 55 },
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 160, outputTokens: 90 },
      ],
      errorCodes: [],
    };

    const report = compareTrajectories(a, b);
    expect(report.verdict).toBe('identical');
    expect(report.firstDivergence).toBeUndefined();
    expect(report.turnCountA).toBe(2);
    expect(report.turnCountB).toBe(2);
  });

  it('reports different-length when turn counts differ', () => {
    const a: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 100, outputTokens: 50 },
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 150, outputTokens: 80 },
      ],
      errorCodes: [],
    };
    const b: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 100, outputTokens: 50 },
      ],
      errorCodes: [],
    };

    const report = compareTrajectories(a, b);
    expect(report.verdict).toBe('different-length');
    expect(report.firstDivergence?.turn).toBe(1);
    expect(report.turnCountA).toBe(2);
    expect(report.turnCountB).toBe(1);
  });

  it('reports divergent at turn N when tool sequences differ', () => {
    const a: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 100, outputTokens: 50 },
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 150, outputTokens: 80 },
      ],
      errorCodes: [],
    };
    const b: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 100, outputTokens: 50 },
        { toolCalls: ['calculate'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 150, outputTokens: 80 },
      ],
      errorCodes: [],
    };

    const report = compareTrajectories(a, b);
    expect(report.verdict).toBe('divergent');
    expect(report.firstDivergence).toBeDefined();
    expect(report.firstDivergence?.turn).toBe(1);
    expect(report.firstDivergence?.kind).toBe('tool');
    expect(report.firstDivergence?.a).toBe('search');
    expect(report.firstDivergence?.b).toBe('calculate');
  });

  it('reports divergent when stop reasons differ', () => {
    const a: TrajectorySkeleton = {
      turns: [
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 100, outputTokens: 50 },
      ],
      errorCodes: [],
    };
    const b: TrajectorySkeleton = {
      turns: [
        { toolCalls: [], hasToolResults: [], stopReason: 'max_tokens', statusCode: 200, inputTokens: 100, outputTokens: 50 },
      ],
      errorCodes: [],
    };

    const report = compareTrajectories(a, b);
    expect(report.verdict).toBe('divergent');
    expect(report.firstDivergence?.kind).toBe('stopReason');
    expect(report.firstDivergence?.a).toBe('end_turn');
    expect(report.firstDivergence?.b).toBe('max_tokens');
  });

  it('reports divergent when error signatures differ', () => {
    const a: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 100, outputTokens: 50 },
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 429, inputTokens: 0, outputTokens: 0 },
      ],
      errorCodes: [429],
    };
    const b: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 100, outputTokens: 50 },
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 150, outputTokens: 80 },
      ],
      errorCodes: [],
    };

    const report = compareTrajectories(a, b);
    expect(report.verdict).toBe('divergent');
    expect(report.firstDivergence?.kind).toBe('error');
    expect(report.firstDivergence?.turn).toBe(1);
    expect(report.firstDivergence?.a).toBe('429');
    expect(report.firstDivergence?.b).toBe('200');
  });

  it('does NOT flag divergence for different token counts alone', () => {
    const a: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 100, outputTokens: 50 },
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 150, outputTokens: 80 },
      ],
      errorCodes: [],
    };
    const b: TrajectorySkeleton = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 999, outputTokens: 888 },
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 777, outputTokens: 666 },
      ],
      errorCodes: [],
    };

    const report = compareTrajectories(a, b);
    expect(report.verdict).toBe('identical');
    // But token trajectory should still report the different values
    expect(report.tokenTrajectory).toHaveLength(2);
    expect(report.tokenTrajectory[0]?.aInput).toBe(100);
    expect(report.tokenTrajectory[0]?.bInput).toBe(999);
    expect(report.tokenTrajectory[1]?.aOutput).toBe(80);
    expect(report.tokenTrajectory[1]?.bOutput).toBe(666);
  });

  it('builds token trajectory for different-length runs', () => {
    const a: TrajectorySkeleton = {
      turns: [
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 100, outputTokens: 50 },
      ],
      errorCodes: [],
    };
    const b: TrajectorySkeleton = {
      turns: [
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 100, outputTokens: 50 },
        { toolCalls: [], hasToolResults: [], stopReason: 'end_turn', statusCode: 200, inputTokens: 200, outputTokens: 75 },
      ],
      errorCodes: [],
    };

    const report = compareTrajectories(a, b);
    expect(report.tokenTrajectory).toHaveLength(2);
    // Turn 1 should have null for run A
    expect(report.tokenTrajectory[1]?.aInput).toBeNull();
    expect(report.tokenTrajectory[1]?.aOutput).toBeNull();
    expect(report.tokenTrajectory[1]?.bInput).toBe(200);
    expect(report.tokenTrajectory[1]?.bOutput).toBe(75);
  });

  it('handles empty trajectories', () => {
    const a: TrajectorySkeleton = { turns: [], errorCodes: [] };
    const b: TrajectorySkeleton = { turns: [], errorCodes: [] };

    const report = compareTrajectories(a, b);
    expect(report.verdict).toBe('identical');
    expect(report.tokenTrajectory).toEqual([]);
  });

  it('detects toolResult divergence — same call, different result presence', () => {
    // Both runs called `search` at turn 0 but run A got a result back and run
    // B did not (typical failure mode: tool returned an error / agent crashed).
    const a = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [true], stopReason: 'tool_use', statusCode: 200, inputTokens: 10, outputTokens: 5 },
      ],
      errorCodes: [],
    };
    const b = {
      turns: [
        { toolCalls: ['search'], hasToolResults: [false], stopReason: 'tool_use', statusCode: 200, inputTokens: 10, outputTokens: 5 },
      ],
      errorCodes: [],
    };

    const report = compareTrajectories(a, b);

    expect(report.verdict).toBe('divergent');
    expect(report.firstDivergence?.kind).toBe('toolResult');
    expect(report.firstDivergence?.turn).toBe(0);
  });

  it('does not regress comma-named tool false positives', () => {
    // Defensive: ensure element-wise compare doesn't conflate
    // [tool "a,b"] with [tool "a", tool "b"] via join(',') collision.
    const a = {
      turns: [{ toolCalls: ['a,b'], hasToolResults: [false], stopReason: 'tool_use', statusCode: 200, inputTokens: 0, outputTokens: 0 }],
      errorCodes: [],
    };
    const b = {
      turns: [{ toolCalls: ['a', 'b'], hasToolResults: [false, false], stopReason: 'tool_use', statusCode: 200, inputTokens: 0, outputTokens: 0 }],
      errorCodes: [],
    };

    expect(compareTrajectories(a, b).verdict).toBe('divergent');
  });
});

// ---------------------------------------------------------------------------
// Integration: extractTrajectory -> compareTrajectories
// ---------------------------------------------------------------------------

describe('extractTrajectory + compareTrajectories integration', () => {
  it('identifies identical runs end-to-end', () => {
    const sessionA = makeSession([
      makeAnthropicRequest({ toolCalls: [{ name: 'search' }], stopReason: 'tool_use', inputTokens: 100, outputTokens: 50 }),
      makeAnthropicRequest({ stopReason: 'end_turn', inputTokens: 200, outputTokens: 100 }),
    ]);
    const sessionB = makeSession([
      makeAnthropicRequest({ toolCalls: [{ name: 'search' }], stopReason: 'tool_use', inputTokens: 110, outputTokens: 55 }),
      makeAnthropicRequest({ stopReason: 'end_turn', inputTokens: 210, outputTokens: 105 }),
    ]);

    const trajA = extractTrajectory(sessionA);
    const trajB = extractTrajectory(sessionB);
    const report = compareTrajectories(trajA, trajB);

    expect(report.verdict).toBe('identical');
  });

  it('identifies divergent runs end-to-end', () => {
    const sessionA = makeSession([
      makeAnthropicRequest({ toolCalls: [{ name: 'search' }], stopReason: 'tool_use' }),
      makeAnthropicRequest({ toolCalls: [{ name: 'search' }], stopReason: 'tool_use' }),
      makeAnthropicRequest({ stopReason: 'end_turn' }),
    ]);
    const sessionB = makeSession([
      makeAnthropicRequest({ toolCalls: [{ name: 'search' }], stopReason: 'tool_use' }),
      makeAnthropicRequest({ toolCalls: [{ name: 'calculate' }], stopReason: 'tool_use' }),
      makeAnthropicRequest({ stopReason: 'end_turn' }),
    ]);

    const trajA = extractTrajectory(sessionA);
    const trajB = extractTrajectory(sessionB);
    const report = compareTrajectories(trajA, trajB);

    expect(report.verdict).toBe('divergent');
    expect(report.firstDivergence?.turn).toBe(1);
    expect(report.firstDivergence?.kind).toBe('tool');
  });

  it('identifies different-length runs end-to-end', () => {
    const sessionA = makeSession([
      makeAnthropicRequest({ toolCalls: [{ name: 'search' }], stopReason: 'tool_use' }),
      makeAnthropicRequest({ stopReason: 'end_turn' }),
    ]);
    const sessionB = makeSession([
      makeAnthropicRequest({ toolCalls: [{ name: 'search' }], stopReason: 'tool_use' }),
      makeAnthropicRequest({ toolCalls: [{ name: 'calculate' }], stopReason: 'tool_use' }),
      makeAnthropicRequest({ stopReason: 'end_turn' }),
    ]);

    const trajA = extractTrajectory(sessionA);
    const trajB = extractTrajectory(sessionB);
    const report = compareTrajectories(trajA, trajB);

    expect(report.verdict).toBe('different-length');
  });

  it('works with OpenAI requests', () => {
    const sessionA = makeSession([
      makeOpenAiRequest({ toolCalls: [{ name: 'web_search' }], finishReason: 'tool_calls' }),
      makeOpenAiRequest({ finishReason: 'stop' }),
    ]);
    const sessionB = makeSession([
      makeOpenAiRequest({ toolCalls: [{ name: 'web_search' }], finishReason: 'tool_calls' }),
      makeOpenAiRequest({ finishReason: 'stop' }),
    ]);

    const trajA = extractTrajectory(sessionA);
    const trajB = extractTrajectory(sessionB);
    const report = compareTrajectories(trajA, trajB);

    expect(report.verdict).toBe('identical');
  });
});

// ---------------------------------------------------------------------------
// Provider coverage for tool-call / token extraction (openai_responses, gemini, ollama)
// ---------------------------------------------------------------------------

function makeRequest(parsed: unknown, path: string): SessionRequest {
  return {
    item: makeItem(`req-${Math.random()}`),
    parsed: parsed as SessionRequest['parsed'],
    path,
    method: 'POST',
    statusCode: 200,
    timestamp: 0,
  };
}

describe('extractTrajectory — provider coverage', () => {
  it('extracts tool calls + tokens from OpenAI Responses (output / input_tokens)', () => {
    const parsed = {
      kind: 'openai_responses',
      output: [
        { type: 'function_call', name: 'search', call_id: 'fc_1', arguments: '{}' },
        { type: 'message', content: [{ type: 'output_text', text: 'hi' }] },
      ],
      usage: { input_tokens: 11, output_tokens: 7 },
    };
    const traj = extractTrajectory(makeSession([makeRequest(parsed, '/v1/responses')]));
    expect(traj.turns[0]!.toolCalls).toEqual(['search']);
    expect(traj.turns[0]!.hasToolResults).toEqual([true]);
    expect(traj.turns[0]!.inputTokens).toBe(11);
    expect(traj.turns[0]!.outputTokens).toBe(7);
  });

  it('extracts tool calls + stop reason + tokens from Gemini', () => {
    const parsed = {
      kind: 'gemini',
      candidates: [{
        content: { parts: [{ functionCall: { name: 'get_weather', args: {} } }] },
        finishReason: 'STOP',
      }],
      usage: { promptTokenCount: 20, candidatesTokenCount: 9 },
    };
    const traj = extractTrajectory(makeSession([makeRequest(parsed, '/v1beta/models/x:generateContent')]));
    expect(traj.turns[0]!.toolCalls).toEqual(['get_weather']);
    expect(traj.turns[0]!.stopReason).toBe('STOP');
    expect(traj.turns[0]!.inputTokens).toBe(20);
    expect(traj.turns[0]!.outputTokens).toBe(9);
  });

  it('counts Gemini thinking tokens as output (candidates + thoughts)', () => {
    const turn = (usage: Record<string, number>) =>
      extractTrajectory(makeSession([makeRequest({ kind: 'gemini', candidates: [], usage }, '/v1beta/models/x:generateContent')])).turns[0]!;
    expect(turn({ promptTokenCount: 20, candidatesTokenCount: 9, thoughtsTokenCount: 120 }).outputTokens).toBe(129);
    expect(turn({ promptTokenCount: 20, thoughtsTokenCount: 120 }).outputTokens).toBe(120);
    expect(turn({ promptTokenCount: 20 }).outputTokens).toBeNull();
  });

  it('extracts tool calls from Ollama (responseMessage.tool_calls)', () => {
    const parsed = {
      kind: 'ollama',
      responseMessage: { content: '', tool_calls: [{ function: { name: 'lookup', arguments: {} } }] },
      usage: { prompt_eval_count: 5, eval_count: 2 },
    };
    const traj = extractTrajectory(makeSession([makeRequest(parsed, '/api/chat')]));
    expect(traj.turns[0]!.toolCalls).toEqual(['lookup']);
    expect(traj.turns[0]!.inputTokens).toBe(5);
    expect(traj.turns[0]!.outputTokens).toBe(2);
  });
});
