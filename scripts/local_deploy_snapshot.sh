#!/usr/bin/env bash

set -e

export MAVEN_OPTS="$MAVEN_OPTS -Xms8192m -Xmx8192m"
export JAVA_OPTS="$JAVA_OPTS -Xms8192m -Xmx8192m"
# line below no longer appears to be working so using less resilient hard coded location
#export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
export JAVA_HOME=`/Library/Java/JavaVirtualMachines/jdk1.8.0_201.jdk/Contents/Home`
echo
java -version
echo
./mvnw -version
echo

export GPG_TTY=$(tty)

./mvnw clean deploy -P release $1 -Djava.security.egd=file:/dev/./urandom