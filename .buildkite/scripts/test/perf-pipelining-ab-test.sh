#!/usr/bin/env bash
# Docker-free checks for the in-order HTTP/1.1 pipelining A/B: lib/perf-pipelining-ab.sh's arm order,
# result assembly and genuineness verdict, its orchestration against stubbed docker / python3 / curl,
# the wiring in perf-test-run.sh, and the compare step's notify-only rows and annotation.
# Run: .buildkite/scripts/test/perf-pipelining-ab-test.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env
LIB="$REPO_ROOT/.buildkite/scripts/steps/lib/perf-pipelining-ab.sh"
RUN="$REPO_ROOT/.buildkite/scripts/steps/perf-test-run.sh"
COMPARE="$REPO_ROOT/.buildkite/scripts/steps/perf-test-compare.sh"
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
WORK="$(mktemp -d "${TMPDIR:-/tmp}/perf-pipelining-ab-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
# shellcheck source=../steps/lib/perf-pipelining-ab.sh
. "$LIB"

behaviour() { # proto p50 p95 p99 rps -> one regression.js-shaped behaviours file body for match + forward
  jq -nc --arg p "$1" --argjson p50 "$2" --argjson p95 "$3" --argjson p99 "$4" --argjson rps "$5" \
    '{proto: $p, behaviours: {("match_" + $p): {p50_ms: $p50, p95_ms: $p95, p99_ms: $p99, throughput_rps: $rps, error_rate: 0, sample_count: 7000},
                              ("forward_" + $p): {p50_ms: $p50, p95_ms: $p95, p99_ms: $p99, throughput_rps: $rps, error_rate: 0, sample_count: 7000}}}'
}
pipelined() { # proto p95 rps
  jq -nc --arg p "$1" --argjson p95 "$2" --argjson rps "$3" \
    '{mode: "load", proto: $p, behaviours: {("pipelined_" + $p): {p50_ms: 1, p95_ms: $p95, p99_ms: ($p95 * 2), throughput_rps: $rps, error_rate: 0, sample_count: 900}}}'
}
probe() { # in_order(true|false)
  if [ "$1" = true ]; then echo '{"mode":"probe","ok":true,"first_body":"slow","second_body":"fast","in_order":true}'
  else echo '{"mode":"probe","ok":true,"first_body":"fast","second_body":"slow","in_order":false}'; fi
}
genuine() { # dir
  mkdir -p "$1"
  behaviour pipelining_on 1.1 2.2 4.0 200 > "$1/pl-reg-on.json"
  behaviour pipelining_off 1.0 2.0 4.0 200 > "$1/pl-reg-off.json"
  pipelined pipelining_on 3 18000 > "$1/pl-load-on.json"
  pipelined pipelining_off 2 24000 > "$1/pl-load-off.json"
  probe true > "$1/pl-probe-on.json"
  probe false > "$1/pl-probe-off.json"
}

echo "--- 1. arm order alternates with the build number"
check "even build: on first" "on off" "$(pl_arm_order 12)"
check "odd build: off first" "off on" "$(pl_arm_order 7)"
check "no build number: on first" "on off" "$(pl_arm_order "")"
check "garbage build number: on first" "on off" "$(pl_arm_order abc)"

echo "--- 2. a genuine A/B: three arms, on / off ratios, measured"
genuine "$WORK/genuine"
G="$(pl_assemble_json "$WORK/genuine" "on,off" bridge_ip bridge_ip 8 16)"
check "measured" "true" "$(jq -r '.measured' <<<"$G")"
check "no problems" "0" "$(jq -r '.problems | length' <<<"$G")"
check "arms" "forward,match,pipelined" "$(jq -r '.arms | keys | join(",")' <<<"$G")"
check "match p95 ratio is on / off" "1.1" "$(jq -r '.arms.match.p95_ratio' <<<"$G")"
check "match p99 ratio" "1" "$(jq -r '.arms.match.p99_ratio' <<<"$G")"
check "pipelined p95 ratio" "1.5" "$(jq -r '.arms.pipelined.p95_ratio' <<<"$G")"
check "pipelined throughput ratio" "0.75" "$(jq -r '.arms.pipelined.throughput_ratio' <<<"$G")"
check "absolutes for both arms" "3|2|18000|24000" "$(jq -r '.arms.pipelined | "\(.p95_on_ms)|\(.p95_off_ms)|\(.throughput_on_rps)|\(.throughput_off_rps)"' <<<"$G")"
check "only the pipelined arm is flagged pipelined" "pipelined" "$(jq -r '.arms | to_entries | map(select(.value.pipelined)) | map(.key) | join(",")' <<<"$G")"
check "client recorded" "bridge_ip|bridge_ip|8|16" "$(jq -r '.client | "\(.path_on)|\(.path_off)|\(.connections)|\(.depth)"' <<<"$G")"

