#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i mockserver/mockserver:maven \
  -m 7g \
  -e "BUILDKITE_BRANCH=${BUILDKITE_BRANCH:-}" \
  -- /build/scripts/buildkite_quick_build.sh
