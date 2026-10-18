#!/usr/bin/env bash
# Default k6 runtime for scripts/rw-multi-k6-sweep.sh: each rung's
# gracefulStop and the Go GC limit of every measured k6 process. Defines functions only.
# Rationale: docs/code/performance-measurement.md, "k6 heap and GC".
# Tests: .buildkite/scripts/test/perf-k6-runtime-test.sh

# Percent of the memory the k6 processes can reach (their NUMA node's, else the Docker host's) that
# they may use between them. The rest is headroom for the SUT, Prometheus, the upstream, Docker and
# the OS (page cache included).
K6_GOMEMLIMIT_HOST_PCT=50

# k6_graceful_stop_default <gap_seconds>: the gap in whole seconds, so never above it, and at least
# 1 s (sweep.js's floor). At or below the gap, adjacent rungs' VU reservations do not overlap.
k6_graceful_stop_default() {
  local g="${1:-}"
  [[ "$g" =~ ^[0-9]+$ ]] || return 1
  [ "$g" -ge 1 ] || g=1
  echo "${g}s"
}

# k6_gomemlimit_default <docker_mem_bytes> <procs>: K6_GOMEMLIMIT_HOST_PCT of the Docker host's
# memory split evenly over the k6 processes that run at once, in whole MiB (rounded down).
# Fails (no output) on a memory size or process count that is not a positive whole number.
k6_gomemlimit_default() {
  local mem="${1:-}" n="${2:-}" mib
  [[ "$mem" =~ ^[1-9][0-9]{0,15}$ ]] && [[ "$n" =~ ^[1-9][0-9]{0,3}$ ]] || return 1
  mib=$(( mem * K6_GOMEMLIMIT_HOST_PCT / 100 / n / 1048576 ))
  [ "$mib" -ge 1 ] || return 1
  echo "${mib}MiB"
}

# k6_docker_mem_bytes: the memory of the host the k6 containers run on, as Docker sees it. Not the
# local machine's: under Docker Desktop that is a smaller VM, which is the limit that matters.
k6_docker_mem_bytes() {
  docker info --format '{{.MemTotal}}' 2>/dev/null | tr -d '[:space:]'
}

# k6_numa_node_mem_bytes <node>: MemTotal of one NUMA node from sysfs node<N>/meminfo, in bytes.
# Honours PERF_SYSFS_NODE_ROOT / PERF_SYSFS_ROOT like lib/perf-cpu-topology.sh. Fails when unreadable.
k6_numa_node_mem_bytes() {
  local root="${PERF_SYSFS_NODE_ROOT:-${PERF_SYSFS_ROOT:-/sys}/devices/system/node}" kb
  [[ "${1:-}" =~ ^[0-9]{1,4}$ ]] || return 1
  kb="$(awk -v n="$1" '$1 == "Node" && $2 == n && $3 == "MemTotal:" && $5 == "kB" { print $4; exit }' "$root/node$1/meminfo" 2>/dev/null)"
  [[ "$kb" =~ ^[1-9][0-9]{0,12}$ ]] || return 1
  echo $(( kb * 1024 ))
}

# k6_gomemlimit_resolve <docker_mem_bytes> <cpuset>...: the derived GOMEMLIMIT for one k6 process per
# cpuset, as {"limit":"<n>MiB","basis":{...}}. A k6 container bound to one NUMA node (--cpuset-mems)
# can only use that node's memory, so where every cpuset sits on one node with a readable meminfo the
# limit is K6_GOMEMLIMIT_HOST_PCT of each node over the processes on it, taking the smallest; it never
# exceeds the host-wide share. Otherwise it is the host-wide share (k6_gomemlimit_default). Needs
# cpuset_numa_nodes (lib/perf-cpu-topology.sh) for the node basis; fails when no basis is derivable.
k6_gomemlimit_resolve() {
  local mem="${1:-}" n host="" host_mib="" reason="" nodes="" node nmem mib best="" spec count bound entries="[]"
  shift || return 1
  n="$#"
  [ "$n" -ge 1 ] || return 1
  host="$(k6_gomemlimit_default "$mem" "$n")" && host_mib="${host%MiB}"
  if ! declare -F cpuset_numa_nodes >/dev/null; then
    reason="the NUMA node lookup is not loaded"
  else
    for spec in "$@"; do
      node="$(cpuset_numa_nodes "$spec" 2>/dev/null)" || { reason="cpuset $spec has no known NUMA node"; nodes=""; break; }
      case "$node" in *" "*) reason="cpuset $spec spans NUMA nodes $node"; nodes=""; break ;; esac
      nodes="$nodes $node"
    done
  fi
  if [ -n "$nodes" ]; then
    for node in $(tr ' ' '\n' <<<"$nodes" | sed '/^$/d' | sort -n -u); do
      nmem="$(k6_numa_node_mem_bytes "$node")" || { reason="node$node/meminfo is not readable"; entries="[]"; best=""; break; }
      count="$(tr ' ' '\n' <<<"$nodes" | grep -cx "$node")"
      mib=$(( nmem * K6_GOMEMLIMIT_HOST_PCT / 100 / count / 1048576 ))
      entries="$(jq -c --argjson node "$node" --argjson m "$nmem" --argjson c "$count" --argjson mib "$mib" \
        '. + [{node:$node, mem_total_bytes:$m, procs:$c, per_proc_mib:$mib}]' <<<"$entries")"
      if [ -z "$best" ] || [ "$mib" -lt "$best" ]; then best="$mib"; fi
    done
  fi
  if [ -n "$best" ] && [ "$best" -ge 1 ]; then
    bound=numa_node
    if [ -n "$host_mib" ] && [ "$host_mib" -lt "$best" ]; then best="$host_mib"; bound=docker_host; fi
    jq -nc --arg l "${best}MiB" --argjson e "$entries" --arg mem "$mem" --argjson n "$n" --argjson pct "$K6_GOMEMLIMIT_HOST_PCT" --arg b "$bound" '
      {limit:$l, basis:{memory_basis:"numa_node", bound_by:$b, numa_nodes:$e,
                        docker_mem_total_bytes:($mem | tonumber? // null), procs:$n, host_pct:$pct}}'
    return 0
  fi
  [ -n "$host" ] || return 1
  jq -nc --arg l "$host" --argjson m "$mem" --argjson n "$n" --argjson pct "$K6_GOMEMLIMIT_HOST_PCT" --arg r "$reason" '
    {limit:$l, basis:{memory_basis:"docker_host", numa_fallback_reason:(if $r == "" then null else $r end),
                      docker_mem_total_bytes:$m, procs:$n, host_pct:$pct}}'
}
