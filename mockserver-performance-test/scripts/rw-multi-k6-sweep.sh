#!/usr/bin/env bash
set -euo pipefail
# ERR fires only for top-level commands (no errtrace: it misfires inside $(...)). The EXIT trap
# reads $BASH_COMMAND first: the command running when it exited (for a pipeline, its last stage).
LAST_ERR=""
trap 'LAST_ERR="exit $? at line $LINENO: $BASH_COMMAND"; echo ":x: rw-multi-k6: failed ($LAST_ERR)" >&2' ERR

# rw-multi-k6-sweep.sh [out.json] — the sweep.js ladder from N independent k6 processes
# started at one instant, merged in Prometheus (native histograms). Opt-in; its perf-xl
# ceiling is what the page headline switches to. Design, env knobs and degrade tests:
# docs/code/performance-measurement.md (`rw-multi-k6-sweep.sh` section).
# Invariants: every gate is fail-closed (a failed check -> "valid": false, exit 2), and the
# healthy ceiling and rig validity come from the shared libs, never a copy of their rules.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${PERF_RW_REPO_ROOT:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
LIB_DIR="$REPO_ROOT/.buildkite/scripts/steps/lib"
FIGURES_JQ="$LIB_DIR/perf-website-figures.jq"
CROSS_JQ="$LIB_DIR/perf-rw-cross-check.jq"
for lib in perf-cpu-topology.sh perf-sweep-window.sh perf-derive-saturation.sh perf-k6-interrupted.sh perf-k6-runtime.sh perf-tail-instrument.sh; do
  if [ ! -r "$LIB_DIR/$lib" ]; then
    echo ":x: $LIB_DIR/$lib not found — refusing to run without the shared guard" >&2
    exit 1
  fi
  # shellcheck source=/dev/null
  . "$LIB_DIR/$lib"
done
for f in "$FIGURES_JQ" "$CROSS_JQ"; do [ -r "$f" ] || { echo ":x: $f not found" >&2; exit 1; }; done
[ -r "$LIB_DIR/perf-sut-recvq.sh" ] || { echo ":x: $LIB_DIR/perf-sut-recvq.sh not found" >&2; exit 1; }
# shellcheck source=/dev/null
. "$LIB_DIR/perf-sut-recvq.sh"

OUT_FILE="${1:-/dev/stdout}"

# --- inputs ------------------------------------------------------------------
MOCKSERVER_IMAGE="${PERF_RW_IMAGE:-${MOCKSERVER_IMAGE:-mockserver/mockserver:mockserver-snapshot-graaljs}}"
K6_IMAGE="${PERF_RW_K6_IMAGE:-grafana/k6:1.7.1@sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f}"
PROM_IMAGE="${PERF_RW_PROM_IMAGE:-prom/prometheus:v3.1.0@sha256:6559acbd5d770b15bb3c954629ce190ac3cbbdb2b7f1c30f0385c4e05104e218}"
K6_DIR="$REPO_ROOT/mockserver-performance-test/k6"
HOST_CORES="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 0)"

# Own ladder, not the published one: split over N processes the client knee moves up, so it
# keeps the published rungs to 48k (comparable rung for rung) and continues past the SUT's
# projected CPU ceiling (docs/code/performance-measurement.md, "Ladder").
DEFAULT_RATES="500,1000,2000,4000,8000,16000,24000,32000,36000,40000,44000,48000,56000,64000,72000,80000,96000,112000,128000"
# Two or more sockets and NUMA nodes (perf-xl): k6 gets a socket to itself and is not the
# limit at 128k, so the ladder steps 8k from 96k to 160k to resolve the ceiling there.
MULTI_SOCKET_RATES="500,1000,2000,4000,8000,16000,24000,32000,36000,40000,44000,48000,56000,64000,72000,80000,96000,104000,112000,120000,128000,136000,144000,152000,160000"
numa_map_prime
LADDER_PROFILE=default
if [ "$(numa_package_count)" -ge 2 ] && [ "$(numa_node_count)" -ge 2 ]; then LADDER_PROFILE=multi_socket; fi
if [ -n "${PERF_RW_RATES:-}" ]; then RATES="$PERF_RW_RATES"; RATES_SOURCE="env"
elif [ "$LADDER_PROFILE" = multi_socket ]; then RATES="$MULTI_SOCKET_RATES"; RATES_SOURCE=default
else RATES="$DEFAULT_RATES"; RATES_SOURCE=default; fi
SWEEP_STEP="${PERF_RW_STEP:-15s}"
SWEEP_GAP="${PERF_RW_GAP:-5s}"
SETTLE_S="${PERF_RW_SETTLE_S:-3}"
PUSH_S="${PERF_RW_PUSH_INTERVAL_S:-1}"
QUIET_S="${PERF_RW_QUIET_S:-$(( PUSH_S * 2 > 5 ? PUSH_S * 2 : 5 ))}"
START_LEAD_S="${PERF_RW_START_LEAD_S:-20}"
MAX_SKEW_MS="${PERF_RW_MAX_SKEW_MS:-100}"
# Slack on the cut-excess bound (requests between a settle boundary and its cut must not
# exceed the offered rate over 2 push intervals).
WINDOW_TOL="${PERF_RW_WINDOW_TOL:-0.05}"
WINDOW_MODE="${PERF_RW_WINDOW_MODE:-wallclock}"
VU_DIAGNOSTICS="${PERF_RW_VU_DIAGNOSTICS:-true}"
# Report-only: Go's GC trace in each k6 log (GODEBUG=gctrace=1), summarised per rung as .k6_gc.
K6_GCTRACE="${PERF_RW_K6_GCTRACE:-true}"
case "$K6_GCTRACE" in true|false) ;; *) echo ":x: PERF_RW_K6_GCTRACE must be true or false" >&2; exit 2 ;; esac
# Report-only: the SUT's socket receive queues over the main ladder (lib/perf-sut-recvq.sh).
SUT_RECVQ="${PERF_RW_SUT_RECVQ:-true}"
case "$SUT_RECVQ" in true|false) ;; *) echo ":x: PERF_RW_SUT_RECVQ must be true or false" >&2; exit 2 ;; esac
SUT_RECVQ_INTERVAL_S="${PERF_RW_SUT_RECVQ_INTERVAL_S:-0.1}"
[[ "$SUT_RECVQ_INTERVAL_S" =~ ^[0-9]+(\.[0-9]+)?$ ]] && awk -v i="$SUT_RECVQ_INTERVAL_S" 'BEGIN { exit !(i >= 0.02 && i <= 10) }' \
  || { echo ":x: PERF_RW_SUT_RECVQ_INTERVAL_S must be a number of seconds from 0.02 to 10" >&2; exit 2; }
# Go GC knobs passed to every measured k6 process as GOGC / GOMEMLIMIT. Unset GOMEMLIMIT is derived
# from the k6 processes' NUMA node memory, else the Docker host's, once N is known
# (lib/perf-k6-runtime.sh); it bounds the heap that a high GOGC lets grow. Go's own defaults are
# PERF_RW_K6_GOGC=100 and PERF_RW_K6_GOMEMLIMIT=off. The hardware matrix (lib/perf-percore.sh) pins
# 400, the previous default, so its baseline signature is unchanged.
K6_GOGC="${PERF_RW_K6_GOGC:-1600}"
K6_GOMEMLIMIT="${PERF_RW_K6_GOMEMLIMIT:-}"
if [ -n "$K6_GOGC" ] && ! [[ "$K6_GOGC" =~ ^(off|[0-9]+)$ ]]; then
  echo ":x: PERF_RW_K6_GOGC='$K6_GOGC' must be a whole percentage or off (Go would silently use 100)" >&2; exit 2
fi
if [ -n "$K6_GOMEMLIMIT" ] && ! [[ "$K6_GOMEMLIMIT" =~ ^(off|[0-9]+(B|KiB|MiB|GiB|TiB)?)$ ]]; then
  echo ":x: PERF_RW_K6_GOMEMLIMIT='$K6_GOMEMLIMIT' must be off or bytes with an optional B/KiB/MiB/GiB/TiB suffix (e.g. 8GiB)" >&2; exit 2
fi
if [ "$K6_GOGC" = off ] && [ "$K6_GOMEMLIMIT" = off ]; then
  echo ":x: PERF_RW_K6_GOGC=off needs a GOMEMLIMIT (leave PERF_RW_K6_GOMEMLIMIT unset for the derived one): with both off Go never collects" >&2; exit 2
fi
# Report-only tail attribution files: each k6 process's per-second series and GC trace, and
# host kernel counters sampled over the main ladder (lib/perf-tail-instrument.sh). Never a gate.
TAIL_INSTRUMENT="${PERF_RW_TAIL_INSTRUMENT:-true}"
case "$TAIL_INSTRUMENT" in true|false) ;; *) echo ":x: PERF_RW_TAIL_INSTRUMENT must be true or false" >&2; exit 2 ;; esac
HKS_INTERVAL_S="${PERF_RW_HOST_SAMPLER_INTERVAL_S:-1}"
if ! [[ "$HKS_INTERVAL_S" =~ ^[0-9]+(\.[0-9]+)?$ ]] || ! awk -v v="$HKS_INTERVAL_S" 'BEGIN{exit !(v+0 >= 0.1)}'; then
  echo ":x: PERF_RW_HOST_SAMPLER_INTERVAL_S='$HKS_INTERVAL_S' must be a number of seconds of at least 0.1" >&2; exit 2
fi
HKS_MAX_BYTES="${PERF_RW_HOST_SAMPLER_MAX_BYTES:-33554432}"
[[ "$HKS_MAX_BYTES" =~ ^[1-9][0-9]{3,9}$ ]] || { echo ":x: PERF_RW_HOST_SAMPLER_MAX_BYTES='$HKS_MAX_BYTES' must be a whole number of bytes from 1000" >&2; exit 2; }
ACCOUNT_TOL="${PERF_RW_ACCOUNT_TOL:-0}"
WARMUP_RATE="${PERF_RW_WARMUP_RATE:-2000}"
WARMUP_DURATION="${PERF_RW_WARMUP_DURATION:-10s}"
SERVER_MEMORY="${PERF_RW_MEMORY:-1g}"
DEGRADE="${PERF_RW_DEGRADE:-}"
# Test hook for the cut gates, on one process: "<proc>:query" corrupts its cut query;
# "<proc>:empty" moves its boundary past every sample (a null cut, no failure); "<proc>:stale"
# takes the sample 3 pushes before the cut as the last pre-boundary one (skipped pushes, must
# pass); "<proc>:late" cuts at the 4th sample at or after the boundary (must fail).
CUT_FAULT="${PERF_RW_TEST_CUT_FAULT:-}"
case "$CUT_FAULT" in
  ""|?*:query|?*:empty|?*:stale|?*:late) ;;
  *) echo ":x: PERF_RW_TEST_CUT_FAULT='$CUT_FAULT' must be <proc>:query, <proc>:empty, <proc>:stale or <proc>:late" >&2; exit 2 ;;
esac
SOFT_STEPS="xcheck_phase merge_main sweep_json saturation headline headline_rule per_process cross_check"
if [ -n "${PERF_RW_TEST_NULL_RUNG:-}" ]; then
  case ",$RATES," in
    *",$PERF_RW_TEST_NULL_RUNG,"*) ;;
    *) echo ":x: PERF_RW_TEST_NULL_RUNG='$PERF_RW_TEST_NULL_RUNG' is not a rung of PERF_RW_RATES ($RATES)" >&2; exit 2 ;;
  esac
fi
case "${PERF_RW_TEST_ZERO_TAIL:-}" in
  ""|true) ;;
  *) echo ":x: PERF_RW_TEST_ZERO_TAIL='$PERF_RW_TEST_ZERO_TAIL' must be empty or true" >&2; exit 2 ;;
esac
case "${PERF_RW_TEST_RESOLVE_ONLY:-}" in
  ""|true) ;;
  *) echo ":x: PERF_RW_TEST_RESOLVE_ONLY='$PERF_RW_TEST_RESOLVE_ONLY' must be empty or true" >&2; exit 2 ;;
esac
# It prints and starts nothing, so with an output file it would leave a real run's result unwritten.
if [ "${PERF_RW_TEST_RESOLVE_ONLY:-}" = true ] && [ "$OUT_FILE" != /dev/stdout ]; then
  echo ":x: PERF_RW_TEST_RESOLVE_ONLY=true takes no output file (got '$OUT_FILE'): it measures nothing" >&2; exit 2
fi
case "${PERF_RW_TEST_PLACEMENT_ONLY:-}" in
  ""|true) ;;
  *) echo ":x: PERF_RW_TEST_PLACEMENT_ONLY='$PERF_RW_TEST_PLACEMENT_ONLY' must be empty or true" >&2; exit 2 ;;
esac
if [ "${PERF_RW_TEST_PLACEMENT_ONLY:-}" = true ] && [ "$OUT_FILE" != /dev/stdout ]; then
  echo ":x: PERF_RW_TEST_PLACEMENT_ONLY=true takes no output file (got '$OUT_FILE'): it measures nothing" >&2; exit 2
fi
if [ -n "${PERF_RW_TEST_FAIL_STEP:-}" ]; then
  case " $SOFT_STEPS " in
    *" $PERF_RW_TEST_FAIL_STEP "*) ;;
    *) echo ":x: PERF_RW_TEST_FAIL_STEP='$PERF_RW_TEST_FAIL_STEP' is not a step; valid: $SOFT_STEPS" >&2; exit 2 ;;
  esac
fi

# The healthy ceiling's p99 bound (ms), applied to this arm only.
P99_MAX_MS="${PERF_RW_P99_MAX_MS:-10}"
if ! [[ "$P99_MAX_MS" =~ ^[0-9]+(\.[0-9]+)?$ ]] || ! awk -v v="$P99_MAX_MS" 'BEGIN{exit !(v+0 > 0)}'; then
  echo ":x: PERF_RW_P99_MAX_MS='$P99_MAX_MS' must be a number of milliseconds above 0" >&2; exit 2
fi
# The GC-masked figure (.gc_masked): a rung with fewer quiet seconds than this states no figure.
GC_MASK_MIN_QUIET_S="${PERF_RW_GC_MASK_MIN_QUIET_S:-3}"
[[ "$GC_MASK_MIN_QUIET_S" =~ ^[1-9][0-9]{0,3}$ ]] || { echo ":x: PERF_RW_GC_MASK_MIN_QUIET_S='$GC_MASK_MIN_QUIET_S' must be a whole number of seconds from 1" >&2; exit 2; }
# Which p99 the headline's bound reads: the GC-masked one, or the whole rung's. The hardware
# matrix passes unmasked_p99, as its k6 runs at GOGC 400, where too few seconds are quiet.
HEADLINE_RULE="${PERF_RW_HEADLINE_RULE:-gc_masked_p99}"
case "$HEADLINE_RULE" in
  gc_masked_p99|unmasked_p99) ;;
  *) echo ":x: PERF_RW_HEADLINE_RULE='$HEADLINE_RULE' must be gc_masked_p99 or unmasked_p99" >&2; exit 2 ;;
esac

# Rig-validity tunables — the same names and defaults perf-test-run.sh passes to
# derive_saturation, so both methods judge a rung by one rule.
# shellcheck disable=SC2034  # SWEEP_* are read by lib/perf-derive-saturation.sh
SWEEP_ERR_EPS="${PERF_SWEEP_ERROR_EPS:-0.01}"
# shellcheck disable=SC2034
SWEEP_DROP_TOL="${PERF_SWEEP_DROP_TOL:-0.01}"
# shellcheck disable=SC2034
SWEEP_OCC_KNEE="${PERF_SWEEP_OCC_KNEE:-0.80}"
KEEP="${PERF_RW_KEEP:-0.95}"

# Cross-check: one k6 in the published summary mode (vu_tag window, full summary)
# that ALSO remote-writes, on rungs where a single process is not saturated.
XCHECK="${PERF_RW_XCHECK:-true}"
# multi_socket: one k6 on a whole NUMA node holds its latency floor through 40k, so cross_run has
# rungs at 8k-10k per main process; 40k is also the per-process rate its VU pool is sized for.
XCHECK_MAX_RPS_DEFAULT=24000
if [ "$LADDER_PROFILE" = multi_socket ]; then XCHECK_MAX_RPS_DEFAULT=40000; fi
XCHECK_MAX_RPS="${PERF_RW_XCHECK_MAX_RPS:-$XCHECK_MAX_RPS_DEFAULT}"
# Same-request tolerances: native histograms (bucket factor 1.1) resolve a quantile to ~5%.
# The time cut is held to by_tag from the same cut on (lib/perf-rw-cross-check.jq), at the same
# tolerances. The absolute floor only matters below ~0.05 ms.
XTOL_P50="${PERF_RW_XCHECK_TOL_P50:-0.10}"
XTOL_P95="${PERF_RW_XCHECK_TOL_P95:-0.10}"
XTOL_P99="${PERF_RW_XCHECK_TOL_P99:-0.15}"
XTOL_ABS_MS="${PERF_RW_XCHECK_TOL_ABS_MS:-0.005}"
# Cross-run tolerances (two separate runs of the same rung, so run-to-run noise).
RTOL_ACHIEVED="${PERF_RW_CROSSRUN_TOL_ACHIEVED:-0.02}"
RTOL_P50="${PERF_RW_CROSSRUN_TOL_P50:-0.20}"
RTOL_P95="${PERF_RW_CROSSRUN_TOL_P95:-0.35}"
RTOL_P99="${PERF_RW_CROSSRUN_TOL_P99:-0.60}"
# cross_run judges only rungs where each main-phase k6 offers at least this rate (0: every rung);
# below it a process's own rate moves latency, so the arms are not the same regime.
RTOL_MIN_PP="${PERF_RW_CROSSRUN_MIN_PER_PROC_RPS:-8000}"
[[ "$RTOL_MIN_PP" =~ ^(0|[1-9][0-9]{0,6})$ ]] || { echo ":x: PERF_RW_CROSSRUN_MIN_PER_PROC_RPS='$RTOL_MIN_PP' must be a whole number of requests per second from 0" >&2; exit 2; }

