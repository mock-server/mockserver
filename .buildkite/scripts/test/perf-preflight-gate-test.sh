#!/usr/bin/env bash
# Network-free checks for scripts/release/check-perf-preflight.sh: its grader self-test, the
# budget-mapping lint on the committed budget file, and the full gate on a run object trimmed
# from a real daily S3 run (fixtures/perf-preflight/), with its commit set to HEAD.
# Run: .buildkite/scripts/test/perf-preflight-gate-test.sh
#      (PERF_PREFLIGHT_SCRIPT=<path> to test another copy)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env PERF_PREFLIGHT_SCRIPT
GATE="${PERF_PREFLIGHT_SCRIPT:-$REPO_ROOT/scripts/release/check-perf-preflight.sh}"
BUDGETS="$REPO_ROOT/mockserver-performance-test/perf-budgets.json"
FIXTURE="$REPO_ROOT/.buildkite/scripts/test/fixtures/perf-preflight/run-a82cfcb1e6.json"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/perf-preflight-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
FAILS=0
ok()  { echo "  ok   $1"; }
bad() { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }

# expect <label> <want-rc> <want-text|-> -- <command...>; output goes to $WORK/out
expect() {
  local label="$1" want_rc="$2" want_text="$3" rc=0
  shift 4
  "$@" >"$WORK/out" 2>&1 || rc=$?
  if [ "$rc" != "$want_rc" ]; then
    bad "$label: exit $rc, expected $want_rc"; sed 's/^/       /' "$WORK/out" | tail -n 8 >&2; return 0
  fi
  if [ "$want_text" != "-" ] && ! grep -qF -- "$want_text" "$WORK/out"; then
    bad "$label: output lacks '$want_text'"; sed 's/^/       /' "$WORK/out" | tail -n 8 >&2; return 0
  fi
  ok "$label"
}

HEAD_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"
# run_gate <run.json>: the full gate in local-dir mode, measuring and releasing HEAD.
run_gate() {
  local dir="$WORK/runs-$RANDOM"
  mkdir -p "$dir"
  cp "$1" "$dir/2026-01-01T00-00-00Z__fixture.json"
  env PERF_PREFLIGHT_LOCAL_DIR="$dir" PERF_PREFLIGHT_RELEASE_COMMIT="$HEAD_SHA" \
      PERF_ACCEPTED_REGRESSIONS_FILE="$WORK/no-acceptances.json" "$GATE"
}
run_with() { # <jq filter> <out.json>: the real-shaped fixture, commit set to HEAD, then the filter
  jq --arg c "$HEAD_SHA" ".commit = \$c | $1" "$FIXTURE" > "$2"
}

echo "--- grader self-test"
expect "--self-test passes" 0 "Self-test PASSED" -- "$GATE" --self-test
expect "--self-test and --lint-budgets together are rejected" 64 "separate modes" -- "$GATE" --self-test --lint-budgets

echo "--- budget-mapping lint"
expect "the committed budget file has no unmapped gating budget" 0 "LINT OK" -- "$GATE" --lint-budgets
jq '.budgets["behaviours.new_arm.error_rate"] = {"dir":"up","min_pct":0,"floor":0.01,"gating":true}' "$BUDGETS" > "$WORK/budgets-new.json"
expect "a newly added gating absolute budget with no mapping fails the lint" 1 "behaviours.new_arm.error_rate" -- \
  env PERF_BUDGETS_FILE="$WORK/budgets-new.json" "$GATE" --lint-budgets
jq '.budgets["behaviours.new_arm.error_rate"] = {"dir":"up","min_pct":0,"floor":0.01,"gating":false}' "$BUDGETS" > "$WORK/budgets-advisory.json"
expect "a new NON-gating budget needs no mapping" 0 "LINT OK" -- \
  env PERF_BUDGETS_FILE="$WORK/budgets-advisory.json" "$GATE" --lint-budgets
jq '.budgets["behaviours.new_arm.p99_ms"] = {"dir":"up","min_pct":0.1,"floor":null,"gating":true}' "$BUDGETS" > "$WORK/budgets-relative.json"
expect "a new relative (floor:null) gating budget is delegated, not unmapped" 0 "LINT OK" -- \
  env PERF_BUDGETS_FILE="$WORK/budgets-relative.json" "$GATE" --lint-budgets
jq '.budgets |= with_entries(if (.value | type) == "object" then .value.gating = false else . end)' "$BUDGETS" > "$WORK/budgets-none.json"
expect "a budget file with no gating budget at all fails the lint" 1 "no gating:true budgets" -- \
  env PERF_BUDGETS_FILE="$WORK/budgets-none.json" "$GATE" --lint-budgets
mkdir -p "$WORK/gate-unmapped"
grep -v '"churn.alloc_ratio_index_n15000" then' "$GATE" > "$WORK/gate-unmapped/check-perf-preflight.sh"
expect "the gate with its churn mapping removed fails the lint on the committed budgets" 1 "churn.alloc_ratio_index_n15000" -- \
  env PERF_BUDGETS_FILE="$BUDGETS" bash "$WORK/gate-unmapped/check-perf-preflight.sh" --lint-budgets

