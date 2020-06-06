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

# to run from specific test use argument in quotes "ExpectationFileWatcherIntegrationTest" or "ExpectationFileWatcherIntegrationTest#shouldDetectModifiedInitialiserJsonOnAdd"
./mvnw -T 1C -Dtest="none" -Dit.test="$1" clean install -DskipAssembly=true -DfailIfNoTests=false