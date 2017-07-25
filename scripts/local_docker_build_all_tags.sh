#!/usr/bin/env bash

COMMIT_LIST=`git log --all -G"jar\-with\-dependencies" --grep "version" --grep "update" --grep "upgrading" --grep "updating" --format=format:'%h' -- 'docker/Dockerfile'`

function runCommand {
    echo
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    echo "Executing command: $1"
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    eval $1
    echo
}

for commit in $COMMIT_LIST; do
	VERSION=`git show $commit:docker/Dockerfile | awk '/mockserver\-netty\-.*\-jar\-with\-dependencies\.jar/{ print $0 }' | grep -o '/mockserver\-netty\-[0-9][0-9\.]*\-jar\-with\-dependencies\.jar' | egrep -o '[0-9][0-9\.]*'`
    echo $commit, $VERSION;
    cp docker/Dockerfile ../Dockerfile_mockserver
    git checkout "$commit"
    cp ../Dockerfile_mockserver docker/Dockerfile
#    sed -i '' -e 's/FROM java/FROM openjdk\:alpine/g' docker/Dockerfile

    runCommand "docker build -t jamesdbloom/mockserver:mockserver-$VERSION ./docker"
#    runCommand "docker push jamesdbloom/mockserver:mockserver-$VERSION"
    git reset HEAD --hard
    echo
done
