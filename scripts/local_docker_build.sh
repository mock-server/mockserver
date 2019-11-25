#!/usr/bin/env bash

docker pull jamesdbloom/mockserver:maven
docker run --memory=4096m --oom-kill-disable -v `pwd`:/mockserver -w /mockserver -a stdout -a stderr jamesdbloom/mockserver:maven /mockserver/scripts/buildkite_quick_build.sh
