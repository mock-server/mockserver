#!/usr/bin/env bash
# Build self-contained JSON Schema files and publish to S3.
#
# Dry-run: build + validate schemas locally, skip S3 upload.

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

require_cmd jq
require_release_inputs
skip_unless_release_type "schema" full,post-maven

log_step "Publish JSON Schema $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

SCHEMA_DIR="$REPO_ROOT/mockserver/mockserver-core/src/main/resources/org/mockserver/model/schema"
OUTPUT_DIR="$REPO_ROOT/.tmp/schema"
rm -rf "$OUTPUT_DIR"; mkdir -p "$OUTPUT_DIR"

REFERENCE_FILES=(
  expectation requestDefinition openAPIDefinition httpRequest httpResponse
  httpTemplate httpForward httpClassCallback httpObjectCallback
  httpOverrideForwardedRequest httpForwardValidateAction httpError
  httpSseResponse httpWebSocketResponse afterAction times timeToLive
  stringOrJsonSchema body bodyWithContentType delay connectionOptions
  keyToMultiValue keyToValue socketAddress protocol draft-07
)

build_self_contained_schema() {
  local main_file="$1" output_file="$2"
  local schema; schema=$(cat "$SCHEMA_DIR/${main_file}.json")
  for ref in "${REFERENCE_FILES[@]}"; do
    [[ "$ref" == "$main_file" ]] && continue
    local ref_file="$SCHEMA_DIR/${ref}.json"
    [[ -f "$ref_file" ]] || { log_error "Missing ref: $ref_file"; exit 1; }
    local definition; definition=$(cat "$ref_file")
    # NOTE: must use `!= null`, not `// empty`. `empty` in jq produces no
    # output, and `if (empty) then ... else ... end` produces no output,
    # which silently writes a 0-byte schema file.
    schema=$(echo "$schema" | jq --arg name "$ref" --argjson def "$definition" '
      .definitions[$name] = $def |
      if ($def.definitions != null) then
        reduce ($def.definitions | to_entries[]) as $entry (.; .definitions[$entry.key] = $entry.value)
      else . end
    ')
  done
  schema=$(echo "$schema" | jq --arg id "https://www.mock-server.com/schema/${main_file}.json" '
    . + {"$id": $id, "$schema": "http://json-schema.org/draft-07/schema#"}')
  echo "$schema" | jq '.' > "$output_file"
  log_info "  generated $output_file ($(wc -c < "$output_file" | tr -d ' ') bytes)"
}

log_info "Build self-contained expectation schema"
build_self_contained_schema "expectation" "$OUTPUT_DIR/expectation.json"
log_info "Build self-contained expectations (array) schema"
build_self_contained_schema "expectations" "$OUTPUT_DIR/expectations.json"

log_info "Validate"
shopt -s nullglob
for f in "$OUTPUT_DIR"/*.json; do
  jq empty "$f" >/dev/null
  def_count=$(jq '.definitions | length' "$f")
  log_info "  $(basename "$f"): $def_count definitions"
done

if is_dry_run; then
  log_dry "skip: aws s3 sync of schemas"
  log_info "Built: $OUTPUT_DIR/"
else
  [[ -n "${WEBSITE_BUCKET:-}" ]] || { log_error "WEBSITE_BUCKET not set"; exit 1; }
  assume_website_role
  aws s3 sync "$OUTPUT_DIR/" "s3://$WEBSITE_BUCKET/schema/" \
    --content-type "application/schema+json" \
    --cache-control "public, max-age=86400"
fi

log_info "Schema publish complete"
