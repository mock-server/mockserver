#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

docker pull mockserver/mockserver:maven
docker run --rm \
  -v "$REPO_ROOT:/build" \
  -w /build/mockserver-node \
  -a stdout -a stderr \
  mockserver/mockserver:maven bash -c \
  '/build/.buildkite/scripts/install-nodejs.sh && /build/mockserver-node/scripts/buildkite_quick_build.sh'
