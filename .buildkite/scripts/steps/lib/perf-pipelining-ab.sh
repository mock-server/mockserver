#!/usr/bin/env bash
# In-order HTTP/1.1 pipelining A/B, sourced by perf-test-run.sh. Two fresh SUTs from the run's image,
# identical except MOCKSERVER_HTTP1_PIPELINING_IN_ORDER=true|false, measured one after the other on
# the main SUT's cpuset: regression.js (match + forward, k6 never pipelines) and a pipelining client
# (mockserver-performance-test/k6/tools/pipelined_http1_client.py). The metric is the on / off ratio.
# Notify-only and kept out of the run's validity block. Defines functions only.
# Docs: docs/code/performance-measurement.md. Tests: .buildkite/scripts/test/perf-pipelining-ab-test.sh

# Arm order alternates with the build number so a drift across the phase does not favour one arm.
pl_arm_order() { # build_number
  local n="${1:-0}"
  [[ "$n" =~ ^[0-9]+$ ]] || n=0
  if [ $(( n % 2 )) -eq 0 ]; then echo "on off"; else echo "off on"; fi
}

# host:port the host-side pipelining client dials: the container's bridge IP when the host can reach
# it (no docker-proxy hop), else the published port. Prints "<addr> <path>"; empty when neither is known.
pl_dial_addr() { # container
  local ip hp
  ip="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$1" 2>/dev/null | awk '{print $1}')" || ip=""
  if [ -n "$ip" ] && [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 -X PUT "http://${ip}:1080/mockserver/status" 2>/dev/null)" = 200 ]; then
    echo "${ip}:1080 bridge_ip"; return 0
  fi
  hp="$(docker port "$1" 1080/tcp 2>/dev/null | head -1)" || hp=""
  [ -z "$hp" ] || echo "$hp published_port"
}

pl_json_or_empty() { jq -ce . "$1" 2>/dev/null || echo '{}'; }

