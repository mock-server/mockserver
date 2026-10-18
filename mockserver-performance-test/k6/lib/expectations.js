// Expectation seeding + the per-request actions exercised by the scenarios.
//
// Parity with the historical Locust harness:
//   - the same 4 expectations are seeded (the request matches the LAST one, so
//     the matcher does a near-full scan — the realistic worst case)
//   - the `match` action GETs /simple (data-plane hot path)
//   - the `create` action PUTs a stable-id /churn expectation (control-plane
//     churn — an in-place REPLACE, so it never grows the expectation store; see
//     createSimpleExpectation below for why this differs from the legacy Locust
//     task, which self-inflicted a 404 storm on the soak's match arm)
//   - the `forward` action GETs /forward (proxy/override path)
//
// New scenarios add a large-body match and a regex-matcher workload to exercise
// the body-decode and regex paths called out in the performance plan.

import http from 'k6/http';
import { check, fail } from 'k6';
import { SharedArray } from 'k6/data';
import { CONFIG, FORWARD, REGRESSION, PROXY, STREAMING, COVERAGE } from './config.js';

// The 4 seeded expectations — byte-for-byte the same shapes as the legacy
// expectations.json so recorded baselines remain comparable.
export const SEED_EXPECTATIONS = [
  {
    httpRequest: { path: '/not_simple' },
    httpResponse: { statusCode: 200, body: 'some not simple response' },
    times: { unlimited: true },
  },
  {
    httpRequest: { method: 'POST', path: '/simple' },
    httpResponse: { statusCode: 200, body: 'some simple POST response' },
    times: { unlimited: true },
  },
  {
    httpRequest: { path: '/forward' },
    httpOverrideForwardedRequest: {
      httpRequest: { headers: { host: ['127.0.0.1:1080'] }, path: '/simple' },
    },
    times: { unlimited: true },
  },
  {
    httpRequest: { path: '/simple' },
    httpResponse: { statusCode: 200, body: 'some simple response' },
    times: { unlimited: true },
  },
];

// A ~4 KB JSON expectation + matching request body to exercise the body-decode
// and JSON-match path (BodyDecoderEncoder / JsonStringMatcher in the plan).
const LARGE_BODY = JSON.stringify({
  items: Array.from({ length: 50 }, (_, i) => ({
    id: i,
    name: `item-${i}`,
    tags: ['alpha', 'beta', 'gamma'],
    nested: { value: i * 7, label: `label-${i}` },
  })),
});

export const LARGE_BODY_EXPECTATION = {
  httpRequest: {
    method: 'POST',
    path: '/large',
    body: { type: 'JSON', json: LARGE_BODY, matchType: 'ONLY_MATCHING_FIELDS' },
  },
  httpResponse: { statusCode: 200, body: 'matched large body' },
  times: { unlimited: true },
};

// A regex path matcher to exercise the RegexStringMatcher / timeout-executor
// path (plan A1.3).
export const REGEX_EXPECTATION = {
  httpRequest: { path: '/regex/[a-z0-9]+/resource' },
  httpResponse: { statusCode: 200, body: 'matched regex' },
  times: { unlimited: true },
};

// A Velocity response-template expectation to exercise the dynamic
// response-generation path (TemplateEngine), the heaviest always-available
// per-request CPU path that needs no external responder. VELOCITY is the
// default engine and is always on the classpath, so this runs on the stock
// image. (Object/class callbacks need a connected websocket responder / a class
// on the classpath — deferred to a v2 nullable `callback` behaviour.)
export const TEMPLATE_EXPECTATION = {
  httpRequest: { path: '/template' },
  httpResponseTemplate: {
    templateType: 'VELOCITY',
    template: '{ "statusCode": 200, "body": "path=$!request.path method=$!request.method" }',
  },
  times: { unlimited: true },
};

// Mustache response-template arm. jmustache is a NON-optional core
// dependency, so this renders on the stock image alongside Velocity. Mustache
// reads request fields via {{request.path}} / {{request.method}}.
export const MUSTACHE_TEMPLATE_EXPECTATION = {
  httpRequest: { path: '/template_mustache' },
  httpResponseTemplate: {
    templateType: 'MUSTACHE',
    template: '{ "statusCode": 200, "body": "path={{request.path}} method={{request.method}}" }',
  },
  times: { unlimited: true },
};

