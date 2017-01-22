#!/usr/bin/env bash

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
    if [ $? -eq 0 ]; then
	    #/usr/local/Cellar/maven/3.2.3/bin/mvn release:clean -Drelease.arguments="-DnonReleaseBuild=false -Dmaven.test.skip=true -DskipTests=true" && \
	    #/usr/local/Cellar/maven/3.2.3/bin/mvn release:prepare -Drelease.arguments="-DnonReleaseBuild=false -Dmaven.test.skip=true -DskipTests=true" && \
	    #/usr/local/Cellar/maven/3.2.3/bin/mvn release:perform -Drelease.arguments="-DnonReleaseBuild=false -Dmaven.test.skip=true -DskipTests=true"
	fi
	cd $current_directory
}

MODULE_LIST="mockserver-maven-plugin mockserver-maven-plugin-integration-tests"

for module in $MODULE_LIST; do
    (runSubModule $module);
done
