#!/usr/bin/env bash
# Buildkite adapter for the CI-agnostic release scripts.
#
# Translates Buildkite meta-data + pipeline inputs into the environment-
# variable contract that scripts/release/* expects, then invokes one of:
#   prepare | finalize | preflight | <component-name>
#
# After the script exits, syncs any values it wrote to
# .tmp/release-outputs.env back into Buildkite meta-data so the next step
# (potentially on a different agent) can read them.
#
# All Buildkite-specific code lives in this file. The release scripts know
# nothing about Buildkite.

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <prepare|finalize|preflight|<component>>" >&2
  exit 2
fi

STAGE="$1"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# ---- Translate Buildkite meta-data into env vars --------------------------
get_meta() { buildkite-agent meta-data get "$1" 2>/dev/null || echo ""; }
set_meta() { buildkite-agent meta-data set "$1" "$2"; }

export RELEASE_VERSION="${RELEASE_VERSION:-$(get_meta release-version)}"
export NEXT_VERSION="${NEXT_VERSION:-$(get_meta next-version)}"
export RELEASE_TYPE="${RELEASE_TYPE:-$(get_meta release-type)}"
export CREATE_VERSIONED_SITE="${CREATE_VERSIONED_SITE:-$(get_meta create-versioned-site)}"
[[ -z "$RELEASE_TYPE" ]] && export RELEASE_TYPE="full"
[[ -z "$CREATE_VERSIONED_SITE" ]] && export CREATE_VERSIONED_SITE="no"

# Buildkite triggers real releases — opt out of the safer default.
export DRY_RUN="${DRY_RUN:-false}"

# Seed cross-step outputs from meta-data so the script sees them as env vars.
WEBSITE_BUCKET_META=$(get_meta release.WEBSITE_BUCKET)
DISTRIBUTION_ID_META=$(get_meta release.DISTRIBUTION_ID)
[[ -n "$WEBSITE_BUCKET_META" ]] && export WEBSITE_BUCKET="$WEBSITE_BUCKET_META"
[[ -n "$DISTRIBUTION_ID_META" ]] && export DISTRIBUTION_ID="$DISTRIBUTION_ID_META"

# ---- Locate the script for this stage -------------------------------------
case "$STAGE" in
  prepare|finalize|preflight)
    SCRIPT="$REPO_ROOT/scripts/release/$STAGE.sh"
    ;;
  *)
    SCRIPT="$REPO_ROOT/scripts/release/components/$STAGE.sh"
    ;;
esac

if [[ ! -x "$SCRIPT" ]]; then
  echo "ERROR: no such release script: $SCRIPT" >&2
  exit 2
fi

# ---- Clear any stale outputs from a prior step ----------------------------
RELEASE_OUTPUTS_FILE="$REPO_ROOT/.tmp/release-outputs.env"
rm -f "$RELEASE_OUTPUTS_FILE"

# ---- Run the script -------------------------------------------------------
set +e
"$SCRIPT" --execute
exit_code=$?
set -e

# ---- Sync outputs back to Buildkite meta-data -----------------------------
if [[ -f "$RELEASE_OUTPUTS_FILE" ]]; then
  echo "--- Syncing $(wc -l < "$RELEASE_OUTPUTS_FILE" | tr -d ' ') output(s) to Buildkite meta-data"
  while IFS='=' read -r key value; do
    [[ -z "$key" ]] && continue
    echo "    release.$key"
    set_meta "release.$key" "$value"
  done < "$RELEASE_OUTPUTS_FILE"
fi

exit "$exit_code"
