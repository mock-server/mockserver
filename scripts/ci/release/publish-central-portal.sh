#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd curl
require_cmd jq

log_step "Publishing deployment on Central Portal"

AUTH=$(central_portal_auth_header)

if is_ci; then
  DEPLOYMENT_ID="${DEPLOYMENT_ID:-$(buildkite-agent meta-data get deployment-id)}"
else
  : "${DEPLOYMENT_ID:?Set DEPLOYMENT_ID}"
fi

log_info "Publishing deployment: $DEPLOYMENT_ID"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H "Authorization: Bearer $AUTH" \
  "https://central.sonatype.com/api/v1/publisher/deployment/$DEPLOYMENT_ID")

if [[ "$HTTP_CODE" != "204" && "$HTTP_CODE" != "200" ]]; then
  log_error "Publish request failed with HTTP $HTTP_CODE"
  exit 1
fi

log_info "Publish initiated, polling for completion..."

MAX_ATTEMPTS=60
ATTEMPT=0
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  RESPONSE=$(curl -sf -X POST \
    -H "Authorization: Bearer $AUTH" \
    "https://central.sonatype.com/api/v1/publisher/status?id=$DEPLOYMENT_ID")

  STATUS=$(echo "$RESPONSE" | jq -r '.deploymentState')

  case "$STATUS" in
    PUBLISHED)
      log_info "Published to Maven Central"
      exit 0
      ;;
    PUBLISHING)
      log_info "Status: PUBLISHING (attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS)"
      sleep 30
      ;;
    FAILED)
      log_error "Publishing FAILED"
      echo "$RESPONSE" | jq -r '.errors // empty' >&2
      exit 1
      ;;
    *)
      log_error "Unexpected state: $STATUS"
      echo "$RESPONSE" >&2
      exit 1
      ;;
  esac
  ATTEMPT=$((ATTEMPT + 1))
done

log_error "Timed out waiting for publish after $MAX_ATTEMPTS attempts"
exit 1
