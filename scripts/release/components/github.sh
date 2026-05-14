#!/usr/bin/env bash
# Create the GitHub Release for the mockserver-X.Y.Z tag.
#
# Dry-run: extract changelog notes + show them, skip `gh release create`.

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

require_cmd docker
require_release_inputs
skip_unless_release_type "github" full,post-maven

log_step "Create GitHub Release for $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

log_info "Extract release notes from changelog.md"
CHANGELOG_EXTRACT=$(sed -n "/## \[$RELEASE_VERSION\]/,/## \[/p" "$REPO_ROOT/changelog.md" | sed '$d')
if [[ -z "$CHANGELOG_EXTRACT" ]]; then
  CHANGELOG_EXTRACT="Release $RELEASE_VERSION"
fi

NOTES_FILE="$REPO_ROOT/.tmp/changelog-extract.md"
mkdir -p "$REPO_ROOT/.tmp"
echo "$CHANGELOG_EXTRACT" > "$NOTES_FILE"
log_info "Notes preview:"
sed 's/^/    /' "$NOTES_FILE"

if is_dry_run; then
  log_dry "skip: gh release create mockserver-$RELEASE_VERSION"
else
  GITHUB_TOKEN=$(load_secret "mockserver-release/github-token" "token")
  log_info "Creating release mockserver-$RELEASE_VERSION"
  in_docker "$GH_IMAGE" \
    -w /build \
    -e "GITHUB_TOKEN=$GITHUB_TOKEN" \
    -- release create "mockserver-$RELEASE_VERSION" \
         --title "MockServer $RELEASE_VERSION" \
         --notes-file ".tmp/changelog-extract.md" \
         --latest
fi

rm -f "$NOTES_FILE"
log_info "GitHub Release complete"
