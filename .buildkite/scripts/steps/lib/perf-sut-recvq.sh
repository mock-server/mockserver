#!/usr/bin/env bash
# SUT socket receive-queue sampler for scripts/rw-multi-k6-sweep.sh:
# at a fixed interval, the Recv-Q of every TCP socket in the SUT container's network namespace, read
# from the host. Bytes waiting there have reached the socket but not MockServer's event loop. The
# python engine (perf-sut-recvq.py) samples sub-second; this file's shell loop is its fallback.
# Report-only: nothing here gates a run, and every function fails soft. Defines functions only.
# Docs: docs/code/performance-measurement.md, "SUT receive queues". Tests: .buildkite/scripts/test/perf-sut-recvq-test.sh

SUT_RECVQ_CSV_HEADER="ts,source,estab,recvq_nonzero,recvq_sum_bytes,recvq_max_bytes,listen_recvq,read_ms"
SUT_RECVQ_PID=""
SUT_RECVQ_DIR=""
SUT_RECVQ_PY="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)/perf-sut-recvq.py"

# _recvq_proc <proc_root> <pid>: "estab,nonzero,sum,max,listen" from <root>/<pid>/net/tcp{,6}. Field 4
# is the state (01 established, 0A listen); field 5 is tx_queue:rx_queue in hex. A listening socket's
# rx_queue is its accept queue.
_recvq_proc() {
  local f files=()
  for f in "$1/$2/net/tcp" "$1/$2/net/tcp6"; do [ -r "$f" ] && files+=("$f"); done
  [ "${#files[@]}" -gt 0 ] || return 1
  awk '
    function hex(s,  i, n, c) { n = 0; s = tolower(s); for (i = 1; i <= length(s); i++) { c = index("0123456789abcdef", substr(s, i, 1)); if (c == 0) return -1; n = n * 16 + c - 1 } return n }
    $1 ~ /^[0-9]+:$/ { split($5, q, ":"); rq = (q[2] == "00000000") ? 0 : hex(q[2]); if (rq < 0) next; seen = 1
      if ($4 == "01") { e++; if (rq > 0) { nz++; sum += rq; if (rq > mx) mx = rq } } else if ($4 == "0A") lq += rq }
    END { if (!seen) exit 1; printf "%d,%d,%d,%d,%d\n", e, nz, sum, mx, lq }' "${files[@]}"
}

# _recvq_ss <pid>: the same columns from `ss -tanH` run in the SUT's network namespace (needs root).
_recvq_ss() {
  nsenter -t "$1" -n ss -tanH 2>/dev/null | awk '
    NF >= 5 { seen = 1; rq = $2 + 0
      if ($1 == "ESTAB") { e++; if (rq > 0) { nz++; sum += rq; if (rq > mx) mx = rq } } else if ($1 == "LISTEN") lq += rq }
    END { if (!seen) exit 1; printf "%d,%d,%d,%d,%d\n", e, nz, sum, mx, lq }'
}

_recvq_now() { if [ -n "${EPOCHREALTIME:-}" ]; then printf '%.3f' "${EPOCHREALTIME/,/.}"; else date +%s; fi; }

# Runs until stopped, its parent is gone, max_samples or max_bytes; the reason goes to .recvq-end.
_recvq_loop() { # dir source pid root interval max_samples max_bytes parent_pid
  set +e
  local dir="$1" src="$2" i=0 reason="" row stop="" t0 t1
  trap 'stop=1' TERM
  while :; do
    kill -0 "$8" 2>/dev/null || { reason=parent_gone; break; }
    [ "$i" -lt "$6" ] || { reason=max_samples; break; }
    t0="$(_recvq_now)"
    if [ "$src" = ss ]; then row="$(_recvq_ss "$3")"; else row="$(_recvq_proc "$4" "$3")"; fi
    t1="$(_recvq_now)"
    # read_ms (how long the read took) only with sub-second clocks (bash 5's EPOCHREALTIME).
    [ -n "$row" ] && echo "$t0,$src,$row,$(awk -v a="$t0" -v b="$t1" 'BEGIN { if (a ~ /\./) printf "%.1f", (b - a) * 1000 }')" >> "$dir/main-sut-recvq.csv"
    i=$((i + 1)); echo "$i" > "$dir/.recvq-count"
    [ "$(wc -c < "$dir/main-sut-recvq.csv")" -lt "$7" ] || { reason=max_bytes; break; }
    [ -z "$stop" ] || { reason=stopped; break; }
    sleep "$5"
    [ -z "$stop" ] || { reason=stopped; break; }
  done
  echo "$reason" > "$dir/.recvq-end"
}

