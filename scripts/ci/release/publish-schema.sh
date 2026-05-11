#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd jq
require_cmd aws

log_step "Publishing JSON Schema for $RELEASE_VERSION"

if [[ -z "$WEBSITE_BUCKET" ]]; then
  log_error "WEBSITE_BUCKET not set — cannot publish schemas"
  exit 1
fi

cd "$REPO_ROOT"

SCHEMA_DIR="mockserver/mockserver-core/src/main/resources/org/mockserver/model/schema"
OUTPUT_DIR="$REPO_ROOT/.tmp/schema"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

REFERENCE_FILES=(
  expectation
  requestDefinition
  openAPIDefinition
  httpRequest
  httpResponse
  httpTemplate
  httpForward
  httpClassCallback
  httpObjectCallback
  httpOverrideForwardedRequest
  httpForwardValidateAction
  httpError
  httpSseResponse
  httpWebSocketResponse
  afterAction
  times
  timeToLive
  stringOrJsonSchema
  body
  bodyWithContentType
  delay
  connectionOptions
  keyToMultiValue
  keyToValue
  socketAddress
  protocol
  draft-07
)

build_self_contained_schema() {
  local main_file="$1" output_file="$2"

  local schema
  schema=$(cat "$SCHEMA_DIR/${main_file}.json")

  for ref in "${REFERENCE_FILES[@]}"; do
    [[ "$ref" == "$main_file" ]] && continue
    local ref_file="$SCHEMA_DIR/${ref}.json"
    if [[ ! -f "$ref_file" ]]; then
      log_error "Required schema reference missing: $ref_file"
      exit 1
    fi
    local definition
    definition=$(cat "$ref_file")
    schema=$(echo "$schema" | jq --arg name "$ref" --argjson def "$definition" '
      .definitions[$name] = $def |
      if ($def.definitions // empty) then
        reduce ($def.definitions | to_entries[]) as $entry (.; .definitions[$entry.key] = $entry.value)
      else .
      end
    ')
  done

  schema=$(echo "$schema" | jq --arg id "https://www.mock-server.com/schema/${main_file}.json" '
    . + {"$id": $id, "$schema": "http://json-schema.org/draft-07/schema#"}
  ')

  echo "$schema" | jq '.' > "$output_file"
  log_info "Generated: $output_file ($(wc -c < "$output_file" | tr -d ' ') bytes)"
}

log_info "Building self-contained expectation schema"
build_self_contained_schema "expectation" "$OUTPUT_DIR/expectation.json"

log_info "Building self-contained expectations (array) schema"
build_self_contained_schema "expectations" "$OUTPUT_DIR/expectations.json"

log_info "Validating generated schemas"
shopt -s nullglob
for f in "$OUTPUT_DIR"/*.json; do
  if ! jq empty "$f" 2>/dev/null; then
    log_error "Invalid JSON in $f"
    exit 1
  fi
  def_count=$(jq '.definitions | length' "$f")
  log_info "  $(basename "$f"): $def_count definitions"
done

log_info "Assuming website role for S3 upload"
assume_website_role

log_info "Uploading schemas to S3: $WEBSITE_BUCKET/schema/"
aws s3 sync "$OUTPUT_DIR/" "s3://$WEBSITE_BUCKET/schema/" \
  --content-type "application/schema+json" \
  --cache-control "public, max-age=86400"

log_info "JSON schemas published to https://www.mock-server.com/schema/"
log_info "  - https://www.mock-server.com/schema/expectation.json"
log_info "  - https://www.mock-server.com/schema/expectations.json"
