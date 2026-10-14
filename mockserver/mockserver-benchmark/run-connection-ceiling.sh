#!/usr/bin/env bash
#
# The CONNECTION-SCALING CEILING: how many concurrent established connections a
# MockServer holds before request latency degrades. Starts a server, runs the ladder against it, stops it.
#
#   ./run-connection-ceiling.sh                 # measure, using the calibrated client ceiling below
#   ./run-connection-ceiling.sh calibrate       # find THIS box's client ceiling and what limits it
#
# -XX:-MaxFDLimit is applied ON macOS ONLY, and that asymmetry is deliberate because the flag does
# OPPOSITE things on the two platforms:
#   * macOS — the JDK sets its own RLIMIT_NOFILE to min(hard, OPEN_MAX=10240) however high `ulimit -n`
#     reads, so an un-flagged JVM (driver OR server) hits the JDK's limit, not the server's. The flag
#     leaves the inherited soft limit alone, so it must be paired with a raised `ulimit -n`.
#   * Linux — the JDK already raises the soft limit to the HARD limit. The flag DISABLES that and
#     leaves 1024. Never pass it here.
# With the clamp lifted on macOS the wall moves to the client's ephemeral source-port range, which is
# GLOBAL, so more server ports do not raise the ceiling. Run `calibrate` on any new box. Measured
# figures: docs/code/performance-measurement.md.
#
# GETTING PAST THE SOURCE-PORT WALL — MULTIPLE CLIENT SOURCE ADDRESSES (REQUIRES ROOT, MANUAL STEP):
#   The wall is the client's GLOBAL ephemeral source-port range, not the server. More SERVER
#   ports do not move it. The only lever is more client source ADDRESSES: each local
#   address has its own ephemeral range, so N loopback aliases roughly multiply the ceiling by N. Set
#   CEILING_SOURCE_ADDRESSES to a comma-separated list and the driver round-robins connections across
#   them, reporting how many distinct addresses were actually used and how many connections landed on
#   each (so a missing alias cannot be mistaken for a raised ceiling — the driver refuses to start if any
#   listed address is not bindable).
#
#   Creating a non-primary loopback alias needs root, which this script does NOT do for you. On macOS,
#   BEFORE the run, create the aliases (example for three extra addresses):
#       sudo ifconfig lo0 alias 127.0.0.2 up
#       sudo ifconfig lo0 alias 127.0.0.3 up
#       sudo ifconfig lo0 alias 127.0.0.4 up
#   then run e.g.:
#       CEILING_SOURCE_ADDRESSES=127.0.0.1,127.0.0.2,127.0.0.3,127.0.0.4 \
#         CEILING_LADDER=8000,20000,40000 ./run-connection-ceiling.sh
#   and AFTER the run, TEAR THE ALIASES DOWN so the machine is not left reconfigured:
#       sudo ifconfig lo0 -alias 127.0.0.2
#       sudo ifconfig lo0 -alias 127.0.0.3
#       sudo ifconfig lo0 -alias 127.0.0.4
#   (List current aliases with `ifconfig lo0`; a reboot also clears them. On Linux the whole 127.0.0.0/8
#   is already local, so no alias is needed — just list the addresses.)
#
# Tunables (env): CEILING_MODE (h1|tls|calibrate), CEILING_LADDER, CEILING_PROBE_REQUESTS,
# CEILING_SETTLE_MS, CEILING_WARMUP_REQUESTS, CEILING_TIMEWAIT_DRAIN_MS, CEILING_CLIENT_CEILING,
# CEILING_SERVER_PORTS, CEILING_SOURCE_ADDRESSES.
#
# The ladder must also fit TWO ADJACENT rungs in the port range: a rung's sockets sit in TIME_WAIT for
# 2*MSL (30 s on macOS) after it closes, so 8,000 followed immediately by 12,000 asks for 20,000 ports
# and fails part-way up. CEILING_TIMEWAIT_DRAIN_MS defaults to 45 s for that reason. If the rig does run
# out, the driver records the rung as `rig_valid:false` with `exhausted_by` rather than reporting a
# latency — a rig limit is a result about the rig, never a server ceiling.
#
# Requires mockserver-netty installed locally first:
#   (cd .. && ./mvnw -pl mockserver-netty -am install -DskipTests)
#
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${DIR}"

