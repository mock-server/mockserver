#!/usr/bin/env bash
# perf-test-compare.sh judges a budget's `bimodal` metrics against the baseline mode the head falls
# in, plus a mode-share check. Synthetic windows pin each branch; the real p95 history from builds
# 649-734 (fixtures/perf-bimodal, written by its generate-history.py) is replayed through the real
# compare program with and without the rule.
# Run: .buildkite/scripts/test/perf-bimodal-budget-test.sh   (PERF_COMPARE_SCRIPT=<path> to test another copy)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env PERF_COMPARE_SCRIPT
CMP="${PERF_COMPARE_SCRIPT:-$REPO_ROOT/.buildkite/scripts/steps/perf-test-compare.sh}"
BUDGETS="$REPO_ROOT/mockserver-performance-test/perf-budgets.json"
HISTORY="$REPO_ROOT/.buildkite/scripts/test/fixtures/perf-bimodal/p95-history-649-734.json"
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
WORK="$(mktemp -d "${TMPDIR:-/tmp}/perf-bimodal-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

COMPARE="$(awk '/^COMPARE='"'"'$/ {on = 1; next} on && /^'"'"'$/ {exit} on' "$CMP")"
[ -n "$COMPARE" ] || { echo "FAIL: COMPARE program not found in $CMP" >&2; exit 1; }
# The program is a single-quoted shell string: an apostrophe in it ends the string in the real
# script, though this awk extraction would not notice.
check "the COMPARE program holds no single quote" "0" "$(tr -cd "'" <<<"$COMPARE" | wc -c | tr -d ' ')"
NOBIMODAL="$WORK/budgets-without-bimodal.json"
jq '.budgets |= with_entries(if (.value | type) == "object" then .value |= del(.bimodal) else . end)' "$BUDGETS" > "$NOBIMODAL"

# A run carrying one p95 metric: `<arm>.p95_ms` under .behaviours, `info_<arm>.p95_ms` under the INFO arm.
MK='def mk($k; $v): ($k | sub("\\.p95_ms$"; "")) as $n
  | if ($k | startswith("info_"))
    then {agent: {instance_type: "c5.12xlarge"}, info_log_level_arm: {measured: true, behaviours: {($n | ltrimstr("info_")): {p95_ms: $v}}}}
    else {agent: {instance_type: "c5.12xlarge"}, behaviours: {($n): {p95_ms: $v}}} end;'
row() { # metric head baseline_csv [budgets] -> the metric's row as "regression|mode|baseline|threshold"
  jq -nr --arg k "$1" --argjson h "$2" --argjson bv "[$3]" --slurpfile b "${4:-$BUDGETS}" --argjson minbaseline 5 \
    "$MK"' ($b[0].budgets) as $budgets | ($bv | map(mk($k; .))) as $baseline | mk($k; $h) | '"$COMPARE"' | .rows[] | select(.name == $k)
      | "\(.regression)|\(.mode // "none")|\(.baseline * 1000 | round / 1000)|\(.threshold * 1000 | round / 1000)"'
}
field() { cut -d'|' -f"$1" <<<"$2"; }

BODY="0.43,0.45,0.48,0.50,0.55,0.62,0.70"
WINDOW="$BODY,3.7,3.8,3.9"

echo "--- 1. a listed metric over a two-mode window is judged against its head's mode"
R="$(row match_http.p95_ms 4.0 "$WINDOW")"
check "a clump-level head is not flagged" "false|upper" "$(field 1-2 "$R")"
check "  and is reported against the upper mode's median" "3.8" "$(field 3 "$R")"
check "the same head under the single band is flagged (the flap)" "true|none" "$(field 1-2 "$(row match_http.p95_ms 4.0 "$WINDOW" "$NOBIMODAL")")"
R="$(row match_http.p95_ms 0.52 "$WINDOW")"
check "a body-level head is not flagged, against the lower mode" "false|lower|0.5" "$(field 1-3 "$R")"
check "a head between the modes is flagged" "true|between" "$(field 1-2 "$(row match_http.p95_ms 1.5 "$WINDOW")")"
check "a head above the upper band is flagged" "true|upper" "$(field 1-2 "$(row match_http.p95_ms 6.0 "$WINDOW")")"
check "a catastrophic head is flagged" "true" "$(field 1 "$(row match_http.p95_ms 26 "$WINDOW")")"
check "the upper band is median +/- upper_band_pct when that is wider than 3 sigma" "4.75" "$(field 4 "$(row match_http.p95_ms 4.0 "$WINDOW")")"
check "a head just under the upper band's lower edge is between, so flagged" "true|between" "$(field 1-2 "$(row match_http.p95_ms 2.8 "$WINDOW")")"
check "the lower band is still median+3 sigma, at least min_pct" "true|between" "$(field 1-2 "$(row match_http.p95_ms 0.8 "$WINDOW")")"

