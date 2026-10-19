#!/usr/bin/env bash
# On-demand INFO-level investigation on the perf queue, never part of the daily run or its baseline.
# PART=jfr profiles the INFO consumer under sweep.js; PART=heap measures live heap per counted byte;
# PART=promotion measures young-GC promotion per sweep.js rung at chosen retention divisors.
# Usage, knobs and results: docs/infrastructure/ci-cd.md#info-level-investigation
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
PART="${PERF_INFO_INVESTIGATION_PART:?set PERF_INFO_INVESTIGATION_PART to jfr, heap or promotion}"
case "$PART" in jfr|heap|promotion) ;; *) echo "ERROR: PERF_INFO_INVESTIGATION_PART='$PART' is not jfr, heap or promotion" >&2; exit 1 ;; esac

# shellcheck source=lib/perf-jfr-image.sh
. "$SCRIPT_DIR/lib/perf-jfr-image.sh"
PERF_HISTO_JDK_IMAGE="${PERF_HISTO_JDK_IMAGE:-eclipse-temurin:21-jdk}"
# shellcheck source=lib/perf-info-heap-probe.sh
. "$SCRIPT_DIR/lib/perf-info-heap-probe.sh"
# shellcheck source=lib/perf-java-opts.sh
. "$SCRIPT_DIR/lib/perf-java-opts.sh"
# shellcheck source=lib/perf-info-inv.sh
. "$SCRIPT_DIR/lib/perf-info-inv.sh"

MOCKSERVER_IMAGE="${MOCKSERVER_IMAGE:-mockserver/mockserver:mockserver-snapshot-graaljs}"
K6_IMAGE="grafana/k6:1.7.1@sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f"
RUN_ID="${BUILDKITE_BUILD_NUMBER:-local}-$$"
NETWORK="perf-inv-${RUN_ID}"
OUT_ROOT="${PERF_INV_OUT_ROOT:-$REPO_ROOT/perf-info-investigation}"
OUT="$OUT_ROOT/$PART"
DIAG="$(mktemp -d "${TMPDIR:-/tmp}/perf-inv-diag.XXXXXX")"
mkdir -p "$OUT" && chmod 0777 "$OUT" "$DIAG"
LABEL=(--label "$PERF_INV_LABEL=$RUN_ID" --label "$PERF_INV_AGENT_LABEL=$(inv_agent)")
SAMPLER=""

cleanup() {
  trap '' TERM INT
  [ -z "$SAMPLER" ] || kill "$SAMPLER" >/dev/null 2>&1 || true
  inv_reap "$RUN_ID"
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  if command -v buildkite-agent >/dev/null 2>&1; then
    (cd "$(dirname "$OUT_ROOT")" && buildkite-agent artifact upload "$(basename "$OUT_ROOT")/$PART/**/*") || true
  fi
  rm -rf "$DIAG" >/dev/null 2>&1 || true
}
# A cancel or timeout sends TERM: exit 143 (130 on INT); the EXIT trap cleans up, and ignores a second signal.
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
inv_reap

CORES="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 0)"
SERVER_CPUS=""; UPSTREAM_CPUS=""; K6_CPUS=""
if [ "$CORES" -ge 24 ]; then SERVER_CPUS="0-5"; UPSTREAM_CPUS="6-9"; K6_CPUS="10-23"; fi
cpuset() { [ -z "$1" ] || printf -- '--cpuset-cpus=%s' "$1"; }
now_s() { if [ -n "${EPOCHREALTIME:-}" ]; then echo "$EPOCHREALTIME"; else python3 -c 'import time; print(time.time())'; fi; }
command -v timeout >/dev/null 2>&1 || timeout() { shift; "$@"; }

docker network create "$NETWORK" >/dev/null
docker pull -q "$MOCKSERVER_IMAGE" >/dev/null
docker pull -q "$PERF_HISTO_JDK_IMAGE" >/dev/null
IMAGE_JTO="$(image_java_tool_options "$MOCKSERVER_IMAGE")"
jq -n --arg image "$MOCKSERVER_IMAGE" \
  --arg digest "$(docker image inspect --format '{{index .RepoDigests 0}}' "$MOCKSERVER_IMAGE" 2>/dev/null || true)" \
  --arg revision "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$MOCKSERVER_IMAGE" 2>/dev/null || true)" \
  --arg created "$(docker image inspect --format '{{.Created}}' "$MOCKSERVER_IMAGE" 2>/dev/null || true)" \
  --arg image_jto "$IMAGE_JTO" --arg harness "$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || true)" \
  --arg instance "$(curl -sf --max-time 2 http://169.254.169.254/latest/meta-data/instance-type 2>/dev/null || true)" \
  --arg cores "$CORES" --arg server_cpus "$SERVER_CPUS" --arg upstream_cpus "$UPSTREAM_CPUS" --arg k6_cpus "$K6_CPUS" \
  '{image: $image, digest: $digest, revision: $revision, created: $created, image_java_tool_options: $image_jto,
    harness_commit: $harness, instance_type: $instance, cores: $cores,
    cpusets: {server: $server_cpus, upstream: $upstream_cpus, k6: $k6_cpus}}' > "$OUT/provenance.json"
