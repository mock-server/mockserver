#!/usr/bin/env bash
# Fixture tests for the INFO-arm live-heap reset probe (lib/perf-info-heap-probe.sh) against stub
# docker and curl on PATH, and its wiring: the INFO sweep keeps its log, the probe runs after the
# arm's measurements, and its record lands in info_log_level_arm. No Docker needed.
# Run: .buildkite/scripts/test/perf-info-heap-probe-test.sh
#   (PERF_IHP_LIB / PERF_RUN_SCRIPT / PERF_K6_DIR=<path> to test another copy)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env PERF_IHP_LIB PERF_RUN_SCRIPT PERF_K6_DIR
LIB="${PERF_IHP_LIB:-$REPO_ROOT/.buildkite/scripts/steps/lib/perf-info-heap-probe.sh}"
F="${PERF_RUN_SCRIPT:-$REPO_ROOT/.buildkite/scripts/steps/perf-test-run.sh}"
K6="${PERF_K6_DIR:-$REPO_ROOT/mockserver-performance-test/k6}"
# shellcheck source=../steps/lib/perf-info-heap-probe.sh
. "$LIB"
T="$(mktemp -d "${TMPDIR:-/tmp}/perf-ihp-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}

HISTO_TAIL='Total        1234567      %s'
histo() { # total_bytes -> a GC.class_histogram print
  printf '%s\n' '1:' ' num     #instances         #bytes  class name (module)' \
    '-------------------------------------------------------' \
    '   1:        402011      301234567  [B (java.base@25)' \
    '   2:        391002        9384048  java.lang.String (java.base@25)'
  # shellcheck disable=SC2059
  printf "$HISTO_TAIL\n" "$1"
}

echo "--- 1. histogram_total_bytes and the record"
check "the Total line's bytes" "550000000" "$(histo 550000000 | histogram_total_bytes)"
check "no Total line reads empty" "" "$(printf '1:\ncom.sun.tools.attach.AttachNotSupportedException\n' | histogram_total_bytes)"
R="$(info_heap_probe_json true 40000 60000000 2 2000 550000000 400000000 '[]')"
check "multiple = live-heap delta / released counted bytes" "2.5" "$(jq -r .live_heap_per_counted_byte <<<"$R")"
check "bytes per released entry" "3750" "$(jq -r .live_heap_bytes_per_entry <<<"$R")"
check "released counted bytes" "59998000" "$(jq -r .released_counted_bytes <<<"$R")"
check "live-heap delta" "150000000" "$(jq -r .live_heap_delta_bytes <<<"$R")"
check "status measured, notify-only, no collector given" "measured true true null" "$(jq -r '"\(.status) \(.notify_only) \(.drained) \(.gc)"' <<<"$R")"
R="$(info_heap_probe_json false 40000 60000000 "" "" 550000000 "" '["x"]')"
check "no after-reset reading: derived fields null, status failed" "null null null failed" \
  "$(jq -r '"\(.live_heap_delta_bytes) \(.live_heap_per_counted_byte) \(.live_heap_bytes_per_entry) \(.status)"' <<<"$R")"
R="$(info_heap_probe_json true 5 1000 5 1000 550000000 549000000 '[]')"
check "nothing released: no division" "null null failed" "$(jq -r '"\(.live_heap_per_counted_byte) \(.live_heap_bytes_per_entry) \(.status)"' <<<"$R")"
R="$(info_heap_probe_json true 40000 60000000 2 2000 550000000 400000000 '["drain"]')"
check "a measurement with problems says so" "measured_with_problems" "$(jq -r .status <<<"$R")"

# Stubs. Each records one line per call in $S/events; state files in $S steer them.
S="$T/state"; B="$T/bin"; mkdir -p "$S" "$B" "$T/diag"
cat > "$B/docker" <<'STUB'
#!/usr/bin/env bash
S="$IHP_STATE"
[ "$1" = run ] || { echo "docker $1" >> "$S/events"; exit 0; }
a="$*"
case "$a" in
  *" sh -c "*)
    echo "uid $a" >> "$S/events"
    [ ! -f "$S/uid_fail" ] || exit 125
    echo "Unable to find image 'eclipse-temurin:21-jdk' locally" >&2
    echo "1001 1002" ;;
  *"jcmd 1 GC.class_histogram"*)
    echo "histogram $a" >> "$S/events"
    [ ! -f "$S/jcmd_hang" ] || exec sleep 30
    [ ! -f "$S/jcmd_fail" ] || { echo "1:"; echo "com.sun.tools.attach.AttachNotSupportedException: Unable to open socket file"; exit 1; }
    if [ -f "$S/reset" ]; then t=400000000; else t=550000000; fi
    echo "1:"
    if [ -f "$S/long_histo" ]; then for i in $(seq 1 60); do echo "  $i:   10   1000  C$i"; done; else echo "   1:   402011   301234567  [B (java.base@25)"; fi
    printf 'Total        1234567      %s\n' "$t" ;;
  *"jcmd 1 GC.run"*)
    echo "gc-run" >> "$S/events"; echo "1:"; echo "Command executed successfully" ;;
  *) echo "unexpected $a" >> "$S/events"; exit 2 ;;
