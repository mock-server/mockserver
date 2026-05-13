#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker
require_cmd aws
require_cmd jq

log_step "Deploying release $RELEASE_VERSION to Central Portal"

log_info "Loading release secrets"
GPG_KEY_B64=$(load_secret "mockserver-release/gpg-key" "key")
GPG_PASSPHRASE=$(load_secret "mockserver-release/gpg-key" "passphrase")
SONATYPE_USERNAME=$(load_secret "mockserver-build/sonatype" "username")
SONATYPE_PASSWORD=$(load_secret "mockserver-build/sonatype" "password")

# Run GPG import + Maven deploy in a single container so they share the
# gnupg keyring. Settings.xml is generated inside the container too so
# credentials never touch the host filesystem.
log_info "Running gpg import + mvn deploy in Maven container"
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
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

    cat > /tmp/release-settings.xml <<SETTINGS
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <servers>
    <server>
      <id>central-portal</id>
      <username>${SONATYPE_USERNAME}</username>
      <password>${SONATYPE_PASSWORD}</password>
    </server>
  </servers>
</settings>
SETTINGS

    mvn deploy -P release -DskipTests \
      -Dgpg.passphrase="$GPG_PASSPHRASE" \
      -Dgpg.useagent=false \
      --settings /tmp/release-settings.xml
  '

log_info "Release deployed to Central Portal"
log_info "Review at https://central.sonatype.com/publishing/deployments"
log_info "Use poll-central-portal.sh to check validation status"
