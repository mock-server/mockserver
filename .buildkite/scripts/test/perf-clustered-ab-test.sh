#!/usr/bin/env bash
# Docker-free checks for the clustered-state A/B: lib/perf-clustered-ab.sh's pair order, cpuset split
# and pinning fallbacks, cgroup cpu.stat reading, result assembly and measured verdict, the paired
# window loop against stubbed k6 runs, the wiring in perf-test-run.sh, and the compare step's
# measured gate, throttling notes and method-keyed baseline.
# Run: .buildkite/scripts/test/perf-clustered-ab-test.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env
unset BUILDKITE_BUILD_NUMBER
LIB="$REPO_ROOT/.buildkite/scripts/steps/lib/perf-clustered-ab.sh"
RUN="$REPO_ROOT/.buildkite/scripts/steps/perf-test-run.sh"
COMPARE="$REPO_ROOT/.buildkite/scripts/steps/perf-test-compare.sh"
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
WORK="$(mktemp -d "${TMPDIR:-/tmp}/perf-clustered-ab-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
# shellcheck source=../steps/lib/perf-cpu-topology.sh
. "$REPO_ROOT/.buildkite/scripts/steps/lib/perf-cpu-topology.sh"
# shellcheck source=../steps/lib/perf-clustered-ab.sh
. "$LIB"

# make_topo <root> <ncpus> <cores>: cpu i on core (i % cores), one package, one NUMA node.
make_topo() {
  local i d
  for ((i = 0; i < $2; i++)); do
    d="$1/devices/system/cpu/cpu$i/topology"; mkdir -p "$d"
    echo 0 > "$d/physical_package_id"; echo $((i % $3)) > "$d/core_id"
  done
  mkdir -p "$1/devices/system/node/node0"; echo "0-$(($2 - 1))" > "$1/devices/system/node/node0/cpulist"
}

echo "--- 1. pair order alternates within a run and with the build number"
check "even build" "control cluster|cluster control|control cluster" \
  "$(clu_pair_order 1 12)|$(clu_pair_order 2 12)|$(clu_pair_order 3 12)"
check "odd build" "cluster control|control cluster" "$(clu_pair_order 1 7)|$(clu_pair_order 2 7)"
check "no or garbage build number reads as 0" "control cluster|control cluster" "$(clu_pair_order 1 "")|$(clu_pair_order 1 abc)"

echo "--- 2. cpusets carved from the SUT cpuset"
check "0-5 in twos" "0-1 2-3 4-5" "$(clu_split_cpusets 0-5 2)"
check "a list cpuset" "0,2 4,6 8,10" "$(clu_split_cpusets 0,2,4,6,8,10 2)"
check "the first six of a wider cpuset" "0 1 2" "$(clu_split_cpusets 0-9 1)"
check "too small fails" "1" "$(clu_split_cpusets 0-4 2 >/dev/null; echo $?)"
check "a fractional width fails" "1" "$(clu_split_cpusets 0-5 1.5 >/dev/null; echo $?)"

echo "--- 3. pinning: cpuset when it can be proven disjoint, else the --cpus fallback"
make_topo "$WORK/topo12" 12 12
make_topo "$WORK/topo-siblings" 12 3
pin() { # sysfs server_cpus width -> mode|reason|ctrl|a|b
  (
    export PERF_SYSFS_ROOT="$1"
    # shellcheck disable=SC2034  # read by clu_resolve_pinning
    UPSTREAM_CPUS=6 K6_CPUS=7-11
    clu_resolve_pinning "$2" "$3" >/dev/null 2>&1
    echo "$CLU_PIN_MODE|$CLU_PIN_REASON|$CLU_CTRL_CPUS|$CLU_A_CPUS|$CLU_B_CPUS"
  )
}
check "default: three disjoint pairs from the SUT cpuset" "cpuset|server_cpuset_split|0-1|2-3|4-5" "$(pin "$WORK/topo12" 0-5 2)"
check "an unpinned host keeps --cpus" "cpu-quota|host_unpinned|||" "$(pin "$WORK/topo12" "" 2)"
check "a fractional PERF_CLUSTERED_CPUS keeps --cpus" "cpu-quota|fractional_cpus|||" "$(pin "$WORK/topo12" 0-5 1.5)"
check "a SUT cpuset too small keeps --cpus" "cpu-quota|server_cpuset_too_small|||" "$(pin "$WORK/topo12" 0-3 2)"
check "PERF_CLUSTERED_PIN=false keeps --cpus" "cpu-quota|disabled|||" "$(PERF_CLUSTERED_PIN=false pin "$WORK/topo12" 0-5 2)"
check "explicit cpusets are used" "cpuset|override|1|3|5" \
  "$(PERF_CLUSTERED_CTRL_CPUS=1 PERF_CLUSTERED_A_CPUS=3 PERF_CLUSTERED_B_CPUS=5 pin "$WORK/topo12" 0-5 2)"
