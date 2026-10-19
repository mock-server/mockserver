#!/usr/bin/env bash
# Fixture tests for perf-test-info-investigation.sh and lib/perf-info-inv.sh, no Docker needed:
# section 1 runs the cell verdict on build 697's own cells.jsonl and mutations of it; sections 2-4 run
# the step with `docker`, `curl` and `buildkite-agent` stubbed on PATH (a cancelled step leaves no
# container, an unmeasured cell fails the step, a fully measured one passes); section 5 runs the
# promotion analysis on fixture GC logs, and section 6 runs PART=promotion on the same stubs.
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
check "an overload sample without a ring figure fails" "1|json10k-INFO-G1: an overload sample has no ring figure" "$(verdict "$(mutate '.overload.samples[1].ring_multiple = null')" 8 true)"
check "an overload sample without a whole-log figure fails" "1|json10k-INFO-G1: an overload sample has no whole-log figure" "$(verdict "$(mutate '.overload.samples[1].whole_log_bytes = null')" 8 true)"
check "an overload phase with no samples fails" "1|json10k-INFO-G1: an overload sample has no whole-log figure" "$(verdict "$(mutate '.overload.samples = []')" 8 true)"
# The consumer kept up: the ring held a few MB at most, one sample was empty, and the load evicted throughout.
SMALL_RING='.overload.samples |= [.[] | .in_flight_bytes = 723185 | .ring_multiple = 1.586] | .overload.samples[0] |= (.in_flight_bytes = 0 | .ring_multiple = null)
  | .overload.window_open_evicted = 100 | .overload.samples |= [to_entries[] | .value + {evicted: (200 + .key * 100)}] | .overload.load_running_at_end = true
  | .overload.counters_after.dropped_ring_full = .counters_after_fill.dropped_ring_full
  | .overload.counters_after.dropped_in_flight_bytes = .counters_after_fill.dropped_in_flight_bytes'
check "a ring that never backed up is not reached, not failed" "0|" "$(verdict "$(mutate "$SMALL_RING")" 8 true)"
check "... and is recorded as not_reached" "not_reached" "$(jq -r "$INV_RING_BACKED_UP_JQ"' select(.cell == "json10k-INFO-G1") | overload_status' "$(mutate "$SMALL_RING")")"
check "... with no ring figure at all" "0|" "$(verdict "$(mutate "$SMALL_RING"' | .overload.samples[].ring_multiple = null')" 8 true)"
check "a backed-up ring is still measured" "measured" "$(jq -r "$INV_RING_BACKED_UP_JQ"' select(.cell == "json10k-INFO-G1") | overload_status' "$CELLS")"
check "a not-reached phase still needs its whole-log figure" "1|json10k-INFO-G1: an overload sample has no whole-log figure" "$(verdict "$(mutate "$SMALL_RING"' | .overload.samples[1].whole_log_bytes = null')" 8 true)"
check "an unreadable in-flight gauge fails, never reads as an empty ring" "1|json10k-INFO-G1: an overload sample has no in-flight reading" "$(verdict "$(mutate "$SMALL_RING"' | .overload.samples[0].in_flight_bytes = null')" 8 true)"
check "an unreadable in-flight cap fails" "1|json10k-INFO-G1: the overload has no in-flight cap reading" "$(verdict "$(mutate "$SMALL_RING"' | .overload.counters_after.max_in_flight_bytes = null')" 8 true)"
check "ring_full drops in the phase count as backed up, so a missing ring figure fails" "1|json10k-INFO-G1: an overload sample has no ring figure" \
  "$(verdict "$(mutate "$SMALL_RING"' | .overload.counters_after.dropped_ring_full = ((.counters_after_fill.dropped_ring_full // 0) + 1) | .overload.samples[1].ring_multiple = null')" 8 true)"
check "in-flight byte drops in the phase count as backed up, so a missing ring figure fails" "1|json10k-INFO-G1: an overload sample has no ring figure" \
  "$(verdict "$(mutate "$SMALL_RING"' | .overload.counters_after.dropped_in_flight_bytes = ((.counters_after_fill.dropped_in_flight_bytes // 0) + 1) | .overload.samples[1].ring_multiple = null')" 8 true)"