// JavaScript response-template arm. The GraalJS engine is an OPTIONAL
// dependency present only in the -graaljs image variant; a JavaScript template on
// the stock image throws (fail-loud) and the request 500s. So this arm is seeded
// ONLY when REGRESSION.jsTemplate is on (perf-test-run.sh, running the -graaljs
// image), and regression.js probes it in setup() so an absent engine fails the
// run loudly rather than recording a silent 100%-error arm. A JavaScript template
// is the BARE BODY of the render function (the engine wraps it and supplies
// `request`); it returns the response object — do NOT wrap it in your own
// `function handle(){}`, which would double-wrap and yield a null response body.
export const JS_TEMPLATE_EXPECTATION = {
  httpRequest: { path: '/template_javascript' },
  httpResponseTemplate: {
    templateType: 'JAVASCRIPT',
    template: "return { 'statusCode': 200, 'body': 'path=' + request.path + ' method=' + request.method };",
  },
  times: { unlimited: true },
};

// Build a JSON body of approximately `targetBytes` carrying a fixed
// top-level `marker` field. The matching expectation matches ONLY the marker
// (ONLY_MATCHING_FIELDS), so the server must DECODE the whole posted body (the
// size axis) while the match traversal stays constant — the variable under test
// is body size, not match complexity.
function buildLargeJson(targetBytes, marker) {
  const head = `{"marker":"${marker}","filler":[`;
  const tail = ']}';
  // Each element is a fixed-width quoted token plus a comma; size to target.
  const token = (i) => `"item-${String(i).padStart(9, '0')}"`;
  const parts = [];
  let size = head.length + tail.length;
  let i = 0;
  while (size < targetBytes) {
    const t = token(i);
    parts.push(t);
    size += t.length + 1; // + comma
    i += 1;
  }
  return `${head}${parts.join(',')}${tail}`;
}

// The large bodies are held in a SharedArray so they are built ONCE per k6
// process and shared across all VUs, rather than re-materialised in every VU's
// init context (which at 10 MB across the whole VU pool would exhaust the client).
export const LARGE_BODIES = new SharedArray('regression-large-bodies', () => [
  { marker: 'large-1mb', body: buildLargeJson(REGRESSION.large1mbBytes, 'large-1mb') },
  { marker: 'large-10mb', body: buildLargeJson(REGRESSION.large10mbBytes, 'large-10mb') },
]);

// A large-body match expectation keyed on the small fixed marker only.
function largeBodyExpectation(path, marker) {
  return {
    httpRequest: {
      method: 'POST',
      path,
      body: { type: 'JSON', json: JSON.stringify({ marker }), matchType: 'ONLY_MATCHING_FIELDS' },
    },
    httpResponse: { statusCode: 200, body: `matched ${marker}` },
    times: { unlimited: true },
  };
}

export const LARGE_1MB_EXPECTATION = largeBodyExpectation('/large_1mb', 'large-1mb');
export const LARGE_10MB_EXPECTATION = largeBodyExpectation('/large_10mb', 'large-10mb');

// File-backed RESPONSE body arm (FileBodyMaterialiser path). The file
// must exist on the SUT filesystem; perf-test-run.sh generates and mounts it and
// passes its server-side path via K6_REG_FILE_BODY_PATH. When that is empty the
// arm is absent.
export function fileBodyExpectation(filePath) {
  return {
    httpRequest: { path: '/large_file' },
    httpResponse: {
      statusCode: 200,
      body: { type: 'FILE', filePath, contentType: 'application/json' },
    },
    times: { unlimited: true },
  };
}

// Build the /forward expectation routed at the given upstream host. Uses
// httpOverrideForwardedRequest (a forward action that applies overrides then
// forwards), routing /forward -> <host>/simple. For a regression baseline the
// host is a DEDICATED upstream so the forward latency is not contaminated by the
// matching load on the instance under measurement.
export function forwardExpectation(host) {
  return {
    httpRequest: { path: '/forward' },
    httpOverrideForwardedRequest: {
      httpRequest: { headers: { host: [host] }, path: '/simple' },
    },
    times: { unlimited: true },
  };
}

// --- LLM/SSE streaming under concurrency -------------------------------------
//
// Build an httpSseResponse expectation whose `events` each carry a DETERMINISTIC
// per-event delay (no streaming-physics jitter), so the server's inter-token
// timing is a clean actual-minus-requested measurement. Each event's `data` is a
// short "t<idx>" token kept small ON PURPOSE — the whole events list is retained
// per matched request in the count-bounded event-log ring, so bytesPerToken
// drives the retained-heap arithmetic documented in config.js STREAMING.
//
// Every event including the first carries the delay so the reader's inter-arrival
// gaps between CONSECUTIVE data lines are all governed by the same requested
// delay (the reader discards the first gap, which also absorbs connection/TTFB
// overhead — see tools/sse-fidelity-reader.py).
export function streamingSseExpectation(path, tokens, delayMs) {
  const events = [];
  for (let i = 0; i < tokens; i += 1) {
    events.push({ data: `t${i}`, delay: { timeUnit: 'MILLISECONDS', value: delayMs } });
  }
  return {
    httpRequest: { path },
    httpSseResponse: { statusCode: 200, events },
    times: { unlimited: true },
  };
}

