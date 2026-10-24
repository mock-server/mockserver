#!/usr/bin/env bash
# FILE response body A/B, sourced by perf-test-run.sh. One fresh SUT from the run's image, on the main
# SUT's cpuset and memory with the read-only /perf-files mount; k6/file_body_ab.js measures, per size,
# a FILE body and the identical inline STRING body back to back. The metric is the file / inline ratio.
# Notify-only and kept out of the run's validity block. Defines functions only.
# Docs: docs/code/performance-measurement.md. Tests: .buildkite/scripts/test/perf-file-body-ab-test.sh

FB_SUT="mockserver-perf-fb-${RUN_ID:-local}"
FB_ALIAS="filebody-ab"
FB_CONTENT_AWK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/perf-file-body-ab-content.awk"

# Arm order alternates with the build number so a drift across the phase does not favour one arm.
fb_arm_order() { # build_number
  local n="${1:-0}"
  [[ "$n" =~ ^[0-9]+$ ]] || n=0
  if [ $(( n % 2 )) -eq 0 ]; then echo "file inline"; else echo "inline file"; fi
}

# Writes <dir>/fb-ab-<size>.json for each <n>kb in the comma-separated sizes; fails on a malformed size.
fb_write_body_files() { # dir sizes
  local size list
  IFS=',' read -ra list <<<"$2"
  [ "${#list[@]}" -gt 0 ] || { echo "WARNING: FILE-body A/B: no sizes" >&2; return 1; }
  for size in "${list[@]}"; do
    [[ "$size" =~ ^[1-9][0-9]{0,4}kb$ ]] || { echo "WARNING: FILE-body A/B: size '$size' is not <n>kb" >&2; return 1; }
    awk -v label="$size" -v bytes="$(( ${size%kb} * 1024 ))" -f "$FB_CONTENT_AWK" > "$1/fb-ab-$size.json" || return 1
    chmod 0644 "$1/fb-ab-$size.json" || return 1
  done
}

