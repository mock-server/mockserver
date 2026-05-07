#!/usr/bin/env bash
set -euo pipefail

DEFAULT_BRANCH="${BUILDKITE_PULL_REQUEST_BASE_BRANCH:-}"
if [ -z "$DEFAULT_BRANCH" ]; then
  DEFAULT_BRANCH=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || true)
fi
DEFAULT_BRANCH=${DEFAULT_BRANCH:-master}

if [ -n "${BUILDKITE_PULL_REQUEST_BASE_BRANCH:-}" ]; then
  MERGE_BASE=$(git merge-base HEAD "origin/${DEFAULT_BRANCH}" 2>/dev/null || echo "HEAD~1")
  CHANGED_FILES=$(git diff --name-only "$MERGE_BASE"..HEAD 2>/dev/null || git diff-tree --no-commit-id --name-only -r HEAD)
else
  CHANGED_FILES=$(git diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null || git diff --name-only HEAD~1..HEAD)
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
    timeout_in_minutes: 5
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
    timeout_in_minutes: 5
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
