#!/usr/bin/env bash

set -e

export MAVEN_OPTS="$MAVEN_OPTS -Xms8192m -Xmx8192m"
export JAVA_OPTS="$JAVA_OPTS -Xms8192m -Xmx8192m"
export JAVA_HOME=`/usr/libexec/java_home -v 12`
echo
java -version
echo
./mvnw -version
echo

# to run from specific module use argument in quotes "-rf mockserver-war"
./mvnw -T 2C clean install -offline $1 -Djava.security.egd=file:/dev/urandom -DskipAssembly=true