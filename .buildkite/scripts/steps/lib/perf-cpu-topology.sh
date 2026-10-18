#!/usr/bin/env bash
# Shared CPU-topology helpers for the performance harness.
#
# WHY THIS EXISTS. The physical-core DISJOINTNESS proof used to live inline in
# perf-test-run.sh, guarding only the main SUT / upstream / k6 pinning. The
# multi-process client rig (mockserver-performance-test/scripts/multi-process-sweep.sh)
# pins its own SUT and N client blocks by LOGICAL cpu id, and at higher process
# counts a client block can cross onto the hyperthread SIBLINGS of the server's
# cores — silently putting the load generator inside the system under test and
# reporting the result as a SERVER ceiling. That is exactly the defect the main
# guard was written to stop ("the load generator has been sharing the server's
# physical cores all along") and the whole justification for the hardware resize.
# So the proof is factored HERE, ONE implementation, sourced by BOTH callers, and
# generalised over an arbitrary list of role/spec pairs — the two callers cannot
# drift, and there is no second copy of the topology parsing to rot.
#
# This file only DEFINES functions (no side effects), so it is safe to `source`
# under `set -euo pipefail`.

# Expand a cpuset spec ("0-5" / "8,9" / "6") into a space-separated list of logical
# CPU ids, so each can be mapped to the PHYSICAL core it actually sits on.
expand_cpuset() {
  local spec="$1" part a b i out="" _xparts
  [ -z "$spec" ] && { echo ""; return; }
  IFS=',' read -ra _xparts <<< "$spec"
  for part in "${_xparts[@]}"; do
    if [[ "$part" == *-* ]]; then
      a="${part%%-*}"; b="${part##*-}"
      for ((i=a; i<=b; i++)); do out="$out $i"; done
    else
      out="$out $part"
    fi
  done
  echo "${out# }"
}

# Map a logical CPU id to a stable PHYSICAL core key ("<package>:<core>"). Two
# hyperthread siblings share one key, which is the whole point: cpusets that look
# disjoint in logical numbering can be the two threads of the same core. It resolves
# REAL topology from sysfs rather than assuming an enumeration, so it holds whatever
# the sibling mapping turns out to be. PERF_SYSFS_CPU_ROOT exists so the guard itself
# can be tested against a SIMULATED topology; it defaults to the real sysfs path.
# PERF_SYSFS_ROOT moves the whole tree (cpu and node) for the NUMA fixture tests; the narrower
# PERF_SYSFS_CPU_ROOT still wins for the cpu half, so existing callers keep working.
perf_sysfs_cpu_root() { echo "${PERF_SYSFS_CPU_ROOT:-${PERF_SYSFS_ROOT:-/sys}/devices/system/cpu}"; }
perf_sysfs_node_root() { echo "${PERF_SYSFS_NODE_ROOT:-${PERF_SYSFS_ROOT:-/sys}/devices/system/node}"; }

phys_core_key() {
  local cpu="$1" base
  base="$(perf_sysfs_cpu_root)/cpu$1/topology"
  [ -r "$base/core_id" ] || return 1
  printf '%s:%s' "$(cat "$base/physical_package_id" 2>/dev/null || echo 0)" "$(cat "$base/core_id")"
}