echo "--- full gate on a real-shaped daily run object"
run_with '.' "$WORK/good.json"
expect "a valid, current, in-budget run PASSES" 0 "PASS:" -- run_gate "$WORK/good.json"
expect "  ... with both absolute gating budgets evaluated" 0 "BUDGETS ok: 2 gating absolute metric(s)" -- run_gate "$WORK/good.json"
run_with '.churn.alloc_ratio_index_n15000 = 1200' "$WORK/churn-bad.json"
expect "a churn/static allocation ratio over its floor is a BREACH" 1 "BREACH: gating budget churn.alloc_ratio_index_n15000" -- run_gate "$WORK/churn-bad.json"
run_with 'del(.churn)' "$WORK/churn-absent.json"
expect "a run without the churn ratio is INDETERMINATE" 2 "churn.alloc_ratio_index_n15000 could not be evaluated" -- run_gate "$WORK/churn-absent.json"
run_with '.forward_guard.error_rate = 0.5' "$WORK/fwd-bad.json"
expect "a forward error rate over its floor is still a BREACH" 1 "BREACH: gating budget forward.error_rate" -- run_gate "$WORK/fwd-bad.json"

echo "--- RECENCY against a scratch history: which later commits still ship the measured code"
# A throwaway repository, so the real clone gains no objects.
G="$WORK/history"
git init -q "$G"
commit() { # <path> -> the new commit's SHA
  mkdir -p "$G/$(dirname "$1")"; echo "$RANDOM" >> "$G/$1"
  git -C "$G" add -A
  GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t git -C "$G" -c commit.gpgsign=false commit -q -m "$1"
  git -C "$G" rev-parse HEAD
}
MEASURED="$(commit mockserver/core.java)"
DOCS_TIP="$(commit docs/notes.md)"
PLUGIN_TIP="$(commit mockserver/mockserver-maven-plugin/pom.xml)"
JAVA_TIP="$(commit mockserver/later.java)"
git -C "$G" checkout -q "$PLUGIN_TIP"
DOCKER_TIP="$(commit docker/graaljs/Dockerfile)"
# release_gate <run.json> <release commit>: the gate on the scratch history
release_gate() {
  local dir="$WORK/runs-$RANDOM"
  mkdir -p "$dir"
  cp "$1" "$dir/2026-01-01T00-00-00Z__fixture.json"
  env PERF_PREFLIGHT_LOCAL_DIR="$dir" PERF_PREFLIGHT_GIT_REPO="$G" PERF_PREFLIGHT_RELEASE_COMMIT="$2" \
      PERF_ACCEPTED_REGRESSIONS_FILE="$WORK/no-acceptances.json" "$GATE"
}
jq --arg c "$MEASURED" --arg h "$DOCS_TIP" '.commit = $c | .harness_commit = $h' "$FIXTURE" > "$WORK/measured.json"
expect "docs-only commits since the measured one: EQUIVALENT, passes" 0 "RECENCY ok (EQUIVALENT)" -- release_gate "$WORK/measured.json" "$DOCS_TIP"
expect "a maven-plugin-only change since: EQUIVALENT, passes" 0 "RECENCY ok (EQUIVALENT)" -- release_gate "$WORK/measured.json" "$PLUGIN_TIP"
expect "a mockserver/ change since: STALE" 1 "STALE:" -- release_gate "$WORK/measured.json" "$JAVA_TIP"
expect "  ... and the remedy names a [perf-run] build on the release commit" 1 "at commit ${JAVA_TIP} with [perf-run] in its message" -- release_gate "$WORK/measured.json" "$JAVA_TIP"
expect "  ... and names the build the run was on" 1 "That run's build was on ${DOCS_TIP:0:12} and was served the :snapshot image built from ${MEASURED:0:12}" -- release_gate "$WORK/measured.json" "$JAVA_TIP"
expect "a docker/ change since: STALE, remedy rebuilds the :snapshot images" 1 "start a mockserver-java build on master at ${DOCKER_TIP}" -- release_gate "$WORK/measured.json" "$DOCKER_TIP"
jq --arg c "$MEASURED" '.commit = $c | .harness_commit = "HEAD"' "$FIXTURE" > "$WORK/symbolic.json"
release_gate "$WORK/symbolic.json" "$JAVA_TIP" > "$WORK/symbolic.out" 2>&1 || true
if grep -qF "was served the :snapshot image" "$WORK/symbolic.out"; then bad "a symbolic harness_commit is reported as a different build"; else ok "a symbolic harness_commit is not reported as a different build"; fi

echo
if [ "$FAILS" -gt 0 ]; then echo ":x: $FAILS check(s) failed" >&2; exit 1; fi
echo "all perf preflight gate checks passed"
