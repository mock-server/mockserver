#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"

docker run --net=host -v /var/run/docker.sock:/var/run/docker.sock -v ~/.m2:/root/.m2 -v ~/.gradle:/root/.gradle -v "${SCRIPT_DIR}"/..:/build/mockserver -w /build/mockserver -e BUILDKITE_BRANCH=master mockserver/mockserver:maven /build/mockserver/scripts/buildkite_deploy_snapshot.sh