# THE GUARD, generalised over an arbitrary list of role/spec PAIRS:
#   cpusets_physically_disjoint server "0-5" upstream "6" k6 "8-19"
#   cpusets_physically_disjoint server "0-3" client0 "4-5" client1 "6-7"
#
# Proves the given cpusets occupy DISJOINT physical cores (not merely disjoint
# logical ids — two hyperthread siblings are ONE core). Returns:
#   0  disjoint; OR topology unreadable OFF-CI (a limitation, not a fault — the
#      caller still runs, the numbers are just labelled unverifiable).
#   1  (FAIL THE RUN) two cpusets share a physical core; OR a non-empty spec expands
#      to nothing (a reversed/malformed range that would otherwise narrow the check
#      silently); OR the args are not role/spec pairs; OR topology unreadable IN CI
#      (BUILDKITE=true) — an unprovable benchmark is the thing this guard stops.
# An EMPTY spec is skipped (a role may legitimately be unpinned), never treated as
# "no overlap proven".
#
# Two historical defects of the original inline guard are deliberately avoided here,
# both of the class it targets (a guard that fails for the wrong reason, or passes
# because it examined nothing):
#   * NO `local -A`. Associative arrays need bash 4; macOS ships bash 3.2, where the
#     array form is a syntax ERROR — the guard would die instead of checking. The
#     seen-cores set is a newline-separated "<core key> <role>" table scanned with awk.
#   * A non-empty spec that expands to NOTHING is FATAL, not skipped — otherwise a
#     reversed range ("13-8") would quietly drop a role from the check while the rest
#     reported disjoint.
cpusets_physically_disjoint() {
  # Args must be role/spec PAIRS. An odd count means the caller dropped a spec; an
  # unpaired role would otherwise be shifted past and silently ignored — the very
  # silent-skip failure this guard exists to prevent. Fail closed on misuse.
  if [ $(( $# % 2 )) -ne 0 ]; then
    echo "^^^ +++"
    echo ":x: cpusets_physically_disjoint requires role/spec PAIRS, got $# argument(s) — refusing to verify against a malformed argument list" >&2
    return 1
  fi

  local seen="" role spec cpu key dupe="" count=0 prev roles="" pairs=""
  while [ "$#" -ge 2 ]; do
    role="$1"; spec="$2"; shift 2
    roles="${roles:+$roles / }$role"
    pairs="${pairs:+$pairs }$role=${spec:-<unpinned>}"
    [ -z "$spec" ] && continue
    local _expanded; _expanded="$(expand_cpuset "$spec")"
    if [ -z "$_expanded" ]; then
      echo "^^^ +++"
      echo ":x: the $role cpuset '$spec' expands to no cpus (a reversed or malformed range?) — refusing to verify core isolation against an empty set" >&2
      return 1
    fi
    for cpu in $_expanded; do
      if ! key="$(phys_core_key "$cpu")"; then
        # Topology unreadable (no /sys — e.g. a local macOS run). Off-CI that is a
        # limitation; in CI it means we cannot PROVE the numbers are honest, and an
        # unprovable benchmark is the thing this guard exists to stop.
        if [ "${BUILDKITE:-}" = "true" ]; then
          echo "^^^ +++"
          echo ":x: cannot read CPU topology for cpu${cpu}; refusing to produce benchmark figures that cannot be shown to be contention-free" >&2
          return 1
        fi
        echo "--- WARNING: CPU topology unreadable — cannot verify the cpusets are physically disjoint (expected off-CI)"
        return 0
      fi
      prev="$(printf '%s\n' "$seen" | awk -v k="$key" '$1==k {print $2; exit}')"
      # A role may own both hyperthreads of a core; only a core shared by two roles is contention.
      if [ "$prev" = "$role" ]; then
        continue
      elif [ -n "$prev" ]; then
        dupe="$dupe\n    physical core $key is used by BOTH $prev and $role (via cpu$cpu)"
      else
        seen="$seen$key $role"$'\n'
        count=$((count + 1))
      fi
    done
  done

  if [ -n "$dupe" ]; then
    echo "^^^ +++"
    echo ":x: the $roles cpusets OVERLAP on physical cores — the load generator would contend with the system under test, so any throughput figure from this run would measure the two of them fighting, not the server:" >&2
    printf '%b\n' "$dupe" >&2
    echo "    $pairs on $(nproc 2>/dev/null || echo '?') logical cpus" >&2
    echo "    Fix the cpusets or use a box with more physical cores." >&2
    return 1
  fi
  echo "--- verified: $roles occupy ${count} distinct physical cores, none shared"
  return 0
}

# Count the DISTINCT physical cores a cpuset spec occupies, resolved from real
# sysfs topology (two hyperthread siblings count ONCE). This is a REPORTING helper
# for perf-result.json so a published figure can state real cores rather than vCPUs
# — it never fails the run (cpusets_physically_disjoint above is the fail-closed
# guard). Echoes:
#   an integer  the number of distinct physical cores in the spec (0 for an empty spec);
#   "null"      topology could not be read for some cpu in the spec (e.g. off-CI on
#               macOS with no /sys) — the caller records null, not a guessed count.
# bash-3.2-safe: distinct keys are counted via sort -u, no `local -A`.
phys_core_count() {
  local spec="$1" cpu key keys=""
  [ -z "$spec" ] && { echo 0; return; }
  for cpu in $(expand_cpuset "$spec"); do
    if ! key="$(phys_core_key "$cpu")"; then echo "null"; return; fi
    keys="$keys$key"$'\n'
  done
  printf '%s' "$keys" | sort -u | sed '/^$/d' | wc -l | tr -d ' '
}

# --- NUMA placement (the two-socket perf-xl rig) ---------------------------------------------
# Disjoint physical cores are not enough on a two-socket host: a cpuset that straddles nodes
# splits one role across two LLCs and remote memory, and a k6 on the SUT's socket competes with
# it for memory bandwidth and LLC. Everything below reads the node map from sysfs
# (node*/cpulist, cpu*/topology/thread_siblings_list), never from cpu numbering.

# Compress cpu ids (space-separated, any order) into a cpuset spec: "7 8 9 10 31" -> "7-10,31".
compress_cpulist() {
  tr ' ' '\n' <<<"$1" | sed '/^$/d' | sort -n -u | awk '
    function emit() { out = out (out == "" ? "" : ",") (s == e ? s : s "-" e) }
    NR == 1 { s = $1; e = $1; next }
    $1 == e + 1 { e = $1; next }
    { emit(); s = $1; e = $1 }
    END { if (NR > 0) { emit(); print out } }'
}

# The node map, one "<cpu> <node> <package>" line per cpu, ascending by cpu (the package is
# physical_package_id, 0 where unreadable, as in phys_core_key). A kernel built without NUMA has
# no node directory: with readable cpu topology that host is one node, 0. Empty when nothing is
# readable (e.g. a macOS Docker Desktop run).
_numa_map_compute() {
  local root d nodes
  root="$(perf_sysfs_node_root)"
  set -- "$root"/node[0-9]*/cpulist
  if [ -e "$1" ]; then
    nodes="$(awk '{ n = FILENAME; sub(/\/cpulist$/, "", n); sub(/.*\/node/, "", n); gsub(/[[:space:]]/, "")
           k = split($0, part, ",")
           for (i = 1; i <= k; i++) { if (part[i] == "") continue
             m = split(part[i], r, "-"); b = (m > 1 ? r[2] : r[1])
             for (c = r[1] + 0; c <= b + 0; c++) print c, n } }' "$@" 2>/dev/null)"
  elif [ ! -d "$root" ] && phys_core_key 0 >/dev/null 2>&1; then
    nodes="$(for d in "$(perf_sysfs_cpu_root)"/cpu[0-9]*; do [ -r "$d/topology/core_id" ] && echo "${d##*/cpu} 0"; done)"
  fi
  [ -n "${nodes:-}" ] || return 0
  set -- "$(perf_sysfs_cpu_root)"/cpu[0-9]*/topology/physical_package_id
  { [ -e "$1" ] && awk '{ c = FILENAME; sub(/\/topology\/physical_package_id$/, "", c); sub(/.*\/cpu/, "", c); print "P", c, $1 }' "$@" 2>/dev/null
    printf '%s\n' "$nodes"; } \
    | awk '$1 == "P" { pkg[$2] = $3; next } NF == 2 { print $1, $2, (($1 in pkg) ? pkg[$1] : 0) }' | sort -n -k1,1
  return 0
}
_numa_map_key() { echo "${PERF_SYSFS_NODE_ROOT:-${PERF_SYSFS_ROOT:-/sys}/devices/system/node}|$(perf_sysfs_cpu_root)"; }
# The map is static for a run, so a caller primes it once; every later lookup (each in its own
# $(...) subshell) then reads the variable instead of sysfs. A different sysfs root recomputes.
numa_map_prime() { _NUMA_MAP="$(_numa_map_compute)"; _NUMA_MAP_KEY="$(_numa_map_key)"; }
numa_map() {
  if [ -n "${_NUMA_MAP_KEY:-}" ] && [ "$_NUMA_MAP_KEY" = "$(_numa_map_key)" ]; then
    [ -n "$_NUMA_MAP" ] && printf '%s\n' "$_NUMA_MAP"
  else
    _numa_map_compute
  fi
  return 0
}

# Node ids that own at least one cpu, ascending ("0 1"); empty when the map is unreadable.
numa_nodes() { numa_map | awk '{print $2}' | sort -n -u | paste -sd' ' -; }
numa_node_count() { local n; n="$(numa_nodes)"; wc -w <<<"$n" | tr -d ' '; }
numa_package_count() { numa_map | awk '{print $3}' | sort -u | sed '/^$/d' | wc -l | tr -d ' '; }

# The distinct packages (sockets) a cpuset spec or a node occupies, ascending.
cpuset_packages() { # spec
  local cpus; cpus="$(expand_cpuset "$1")"; [ -n "$cpus" ] || return 3
  numa_map | awk -v want="$cpus" 'BEGIN { k = split(want, w, " "); for (i = 1; i <= k; i++) on[w[i]] = 1 }
    ($1 in on) { print $3 }' | sort -n -u | paste -sd' ' -
}
numa_node_packages() { numa_map | awk -v n="$1" '$2 == n { print $3 }' | sort -n -u | paste -sd' ' -; } # node

# A node's cpus, ascending; returns 1 for a node with none.
numa_node_cpus() {
  local out
  out="$(numa_map | awk -v n="$1" '$2 == n {print $1}' | paste -sd' ' -)"
  [ -n "$out" ] || return 1
  echo "$out"
}

# The distinct nodes a cpuset spec occupies, ascending ("0" / "0 1"). Returns 1 when the node
# map is unreadable, 2 when a cpu is in no node (offline or absent), 3 when the spec is empty.
cpuset_numa_nodes() {
  local cpus map out
  cpus="$(expand_cpuset "$1")"; [ -n "$cpus" ] || return 3
  map="$(numa_map)"; [ -n "$map" ] || return 1
  out="$(awk -v want="$cpus" '
    BEGIN { k = split(want, w, " ") }
    { node[$1] = $2 }
    END { for (i = 1; i <= k; i++) { if (!(w[i] in node)) { print "MISSING"; exit } seen[node[w[i]]] = 1 }
          for (x in seen) print x }' <<<"$map" | sort -n | paste -sd' ' -)"
  case "$out" in *MISSING*) return 2 ;; esac
  echo "$out"
}

# Unprovable socket placement: a limitation off-CI (warn, 0), a refused run in CI (1).
_numa_unproven() { # reason
  if [ "${BUILDKITE:-}" = "true" ]; then
    echo "^^^ +++"
    echo ":x: $1; refusing to produce benchmark figures whose socket placement cannot be shown" >&2
    return 1
  fi
  echo "--- WARNING: $1 (expected off-CI)"
  return 0
}
_numa_unknown() { _numa_unproven "the NUMA node map is unreadable, so cannot verify $1"; } # what

# GUARD: one role's cpuset sits on ONE node. An empty (unpinned) spec passes only on a host
# with at most one node. Fails closed on a straddle, an offline cpu or a malformed spec.
assert_cpuset_single_node() { # role spec
  local role="$1" spec="$2" nodes rc=0
  if [ -z "$spec" ]; then
    if [ "$(numa_node_count)" -gt 1 ]; then
      echo "^^^ +++"
      echo ":x: the $role is unpinned on a $(numa_node_count)-node host, so it would float across NUMA nodes" >&2
      return 1
    fi
    return 0
  fi
  nodes="$(cpuset_numa_nodes "$spec")" || rc=$?
  case "$rc" in
    0) ;;
    1) _numa_unknown "that the $role cpuset $spec is on one NUMA node"; return ;;
    2) echo ":x: the $role cpuset '$spec' names a cpu that is in no NUMA node (offline or absent)" >&2; return 1 ;;
    *) echo ":x: the $role cpuset '$spec' expands to no cpus (a reversed or malformed range?)" >&2; return 1 ;;
  esac
  if [ "$(wc -w <<<"$nodes" | tr -d ' ')" -ne 1 ]; then
    echo "^^^ +++"
    echo ":x: the $role cpuset '$spec' straddles NUMA nodes ${nodes// /, }: its threads would split across sockets (two LLCs, remote memory), so it would not measure one $role" >&2
    return 1
  fi
  echo "--- verified: $role cpuset $spec is on NUMA node $nodes"
}

