#!/usr/bin/env bash

export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
JAVA_VER=$(java -version 2>&1 | sed 's/java version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
current_directory=${PWD}

if [ $JAVA_VER -eq 16 ]; then
    echo
    echo "------------------------"
    echo "------- JAVA 1.6 -------"
    echo "------------------------"
    echo
elif [ $JAVA_VER -eq 17 ]; then
    echo
    echo "--------------------"
    echo "----- JAVA 1.7 -----"
    echo "--------------------"
    echo
else
    echo
    echo "--------------------"
    echo "-UNKOWN JAVA VERSION-"
    echo "--------------------"
    echo
    exit 1
fi

if [ "${TRAVIS_PULL_REQUEST}" = "false" ] ; then
    rm -rf $current_directory/target/travis
    git clone -b travis `git config --get remote.origin.url` $current_directory/target/travis
    mvn deploy --settings $current_directory/target/travis/settings.xml -Djava.security.egd=file:/dev/./urandom
else
    mvn package -Dmaven-invoker-parallel-threads=2 -Djava.security.egd=file:/dev/./urandom
fi

