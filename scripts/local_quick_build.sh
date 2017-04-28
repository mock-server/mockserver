#!/usr/bin/env bash

function finish {
  dmesg | grep -E -i -B100 'killed process'
}
trap finish ERR

# java 1.6 build
export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
# -agentpath:/Applications/jprofiler8/bin/macos/libjprofilerti.jnilib=port=25000
export JAVA_HOME=`/usr/libexec/java_home -v 1.6`

mvn clean install $1 -Dmaven-invoker-parallel-threads=4 -Djava.security.egd=file:/dev/./urandom