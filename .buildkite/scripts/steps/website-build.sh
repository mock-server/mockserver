#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i jekyll/builder:latest \
  -w /build/jekyll-www.mock-server.com \
  -e "JEKYLL_ENV=production" \
  -- bash -c 'bundle install && bundle exec jekyll build'
