#!/usr/bin/env bash
# Fail, naming file:line, when the hand-written performance headline (the performance.html front-matter
# description and rate-stating schema_faq answers, its llms.txt line, the body's core count) disagrees
# with jekyll-www.mock-server.com/_data/perf_figures.json, which is all the perf publish patch rewrites.
# Rules: lib/website-perf-headline-check.jq. Docs: docs/operations/website.md#performance-headline-lint.
# Seams for the fixture tests: WEBSITE_PERF_LINT_SITE (site dir), WEBSITE_PERF_LINT_PUBLISH_SCRIPT.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
SITE="${WEBSITE_PERF_LINT_SITE:-$REPO_ROOT/jekyll-www.mock-server.com}"
PUBLISH="${WEBSITE_PERF_LINT_PUBLISH_SCRIPT:-$SCRIPT_DIR/perf-website-publish.sh}"
DATA="$SITE/_data/perf_figures.json"
PAGE="$SITE/mock_server/performance.html"
LLMS="$SITE/llms.txt"

command -v jq >/dev/null 2>&1 || { echo "+++ :x: jq is required" >&2; exit 1; }
for f in "$DATA" "$PAGE" "$LLMS" "$PUBLISH"; do
  [ -f "$f" ] || { echo "+++ :x: missing $f" >&2; exit 1; }
done
rel() { local p="$1"; echo "${p#"$REPO_ROOT"/}"; }

# One line per place: kind<TAB>file:line<TAB>text. A description or answer the reader cannot take as
# one quoted line is reported, never skipped.
places="$(
  awk -v page="$(rel "$PAGE")" '
    function emit(kind, text) { gsub(/\t/, " ", text); printf "%s\t%s:%d\t%s\n", kind, page, NR, text }
    function quoted(kind, rest) {
      if (rest ~ /^"([^"\\]|\\.)*"[ \t]*$/) {
        sub(/[ \t]*$/, "", rest); emit(kind, substr(rest, 2, length(rest) - 2))
      } else emit("unreadable", kind " is not a one-line double-quoted string, so its figures cannot be checked")
    }
    NR == 1 && $0 == "---" { fm = 1; next }
    fm && $0 == "---" { fm = 0; next }
    fm && /^description:/ { r = $0; sub(/^description:[ \t]*/, "", r); quoted("description", r); next }
    fm && /^[ \t]*(-[ \t]+)?answer:/ { r = $0; sub(/^[ \t]*(-[ \t]+)?answer:[ \t]*/, "", r); quoted("faq", r); next }
    !fm { emit("body", $0) }
  ' "$PAGE"
  awk -v llms="$(rel "$LLMS")" 'index($0, "mock_server/performance.html") {
    t = $0; gsub(/\t/, " ", t); printf "llms\t%s:%d\t%s\n", llms, NR, t }' "$LLMS"
)"
expected="$(sed -n 's/^RW_EXPECTED_CEILING_RPS=\([0-9][0-9]*\)$/\1/p' "$PUBLISH")"
adjacent="$(sed -n 's/^RW_ADJACENT_CEILING_RPS=\([0-9][0-9]*\)$/\1/p' "$PUBLISH")"
# A file that is not exactly one JSON document reaches the check as an empty array.
fig="$(jq -cs '.' "$DATA" 2>/dev/null)" || fig='[]'

problems="$(jq -n --argjson fig "$fig" --arg places "$places" --arg expected "$expected" --arg adjacent "$adjacent" \
  -f "$SCRIPT_DIR/lib/website-perf-headline-check.jq")" || { echo "+++ :x: the headline check could not run" >&2; exit 1; }
count="$(jq -r 'if type == "array" and all(.[]; type == "string") then length else "unreadable" end' <<<"$problems" 2>/dev/null || echo unreadable)"
if [ "$count" = "unreadable" ]; then
  echo "+++ :x: the headline check did not return a list of problems" >&2
  exit 1
fi
if [ "$count" -gt 0 ]; then
  echo "+++ :x: the hand-written performance headline disagrees with $(rel "$DATA") ($count problem(s))" >&2
  jq -r '.[] | "  " + .' <<<"$problems" >&2
  echo "Update the text to the committed figure (or the figure to the text's source run)." >&2
  exit 1
fi
echo "OK: the performance headline text agrees with $(rel "$DATA") ($(jq -r '.headline.healthy_ceiling_rps_display' "$DATA") req/s)"
