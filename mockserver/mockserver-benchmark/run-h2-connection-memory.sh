#!/usr/bin/env bash
#
# Build and run the HTTP/2 PER-CONNECTION MEMORY benchmark.
#
# Adds the CONNECTIONS axis the throughput harness (run-h2-multiplex.sh) lacks: N connections x M
# concurrent in-flight streams, shapes 1x1 / 10x10 / 100x10, recording the heap delta per established
# connection — exactly what the 8.0.0 changelog warned the HTTP/2-multiplex migration changed. The event
# log is cleared before each heap sample, so the delta measures connection + stream child-channel state,
# not logged request/response bodies.
#
#   ./run-h2-connection-memory.sh                 # full sweep -> perf-h2-connection-memory.json
#   ./run-h2-connection-memory.sh selftest        # prove every validation gate fires (no server)
#   H2_MEM_SHAPES=1x1,5x5 H2_MEM_REPEATS=2 ./run-h2-connection-memory.sh   # tiny smoke
#
# Tunables (env): H2_MEM_SHAPES (e.g. "1x1,10x10,100x10"), H2_MEM_REPEATS, H2_MEM_DELAY_S,
# H2_MEM_ESTABLISH_TIMEOUT_S, H2_MEM_OUTPUT.
#
# The harness self-validates and exits NON-ZERO (code 2, "HARNESS VALIDATION FAILED") on any integrity
# breach — connections not distinct, streams not established (C*S in flight is impossible on fewer than
# ceil(C*S/100) connections given MAX_CONCURRENT_STREAMS=100), the event log not empty at sample time, or a
# per-connection figure below a floor / above a ceiling. That is deliberately distinct from a valid run that
# simply records more (or less) memory, which exits 0.
#
# For the one-time CROSS-VERSION comparison against a pre-8.0.0 image (the measurement that answers the
# changelog), see run-h2-connection-memory-compare.sh.
#
# Requires mockserver-netty installed locally first:
#   (cd .. && ./mvnw -pl mockserver-netty -am install -DskipTests)
#
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${DIR}"

mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Djacoco.skip=true

CP="target/classes:$(cat target/classpath.txt)"
exec java -cp "${CP}" org.mockserver.benchmark.Http2ConnectionMemoryBenchmark "$@"
