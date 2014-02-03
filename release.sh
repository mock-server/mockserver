#!/usr/bin/env bash

# java 1.6 build
export JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
mvn release:clean && mvn release:prepare && mvn release:perform

if [ $? -eq 0 ]; then
    # java 1.7 build
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home
    mvn release:clean && mvn release:prepare && mvn release:perform
fi