check "a partial override keeps --cpus" "cpu-quota|partial_override|||" "$(PERF_CLUSTERED_A_CPUS=9 pin "$WORK/topo12" 0-5 2)"
check "nodes sharing physical cores keep --cpus" "cpu-quota|not_physically_disjoint|||" "$(pin "$WORK/topo-siblings" 0-5 2)"
check "an override overlapping k6 keeps --cpus" "cpu-quota|not_physically_disjoint|||" \
  "$(PERF_CLUSTERED_CTRL_CPUS=7 PERF_CLUSTERED_A_CPUS=8 PERF_CLUSTERED_B_CPUS=9 pin "$WORK/topo12" 0-5 2)"
# shellcheck disable=SC2034  # CLU_CPUS is read by clu_cpu_flags
flags() { ( export PERF_SYSFS_ROOT="$WORK/topo12"; CLU_PIN_MODE="$1"; CLU_CPUS=2; clu_cpu_flags "$2"; echo "${CLU_CPU_FLAGS[*]}" ); }
check "pinned: a cpuset and its NUMA node, no quota" "--cpuset-cpus=0-1 --cpuset-mems=0" "$(flags cpuset 0-1)"
check "fallback: a CFS quota" "--cpus 2" "$(flags cpu-quota "")"

echo "--- 4. cgroup cpu.stat: v2 (systemd and cgroupfs), v1, nested, and absent"
CG="$WORK/cgroup"
mkdir -p "$CG/system.slice/docker-aaa.scope" "$CG/docker/bbb" "$CG/cpu,cpuacct/docker/ccc" "$CG/kubepods.slice/x/docker-ddd.scope"
printf 'usage_usec 5000000\nuser_usec 4000000\nsystem_usec 1000000\nnr_periods 100\nnr_throttled 7\nthrottled_usec 350000\n' \
  > "$CG/system.slice/docker-aaa.scope/cpu.stat"
printf 'usage_usec 10\nnr_periods 0\nnr_throttled 0\nthrottled_usec 0\n' > "$CG/docker/bbb/cpu.stat"
printf 'nr_periods 50\nnr_throttled 5\nthrottled_time 2500000\n' > "$CG/cpu,cpuacct/docker/ccc/cpu.stat"
printf 'usage_usec 1\n' > "$CG/kubepods.slice/x/docker-ddd.scope/cpu.stat"
mkdir -p "$WORK/bin"
cat > "$WORK/bin/docker" <<'STUB'
#!/usr/bin/env bash
echo "docker $*" >> "${STUB_LOG:-/dev/null}"
if [ "$1" = inspect ] && [ "$2" = -f ]; then case "$4" in c-a) echo aaa ;; c-b) echo bbb ;; c-c) echo ccc ;; c-d) echo ddd ;; *) exit 1 ;; esac; fi
exit 0
STUB
chmod +x "$WORK/bin/docker"
stat_of() { PATH="$WORK/bin:$PATH" PERF_CGROUP_ROOT="$CG" clu_cpu_stat_json "$(PATH="$WORK/bin:$PATH" PERF_CGROUP_ROOT="$CG" clu_cpu_stat_file "$1" || true)" | jq -c .; }
check "v2 systemd scope" '{"nr_periods":100,"nr_throttled":7,"throttled_usec":350000,"usage_usec":5000000}' "$(stat_of c-a)"
check "v2 cgroupfs" '{"nr_periods":0,"nr_throttled":0,"throttled_usec":0,"usage_usec":10}' "$(stat_of c-b)"
check "v1: throttled_time ns to usec, no usage" '{"nr_periods":50,"nr_throttled":5,"throttled_usec":2500,"usage_usec":null}' "$(stat_of c-c)"
check "found by search when nested elsewhere" '{"nr_periods":null,"nr_throttled":null,"throttled_usec":null,"usage_usec":1}' "$(stat_of c-d)"
check "an unknown container reads as null" "null" "$(stat_of c-z)"
printf 'nr_periods 1x\n' > "$WORK/garbage.stat"
check "a snapshot of an unparseable cpu.stat is null" "null|null" \
  "$(CLU_STAT_FILE_control="$WORK/garbage.stat" clu_snapshot control)|$(CLU_STAT_FILE_node_a="" clu_snapshot node_a)"

