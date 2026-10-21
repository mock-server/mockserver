import { Fragment, memo, useDeferredValue, useState, useMemo } from 'react';
import type React from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Checkbox from '@mui/material/Checkbox';
import Tooltip from '@mui/material/Tooltip';
import IconButton from '@mui/material/IconButton';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import BoltIcon from '@mui/icons-material/Bolt';
import EditIcon from '@mui/icons-material/Edit';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import ScienceOutlinedIcon from '@mui/icons-material/ScienceOutlined';
import type { JsonListItem as JsonListItemType } from '../types';
import type { ConversationPredicates } from '../lib/llmTraffic';
import JsonViewer from './JsonViewer';
import TruncatedBodyNotice from './TruncatedBodyNotice';
import { useLoadFullRow } from '../hooks/useLoadFullRow';
import DescriptionDisplay from './DescriptionDisplay';
import PredicatePills from './PredicatePills';
import { monospaceFontFamily, transitions } from '../theme';
import { formatRowTime } from '../lib/logEntryTime';

interface JsonListItemProps {
  item: JsonListItemType;
  index: number;
  /**
   * Optional turn-position info for LLM scenario expectations. When present a
   * `[Turn N of M]` chip is rendered alongside the predicate pills so the user
   * sees the canonical sequence even when the turn lacks an explicit
   * `turnIndex` predicate (e.g. matches via `containsToolResultFor`).
   */
  turnPosition?: { position: number; total: number };
  /**
   * Controlled expand state, lifted to the panel so it survives the row being
   * unmounted while scrolled out of a virtualized list. When omitted the row
   * falls back to its own internal state (standalone use / tests).
   */
  expanded?: boolean;
  onToggleExpand?: (key: string) => void;
  /**
   * When provided, an "Edit" icon button is rendered (revealed on row hover)
   * that hands this expectation off to the Composer. Only wired up by the
   * Active Expectations panel; omitted for Received / Proxied request rows.
   */
  onEdit?: (item: JsonListItemType) => void;
  /**
   * When provided, a "Duplicate" icon button is rendered (revealed on row
   * hover) that loads a COPY of this expectation into the Composer with its id
   * stripped, so saving creates a brand-new expectation. Only wired up by the
   * Active Expectations panel.
   */
  onDuplicate?: (item: JsonListItemType) => void;
  /**
   * When provided, a "Delete" icon button is rendered (revealed on row hover)
   * that removes this single expectation. Only wired up by the Active
   * Expectations panel.
   */
  onDelete?: (item: JsonListItemType) => void;
  /**
   * When provided, a "Test" icon button is rendered (revealed on row hover)
   * that opens the matcher playground seeded with this expectation's JSON, so
   * the user can dry-run what it matches. Only wired up by the Active
   * Expectations panel.
   */
  onTest?: (item: JsonListItemType) => void;
  /**
   * When provided, a leading selection checkbox is rendered (bulk-select mode).
   * `selected` reflects its checked state and the callback receives this row's
   * key. Only wired up by the Active Expectations panel's bulk-actions mode.
   */
  onSelectToggle?: (key: string) => void;
  selected?: boolean;
}

import { PROVIDER_DISPLAY } from '../lib/clientFilters';

// Re-use the canonical provider display map from clientFilters
const PROVIDER_LABELS = PROVIDER_DISPLAY;

interface LlmBadgeInfo {
  provider: string;
  model: string | null;
  textPreview: string | null;
  streaming: boolean;
  toolCallCount: number;
  isEmbedding: boolean;
  isStateful: boolean;
  turnIndex: number | null;
  isolationLabel: string | null;
  predicates: ConversationPredicates | null;
}

/**
 * Pull the request method + path out of the item value. Works for all three
 * panels: Active Expectations (httpRequest is the matcher), Received Requests
 * (the bare request was wrapped under httpRequest by DashboardWebSocketHandler),
 * and Proxied Requests (same wrapping). Returns null when the value has no
 * recognisable request shape (e.g. raw OpenAPI matchers) so the caller can
 * fall back to the opaque DescriptionDisplay.
 */
