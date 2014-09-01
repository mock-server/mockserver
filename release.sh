#!/usr/bin/env bash

# java 1.6 build
export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx1024m'
export JAVA_OPTS='-XX:MaxPermSize=1024m -Xmx1024m'
if [ $? -eq 0 ]; then
    export JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
    echo
    echo "-------------------------"
    echo "------- JAVA 1.6  -------"
    echo "-------------------------"
    echo
    mvn release:clean && mvn release:prepare && mvn release:perform
fi