# GUARD: two cpusets share no NUMA node (k6 on the other socket from the SUT).
assert_nodes_disjoint() { # role_a spec_a role_b spec_b
  if [ "$#" -ne 4 ]; then
    echo ":x: assert_nodes_disjoint needs role/spec, role/spec (4 arguments), got $#" >&2; return 1
  fi
  local a="" b="" rc=0 n shared=""
  a="$(cpuset_numa_nodes "$2")" || rc=$?
  if [ "$rc" -eq 0 ]; then b="$(cpuset_numa_nodes "$4")" || rc=$?; fi
  case "$rc" in
    0) ;;
    1) _numa_unknown "that $1 and $3 are on different NUMA nodes"; return ;;
    2) echo ":x: the $1 or $3 cpuset names a cpu that is in no NUMA node (offline or absent)" >&2; return 1 ;;
    *) echo ":x: the $1 or $3 cpuset expands to no cpus (an empty, reversed or malformed spec?)" >&2; return 1 ;;
  esac
  for n in $a; do case " $b " in *" $n "*) shared="$shared $n" ;; esac; done
  if [ -n "$shared" ]; then
    echo "^^^ +++"
    echo ":x: $1 ($2, node ${a// /, }) and $3 ($4, node ${b// /, }) share NUMA node${shared}: they would contend for one socket's memory bandwidth and LLC" >&2
    return 1
  fi
  echo "--- verified: $1 on NUMA node ${a// /, }, $3 on node ${b// /, }"
}

