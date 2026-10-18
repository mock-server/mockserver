#!/usr/bin/env bash
# Fixture tests for the NUMA placement in lib/perf-cpu-topology.sh and its three users
# (scripts/rw-multi-k6-sweep.sh, lib/perf-percore.sh's hardware matrix and perf-test-run.sh's main
# SUT, upstream and k6), driven against fake sysfs trees through PERF_SYSFS_ROOT. Docker is a stub,
# so nothing is started.
# Run: .buildkite/scripts/test/perf-cpu-topology-test.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env PERF_TOPO_HARNESS PERF_TOPO_PERCORE PERF_TOPO_RUN
# shellcheck source=../steps/lib/perf-cpu-topology.sh
. "$REPO_ROOT/.buildkite/scripts/steps/lib/perf-cpu-topology.sh"
HARNESS="${PERF_TOPO_HARNESS:-$REPO_ROOT/mockserver-performance-test/scripts/rw-multi-k6-sweep.sh}"
PERCORE="${PERF_TOPO_PERCORE:-$REPO_ROOT/.buildkite/scripts/steps/lib/perf-percore.sh}"
RUN="${PERF_TOPO_RUN:-$REPO_ROOT/.buildkite/scripts/steps/perf-test-run.sh}"
T="$(mktemp -d "${TMPDIR:-/tmp}/perf-topo-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
unset BUILDKITE PERF_SYSFS_CPU_ROOT PERF_SYSFS_NODE_ROOT PERF_K6_NUMA_NODE

# make_cpu <root> <cpu> <package> <core_id> <thread_siblings_list>
make_cpu() {
  local d="$1/devices/system/cpu/cpu$2/topology"
  mkdir -p "$d"
  echo "$3" > "$d/physical_package_id"; echo "$4" > "$d/core_id"; echo "$5" > "$d/thread_siblings_list"
}
make_node() { mkdir -p "$1/devices/system/node/node$2"; echo "$3" > "$1/devices/system/node/node$2/cpulist"; }

# c5.12xlarge: one socket, 24 cores, 48 threads, siblings N and N+24, one node.
C5="$T/c5"
for c in $(seq 0 47); do b=$(( c % 24 )); make_cpu "$C5" "$c" 0 "$b" "$b,$(( b + 24 ))"; done
make_node "$C5" 0 "0-47"
# c6i.32xlarge: two sockets x 32 cores, 128 threads. The kernel enumerates every socket's first
# threads before any sibling, as AWS documents for the two-socket c5.24xlarge (node0 0-23,48-71;
# node1 24-47,72-95), so node0 is 0-31,64-95, node1 32-63,96-127 and siblings are N and N+64.
# core_id restarts per package, so the package must disambiguate physical cores.
C6I="$T/c6i"
for c in $(seq 0 127); do
  b=$(( c % 64 )); make_cpu "$C6I" "$c" $(( b / 32 )) $(( b % 32 )) "$b,$(( b + 64 ))"
done
make_node "$C6I" 0 "0-31,64-95"; make_node "$C6I" 1 "32-63,96-127"
# Another two-node numbering (siblings adjacent, nodes contiguous): the layout must follow the
# node map, not assume c6i's.
ALT="$T/alt"
for c in $(seq 0 63); do b=$(( c - c % 2 )); make_cpu "$ALT" "$c" $(( c / 32 )) $(( (c % 32) / 2 )) "$b-$(( b + 1 ))"; done
make_node "$ALT" 0 "0-31"; make_node "$ALT" 1 "32-63"
# Node 0 owns the HIGH cpus: a layout that took "the lowest cpus" instead of node 0's would show.
SWAP="$T/swap"
for c in $(seq 0 15); do make_cpu "$SWAP" "$c" $(( c < 8 ? 1 : 0 )) "$c" "$c"; done
make_node "$SWAP" 0 "8-15"; make_node "$SWAP" 1 "0-7"
# Three nodes: a k6 straddling nodes 1 and 2 shares no node with the SUT, so only the per-k6
# single-node check stops it.
TRI="$T/tri"
for c in $(seq 0 11); do make_cpu "$TRI" "$c" $(( c / 4 )) "$c" "$c"; done
make_node "$TRI" 0 "0-3"; make_node "$TRI" 1 "4-7"; make_node "$TRI" 2 "8-11"
# The c6i booted with numa=off: two sockets, one node. The node map cannot show the split.
NUMAOFF="$T/numaoff"
for c in $(seq 0 127); do
  b=$(( c % 64 )); make_cpu "$NUMAOFF" "$c" $(( b / 32 )) $(( b % 32 )) "$b,$(( b + 64 ))"
done
make_node "$NUMAOFF" 0 "0-127"
# Sub-NUMA clustering: two sockets of two nodes each. Node 1 is the SUT's socket, node 2 the other.
SNC="$T/snc"
for c in $(seq 0 15); do make_cpu "$SNC" "$c" $(( c / 8 )) "$c" "$c"; done
make_node "$SNC" 0 "0-3"; make_node "$SNC" 1 "4-7"; make_node "$SNC" 2 "8-11"; make_node "$SNC" 3 "12-15"
# One socket split into two nodes: no other socket to put k6 on.
SNC1="$T/snc1"
for c in $(seq 0 7); do make_cpu "$SNC1" "$c" 0 "$c" "$c"; done
make_node "$SNC1" 0 "0-3"; make_node "$SNC1" 1 "4-7"
# A kernel without NUMA: cpu topology but no node directory.
NONUMA="$T/nonuma"
for c in $(seq 0 7); do b=$(( c % 4 )); make_cpu "$NONUMA" "$c" 0 "$b" "$b,$(( b + 4 ))"; done

echo "--- 1. node map from sysfs"
check "compress_cpulist" "7-10,31-34" "$(compress_cpulist "34 7 8 31 9 10 32 33")"
check "compress_cpulist single + pair" "7,71" "$(compress_cpulist "71 7")"
check "c5: one node" "0" "$(PERF_SYSFS_ROOT=$C5 numa_nodes)"
check "c6i: two nodes" "0 1" "$(PERF_SYSFS_ROOT=$C6I numa_nodes)"
check "c6i: node count" "2" "$(PERF_SYSFS_ROOT=$C6I numa_node_count)"
check "c6i: 0-5 on node 0" "0" "$(PERF_SYSFS_ROOT=$C6I cpuset_numa_nodes 0-5)"
check "c6i: siblings 64-69 on node 0" "0" "$(PERF_SYSFS_ROOT=$C6I cpuset_numa_nodes 64-69)"
check "c6i: 32-39,96-103 on node 1" "1" "$(PERF_SYSFS_ROOT=$C6I cpuset_numa_nodes 32-39,96-103)"
check "c6i: 30-33 straddles" "0 1" "$(PERF_SYSFS_ROOT=$C6I cpuset_numa_nodes 30-33)"
check "c6i: an absent cpu is rc 2" "2" "$(PERF_SYSFS_ROOT=$C6I cpuset_numa_nodes 200 >/dev/null; echo $?)"
check "unreadable map is rc 1" "1" "$(PERF_SYSFS_ROOT=$T/none cpuset_numa_nodes 0 >/dev/null; echo $?)"
check "no-NUMA kernel: one node 0" "0" "$(PERF_SYSFS_ROOT=$NONUMA numa_nodes)"
check "no-NUMA kernel: 0-7 on node 0" "0" "$(PERF_SYSFS_ROOT=$NONUMA cpuset_numa_nodes 0-7)"
check "c6i: node 1 physical core 0 + sibling" "32 96" "$(PERF_SYSFS_ROOT=$C6I numa_node_phys_cores 1 | head -1)"
check "c6i: node 0 has 32 physical cores" "32" "$(PERF_SYSFS_ROOT=$C6I numa_node_phys_cores 0 | wc -l | tr -d ' ')"
check "mems flag on node 1" "--cpuset-mems=1" "$(PERF_SYSFS_ROOT=$C6I numa_mems_flag 32-35)"
check "mems flag on no-NUMA kernel" "--cpuset-mems=0" "$(PERF_SYSFS_ROOT=$NONUMA numa_mems_flag 0-3)"
check "no mems flag for a straddle" "" "$(PERF_SYSFS_ROOT=$C6I numa_mems_flag 30-33)"
check "no mems flag when unreadable" "" "$(PERF_SYSFS_ROOT=$T/none numa_mems_flag 0-3)"
check "physical disjointness reads PERF_SYSFS_ROOT (c6i 0 and 64 are one core)" "1" \
  "$(PERF_SYSFS_ROOT=$C6I cpusets_physically_disjoint a 0 b 64 >/dev/null 2>&1; echo $?)"