echo "--- 3. the flag did not take effect: not measured"
genuine "$WORK/noflag"; probe true > "$WORK/noflag/pl-probe-off.json"
N="$(pl_assemble_json "$WORK/noflag" "on,off" bridge_ip bridge_ip 8 16)"
check "measured false" "false" "$(jq -r '.measured' <<<"$N")"
check "the problem names the flag" "1" "$(jq -r '[.problems[] | select(test("flag did not take effect"))] | length' <<<"$N")"
genuine "$WORK/noorder"; probe false > "$WORK/noorder/pl-probe-on.json"
check "the in-order arm answering out of order is not measured" "false" "$(pl_assemble_json "$WORK/noorder" "on,off" x x 8 16 | jq -r '.measured')"

echo "--- 4. nothing measured: no arms, every problem named"
mkdir -p "$WORK/empty"
E="$(pl_assemble_json "$WORK/empty" "on,off" "" "" 8 16)"
check "measured false, no arms, five problems (two probes, three arms)" "false|0|5" "$(jq -r '"\(.measured)|\(.arms | length)|\(.problems | length)"' <<<"$E")"
genuine "$WORK/oneside"; rm "$WORK/oneside/pl-reg-off.json" "$WORK/oneside/pl-load-off.json"
check "an arm measured on one side only gives no ratio" "false|0" "$(pl_assemble_json "$WORK/oneside" "on,off" x x 8 16 | jq -r '"\(.measured)|\(.arms | length)"')"

echo "--- 4b. the pipelining client failed on both SUTs: not measured, even with match and forward present"
genuine "$WORK/noclient"
echo '{"mode":"load","ok":false,"error":"seeding returned HTTP 500"}' > "$WORK/noclient/pl-load-on.json"
echo '{"mode":"load","ok":false,"error":"seeding returned HTTP 500"}' > "$WORK/noclient/pl-load-off.json"
C="$(pl_assemble_json "$WORK/noclient" "on,off" bridge_ip bridge_ip 8 16)"
check "measured false" "false" "$(jq -r '.measured' <<<"$C")"
check "the k6 arms still carry their ratios" "forward,match" "$(jq -r '.arms | keys | join(",")' <<<"$C")"
check "the one problem names the client and its error" "1" \
  "$(jq -r '[.problems[] | select(test("pipelining client produced no usable p95 on both sides .on: seeding returned HTTP 500, off: seeding returned HTTP 500"))] | length' <<<"$C")"
genuine "$WORK/oneclient"; rm "$WORK/oneclient/pl-load-off.json"
check "a client result on one side only: not measured, the missing side named" "false|1" \
  "$(pl_assemble_json "$WORK/oneclient" "on,off" x x 8 16 | jq -r '"\(.measured)|\([.problems[] | select(test("off: no result"))] | length)"')"
genuine "$WORK/noforward"; jq '.behaviours |= with_entries(select(.key | startswith("match")))' "$WORK/genuine/pl-reg-off.json" > "$WORK/noforward/pl-reg-off.json"
check "a missing k6 arm: not measured, the arm named" "false|1" \
  "$(pl_assemble_json "$WORK/noforward" "on,off" x x 8 16 | jq -r '"\(.measured)|\([.problems[] | select(test("arm forward produced no"))] | length)"')"
