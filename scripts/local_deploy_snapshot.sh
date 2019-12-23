#!/usr/bin/env bash

set -e

export MAVEN_OPTS="$MAVEN_OPTS -Xms2048m -Xmx8192m"
export JAVA_OPTS="$JAVA_OPTS -Xms2048m -Xmx8192m"
export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
echo
java -version
echo
./mvnw -version
echo

export GPG_TTY=$(tty)

./mvnw clean deploy -P release $1 -Djava.security.egd=file:/dev/./urandom