esac
STUB
cat > "$B/curl" <<'STUB'
#!/usr/bin/env bash
S="$IHP_STATE"
case "$*" in
  *"-X PUT"*"/mockserver/reset"*)
    echo "reset" >> "$S/events"
    [ ! -f "$S/reset_fail" ] || exit 22
    [ -f "$S/reset_noop" ] || touch "$S/reset" ;;
  *"/mockserver/metrics"*)
    [ ! -f "$S/metrics_fail" ] || exit 7
    busy=0; [ ! -f "$S/busy" ] || busy="$(cat "$S/busy")"
    ring=0
    if [ "$busy" = always ]; then ring=500; elif [ "$busy" -gt 0 ]; then ring=500; echo $((busy - 1)) > "$S/busy"; fi
    echo "scrape ring=$ring" >> "$S/events"
    if [ -f "$S/reset" ]; then b=2000; e=2; else b=6.0E7; e=40000; fi
    printf '# TYPE x gauge\nmock_server_event_log_retained_bytes %s\nmock_server_event_log_retained_entries %s\n' "$b" "$e"
    printf 'mock_server_event_log_ring_occupancy %s\nmock_server_event_log_in_flight_bytes 0\nmock_server_event_log_max_retained_bytes 9.0E7\n' "$ring"
    printf 'jvm_runtime_info{java_runtime_version="25.0.1",gc="ZGC Minor Cycles,ZGC Major Cycles",vm_name="OpenJDK"} 1.0\n' ;;
  *) exit 2 ;;
esac
STUB
chmod +x "$B/docker" "$B/curl"
export IHP_STATE="$S" PERF_HISTO_JDK_IMAGE="eclipse-temurin:21-jdk" PERF_INFO_HEAP_PROBE_POLL_S=0.1 PERF_INFO_HEAP_PROBE_DRAIN_S=5
probe() { # [deadline_s] -> the record in $T/out.json, stderr in $T/err, wall seconds in $T/secs
  local t0 rc=0; t0="$(date +%s)"
  PATH="$B:$PATH" info_heap_probe_run info-sut 127.0.0.1:32768 "$T/diag" "${1:-60}" > "$T/out.json" 2> "$T/err" || rc=$?
  echo $(( $(date +%s) - t0 )) > "$T/secs"
  check "exit 0" 0 "$rc"
  jq -e 'type == "object"' "$T/out.json" >/dev/null 2>&1 || bad "the probe printed no JSON object: $(head -c 300 "$T/out.json")"
}
reset_state() { rm -rf "$S" "$T/diag"; mkdir -p "$S" "$T/diag"; : > "$S/events"; }
j() { jq -r "$1" "$T/out.json" 2>/dev/null || echo "<no record>"; }
line_of() { grep -n -m1 -- "$1" "$S/events" | cut -d: -f1; }

echo "--- 2. the happy path: drain, histogram, reset, GC.run, histogram"
reset_state; echo 3 > "$S/busy"
probe
check "status" "measured" "$(j .status)"
check "drained only once the ring is empty" "true" "$(j .drained)"
check "entries and counted bytes read at the first histogram" "40000 60000000" "$(j '"\(.retained_entries) \(.retained_counted_bytes)"')"
check "residual after the reset" "2 2000" "$(j '"\(.residual_entries_after_reset) \(.residual_counted_bytes_after_reset)"')"
check "live heap before/after/delta" "550000000 400000000 150000000" "$(j '"\(.live_heap_before_reset_bytes) \(.live_heap_after_reset_bytes) \(.live_heap_delta_bytes)"')"
check "multiple and per-entry bytes" "2.5 3750" "$(j '"\(.live_heap_per_counted_byte) \(.live_heap_bytes_per_entry)"')"
check "no problems, no warnings" "0 0" "$(j '.problems | length') $(grep -c WARNING "$T/err" || true)"
check "the first histogram follows two idle scrapes" "true" \
  "$([ "$(line_of 'histogram')" -gt "$(grep -n 'scrape ring=0' "$S/events" | sed -n 2p | cut -d: -f1)" ] && echo true || echo false)"
check "order: histogram, reset, GC.run, histogram" "true" \
  "$(a="$(line_of histogram)"; b="$(line_of reset)"; c="$(line_of gc-run)"; d="$(grep -n histogram "$S/events" | sed -n 2p | cut -d: -f1)"
     [ -n "$d" ] && [ "$a" -lt "$b" ] && [ "$b" -lt "$c" ] && [ "$c" -lt "$d" ] && echo true || echo false)"
