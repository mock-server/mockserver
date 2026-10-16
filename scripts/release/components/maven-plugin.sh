#!/usr/bin/env bash
# Release the mockserver-maven-plugin to Maven Central.
#
# The plugin inherits its version from the mockserver parent pom and uses
# ${project.version} for its internal mockserver-* dependency references, so
# version bumps are handled by the main maven-central release component. This
# script just builds the core mockserver (so the plugin can resolve it), then
# tags, deploys and publishes the plugin to Maven Central.
#
# Dry-run: build + verify (with tests) only; skip tag + deploy.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source-path=SCRIPTDIR source=../_lib.sh
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
require_cmd git
require_cmd curl
require_cmd jq
require_release_inputs
skip_unless_release_type "maven-plugin" full,post-maven

log_step "Release mockserver-maven-plugin $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

# Idempotent: if this version is already on Maven Central the plugin release
# is done. A re-run must not redeploy an existing version - Central rejects it.
if ! is_dry_run && curl -fsI -o /dev/null \
    "https://repo1.maven.org/maven2/org/mock-server/mockserver-maven-plugin/$RELEASE_VERSION/mockserver-maven-plugin-$RELEASE_VERSION.pom"; then
  log_info "mockserver-maven-plugin $RELEASE_VERSION already on Maven Central - skipping"
  exit 0
fi

log_info "Build core MockServer (in Docker)"
in_maven -w /build/mockserver \
  -- mvn clean install -DskipTests -DskipITs

# Same command in dry-run, so the dry run runs the plugin tests the release runs.
# Pom-default test settings, as CI's maven-plugin build (maven-plugin-build.sh) uses.
log_info "Verify maven-plugin (in Docker)"
in_maven -w /build/mockserver/mockserver-maven-plugin -- mvn clean verify \
  || { build_rc=$?; collect_failed_tests; exit "$build_rc"; }

if is_dry_run; then
  log_dry "skip: tag, deploy"
  log_info "maven-plugin dry-run complete"
  exit 0
fi

git_tag_and_push "maven-plugin-$RELEASE_VERSION"

log_info "Deploy + publish maven-plugin to Maven Central (GPG-sign in container)"

# Write secrets to 0600 files under .tmp/ (mounted at /build in the
# container) instead of passing via `docker run -e`. Env vars are readable
# via /proc/1/environ and `docker inspect`; file-based secrets are not.
# Pattern mirrors the cosign key handling in helm.sh.
mkdir -p "$REPO_ROOT/.tmp"
GPG_KEY_FILE="$REPO_ROOT/.tmp/gpg-key-plugin.$$"
GPG_PASS_FILE="$REPO_ROOT/.tmp/gpg-passphrase-plugin.$$"
SONATYPE_CREDS_FILE="$REPO_ROOT/.tmp/sonatype-creds-plugin.$$"
_mp_cleanup_secrets() {
  rm -f "$GPG_KEY_FILE" "$GPG_PASS_FILE" "$SONATYPE_CREDS_FILE"
}
( umask 077
  load_secret "mockserver-release/gpg-key" "key"        > "$GPG_KEY_FILE"
  load_secret "mockserver-release/gpg-key" "passphrase" > "$GPG_PASS_FILE"
  load_secret "mockserver-build/sonatype" "username"     > "$SONATYPE_CREDS_FILE"
  load_secret "mockserver-build/sonatype" "password"    >> "$SONATYPE_CREDS_FILE"
)

# Write the mvn output to a tmpfile under .tmp/ (per AGENTS.md policy) so we
# can both stream it to the agent's stdout AND extract the deploymentId
# afterwards. Capturing in `$(...)` would obscure mvn's exit code: under
# `set -o pipefail` the pipeline's exit status is the rightmost non-zero,
# so a `$(cmd | tee /dev/stderr)` returns tee's exit code (always 0) — a
# failed `mvn deploy` would be silently swallowed (same class of bug as
# F-MC-01 in maven-central.sh). Explicit PIPESTATUS[0] check after the
# pipe makes the failure surface immediately. tmpfile is rm'd as soon as
# DEPLOYMENT_ID is captured because it can contain GPG/Sonatype output if
# any subprocess accidentally enables `set -x`.
DEPLOY_LOG=$(mktemp "$REPO_ROOT/.tmp/mockserver-maven-plugin-deploy.XXXXXX")
trap '_mp_cleanup_secrets; rm -f "$DEPLOY_LOG"' EXIT

