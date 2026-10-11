#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# The headline text Jekyll does not template must agree with _data/perf_figures.json.
"$SCRIPT_DIR/website-perf-headline-lint.sh"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i ruby:3.3 \
  -w /build/jekyll-www.mock-server.com \
  --cache bundler \
  -e "JEKYLL_ENV=production" \
  -- bash -c 'bundle install && bundle exec jekyll build'
