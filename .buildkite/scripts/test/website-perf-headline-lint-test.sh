#!/usr/bin/env bash
# Fixture checks for steps/website-perf-headline-lint.sh: the hand-written performance headline (page
# description, schema_faq answers, llms.txt line, body core count) against _data/perf_figures.json.
# Each case edits a copy of fixtures/website-perf-headline/ (a cut-down page in the live wording), so a
# figure refresh on the real page never breaks these; an edit that does not apply fails, never passes.
# The committed site itself is linted once, first, and then with its llms.txt run tally changed, so a
# reword of that tally phrase ("the N like-for-like runs") fails that edit and needs this test updated.
# Run: .buildkite/scripts/test/website-perf-headline-lint-test.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LINT="$REPO_ROOT/.buildkite/scripts/steps/website-perf-headline-lint.sh"
FIX="$REPO_ROOT/.buildkite/scripts/test/fixtures/website-perf-headline"
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
T="$(mktemp -d "${TMPDIR:-/tmp}/website-perf-headline-lint-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT

# fixture <case>: a copy of the four inputs under $T/<case>; sets C (site) and P (publish script).
fixture() {
  C="$T/$1/site"; P="$T/$1/publish.sh"
  mkdir -p "$C/_data" "$C/mock_server"
  cp "$FIX/perf_figures.json" "$C/_data/"; cp "$FIX/performance.html" "$C/mock_server/"
  cp "$FIX/llms.txt" "$C/"; cp "$FIX/publish-constants.txt" "$P"
}
# edit <file> <sed program>: fails the run if the program changed nothing.
edit() {
  sed "$2" "$1" > "$1.new"
  if [ "$(cksum < "$1")" = "$(cksum < "$1.new")" ]; then bad "fixture edit '$2' did not apply to $1"; fi
  mv "$1.new" "$1"
}
jedit() { jq "$2" "$1" > "$1.new" && mv "$1.new" "$1"; }
# lint <case>: runs the lint on that case's copy; prints "<exit code>".
lint() {
  local rc=0
  WEBSITE_PERF_LINT_SITE="$T/$1/site" WEBSITE_PERF_LINT_PUBLISH_SCRIPT="$T/$1/publish.sh" \
    bash "$LINT" > "$T/$1.out" 2>&1 || rc=$?
  echo "$rc"
}
says() { [ -f "$T/$1.out" ] || { echo MISSING; return 0; }; grep -qF -- "$2" "$T/$1.out" && echo yes || echo no; }
lines() { [ -f "$T/$1.out" ] || { echo MISSING; return 0; }; grep -cF -- "$2" "$T/$1.out" || true; }

PAGE_REL="mock_server/performance.html"
line_of() { grep -n -m1 -F -- "$2" "$1" | cut -d: -f1; }
DESC="$(line_of "$FIX/performance.html" 'description: "')"
FAQ1="$(grep -n 'answer: ".*requests/sec' "$FIX/performance.html" | sed -n 1p | cut -d: -f1)"
FAQ2="$(grep -n 'answer: ".*requests/sec' "$FIX/performance.html" | sed -n 2p | cut -d: -f1)"
LLMS="$(line_of "$FIX/llms.txt" 'mock_server/performance.html')"
BODY="$(line_of "$FIX/performance.html" '<p itemprop="headline">A single MockServer instance holds a sub-millisecond median up to')"
SPREAD="$(line_of "$FIX/performance.html" '{% capture ceiling_spread %}')"
FAQ3="$(line_of "$FIX/performance.html" 'answer: "No. At 5,000 expectations')"
check "the fixture page has a description, two rate-stating FAQ answers, an llms.txt line, a headline paragraph and a run tally" "yes" \
  "$([ -n "$DESC" ] && [ -n "$FAQ3" ] && [ -n "$FAQ1" ] && [ -n "$FAQ2" ] && [ -n "$LLMS" ] && [ -n "$BODY" ] && [ -n "$SPREAD" ] && echo yes || echo no)"

echo "--- the committed site, then text that agrees"
rc=0; bash "$LINT" > "$T/repo.out" 2>&1 || rc=$?
check "the repository as committed passes" "0|yes" "$rc|$(says repo 'OK: the performance headline text agrees')"
mkdir -p "$T/live/site/_data" "$T/live/site/mock_server"
cp "$REPO_ROOT/jekyll-www.mock-server.com/_data/perf_figures.json" "$T/live/site/_data/"
cp "$REPO_ROOT/jekyll-www.mock-server.com/mock_server/performance.html" "$T/live/site/mock_server/"
cp "$REPO_ROOT/jekyll-www.mock-server.com/llms.txt" "$T/live/site/"
cp "$REPO_ROOT/.buildkite/scripts/steps/perf-website-publish.sh" "$T/live/publish.sh"
LIVE_LLMS="$(line_of "$T/live/site/llms.txt" 'mock_server/performance.html')"
edit "$T/live/site/llms.txt" "${LIVE_LLMS}s/ the [A-Za-z0-9-]* like-for-like runs/ the 98 like-for-like runs/"
check "  ... and its run tally is read: changing the llms.txt one alone is named" "1|yes" \
  "$(lint live)|$(says live "llms.txt:$LIVE_LLMS says 98")"
