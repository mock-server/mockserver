import { useMemo, useState, useCallback, useEffect } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import Button from '@mui/material/Button';
import Collapse from '@mui/material/Collapse';
import SearchIcon from '@mui/icons-material/Search';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useDashboardStore } from '../store';
import { useAutoLoadLlmRows, useLoadFullRow } from '../hooks/useLoadFullRow';
import { hasTruncatedBodies } from '../lib/fullBody';
import TruncatedBodyNotice from './TruncatedBodyNotice';
import type { JsonListItem } from '../types';
import { groupBySession, parseIsolationSource, shortenScenarioName, type Session, type SessionRequest } from '../lib/sessionGrouping';
import {
  getModelLabel,
  getTokenSummary,
  getNumericTokens,
  summarizeTraffic,
  groupConversationTurns,
  type ParsedTraffic,
} from '../lib/llmTraffic';
import { estimateCostUsd } from '../lib/llmPricing';
import {
  AnthropicConversationView,
  OpenAiConversationView,
  GroupedConversationView,
} from './ConversationView';
import AgentRunGraph from './AgentRunGraph';
import { CompareRunsBody } from './CompareRunsDialog';
import { monospaceFontFamily, transitions } from '../theme';
import { useHeldItems } from '../hooks/useHeldItems';
import { combineTraffic } from '../lib/combineTraffic';

// Matches Panel's AT_TOP_THRESHOLD_PX: absorbs HiDPI sub-pixel offsets and a few
// pixels of inertial overshoot, so a reader parked at the top stays recognised as
// being there.
const SESSION_AT_TOP_THRESHOLD_PX = 8;
// Stable key accessor for useHeldItems — module scope, so its identity never changes.
const sessionRequestKeyOf = (r: { key: string }) => r.key;

// ---------------------------------------------------------------------------
// Status colour for request chips
// ---------------------------------------------------------------------------

function statusColor(code: number | null): 'success' | 'error' | 'warning' | 'default' {
  if (code === null) return 'default';
  if (code >= 200 && code < 300) return 'success';
  if (code >= 400) return 'error';
  if (code >= 300) return 'warning';
  return 'default';
}

// ---------------------------------------------------------------------------
// Request chip
// ---------------------------------------------------------------------------

interface RequestChipProps {
  request: SessionRequest;
  turnIndex: number;
  selected: boolean;
  onClick: () => void;
}

