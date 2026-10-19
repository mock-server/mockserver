#!/usr/bin/env bash
# Fixture tests for perf-test-info-investigation.sh and lib/perf-info-inv.sh, no Docker needed:
# section 1 runs the cell verdict on build 697's own cells.jsonl and mutations of it; sections 2-4 run
# the step with `docker`, `curl` and `buildkite-agent` stubbed on PATH (a cancelled step leaves no
# container, an unmeasured cell fails the step, a fully measured one passes).
# Run: .buildkite/scripts/test/perf-info-investigation-test.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env
STEP="$REPO_ROOT/.buildkite/scripts/steps/perf-test-info-investigation.sh"
# shellcheck source=../steps/lib/perf-info-inv.sh
. "$REPO_ROOT/.buildkite/scripts/steps/lib/perf-info-inv.sh"
CELLS="$REPO_ROOT/.buildkite/scripts/test/fixtures/info-investigation/cells-697.jsonl"
T="$(mktemp -d "${TMPDIR:-/tmp}/perf-info-inv-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
FAILS=0
ok()  { echo "  ok   $1"; }
bad() { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi; }

verdict() { # cells expected overload -> "rc|first line"
  local out rc=0
  out="$(inv_cells_verdict "$1" "$2" "$3")" || rc=$?
  printf '%s|%s' "$rc" "$(head -1 <<<"$out")"
}
mutate() { # jq filter applied to the first cell only -> file
  jq -c --argjson first "$(head -1 "$CELLS" | jq -c .)" "if . == \$first then $1 else . end" "$CELLS" > "$T/m.jsonl"
  echo "$T/m.jsonl"
}

echo "--- 1. the cell verdict"
check "build 697's eight cells are all measured" "0|" "$(verdict "$CELLS" 8 true)"
check "a missing cell fails" "1|expected 9 cells, found 8" "$(verdict "$CELLS" 9 true)"
check "a SUT that never started fails" "1|json10k-INFO-G1: status sut_failed" "$(verdict "$(mutate '{cell, status: "sut_failed"}')" 8 true)"
check "a failed drained probe fails" "1|json10k-INFO-G1: drained probe failed" "$(verdict "$(mutate '.drained_probe.status = "failed"')" 8 true)"
check "a probe with problems fails" "1|json10k-INFO-G1: drained probe measured_with_problems" "$(verdict "$(mutate '.drained_probe.status = "measured_with_problems"')" 8 true)"
check "a fill short of 93% fails" "1|json10k-INFO-G1: the fill reached 0.5 of the budget" "$(verdict "$(mutate '.fill.peak_fraction_of_budget = 0.5')" 8 true)"
check "a fill with no budget reading fails" "1|json10k-INFO-G1: the fill reached null of the budget" "$(verdict "$(mutate '.fill.peak_fraction_of_budget = null')" 8 true)"
check "an INFO cell without its overload phase fails" "1|json10k-INFO-G1: no overload phase" "$(verdict "$(mutate '.overload = null')" 8 true)"
check "... unless the overload phase was switched off" "0|" "$(verdict "$(mutate '.overload = null')" 8 false)"
check "an overload window opened below 93% fails" "1|json10k-INFO-G1: the overload window opened below 93% of the budget" "$(verdict "$(mutate '.overload.reached_93pct_before_window = false')" 8 true)"
check "an overload sample without a ring figure fails" "1|json10k-INFO-G1: an overload sample has no whole-log or ring figure" "$(verdict "$(mutate '.overload.samples[1].ring_multiple = null')" 8 true)"
jq -c 'if .profile == "proxy10k" then .upstream_retained_entries = 0 else . end' "$CELLS" > "$T/noup.jsonl"
check "a proxy cell whose upstream saw nothing fails" "1|proxy10k-INFO-G1: no request reached the upstream" "$(verdict "$T/noup.jsonl" 8 true)"
printf '{"cell":\n' > "$T/broken.jsonl"
check "an unparsable cells file fails" "1" "$(verdict "$T/broken.jsonl" 1 true | cut -d'|' -f1)"