// Seed the streaming.js expectations: the SSE stream (the load) and the
// plain /simple match used by the within-run A/B. When K6_STREAM_MATCH_DELAY_MS
// > 0 a fixed server-side delay is added to the match response so a run can prove
// the match percentiles still move with a REAL slowdown (positive control).
export function seedStreaming() {
  const matchResponse = { statusCode: 200, body: 'some simple response' };
  if (STREAMING.matchDelayMs > 0) {
    matchResponse.delay = { timeUnit: 'MILLISECONDS', value: STREAMING.matchDelayMs };
  }
  const expectations = [
    { httpRequest: { path: STREAMING.matchPath }, httpResponse: matchResponse, times: { unlimited: true } },
    streamingSseExpectation(STREAMING.streamPath, STREAMING.tokens, STREAMING.delayMs),
  ];
  const res = http.put(`${CONFIG.controlPlane}/expectation`, JSON.stringify(expectations), jsonParams());
  if (res.status !== 201 && res.status !== 200) {
    fail(`failed to seed streaming expectations: HTTP ${res.status} ${res.body}`);
  }
  return res;
}

// Fail-loud probe of the streaming arms (streaming.js setup()). Both the SSE
// stream and the match path must answer before a run baselines their numbers —
// a stream that 404s would otherwise record a silent 100%-error load arm and a
// heap/fidelity measurement of NOTHING.
export function verifyStreamingArms() {
  const m = http.get(`${CONFIG.baseUrl}${STREAMING.matchPath}`, { headers: CONFIG.keepAliveHeaders });
  if (m.status !== 200) {
    fail(`streaming match arm: GET ${STREAMING.matchPath} returned HTTP ${m.status} — the SUT is not seeded. Body: ${m.body}`);
  }
  // The SSE stream returns the whole (delayed) body to k6's buffering client, so
  // this probe also blocks for ~tokens×delay ms; keep tokens modest. A 200 with a
  // text/event-stream content-type confirms the streaming action is wired.
  const s = http.get(`${CONFIG.baseUrl}${STREAMING.streamPath}`, { headers: CONFIG.keepAliveHeaders });
  if (s.status !== 200) {
    fail(`streaming arm: GET ${STREAMING.streamPath} returned HTTP ${s.status} — the httpSseResponse expectation is not serving. Body: ${s.body}`);
  }
}

// Actions (tagged so k6 reports per-operation).
export function getStream(extraTags) {
  // A VU calling this blocks for the whole (delayed) stream, so a constant-VUs pool
  // of N holds ~N streams open — the concurrency knob. No `check` on the body: the
  // measurement of interest is server-side (heap/fidelity), and a status check adds
  // no value while the VU is occupied for seconds per iteration.
  const res = http.get(`${CONFIG.baseUrl}${STREAMING.streamPath}`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'stream', name: `GET ${STREAMING.streamPath} (SSE)`, ...(extraTags || {}) },
    // The response body is discarded (responseType none) so k6 does not retain the
    // whole streamed body per VU — the client-side memory footprint of holding N
    // concurrent streams open must not itself become the bottleneck.
    responseType: 'none',
  });
  return res;
}

export function getStreamMatch(op, extraTags) {
  const res = http.get(`${CONFIG.baseUrl}${STREAMING.matchPath}`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op, name: `GET ${STREAMING.matchPath}`, ...(extraTags || {}) },
  });
  check(res, { [`${op}: 200`]: (r) => r.status === 200 });
  return res;
}

function jsonParams(extraTags) {
  return {
    headers: { 'Content-Type': 'application/json', ...CONFIG.keepAliveHeaders },
    tags: extraTags || {},
  };
}

// --- lifecycle -------------------------------------------------------------

// Seed the base expectations. Called from each scenario's setup(). Fails the
// run loudly if MockServer is unreachable or rejects the expectations.
export function seedExpectations(extra = []) {
  const payload = JSON.stringify([...SEED_EXPECTATIONS, ...extra]);
  const res = http.put(`${CONFIG.controlPlane}/expectation`, payload, jsonParams());
  if (res.status !== 201 && res.status !== 200) {
    fail(`failed to seed expectations: HTTP ${res.status} ${res.body}`);
  }
  return res;
}

