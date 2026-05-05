#!/usr/bin/env bash

host="${1:-localhost}"  # use host.docker.internal for Docker for Desktop

docker stop locust || true
docker run --volume ${PWD}/docker_performance_test.sh:/docker_performance_test.sh --volume ${PWD}/locustfile.py:/locustfile.py --env MOCKSERVER_HOST="${host}":1080 --rm --name locust -p 8089:8089 --entrypoint /docker_performance_test.sh mockserver/mockserver:performance
