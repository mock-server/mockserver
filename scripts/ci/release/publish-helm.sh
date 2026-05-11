#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd helm
require_cmd aws

log_step "Publishing Helm chart $RELEASE_VERSION"

cd "$REPO_ROOT"

if [[ -z "$WEBSITE_BUCKET" ]]; then
  log_error "WEBSITE_BUCKET not set — cannot publish Helm chart"
  exit 1
fi

log_info "Updating Chart.yaml"
CHART_FILE="helm/mockserver/Chart.yaml"
sed_i "s/^version: .*/version: \"$RELEASE_VERSION\"/" "$CHART_FILE"
sed_i "s/^appVersion: .*/appVersion: \"$RELEASE_VERSION\"/" "$CHART_FILE"

log_info "Assuming website role for S3 upload"
assume_website_role

log_info "Cleaning local chart cache"
rm -f helm/charts/mockserver-*.tgz helm/charts/index.yaml

log_info "Downloading existing Helm charts from S3"
aws s3 sync "s3://$WEBSITE_BUCKET/" helm/charts/ \
  --exclude "*" --include "mockserver-*.tgz" --include "index.yaml"

log_info "Packaging Helm chart"
helm package ./helm/mockserver/ --destination helm/charts/

log_info "Rebuilding Helm repo index with all charts"
helm repo index helm/charts/ --url "https://www.mock-server.com"

log_info "Uploading chart and index to S3"
aws s3 cp "helm/charts/mockserver-$RELEASE_VERSION.tgz" "s3://$WEBSITE_BUCKET/"
aws s3 cp "helm/charts/index.yaml" "s3://$WEBSITE_BUCKET/"

log_info "Committing Helm chart"
cd "$REPO_ROOT"
git add helm/mockserver/Chart.yaml "helm/charts/mockserver-$RELEASE_VERSION.tgz" helm/charts/index.yaml
git commit -m "release: add Helm chart $RELEASE_VERSION" || true
git push origin master

log_info "Helm chart $RELEASE_VERSION published"
