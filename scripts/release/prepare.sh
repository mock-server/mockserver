#!/usr/bin/env bash
# Prepare a release: validate inputs, bump pom.xml versions, commit, tag, push.
#
# This is the first script the orchestrator runs. After this completes, every
# subsequent component script syncs to origin/master to pick up the bump.
#
# Inputs (env vars): see docs/operations/release-principles.md §6.
# Args: --dry-run

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --execute) DRY_RUN=false; shift ;;
    -h|--help) echo "Usage: $0 [--dry-run|--execute]"; exit 0 ;;
    *) log_error "Unknown arg: $1"; exit 2 ;;
  esac
done

require_cmd git
require_cmd python3
require_release_inputs

log_step "Prepare release $RELEASE_VERSION (dry-run=$DRY_RUN)"

# Validation
if [[ "$RELEASE_VERSION" == "$OLD_VERSION" ]]; then
  log_error "RELEASE_VERSION must differ from OLD_VERSION (latest tag: $OLD_VERSION)"
  exit 1
fi
if [[ ! "$CURRENT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
  log_error "Current pom version must be X.Y.Z-SNAPSHOT, got: $CURRENT_VERSION"
  exit 1
fi
if [[ "$CREATE_VERSIONED_SITE" == "yes" && "${RELEASE_VERSION%.*}" == "${OLD_VERSION%.*}" ]]; then
  log_error "CREATE_VERSIONED_SITE=yes only valid for major or minor releases"
  exit 1
fi
if git -C "$REPO_ROOT" rev-parse "mockserver-$RELEASE_VERSION" >/dev/null 2>&1; then
  log_error "Tag mockserver-$RELEASE_VERSION already exists"
  exit 1
fi
if grep -Eq "^## \[$RELEASE_VERSION\]" "$REPO_ROOT/changelog.md"; then
  log_error "changelog.md already contains a section for $RELEASE_VERSION"
  exit 1
fi
UNRELEASED_SECTION=$(sed -n '/^## \[Unreleased\]/,/^## \[/p' "$REPO_ROOT/changelog.md" | sed '1d;$d')
if ! printf '%s\n' "$UNRELEASED_SECTION" | grep -Eq '^- '; then
  log_error "changelog.md has no bullets under Unreleased"
  exit 1
fi

log_info "Inputs OK:"
log_info "  Release:  $RELEASE_VERSION"
log_info "  Next:     $NEXT_VERSION"
log_info "  Previous: $OLD_VERSION"
log_info "  Current:  $CURRENT_VERSION"
log_info "  Type:     $RELEASE_TYPE"
log_info "  Versioned site: $CREATE_VERSIONED_SITE"

# Skip the pom + git work for partial reruns that don't change versions.
case "$RELEASE_TYPE" in
  docker-only|post-maven)
    log_info "Skipping pom bump + tag for RELEASE_TYPE=$RELEASE_TYPE"
    exit 0 ;;
esac

log_info "Updating pom.xml versions from $CURRENT_VERSION to $RELEASE_VERSION"
if is_dry_run; then
  log_dry "would: update 12 module pom.xml files"
else
  update_pom_versions "$REPO_ROOT/mockserver" "$CURRENT_VERSION" "$RELEASE_VERSION"
fi

git_commit_and_push "release: set version $RELEASE_VERSION" mockserver/
git_tag_and_push "mockserver-$RELEASE_VERSION"

log_info "Prepare complete"
