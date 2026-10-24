#!/usr/bin/env bash
# Docker-free checks for the FILE-body A/B: lib/perf-file-body-ab.sh's arm order, body files, result
# assembly and fail-closed verdict, its orchestration against stubbed docker, the wiring in
# perf-test-run.sh, and the compare step's notify-only rows and annotation.
# Run: .buildkite/scripts/test/perf-file-body-ab-test.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env
LIB="$REPO_ROOT/.buildkite/scripts/steps/lib/perf-file-body-ab.sh"
RUN="$REPO_ROOT/.buildkite/scripts/steps/perf-test-run.sh"
COMPARE="$REPO_ROOT/.buildkite/scripts/steps/perf-test-compare.sh"
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
WORK="$(mktemp -d "${TMPDIR:-/tmp}/perf-file-body-ab-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
# shellcheck source=../steps/lib/perf-file-body-ab.sh
. "$LIB"

arm() { # p50 p95 p99 rps error_rate window_index -> one file_body_ab.js arm, its window 37 s after the last
  jq -nc --argjson p50 "$1" --argjson p95 "$2" --argjson p99 "$3" --argjson rps "$4" --argjson e "$5" --argjson w "$6" \
    '{p50_ms: $p50, p95_ms: $p95, p99_ms: $p99, sample_count: 6000, throughput_rps: $rps, offered_rps: 200,
      dropped_iterations: 0, error_rate: $e, settle_excluded: 1000, measured_window_s: 30,
      first_response_ms: (1800000027000 + $w * 37000), last_response_ms: (1800000057000 + $w * 37000)}'
}
genuine() { # file -> a file_body_ab.js result with FILE slower than inline at both sizes
  jq -nc --argjson f1 "$(arm 0.8 1.2 2.4 200 0 0)" --argjson i1 "$(arm 0.6 1.0 2.0 200 0 1)" \
    --argjson f64 "$(arm 1.5 3 6 199 0 2)" --argjson i64 "$(arm 1 2 4 200 0 3)" \
    '{order: ["file", "inline"],
      sizes: {"1kb": {bytes: 1024, file: $f1, inline: $i1}, "64kb": {bytes: 65536, file: $f64, inline: $i64}},
      probe: {ok: true, sizes: {}}, config: {rate_rps: 200}}' > "$1"
}

echo "--- 1. arm order alternates with the build number"
check "even build: file first" "file inline" "$(fb_arm_order 12)"
check "odd build: inline first" "inline file" "$(fb_arm_order 7)"
check "no build number: file first" "file inline" "$(fb_arm_order "")"
check "garbage build number: file first" "file inline" "$(fb_arm_order abc)"

echo "--- 2. body files: exact sizes, the shared generator, malformed sizes refused"
mkdir -p "$WORK/files"
fb_write_body_files "$WORK/files" "1kb,64kb"
check "1kb is 1024 bytes" "1024" "$(wc -c < "$WORK/files/fb-ab-1kb.json" | tr -d ' ')"
check "64kb is 65536 bytes" "65536" "$(wc -c < "$WORK/files/fb-ab-64kb.json" | tr -d ' ')"
check "valid JSON carrying its marker" "fb-ab-64kb" "$(jq -r .marker "$WORK/files/fb-ab-64kb.json")"
check "world-readable for the SUT's non-root user" "2" "$(find "$WORK/files" -name 'fb-ab-*.json' -perm -0444 | wc -l | tr -d ' ')"
for s in 0kb 1mb 64 "1kb,*" ""; do
  check "size list '$s' refused" "1" "$(fb_write_body_files "$WORK/files" "$s" 2>/dev/null && echo 0 || echo 1)"
done

