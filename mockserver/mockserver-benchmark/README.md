# mockserver-benchmark

JMH (Java Microbenchmark Harness) micro-benchmarks for MockServer's
request-matching **hot path**.

This module is **not part of the default build**. It is deliberately left out of
the parent `pom.xml` `<modules>` list so `mvn install` and CI never compile it —
JMH's annotation processor and the benchmark classpath must not touch production
artifacts. Build and run it on demand with `./run.sh`.

## Why it exists

The Part-A hot-path optimizations (see `docs/plans/…performance…`) are mostly
**allocation** reductions — e.g. a `MatchDifference` (backed by a
`ConcurrentHashMap`) allocated *per matcher, per request*, and a sorted matcher
list rebuilt on every request. k6 (the end-to-end load harness) proves the
*user-visible* throughput/latency win but is too noisy to prove an allocation
reduction. JMH with the GC profiler (`-prof gc`) measures
**bytes-allocated-per-op** for a single method in isolation, handling JIT
warmup, dead-code elimination and constant folding.

**Gate:** no A1/A2 allocation "win" is committed without before/after
`gc.alloc.rate.norm` numbers from this module.

## Running

```bash
# one-time: install the module under test
(cd .. && mvn -o -pl mockserver-core install -DskipTests)

# the important run — allocation profile across scan lengths and matcher shapes
./run.sh -prof gc

# a focused, faster run
./run.sh -prof gc -f 1 -wi 3 -i 5 -p expectationCount=100 -p matcherType=EXACT

# list benchmarks / pass any other JMH option
./run.sh -l
```

## What is measured

`MatchingBenchmark.firstMatchingExpectation_noMatch` calls
`RequestMatchers.firstMatchingExpectation(...)` with a request that matches
**none** of the registered expectations — the worst case that forces a full
scan of all N matchers (every matcher allocates a `MatchDifference` and is
evaluated).

Parameters:

