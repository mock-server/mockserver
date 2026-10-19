#!/usr/bin/env bash
# Helpers for perf-test-info-investigation.sh (docs/infrastructure/ci-cd.md#info-level-investigation).
# Defines functions only. Tests: .buildkite/scripts/test/perf-info-investigation-test.sh

# Every container the step starts carries these labels: the run id, and the agent that started it.
PERF_INV_LABEL=mockserver-perf-inv
PERF_INV_AGENT_LABEL=mockserver-perf-inv.agent
inv_agent() { printf '%s' "${BUILDKITE_AGENT_NAME:-$(hostname 2>/dev/null || echo local)}"; }

# inv_reap [run_id]: force-removes that run's containers (by label, and by anchored name for the probe's
# jcmd sidecars, which carry no label); with no run id, the containers this agent started in an earlier
# job, which only a SIGKILLed job leaves behind. Another agent's containers are never touched.
inv_reap() {
  local ids
  if [ -n "${1:-}" ]; then
    ids="$( { docker ps -aq --filter "label=$PERF_INV_LABEL=$1"; docker ps -aq --filter "name=inv-(.*-)?$1(-|\$)"; } 2>/dev/null | sort -u)" || true
  else
    ids="$(docker ps -aq --filter "label=$PERF_INV_AGENT_LABEL=$(inv_agent)" 2>/dev/null | sort -u)" || true
  fi
  # shellcheck disable=SC2086 # one id per word
  [ -z "$ids" ] || docker rm -f $ids >/dev/null 2>&1 || true
}

# A cell's INFO overload phase backed the ring up when a sample held at least 93% of the in-flight cap,
# the same threshold as the fill, or the phase added drops of either cause. Otherwise there is no ring
# backlog to measure, and the phase is recorded as not_reached rather than failed, provided the load
# is shown to have run through the window (load_ran).
INV_RING_BACKED_UP_JQ='def ring_backed_up:
  (.overload.counters_after.max_in_flight_bytes // 0) as $cap
  | $cap > 0 and ((([.overload.samples[]?.in_flight_bytes // 0] | max // 0) >= 0.93 * $cap)
      or (.overload.counters_after.dropped_ring_full // 0) > (.counters_after_fill.dropped_ring_full // 0)
      or (.overload.counters_after.dropped_in_flight_bytes // 0) > (.counters_after_fill.dropped_in_flight_bytes // 0));
def overload_status: if ring_backed_up then "measured" else "not_reached" end;
def load_ran:
  .overload.load_running_at_end == true
  and ([.overload.window_open_evicted, (.overload.samples[]?.evicted)] as $e
       | ($e | length) >= 4 and all($e[]; . != null)
         and all(range(1; $e | length) as $i | $e[$i] > $e[$i - 1]; .));'

# inv_cells_verdict <cells.jsonl> <expected cell count> <overload expected: true|false>: prints one
# line per missing or not fully measured cell and returns 1 when there is any, else returns 0.
inv_cells_verdict() {
  local cells="$1" expected="$2" overload="$3" got bad nl=$'\n'
  got="$(jq -s 'length' "$cells" 2>/dev/null)" || got="unreadable"
  bad="$(jq -r --argjson overload "$overload" "$INV_RING_BACKED_UP_JQ"'
    def why:
      if .status != "measured" then "status \(.status)"
      elif (.drained_probe.status // "missing") != "measured" then "drained probe \(.drained_probe.status // "missing")"
      elif (.fill.peak_fraction_of_budget // 0) < 0.93 then "the fill reached \(.fill.peak_fraction_of_budget) of the budget"
      elif .profile == "proxy10k" and (.upstream_retained_entries // 0) <= 0 then "no request reached the upstream"
      elif .level == "INFO" and $overload and .overload == null then "no overload phase"
      elif .overload == null then empty
      elif .overload.reached_93pct_before_window != true then "the overload window opened below 93% of the budget"
      elif (.overload.drained_probe.status // "missing") != "measured" then "overload drained probe \(.overload.drained_probe.status // "missing")"
      elif ((.overload.samples // []) | length) == 0
        or ([.overload.samples[] | select(.whole_log_bytes == null)] | length) > 0 then "an overload sample has no whole-log figure"
      elif ([.overload.samples[] | select(.in_flight_bytes == null)] | length) > 0 then "an overload sample has no in-flight reading"
      elif (.overload.counters_after.max_in_flight_bytes // 0) <= 0 then "the overload has no in-flight cap reading"
      elif .overload.status != null and .overload.status != overload_status then "overload status \(.overload.status) does not match its samples"
      elif ring_backed_up and ([.overload.samples[] | select(.in_flight_bytes > 0 and .ring_multiple == null)] | length) > 0
        then "an overload sample has no ring figure"
      elif ring_backed_up and ([.overload.samples[] | select(.ring_multiple != null)] | length) == 0
        then "the overload backed up, but no sample has a ring figure"
      elif (ring_backed_up | not) and (load_ran | not)
        then "the ring never backed up, but the overload load is not shown to have run through the window"
      else empty end;
    "\(.cell // "?"): \(why)"' "$cells" 2>/dev/null)" || bad="$cells is not valid JSON lines"
  if [ "$got" != "$expected" ]; then bad="${bad:+$bad$nl}expected $expected cells, found $got"; fi
  [ -n "$bad" ] || return 0
  printf '%s\n' "$bad"
  return 1
}

# inv_promo_verdict <cells.jsonl> <expected cell count>: the PART=promotion counterpart of
# inv_cells_verdict. A cell counts only when its budget was confirmed by the server's gauges, the
# prefill reached 93% of it, the GC log is the expected collector's, and every rung was derived with
# no failed request.
inv_promo_verdict() {
  local cells="$1" expected="$2" got bad nl=$'\n'
  got="$(jq -s 'length' "$cells" 2>/dev/null)" || got="unreadable"
  bad="$(jq -r '
    def why:
      if .status != "measured" then "status \(.status)"
      elif .gauges.max_retained_bytes != .budget_bytes then "budget \(.gauges.max_retained_bytes) is not \(.budget_bytes)"
      elif (.prefill.peak_fraction_of_budget // 0) < 0.93 then "the prefill reached \(.prefill.peak_fraction_of_budget) of the budget"
      elif .analysis.collector != .collector then "the GC log is \(.analysis.collector), not \(.collector)"
      elif ((.analysis.rungs // []) | length) == 0 then "no rung was derived"
      else ([.analysis.rungs[] | if .status != "measured" then "rung \(.offered_rps): \(.status)"
               elif (.error_rate // 0) > 0 then "rung \(.offered_rps): error rate \(.error_rate)" else empty end] | first // empty) end;
    "\(.cell // "?"): \(why)"' "$cells" 2>/dev/null)" || bad="$cells is not valid JSON lines"
  if [ "$got" != "$expected" ]; then bad="${bad:+$bad$nl}expected $expected cells, found $got"; fi
  [ -n "$bad" ] || return 0
  printf '%s\n' "$bad"
  return 1
}
