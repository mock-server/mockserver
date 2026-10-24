// FILE response body vs the same body inline (STRING), measured back to back on one SUT.
//
// Per size (K6_FB_SIZES, default 1kb,64kb) two expectations answer an identical application/json
// body: /fb_ab/<size>/file from a FILE body (read from disk per request on the file read pool)
// and /fb_ab/<size>/inline from a STRING body. After a shared warm-up, each (size, arm) gets its
// own constant-arrival-rate window, one after the other, in the K6_FB_ORDER arm order, so the
// file / inline ratio isolates the read and the pool hop. setup() refuses to measure unless both
// arms answer byte-identical bodies; per request, a non-200 or a body of the wrong length counts
// as an error (fb_ab_errors). Driven by .buildkite/scripts/steps/lib/perf-file-body-ab.sh, which
// writes the files with lib/perf-file-body-ab-content.awk into the SUT's read-only mount.
//
//   k6 run -e BASE_URL=http://filebody-ab:1080 -e K6_FB_FILE_DIR=/perf-files file_body_ab.js
//
import exec from 'k6/execution';
import http from 'k6/http';
import { fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { CONFIG } from './lib/config.js';

function env(name, fallback) {
  const v = __ENV[name];
  return v === undefined || v === '' ? fallback : v;
}
function posInt(name, fallback) {
  const raw = env(name, String(fallback));
  if (!/^[1-9][0-9]*$/.test(raw)) throw new Error(`file_body_ab.js: ${name}='${raw}' is not a positive integer`);
  return Number(raw);
}
function toSeconds(name, d) {
  const re = /(\d+)(ms|s|m|h)/g;
  let total = 0; let matched = false; let t;
  while ((t = re.exec(String(d))) !== null) {
    matched = true;
    total += Number(t[1]) * { ms: 0.001, s: 1, m: 60, h: 3600 }[t[2]];
  }
  if (!matched) throw new Error(`file_body_ab.js: ${name}='${d}' is not a duration`);
  return total;
}
function round(v, dp = 3) {
  if (v === undefined || v === null || Number.isNaN(v)) return null;
  const f = 10 ** dp;
  return Math.round(v * f) / f;
}

// Must match lib/perf-file-body-ab-content.awk byte for byte (setup() checks the served file).
export function fbContent(label, bytes) {
  const head = `{"marker":"fb-ab-${label}","pad":"`;
  const tail = '"}';
  return head + 'x'.repeat(bytes - head.length - tail.length) + tail;
}

const SIZES = env('K6_FB_SIZES', '1kb,64kb').split(',').map((s) => s.trim()).filter((s) => s);
if (SIZES.length === 0 || new Set(SIZES).size !== SIZES.length) {
  throw new Error(`file_body_ab.js: K6_FB_SIZES='${__ENV.K6_FB_SIZES}' must name distinct sizes`);
}
const BYTES = {};
for (const size of SIZES) {
  const m = /^([1-9][0-9]{0,4})kb$/.exec(size);
  if (!m) throw new Error(`file_body_ab.js: size '${size}' is not <n>kb (1 to 99999)`);
  BYTES[size] = Number(m[1]) * 1024;
}
const ORDER = env('K6_FB_ORDER', 'file,inline').split(',').map((s) => s.trim());
if (ORDER.join(',') !== 'file,inline' && ORDER.join(',') !== 'inline,file') {
  throw new Error(`file_body_ab.js: K6_FB_ORDER='${__ENV.K6_FB_ORDER}' must be file,inline or inline,file`);
}
const FILE_DIR = env('K6_FB_FILE_DIR', '/perf-files').replace(/\/+$/, '');
const RATE = posInt('K6_FB_RATE', 200);
const VUS = posInt('K6_FB_VUS', 20);
const WARMUP_RATE = posInt('K6_FB_WARMUP_RATE', 100);
const WARMUP_SEC = toSeconds('K6_FB_WARMUP', env('K6_FB_WARMUP', '20s'));
const DURATION_SEC = toSeconds('K6_FB_DURATION', env('K6_FB_DURATION', '35s'));
const SETTLE_SEC = toSeconds('K6_FB_SETTLE', env('K6_FB_SETTLE', '5s'));
const GAP_SEC = toSeconds('K6_FB_GAP', env('K6_FB_GAP', '2s'));
if (WARMUP_SEC <= 0 || SETTLE_SEC >= DURATION_SEC) {
  throw new Error('file_body_ab.js: K6_FB_WARMUP must be positive and K6_FB_SETTLE shorter than K6_FB_DURATION');
}
const MEASURED_SEC = DURATION_SEC - SETTLE_SEC;
// Below this many measured requests a p95 / p99 is close to the max, so it is reported as null.
const MIN_TAIL_SAMPLES = 30;
const RESULT_PATH = env('K6_RESULT_PATH', 'file-body-ab-result.json');

// Lint seam: K6_FB_SELFTEST_DIR holds files written by the awk program; init refuses on any drift.
const SELFTEST_DIR = env('K6_FB_SELFTEST_DIR', '');
if (SELFTEST_DIR) {
  for (const size of SIZES) {
    if (open(`${SELFTEST_DIR}/fb-ab-${size}.json`) !== fbContent(size, BYTES[size])) {
      throw new Error(`file_body_ab.js: fbContent('${size}') differs from perf-file-body-ab-content.awk's file`);
    }
  }
}

const WINDOWS = [];
for (const size of SIZES) {
  for (const arm of ORDER) WINDOWS.push({ op: `${size}_${arm}`, size, arm, path: `/fb_ab/${size}/${arm}` });
}
const WINDOW_BY_OP = Object.fromEntries(WINDOWS.map((w) => [w.op, w]));
const startSec = (i) => WARMUP_SEC + GAP_SEC + i * (DURATION_SEC + GAP_SEC);

const fbErrors = new Rate('fb_ab_errors');
// Wall-clock (epoch ms) of each measured response, so min / max per op prove the order the windows ran in.
const fbAt = new Trend('fb_ab_at');

function thresholds() {
  const t = {};
  for (const { op } of WINDOWS) {
    t[`http_req_duration{op:${op}}`] = ['p(50)>=0', 'p(95)>=0', 'p(99)>=0'];
    t[`fb_ab_errors{op:${op}}`] = ['rate>=0'];
    t[`fb_ab_at{op:${op}}`] = ['min>=0'];
    t[`http_reqs{op:${op}}`] = ['count>=0'];
    t[`http_reqs{op:${op}_settle}`] = ['count>=0'];
    t[`dropped_iterations{scenario:${op}}`] = ['count>=0'];
  }
  return t;
}

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate', exec: 'warmupOp', rate: WARMUP_RATE, timeUnit: '1s',
      duration: `${WARMUP_SEC}s`, preAllocatedVUs: VUS, maxVUs: VUS,
    },
    ...Object.fromEntries(WINDOWS.map((w, i) => [w.op, {
      executor: 'constant-arrival-rate', exec: 'abOp', rate: RATE, timeUnit: '1s',
      duration: `${DURATION_SEC}s`, startTime: `${startSec(i)}s`, preAllocatedVUs: VUS, maxVUs: VUS,
    }])),
  },
  thresholds: thresholds(),
};

