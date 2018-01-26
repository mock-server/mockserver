#!/usr/bin/env bash

export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_HOME=`/usr/libexec/java_home -v 1.7`
echo
java -version
echo
mvn -version
echo

CURRENT_BRANCH="$(git branch | sed -n -e 's/^\* \(.*\)/\1/p')"
#if [[ "$CURRENT_BRANCH" -ne "master" ]]; then
    mvn clean deploy $1 -Djava.security.egd=file:/dev/./urandom
#else
#    mvn clean install $1 -Djava.security.egd=file:/dev/./urandom
#fi