check "physical disjointness: c6i package disambiguates core_id 0 (cpus 0 and 32)" "0" \
  "$(PERF_SYSFS_ROOT=$C6I cpusets_physically_disjoint a 0 b 32 >/dev/null 2>&1; echo $?)"

echo "--- 2. the guards"
rc() { "$@" >/dev/null 2>&1 && echo 0 || echo 1; }
check "single node: c6i SUT 0-5" "0" "$(PERF_SYSFS_ROOT=$C6I rc assert_cpuset_single_node server 0-5)"
check "single node: c6i straddle 30-33 FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc assert_cpuset_single_node server 30-33)"
check "single node: c6i 0-5,96 FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc assert_cpuset_single_node server 0-5,96)"
check "single node: absent cpu FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc assert_cpuset_single_node server 0,200)"
check "single node: reversed range FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc assert_cpuset_single_node server 9-3)"
check "single node: unpinned on 2 nodes FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc assert_cpuset_single_node server "")"
check "single node: unpinned on 1 node ok" "0" "$(PERF_SYSFS_ROOT=$C5 rc assert_cpuset_single_node server "")"
check "single node: c5 0-5 ok" "0" "$(PERF_SYSFS_ROOT=$C5 rc assert_cpuset_single_node server 0-5)"
check "single node: unreadable off-CI warns, passes" "0" "$(PERF_SYSFS_ROOT=$T/none rc assert_cpuset_single_node server 0-5)"
check "single node: unreadable in CI FAILS" "1" "$(BUILDKITE=true PERF_SYSFS_ROOT=$T/none rc assert_cpuset_single_node server 0-5)"
check "disjoint: c6i 0-5 vs node 1" "0" "$(PERF_SYSFS_ROOT=$C6I rc assert_nodes_disjoint server 0-5 k6 32-63)"
check "disjoint: c6i 0-5 vs 8-11 FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc assert_nodes_disjoint server 0-5 k6 8-11)"
check "disjoint: c6i 0-5 vs a straddle FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc assert_nodes_disjoint server 0-5 k6 30-40)"
check "disjoint: c5 0-5 vs 7-22 FAILS (one node)" "1" "$(PERF_SYSFS_ROOT=$C5 rc assert_nodes_disjoint server 0-5 k6 7-22)"
check "disjoint: wrong arity FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc assert_nodes_disjoint server 0-5 k6)"
check "disjoint: unreadable in CI FAILS" "1" "$(BUILDKITE=true PERF_SYSFS_ROOT=$T/none rc assert_nodes_disjoint a 0 b 1)"
check "check: c6i other, k6 on node 1" "0" "$(PERF_SYSFS_ROOT=$C6I rc numa_placement_check other 0-5 32-39,96-103 40-47,104-111)"
check "check: c6i other, one k6 on node 0 FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc numa_placement_check other 0-5 32-39 8-11)"
check "check: c6i other, a k6 straddling FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc numa_placement_check other 0-5 28-35)"
check "check: 3 nodes, other, a k6 straddling nodes 1 and 2 FAILS" "1" "$(PERF_SYSFS_ROOT=$TRI rc numa_placement_check other 0-1 6-9)"
check "check: 3 nodes, other, k6 on nodes 1 and 2 separately" "0" "$(PERF_SYSFS_ROOT=$TRI rc numa_placement_check other 0-1 4-7 8-11)"
check "check: c6i same, k6 on node 0" "0" "$(PERF_SYSFS_ROOT=$C6I rc numa_placement_check same 0-5 8-13,72-77)"
check "check: c6i same, k6 on node 1 FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc numa_placement_check same 0-5 32-39)"
check "check: c5 other, k6 on the only node" "0" "$(PERF_SYSFS_ROOT=$C5 rc numa_placement_check other 0-5 7-10,31-34)"
check "check: unreadable off-CI warns once, passes" "0|1" \
  "$(PERF_SYSFS_ROOT=$T/none rc numa_placement_check other 0-5 7-10)|$(PERF_SYSFS_ROOT=$T/none numa_placement_check other 0-5 7-10 2>&1 | grep -c WARNING)"
check "check: unreadable in CI FAILS" "1" "$(BUILDKITE=true PERF_SYSFS_ROOT=$T/none rc numa_placement_check other 0-5 7-10)"
check "numa=off c6i: 2 sockets, 1 node" "2|1" "$(PERF_SYSFS_ROOT=$NUMAOFF numa_package_count)|$(PERF_SYSFS_ROOT=$NUMAOFF numa_node_count)"
check "check: numa=off (more sockets than nodes) in CI FAILS" "1" \
  "$(BUILDKITE=true PERF_SYSFS_ROOT=$NUMAOFF rc numa_placement_check other 0-5 7-10,31-34)"
check "check: numa=off off-CI warns, passes" "0|1" \
  "$(PERF_SYSFS_ROOT=$NUMAOFF rc numa_placement_check other 0-5 7-10)|$(PERF_SYSFS_ROOT=$NUMAOFF numa_placement_check other 0-5 7-10 2>&1 | grep -c 'sockets but 1 NUMA node')"
check "check: SNC, k6 on another node of the SUT's socket FAILS" "1" "$(BUILDKITE=true PERF_SYSFS_ROOT=$SNC rc numa_placement_check other 0-1 4-5)"
check "check: SNC, k6 on the other socket" "0" "$(BUILDKITE=true PERF_SYSFS_ROOT=$SNC rc numa_placement_check other 0-1 8-9 10-11)"
check "check: c6i in CI (sockets = nodes)" "0" "$(BUILDKITE=true PERF_SYSFS_ROOT=$C6I rc numa_placement_check other 0-5 32-39)"
check "check: c5 in CI (one socket, one node)" "0" "$(BUILDKITE=true PERF_SYSFS_ROOT=$C5 rc numa_placement_check other 0-5 7-10,31-34)"
check "check: bad mode FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc numa_placement_check sideways 0-5 32-39)"
check "check: no k6 cpuset FAILS" "1" "$(PERF_SYSFS_ROOT=$C6I rc numa_placement_check other 0-5)"

echo "--- 3. the two-socket layout"
L="$(PERF_SYSFS_ROOT=$C6I numa_split_layout 6 4 other)"
check "c6i: SUT on node 0 cores 0-5, siblings idle" "0-5" "$(layout_value server "$L")"
check "c6i: upstream on the next core" "6" "$(layout_value upstream "$L")"
check "c6i: Prometheus on the next core + sibling" "7,71" "$(layout_value prometheus "$L")"
check "c6i: 4 x 8 k6 cores on node 1, both threads" \
  "32-39,96-103;40-47,104-111;48-55,112-119;56-63,120-127" "$(layout_value k6 "$L")"
check "c6i: nodes" "0 1" "$(layout_value sut_node "$L") $(layout_value k6_node "$L")"
check "c6i: N=8 -> 8 x 4" "32-35,96-99;36-39,100-103;40-43,104-107;44-47,108-111;48-51,112-115;52-55,116-119;56-59,120-123;60-63,124-127" \
  "$(layout_value k6 "$(PERF_SYSFS_ROOT=$C6I numa_split_layout 6 8 other)")"
check "c6i: 4 x 6 (A/B parity with same)" "32-37,96-101;38-43,102-107;44-49,108-113;50-55,114-119" \
  "$(layout_value k6 "$(PERF_SYSFS_ROOT=$C6I numa_split_layout 6 4 other 6)")"
check "c6i same: k6 on node 0's 24 free cores" "8-13,72-77;14-19,78-83;20-25,84-89;26-31,90-95" \
  "$(layout_value k6 "$(PERF_SYSFS_ROOT=$C6I numa_split_layout 6 4 same)")"
LA="$(PERF_SYSFS_ROOT=$ALT numa_split_layout 6 4 other)"
check "alt numbering: SUT one thread per core" "0,2,4,6,8,10" "$(layout_value server "$LA")"
check "alt numbering: Prometheus core + sibling" "14-15" "$(layout_value prometheus "$LA")"
check "alt numbering: k6 on node 1" "32-39;40-47;48-55;56-63" "$(layout_value k6 "$LA")"
LS="$(PERF_SYSFS_ROOT=$SWAP numa_split_layout 6 4 other)"
check "node 0 on the high cpus: the SUT, upstream and Prometheus follow node 0" "8-13|14|15" \
  "$(layout_value server "$LS")|$(layout_value upstream "$LS")|$(layout_value prometheus "$LS")"
