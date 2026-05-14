#!/usr/bin/env bash
# Update the MockServer OpenAPI spec on SwaggerHub.
#
# Dry-run: validate spec exists, skip POST to SwaggerHub.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/_lib.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --execute) DRY_RUN=false; shift ;;
    -h|--help) echo "Usage: $0 [--dry-run|--execute]"; exit 0 ;;
    *) log_error "Unknown arg: $1"; exit 2 ;;
  esac
done

require_cmd curl
require_cmd jq
require_release_inputs
skip_unless_release_type "swaggerhub" full,post-maven

log_step "Update SwaggerHub $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

SPEC="$REPO_ROOT/mockserver/mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml"
[[ -f "$SPEC" ]] || { log_error "OpenAPI spec not found: $SPEC"; exit 1; }
log_info "Spec: $SPEC"
log_info "Spec version: $(grep -E '^  version:' "$SPEC" | head -1 || echo unknown)"

if is_dry_run; then
  log_dry "skip: POST spec to SwaggerHub"
else
  API_KEY=$(load_secret "mockserver-release/swaggerhub" "api_key")
  log_info "Uploading spec to SwaggerHub"
  curl -fsS -X POST \
    -H "Authorization: $API_KEY" \
    -H "Content-Type: application/yaml" \
    --data-binary "@$SPEC" \
    "https://api.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/$RELEASE_VERSION?isPrivate=false&oas=3.0.0"
fi

log_info "SwaggerHub update complete"
