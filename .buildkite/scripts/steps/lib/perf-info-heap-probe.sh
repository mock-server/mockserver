#!/usr/bin/env bash
# INFO-arm live-heap reset probe, sourced by perf-test-run.sh. After the INFO sweep, with the event
# log left full, it measures what the retained log costs in live heap: a class histogram, a
# PUT /mockserver/reset, a forced GC and a second histogram. The live-heap drop divided by the
# counted bytes the reset released is the multiple docs/code/memory-management.md tabulates.
# Notify-only: every failure becomes a null field plus a problem string, never a non-zero exit.
# Defines functions only. Docs: docs/code/performance-measurement.md. Tests:
# .buildkite/scripts/test/perf-info-heap-probe-test.sh

# The "Total" line's byte count from `jcmd <pid> GC.class_histogram` on stdin; empty when absent.
histogram_total_bytes() {
  awk '$1 == "Total" && $3 ~ /^[0-9]+$/ {t = $3} END {if (t != "") print t}' || true
}

# _ihp_bounded <seconds> <cmd...>: the command, killed after <seconds>. Uses timeout(1) when present.
_ihp_bounded() {
  local s="$1" pid w rc=0; shift
  if command -v timeout >/dev/null 2>&1; then timeout "$s" "$@"; return; fi
  "$@" & pid=$!
  ( sleep "$s"; kill "$pid" ) >/dev/null 2>&1 & w=$!
  wait "$pid" || rc=$?
  kill "$w" >/dev/null 2>&1 || true; wait "$w" 2>/dev/null || true
  return "$rc"
}

_ihp_left() { echo $(( IHP_END - $(date -u +%s) )); }

# _ihp_gauges: "retained_bytes retained_entries ring_occupancy in_flight_bytes" from metrics text on
# stdin, each "-" when absent.
_ihp_gauges() {
  awk '
    function v(x) { return x == "" ? "-" : sprintf("%.0f", x) }
    $1 == "mock_server_event_log_retained_bytes" {b = $2} $1 == "mock_server_event_log_retained_entries" {e = $2}
    $1 == "mock_server_event_log_ring_occupancy" {o = $2} $1 == "mock_server_event_log_in_flight_bytes" {f = $2}
    END {print v(b), v(e), v(o), v(f)}'
}

# _ihp_sidecar <label> <uid:gid> <cmd...>: runs the command in a JDK sidecar sharing the INFO SUT's
# PID namespace (its JVM is pid 1), bounded by the time left. Output in IHP_OUT. Called directly,
# never inside $(...), so the problems it records reach the caller.
_ihp_sidecar() {
  local label="$1" user="$2" name rc=0 tmo; shift 2
  IHP_OUT=""
  tmo="$(_ihp_left)"; [ "$tmo" -le 90 ] || tmo=90
  if [ "$tmo" -le 0 ]; then IHP_PROBLEMS+=("deadline reached before $label"); return 1; fi
  name="info-heap-probe-${IHP_CONTAINER}-${label}"
  IHP_OUT="$(_ihp_bounded "$tmo" docker run --rm --name "$name" --pid="container:$IHP_CONTAINER" --user "$user" \
               "$PERF_HISTO_JDK_IMAGE" "$@" 2>&1)" || rc=$?
  if [ "$rc" -ne 0 ]; then
    docker rm -f "$name" >/dev/null 2>&1 || true
    IHP_PROBLEMS+=("$label failed or timed out after ${tmo}s (exit $rc): $(printf '%s' "$IHP_OUT" | tail -2 | tr '\n' ' ')")
    return 1
  fi
}

# _ihp_jcmd <label> <jcmd command...>: jcmd against the SUT JVM as its own uid (attach requires it).
_ihp_jcmd() { local label="$1"; shift; _ihp_sidecar "$label" "${IHP_UID}:${IHP_GID}" jcmd 1 "$@"; }

# _ihp_histogram <label>: sets IHP_TOTAL to the live-heap total in bytes (GC.class_histogram runs a
# full GC first), empty on failure; the top of the histogram is kept in $IHP_DIR for the bundle.
_ihp_histogram() {
  IHP_TOTAL=""
  _ihp_jcmd "histogram-$1" GC.class_histogram || return 0
  IHP_TOTAL="$(histogram_total_bytes <<<"$IHP_OUT")"
  [ -z "$IHP_DIR" ] || awk 'NR <= 43 {print; next} {last = $0} END {if (NR > 43) print last}' <<<"$IHP_OUT" > "$IHP_DIR/reset-probe-histogram-$1.txt" 2>/dev/null || true
  [ -n "$IHP_TOTAL" ] || IHP_PROBLEMS+=("jcmd GC.class_histogram ($1) printed no Total line")
}

