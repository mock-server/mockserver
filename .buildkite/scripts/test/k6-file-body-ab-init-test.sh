#!/bin/sh
# file_body_ab.js at init, through `k6 inspect` (no load, no server): its window layout, the knobs it
# refuses, and that its inline body (fbContent) is byte-identical to the file
# lib/perf-file-body-ab-content.awk writes for the SUT. POSIX sh: perf-test-lint.sh runs it inside the
# k6 container; --include-system-env-vars=false keeps the build's environment out.
# Locally: K6_BIN=/path/to/k6 .buildkite/scripts/test/k6-file-body-ab-init-test.sh
set -eu

K6="${K6_BIN:-k6}"
HERE="$(cd "$(dirname "$0")" && pwd)"
JS="$HERE/../../../mockserver-performance-test/k6/file_body_ab.js"
AWK_PROG="$HERE/../steps/lib/perf-file-body-ab-content.awk"
command -v "$K6" >/dev/null 2>&1 || { echo "ERROR: no k6 binary ('$K6'): set K6_BIN" >&2; exit 2; }
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILS=0
bad() { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }

# windows <name> <expected "scenario@startTime ..."> [-e VAR=value...]
windows() {
  name="$1"; want="$2"; shift 2
  if ! out="$("$K6" inspect --include-system-env-vars=false "$@" "$JS" 2>&1)"; then
    bad "$name: refused: $(printf '%s\n' "$out" | grep -o 'msg="[^"]*' | head -1)"; return 0
  fi
  got="$(printf '%s\n' "$out" | awk '/^    "[0-9a-z_]+": \{$/ { s = $1; gsub(/[":]/, "", s) } /"startTime": "/ { t = $2; gsub(/[",]/, "", t); printf "%s@%s ", s, t }' | sed 's/ $//')"
  if [ "$got" = "$want" ]; then echo "  ok   $name"; else bad "$name: expected '$want', got '$got'"; fi
}
# refuses <name> <message> [-e VAR=value...]
refuses() {
  name="$1"; message="$2"; shift 2
  if out="$("$K6" inspect --include-system-env-vars=false "$@" "$JS" 2>&1)"; then bad "$name: accepted"; return 0; fi
  case "$out" in
    *"$message"*) echo "  ok   $name" ;;
    *) bad "$name: refused without '$message'" ;;
  esac
}

echo "--- file_body_ab.js: back-to-back windows after the warm-up"
windows "the defaults: 1kb then 64kb, file first, 35 s windows 2 s apart" \
  "1kb_file@22s 1kb_inline@59s 64kb_file@1m36s 64kb_inline@2m13s"
windows "inline first (odd builds)" "1kb_file@59s 1kb_inline@22s 64kb_file@2m13s 64kb_inline@1m36s" -e K6_FB_ORDER=inline,file
windows "one size, shorter windows" "4kb_file@12s 4kb_inline@34s" -e K6_FB_SIZES=4kb -e K6_FB_WARMUP=10s -e K6_FB_DURATION=20s

echo "--- file_body_ab.js: knobs it refuses"
refuses "an unknown arm order" "K6_FB_ORDER" -e K6_FB_ORDER=file,file
refuses "a size not in kb" "is not <n>kb" -e K6_FB_SIZES=1mb
refuses "a repeated size" "distinct sizes" -e K6_FB_SIZES=1kb,1kb
refuses "a settle as long as the window" "K6_FB_SETTLE shorter" -e K6_FB_SETTLE=35s
refuses "a fractional rate" "K6_FB_RATE" -e K6_FB_RATE=1.5

echo "--- file_body_ab.js: the inline body matches the awk-written file"
for size in 1kb 64kb; do
  awk -v label="$size" -v bytes="$(( ${size%kb} * 1024 ))" -f "$AWK_PROG" > "$WORK/fb-ab-$size.json"
done
if out="$("$K6" inspect --include-system-env-vars=false -e K6_FB_SELFTEST_DIR="$WORK" "$JS" 2>&1)"; then
  echo "  ok   1kb and 64kb identical"
else
  bad "fbContent differs from the awk file: $(printf '%s\n' "$out" | grep -o 'msg="[^"]*' | head -1)"
fi
printf ' ' >> "$WORK/fb-ab-64kb.json"
refuses "one extra byte in the file is caught" "differs from perf-file-body-ab-content.awk" -e K6_FB_SELFTEST_DIR="$WORK"

if [ "$FAILS" -gt 0 ]; then echo "FAILED: $FAILS check(s)" >&2; exit 1; fi
echo "all file_body_ab.js init checks passed"
