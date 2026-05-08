#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/ci/release" && pwd)"

RELEASE_VERSION="${1:?Usage: $0 <release-version> <next-snapshot> <old-version>}"
NEXT_VERSION="${2:?Usage: $0 <release-version> <next-snapshot> <old-version>}"
OLD_VERSION="${3:?Usage: $0 <release-version> <next-snapshot> <old-version>}"
export RELEASE_VERSION NEXT_VERSION OLD_VERSION

echo "=== MockServer Release $RELEASE_VERSION ==="
echo "Next SNAPSHOT: $NEXT_VERSION"
echo "Old version:   $OLD_VERSION"
echo

"$SCRIPT_DIR/verify-totp.sh"
"$SCRIPT_DIR/validate.sh"

"$SCRIPT_DIR/set-release-version.sh"
"$SCRIPT_DIR/build-and-test.sh"
read -rp "Build passed. Deploy to Central Portal? [y/N] " c; [[ "$c" == [yY] ]] || exit 1
"$SCRIPT_DIR/deploy-release.sh"
"$SCRIPT_DIR/poll-central-portal.sh"
read -rp "Validated. Review at https://central.sonatype.com/publishing — Publish? [y/N] " c; [[ "$c" == [yY] ]] || exit 1
"$SCRIPT_DIR/publish-central-portal.sh"
"$SCRIPT_DIR/wait-for-central.sh"

"$SCRIPT_DIR/deploy-snapshot.sh"
"$SCRIPT_DIR/update-versions.sh"

"$SCRIPT_DIR/publish-npm.sh" mockserver-node
"$SCRIPT_DIR/publish-npm.sh" mockserver-client-node

"$SCRIPT_DIR/release-maven-plugin.sh"

"$SCRIPT_DIR/publish-docker.sh" &
"$SCRIPT_DIR/publish-helm.sh" &
"$SCRIPT_DIR/publish-javadoc.sh" &
"$SCRIPT_DIR/update-swaggerhub.sh" &
"$SCRIPT_DIR/publish-website.sh" &
"$SCRIPT_DIR/publish-pypi.sh" &
"$SCRIPT_DIR/publish-rubygems.sh" &
"$SCRIPT_DIR/github-release.sh" &
wait

read -rp "Create versioned site? [y/N] " c
[[ "$c" == [yY] ]] && "$SCRIPT_DIR/create-versioned-site.sh"

read -rp "Update Homebrew? [y/N] " c
[[ "$c" == [yY] ]] && "$SCRIPT_DIR/update-homebrew.sh"

echo "=== Release $RELEASE_VERSION complete ==="
