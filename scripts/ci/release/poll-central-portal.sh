#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd curl
require_cmd jq

log_step "Polling Central Portal for deployment validation"

AUTH=$(central_portal_auth_header)

if is_ci; then
  DEPLOYMENT_ID="${DEPLOYMENT_ID:-$(buildkite-agent meta-data get deployment-id 2>/dev/null || echo '')}"
fi

if [[ -z "${DEPLOYMENT_ID:-}" ]]; then
  log_info "Listing recent deployments to find deployment ID..."
  DEPLOYMENTS=$(curl -sf \
    -H "Authorization: Bearer $AUTH" \
    "https://central.sonatype.com/api/v1/publisher/published?namespace=org.mock-server&name=mockserver-core&version=$RELEASE_VERSION" 2>/dev/null || echo '{}')

  if [[ -z "$DEPLOYMENTS" || "$DEPLOYMENTS" == "{}" ]]; then
    log_info "Could not auto-detect deployment ID."
    read -rp "Enter deployment ID from Central Portal: " DEPLOYMENT_ID
  else
    DEPLOYMENT_ID=$(echo "$DEPLOYMENTS" | jq -r '.deployments[0].deploymentId // empty')
    if [[ -z "$DEPLOYMENT_ID" ]]; then
      read -rp "Enter deployment ID from Central Portal: " DEPLOYMENT_ID
    fi
  fi
fi

if [[ -z "${DEPLOYMENT_ID:-}" ]]; then
  log_error "No deployment ID available"
  exit 1
fi

log_info "Polling deployment: $DEPLOYMENT_ID"

if is_ci; then
  buildkite-agent meta-data set deployment-id "$DEPLOYMENT_ID"
fi

MAX_ATTEMPTS=60
ATTEMPT=0
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  RESPONSE=$(curl -sf -X POST \
    -H "Authorization: Bearer $AUTH" \
    "https://central.sonatype.com/api/v1/publisher/status?id=$DEPLOYMENT_ID")

  STATUS=$(echo "$RESPONSE" | jq -r '.deploymentState')

  case "$STATUS" in
    VALIDATED)
      log_info "Deployment validated successfully"
      exit 0
      ;;
    FAILED)
      log_error "Validation FAILED"
      echo "$RESPONSE" | jq -r '.errors // empty' >&2
      exit 1
      ;;
    PENDING|VALIDATING)
      log_info "Status: $STATUS (attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS)"
      sleep 30
      ;;
    *)
      log_error "Unexpected deployment state: $STATUS"
      echo "$RESPONSE" >&2
      exit 1
      ;;
  esac
  ATTEMPT=$((ATTEMPT + 1))
done

log_error "Timed out waiting for validation after $MAX_ATTEMPTS attempts"
exit 1