# The .file_body block from file_body_ab.js's result. measured is true only when k6 exited 0, its setup
# probe proved every size's FILE and inline bodies identical, the measured windows ran one after another
# in the requested order (by their first and last response times), and
# every arm of every requested size has a p95, a p99 and a throughput with at most 1% errors. Only then
# are ratios (file / inline) published.
fb_assemble_json() { # result_file status arm_order sizes
  local r; r="$(jq -ce . "$1" 2>/dev/null)" || r='{}'
  jq -nc --argjson r "$r" --arg status "$2" --arg arm_order "$3" --arg sizes "$4" '
    def ratio(a; b): if a != null and (b // 0) > 0 then ((a / b) * 1000 | round) / 1000 else null end;
    def usable(v): v != null and v > 0;
    ($sizes | split(",") | map(select(. != ""))) as $want
    | [ $want[] as $s | ($arm_order | split(","))[] as $arm | ($r.sizes[$s][$arm] // {}) | {s: $s, arm: $arm, first: .first_response_ms, last: .last_response_ms} ] as $seq
    | ([ (if ($want | length) == 0 then "no sizes were requested" else empty end),
         (if $status == "0" then empty
          elif $status == "sut_not_ready" then "the A/B SUT did not become ready, so nothing was measured"
          elif $status == "no_files" then "the body files could not be written into the SUT mount (sizes: \($sizes))"
          else "file_body_ab.js exited \($status), so its setup probe failed or the run aborted (see the step log)" end),
         (if $r == {} then "file_body_ab.js wrote no result" else empty end),
         (if $r == {} or ($r.probe.ok // false) == true then empty
          else "the setup probe did not prove the FILE and inline bodies identical" end),
         (if $r == {} or (($r.order // []) | join(",")) == $arm_order then empty
          else "k6 was given the arm order \(($r.order // []) | join(",")), not \($arm_order)" end),
         (if $r == {} or ($seq | map(select(.first == null or .last == null)) | length) > 0 then empty
          else range(1; $seq | length) as $i | $seq[$i - 1] as $p | $seq[$i] as $n
          | if $p.last < $n.first then empty
            else "the \($n.s) \($n.arm) window did not start after the \($p.s) \($p.arm) window ended (first response \($n.first) ms, previous last \($p.last) ms)" end end),
         ($want[] as $s | ($r.sizes[$s] // {}) as $v | ("file", "inline") as $arm | ($v[$arm]) as $a
          | if $r == {} then empty
            elif $a == null then "size \($s): the \($arm) arm produced no result"
            elif (usable($a.p95_ms) and usable($a.p99_ms) and usable($a.throughput_rps)
                  and usable($a.first_response_ms) and usable($a.last_response_ms)) | not then
              "size \($s): the \($arm) arm has no usable p95, p99, throughput or response times (p95 \($a.p95_ms), p99 \($a.p99_ms), \($a.throughput_rps) rps, \($a.sample_count) samples)"
            elif ($a.error_rate // 1) > 0.01 or ($a.error_rate // 0) < 0 then
              "size \($s): the \($arm) arm failed \($a.error_rate // "an unknown share") of its requests (non-200 or a body of the wrong length; over the 1% bound)"
            else empty end) ]) as $problems
    | (($problems | length) == 0) as $measured
    | { attempted: true, measured: $measured, problems: $problems,
        ratio: "FILE body / inline STRING body", arm_order: $arm_order,
        sizes: ([ $want[] as $s | ($r.sizes[$s] // {}) as $v | ($v.file // {}) as $f | ($v.inline // {}) as $i
          | select($v != {})
          | {key: $s, value: ({
              bytes: $v.bytes,
              p50_file_ms: $f.p50_ms, p50_inline_ms: $i.p50_ms,
              p95_file_ms: $f.p95_ms, p95_inline_ms: $i.p95_ms,
              p99_file_ms: $f.p99_ms, p99_inline_ms: $i.p99_ms,
              throughput_file_rps: $f.throughput_rps, throughput_inline_rps: $i.throughput_rps,
              error_rate_file: $f.error_rate, error_rate_inline: $i.error_rate,
              sample_count_file: $f.sample_count, sample_count_inline: $i.sample_count,
              dropped_iterations_file: $f.dropped_iterations, dropped_iterations_inline: $i.dropped_iterations,
              offered_rps: ($f.offered_rps // $i.offered_rps),
              window_file_ms: [$f.first_response_ms, $f.last_response_ms],
              window_inline_ms: [$i.first_response_ms, $i.last_response_ms] }
            + (if $measured then {
                p50_ratio: ratio($f.p50_ms; $i.p50_ms), p95_ratio: ratio($f.p95_ms; $i.p95_ms),
                p99_ratio: ratio($f.p99_ms; $i.p99_ms), throughput_ratio: ratio($f.throughput_rps; $i.throughput_rps) }
               else {} end)) } ] | from_entries),
        probe: ($r.probe // null), config: ($r.config // null) }'
}

# Starts the SUT, writes the body files, runs file_body_ab.js and sets FILE_BODY_AB_JSON. Needs
# perf-test-run.sh's start_mockserver, wait_ready, cpuset_arg, FILE_BODY_DIR / FILE_BODY_MOUNT /
# FILE_BODY_CONTAINER_PATH and run globals.
run_file_body_ab() {
  local sizes="${PERF_FILE_BODY_AB_SIZES:-1kb,64kb}" status=0 order
  order="$(fb_arm_order "${BUILDKITE_BUILD_NUMBER:-0}")"
  rm -f "$OUT_DIR/fb-ab.json"
  echo "--- FILE-body A/B: sizes $sizes, arms '$order' on cpus=${SERVER_CPUS:-unpinned} mem=$SERVER_MEMORY"
  if ! fb_write_body_files "$FILE_BODY_DIR" "$sizes"; then
    status=no_files
  else
    start_mockserver "$FB_SUT" "$SERVER_CPUS" "$FB_ALIAS" "" "$SERVER_MEMORY" "$FILE_BODY_MOUNT" "ERROR" "" "" 1080 \
      || echo "WARNING: FILE-body A/B: the SUT did not start" >&2
    if wait_ready "$FB_SUT"; then
      # shellcheck disable=SC2046
      docker run --rm --network "$NETWORK" $(cpuset_arg "$K6_CPUS") \
        -v "$REPO_ROOT/mockserver-performance-test/k6:/k6:ro" -v "$OUT_DIR:/out" \
        -e "BASE_URL=http://$FB_ALIAS:1080" -e "K6_RESULT_PATH=/out/fb-ab.json" \
        -e "K6_FB_SIZES=$sizes" -e "K6_FB_ORDER=${order// /,}" -e "K6_FB_FILE_DIR=${FILE_BODY_CONTAINER_PATH%/*}" \
        ${K6_FB_RATE:+-e K6_FB_RATE="$K6_FB_RATE"} ${K6_FB_DURATION:+-e K6_FB_DURATION="$K6_FB_DURATION"} \
        ${K6_FB_WARMUP:+-e K6_FB_WARMUP="$K6_FB_WARMUP"} \
        "$K6_IMAGE" run /k6/file_body_ab.js > "$OUT_DIR/fb-ab-k6.log" 2>&1 || status=$?
      [ "$status" = 0 ] || { echo "WARNING: FILE-body A/B: file_body_ab.js exited $status; its last lines:" >&2; tail -15 "$OUT_DIR/fb-ab-k6.log" >&2 || true; }
    else
      status=sut_not_ready
    fi
    docker rm -f "$FB_SUT" >/dev/null 2>&1 || true
  fi
  FILE_BODY_AB_JSON="$(fb_assemble_json "$OUT_DIR/fb-ab.json" "$status" "${order// /,}" "$sizes")" || FILE_BODY_AB_JSON=""
  jq -e . >/dev/null 2>&1 <<<"$FILE_BODY_AB_JSON" || FILE_BODY_AB_JSON='{"attempted":true,"measured":false,"problems":["result assembly failed"],"sizes":{}}'
  echo "--- FILE-body A/B result: $(jq -c '{measured, problems, sizes: (.sizes | with_entries(.value |= {p95_ratio, p99_ratio, throughput_ratio}))}' <<<"$FILE_BODY_AB_JSON")"
}

# The phase hook: sets FILE_BODY_AB_JSON, FILE_BODY_AB_ATTEMPTED and FILE_BODY_AB_SKIP_REASON
# (disabled: PERF_FILE_BODY_AB is not true, or an arm-only run; host_network: the SUT needs the bridge).
# shellcheck disable=SC2034  # the FILE_BODY_AB_* globals are read by perf-test-run.sh's result assembly
fb_ab_phase() {
  FILE_BODY_AB_JSON='{}'; FILE_BODY_AB_ATTEMPTED=false; FILE_BODY_AB_SKIP_REASON=disabled
  if [ "${PERF_FILE_BODY_AB:-true}" != true ] || { declare -F arm_only >/dev/null && arm_only; }; then return 0; fi
  if [ "${PERF_NETWORK_MODE:-bridge}" = host ]; then
    FILE_BODY_AB_SKIP_REASON=host_network
    echo "--- FILE-body A/B skipped: PERF_NETWORK_MODE=host (its SUT is reached by bridge alias)"
    return 0
  fi
  FILE_BODY_AB_ATTEMPTED=true; FILE_BODY_AB_SKIP_REASON=""
  local t0; t0="$(date -u +%s)"
  run_file_body_ab
  echo "--- FILE-body A/B done; added wall-clock $(( $(date -u +%s) - t0 ))s"
}

fb_ab_cleanup() { docker rm -f "$FB_SUT" >/dev/null 2>&1 || true; }
