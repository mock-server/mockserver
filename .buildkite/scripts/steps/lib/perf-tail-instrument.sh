#!/usr/bin/env bash
# Tail-attribution instrumentation for scripts/rw-multi-k6-sweep.sh:
# each k6 process's Go GC trace and per-second series, the quiet seconds the GC-masked figure reads, and
# a host kernel-counter sampler. Report-only:
# nothing here gates a run, and every function fails soft. Defines functions only; bash 3.2 compatible.
# Docs: docs/code/performance-measurement.md, "Tail attribution files".
# Tests: .buildkite/scripts/test/perf-tail-instrument-test.sh

# shellcheck disable=SC2034  # read by scripts/rw-multi-k6-sweep.sh
K6_GC_CSV_HEADER="proc,gc,start_epoch_s,stw_sweep_ms,mark_ms,stw_mark_ms,assist_cpu_ms,bg_cpu_ms,idle_cpu_ms,heap_start_mb,heap_end_mb,heap_live_mb,goal_mb"
K6_TS_CSV_HEADER="ts,proc,rung_offered_rps,t_in_rung_s,reqs,over_5ms,iterations,dropped_iterations,vus,gc_cycles_started,gc_mark_ms,over_bound"
HKS_TCP_COUNTERS="RetransSegs InErrs OutRsts InSegs OutSegs ListenOverflows ListenDrops TCPBacklogDrop TCPTimeouts TCPSynRetrans TCPFastRetrans TCPLossProbes TCPRcvQDrop TCPZeroWindowDrop TCPReqQFullDrop"
HKS_CPU_CSV_HEADER="ts,interval_s,cpu,role,usr_pct,sys_pct,soft_pct,irq_pct,idle_pct,net_rx,net_tx,softnet_processed,softnet_dropped,softnet_squeezed"
HKS_TCP_CSV_HEADER="ts,interval_s,netns,$(tr ' ' ',' <<<"$HKS_TCP_COUNTERS")"

# k6_gctrace_csv <proc> <container_started_epoch_s|null> <log>: one CSV row per GODEBUG=gctrace=1 line
# ("gc N @<s since start>s P%: a+b+c ms clock, ..."). The concurrent mark runs from start + a ms for b ms.
k6_gctrace_csv() {
  [ -r "$3" ] || return 0
  awk -v p="$1" -v st="$2" '
    BEGIN { if (st == "" || st == "null") exit }
    $1 == "gc" && $3 ~ /^@[0-9.]+s$/ && $7 == "clock," && $10 == "cpu," && $12 == "MB," {
      split($5, c, "+"); split($8, u, /[+\/]/); split($11, h, "->")
      printf "%s,%s,%.3f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n", p, $2, st + substr($3, 2), c[1], c[2], c[3], u[2], u[3], u[4], h[1], h[2], h[3], $13 }' "$3"
}

