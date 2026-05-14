#!/usr/bin/env bash
# Publish Helm chart for MockServer.
#
# Dry-run: package + lint chart, skip S3 upload + git push.

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
skip_unless_release_type "helm" full,post-maven

log_step "Publish Helm chart $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

CHART_FILE="$REPO_ROOT/helm/mockserver/Chart.yaml"

log_info "Update Chart.yaml to $RELEASE_VERSION"
if is_dry_run; then
  log_dry "would: set version + appVersion in $CHART_FILE"
else
  sed -i.bak "s/^version: .*/version: \"$RELEASE_VERSION\"/" "$CHART_FILE"
  sed -i.bak "s/^appVersion: .*/appVersion: \"$RELEASE_VERSION\"/" "$CHART_FILE"
  rm -f "$CHART_FILE.bak"
fi

log_info "Lint chart"
in_docker "$HELM_IMAGE" -w /build -- lint ./helm/mockserver/

log_info "Package chart"
mkdir -p "$REPO_ROOT/helm/charts"
in_docker "$HELM_IMAGE" -w /build -- package ./helm/mockserver/ --destination helm/charts/

if is_dry_run; then
  log_dry "skip: aws s3 download index, repack, upload, commit/push"
  log_info "Built chart: helm/charts/mockserver-$RELEASE_VERSION.tgz"
else
  log_info "Sync existing charts from S3"
  if [[ -z "${WEBSITE_BUCKET:-}" ]]; then
    log_error "WEBSITE_BUCKET not set"; exit 1
  fi
  assume_website_role
  rm -f "$REPO_ROOT"/helm/charts/index.yaml
  aws s3 sync "s3://$WEBSITE_BUCKET/" "$REPO_ROOT/helm/charts/" \
    --exclude "*" --include "mockserver-*.tgz" --include "index.yaml"

  log_info "Rebuild Helm repo index"
  in_docker "$HELM_IMAGE" -w /build -- repo index helm/charts/ --url "https://www.mock-server.com"

  log_info "Upload to S3"
  aws s3 cp "$REPO_ROOT/helm/charts/mockserver-$RELEASE_VERSION.tgz" "s3://$WEBSITE_BUCKET/"
  aws s3 cp "$REPO_ROOT/helm/charts/index.yaml" "s3://$WEBSITE_BUCKET/"

  git_commit_and_push "release: Helm chart $RELEASE_VERSION" \
    helm/mockserver/Chart.yaml \
    "helm/charts/mockserver-$RELEASE_VERSION.tgz" \
    helm/charts/index.yaml
fi

log_info "Helm publish complete"
