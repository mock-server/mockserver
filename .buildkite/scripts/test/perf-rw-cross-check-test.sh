#!/usr/bin/env bash
# Fixture tests for the rw-multi-k6 same-requests cross-check (lib/perf-rw-cross-check.jq),
# no Docker needed. The base rung is a real rig rung:
# by_time dropped about one push interval of steady requests, so its p99 is far from the
# published one while by_tag, over the same requests, agrees. Its by_tag-at-the-cut figures
# are synthetic (that run predates the query): by_time less 6 settle requests in flight.
# Run: .buildkite/scripts/test/perf-rw-cross-check-test.sh
# PERF_XCHECK_HARNESS=<path> tests another copy of the harness's by_tag-at-the-cut query (a degrade check).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env PERF_XCHECK_HARNESS
HARNESS="${PERF_XCHECK_HARNESS:-$REPO_ROOT/mockserver-performance-test/scripts/rw-multi-k6-sweep.sh}"
CROSS_JQ="$REPO_ROOT/.buildkite/scripts/steps/lib/perf-rw-cross-check.jq"
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}

# One-rung input; $1 is a jq update applied to it (the degrade).
input() { # update
  jq -nc '
    {live:true,
     pub:{points:[{offered_rps:28800, achieved_rps:28740.3, sample_count:431104, measured_sample_count:345612,
                   p50_ms:0.081, p90_ms:0.114, p95_ms:0.134, p99_ms:0.359, p999_ms:1.642}]},
     bytime:{points:[{sample_count:431104, measured_sample_count:317026, p50_ms:0.081, p95_ms:0.132, p99_ms:0.252,
                      settle_cut_ok:true, accounting_ok:true, per_process:[{pool:2048}]}]},
     bytag:{points:[{measured_sample_count:345612, p50_ms:0.081, p95_ms:0.134, p99_ms:0.358}]},
     bytagcut:{points:[{measured_sample_count:317020, p50_ms:0.081, p95_ms:0.132, p99_ms:0.251}]},
     main:null, xcost:[],
     tol:{p50:0.10, p95:0.10, p99:0.15, abs_ms:0.005, push_s:1, window_tol:0.05,
          cross_run:{achieved_ratio:0.02, p50:0.20, p95:0.35, p99:0.60}}}' | jq -c "$1"
}
cross() { input "$1" | jq -c -f "$CROSS_JQ"; }
eqv() { jq -r '.equivalent' <<<"$1"; }
has() { grep -qE "$1" <<<"$2" && echo yes || echo no; } # regex text
rung() { jq -r ".same_requests.rungs[0].$2" <<<"$1"; }

echo "--- 1. a rig rung whose time cut dropped ~1 s of requests: held to by_tag from the same cut"
R="$(cross '.')"
check "reference is by_tag at the cut" "by_tag_at_cut" "$(rung "$R" by_time.reference)"
check "by_time p99 0.252 matches by_tag at the cut 0.251" "true" "$(rung "$R" by_time.p99.ok)"
check "  ... though 30% under the published 0.359" "0.359" "$(rung "$R" by_time.p99.published)"
check "6 settle requests after the cut, within the pool" "6 2048 true" "$(rung "$R" 'by_time.counts | "\(.settle_after_cut) \(.max_settle_after_cut) \(.ok)"')"
check "window: 28586 excluded, within 60480" "28586 60480 true" "$(rung "$R" 'window | "\(.excluded) \(.max_excluded) \(.ok)"')"
check "by_tag agrees on the same requests" "true" "$(rung "$R" by_tag.ok)"
check "equivalent" "true" "$(eqv "$R")"
check "no failure listed" "0" "$(jq -r '.same_requests.failed | length' <<<"$R")"

echo "--- 2. inside the old rank band, outside the new reference"
# The old band held this p99 between published Q(0.908) and Q(0.9908): about 0.114..0.371 ms.
R="$(cross '.bytime.points[0].p99_ms = 0.359')"
check "by_time p99 equal to the published one (cut not applied to the histogram) fails" "false" "$(eqv "$R")"
check "  ... named with its reference" "true" "$(jq -r '.same_requests.failed[0] | test("by_time vs by_tag_at_cut .*p99 0.359 vs 0.251")' <<<"$R")"
check "by_time p99 0.30 (17% off by_tag at the cut) fails" "false" "$(eqv "$(cross '.bytime.points[0].p99_ms = 0.30')")"
check "by_time p99 0.20 fails" "false" "$(eqv "$(cross '.bytime.points[0].p99_ms = 0.20')")"
check "by_time p99 0.288 (within 15%) passes" "true" "$(eqv "$(cross '.bytime.points[0].p99_ms = 0.288')")"
check "by_time p50 0.12 fails" "false" "$(eqv "$(cross '.bytime.points[0].p50_ms = 0.12')")"