# k6_timeseries_csv <ranges.json> <gc.csv> <start_at_s> <step_s> <gap_s> <rates_csv>: the per-second CSV
# (K6_TS_CSV_HEADER). ranges.json maps reqs/over_5ms/over_bound/iterations/dropped (cumulative) and vus (a gauge) to
# a Prometheus matrix (or null). Row ts covers [ts, ts + 1): counters are cum(ts + 1) - cum(ts), vus is
# the value at ts + 1. A counter before its first sample is 0 (k6 sends dropped_iterations only once one
# drops); a failed query, a hole after the first sample and a fall of more than 0.5 (a reset) are null;
# a smaller fall is rounding in the interpolated over-5 ms count, so 0.
k6_timeseries_csv() {
  local ranges="$1" gc="$2"
  [ -r "$gc" ] || gc=/dev/null
  echo "$K6_TS_CSV_HEADER"
  jq -r 'to_entries[] | select(.key != "failed") | .key as $m
         | (if .value == null then empty else "ok \($m) - -" end), ((.value // [])[] | .metric.proc as $p | .values[] | "\($m) \($p) \(.[0]) \(.[1])")' "$ranges" \
  | awk -v start="$3" -v step="$4" -v gap="$5" -v rates="$6" -v gcfile="$gc" '
    function cum(m, p, t) { return ((m, p, t) in v) ? v[m, p, t] : ((m in okq && (!((m, p) in first) || t < first[m, p])) ? 0 : "") }
    function d(m, p, t,  a, b) { a = cum(m, p, t); b = cum(m, p, t + 1)
      if (a == "" || b == "" || b - a < -0.5) return ""; return (b > a) ? b - a : 0 }
    BEGIN {
      nr = split(rates, off, ",")
      while ((getline line < gcfile) > 0) {
        split(line, f, ","); if (f[1] == "proc" || f[3] == "") continue
        g = ++ng[f[1]]; gs[f[1], g] = f[3] + 0; ms0 = f[3] + f[4] / 1000; m0[f[1], g] = ms0; m1[f[1], g] = ms0 + f[5] / 1000
      }
    }
    $1 == "ok" { okq[$2] = 1; next }
    $4 != "NaN" && $4 !~ /Inf/ { t = int($3 + 0.5); v[$1, $2, t] = $4 + 0; seen[$2, t] = 1; procs[$2] = 1
                                 if (!(($1, $2) in first) || t < first[$1, $2]) first[$1, $2] = t
                                 if (!($2 in lo) || t < lo[$2]) lo[$2] = t; if (!($2 in hi) || t > hi[$2]) hi[$2] = t }
    END {
      for (p in procs) for (t = lo[p]; t < hi[p]; t++) {
        if (!((p, t) in seen) && !((p, t + 1) in seen)) continue
        k = int((t - start) / (step + gap)); rs = start + k * (step + gap)
        rate = ""; tin = ""
        if (t >= start && k < nr && t < rs + step) { rate = off[k + 1]; tin = t - rs }
        cyc = 0; mark = 0
        for (g = 1; g <= ng[p]; g++) {
          if (gs[p, g] >= t && gs[p, g] < t + 1) cyc++
          a = (m0[p, g] > t ? m0[p, g] : t); b = (m1[p, g] < t + 1 ? m1[p, g] : t + 1)
          if (b > a) mark += (b - a) * 1000
        }
        o5 = d("over_5ms", p, t); ob = d("over_bound", p, t); vu = (("vus", p, t + 1) in v) ? v["vus", p, t + 1] : ""
        printf "%d,%s,%s,%s,%s,%s,%s,%s,%s,%d,%.1f,%s\n", t, p, rate, tin, d("reqs", p, t), (o5 == "" ? "" : sprintf("%.1f", o5)),
          d("iterations", p, t), d("dropped", p, t), vu, cyc, mark, (ob == "" ? "" : sprintf("%.1f", ob))
      }
    }' | sort -t, -k2,2 -k1,1n
}

# --- GC-masked figure (report-only) -----------------------------------------------------------------
# k6_gc_masked_rungs <timeseries.csv> <gc.csv> <n_procs> <settle_s> <push_s>: one JSON object,
# {gc_procs, rungs:[{rung_offered_rps, seconds:{measured, quiet, gc, incomplete}, quiet_windows:[[a, b)],
# rows:{requests, over_5ms, over_bound}}]}. A row ts holds the pushes that landed in (ts, ts + 1], so
# requests completed in (ts - push, ts + 1]: it is measured from settle + push into its rung, and a GC
# second when any process's cycle (sweep to mark termination) overlaps that span. A second without a
# complete row from all n processes is never quiet. over_bound is null when a quiet row lacks the column.
k6_gc_masked_rungs() {
  [ -r "$1" ] && [ -r "$2" ] || { echo '{"gc_procs":0,"rungs":[]}'; return 0; }
  awk -F, -v n="$3" -v settle="$4" -v push="$5" '
    FNR == 1 { file++; for (i = 1; i <= NF; i++) col[file, $i] = i; next }
    file == 1 { if ($(col[1, "start_epoch_s"]) == "") next
                if (!($1 in gp)) { gp[$1] = 1; gprocs++ }
                ng++; gs[ng] = $(col[1, "start_epoch_s"]) + 0
                ge[ng] = gs[ng] + ($(col[1, "stw_sweep_ms"]) + $(col[1, "mark_ms"]) + $(col[1, "stw_mark_ms"])) / 1000; next }
    { rate = $(col[2, "rung_offered_rps"]); tin = $(col[2, "t_in_rung_s"])
      if (rate == "" || tin == "" || tin + 0 < settle + push) next
      t = $(col[2, "ts"]) + 0; k = rate SUBSEP t
      if (!(rate in lo)) { order[++nr] = rate; lo[rate] = t; hi[rate] = t }
      if (t < lo[rate]) lo[rate] = t; if (t > hi[rate]) hi[rate] = t
      np[k]++; rq = $(col[2, "reqs"]); o5 = $(col[2, "over_5ms"])
      if (rq == "" || o5 == "") bad[k] = 1
      r[k] += rq; f[k] += o5
      if (!((2, "over_bound") in col) || $(col[2, "over_bound"]) == "") nob[k] = 1; else b[k] += $(col[2, "over_bound"]) }
    END {
      printf "{\"gc_procs\":%d,\"rungs\":[", gprocs
      for (x = 1; x <= nr; x++) {
        rate = order[x]; m = 0; q = 0; g = 0; inc = 0; sr = 0; sf = 0; sb = 0; nb = 0; w = ""; open = 0
        for (t = lo[rate]; t <= hi[rate]; t++) {
          k = rate SUBSEP t; state = "quiet"
          if (!(k in np)) state = "absent"
          else if (np[k] != n || (k in bad)) state = "incomplete"
          else for (i = 1; i <= ng; i++) if (gs[i] < t + 1 && ge[i] > t - push) { state = "gc"; break }
          if (state != "absent") m++
          if (state == "gc") g++; else if (state == "incomplete") inc++
          if (state == "quiet") { q++; sr += r[k]; sf += f[k]; sb += b[k]; if (k in nob) nb = 1
                                  if (!open) { w = w (w == "" ? "" : ",") "[" t; open = 1 } }
          else if (open) { w = w "," t "]"; open = 0 }
        }
        if (open) w = w "," (hi[rate] + 1) "]"
        printf "%s{\"rung_offered_rps\":%s,\"seconds\":{\"measured\":%d,\"quiet\":%d,\"gc\":%d,\"incomplete\":%d},\"quiet_windows\":[%s],\"rows\":{\"requests\":%d,\"over_5ms\":%.1f,\"over_bound\":%s}}",
          (x > 1 ? "," : ""), rate, m, q, g, inc, w, sr, sf, ((nb || q == 0) ? "null" : sprintf("%.1f", sb))
      }
      print "]}"
    }' "$2" "$1"
}

# k6_gc_masked_expr <histogram selector> <quiet_windows JSON>: the PromQL histogram of the requests pushed
# inside the windows, as the sum over windows [a, b) of (cumulative at b) - (cumulative at a). A series
# with no sample yet at a counts as 0 there, which is its cumulative value.
k6_gc_masked_expr() {
  jq -r --arg h "$1" '[ .[] | "(sum(\($h) @ \(.[1])) - sum(\($h) @ \(.[0])))" ] | join(" + ")' <<<"$2"
}

# --- host kernel-counter sampler -------------------------------------------------------------------
# Reads <root>/stat, <root>/softirqs, <root>/net/softnet_stat and, per network namespace, the Tcp: and
# TcpExt: lines of <root>/<pid>/net/{snmp,netstat} ("self" = the sampler's own namespace). Containers
# have their own namespaces, so the host's /proc/net never sees the SUT's or k6's TCP counters.
HOST_KERNEL_SAMPLER_PID=""
HOST_KERNEL_SAMPLER_DIR=""

_hks_root() { echo "${PERF_PROC_ROOT:-/proc}"; }

# _hks_snapshot <root> <netns "label=pid ...">: normalised counter lines for _hks_rows.
_hks_snapshot() {
  local root="$1" ns label pid
  echo "time ${EPOCHREALTIME:-$(date +%s)}" | tr ',' '.'
  [ -r "$root/stat" ] && awk '$1 ~ /^cpu[0-9]+$/ { print "stat", substr($1, 4), $2 + $3, $4, $5 + $6, $7, $8, $9 }' "$root/stat" 2>/dev/null
  [ -r "$root/softirqs" ] && awk 'NR == 1 { for (i = 1; i <= NF; i++) cpu[i] = substr($i, 4); next }
    $1 == "NET_RX:" || $1 == "NET_TX:" { for (i = 2; i <= NF; i++) print "sirq", substr($1, 1, 6), cpu[i - 1], $i }' "$root/softirqs" 2>/dev/null
  [ -r "$root/net/softnet_stat" ] && awk '
    function hex(s,  i, n, c) { n = 0; s = tolower(s); for (i = 1; i <= length(s); i++) { c = index("0123456789abcdef", substr(s, i, 1)); if (c == 0) return -1; n = n * 16 + c - 1 } return n }
    { print "snet", (NF >= 13 ? hex($13) : NR - 1), hex($1), hex($2), hex($3) }' "$root/net/softnet_stat" 2>/dev/null
  for ns in $2; do
    label="${ns%%=*}"; pid="${ns#*=}"
    [ -n "$pid" ] && [ "$pid" != 0 ] || continue # never "$root//net": that is the sampler's own namespace
    cat "$root/$pid/net/snmp" "$root/$pid/net/netstat" 2>/dev/null | awk -v l="$label" '
      $1 == "Tcp:" || $1 == "TcpExt:" { if (!($1 in hdr)) { hdr[$1] = 1; for (i = 2; i <= NF; i++) name[$1, i] = $i; next }
                                        for (i = 2; i <= NF; i++) print "tcp", l, name[$1, i], $i }'
  done
  return 0
}

# _hks_rows <prev> <cur> <roles "role=cpuset ..."> <cpu_csv> <tcp_csv>: appends one interval's rows. Every
# cpu named in a role gets its own row; every other cpu is summed into cpu "other".
_hks_rows() {
  awk -v roles="$3" -v cpuout="$4" -v tcpout="$5" -v counters="$HKS_TCP_COUNTERS" '
    function expand(spec, role,  parts, n, i, ab, c) { n = split(spec, parts, ",")
      for (i = 1; i <= n; i++) { if (split(parts[i], ab, "-") == 2) { for (c = ab[1] + 0; c <= ab[2] + 0; c++) want[c] = role } else if (parts[i] != "") want[parts[i] + 0] = role } }
    function dl(a, b) { return (a == "" || b == "" || b + 0 < a + 0) ? "" : b - a }
    function pct(x, tot) { return (x == "" || tot <= 0) ? "" : sprintf("%.1f", 100 * x / tot) }
    BEGIN { nr = split(roles, r, " "); for (i = 1; i <= nr; i++) { split(r[i], kv, "="); expand(kv[2], kv[1]) }
            nc = split(counters, cn, " ") }
    FNR == 1 { file++ }
    $1 == "time" { t[file] = $2; next }
    $1 == "stat" { for (i = 3; i <= 8; i++) s[file, $2, i] = $i; cpus[$2] = 1; next }
    $1 == "sirq" { q[file, $2, $3] = $4; cpus[$3] = 1; next }
    $1 == "snet" { n[file, $2, 1] = $3; n[file, $2, 2] = $4; n[file, $2, 3] = $5; cpus[$2] = 1; next }
    $1 == "tcp"  { c[file, $2, $3] = $4; ns[$2] = 1; next }
    END {
      if (file < 2) exit
      ts = int(t[1]); iv = sprintf("%.3f", t[2] - t[1])
      for (cpu in cpus) {
        key = (cpu in want) ? cpu : "other"; role[key] = (cpu in want) ? want[cpu] : "other"; keys[key] = 1
        tot = 0; for (i = 3; i <= 8; i++) { x = dl(s[1, cpu, i], s[2, cpu, i]); f[i] = x; tot += x; agg[key, i] += x }
        agg[key, "tot"] += tot
        rx = dl(q[1, "NET_RX", cpu], q[2, "NET_RX", cpu]); tx = dl(q[1, "NET_TX", cpu], q[2, "NET_TX", cpu])
        if (rx != "") { agg[key, "rx"] += rx; hasrx[key] = 1 } if (tx != "") { agg[key, "tx"] += tx; hastx[key] = 1 }
        for (j = 1; j <= 3; j++) { x = dl(n[1, cpu, j], n[2, cpu, j]); if (x != "") { agg[key, "sn" j] += x; hassn[key, j] = 1 } }
      }
      for (key in keys) {
        tot = agg[key, "tot"]
        printf "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n", ts, iv, key, role[key],
          pct(agg[key, 3], tot), pct(agg[key, 4], tot), pct(agg[key, 7], tot), pct(agg[key, 6], tot), pct(agg[key, 5], tot),
          ((key in hasrx) ? agg[key, "rx"] : ""), ((key in hastx) ? agg[key, "tx"] : ""),
          (((key, 1) in hassn) ? agg[key, "sn1"] : ""), (((key, 2) in hassn) ? agg[key, "sn2"] : ""), (((key, 3) in hassn) ? agg[key, "sn3"] : "") >> cpuout
      }
      for (l in ns) { line = ts "," iv "," l; for (i = 1; i <= nc; i++) line = line "," dl(c[1, l, cn[i]], c[2, l, cn[i]]); print line >> tcpout }
    }' "$1" "$2"
}

_hks_why() { if [ "$_HKS_OS" = Linux ]; then echo "$1 is not readable"; else echo "$1 is not readable (host is $_HKS_OS, which has no Linux /proc)"; fi; }
_hks_src() { # name path ok reason: adds one source to the caller's $out
  out="$(jq -c --arg k "$1" --arg p "$2" --argjson ok "$3" --arg r "$4" '. + {($k): {path:$p, available:$ok, reason:(if $ok then null else $r end)}}' <<<"$out")"
}
# _hks_probe <root> <netns>: one JSON object per source, {path, available, reason}.
_hks_probe() {
  local root="$1" out="{}" ns label pid
  _HKS_OS="$(uname -s 2>/dev/null || echo unknown)"
  if grep -q '^cpu0 ' "$root/stat" 2>/dev/null; then _hks_src proc_stat "$root/stat" true ""; else _hks_src proc_stat "$root/stat" false "$(_hks_why "$root/stat")"; fi
  if grep -q 'NET_RX:' "$root/softirqs" 2>/dev/null; then _hks_src softirqs "$root/softirqs" true ""; else _hks_src softirqs "$root/softirqs" false "$(_hks_why "$root/softirqs")"; fi
  if grep -q . "$root/net/softnet_stat" 2>/dev/null; then _hks_src softnet_stat "$root/net/softnet_stat" true "" # procfs reads size 0: not -s
  else _hks_src softnet_stat "$root/net/softnet_stat" false "$(_hks_why "$root/net/softnet_stat")"; fi
  for ns in $2; do
    label="${ns%%=*}"; pid="${ns#*=}"
    if [ -z "$pid" ] || [ "$pid" = 0 ]; then _hks_src "tcp_$label" "" false "no process id for this namespace (container not running?)"
    elif grep -q '^Tcp:' "$root/$pid/net/snmp" 2>/dev/null; then _hks_src "tcp_$label" "$root/$pid/net" true ""
    else _hks_src "tcp_$label" "$root/$pid/net" false "$(_hks_why "$root/$pid/net/snmp")"; fi
  done
  echo "$out"
}

_hks_sleep() { # interval: whole seconds land just after a second boundary when EPOCHREALTIME is there
  local now us
  if [ "$1" = 1 ] && [ -n "${EPOCHREALTIME:-}" ]; then
    now="${EPOCHREALTIME/,/.}"; us="${now#*.}"
    sleep "$(awk -v u="$us" 'BEGIN { printf "%.3f", (1020000 - u) / 1e6 }')"
  else
    sleep "$1"
  fi
}

# Runs until stopped, its parent is gone, max_samples or max_bytes; the reason goes to .hks-end.
_hks_loop() { # dir root roles netns interval max_samples max_bytes parent_pid
  set +e
  local dir="$1" i=0 reason="" bytes stop=""
  # Bash runs this between commands, so a stop never leaves a half-written row.
  trap 'stop=1' TERM
  _hks_snapshot "$2" "$4" > "$dir/.hks-prev"
  while :; do
    kill -0 "$8" 2>/dev/null || { reason=parent_gone; break; }
    [ "$i" -lt "$6" ] || { reason=max_samples; break; }
    _hks_sleep "$5"
    [ -z "$stop" ] || { reason=stopped; break; }
    _hks_snapshot "$2" "$4" > "$dir/.hks-cur"
    _hks_rows "$dir/.hks-prev" "$dir/.hks-cur" "$3" "$dir/host-kernel-cpu.csv" "$dir/host-kernel-tcp.csv"
    mv -f "$dir/.hks-cur" "$dir/.hks-prev"
    i=$((i + 1)); echo "$i" > "$dir/.hks-count"
    bytes=$(( $(wc -c < "$dir/host-kernel-cpu.csv") + $(wc -c < "$dir/host-kernel-tcp.csv") ))
    [ "$bytes" -lt "$7" ] || { reason=max_bytes; break; }
    [ -z "$stop" ] || { reason=stopped; break; }
  done
  times > "$dir/.hks-times" # its own CPU, shell and children (awk, cat, sleep), for the status
  echo "$reason" > "$dir/.hks-end"
}

# host_kernel_sampler_start <dir> <roles "role=cpuset ..."> <netns "label=pid ..."> <max_samples>
#   [interval_s] [max_bytes] [pin_cpuset]: writes host-kernel-{cpu,tcp}.csv and host-kernel-status.json
# into <dir>. Always returns 0. Starts nothing when no source is readable (the status says why).
host_kernel_sampler_start() {
  local dir="$1" roles="$2" netns="$3" max="$4" iv="${5:-1}" maxb="${6:-33554432}" pin="${7:-}" root sources running=false pinned=""
  HOST_KERNEL_SAMPLER_PID=""; HOST_KERNEL_SAMPLER_DIR="$dir"
  root="$(_hks_root)"
  {
    echo "$HKS_CPU_CSV_HEADER" > "$dir/host-kernel-cpu.csv"
    echo "$HKS_TCP_CSV_HEADER" > "$dir/host-kernel-tcp.csv"
    rm -f "$dir/.hks-end" "$dir/.hks-count" "$dir/.hks-prev" "$dir/.hks-cur" "$dir/.hks-times"
    sources="$(_hks_probe "$root" "$netns")" || sources=""
    [ -n "$sources" ] || sources='{}'
    if jq -e 'any(.[]; .available)' >/dev/null 2>&1 <<<"$sources"; then
      ( _hks_loop "$dir" "$root" "$roles" "$netns" "$iv" "$max" "$maxb" "$$" ) </dev/null >/dev/null 2>&1 &
      HOST_KERNEL_SAMPLER_PID=$!; running=true
      if [ -n "$pin" ] && command -v taskset >/dev/null 2>&1 && taskset -acp "$pin" "$HOST_KERNEL_SAMPLER_PID" >/dev/null 2>&1; then pinned="$pin"; fi
    fi
    jq -nc --argjson src "$sources" --arg roles "$roles" --arg netns "$netns" --argjson max "$max" --arg iv "$iv" \
      --argjson maxb "$maxb" --arg pinned "$pinned" --argjson running "$running" --arg root "$root" --argjson now "$(date +%s)" '
      {running:$running, started_epoch_s:$now, proc_root:$root, interval_s:($iv | tonumber), max_samples:$max, max_bytes:$maxb,
       pinned_cpus:(if $pinned == "" then null else $pinned end), roles:$roles, netns:$netns, sources:$src,
       reason:(if $running then null else "no kernel counter source is readable on this host" end),
       stopped_epoch_s:null, stop_reason:null, samples:null, truncated:null,
       note:"report-only; host-kernel-cpu.csv and host-kernel-tcp.csv rows cover [ts, ts + interval_s); an empty cell is a counter that was unavailable (see sources)"}' \
      > "$dir/host-kernel-status.json"
  } 2>/dev/null || true
  return 0
}

# host_kernel_sampler_stop: idempotent; kills the sampler and records how and when it ended.
host_kernel_sampler_stop() {
  local dir="$HOST_KERNEL_SAMPLER_DIR" pid="$HOST_KERNEL_SAMPLER_PID" reason=not_started samples cpu n=0
  [ -n "$dir" ] || return 0
  HOST_KERNEL_SAMPLER_PID=""; HOST_KERNEL_SAMPLER_DIR=""
  {
    if [ -n "$pid" ]; then
      # TERM lets the loop finish its row; KILL after 3 s covers a loop stuck on a read.
      kill -TERM "$pid" 2>/dev/null
      while kill -0 "$pid" 2>/dev/null && [ "$n" -lt 30 ]; do sleep 0.1; n=$((n + 1)); done
      if kill -0 "$pid" 2>/dev/null; then kill -KILL "$pid" 2>/dev/null; reason=killed
      else reason="$(cat "$dir/.hks-end" 2>/dev/null)"; fi
      wait "$pid" 2>/dev/null
    fi
    samples="$(cat "$dir/.hks-count" 2>/dev/null)"
    # `times` prints "XmY.YYYs XmY.YYYs" for the shell, then for its children: user and system.
    cpu="$(awk '{ for (i = 1; i <= 2; i++) { split($i, a, "m"); t += a[1] * 60 + a[2] } } END { if (NR) printf "%.3f", t }' "$dir/.hks-times" 2>/dev/null)"
    jq -c --arg r "${reason:-unknown}" --arg s "${samples:-0}" --argjson now "$(date +%s)" --arg cpu "$cpu" '
      ($cpu | if . == "" then null else tonumber end) as $c
      | . + {stopped_epoch_s:$now, stop_reason:$r, samples:($s | tonumber), truncated:($r == "max_samples" or $r == "max_bytes"),
             sampler_cpu_s:$c,
             sampler_cpu_pct_of_one_cpu:(if $c == null or $now <= .started_epoch_s then null
                                         else ($c * 1000 / ($now - .started_epoch_s) | round) / 10 end)}' \
      "$dir/host-kernel-status.json" > "$dir/.hks-status" && mv -f "$dir/.hks-status" "$dir/host-kernel-status.json"
    rm -f "$dir/.hks-prev" "$dir/.hks-cur" "$dir/.hks-end" "$dir/.hks-count" "$dir/.hks-times"
  } 2>/dev/null || true
  return 0
}
