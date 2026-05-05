#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i ruby:3.3 \
  -w /build/jekyll-www.mock-server.com \
  -e "JEKYLL_ENV=production" \
  -- bash -c 'bundle install && bundle exec jekyll build'
