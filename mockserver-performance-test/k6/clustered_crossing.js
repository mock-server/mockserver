// Clustered per-request-CROSSING arm.
//
// regression.js (run unchanged alongside this, against the same two targets)
// measures the clustered MEMBERSHIP steady-state overhead: its arms seed
// times:{unlimited:true} and match against each node's LOCAL compiled-matcher
// cache, so NOTHING crosses the JGroups network on the request path — the ratio
// there is ~1.0 and answers "what does merely being a clustered member cost".
//
// This script measures the OTHER half of the clustered A/B — what "moving expectation
// reads/writes onto a network" actually costs PER REQUEST — by driving a
// workload that DOES cross the network on every matched request:
//
//   BOUNDED Times. An expectation with times:{remainingTimes:N} (N huge, so it
//   never exhausts over the run) enforces its per-expectation limit. With
//   clusterSharedTimesEnabled=true (the DEFAULT when clustered), each match runs
//   a shared-Times compareAndSet through the StateBackend
//   (RequestMatchers -> KeyValueStore.compareAndSet). On the in-memory CONTROL
//   that CAS is a local ConcurrentHashMap swap; on the CLUSTER it is a SYNCHRONOUS
//   REPL_SYNC round trip (InfinispanKeyValueStore.compareAndSet -> cache.replace,
//   replicated to the peer before returning). So the clustered/control ratio for
//   THIS arm is the real per-request network cost — the number the clustered A/B
//   exists to find. The contrast with regression.js's ~1.0 non-crossing arms is the result.
//
// Emits the SAME {proto, behaviours:{<op>_<proto>:{...}}} shape as regression.js
// so perf-test-run.sh's ratio machinery and the .behaviours merge treat the
// crossing arm identically (op name: `crossing`). To reduce single-counter CAS
// contention (which would otherwise dominate on BOTH arms and blur the network
// signal), the load is spread over CLU_CROSS_KEYS distinct bounded expectations.
//
//   k6 run -e BASE_URL=http://clu-a:1080 -e PROTO=clustered clustered_crossing.js
//
import exec from 'k6/execution';
import http from 'k6/http';
import { fail } from 'k6';
import { CONFIG } from './lib/config.js';

function env(name, fallback) {
  const v = __ENV[name];
  return v === undefined || v === '' ? fallback : v;
}
function num(name, fallback) {
  const v = env(name, undefined);
  return v === undefined ? fallback : Number(v);
}
function toSeconds(d) {
  const re = /(\d+)(ms|s|m|h)/g;
  let total = 0; let matched = false; let t;
  while ((t = re.exec(String(d))) !== null) {
    matched = true;
    total += Number(t[1]) * { ms: 0.001, s: 1, m: 60, h: 3600 }[t[2]];
  }
  if (!matched) throw new Error(`toSeconds: cannot parse "${d}"`);
  return total;
}
function round(v, dp = 3) {
  if (v === undefined || v === null || Number.isNaN(v)) return null;
  const f = 10 ** dp;
  return Math.round(v * f) / f;
}

const PROTO = env('PROTO', CONFIG.baseUrl.startsWith('https') ? 'https_h2' : 'http');
const RATE = num('K6_CLU_CROSS_RATE', 50); // offered req/s
const WARMUP = env('K6_CLU_CROSS_WARMUP', env('K6_REG_WARMUP', '15s'));
const DURATION = env('K6_CLU_CROSS_DURATION', env('K6_REG_DURATION', '45s'));
const SETTLE = env('K6_CLU_CROSS_SETTLE', '10s');
const VUS = num('K6_CLU_CROSS_VUS', 30);
// Distinct bounded expectations to spread the CAS across (less single-counter
// contention, so the measured delta is the network round trip, not lock waiting).
const KEYS = num('K6_CLU_CROSS_KEYS', 20);
// remainingTimes per expectation: huge so it NEVER exhausts (an exhausted
// expectation 404s and would poison error_rate). Bounded (not unlimited) is what
// makes it cross — the value only has to outlast the run.
const REMAINING = num('K6_CLU_CROSS_REMAINING', 1000000000);
// Same low-sample tail suppression rule as regression.js.
const MIN_TAIL_SAMPLES = 30;
const RESULT_PATH = env('K6_CLU_CROSS_RESULT_PATH', 'clustered-crossing-result.json');

const WARMUP_SEC = toSeconds(WARMUP);
const SETTLE_SEC = toSeconds(SETTLE);
const DURATION_SEC = toSeconds(DURATION);
const MEASURED_WINDOW_SEC = DURATION_SEC - SETTLE_SEC;

function crossPath(i) { return `/crossing-${i % KEYS}`; }

// Requests before the settle boundary are tagged crossing_settle and excluded
// from the measured percentiles (same hygiene as regression.js).
function phaseTag() {
  const elapsedSec = exec.instance.currentTestRunDuration / 1000;
  return elapsedSec >= WARMUP_SEC + SETTLE_SEC ? 'crossing' : 'crossing_settle';
}

