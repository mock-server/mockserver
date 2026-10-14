#!/usr/bin/env bash
#
# Research benchmark: the per-match hand-off to the shared matching thread pool
# (MatchingTimeoutExecutor.callWithTimeout — submit + block a Netty event-loop thread on future.get)
# versus evaluating the same regex inline (regexMatchingTimeoutMillis=0).
# See MatchingTimeoutHandoffBenchmark (JMH) and MatchingTimeoutHandoffProof (the mechanism proof).
#
# This is a RESEARCH benchmark — it is NOT wired into any CI gate.
#
#   ./run-g6-handoff.sh               # proof + the three thread-count JMH runs (t=1,4,8)
#   ./run-g6-handoff.sh proof         # just the pool-on/off + saturation proof (fast, no JMH)
#   ./run-g6-handoff.sh 4             # just the JMH POOL-vs-INLINE matrix at -t 4
#
# One-time prerequisite (install core from THIS worktree so the benchmark measures this source):
#   (cd .. && mvn -o -pl mockserver-core install -DskipTests -Djacoco.skip=true)
#
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${DIR}"

mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Djacoco.skip=true
CP="target/classes:$(cat target/classpath.txt)"

run_proof() {
  echo "=== hand-off + saturation proof (must print PROOF: PASS) ==="
  java -cp "${CP}" org.mockserver.matchers.MatchingTimeoutHandoffProof
}

run_jmh() {
  local threads="$1"
  echo "=== JMH POOL-vs-INLINE at -t ${threads} (-prof gc) ==="
  java -cp "${CP}" org.openjdk.jmh.Main MatchingTimeoutHandoffBenchmark \
    -f 1 -wi 5 -i 5 -t "${threads}" -prof gc
}

case "${1:-all}" in
  proof) run_proof ;;
  1|2|4|8) run_proof; run_jmh "$1" ;;
  all)
    run_proof
    # Run thread counts SEQUENTIALLY — concurrent JVMs would contend for cores and corrupt timings.
    for t in 1 4 8; do run_jmh "$t"; done
    ;;
  *) echo "usage: $0 [proof|1|2|4|8|all]"; exit 2 ;;
esac