cat "$OUT/provenance.json"

# start_sut <name> <alias> <log_level> <memory> <extra JAVA_TOOL_OPTIONS> [cpus]; SUT_ENV adds -e pairs.
SUT_ENV=()
start_sut() {
  local name="$1" alias="$2" level="$3" mem="$4" opts="$5" cpus="${6-$SERVER_CPUS}" jto
  jto="$(compose_java_tool_options "$IMAGE_JTO" "$opts")"
  # shellcheck disable=SC2046
  docker run -d --name "$name" "${LABEL[@]}" --network "$NETWORK" --network-alias "$alias" $(cpuset "$cpus") \
    --memory="$mem" -p 127.0.0.1::1080 -v "$DIAG:/diag" \
    ${jto:+-e "JAVA_TOOL_OPTIONS=$jto"} ${SUT_ENV[@]+"${SUT_ENV[@]}"} \
    -e MOCKSERVER_LOG_LEVEL="$level" -e MOCKSERVER_DISABLE_SYSTEM_OUT=true -e MOCKSERVER_METRICS_ENABLED=true \
    "$MOCKSERVER_IMAGE" -serverPort 1080 >/dev/null
  local _
  for _ in $(seq 1 90); do
    if [ "$(docker inspect --format '{{.State.Status}}' "$name" 2>/dev/null || echo missing)" != running ]; then
      echo "ERROR: $name is not running" >&2; docker logs --tail 50 "$name" >&2 2>&1 || true; return 1
    fi
    HOSTPORT="$(docker port "$name" 1080/tcp 2>/dev/null | head -1 || true)"
    if [ -n "$HOSTPORT" ] && curl -sf --max-time 2 -X PUT "http://$HOSTPORT/mockserver/status" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "ERROR: $name did not become ready" >&2; docker logs --tail 50 "$name" >&2 2>&1 || true; return 1
}

metrics() { curl -sf --max-time 4 "http://$1/mockserver/metrics" 2>/dev/null || true; }
gauge() { awk -v m="$1" '$1 == m && $2 != "" {printf "%.0f", $2 + 0; exit}'; }
dropped() { awk -v r="$1" 'index($1, "mock_server_dropped_log_events_total{") == 1 && index($1, "reason=\"" r "\"") {t += $2} END {printf "%.0f", t + 0}'; }

jvm_uid_gid() { # container -> "uid gid"
  # shellcheck disable=SC2016
  docker run --rm "${LABEL[@]}" --pid="container:$1" --user 0:0 "$PERF_HISTO_JDK_IMAGE" \
    sh -c 'awk "/^Uid:/{u=\$2} /^Gid:/{g=\$2} END{print u, g}" /proc/1/status' 2>/dev/null | tail -1
}
jcmd_in() { # container uid:gid jcmd-args...
  local c="$1" user="$2"; shift 2
  timeout 120 docker run --rm "${LABEL[@]}" --pid="container:$c" --user "$user" "$PERF_HISTO_JDK_IMAGE" jcmd 1 "$@"
}

k6_run() { # name mode target iterations duration [proxy] -> detached unless iterations > 0
  local name="$1" mode="$2" target="$3" iters="$4" dur="$5" proxy="${6:-}" detach=(-d)
  [ "$iters" -gt 0 ] && detach=()
  # shellcheck disable=SC2046
  docker run ${detach[@]+"${detach[@]}"} --rm --name "$name" "${LABEL[@]}" --network "$NETWORK" $(cpuset "$K6_CPUS") \
    -v "$SCRIPT_DIR/lib/perf-info-heapfill.js:/heapfill.js:ro" \
    -e MODE="$mode" -e TARGET="$target" -e ITERATIONS="$iters" -e DURATION="$dur" -e BODY_BYTES="${BODY_BYTES:-10240}" \
    ${proxy:+-e HTTP_PROXY="$proxy"} ${proxy:+-e NO_PROXY=} \
    "$K6_IMAGE" run --quiet --no-summary /heapfill.js >/dev/null 2>&1
}