# A node's physical cores in ascending order of their lowest cpu, one per line:
# "<first cpu> <its hyperthread siblings on this node...>". Siblings come from
# thread_siblings_list, or from the core keys above where that is unreadable.
numa_node_phys_cores() { # node
  local cpus c root files=() pairs=""
  cpus="$(numa_node_cpus "$1")" || return 1
  root="$(perf_sysfs_cpu_root)"
  for c in $cpus; do files+=("$root/cpu$c/topology/thread_siblings_list"); done
  if [ -r "${files[0]}" ] && awk 'END{}' "${files[@]}" 2>/dev/null; then
    pairs="$(awk '{ c = FILENAME; sub(/\/topology\/thread_siblings_list$/, "", c); sub(/.*\/cpu/, "", c)
                    gsub(/[[:space:]]/, ""); print c, $0 }' "${files[@]}")"
  else
    for c in $cpus; do pairs="$pairs$c $(phys_core_key "$c" || echo "?$c")"$'\n'; done
    pairs="$(awk '{ key[NR] = $2; cpu[NR] = $1 } END { for (i = 1; i <= NR; i++) { l = ""
               for (j = 1; j <= NR; j++) if (key[j] == key[i]) l = l (l == "" ? "" : ",") cpu[j]
               print cpu[i], l } }' <<<"$pairs")"
  fi
  awk -v cpus="$cpus" '
    BEGIN { k = split(cpus, order, " "); for (i = 1; i <= k; i++) on[order[i]] = 1 }
    { sib[$1] = $2 }
    END { for (i = 1; i <= k; i++) { c = order[i]; if (c in seen) continue
            line = c; seen[c] = 1; m = split(sib[c], part, ",")
            for (j = 1; j <= m; j++) { q = split(part[j], r, "-"); b = (q > 1 ? r[2] : r[1])
              for (s = r[1] + 0; s <= b + 0; s++) if ((s in on) && !(s in seen)) { line = line " " s; seen[s] = 1 } }
            print line } }' <<<"$pairs"
}

