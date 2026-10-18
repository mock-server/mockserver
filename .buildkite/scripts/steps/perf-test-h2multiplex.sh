#!/usr/bin/env bash
set -euo pipefail

# HTTP/2 multiplex benchmark step (perf queue): the cost of a child channel per
# HTTP/2 stream. Two axes, jq-merged into perf-h2-multiplex.json:
#   1. Http2StreamChannelBenchmark — N streams over ONE h2c connection, N = 1, 10, 100
#      (the server caps MAX_CONCURRENT_STREAMS at 100) -> .h2_multiplex.
#   2. Http2ConnectionMemoryBenchmark — heap per connection for C x S shapes, with the
#      event log cleared before each sample -> .h2_connection_memory.
#
# NOTIFY-ONLY: no threshold fails the build (variance is not yet characterised);
# compare annotates bytes_per_connection against its non-gating budget. A NON-zero
# exit means the harness self-validation failed (an untrustworthy measurement, not a
# slowdown), so it must stay red: no soft_fail. The selftest runs first to prove
# every gate still fires. H2_MEM_COMPARE_IMAGES adds an optional cross-version arm.
# See docs/code/performance-measurement.md.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

MAVEN_IMAGE="${MAVEN_IMAGE:-mockserver/mockserver:maven}"

# Sweep + iteration budget (env-tunable). Defaults give N*requestsPerStream total
# requests per point (e.g. 100*500 = 50,000 at N=100) — enough to be meaningful on
# a quiet box while staying inside the step timeout.
H2_BENCH_STREAMS="${H2_BENCH_STREAMS:-1,10,100}"
H2_BENCH_REQUESTS_PER_STREAM="${H2_BENCH_REQUESTS_PER_STREAM:-500}"
H2_BENCH_WARMUP_REQUESTS="${H2_BENCH_WARMUP_REQUESTS:-200}"

# Per-connection memory axis. shapes = connections x streams; repeats gives
# the sample spread; delay holds the matched responses open so the streams stay in flight.
H2_MEM_SHAPES="${H2_MEM_SHAPES:-1x1,10x10,100x10}"
H2_MEM_REPEATS="${H2_MEM_REPEATS:-5}"
H2_MEM_DELAY_S="${H2_MEM_DELAY_S:-600}"

OUT_JSON="$REPO_ROOT/perf-h2-multiplex.json"

echo "--- building mockserver-netty, then running the HTTP/2 multiplex + per-connection-memory benchmarks"
# shellcheck disable=SC2016
"$SCRIPT_DIR/../run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -m "${MAVEN_MEMORY:-7g}" \
  --entrypoint bash \
  -w /build \
  -e "H2_BENCH_STREAMS=$H2_BENCH_STREAMS" \
  -e "H2_BENCH_REQUESTS_PER_STREAM=$H2_BENCH_REQUESTS_PER_STREAM" \
  -e "H2_BENCH_WARMUP_REQUESTS=$H2_BENCH_WARMUP_REQUESTS" \
  -e "H2_MEM_SHAPES=$H2_MEM_SHAPES" \
  -e "H2_MEM_REPEATS=$H2_MEM_REPEATS" \
  -e "H2_MEM_DELAY_S=$H2_MEM_DELAY_S" \
  -- -c '
    set -euo pipefail
    cd /build/mockserver                       # the Maven reactor root (pom.xml lives here, not /build)
    # -P "!build-ui" skips the node+npm UI bundle: the h2 benchmarks never serve the dashboard,
    # so the bundled UI is dead weight here, as in perf-test-microbench.sh and perf-alloc-gate.sh.
    mvn -q -pl mockserver-netty -am install -DskipTests -DskipITs -P '!build-ui' -Djacoco.skip=true -Dcheckstyle.skip=true
    cd mockserver-benchmark
    mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Djacoco.skip=true
    CP="target/classes:$(cat target/classpath.txt)"
    # Prove every validation gate fires BEFORE trusting a measured run (server-free, fast).
    java -cp "$CP" org.mockserver.benchmark.Http2StreamChannelBenchmark selftest
    java -cp "$CP" org.mockserver.benchmark.Http2ConnectionMemoryBenchmark selftest
    # The measured sweeps. A gate breach in either exits non-zero and fails the step loudly.
    java -cp "$CP" org.mockserver.benchmark.Http2StreamChannelBenchmark /build/perf-h2-throughput.json
    java -cp "$CP" org.mockserver.benchmark.Http2ConnectionMemoryBenchmark /build/perf-h2-connection-memory.json
    # Merge the throughput ({h2_multiplex:...}) and memory ({h2_connection_memory:...}) objects into the
    # single artifact perf-test-compare.sh persists. Distinct top-level keys, so * is a clean union.
    jq -s ".[0] * .[1]" /build/perf-h2-throughput.json /build/perf-h2-connection-memory.json > /build/perf-h2-multiplex.json
  '

if [ ! -f "$OUT_JSON" ]; then
  echo "ERROR: the HTTP/2 benchmark step did not produce $OUT_JSON" >&2
  exit 1
fi

echo "--- perf-h2-multiplex.json"
cat "$OUT_JSON"

if command -v buildkite-agent >/dev/null 2>&1; then
  buildkite-agent artifact upload "perf-h2-multiplex.json" || true
fi

# --- OPTIONAL one-time cross-version comparison (the changelog's whole point) ------------------------------
# Not part of the recurring measurement: it needs two published server images (a pre-8.0.0 and an 8.0.0+),
# which are present on a dev laptop but not on a clean CI agent. Set H2_MEM_COMPARE_IMAGES to two
# space/comma-separated image tags to run it. It drives the SAME external client (the benchmark's `hold`
# mode) against each image, holding the client side constant, and diffs the server container's RSS per
# established connection — so the difference is attributable to the server-side change. Skipped by default;
# never fails the step (it is an investigation, not a gate).
if [ -n "${H2_MEM_COMPARE_IMAGES:-}" ]; then
  echo "--- optional cross-version per-connection RSS comparison: $H2_MEM_COMPARE_IMAGES"
  H2_MEM_COMPARE_IMAGES="$H2_MEM_COMPARE_IMAGES" \
  H2_MEM_COMPARE_SHAPES="${H2_MEM_COMPARE_SHAPES:-1x1 10x10 100x10}" \
  H2_MEM_COMPARE_MEM="${H2_MEM_COMPARE_MEM:-512m}" \
    "$REPO_ROOT/mockserver/mockserver-benchmark/run-h2-connection-memory-compare.sh" \
      || echo "cross-version comparison arm failed (non-fatal)"
fi
