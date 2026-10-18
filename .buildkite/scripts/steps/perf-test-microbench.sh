#!/usr/bin/env bash
set -euo pipefail

# Micro-benchmark step (perf queue): JMH backstops that do not depend on the
# rolling k6 baseline. Runs MatchingBenchmark (matcher hot path, gating), the
# promoted extra and proxy-path benchmarks (notify-only), the scaling sweep and
# the candidate-index churn gate, and uploads their JSON for perf-test-compare.sh.
# Heavy (builds mockserver-netty + upstream, forks a JVM per param), so it runs
# only in the perf pipeline. Design notes: docs/code/performance-measurement.md.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

MAVEN_IMAGE="${MAVEN_IMAGE:-mockserver/mockserver:maven}"
# 3 matcher types at the 100-expectation scan depth. Keep -f 2: one fork never
# samples inter-fork JIT variance, so the derived timing budget would be too tight.
# The class include keeps the extra benchmarks below out of the gating rows.
# Keep exactly one detailedMatchFailures arm pinned (`true`, the shipped default):
# unpinned, both arms land in the gating set. The reshape suffixes it `_detailed`;
# the `false` arm keeps its allocation floor in perf-alloc-gate.sh.
JMH_ARGS="${JMH_ARGS:--f 2 -wi 2 -i 3 -r 2 -w 2 -p matcherType=EXACT,REGEX,JSON_BODY -p expectationCount=100 -p logLevel=INFO -p detailedMatchFailures=true -prof gc}"
JMH_INCLUDE="${JMH_INCLUDE:-org\.mockserver\.benchmark\.MatchingBenchmark\.}"

# --- promoted extra benchmarks (notify-only, under microbench_extra) ------------
# Same classpath, one extra JMH invocation. -bm avgt forces one mode so every row
# is a comparable time_per_op (MetricsIncrement declares Throughput+AverageTime).
JMH_INCLUDE_EXTRA="${JMH_INCLUDE_EXTRA:-org\.mockserver\.benchmark\.(InboundDecodeBenchmark|LocalCallbackDispatchBenchmark|ForwardPathBenchmark|OpenApiValidationBenchmark|MetricsIncrementBenchmark|ResponseWriteBenchmark|Http3RequestBridgeBenchmark)\.}"
JMH_ARGS_EXTRA="${JMH_ARGS_EXTRA:--f 2 -wi 2 -i 3 -r 2 -w 2 -bm avgt -prof gc}"

# EXACT row count across the extra and proxy invocations, so a renamed class or a
# proxy -p pin that stopped applying fails closed (non-gating rows would otherwise
# drift unnoticed). Bump it deliberately with any benchmark surface change:
#   InboundDecode 1x3 bodySize = 3      LocalCallbackDispatch 3 methods = 3
#   ForwardPath 8 methods      = 8      OpenApiValidation 1x2 mode x2 schema = 4
#   MetricsIncrement 2 (avgt)  = 2      ResponseWrite 1x3 size x2 declareBodyCharset = 6
#   Http3RequestBridge 1x2 protocol x3 bodySize = 6                   extra subtotal = 32
#   RelayByteCopy 2 methods (pinned) = 2   SocksHandshake 3 methods (pinned) = 3  = 37
EXTRA_EXPECTED="${EXTRA_EXPECTED:-37}"

# --- proxy-path benchmarks (notify-only, merged into microbench_extra) ----------
# A SEPARATE invocation, not appended to JMH_INCLUDE_EXTRA: its -p pins would also
# pin InboundDecodeBenchmark (which shares the bodySize @Param), and unpinned their
# full cartesians would blow the step timeout. JMH applies each -p only to the class
# that declares it. Pinned cell: 256 KiB relayed in 1460-byte fragments; SOCKS5_PASSWORD.
JMH_INCLUDE_PROXY="${JMH_INCLUDE_PROXY:-org\.mockserver\.benchmark\.(RelayByteCopyBenchmark|SocksHandshakeBenchmark)\.}"
JMH_ARGS_PROXY="${JMH_ARGS_PROXY:--f 2 -wi 2 -i 3 -r 2 -w 2 -bm avgt -prof gc -p bodySize=262144 -p readSize=1460 -p scenario=SOCKS5_PASSWORD}"

