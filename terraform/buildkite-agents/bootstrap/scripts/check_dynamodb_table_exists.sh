#!/usr/bin/env bash
TABLE_NAME=$1
PROFILE=$2
REGION=$3

[[ "$(uname)" == "Darwin" && -d "/opt/homebrew/opt/expat/lib" ]] && export DYLD_LIBRARY_PATH="/opt/homebrew/opt/expat/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
[[ -n "${NODE_EXTRA_CA_CERTS:-}" && -z "${AWS_CA_BUNDLE:-}" ]] && export AWS_CA_BUNDLE="$NODE_EXTRA_CA_CERTS"

if aws dynamodb describe-table --table-name "${TABLE_NAME}" --profile "${PROFILE}" --region "${REGION}" >/dev/null 2>&1; then
  printf '{"name": "%s", "exists": "true"}\n' "${TABLE_NAME}"
else
  printf '{"name": "unknown", "exists": "false"}\n'
fi
