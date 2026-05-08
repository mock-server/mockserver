#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd npm
require_cmd grunt

PKG_DIR="${1:?Usage: publish-npm.sh <mockserver-node|mockserver-client-node>}"

if [[ "$PKG_DIR" != "mockserver-node" && "$PKG_DIR" != "mockserver-client-node" ]]; then
  log_error "Argument must be mockserver-node or mockserver-client-node"
  exit 1
fi

log_step "Publishing $PKG_DIR $RELEASE_VERSION to npm"

cd "$REPO_ROOT/$PKG_DIR"

log_info "Cleaning"
rm -rf package-lock.json node_modules

log_info "Installing dependencies"
npm i

if [[ "$PKG_DIR" == "mockserver-node" ]]; then
  log_info "Running audit fix and grunt"
  npm audit fix 2>/dev/null || true
  grunt
else
  log_info "Running grunt"
  grunt
fi

log_info "Committing build artifacts"
cd "$REPO_ROOT"
git add -A
git commit -m "release: publish $PKG_DIR $RELEASE_VERSION" || true
git push origin master

log_info "Tagging $PKG_DIR-$RELEASE_VERSION"
git tag "$PKG_DIR-$RELEASE_VERSION"
git push origin "$PKG_DIR-$RELEASE_VERSION"

log_info "Publishing to npm (interactive OTP required)"
cd "$REPO_ROOT/$PKG_DIR"
read -rp "Enter npm OTP: " OTP
npm publish --access=public --otp="$OTP"

log_info "Published $PKG_DIR $RELEASE_VERSION to npm"
