#!/usr/bin/env bash

set -e

export MAVEN_OPTS="$MAVEN_OPTS -Xms2048m -Xmx8192m"
export JAVA_OPTS="$JAVA_OPTS -Xms2048m -Xmx8192m"
export JAVA_HOME=`/usr/libexec/java_home -v 12`
echo
java -version
echo
./mvnw -version
echo

# to run from specific module use argument in quotes "mockserver-netty"
./mvnw -T 1C clean install -DskipAssembly=true -pl="$1"