echo "--- 2. everything else keeps the single median+MAD band"
check "a metric the budget does not list" "true|none" "$(field 1-2 "$(row forward_http.p95_ms 4.0 "$WINDOW")")"
check "a listed metric's p99 (another budget)" "none" "$(jq -nr --argjson bv "[$WINDOW]" --slurpfile b "$BUDGETS" --argjson minbaseline 5 \
  '($b[0].budgets) as $budgets | ($bv | map({agent: {instance_type: "c5.12xlarge"}, behaviours: {match_http: {p95_ms: 1, p99_ms: .}}})) as $baseline
   | {agent: {instance_type: "c5.12xlarge"}, behaviours: {match_http: {p95_ms: 1, p99_ms: 4.0}}} | '"$COMPARE"' | .rows[] | select(.name == "match_http.p99_ms") | .mode // "none"')"
check "a unimodal window" "false|none" "$(field 1-2 "$(row match_http.p95_ms 0.52 "$BODY,0.47,0.53,0.58")")"
check "a unimodal window still flags a jump" "true|none" "$(field 1-2 "$(row match_http.p95_ms 1.5 "$BODY,0.47,0.53,0.58")")"
check "a lower mode of 2 runs cannot be judged, so the single band applies" "none" "$(field 2 "$(row match_http.p95_ms 4.0 "0.45,0.50,3.4,3.6,3.8,4.0,4.2,4.4")")"
check "a largest gap under split_ratio is one mode" "none" "$(field 2 "$(row match_http.p95_ms 0.9 "$BODY,1.0,1.1,1.2")")"
check "a one-run upper mode is a mode" "false|upper" "$(field 1-2 "$(row match_http.p95_ms 4.2 "$BODY,0.47,0.53,3.8")")"
check "  and a head well below it is between" "true|between" "$(field 1-2 "$(row match_http.p95_ms 2.5 "$BODY,0.47,0.53,3.8")")"
check "the INFO arm's listed metric uses the rule too" "false|upper" "$(field 1-2 "$(row info_match_http.p95_ms 4.0 "$WINDOW")")"
check "an INFO metric not listed does not" "true|none" "$(field 1-2 "$(row info_forward_http.p95_ms 4.0 "$WINDOW")")"

echo "--- 3. the mode-share check: a run of upper-mode heads is flagged even inside the upper band"
SHIFTED="0.43,0.45,0.48,0.50,0.55,3.7,3.8,3.9,3.75,3.85"
check "6 of the last 6 in the upper mode at a 0.32 share is flagged" "true|upper" "$(field 1-2 "$(row match_http.p95_ms 4.0 "$SHIFTED")")"
check "  and the same values with the upper runs oldest are not" "false|upper" "$(field 1-2 "$(row match_http.p95_ms 4.0 "3.7,3.8,3.9,3.75,3.85,0.43,0.45,0.48,0.50,0.55")")"
check "  nor is a body-level head after them" "false|lower" "$(field 1-2 "$(row match_http.p95_ms 0.5 "$SHIFTED")")"
check "4 of the last 6 (the window above) is within a 0.32 share" "false|upper" "$(field 1-2 "$(row match_http.p95_ms 4.0 "$WINDOW")")"
check "6 of 6 is ordinary at template_http's 0.75 share" "false|upper" "$(field 1-2 "$(row template_http.p95_ms 4.0 "$SHIFTED")")"
check "the INFO arm's share (0.44) flags 6 of 6" "true" "$(field 1 "$(row info_match_http.p95_ms 4.0 "$SHIFTED")")"
check "a lower mode of 1-2 runs still classifies for the share check" "true" "$(field 1 "$(row match_http.p95_ms 4.0 "0.45,0.50,3.4,3.6,3.8,4.0,4.2,4.4")")"
check "an unlisted metric gets no share check" "" "$(jq -nr --argjson bv "[$SHIFTED]" --slurpfile b "$BUDGETS" --argjson minbaseline 5 \
  "$MK"' ($b[0].budgets) as $budgets | ($bv | map(mk("forward_http.p95_ms"; .))) as $baseline | mk("forward_http.p95_ms"; 4.0) | '"$COMPARE"' | .rows[] | select(.name == "forward_http.p95_ms") | .share // ""')"

