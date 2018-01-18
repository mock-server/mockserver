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

runCommand "docker build -t jamesdbloom/mockserver:mockserver-$1 ./docker"
echo "proceed?" && read && echo "are you sure?" && read
runCommand "docker push jamesdbloom/mockserver:mockserver-$1"