PORTS="${CEILING_SERVER_PORTS:-1080,1081,1082,1083}"
JAR="${DIR}/../mockserver-netty/target/mockserver-netty-$(cd .. && mvn -q -o help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null)-jar-with-dependencies.jar"
if [ ! -f "${JAR}" ]; then
    echo "ERROR: fat jar not found at ${JAR} — run (cd .. && ./mvnw -pl mockserver-netty -am package -DskipTests)" >&2
    exit 2
fi

mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Djacoco.skip=true
CP="target/classes:$(cat target/classpath.txt)"

# See the header: on macOS both JVMs need the flag or the server's own 10,240 descriptor cap is the
# ceiling this harness "finds"; on Linux the same flag would lower the limit instead of raising it.
FD_FLAG=()
if [ "$(uname -s)" = "Darwin" ]; then
    FD_FLAG=(-XX:-MaxFDLimit)
    ulimit -Sn 65536 2>/dev/null || true
fi

java "${FD_FLAG[@]}" -Xms1g -Xmx4g -jar "${JAR}" -serverPort "${PORTS}" -logLevel WARN > target/ceiling-server.log 2>&1 &
SERVER_PID=$!
trap 'kill "${SERVER_PID}" 2>/dev/null || true' EXIT

FIRST_PORT="${PORTS%%,*}"
ALL_PORTS="$(printf '%s' "${PORTS}" | tr ',' ' ')"

# Readiness is the control-plane 200, never a listening port — MockServer accepts and then resets while
# it is still initialising, so a port probe reports ready before the server can serve.
#
# EVERY port, not just the first, and the server process must still be alive: a stale MockServer
# on the first port answers 200 while ours, unable to bind, has exited — and the ladder then
# reports the refused connections as "RIG EXHAUSTED", blaming the wrong limit.
ready=false
for _ in $(seq 1 60); do
    if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
        echo "ERROR: the benchmark server exited during start-up. Last lines of target/ceiling-server.log:" >&2
        tail -5 target/ceiling-server.log >&2
        echo "HINT: if it printed its usage text, it could not bind one of ${PORTS} — something else is" >&2
        echo "      probably holding one. Naming only the first port here would repeat the very mistake" >&2
        echo "      this check exists to catch, so here is every one of them:" >&2
        for p in ${ALL_PORTS}; do
            # `|| true`: under `set -euo pipefail` an lsof that matches nothing exits non-zero and
            # would kill the script here — inside the very handler whose job is to explain the
            # failure, losing both the listing and the exit code the caller is checking for.
            holder="$(lsof -nP -iTCP:"${p}" -sTCP:LISTEN 2>/dev/null | tail -1 || true)"
            echo "      port ${p}: ${holder:-(nothing listening)}" >&2
        done
        echo "      Free the conflicting one, or pick another set with CEILING_SERVER_PORTS." >&2
        exit 2
    fi
    all_ok=true
    for p in ${ALL_PORTS}; do
        if [ "$(curl -s -o /dev/null -w '%{http_code}' -X PUT "http://127.0.0.1:${p}/mockserver/status" || true)" != "200" ]; then
            all_ok=false; break
        fi
    done
    if [ "${all_ok}" = true ]; then ready=true; break; fi
    sleep 1
done
if [ "${ready}" != true ]; then
    echo "ERROR: server did not become ready on ALL of ${PORTS}; see target/ceiling-server.log" >&2
    for p in ${ALL_PORTS}; do
        echo "    port ${p}: $(curl -s -o /dev/null -w '%{http_code}' -X PUT "http://127.0.0.1:${p}/mockserver/status" || true)" >&2
    done
    exit 2
fi

if [ "${1:-}" = "calibrate" ]; then
    export CEILING_MODE=calibrate
fi

# NOT `exec`: exec replaces this shell, so the EXIT trap above never runs and the server started
# above is left alive — which then makes the NEXT run fail to bind its ports, for a reason that
# looks nothing like its cause.
java "${FD_FLAG[@]}" -Xms1g -Xmx6g -cp "${CP}" \
    org.mockserver.benchmark.ConnectionCeilingBenchmark 127.0.0.1 "${PORTS}"