| Param | Values | Meaning |
|-------|--------|---------|
| `expectationCount` | 1, 10, 100, 1000 | number of registered expectations (scan length) |
| `matcherType` | EXACT, REGEX, JSON_BODY, HEADERS_MISS, XML_BODY, JSON_DEEP_DEFER, JSON_DEEP_REJECT, JSON_PATH, XPATH, JSON_SCHEMA | shape of the registered matchers (see the `matcherType` javadoc); every CI consumer pins this param, so adding a value never widens their runs |
| `detailedMatchFailures` | false, true | `false` = the opt-out matcher hot path; `true` (the shipped default) = the `MatchDifference` → `StringFormatter` formatting path (the #1/#2 production allocation sites, made lazy by `a8898b263`) |

Metrics are off and INFO logging is off. The `detailedMatchFailures=false` arm is
the common case Part A optimizes; the `true` arm is measured and given its own
absolute allocation floor by the per-merge `perf-alloc-gate.sh` so a regression
re-introducing eager formatting fails the gate. The daily micro-bench primary run
pins `true` (the shipped default); the scaling sweep below pins `false`; the
allocation gate pins both, and also gates a `HEADERS_MISS` arm at INFO and
WARN, because `EXACT` never reaches the per-candidate matching scan. The headline number for the allocation work is
**`gc.alloc.rate.norm`** (B/op); `ns/op` (shown as µs/op) is the secondary signal.

`MatchingHitBenchmark.firstMatchingExpectation_match` is the matched-path
companion: N expectations sharing one `(method, path)` candidate-index bucket,
and a request that matches the FIRST or the LAST of them
(`expectationCount={64,100,1000}` × `hitPosition` × `logLevel={INFO,WARN}` ×
`detailedMatchFailures`). Use it to check that work added to the scan does not
tax requests that match. It is a separate class so the daily micro-bench and
`perf-alloc-gate.sh`, which include every method of `MatchingBenchmark`, keep
their row counts; it is wired into no gate.

```bash
./run.sh -prof gc MatchingHitBenchmark
```

## Scaling sweep (`run-scaling.sh`)

`run-scaling.sh` is a sibling of `run.sh` that runs a **fixed** param sweep and
emits a machine-readable `perf-scaling.json` (the documentation-site chart
contract). It is not free-form like `run.sh`: it runs two benchmark sets and
reshapes their JMH JSON with `jq`.

```bash
# full sweep (bounded default iterations), writes <repo-root>/perf-scaling.json
./run-scaling.sh

# fast validation run (seconds-per-combo)
JMH_ARGS_SCALING='-f 1 -wi 1 -i 2 -r 1 -w 1' ./run-scaling.sh

# custom output path
SCALING_RESULT_PATH=/tmp/perf-scaling.json ./run-scaling.sh
```

What it measures and the output shape:

| Set | Benchmark | Sweep | Shows |
|-----|-----------|-------|-------|
| `matching` | `MatchingBenchmark` | `expectationCount={1,10,100,1000}` × `matcherType={EXACT,REGEX}`, `logLevel=WARN`, `detailedMatchFailures=false`, `-prof gc` | matching time + allocation **grow** with scan length |
| `candidate_index` | `CandidateIndexBenchmark` | `n={1,10,100,1000,5000}` × `indexMode={SCAN,INDEX}`, `outcome=MISS`, `shape=LITERAL` | SCAN grows; **INDEX stays ~flat** |

```json
{
  "scaling": {
    "matching": [
      {"expectations": 1, "matcherType": "EXACT", "time_per_op": 0.42, "time_unit": "us/op", "alloc_bytes_per_op": 120.0}
    ],
    "candidate_index": [
      {"mode": "INDEX", "expectations": 5000, "time_per_op": 0.9, "time_unit": "us/op"}
    ]
  }
}
```

`time_per_op` = `primaryMetric.score`, `time_unit` = `primaryMetric.scoreUnit`,
`alloc_bytes_per_op` = `secondaryMetrics["gc.alloc.rate.norm"].score` (null if
absent; only on the `matching` set). `expectations` is the integer param
(`expectationCount` for `matching`, `n` for `candidate_index`). A validated
sample lives at `fixtures/sample-perf-scaling.json`.

## Workflow for a Part-A change

1. `./run.sh -prof gc | tee before.txt`
2. Make the A1/A2 change in `mockserver-core`, `mvn -o -pl mockserver-core install -DskipTests`.
3. `./run.sh -prof gc | tee after.txt`
4. Compare `gc.alloc.rate.norm` (and `ns/op`). Quote the delta in the commit.

## HTTP/2 multiplex benchmark (`run-h2-multiplex.sh`) — issue #2669

Unlike the JMH benchmarks above (which measure a single hot-path *method* in
isolation), `Http2StreamChannelBenchmark` is an **end-to-end** harness: it boots a
real `MockServer` and drives it over an h2c connection to answer the open question
from issue #2669 — *what does giving every HTTP/2 stream its own child channel cost
under concurrency?* Since #2669 the server's only HTTP/2 pipeline is
`Http2FrameCodec + Http2MultiplexHandler`, so every stream is a child channel with
its own pipeline (strictly more per-stream allocation than a shared connection
pipeline).

Because it boots a full server, this harness depends on `mockserver-netty` (not just
`mockserver-core`); install that first.

```bash
# one-time: install the server under test
(cd .. && ./mvnw -pl mockserver-netty -am install -DskipTests)

# full sweep (N = 1, 10, 100 concurrent streams over ONE connection) -> perf-h2-multiplex.json
./run-h2-multiplex.sh

# tiny local smoke (proves the client completes real round trips)
H2_BENCH_STREAMS=1 H2_BENCH_REQUESTS_PER_STREAM=5 H2_BENCH_WARMUP_REQUESTS=0 ./run-h2-multiplex.sh

# prove every validation gate fires (server-free)
./run-h2-multiplex.sh selftest
```

**Concurrency model.** Each HTTP/2 request gets its *own* stream — that is the unit
whose per-stream cost #2669 changed. Concurrency `N` is realised as `N` driver
threads ("lanes"); each lane issues `H2_BENCH_REQUESTS_PER_STREAM` requests
sequentially, opening a fresh `Http2StreamChannel` per request, so at any instant
there are exactly `N` in-flight streams. Total requests per point = `N ×
requestsPerStream`. The server caps `MAX_CONCURRENT_STREAMS` at 100, so **100 is the
concurrency ceiling on a single connection** — higher concurrency is a
multi-connection question and is intentionally not attempted here.

**Self-validation (mandatory, fails loudly).** A run publishes numbers only if it
passes five harness-integrity gates, otherwise it prints `HARNESS VALIDATION FAILED`
and exits code **2**: (1) the latch counts *total* expected responses (`streams ×
requestsPerStream`) and asserts `completed == expected`; (2) every response is HTTP
200 with the exact body; (3) a ~1 µs latency floor (a loopback round trip cannot be
faster); (4) a throughput ceiling that rejects physically-impossible numbers; (5)
any reset/timeout/non-200 fails the run. These are *integrity* checks, deliberately
distinct from any performance threshold — a gate failure means the measurement is
untrustworthy; a slow-but-valid run exits 0 and simply records slower numbers. Run
`selftest` to see every gate fire against fabricated results.

**Output shape.** `{ "h2_multiplex": { "streams_<N>": {throughput_rps, p50_us,
p95_us, p99_us, min_us, max_us, requests} } }`. `fixtures/sample-perf-h2-multiplex.json`
documents this shape — its **numbers are illustrative placeholders, not a
measurement** (real figures land in the S3 run history from the first CI run). In CI
the notify-only `perf-test-h2multiplex.sh` step (perf queue) records it into the
run-history baseline; there is **no pass/fail threshold yet** because run-to-run
variance on real agents is unknown — setting one now would be guessing.

### Bytes per HTTP/2 request (`Http2StreamAllocationBenchmark`)

The allocation companion: a JMH class that opens a fresh stream per op on one h2c
connection and `POST`s a 64-byte (`SMALL`) or 256 KiB (`OVER_WINDOW`, past the
65,535-byte flow-control window) body. `-prof gc` counts every thread, so
`gc.alloc.rate.norm` is client + server bytes per request — an A/B figure for a
server change, not the server's absolute cost. On demand only; no CI step runs it.

```bash
./run.sh -prof gc -f 2 Http2StreamAllocationBenchmark
```

## HTTP/2 per-connection memory benchmark (`run-h2-connection-memory.sh`) — issue #2669, item 11

The throughput harness above sweeps streams over **one** connection. This one adds the missing
**connections axis** — *N connections × M concurrent in-flight streams* (shapes 1×1, 10×10, 100×10) —
and records the **heap delta per established connection**, exactly what the 8.0.0 changelog warned the <!-- version-ref:keep -->
multiplex migration changed. `Http2ConnectionMemoryBenchmark` boots a real `MockServer` in-process and,
per shape:

1. forces GC (`MemoryMXBean.gc()`) and reads retained heap as the baseline `H0` (no connections open);
2. opens `C` real TCP connections, each holding `S` concurrently in-flight streams (the matched response
   is held open by a long server-side delay, so `C×S` stream child channels stay alive);
3. **clears the event log** and confirms its occupancy is ~0, so the delta cannot be logged bodies;
4. forces GC, reads `H1`; `bytes_per_connection = (H1 − H0) / C`;

repeated `H2_MEM_REPEATS` times, reporting the median and sample spread.

```bash
(cd .. && ./mvnw -pl mockserver-netty -am install -DskipTests)   # one-time
./run-h2-connection-memory.sh                 # full sweep -> perf-h2-connection-memory.json
H2_MEM_SHAPES=1x1,5x5 H2_MEM_REPEATS=2 ./run-h2-connection-memory.sh   # tiny smoke
./run-h2-connection-memory.sh selftest        # prove every gate fires (server-free)
```

**Establishment is proven, not assumed.** Four gates fail the run loudly (exit 2) rather than publish a
number over nothing: (1) the driver opened `C` **distinct** local ports (a collapsed/multiplexed client
fails here); (2) the server confirmed `C×S` in-flight streams — impossible on fewer than `ceil(C×S/100)`
connections given `MAX_CONCURRENT_STREAMS=100`, an independent protocol-level proof the axis is real
(1000 in flight ⇒ ≥10 connections); (3) the event log was empty at sample time (delta is not logged
bodies); (4) the per-connection figure is above a floor (measured *something*) and below a ceiling
(did not measure the whole heap). The heap is the whole in-process JVM (client + server), so the figure
is an honest **trend** number; attributing the cost to the *server-side* change is what the cross-version
comparison below does.

**Output shape.** `{ "h2_connection_memory": { "conn_<C>x<S>": {bytes_per_connection, spread_pct,
inflight_confirmed, distinct_connections, log_occupancy, heap_baseline_bytes, heap_loaded_bytes, samples,
…} } }`. The step `perf-test-h2multiplex.sh` merges this with the throughput object into
`perf-h2-multiplex.json`; `perf-test-compare.sh` persists it into the S3 run history for a dated trend.
The `h2_connection_memory.*.bytes_per_connection` budget key is committed **non-gating** and currently
**dormant** (the daily compare's metrics jq does not yet read `.h2_connection_memory` — see the item-11
note in `perf-budgets.json`).

### Cross-version comparison (`run-h2-connection-memory-compare.sh`) — the changelog's whole point

A new number means nothing without something to compare it to. This script answers the changelog directly:
it drives the **same external client** (`hold` mode) against two server images in turn and diffs the server
**container's RSS** per established connection (RSS, not heap, because it is the one measure available
uniformly for any published image, and it captures heap + Netty direct buffers). Because the client is
identical, the difference is attributable to the server-side change. It warms the h2 stream path before the
idle baseline so one-time JVM warm-up is not mis-charged to the measured connections.

