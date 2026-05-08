#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

log_step "Setting release version $RELEASE_VERSION"

cd "$REPO_ROOT/mockserver"

log_info "Setting Maven version"
./mvnw versions:set -DnewVersion="$RELEASE_VERSION" -DgenerateBackupPoms=false
./mvnw versions:commit

log_info "Committing version change"
cd "$REPO_ROOT"
git add -A
git commit -m "release: set version $RELEASE_VERSION"

log_info "Creating tag mockserver-$RELEASE_VERSION"
git tag "mockserver-$RELEASE_VERSION"

log_info "Pushing to origin"
git push origin master
git push origin "mockserver-$RELEASE_VERSION"

log_info "Release version $RELEASE_VERSION set and tagged"
