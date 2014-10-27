#!/usr/bin/env bash

# java 1.6 build
export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
export JAVA_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
export JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
echo
echo "-------------------------"
echo "------- JAVA 1.6  -------"
echo "-------------------------"
echo
mvn clean deploy $1