<!-- version-ref:keep-begin -->
```bash
H2_MEM_COMPARE_IMAGES="mockserver/mockserver:7.6.0 mockserver/mockserver:mockserver-8.0.0" \
  ./run-h2-connection-memory-compare.sh          # requires the module compiled + docker
```

**Result (2026-09-17, `-m 512m`, 3 repeats, median), pre-multiplex 7.6.0 vs first-multiplex 8.0.0:**

| shape  | 7.6.0 (pre-multiplex) | 8.0.0 (multiplex) | delta |
|--------|----------------------:|------------------:|------:|
| 1×1    | 209,715 B/conn        | 209,716 B/conn    | ~0 (below RSS 0.1 MiB granularity — one connection is unresolvable) |
| 10×10  | 356,515 B/conn        | 346,030 B/conn    | within noise (spreads 29–39% overlap) |
| 100×10 | 271,581 B/conn (spread 4.2%) | 338,690 B/conn (spread 6.2%) | **+24.7%** (~+67 KB/conn ≈ ~6.7 KB per concurrent stream child-channel) |

<!-- version-ref:keep-end -->

**Conclusion.** The changelog's warning is **confirmed and quantified**: the HTTP/2-multiplex migration did
raise per-connection memory — by ~25% at the 100×10 shape RSS can resolve with low spread (the extra
~67 KB/connection is broadly the cost of the 10 concurrent stream-as-child-channels the design adds). It is
a **modest** increase, not the multi-fold blow-up a naive un-warmed measurement first suggested (before the
h2-path warm-up fix, un-amortised first-traffic JVM warm-up inflated it to a spurious ~2.7×). At 1×1 and
10×10 the difference is within RSS granularity/run-to-run noise.

