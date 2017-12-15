#!/usr/bin/env bash

function runCommand {
    echo
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    echo "Executing command: $1"
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    eval $1
    echo
}

COMMIT_LIST=`git log --all --since="Tue Apr 25 19:55:27 2017 +0100"  --grep "prepare release" --format=format:'%h'`

for commit in $COMMIT_LIST; do
	VERSION=`git show $commit:pom.xml | grep -C1 -o '<artifactId>mockserver</artifactId>' | egrep -o '[0-9][0-9\.]*'`
    echo $commit, $VERSION;
    runCommand "git checkout $commit"
	runCommand "mvn javadoc:aggregate -DreportOutputDirectory='/Users/jamesbloom/git/mockserver/javadoc/$VERSION'"
done
