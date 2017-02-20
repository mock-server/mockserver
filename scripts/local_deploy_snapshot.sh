#!/usr/bin/env bash

# java 1.6 build
export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
export JAVA_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
export JAVA_HOME=`/usr/libexec/java_home -v 1.6`

mvn clean deploy $1 -Djava.security.egd=file:/dev/./urandom