# --- stubs: containers live in $STUB_DIR/containers as "name label,label", one per line
STUB_DIR="$T/stub"; mkdir -p "$STUB_DIR/bin"
cat > "$STUB_DIR/bin/docker" <<'STUB'
#!/usr/bin/env bash
d="$STUB_DIR"; echo "docker $*" >> "$d/calls.log"; touch "$d/containers"
cmd="$1"; shift
case "$cmd" in
  run)
    all="$*"; name=""; label=""; detach=false
    while [ $# -gt 0 ]; do case "$1" in --name) name="$2"; shift ;; --label) label="${label:+$label,}$2"; shift ;; -d) detach=true ;; esac; shift; done
    case "$name" in k6-*) rm -f "$d/reset" ;; esac
    if [ "$detach" = true ]; then echo "$name $label" >> "$d/containers"; echo "$name"; exit 0; fi
    case "$all" in
      *GC.class_histogram*) printf ' num     #instances         #bytes  class name\nTotal         10        1000000\n' ;;
      */proc/1/status*) echo "1000 1000" ;;
    esac ;;
  ps)
    f="$3"
    case "$f" in
      label=*=*) awk -v l="${f#label=}" '{n = split($2, a, ","); for (i = 1; i <= n; i++) if (a[i] == l) {print $1; break}}' "$d/containers" ;;
      label=*) awk -v l="${f#label=}=" '{n = split($2, a, ","); for (i = 1; i <= n; i++) if (index(a[i], l) == 1) {print $1; break}}' "$d/containers" ;;
      name=*) awk '{print $1}' "$d/containers" | grep -E -- "${f#name=}" || true ;;
    esac ;;
  rm|kill)
    [ "$cmd" = rm ] && [ -n "${STUB_SLOW_RM:-}" ] && sleep 1
    for n in "$@"; do [ "$n" = -f ] && continue; awk -v n="$n" '$1 != n' "$d/containers" > "$d/c.tmp"; mv "$d/c.tmp" "$d/containers"; done ;;
  inspect)
    if [ -n "${STUB_FAIL_SUT:-}" ]; then echo exited; else echo running; fi ;;
  port) echo 127.0.0.1:1 ;;
esac
exit 0
STUB
cat > "$STUB_DIR/bin/curl" <<'STUB'
#!/usr/bin/env bash
d="$STUB_DIR"
case "$*" in
  *169.254.169.254*) exit 7 ;;
  */mockserver/metrics*)
    b=100; { [ -f "$d/reset" ] || [ -n "${STUB_NEVER_FILL:-}" ]; } && b=10
    printf '%s\n' "mock_server_event_log_retained_bytes $b" "mock_server_event_log_retained_entries 5" \
      "mock_server_event_log_max_retained_bytes 100" "mock_server_event_log_ring_occupancy 0" \
      "mock_server_event_log_in_flight_bytes 0" "mock_server_event_log_max_in_flight_bytes 100" ;;
  */mockserver/reset*) touch "$d/reset" ;;
esac
exit 0
STUB
printf '#!/usr/bin/env bash\necho "buildkite-agent $*" >> "$STUB_DIR/calls.log"\n' > "$STUB_DIR/bin/buildkite-agent"
chmod +x "$STUB_DIR/bin/"*

run_step() { # extra env... -> runs the heap step in the background, pid in STEP_PID
  : > "$STUB_DIR/calls.log"; : > "$STUB_DIR/containers"; rm -f "$STUB_DIR/reset"
  env PATH="$STUB_DIR/bin:$PATH" STUB_DIR="$STUB_DIR" TMPDIR="$T" BUILDKITE_BUILD_NUMBER=900 BUILDKITE_AGENT_NAME=agent-a \
    PERF_INFO_INVESTIGATION_PART=heap PERF_INV_OUT_ROOT="$T/out" PERF_INV_COLLECTORS=G1 \
    PERF_INV_PROFILES=json10k PERF_INV_LEVELS=WARN PERF_INV_OVERLOAD_S=0 "$@" \
    bash "$STEP" > "$T/step.log" 2>&1 &
  STEP_PID=$!
}
live_containers() { awk 'NF' "$STUB_DIR/containers" | wc -l | tr -d ' '; }

