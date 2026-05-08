#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd curl

log_step "Cleaning up failed release $RELEASE_VERSION"

cd "$REPO_ROOT"

AUTH=$(central_portal_auth_header)
DEPLOYMENT_ID="${1:-}"

if [[ -z "$DEPLOYMENT_ID" ]] && is_ci; then
  DEPLOYMENT_ID=$(buildkite-agent meta-data get deployment-id 2>/dev/null || echo '')
fi

if [[ -n "$DEPLOYMENT_ID" ]]; then
  log_info "Dropping Central Portal deployment $DEPLOYMENT_ID"
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    -H "Authorization: Bearer $AUTH" \
    "https://central.sonatype.com/api/v1/publisher/deployment/$DEPLOYMENT_ID")
  log_info "Central Portal response: HTTP $HTTP_CODE"
fi

PRE_RELEASE_COMMIT="${PRE_RELEASE_COMMIT:-}"
if [[ -z "$PRE_RELEASE_COMMIT" ]]; then
  read -rp "Enter pre-release commit hash to reset to: " PRE_RELEASE_COMMIT
fi

if [[ -n "$PRE_RELEASE_COMMIT" ]]; then
  confirm "Reset to $PRE_RELEASE_COMMIT and force push?"
  log_info "Resetting to $PRE_RELEASE_COMMIT"
  git reset --hard "$PRE_RELEASE_COMMIT"
  git push --force origin master
fi

log_info "Deleting tag mockserver-$RELEASE_VERSION"
git tag -d "mockserver-$RELEASE_VERSION" 2>/dev/null || true
git push origin ":refs/tags/mockserver-$RELEASE_VERSION" 2>/dev/null || true

log_info "Cleanup complete"