export const options = {
  insecureSkipTLSVerify: CONFIG.insecureSkipTLSVerify,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      exec: 'warmupOp',
      rate: Math.max(10, Math.round(RATE / 2)),
      timeUnit: '1s',
      duration: WARMUP,
      preAllocatedVUs: VUS,
      maxVUs: VUS,
    },
    crossing: {
      executor: 'constant-arrival-rate',
      exec: 'crossingOp',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      startTime: WARMUP,
      // preAllocatedVUs == maxVUs (Finding-3 no-mid-run-allocation invariant).
      preAllocatedVUs: VUS,
      maxVUs: VUS,
    },
  },
  thresholds: {
    'http_req_duration{op:crossing}': ['p(50)>=0', 'p(95)>=0', 'p(99)>=0'],
    'http_req_failed{op:crossing}': ['rate>=0'],
    'http_reqs{op:crossing}': ['count>=0'],
    'http_reqs{op:crossing_settle}': ['count>=0'],
    'dropped_iterations{scenario:crossing}': ['count>=0'],
  },
};

export function setup() {
  http.put(`${CONFIG.controlPlane}/reset`, null, { headers: { 'Content-Type': 'application/json', ...CONFIG.keepAliveHeaders } });
  const expectations = [];
  for (let i = 0; i < KEYS; i++) {
    expectations.push({
      httpRequest: { path: `/crossing-${i}` },
      httpResponse: { statusCode: 200, body: 'crossing' },
      // BOUNDED (not unlimited) => shared-Times CAS per match => a REPL_SYNC
      // network round trip on the clustered backend.
      times: { remainingTimes: REMAINING },
    });
  }
  const res = http.put(`${CONFIG.controlPlane}/expectation`, JSON.stringify(expectations),
    { headers: { 'Content-Type': 'application/json', ...CONFIG.keepAliveHeaders } });
  if (res.status !== 201 && res.status !== 200) {
    fail(`failed to seed crossing expectations: HTTP ${res.status} ${res.body}`);
  }
  // Fail loud if the bounded arm does not actually answer 200 (a misconfigured
  // backend would otherwise baseline a silent 100%-error arm as a result).
  const probe = http.get(`${CONFIG.baseUrl}/crossing-0`, { headers: CONFIG.keepAliveHeaders });
  if (probe.status !== 200) {
    fail(`crossing probe did not return 200: HTTP ${probe.status}`);
  }
}

export function warmupOp() {
  http.get(`${CONFIG.baseUrl}${crossPath(exec.scenario.iterationInTest)}`,
    { headers: CONFIG.keepAliveHeaders, tags: { op: 'warmup' } });
}

export function crossingOp() {
  http.get(`${CONFIG.baseUrl}${crossPath(exec.scenario.iterationInTest)}`,
    { headers: CONFIG.keepAliveHeaders, tags: { op: phaseTag() } });
}

export function teardown() {
  http.put(`${CONFIG.controlPlane}/reset`, null, { headers: { 'Content-Type': 'application/json', ...CONFIG.keepAliveHeaders } });
}

export function handleSummary(data) {
  const dur = data.metrics['http_req_duration{op:crossing}'];
  const failed = data.metrics['http_req_failed{op:crossing}'];
  const reqs = data.metrics['http_reqs{op:crossing}'];
  const settleReqs = data.metrics['http_reqs{op:crossing_settle}'];
  const dropped = data.metrics['dropped_iterations{scenario:crossing}'];
  const measuredWindowSec = MEASURED_WINDOW_SEC > 0 ? MEASURED_WINDOW_SEC : DURATION_SEC;
  const behaviours = {};
  if (dur && dur.values) {
    const count = reqs && reqs.values ? reqs.values.count : 0;
    const throughput = round(measuredWindowSec > 0 ? count / measuredWindowSec : 0);
    const enoughForTail = count >= MIN_TAIL_SAMPLES;
    behaviours[`crossing_${PROTO}`] = {
      p50_ms: round(dur.values['p(50)'] !== undefined ? dur.values['p(50)'] : dur.values.med),
      p95_ms: enoughForTail ? round(dur.values['p(95)']) : null,
      p99_ms: enoughForTail ? round(dur.values['p(99)']) : null,
      sample_count: round(count, 0),
      throughput_rps: throughput,
      offered_rps: RATE,
      dropped_iterations: round(dropped && dropped.values ? dropped.values.count : 0, 0),
      delivery_ratio: round(RATE > 0 ? throughput / RATE : null, 4),
      error_rate: failed && failed.values ? round(failed.values.rate, 5) : 0,
      settle_excluded: round(settleReqs && settleReqs.values ? settleReqs.values.count : 0, 0),
      measured_window_s: round(measuredWindowSec, 3),
      crosses_network: true,
    };
  }
  const out = { proto: PROTO, behaviours };
  const json = JSON.stringify(out, null, 2);
  const result = {};
  result[RESULT_PATH] = json;
  result.stdout = `\nclustered crossing result (${PROTO}):\n${json}\n`;
  return result;
}