# Target: launch a pinned SUT (default) or drive an existing one on an existing
# docker network (perf-test-run.sh passes its main SUT, so both methods measure it).
NETWORK_IN="${PERF_RW_NETWORK:-}"
TARGET_URL="${PERF_RW_TARGET_URL:-}"
SUT_CONTAINER="${PERF_RW_SUT_CONTAINER:-}"

# Placement (docs/code/performance-measurement.md, "Placement"). One NUMA node: fixed strings; on
# the c5.12xlarge (siblings N, N+24) SUT on cores 0-5 (siblings idle), upstream 6, Prometheus 23,
# four k6 processes on four whole cores each across 7-22; smaller hosts are proportional. Two or
# more nodes (perf-xl): numa_split_layout, SUT/upstream/Prometheus on node 0 and k6 on node 1.
K6_NUMA_NODE="${PERF_K6_NUMA_NODE:-other}"
case "$K6_NUMA_NODE" in other|same) ;; *) echo ":x: PERF_K6_NUMA_NODE='$K6_NUMA_NODE' must be other or same" >&2; exit 2 ;; esac
HOST_CORES="${PERF_TEST_HOST_CORES:-$HOST_CORES}"
NUMA_LAYOUT=""; _lrc=0
NUMA_LAYOUT="$(numa_split_layout 6 "${PERF_RW_PROCS:-4}" "$K6_NUMA_NODE" "${PERF_RW_K6_CORES_PER_PROC:-}")" || _lrc=$?
if [ "$_lrc" -eq 0 ]; then
  DEF_SERVER="$(layout_value server "$NUMA_LAYOUT")"; DEF_PROM="$(layout_value prometheus "$NUMA_LAYOUT")"
  DEF_K6="$(layout_value k6 "$NUMA_LAYOUT")"; DEF_UPSTREAM="$(layout_value upstream "$NUMA_LAYOUT")"
  PLACEMENT_LAYOUT="$([ "$K6_NUMA_NODE" = same ] && echo numa_same_node || echo numa_split)"
elif [ "$_lrc" -ne 1 ] && { [ -z "${PERF_RW_SERVER_CPUS:-}" ] || [ -z "${PERF_RW_PROM_CPUS:-}" ] || [ -z "${PERF_RW_K6_CPUSETS:-}" ]; }; then
  echo ":x: the NUMA layout does not fit this host (above); set PERF_RW_SERVER_CPUS, PERF_RW_PROM_CPUS and PERF_RW_K6_CPUSETS" >&2
  exit 1
elif [ "$HOST_CORES" -ge 48 ]; then
  DEF_SERVER="0-5"; DEF_PROM="23,47"; DEF_UPSTREAM="6"
  DEF_K6="7-10,31-34;11-14,35-38;15-18,39-42;19-22,43-46"
else
  DEF_SERVER="0-3"; DEF_PROM="4"; DEF_UPSTREAM=""
  _n="${PERF_RW_PROCS:-3}"; _avail=$(( HOST_CORES - 6 )); _w=$(( _avail / _n )); [ "$_w" -lt 1 ] && _w=1
  DEF_K6=""
  for ((i=0;i<_n;i++)); do
    _s=$(( 5 + i * _w )); _e=$(( _s + _w - 1 ))
    DEF_K6="${DEF_K6:+$DEF_K6;}$([ "$_w" -eq 1 ] && echo "$_s" || echo "$_s-$_e")"
  done
fi
if [ -z "${PLACEMENT_LAYOUT:-}" ]; then
  case "$(numa_node_count)" in 0) PLACEMENT_LAYOUT=topology_unknown ;; 1) PLACEMENT_LAYOUT=single_node ;; *) PLACEMENT_LAYOUT=explicit ;; esac
fi
[ -n "${PERF_RW_K6_CPUSETS:-}" ] && PLACEMENT_LAYOUT=explicit
SERVER_CPUS="${PERF_RW_SERVER_CPUS:-$DEF_SERVER}"
PROM_CPUS="${PERF_RW_PROM_CPUS:-$DEF_PROM}"
# Another container left running on the host (perf-test-run.sh's upstream), proven disjoint too.
UPSTREAM_CPUS="${PERF_RW_UPSTREAM_CPUS:-}"
IFS=';' read -ra K6_SETS <<< "${PERF_RW_K6_CPUSETS:-$DEF_K6}"
N="${PERF_RW_PROCS:-${#K6_SETS[@]}}"
if [ "$N" -lt 1 ] || [ "$N" -gt "${#K6_SETS[@]}" ]; then
  echo ":x: PERF_RW_PROCS=$N but only ${#K6_SETS[@]} k6 cpuset(s) given (PERF_RW_K6_CPUSETS, ';'-separated)" >&2
  exit 1
fi
K6_SETS=("${K6_SETS[@]:0:$N}")
XCHECK_CPUS="${PERF_RW_XCHECK_CPUS:-$(IFS=','; echo "${K6_SETS[*]}")}"

# sweep.js gives each rung a pool of ceil(rate x 0.08) VUs per process, capped at its 2,048 default.
# Where the client has a socket of its own the cap would bind before k6's CPU does, so the arm lifts
# it to the top rung's own pool there; elsewhere k6 runs out of CPU first and sweep.js keeps 2,048.
RW_VUS_PER_KRPS=80 # sweep.js's K6_SWEEP_VUS_PER_KRPS default (lib/config.js)
RW_SWEEP_VU_CEILING=2048 # sweep.js's K6_SWEEP_VU_CEILING default
K6_VU_CEILING="${PERF_RW_K6_VU_CEILING:-}"; K6_VU_CEILING_SOURCE="env"; K6_VU_CEILING_BASIS=""
if [ -n "$K6_VU_CEILING" ] && ! [[ "$K6_VU_CEILING" =~ ^[1-9][0-9]{0,5}$ ]]; then
  echo ":x: PERF_RW_K6_VU_CEILING='$K6_VU_CEILING' must be a whole number of VUs above 0" >&2; exit 2
fi
if [ -z "$K6_VU_CEILING" ]; then
  K6_VU_CEILING_SOURCE="sweep.js default"
  _top_pp=0
  IFS=',' read -ra _rates <<< "$RATES"
  for _a in "${_rates[@]}"; do
    if [[ "$_a" =~ ^[0-9]+$ ]] && [ $(( 10#$_a / N )) -gt "$_top_pp" ]; then _top_pp=$(( 10#$_a / N )); fi
  done
  _sized=$(( (_top_pp * RW_VUS_PER_KRPS + 999) / 1000 ))
  if [ "$LADDER_PROFILE" = multi_socket ] && [ "$_sized" -gt "$RW_SWEEP_VU_CEILING" ]; then
    K6_VU_CEILING="$_sized"; K6_VU_CEILING_SOURCE=derived
    K6_VU_CEILING_BASIS="{\"top_per_process_rps\":$_top_pp,\"vus_per_krps\":$RW_VUS_PER_KRPS}"
  fi
fi

# --- validation --------------------------------------------------------------
to_secs() {
  awk -v s="$1" 'BEGIN{ t=0; n="";
    for(i=1;i<=length(s);i++){c=substr(s,i,1);
      if(c ~ /[0-9]/){n=n c}
      else{v=n+0; n="";
        if(c=="m" && substr(s,i+1,1)=="s"){t+=v/1000; i++}
        else if(c=="s")t+=v; else if(c=="m")t+=v*60; else if(c=="h")t+=v*3600}}
    printf "%d", t}'
}
STEP_S="$(to_secs "$SWEEP_STEP")"
GAP_S="$(to_secs "$SWEEP_GAP")"
# Each rung's gracefulStop; unset = the gap (min 1s), k6's own default is 30s. At or below the gap,
# rung VU reservations stop overlapping, so k6 initialises the largest pool, not the sum of ~3
# adjacent ones; a request cut off at gracefulStop is in no count (rw_no_interrupted_iterations).
K6_GRACEFUL_STOP="${PERF_RW_K6_GRACEFUL_STOP:-$(k6_graceful_stop_default "$GAP_S")}"
if ! [[ "$K6_GRACEFUL_STOP" =~ ^([1-9][0-9]*s|[1-9][0-9]{3,}ms)$ ]]; then
  echo ":x: PERF_RW_K6_GRACEFUL_STOP='$K6_GRACEFUL_STOP' must be whole s or ms of at least 1s (e.g. 5s)" >&2; exit 2
fi
K6_GOMEMLIMIT_BASIS=""
k6_runtime_json() { # the result's .config.k6_runtime
  jq -nc --arg gogc "$K6_GOGC" --arg gomem "$K6_GOMEMLIMIT" --arg gstop "$K6_GRACEFUL_STOP" \
    --arg basis "$K6_GOMEMLIMIT_BASIS" --arg s_gogc "${PERF_RW_K6_GOGC:+env}" \
    --arg s_gomem "${PERF_RW_K6_GOMEMLIMIT:+env}" --arg s_gstop "${PERF_RW_K6_GRACEFUL_STOP:+env}" \
    --arg vuc "${K6_VU_CEILING:-}" --arg s_vuc "${K6_VU_CEILING_SOURCE:-}" --arg vbasis "${K6_VU_CEILING_BASIS:-}" \
    --argjson swvuc "$RW_SWEEP_VU_CEILING" '
    def v: if . == "" then null else . end;
    {gogc:($gogc | v), gomemlimit:($gomem | v), graceful_stop:($gstop | v),
     vu_ceiling:(if $vuc == "" then $swvuc else ($vuc | tonumber) end),
     source:{gogc:($s_gogc | if . == "" then "default" else . end),
             gomemlimit:($s_gomem | if . == "" then "derived" else . end),
             graceful_stop:($s_gstop | if . == "" then "default (the gap)" else . end),
             vu_ceiling:($s_vuc | v)},
     gomemlimit_basis:(if $basis == "" then null else ($basis | fromjson) end),
     vu_ceiling_basis:(if $vbasis == "" then null else ($vbasis | fromjson) end),
     note:"applied to every measured k6 process (xcheck and main phases); Go and k6 defaults are GOGC 100, GOMEMLIMIT off and gracefulStop 30s; vu_ceiling caps each rung pool of ceil(rate x 0.08) VUs per process; null = not resolved before an abort"}'
}
# The result's .method.ab: the opt-in A/B knobs set (null = not set). Any of them makes the run a trial,
# never a counting run (docs/code/performance-measurement.md, "Tail attribution files").
ab_json() {
  jq -nc --arg numa "$K6_NUMA_NODE" --arg gogc "${PERF_RW_K6_GOGC:-}" --arg gomem "${PERF_RW_K6_GOMEMLIMIT:-}" \
    --arg cpp "${PERF_RW_K6_CORES_PER_PROC:-}" --arg vuc "${PERF_RW_K6_VU_CEILING:-}" \
    --arg p99max "${PERF_RW_P99_MAX_MS:-}" --arg minq "${PERF_RW_GC_MASK_MIN_QUIET_S:-}" '
    def v: if . == "" then null else . end;
    {k6_numa_node:(if $numa == "same" then $numa else null end), k6_gogc:($gogc | v), k6_gomemlimit:($gomem | v),
     k6_cores_per_proc:($cpp | v), k6_vu_ceiling:($vuc | v), p99_max_ms:($p99max | v), gc_mask_min_quiet_s:($minq | v)}
    | . + {trial:any(.[]; . != null)}'
}
case "$WINDOW_MODE" in wallclock|vu_tag) ;; *) echo ":x: PERF_RW_WINDOW_MODE must be wallclock or vu_tag" >&2; exit 1 ;; esac
[[ "$PUSH_S" =~ ^[1-9][0-9]*$ ]] || { echo ":x: PERF_RW_PUSH_INTERVAL_S must be a whole number of seconds >= 1" >&2; exit 1; }
if [ "$QUIET_S" -lt $(( PUSH_S * 2 )) ]; then
  echo ":x: PERF_RW_QUIET_S=$QUIET_S is below 2x the push interval ($PUSH_S s): the final rung's last pushes could be lost at shutdown" >&2
  exit 1
fi
[ "$SETTLE_S" -lt "$STEP_S" ] || { echo ":x: settle ($SETTLE_S s) must be shorter than the step ($STEP_S s)" >&2; exit 1; }
case "$DEGRADE" in ""|pause_prometheus|stall_prometheus|kill:[0-9]*) ;; *) echo ":x: PERF_RW_DEGRADE must be empty, pause_prometheus, stall_prometheus or kill:<index>" >&2; exit 1 ;; esac

cpu_count() { local c; c="$(expand_cpuset "$1" | wc -w | tr -d ' ')"; echo "${c:-0}"; }

