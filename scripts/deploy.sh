#!/usr/bin/env bash

# java 1.6 build
export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
export JAVA_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
export JAVA_HOME=`/usr/libexec/java_home -v 1.6`
echo
echo "-------------------------"
echo "------- JAVA 1.6  -------"
echo "-------------------------"
echo
/usr/local/Cellar/maven/3.2.3/bin/mvn clean deploy $1