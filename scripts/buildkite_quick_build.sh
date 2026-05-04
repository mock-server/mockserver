#!/usr/bin/env bash

set -euo pipefail

MVN_PID=""

cleanup() {
    if [ -n "$MVN_PID" ] && kill -0 "$MVN_PID" 2>/dev/null; then
        echo "Build cancelled or interrupted - forwarding SIGTERM to Maven (PID $MVN_PID)"
        kill -TERM "$MVN_PID" 2>/dev/null || true
        
        echo "Waiting up to 30s for Maven to flush Surefire reports gracefully..."
        local i=0
        while [ $i -lt 30 ] && kill -0 "$MVN_PID" 2>/dev/null; do
            sleep 1
            i=$((i + 1))
        done
        
        if kill -0 "$MVN_PID" 2>/dev/null; then
            echo "Maven did not exit within 30s - sending SIGKILL"
            kill -KILL "$MVN_PID" 2>/dev/null || true
            wait "$MVN_PID" 2>/dev/null || true
        fi
    fi
}

trap cleanup SIGTERM SIGINT

echo "whoami: $(whoami)"

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

set +e
./mvnw -T 1C clean install ${1:-} -Djava.security.egd=file:/dev/./urandom -Dmockserver.testOutput=quiet -DdisableXmlReport=false -DredirectTestOutputToFile=true -Dmockserver.testLogLevel=INFO &
MVN_PID=$!
wait $MVN_PID
MVN_EXIT=$?
set -e

trap - SIGTERM SIGINT

exit $MVN_EXIT
