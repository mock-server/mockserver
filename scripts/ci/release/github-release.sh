#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd gh

log_step "Creating GitHub Release for $RELEASE_VERSION"

cd "$REPO_ROOT"

GITHUB_TOKEN=$(load_secret "mockserver-release/github-token" "token")
export GITHUB_TOKEN

CHANGELOG_EXTRACT=""
if [[ -f "changelog.md" ]]; then
  CHANGELOG_EXTRACT=$(sed -n "/## \[$RELEASE_VERSION\]/,/## \[/p" changelog.md | head -n -1)
fi

if [[ -z "$CHANGELOG_EXTRACT" ]]; then
  CHANGELOG_EXTRACT="Release $RELEASE_VERSION"
fi

NOTES_FILE="$REPO_ROOT/.tmp/changelog-extract.md"
mkdir -p "$REPO_ROOT/.tmp"
echo "$CHANGELOG_EXTRACT" > "$NOTES_FILE"

log_info "Creating release mockserver-$RELEASE_VERSION"
gh release create "mockserver-$RELEASE_VERSION" \
  --title "MockServer $RELEASE_VERSION" \
  --notes-file "$NOTES_FILE" \
  --latest

rm -f "$NOTES_FILE"

log_info "GitHub Release created: mockserver-$RELEASE_VERSION"