# ---------------------------------------------------------------------------------------------
if [ "$PART" = jfr ]; then
  RATES="${PERF_INV_JFR_RATES:-2000,4000,6000,8000,10000,12000,16000}"
  STEP="${PERF_INV_JFR_STEP:-30s}"
  SUT="perf-inv-info-${RUN_ID}"
  mkdir -p "$DIAG/jfr-repo" && chmod 0777 "$DIAG/jfr-repo"
  JFR_OPTS="-XX:StartFlightRecording=name=inv,settings=profile,maxsize=4g,jdk.JavaMonitorEnter#threshold=1ms,jdk.ThreadCPULoad#period=1s,jdk.CPUTimeSample#enabled=true -XX:FlightRecorderOptions=repository=/diag/jfr-repo,stackdepth=128"
  echo "--- INFO SUT ($MOCKSERVER_IMAGE, 2g, shipped event-log default) with JFR profile"
  start_sut "$SUT" mockserver-info INFO 2g "$JFR_OPTS"
  SUT_HP="$HOSTPORT"
  metrics "$SUT_HP" | grep -E '^(mock_server_event_log_max|jvm_memory_max_bytes\{area="heap"|jvm_runtime_info)' > "$OUT/sut-config-metrics.txt" || true
  echo "ts,dropped_ring_full,dropped_in_flight_bytes,ring_occupancy,in_flight_bytes,retained_entries" > "$OUT/event-log-pressure.csv"
  ( while true; do
      m="$(metrics "$SUT_HP")"
      [ -n "$m" ] && printf '%s,%s,%s,%s,%s,%s\n' "$(date -u +%s)" "$(dropped ring_full <<<"$m")" "$(dropped in_flight_bytes <<<"$m")" \
        "$(gauge mock_server_event_log_ring_occupancy <<<"$m")" "$(gauge mock_server_event_log_in_flight_bytes <<<"$m")" \
        "$(gauge mock_server_event_log_retained_entries <<<"$m")"
      sleep 2
    done >> "$OUT/event-log-pressure.csv" ) & SAMPLER=$!
  echo "--- sweep.js at INFO: rates $RATES, step $STEP"
  # shellcheck disable=SC2046
  docker run --rm --name "k6-inv-sweep-${RUN_ID}" "${LABEL[@]}" --network "$NETWORK" $(cpuset "$K6_CPUS") \
    -v "$REPO_ROOT/mockserver-performance-test/k6:/k6:ro" -v "$OUT:/out" \
    -e BASE_URL=http://mockserver-info:1080 -e PROTO=http -e K6_SWEEP_RATES="$RATES" -e K6_SWEEP_STEP="$STEP" \
    -e K6_SWEEP_GAP=5s -e K6_SWEEP_SETTLE=3s -e K6_SWEEP_RESULT_PATH=/out/info-sweep.json \
    "$K6_IMAGE" run /k6/sweep.js > "$OUT/k6-sweep.log" 2>&1 || echo "WARNING: sweep.js exited non-zero (see k6-sweep.log)" >&2
  kill "$SAMPLER" >/dev/null 2>&1 || true; wait "$SAMPLER" 2>/dev/null || true; SAMPLER=""
  [ -s "$OUT/info-sweep.json" ] || { echo "ERROR: sweep.js wrote no result, so the ladder did not run" >&2; exit 1; }
  read -r UID_ GID_ <<<"$(jvm_uid_gid "$SUT")"
  echo "--- JFR.dump as ${UID_}:${GID_}"
  jcmd_in "$SUT" "${UID_}:${GID_}" JFR.dump name=inv filename=/diag/info-sweep.jfr > "$OUT/jfr-dump.log" 2>&1 || cat "$OUT/jfr-dump.log" >&2
  jcmd_in "$SUT" "${UID_}:${GID_}" Thread.print > "$OUT/thread-print-after.txt" 2>&1 || true
  metrics "$SUT_HP" > "$OUT/sut-metrics-end.txt" || true
  if [ -s "$DIAG/info-sweep.jfr" ]; then
    gzip -c "$DIAG/info-sweep.jfr" > "$OUT/info-sweep.jfr.gz"
    docker run --rm "${LABEL[@]}" -v "$DIAG:/w:ro" "$PERF_JFR_JDK_IMAGE" jfr summary /w/info-sweep.jfr > "$OUT/jfr-summary.txt" 2>&1 || true
  else
    echo "ERROR: no JFR recording written" >&2; exit 1
  fi
  echo "--- done: $(ls -la "$OUT")"
  exit 0
fi

