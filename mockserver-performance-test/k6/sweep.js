// Throughput-vs-latency sweep — measures the "knee" of MockServer's load curve.
//
// Offers load at an ascending LADDER of fixed arrival rates (K6_SWEEP_RATES) and
// records, per rate step, the ACHIEVED throughput, latency percentiles
// (p50/p90/p95/p99/p99.9), a per-phase latency breakdown (phase_ms, see PHASES),
// and error rate. This series is what we plot as a load-vs-latency knee curve on
// the documentation site, so the JSON output shape is a hard contract (see
// handleSummary).
//
// Each rate is one constant-arrival-rate scenario, staggered after the previous
// (startTime = sum of prior step+gap durations) with a short quiet gap between
// steps so one step's tail latency does not bleed into the next step's
// percentiles. Every request is tagged rate:<offered> so the per-step
// http_req_duration / http_req_failed / http_reqs submetrics are computed in the
// summary. The first K6_SWEEP_SETTLE of each rung (the onset transient) is
// excluded from its latency percentiles only — see latency_window in the output
// and enterWindow for how a request is assigned to the settle or steady window.
// There are deliberately NO aborting thresholds — at the top of the
// ladder k6 may drop iterations (VU-starved) and latency/errors may degrade
// sharply; observing that degradation IS the point.
//
//   k6 run mockserver-performance-test/k6/sweep.js
//   k6 run -e K6_SWEEP_RATES=200,500,1000 -e K6_SWEEP_STEP=8s \
//     -e K6_SWEEP_RESULT_PATH=/tmp/sweep-result.json .../sweep.js
//
// ---------------------------------------------------------------------------
// VU-POOL DIAGNOSTICS. Each rung also records (additive fields; consumers ignore
// unknown keys) what explains a rung dropping iterations:
//   * vus_active_*       — the concurrency the rung actually used, sampled at the start of
//                          every iteration. Below the rung's pool, the pool was not the limit.
//   * vus_diagnostics    — whole run: did the initialised pool grow past k6's planned peak
//                          (plannedVusBaseline)? With fixed per-rung pools it must not.
//   * stalls / stall_*   — deep-tail requests (> K6_SWEEP_STALL_MS) and the concurrency then.
//   * stall_time_buckets — when within the rung the stalls fell (clustered = transient
//                          stall). A proxy for drop timing: k6 cannot timestamp a drop.
// Pools are fixed and sized per rung (poolForRate; derivation in
// docs/code/performance-measurement.md, "k6 load-shape sizing"); these diagnostics verify that.
// ---------------------------------------------------------------------------
import { Trend, Counter, Gauge } from 'k6/metrics';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { CONFIG, SWEEP } from './lib/config.js';
import { seedExpectations, resetMockServer, getSimple } from './lib/expectations.js';

