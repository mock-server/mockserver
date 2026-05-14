#!/usr/bin/env bash
# Verify the host has the minimum tools needed to run the release pipeline.
# Everything else runs inside Docker containers — so the host only needs:
#   bash, git, aws, jq, curl, python3, docker (+ working daemon)
#
# This script is CI-agnostic. Run it locally to validate your dev box, or
# from a CI adapter to validate an agent.

set -euo pipefail

log_info()  { echo "--- $*"; }
log_error() { echo "--- :x: $*" >&2; }
log_step()  { echo "--- :arrow_right: $*"; }

ERRORS=0
check() {
  local cmd="$1" reason="$2"
  if command -v "$cmd" >/dev/null 2>&1; then
    echo "  ✓ $cmd  ($reason)"
  else
    echo "  ✗ $cmd missing  ($reason)" >&2
    ERRORS=$((ERRORS + 1))
  fi
}

log_step "Preflight: host tool check"
check bash    "all release scripts"
check git     "all release scripts"
check aws     "AWS Secrets Manager + S3"
check jq      "JSON parsing"
check curl    "HTTP calls"
check python3 "pom version updates, schema generation"
check docker  "every language toolchain runs in a container"

if command -v docker >/dev/null 2>&1; then
  log_step "Docker daemon smoke test"
  if docker run --rm hello-world >/dev/null 2>&1; then
    echo "  ✓ docker daemon reachable + can pull/run images"
  else
    echo "  ✗ docker installed but 'docker run hello-world' failed" >&2
    ERRORS=$((ERRORS + 1))
  fi
fi

if [[ $ERRORS -gt 0 ]]; then
  log_error "Preflight FAILED: $ERRORS tool(s) missing"
  exit 1
fi

log_info "Preflight PASSED"
