#!/usr/bin/env bash

set -euo pipefail

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

./mvnw -T 1C clean install ${1:-} -Djava.security.egd=file:/dev/./urandom -Dmockserver.testOutput=quiet -DdisableXmlReport=false -DredirectTestOutputToFile=true