echo "--- 5. assembly: median of per-pair ratios, strict nulls, and the measured verdict"
reg() { # file proto p50 p95 [err]
  jq -nc --arg p "$2" --argjson a "$3" --argjson b "$4" --argjson e "${5:-0}" '{proto: $p, behaviours: {
    ("match_" + $p): {p50_ms: $a, p95_ms: $b, p99_ms: ($b * 2), throughput_rps: 200, offered_rps: 200, error_rate: $e, sample_count: 4000},
    ("forward_" + $p): {p50_ms: 0.7, p95_ms: 2, p99_ms: 4, throughput_rps: 200, error_rate: 0, sample_count: 4000},
    ("large_10mb_" + $p): {p50_ms: null, p95_ms: null, p99_ms: null, throughput_rps: 0.1, error_rate: 0, sample_count: 3}}}' > "$1"
}
cross() { # file proto p50
  jq -nc --arg p "$2" --argjson a "$3" '{proto: $p, behaviours: {("crossing_" + $p):
    {p50_ms: $a, p95_ms: ($a * 2), p99_ms: ($a * 3), throughput_rps: 50, error_rate: 0, sample_count: 1000, crosses_network: true}}}' > "$1"
}
# Cluster p50 per pair 0.33 / 0.6 / 0.36 against a 0.3 control: per-pair ratios 1.1 / 2 / 1.2, median 1.2.
genuine() { # dir
  mkdir -p "$1"; local i c
  for i in 1 2 3; do
    c="$(echo "0.33 0.6 0.36" | cut -d' ' -f"$i")"
    reg "$1/clu-p$i-reg-control.json" clustered_control 0.3 1
    reg "$1/clu-p$i-reg-cluster.json" clustered "$c" 1."$i"
    cross "$1/clu-p$i-cross-control.json" clustered_control 0.4
    cross "$1/clu-p$i-cross-cluster.json" clustered 1.6
  done
  : > "$1/clu-throttle.ndjson"
}
CLUSTER_OK='{"member_count_node_a":2,"member_count_node_b":2,"control_member_count":1,"state_crossed_network":true,"member_count_node_a_after":2,"member_count_node_b_after":2,"control_member_count_after":1}'
META="{\"image\":\"img\",\"cluster\":$CLUSTER_OK,\"cpu_pinning\":{\"mode\":\"cpuset\"}}"
meta_with() { jq -c --argjson c "$1" '.cluster += $c' <<<"$META"; } # cluster field overrides
# A full throttle log: one line per window and container, 6 per pair.
full_throttle() { # dir pairs periods throttled
  local i k a
  for ((i = 1; i <= $2; i++)); do for k in reg cross; do for a in control cluster; do
    if [ "$a" = control ]; then tline "$i" control control "$3" "$4" "$k"
    else tline "$i" cluster node_a "$3" "$4" "$k"; tline "$i" cluster node_b "$3" 0 "$k"; fi
  done; done; done > "$1/clu-throttle.ndjson"
}
tline() { # pair arm role periods throttled [kind] [usage]
  jq -nc --argjson p "$1" --arg a "$2" --arg r "$3" --argjson n "$4" --argjson t "$5" --arg k "${6:-reg}" --argjson u "${7:-1000000}" \
    '{pair: $p, kind: $k, arm: $a, wall_s: 10,
      before: {($r): {nr_periods: 100, nr_throttled: 10, throttled_usec: 0, usage_usec: 0}},
      after: {($r): {nr_periods: (100 + $n), nr_throttled: (10 + $t), throttled_usec: ($t * 1000), usage_usec: $u}}}'
}
genuine "$WORK/g"
A="$(clu_assemble_json "$WORK/g" 3 "$META")"
S="$(jq -c .state <<<"$A")"
check "measured, no problems" "true|0" "$(jq -r '"\(.measured)|\(.problems | length)"' <<<"$S")"
check "method names the cpu mode" "paired-cpuset" "$(jq -r .method <<<"$S")"
check "meta merged" "img|2" "$(jq -r '"\(.image)|\(.cluster.member_count_node_a)"' <<<"$S")"
check "arms keyed by op" "crossing,forward,large_10mb,match" "$(jq -r '.arms | keys | join(",")' <<<"$S")"
check "p50 ratio is the median of the per-pair ratios, not one outlier pair" "1.2|[1.1,2,1.2]" \
  "$(jq -r '.arms.match | "\(.p50_ratio)|\(.p50_pair_ratios | tojson)"' <<<"$S")"
check "p95 ratio median" "1.2" "$(jq -r '.arms.match.p95_ratio' <<<"$S")"
check "absolutes are window medians" "0.3|0.36|1|1.2" \
  "$(jq -r '.arms.match | "\(.p50_control_ms)|\(.p50_clustered_ms)|\(.p95_control_ms)|\(.p95_clustered_ms)"' <<<"$S")"
check "an arm below the tail floor keeps null ratios but its throughput" "null|null|1" \
  "$(jq -r '.arms.large_10mb | "\(.p50_ratio)|\(.p95_ratio)|\(.throughput_ratio)"' <<<"$S")"
check "the crossing arm is flagged as crossing the network" "true|4|false" \
  "$(jq -r '.arms | "\(.crossing.crosses_network)|\(.crossing.p50_ratio)|\(.match.crosses_network)"' <<<"$S")"
check "behaviours keep their keys" "crossing_clustered_control,forward_clustered_control,large_10mb_clustered_control,match_clustered_control" \
  "$(jq -r '.control_behaviours | keys | join(",")' <<<"$A")"
check "behaviours: medians, summed counts, window count" "0.36|12000|3" \
  "$(jq -r '.cluster_behaviours.match_clustered | "\(.p50_ms)|\(.sample_count)|\(.windows)"' <<<"$A")"
check "behaviours: a crossing flag survives" "true" "$(jq -r '.cluster_behaviours.crossing_clustered.crosses_network' <<<"$A")"

genuine "$WORK/err"; reg "$WORK/err/clu-p2-reg-cluster.json" clustered 0.6 1.2 0.03
E="$(clu_assemble_json "$WORK/err" 3 "$META" | jq -c .state)"
check "error rate is the mean over windows" "0.01" "$(jq -r '.arms.match.clustered_error_rate' <<<"$E")"
check "  ... and one window over 1% unmeasures the run, naming it" "false|1" \
  "$(jq -r '"\(.measured)|\([.problems[] | select(test("pair 2 regression.js window on the cluster arm: match_clustered failed 0.03 of its requests .over the 1% bound."))] | length)"' <<<"$E")"