function extractRequestParts(value: Record<string, unknown>): { method: string | null; path: string | null } | null {
  const req = value['httpRequest'];
  if (!req || typeof req !== 'object') return null;
  const r = req as Record<string, unknown>;
  // method/path can be plain strings OR NottableString objects { value, not }
  const readField = (field: string): string | null => {
    const raw = r[field];
    if (typeof raw === 'string') return raw;
    if (raw && typeof raw === 'object' && 'value' in (raw as Record<string, unknown>)) {
      const v = (raw as Record<string, unknown>)['value'];
      return typeof v === 'string' ? v : null;
    }
    return null;
  };
  const method = readField('method');
  const path = readField('path');
  if (!method && !path) return null;
  return { method, path };
}

// A received request with no method or path (a DNS query, a binary message) is described by the
// server as its kind and a summary, which take the method and path slots. The server leaves out
// `json: false`, so only `json: true` marks the other object form.
function describedRequestParts(description: unknown): { method: string | null; path: string | null } | null {
  if (!description || typeof description !== 'object') return null;
  const d = description as Record<string, unknown>;
  if (d['json'] === true || typeof d['first'] !== 'string' || typeof d['second'] !== 'string') return null;
  return { method: d['first'], path: d['second'] };
}

// Top-level action keys an expectation may carry, mapped to a short human
// label. Used to give non-HTTP expectations (gRPC / spec-derived matchers with
// no method+path) a meaningful row summary instead of repeating the id.
const ACTION_LABELS: ReadonlyArray<readonly [string, string]> = [
  ['httpResponse', 'HTTP response'],
  ['httpResponseTemplate', 'response template'],
  ['httpResponseClassCallback', 'response callback'],
  ['httpResponseObjectCallback', 'response callback'],
  ['httpForward', 'forward'],
  ['httpForwardTemplate', 'forward template'],
  ['httpForwardClassCallback', 'forward callback'],
  ['httpForwardObjectCallback', 'forward callback'],
  ['httpOverrideForwardedRequest', 'override forward'],
  ['httpError', 'error'],
  ['httpLlmResponse', 'LLM response'],
];

/**
 * Derive a short human label for an expectation that lacks a recognisable
 * method+path summary (e.g. gRPC or OpenAPI-derived matchers). Prefers the
 * top-level action key; falls back to a generic "expectation". Never returns
 * the id, so the row never renders "<id>: <id>".
 */
function extractActionLabel(value: Record<string, unknown>): string {
  for (const [key, label] of ACTION_LABELS) {
    if (value[key] != null) return label;
  }
  return 'expectation';
}

/**
 * Whether `item.description` is just the expectation id (the server falls back
 * to the id as the description for non-HTTP matchers, which would render as
 * "<id>: <id>"). When true the caller should show a derived label instead.
 */
function descriptionIsJustId(description: unknown, value: Record<string, unknown>): boolean {
  const id = value['id'];
  if (typeof id !== 'string' || typeof description !== 'string') return false;
  return description.trim() === id;
}

/**
 * Read the expectation's match `priority` (higher wins; defaults to 0 when
 * absent). Returns null when the value carries no numeric priority field so the
 * caller can suppress the chip for non-expectation rows (Received / Proxied
 * requests, which have no priority).
 */
export function extractPriority(value: Record<string, unknown>): number | null {
  const raw = value['priority'];
  return typeof raw === 'number' ? raw : null;
}

function extractIsolationLabel(scenarioName: string | undefined): string | null {
  if (!scenarioName) return null;
  const isoMatch = /__iso=([^_]+):(.+)$/.exec(scenarioName);
  if (!isoMatch) return null;
  return `${isoMatch[1]}:${isoMatch[2]}`;
}

