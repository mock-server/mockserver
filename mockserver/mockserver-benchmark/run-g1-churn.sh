#!/usr/bin/env bash
#
# Research benchmark: candidate-index / sorted-list REBUILD cost under expectation churn.
# See CandidateIndexChurnBenchmark (JMH) and CandidateIndexChurnRebuildProof (the mechanism proof).
#
# TWO ways to run this:
#
#   RESEARCH (free-form, prints JMH tables to stdout — NOT gated):
#     ./run-g1-churn.sh                 # proof + the three thread-count JMH runs (t=1,4,8)
#     ./run-g1-churn.sh proof           # just the rebuild-proof (fast, no JMH)
#     ./run-g1-churn.sh 1               # just the JMH matrix at -t 1
#   One-time prerequisite for the research modes (install core from THIS worktree so the
#   benchmark measures this source):
#     (cd .. && mvn -o -pl mockserver-core install -DskipTests -Djacoco.skip=true)
#
#   CI (machine-readable contract file for the daily perf gate — SELF-CONTAINED build):
#     ./run-g1-churn.sh ci              # emits perf-churn.json {churn:{alloc_ratio_index_n15000, ...}}
#   Driven by .buildkite/scripts/steps/perf-test-microbench.sh. Mirror of run-scaling.sh:
#   it builds mockserver-netty + its upstream reactor deps (the benchmark module depends on
#   BOTH core and netty, and -am builds a module's upstream only, so targeting core alone
#   leaves netty missing), runs the rebuild PROOF as a mechanism sanity check, then runs the
#   ONE gated arm (n=15,000, indexMode=INDEX, mode=STATIC vs CHURN, -t 1, JMH -foe true) and
#   reshapes the churn/static ALLOCATION ratio into the contract file. The FAIL-CLOSED guard
#   that a CHURN arm actually churned (rather than silently degrading to static when its daemon
#   writer thread dies, which would land the ratio at ~1.0 and pass the 1.5 floor green) lives
#   in the benchmark's own @TearDown: it throws on unhealthy churn signals, and -foe true makes
#   that JMH error exit non-zero so `set -e` reds the step. The ratio is a within-run A/B, so
#   it cancels host/JVM/GC noise and is machine-independent — allocation is bytes/op, not timing.
#
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${DIR}/../.." && pwd)"
cd "${DIR}"

# CI-mode knobs (overridable for a fast local validation run). -f 1: gc.alloc.rate.norm
# is deterministic across forks (allocation is bytes/op, not timing), and the metric is a
# RATIO, so a single fork is sufficient and faster than the microbench timing runs' -f 2.
JMH_ARGS_CHURN="${JMH_ARGS_CHURN:--f 1 -wi 3 -i 5 -r 1 -w 1 -t 1}"
CHURN_RESULT_PATH="${CHURN_RESULT_PATH:-${REPO_ROOT}/perf-churn.json}"
RAW_CHURN="${DIR}/target/jmh-churn.json"

build_classpath() {
  mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Djacoco.skip=true
  CP="target/classes:$(cat target/classpath.txt)"
}

run_proof() {
  echo "=== rebuild proof (must print PROOF: PASS) ==="
  java -cp "${CP}" org.mockserver.mock.CandidateIndexChurnRebuildProof
}

run_jmh() {
  local threads="$1"
  echo "=== JMH matrix at -t ${threads} (-prof gc) ==="
  java -cp "${CP}" org.openjdk.jmh.Main CandidateIndexChurnBenchmark \
    -f 1 -wi 3 -i 5 -t "${threads}" -prof gc
}

