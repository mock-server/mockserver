#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
cd "${SCRIPT_DIR}"

PORT="${1:-4000}"
if ! [[ "$PORT" =~ ^[0-9]+$ ]] || [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  echo "error: PORT must be 1-65535 (got: ${1:-})"
  exit 1
fi

export RBENV_VERSION="${RBENV_VERSION:-3.3.11}"

if ! rbenv which bundle >/dev/null 2>&1; then
  echo "error: rbenv Ruby ${RBENV_VERSION} not installed"
  echo "  run: rbenv install ${RBENV_VERSION} && rbenv exec gem install bundler"
  exit 1
fi

if [ ! -d vendor/bundle ] || [ Gemfile -nt vendor/bundle ] || [ Gemfile.lock -nt vendor/bundle ]; then
  echo "--- Installing gems..."
  rbenv exec bundle config set --local path vendor/bundle 2>/dev/null
  rbenv exec bundle install --quiet
fi

echo "--- Starting Jekyll with live reload on http://127.0.0.1:${PORT}/"
rbenv exec bundle exec jekyll serve \
  --livereload \
  --incremental \
  --port "${PORT}" \
  --host 127.0.0.1 \
  --open-url