check "node 0 on the high cpus: k6 on node 1" "0-1;2-3;4-5;6-7" "$(layout_value k6 "$LS")"
LN="$(PERF_SYSFS_ROOT=$SNC numa_split_layout 2 2 other)"
check "SNC: k6 on node 2 (the other socket), not node 1" "2|8-9;10-11" "$(layout_value k6_node "$LN")|$(layout_value k6 "$LN")"
check "SNC on one socket: no node for k6, rc 2" "2" "$(PERF_SYSFS_ROOT=$SNC1 numa_split_layout 1 1 other >/dev/null 2>&1; echo $?)"
check "one node: bad numbers still rc 1, not a number error" "1|" \
  "$(PERF_SYSFS_ROOT=$C5 numa_split_layout 6 0 other >/dev/null 2>"$T/err"; echo $?)|$(cat "$T/err")"
check "c5 (one node): rc 1, the caller keeps its layout" "1" "$(PERF_SYSFS_ROOT=$C5 numa_split_layout 6 4 other >/dev/null 2>&1; echo $?)"
check "unreadable: rc 1" "1" "$(PERF_SYSFS_ROOT=$T/none numa_split_layout 6 4 other >/dev/null 2>&1; echo $?)"
check "does not fit: 4 x 9 on node 1 is rc 2" "2" "$(PERF_SYSFS_ROOT=$C6I numa_split_layout 6 4 other 9 >/dev/null 2>&1; echo $?)"
check "does not fit: SUT 31 cores is rc 2" "2" "$(PERF_SYSFS_ROOT=$C6I numa_split_layout 31 4 other >/dev/null 2>&1; echo $?)"
check "bad procs is rc 2" "2" "$(PERF_SYSFS_ROOT=$C6I numa_split_layout 6 0 other >/dev/null 2>&1; echo $?)"
check "single k6, c6i other: 17 cores of node 1, one thread each" "32-48" "$(PERF_SYSFS_ROOT=$C6I numa_single_k6_cpus other 0-5 6 17)"
check "single k6, c6i same: node 0 after the SUT and upstream (the c5 string)" "7-23" "$(PERF_SYSFS_ROOT=$C6I numa_single_k6_cpus same 0-5 6 17)"
check "single k6, same: skips the upstream's core, wherever it is" "6,8-23" "$(PERF_SYSFS_ROOT=$C6I numa_single_k6_cpus same 0-5 7 17)"
check "single k6, same: skips a core whose sibling the upstream uses" "7-23" "$(PERF_SYSFS_ROOT=$C6I numa_single_k6_cpus same 0-5 70 17)"
check "single k6, node 0 on the high cpus: k6 on node 1's cpus" "0-6" "$(PERF_SYSFS_ROOT=$SWAP numa_single_k6_cpus other 8-13 14 7)"
check "single k6, SNC: the other socket's node, not node 1" "8-11" "$(PERF_SYSFS_ROOT=$SNC numa_single_k6_cpus other 0-1 2 4)"
check "single k6, one node: rc 1, the caller keeps 7-23" "1" "$(PERF_SYSFS_ROOT=$C5 numa_single_k6_cpus other 0-5 6 17 >/dev/null 2>&1; echo $?)"
check "single k6, unreadable: rc 1" "1" "$(PERF_SYSFS_ROOT=$T/none numa_single_k6_cpus other 0-5 6 17 >/dev/null 2>&1; echo $?)"
check "single k6, does not fit: rc 2" "2" "$(PERF_SYSFS_ROOT=$C6I numa_single_k6_cpus other 0-5 6 33 >/dev/null 2>&1; echo $?)"
check "single k6, SUT straddling nodes: rc 2" "2" "$(PERF_SYSFS_ROOT=$C6I numa_single_k6_cpus other 30-33 6 17 >/dev/null 2>&1; echo $?)"
check "single k6, one socket split into nodes: rc 2" "2" "$(PERF_SYSFS_ROOT=$SNC1 numa_single_k6_cpus other 0-1 2 2 >/dev/null 2>&1; echo $?)"
check "single k6, bad mode: rc 2" "2" "$(PERF_SYSFS_ROOT=$C6I numa_single_k6_cpus sideways 0-5 6 17 >/dev/null 2>&1; echo $?)"
check "every c6i layout cpuset is physically disjoint" "0" \
  "$(PERF_SYSFS_ROOT=$C6I rc cpusets_physically_disjoint server 0-5 upstream 6 prometheus 7,71 \
       k0 32-39,96-103 k1 40-47,104-111 k2 48-55,112-119 k3 56-63,120-127)"

echo "--- 4. rw-multi-k6-sweep.sh resolves its placement (PERF_RW_TEST_PLACEMENT_ONLY, stub docker)"
mkdir -p "$T/bin"
printf '#!/usr/bin/env bash\necho "docker $*" >> "$STUB_DOCKER_LOG"\nexit 1\n' > "$T/bin/docker"
chmod +x "$T/bin/docker"
place() { # sysfs host_cores env... -> JSON in $R, exit code in $RC
  local root="$1" hc="$2"; shift 2
  RC=0
  env PATH="$T/bin:$PATH" STUB_DOCKER_LOG="$T/docker.log" PERF_SYSFS_ROOT="$root" PERF_TEST_HOST_CORES="$hc" \
    PERF_RW_TEST_PLACEMENT_ONLY=true PERF_RW_K6_GOMEMLIMIT=off PERF_RW_REPO_ROOT="$REPO_ROOT" "$@" \
    bash "$HARNESS" >"$T/out.json" 2>"$T/stderr.log" || RC=$?
  R="$(cat "$T/out.json")"
}
q() { jq -r "$1" <<<"$R" 2>/dev/null || echo "<no json>"; }
: > "$T/docker.log"
# Today's default strings, byte for byte, on every one-node or unreadable host.
C5_K6="7-10,31-34;11-14,35-38;15-18,39-42;19-22,43-46"
place "$C5" 48
check "c5: exit 0" "0" "$RC"
check "c5: server/prometheus/k6 are today's strings" "0-5|23,47|$C5_K6" "$(q '"\(.server)|\(.prometheus)|\(.k6 | join(";"))"')"
check "c5: layout single_node, nodes 0/0" "single_node|1|0|0|true" "$(q '.placement | "\(.layout)|\(.numa_nodes)|\(.sut_node)|\(.k6_node)|\(.baseline_eligible)"')"
check "c5: every container's memory on node 0" "--cpuset-mems=0|--cpuset-mems=0|--cpuset-mems=0|4" \
  "$(q '"\(.mems.server)|\(.mems.prometheus)|\(.mems.xcheck)|\(.mems.k6 | map(select(. == "--cpuset-mems=0")) | length)"')"
place "$T/none" 48
check "unreadable, 48 cpus: today's strings" "0-5|23,47|$C5_K6|topology_unknown" "$(q '"\(.server)|\(.prometheus)|\(.k6 | join(";"))|\(.placement.layout)"')"
check "unreadable: no --cpuset-mems, numa_nodes null" "||null" "$(q '"\(.mems.server)|\(.mems.k6 | join(""))|\(.placement.numa_nodes)"')"
place "$T/none" 12
check "unreadable, 12 cpus (Docker Desktop): today's proportional layout" "0-3|4|5-6;7-8;9-10" "$(q '"\(.server)|\(.prometheus)|\(.k6 | join(";"))"')"
place "$T/none" 12 PERF_RW_PROCS=2
check "unreadable, 12 cpus, N=2: today's layout" "5-7;8-10" "$(q '.k6 | join(";")')"
place "$C6I" 128
check "c6i: exit 0" "0" "$RC"
check "c6i: SUT, Prometheus, k6 from the node map" "0-5|7,71|32-39,96-103;40-47,104-111;48-55,112-119;56-63,120-127" \
  "$(q '"\(.server)|\(.prometheus)|\(.k6 | join(";"))"')"
check "c6i: upstream default on the next core" "6" "$(q '.upstream_default')"
check "c6i: placement record" "numa_split|2|0|1|true" "$(q '.placement | "\(.layout)|\(.numa_nodes)|\(.sut_node)|\(.k6_node)|\(.baseline_eligible)"')"
check "c6i: memory per container's node" "--cpuset-mems=0|--cpuset-mems=0|--cpuset-mems=1|--cpuset-mems=1" \
  "$(q '"\(.mems.server)|\(.mems.prometheus)|\(.mems.xcheck)|\(.mems.k6 | unique | join(","))"')"
place "$C6I" 128 PERF_RW_PROCS=2
check "c6i: N=2 -> 2 x 16" "32-47,96-111;48-63,112-127" "$(q '.k6 | join(";")')"
place "$C6I" 128 PERF_K6_NUMA_NODE=same
check "c6i same: k6 on node 0, marked non-baseline" "8-13,72-77;14-19,78-83;20-25,84-89;26-31,90-95|numa_same_node|0|false" \
  "$(q '"\(.k6 | join(";"))|\(.placement.layout)|\(.placement.k6_node)|\(.placement.baseline_eligible)"')"
