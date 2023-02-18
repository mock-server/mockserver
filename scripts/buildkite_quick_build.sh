#!/usr/bin/env bash

set -euo pipefail

#export MAVEN_OPTS="$MAVEN_OPTS -Xms2048m -Xmx8192m"
#export JAVA_OPTS="$JAVA_OPTS -Xms2048m -Xmx8192m"
#echo
#free -mh
#ulimit -u
#ulimit -a
#ps -eLf | grep 'myuser' | wc -l
#ps -eLf | wc -l
#sysctl kernel.pid_max
#sysctl -w kernel.pid_max=4194303
#sysctl kernel.pid_max
#echo
#java -version
#echo
#./mvnw -version
#echo
#
#if test "$BUILDKITE_BRANCH" = "master"; then
#    echo "BRANCH: MASTER"
#else
#    echo "BRANCH: $CURRENT_BRANCH"
#fi
#
#echo "whoami: "
#whoami
#
#./mvnw clean install $1