check "the arms dialled by different paths: not measured" "false|1" \
  "$(pl_assemble_json "$WORK/genuine" "on,off" bridge_ip published_port 8 16 | jq -r '"\(.measured)|\([.problems[] | select(test("different paths"))] | length)"')"

echo "--- 4c. every request failing still gives percentiles: not measured"
genuine "$WORK/allfail"
for f in pl-reg-on pl-reg-off pl-load-on pl-load-off; do
  jq '.behaviours |= map_values(.error_rate = 1)' "$WORK/genuine/$f.json" > "$WORK/allfail/$f.json"
done
F="$(pl_assemble_json "$WORK/allfail" "on,off" x x 8 16)"
check "all arms at 100% errors: not measured, though every arm has a p95 ratio" "false|3" \
  "$(jq -r '"\(.measured)|\([.arms[] | select(.p95_ratio != null)] | length)"' <<<"$F")"
check "  ... one problem per arm and side" "6" "$(jq -r '[.problems[] | select(test("over the 1% bound"))] | length' <<<"$F")"
genuine "$WORK/oneerr"; jq '.behaviours |= map_values(.error_rate = 0.02)' "$WORK/genuine/pl-load-off.json" > "$WORK/oneerr/pl-load-off.json"
check "one side of one arm over 1%: not measured, that side named" "false|1" \
  "$(pl_assemble_json "$WORK/oneerr" "on,off" x x 8 16 | jq -r '"\(.measured)|\([.problems[] | select(test("arm pipelined failed 0.02 of its requests with the flag off"))] | length)"')"
genuine "$WORK/lowerr"; jq '.behaviours |= map_values(.error_rate = 0.01)' "$WORK/genuine/pl-reg-on.json" > "$WORK/lowerr/pl-reg-on.json"
check "exactly 1% is within the bound" "true" "$(pl_assemble_json "$WORK/lowerr" "on,off" x x 8 16 | jq -r '.measured')"
genuine "$WORK/zerop95"; jq '.behaviours |= map_values(.p95_ms = 0)' "$WORK/genuine/pl-load-off.json" > "$WORK/zerop95/pl-load-off.json"
check "a zero p95 on one side is reported with the real values" "1" \
  "$(pl_assemble_json "$WORK/zerop95" "on,off" x x 8 16 | jq -r '[.problems[] | select(test("no usable p95 on both sides .on: p95 3, off: p95 0."))] | length')"