place "$C6I" 128 PERF_K6_NUMA_NODE=same PERF_RW_K6_CORES_PER_PROC=6
check "c6i other at the same-socket arm's 6 cores per process (A/B parity)" "0" "$RC"
# The same-socket A/B as the perf-xl arm step runs it: perf-test-run.sh passes its SUT and upstream.
place "$C6I" 128 PERF_K6_NUMA_NODE=same PERF_RW_SERVER_CPUS=0-5 PERF_RW_UPSTREAM_CPUS=6
check "c6i same, SUT and upstream passed in: k6 on node 0, disjoint, a non-baseline trial" \
  "0|8-13,72-77;14-19,78-83;20-25,84-89;26-31,90-95|numa_same_node|false|same|true" \
  "$RC|$(q '"\(.k6 | join(";"))|\(.placement.layout)|\(.placement.baseline_eligible)|\(.ab.k6_numa_node)|\(.ab.trial)"')"
place "$C6I" 128 PERF_RW_SERVER_CPUS=0-5 PERF_RW_UPSTREAM_CPUS=6 PERF_RW_K6_CORES_PER_PROC=6
check "c6i the A/B's control: other socket at 6 cores per process, also a trial" \
  "0|32-37,96-101;38-43,102-107;44-49,108-113;50-55,114-119|numa_split|null|6|true" \
  "$RC|$(q '"\(.k6 | join(";"))|\(.placement.layout)|\(.ab.k6_numa_node)|\(.ab.k6_cores_per_proc)|\(.ab.trial)"')"
place "$C6I" 128 PERF_RW_K6_CPUSETS="8-11;12-15"
check "c6i: explicit k6 on the SUT's node FAILS" "1" "$RC"
check "  ... naming the NUMA guard" "yes" "$(grep -q 'NUMA placement guard' "$T/stderr.log" && echo yes || echo no)"
place "$C6I" 128 PERF_RW_SERVER_CPUS="0-4,60" PERF_RW_PROCS=2 PERF_RW_K6_CORES_PER_PROC=4 # disjoint cores, two nodes
check "c6i: a SUT straddling nodes FAILS" "1" "$RC"
check "  ... naming the straddle" "yes" "$(grep -q 'straddles NUMA nodes' "$T/stderr.log" && echo yes || echo no)"
place "$C6I" 128 PERF_RW_PROM_CPUS="7,60" PERF_RW_PROCS=2 PERF_RW_K6_CORES_PER_PROC=4 # disjoint cores, two nodes
check "c6i: Prometheus straddling nodes FAILS" "1" "$RC"
check "  ... naming Prometheus" "yes" "$(grep -q 'prometheus cpuset .7,60. straddles' "$T/stderr.log" && echo yes || echo no)"
place "$C6I" 128 PERF_RW_XCHECK_CPUS="0-3"
check "c6i: the cross-check k6 on the SUT's node FAILS" "1" "$RC"
place "$C6I" 128 PERF_RW_SERVER_CPUS="0-5" PERF_RW_K6_CPUSETS="32-35;36-39" PERF_RW_PROCS=2
check "c6i: explicit, valid -> layout explicit" "0|explicit|1" "$(echo "$RC")|$(q '.placement.layout')|$(q '.placement.k6_node')"
place "$C6I" 128 PERF_RW_K6_CORES_PER_PROC=9
check "c6i: a layout that does not fit FAILS" "1" "$RC"
place "$C6I" 128 PERF_K6_NUMA_NODE=sideways
check "bad PERF_K6_NUMA_NODE FAILS" "2" "$RC"
place "$NUMAOFF" 128 BUILDKITE=true
check "numa=off c6i in CI: the multi-k6 arm FAILS" "1" "$RC"
check "  ... naming the socket/node mismatch" "yes" "$(grep -q '2 sockets but 1 NUMA node' "$T/stderr.log" && echo yes || echo no)"
place "$SNC" 16 PERF_RW_SERVER_CPUS=0-1 PERF_RW_PROM_CPUS=3 PERF_RW_K6_CPUSETS="4-5;6-7" PERF_RW_PROCS=2
check "SNC: explicit k6 on the SUT's socket FAILS" "1" "$RC"
place "$SNC" 16 PERF_RW_SERVER_CPUS=0-1 PERF_RW_PROM_CPUS=3 PERF_RW_K6_CPUSETS="8-9;10-11" PERF_RW_PROCS=2
check "SNC: explicit k6 on the other socket" "0|2" "$RC|$(q '.placement.k6_node')"
place "$C5" 48 PERF_RW_K6_CPUSETS="5-8;9-12"
check "the physical-core guard still runs (k6 on the SUT's core 5)" "1" "$RC"

echo "--- 4b. the ladder and the k6 VU-pool ceiling per host (c5 byte-identical; multi-socket extended)"
# The c5 default ladder, byte for byte, as it was before the multi-socket ladder existed.
C5_RATES="500,1000,2000,4000,8000,16000,24000,32000,36000,40000,44000,48000,56000,64000,72000,80000,96000,112000,128000"
XL_RATES="500,1000,2000,4000,8000,16000,24000,32000,36000,40000,44000,48000,56000,64000,72000,80000,96000,104000,112000,120000,128000,136000,144000,152000,160000"
ladder() { q '"\(.ladder.profile)|\(.ladder.source)|\(.ladder.rates)"'; }
vuc() { q '"\(.k6_runtime.vu_ceiling)|\(.k6_runtime.source.vu_ceiling)|\(.k6_runtime.vu_ceiling_basis.top_per_process_rps // "-")"'; }
place "$C5" 48
check "c5: the default ladder is unchanged" "default|default|$C5_RATES" "$(ladder)"
check "c5: sweep.js keeps its 2,048 VU ceiling (k6 CPU binds first)" "2048|sweep.js default|-" "$(vuc)"
place "$T/none" 48
check "unreadable topology: the c5 ladder" "default|default|$C5_RATES" "$(ladder)"
place "$NUMAOFF" 128
check "numa=off c6i (2 sockets, 1 node): the c5 ladder" "default|default|$C5_RATES" "$(ladder)"
place "$SNC1" 8 PERF_K6_NUMA_NODE=same PERF_RW_SERVER_CPUS=0 PERF_RW_PROM_CPUS=1 PERF_RW_K6_CPUSETS="2;3" PERF_RW_PROCS=2
check "one socket split into two nodes: the c5 ladder" "0|default|default|$C5_RATES" "$RC|$(ladder)"
place "$C6I" 128
check "c6i: 8k rungs from 96k to 160k" "multi_socket|default|$XL_RATES" "$(ladder)"
check "c6i: the VU ceiling is the 160k rung's own pool (40,000 rps per process x 0.08)" "3200|derived|40000" "$(vuc)"
place "$C6I" 128 PERF_K6_NUMA_NODE=same
check "c6i same-socket A/B: the same ladder and ceiling as the other arm" "multi_socket|default|$XL_RATES|3200|derived|40000" "$(ladder)|$(vuc)"
place "$C6I" 128 PERF_RW_PROCS=2
check "c6i N=2: the ceiling follows the per-process top rung (80,000 x 0.08)" "6400|derived|80000" "$(vuc)"
place "$C6I" 128 PERF_RW_RATES=1000,64000,128000
check "c6i PERF_RW_RATES: applied as given, the ceiling from its top rung" "multi_socket|env|1000,64000,128000|2560|derived|32000" "$(ladder)|$(vuc)"
place "$C6I" 128 PERF_RW_RATES=1000,8000,72000
check "c6i PERF_RW_RATES whose pools fit under 2,048: sweep.js's ceiling" "env|1000,8000,72000|2048|sweep.js default" \
  "$(q '"\(.ladder.source)|\(.ladder.rates)"')|$(q '"\(.k6_runtime.vu_ceiling)|\(.k6_runtime.source.vu_ceiling)"')"
place "$C5" 48 PERF_RW_RATES=500,2000
check "c5 PERF_RW_RATES: applied as given" "default|env|500,2000" "$(ladder)"
place "$C5" 48 PERF_RW_K6_VU_CEILING=4096
check "c5 PERF_RW_K6_VU_CEILING: applied as given" "4096|env|-" "$(vuc)"
place "$C6I" 128 PERF_RW_K6_VU_CEILING=2048
check "c6i PERF_RW_K6_VU_CEILING=2048 restores sweep.js's cap" "2048|env|-" "$(vuc)"
for bad in 0 abc 12.5 -3; do
  place "$C5" 48 PERF_RW_K6_VU_CEILING="$bad"
  check "PERF_RW_K6_VU_CEILING='$bad' is rejected with exit 2" "2" "$RC"
