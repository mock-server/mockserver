#!/usr/bin/env bash

docker pull jamesdbloom/mockserver:maven
docker run -i -t --memory=4096m --oom-kill-disable -v `pwd`:/mockserver -w /mockserver -a stdout -a stderr jamesdbloom/mockserver:maven /bin/bash
