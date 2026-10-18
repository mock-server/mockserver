#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# perf-percore.sh — req/s per core for the SERVING path. Emits a self-describing `serving_percore` JSON block (stdout, or the
# file named by $1) that perf-test-run.sh merges into result.json under
# `.serving_percore` and copies out as the `serving-percore.json` artifact.
#
# WHAT IT MEASURES (and how it differs from the injector per-core curve).
# `inject-percore.json` (perf-test-inject.sh, chart_inject_percore) is the
# per-core ceiling of the LOAD GENERATOR. This is its mirror for the SERVER: pin
# ONE MockServer SUT to C cores, drive the sweep ladder against it from a k6 on
# DISJOINT cores, and record, per C:
#   * rig_valid_peak_achieved_rps — max achieved over RIG-VALID rungs (client had CPU
#                           headroom, no dropped iterations, low errors). This is
#                           the SAME rig-valid peak definition perf-test-run.sh's
#                           saturation block uses; a rung where k6 (not the SUT)
#                           was the bottleneck is EXCLUDED, or the C-point would
#                           measure the injector, not the serving path.
#   * healthy_ceiling_rps — the highest rung where achieved stayed >= keep*offered
#                           with ZERO errors AND p50 within lat_mult x the flat-
#                           region p50, below the first rung where that fails.
#                           This is NOT re-implemented here: each C's
#                           sweep is fed as a synthetic run to the ONE authoritative
#                           implementation, lib/perf-website-figures.jq (the same
#                           definition render_perf_charts.py carries), and
#                           .headline.healthy_ceiling_rps is read back out. Do not
#                           copy its arithmetic here: a third divergent copy of the
#                           ceiling rule is exactly what reuse prevents.
#   * rps_per_core        — healthy_ceiling_rps / C. The HEALTHY ceiling, not the
#                           degraded peak, is the honest "req/s per core you can
#                           actually serve". (peak_per_core is also emitted.)
#
# THE C=16 PREREQUISITE (easy to miss). At C=16 the SUT wants 16
# cores and the k6 client needs its OWN disjoint cores, so the rung is only
# feasible on a box with >=16 + K6_MIN_CORES cores. On anything smaller the curve
# STOPS at the largest feasible C and SAYS SO: every requested-but-infeasible C is
# recorded in `.serving_percore.skipped[]` with a reason, and `max_cores_measured`
# / `curve_complete_to_16` make the limit explicit in the artifact and annotation.
# A ladder that silently ends early reads as "measured to 16" to a skimmer — so it
# must never silently end early.
#
# PINNING. `--cpuset-cpus` is the reliable
# pin: it changes what the container's JVM sees via Runtime.availableProcessors(),
# which sizes actionHandlerThreadCount()=max(5,availableProcessors()) and the pools
# derived from it. `--cpus` (CFS quota) does NOT surface in the processor count, so
# it would leave the pools sized for the whole host — not what we mean to measure.
# We PROVE the pin took: for each C a one-shot probe container on the IDENTICAL
# image and cpuset prints availableProcessors (recorded as .available_processors);
# same image runtime + same cgroup cpuset as the SUT, so it is definitionally the
# count the SUT's own JVM computed. A C whose probe != C fails the run loudly.
#
# WARM-UP IS BIAS, NOT NOISE. Before each measured sweep a short warm-up drive
# runs (never measured), so the sweep's first rung is not paying JIT/first-touch
# cost. The warm-up's own p50 and the first vs second rung p50 are recorded under
# .warmup so a reader can SEE the transient was removed rather than averaged in.
#
# EVENT-LOG RETENTION, PER RUNG. MockServer keeps full request/response bodies in
# a COUNT-bounded ring (maxLogEntries), so retained bytes ~ maxLogEntries x body
# is roughly CONSTANT with rate, but an entry's RESIDENCE time = maxLogEntries /
# achieved_rps LENGTHENS as throughput falls. At C=1 the SUT is slow, so residence
# is long — the arithmetic is done PER RUNG (.ladder[].retention_residence_s), not
# once. maxLogEntries has no metrics endpoint, so it is an ASSUMED input
# (PERF_PERCORE_MAX_LOG_ENTRIES, honestly labelled `retention.assumed_max_log_entries`).
#
# NON-GATING. Every serving_percore.* metric is notify-only (see perf-budgets.json
# and perf-test-compare.sh). This harness only measures; it never fails a build on
# a throughput number.
#
# HARDWARE MATRIX MODE: PERF_PERCORE_MODE=hw_matrix runs the same procedure
# over PERF_HW_MATRIX ("cores:memory[:control]") and emits `serving_hw_matrix`. Each point
# also sets a container memory limit (no swap, no -Xmx, as in a user's container) and
# records resolved heap/log bounds, peak memory, OOM state and any lower-bound reasons.
# By default (PERF_HW_MATRIX_CLIENT=multik6) each point is driven by
# mockserver-performance-test/scripts/rw-multi-k6-sweep.sh against that point's SUT, from one
# fixed client placement, and its rig validity and lower-bound reasons come from derive_saturation
# (lib/perf-hw-matrix-rw.jq), not the single-k6 85%-of-pin rule below.
# Each hw_matrix SUT also writes a GC log (PERF_HW_MATRIX_GC_LOG, default true) and takes
# PERF_HW_MATRIX_SUT_JAVA_OPTS, both appended to the image's own JAVA_TOOL_OPTIONS; the sampler
# records GC time, event-log drops/evictions and the SUT cgroup's memory.events and PSI, which
# each multik6 point summarises per rung steady window (.rung_jvm, <point dir>/jvm-rungs.json).
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${PERF_PERCORE_REPO_ROOT:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
FIGURES_JQ="$SCRIPT_DIR/perf-website-figures.jq"
if [ -r "$SCRIPT_DIR/perf-sweep-window.sh" ]; then
  # shellcheck source=SCRIPTDIR/perf-sweep-window.sh
  . "$SCRIPT_DIR/perf-sweep-window.sh"
else
  echo ":x: sweep latency-window check lib not found at $SCRIPT_DIR/perf-sweep-window.sh" >&2
  exit 1
fi
# shellcheck source=perf-cpu-topology.sh
. "$SCRIPT_DIR/perf-cpu-topology.sh"
# shellcheck source=perf-java-opts.sh
. "$SCRIPT_DIR/perf-java-opts.sh"
# shellcheck source=perf-hw-matrix-jvm.sh
. "$SCRIPT_DIR/perf-hw-matrix-jvm.sh"

OUT_FILE="${1:-/dev/stdout}"

MODE="${PERF_PERCORE_MODE:-percore}"
case "$MODE" in
  percore|hw_matrix) ;;
  *) echo "ERROR: PERF_PERCORE_MODE='$MODE' (expected percore or hw_matrix)" >&2; exit 2 ;;
esac

