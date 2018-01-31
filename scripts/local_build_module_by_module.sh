#!/usr/bin/env bash

set -e

export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
current_directory=${PWD}

function printModule {
    echo
    printf -v str "%-$((${#1} + 8))s" ' '; echo "${str// /=}"
    echo "Module: $1"
    printf -v str "%-$((${#1} + 8))s" ' '; echo "${str// /=}"
    echo
}

function runSubModule {
    printModule "$1"
    cd $1
    mvn install -Dmaven-invoker-parallel-threads=2 -Djava.security.egd=file:/dev/./urandom
    cd $current_directory
}

MODULE_LIST="mockserver-core mockserver-client-java mockserver-integration-testing mockserver-netty mockserver-war mockserver-proxy-war mockserver-maven-plugin mockserver-maven-plugin-integration-tests mockserver-examples"

for module in $MODULE_LIST; do
    (runSubModule $module);
done