done
place "$C6I" 128
check "c6i: the raw node cpulists are recorded" '{"node0":"0-31,64-95","node1":"32-63,96-127"}' "$(q '.node_cpulists | tostring')"
check "  ... and logged" "yes" "$(grep -q -- '^--- NUMA node cpulists (raw .*): node0=0-31,64-95 node1=32-63,96-127$' "$T/stderr.log" && echo yes || echo no)"
check "  ... and the placement line says its mems are requested, not observed" "yes" \
  "$(grep -q -- '^--- placement: .* requested mems: server=--cpuset-mems=0 ' "$T/stderr.log" && echo yes || echo no)"
place "$T/none" 48
check "unreadable: no node cpulists" "{}" "$(q '.node_cpulists | tostring')"
check "nothing was started (stub docker never called)" "0" "$(wc -l < "$T/docker.log" | tr -d ' ')"

echo "--- 5. perf-percore.sh hardware matrix and per-core placement (PERF_PERCORE_TEST_PLACEMENT_ONLY)"
printf '#!/usr/bin/env bash\necho "docker $*" >> "$STUB_DOCKER_LOG"\nexit 0\n' > "$T/bin/docker"
percore() { # sysfs host_cores mode env... -> JSON in $R, exit code in $RC
  local root="$1" hc="$2" mode="$3"; shift 3
  RC=0
  env PATH="$T/bin:$PATH" STUB_DOCKER_LOG="$T/docker.log" PERF_SYSFS_ROOT="$root" PERF_TEST_HOST_CORES="$hc" \
    PERF_PERCORE_TEST_PLACEMENT_ONLY=true PERF_PERCORE_MODE="$mode" PERF_PERCORE_REPO_ROOT="$REPO_ROOT" "$@" \
    bash "$PERCORE" >"$T/out.json" 2>"$T/stderr.log" || RC=$?
  R="$(cat "$T/out.json")"
}
percore "$C5" 48 hw_matrix
check "c5 matrix: exit 0" "0" "$RC"
check "c5 matrix: today's client layout" "$C5_K6|23,47|single_node" "$(q '"\(.k6_cpusets | join(";"))|\(.prometheus_cpus)|\(.layout)"')"
check "c5 matrix: SUT on cores 0..C-1" "0|0,1|0,1|0,1,2|0,1,2,3|0,1,2,3,4,5" "$(q '[.points[].server_cpus] | join("|")')"
check "c5 matrix: every point placed on node 0" "6|0|0" "$(q '"\(.points | length)|\([.points[].placement.sut_node] | unique | join(","))|\([.points[].placement.k6_node] | unique | join(","))"')"
percore "$C6I" 128 hw_matrix
check "c6i matrix: exit 0" "0" "$RC"
check "c6i matrix: the multi-k6 arm's client layout" "32-39,96-103;40-47,104-111;48-55,112-119;56-63,120-127|7,71|numa_split" \
  "$(q '"\(.k6_cpusets | join(";"))|\(.prometheus_cpus)|\(.layout)"')"
check "c6i matrix: SUT on node 0 cores" "0|0,1|0,1|0,1,2|0,1,2,3|0,1,2,3,4,5" "$(q '[.points[].server_cpus] | join("|")')"
check "c6i matrix: points record SUT node 0, k6 node 1" "0|1|--cpuset-mems=0|--cpuset-mems=1" \
  "$(q '"\([.points[].placement.sut_node] | unique | join(","))|\([.points[].placement.k6_node] | unique | join(","))|\([.points[].mems.server] | unique | join(","))|\([.points[].mems.k6] | unique | join(","))"')"
percore "$C6I" 128 hw_matrix PERF_K6_NUMA_NODE=same
check "c6i matrix same: k6 on node 0, non-baseline" "8-13,72-77;14-19,78-83;20-25,84-89;26-31,90-95|false" \
  "$(q '"\(.k6_cpusets | join(";"))|\([.points[].placement.baseline_eligible] | unique | join(","))"')"
percore "$C6I" 128 hw_matrix PERF_HW_MATRIX_K6_CPUSETS="8-11;12-15" PERF_HW_MATRIX_PROM_CPUS="7"
check "c6i matrix: k6 on the SUT's node -> every point a NUMA failure" "0|6|failure" \
  "$(q '"\(.points | length)|\(.skipped | length)|\([.skipped[].type] | unique | join(","))"')"
percore "$C6I" 128 hw_matrix PERF_HW_MATRIX_PROM_CPUS="7,60" PERF_HW_MATRIX_PROCS=2 PERF_HW_MATRIX_K6_CORES_PER_PROC=4
check "c6i matrix: Prometheus straddling nodes -> every point a NUMA failure" "0|6|failure" \
  "$(q '"\(.points | length)|\(.skipped | length)|\([.skipped[].type] | unique | join(","))"')"
percore "$C6I" 128 percore PERF_PERCORE_CORES=1,4
check "c6i per-core: single k6 on node 1, one thread per core minus the reserve" "0|0,1,2,3|1|31" \
  "$(q '"\(.points[0].server_cpus)|\(.points[1].server_cpus)|\([.points[].placement.k6_node] | unique | join(","))|\(.points[0].k6_cpus | split(",") | length)"')"
check "c6i per-core: k6 cpus all on node 1" "1" "$(PERF_SYSFS_ROOT=$C6I cpuset_numa_nodes "$(q '.points[0].k6_cpus')")"
percore "$SWAP" 16 percore PERF_PERCORE_CORES=1,4
check "node 0 on the high cpus, per-core: SUT on node 0, k6 on node 1" "8|8,9,10,11|0,1,2,3,4,5,6" \
  "$(q '"\(.points[0].server_cpus)|\(.points[1].server_cpus)|\(.points[0].k6_cpus)"')"
percore "$SNC" 16 percore PERF_PERCORE_CORES=1
check "SNC per-core: k6 on node 2 (the other socket), not node 1" "0|8,9,10|2" \
  "$(q '"\(.points[0].server_cpus)|\(.points[0].k6_cpus)|\(.points[0].placement.k6_node)"')"
percore "$C5" 48 percore PERF_PERCORE_CORES=1,4
check "c5 per-core: unchanged (SUT 0, k6 1..22)" "0|1-22" \
  "$(q '.points[0].server_cpus')|$(compress_cpulist "$(q '.points[0].k6_cpus' | tr ',' ' ')")"

echo "--- 5b. hardware-matrix re-assembly restores the perf-xl placement from matrix-inputs.json"
reassemble_xl() { # mode(other|same) -> the re-assembled block in $R
  local W="$T/xl-$1" layout=numa_split
  [ "$1" = same ] && layout=numa_same_node
  mkdir -p "$W"
  jq -n --arg mode "$1" --arg layout "$layout" '{source:"live", env:{PERF_HW_MATRIX:"1:512m,2:1g"},
    resolved:{host_cores:128, host_physical_cores:64, topology_known:true, k6_physical_cores:32,
              image:"mockserver/mockserver:test", image_java_tool_options:"", java_tool_options:"", rig_paused:true,
              hw_k6_cpusets:"32-39,96-103;40-47,104-111;48-55,112-119;56-63,120-127", hw_prom_cpus:"7,71",
              layout:$layout, k6_numa_node:$mode}}' > "$W/matrix-inputs.json"
  printf '%s\n' '{"cores":1,"key":"1c-512m","memory_limit":"512m","reason":"x","type":"failure"}' \
    '{"cores":2,"key":"2c-1g","memory_limit":"1g","reason":"x","type":"failure"}' > "$W/skipped.ndjson"
  RC=0
  # Re-assembled on a host with no topology: every placement fact must come from the inputs.
  env PATH="$T/bin:$PATH" STUB_DOCKER_LOG="$T/docker.log" PERF_SYSFS_ROOT="$T/none" PERF_PERCORE_MODE=hw_matrix \
    PERF_HW_MATRIX_REASSEMBLE_DIR="$W" PERF_PERCORE_REPO_ROOT="$REPO_ROOT" bash "$PERCORE" "$T/xl.json" 2>"$T/stderr.log" || RC=$?
  R="$(cat "$T/xl.json" 2>/dev/null)"
}
reassemble_xl other
check "re-assembly: exit 0" "0" "$RC"
check "re-assembly: perf-xl client placement restored" "32-39,96-103;40-47,104-111;48-55,112-119;56-63,120-127|7,71|numa_split|other|true" \
  "$(q '"\(.client_placement.k6_cpusets | join(";"))|\(.client_placement.prometheus_cpus)|\(.client_placement.layout)|\(.client_placement.k6_numa_node)|\(.baseline_eligible)"')"
reassemble_xl same
check "re-assembly of a same-socket matrix: not baseline-eligible" "numa_same_node|same|false" \
  "$(q '"\(.client_placement.layout)|\(.client_placement.k6_numa_node)|\(.baseline_eligible)"')"