check "a phase backed up only by drops, with an empty ring at every sample and its load gone, fails" "1|json10k-INFO-G1: the overload backed up, but no sample has a ring figure" \
  "$(verdict "$(mutate "$SMALL_RING"' | .overload.samples[].in_flight_bytes = 0 | .overload.samples[].ring_multiple = null | .overload.load_running_at_end = false
     | .overload.counters_after.dropped_in_flight_bytes = ((.counters_after_fill.dropped_in_flight_bytes // 0) + 1)')" 8 true)"
LOAD_STOPPED="1|json10k-INFO-G1: the ring never backed up, but the overload load is not shown to have run through the window"
check "an overload whose load generator was gone at the end is failed, not not_reached" "$LOAD_STOPPED" "$(verdict "$(mutate "$SMALL_RING"' | .overload.load_running_at_end = false')" 8 true)"
check "... as is one whose evictions stopped between samples" "$LOAD_STOPPED" "$(verdict "$(mutate "$SMALL_RING"' | .overload.samples[2].evicted = .overload.samples[1].evicted')" 8 true)"
check "... or that has no eviction reading at the window's open" "$LOAD_STOPPED" "$(verdict "$(mutate "$SMALL_RING"' | .overload.window_open_evicted = null')" 8 true)"
check "... or fewer than three samples" "$LOAD_STOPPED" "$(verdict "$(mutate "$SMALL_RING"' | .overload.samples |= .[:2]')" 8 true)"
check "... or none in a sample" "$LOAD_STOPPED" "$(verdict "$(mutate "$SMALL_RING"' | .overload.samples[0].evicted = null')" 8 true)"
check "... or no load record at all" "$LOAD_STOPPED" "$(verdict "$(mutate "$SMALL_RING"' | del(.overload.load_running_at_end, .overload.window_open_evicted)')" 8 true)"
check "a recorded status that its samples contradict fails" "1|json10k-INFO-G1: overload status not_reached does not match its samples" "$(verdict "$(mutate '.overload.status = "not_reached"')" 8 true)"
check "... as does an unknown one" "1|json10k-INFO-G1: overload status bogus does not match its samples" "$(verdict "$(mutate "$SMALL_RING"' | .overload.status = "bogus"')" 8 true)"
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
    all="$*"; name=""; label=""; detach=false; out=""; result=""
    while [ $# -gt 0 ]; do case "$1" in
      --name) name="$2"; shift ;; --label) label="${label:+$label,}$2"; shift ;; -d) detach=true ;;
      -v) case "$2" in *:/out) out="${2%:/out}" ;; *:/diag) echo "${2%:/diag}" > "$d/diag" ;; esac; shift ;;
      -e) case "$2" in K6_SWEEP_RESULT_PATH=*) result="${2#*=/out/}" ;; JAVA_TOOL_OPTIONS=*file=/diag/*) c="${2#*file=/diag/}"; echo "${c%%/*}" > "$d/gccell" ;; esac; shift ;;
    esac; shift; done
    case "$name" in k6-*) rm -f "$d/reset" ;; esac
    if [ "$detach" = true ]; then echo "$name $label" >> "$d/containers"; echo "$name"; exit 0; fi
    if [ -n "$result" ] && [ -n "$out" ] && [[ "$name" == *-sweep ]]; then
      python3 - "$out/$result" "$(cat "$d/diag")/$(cat "$d/gccell")/gc-1.log" <<'PY'
import datetime, json, sys, time
start = time.time()
time.sleep(4.5)
iso = lambda t: datetime.datetime.fromtimestamp(t, datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3] + '+0000'
json.dump({'points': [{'offered_rps': 2000, 'p50_ms': 0.4, 'start_epoch_ms': int(start * 1000)}]}, open(sys.argv[1], 'w'))
with open(sys.argv[2], 'w') as f:
    f.write('[%s][0.1s][info][gc     ] Using G1\n[%s][0.1s][info][gc,init] Heap Region Size: 1M\n' % (iso(start), iso(start)))
    f.write('[%s][2.0s][info][gc,heap     ] GC(1) Eden regions: 40->0(60)\n' % iso(start + 2))
    f.write('[%s][2.0s][info][gc,heap     ] GC(1) Old regions: 10->12\n' % iso(start + 2))
    f.write('[%s][2.0s][info][gc          ] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 80M->30M(200M) 3.000ms\n' % iso(start + 2))
PY
      exit 0
    fi
    case "$all" in
      *PrintFlagsFinal*) echo "   size_t MaxHeapSize                              = 484442112                                 {product} {ergonomic}" ;;
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
    case "${*: -1}" in k6-*-ol) [ -n "${STUB_K6_DIED:-}" ] && exit 1 ;; esac
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
    if [ -n "${STUB_PROMO:-}" ]; then
      e=$(( $(cat "$d/evicted" 2>/dev/null || echo 0) + 1000 )); echo "$e" > "$d/evicted"
      printf '%s\n' "mock_server_event_log_retained_bytes ${STUB_RETAINED:-38622208}" "mock_server_event_log_retained_entries 500" \
        "mock_server_event_log_max_retained_bytes ${STUB_MAX_RETAINED:-38622208}" "mock_server_event_log_max_in_flight_bytes 66209792" \
        "mock_server_event_log_ring_occupancy 10" "mock_server_event_log_in_flight_bytes 0" "mock_server_evicted_log_entries_total $e"
      exit 0
    fi
    b=100; { [ -f "$d/reset" ] || [ -n "${STUB_NEVER_FILL:-}" ]; } && b=10
    e=$(( $(cat "$d/evicted" 2>/dev/null || echo 0) + 1000 )); echo "$e" > "$d/evicted"
    printf '%s\n' "mock_server_event_log_retained_bytes $b" "mock_server_event_log_retained_entries 5" "mock_server_evicted_log_entries_total $e" \
      "mock_server_event_log_max_retained_bytes 100" "mock_server_event_log_ring_occupancy 0" \
      "mock_server_event_log_in_flight_bytes 0" "mock_server_event_log_max_in_flight_bytes 100" ;;
  */mockserver/reset*) touch "$d/reset" ;;
