#!/usr/bin/env bash
#
# Reports SharedEchoServerGuard firings from a netty IT shard: failsafe redirects the guard's
# stderr into per-class *-output.txt files that are not uploaded for passing tests. Each firing
# is printed, annotated and its output file copied under failed-tests. Never fails the step.
# The grep string is DIAGNOSTIC_HEADER read from the guard's source; SharedEchoServerGuardTest
# runs --print-header to keep that derivation in step with the constant.
#
# Usage: java-netty-it-echo-guard-report.sh <shard> | --print-header

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
GUARD_SOURCE="${ECHO_GUARD_SOURCE:-$REPO_ROOT/mockserver/mockserver-integration-testing/src/main/java/org/mockserver/testing/integration/mock/SharedEchoServerGuard.java}"
REPORTS_DIR="${ECHO_GUARD_REPORTS_DIR:-$REPO_ROOT/mockserver/mockserver-netty/target/failsafe-reports}"
COLLECT_DIR="${ECHO_GUARD_COLLECT_DIR:-$REPO_ROOT/mockserver/target/failed-tests/shared-echo-guard}"
BLOCK_LINES="${ECHO_GUARD_BLOCK_LINES:-200}"
AGENT="${ECHO_GUARD_AGENT:-buildkite-agent}"

derive_header() {
  local header count
  header="$(sed -n 's/^[[:space:]]*static final String DIAGNOSTIC_HEADER = "\(.*\)";[[:space:]]*$/\1/p' "$GUARD_SOURCE" 2>/dev/null)"
  count="$(printf '%s' "$header" | grep -c '' || true)"
  if [ "$count" != "1" ] || [ -z "$header" ]; then
    return 1
  fi
  printf '%s\n' "$header"
}

annotate() {
  local context="$1" body="$2"
  if command -v "$AGENT" >/dev/null 2>&1; then
    printf '%s\n' "$body" | "$AGENT" annotate --style warning --context "$context" || true
  fi
}

if [ "${1:-}" = "--print-header" ]; then
  derive_header
  exit $?
fi

SHARD="${1:-unknown}"
CONTEXT="shared-echo-guard-${SHARD}"

if ! HEADER="$(derive_header)"; then
  msg="could not read DIAGNOSTIC_HEADER from ${GUARD_SOURCE#"$REPO_ROOT"/}, so SharedEchoServerGuard firings in netty IT shard ${SHARD} were not checked"
  echo "+++ :warning: ${msg}" >&2
  annotate "$CONTEXT" ":warning: ${msg}"
  exit 0
fi

matches=()
if [ -d "$REPORTS_DIR" ]; then
  while IFS= read -r file; do
    [ -n "$file" ] && matches+=("$file")
  done < <(grep -alxF -- "$HEADER" "$REPORTS_DIR"/*-output.txt 2>/dev/null | sort)
fi

if [ "${#matches[@]}" -eq 0 ]; then
  exit 0
fi

echo "+++ :warning: SharedEchoServerGuard fired in netty IT shard ${SHARD} (${#matches[@]} test class(es))"
collected=true
mkdir -p "$COLLECT_DIR/$SHARD" 2>/dev/null || collected=false
summary=""
for file in "${matches[@]}"; do
  class="$(basename "$file" -output.txt)"
  firings="$(grep -acxF -- "$HEADER" "$file")"
  reasons="$(HEADER="$HEADER" awk 'after { print; after = 0 } $0 == ENVIRON["HEADER"] { after = 1 }' "$file")"
  echo "${class}: ${firings} firing(s)"
  HEADER="$HEADER" awk -v max="$BLOCK_LINES" '
    $0 == ENVIRON["HEADER"] { printing = 1; printed = 0 }
    printing && printed < max { print "    " $0; printed++ }
  ' "$file"
  cp "$file" "$COLLECT_DIR/$SHARD/" 2>/dev/null || collected=false
  summary+="- \`${class}\`: ${firings} firing(s)"$'\n'
  while IFS= read -r reason; do
    [ -n "$reason" ] && summary+="  - ${reason}"$'\n'
  done <<<"$reasons"
done

if [ "$collected" = true ]; then
  where="is in the artefacts under \`mockserver/target/failed-tests/shared-echo-guard/${SHARD}/\`"
else
  where="could not be copied for upload; see the job log"
fi
annotate "$CONTEXT" "**SharedEchoServerGuard fired in netty IT shard ${SHARD}**: a shared echo server stopped answering and was replaced. Full output, with the thread dump, ${where}.

${summary}"
exit 0
