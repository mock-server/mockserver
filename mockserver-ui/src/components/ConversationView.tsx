import { Fragment, useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Button from '@mui/material/Button';
import BuildIcon from '@mui/icons-material/Build';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import Tooltip from '@mui/material/Tooltip';
import JsonViewer from './JsonViewer';
import { monospaceFontFamily } from '../theme';
import type {
  AnthropicParsed,
  AnthropicContentBlock,
  OpenAiParsed,
  OpenAiChoice,
  GeminiParsed,
  OllamaParsed,
  OpenAiResponsesParsed,
  ParsedTraffic,
  ConversationGroup,
} from '../lib/llmTraffic';
import { geminiOutputTokens } from '../lib/llmTraffic';

// ---------------------------------------------------------------------------
// Shared bubble styling constants
// ---------------------------------------------------------------------------

const BUBBLE_MAX_WIDTH = '75%';
const BUBBLE_RADIUS = 2.5; // MUI spacing => 20px
const MONO_FONT_SIZE = '0.75rem';
const LABEL_FONT_SIZE = '0.65rem';

const leftBubbleSx = {
  alignSelf: 'flex-start',
  maxWidth: BUBBLE_MAX_WIDTH,
  bgcolor: 'action.hover',
  borderRadius: BUBBLE_RADIUS,
  px: 1.5,
  py: 1,
} as const;

const rightBubbleSx = {
  alignSelf: 'flex-end',
  maxWidth: BUBBLE_MAX_WIDTH,
  bgcolor: 'primary.main',
  color: 'primary.contrastText',
  borderRadius: BUBBLE_RADIUS,
  px: 1.5,
  py: 1,
} as const;

const toolBubbleSx = {
  // Tool bubbles nest inside an already-narrow chat bubble (`maxWidth: 75%`),
  // so capping them at another 75% (= 56% of the panel) made long JSON values
  // like the get_weather argument get visually clipped. Let them fill the
  // parent bubble width and add overflow:auto + word-break so anything still
  // too wide can scroll instead of being silently truncated.
  maxWidth: '100%',
  minWidth: 0,
  overflow: 'auto',
  wordBreak: 'break-word',
  bgcolor: 'background.paper',
  border: 1,
  borderColor: 'secondary.main',
  borderRadius: BUBBLE_RADIUS,
  px: 1.5,
  py: 1,
} as const;

// ---------------------------------------------------------------------------
// Role label above a bubble
// ---------------------------------------------------------------------------

function RoleLabel({ role, align }: { role: string; align: 'left' | 'right' }) {
  return (
    <Typography
      variant="caption"
      sx={{
        display: 'block',
        fontSize: LABEL_FONT_SIZE,
        color: 'text.secondary',
        textAlign: align,
        mb: 0.25,
        textTransform: 'capitalize',
      }}
    >
      {role}
    </Typography>
  );
}

// ---------------------------------------------------------------------------
// System banner (full-width, centered, muted)
// ---------------------------------------------------------------------------

function SystemBanner({ content }: { content: unknown }) {
  return (
    <Box
      sx={{
        width: '100%',
        bgcolor: 'action.disabledBackground',
        borderRadius: 1.5,
        px: 2,
        py: 1,
        mb: 1.5,
      }}
    >
      <Typography
        variant="caption"
        sx={{
          display: 'block',
          fontWeight: 700,
          fontSize: LABEL_FONT_SIZE,
          color: 'text.secondary',
          mb: 0.5,
          textAlign: 'center',
          textTransform: 'uppercase',
          letterSpacing: 0.5,
        }}
      >
        System
      </Typography>
      {typeof content === 'string' ? (
        <Typography
          variant="body2"
          sx={{
            fontFamily: monospaceFontFamily,
            fontSize: MONO_FONT_SIZE,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
          }}
        >
          {content}
        </Typography>
      ) : Array.isArray(content) ? (
        // Anthropic system can be an array of blocks
        content.map((block, i) => {
          const b = block as Record<string, unknown>;
          if (b['type'] === 'text' && typeof b['text'] === 'string') {
            return (
              <Typography
                key={i}
                variant="body2"
                sx={{
                  fontFamily: monospaceFontFamily,
                  fontSize: MONO_FONT_SIZE,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                {b['text']}
              </Typography>
            );
          }
          return (
            <Box key={i} sx={{ mt: 0.5 }}>
              <JsonViewer data={b} collapsed={2} />
            </Box>
          );
        })
      ) : (
        <JsonViewer data={content as Record<string, unknown>} collapsed={2} />
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Anthropic content block renderer (used for both request messages and response)
// ---------------------------------------------------------------------------

function renderAnthropicContentBlock(
  block: AnthropicContentBlock,
  index: number,
  side: 'left' | 'right',
) {
  if (block.type === 'text' && block.text != null) {
    return (
      <Typography
        key={index}
        variant="body2"
        sx={{
          fontFamily: monospaceFontFamily,
          fontSize: MONO_FONT_SIZE,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          color: side === 'right' ? 'primary.contrastText' : 'text.primary',
        }}
      >
        {block.text}
      </Typography>
    );
  }

  if (block.type === 'tool_use') {
    return (
      <Box key={index} sx={{ ...toolBubbleSx, alignSelf: side === 'left' ? 'flex-start' : 'flex-end', mt: 0.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
          <BuildIcon sx={{ fontSize: '0.85rem', color: 'secondary.main' }} />
          <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontWeight: 600, fontSize: LABEL_FONT_SIZE, color: 'secondary.main' }}>
            {block.name ?? 'tool_use'}
          </Typography>
        </Box>
        {block.input != null && (
          <JsonViewer data={block.input as Record<string, unknown>} collapsed={1} />
        )}
      </Box>
    );
  }

  if (block.type === 'tool_result') {
    const b = block as unknown as Record<string, unknown>;
    return (
      <Box key={index} sx={{ ...toolBubbleSx, alignSelf: 'flex-start', borderColor: 'info.main', mt: 0.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
          <BuildIcon sx={{ fontSize: '0.85rem', color: 'info.main' }} />
          <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontWeight: 600, fontSize: LABEL_FONT_SIZE, color: 'info.main' }}>
            tool_result
          </Typography>
        </Box>
        {typeof b['content'] === 'string' ? (
          <Typography variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
            {b['content']}
          </Typography>
        ) : b['content'] != null ? (
          <JsonViewer data={b['content'] as Record<string, unknown>} collapsed={1} />
        ) : null}
      </Box>
    );
  }

  if (block.type === 'image') {
    return (
      <Typography key={index} variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, color: 'text.secondary' }}>
        [image]
      </Typography>
    );
  }

  // Unknown block type
  return (
    <Typography key={index} variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, color: 'text.secondary' }}>
      [{block.type}]
    </Typography>
  );
}

// ---------------------------------------------------------------------------
// Render Anthropic message content (string or array of blocks)
// ---------------------------------------------------------------------------

function renderAnthropicMessageContent(content: unknown, side: 'left' | 'right') {
  if (typeof content === 'string') {
    return (
      <Typography
        variant="body2"
        sx={{
          fontFamily: monospaceFontFamily,
          fontSize: MONO_FONT_SIZE,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          color: side === 'right' ? 'primary.contrastText' : 'text.primary',
        }}
      >
        {content}
      </Typography>
    );
  }

  if (Array.isArray(content)) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
        {content.map((block, i) => renderAnthropicContentBlock(block as AnthropicContentBlock, i, side))}
      </Box>
    );
  }

  // Fallback: render as JSON
  if (content != null && typeof content === 'object') {
    return <JsonViewer data={content as Record<string, unknown>} collapsed={2} />;
  }

  return null;
}

// ---------------------------------------------------------------------------
// Render OpenAI message content
// ---------------------------------------------------------------------------

function renderOpenAiToolCalls(toolCalls: unknown[], side: 'left' | 'right') {
  return toolCalls.map((tc, i) => {
    const call = tc as Record<string, unknown>;
    const fn = call['function'] as Record<string, unknown> | undefined;
    const name = fn ? String(fn['name'] ?? 'function') : 'tool_call';
    let args: unknown = fn?.['arguments'];
    if (typeof args === 'string') {
      try { args = JSON.parse(args); } catch { /* leave as string */ }
    }

    return (
      <Box key={i} sx={{ ...toolBubbleSx, alignSelf: side === 'left' ? 'flex-start' : 'flex-end', mt: 0.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
          <BuildIcon sx={{ fontSize: '0.85rem', color: 'secondary.main' }} />
          <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontWeight: 600, fontSize: LABEL_FONT_SIZE, color: 'secondary.main' }}>
            {name}
          </Typography>
        </Box>
        {args != null && (
          typeof args === 'object' ? (
            <JsonViewer data={args as Record<string, unknown>} collapsed={1} />
          ) : (
            <Typography variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {String(args)}
            </Typography>
          )
        )}
      </Box>
    );
  });
}

// ---------------------------------------------------------------------------
// Metadata strip (chips across the top)
// ---------------------------------------------------------------------------

interface MetadataStripProps {
  model: string | null;
  inputTokens?: number;
  outputTokens?: number;
  stopReason: string | null;
  streamed: boolean;
  streamTruncated: boolean;
}

function MetadataStrip({ model, inputTokens, outputTokens, stopReason, streamed, streamTruncated }: MetadataStripProps) {
  const hasAny = model || inputTokens != null || outputTokens != null || stopReason || streamed || streamTruncated;
  if (!hasAny) return null;

  return (
    <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mb: 1.5, justifyContent: 'center' }}>
      {model && (
        <Tooltip title={model}>
          <Chip label={model} size="small" variant="outlined" sx={{ height: 20, fontSize: '0.65rem', maxWidth: 200, '& .MuiChip-label': { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }} />
        </Tooltip>
      )}
      {inputTokens != null && <Chip label={`In: ${inputTokens}`} size="small" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />}
      {outputTokens != null && <Chip label={`Out: ${outputTokens}`} size="small" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />}
      {stopReason && <Chip label={`Stop: ${stopReason}`} size="small" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />}
      {streamed && <Chip label="Streamed" size="small" color="info" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />}
      {streamTruncated && <Chip label="Truncated" size="small" color="warning" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Truncated / malformed non-stream response notice
// ---------------------------------------------------------------------------

/**
 * True when a non-stream response body looks like it failed to parse — e.g. it was
 * truncated at the capture cap, leaving invalid JSON. Returns false for an empty
 * body (nothing captured yet) and for a body that parses cleanly. Streaming
 * truncation is surfaced separately by the "Truncated" metadata chip, so callers
 * pass `rawResponseBody` only for non-stream responses.
 */
export function responseLooksUnparseable(rawResponseBody: string | null | undefined): boolean {
  if (typeof rawResponseBody !== 'string') return false;
  const trimmed = rawResponseBody.trim();
  if (trimmed.length === 0) return false;
  try {
    JSON.parse(trimmed);
    return false;
  } catch {
    return true;
  }
}

/**
 * Warns that a non-stream response body could not be parsed (and was likely
 * truncated), with a toggle to reveal the raw captured body. Renders nothing when
 * the body parsed cleanly or is absent, so it is safe to drop into every view.
 */
function TruncatedResponseNotice({ rawResponseBody }: { rawResponseBody?: string | null }) {
  const [showRaw, setShowRaw] = useState(false);
  if (!responseLooksUnparseable(rawResponseBody)) return null;

  return (
    <Box
      data-testid="truncated-response-notice"
      sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, alignItems: 'flex-start', mt: 0.5 }}
    >
      <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center', flexWrap: 'wrap' }}>
        <Chip
          size="small"
          color="warning"
          variant="outlined"
          icon={<WarningAmberIcon sx={{ fontSize: '0.85rem' }} />}
          label="Response could not be parsed — possibly truncated"
          sx={{ height: 20, fontSize: '0.65rem' }}
        />
        <Button
          size="small"
          onClick={() => setShowRaw((v) => !v)}
          sx={{ fontSize: '0.65rem', textTransform: 'none', minWidth: 0, py: 0 }}
        >
          {showRaw ? 'Hide raw body' : 'Show raw body'}
        </Button>
      </Box>
      {showRaw && (
        <Box
          component="pre"
          sx={{
            m: 0,
            p: 1,
            width: '100%',
            borderRadius: 1,
            bgcolor: 'action.hover',
            fontFamily: monospaceFontFamily,
            fontSize: '0.7rem',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            overflowX: 'auto',
          }}
        >
          {rawResponseBody}
        </Box>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Anthropic ConversationView
// ---------------------------------------------------------------------------

export function AnthropicConversationView({ parsed, rawResponseBody }: { parsed: AnthropicParsed; rawResponseBody?: string | null }) {
  const inputTokens = parsed.usage?.input_tokens;
  const outputTokens = parsed.usage?.output_tokens;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, p: 1 }}>
      <MetadataStrip
        model={parsed.model}
        inputTokens={inputTokens}
        outputTokens={outputTokens}
        stopReason={parsed.stopReason}
        streamed={parsed.streamed}
        streamTruncated={parsed.streamTruncated}
      />

      {/* System banner */}
      {parsed.system != null && <SystemBanner content={parsed.system} />}

      {/* Request messages */}
      {parsed.messages.map((msg, i) => {
        const m = msg as Record<string, unknown>;
        const role = String(m['role'] ?? 'unknown');
        const isLeft = role === 'user' || role === 'tool';
        const side = isLeft ? 'left' : 'right';
        const bubbleSx = isLeft ? leftBubbleSx : rightBubbleSx;

        return (
          <Box key={`msg-${i}`} sx={{ display: 'flex', flexDirection: 'column' }}>
            <RoleLabel role={role} align={side} />
            <Box sx={bubbleSx}>
              {renderAnthropicMessageContent(m['content'], side)}
            </Box>
          </Box>
        );
      })}

      {/* Response (final assistant bubble) */}
      {parsed.responseContent.length > 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column' }}>
          <RoleLabel role="assistant" align="right" />
          <Box sx={{ ...rightBubbleSx, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            {parsed.responseContent.map((block, i) => renderAnthropicContentBlock(block, i, 'right'))}
          </Box>
        </Box>
      )}

      {/* Truncated/malformed non-stream response warning */}
      {parsed.responseContent.length === 0 && <TruncatedResponseNotice rawResponseBody={rawResponseBody} />}

      {/* Empty state */}
      {parsed.messages.length === 0 && parsed.responseContent.length === 0 && !responseLooksUnparseable(rawResponseBody) && (
        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
          No conversation content
        </Typography>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// OpenAI ConversationView
// ---------------------------------------------------------------------------

export function OpenAiConversationView({ parsed, rawResponseBody }: { parsed: OpenAiParsed; rawResponseBody?: string | null }) {
  const inputTokens = parsed.usage?.prompt_tokens;
  const outputTokens = parsed.usage?.completion_tokens;

  // Compute the first finish_reason from choices
  const finishReason = parsed.choices.reduce<string | null>((acc, c) => acc ?? c.finish_reason ?? null, null);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, p: 1 }}>
      <MetadataStrip
        model={parsed.model}
        inputTokens={inputTokens}
        outputTokens={outputTokens}
        stopReason={finishReason}
        streamed={parsed.streamed}
        streamTruncated={parsed.streamTruncated}
      />

      {/* Request messages */}
      {parsed.messages.map((msg, i) => {
        const m = msg as Record<string, unknown>;
        const role = String(m['role'] ?? 'unknown');

        // System message => banner
        if (role === 'system') {
          return <SystemBanner key={`msg-${i}`} content={m['content']} />;
        }

        const isLeft = role === 'user' || role === 'tool';
        const side = isLeft ? 'left' : 'right';
        const bubbleSx = isLeft ? leftBubbleSx : rightBubbleSx;

        const content = m['content'];
        const toolCalls = m['tool_calls'] as unknown[] | undefined;

        return (
          <Box key={`msg-${i}`} sx={{ display: 'flex', flexDirection: 'column' }}>
            <RoleLabel role={role} align={side} />
            {content != null && (
              <Box sx={bubbleSx}>
                {typeof content === 'string' ? (
                  <Typography
                    variant="body2"
                    sx={{
                      fontFamily: monospaceFontFamily,
                      fontSize: MONO_FONT_SIZE,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                      color: side === 'right' ? 'primary.contrastText' : 'text.primary',
                    }}
                  >
                    {content}
                  </Typography>
                ) : Array.isArray(content) ? (
                  // OpenAI content can be an array of parts (text, image_url, etc.)
                  content.map((part, j) => {
                    const p = part as Record<string, unknown>;
                    if (p['type'] === 'text' && typeof p['text'] === 'string') {
                      return (
                        <Typography
                          key={j}
                          variant="body2"
                          sx={{
                            fontFamily: monospaceFontFamily,
                            fontSize: MONO_FONT_SIZE,
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-word',
                            color: side === 'right' ? 'primary.contrastText' : 'text.primary',
                          }}
                        >
                          {p['text']}
                        </Typography>
                      );
                    }
                    if (p['type'] === 'image_url') {
                      return (
                        <Typography key={j} variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, color: 'text.secondary' }}>
                          [image]
                        </Typography>
                      );
                    }
                    return (
                      <Typography key={j} variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, color: 'text.secondary' }}>
                        [{String(p['type'] ?? 'unknown')}]
                      </Typography>
                    );
                  })
                ) : (
                  <JsonViewer data={content as Record<string, unknown>} collapsed={2} />
                )}
              </Box>
            )}
            {/* Tool calls attached to this message */}
            {toolCalls && toolCalls.length > 0 && renderOpenAiToolCalls(toolCalls, side)}
          </Box>
        );
      })}

      {/* Response choices */}
      {parsed.choices.map((choice: OpenAiChoice, i: number) => {
        const hasContent = choice.message?.content != null;
        const hasToolCalls = choice.message?.tool_calls && choice.message.tool_calls.length > 0;

        if (!hasContent && !hasToolCalls) return null;

        return (
          <Box key={`choice-${i}`} sx={{ display: 'flex', flexDirection: 'column' }}>
            <RoleLabel role="assistant" align="right" />
            {hasContent && (
              <Box sx={rightBubbleSx}>
                <Typography
                  variant="body2"
                  sx={{
                    fontFamily: monospaceFontFamily,
                    fontSize: MONO_FONT_SIZE,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    color: 'primary.contrastText',
                  }}
                >
                  {choice.message!.content}
                </Typography>
              </Box>
            )}
            {hasToolCalls && renderOpenAiToolCalls(choice.message!.tool_calls!, 'right')}
          </Box>
        );
      })}

      {/* Truncated/malformed non-stream response warning */}
      {parsed.choices.length === 0 && <TruncatedResponseNotice rawResponseBody={rawResponseBody} />}

      {/* Empty state */}
      {parsed.messages.length === 0 && parsed.choices.length === 0 && !responseLooksUnparseable(rawResponseBody) && (
        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
          No conversation content
        </Typography>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Gemini helpers
// ---------------------------------------------------------------------------

/**
 * Gemini messages are { role: 'user'|'model', parts: [{text}|{functionCall}|{functionResponse}|...] }
 * Render the parts list as chat-bubble content with tool boxes for function calls/responses.
 */
function renderGeminiParts(parts: unknown, side: 'left' | 'right') {
  if (!Array.isArray(parts)) {
    if (parts != null && typeof parts === 'object') {
      return <JsonViewer data={parts as Record<string, unknown>} collapsed={2} />;
    }
    return null;
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
      {parts.map((p, i) => {
        const part = p as Record<string, unknown>;
        if (typeof part['text'] === 'string') {
          return (
            <Typography
              key={i}
              variant="body2"
              sx={{
                fontFamily: monospaceFontFamily,
                fontSize: MONO_FONT_SIZE,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                color: side === 'right' ? 'primary.contrastText' : 'text.primary',
              }}
            >
              {part['text']}
            </Typography>
          );
        }
        if (part['functionCall'] && typeof part['functionCall'] === 'object') {
          const fc = part['functionCall'] as Record<string, unknown>;
          return (
            <Box key={i} sx={{ ...toolBubbleSx, alignSelf: side === 'left' ? 'flex-start' : 'flex-end', mt: 0.5 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
                <BuildIcon sx={{ fontSize: '0.85rem', color: 'secondary.main' }} />
                <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontWeight: 600, fontSize: LABEL_FONT_SIZE, color: 'secondary.main' }}>
                  {String(fc['name'] ?? 'functionCall')}
                </Typography>
              </Box>
              {fc['args'] != null && (
                <JsonViewer data={fc['args'] as Record<string, unknown>} collapsed={1} />
              )}
            </Box>
          );
        }
        if (part['functionResponse'] && typeof part['functionResponse'] === 'object') {
          const fr = part['functionResponse'] as Record<string, unknown>;
          return (
            <Box key={i} sx={{ ...toolBubbleSx, alignSelf: 'flex-start', borderColor: 'info.main', mt: 0.5 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
                <BuildIcon sx={{ fontSize: '0.85rem', color: 'info.main' }} />
                <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontWeight: 600, fontSize: LABEL_FONT_SIZE, color: 'info.main' }}>
                  {String(fr['name'] ?? 'functionResponse')}
                </Typography>
              </Box>
              {fr['response'] != null && (
                <JsonViewer data={fr['response'] as Record<string, unknown>} collapsed={1} />
              )}
            </Box>
          );
        }
        if (part['inlineData'] && typeof part['inlineData'] === 'object') {
          return (
            <Typography key={i} variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, color: 'text.secondary' }}>
              [inlineData]
            </Typography>
          );
        }
        return (
          <Box key={i} sx={{ mt: 0.5 }}>
            <JsonViewer data={part} collapsed={1} />
          </Box>
        );
      })}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Gemini ConversationView
// ---------------------------------------------------------------------------

export function GeminiConversationView({ parsed, rawResponseBody }: { parsed: GeminiParsed; rawResponseBody?: string | null }) {
  const inputTokens = parsed.usage?.promptTokenCount;
  const outputTokens = geminiOutputTokens(parsed.usage);

  // Pull the first candidate's finishReason (if any) for the metadata strip.
  const firstCandidate = parsed.candidates[0] as Record<string, unknown> | undefined;
  const stopReason = firstCandidate
    ? (typeof firstCandidate['finishReason'] === 'string' ? firstCandidate['finishReason'] : null)
    : null;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, p: 1 }}>
      <MetadataStrip
        model={parsed.model}
        inputTokens={inputTokens}
        outputTokens={outputTokens}
        stopReason={stopReason}
        streamed={parsed.streamed}
        streamTruncated={parsed.streamTruncated}
      />

      {/* Request side: contents */}
      {parsed.contents.map((entry, i) => {
        const e = entry as Record<string, unknown>;
        // Gemini wire role is 'user' or 'model'; treat 'user' on the left,
        // 'model' on the right to match Anthropic/OpenAI orientation.
        const role = String(e['role'] ?? 'unknown');
        const isLeft = role === 'user';
        const side: 'left' | 'right' = isLeft ? 'left' : 'right';
        const bubbleSx = isLeft ? leftBubbleSx : rightBubbleSx;
        return (
          <Box key={`req-${i}`} sx={{ display: 'flex', flexDirection: 'column' }}>
            <RoleLabel role={role} align={side} />
            <Box sx={bubbleSx}>
              {renderGeminiParts(e['parts'], side)}
            </Box>
          </Box>
        );
      })}

      {/* Response side: candidates */}
      {parsed.candidates.map((cand, i) => {
        const c = cand as Record<string, unknown>;
        const content = c['content'] as Record<string, unknown> | undefined;
        if (!content) return null;
        return (
          <Box key={`cand-${i}`} sx={{ display: 'flex', flexDirection: 'column' }}>
            <RoleLabel role={typeof content['role'] === 'string' ? String(content['role']) : 'model'} align="right" />
            <Box sx={rightBubbleSx}>
              {renderGeminiParts(content['parts'], 'right')}
            </Box>
          </Box>
        );
      })}

      {/* Truncated/malformed non-stream response warning */}
      {parsed.candidates.length === 0 && <TruncatedResponseNotice rawResponseBody={rawResponseBody} />}

      {/* Empty state */}
      {parsed.contents.length === 0 && parsed.candidates.length === 0 && !responseLooksUnparseable(rawResponseBody) && (
        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
          No conversation content
        </Typography>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Ollama helpers — Ollama message shape mirrors OpenAI's chat.completions
// closely enough that we render strings directly and reuse the tool-call
// renderer with a small adapter for the differently-shaped tool calls.
// ---------------------------------------------------------------------------

function renderOllamaMessageContent(content: unknown, side: 'left' | 'right') {
  if (typeof content === 'string') {
    return (
      <Typography
        variant="body2"
        sx={{
          fontFamily: monospaceFontFamily,
          fontSize: MONO_FONT_SIZE,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          color: side === 'right' ? 'primary.contrastText' : 'text.primary',
        }}
      >
        {content}
      </Typography>
    );
  }
  if (content != null && typeof content === 'object') {
    return <JsonViewer data={content as Record<string, unknown>} collapsed={2} />;
  }
  return null;
}

// ---------------------------------------------------------------------------
// Ollama ConversationView
// ---------------------------------------------------------------------------

export function OllamaConversationView({ parsed, rawResponseBody }: { parsed: OllamaParsed; rawResponseBody?: string | null }) {
  const inputTokens = parsed.usage?.prompt_eval_count;
  const outputTokens = parsed.usage?.eval_count;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, p: 1 }}>
      <MetadataStrip
        model={parsed.model}
        inputTokens={inputTokens}
        outputTokens={outputTokens}
        stopReason={null}
        streamed={parsed.streamed}
        streamTruncated={parsed.streamTruncated}
      />

      {/* Request side: messages */}
      {parsed.messages.map((msg, i) => {
        const m = msg as Record<string, unknown>;
        const role = String(m['role'] ?? 'unknown');

        if (role === 'system') {
          return <SystemBanner key={`msg-${i}`} content={m['content']} />;
        }

        const isLeft = role === 'user' || role === 'tool';
        const side: 'left' | 'right' = isLeft ? 'left' : 'right';
        const bubbleSx = isLeft ? leftBubbleSx : rightBubbleSx;
        const toolCalls = m['tool_calls'] as unknown[] | undefined;

        return (
          <Box key={`msg-${i}`} sx={{ display: 'flex', flexDirection: 'column' }}>
            <RoleLabel role={role} align={side} />
            {m['content'] != null && (
              <Box sx={bubbleSx}>
                {renderOllamaMessageContent(m['content'], side)}
              </Box>
            )}
            {toolCalls && toolCalls.length > 0 && renderOpenAiToolCalls(toolCalls, side)}
          </Box>
        );
      })}

      {/* Response side: responseMessage */}
      {parsed.responseMessage != null && (() => {
        const r = parsed.responseMessage as Record<string, unknown>;
        const role = typeof r['role'] === 'string' ? String(r['role']) : 'assistant';
        const toolCalls = r['tool_calls'] as unknown[] | undefined;
        return (
          <Box sx={{ display: 'flex', flexDirection: 'column' }}>
            <RoleLabel role={role} align="right" />
            {r['content'] != null && (
              <Box sx={rightBubbleSx}>
                {renderOllamaMessageContent(r['content'], 'right')}
              </Box>
            )}
            {toolCalls && toolCalls.length > 0 && renderOpenAiToolCalls(toolCalls, 'right')}
          </Box>
        );
      })()}

      {/* Truncated/malformed non-stream response warning */}
      {parsed.responseMessage == null && <TruncatedResponseNotice rawResponseBody={rawResponseBody} />}

      {/* Empty state */}
      {parsed.messages.length === 0 && parsed.responseMessage == null && !responseLooksUnparseable(rawResponseBody) && (
        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
          No conversation content
        </Typography>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// OpenAI Responses helpers
// ---------------------------------------------------------------------------

/**
 * OpenAI Responses API items are typed: `message`, `function_call`,
 * `function_call_output`, `reasoning`, `image_generation_call`, etc.
 * For each type we pick the most useful visual representation.
 */
function renderResponsesItem(item: unknown, index: number, side: 'left' | 'right') {
  const it = item as Record<string, unknown>;
  const type = String(it['type'] ?? 'message');

  if (type === 'message') {
    const role = String(it['role'] ?? 'assistant');
    const isLeft = role === 'user' || role === 'tool' || role === 'developer';
    const renderSide: 'left' | 'right' = isLeft ? 'left' : 'right';
    const bubbleSx = isLeft ? leftBubbleSx : rightBubbleSx;
    const content = it['content'];

    return (
      <Box key={index} sx={{ display: 'flex', flexDirection: 'column' }}>
        <RoleLabel role={role} align={renderSide} />
        <Box sx={bubbleSx}>
          {typeof content === 'string' ? (
            <Typography
              variant="body2"
              sx={{
                fontFamily: monospaceFontFamily,
                fontSize: MONO_FONT_SIZE,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                color: renderSide === 'right' ? 'primary.contrastText' : 'text.primary',
              }}
            >
              {content}
            </Typography>
          ) : Array.isArray(content) ? (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
              {content.map((part, j) => {
                const p = part as Record<string, unknown>;
                const t = String(p['type'] ?? '');
                if ((t === 'input_text' || t === 'output_text') && typeof p['text'] === 'string') {
                  return (
                    <Typography
                      key={j}
                      variant="body2"
                      sx={{
                        fontFamily: monospaceFontFamily,
                        fontSize: MONO_FONT_SIZE,
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                        color: renderSide === 'right' ? 'primary.contrastText' : 'text.primary',
                      }}
                    >
                      {p['text']}
                    </Typography>
                  );
                }
                if (t === 'input_image') {
                  return (
                    <Typography key={j} variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, color: 'text.secondary' }}>
                      [input_image]
                    </Typography>
                  );
                }
                return (
                  <Typography key={j} variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, color: 'text.secondary' }}>
                    [{t || 'unknown'}]
                  </Typography>
                );
              })}
            </Box>
          ) : content != null ? (
            <JsonViewer data={content as Record<string, unknown>} collapsed={2} />
          ) : null}
        </Box>
      </Box>
    );
  }

  if (type === 'function_call') {
    let args: unknown = it['arguments'];
    if (typeof args === 'string') {
      try { args = JSON.parse(args); } catch { /* leave as string */ }
    }
    return (
      <Box key={index} sx={{ display: 'flex', flexDirection: 'column' }}>
        <RoleLabel role="function_call" align={side} />
        <Box sx={{ ...toolBubbleSx, alignSelf: side === 'left' ? 'flex-start' : 'flex-end' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
            <BuildIcon sx={{ fontSize: '0.85rem', color: 'secondary.main' }} />
            <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontWeight: 600, fontSize: LABEL_FONT_SIZE, color: 'secondary.main' }}>
              {String(it['name'] ?? 'function_call')}
            </Typography>
          </Box>
          {args != null && (
            typeof args === 'object' ? (
              <JsonViewer data={args as Record<string, unknown>} collapsed={1} />
            ) : (
              <Typography variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, whiteSpace: 'pre-wrap' }}>
                {String(args)}
              </Typography>
            )
          )}
        </Box>
      </Box>
    );
  }

  if (type === 'function_call_output') {
    return (
      <Box key={index} sx={{ display: 'flex', flexDirection: 'column' }}>
        <RoleLabel role="function_call_output" align="left" />
        <Box sx={{ ...toolBubbleSx, alignSelf: 'flex-start', borderColor: 'info.main' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
            <BuildIcon sx={{ fontSize: '0.85rem', color: 'info.main' }} />
            <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontWeight: 600, fontSize: LABEL_FONT_SIZE, color: 'info.main' }}>
              output
            </Typography>
          </Box>
          {typeof it['output'] === 'string' ? (
            <Typography variant="body2" sx={{ fontFamily: monospaceFontFamily, fontSize: MONO_FONT_SIZE, whiteSpace: 'pre-wrap' }}>
              {it['output']}
            </Typography>
          ) : it['output'] != null ? (
            <JsonViewer data={it['output'] as Record<string, unknown>} collapsed={1} />
          ) : null}
        </Box>
      </Box>
    );
  }

  // Unknown type — show JSON
  return (
    <Box key={index} sx={{ mt: 0.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontSize: LABEL_FONT_SIZE, color: 'text.secondary' }}>
        [{type}]
      </Typography>
      <JsonViewer data={it} collapsed={1} />
    </Box>
  );
}

