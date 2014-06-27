#!/usr/bin/env bash

# java 1.6 build
export JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
echo
echo "-------------------------"
echo "------- JAVA 1.6  -------"
echo "-------------------------"
echo
mvn -T2C clean install