# Defined here, not in the scaling section, because the .config.jmh fingerprint
# recorded with the microbench result includes it.
JMH_ARGS_SCALING="${JMH_ARGS_SCALING:--f 2 -wi 2 -i 3 -r 2 -w 2}"

RESULT_RAW="mockserver/mockserver-benchmark/target/jmh-result.json"
EXTRA_RAW="mockserver/mockserver-benchmark/target/jmh-result-extra.json"
PROXY_RAW="mockserver/mockserver-benchmark/target/jmh-result-proxy.json"
OUT_JSON="$REPO_ROOT/perf-microbench.json"
EXTRA_JSON="$REPO_ROOT/perf-microbench-extra.json"

# Compare depends on this step with `allow_failure: true` (see perf-test-guard.sh),
# so it runs regardless of this step's exit code and never reports its failure.
# Annotate the build ourselves so a dead backstop is not just an unwatched red square.
annotate_on_failure() {
  local ec=$?
  if [ "$ec" -ne 0 ] && command -v buildkite-agent >/dev/null 2>&1; then
    printf '%s\n' ":x: **Perf micro-benchmark backstop FAILED** (exit ${ec}) — a JMH benchmark (MatchingBenchmark, the promoted dark benchmarks, or the scaling sweep) produced NO signal for this run. This is a harness/build failure (e.g. the benchmark reactor did not build/resolve, or a benchmark include matched nothing), NOT a measured regression. See this step's log." \
      | buildkite-agent annotate --style error --context perf-microbench || true
  fi
}
trap annotate_on_failure EXIT

echo "--- building mockserver-netty + upstream (the benchmark's compile deps), then running MatchingBenchmark"
# shellcheck disable=SC2016
"$SCRIPT_DIR/../run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -m "${MAVEN_MEMORY:-7g}" \
  --entrypoint bash \
  -w /build \
  -e "JMH_ARGS=$JMH_ARGS" \
  -e "JMH_INCLUDE=$JMH_INCLUDE" \
  -e "JMH_ARGS_EXTRA=$JMH_ARGS_EXTRA" \
  -e "JMH_INCLUDE_EXTRA=$JMH_INCLUDE_EXTRA" \
  -e "JMH_ARGS_PROXY=$JMH_ARGS_PROXY" \
  -e "JMH_INCLUDE_PROXY=$JMH_INCLUDE_PROXY" \
  -- -c '
    set -euo pipefail
    cd /build/mockserver                       # the Maven reactor root (pom.xml lives here, not /build)
    # -pl mockserver-netty -am must cover every org.mock-server module the benchmark
    # depends on (mockserver-benchmark/pom.xml). Not -pl mockserver-benchmark: that
    # module is deliberately outside the parent <modules>, so Maven cannot select it.
    mvn -q -pl mockserver-netty -am install -DskipTests -DskipITs -P '!build-ui' -Djacoco.skip=true -Dcheckstyle.skip=true
    cd mockserver-benchmark
    mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Djacoco.skip=true
    CP="target/classes:$(cat target/classpath.txt)"
    # shellcheck disable=SC2086
    java -cp "$CP" org.openjdk.jmh.Main "$JMH_INCLUDE" $JMH_ARGS -rf json -rff target/jmh-result.json
    # The promoted extra benchmarks, same classpath.
    # shellcheck disable=SC2086
    java -cp "$CP" org.openjdk.jmh.Main "$JMH_INCLUDE_EXTRA" $JMH_ARGS_EXTRA -rf json -rff target/jmh-result-extra.json
    # The proxy-path benchmarks: a separate invocation so their -p pins stay off the
    # extra classes above (see JMH_ARGS_PROXY).
    # shellcheck disable=SC2086
    java -cp "$CP" org.openjdk.jmh.Main "$JMH_INCLUDE_PROXY" $JMH_ARGS_PROXY -rf json -rff target/jmh-result-proxy.json
  '