function RequestChip({ request, turnIndex, selected, onClick }: RequestChipProps) {
  const label = `[${turnIndex}] ${request.method ?? '?'} ${request.path ?? '/'} → ${request.statusCode ?? '?'}`;
  return (
    <Tooltip title={label}>
      <Chip
        label={label}
        size="small"
        color={statusColor(request.statusCode)}
        variant={selected ? 'filled' : 'outlined'}
        onClick={onClick}
        sx={{
          height: 22,
          fontSize: '0.65rem',
          fontFamily: monospaceFontFamily,
          cursor: 'pointer',
          maxWidth: 220,
          transition: transitions.forProps(['background-color', 'border-color', 'box-shadow']),
          '& .MuiChip-label': { px: 0.75, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
        }}
      />
    </Tooltip>
  );
}

// ---------------------------------------------------------------------------
// Request detail (expanded below the chip timeline)
// ---------------------------------------------------------------------------

/** The same notice Traffic shows for a row whose body the server shortened, with Load Full Body. */
function ShortenedBodyNotice({ item }: { item: JsonListItem }) {
  const loadFullRow = useLoadFullRow();
  const load = useCallback(async () => {
    await loadFullRow(item);
  }, [loadFullRow, item]);
  const marker = item.truncatedBodies?.httpRequest ?? item.truncatedBodies?.httpResponse;
  return marker ? <TruncatedBodyNotice marker={marker} onLoad={load} /> : null;
}

function RequestDetail({ request }: { request: SessionRequest }) {
  const { parsed } = request;

  // A conversation parsed from the first part of a body would read as complete but be missing messages.
  if (hasTruncatedBodies(request.item)) {
    return (
      <Box sx={{ p: 1 }} data-testid="trace-request-shortened">
        <ShortenedBodyNotice item={request.item} />
        <Typography variant="caption" color="text.secondary">
          The conversation is shown once the full body is loaded.
        </Typography>
      </Box>
    );
  }

  if (parsed.kind === 'anthropic') {
    return <AnthropicConversationView parsed={parsed} />;
  }
  if (parsed.kind === 'openai') {
    return <OpenAiConversationView parsed={parsed} />;
  }

  // OpenAI Responses, Gemini, and Ollama do not yet have dedicated rich viewers
  // (a follow-up will add per-provider views). Fall back to model + token chip
  // header plus a scrollable pretty-printed JSON, NOT a truncated slice — a 500-
  // byte cap silently hides most real conversation content.
  const model = getModelLabel(parsed);
  const tokens = getTokenSummary(parsed);

  return (
    <Box sx={{ p: 1 }}>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 0.5 }}>
        {model && <Chip label={`Model: ${model}`} size="small" variant="outlined" sx={{ height: 18, fontSize: '0.6rem' }} />}
        {tokens && (
          <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.65rem' }}>
            {tokens}
          </Typography>
        )}
      </Box>
      <Box
        component="pre"
        sx={{
          fontFamily: monospaceFontFamily,
          fontSize: '0.65rem',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          maxHeight: 400,
          overflowY: 'auto',
          m: 0,
          p: 1,
          backgroundColor: 'action.hover',
          borderRadius: 1,
        }}
      >
        {JSON.stringify(request.item.value, null, 2)}
      </Box>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Session conversation — full chat transcript for the whole session, rendered
// with the same provider-specific Conversation views used in the Traffic tab.
// ---------------------------------------------------------------------------

const CONVERSATION_KINDS = new Set(['anthropic', 'openai', 'openai_responses', 'gemini', 'ollama']);

const PROVIDER_LABELS: Record<string, string> = {
  anthropic: 'Anthropic',
  openai: 'OpenAI',
  openai_responses: 'OpenAI Responses',
  gemini: 'Gemini',
  ollama: 'Ollama',
};
// Bedrock traffic is parsed into the Anthropic shape, so its label comes from the platform marker.
const providerLabel = (parsed: ParsedTraffic): string =>
  parsed.kind === 'anthropic' && parsed.platform === 'bedrock' ? 'Bedrock' : (PROVIDER_LABELS[parsed.kind] ?? parsed.kind);

/**
 * Session-level conversation view. A stateless agent CLI resends its whole
 * growing history on every turn, so rendering each captured request in full reads
 * as endless near-duplicate history. Instead we collapse a run of requests whose
 * message lists grow as prefixes of one another into ONE thread (via the pure
 * `groupConversationTurns`), and render it as a single conversation that GROWS —
 * each turn contributes only the new content it added, plus the final response.
 *
 * When the history diverges (edited prompt) or the lane mixes unrelated requests
 * or providers, grouping yields more than one thread; we render each separately
 * so nothing is hidden. The raw per-request data is always reachable via the
 * request chips above. Collapsed by default to keep the lanes compact.
 */
function SessionConversation({ requests }: { requests: SessionRequest[] }) {
  const [open, setOpen] = useState(false);
  const convRequests = useMemo(
    () => requests.filter((r) => CONVERSATION_KINDS.has(r.parsed.kind)),
    [requests],
  );
  // Requests with a shortened body stay out of the threads until loaded whole: parsed from a prefix
  // they would show a turn with messages missing as if it were complete.
  const wholeRequests = useMemo(() => convRequests.filter((r) => !hasTruncatedBodies(r.item)), [convRequests]);
  const shortenedRequests = useMemo(
    () => requests.flatMap((r, turnIndex) => (CONVERSATION_KINDS.has(r.parsed.kind) && hasTruncatedBodies(r.item) ? [{ request: r, turnIndex }] : [])),
    [requests],
  );
  // Group consecutive growing-history requests into threads. `requests` is already
  // sorted chronologically (oldest first) by the session grouping, which is the
  // order the prefix walk needs.
  const groups = useMemo(
    () =>
      groupConversationTurns(
        wholeRequests.map((r) => ({
          parsed: r.parsed,
          host: summarizeTraffic(r.item.value).host,
          data: r,
        })),
      ),
    [wholeRequests],
  );

  if (convRequests.length === 0) return null;

  const multipleThreads = groups.length > 1;
  const onlyGroup = groups.length === 1 ? groups[0]! : null;
  // Button label: a single multi-turn thread says how many turns collapsed; a
  // single one-shot is just "Conversation"; multiple threads says how many.
  const label = multipleThreads
    ? `Conversations (${groups.length})`
    : onlyGroup && onlyGroup.collapsed
      ? `Conversation (${onlyGroup.turns.length} turns)`
      : 'Conversation';

  return (
    <Box sx={{ px: 1.5, pb: 0.75 }}>
      <Button
        size="small"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        startIcon={open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
        sx={{ textTransform: 'none', fontSize: '0.7rem', color: 'text.secondary', px: 0.5, minWidth: 0 }}
      >
        {label}
      </Button>
      <Collapse in={open} unmountOnExit>
        <Box sx={{ mt: 0.5, maxHeight: 500, overflowY: 'auto' }}>
          {shortenedRequests.map(({ request, turnIndex }) => (
            <Box key={request.item.key} data-testid="trace-conversation-shortened">
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                [{turnIndex}] {request.method ?? '?'} {request.path ?? '/'} is left out of the conversation until its full body is loaded.
              </Typography>
              <ShortenedBodyNotice item={request.item} />
            </Box>
          ))}
          {multipleThreads && (
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ display: 'block', mb: 0.5, fontStyle: 'italic' }}
            >
              This lane holds {groups.length} separate conversation threads — the request
              history diverged or mixes unrelated requests. Each is shown below.
            </Typography>
          )}
          {groups.map((group, i) => (
            <Box key={group.key}>
              {multipleThreads && (
                <Typography
                  variant="overline"
                  sx={{ display: 'block', fontSize: '0.6rem', color: 'text.secondary', mt: i > 0 ? 1 : 0 }}
                >
                  {providerLabel(group.turns[0]!.parsed)} thread {i + 1}
                </Typography>
              )}
              <GroupedConversationView group={group} />
            </Box>
          ))}
        </Box>
      </Collapse>
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Per-session token/cost aggregation (pure, no network)
// ---------------------------------------------------------------------------

interface SessionUsage {
  totalInputTokens: number;
  totalOutputTokens: number;
  estimatedCostUsd: number | null;
}

function computeSessionUsage(requests: SessionRequest[]): SessionUsage | null {
  let totalIn = 0;
  let totalOut = 0;
  let hasCost = false;
  let totalCost = 0;

  for (const req of requests) {
    const tokens = getNumericTokens(req.parsed);
    if (tokens) {
      totalIn += tokens.inputTokens;
      totalOut += tokens.outputTokens;
      const model = getModelLabel(req.parsed);
      if (model && req.parsed.kind !== 'mcp' && req.parsed.kind !== 'generic') {
        const cost = estimateCostUsd(req.parsed.kind, model, tokens.inputTokens, tokens.outputTokens);
        if (cost !== null) {
          hasCost = true;
          totalCost += cost;
        }
      }
    }
  }

  if (totalIn === 0 && totalOut === 0) return null;
  return {
    totalInputTokens: totalIn,
    totalOutputTokens: totalOut,
    estimatedCostUsd: hasCost ? totalCost : null,
  };
}

function formatCost(usd: number): string {
  if (usd < 0.01) return `$${usd.toFixed(6)}`;
  if (usd < 1) return `$${usd.toFixed(4)}`;
  return `$${usd.toFixed(2)}`;
}

// ---------------------------------------------------------------------------
// Session lane
// ---------------------------------------------------------------------------

interface SessionLaneProps {
  session: Session;
  connectionParams: { host: string; port: string; secure: boolean };
  /** Reports whether this lane currently has a request expanded. */
  onOpenChange?: (open: boolean) => void;
}

// Map a parsed-traffic kind to the LLM Provider enum name explain_agent_run expects.
const KIND_TO_PROVIDER: Record<string, string> = {
  anthropic: 'ANTHROPIC',
  openai: 'OPENAI',
  openai_responses: 'OPENAI_RESPONSES',
  gemini: 'GEMINI',
  ollama: 'OLLAMA',
};

function SessionLane({ session, connectionParams, onOpenChange }: SessionLaneProps) {
  const [expandedRequest, setExpandedRequest] = useState<number | null>(null);

  // Tell the parent whether this lane has a request open. The parent needs it to
  // HOLD the underlying requests: sessions are DERIVED from the live
  // recorded/proxied arrays, which the server caps at 100 rows and evicts from as
  // new traffic arrives, so a session being read can lose its requests — or vanish
  // outright — while the reader is looking at it.
  useEffect(() => {
    onOpenChange?.(expandedRequest !== null);
  }, [expandedRequest, onOpenChange]);

  const displayName = shortenScenarioName(session.scenarioName);
  const isUnscoped = session.scenarioName === '<unscoped>';
  const usage = useMemo(() => computeSessionUsage(session.requests), [session.requests]);

  // Derive a provider + path for the correlated call-graph lookup.
  const graphRequest = session.requests.find((r) => KIND_TO_PROVIDER[r.parsed.kind] != null);
  const graphProvider = graphRequest
    ? graphRequest.parsed.kind === 'anthropic' && graphRequest.parsed.platform === 'bedrock'
      ? 'BEDROCK'
      : KIND_TO_PROVIDER[graphRequest.parsed.kind]
    : null;
  const graphPath = graphRequest ? graphRequest.path : null;

  // Derive the isolation scope for this lane so the call graph can be filtered to
  // this single session server-side (otherwise two sessions on the same endpoint
  // would render the same merged graph). `scenarioName` retains the full
  // `…__iso=header:x-agent-id` form, from which parseIsolationSource yields the
  // source type/key; the value is the session's isolationKey. For the <unscoped>
  // lane parseIsolationSource returns null, so the scope props stay undefined —
  // but the graph is only rendered for scoped lanes anyway.
  const isolationSource = parseIsolationSource(session.scenarioName);

  const handleChipClick = useCallback(
    (index: number) => {
      setExpandedRequest(expandedRequest === index ? null : index);
    },
    [expandedRequest],
  );

  return (
    <Paper
      variant="outlined"
      sx={{ mb: 1 }}
    >
      {/* Lane header */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 1.5,
          py: 0.75,
          borderBottom: 1,
          borderColor: 'divider',
          flexWrap: 'wrap',
        }}
      >
        <Tooltip title={isUnscoped
            ? (session.isolationKey !== '<unscoped>'
              ? `Unscoped requests (${session.isolationKey})`
              : 'Unscoped requests')
            : `${displayName} / ${session.isolationKey}`
          }
        >
          <Typography
            variant="subtitle2"
            noWrap
            sx={{
              fontWeight: 600,
              fontSize: '0.8rem',
              fontFamily: isUnscoped ? undefined : 'monospace',
              fontStyle: isUnscoped ? 'italic' : 'normal',
              maxWidth: 300,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {isUnscoped
              ? (session.isolationKey !== '<unscoped>'
                ? `Unscoped requests (${session.isolationKey})`
                : 'Unscoped requests')
              : `${displayName} / ${session.isolationKey}`
            }
          </Typography>
        </Tooltip>
        <Chip
          label={`${session.requests.length} request${session.requests.length !== 1 ? 's' : ''}`}
          size="small"
          variant="outlined"
          sx={{ height: 18, fontSize: '0.6rem', '& .MuiChip-label': { px: 0.5 } }}
        />
        {usage && (
          <Tooltip title={`${usage.totalInputTokens.toLocaleString()} in / ${usage.totalOutputTokens.toLocaleString()} out`}>
            <Chip
              label={`${usage.totalInputTokens.toLocaleString()} in / ${usage.totalOutputTokens.toLocaleString()} out`}
              size="small"
              variant="outlined"
              sx={{ height: 18, fontSize: '0.6rem', fontFamily: monospaceFontFamily, maxWidth: 180, '& .MuiChip-label': { px: 0.5, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }}
            />
          </Tooltip>
        )}
        {usage?.estimatedCostUsd != null && (
          <Tooltip title={formatCost(usage.estimatedCostUsd)}>
            <Chip
              label={formatCost(usage.estimatedCostUsd)}
              size="small"
              variant="outlined"
              color="warning"
              sx={{ height: 18, fontSize: '0.6rem', fontFamily: monospaceFontFamily, '& .MuiChip-label': { px: 0.5 } }}
            />
          </Tooltip>
        )}
      </Box>

      {/* Horizontal request timeline */}
      <Box
        sx={{
          display: 'flex',
          gap: 0.5,
          px: 1.5,
          py: 0.75,
          overflowX: 'auto',
          flexWrap: 'wrap',
        }}
      >
        {session.requests.map((req, i) => (
          <RequestChip
            key={req.item.key}
            request={req}
            turnIndex={i}
            selected={expandedRequest === i}
            onClick={() => handleChipClick(i)}
          />
        ))}
      </Box>

      {/* Expanded detail */}
      {expandedRequest !== null && session.requests[expandedRequest] && (
        <>
          <Divider />
          <Box sx={{ maxHeight: 400, overflowY: 'auto' }}>
            <RequestDetail request={session.requests[expandedRequest]} />
          </Box>
        </>
      )}

      {/* Session-level conversation transcript (chat-bubble Conversation view,
          same as the Traffic tab). Shown for any session with a detectable LLM
          provider, including unscoped proxy traffic. */}
      <SessionConversation requests={session.requests} />

      {/* "Show Mermaid" link below the Conversation section — opens the
          correlated agent-run call graph (fetched on demand via explain_agent_run)
          as a Mermaid diagram, a compact alternative to the chat transcript.
          Only shown for a scoped session: the <unscoped> lane is a heterogeneous
          catch-all of unrelated requests across providers/paths, so a single
          correlated call graph (derived from one provider+path) cannot represent
          it and would not match the conversation transcript above. */}
      {graphProvider && !isUnscoped && (
        <Box sx={{ px: 1.5, pb: 0.75 }}>
          <AgentRunGraph
            connectionParams={connectionParams}
            provider={graphProvider}
            path={graphPath}
            isolationType={isolationSource?.sourceType}
            isolationKey={isolationSource?.sourceKey}
            isolationValue={session.isolationKey}
          />
        </Box>
      )}
    </Paper>
  );
}

// ---------------------------------------------------------------------------
// Main SessionInspector component
// ---------------------------------------------------------------------------

interface SessionInspectorProps {
  connectionParams: { host: string; port: string; secure: boolean };
}

export default function SessionInspector({ connectionParams }: SessionInspectorProps) {
  // Mirror TrafficInspector: combine both proxied + mocked traffic. Sessions are
  // grouped by isolation key extracted from a matching conversation expectation,
  // regardless of which capture pipeline the request travelled through.
  const proxiedRequests = useDashboardStore((s) => s.proxiedRequests);
  const recordedRequests = useDashboardStore((s) => s.recordedRequests);
  const activeExpectations = useDashboardStore((s) => s.activeExpectations);
  useAutoLoadLlmRows();
  const [search, setSearch] = useState('');
  const [tab, setTab] = useState(0);

  const liveRequests = useMemo(
    () => combineTraffic(proxiedRequests, recordedRequests),
    [proxiedRequests, recordedRequests],
  );

  // Hold the requests a reader is working with. Sessions are DERIVED from these
  // arrays, which are the same server-capped live window the dashboard panels use
  // — at most 100 rows, oldest evicted as new traffic arrives. Under load a
  // session being read loses its requests, and can disappear entirely, while the
  // reader is looking at it. Held while the reader has scrolled the lane list away
  // from the top or has a request open in any lane.
  const [listScrolledAway, setListScrolledAway] = useState(false);
  const [openLanes, setOpenLanes] = useState<ReadonlySet<string>>(() => new Set());
  const interacting = listScrolledAway || openLanes.size > 0;
  const allRequests = useHeldItems(liveRequests, sessionRequestKeyOf, interacting);

  const handleLaneOpenChange = useCallback((laneKey: string, open: boolean) => {
    setOpenLanes((prev) => {
      if (open === prev.has(laneKey)) return prev;
      const next = new Set(prev);
      if (open) next.add(laneKey);
      else next.delete(laneKey);
      return next;
    });
  }, []);
  const sessions = useMemo(
    () => groupBySession(allRequests, activeExpectations),
    [allRequests, activeExpectations],
  );

  const filteredSessions = useMemo(() => {
    if (!search) return sessions;
    const lower = search.toLowerCase();
    return sessions.filter((s) => {
      const name = shortenScenarioName(s.scenarioName).toLowerCase();
      const key = s.isolationKey.toLowerCase();
      return name.includes(lower) || key.includes(lower);
    });
  }, [sessions, search]);

  const hasLlmTraffic = sessions.length > 0;

  return (
    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minHeight: 0, p: 1 }}>
      <Paper variant="outlined" sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minHeight: 0 }}>
        <Tabs
          value={tab}
          onChange={(_, v: number) => setTab(v)}
          sx={{ borderBottom: 1, borderColor: 'divider', minHeight: 36, '& .MuiTab-root': { minHeight: 36, py: 0.5, typography: 'body2' } }}
        >
          <Tab label="Traces" />
          <Tab label="Compare" />
        </Tabs>

        {tab === 0 && (
          <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minHeight: 0, p: 1 }}>
            {/* Summary bar */}
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                mb: 1,
                flexShrink: 0,
                flexWrap: 'wrap',
              }}
            >
              {hasLlmTraffic && (
                <Chip
                  label={`Active traces: ${sessions.length}`}
                  size="small"
                  color="primary"
                  variant="outlined"
                  sx={{ height: 20, fontSize: '0.65rem', '& .MuiChip-label': { px: 0.75 } }}
                />
              )}
              <TextField
                size="small"
                placeholder="Filter traces..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchIcon fontSize="small" />
                      </InputAdornment>
                    ),
                  },
                }}
                sx={{
                  ml: 'auto',
                  maxWidth: 250,
                  '& .MuiInputBase-root': { height: 28, fontSize: '0.75rem' },
                  '& .MuiSvgIcon-root': { fontSize: '0.875rem' },
                }}
              />
            </Box>

            {/* Session lanes */}
            <Box
              sx={{ flex: 1, overflowY: 'auto', minHeight: 0 }}
              onScroll={(e) => {
                const away = e.currentTarget.scrollTop > SESSION_AT_TOP_THRESHOLD_PX;
                setListScrolledAway((prev) => (prev === away ? prev : away));
              }}
            >
              {!hasLlmTraffic ? (
                <Box sx={{ textAlign: 'center', py: 4 }}>
                  <Typography variant="body2" color="text.secondary">
                    No LLM traffic captured yet
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                    Configure your application to proxy through MockServer to see traces grouped here.
                  </Typography>
                </Box>
              ) : filteredSessions.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
                  No traces match the current filter
                </Typography>
              ) : (
                filteredSessions.map((session) => (
                  <SessionLane
                    key={`${session.scenarioName}::${session.isolationKey}`}
                    session={session}
                    connectionParams={connectionParams}
                    onOpenChange={(open) =>
                      handleLaneOpenChange(`${session.scenarioName}::${session.isolationKey}`, open)
                    }
                  />
                ))
              )}
            </Box>
          </Box>
        )}

        {tab === 1 && (
          <Box sx={{ flex: 1, overflowY: 'auto', minHeight: 0, p: 2 }}>
            <CompareRunsBody />
          </Box>
        )}
      </Paper>
    </Box>
  );
}
