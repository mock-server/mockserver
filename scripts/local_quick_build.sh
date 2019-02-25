#!/usr/bin/env bash

export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
echo
java -version
echo
./mvnw -version
echo

# to run from specific module use argument in quotes "-rf mockserver-war"
./mvnw -T 1C clean install -offline $1 -Djava.security.egd=file:/dev/urandom -DskipAssembly=true