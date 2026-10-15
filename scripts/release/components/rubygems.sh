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

# In dry-run, update-version-references (which bumps version.rb) is skipped and
# its bump would never reach this step's fresh checkout anyway, so the file still
# holds the previous version. Bump it in-place to RELEASE_VERSION so the dry-run
# builds the real version; restore on exit (dry-run never commits).
VERSION_RB_FILE="$RUBY_DIR/lib/mockserver/version.rb"
if is_dry_run && [[ "$VERSION" != "$RELEASE_VERSION" ]]; then
  mkdir -p "$REPO_ROOT/.tmp"
  cp "$VERSION_RB_FILE" "$REPO_ROOT/.tmp/version.rb.bak"
  # shellcheck disable=SC2064  # expand the path now, not at trap-fire time
  trap "cp '$REPO_ROOT/.tmp/version.rb.bak' '$VERSION_RB_FILE' 2>/dev/null || true" EXIT
  _newrb="$REPO_ROOT/.tmp/version.rb.new"
  # write in place, not mv: a replaced inode may not be visible yet to the next
  # Docker Desktop bind-mount container, whose gem build then cannot load it
  sed "s/VERSION = '.*'/VERSION = '$RELEASE_VERSION'/" "$VERSION_RB_FILE" > "$_newrb" \
    && cat "$_newrb" > "$VERSION_RB_FILE" && rm -f "$_newrb"
  grep -qE "VERSION = '$RELEASE_VERSION'" "$VERSION_RB_FILE" \
    || { log_error "dry-run: failed to bump version.rb (format changed?)"; exit 1; }
  VERSION="$RELEASE_VERSION"
  log_info "dry-run: bumped version.rb to $RELEASE_VERSION in-place (not committed)"
fi

# Fail-fast version guard. Must run BEFORE the "already on RubyGems"
# idempotency check, otherwise a stale version.rb (e.g. prepare.sh didn't
# bump it) would silently skip — masking the bug behind an "already
# published" message. The idempotency check still preserves re-runnability
# for the happy path where the source file IS at $RELEASE_VERSION.
if [[ "$VERSION" != "$RELEASE_VERSION" ]]; then
  log_error "version.rb VERSION ($VERSION) does not match RELEASE_VERSION ($RELEASE_VERSION) — refusing to publish wrong version"
  exit 1
fi

if ! is_dry_run; then
  log_info "Check RubyGems for existing $VERSION"
  http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    "https://rubygems.org/api/v1/versions/mockserver-client.json")
  case "$http_code" in
    200)
      if curl -sf "https://rubygems.org/api/v1/versions/mockserver-client.json" \
           | jq -e ".[] | select(.number == \"$VERSION\")" >/dev/null 2>&1; then
        # Idempotent: an already-published version means a prior run did this.
        log_info "mockserver-client $VERSION already on RubyGems - skipping"
        exit 0
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
    --secret-env "GEM_HOST_API_KEY=$GEM_HOST_API_KEY" \
    -e "VERSION=$VERSION" \
    -- bash -ec '
      set +x
      gem push "mockserver-client-${VERSION}.gem"
    '
fi

log_info "RubyGems publish complete"