function extractLlmBadge(value: Record<string, unknown>): LlmBadgeInfo | null {
  const llm = value['httpLlmResponse'] as Record<string, unknown> | undefined;
  if (!llm) return null;

  const providerRaw = llm['provider'] as string | undefined;
  if (!providerRaw) return null;

  const provider = PROVIDER_LABELS[providerRaw] ?? providerRaw;
  const model = (llm['model'] as string | undefined) ?? null;

  let textPreview: string | null = null;
  const completion = llm['completion'] as Record<string, unknown> | undefined;
  const streaming = (llm['streaming'] as boolean | undefined) === true;
  let toolCallCount = 0;
  if (completion) {
    const text = completion['text'] as string | undefined;
    if (text) {
      textPreview = text.length > 80 ? text.substring(0, 80) + '…' : text;
    }
    const toolCalls = completion['toolCalls'] as unknown[] | undefined;
    if (toolCalls) {
      toolCallCount = toolCalls.length;
    }
  }

  const isEmbedding = 'embedding' in llm;

  const predicatesObj = llm['conversationPredicates'] as ConversationPredicates | undefined;
  const isStateful = predicatesObj != null;
  const turnIndex = predicatesObj?.turnIndex ?? null;

  // We are already past the `if (!llm) return null` guard, so the scenario name
  // can only be read from the LLM action itself. The httpResponseObjectCallback
  // path the badge previously consulted was unreachable.
  const scenarioName = llm['scenarioName'] as string | undefined;
  const isolationLabel = extractIsolationLabel(scenarioName);

  return {
    provider,
    model,
    textPreview,
    streaming,
    toolCallCount,
    isEmbedding,
    isStateful,
    turnIndex,
    isolationLabel,
    predicates: predicatesObj ?? null,
  };
}

/**
 * Build a concise human-readable summary of a top-level `chaos` block on an
 * expectation. Returns null when no chaos profile is present.
 */
function extractChaosSummary(value: Record<string, unknown>): string | null {
  const raw = value['chaos'];
  if (!raw || typeof raw !== 'object') return null;
  const c = raw as Record<string, unknown>;
  const parts: string[] = [];
  if (typeof c['errorStatus'] === 'number') {
    const prob = typeof c['errorProbability'] === 'number' ? c['errorProbability'] as number : null;
    const probStr = prob != null ? ` @${Math.round(prob * 100)}%` : '';
    parts.push(`${c['errorStatus']}${probStr}`);
  }
  if (c['latency'] && typeof c['latency'] === 'object') {
    const lat = c['latency'] as Record<string, unknown>;
    if (typeof lat['value'] === 'number') {
      const unit = typeof lat['timeUnit'] === 'string' ? (lat['timeUnit'] as string).toLowerCase() : 'ms';
      parts.push(`${lat['value']}${unit.slice(0, 2)}`);
    }
  }
  if (typeof c['succeedFirst'] === 'number' || typeof c['failRequestCount'] === 'number') {
    const sf = typeof c['succeedFirst'] === 'number' ? c['succeedFirst'] as number : 0;
    const fc = typeof c['failRequestCount'] === 'number' ? `${c['failRequestCount']}` : '∞';
    parts.push(`window ${sf}+${fc}`);
  }
  return parts.length > 0 ? parts.join(', ') : 'enabled';
}


/**
 * The expectation's free-form `metadata` object as display rows, or null when absent.
 * Metadata never affects matching; strings render verbatim, every other JSON value
 * (number, boolean, null, array, object) as compact JSON.
 */
function extractMetadataEntries(value: Record<string, unknown>): Array<[string, string]> | null {
  const raw = value['metadata'];
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const entries = Object.entries(raw as Record<string, unknown>).map(
    ([k, v]): [string, string] => [k, typeof v === 'string' ? v : JSON.stringify(v)],
  );
  return entries.length > 0 ? entries : null;
}

