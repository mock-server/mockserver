#!/usr/bin/env bash

set -euo pipefail

echo "whoami: $(whoami)"

echo
java -version
echo
./mvnw -version
echo
export MAVEN_OPTS="${MAVEN_OPTS:-} -Xms2048m -Xmx8192m"
export JAVA_OPTS="${JAVA_OPTS:-} -Xms2048m -Xmx8192m"

if test "${BUILDKITE_BRANCH:-}" = "master"; then
    echo "BRANCH: MASTER"
else
    echo "BRANCH: ${CURRENT_BRANCH:-}"
fi

./mvnw clean install ${1:-} -Djava.security.egd=file:/dev/./urandom