fixture good
check "the fixture passes: other answers, other llms.txt lines and unrelated body numbers are not headline text" "0" "$(lint good)"
fixture k
edit "$C/llms.txt" "${LLMS}s/serves about 144,000 requests/serves about 144k requests/"
check "  ... and the headline may be written 144k" "0" "$(lint k)"
fixture achieved
edit "$C/$PAGE_REL" "${FAQ1}s/roughly 143,700 req/roughly 143,719 req/"
edit "$C/$PAGE_REL" "${FAQ1}s/latency is 9.7 ms/latency is 9.73 ms/"
check "  ... and the achieved rate and a latency to any precision they round to" "0" "$(lint achieved)"

echo "--- a hand-written number that disagrees with the published figure"
fixture desc
edit "$C/$PAGE_REL" "${DESC}s/about 144,000/about 152,000/"
check "the description's headline: named at its line" "1|yes" "$(lint desc)|$(says desc "$PAGE_REL:$DESC: states the headline as 152,000; perf_figures.json has 144,000")"
fixture faq1
edit "$C/$PAGE_REL" "${FAQ1}s/roughly 143,700 req/roughly 143,000 req/"
check "the first FAQ answer's achieved rate, off by more than it rounds" "1|yes" "$(lint faq1)|$(says faq1 "$PAGE_REL:$FAQ1: 143,000 is not a published figure")"
fixture faq2
edit "$C/$PAGE_REL" "${FAQ2}s/served about 144,000 requests/served about 128k requests/"
check "the second FAQ answer's headline, in the k form" "1|yes" "$(lint faq2)|$(says faq2 "$PAGE_REL:$FAQ2: states the headline as 128k")"
fixture llms
edit "$C/llms.txt" "${LLMS}s/serves about 144,000 requests/serves about 145k requests/"
check "the llms.txt headline" "1|yes" "$(lint llms)|$(says llms "llms.txt:$LLMS: states the headline as 145k")"
fixture subms
edit "$C/$PAGE_REL" "${FAQ1}s/a millisecond to 120,000 requests/a millisecond to 128,000 requests/"
check "a rate stated beside the headline (sub-millisecond p95 through)" "1|yes" "$(lint subms)|$(says subms "$PAGE_REL:$FAQ1: 128,000 is not a published figure")"
fixture at
edit "$C/$PAGE_REL" "${FAQ1}s/At 144,000 requests/At 152,000 requests/"
check "the ceiling restated without \"about\"" "1|yes" "$(lint at)|$(says at "$PAGE_REL:$FAQ1: 152,000 is not a published figure")"
fixture rung
edit "$C/llms.txt" "${LLMS}s/provision against 136,000/provision against 128,000/"
check "a rung that is neither the published nor the other one the runs read" "1|yes" "$(lint rung)|$(says rung "llms.txt:$LLMS: 128,000 is not a published figure")"
fixture lat
edit "$C/$PAGE_REL" "${DESC}s/p99 within 10 ms/p99 within 12 ms/"
edit "$C/$PAGE_REL" "${FAQ1}s/latency is 9.7 ms/latency is 9.8 ms/"
check "latencies: the bound in the description and the p99 in an answer" "1|yes|yes" \
  "$(lint lat)|$(says lat "$PAGE_REL:$DESC: 12 ms is not a published latency")|$(says lat "$PAGE_REL:$FAQ1: 9.8 ms is not a published latency")"
fixture cores
edit "$C/$PAGE_REL" "${DESC}s/a six-core instance/an eight-core instance/"
edit "$C/$PAGE_REL" "${BODY}s/on six CPU cores/on eight CPU cores/"
check "the core count, in the description and in the page body" "1|yes|yes" \
  "$(lint cores)|$(says cores "$PAGE_REL:$DESC: states eight cores; perf_figures.json headline.server_cores is 6")|$(says cores "$PAGE_REL:$BODY: states eight cores")"
