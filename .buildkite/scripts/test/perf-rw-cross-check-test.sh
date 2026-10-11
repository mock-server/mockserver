#!/usr/bin/env bash
# Fixture tests for the rw-multi-k6 same-requests cross-check (lib/perf-rw-cross-check.jq,
# performance programme item 31), no Docker needed. The base rung is a real rig rung (item 52):
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

if [ "$FAILS" -gt 0 ]; then echo ":x: $FAILS cross-check fixture check(s) failed" >&2; exit 1; fi
echo "all cross-check fixture checks passed"