export function resetMockServer() {
  return http.put(`${CONFIG.controlPlane}/reset`, null, jsonParams());
}

// Seed the expectations exercised by regression.js / growth.js: the static
// /simple (mock match), /template (dynamic response), /forward (forward action
// to the upstream — or self when K6_FORWARD_SELF=true), and the large JSON body.
// The upstream MockServer must itself be seeded with a /simple response (done by
// perf-test-run.sh) for the forward behaviour to return 200.
export function seedRegression() {
  const host = FORWARD.forwardSelf ? '127.0.0.1:1080' : FORWARD.upstreamHost;
  // Self-test hook: when K6_REG_MATCH_DELAY_MS > 0, add a fixed server-side delay
  // to the /simple (match) response so a run proves the measured percentiles
  // still move with a REAL server slowdown. Off by default (delay omitted).
  const simpleResponse = { statusCode: 200, body: 'some simple response' };
  if (REGRESSION.matchDelayMs > 0) {
    simpleResponse.delay = { timeUnit: 'MILLISECONDS', value: REGRESSION.matchDelayMs };
  }
  const expectations = [
    {
      httpRequest: { path: '/simple' },
      httpResponse: simpleResponse,
      times: { unlimited: true },
    },
    TEMPLATE_EXPECTATION,
    MUSTACHE_TEMPLATE_EXPECTATION, // always on (jmustache is bundled)
    forwardExpectation(host),
    LARGE_BODY_EXPECTATION, // 4 KB (existing `large` arm)
    LARGE_1MB_EXPECTATION,
    LARGE_10MB_EXPECTATION,
  ];
  // The JavaScript arm needs GraalJS; seed it only when enabled.
  if (REGRESSION.jsTemplate) {
    expectations.push(JS_TEMPLATE_EXPECTATION);
  }
  // The file-backed arm needs a server-side file; seed only when set.
  if (REGRESSION.fileBodyPath) {
    expectations.push(fileBodyExpectation(REGRESSION.fileBodyPath));
  }
  const res = http.put(`${CONFIG.controlPlane}/expectation`, JSON.stringify(expectations), jsonParams());
  if (res.status !== 201 && res.status !== 200) {
    fail(`failed to seed regression expectations: HTTP ${res.status} ${res.body}`);
  }
  return res;
}

// Fail-loud verification of the OPTIONAL arms (JavaScript template, file-backed
// body). Both depend on server-side capability that may be absent (a
// non-GraalJS image; a missing/unreadable file) and would otherwise record a
// silent 100%-error arm that reads as a result. Probe each enabled arm ONCE and
// fail() the whole run if it does not answer 200 — a broken arm must abort the
// run, never be baselined as a zero. Called from regression.js setup().
export function verifyRegressionArms() {
  if (REGRESSION.jsTemplate) {
    const res = http.get(`${CONFIG.baseUrl}/template_javascript`, { headers: CONFIG.keepAliveHeaders });
    if (res.status !== 200) {
      fail(
        `JavaScript template arm (K6_REG_JS_TEMPLATE) is enabled but /template_javascript returned HTTP ${res.status} — ` +
        `the SUT image almost certainly lacks the GraalJS engine (use a -graaljs image, or set K6_REG_JS_TEMPLATE=false). ` +
        `Body: ${res.body}`,
      );
    }
  }
  if (REGRESSION.fileBodyPath) {
    const res = http.get(`${CONFIG.baseUrl}/large_file`, { headers: CONFIG.keepAliveHeaders });
    if (res.status !== 200) {
      fail(
        `File-backed body arm (K6_REG_FILE_BODY_PATH=${REGRESSION.fileBodyPath}) is enabled but /large_file returned ` +
        `HTTP ${res.status} — the SUT cannot read that file (check the mount/path). Body: ${res.body}`,
      );
    }
  }
}

// Seed ONLY the /forward expectation (forward.js). The forward path is the sole
// workload, so the instance under measurement has just this one expectation. The
// upstream MockServer (or self, when K6_FORWARD_SELF=true) must itself answer
// /simple with a 200 for the forward to succeed.
export function seedForward() {
  const host = FORWARD.forwardSelf ? '127.0.0.1:1080' : FORWARD.upstreamHost;
  const res = http.put(
    `${CONFIG.controlPlane}/expectation`,
    JSON.stringify([forwardExpectation(host)]),
    jsonParams(),
  );
  if (res.status !== 201 && res.status !== 200) {
    fail(`failed to seed forward expectation: HTTP ${res.status} ${res.body}`);
  }
  return res;
}

