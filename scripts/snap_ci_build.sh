#!/usr/bin/env bash

export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"

mvn -q compile -Dmaven.test.skip=true -DskipTests=true -DskipITs=true
mvn deploy --settings settings.xml -Djava.security.egd=file:/dev/./urandom