in_docker "$MAVEN_IMAGE" \
  -w /build/mockserver/mockserver-maven-plugin \
  -v mockserver-m2-cache:/root/.m2 \
  -- bash -ec "${maven_packaging_prelude}"'
    command -v gpg >/dev/null 2>&1 || { apt-get -o Acquire::Retries=3 update -qq >/dev/null \
      && apt-get -o Acquire::Retries=3 install -y -qq gnupg >/dev/null; }
    set +x
    base64 -d < /build/.tmp/gpg-key-plugin.'$$' | gpg --batch --import
    mkdir -p ~/.gnupg
    echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf
    gpgconf --reload gpg-agent 2>/dev/null || true
    GPG_PASSPHRASE=$(cat /build/.tmp/gpg-passphrase-plugin.'$$')
    SONATYPE_USERNAME=$(head -1 /build/.tmp/sonatype-creds-plugin.'$$')
    SONATYPE_PASSWORD=$(tail -1 /build/.tmp/sonatype-creds-plugin.'$$')
    cat > /tmp/settings.xml <<SETTINGS
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <servers>
    <server>
      <id>central-portal</id>
      <username>${SONATYPE_USERNAME}</username>
      <password>${SONATYPE_PASSWORD}</password>
    </server>
    <server>
      <id>gpg.passphrase</id>
      <passphrase>${GPG_PASSPHRASE}</passphrase>
    </server>
  </servers>
</settings>
SETTINGS
    # central-publishing-maven-plugin is configured in the parent pom with
    # autoPublish=false / waitUntil=validated. Those are explicit <configuration>
    # values, which Maven gives precedence over -D user properties, so they
    # cannot be flipped from the command line. `mvn deploy` therefore only
    # uploads and validates the deployment and prints its deploymentId; the
    # promote-to-published step is done via the Central Portal API below.
    mvn deploy -P release -DskipTests -DskipITs \
      -Dgpg.passphraseServerId=gpg.passphrase \
      -Dgpg.useagent=false \
      --settings /tmp/settings.xml
  ' 2>&1 | tee "$DEPLOY_LOG"
mvn_exit=${PIPESTATUS[0]}
if [[ $mvn_exit -ne 0 ]]; then
  log_error "mvn deploy failed (in_docker exit $mvn_exit)"
  exit "$mvn_exit"
fi

# Promote the validated deployment to PUBLISHED. central-publishing-maven-plugin
# logs "deploymentId: <uuid>" on upload — extract it, POST it to the Central
# Portal publish endpoint, then poll until the deployment leaves VALIDATED.
# Strict UUID shape: 8-4-4-4-12 hex (java.util.UUID.randomUUID() is lowercase
# but accept both cases defensively); validate the captured value before any
# API call.
DEPLOYMENT_ID=$(grep -oE 'deploymentId: [0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b' "$DEPLOY_LOG" \
  | head -1 | awk '{print $2}')
rm -f "$DEPLOY_LOG"
_mp_cleanup_secrets
trap - EXIT
if [[ ! "$DEPLOYMENT_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
  log_error "Could not extract a well-formed deploymentId from mvn deploy output (got: '${DEPLOYMENT_ID:-<empty>}')"
  exit 1
fi
log_info "Central Portal deployment: $DEPLOYMENT_ID"

# Write Sonatype credentials to a 0600 netrc-format file for curl.
CP_NETRC="$REPO_ROOT/.tmp/sonatype-netrc-plugin.$$"
( umask 077
  set +x
  _user=$(load_secret "mockserver-build/sonatype" "username")
  _pass=$(load_secret "mockserver-build/sonatype" "password")
  printf 'machine central.sonatype.com\nlogin %s\npassword %s\n' "$_user" "$_pass" > "$CP_NETRC"
)
trap 'rm -f "$CP_NETRC"' EXIT

log_info "Publishing deployment $DEPLOYMENT_ID"
mkdir -p "$REPO_ROOT/.tmp"
publish_response_file=$(mktemp "$REPO_ROOT/.tmp/mockserver-maven-plugin-publish.XXXXXX")
publish_http=$(curl -sS --connect-timeout 10 --max-time 30 -X POST --netrc-file "$CP_NETRC" \
  -o "$publish_response_file" -w '%{http_code}' \
  "https://central.sonatype.com/api/v1/publisher/deployment/$DEPLOYMENT_ID" 2>/dev/null || echo "000")
if [[ "$publish_http" != "204" && "$publish_http" != "200" ]]; then
  log_error "Failed to publish deployment $DEPLOYMENT_ID (HTTP $publish_http)"
  cat "$publish_response_file" || true
  rm -f "$publish_response_file"
  exit 1
fi
rm -f "$publish_response_file"
log_info "  publish acknowledged (HTTP $publish_http)"

log_info "Waiting for the deployment to leave VALIDATED"
state=""
published=false
for i in $(seq 1 20); do   # 20 × 15s = 5 min
  state=$(curl -fsS --connect-timeout 10 --max-time 30 -X POST --netrc-file "$CP_NETRC" \
    "https://central.sonatype.com/api/v1/publisher/status?id=$DEPLOYMENT_ID" \
    2>/dev/null | jq -r '.deploymentState' 2>/dev/null || true)
  log_info "  attempt $i: state=${state:-<empty>}"
  case "$state" in
    PUBLISHING|PUBLISHED) published=true; break ;;
    FAILED)               log_error "Central Portal deployment FAILED"; exit 1 ;;
  esac
  sleep 15
done
if ! $published; then
  log_error "Deployment $DEPLOYMENT_ID did not start publishing within 5 minutes"
  exit 1
fi

log_info "maven-plugin release complete (deployment $DEPLOYMENT_ID is $state)"