check "both histograms attach in the SUT's PID namespace as the JVM's uid" "2" \
  "$(grep -c -- 'histogram.*--pid=container:info-sut --user 1001:1002 eclipse-temurin:21-jdk jcmd 1 GC.class_histogram' "$S/events")"
check "the uid lookup runs as root in the same namespace" "1" "$(grep -c -- 'uid .*--pid=container:info-sut --user 0:0' "$S/events")"
check "the INFO SUT's own collector" "ZGC Minor Cycles,ZGC Major Cycles" "$(j .gc)"
check "both histograms land in the diagnostics dir with their Total" "2" \
  "$(cat "$T/diag/reset-probe-histogram-before.txt" "$T/diag/reset-probe-histogram-after.txt" 2>/dev/null | grep -c '^Total')"

reset_state; touch "$S/long_histo"
probe
check "a long histogram is cut to its top rows and keeps its Total" "44 true" \
  "$(wc -l < "$T/diag/reset-probe-histogram-before.txt" | tr -d ' ') $(tail -1 "$T/diag/reset-probe-histogram-before.txt" | grep -q '^Total  *1234567  *550000000$' && echo true || echo false)"
check "a long histogram's total still counts" "2.5" "$(j .live_heap_per_counted_byte)"

echo "--- 3. jcmd cannot attach: counted fields kept, heap fields null, no reset"
reset_state; touch "$S/jcmd_fail"
probe
check "status failed, heap null" "failed null null" "$(j '"\(.status) \(.live_heap_before_reset_bytes) \(.live_heap_per_counted_byte)"')"
check "counted bytes still recorded" "60000000" "$(j .retained_counted_bytes)"
check "the log is not reset without a first histogram" "0" "$(grep -c '^reset' "$S/events" || true)"
check "the problem names the failed jcmd" "true" "$(j '[.problems[] | test("histogram-before failed")] | any')"
check "a WARNING on stderr" "true" "$(grep -q 'WARNING: INFO live-heap reset probe: histogram-before failed' "$T/err" && echo true || echo false)"

echo "--- 4. the reset fails"
reset_state; touch "$S/reset_fail"
probe
check "before kept, after null, failed" "550000000 null null failed" \
  "$(j '"\(.live_heap_before_reset_bytes) \(.live_heap_after_reset_bytes) \(.residual_counted_bytes_after_reset) \(.status)"')"
check "problem recorded" "true" "$(j '[.problems[] | test("PUT /mockserver/reset failed")] | any')"
check "no GC.run or second histogram" "0 1" "$(grep -c gc-run "$S/events" || true) $(grep -c histogram "$S/events")"

echo "--- 5. the reset is accepted but the log does not shrink: bounded by the deadline"
reset_state; touch "$S/reset_noop"
probe 4
check "after null, failed" "null failed" "$(j '"\(.live_heap_after_reset_bytes) \(.status)"')"
check "problem recorded" "true" "$(j '[.problems[] | test("did not fall")] | any')"
check "returned within the deadline" "true" "$([ "$(cat "$T/secs")" -le 8 ] && echo true || echo false)"

echo "--- 6. the ring never drains: measured anyway, flagged"
reset_state; echo always > "$S/busy"
PERF_INFO_HEAP_PROBE_DRAIN_S=1 probe
check "drained false, still measured" "false measured_with_problems 2.5" "$(j '"\(.drained) \(.status) \(.live_heap_per_counted_byte)"')"
check "the problem quotes the last scrape" "true" "$(j '[.problems[] | test("not drained.*40000 500 0")] | any')"

echo "--- 7. metrics unreachable"
reset_state; touch "$S/metrics_fail"
PERF_INFO_HEAP_PROBE_DRAIN_S=1 probe
check "nothing counted, nothing reset" "null null false failed 0" \
  "$(j '"\(.retained_counted_bytes) \(.live_heap_per_counted_byte) \(.drained) \(.status)"') $(grep -c '^reset' "$S/events" || true)"
check "problem recorded" "true" "$(j '[.problems[] | test("retained_bytes unreadable")] | any')"

echo "--- 8. the uid lookup fails"
reset_state; touch "$S/uid_fail"
probe
check "no histograms, failed" "null failed 0" "$(j '"\(.live_heap_before_reset_bytes) \(.status)"') $(grep -c histogram "$S/events" || true)"
check "problem recorded" "true" "$(j '[.problems[] | test("uid/gid unresolved")] | any')"

