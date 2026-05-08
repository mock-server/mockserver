#!/usr/bin/env bash
set -euo pipefail

#
# Cleans up Buildkite builds for closed/merged GitHub PRs.
#
# Two modes:
#   1. Webhook-triggered: receives a GitHub pull_request:closed webhook via
#      Buildkite Pipeline Trigger. Cleans up builds for that specific PR branch.
#   2. Scheduled: sweeps all pipelines for builds on branches whose PRs are
#      no longer open.
#
# The mode is auto-detected: if buildkite:webhook meta-data exists, it runs
# in webhook mode; otherwise it runs in scheduled sweep mode.
#

SECRET_ID="${BUILDKITE_API_TOKEN_SECRET_ID:-mockserver-build/buildkite-api-token}"
REGION="${AWS_REGION:-eu-west-2}"
ORG="mockserver"
GITHUB_REPO="mock-server/mockserver-monorepo"
API="https://api.buildkite.com/v2/organizations/${ORG}"

PIPELINES=(
  "mockserver"
  "mockserver-java"
  "mockserver-node"
  "mockserver-ui"
  "mockserver-python"
  "mockserver-ruby"
  "mockserver-maven-plugin"
  "mockserver-performance-test"
  "mockserver-container-tests"
  "mockserver-website"
  "mockserver-build-image"
  "mockserver-infra"
)

echo "--- :aws: Fetching Buildkite API token from Secrets Manager"
BUILDKITE_API_TOKEN=$(aws secretsmanager get-secret-value \
  --secret-id "$SECRET_ID" --region "$REGION" --query SecretString --output text)

if [ -z "$BUILDKITE_API_TOKEN" ]; then
  echo "^^^ +++"
  echo ":x: Failed to fetch Buildkite API token"
  exit 1
fi

AUTH="Authorization: Bearer ${BUILDKITE_API_TOKEN}"

cancel_and_delete_builds() {
  local branch="$1"
  local label="$2"
  local cancelled=0
  local deleted=0

  for PIPELINE in "${PIPELINES[@]}"; do
    PAGE=1
    while true; do
      BUILDS=$(curl -sS --max-time 30 \
        -H "$AUTH" \
        "${API}/pipelines/${PIPELINE}/builds?branch=${branch}&per_page=100&page=${PAGE}" 2>/dev/null) || break

      COUNT=$(echo "$BUILDS" | jq 'length')
      if [ "$COUNT" = "0" ] || [ "$COUNT" = "null" ]; then
        break
      fi

      echo "$BUILDS" | jq -r '.[] | [.number, .state] | @tsv' | while IFS=$'\t' read -r NUM STATE; do
        if [[ "$STATE" =~ ^(running|scheduled|creating|canceling|blocked|waiting)$ ]]; then
          echo "  ${PIPELINE} #${NUM}: cancelling (${STATE})"
          curl -sS --max-time 15 -X PUT \
            -H "$AUTH" \
            "${API}/pipelines/${PIPELINE}/builds/${NUM}/cancel" >/dev/null 2>&1 || true
          sleep 1
        fi

        echo "  ${PIPELINE} #${NUM}: deleting"
        curl -sS --max-time 15 -X DELETE \
          -H "$AUTH" \
          "${API}/pipelines/${PIPELINE}/builds/${NUM}" >/dev/null 2>&1 || true
      done

      if [ "$COUNT" -lt 100 ]; then
        break
      fi
      PAGE=$((PAGE + 1))
    done
  done
}

WEBHOOK=$(buildkite-agent meta-data get buildkite:webhook 2>/dev/null || echo "")

if [ -n "$WEBHOOK" ]; then
  echo "--- :github: Webhook-triggered mode"
  ACTION=$(echo "$WEBHOOK" | jq -r '.action // ""')
  PR_NUMBER=$(echo "$WEBHOOK" | jq -r '.number // ""')
  PR_BRANCH=$(echo "$WEBHOOK" | jq -r '.pull_request.head.ref // ""')
  PR_MERGED=$(echo "$WEBHOOK" | jq -r '.pull_request.merged // false')

  if [ "$ACTION" != "closed" ]; then
    echo "Ignoring action '${ACTION}' (only 'closed' triggers cleanup)"
    exit 0
  fi

  if [ -z "$PR_BRANCH" ]; then
    echo ":x: No branch found in webhook payload"
    exit 1
  fi

  MERGED_LABEL="closed"
  if [ "$PR_MERGED" = "true" ]; then
    MERGED_LABEL="merged"
  fi

  echo "PR #${PR_NUMBER} ${MERGED_LABEL} — cleaning up builds on branch: ${PR_BRANCH}"
  cancel_and_delete_builds "$PR_BRANCH" "PR #${PR_NUMBER}"

else
  echo "--- :broom: Scheduled sweep mode"
  CLEANED_BRANCHES=()

  for PIPELINE in "${PIPELINES[@]}"; do
    PAGE=1
    while true; do
      BUILDS=$(curl -sS --max-time 30 \
        -H "$AUTH" \
        "${API}/pipelines/${PIPELINE}/builds?per_page=100&page=${PAGE}" 2>/dev/null) || break

      COUNT=$(echo "$BUILDS" | jq 'length')
      if [ "$COUNT" = "0" ] || [ "$COUNT" = "null" ]; then
        break
      fi

      PR_BRANCHES=$(echo "$BUILDS" | jq -r '[.[] | select(.pull_request != null) | .branch] | unique | .[]')

      for BRANCH in $PR_BRANCHES; do
        ALREADY_CLEANED=false
        for DONE in "${CLEANED_BRANCHES[@]+"${CLEANED_BRANCHES[@]}"}"; do
          if [ "$DONE" = "$BRANCH" ]; then
            ALREADY_CLEANED=true
            break
          fi
        done
        if [ "$ALREADY_CLEANED" = "true" ]; then
          continue
        fi

        PR_NUMBER=$(echo "$BUILDS" | jq -r --arg b "$BRANCH" \
          '[.[] | select(.branch == $b and .pull_request != null) | .pull_request.id] | first')
        if [ -z "$PR_NUMBER" ] || [ "$PR_NUMBER" = "null" ]; then
          continue
        fi

        PR_STATE=$(curl -sS --max-time 10 \
          "https://api.github.com/repos/${GITHUB_REPO}/pulls/${PR_NUMBER}" 2>/dev/null \
          | jq -r '.state // "unknown"') || continue

        if [ "$PR_STATE" != "open" ]; then
          echo "--- :broom: PR #${PR_NUMBER} is ${PR_STATE} — cleaning up branch ${BRANCH}"
          cancel_and_delete_builds "$BRANCH" "PR #${PR_NUMBER}"
          CLEANED_BRANCHES+=("$BRANCH")
        fi
      done

      if [ "$COUNT" -lt 100 ]; then
        break
      fi
      PAGE=$((PAGE + 1))
    done
  done
fi

echo "--- :white_check_mark: Cleanup complete"
