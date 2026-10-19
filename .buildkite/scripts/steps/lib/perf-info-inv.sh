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

# inv_cells_verdict <cells.jsonl> <expected cell count> <overload expected: true|false>: prints one
# line per missing or not fully measured cell and returns 1 when there is any, else returns 0.
inv_cells_verdict() {
  local cells="$1" expected="$2" overload="$3" got bad nl=$'\n'
  got="$(jq -s 'length' "$cells" 2>/dev/null)" || got="unreadable"
  bad="$(jq -r --argjson overload "$overload" '
    def why:
      if .status != "measured" then "status \(.status)"
      elif (.drained_probe.status // "missing") != "measured" then "drained probe \(.drained_probe.status // "missing")"
      elif (.fill.peak_fraction_of_budget // 0) < 0.93 then "the fill reached \(.fill.peak_fraction_of_budget) of the budget"
      elif .profile == "proxy10k" and (.upstream_retained_entries // 0) <= 0 then "no request reached the upstream"
      elif .level == "INFO" and $overload and .overload == null then "no overload phase"
      elif .overload == null then empty
      elif .overload.reached_93pct_before_window != true then "the overload window opened below 93% of the budget"
      elif (.overload.drained_probe.status // "missing") != "measured" then "overload drained probe \(.overload.drained_probe.status // "missing")"
      elif ([.overload.samples[]? | select(.whole_log_bytes == null or .ring_multiple == null)] | length) > 0
        or (.overload.samples | length) == 0 then "an overload sample has no whole-log or ring figure"
      else empty end;
    "\(.cell // "?"): \(why)"' "$cells" 2>/dev/null)" || bad="$cells is not valid JSON lines"
  if [ "$got" != "$expected" ]; then bad="${bad:+$bad$nl}expected $expected cells, found $got"; fi
  [ -n "$bad" ] || return 0
  printf '%s\n' "$bad"
  return 1
}