# ---------------------------------------------------------------------------------------------
# PART=promotion. Each cell (collector x container memory x retention divisor): a fresh SUT with
# -Xlog:gc* and maxEventLogSizeInBytes = heap/divisor, a sweep.js prefill to 93% of that budget, then
# the sweep.js ladder with 1 s event-log samples; lib/perf-info-promotion.py derives each rung.
if [ "$PART" = promotion ]; then
  COLLECTORS="${PERF_INV_COLLECTORS:-G1 ZGC}"
  MEMORIES="${PERF_INV_PROMO_MEMORIES:-1g 2g}"
  DIVISORS="${PERF_INV_PROMO_DIVISORS:-12 20}"
  LEVEL="${PERF_INV_PROMO_LEVEL:-INFO}"
  RATES="${PERF_INV_PROMO_RATES:-2000,3000,4000,5000,6000,8000,10000}"
  STEP_S="${PERF_INV_PROMO_STEP_S:-30}"; SETTLE_S="${PERF_INV_PROMO_SETTLE_S:-5}"
  PREFILL_RATE="${PERF_INV_PROMO_PREFILL_RATE:-2000}"
  FILL_TIMEOUT_S="${PERF_INV_FILL_TIMEOUT_S:-240}"
  PROMO_CPUS=""; [ -n "$SERVER_CPUS" ] && PROMO_CPUS="${PERF_INV_PROMO_SERVER_CPUS:-0-1}"
  case "$LEVEL" in INFO|WARN) ;; *) echo "ERROR: PERF_INV_PROMO_LEVEL='$LEVEL' is not INFO or WARN" >&2; exit 1 ;; esac
  CELLS="$OUT/cells.jsonl"; : > "$CELLS"
  ENTRY_XX="$(docker image inspect --format '{{json .Config.Entrypoint}}' "$MOCKSERVER_IMAGE" 2>/dev/null \
    | jq -r '.[]? | select(startswith("-XX:"))' 2>/dev/null | paste -sd' ' - || true)"

  k6_sweep() { # name rates step_s settle_s result manage_sut [detach]
    # shellcheck disable=SC2046
    docker run ${7:+-d} --rm --name "$1" "${LABEL[@]}" --network "$NETWORK" $(cpuset "$K6_CPUS") \
      -v "$REPO_ROOT/mockserver-performance-test/k6:/k6:ro" -v "$OUT:/out" \
      -e BASE_URL=http://mockserver:1080 -e PROTO=http -e K6_SWEEP_RATES="$2" -e K6_SWEEP_STEP="${3}s" \
      -e K6_SWEEP_GAP=5s -e K6_SWEEP_SETTLE="${4}s" -e K6_SWEEP_RESULT_PATH="$5" \
      -e K6_SWEEP_MANAGE_SUT="$6" -e K6_SWEEP_RESET_ON_TEARDOWN=false \
      "$K6_IMAGE" run /k6/sweep.js
  }
  ts_ms() { awk -v t="$(now_s)" 'BEGIN {printf "%.0f", t * 1000}'; }

  for gc in $COLLECTORS; do
    case "$gc" in G1) gc_flag="-XX:+UseG1GC" ;; ZGC) gc_flag="-XX:+UseZGC" ;; *) echo "ERROR: collector '$gc'" >&2; exit 1 ;; esac
    for mem in $MEMORIES; do
      # shellcheck disable=SC2086 # ENTRY_XX is one flag per word
      max_heap="$(docker run --rm "${LABEL[@]}" --memory="$mem" --entrypoint java \
        -e "JAVA_TOOL_OPTIONS=$(compose_java_tool_options "$IMAGE_JTO" "$gc_flag")" "$MOCKSERVER_IMAGE" \
        $ENTRY_XX -XX:+PrintFlagsFinal -version 2>/dev/null | awk '$2 == "MaxHeapSize" {print $4; exit}' || true)"
      heap_kb=""; [[ "$max_heap" =~ ^[0-9]+$ ]] && heap_kb=$(( max_heap / 1024 - 20480 ))
      for div in $DIVISORS; do
        cell="${LEVEL}-${gc}-${mem}-d${div}"; CD="$OUT/$cell"; mkdir -p "$CD" "$DIAG/$cell"; chmod 0777 "$CD" "$DIAG/$cell"
        sut="perf-inv-sut-${RUN_ID}-${cell}"; k6name="k6-inv-${RUN_ID}-${cell}"
        echo "+++ cell $cell (MaxHeapSize ${max_heap:-unknown})"
        if [ -z "$heap_kb" ] || [ "$heap_kb" -le 0 ]; then
          jq -nc --arg cell "$cell" '{cell: $cell, status: "heap_unknown"}' >> "$CELLS"; continue
        fi
        budget=$(( heap_kb / div * 1024 )); in_flight=$(( heap_kb / 7 * 1024 )); [ "$budget" -gt "$in_flight" ] && in_flight="$budget"
        SUT_ENV=(-e "MOCKSERVER_MAX_EVENT_LOG_SIZE_IN_BYTES=$budget")
        if ! start_sut "$sut" mockserver "$LEVEL" "$mem" \
            "$gc_flag -Xlog:gc*:file=/diag/$cell/gc-%p.log:time,uptime,level,tags:filecount=0" "$PROMO_CPUS"; then
          SUT_ENV=(); jq -nc --arg cell "$cell" '{cell: $cell, status: "sut_failed"}' >> "$CELLS"; docker rm -f "$sut" >/dev/null 2>&1 || true; continue
        fi
        SUT_ENV=(); hp="$HOSTPORT"; m="$(metrics "$hp")"
        got_budget="$(gauge mock_server_event_log_max_retained_bytes <<<"$m")"; got_if="$(gauge mock_server_event_log_max_in_flight_bytes <<<"$m")"
        status=measured; prefill='null'; analysis='null'
        if [ "$got_budget" != "$budget" ] || [ "$got_if" != "$in_flight" ]; then
          status=budget_mismatch
        else
          k6_sweep "${k6name}-prefill" "$PREFILL_RATE" 1200 3 "/out/$cell/prefill.json" true detach > /dev/null
          t0="$(date +%s)"; peak=0
          while [ $(( $(date +%s) - t0 )) -lt "$FILL_TIMEOUT_S" ]; do
            b="$(metrics "$hp" | gauge mock_server_event_log_retained_bytes)"
            [ -n "$b" ] && [ "$b" -gt "$peak" ] && peak="$b"
            awk -v b="$peak" -v x="$budget" 'BEGIN {exit !(b >= 0.93 * x)}' && break
            sleep 0.5
          done
          docker kill "${k6name}-prefill" >/dev/null 2>&1 || true
          prefill="$(jq -nc --argjson peak "$peak" --argjson budget "$budget" --argjson el "$(( $(date +%s) - t0 ))" \
            '{peak_retained_bytes: $peak, peak_fraction_of_budget: ($peak / $budget * 10000 | round / 10000), elapsed_s: $el}')"
          if [ "$(jq -r '.peak_fraction_of_budget >= 0.93' <<<"$prefill")" != true ]; then
            status=prefill_short
          else
            echo "ts_ms,retained_entries,retained_bytes,evicted,ring_occupancy,in_flight_bytes,dropped_ring_full,dropped_in_flight_bytes" > "$CD/samples.csv"
            ( while :; do
                t="$(ts_ms)"; m="$(metrics "$hp")"
                [ -n "$m" ] && printf '%s,%s,%s,%s,%s,%s,%s,%s\n' "$t" "$(gauge mock_server_event_log_retained_entries <<<"$m")" \
                  "$(gauge mock_server_event_log_retained_bytes <<<"$m")" "$(gauge mock_server_evicted_log_entries_total <<<"$m")" \
                  "$(gauge mock_server_event_log_ring_occupancy <<<"$m")" "$(gauge mock_server_event_log_in_flight_bytes <<<"$m")" \
                  "$(dropped ring_full <<<"$m")" "$(dropped in_flight_bytes <<<"$m")"
                sleep 1
              done >> "$CD/samples.csv" ) & SAMPLER=$!
            k6_sweep "${k6name}-sweep" "$RATES" "$STEP_S" "$SETTLE_S" "/out/$cell/sweep.json" false > "$CD/k6-sweep.log" 2>&1 \
              || echo "WARNING: sweep.js exited non-zero (see $cell/k6-sweep.log)" >&2
            kill "$SAMPLER" >/dev/null 2>&1 || true; wait "$SAMPLER" 2>/dev/null || true; SAMPLER=""
            metrics "$hp" > "$CD/sut-metrics-end.txt" || true
          fi
        fi
        docker rm -f "$sut" >/dev/null 2>&1 || true
        gclog="$DIAG/$cell/gc-1.log"
        if [ "$status" = measured ]; then
          if [ ! -s "$CD/sweep.json" ]; then status=no_sweep_result
          elif [ ! -s "$gclog" ]; then status=no_gc_log
          elif ! analysis="$(python3 -I "$SCRIPT_DIR/lib/perf-info-promotion.py" "$gclog" "$CD/sweep.json" "$CD/samples.csv" "$SETTLE_S" "$STEP_S" "$budget")"; then
            status=analysis_failed; analysis='null'
          fi
        fi
        [ -s "$gclog" ] && gzip -c "$gclog" > "$CD/gc.log.gz"
        jq -nc --arg cell "$cell" --arg gc "$gc" --arg mem "$mem" --arg level "$LEVEL" --argjson div "$div" --arg status "$status" \
          --argjson max_heap "$max_heap" --argjson heap_kb "$heap_kb" --argjson budget "$budget" --argjson in_flight "$in_flight" \
          --arg got_budget "$got_budget" --arg got_if "$got_if" --argjson prefill "$prefill" --argjson analysis "$analysis" \
          'def n: if . == "" then null else tonumber end;
           {cell: $cell, collector: $gc, memory: $mem, level: $level, divisor: $div, status: $status,
            max_heap_bytes: $max_heap, heap_available_kb: $heap_kb, budget_bytes: $budget, in_flight_cap_bytes: $in_flight,
            gauges: {max_retained_bytes: ($got_budget | n), max_in_flight_bytes: ($got_if | n)},
            prefill: $prefill, analysis: $analysis}' | tee -a "$CELLS"
      done
    done
  done

  jq -rs '
    ["cell","offered","achieved","p50_ms","young_s","promoted_mb_s","alloc_mb_young","major","stalls","lifetime_s","life/young","ring/young","life_alloc/budget","drops_rf","drops_ib","status"],
    (.[] | .cell as $c | (.analysis.rungs // [])[] | [$c, .offered_rps, .achieved_rps, .p50_ms, .young_interval_s, .promoted_mb_s,
      .alloc_mb_per_young, .major_collections, .allocation_stalls, .lifetime_s, .lifetime_over_young_interval,
      .ring_wait_over_young_interval, .lifetime_alloc_over_budget, .dropped_ring_full, .dropped_in_flight_bytes, .status])
    | map(tostring) | join("\t")' "$CELLS" | tee "$OUT/summary.tsv"

  set -- $COLLECTORS; n_gc=$#; set -- $MEMORIES; n_mem=$#; set -- $DIVISORS; n_div=$#
  if ! verdict="$(inv_promo_verdict "$CELLS" $(( n_gc * n_mem * n_div )))"; then
    printf '+++ :x: promotion investigation incomplete: every cell must be fully measured\n%s\n' "$verdict" >&2
    if command -v buildkite-agent >/dev/null 2>&1; then
      printf ':x: **INFO promotion investigation incomplete** - not every cell was fully measured:\n\n```\n%s\n```\n' "$verdict" \
        | buildkite-agent annotate --style error --context perf-info-investigation || true
    fi
    exit 1
  fi
  echo "--- every cell fully measured"
  exit 0
