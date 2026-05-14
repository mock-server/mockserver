#!/usr/bin/env bash
# Release the core Maven artifacts to Maven Central.
#
# Steps:
#   1. Pull origin/master so we see the version bump from prepare.sh.
#   2. Build + test the release version (mvn clean install).
#   3. Deploy signed artifacts to the Central Portal staging.
#   4. Poll the Portal until validation passes.
#   5. Publish (promote staging to release).
#   6. Wait for the artifacts to be searchable on Maven Central.
#
# Dry-run mode: builds and (locally) signs, but skips the Sonatype upload,
# publish, and sync wait. Good enough to validate the build + sign work.

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
skip_unless_release_type "maven-central" full,maven-only

log_step "Release Maven Central artifacts $RELEASE_VERSION (dry-run=$DRY_RUN)"

sync_to_origin_master

# ---- Build & test ----------------------------------------------------------
# Build (and run unit tests). In dry-run we skip tests — they take 5-10
# minutes and may be flaky in a one-off container. Real release runs them.
SKIP_TESTS_FLAG=""
if is_dry_run; then
  SKIP_TESTS_FLAG="-DskipTests"
  log_info "Build $RELEASE_VERSION (Maven in Docker, tests skipped in dry-run)"
else
  log_info "Build + test $RELEASE_VERSION (Maven in Docker)"
fi
in_maven -w /build/mockserver \
  -- mvn -T 1C clean install $SKIP_TESTS_FLAG -Djava.security.egd=file:/dev/./urandom

# ---- Deploy + sign ---------------------------------------------------------
# The passphrase is delivered to Maven via the `central-portal` server's
# <passphrase> element in the generated settings.xml, NOT via the
# -Dgpg.passphrase command-line property — that would expose the passphrase
# in the container's process list and in any tracing output.
if is_dry_run; then
  log_dry "skip: deploy to Sonatype Central Portal"
  log_dry "skip: GPG-sign and upload artifacts"
else
  log_info "Deploy to Sonatype with GPG signing"
  GPG_KEY_B64=$(load_secret "mockserver-release/gpg-key" "key")
  GPG_PASSPHRASE=$(load_secret "mockserver-release/gpg-key" "passphrase")
  SONATYPE_USERNAME=$(load_secret "mockserver-build/sonatype" "username")
  SONATYPE_PASSWORD=$(load_secret "mockserver-build/sonatype" "password")

  in_docker "$MAVEN_IMAGE" \
    -w /build/mockserver \
    -v mockserver-m2-cache:/root/.m2 \
    -e "GPG_KEY_B64=$GPG_KEY_B64" \
    -e "GPG_PASSPHRASE=$GPG_PASSPHRASE" \
    -e "SONATYPE_USERNAME=$SONATYPE_USERNAME" \
    -e "SONATYPE_PASSWORD=$SONATYPE_PASSWORD" \
    -- bash -ec '
      apt-get update -qq >/dev/null
      apt-get install -y -qq gnupg >/dev/null
      set +x
      echo "$GPG_KEY_B64" | base64 -d | gpg --batch --import
      mkdir -p ~/.gnupg
      echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf
      gpgconf --reload gpg-agent 2>/dev/null || true
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
      mvn deploy -P release -DskipTests \
        -Dgpg.passphraseServerId=gpg.passphrase \
        -Dgpg.useagent=false \
        --settings /tmp/settings.xml
    '
fi

# ---- Helpers for the Central Portal API -----------------------------------
# Use a single auth header that's stripped of any line wrapping. GNU base64
# wraps at 76 chars by default; the wrapped form breaks curl's header parse.
central_portal_auth() {
  local user pass
  user=$(load_secret "mockserver-build/sonatype" "username")
  pass=$(load_secret "mockserver-build/sonatype" "password")
  printf "%s:%s" "$user" "$pass" | base64 | tr -d '\n'
}

if ! is_dry_run; then
  CP_AUTH=$(central_portal_auth)