esac
exit 0
STUB
printf '#!/usr/bin/env bash\necho "buildkite-agent $*" >> "$STUB_DIR/calls.log"\n' > "$STUB_DIR/bin/buildkite-agent"
chmod +x "$STUB_DIR/bin/"*

run_step() { # extra env... -> runs the heap step in the background, pid in STEP_PID
  : > "$STUB_DIR/calls.log"; : > "$STUB_DIR/containers"; rm -f "$STUB_DIR/reset" "$STUB_DIR/evicted"
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
run_step PERF_INV_LEVELS=INFO PERF_INV_OVERLOAD_S=3; rc=0; wait "$STEP_PID" || rc=$?
check "an INFO cell whose ring never backed up passes" "0" "$rc"
check "its overload is recorded as not_reached" "not_reached" "$(jq -r '.overload.status' "$T/out/heap/cells.jsonl" 2>/dev/null)"
check "... in the summary too" "not_reached" "$(awk -F'\t' 'NR == 2 {print $NF}' "$T/out/heap/summary.tsv" 2>/dev/null)"
if grep -q 'annotate --style info --context perf-info-investigation-not-reached' "$STUB_DIR/calls.log"; then ok "a not-reached annotation is posted"; else bad "no not-reached annotation: $(tail -5 "$T/step.log")"; fi
run_step PERF_INV_LEVELS=INFO PERF_INV_OVERLOAD_S=3 STUB_K6_DIED=1; rc=0; wait "$STEP_PID" || rc=$?
check "an INFO cell whose overload load generator died fails the step" "1" "$rc"
if grep -q 'json10k-INFO-G1: the ring never backed up, but the overload load is not shown' "$T/step.log"; then ok "the failure names the stopped load"; else bad "the failure does not name the stopped load: $(tail -5 "$T/step.log")"; fi

echo "--- 5. the promotion analysis on fixture GC logs"
PROMO_PY="$REPO_ROOT/.buildkite/scripts/steps/lib/perf-info-promotion.py"
FX="$REPO_ROOT/.buildkite/scripts/test/fixtures/info-investigation"
if ! command -v python3 >/dev/null 2>&1; then
  if [ "${BUILDKITE:-}" = "true" ]; then bad "python3 absent: the promotion analysis is untested"; else echo "  SKIP python3 absent"; fi
else
  promo() { python3 -I "$PROMO_PY" "$FX/promotion-$1-gc.txt" "$FX/promotion-sweep.json" "$FX/promotion-samples.txt" 5 30 40000000 | jq -r "$2"; }
  check "ZGC: the collector is read from the log" "ZGC" "$(promo zgc .collector)"
  check "ZGC: young collections, majors and stalls inside the window only" "6 1 1" "$(promo zgc '.rungs[0] | "\(.young_collections) \(.major_collections) \(.allocation_stalls)"')"
  check "ZGC: promotion is the Relocate End Promoted row, the major's young included" "2.2 4.167" "$(promo zgc '.rungs[0] | "\(.promoted_mb_s) \(.young_interval_s)"')"
  check "ZGC: allocation per young cycle counts the gap and the cycle" "82 19.7" "$(promo zgc '.rungs[0] | "\(.alloc_mb_per_young) \(.alloc_mb_s)"')"
  check "ZGC: lifetime is retained entries over the eviction rate" "10 2.4 0.048" "$(promo zgc '.rungs[0] | "\(.lifetime_s) \(.lifetime_over_young_interval) \(.ring_wait_over_young_interval)"')"
  check "ZGC: drops are the window's deltas" "7 2" "$(promo zgc '.rungs[0] | "\(.dropped_ring_full) \(.dropped_in_flight_bytes)"')"
  check "a rung that never started is not measured" "rung did not start" "$(promo zgc '.rungs[1].status')"
  check "G1: old-region growth in young-only pauses; the Mixed pause is counted, not summed" "G1 5 1 0.64" "$(promo g1 '"\(.collector) \(.rungs[0].young_collections) \(.rungs[0].mixed_pauses) \(.rungs[0].promoted_mb_s)"')"
  check "G1: a concurrent mark cycle counts as a major; eden is the allocation" "1 50" "$(promo g1 '.rungs[0] | "\(.major_collections) \(.alloc_mb_per_young)"')"
  unparsed() { python3 -I "$PROMO_PY" "$1" "$FX/promotion-sweep.json" "$FX/promotion-samples.txt" 5 30 40000000 | jq -r '.rungs[0] | "\(.status) \(.promoted_mb_s)"'; }
  grep -v 'Promoted:' "$FX/promotion-zgc-gc.txt" > "$T/zgc-nopromoted.txt"
  check "ZGC: a log without the Promoted row fails the rung, never reads as 0" "promotion unparsed null" "$(unparsed "$T/zgc-nopromoted.txt")"
  grep -v 'Heap Region Size' "$FX/promotion-g1-gc.txt" > "$T/g1-noregion.txt"
  check "G1: a log without the region size fails the rung, never reads as 0" "promotion unparsed null" "$(unparsed "$T/g1-noregion.txt")"
  grep -v 'Allocated:' "$FX/promotion-zgc-gc.txt" > "$T/zgc-noallocated.txt"
  check "ZGC: a log without the Allocated row fails the rung, never reads as less" "allocation unparsed" \
    "$(python3 -I "$PROMO_PY" "$T/zgc-noallocated.txt" "$FX/promotion-sweep.json" "$FX/promotion-samples.txt" 5 30 40000000 | jq -r '.rungs[0].status')"
  python3 -I "$PROMO_PY" "$T/zgc-nopromoted.txt" "$FX/promotion-sweep.json" "$FX/promotion-samples.txt" 5 30 40000000 > "$T/unparsed.json"
fi

echo "--- 6. the promotion part, on stubbed docker and curl"
run_promo() { # extra env... -> runs PART=promotion to completion, rc in PROMO_RC
  : > "$STUB_DIR/calls.log"; : > "$STUB_DIR/containers"; rm -f "$STUB_DIR/reset" "$STUB_DIR/evicted"; rm -rf "$T/out"
  PROMO_RC=0
  env PATH="$STUB_DIR/bin:$PATH" STUB_DIR="$STUB_DIR" TMPDIR="$T" BUILDKITE_BUILD_NUMBER=901 BUILDKITE_AGENT_NAME=agent-a \
    PERF_INFO_INVESTIGATION_PART=promotion PERF_INV_OUT_ROOT="$T/out" PERF_INV_COLLECTORS=G1 PERF_INV_PROMO_MEMORIES=1g \
    PERF_INV_PROMO_DIVISORS=12 PERF_INV_PROMO_STEP_S=5 PERF_INV_PROMO_SETTLE_S=1 PERF_INV_FILL_TIMEOUT_S=3 STUB_PROMO=1 "$@" \
    bash "$STEP" > "$T/step.log" 2>&1 || PROMO_RC=$?
}
if command -v python3 >/dev/null 2>&1; then
  PCELLS="$T/out/promotion/cells.jsonl"
  run_promo
  check "a fully measured promotion cell passes" "0" "$PROMO_RC"
  check "its budget is heap/12 from MaxHeapSize, confirmed by the gauges" "measured 38622208 66209792 38622208" \
    "$(jq -r '"\(.status) \(.budget_bytes) \(.in_flight_cap_bytes) \(.gauges.max_retained_bytes)"' "$PCELLS" 2>/dev/null)"
  check "its rung is derived from the GC log and the samples" "measured G1 1" \
    "$(jq -r '"\(.analysis.rungs[0].status) \(.analysis.collector) \(.analysis.rungs[0].young_collections)"' "$PCELLS" 2>/dev/null)"
  if grep -q 'MOCKSERVER_MAX_EVENT_LOG_SIZE_IN_BYTES=38622208' "$STUB_DIR/calls.log" && grep -q 'Xlog:gc\*:file=/diag/INFO-G1-1g-d12/gc-%p.log' "$STUB_DIR/calls.log"; then
    ok "the SUT gets the budget and the GC log"; else bad "the SUT was not started with the budget and GC log: $(grep 'run -d' "$STUB_DIR/calls.log" | head -2)"; fi
  if [ -s "$T/out/promotion/INFO-G1-1g-d12/gc.log.gz" ]; then ok "the GC log is kept as an artifact"; else bad "no gc.log.gz"; fi
  check "no container is left after a promotion pass" "0" "$(live_containers)"
  pverdict() { local rc=0 o; o="$(inv_promo_verdict "$1" "$2")" || rc=$?; printf '%s|%s' "$rc" "$(head -1 <<<"$o")"; }
  pmut() { jq -c "$1" "$PCELLS" > "$T/pm.jsonl"; echo "$T/pm.jsonl"; }
  check "the verdict passes the measured cell" "0|" "$(pverdict "$PCELLS" 1)"
  check "a missing promotion cell fails" "1|expected 2 cells, found 1" "$(pverdict "$PCELLS" 2)"
  check "an unconfirmed budget fails" "1|INFO-G1-1g-d12: budget 1 is not 38622208" "$(pverdict "$(pmut '.gauges.max_retained_bytes = 1')" 1)"
  check "a GC log from the other collector fails" "1|INFO-G1-1g-d12: the GC log is ZGC, not G1" "$(pverdict "$(pmut '.analysis.collector = "ZGC"')" 1)"
  check "an underived rung fails" "1|INFO-G1-1g-d12: rung 2000: fewer than two metric samples" \
    "$(pverdict "$(pmut '.analysis.rungs[0].status = "fewer than two metric samples"')" 1)"
  check "a rung with failed requests fails" "1|INFO-G1-1g-d12: rung 2000: error rate 0.02" \
    "$(pverdict "$(pmut '.analysis.rungs[0].error_rate = 0.02')" 1)"
  jq -c --slurpfile a "$T/unparsed.json" '.analysis = ($a[0] | .collector = "G1")' "$PCELLS" > "$T/pu.jsonl"
  check "a cell whose promotion row did not parse fails" "1|INFO-G1-1g-d12: rung 2000: promotion unparsed" "$(pverdict "$T/pu.jsonl" 1)"
  run_promo STUB_MAX_RETAINED=123
  check "a budget the server does not report fails the step" "1|budget_mismatch" "$PROMO_RC|$(jq -r .status "$PCELLS" 2>/dev/null)"
  run_promo STUB_RETAINED=1000
  check "a prefill short of 93% fails the step" "1|prefill_short" "$PROMO_RC|$(jq -r .status "$PCELLS" 2>/dev/null)"
  check "no container is left after a promotion failure" "0" "$(live_containers)"
fi

[ "$FAILS" -eq 0 ] || { echo "FAILED: $FAILS check(s)" >&2; exit 1; }
echo "perf-info-investigation-test: all checks passed"
