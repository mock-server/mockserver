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
echo MAVEN_OPTS=$MAVEN_OPTS
echo
echo JAVA_OPTS=$JAVA_OPTS
echo
echo JAVA_HOME=$JAVA_HOME
echo
echo
/usr/local/Cellar/maven32/3.2.5/bin/mvn clean deploy $1 -Djava.security.egd=file:/dev/./urandom