#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd ruby
require_cmd gem
require_cmd curl

log_step "Publishing Ruby client $RELEASE_VERSION to RubyGems"

RUBY_DIR="$REPO_ROOT/mockserver-client-ruby"

VERSION=$(ruby -e "load '$RUBY_DIR/lib/mockserver/version.rb'; puts MockServer::VERSION")
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

log_info "Building gem"
cd "$RUBY_DIR"
gem build mockserver-client.gemspec

log_info "Fetching RubyGems API key"
GEM_HOST_API_KEY=$(load_secret "mockserver-build/rubygems" "api_key")

log_info "Pushing to RubyGems"
(
  set +x
  GEM_HOST_API_KEY="$GEM_HOST_API_KEY" \
  gem push mockserver-client-"$VERSION".gem
)

log_info "Published mockserver-client $VERSION to RubyGems"
