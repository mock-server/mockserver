#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd npm

PKG_DIR="${1:?Usage: publish-npm.sh <mockserver-node|mockserver-client-node>}"

if [[ "$PKG_DIR" != "mockserver-node" && "$PKG_DIR" != "mockserver-client-node" ]]; then
  log_error "Argument must be mockserver-node or mockserver-client-node"
  exit 1
fi

log_step "Publishing $PKG_DIR $RELEASE_VERSION to npm"

cd "$REPO_ROOT/$PKG_DIR"

NPMRC_FILE="$REPO_ROOT/.tmp/${PKG_DIR}-npmrc"
mkdir -p "$REPO_ROOT/.tmp"

cleanup() {
  rm -f "$NPMRC_FILE"
}
trap cleanup EXIT

log_info "Fetching npm automation token"
NPM_TOKEN=$(load_secret "mockserver-release/npm-token" "token")
if [[ -z "$NPM_TOKEN" || "$NPM_TOKEN" == "null" ]]; then
  log_error "npm token is missing from mockserver-release/npm-token"
  exit 1
fi

(
  set +x
  cat > "$NPMRC_FILE" <<EOF
//registry.npmjs.org/:_authToken=${NPM_TOKEN}
registry=https://registry.npmjs.org/
always-auth=true
EOF
)

export NPM_CONFIG_USERCONFIG="$NPMRC_FILE"

log_info "Verifying npm authentication"
npm whoami >/dev/null || { log_error "npm authentication failed — check mockserver-release/npm-token"; exit 1; }

npm_install() {
  local attempts=0
  until npm i; do
    attempts=$((attempts + 1))
    if [[ "$attempts" -ge 5 ]]; then
      log_error "npm install failed after ${attempts} attempts"
      exit 1
    fi
    log_info "npm install failed, retrying in 15 seconds"
    sleep 15
  done
}

log_info "Cleaning"
rm -rf package-lock.json node_modules

log_info "Installing dependencies"
npm_install

if [[ "$PKG_DIR" == "mockserver-node" ]]; then
  log_info "Running audit fix and grunt"
  npm audit fix 2>/dev/null || true
  npx grunt
else
  log_info "Running grunt"
  npx grunt
fi

log_info "Committing build artifacts"
cd "$REPO_ROOT"
git add "$PKG_DIR"
git commit -m "release: publish $PKG_DIR $RELEASE_VERSION" || true
git push origin master

log_info "Tagging $PKG_DIR-$RELEASE_VERSION"
git tag "$PKG_DIR-$RELEASE_VERSION"
git push origin "$PKG_DIR-$RELEASE_VERSION"

log_info "Publishing to npm"
cd "$REPO_ROOT/$PKG_DIR"
npm publish --access=public

log_info "Published $PKG_DIR $RELEASE_VERSION to npm"
