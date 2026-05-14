#!/usr/bin/env bash
# Publish mockserver-client gem to RubyGems.
#
# Dry-run: gem build, version check, skip gem push.

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
require_cmd curl
require_cmd jq
require_release_inputs
skip_unless_release_type "rubygems" full,post-maven

log_step "Publish RubyGems $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

RUBY_DIR="$REPO_ROOT/mockserver-client-ruby"

log_info "Read version from version.rb"
VERSION=$(in_docker "$RUBY_IMAGE" \
  -w /build/mockserver-client-ruby \
  -- ruby -e "load 'lib/mockserver/version.rb'; puts MockServer::VERSION")
[[ -n "$VERSION" ]] || { log_error "could not read version.rb"; exit 1; }
log_info "  version: $VERSION"

if ! is_dry_run; then
  log_info "Check RubyGems for existing $VERSION"
  http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    "https://rubygems.org/api/v1/versions/mockserver-client.json")
  case "$http_code" in
    200)
      if curl -sf "https://rubygems.org/api/v1/versions/mockserver-client.json" \
           | jq -e ".[] | select(.number == \"$VERSION\")" >/dev/null 2>&1; then
        log_error "Version $VERSION already on RubyGems"
        exit 1
      fi ;;
    *) log_error "RubyGems returned HTTP $http_code"; exit 1 ;;
  esac
fi

rm -f "$RUBY_DIR"/mockserver-client-*.gem 2>/dev/null || true

log_info "Build gem"
in_docker "$RUBY_IMAGE" \
  -w /build/mockserver-client-ruby \
  -- gem build mockserver-client.gemspec

if is_dry_run; then
  log_dry "skip: gem push to RubyGems"
  log_info "Built: $RUBY_DIR/mockserver-client-$VERSION.gem"
else
  log_info "Push to RubyGems"
  GEM_HOST_API_KEY=$(load_secret "mockserver-build/rubygems" "api_key")
  in_docker "$RUBY_IMAGE" \
    -w /build/mockserver-client-ruby \
    -e "GEM_HOST_API_KEY=$GEM_HOST_API_KEY" \
    -e "VERSION=$VERSION" \
    -- bash -ec '
      set +x
      gem push "mockserver-client-${VERSION}.gem"
    '
fi

log_info "RubyGems publish complete"
