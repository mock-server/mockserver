#!/usr/bin/env bash
#
# Build and run the MockServer matcher "scaling" JMH sweep, then reshape the raw
# JMH JSON into the documentation-site contract file perf-scaling.json.
#
# This proves two things the doc-site chart visualises:
#   1. MatchingBenchmark — request-matching cost (time + allocation) GROWS with the
#      number of registered expectations (the full linear scan, scan length = N).
#   2. CandidateIndexBenchmark — the candidate-index optimization keeps matching cost
#      ~FLAT as N grows (INDEX) versus the linear scan (SCAN), worst-case MISS, literal set.
#
# Unlike run.sh (interactive, free-form JMH args) this script runs a FIXED param sweep
# and emits a machine-readable result. Mirror of .buildkite/scripts/steps/perf-test-microbench.sh.
#
#   ./run-scaling.sh
#   JMH_ARGS_SCALING='-f 1 -wi 1 -i 2 -r 1 -w 1' ./run-scaling.sh    # fast validation run
#   SCALING_RESULT_PATH=/path/to/perf-scaling.json ./run-scaling.sh  # custom output path
#
# Env overrides:
#   JMH_ARGS_SCALING    JMH iteration/fork/time args (default: -f 2 -wi 2 -i 3 -r 2 -w 2)
#   SCALING_RESULT_PATH output contract file (default: <repo-root>/perf-scaling.json)
#
# Requires JDK 17+, Maven and jq. Builds mockserver-netty plus its upstream
# reactor deps -- the benchmark module depends on BOTH core and netty, and -am
# builds a module's upstream only, so targeting core alone leaves netty missing.
#
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${DIR}/../.." && pwd)"
cd "${DIR}"

# Bounded by default; override for a fast validation run. JMH forks a JVM per param
# combo, so iteration counts multiply across the (many) combos below — keep them small.
# -f 2: two forks sample inter-fork JIT variance so the reported dispersion is not understated.
JMH_ARGS_SCALING="${JMH_ARGS_SCALING:--f 2 -wi 2 -i 3 -r 2 -w 2}"
SCALING_RESULT_PATH="${SCALING_RESULT_PATH:-${REPO_ROOT}/perf-scaling.json}"

RAW_MATCHING="${DIR}/target/jmh-scaling-matching.json"
RAW_INDEX="${DIR}/target/jmh-scaling-index.json"

echo "--- building mockserver-netty + its upstream reactor deps"
# Install mockserver-netty AND its upstream (-am), then compile the benchmark so JMH's
# annotation processor regenerates META-INF/BenchmarkList, and resolve the classpath.
# Installing core alone is not enough: some benchmark sources import mockserver-netty.
# Do NOT use "-pl mockserver-benchmark": the module is deliberately absent from the parent
# <modules> (its JMH annotation processor must not enter the default build), so Maven
# cannot select it; the install target must name an in-reactor module.
( cd "${REPO_ROOT}/mockserver" \
  && mvn -q -pl mockserver-netty -am install -DskipTests -DskipITs -P '!build-ui' -Djacoco.skip=true -Dcheckstyle.skip=true )
mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Djacoco.skip=true

CP="target/classes:$(cat target/classpath.txt)"

echo "--- running MatchingBenchmark (scan cost grows with expectationCount)"
# EXACT + REGEX matcher shapes, WARN log level (perf-tuned deployment), with the GC
# profiler so we capture bytes-allocated-per-op alongside time-per-op.
# shellcheck disable=SC2086
java -cp "${CP}" org.openjdk.jmh.Main \
  "org\.mockserver\.benchmark\.MatchingBenchmark\.firstMatchingExpectation_noMatch" \
  -p expectationCount=1,10,100,1000 \
  -p matcherType=EXACT,REGEX \
  -p logLevel=WARN \
  -p detailedMatchFailures=false \
  -prof gc \
  ${JMH_ARGS_SCALING} \
  -rf json -rff "${RAW_MATCHING}"

echo "--- running CandidateIndexBenchmark (SCAN grows, INDEX stays flat)"
# SCAN vs INDEX over the full expectation-count range the class declares (param 'n');
# worst-case MISS, pure-literal set. No GC profiler needed here (time-only contract).
# shellcheck disable=SC2086
java -cp "${CP}" org.openjdk.jmh.Main \
  "org\.mockserver\.benchmark\.CandidateIndexBenchmark\.firstMatchingExpectation" \
  -p n=1,10,100,1000,5000 \
  -p indexMode=SCAN,INDEX \
  -p outcome=MISS \
  -p shape=LITERAL \
  ${JMH_ARGS_SCALING} \
  -rf json -rff "${RAW_INDEX}"

for f in "${RAW_MATCHING}" "${RAW_INDEX}"; do
  if [ ! -f "${f}" ]; then
    echo "ERROR: JMH did not produce ${f}" >&2
    exit 1
  fi
done

echo "--- reshaping into ${SCALING_RESULT_PATH}"
# matching[]:        {expectations, matcherType, time_per_op, time_unit, alloc_bytes_per_op}
# candidate_index[]: {mode, expectations, time_per_op, time_unit}
# time_per_op = primaryMetric.score; time_unit = primaryMetric.scoreUnit;
# alloc_bytes_per_op = secondaryMetrics["gc.alloc.rate.norm"].score (null if absent).
# 'expectations' is the integer param: .params.expectationCount (matching) / .params.n (index).
jq -n \
  --slurpfile matching "${RAW_MATCHING}" \
  --slurpfile index "${RAW_INDEX}" \
  '{
     scaling: {
       matching: [ $matching[0][] | {
         expectations: (.params.expectationCount | tonumber),
         matcherType: .params.matcherType,
         time_per_op: .primaryMetric.score,
         time_unit: .primaryMetric.scoreUnit,
         alloc_bytes_per_op: (.secondaryMetrics["gc.alloc.rate.norm"].score // null)
       } ],
       candidate_index: [ $index[0][] | {
         mode: .params.indexMode,
         expectations: (.params.n | tonumber),
         time_per_op: .primaryMetric.score,
         time_unit: .primaryMetric.scoreUnit
       } ]
     }
   }' > "${SCALING_RESULT_PATH}"

echo "--- perf-scaling.json"
cat "${SCALING_RESULT_PATH}"

if command -v buildkite-agent >/dev/null 2>&1; then
  buildkite-agent artifact upload "$(basename "${SCALING_RESULT_PATH}")" || true
fi
