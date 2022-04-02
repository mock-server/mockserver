#!/usr/bin/env bash

set -e

export MAVEN_OPTS="$MAVEN_OPTS -Xms8192m -Xmx8192m"
export JAVA_OPTS="$JAVA_OPTS -Xms8192m -Xmx8192m"
echo
java -version
echo
./mvnw -version
echo

if test "$BUILDKITE_BRANCH" = "master"; then
    echo "BRANCH: MASTER"
else
    echo "BRANCH: $CURRENT_BRANCH"
fi

echo "whoami: "
whoami

./mvnw clean install $1 -Djava.security.egd=file:/dev/urandom -DskipAssembly=true