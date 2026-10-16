#!/usr/bin/env bash
# Docker-free checks for perf-test-run.sh's start-up path: the JVM diagnostics tiers, the env guards
# that run before any container starts, and wait_ready against a stub `docker` for every container
# state it must tell apart. The functions are lifted from the real script, not copied.
# Run: .buildkite/scripts/test/perf-run-ready-test.sh   (PERF_RUN_SCRIPT=<path> to test another copy)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env PERF_RUN_SCRIPT
F="${PERF_RUN_SCRIPT:-$REPO_ROOT/.buildkite/scripts/steps/perf-test-run.sh}"
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
WORK="$(mktemp -d "${TMPDIR:-/tmp}/perf-run-ready-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# A top-level function's text, from `name() {` to its closing `}` (one-liners included).
extract() {
  awk -v n="$1" '$0 ~ "^"n"\\(\\) *\\{" {p=1; print; if ($0 ~ /}$/) exit; next} p {print} p && /^}/ {exit}' "$F"
}
for fn in tier1_jvm_opts gc_log_jvm_opts tier2_jvm_opts diag_jvm_opts print_container_postmortem wait_ready; do
  body="$(extract "$fn")"
  if [ -z "$body" ]; then bad "function $fn not found in $F"; continue; fi
  eval "$body"
done
[ "$FAILS" -eq 0 ] || { echo "FAILED: $FAILS check(s)" >&2; exit 1; }

echo "--- 1. diag_jvm_opts per tier (standard and deep must not drift: they set the baseline's JVM)"
T1='-XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/diag/sut/heapdump.hprof'
GC='-Xlog:gc*,gc+heap=info:file=/diag/sut/gc-%p.log:time,uptime,level,tags:filecount=5,filesize=20m'
T2='-XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics -XX:StartFlightRecording=name=perfdiag,settings=profile,maxsize=256m,jdk.JavaMonitorEnter#threshold=1ms,jdk.CPUTimeSample#enabled=true -XX:FlightRecorderOptions=repository=/diag/sut/jfr-repo,stackdepth=128'
check "standard = tier 1 only" "$T1" "$(PERF_JVM_DIAGNOSTICS=standard diag_jvm_opts sut)"
check "gc = tier 1 + GC file log" "$T1 $GC" "$(PERF_JVM_DIAGNOSTICS=gc diag_jvm_opts sut)"
check "deep = tier 1 + tier 2" "$T1 $GC $T2" "$(PERF_JVM_DIAGNOSTICS=deep diag_jvm_opts sut)"
check "gc log follows the subdir" "-Xlog:gc*,gc+heap=info:file=/diag/info/gc-%p.log:time,uptime,level,tags:filecount=5,filesize=20m" \
  "$(gc_log_jvm_opts info)"

echo "--- 2. env guards: tier allow-list and the /diag/ refusal, before any temp dir or container"
GUARDS="$(awk '/^# JVM diagnostics tier/ {p=1} p {print} p && /^fi$/ {exit}' "$F")"
if [ -z "$GUARDS" ]; then
  bad "guard block (from '# JVM diagnostics tier' to its 'fi') not found"
else
  guard_rc() { # PERF_JVM_DIAGNOSTICS PERF_SERVER_JAVA_OPTS -> exit code of the guard block
    local rc=0
    env -u PERF_JVM_DIAGNOSTICS -u PERF_SERVER_JAVA_OPTS ${1:+PERF_JVM_DIAGNOSTICS="$1"} ${2:+PERF_SERVER_JAVA_OPTS="$2"} \
      bash -c "set -euo pipefail; $GUARDS" >/dev/null 2>&1 || rc=$?
    echo "$rc"
  }
  check "unset tier defaults to standard and passes" "0" "$(guard_rc "" "")"
  for t in standard gc deep; do check "tier $t accepted" "0" "$(guard_rc "$t" "")"; done
  for t in bogus Deep "gc " full; do check "tier '$t' refused" "1" "$(guard_rc "$t" "")"; done
  check "a heap/GC option in PERF_SERVER_JAVA_OPTS passes" "0" "$(guard_rc "" "-XX:+UseZGC -Xmx2g")"
  check "the stdout GC-log form passes" "0" "$(guard_rc "" "-Xlog:async -Xlog:gc*:stdout:time,uptime,level,tags")"
  check "a /diag/ file target is refused (build 542)" "1" "$(guard_rc "" "-Xlog:gc*:file=/diag/sut/gc-%p.log")"
