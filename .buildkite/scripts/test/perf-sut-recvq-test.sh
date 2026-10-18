#!/usr/bin/env bash
# Fixture tests for the SUT receive-queue sampler:
# lib/perf-sut-recvq.sh and its python engine, lib/perf-sut-recvq.py, against a fake /proc
# (hand-made and kernel-format tables) and stub nsenter/ss, and its wiring into
# scripts/rw-multi-k6-sweep.sh. No Docker needed.
# Run: .buildkite/scripts/test/perf-sut-recvq-test.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env PERF_RQ_LIB PERF_RQ_HARNESS
LIB="${PERF_RQ_LIB:-$REPO_ROOT/.buildkite/scripts/steps/lib/perf-sut-recvq.sh}"
HARNESS="${PERF_RQ_HARNESS:-$REPO_ROOT/mockserver-performance-test/scripts/rw-multi-k6-sweep.sh}"
PY="$(dirname "$LIB")/perf-sut-recvq.py"
# shellcheck source=../steps/lib/perf-sut-recvq.sh
. "$LIB"
T="$(mktemp -d "${TMPDIR:-/tmp}/perf-recvq-test.XXXXXX")"
ORPHAN=""
trap '{ [ -z "$ORPHAN" ] || kill -9 "$ORPHAN"; } 2>/dev/null || true; rm -rf "$T"' EXIT
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
st() { jq -r "$2" "$1/main-sut-recvq-status.json" 2>/dev/null || echo "<no status>"; } # work dir, jq filter
# poll <seconds> <command...>: true once the command succeeds, false after the bound (never a fixed sleep).
poll() { local end=$(( $(date +%s) + $1 )); shift; until "$@"; do [ "$(date +%s)" -lt "$end" ] || return 1; sleep 0.1; done; }
rows_at_least() { [ $(( $(wc -l < "$1") - 1 )) -ge "$2" ]; }

# A fake /proc for pid 4242: a listener with 3 queued connections, three established sockets with
# 0, 500 (0x1F4) and 16 (tcp6) unread bytes, and a TIME_WAIT socket that must not count.
P="$T/proc"; mkdir -p "$P/4242/net"
cat > "$P/4242/net/tcp" <<'TCP'
  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 00000000:0438 00000000:0000 0A 00000000:00000003 00:00000000 00000000     0        0 10 1 0000000000000000 100 0 0 10 0
   1: 0300120A:0438 0500120A:A8C2 01 00000000:00000000 00:00000000 00000000     0        0 11 1 0000000000000000 20 4 30 10 -1
   2: 0300120A:0438 0500120A:A8C3 01 00000010:000001F4 00:00000000 00000000     0        0 12 1 0000000000000000 20 4 30 10 -1
   3: 0300120A:0438 0500120A:A8C4 06 00000000:00FFFFFF 00:00000000 00000000     0        0 0 3 0000000000000000
TCP
cat > "$P/4242/net/tcp6" <<'TCP'
  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 0000000000000000FFFF00000300120A:0438 0000000000000000FFFF00000600120A:B000 01 00000000:00000010 00:00000000 00000000     0        0 13 1 0000000000000000 20 4 30 10 -1
TCP
mkdir -p "$T/bin" "$T/ssbin"
printf '#!/usr/bin/env bash\necho "nsenter: reassociate to namespace ns/net failed: Operation not permitted" >&2\nexit 1\n' > "$T/bin/nsenter"
printf '#!/usr/bin/env bash\nexit 0\n' > "$T/bin/ss"
printf '#!/usr/bin/env bash\nshift 3; "$@"\n' > "$T/ssbin/nsenter"
cat > "$T/ssbin/ss" <<'SS'
#!/usr/bin/env bash
echo "LISTEN 2      4096   0.0.0.0:1080      0.0.0.0:*"
echo "ESTAB  0      0      172.18.0.3:1080   172.18.0.5:43010"
echo "ESTAB  7000   0      172.18.0.3:1080   172.18.0.5:43011"
echo "TIME-WAIT 0   0      172.18.0.3:1080   172.18.0.5:43012"
SS
chmod +x "$T/bin/"* "$T/ssbin/"*