// ---------------------------------------------------------------------------
// OpenAI Responses ConversationView
// ---------------------------------------------------------------------------

export function OpenAiResponsesConversationView({ parsed, rawResponseBody }: { parsed: OpenAiResponsesParsed; rawResponseBody?: string | null }) {
  // Responses API reports input_tokens / output_tokens (not prompt_/completion_tokens).
  const inputTokens = parsed.usage?.input_tokens;
  const outputTokens = parsed.usage?.output_tokens;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, p: 1 }}>
      <MetadataStrip
        model={parsed.model}
        inputTokens={inputTokens}
        outputTokens={outputTokens}
        stopReason={null}
        streamed={parsed.streamed}
        streamTruncated={parsed.streamTruncated}
      />

      {/* Request side: input */}
      {parsed.input.map((item, i) => renderResponsesItem(item, i, 'left'))}

      {/* Response side: output */}
      {parsed.output.map((item, i) => renderResponsesItem(item, parsed.input.length + i, 'right'))}

      {/* Truncated/malformed non-stream response warning */}
      {parsed.output.length === 0 && <TruncatedResponseNotice rawResponseBody={rawResponseBody} />}

      {/* Empty state */}
      {parsed.input.length === 0 && parsed.output.length === 0 && !responseLooksUnparseable(rawResponseBody) && (
        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
          No conversation content
        </Typography>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Grouped (growing-conversation) view
//
// A stateless coding-assistant CLI resends its whole growing history every turn.
// `groupConversationTurns` (llmTraffic.ts) collapses such a run into ONE group
// whose turns each carry only the NEW messages they added. This view renders the
// group as a single thread that GROWS — each turn contributes only its delta, not
// the whole replayed history — reusing the per-provider Conversation views above.
//
// Trick: for each turn we synthesise a parsed object whose request-message array
// is just that turn's delta, and we keep the assistant response / model / token
// metadata only on the LAST turn (every earlier turn's response reappears as a
// message in the next turn's delta, so showing it again would duplicate it).
// ---------------------------------------------------------------------------

/** The subset of ParsedTraffic kinds that carry a renderable conversation. */
type ConversationParsed =
  | AnthropicParsed
  | OpenAiParsed
  | OpenAiResponsesParsed
  | GeminiParsed
  | OllamaParsed;

function isConversationParsed(parsed: ParsedTraffic): parsed is ConversationParsed {
  return (
    parsed.kind === 'anthropic' ||
    parsed.kind === 'openai' ||
    parsed.kind === 'openai_responses' ||
    parsed.kind === 'gemini' ||
    parsed.kind === 'ollama'
  );
}

/**
 * Build a per-turn parsed object: the request-message array is replaced with this
 * turn's delta; response/model/token metadata is retained only on the last turn
 * and the system prompt only on the first. SSE events are dropped (already
 * reassembled into the response on the last turn).
 */
function turnParsed(
  parsed: ConversationParsed,
  delta: unknown[],
  isFirst: boolean,
  isLast: boolean,
): ConversationParsed {
  const model = isLast ? parsed.model : null;
  const streamed = isLast ? parsed.streamed : false;
  const streamTruncated = isLast ? parsed.streamTruncated : false;
  switch (parsed.kind) {
    case 'anthropic':
      return {
        ...parsed,
        messages: delta,
        system: isFirst ? parsed.system : null,
        model,
        responseContent: isLast ? parsed.responseContent : [],
        usage: isLast ? parsed.usage : null,
        stopReason: isLast ? parsed.stopReason : null,
        sseEvents: null,
        streamed,
        streamTruncated,
      };
    case 'openai':
      return {
        ...parsed,
        messages: delta,
        model,
        choices: isLast ? parsed.choices : [],
        usage: isLast ? parsed.usage : null,
        sseEvents: null,
        streamed,
        streamTruncated,
      };
    case 'openai_responses':
      return {
        ...parsed,
        input: delta,
        model,
        output: isLast ? parsed.output : [],
        usage: isLast ? parsed.usage : null,
        sseEvents: null,
        streamed,
        streamTruncated,
      };
    case 'gemini':
      return {
        ...parsed,
        contents: delta,
        model,
        candidates: isLast ? parsed.candidates : [],
        usage: isLast ? parsed.usage : null,
        sseEvents: null,
        streamed,
        streamTruncated,
      };
    case 'ollama':
      return {
        ...parsed,
        messages: delta,
        model,
        responseMessage: isLast ? parsed.responseMessage : null,
        usage: isLast ? parsed.usage : null,
        sseEvents: null,
        streamed,
        streamTruncated,
      };
  }
}

function renderConversationByKind(parsed: ConversationParsed) {
  switch (parsed.kind) {
    case 'anthropic':
      return <AnthropicConversationView parsed={parsed} />;
    case 'openai':
      return <OpenAiConversationView parsed={parsed} />;
    case 'openai_responses':
      return <OpenAiResponsesConversationView parsed={parsed} />;
    case 'gemini':
      return <GeminiConversationView parsed={parsed} />;
    case 'ollama':
      return <OllamaConversationView parsed={parsed} />;
  }
}

/** A labelled divider between turns in the growing thread. */
function TurnDivider({ label }: { label: string }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, my: 0.75 }}>
      <Divider sx={{ flex: 1 }} />
      <Chip
        label={label}
        size="small"
        variant="outlined"
        color="primary"
        sx={{ height: 18, fontSize: '0.6rem', fontWeight: 600 }}
      />
      <Divider sx={{ flex: 1 }} />
    </Box>
  );
}