echo "--- 3. a genuine A/B: file / inline ratios per size, measured"
genuine "$WORK/genuine.json"
G="$(fb_assemble_json "$WORK/genuine.json" 0 "file,inline" "1kb,64kb")"
check "measured, no problems" "true|0" "$(jq -r '"\(.measured)|\(.problems | length)"' <<<"$G")"
check "sizes" "1kb,64kb" "$(jq -r '.sizes | keys | join(",")' <<<"$G")"
check "1kb p95 ratio is file / inline" "1.2" "$(jq -r '.sizes["1kb"].p95_ratio' <<<"$G")"
check "64kb p99 ratio" "1.5" "$(jq -r '.sizes["64kb"].p99_ratio' <<<"$G")"
check "64kb throughput ratio" "0.995" "$(jq -r '.sizes["64kb"].throughput_ratio' <<<"$G")"
check "absolutes for both arms" "3|2|199|200|65536" "$(jq -r '.sizes["64kb"] | "\(.p95_file_ms)|\(.p95_inline_ms)|\(.throughput_file_rps)|\(.throughput_inline_rps)|\(.bytes)"' <<<"$G")"
check "each arm's measured window is recorded" "1800000101000|1800000168000" "$(jq -r '.sizes["64kb"] | "\(.window_file_ms[0])|\(.window_inline_ms[1])"' <<<"$G")"
check "arm order and probe recorded" "file,inline|true" "$(jq -r '"\(.arm_order)|\(.probe.ok)"' <<<"$G")"

echo "--- 4. fail closed: any failure or error over 1% publishes no ratio"
unmeasured() { # name expected_problem_regex json
  check "$1: not measured" "false" "$(jq -r '.measured' <<<"$3")"
  check "$1: no ratio anywhere" "0" "$(jq -r '[.sizes[] | to_entries[] | select(.key | endswith("_ratio"))] | length' <<<"$3")"
  check "$1: the problem is named" "1" "$(jq -r --arg re "$2" '[.problems[] | select(test($re))] | length' <<<"$3")"
}
unmeasured "k6 exited 107" "exited 107, so its setup probe failed" "$(fb_assemble_json "$WORK/genuine.json" 107 "file,inline" "1kb,64kb")"
unmeasured "SUT never ready" "did not become ready" "$(fb_assemble_json "$WORK/none.json" sut_not_ready "file,inline" "1kb,64kb")"
unmeasured "no body files" "could not be written" "$(fb_assemble_json "$WORK/none.json" no_files "file,inline" "1kb")"
unmeasured "no result file" "wrote no result" "$(fb_assemble_json "$WORK/none.json" 0 "file,inline" "1kb,64kb")"
jq '.probe = null' "$WORK/genuine.json" > "$WORK/noprobe.json"
unmeasured "no probe" "did not prove the FILE and inline bodies identical" "$(fb_assemble_json "$WORK/noprobe.json" 0 "file,inline" "1kb,64kb")"
unmeasured "k6 given the wrong order" "given the arm order file,inline, not inline,file" "$(fb_assemble_json "$WORK/genuine.json" 0 "inline,file" "1kb,64kb")"
jq '.sizes["1kb"].inline.first_response_ms = .sizes["1kb"].file.last_response_ms' "$WORK/genuine.json" > "$WORK/overlap.json"
unmeasured "overlapping windows" "the 1kb inline window did not start after the 1kb file window ended" "$(fb_assemble_json "$WORK/overlap.json" 0 "file,inline" "1kb,64kb")"
jq '.sizes["1kb"].file as $f | .sizes["1kb"].file = .sizes["1kb"].inline | .sizes["1kb"].inline = $f' "$WORK/genuine.json" > "$WORK/swapped.json"
unmeasured "windows that ran in the other order, though k6 reports the requested one" "the 1kb inline window did not start after the 1kb file window ended" \
  "$(fb_assemble_json "$WORK/swapped.json" 0 "file,inline" "1kb,64kb")"
