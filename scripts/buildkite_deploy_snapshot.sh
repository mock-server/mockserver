#!/usr/bin/env bash

set -xeuo pipefail

#export MAVEN_OPTS="$MAVEN_OPTS -Xmx3072m"
#export JAVA_OPTS="$JAVA_OPTS -Xmx3072m"
echo
free -mh
ulimit -u
ulimit -a
ps -eLf | grep 'myuser' | wc -l
ps -eLf | wc -l
sysctl kernel.pid_max
sysctl -w kernel.pid_max=4194303
sysctl kernel.pid_max
echo
java -version
echo
./mvnw -version
echo

if test "$BUILDKITE_BRANCH" = "master"; then
    echo "BRANCH: MASTER"
    ./mvnw -s /etc/maven/settings.xml clean deploy $1 -Djava.security.egd=file:/dev/./urandom
else
    echo "BRANCH: $CURRENT_BRANCH"
    ./mvnw -s /etc/maven/settings.xml clean install $1 -Djava.security.egd=file:/dev/./urandom
fi