echo "--- 2b. the by_tag-at-the-cut reference itself"
R="$(cross '.bytagcut = null')"
check "live run with no by_tag at the cut fails" "false" "$(eqv "$R")"
check "  ... no reference" "null" "$(rung "$R" by_time.reference)"
check "  ... named" "true" "$(jq -r '.same_requests.failed[0] | test("no by_tag at the cut")' <<<"$R")"
check "live run with no by_tag at the cut fails even when by_time matches the published window" "false" "$(eqv "$(cross '.bytagcut = null | .bytime.points[0].p99_ms = 0.357')")"
check "live run with an empty point fails" "false" "$(eqv "$(cross '.bytagcut.points = [null]')")"
check "by_tag at the cut missing a quantile fails" "false" "$(eqv "$(cross '.bytagcut.points[0].p95_ms = null')")"
R="$(cross '.bytagcut.points[0].measured_sample_count = 317027')"
check "by_tag at the cut holding more requests than by_time fails" "false" "$(eqv "$R")"
check "  ... on its count" "false" "$(rung "$R" by_time.counts.ok)"
check "settle requests after the cut equal to the pool pass" "true" "$(eqv "$(cross '.bytagcut.points[0].measured_sample_count = (317026 - 2048)')")"
check "one over the pool fails" "false" "$(eqv "$(cross '.bytagcut.points[0].measured_sample_count = (317026 - 2049)')")"
R="$(cross 'del(.live) | .bytagcut = null')"
check "offline, a run without it is held to the published quantiles" "published" "$(rung "$R" by_time.reference)"
check "  ... where the rig rung p99 gap fails" "false" "$(eqv "$R")"
check "  ... and a cut with no tail in its dropped second passes" "true" "$(eqv "$(cross 'del(.live) | .bytagcut = null | .bytime.points[0].p99_ms = 0.357')")"

echo "--- 3. a wrong query, histogram path or cut must still fail"
R="$(cross '.bytag.points[0].p99_ms = 0.718')"
check "by_tag p99 doubled" "false" "$(eqv "$R")"
check "by_tag p99 doubled names by_tag" "true" "$(jq -r '.same_requests.failed[0] | test("by_tag")' <<<"$R")"
R="$(cross '.bytag.points[0].measured_sample_count = 317026')"
check "by_tag over different requests (count)" "false" "$(eqv "$R")"
R="$(cross '.bytime.points[0].p50_ms = 0.162')"
check "by_time p50 doubled" "false" "$(eqv "$R")"
R="$(cross '.bytime.points[0].p95_ms = 0.268')"
check "by_time p95 doubled" "false" "$(eqv "$R")"
R="$(cross '.bytime.points[0].measured_sample_count = 431104')"
check "by_time cut not subtracted (settle requests kept)" "false" "$(eqv "$R")"
check "  ... rejected by the window" "false" "$(rung "$R" window.ok)"
R="$(cross '.bytime.points[0].measured_sample_count = (345612 - 86400)')"
check "by_time cut 3 pushes late" "false" "$(eqv "$R")"
R="$(cross '.bytime.points[0].settle_cut_ok = false')"
check "by_time cut outside its bound" "false" "$(eqv "$R")"
R="$(cross '.bytime.points[0].per_process[0].pool = null | .bytime.points[0].measured_sample_count = 345700')"
check "requests added with no pool to explain them" "false" "$(eqv "$R")"
R="$(cross '.bytime.points[0].p99_ms = null')"
check "by_time p99 missing" "false" "$(eqv "$R")"
R="$(cross '.bytime.points[0].sample_count = 431000')"
check "total counts differ" "false" "$(eqv "$R")"
R="$(cross '.bytime = null')"
check "by_time merge missing" "false" "$(eqv "$R")"
R="$(cross '.bytime.points[0].accounting_ok = false')"
check "by_time accounting failed" "false" "$(eqv "$R")"