fixture tally
edit "$C/llms.txt" "${LLMS}s/in the six like-for-like runs/in the seven like-for-like runs/"
check "a run tally one place updated and another did not" "1|yes" "$(lint tally)|$(says tally "llms.txt:$LLMS says seven")"
fixture btally
edit "$C/$PAGE_REL" "${SPREAD}s/In the six like-for-like/In the five like-for-like/"
check "  ... including the page body's tally" "1|yes" "$(lint btally)|$(says btally "$PAGE_REL:$SPREAD says five")"
# tally_all <case> <count>: every place states that tally.
tally_all() {
  edit "$C/$PAGE_REL" "s/the six like-for-like/the $2 like-for-like/g"
  edit "$C/llms.txt" "${LLMS}s/the six like-for-like/the $2 like-for-like/"
}
fixture t13
tally_all t13 thirteen
check "a tally past twelve that every place agrees on passes" "0" "$(lint t13)"
fixture t13x
tally_all t13x thirteen
edit "$C/llms.txt" "${LLMS}s/the thirteen like-for-like/the twelve like-for-like/"
check "  ... and thirteen against twelve is named" "1|yes|yes" "$(lint t13x)|$(says t13x "llms.txt:$LLMS says twelve")|$(says t13x "$PAGE_REL:$SPREAD says thirteen")"
fixture tnum
tally_all tnum thirteen
edit "$C/llms.txt" "${LLMS}s/the thirteen like-for-like/the 13 like-for-like/"
check "a numeral and the same count in words agree" "0" "$(lint tnum)"
fixture tnumx
edit "$C/llms.txt" "${LLMS}s/the six like-for-like/the 13 like-for-like/"
check "  ... and a numeral that disagrees is named" "1|yes" "$(lint tnumx)|$(says tnumx "llms.txt:$LLMS says 13")"
fixture t21
tally_all t21 twenty
edit "$C/llms.txt" "${LLMS}s/the twenty like-for-like/the twenty-one like-for-like/"
check "a compound count is read whole, not as its last word" "1|yes" "$(lint t21)|$(says t21 "llms.txt:$LLMS says twenty-one")"
fixture t99
tally_all t99 "ninety-nine"
edit "$C/llms.txt" "${LLMS}s/the ninety-nine like-for-like/the 99 like-for-like/"
check "  ... up to ninety-nine" "0" "$(lint t99)"
fixture tbig
tally_all tbig "a hundred and two"
check "a count in words past ninety-nine fails closed" "1|yes" \
  "$(lint tbig)|$(says tbig "llms.txt:$LLMS: the run tally \"a hundred and two\" is not a count the check can read")"
fixture tadj
edit "$C/llms.txt" "${LLMS}s/the six like-for-like/the six qualifying like-for-like/"
check "a word between the count and \"like-for-like runs\" fails closed" "1|yes" \
  "$(lint tadj)|$(says tadj "llms.txt:$LLMS: mentions like-for-like runs without a count")"

echo "--- an FAQ answer that states a rate in any unit, in any case, is checked"
for unit in "RPS" "rps" "req/sec" "requests a second" "Requests/Sec" "REQ/S" "Requests Per Second"; do
  id="unit-$(printf '%s' "$unit" | tr -c 'A-Za-z' '-')"
  fixture "$id"
  edit "$C/$PAGE_REL" "${FAQ3}s#answer: \".*\"#answer: \"A single instance serves about 152k $unit.\"#"
  check "an answer in \"$unit\" with a stale headline is named" "1|yes" "$(lint "$id")|$(says "$id" "$PAGE_REL:$FAQ3: states the headline as 152k")"
  fixture "$id-ok"
  edit "$C/$PAGE_REL" "${FAQ3}s#answer: \".*\"#answer: \"A single instance serves about 144k $unit.\"#"
  check "  ... and one with the published headline passes" "0" "$(lint "$id-ok")"
done
fixture plain
edit "$C/$PAGE_REL" "${FAQ3}s#answer: \".*\"#answer: \"A single instance serves about 152000 RPS.\"#"
check "a plain-digit rate before an upper-case unit is read" "1|yes" "$(lint plain)|$(says plain "$PAGE_REL:$FAQ3: states the headline as 152000")"

echo "--- the published figure moved and the text did not"
fixture moved
jedit "$C/_data/perf_figures.json" '.headline.healthy_ceiling_rps = 152000 | .headline.healthy_ceiling_rps_display = "152,000"'
check "a new ceiling: every place that states the old one is named" "1|4" "$(lint moved)|$(lines moved 'states the headline as 144,000; perf_figures.json has 152,000')"
check "  ... and the old pair's rates are no longer published figures" "yes" "$(says moved "$PAGE_REL:$FAQ1: 136,000 is not a published figure")"
fixture alt
jedit "$C/_data/perf_figures.json" '.headline.healthy_ceiling_rps = 136000 | .headline.healthy_ceiling_rps_display = "136,000"'
check "the other rung: 144,000 may still be mentioned, but not as the headline" "1|4|0" \
  "$(lint alt)|$(lines alt 'states the headline as 144,000; perf_figures.json has 136,000')|$(lines alt '144,000 is not a published figure')"

