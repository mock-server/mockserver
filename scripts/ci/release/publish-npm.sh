#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker
require_cmd git
require_cmd aws
require_cmd jq

PKG_DIR="${1:?Usage: publish-npm.sh <mockserver-node|mockserver-client-node>}"

if [[ "$PKG_DIR" != "mockserver-node" && "$PKG_DIR" != "mockserver-client-node" ]]; then
  log_error "Argument must be mockserver-node or mockserver-client-node"
  exit 1
fi

log_step "Publishing $PKG_DIR $RELEASE_VERSION to npm"

log_info "Fetching npm automation token"
NPM_TOKEN=$(load_secret "mockserver-release/npm-token" "token")
if [[ -z "$NPM_TOKEN" || "$NPM_TOKEN" == "null" ]]; then
  log_error "npm token is missing from mockserver-release/npm-token"
  exit 1
fi

log_info "Building package in Node container (install + grunt)"
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$NODE_IMAGE" \
  -w "/build/$PKG_DIR" \
  -e "PKG_DIR=$PKG_DIR" \
  -- bash -ec '
    rm -rf package-lock.json node_modules

    attempts=0
    until npm i; do
      attempts=$((attempts + 1))
      if [ "$attempts" -ge 5 ]; then
        echo "npm install failed after ${attempts} attempts"
        exit 1
      fi
      echo "npm install failed, retrying in 15s"
      sleep 15
    done

    if [ "$PKG_DIR" = "mockserver-node" ]; then
      npm audit fix 2>/dev/null || true
      npx grunt
    else
      npx grunt
    fi
  '

log_info "Committing build artifacts"
cd "$REPO_ROOT"
git add "$PKG_DIR"
git commit -m "release: publish $PKG_DIR $RELEASE_VERSION" || true
git push origin HEAD:master

log_info "Tagging $PKG_DIR-$RELEASE_VERSION"
git tag "$PKG_DIR-$RELEASE_VERSION"
git push origin "$PKG_DIR-$RELEASE_VERSION"

log_info "Publishing to npm (tag + commit already pushed)"
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$NODE_IMAGE" \
  -w "/build/$PKG_DIR" \
  -e "NPM_TOKEN=$NPM_TOKEN" \
  -- bash -ec '
    set +x
    cat > /tmp/.npmrc <<NPMRC
//registry.npmjs.org/:_authToken=${NPM_TOKEN}
registry=https://registry.npmjs.org/
always-auth=true
NPMRC
    export NPM_CONFIG_USERCONFIG=/tmp/.npmrc

    npm whoami >/dev/null || { echo "npm authentication failed"; exit 1; }
    npm publish --access=public
  '

log_info "Published $PKG_DIR $RELEASE_VERSION to npm"
