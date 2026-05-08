#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd aws

log_step "Publishing Javadoc for $RELEASE_VERSION"

cd "$REPO_ROOT"

log_info "Checking out release tag"
git checkout "mockserver-$RELEASE_VERSION"

log_info "Generating Javadoc"
mkdir -p .tmp/javadoc
cd mockserver
./mvnw javadoc:aggregate -P release \
  -DreportOutputDirectory="$REPO_ROOT/.tmp/javadoc/$RELEASE_VERSION" \
  -DskipTests

log_info "Assuming website role for S3 upload"
cd "$REPO_ROOT"
assume_website_role

WEBSITE_BUCKET="${WEBSITE_BUCKET:-www.mock-server.com}"

log_info "Uploading Javadoc to S3"
aws s3 sync ".tmp/javadoc/$RELEASE_VERSION" "s3://$WEBSITE_BUCKET/versions/$RELEASE_VERSION/" \
  --delete

log_info "Switching back to master"
git checkout master

log_info "Javadoc for $RELEASE_VERSION published"