function hit(w, opTag) {
  const res = http.get(`${CONFIG.baseUrl}${w.path}`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: opTag, name: `GET /fb_ab/${w.size}/${w.arm}` },
  });
  fbErrors.add(!(res.status === 200 && typeof res.body === 'string' && res.body.length === BYTES[w.size]), { op: opTag });
  fbAt.add(Date.now(), { op: opTag });
}

export function setup() {
  const expectations = [];
  for (const w of WINDOWS) {
    const body = w.arm === 'file'
      ? { type: 'FILE', filePath: `${FILE_DIR}/fb-ab-${w.size}.json`, contentType: 'application/json' }
      : { type: 'STRING', string: fbContent(w.size, BYTES[w.size]), contentType: 'application/json' };
    expectations.push({
      id: `fb-ab-${w.op}`, httpRequest: { method: 'GET', path: w.path },
      httpResponse: { statusCode: 200, body }, times: { unlimited: true },
    });
  }
  const seeded = http.put(`${CONFIG.controlPlane}/expectation`, JSON.stringify(expectations),
    { headers: { 'Content-Type': 'application/json' } });
  if (seeded.status !== 201 && seeded.status !== 200) fail(`file_body_ab.js: seeding returned HTTP ${seeded.status}: ${seeded.body}`);
  const sizes = {};
  for (const size of SIZES) {
    const want = fbContent(size, BYTES[size]);
    const file = http.get(`${CONFIG.baseUrl}/fb_ab/${size}/file`);
    const inline = http.get(`${CONFIG.baseUrl}/fb_ab/${size}/inline`);
    sizes[size] = {
      file_status: file.status, inline_status: inline.status,
      file_bytes: file.body ? file.body.length : 0, inline_bytes: inline.body ? inline.body.length : 0,
      file_content_type: file.headers['Content-Type'] || null, inline_content_type: inline.headers['Content-Type'] || null,
      identical: file.status === 200 && inline.status === 200 && file.body === want && inline.body === want
        && file.headers['Content-Type'] === inline.headers['Content-Type'],
    };
    if (!sizes[size].identical) fail(`file_body_ab.js: the ${size} FILE and inline bodies are not identical: ${JSON.stringify(sizes[size])}`);
  }
  return { ok: true, sizes };
}