echo "--- 4. window edges"
R="$(cross '.bytime.points[0] += {measured_sample_count:(345612 - 60480)} | .bytagcut.points[0].measured_sample_count = (345612 - 60480)')"
check "d at the window maximum (2 pushes x rate x 1.05 = 60480) is allowed" "true" "$(rung "$R" window.ok)"
check "  ... and equivalent" "true" "$(eqv "$R")"
R="$(cross '.bytime.points[0] += {measured_sample_count:(345612 - 60481)} | .bytagcut.points[0].measured_sample_count = (345612 - 60481)')"
check "d one over the window maximum fails" "false" "$(eqv "$R")"
R="$(cross '.bytime.points[0] += {measured_sample_count:(345612 + 1000), p99_ms:0.357} | .bytagcut.points[0] += {measured_sample_count:345612, p99_ms:0.358}')"
check "d<0 (1000 settle requests completing after the cut, pool 2048): window ok" "true" "$(rung "$R" window.ok)"
check "d<0 with agreeing quantiles: equivalent" "true" "$(eqv "$R")"
R="$(cross '.bytime.points[0] += {measured_sample_count:(345612 + 2049)} | .bytagcut.points[0].measured_sample_count = 345612')"
check "d<0 beyond the in-flight pool fails" "false" "$(eqv "$R")"

echo "--- 5. a cut at the boundary"
R="$(cross '.bytime.points[0] += {measured_sample_count:345612, p99_ms:0.357} | .bytagcut.points[0] += {measured_sample_count:345612, p99_ms:0.358}')"
check "d=0, by_tag at the cut equals by_tag: equivalent" "true" "$(eqv "$R")"
R="$(cross '.bytime.points[0] += {measured_sample_count:345612, p99_ms:0.252} | .bytagcut.points[0] += {measured_sample_count:345612, p99_ms:0.358}')"
check "d=0, same p99 gap fails" "false" "$(eqv "$R")"

echo "--- 6. the harness's by_tag-at-the-cut query (stub Prometheus: count 300000, quantiles 0.2514 ms)"
T="$(mktemp -d "${TMPDIR:-/tmp}/perf-xcheck-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
eval "$(sed -n '/^xcheck_tag_at_cut() {/,/^}/p' "$HARNESS")"
promv() { echo "$1" >> "$WORK/queries.txt"
  case "$1" in
    "histogram_count(sum(("*) echo "${STUB_AT_CUT:-1234}" ;;
    histogram_count*) echo 300000 ;;
    *) echo 0.2514 ;;
  esac; }
WORK="$T"
echo '{"n":1,"per_process_rates":[9600,28800]}' > "$T/xcheck-meta.json"
echo '{"points":[{"per_process":[{"proc":"xcheck-p0","settle_cut_s":1790809716.994}]},{"per_process":[{"proc":"xcheck-p0","settle_cut_s":null}]}]}' > "$T/xcheck-by-time.json"
xcheck_tag_at_cut
Q="$(cat "$T/queries.txt")"
check "a point per rung; a rung with no cut has none" "2 null" "$(jq -r '"\(.points | length) \(.points[1])"' "$T/xcheck-tag-at-cut.json")"
check "count and rounded quantiles" '{"measured_sample_count":300000,"p50_ms":0.251,"p95_ms":0.251,"p99_ms":0.251}' "$(jq -c '.points[0]' "$T/xcheck-tag-at-cut.json")"
check "p99 from the steady series less the steady series at the process's cut" "yes" \
  "$(has 'histogram_quantile\(0\.99, \(sum\(k6_http_req_duration_seconds\{proc=~"xcheck-p\[0-9\]\+",rate="9600",win="9600_steady"\}\) - sum\(\(k6_http_req_duration_seconds\{proc="xcheck-p0",rate="9600",win="9600_steady"\} @ 1790809716\.994\)\)\)\) \* 1000' "$Q")"
check "no query for the rung with no cut" "no" "$(has 'rate="28800"' "$Q")"
: > "$T/queries.txt"; STUB_AT_CUT=null xcheck_tag_at_cut
check "no steady sample at the cut: nothing subtracted" "yes" \
  "$(has '^histogram_quantile\(0\.99, sum\(k6_http_req_duration_seconds\{proc=~"xcheck-p\[0-9\]\+",rate="9600",win="9600_steady"\}\)\) \* 1000$' "$(cat "$T/queries.txt")")"