# k6's node: the first node on a socket <node> is not on. Sub-NUMA clustering splits one socket
# into several nodes, so the next node id can be the same socket. Returns 1 when there is none.
numa_other_socket_node() { # node
  local n v p0
  p0=" $(numa_node_packages "$1") "
  for n in $(numa_nodes); do
    [ "$n" = "$1" ] && continue
    for v in $(numa_node_packages "$n"); do case "$p0" in *" $v "*) continue 2 ;; esac; done
    echo "$n"; return 0
  done
  return 1
}

# The two-socket layout of the multi-k6 arm and the hardware matrix, from the node map:
#   server      the first <sut_cores> physical cores of the first node, one thread each
#   upstream    the next core, one thread;  prometheus  the core after it, both threads
#   k6          <procs> groups of <per> physical cores, both threads: on the second node (mode
#               other), or on what the first node has left (mode same, the interference A/B)
# <per> defaults to the k6 node's free cores / procs. Prints key=value lines (server, upstream,
# prometheus, k6 (';'-joined), sut_node, k6_node, per_proc). Returns 1 on a host with fewer
# than two nodes (the caller keeps its single-node layout) and 2 when the layout cannot fit.
numa_split_layout() { # sut_cores procs mode [per]
  local sut_cores="$1" procs="$2" mode="$3" per="${4:-}" nodes n0 n1 lines k6lines k6node
  local total0 need0 avail i g v server up prom k6=""
  nodes="$(numa_nodes)"
  [ "$(wc -w <<<"$nodes" | tr -d ' ')" -ge 2 ] || return 1
  for v in "$sut_cores" "$procs" ${per:+"$per"}; do
    [[ "$v" =~ ^[1-9][0-9]*$ ]] || { echo ":x: numa_split_layout: '$v' is not a positive whole number" >&2; return 2; }
  done
  n0="$(awk '{print $1}' <<<"$nodes")"; n1="$(numa_other_socket_node "$n0")" || n1=""
  lines="$(numa_node_phys_cores "$n0")" || { echo ":x: cannot read the physical cores of NUMA node $n0" >&2; return 2; }
  total0="$(sed '/^$/d' <<<"$lines" | wc -l | tr -d ' ')"; need0=$(( sut_cores + 2 ))
  if [ "$total0" -lt "$need0" ]; then
    echo ":x: NUMA node $n0 has $total0 physical cores; the SUT ($sut_cores), upstream (1) and Prometheus (1) need $need0" >&2; return 2
  fi
  server="$(compress_cpulist "$(head -n "$sut_cores" <<<"$lines" | awk '{print $1}' | paste -sd' ' -)")"
  up="$(sed -n "$(( sut_cores + 1 ))p" <<<"$lines" | awk '{print $1}')"
  prom="$(compress_cpulist "$(sed -n "$(( sut_cores + 2 ))p" <<<"$lines")")"
  case "$mode" in
    other)
      [ -n "$n1" ] || { echo ":x: no NUMA node is on another socket from node $n0 (one socket split into nodes?)" >&2; return 2; }
      k6node="$n1"; k6lines="$(numa_node_phys_cores "$n1")" || { echo ":x: cannot read the physical cores of NUMA node $n1" >&2; return 2; } ;;
    same)  k6node="$n0"; k6lines="$(tail -n +"$(( need0 + 1 ))" <<<"$lines")" ;;
    *) echo ":x: k6 NUMA node mode '$mode' must be other or same" >&2; return 2 ;;
  esac
  avail="$(sed '/^$/d' <<<"$k6lines" | wc -l | tr -d ' ')"
  [ -n "$per" ] || per=$(( avail / procs ))
  if [ "$per" -lt 1 ] || [ $(( per * procs )) -gt "$avail" ]; then
    echo ":x: NUMA node $k6node has $avail free physical cores; $procs k6 process(es) x ${per} core(s) do not fit" >&2; return 2
  fi
  for ((i = 0; i < procs; i++)); do
    g="$(sed -n "$(( i * per + 1 )),$(( (i + 1) * per ))p" <<<"$k6lines" | paste -sd' ' -)"
    k6="${k6:+$k6;}$(compress_cpulist "$g")"
  done
  printf 'server=%s\nupstream=%s\nprometheus=%s\nk6=%s\nsut_node=%s\nk6_node=%s\nper_proc=%s\n' \
    "$server" "$up" "$prom" "$k6" "$n0" "$k6node" "$per"
}
layout_value() { sed -n "s/^$1=//p" <<<"$2"; } # key layout_text

