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

probe_java() {
  if command -v java >/dev/null 2>&1; then
    return 0
  fi
  echo "  java not in PATH — probing for installations:"
  local candidates=(
    "${JAVA_HOME:-}/bin/java"
    /usr/lib/jvm/*/bin/java
    /opt/*/bin/java
    /Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java
  )
  local found=0
  for c in "${candidates[@]}"; do
    if [[ -x "$c" ]]; then
      echo "    found: $c"
      found=1
    fi
  done
  if [[ $found -eq 0 ]]; then
    echo "    no java binaries found under /usr/lib/jvm /opt /Library/Java"
  fi
  echo "  JAVA_HOME=${JAVA_HOME:-<unset>}"
  echo "  PATH=$PATH"
}

log_step "Preflight: verifying tools for queue=$QUEUE"

echo ""
log_info "Common tools (all queues)"
check_cmd bash       "all scripts"
check_cmd git        "all scripts"
check_cmd aws        "common.sh:load_secret"
check_cmd jq         "common.sh:load_secret"
check_cmd curl       "API calls"
check_cmd python3    "set-release-version, verify-totp, publish-pypi"
check_cmd sed        "update-versions"
check_cmd grep       "common.sh"

if [[ "$QUEUE" == "default" || "$QUEUE" == "all" ]]; then
  echo ""
  log_info "Default-queue tools"
  check_cmd java       "build-and-test"
  command -v java >/dev/null 2>&1 || probe_java
  check_cmd javac      "build-and-test (compilation)" optional
  check_cmd docker     "publish-docker"
  check_cmd gem        "publish-rubygems"
  check_cmd buildkite-agent "all CI steps"
fi

if [[ "$QUEUE" == "release" || "$QUEUE" == "all" ]]; then
  echo ""
  log_info "Release-queue tools"
  check_cmd java       "deploy-release, deploy-snapshot, release-maven-plugin, publish-javadoc, update-versions"
  command -v java >/dev/null 2>&1 || probe_java
  check_cmd javac      "Maven compilation" optional
  check_cmd gpg        "deploy-release, release-maven-plugin"
  check_cmd npm        "publish-npm"
  check_cmd npx        "publish-npm"
  check_cmd helm       "publish-helm"
  check_cmd bundle     "publish-website (Jekyll)"
  check_cmd gh         "github-release"
  check_cmd terraform  "create-versioned-site, common.sh website-bucket lookup" optional
  check_cmd buildkite-agent "all CI steps"
fi

echo ""
log_info "Summary: errors=$ERRORS warnings=$WARNINGS"

if [[ $ERRORS -gt 0 ]]; then
  log_error "Preflight FAILED — $ERRORS required tool(s) missing on queue=$QUEUE"
  exit 1
fi

log_info "Preflight PASSED for queue=$QUEUE"
exit 0