// --- actions (tagged so k6 reports per-operation) --------------------------

// The `create` arm CHURNS THE EVENT LOG with control-plane traffic; it must NOT grow
// the expectation store, or over a long run it fills maxExpectations, evicts the
// seeded /simple match expectation and the match arm 404s. Two properties prevent it:
//   1. a STABLE `id`: PUT /expectation upserts by id, so the store holds ONE entry
//      however many creates run (and still answers 201, keeping `create: 201` green);
//   2. a DISTINCT path (/churn): even without the id it cannot shadow or evict /simple.
// times.remainingTimes:5 is inert (nothing GETs /churn); it mirrors the old Locust shape.
const CHURN_EXPECTATION = JSON.stringify([
  {
    id: 'perf-churn',
    httpRequest: { path: '/churn' },
    httpResponse: { statusCode: 200, body: 'some churn response' },
    times: { remainingTimes: 5 },
  },
]);

// NOTE: name kept as createSimpleExpectation for its three callers (soak.js,
// load.js, smoke.js). All three drive the `create` control-plane arm and match
// via the seeded /simple; none depend on this creating a /simple entry or a
// DISTINCT entry per call, so the stable-id /churn shape is a safe drop-in for
// every caller (and fixes the same latent eviction in load.js/smoke.js too).
export function createSimpleExpectation() {
  const res = http.put(
    `${CONFIG.controlPlane}/expectation`,
    CHURN_EXPECTATION,
    jsonParams({ op: 'create', name: 'PUT /mockserver/expectation' }),
  );
  check(res, { 'create: 201': (r) => r.status === 201 });
  return res;
}

export function getSimple(extraTags) {
  const res = http.get(`${CONFIG.baseUrl}/simple`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'match', name: 'GET /simple', ...(extraTags || {}) },
  });
  check(res, { 'match: 200': (r) => r.status === 200 });
  return res;
}

export function getForward(extraTags) {
  const res = http.get(`${CONFIG.baseUrl}/forward`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'forward', name: 'GET /forward', ...(extraTags || {}) },
  });
  check(res, { 'forward: 200': (r) => r.status === 200 });
  return res;
}

export function postLargeBody(extraTags) {
  const res = http.post(`${CONFIG.baseUrl}/large`, LARGE_BODY, jsonParams({ op: 'large', name: 'POST /large', ...(extraTags || {}) }));
  check(res, { 'large: 200': (r) => r.status === 200 });
  return res;
}

export function getRegex() {
  const res = http.get(`${CONFIG.baseUrl}/regex/abc123/resource`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'regex', name: 'GET /regex/:id/resource' },
  });
  check(res, { 'regex: 200': (r) => r.status === 200 });
  return res;
}

export function getTemplated(extraTags) {
  const res = http.get(`${CONFIG.baseUrl}/template`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'template', name: 'GET /template', ...(extraTags || {}) },
  });
  check(res, { 'template: 200': (r) => r.status === 200 });
  return res;
}

// Mustache template arm.
export function getTemplatedMustache(extraTags) {
  const res = http.get(`${CONFIG.baseUrl}/template_mustache`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'template_mustache', name: 'GET /template_mustache', ...(extraTags || {}) },
  });
  check(res, { 'template_mustache: 200': (r) => r.status === 200 });
  return res;
}

// JavaScript template arm (GraalJS).
export function getTemplatedJavaScript(extraTags) {
  const res = http.get(`${CONFIG.baseUrl}/template_javascript`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'template_javascript', name: 'GET /template_javascript', ...(extraTags || {}) },
  });
  check(res, { 'template_javascript: 200': (r) => r.status === 200 });
  return res;
}

// POST a large JSON body (server decodes the whole body; matches only
// the small marker). LARGE_BODIES[0] is 1 MB, [1] is 10 MB.
export function postLarge1mb(extraTags) {
  const res = http.post(`${CONFIG.baseUrl}/large_1mb`, LARGE_BODIES[0].body,
    jsonParams({ op: 'large_1mb', name: 'POST /large_1mb', ...(extraTags || {}) }));
  check(res, { 'large_1mb: 200': (r) => r.status === 200 });
  return res;
}

export function postLarge10mb(extraTags) {
  const res = http.post(`${CONFIG.baseUrl}/large_10mb`, LARGE_BODIES[1].body,
    jsonParams({ op: 'large_10mb', name: 'POST /large_10mb', ...(extraTags || {}) }));
  check(res, { 'large_10mb: 200': (r) => r.status === 200 });
  return res;
}

