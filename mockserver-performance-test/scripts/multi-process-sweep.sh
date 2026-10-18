#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# multi-process-sweep.sh — a load generator that can SATURATE the server, to answer:
# "why does a single k6 process drop iterations with three quarters of its VU
# pool unused, and is the flat ~6,000 rps healthy ceiling a CLIENT limit or a
# SERVER limit?"
#
# WHY THIS EXISTS (the evidence it is built to act on).
#   * The per-core serving curve (perf-percore.sh) is flat at ~6,000 rps for
#     C = 1,2,4,8 and NEITHER side is CPU-bound: the SUT draws ~one core's worth
#     at every C and the single k6 used ~2 of its 7-14 disjoint cores.
#   * A native re-run (M3 Max) killed the two obvious server-side explanations:
#     the server reached 30,704 rps at 317% CPU when driven hard (NOT
#     single-threaded-limited), containerisation costs ~10% not 5x, and
#     disabling the event log changed peak throughput not at all.
#   * Yet the single k6's HEALTHY (no-drops) ceiling was 4,000-8,000 rps — the
#     same order as CI's 6,000 — while the server was nowhere near saturated and
#     k6's VU pool was 3/4 idle (vus_concurrent_overall_max 669 of 4,000).
#
# A flat curve with neither side CPU-bound is the shape of a COORDINATION /
# SERIALISATION limit, not a capacity limit. The single most likely locus, on
# that evidence, is INSIDE one k6 process (its arrival-rate executor + metrics
# pipeline + Go GC), NOT the server. This harness tests that directly by
# splitting the offered load across N INDEPENDENT k6 processes pinned to
# disjoint cores and asking whether the AGGREGATE clean ceiling rises with N.
#
# WHAT IT MEASURES (and how it discriminates client-limit from server-limit).
# For each process count N in $PERF_MULTI_PROCS (default 1,2,4) it drives the
# SAME aggregate offered-rate ladder ($PERF_MULTI_AGG_RATES), split N ways so
# each process offers agg/N, all N running in the SAME wall-clock window, and
# records PER PROCESS and in AGGREGATE:
#   * offered vs achieved, per process AND aggregated (Sum, never double-counted
#     — each process offers a disjoint share of the arrival rate);
#   * dropped_iterations per process and summed — the client-shortfall signal;
#   * client CPU PER PROCESS and the SUT CPU, both sampled from one docker-stats
#     call, so "neither side is CPU-bound" is RE-TESTED at the new load rather
#     than asserted (an instrument that never records client CPU can be measuring
#     the wrong subject);
#   * a per-N `limited_by` verdict AND a top-level `scaling` verdict.
#
# THE DISCRIMINATOR (the whole point — the old instrument could not tell you
# WHY it stopped). A flat single-process curve cannot separate "client cannot
# offer more" from "server cannot serve more". This one can:
#   * aggregate clean ceiling RISES ~linearly with N, SUT CPU climbing toward
#     its pin  => the ceiling was a PER-PROCESS CLIENT limit; more processes
#     help; report the new, higher server-side ceiling.
#   * aggregate clean ceiling FLAT regardless of N, per-process achieved FALLING
#     as ~agg/N, BOTH sides with CPU headroom => a SHARED-PATH limit (loopback /
#     veth / the SUT accept path / a server-internal serialisation) — more
#     client processes on THIS host will NOT help; you need multiple client
#     HOSTS or the limit is server-internal. The harness SAYS which, it does not
#     assume.
#   * any process pinned AT its client-CPU pin => that process was client-CPU
#     -bound; its rungs are excluded as server figures (client_cpu_at_pin).
#
# NOT A GATE. This harness only measures. It emits a self-describing JSON block
# (stdout, or the file named by $1). CI wiring (an env-gated call + a NON-gating
# compare key) is described in the script header comment `CI WIRING` below and
# is intentionally NOT done here — perf-test-run.sh / perf-test-compare.sh /
# perf-budgets.json are owned by a concurrent change.
#
# RUN IT LOCALLY:
#   mockserver-performance-test/scripts/multi-process-sweep.sh /tmp/mp.json
#   PERF_MULTI_PROCS=1,2,4 PERF_MULTI_AGG_RATES=2000,4000,8000,16000,24000 \
#     PERF_MULTI_STEP=12s .../multi-process-sweep.sh /tmp/mp.json
#   # smoke (proves aggregation + discriminators; NOT a measurement):
#   PERF_MULTI_PROCS=2 PERF_MULTI_AGG_RATES=200,400 PERF_MULTI_STEP=4s \
#     PERF_MULTI_WARMUP_DURATION=2s .../multi-process-sweep.sh /tmp/smoke.json
#
# CI WIRING — DONE (faebd742f). This script is called from perf-test-run.sh behind
# the opt-in PERF_SERVING_MULTIPROC flag, merged into result.json under
# `.serving_multiproc` and uploaded as the `serving-multiproc.json` artifact;
# perf-test-compare.sh reads it NON-GATING with a `serving_multiproc_attempted`
# presence gate; and perf-budgets.json declares the two notify-only keys
# `serving_multiproc_aggregate_healthy_ceiling_rps` and
# `serving_multiproc_scales_with_procs`. Do not re-add that wiring.
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${PERF_MULTI_REPO_ROOT:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
# The ONE authoritative healthy-ceiling implementation. Reused, never
# re-implemented — a divergent third copy of the rule is what reuse prevents. Only
# READ here (never edited); path overridable so the harness is not wedded to a
# CI-tree layout.
FIGURES_JQ="${PERF_MULTI_FIGURES_JQ:-$REPO_ROOT/.buildkite/scripts/steps/lib/perf-website-figures.jq}"

# The SAME physical-core disjointness proof the main run uses (perf-test-run.sh's
# assert_cpusets_physically_disjoint), factored into a shared lib. This rig pins its
# SUT and N client blocks by LOGICAL cpu id; at higher process counts a client block
# can cross onto the hyperthread SIBLINGS of the server's cores, silently putting the
# load generator inside the SUT and reporting the result as a server ceiling — the
# exact defect that justified the perf-box resize. So before launching each N we PROVE
# the server and client cpusets occupy distinct PHYSICAL cores (resolved from /sys, not
# assumed from the enumeration) and FAIL THE RUN on overlap. Reused, never duplicated.
# Fail closed if the lib is absent — a rig without the proof must not run.
TOPOLOGY_LIB="${PERF_MULTI_TOPOLOGY_LIB:-$REPO_ROOT/.buildkite/scripts/steps/lib/perf-cpu-topology.sh}"
if [ -r "$TOPOLOGY_LIB" ]; then
  # shellcheck source=/dev/null
  . "$TOPOLOGY_LIB"
else
  echo ":x: CPU-topology guard lib not found at $TOPOLOGY_LIB — refusing to run a multi-process sweep without the physical-core disjointness proof" >&2
  exit 1
fi
SWEEP_WINDOW_LIB="${PERF_MULTI_SWEEP_WINDOW_LIB:-$REPO_ROOT/.buildkite/scripts/steps/lib/perf-sweep-window.sh}"
if [ -r "$SWEEP_WINDOW_LIB" ]; then
  # shellcheck source=/dev/null
  . "$SWEEP_WINDOW_LIB"
else
  echo ":x: sweep latency-window check lib not found at $SWEEP_WINDOW_LIB" >&2
  exit 1
fi

OUT_FILE="${1:-/dev/stdout}"

# --- inputs (all overridable) --------------------------------------------------
MOCKSERVER_IMAGE="${PERF_MULTI_IMAGE:-${MOCKSERVER_IMAGE:-mockserver/mockserver:mockserver-snapshot-graaljs}}"
K6_IMAGE="${PERF_MULTI_K6_IMAGE:-grafana/k6:1.7.1@sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f}"
K6_DIR="$REPO_ROOT/mockserver-performance-test/k6"

HOST_CORES="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 0)"

# The process-count ladder — the AXIS of the experiment. aggregate ceiling vs N.
PROCS_LADDER="${PERF_MULTI_PROCS:-1,2,4}"

# Core allocation. The SUT is pinned to a generous fixed block so it is NOT the
# artificial bottleneck (native data: ~3 cores of CPU carried 30k rps), and each
# client process gets its own DISJOINT block. A reserve is held back for the
# kernel/dockerd/sampler. Feasibility per N: SERVER + N*CLIENT_EACH + RESERVE must
# fit the host, else that N is SKIPPED with a reason (never silently dropped, and
# never run on shared cores — that would measure contention, not the server).
SERVER_CORES="${PERF_MULTI_SERVER_CORES:-4}"
CLIENT_CORES_EACH="${PERF_MULTI_CLIENT_CORES_EACH:-2}"
RESERVE_CORES="${PERF_MULTI_RESERVE_CORES:-1}"

# AGGREGATE offered-rate ladder (summed across processes). Each process offers
# agg/N. Comparable across N because the aggregate rungs stay fixed. Default spans
# below CI's 6k ceiling up past the ~27k the container path carried natively, so
# the ladder can actually REACH the limit rather than stop short of it.
AGG_RATES="${PERF_MULTI_AGG_RATES:-2000,4000,6000,8000,12000,16000,20000,24000,32000}"
SWEEP_STEP="${PERF_MULTI_STEP:-12s}"
SWEEP_GAP="${PERF_MULTI_GAP:-4s}"
# Per-process VU pool. Left to sweep.js's per-rung sizing by default (unset); a
# flat override applies the SAME pool to every rung of every process when set.
SWEEP_PRE_VUS="${PERF_MULTI_PRE_VUS:-}"
SWEEP_MAX_VUS="${PERF_MULTI_MAX_VUS:-}"
WARMUP_RATE="${PERF_MULTI_WARMUP_RATE:-500}"
WARMUP_DURATION="${PERF_MULTI_WARMUP_DURATION:-8s}"

SERVER_MEMORY="${PERF_MULTI_MEMORY:-1g}"

# Target override: when set, do NOT launch/pin a SUT — drive an EXTERNAL server
# (the "several client hosts" / real-box topology). SUT CPU is then only sampled
# if PERF_MULTI_SUT_CONTAINER names a co-visible container; otherwise it is null
# and the server-side attribution honestly reports "sut_cpu_unavailable".
TARGET_URL="${PERF_MULTI_TARGET_URL:-}"
SUT_CONTAINER="${PERF_MULTI_SUT_CONTAINER:-}"

SWEEP_ERR_EPS="${PERF_MULTI_ERROR_EPS:-0.01}"
SWEEP_SETTLE_S="${PERF_MULTI_SETTLE_S:-3}"
SWEEP_SAMPLE_INTERVAL="${PERF_MULTI_SAMPLE_INTERVAL:-2}"
MIN_TAIL_SAMPLES="${PERF_MULTI_MIN_TAIL_SAMPLES:-30}"
# achieved/offered floor for a "clean" aggregate rung — the same 0.95 keep the
# healthy-ceiling rule uses. CPU-headroom threshold (fraction of a side's pin) mirrors perf-percore.sh.
KEEP="${PERF_MULTI_KEEP:-0.95}"
CPU_HEADROOM_FRAC="${PERF_MULTI_CPU_HEADROOM_FRAC:-0.85}"
# Fraction of N above which "aggregate scales with processes" is declared. If the
# aggregate ceiling at max-N is >= SCALE_FACTOR x (N_max/N_min) x the ceiling at
# min-N, the limit was per-process/client; else it did not scale (shared/server).
SCALE_FACTOR="${PERF_MULTI_SCALE_FACTOR:-0.6}"

RUN_ID="${BUILDKITE_BUILD_ID:-local}-$$-multiproc"
NETWORK="mockserver-multiproc-${RUN_ID}"
SERVER="mockserver-multiproc-sut-${RUN_ID}"
K6_PREFIX="mockserver-multiproc-k6-${RUN_ID}"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/perf-multiproc.XXXXXX")"
chmod 0777 "$WORK"

# Names of every k6 container we may launch, so cleanup can remove them all.
ALL_K6_NAMES=""

cleanup() {
  docker rm -f "$SERVER" >/dev/null 2>&1 || true
  # shellcheck disable=SC2086
  [ -n "$ALL_K6_NAMES" ] && docker rm -f $ALL_K6_NAMES >/dev/null 2>&1 || true
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

# cpuset "a-b" (or "a" when width 1) from a start core and a width.
cpuset_range() { local start="$1" width="$2"; if [ "$width" -le 1 ]; then echo "$start"; else echo "$start-$((start+width-1))"; fi; }

echo "--- item 18 multi-process sweep: host_cores=$HOST_CORES procs=$PROCS_LADDER agg_rates=$AGG_RATES server_cores=$SERVER_CORES client_cores_each=$CLIENT_CORES_EACH image=$MOCKSERVER_IMAGE target=${TARGET_URL:-<launch SUT>}" >&2

if [ ! -f "$FIGURES_JQ" ]; then
  echo "WARNING: perf-website-figures.jq not found at $FIGURES_JQ — aggregate healthy_ceiling_rps will fall back to an inline copy of the SAME Finding-1 rule." >&2
fi

# --- healthy_ceiling for a synthetic sweep, via the ONE authoritative filter ---
# Feeds an offered/achieved/p50/error series to perf-website-figures.jq and reads
# .headline.healthy_ceiling_rps back. Falls back to an inline copy of the SAME
# Finding-1 rule ONLY if the filter file is absent (kept byte-identical in intent
# so a fallback run is not silently a different definition).
healthy_ceiling_of() { # points-json-file [ladder-json-file] -> prints "rps p50 p95" (space sep) or "null null null"
  # The optional ladder ({offered_rps, rig_valid, client_limited, error_rate} per rung) lets an excluded
  # rung that returned errors stop the climb, as it does in the filter.
  local pts_file="$1" lad_file="${2:-}" now_iso headline lad="null"
  now_iso="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  [ -z "$lad_file" ] || lad="$(jq -c . "$lad_file")"
  if [ -f "$FIGURES_JQ" ]; then
    local syn
    syn="$(jq -nc --slurpfile p "$pts_file" --argjson l "$lad" --arg ts "$now_iso" \
      '{schema_version:2, timestamp_utc:$ts, config:{}, agent:{}, sweep:{points:$p[0]}}
       + (if $l == null then {} else {saturation:{ladder:$l}} end)')"
    headline="$(jq --arg now "$now_iso" --argjson lat_mult 3 --argjson keep "$KEEP" --arg fix_date "2026-09-16" \
      -f "$FIGURES_JQ" <<<"$syn" 2>/dev/null | jq -c '.headline // {}')"
  else
    headline="$(jq -c --argjson keep "$KEEP" --argjson l "$lad" '
      (sort_by(.offered_rps)) as $s
      | ([ $s[0:4][] | .p50_ms ] | map(select(. != null)) | sort) as $flat
      | (($flat|length) as $n | if $n==0 then null elif ($n%2)==1 then $flat[($n/2|floor)] else (($flat[$n/2-1]+$flat[$n/2])/2) end) as $fp50
      | (if $fp50==null then null else $fp50*3 end) as $lt
      | def ok: .offered_rps>0 and .achieved_rps>=($keep*.offered_rps) and (.error_rate//0)==0 and $lt!=null and .p50_ms!=null and .p50_ms<=$lt;
        ([ ($s[] | select(ok | not)), (($l // [])[] | select(.rig_valid != true and .client_limited != true and ((.error_rate // 0) > 0))) ]
         | sort_by(.offered_rps) | first) as $bad
      | [ $s[] | select(ok and ($bad == null or .offered_rps < $bad.offered_rps)) ] as $h
      | ($h | last) as $c
      | {healthy_ceiling_rps:($c.offered_rps // null), healthy_ceiling_p50_ms:($c.p50_ms // null), healthy_ceiling_p95_ms:($c.p95_ms // null)}
    ' "$pts_file")"
  fi
  local rps p50 p95
  rps="$(jq -r '.healthy_ceiling_rps // "null"' <<<"$headline")"
  p50="$(jq -r '.healthy_ceiling_p50_ms // "null"' <<<"$headline")"
  p95="$(jq -r '.healthy_ceiling_p95_ms // "null"' <<<"$headline")"
  echo "$rps $p50 $p95"
}

# --- optional: launch + pin the SUT (skipped when TARGET_URL drives an external
# server). One SUT is (re)started fresh per N so no warm-state carries between
# process counts. --------------------------------------------------------------
start_sut() { # server_cpuset
  local scpu="$1"
  docker rm -f "$SERVER" >/dev/null 2>&1 || true
  docker run -d --rm --name "$SERVER" --network "$NETWORK" --network-alias mockserver \
    --cpuset-cpus="$scpu" --memory="$SERVER_MEMORY" -p 127.0.0.1::1080 \
    -e MOCKSERVER_LOG_LEVEL=ERROR -e MOCKSERVER_DISABLE_SYSTEM_OUT=true \
    -e MOCKSERVER_METRICS_ENABLED=true \
    "$MOCKSERVER_IMAGE" -serverPort 1080 >/dev/null
}

wait_ready() { # base_url_for_curl
  local url="$1" code
  for _ in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 -X PUT "${url}/mockserver/status" 2>/dev/null || echo 000)"
    [ "$code" = "200" ] && return 0
    sleep 2
  done
  return 1
}

if [ -z "$TARGET_URL" ]; then
  docker network create "$NETWORK" >/dev/null
fi

IFS=',' read -ra PROCS_ARR <<< "$PROCS_LADDER"
IFS=',' read -ra AGG_ARR <<< "$AGG_RATES"

POINTS=()
SKIPPED=()

for N in "${PROCS_ARR[@]}"; do
  # --- feasibility of pinning N disjoint client blocks (+ server + reserve) -----
  need=$(( SERVER_CORES + N * CLIENT_CORES_EACH + RESERVE_CORES ))
  if [ -z "$TARGET_URL" ] && [ "$need" -gt "$HOST_CORES" ]; then
    reason="needs ${SERVER_CORES} SUT + ${N}x${CLIENT_CORES_EACH} client + ${RESERVE_CORES} reserve = ${need} cores; host has ${HOST_CORES}"
    echo "--- N=$N SKIPPED: $reason" >&2
    SKIPPED+=("$(jq -nc --argjson n "$N" --arg r "$reason" '{procs:$n, reason:$r, type:"infeasible"}')")
    continue
  fi
  if [ -n "$TARGET_URL" ]; then
    # external target: still require disjoint client blocks + reserve to fit.
    need_c=$(( N * CLIENT_CORES_EACH + RESERVE_CORES ))
    if [ "$need_c" -gt "$HOST_CORES" ]; then
      reason="external target: ${N}x${CLIENT_CORES_EACH} client + ${RESERVE_CORES} reserve = ${need_c} cores; host has ${HOST_CORES}"
      echo "--- N=$N SKIPPED: $reason" >&2
      SKIPPED+=("$(jq -nc --argjson n "$N" --arg r "$reason" '{procs:$n, reason:$r, type:"infeasible"}')")
      continue
    fi
  fi

  # --- per-process rate share: each process offers agg/N (integer). ------------
  PP_RATES=""
  for a in "${AGG_ARR[@]}"; do
    pp=$(( a / N ))
    [ "$pp" -lt 1 ] && pp=1
    PP_RATES="${PP_RATES:+$PP_RATES,}$pp"
  done
  IFS=',' read -ra PP_ARR <<< "$PP_RATES"

  # --- cpusets: server 0..S-1 (if launched), clients disjoint above it ---------
  if [ -z "$TARGET_URL" ]; then
    SCPU="$(cpuset_range 0 "$SERVER_CORES")"
    CLIENT_BASE="$SERVER_CORES"
    CURL_URL=""   # filled after SUT starts (host port)
    BASE_URL="http://mockserver:1080"
  else
    SCPU=""
    CLIENT_BASE=0
    CURL_URL="$TARGET_URL"
    BASE_URL="$TARGET_URL"
  fi

  echo "+++ N=$N  server_cpus=${SCPU:-<external>}  per_process_rates=$PP_RATES  client_cores_each=$CLIENT_CORES_EACH" >&2

  # --- PROVE the cpusets are physically disjoint BEFORE spinning containers -----
  # The feasibility check above only proved the blocks fit in LOGICAL cpus; it says
  # nothing about hyperthread siblings. Here we hand the SUT (when launched) and every
  # client block to the shared /sys-resolving checker: if a client block lands on a
  # SIBLING of a server core, the load generator would contend with the SUT and the
  # throughput would measure the two fighting, not the server — so we FAIL THE RUN
  # rather than skip N (a silent skip would let the ladder look complete while missing
  # the very rungs the resize was meant to make measurable). Off-CI (no /sys) the
  # checker WARNS and proceeds; in CI (BUILDKITE=true) an unreadable topology is fatal.
  DISJOINT_PAIRS=()
  [ -z "$TARGET_URL" ] && DISJOINT_PAIRS+=(server "$SCPU")
  for ((i=0;i<N;i++)); do
    DISJOINT_PAIRS+=("client$i" "$(cpuset_range $(( CLIENT_BASE + i * CLIENT_CORES_EACH )) "$CLIENT_CORES_EACH")")
  done
  if ! cpusets_physically_disjoint "${DISJOINT_PAIRS[@]}"; then
    echo ":x: N=$N: the server and client cpusets are NOT physically disjoint (see above) — refusing to report a load generator contending with the server as a server figure" >&2
    exit 1
  fi

  # --- start (or confirm) the SUT ----------------------------------------------
  SUT_NAME_FOR_STATS="$SUT_CONTAINER"
  if [ -z "$TARGET_URL" ]; then
    start_sut "$SCPU"
    HOSTPORT="$(docker port "$SERVER" 1080/tcp 2>/dev/null | head -1)"
    HOSTPORT="${HOSTPORT:-127.0.0.1:1080}"
    CURL_URL="http://${HOSTPORT}"
    SUT_NAME_FOR_STATS="$SERVER"
    if ! wait_ready "$CURL_URL"; then
      echo "ERROR: SUT not ready at N=$N — skipping" >&2
      SKIPPED+=("$(jq -nc --argjson n "$N" --arg r "SUT did not become ready (PUT /mockserver/status != 200)" '{procs:$n, reason:$r, type:"failure"}')")
      docker rm -f "$SERVER" >/dev/null 2>&1 || true
      continue
    fi
  else
    if ! wait_ready "$CURL_URL"; then
      echo "ERROR: external target not ready at $CURL_URL — skipping N=$N" >&2
      SKIPPED+=("$(jq -nc --argjson n "$N" --arg r "external target not ready (PUT /mockserver/status != 200)" '{procs:$n, reason:$r, type:"failure"}')")
      continue
    fi
  fi

  # seed /simple + measure body size for context (does not perturb the window;
  # sweep.js re-seeds in setup()).
  curl -s --max-time 5 -X PUT "${CURL_URL}/mockserver/expectation" \
    -H 'Content-Type: application/json' \
    -d '[{"httpRequest":{"path":"/simple"},"httpResponse":{"statusCode":200,"body":"simple"},"times":{"unlimited":true}}]' \
    -o /dev/null 2>/dev/null || true
  BODY_BYTES="$(curl -s --max-time 5 "${CURL_URL}/simple" 2>/dev/null | wc -c | tr -d ' ')"
  BODY_BYTES="${BODY_BYTES:-6}"

  # --- warm-up drive (NEVER measured): one process on the first client cpuset --
  WCPU="$(cpuset_range "$CLIENT_BASE" "$CLIENT_CORES_EACH")"
  WNET=(); [ -z "$TARGET_URL" ] && WNET=(--network "$NETWORK")
  # ${arr[@]+...} guards the empty-array expansion so this is safe under `set -u`
  # on bash 3.2 (macOS) as well as bash 4+ (CI Linux).
  docker run --rm ${WNET[@]+"${WNET[@]}"} --cpuset-cpus="$WCPU" \
    -v "$K6_DIR:/k6:ro" -v "$WORK:/out" \
    -e "BASE_URL=$BASE_URL" -e "PROTO=http" \
    -e "K6_SWEEP_RATES=$WARMUP_RATE" -e "K6_SWEEP_STEP=$WARMUP_DURATION" -e "K6_SWEEP_GAP=1s" \
    -e "K6_SWEEP_SETTLE=0s" -e "K6_SWEEP_RESULT_PATH=/out/warmup-N${N}.json" \
    ${SWEEP_PRE_VUS:+-e "K6_SWEEP_PRE_VUS=$SWEEP_PRE_VUS"} ${SWEEP_MAX_VUS:+-e "K6_SWEEP_MAX_VUS=$SWEEP_MAX_VUS"} \
    "$K6_IMAGE" run /k6/sweep.js >/dev/null 2>&1 || true

  # --- CPU sampler: SUT + every k6 process, ONE docker-stats call per tick, long
  # format `ts name cpu` so a variable process count needs no column bookkeeping.
  # CRUCIAL: `docker stats --no-stream <names...>` FAILS THE WHOLE CALL (and prints
  # NOTHING) if ANY named container does not exist — and the k6 containers do not
  # exist yet on the sampler's first ticks, and an exited container vanishes
  # mid-run. So we snapshot ALL containers in one call (still one process spawn per
  # tick) and filter to our names in awk: a name that is not up yet simply yields
  # no row, instead of blanking the entire sample (which records null CPU on both
  # sides).
  CPU_LOG="$WORK/cpu-N${N}.csv"
  : > "$CPU_LOG"
  WANT_NAMES=""
  for ((i=0;i<N;i++)); do WANT_NAMES="${WANT_NAMES:+$WANT_NAMES }${K6_PREFIX}-N${N}-p${i}"; done
  [ -n "$SUT_NAME_FOR_STATS" ] && WANT_NAMES="${WANT_NAMES:+$WANT_NAMES }$SUT_NAME_FOR_STATS"
  ( while true; do
      # Stamped on return (performance-measurement.md, sweep.js).
      stats="$(docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' 2>/dev/null || true)"
      ts="$(date -u +%s)"
      awk -v t="$ts" -v want="$WANT_NAMES" '
            BEGIN{ split(want, a, " "); for(i in a) keep[a[i]]=1 }
            ($1 in keep){ gsub(/%/,"",$2); printf "%s %s %s\n", t, $1, $2 }' <<<"$stats" >> "$CPU_LOG"
      sleep "$SWEEP_SAMPLE_INTERVAL"
    done ) & SAMPLER_PID=$!

  # --- launch N k6 processes in parallel, each on its own cpuset, each writing
  # its own result JSON. Tight launch loop so all N scenarios start within a
  # second or two and their per-rung windows overlap (sweep.js staggers rungs
  # deterministically from RATES, so lockstep holds once started). ------------
  T0="$(date -u +%s)"
  PIDS=()
  for ((i=0;i<N;i++)); do
    kname="${K6_PREFIX}-N${N}-p${i}"
    ALL_K6_NAMES="$ALL_K6_NAMES $kname"
    ccpu="$(cpuset_range $(( CLIENT_BASE + i * CLIENT_CORES_EACH )) "$CLIENT_CORES_EACH")"
    KNET=(); [ -z "$TARGET_URL" ] && KNET=(--network "$NETWORK")
    docker run --rm --name "$kname" ${KNET[@]+"${KNET[@]}"} --cpuset-cpus="$ccpu" \
      -v "$K6_DIR:/k6:ro" -v "$WORK:/out" \
      -e "BASE_URL=$BASE_URL" -e "PROTO=http" \
      -e "K6_SWEEP_RATES=$PP_RATES" -e "K6_SWEEP_STEP=$SWEEP_STEP" -e "K6_SWEEP_GAP=$SWEEP_GAP" \
      -e "K6_SWEEP_SETTLE=${SWEEP_SETTLE_S}s" -e "K6_SWEEP_RESULT_PATH=/out/sweep-N${N}-p${i}.json" \
      ${SWEEP_PRE_VUS:+-e "K6_SWEEP_PRE_VUS=$SWEEP_PRE_VUS"} ${SWEEP_MAX_VUS:+-e "K6_SWEEP_MAX_VUS=$SWEEP_MAX_VUS"} \
      "$K6_IMAGE" run --quiet /k6/sweep.js >"$WORK/k6-N${N}-p${i}.log" 2>&1 &
    PIDS+=("$!")
  done
  # wait for every process; a non-zero exit is tolerated (sweep has no aborting
  # thresholds, but a drop-heavy top rung can still return non-zero) — the JSON
  # each wrote is what we read.
  for pid in "${PIDS[@]}"; do wait "$pid" || true; done
  # kill + reap the sampler quietly (bash prints a "Terminated" job notice otherwise).
  kill "$SAMPLER_PID" >/dev/null 2>&1 || true
  wait "$SAMPLER_PID" 2>/dev/null || true

  [ -z "$TARGET_URL" ] && docker rm -f "$SERVER" >/dev/null 2>&1 || true

  # --- collect the per-process sweep files -------------------------------------
  PROC_FILES=()
  missing=0
  for ((i=0;i<N;i++)); do
    f="$WORK/sweep-N${N}-p${i}.json"
    if jq -e '.points | length > 0' "$f" >/dev/null 2>&1; then
      PROC_FILES+=("$f")
    else
      echo "WARNING: process p${i} produced no points at N=$N (see $WORK/k6-N${N}-p${i}.log)" >&2
      missing=$((missing+1))
    fi
  done
  if [ "${#PROC_FILES[@]}" -eq 0 ]; then
    SKIPPED+=("$(jq -nc --argjson n "$N" --arg r "no process produced points" '{procs:$n, reason:$r, type:"failure"}')")
    continue
  fi
  # One process whose windows do not balance corrupts the aggregate, so the whole N fails.
  WINDOW_MISMATCH=""
  for f in "${PROC_FILES[@]}"; do
    wm="$(sweep_window_mismatches "$f")" || WINDOW_MISMATCH="${WINDOW_MISMATCH:+$WINDOW_MISMATCH; }$(basename "$f") $wm"
  done
  if [ -n "$WINDOW_MISMATCH" ]; then
    echo "ERROR: sweep latency windows inconsistent at N=$N — $WINDOW_MISMATCH" >&2
    SKIPPED+=("$(jq -nc --argjson n "$N" --arg r "latency windows inconsistent: $WINDOW_MISMATCH" '{procs:$n, reason:$r, type:"failure"}')")
    continue
  fi

  # --- per-process CPU (overall max + fraction of that process's core pin) -----
  CLIENT_PIN_PCT=$(( CLIENT_CORES_EACH * 100 ))
  SUT_PIN_PCT=$(( SERVER_CORES * 100 ))
  [ -n "$TARGET_URL" ] && SUT_PIN_PCT=0   # external: pin unknown

  # Build per-process CPU maps: overall max per process container, and per-rung
  # windowed max (window k = [T0 + k*(step+gap)+settle , T0 + k*(step+gap)+step]).
  # Long-format CSV: `ts name cpu`.
  proc_cpu_json() { # container-name -> {overall_max, per_rung:{aggrate:max}}
    local name="$1"
    local overall
    overall="$(awk -v n="$name" '$2==n && $3!="" {c=$3+0; if(c>m){m=c; s=1}} END{ if(s) printf "%.1f", m }' "$CPU_LOG")"
    local rungmap="{}"
    for k in "${!AGG_ARR[@]}"; do
      local a="${AGG_ARR[$k]}"
      local ws=$(( T0 + k * (STEP_S + GAP_S) + SWEEP_SETTLE_S ))
      local we=$(( T0 + k * (STEP_S + GAP_S) + STEP_S ))
      local mx
      mx="$(awk -v n="$name" -v a="$ws" -v b="$we" '$2==n && $1>=a && $1<b && $3!="" {c=$3+0; if(c>m){m=c; s=1}} END{ if(s) printf "%.1f", m }' "$CPU_LOG")"
      rungmap="$(jq -c --arg key "$a" --argjson v "${mx:-null}" '. + {($key): $v}' <<<"$rungmap")"
    done
    jq -nc --argjson om "${overall:-null}" --argjson rm "$rungmap" '{overall_max_cpu_pct:$om, per_rung_cpu_pct:$rm}'
  }

  # per-process objects (index, cpuset, cpu, per-process healthy ceiling)
  PROC_OBJS=()
  for ((i=0;i<N;i++)); do
    f="$WORK/sweep-N${N}-p${i}.json"
    jq -e '.points | length > 0' "$f" >/dev/null 2>&1 || continue
    ccpu="$(cpuset_range $(( CLIENT_BASE + i * CLIENT_CORES_EACH )) "$CLIENT_CORES_EACH")"
    kname="${K6_PREFIX}-N${N}-p${i}"
    cpuobj="$(proc_cpu_json "$kname")"
    # per-process healthy ceiling via the authoritative rule (its OWN offered scale)
    read -r pp_hc pp_p50 pp_p95 <<<"$(healthy_ceiling_of "$f" 2>/dev/null || echo 'null null null')"
    PROC_OBJS+=("$(jq -nc \
      --argjson idx "$i" --arg cpus "$ccpu" --argjson pin "$CLIENT_PIN_PCT" \
      --argjson cpu "$cpuobj" \
      --argjson hc "${pp_hc:-null}" --argjson hp50 "${pp_p50:-null}" --argjson hp95 "${pp_p95:-null}" \
      --slurpfile s "$f" '
      ($cpu.overall_max_cpu_pct) as $om
      | {
          index:$idx, client_cpus:$cpus, client_pin_pct:$pin,
          client_cpu_peak_pct:$om,
          client_cpu_frac_of_pin:(if ($om==null or $pin<=0) then null else (($om/$pin)*1000|round)/1000 end),
          client_at_cpu_pin:(if $om==null then null else ($om >= ('"$CPU_HEADROOM_FRAC"' * $pin)) end),
          per_rung_cpu_pct:$cpu.per_rung_cpu_pct,
          healthy_ceiling_rps:$hc, healthy_ceiling_p50_ms:$hp50, healthy_ceiling_p95_ms:$hp95,
          vus_diagnostics:($s[0].vus_diagnostics // null),
          points:$s[0].points
        }')")
  done
  PROC_OBJS_JSON="$(printf '%s\n' "${PROC_OBJS[@]}" | jq -sc '.')"

  # --- SUT CPU (overall + per aggregate-rung) ----------------------------------
  if [ -n "$SUT_NAME_FOR_STATS" ]; then
    SUT_CPU_OBJ="$(proc_cpu_json "$SUT_NAME_FOR_STATS")"
  else
    SUT_CPU_OBJ='{"overall_max_cpu_pct":null,"per_rung_cpu_pct":{}}'
  fi

  # --- AGGREGATE the ladder rung-by-rung across processes (NO double-counting):
  # aggregate offered = sum of per-process offered; aggregate achieved = sum of
  # per-process achieved; drops summed. Latency percentiles CANNOT be merged
  # across processes without the raw distribution, so the aggregate reports the
  # WORST-case p50/p95/p99 across processes (max), explicitly labelled, alongside
  # the per-process values. Client-sound iff EVERY contributing process had CPU
  # headroom and low error at that rung. ---------------------------------------
  AGG="$(jq -nc \
    --slurpfile procs <(printf '%s\n' "${PROC_OBJS[@]}") \
    --argjson aggrates "$(jq -nc --arg r "$AGG_RATES" '$r|split(",")|map(tonumber)')" \
    --argjson n "$N" \
    --argjson err_eps "$SWEEP_ERR_EPS" \
    --argjson min_tail "$MIN_TAIL_SAMPLES" \
    --argjson keep "$KEEP" \
    --argjson client_pin "$CLIENT_PIN_PCT" \
    --argjson headroom_frac "$CPU_HEADROOM_FRAC" \
    --argjson sutcpu "$SUT_CPU_OBJ" \
    --argjson sutpin "$SUT_PIN_PCT" '
    ($procs) as $P
    | ($P | length) as $np
    | [ range(0; ($aggrates|length)) as $k
        | ($aggrates[$k]) as $aggrate
        | [ $P[] | .points[$k] ] as $rung
        | ([ $rung[] | .offered_rps // 0 ] | add) as $off
        | ([ $rung[] | .achieved_rps // 0 ] | add) as $ach
        | ([ $rung[] | .dropped_iterations // 0 ] | add) as $drops
        | ([ $rung[] | .sample_count // 0 ] | add) as $samples
        | ([ $rung[] | .measured_sample_count // .sample_count // 0 ] | add) as $tail_samples
        | ([ $rung[] | .error_rate // 0 ] | max) as $err_max
        | ([ $rung[] | .p50_ms // 0 ] | max) as $p50_max
        | ([ $rung[] | .p95_ms // empty ] | max) as $p95_max
        | ([ $rung[] | .p99_ms // empty ] | max) as $p99_max
        # per-process client CPU at this rung, and whether ANY process was at pin.
        | [ $P[] | .per_rung_cpu_pct[($aggrate|tostring)] ] as $ccpu
        | ([ $ccpu[] | select(. != null) ] | max) as $ccpu_max
        | (any($ccpu[]; . != null and . >= ($headroom_frac * $client_pin))) as $any_client_at_pin
        | ($sutcpu.per_rung_cpu_pct[($aggrate|tostring)]) as $sc
        | ($err_max <= $err_eps) as $low_err
        # A rung is CLIENT-SOUND (trustworthy as an aggregate SERVER figure) iff no
        # contributing process was at its CPU pin and errors were low. Dropped
        # iterations do NOT by themselves invalidate it: with client CPU headroom,
        # drops mean the SERVER (or the shared path) could not keep up — that IS
        # the signal, not a per-process client limit.
        | ((($any_client_at_pin | not)) and $low_err) as $client_sound
        | ($tail_samples >= $min_tail) as $enough_tail
        | {
            nominal_agg_offered_rps:$aggrate,
            agg_offered_rps:$off,
            agg_achieved_rps:$ach,
            achieved_ratio:(if $off>0 then (($ach/$off)*1000|round)/1000 else null end),
            agg_dropped_iterations:$drops,
            agg_sample_count:$samples,
            error_rate_max:$err_max,
            # worst-case latency across processes (NOT a merged percentile).
            p50_ms_worst:$p50_max,
            p95_ms_worst:(if $enough_tail then $p95_max else null end),
            p99_ms_worst:(if $enough_tail then $p99_max else null end),
            per_process_offered_rps:[ $rung[] | .offered_rps ],
            per_process_achieved_rps:[ $rung[] | .achieved_rps ],
            per_process_dropped:[ $rung[] | .dropped_iterations // 0 ],
            client_cpu_pct_max:$ccpu_max,
            any_client_at_pin:$any_client_at_pin,
            sut_cpu_pct:$sc,
            sut_cpu_frac_of_pin:(if ($sc==null or $sutpin<=0) then null else (($sc/$sutpin)*1000|round)/1000 end),
            client_sound:$client_sound,
            server_saturated:($drops>0 and (($any_client_at_pin|not))),
            exclude_reason:(
              if $client_sound then null
              elif $any_client_at_pin then "a client process reached >=\($headroom_frac*100)% of its \($client_pin)% pin (client-CPU bottleneck)"
              else "aggregate error_rate \($err_max) > \($err_eps)" end)
          } ] as $ladder
    | {
        procs:$n,
        processes_contributing:$np,
        client_pin_pct:$client_pin,
        sut_pin_pct:$sutpin,
        sut_cpu_peak_pct:$sutcpu.overall_max_cpu_pct,
        sut_cpu_frac_of_pin_peak:(if ($sutcpu.overall_max_cpu_pct==null or $sutpin<=0) then null else (($sutcpu.overall_max_cpu_pct/$sutpin)*1000|round)/1000 end),
        client_cpu_peak_pct:([ $P[] | .client_cpu_peak_pct | select(. != null) ] | max),
        client_cpu_frac_of_pin_peak:([ $P[] | .client_cpu_frac_of_pin | select(. != null) ] | max),
        ladder:$ladder,
        excluded:[ $ladder[] | select(.client_sound|not) | {nominal_agg_offered_rps, agg_achieved_rps, client_cpu_pct_max, agg_dropped_iterations, error_rate_max, reason:.exclude_reason} ]
      }')"

  # --- aggregate healthy ceiling via the authoritative rule --------------------
  # Build a synthetic offered/achieved/p50/error series from the CLIENT-SOUND
  # aggregate rungs only, then read the Finding-1 headline back.
  AGG_PTS="$WORK/agg-pts-N${N}.json"
  jq -c '[ .ladder[] | select(.client_sound)
           | {offered_rps:.agg_offered_rps, achieved_rps:.agg_achieved_rps,
              p50_ms:.p50_ms_worst, p95_ms:.p95_ms_worst, p99_ms:.p99_ms_worst,
              error_rate:.error_rate_max, sample_count:.agg_sample_count,
              dropped_iterations:.agg_dropped_iterations} ]' <<<"$AGG" > "$AGG_PTS"
  jq -c '[ .ladder[] | {offered_rps:.agg_offered_rps, rig_valid:.client_sound, client_limited:.any_client_at_pin,
                        error_rate:.error_rate_max} ]' <<<"$AGG" > "$WORK/agg-ladder-N${N}.json"
  read -r AGG_HC AGG_HC_P50 AGG_HC_P95 <<<"$(healthy_ceiling_of "$AGG_PTS" "$WORK/agg-ladder-N${N}.json" 2>/dev/null || echo 'null null null')"

  # --- rig-valid aggregate peak (max client-sound achieved) + attribution ------
  AGG="$(jq -c \
    --argjson hc "${AGG_HC:-null}" --argjson hp50 "${AGG_HC_P50:-null}" --argjson hp95 "${AGG_HC_P95:-null}" \
    --argjson headroom_frac "$CPU_HEADROOM_FRAC" '
    ([ .ladder[] | select(.client_sound) | .agg_achieved_rps ] | max // 0) as $peak
    | (.ladder | map(select(.client_sound)) | sort_by(.agg_achieved_rps) | last) as $peakrung
    # attribution over ALL rungs: did the SUT ever approach its pin? did any
    # client ever reach its pin? did drops appear with BOTH sides in headroom?
    | ([ .ladder[] | .sut_cpu_frac_of_pin | select(. != null) ] | max) as $sut_fmax
    | (any(.ladder[]; .any_client_at_pin)) as $client_hit_pin
    | (any(.ladder[]; .agg_dropped_iterations > 0)) as $any_drops
    | (any(.ladder[]; .achieved_ratio != null and .achieved_ratio < '"$KEEP"')) as $any_shortfall
    | . + {
        aggregate_healthy_ceiling_rps:$hc,
        aggregate_healthy_ceiling_p50_ms:$hp50,
        aggregate_healthy_ceiling_p95_ms:$hp95,
        aggregate_rig_valid_peak_achieved_rps:$peak,
        aggregate_peak_offered_rps:($peakrung.nominal_agg_offered_rps // null),
        # WHY it stopped — the discriminator the old single-process instrument
        # could not compute. Reached the top of the ladder cleanly => the ladder
        # under-reached; raise PERF_MULTI_AGG_RATES. Drops/shortfall WITH the SUT
        # near its pin => server CPU. WITH a client at its pin => client CPU. WITH
        # both sides in headroom => a shared-path/coordination limit (loopback,
        # veth, accept path, or a server-internal serialisation) that more client
        # processes on THIS host cannot lift.
        limited_by:(
          if ($any_drops|not) and ($any_shortfall|not) then "not_saturated_raise_rates"
          elif $sut_fmax != null and $sut_fmax >= $headroom_frac then "server_cpu"
          elif $client_hit_pin then "client_cpu"
          else "shared_path_or_coordination" end),
        limited_by_evidence:{
          sut_cpu_frac_of_pin_max:$sut_fmax,
          any_client_at_pin:$client_hit_pin,
          any_drops:$any_drops,
          any_shortfall_below_keep:$any_shortfall
        }
      }' <<<"$AGG")"

  # stitch in per-process detail + config context
  AGG="$(jq -c --argjson procs "$PROC_OBJS_JSON" --arg pprates "$PP_RATES" \
    --argjson body "$BODY_BYTES" '
    . + {per_process:$procs, per_process_rates:($pprates|split(",")|map(tonumber)), body_bytes:$body}' <<<"$AGG")"

  echo "    N=$N  agg_healthy_ceiling=${AGG_HC} rps  peak=$(jq -r '.aggregate_rig_valid_peak_achieved_rps' <<<"$AGG")  sut_cpu_peak=$(jq -r '.sut_cpu_peak_pct' <<<"$AGG")%/$(jq -r '.sut_pin_pct' <<<"$AGG")%  client_cpu_peak=$(jq -r '.client_cpu_peak_pct' <<<"$AGG")%/$(jq -r '.client_pin_pct' <<<"$AGG")%  limited_by=$(jq -r '.limited_by' <<<"$AGG")" >&2
  POINTS+=("$AGG")
done

# --- scaling verdict across N: did the aggregate clean ceiling rise with N? ----
# min-N and max-N ceilings; a ceiling that scales ~linearly with N means the
# limit was PER-PROCESS (client-side) and multi-process helps; a flat ceiling
# means a SHARED-PATH or server limit that more processes on this host cannot
# lift. Uses aggregate_healthy_ceiling_rps, keyed off the process COUNT.
# Points and skips reach jq as FILES: with per-rung, per-process detail they outgrow the kernel's
# per-argument limit (128 KiB on Linux) at a handful of process counts.
printf '%s\n' "${POINTS[@]+"${POINTS[@]}"}" > "$WORK/points.ndjson"
printf '%s\n' "${SKIPPED[@]+"${SKIPPED[@]}"}" > "$WORK/skipped.ndjson"
# A failed assembly never reads as an empty profile: exit non-zero with an error block naming why.
assembly_failed() { # what, stderr file
  local detail
  detail="$(head -c 600 "$2" 2>/dev/null | tr '\n' ' ')"
  echo ":x: ERROR: multiproc: $1 failed from ${#POINTS[@]} point(s) — ${detail:-no error output}" >&2
  jq -nc --arg d "$1 failed: ${detail:-no error output}" --argjson np "${#POINTS[@]}" --argjson ns "${#SKIPPED[@]}" \
    '{attempted:true, error:"assembly_failed", error_detail:$d, points_lost:$np, skips_lost:$ns, points:[], skipped:[]}' > "$OUT_FILE"
  exit 1
}

SCALING="$(jq -nc --slurpfile points_in "$WORK/points.ndjson" --argjson factor "$SCALE_FACTOR" '
  ($points_in | map(select(type == "object")) | sort_by(.procs)) as $p
  | ($p | map(select(.aggregate_healthy_ceiling_rps != null))) as $valid
  | if ($valid | length) < 2 then
      {verdict:"insufficient_points", note:"need >=2 process counts with a valid aggregate ceiling to judge scaling"}
    else
      ($valid | first) as $lo
      | ($valid | last) as $hi
      | ($hi.procs / $lo.procs) as $proc_ratio
      | (if $lo.aggregate_healthy_ceiling_rps > 0 then ($hi.aggregate_healthy_ceiling_rps / $lo.aggregate_healthy_ceiling_rps) else null end) as $ceiling_ratio
      | {
          min_procs:$lo.procs, min_procs_ceiling_rps:$lo.aggregate_healthy_ceiling_rps,
          max_procs:$hi.procs, max_procs_ceiling_rps:$hi.aggregate_healthy_ceiling_rps,
          proc_ratio:$proc_ratio,
          ceiling_ratio:(if $ceiling_ratio==null then null else (($ceiling_ratio)*1000|round)/1000 end),
          # scales_with_procs: the aggregate ceiling grew by at least $factor of
          # the process-count ratio. TRUE => the flat single-process ceiling was a
          # PER-PROCESS CLIENT limit and multi-process is the right instrument.
          # FALSE => the ceiling is flat vs N: a shared-path/server limit that more
          # client processes on this host cannot lift (need multiple client HOSTS,
          # or the limit is server-internal). Read WITH per-N limited_by.
          scales_with_procs:(if $ceiling_ratio==null then null else ($ceiling_ratio >= ($factor * $proc_ratio)) end),
          scale_factor_threshold:$factor,
          interpretation:(
            if $ceiling_ratio==null then "cannot judge (min-N ceiling is 0/null)"
            elif ($ceiling_ratio >= ($factor * $proc_ratio)) then "aggregate ceiling scales with process count => the single-process ceiling was a per-process CLIENT limit; multi-process load generation lifts it"
            else "aggregate ceiling is flat vs process count => a SHARED-PATH or server limit; more client processes on this host will NOT help (need multiple client hosts, or the limit is server-internal). See per-N limited_by." end)
        }
    end' 2> "$WORK/scaling.err")" || assembly_failed "the scaling verdict" "$WORK/scaling.err"

jq -nc \
  --slurpfile points_in "$WORK/points.ndjson" \
  --slurpfile skipped_in "$WORK/skipped.ndjson" \
  --argjson scaling "$SCALING" \
  --argjson host_cores "$HOST_CORES" \
  --arg procs "$PROCS_LADDER" \
  --arg agg_rates "$AGG_RATES" \
  --arg step "$SWEEP_STEP" --arg gap "$SWEEP_GAP" --argjson settle "$SWEEP_SETTLE_S" \
  --arg target "${TARGET_URL:-launched}" \
  --argjson server_cores "$SERVER_CORES" \
  --argjson client_cores_each "$CLIENT_CORES_EACH" '
  ($points_in | map(select(type == "object"))) as $points
  | ($skipped_in | map(select(type == "object"))) as $skipped
  | {
    attempted:true,
    proto:"http",
    experiment:"multi-process aggregate throughput vs process count (item 18: is the ~6,000 rps ceiling a client or a server limit?)",
    host_cores:$host_cores,
    target:$target,
    server_cores:$server_cores,
    client_cores_each:$client_cores_each,
    procs_requested:($procs|split(",")|map(tonumber)),
    procs_measured:($points|map(.procs)),
    agg_rates:$agg_rates,
    sweep:{step:$step, gap:$gap, latency_settle_s:$settle},
    healthy_ceiling_definition:"lib/perf-website-figures.jq headline (Finding 1: highest client-sound rung achieved>=keep*offered, zero errors, p50<=3x flat-region p50, below the first one that fails) applied to the AGGREGATE offered/achieved series — reused, not re-implemented",
    aggregation_note:"agg_offered/agg_achieved are SUMS across the N disjoint processes (no double-counting); latency is per-process, and the aggregate reports the WORST (max) percentile across processes, not a merged one",
    scaling:$scaling,
    points:($points|sort_by(.procs)),
    skipped:$skipped
  }' > "$WORK/assembled.json" 2> "$WORK/assembly.err" || assembly_failed "assembling the block" "$WORK/assembly.err"
cat "$WORK/assembled.json" > "$OUT_FILE"

echo "--- multiproc: points=$(jq -r '.points|length' "$OUT_FILE") skipped=$(jq -r '.skipped|length' "$OUT_FILE") scales_with_procs=$(jq -r '.scaling.scales_with_procs' "$OUT_FILE")" >&2
