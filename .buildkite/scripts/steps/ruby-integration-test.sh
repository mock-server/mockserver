#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i ruby:3.3 \
  -w /build/mockserver-client-ruby \
  -s \
  -- bash -c 'apt-get update -qq && apt-get install -y -qq docker.io >/dev/null 2>&1 && bundle install && bundle exec rspec --tag integration -f documentation'