# sut_recvq_sampler_start <dir> <sut_pid> <max_samples> [interval_s] [max_bytes] [pin_cpuset]: writes
# main-sut-recvq.csv and main-sut-recvq-status.json into <dir>. Source: ss in the SUT's namespace via
# nsenter when that works, else <root>/<pid>/net/tcp{,6} (PERF_PROC_ROOT, default /proc). Engine: the
# python one when python3 (PERF_SUT_RECVQ_PYTHON, a test seam) reads that source, else the shell
# loop, which forks per sample and so never runs faster than once a second. Always returns 0; starts
# nothing when no source is readable, and the status says why.
sut_recvq_sampler_start() {
  local dir="$1" pid="${2:-}" max="${3:-1}" iv="${4:-1}" maxb="${5:-16777216}" pin="${6:-}" root src="" reason="" pinned="" running=false
  local py="${PERF_SUT_RECVQ_PYTHON:-python3}" engine="" req_iv
  SUT_RECVQ_PID=""; SUT_RECVQ_DIR="$dir"
  root="${PERF_PROC_ROOT:-/proc}"; req_iv="$iv"
  {
    echo "$SUT_RECVQ_CSV_HEADER" > "$dir/main-sut-recvq.csv"
    rm -f "$dir/.recvq-end" "$dir/.recvq-count" "$dir/.recvq-stats.json"
    if ! [[ "$pid" =~ ^[1-9][0-9]*$ ]]; then
      reason="no SUT process id (an external target, or the container is not running)"
    elif command -v nsenter >/dev/null 2>&1 && command -v ss >/dev/null 2>&1 && _recvq_ss "$pid" >/dev/null; then
      src=ss
    elif _recvq_proc "$root" "$pid" >/dev/null; then
      src=proc
    else
      reason="neither nsenter + ss (needs root) nor $root/$pid/net/tcp is readable"
    fi
    if [ -n "$src" ]; then
      if [ -r "$SUT_RECVQ_PY" ] && command -v "$py" >/dev/null 2>&1 \
         && { if [ "$src" = ss ]; then "$py" "$SUT_RECVQ_PY" sample ss "$pid"; else "$py" "$SUT_RECVQ_PY" sample proc "$root" "$pid"; fi; } >/dev/null; then
        engine=python
        "$py" "$SUT_RECVQ_PY" run "$dir" "$src" "$pid" "$root" "$iv" "$max" "$maxb" "$$" </dev/null >/dev/null 2>&1 &
      else
        engine=shell
        awk -v i="$iv" 'BEGIN { exit !(i + 0 >= 1) }' || iv=1
        ( _recvq_loop "$dir" "$src" "$pid" "$root" "$iv" "$max" "$maxb" "$$" ) </dev/null >/dev/null 2>&1 &
      fi
      SUT_RECVQ_PID=$!; running=true
      if [ -n "$pin" ] && command -v taskset >/dev/null 2>&1 && taskset -acp "$pin" "$SUT_RECVQ_PID" >/dev/null 2>&1; then pinned="$pin"; fi
    fi
    jq -nc --argjson running "$running" --arg src "$src" --arg reason "$reason" --arg pid "$pid" --arg iv "$iv" --arg req "$req_iv" \
      --arg engine "$engine" --argjson max "$max" --argjson maxb "$maxb" --arg pinned "$pinned" --argjson now "$(date +%s)" '
      {running:$running, source:(if $src == "" then null else $src end), reason:(if $reason == "" then null else $reason end),
       engine:(if $engine == "" then null else $engine end),
       sut_pid:(if $pid == "" then null else $pid end), interval_s:($iv | tonumber), requested_interval_s:($req | tonumber),
       max_samples:$max, max_bytes:$maxb,
       pinned_cpus:(if $pinned == "" then null else $pinned end), started_epoch_s:$now, stopped_epoch_s:null,
       stop_reason:null, samples:null, engine_stats:null, file:"main-sut-recvq.csv",
       note:"report-only; one row per sample of every TCP socket in the SUT network namespace: estab = established sockets, recvq_* = their unread bytes (Recv-Q), listen_recvq = connections waiting in the accept queue, read_ms = how long the read took (null without a sub-second clock); a queue that fills and drains between two samples is missed; the shell engine never samples faster than once a second; engine_stats (python engine) holds the achieved rate, read times, overruns and the sampler CPU"}' \
      > "$dir/main-sut-recvq-status.json"
  } 2>/dev/null || true
  return 0
}

