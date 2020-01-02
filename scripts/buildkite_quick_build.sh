#!/usr/bin/env bash

set -e

export MAVEN_OPTS="$MAVEN_OPTS -Xms6144m -Xmx6144m"
export JAVA_OPTS="$JAVA_OPTS -Xms6144m -Xmx6144m"
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

./mvnw -T 2C clean install $1 -Djava.security.egd=file:/dev/urandom -DskipAssembly=true