if [ ! -f "$REPO_ROOT/$RESULT_RAW" ]; then
  echo "ERROR: JMH did not produce $RESULT_RAW" >&2
  exit 1
fi

# Reshape JMH's array into {microbench: {<matcherType>_<count>[_detailed]: {time_per_op, ...}}}
# with lib/perf-microbench-reshape.jq, which scores the gating time_per_op as a trimmed mean of the
# measured iterations rather than JMH's mean (see that file). The `_detailed` suffix marks a
# detailedMatchFailures=true row (see the pin note on JMH_ARGS).
#
# It also records the JMH arg strings as the .config.jmh fingerprint: compare skips
# baseline runs with a different fingerprint, so a methodology change resets the
# baseline instead of firing a false regression. Keep JMH_ARGS_PROXY out of it: the
# proxy forks cannot move the matcher timings, and including it would reset the one
# gating baseline on every proxy-only tweak.
jq --arg args "$JMH_ARGS" --arg argsExtra "$JMH_ARGS_EXTRA" --arg argsScaling "$JMH_ARGS_SCALING" \
   -f "$SCRIPT_DIR/lib/perf-microbench-reshape.jq" \
  "$REPO_ROOT/$RESULT_RAW" > "$OUT_JSON"

echo "--- perf-microbench.json"
cat "$OUT_JSON"

if command -v buildkite-agent >/dev/null 2>&1; then
  buildkite-agent artifact upload "perf-microbench.json" || true
fi

# --- reshape the extra + proxy benchmark results -------------------------------
if [ ! -f "$REPO_ROOT/$EXTRA_RAW" ]; then
  echo "ERROR: promoted dark benchmarks did not produce $EXTRA_RAW (JMH include matched nothing, or the run crashed)" >&2
  exit 1
fi
# A missing proxy raw must fail here; `jq -s add` below would otherwise iterate null.
if [ ! -f "$REPO_ROOT/$PROXY_RAW" ]; then
  echo "ERROR: proxy-path benchmarks (items 9b/9c) did not produce $PROXY_RAW (JMH include matched nothing, or the run crashed)" >&2
  exit 1
fi

