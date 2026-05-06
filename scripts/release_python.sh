#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PYTHON_DIR="$REPO_ROOT/mockserver-client-python"
SECRET_ID="mockserver-build/pypi"
REGION="eu-west-2"

for cmd in jq python3 aws curl; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "Missing required command: $cmd" >&2; exit 1; }
done
python3 -m build --help >/dev/null 2>&1 || { echo "Missing required Python package: build (pip install build)" >&2; exit 1; }
python3 -m twine --version >/dev/null 2>&1 || { echo "Missing required Python package: twine (pip install twine)" >&2; exit 1; }

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

echo "--- Reading version from pyproject.toml"
VERSION=$(grep -E '^version\s*=' "$PYTHON_DIR/pyproject.toml" | head -1 | sed 's/.*= *"\(.*\)".*/\1/')
echo "Version: $VERSION"

echo "--- Checking if version already exists on PyPI"
http_code=$(curl -s -o /dev/null -w "%{http_code}" "https://pypi.org/pypi/mockserver-client/$VERSION/json")
case "$http_code" in
  200) echo "ERROR: Version $VERSION already exists on PyPI" >&2; exit 1 ;;
  404) ;;
  *)   echo "ERROR: PyPI returned HTTP $http_code while checking version" >&2; exit 1 ;;
esac

echo "--- Cleaning previous builds"
rm -rf "$PYTHON_DIR/dist" "$PYTHON_DIR/build" "$PYTHON_DIR"/*.egg-info

echo "--- Building Python package"
python3 -m build "$PYTHON_DIR"

echo "--- Verifying package"
python3 -m twine check "$PYTHON_DIR/dist/"*

echo "--- Fetching PyPI token from Secrets Manager"
PYPI_TOKEN=$(load_secret "$SECRET_ID" "token")

echo "--- Uploading to PyPI"
(
  set +x
  TWINE_USERNAME="__token__" \
  TWINE_PASSWORD="$PYPI_TOKEN" \
  python3 -m twine upload "$PYTHON_DIR/dist/"*
)

echo "--- Successfully published mockserver-client $VERSION to PyPI"
echo "    https://pypi.org/project/mockserver-client/$VERSION/"
