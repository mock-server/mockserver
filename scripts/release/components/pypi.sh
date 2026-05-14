#!/usr/bin/env bash
# Publish mockserver-client to PyPI.
#
# Dry-run: build + twine check, skip twine upload.

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
require_release_inputs
skip_unless_release_type "pypi" full,post-maven

log_step "Publish PyPI $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

PYTHON_DIR="$REPO_ROOT/mockserver-client-python"
VERSION=$(grep -E '^version\s*=' "$PYTHON_DIR/pyproject.toml" | head -1 | sed 's/.*= *"\(.*\)".*/\1/')
[[ -n "$VERSION" ]] || { log_error "could not parse version from pyproject.toml"; exit 1; }
log_info "Package version: $VERSION"

if ! is_dry_run; then
  log_info "Checking PyPI for existing version"
  http_code=$(curl -s -o /dev/null -w "%{http_code}" "https://pypi.org/pypi/mockserver-client/$VERSION/json")
  case "$http_code" in
    200) log_error "$VERSION already on PyPI"; exit 1 ;;
    404) ;;
    *)   log_error "PyPI returned HTTP $http_code"; exit 1 ;;
  esac
fi

rm -rf "$PYTHON_DIR/dist" "$PYTHON_DIR/build" "$PYTHON_DIR"/*.egg-info 2>/dev/null || true

log_info "Build + validate package (Python in Docker)"
in_docker "$PYTHON_IMAGE" \
  -w /build/mockserver-client-python \
  -- bash -ec '
    pip install --quiet --no-cache-dir build twine
    python -m build .
    python -m twine check dist/*
  '

if is_dry_run; then
  log_dry "skip: twine upload to PyPI"
  log_info "Built artifacts: $PYTHON_DIR/dist/"
  ls -la "$PYTHON_DIR/dist/" 2>/dev/null || true
else
  log_info "Uploading to PyPI"
  PYPI_TOKEN=$(load_secret "mockserver-build/pypi" "token")
  in_docker "$PYTHON_IMAGE" \
    -w /build/mockserver-client-python \
    -e "TWINE_USERNAME=__token__" \
    -e "TWINE_PASSWORD=$PYPI_TOKEN" \
    -- bash -ec '
      pip install --quiet --no-cache-dir twine
      set +x
      python -m twine upload dist/*
    '
fi

log_info "PyPI publish complete"
