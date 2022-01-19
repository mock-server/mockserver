#!/usr/bin/env bash

docker run -v $(pwd):/build/mockserver -w /build/mockserver -a stdout -a stderr -e BUILDKITE_BRANCH=$BUILDKITE_BRANCH mockserver/mockserver:maven /build/mockserver/scripts/buildkite_quick_build.sh
