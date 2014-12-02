#!/usr/bin/env bash

export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
export JAVA_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
JAVA_VER=$(java -version 2>&1 | sed 's/java version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
current_directory=${PWD}

if [ $JAVA_VER -eq 16 ]; then
    echo
    echo "------------------------"
    echo "------- JAVA 1.6 -------"
    echo "------------------------"
    echo
    rm -rf $current_directory/target/travis
    git clone -b travis `git config --get remote.origin.url` $current_directory/target/travis
    mvn deploy --settings $current_directory/target/travis/settings.xml -DskipJasmineProxyTests=true
fi

if [ $JAVA_VER -eq 17 ]; then
    echo
    echo "--------------------"
    echo "----- JAVA 1.7 -----"
    echo "--------------------"
    echo
    mvn -q install
fi

if [ $JAVA_VER -eq 18 ]; then
    echo
    echo "--------------------"
    echo "----- JAVA 1.8 -----"
    echo "--------------------"
    echo
    mvn -q install
fi
