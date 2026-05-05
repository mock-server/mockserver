#!/usr/bin/env bash

host="${1:-localhost}"  # use host.docker.internal for Docker for Desktop

$(dirname $0)/runMockServer.sh

sleep 10

$(dirname $0)/runLocust.sh "${host}"
