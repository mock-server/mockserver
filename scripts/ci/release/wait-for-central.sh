#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd curl

log_step "Waiting for Maven Central sync of $RELEASE_VERSION"

ARTIFACT_URL="https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/$RELEASE_VERSION/mockserver-netty-$RELEASE_VERSION.jar"
MAX_ATTEMPTS=120
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$ARTIFACT_URL")
  if [ "$HTTP_CODE" = "200" ]; then
    log_info "Release $RELEASE_VERSION available on Maven Central"
    log_info "  $ARTIFACT_URL"
    exit 0
  fi
  log_info "Waiting for Central sync (attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS, HTTP $HTTP_CODE)"
  sleep 60
  ATTEMPT=$((ATTEMPT + 1))
done

log_error "Timed out waiting for Central sync after $MAX_ATTEMPTS minutes"
exit 1
