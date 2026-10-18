// Proxy-path + TLS-handshake scenario.
//
// Forward mode: drive MockServer IN PROXY MODE rather than as a mock — absolute-URI forwarding and a
// CONNECT tunnel carrying HTTPS, both to the upstream the run already starts.
// Handshake mode: MockServer's INBOUND TLS/mTLS handshake cost, which the https_h2
// regression run amortises to ~0 by reusing one connection per VU.
//
// TWO modes (K6_PROXY_MODE), run as SEPARATE k6 invocations because they need
// mutually exclusive process-global settings (proxy env + keep-alive for forward;
// no proxy + noConnectionReuse for handshake — see lib/config.js PROXY):
//   forward    k6 run -e K6_PROXY_MODE=forward   ... proxy.js   (HTTP_PROXY/HTTPS_PROXY set on the k6 container)
//   handshake  k6 run -e K6_PROXY_MODE=handshake ... proxy.js   (K6_HS_*_URL per arm, K6_HS_CLIENT_CERT/KEY for mTLS)
//
// NAMING TRAP: ForwardPathBenchmark measures the LOAD
// GENERATOR's outbound render path, NOT proxying. This file is what actually
// benchmarks the proxy.
//
// MEASURED-WINDOW HYGIENE — cloned in shape from the CURRENT regression.js, whose
// scenario shape is what keeps percentiles honest (an older shape had tails four
// orders of magnitude out), so all of its defences are carried here: (1) STAGGER — scenarios start K6_PROXY_STAGGER apart so their
// connection-open transients do not superimpose; (2) PRE-ALLOCATE —
// preAllocatedVUs == maxVUs, so the executor NEVER allocates a VU (a fresh
// connection) mid-measurement, forbidding the connection-storm ramp; (3) SETTLE —
// the first K6_PROXY_SETTLE of each scenario is tagged op:<op>_settle and excluded
// from the percentiles (load still runs; only the known start artefact is dropped),
// with settle_excluded + delivery_ratio reported for audit. Plus warm-every-path
// (a cold heavy path at full rate is the storm's trigger) and the MIN_TAIL_SAMPLES
// tail suppression. Starting from an older revision would silently reintroduce the
// tail bug.
//
import exec from 'k6/execution';
import { CONFIG, PROXY } from './lib/config.js';
import {
  getProxiedAbsolute,
  getProxiedConnect,
  getHandshakeSimple,
  verifyProxyForwardArms,
  verifyHandshakeArms,
} from './lib/expectations.js';

const MODE = PROXY.mode;

// --- shared timing/format helpers (identical semantics to regression.js) -------
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

// Minimum measured request count below which a high percentile (p95/p99) is not a
// statistic the data can support — it collapses to ~the max. Below this the tail
// is suppressed (a sample_count is emitted instead), exactly as regression.js.
const MIN_TAIL_SAMPLES = 30;

// --- arm sets, per mode --------------------------------------------------------
// Each arm: an op (which becomes the metric key), the exec function, and (for
// non-heavy arms) a warm() touched during the warmup window so the path is JIT/TLS
// warm before measurement. `heavy: true` marks an arm that must NOT be warmed at the
// ~warmup rate; there are none here (all bodies are tiny), but the contract check
// below is kept so a future heavy arm cannot silently skip its rate-bounding, and a
// typo like `warmm:` on a normal arm cannot silently un-warm it.
let ARMS;
if (MODE === 'forward') {
  // The proxy relays a TINY body (GET /simple), so no arm is heavy (see the
  // retained-heap arithmetic in lib/config.js PROXY.rate).
  ARMS = [
    { op: 'forward_absolute', exec: 'forwardAbsoluteOp', warm: () => getProxiedAbsolute({ op: 'warmup' }) },
    { op: 'forward_connect', exec: 'forwardConnectOp', warm: () => getProxiedConnect({ op: 'warmup' }) },
  ];
} else if (MODE === 'handshake') {
  // One arm per ENABLED direct-TLS SUT. The measured metric is the TLS handshake
  // time (http_req_tls_handshaking), not request duration, and handshakes/sec is
  // the completed-iteration rate (noConnectionReuse => 1 handshake per iteration).
  const HS = [
    { op: 'tls13', url: PROXY.tls13Url },
    { op: 'mtls', url: PROXY.mtlsUrl },
    { op: 'jdk', url: PROXY.jdkUrl },
  ].filter((a) => a.url);
  if (HS.length === 0) {
    throw new Error('proxy.js handshake mode: no handshake arm enabled — set at least one of K6_HS_TLS13_URL / K6_HS_MTLS_URL / K6_HS_JDK_URL');
  }
  ARMS = HS.map((a) => ({
    op: a.op,
    url: a.url,
    exec: `${a.op}HandshakeOp`,
    warm: ((url, op) => () => getHandshakeSimple(url, op, { op: 'warmup' }))(a.url, a.op),
  }));
} else {
  throw new Error(`proxy.js: unknown K6_PROXY_MODE "${MODE}" (expected 'forward' or 'handshake')`);
}

