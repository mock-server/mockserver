#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd terraform
require_cmd bundle
require_cmd jekyll
require_cmd aws

log_step "Creating versioned site for $RELEASE_VERSION"

cd "$REPO_ROOT"

MAJOR="${RELEASE_VERSION%%.*}"
MINOR_REST="${RELEASE_VERSION#*.}"
MINOR="${MINOR_REST%%.*}"
SUBDOMAIN="${MAJOR}-${MINOR}"

log_info "Version subdomain: ${SUBDOMAIN}.mock-server.com"

TF_DIR="$REPO_ROOT/terraform/website"

if [[ ! -d "$TF_DIR" ]]; then
  log_error "Terraform website module not found at $TF_DIR"
  exit 1
fi

log_info "Adding versioned site to Terraform config"
if ! grep -q "\"$SUBDOMAIN\"" "$TF_DIR/terraform.tfvars" 2>/dev/null; then
  log_info "Appending $SUBDOMAIN to versioned_sites in terraform.tfvars"
fi

log_info "Running Terraform plan"
terraform -chdir="$TF_DIR" init -input=false
terraform -chdir="$TF_DIR" plan -input=false

if ! is_ci; then
  confirm "Apply Terraform changes?"
fi

log_info "Applying Terraform"
terraform -chdir="$TF_DIR" apply -input=false -auto-approve

log_info "Building Jekyll site for versioned site"
cd "$REPO_ROOT/jekyll-www.mock-server.com"
rm -rf _site
bundle exec jekyll build

log_info "Copying legacy URL pages"
cp _site/mock_server/mockserver_clients.html _site/ 2>/dev/null || true
cp _site/mock_server/running_mock_server.html _site/ 2>/dev/null || true
cp _site/mock_server/debugging_issues.html _site/ 2>/dev/null || true
cp _site/mock_server/creating_expectations.html _site/ 2>/dev/null || true

log_info "Assuming website role for S3 upload"
cd "$REPO_ROOT"
assume_website_role

VERSIONED_BUCKET="${SUBDOMAIN}.mock-server.com"

log_info "Syncing to versioned site bucket: $VERSIONED_BUCKET"
aws s3 sync "$REPO_ROOT/jekyll-www.mock-server.com/_site/" "s3://$VERSIONED_BUCKET/" --delete

log_info "Committing versioned site config"
git add -A
git commit -m "release: add versioned site ${SUBDOMAIN}.mock-server.com" || true
git push origin master

log_info "Versioned site ${SUBDOMAIN}.mock-server.com created"