// GET a response served from a file on the SUT (FileBodyMaterialiser).
export function getLargeFile(extraTags) {
  const res = http.get(`${CONFIG.baseUrl}/large_file`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'large_file', name: 'GET /large_file', ...(extraTags || {}) },
  });
  check(res, { 'large_file: 200': (r) => r.status === 200 });
  return res;
}

// --- proxy.js: forward-proxy and TLS-handshake actions ------------------------
//
// The SUT under measurement in proxy.js's FORWARD mode is configured as a forward
// PROXY, so these arms do NOT seed it — they route a request at the UPSTREAM and
// let MockServer relay it. The run step seeds the upstream's /simple; k6 only
// probes the arms in setup() (verifyProxyForwardArms) and fails loud if the relay
// does not work, never recording a silent 100%-error arm.

// Absolute-URI forwarding. With HTTP_PROXY pointed at the SUT, an http://
// target is sent to the SUT as an absolute-URI GET; the SUT forwards it to the
// upstream and relays the response. Keep-Alive so connections are reused (a real
// proxy client), which keeps the measured latency about the RELAY, not handshakes.
export function getProxiedAbsolute(extraTags) {
  const res = http.get(`http://${PROXY.upstreamHost}/simple`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'forward_absolute', name: 'GET http://upstream/simple (absolute-URI)', ...(extraTags || {}) },
  });
  check(res, { 'forward_absolute: 200': (r) => r.status === 200 });
  return res;
}

// CONNECT tunnel carrying HTTPS. With HTTPS_PROXY pointed at the SUT, an
// https:// target makes k6 issue CONNECT <upstream> to the SUT, which establishes a
// tunnel; k6 then performs a TLS handshake over that tunnel and the request reaches
// the upstream (the response body confirms the relay landed there). NOTE: this
// measures the proxy CONNECT PATH, not necessarily an end-to-end-to-upstream TLS
// session — MockServer's forward proxy may terminate the CONNECT TLS itself with a
// dynamically generated certificate (a MITM), in which case http_req_tls_handshaking
// is timed against the SUT. Either way a NON-ZERO http_req_tls_handshaking proves a
// real TLS handshake was carried over the tunnel (the arm is genuinely a TLS path,
// not a plain request), which is what the setup() guard asserts.
export function getProxiedConnect(extraTags) {
  const res = http.get(`https://${PROXY.upstreamHost}/simple`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op: 'forward_connect', name: 'GET https://upstream/simple (CONNECT tunnel)', ...(extraTags || {}) },
  });
  check(res, { 'forward_connect: 200': (r) => r.status === 200 });
  return res;
}

// A direct-TLS GET against a handshake SUT's seeded /simple. With
// noConnectionReuse (proxy.js handshake mode) every call is a fresh TCP+TLS
// handshake, so http_req_tls_handshaking is paid on EVERY iteration (never
// amortised). The body is header-only/tiny so the handshake dominates.
export function getHandshakeSimple(baseUrl, op, extraTags) {
  const res = http.get(`${baseUrl}/simple`, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op, name: `GET ${op} /simple (fresh TLS)`, ...(extraTags || {}) },
  });
  check(res, { [`${op}: 200`]: (r) => r.status === 200 });
  return res;
}

// Fail-loud probe of the FORWARD-mode arms: the absolute-URI and CONNECT relays
// must each return 200 from the upstream. Called from proxy.js setup().
export function verifyProxyForwardArms() {
  const abs = http.get(`http://${PROXY.upstreamHost}/simple`, { headers: CONFIG.keepAliveHeaders });
  if (abs.status !== 200) {
    fail(`forward_absolute arm: GET http://${PROXY.upstreamHost}/simple via proxy returned HTTP ${abs.status} — the SUT is not forwarding absolute-URI requests to the upstream (check HTTP_PROXY + upstream seed). Body: ${abs.body}`);
  }
  const conn = http.get(`https://${PROXY.upstreamHost}/simple`, { headers: CONFIG.keepAliveHeaders });
  if (conn.status !== 200) {
    fail(`forward_connect arm: GET https://${PROXY.upstreamHost}/simple via CONNECT tunnel returned HTTP ${conn.status} — the SUT is not tunnelling CONNECT to the upstream (check HTTPS_PROXY + upstream TLS). Body: ${conn.body}`);
  }
  // The CONNECT arm MUST have carried a real TLS handshake over the tunnel (whether
  // terminated at the upstream or at the SUT's MITM cert); a zero handshake time
  // would mean no TLS occurred, i.e. this is not a real HTTPS CONNECT path. Fail the
  // run loudly rather than baseline an arm that never carried TLS.
  if (!(conn.timings && conn.timings.tls_handshaking > 0)) {
    fail(`forward_connect arm: CONNECT to ${PROXY.upstreamHost} completed with tls_handshaking=${conn.timings ? conn.timings.tls_handshaking : 'n/a'} ms — no TLS handshake was carried over the tunnel, so this is NOT a real HTTPS CONNECT path.`);
  }
}