// Fail-loud contract check on the warm/heavy invariant: a heavy arm
// must NOT declare warm; every non-heavy arm MUST declare a warm(). Both throw at
// init so a silent regression becomes a loud one.
for (const arm of ARMS) {
  if (arm.heavy && arm.warm) {
    throw new Error(`proxy.js ARMS: '${arm.op}' is heavy and must NOT declare warm`);
  }
  if (!arm.heavy && typeof arm.warm !== 'function') {
    throw new Error(`proxy.js ARMS: '${arm.op}' must declare a warm() (every non-heavy arm is warmed; a missing warm would reintroduce the Finding-3 cold-start tail)`);
  }
}

const OPS = ARMS.map((a) => a.op);

// Finding-3 invariant made unbreakable: preAllocatedVUs == maxVUs, so the executor
// can never allocate a VU (a fresh connection) mid-measurement. Both knobs exist
// (K6_PROXY_PRE_VUS / K6_PROXY_MAX_VUS are documented), but they MUST be equal —
// so rather than silently ignoring maxVUs, require it and fail loud if a caller
// sets them apart. The single VU_POOL value below then feeds BOTH scenario fields.
if (PROXY.preAllocatedVUs !== PROXY.maxVUs) {
  throw new Error(`proxy.js: K6_PROXY_PRE_VUS (${PROXY.preAllocatedVUs}) must equal K6_PROXY_MAX_VUS (${PROXY.maxVUs}) — the Finding-3 no-mid-run-allocation invariant forbids a VU ramp. Set them equal or leave both unset.`);
}
const VU_POOL = PROXY.preAllocatedVUs;

// Per-arm offered rate (req/s). Forward arms run the full PROXY.rate; handshake
// arms run the lower PROXY.handshakeRate (each iteration opens a new connection).
const ARM_RATE = MODE === 'handshake' ? PROXY.handshakeRate : PROXY.rate;
const ARM_DURATION = MODE === 'handshake' ? PROXY.handshakeDuration : PROXY.duration;
const OFFERED = Object.fromEntries(ARMS.map((a) => [a.op, a.rate || ARM_RATE]));

// Derived timing (seconds).
const WARMUP_SEC = toSeconds(PROXY.warmup);
const STAGGER_SEC = toSeconds(PROXY.stagger);
const SETTLE_SEC = toSeconds(PROXY.settle);
const DURATION_SEC = toSeconds(ARM_DURATION);
const MEASURED_WINDOW_SEC = DURATION_SEC - SETTLE_SEC;

function startOffsetSec(op) {
  return WARMUP_SEC + OPS.indexOf(op) * STAGGER_SEC;
}

// Requests before a scenario's settle boundary are tagged op:<op>_settle so they
// are excluded from the measured submetric — whole-test elapsed time vs the known
// measured-window start, robust regardless of which VU runs the iteration.
function phaseTag(op) {
  const elapsedSec = exec.instance.currentTestRunDuration / 1000;
  const measuredStartSec = startOffsetSec(op) + SETTLE_SEC;
  return elapsedSec >= measuredStartSec ? op : `${op}_settle`;
}

// The trend metric each arm's percentiles come from: forward arms measure request
// duration (the relay); handshake arms measure the TLS handshake time itself.
const TREND = MODE === 'handshake' ? 'http_req_tls_handshaking' : 'http_req_duration';

