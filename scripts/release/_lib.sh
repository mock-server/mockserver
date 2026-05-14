#!/usr/bin/env bash
# Shared library for release scripts.
#
# DESIGN: see docs/operations/release-principles.md
#
# Release scripts are CI-agnostic. They read configuration from environment
# variables only. Any CI-specific glue (buildkite-agent meta-data lookups,
# CI annotations, etc.) lives in adapter scripts under .buildkite/scripts/
# or .github/workflows/.

set -euo pipefail

RELEASE_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$RELEASE_LIB_DIR/../.." && pwd)"
export REPO_ROOT

# -----------------------------------------------------------------------------
# Pinned tool images — single source of truth for both local and CI.
# Override via env var if needed for a specific run.
# -----------------------------------------------------------------------------

MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-11}"
NODE_IMAGE="${NODE_IMAGE:-node:20-bookworm}"
RUBY_IMAGE="${RUBY_IMAGE:-ruby:3.2-bookworm}"
HELM_IMAGE="${HELM_IMAGE:-alpine/helm:3.16.2}"
GH_IMAGE="${GH_IMAGE:-maniator/gh:v2.62.0}"
PYTHON_IMAGE="${PYTHON_IMAGE:-python:3.12-slim-bookworm}"
TERRAFORM_IMAGE="${TERRAFORM_IMAGE:-hashicorp/terraform:1.9}"
export MAVEN_IMAGE NODE_IMAGE RUBY_IMAGE HELM_IMAGE GH_IMAGE PYTHON_IMAGE TERRAFORM_IMAGE

REGION="${AWS_REGION:-eu-west-2}"

# -----------------------------------------------------------------------------
# Logging
# -----------------------------------------------------------------------------

log_info()  { echo "--- $*"; }
log_error() { echo "--- :x: $*" >&2; }
log_step()  { echo "--- :arrow_right: $*"; }
log_dry()   { echo "--- :test_tube: [DRY RUN] $*"; }

# -----------------------------------------------------------------------------
# Dry-run support
# -----------------------------------------------------------------------------

# Released scripts read this. Default: dry-run if not explicitly set, so a
# careless local invocation can't deploy. CI adapters explicitly set
# DRY_RUN=false to actually release.
DRY_RUN="${DRY_RUN:-true}"

is_dry_run() { [[ "$DRY_RUN" == "true" ]]; }

dry_run_or() {
  local description="$1"; shift
  if is_dry_run; then
    log_dry "skip: $description"
    log_dry "would: $*"
    return 0
  fi
  log_info "$description"
  "$@"
}

# -----------------------------------------------------------------------------
# Input validation
#
# Every component script calls this near the top. It checks that the env-var
# contract is honoured. No CI-specific lookups happen here — the caller is
# responsible for setting the env vars (whether it's a Buildkite adapter, a
# GitHub Actions workflow, or a local invocation).
# -----------------------------------------------------------------------------

