#!/usr/bin/env bash

docker pull mockserver/mockserver:grunt
docker run -v $(pwd):/build/mockserver-client-node -w /build/mockserver-client-node -a stdout -a stderr -e BUILDKITE_BRANCH=$BUILDKITE_BRANCH mockserver/mockserver:grunt /build/mockserver-client-node/scripts/buildkite_quick_build.sh
