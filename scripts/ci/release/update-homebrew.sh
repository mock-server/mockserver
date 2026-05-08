#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd brew

log_step "Updating Homebrew formula for $RELEASE_VERSION"

GITHUB_TOKEN=$(load_secret "mockserver-release/github-token" "token")

brew doctor || true
brew update

log_info "Bumping mockserver formula"
HOMEBREW_GITHUB_API_TOKEN="$GITHUB_TOKEN" \
  brew bump-formula-pr --strict mockserver \
  --url="https://search.maven.org/remotecontent?filepath=org/mock-server/mockserver-netty/$RELEASE_VERSION/mockserver-netty-$RELEASE_VERSION-brew-tar.tar"

log_info "Homebrew formula PR created for $RELEASE_VERSION"
