#!/usr/bin/env bash

export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=1024m -Xmx2048m"
export JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=1024m -Xmx2048m"

function branchName {
    local branch_name=$(git symbolic-ref -q HEAD)
    branch_name=${branch_name##refs/heads/}
    branch_name=${branch_name:-HEAD}
    echo "$branch_name"
}

if [ "$(branchName)" = "master" ]; then
    mvn -q compile --settings settings.xml -Dmaven.test.skip=true -DskipTests=true -DskipITs=true
    mvn deploy --settings settings.xml -Djava.security.egd=file:/dev/./urandom
else
    mvn -q compile -Dmaven.test.skip=true -DskipTests=true -DskipITs=true
    mvn install -Djava.security.egd=file:/dev/./urandom
fi