// Materialise per-op submetrics via always-passing thresholds (notify-only, >=0
// never fails) so handleSummary can read p50/p95/p99, the failed-rate and the
// per-op request count + the excluded settle count + dropped iterations.
function proxyThresholds(ops) {
  const t = {};
  for (const op of ops) {
    t[`${TREND}{op:${op}}`] = ['p(50)>=0', 'p(95)>=0', 'p(99)>=0'];
    t[`http_req_failed{op:${op}}`] = ['rate>=0'];
    t[`http_reqs{op:${op}}`] = ['count>=0'];
    t[`dropped_iterations{scenario:${op}}`] = ['count>=0'];
    t[`http_reqs{op:${op}_settle}`] = ['count>=0'];
  }
  return t;
}

function measuredScenario(arm) {
  // preAllocatedVUs == maxVUs per arm — the Finding-3 no-mid-run-allocation
  // invariant (VU_POOL is validated equal to K6_PROXY_MAX_VUS at load time above).
  // If a transient exceeds the pool k6 DROPS (counted), never ramps.
  const vus = arm.vus || VU_POOL;
  return {
    executor: 'constant-arrival-rate',
    exec: arm.exec,
    rate: arm.rate || ARM_RATE,
    timeUnit: arm.timeUnit || '1s',
    duration: ARM_DURATION,
    startTime: `${startOffsetSec(arm.op)}s`,
    preAllocatedVUs: vus,
    maxVUs: vus,
  };
}

// mTLS client certificate (handshake mode only): presented to the mtls arm's SUT.
// Read at init from the mounted PEM paths. Without it the mTLS handshake is
// rejected by the server — which is the negative control the run also exercises.
let tlsAuth;
if (MODE === 'handshake' && PROXY.mtlsUrl && PROXY.clientCertPath && PROXY.clientKeyPath) {
  const mtlsHost = PROXY.mtlsUrl.replace(/^[a-z]+:\/\//i, '').split(':')[0];
  tlsAuth = [{
    domains: [mtlsHost],
    cert: open(PROXY.clientCertPath),
    key: open(PROXY.clientKeyPath),
  }];
}

export const options = {
  // All proxy/handshake targets are the local self-signed perf rig, so TLS
  // verification is skipped (overridable). The connect + handshake arms depend on
  // this to reach the upstream / SUTs.
  insecureSkipTLSVerify: true,
  // handshake mode: force a FRESH TCP+TLS handshake on every iteration so handshake
  // cost is measured, never amortised. Forward mode reuses connections (a real
  // proxy client), so the measured latency is the RELAY, not handshakes.
  noConnectionReuse: MODE === 'handshake',
  ...(tlsAuth ? { tlsAuth } : {}),
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      exec: 'warmupOp',
      rate: Math.max(10, Math.round(ARM_RATE / 4)),
      timeUnit: '1s',
      duration: PROXY.warmup,
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
    ...Object.fromEntries(ARMS.map((arm) => [arm.op, measuredScenario(arm)])),
  },
  thresholds: proxyThresholds(OPS),
};

export function setup() {
  if (MODE === 'forward') {
    verifyProxyForwardArms();
  } else {
    verifyHandshakeArms();
  }
}

export function warmupOp() {
  for (const arm of ARMS) {
    if (!arm.heavy) {
      arm.warm();
    }
  }
}

// --- forward-mode exec functions ----------------------------------------------
export function forwardAbsoluteOp() {
  getProxiedAbsolute({ op: phaseTag('forward_absolute') });
}
export function forwardConnectOp() {
  getProxiedConnect({ op: phaseTag('forward_connect') });
}

// --- handshake-mode exec functions (one per enabled arm) ----------------------
export function tls13HandshakeOp() {
  getHandshakeSimple(PROXY.tls13Url, phaseTag('tls13'));
}
export function mtlsHandshakeOp() {
  getHandshakeSimple(PROXY.mtlsUrl, phaseTag('mtls'));
}
export function jdkHandshakeOp() {
  getHandshakeSimple(PROXY.jdkUrl, phaseTag('jdk'));
}

export function teardown() {
  // Nothing to reset: forward mode never seeds the SUT (it proxies), and the
  // handshake SUTs (seeded by the run step) are torn down with the run's cleanup.
  // A reset here would either be a no-op or, in handshake mode where BASE_URL is
  // unset, hit a bogus localhost control plane inside the k6 container.
}

