#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker
require_cmd aws
require_cmd git

# Wrap terraform invocations in Docker so this script runs on a CI agent
# that has only docker installed (and locally on any laptop with Docker).
# Materialise AWS creds first so the container doesn't need access to the
# instance metadata endpoint.
tf() {
  local aws_env
  aws_env=$(aws configure export-credentials --format env 2>/dev/null || true)
  if [[ -z "$aws_env" ]]; then
    log_error "aws configure export-credentials returned no creds — requires AWS CLI v2.10+ and valid credentials in scope"
    exit 1
  fi
  local -a env_args=()
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    line="${line#export }"
    env_args+=(-e "$line")
  done <<< "$aws_env"
  env_args+=(-e "AWS_DEFAULT_REGION=${AWS_REGION:-eu-west-2}")
  env_args+=(-e "AWS_REGION=${AWS_REGION:-eu-west-2}")

  "$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
    -i "$TERRAFORM_IMAGE" \
    -w /build \
    "${env_args[@]}" \
    -- "$@"
}

log_step "Creating versioned site for $RELEASE_VERSION"

cd "$REPO_ROOT"

SUBDOMAIN=$(version_to_subdomain "$RELEASE_VERSION")
log_info "Version subdomain: ${SUBDOMAIN}.mock-server.com"

TF_DIR="$REPO_ROOT/terraform/website"
TFVARS="$TF_DIR/terraform.tfvars"

if [[ ! -f "$TFVARS" ]]; then
  log_error "terraform.tfvars not found at $TFVARS"
  exit 1
fi

if grep -qE "\"${SUBDOMAIN}\"\\s*=" "$TFVARS"; then
  log_info "Site $SUBDOMAIN already exists in terraform.tfvars — updating latest_version only"
else
  BUCKET_NAME="aws-website-mockserver-${SUBDOMAIN}"
  log_info "Adding site $SUBDOMAIN (bucket: $BUCKET_NAME) to terraform.tfvars"

  sed_i "s/^}$/  \"${SUBDOMAIN}\" = { bucket_name = \"${BUCKET_NAME}\" }\n}/" "$TFVARS"
fi

OLD_SUBDOMAIN=$(version_to_subdomain "$OLD_VERSION")
OLD_BUCKET=$(grep -E "\"${OLD_SUBDOMAIN}\"\\s*=" "$TFVARS" | sed 's/.*bucket_name *= *"\([^"]*\)".*/\1/')
sed_i "s/^latest_version.*=.*/latest_version              = \"${SUBDOMAIN}\"/" "$TFVARS"

log_info "Initializing Terraform"
tf -chdir=/build/terraform/website init -input=false

log_info "Planning Terraform changes"
trap 'rm -f "$TF_DIR/tfplan"' EXIT
tf -chdir=/build/terraform/website plan -input=false -out=tfplan

if ! is_ci; then
  confirm "Apply Terraform changes to create versioned site?"
fi

log_info "Applying Terraform"
tf -chdir=/build/terraform/website apply -input=false tfplan
rm -f "$TF_DIR/tfplan"
trap - EXIT

NEW_BUCKET=$(tf -chdir=/build/terraform/website output -raw main_bucket_name)
NEW_DISTRIBUTION_ID=$(tf -chdir=/build/terraform/website output -raw main_distribution_id)

log_info "New main bucket: $NEW_BUCKET"
log_info "Main distribution ID: $NEW_DISTRIBUTION_ID"

if [[ -n "$OLD_BUCKET" && "$OLD_BUCKET" != "$NEW_BUCKET" ]]; then
  log_info "Copying content from old bucket ($OLD_BUCKET) to new bucket ($NEW_BUCKET)"
  assume_website_role
  aws s3 sync "s3://$OLD_BUCKET/" "s3://$NEW_BUCKET/"
  log_info "Baseline content copied — main site is live on new bucket"
fi

if is_ci; then
  buildkite-agent meta-data set website-bucket "$NEW_BUCKET"
  buildkite-agent meta-data set distribution-id "$NEW_DISTRIBUTION_ID"
fi

export WEBSITE_BUCKET="$NEW_BUCKET"
export DISTRIBUTION_ID="$NEW_DISTRIBUTION_ID"

log_info "Committing versioned site config"
git add "$TFVARS"
git commit -m "release: add versioned site ${SUBDOMAIN}.mock-server.com" || true
git push origin HEAD:master

log_info "Versioned site ${SUBDOMAIN}.mock-server.com created"
log_info "Main site now points to bucket: $NEW_BUCKET"
