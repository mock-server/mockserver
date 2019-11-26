#!/usr/bin/env bash

COMMIT_LIST="mockserver-5.0.0 mockserver-5.0.1 mockserver-5.1.0 mockserver-5.1.1 mockserver-5.2.0 mockserver-5.2.1 mockserver-5.2.2 mockserver-5.2.3 mockserver-5.3.0 mockserver-5.4.1 mockserver-5.5.0 mockserver-5.5.1 mockserver-5.5.2 mockserver-5.5.3 mockserver-5.5.4 mockserver-5.6.0 mockserver-5.6.1 mockserver-5.7.0 mockserver-5.7.1 mockserver-5.7.2"

function runCommand {
    echo
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    echo "Executing command: $1"
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    eval $1
    echo
}

cd mockserver
for commit in $COMMIT_LIST; do
	VERSION=`echo $commit | egrep -o '([0-9]+\.){2}[0-9]+'`
    echo $commit, $VERSION;
    git checkout "$commit"
    sed -E "s|([0-9]+\.){2}[0-9]+|${VERSION}|g" docker/Dockerfile > docker/Dockerfile_updated
    sed s/--max-redirect=5/--max-redirect=15/g docker/Dockerfile_updated > docker/Dockerfile_updated_two    
    sed s/--max-redirect=1/--max-redirect=15/g docker/Dockerfile_updated > docker/Dockerfile_updated_three    
    cp docker/Dockerfile_updated_three docker/Dockerfile
    runCommand "docker build -t mockserver/mockserver:mockserver-$VERSION ./docker"
    runCommand "docker push mockserver/mockserver:mockserver-$VERSION"
    git reset HEAD --hard
    git checkout master
    git add -A
    git reset HEAD --hard
    echo
done
