#!/usr/bin/env bash

# java 1.6 build
export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
export JAVA_OPTS='-XX:MaxPermSize=1024m -Xmx2048m'
# -agentpath:/Applications/jprofiler8/bin/macos/libjprofilerti.jnilib=port=25000
export JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
echo
echo "-------------------------"
echo "------- JAVA 1.6  -------"
echo "-------------------------"
echo
mvn clean install $1