genuine "$WORK/lowerr"; reg "$WORK/lowerr/clu-p1-reg-control.json" clustered_control 0.3 1 0.01
check "exactly 1% errors is within the bound" "true" "$(clu_assemble_json "$WORK/lowerr" 3 "$META" | jq -r .state.measured)"
# Reviewer case 1: node A refuses every request fast, so the cluster looks 6.7x faster.
genuine "$WORK/allfail"
for i in 1 2 3; do jq '.behaviours |= map_values(.error_rate = 1 | if .p50_ms != null then .p50_ms = 0.045 else . end)' \
  "$WORK/g/clu-p$i-reg-cluster.json" > "$WORK/allfail/clu-p$i-reg-cluster.json"; done
F="$(clu_assemble_json "$WORK/allfail" 3 "$META" | jq -c .state)"
check "every cluster window at 100% errors: a 0.15 ratio, but not measured" "0.15|false" "$(jq -r '"\(.arms.match.p50_ratio)|\(.measured)"' <<<"$F")"
check "  ... one problem per failing arm and window" "9" "$(jq -r '[.problems[] | select(test("failed 1 of its requests"))] | length' <<<"$F")"
genuine "$WORK/noerr"; jq '.behaviours.crossing_clustered |= del(.error_rate)' "$WORK/g/clu-p1-cross-cluster.json" > "$WORK/noerr/clu-p1-cross-cluster.json"
check "a window with no error rate is not trusted" "false|1" \
  "$(clu_assemble_json "$WORK/noerr" 3 "$META" | jq -r '.state | "\(.measured)|\([.problems[] | select(test("crossing_clustered failed an unknown share"))] | length)"')"
# Reviewer case 2: node B leaves the view after the up-front probe.
L="$(clu_assemble_json "$WORK/g" 3 "$(meta_with '{"member_count_node_a_after":1,"member_count_node_b_after":0}')" | jq -c .state)"
check "a member lost during the windows: not measured, the after-view named" "false|1" \
  "$(jq -r '"\(.measured)|\([.problems[] | select(test("below two members after the windows .A 1, B 0."))] | length)"' <<<"$L")"
check "a view never re-read after the windows: not measured" "false|2" \
  "$(clu_assemble_json "$WORK/g" 3 "$(jq -c '.cluster |= del(.member_count_node_a_after, .member_count_node_b_after, .control_member_count_after)' <<<"$META")" \
     | jq -r '.state | "\(.measured)|\([.problems[] | select(test("unread"))] | length)"')"
check "a control that joined something during the windows: not measured" "false" \
  "$(clu_assemble_json "$WORK/g" 3 "$(meta_with '{"control_member_count_after":2}')" | jq -r .state.measured)"
genuine "$WORK/gap"; jq '.behaviours |= with_entries(select(.key | startswith("large_10mb") | not))' "$WORK/g/clu-p2-reg-control.json" > "$WORK/gap/clu-p2-reg-control.json"
G2="$(clu_assemble_json "$WORK/gap" 3 "$META" | jq -c .state)"
check "an optional arm missing from one window: recorded as a warning, its ratios null, still measured" "true|1|null" \
  "$(jq -r '"\(.measured)|\([.warnings[] | select(test("pair 2 regression.js window on the control arm has no large_10mb arm"))] | length)|\(.arms.large_10mb.throughput_ratio)"' <<<"$G2")"
check "a complete run has no warnings" "0" "$(jq -r '.warnings | length' <<<"$S")"
genuine "$WORK/gapreq"; jq '.behaviours |= with_entries(select(.key | startswith("forward") | not))' "$WORK/g/clu-p3-reg-cluster.json" > "$WORK/gapreq/clu-p3-reg-cluster.json"
check "a required arm missing from one window: not measured, the window named" "false|1|0" \
  "$(clu_assemble_json "$WORK/gapreq" 3 "$META" | jq -r '.state | "\(.measured)|\([.problems[] | select(test("pair 3 regression.js window on the cluster arm has no forward arm"))] | length)|\(.warnings | length)"')"
genuine "$WORK/nofwd"; for i in 1 2 3; do jq '.behaviours |= with_entries(select(.key | startswith("forward") | not))' "$WORK/g/clu-p$i-reg-cluster.json" > "$WORK/nofwd/clu-p$i-reg-cluster.json"; done
check "a required arm missing: not measured, named" "false|1" \
  "$(clu_assemble_json "$WORK/nofwd" 3 "$META" | jq -r '.state | "\(.measured)|\([.problems[] | select(test("arm forward produced no p50 ratio"))] | length)"')"

