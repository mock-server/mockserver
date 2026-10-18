// LLM/SSE streaming under concurrency.
//
// MECHANISM. An
// httpSseResponse streams each event by scheduling its per-token delay onto the
// SHARED action-handler pool: HttpSseResponseActionHandler.scheduleEvents ->
// Scheduler.schedule(writeEvent, delay), where Scheduler is a
// ScheduledThreadPoolExecutor sized actionHandlerThreadCount() (default
// max(5, availableProcessors)). Scheduling is CHAINED per stream (the next event
// is scheduled only after the current writeAndFlush completes), so one stream has
// at most one outstanding task, but N concurrent streams each firing at 1/delay
// put ~N/delay writeEvent tasks/second through that small pool. writeEvent runs
// on a scheduler thread and its ctx.writeAndFlush hands the actual write to a
// Netty event loop. So the failure mode this scenario probes is (1) scheduler-
// thread STARVATION -> tasks execute late -> per-token timing DRIFTS (a fidelity
// loss), and (2) event-loop write pressure a concurrent `match` pays for.
//
// Do NOT look for CallerRunsPolicy firing under saturation: ScheduledThreadPoolExecutor's
// DelayedWorkQueue is UNBOUNDED, so the rejection handler fires only at shutdown. The
// saturation signal is delayed-task execution (the inter-token error the run step's
// reader measures); MockServer exposes no CallerRunsPolicy counter, and none is faked.
//
// WHAT k6 MEASURES HERE (k6 buffers SSE, so it cannot time individual tokens):
//   1. It SUSTAINS the concurrency — a constant-VUs `stream` arm where each VU
//      holds one long-lived GET /stream open (~STREAMING.concurrency in flight).
//   2. The within-run match A/B: a fixed-rate `match` arm
//      measured FIRST alone (match_baseline) then AGAIN during the stream load
//      (match_under_stream). The p95 ratio is "does streaming steal the hot path".
// The inter-token delay-error distribution and heap-per-open-stream are measured server-side by perf-test-run.sh and merged into .streaming.
//
// MEASURED-WINDOW HYGIENE — cloned from the current regression.js shape:
// a warmup scenario, staggered phase starts, preAllocatedVUs == maxVUs on the
// fixed-rate match arms (no mid-run allocation), a settle exclusion tagged
// op:<op>_settle, MIN_TAIL_SAMPLES tail suppression, and the heavy/warm load-time
// contract. The `stream` arm is `heavy` (no warm): warming it at the ~warmup rate
// would open thousands of long-lived streams at once. See config.js STREAMING for
// the retained-heap arithmetic that bounds tokens/delay/concurrency.
//
//   k6 run mockserver-performance-test/k6/streaming.js
//
import exec from 'k6/execution';
import { CONFIG, STREAMING } from './lib/config.js';
import {
  seedStreaming,
  verifyStreamingArms,
  resetMockServer,
  getStream,
  getStreamMatch,
} from './lib/expectations.js';

function toSeconds(d) {
  const str = String(d).trim();
  const tokenRe = /(\d+)(ms|s|m|h)/g;
  let total = 0;
  let matched = false;
  let token;
  while ((token = tokenRe.exec(str)) !== null) {
    matched = true;
    const value = Number(token[1]);
    total += value * { ms: 0.001, s: 1, m: 60, h: 3600 }[token[2]];
  }
  if (!matched) {
    throw new Error(`toSeconds: cannot parse duration "${d}"`);
  }
  return total;
}

function round(v, dp = 3) {
  if (v === undefined || v === null || Number.isNaN(v)) {
    return null;
  }
  const f = 10 ** dp;
  return Math.round(v * f) / f;
}

const MIN_TAIL_SAMPLES = 30;

// Fixed-rate match arms: preAllocatedVUs == maxVUs (Finding-3 no-mid-run-alloc).
if (STREAMING.matchPreAllocatedVUs !== STREAMING.matchMaxVUs) {
  throw new Error(`streaming.js: K6_STREAM_MATCH_PRE_VUS (${STREAMING.matchPreAllocatedVUs}) must equal K6_STREAM_MATCH_MAX_VUS (${STREAMING.matchMaxVUs}) — the Finding-3 no-mid-run-allocation invariant forbids a VU ramp on the measured match arms.`);
}
const MATCH_VUS = STREAMING.matchPreAllocatedVUs;

// Timeline (seconds):
//   [0, WARMUP)                                   warmup (match warmed; stream NOT)
//   [WARMUP, WARMUP+BASELINE)                      match_baseline (alone)
//   [WARMUP+BASELINE, WARMUP+BASELINE+LOAD)        stream load + match_under_stream
const WARMUP_SEC = toSeconds(STREAMING.warmup);
const BASELINE_SEC = toSeconds(STREAMING.matchBaselineDuration);
const LOAD_SEC = toSeconds(STREAMING.loadDuration);
const SETTLE_SEC = toSeconds(STREAMING.settle);

const BASELINE_START = WARMUP_SEC;
const LOAD_START = WARMUP_SEC + BASELINE_SEC;

