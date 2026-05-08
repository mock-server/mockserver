#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd python3
require_cmd curl

log_step "Publishing Python client $RELEASE_VERSION to PyPI"

PYTHON_DIR="$REPO_ROOT/mockserver-client-python"

python3 -m build --help >/dev/null 2>&1 || { log_error "Missing Python package: build"; exit 1; }
python3 -m twine --version >/dev/null 2>&1 || { log_error "Missing Python package: twine"; exit 1; }

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

log_info "Building Python package"
python3 -m build "$PYTHON_DIR"

log_info "Verifying package"
python3 -m twine check "$PYTHON_DIR/dist/"*

log_info "Fetching PyPI token"
PYPI_TOKEN=$(load_secret "mockserver-build/pypi" "token")

log_info "Uploading to PyPI"
(
  set +x
  TWINE_USERNAME="__token__" \
  TWINE_PASSWORD="$PYPI_TOKEN" \
  python3 -m twine upload "$PYTHON_DIR/dist/"*
)

log_info "Published mockserver-client $VERSION to PyPI"
