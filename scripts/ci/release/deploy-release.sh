#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd gpg

log_step "Deploying release $RELEASE_VERSION to Central Portal"

log_info "Importing GPG key"
GPG_KEY_B64=$(load_secret "mockserver-release/gpg-key" "key")
GPG_PASSPHRASE=$(load_secret "mockserver-release/gpg-key" "passphrase")

(
  set +x
  echo "$GPG_KEY_B64" | base64 -d | gpg --batch --import
)

mkdir -p ~/.gnupg
echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf
gpgconf --reload gpg-agent 2>/dev/null || true

log_info "Generating release settings.xml"
SONATYPE_USERNAME=$(load_secret "mockserver-build/sonatype" "username")
SONATYPE_PASSWORD=$(load_secret "mockserver-build/sonatype" "password")

SETTINGS_FILE="$REPO_ROOT/.tmp/release-settings.xml"
mkdir -p "$REPO_ROOT/.tmp"
(
  set +x
  cat > "$SETTINGS_FILE" <<SETTINGS_EOF
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
SETTINGS_EOF
)

cleanup() {
  rm -f "$SETTINGS_FILE"
  gpg --batch --yes --delete-secret-and-public-key "$(gpg --list-secret-keys --keyid-format long 2>/dev/null | grep -E '^sec' | head -1 | awk '{print $2}' | cut -d/ -f2)" 2>/dev/null || true
}
trap cleanup EXIT

log_info "Deploying with GPG signing"
cd "$REPO_ROOT/mockserver"
(
  set +x
  ./mvnw deploy -P release -DskipTests \
    -Dgpg.passphrase="$GPG_PASSPHRASE" \
    -Dgpg.useagent=false \
    --settings "$SETTINGS_FILE"
)

log_info "Release deployed to Central Portal"
log_info "Review at https://central.sonatype.com/publishing/deployments"
log_info "Use poll-central-portal.sh to check validation status"