jq '.sizes["64kb"] as $s | .sizes["64kb"].file.first_response_ms = 1 | .sizes["64kb"].file.last_response_ms = 2' "$WORK/genuine.json" > "$WORK/early.json"
unmeasured "a 64kb window before the 1kb ones" "the 64kb file window did not start after the 1kb inline window ended" "$(fb_assemble_json "$WORK/early.json" 0 "file,inline" "1kb,64kb")"
jq '.sizes["1kb"].file.last_response_ms = null' "$WORK/genuine.json" > "$WORK/notimes.json"
unmeasured "no response times" "size 1kb: the file arm has no usable p95, p99, throughput or response times" "$(fb_assemble_json "$WORK/notimes.json" 0 "file,inline" "1kb,64kb")"
jq '.sizes["64kb"].file = null' "$WORK/genuine.json" > "$WORK/nofile.json"
unmeasured "one arm missing" "size 64kb: the file arm produced no result" "$(fb_assemble_json "$WORK/nofile.json" 0 "file,inline" "1kb,64kb")"
unmeasured "a requested size missing" "size 4kb: the file arm produced no result" "$(fb_assemble_json "$WORK/genuine.json" 0 "file,inline" "1kb,4kb")"
jq '.sizes["1kb"].inline.p99_ms = null' "$WORK/genuine.json" > "$WORK/nop99.json"
unmeasured "a low-sample arm (null p99)" "size 1kb: the inline arm has no usable" "$(fb_assemble_json "$WORK/nop99.json" 0 "file,inline" "1kb,64kb")"
jq '.sizes["1kb"].file.throughput_rps = 0' "$WORK/genuine.json" > "$WORK/zero.json"
unmeasured "zero throughput" "size 1kb: the file arm has no usable" "$(fb_assemble_json "$WORK/zero.json" 0 "file,inline" "1kb,64kb")"
jq '.sizes["64kb"].file.error_rate = 0.02' "$WORK/genuine.json" > "$WORK/err.json"
unmeasured "2% errors on one arm" "size 64kb: the file arm failed 0.02 of its requests" "$(fb_assemble_json "$WORK/err.json" 0 "file,inline" "1kb,64kb")"
jq '.sizes["64kb"].file.error_rate = -0.5' "$WORK/genuine.json" > "$WORK/errneg.json"
unmeasured "a negative error rate" "size 64kb: the file arm failed -0.5" "$(fb_assemble_json "$WORK/errneg.json" 0 "file,inline" "1kb,64kb")"
jq '.sizes["64kb"].inline.error_rate = null' "$WORK/genuine.json" > "$WORK/errnull.json"
unmeasured "an unknown error rate" "size 64kb: the inline arm failed an unknown share" "$(fb_assemble_json "$WORK/errnull.json" 0 "file,inline" "1kb,64kb")"
jq '.sizes |= map_values(.file.error_rate = 0.01 | .inline.error_rate = 0.01)' "$WORK/genuine.json" > "$WORK/onepct.json"
check "exactly 1% errors is within the bound" "true" "$(fb_assemble_json "$WORK/onepct.json" 0 "file,inline" "1kb,64kb" | jq -r '.measured')"
check "the absolutes stay for diagnosis when not measured" "1.2" "$(fb_assemble_json "$WORK/err.json" 0 "file,inline" "1kb,64kb" | jq -r '.sizes["1kb"].p95_file_ms')"
check "no sizes requested: not measured" "false|1" \
  "$(fb_assemble_json "$WORK/genuine.json" 0 "file,inline" "" | jq -r '"\(.measured)|\([.problems[] | select(test("no sizes were requested"))] | length)"')"
echo 'not json' > "$WORK/garbage.json"
unmeasured "an unparseable result" "wrote no result" "$(fb_assemble_json "$WORK/garbage.json" 0 "file,inline" "1kb,64kb")"