function JsonListItem({ item, index, turnPosition, expanded: expandedProp, onToggleExpand, onEdit, onDuplicate, onDelete, onTest, onSelectToggle, selected }: JsonListItemProps) {
  const [internalExpanded, setInternalExpanded] = useState(false);
  const loadFullRow = useLoadFullRow();
  const expanded = expandedProp ?? internalExpanded;
  const handleToggle = () => {
    if (onToggleExpand) onToggleExpand(item.key);
    else setInternalExpanded((prev) => !prev);
  };
  // The chevron + row layout react to `expanded` urgently (instant click
  // feedback), but the expensive expanded JSON tree is gated on the deferred
  // value so building it (the @uiw/react-json-view subtree) happens in a
  // non-blocking follow-up render instead of stalling the click.
  const showBody = useDeferredValue(expanded);
  const llmBadge = useMemo(() => extractLlmBadge(item.value), [item.value]);
  const requestParts = useMemo(
    () => extractRequestParts(item.value) ?? describedRequestParts(item.description),
    [item.value, item.description],
  );
  const chaosSummary = useMemo(() => extractChaosSummary(item.value), [item.value]);
  const priority = useMemo(() => extractPriority(item.value), [item.value]);
  const metadataEntries = useMemo(() => extractMetadataEntries(item.value), [item.value]);

  return (
    <Box
      sx={{
        position: 'relative',
        py: 0.5,
        px: 1,
        borderBottom: 1,
        borderColor: 'divider',
        bgcolor: selected ? 'action.selected' : undefined,
        '&:hover .copy-btn': { opacity: 1 },
        '&:hover .row-actions': { opacity: 1 },
        // Keep the actions reachable for keyboard users even without hover.
        '&:focus-within .row-actions': { opacity: 1 },
        '&:last-child': { borderBottom: 0 },
      }}
    >
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 0.25,
          cursor: 'pointer',
          userSelect: 'none',
        }}
        onClick={handleToggle}
      >
        {/* First row: expand toggle + position + (optional) expectation id +
            method on the left, path on the right. The expectation id is only
            present for Active Expectations entries (Received / Proxied request
            items have synthetic keys like "<id>_request" / "<id>_proxied" and
            no value.id field). */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, minWidth: 0 }}>
          {onSelectToggle && (
            // Leading bulk-select checkbox (Active Expectations bulk-actions
            // mode). stopPropagation keeps ticking it from toggling the row's
            // expand state.
            <Checkbox
              size="small"
              checked={!!selected}
              onClick={(e) => e.stopPropagation()}
              onChange={() => onSelectToggle(item.key)}
              slotProps={{ input: { 'aria-label': `Select expectation ${index}` } }}
              sx={{ p: 0.25, flexShrink: 0 }}
            />
          )}
          {/* The chevron IconButton is the accessible/keyboard control; the row
              stays click-to-toggle for the mouse but is not itself a button. */}
          <IconButton
            size="small"
            aria-label={expanded ? 'Collapse' : 'Expand'}
            aria-expanded={expanded}
            onClick={(e) => { e.stopPropagation(); handleToggle(); }}
            sx={{ p: 0, '& .MuiSvgIcon-root': { fontSize: '1rem' } }}
          >
            {expanded ? <ExpandMoreIcon /> : <ChevronRightIcon />}
          </IconButton>
          <Box
            component="span"
            title={item.timestamp ?? undefined}
            sx={{ fontFamily: monospaceFontFamily, fontSize: '0.8em', color: 'text.secondary', minWidth: 24, whiteSpace: 'nowrap' }}
          >
            {/* The time it happened, not a position in the list. These sections are
                a capped live window, so an ordinal renumbers on every push and
                cannot be referred to; the timestamp is stable and lines up with
                the Log Messages panel. Falls back to the ordinal for lists that
                carry no timestamp (expectations are a set, not a stream). */}
            {item.timestamp ? formatRowTime(item.timestamp) : index}
          </Box>
          {typeof item.value['id'] === 'string' && (
            <Box
              component="span"
              sx={{
                fontFamily: monospaceFontFamily,
                fontSize: '0.85em',
                fontWeight: 600,
                whiteSpace: 'nowrap',
              }}
            >
              {item.value['id'] as string}:
            </Box>
          )}
          {requestParts ? (
            <>
              <Box
                component="span"
                sx={{ fontFamily: monospaceFontFamily, fontSize: '0.85em', fontWeight: 600, minWidth: 50 }}
              >
                {requestParts.method ?? '·'}
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }} />
              <Box
                component="span"
                sx={{
                  fontFamily: monospaceFontFamily,
                  fontSize: '0.85em',
                  color: 'text.secondary',
                  textAlign: 'right',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  maxWidth: '70%',
                }}
                title={requestParts.path ?? undefined}
              >
                {requestParts.path ?? '·'}
              </Box>
            </>
          ) : descriptionIsJustId(item.description, item.value) ? (
            // Non-HTTP expectation (e.g. gRPC / spec-derived) whose server-side
            // description is just the id. Show a derived action label instead of
            // repeating the id (which produced the "<id>: <id>" display bug).
            <>
              <Box
                component="span"
                sx={{ fontSize: '0.8em', color: 'text.secondary', fontStyle: 'italic' }}
              >
                {extractActionLabel(item.value)}
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }} />
            </>
          ) : item.description ? (
            <>
              <DescriptionDisplay description={item.description} />
              <Box sx={{ flex: 1, minWidth: 0 }} />
            </>
          ) : (
            <Box sx={{ flex: 1, minWidth: 0 }} />
          )}
          {priority != null && (
            // Priority chip for Active Expectations: higher priority wins when
            // several mocks match the same request. Always shown (incl. the
            // default 0) so the match order is visible at a glance; emphasised
            // when non-default. Stays visible (not hover-gated) so it can be
            // scanned and sorted against.
            <Tooltip title={`Match priority ${priority} (higher wins)`}>
              <Chip
                label={`P${priority}`}
                size="small"
                color={priority !== 0 ? 'primary' : 'default'}
                variant="outlined"
                sx={{ height: 18, ml: 0.5, flexShrink: 0, fontSize: (t) => t.typography.caption.fontSize }}
              />
            </Tooltip>
          )}
          {(onEdit || onDuplicate || onDelete || onTest) && (
            // Per-row Test / Edit / Duplicate / Delete actions for Active Expectations. Hidden until
            // the row is hovered (see the parent's `&:hover .row-actions` rule)
            // so they don't clutter the dense list. stopPropagation keeps a click
            // from toggling the row's expand state.
            <Box
              className="row-actions"
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.25,
                ml: 0.5,
                flexShrink: 0,
                opacity: 0,
                transition: transitions.fast,
              }}
            >
              {onTest && (
                <Tooltip title="Test what this expectation matches">
                  <IconButton
                    size="small"
                    aria-label="Test expectation"
                    onClick={(e) => { e.stopPropagation(); onTest(item); }}
                    sx={{ p: 0.25, '& .MuiSvgIcon-root': { fontSize: '1rem' } }}
                  >
                    <ScienceOutlinedIcon />
                  </IconButton>
                </Tooltip>
              )}
              {onEdit && (
                <Tooltip title="Edit in Composer">
                  <IconButton
                    size="small"
                    aria-label="Edit expectation"
                    onClick={(e) => { e.stopPropagation(); onEdit(item); }}
                    sx={{ p: 0.25, '& .MuiSvgIcon-root': { fontSize: '1rem' } }}
                  >
                    <EditIcon />
                  </IconButton>
                </Tooltip>
              )}
              {onDuplicate && (
                <Tooltip title="Duplicate as new expectation">
                  <IconButton
                    size="small"
                    aria-label="Duplicate expectation"
                    onClick={(e) => { e.stopPropagation(); onDuplicate(item); }}
                    sx={{ p: 0.25, '& .MuiSvgIcon-root': { fontSize: '1rem' } }}
                  >
                    <ContentCopyIcon />
                  </IconButton>
                </Tooltip>
              )}
              {onDelete && (
                <Tooltip title="Delete expectation">
                  <IconButton
                    size="small"
                    color="error"
                    aria-label="Delete expectation"
                    onClick={(e) => { e.stopPropagation(); onDelete(item); }}
                    sx={{ p: 0.25, '& .MuiSvgIcon-root': { fontSize: '1rem' } }}
                  >
                    <DeleteOutlineIcon />
                  </IconButton>
                </Tooltip>
              )}
            </Box>
          )}
        </Box>
        {/* Second row: LLM badge chips below the matcher so wide chip sets wrap
            cleanly inside the panel instead of forcing horizontal scroll. */}
        {llmBadge && (() => {
          // Build optional signal chips (provider chip is mandatory and renders separately).
          const optionalChips: Array<{ key: string; element: React.ReactElement; tooltipLabel: string }> = [];
          if (llmBadge.streaming) {
            optionalChips.push({
              key: 'stream',
              tooltipLabel: 'streaming',
              element: (
                <Chip
                  icon={<BoltIcon sx={{ fontSize: '0.8rem' }} />}
                  label="stream"
                  size="small"
                  variant="outlined"
                  sx={{ height: 20, fontSize: (t) => t.typography.caption.fontSize }}
                />
              ),
            });
          }
          if (llmBadge.toolCallCount > 0) {
            const lbl = `${llmBadge.toolCallCount} tool${llmBadge.toolCallCount > 1 ? 's' : ''}`;
            optionalChips.push({
              key: 'tools',
              tooltipLabel: lbl,
              element: (
                <Chip
                  label={lbl}
                  size="small"
                  variant="outlined"
                  sx={{ height: 20, fontSize: (t) => t.typography.caption.fontSize }}
                />
              ),
            });
          }
          if (llmBadge.isEmbedding) {
            optionalChips.push({
              key: 'embedding',
              tooltipLabel: 'embedding',
              element: (
                <Chip
                  label="embedding"
                  size="small"
                  variant="outlined"
                  sx={{ height: 20, fontSize: (t) => t.typography.caption.fontSize }}
                />
              ),
            });
          }
          if (llmBadge.isStateful) {
            // Prefer the canonical "Turn N of M" position computed from
            // scenario state ordering over the predicate-derived turnIndex,
            // since the latter is absent on turns that match via other
            // predicates (e.g. containsToolResultFor).
            const lbl = turnPosition
              ? `turn ${turnPosition.position} of ${turnPosition.total}`
              : llmBadge.turnIndex != null
                ? `turn ${llmBadge.turnIndex}`
                : 'stateful';
            optionalChips.push({
              key: 'stateful',
              tooltipLabel: lbl,
              element: (
                <Chip
                  label={lbl}
                  size="small"
                  color="info"
                  variant="outlined"
                  sx={{ height: 20, fontSize: (t) => t.typography.caption.fontSize }}
                />
              ),
            });
          }
          if (llmBadge.isolationLabel) {
            const lbl = `iso=${llmBadge.isolationLabel}`;
            optionalChips.push({
              key: 'isolation',
              tooltipLabel: lbl,
              element: (
                <Chip
                  label={lbl}
                  size="small"
                  variant="outlined"
                  sx={{ height: 20, fontSize: (t) => t.typography.caption.fontSize }}
                />
              ),
            });
          }

          // Cap at 3 visible optional chips (4 total including the mandatory provider chip).
          // Surplus is collapsed into a "+N more" chip with a tooltip listing them.
          const MAX_OPTIONAL = 3;
          const visibleChips = optionalChips.slice(0, MAX_OPTIONAL);
          const hiddenChips = optionalChips.slice(MAX_OPTIONAL);

          return (
          // pl matches the cumulative width of [expand icon] + gap + [index]
          // + gap so the chips visually start at the same x-coordinate as the
          // expectation id (when present) or the method (when not).
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, pl: 6, flexWrap: 'wrap' }}>
            <Chip
              icon={<SmartToyIcon sx={{ fontSize: '0.85rem' }} />}
              label={`LLM Response – ${llmBadge.provider}${llmBadge.model ? ' / ' + llmBadge.model : ''}`}
              size="small"
              color="secondary"
              variant="outlined"
              sx={{ height: 20, fontSize: (t) => t.typography.caption.fontSize }}
            />
            {visibleChips.map((c) => <Fragment key={c.key}>{c.element}</Fragment>)}
            {hiddenChips.length > 0 && (
              <Tooltip title={hiddenChips.map((c) => c.tooltipLabel).join(', ')}>
                <Chip
                  label={`+${hiddenChips.length} more`}
                  size="small"
                  variant="outlined"
                  sx={{ height: 20, fontSize: (t) => t.typography.caption.fontSize }}
                />
              </Tooltip>
            )}
            {llmBadge.textPreview && (
              <Box
                component="span"
                sx={{
                  fontFamily: monospaceFontFamily,
                  fontSize: '0.7em',
                  color: 'text.secondary',
                  maxWidth: 200,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {llmBadge.textPreview}
              </Box>
            )}
          </Box>
          );
        })()}
        {/* Chaos profile summary chip — shown for any expectation (standard
            or LLM) that has a top-level `chaos` block. */}
        {chaosSummary && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, pl: 6 }}>
            <Chip
              label={`Chaos: ${chaosSummary}`}
              size="small"
              color="warning"
              variant="outlined"
              sx={{ height: 20, fontSize: (t) => t.typography.caption.fontSize }}
            />
          </Box>
        )}
      </Box>
      {showBody && (
        // pl: 6 matches the second-row chip indent so the expanded JSON
        // body lines up with the expectation id (Active Expectations) or
        // the method (Received / Proxied requests, where no id is shown).
        <Box sx={{ pl: 6, pt: 0.5 }}>
          {llmBadge?.predicates && (
            <Box sx={{ mb: 1 }}>
              <PredicatePills predicates={llmBadge.predicates} />
            </Box>
          )}
          {(item.truncatedBodies?.httpRequest ?? item.truncatedBodies?.httpResponse) && (
            <TruncatedBodyNotice
              marker={item.truncatedBodies?.httpRequest ?? item.truncatedBodies?.httpResponse ?? { logEntryId: '', part: 'request', originalLength: 0, shownLength: 0 }}
              onLoad={async () => {
                await loadFullRow(item);
              }}
            />
          )}
          {item.truncatedExpectation && (
            // Edit, Duplicate and Test load the whole expectation, so only this view is shortened.
            <TruncatedBodyNotice
              marker={{
                logEntryId: '',
                part: 'expectation',
                originalLength: item.truncatedExpectation.originalLength,
                shownLength: item.truncatedExpectation.shownLength,
                loadable: false,
              }}
            />
          )}
          {metadataEntries && (
            <Box sx={{ mb: 1, fontSize: (t) => t.typography.caption.fontSize }}>
              <Box sx={{ fontWeight: 600, color: 'text.secondary' }}>Metadata</Box>
              <Box
                component="dl"
                aria-label="Metadata"
                sx={{ display: 'grid', gridTemplateColumns: 'max-content minmax(0, 1fr)', columnGap: 1, rowGap: 0.25, m: 0 }}
              >
                {metadataEntries.map(([k, v]) => (
                  <Fragment key={k}>
                    <Box component="dt" sx={{ fontFamily: monospaceFontFamily, fontWeight: 600 }}>{k}</Box>
                    <Box
                      component="dd"
                      title={v}
                      sx={{ m: 0, fontFamily: monospaceFontFamily, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                    >
                      {v}
                    </Box>
                  </Fragment>
                ))}
              </Box>
            </Box>
          )}
          {/* a shortened expectation is not offered for copying: the copy would be pasted or saved shortened */}
          <JsonViewer data={item.value} collapsed={1} enableClipboard={!item.truncatedExpectation} />
        </Box>
      )}
    </Box>
  );
}

// Memoized: rows are re-rendered once per second by the WebSocket push. The
// store now preserves the `item` reference for unchanged entries (see
// reconcileByKey), so default shallow prop comparison lets unchanged rows skip
// the whole render — including the expensive expanded JsonViewer subtree.
export default memo(JsonListItem);