echo "--- 1. one sample, from /proc/<pid>/net/tcp{,6} and from ss"
check "proc: estab, nonzero, sum, max, accept queue" "3,2,516,500,3" "$(_recvq_proc "$P" 4242)"
check "proc: no such pid fails" "fail" "$(_recvq_proc "$P" 999 || echo fail)"
check "proc: a malformed rx_queue is skipped, not read as 0" "1,0,0,0,0" \
  "$(mkdir -p "$P/77/net"; printf '  sl\n   0: A:1 B:2 01 0:0000zz00\n   1: A:1 B:3 01 0:0\n' > "$P/77/net/tcp"; _recvq_proc "$P" 77)"
check "ss: the same columns from ss -tanH in the namespace" "2,1,7000,7000,2" "$(PATH="$T/ssbin:$PATH" _recvq_ss 4242)"
check "ss: nsenter refused (not root) fails" "fail" "$(PATH="$T/bin:$PATH" _recvq_ss 4242 || echo fail)"

# Tables in the kernel's own format (net/ipv6/tcp_ipv6.c): a dual-stack listener, line numbers past
# 9999 (the sl column widens), established sockets with 0, 0x88 and 0x110 unread bytes, a
# CLOSE_WAIT socket with unread bytes, and TIME_WAIT and SYN_RECV rows in their shorter format.
# /proc/<pid>/net/tcp holds only its header, as for a JVM whose sockets are all IPv6.
K="$T/kproc"; mkdir -p "$K/5151/net"
echo "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode" > "$K/5151/net/tcp"
cat > "$K/5151/net/tcp6" <<'TCP'
  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 00000000000000000000000000000000:0438 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 31337 1 0000000000000000 100 0 0 10 0
9998: 0000000000000000FFFF00000300120A:0438 0000000000000000FFFF00000600120A:9C40 01 00000000:00000088 00:00000000 00000000     0        0 40001 1 0000000000000000 20 4 30 10 -1
9999: 0000000000000000FFFF00000300120A:0438 0000000000000000FFFF00000600120A:9C41 01 00000000:00000000 00:00000000 00000000     0        0 40002 1 0000000000000000 20 4 30 10 -1
10000: 0000000000000000FFFF00000300120A:0438 0000000000000000FFFF00000700120A:9C42 01 0000008A:00000110 01:00000014 00000000     0        0 40003 1 0000000000000000 21 4 30 10 -1
10001: 0000000000000000FFFF00000300120A:0438 0000000000000000FFFF00000700120A:9C43 08 00000000:00000044 00:00000000 00000000     0        0 40004 1 0000000000000000 20 4 30 10 -1
10002: 0000000000000000FFFF00000300120A:0438 0000000000000000FFFF00000700120A:9C44 06 00000000:00000000 03:000005DC 00000000     0        0 0 3 0000000000000000
10003: 0000000000000000FFFF00000300120A:0438 0000000000000000FFFF00000800120A:9C45 03 00000000:00000000 01:00000064 00000000     0        0 0 2 0000000000000000
TCP
check "proc, kernel format: only established sockets count, wide line numbers parse" "3,2,408,272,0" "$(_recvq_proc "$K" 5151)"
mkdir -p "$K/6161/net"; cp "$K/5151/net/tcp" "$K/6161/net/tcp"; head -n 1 "$K/5151/net/tcp6" > "$K/6161/net/tcp6"
check "proc: header-only tables are no sample, not a row of zeros" "fail" "$(_recvq_proc "$K" 6161 || echo fail)"

if ! command -v python3 >/dev/null 2>&1; then
  if [ "${BUILDKITE:-}" = "true" ]; then bad "python3 absent: the python engine is untested"; else echo "  SKIP python3 absent"; fi
  PYOK=false