echo "--- 5. fb_ab_phase and run_file_body_ab against stubbed docker"
mkdir -p "$WORK/bin" "$WORK/out" "$WORK/mount"
cat > "$WORK/bin/docker" <<'STUB'
#!/usr/bin/env bash
echo "docker $*" >> "$STUB_LOG"
if [ "$1" = run ]; then
  sizes="" order="" dir=""
  for a in "$@"; do case "$a" in K6_FB_SIZES=*) sizes="${a#*=}" ;; K6_FB_ORDER=*) order="${a#*=}" ;; K6_FB_FILE_DIR=*) dir="${a#*=}" ;; esac; done
  [ -n "${STUB_K6_EXIT:-}" ] && exit "$STUB_K6_EXIT"
  jq -nc --arg sizes "$sizes" --arg order "$order" \
    '{p50_ms: 1, p95_ms: 2, p99_ms: 3, sample_count: 6000, throughput_rps: 200, offered_rps: 200, dropped_iterations: 0, error_rate: 0} as $a
     | ($order | split(",")) as $o
     | {order: $o, probe: {ok: true},
        sizes: ([$sizes | split(",") | to_entries[] | .key as $k | {key: .value, value: ({bytes: 1}
          + ([$o | to_entries[] | {key: .value, value: ($a + {first_response_ms: (1000 + ($k * 2 + .key) * 100), last_response_ms: (1050 + ($k * 2 + .key) * 100)})}] | from_entries))}] | from_entries)}' > "$OUT_DIR/fb-ab.json"
  echo "k6 file dir $dir" >> "$STUB_LOG"
fi
exit 0
STUB
chmod +x "$WORK/bin/docker"
phase() { # build_number [VAR=value...] -> "<attempted>|<skip_reason>|<measured>"; logs in $WORK/stub.log
  : > "$WORK/stub.log"
  local build="$1"; shift
  (
    export PATH="$WORK/bin:$PATH" STUB_LOG="$WORK/stub.log" OUT_DIR="$WORK/out"
    for kv in "$@"; do export "${kv?}"; done
    # shellcheck disable=SC2034  # globals the sourced lib reads
    REPO_ROOT=/repo NETWORK=net K6_IMAGE=k6 K6_CPUS="" SERVER_CPUS=0-5 SERVER_MEMORY=2g
    # shellcheck disable=SC2034
    FILE_BODY_DIR="$WORK/mount" FILE_BODY_MOUNT="$WORK/mount:/perf-files:ro" FILE_BODY_CONTAINER_PATH=/perf-files/large-file-body.json
    # shellcheck disable=SC2034
    BUILDKITE_BUILD_NUMBER="$build"
    cpuset_arg() { :; }
    arm_only() { [ -n "${PERF_RUN_ARM:-}" ]; }
    wait_ready() { echo "wait_ready $1" >> "$STUB_LOG"; [ -z "${STUB_NOT_READY:-}" ]; }
    start_mockserver() { echo "start_mockserver $1 cpus=$2 alias=$3 publish=$4 mem=$5 mount=$6 level=$7 port=${10} args=$#" >> "$STUB_LOG"; }
    fb_ab_phase >/dev/null
    echo "$FILE_BODY_AB_ATTEMPTED|$FILE_BODY_AB_SKIP_REASON|$(jq -r 'if .measured == null then "none" else (.measured | tostring) end' <<<"$FILE_BODY_AB_JSON")"
  )
}
check "default: attempted and measured" "true||true" "$(phase 4)"
check "one SUT on the main SUT's cpuset, memory and read-only mount, ERROR level, the harness event-log budget" "1" \
  "$(grep -c "^start_mockserver mockserver-perf-fb-local cpus=0-5 alias=filebody-ab publish= mem=2g mount=$WORK/mount:/perf-files:ro level=ERROR port=1080 args=10$" "$WORK/stub.log" || true)"
check "the body files land in the SUT mount" "1024|65536" "$(wc -c < "$WORK/mount/fb-ab-1kb.json" | tr -d ' ')|$(wc -c < "$WORK/mount/fb-ab-64kb.json" | tr -d ' ')"
check "k6 dials the SUT by its alias and reads the mount's container path" "1|1" \
  "$(grep -c 'BASE_URL=http://filebody-ab:1080' "$WORK/stub.log" || true)|$(grep -c '^k6 file dir /perf-files$' "$WORK/stub.log" || true)"