echo "--- 4. the annotation names the mode"
TABLE_STMT="$(awk '/^TABLE="\$\(printf/ {p=1} p {print} p && /\)'"'"'\)"$/ {exit}' "$CMP")"
[ -n "$TABLE_STMT" ] || bad "TABLE statement not found in $CMP"
table_of() { # head baseline_csv -> the rendered table for one compared row
  # shellcheck disable=SC2034  # read by the eval'd TABLE statement
  ( RESULT_CMP="$(jq -n --argjson h "$1" --argjson bv "[$2]" --slurpfile b "$BUDGETS" --argjson minbaseline 5 \
      "$MK"' ($b[0].budgets) as $budgets | ($bv | map(mk("match_http.p95_ms"; .))) as $baseline | mk("match_http.p95_ms"; $h) | '"$COMPARE")"
    eval "$TABLE_STMT"; printf '%s' "$TABLE" )
}
check "upper mode shown" "1" "$(table_of 4.0 "$WINDOW" | grep -c '| 3.8 (upper mode) |' || true)"
check "between modes shown, flagged" "1" "$(table_of 1.5 "$WINDOW" | grep -c '(between modes).*flagged' || true)"
check "a share flag names the counts" "1" "$(table_of 4.0 "$SHIFTED" | grep -cF '(6 of last 6 runs upper-mode, expected 32%)' || true)"
check "a single-band row is unchanged" "1" "$(table_of 0.52 "$BODY,0.47,0.53,0.58" | grep -cF '| match_http.p95_ms | 0.52 | 0.515 | 0.76 | notify-only |' || true)"

echo "--- 5. the real p95 history (builds 649-734), replayed"
replay() { # metric budgets -> builds flagged, space-separated
  jq -nr --arg k "$1" --slurpfile f "$HISTORY" --slurpfile b "$2" --argjson minbaseline 5 \
    "$MK"' ($b[0].budgets) as $budgets
    | [ $f[0].metrics[$k][] as $w
        | ($w.baseline | map(mk($k; .))) as $baseline
        | (mk($k; $w.head) | '"$COMPARE"' | .rows[] | select(.name == $k and .regression == true))
        | $w.build ] | map(tostring) | join(" ")'
}
# The single-band sets are what the production annotations reported for these builds.
check "match_http.p95: single band" "650 651 653 654 687 688 726 734" "$(replay match_http.p95_ms "$NOBIMODAL")"
check "match_http.p95: bimodal (655 and 659 by the share check: 5 clump runs in 6)" "655 659 673 684 728" "$(replay match_http.p95_ms "$BUDGETS")"
check "template_http.p95: single band" "673 679 683 684" "$(replay template_http.p95_ms "$NOBIMODAL")"
check "template_http.p95: bimodal" "" "$(replay template_http.p95_ms "$BUDGETS")"
check "info_match_http.p95: single band" "649 650 651 672 681 683 689 690" "$(replay info_match_http.p95_ms "$NOBIMODAL")"
check "info_match_http.p95: bimodal" "679" "$(replay info_match_http.p95_ms "$BUDGETS")"
check "info_match_https_h2.p95: single band" "651 652 657 658 728" "$(replay info_match_https_h2.p95_ms "$NOBIMODAL")"
check "info_match_https_h2.p95: bimodal" "649 653 681" "$(replay info_match_https_h2.p95_ms "$BUDGETS")"
check "the replay compared every window (38, 38, 39, 39)" "38 38 39 39" \
  "$(for k in match_http template_http info_match_http info_match_https_h2; do jq -nr --arg k "$k.p95_ms" --slurpfile f "$HISTORY" --slurpfile b "$BUDGETS" --argjson minbaseline 5 \
      "$MK"' ($b[0].budgets) as $budgets | [ $f[0].metrics[$k][] as $w | ($w.baseline | map(mk($k; .))) as $baseline
        | (mk($k; $w.head) | '"$COMPARE"' | .rows[] | select(.name == $k and (.threshold | type) == "number")) ] | length'; done | tr '\n' ' ' | sed 's/ $//')"
check "every metric the budget lists as bimodal has replayed history here" "$(jq -c '.metrics | keys' "$HISTORY")" \
  "$(jq -c '[.budgets[] | objects | .bimodal.metrics // empty | keys[]] | sort' "$BUDGETS")"

if command -v python3 >/dev/null 2>&1; then
  if python3 -c 'import ast, sys; ast.parse(open(sys.argv[1]).read())' "$(dirname "$HISTORY")/generate-history.py"; then
    ok "the fixture generator parses"
  else bad "the fixture generator does not parse"; fi
fi

if [ "$FAILS" -gt 0 ]; then echo "FAILED: $FAILS check(s)" >&2; exit 1; fi
echo "all bimodal budget checks passed"
