#!/usr/bin/env bash

set -euo pipefail

log_debug() {
    echo "[$(date -u +"%Y-%m-%d %H:%M:%S UTC")] $*"
}

log_debug "=== BUILD START ==="
log_debug "User: $(whoami)"
log_debug "Memory: $(free -h 2>/dev/null | grep Mem || echo 'free command not available')"
log_debug "Disk: $(df -h /build/mockserver 2>/dev/null | tail -1 || echo 'df command not available')"

echo
java -version
echo
./mvnw -version
echo
export MAVEN_OPTS="${MAVEN_OPTS:-} -Xms4096m -Xmx12288m"
export JAVA_OPTS="${JAVA_OPTS:-} -Xms4096m -Xmx12288m"

if test "${BUILDKITE_BRANCH:-}" = "master"; then
    echo "BRANCH: MASTER"
else
    echo "BRANCH: ${CURRENT_BRANCH:-}"
fi

log_debug "Starting Maven build (foreground)..."
set +e
./mvnw -T 1C clean install ${1:-} -Djava.security.egd=file:/dev/./urandom -Dmockserver.testOutput=quiet -DdisableXmlReport=false -DredirectTestOutputToFile=true -Dmockserver.testLogLevel=INFO
MVN_EXIT=$?
log_debug "Maven exited with code=$MVN_EXIT"
set -e

trap - SIGTERM SIGINT

log_debug "=== BUILD END (exit $MVN_EXIT) ==="
exit $MVN_EXIT
