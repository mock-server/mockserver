#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

log_step "Validating release inputs"

check_secret_key() {
  local secret_id="$1"
  local key="$2"
  local label="$3"
  local value
  value=$(load_secret "$secret_id" "$key")
  if [[ -z "$value" || "$value" == "null" ]]; then
    log_error "$label is missing in $secret_id"
    exit 1
  fi
}

if [[ ! "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  log_error "RELEASE_VERSION must be in X.Y.Z format, got: $RELEASE_VERSION"
  exit 1
fi

if [[ ! "$NEXT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
  log_error "NEXT_VERSION must be in X.Y.Z-SNAPSHOT format, got: $NEXT_VERSION"
  exit 1
fi

if [[ ! "$OLD_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  log_error "OLD_VERSION must be in X.Y.Z format, got: $OLD_VERSION"
  exit 1
fi

CURRENT_PROJECT_VERSION=$(current_project_version)
if [[ ! "$CURRENT_PROJECT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
  log_error "Current project version must be an X.Y.Z-SNAPSHOT, got: $CURRENT_PROJECT_VERSION"
  exit 1
fi

EXPECTED_NEXT_VERSION="$(increment_patch_version "$RELEASE_VERSION")-SNAPSHOT"
if [[ "$NEXT_VERSION" != "$EXPECTED_NEXT_VERSION" ]]; then
  log_error "NEXT_VERSION must be the next patch SNAPSHOT after RELEASE_VERSION, expected: $EXPECTED_NEXT_VERSION"
  exit 1
fi

LATEST_RELEASE_VERSION=$(latest_release_version)
if [[ -n "$LATEST_RELEASE_VERSION" && "$OLD_VERSION" != "$LATEST_RELEASE_VERSION" ]]; then
  log_error "OLD_VERSION must match the latest released version, expected: $LATEST_RELEASE_VERSION"
  exit 1
fi

if [[ "$RELEASE_VERSION" == "$OLD_VERSION" ]]; then
  log_error "RELEASE_VERSION must differ from OLD_VERSION"
  exit 1
fi

if is_ci; then
  CURRENT_BRANCH="${BUILDKITE_BRANCH:-unknown}"
else
  CURRENT_BRANCH=$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)
fi
if [[ "$CURRENT_BRANCH" != "master" ]]; then
  log_error "Releases must be performed from master, currently on: $CURRENT_BRANCH"
  exit 1
fi

if [[ -n "$(git -C "$REPO_ROOT" status --porcelain)" ]]; then
  log_error "Working tree is dirty — commit or stash changes before releasing"
  git -C "$REPO_ROOT" status --short >&2
  exit 1
fi

if git -C "$REPO_ROOT" rev-parse "mockserver-$RELEASE_VERSION" >/dev/null 2>&1; then
  log_error "Tag mockserver-$RELEASE_VERSION already exists"
  exit 1
fi

if grep -Eq "^## \[$RELEASE_VERSION\]" "$REPO_ROOT/changelog.md"; then
  log_error "changelog.md already contains a section for $RELEASE_VERSION"
  exit 1
fi

UNRELEASED_SECTION=$(sed -n '/^## \[Unreleased\]/,/^## \[/p' "$REPO_ROOT/changelog.md" | sed '1d;$d')
if ! printf '%s\n' "$UNRELEASED_SECTION" | grep -Eq '^- '; then
  log_error "changelog.md must contain at least one bullet under the Unreleased section"
  exit 1
fi

if [[ "$CREATE_VERSIONED_SITE" == "yes" && "${RELEASE_VERSION%.*}" == "${OLD_VERSION%.*}" ]]; then
  log_error "Create Versioned Site should only be enabled for a major or minor release"
  exit 1
fi

mkdir -p "$REPO_ROOT/.tmp"
printf '%s\n' "$CURRENT_PROJECT_VERSION" > "$REPO_ROOT/.tmp/release-current-version"
if is_ci; then
  buildkite-agent meta-data set current-version "$CURRENT_PROJECT_VERSION"
fi

log_info "Checking required release secrets"
check_secret_key "mockserver-build/sonatype" "username" "Sonatype username"
check_secret_key "mockserver-build/sonatype" "password" "Sonatype password"
check_secret_key "mockserver-release/gpg-key" "key" "GPG private key"
check_secret_key "mockserver-release/gpg-key" "passphrase" "GPG passphrase"

if [[ "$RELEASE_TYPE" == "full" || "$RELEASE_TYPE" == "post-maven" ]]; then
  check_secret_key "mockserver-build/dockerhub" "username" "Docker Hub username"
  check_secret_key "mockserver-build/dockerhub" "token" "Docker Hub token"
  check_secret_key "mockserver-build/pypi" "token" "PyPI token"
  check_secret_key "mockserver-build/rubygems" "api_key" "RubyGems API key"
  check_secret_key "mockserver-release/github-token" "token" "GitHub token"
  check_secret_key "mockserver-release/npm-token" "token" "npm token"
  check_secret_key "mockserver-release/swaggerhub" "api_key" "SwaggerHub API key"
  check_secret_key "mockserver-release/website-role" "role_arn" "Website role ARN"
fi

log_info "Validation passed"
log_info "  Release:  $RELEASE_VERSION"
log_info "  Next:     $NEXT_VERSION"
log_info "  Previous: $OLD_VERSION"
log_info "  Current:  $CURRENT_PROJECT_VERSION"
log_info "  Type:     $RELEASE_TYPE"
log_info "  Branch:   $CURRENT_BRANCH"