echo "--- 7. cross_run: judged only where each main-phase k6 offers at least the per-process floor"
# Rungs 500..24000 and 32000 of perf build 667 (single process vs 4 processes): every rung a
# single process covers is below 8000 per process. $1: floor ("" = none), $2: a jq update.
xrun() { # floor update
  jq -nc --arg min "$1" '
    def pt($o; $a; $p50; $p95; $p99): {offered_rps:$o, achieved_rps:$a, p50_ms:$p50, p95_ms:$p95, p99_ms:$p99};
    def mp($o; $a; $p50; $p95; $p99): pt($o; $a; $p50; $p95; $p99) + {nominal_agg_offered_rps:$o, per_process:[{},{},{},{}]};
    {live:true, bytime:null, bytag:null, bytagcut:null, xcost:[],
     pub:{points:[pt(500; 500.1; 0.423; 0.601; 0.685), pt(1000; 1000.1; 0.37; 0.513; 0.608), pt(2000; 2000.1; 0.351; 0.544; 0.653),
                  pt(4000; 3992.5; 0.252; 0.523; 0.648), pt(8000; 8000; 0.181; 0.442; 0.607), pt(16000; 16000.1; 0.081; 0.285; 0.432),
                  pt(24000; 24000.4; 0.078; 0.235; 0.306)]},
     main:{points:[mp(500; 500.3; 0.471; 0.629; 0.686), mp(1000; 1000.3; 0.452; 0.576; 0.632), mp(2000; 2000.3; 0.346; 0.441; 0.534),
                   mp(4000; 4000.3; 0.25; 0.47; 0.629), mp(8000; 7990.7; 0.217; 0.51; 0.696), mp(16000; 16000.4; 0.16; 0.467; 0.677),
                   mp(24000; 23941.3; 0.117; 0.415; 0.657), mp(32000; 32000.4; 0.084; 0.272; 0.41)]},
     tol:{p50:0.10, p95:0.10, p99:0.15, abs_ms:0.005, push_s:1, window_tol:0.05,
          cross_run:({achieved_ratio:0.02, p50:0.20, p95:0.35, p99:0.60}
                     + (if $min == "" then {} else {min_per_process_rps:($min|tonumber)} end))}}' \
    | jq -c "$2" | jq -c -f "$CROSS_JQ" | jq -c '.cross_run'
}
crun() { jq -r "$2" <<<"$1"; }
at() { jq -r --argjson o "$2" ".rungs[] | select(.offered_rps == \$o) | $3" <<<"$1"; } # cross_run offered_rps expr
# A synthetic single-process 32000 rung, where both arms are past the knee (multi p50 0.084).
X32='.pub.points += [{offered_rps:32000, achieved_rps:32000.2, p50_ms:0.080, p95_ms:0.25, p99_ms:0.40}]'
R="$(xrun 8000 '.')"
check "rig ladder at the 8000 floor: nothing judged, so agrees is null" "null 0 7 1" "$(crun "$R" '"\(.agrees) \(.compared) \(.below_floor) \(.no_counterpart)"')"
check "  ... the 16000 rung is 4000 per process, reported with its figures, not judged" "below per-process floor 4000 null 0.081 0.16 false" \
  "$(at "$R" 16000 '"\(.status) \(.per_process_rps) \(.ok) \(.latency.p50.published) \(.latency.p50.remote_write) \(.latency.p50.ok)"')"
check "  ... and its achieved ratios too" "1 1 true" "$(at "$R" 16000 '"\(.achieved_ratio.single) \(.achieved_ratio.multi) \(.achieved_ratio.ok)"')"
check "  ... no rung below the floor carries a verdict" "0" "$(crun "$R" '[.rungs[] | select(.status == "below per-process floor" and .ok != null)] | length')"
R="$(xrun "" '.')"
check "no floor (an input from before it): the recorded verdict, every rung judged" "false 7 0" "$(crun "$R" '"\(.agrees) \(.compared) \(.below_floor)"')"
check "  ... 16000 fails on p50 0.081 vs 0.16" "compared false" "$(at "$R" 16000 '"\(.status) \(.ok)"')"
check "floor 0 judges every rung too" "false 7" "$(crun "$(xrun 0 '.')" '"\(.agrees) \(.compared)"')"
R="$(xrun 8000 "$X32")"
check "mixed ladder: 32000 (8000 per process, at the floor) judged and agreeing" "true 1 7 0" "$(crun "$R" '"\(.agrees) \(.compared) \(.below_floor) \(.no_counterpart)"')"
check "  ... at 8000 per process" "compared 8000 true" "$(at "$R" 32000 '"\(.status) \(.per_process_rps) \(.ok)"')"
check "  ... while the 16000 rung's disagreement is not counted" "below per-process floor false" "$(at "$R" 16000 '"\(.status) \(.latency.p50.ok)"')"
check "floor one above 32000's per-process rate: nothing judged" "null 0 8" "$(crun "$(xrun 8001 "$X32")" '"\(.agrees) \(.compared) \(.below_floor)"')"
R="$(xrun 8000 "$X32 | .pub.points[7].p50_ms = 0.04")"
check "a judged rung outside tolerance: agrees false" "false 1" "$(crun "$R" '"\(.agrees) \(.compared)"')"
R="$(xrun 8000 "$X32 | .pub.points[7].achieved_rps = null")"
check "an incomplete rung above the floor is judged and fails" "false incomplete" "$(crun "$R" '"\(.agrees) \(.rungs[7].status)"')"
R="$(xrun 8000 '.main.points[].per_process = [{}]')"
check "one main-phase process: the arms share a layout, so every rung is judged" "false 7 0" "$(crun "$R" '"\(.agrees) \(.compared) \(.below_floor)"')"
check "  ... 4000 agrees" "compared 4000 true" "$(at "$R" 4000 '"\(.status) \(.per_process_rps) \(.ok)"')"
R="$(xrun 8000 '.main.points[] |= del(.per_process)')"
check "a merged ladder without per-process entries is judged as before" "false 7 0" "$(crun "$R" '"\(.agrees) \(.compared) \(.below_floor)"')"
check "the floor is reported with the tolerances" "8000" \
  "$(jq -nc '{live:true, main:null, xcost:[], tol:{p50:0.1, p95:0.1, p99:0.15, abs_ms:0.005, push_s:1, window_tol:0.05, cross_run:{achieved_ratio:0.02, p50:0.2, p95:0.35, p99:0.6, min_per_process_rps:8000}}}' | jq -r -f "$CROSS_JQ" | jq -r '.tolerances.cross_run.min_per_process_rps')"

