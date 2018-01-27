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
if test "$BUILDKITE_BRANCH" = "master" || "$CURRENT_BRANCH" = "master"; then
    echo "BRANCH: MASTER"
    mvn clean deploy $1 -Djava.security.egd=file:/dev/./urandom
else
    echo "BRANCH: $CURRENT_BRANCH"
    mvn clean install $1 -Djava.security.egd=file:/dev/./urandom
fi