// --- result assembly -----------------------------------------------------------
// Forward mode emits a .behaviours object (op_proto keys) IDENTICAL in shape to
// regression.js, so perf-test-compare.sh picks the arms up with zero metric-loop
// changes. Handshake mode emits a .tls_handshake object keyed by arm; the run step
// augments each arm with server CPU + JVM allocation per handshake.
export function handleSummary(data) {
  const proto = PROXY.proto;
  const measuredWindowSec = MEASURED_WINDOW_SEC > 0 ? MEASURED_WINDOW_SEC : DURATION_SEC;
  const perArm = {};
  for (const op of OPS) {
    const trend = data.metrics[`${TREND}{op:${op}}`];
    const failed = data.metrics[`http_req_failed{op:${op}}`];
    const reqs = data.metrics[`http_reqs{op:${op}}`];
    if (!trend || !trend.values) {
      continue;
    }
    const count = reqs && reqs.values ? reqs.values.count : 0;
    const settleReqs = data.metrics[`http_reqs{op:${op}_settle}`];
    const settleExcluded = settleReqs && settleReqs.values ? settleReqs.values.count : 0;
    const dropped = data.metrics[`dropped_iterations{scenario:${op}}`];
    const droppedCount = dropped && dropped.values ? dropped.values.count : 0;
    const throughput = round(measuredWindowSec > 0 ? count / measuredWindowSec : 0);
    const offered = OFFERED[op] || ARM_RATE;
    const enoughForTail = count >= MIN_TAIL_SAMPLES;
    const p50 = round(trend.values['p(50)'] !== undefined ? trend.values['p(50)'] : trend.values.med);
    const p95 = enoughForTail ? round(trend.values['p(95)']) : null;
    const p99 = enoughForTail ? round(trend.values['p(99)']) : null;
    const errorRate = failed && failed.values ? round(failed.values.rate, 5) : 0;
    perArm[op] = {
      p50, p95, p99, count, throughput, offered, droppedCount, settleExcluded, errorRate,
    };
  }

  let out;
  if (MODE === 'forward') {
    const behaviours = {};
    for (const op of OPS) {
      const a = perArm[op];
      if (!a) { continue; }
      behaviours[`${op}_${proto}`] = {
        p50_ms: a.p50,
        p95_ms: a.p95,
        p99_ms: a.p99,
        sample_count: round(a.count, 0),
        throughput_rps: a.throughput,
        offered_rps: a.offered,
        dropped_iterations: round(a.droppedCount, 0),
        delivery_ratio: round(a.offered > 0 ? a.throughput / a.offered : null, 4),
        error_rate: a.errorRate,
        settle_excluded: round(a.settleExcluded, 0),
        measured_window_s: round(measuredWindowSec, 3),
      };
    }
    out = { proto, behaviours };
  } else {
    // handshake mode: keyed by arm short name (tls13/mtls/jdk). p50/p95 are the TLS
    // handshake time; handshakes_per_s is the completed rate (1 handshake / iter).
    const tls_handshake = {};
    for (const op of OPS) {
      const a = perArm[op];
      if (!a) { continue; }
      tls_handshake[op] = {
        handshake_p50_ms: a.p50,
        handshake_p95_ms: a.p95,
        handshake_p99_ms: a.p99,
        handshakes_per_s: a.throughput,
        offered_rps: a.offered,
        sample_count: round(a.count, 0),
        dropped_iterations: round(a.droppedCount, 0),
        delivery_ratio: round(a.offered > 0 ? a.throughput / a.offered : null, 4),
        error_rate: a.errorRate,
        settle_excluded: round(a.settleExcluded, 0),
        measured_window_s: round(measuredWindowSec, 3),
        // cpu_ms_per_handshake + alloc_kb_per_handshake are filled in by
        // perf-test-run.sh from server-side sampling across this arm's window
        // (k6 cannot see the SUT's CPU/JVM allocation). Present as null here so the
        // schema is stable and the run step only has to set the numbers.
        cpu_ms_per_handshake: null,
        alloc_kb_per_handshake: null,
      };
    }
    out = { proto, tls_handshake };
  }

  const json = JSON.stringify(out, null, 2);
  const result = {};
  result[PROXY.resultPath] = json;
  result.stdout = `\nproxy result (mode=${MODE}, proto=${proto}):\n${json}\n`;
  return result;
}