echo "--- 8. the harness passes the floor (default 8000) into the cross-check input"
eval "$(grep -E '^RTOL_[A-Z0-9_]+=' "$HARNESS")"
eval "$(sed -n '/^cross_input() {/,/^}/p' "$HARNESS")"
cpu_cost_json() { echo '[]'; }
# shellcheck disable=SC2034  # read by the eval'd cross_input
XTOL_P50=0.10 XTOL_P95=0.10 XTOL_P99=0.15 XTOL_ABS_MS=0.005 PUSH_S=1 WINDOW_TOL=0.05
for f in xcheck-p0 xcheck-by-time xcheck-by-tag main-merged; do echo '{"points":[]}' > "$T/$f.json"; done
check "default floor" "8000" "$(cross_input | jq -r '.tol.cross_run.min_per_process_rps')"
# shellcheck disable=SC2034
RTOL_MIN_PP=0
check "a floor of 0 reaches the program" "0" "$(cross_input | jq -r '.tol.cross_run.min_per_process_rps')"

echo "--- 9. the cross-check cap: 40k on multi_socket, 24k otherwise, env wins"
xcap() { # ladder_profile [PERF_RW_XCHECK_MAX_RPS]
  # shellcheck disable=SC2034  # read by the eval'd cap block
  ( LADDER_PROFILE="$1"; PERF_RW_XCHECK_MAX_RPS="${2:-}"; [ -n "${2:-}" ] || unset PERF_RW_XCHECK_MAX_RPS
    eval "$(sed -n '/^XCHECK_MAX_RPS_DEFAULT=/,/^XCHECK_MAX_RPS=/p' "$HARNESS")"; echo "${XCHECK_MAX_RPS:-}" )
}
check "default profile keeps 24k" "24000" "$(xcap default)"
check "multi_socket defaults to 40k" "40000" "$(xcap multi_socket)"
check "PERF_RW_XCHECK_MAX_RPS overrides the profile default" "32000" "$(xcap multi_socket 32000)"
MS_RATES="$(sed -n 's/^MULTI_SOCKET_RATES="\(.*\)"$/\1/p' "$HARNESS")"
xr() { # XCHECK_MAX_RPS
  # shellcheck disable=SC2034  # read by the eval'd XRATES block
  ( XCHECK=true RATES="$MS_RATES" XCHECK_MAX_RPS="$1"; unset PERF_RW_XCHECK_RATES
    eval "$(sed -n '/^XRATES=""/,/^fi$/p' "$HARNESS")"; echo "${XRATES:-}" )
}
check "multi_socket xcheck ladder reaches 40k (32k-40k judged at N=4)" \
  "500,1000,2000,4000,8000,16000,24000,32000,36000,40000" "$(xr "$(xcap multi_socket)")"

if [ "$FAILS" -gt 0 ]; then echo ":x: $FAILS cross-check fixture check(s) failed" >&2; exit 1; fi
echo "all cross-check fixture checks passed"
