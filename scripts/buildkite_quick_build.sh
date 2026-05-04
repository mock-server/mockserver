#!/usr/bin/env bash

set -euo pipefail

cleanup() {
    echo "Build cancelled or interrupted (caught signal)"
    exit 143
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
./mvnw -T 1C clean install ${1:-} -Djava.security.egd=file:/dev/./urandom -Dmockserver.testOutput=quiet -DdisableXmlReport=false -DredirectTestOutputToFile=true -Dmockserver.testLogLevel=INFO
MVN_EXIT=$?
set -e

trap - SIGTERM SIGINT

exit $MVN_EXIT