echo "--- 6. every pinned container also pins its memory to that cpuset's node"
# <file> <cpuset expr> <mems var> <the mems var's definition>: one docker run per row, and no
# --cpuset-cpus line outside the table (a new container without its mems flag fails here).
RW="$HARNESS"
MEMS_TABLE="$RW|\$PROM_CPUS|PROM_MEMS|PROM_MEMS=\"\$(numa_mems_flag \"\$PROM_CPUS\")\"
$RW|\$XCHECK_CPUS|XCHECK_MEMS|XCHECK_MEMS=\"\$(numa_mems_flag \"\$XCHECK_CPUS\")\"
$RW|\$SERVER_CPUS|SUT_MEMS|SUT_MEMS=\"\$(numa_mems_flag \"\$SERVER_CPUS\")\"
$RW|\${set_arr[\$i]}|mems|mems=\"\$(numa_mems_flag \"\${set_arr[\$i]}\")\"
$PERCORE|\$1|mems|local mems; mems=\"\$(numa_mems_flag \"\$1\")\"
$PERCORE|\$SCPU|SUT_MEMS|SUT_MEMS=\"\$(numa_mems_flag \"\$SCPU\")\"
$PERCORE|\$KCPU|K6_MEMS|K6_MEMS=\"\$(numa_mems_flag \"\${KCPU//;/,}\")\""
while IFS='|' read -r f cpus var def; do
  n="$(grep -cF -- "--cpuset-cpus=\"$cpus\" \${$var:+\"\$$var\"}" "$f" || true)"
  [ "$n" -ge 1 ] && ok "$(basename "$f"): --cpuset-cpus=\"$cpus\" carries \$$var ($n run(s))" \
    || bad "$(basename "$f"): no docker run pins --cpuset-cpus=\"$cpus\" with \${$var:+\"\$$var\"}"
  grep -qF -- "$def" "$f" && ok "  ... and \$$var is the node of that cpuset" || bad "$(basename "$f"): \$$var is not defined as: $def"
done <<<"$MEMS_TABLE"
for f in "$RW" "$PERCORE"; do
  stray="$(grep -n -- '--cpuset-cpus=' "$f" | grep -v -- '_MEMS:+"\|{mems:+"' || true)"
  check "$(basename "$f"): no --cpuset-cpus without a mems flag" "" "$stray"
done

echo "--- 6b. rw-multi-k6-sweep.sh records what Docker applied to every container it starts detached"
# <container var>|<the log call that must follow its docker run -d>: a new detached container fails here.
OBS_TABLE='$PROM_NAME|log_container_cpuset prometheus "$PROM_NAME" "$OBSERVED"
$SUT_NAME|[ -n "$SUT_CONTAINER" ] && log_container_cpuset sut "$SUT_CONTAINER" "$OBSERVED"
$kname|log_container_cpuset "k6_${phase}_p${i}" "$kname" "$OBSERVED"'
check "every detached docker run is in the table" "$(wc -l <<<"$OBS_TABLE" | tr -d ' ')" "$(grep -cE 'docker run -d ' "$RW")"
while IFS='|' read -r var call; do
  # The log call must come after that container's docker run and before the next docker run.
  got="$(OBS_VAR="$var" OBS_CALL="$call" awk 'index($0, "docker run -d --name \"" ENVIRON["OBS_VAR"] "\"") {p = 1; next}
    p && index($0, "docker run ") {exit} p && index($0, ENVIRON["OBS_CALL"]) {print "yes"; exit}' "$RW")"
  check "$var: its observed cpuset is logged and recorded" "yes" "${got:-no}"