echo "--- 5. run_pipelining_ab against stubbed docker, curl and python3"
mkdir -p "$WORK/bin" "$WORK/out"
cat > "$WORK/bin/docker" <<'STUB'
#!/usr/bin/env bash
echo "docker $*" >> "$STUB_LOG"
case "$1" in
  inspect) echo "172.18.0.9 " ;;
  port) echo "127.0.0.1:55555" ;;
  run)
    proto="" out=""
    for a in "$@"; do case "$a" in PROTO=*) proto="${a#PROTO=}" ;; K6_RESULT_PATH=/out/*) out="${a#K6_RESULT_PATH=/out/}" ;; esac; done
    jq -nc --arg p "$proto" '{p50_ms: 1, p95_ms: 2, p99_ms: 3, throughput_rps: 200, error_rate: 0, sample_count: 7000} as $b
      | {proto: $p, behaviours: {("match_" + $p): $b, ("forward_" + $p): $b}}' > "$OUT_DIR/$out" ;;
esac
exit 0
STUB
cat > "$WORK/bin/curl" <<'STUB'
#!/usr/bin/env bash
printf 200
STUB
cat > "$WORK/bin/python3" <<'STUB'
#!/usr/bin/env bash
echo "python3 $*" >> "$STUB_LOG"
mode="$2" out="" proto=""
while [ $# -gt 0 ]; do case "$1" in --out) out="$2"; shift ;; --proto) proto="$2"; shift ;; esac; shift; done
case "$mode:$out" in
  probe:*-on.json) echo '{"ok":true,"in_order":true}' > "$out" ;;
  probe:*-off.json) echo '{"ok":true,"in_order":false}' > "$out" ;;
  load:*) jq -nc --arg p "$proto" '{proto: $p, behaviours: {("pipelined_" + $p): {p50_ms: 1, p95_ms: 2, p99_ms: 3, throughput_rps: 9000, error_rate: 0, sample_count: 900}}}' > "$out" ;;
esac
STUB
chmod +x "$WORK/bin/docker" "$WORK/bin/curl" "$WORK/bin/python3"
orchestrate() { # build_number -> PIPELINING_JSON; logs in $WORK/stub.log
  : > "$WORK/stub.log"
  (
    export PATH="$WORK/bin:$PATH" STUB_LOG="$WORK/stub.log" OUT_DIR="$WORK/out"
    # shellcheck disable=SC2034  # globals the sourced lib reads
    REPO_ROOT=/repo NETWORK=net K6_IMAGE=k6 K6_CPUS="" SERVER_CPUS=0-5 SERVER_MEMORY=2g PL_SUT_PREFIX=pl
    # shellcheck disable=SC2034
    BUILDKITE_BUILD_NUMBER="$1"
    START_EXTRA_ENV=()
    cpuset_arg() { :; }
    wait_ready() { echo "wait_ready $1" >> "$STUB_LOG"; [ "${STUB_NOT_READY:-}" != "$1" ]; }
    start_mockserver() { echo "start_mockserver $1 cpus=$2 alias=$3 mem=$5 env=${START_EXTRA_ENV[*]}" >> "$STUB_LOG"; }
    run_pipelining_ab >/dev/null
    echo "$PIPELINING_JSON"
  )
}
O="$(orchestrate 4)"
check "measured from the stubbed arms" "true" "$(jq -r '.measured' <<<"$O")"
check "arms from regression.js and the client" "forward,match,pipelined" "$(jq -r '.arms | keys | join(",")' <<<"$O")"
check "the on SUT is started with the flag on, on the main SUT cpuset" "1" \
  "$(grep -c '^start_mockserver pl-on cpus=0-5 alias=pipelining-on mem=2g env=-e MOCKSERVER_HTTP1_PIPELINING_IN_ORDER=true$' "$WORK/stub.log" || true)"
check "the off SUT is started with the flag off" "1" \
  "$(grep -c '^start_mockserver pl-off .*env=-e MOCKSERVER_HTTP1_PIPELINING_IN_ORDER=false$' "$WORK/stub.log" || true)"
check "even build: the on arm runs first" "pl-on pl-off" "$(awk '/^start_mockserver/ {print $2}' "$WORK/stub.log" | paste -sd' ' -)"
check "regression.js runs only match and forward" "2" "$(grep -c 'K6_REG_ARMS=match,forward' "$WORK/stub.log" || true)"
check "the client dials the bridge IP" "4" "$(grep -c '^python3 .*--target 172.18.0.9:1080' "$WORK/stub.log" || true)"
check "both SUTs are removed" "2" "$(grep -cE '^docker rm -f pl-(on|off)$' "$WORK/stub.log" || true)"
check "arm order recorded" "on,off" "$(jq -r '.arm_order' <<<"$O")"
O="$(orchestrate 5)"
check "odd build: the off arm runs first" "pl-off pl-on" "$(awk '/^start_mockserver/ {print $2}' "$WORK/stub.log" | paste -sd' ' -)"
check "odd build: still measured" "true" "$(jq -r '.measured' <<<"$O")"
O="$(STUB_NOT_READY=pl-off orchestrate 4)"
check "a SUT that never gets ready: not measured, no error, both SUTs still removed" "false|2" \
  "$(jq -r '.measured' <<<"$O")|$(grep -cE '^docker rm -f pl-(on|off)$' "$WORK/stub.log" || true)"
check "  ... and nothing was driven at it" "0" "$(grep -c 'pipelining_off' "$WORK/stub.log" || true)"

echo "--- 6. perf-test-run.sh wiring"
check "the arm is off in arm-only and host-network runs" "2" "$(grep -c 'PERF_PROXY_PROFILE=false; PERF_PIPELINING=false$' "$RUN" || true)"
check "the arm defaults off for a release comparison" "1" "$(grep -c 'PERF_PIPELINING="${PERF_PIPELINING:-false}"' "$RUN" || true)"
check "the allocation profile turns it off" "1" "$(grep -c '^PERF_PIPELINING=false \\$' "$REPO_ROOT/.buildkite/scripts/steps/perf-test-allocprofile.sh" || true)"
check "cleanup removes both SUTs" "1" "$(grep -c '"$PL_SUT_PREFIX-on" "$PL_SUT_PREFIX-off"' "$RUN" || true)"
check "the block is not a validity check" "0" "$(grep -c 'add_check "pipelining' "$RUN" || true)"
check "the result carries the block" "3" "$(grep -cE '^    pipelining_inorder(_attempted|_skip_reason)?: \$pipelining_inorder' "$RUN" || true)"

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
  result() { # timestamp pipelining_json attempted
    jq -nc --arg ts "$1" --argjson pl "$2" --argjson att "$3" '{
      schema_version: 3, commit: "0123456789abcdef0123456789abcdef01234567", branch: "master", timestamp_utc: $ts,
      agent: {instance_type: "c5.12xlarge", instance_type_source: "observed", queue: "perf"},
      config: {image_stale: false, config_profile: "default", rig_profile: "default", jvm_diagnostics: "standard"},
      validity: {valid: true, checks: [{name: "fixture", ok: true, detail: ""}]}, baseline_eligible: true,
      behaviours: {match: {p95_ms: 1.5}},
      pipelining_inorder: $pl, pipelining_inorder_attempted: $att, pipelining_inorder_skip_reason: ""}'
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
  check "measured: 12 rows per arm, 3 arms" "36" "$(grep -cE '^\| pipelining_inorder\.(match|forward|pipelined)\.' "$WORK/measured.log" || true)"
  check "measured: the ratio rows are compared and notify-only" "1" \
    "$(grep -cE '^\| pipelining_inorder\.match\.p95_ratio \| 1\.1 \| 1\.1 \| [0-9.]+ \| notify-only \| :white_check_mark: ok \|$' "$WORK/measured.log" || true)"
  check "measured: the reading note is shown" "1" "$(grep -c 'In-order pipelining A/B:\*\*' "$WORK/measured.log" || true)"
  result 2026-10-01T04:30:00Z "$C" true > "$WORK/head-noclient.json"
  check "client failed: exit 0" "0" "$(compare noclient "$WORK/head-noclient.json")"
  check "client failed: no rows, not even match or forward" "0" "$(grep -c '^| pipelining_inorder\.' "$WORK/noclient.log" || true)"
  check "client failed: a warning names the client" "1" "$(grep -c 'did NOT measure this build.*pipelining client produced no' "$WORK/noclient.log" || true)"
  result 2026-10-01T05:00:00Z "$N" true > "$WORK/head-unmeasured.json"
  check "not measured: exit 0" "0" "$(compare unmeasured "$WORK/head-unmeasured.json")"
  check "not measured: no rows" "0" "$(grep -c '^| pipelining_inorder\.' "$WORK/unmeasured.log" || true)"
  check "not measured: a warning names the problem" "1" "$(grep -c 'did NOT measure this build.*flag did not take effect' "$WORK/unmeasured.log" || true)"
  jq '.pipelining_inorder = {} | .pipelining_inorder_attempted = false | .pipelining_inorder_skip_reason = "disabled" | .timestamp_utc = "2026-10-01T06:00:00Z"' \
    "$WORK/head-measured.json" > "$WORK/head-disabled.json"
  check "disabled: exit 0" "0" "$(compare disabled "$WORK/head-disabled.json")"
  check "disabled: no rows and no note" "0|0" \
    "$(grep -c '^| pipelining_inorder\.' "$WORK/disabled.log" || true)|$(grep -c 'In-order pipelining A/B' "$WORK/disabled.log" || true)"
fi

if [ "$FAILS" -gt 0 ]; then echo "FAILED: $FAILS check(s)" >&2; exit 1; fi
echo "all pipelining A/B checks passed"
