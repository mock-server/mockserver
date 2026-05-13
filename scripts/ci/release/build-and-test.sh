#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker

log_step "Building and testing MockServer $RELEASE_VERSION"

# Run Maven in a container so this script works identically on a developer
# laptop and on a bare CI agent. The m2 cache is persisted in a named volume
# so the second mvn invocation in the same release reuses downloads.
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -w /build/mockserver \
  -v mockserver-m2-cache:/root/.m2 \
  -- mvn -T 1C clean install \
       -Djava.security.egd=file:/dev/./urandom

log_info "Build and tests passed for $RELEASE_VERSION"
