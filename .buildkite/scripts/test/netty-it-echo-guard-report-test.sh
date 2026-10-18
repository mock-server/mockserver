#!/usr/bin/env bash
# Fixture checks for java-netty-it-echo-guard-report.sh and its EXIT trap in java-netty-it-shard.sh:
# a firing is printed, annotated and collected; a clean shard prints and annotates nothing; the
# report never fails the step, and runs when the shard's tests fail.
# Run: .buildkite/scripts/test/netty-it-echo-guard-report-test.sh
# shellcheck disable=SC2016  # check() evals its single-quoted condition
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
STEPS="$REPO_ROOT/.buildkite/scripts/steps"
REPORT="$STEPS/java-netty-it-echo-guard-report.sh"
GUARD_SOURCE="$REPO_ROOT/mockserver/mockserver-integration-testing/src/main/java/org/mockserver/testing/integration/mock/SharedEchoServerGuard.java"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/echo-guard-report-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
FAILS=0
ok()  { echo "  ok   $1"; }
bad() { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }

mkdir -p "$WORK/fake-bin"
cat > "$WORK/fake-bin/buildkite-agent" <<'EOF'
#!/usr/bin/env bash
{ echo "ARGS: $*"; [ "${1:-}" = "annotate" ] && cat; } >> "$FAKE_AGENT_LOG"
exit 0
EOF
chmod +x "$WORK/fake-bin/buildkite-agent"
export FAKE_AGENT_LOG="$WORK/agent.log"

HEADER="$("$REPORT" --print-header)"
check "the header is derived from the guard's DIAGNOSTIC_HEADER" \
  '[ -n "$HEADER" ] && grep -qF "DIAGNOSTIC_HEADER = \"$HEADER\";" "$GUARD_SOURCE"'

write_firing() { # <file> <port>
  printf 'INFO some test output\n\n%s\ninsecure echo server on port %s did not answer within 2000ms (2 attempts); replacing it\nopen file descriptors: 120 of 1048576\nthread dump (2 threads):\n"main" #1 RUNNABLE\n\tat java.lang.Thread.run(Thread.java:1)\n\nINFO later output\n' \
    "$HEADER" "$2" > "$1"
}

run_report() { # <case-dir> <shard> [agent]; output in <case-dir>/out, agent log in $FAKE_AGENT_LOG
  local dir="$1" rc=0
  : > "$FAKE_AGENT_LOG"
  env PATH="$WORK/fake-bin:$PATH" ECHO_GUARD_AGENT="${3:-buildkite-agent}" ECHO_GUARD_REPORTS_DIR="$dir/reports" ECHO_GUARD_COLLECT_DIR="$dir/collected" \
    "$REPORT" "$2" > "$dir/out" 2>&1 || rc=$?
  echo "$rc" > "$dir/rc"
}

echo "--- a shard with no failsafe reports"
mkdir -p "$WORK/none"
run_report "$WORK/none" mock
check "exits 0" '[ "$(cat "$WORK/none/rc")" = 0 ]'
check "prints nothing" '[ ! -s "$WORK/none/out" ]'
check "annotates nothing" '[ ! -s "$FAKE_AGENT_LOG" ]'

echo "--- a clean shard"
mkdir -p "$WORK/clean/reports"
printf 'INFO ordinary output\nSHARED ECHO SERVER\nthread dump: printed with the first %s\n' "$HEADER" \
  > "$WORK/clean/reports/org.example.CleanIntegrationTest-output.txt"
run_report "$WORK/clean" mock
check "exits 0" '[ "$(cat "$WORK/clean/rc")" = 0 ]'
check "prints nothing" '[ ! -s "$WORK/clean/out" ]'
check "annotates nothing" '[ ! -s "$FAKE_AGENT_LOG" ]'
check "collects nothing" '[ ! -e "$WORK/clean/collected" ]'

echo "--- a shard where the guard fired"
mkdir -p "$WORK/fired/reports"
cp "$WORK/clean/reports/"* "$WORK/fired/reports/"
write_firing "$WORK/fired/reports/org.example.ForwardIntegrationTest-output.txt" 40123
run_report "$WORK/fired" proxy-http
check "exits 0" '[ "$(cat "$WORK/fired/rc")" = 0 ]'
check "prints the firing class" 'grep -qF "org.example.ForwardIntegrationTest: 1 firing(s)" "$WORK/fired/out"'
check "prints the diagnostic block" 'grep -qF "$HEADER" "$WORK/fired/out" && grep -qF "on port 40123 did not answer" "$WORK/fired/out" && grep -qF "thread dump (2 threads)" "$WORK/fired/out"'
check "does not name the clean class" '! grep -qF CleanIntegrationTest "$WORK/fired/out"'
check "annotates with the shard's warning context" 'grep -qF "ARGS: annotate --style warning --context shared-echo-guard-proxy-http" "$FAKE_AGENT_LOG"'
check "the annotation names the class and reason" 'grep -qF "org.example.ForwardIntegrationTest" "$FAKE_AGENT_LOG" && grep -qF "on port 40123 did not answer" "$FAKE_AGENT_LOG"'
check "the annotation's markdown code spans are not backslash-escaped" '! grep -qF "\\\`" "$FAKE_AGENT_LOG" && grep -qF "\`mockserver/target/failed-tests/shared-echo-guard/proxy-http/\`" "$FAKE_AGENT_LOG"'
check "copies the output file for upload" '[ -f "$WORK/fired/collected/proxy-http/org.example.ForwardIntegrationTest-output.txt" ]'
check "collects only the firing class" '[ ! -e "$WORK/fired/collected/proxy-http/org.example.CleanIntegrationTest-output.txt" ]'

