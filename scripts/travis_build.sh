#!/usr/bin/env bash

export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
JAVA_VER=$(java -version 2>&1 | sed 's/java version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
current_directory=${PWD}

if [ $JAVA_VER -eq 16 ]; then
    echo
    echo "----------------------"
    echo "------ JAVA 1.6 ------"
    echo "----------------------"
    echo
elif [ $JAVA_VER -eq 17 ]; then
    echo
    echo "----------------------"
    echo "------ JAVA 1.7 ------"
    echo "----------------------"
    echo
else
    echo
    echo "----------------------"
    echo "-UNKNOWN JAVA VERSION-"
    echo "----------------------"
    echo
    exit 1
fi

rm -rf $current_directory/target/travis
git clone -b travis `git config --get remote.origin.url` $current_directory/target/travis

function runSubModule {
    echo "Running Module: $1"
    cd $1
    if [ "${TRAVIS_PULL_REQUEST}" = "false" ] ; then
        mvn deploy --settings $current_directory/target/travis/settings.xml -Djava.security.egd=file:/dev/./urandom
    else
        mvn package -Dmaven-invoker-parallel-threads=2 -Djava.security.egd=file:/dev/./urandom
    fi
}

MODULE_LIST="mockserver-logging mockserver-core mockserver-client-java mockserver-integration-testing mockserver-netty mockserver-maven-plugin mockserver-client-ruby mockserver-war mockserver-proxy-war mockserver-maven-plugin-integration-tests mockserver-client-javascript mockserver-examples"

for module in $MODULE_LIST; do
    (runSubModule $module);
done