fi

# ---------------------------------------------------------------------------------------------
# PART=heap. Each cell: fresh SUT at -Xmx1g -Xms1g (PERF_INV_HEAP_MB), warm-up, reset, H0, fill to 93% of the retained
# budget then 40% more, drained probe (histogram, reset, GC, histogram). INFO cells then refill and
# hold 8 clients for OVERLOAD_S, histogram at three points, and probe the drained log again.
COLLECTORS="${PERF_INV_COLLECTORS:-G1 ZGC}"
PROFILES="${PERF_INV_PROFILES:-json10k proxy10k}"
LEVELS="${PERF_INV_LEVELS:-INFO WARN}"
OVERLOAD_S="${PERF_INV_OVERLOAD_S:-60}"
FILL_TIMEOUT_S="${PERF_INV_FILL_TIMEOUT_S:-240}"
HEAP_MB="${PERF_INV_HEAP_MB:-1024}"
HEAP_BYTES=$(( HEAP_MB * 1048576 ))
CEILING_BYTES=$(( (HEAP_BYTES / 1024 - 20480) * 1024 ))
UPSTREAM="perf-inv-upstream-${RUN_ID}"
CELLS="$OUT/cells.jsonl"; : > "$CELLS"

seed_json() { # host:port
  curl -sf --max-time 10 -X PUT "http://$1/mockserver/expectation" -d \
    '{"httpRequest":{"path":"/json10k"},"httpResponse":{"statusCode":200,"headers":{"content-type":["application/json"]},"body":"{\"ok\":true}"},"times":{"unlimited":true}}' >/dev/null
}
seed_echo() { # host:port
  curl -sf --max-time 10 -X PUT "http://$1/mockserver/expectation" -d \
    '{"httpRequest":{"path":"/echo"},"httpResponseTemplate":{"templateType":"MUSTACHE","template":"{\"statusCode\":200,\"headers\":{\"content-type\":[\"text/plain\"]},\"body\":\"{{ request.body }}\"}"},"times":{"unlimited":true}}' >/dev/null
}

