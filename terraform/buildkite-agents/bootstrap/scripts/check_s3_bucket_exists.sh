#!/usr/bin/env bash
BUCKET_NAME=$1
PROFILE=$2

[[ "$(uname)" == "Darwin" && -d "/opt/homebrew/opt/expat/lib" ]] && export DYLD_LIBRARY_PATH="/opt/homebrew/opt/expat/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
[[ -n "${NODE_EXTRA_CA_CERTS:-}" && -z "${AWS_CA_BUNDLE:-}" ]] && export AWS_CA_BUNDLE="$NODE_EXTRA_CA_CERTS"

if aws s3api head-bucket --bucket "${BUCKET_NAME}" --profile "${PROFILE}" >/dev/null 2>&1; then
  printf '{"name": "%s", "exists": "true"}\n' "${BUCKET_NAME}"
else
  printf '{"name": "unknown", "exists": "false"}\n'
fi