genuine "$WORK/onenull"; jq '.behaviours.match_clustered.p95_ms = null' "$WORK/g/clu-p2-reg-cluster.json" > "$WORK/onenull/clu-p2-reg-cluster.json"
O="$(clu_assemble_json "$WORK/onenull" 3 "$META" | jq -c .state)"
check "one pair without a p95 on an arm above the tail floor: no p95 ratio, p50 untouched, not measured" "null|null|1.2|false|1" \
  "$(jq -r '"\(.arms.match.p95_ratio)|\(.arms.match.p95_clustered_ms)|\(.arms.match.p50_ratio)|\(.measured)|\([.problems[] | select(test("arm match cleared the 30-sample tail floor in every window but has no p95_ratio"))] | length)"' <<<"$O")"
check "an arm below the tail floor (large_10mb) does not unmeasure the run" "3|true" \
  "$(jq -r '"\(.arms.large_10mb.min_window_samples)|\(.measured)"' <<<"$S")"

genuine "$WORK/missing"; rm "$WORK/missing/clu-p2-reg-control.json"
M="$(clu_assemble_json "$WORK/missing" 3 "$META")"
check "a missing window: not measured, the window named" "false|1" \
  "$(jq -r '.state | "\(.measured)|\([.problems[] | select(test("pair 2 regression.js window on the control arm produced no result"))] | length)"' <<<"$M")"
check "  ... and the regression arms on that side have no ratio" "null|null" \
  "$(jq -r '.state.arms.match | "\(.p50_ratio)|\(.p50_control_ms)"' <<<"$M")"
check "  ... while the crossing arm, complete, still has one" "4" "$(jq -r '.state.arms.crossing.p50_ratio' <<<"$M")"
genuine "$WORK/emptyb"; echo '{"proto":"clustered","behaviours":{}}' > "$WORK/emptyb/clu-p3-cross-cluster.json"
check "an empty behaviours block counts as no result" "false" "$(clu_assemble_json "$WORK/emptyb" 3 "$META" | jq -r .state.measured)"
genuine "$WORK/garbage"; echo 'not json' > "$WORK/garbage/clu-p1-reg-cluster.json"
check "an unparseable window counts as no result" "false" "$(clu_assemble_json "$WORK/garbage" 3 "$META" | jq -r .state.measured)"
check "a non-genuine cluster: not measured, each failure named" "false|3" \
  "$(clu_assemble_json "$WORK/g" 3 "$(meta_with '{"member_count_node_a":1,"state_crossed_network":false,"control_member_count":2}')" \
     | jq -r '.state | "\(.measured)|\(.problems | length)"')"
check "the fallback names its method" "paired-cpu-quota" \
  "$(clu_assemble_json "$WORK/g" 3 "$(jq -c '.cpu_pinning.mode = "cpu-quota"' <<<"$META")" | jq -r .state.method)"
mkdir -p "$WORK/none"
check "nothing measured at all: not measured, every window and required arm named, no ratios" "false|15|0" \
  "$(clu_assemble_json "$WORK/none" 3 "$META" | jq -r '.state | "\(.measured)|\(.problems | length)|\([.arms[] | select(.p50_ratio != null)] | length)"')"

echo "--- 6. throttling: deltas per window and container, the material flag, and absence"
genuine "$WORK/th"
full_throttle "$WORK/th" 3 0 0
T="$(clu_assemble_json "$WORK/th" 3 "$META" | jq -c .state.throttling)"
check "pinned, no quota: available, nothing throttled, not material" "true|false|0|0|18" \
  "$(jq -r '"\(.available)|\(.material)|\(.by_role.control.nr_throttled)|\(.by_role.node_a.max_window_throttled_period_pct)|\(.windows | length)"' <<<"$T")"
check "cpu use per window" "10" "$(jq -r '.windows[0].cpu_util_pct' <<<"$T")"
full_throttle "$WORK/th" 3 100 0
jq -c 'if .pair == 2 and .kind == "cross" and (.after | has("node_a")) then .after.node_a.nr_throttled += 3 else . end' \
  "$WORK/th/clu-throttle.ndjson" > "$WORK/th/t2" && mv "$WORK/th/t2" "$WORK/th/clu-throttle.ndjson"
T="$(clu_assemble_json "$WORK/th" 3 "$META" | jq -c .state.throttling)"
check "3% of one window's periods throttled: material, the worst window recorded" "true|3|3|600" \
  "$(jq -r '"\(.material)|\(.by_role.node_a.max_window_throttled_period_pct)|\(.by_role.node_a.nr_throttled)|\(.by_role.node_a.nr_periods)"' <<<"$T")"
check "the threshold is configurable" "false|5" \
  "$(PERF_CLUSTERED_THROTTLE_MATERIAL_PCT=5 clu_assemble_json "$WORK/th" 3 "$META" | jq -r '.state.throttling | "\(.material)|\(.material_threshold_pct)"')"
full_throttle "$WORK/th" 3 0 0
jq -c 'if .pair == 1 and .arm == "cluster" and .kind == "reg" then .before.node_a = null else . end' "$WORK/th/clu-throttle.ndjson" > "$WORK/th/t2" && mv "$WORK/th/t2" "$WORK/th/clu-throttle.ndjson"
check "an unreadable cpu.stat: not available, material unknown" "false|null" \
  "$(clu_assemble_json "$WORK/th" 3 "$META" | jq -r '.state.throttling | "\(.available)|\(.material)"')"