if [[ " $PROFILES " == *" proxy10k "* ]]; then
  echo "--- upstream echo server"
  start_sut "$UPSTREAM" mockserver-upstream ERROR 2g "" "$UPSTREAM_CPUS"
  UP_HP="$HOSTPORT"; seed_echo "$UP_HP"
  echo "upstream echo check: $(curl -sf --max-time 5 -X POST -d 'echo-me' "http://$UP_HP/echo" || echo FAILED)"
fi

# fill <sut> <hostport> <mode> <target> <proxy> <k6name>: runs k6 until retained bytes reach 93% of
# the budget (or FILL_TIMEOUT_S), then 40% longer; sets FILL_JSON.
fill() {
  local sut="$1" hp="$2" mode="$3" target="$4" proxy="$5" k6name="$6" t0 now t93="" m b max frac=0 peak=0
  k6_run "$k6name" "$mode" "$target" 0 60m "$proxy"
  t0="$(now_s)"
  while :; do
    m="$(metrics "$hp")"; b="$(gauge mock_server_event_log_retained_bytes <<<"$m")"; max="$(gauge mock_server_event_log_max_retained_bytes <<<"$m")"
    now="$(now_s)"
    if [ -n "$b" ] && [ -n "$max" ] && [ "$max" -gt 0 ]; then
      [ "$b" -gt "$peak" ] && peak="$b"
      if [ -z "$t93" ] && awk -v b="$b" -v m="$max" 'BEGIN{exit !(b >= 0.93 * m)}'; then t93="$(awk -v a="$now" -v z="$t0" 'BEGIN{print a - z}')"; fi
    fi
    if [ -n "$t93" ] && awk -v a="$now" -v z="$t0" -v t="$t93" 'BEGIN{exit !(a - z >= 1.4 * t)}'; then break; fi
    if [ -z "$t93" ] && awk -v a="$now" -v z="$t0" -v t="$FILL_TIMEOUT_S" 'BEGIN{exit !(a - z >= t)}'; then break; fi
    sleep 0.5
  done
  docker kill "$k6name" >/dev/null 2>&1 || true
  frac="$(awk -v p="$peak" -v m="${max:-0}" 'BEGIN{if (m > 0) printf "%.4f", p / m; else print "null"}')"
  FILL_JSON="$(jq -nc --arg t93 "$t93" --argjson peak "$peak" --argjson frac "$frac" --arg max "${max:-}" --arg el "$(awk -v a="$(now_s)" -v z="$t0" 'BEGIN{printf "%.1f", a - z}')" \
    '{reached_93pct_after_s: (if $t93 == "" then null else ($t93 | tonumber * 10 | round / 10) end), elapsed_s: ($el | tonumber),
      peak_retained_bytes: $peak, peak_fraction_of_budget: $frac, max_retained_bytes: (if $max == "" then null else ($max | tonumber) end)}')"
}