# Both result arrays become one `microbench_extra` object keyed
# <ClassName>.<method>[_<param>-<value>...]; compare treats it as notify-only.
jq -s 'add | [.[] | {
      key: ((.benchmark | sub("^org\\.mockserver\\.benchmark\\.";""))
            + (if ((.params // {}) | length) > 0
               then "_" + ((.params) | to_entries | sort_by(.key) | map(.key + "-" + (.value|tostring)) | join("_"))
               else "" end)),
      value: {
        time_per_op: .primaryMetric.score,
        time_unit: .primaryMetric.scoreUnit,
        alloc_bytes_per_op: (.secondaryMetrics["gc.alloc.rate.norm"].score // null)
      }
    }] | from_entries | {microbench_extra: .}' \
  "$REPO_ROOT/$EXTRA_RAW" "$REPO_ROOT/$PROXY_RAW" > "$EXTRA_JSON"

# Exact row count (see EXTRA_EXPECTED). Must be checked here, at the producer:
# compare iterates head metrics only, so it cannot see a row that went missing.
EXTRA_COUNT="$(jq '.microbench_extra | length' "$EXTRA_JSON")"
if [ "${EXTRA_COUNT:-0}" -ne "$EXTRA_EXPECTED" ]; then
  echo "ERROR: promoted extra benchmarks (item 15b dark set + item 9b/9c proxy set) produced ${EXTRA_COUNT:-0} result rows in $EXTRA_JSON, expected ${EXTRA_EXPECTED} — benchmark rename/include drift, a crashed fork, a proxy -p pin that stopped applying, or a deliberate surface change that did not bump EXTRA_EXPECTED." >&2
  echo "Rows actually emitted:" >&2
  jq -r '.microbench_extra | keys[] | "  - " + .' "$EXTRA_JSON" >&2 || true
  exit 1
fi
echo "--- perf-microbench-extra.json (${EXTRA_COUNT}/${EXTRA_EXPECTED} promoted benchmark rows)"
cat "$EXTRA_JSON"

if command -v buildkite-agent >/dev/null 2>&1; then
  buildkite-agent artifact upload "perf-microbench-extra.json" || true
fi

# --- scaling sweep ------------------------------------------------------------
# run-scaling.sh: MatchingBenchmark + CandidateIndexBenchmark over a fixed param
# sweep -> perf-scaling.json. Recorded, not compared. It does its own reactor build;
# JMH_ARGS_SCALING bounds it to fit the step timeout (one fork per param combo).
SCALING_RAW="mockserver/mockserver-benchmark/perf-scaling.json"

echo "--- running scaling sweep (run-scaling.sh)"
# shellcheck disable=SC2016
"$SCRIPT_DIR/../run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -m "${MAVEN_MEMORY:-7g}" \
  --entrypoint bash \
  -w /build \
  -e "JMH_ARGS_SCALING=$JMH_ARGS_SCALING" \
  -- -c '
    set -euo pipefail
    cd /build/mockserver/mockserver-benchmark        # run-scaling.sh resolves the reactor root from here
    SCALING_RESULT_PATH="$(pwd)/perf-scaling.json" ./run-scaling.sh
  '

if [ ! -f "$REPO_ROOT/$SCALING_RAW" ]; then
  echo "ERROR: scaling sweep did not produce $SCALING_RAW" >&2
  exit 1
fi
cp "$REPO_ROOT/$SCALING_RAW" "$REPO_ROOT/perf-scaling.json"

echo "--- perf-scaling.json"
cat "$REPO_ROOT/perf-scaling.json"

if command -v buildkite-agent >/dev/null 2>&1; then
  buildkite-agent artifact upload "perf-scaling.json" || true
fi

# --- G1 churn gate ------------------------------------------------------------
# run-g1-churn.sh: CandidateIndexChurnBenchmark's gated arm -> perf-churn.json. The
# churn/static allocation ratio is budgeted in perf-budgets.json, so it GATES. A dead
# writer thread would make a churn run look static and pass the floor; the
# benchmark's @TearDown assertion (with JMH -foe true) is what fails the step then.
CHURN_RAW="mockserver/mockserver-benchmark/perf-churn.json"
JMH_ARGS_CHURN="${JMH_ARGS_CHURN:--f 1 -wi 3 -i 5 -r 1 -w 1 -t 1}"

echo "--- running G1 churn gate (run-g1-churn.sh ci)"
# shellcheck disable=SC2016
"$SCRIPT_DIR/../run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -m "${MAVEN_MEMORY:-7g}" \
  --entrypoint bash \
  -w /build \
  -e "JMH_ARGS_CHURN=$JMH_ARGS_CHURN" \
  -- -c '
    set -euo pipefail
    cd /build/mockserver/mockserver-benchmark        # run-g1-churn.sh resolves the reactor root from here
    CHURN_RESULT_PATH="$(pwd)/perf-churn.json" ./run-g1-churn.sh ci
  '

if [ ! -f "$REPO_ROOT/$CHURN_RAW" ]; then
  echo "ERROR: G1 churn gate did not produce $CHURN_RAW" >&2
  exit 1
fi
cp "$REPO_ROOT/$CHURN_RAW" "$REPO_ROOT/perf-churn.json"

echo "--- perf-churn.json"
cat "$REPO_ROOT/perf-churn.json"

if command -v buildkite-agent >/dev/null 2>&1; then
  buildkite-agent artifact upload "perf-churn.json" || true
fi