# --- CI: build, PROVE the churn arm is real, measure ONE arm, emit the contract file ------
run_ci() {
  echo "--- building mockserver-netty + its upstream reactor deps (self-contained, mirrors run-scaling.sh)"
  # Install mockserver-netty AND its upstream (-am), then compile the benchmark so JMH's
  # annotation processor regenerates META-INF/BenchmarkList, and resolve the classpath. The
  # -am set covers BOTH org.mock-server module deps the benchmark declares (core AND netty);
  # installing only core fails because "mvn compile" compiles ALL benchmark sources, some of
  # which import mockserver-netty. We do NOT use "-pl mockserver-benchmark": the benchmark
  # module is deliberately absent from the parent <modules> (its JMH annotation processor
  # must not enter the default build), so Maven cannot select it as a reactor project.
  ( cd "${REPO_ROOT}/mockserver" \
    && mvn -q -pl mockserver-netty -am install -DskipTests -DskipITs -P '!build-ui' -Djacoco.skip=true -Dcheckstyle.skip=true )
  build_classpath

  # MECHANISM SANITY CHECK (supplementary, NOT the dead-writer guard). CandidateIndexChurnRebuildProof
  # is a SYNCHRONOUS, main-thread check: it does a manual clear+add and asserts the mutation bumps
  # the store's modification counter AND makes toSortedList() return a fresh instance, while a static
  # store reuses its cached one. It confirms the rebuild MECHANISM the benchmark relies on still
  # works (e.g. catches a refactor that stopped nulling sortedCache). It does NOT — and cannot —
  # observe the benchmark's daemon WRITER THREAD, which is the thing the measured CHURN arm depends
  # on. The guard against a silently-static CHURN arm (dead writer -> ratio ~1.0 -> false green) is
  # the benchmark's OWN @TearDown assertion, which throws when storeModifications/rebuilds/
  # stillMatchesUnderChurn show the arm did not churn, enforced by JMH -foe true below. Require the
  # exact "PROOF: PASS" line here; anything else reds the step.
  echo "--- churn rebuild proof (mechanism sanity check)"
  local proof_out
  proof_out="$(java -cp "${CP}" org.mockserver.mock.CandidateIndexChurnRebuildProof 2>&1)" || true
  printf '%s\n' "${proof_out}"
  if ! grep -qx 'PROOF: PASS' <<<"${proof_out}"; then
    echo "ERROR: CandidateIndexChurnRebuildProof did not print 'PROOF: PASS' — the churn arm cannot be trusted to actually churn, so the ratio is meaningless. Failing the step (fail-closed) rather than emitting a green-looking ratio." >&2
    exit 1
  fi

  # The ONE gated arm: n=15,000 expectations, candidate index engaged (INDEX), STATIC vs CHURN,
  # single reader thread. -prof gc captures gc.alloc.rate.norm (bytes/op). -p overrides the
  # class @Param values, so exactly these 2 combos run.
  echo "--- churn JMH gated arm (n=15000, indexMode=INDEX, mode=STATIC,CHURN, ${JMH_ARGS_CHURN})"
  # -foe true (fail-on-error) is HARDCODED, not part of the overridable JMH_ARGS_CHURN, because it
  # is a correctness invariant of the fail-closed design, NOT a tuning knob. JMH's failOnError
  # DEFAULTS TO FALSE: without -foe true, the CandidateIndexChurnBenchmark @TearDown throw that
  # fires when the CHURN arm did not actually churn (dead writer thread -> degraded-to-static
  # measurement) is PRINTED but JMH still exits 0 and may serialize a ~1.0 CHURN ratio — the
  # step would go green on a run that measured nothing. With -foe true, that teardown exception
  # makes org.openjdk.jmh.Main exit non-zero, so `set -e` reds the step. Proven by forcing a dead
  # writer: exit 0 without -foe, exit 1 with it.
  # shellcheck disable=SC2086
  java -cp "${CP}" org.openjdk.jmh.Main CandidateIndexChurnBenchmark \
    -p n=15000 -p indexMode=INDEX -p mode=STATIC,CHURN \
    ${JMH_ARGS_CHURN} -foe true -prof gc \
    -rf json -rff "${RAW_CHURN}"

  if [ ! -f "${RAW_CHURN}" ]; then
    echo "ERROR: JMH did not produce ${RAW_CHURN}" >&2
    exit 1
  fi

  echo "--- reshaping into ${CHURN_RESULT_PATH}"
  # churn.alloc_ratio_index_n15000 = CHURN alloc_bytes_per_op / STATIC alloc_bytes_per_op at
  # n=15000/INDEX. JMH @Param values are STRINGS (.params.n == "15000"). The raw STATIC/CHURN
  # bytes are carried alongside as UNBUDGETED provenance (perf-test-compare.sh's metrics jq
  # names ONLY .churn.alloc_ratio_index_n15000, so these siblings never become head metrics and
  # cannot trip the fail-closed missing-budget rule).
  jq '
    ([ .[] | select(.params.indexMode == "INDEX" and .params.n == "15000") ]) as $rows
    | ($rows | map(select(.params.mode == "STATIC")) | (.[0].secondaryMetrics["gc.alloc.rate.norm"].score // null)) as $static
    | ($rows | map(select(.params.mode == "CHURN"))  | (.[0].secondaryMetrics["gc.alloc.rate.norm"].score // null)) as $churn
    | { churn: {
          alloc_ratio_index_n15000: (if ($static != null and $churn != null and $static > 0) then ($churn / $static) else null end),
          static_alloc_bytes_per_op: $static,
          churn_alloc_bytes_per_op: $churn,
          n: 15000,
          index_mode: "INDEX",
          threads: 1
      } }' "${RAW_CHURN}" > "${CHURN_RESULT_PATH}"

  # Fail-closed: the reshape must have produced a positive numeric ratio. A missing arm (JMH
  # include drift, a crashed fork) yields null, which would otherwise sail through as a merged
  # block with a null value that compare silently drops — the gate would vanish without a red.
  local ratio
  ratio="$(jq -r '.churn.alloc_ratio_index_n15000 // "null"' "${CHURN_RESULT_PATH}")"
  if ! grep -Eq '^[0-9]+(\.[0-9]+)?$' <<<"${ratio}"; then
    echo "ERROR: churn reshape produced no numeric alloc ratio (got '${ratio}') — a STATIC or CHURN arm did not emit gc.alloc.rate.norm at n=15000/INDEX. Failing the step (fail-closed)." >&2
    exit 1
  fi

  echo "--- perf-churn.json"
  cat "${CHURN_RESULT_PATH}"

  if command -v buildkite-agent >/dev/null 2>&1; then
    buildkite-agent artifact upload "$(basename "${CHURN_RESULT_PATH}")" || true
  fi
}

case "${1:-all}" in
  ci) run_ci ;;
  proof) build_classpath; run_proof ;;
  1|2|4|8) build_classpath; run_proof; run_jmh "$1" ;;
  all)
    build_classpath
    run_proof
    # Run thread counts SEQUENTIALLY — concurrent JVMs would contend for cores and corrupt timings.
    for t in 1 4 8; do run_jmh "$t"; done
    ;;
  *) echo "usage: $0 [ci|proof|1|2|4|8|all]"; exit 2 ;;
esac
