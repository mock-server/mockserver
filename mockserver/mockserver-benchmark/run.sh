#!/usr/bin/env bash
#
# Build and run the MockServer hot-path JMH benchmarks.
#
#   ./run.sh                                  # all benchmarks, default settings
#   ./run.sh -prof gc                         # WITH the allocation profiler (the important run)
#   ./run.sh -prof gc -f 1 -wi 3 -i 5 \
#            -p expectationCount=100 -p matcherType=EXACT   # focused, faster run
#   ./run.sh -l                               # list available benchmarks
#
# Any extra args are passed straight through to org.openjdk.jmh.Main. The most
# useful column for allocation work is gc.alloc.rate.norm
# (bytes allocated per op) from `-prof gc`.
#
# Requires mockserver-netty AND its upstream reactor deps installed locally first:
#   (cd .. && mvn -o -pl mockserver-netty -am install -DskipTests)
# The benchmark module depends on core AND netty, and -am builds a
# module's upstream only -- installing core alone leaves netty unresolvable and
# the `mvn compile` below fails.
#
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${DIR}"

# Compile (the JMH annotation processor generates META-INF/BenchmarkList) and
# resolve the full runtime classpath.
mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Djacoco.skip=true

CP="target/classes:$(cat target/classpath.txt)"
exec java -cp "${CP}" org.openjdk.jmh.Main "$@"