echo "--- a second firing in one fork, which names the header in its thread-dump line"
mkdir -p "$WORK/twice/reports"
write_firing "$WORK/twice/reports/org.example.TwiceIntegrationTest-output.txt" 40123
{
  printf '\n%s\nsecure echo server on port 40124 did not answer within 2000ms (2 attempts); replacing it\nopen file descriptors: 130 of 1048576\nthread dump: printed with the first %s\nINFO unrelated line after the diagnostic\n' "$HEADER" "$HEADER"
  printf 'INFO raw body \000\001\377 bytes\n'
} >> "$WORK/twice/reports/org.example.TwiceIntegrationTest-output.txt"
run_report "$WORK/twice" mock
check "exits 0" '[ "$(cat "$WORK/twice/rc")" = 0 ]'
check "counts only whole header lines as firings" 'grep -qF "org.example.TwiceIntegrationTest: 2 firing(s)" "$WORK/twice/out"'
check "annotates both reasons" 'grep -qF "on port 40123 did not answer" "$FAKE_AGENT_LOG" && grep -qF "on port 40124 did not answer" "$FAKE_AGENT_LOG"'
check "annotates no line that follows the thread-dump reference" '! grep -qF "INFO unrelated line" "$FAKE_AGENT_LOG"'
check "annotates exactly two reasons" '[ "$(grep -c "^  - " "$FAKE_AGENT_LOG")" = 2 ]'

echo "--- a firing without buildkite-agent"
mkdir -p "$WORK/no-agent/reports"
cp "$WORK/fired/reports/"* "$WORK/no-agent/reports/"
run_report "$WORK/no-agent" rest "$WORK/absent/buildkite-agent"
check "exits 0" '[ "$(cat "$WORK/no-agent/rc")" = 0 ]'
check "still prints the firing" 'grep -qF "org.example.ForwardIntegrationTest: 1 firing(s)" "$WORK/no-agent/out"'
check "calls no agent" '[ ! -s "$FAKE_AGENT_LOG" ]'

echo "--- a guard source the header cannot be read from"
mkdir -p "$WORK/no-source/reports"
: > "$FAKE_AGENT_LOG"
rc=0
env PATH="$WORK/fake-bin:$PATH" ECHO_GUARD_SOURCE="$WORK/missing.java" ECHO_GUARD_REPORTS_DIR="$WORK/no-source/reports" \
  "$REPORT" mock > "$WORK/no-source/out" 2>&1 || rc=$?
check "exits 0" '[ "$rc" = 0 ]'
check "warns in the log and in an annotation" 'grep -qF "could not read DIAGNOSTIC_HEADER" "$WORK/no-source/out" && grep -qF "could not read DIAGNOSTIC_HEADER" "$FAKE_AGENT_LOG"'
rc=0
env ECHO_GUARD_SOURCE="$WORK/missing.java" "$REPORT" --print-header > /dev/null 2>&1 || rc=$?
check "--print-header fails when it cannot derive the header" '[ "$rc" = 1 ]'

echo "--- java-netty-it-shard.sh reports a firing when the shard's tests fail"
SHARD_ROOT="$WORK/shard-root"
mkdir -p "$SHARD_ROOT/.buildkite/scripts/steps"
cp "$STEPS/java-netty-it-shard.sh" "$STEPS/java-netty-it-echo-guard-report.sh" "$STEPS/java-collect-failures.sh" \
  "$SHARD_ROOT/.buildkite/scripts/steps/"
cat > "$SHARD_ROOT/.buildkite/scripts/run-in-docker.sh" <<'EOF'
#!/usr/bin/env bash
mkdir -p mockserver/mockserver-netty/target/failsafe-reports
printf '%s\nsecure echo server has no bound port; replacing it\n' "$FIXTURE_HEADER" \
  > mockserver/mockserver-netty/target/failsafe-reports/org.example.WedgedIntegrationTest-output.txt
exit 7
EOF
chmod +x "$SHARD_ROOT/.buildkite/scripts/run-in-docker.sh"
: > "$FAKE_AGENT_LOG"
rc=0
env PATH="$WORK/fake-bin:$PATH" ECHO_GUARD_SOURCE="$GUARD_SOURCE" FIXTURE_HEADER="$HEADER" \
  "$SHARD_ROOT/.buildkite/scripts/steps/java-netty-it-shard.sh" mock > "$WORK/shard.out" 2>&1 || rc=$?
check "keeps the shard's exit code" '[ "$rc" = 7 ]'
check "prints the firing" 'grep -qF "org.example.WedgedIntegrationTest: 1 firing(s)" "$WORK/shard.out"'
check "annotates it" 'grep -qF "ARGS: annotate --style warning --context shared-echo-guard-mock" "$FAKE_AGENT_LOG"'
check "collects it under the step's failed-tests artefact path" \
  '[ -f "$SHARD_ROOT/mockserver/target/failed-tests/shared-echo-guard/mock/org.example.WedgedIntegrationTest-output.txt" ]'

echo ""
if [ "$FAILS" -gt 0 ]; then
  echo "FAILED: $FAILS check(s)" >&2
  exit 1
fi
echo "All echo-guard report checks passed"
