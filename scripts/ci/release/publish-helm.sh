#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd helm
require_cmd aws

log_step "Publishing Helm chart $RELEASE_VERSION"

cd "$REPO_ROOT"

log_info "Updating Chart.yaml"
CHART_FILE="helm/mockserver/Chart.yaml"
sed_i "s/^version: .*/version: \"$RELEASE_VERSION\"/" "$CHART_FILE"
sed_i "s/^appVersion: .*/appVersion: \"$RELEASE_VERSION\"/" "$CHART_FILE"

log_info "Packaging Helm chart"
helm package ./helm/mockserver/ --destination helm/charts/

log_info "Updating Helm repo index"
helm repo index helm/charts/

log_info "Assuming website role for S3 upload"
assume_website_role

WEBSITE_BUCKET="${WEBSITE_BUCKET:-www.mock-server.com}"

log_info "Uploading chart to S3"
aws s3 cp "helm/charts/mockserver-$RELEASE_VERSION.tgz" "s3://$WEBSITE_BUCKET/"
aws s3 cp "helm/charts/index.yaml" "s3://$WEBSITE_BUCKET/"

log_info "Committing Helm chart"
cd "$REPO_ROOT"
git add helm/mockserver/Chart.yaml "helm/charts/mockserver-$RELEASE_VERSION.tgz" helm/charts/index.yaml
git commit -m "release: add Helm chart $RELEASE_VERSION"
git push origin master

log_info "Helm chart $RELEASE_VERSION published"
