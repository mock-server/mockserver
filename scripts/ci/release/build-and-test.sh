#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd java
require_cmd git

log_step "Building and testing MockServer $RELEASE_VERSION"

cd "$REPO_ROOT/mockserver"

./mvnw -T 1C clean install \
  -Djava.security.egd=file:/dev/./urandom

log_info "Build and tests passed for $RELEASE_VERSION"