gc_label() { awk 'index($1, "jvm_runtime_info{") == 1 && match($0, /gc="[^"]*"/) {print substr($0, RSTART + 4, RLENGTH - 5); exit}'; }
counters_json() { # host:port -> drops/evictions/budgets
  local m; m="$(metrics "$1")"
  jq -nc --arg rf "$(dropped ring_full <<<"$m")" --arg ib "$(dropped in_flight_bytes <<<"$m")" \
    --arg ev "$(gauge mock_server_evicted_log_entries_total <<<"$m")" \
    --arg mrb "$(gauge mock_server_event_log_max_retained_bytes <<<"$m")" --arg mif "$(gauge mock_server_event_log_max_in_flight_bytes <<<"$m")" \
    --arg gc "$(gc_label <<<"$m")" \
    'def n: if . == "" then null else tonumber end;
     {dropped_ring_full: ($rf | n), dropped_in_flight_bytes: ($ib | n), evicted: ($ev | n),
      max_retained_bytes: ($mrb | n), max_in_flight_bytes: ($mif | n), gc: (if $gc == "" then null else $gc end)}'
}

histo_total() { # container uid:gid label celldir -> bytes on stdout
  local out; out="$(jcmd_in "$1" "$2" GC.class_histogram 2>&1)" || { echo ""; return 0; }
  awk 'NR <= 43 {print; next} {last = $0} END {if (NR > 43) print last}' <<<"$out" > "$4/histogram-$3.txt" 2>/dev/null || true
  histogram_total_bytes <<<"$out"
}

