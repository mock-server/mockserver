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
PIPELINES_UPLOADED=0

upload_if_changed() {
  local path_regex="$1"
  local pipeline_file="$2"
  if printf '%s\n' "$CHANGED_FILES" | grep -qE -- "$path_regex"; then
    echo "--- :pipeline: Uploading ${pipeline_file} (matched ${path_regex})"
    buildkite-agent pipeline upload "$pipeline_file"
    PIPELINES_UPLOADED=$((PIPELINES_UPLOADED + 1))
  fi
}

upload_if_changed "^(mockserver/|mockserver-ui/)" ".buildkite/pipeline-java.yml"
upload_if_changed "^mockserver-ui/" ".buildkite/pipeline-ui.yml"
upload_if_changed "^(mockserver-node/|mockserver-client-node/)" ".buildkite/pipeline-node.yml"
upload_if_changed "^mockserver-client-python/" ".buildkite/pipeline-python.yml"
upload_if_changed "^mockserver-client-ruby/" ".buildkite/pipeline-ruby.yml"
upload_if_changed "^mockserver-maven-plugin/" ".buildkite/pipeline-maven-plugin.yml"
upload_if_changed "^mockserver-performance-test/" ".buildkite/pipeline-perf-test.yml"

if printf '%s\n' "$CHANGED_FILES" | grep -qE -- "^(\.buildkite/|\.github/|terraform/|docker/|scripts/|helm/|docs/|AGENTS\.md|opencode\.jsonc|\.opencode/)"; then
  echo "--- :pipeline: Uploading pipeline-infra.yml (infra changes)"
  buildkite-agent pipeline upload ".buildkite/pipeline-infra.yml"
  PIPELINES_UPLOADED=$((PIPELINES_UPLOADED + 1))
fi

if [ "$PIPELINES_UPLOADED" -eq 0 ]; then
  echo "--- :pipeline: No project-specific changes detected, uploading default pipeline"
  buildkite-agent pipeline upload ".buildkite/pipeline-default.yml"
fi