echo "--- 9. a hung jcmd is cut at the deadline"
reset_state; touch "$S/jcmd_hang"
probe 3
check "returned within the deadline" "true" "$([ "$(cat "$T/secs")" -le 10 ] && echo true || echo false)"
check "failed with a timed-out jcmd" "failed true" "$(j .status) $(j '[.problems[] | test("histogram-before failed or timed out")] | any')"
check "the stuck sidecar is removed" "1" "$(grep -c '^docker rm' "$S/events" || true)"

echo "--- 10. wiring into perf-test-run.sh and sweep.js"
extract() { awk -v n="$1" '$0 ~ "^"n"\\(\\) *\\{" {p=1; print; next} p {print} p && /^}/ {exit}' "$F"; }
eval "$(extract run_sweep)"
sweep_cpu_sampler() { :; }
cpuset_arg() { :; }
sweep_args() { # [keep_log] -> the docker run arguments of run_sweep
  (
    docker() { printf '%s\n' "$@" > "$T/sweep.args"; }
    ACTIVE_LOAD_FILE="$T/active" SUT_DEATH_SENTINEL="$T/none" PERF_NETWORK_MODE=bridge NET_CLIENT=() K6_CPUS="" \
      REPO_ROOT=/r OUT_DIR=/o SWEEP_RATES=1 SWEEP_STEP=1s SWEEP_GAP=1s SETTLE_S=1 K6_IMAGE=k6 \
      run_sweep k6 mockserver-info /o/s.json /o/c.csv http "$@"
    cat "$T/sweep.args"
  )
}
check "keep_log=true hands the ladder K6_SWEEP_RESET_ON_TEARDOWN=false" "1" "$(sweep_args true | grep -c '^K6_SWEEP_RESET_ON_TEARDOWN=false$')"
check "keep_log=false keeps the sweep's own reset" "0" "$(sweep_args false | grep -c 'K6_SWEEP_RESET_ON_TEARDOWN' || true)"
check "no keep_log keeps the sweep's own reset" "0" "$(sweep_args | grep -c 'K6_SWEEP_RESET_ON_TEARDOWN' || true)"
INFO_SWEEP_LINE="$(grep -n 'run_sweep "\$INFO_SWEEP_K6"' "$F" | cut -d: -f1)"
PROBE_LINE="$(grep -n 'info_heap_probe_run "\$INFO_SERVER"' "$F" | cut -d: -f1)"
SAMPLER_STOP_LINE="$(awk -v s="$INFO_SWEEP_LINE" 'NR > s && /^ *stop_info_els_sampler$/ {print NR; exit}' "$F")"
RM_LINE="$(grep -n 'docker rm -f "\$INFO_SERVER"' "$F" | cut -d: -f1)"
check "the INFO sweep keeps its log when the probe is on" "1" "$(grep -c 'run_sweep "\$INFO_SWEEP_K6" .* http "\$PERF_INFO_HEAP_PROBE"' "$F")"
check "the main ERROR sweep never keeps its log" "0" "$(grep -E 'run_sweep "\$SWEEP_K6"' "$F" | grep -c 'PERF_INFO_HEAP_PROBE' || true)"
check "the probe runs after the INFO sweep and its sampler, before the SUT is removed" "true" \
  "$([ -n "$PROBE_LINE" ] && [ "$PROBE_LINE" -gt "$SAMPLER_STOP_LINE" ] && [ "$SAMPLER_STOP_LINE" -gt "$INFO_SWEEP_LINE" ] && [ "$PROBE_LINE" -lt "$RM_LINE" ] && echo true || echo false)"
check "the probe is gated on PERF_INFO_HEAP_PROBE" "1" "$(grep -c 'if \[ "\$PERF_INFO_HEAP_PROBE" = true \] && \[ -n "\$INFO_METRICS_HOSTPORT" \]' "$F")"
check "the record lands in info_log_level_arm" "2" "$(grep -cE -- '--argjson live_heap_reset_probe "\$INFO_HEAP_PROBE_JSON"|live_heap_reset_probe: \$live_heap_reset_probe,' "$F")"
check "PERF_INFO_HEAP_PROBE defaults on" "1" "$(grep -c '^PERF_INFO_HEAP_PROBE="\${PERF_INFO_HEAP_PROBE:-true}"$' "$F")"
check "sweep.js resets in teardown only when resetOnTeardown" "1" "$(grep -c 'if (SWEEP.manageSut && SWEEP.resetOnTeardown) {' "$K6/sweep.js")"
check "resetOnTeardown defaults to true" "1" "$(grep -c "resetOnTeardown: bool('K6_SWEEP_RESET_ON_TEARDOWN', true)," "$K6/lib/config.js")"

if [ "$FAILS" -ne 0 ]; then echo "FAILED: $FAILS check(s)" >&2; exit 1; fi
echo "perf-info-heap-probe-test: all checks passed"