# info_heap_probe_json <drained> <entries_before> <bytes_before> <entries_after> <bytes_after>
#   <heap_before> <heap_after> <problems_json_array> [gc] -> the info_log_level_arm.live_heap_reset_probe
#   record. Empty arguments become null; derived fields are null unless both of their inputs exist.
info_heap_probe_json() {
  jq -nc --arg drained "$1" --arg eb "$2" --arg bb "$3" --arg ea "$4" --arg ba "$5" \
    --arg hb "$6" --arg ha "$7" --argjson problems "${8:-[]}" --arg gc "${9:-}" '
    def n: if . == "" or . == "-" then null else tonumber end;
    ($eb | n) as $eb | ($bb | n) as $bb | ($ea | n) as $ea | ($ba | n) as $ba
    | ($hb | n) as $hb | ($ha | n) as $ha
    | (if $bb != null and $ba != null then $bb - $ba else null end) as $counted
    | (if $eb != null and $ea != null then $eb - $ea else null end) as $entries
    | (if $hb != null and $ha != null then $hb - $ha else null end) as $delta
    | {
        notify_only: true,
        method: "jcmd GC.class_histogram before and after PUT /mockserver/reset (GC.run first), the INFO sweep traffic left in the log",
        gc: (if $gc == "" then null else $gc end),
        drained: (if $drained == "" then null else $drained == "true" end),
        retained_entries: $eb,
        retained_counted_bytes: $bb,
        residual_entries_after_reset: $ea,
        residual_counted_bytes_after_reset: $ba,
        released_entries: $entries,
        released_counted_bytes: $counted,
        live_heap_before_reset_bytes: $hb,
        live_heap_after_reset_bytes: $ha,
        live_heap_delta_bytes: $delta,
        live_heap_per_counted_byte: (if $delta != null and $counted != null and $counted > 0 then ($delta / $counted * 1000 | round) / 1000 else null end),
        live_heap_bytes_per_entry: (if $delta != null and $entries != null and $entries > 0 then ($delta / $entries | round) else null end),
        problems: $problems
      }
      | .status = (if .live_heap_per_counted_byte != null and (.problems | length) == 0 then "measured"
                   elif .live_heap_per_counted_byte != null then "measured_with_problems"
                   else "failed" end)'
}

# _ihp_read_gauges: one scrape into IHP_B IHP_E IHP_O IHP_F (retained bytes and entries, ring
# occupancy, in-flight bytes), each empty when absent, and the text into IHP_METRICS; returns 1 when
# the scrape failed.
_ihp_read_gauges() {
  IHP_B=""; IHP_E=""; IHP_O=""; IHP_F=""
  IHP_METRICS="$(curl -sf --max-time 4 "http://$IHP_HOSTPORT/mockserver/metrics" 2>/dev/null)" || { IHP_METRICS=""; return 1; }
  read -r IHP_B IHP_E IHP_O IHP_F <<<"$(_ihp_gauges <<<"$IHP_METRICS")"
  [ "$IHP_B" != "-" ] || IHP_B=""; [ "$IHP_E" != "-" ] || IHP_E=""
  [ "$IHP_O" != "-" ] || IHP_O=""; [ "$IHP_F" != "-" ] || IHP_F=""
}