check "throttling never makes a run unmeasured" "true" "$(clu_assemble_json "$WORK/th" 3 "$META" | jq -r '.state.measured')"
full_throttle "$WORK/th" 3 0 0
head -n 17 "$WORK/th/clu-throttle.ndjson" > "$WORK/th/t2" && mv "$WORK/th/t2" "$WORK/th/clu-throttle.ndjson"
check "a window with no throttle line: not available" "false|17" \
  "$(clu_assemble_json "$WORK/th" 3 "$META" | jq -r '.state.throttling | "\(.available)|\(.windows | length)"')"

echo "--- 7. clu_run_pairs: adjacent alternating windows, warm-ups, snapshots"
loop() { # build_number [failing_out] -> stub log
  (
    export STUB_LOG="$WORK/loop.log" OUT_DIR="$WORK/loop"; : > "$STUB_LOG"; mkdir -p "$OUT_DIR"
    # shellcheck disable=SC2034  # read by the lib
    BUILDKITE_BUILD_NUMBER="$1" CLU_WARMUP=20s CLU_PAIR_WARMUP=5s CLU_A_ALIAS=clu-a FAIL_OUT="${2:-}"
    stub_k6() { echo "$1 $2 $3 $4 $5" >> "$STUB_LOG"; [ "$4" != "$FAIL_OUT" ] || return 3
      local ops='["match","forward"]'; [ "$1" = cross ] && ops='["crossing"]'
      jq -nc --arg p "$2" --argjson ops "$ops" '{proto: $p, behaviours: ([ $ops[] | {key: (. + "_" + $p), value: {p50_ms: 1, p95_ms: 2, throughput_rps: 50, error_rate: 0, sample_count: 99}} ] | from_entries)}' > "$OUT_DIR/$4"; }
    run_clu_regression() { stub_k6 reg "$@"; }
    run_clu_crossing() { stub_k6 cross "$@"; }
    clu_snapshot() { echo "{\"nr_periods\":0,\"nr_throttled\":0,\"throttled_usec\":0,\"usage_usec\":0}"; }
    clu_run_pairs 3 >/dev/null 2>"$WORK/loop.err"
    echo "orders=$CLU_PAIR_ORDERS" >> "$STUB_LOG"
  )
}
loop 4
check "even build: pair order control,cluster / cluster,control / control,cluster" "orders=control,cluster cluster,control control,cluster" "$(tail -1 "$WORK/loop.log")"
check "pair 1: regression on its first arm then its second, crossing reversed" \
  "reg clustered_control http://clu-ctrl:1080 clu-p1-reg-control.json 20s|reg clustered http://clu-a:1080 clu-p1-reg-cluster.json 20s|cross clustered http://clu-a:1080 clu-p1-cross-cluster.json 20s|cross clustered_control http://clu-ctrl:1080 clu-p1-cross-control.json 20s" \
  "$(sed -n 1,4p "$WORK/loop.log" | paste -sd'|' -)"
check "pair 2 opens on the cluster with the short warm-up" "reg clustered http://clu-a:1080 clu-p2-reg-cluster.json 5s" "$(sed -n 5p "$WORK/loop.log")"
check "twelve windows" "12" "$(grep -c '^reg \|^cross ' "$WORK/loop.log")"
check "one throttle line per window, control alone, cluster both nodes" "12|control|node_a,node_b" \
  "$(wc -l < "$WORK/loop/clu-throttle.ndjson" | tr -d ' ')|$(jq -rs '[.[] | select(.arm == "control") | .before | keys | join(",")] | unique | join(";")' "$WORK/loop/clu-throttle.ndjson")|$(jq -rs '[.[] | select(.arm == "cluster") | .after | keys | join(",")] | unique | join(";")' "$WORK/loop/clu-throttle.ndjson")"
check "the stubbed pairs assemble as measured" "true" \
  "$(clu_assemble_json "$WORK/loop" 3 "$META" | jq -r .state.measured)"
loop 5
check "odd build: the cluster opens" "orders=cluster,control control,cluster cluster,control" "$(tail -1 "$WORK/loop.log")"
loop 4 clu-p3-cross-control.json
check "a failed window is warned about, still snapshotted, and unmeasures the run" "1|12|false" \
  "$(grep -c 'pair 3 cross window on the control arm failed (exit 3)' "$WORK/loop.err")|$(wc -l < "$WORK/loop/clu-throttle.ndjson" | tr -d ' ')|$(clu_assemble_json "$WORK/loop" 3 "$META" | jq -r .state.measured)"
loop 4
check "a rerun clears the previous window files" "true" "$(clu_assemble_json "$WORK/loop" 3 "$META" | jq -r .state.measured)"