// Fail-loud probe of the HANDSHAKE-mode arms: each ENABLED direct-TLS SUT must
// answer /simple with 200, over a genuine TLS handshake. Called from proxy.js
// setup(). An enabled arm that cannot handshake aborts the run rather than
// recording a silent 100%-error / zero-handshake arm.
export function verifyHandshakeArms() {
  const arms = [
    ['tls13', PROXY.tls13Url],
    ['mtls', PROXY.mtlsUrl],
    ['jdk', PROXY.jdkUrl],
  ];
  for (const [name, url] of arms) {
    if (!url) {
      continue;
    }
    const res = http.get(`${url}/simple`, { headers: CONFIG.keepAliveHeaders });
    if (res.status !== 200) {
      fail(`handshake_${name} arm: GET ${url}/simple returned HTTP ${res.status} — the SUT is not reachable over TLS / not seeded / (mtls) rejected the client certificate. Body: ${res.body}`);
    }
    if (!(res.timings && res.timings.tls_handshaking > 0)) {
      fail(`handshake_${name} arm: ${url}/simple answered but tls_handshaking=${res.timings ? res.timings.tls_handshaking : 'n/a'} ms — no TLS handshake was measured, so the handshake numbers would be meaningless.`);
    }
  }
}

// --- coverage.js: path-coverage arms --------------------------------------------
//
// The matcher arms register COVERAGE.matcherCandidates expectations per matcher
// type on one literal POST path. Candidate k matches only a body whose variant is
// k, and the request carries variant n-1, so the LAST-registered candidate is the
// first match and every request evaluates all n body matchers.

export const COVERAGE_MATCHER_TYPES = ['jsonpath', 'xpath', 'jsonschema'];

function coverageOrderItems(variant) {
  const items = [];
  for (let i = 0; i < 10; i++) {
    items.push({ variant: i === 5 ? variant : 100000 + i, sku: `sku-${i}`, qty: i + 1, price: 9.5 + i });
  }
  return items;
}

function coverageJsonBody(variant) {
  return JSON.stringify({
    customer: { id: 'c-42', tier: 'gold', region: 'eu-west' },
    variant,
    order: coverageOrderItems(variant),
  });
}

function coverageXmlBody(variant) {
  const items = coverageOrderItems(variant)
    .map((it) => `<item><variant>${it.variant}</variant><sku>${it.sku}</sku><qty>${it.qty}</qty><price>${it.price}</price></item>`)
    .join('');
  return `<order><customer id="c-42" tier="gold" region="eu-west"/>${items}</order>`;
}

function coverageBodyMatcher(type, k) {
  if (type === 'jsonpath') {
    return { type: 'JSON_PATH', jsonPath: `$.order[?(@.variant == ${k})]` };
  }
  if (type === 'xpath') {
    return { type: 'XPATH', xpath: `/order/item[variant='${k}']` };
  }
  return {
    type: 'JSON_SCHEMA',
    jsonSchema: { type: 'object', required: ['variant'], properties: { variant: { enum: [k] } } },
  };
}

function coverageMatcherRequest(type, variant) {
  return type === 'xpath'
    ? { body: coverageXmlBody(variant), contentType: 'application/xml' }
    : { body: coverageJsonBody(variant), contentType: 'application/json' };
}

const COVERAGE_HIT = Object.fromEntries(
  COVERAGE_MATCHER_TYPES.map((t) => [t, coverageMatcherRequest(t, COVERAGE.matcherCandidates - 1)]),
);
const COVERAGE_MISS = Object.fromEntries(COVERAGE_MATCHER_TYPES.map((t) => [t, coverageMatcherRequest(t, -1)]));