/**
 * Render one grouped conversation as a single growing thread. A single-turn group
 * renders exactly as the underlying per-provider Conversation view does today; a
 * multi-turn group shows each turn's delta under a "Turn N" divider.
 */
export function GroupedConversationView<T>({ group }: { group: ConversationGroup<T> }) {
  const { turns } = group;
  if (turns.length === 0) return null;

  // A single-turn group is just the one request — render it unchanged.
  if (turns.length === 1) {
    const only = turns[0]!.parsed;
    return isConversationParsed(only) ? renderConversationByKind(only) : null;
  }

  return (
    <Box data-testid="grouped-conversation" sx={{ display: 'flex', flexDirection: 'column' }}>
      {turns.map((turn, i) => {
        const isFirst = i === 0;
        const isLast = i === turns.length - 1;
        // An exact duplicate resend adds nothing new — skip it unless it carries
        // the final response (the last turn always renders so the reply shows).
        if (turn.newMessages.length === 0 && !isLast) return null;
        if (!isConversationParsed(turn.parsed)) return null;
        const synthesized = turnParsed(turn.parsed, turn.newMessages, isFirst, isLast);
        return (
          <Box key={i}>
            <TurnDivider label={isLast ? `Turn ${i + 1} · response` : `Turn ${i + 1}`} />
            {renderConversationByKind(synthesized)}
          </Box>
        );
      })}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Scripted turn data structures (from httpLlmResponse.conversationPredicates)
// ---------------------------------------------------------------------------

export interface ScriptedTurn {
  turnIndex: number;
  predicates: Record<string, unknown>;
  response: {
    text?: string;
    toolCalls?: Array<{ name: string; arguments?: string }>;
    stopReason?: string;
    streaming?: boolean;
  };
  scenarioState: string;
  newScenarioState: string;
  // The conversation this turn belongs to. Multiple conversations are flattened
  // into a single `turns` array; the panel renders a separator chip whenever
  // this changes between adjacent turns.
  scenarioName?: string;
}

// ---------------------------------------------------------------------------
// ScriptedTurnsPanel — renders the scripted turn sequence
// ---------------------------------------------------------------------------

export function ScriptedTurnsPanel({ turns }: { turns: ScriptedTurn[] }) {
  if (turns.length === 0) return null;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, p: 1 }}>
      <Typography
        variant="subtitle2"
        sx={{
          fontWeight: 700,
          textTransform: 'uppercase',
          letterSpacing: 0.5,
          fontSize: '0.7rem',
          color: 'text.secondary',
          textAlign: 'center',
          mb: 0.5,
        }}
      >
        Scripted Conversation Turns
      </Typography>

      {turns.map((turn, i) => {
        const predicateEntries = Object.entries(turn.predicates).filter(
          ([, v]) => v !== undefined && v !== null,
        );
        const hasPredicates = predicateEntries.length > 0;
        // tsconfig has noUncheckedIndexedAccess, so the array index returns T | undefined;
        // the i > 0 guard is enough at runtime but TypeScript needs an explicit narrowing.
        const previousScenario = i > 0 ? turns[i - 1]?.scenarioName : undefined;
        const isFirstOfConversation = i === 0 || turn.scenarioName !== previousScenario;
        const conversationHeader = isFirstOfConversation && turn.scenarioName
          ? turn.scenarioName.replace(/^__llm_conv_/, 'conv ').replace(/__iso=.*/, '')
          : null;

        return (
          <Fragment key={`scripted-turn-${i}`}>
            {conversationHeader && (
              <Typography
                variant="overline"
                sx={{ fontSize: '0.6rem', color: 'text.secondary', textAlign: 'center', mt: i > 0 ? 1 : 0 }}
              >
                {conversationHeader}
              </Typography>
            )}
          <Box
            sx={{
              border: 1,
              borderColor: 'divider',
              borderRadius: 2,
              p: 1.5,
            }}
          >
            {/* Turn header with state transition */}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 1 }}>
              <Chip
                label={`Turn ${turn.turnIndex}`}
                size="small"
                color="primary"
                variant="outlined"
                sx={{ height: 20, fontSize: '0.65rem', fontWeight: 600 }}
              />
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <Chip
                  label={turn.scenarioState}
                  size="small"
                  variant="outlined"
                  sx={{ height: 18, fontSize: '0.6rem' }}
                />
                <ArrowForwardIcon sx={{ fontSize: '0.75rem', color: 'text.secondary' }} />
                <Chip
                  label={turn.newScenarioState}
                  size="small"
                  variant="outlined"
                  sx={{ height: 18, fontSize: '0.6rem' }}
                />
              </Box>
            </Box>

            {/* Predicates */}
            {hasPredicates && (
              <Box sx={{ mb: 1 }}>
                <Typography
                  variant="caption"
                  sx={{ fontSize: '0.6rem', color: 'text.secondary', fontWeight: 600, textTransform: 'uppercase' }}
                >
                  When
                </Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.25 }}>
                  {predicateEntries.map(([key, value]) => (
                    <Tooltip key={key} title={`${key}: ${String(value)}`}>
                      <Chip
                        label={`${key}: ${String(value)}`}
                        size="small"
                        variant="outlined"
                        color="info"
                        sx={{ height: 18, fontSize: '0.6rem', maxWidth: 200, '& .MuiChip-label': { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }}
                      />
                    </Tooltip>
                  ))}
                </Box>
              </Box>
            )}

            <Divider sx={{ my: 0.5 }} />

            {/* Response */}
            <Box>
              <Typography
                variant="caption"
                sx={{ fontSize: '0.6rem', color: 'text.secondary', fontWeight: 600, textTransform: 'uppercase' }}
              >
                Response
              </Typography>
              {turn.response.text && (
                <Typography
                  variant="body2"
                  sx={{
                    fontFamily: monospaceFontFamily,
                    fontSize: MONO_FONT_SIZE,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    mt: 0.25,
                  }}
                >
                  {turn.response.text}
                </Typography>
              )}
              {turn.response.toolCalls && turn.response.toolCalls.length > 0 && (
                <Box sx={{ mt: 0.5, display: 'flex', flexDirection: 'column', gap: 0.25 }}>
                  {turn.response.toolCalls.map((tc, j) => (
                    <Box key={j} sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <BuildIcon sx={{ fontSize: '0.75rem', color: 'secondary.main' }} />
                      <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontSize: '0.65rem', fontWeight: 600, color: 'secondary.main' }}>
                        {tc.name}
                      </Typography>
                      {tc.arguments && (
                        <Typography variant="caption" sx={{ fontFamily: monospaceFontFamily, fontSize: '0.6rem', color: 'text.secondary' }}>
                          ({tc.arguments.length > 40 ? tc.arguments.substring(0, 40) + '...' : tc.arguments})
                        </Typography>
                      )}
                    </Box>
                  ))}
                </Box>
              )}
              {turn.response.stopReason && (
                <Chip
                  label={`Stop: ${turn.response.stopReason}`}
                  size="small"
                  variant="outlined"
                  sx={{ height: 18, fontSize: '0.6rem', mt: 0.5 }}
                />
              )}
              {turn.response.streaming && (
                <Chip
                  label="Streamed"
                  size="small"
                  color="info"
                  variant="outlined"
                  sx={{ height: 18, fontSize: '0.6rem', mt: 0.5, ml: 0.5 }}
                />
              )}
            </Box>
          </Box>
          </Fragment>
        );
      })}
    </Box>
  );
}