check "even build: file first" "1" "$(grep -c 'K6_FB_ORDER=file,inline' "$WORK/stub.log" || true)"
check "the SUT is removed" "1" "$(grep -c '^docker rm -f mockserver-perf-fb-local$' "$WORK/stub.log" || true)"
phase 5 >/dev/null
check "odd build: inline first" "1" "$(grep -c 'K6_FB_ORDER=inline,file' "$WORK/stub.log" || true)"
check "odd build: measured, the windows in inline-first order" "true||true" "$(phase 5)"
check "sizes are overridable" "true||true" "$(phase 4 PERF_FILE_BODY_AB_SIZES=4kb)"
check "  ... and reach k6" "1" "$(grep -c 'K6_FB_SIZES=4kb' "$WORK/stub.log" || true)"
check "k6 failing: attempted, not measured, SUT still removed" "true||false|1" \
  "$(phase 4 STUB_K6_EXIT=107)|$(grep -c '^docker rm -f mockserver-perf-fb-local$' "$WORK/stub.log" || true)"
check "SUT never ready: not measured, no k6, SUT removed" "true||false|0|1" \
  "$(phase 4 STUB_NOT_READY=1)|$(grep -c '^docker run' "$WORK/stub.log" || true)|$(grep -c '^docker rm -f' "$WORK/stub.log" || true)"
check "a malformed size: not measured, no SUT started" "true||false|0" \
  "$(phase 4 PERF_FILE_BODY_AB_SIZES=1mb)|$(grep -c '^start_mockserver' "$WORK/stub.log" || true)"
check "PERF_FILE_BODY_AB=false: skipped as disabled" "false|disabled|none|0" \
  "$(phase 4 PERF_FILE_BODY_AB=false)|$(grep -c . "$WORK/stub.log" || true)"
check "an arm-only run: skipped as disabled" "false|disabled|none" "$(phase 4 PERF_RUN_ARM=rw_multik6)"
check "host network: skipped with its reason" "false|host_network|none" "$(phase 4 PERF_NETWORK_MODE=host)"

echo "--- 6. perf-test-run.sh wiring"
check "the lib is sourced" "1" "$(grep -c '^\. "\$SCRIPT_DIR/lib/perf-file-body-ab.sh"$' "$RUN" || true)"
check "the phase runs once, after a liveness check" "1" "$(grep -B1 '^fb_ab_phase$' "$RUN" | grep -c '^abort_if_sut_died$' || true)"
check "cleanup removes the SUT" "1" "$(grep -c '^  declare -F fb_ab_cleanup >/dev/null && fb_ab_cleanup$' "$RUN" || true)"
check "the block is not a validity check" "0" "$(grep -c 'add_check "file_body' "$RUN" || true)"
check "the result carries the block" "3" "$(grep -cE '^    file_body(_attempted|_skip_reason)?: \$file_body' "$RUN" || true)"
check "the allocation profile turns it off" "1" "$(grep -c '^PERF_FILE_BODY_AB=false \\$' "$REPO_ROOT/.buildkite/scripts/steps/perf-test-allocprofile.sh" || true)"

echo "--- 7. compare: notify-only rows when measured, an annotation when not"
# compare needs bash 4+ (mapfile). macOS ships 3.2, so off-CI that part skips loudly; in CI it fails.
if [ "$(bash -c 'echo "${BASH_VERSINFO[0]}"')" -lt 4 ]; then
  if [ "${BUILDKITE:-}" = "true" ]; then echo "FAIL: bash on PATH is older than 4; perf-test-compare.sh cannot run" >&2; exit 1; fi
  echo "  SKIP: bash on PATH is older than 4 (perf-test-compare.sh needs mapfile)"
