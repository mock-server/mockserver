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

RESPONSE_FILE=$(mktemp /tmp/bk-response-XXXXXX.json)
trap 'rm -f "$RESPONSE_FILE"' EXIT

echo "--- :aws: Fetching Buildkite API token from Secrets Manager"
BUILDKITE_API_TOKEN=$(aws secretsmanager get-secret-value \
  --secret-id "$SECRET_ID" --region "$REGION" --query SecretString --output text)

if [ -z "$BUILDKITE_API_TOKEN" ]; then
  echo "^^^ +++"
  echo ":x: Failed to fetch Buildkite API token from Secrets Manager (${SECRET_ID})"
  exit 1
fi

IS_PR="false"
if [ -n "${BUILDKITE_PULL_REQUEST:-}" ] && [ "$BUILDKITE_PULL_REQUEST" != "false" ]; then
  IS_PR="true"
fi

ENV_VARS="{}"
if [ "$IS_PR" = "true" ]; then
  ENV_VARS=$(jq -n \
    --arg pr "$BUILDKITE_PULL_REQUEST" \
    --arg base "${BUILDKITE_PULL_REQUEST_BASE_BRANCH:-master}" \
    --arg repo "${BUILDKITE_PULL_REQUEST_REPO:-}" \
    '{BUILDKITE_PULL_REQUEST: $pr, BUILDKITE_PULL_REQUEST_BASE_BRANCH: $base, BUILDKITE_PULL_REQUEST_REPO: $repo}')
fi

JQ_ARGS=(
  --arg commit "$BUILDKITE_COMMIT"
  --arg branch "$BUILDKITE_BRANCH"
  --arg message "${BUILDKITE_MESSAGE:-}"
  --argjson env "$ENV_VARS"
)

if [ "$IS_PR" = "true" ]; then
  JQ_ARGS+=(
    --argjson pr_id "$BUILDKITE_PULL_REQUEST"
    --arg pr_base "${BUILDKITE_PULL_REQUEST_BASE_BRANCH:-master}"
    --arg pr_repo "${BUILDKITE_PULL_REQUEST_REPO:-}"
  )
  PAYLOAD=$(jq -n "${JQ_ARGS[@]}" '{
    commit: $commit,
    branch: $branch,
    message: $message,
    env: $env,
    ignore_pipeline_branch_filters: true,
    pull_request_id: $pr_id,
    pull_request_base_branch: $pr_base,
    pull_request_repository: (if $pr_repo == "" then null else $pr_repo end)
  } | with_entries(select(.value != null))')
else
  PAYLOAD=$(jq -n "${JQ_ARGS[@]}" '{
    commit: $commit,
    branch: $branch,
    message: $message,
    env: $env,
    ignore_pipeline_branch_filters: true
  }')
fi

echo "--- :buildkite: Creating build on ${LABEL} (${PIPELINE_SLUG})"
if [ "$IS_PR" = "true" ]; then
  echo "    PR #${BUILDKITE_PULL_REQUEST} -> ${BUILDKITE_PULL_REQUEST_BASE_BRANCH:-master}"
fi

HTTP_CODE=$(curl -sS --max-time 30 --connect-timeout 10 -o "$RESPONSE_FILE" -w '%{http_code}' \
  -X POST "$API_BASE" \
  -H "Authorization: Bearer ${BUILDKITE_API_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

if ! [[ "$HTTP_CODE" =~ ^[0-9]+$ ]] || [ "$HTTP_CODE" -lt 200 ] || [ "$HTTP_CODE" -ge 300 ]; then
  echo "^^^ +++"
  echo ":x: Buildkite API returned HTTP ${HTTP_CODE} creating build on ${LABEL}"
  echo "    Request payload:"
  echo "$PAYLOAD" | jq 'del(.env)' 2>/dev/null || echo "$PAYLOAD"
  echo "    Response:"
  cat "$RESPONSE_FILE" 2>/dev/null
  exit 1
fi

RESPONSE=$(cat "$RESPONSE_FILE")

BUILD_NUMBER=$(echo "$RESPONSE" | jq -r '.number')
BUILD_URL=$(echo "$RESPONSE" | jq -r '.web_url')

if ! [[ "$BUILD_NUMBER" =~ ^[0-9]+$ ]]; then
  echo "^^^ +++"
  echo ":x: Failed to extract build number from API response"
  cat "$RESPONSE_FILE" 2>/dev/null
  exit 1
fi

BUILD_API_URL="${API_BASE}/${BUILD_NUMBER}"

echo ":white_check_mark: ${LABEL} build #${BUILD_NUMBER} created"
echo "    ${BUILD_URL}"

echo "--- :hourglass: Waiting for ${LABEL} build #${BUILD_NUMBER} to complete"

POLL_INTERVAL="${CHILD_POLL_INTERVAL_SECONDS:-30}"
MAX_WAIT="${CHILD_MAX_WAIT_SECONDS:-7200}"
ELAPSED=0
CONSECUTIVE_ERRORS=0

while [ "$ELAPSED" -lt "$MAX_WAIT" ]; do
  sleep "$POLL_INTERVAL"
  ELAPSED=$((ELAPSED + POLL_INTERVAL))

  POLL_RESPONSE=$(curl -sS --max-time 15 --connect-timeout 10 --retry 2 --retry-delay 5 \
    -o "$RESPONSE_FILE" -w '%{http_code}' \
    -H "Authorization: Bearer ${BUILDKITE_API_TOKEN}" \
    "${BUILD_API_URL}" 2>/dev/null || echo "000")

  if ! [[ "$POLL_RESPONSE" =~ ^2[0-9][0-9]$ ]]; then
    CONSECUTIVE_ERRORS=$((CONSECUTIVE_ERRORS + 1))
    echo "    :warning: API poll returned HTTP ${POLL_RESPONSE} (attempt ${CONSECUTIVE_ERRORS}/10)"
    if [ "$CONSECUTIVE_ERRORS" -ge 10 ]; then
      echo "^^^ +++"
      echo ":x: Too many consecutive API errors polling ${LABEL} build #${BUILD_NUMBER}"
      exit 1
    fi
    continue
  fi
  CONSECUTIVE_ERRORS=0

  BUILD_STATE=$(jq -r '.state' "$RESPONSE_FILE")

  case "$BUILD_STATE" in
    passed)
      echo ":white_check_mark: ${LABEL} build #${BUILD_NUMBER} passed"
      exit 0
      ;;
    failed)
      echo "^^^ +++"
      echo ":x: ${LABEL} build #${BUILD_NUMBER} failed"
      echo "    ${BUILD_URL}"
      exit 1
      ;;
    canceled|canceling)
      echo "^^^ +++"
      echo ":warning: ${LABEL} build #${BUILD_NUMBER} was canceled"
      echo "    ${BUILD_URL}"
      exit 1
      ;;
    not_run)
      echo "^^^ +++"
      echo ":warning: ${LABEL} build #${BUILD_NUMBER} did not run"
      echo "    ${BUILD_URL}"
      exit 1
      ;;
    *)
      MINS=$((ELAPSED / 60))
      echo "    ${LABEL} build #${BUILD_NUMBER}: ${BUILD_STATE} (${MINS}m elapsed)"
      ;;
  esac
done

echo "^^^ +++"
echo ":x: Timed out waiting for ${LABEL} build #${BUILD_NUMBER} after $((MAX_WAIT / 60))m"
echo "    ${BUILD_URL}"
exit 1