fi

# ---- Poll for validation ---------------------------------------------------
if is_dry_run; then
  log_dry "skip: poll Central Portal validation"
else
  log_info "Polling Central Portal for validation result"
  TIMEOUT_ITERATIONS=60   # 60 × 30s = 30 minutes
  validation_status=""
  for i in $(seq 1 "$TIMEOUT_ITERATIONS"); do
    response=$(curl -sf \
      -H "Authorization: Basic $CP_AUTH" \
      "https://central.sonatype.com/api/v1/publisher/deployment/list?namespace=org.mock-server" \
      2>/dev/null || true)
    # Most recent deployment for this version (head -1 in case of retries).
    validation_status=$(echo "$response" \
      | jq -r ".deployments[] | select(.version == \"$RELEASE_VERSION\") | .deploymentState" 2>/dev/null \
      | head -1 || true)
    log_info "  attempt $i: state=${validation_status:-<empty>}"
    case "$validation_status" in
      VALIDATED|PUBLISHING|PUBLISHED)
        log_info "Validation passed: $validation_status"
        break ;;
      FAILED)
        log_error "Validation FAILED"; echo "$response"; exit 1 ;;
    esac
    sleep 30
  done
  # Loop exhaustion — explicit timeout failure rather than silent fall-through.
  case "$validation_status" in
    VALIDATED|PUBLISHING|PUBLISHED) ;;
    *)
      log_error "Central Portal validation timed out after $((TIMEOUT_ITERATIONS * 30))s (last state=${validation_status:-<empty>})"
      exit 1 ;;
  esac
fi

# ---- Publish (promote) -----------------------------------------------------
if is_dry_run; then
  log_dry "skip: publish (promote staging to release)"
else
  # The validation poll may have observed the state already at PUBLISHING /
  # PUBLISHED if the Portal auto-published. Only call POST publish if we're
  # still at VALIDATED.
  if [[ "$validation_status" == "VALIDATED" ]]; then
    log_info "Publishing to Maven Central"
    DEPLOYMENT_ID=$(curl -sf \
      -H "Authorization: Basic $CP_AUTH" \
      "https://central.sonatype.com/api/v1/publisher/deployment/list?namespace=org.mock-server" \
      | jq -r ".deployments[] | select(.version == \"$RELEASE_VERSION\" and .deploymentState == \"VALIDATED\") | .deploymentId" \
      | head -1)
    if [[ -z "$DEPLOYMENT_ID" ]]; then
      log_error "Could not find VALIDATED deployment for $RELEASE_VERSION"
      exit 1
    fi
    log_info "Publishing deployment $DEPLOYMENT_ID"
    curl -fS -X POST \
      -H "Authorization: Basic $CP_AUTH" \
      "https://central.sonatype.com/api/v1/publisher/deployment/$DEPLOYMENT_ID"
  else
    log_info "Skipping publish — Portal state already $validation_status"
  fi
fi

# ---- Wait for Maven Central sync ------------------------------------------
if is_dry_run; then
  log_dry "skip: wait for Maven Central sync"
else
  log_info "Waiting for Maven Central sync of $RELEASE_VERSION"
  url="https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/$RELEASE_VERSION/mockserver-netty-$RELEASE_VERSION.pom"
  SYNC_TIMEOUT_ITERATIONS=120  # 120 × 60s = 2 hours
  synced=false
  for i in $(seq 1 "$SYNC_TIMEOUT_ITERATIONS"); do
    if curl -sf -o /dev/null -I "$url"; then
      log_info "Synced ($RELEASE_VERSION found at $url)"
      synced=true
      break
    fi
    log_info "  attempt $i: not yet visible"
    sleep 60
  done
  if ! $synced; then
    log_error "Maven Central sync timed out after $((SYNC_TIMEOUT_ITERATIONS * 60))s for $url"
    exit 1
  fi
fi

log_info "Maven Central release complete"
