# MockServer k6 performance harness

[k6](https://k6.io) load tests for MockServer. Supersedes the Locust harness
(the Locust files remain in `..` for one release, then are retired). k6 gives
native Prometheus remote-write output, built-in pass/fail **thresholds** (CI
regression gates), and Grafana-native dashboards.

## Scripts

| Script | Purpose | Gates |
|--------|---------|-------|
| `smoke.js` | Wiring sanity — exercises every action once (match, create, forward, large-body, regex). Fast. | Correctness only (status codes + zero transport errors). No latency gate. |
| `load.js` | Primary load test. Closed-loop arrival-rate: `match` (GET /simple) ramps to peak while `create` (PUT expectation) churns the control plane. | p95/p99 latency + error-rate + check-rate (CI pass/fail). |
| `forward.js` | Forward-path regression guard. Drives the OUTBOUND/FORWARD path (`GET /forward` → upstream `/simple`) at a sustained high rate (ramp to ~1500 rps). Guards the `mockserver.forwardConnectionPoolEnabled` default: without pooling, every forwarded request opens a fresh upstream socket and at peak rate exhausts ephemeral ports → BindException → failures. | **error-rate** (the pool-regression signal) + forward-path p95/p99 + check-rate. |
| `stress.js` | Ramp the match path past the load peak to find the breaking point. | Aborts only if error rate exceeds the ceiling (latency intentionally ungated). |
| `soak.js` | Sustained moderate load over a long duration to surface memory/GC/connection leaks. Pair with the Grafana stack to watch JVM heap. Emits each arm's latency per window (`soak.<arm>.windows`) so drift can be read across the run. | Match p95/p99 + error-rate + check-rate. Drift is reported, not gated. |
| `coverage.js` | Path-coverage arms for paths no other script drives, one mode per invocation (`K6_COV_MODE`): `churn` / `churn_mtls` (fresh TLS handshake per request, without / with a client certificate), `keepalive` / `keepalive_mtls` (reused connections, without / with a client certificate), `matchers` (JSONPath, XPath and JSON-schema body matchers each scanning `K6_COV_MATCHER_CANDIDATES` expectations), `capture` and `download` (through `HTTP_PROXY`: proxy load, and large proxied bodies from `K6_COV_DOWNLOAD_URL`). Writes `{mode, arms:{<op>:{p50_ms, p95_ms, p99_ms, delivery_ratio, error_rate, ...}}}` to `K6_COV_RESULT_PATH`. `setup()` fails loud if an arm would not measure its named path. Driven by `perf-test-run.sh`'s path-coverage phase (see `docs/code/performance-measurement.md`). | Notify-only. |
| `regression.js` | Daily regression harness. Four `constant-arrival-rate` scenarios (`match`, `forward`, `template`, `large`) each tagged `op:<name>`. A warmup scenario runs first and touches **every** measured path (incl. `/large`). To keep the measured percentiles describing the SERVER not the client rig, the four scenarios start **staggered** (`K6_REG_STAGGER`), the VU pool is fixed with `preAllocatedVUs == maxVUs` (`K6_REG_PRE_VUS`/`K6_REG_MAX_VUS`, no mid-run allocation storm), and a per-scenario **settle window** (`K6_REG_SETTLE`) at the start of the measured window is tagged `op:<op>_settle` and excluded from the percentiles (load still runs; only the start transient is dropped). Run twice per CI job — once `BASE_URL=http://...` and once `BASE_URL=https://... PROTO=https_h2` (HTTPS negotiates HTTP/2 via ALPN). Writes per-behaviour `{p50_ms, p95_ms, p99_ms, throughput_rps, offered_rps, dropped_iterations, delivery_ratio, error_rate, settle_excluded, measured_window_s}` keyed `<op>_<proto>` to `K6_RESULT_PATH`. `K6_REG_MATCH_DELAY_MS>0` is a self-test knob that seeds a fixed server delay on the match response to prove the percentiles still track a real slowdown. | Notify-only (no k6 thresholds gate the CI build). |
| `growth.js` | Resource-growth regression harness. Validates that latency does not climb as the request log fills (see issue #2329: O(n) eviction once the `maxLogEntries` ring is full). Runs a sustained `load` scenario on the match path at a rate that fills `maxLogEntries` early; `window:first` and `window:last` probes measure the latency slope. Writes first/last-window p95 + ratio. | Notify-only. |
| `sweep.js` | Throughput-vs-latency "knee" curve. Offers the match path (`GET /simple`) at an ascending LADDER of fixed arrival rates (`K6_SWEEP_RATES`); each rate is a staggered `constant-arrival-rate` step tagged `rate:<offered>`. Per step it records the ACHIEVED throughput, latency percentiles (p50/p90/p95/p99/p99.9), and error rate; the percentiles exclude the first `K6_SWEEP_SETTLE` of each step (the rung-onset transient), while throughput, errors and drops count the whole step. Writes `{proto, latency_window, points:[{offered_rps, achieved_rps, p50_ms…p999_ms, sample_count, measured_sample_count, settle_excluded, full_rung_ms, error_rate}]}` to `K6_SWEEP_RESULT_PATH` — the series plotted as a load-vs-latency knee curve on the docs site. Also REUSED per pinned core-count by the serving per-core harness (`.buildkite/scripts/steps/lib/perf-percore.sh`, performance-programme item 18); `measured_sample_count` (post-settle requests) is what it applies MIN_TAIL_SAMPLES tail suppression to. | Notify-only (NO aborting thresholds — dropped iterations / degradation at the top of the ladder are the point). |
| `proxy.js` | Proxy-path + TLS-handshake harness (performance-programme items 9a + 14). **TWO modes** (`K6_PROXY_MODE`), run as separate invocations because they need mutually exclusive process-global settings. **`forward`**: drives MockServer AS A FORWARD PROXY — the k6 container gets `HTTP_PROXY`/`HTTPS_PROXY` pointed at the SUT, so an `http://upstream` target becomes an **absolute-URI forward** and an `https://upstream` target a **CONNECT tunnel carrying TLS** through the SUT (which MockServer may itself terminate with a generated cert). Emits `.behaviours` keyed `forward_absolute_proxy` / `forward_connect_proxy` (same shape as `regression.js`, so `perf-test-compare.sh` picks them up via `behaviours.*` with no change — which resets the k6 arm-set baseline once). **`handshake`**: MockServer's INBOUND TLS handshake cost, which the `https_h2` run amortises to ~0 by reusing connections. Hits three distinct SUTs with `noConnectionReuse` (a fresh TCP+TLS handshake per iteration): **TLS 1.3 server-only**, **mTLS-required** (presents a client cert via `tlsAuth`), and **native-provider-absent** (`-Dio.netty.handler.ssl.noOpenSsl=true`, the Dockerfile's documented JDK-provider fallback). Emits `.tls_handshake` per arm `{handshake_p50/p95/p99_ms, handshakes_per_s, cpu_ms_per_handshake, alloc_kb_per_handshake, error_rate}` — the CPU/alloc-per-handshake fields are filled in by `perf-test-run.sh` from per-SUT sampling. Clones `regression.js`'s FIXED measured-window shape (stagger, `preAllocatedVUs == maxVUs`, warm-every-path, settle exclusion, `MIN_TAIL_SAMPLES`). `setup()` fails loud if the CONNECT tunnel carried no TLS handshake at all or an enabled handshake arm cannot handshake. | Notify-only. |

## Running

```bash
# against a local MockServer on :1080 (default)
k6 run mockserver-performance-test/k6/smoke.js
k6 run mockserver-performance-test/k6/load.js

# point at another instance / protocol
k6 run -e BASE_URL=https://localhost:1080 mockserver-performance-test/k6/load.js
k6 run -e MOCKSERVER_HOST=host.docker.internal:1080 .../load.js

# shape the load / relax gates on a slow agent
k6 run -e K6_PEAK_RATE=1000 -e K6_P95_MS=40 .../load.js

# regression script — HTTP then HTTPS+H2 (requires mockserver-upstream for forward behaviour)
k6 run -e BASE_URL=http://localhost:1080 -e K6_RESULT_PATH=/tmp/result-http.json \
  mockserver-performance-test/k6/regression.js
k6 run -e BASE_URL=https://localhost:1080 -e PROTO=https_h2 -e K6_RESULT_PATH=/tmp/result-h2.json \
  mockserver-performance-test/k6/regression.js

# regression without a dedicated upstream (single-container smoke only)
k6 run -e K6_FORWARD_SELF=true -e BASE_URL=http://localhost:1080 \
  mockserver-performance-test/k6/regression.js

# growth script
k6 run -e BASE_URL=http://localhost:1080 mockserver-performance-test/k6/growth.js

# sweep script — full default ladder (500…32000 rps); writes the knee-curve JSON
k6 run -e K6_SWEEP_RESULT_PATH=/tmp/sweep-result.json \
  mockserver-performance-test/k6/sweep.js

# sweep with a short, low ladder (laptop-safe smoke)
k6 run -e K6_SWEEP_RATES=200,500,1000 -e K6_SWEEP_STEP=8s \
  -e K6_SWEEP_RESULT_PATH=/tmp/sweep-result.json \
  mockserver-performance-test/k6/sweep.js
```

### Throughput-vs-latency sweep (`sweep.js`)

`sweep.js` offers the match path at an ascending ladder of FIXED arrival rates
and records, per rate step, the achieved throughput, latency percentiles, and
error rate — the data series plotted as the load-vs-latency "knee" curve. Each
ladder rung is its own `constant-arrival-rate` scenario, staggered after the
previous (`startTime` = sum of prior `step` + `gap` durations) with a short quiet
gap so one step's tail does not bleed into the next step's percentiles. Every
request is tagged `rate:<offered>` so the per-step submetrics are computed in the
summary.

There are deliberately **no aborting thresholds** — at the top of the ladder k6
may drop iterations (VU-starved) and latency/errors degrade sharply; observing
that degradation IS the point. The output JSON shape is a hard contract:

```json
{
  "proto": "http",
  "points": [
    {"offered_rps": 500, "achieved_rps": 499.6, "p50_ms": 0.9, "p90_ms": 1.4,
     "p95_ms": 1.8, "p99_ms": 3.2, "p999_ms": 7.1, "error_rate": 0.0}
  ]
}
```

A committed sample (a real run against MockServer 8.0.0 on Docker Desktop, ladder <!-- version-ref:keep -->
`500…16000` rps) lives at `k6/fixtures/sample-perf-sweep.json` — used to build
and test the docs-site knee-curve chart without re-running a load test.

### Forward-path regression guard (`forward.js`)

Guards the upstream connection-pool default (`mockserver.forwardConnectionPoolEnabled`,
default **true**). It hammers the forward path at a high sustained rate; with
pooling on the error rate stays ~0, and if pooling regresses to per-request
connections the host exhausts ephemeral ports (`BindException`) and the
error-rate threshold trips.

**Topology** — the SUT forwards to a *separate* loopback upstream MockServer so
the SUT does real outbound connections (the thing being pooled):

```mermaid
flowchart LR
  k6["k6 forward.js"] -->|"GET /forward"| sut["SUT MockServer :1080\nforwardConnectionPoolEnabled default"]
  sut -->|"forward to /simple"| upstream["upstream MockServer :1090\nanswers /simple -> 200"]
```

```bash
# 1. upstream MockServer on :1090 answering /simple -> 200
java -jar mockserver/mockserver-netty/target/mockserver-netty-*-jar-with-dependencies.jar \
     -serverPort 1090 >/tmp/upstream.log 2>&1 &
curl -s -XPUT 'http://localhost:1090/mockserver/expectation' -d \
  '[{"httpRequest":{"path":"/simple"},"httpResponse":{"statusCode":200,"body":"some simple response"},"times":{"unlimited":true}}]'

# 2. SUT MockServer on :1080 (pool default = ON — the guarded path)
java -jar mockserver/mockserver-netty/target/mockserver-netty-*-jar-with-dependencies.jar \
     -serverPort 1080 >/tmp/sut.log 2>&1 &

# 3. run the guard — SUT forwards to the upstream on :1090
k6 run -e FORWARD_UPSTREAM_HOST=127.0.0.1:1090 \
       mockserver-performance-test/k6/forward.js

# demonstrate the guard catches a regression: force pooling OFF on the SUT,
# re-run — the error-rate gate trips at peak (BindException-driven failures)
java -Dmockserver.forwardConnectionPoolEnabled=false -jar \
     mockserver/mockserver-netty/target/mockserver-netty-*-jar-with-dependencies.jar \
     -serverPort 1080 ...

# single-container quick smoke (NOT the real guard): SUT forwards to itself
k6 run -e K6_FORWARD_SELF=true mockserver-performance-test/k6/forward.js
```

**What each threshold guards** (`forward.js`):

- `http_req_failed{op:forward}` / `http_req_failed` — **the regression guard.**
  Pooled ≈ 0; a per-request-connection regression spikes this at peak (ephemeral
  port exhaustion → BindException). Reuses `K6_MAX_ERROR_RATE` (default `0.01`).
- `http_req_duration{op:forward}` p95/p99 — forward-path latency bounds (looser
  than the match path because of the upstream hop). Override with
  `K6_FWD_P95_MS` / `K6_FWD_P99_MS`.
- `checks` — every forward returns a 200 from the upstream.

### Proxy-path + TLS handshake (`proxy.js`)

Two modes, run as **separate** invocations (they need mutually exclusive
process-global settings — a proxy env vs `noConnectionReuse`):

```bash
# item 9a — MockServer as a forward proxy. The proxy env is set on the CLIENT,
# and the request targets the UPSTREAM (routed through the SUT):
#   http://upstream  -> absolute-URI forward ;  https://upstream -> CONNECT tunnel
HTTP_PROXY=http://mockserver:1080 HTTPS_PROXY=http://mockserver:1080 \
  k6 run -e K6_PROXY_MODE=forward \
         -e FORWARD_UPSTREAM_HOST=mockserver-upstream:1080 \
         mockserver-performance-test/k6/proxy.js

# item 14 — inbound TLS/mTLS handshake cost (fresh handshake per iteration). Each
# arm targets its own SUT; the mTLS arm presents a client cert via tlsAuth.
k6 run -e K6_PROXY_MODE=handshake \
       -e K6_HS_TLS13_URL=https://mockserver:1080 \
       -e K6_HS_MTLS_URL=https://mockserver-mtls:1080 \
       -e K6_HS_JDK_URL=https://mockserver-jdk:1080 \
       -e K6_HS_CLIENT_CERT=/certs/client.pem -e K6_HS_CLIENT_KEY=/certs/client.key \
       mockserver-performance-test/k6/proxy.js
```

`perf-test-run.sh` drives both against the run's existing upstream/SUT, starts the
mTLS SUT (`tlsMutualAuthenticationRequired=true`, trusting a generated CA) and the
native-provider-absent SUT (`-Dio.netty.handler.ssl.noOpenSsl=true`), and augments
each handshake arm with per-handshake server **CPU** (`docker stats`, integrated)
and **allocation** (`jvm_memory_allocated_bytes` delta ÷ `requests_received_count`
delta). `setup()` fails the run loudly if the CONNECT tunnel carried no TLS
handshake at all (i.e. not a real HTTPS CONNECT path) or an enabled handshake arm
cannot handshake (mTLS cert rejected, etc.).

## Environment variables

All tunables are env-driven (see `lib/config.js`). Connection target resolves as
`BASE_URL` → else `MOCKSERVER_PROTOCOL` + `MOCKSERVER_HOST` (bare host gets `:1080`).

| Variable | Default | Meaning |
|----------|---------|---------|
| `BASE_URL` | – | Full base URL, e.g. `https://mockserver:1080` (wins over the pair below) |
| `MOCKSERVER_PROTOCOL` | `http` | `http` or `https` |
| `MOCKSERVER_HOST` | `localhost:1080` | host[:port] (mirrors the Locust variable) |
| `INSECURE_SKIP_TLS_VERIFY` | local→`true`, public→`false` | Skip TLS verification. Defaults insecure only for loopback/private hosts (MockServer uses a self-signed CA); set explicitly to `true` for a public HTTPS target. |
| `K6_START_RATE` / `K6_PEAK_RATE` | `50` / `500` | load.js ramping-arrival-rate (req/s) |
| `K6_RAMP_UP` / `K6_HOLD` / `K6_RAMP_DOWN` | `30s` / `1m` / `15s` | load.js stage durations (compound forms like `1m30s` supported) |
| `K6_CREATE_RATE` | `10` | control-plane create-expectation rate (req/s) |
| `K6_STRESS_PEAK_RATE` | `5000` | stress.js peak target (req/s) |
| `K6_SOAK_RATE` / `K6_SOAK_DURATION` | `200` / `30m` | soak.js sustained rate + duration |
| `K6_SOAK_WINDOW` / `K6_SOAK_WARMUP` | `5m` / `15m` | soak.js per-window latency series: window width (1 to 96 windows per run), and the lead-in whose windows are never a drift reference. These two and `K6_SOAK_DURATION` need a unit (`2h`, `90s`, `1h30m`); soak.js refuses anything else when it starts |
| `K6_SOAK_DRIFT_WINDOWS` | `0` | soak.js refuses to start unless at least this many windows start at or after the warm-up (the weekly soak step passes `6`); `0` skips the check |
| `K6_P95_MS` / `K6_P99_MS` | `25` / `100` | latency thresholds (ms) |
| `K6_MAX_ERROR_RATE` / `K6_MIN_CHECK_RATE` | `0.01` / `0.99` | error/check-rate thresholds |
| `K6_PRE_VUS` / `K6_MAX_VUS` | `50` / `600` | VU pool for the arrival-rate executors |

**forward.js additional variables** (defined in `lib/config.js` `FORWARD_LOAD` block):

| Variable | Default | Meaning |
|----------|---------|---------|
| `K6_FWD_START_RATE` / `K6_FWD_PEAK_RATE` | `100` / `1500` | forward.js ramping-arrival-rate (req/s); peak is the rate that broke the old per-request default |
| `K6_FWD_RAMP_UP` / `K6_FWD_HOLD` / `K6_FWD_RAMP_DOWN` | `30s` / `1m` / `15s` | forward.js stage durations |
| `K6_FWD_PRE_VUS` / `K6_FWD_MAX_VUS` | `200` / `2000` | VU pool for the forward arrival-rate executor |
| `K6_FWD_P95_MS` / `K6_FWD_P99_MS` | `50` / `200` | forward-path latency thresholds (ms); looser than the match path due to the upstream hop |
| `FORWARD_UPSTREAM_HOST` | `mockserver-upstream:1080` | host:port of the upstream MockServer the SUT forwards to (set to `127.0.0.1:1090` for the local two-instance topology) |
| `K6_FORWARD_SELF` | – | `true` loops `/forward` back to the SUT's own `/simple` (single-container smoke only) |

**regression.js / growth.js additional variables** (defined in `lib/config.js` `REGRESSION` and `GROWTH` blocks):

| Variable | Default | Meaning |
|----------|---------|---------|
| `K6_REG_RATE` | `200` | Constant arrival rate per behaviour scenario (req/s) |
| `K6_REG_DURATION` | `2m` | Duration of each behaviour scenario |
| `K6_REG_WARMUP` | `30s` | Warmup scenario duration (runs before measurements). Warms every path, including `/large` -- omitting it left the heaviest path JIT-cold and produced a start-of-scenario backlog |
| `K6_REG_PRE_VUS` | `50` | Pre-allocated VUs. **Kept equal to `K6_REG_MAX_VUS` deliberately**, so k6 can never do a mid-run allocation ramp -- the four scenarios' simultaneous ramps were the connection storm behind Finding 3 |
| `K6_REG_MAX_VUS` | `50` | Maximum VUs. See above: equal to `K6_REG_PRE_VUS` by design. At 200 rps and sub-ms latency the steady-state need is ~1 VU, so this bounds the peak transient burst, not ongoing concurrency |
| `K6_REG_STAGGER` | `5s` | Gap between successive behaviour scenario start times, so their start transients do not superimpose |
| `K6_REG_SETTLE` | `10s` | Leading slice of each scenario excluded from the measured percentiles. Load still runs; the exclusion is reported as `settle_excluded` and `measured_window_s` so it is auditable |
| `K6_REG_MATCH_DELAY_MS` | `0` | **Self-test knob.** Injects a uniform server-side delay on the `match` expectation, to prove the measured percentiles still move when the server genuinely slows. Leave at `0`; it is per-invocation and never persisted |
| `PROTO` | `http` | Protocol tag appended to result keys (e.g. `https_h2`) |
| `K6_HTTP2` | – | When set, enables HTTP/2 in the k6 HTTP client |
| `K6_RESULT_PATH` | – | File path where `handleSummary()` writes the result JSON |
| `K6_GROWTH_RATE` | `800` | Sustained load rate for the growth fill scenario (req/s) |
| `K6_GROWTH_DURATION` | `6m` | Total growth run duration |
| `K6_GROWTH_PROBE` | `30s` | Duration of the `window:first` and `window:last` probe scenarios |
| `FORWARD_UPSTREAM_HOST` | `mockserver-upstream:1080` | Host:port of the dedicated upstream MockServer for `forward` expectations |
| `K6_FORWARD_SELF` | – | When set to `true`, the `forward` expectation loops back to the same instance (`127.0.0.1:1080`) instead of requiring a separate upstream. Use for local single-container smoke only. |

**sweep.js additional variables** (defined in `lib/config.js` `SWEEP` block):

| Variable | Default | Meaning |
|----------|---------|---------|
| `K6_SWEEP_RATES` | `500,1000,2000,4000,8000,16000,32000` | Comma-separated ascending ladder of offered arrival rates (req/s); one staggered `constant-arrival-rate` step per rung |
| `K6_SWEEP_STEP` | `20s` | Duration each rate step holds |
| `K6_SWEEP_GAP` | `5s` | Quiet gap between steps (no requests) so percentiles do not bleed across steps |
| `K6_SWEEP_SETTLE` | `3s` | Leading slice of each step excluded from its latency percentiles (the rung-onset transient); must be shorter than `K6_SWEEP_STEP`. Load still runs and still counts toward `achieved_rps`, `error_rate` and `dropped_iterations`. Reported as `latency_window.settle_s`, per-rung `settle_excluded`, and the whole-step percentiles as `full_rung_ms`. CI sets it from `PERF_SWEEP_SETTLE_S` (main and INFO ladders), `PERF_PERCORE_SETTLE_S` (per-core rig) and `PERF_MULTI_SETTLE_S` (multi-process rig); their unmeasured warm-up drives pass `0s` |
| `K6_SWEEP_VUS_PER_KRPS` / `K6_SWEEP_VU_FLOOR` / `K6_SWEEP_VU_CEILING` | `80` / `96` / `2048` | Per-rung fixed-pool sizing: each rung's pool `= clamp(ceil(rate × K6_SWEEP_VUS_PER_KRPS/1000), floor, ceiling)`, with `preAllocatedVUs == maxVUs` within the rung (Finding-3 no-mid-run-allocation invariant). Scaling by rate stops the low rungs storming while giving the high rungs enough VUs to show the *server* ceiling, not a client one. `80` (=0.08 VUs/rps) is taken from the worst measured peak/rate ratio (34/500 = 0.068) plus margin, so it clears **every** measured ramped peak (build #347: 128 @2k→160, 216 @4k→320, 301 @8k→640); floor `96` covers the low rungs (incl. the interpolated 1k peak); ceiling `2048` caps k6 pre-init on the saturated high rungs while still exceeding the 32k peak (1,283). Full per-rung margin table in `lib/config.js`. On a host with two or more sockets and NUMA nodes the multi-k6 arm (`scripts/rw-multi-k6-sweep.sh`) passes a higher ceiling, its top rung's own pool, because there k6 has CPU to spare where 2,048 binds (`PERF_RW_K6_VU_CEILING` overrides it). |
| `K6_SWEEP_PRE_VUS` / `K6_SWEEP_MAX_VUS` | unset | Optional FLAT override — forces one pool on every rung (both must be set and equal, else sweep.js throws). Left unset by default so per-rung sizing applies |
| `PROTO` | `http` | Protocol tag recorded in the result (`https_h2` for an HTTPS+H2 run) |
| `K6_SWEEP_RESULT_PATH` | `sweep-result.json` | File path where `handleSummary()` writes the knee-curve result JSON |

**proxy.js additional variables** (defined in `lib/config.js` `PROXY` block):

| Variable | Default | Meaning |
|----------|---------|---------|
| `K6_PROXY_MODE` | `forward` | `forward` (item 9a proxy latency) or `handshake` (item 14 TLS handshake cost) |
| `K6_PROXY_RATE` | `200` | Offered req/s per forward-proxy arm |
| `K6_PROXY_DURATION` / `K6_PROXY_WARMUP` | `2m` / `30s` | Measured window / warmup (both modes) |
| `K6_PROXY_STAGGER` / `K6_PROXY_SETTLE` | `5s` / `10s` | Scenario stagger / per-scenario settle exclusion (Finding 3 shape) |
| `K6_PROXY_PRE_VUS` / `K6_PROXY_MAX_VUS` | `50` / `50` | VU pool — **kept equal** (no mid-run allocation ramp) |
| `FORWARD_UPSTREAM_HOST` | `mockserver-upstream:1080` | Upstream the proxy forwards/tunnels to (shared with forward.js) |
| `HTTP_PROXY` / `HTTPS_PROXY` | – | **Set on the k6 process/container** (not in the script) to point forward-mode requests through the SUT |
| `K6_HS_RATE` / `K6_HS_DURATION` | `50` / `1m` | Fresh-handshake rate + window per handshake arm |
| `K6_HS_TLS13_URL` / `K6_HS_MTLS_URL` / `K6_HS_JDK_URL` | – | Per-arm direct-TLS targets (empty ⇒ arm absent) |
| `K6_HS_CLIENT_CERT` / `K6_HS_CLIENT_KEY` | – | PEM paths the mTLS arm presents via `tlsAuth` |
| `PROTO` | `proxy` | Transport tag folded into result keys |
| `K6_PROXY_RESULT_PATH` | `proxy-result.json` | File path where `handleSummary()` writes the result JSON |

## Seeded expectations

`setup()` seeds the same 4 expectations as the legacy harness (the request
matches the **last** one, so the matcher does a near-full scan — the realistic
worst case), plus a large-body JSON expectation and a regex-path expectation for
the body-decode and regex scenarios.

> **Note — the `/forward` expectation self-loops.** It uses
> `httpOverrideForwardedRequest` to proxy `/forward` → `/simple` on
> `127.0.0.1:1080`, i.e. MockServer forwards to **itself**. This intentionally
> exercises the proxy/override path on a single instance, but requires the
> instance to be reachable at `127.0.0.1:1080` from inside its own container.
> The `forward` action is only used by `smoke.js`.

`regression.js` seeds its own expectations via `lib/expectations.js` `seedRegression()`:

- **Static match** (`match`) — simple GET response, matched by path
- **Forward** (`forward`) — forward action targeting `FORWARD_UPSTREAM_HOST` (a separate upstream MockServer instance). Requires `mockserver-upstream` to be running unless `K6_FORWARD_SELF=true`.
- **Velocity template** (`template`) — `TEMPLATE_EXPECTATION` using a Velocity response template, seeded via `lib/expectations.js`
- **Large body** (`large`) — ~4 KB JSON response body

> **Object/class callbacks are deferred to a future v2.** The dynamic-response path (object callback) needs a WebSocket responder or classpath class. The Velocity template expectation covers the dynamic-response path today.

## Prometheus / Grafana output

```bash
k6 run -o experimental-prometheus-rw \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
  mockserver-performance-test/k6/load.js
```

A docker-compose stack that wires k6 → Prometheus → Grafana and scrapes
MockServer's own `/mockserver/metrics` is added in a later increment.