// Stable ids make a re-seed an in-place replace, so seeding from each mode's
// setup() never duplicates or reorders an expectation.
export function seedCoverage(withMatchers) {
  const list = [
    {
      id: 'cov-tls',
      httpRequest: { path: '/cov/tls' },
      httpResponse: { statusCode: 200, body: 'cov tls' },
      times: { unlimited: true },
    },
    {
      id: 'cov-mtls',
      httpRequest: { path: '/cov/mtls', clientCertificate: { subject: `.*${COVERAGE.clientCertSubject}.*` } },
      httpResponse: { statusCode: 200, body: 'cov mtls' },
      times: { unlimited: true },
    },
  ];
  if (withMatchers) {
    for (const type of COVERAGE_MATCHER_TYPES) {
      for (let k = 0; k < COVERAGE.matcherCandidates; k++) {
        list.push({
          id: `cov-${type}-${k}`,
          httpRequest: { method: 'POST', path: `/cov/${type}`, body: coverageBodyMatcher(type, k) },
          httpResponse: { statusCode: 200, body: `matched-${type}-${k}` },
          times: { unlimited: true },
        });
      }
    }
  }
  const res = http.put(`${COVERAGE.httpUrl}/mockserver/expectation`, JSON.stringify(list), jsonParams());
  if (res.status !== 201 && res.status !== 200) {
    fail(`coverage: failed to seed ${list.length} expectations: HTTP ${res.status} ${res.body}`);
  }
}

export function getCoverage(url, op, extraTags, params) {
  const res = http.get(url, {
    headers: CONFIG.keepAliveHeaders,
    tags: { op, name: `GET ${op}`, ...(extraTags || {}) },
    ...(params || {}),
  });
  check(res, { [`${op}: 200`]: (r) => r.status === 200 });
  return res;
}

export function postCoverageMatcher(type, op, extraTags) {
  const req = COVERAGE_HIT[type];
  const res = http.post(`${COVERAGE.httpUrl}/cov/${type}`, req.body, {
    headers: { 'Content-Type': req.contentType, ...CONFIG.keepAliveHeaders },
    tags: { op, name: `POST /cov/${type}`, ...(extraTags || {}) },
  });
  check(res, { [`${op}: 200`]: (r) => r.status === 200 });
  return res;
}

// Fail-loud probes, called from coverage.js setup(). Each proves the arm measures
// the path it is named for, not a fallback that happens to answer 200. The two
// halves of the certificate proof run in different invocations: a no-certificate
// invocation must be REFUSED /cov/mtls, a certificate invocation must be served it.
export function verifyCoverageTls(withCertificate) {
  const tls = http.get(`${COVERAGE.tlsUrl}/cov/tls`);
  if (tls.status !== 200 || !(tls.timings && tls.timings.tls_handshaking > 0)) {
    fail(`coverage tls: GET ${COVERAGE.tlsUrl}/cov/tls returned HTTP ${tls.status} with tls_handshaking=${tls.timings ? tls.timings.tls_handshaking : 'n/a'} ms - not a TLS request to the coverage SUT`);
  }
  // The TLS arms are documented as HTTP/2 (ALPN); prove it from the client side.
  if (tls.proto !== 'HTTP/2.0') {
    fail(`coverage tls: k6 negotiated ${tls.proto} with the coverage SUT, not HTTP/2.0`);
  }
  const mtls = http.get(`${COVERAGE.tlsUrl}/cov/mtls`);
  if (withCertificate && mtls.status !== 200) {
    fail(`coverage mtls: GET ${COVERAGE.tlsUrl}/cov/mtls returned HTTP ${mtls.status} - the client certificate was not presented, or its subject did not match "${COVERAGE.clientCertSubject}"`);
  }
  if (!withCertificate && mtls.status === 200) {
    fail('coverage mtls: /cov/mtls answered 200 WITHOUT a client certificate - the certificate gate is not enforced, so the mtls arms would not prove a certificate was presented');
  }
}

export function verifyCoverageMatchers() {
  const last = COVERAGE.matcherCandidates - 1;
  for (const type of COVERAGE_MATCHER_TYPES) {
    const hit = http.post(`${COVERAGE.httpUrl}/cov/${type}`, COVERAGE_HIT[type].body, {
      headers: { 'Content-Type': COVERAGE_HIT[type].contentType },
    });
    if (hit.status !== 200 || String(hit.body) !== `matched-${type}-${last}`) {
      fail(`coverage ${type}: expected the last candidate (matched-${type}-${last}) but got HTTP ${hit.status} "${hit.body}"`);
    }
    const miss = http.post(`${COVERAGE.httpUrl}/cov/${type}`, COVERAGE_MISS[type].body, {
      headers: { 'Content-Type': COVERAGE_MISS[type].contentType },
    });
    if (miss.status === 200) {
      fail(`coverage ${type}: a body matching NO candidate answered 200 ("${miss.body}") - the matcher is not discriminating`);
    }
  }
}

export function verifyCoverageProxy(url, params) {
  const res = http.get(url, params || {});
  if (res.status !== 200) {
    fail(`coverage proxy: GET ${url} through the SUT proxy returned HTTP ${res.status} - check HTTP_PROXY and the upstream seed`);
  }
  return res;
}
