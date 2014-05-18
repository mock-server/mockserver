#!/usr/bin/env bash

JAVA_VER=$(java -version 2>&1 | sed 's/java version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx1024m'
export JAVA_OPTS='-XX:MaxPermSize=1024m -Xmx1024m'

if [ $JAVA_VER -eq 16 ]; then
    echo
    echo "-------------------------"
    echo "------- JAVA 1.6  -------"
    echo "-------------------------"
    echo
    git clone -b travis `git config --get remote.origin.url` target/travis
    mvn deploy --settings target/travis/settings.xml
fi

if [ $JAVA_VER -eq 17 ]; then
    echo
    echo "--------------------"
    echo "----- JAVA 1.7 -----"
    echo "--------------------"
    echo
    mvn test
fi

if [ $JAVA_VER -eq 18 ]; then
    echo
    echo "--------------------"
    echo "----- JAVA 1.8 -----"
    echo "--------------------"
    echo
    mvn test
fi
