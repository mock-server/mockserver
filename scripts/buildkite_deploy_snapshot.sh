#!/usr/bin/env bash

export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
echo
java -version
echo
mvn -version
echo

if test "$CURRENT_BRANCH" = "master"; then
    echo "BRANCH: MASTER"
    mvn clean deploy $1 -Djava.security.egd=file:/dev/./urandom
else
    echo "BRANCH: $CURRENT_BRANCH"
    mvn clean install $1 -Djava.security.egd=file:/dev/./urandom
fi