RUN_ID="${BUILDKITE_BUILD_ID:-local}-$$-rw"
PROM_NAME="mockserver-rw-prom-${RUN_ID}"
SUT_NAME="mockserver-rw-sut-${RUN_ID}"
# Containers resolve each other by these short aliases, never by the names above: a DNS label
# caps at 63 characters and the names reach 64+ with a 5-digit PID. Unique per run, because
# PERF_RW_NETWORK may be a network other containers share.
ALIAS_ID="$(printf '%s' "$RUN_ID" | cksum | awk '{print $1}')"
PROM_ALIAS="rw-prom-${ALIAS_ID}"
SUT_ALIAS="rw-sut-${ALIAS_ID}"
K6_PREFIX="mockserver-rw-k6-${RUN_ID}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/perf-rw.XXXXXX")"
chmod 0777 "$WORK"
ALL_NAMES=""
OWN_NETWORK=""
SUT_MEMS=""; PROM_MEMS=""; XCHECK_MEMS=""; K6_MEMS_JSON=""; OBSERVED=""; NODE_CPULISTS_JSON=""
RESULT_WRITTEN=0
# The result's placement: the arm's computed record plus what Docker reports each container got
# (observed[], observed_mems = the SUT's) beside what the arm asked for. null before it is resolved.
placement_result_json() {
  local obs="[]"
  if [ -s "${OBSERVED:-}" ]; then obs="$(jq -sc . "$OBSERVED" 2>/dev/null)" || obs="[]"; fi
  jq -nc --argjson p "${PLACEMENT_JSON:-null}" --argjson obs "${obs:-[]}" --argjson cpulists "${NODE_CPULISTS_JSON:-null}" \
    --arg sm "${SUT_MEMS#--cpuset-mems=}" --arg pm "${PROM_MEMS#--cpuset-mems=}" --arg xm "${XCHECK_MEMS#--cpuset-mems=}" \
    --argjson km "${K6_MEMS_JSON:-null}" '
    if $p == null then null else
      ([ $obs[] | select(.role == "sut") ] | first) as $sut
      | $p + {observed_mems: ($sut.cpuset_mems // null),
              sut_mems_as_requested: (if $sut == null or $sut.cpuset_mems == null then null else $sut.cpuset_mems == $sm end),
              requested_mems: {sut: $sm, prometheus: $pm, xcheck: $xm, k6: $km},
              observed: $obs, node_cpulists: $cpulists,
              note: "observed = docker inspect HostConfig.CpusetCpus/CpusetMems per container (\"\" = unrestricted, null = not inspectable); requested_mems = the --cpuset-mems this arm computes for its own containers (k6: one per main process, in order)"}
    end'
}
# An abort still leaves an invalid result naming the failed step, plus whatever the main
# phase merged, so a rig run is never reduced to `{}`.
write_fallback_result() { # rc failed_command
  local rc="$1" err merged="$WORK/main-merged.json" out
  if [ -n "$LAST_ERR" ]; then err="$LAST_ERR"
  # Deliberate default: TERM/INT set LAST_ERR, but another path could still exit 0 here.
  elif [ "$rc" -eq 0 ]; then err="terminated (signal?) while running: $2"
  else err="exit $rc: $2"; fi
  [ -s "$merged" ] || merged=/dev/null
  fallback_json() { # merged_file
    jq -n --arg err "$err" --argjson n "${N:-0}" --slurpfile merged "$1" --argjson k6rt "$(k6_runtime_json 2>/dev/null || echo null)" \
      --argjson placement "$(placement_result_json 2>/dev/null || echo "${PLACEMENT_JSON:-null}")" \
      --arg hook_cut "${PERF_RW_TEST_CUT_FAULT:-}" --arg hook_step "${PERF_RW_TEST_FAIL_STEP:-}" --arg hook_null "${PERF_RW_TEST_NULL_RUNG:-}" --arg hook_zero "${PERF_RW_TEST_ZERO_TAIL:-}" '
      {attempted:true, valid:false, headline:null, config:{k6_runtime:$k6rt}, placement:$placement,
       invalid_reasons:["rw_harness_completed: the harness aborted before assembling its result (\($err))"],
       validity:{valid:false, checks:[{name:"rw_harness_completed", ok:false, detail:$err}]},
       method:{method:"remote_write_multi_k6", procs:$n,
               test_hooks:({cut_fault:$hook_cut, fail_step:$hook_step, null_rung:$hook_null, zero_tail:$hook_zero} | with_entries(select(.value != "")))},
       main_merged:($merged[0] // null)}'
  }
  # An unreadable merged file must not leave the output empty: retry without it.
  out="$(fallback_json "$merged" 2>/dev/null)" || out=""
  [ -n "$out" ] || out="$(fallback_json /dev/null 2>/dev/null)" || true
  printf '%s\n' "$out" > "$OUT_FILE" 2>/dev/null || true
}
cleanup() { # rc failed_command
  local rc="$1"
  # First, so its status and CSVs are final before the copy below; a no-op once run_phase stopped it.
  { declare -F host_kernel_sampler_stop >/dev/null && host_kernel_sampler_stop; } 2>/dev/null || true
  if [ "$RESULT_WRITTEN" != 1 ] && [ "$OUT_FILE" != /dev/stdout ]; then write_fallback_result "$rc" "$2"; fi
  # JSON, CSV and text files, full k6 logs and the Prometheus log (taken before the
  # containers go). Guarded: a copy failure must not skip cleanup or change the exit code.
  if [ -n "${PERF_RW_DEBUG_DIR:-}" ] && [ -d "$WORK" ]; then
    {
      mkdir -p "$PERF_RW_DEBUG_DIR"
      cp "$WORK"/*.json "$WORK"/*.csv "$WORK"/*.txt "$WORK"/*.yml "$WORK"/*.log "$PERF_RW_DEBUG_DIR"/
    } 2>/dev/null || true
    { docker logs "$PROM_NAME" > "$PERF_RW_DEBUG_DIR/prometheus.log" 2>&1; } 2>/dev/null || true
  fi
  # Background samplers (a failed soft step can orphan one, reparented away from $$) are
  # recorded as "PID start-time"; one is killed only if that PID still has the same start
  # time, i.e. it is the same process, never a reused PID in this or another run.
  if [ -f "$WORK/pids.txt" ]; then
    local pid started
    while read -r pid started; do
      [ -n "$started" ] && [ "$(proc_start "$pid")" = "$started" ] && kill "$pid" 2>/dev/null || true
    done < "$WORK/pids.txt"
  fi
  # Names started inside a soft-step subshell only reach the registry file.
  [ -f "$WORK/containers.txt" ] && ALL_NAMES="$ALL_NAMES $(tr '\n' ' ' < "$WORK/containers.txt")"
  # shellcheck disable=SC2086
  [ -n "$ALL_NAMES" ] && docker rm -f $ALL_NAMES >/dev/null 2>&1 || true
  [ -n "$OWN_NETWORK" ] && docker network rm "$OWN_NETWORK" >/dev/null 2>&1 || true
  if [ -n "${PERF_RW_KEEP_WORK:-}" ]; then echo "--- work dir kept: $WORK" >&2; else rm -rf "$WORK"; fi
}
trap 'cleanup "$?" "$BASH_COMMAND"' EXIT
# Name a cancellation (e.g. Buildkite's SIGTERM) and still run cleanup. Subshells reset these.
trap 'LAST_ERR="terminated by SIGTERM while running: $BASH_COMMAND"; exit 143' TERM
trap 'LAST_ERR="interrupted by SIGINT while running: $BASH_COMMAND"; exit 130' INT
die() { LAST_ERR="$1"; echo ":x: $1" >&2; exit 1; } # a deliberate stop the fallback can name
proc_start() { ps -o lstart= -p "$1" 2>/dev/null | awk '{$1=$1; print}'; } # "" if gone or unsupported
record_pid() { echo "$1 $(proc_start "$1")" >> "$WORK/pids.txt"; }

if [ -z "$K6_GOMEMLIMIT" ]; then
  _mem="$(k6_docker_mem_bytes)" || _mem=""
  _gml="$(k6_gomemlimit_resolve "$_mem" "${K6_SETS[@]}")" \
    || die "cannot derive the k6 GOMEMLIMIT: Docker reported MemTotal '${_mem}' for N=$N and no k6 NUMA node memory is readable; set PERF_RW_K6_GOMEMLIMIT (e.g. 8GiB, or off)"
  K6_GOMEMLIMIT="$(jq -r .limit <<<"$_gml")"; K6_GOMEMLIMIT_BASIS="$(jq -c .basis <<<"$_gml")"
  if [ "${K6_GOMEMLIMIT%MiB}" -lt 1024 ]; then
    echo "WARNING: derived k6 GOMEMLIMIT $K6_GOMEMLIMIT is under 1 GiB; if a k6 live heap reaches it, Go collects continuously and k6 CPU per request rises" >&2
  fi
fi
K6_GO_ENV=(-e "GOGC=$K6_GOGC" -e "GOMEMLIMIT=$K6_GOMEMLIMIT")
if [ "${PERF_RW_TEST_RESOLVE_ONLY:-}" = true ]; then # test hook: print .config.k6_runtime, start nothing
  k6_runtime_json; RESULT_WRITTEN=1; exit 0
fi

echo "--- item 31 remote-write multi-k6 sweep: N=$N rates=$RATES step=$SWEEP_STEP gap=$SWEEP_GAP settle=${SETTLE_S}s push=${PUSH_S}s quiet=${QUIET_S}s window=$WINDOW_MODE server=$SERVER_CPUS prometheus=$PROM_CPUS upstream=${UPSTREAM_CPUS:-none} k6=${K6_SETS[*]} target=${TARGET_URL:-<launch SUT>} degrade=${DEGRADE:-none} k6_gogc=$K6_GOGC k6_gomemlimit=$K6_GOMEMLIMIT k6_graceful_stop=$K6_GRACEFUL_STOP ladder=$LADDER_PROFILE/$RATES_SOURCE k6_vu_ceiling=${K6_VU_CEILING:-$RW_SWEEP_VU_CEILING} ($K6_VU_CEILING_SOURCE) tail_instrument=$TAIL_INSTRUMENT" >&2
AB_JSON="$(ab_json)"
if [ "$(jq -r '.trial' <<<"$AB_JSON")" = true ]; then
  echo "--- A/B trial, not a counting run for item 44 (.method.ab): $AB_JSON" >&2
fi

# --- placement proof: SUT, Prometheus, upstream and every k6 on disjoint physical cores ---
PAIRS=(server "$SERVER_CPUS" prometheus "$PROM_CPUS")
[ -n "$UPSTREAM_CPUS" ] && PAIRS+=(upstream "$UPSTREAM_CPUS")
for ((i=0;i<N;i++)); do PAIRS+=("k6_$i" "${K6_SETS[$i]}"); done
if ! cpusets_physically_disjoint "${PAIRS[@]}" >&2; then
  die "SUT / Prometheus / upstream / k6 cpusets are not physically disjoint — refusing to measure contention"
fi
# --- NUMA guard: the SUT, Prometheus and each k6 (cross-check included) on one node each, and k6
# on another node from the SUT whenever there is one (PERF_K6_NUMA_NODE=same is the explicit A/B).
if ! numa_placement_check "$K6_NUMA_NODE" "$SERVER_CPUS" "${K6_SETS[@]}" "$XCHECK_CPUS" >&2; then
  die "the SUT / k6 cpusets fail the NUMA placement guard — refusing to measure across sockets"
fi
assert_cpuset_single_node prometheus "$PROM_CPUS" >&2 || die "the Prometheus cpuset straddles NUMA nodes"
PLACEMENT_JSON="$(numa_placement_json "$PLACEMENT_LAYOUT" "$K6_NUMA_NODE" "$SERVER_CPUS" "${K6_SETS[@]}")"
# Each container's memory on the node it runs on; empty (no flag) where the node map is unreadable.
SUT_MEMS="$(numa_mems_flag "$SERVER_CPUS")"; PROM_MEMS="$(numa_mems_flag "$PROM_CPUS")"
XCHECK_MEMS="$(numa_mems_flag "$XCHECK_CPUS")"
# The flag each main k6 process gets in run_phase, "" where none.
K6_MEMS_JSON="$(for _k in "${K6_SETS[@]}"; do _f="$(numa_mems_flag "$_k")"; printf '%s\n' "${_f#--cpuset-mems=}"; done | jq -Rsc 'split("\n") | .[:-1]')"
NODE_CPULISTS_JSON="$(numa_node_cpulists_json)"
numa_log_node_cpulists >&2
# What this arm asks for; what each container received follows as "--- observed placement:" lines.
echo "--- placement: $PLACEMENT_JSON requested mems: server=${SUT_MEMS:-none} prometheus=${PROM_MEMS:-none} xcheck=${XCHECK_MEMS:-none} k6=$K6_MEMS_JSON" >&2
if [ "${PERF_RW_TEST_PLACEMENT_ONLY:-}" = true ]; then # test hook: print the resolved placement, start nothing
  jq -nc --arg s "$SERVER_CPUS" --arg p "$PROM_CPUS" --arg u "${DEF_UPSTREAM:-}" --arg k "$(IFS=';'; echo "${K6_SETS[*]}")" \
    --arg x "$XCHECK_CPUS" --arg sm "$SUT_MEMS" --arg pm "$PROM_MEMS" --arg xm "$XCHECK_MEMS" \
    --arg km "$(for _k in "${K6_SETS[@]}"; do numa_mems_flag "$_k"; echo; done)" --argjson placement "$PLACEMENT_JSON" \
    --arg rates "$RATES" --arg profile "$LADDER_PROFILE" --arg rsrc "$RATES_SOURCE" \
    --argjson k6rt "$(k6_runtime_json)" --argjson cpulists "$NODE_CPULISTS_JSON" --argjson ab "$AB_JSON" '
    {ab:$ab, server:$s, prometheus:$p, upstream_default:$u, k6:($k | split(";")), xcheck:$x,
     mems:{server:$sm, prometheus:$pm, xcheck:$xm, k6:($km | split("\n") | map(select(. != "")))}, placement:$placement,
     ladder:{profile:$profile, source:$rsrc, rates:$rates}, k6_runtime:$k6rt, node_cpulists:$cpulists}'
  RESULT_WRITTEN=1; exit 0
fi
OBSERVED="$WORK/observed-placement.ndjson"
: > "$OBSERVED"

# --- container-side URLs: every host a k6 container resolves --------------------
# Go's resolver (k6) refuses a DNS label over 63 characters, so a long alias turns every push
# into "no such host". Checked on the assembled URLs, not on the alias constants.
assert_dns_host() { # what url
  local host="${2#*://}" rest label
  host="${host%%/*}"; host="${host%:*}"
  case "$host" in \[*) return 0 ;; esac # IPv6 literal: not a DNS name
  [ -n "$host" ] || die "$1 '$2' has no host"
  rest="$host."
  while [ -n "$rest" ]; do
    label="${rest%%.*}"; rest="${rest#*.}"
    if [ -z "$label" ] || [ "${#label}" -gt 63 ]; then
      die "$1 host '$host' has a ${#label}-character DNS label; a label caps at 63 (RFC 1035), so k6 could not resolve it. Use a short --network-alias, not the container name"
    fi
  done
}
LAUNCH_SUT=""
if [ -z "$TARGET_URL" ]; then LAUNCH_SUT=1; TARGET_URL="http://${SUT_ALIAS}:1080"; fi
RW_URL="http://${PROM_ALIAS}:9090/api/v1/write"
assert_dns_host "remote-write URL" "$RW_URL"
assert_dns_host "target URL" "$TARGET_URL"

# --- network, Prometheus, SUT --------------------------------------------------
if [ -n "$NETWORK_IN" ]; then
  NETWORK="$NETWORK_IN"
else
  NETWORK="mockserver-rw-${RUN_ID}"
  docker network create "$NETWORK" >/dev/null
  OWN_NETWORK="$NETWORK"
fi

cat > "$WORK/prometheus.yml" <<'EOF'
global:
  scrape_interval: 1h
scrape_configs: []
EOF
chmod 0644 "$WORK/prometheus.yml"
ALL_NAMES="$ALL_NAMES $PROM_NAME"
# Native histograms on; the lookback is long so each rung's final cumulative value is
# readable at any instant after the run without a per-rung query time.
docker run -d --name "$PROM_NAME" --network "$NETWORK" --network-alias "$PROM_ALIAS" \
  --cpuset-cpus="$PROM_CPUS" ${PROM_MEMS:+"$PROM_MEMS"} -p 127.0.0.1::9090 \
  -v "$WORK/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  "$PROM_IMAGE" --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.path=/prometheus \
  --web.enable-remote-write-receiver --enable-feature=native-histograms \
  --query.lookback-delta=2h >/dev/null
log_container_cpuset prometheus "$PROM_NAME" "$OBSERVED"
# Resolved per call: a Prometheus restart moves the published host port.
# PERF_RW_PUBLISHED_HOST: where published ports are reachable when this script itself runs in
# a container (e.g. host.docker.internal); default is the address docker reports.
published() { local hp; hp="$(docker port "$1" "$2" 2>/dev/null | head -1)"; echo "${PERF_RW_PUBLISHED_HOST:-${hp%:*}}:${hp##*:}"; }
prom_url() { echo "http://$(published "$PROM_NAME" 9090/tcp)"; }
PROM_URL="$(prom_url)"
for _ in $(seq 1 60); do
  curl -sf "$PROM_URL/-/ready" >/dev/null 2>&1 && break
  sleep 1
done
curl -sf "$PROM_URL/-/ready" >/dev/null || die "Prometheus did not become ready at $PROM_URL"

# Remote-write send failures in k6 logs (the same count gates rw_no_failed_pushes).
rw_push_failures() { { grep -hiE 'failed to send|level=error.*remote write' "$@" 2>/dev/null || true; } | wc -l | tr -d ' '; }
# Pre-flight: before any rung, one k6 inside $NETWORK pushes through the same output, URL and
# resolver the ladder uses, and its series must reach Prometheus; a dead path fails in seconds.
preflight_remote_write() {
  local tag="preflight" name="${K6_PREFIX}-preflight" log="$WORK/preflight.log" got="" fails
  ALL_NAMES="$ALL_NAMES $name"
  printf 'export default function () {}\n' | docker run -i --rm --name "$name" --network "$NETWORK" \
    --cpuset-cpus="$XCHECK_CPUS" ${XCHECK_MEMS:+"$XCHECK_MEMS"} -e "K6_PROMETHEUS_RW_SERVER_URL=$RW_URL" \
    -e "K6_PROMETHEUS_RW_PUSH_INTERVAL=${PUSH_S}s" -e "K6_PROMETHEUS_RW_STALE_MARKERS=false" \
    "$K6_IMAGE" run --quiet --vus 1 --iterations 1 --tag "proc=$tag" -o experimental-prometheus-rw - > "$log" 2>&1 \
    || die "remote-write pre-flight: the k6 container failed to run (see preflight.log): $(tail -n 3 "$log" | tr '\n' ' ')"
  fails="$(rw_push_failures "$log")"
  if [ "$fails" -gt 0 ]; then
    die "remote-write pre-flight: $fails push failure(s) to $RW_URL before any rung: $(grep -m1 -hiE 'failed to send|level=error.*remote write' "$log")"
  fi
  for _ in $(seq 1 10); do
    got="$(curl -sf --max-time 5 --data-urlencode "query=sum(k6_iterations_total{proc=\"$tag\"})" "$(prom_url)/api/v1/query" \
      | jq -r '.data.result[0].value[1] // empty' 2>/dev/null)" || got=""
    [ -n "$got" ] && [ "$got" != 0 ] && break
    sleep 1
  done
  [ -n "$got" ] && [ "$got" != 0 ] || die "remote-write pre-flight: k6 reported no push failure, but its iteration never reached Prometheus via $RW_URL"
  echo "--- remote-write pre-flight ok: k6 in $NETWORK pushed to $RW_URL (k6_iterations_total{proc=\"$tag\"}=$got)" >&2
}
preflight_remote_write

wait_ready() {
  local url="$1" code
  for _ in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 -X PUT "${url}/mockserver/status" 2>/dev/null || echo 000)"
    [ "$code" = "200" ] && return 0
    sleep 2
  done
  return 1
}

if [ -n "$LAUNCH_SUT" ]; then
  ALL_NAMES="$ALL_NAMES $SUT_NAME"
  docker run -d --name "$SUT_NAME" --network "$NETWORK" --network-alias "$SUT_ALIAS" \
    --cpuset-cpus="$SERVER_CPUS" ${SUT_MEMS:+"$SUT_MEMS"} --memory="$SERVER_MEMORY" -p 127.0.0.1::1080 \
    -e MOCKSERVER_LOG_LEVEL=ERROR -e MOCKSERVER_DISABLE_SYSTEM_OUT=true -e MOCKSERVER_METRICS_ENABLED=true \
    "$MOCKSERVER_IMAGE" -serverPort 1080 >/dev/null
  SUT_CONTAINER="$SUT_NAME"
  CURL_URL="http://$(published "$SUT_NAME" 1080/tcp)"
else
  CURL_URL="${PERF_RW_TARGET_CURL_URL:-$TARGET_URL}"
fi
# The SUT may have been started by the caller, so its cpuset is what Docker reports, not SUT_MEMS.
[ -n "$SUT_CONTAINER" ] && log_container_cpuset sut "$SUT_CONTAINER" "$OBSERVED"
wait_ready "$CURL_URL" || die "SUT not ready at $CURL_URL"

# --- Prometheus query helpers ------------------------------------------------
# Fail closed without aborting: a failed query is recorded in $PROM_FAILURES (which fails
# the rw_prometheus_queries_ok gate) and reads as an empty result ("[]" / "null"), never "".
PROM_FAILURES="$WORK/prom-query-failures.txt"
PROM_WARNINGS="$WORK/prom-query-warnings.txt"
: > "$PROM_FAILURES"; : > "$PROM_WARNINGS"
promq() { # expr -> JSON result array ("[]" on failure)
  local body out
  if body="$(curl -sf --max-time 30 --data-urlencode "query=$1" "$(prom_url)/api/v1/query")" \
     && out="$(jq -ce '.data.result | arrays' <<<"$body")"; then
    # An empty result with a PromQL warning (e.g. mixed float/histogram) is otherwise silent.
    jq -r --arg q "$1" '((.warnings // []) + (.infos // []))[] | "\($q) :: \(.)"' <<<"$body" >> "$PROM_WARNINGS" 2>/dev/null || true
    echo "$out"
  else
    echo ":x: Prometheus query failed (container $PROM_NAME down or restarted?): $1" >&2
    echo "$1" >> "$PROM_FAILURES"
    echo "[]"
  fi
}
promv() { # expr -> first scalar value, or "null"
  local out
  out="$(promq "$1" | jq -r 'if length == 0 then "null" else (.[0].value[1] | if . == "NaN" or . == "+Inf" or . == "-Inf" then "null" else . end) end' 2>/dev/null)" || out=""
  nn "$out"
}
nn() { [ -n "$1" ] && echo "$1" || echo null; } # "" -> null for bash-side consumers

usage_usec() { docker exec "$1" cat /sys/fs/cgroup/cpu.stat 2>/dev/null | awk '$1=="usage_usec"{print $2}'; }
sleep_until() { while [ "$(date +%s)" -lt "$1" ]; do sleep 0.2; done; }

# Report-only or post-measurement steps run in an errexit subshell: a failure is recorded
# (rw_assembly_steps_ok) and a default used, so the result is still written.
# PERF_RW_TEST_FAIL_STEP=<name> forces that step to fail (degrade test).
ASSEMBLY_FAILURES=""
# Invariant: every failed step reaches ASSEMBLY_FAILURES, so rw_assembly_steps_ok fails.
note_step_failure() {
  ASSEMBLY_FAILURES="${ASSEMBLY_FAILURES:+$ASSEMBLY_FAILURES, }$1"
  echo ":x: rw-multi-k6: step '$1' failed; recorded as a failed check, continuing" >&2
}
soft() { # name cmd...
  local name="$1"; shift
  if [ "${PERF_RW_TEST_FAIL_STEP:-}" = "$name" ]; then note_step_failure "$name"; return 0; fi
  ( "$@" ) & local pid=$!
  wait "$pid" || note_step_failure "$name"
}
soft_capture() { # var default name cmd...  (stdout of cmd -> var)
  local var="$1" def="$2" name="$3"; shift 3
  local out="$WORK/.step-$name.out"
  if [ "${PERF_RW_TEST_FAIL_STEP:-}" != "$name" ]; then
    ( "$@" ) > "$out" & local pid=$!
    if wait "$pid" && [ -s "$out" ]; then printf -v "$var" '%s' "$(cat "$out")"; return 0; fi
  fi
  note_step_failure "$name"
  printf -v "$var" '%s' "$def"
}

# Host kernel counters over the main ladder: per cpu for the SUT and each k6 process, TCP per network
# namespace (the host's, the SUT's, each k6's), on Prometheus's cpus where taskset exists. Report-only.
start_tail_sampler() { # k6_names cpusets(;) ladder_end_s
  local -a sets_arr; IFS=';' read -ra sets_arr <<< "$2"
  local roles="sut=$SERVER_CPUS" netns="host=self" i=0 k pid max
  if [ -n "$SUT_CONTAINER" ]; then
    pid="$(docker inspect -f '{{.State.Pid}}' "$SUT_CONTAINER" 2>/dev/null || true)"; netns="$netns sut=${pid:-}"
  fi
  for k in $1; do
    pid="$(docker inspect -f '{{.State.Pid}}' "$k" 2>/dev/null || true)"
    roles="$roles k6_$i=${sets_arr[$i]}"; netns="$netns k6_$i=${pid:-}"; i=$((i + 1))
  done
  max="$(awk -v s=$(( $3 - $(date +%s) + QUIET_S + 60 )) -v iv="$HKS_INTERVAL_S" 'BEGIN { n = int(s / iv) + 1; print (n < 1 ? 1 : n) }')"
  host_kernel_sampler_start "$WORK" "$roles" "$netns" "$max" "$HKS_INTERVAL_S" "$HKS_MAX_BYTES" "$PROM_CPUS"
  [ -z "$HOST_KERNEL_SAMPLER_PID" ] || record_pid "$HOST_KERNEL_SAMPLER_PID"
  echo "--- host kernel sampler: $(jq -c '{running, reason, pinned_cpus, unavailable:[.sources | to_entries[] | select(.value.available | not) | "\(.key): \(.value.reason)"]}' "$WORK/host-kernel-status.json" 2>/dev/null || echo "no status")" >&2
}

# --- one synchronised ladder ---------------------------------------------------
# run_phase <phase> <n> <agg_rates_csv> <window_mode> <lean> <cpusets;...>
# Leaves $WORK/<phase>-p<i>.json (per-process summaries), $WORK/<phase>-meta.json
# and $WORK/<phase>-cpu.csv (`ts name cpu`).
run_phase() {
  local phase="$1" n="$2" agg="$3" wmode="$4" lean="$5" sets="$6"
  local -a set_arr agg_arr
  IFS=';' read -ra set_arr <<< "$sets"
  IFS=',' read -ra agg_arr <<< "$agg"
  local pp="" a
  for a in "${agg_arr[@]}"; do pp="${pp:+$pp,}$(( a / n > 0 ? a / n : 1 ))"; done
  local rungs="${#agg_arr[@]}"
  local start_s=$(( $(date +%s) + START_LEAD_S ))
  local ladder_end_s=$(( start_s + (rungs - 1) * (STEP_S + GAP_S) + STEP_S ))
  local names="" i mems
  echo "+++ phase=$phase N=$n per_process_rates=$pp window=$wmode lean=$lean start_at=$start_s ladder_end=$ladder_end_s" >&2
  for ((i=0;i<n;i++)); do
    local kname="${K6_PREFIX}-${phase}-p${i}"
    names="${names:+$names }$kname"
    ALL_NAMES="$ALL_NAMES $kname"; echo "$kname" >> "$WORK/containers.txt"
    # No --quiet: k6's progress line is its only report of interrupted iterations.
    mems="$(numa_mems_flag "${set_arr[$i]}")"
    docker run -d --name "$kname" --network "$NETWORK" --cpuset-cpus="${set_arr[$i]}" ${mems:+"$mems"} \
      -v "$K6_DIR:/k6:ro" -v "$WORK:/out" \
      -e "BASE_URL=$TARGET_URL" -e "PROTO=http" \
      -e "K6_SWEEP_RATES=$pp" -e "K6_SWEEP_STEP=${STEP_S}s" -e "K6_SWEEP_GAP=${GAP_S}s" \
      -e "K6_SWEEP_SETTLE=${SETTLE_S}s" -e "K6_SWEEP_RESULT_PATH=/out/${phase}-p${i}.json" \
      -e "K6_SWEEP_WINDOW_MODE=$wmode" -e "K6_SWEEP_LEAN_SUMMARY=$lean" \
      -e "K6_SWEEP_VU_DIAGNOSTICS=$VU_DIAGNOSTICS" -e "K6_SWEEP_GRACEFUL_STOP=$K6_GRACEFUL_STOP" \
      ${K6_VU_CEILING:+-e "K6_SWEEP_VU_CEILING=$K6_VU_CEILING"} \
      -e "GODEBUG=$([ "$K6_GCTRACE" = true ] && echo gctrace=1)" ${K6_GO_ENV[@]+"${K6_GO_ENV[@]}"} \
      -e "K6_SWEEP_START_AT_MS=$(( start_s * 1000 ))" -e "K6_SWEEP_QUIET=${QUIET_S}s" \
      -e "K6_SWEEP_MANAGE_SUT=$([ "$i" -eq 0 ] && echo true || echo false)" \
      -e "K6_PROMETHEUS_RW_SERVER_URL=$RW_URL" \
      -e "K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true" \
      -e "K6_PROMETHEUS_RW_PUSH_INTERVAL=${PUSH_S}s" \
      -e "K6_PROMETHEUS_RW_STALE_MARKERS=false" \
      "$K6_IMAGE" run --tag "proc=${phase}-p${i}" -o experimental-prometheus-rw /k6/sweep.js >/dev/null
    log_container_cpuset "k6_${phase}_p${i}" "$kname" "$OBSERVED"
  done
  if [ "$phase" = main ] && [ "$TAIL_INSTRUMENT" = true ]; then
    start_tail_sampler "$names" "$sets" "$ladder_end_s" || echo "WARNING: rw-multi-k6: host kernel sampler not started (report-only)" >&2
  fi

  local cpu_log="$WORK/${phase}-cpu.csv" want="$names $PROM_NAME ${SUT_CONTAINER:-}"
  : > "$cpu_log"
  # Stamped on return: docker stats reports the ~1 s before it returns (performance-measurement.md, sweep.js).
  # Ends with its parent too: a SIGKILLed harness runs no cleanup to kill it.
  ( while kill -0 "$$" 2>/dev/null; do
      stats="$(docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' 2>/dev/null || true)"
      ts="$(date -u +%s)"
      awk -v t="$ts" -v want="$want" '
            BEGIN{ split(want, w, " "); for(k in w) keep[w[k]]=1 }
            ($1 in keep){ gsub(/%/,"",$2); printf "%s %s %s\n", t, $1, $2 }' <<<"$stats" >> "$cpu_log"
      sleep "${PERF_RW_SAMPLE_INTERVAL:-1}"
    done ) & local sampler=$!
  record_pid "$sampler"

  # cgroup CPU at the ladder's start and end: k6 CPU per request over the load
  # window only (VU initialisation and the summary are excluded).
  ( sleep_until "$start_s"
    for k in $names; do echo "$k start $(usage_usec "$k")"; done
    sleep_until "$ladder_end_s"
    for k in $names; do echo "$k end $(usage_usec "$k")"; done ) > "$WORK/${phase}-cgroup.txt" 2>/dev/null & local cgroup_pid=$!
  record_pid "$cgroup_pid"

  local degrade_pid=""
  if [ "$phase" = main ] && [ -n "$DEGRADE" ]; then
    case "$DEGRADE" in
      kill:*)
        local victim="${K6_PREFIX}-${phase}-p${DEGRADE#kill:}"
        ( sleep_until $(( ladder_end_s - 1 )); echo "--- DEGRADE: SIGKILL $victim before its last flush" >&2; docker kill "$victim" >/dev/null 2>&1 || true ) & degrade_pid=$!
        ;;
      pause_prometheus)
        ( sleep_until $(( ladder_end_s - 1 )); echo "--- DEGRADE: pausing Prometheus so the final flushes fail" >&2; docker pause "$PROM_NAME" >/dev/null 2>&1 || true ) & degrade_pid=$!
        ;;
      stall_prometheus)
        # Mid-ladder: stall pushes across a rung's settle boundary, then resume.
        local sr=$(( rungs > 1 ? 1 : 0 )) stall_s=$(( PUSH_S * 4 ))
        local stall_at=$(( start_s + sr * (STEP_S + GAP_S) + SETTLE_S - 1 ))
        ( sleep_until "$stall_at"; echo "--- DEGRADE: stalling Prometheus ${stall_s}s across rung $sr's settle boundary" >&2
          docker pause "$PROM_NAME" >/dev/null 2>&1 || true; sleep "$stall_s"; docker unpause "$PROM_NAME" >/dev/null 2>&1 || true ) & degrade_pid=$!
        ;;
    esac
  fi

  local exits=""
  for k in $names; do exits="$exits $(docker wait "$k" 2>/dev/null || echo 255)"; done
  [ -n "$degrade_pid" ] && wait "$degrade_pid" 2>/dev/null || true
  if [ "$DEGRADE" = pause_prometheus ] && [ "$phase" = main ]; then docker unpause "$PROM_NAME" >/dev/null 2>&1 || true; fi
  wait "$cgroup_pid" 2>/dev/null || true
  kill "$sampler" >/dev/null 2>&1 || true; wait "$sampler" 2>/dev/null || true
  if [ "$phase" = main ]; then host_kernel_sampler_stop; fi
  i=0
  # Each container's start (epoch s), which places its gctrace lines (@seconds since start).
  local started="" st
  for k in $names; do
    docker logs "$k" > "$WORK/${phase}-p${i}.log" 2>&1 || true; i=$((i+1))
    st="$(docker inspect -f '{{.State.StartedAt}}' "$k" 2>/dev/null | jq -Rr '(capture("^(?<b>[^.Z]+)(\\.(?<f>[0-9]+))?Z$") // null)
      | if . == null then "null" else ((.b + "Z") | fromdateiso8601) + (("0." + (.f // "0")) | tonumber) end' 2>/dev/null || true)"
    started="$started ${st:-null}"
  done

  jq -nc --arg phase "$phase" --argjson n "$n" --arg agg "$agg" --arg pp "$pp" --arg sets "$sets" \
    --arg wmode "$wmode" --arg lean "$lean" --argjson start_s "$start_s" --argjson end_s "$ladder_end_s" \
    --arg names "$names" --arg exits "$exits" --arg started "$started" --argjson step "$STEP_S" --argjson gap "$GAP_S" '
    {phase:$phase, n:$n, window_mode:$wmode, lean_summary:($lean=="true"), step_s:$step, gap_s:$gap,
     agg_rates:($agg|split(",")|map(tonumber)), per_process_rates:($pp|split(",")|map(tonumber)),
     cpusets:($sets|split(";")), start_at_s:$start_s, ladder_end_s:$end_s,
     containers:($names|split(" ")), exit_codes:($exits|split(" ")|map(select(.!=""))|map(tonumber)),
     container_started_epoch_s:($started|split(" ")|map(select(.!=""))|map(tonumber? // null))}' > "$WORK/${phase}-meta.json"
}

# --- merge one phase from Prometheus -------------------------------------------
# merge_phase <phase> <window_mode> <out_name>: writes $WORK/<out_name>.json. The
# window mode is a parameter so the cross-check can cut the same requests both ways.
merge_phase() {
  local phase="$1" wmode="$2" out_name="$3" meta="$WORK/$1-meta.json"
  local n; n="$(jq -r '.n' "$meta")"
  local -a agg_arr pp_arr
  IFS=',' read -ra agg_arr <<< "$(jq -r '.agg_rates|join(",")' "$meta")"
  IFS=',' read -ra pp_arr <<< "$(jq -r '.per_process_rates|join(",")' "$meta")"
  local rungs_json="[]" k i
  local procre="${phase}-p[0-9]+"
  for k in "${!agg_arr[@]}"; do
    local r="${pp_arr[$k]}" sel
    sel="proc=~\"${procre}\",rate=\"${r}\""
    # Per-process: Prometheus count vs the process's own count, rung start, settle cut.
    local procs_json="[]"
    local bexpr=""
    for ((i=0;i<n;i++)); do
      local proc="${phase}-p${i}" f="$WORK/${phase}-p${i}.json"
      local summ_count="null" start_ms="null" failed="null" dropped="null" pool="null" setup_late="null"
      local stalls="null" stall_thr="null"
      if jq -e ".points[$k]" "$f" >/dev/null 2>&1; then
        summ_count="$(jq -r ".points[$k].sample_count // \"null\"" "$f")"
        start_ms="$(jq -r ".points[$k].start_epoch_ms // \"null\"" "$f")"
        failed="$(jq -r ".points[$k].failed_count // \"null\"" "$f")"
        dropped="$(jq -r ".points[$k].dropped_iterations // \"null\"" "$f")"
        stalls="$(jq -r ".points[$k].stalls_post_settle // \"null\"" "$f")"
        stall_thr="$(jq -r ".points[$k].stall_ms_threshold // \"null\"" "$f")"
        pool="$(jq -r ".vus_diagnostics.pool_per_rung[\"$r\"] // \"null\"" "$f")"
        setup_late="$(jq -r 'if .wallclock.start_at_ms and .wallclock.setup_end_ms then (.wallclock.setup_end_ms - .wallclock.start_at_ms) else "null" end' "$f")"
      fi
      local prom_count prom_hist_count
      prom_count="$(promv "sum(k6_http_reqs_total{proc=\"$proc\",rate=\"$r\"})")"
      prom_hist_count="$(promv "sum(histogram_count(k6_http_req_duration_seconds{proc=\"$proc\",rate=\"$r\"}))")"
      # Settle cut: the dominant series' first pushed sample at or after (rung start +
      # settle), so a sparse error series cannot set it. It never reaches back into the
      # settle window; cut_excess estimates the requests it drops from the steady window.
      local cut_s="null" boundary_s="null" cut_excess="null" prev_s="null" cut_delta="null"
      if [ "$wmode" = wallclock ] && [ "$start_ms" != null ]; then
        boundary_s="$(awk -v s="$start_ms" -v st="$SETTLE_S" 'BEGIN{printf "%.3f", s/1000 + st}')"
        local cut_query_suffix="" cut_pick=0 prev_back=1
        [ "$CUT_FAULT" = "$proc:query" ] && cut_query_suffix=")"
        [ "$CUT_FAULT" = "$proc:empty" ] && boundary_s="$(awk -v b="$boundary_s" 'BEGIN{printf "%.3f", b + 100000}')"
        [ "$CUT_FAULT" = "$proc:late" ] && cut_pick=3
        [ "$CUT_FAULT" = "$proc:stale" ] && prev_back=3
        local span=$(( STEP_S + GAP_S + QUIET_S + 60 ))
        local end_eval cut_pair
        end_eval="$(awk -v s="$start_ms" -v sp="$span" 'BEGIN{printf "%.3f", s/1000 + sp}')"
        # "<cut> <sample before the cut>" from the dominant series ("null" where there is none).
        cut_pair="$(promq "k6_http_reqs_total{proc=\"$proc\",rate=\"$r\"}[${span}s] @ ${end_eval}${cut_query_suffix}" \
          | jq -r --argjson b "$boundary_s" --argjson pick "$cut_pick" --argjson back "$prev_back" '
              if length == 0 then "null null" else
                (max_by(.values[-1][1] | tonumber) | [ .values[] | .[0] ] | sort) as $t
                | ([ range(0; $t | length) | select($t[.] >= $b) ] | .[$pick]) as $ci
                | if $ci == null then "null null"
                  else "\($t[$ci]) \(if $ci - $back >= 0 then $t[$ci - $back] else "null" end)" end
              end')" || cut_pair=""
        read -r cut_s prev_s <<<"$cut_pair" || true
        cut_s="$(nn "${cut_s:-}")"; prev_s="$(nn "${prev_s:-}")"
        if [ "$cut_s" != null ]; then
          bexpr="${bexpr:+$bexpr or }(k6_http_req_duration_seconds{proc=\"$proc\",rate=\"$r\"} @ ${cut_s})"
          local at_cut at_prev t_prev
          at_cut="$(promv "sum(k6_http_reqs_total{proc=\"$proc\",rate=\"$r\"} @ ${cut_s})")"
          if [ "$prev_s" != null ]; then
            t_prev="$prev_s"
            at_prev="$(promv "sum(k6_http_reqs_total{proc=\"$proc\",rate=\"$r\"} @ ${prev_s})")"
          else
            # The cut is the series' first sample: its counter was 0 at the rung start.
            t_prev="$(awk -v s="$start_ms" 'BEGIN{printf "%.3f", s/1000}')"; at_prev=0
          fi
          # Pro-rate prev->cut to (boundary, cut], assuming a uniform completion rate; a stale
          # prev must not count as excess. A missing value or a counter reset leaves it null.
          if [ "$at_cut" != null ] && [ "$at_prev" != null ]; then
            cut_delta="$(nn "$(awk -v a="$at_cut" -v p="$at_prev" 'BEGIN{printf "%d", a - p}')")"
            cut_excess="$(nn "$(awk -v a="$at_cut" -v p="$at_prev" -v c="$cut_s" -v b="$boundary_s" -v t="$t_prev" \
              'BEGIN{ if (c + 0 <= t + 0 || a + 0 < p + 0) exit; printf "%d", (a - p) * (c - b) / (c - t) + 0.5 }')")"
          fi
        fi
      fi
      procs_json="$(jq -c --arg proc "$proc" --argjson sc "$summ_count" --argjson pc "$prom_count" \
        --argjson hc "$prom_hist_count" --argjson st "$start_ms" --argjson fl "$failed" --argjson dr "$dropped" \
        --argjson pool "$pool" --argjson b "$boundary_s" --argjson cut "$cut_s" --argjson tol "$ACCOUNT_TOL" \
        --argjson sl "$setup_late" --argjson cx "$cut_excess" --argjson rate "$r" \
        --argjson prev "$prev_s" --argjson cdelta "$cut_delta" \
        --argjson stalls "$stalls" --argjson sthr "$stall_thr" \
        --argjson push "$PUSH_S" --argjson wtol "$WINDOW_TOL" --arg wmode "$wmode" '
        . + [{proc:$proc, summary_count:$sc, prometheus_count:$pc, prometheus_histogram_count:$hc,
              rung_start_epoch_ms:$st, failed_count:$fl, dropped_iterations:$dr, pool:$pool,
              stalls_post_settle:$stalls, stall_ms_threshold:$sthr,
              drop_fraction:(if $dr == null or $sc == null or ($dr + $sc) == 0 then null else (($dr / ($dr + $sc)) * 100000 | round) / 100000 end),
              setup_end_minus_start_at_ms:$sl,
              settle_boundary_s:$b, settle_cut_s:$cut,
              settle_cut_late_ms:(if $cut==null or $b==null then null else ((($cut-$b)*1000)|round) end),
              # The sample before the cut, how long before the boundary it was, and the count
              # since it. cut_excess_requests pro-rates that count to (boundary, cut]: the
              # requests dropped from the steady window, bounded by 2 push intervals at the offered rate.
              settle_prev_sample_s:$prev,
              pre_boundary_sample_age_ms:(if $prev==null or $b==null then null else ((($b-$prev)*1000)|round) end),
              cut_count_since_prev_sample:$cdelta,
              cut_excess_requests:$cx,
              cut_excess_max:(($rate * 2 * $push * (1 + $wtol)) | floor),
              # Wall-clock mode needs a measured cut and excess; a missing one fails, never passes.
              cut_measured:($wmode != "wallclock" or ($cut != null and $cx != null)),
              cut_excess_ok:($wmode != "wallclock" or ($cx != null and $cx <= ($rate * 2 * $push * (1 + $wtol)))),
              accounted:($sc != null and $pc != null and $hc != null
                         and (($pc - $sc)|fabs) <= $tol and (($hc - $sc)|fabs) <= $tol)}]' <<<"$procs_json")"
    done

    # Merged histograms: A = whole rung (final cumulative), B = up to each process's cut.
    local a_expr="sum(k6_http_req_duration_seconds{${sel}})" s_expr b_count
    if [ "$wmode" = wallclock ]; then
      if [ -n "$bexpr" ]; then
        s_expr="(${a_expr} - sum(${bexpr}))"
        b_count="$(promv "histogram_count(sum(${bexpr}))")"
      else
        s_expr="$a_expr"; b_count=0
      fi
    else
      s_expr="sum(k6_http_req_duration_seconds{${sel},win=\"${r}_steady\"})"
      b_count="$(promv "histogram_count(sum(k6_http_req_duration_seconds{${sel},win=\"${r}_settle\"}))")"
    fi
    local q vals="{}"
    for q in 0.5 0.9 0.95 0.99 0.999; do
      vals="$(jq -c --arg q "$q" --argjson v "$(promv "histogram_quantile($q, ${s_expr}) * 1000")" '. + {($q):$v}' <<<"$vals")"
      vals="$(jq -c --arg q "full_$q" --argjson v "$(promv "histogram_quantile($q, ${a_expr}) * 1000")" '. + {($q):$v}' <<<"$vals")"
    done
    local a_count s_count vus_p95="null" vus_max="null" over5
    a_count="$(promv "histogram_count(${a_expr})")"
    s_count="$(promv "histogram_count(${s_expr})")"
    # Client-side share over 5 ms from the same merged steady histogram as the percentiles.
    over5="$(promv "1 - histogram_fraction(0, 0.005, ${s_expr})")"
    if [ "$VU_DIAGNOSTICS" = true ]; then
      # Occupancy is per process (each has its own pool), so take the busiest process.
      vus_p95="$(nn "$(promq "histogram_quantile(0.95, sum by (proc) (k6_sweep_vus_active{${sel}}))" | jq -r '[.[].value[1]|tonumber] | max // "null"' || true)")"
      vus_max="$(nn "$(promq "histogram_quantile(1, sum by (proc) (k6_sweep_vus_active{${sel}}))" | jq -r '[.[].value[1]|tonumber] | max // "null"' || true)")"
    fi
    rungs_json="$(jq -c --argjson agg "${agg_arr[$k]}" --argjson r "$r" --argjson n "$n" --argjson procs "$procs_json" \
      --argjson v "$vals" --argjson ac "$a_count" --argjson sc "$s_count" --argjson bc "${b_count:-null}" \
      --argjson step "$STEP_S" --argjson vp95 "$vus_p95" --argjson vmax "$vus_max" --argjson maxskew "$MAX_SKEW_MS" \
      --argjson push "$PUSH_S" --argjson over5 "$over5" '
      def r3: if . == null then null else (. * 1000 | round) / 1000 end;
      ([ $procs[] | .rung_start_epoch_ms | select(. != null) ]) as $starts
      | ([ $procs[] | .summary_count // 0 ] | add) as $summ_total
      | ([ $procs[] | .failed_count // 0 ] | add) as $failed
      | ([ $procs[] | .dropped_iterations // 0 ] | add) as $dropped
      | (if ($starts|length) == $n then (($starts|max) - ($starts|min)) else null end) as $skew
      | . + [{
          offered_rps: ($r * $n),
          nominal_agg_offered_rps: $agg,
          achieved_rps: (if $ac == null then null else (($ac / $step) * 10 | round) / 10 end),
          p50_ms: ($v["0.5"]|r3), p90_ms: ($v["0.9"]|r3), p95_ms: ($v["0.95"]|r3),
          p99_ms: ($v["0.99"]|r3), p999_ms: ($v["0.999"]|r3),
          phase_ms: null,
          sample_count: $ac,
          measured_sample_count: $sc,
          settle_excluded: $bc,
          client_over_5ms_frac: (if $over5 == null then null else ([$over5, 0] | max) * 100000 | round / 100000 end),
          full_rung_ms: {p50_ms: ($v["full_0.5"]|r3), p95_ms: ($v["full_0.95"]|r3),
                         p99_ms: ($v["full_0.99"]|r3), p999_ms: ($v["full_0.999"]|r3)},
          error_rate: (if $summ_total > 0 then (($failed / $summ_total) * 100000 | round) / 100000 else 0 end),
          dropped_iterations: $dropped,
          vus_active_p95: $vp95, vus_active_max: $vmax,
          per_process_pool: ([ $procs[] | .pool ] | max),
          start_skew_ms: $skew,
          skew_ok: ($skew != null and $skew <= $maxskew),
          accounting_ok: (($procs | length) == $n and all($procs[]; .accounted)),
          # The cut may land at most 2 push intervals after its boundary (none in vu_tag mode).
          settle_cut_ok: all($procs[]; .settle_cut_s == null
                                 or (.settle_cut_late_ms >= 0 and .settle_cut_late_ms <= 2 * $push * 1000)),
          cut_measured: all($procs[]; .cut_measured),
          cut_excess_ok: all($procs[]; .cut_excess_ok),
          per_process: $procs
        }]' <<<"$rungs_json")"
  done
  jq -nc --argjson pts "$rungs_json" --slurpfile meta "$meta" --arg w "$wmode" \
    '{meta:($meta[0] + {merged_window_mode:$w}), points:$pts}' > "$WORK/${out_name}.json"
}

# Cgroup CPU us/request per process over the ladder window.
cpu_cost_json() { # phase
  local phase="$1" meta="$WORK/$1-meta.json" out="[]" i=0 k
  for k in $(jq -r '.containers[]' "$meta"); do
    local u0 u1 reqs
    u0="$(awk -v k="$k" '$1==k && $2=="start"{print $3}' "$WORK/${phase}-cgroup.txt")"
    u1="$(awk -v k="$k" '$1==k && $2=="end"{print $3}' "$WORK/${phase}-cgroup.txt")"
    reqs="$(jq -r '[.points[].sample_count // 0] | add // 0' "$WORK/${phase}-p${i}.json" 2>/dev/null || echo 0)"
    out="$(jq -c --arg c "$k" --argjson u0 "${u0:-null}" --argjson u1 "${u1:-null}" --argjson n "${reqs:-0}" '
      . + [{container:$c, cpu_usec:(if $u0==null or $u1==null then null else ($u1-$u0) end), requests:$n,
            cpu_us_per_request:(if $u0==null or $u1==null or $n<=0 then null else ((($u1-$u0)/$n)*10|round)/10 end)}]' <<<"$out")"
    i=$((i+1))
  done
  echo "$out"
}

# --- warm-up (never measured) --------------------------------------------------
if [ "$(to_secs "$WARMUP_DURATION")" -gt 0 ]; then
  echo "--- warm-up: ${WARMUP_RATE} rps for $WARMUP_DURATION (not measured)" >&2
  docker run --rm --network "$NETWORK" --cpuset-cpus="$XCHECK_CPUS" ${XCHECK_MEMS:+"$XCHECK_MEMS"} -v "$K6_DIR:/k6:ro" -v "$WORK:/out" \
    -e "BASE_URL=$TARGET_URL" -e "PROTO=http" -e "K6_SWEEP_RATES=$WARMUP_RATE" -e "K6_SWEEP_STEP=$WARMUP_DURATION" \
    -e "K6_SWEEP_GAP=1s" -e "K6_SWEEP_SETTLE=0s" -e "K6_SWEEP_RESULT_PATH=/out/warmup.json" \
    "$K6_IMAGE" run --quiet /k6/sweep.js >/dev/null 2>&1 || true
fi

# --- cross-check phase: one k6, published summary mode, also remote-writing ------
XRATES=""
if [ "$XCHECK" = true ]; then
  IFS=',' read -ra _all <<< "$RATES"
  for a in "${_all[@]}"; do [ "$a" -le "$XCHECK_MAX_RPS" ] && XRATES="${XRATES:+$XRATES,}$a"; done
  XRATES="${PERF_RW_XCHECK_RATES:-$XRATES}"
fi
# The cross-check program's input (lib/perf-rw-cross-check.jq) from the work files.
cross_input() {
  local tagcut="$WORK/xcheck-tag-at-cut.json" xcost
  [ -s "$tagcut" ] || tagcut=/dev/null
  xcost="$(cpu_cost_json xcheck)"
  jq -nc --arg live true --slurpfile pub "$WORK/xcheck-p0.json" --slurpfile bytime "$WORK/xcheck-by-time.json" \
    --slurpfile bytag "$WORK/xcheck-by-tag.json" --slurpfile tagcut "$tagcut" --slurpfile main "$WORK/main-merged.json" \
    --argjson xcost "$xcost" \
    --argjson t50 "$XTOL_P50" --argjson t95 "$XTOL_P95" --argjson t99 "$XTOL_P99" --argjson abs "$XTOL_ABS_MS" \
    --argjson push "$PUSH_S" --argjson wtol "$WINDOW_TOL" \
    --argjson ra "$RTOL_ACHIEVED" --argjson r50 "$RTOL_P50" --argjson r95 "$RTOL_P95" --argjson r99 "$RTOL_P99" \
    --argjson rpp "$RTOL_MIN_PP" '
    {live:($live == "true"), pub:$pub[0], bytime:$bytime[0], bytag:$bytag[0], bytagcut:$tagcut[0], main:$main[0], xcost:$xcost,
     tol:{p50:$t50, p95:$t95, p99:$t99, abs_ms:$abs, push_s:$push, window_tol:$wtol,
          cross_run:{achieved_ratio:$ra, p50:$r50, p95:$r95, p99:$r99, min_per_process_rps:$rpp}}}'
}
# by_tag (exactly the published requests) from each process's settle cut on: the requests the
# time cut should hold. A failed query fails rw_prometheus_queries_ok; a rung with no cut gets
# no point, which fails the cross-check.
xcheck_tag_at_cut() {
  local k r steady cuts proc cut bexpr s_expr q v pt pts="[]"
  for k in $(jq -r '.points | keys[]' "$WORK/xcheck-by-time.json"); do
    r="$(jq -r ".per_process_rates[$k]" "$WORK/xcheck-meta.json")"
    steady="k6_http_req_duration_seconds{proc=~\"xcheck-p[0-9]+\",rate=\"$r\",win=\"${r}_steady\"}"
    cuts="$(jq -r ".points[$k].per_process[] | \"\(.proc) \(.settle_cut_s)\"" "$WORK/xcheck-by-time.json")"
    pt=null
    if [ -n "$cuts" ] && ! grep -q ' null$' <<<"$cuts"; then
      bexpr=""
      while read -r proc cut; do
        bexpr="${bexpr:+$bexpr or }(k6_http_req_duration_seconds{proc=\"$proc\",rate=\"$r\",win=\"${r}_steady\"} @ ${cut})"
      done <<<"$cuts"
      # No steady sample at or before any cut: none had completed, so nothing is subtracted.
      if [ "$(promv "histogram_count(sum(${bexpr}))")" = null ]; then s_expr="sum(${steady})"
      else s_expr="(sum(${steady}) - sum(${bexpr}))"; fi
      pt="$(jq -nc --argjson c "$(promv "histogram_count(${s_expr})")" '{measured_sample_count:$c}')"
      for q in 0.5 0.95 0.99; do
        v="$(promv "histogram_quantile($q, ${s_expr}) * 1000")"
        pt="$(jq -c --arg q "$q" --argjson v "$v" \
          '. + {({"0.5":"p50_ms","0.95":"p95_ms","0.99":"p99_ms"}[$q]): (if $v == null then null else ($v * 1000 | round) / 1000 end)}' <<<"$pt")"
      done
    fi
    pts="$(jq -c --argjson p "$pt" '. + [$p]' <<<"$pts")"
  done
  jq -nc --argjson p "$pts" '{points:$p}' > "$WORK/xcheck-tag-at-cut.json"
}
xcheck_phase() {
  run_phase xcheck 1 "$XRATES" vu_tag false "$XCHECK_CPUS"
  merge_phase xcheck vu_tag xcheck-by-tag
  merge_phase xcheck wallclock xcheck-by-time
  xcheck_tag_at_cut
}
[ -n "$XRATES" ] && soft xcheck_phase xcheck_phase
# A push path that broke after the pre-flight: stop before the main phase spends its rig time.
XCHECK_RW_FAILURES="$(rw_push_failures "$WORK"/xcheck-p*.log)"
if [ "$XCHECK_RW_FAILURES" -gt 0 ]; then
  die "cross-check phase: $XCHECK_RW_FAILURES remote-write send failure(s), aborting before the main phase: $(grep -m1 -hiE 'failed to send|level=error.*remote write' "$WORK"/xcheck-p*.log)"
fi

if [ "$SUT_RECVQ" = true ]; then
  _rq_max=$(( START_LEAD_S + $(tr ',' '\n' <<<"$RATES" | grep -c .) * (STEP_S + GAP_S) + QUIET_S + 120 ))
  _rq_max="$(awk -v s="$_rq_max" -v i="$SUT_RECVQ_INTERVAL_S" 'BEGIN { printf "%d", s / i + 1 }')"
  sut_recvq_sampler_start "$WORK" "$(docker inspect -f '{{.State.Pid}}' "${SUT_CONTAINER:-}" 2>/dev/null || true)" "$_rq_max" \
    "$SUT_RECVQ_INTERVAL_S" 16777216 "$PROM_CPUS"
  [ -z "$SUT_RECVQ_PID" ] || record_pid "$SUT_RECVQ_PID"
  echo "--- SUT receive-queue sampler: $(jq -c '{running, source, engine, interval_s, reason, pinned_cpus}' "$WORK/main-sut-recvq-status.json" 2>/dev/null || echo "no status")" >&2
fi
run_phase main "$N" "$RATES" "$WINDOW_MODE" true "$(IFS=';'; echo "${K6_SETS[*]}")"
if [ "$SUT_RECVQ" = true ]; then
  sut_recvq_sampler_stop
  { sut_recvq_rungs "$WORK/main-sut-recvq.csv" "$(jq -r .start_at_s "$WORK/main-meta.json")" "$STEP_S" "$GAP_S" "$SETTLE_S" \
      "$(jq -r '.agg_rates | join(",")' "$WORK/main-meta.json")" > "$WORK/main-sut-recvq-rungs.json"; } 2>/dev/null \
    || echo "WARNING: rw-multi-k6: no per-rung receive-queue summary (report-only)" >&2
fi
soft merge_main merge_phase main "$WINDOW_MODE" main-merged
# Without the merge there is nothing to judge: keep an empty ladder so every gate still runs
# (rw_rungs_measured and rw_assembly_steps_ok then fail with the reason).
[ -s "$WORK/main-merged.json" ] || jq -nc --slurpfile meta "$WORK/main-meta.json" '{meta:$meta[0], points:[]}' > "$WORK/main-merged.json"
# Series inventory for diagnosis only; not via promq, so it never affects a gate.
{ curl -sf --max-time 30 --data-urlencode 'query=count by (__name__, proc, rate) ({__name__=~"k6_http_req.*"})' \
    "$(prom_url)/api/v1/query" | jq -c '[.data.result[] | {name:.metric.__name__, proc:.metric.proc, rate:.metric.rate, series:(.value[1]|tonumber)}]' \
    > "$WORK/prom-series-inventory.json"; } 2>/dev/null || true

# --- tail attribution files (report-only; never a gate) --------------------------
# main-k6-gc.csv (every gctrace cycle), main-k6-timeseries.csv (per process and second, from Prometheus
# range queries over the ladder) and tail-instrument.json (what each holds, or why it is empty).
k6_prom_ranges() { # from_s to_s out_file -> {reqs, over_5ms, over_bound, iterations, dropped, vus: matrix | null, failed: [...]}
  # Matrices travel through files, never arguments: one ladder's matrix passes Linux's 128 KiB per-argument limit.
  local sel='proc=~"main-p[0-9]+"' h='k6_http_req_duration_seconds{proc=~"main-p[0-9]+"}' name expr r="$3.query" bound_s
  bound_s="$(awk -v ms="$P99_MAX_MS" 'BEGIN { printf "%.6f", ms / 1000 }')"
  echo '{"failed":[]}' > "$3"
  for name in reqs over_5ms over_bound iterations dropped vus; do
    case "$name" in
      reqs) expr="sum by (proc) (k6_http_reqs_total{$sel})" ;;
      # A histogram with no observations has a NaN fraction; the >= 0 filter drops it, as it adds nothing.
      over_5ms) expr="sum by (proc) (histogram_count($h) * (1 - (histogram_fraction(0, 0.005, $h) >= 0)))" ;;
      over_bound) expr="sum by (proc) (histogram_count($h) * (1 - (histogram_fraction(0, $bound_s, $h) >= 0)))" ;;
      iterations) expr="sum by (proc) (k6_iterations_total{$sel})" ;;
      dropped) expr="sum by (proc) (k6_dropped_iterations_total{$sel})" ;;
      vus) expr="max by (proc) (k6_vus{$sel})" ;;
    esac
    curl -sf --max-time 30 --data-urlencode "query=$expr" --data-urlencode "start=$1" --data-urlencode "end=$2" \
        --data-urlencode "step=1" "$(prom_url)/api/v1/query_range" 2>/dev/null | jq -c '.data.result | arrays' > "$r" 2>/dev/null \
      || : > "$r"
    jq -c --arg n "$name" --slurpfile r "$r" '.[$n] = ($r[0] // null) | if .[$n] == null then .failed += [$n] else . end' "$3" > "$3.tmp" \
      && mv -f "$3.tmp" "$3"
  done
  rm -f "$r"
}
build_tail_files() {
  local meta="$WORK/main-meta.json" i started from to span reason="" status
  echo "$K6_GC_CSV_HEADER" > "$WORK/main-k6-gc.csv"
  for ((i=0;i<N;i++)); do
    started="$(jq -r ".container_started_epoch_s[$i] // \"null\"" "$meta")"
    k6_gctrace_csv "main-p$i" "$started" "$WORK/main-p$i.log" >> "$WORK/main-k6-gc.csv"
  done
  from=$(( $(jq -r '.start_at_s' "$meta") - 5 )); to=$(( $(jq -r '.ladder_end_s' "$meta") + QUIET_S + 5 )); span=$(( to - from ))
  if [ "$span" -le 10000 ]; then # Prometheus answers at most 11,000 points per series
    k6_prom_ranges "$from" "$to" "$WORK/main-k6-ranges.json"
  else
    reason="the ladder spans ${span} s, over the 10,000 one-second points a range query returns"
    echo '{"failed":["all"]}' > "$WORK/main-k6-ranges.json"
  fi
  k6_timeseries_csv "$WORK/main-k6-ranges.json" "$WORK/main-k6-gc.csv" "$(jq -r '.start_at_s' "$meta")" \
    "$STEP_S" "$GAP_S" "$(jq -r '.agg_rates | join(",")' "$meta")" > "$WORK/main-k6-timeseries.csv"
  status="$(jq -nc --slurpfile q "$WORK/main-k6-ranges.json" --arg reason "$reason" --argjson from "$from" --argjson to "$to" \
    --arg gctrace "$K6_GCTRACE" --argjson cycles "$(( $(wc -l < "$WORK/main-k6-gc.csv") - 1 ))" --arg bound "$P99_MAX_MS" \
    --argjson rows "$(( $(wc -l < "$WORK/main-k6-timeseries.csv") - 1 ))" '
    ($q[0] // {failed:["all"]}) as $r
    | {k6_gctrace:{file:"main-k6-gc.csv", cycles:$cycles,
                 reason:(if $gctrace != "true" then "PERF_RW_K6_GCTRACE=false" elif $cycles == 0 then "no gctrace line in any main-p*.log" else null end)},
     k6_timeseries:{file:"main-k6-timeseries.csv", rows:$rows, from_s:$from, to_s:$to, step_s:1, over_bound_ms:($bound | tonumber),
                    failed_queries:$r.failed,
                    empty_series:[ $r | to_entries[] | select(.key != "failed" and (.value | type == "array" and length == 0)) | .key ],
                    reason:(if $reason != "" then $reason elif $rows == 0 then "no Prometheus series for main-p*" else null end),
                    note:"row ts covers [ts, ts + 1): counters are the difference of cumulative values at ts and ts + 1 (an empty series, as dropped_iterations until one drops, reads 0), vus the value at ts + 1; over_bound counts requests over over_bound_ms, the healthy ceiling p99 bound; empty = null"}}')"
  jq -c --argjson s "$status" '$s + {host_kernel: .}' "$WORK/host-kernel-status.json" 2>/dev/null > "$WORK/tail-instrument.json" \
    || jq -c '. + {host_kernel: null}' <<<"$status" > "$WORK/tail-instrument.json"
  rm -f "$WORK/main-k6-ranges.json"
}
# The result always gets a JSON object: a step that failed inside build_tail_files leaves at worst a reason.
write_tail_files() {
  ( build_tail_files ) || true
  jq -e 'type == "object"' "$WORK/tail-instrument.json" >/dev/null 2>&1 && return 0
  echo "WARNING: rw-multi-k6: tail-instrument.json is empty or not JSON; the tail attribution files are incomplete (report-only, not a gate)" >&2
  echo '{"error":"tail-instrument.json was empty or not JSON when the run ended; the tail attribution files are incomplete"}' > "$WORK/tail-instrument.json"
}
tail_instrument_json() { # one JSON object, or null: never empty, which would abort the result assembly
  local v # a failed jq can still print (a missing file prints null), so its output is replaced, not appended to
  v="$(jq -sc 'if length == 1 and (.[0] | type) == "object" then .[0] else null end' "$WORK/tail-instrument.json" 2>/dev/null)" || v=null
  echo "$v"
}
if [ "$TAIL_INSTRUMENT" = true ]; then write_tail_files; fi

# Degrade hook: PERF_RW_TEST_NULL_RUNG=<aggregate rate> blanks that rung's merged count, as
# when a rung's histogram query returns nothing.
if [ -n "${PERF_RW_TEST_NULL_RUNG:-}" ]; then
  jq --argjson r "$PERF_RW_TEST_NULL_RUNG" '.points |= map(if .nominal_agg_offered_rps == $r then .achieved_rps = null | .sample_count = null else . end)' \
    "$WORK/main-merged.json" > "$WORK/main-merged.tmp" && mv "$WORK/main-merged.tmp" "$WORK/main-merged.json"
fi

# Degrade hook: PERF_RW_TEST_ZERO_TAIL=true reports no client tail at every rung (the old
# fabricated zero), which rw_client_tail_consistent must reject wherever p99 is over 5.5 ms.
if [ "${PERF_RW_TEST_ZERO_TAIL:-}" = true ]; then
  jq '.points |= map(.client_over_5ms_frac = 0)' "$WORK/main-merged.json" > "$WORK/main-merged.tmp" \
    && mv "$WORK/main-merged.tmp" "$WORK/main-merged.json"
fi

# --- assemble the sweep.json-shaped result --------------------------------------
MAIN_META="$WORK/main-meta.json"
build_sweep_json() {
jq --argjson settle "$SETTLE_S" --argjson step "$STEP_S" --arg w "$WINDOW_MODE" '
  {proto:"http",
   latency_window:{settle_s:$settle, measured_s:($step-$settle), mode:$w, cut:"prometheus"},
   points:[ .points[] | del(.per_process, .nominal_agg_offered_rps, .per_process_pool, .start_skew_ms, .skew_ok, .accounting_ok, .settle_cut_ok, .cut_excess_ok, .cut_measured, .client_over_5ms_frac) ],
   vus_diagnostics:{pool_per_rung:(reduce .points[] as $p ({}; . + {($p.offered_rps|tostring): $p.per_process_pool})),
                    note:"pool_per_rung is PER PROCESS, keyed by the aggregate offered rate; vus_active_p95 is the busiest process"}}
' "$WORK/main-merged.json"
}
soft_capture SWEEP_OUT '{"proto":"http","points":[]}' sweep_json build_sweep_json
printf '%s\n' "$SWEEP_OUT" > "$WORK/sweep.json"

WINDOW_MISMATCH=""
WINDOW_MISMATCH="$(sweep_window_mismatches "$WORK/sweep.json")" || true

# Rig validity through the published rule: the worst k6 process's CPU, scaled to a
# common pin, stands in for the single client (every process must have headroom).
K6_CORES="$(cpu_count "${K6_SETS[0]}")"
K6_PIN_PCT=$(( K6_CORES * 100 ))
# Physical cores of that pin, so derive_saturation lowers its CPU ceiling for hyperthread pairs.
# shellcheck disable=SC2034  # read by derive_saturation
K6_PHYS_CORES="$(phys_core_count "${K6_SETS[0]}")"; K6_PHYS_CORES="${K6_PHYS_CORES:-null}"
PINS=""
for ((i=0;i<N;i++)); do PINS="${PINS:+$PINS }${K6_PREFIX}-main-p${i}=$(( $(cpu_count "${K6_SETS[$i]}") * 100 ))"; done
# shellcheck disable=SC2034  # read by derive_saturation
SWEEP_RATES="$(jq -r '[.points[].offered_rps] | join(",")' "$WORK/sweep.json")"
RUNG_COUNT="$(jq '.points | length' "$WORK/sweep.json")"
# CPU windows start from the earliest MEASURED rung-0 start, not the requested instant.
T0_S="$(jq -r '[(.points[0].per_process // [])[] | .rung_start_epoch_ms | select(. != null)]
  | if length == 0 then "null" else (min / 1000 | floor) end' "$WORK/main-merged.json")"
[ "$T0_S" = null ] && T0_S="$(jq -r '.start_at_s' "$MAIN_META")"
# The worst process is the highest per-process MEAN over each rung's steady window (the window
# derive_saturation reads, from T0_S, as sweep.json carries no rung starts), written to every
# sample in that window; a per-sample max across N noisy readings overstates every process's
# mean, more so as N grows. Outside a window: that max. derive_saturation's k6_cpu_pct_max
# therefore equals the mean here; per_process[].per_rung[].cpu_pct_max holds the real maxima.
{ echo "ts,cpu_pct"
  awk -v pins="$PINS" -v ref="$K6_PIN_PCT" -v t0="$T0_S" -v step="$STEP_S" -v gap="$GAP_S" \
      -v settle="$SETTLE_S" -v rungs="$RUNG_COUNT" '
    function rung(t,  i, rs) { if (t < t0) return -1; i = int((t - t0) / (step + gap)); if (i >= rungs) return -1
                               rs = t0 + i * (step + gap); return (t >= rs + settle && t < rs + step) ? i : -1 }
    BEGIN{ n=split(pins, a, " "); for(i=1;i<=n;i++){ split(a[i], kv, "="); pin[kv[1]]=kv[2] } }
    ($2 in pin) && $3 != "" { v=$3*ref/pin[$2]; if(!($1 in m) || v>m[$1]) m[$1]=v
                              k=rung($1+0); if (k >= 0) { s[k, $2]+=v; c[k, $2]++ } }
    END{ for (key in s) { split(key, kk, SUBSEP); mean=s[key]/c[key]; if (!(kk[1] in w) || mean > w[kk[1]]) w[kk[1]]=mean }
         for (t in m) { k=rung(t+0); printf "%s,%.2f\n", t, ((k >= 0) && (k in w)) ? w[k] : m[t] } }' "$WORK/main-cpu.csv" | sort -t, -k1,1n
} > "$WORK/main-k6-cpu.csv"
# The SUT's sampled CPU turns on derive_saturation's server-headroom test (a short rung with
# the server under 85% of its pin is client-limited). Off when the SUT's pin is unknown: an
# external target without an explicit PERF_RW_SERVER_CPUS.
# shellcheck disable=SC2034  # read by derive_saturation
SERVER_PIN_PCT=0
SUT_CPU_LOG="$WORK/main-sut-cpu.csv"
: > "$SUT_CPU_LOG"
if [ -n "$SUT_CONTAINER" ] && { [ -n "$LAUNCH_SUT" ] || [ -n "${PERF_RW_SERVER_CPUS:-}" ]; }; then
  # shellcheck disable=SC2034
  SERVER_PIN_PCT=$(( $(cpu_count "$SERVER_CPUS") * 100 ))
  { echo "ts,cpu_pct"
    awk -v c="$SUT_CONTAINER" '$2 == c && $3 != "" { printf "%s,%s\n", $1, $3 }' "$WORK/main-cpu.csv"
  } > "$SUT_CPU_LOG"
fi
soft_capture SATURATION_JSON '{"ladder":[],"rig_valid_peak_achieved_rps":null,"saturation_rps":null}' saturation \
  derive_saturation "$WORK/sweep.json" "$WORK/main-k6-cpu.csv" "$T0_S" "$SUT_CPU_LOG"
# derive_saturation reads a rung with no CPU sample as 0% (headroom), so count them here.
CPU_SAMPLES="[]"
for ((k=0;k<RUNG_COUNT;k++)); do
  ws=$(( T0_S + k * (STEP_S + GAP_S) + SETTLE_S )); we=$(( T0_S + k * (STEP_S + GAP_S) + STEP_S ))
  cnt="$(awk -F',' -v a="$ws" -v b="$we" 'NR>1 && $1>=a && $1<b {n++} END{print n+0}' "$WORK/main-k6-cpu.csv")"
  CPU_SAMPLES="$(jq -c --argjson c "$cnt" '. + [$c]' <<<"$CPU_SAMPLES")"
done

NOW_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
SYNTH="$(jq -nc --slurpfile s "$WORK/sweep.json" --argjson sat "$SATURATION_JSON" --arg ts "$NOW_ISO" \
  --argjson scpus "$(cpu_count "$SERVER_CPUS")" --argjson k6rt "$(k6_runtime_json)" '
  {schema_version:2, timestamp_utc:$ts, config:{k6_runtime:$k6rt}, agent:{server_cpus:$scpus}, sweep:$s[0], saturation:$sat}')"
# This arm alone adds the p99 bound (P99_MAX_MS, validated at startup); every other caller of
# the filter stays p50-only (docs/code/performance-measurement.md, "The multi-k6 arm's p99 bound").
headline_for() { # a SYNTH-shaped run
  jq -c --arg now "$NOW_ISO" --argjson lat_mult 3 --argjson keep "$KEEP" --arg fix_date "2026-09-16" \
    --argjson p99_max_ms "$P99_MAX_MS" -f "$FIGURES_JQ" <<<"$1" | jq -c '.headline'
}
# HEADLINE is the whole-rung (unmasked) headline; the result's .headline follows HEADLINE_RULE (below).
headline_of() { headline_for "$SYNTH"; }
soft_capture HEADLINE null headline headline_of

# --- GC-masked figure (never a validity gate) -----------------------------------
# .gc_masked: per rung, the requests pushed in seconds where no k6 process was in or just after a GC
# cycle (lib/perf-tail-instrument.sh), with p99 from the native histograms over those seconds, and the
# ceiling the rule above gives on that p99. Its queries bypass promq, so a failure here is a null
# with a reason and never reaches rw_prometheus_queries_ok. Docs: performance-measurement.md, "The GC-masked figure".
gc_masked_query() { # expr -> a finite number, or null
  local v
  v="$(curl -sf --max-time 30 --data-urlencode "query=$1" "$(prom_url)/api/v1/query" 2>/dev/null \
    | jq -r '.data.result | if length == 1 then .[0].value[1] else "null" end' 2>/dev/null)" || v=null
  [[ "$v" =~ ^-?[0-9]+(\.[0-9]+)?(e[-+]?[0-9]+)?$ ]] || v=null
  echo "$v"
}
gc_masked_unavailable() { # reason
  jq -nc --arg r "$1" --arg rule "$HEADLINE_RULE" \
    '{available:false, reason:$r, report_only:($rule != "gc_masked_p99"), rungs:[], healthy_ceiling:null}'
}
build_gc_masked() {
  local base rungs="[]" k count rate nominal entry windows quiet expr vals q synth head bound_s
  [ "$TAIL_INSTRUMENT" = true ] || { gc_masked_unavailable "PERF_RW_TAIL_INSTRUMENT=false: no per-second series"; return 0; }
  [ "$K6_GCTRACE" = true ] || { gc_masked_unavailable "PERF_RW_K6_GCTRACE=false: no GC cycle times, so no second can be called quiet"; return 0; }
  base="$(k6_gc_masked_rungs "$WORK/main-k6-timeseries.csv" "$WORK/main-k6-gc.csv" "$N" "$SETTLE_S" "$PUSH_S")"
  if [ "$(jq -r '.gc_procs' <<<"$base")" != "$N" ]; then
    gc_masked_unavailable "main-k6-gc.csv has GC cycles for $(jq -r '.gc_procs' <<<"$base") of $N k6 processes (a missing gctrace or container start time), so no second can be called quiet"; return 0
  fi
  if [ "$(jq -r '.rungs | length' <<<"$base")" = 0 ]; then
    gc_masked_unavailable "main-k6-timeseries.csv has no row inside a rung's measured window (the Prometheus series are missing)"; return 0
  fi
  bound_s="$(awk -v ms="$P99_MAX_MS" 'BEGIN { printf "%.6f", ms / 1000 }')"
  count="$(jq -r '.agg_rates | length' "$MAIN_META")"
  for ((k=0;k<count;k++)); do
    nominal="$(jq -r ".agg_rates[$k]" "$MAIN_META")"; rate="$(jq -r ".per_process_rates[$k]" "$MAIN_META")"
    entry="$(jq -c --argjson r "$nominal" '[.rungs[] | select(.rung_offered_rps == $r)] | first // null' <<<"$base")"
    vals='{"requests":null,"over_5ms_frac":null,"over_bound_frac":null,"p99_ms":null,"p999_ms":null}'
    quiet="$(jq -r '.seconds.quiet // 0' <<<"$entry")"
    if [ "$entry" != null ] && [ "$quiet" -ge "$GC_MASK_MIN_QUIET_S" ]; then
      windows="$(jq -c '.quiet_windows' <<<"$entry")"
      expr="$(k6_gc_masked_expr "k6_http_req_duration_seconds{proc=~\"main-p[0-9]+\",rate=\"$rate\"}" "$windows")"
      for q in "requests|histogram_count($expr)" "over_5ms_frac|1 - histogram_fraction(0, 0.005, $expr)" \
               "over_bound_frac|1 - histogram_fraction(0, $bound_s, $expr)" \
               "p99_ms|histogram_quantile(0.99, $expr) * 1000" "p999_ms|histogram_quantile(0.999, $expr) * 1000"; do
        vals="$(jq -c --arg key "${q%%|*}" --argjson v "$(gc_masked_query "${q#*|}")" '.[$key] = $v' <<<"$vals")"
      done
    fi
    rungs="$(jq -c --argjson e "$entry" --argjson v "$vals" --argjson min "$GC_MASK_MIN_QUIET_S" --argjson nominal "$nominal" \
      --argjson p "$(jq -c ".sweep.points[$k] // null" <<<"$SYNTH")" '
      def r5: if . == null then null else ([., 0] | max) * 100000 | round / 100000 end;
      def r3: if . == null then null else (. * 1000 | round) / 1000 end;
      def frac($a; $b): if $a == null or $b == null or $b <= 0 then null else ($a / $b) | r5 end;
      ($e != null and $e.seconds.quiet >= $min) as $enough
      | ($enough and $v.p99_ms != null and $v.requests != null and $v.requests > 0) as $ok
      | . + [{offered_rps: ($p.offered_rps // $nominal),
              seconds: ($e.seconds // {measured:0, quiet:0, gc:0, incomplete:0}),
              quiet_windows: ($e.quiet_windows // []),
              requests: (if $ok then $v.requests else null end),
              over_5ms_frac: (if $ok then $v.over_5ms_frac | r5 else null end),
              over_bound_frac: (if $ok then $v.over_bound_frac | r5 else null end),
              p99_ms: (if $ok then $v.p99_ms | r3 else null end),
              p999_ms: (if $ok then $v.p999_ms | r3 else null end),
              rows: (if $e == null or $e.seconds.quiet == 0 then null
                     else {requests: $e.rows.requests, over_5ms_frac: frac($e.rows.over_5ms; $e.rows.requests),
                           over_bound_frac: frac($e.rows.over_bound; $e.rows.requests)} end),
              unmasked_p99_ms: ($p.p99_ms // null),
              reason: (if $ok then null
                       elif $e == null then "no row of this rung in main-k6-timeseries.csv"
                       elif $enough | not then "\($e.seconds.quiet) quiet second(s) of \($e.seconds.measured), under the minimum of \($min)"
                       else "Prometheus returned no histogram for the quiet seconds" end)}]' <<<"$rungs")"
  done
  # A rung without a masked figure gets a null p99, which the bounded rule fails.
  synth="$(jq -c --argjson g "$rungs" '.sweep.points |= [ to_entries[] | .value + {p99_ms: $g[.key].p99_ms} ]' <<<"$SYNTH")"
  head="$(headline_for "$synth")"
  jq -nc --argjson g "$rungs" --argjson h "$head" --argjson unmasked "$HEADLINE" --argjson min "$GC_MASK_MIN_QUIET_S" \
    --argjson settle "$SETTLE_S" --argjson push "$PUSH_S" --arg bound "$P99_MAX_MS" --arg rule "$HEADLINE_RULE" '
    ($bound | tonumber) as $b
    | {available: true, reason: null, report_only: ($rule != "gc_masked_p99"),
       p99_max_ms: $b, min_quiet_s: $min, measured_from_s: ($settle + $push),
       method: "per rung, the native histograms of every k6 process over the union of quiet seconds: sum over windows [a, b) of (cumulative at b) - (cumulative at a), then histogram_quantile. A second is quiet when no k6 process has a GC cycle (gctrace: sweep termination to mark termination) overlapping the \($push + 1) s its row can hold requests from; seconds run from settle + one push interval into the rung",
       error: "a quantile is interpolated in a native-histogram bucket 10% wide (about 5%); a request is counted in the push that carried it, up to \($push) s after it completed, which is why the second after a cycle is masked too; the quiet seconds are a sample of the rung, not all of it; p50, achieved rate and errors in the ceiling rule stay those of the whole rung",
       rungs: $g,
       headline: $h,
       healthy_ceiling: {
         rps: ($h.healthy_ceiling_rps // null), p99_ms: ($h.healthy_ceiling_p99_ms // null), p99_max_ms: $b,
         unmasked_rps: ($unmasked.healthy_ceiling_rps // null),
         no_masked_figure_at: [ $g[] | select(.p99_ms == null) | .offered_rps ],
         note: "the first-failure rule of .headline with each rung p99 replaced by its masked p99; a rung without a masked figure (listed in no_masked_figure_at) fails the bound, so a missing figure can only lower this ceiling"}}'
}
# As the headline: an invalid run states no ceiling; the computed one stays for diagnosis. The full
# masked headline is dropped: under gc_masked_p99 it is the result's .headline.
gc_masked_result() { # valid
  jq -c --arg valid "$1" 'del(.headline) | if $valid == "true" or .healthy_ceiling == null then .
    else . + {healthy_ceiling: null, healthy_ceiling_if_valid: .healthy_ceiling} end' <<<"$GC_MASKED"
}
GC_MASKED="$( ( build_gc_masked ) 2>/dev/null )" || GC_MASKED=""
if ! jq -e 'type == "object"' >/dev/null 2>&1 <<<"$GC_MASKED"; then
  echo "WARNING: rw-multi-k6: the GC-masked figure could not be assembled (not a validity gate; under gc_masked_p99 the run states no ceiling)" >&2
  GC_MASKED="$(gc_masked_unavailable "the GC-masked figure could not be assembled")"
fi

# --- the headline rule ------------------------------------------------------------
# gc_masked_p99: the ceiling is .gc_masked's, a rung without a masked figure fails the bound, and a
# missing figure gives no headline, never the whole-rung one. condition_3 is the headline rule's criterion (3)
# under this rule. Docs: performance-measurement.md, "The headline rule".
headline_by_rule() { # -> {headline, rule}
  jq -nc --arg rule "$HEADLINE_RULE" --argjson unmasked "$HEADLINE" --argjson g "$GC_MASKED" --arg bound "$P99_MAX_MS" '
    ($bound | tonumber) as $b
    | ($unmasked.healthy_ceiling_rps // null) as $urps
    | if $rule == "unmasked_p99" then
        {headline: $unmasked,
         rule: {name: $rule, p99: "the whole rung", p99_max_ms: $b, reason: null, unmasked_ceiling_rps: $urps, condition_3: null}}
      else
        (if $g.available == true then $g.headline else null end) as $h
        | (if $h == null then null
           else [ $g.rungs[] | select(.offered_rps > $h.healthy_ceiling_rps and (.p99_ms == null or .p99_ms > $b)) ]
                | sort_by(.offered_rps) | first end) as $f
        | {headline: $h,
           rule: {name: $rule,
             p99: "GC-masked: per rung, over the seconds with no k6 process in or just after a GC cycle (.gc_masked)",
             p99_max_ms: $b,
             reason: (if $h != null then null
                      elif $g.available != true then "no GC-masked figure (\($g.reason)), so this rule states no ceiling; it never falls back to the whole-rung p99"
                      else "no rung holds the bound on its masked p99" end),
             unmasked_ceiling_rps: $urps,
             condition_3: ({first_failure: (if $f == null then null
                              else {offered_rps: $f.offered_rps, p99_ms: $f.p99_ms, unmasked_p99_ms: $f.unmasked_p99_ms,
                                    quiet_s: $f.seconds.quiet, reason: $f.reason} end),
                            evidence: "the client and transport shares over 5 ms at first_failure are .tail_localisation.rungs[] (not a gate)"}
               + if $h == null then {ok: false, reason: "no masked ceiling"}
                 elif $f == null then {ok: false, reason: "no rung above the ceiling fails the masked bound, so the tail was not observed"}
                 elif $f.p99_ms == null then {ok: false, reason: "the first rung above the ceiling that fails the bound, \($f.offered_rps), has no masked figure (\($f.reason)), so its tail was not observed in GC-free seconds"}
                 else {ok: true, reason: null} end)}}
      end'
}
soft_capture HEADLINE_BY_RULE '{"headline":null,"rule":null}' headline_rule headline_by_rule

# --- per-process, CPU and Prometheus cost ---------------------------------------
cpu_stats() { # csv name from_s to_s -> {mean,max,samples} over [from_s, to_s), as derive_saturation
  awk -v n="$2" -v a="$3" -v b="$4" '$2==n && $1>=a && $1<b && $3!="" {s+=$3; c++; if($3+0>m) m=$3+0}
    END{ if(c) printf "{\"mean\":%.1f,\"max\":%.1f,\"samples\":%d}", s/c, m, c; else printf "{\"mean\":null,\"max\":null,\"samples\":0}" }' "$1"
}
L0="$(jq -r '.start_at_s' "$MAIN_META")"; L1="$(jq -r '.ladder_end_s' "$MAIN_META")"
# cpu_frac_of_logical_pin divides by every logical cpu in the pin; cpu_frac_of_ceiling divides the
# pin-scaled mean by derive_saturation's hyperthread-adjusted ceiling, the figure its k6 CPU test uses.
build_per_process() {
  local MAIN_COST PER_PROCESS="[]" c pin per_rung ws we i k ceiling
  MAIN_COST="$(cpu_cost_json main)"
  ceiling="$(jq -r '.client_cpu_ceiling_pct // "null"' <<<"$SATURATION_JSON")"
  for ((i=0;i<N;i++)); do
    c="${K6_PREFIX}-main-p${i}"
    pin=$(( $(cpu_count "${K6_SETS[$i]}") * 100 ))
    per_rung="[]"
    for ((k=0;k<RUNG_COUNT;k++)); do
      ws=$(( T0_S + k * (STEP_S + GAP_S) + SETTLE_S )); we=$(( T0_S + k * (STEP_S + GAP_S) + STEP_S ))
      per_rung="$(jq -c --argjson st "$(cpu_stats "$WORK/main-cpu.csv" "$c" "$ws" "$we")" --argjson pin "$pin" \
        --argjson ref "$K6_PIN_PCT" --argjson ceil "$ceiling" \
        --argjson k "$k" --argjson i "$i" --slurpfile m "$WORK/main-merged.json" '
        . + [{offered_rps:$m[0].points[$k].offered_rps, cpu_pct_mean:$st.mean, cpu_pct_max:$st.max, cpu_samples:$st.samples,
              cpu_frac_of_logical_pin:(if $st.mean == null then null else (($st.mean / $pin) * 1000 | round) / 1000 end),
              cpu_frac_of_ceiling:(if $st.mean == null or $ceil == null or $ceil <= 0 then null
                                   else (($st.mean * $ref / $pin / $ceil) * 1000 | round) / 1000 end),
              drop_fraction:$m[0].points[$k].per_process[$i].drop_fraction}]' <<<"$per_rung")"
    done
    PER_PROCESS="$(jq -c --argjson i "$i" --arg set "${K6_SETS[$i]}" --argjson pin "$pin" --argjson per_rung "$per_rung" \
      --argjson cpu "$(cpu_stats "$WORK/main-cpu.csv" "$c" "$L0" "$L1")" --argjson cost "$MAIN_COST" \
      --argjson ceil "$ceiling" \
      --argjson exit "$(jq ".exit_codes[$i] // null" "$MAIN_META")" --slurpfile s <(cat "$WORK/main-p${i}.json" 2>/dev/null || echo 'null') '
      . + [{index:$i, cpuset:$set, pin_pct:$pin, cpu_ceiling_pct:$ceil, exit_code:$exit,
            summary_present:($s[0] != null),
            cpu_pct_over_ladder:$cpu, cpu_us_per_request:$cost[$i].cpu_us_per_request, requests:$cost[$i].requests,
            vus_initialized:($s[0].vus_diagnostics.vus_initialized_global_max // null),
            setup_end_minus_start_at_ms:(if $s[0].wallclock.start_at_ms and $s[0].wallclock.setup_end_ms
                                         then ($s[0].wallclock.setup_end_ms - $s[0].wallclock.start_at_ms) else null end),
            per_rung:$per_rung}]' <<<"$PER_PROCESS")"
  done
  echo "$PER_PROCESS"
}
soft_capture PER_PROCESS '[]' per_process build_per_process
# Report-only, never a gate: Go GC CPU per process and rung steady window, from the gctrace
# lines ("gc N @<s since start>s P%: ... ms clock, a+b/c/d+e ms cpu, ..."), in docker stats'
# unit (% of one CPU), so a k6 CPU burst can be told apart as GC or not. null when unavailable.
k6_gc_json() {
  [ "$K6_GCTRACE" = true ] || { echo null; return 0; }
  local out="[]" i started rung
  for ((i=0;i<N;i++)); do
    started="$(jq -r ".container_started_epoch_s[$i] // \"null\"" "$MAIN_META")"
    rung="$(awk -v st="$started" -v t0="$T0_S" -v step="$STEP_S" -v gap="$GAP_S" -v settle="$SETTLE_S" \
        -v rungs="$RUNG_COUNT" -v rates="$SWEEP_RATES" '
      BEGIN { if (st == "null") exit; split(rates, off, ",") }
      $1 == "gc" && $3 ~ /^@[0-9.]+s$/ && $10 == "cpu," {
        t = st + substr($3, 2) + 0; k = int((t - t0) / (step + gap)); rs = t0 + k * (step + gap)
        if (t < t0 || k >= rungs || t < rs + settle || t >= rs + step) next
        m = split($8, v, /[+\/]/); ms = 0; for (j = 1; j <= m; j++) ms += v[j]
        cpu[k] += ms; n[k]++ }
      END { if (st == "null") exit; printf "["
        for (k = 0; k < rungs; k++) printf "%s{\"offered_rps\":%s,\"gc_cycles\":%d,\"gc_cpu_pct\":%.1f}", (k ? "," : ""), off[k + 1], n[k], cpu[k] / ((step - settle) * 10)
        print "]" }' "$WORK/main-p${i}.log")"
    out="$(jq -c --argjson i "$i" --argjson r "${rung:-null}" '. + [{index:$i, per_rung:$r}]' <<<"$out")"
  done
  echo "$out"
}
K6_GC="$(k6_gc_json 2>/dev/null)" || K6_GC=null
jq -e . >/dev/null 2>&1 <<<"$K6_GC" || K6_GC=null
# Every measured process (the cross-check's too, when its phase ran); a missing log reads as null.
k6_interrupted_all() {
  local out="[]" ph n i
  for ph in xcheck main; do
    n="$(jq -r '.n // empty' "$WORK/$ph-meta.json" 2>/dev/null || true)"
    if [ "$ph" = main ]; then n="${n:-$N}"; elif [ -z "$n" ]; then continue; fi
    for ((i=0;i<${n:-0};i++)); do
      out="$(jq -c --argjson e "$(k6_interrupted_json "$ph-p$i" "$WORK/$ph-p$i.log")" '. + [$e]' <<<"$out")"
    done
  done
  echo "$out"
}
INTERRUPTED_FAIL='{"name":"rw_no_interrupted_iterations","ok":false,"detail":"assembly failed: the per-process interrupted-iteration counts could not be built"}'
K6_INTERRUPTED="$(k6_interrupted_all)" || K6_INTERRUPTED=""
if jq -e 'type == "array"' >/dev/null 2>&1 <<<"$K6_INTERRUPTED"; then
  INTERRUPTED_CHECK="$(k6_interrupted_check "$K6_INTERRUPTED")" || INTERRUPTED_CHECK="$INTERRUPTED_FAIL"
else
  K6_INTERRUPTED="[]"; INTERRUPTED_CHECK="$INTERRUPTED_FAIL"
fi
SUT_CPU="$( [ -n "$SUT_CONTAINER" ] && cpu_stats "$WORK/main-cpu.csv" "$SUT_CONTAINER" "$L0" "$L1" || echo '{"mean":null,"max":null}')"
PROM_CPU="$(cpu_stats "$WORK/main-cpu.csv" "$PROM_NAME" "$L0" "$L1")"
# Send failures and slow flushes both invalidate: k6 warns a slow flush may drop samples.
RW_FAILURES="$(rw_push_failures "$WORK"/*-p*.log)"
SLOW_FLUSHES="$( { grep -hoE 'took [0-9.]+(ms|s) while flush period' "$WORK"/*-p*.log 2>/dev/null || true; } \
  | awk '{d=$2; v=d+0; if (d ~ /ms$/) v=v/1000; n++; if (v>m) m=v} END{printf "{\"count\":%d,\"max_took_s\":%s}", n, (n ? sprintf("%.3f", m) : "null")}')"

# --- cross-check ------------------------------------------------------------------
CROSS='{"attempted":false}'
# Report-only comparisons: rungs with no single-process counterpart (above the cross-check
# cap) or with a missing figure are labelled, never divided. Missing inputs fail the step.
build_cross() { cross_input | jq -c -f "$CROSS_JQ"; }
if [ -n "$XRATES" ]; then
  soft_capture CROSS '{"attempted":true,"error":"cross-check assembly failed (see rw_assembly_steps_ok)"}' cross_check build_cross
fi
# cross_run is report-only (two separate runs), so it gates nothing, but it is not left silent.
if [ "$(jq -r '.cross_run.agrees | if . == null then "null" else tostring end' <<<"$CROSS" 2>/dev/null)" = false ]; then
  echo "WARNING: rw-multi-k6 cross_run (report-only, not a gate): the N-process ladder disagrees with the single-process cross-check run beyond the run-to-run tolerances ($(jq -c '.tolerances.cross_run' <<<"$CROSS" 2>/dev/null)); single vs multi per rung:" >&2
  jq -r '(.cross_run.rungs // [])[] | select((.status == "compared" or .status == "incomplete") and .ok != true)
    | "    \(.offered_rps) rps [\(.status); outside: \([ (if .achieved_ratio.ok then empty else "achieved" end), (if .latency.p50.ok then empty else "p50" end), (if .latency.p95.ok then empty else "p95" end), (if .latency.p99.ok then empty else "p99" end) ] | join(","))]: achieved/offered \(.achieved_ratio.single) vs \(.achieved_ratio.multi); p50 \(.latency.p50.published) vs \(.latency.p50.remote_write) ms; p95 \(.latency.p95.published) vs \(.latency.p95.remote_write) ms; p99 \(.latency.p99.published) vs \(.latency.p99.remote_write) ms"' <<<"$CROSS" >&2 || true
  [ "$(jq -r '.cross_run.compared // 0' <<<"$CROSS" 2>/dev/null)" != 0 ] || echo "    no rung had a single-process counterpart to compare" >&2
fi
if [ "$(jq -r '.cross_run.below_floor // 0' <<<"$CROSS" 2>/dev/null)" != 0 ]; then
  echo "--- rw-multi-k6 cross_run: $(jq -r '.cross_run.below_floor' <<<"$CROSS") rung(s) below ${RTOL_MIN_PP} rps per process reported, not judged (PERF_RW_CROSSRUN_MIN_PER_PROC_RPS); agrees=$(jq -r '.cross_run.agrees | if . == null then "n/a" else tostring end' <<<"$CROSS"):" >&2
  jq -r '.cross_run.rungs[] | select(.status == "below per-process floor")
    | "    \(.offered_rps) rps (\(.per_process_rps) per process): p50 \(.latency.p50.published) vs \(.latency.p50.remote_write) ms; p95 \(.latency.p95.published) vs \(.latency.p95.remote_write) ms; p99 \(.latency.p99.published) vs \(.latency.p99.remote_write) ms"' <<<"$CROSS" >&2 || true
fi

# --- validity (fail closed) ---------------------------------------------------------
VALIDITY="$(jq -nc --slurpfile m "$WORK/main-merged.json" --argjson pp "$PER_PROCESS" --arg wm "$WINDOW_MISMATCH" \
  --argjson cross "$CROSS" --argjson rwfail "${RW_FAILURES:-0}" --argjson cpusamples "$CPU_SAMPLES" \
  --argjson slow "$SLOW_FLUSHES" --argjson intcheck "$INTERRUPTED_CHECK" \
  --argjson promfail "$(wc -l < "$PROM_FAILURES" | tr -d ' ')" --arg promfirst "$(head -1 "$PROM_FAILURES")" \
  --argjson push "$PUSH_S" --argjson n "$N" --arg stepfail "$ASSEMBLY_FAILURES" \
  --argjson want "$(jq '.agg_rates | length' "$MAIN_META")" '
  def check($name; $ok; $detail): {name:$name, ok:$ok, detail:(if $ok then "ok" else $detail end)};
  ($m[0].points) as $P
  | [ check("rw_assembly_steps_ok"; $stepfail == "";
        "step(s) failed and were replaced by defaults: \($stepfail); the result was still written"),
      check("rw_rungs_measured"; ($P|length) == $want
            and all($P[]; (.sample_count|type) == "number" and (.achieved_rps|type) == "number" and (.p50_ms|type) == "number");
        "expected \($want) rungs with a count, achieved rps and p50; missing at " + ([ $P[] | select(((.sample_count|type) == "number" and (.achieved_rps|type) == "number" and (.p50_ms|type) == "number")|not) | "\(.offered_rps) (count=\(.sample_count), achieved=\(.achieved_rps), p50=\(.p50_ms))" ] | join(", ")) + " (\($P|length) rung(s) merged)"),
      check("rw_accounting"; all($P[]; .accounting_ok);
        "Prometheus request counts differ from the processes own counts (a lost remote-write flush or a missing process summary) at " + ([ $P[] | select(.accounting_ok|not) | "\(.offered_rps) [" + ([ .per_process[] | select(.accounted|not) | "\(.proc) summary=\(.summary_count) prometheus=\(.prometheus_count) hist=\(.prometheus_histogram_count)" ] | join(", ")) + "]" ] | join("; "))),
      check("rw_processes_exited_cleanly"; ($pp|length) == $n and all($pp[]; .summary_present and .exit_code == 0);
        "\($pp|length)/\($n) process entries; " +
        ([ $pp[] | select((.summary_present and .exit_code == 0)|not) | "p\(.index) exit=\(.exit_code) summary=\(.summary_present)" ] | join(", "))),
      check("rw_start_skew_within_bound"; all($P[]; .skew_ok);
        "over bound (or unknown: a process reported no rung start) at " + ([ $P[] | select(.skew_ok|not) | "\(.offered_rps): \(.start_skew_ms // "unknown") ms" ] | join(", "))),
      check("rw_settle_cut_within_bound"; all($P[]; .settle_cut_ok);
        "a settle cut landed before its boundary or more than \(2 * $push) s after it at " + ([ $P[] | select(.settle_cut_ok|not) | "\(.offered_rps): late_ms=\([ .per_process[] | .settle_cut_late_ms ])" ] | join(", "))),
      check("rw_settle_cut_measured"; all($P[]; .cut_measured);
        ([ $P[] | . as $p | .per_process[] | select(.cut_measured|not)
           | "cut excess could not be measured for proc \(.proc) rung \($p.offered_rps) (cut=\(.settle_cut_s), excess=\(.cut_excess_requests))" ] | join("; "))),
      check("rw_settle_cut_excess_bounded"; all($P[]; .cut_excess_ok);
        "the settle cut excess is missing or exceeds 2 push intervals at the offered rate at " + ([ $P[] | select(.cut_excess_ok|not) | "\(.offered_rps): " + ([ .per_process[] | select(.cut_excess_ok|not) | "\(.proc) excess=\(.cut_excess_requests) > \(.cut_excess_max)" ] | join(", ")) ] | join("; "))),
      check("rw_prometheus_queries_ok"; $promfail == 0;
        "\($promfail) Prometheus quer(y/ies) failed, so merged figures are missing; first: \($promfirst)"),
      check("rw_cpu_sampled_every_rung"; all($cpusamples[]; . > 0);
        "no k6 CPU sample in the steady window of rung index(es) " + ([ range(0; $cpusamples|length) | select($cpusamples[.] == 0) | tostring ] | join(", "))),
      check("rw_window_accounts_every_request"; $wm == ""; $wm),
      # A rung whose p99 is over 5 ms has at least 1% of requests over 5 ms; 5.5 / 0.005 leave
      # room for the two interpolations disagreeing inside one native-histogram bucket.
      check("rw_client_tail_consistent"; all($P[]; (.p99_ms == null) or (.p99_ms <= 5.5)
                                                or ((.client_over_5ms_frac | type) == "number" and .client_over_5ms_frac > 0.005));
        "p99 over 5.5 ms but no client share over 5 ms (<= 0.5% or missing) at " + ([ $P[] | select((.p99_ms != null) and (.p99_ms > 5.5) and (((.client_over_5ms_frac | type) == "number" and .client_over_5ms_frac > 0.005) | not)) | "\(.offered_rps): p99 \(.p99_ms) ms, client_over_5ms_frac \(.client_over_5ms_frac)" ] | join(", "))),
      check("rw_cross_check_same_requests"; (($cross.attempted | not) or ($cross.same_requests.equivalent and $cross.same_requests.accounting_ok));
        "the same requests measured by the published summary and by the Prometheus merge disagree beyond tolerance at " + (($cross.same_requests.failed // []) | if length == 0 then "(no rung; see cross_check.same_requests)" else join(" | ") end)),
      check("rw_no_failed_pushes"; $rwfail == 0; "\($rwfail) k6 log line(s) report a remote-write send failure"),
      check("rw_no_slow_flushes"; $slow.count == 0;
        "\($slow.count) remote-write flush(es) took longer than the \($push) s push interval (max \($slow.max_took_s) s); k6 warns samples may be dropped"),
      $intcheck
    ] as $checks
  | {valid:all($checks[]; .ok), checks:$checks,
     reasons:[ $checks[] | select(.ok|not) | "\(.name): \(.detail)" ]}')"

# --- output -------------------------------------------------------------------------
GC_MASKED_OUT="$(gc_masked_result "$(jq -r '.valid' <<<"$VALIDITY")" 2>/dev/null)" || GC_MASKED_OUT=""
jq -e 'type == "object"' >/dev/null 2>&1 <<<"$GC_MASKED_OUT" || GC_MASKED_OUT=null
jq -n --argjson synth "$SYNTH" --argjson headline "$HEADLINE" --argjson byrule "$HEADLINE_BY_RULE" --slurpfile m "$WORK/main-merged.json" \
  --argjson validity "$VALIDITY" --argjson cross "$CROSS" --argjson pp "$PER_PROCESS" \
  --argjson sutcpu "$SUT_CPU" --argjson promcpu "$PROM_CPU" --argjson n "$N" --argjson push "$PUSH_S" \
  --argjson quiet "$QUIET_S" --argjson lead "$START_LEAD_S" --argjson maxskew "$MAX_SKEW_MS" \
  --arg wmode "$WINDOW_MODE" --arg vudiag "$VU_DIAGNOSTICS" --arg degrade "$DEGRADE" \
  --arg scpus "$SERVER_CPUS" --arg pcpus "$PROM_CPUS" --arg k6img "$K6_IMAGE" --arg promimg "$PROM_IMAGE" \
  --arg msimg "$MOCKSERVER_IMAGE" --argjson host_cores "$HOST_CORES" --argjson acct_tol "$ACCOUNT_TOL" \
  --argjson cpusamples "$CPU_SAMPLES" --argjson t0 "$T0_S" --arg p99max "$P99_MAX_MS" \
  --argjson slow "$SLOW_FLUSHES" --argjson rwfail "${RW_FAILURES:-0}" --argjson k6gc "$K6_GC" --argjson k6int "$K6_INTERRUPTED" \
  --arg hook_cut "${PERF_RW_TEST_CUT_FAULT:-}" --arg hook_step "${PERF_RW_TEST_FAIL_STEP:-}" --arg hook_null "${PERF_RW_TEST_NULL_RUNG:-}" --arg hook_zero "${PERF_RW_TEST_ZERO_TAIL:-}" \
  --argjson promwarn "$(head -n 50 "$PROM_WARNINGS" | jq -R . | jq -sc .)" --argjson placement "$(placement_result_json)" \
  --arg rates_source "$RATES_SOURCE" --arg ladder_profile "$LADDER_PROFILE" --argjson ab "$AB_JSON" \
  --argjson tailinst "$(tail_instrument_json)" --argjson gcmasked "$GC_MASKED_OUT" '
  ($m[0].points) as $P
  | $synth + {
      rig_valid_peak_achieved_rps: $synth.saturation.rig_valid_peak_achieved_rps,
      saturation_rps: $synth.saturation.saturation_rps,
      # An invalid run carries no headline; the computed one stays for diagnosis. .headline follows
      # .headline_rule; .headline_unmasked is the whole-rung one, kept for comparison.
      headline: (if $validity.valid then $byrule.headline else null end),
      headline_if_valid: $byrule.headline,
      headline_rule: $byrule.rule,
      headline_unmasked: (if $validity.valid then $headline else null end),
      valid: $validity.valid,
      invalid_reasons: $validity.reasons,
      validity: {valid: $validity.valid, checks: $validity.checks},
      placement: $placement,
      method: {
        method: "remote_write_multi_k6", procs: $n, push_interval_s: $push, quiet_s: $quiet,
        window_mode: $wmode, lean_summary: true, vu_diagnostics: ($vudiag == "true"),
        start_lead_s: $lead, max_start_skew_ms_bound: $maxskew,
        observed_max_start_skew_ms: ([ $P[] | .start_skew_ms | select(. != null) ] | max),
        max_setup_end_minus_start_at_ms: ([ $pp[] | .setup_end_minus_start_at_ms | select(. != null) ] | max),
        native_histograms: true, histogram_bucket_factor: 1.1, accounting_tolerance: $acct_tol,
        healthy_ceiling_p99_max_ms: ($p99max | tonumber? // $p99max),
        server_cpus: $scpus, prometheus_cpus: $pcpus, host_cores: $host_cores,
        ladder: {profile: $ladder_profile, source: $rates_source},
        ab: $ab,
        k6_image: $k6img, prometheus_image: $promimg, mockserver_image: $msimg,
        cpu_window_t0_s: $t0,
        degrade: (if $degrade == "" then null else $degrade end),
        test_hooks: ({cut_fault:$hook_cut, fail_step:$hook_step, null_rung:$hook_null, zero_tail:$hook_zero} | with_entries(select(.value != "")))
      },
      accounting: [ $P[] | {offered_rps, ok:.accounting_ok, per_process:[ .per_process[] | {proc, summary_count, prometheus_count, prometheus_histogram_count, accounted} ]} ],
      windows: [ $P[] | {offered_rps, start_skew_ms, skew_ok, settle_cut_ok,
                         settle_cut_late_ms:[ .per_process[] | .settle_cut_late_ms ],
                         pre_boundary_sample_age_ms:[ .per_process[] | .pre_boundary_sample_age_ms ],
                         cut_count_since_prev_sample:[ .per_process[] | .cut_count_since_prev_sample ],
                         cut_excess_requests:[ .per_process[] | .cut_excess_requests ], cut_excess_ok,
                         measured_sample_count, settle_excluded} ]
               | [ range(0; length) as $k | .[$k] + {k6_cpu_samples: $cpusamples[$k]} ],
      per_process: [ $pp[] | . as $p | . + {interrupted_iterations: ([ $k6int[] | select(.proc == "main-p\($p.index)") | .interrupted ] | first)} ],
      k6_interrupted: $k6int,
      # Per rung, for perf-test-run.sh tail localisation: the earliest process start and the
      # client share over 5 ms from the merged steady histogram (null when not computable).
      # stalls_post_settle is informational and null unless every process reported it.
      rung_windows: [ $P[] | {offered_rps, sample_count, measured_sample_count, client_over_5ms_frac,
          start_epoch_ms: ([ .per_process[] | .rung_start_epoch_ms | select(. != null) ] | min),
          stalls_post_settle: (if (.per_process | length) > 0 and all(.per_process[]; .stalls_post_settle != null)
                               then ([ .per_process[] | .stalls_post_settle ] | add) else null end)} ],
      remote_write: {send_failures: $rwfail, slow_flushes: $slow},
      k6_gc: {note: "report-only: Go GC CPU (% of one CPU) and cycles per process over each rung steady window, from GODEBUG=gctrace=1", per_process: $k6gc},
      prometheus: {query_warnings: $promwarn},
      tail_instrumentation: $tailinst,
      gc_masked: $gcmasked,
      cpu: {sut_pct_over_ladder:$sutcpu, prometheus_pct_over_ladder:$promcpu,
            k6_cpu_us_per_request_mean: ([ $pp[] | .cpu_us_per_request | select(. != null) ] | if length == 0 then null else (add / length * 10 | round) / 10 end)},
      cross_check: $cross
    }' > "$WORK/result.json"
cp "$WORK/result.json" "$OUT_FILE"
RESULT_WRITTEN=1

echo "--- rw-multi-k6: valid=$(jq -r '.valid' "$WORK/result.json") rule=$(jq -r '.headline_rule.name // "none"' "$WORK/result.json") $(jq -r 'if .valid == true then "healthy_ceiling=\(.headline.healthy_ceiling_rps // "null")" else "healthy_ceiling_if_valid=\(.headline_if_valid.healthy_ceiling_rps // "null") (NOT a result: the run is invalid)" end' "$WORK/result.json") rig_valid_peak=$(jq -r '.rig_valid_peak_achieved_rps' "$WORK/result.json") skew_max_ms=$(jq -r '.method.observed_max_start_skew_ms' "$WORK/result.json") k6_us_per_req=$(jq -r '.cpu.k6_cpu_us_per_request_mean' "$WORK/result.json") cross_check_equivalent=$(jq -r '.cross_check.equivalent | if . == null then "n/a" else tostring end' "$WORK/result.json") cross_run_agrees=$(jq -r '.cross_check.cross_run.agrees | if . == null then "n/a" else tostring end' "$WORK/result.json")" >&2
echo "--- rw-multi-k6 GC-masked ($(jq -r 'if .gc_masked.report_only == false then "the headline rule reads it" else "report-only" end' "$WORK/result.json")): $(jq -r '.gc_masked | if .available then (.healthy_ceiling // .healthy_ceiling_if_valid) as $c | "ceiling\(if .healthy_ceiling == null then "_if_valid" else "" end)=\($c.rps // "null") at masked p99 \($c.p99_ms // "null") ms (bound \(.p99_max_ms) ms; unmasked ceiling \($c.unmasked_rps // "null")); no masked figure, so failing the bound, at \($c.no_masked_figure_at | length) rung(s)" else "unavailable: \(.reason)" end' "$WORK/result.json")" >&2
if [ "$(jq -r '.valid' "$WORK/result.json")" != true ]; then
  echo ":x: remote-write multi-k6 run INVALID:" >&2
  jq -r '.invalid_reasons[] | "    - " + .' "$WORK/result.json" >&2
  exit 2
fi
