#!/usr/bin/env bash
# Fixture tests for lib/perf-k6-interrupted.sh, no Docker needed.
# The fixtures are trimmed rw-multi-k6 process logs from real grafana/k6 1.7.1 runs: one clean,
# one against a SUT delaying every response 2 s with a 1 s gracefulStop (interruptions).
# Run: .buildkite/scripts/test/perf-k6-interrupted-test.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env PERF_K6INT_HARNESS
FIX="$REPO_ROOT/.buildkite/scripts/test/fixtures"
# shellcheck source=../steps/lib/perf-k6-interrupted.sh
. "$REPO_ROOT/.buildkite/scripts/steps/lib/perf-k6-interrupted.sh"
T="$(mktemp -d "${TMPDIR:-/tmp}/perf-k6int-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
field() { jq -r ".$2 | tostring" <<<"$1"; }
CLEAN="$FIX/k6-1.7.1-progress-clean.txt"
INTR="$FIX/k6-1.7.1-progress-interrupted.txt"

echo "--- 1. parse real k6 1.7.1 progress output"
E="$(k6_interrupted_json main-p0 "$CLEAN")"
check "clean: interrupted 0" "0" "$(field "$E" interrupted)"
check "clean: complete from the last line (gctrace lines interleaved)" "539964" "$(field "$E" complete)"
check "clean: no first interruption" "null" "$(field "$E" first_interrupted_at_s)"
E="$(k6_interrupted_json main-p1 "$INTR")"
check "interrupted: the LAST line's count, not the first" "136" "$(field "$E" interrupted)"
check "interrupted: complete" "1342" "$(field "$E" complete)"
check "interrupted: first non-zero line's elapsed (0m49.0s)" "49" "$(field "$E" first_interrupted_at_s)"
check "proc carried" "main-p1" "$(field "$E" proc)"
printf 'running (1h02m05.5s), 0000/2049 VUs, 10 complete and 3 interrupted iterations\r\n' > "$T/hm.log"
check "h/m/s elapsed and CRLF" "3725.5 3" "$(jq -r '"\(.first_interrupted_at_s) \(.interrupted)"' <<<"$(k6_interrupted_json p "$T/hm.log")")"

echo "--- 2. no readable count is null, never 0"
grep -v '^running' "$CLEAN" > "$T/quiet.log"
check "a log without progress lines (--quiet)" "null" "$(field "$(k6_interrupted_json p "$T/quiet.log")" interrupted)"
check "a missing log" "null" "$(field "$(k6_interrupted_json p "$T/absent.log")" interrupted)"
sed 's/ interrupted iterations/ stopped iterations/' "$CLEAN" > "$T/reworded.log"
check "a reworded progress line (k6 format change)" "null" "$(field "$(k6_interrupted_json p "$T/reworded.log")" interrupted)"

echo "--- 3. rw_no_interrupted_iterations check"
C0="$(k6_interrupted_json main-p0 "$CLEAN")"; C1="$(k6_interrupted_json main-p1 "$CLEAN")"
C="$(k6_interrupted_check "[$C0,$C1]")"
check "all processes clean -> ok" "true ok" "$(jq -r '"\(.ok) \(.detail)"' <<<"$C")"
check "check name" "rw_no_interrupted_iterations" "$(field "$C" name)"
C="$(k6_interrupted_check "[$C0,$(k6_interrupted_json main-p1 "$INTR")]")"
check "one process interrupted -> fails" "false" "$(field "$C" ok)"
check "detail names the process and count" "true" "$(jq -r '.detail | test("main-p1 136 interrupted \\(first at 49 s\\)")' <<<"$C")"
C="$(k6_interrupted_check "[$C0,$(k6_interrupted_json xcheck-p0 "$T/quiet.log")]")"
check "an unreadable count -> fails" "false" "$(field "$C" ok)"
check "detail names the unreadable process" "true" "$(jq -r '.detail | test("xcheck-p0 count unreadable")' <<<"$C")"
check "no processes -> fails" "false" "$(field "$(k6_interrupted_check '[]')" ok)"

echo "--- 4. the harness wires the check in (bound to its own blocks, not anywhere in the file)"
HARNESS="${PERF_K6INT_HARNESS:-$REPO_ROOT/mockserver-performance-test/scripts/rw-multi-k6-sweep.sh}"
block() { awk -v a="$1" -v b="$2" 'index($0, a) == 1 {on = 1} on {print} on && index($0, b) {exit}' "$HARNESS"; }
VAL="$(block 'VALIDITY="$(jq ' '] as $checks')"
OUT="$(block 'jq -n --argjson synth ' '> "$WORK/result.json"')"
PHASE="$(block 'run_phase() {' '/k6/sweep.js >/dev/null' | grep -F '"$K6_IMAGE" run ' || true)"
has() { grep -qE -- "$1" <<<"$2" && echo yes || echo no; }
check "VALIDITY passes the check in" "yes" "$(has '--argjson intcheck "\$INTERRUPTED_CHECK"' "$VAL")"
check "VALIDITY lists \$intcheck among its checks" "yes" "$(has '^[[:space:]]*\$intcheck,?[[:space:]]*$' "$VAL")"
check "the output passes the counts in" "yes" "$(has '--argjson k6int "\$K6_INTERRUPTED"' "$OUT")"
check "per_process merges interrupted_iterations from \$k6int" "yes" "$(has 'per_process: \[ \$pp\[\] .*interrupted_iterations: .*\$k6int\[\]' "$OUT")"
check "the measured k6 run is not --quiet" "no" "$(has '--quiet' "$PHASE")"
check "the measured k6 run line was found" "yes" "$(has '"\$K6_IMAGE" run --tag "proc=' "$PHASE")"

if [ "$FAILS" -gt 0 ]; then echo ":x: $FAILS k6 interrupted-iteration check(s) failed" >&2; exit 1; fi
echo "--- all k6 interrupted-iteration fixture checks passed"