fi
GUARD_LINE="$(grep -n '^# JVM diagnostics tier' "$F" | head -1 | cut -d: -f1)"
OUT_LINE="$(grep -n '^OUT_DIR="$(mktemp' "$F" | head -1 | cut -d: -f1)"
check "guards run before the OUT_DIR mktemp" "true" \
  "$([ -n "$GUARD_LINE" ] && [ -n "$OUT_LINE" ] && [ "$GUARD_LINE" -lt "$OUT_LINE" ] && echo true || echo false)"

echo "--- 3. wait_ready against a stub docker"
# STUB_STATES: one "<status> <health>" per poll, the last repeating; "missing" mimics docker 29,
# which prints an empty line to stdout and exits 1 for an unknown container.
POLLS="$WORK/polls"
docker() {
  case "$1" in
    inspect)
      if [[ "$*" == *"{{json .State}}"* ]]; then printf '{"Status":"stub","ExitCode":1}\n'; return 0; fi
      local n; n=$(( $(cat "$POLLS") + 1 )); echo "$n" > "$POLLS"
      local s; s="$(sed -n "${n}p" <<<"$STUB_STATES")"; [ -n "$s" ] || s="$(tail -1 <<<"$STUB_STATES")"
      if [ "$s" = missing ]; then echo ""; return 1; fi
      echo "$s" ;;
    logs) echo "stub-jvm-log-line" ;;
  esac
}
sleep() { :; }
ready() { # name  expected_rc  expected_polls  stderr_must_contain  states...
  local name="$1" want_rc="$2" want_polls="$3" want_msg="$4" rc=0; shift 4
  STUB_STATES="$(printf '%s\n' "$@")"; echo 0 > "$POLLS"
  wait_ready "c" 2>"$WORK/err" || rc=$?
  check "$name: rc" "$want_rc" "$rc"
  check "$name: polls" "$want_polls" "$(cat "$POLLS")"
  if [ -n "$want_msg" ]; then
    if grep -qF -- "$want_msg" "$WORK/err"; then ok "$name: says '$want_msg'"; else bad "$name: stderr lacks '$want_msg': $(head -3 "$WORK/err")"; fi
  fi
}
ready "healthy"                       0 1  ""                            "running healthy"
ready "no healthcheck"                0 1  ""                            "running nohealth"
ready "starting then healthy"         0 3  ""                            "running starting" "running starting" "running healthy"
ready "missing (docker 29 empty line)" 1 1 "exited early"                "missing"
ready "exited (kept, build 542)"      1 1  "is exited before"            "exited unhealthy"
ready "exited, prints its log"        1 1  "stub-jvm-log-line"           "exited unhealthy"
ready "dead"                          1 1  "is dead before"              "dead nohealth"
ready "removing (--rm dying)"         1 1  "is removing before"          "removing unhealthy"
ready "created (run -d never started)" 1 1 "is created before"           "created nohealth"
ready "dies after starting"           1 2  "is exited before"            "running starting" "exited unhealthy"
ready "unhealthy, not running"        1 1  "unhealthy and not running"   "paused unhealthy"
ready "running unhealthy keeps polling" 1 60 "did not become ready"      "running unhealthy"
ready "never ready: post-mortem"      1 60 "stub-jvm-log-line"           "running starting"

echo "--- 4. image revision vs harness commit: which commits are served the measured image"
SNAPSHOT_LIB="$REPO_ROOT/.buildkite/scripts/lib/snapshot-image-inputs.sh"
FNS="source '$SNAPSHOT_LIB'"$'\n'
for fn in forced_run image_revision_refusal harness_commit_measured; do
  body="$(extract "$fn")"
  if [ -z "$body" ]; then bad "function $fn not found in $F"; continue; fi
  FNS="$FNS$body"$'\n'