export function warmupOp() {
  const w = WINDOWS[exec.scenario.iterationInTest % WINDOWS.length];
  hit(w, 'warmup');
}

// The first K6_FB_SETTLE of each window is tagged <op>_settle and kept out of the percentiles.
export function abOp() {
  const w = WINDOW_BY_OP[exec.scenario.name];
  hit(w, Date.now() - exec.scenario.startTime < SETTLE_SEC * 1000 ? `${w.op}_settle` : w.op);
}

function armStats(data, op) {
  const dur = data.metrics[`http_req_duration{op:${op}}`];
  if (!dur || !dur.values) return null;
  const value = (name, field) => {
    const m = data.metrics[name];
    return m && m.values ? m.values[field] : 0;
  };
  const count = value(`http_reqs{op:${op}}`, 'count');
  const enough = count >= MIN_TAIL_SAMPLES;
  const errors = data.metrics[`fb_ab_errors{op:${op}}`];
  const at = data.metrics[`fb_ab_at{op:${op}}`];
  return {
    p50_ms: enough ? round(dur.values['p(50)']) : null,
    p95_ms: enough ? round(dur.values['p(95)']) : null,
    p99_ms: enough ? round(dur.values['p(99)']) : null,
    sample_count: count,
    throughput_rps: round(count / MEASURED_SEC),
    offered_rps: RATE,
    dropped_iterations: value(`dropped_iterations{scenario:${op}}`, 'count'),
    error_rate: count > 0 && errors && errors.values ? round(errors.values.rate, 5) : null,
    first_response_ms: count > 0 && at && at.values ? at.values.min : null,
    last_response_ms: count > 0 && at && at.values ? at.values.max : null,
    settle_excluded: value(`http_reqs{op:${op}_settle}`, 'count'),
    measured_window_s: MEASURED_SEC,
  };
}

export function handleSummary(data) {
  const sizes = {};
  for (const size of SIZES) {
    sizes[size] = { bytes: BYTES[size], file: armStats(data, `${size}_file`), inline: armStats(data, `${size}_inline`) };
  }
  const out = {
    order: ORDER, sizes, probe: data.setup_data || null,
    config: { rate_rps: RATE, vus: VUS, warmup_s: WARMUP_SEC, duration_s: DURATION_SEC, settle_s: SETTLE_SEC, gap_s: GAP_SEC },
  };
  const json = JSON.stringify(out, null, 2);
  return { [RESULT_PATH]: json, stdout: `\nfile-body A/B result:\n${json}\n` };
}
