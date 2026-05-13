#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker
require_cmd aws
require_cmd git

log_step "Publishing Javadoc for $RELEASE_VERSION"

if [[ -z "$WEBSITE_BUCKET" ]]; then
  log_error "WEBSITE_BUCKET not set — cannot publish Javadoc"
  exit 1
fi

cd "$REPO_ROOT"

cleanup_git() {
  log_info "Restoring master branch"
  git checkout master 2>/dev/null || true
}
trap cleanup_git EXIT

log_info "Checking out release tag"
git checkout "mockserver-$RELEASE_VERSION"

log_info "Generating Javadoc (in Docker)"
mkdir -p .tmp/javadoc
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -w /build/mockserver \
  -v mockserver-m2-cache:/root/.m2 \
  -- mvn javadoc:aggregate -P release \
       -DreportOutputDirectory="/build/.tmp/javadoc/$RELEASE_VERSION" \
       -DskipTests

log_info "Assuming website role for S3 upload"
assume_website_role

log_info "Uploading Javadoc to S3"
aws s3 sync ".tmp/javadoc/$RELEASE_VERSION" "s3://$WEBSITE_BUCKET/versions/$RELEASE_VERSION/" \
  --delete

log_info "Switching back to master"
git checkout master
trap - EXIT

log_info "Javadoc for $RELEASE_VERSION published"