// Sum a k6 duration string to seconds (supports compound forms like "1m30s").
function toSeconds(d) {
  const str = String(d).trim();
  const tokenRe = /(\d+(?:\.\d+)?)(ms|s|m|h)/g;
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

const RATES = SWEEP.rates;
const STEP_SECONDS = toSeconds(SWEEP.step);
const GAP_SECONDS = toSeconds(SWEEP.gap);
if (!/^\d+(ms|s)$/.test(String(SWEEP.settle).trim())) {
  throw new Error(`sweep.js: K6_SWEEP_SETTLE must be a whole number of ms or s (e.g. 3s), got "${SWEEP.settle}"`);
}
const SETTLE_SECONDS = toSeconds(SWEEP.settle);
// An iteration still running at gracefulStop is interrupted and in no k6 metric, so keep it >= 1s.
if (SWEEP.gracefulStop !== null && (!/^\d+(ms|s)$/.test(SWEEP.gracefulStop) || toSeconds(SWEEP.gracefulStop) < 1)) {
  throw new Error(`sweep.js: K6_SWEEP_GRACEFUL_STOP must be whole ms or s of at least 1s (e.g. 5s), got "${SWEEP.gracefulStop}"`);
}
const SETTLE_MS = SETTLE_SECONDS * 1000;
if (SETTLE_SECONDS >= STEP_SECONDS) {
  throw new Error(`sweep.js: K6_SWEEP_SETTLE (${SWEEP.settle}) must be shorter than K6_SWEEP_STEP (${SWEEP.step}) or no rung has a measured latency window.`);
}

// Opt-in multi-process remote-write mode; every default below reproduces
// the single-process published method exactly.
if (!['vu_tag', 'wallclock'].includes(SWEEP.windowMode)) {
  throw new Error(`sweep.js: K6_SWEEP_WINDOW_MODE must be vu_tag or wallclock, got "${SWEEP.windowMode}"`);
}
const WALLCLOCK = SWEEP.windowMode === 'wallclock';
const QUIET_SECONDS = toSeconds(SWEEP.quiet === '0' ? '0s' : SWEEP.quiet);
const START_AT_MS = SWEEP.startAtMs > 0 ? SWEEP.startAtMs : 0;
// True when the harness cuts wall-clock windows (a synchronised set, or no window tag); the
// lean summary then keeps the rung-start submetric, which the default summary always keeps.
const RECORD_RUNG_START = WALLCLOCK || START_AT_MS > 0;

// Within every rung preAllocatedVUs == maxVUs, so no executor can allocate a VU (a
// fresh connection) mid-run. The pool is sized PER RUNG (poolForRate): one flat pool
// would either storm the low rungs or cap the high ones and silently move rig_valid.

// Optional FLAT override: K6_SWEEP_PRE_VUS / K6_SWEEP_MAX_VUS force one pool on
// every rung. If EITHER is set, BOTH must be set and equal (a ramp is forbidden);
// if NEITHER is set, per-rung sizing applies.
const FLAT_PRE = SWEEP.preAllocatedVUs;
const FLAT_MAX = SWEEP.maxVUs;
const FLAT_SET = FLAT_PRE !== undefined || FLAT_MAX !== undefined;
if (FLAT_SET && FLAT_PRE !== FLAT_MAX) {
  throw new Error(`sweep.js: K6_SWEEP_PRE_VUS (${FLAT_PRE}) must equal K6_SWEEP_MAX_VUS (${FLAT_MAX}) — the Finding-3 no-mid-run-allocation invariant forbids a VU ramp. Set them equal or leave both unset (per-rung sizing).`);
}

// Per-rung fixed pool: clamp(ceil(rate * vuPerRps), vuFloor, vuCeiling). Scales
// the pool with the rung's offered rate so low rungs stay light (no connection
// storm) while high rungs get the VUs they need to demonstrate the server ceiling
// rather than a client one. A flat override, when set, wins for every rung.
function poolForRate(rate) {
  if (FLAT_SET) {
    return FLAT_PRE;
  }
  const sized = Math.ceil(rate * SWEEP.vuPerRps);
  return Math.min(SWEEP.vuCeiling, Math.max(SWEEP.vuFloor, sized));
}
// rate -> fixed pool, computed once and reused by buildScenarios AND the
// vus_diagnostics baseline so the two never disagree.
const POOLS = new Map(RATES.map((r) => [r, poolForRate(r)]));

// The VU count k6 initializes before the test starts, computed as k6 v1.7.1 plans it
// (ScenarioConfigs.GetFullExecutionRequirements + GetMaxPlannedVUs): each scenario
// reserves its planned VUs from startTime to startTime + duration + gracefulStop (30s
// default), and k6 initializes the peak of the overlapping reservations' sum. A
// reservation ending at the instant another starts is released first, as in k6.
// null for an executor this does not model, so vus_pool_grew reads null, not false.
function plannedVusBaseline(scenarios) {
  const events = [];
  for (const sc of Object.values(scenarios)) {
    const planned = sc.preAllocatedVUs !== undefined ? sc.preAllocatedVUs : sc.vus;
    const span = sc.duration || sc.maxDuration;
    if (planned === undefined || span === undefined) {
      return null;
    }
    // Whole milliseconds, so fractional steps cannot turn a tie into an overlap.
    const start = Math.round(toSeconds(sc.startTime || '0s') * 1000);
    const end = start + Math.round((toSeconds(span) + toSeconds(sc.gracefulStop || '30s')) * 1000);
    events.push([start, planned], [end, -planned]);
  }
  events.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  let current = 0;
  let peak = 0;
  for (const [, delta] of events) {
    current += delta;
    peak = Math.max(peak, current);
  }
  return peak;
}

// --- VU-pool diagnostics tunables --------------------------------------------
// A request slower than STALL_MS is counted as a "stall" — deep-tail latency well
// above the ~0.18 ms p50, the observable signature of the hypothesised transient
// server stall. Diagnostic only; never gates.
const STALL_MS = Number(__ENV.K6_SWEEP_STALL_MS || 5);
// Each rung is divided into this many equal WALL-TIME buckets so stall timing
// WITHIN a rung is legible (clustered vs uniform). Kept small — cardinality is
// rungs x buckets.
const TIME_BUCKETS = Math.max(1, Math.trunc(Number(__ENV.K6_SWEEP_TIME_BUCKETS || 6)) || 6);
const BUCKET_WIDTH_MS = TIME_BUCKETS > 0 ? (STEP_SECONDS * 1000) / TIME_BUCKETS : STEP_SECONDS * 1000;
// offered-rate -> ladder index, to recover a rung's scheduled start offset so the
// stall time-bucket can be computed from the test clock.
const RATE_INDEX = new Map(RATES.map((r, i) => [r, i]));

// k6's six built-in request-timing phases, in wire order, mapped to the output
// key used in each rung's phase_ms block. These are metrics k6 ALREADY computes
// for every request, so capturing them adds no per-request work — only summary-
// time reads. Captured because http_req_duration alone cannot say WHICH phase
// owns the latency tail, and it deliberately EXCLUDES connection setup:
// duration == sending + waiting + receiving, so a tail in blocked/connecting/tls
// never shows up in the duration percentiles we quote. Reading the split tells
// waiting-heavy (time to first byte -> server) from sending/receiving-heavy
// (client socket work) from blocked/connecting-heavy (connection setup).
const PHASES = [
  ['http_req_blocked', 'blocked'],
  ['http_req_connecting', 'connecting'],
  ['http_req_tls_handshaking', 'tls'],
  ['http_req_sending', 'sending'],
  ['http_req_waiting', 'waiting'],
  ['http_req_receiving', 'receiving'],
];

// Custom metrics. Trend/Counter samples carry their OWN tags (2nd arg to .add),
// independent of request tags, so tagging these by rung does NOT perturb the
// http_req_* submetrics the knee chart depends on.
const vusActiveTrend = new Trend('sweep_vus_active'); // active VUs at iteration start
const stallCounter = new Counter('sweep_stalls'); // requests slower than STALL_MS
const stallConcurrencyTrend = new Trend('sweep_stall_concurrency'); // active VUs at a stall
const stallBucketCounter = new Counter('sweep_stalls_bucketed'); // stalls by time-in-rung bucket
// Wall-clock start (epoch ms) of each rung, so the harness can line server-side samples and
// JFR windows up with a rung. Written once per VU per rung, never per request.
const rungStartGauge = new Gauge('sweep_rung_start_epoch_ms');

// Build one constant-arrival-rate scenario per ladder rung, staggered so they run
// back-to-back (step + gap) rather than concurrently. The rate-tagged submetrics
// let handleSummary compute per-step percentiles.
function buildScenarios() {
  const scenarios = {};
  RATES.forEach((rate, i) => {
    const startTime = i * (STEP_SECONDS + GAP_SECONDS);
    // Per-rung FIXED pool (preAllocatedVUs == maxVUs): no mid-run allocation, and
    // sized to this rung's offered rate so the low rungs do not storm and the high
    // rungs are not client-capped.
    const pool = POOLS.get(rate);
    scenarios[`rate_${rate}`] = {
      executor: 'constant-arrival-rate',
      exec: 'matchAt',
      rate,
      timeUnit: '1s',
      duration: `${STEP_SECONDS}s`,
      startTime: `${startTime}s`,
      preAllocatedVUs: pool,
      maxVUs: pool,
      // Pass the offered rate to the exec fn via env-free scenario tag is not
      // possible, so each scenario gets its own exec wrapper via the tag below.
      tags: { rate: String(rate) },
      env: { SWEEP_RATE: String(rate) },
      ...(SWEEP.gracefulStop !== null ? { gracefulStop: SWEEP.gracefulStop } : {}),
    };
  });
  if (QUIET_SECONDS > 0) {
    // Idle after the last rung ends so the remote-write output pushes the final
    // rung's samples on its own period, not only in the shutdown flush.
    scenarios.quiet_tail = {
      executor: 'shared-iterations',
      exec: 'quietTail',
      vus: 1,
      iterations: 1,
      startTime: `${(RATES.length - 1) * (STEP_SECONDS + GAP_SECONDS) + STEP_SECONDS}s`,
      maxDuration: `${QUIET_SECONDS + 30}s`,
    };
  }
  return scenarios;
}

// Materialise the per-step submetrics in the summary by declaring (always-true,
// non-aborting) thresholds. The >=0 expressions never fail (notify-only) but
// force k6 to compute and expose p(50)/p(90)/p(95)/p(99)/p(99.9), the failed
// rate, and the request count per rate tag so handleSummary can read them.
function sweepThresholds() {
  const t = {};
  if (SWEEP.leanSummary) {
    return leanThresholds(t);
  }
  for (const rate of RATES) {
    // Published percentiles are post-settle; the whole-rung set feeds full_rung_ms.
    t[`http_req_duration{win:${rate}_steady}`] = ['p(50)>=0', 'p(90)>=0', 'p(95)>=0', 'p(99)>=0', 'p(99.9)>=0'];
    t[`http_req_duration{rate:${rate}}`] = ['p(50)>=0', 'p(95)>=0', 'p(99)>=0', 'p(99.9)>=0'];
    t[`http_reqs{win:${rate}_steady}`] = ['count>=0'];
    t[`http_reqs{win:${rate}_settle}`] = ['count>=0'];
    t[`sweep_stalls{win:${rate}_steady}`] = ['count>=0'];
    t[`sweep_rung_start_epoch_ms{rate:${rate}}`] = ['value>=0'];
    // Per-phase breakdown submetrics (see PHASES). p95 + p99 ONLY: the question
    // is which phase owns the TAIL, so only the high percentiles carry it (a mean
    // or median cannot locate a tail); p99.9 is omitted because a per-phase p99.9
    // needs more samples than the low rungs produce in one short step. Same
    // notify-only materialise-via-threshold trick as http_req_duration.
    for (const [metric] of PHASES) {
      t[`${metric}{win:${rate}_steady}`] = ['p(95)>=0', 'p(99)>=0'];
    }
    t[`http_req_failed{rate:${rate}}`] = ['rate>=0'];
    t[`http_reqs{rate:${rate}}`] = ['count>=0'];
    // dropped_iterations is k6's own "the client could not launch this iteration
    // on time" counter (VU-starved / arrival-rate not met). Materialise it per
    // rung (scenario tag rate_<rate>) so handleSummary can report whether the
    // CLIENT fell behind at each offered rate — a rung with drops is one where
    // k6, not MockServer, ran out of headroom, so its achieved throughput is a
    // client ceiling and must be EXCLUDED from the derived saturation point.
    t[`dropped_iterations{scenario:rate_${rate}}`] = ['count>=0'];
    // VU-pool diagnostics submetrics. Same materialise-
    // via-threshold trick; all notify-only.
    t[`sweep_vus_active{rate:${rate}}`] = ['max>=0', 'avg>=0', 'p(95)>=0', 'med>=0'];
    t[`sweep_stalls{rate:${rate}}`] = ['count>=0'];
    t[`sweep_stall_concurrency{rate:${rate}}`] = ['max>=0', 'avg>=0'];
    // One submetric per (rung, time-bucket). A single combined `slot` tag
    // ("<rate>_<bucket>") is used deliberately instead of two tags so the
    // threshold key matches the read-back key verbatim regardless of how k6
    // orders multi-tag submetric names.
    for (let b = 0; b < TIME_BUCKETS; b += 1) {
      t[`sweep_stalls_bucketed{slot:${rate}_${b}}`] = ['count>=0'];
    }
  }
  return t;
}

// Lean summary: only the counts and error rate the harness reconciles
// against Prometheus. Percentile submetrics are not materialised because latency
// comes from the merged native histograms.
function leanThresholds(t) {
  for (const rate of RATES) {
    t[`http_reqs{rate:${rate}}`] = ['count>=0'];
    t[`http_req_failed{rate:${rate}}`] = ['rate>=0'];
    t[`dropped_iterations{scenario:rate_${rate}}`] = ['count>=0'];
    if (RECORD_RUNG_START) {
      t[`sweep_rung_start_epoch_ms{rate:${rate}}`] = ['value>=0'];
    }
  }
  return t;
}

// setup() may sleep until START_AT_MS, so give it room beyond the default 60 s.
function setupTimeout() {
  if (START_AT_MS <= 0) {
    return undefined;
  }
  return `${Math.max(60, Math.ceil((START_AT_MS - Date.now()) / 1000) + 60)}s`;
}

const SCENARIOS = buildScenarios();
// The no-growth expectation for k6's initialized VU count (vus_max).
const INIT_BASELINE = plannedVusBaseline(SCENARIOS);

export const options = {
  insecureSkipTLSVerify: CONFIG.insecureSkipTLSVerify,
  // handleSummary reads these stats off each submetric's `values`; p(50)/p(99)/
  // p(99.9) are NOT in k6's default trend set, so declare them or they come back
  // null.
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'max'],
  scenarios: SCENARIOS,
  thresholds: sweepThresholds(),
  ...(START_AT_MS > 0 ? { setupTimeout: setupTimeout() } : {}),
};

