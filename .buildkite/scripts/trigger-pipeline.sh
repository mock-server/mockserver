#!/usr/bin/env bash
set -euo pipefail

if [ -z "${1:-}" ]; then
  echo "Usage: $0 <pipeline-slug> [label]" >&2
  exit 1
fi

PIPELINE_SLUG="$1"
LABEL="${2:-$PIPELINE_SLUG}"

SECRET_ID="${BUILDKITE_API_TOKEN_SECRET_ID:-mockserver-build/buildkite-api-token}"
REGION="${AWS_REGION:-eu-west-2}"
ORG="mockserver"
API_BASE="https://api.buildkite.com/v2/organizations/${ORG}/pipelines/${PIPELINE_SLUG}/builds"
POLL_INTERVAL=10
TIMEOUT=3600

echo "--- :aws: Fetching Buildkite API token from Secrets Manager"
BUILDKITE_API_TOKEN=$(aws secretsmanager get-secret-value \
  --secret-id "$SECRET_ID" --region "$REGION" --query SecretString --output text)

if [ -z "$BUILDKITE_API_TOKEN" ]; then
  echo "^^^ +++"
  echo ":x: Failed to fetch Buildkite API token from Secrets Manager (${SECRET_ID})"
  exit 1
fi

ENV_VARS="{}"
if [ -n "${BUILDKITE_PULL_REQUEST:-}" ] && [ "$BUILDKITE_PULL_REQUEST" != "false" ]; then
  ENV_VARS=$(jq -n \
    --arg pr "$BUILDKITE_PULL_REQUEST" \
    --arg base "${BUILDKITE_PULL_REQUEST_BASE_BRANCH:-master}" \
    --arg repo "${BUILDKITE_PULL_REQUEST_REPO:-}" \
    '{BUILDKITE_PULL_REQUEST: $pr, BUILDKITE_PULL_REQUEST_BASE_BRANCH: $base, BUILDKITE_PULL_REQUEST_REPO: $repo}')
fi

PAYLOAD=$(jq -n \
  --arg commit "$BUILDKITE_COMMIT" \
  --arg branch "$BUILDKITE_BRANCH" \
  --arg message "${BUILDKITE_MESSAGE:-}" \
  --argjson env "$ENV_VARS" \
  '{commit: $commit, branch: $branch, message: $message, env: $env}')

echo "--- :buildkite: Creating build on ${LABEL} (${PIPELINE_SLUG})"
RESPONSE=$(curl -sSf --max-time 30 --connect-timeout 10 -X POST "$API_BASE" \
  -H "Authorization: Bearer ${BUILDKITE_API_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

BUILD_NUMBER=$(echo "$RESPONSE" | jq -r '.number')
BUILD_URL=$(echo "$RESPONSE" | jq -r '.web_url')
BUILD_API_URL="${API_BASE}/${BUILD_NUMBER}"

echo "--- :buildkite: ${LABEL} build #${BUILD_NUMBER}"
echo "    ${BUILD_URL}"

ELAPSED=0
while true; do
  if [ "$ELAPSED" -ge "$TIMEOUT" ]; then
    echo "^^^ +++"
    echo ":x: Timed out after ${TIMEOUT}s waiting for ${LABEL} build #${BUILD_NUMBER}"
    exit 1
  fi

  sleep "$POLL_INTERVAL"
  ELAPSED=$((ELAPSED + POLL_INTERVAL))

  STATE=$(curl -sS --max-time 30 --connect-timeout 10 "${BUILD_API_URL}" \
    -H "Authorization: Bearer ${BUILDKITE_API_TOKEN}" | jq -r '.state')

  if [ -z "$STATE" ] || [ "$STATE" = "null" ]; then
    echo "^^^ +++"
    echo ":x: Failed to fetch build state for ${LABEL} build #${BUILD_NUMBER}"
    echo "    ${BUILD_URL}"
    exit 1
  fi

  case "$STATE" in
    passed)
      echo ":white_check_mark: ${LABEL} build #${BUILD_NUMBER} passed"
      exit 0
      ;;
    failed|canceled|cancelled)
      echo "^^^ +++"
      echo ":x: ${LABEL} build #${BUILD_NUMBER} ${STATE}"
      echo "    ${BUILD_URL}"
      exit 1
      ;;
    not_run|skipped|broken)
      echo "^^^ +++"
      echo ":x: ${LABEL} build #${BUILD_NUMBER} ${STATE}"
      echo "    ${BUILD_URL}"
      exit 1
      ;;
    blocked)
      echo ":hourglass: ${LABEL} build #${BUILD_NUMBER} is blocked — waiting..."
      ;;
    running|scheduled|creating|canceling)
      ;;
    *)
      echo ":warning: Unknown state '${STATE}' for ${LABEL} build #${BUILD_NUMBER}"
      ;;
  esac
done