echo "--- 8. perf-test-run.sh wiring"
check "the lib is sourced" "1" "$(grep -c '^\. "\$SCRIPT_DIR/lib/perf-clustered-ab.sh"$' "$RUN" || true)"
check "no node is started with a bare --cpus" "0" "$(grep -c -- '--cpus "\$CLU_CPUS"' "$RUN" || true)"
check "each node gets its own cpuset" "3" "$(grep -cE 'start_clu "\$CLU_(CTRL|A|B)" .*"\$CLU_(CTRL|A|B)_CPUS"' "$RUN" || true)"
check "the pins come from the SUT cpuset" "1" "$(grep -c '^    clu_resolve_pinning "\${SERVER_CPUS:-}" "\$CLU_CPUS"$' "$RUN" || true)"
check "the windows run paired" "1" "$(grep -c '^    clu_run_pairs "\$CLU_PAIRS"$' "$RUN" || true)"
check "all three nodes are removed after the windows" "1" "$(grep -c '^    docker rm -f "\$CLU_CTRL" "\$CLU_A" "\$CLU_B"' "$RUN" || true)"
check "the result block comes from the assembly" "1" "$(grep -c "CLUSTERED_JSON=\"\$(jq -ce '.state'" "$RUN" || true)"
check "the windows take a warm-up argument" "2" "$(grep -cE 'K6_(REG|CLU_CROSS)_WARMUP=\$\{4:-\$CLU_WARMUP\}' "$RUN" || true)"
check "the validity check is unchanged: genuineness and a ratio, not measured" "0" \
  "$(sed -n '/add_check "clustered_metrics_present" true/,+1p' "$RUN" | grep -c measured || true)"
check "a non-default clustered cpu setting excludes the run from the baseline" "1|1" \
  "$(grep -c '|| \[ "\${PERF_CLUSTERED_CPUS:-2}" != 2 \] || \[ "\${PERF_CLUSTERED_PIN:-true}" != true \] \\$' "$RUN" || true)|$(grep -c '|| \[ -n "\${PERF_CLUSTERED_CTRL_CPUS:-}\${PERF_CLUSTERED_A_CPUS:-}\${PERF_CLUSTERED_B_CPUS:-}" \]; then$' "$RUN" || true)"
check "the views are re-read after the windows and recorded" "1|3" \
  "$(grep -c '^    A_MEMBERS_AFTER="\$(clu_members "\$A_HP")"; B_MEMBERS_AFTER="\$(clu_members "\$B_HP")"$' "$RUN" || true)|$(grep -cE '^          (member_count_node_a_after|member_count_node_b_after|control_member_count_after): \$' "$RUN" || true)"
check "the finished streaming SUT is removed before the nodes start" "1" "$(grep -c '^    docker rm -f "\$STREAM_SUT" >/dev/null 2>&1 || true$' "$RUN" || true)"
check "the lint runs this test" "1" "$(grep -c 'perf-clustered-ab-test.sh' "$REPO_ROOT/.buildkite/scripts/steps/perf-test-lint.sh" || true)"

echo "--- 9. compare: measured gate, notes, and the method-keyed baseline"
if [ "$(bash -c 'echo "${BASH_VERSINFO[0]}"')" -lt 4 ]; then
  if [ "${BUILDKITE:-}" = "true" ]; then echo "FAIL: bash on PATH is older than 4; perf-test-compare.sh cannot run" >&2; exit 1; fi
  echo "  SKIP: bash on PATH is older than 4 (perf-test-compare.sh needs mapfile)"
else
  B="$WORK/bucket"; mkdir -p "$WORK/cbin" "$B/runs/master"
  cat > "$WORK/cbin/aws" <<'STUB'
#!/usr/bin/env bash
case "$1 $2" in
  "s3 cp")
    if [[ "$4" == s3://* ]]; then k="${4#s3://*/}"; mkdir -p "$FAKE_BUCKET/$(dirname "$k")"; cp "$3" "$FAKE_BUCKET/$k"
    else cp "$FAKE_BUCKET/${3#s3://*/}" "$4"; fi ;;
  "s3 ls") p="${3#s3://*/}"; [ -d "$FAKE_BUCKET/$p" ] && (cd "$FAKE_BUCKET" && find "$p" -type f | sort | sed 's/^/2026-10-01 00:00:00 100 /') ;;
  *) exit 1 ;;
esac
STUB
  cat > "$WORK/cbin/buildkite-agent" <<'STUB'