else
  PYOK=true
  echo "--- 1b. the python engine reads the same tables to the same row"
  check "python proc: the hand-made tables" "3,2,516,500,3" "$(python3 "$PY" sample proc "$P" 4242)"
  check "python proc: the kernel-format tables" "3,2,408,272,0" "$(python3 "$PY" sample proc "$K" 5151)"
  check "python proc: a malformed rx_queue is skipped, not read as 0" "1,0,0,0,0" "$(python3 "$PY" sample proc "$P" 77)"
  check "python proc: header-only tables are no sample" "fail" "$(python3 "$PY" sample proc "$K" 6161 || echo fail)"
  check "python proc: no such pid fails" "fail" "$(python3 "$PY" sample proc "$P" 999 || echo fail)"
  check "python ss: the same columns" "2,1,7000,7000,2" "$(PATH="$T/ssbin:$PATH" python3 "$PY" sample ss 4242)"
  check "python ss: nsenter refused (not root) fails" "fail" "$(PATH="$T/bin:$PATH" python3 "$PY" sample ss 4242 || echo fail)"
  check "python: a bad command line is usage, exit 2" "2" "$(python3 "$PY" run "$T" proc 1 "$P" 0 1 1 1 >/dev/null 2>&1; echo $?)"
  # A full-size table in the kernel's format: the 12,804 sockets of a 160k rung, one in 97 with a request unread.
  mkdir -p "$K/7171/net"; cp "$K/5151/net/tcp" "$K/7171/net/tcp"
  awk 'BEGIN { print "  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode"
    printf "%4d: 00000000000000000000000000000000:0438 00000000000000000000000000000000:0000 0A 00000000:00000002 00:00000000 00000000     0        0 31337 1 0000000000000000 100 0 0 10 0\n", 0
    for (i = 1; i < 12804; i++) printf "%4d: 0000000000000000FFFF00000300120A:0438 0000000000000000FFFF00000600120A:%04X 01 00000000:%08X 00:00000000 00000000     0        0 %d 1 0000000000000000 20 4 30 10 -1\n", i, 30000 + i % 30000, (i % 97 == 0) ? 136 + (i % 3) * 136 : 0, 100000 + i }' > "$K/7171/net/tcp6"
  check "python proc: a 12,804-socket table matches the shell parser" "$(_recvq_proc "$K" 7171)" "$(python3 "$PY" sample proc "$K" 7171)"
  check "  ... which counts every socket" "12803,131,35768,408,2" "$(python3 "$PY" sample proc "$K" 7171)"
fi

