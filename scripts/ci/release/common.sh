#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
export REPO_ROOT
REGION="eu-west-2"

is_ci() { [[ -n "${BUILDKITE:-}" ]]; }

log_info()  { echo "--- $*"; }
log_error() { echo "--- :x: $*" >&2; }
log_step()  { echo "--- :arrow_right: $*"; }

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || { log_error "Missing required command: $cmd"; exit 1; }
}

sed_i() {
  if sed --version 2>/dev/null | grep -q GNU; then
    sed -i "$@"
  else
    sed -i '' "$@"
  fi
}

require_env() {
  local var="$1"
  if [[ -z "${!var:-}" ]]; then
    log_error "Required environment variable not set: $var"
    exit 1
  fi
}

confirm() {
  if is_ci; then return 0; fi
  local prompt="$1"
  read -rp "$prompt [y/N] " response
  [[ "$response" == [yY] ]] || { echo "Aborted."; exit 1; }
}

load_secret() {
  local secret_id="$1" key="$2"
  local xtrace_state
  xtrace_state=$(shopt -po xtrace 2>/dev/null || true)
  set +x
  local json
  if is_ci; then
    json=$(aws secretsmanager get-secret-value \
      --secret-id "$secret_id" \
      --region "$REGION" \
      --query SecretString --output text)
  else
    json=$(aws secretsmanager get-secret-value \
      --secret-id "$secret_id" \
      --region "$REGION" \
      --profile "${AWS_PROFILE:-mockserver-build}" \
      --query SecretString --output text)
  fi
  echo "$json" | jq -r ".$key"
  eval "$xtrace_state"
}

assume_website_role() {
  local role_arn
  role_arn=$(load_secret "mockserver-release/website-role" "role_arn")
  local xtrace_state
  xtrace_state=$(shopt -po xtrace 2>/dev/null || true)
  set +x
  local creds
  creds=$(aws sts assume-role \
    --role-arn "$role_arn" \
    --role-session-name "mockserver-release-${RELEASE_VERSION}" \
    --duration-seconds 3600 \
    --output json)
  local aki_val sak_val st_val
  aki_val=$(echo "$creds" | jq -r '.Credentials.AccessKeyId')
  sak_val=$(echo "$creds" | jq -r '.Credentials.SecretAccessKey')
  st_val=$(echo "$creds" | jq -r '.Credentials.SessionToken')
  export AWS_ACCESS_KEY_ID="$aki_val"
  export AWS_SECRET_ACCESS_KEY="$sak_val"
  export AWS_SESSION_TOKEN="$st_val"
  eval "$xtrace_state"
}

central_portal_auth_header() {
  local username password
  username=$(load_secret "mockserver-build/sonatype" "username")
  password=$(load_secret "mockserver-build/sonatype" "password")
  printf "%s:%s" "$username" "$password" | base64
}

version_to_subdomain() {
  local ver="$1"
  local major="${ver%%.*}"
  local minor_rest="${ver#*.}"
  local minor="${minor_rest%%.*}"
  echo "${major}-${minor}"
}

if is_ci; then
  RELEASE_VERSION=$(buildkite-agent meta-data get release-version)
  NEXT_VERSION=$(buildkite-agent meta-data get next-version)
  OLD_VERSION=$(buildkite-agent meta-data get old-version)
  WEBSITE_BUCKET=$(buildkite-agent meta-data get website-bucket 2>/dev/null || echo "")
  DISTRIBUTION_ID=$(buildkite-agent meta-data get distribution-id 2>/dev/null || echo "")
else
  : "${RELEASE_VERSION:?Set RELEASE_VERSION}" "${NEXT_VERSION:?Set NEXT_VERSION}" "${OLD_VERSION:?Set OLD_VERSION}"
  : "${WEBSITE_BUCKET:=}"
  : "${DISTRIBUTION_ID:=}"
fi

if [[ -z "$WEBSITE_BUCKET" ]]; then
  WEBSITE_BUCKET=$(cd "$REPO_ROOT/terraform/website" && terraform output -raw main_bucket_name 2>/dev/null || echo "")
fi
if [[ -z "$DISTRIBUTION_ID" ]]; then
  DISTRIBUTION_ID=$(cd "$REPO_ROOT/terraform/website" && terraform output -raw main_distribution_id 2>/dev/null || echo "")
fi

export RELEASE_VERSION NEXT_VERSION OLD_VERSION WEBSITE_BUCKET DISTRIBUTION_ID
