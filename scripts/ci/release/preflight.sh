#!/usr/bin/env bash
set -euo pipefail

# Minimal logging — common.sh isn't sourced here because it triggers
# meta-data lookups that we don't want to require for preflight.
log_info()  { echo "--- $*"; }
log_error() { echo "--- :x: $*" >&2; }
log_step()  { echo "--- :arrow_right: $*"; }

usage() {
  echo "Usage: $0 <release|default|all>" >&2
  exit 2
}

if [[ $# -lt 1 ]]; then
  usage
fi

QUEUE="$1"
case "$QUEUE" in
  default|release|all) ;;
  *)
    log_error "Unknown queue: $QUEUE"
    usage
    ;;
esac

ERRORS=0
WARNINGS=0

# check_cmd returns 0 always — accumulates errors via the ERRORS counter so
# the script reports ALL missing tools, not just the first one (we are running
# under set -e and want to keep going).
check_cmd() {
  local cmd="$1"
  local needed_by="$2"
  local optional="${3:-no}"
  if command -v "$cmd" >/dev/null 2>&1; then
    echo "  ✓ $cmd  ($needed_by)"
    return 0
  fi
  if [[ "$optional" == "optional" ]]; then
    echo "  ⚠ $cmd missing (optional; needed by: $needed_by)"
    WARNINGS=$((WARNINGS + 1))
    return 0
  fi
  echo "  ✗ $cmd missing  (needed by: $needed_by)" >&2
  ERRORS=$((ERRORS + 1))
  return 0
}

check_docker_works() {
  local image="hello-world"
  if docker run --rm "$image" >/dev/null 2>&1; then
    echo "  ✓ docker run works (smoke-tested with $image)"
    return 0
  fi
  echo "  ✗ docker installed but 'docker run --rm hello-world' failed — daemon unreachable?" >&2
  ERRORS=$((ERRORS + 1))
  return 0
}

log_step "Preflight: verifying tools for queue=$QUEUE"

echo ""
log_info "Host tools (all queues — everything else runs in Docker)"
check_cmd bash       "all scripts"
check_cmd git        "all scripts"
check_cmd aws        "common.sh:load_secret"
check_cmd jq         "common.sh:load_secret"
check_cmd curl       "API calls"
check_cmd python3    "set-release-version, verify-totp"
check_cmd docker     "every Maven/npm/helm/ruby/gh step runs in Docker"
check_cmd buildkite-agent "all CI steps"

if command -v docker >/dev/null 2>&1; then
  echo ""
  log_info "Docker smoke test"
  check_docker_works
fi

echo ""
log_info "Summary: errors=$ERRORS warnings=$WARNINGS"

if [[ $ERRORS -gt 0 ]]; then
  log_error "Preflight FAILED — $ERRORS required tool(s) missing on queue=$QUEUE"
  exit 1
fi

log_info "Preflight PASSED for queue=$QUEUE"
exit 0
