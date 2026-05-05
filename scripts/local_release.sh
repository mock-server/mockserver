#!/usr/bin/env bash

set -e

export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
# line below no longer appears to be working so using less resilient hard coded location
#export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_201.jdk/Contents/Home
echo
java -version
echo
./mvnw -version
echo

export GPG_TTY=$(tty)

./mvnw release:clean -P release -Drelease.arguments="-Dmaven.test.skip=true -DskipTests=true" && \
./mvnw release:prepare -P release -Drelease.arguments="-Dmaven.test.skip=true -DskipTests=true" && \
./mvnw release:perform -P release -Drelease.arguments="-Dmaven.test.skip=true -DskipTests=true"