> **Read the delta, not the absolute.** The trustworthy quantity is the **cross-version delta**, because the
> identical client and identical warm-up are applied to both images so everything else cancels. The absolute
> `bytes_per_connection` is **not** a pure per-connection cost: the warm-up opens a fixed 10×10 = 100 streams
> regardless of the measured shape, which pre-grows Netty's pooled arena, so the 100×10 figure is the
> *marginal* cost beyond a ~100-stream-warm process. Likewise the in-process absolute figures are
> whole-JVM (client + server) heap. Treat both absolutes as trend/order-of-magnitude signals; treat the
> cross-version delta as the answer to "did per-connection memory change at 8.0.0?". <!-- version-ref:keep -->

## Connection-scaling ceiling (`run-connection-ceiling.sh`) — programme item 21

How many concurrent established connections a MockServer holds before request latency degrades. k6
allocates a virtual user per connection and is not built to park tens of thousands of idle ones, so
this is the purpose-built holder the plan called for: it opens N connections, proves them established
**on the server**, parks them idle, and measures latency on a **separate** connection. The parked
connections generate no traffic — the axis under test is connection *state*, not offered load.

```bash
# one-time: install the server under test
(cd .. && ./mvnw -pl mockserver-netty -am install -DskipTests)

./run-connection-ceiling.sh calibrate   # what THIS box can hold, and which resource stops it
./run-connection-ceiling.sh             # the ladder (CEILING_MODE=h1 by default, or tls)
```