else
  B="$WORK/bucket"; mkdir -p "$WORK/cbin" "$B"
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
  result() { # timestamp file_body_json attempted skip_reason
    jq -nc --arg ts "$1" --argjson fb "$2" --argjson att "$3" --arg why "${4:-}" '{
      schema_version: 3, commit: "0123456789abcdef0123456789abcdef01234567", branch: "master", timestamp_utc: $ts,
      agent: {instance_type: "c5.12xlarge", instance_type_source: "observed", queue: "perf"},
      config: {image_stale: false, config_profile: "default", rig_profile: "default", jvm_diagnostics: "standard"},
      validity: {valid: true, checks: [{name: "fixture", ok: true, detail: ""}]}, baseline_eligible: true,
      behaviours: {match: {p95_ms: 1.5}},
      file_body: $fb, file_body_attempted: $att, file_body_skip_reason: $why}'
  }
  for i in 1 2 3 4 5 6; do
    mkdir -p "$B/runs/master"; result "2026-09-0${i}T04:00:00Z" "$G" true > "$B/runs/master/2026-09-0${i}T04-00-00Z__000000000${i}.json"
  done
  compare() { # name head_file
    local rc=0
    env -i PATH="$WORK/cbin:$PATH" HOME="${HOME:-/tmp}" TMPDIR="$WORK" FAKE_BUCKET="$B" HEAD_RESULT="$2" \
      PERF_BUDGETS_COMMIT=fixture bash "$COMPARE" >"$WORK/$1.log" 2>&1 || rc=$?
    echo "$rc"
  }
  result 2026-10-01T04:00:00Z "$G" true > "$WORK/head-measured.json"
  check "measured: exit 0" "0" "$(compare measured "$WORK/head-measured.json")"
  check "measured: every metric has a budget" "0" "$(grep -c 'Perf budget INCOMPLETE' "$WORK/measured.log" || true)"
  check "measured: 12 rows per size, 2 sizes" "24" "$(grep -cE '^\| file_body\.(1kb|64kb)\.' "$WORK/measured.log" || true)"
  check "measured: the ratio rows are compared and notify-only" "1" \
    "$(grep -cE '^\| file_body\.64kb\.p99_ratio \| 1\.5 \| 1\.5 \| [0-9.]+ \| notify-only \| :white_check_mark: ok \|$' "$WORK/measured.log" || true)"
  check "measured: the reading note is shown" "1" "$(grep -c 'FILE-body A/B:\*\*' "$WORK/measured.log" || true)"
  result 2026-10-01T05:00:00Z "$(fb_assemble_json "$WORK/err.json" 0 "file,inline" "1kb,64kb")" true > "$WORK/head-unmeasured.json"
  check "not measured: exit 0" "0" "$(compare unmeasured "$WORK/head-unmeasured.json")"
  check "not measured: no rows, not even the absolutes" "0" "$(grep -c '^| file_body\.' "$WORK/unmeasured.log" || true)"
  check "not measured: a warning names the problem" "1" "$(grep -c 'FILE-body A/B did NOT measure this build.*size 64kb: the file arm failed 0.02' "$WORK/unmeasured.log" || true)"
  result 2026-10-01T06:00:00Z '{}' false disabled > "$WORK/head-disabled.json"
  check "disabled: exit 0" "0" "$(compare disabled "$WORK/head-disabled.json")"
  check "disabled: no rows and no note" "0|0" \
    "$(grep -c '^| file_body\.' "$WORK/disabled.log" || true)|$(grep -c 'FILE-body A/B' "$WORK/disabled.log" || true)"
  result 2026-10-01T07:00:00Z '{}' false host_network > "$WORK/head-host.json"
  check "host network: exit 0, the skip is noted" "0|1" \
    "$(compare host "$WORK/head-host.json")|$(grep -c 'FILE-body A/B skipped.*host_network' "$WORK/host.log" || true)"
fi

if [ "$FAILS" -gt 0 ]; then echo "FAILED: $FAILS check(s)" >&2; exit 1; fi
echo "all FILE-body A/B checks passed"
