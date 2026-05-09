#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd gpg

log_step "Releasing mockserver-maven-plugin $RELEASE_VERSION"

cd "$REPO_ROOT"

CURRENT_SNAPSHOT="${RELEASE_VERSION}-SNAPSHOT"
PLUGIN_DIR="$REPO_ROOT/mockserver-maven-plugin"
PLUGIN_POM="$PLUGIN_DIR/pom.xml"

log_info "Updating dependency versions from SNAPSHOT to release"
sed_i "s|<version>${CURRENT_SNAPSHOT}</version>|<version>${RELEASE_VERSION}</version>|g" "$PLUGIN_POM"

log_info "Building core MockServer first"
cd "$REPO_ROOT/mockserver"
./mvnw clean install -DskipTests

log_info "Verifying maven-plugin"
cd "$PLUGIN_DIR"
./mvnw clean verify

log_info "Committing dependency version update"
cd "$REPO_ROOT"
git add "$PLUGIN_POM"
git commit -m "release: maven-plugin dependencies $RELEASE_VERSION"

log_info "Setting maven-plugin version to $RELEASE_VERSION"
cd "$PLUGIN_DIR"
./mvnw versions:set -DnewVersion="$RELEASE_VERSION" -DgenerateBackupPoms=false
./mvnw versions:commit

cd "$REPO_ROOT"
git add -A
git commit -m "release: set maven-plugin version $RELEASE_VERSION"

log_info "Tagging maven-plugin"
git tag "maven-plugin-$RELEASE_VERSION"
git push origin master
git push origin "maven-plugin-$RELEASE_VERSION"

log_info "Deploying maven-plugin release"

GPG_KEY_B64=$(load_secret "mockserver-release/gpg-key" "key")
GPG_PASSPHRASE=$(load_secret "mockserver-release/gpg-key" "passphrase")

(
  set +x
  echo "$GPG_KEY_B64" | base64 -d | gpg --batch --import
)

mkdir -p ~/.gnupg
echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf
gpgconf --reload gpg-agent 2>/dev/null || true

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

cd "$PLUGIN_DIR"
(
  set +x
  ./mvnw deploy -P release -DskipTests \
    -Dgpg.passphrase="$GPG_PASSPHRASE" \
    -Dgpg.useagent=false \
    --settings "$SETTINGS_FILE"
)

log_info "Setting maven-plugin to next SNAPSHOT"
./mvnw versions:set -DnewVersion="$NEXT_VERSION" -DgenerateBackupPoms=false
./mvnw versions:commit

sed_i "s|<version>${RELEASE_VERSION}</version>|<version>${NEXT_VERSION}</version>|g" "$PLUGIN_POM"

log_info "Deploying SNAPSHOT"
if is_ci; then
  SONATYPE_USERNAME="$SONATYPE_USERNAME" \
  SONATYPE_PASSWORD="$SONATYPE_PASSWORD" \
  ./mvnw clean deploy -DskipTests \
    --settings "$REPO_ROOT/mockserver/.buildkite-settings.xml"
else
  ./mvnw clean deploy -DskipTests \
    --settings "$REPO_ROOT/mockserver/.buildkite-settings.xml"
fi

cd "$REPO_ROOT"
git add -A
git commit -m "release: set maven-plugin next version $NEXT_VERSION"
git push origin master

log_info "mockserver-maven-plugin $RELEASE_VERSION released"
