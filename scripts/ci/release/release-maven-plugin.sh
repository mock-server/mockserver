#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker
require_cmd git
require_cmd python3
require_cmd aws
require_cmd jq

log_step "Releasing mockserver-maven-plugin $RELEASE_VERSION"

CURRENT_SNAPSHOT="${RELEASE_VERSION}-SNAPSHOT"
PLUGIN_DIR="$REPO_ROOT/mockserver-maven-plugin"
PLUGIN_POM="$PLUGIN_DIR/pom.xml"

log_info "Updating dependency versions from SNAPSHOT to release"
sed_i "s|<version>${CURRENT_SNAPSHOT}</version>|<version>${RELEASE_VERSION}</version>|g" "$PLUGIN_POM"

log_info "Building core MockServer first (in Docker)"
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -w /build/mockserver \
  -v mockserver-m2-cache:/root/.m2 \
  -- mvn clean install -DskipTests

log_info "Verifying maven-plugin (in Docker)"
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -w /build/mockserver-maven-plugin \
  -v mockserver-m2-cache:/root/.m2 \
  -- mvn clean verify

log_info "Committing dependency version update"
cd "$REPO_ROOT"
git add "$PLUGIN_POM"
git commit -m "release: maven-plugin dependencies $RELEASE_VERSION"

log_info "Setting maven-plugin version to $RELEASE_VERSION"
python3 - "$CURRENT_SNAPSHOT" "$RELEASE_VERSION" "$PLUGIN_POM" << 'PYEOF'
import sys, pathlib
old_v, new_v, path = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
text = path.read_text()
old_tag = f"<version>{old_v}</version>"
new_tag = f"<version>{new_v}</version>"
if old_tag not in text:
    print(f"ERROR: {old_tag} not in {path}", file=sys.stderr)
    sys.exit(1)
path.write_text(text.replace(old_tag, new_tag))
print(f"  updated {path}: {old_v} -> {new_v}")
PYEOF

cd "$REPO_ROOT"
git add "$PLUGIN_POM"
git commit -m "release: set maven-plugin version $RELEASE_VERSION"

log_info "Tagging maven-plugin"
git tag "maven-plugin-$RELEASE_VERSION"
git push origin HEAD:master
git push origin "maven-plugin-$RELEASE_VERSION"

log_info "Deploying maven-plugin release (in Docker with GPG)"
GPG_KEY_B64=$(load_secret "mockserver-release/gpg-key" "key")
GPG_PASSPHRASE=$(load_secret "mockserver-release/gpg-key" "passphrase")
SONATYPE_USERNAME=$(load_secret "mockserver-build/sonatype" "username")
SONATYPE_PASSWORD=$(load_secret "mockserver-build/sonatype" "password")

"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -w /build/mockserver-maven-plugin \
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

log_info "Setting maven-plugin to next SNAPSHOT"
python3 - "$RELEASE_VERSION" "$NEXT_VERSION" "$PLUGIN_POM" << 'PYEOF'
import sys, pathlib
old_v, new_v, path = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
text = path.read_text()
old_tag = f"<version>{old_v}</version>"
new_tag = f"<version>{new_v}</version>"
if old_tag not in text:
    print(f"ERROR: {old_tag} not in {path}", file=sys.stderr)
    sys.exit(1)
path.write_text(text.replace(old_tag, new_tag))
print(f"  updated {path}: {old_v} -> {new_v}")
PYEOF

log_info "Deploying maven-plugin SNAPSHOT"
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -w /build/mockserver-maven-plugin \
  -v mockserver-m2-cache:/root/.m2 \
  -e "SONATYPE_USERNAME=$SONATYPE_USERNAME" \
  -e "SONATYPE_PASSWORD=$SONATYPE_PASSWORD" \
  -- mvn clean deploy -DskipTests --settings /build/mockserver/.buildkite-settings.xml

cd "$REPO_ROOT"
git add "$PLUGIN_POM"
git commit -m "release: set maven-plugin next version $NEXT_VERSION"
git push origin HEAD:master

log_info "mockserver-maven-plugin $RELEASE_VERSION released"