# The .pipelining_inorder block from the per-arm files in <dir> (pl-{reg,load,probe}-{on,off}.json).
# measured is true only when both probes prove their configuration, every arm (match, forward and
# pipelined, the one the gate works on) has an on / off p95 ratio and at most 1% errors on each side,
# and both arms dialled the same path.
pl_assemble_json() { # dir arm_order path_on path_off connections depth
  local d="$1"
  jq -nc \
    --argjson reg_on "$(pl_json_or_empty "$d/pl-reg-on.json")" \
    --argjson reg_off "$(pl_json_or_empty "$d/pl-reg-off.json")" \
    --argjson load_on "$(pl_json_or_empty "$d/pl-load-on.json")" \
    --argjson load_off "$(pl_json_or_empty "$d/pl-load-off.json")" \
    --argjson probe_on "$(pl_json_or_empty "$d/pl-probe-on.json")" \
    --argjson probe_off "$(pl_json_or_empty "$d/pl-probe-off.json")" \
    --arg arm_order "$2" --arg path_on "$3" --arg path_off "$4" \
    --argjson connections "${5:-null}" --argjson depth "${6:-null}" '
    def byop(b; p): (b | to_entries | map({key: (.key | sub("_" + p + "$"; "")), value: .value}) | from_entries);
    def ratio(a; b): if a != null and (b // 0) > 0 then ((a / b) * 1000 | round) / 1000 else null end;
    def side(load; b): if load.error != null then load.error elif load == {} then "no result" else "p95 \(b.pipelined.p95_ms // null)" end;
    byop(($reg_on.behaviours // {}) + ($load_on.behaviours // {}); "pipelining_on") as $ON
    | byop(($reg_off.behaviours // {}) + ($load_off.behaviours // {}); "pipelining_off") as $OFF
    | ([ $ON | keys[] | select($OFF[.] != null) | . as $op | ($ON[$op]) as $a | ($OFF[$op]) as $b
         | {key: $op, value: {
             pipelined: ($op == "pipelined"),
             p50_on_ms: $a.p50_ms, p50_off_ms: $b.p50_ms, p50_ratio: ratio($a.p50_ms; $b.p50_ms),
             p95_on_ms: $a.p95_ms, p95_off_ms: $b.p95_ms, p95_ratio: ratio($a.p95_ms; $b.p95_ms),
             p99_on_ms: $a.p99_ms, p99_off_ms: $b.p99_ms, p99_ratio: ratio($a.p99_ms; $b.p99_ms),
             throughput_on_rps: $a.throughput_rps, throughput_off_rps: $b.throughput_rps,
             throughput_ratio: ratio($a.throughput_rps; $b.throughput_rps),
             error_rate_on: $a.error_rate, error_rate_off: $b.error_rate,
             sample_count_on: $a.sample_count, sample_count_off: $b.sample_count }} ] | from_entries) as $arms
    | ([ (if ($probe_on.ok == true) and ($probe_on.in_order == true) then empty
          else "the in-order arm did not answer a pipelined slow-then-fast pair in order (probe: \($probe_on | tojson))" end),
         (if ($probe_off.ok == true) and ($probe_off.in_order == false) then empty
          else "the out-of-order arm did not answer the fast request first, so the flag did not take effect (probe: \($probe_off | tojson))" end),
         (if $arms.pipelined.p95_ratio != null then empty
          else "the pipelining client produced no usable p95 on both sides (on: \(side($load_on; $ON)), off: \(side($load_off; $OFF)))" end),
         ("match", "forward" | . as $op
          | if $arms[$op].p95_ratio != null then empty else "regression.js arm \($op) produced no on / off p95 ratio" end),
         ($arms | to_entries[] | .key as $op | .value as $v | ("on", "off") as $side
          | ($v["error_rate_" + $side]) as $e
          | if ($e // 1) <= 0.01 then empty
            else "arm \($op) failed \($e // "an unknown share of") of its requests with the flag \($side) (over the 1% bound), so its latency describes failures" end),
         (if $path_on == $path_off then empty
          else "the arms reached their SUTs by different paths (on: \($path_on), off: \($path_off)), so their pipelined figures are not comparable" end) ]) as $problems
    | { attempted: true, measured: (($problems | length) == 0), problems: $problems,
        flag: "mockserver.http1PipeliningInOrder", ratio: "in-order on / off",
        arm_order: $arm_order,
        client: {path_on: $path_on, path_off: $path_off, connections: $connections, depth: $depth},
        probe: {on: $probe_on, off: $probe_off}, arms: $arms }'
}

# regression.js, match + forward only, against one arm's SUT over the run's docker network.
pl_run_regression() { # arm base_url out
  # shellcheck disable=SC2046
  docker run --rm --network "$NETWORK" $(cpuset_arg "$K6_CPUS") \
    -v "$REPO_ROOT/mockserver-performance-test/k6:/k6:ro" -v "$OUT_DIR:/out" \
    -e "BASE_URL=$2" -e "PROTO=pipelining_$1" -e "INSECURE_SKIP_TLS_VERIFY=false" \
    -e "K6_RESULT_PATH=/out/$3" -e "K6_REG_ARMS=match,forward" \
    -e "K6_REG_JS_TEMPLATE=false" -e "K6_REG_FILE_BODY_PATH=" \
    -e "K6_REG_WARMUP=$PL_WARMUP" -e "K6_REG_DURATION=$PL_DURATION" \
    ${K6_REG_RATE:+-e K6_REG_RATE="$K6_REG_RATE"} \
    "$K6_IMAGE" run /k6/regression.js >/dev/null 2>&1
}

# <cmd...> killed after <seconds> when timeout(1) exists: a wedged client must not spend the step timeout.
pl_bounded() { # seconds cmd...
  local s="$1"; shift
  if command -v timeout >/dev/null 2>&1; then timeout "$s" "$@"; else "$@"; fi
}

# Runs both arms and sets PIPELINING_JSON. Needs perf-test-run.sh's start_mockserver, wait_ready,
# cpuset_arg, START_EXTRA_ENV and run globals.
run_pipelining_ab() {
  local client="$REPO_ROOT/mockserver-performance-test/k6/tools/pipelined_http1_client.py"
  local runner=(python3) arm inorder name dial addr path_on="" path_off=""
  PL_WARMUP="${K6_PL_REG_WARMUP:-20s}"; PL_DURATION="${K6_PL_REG_DURATION:-45s}"
  local conns="${PERF_PIPELINING_CONNECTIONS:-8}" depth="${PERF_PIPELINING_DEPTH:-16}"
  local load_warmup="${PERF_PIPELINING_LOAD_WARMUP_S:-10}" load_duration="${PERF_PIPELINING_LOAD_DURATION_S:-30}"
  local load_bound; load_bound="$(awk -v w="$load_warmup" -v d="$load_duration" 'BEGIN { printf "%d", w + d + 60 }')"
  local order; order="$(pl_arm_order "${BUILDKITE_BUILD_NUMBER:-0}")"
  if [ -n "${K6_CPUS:-}" ] && command -v taskset >/dev/null 2>&1; then runner=(taskset -c "$K6_CPUS" python3); fi
  rm -f "$OUT_DIR"/pl-{reg,load,probe}-{on,off}.json
  echo "--- pipelining A/B: arms '$order' on cpus=${SERVER_CPUS:-unpinned} mem=$SERVER_MEMORY; regression.js match+forward ${PL_WARMUP}+${PL_DURATION}; pipelined ${conns}x${depth} ${load_warmup}s+${load_duration}s"
  for arm in $order; do
    inorder=true; [ "$arm" = off ] && inorder=false
    name="$PL_SUT_PREFIX-$arm"
    # shellcheck disable=SC2034  # read by perf-test-run.sh's start_mockserver
    START_EXTRA_ENV=(-e "MOCKSERVER_HTTP1_PIPELINING_IN_ORDER=$inorder")
    start_mockserver "$name" "$SERVER_CPUS" "pipelining-$arm" "publish" "$SERVER_MEMORY" "" "ERROR" "" "" 1080 \
      || echo "WARNING: pipelining A/B: the $arm SUT did not start" >&2
    # shellcheck disable=SC2034
    START_EXTRA_ENV=()
    if wait_ready "$name"; then
      dial="$(pl_dial_addr "$name")" || dial=""; addr="${dial% *}"
      if [ "$arm" = on ]; then path_on="${dial#* }"; else path_off="${dial#* }"; fi
      if [ -n "$addr" ]; then
        pl_bounded 30 "${runner[@]}" "$client" probe --target "$addr" --out "$OUT_DIR/pl-probe-$arm.json" \
          || echo "WARNING: pipelining A/B: $arm probe failed" >&2
      fi
      pl_run_regression "$arm" "http://pipelining-$arm:1080" "pl-reg-$arm.json" \
        || echo "WARNING: pipelining A/B: $arm regression.js failed" >&2
      if [ -n "$addr" ]; then
        pl_bounded "$load_bound" "${runner[@]}" "$client" load --target "$addr" --proto "pipelining_$arm" \
          --connections "$conns" --depth "$depth" --warmup-s "$load_warmup" --duration-s "$load_duration" \
          --out "$OUT_DIR/pl-load-$arm.json" || echo "WARNING: pipelining A/B: $arm pipelined load failed" >&2
      fi
    fi
    docker rm -f "$name" >/dev/null 2>&1 || true
  done
  PIPELINING_JSON="$(pl_assemble_json "$OUT_DIR" "${order// /,}" "$path_on" "$path_off" "$conns" "$depth")" || PIPELINING_JSON=""
  jq -e . >/dev/null 2>&1 <<<"$PIPELINING_JSON" || PIPELINING_JSON='{"attempted":true,"measured":false,"problems":["result assembly failed"],"arms":{}}'
  echo "--- pipelining A/B result: $(jq -c '{measured, problems, arms: (.arms | with_entries(.value |= {p95_ratio, p99_ratio, throughput_ratio}))}' <<<"$PIPELINING_JSON")"
}
