/**
 * Structural trajectory comparison for captured agent runs.
 *
 * Compares two sessions along their structural skeleton ONLY:
 * - Tool call sequence (tool name only, not arguments)
 * - Turn count
 * - Final stop_reason
 * - Error / retry signatures (any 4xx/5xx in the trace)
 * - Token usage trajectory (input + output per turn)
 * - Presence of tool results (boolean per tool call, not content)
 *
 * NO free-text comparison is performed.
 */

import type { Session } from './sessionGrouping';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface TurnSkeleton {
  /** Tool names called in this turn, empty array if no tool calls */
  toolCalls: string[];
  /** Whether each tool call has a result (aligned with toolCalls) */
  hasToolResults: boolean[];
  /** The stop/finish reason for this turn, null if not available */
  stopReason: string | null;
  /** HTTP status code of the response, null if not available */
  statusCode: number | null;
  /** Input token count, null if unavailable */
  inputTokens: number | null;
  /** Output token count, null if unavailable */
  outputTokens: number | null;
}

export interface TrajectorySkeleton {
  turns: TurnSkeleton[];
  /** Distinct error status codes seen across all turns */
  errorCodes: number[];
}

export interface FirstDivergence {
  turn: number;
  kind: 'tool' | 'toolResult' | 'stopReason' | 'error' | 'usage';
  a: string;
  b: string;
}

export interface TokenTrajectoryEntry {
  turn: number;
  aInput: number | null;
  aOutput: number | null;
  bInput: number | null;
  bOutput: number | null;
}

export type DiffVerdict = 'identical' | 'divergent' | 'different-length';

export interface DiffReport {
  verdict: DiffVerdict;
  firstDivergence?: FirstDivergence;
  tokenTrajectory: TokenTrajectoryEntry[];
  turnCountA: number;
  turnCountB: number;
}

// ---------------------------------------------------------------------------
// Extraction helpers
// ---------------------------------------------------------------------------

function getToolCallNames(parsed: Record<string, unknown>): string[] {
  const kind = parsed['kind'] as string | undefined;

  if (kind === 'anthropic') {
    const content = parsed['responseContent'];
    if (!Array.isArray(content)) return [];
    return content
      .filter((c: unknown) => {
        if (typeof c !== 'object' || c === null) return false;
        return (c as Record<string, unknown>)['type'] === 'tool_use';
      })
      .map((c: unknown) => {
        const name = (c as Record<string, unknown>)['name'];
        return typeof name === 'string' ? name : 'unknown';
      });
  }

  if (kind === 'openai') {
    const choices = parsed['choices'];
    if (!Array.isArray(choices)) return [];
    const names: string[] = [];
    for (const choice of choices) {
      if (typeof choice !== 'object' || choice === null) continue;
      const message = (choice as Record<string, unknown>)['message'];
      if (typeof message !== 'object' || message === null) continue;
      const toolCalls = (message as Record<string, unknown>)['tool_calls'];
      if (!Array.isArray(toolCalls)) continue;
      for (const tc of toolCalls) {
        if (typeof tc !== 'object' || tc === null) continue;
        const fn = (tc as Record<string, unknown>)['function'];
        if (typeof fn === 'object' && fn !== null) {
          const name = (fn as Record<string, unknown>)['name'];
          names.push(typeof name === 'string' ? name : 'unknown');
        }
      }
    }
    return names;
  }

  if (kind === 'openai_responses') {
    // Responses API: output[] items with type 'function_call' carry the tool name.
    const output = parsed['output'];
    if (!Array.isArray(output)) return [];
    const names: string[] = [];
    for (const item of output) {
      if (typeof item !== 'object' || item === null) continue;
      const obj = item as Record<string, unknown>;
      if (obj['type'] === 'function_call') {
        names.push(typeof obj['name'] === 'string' ? (obj['name'] as string) : 'unknown');
      }
    }
    return names;
  }

  if (kind === 'gemini') {
    // candidates[].content.parts[].functionCall.name
    const candidates = parsed['candidates'];
    if (!Array.isArray(candidates)) return [];
    const names: string[] = [];
    for (const candidate of candidates) {
      const content = (candidate as Record<string, unknown>)?.['content'] as Record<string, unknown> | undefined;
      if (!content || !Array.isArray(content['parts'])) continue;
      for (const part of content['parts'] as Record<string, unknown>[]) {
        const fn = part['functionCall'] as Record<string, unknown> | undefined;
        if (fn && typeof fn['name'] === 'string') names.push(fn['name']);
      }
    }
    return names;
  }

  if (kind === 'ollama') {
    // responseMessage.tool_calls[].function.name
    const msg = parsed['responseMessage'] as Record<string, unknown> | undefined;
    if (!msg || !Array.isArray(msg['tool_calls'])) return [];
    const names: string[] = [];
    for (const tc of msg['tool_calls'] as Record<string, unknown>[]) {
      const fn = tc['function'] as Record<string, unknown> | undefined;
      if (fn && typeof fn['name'] === 'string') names.push(fn['name']);
    }
    return names;
  }

  return [];
}