#!/usr/bin/env bash
if [ "$1 $2" = "artifact download" ]; then [ "$3" = perf-result.json ] && cp "$HEAD_RESULT" "$4/perf-result.json" && exit 0; exit 1; fi
[ "$1" = annotate ] && cat > /dev/null
exit 0
STUB
  chmod +x "$WORK/cbin/aws" "$WORK/cbin/buildkite-agent"
  # Legacy baseline: no method, so single-window-cpu-quota; wildly throttled clustered tails.
  LEGACY_STATE="$(jq -c '.state | del(.method, .measured, .problems, .throttling) | .arms.match.p95_ratio = 5 | .arms.match.p50_ratio = 1.05' <<<"$A")"
  result() { # timestamp state_json control_behaviours cluster_behaviours
    jq -nc --arg ts "$1" --argjson cs "$2" --argjson cb "$3" --argjson kb "$4" '{
      schema_version: 3, commit: "0123456789abcdef0123456789abcdef01234567", branch: "master", timestamp_utc: $ts,
      agent: {instance_type: "c5.12xlarge", instance_type_source: "observed", queue: "perf"},
      config: {image_stale: false, config_profile: "default", rig_profile: "default", jvm_diagnostics: "standard"},
      validity: {valid: true, checks: [{name: "fixture", ok: true, detail: ""}]}, baseline_eligible: true,
      behaviours: ({match_http: {p95_ms: 1.5, p99_ms: 3, error_rate: 0}} + $cb + $kb),
      clustered_state: $cs, clustered_attempted: true, clustered_skip_reason: ""}'
  }
  CB="$(jq -c .control_behaviours <<<"$A")"; KB="$(jq -c .cluster_behaviours <<<"$A")"
  LEGACY_KB="$(jq -c '.match_clustered.p99_ms = 200' <<<"$KB")"
  for i in 1 2 3 4 5 6; do
    result "2026-09-0${i}T04:00:00Z" "$LEGACY_STATE" "$CB" "$LEGACY_KB" > "$B/runs/master/2026-09-0${i}T04-00-00Z__000000000${i}.json"
  done
  compare() { # name head_file
    local rc=0
    env -i PATH="$WORK/cbin:$PATH" HOME="${HOME:-/tmp}" TMPDIR="$WORK" FAKE_BUCKET="$B" HEAD_RESULT="$2" \
      PERF_BUDGETS_COMMIT=fixture bash "$COMPARE" >"$WORK/$1.log" 2>&1 || rc=$?
    echo "$rc"
  }
  result 2026-10-01T04:00:00Z "$S" "$CB" "$KB" > "$WORK/head-new.json"
  check "new method against a legacy baseline: exit 0" "0" "$(compare new "$WORK/head-new.json")"
  check "  ... every metric has a budget" "0" "$(grep -c 'Perf budget INCOMPLETE' "$WORK/new.log" || true)"
  check "  ... the ratio rows are present but new, not compared with the throttled history" "1" \
    "$(grep -cE '^\| clustered_state\.match\.p95_ratio \| 1\.2 \| .*:new: new' "$WORK/new.log" || true)"
  check "  ... the clustered behaviours are new too" "1" "$(grep -cE '^\| match_clustered\.p99_ms \| .*:new: new' "$WORK/new.log" || true)"
  check "  ... the main behaviours keep their history" "1" "$(grep -cE '^\| match_http\.p95_ms \| 1\.5 \| 1\.5 \|' "$WORK/new.log" || true)"
  check "  ... the reset is announced" "1" "$(grep -c 'Clustered A/B baseline reset — method changed.*paired-cpuset.*6 baseline run' "$WORK/new.log" || true)"
  for i in 1 2 3 4 5 6; do
    result "2026-09-1${i}T04:00:00Z" "$S" "$CB" "$KB" > "$B/runs/master/2026-09-1${i}T04-00-00Z__000000001${i}.json"
  done
  result 2026-10-02T04:00:00Z "$S" "$CB" "$KB" > "$WORK/head-same.json"
  check "same method with enough history: compared, ok" "0|1" \
    "$(compare same "$WORK/head-same.json")|$(grep -cE '^\| clustered_state\.match\.p95_ratio \| 1\.2 \| 1\.2 \| [0-9.]+ \| notify-only \| :white_check_mark: ok \|$' "$WORK/same.log" || true)"
  result 2026-10-03T04:00:00Z "$(jq -c '.state' <<<"$M")" "$CB" "$KB" > "$WORK/head-unmeasured.json"
  check "not measured: exit 0, no clustered_state rows, a warning naming the window" "0|0|1" \
    "$(compare unmeasured "$WORK/head-unmeasured.json")|$(grep -c '^| clustered_state\.' "$WORK/unmeasured.log" || true)|$(grep -c 'Clustered state A/B did NOT measure this build.*pair 2 regression.js window on the control arm' "$WORK/unmeasured.log" || true)"
  result 2026-10-04T04:00:00Z "$(jq -c '.throttling = {available: true, material: true, material_threshold_pct: 1, by_role: {node_a: {max_window_throttled_period_pct: 3}}}' <<<"$S")" "$CB" "$KB" > "$WORK/head-throttled.json"
  check "material throttling: still compared, and a warning" "0|1|1" \
    "$(compare throttled "$WORK/head-throttled.json")|$(grep -c '^| clustered_state\.match\.p95_ratio' "$WORK/throttled.log" || true)|$(grep -c 'containers were CPU-throttled.*node_a 3%' "$WORK/throttled.log" || true)"
  result 2026-10-06T04:00:00Z "$G2" "$CB" "$KB" > "$WORK/head-gap.json"
  check "an optional arm missing from a window: compared, and noted" "0|1" \
    "$(compare gap "$WORK/head-gap.json")|$(grep -c 'arms missing from some windows.*no large_10mb arm' "$WORK/gap.log" || true)"
  result 2026-10-05T04:00:00Z "$(jq -c '.throttling = {available: false, material: null}' <<<"$S")" "$CB" "$KB" > "$WORK/head-nothrottle.json"
  check "throttling unreadable: an information note" "0|1" \
    "$(compare nothrottle "$WORK/head-nothrottle.json")|$(grep -c 'Clustered A/B throttling not recorded' "$WORK/nothrottle.log" || true)"
fi

if [ "$FAILS" -gt 0 ]; then echo "FAILED: $FAILS check(s)" >&2; exit 1; fi
echo "all clustered A/B checks passed"