# info_heap_probe_run <container> <host:port> <diag_dir> <deadline_s> -> the record on
# stdout; warnings on stderr. Always returns 0. Reads PERF_HISTO_JDK_IMAGE, and the optional
# PERF_INFO_HEAP_PROBE_DRAIN_S (default 60) and PERF_INFO_HEAP_PROBE_POLL_S (default 1).
info_heap_probe_run() {
  IHP_CONTAINER="$1"; IHP_HOSTPORT="$2"; IHP_DIR="$3"; IHP_UID=""; IHP_GID=""
  IHP_END=$(( $(date -u +%s) + ${4:-240} )); IHP_PROBLEMS=()
  local poll="${PERF_INFO_HEAP_PROBE_POLL_S:-1}" bound prev="" cur drained=false
  local eb="" bb="" ea="" ba="" hb="" ha="" gc="" problems p
  bound=$(( $(date -u +%s) + ${PERF_INFO_HEAP_PROBE_DRAIN_S:-60} )); [ "$bound" -le "$IHP_END" ] || bound="$IHP_END"
  # Drained: the ring and the in-flight bytes are empty (or not exported) and two scrapes in a row
  # agree on the retained log.
  cur="unreachable"
  while :; do
    if _ihp_read_gauges; then
      cur="${IHP_B:--} ${IHP_E:--} ${IHP_O:--} ${IHP_F:--}"
      if [ -n "$IHP_B" ] && [ "${IHP_O:-0}" = 0 ] && [ "${IHP_F:-0}" = 0 ] && [ "$cur" = "$prev" ]; then drained=true; break; fi
      prev="$cur"
    fi
    [ "$(date -u +%s)" -lt "$bound" ] || break
    sleep "$poll"
  done
  [ "$drained" = true ] || IHP_PROBLEMS+=("event log not drained (ring and in-flight bytes empty, two equal scrapes) within the drain bound; last scrape (bytes entries ring in_flight): $cur")
  # shellcheck disable=SC2016 # awk's $2, not the shell's
  if _ihp_sidecar uid 0:0 sh -c 'awk "/^Uid:/{u=\$2} /^Gid:/{g=\$2} END{print u, g}" /proc/1/status'; then
    read -r IHP_UID IHP_GID <<<"$(printf '%s\n' "$IHP_OUT" | tail -1)" || true
  fi
  if [[ "$IHP_UID" =~ ^[0-9]+$ ]] && [[ "$IHP_GID" =~ ^[0-9]+$ ]]; then
    _ihp_histogram before; hb="$IHP_TOTAL"
  else
    IHP_PROBLEMS+=("INFO SUT JVM uid/gid unresolved ('$IHP_UID:$IHP_GID'), so no histograms were taken")
  fi
  # The gauges at H1, so the counted bytes describe the log the histogram saw.
  _ihp_read_gauges || true; bb="$IHP_B"; eb="$IHP_E"
  gc="$(awk 'index($1, "jvm_runtime_info{") == 1 && match($0, /[{,]gc="[^"]*"/) {v = substr($0, RSTART, RLENGTH); sub(/^[{,]gc="/, "", v); sub(/"$/, "", v); print v; exit}' <<<"$IHP_METRICS")"
  [ -n "$bb" ] || IHP_PROBLEMS+=("mock_server_event_log_retained_bytes unreadable before the reset")
  if [ -n "$hb" ] && [ -n "$bb" ]; then
    if [ "$(_ihp_left)" -le 0 ]; then
      IHP_PROBLEMS+=("deadline reached before PUT /mockserver/reset")
    elif ! curl -sf --max-time 15 -X PUT "http://$IHP_HOSTPORT/mockserver/reset" >/dev/null 2>&1; then
      IHP_PROBLEMS+=("PUT /mockserver/reset failed")
    else
      bound=$(( $(date -u +%s) + 30 )); [ "$bound" -le "$IHP_END" ] || bound="$IHP_END"
      while :; do
        if _ihp_read_gauges && [ -n "$IHP_B" ] && [ "$IHP_B" -lt "$bb" ]; then ba="$IHP_B"; ea="$IHP_E"; break; fi
        [ "$(date -u +%s)" -lt "$bound" ] || { IHP_PROBLEMS+=("mock_server_event_log_retained_bytes did not fall after PUT /mockserver/reset"); break; }
        sleep "$poll"
      done
      if [ -n "$ba" ] && _ihp_jcmd gc-run GC.run; then
        _ihp_histogram after; ha="$IHP_TOTAL"
      fi
    fi
  fi
  problems='[]'
  for p in ${IHP_PROBLEMS[@]+"${IHP_PROBLEMS[@]}"}; do
    echo "WARNING: INFO live-heap reset probe: $p" >&2
    problems="$(jq -c --arg p "$p" '. + [$p]' <<<"$problems")"
  done
  info_heap_probe_json "$drained" "$eb" "$bb" "$ea" "$ba" "$hb" "$ha" "$problems" "$gc" \
    || jq -nc '{notify_only: true, status: "failed", problems: ["record assembly failed"]}'
  return 0
}