for gc in $COLLECTORS; do
  case "$gc" in G1) gc_flag="-XX:+UseG1GC" ;; ZGC) gc_flag="-XX:+UseZGC" ;; *) echo "ERROR: collector '$gc'" >&2; exit 1 ;; esac
  for profile in $PROFILES; do
    for level in $LEVELS; do
      cell="${profile}-${level}-${gc}"; CD="$OUT/$cell"; mkdir -p "$CD"
      sut="perf-inv-sut-${RUN_ID}-${cell}"; k6name="k6-inv-${RUN_ID}-${cell}"
      echo "+++ cell $cell"
      if ! start_sut "$sut" mockserver "$level" 2g "$gc_flag -Xmx${HEAP_MB}m -Xms${HEAP_MB}m"; then
        jq -nc --arg cell "$cell" '{cell: $cell, status: "sut_failed"}' >> "$CELLS"; docker rm -f "$sut" >/dev/null 2>&1 || true; continue
      fi
      hp="$HOSTPORT"
      case "$profile" in
        json10k|json100k) mode=json; target="http://mockserver:1080/json10k"; proxy=""; seed_json "$hp" ;;
        proxy10k) mode=proxy; target="http://mockserver-upstream:1080/echo"; proxy="http://mockserver:1080" ;;
        *) echo "ERROR: profile '$profile'" >&2; exit 1 ;;
      esac
      BODY_BYTES=10240; [ "$profile" = json100k ] && BODY_BYTES=102400
      k6_run "${k6name}-warm" "$mode" "$target" 300 1m "$proxy" || echo "WARNING: warm-up k6 exited non-zero" >&2
      curl -sf --max-time 15 -X PUT "http://$hp/mockserver/reset" >/dev/null
      [ "$mode" = json ] && seed_json "$hp"
      [ "$profile" = proxy10k ] && { curl -sf --max-time 15 -X PUT "http://$UP_HP/mockserver/reset" >/dev/null; seed_echo "$UP_HP"; }
      sleep 3
      read -r uid gid <<<"$(jvm_uid_gid "$sut")"
      h0="$(histo_total "$sut" "${uid}:${gid}" h0 "$CD")"
      fill "$sut" "$hp" "$mode" "$target" "$proxy" "$k6name"; fill1="$FILL_JSON"
      ctr1="$(counters_json "$hp")"
      up_reqs=null
      [ "$profile" = proxy10k ] && up_reqs="$(metrics "$UP_HP" | awk '$1 ~ /^mock_server_event_log_retained_entries$/ {printf "%.0f", $2}')"
      probe1="$(info_heap_probe_run "$sut" "$hp" "$CD" 300)"
      for f in "$CD"/reset-probe-histogram-*.txt; do [ -e "$f" ] && mv "$f" "${f%.txt}-fill.txt"; done
      overload=null
      if [ "$level" = INFO ] && [ "$OVERLOAD_S" -gt 0 ]; then
        [ "$mode" = json ] && seed_json "$hp"
        sleep 3
        h0b="$(histo_total "$sut" "${uid}:${gid}" h0b "$CD")"
        k6_run "${k6name}-ol" "$mode" "$target" 0 60m "$proxy"
        tstart="$(date +%s)"; reached=false
        while [ $(( $(date +%s) - tstart )) -lt "$FILL_TIMEOUT_S" ]; do
          m="$(metrics "$hp")"
          if awk -v b="$(gauge mock_server_event_log_retained_bytes <<<"$m")" -v x="$(gauge mock_server_event_log_max_retained_bytes <<<"$m")" 'BEGIN{exit !(x > 0 && b >= 0.93 * x)}'; then reached=true; break; fi
          sleep 0.5
        done
        tw="$(date +%s)"; samples='[]'
        for at in $(( OVERLOAD_S / 3 )) $(( OVERLOAD_S * 2 / 3 )) $(( OVERLOAD_S * 11 / 12 )); do
          while [ $(( $(date +%s) - tw )) -lt "$at" ]; do sleep 0.5; done
          m="$(metrics "$hp")"
          inf="$(gauge mock_server_event_log_in_flight_bytes <<<"$m")"; occ="$(gauge mock_server_event_log_ring_occupancy <<<"$m")"; rb="$(gauge mock_server_event_log_retained_bytes <<<"$m")"
          hs="$(histo_total "$sut" "${uid}:${gid}" "ol-${at}s" "$CD")"
          samples="$(jq -c --arg at "$at" --arg inf "$inf" --arg occ "$occ" --arg rb "$rb" --arg hs "$hs" \
            'def n: if . == "" then null else tonumber end; . + [{at_s: ($at | n), in_flight_bytes: ($inf | n), ring_occupancy: ($occ | n), retained_bytes: ($rb | n), live_heap_bytes: ($hs | n)}]' <<<"$samples")"
        done
        while [ $(( $(date +%s) - tw )) -lt "$OVERLOAD_S" ]; do sleep 0.5; done
        docker kill "${k6name}-ol" >/dev/null 2>&1 || true
        ctr2="$(counters_json "$hp")"
        probe2="$(info_heap_probe_run "$sut" "$hp" "$CD" 300)"
        for f in "$CD"/reset-probe-histogram-*.txt; do [ -e "$f" ] && [[ "$f" != *-fill.txt ]] && mv "$f" "${f%.txt}-overload.txt"; done
        overload="$(jq -nc --argjson samples "$samples" --argjson probe "$probe2" --argjson ctr "$ctr2" --arg h0b "$h0b" \
          --argjson reached "$reached" --argjson ceiling "$CEILING_BYTES" --argjson xmx "$HEAP_BYTES" '
          ($h0b | if . == "" then null else tonumber end) as $h0
          | ($probe.live_heap_delta_bytes) as $deque
          | {reached_93pct_before_window: $reached, h0_bytes: $h0, counters_after: $ctr, drained_probe: $probe,
             samples: [$samples[] | . + (if $h0 != null and .live_heap_bytes != null then
                 (.live_heap_bytes - $h0) as $whole
                 | {whole_log_bytes: $whole,
                    whole_log_share_of_ceiling: ($whole / $ceiling * 1000 | round / 1000),
                    live_heap_share_of_xmx: (.live_heap_bytes / $xmx * 1000 | round / 1000),
                    ring_multiple: (if $deque != null and (.in_flight_bytes // 0) > 0 then (($whole - $deque) / .in_flight_bytes * 1000 | round / 1000) else null end)}
               else {} end)]}
          | .peak = (.samples | max_by(.whole_log_bytes // 0))')"
      fi
      jq -nc --arg cell "$cell" --arg gc "$gc" --arg profile "$profile" --arg level "$level" --arg h0 "$h0" \
        --argjson fill "$fill1" --argjson counters "$ctr1" --argjson probe "$probe1" --argjson overload "$overload" \
        --argjson upstream_retained "${up_reqs:-null}" \
        '{cell: $cell, collector: $gc, profile: $profile, level: $level, status: "measured", h0_bytes: ($h0 | if . == "" then null else tonumber end),
          fill: $fill, counters_after_fill: $counters, drained_probe: $probe, upstream_retained_entries: $upstream_retained, overload: $overload}' \
        | tee -a "$CELLS"
      docker rm -f "$sut" >/dev/null 2>&1 || true
    done
  done
done

jq -rs '
  ["cell","gc","fill_frac","t93_s","drops_rf","drops_ib","drained_mult","per_entry_B","ol_deque_mult","ol_ring_mult","ol_whole_share","ol_heap_share","ol_drops_rf","ol_drops_ib"],
  (.[] | [.cell, (.counters_after_fill.gc // .collector), .fill.peak_fraction_of_budget, .fill.reached_93pct_after_s,
          .counters_after_fill.dropped_ring_full, .counters_after_fill.dropped_in_flight_bytes,
          .drained_probe.live_heap_per_counted_byte, .drained_probe.live_heap_bytes_per_entry,
          .overload.drained_probe.live_heap_per_counted_byte, .overload.peak.ring_multiple,
          .overload.peak.whole_log_share_of_ceiling, .overload.peak.live_heap_share_of_xmx,
          .overload.counters_after.dropped_ring_full, .overload.counters_after.dropped_in_flight_bytes])
  | map(tostring) | join("\t")' "$CELLS" | tee "$OUT/summary.tsv"

set -- $COLLECTORS; n_gc=$#; set -- $PROFILES; n_profiles=$#; set -- $LEVELS; n_levels=$#
overload_expected=false; [ "$OVERLOAD_S" -gt 0 ] && overload_expected=true
if ! verdict="$(inv_cells_verdict "$CELLS" $(( n_gc * n_profiles * n_levels )) "$overload_expected")"; then
  printf '+++ :x: heap investigation incomplete: every cell must be fully measured\n%s\n' "$verdict" >&2
  if command -v buildkite-agent >/dev/null 2>&1; then
    printf ':x: **INFO heap investigation incomplete** - not every cell was fully measured:\n\n```\n%s\n```\n' "$verdict" \
      | buildkite-agent annotate --style error --context perf-info-investigation || true
  fi
  exit 1
fi
echo "--- every cell fully measured"
