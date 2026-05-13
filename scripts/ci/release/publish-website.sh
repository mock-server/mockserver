#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker
require_cmd aws

log_step "Publishing website for $RELEASE_VERSION"

log_info "Building Jekyll site (in Docker)"
rm -rf "$REPO_ROOT/jekyll-www.mock-server.com/_site"
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$RUBY_IMAGE" \
  -w /build/jekyll-www.mock-server.com \
  -v mockserver-bundle-cache:/usr/local/bundle \
  -- bash -ec '
    bundle install --quiet
    bundle exec jekyll build
  '

cd "$REPO_ROOT/jekyll-www.mock-server.com"
log_info "Copying legacy URL pages"
cp _site/mock_server/mockserver_clients.html _site/ 2>/dev/null || true
cp _site/mock_server/running_mock_server.html _site/ 2>/dev/null || true
cp _site/mock_server/debugging_issues.html _site/ 2>/dev/null || true
cp _site/mock_server/creating_expectations.html _site/ 2>/dev/null || true

if [[ -z "$WEBSITE_BUCKET" ]]; then
  log_error "WEBSITE_BUCKET not set — cannot publish website"
  exit 1
fi

log_info "Assuming website role"
cd "$REPO_ROOT"
assume_website_role

log_info "Syncing to S3"
aws s3 sync "$REPO_ROOT/jekyll-www.mock-server.com/_site/" "s3://$WEBSITE_BUCKET/" --delete \
  --exclude "versions/*" \
  --exclude "schema/*" \
  --exclude "index.yaml" \
  --exclude "*.tgz"

if [[ -n "$DISTRIBUTION_ID" ]]; then
  log_info "Invalidating CloudFront cache"
  aws cloudfront create-invalidation \
    --distribution-id "$DISTRIBUTION_ID" \
    --paths "/*"
fi

log_info "Website published for $RELEASE_VERSION"