// Per-arm measured window (duration minus the settle exclusion).
const MATCH_BASELINE_WINDOW = BASELINE_SEC - SETTLE_SEC;
const MATCH_LOAD_WINDOW = LOAD_SEC - SETTLE_SEC;

// A fixed-rate match arm's requests before its settle boundary are tagged
// op:<op>_settle and excluded from the measured percentiles. startSec is the
// arm's own scenario start; the measured window opens SETTLE later.
function matchPhaseTag(op, startSec) {
  const elapsedSec = exec.instance.currentTestRunDuration / 1000;
  return elapsedSec >= startSec + SETTLE_SEC ? op : `${op}_settle`;
}

function streamingThresholds() {
  const t = {};
  for (const op of ['match_baseline', 'match_under_stream']) {
    t[`http_req_duration{op:${op}}`] = ['p(50)>=0', 'p(95)>=0', 'p(99)>=0'];
    t[`http_req_failed{op:${op}}`] = ['rate>=0'];
    t[`http_reqs{op:${op}}`] = ['count>=0'];
    t[`http_reqs{op:${op}_settle}`] = ['count>=0'];
    t[`dropped_iterations{scenario:${op}}`] = ['count>=0'];
  }
  // The stream load arm: its request duration is ~tokens×delay (not a latency of
  // interest), but its error rate and dropped iterations ARE — a stream arm that
  // errors or that k6 cannot keep open means the concurrency was not actually
  // delivered, so the heap/fidelity numbers describe fewer streams than intended.
  t['http_req_failed{op:stream}'] = ['rate>=0'];
  t['http_reqs{op:stream}'] = ['count>=0'];
  t['dropped_iterations{scenario:stream}'] = ['count>=0'];
  return t;
}

export const options = {
  insecureSkipTLSVerify: CONFIG.insecureSkipTLSVerify,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      exec: 'warmupOp',
      rate: Math.max(10, Math.round(STREAMING.matchRate / 4)),
      timeUnit: '1s',
      duration: STREAMING.warmup,
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
    // Match alone — the A/B control.
    match_baseline: {
      executor: 'constant-arrival-rate',
      exec: 'matchBaselineOp',
      rate: STREAMING.matchRate,
      timeUnit: '1s',
      duration: STREAMING.matchBaselineDuration,
      startTime: `${BASELINE_START}s`,
      preAllocatedVUs: MATCH_VUS,
      maxVUs: MATCH_VUS,
    },
    // The concurrency load: constant-VUs, each VU holds one open stream. Its VU
    // count IS the concurrency. constant-vus has no arrival ramp, so there is no
    // Finding-3 mid-run allocation to forbid — the pool is fixed by definition.
    stream_load: {
      executor: 'constant-vus',
      exec: 'streamOp',
      vus: STREAMING.concurrency,
      duration: STREAMING.loadDuration,
      startTime: `${LOAD_START}s`,
    },
    // Match again, overlapping the stream load — the A/B treatment.
    match_under_stream: {
      executor: 'constant-arrival-rate',
      exec: 'matchUnderStreamOp',
      rate: STREAMING.matchRate,
      timeUnit: '1s',
      duration: STREAMING.loadDuration,
      startTime: `${LOAD_START}s`,
      preAllocatedVUs: MATCH_VUS,
      maxVUs: MATCH_VUS,
    },
  },
  thresholds: streamingThresholds(),
};

export function setup() {
  seedStreaming();
  verifyStreamingArms();
}

export function warmupOp() {
  // Warm ONLY the match path. The stream arm is heavy (a warmup at this rate would
  // open a storm of long-lived streams), so it is deliberately not warmed — it is
  // the load, and its cold first cohort lands in the excluded settle window.
  getStreamMatch('warmup');
}

export function matchBaselineOp() {
  getStreamMatch(matchPhaseTag('match_baseline', BASELINE_START));
}

export function matchUnderStreamOp() {
  getStreamMatch(matchPhaseTag('match_under_stream', LOAD_START));
}

export function streamOp() {
  getStream({ op: 'stream' });
}

export function teardown() {
  resetMockServer();
}

function armSummary(data, op, measuredWindowSec) {
  const dur = data.metrics[`http_req_duration{op:${op}}`];
  const failed = data.metrics[`http_req_failed{op:${op}}`];
  const reqs = data.metrics[`http_reqs{op:${op}}`];
  if (!dur || !dur.values) {
    return null;
  }
  const count = reqs && reqs.values ? reqs.values.count : 0;
  const settleReqs = data.metrics[`http_reqs{op:${op}_settle}`];
  const settleExcluded = settleReqs && settleReqs.values ? settleReqs.values.count : 0;
  const dropped = data.metrics[`dropped_iterations{scenario:${op}}`];
  const droppedCount = dropped && dropped.values ? dropped.values.count : 0;
  const throughput = round(measuredWindowSec > 0 ? count / measuredWindowSec : 0);
  const enoughForTail = count >= MIN_TAIL_SAMPLES;
  return {
    p50_ms: round(dur.values['p(50)'] !== undefined ? dur.values['p(50)'] : dur.values.med),
    p95_ms: enoughForTail ? round(dur.values['p(95)']) : null,
    p99_ms: enoughForTail ? round(dur.values['p(99)']) : null,
    sample_count: round(count, 0),
    throughput_rps: throughput,
    offered_rps: STREAMING.matchRate,
    dropped_iterations: round(droppedCount, 0),
    delivery_ratio: round(STREAMING.matchRate > 0 ? throughput / STREAMING.matchRate : null, 4),
    error_rate: failed && failed.values ? round(failed.values.rate, 5) : 0,
    settle_excluded: round(settleExcluded, 0),
  };
}