done <<<"$OBS_TABLE"
check "the result and the fallback both carry the observed placement" "1|1" \
  "$(grep -cF -- '--argjson placement "$(placement_result_json)"' "$RW")|$(grep -cF -- '--argjson placement "$(placement_result_json 2>/dev/null' "$RW")"
PRJ="$(awk '/^placement_result_json\(\) \{/ {p=1} p {print} p && /^}/ {exit}' "$RW")"
[ -n "$PRJ" ] || bad "placement_result_json not found in $RW"
prj() { # observed_ndjson sut_mems [k6_mems_json] -> the placement block
  printf '%s\n' "$1" > "$T/obs.ndjson"
  env -i PATH="$PATH" bash -c "set -euo pipefail; $PRJ
    PLACEMENT_JSON='{\"layout\":\"numa_split\"}' OBSERVED='$T/obs.ndjson' NODE_CPULISTS_JSON='{\"node0\":\"0-31\"}'
    SUT_MEMS='$2' PROM_MEMS='--cpuset-mems=0' XCHECK_MEMS='--cpuset-mems=1' K6_MEMS_JSON='${3:-}'
    placement_result_json"
}
OBS_SUT='{"role":"sut","container":"s","cpuset_cpus":"0-5","cpuset_mems":"0"}'
OBS_K6='{"role":"k6_main_p0","container":"k","cpuset_cpus":"32-39,96-103","cpuset_mems":"1"}'
check "observed_mems is the SUT's, as requested" "0|true|0|1|numa_split|2" \
  "$(prj "$OBS_SUT"$'\n'"$OBS_K6" --cpuset-mems=0 | jq -r '"\(.observed_mems)|\(.sut_mems_as_requested)|\(.requested_mems.sut)|\(.requested_mems.xcheck)|\(.layout)|\(.observed | length)"')"
check "requested_mems.k6: one entry per main k6 process, in order; null before it is resolved" '["1","1","1",""]|null' \
  "$(prj "$OBS_SUT" --cpuset-mems=0 '["1","1","1",""]' | jq -c '.requested_mems.k6')|$(prj "$OBS_SUT" --cpuset-mems=0 | jq -c '.requested_mems.k6')"
# shellcheck disable=SC2016  # literal source lines
KM_BUILD='K6_MEMS_JSON="$(for _k in "${K6_SETS[@]}"; do _f="$(numa_mems_flag "$_k")"'
# shellcheck disable=SC2016
KM_RUN='mems="$(numa_mems_flag "${set_arr[$i]}")"'
KM_OK=no; grep -qF -- "$KM_BUILD" "$RW" && grep -qF -- "$KM_RUN" "$RW" && KM_OK=yes
check "  ... built from numa_mems_flag over every main k6 cpuset, as run_phase passes it" "yes" "$KM_OK"
check "a SUT on other memory than requested is shown, not hidden" "\"\"|false" \
  "$(prj '{"role":"sut","container":"s","cpuset_cpus":"0-5","cpuset_mems":""}' --cpuset-mems=0 | jq -r '"\(.observed_mems | tojson)|\(.sut_mems_as_requested)"')"
check "an uninspectable SUT reads null, never as requested" "null|null" \
  "$(prj '{"role":"sut","container":"s","cpuset_cpus":null,"cpuset_mems":null}' --cpuset-mems=0 | jq -r '"\(.observed_mems)|\(.sut_mems_as_requested)"')"
check "no SUT observed (external target): null" "null|null|1" \
  "$(prj "$OBS_K6" "" | jq -r '"\(.observed_mems)|\(.sut_mems_as_requested)|\(.observed | length)"')"

echo "--- 6c. the multi-k6 arm passes its VU ceiling to every measured k6, mirroring sweep.js's defaults"
check "run_phase passes K6_SWEEP_VU_CEILING when the arm sets one" "1" \
  "$(grep -cF -- '${K6_VU_CEILING:+-e "K6_SWEEP_VU_CEILING=$K6_VU_CEILING"}' "$RW")"
CONFIG_JS="$REPO_ROOT/mockserver-performance-test/k6/lib/config.js"
cfg_default() { sed -nE "s/.*num\('$1', ([0-9]+)\).*/\1/p" "$CONFIG_JS"; }
check "RW_VUS_PER_KRPS is sweep.js's default (config.js|harness)" "80|80" \
  "$(cfg_default K6_SWEEP_VUS_PER_KRPS)|$(sed -nE 's/^RW_VUS_PER_KRPS=([0-9]+).*/\1/p' "$RW")"
check "RW_SWEEP_VU_CEILING is sweep.js's default (config.js|harness)" "2048|2048" \
  "$(cfg_default K6_SWEEP_VU_CEILING)|$(sed -nE 's/^RW_SWEEP_VU_CEILING=([0-9]+).*/\1/p' "$RW")"

LINT="$REPO_ROOT/.buildkite/scripts/steps/perf-test-lint.sh"
check "perf-test-lint.sh inspects sweep.js at the multi-socket ladder per process (N=4) and its ceiling" \
  "$(sed -nE 's/^MULTI_SOCKET_RATES="([0-9,]+)"$/\1/p' "$RW" | tr ',' '\n' | awk '{printf "%s%d", (NR > 1 ? "," : ""), $1 / 4}')|3200" \
  "$(grep -oE 'K6_SWEEP_RATES=[0-9,]+ -e K6_SWEEP_VU_CEILING=[0-9]+' "$LINT" | sed -E 's/K6_SWEEP_RATES=([0-9,]+) -e K6_SWEEP_VU_CEILING=([0-9]+)/\1|\2/')"

echo "--- 7. perf-test-run.sh: main SUT, upstream and k6 placement, memory and eligibility (real blocks)"
# Lifted from the script rather than run whole: everything after its trap and stale-container sweep
# would need docker, and a test-only switch in the script could leak into a real run.
lift() { # first_line_regex last_line_regex -> the lines from the first match through the next end match
  LIFT_A="$1" LIFT_B="$2" awk '!p && $0 ~ ENVIRON["LIFT_A"] {p=1; print; next} p {print} p && $0 ~ ENVIRON["LIFT_B"] {exit}' "$RUN"
}
extract_fn() { awk -v n="$1" '$0 ~ "^"n"\\(\\) *\\{" {p=1; print; if ($0 ~ /}$/) exit; next} p {print} p && /^}/ {exit}' "$RUN"; }
NODEMODE="$(lift '^K6_NUMA_NODE="[$][{]PERF_K6_NUMA_NODE:-other[}]"$' '^case "[$]K6_NUMA_NODE"')"
LIBSRC="$(lift '^if \[ -r "[$]SCRIPT_DIR/lib/perf-cpu-topology.sh" \]; then$' '^fi$')"
PIN="$(lift '^# --- core pinning' '^fi$')"
ELIG="$(awk '/^BASELINE_ELIGIBLE="true"$/ {p=1} /^# --- image freshness:/ {exit} p' "$RUN")"
HWMELIG="$(lift '^# The matrix records its own eligibility' '^fi$')"
GUARDFN="$(extract_fn assert_cpusets_physically_disjoint)"; STARTFN="$(extract_fn start_mockserver)"; CPUARG="$(extract_fn cpuset_arg)"
for b in NODEMODE LIBSRC PIN ELIG HWMELIG GUARDFN STARTFN CPUARG; do [ -n "${!b}" ] || bad "block $b not found in $RUN"; done
# A range with no end match runs to EOF, and executing that would run the rest of the script.
for b in LIBSRC PIN HWMELIG; do [ "$(tail -1 <<<"${!b}")" = "fi" ] || bad "block $b does not end at its fi"; done
grep -q '^case "\$K6_NUMA_NODE"' <<<"$(tail -1 <<<"$NODEMODE")" || bad "block NODEMODE does not end at its case"
printf '%s\n' 'set -euo pipefail' 'getconf() { echo "$FAKE_CORES"; }' "$NODEMODE" "$LIBSRC" "$GUARDFN" "$PIN" \
  'echo "RESULT|$SERVER_CPUS|$UPSTREAM_CPUS|$K6_CPUS|${_NUMA_MAP_KEY:+primed}"' > "$T/pin.sh"
pin() { # sysfs logical_cpus env... -> "rc|server|upstream|k6|primed"; output in $T/pin.log
  local root="$1" cores="$2" rc=0; shift 2
  env -i PATH="$PATH" PERF_SYSFS_ROOT="$root" FAKE_CORES="$cores" SCRIPT_DIR="$(dirname "$RUN")" "$@" \
    bash "$T/pin.sh" >"$T/pin.log" 2>&1 || rc=$?
  echo "$rc|$(grep '^RESULT|' "$T/pin.log" | cut -d'|' -f2- || true)"
}
logged() { grep -q -- "$1" "$T/pin.log" && echo yes || echo no; }
# One node, an unreadable map, numa=off or a small box: today's strings exactly.
check "c5: today's cpusets, map primed after the lib is sourced" "0|0-5|6|7-23|primed" "$(pin "$C5" 48)"
check "  ... SUT and k6 each verified on node 0" "yes|yes" "$(logged 'server cpuset 0-5 is on NUMA node 0')|$(logged 'k6_0 cpuset 7-23 is on NUMA node 0')"
check "c5 in CI: one socket, one node passes" "0|0-5|6|7-23|primed" "$(pin "$C5" 48 BUILDKITE=true)"
check "unreadable (Docker Desktop), 48 cpus: today's cpusets" "0|0-5|6|7-23|primed" "$(pin "$T/none" 48)"
check "  ... the node cpulists are logged as unreadable" "yes" "$(logged '^--- NUMA node cpulists (raw .*): unreadable$')"
check "  ... the NUMA check warns, once" "1" "$(grep -c 'WARNING: the NUMA node map is unreadable' "$T/pin.log" || true)"
check "unreadable, 12 cpus: unpinned, as today" "0||||primed" "$(pin "$T/none" 12)"
check "c5 with 8 cpus online: unpinned, no NUMA check" "0||||primed|no" "$(pin "$C5" 8)|$(logged 'NUMA node [0-9]\|NUMA node map\|NUMA placement')"
check "numa=off c6i off-CI: today's cpusets, warned" "0|0-5|6|7-23|primed|yes" "$(pin "$NUMAOFF" 128)|$(logged 'sockets but 1 NUMA node')"
check "numa=off c6i in CI: FAILS on the socket/node mismatch" "1|yes" "$(pin "$NUMAOFF" 128 BUILDKITE=true | cut -d'|' -f1)|$(logged '2 sockets but 1 NUMA node')"
# Two nodes: k6 moves to the other socket, the rest stays.
check "c6i: k6 on node 1, 17 cores one thread each" "0|0-5|6|32-48|primed" "$(pin "$C6I" 128)"
check "  ... the raw node cpulists are logged once" "1" \
  "$(grep -c -- '^--- NUMA node cpulists (raw .*/devices/system/node/node\*/cpulist): node0=0-31,64-95 node1=32-63,96-127$' "$T/pin.log" || true)"
check "  ... verified on different nodes" "yes" "$(logged 'server on NUMA node 0, k6 on node 1')"
check "c6i in CI" "0|0-5|6|32-48|primed" "$(pin "$C6I" 128 BUILDKITE=true)"
check "c6i same: k6 back on node 0, the c5 string" "0|0-5|6|7-23|primed|yes" "$(pin "$C6I" 128 PERF_K6_NUMA_NODE=same)|$(logged "k6 on the SUT's NUMA node 0")"
check "c6i: explicit PERF_K6_CPUS=7-23 (the SUT's node) FAILS" "1|yes" "$(pin "$C6I" 128 PERF_K6_CPUS=7-23 | cut -d'|' -f1)|$(logged 'share NUMA node')"
check "c6i: explicit PERF_K6_CPUS on node 1 is kept" "0|0-5|6|32-63|primed" "$(pin "$C6I" 128 PERF_K6_CPUS=32-63)"
check "c6i same: explicit k6 on node 1 FAILS" "1" "$(pin "$C6I" 128 PERF_K6_NUMA_NODE=same PERF_K6_CPUS=32-48 | cut -d'|' -f1)"
check "c6i: a SUT straddling nodes FAILS before k6 is chosen" "1|yes" "$(pin "$C6I" 128 PERF_SERVER_CPUS=30-33 | cut -d'|' -f1)|$(logged "SUT cpuset '30-33' is not on one NUMA node")"
check "c6i: an upstream straddling nodes FAILS" "1|yes" "$(pin "$C6I" 128 PERF_UPSTREAM_CPUS=6,40 | cut -d'|' -f1)|$(logged "upstream cpuset '6,40' straddles")"
check "alt numbering: node 1 too small for 17 cores FAILS, naming PERF_K6_CPUS" "1|yes" "$(pin "$ALT" 64 | cut -d'|' -f1)|$(logged 'set PERF_K6_CPUS')"
check "SNC: the default does not fit FAILS" "1" "$(pin "$SNC" 16 PERF_SERVER_CPUS=0-1 PERF_UPSTREAM_CPUS=2 | cut -d'|' -f1)"
check "SNC: explicit k6 on the SUT's socket FAILS" "1" "$(pin "$SNC" 16 PERF_SERVER_CPUS=0-1 PERF_UPSTREAM_CPUS=2 PERF_K6_CPUS=4-7 | cut -d'|' -f1)"
check "SNC: explicit k6 on the other socket" "0|0-1|2|8-11|primed" "$(pin "$SNC" 16 PERF_SERVER_CPUS=0-1 PERF_UPSTREAM_CPUS=2 PERF_K6_CPUS=8-11)"
check "one socket split into nodes: no node for k6 FAILS" "1|yes" "$(pin "$SNC1" 16 PERF_SERVER_CPUS=0-1 PERF_UPSTREAM_CPUS=2 | cut -d'|' -f1)|$(logged 'no NUMA node is on another socket')"
check "bad PERF_K6_NUMA_NODE FAILS" "1|yes" "$(pin "$C5" 48 PERF_K6_NUMA_NODE=sideways | cut -d'|' -f1)|$(logged "PERF_K6_NUMA_NODE='sideways' must be other or same")"
check "  ... even unpinned, before any placement" "1|yes" "$(pin "$C5" 8 PERF_K6_NUMA_NODE=sideways | cut -d'|' -f1)|$(logged "^ERROR: PERF_K6_NUMA_NODE='sideways' must be other or same")"

# start_mockserver: the SUT, the upstream and every other container it starts get their cpuset's node.
printf '%s\n' 'set -euo pipefail' "$LIBSRC" "$CPUARG" "$STARTFN" \
  'require_dns_hostname() { :; }; diag_jvm_opts() { :; }; compose_java_tool_options() { :; }' \
  'docker() { case "$1" in
      run) printf "%s\n" "$@" > "$ARGS_FILE"; [ "${FAKE_RUN:-ok}" = ok ] ;;
      inspect) [ "${FAKE_INSPECT:-ok}" = ok ] || return 1
               printf "%s %s\n" "$(sed -n "s/^--cpuset-cpus=//p" "$ARGS_FILE")" "$(sed -n "s/^--cpuset-mems=//p" "$ARGS_FILE")" ;;
    esac; }' \
  'NETWORK=n MOCKSERVER_IMAGE=img PERF_MAX_EVENT_LOG_BYTES=1 HARNESS_FIXED_EVENT_LOG_BYTES=1 SUT_IMAGE_JAVA_TOOL_OPTIONS= PERF_NETWORK_MODE=bridge START_EXTRA_ENV=()' \
  'start_mockserver name "$CPUS" alias || exit "$?" # an || caller, as perf-path-coverage.sh is: set -e is off inside' > "$T/start.sh"