# sampler_checks <engine> <PERF_SUT_RECVQ_PYTHON>: start, rows, bounds, stop and status for one engine.
sampler_checks() {
  local eng="$1" w="$T/w-$1" pg="$T/pg-$1" iv
  export PERF_SUT_RECVQ_PYTHON="$2"
  echo "--- 2. the sampler ($eng engine): source choice, rows, stop, status"
  mkdir -p "$w" "$pg"
  PATH="$T/bin:$PATH" PERF_PROC_ROOT="$P" sut_recvq_sampler_start "$w" 4242 100 0.2
  check "nsenter refused: falls back to /proc, running on the $eng engine" "true proc null $eng" \
    "$(st "$w" '"\(.running) \(.source) \(.reason) \(.engine)"')"
  iv="$([ "$eng" = python ] && echo 0.2 || echo 1)"
  check "interval: 0.2 s requested, $iv s used (the shell engine never goes below 1 s)" "0.2 $iv" "$(st "$w" '"\(.requested_interval_s) \(.interval_s)"')"
  check "rows are written at the interval (3 within 30 s)" "yes" "$(poll 30 rows_at_least "$w/main-sut-recvq.csv" 3 && echo yes || echo no)"
  sut_recvq_sampler_stop
  check "every row is one proc sample" "proc,3,2,516,500,3" "$(tail -n 1 "$w/main-sut-recvq.csv" | cut -d, -f2-7)"
  check "read_ms is a duration where the clock has sub-seconds, else empty" "yes" \
    "$(tail -n 1 "$w/main-sut-recvq.csv" | awk -F, -v rt="${EPOCHREALTIME:-}" -v e="$eng" '{ print ((e == "shell" && rt == "" && $8 == "") || ((e == "python" || rt != "") && $8 ~ /^[0-9]+\.[0-9]$/)) ? "yes" : "no: " $8 }')"
  check "status: stopped, its samples counted" "stopped true" "$(st "$w" '"\(.stop_reason) \(.samples >= 3)"')"
  if [ "$eng" = python ]; then
    check "engine_stats: rows, read times and sampler CPU recorded" "python true true true" \
      "$(st "$w" '"\(.engine_stats.engine) \(.engine_stats.rows >= 3) \(.engine_stats.read_ms.max >= .engine_stats.read_ms.p50) \(.engine_stats.cpu_s >= 0)"')"
  else
    check "engine_stats: none from the shell engine" "null" "$(st "$w" .engine_stats)"
  fi
  check "stop is idempotent and leaves no scratch files" "0 stopped" \
    "$(sut_recvq_sampler_stop; find "$w" -name '.recvq-*' | wc -l | tr -d ' ') $(st "$w" .stop_reason)"
  PATH="$T/ssbin:$PATH" PERF_PROC_ROOT="$P" sut_recvq_sampler_start "$w" 4242 2 0.1
  poll 30 test -s "$w/.recvq-end" || true; sut_recvq_sampler_stop
  check "nsenter + ss usable: ss is the source, bounded by max_samples" "ss $eng max_samples 2" "$(st "$w" '"\(.source) \(.engine) \(.stop_reason) \(.samples)"')"
  check "  ... its rows come from ss" "ss,2,1,7000,7000,2" "$(tail -n 1 "$w/main-sut-recvq.csv" | cut -d, -f2-7)"
  PATH="$T/bin:$PATH" PERF_PROC_ROOT="$P" sut_recvq_sampler_start "$w" 4242 100 0.1 150
  poll 30 test -s "$w/.recvq-end" || true; sut_recvq_sampler_stop
  check "bounded by max_bytes: stops once the CSV reaches it" "max_bytes yes" \
    "$(st "$w" .stop_reason) $([ "$(wc -c < "$w/main-sut-recvq.csv")" -lt 300 ] && echo yes || echo no)"
  sut_recvq_sampler_start "$w" "" 10
  check "no SUT pid (external target): not started, says why" "false null null true" "$(st "$w" '"\(.running) \(.source) \(.engine) \(.reason | test("no SUT process id"))"')"
  check "  ... and the CSV is just the header" "1" "$(wc -l < "$w/main-sut-recvq.csv" | tr -d ' ')"
  sut_recvq_sampler_stop
  check "  ... stop on a sampler that never ran records it" "not_started 0" "$(st "$w" '"\(.stop_reason) \(.samples)"')"
  PATH="$T/bin:$PATH" PERF_PROC_ROOT="$T/nowhere" sut_recvq_sampler_start "$w" 4242 10
  check "nothing readable: not started, says why" "false true" "$(st "$w" '"\(.running) \(.reason | test("neither nsenter"))"')"
  sut_recvq_sampler_stop
  mkdir -p "$T/ro"; chmod 0555 "$T/ro"
  check "an unwritable work dir never fails the caller" "0" "$(PERF_PROC_ROOT="$T/nowhere" sut_recvq_sampler_start "$T/ro/missing" 4242 10 >/dev/null 2>&1; echo $?)"
  # The harness dies without stopping it (SIGKILL): the sampler notices its parent is gone and exits.
  ( PATH="$T/bin:$PATH" PERF_PROC_ROOT="$P" bash -c '. "$1"; sut_recvq_sampler_start "$2" 4242 100000 0.1
    echo "$SUT_RECVQ_PID" > "$2/sampler.pid"; kill -9 $$' _ "$LIB" "$pg" || true ) 2>/dev/null
  ORPHAN="$(cat "$pg/sampler.pid" 2>/dev/null || true)"
  check "parent killed: the sampler records parent_gone within 30 s" "parent_gone" \
    "$(poll 30 test -s "$pg/.recvq-end" && cat "$pg/.recvq-end" || echo "still running")"
  check "  ... and exits" "gone" "$(poll 30 bash -c "! kill -0 '$ORPHAN' 2>/dev/null" && echo gone || echo alive)"
  ORPHAN=""
  unset PERF_SUT_RECVQ_PYTHON
}

if [ "$PYOK" = true ]; then
  sampler_checks python python3
  # Sub-second sampling at a fixed rate: 20 rows at 0.05 s land well inside the shell engine's 1 s floor.
  mkdir -p "$T/fast"
  PERF_SUT_RECVQ_PYTHON=python3 PATH="$T/bin:$PATH" PERF_PROC_ROOT="$K" sut_recvq_sampler_start "$T/fast" 7171 100000 0.05
  poll 60 rows_at_least "$T/fast/main-sut-recvq.csv" 20 || true
  sut_recvq_sampler_stop
  check "python engine at 0.05 s on 12,804 sockets: 20 rows, a mean gap under 0.5 s" "yes" \
    "$(awk -F, 'NR == 2 { a = $1 } NR > 1 { b = $1; n++ } END { print (n >= 20 && (b - a) / (n - 1) < 0.5) ? "yes" : "no: " n " rows over " b - a " s" }' "$T/fast/main-sut-recvq.csv")"
  check "  ... every row is the full table" "12803,131,35768,408,2" "$(tail -n 1 "$T/fast/main-sut-recvq.csv" | cut -d, -f3-7)"
  echo "  info: python engine read_ms on 12,804 sockets (this host): $(jq -c .engine_stats.read_ms "$T/fast/main-sut-recvq-status.json")"
  # A slow source: each ss read takes 0.25 s against a 0.2 s interval, so every read overruns one tick
  # and the next sample waits for the tick after it (0.4 s apart), never starting at once (0.25 s apart).
  mkdir -p "$T/slowbin" "$T/slow"; cp "$T/ssbin/nsenter" "$T/slowbin/nsenter"
  printf '#!/usr/bin/env bash\nsleep 0.25\nexec "%s" "$@"\n' "$T/ssbin/ss" > "$T/slowbin/ss"; chmod +x "$T/slowbin/"*
  PERF_SUT_RECVQ_PYTHON=python3 PATH="$T/slowbin:$PATH" PERF_PROC_ROOT="$P" sut_recvq_sampler_start "$T/slow" 4242 100000 0.2
  poll 60 rows_at_least "$T/slow/main-sut-recvq.csv" 4 || true
  sut_recvq_sampler_stop
  check "a read longer than the interval skips and counts the ticks it overran" "ss python true true" \
    "$(st "$T/slow" '"\(.source) \(.engine) \(.engine_stats.overruns >= 3) \(.engine_stats.overruns >= .engine_stats.rows - 1)"')"
  check "  ... rather than sampling back to back: mean gap at least 0.35 s" "yes" \
    "$(awk -F, 'NR == 2 { a = $1 } NR > 1 { b = $1; n++ } END { print (n >= 4 && (b - a) / (n - 1) >= 0.35) ? "yes" : "no: " n " rows over " b - a " s" }' "$T/slow/main-sut-recvq.csv")"
fi
sampler_checks shell "$T/no-python3"

echo "--- 3. per-rung summary over each steady window"
# start 1000, step 10, gap 5, settle 3: rung 0 steady [1003,1010), rung 1 [1018,1025).
cat > "$T/rq.csv" <<'CSV'
ts,source,estab,recvq_nonzero,recvq_sum_bytes,recvq_max_bytes,listen_recvq,read_ms
1001.0,proc,10,5,99999,99999,9,1.0
1003.0,proc,10,0,0,0,0,2.0
1005.5,proc,10,2,300,200,1,4.0
1009.9,proc,10,48,6528,136,0,6.0
1012.0,proc,10,9,88888,88888,8,9.9
1018.0,proc,12,4,1000,700,2,
1024.0,proc,12,0,0,0,0,
1031.0,proc,12,9,77777,77777,7,
CSV
R="$(sut_recvq_rungs "$T/rq.csv" 1000 10 5 3 "8000,16000,24000")"
# Rung 0's last sample holds 48 sockets unread at 8,000 req/s: 6 ms deep, so 1 of its 3 samples is 5 ms deep.
check "settle and gap samples are excluded; a rung with no sample is absent; depth and read times per rung" \
  '[{"offered_rps":8000,"samples":3,"samples_per_s":0.43,"recvq_nonzero_mean":16.67,"est_wait_to_read_ms":2.0833,"depth_ms_max":6.0000,"deep_5ms_sample_frac":0.333,"recvq_sum_bytes_mean":2276.0,"recvq_sum_bytes_max":6528,"recvq_max_bytes_max":200,"nonzero_sample_frac":0.667,"listen_recvq_max":1,"read_ms_mean":4.0,"read_ms_max":6.0},{"offered_rps":16000,"samples":2,"samples_per_s":0.29,"recvq_nonzero_mean":2.00,"est_wait_to_read_ms":0.1250,"depth_ms_max":0.2500,"deep_5ms_sample_frac":0.000,"recvq_sum_bytes_mean":500.0,"recvq_sum_bytes_max":1000,"recvq_max_bytes_max":700,"nonzero_sample_frac":0.500,"listen_recvq_max":2,"read_ms_mean":null,"read_ms_max":null}]' "$R"
check "the summary is valid JSON" "2" "$(jq length <<<"$R")"
check "no CSV: an empty array" "[]" "$(sut_recvq_rungs "$T/none.csv" 1000 10 5 3 "8000")"

echo "--- 4. wiring in rw-multi-k6-sweep.sh"
MAIN="$(awk '/^if \[ "\$SUT_RECVQ" = true \]; then$/ {on = 1} on {print} /^run_phase main / {seen = 1} seen && /^fi$/ {exit}' "$HARNESS")"
has() { grep -qE -- "$1" <<<"$2" && echo yes || echo no; }
check "the sampler starts before the main ladder" "yes" "$(has '^  sut_recvq_sampler_start "\$WORK"' "$(sed -n '1,/^run_phase main /p' <<<"$MAIN")")"
check "it is stopped after the main ladder and summarised per rung" "yes yes" \
  "$(has '^  sut_recvq_sampler_stop$' "$(sed -n '/^run_phase main /,$p' <<<"$MAIN")") $(has 'sut_recvq_rungs ' "$(sed -n '/^run_phase main /,$p' <<<"$MAIN")")"
check "its pid is recorded, so an abort's cleanup kills it" "yes" "$(has 'record_pid "\$SUT_RECVQ_PID"' "$MAIN")"
check "it samples at PERF_RW_SUT_RECVQ_INTERVAL_S (default 0.1 s), its sample bound scaled to it" "yes yes yes" \
  "$(has '^    "\$SUT_RECVQ_INTERVAL_S" 16777216 "\$PROM_CPUS"$' "$MAIN") $(has 's / i \+ 1' "$MAIN") $(has '^SUT_RECVQ_INTERVAL_S="\$\{PERF_RW_SUT_RECVQ_INTERVAL_S:-0\.1\}"$' "$(cat "$HARNESS")")"
mkdir -p "$T/dbin"; printf '#!/usr/bin/env bash\nexit 1\n' > "$T/dbin/docker"; chmod +x "$T/dbin/docker"
RC=0; env PATH="$T/dbin:$PATH" PERF_RW_TEST_RESOLVE_ONLY=true PERF_RW_K6_GOMEMLIMIT=off PERF_RW_K6_CPUSETS="1;2" PERF_RW_PROCS=2 \
  PERF_RW_SUT_RECVQ=yes PERF_RW_REPO_ROOT="$REPO_ROOT" bash "$HARNESS" >/dev/null 2>"$T/h.err" || RC=$?
check "PERF_RW_SUT_RECVQ=yes is rejected at startup with exit 2, naming the setting" "2 yes" \
  "$RC $(grep -q 'PERF_RW_SUT_RECVQ must be true or false' "$T/h.err" && echo yes || echo no)"
for bad_iv in 0.01 11 1e-1 fast; do
  RC=0; env PATH="$T/dbin:$PATH" PERF_RW_TEST_RESOLVE_ONLY=true PERF_RW_K6_GOMEMLIMIT=off PERF_RW_K6_CPUSETS="1;2" PERF_RW_PROCS=2 \
    PERF_RW_SUT_RECVQ_INTERVAL_S="$bad_iv" PERF_RW_REPO_ROOT="$REPO_ROOT" bash "$HARNESS" >/dev/null 2>"$T/h.err" || RC=$?
  check "PERF_RW_SUT_RECVQ_INTERVAL_S=$bad_iv is rejected at startup with exit 2" "2 yes" \
    "$RC $(grep -q 'PERF_RW_SUT_RECVQ_INTERVAL_S must be a number of seconds from 0.02 to 10' "$T/h.err" && echo yes || echo no)"
done
RC=0; env PATH="$T/dbin:$PATH" PERF_RW_TEST_RESOLVE_ONLY=true PERF_RW_K6_GOMEMLIMIT=off PERF_RW_K6_CPUSETS="1;2" PERF_RW_PROCS=2 \
  PERF_RW_SUT_RECVQ_INTERVAL_S=0.05 PERF_RW_REPO_ROOT="$REPO_ROOT" bash "$HARNESS" >/dev/null 2>"$T/h.err" || RC=$?
check "PERF_RW_SUT_RECVQ_INTERVAL_S=0.05 passes the startup check" "no" \
  "$(grep -q 'PERF_RW_SUT_RECVQ_INTERVAL_S must be' "$T/h.err" && echo yes || echo no)"

if [ "$FAILS" -gt 0 ]; then echo ":x: $FAILS receive-queue check(s) failed" >&2; exit 1; fi
echo "--- all receive-queue fixture checks passed"