export function setup() {
  if (SWEEP.manageSut) {
    seedExpectations();
  }
  if (START_AT_MS <= 0) {
    return undefined;
  }
  // Sleep until the shared start instant; scenario startTime offsets then count from
  // the same wall-clock origin in every process.
  const waitMs = START_AT_MS - Date.now();
  if (waitMs > 0) {
    sleep(waitMs / 1000);
  }
  return { start_at_ms: START_AT_MS, setup_end_ms: Date.now() };
}

export function quietTail() {
  sleep(QUIET_SECONDS);
}

// Settle/steady window, tracked per VU (each VU has its own JS runtime). `win` is a
// VU tag set when the VU enters a rung or crosses its settle boundary, keeping a
// per-request tag and a per-iteration exec.scenario read (which allocates) off the
// request path, where k6 CPU caps the top rungs.
// Relies on k6 keeping VU tags across iterations of the same scenario.
let winRate = null;
let steadyAtMs = 0;
let inSteady = false;

function enterWindow(rate) {
  if (rate !== winRate) {
    winRate = rate;
    rungStartGauge.add(exec.scenario.startTime, { rate });
    steadyAtMs = exec.scenario.startTime + SETTLE_MS;
    inSteady = Date.now() >= steadyAtMs;
    exec.vu.metrics.tags.win = `${rate}_${inSteady ? 'steady' : 'settle'}`;
  } else if (!inSteady && Date.now() >= steadyAtMs) {
    inSteady = true;
    exec.vu.metrics.tags.win = `${rate}_steady`;
  }
}