export function handleSummary(data) {
  const proto = STREAMING.proto;
  const baseWindow = MATCH_BASELINE_WINDOW > 0 ? MATCH_BASELINE_WINDOW : BASELINE_SEC;
  const loadWindow = MATCH_LOAD_WINDOW > 0 ? MATCH_LOAD_WINDOW : LOAD_SEC;
  const baseline = armSummary(data, 'match_baseline', baseWindow);
  const underStream = armSummary(data, 'match_under_stream', loadWindow);

  // Stream load arm stats (completed streams, error rate, drops) — proof the
  // concurrency was actually delivered, so the server-side heap/fidelity numbers
  // describe the intended number of open streams.
  const streamReqs = data.metrics['http_reqs{op:stream}'];
  const streamFailed = data.metrics['http_req_failed{op:stream}'];
  const streamDropped = data.metrics['dropped_iterations{scenario:stream}'];
  const streamsCompleted = streamReqs && streamReqs.values ? streamReqs.values.count : 0;
  const streamErrorRate = streamFailed && streamFailed.values ? round(streamFailed.values.rate, 5) : 0;
  const streamDroppedCount = streamDropped && streamDropped.values ? streamDropped.values.count : 0;

  // The headline hot-path-theft metric: p95 under stream / p95 baseline. Null if
  // either arm lacks the samples to support a p95.
  const ratio = (baseline && underStream && baseline.p95_ms && underStream.p95_ms)
    ? round(underStream.p95_ms / baseline.p95_ms, 4)
    : null;

  // FLAT scalar block. perf-test-run.sh merges in the server-side metrics
  // (heap_bytes_per_stream, intertoken_error_*), and perf-test-compare.sh pulls a
  // CURATED subset of these names as notify-only budgeted metrics (see that step).
  const streaming = {
    concurrency: STREAMING.concurrency,
    tokens_per_stream: STREAMING.tokens,
    requested_delay_ms: STREAMING.delayMs,
    // A/B: match latency alone vs during stream load, and the ratio.
    match_baseline_p50_ms: baseline ? baseline.p50_ms : null,
    match_baseline_p95_ms: baseline ? baseline.p95_ms : null,
    match_baseline_p99_ms: baseline ? baseline.p99_ms : null,
    match_baseline_sample_count: baseline ? baseline.sample_count : null,
    match_baseline_delivery_ratio: baseline ? baseline.delivery_ratio : null,
    match_under_stream_p50_ms: underStream ? underStream.p50_ms : null,
    match_under_stream_p95_ms: underStream ? underStream.p95_ms : null,
    match_under_stream_p99_ms: underStream ? underStream.p99_ms : null,
    match_under_stream_sample_count: underStream ? underStream.sample_count : null,
    match_under_stream_delivery_ratio: underStream ? underStream.delivery_ratio : null,
    match_under_stream_error_rate: underStream ? underStream.error_rate : null,
    match_p95_ratio: ratio,
    // Stream-load delivery proof.
    streams_completed: round(streamsCompleted, 0),
    stream_error_rate: streamErrorRate,
    stream_dropped_iterations: round(streamDroppedCount, 0),
    measured_load_window_s: round(loadWindow, 3),
    // Server-side metrics filled in by perf-test-run.sh (null here so the schema
    // is stable): the inter-token delay-error distribution (idle control vs under
    // load, ms) and heap per open stream. These are the metrics k6 cannot see.
    intertoken_error_idle_p50_ms: null,
    intertoken_error_idle_p95_ms: null,
    intertoken_error_idle_p99_ms: null,
    intertoken_error_load_p50_ms: null,
    intertoken_error_load_p95_ms: null,
    intertoken_error_load_p99_ms: null,
    intertoken_error_p95_ratio: null,
    intertoken_reader_streams_idle: null,
    intertoken_reader_streams_load: null,
    heap_idle_floor_bytes: null,
    heap_streaming_floor_bytes: null,
    heap_bytes_per_stream: null,
    // CallerRunsPolicy counter: NOT emitted — MockServer exposes no
    // such counter, and the policy does not fire under load (unbounded scheduled
    // queue). Recorded here as a documented absence rather than a fabricated proxy.
    caller_runs_policy_count: null,
    caller_runs_policy_available: false,
  };

  const out = { proto, streaming };
  const json = JSON.stringify(out, null, 2);
  const result = {};
  result[STREAMING.resultPath] = json;
  result.stdout = `\nstreaming result (${proto}):\n${json}\n`;
  return result;
}
