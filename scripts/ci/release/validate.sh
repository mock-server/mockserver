#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

log_step "Validating release inputs"

if [[ ! "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  log_error "RELEASE_VERSION must be in X.Y.Z format, got: $RELEASE_VERSION"
  exit 1
fi

if [[ ! "$NEXT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
  log_error "NEXT_VERSION must be in X.Y.Z-SNAPSHOT format, got: $NEXT_VERSION"
  exit 1
fi

if [[ ! "$OLD_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  log_error "OLD_VERSION must be in X.Y.Z format, got: $OLD_VERSION"
  exit 1
fi

CURRENT_BRANCH=$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)
if [[ "$CURRENT_BRANCH" != "master" ]]; then
  log_error "Releases must be performed from master, currently on: $CURRENT_BRANCH"
  exit 1
fi

if [[ -n "$(git -C "$REPO_ROOT" status --porcelain)" ]]; then
  log_error "Working tree is dirty — commit or stash changes before releasing"
  git -C "$REPO_ROOT" status --short >&2
  exit 1
fi

if git -C "$REPO_ROOT" rev-parse "mockserver-$RELEASE_VERSION" >/dev/null 2>&1; then
  log_error "Tag mockserver-$RELEASE_VERSION already exists"
  exit 1
fi

log_info "Validation passed"
log_info "  Release:  $RELEASE_VERSION"
log_info "  Next:     $NEXT_VERSION"
log_info "  Previous: $OLD_VERSION"
log_info "  Branch:   $CURRENT_BRANCH"
