#!/usr/bin/env bash

export MAVEN_OPTS="$MAVEN_OPTS -Dmockserver.logLevel=WARN -Dmockserver.nioEventLoopThreadCount=2 -XX:MaxPermSize=1024m -Xms3072m -Xmx3072m"
export JAVA_OPTS="$JAVA_OPTS -Dmockserver.logLevel=WARN -Dmockserver.nioEventLoopThreadCount=2 -XX:MaxPermSize=1024m -Xms3072m -Xmx3072m"
export JAVA_HOME=`/usr/libexec/java_home -v 1.7`
echo
java -version
echo
mvn -version
echo

mvn clean install $1 -Djava.security.egd=file:/dev/./urandom
