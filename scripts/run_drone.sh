#!/usr/bin/env bash

docker stop mockserver
docker rm mockserver
docker run -t -i --name mockserver -v /vagrant/git/mockserver:/mocksever drone_io/mockserver:built_stopped /bin/bash

# export JAVA_OPTS="-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"