# perf-test-run.sh's single-process k6 on a host with two or more nodes: the first <count> physical
# cores, one thread each, of the node on another socket from the SUT's (mode other), or of the SUT's
# node minus every core the SUT or upstream touches (mode same). Prints the cpuset. Returns 1 on a
# host with fewer than two nodes (the caller keeps its fixed default), 2 when it cannot be placed.
numa_single_k6_cpus() { # mode sut_spec upstream_spec count
  local mode="$1" sut="$2" up="$3" count="$4" sn node firsts avail
  [ "$(numa_node_count)" -ge 2 ] || return 1
  [[ "$count" =~ ^[1-9][0-9]*$ ]] || { echo ":x: numa_single_k6_cpus: '$count' is not a positive whole number" >&2; return 2; }
  sn="$(cpuset_numa_nodes "$sut" 2>/dev/null)" || sn=""
  case "$sn" in ""|*" "*) echo ":x: the SUT cpuset '$sut' is not on one NUMA node, so k6's node cannot be chosen" >&2; return 2 ;; esac
  case "$mode" in
    other) node="$(numa_other_socket_node "$sn")" \
             || { echo ":x: no NUMA node is on another socket from the SUT's node $sn (one socket split into nodes?)" >&2; return 2; } ;;
    same) node="$sn" ;;
    *) echo ":x: k6 NUMA node mode '$mode' must be other or same" >&2; return 2 ;;
  esac
  firsts="$(numa_node_phys_cores "$node" | awk -v used="$(expand_cpuset "$sut${up:+,$up}")" '
    BEGIN { n = split(used, u, " "); for (i = 1; i <= n; i++) x[u[i]] = 1 }
    { for (i = 1; i <= NF; i++) if ($i in x) next; print $1 }')"
  avail="$(sed '/^$/d' <<<"$firsts" | wc -l | tr -d ' ')"
  if [ "$avail" -lt "$count" ]; then
    echo ":x: NUMA node $node has $avail free physical cores; the single-process k6 needs $count" >&2; return 2
  fi
  compress_cpulist "$(head -n "$count" <<<"$firsts" | paste -sd' ' -)"
}

