import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { geminiOutputTokens, type ParsedTraffic } from '../lib/llmTraffic';
import { estimateCostUsd } from '../lib/llmPricing';

// ---------------------------------------------------------------------------
// Provider label + token extraction
// ---------------------------------------------------------------------------

interface TokenCounts {
  inputTokens: number;
  outputTokens: number;
}

// Returns null when no usage data is present at all (so the UI shows "no usage data"
// rather than a misleading 0-token / $0.00). A field that is simply absent counts as 0,
// but if BOTH counts are absent the whole reading is treated as unknown.
function toCounts(input: number | undefined, output: number | undefined): TokenCounts | null {
  if (input == null && output == null) return null;
  return { inputTokens: input ?? 0, outputTokens: output ?? 0 };
}

function extractTokens(parsed: ParsedTraffic): TokenCounts | null {
  if (parsed.kind === 'anthropic' && parsed.usage) {
    return toCounts(parsed.usage.input_tokens, parsed.usage.output_tokens);
  }
  if (parsed.kind === 'openai' && parsed.usage) {
    return toCounts(parsed.usage.prompt_tokens, parsed.usage.completion_tokens);
  }
  if (parsed.kind === 'openai_responses' && parsed.usage) {
    // Responses API uses input_tokens / output_tokens.
    return toCounts(parsed.usage.input_tokens, parsed.usage.output_tokens);
  }
  if (parsed.kind === 'gemini' && parsed.usage) {
    return toCounts(parsed.usage.promptTokenCount, geminiOutputTokens(parsed.usage));
  }
  if (parsed.kind === 'ollama' && parsed.usage) {
    return toCounts(parsed.usage.prompt_eval_count, parsed.usage.eval_count);
  }
  return null;
}

function providerLabel(parsed: ParsedTraffic): string {
  switch (parsed.kind) {
    case 'anthropic': return parsed.platform === 'bedrock' ? 'Bedrock' : 'Anthropic';
    case 'openai': return 'OpenAI';
    case 'openai_responses': return 'OpenAI Responses';
    case 'gemini': return 'Gemini';
    case 'ollama': return 'Ollama';
    default: return parsed.kind;
  }
}

function extractStopReason(parsed: ParsedTraffic): string | null {
  if (parsed.kind === 'anthropic') return parsed.stopReason;
  if (parsed.kind === 'openai') {
    return parsed.choices.find((c) => c.finish_reason)?.finish_reason ?? null;
  }
  if (parsed.kind === 'gemini') {
    const first = parsed.candidates[0] as Record<string, unknown> | undefined;
    if (first && typeof first['finishReason'] === 'string') return first['finishReason'];
    return null;
  }
  // openai_responses and ollama don't surface a consistent stop-reason field;
  // their structured codecs report it inside individual output items.
  return null;
}

function formatCost(cost: number | null): string {
  if (cost === null) return '—';
  if (cost === 0) return '$0.00';
  if (cost < 0.01) return `$${cost.toFixed(4)}`;
  return `$${cost.toFixed(2)}`;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export interface LlmUsageDetailProps {
  parsed: ParsedTraffic;
}

/**
 * Always-visible strip rendered above the detail-pane tabs for LLM traffic.
 * Returns null for non-LLM kinds so the caller can drop it inline without
 * a wrapper check.
 */
export default function LlmUsageDetail({ parsed }: LlmUsageDetailProps) {
  const isLlm =
    parsed.kind === 'anthropic' ||
    parsed.kind === 'openai' ||
    parsed.kind === 'openai_responses' ||
    parsed.kind === 'gemini' ||
    parsed.kind === 'ollama';
  if (!isLlm) return null;

  const tokens = extractTokens(parsed);
  const model = 'model' in parsed ? parsed.model : null;
  const stopReason = extractStopReason(parsed);

  const cost = tokens && model
    ? estimateCostUsd(parsed.kind, model, tokens.inputTokens, tokens.outputTokens)
    : null;

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.75,
        flexWrap: 'wrap',
        px: 1,
        py: 0.5,
        borderBottom: 1,
        borderColor: 'divider',
        bgcolor: 'action.hover',
        flexShrink: 0,
      }}
    >
      <Chip
        label={providerLabel(parsed)}
        size="small"
        color="primary"
        variant="outlined"
        sx={{ height: 18, fontSize: '0.65rem', '& .MuiChip-label': { px: 0.75 } }}
      />
      {model && (
        <Typography
          variant="caption"
          sx={{ fontSize: '0.7rem', fontWeight: 600, color: 'text.primary' }}
        >
          {model}
        </Typography>
      )}
      {tokens && (
        <Chip
          label={`${tokens.inputTokens} in / ${tokens.outputTokens} out`}
          size="small"
          variant="outlined"
          sx={{ height: 18, fontSize: '0.65rem', '& .MuiChip-label': { px: 0.75 } }}
        />
      )}
      <Chip
        label={formatCost(cost)}
        size="small"
        variant="outlined"
        color={cost === null ? 'default' : 'success'}
        sx={{ height: 18, fontSize: '0.65rem', '& .MuiChip-label': { px: 0.75 } }}
      />
      {stopReason && (
        <Chip
          label={`stop: ${stopReason}`}
          size="small"
          variant="outlined"
          sx={{ height: 18, fontSize: '0.65rem', '& .MuiChip-label': { px: 0.75 } }}
        />
      )}
      {tokens === null && (
        <Typography variant="caption" sx={{ fontSize: '0.65rem', color: 'text.secondary', ml: 0.5 }}>
          (no usage data on this request)
        </Typography>
      )}
    </Box>
  );
}