started() { # sysfs cpus [env...] -> the container's --cpuset-* arguments, '|'-joined; stderr in $T/start.err
  local root="$1" cpus="$2"; shift 2
  : > "$T/start.args"
  env -i PATH="$PATH" PERF_SYSFS_ROOT="$root" CPUS="$cpus" ARGS_FILE="$T/start.args" SCRIPT_DIR="$(dirname "$RUN")" "$@" \
    bash "$T/start.sh" >/dev/null 2>"$T/start.err" || echo "rc=$?"
  grep -E '^--cpuset-' "$T/start.args" | paste -sd'|' - || true
}
observed() { grep -- '^--- observed placement:' "$T/start.err" | paste -sd'|' - || true; }
check "c6i SUT 0-5: memory on node 0" "--cpuset-cpus=0-5|--cpuset-mems=0" "$(started "$C6I" 0-5)"
check "c6i upstream 6: memory on node 0" "--cpuset-cpus=6|--cpuset-mems=0" "$(started "$C6I" 6)"
check "c6i container on node 1: memory on node 1" "--cpuset-cpus=32-35|--cpuset-mems=1" "$(started "$C6I" 32-35)"
check "c5 SUT: memory on node 0" "--cpuset-cpus=0-5|--cpuset-mems=0" "$(started "$C5" 0-5)"
check "unreadable map (Docker Desktop): --cpuset-cpus only, as today" "--cpuset-cpus=0-5" "$(started "$T/none" 0-5)"
check "unpinned container: neither flag, as today" "" "$(started "$C6I" "")"
echo "--- 7b. start_mockserver logs the cpuset Docker applied (docker inspect), and still fails on a failed run"
started "$C6I" 0-5 >/dev/null
check "c6i SUT: the observed cpus and mems are logged" "--- observed placement: alias name cpuset_cpus=0-5 cpuset_mems=0" "$(observed)"
started "$C6I" "" >/dev/null
check "unpinned: logged as unrestricted" "--- observed placement: alias name cpuset_cpus=<unrestricted> cpuset_mems=<unrestricted>" "$(observed)"
check "an inspect failure is logged, not fatal" "--cpuset-cpus=0-5|--cpuset-mems=0" "$(started "$C6I" 0-5 FAKE_INSPECT=fail)"
check "  ... as <inspect failed>" "--- observed placement: alias name cpuset_cpus=<inspect failed> cpuset_mems=<inspect failed>" "$(observed)"
check "a failed docker run still fails start_mockserver, with nothing logged as observed" "rc=1|" \
  "$(started "$C6I" 0-5 FAKE_RUN=fail | grep -o '^rc=[0-9]*' | head -1)|$(observed)"
check "the main SUT and upstream are started with their cpusets" "1|1" \
  "$(grep -cE '^start_mockserver "\$UPSTREAM" "\$UPSTREAM_CPUS" ' "$RUN" || true)|$(grep -cE '^start_mockserver "\$SERVER" "\$SERVER_CPUS" ' "$RUN" || true)"

# Baseline eligibility: PERF_K6_NUMA_NODE=same, and the hardware matrix's own verdict.
elig() { # env... -> BASELINE_ELIGIBLE after the real eligibility block
  env -i PATH="$PATH" PERF_RUN_ARM="" PERF_JVM_DIAGNOSTICS=standard JAVA_TOOL_OPTS_VAL="" CONFIG_PROFILE=default RIG_PROFILE=default "$@" \
    bash -c "set -euo pipefail; arm_only() { [ -n \"\$PERF_RUN_ARM\" ]; }
$NODEMODE
$ELIG
echo \"\$BASELINE_ELIGIBLE\${ELIG_SHOW_REASONS:+\$INELIGIBLE_REASONS}\"" 2>/dev/null | tail -1
}
check "eligibility: default k6 placement stays eligible" "true|true" "$(elig)|$(elig PERF_K6_NUMA_NODE=other)"
check "eligibility: PERF_K6_NUMA_NODE=same is not eligible" "false" "$(elig PERF_K6_NUMA_NODE=same)"
check "eligibility: each cause is recorded as a reason, in order (item 44 reads them)" "true|false k6_numa_same|false arm_only|false jvm_diagnostics arm_only|false jvm_diagnostics" \
  "$(elig ELIG_SHOW_REASONS=1)|$(elig ELIG_SHOW_REASONS=1 PERF_K6_NUMA_NODE=same)|$(elig ELIG_SHOW_REASONS=1 PERF_RUN_ARM=rw_multik6 PERF_SERVING_RW_MULTIK6=true)|$(elig ELIG_SHOW_REASONS=1 PERF_RUN_ARM=rw_multik6 PERF_SERVING_RW_MULTIK6=true PERF_JVM_DIAGNOSTICS=gc)|$(elig ELIG_SHOW_REASONS=1 PERF_JVM_DIAGNOSTICS=deep)"
MARK_INELIGIBLE="$(grep '^mark_ineligible() {' "$RUN")" # the real recorder, defined above the block
hwm_elig() { # serving_hw_matrix block -> BASELINE_ELIGIBLE and the reasons after the real matrix-eligibility block
  env -i PATH="$PATH" SERVING_HW_MATRIX_JSON="$1" BASELINE_ELIGIBLE=true INELIGIBLE_REASONS="" bash -c "set -euo pipefail
$MARK_INELIGIBLE
$HWMELIG
echo \"\$BASELINE_ELIGIBLE\$INELIGIBLE_REASONS\"" 2>/dev/null | tail -1
}
check "matrix block: not run, eligible, error, or null keeps the run eligible" "true|true|true|true" \
  "$(hwm_elig '{}')|$(hwm_elig '{"baseline_eligible":true}')|$(hwm_elig '{"error":"x"}')|$(hwm_elig '{"baseline_eligible":null}')"
check "matrix block: baseline_eligible false makes the run ineligible, and records why" "false hw_matrix_placement" "$(hwm_elig '{"points":[],"baseline_eligible":false}')"
line_of() { grep -nE -- "$1" "$RUN" | head -1 | cut -d: -f1; }
L_HWM="$(line_of '^  SERVING_HW_MATRIX_JSON="[$][(]percore_block')"; L_VETO="$(line_of '^# The matrix records its own eligibility')"
L_OUT="$(line_of '--argjson baseline_eligible "[$]BASELINE_ELIGIBLE"')"
check "the matrix verdict is read after the matrix runs and before the result is assembled" "true" \
  "$([ -n "$L_HWM" ] && [ -n "$L_VETO" ] && [ -n "$L_OUT" ] && [ "$L_HWM" -lt "$L_VETO" ] && [ "$L_VETO" -lt "$L_OUT" ] && echo true || echo false)"

echo
if [ "$FAILS" -gt 0 ]; then echo ":x: $FAILS check(s) failed" >&2; exit 1; fi
echo "all perf-cpu-topology checks passed"