### Measure the rig before the server

The recurring failure this programme keeps finding is a number that turns out to describe the client.
Two different client ceilings exist here and **they are not the same limit**, so `calibrate` reports
which one it hit rather than just where it stopped:

| Platform | JVM file-descriptor behaviour by default | with `-XX:-MaxFDLimit` |
|---|---|---|
| **macOS** | `min(hard, OPEN_MAX)` = **10,240**, however high `ulimit -n` reads (60,000 shell still yields 10,240) | the inherited soft limit, unchanged — so pair it with a raised `ulimit -n` |
| **Linux** | raises the soft limit to the **hard** limit (measured 1,024 → 1,048,576) | **disables** that, leaving 1,024 |

So the flag is **not** a portable "more descriptors" switch — it helps on macOS and hurts on Linux,
and `run-connection-ceiling.sh` applies it only on Darwin for that reason. An un-flagged macOS run
stops at ~9,977 connections and that is the JDK, not MockServer. The flag is needed on the **server**
JVM too, or the server's own cap is the ceiling the harness "finds".

With the clamp lifted the wall moves to ephemeral source ports: **15,511** connections measured on
one destination port, **15,609** across four — a ratio of **1.01**, so on macOS the source-port range
is global and giving the server more ports buys nothing. Closed sockets hold their port in
`TIME_WAIT` for 2×MSL (30 s), so two adjacent rungs must both fit the range;
`CEILING_TIMEWAIT_DRAIN_MS` defaults to 45 s. If the rig does run out, the rung is recorded
`rig_valid:false` with `exhausted_by`, never as a latency.

### Each rung is paired with its own baseline

A rung's latency means nothing in isolation on a laptop, so every rung is preceded by a
zero-connection sample and reported against **that** partner. One run-start baseline is not enough:
measured that way the box drifted **35% faster** across a single ladder — larger than the effect
being looked for, and monotonic, so every higher rung appeared to be an improvement. Pairing cancels
drift slower than a pair; the first-to-last gap is still printed as a measure of how much the box
moved.

Both arms carry their own establishment proof. Client side: N distinct local ports. Server side: the
event log recorded N requests — every parked connection completed a real request before going idle,
so a socket that only finished a handshake fails the second proof. The TLS arm additionally asserts a
non-null negotiated cipher, because MockServer detects TLS per connection: a driver that failed to
install the handler would still get 200s over plaintext and report a full ladder for the wrong
protocol.

### Result (2026-09-20, Apple M3 Max, Zulu 21.0.3, server and driver in separate JVMs)

**No measurable degradation up to the rig's limit, on either protocol.** Holding 12,000 idle
keep-alive connections (`h1`) or 8,000 TLS connections, probe latency was indistinguishable from the
same measurement with nothing held — every ratio inside the run-to-run spread of the baselines
themselves, with all connections established, zero probe errors and ~97% client CPU headroom.

> **The first run looked like a finding and was not.** It rose steadily across the top four rungs to
> **1.14×** at 12,000 connections. An independent repeat gave 0.97 / 1.00 / 0.84 / 1.14 / 0.79 / 0.88 — no trend.
> The apparent curve was noise the size of the effect. Quote a rung only if a repeat reproduces it.

**Not established:** anything above ~15,500 connections, which is this driver's ephemeral-port
ceiling and not MockServer's. Exceeding it needs more client *source addresses* — loopback aliases or
more load-generator machines — not more server ports.
