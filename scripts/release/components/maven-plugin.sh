#!/usr/bin/env bash
# Release the mockserver-maven-plugin to Maven Central.
#
# Flow:
#   1. Bump plugin dependency on core mockserver from SNAPSHOT -> release version.
#   2. Build core mockserver locally (so the plugin can resolve it).
#   3. Verify the plugin builds.
#   4. Bump plugin's own version from SNAPSHOT -> release version, commit, tag, push.
#   5. mvn deploy -P release (GPG sign + Sonatype upload).
#   6. Bump plugin to next SNAPSHOT, commit, push.
#
# Dry-run: do steps 1-3 (build + verify), skip steps 4-6.

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
require_cmd git
require_cmd python3
require_release_inputs
skip_unless_release_type "maven-plugin" full,post-maven

log_step "Release mockserver-maven-plugin $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

CURRENT_SNAPSHOT="${RELEASE_VERSION}-SNAPSHOT"
PLUGIN_DIR="$REPO_ROOT/mockserver-maven-plugin"
PLUGIN_POM="$PLUGIN_DIR/pom.xml"

log_info "Bump plugin dependency: $CURRENT_SNAPSHOT -> $RELEASE_VERSION"
if is_dry_run; then
  log_dry "would: sed -i ... pom.xml dependency version"
else
  python3 - "$CURRENT_SNAPSHOT" "$RELEASE_VERSION" "$PLUGIN_POM" << 'PYEOF'
import sys, pathlib
old, new, path = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
text = path.read_text()
old_tag = f"<version>{old}</version>"
new_tag = f"<version>{new}</version>"
if old_tag not in text:
    print(f"WARN: {old_tag} not in {path}", file=sys.stderr)
else:
    path.write_text(text.replace(old_tag, new_tag))
    print(f"  updated {path}: {old} -> {new}")
PYEOF
fi

log_info "Build core MockServer (in Docker)"
in_docker "$MAVEN_IMAGE" \
  -w /build/mockserver \
  -v mockserver-m2-cache:/root/.m2 \
  -- mvn clean install -DskipTests

log_info "Verify maven-plugin (in Docker)"
in_docker "$MAVEN_IMAGE" \
  -w /build/mockserver-maven-plugin \
  -v mockserver-m2-cache:/root/.m2 \
  -- mvn clean verify

if is_dry_run; then
  log_dry "skip: commit, tag, deploy, snapshot bump, push"
  log_info "maven-plugin dry-run complete"
  exit 0
fi

git_commit_and_push "release: maven-plugin dependencies $RELEASE_VERSION" \
  "mockserver-maven-plugin/pom.xml"

log_info "Bump maven-plugin version: $CURRENT_SNAPSHOT -> $RELEASE_VERSION"
update_pom_versions "$PLUGIN_DIR" "$CURRENT_SNAPSHOT" "$RELEASE_VERSION"

git_commit_and_push "release: set maven-plugin version $RELEASE_VERSION" \
  "mockserver-maven-plugin/pom.xml"
git_tag_and_push "maven-plugin-$RELEASE_VERSION"

log_info "Deploy maven-plugin to Sonatype (GPG-sign in container)"
GPG_KEY_B64=$(load_secret "mockserver-release/gpg-key" "key")
GPG_PASSPHRASE=$(load_secret "mockserver-release/gpg-key" "passphrase")
SONATYPE_USERNAME=$(load_secret "mockserver-build/sonatype" "username")
SONATYPE_PASSWORD=$(load_secret "mockserver-build/sonatype" "password")

in_docker "$MAVEN_IMAGE" \
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

log_info "Bump maven-plugin to next SNAPSHOT $NEXT_VERSION"
update_pom_versions "$PLUGIN_DIR" "$RELEASE_VERSION" "$NEXT_VERSION"
git_commit_and_push "release: maven-plugin next $NEXT_VERSION" \
  "mockserver-maven-plugin/pom.xml"

log_info "maven-plugin release complete"