echo "--- the other rung is allowed only while the page and the publish step name the same pair"
fixture pair
edit "$P" 's/^RW_ADJACENT_CEILING_RPS=136000$/RW_ADJACENT_CEILING_RPS=128000/'
check "a publish step naming another pair: the page gate is named, and 136,000 is no longer allowed" "1|yes|yes" \
  "$(lint pair)|$(says pair 'the run-tally gate names 136,000 and 144,000 but perf-website-publish.sh names 128,000 and 144,000')|$(says pair '136,000 is not a published figure')"
fixture noconst
edit "$P" '/^RW_EXPECTED_CEILING_RPS=/d'
check "a publish step that names no expected rung" "1|yes" "$(lint noconst)|$(says noconst 'do not name two different rungs')"
fixture nogate
edit "$C/$PAGE_REL" 's/hc == 136000/hc == 0 and false/'
check "a page whose tally gate no longer names the other rung" "1|yes" "$(lint nogate)|$(says nogate '136,000 is not a published figure')"
fixture norule
jedit "$C/_data/perf_figures.json" 'del(.headline_rule)'
check "page data with no multi-k6 rule" "1|yes" "$(lint norule)|$(says norule '136,000 is not a published figure')"

echo "--- page data the check cannot read fails closed"
fixture nodisplay
jedit "$C/_data/perf_figures.json" 'del(.headline.healthy_ceiling_rps_display)'
check "a missing headline.healthy_ceiling_rps_display" "1|yes" "$(lint nodisplay)|$(says nodisplay 'headline.healthy_ceiling_rps_display is null, not a string')"
fixture nocores
jedit "$C/_data/perf_figures.json" 'del(.headline.server_cores)'
check "a missing headline.server_cores" "1|yes" "$(lint nocores)|$(says nocores 'headline.server_cores is null')"
fixture norps
jedit "$C/_data/perf_figures.json" '.headline.healthy_ceiling_rps = "144000"'
check "a ceiling that is not a number" "1|yes" "$(lint norps)|$(says norps 'headline.healthy_ceiling_rps is "144000", not a whole number')"
fixture skew
jedit "$C/_data/perf_figures.json" '.headline.healthy_ceiling_rps_display = "145,000"'
check "a display figure that is not the ceiling" "1|yes" "$(lint skew)|$(says skew 'healthy_ceiling_rps_display "145,000" is not headline.healthy_ceiling_rps 144000')"
fixture nolat
jedit "$C/_data/perf_figures.json" 'del(.headline.healthy_ceiling_p99_whole_rung_ms)'
check "a missing latency field: the text that states it is named" "1|yes" "$(lint nolat)|$(says nolat "llms.txt:$LLMS: 44.9 ms is not a published latency")"
fixture cut
head -c 300 "$FIX/perf_figures.json" > "$C/_data/perf_figures.json"
check "truncated JSON" "1|yes" "$(lint cut)|$(says cut 'not one JSON document')"
fixture twice
cat "$FIX/perf_figures.json" "$FIX/perf_figures.json" > "$C/_data/perf_figures.json"
check "two JSON documents" "1|yes" "$(lint twice)|$(says twice 'not one JSON document')"
fixture absent
rm "$C/_data/perf_figures.json"
check "no page data" "1|yes" "$(lint absent)|$(says absent 'missing ')"

echo "--- text the check cannot read fails closed"
fixture block
edit "$C/$PAGE_REL" "${DESC}s/^description: \"/description: >\\
  \"/"
check "a description that is not one quoted line" "1|yes" "$(lint block)|$(says block "$PAGE_REL:$DESC: description is not a one-line double-quoted string")"
fixture noclaim
edit "$C/$PAGE_REL" "${DESC}s/serves about 144,000/serves 144,000/"
check "a description that no longer states the headline as \"about N\"" "1|yes" "$(lint noclaim)|$(says noclaim "$PAGE_REL:$DESC: states no headline")"
fixture nollms
edit "$C/llms.txt" "${LLMS}d"
check "llms.txt without the performance line" "1|yes" "$(lint nollms)|$(says nollms 'llms.txt: no line links mock_server/performance.html')"
fixture nofaq
edit "$C/$PAGE_REL" '/answer: ".*requests\/sec/d'
check "no FAQ answer that states a rate" "1|yes" "$(lint nofaq)|$(says nofaq 'no schema_faq answer states a rate')"

echo
if [ "$FAILS" -gt 0 ]; then echo ":x: $FAILS check(s) failed" >&2; exit 1; fi
echo "all website perf headline lint checks passed"
