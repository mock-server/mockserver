#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd curl

log_step "Updating SwaggerHub API spec for $RELEASE_VERSION"

cd "$REPO_ROOT"

MAJOR="${RELEASE_VERSION%%.*}"
MINOR_REST="${RELEASE_VERSION#*.}"
MINOR="${MINOR_REST%%.*}"
API_VERSION="${MAJOR}.${MINOR}.x"

SPEC_FILE="mockserver/mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml"

if [[ ! -f "$SPEC_FILE" ]]; then
  log_error "OpenAPI spec not found: $SPEC_FILE"
  exit 1
fi

SWAGGERHUB_KEY=$(load_secret "mockserver-release/swaggerhub" "api_key")

log_info "Uploading spec version $API_VERSION"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "https://api.swaggerhub.com/apis/jamesdbloom/mock-server-openapi?version=$API_VERSION" \
  -H "Authorization: $SWAGGERHUB_KEY" \
  -H "Content-Type: application/yaml" \
  --data-binary "@$SPEC_FILE")

if [[ "$HTTP_CODE" != "200" && "$HTTP_CODE" != "201" ]]; then
  log_error "SwaggerHub upload failed with HTTP $HTTP_CODE"
  exit 1
fi

log_info "Setting version as published"
curl -s -X PUT \
  "https://api.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/$API_VERSION/settings/lifecycle" \
  -H "Authorization: $SWAGGERHUB_KEY" \
  -H "Content-Type: application/json" \
  -d '{"published": true}'

log_info "SwaggerHub updated to $API_VERSION"