done
LAG_BLOCK="$(awk '/^# The checkout.s full SHA/ {p=1} p {print} p && /^  exit 1$/ {e=1} e && /^fi$/ {exit}' "$F")"
[ -n "$LAG_BLOCK" ] || bad "refusal block (from '# The checkout's full SHA' to the 'fi' after its exit) not found"
# A scratch history with no remote, so a commit it lacks cannot be fetched (fail closed).
G="$WORK/history"
git init -q "$G"
commit() { # <path> -> the new commit's SHA
  mkdir -p "$G/$(dirname "$1")"; echo "$RANDOM" >> "$G/$1"
  git -C "$G" add -A
  GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t git -C "$G" -c commit.gpgsign=false commit -q -m "$1"
  git -C "$G" rev-parse HEAD
}
JAVA_SHA="$(commit mockserver/core.java)"
DOCS_SHA="$(commit docs/notes.md)"
LATER_JAVA_SHA="$(commit mockserver/later.java)"
git -C "$G" checkout -q "$DOCS_SHA"
ABSENT_SHA="0123456789abcdef0123456789abcdef01234567"
# lag_rc <image revision> <BUILDKITE_COMMIT> [VAR=value...] -> exit code of the real block against a
# stub docker; the meta-data it sets is appended to $WORK/meta
lag_rc() {
  local rev="$1" commit="$2" rc=0; shift 2
  : > "$WORK/meta"
  env -u BUILDKITE_SOURCE -u BUILDKITE_MESSAGE -u BUILDKITE_BRANCH -u PERF_RELEASE_COMPARISON -u PERF_RUN_NAME \
    STUB_REV="$rev" BUILDKITE_COMMIT="$commit" REPO_ROOT="$G" MOCKSERVER_IMAGE="stub:snapshot" META="$WORK/meta" "$@" \
    bash -c "set -euo pipefail; docker() { printf '%s\n' \"\$STUB_REV\"; }
buildkite-agent() { if [ \"\$1\" = meta-data ]; then echo \"\$3=\$4\" >> \"\$META\"; else cat >/dev/null; fi; }
$FNS $LAG_BLOCK" >"$WORK/lag" 2>&1 || rc=$?
  echo "$rc"
}
check "[perf-run], image at an older java commit, tip changes mockserver/: refused" "1" "$(lag_rc "$JAVA_SHA" "$LATER_JAVA_SHA" BUILDKITE_MESSAGE="release [perf-run]")"
if grep -qF "built from $JAVA_SHA, and the harness commit $LATER_JAVA_SHA is not served that image" "$WORK/lag" && grep -qF "[perf-run]" "$WORK/lag"; then
  ok "  ... names both commits and the [perf-run] remedy"
else
  bad "  ... refusal text: $(head -3 "$WORK/lag")"
fi
check "  ... and flags the build as refused for the freshness watchdog" "perf_run_refused=image_revision" "$(cat "$WORK/meta")"
check "[perf-run], image at the last java commit, docs-only tip: runs" "0" "$(lag_rc "$JAVA_SHA" "$DOCS_SHA" BUILDKITE_MESSAGE="[perf-run]")"
check "  ... and clears the refused flag" "perf_run_refused=no" "$(cat "$WORK/meta")"
check "UI build, tip changes mockserver/: refused" "1" "$(lag_rc "$JAVA_SHA" "$LATER_JAVA_SHA" BUILDKITE_SOURCE=ui)"
check "UI build, image newer than the harness: refused" "1" "$(lag_rc "$LATER_JAVA_SHA" "$DOCS_SHA" BUILDKITE_SOURCE=ui)"
check "UI build, image newer than the harness by a docs-only commit: refused" "1" "$(lag_rc "$DOCS_SHA" "$JAVA_SHA" BUILDKITE_SOURCE=ui)"
check "UI build, symbolic HEAD commit, image current: runs" "0" "$(lag_rc "$DOCS_SHA" HEAD BUILDKITE_SOURCE=ui)"
check "[perf-run], image from a commit not in the clone: refused" "1" "$(lag_rc "$ABSENT_SHA" "$DOCS_SHA" BUILDKITE_MESSAGE="[perf-run]")"
check "[perf-run], image unlabelled: refused" "1" "$(lag_rc "" "$DOCS_SHA" BUILDKITE_MESSAGE="[perf-run]")"
check "scheduled run, tip changes mockserver/: runs (filed under the image revision)" "0" "$(lag_rc "$JAVA_SHA" "$LATER_JAVA_SHA" BUILDKITE_SOURCE=schedule)"
check "weekly scheduled [perf-run] build, tip changes mockserver/: runs" "0" "$(lag_rc "$JAVA_SHA" "$LATER_JAVA_SHA" BUILDKITE_SOURCE=schedule BUILDKITE_MESSAGE="Scheduled: weekly perf-xl headline run [perf-run] [perf-xl-weekly]")"
check "[perf-run] on a branch, tip changes mockserver/: runs" "0" "$(lag_rc "$JAVA_SHA" "$LATER_JAVA_SHA" BUILDKITE_MESSAGE="[perf-run]" BUILDKITE_BRANCH=harness-change)"
check "[perf-run] release comparison: runs" "0" "$(lag_rc "" "$DOCS_SHA" BUILDKITE_MESSAGE="[perf-run]" PERF_RELEASE_COMPARISON=8.0.0)"
check "a refused allocprofile step writes no meta-data" "1:" "$(lag_rc "$JAVA_SHA" "$LATER_JAVA_SHA" BUILDKITE_MESSAGE="[perf-run]" PERF_RUN_NAME=allocprofile):$(cat "$WORK/meta")"
REFUSAL_LINE="$(grep -n '^LAG_REFUSAL=' "$F" | head -1 | cut -d: -f1)"
START_LINE="$(grep -n '^echo "--- starting upstream + MockServer' "$F" | head -1 | cut -d: -f1)"
check "the refusal runs before the SUT starts" "true" \
  "$([ -n "$REFUSAL_LINE" ] && [ -n "$START_LINE" ] && [ "$REFUSAL_LINE" -lt "$START_LINE" ] && echo true || echo false)"

measured() { # ATTRIBUTED_COMMIT HARNESS_COMMIT PROVENANCE_OK -> 0 when the run may be recorded
  local rc=0
  REPO_ROOT="$G" ATTRIBUTED_COMMIT="$1" HARNESS_COMMIT="$2" PROVENANCE_OK="$3" bash -c "$FNS harness_commit_measured" || rc=$?
  echo "$rc"
}
check "image built from the harness commit: recorded" "0" "$(measured "$DOCS_SHA" "$DOCS_SHA" true)"
check "image at the last java commit, docs-only harness: recorded" "0" "$(measured "$JAVA_SHA" "$DOCS_SHA" true)"
check "mockserver/ changed since the image: not recorded" "1" "$(measured "$JAVA_SHA" "$LATER_JAVA_SHA" true)"
check "image newer than the harness commit: not recorded" "1" "$(measured "$DOCS_SHA" "$JAVA_SHA" true)"
check "image from a commit not in the clone: not recorded" "1" "$(measured "$ABSENT_SHA" "$DOCS_SHA" true)"
check "malformed label: not recorded" "1" "$(measured "$DOCS_SHA" "$DOCS_SHA" false)"
check "unlabelled image in the grace window: recorded as before" "0" "$(measured "$DOCS_SHA" "$DOCS_SHA" null)"
check "the ran-commit meta-data is set only under harness_commit_measured" "1" \
  "$(grep -B1 'meta-data set "perf_regression_ran_commit"' "$F" | grep -c 'if harness_commit_measured; then' || true)"

if [ "$FAILS" -gt 0 ]; then echo "FAILED: $FAILS check(s)" >&2; exit 1; fi
echo "all perf-run start-up checks passed"
