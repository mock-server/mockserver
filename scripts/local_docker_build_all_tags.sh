#!/usr/bin/env bash

COMMIT_LIST=`git log --all -G"jar\-with\-dependencies" --grep "version" --grep "update" --grep "upgrading" --grep "updating" --format=format:'%h' -- 'docker/Dockerfile'`

function runCommand {
    echo
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    echo "Executing command: $1"
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    echo
    if [ "$2" == "PUSH" ]; then
        eval $1
    fi
}

for commit in $COMMIT_LIST; do
	VERSION=`git show $commit:docker/Dockerfile | awk '/mockserver\-netty\-.*\-jar\-with\-dependencies\.jar/{ print $0 }' | grep -o '/mockserver\-netty\-[0-9][0-9\.]*\-jar\-with\-dependencies\.jar' | egrep -o '[0-9][0-9\.]*'`
    echo $commit, $VERSION;
    runCommand "docker build docker" $1
    echo
done
