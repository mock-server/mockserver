# shellcheck shell=bash
# Hardware-matrix knee diagnostics, sourced by
# lib/perf-percore.sh; report-only, never a gate.
# GC time and count, event-log drops (summed over every reason) and evictions from the metrics scrape the sampler already
# makes, plus the SUT cgroup's memory.events and PSI totals (us) when the host exposes them
# (cgroup v2, systemd or cgroupfs driver); a blank cell is unreadable, never zero.
# shellcheck disable=SC2034  # read by perf-percore.sh and the fixture test
JVM_COLS="gc_seconds,gc_count,dropped_log_events,evicted_log_entries,memev_high,memev_max,memev_oom,memev_oom_kill,cpu_some_us,cpu_full_us,mem_some_us,mem_full_us"
sut_cgroup_dir() { # full container id -> its cgroup v2 dir on the host, or nothing
  local d
  [ -n "$1" ] || return 0
  for d in "/sys/fs/cgroup/system.slice/docker-$1.scope" "/sys/fs/cgroup/docker/$1"; do
    [ -r "$d/memory.events" ] && { echo "$d"; return 0; }
  done
  return 0
}
jvm_metric_cols() { # metrics text on stdin
  awk '/^jvm_gc_collection_seconds_sum / { s = $2 } /^jvm_gc_collection_count / { c = $2 }
       /^mock_server_dropped_log_events_total[{ ]/ { d += $2; dn = 1 } /^mock_server_evicted_log_entries_total / { e = $2 }
       END { printf "%s,%s,%s,%s", s, (c == "" ? "" : sprintf("%.0f", c)), (dn ? sprintf("%.0f", d) : ""), (e == "" ? "" : sprintf("%.0f", e)) }'
}
cgroup_cols() { # cgroup dir (may be empty)
  local m="" cp="" mp=""
  if [ -n "$1" ]; then
    m="$(cat "$1/memory.events" 2>/dev/null || true)"; cp="$(cat "$1/cpu.pressure" 2>/dev/null || true)"
    mp="$(cat "$1/memory.pressure" 2>/dev/null || true)"
  fi
  printf '%s\n--cpu\n%s\n--mem\n%s\n' "$m" "$cp" "$mp" | awk '
    /^--cpu$/ { sec = "cpu"; next } /^--mem$/ { sec = "mem"; next }
    sec == "" { v[$1] = $2; next }
    { for (i = 2; i <= NF; i++) if ($i ~ /^total=/) { split($i, t, "="); v[sec "_" $1] = t[2] } }
    END { printf "%s,%s,%s,%s,%s,%s,%s,%s", v["high"], v["max"], v["oom"], v["oom_kill"],
            v["cpu_some"], v["cpu_full"], v["mem_some"], v["mem_full"] }'
}
# Per rung steady window [start + settle, start + step]: each counter's change between its
# first and last sample there, and per second of that span (PSI as a fraction of wall time).
jvm_rungs_json() { # samples_csv rung_windows_json step_s settle_s
  [ -n "$1" ] && [ -s "$1" ] || { echo null; return 0; }
  jq -Rsc --argjson rw "$2" --argjson step "$3" --argjson settle "$4" '
    (split("\n") | map(select(length > 0))) as $lines
    | ($lines[0] | split(",")) as $h
    | [ $lines[1:][] | split(",") | map(if . == "" then null else (tonumber? // null) end) ] as $s
    | def delta($i): [ .[] | select(.[$i] != null) | [.[0], .[$i]] ]
        | if length < 2 then null else {d:(.[-1][1] - .[0][1]), dt:(.[-1][0] - .[0][0])} end;
      def r4: if . == null then null else (. * 10000 | round) / 10000 end;
      [ $rw[] | select(.start_epoch_ms != null) | (.start_epoch_ms / 1000) as $t0
        | [ $s[] | select(.[0] >= $t0 + $settle and .[0] <= $t0 + $step) ] as $w
        | (reduce range(1; $h | length) as $i ({}; .[$h[$i]] = ($w | delta($i)))) as $d
        | def per_s($k; $scale): if $d[$k] == null or $d[$k].dt <= 0 then null else ($d[$k].d / $d[$k].dt / $scale | r4) end;
          {offered_rps, samples:($w | length),
           gc_seconds_per_s:per_s("gc_seconds"; 1), gc_count:$d.gc_count.d,
           dropped_log_events:$d.dropped_log_events.d, evicted_log_entries_per_s:per_s("evicted_log_entries"; 1),
           memory_events:{high:$d.memev_high.d, max:$d.memev_max.d, oom:$d.memev_oom.d, oom_kill:$d.memev_oom_kill.d},
           cpu_pressure_some_frac:per_s("cpu_some_us"; 1000000), cpu_pressure_full_frac:per_s("cpu_full_us"; 1000000),
           memory_pressure_some_frac:per_s("mem_some_us"; 1000000), memory_pressure_full_frac:per_s("mem_full_us"; 1000000)} ]' "$1"
}
