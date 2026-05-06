#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RUBY_DIR="$REPO_ROOT/mockserver-client-ruby"
SECRET_ID="mockserver-build/rubygems"
REGION="eu-west-2"

for cmd in jq ruby gem aws curl; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "Missing required command: $cmd" >&2; exit 1; }
done

is_ci() { [[ -n "${BUILDKITE:-}" ]]; }

load_secret() {
  local secret_id="$1" key="$2"
  local xtrace_state
  xtrace_state=$(shopt -po xtrace 2>/dev/null || true)
  set +x
  local json
  if is_ci; then
    json=$(aws secretsmanager get-secret-value \
      --secret-id "$secret_id" \
      --region "$REGION" \
      --query SecretString --output text)
  else
    json=$(aws secretsmanager get-secret-value \
      --secret-id "$secret_id" \
      --region "$REGION" \
      --profile "${AWS_PROFILE:-mockserver-build}" \
      --query SecretString --output text)
  fi
  echo "$json" | jq -r ".$key"
  eval "$xtrace_state"
}

echo "--- Reading version"
VERSION=$(ruby -e "load '$RUBY_DIR/lib/mockserver/version.rb'; puts MockServer::VERSION")
echo "Version: $VERSION"

echo "--- Checking if version already exists on RubyGems"
http_code=$(curl -s -o /dev/null -w "%{http_code}" "https://rubygems.org/api/v1/versions/mockserver-client.json")
case "$http_code" in
  200)
    if echo "$(curl -sf "https://rubygems.org/api/v1/versions/mockserver-client.json")" | jq -e ".[] | select(.number == \"$VERSION\")" >/dev/null 2>&1; then
      echo "ERROR: Version $VERSION already exists on RubyGems" >&2
      exit 1
    fi
    ;;
  *)
    echo "ERROR: RubyGems returned HTTP $http_code while checking versions" >&2
    exit 1
    ;;
esac

echo "--- Cleaning previous builds"
rm -f "$RUBY_DIR"/mockserver-client-*.gem

echo "--- Building gem"
cd "$RUBY_DIR"
gem build mockserver-client.gemspec

echo "--- Fetching RubyGems API key from Secrets Manager"
GEM_HOST_API_KEY=$(load_secret "$SECRET_ID" "api_key")

echo "--- Pushing to RubyGems"
(
  set +x
  GEM_HOST_API_KEY="$GEM_HOST_API_KEY" \
  gem push mockserver-client-"$VERSION".gem
)

echo "--- Successfully published mockserver-client $VERSION to RubyGems"
echo "    https://rubygems.org/gems/mockserver-client/versions/$VERSION"
