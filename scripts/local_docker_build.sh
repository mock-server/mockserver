#!/usr/bin/env bash

docker pull jamesdbloom/mockserver:maven
docker run -v `pwd`:/mockserver -w /mockserver -a stdout -a stderr jamesdbloom/mockserver:maven /mockserver/scripts/local_quick_build.sh