# "--cpuset-mems=<node>" for a cpuset on one known node; nothing when that cannot be shown.
numa_mems_flag() { # spec
  local n
  n="$(cpuset_numa_nodes "$1" 2>/dev/null)" || return 0
  case "$n" in ""|*" "*) return 0 ;; esac
  printf -- '--cpuset-mems=%s' "$n"
}

# GUARD for one arm: the SUT and every k6 cpuset each on one node, and k6 on a node the SUT
# is not on whenever the host has two (mode other) — or on the SUT's node (mode same).
numa_placement_check() { # mode sut_spec k6_spec...
  local mode="$1" sut="$2" k i=0 all="" sn kn v
  shift 2
  case "$mode" in other|same) ;; *) echo ":x: PERF_K6_NUMA_NODE='$mode' must be other or same" >&2; return 1 ;; esac
  [ "$#" -ge 1 ] || { echo ":x: numa_placement_check needs at least one k6 cpuset" >&2; return 1; }
  if [ -z "$(numa_map)" ]; then _numa_unknown "the SUT and k6 NUMA placement"; return; fi
  # More sockets than nodes (numa=off, a NUMA-less kernel): the node map cannot show the split.
  if [ "$(numa_package_count)" -gt "$(numa_node_count)" ]; then
    _numa_unproven "the host has $(numa_package_count) sockets but $(numa_node_count) NUMA node(s), so the node map cannot show which socket k6 is on"
    return
  fi
  assert_cpuset_single_node server "$sut" || return 1
  for k in "$@"; do
    assert_cpuset_single_node "k6_$i" "$k" || return 1
    all="${all:+$all,}$k"; i=$(( i + 1 ))
  done
  [ "$(numa_node_count)" -ge 2 ] || return 0
  if [ "$mode" = other ]; then
    if ! assert_nodes_disjoint server "$sut" k6 "$all"; then
      echo "    k6 must run on another NUMA node from the SUT on a multi-node host; PERF_K6_NUMA_NODE=same is the explicit same-socket A/B" >&2
      return 1
    fi
    sn=" $(cpuset_packages "$sut") "; kn="$(cpuset_packages "$all")"
    for v in $kn; do
      case "$sn" in *" $v "*)
        echo "^^^ +++"
        echo ":x: k6 ($all) is on another NUMA node but the same socket ($v) as the SUT ($sut): sub-NUMA clustering splits a socket, so k6 would still share its memory controller and LLC" >&2
        return 1 ;;
      esac
    done
  else
    sn="$(cpuset_numa_nodes "$sut")"; kn="$(cpuset_numa_nodes "$all")"
    if [ "$sn" != "$kn" ]; then
      echo "^^^ +++"
      echo ":x: PERF_K6_NUMA_NODE=same but k6 is on NUMA node ${kn// /, } and the SUT on ${sn// /, }" >&2
      return 1
    fi
    echo "--- verified: k6 on the SUT's NUMA node $sn (PERF_K6_NUMA_NODE=same: an interference A/B, never a baseline run)"
  fi
}

