#!/usr/bin/env bash

#function finish {
#  sudo dmesg | grep -E -i -B100 'killed process'
#}
#trap finish ERR

# java 1.6 build
export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
# -agentpath:/Applications/jprofiler8/bin/macos/libjprofilerti.jnilib=port=25000
export JAVA_HOME=`/usr/libexec/java_home -v 1.6`
echo
echo "======================================================================================"
echo "Requires maven version 3.2.5 so that Java 1.6 can be used, i.e. brew install maven@3.2"
echo "======================================================================================"
echo
java -version
echo

mvn clean install $1 -Dmaven-invoker-parallel-threads=4 -Djava.security.egd=file:/dev/./urandom