echo "--- 2. a cancelled step leaves no container behind"
run_step STUB_NEVER_FILL=1 PERF_INV_FILL_TIMEOUT_S=600
for _ in $(seq 1 200); do grep -q '^k6-inv-' "$STUB_DIR/containers" 2>/dev/null && break; sleep 0.1; done
if grep -q '^k6-inv-' "$STUB_DIR/containers"; then ok "the fill's k6 container was running when the step was cancelled"
else bad "the fill never started a k6 container: $(tail -5 "$T/step.log")"; fi
check "the SUT and the fill both carry the run label" "2" "$(grep -c ' mockserver-perf-inv=900-' "$STUB_DIR/containers" || true)"
check "... and the agent label" "2" "$(grep -c 'mockserver-perf-inv.agent=agent-a' "$STUB_DIR/containers" || true)"
kill -TERM "$STEP_PID"; rc=0; wait "$STEP_PID" || rc=$?
check "a TERM ends the step with 143" "143" "$rc"
check "no container is left after the cancel" "0" "$(live_containers)"
if grep -q '^docker network rm perf-inv-900-' "$STUB_DIR/calls.log"; then ok "the network is removed"; else bad "the network was not removed"; fi
if grep -q 'buildkite-agent artifact upload' "$STUB_DIR/calls.log"; then ok "partial results are still uploaded"; else bad "nothing was uploaded"; fi

run_step STUB_NEVER_FILL=1 STUB_SLOW_RM=1 PERF_INV_FILL_TIMEOUT_S=600
for _ in $(seq 1 200); do grep -q '^k6-inv-' "$STUB_DIR/containers" 2>/dev/null && break; sleep 0.1; done
kill -TERM "$STEP_PID"
for _ in $(seq 1 100); do grep -q '^docker rm -f' "$STUB_DIR/calls.log" && break; sleep 0.05; done
kill -TERM "$STEP_PID" 2>/dev/null || true; rc=0; wait "$STEP_PID" || rc=$?
check "a second TERM during cleanup does not cut it short" "143|0|yes" \
  "$rc|$(live_containers)|$(grep -q '^docker network rm perf-inv-900-' "$STUB_DIR/calls.log" && echo yes || echo no)"

echo "--- 3. the reaps stay in scope"
: > "$STUB_DIR/calls.log"
printf '%s\n' "k6-inv-1-old mockserver-perf-inv=1-1,mockserver-perf-inv.agent=agent-a" \
  "k6-inv-2-other mockserver-perf-inv=2-2,mockserver-perf-inv.agent=agent-b" "unrelated-container other=1" > "$STUB_DIR/containers"
( PATH="$STUB_DIR/bin:$PATH"; export STUB_DIR BUILDKITE_AGENT_NAME=agent-a; inv_reap )
check "the startup reap removes only this agent's leftovers" "k6-inv-2-other unrelated-container" "$(awk '{print $1}' "$STUB_DIR/containers" | tr '\n' ' ' | sed 's/ $//')"
printf '%s\n' "k6-inv-694-1234-cell" "info-heap-probe-perf-inv-sut-694-1234-cell-before" "k6-inv-sweep-694-1234" \
  "k6-inv-694-12345-cell" "k6-inv-1694-1234-cell" > "$STUB_DIR/containers"
( PATH="$STUB_DIR/bin:$PATH"; export STUB_DIR; inv_reap 694-1234 )
check "the run reap matches its own run id only" "k6-inv-694-12345-cell k6-inv-1694-1234-cell" "$(awk '{print $1}' "$STUB_DIR/containers" | tr '\n' ' ' | sed 's/ $//')"

echo "--- 4. the step fails closed"
run_step STUB_FAIL_SUT=1; rc=0; wait "$STEP_PID" || rc=$?
check "a SUT that never starts fails the step" "1" "$rc"
if grep -q 'json10k-WARN-G1: status sut_failed' "$T/step.log"; then ok "the failure names the cell"; else bad "the failure does not name the cell: $(tail -5 "$T/step.log")"; fi
if grep -q 'buildkite-agent annotate --style error' "$STUB_DIR/calls.log"; then ok "an error annotation is posted"; else bad "no error annotation"; fi
check "no container is left after the failure" "0" "$(live_containers)"
run_step; rc=0; wait "$STEP_PID" || rc=$?
check "a fully measured cell passes" "0" "$rc"
check "its cell record is measured" "measured measured" "$(jq -r '"\(.status) \(.drained_probe.status)"' "$T/out/heap/cells.jsonl" 2>/dev/null)"
check "no container is left after a pass" "0" "$(live_containers)"

[ "$FAILS" -eq 0 ] || { echo "FAILED: $FAILS check(s)" >&2; exit 1; }
echo "perf-info-investigation-test: all checks passed"