// wallclock mode: no window tag; only note the rung start once per VU per rung.
function noteRungStart(rate) {
  if (rate !== winRate) {
    winRate = rate;
    rungStartGauge.add(exec.scenario.startTime, { rate });
  }
}

// Single exec fn for every rung; the scenario's env.SWEEP_RATE supplies the
// offered-rate tag so the request lands in this step's submetric.
export function matchAt() {
  const rate = __ENV.SWEEP_RATE;
  const rateTag = { rate };
  if (!SWEEP.vuDiagnostics) {
    if (WALLCLOCK) {
      noteRungStart(rate);
    } else {
      enterWindow(rate);
    }
    getSimple(rateTag);
    return;
  }
  // Sample active VUs at iteration START. `vusActive` is the process-global count
  // of VUs currently running iterations; because the rungs are staggered with quiet
  // gaps, during a rung only THIS scenario is active, so the sample reflects this
  // rung's own concurrency. Sampling per-iteration (rather than reading k6's 1 Hz
  // `vus` gauge) is deliberate: a stall-driven pile-up lasts only a few ms and the
  // 1 Hz gauge misses it, so this is the sensitive instrument for the stall
  // question. (Pool-SIZE growth is a whole-test property — see vus_diagnostics in
  // handleSummary — not something a per-rung sample can isolate, because k6 shares
  // initialized VUs across rungs.)
  const active = exec.instance.vusActive;
  vusActiveTrend.add(active, rateTag);

  // Rung-relative clock: the onset window is measured from THIS scenario's start.
  if (WALLCLOCK) {
    noteRungStart(rate);
  } else {
    enterWindow(rate);
  }
  const res = getSimple(rateTag);

  // Stall accounting: a request slower than STALL_MS is deep-tail latency — the
  // observable signature of the hypothesised transient stall. Record its count,
  // the VU concurrency observed AT the stall (does a stall coincide with many VUs
  // piled up?), and WHEN within the rung it fell (clustered vs uniform).
  const duration = res && res.timings ? res.timings.duration : 0;
  if (duration > STALL_MS) {
    stallCounter.add(1, rateTag);
    // Re-read vusActive HERE rather than reusing the iteration-start `active`. A
    // pile-up builds DURING the slow request, so the iteration-start reading
    // understates it — and a field named stall_concurrency must measure
    // concurrency at the stall, not concurrency at the start of the iteration
    // that later stalled. Only costs a read on the (rare) stall path.
    stallConcurrencyTrend.add(exec.instance.vusActive, rateTag);
    const idx = RATE_INDEX.has(Number(rate)) ? RATE_INDEX.get(Number(rate)) : 0;
    const offsetMs = idx * (STEP_SECONDS + GAP_SECONDS) * 1000;
    let bucket = Math.floor((exec.instance.currentTestRunDuration - offsetMs) / BUCKET_WIDTH_MS);
    if (bucket < 0) {
      bucket = 0;
    } else if (bucket >= TIME_BUCKETS) {
      bucket = TIME_BUCKETS - 1;
    }
    stallBucketCounter.add(1, { slot: `${rate}_${bucket}` });
  }
}

