#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker
require_cmd curl
require_cmd aws
require_cmd jq

log_step "Publishing Python client $RELEASE_VERSION to PyPI"

PYTHON_DIR="$REPO_ROOT/mockserver-client-python"

VERSION=$(grep -E '^version\s*=' "$PYTHON_DIR/pyproject.toml" | head -1 | sed 's/.*= *"\(.*\)".*/\1/')
log_info "Version from pyproject.toml: $VERSION"

log_info "Checking if version already exists on PyPI"
http_code=$(curl -s -o /dev/null -w "%{http_code}" "https://pypi.org/pypi/mockserver-client/$VERSION/json")
case "$http_code" in
  200) log_error "Version $VERSION already exists on PyPI"; exit 1 ;;
  404) ;;
  *)   log_error "PyPI returned HTTP $http_code"; exit 1 ;;
esac

log_info "Cleaning previous builds"
rm -rf "$PYTHON_DIR/dist" "$PYTHON_DIR/build" "$PYTHON_DIR"/*.egg-info

log_info "Fetching PyPI token"
PYPI_TOKEN=$(load_secret "mockserver-build/pypi" "token")

log_info "Building and uploading PyPI package (in Docker)"
"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$PYTHON_IMAGE" \
  -w /build/mockserver-client-python \
  -e "TWINE_USERNAME=__token__" \
  -e "TWINE_PASSWORD=$PYPI_TOKEN" \
  -- bash -ec '
    pip install --quiet --no-cache-dir build twine
    python -m build .
    python -m twine check dist/*
    set +x
    python -m twine upload dist/*
  '

log_info "Published mockserver-client $VERSION to PyPI"