# Offline re-assembly (no Docker): PERF_HW_MATRIX_REASSEMBLE_DIR names a hardware-matrix work dir
# (PERF_HW_MATRIX_DEBUG_DIR of an earlier run). Its matrix-inputs.json restores that run's settings
# and host facts; each <point>/point-inputs.json and the point's rw/jvm files are re-assembled.
REASSEMBLE_DIR="${PERF_HW_MATRIX_REASSEMBLE_DIR:-}"
RUN_INPUTS_KEYS_RE='^PERF_(HW_MATRIX|PERCORE)(_[A-Z0-9_]+)?$'
RUN_INPUTS_EXCLUDED='["PERF_HW_MATRIX_DEBUG_DIR","PERF_HW_MATRIX_REASSEMBLE_DIR","PERF_HW_MATRIX_TEST_FAULT","PERF_HW_MATRIX_RIG_PAUSED","PERF_PERCORE_REPO_ROOT","PERF_PERCORE_MODE"]'
if [ -n "$REASSEMBLE_DIR" ]; then
  if [ "$MODE" != hw_matrix ]; then
    echo "ERROR: PERF_HW_MATRIX_REASSEMBLE_DIR needs PERF_PERCORE_MODE=hw_matrix" >&2; exit 2
  fi
  RUN_INPUTS="$REASSEMBLE_DIR/matrix-inputs.json"
  if ! jq -e '(.resolved | type) == "object"' "$RUN_INPUTS" >/dev/null 2>&1; then
    echo "ERROR: $RUN_INPUTS is missing or has no .resolved block; cannot re-assemble $REASSEMBLE_DIR" >&2; exit 2
  fi
  # The recorded settings replace the operator's: a matching variable left in this shell would
  # silently re-assemble a different matrix from the one that ran.
  for _k in $(compgen -e); do
    [[ "$_k" =~ $RUN_INPUTS_KEYS_RE ]] || continue
    jq -e --arg k "$_k" 'index($k) != null' <<<"$RUN_INPUTS_EXCLUDED" >/dev/null && continue
    echo "--- re-assembly ignores $_k from this shell (the run's own settings come from matrix-inputs.json)" >&2
    unset "$_k"
  done
  # Only the matrix's own settings are restored, never paths or test hooks. Some reach bash
  # arithmetic, which would execute a $(...) or a[...] inside a value, so those are refused.
  while IFS=$'\t' read -r _k _v; do
    [ -n "$_k" ] || continue
    if [[ "$_v" == *[\$\`\[\]]* ]]; then
      echo "ERROR: $RUN_INPUTS: refusing $_k='$_v' (contains \$, \` or brackets)" >&2; exit 2
    fi
    export "$_k=$_v"
  done < <(jq -r --arg re "$RUN_INPUTS_KEYS_RE" --argjson ex "$RUN_INPUTS_EXCLUDED" \
             '(.env // {}) | to_entries[] | select((.key | test($re)) and (.key as $k | $ex | index($k) | not))
              | [.key, (.value | tostring)] | @tsv' "$RUN_INPUTS")
  _client="$(jq -r '.env.PERF_HW_MATRIX_CLIENT // "multik6"' "$RUN_INPUTS")"
  if [ "$_client" != multik6 ]; then
    echo "ERROR: only a multik6 matrix can be re-assembled ($RUN_INPUTS has client=$_client)" >&2; exit 2
  fi
  PERF_HW_MATRIX_RIG_PAUSED="$(jq -r '.resolved.rig_paused == true' "$RUN_INPUTS")"; export PERF_HW_MATRIX_RIG_PAUSED
  _img="$(jq -r '.resolved.image // empty' "$RUN_INPUTS")"
  [ -n "$_img" ] && export PERF_PERCORE_IMAGE="$_img"
fi

# --- inputs (all overridable) --------------------------------------------------
MOCKSERVER_IMAGE="${PERF_PERCORE_IMAGE:-${MOCKSERVER_IMAGE:-mockserver/mockserver:mockserver-snapshot-graaljs}}"
K6_IMAGE="${PERF_PERCORE_K6_IMAGE:-grafana/k6:1.7.1@sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f}"
PROBE_JDK_IMAGE="${PERF_PERCORE_JDK_IMAGE:-eclipse-temurin:17-jdk}"
K6_DIR="$REPO_ROOT/mockserver-performance-test/k6"

HOST_CORES="${PERF_TEST_HOST_CORES:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 0)}"
[ -n "$REASSEMBLE_DIR" ] && HOST_CORES="$(jq -r '.resolved.host_cores // 0' "$RUN_INPUTS")"
# Which NUMA node k6 runs on when the host has two: other (default) or same (the SUT's node,
# the interference A/B; never a baseline run). See numa_placement_check.
K6_NUMA_NODE="${PERF_K6_NUMA_NODE:-other}"
[ -n "$REASSEMBLE_DIR" ] && K6_NUMA_NODE="$(jq -r '.resolved.k6_numa_node // "other"' "$RUN_INPUTS")"
case "$K6_NUMA_NODE" in other|same) ;; *) echo "ERROR: PERF_K6_NUMA_NODE='$K6_NUMA_NODE' (expected other or same)" >&2; exit 2 ;; esac
PLACEMENT_ONLY="${PERF_PERCORE_TEST_PLACEMENT_ONLY:-}"
case "$PLACEMENT_ONLY" in ""|true) ;; *) echo "ERROR: PERF_PERCORE_TEST_PLACEMENT_ONLY='$PLACEMENT_ONLY' (expected empty or true)" >&2; exit 2 ;; esac

# The core ladder. C=16 is included so the artifact records it as
# skipped-with-reason on a box that cannot host it, rather than omitting the rung.
CORE_LADDER="${PERF_PERCORE_CORES:-1,2,4,8,16}"
# k6 needs its own cores, and to measure the SERVER's ceiling the client must have
# MORE capacity than the server — otherwise the ladder measures the client, not the
# serving path (a capped client becomes the bottleneck even at C=1 and bends the
# curve). So the client gets ALL spare cores by default: one thread on
# every physical core the SUT does not use, minus a small reserve for the
# kernel/docker/sampler (see select_cpusets). K6_MIN_CORES is the floor below which a rung
# is skipped (the client would be too weak to be trusted); K6_MAX_CORES defaults to
# the host count (no artificial cap).
K6_MIN_CORES="${PERF_PERCORE_K6_MIN_CORES:-2}"
K6_MAX_CORES="${PERF_PERCORE_K6_MAX_CORES:-$HOST_CORES}"
# Cores held back from BOTH server and client for the kernel, dockerd and the CPU
# sampler, so neither the SUT nor k6 contends with the box's own overhead.
K6_RESERVE="${PERF_PERCORE_RESERVE_CORES:-1}"

# Sweep ladder + timing per C. Kept overridable.
#
# The default is DELIBERATELY NOT a doubling ladder. healthy_ceiling_rps is defined as the highest
# RUNG whose achieved rate held at or above keep x offered (see the derivation below), so the
# ceiling can only ever take a rung's value and its resolution IS the rung spacing. On the old
# doubling ladder (250,500,1000,2000,4000,8000,16000,32000) that meant a factor-of-two uncertainty:
# a server whose true ceiling is 7,900 rps reported 4,000. The extra rungs sit in the 2k-8k region
# where the ceilings cluster; each costs one (step + gap). History: docs/code/performance-measurement.md.
SWEEP_RATES="${PERF_PERCORE_SWEEP_RATES:-500,1000,1500,2000,3000,4000,5000,6000,7000,8000,10000,12000,16000,32000}"
# The single-client matrix ladder spaces rungs 6-17% apart from 8,000 req/s up and 12-100%
# below that, so a ceiling is only resolved to one rung (the memory verdict allows for it).
# Each point offers only rates up to MAX_RPS_PER_CORE x cores (0 = no cap); a ceiling
# on a point's top rung is flagged ladder_top_reached (a lower bound).
if [ "$MODE" = hw_matrix ] && [ "${PERF_HW_MATRIX_CLIENT:-multik6}" = single ]; then
  SWEEP_RATES="${PERF_HW_MATRIX_SWEEP_RATES:-1000,2000,3000,4000,5000,6000,7000,8000,9000,10000,11000,12000,14000,16000,18000,20000,22000,24000,26000,28000,32000,36000,40000,44000,48000,52000,56000,60000,64000,68000,72000}"
fi
HW_MAX_RPS_PER_CORE="${PERF_HW_MATRIX_MAX_RPS_PER_CORE:-20000}"
HW_MATRIX_SPEC="${PERF_HW_MATRIX:-1:512m,2:1g,2:2g:control,3:1536m,4:2g,6:2g}"
# multik6 (default): each point is driven by scripts/rw-multi-k6-sweep.sh (N k6 processes merged
# in Prometheus) from one fixed placement; single: the one-k6 sweep percore mode uses.
HW_CLIENT="${PERF_HW_MATRIX_CLIENT:-multik6}"
case "$HW_CLIENT" in
  multik6|single) ;;
  *) echo "ERROR: PERF_HW_MATRIX_CLIENT='$HW_CLIENT' (expected multik6 or single)" >&2; exit 2 ;;
esac
[ "$MODE" = hw_matrix ] || HW_CLIENT=single
HW_SUT_JAVA_OPTS="${PERF_HW_MATRIX_SUT_JAVA_OPTS:-}"
HW_GC_LOG="${PERF_HW_MATRIX_GC_LOG:-true}"
case "$HW_GC_LOG" in
  true|false) ;;
  *) echo "ERROR: PERF_HW_MATRIX_GC_LOG='$HW_GC_LOG' (expected true or false)" >&2; exit 2 ;;
esac
[ "$MODE" = hw_matrix ] || { HW_SUT_JAVA_OPTS=""; HW_GC_LOG=false; }
# %p: a file per JVM, so another JVM in the container (an older image's Java HEALTHCHECK, which
# also reads JAVA_TOOL_OPTIONS) cannot rotate the server's log away at its start.
HW_GC_LOG_OPT='-Xlog:gc*:file=/jvm-diag/gc-%p.log:time,uptime,level,tags'
HW_SUT_EXTRA_OPTS="${HW_SUT_JAVA_OPTS}"
[ "$HW_GC_LOG" = true ] && HW_SUT_EXTRA_OPTS="${HW_SUT_EXTRA_OPTS:+$HW_SUT_EXTRA_OPTS }$HW_GC_LOG_OPT"
if [ "$HW_CLIENT" = multik6 ]; then
  HW_WARMUP_RPS_PER_CORE="${PERF_HW_MATRIX_WARMUP_RPS_PER_CORE:-4000}"
else
  HW_WARMUP_RPS_PER_CORE="${PERF_HW_MATRIX_WARMUP_RPS_PER_CORE:-1000}"
fi
# multik6 ladder per point: HW_LADDER_RUNGS rates spaced geometrically from LO to HI x cores x
# RPS_PER_CORE_REF, rounded to 100 (17 rungs over 0.5-1.6x are ~7.5% apart), below them the
# ANCHORS fractions (at least 1,000 req/s): the healthy-ceiling rule takes its flat-region p50
# from the lowest four rungs, so the anchors keep that baseline near-unloaded at every size.
# An explicit PERF_HW_MATRIX_SWEEP_RATES is offered to every point unchanged instead.
HW_RPS_PER_CORE_REF="${PERF_HW_MATRIX_RPS_PER_CORE_REF:-16000}"
HW_LADDER_LO="${PERF_HW_MATRIX_LADDER_LO:-0.5}"
HW_LADDER_HI="${PERF_HW_MATRIX_LADDER_HI:-1.6}"
HW_LADDER_RUNGS="${PERF_HW_MATRIX_LADDER_RUNGS:-17}"
HW_LADDER_ANCHORS="${PERF_HW_MATRIX_LADDER_ANCHORS-0.1,0.2,0.3}"
HW_XCHECK_MAX_RPS="${PERF_HW_MATRIX_XCHECK_MAX_RPS:-40000}"
HW_P99_MAX_MS="${PERF_HW_MATRIX_P99_MAX_MS:-10}"
if [ "$HW_CLIENT" = multik6 ]; then
  for _v in HW_RPS_PER_CORE_REF HW_LADDER_RUNGS HW_XCHECK_MAX_RPS; do
    [[ "${!_v}" =~ ^[1-9][0-9]*$ ]] || { echo "ERROR: $_v='${!_v}' must be a positive integer" >&2; exit 2; }
  done
  [ "$HW_LADDER_RUNGS" -ge 2 ] || { echo "ERROR: PERF_HW_MATRIX_LADDER_RUNGS must be at least 2" >&2; exit 2; }
  if ! awk -v lo="$HW_LADDER_LO" -v hi="$HW_LADDER_HI" 'BEGIN{exit !(lo+0 > 0 && hi+0 > lo+0)}'; then
    echo "ERROR: PERF_HW_MATRIX_LADDER_LO/HI ($HW_LADDER_LO/$HW_LADDER_HI) must satisfy 0 < LO < HI" >&2; exit 2
  fi
  if ! [[ "$HW_LADDER_ANCHORS" =~ ^([0-9.]+(,[0-9.]+)*)?$ ]] \
     || ! awk -v a="$HW_LADDER_ANCHORS" -v lo="$HW_LADDER_LO" 'BEGIN{n=split(a, f, ","); for(i=1;i<=n;i++) if(!(f[i]+0 > 0 && f[i]+0 < lo+0)) exit 1}'; then
    echo "ERROR: PERF_HW_MATRIX_LADDER_ANCHORS='$HW_LADDER_ANCHORS' must be comma-separated fractions above 0 and below LO ($HW_LADDER_LO), or empty" >&2; exit 2
  fi
fi
# The rates one point is offered (multik6), comma-separated and strictly increasing.
hw_point_rates() { # cores
  if [ -n "${PERF_HW_MATRIX_SWEEP_RATES:-}" ]; then echo "$PERF_HW_MATRIX_SWEEP_RATES"; return; fi
  awk -v c="$1" -v ref="$HW_RPS_PER_CORE_REF" -v lo="$HW_LADDER_LO" -v hi="$HW_LADDER_HI" -v n="$HW_LADDER_RUNGS" \
      -v anchors="$HW_LADDER_ANCHORS" 'BEGIN{
    base = c * ref * lo; r = (hi / lo) ^ (1 / (n - 1)); out = ""; prev = 0
    na = split(anchors, a, ",")
    for (i = 1; i <= na; i++) { v = int(c * ref * a[i] / 100 + 0.5) * 100; if (v < 1000) v = 1000
      if (v <= prev || v >= base) continue; out = out (out == "" ? "" : ",") v; prev = v }
    for (i = 0; i < n; i++) { v = int(base * r ^ i / 100 + 0.5) * 100
      if (v <= prev) continue; out = out (out == "" ? "" : ",") v; prev = v }
    print out }'
}
SWEEP_STEP="${PERF_PERCORE_SWEEP_STEP:-12s}"
SWEEP_GAP="${PERF_PERCORE_SWEEP_GAP:-4s}"
if [ "$HW_CLIENT" = multik6 ]; then
  SWEEP_STEP="${PERF_HW_MATRIX_STEP:-15s}"
  SWEEP_GAP="${PERF_HW_MATRIX_GAP:-5s}"
fi
# Empty by default: sweep.js then sizes a fixed pool per rung. If set, both must be
# set and equal (sweep.js refuses a pre/max VU ramp — the Finding-3 invariant).
SWEEP_PRE_VUS="${PERF_PERCORE_SWEEP_PRE_VUS:-}"
SWEEP_MAX_VUS="${PERF_PERCORE_SWEEP_MAX_VUS:-}"
WARMUP_RATE="${PERF_PERCORE_WARMUP_RATE:-500}"
WARMUP_DURATION="${PERF_PERCORE_WARMUP_DURATION:-8s}"

SERVER_MEMORY="${PERF_PERCORE_MEMORY:-1g}"
# Event-log retention parameters for the per-rung residence arithmetic. The default mirrors
# the product's min(heapKB/8, 250000) on the image's MaxRAMPercentage heap of SERVER_MEMORY,
# read from the image's ENTRYPOINT; 45 (the GraalJS image this rig runs) if it cannot be read.
percore_image_heap_pct() {
  local pct
  pct="$(docker image inspect -f '{{json .Config.Entrypoint}}' "$MOCKSERVER_IMAGE" 2>/dev/null \
    | grep -oE 'MaxRAMPercentage=[0-9]+' | head -1 | cut -d= -f2 || true)"
  echo "${pct:-45}"
}
percore_default_max_log_entries() {
  local mem="$1" bytes
  case "$mem" in
    *[gG]) bytes=$(( ${mem%[gG]} * 1024 * 1024 * 1024 )) ;;
    *[mM]) bytes=$(( ${mem%[mM]} * 1024 * 1024 )) ;;
    *) echo 250000; return ;;
  esac
  local entries=$(( (bytes * $(percore_image_heap_pct) / 100 / 1024 - 20480) / 8 ))
  [ "$entries" -gt 250000 ] && entries=250000
  [ "$entries" -lt 1000 ] && entries=1000
  echo "$entries"
}
# "512m" / "2g" -> bytes; empty on anything else (validated by the matrix parser).
mem_to_bytes() {
  case "$1" in
    *[gG]) echo $(( ${1%[gG]} * 1024 * 1024 * 1024 )) ;;
    *[mM]) echo $(( ${1%[mM]} * 1024 * 1024 )) ;;
    *) echo "" ;;
  esac
}

# The point list: parallel arrays of cores / memory limit / control flag. percore
# mode is the core ladder at one memory; hw_matrix mode is PERF_HW_MATRIX. A malformed
# entry fails the step loudly (exit 2) rather than measuring a matrix nobody asked for.
P_CORES=(); P_MEM=(); P_CONTROL=()
if [ "$MODE" = hw_matrix ]; then
  IFS=',' read -ra _hw_entries <<< "$HW_MATRIX_SPEC"
  for _e in "${_hw_entries[@]}"; do
    IFS=':' read -r _c _m _f _extra <<< "$_e"
    if ! [[ "$_c" =~ ^[1-9][0-9]*$ ]] || ! [[ "$_m" =~ ^[1-9][0-9]*[mMgG]$ ]] \
       || { [ -n "${_f:-}" ] && [ "$_f" != control ]; } || [ -n "${_extra:-}" ]; then
      echo "ERROR: PERF_HW_MATRIX entry '$_e' is not cores:memory[:control] (e.g. 2:1g or 2:2g:control)" >&2
      exit 2
    fi
    _m="$(printf '%s' "$_m" | tr 'MG' 'mg')"
    for _i in "${!P_CORES[@]}"; do
      if [ "${P_CORES[$_i]}" = "$_c" ] && [ "$(mem_to_bytes "${P_MEM[$_i]}")" = "$(mem_to_bytes "$_m")" ]; then
        echo "ERROR: PERF_HW_MATRIX lists ${_c}:${_m} twice — each point needs a distinct cores:memory pair" >&2
        exit 2
      fi
    done
    P_CORES+=("$_c"); P_MEM+=("$_m")
    if [ "${_f:-}" = control ]; then P_CONTROL+=(true); else P_CONTROL+=(false); fi
  done
else
  IFS=',' read -ra _pc_cores <<< "$CORE_LADDER"
  for _c in "${_pc_cores[@]}"; do P_CORES+=("$_c"); P_MEM+=("$SERVER_MEMORY"); P_CONTROL+=(false); done
fi

SWEEP_SETTLE_S="${PERF_PERCORE_SETTLE_S:-3}"
SWEEP_ERR_EPS="${PERF_PERCORE_ERROR_EPS:-0.01}"
SWEEP_SAMPLE_INTERVAL="${PERF_PERCORE_SAMPLE_INTERVAL:-2}"
# The MIN_TAIL_SAMPLES floor the k6 harnesses use (regression.js et al.).
MIN_TAIL_SAMPLES="${PERF_PERCORE_MIN_TAIL_SAMPLES:-30}"

RUN_ID="${BUILDKITE_BUILD_ID:-local}-$$-percore"
NETWORK="mockserver-percore-${RUN_ID}"
SERVER="mockserver-percore-sut-${RUN_ID}"
K6_NAME="mockserver-percore-k6-${RUN_ID}"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/perf-percore.XXXXXX")"
chmod 0777 "$WORK"

cleanup() {
  docker rm -f "$SERVER" "$K6_NAME" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  rm -rf "$WORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# k6 duration string -> integer seconds.
to_secs() {
  awk -v s="$1" 'BEGIN{ t=0; n="";
    for(i=1;i<=length(s);i++){c=substr(s,i,1);
      if(c ~ /[0-9]/){n=n c}
      else{v=n+0; n="";
        if(c=="s")t+=v; else if(c=="m")t+=v*60; else if(c=="h")t+=v*3600}}
    printf "%d", t}'
}

STEP_S="$(to_secs "$SWEEP_STEP")"
GAP_S="$(to_secs "$SWEEP_GAP")"

# --- multik6 placement: ONE layout for every point and rung, so client capacity is constant.
# On the c5.12xlarge (48 logical cpus, siblings N / N+24): the SUT on physical cores 0..C-1
# (siblings idle), four k6 processes on four physical cores each (both hyperthreads) across
# 7-22, Prometheus on 23; cores C..6 stay idle (the paused main SUT and upstream sit there).
# A host with two or more NUMA nodes takes the multi-k6 arm's layout from the node map
# (numa_split_layout: Prometheus on node 0 after the SUT and upstream slots, k6 on node 1).
# Any other host gets a logical-id layout above the matrix's largest point; it is not
# hyperthread-aware (the physical-disjointness proof below still refuses an overlapping point), so
# set PERF_HW_MATRIX_K6_CPUSETS / PERF_HW_MATRIX_PROM_CPUS for a sibling-aware layout there.
numa_map_prime
HW_MAX_POINT_CORES=0
for _c in "${P_CORES[@]}"; do [ "$_c" -gt "$HW_MAX_POINT_CORES" ] && HW_MAX_POINT_CORES="$_c"; done
HW_K6_SETS=(); HW_PROM_CPUS=""
HW_LAYOUT=""
if [ "$HW_CLIENT" = multik6 ]; then
  _slots="$HW_MAX_POINT_CORES"; [ "$_slots" -lt 6 ] && _slots=6
  _lrc=0
  _numa_layout="$(numa_split_layout "$_slots" "${PERF_HW_MATRIX_PROCS:-4}" "$K6_NUMA_NODE" "${PERF_HW_MATRIX_K6_CORES_PER_PROC:-}")" || _lrc=$?
  [ -n "$REASSEMBLE_DIR" ] && _lrc=1 # the host's layout is restored from matrix-inputs.json below
  if [ "$_lrc" -eq 0 ]; then
    _def_k6="$(layout_value k6 "$_numa_layout")"; _def_prom="$(layout_value prometheus "$_numa_layout")"
    HW_LAYOUT="$([ "$K6_NUMA_NODE" = same ] && echo numa_same_node || echo numa_split)"
  elif [ "$_lrc" -ne 1 ] && { [ -z "${PERF_HW_MATRIX_K6_CPUSETS:-}" ] || [ -z "${PERF_HW_MATRIX_PROM_CPUS:-}" ]; }; then
    echo "ERROR: the NUMA layout does not fit this host (above); set PERF_HW_MATRIX_K6_CPUSETS and PERF_HW_MATRIX_PROM_CPUS" >&2
    exit 2
  elif [ "$HOST_CORES" -eq 48 ]; then
    _def_k6="7-10,31-34;11-14,35-38;15-18,39-42;19-22,43-46"; _def_prom="23,47"
  else
    _def_prom="$(( HOST_CORES - 1 ))"; _first=$(( HW_MAX_POINT_CORES + 1 )); _avail=$(( HOST_CORES - 1 - _first ))
    _n=4; [ "$_avail" -lt "$_n" ] && _n="$_avail"; _def_k6=""
    if [ "$_n" -ge 1 ]; then
      _w=$(( _avail / _n ))
      for ((i=0; i<_n; i++)); do
        _s=$(( _first + i * _w )); _e=$(( _s + _w - 1 ))
        _def_k6="${_def_k6:+$_def_k6;}$([ "$_w" -eq 1 ] && echo "$_s" || echo "$_s-$_e")"
      done
    fi
  fi
  if [ -n "$REASSEMBLE_DIR" ] && jq -e '.resolved.hw_k6_cpusets | type == "string"' "$RUN_INPUTS" >/dev/null 2>&1; then
    _def_k6="$(jq -r '.resolved.hw_k6_cpusets' "$RUN_INPUTS")"; _def_prom="$(jq -r '.resolved.hw_prom_cpus' "$RUN_INPUTS")"
  fi
  IFS=';' read -ra HW_K6_SETS <<< "${PERF_HW_MATRIX_K6_CPUSETS:-$_def_k6}"
  HW_PROM_CPUS="${PERF_HW_MATRIX_PROM_CPUS:-$_def_prom}"
  if [ "${#HW_K6_SETS[@]}" -lt 1 ] || [ -z "$HW_PROM_CPUS" ]; then
    echo "ERROR: no room for the multik6 client on ${HOST_CORES} cpus (largest point ${HW_MAX_POINT_CORES} cores); set PERF_HW_MATRIX_K6_CPUSETS and PERF_HW_MATRIX_PROM_CPUS" >&2
    exit 2
  fi
  [ -n "${PERF_HW_MATRIX_K6_CPUSETS:-}" ] && HW_LAYOUT=explicit
fi
if [ -z "$HW_LAYOUT" ]; then
  # The single-k6 client's NUMA layout is select_cpusets' node split below.
  case "$(numa_node_count)" in
    0) HW_LAYOUT=topology_unknown ;;
    1) HW_LAYOUT=single_node ;;
    *) HW_LAYOUT="$([ "$K6_NUMA_NODE" = same ] && echo numa_same_node || echo numa_split)" ;;
  esac
  [ -n "$REASSEMBLE_DIR" ] && HW_LAYOUT="$(jq -r '.resolved.layout // "unrecorded"' "$RUN_INPUTS")"
fi
HW_K6_SPEC="$(IFS=';'; echo "${HW_K6_SETS[*]:-}")"

if [ "$MODE" = hw_matrix ]; then
  echo "--- item 27 hardware matrix: host_cores=$HOST_CORES matrix=$HW_MATRIX_SPEC client=$HW_CLIENT$([ "$HW_CLIENT" = multik6 ] && echo " k6=${HW_K6_SPEC} prometheus=${HW_PROM_CPUS}" || echo " k6=[${K6_MIN_CORES}..${K6_MAX_CORES}]") image=$MOCKSERVER_IMAGE" >&2
else
  echo "--- item 18 serving per-core: host_cores=$HOST_CORES ladder=$CORE_LADDER k6=[${K6_MIN_CORES}..${K6_MAX_CORES}] image=$MOCKSERVER_IMAGE" >&2
fi

# --- compile the availableProcessors probe ONCE (JDK image; the SUT image is a
# JRE and cannot run the single-file source launcher, so we ship a .class and run
# it with `--entrypoint java -cp` in the SUT image). ---------------------------
if [ -z "$REASSEMBLE_DIR" ]; then
cat > "$WORK/AvailableProcessors.java" <<'EOF'
public class AvailableProcessors {
  public static void main(String[] a) {
    System.out.println("availableProcessors=" + Runtime.getRuntime().availableProcessors());
  }
}
EOF
if ! docker run --rm -v "$WORK:/w" -w /w "$PROBE_JDK_IMAGE" javac AvailableProcessors.java >/dev/null 2>&1; then
  echo "ERROR: could not compile the availableProcessors probe with $PROBE_JDK_IMAGE" >&2
  echo '{"attempted":true,"error":"probe_compile_failed","points":[],"skipped":[]}' > "$OUT_FILE"
  exit 0
fi
fi

# The SUT's JAVA_TOOL_OPTIONS: the image's own default (kept, so its GC is not dropped) plus
# the hw_matrix extras. Unreadable image env would silently drop that default, so stop.
SUT_IMAGE_JTO=""; SUT_JTO=""
if [ -n "$REASSEMBLE_DIR" ]; then
  SUT_IMAGE_JTO="$(jq -r '.resolved.image_java_tool_options // ""' "$RUN_INPUTS")"
  SUT_JTO="$(jq -r '.resolved.java_tool_options // ""' "$RUN_INPUTS")"
elif [ -n "$HW_SUT_EXTRA_OPTS" ]; then
  docker image inspect "$MOCKSERVER_IMAGE" >/dev/null 2>&1 || docker pull -q "$MOCKSERVER_IMAGE" >/dev/null 2>&1 || true
  if ! docker image inspect "$MOCKSERVER_IMAGE" >/dev/null 2>&1; then
    echo "ERROR: cannot inspect $MOCKSERVER_IMAGE for its JAVA_TOOL_OPTIONS default" >&2
    echo '{"attempted":true,"error":"sut_image_env_unreadable","points":[],"skipped":[]}' > "$OUT_FILE"
    exit 0
  fi
  SUT_IMAGE_JTO="$(image_java_tool_options "$MOCKSERVER_IMAGE")"
  SUT_JTO="$(compose_java_tool_options "$SUT_IMAGE_JTO" "$HW_SUT_EXTRA_OPTS")"
  echo "--- SUT JAVA_TOOL_OPTIONS: $SUT_JTO (image default: ${SUT_IMAGE_JTO:-none})" >&2
fi

[ -n "$REASSEMBLE_DIR" ] || docker network create "$NETWORK" >/dev/null

# Report a JVM's availableProcessors for a cpuset, using the SUT image's own JVM.
probe_processors() { # cpuset
  local mems; mems="$(numa_mems_flag "$1")"
  docker run --rm --cpuset-cpus="$1" ${mems:+"$mems"} --entrypoint java \
    -v "$WORK:/probe:ro" "$MOCKSERVER_IMAGE" -cp /probe AvailableProcessors 2>/dev/null \
    | sed -n 's/^availableProcessors=//p' | head -1
}

# Where sysfs topology is readable (the Linux perf host), the SUT takes C logical cpus
# on C DISTINCT physical cores and k6 takes one thread on each remaining physical core
# (minus the reserve), so the client never runs on the SUT's hyperthread siblings; on
# the c5.12xlarge (siblings N / N+24) that is SUT 0..C-1 and k6 C..22. Where it is not
# readable (a macOS Docker Desktop run) it falls back to logical ids, labelled unverified.
TOPO_KNOWN=false
HOST_PHYS_CORES=null
if [ -n "$REASSEMBLE_DIR" ]; then
  TOPO_KNOWN="$(jq -r '.resolved.topology_known == true' "$RUN_INPUTS")"
  HOST_PHYS_CORES="$(jq -r '.resolved.host_physical_cores // null' "$RUN_INPUTS")"
elif phys_core_key 0 >/dev/null 2>&1; then
  TOPO_KNOWN=true
  HOST_PHYS_CORES="$(phys_core_count "0-$((HOST_CORES-1))")"
fi
# k6's physical core count, for the matrix block (host topology, so restored when re-assembling).
if [ -n "$REASSEMBLE_DIR" ]; then
  K6_PHYS_CORES="$(jq -r '.resolved.k6_physical_cores // null' "$RUN_INPUTS")"
else
  K6_PHYS_CORES="$(phys_core_count "${HW_K6_SPEC//;/,}")"
fi
# The hardware matrix's settings and host facts, so its work dir can be re-assembled offline.
if [ "$MODE" = hw_matrix ] && [ -z "$REASSEMBLE_DIR" ] && [ -n "${PERF_HW_MATRIX_DEBUG_DIR:-}" ]; then
  mkdir -p "$PERF_HW_MATRIX_DEBUG_DIR"
  jq -n --arg re "$RUN_INPUTS_KEYS_RE" --argjson ex "$RUN_INPUTS_EXCLUDED" --argjson hc "$HOST_CORES" \
    --argjson hpc "$HOST_PHYS_CORES" --argjson topo "$TOPO_KNOWN" --argjson k6p "$K6_PHYS_CORES" \
    --arg img "$MOCKSERVER_IMAGE" --arg ijto "$SUT_IMAGE_JTO" --arg jto "$SUT_JTO" \
    --arg k6sets "$HW_K6_SPEC" --arg promcpus "$HW_PROM_CPUS" --arg layout "$HW_LAYOUT" --arg k6node "$K6_NUMA_NODE" \
    --argjson paused "$([ "${PERF_HW_MATRIX_RIG_PAUSED:-false}" = true ] && echo true || echo false)" '
    {source:"live",
     env:($ENV | with_entries(select((.key | test($re)) and (.key as $k | $ex | index($k) | not)))),
     resolved:{host_cores:$hc, host_physical_cores:$hpc, topology_known:$topo, k6_physical_cores:$k6p,
               image:$img, image_java_tool_options:$ijto, java_tool_options:$jto, rig_paused:$paused,
               hw_k6_cpusets:$k6sets, hw_prom_cpus:$promcpus, layout:$layout, k6_numa_node:$k6node}}' \
    > "$PERF_HW_MATRIX_DEBUG_DIR/matrix-inputs.json" \
    || echo "WARNING: could not write $PERF_HW_MATRIX_DEBUG_DIR/matrix-inputs.json — this matrix cannot be re-assembled offline" >&2
fi
# echoes "<server cpuset> <k6 cpuset> <k6 core count>"; k6 cpuset "-" when none fit.
# On a host with two or more NUMA nodes the SUT takes its cores from the first node and k6 from
# the second (or, PERF_K6_NUMA_NODE=same, from what the first has left); on one node both
# choose from every cpu, as before.
SUT_CANDIDATES=""; K6_CANDIDATES=""; CPU_KEYS=""
if [ "$TOPO_KNOWN" = true ] && [ -z "$REASSEMBLE_DIR" ]; then
  # "<cpu> <physical core key>" for every readable cpu, read once (select_cpusets runs per point).
  for ((_cpu=0; _cpu<HOST_CORES; _cpu++)); do
    _key="$(phys_core_key "$_cpu")" && CPU_KEYS="$CPU_KEYS$_cpu $_key"$'\n'
  done
  SUT_CANDIDATES="$(seq 0 $(( HOST_CORES - 1 )) | paste -sd' ' -)"; K6_CANDIDATES="$SUT_CANDIDATES"
  if [ "$(numa_node_count)" -ge 2 ]; then
    _nodes="$(numa_nodes)"; _n0="$(awk '{print $1}' <<<"$_nodes")"
    SUT_CANDIDATES="$(numa_node_cpus "$_n0")"
    if [ "$K6_NUMA_NODE" = same ]; then K6_CANDIDATES="$SUT_CANDIDATES"
    elif _n1="$(numa_other_socket_node "$_n0")"; then K6_CANDIDATES="$(numa_node_cpus "$_n1")"
    else K6_CANDIDATES=""; fi # no other socket: no k6 cores, so every point is infeasible
  fi
fi
select_cpusets() {
  local c="$1" s="" k=""
  if [ "$TOPO_KNOWN" != true ]; then
    local w=$(( HOST_CORES - c - K6_RESERVE ))
    [ "$w" -gt "$K6_MAX_CORES" ] && w="$K6_MAX_CORES"
    if [ "$c" -eq 1 ]; then s="0"; else s="0-$((c-1))"; fi
    if [ "$w" -lt 1 ]; then echo "$s - $w"; return; fi
    if [ "$w" -eq 1 ]; then k="$c"; else k="$c-$((c+w-1))"; fi
    echo "$s $k $w"; return
  fi
  # SUT: the first C candidates on distinct physical cores. k6: one thread on every other physical
  # core among its candidates, minus the reserve, capped at K6_MAX_CORES.
  awk -v c="$c" -v sc="$SUT_CANDIDATES" -v kc="$K6_CANDIDATES" -v reserve="$K6_RESERVE" -v max="$K6_MAX_CORES" '
    NF == 2 { key[$1] = $2 }
    END {
      ns = split(sc, S, " "); sn = 0; s = ""
      for (i = 1; i <= ns && sn < c; i++) { x = S[i]; if (!(x in key) || (key[x] in sk)) continue
        sk[key[x]] = 1; s = s (s == "" ? "" : ",") x; sn++ }
      nk = split(kc, K, " "); kn = 0
      for (i = 1; i <= nk; i++) { x = K[i]; if (!(x in key) || (key[x] in sk) || (key[x] in kseen)) continue
        kseen[key[x]] = 1; kl[++kn] = x }
      kn -= reserve; if (kn > max + 0) kn = max + 0; if (sn < c) kn = 0
      k = ""; for (i = 1; i <= kn; i++) k = k (k == "" ? "" : ",") kl[i]
      printf "%s %s %d\n", (s == "" ? "-" : s), (k == "" ? "-" : k), kn }' <<<"$CPU_KEYS"
  return 0
}

# The SUT container's end state: still running, OOM-killed, exit code, restarts, when it
# stopped (epoch, null while running) and how many OutOfMemoryError lines it logged.
sut_state_json() {
  local st fa fa_epoch oome name="$SERVER"
  [ "${PERF_HW_MATRIX_TEST_FAULT:-}" = sut_inspect ] && name="${SERVER}-missing" # degrade test only
  # A failed inspect prints an empty line, which would otherwise parse as running=false (a dead SUT).
  if ! st="$(docker inspect --format '{{.State.Running}};{{.State.OOMKilled}};{{.State.ExitCode}};{{.RestartCount}};{{.State.FinishedAt}}' "$name" 2>/dev/null)" \
     || [ -z "${st//[[:space:]]/}" ]; then
    st='unknown;unknown;;;'
  fi
  fa="$(cut -d';' -f5 <<<"$st")"; fa_epoch=""
  case "$fa" in ""|0001-*) ;; *)
    fa_epoch="$(date -u -d "$fa" +%s 2>/dev/null || date -u -j -f '%Y-%m-%dT%H:%M:%S' "${fa%%.*}" +%s 2>/dev/null || echo '')" ;;
  esac
  oome="$(docker logs "$SERVER" 2>&1 | grep -c 'OutOfMemoryError' || true)"
  jq -nc --arg run "$(cut -d';' -f1 <<<"$st")" --arg oom "$(cut -d';' -f2 <<<"$st")" \
    --arg ec "$(cut -d';' -f3 <<<"$st")" --arg rc "$(cut -d';' -f4 <<<"$st")" \
    --arg fae "$fa_epoch" --arg oome "${oome:-0}" '
    {running:(if $run == "unknown" then null else ($run == "true") end),
     oom_killed:(if $oom == "unknown" then null else ($oom == "true") end),
     exit_code:($ec | tonumber? // null), restart_count:($rc | tonumber? // null),
     finished_at_epoch:($fae | tonumber? // null), java_oom_errors:($oome | tonumber? // 0)}'
}

# Heap ceiling and event-log bounds as the running SUT reports them (null when a gauge
# is absent, e.g. an image that predates it).
resolved_bounds_json() {
  local m
  m="$(curl -s --max-time 5 "$METRICS_URL" 2>/dev/null || true)"
  awk '
    /^jvm_memory_max_bytes\{area="heap"\} /                 { h=$2 }
    /^mock_server_event_log_max_retained_entries /           { e=$2 }
    /^mock_server_event_log_max_retained_bytes /             { b=$2 }
    /^mock_server_event_log_max_in_flight_bytes /            { f=$2 }
    /^mock_server_event_log_ring_capacity /                  { r=$2 }
    function num(v) { return (v == "" || v + 0 <= 0) ? "null" : sprintf("%.0f", v) }
    END { printf "{\"max_heap_bytes\":%s,\"max_log_entries\":%s,\"max_event_log_bytes\":%s,\"max_in_flight_bytes\":%s,\"ring_capacity\":%s,\"source\":\"sut-metrics\"}\n",
            num(h), num(e), num(b), num(f), num(r) }' <<<"$m"
}

# --- sample BOTH the k6 CLIENT and the SUT CPU during the measured sweep -----
# The SUT CPU is the decisive datum for attributing a falling peak: if the SUT
# sat WELL BELOW its C-core pin (C*100%) while achieved throughput fell, the
# server had spare CPU and the limit is the load path / virtualization, NOT the
# server; if it sat AT ~C*100% the server itself was the ceiling. Both are read
# from ONE `docker stats --no-stream` call (two container names) so the sampler
# adds one probe per interval, not two, and the k6 and SUT samples share a
# timestamp. A name not yet running just yields no line (handled by the awk).
# The SUT's memory use and retained log entries ride the same interval, so a point
# records how close it ran to its memory limit and whether the log filled.
start_point_sampler() {
  # docker stats fails outright on a missing name, so the k6 container is named only when one runs.
  local names="$SERVER"
  [ "$HW_CLIENT" = single ] && names="$K6_NAME $SERVER"
  CPU_LOG="$WORK/cpu-${PKEY}.csv"
  # hw_matrix keeps it in the point dir, so the work-files archive carries the memory peaks.
  if [ "$MODE" = hw_matrix ]; then mkdir -p "$POINT_DIR"; CPU_LOG="$POINT_DIR/sut-samples.csv"; fi
  echo "ts,k6_cpu_pct,sut_cpu_pct,sut_mem_bytes,retained_entries,retained_bytes" > "$CPU_LOG"
  JVM_LOG=""
  if [ "$MODE" = hw_matrix ]; then
    JVM_LOG="$POINT_DIR/jvm-samples.csv"; mkdir -p "$POINT_DIR"
    echo "ts,$JVM_COLS" > "$JVM_LOG"
  fi
  ( while true; do
      # Stamped on return (performance-measurement.md, sweep.js).
      # shellcheck disable=SC2086  # $names is one or two container names
      stats="$(docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' $names 2>/dev/null || echo '')"
      ts="$(date -u +%s)"
      k6c="$(printf '%s\n' "$stats" | awk -v n="$K6_NAME" '$1==n{gsub(/%/,"",$2); print $2}')"
      sutc="$(printf '%s\n' "$stats" | awk -v n="$SERVER" '$1==n{gsub(/%/,"",$2); print $2}')"
      sutm="$(printf '%s\n' "$stats" | awk -v n="$SERVER" '
        function tob(s,  n, u) { n = s; sub(/[A-Za-z]+$/, "", n); u = s; sub(/^[0-9.]+/, "", u)
          if (u == "KiB" || u == "kB" || u == "KB") return n * 1024
          if (u == "MiB" || u == "MB") return n * 1048576
          if (u == "GiB" || u == "GB") return n * 1073741824
          return n }
        $1==n { printf "%.0f", tob($3) }')"
      mbody="$(curl -s --max-time 1 "$METRICS_URL" 2>/dev/null || true)"
      retl="$(awk '
        /^mock_server_event_log_retained_entries / { e = sprintf("%.0f", $2) }
        /^mock_server_event_log_retained_bytes /   { b = sprintf("%.0f", $2) }
        END { printf "%s,%s", e, b }' <<<"$mbody" || true)"
      printf '%s,%s,%s,%s,%s\n' "$ts" "${k6c:-}" "${sutc:-}" "${sutm:-}" "${retl:-,}" >> "$CPU_LOG"
      [ -n "$JVM_LOG" ] && printf '%s,%s,%s\n' "$ts" "$(jvm_metric_cols <<<"$mbody")" "$(cgroup_cols "$SUT_CGROUP_DIR")" >> "$JVM_LOG"
      sleep "$SWEEP_SAMPLE_INTERVAL"
    done ) & SAMPLER_PID=$!
}

cpusets_logically_overlap() { # spec_a spec_b
  local a b
  for a in $(expand_cpuset "$1"); do
    for b in $(expand_cpuset "$2"); do [ "$a" = "$b" ] && return 0; done
  done
  return 1
}

# Record the current point as a harness failure (never a point, never silently dropped).
skip_point_failure() { # reason
  echo "ERROR: $LBL: $1" >&2
  SKIPPED+=("$(jq -c --arg r "$1" --argjson st "${STATE_JSON:-null}" '. + {reason:$r, type:"failure", sut_state:$st}' <<<"$POINT_META")")
}
# PERF_HW_MATRIX_TEST_FAULT (degrade tests only): sut_inspect makes the SUT state unreadable;
# point_jq makes the point assembly fail; assembly makes the whole-matrix assembly fail.
case "${PERF_HW_MATRIX_TEST_FAULT:-}" in
  ""|sut_inspect|point_jq|assembly) ;;
  *) echo "ERROR: PERF_HW_MATRIX_TEST_FAULT='$PERF_HW_MATRIX_TEST_FAULT' (expected sut_inspect, point_jq or assembly)" >&2; exit 2 ;;
esac

# The SUT's peak memory and retained event-log entries/bytes over the point's samples.
sample_peaks() {
  MEM_PEAK="$(awk -F',' 'NR>1 && $4!="" { n++; if($4+0>m) m=$4+0 } END{ if(n>0) printf "%.0f", m; }' "$CPU_LOG" 2>/dev/null || echo '')"
  RETAINED_PEAK="$(awk -F',' 'NR>1 && $5!="" { n++; if($5+0>m) m=$5+0 } END{ if(n>0) printf "%.0f", m; }' "$CPU_LOG" 2>/dev/null || echo '')"
  RETAINED_BYTES_PEAK="$(awk -F',' 'NR>1 && $6!="" { n++; if($6+0>m) m=$6+0 } END{ if(n>0) printf "%.0f", m; }' "$CPU_LOG" 2>/dev/null || echo '')"
}

# One multik6 point against the running $SERVER: scripts/rw-multi-k6-sweep.sh in its existing-SUT
# mode. Sets RW_RC, XRATES, STATE_JSON and the memory peaks, and removes the SUT. The VU ceiling is
# pinned at sweep.js's 2,048 so a point measures the same client on every host. k6's GOGC stays 400
# unless the build sets one: the arm's 1600 default rests on perf-xl evidence the matrix never had.
# Its recorded p99-bounded ceiling keeps the whole-rung p99 (unmasked_p99), not the arm's masked rule.
run_point_multik6() {
  local rw_dir="$POINT_DIR"
  RW_RC=0
  mkdir -p "$rw_dir"
  XRATES="${PERF_HW_MATRIX_XCHECK_RATES:-$(tr ',' '\n' <<<"$POINT_RATES" | awk -v cap="$HW_XCHECK_MAX_RPS" '$1+0 <= cap' | head -3 | paste -sd, -)}"
  XRATES="${XRATES:-8000,16000,24000}"
  start_point_sampler
  PERF_RW_REPO_ROOT="$REPO_ROOT" PERF_RW_NETWORK="$NETWORK" PERF_RW_TARGET_URL="http://mockserver:1080" \
    PERF_RW_TARGET_CURL_URL="http://${HOSTPORT}" PERF_RW_SUT_CONTAINER="$SERVER" PERF_RW_SERVER_CPUS="$SCPU" \
    PERF_RW_K6_CPUSETS="$HW_K6_SPEC" PERF_RW_PROCS="${#HW_K6_SETS[@]}" PERF_RW_PROM_CPUS="$HW_PROM_CPUS" \
    PERF_RW_UPSTREAM_CPUS="" PERF_RW_IMAGE="$MOCKSERVER_IMAGE" PERF_RW_K6_IMAGE="$K6_IMAGE" \
    PERF_RW_RATES="$POINT_RATES" PERF_RW_STEP="$SWEEP_STEP" PERF_RW_GAP="$SWEEP_GAP" PERF_RW_SETTLE_S="$SWEEP_SETTLE_S" \
    PERF_RW_WARMUP_RATE="$POINT_WARMUP_RATE" PERF_RW_WARMUP_DURATION="$WARMUP_DURATION" \
    PERF_RW_XCHECK=true PERF_RW_XCHECK_RATES="$XRATES" PERF_RW_P99_MAX_MS="$HW_P99_MAX_MS" PERF_RW_HEADLINE_RULE=unmasked_p99 \
    PERF_RW_K6_VU_CEILING=2048 PERF_RW_K6_GOGC="${PERF_RW_K6_GOGC:-400}" PERF_RW_DEBUG_DIR="$rw_dir" \
    bash "$REPO_ROOT/mockserver-performance-test/scripts/rw-multi-k6-sweep.sh" "$rw_dir/rw-result.json" >&2 || RW_RC=$?
  kill "$SAMPLER_PID" >/dev/null 2>&1 || true
  wait "$SAMPLER_PID" 2>/dev/null || true
  STATE_JSON="$(sut_state_json)"
  docker rm -f "$SERVER" >/dev/null 2>&1 || true
  sample_peaks
}

# Everything a multik6 point's assembly reads besides its rw and jvm files, for re-assembly.
write_point_inputs() {
  jq -n --argjson meta "$POINT_META" --arg rates "$POINT_RATES" --argjson wrate "$POINT_WARMUP_RATE" \
    --arg scpu "$SCPU" --argjson k6c "$k6w" --argjson avail "$AVAIL" --argjson sphys "${SPHYS:-null}" \
    --argjson resolved "$RESOLVED_JSON" --argjson body "$BODY_BYTES" --argjson assumed "$ASSUMED_MAX_LOG_ENTRIES" \
    --argjson state "$STATE_JSON" --argjson rc "$RW_RC" --arg xrates "$XRATES" --argjson cg "$SUT_CGROUP_READABLE" \
    --argjson mempeak "${MEM_PEAK:-null}" --argjson retpeak "${RETAINED_PEAK:-null}" --argjson retbpeak "${RETAINED_BYTES_PEAK:-null}" \
    --argjson placement "${POINT_PLACEMENT:-null}" '
    {source:"live", key:$meta.key, meta:$meta, sweep_rates:$rates, warmup_rate:$wrate, server_cpus:$scpu, placement:$placement,
     k6_cores:$k6c, available_processors:$avail, server_physical_cores:$sphys, resolved:$resolved,
     body_bytes:$body, assumed_max_log_entries:$assumed, sut_state:$state, rw_exit_code:$rc,
     cross_check_rates:$xrates, cgroup_readable:$cg, memory_peak_bytes:$mempeak,
     retained_entries_peak:$retpeak, retained_bytes_peak:$retbpeak}' > "$POINT_DIR/point-inputs.json" \
    || echo "WARNING: could not write $POINT_DIR/point-inputs.json — $LBL cannot be re-assembled offline" >&2
}

# Re-assembly: the same variables from <point>/point-inputs.json; a point the original run skipped
# keeps its skip (skipped.ndjson). Returns 1 after recording a skip.
load_point_inputs() {
  local f="$POINT_DIR/point-inputs.json" prior
  if ! jq -e '(.sut_state | type) == "object" and (.resolved | type) == "object" and .server_cpus != null' "$f" >/dev/null 2>&1; then
    prior="$(jq -c --arg k "$PKEY" 'select(.key == $k)' "$REASSEMBLE_DIR/skipped.ndjson" 2>/dev/null | head -1 || true)"
    if [ -n "$prior" ]; then
      echo "--- $LBL: no point inputs; keeping the original run's skip" >&2
      SKIPPED+=("$prior")
    else
      STATE_JSON=null
      skip_point_failure "nothing to re-assemble: $PKEY/point-inputs.json is missing or incomplete"
    fi
    return 1
  fi
  SCPU="$(jq -r '.server_cpus' "$f")"; k6w="$(jq -c '.k6_cores' "$f")"; AVAIL="$(jq -c '.available_processors' "$f")"
  SPHYS="$(jq -c '.server_physical_cores' "$f")"; RESOLVED_JSON="$(jq -c '.resolved' "$f")"
  BODY_BYTES="$(jq -c '.body_bytes // 6' "$f")"; STATE_JSON="$(jq -c '.sut_state' "$f")"
  RW_RC="$(jq -c '.rw_exit_code' "$f")"; XRATES="$(jq -r '.cross_check_rates // ""' "$f")"
  SUT_CGROUP_READABLE="$(jq -r '.cgroup_readable == true' "$f")"
  ASSUMED_MAX_LOG_ENTRIES="$(jq -c --argjson d "$ASSUMED_MAX_LOG_ENTRIES" '.assumed_max_log_entries // $d' "$f")"
  POINT_RATES="$(jq -r --arg d "$POINT_RATES" '.sweep_rates // $d' "$f")"
  POINT_WARMUP_RATE="$(jq -c --argjson d "$POINT_WARMUP_RATE" '.warmup_rate // $d' "$f")"
  MEM_PEAK="$(jq -r '.memory_peak_bytes // empty' "$f")"; RETAINED_PEAK="$(jq -r '.retained_entries_peak // empty' "$f")"
  RETAINED_BYTES_PEAK="$(jq -r '.retained_bytes_peak // empty' "$f")"
  INPUTS_SOURCE="$(jq -r '.source // "unknown"' "$f")"
  POINT_PLACEMENT="$(jq -c '.placement // null' "$f")"
  return 0
}

# The point's rw result mapped into the point schema by lib/perf-hw-matrix-rw.jq, plus the knee
# diagnostics. Sets AGG, DIED_AT_RPS, DIED_BEFORE_SWEEP, HC_RPS and MAXLOG_USED; returns 1 after
# recording a skip. Every failure is checked explicitly: callers run it where errexit is off.
assemble_point_multik6() {
  local rw_dir="$POINT_DIR" rw hc50 hc99 fae valid died warm rung_jvm skip
  local point_jq="$SCRIPT_DIR/perf-hw-matrix-rw.jq"
  [ "${PERF_HW_MATRIX_TEST_FAULT:-}" = point_jq ] && point_jq="$point_jq.missing"
  # Report-only: kept for an invalid point too, so its knee can still be read.
  rung_jvm="$(jvm_rungs_json "$rw_dir/jvm-samples.csv" "$(jq -c '.rung_windows // []' "$rw_dir/rw-result.json" 2>/dev/null || echo '[]')" "$STEP_S" "$SWEEP_SETTLE_S" 2>/dev/null || echo null)"
  [ -n "$REASSEMBLE_DIR" ] || printf '%s\n' "${rung_jvm:-null}" > "$rw_dir/jvm-rungs.json"

  if [ "$(jq -r '.running' <<<"$STATE_JSON" 2>/dev/null)" != true ] && [ "$(jq -r '.running' <<<"$STATE_JSON" 2>/dev/null)" != false ]; then
    skip_point_failure "could not read the SUT's state after the sweep (docker inspect failed)"; return 1
  fi
  died=false
  if [ "$(jq -r '.running' <<<"$STATE_JSON")" != true ] || [ "$(jq -r '.java_oom_errors' <<<"$STATE_JSON")" != 0 ]; then
    died=true
    echo "WARNING: SUT did not survive the sweep cleanly at $LBL: $STATE_JSON" >&2
  fi
  rw="$(jq -c 'select(type == "object")' "$rw_dir/rw-result.json" 2>/dev/null || true)"
  [ -n "$rw" ] || rw="$(jq -nc --argjson rc "$RW_RC" '{valid:false, invalid_reasons:["the multi-k6 harness wrote no result (exit \($rc))"]}')"
  valid="$(jq -r '.valid == true' <<<"$rw")"
  # An invalid measurement is not a point. A SUT that died stays a point: its status is the result.
  if [ "$valid" != true ] && [ "$died" != true ]; then
    echo "ERROR: multi-k6 measurement INVALID at $LBL (exit $RW_RC): $(jq -r '(.invalid_reasons // []) | join("; ")' <<<"$rw")" >&2
    # Only the reasons go on the command line: the whole rw result can exceed the per-argument limit.
    skip="$(jq -c --argjson ir "$(jq -c '.invalid_reasons // []' <<<"$rw")" --argjson st "$STATE_JSON" --argjson res "$RESOLVED_JSON" --argjson rc "$RW_RC" '
      . + {type:"failure", status:"invalid_measurement",
           reason:("multi-k6 measurement failed its own validity checks: " + ($ir | map(split(":")[0]) | join(", "))),
           invalid_reasons:$ir, rw_exit_code:$rc, oom_killed:($st.oom_killed == true), sut_state:$st, resolved:$res}' <<<"$POINT_META")" || skip=""
    if [ -n "$skip" ]; then SKIPPED+=("$skip"); else skip_point_failure "multi-k6 measurement INVALID (exit $RW_RC); recording its reasons failed"; fi
    return 1
  fi

  hc50=null; hc99=null
  if [ "$valid" = true ]; then
    hc50="$(jq -c --arg now "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --argjson lat_mult 3 --argjson keep 0.95 --arg fix_date "2026-09-16" \
      -f "$FIGURES_JQ" <<<"$rw" 2>/dev/null | jq -c '.headline')" || hc50=null
    hc99="$(jq -c '.headline // null' <<<"$rw")"
  fi
  MAXLOG_USED="$(jq -r '.max_log_entries // empty' <<<"$RESOLVED_JSON")"
  MAXLOG_USED="${MAXLOG_USED:-$ASSUMED_MAX_LOG_ENTRIES}"
  AGG="$(jq -c --argjson cores "$C" --argjson hc50 "${hc50:-null}" --argjson hc99 "${hc99:-null}" \
    --argjson p99_max_ms "$HW_P99_MAX_MS" --argjson maxlog "$MAXLOG_USED" --argjson body "$BODY_BYTES" \
    -f "$point_jq" <<<"$rw")" || AGG=""
  [ -n "$AGG" ] || { skip_point_failure "assembling the point from the multi-k6 result failed"; return 1; }
  HC_RPS="$(jq -r '.healthy_ceiling_rps // "null"' <<<"$AGG")"

  # The rung the SUT stopped in, from its FinishedAt against the measured rung starts.
  DIED_AT_RPS=null; DIED_BEFORE_SWEEP=false
  fae="$(jq -r '.finished_at_epoch // empty' <<<"$STATE_JSON")"
  if [ -n "$fae" ] && [ "$(jq -r '.running' <<<"$STATE_JSON")" != true ]; then
    DIED_AT_RPS="$(jq -r --argjson t "$fae" '[(.rung_windows // [])[] | select(.start_epoch_ms != null and .start_epoch_ms / 1000 <= $t) | .offered_rps] | last // "null"' <<<"$rw")"
    [ "$DIED_AT_RPS" = null ] && DIED_BEFORE_SWEEP=true
  fi

  warm="$(jq -c '{drive_p50_ms:(.points[0].p50_ms // null), drive_achieved_rps:(.points[0].achieved_rps // null)}' "$rw_dir/warmup.json" 2>/dev/null || echo '{}')"
  AGG="$(jq -c --arg scpu "$SCPU" --arg kcpu "$HW_K6_SPEC" --argjson k6c "$k6w" --argjson avail "$AVAIL" \
    --argjson warm "$warm" --argjson wrate "$POINT_WARMUP_RATE" --argjson rc "$RW_RC" --arg xrates "$XRATES" \
    --argjson rjvm "${rung_jvm:-null}" --arg jto "$SUT_JTO" --argjson gclog "$([ "$HW_GC_LOG" = true ] && echo true || echo false)" \
    --argjson cg "$SUT_CGROUP_READABLE" --argjson placement "${POINT_PLACEMENT:-null}" '
    .server_cpus=$scpu | .k6_cpus=$kcpu | .k6_cores=$k6c | .available_processors=$avail | .placement=$placement
    | .jvm = {java_tool_options:(if $jto == "" then null else $jto end), gc_log:$gclog, cgroup_readable:$cg}
    | .rung_jvm = $rjvm
    | .measurement += {rw_exit_code:$rc, cross_check_rates:$xrates}
    | (.ladder[0].p50_ms // null) as $r1 | (.ladder[1].p50_ms // null) as $r2
    | .warmup = ($warm + {drive_rate:$wrate, first_rung_p50_ms:$r1, second_rung_p50_ms:$r2,
                          first_rung_slower_than_second:(($r1 != null) and ($r2 != null) and ($r1 > $r2))})' <<<"$AGG")" || AGG=""
  [ -n "$AGG" ] || { skip_point_failure "adding the placement and warm-up to the point failed"; return 1; }
  return 0
}

# Memory, survival and lower-bound labelling, then the point is recorded. A ceiling is a LOWER
# BOUND when its rung was client-limited, the SUT never neared its CPU pin, it is the top rung
# offered, or the CPU samples needed to rule the first two out are missing (cpu_unverified).
# Returns 1 after recording a skip.
finish_point() {
  if [ "$(jq -r '.running' <<<"$STATE_JSON" 2>/dev/null)" != true ] && [ "$(jq -r '.running' <<<"$STATE_JSON" 2>/dev/null)" != false ]; then
    skip_point_failure "could not read the SUT's state after the sweep (docker inspect failed)"; return 1
  fi
  AGG="$(jq -c --argjson meta "$POINT_META" --argjson resolved "$RESOLVED_JSON" --argjson state "$STATE_JSON" \
    --argjson mempeak "${MEM_PEAK:-null}" --argjson retpeak "${RETAINED_PEAK:-null}" --argjson died_at "$DIED_AT_RPS" \
    --argjson retbpeak "${RETAINED_BYTES_PEAK:-null}" \
    --argjson sphys "${SPHYS:-null}" --argjson assumed "$ASSUMED_MAX_LOG_ENTRIES" --argjson used "$MAXLOG_USED" \
    --arg rates "$POINT_RATES" --argjson topo "$TOPO_KNOWN" --argjson died_early "$DIED_BEFORE_SWEEP" '
    def frac($a; $b): if $a == null or $b == null or $b <= 0 then null else (($a / $b) * 1000 | round) / 1000 end;
    . + $meta
    | ([.ladder[].offered_rps] | max) as $top
    | .healthy_ceiling_rps as $hc
    | ($state.running == false) as $died
    | (($state.java_oom_errors // 0) > 0) as $jvm_oom
    | .server_physical_cores = $sphys
    | .cpus_physically_verified = $topo
    | .sweep_rates = $rates
    | .resolved = $resolved
    | .heap_frac_of_memory_limit = frac($resolved.max_heap_bytes; $meta.memory_limit_bytes)
    | .container_memory_peak_bytes = $mempeak
    | .container_memory_peak_frac_of_limit = frac($mempeak; $meta.memory_limit_bytes)
    | .event_log_retained_entries_peak = $retpeak
    | .event_log_retained_bytes_peak = $retbpeak
    | frac($retpeak; $resolved.max_log_entries) as $cu
    | frac($retbpeak; $resolved.max_event_log_bytes) as $bu
    | .event_log_count_utilisation = $cu
    | .event_log_bytes_utilisation = $bu
    # Either bound evicts, so the log is full when EITHER is reached.
    | .event_log_filled = (if $cu == null and $bu == null then null
                           else ((($cu // 0) >= 0.95) or (($bu // 0) >= 0.95)) end)
    | .event_log_binding_bound = (if $cu == null and $bu == null then null
                                  elif ($bu // 0) >= ($cu // 0) then "bytes" else "entries" end)
    | .event_log_mean_entry_bytes = (if ($retpeak // 0) > 0 and $retbpeak != null
                                     then ($retbpeak / $retpeak | round) else null end)
    | .retention.assumed_max_log_entries = $assumed
    | .retention.max_log_entries_used = $used
    | .retention.max_log_entries_source = (if $resolved.max_log_entries != null then "resolved" else "assumed" end)
    | .retention.retained_bytes_estimate = ($used * .retention.body_bytes)
    | .sut_state = $state
    | .oom_killed = ($state.oom_killed == true)
    | .java_out_of_memory = $jvm_oom
    | .sut_survived = (($died or $jvm_oom) | not)
    | .died_at_offered_rps = (if $died then $died_at else null end)
    | .died_before_sweep = ($died and $died_early)
    | .client_headroom_frac_at_ceiling = (if .healthy_ceiling_client_cpu_pct == null then null
                                          else frac(.client_pin_pct - .healthy_ceiling_client_cpu_pct; .client_pin_pct) end)
    # A SUT that did not survive has no ceiling to bound; its status says what happened.
    # multik6 points carry derive_saturation-based candidates (lib/perf-hw-matrix-rw.jq).
    | .lower_bound_reasons = if ($died or $jvm_oom) then []
        elif has("lower_bound_candidates") then .lower_bound_candidates else [
        (if .client_limited_at_ceiling == true then "client_cpu_limited" else empty end),
        (if $hc != null and .peak_limited_by == "load_path_or_virtualization" then "server_cpu_not_saturated" else empty end),
        (if $hc != null and $hc == $top then "ladder_top_reached" else empty end),
        (if $hc != null and (.peak_limited_by == null or .healthy_ceiling_client_cpu_pct == null)
         then "cpu_unverified" else empty end) ] end
    | del(.lower_bound_candidates)
    | .lower_bound = ((.lower_bound_reasons | length) > 0)
    | .status = (if $state.oom_killed == true then "oom_killed" elif $died then "sut_died"
                 elif $jvm_oom then "java_out_of_memory" elif $hc == null then "no_healthy_ceiling"
                 else "measured" end)' <<<"$AGG")" || AGG=""
  [ -n "$AGG" ] || { skip_point_failure "labelling the point's memory, survival and lower bounds failed"; return 1; }
  if [ -n "$REASSEMBLE_DIR" ]; then
    AGG="$(jq -c --arg src "$INPUTS_SOURCE" '. + {inputs_source:$src}' <<<"$AGG")" \
      || { skip_point_failure "marking the re-assembled point failed"; return 1; }
  fi

  echo "    $LBL  status=$(jq -r '.status' <<<"$AGG") heap=$(jq -r '.resolved.max_heap_bytes' <<<"$AGG") mem_peak=$(jq -r '.container_memory_peak_frac_of_limit' <<<"$AGG") lower_bound=$(jq -r '.lower_bound_reasons | join("+")' <<<"$AGG")" >&2
  echo "    C=$C  healthy_ceiling=${HC_RPS} rps_per_core=$(jq -r '.rps_per_core' <<<"$AGG") peak=$(jq -r '.rig_valid_peak_achieved_rps' <<<"$AGG") sut_cpu@peak=$(jq -r '.sut_cpu_at_peak_pct' <<<"$AGG")%/$(jq -r '.sut_pin_pct' <<<"$AGG")% peak_limited_by=$(jq -r '.peak_limited_by' <<<"$AGG")" >&2
  POINTS+=("$AGG")
  if [ "$C" -gt "$MAX_MEASURED" ]; then MAX_MEASURED="$C"; fi
  if [ "$MODE" = hw_matrix ] && [ -z "$REASSEMBLE_DIR" ]; then
    printf '%s\n' "$AGG" > "$POINT_DIR/point.json" 2>/dev/null || true
  fi
  return 0
}

POINTS=()     # per-point aggregate JSON objects
SKIPPED=()    # {cores, reason, type, ...} for every requested-but-unmeasured point
MAX_MEASURED=0

for PI in "${!P_CORES[@]}"; do
  C="${P_CORES[$PI]}"; MEM="${P_MEM[$PI]}"; IS_CONTROL="${P_CONTROL[$PI]}"
  AGG=""; STATE_JSON=null
  MEM_BYTES="$(mem_to_bytes "$MEM")"
  if [ "$MODE" = hw_matrix ]; then
    PKEY="${C}c-${MEM}"; LBL="C=$C mem=$MEM"; [ "$IS_CONTROL" = true ] && LBL="$LBL (control)"
  else
    PKEY="${C}c"; LBL="C=$C"
  fi
  POINT_DIR="${PERF_HW_MATRIX_DEBUG_DIR:+$PERF_HW_MATRIX_DEBUG_DIR/$PKEY}"; POINT_DIR="${POINT_DIR:-$WORK/rw-${PKEY}}"
  [ -n "$REASSEMBLE_DIR" ] && POINT_DIR="$REASSEMBLE_DIR/$PKEY"
  POINT_META="$(jq -nc --argjson c "$C" --arg mem "$MEM" --argjson memb "${MEM_BYTES:-null}" \
    --argjson ctl "$IS_CONTROL" --arg key "$PKEY" --arg mode "$MODE" '
    if $mode == "hw_matrix" then {cores:$c, key:$key, memory_limit:$mem, memory_limit_bytes:$memb, control:$ctl}
    else {cores:$c, memory_limit:$mem, memory_limit_bytes:$memb} end')"
  ASSUMED_MAX_LOG_ENTRIES="${PERF_PERCORE_MAX_LOG_ENTRIES:-$(percore_default_max_log_entries "$MEM")}"

  # Only offer rates up to MAX_RPS_PER_CORE x C in hw_matrix mode (see SWEEP_RATES).
  POINT_RATES="$SWEEP_RATES"
  if [ "$MODE" = hw_matrix ] && [ "$HW_MAX_RPS_PER_CORE" -gt 0 ]; then
    POINT_RATES="$(tr ',' '\n' <<<"$SWEEP_RATES" | awk -v cap="$(( HW_MAX_RPS_PER_CORE * C ))" '$1+0 <= cap' | paste -sd, -)"
    [ -n "$POINT_RATES" ] || POINT_RATES="$SWEEP_RATES"
  fi
  [ "$HW_CLIENT" = multik6 ] && POINT_RATES="$(hw_point_rates "$C")"
  POINT_WARMUP_RATE="$WARMUP_RATE"
  if [ "$MODE" = hw_matrix ] && [ -z "${PERF_PERCORE_WARMUP_RATE:-}" ]; then
    POINT_WARMUP_RATE=$(( HW_WARMUP_RPS_PER_CORE * C ))
  fi

  if [ -n "$REASSEMBLE_DIR" ]; then
    echo "+++ $LBL  re-assembling from $POINT_DIR" >&2
    if load_point_inputs && assemble_point_multik6; then finish_point || true; fi
    continue
  fi

  # Feasibility: the SUT needs C cores AND k6 needs >= K6_MIN_CORES on DISJOINT
  # cores (with K6_RESERVE held back for the box). The C=16 prerequisite:
  # at C=16 on a <18-core box this is false and the point is skipped
  # with a reason rather than measured wrong (shared cores) or silently omitted.
  read -r SCPU KCPU k6w <<<"$(select_cpusets "$C")"
  if [ "$HW_CLIENT" = multik6 ]; then
    # The client placement is fixed; a point whose SUT cores reach into it cannot be measured.
    KCPU="$HW_K6_SPEC"; k6w="$(expand_cpuset "${HW_K6_SPEC//;/,}" | wc -w | tr -d ' ')"
    reason=""
    if [ "$C" -gt "$HOST_CORES" ] || [ "$SCPU" = "-" ]; then
      reason="needs ${C} SUT cores; host has ${HOST_CORES} logical / ${HOST_PHYS_CORES} physical"
    elif cpusets_logically_overlap "$SCPU" "${HW_K6_SPEC//;/,},${HW_PROM_CPUS}"; then
      reason="SUT cpus ${SCPU} reach into the fixed client placement (k6 ${HW_K6_SPEC}, Prometheus ${HW_PROM_CPUS})"
    fi
    if [ -n "$reason" ]; then
      echo "--- $LBL SKIPPED: $reason" >&2
      SKIPPED+=("$(jq -c --arg r "$reason" '. + {reason:$r, type:"infeasible"}' <<<"$POINT_META")")
      continue
    fi
    _pairs=(server "$SCPU" prometheus "$HW_PROM_CPUS")
    for i in "${!HW_K6_SETS[@]}"; do _pairs+=("k6_$i" "${HW_K6_SETS[$i]}"); done
    if ! cpusets_physically_disjoint "${_pairs[@]}" >&2; then
      SKIPPED+=("$(jq -c '. + {reason:"server, Prometheus and k6 cpusets share a physical core", type:"failure"}' <<<"$POINT_META")")
      continue
    fi
  elif [ "$C" -gt "$HOST_CORES" ] || [ "$SCPU" = "-" ] || [ "$KCPU" = "-" ] || [ "$k6w" -lt "$K6_MIN_CORES" ]; then
    reason="needs ${C} SUT cores + >=${K6_MIN_CORES} disjoint client cores + ${K6_RESERVE} reserved; host has ${HOST_CORES} logical / ${HOST_PHYS_CORES} physical"
    echo "--- $LBL SKIPPED: $reason" >&2
    SKIPPED+=("$(jq -c --arg r "$reason" '. + {reason:$r, type:"infeasible"}' <<<"$POINT_META")")
    continue
  fi
  # multik6 proved its own placement above; its KCPU is ';'-separated, which this check cannot parse.
  if [ "$HW_CLIENT" = single ] && ! cpusets_physically_disjoint server "$SCPU" k6 "$KCPU" >&2; then
    SKIPPED+=("$(jq -c '. + {reason:"server and k6 cpusets share a physical core", type:"failure"}' <<<"$POINT_META")")
    continue
  fi
  # NUMA guard: the SUT and each k6 cpuset on one node, k6 on another node when there is one.
  IFS=';' read -ra _k6_sets <<< "$KCPU"
  if ! numa_placement_check "$K6_NUMA_NODE" "$SCPU" "${_k6_sets[@]}" >&2 \
     || { [ "$HW_CLIENT" = multik6 ] && ! assert_cpuset_single_node prometheus "$HW_PROM_CPUS" >&2; }; then
    SKIPPED+=("$(jq -c '. + {reason:"the SUT, k6 or Prometheus cpusets fail the NUMA placement guard (a cpuset straddles nodes, or k6 shares the SUT node)", type:"failure"}' <<<"$POINT_META")")
    continue
  fi
  POINT_PLACEMENT="$(numa_placement_json "$HW_LAYOUT" "$K6_NUMA_NODE" "$SCPU" "${_k6_sets[@]}")"
  SUT_MEMS="$(numa_mems_flag "$SCPU")"; K6_MEMS="$(numa_mems_flag "${KCPU//;/,}")"
  if [ "$PLACEMENT_ONLY" = true ]; then
    POINTS+=("$(jq -c --arg s "$SCPU" --arg k "$KCPU" --arg sm "$SUT_MEMS" --arg km "$K6_MEMS" --argjson pl "$POINT_PLACEMENT" \
      '. + {server_cpus:$s, k6_cpus:$k, mems:{server:$sm, k6:$km}, placement:$pl}' <<<"$POINT_META")")
    continue
  fi
  SPHYS="$(phys_core_count "$SCPU")"

  echo "+++ $LBL  server_cpus=$SCPU  k6_cpus=$KCPU (${k6w} client cores)  rates=$POINT_RATES" >&2

  # --- pinning proof: the SUT image's JVM, on the server cpuset -----------------
  AVAIL="$(probe_processors "$SCPU" || true)"
  echo "    availableProcessors (SUT image JVM @ cpuset $SCPU) = ${AVAIL:-unknown}" >&2
  if [ "${AVAIL:-0}" != "$C" ]; then
    echo "ERROR: pin proof FAILED at $LBL — JVM reported '${AVAIL:-unknown}' processors, expected $C. --cpuset-cpus did not take; refusing to record a mislabelled point." >&2
    SKIPPED+=("$(jq -c --arg r "pin proof failed: JVM saw ${AVAIL:-unknown} processors, expected ${C}" '. + {reason:$r, type:"failure"}' <<<"$POINT_META")")
    continue
  fi

  # --- start the SUT pinned to C cores, memory-limited ---------------------------
  # No --rm: an OOM-killed or crashed SUT must stay inspectable so the point records
  # it rather than vanishing. --memory-swap equal to --memory means no swap, so the
  # limit is the whole budget (as on a Kubernetes pod); no -Xmx, so the image's
  # MaxRAMPercentage sizes the heap from the limit exactly as a user's container.
  docker rm -f "$SERVER" >/dev/null 2>&1 || true
  sut_extra=()
  if [ "$MODE" = hw_matrix ]; then
    # GC log in the point dir (the work-files tgz), writable by the image's non-root user.
    mkdir -p "$POINT_DIR/jvm" && chmod 0777 "$POINT_DIR/jvm"
    sut_extra+=(-v "$POINT_DIR/jvm:/jvm-diag")
    [ -n "$SUT_JTO" ] && sut_extra+=(-e "JAVA_TOOL_OPTIONS=$SUT_JTO")
  fi
  docker run -d --name "$SERVER" --network "$NETWORK" --network-alias mockserver \
    --cpuset-cpus="$SCPU" ${SUT_MEMS:+"$SUT_MEMS"} --memory="$MEM" --memory-swap="$MEM" -p 127.0.0.1::1080 \
    ${sut_extra[@]+"${sut_extra[@]}"} \
    -e MOCKSERVER_LOG_LEVEL=ERROR -e MOCKSERVER_DISABLE_SYSTEM_OUT=true \
    -e MOCKSERVER_METRICS_ENABLED=true \
    "$MOCKSERVER_IMAGE" -serverPort 1080 >/dev/null
  SUT_CGROUP_DIR=""
  [ "$MODE" = hw_matrix ] && SUT_CGROUP_DIR="$(sut_cgroup_dir "$(docker inspect -f '{{.Id}}' "$SERVER" 2>/dev/null || true)")"
  SUT_CGROUP_READABLE=false; [ -n "$SUT_CGROUP_DIR" ] && SUT_CGROUP_READABLE=true

  HOSTPORT="$(docker port "$SERVER" 1080/tcp 2>/dev/null | head -1)"
  HOSTPORT="${HOSTPORT:-127.0.0.1:1080}"
  METRICS_URL="http://${HOSTPORT}/mockserver/metrics"

  # --- readiness: a listening port is NOT readiness. MockServer accepts then
  # RESETS during init, so poll PUT /mockserver/status (unauthenticated, present
  # in all versions) until it answers 200. ------------------------------------
  ready=false
  for _ in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 -X PUT "http://${HOSTPORT}/mockserver/status" 2>/dev/null || echo 000)"
    if [ "$code" = "200" ]; then ready=true; break; fi
    if ! grep -q "^${SERVER}$" <<<"$(docker ps --format '{{.Names}}' || true)"; then
      echo "ERROR: SUT container exited during startup at $LBL" >&2; docker logs "$SERVER" 2>&1 | tail -20 >&2 || true; break
    fi
    sleep 2
  done
  if [ "$ready" != true ]; then
    STATE_JSON="$(sut_state_json)"
    echo "ERROR: SUT not ready at $LBL — skipping this point (state: $STATE_JSON)" >&2
    SKIPPED+=("$(jq -c --argjson st "$STATE_JSON" '. + {reason:(if $st.oom_killed == true then "SUT was OOM-killed during startup" else "SUT did not become ready (PUT /mockserver/status != 200)" end), type:"failure", oom_killed:$st.oom_killed, sut_state:$st}' <<<"$POINT_META")")
    docker rm -f "$SERVER" >/dev/null 2>&1 || true
    continue
  fi

  # --- resolved heap and event-log bounds, from the SUT's own gauges -------------
  # These are what the JVM and MockServer actually applied under this memory limit,
  # not a formula: the heap ceiling the JVM reports and the event log's count/byte bounds.
  RESOLVED_JSON="$(resolved_bounds_json)"
  echo "    resolved: $RESOLVED_JSON" >&2

  # Measure the served body size once (for the retention arithmetic) — seed /simple
  # and GET it. sweep.js re-seeds/reset in setup()/teardown(), so this is only for
  # the byte count; it does not perturb the measured window.
  curl -s --max-time 5 -X PUT "http://${HOSTPORT}/mockserver/expectation" \
    -H 'Content-Type: application/json' \
    -d '[{"httpRequest":{"path":"/simple"},"httpResponse":{"statusCode":200,"body":"simple"},"times":{"unlimited":true}}]' \
    -o /dev/null 2>/dev/null || true
  # `|| true` throughout: a SUT that dies here (e.g. OOM-killed right after start) must
  # be recorded as a point, not abort the whole matrix under set -e / pipefail.
  BODY_BYTES="$(curl -s --max-time 5 "http://${HOSTPORT}/simple" 2>/dev/null | wc -c | tr -d ' ' || true)"
  BODY_BYTES="${BODY_BYTES:-6}"

  if [ "$HW_CLIENT" = multik6 ]; then
    run_point_multik6 || true
    write_point_inputs
    assemble_point_multik6 || continue
  else # ---- single-k6 client: percore mode, and hw_matrix with PERF_HW_MATRIX_CLIENT=single ----
  # --- warm-up drive (NEVER measured): remove the JIT/first-touch transient so
  # the sweep's first rung is not systematically slow. ------------------------
  WARMUP_JSON="$WORK/warmup-${PKEY}.json"
  docker run --rm --network "$NETWORK" --cpuset-cpus="$KCPU" ${K6_MEMS:+"$K6_MEMS"} \
    -v "$K6_DIR:/k6:ro" -v "$WORK:/out" \
    -e "BASE_URL=http://mockserver:1080" -e "PROTO=http" \
    -e "K6_SWEEP_RATES=$POINT_WARMUP_RATE" -e "K6_SWEEP_STEP=$WARMUP_DURATION" -e "K6_SWEEP_GAP=1s" \
    -e "K6_SWEEP_SETTLE=0s" -e "K6_SWEEP_RESULT_PATH=/out/warmup-${PKEY}.json" \
    ${SWEEP_PRE_VUS:+-e "K6_SWEEP_PRE_VUS=$SWEEP_PRE_VUS"} ${SWEEP_MAX_VUS:+-e "K6_SWEEP_MAX_VUS=$SWEEP_MAX_VUS"} \
    "$K6_IMAGE" run /k6/sweep.js >/dev/null 2>&1 || true
  WARMUP_P50="$(jq -r '(.points[0].p50_ms) // null' "$WARMUP_JSON" 2>/dev/null || echo null)"
  WARMUP_ACH="$(jq -r '(.points[0].achieved_rps) // null' "$WARMUP_JSON" 2>/dev/null || echo null)"

  start_point_sampler

  SWEEP_JSON="$WORK/sweep-${PKEY}.json"
  T0="$(date -u +%s)"
  docker run --rm --name "$K6_NAME" --network "$NETWORK" --cpuset-cpus="$KCPU" ${K6_MEMS:+"$K6_MEMS"} \
    -v "$K6_DIR:/k6:ro" -v "$WORK:/out" \
    -e "BASE_URL=http://mockserver:1080" -e "PROTO=http" \
    -e "K6_SWEEP_RATES=$POINT_RATES" -e "K6_SWEEP_STEP=$SWEEP_STEP" -e "K6_SWEEP_GAP=$SWEEP_GAP" \
    -e "K6_SWEEP_SETTLE=${SWEEP_SETTLE_S}s" -e "K6_SWEEP_RESULT_PATH=/out/sweep-${PKEY}.json" \
    ${SWEEP_PRE_VUS:+-e "K6_SWEEP_PRE_VUS=$SWEEP_PRE_VUS"} ${SWEEP_MAX_VUS:+-e "K6_SWEEP_MAX_VUS=$SWEEP_MAX_VUS"} \
    "$K6_IMAGE" run --quiet /k6/sweep.js >&2 || true
  kill "$SAMPLER_PID" >/dev/null 2>&1 || true
  wait "$SAMPLER_PID" 2>/dev/null || true

  # --- did the SUT survive? (OOM kill / crash / JVM OutOfMemoryError) ----------
  STATE_JSON="$(sut_state_json)"
  docker rm -f "$SERVER" >/dev/null 2>&1 || true
  if [ "$(jq -r '.running' <<<"$STATE_JSON")" != true ] || [ "$(jq -r '.java_oom_errors' <<<"$STATE_JSON")" != 0 ]; then
    echo "WARNING: SUT did not survive the sweep cleanly at $LBL: $STATE_JSON" >&2
  fi

  if ! jq -e '.points | length > 0' "$SWEEP_JSON" >/dev/null 2>&1; then
    echo "ERROR: sweep produced no points at $LBL" >&2
    SKIPPED+=("$(jq -c --argjson st "$STATE_JSON" '. + {reason:(if $st.oom_killed == true then "SUT was OOM-killed and the sweep produced no points" else "sweep produced no points" end), type:"failure", oom_killed:$st.oom_killed, sut_state:$st}' <<<"$POINT_META")")
    continue
  fi
  if ! WINDOW_MISMATCH="$(sweep_window_mismatches "$SWEEP_JSON")"; then
    echo "ERROR: sweep latency windows inconsistent at $LBL — $WINDOW_MISMATCH" >&2
    SKIPPED+=("$(jq -c --arg r "latency windows inconsistent: $WINDOW_MISMATCH" '. + {reason:$r, type:"failure"}' <<<"$POINT_META")")
    continue
  fi

  # --- per-rung max k6 AND SUT CPU% from the sampler + the known ladder schedule
  # NOTE (scheduling caveat, review observation): each rung's window is derived
  # from T0 + i*(step+gap), which ASSUMES the k6 scenarios start the instant the
  # container launches. Container + k6 init latency shifts the real schedule by a
  # few seconds, so the FIRST rung's CPU attribution (and thus its rig_valid) can
  # be slightly misaligned; SWEEP_SETTLE_S trims the leading edge but does not
  # eliminate it. A k6-emitted scenario-start timestamp would fix it exactly; the
  # SUT-CPU series added here is the cross-check in the meantime (a rung mislabelled
  # by a schedule shift still shows the SUT's true CPU for that window).
  K6_PIN_PCT=$(( k6w * 100 ))
  SUT_PIN_PCT=$(( C * 100 ))
  CPU_MAP="{}"; SUT_CPU_MAP="{}"
  IFS=',' read -ra RATE_ARR <<< "$POINT_RATES"
  for i in "${!RATE_ARR[@]}"; do
    r="${RATE_ARR[$i]}"
    ws=$(( T0 + i * (STEP_S + GAP_S) + SWEEP_SETTLE_S ))
    we=$(( T0 + i * (STEP_S + GAP_S) + STEP_S ))
    # Emit the max in the window, or EMPTY when the window caught NO sample (so a
    # sparse/last-rung window becomes null, not a false 0% — a 0 would misread as
    # "idle" and mis-attribute the limit). `n` counts matched rows.
    maxcpu="$(awk -F',' -v a="$ws" -v b="$we" 'NR>1 && $1>=a && $1<b && $2!="" { n++; if($2+0>m) m=$2+0 } END{ if(n>0) printf "%.1f", m; }' "$CPU_LOG" 2>/dev/null || echo '')"
    maxsut="$(awk -F',' -v a="$ws" -v b="$we" 'NR>1 && $1>=a && $1<b && $3!="" { n++; if($3+0>m) m=$3+0 } END{ if(n>0) printf "%.1f", m; }' "$CPU_LOG" 2>/dev/null || echo '')"
    CPU_MAP="$(jq -c --arg k "$r" --argjson v "${maxcpu:-null}" '. + {($k): $v}' <<<"$CPU_MAP")"
    SUT_CPU_MAP="$(jq -c --arg k "$r" --argjson v "${maxsut:-null}" '. + {($k): $v}' <<<"$SUT_CPU_MAP")"
  done
  # The rung the SUT stopped in, from its FinishedAt against the ladder schedule.
  DIED_AT_RPS=null; DIED_BEFORE_SWEEP=false
  _fae="$(jq -r '.finished_at_epoch // empty' <<<"$STATE_JSON")"
  if [ -n "$_fae" ] && [ "$(jq -r '.running' <<<"$STATE_JSON")" != true ]; then
    if [ "$_fae" -lt "$T0" ]; then
      DIED_BEFORE_SWEEP=true
    else
      _idx=$(( (_fae - T0) / (STEP_S + GAP_S) ))
      [ "$_idx" -ge "${#RATE_ARR[@]}" ] && _idx=$(( ${#RATE_ARR[@]} - 1 ))
      DIED_AT_RPS="${RATE_ARR[$_idx]}"
    fi
  fi

  # --- healthy_ceiling via the ONE authoritative implementation ----------------
  # Feed this C's sweep as a synthetic run to lib/perf-website-figures.jq and read
  # its headline back. No re-implementation of the ceiling rule here.
  NOW_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  SYN_RUN="$(jq -nc --slurpfile s "$SWEEP_JSON" --arg cpus "$SCPU" --arg ts "$NOW_ISO" \
    '{schema_version:2, timestamp_utc:$ts, config:{}, agent:{server_cpus:$cpus}, sweep:$s[0]}')"
  HEADLINE="$(jq --arg now "$NOW_ISO" --argjson lat_mult 3 --argjson keep 0.95 --arg fix_date "2026-09-16" \
    -f "$FIGURES_JQ" <<<"$SYN_RUN" 2>/dev/null | jq -c '.headline // {}' || echo '{}')"
  HC_RPS="$(jq -r '.healthy_ceiling_rps // null' <<<"$HEADLINE")"
  HC_P50="$(jq -r '.healthy_ceiling_p50_ms // null' <<<"$HEADLINE")"
  HC_P95="$(jq -r '.healthy_ceiling_p95_ms // null' <<<"$HEADLINE")"

  # --- rig-valid peak + per-rung ladder (spread, tail suppression, residence) ---
  # rig_valid is perf-test-run.sh's saturation block MINUS the no-drops term: k6 CPU
  # headroom (< 85% of its pin) and error_rate <= eps, but NOT "no dropped iterations".
  # That difference is deliberate and is explained at the $rig_valid line below -- with
  # client CPU headroom, drops mean the SERVER could not keep up, which is the signal
  # this ladder is here to capture rather than a reason to discard the rung.
  # The perf-test-run.sh block does include $no_drops; do not "restore" it here. rig_valid_peak_achieved_rps
  # = max achieved over rig-valid rungs. p95/p99 are SUPPRESSED to null on any rung
  # whose post-settle sample count < MIN_TAIL_SAMPLES (the repo rule) so a low-C, low-rate rung
  # never reports a tail that is really just its max.
  MAXLOG_USED="$(jq -r '.max_log_entries // empty' <<<"$RESOLVED_JSON")"
  MAXLOG_USED="${MAXLOG_USED:-$ASSUMED_MAX_LOG_ENTRIES}"
  AGG="$(jq -nc \
    --slurpfile sweep "$SWEEP_JSON" \
    --argjson cpu "$CPU_MAP" \
    --argjson sutcpu "$SUT_CPU_MAP" \
    --argjson sutpin "$SUT_PIN_PCT" \
    --argjson pin "$K6_PIN_PCT" \
    --argjson cores "$C" \
    --argjson k6cores "$k6w" \
    --argjson err_eps "$SWEEP_ERR_EPS" \
    --argjson min_tail "$MIN_TAIL_SAMPLES" \
    --argjson maxlog "$MAXLOG_USED" \
    --argjson body "$BODY_BYTES" \
    --argjson hc_rps "${HC_RPS:-null}" \
    --argjson hc_p50 "${HC_P50:-null}" \
    --argjson hc_p95 "${HC_P95:-null}" '
    ($pin * 0.85) as $cpu_ceiling
    | (($sweep[0].points) // []) as $points
    | [ $points[]
        | ($cpu[(.offered_rps|tostring)]) as $c
        | ($sutcpu[(.offered_rps|tostring)]) as $sc
        | (.dropped_iterations // 0) as $drops
        | (.error_rate // 0) as $err
        | (.offered_rps) as $off | (.achieved_rps // 0) as $ach
        | (.sample_count // 0) as $n
        | (.measured_sample_count // $n) as $n_tail
        | (($k6cores <= 0) or ($c == null) or ($c <= $cpu_ceiling)) as $headroom
        | ($err <= $err_eps) as $low_err
        # A rung is CLIENT-SOUND (trustworthy as a SERVER figure) iff the k6 client
        # had CPU headroom and was not erroring. Dropped iterations do NOT by
        # themselves invalidate it: with client CPU headroom, drops mean the SERVER
        # could not keep up (VUs blocked on slow responses) — that IS the saturation
        # signal we are looking for, not a client limit. (Proven: at C=1 the 1-core
        # SUT dropped iterations from ~1000 rps up while k6 sat at ~250% of its
        # 1200% pin — the earlier no-drops rule mislabelled that as client-starved
        # and collapsed the peak to the last hiccup-free rung.) Only when the client
        # is AT its CPU pin do drops indicate client VU-starvation.
        | ($headroom and $low_err) as $rig_valid
        # server_saturated: dropped iterations WITH client headroom — the server,
        # not the client, was the limit at this rung.
        | ($drops > 0 and $headroom) as $server_saturated
        | ($n_tail >= $min_tail) as $enough_tail
        | {
            offered_rps:$off, achieved_rps:$ach, sample_count:$n,
            measured_sample_count:.measured_sample_count, settle_excluded:.settle_excluded,
            p50_ms:.p50_ms,
            # tail suppression: null below MIN_TAIL_SAMPLES (see comment above).
            p95_ms:(if $enough_tail then .p95_ms else null end),
            p99_ms:(if $enough_tail then .p99_ms else null end),
            error_rate:$err, dropped_iterations:$drops, k6_cpu_pct:$c,
            # SUT CPU% for this rung + how close it ran to its C-core pin. This is
            # the datum that attributes a falling peak: SUT near its pin => server
            # was the ceiling; SUT well below it while achieved fell => the load
            # path / virtualization was the limit, not the server.
            sut_cpu_pct:$sc,
            sut_cpu_frac_of_pin:(if ($sc == null or $sutpin <= 0) then null else (($sc / $sutpin) * 1000 | round) / 1000 end),
            rig_valid:$rig_valid, server_saturated:$server_saturated,
            exclude_reason:(
              if $rig_valid then null
              elif ($headroom|not) then "k6 client CPU \($c)% >= 85% of \($pin)% pin (client bottleneck)"
              else "server error_rate \($err) > \($err_eps)" end),
            # Event-log RESIDENCE for this rung: how long a body lingers in the
            # count-bounded ring before eviction = maxLogEntries / achieved_rps.
            # LENGTHENS as rps falls (long at low C). retained bytes ~ constant.
            retention_residence_s:(if $ach > 0 then (($maxlog / $ach) * 1000 | round) / 1000 else null end),
            # --- VU-pool diagnostics passed through from sweep.js.
            # vus_active_max is the peak CONCURRENT VUs this rung used —
            # if it is far below the pool of the rung while dropped_iterations>0, the pool was
            # NOT the constraint (VUs idle) and the drops need another explanation; a max
            # ABOVE the preAllocatedVUs pool is the only per-rung proof the pool grew.
            # (Whole-run pool growth is in the top-level .vus_diagnostics block, not
            # here — k6 shares initialised VUs across rungs whose reservations overlap,
            # so growth cannot be attributed to one rung.) stall_time_buckets shows WHEN in the rung
            # deep-tail requests fell — clustered => transient stall (the standing
            # hypothesis), uniform => steady limit — a proxy for drop timing (a
            # dropped iteration never runs code, so drops cannot be timestamped).
            vus_active_max:.vus_active_max, vus_active_p95:.vus_active_p95, vus_active_avg:.vus_active_avg,
            stalls:.stalls, stalls_post_settle:.stalls_post_settle, stall_ms_threshold:.stall_ms_threshold,
            stall_concurrency_max:.stall_concurrency_max, stall_concurrency_avg:.stall_concurrency_avg,
            stall_time_buckets:.stall_time_buckets
          } ] as $rungs
    | ([ $rungs[] | select(.rig_valid) | .achieved_rps ] | max // 0) as $peak
    | ($rungs | map(select(.rig_valid)) | sort_by(.achieved_rps) | last) as $peakrung
    # The rung the healthy_ceiling landed on (offered == hc_rps). Its OWN client
    # soundness decides whether HC can be trusted as a SERVER figure: the Finding-1
    # definition (reused verbatim) does NOT look at dropped iterations or client CPU,
    # so on a box where the load generator is co-resident the ceiling rung can be
    # client-contended. When that rung dropped iterations or ran the client near its
    # CPU pin, HC reflects the CLIENT limit as much as the server one — surfaced as
    # client_limited_at_ceiling, never silently trusted.
    | ($rungs | map(select(.offered_rps == $hc_rps)) | first) as $hcrung
    | {
        cores:$cores, server_cpus:null, k6_cpus:null, k6_cores:$k6cores,
        available_processors:null,
        # ceiling_rps mirrors the injector per-core shape (chart_inject_percore):
        # the HEALTHY ceiling is the headline serving figure.
        ceiling_rps:$hc_rps,
        healthy_ceiling_rps:$hc_rps,
        healthy_ceiling_p50_ms:$hc_p50,
        healthy_ceiling_p95_ms:$hc_p95,
        # client-soundness of the ceiling rung (see comment above).
        healthy_ceiling_rig_valid:($hcrung.rig_valid // null),
        healthy_ceiling_dropped_iterations:($hcrung.dropped_iterations // null),
        healthy_ceiling_client_cpu_pct:($hcrung.k6_cpu_pct // null),
        client_limited_at_ceiling:(if $hcrung == null then null else ($hcrung.rig_valid | not) end),
        rig_valid_peak_achieved_rps:$peak,
        peak_offered_rps:($peakrung.offered_rps // null),
        rps_per_core:(if $hc_rps == null then null else (($hc_rps / $cores) * 100 | round) / 100 end),
        peak_per_core:(($peak / $cores) * 100 | round) / 100,
        # --- SUT-side CPU: the evidence that attributes where the peak was bound ---
        sut_pin_pct:$sutpin,
        sut_cpu_peak_pct:([ $rungs[] | .sut_cpu_pct | select(. != null) ] | max // null),
        # SUT CPU at the peak-achieved rung, and its fraction of the C-core pin
        # (null when that rung window caught no sample — a sparse/last-rung window).
        sut_cpu_at_peak_pct:($peakrung.sut_cpu_pct // null),
        sut_cpu_frac_of_pin_at_peak:($peakrung.sut_cpu_frac_of_pin // null),
        # Attribution of the ceiling, from the MAX SUT CPU observed across ALL rungs
        # (well-sampled — robust to a single sparse window, unlike the peak rung
        # lone sample). Threshold 0.85 of the C-core pin mirrors the client-headroom
        # test: "server" = the SUT reached ~its pin on at least one rung (it CAN be
        # the ceiling, a real per-core figure); "load_path_or_virtualization" = the
        # SUT NEVER approached its pin on ANY rung while throughput plateaued/fell, so
        # the server had spare CPU throughout and the limit is the load path (k6 + the
        # Docker VM network), NOT MockServer; null when SUT CPU was not sampled at all.
        peak_limited_by:(
          ([ $rungs[] | .sut_cpu_frac_of_pin | select(. != null) ] | max) as $fmax
          | if $fmax == null then null
            elif $fmax >= 0.85 then "server"
            else "load_path_or_virtualization" end),
        retention:{
          assumed_max_log_entries:$maxlog,
          body_bytes:$body,
          retained_bytes_estimate:($maxlog * $body),
          note:"count-bounded ring: retained bytes ~ maxLogEntries*body (≈constant vs rate); residence = maxLogEntries/achieved_rps lengthens as rps falls (see .ladder[].retention_residence_s)"
        },
        # VU-pool diagnostics for this C, straight from sweep.js. Records
        # the pool CONFIG the sweep ran with (preallocated_vus/max_vus) plus the k6
        # whole-run VU gauges (vus_concurrent_overall_max, vus_initialized_global_max,
        # vus_initialized_baseline and the vus_pool_grew bottom line)
        # so actual pool growth for this C is legible without drilling into the
        # per-rung ladder. Null on an old sweep artifact that predates the fields.
        vus_diagnostics:($sweep[0].vus_diagnostics // null),
        client_pin_pct:$pin,
        ladder:$rungs,
        excluded:[ $rungs[] | select(.rig_valid|not) | {offered_rps, achieved_rps, k6_cpu_pct, dropped_iterations, error_rate, reason:.exclude_reason} ]
      }')"

  # stitch in the shell-known cpusets, the proven processor count, and warm-up.
  AGG="$(jq -c \
    --arg scpu "$SCPU" --arg kcpu "$KCPU" --argjson avail "$AVAIL" --argjson placement "${POINT_PLACEMENT:-null}" \
    --argjson wup_p50 "${WARMUP_P50:-null}" --argjson wup_ach "${WARMUP_ACH:-null}" '
    .server_cpus=$scpu | .k6_cpus=$kcpu | .available_processors=$avail | .placement=$placement
    | (.ladder[0].p50_ms) as $r1
    | (.ladder[1].p50_ms // null) as $r2
    | .warmup={
        drive_rate:'"$POINT_WARMUP_RATE"', drive_p50_ms:$wup_p50, drive_achieved_rps:$wup_ach,
        # first vs second measured rung p50: if the FIRST rung is systematically
        # slower than the second AFTER the warm-up, the transient was not fully
        # removed — surfaced, not averaged away (warm-up is bias, not noise).
        first_rung_p50_ms:$r1, second_rung_p50_ms:$r2,
        first_rung_slower_than_second:(($r1 != null) and ($r2 != null) and ($r1 > $r2))
      }' <<<"$AGG")"
  sample_peaks
  fi # ---- end single-k6 client ----
  finish_point || continue
done
if [ "$PLACEMENT_ONLY" = true ]; then # test hook: the per-point placement and the fixed client layout; nothing started
  printf '%s\n' "${POINTS[@]+"${POINTS[@]}"}" "${SKIPPED[@]+"${SKIPPED[@]}"}" | sed '/^$/d' \
    | jq -sc --arg k6sets "$HW_K6_SPEC" --arg prom "$HW_PROM_CPUS" --arg layout "$HW_LAYOUT" --arg mode "$MODE" '
      {mode:$mode, layout:$layout, k6_cpusets:($k6sets | split(";") | map(select(. != ""))), prometheus_cpus:$prom,
       points:map(select(.reason == null)), skipped:map(select(.reason != null))}'
  exit 0
fi

# --- assemble the serving_percore / serving_hw_matrix block --------------------
# Points and skips go to jq as FILES: together they easily exceed the kernel's per-argument limit
# (128 KiB on Linux), which an --argjson would hit as "Argument list too long". A live matrix keeps
# them in its work dir, so a failed assembly can be re-assembled offline.
ASM_DIR="$WORK"
if [ "$MODE" = hw_matrix ] && [ -z "$REASSEMBLE_DIR" ] && [ -n "${PERF_HW_MATRIX_DEBUG_DIR:-}" ]; then
  ASM_DIR="$PERF_HW_MATRIX_DEBUG_DIR"
fi
printf '%s\n' "${POINTS[@]+"${POINTS[@]}"}" > "$ASM_DIR/points.ndjson"
printf '%s\n' "${SKIPPED[@]+"${SKIPPED[@]}"}" > "$ASM_DIR/skipped.ndjson"
SKIPPED_FILE="$ASM_DIR/skipped.ndjson"
[ "${PERF_HW_MATRIX_TEST_FAULT:-}" = assembly ] && SKIPPED_FILE="$ASM_DIR/skipped.ndjson.missing"
REQUESTED_JSON="$(for i in "${!P_CORES[@]}"; do
  jq -nc --argjson c "${P_CORES[$i]}" --arg m "${P_MEM[$i]}" --argjson ctl "${P_CONTROL[$i]}" \
    '{cores:$c, memory_limit:$m, control:$ctl}'; done | jq -sc '.')"

ASM_OUT="$WORK/assembled.json"; ASM_ERR="$WORK/assembly.err"
asm_rc=0
jq -nc \
  --slurpfile points_in "$ASM_DIR/points.ndjson" \
  --slurpfile skipped_in "$SKIPPED_FILE" \
  --argjson requested "$REQUESTED_JSON" \
  --argjson host_cores "$HOST_CORES" \
  --argjson host_phys "$HOST_PHYS_CORES" \
  --argjson max_measured "$MAX_MEASURED" \
  --arg mode "$MODE" \
  --arg matrix "$HW_MATRIX_SPEC" \
  --argjson per_core_cap "$HW_MAX_RPS_PER_CORE" \
  --argjson k6_reserve "$K6_RESERVE" \
  --argjson rig_paused "$([ "${PERF_HW_MATRIX_RIG_PAUSED:-false}" = true ] && echo true || echo false)" \
  --arg ladder "$CORE_LADDER" \
  --arg rates "$([ "$HW_CLIENT" = multik6 ] || echo "$SWEEP_RATES")" \
  --arg client "$HW_CLIENT" --arg k6sets "$HW_K6_SPEC" --arg promcpus "$HW_PROM_CPUS" \
  --arg layout "$HW_LAYOUT" --arg k6node "$K6_NUMA_NODE" \
  --argjson k6phys "$K6_PHYS_CORES" --arg anchors "$HW_LADDER_ANCHORS" --arg reassembled "${REASSEMBLE_DIR:+$(basename "$REASSEMBLE_DIR")}" \
  --arg ref "$HW_RPS_PER_CORE_REF" --arg lo "$HW_LADDER_LO" --arg hi "$HW_LADDER_HI" --arg rungs "$HW_LADDER_RUNGS" \
  --arg explicit_rates "${PERF_HW_MATRIX_SWEEP_RATES:-}" \
  --arg step "$SWEEP_STEP" --arg gap "$SWEEP_GAP" --argjson settle "$SWEEP_SETTLE_S" \
  --arg imgjto "$SUT_IMAGE_JTO" --arg sutopts "$HW_SUT_JAVA_OPTS" --arg jto "$SUT_JTO" --arg gclog "$HW_GC_LOG" '
  def nonempty: if . == "" then null else . end;
  ($points_in | map(select(type == "object"))) as $points
  | ($skipped_in | map(select(type == "object"))) as $skipped
  | {
    attempted:true,
    mode:$mode,
    proto:"http",
    host_cores:$host_cores,
    host_physical_cores:$host_phys,
    cores_requested:($requested | map(.cores)),
    max_cores_measured:$max_measured,
    # latency_settle_s: the methodology fingerprint perf-test-compare.sh keys the
    # p50-derived healthy-ceiling metrics on.
    sweep:{rates:$rates, step:$step, gap:$gap, latency_settle_s:$settle},
    # false for PERF_K6_NUMA_NODE=same (the same-socket interference A/B); per point in .points[].placement.
    baseline_eligible:($k6node != "same"),
    healthy_ceiling_definition:"lib/perf-website-figures.jq headline (Finding 1: highest rung achieved>=0.95*offered, zero errors, p50<=3x flat-region p50, below the first rung that fails) — reused, not re-implemented",
    skipped:$skipped
  }
  + if $mode == "hw_matrix" then {
      matrix:$matrix,
      matrix_requested:$requested,
      client:$client
    }
    + (if $client == "multik6" then {
        # Every point: the same N k6 processes and Prometheus on fixed cpusets; its own rates in .points[].sweep_rates.
        client_placement:{k6_cpusets:($k6sets | split(";")), k6_physical_cores:$k6phys, prometheus_cpus:$promcpus,
                          layout:$layout, k6_numa_node:$k6node},
        ladder:(if $explicit_rates != "" then {explicit:$explicit_rates}
                else {rps_per_core_ref:($ref | tonumber), lo:($lo | tonumber), hi:($hi | tonumber), rungs:($rungs | tonumber),
                      anchors:($anchors | split(",") | map(tonumber)),
                      rule:"anchors x cores x rps_per_core_ref (at least 1000), then geometric from lo to hi x cores x rps_per_core_ref, rounded to 100"} end)
      } else {
        # each point offers only the rates up to this many rps per core (0 = the full ladder).
        sweep_max_rps_per_core:$per_core_cap,
        k6_reserve_cores:$k6_reserve
      } end)
    + {
      # true only when the caller (perf-test-run.sh) paused every other container on the
      # rig, so nothing else could run on a point cores during its sweep.
      other_containers_paused:$rig_paused,
      server_config:("shipped image defaults (no -Xmx; heap from MaxRAMPercentage of the memory limit) with MOCKSERVER_LOG_LEVEL=ERROR and MOCKSERVER_DISABLE_SYSTEM_OUT=true"
                     + (if $sutopts != "" then "; plus PERF_HW_MATRIX_SUT_JAVA_OPTS (sut_jvm)" else "" end)),
      # JAVA_TOOL_OPTIONS = the image default + extra opts + the GC log (<point dir>/jvm/gc-<pid>.log).
      sut_jvm:{image_java_tool_options:($imgjto | nonempty), extra_java_opts:($sutopts | nonempty),
               gc_log:($gclog == "true"), java_tool_options:($jto | nonempty)},
      log_level:"ERROR",
      points:($points | sort_by(.cores, .memory_limit_bytes))
    } else {
      # explicit, un-skimmable statement of where the curve ends and why.
      curve_complete_to_16:(($points | map(.cores) | max // 0) >= 16),
      points:($points | sort_by(.cores))
    } end
  + (if $reassembled != "" then {reassembled_from:$reassembled} else {} end)' > "$ASM_OUT" 2> "$ASM_ERR" || asm_rc=$?

# A failed assembly must never read as an empty matrix: say so, name the cause, keep the counts.
if [ "$asm_rc" -ne 0 ] || ! jq -e '(.points | type) == "array" and (.skipped | type) == "array"' "$ASM_OUT" >/dev/null 2>&1; then
  ASM_DETAIL="$(head -c 600 "$ASM_ERR" 2>/dev/null | tr '\n' ' ')"
  ASM_DETAIL="jq exit ${asm_rc}: ${ASM_DETAIL:-no error output}"
  echo ":x: ERROR: assembling serving_${MODE} from ${#POINTS[@]} point(s) and ${#SKIPPED[@]} skip(s) FAILED — ${ASM_DETAIL}" >&2
  [ "$ASM_DIR" = "$WORK" ] || echo "    the points and skips are kept in $ASM_DIR (points.ndjson, skipped.ndjson); re-assemble with PERF_HW_MATRIX_REASSEMBLE_DIR" >&2
  jq -nc --arg mode "$MODE" --arg detail "$ASM_DETAIL" --argjson np "${#POINTS[@]}" --argjson ns "${#SKIPPED[@]}" '
    {attempted:true, mode:$mode, error:"assembly_failed",
     error_detail:("assembling the block from \($np) point(s) and \($ns) skip(s) failed: " + $detail),
     points_lost:$np, skips_lost:$ns, points:[], skipped:[]}' > "$OUT_FILE"
  exit 1
fi
cat "$ASM_OUT" > "$OUT_FILE"

echo "--- serving_${MODE} points=$(jq -r '.points | length' "$ASM_OUT") skipped=$(jq -r '.skipped | length' "$ASM_OUT") max_cores_measured=$(jq -r '.max_cores_measured' "$ASM_OUT")" >&2