export function teardown() {
  if (SWEEP.manageSut && SWEEP.resetOnTeardown) {
    resetMockServer();
  }
}

// Emit the machine-readable result consumed by the knee-curve chart. Per rung:
//   offered_rps  — the configured arrival rate for the step
//   achieved_rps — completed requests for that step / step duration (seconds)
//   p*_ms        — latency percentiles from that step's tagged submetric
//   error_rate   — failed-request fraction for that step (0..1)
// Also returns the standard k6 text summary on stdout.
export function handleSummary(data) {
  const points = [];
  for (const rate of RATES) {
    const dur = data.metrics[`http_req_duration{win:${rate}_steady}`];
    const fullDur = data.metrics[`http_req_duration{rate:${rate}}`];
    const fv = fullDur && fullDur.values ? fullDur.values : {};
    const steadyReqs = data.metrics[`http_reqs{win:${rate}_steady}`];
    const settleReqs = data.metrics[`http_reqs{win:${rate}_settle}`];
    const steadyStalls = data.metrics[`sweep_stalls{win:${rate}_steady}`];
    const failed = data.metrics[`http_req_failed{rate:${rate}}`];
    const reqs = data.metrics[`http_reqs{rate:${rate}}`];
    const dropped = data.metrics[`dropped_iterations{scenario:rate_${rate}}`];
    const rungStart = data.metrics[`sweep_rung_start_epoch_ms{rate:${rate}}`];
    const count = reqs && reqs.values ? reqs.values.count : 0;
    const v = dur && dur.values ? dur.values : {};

    // Per-phase latency breakdown for this rung: p95/p99 of each of k6's six
    // built-in request phases, tagged by rung exactly like http_req_duration.
    // This is what discriminates the tail's OWNER — see PHASES for the reading.
    const phaseMs = {};
    for (const [metric, key] of PHASES) {
      const pm = data.metrics[`${metric}{win:${rate}_steady}`];
      const pv = pm && pm.values ? pm.values : {};
      phaseMs[key] = { p95_ms: round(pv['p(95)']), p99_ms: round(pv['p(99)']) };
    }

    // VU-pool diagnostics for this rung.
    const va = data.metrics[`sweep_vus_active{rate:${rate}}`];
    const vaV = va && va.values ? va.values : {};
    const stalls = data.metrics[`sweep_stalls{rate:${rate}}`];
    const sc = data.metrics[`sweep_stall_concurrency{rate:${rate}}`];
    const scV = sc && sc.values ? sc.values : {};
    const stallBuckets = [];
    for (let b = 0; b < TIME_BUCKETS; b += 1) {
      const bm = data.metrics[`sweep_stalls_bucketed{slot:${rate}_${b}}`];
      stallBuckets.push(bm && bm.values ? round(bm.values.count, 0) : 0);
    }

    points.push({
      offered_rps: rate,
      achieved_rps: round(STEP_SECONDS > 0 ? count / STEP_SECONDS : 0, 1),
      p50_ms: round(v['p(50)'] !== undefined ? v['p(50)'] : v.med),
      p90_ms: round(v['p(90)']),
      p95_ms: round(v['p(95)']),
      p99_ms: round(v['p(99)']),
      p999_ms: round(v['p(99.9)']),
      // Per-phase p95/p99 (ms) — additive; older consumers ignore it. duration
      // above == sending + waiting + receiving and omits blocked/connecting/tls,
      // so this block is the only place a connection-setup tail is visible.
      phase_ms: phaseMs,
      // Completed-request count for THIS rung. Additive field (older/other
      // consumers ignore unknown keys; the website renderer's key-presence check
      // does not include it). It lets a downstream aggregator apply the repo's
      // MIN_TAIL_SAMPLES rule (regression.js/proxy.js/streaming.js) and suppress a
      // tail percentile a rung's own sample count cannot support — the per-core
      // serving curve (perf-percore.sh) needs this because its low-C, low
      // arrival-rate rungs can dip below that floor. The percentiles above are left
      // UNSUPPRESSED here so the published knee/percentile charts keep their exact
      // contract; suppression is applied by the consumer that needs it.
      sample_count: round(count, 0),
      // Tail suppression uses measured_sample_count (post-settle); drop and
      // achieved accounting keep sample_count (whole rung).
      // null in wallclock mode: the harness splits the rung by time in Prometheus.
      measured_sample_count: WALLCLOCK ? null : steadyReqs && steadyReqs.values ? round(steadyReqs.values.count, 0) : 0,
      settle_excluded: WALLCLOCK ? null : settleReqs && settleReqs.values ? round(settleReqs.values.count, 0) : 0,
      // Whole-rung percentiles INCLUDING the onset, for audit of the exclusion.
      full_rung_ms: {
        p50_ms: round(fv['p(50)'] !== undefined ? fv['p(50)'] : fv.med),
        p95_ms: round(fv['p(95)']),
        p99_ms: round(fv['p(99)']),
        p999_ms: round(fv['p(99.9)']),
      },
      error_rate: failed && failed.values ? round(failed.values.rate, 5) : 0,
      // Raw failed/total counts, so a multi-process harness can pool error rates.
      failed_count: failed && failed.values ? round(failed.values.passes, 0) : 0,
      // Client-side drop count for this rung: > 0 means k6 could not keep up
      // with the offered arrival rate (VU starvation), so achieved_rps is bounded
      // by the CLIENT and this rung is not a valid server-ceiling candidate.
      dropped_iterations: dropped && dropped.values ? round(dropped.values.count, 0) : 0,
      // --- VU-pool diagnostics. All additive; see the header block. ---
      // vus_active_* : the CONCURRENCY this rung actually used, sampled at the
      //   start of every iteration in this rung. `max` is the peak number of VUs
      //   simultaneously in flight; if it stays well below this rung's fixed pool
      //   (pool_per_rung), the spare VUs were genuinely idle and a pool shortage
      //   CANNOT explain the rung's drops. With the fixed per-rung pool a max ABOVE
      //   the pool is impossible (the executor cannot grow) — so a sub-knee rung
      //   that still drops means its pool needs re-sizing, not that the ramp
      //   returned. (Possible off-by-one:
      //   whether vusActive counts the sampling VU itself has NOT been verified
      //   here. It does not matter for the question this field exists to answer —
      //   1-2 against a pool of 200 reads the same either way — so it is recorded
      //   as unknown rather than asserted in one direction.)
      vus_active_max: round(vaV.max, 1),
      vus_active_p95: round(vaV['p(95)'], 1),
      vus_active_avg: round(vaV.avg, 1),
      // stalls : requests in this rung slower than stall_ms_threshold. stall_*
      //   concurrency is the active-VU count AT those moments (a stall coinciding
      //   with high concurrency = VUs piling up behind a slow response, the drop
      //   mechanism). stall_time_buckets splits the rung into equal wall-time
      //   windows (earliest first) and counts stalls per window: a burst in one
      //   window = a transient stall; a flat spread = a steady limit. This is the
      //   discriminating signal, and a PROXY for drop timing (drops themselves
      //   cannot be timestamped in-script — a dropped iteration never runs code).
      // null, never 0, when not measured: stalls are counted only with VU diagnostics on, the
      // lean summary materialises neither submetric, and wall-clock mode never tags a request
      // `win`, so its steady submetric exists but stays empty.
      stalls: SWEEP.vuDiagnostics && stalls && stalls.values ? round(stalls.values.count, 0) : null,
      // Set against `stalls`, shows what the settle window removed.
      stalls_post_settle: SWEEP.vuDiagnostics && !WALLCLOCK && steadyStalls && steadyStalls.values
        ? round(steadyStalls.values.count, 0) : null,
      stall_ms_threshold: STALL_MS,
      stall_concurrency_max: round(scV.max, 1),
      stall_concurrency_avg: round(scV.avg, 1),
      stall_time_buckets: stallBuckets,
      // Wall-clock start of this rung's k6 scenario (epoch ms); null if the rung never ran.
      start_epoch_ms: rungStart && rungStart.values ? round(rungStart.values.min, 0) : null,
    });
  }
  // Whole-run VU gauges straight from k6's built-ins. vus_max = k6's own max
  // INITIALIZED (allocated) VU count for the entire run, shared across rungs;
  // vus = max concurrently-active VUs for the entire run, but
  // sampled at only 1 Hz so it UNDERSTATES the brief stall pile-ups the per-rung
  // vus_active_max (per-iteration) catches — kept only as a coarse cross-check.
  const vusMaxG = data.metrics.vus_max;
  const vusG = data.metrics.vus;
  const vusInitGlobalMax = vusMaxG && vusMaxG.values ? round(vusMaxG.values.max, 0) : null;
  // With no growth vus_max sits at exactly k6's planned peak (INIT_BASELINE). Anything
  // above it is a VU allocated mid-run — which a fixed per-rung pool
  // (preAllocatedVUs == maxVUs) can never produce, so vus_pool_grew must read false.
  const initBaseline = INIT_BASELINE;
  const out = {
    proto: SWEEP.proto,
    // Absent on results from before the onset exclusion (percentiles include it).
    latency_window: { settle_s: SETTLE_SECONDS, measured_s: STEP_SECONDS - SETTLE_SECONDS, mode: SWEEP.windowMode },
    // Wall-clock alignment for a synchronised multi-process run; absent
    // from a default single-process run.
    wallclock: !RECORD_RUNG_START && !SWEEP.leanSummary ? undefined : {
      start_at_ms: data.setup_data ? data.setup_data.start_at_ms : null,
      setup_end_ms: data.setup_data ? data.setup_data.setup_end_ms : null,
      step_s: STEP_SECONDS,
      gap_s: GAP_SECONDS,
      quiet_s: QUIET_SECONDS,
      lean_summary: SWEEP.leanSummary,
      vu_diagnostics: SWEEP.vuDiagnostics,
    },
    points,
    // Additive, namespaced, WHOLE-RUN block (deliberately not per rung — pool
    // growth cannot be attributed to a single rung here; see the note). Lets a
    // reader see at a glance whether the VU pool grew past its baseline and how
    // much concurrency the whole ladder demanded, before drilling into per-rung
    // vus_active_* and stall_time_buckets.
    vus_diagnostics: {
      // Per-rung FIXED pool (preAllocatedVUs == maxVUs within each rung), keyed by
      // offered rate. flat_override is the K6_SWEEP_PRE_VUS/MAX_VUS value when set,
      // else null (per-rung sizing active).
      pool_per_rung: Object.fromEntries(RATES.map((r) => [String(r), POOLS.get(r)])),
      flat_override: FLAT_SET ? FLAT_PRE : null,
      rung_count: points.length,
      // The no-growth expectation for the global initialized count: k6's planned
      // peak of overlapping rung reservations (see plannedVusBaseline).
      vus_initialized_baseline: initBaseline,
      // Each rung's gracefulStop (K6_SWEEP_GRACEFUL_STOP); null = k6's 30s default.
      graceful_stop: SWEEP.gracefulStop,
      // The actual global initialized high-water for the whole run.
      vus_initialized_global_max: vusInitGlobalMax,
      // The bottom-line answer to "did the pool ever grow?": true iff the global
      // initialized count exceeded k6's planned peak. With the fixed per-rung pool
      // (preAllocatedVUs == maxVUs) this MUST read false — it is now a regression
      // guard: a true here means the invariant was somehow bypassed.
      vus_pool_grew: vusInitGlobalMax === null || initBaseline === null ? null : vusInitGlobalMax > initBaseline,
      // Coarse (1 Hz) whole-run peak active VUs; per-rung vus_active_max is the
      // sensitive figure — expect this to be LOWER when stalls are brief.
      vus_concurrent_overall_max: vusG && vusG.values ? round(vusG.values.max, 0) : null,
      stall_ms_threshold: STALL_MS,
      time_buckets: TIME_BUCKETS,
      step_seconds: STEP_SECONDS,
      bucket_width_ms: round(BUCKET_WIDTH_MS, 0),
      note:
        'Item 18. Per rung: is vus_active_max below that rung pool_per_rung (VUs idle -> pool shortage cannot explain the drops) and do stall_time_buckets cluster (transient stall) or spread evenly (steady limit)? Whole-run: vus_pool_grew says whether the initialized count ever exceeded the k6 planned peak (vus_initialized_baseline: the largest sum of rung pools whose startTime..startTime+duration+gracefulStop reservations overlap). Each rung now has a FIXED pool (preAllocatedVUs == maxVUs) sized to its offered rate from the build #347 peaks, so the ramp can no longer fire: expect vus_pool_grew=false and vus_active_max < the rung pool on every sub-knee rung; a sub-knee rung that still drops means that rung pool needs re-sizing, not that the ramp returned.',
    },
  };
  const json = JSON.stringify(out, null, 2);
  const result = {};
  result[SWEEP.resultPath] = json;
  result.stdout = `\nsweep result (${SWEEP.proto}):\n${json}\n`;
  return result;
}