function getHasToolResults(parsed: Record<string, unknown>): boolean[] {
  const toolNames = getToolCallNames(parsed);
  // A tool result is present if the response contains content blocks
  // with tool_use type that have non-null/non-undefined input.
  // Since we're looking at the response turn, tool results would be
  // in the *next* request's messages. For the skeleton we check whether
  // each tool call in this response has an id (indicating it was sent).
  const kind = parsed['kind'] as string | undefined;

  if (kind === 'anthropic') {
    const content = parsed['responseContent'];
    if (!Array.isArray(content)) return toolNames.map(() => false);
    const toolUseBlocks = content.filter((c: unknown) => {
      if (typeof c !== 'object' || c === null) return false;
      return (c as Record<string, unknown>)['type'] === 'tool_use';
    });
    return toolUseBlocks.map((c: unknown) => {
      if (typeof c !== 'object' || c === null) return false;
      const id = (c as Record<string, unknown>)['id'];
      return id !== undefined && id !== null;
    });
  }

  if (kind === 'openai') {
    const choices = parsed['choices'];
    if (!Array.isArray(choices)) return toolNames.map(() => false);
    const results: boolean[] = [];
    for (const choice of choices) {
      if (typeof choice !== 'object' || choice === null) continue;
      const message = (choice as Record<string, unknown>)['message'];
      if (typeof message !== 'object' || message === null) continue;
      const toolCalls = (message as Record<string, unknown>)['tool_calls'];
      if (!Array.isArray(toolCalls)) continue;
      for (const tc of toolCalls) {
        if (typeof tc !== 'object' || tc === null) {
          results.push(false);
          continue;
        }
        const id = (tc as Record<string, unknown>)['id'];
        results.push(id !== undefined && id !== null);
      }
    }
    return results;
  }

  if (kind === 'openai_responses') {
    const output = parsed['output'];
    if (!Array.isArray(output)) return toolNames.map(() => false);
    const results: boolean[] = [];
    for (const item of output) {
      if (typeof item !== 'object' || item === null) continue;
      const obj = item as Record<string, unknown>;
      if (obj['type'] !== 'function_call') continue;
      const id = obj['call_id'] ?? obj['id'];
      results.push(id !== undefined && id !== null);
    }
    return results;
  }

  // Gemini / Ollama function calls carry no call id; treat each emitted call as present.
  if (kind === 'gemini' || kind === 'ollama') {
    return toolNames.map(() => true);
  }

  return toolNames.map(() => false);
}

function getStopReason(parsed: Record<string, unknown>): string | null {
  const kind = parsed['kind'] as string | undefined;

  if (kind === 'anthropic') {
    const reason = parsed['stopReason'];
    return typeof reason === 'string' ? reason : null;
  }

  if (kind === 'openai') {
    const choices = parsed['choices'];
    if (!Array.isArray(choices) || choices.length === 0) return null;
    const first = choices[0];
    if (typeof first !== 'object' || first === null) return null;
    const reason = (first as Record<string, unknown>)['finish_reason'];
    return typeof reason === 'string' ? reason : null;
  }

  if (kind === 'gemini') {
    const candidates = parsed['candidates'];
    if (!Array.isArray(candidates) || candidates.length === 0) return null;
    const reason = (candidates[0] as Record<string, unknown>)?.['finishReason'];
    return typeof reason === 'string' ? reason : null;
  }

  // openai_responses / ollama do not surface a stop reason in the captured shape.
  return null;
}

