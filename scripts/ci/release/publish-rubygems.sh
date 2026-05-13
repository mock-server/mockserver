#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker
require_cmd curl
require_cmd jq

log_step "Publishing Ruby client $RELEASE_VERSION to RubyGems"

RUBY_DIR="$REPO_ROOT/mockserver-client-ruby"

log_info "Reading version from version.rb (in Docker)"
VERSION=$("$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$RUBY_IMAGE" \
  -w /build/mockserver-client-ruby \
  -- ruby -e "load 'lib/mockserver/version.rb'; puts MockServer::VERSION" | tail -1)
log_info "Version from version.rb: $VERSION"

log_info "Checking if version already exists on RubyGems"
http_code=$(curl -s -o /dev/null -w "%{http_code}" "https://rubygems.org/api/v1/versions/mockserver-client.json")
case "$http_code" in
  200)
    if curl -sf "https://rubygems.org/api/v1/versions/mockserver-client.json" | jq -e ".[] | select(.number == \"$VERSION\")" >/dev/null 2>&1; then
      log_error "Version $VERSION already exists on RubyGems"
      exit 1
    fi
    ;;
  *) log_error "RubyGems returned HTTP $http_code"; exit 1 ;;
esac

log_info "Cleaning previous builds"
rm -f "$RUBY_DIR"/mockserver-client-*.gem

log_info "Fetching RubyGems API key"
GEM_HOST_API_KEY=$(load_secret "mockserver-build/rubygems" "api_key")

log_info "Building and pushing gem (in Docker)"
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$RUBY_IMAGE" \
  -w /build/mockserver-client-ruby \
  -e "GEM_HOST_API_KEY=$GEM_HOST_API_KEY" \
  -e "VERSION=$VERSION" \
  -- bash -ec '
    gem build mockserver-client.gemspec
    set +x
    gem push "mockserver-client-${VERSION}.gem"
  '

log_info "Published mockserver-client $VERSION to RubyGems"
