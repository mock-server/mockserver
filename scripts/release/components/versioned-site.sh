#!/usr/bin/env bash
# Create a versioned subdomain for the docs (X-Y.mock-server.com).
# Only runs for major/minor releases.
#
# Dry-run: terraform plan, skip terraform apply + S3 mirror.

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
require_cmd aws
require_release_inputs
skip_unless_release_type "versioned-site" full,post-maven

if [[ "$CREATE_VERSIONED_SITE" != "yes" ]]; then
  log_info "Skipping (CREATE_VERSIONED_SITE=$CREATE_VERSIONED_SITE)"
  exit 0
fi

log_step "Create versioned site for $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

# Run terraform in a container with materialised AWS creds (so the container
# doesn't need access to the EC2 metadata endpoint).
tf() {
  local aws_env
  aws_env=$(aws configure export-credentials --format env 2>/dev/null || true)
  if [[ -z "$aws_env" ]]; then
    log_error "aws configure export-credentials returned no creds (requires v2.10+)"
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
  in_docker "$TERRAFORM_IMAGE" -w /build "${env_args[@]}" -- "$@"
}

SUBDOMAIN=$(version_to_subdomain "$RELEASE_VERSION")
log_info "Subdomain: ${SUBDOMAIN}.mock-server.com"

TF_DIR="$REPO_ROOT/terraform/website"
TFVARS="$TF_DIR/terraform.tfvars"
[[ -f "$TFVARS" ]] || { log_error "terraform.tfvars not found"; exit 1; }

if ! grep -qE "\"${SUBDOMAIN}\"\\s*=" "$TFVARS"; then
  BUCKET_NAME="aws-website-mockserver-${SUBDOMAIN}"
  log_info "Adding ${SUBDOMAIN} (bucket: $BUCKET_NAME) to terraform.tfvars"
  if is_dry_run; then
    log_dry "would: sed -i to add subdomain entry"
  else
    sed -i.bak "s/^}$/  \"${SUBDOMAIN}\" = { bucket_name = \"${BUCKET_NAME}\" }\n}/" "$TFVARS"
    rm -f "$TFVARS.bak"
  fi
fi

OLD_SUBDOMAIN=$(version_to_subdomain "$OLD_VERSION")
OLD_BUCKET=$(grep -E "\"${OLD_SUBDOMAIN}\"\\s*=" "$TFVARS" 2>/dev/null | sed 's/.*bucket_name *= *"\([^"]*\)".*/\1/' || true)

if is_dry_run; then
  log_dry "would: set latest_version to $SUBDOMAIN"
else
  sed -i.bak "s/^latest_version.*=.*/latest_version              = \"${SUBDOMAIN}\"/" "$TFVARS"
  rm -f "$TFVARS.bak"
fi

trap 'rm -f "$TF_DIR/tfplan"' EXIT
tf -chdir=/build/terraform/website init -input=false
tf -chdir=/build/terraform/website plan -input=false -out=tfplan

if is_dry_run; then
  log_dry "skip: terraform apply + S3 mirror + commit"
  exit 0
fi

tf -chdir=/build/terraform/website apply -input=false tfplan
rm -f "$TF_DIR/tfplan"
trap - EXIT

NEW_BUCKET=$(tf -chdir=/build/terraform/website output -raw main_bucket_name)
NEW_DISTRIBUTION_ID=$(tf -chdir=/build/terraform/website output -raw main_distribution_id)
log_info "New main bucket: $NEW_BUCKET"
log_info "New distribution: $NEW_DISTRIBUTION_ID"

if [[ -n "$OLD_BUCKET" && "$OLD_BUCKET" != "$NEW_BUCKET" ]]; then
  log_info "Mirror content $OLD_BUCKET -> $NEW_BUCKET"
  assume_website_role
  aws s3 sync "s3://$OLD_BUCKET/" "s3://$NEW_BUCKET/"
fi

# Persist for downstream components (helm, javadoc, website, schema). The CI
# adapter syncs .tmp/release-outputs.env to whatever cross-step state the
# pipeline system provides (Buildkite meta-data, GitHub Actions outputs, etc.).
set_release_output WEBSITE_BUCKET "$NEW_BUCKET"
set_release_output DISTRIBUTION_ID "$NEW_DISTRIBUTION_ID"

git_commit_and_push "release: versioned site ${SUBDOMAIN}.mock-server.com" \
  "terraform/website/terraform.tfvars"

log_info "Versioned site ${SUBDOMAIN}.mock-server.com created"
