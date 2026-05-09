#!/usr/bin/env bash
set -euo pipefail

DEFAULT_BRANCH="${BUILDKITE_PULL_REQUEST_BASE_BRANCH:-}"
if [ -z "$DEFAULT_BRANCH" ]; then
  DEFAULT_BRANCH=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || true)
fi
DEFAULT_BRANCH=${DEFAULT_BRANCH:-master}

last_successful_commit() {
  local secret_id="${BUILDKITE_API_TOKEN_SECRET_ID:-mockserver-build/buildkite-api-token}"
  local region="${AWS_REGION:-eu-west-2}"
  local org="${BUILDKITE_ORGANIZATION_SLUG:-mockserver}"
  local pipeline="${BUILDKITE_PIPELINE_SLUG:-mockserver}"
  local branch="${BUILDKITE_BRANCH:-master}"
  local current_build="${BUILDKITE_BUILD_NUMBER:-}"

  local token
  token=$(aws secretsmanager get-secret-value \
    --secret-id "$secret_id" --region "$region" \
    --query SecretString --output text 2>/dev/null) || { echo "    reason: secrets manager unavailable" >&2; return 1; }
  [ -n "$token" ] || { echo "    reason: empty API token" >&2; return 1; }

  local api_base="https://api.buildkite.com/v2/organizations/${org}/pipelines/${pipeline}/builds"

  local response
  response=$(curl -sS --max-time 10 --connect-timeout 5 \
    --get "$api_base" \
    --data-urlencode "branch=${branch}" \
    --data-urlencode "state=passed" \
    --data-urlencode "per_page=10" \
    -H "Authorization: Bearer ${token}" 2>/dev/null) || { echo "    reason: Buildkite API request failed" >&2; return 1; }

  if ! printf '%s' "$response" | jq -e 'type == "array"' >/dev/null 2>&1; then
    echo "    reason: Buildkite API returned non-array response" >&2
    return 1
  fi

  local commit
  if [ -n "$current_build" ] && [[ "$current_build" =~ ^[0-9]+$ ]]; then
    commit=$(printf '%s' "$response" | jq -r \
      --argjson current "$current_build" \
      '[.[] | select(.number < $current)][0].commit // empty' 2>/dev/null)
  else
    commit=$(printf '%s' "$response" | jq -r '.[0].commit // empty' 2>/dev/null)
  fi

  if [ -z "$commit" ]; then
    echo "    reason: no previous successful build found" >&2
    return 1
  fi

  if ! git cat-file -t "$commit" >/dev/null 2>&1; then
    echo "    reason: commit ${commit:0:10} not in local history (shallow clone?)" >&2
    return 1
  fi

  echo "$commit"
}

trigger_all_pipelines() {
  echo "--- :warning: Cannot determine change base — triggering all pipelines"
  CHANGED_FILES=$(git ls-tree -r --name-only HEAD 2>/dev/null || echo "mockserver/")
}

if [ -n "${BUILDKITE_PULL_REQUEST_BASE_BRANCH:-}" ]; then
  MERGE_BASE=$(git merge-base HEAD "origin/${DEFAULT_BRANCH}" 2>/dev/null || echo "HEAD~1")
  CHANGED_FILES=$(git diff --name-only "$MERGE_BASE"..HEAD 2>/dev/null || git diff-tree --no-commit-id --name-only -r HEAD)
else
  LAST_COMMIT=""
  if [ -n "${BUILDKITE:-}" ]; then
    echo "--- :buildkite: Querying last successful build commit"
    LAST_COMMIT=$(last_successful_commit || true)
  fi

  if [ -n "$LAST_COMMIT" ]; then
    echo "    Diffing against last successful build: ${LAST_COMMIT:0:10}"
    CHANGED_FILES=$(git diff --name-only "$LAST_COMMIT"..HEAD 2>/dev/null)
    if [ -z "$CHANGED_FILES" ]; then
      CHANGED_FILES=$(git diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null || true)
    fi
  elif [ -n "${BUILDKITE:-}" ]; then
    trigger_all_pipelines
  else
    CHANGED_FILES=$(git diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null || git diff --name-only HEAD~1..HEAD)
  fi
fi

STEPS=""

trigger_if_changed() {
  local path_regex="$1"
  local pipeline_slug="$2"
  local label="$3"
  if printf '%s\n' "$CHANGED_FILES" | grep -qE -- "$path_regex"; then
    echo "--- :pipeline: Triggering ${label} (matched ${path_regex})"
    STEPS="${STEPS}  - label: \":pipeline: ${label}\"
    command: \".buildkite/scripts/trigger-pipeline.sh ${pipeline_slug} '${label}'\"
    timeout_in_minutes: 120
    agents:
      queue: default
"
  fi
}

trigger_if_changed "^(mockserver/|mockserver-ui/)" "mockserver-java" "MockServer Java"
trigger_if_changed "^mockserver-ui/" "mockserver-ui" "MockServer UI"
trigger_if_changed "^(mockserver-node/|mockserver-client-node/)" "mockserver-node" "MockServer Node"
trigger_if_changed "^mockserver-client-python/" "mockserver-python" "MockServer Python"
trigger_if_changed "^mockserver-client-ruby/" "mockserver-ruby" "MockServer Ruby"
trigger_if_changed "^mockserver-maven-plugin/" "mockserver-maven-plugin" "MockServer Maven Plugin"
trigger_if_changed "^mockserver-performance-test/" "mockserver-performance-test" "MockServer Performance Test"
trigger_if_changed "^container_integration_tests/" "mockserver-container-tests" "MockServer Container Tests"
trigger_if_changed "^jekyll-www.mock-server.com/" "mockserver-website" "MockServer Website"
trigger_if_changed "^docker_build/maven/" "mockserver-build-image" "MockServer Build Image"

if printf '%s\n' "$CHANGED_FILES" | grep -qE -- "^(\.buildkite/|\.github/|terraform/|docker/|scripts/|helm/|docs/|AGENTS\.md|opencode\.jsonc|\.opencode/)"; then
  echo "--- :pipeline: Triggering MockServer Infra (infra changes)"
  STEPS="${STEPS}  - label: \":pipeline: MockServer Infra\"
    command: \".buildkite/scripts/trigger-pipeline.sh mockserver-infra 'MockServer Infra'\"
    timeout_in_minutes: 120
    agents:
      queue: default
"
fi

if [ -z "$STEPS" ]; then
  echo "--- :pipeline: No project-specific changes detected"
  cat <<EOF | buildkite-agent pipeline upload
steps:
  - label: ":white_check_mark: no project changes detected"
    command: "echo 'No project-specific files changed — skipping build'"
    timeout_in_minutes: 1
EOF
else
  printf "steps:\n%s" "$STEPS" | buildkite-agent pipeline upload
fi
