#!/usr/bin/env bash

set -euo pipefail

MVN_PID=""

log_debug() {
    echo "[$(date -u +"%Y-%m-%d %H:%M:%S UTC")] $*"
}

cleanup() {
    log_debug "cleanup() called - Signal received"
    log_debug "MVN_PID=$MVN_PID"
    
    if [ -n "$MVN_PID" ] && kill -0 "$MVN_PID" 2>/dev/null; then
        log_debug "Maven process $MVN_PID is running - forwarding SIGTERM"
        kill -TERM "$MVN_PID" 2>/dev/null || true
        
        log_debug "Waiting up to 30s for Maven to flush Surefire reports gracefully..."
        local i=0
        while [ $i -lt 30 ] && kill -0 "$MVN_PID" 2>/dev/null; do
            sleep 1
            i=$((i + 1))
        done
        
        if kill -0 "$MVN_PID" 2>/dev/null; then
            log_debug "Maven did not exit within 30s - sending SIGKILL"
            kill -KILL "$MVN_PID" 2>/dev/null || true
            wait "$MVN_PID" 2>/dev/null || true
        else
            log_debug "Maven exited gracefully"
        fi
    else
        log_debug "Maven not running (MVN_PID='$MVN_PID' or process already dead)"
    fi
}

trap cleanup SIGTERM SIGINT

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

log_debug "Starting Maven build..."
set +e
./mvnw -T 1C clean install ${1:-} -Djava.security.egd=file:/dev/./urandom -Dmockserver.testOutput=quiet -DdisableXmlReport=false -DredirectTestOutputToFile=true -Dmockserver.testLogLevel=INFO &
MVN_PID=$!
log_debug "Maven started with PID=$MVN_PID"
wait $MVN_PID
MVN_EXIT=$?
log_debug "Maven exited with code=$MVN_EXIT"
set -e

trap - SIGTERM SIGINT

log_debug "=== BUILD END (exit $MVN_EXIT) ==="
exit $MVN_EXIT