# The result's placement record: {numa_nodes, sut_node, k6_node, k6_nodes, layout, k6_numa_node,
# baseline_eligible}. numa_nodes is null when the node map is unreadable.
numa_placement_json() { # layout mode sut_spec k6_spec...
  local layout="$1" mode="$2" sut="$3" k all="" sn kn
  shift 3
  for k in "$@"; do all="${all:+$all,}$k"; done
  sn="$(cpuset_numa_nodes "$sut" 2>/dev/null)" || sn=""
  kn="$(cpuset_numa_nodes "$all" 2>/dev/null)" || kn=""
  jq -nc --arg layout "$layout" --arg mode "$mode" --arg count "$(numa_node_count)" --arg sn "$sn" --arg kn "$kn" '
    def ids: if . == "" then [] else split(" ") | map(tonumber) end;
    ($sn | ids) as $s | ($kn | ids) as $k
    | {numa_nodes:(if $count == "0" then null else ($count | tonumber) end),
       sut_node:(if ($s | length) == 1 then $s[0] else null end),
       k6_node:(if ($k | length) == 1 then $k[0] else null end),
       k6_nodes:$k, layout:$layout, k6_numa_node:$mode,
       baseline_eligible:($mode != "same")}'
}

# --- observed placement: what the kernel and Docker actually gave each run -------------------------
# The raw node*/cpulist files, as one line and as a JSON object ({"node0":"0-31,64-95",...}; {} when
# none is readable). The layout is derived from these, so the log keeps the input beside the output.
numa_node_cpulists_json() {
  local f n out="{}"
  for f in "$(perf_sysfs_node_root)"/node[0-9]*/cpulist; do
    [ -r "$f" ] || continue
    n="${f%/cpulist}"; n="${n##*/}"
    out="$(jq -c --arg n "$n" --arg v "$(tr -d '[:space:]' < "$f")" '. + {($n): $v}' <<<"$out")"
  done
  echo "$out"
}
numa_log_node_cpulists() {
  echo "--- NUMA node cpulists (raw $(perf_sysfs_node_root)/node*/cpulist): $(numa_node_cpulists_json \
    | jq -r 'if length == 0 then "unreadable" else to_entries | map("\(.key)=\(.value)") | join(" ") end')"
}

# The cpuset Docker applied to a container, as {role, container, cpuset_cpus, cpuset_mems}: "" is
# unrestricted (Docker's own empty value), null means the container could not be inspected.
container_cpuset_json() { # role container
  local got cpus="" mems="" ok=false
  if got="$(docker inspect -f '{{.HostConfig.CpusetCpus}} {{.HostConfig.CpusetMems}}' "$2" 2>/dev/null)" \
     && [[ "$got" == *" "* ]] && [[ "$got" != *$'\n'* ]]; then
    cpus="${got%% *}"; mems="${got#* }"; ok=true
  fi
  jq -nc --arg role "$1" --arg c "$2" --arg cpus "$cpus" --arg mems "$mems" --argjson ok "$ok" \
    '{role:$role, container:$c, cpuset_cpus:(if $ok then $cpus else null end), cpuset_mems:(if $ok then $mems else null end)}'
}
# Logs that line to stderr and appends the JSON to <ndjson_file> when given. Never fails the caller.
log_container_cpuset() { # role container [ndjson_file]
  local j
  j="$(container_cpuset_json "$1" "$2" 2>/dev/null)" || return 0
  echo "--- observed placement: $1 $2 $(jq -r 'def v: if . == null then "<inspect failed>" elif . == "" then "<unrestricted>" else . end;
    "cpuset_cpus=\(.cpuset_cpus | v) cpuset_mems=\(.cpuset_mems | v)"' <<<"$j" 2>/dev/null)" >&2
  if [ -n "${3:-}" ]; then printf '%s\n' "$j" >> "$3" 2>/dev/null || true; fi
  return 0
}
