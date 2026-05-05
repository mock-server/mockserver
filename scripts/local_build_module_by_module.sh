#!/usr/bin/env bash

set -e

export MAVEN_OPTS="$MAVEN_OPTS -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -Xmx2048m"

cd mockserver

function printModule {
    echo
    printf -v str "%-$((${#1} + 8))s" ' '; echo "${str// /=}"
    echo "Module: $1"
    printf -v str "%-$((${#1} + 8))s" ' '; echo "${str// /=}"
    echo
}

function runSubModule {
    printModule "$1"
    ./mvnw install -pl "$1" -Dmaven-invoker-parallel-threads=2 -Djava.security.egd=file:/dev/./urandom
}

MODULE_LIST="mockserver-testing mockserver-core mockserver-client-java mockserver-integration-testing mockserver-netty mockserver-war mockserver-proxy-war mockserver-junit-rule mockserver-junit-jupiter mockserver-spring-test-listener mockserver-examples"

for module in $MODULE_LIST; do
    (runSubModule "$module");
done

cd ..

if [[ -d mockserver-maven-plugin ]]; then
    printModule "mockserver-maven-plugin"
    (cd mockserver-maven-plugin && ./mvnw install -Dmaven-invoker-parallel-threads=2 -Djava.security.egd=file:/dev/./urandom)
fi