function getTokens(parsed: Record<string, unknown>): { input: number | null; output: number | null } {
  const kind = parsed['kind'] as string | undefined;
  const usage = parsed['usage'];

  if (!usage || typeof usage !== 'object') {
    return { input: null, output: null };
  }

  const u = usage as Record<string, unknown>;

  if (kind === 'anthropic') {
    const input = typeof u['input_tokens'] === 'number' ? u['input_tokens'] : null;
    const output = typeof u['output_tokens'] === 'number' ? u['output_tokens'] : null;
    return { input, output };
  }

  if (kind === 'openai') {
    const input = typeof u['prompt_tokens'] === 'number' ? u['prompt_tokens'] : null;
    const output = typeof u['completion_tokens'] === 'number' ? u['completion_tokens'] : null;
    return { input, output };
  }

  if (kind === 'openai_responses') {
    // Responses API uses input_tokens / output_tokens.
    const input = typeof u['input_tokens'] === 'number' ? u['input_tokens'] : null;
    const output = typeof u['output_tokens'] === 'number' ? u['output_tokens'] : null;
    return { input, output };
  }

  if (kind === 'gemini') {
    const input = typeof u['promptTokenCount'] === 'number' ? u['promptTokenCount'] : null;
    const candidates = typeof u['candidatesTokenCount'] === 'number' ? u['candidatesTokenCount'] : null;
    const thoughts = typeof u['thoughtsTokenCount'] === 'number' ? u['thoughtsTokenCount'] : null;
    // Gemini bills thinking tokens as output.
    const output = candidates === null && thoughts === null ? null : (candidates ?? 0) + (thoughts ?? 0);
    return { input, output };
  }

  if (kind === 'ollama') {
    const input = typeof u['prompt_eval_count'] === 'number' ? u['prompt_eval_count'] : null;
    const output = typeof u['eval_count'] === 'number' ? u['eval_count'] : null;
    return { input, output };
  }

  return { input: null, output: null };
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Extract a structural trajectory skeleton from a session.
 * Each request in the session becomes one turn.
 */
export function extractTrajectory(session: Session): TrajectorySkeleton {
  const turns: TurnSkeleton[] = [];
  const errorCodes: number[] = [];

  for (const req of session.requests) {
    const parsed = req.parsed as unknown as Record<string, unknown>;
    const toolCalls = getToolCallNames(parsed);
    const hasToolResults = getHasToolResults(parsed);
    const stopReason = getStopReason(parsed);
    const tokens = getTokens(parsed);

    turns.push({
      toolCalls,
      hasToolResults,
      stopReason,
      statusCode: req.statusCode,
      inputTokens: tokens.input,
      outputTokens: tokens.output,
    });

    if (req.statusCode !== null && req.statusCode >= 400) {
      if (!errorCodes.includes(req.statusCode)) {
        errorCodes.push(req.statusCode);
      }
    }
  }

  return { turns, errorCodes: errorCodes.sort((a, b) => a - b) };
}

/**
 * Compare two trajectory skeletons structurally.
 *
 * Tool call sequence comparison uses tool name only, not arguments.
 * Token differences are reported in the tokenTrajectory table but
 * do NOT cause a "divergent" verdict on their own — only tool sequence,
 * stop_reason, and error signatures cause divergence.
 */
export function compareTrajectories(a: TrajectorySkeleton, b: TrajectorySkeleton): DiffReport {
  const maxTurns = Math.max(a.turns.length, b.turns.length);

  // Build token trajectory for all turns
  const tokenTrajectory: TokenTrajectoryEntry[] = [];
  for (let i = 0; i < maxTurns; i++) {
    const turnA = a.turns[i];
    const turnB = b.turns[i];
    tokenTrajectory.push({
      turn: i,
      aInput: turnA?.inputTokens ?? null,
      aOutput: turnA?.outputTokens ?? null,
      bInput: turnB?.inputTokens ?? null,
      bOutput: turnB?.outputTokens ?? null,
    });
  }

  // Different lengths
  if (a.turns.length !== b.turns.length) {
    return {
      verdict: 'different-length',
      firstDivergence: {
        turn: Math.min(a.turns.length, b.turns.length),
        kind: 'tool',
        a: `${a.turns.length} turns`,
        b: `${b.turns.length} turns`,
      },
      tokenTrajectory,
      turnCountA: a.turns.length,
      turnCountB: b.turns.length,
    };
  }

  // Check each turn for divergence
  for (let i = 0; i < a.turns.length; i++) {
    const turnA = a.turns[i]!;
    const turnB = b.turns[i]!;

    // Compare tool call sequences (name only). Use element-wise compare
    // rather than `join(',')` so a hypothetical tool name containing a
    // comma cannot mask a real divergence.
    const toolsDiffer = turnA.toolCalls.length !== turnB.toolCalls.length
      || turnA.toolCalls.some((name, idx) => name !== turnB.toolCalls[idx]);
    if (toolsDiffer) {
      return {
        verdict: 'divergent',
        firstDivergence: {
          turn: i,
          kind: 'tool',
          a: turnA.toolCalls.length > 0 ? turnA.toolCalls.join(', ') : '(no tool calls)',
          b: turnB.toolCalls.length > 0 ? turnB.toolCalls.join(', ') : '(no tool calls)',
        },
        tokenTrajectory,
        turnCountA: a.turns.length,
        turnCountB: b.turns.length,
      };
    }

    // Compare tool-result presence (the same tool was called but one run
    // got a result back while the other didn't — often signals an
    // execution failure or premature termination). Comparison is by
    // boolean presence only, never by result content.
    const resultsDiffer = turnA.hasToolResults.length !== turnB.hasToolResults.length
      || turnA.hasToolResults.some((present, idx) => present !== turnB.hasToolResults[idx]);
    if (resultsDiffer) {
      return {
        verdict: 'divergent',
        firstDivergence: {
          turn: i,
          kind: 'toolResult',
          a: turnA.hasToolResults.map((p) => p ? '✓' : '·').join(' '),
          b: turnB.hasToolResults.map((p) => p ? '✓' : '·').join(' '),
        },
        tokenTrajectory,
        turnCountA: a.turns.length,
        turnCountB: b.turns.length,
      };
    }

    // Compare stop reasons
    if (turnA.stopReason !== turnB.stopReason) {
      return {
        verdict: 'divergent',
        firstDivergence: {
          turn: i,
          kind: 'stopReason',
          a: turnA.stopReason ?? '(none)',
          b: turnB.stopReason ?? '(none)',
        },
        tokenTrajectory,
        turnCountA: a.turns.length,
        turnCountB: b.turns.length,
      };
    }

    // Compare error signatures (status codes)
    const aIsError = turnA.statusCode !== null && turnA.statusCode >= 400;
    const bIsError = turnB.statusCode !== null && turnB.statusCode >= 400;
    if (aIsError !== bIsError || (aIsError && bIsError && turnA.statusCode !== turnB.statusCode)) {
      return {
        verdict: 'divergent',
        firstDivergence: {
          turn: i,
          kind: 'error',
          a: turnA.statusCode !== null ? String(turnA.statusCode) : '(no response)',
          b: turnB.statusCode !== null ? String(turnB.statusCode) : '(no response)',
        },
        tokenTrajectory,
        turnCountA: a.turns.length,
        turnCountB: b.turns.length,
      };
    }
  }

  // All structural checks passed — identical
  return {
    verdict: 'identical',
    tokenTrajectory,
    turnCountA: a.turns.length,
    turnCountB: b.turns.length,
  };
}