# sut_recvq_sampler_stop: idempotent; stops the sampler and records how and when it ended.
sut_recvq_sampler_stop() {
  local dir="$SUT_RECVQ_DIR" pid="$SUT_RECVQ_PID" reason=not_started n=0
  [ -n "$dir" ] || return 0
  SUT_RECVQ_PID=""; SUT_RECVQ_DIR=""
  {
    if [ -n "$pid" ]; then
      kill -TERM "$pid" 2>/dev/null
      while kill -0 "$pid" 2>/dev/null && [ "$n" -lt 30 ]; do sleep 0.1; n=$((n + 1)); done
      if kill -0 "$pid" 2>/dev/null; then kill -KILL "$pid" 2>/dev/null; reason=killed
      else reason="$(cat "$dir/.recvq-end" 2>/dev/null)"; fi
      wait "$pid" 2>/dev/null
    fi
    # A sampler killed before it recorded a count: its rows are the samples.
    [ -s "$dir/.recvq-count" ] || [ ! -r "$dir/main-sut-recvq.csv" ] \
      || echo $(( $(wc -l < "$dir/main-sut-recvq.csv") - 1 )) > "$dir/.recvq-count"
    jq -c --arg r "${reason:-unknown}" --arg s "$(cat "$dir/.recvq-count" 2>/dev/null)" --argjson now "$(date +%s)" \
      --arg stats "$(cat "$dir/.recvq-stats.json" 2>/dev/null)" '
      . + {stopped_epoch_s:$now, stop_reason:$r, samples:(($s | tonumber?) // 0), engine_stats:($stats | fromjson? // null)}' \
      "$dir/main-sut-recvq-status.json" > "$dir/.recvq-status" && mv -f "$dir/.recvq-status" "$dir/main-sut-recvq-status.json"
    rm -f "$dir/.recvq-end" "$dir/.recvq-count" "$dir/.recvq-stats.json"
  } 2>/dev/null || true
  return 0
}

# sut_recvq_rungs <csv> <start_at_s> <step_s> <gap_s> <settle_s> <rates_csv>: per rung, over its steady
# window [start + settle, start + step), a JSON array of {offered_rps, samples, samples_per_s,
# recvq_nonzero_mean, est_wait_to_read_ms, depth_ms_max, deep_5ms_sample_frac, recvq_sum_bytes_mean,
# recvq_sum_bytes_max, recvq_max_bytes_max, nonzero_sample_frac, listen_recvq_max, read_ms_mean,
# read_ms_max}; [] when no rows. est_wait_to_read_ms is Little's law: the mean sockets holding unread
# bytes over the offered rate, as ms. A sample's depth is its own such count as ms of arrivals;
# deep_5ms_sample_frac is the share of samples at least 5 ms deep. read_ms_* are null without read times.
sut_recvq_rungs() {
  [ -r "$1" ] || { echo '[]'; return 0; }
  awk -F, -v start="$2" -v step="$3" -v gap="$4" -v settle="$5" -v rates="$6" '
    BEGIN { nr = split(rates, off, ",") }
    NR == 1 || $1 !~ /^[0-9.]+$/ { next }
    { k = int(($1 - start) / (step + gap)); if ($1 < start || k >= nr) next
      t = $1 - (start + k * (step + gap)); if (t < settle || t >= step) next
      n[k]++; s[k] += $5; nzm[k] += $4; if ($5 > smx[k]) smx[k] = $5; if ($6 > mx[k]) mx[k] = $6; if ($4 > 0) nzs[k]++; if ($7 > lq[k]) lq[k] = $7
      d = (off[k + 1] > 0) ? $4 / off[k + 1] * 1000 : 0; if (d > dmx[k]) dmx[k] = d; if (d >= 5) deep[k]++
      if ($8 ~ /^[0-9.]+$/) { rn[k]++; rs[k] += $8; if ($8 + 0 > rmx[k]) rmx[k] = $8 + 0 } }
    END { printf "["; for (k = 0; k < nr; k++) { if (!(k in n)) continue
            printf "%s{\"offered_rps\":%s,\"samples\":%d,\"samples_per_s\":%.2f,\"recvq_nonzero_mean\":%.2f,\"est_wait_to_read_ms\":%.4f,\"depth_ms_max\":%.4f,\"deep_5ms_sample_frac\":%.3f,\"recvq_sum_bytes_mean\":%.1f,\"recvq_sum_bytes_max\":%d,\"recvq_max_bytes_max\":%d,\"nonzero_sample_frac\":%.3f,\"listen_recvq_max\":%d,", (c++ ? "," : ""), off[k + 1], n[k], (step > settle ? n[k] / (step - settle) : 0), nzm[k] / n[k], (off[k + 1] > 0 ? nzm[k] / n[k] / off[k + 1] * 1000 : 0), dmx[k], deep[k] / n[k], s[k] / n[k], smx[k], mx[k], nzs[k] / n[k], lq[k]
            if (k in rn) printf "\"read_ms_mean\":%.1f,\"read_ms_max\":%.1f}", rs[k] / rn[k], rmx[k]; else printf "\"read_ms_mean\":null,\"read_ms_max\":null}" }
          print "]" }' "$1"
}