require_release_inputs() {
  : "${RELEASE_VERSION:?RELEASE_VERSION must be set (X.Y.Z)}"
  if [[ ! "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    log_error "RELEASE_VERSION must be X.Y.Z, got: $RELEASE_VERSION"
    exit 1
  fi

  # Auto-derive NEXT_VERSION if not supplied.
  if [[ -z "${NEXT_VERSION:-}" ]]; then
    NEXT_VERSION="$(increment_patch_version "$RELEASE_VERSION")-SNAPSHOT"
  fi
  if [[ ! "$NEXT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
    log_error "NEXT_VERSION must be X.Y.Z-SNAPSHOT, got: $NEXT_VERSION"
    exit 1
  fi

  # Auto-derive OLD_VERSION from latest git tag if not supplied.
  if [[ -z "${OLD_VERSION:-}" ]]; then
    git -C "$REPO_ROOT" fetch --tags --quiet 2>/dev/null || true
    OLD_VERSION="$(latest_release_version)"
  fi
  if [[ -z "$OLD_VERSION" ]]; then
    log_error "OLD_VERSION could not be derived — no mockserver-X.Y.Z git tag found"
    exit 1
  fi
  if [[ ! "$OLD_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    log_error "OLD_VERSION must be X.Y.Z, got: $OLD_VERSION"
    exit 1
  fi

  RELEASE_TYPE="${RELEASE_TYPE:-full}"
  case "$RELEASE_TYPE" in
    full|maven-only|docker-only|post-maven) ;;
    *) log_error "RELEASE_TYPE must be full|maven-only|docker-only|post-maven, got: $RELEASE_TYPE"; exit 1 ;;
  esac

  CREATE_VERSIONED_SITE="${CREATE_VERSIONED_SITE:-no}"
  case "$CREATE_VERSIONED_SITE" in
    yes|no) ;;
    *) log_error "CREATE_VERSIONED_SITE must be yes|no, got: $CREATE_VERSIONED_SITE"; exit 1 ;;
  esac

  CURRENT_VERSION="$(current_project_version 2>/dev/null || echo "")"

  export RELEASE_VERSION NEXT_VERSION OLD_VERSION RELEASE_TYPE \
         CREATE_VERSIONED_SITE CURRENT_VERSION DRY_RUN
}

# Each component script declares which RELEASE_TYPEs it applies to. If the
# current RELEASE_TYPE isn't in the list, the script exits 0.
skip_unless_release_type() {
  local component="$1"; shift
  local types_csv="$*"
  local IFS=,
  local t
  for t in $types_csv; do
    if [[ "$RELEASE_TYPE" == "$t" ]]; then return 0; fi
  done
  log_info "Skipping $component for RELEASE_TYPE=$RELEASE_TYPE"
  exit 0
}

# -----------------------------------------------------------------------------
# Environment helpers
# -----------------------------------------------------------------------------

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || { log_error "Missing required command: $cmd"; exit 1; }
}

# -----------------------------------------------------------------------------
# Version helpers
# -----------------------------------------------------------------------------

current_project_version() {
  grep -m1 -E '^[[:space:]]*<version>[^<]+</version>' "$REPO_ROOT/mockserver/pom.xml" \
    | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/'
}

increment_patch_version() {
  local ver="$1"
  local major="${ver%%.*}"
  local minor_rest="${ver#*.}"
  local minor="${minor_rest%%.*}"
  local patch="${ver##*.}"
  echo "${major}.${minor}.$((patch + 1))"
}

latest_release_version() {
  git -C "$REPO_ROOT" tag --list "mockserver-[0-9]*" --sort=-v:refname \
    | sed 's/^mockserver-//' \
    | awk 'NR == 1 { print; exit }'
}

version_to_subdomain() {
  local ver="$1"
  local major="${ver%%.*}"
  local minor_rest="${ver#*.}"
  local minor="${minor_rest%%.*}"
  echo "${major}-${minor}"
}

# Update <version>OLD</version> -> <version>NEW</version> in every pom.xml
# beneath a directory. Skips target/ directories.
update_pom_versions() {
  local search_dir="$1" old_v="$2" new_v="$3"
  require_cmd python3
  python3 - "$old_v" "$new_v" "$search_dir" << 'PYEOF'
import sys, pathlib
old_v, new_v, search = sys.argv[1], sys.argv[2], sys.argv[3]
old_tag = f"<version>{old_v}</version>"
new_tag = f"<version>{new_v}</version>"
updated = []
for path in pathlib.Path(search).rglob("pom.xml"):
    if "target" in path.parts: continue
    text = path.read_text()
    if old_tag in text:
        path.write_text(text.replace(old_tag, new_tag))
        updated.append(str(path.relative_to(search)))
if not updated:
    print(f"ERROR: no pom.xml under {search} contained {old_tag}", file=sys.stderr)
    sys.exit(1)
for p in updated:
    print(f"  updated: {p}")
PYEOF
}

# -----------------------------------------------------------------------------
# Docker helpers
# -----------------------------------------------------------------------------

# Run a command inside a Docker container with the repo mounted at /build.
# Usage:
#   in_docker IMAGE [-w WORKDIR] [-v VOL:DST] [-e KEY=VAL] -- CMD ARGS...
#
# Wraps the existing run-in-docker.sh which logs the docker command for
# local reproduction. The wrapper script redacts secrets in its log banner.
#
# When run behind a corporate TLS-inspecting proxy, set LOCAL_CA_BUNDLE
# (or rely on NODE_EXTRA_CA_CERTS / AWS_CA_BUNDLE which the lib reads
# automatically) to a PEM file on the host. The CA is:
#   - mounted into the container at /etc/ssl/local-ca.pem
#   - exposed via env vars that each toolchain respects (pip/npm/node/aws/gem/curl/git)
#   - installed into the OS CA bundle (so `curl`, `wget` etc. trust it)
#   - imported into the JDK cacerts truststore (so Maven and JVM tools trust it)
#
# The CA-setup prelude only runs when LOCAL_CA_BUNDLE is provided — in CI
# there's no proxy so the wrapper is a no-op and commands run directly.
in_docker() {
  local -a ca_args=()
  local ca="${LOCAL_CA_BUNDLE:-${NODE_EXTRA_CA_CERTS:-${AWS_CA_BUNDLE:-}}}"
  if [[ -n "$ca" && -f "$ca" ]]; then
    ca_args=(
      -v "$ca:/etc/ssl/local-ca.pem:ro"
      -e "NODE_EXTRA_CA_CERTS=/etc/ssl/local-ca.pem"
      -e "AWS_CA_BUNDLE=/etc/ssl/local-ca.pem"
      -e "SSL_CERT_FILE=/etc/ssl/local-ca.pem"
      -e "REQUESTS_CA_BUNDLE=/etc/ssl/local-ca.pem"
      -e "PIP_CERT=/etc/ssl/local-ca.pem"
      -e "GIT_SSL_CAINFO=/etc/ssl/local-ca.pem"
      -e "CURL_CA_BUNDLE=/etc/ssl/local-ca.pem"
    )
  fi
  "$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" -i "$1" "${ca_args[@]+"${ca_args[@]}"}" "${@:2}"
}

# Maven-specific Docker invocation that ALSO installs the host's corp CA
# into the JDK truststore so plugins that download from HTTPS (e.g. the
# frontend-maven-plugin downloading Node.js) work behind a TLS proxy.
#
# In CI, no CA is mounted, the prelude is a no-op, and behaviour matches
# vanilla `in_docker`.
#
# Usage: in_maven [docker-options...] -- <mvn-args...>
in_maven() {
  local -a docker_opts=() mvn_args=()
  local found_sep=false
  for arg in "$@"; do
    if $found_sep; then
      mvn_args+=("$arg")
    elif [[ "$arg" == "--" ]]; then
      found_sep=true
    else
      docker_opts+=("$arg")
    fi
  done
  if [[ ${#mvn_args[@]} -eq 0 ]]; then
    log_error "in_maven: no command after --"
    exit 2
  fi

  # Quote each mvn arg for safe embedding in the bash -ec body.
  local quoted=""
  for a in "${mvn_args[@]}"; do
    quoted+=" $(printf '%q' "$a")"
  done

  in_docker "$MAVEN_IMAGE" \
    "${docker_opts[@]+"${docker_opts[@]}"}" \
    -v mockserver-m2-cache:/root/.m2 \
    -- bash -ec "${ca_install_prelude}exec${quoted}"
}

# Emit a shell snippet that installs the host's corp CA bundle into the
# container's OS trust store AND the JDK truststore. Designed to be the
# first line of a `bash -ec '...'` heredoc passed to in_docker via
# Maven/Java containers.
#
# Idempotent and silent in CI (where /etc/ssl/local-ca.pem isn't mounted)
# so the same heredoc body works in both environments.
ca_install_prelude='
if [ -f /etc/ssl/local-ca.pem ]; then
  if command -v update-ca-certificates >/dev/null 2>&1; then
    cp /etc/ssl/local-ca.pem /usr/local/share/ca-certificates/local-ca.crt 2>/dev/null || true
    update-ca-certificates --fresh >/dev/null 2>&1 || true
  fi
  if command -v keytool >/dev/null 2>&1 && [ -n "${JAVA_HOME:-}" ] && [ -f "${JAVA_HOME}/lib/security/cacerts" ]; then
    keytool -delete -alias local-ca -keystore "${JAVA_HOME}/lib/security/cacerts" -storepass changeit >/dev/null 2>&1 || true
    keytool -importcert -noprompt -trustcacerts -alias local-ca -file /etc/ssl/local-ca.pem -keystore "${JAVA_HOME}/lib/security/cacerts" -storepass changeit >/dev/null 2>&1 || true
  fi
fi
'

# -----------------------------------------------------------------------------
# Cross-step state
#
# Some components produce values that downstream components need (e.g.
# versioned-site computes WEBSITE_BUCKET which helm/javadoc/website/schema
# read). On a single host these can be exported as env vars, but in CI each
# step runs on a different agent so we need persistent storage.
#
# The release scripts are CI-agnostic: they write outputs to a known file
# (.tmp/release-outputs.env) using set_release_output. The CI adapter is
# responsible for syncing that file to the CI's own cross-step state (e.g.
# Buildkite meta-data) and seeding env vars for the next step.
# -----------------------------------------------------------------------------

RELEASE_OUTPUTS_FILE="$REPO_ROOT/.tmp/release-outputs.env"

set_release_output() {
  local key="$1" value="$2"
  mkdir -p "$REPO_ROOT/.tmp"
  # Remove any previous value for this key, then append the new one.
  if [[ -f "$RELEASE_OUTPUTS_FILE" ]]; then
    grep -v "^${key}=" "$RELEASE_OUTPUTS_FILE" > "$RELEASE_OUTPUTS_FILE.tmp" || true
    mv "$RELEASE_OUTPUTS_FILE.tmp" "$RELEASE_OUTPUTS_FILE"
  fi
  printf '%s=%s\n' "$key" "$value" >> "$RELEASE_OUTPUTS_FILE"
  export "$key=$value"
}

# -----------------------------------------------------------------------------
# AWS helpers
# -----------------------------------------------------------------------------

# Load a JSON secret from AWS Secrets Manager. Returns the value of the
# specified key. In --dry-run mode, returns a placeholder so scripts can
# exercise their build/check/lint logic without needing real credentials.
load_secret() {
  local secret_id="$1" key="$2"
  if is_dry_run && [[ -z "${LOAD_REAL_SECRETS_IN_DRY_RUN:-}" ]]; then
    echo "DRY_RUN_PLACEHOLDER_${key^^}"
    return
  fi
  local xtrace_state
  xtrace_state=$(shopt -po xtrace 2>/dev/null || true)
  set +x
  local -a aws_args=(--region "$REGION" --secret-id "$secret_id" --query SecretString --output text)
  [[ -n "${AWS_PROFILE:-}" ]] && aws_args+=(--profile "$AWS_PROFILE")
  local json
  json=$(aws secretsmanager get-secret-value "${aws_args[@]}")
  echo "$json" | jq -r ".$key"
  eval "$xtrace_state"
}

assume_website_role() {
  if is_dry_run; then
    log_dry "skip: assume website role"
    return
  fi
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
  AWS_ACCESS_KEY_ID=$(echo "$creds" | jq -r '.Credentials.AccessKeyId')
  AWS_SECRET_ACCESS_KEY=$(echo "$creds" | jq -r '.Credentials.SecretAccessKey')
  AWS_SESSION_TOKEN=$(echo "$creds" | jq -r '.Credentials.SessionToken')
  export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN
  eval "$xtrace_state"
}

# -----------------------------------------------------------------------------
# Git helpers
# -----------------------------------------------------------------------------

sync_to_origin_master() {
  if is_dry_run; then
    log_dry "skip: git fetch + reset --hard origin/master"
    return
  fi
  git -C "$REPO_ROOT" fetch --quiet --tags origin master
  git -C "$REPO_ROOT" reset --quiet --hard origin/master
}

# Configure git identity (no-op if already configured) and install a push
# credential via http.extraheader. Idempotent. Skipped entirely in dry-run.
configure_git_for_push() {
  if is_dry_run; then return; fi

  if [[ -z "$(git -C "$REPO_ROOT" config user.email 2>/dev/null || true)" ]]; then
    git -C "$REPO_ROOT" config user.email "release@mock-server.com"
  fi
  if [[ -z "$(git -C "$REPO_ROOT" config user.name 2>/dev/null || true)" ]]; then
    git -C "$REPO_ROOT" config user.name "MockServer Release"
  fi

  local token
  token=$(load_secret "mockserver-release/github-token" "token" 2>/dev/null || echo "")
  if [[ -n "$token" && "$token" != "null" ]]; then
    # `base64 | tr -d '\n'` is portable across macOS base64 and GNU base64
    # (which wraps at 76 chars by default — that breaks header parsing).
    git -C "$REPO_ROOT" config "http.https://github.com/.extraheader" \
      "AUTHORIZATION: basic $(printf 'x-access-token:%s' "$token" | base64 | tr -d '\n')"
  fi
}

git_commit_and_push() {
  local message="$1"; shift
  local -a paths=("$@")
  if is_dry_run; then
    log_dry "would: git add ${paths[*]}"
    log_dry "would: git commit -m \"$message\""
    log_dry "would: git push origin HEAD:master"
    return
  fi
  configure_git_for_push
  git -C "$REPO_ROOT" add "${paths[@]}"
  git -C "$REPO_ROOT" commit -m "$message"
  git -C "$REPO_ROOT" push origin HEAD:master
}

git_tag_and_push() {
  local tag="$1"
  if is_dry_run; then
    log_dry "would: git tag $tag"
    log_dry "would: git push origin $tag"
    return
  fi
  configure_git_for_push
  git -C "$REPO_ROOT" tag "$tag"
  git -C "$REPO_ROOT" push origin "$tag"
}
