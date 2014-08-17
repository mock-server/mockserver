#!/usr/bin/env bash

export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx1024m'

# java 1.7 build
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.7.0_55.jdk/Contents/Home
echo
echo "-------------------------"
echo "----- JAVA 1.7 (55) -----"
echo "-------------------------"
echo
if [ -z "$1" ]
then
    echo "not running Java 1.7 due to unknown socket issue with HTTPS CONNECT requests"
    # mvn clean install -Pit
fi

# java 1.8 build
if [ $? -eq 0 ]; then
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_05.jdk/Contents/Home
    echo
    echo "-------------------------"
    echo "----- JAVA 1.8 (05) -----"
    echo "-------------------------"
    echo
    if [ -z "$1" ]
    then
        mvn clean install
    fi
fi

# java 1.6 build
if [ $? -eq 0 ]; then
    export JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
    echo
    echo "-------------------------"
    echo "------- JAVA 1.6  -------"
    echo "-------------------------"
    echo
    if [ -z "$1" ]
    then
        mvn clean install
    else
        # only release if both java 7 and 6 are running correctly
        mvn clean $1
    fi
fi