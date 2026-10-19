#!/usr/bin/env bash
# The published headline is the perf-xl multi-k6 arm's GC-masked ceiling. This checks the
# path from the arm's result to the page data:
#   1. lib/perf-rw-multik6-series.jq: which results are members of the published series
#   2. perf-test-compare.sh with PERF_COMPARE_RESULT_ARTIFACT: only a member is persisted (bash 4+)
#   3. perf-website-publish.sh PERF_PUBLISH_SOURCE=rw_multik6: only a member is published, and
#      each source rewrites only its own part of the page data, before and after the switch
#   4. lib/perf-website-figures-check.jq: a multi-k6 headline the publish step did not write is refused,
#      in the committed file (checked here on every lint) and by the publish step
#   5. the shapes in which the run's first failure is not "the next rate tested": published, without it
# Stub aws (a local directory as the bucket) and buildkite-agent; no S3, no Docker.
# Run: .buildkite/scripts/test/perf-xl-publish-test.sh   (PERF_XL_PUBLISH_STEPS=<dir> to test a copy of steps/)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# shellcheck source=lib/perf-test-env.sh
. "$REPO_ROOT/.buildkite/scripts/test/lib/perf-test-env.sh"
perf_test_scrub_env PERF_XL_PUBLISH_STEPS
STEPS="${PERF_XL_PUBLISH_STEPS:-$REPO_ROOT/.buildkite/scripts/steps}"
BUDGETS="$REPO_ROOT/mockserver-performance-test/perf-budgets.json"
FAILS=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1" >&2; FAILS=$((FAILS + 1)); }
check() { # name expected actual
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected '$2', got '$3'"; fi
}
# Every helper below prints a sentinel when it cannot read its subject, so a check whose expected
# value is "" or "no" cannot pass on a missing file or a failed command.
has() { [ -f "$2" ] || { echo MISSING; return 0; }; grep -qF -- "$1" "$2" && echo yes || echo no; } # text file
contexts() { [ -s "$1" ] || { echo NONE; return 0; }; sed -n 's/.*--context \([^ ]*\).*/\1/p' "$1" | sort -u | tr '\n' ' ' | sed 's/ $//'; } # annotate log
T="$(mktemp -d "${TMPDIR:-/tmp}/perf-xl-publish-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
B="$T/bucket"; mkdir -p "$T/bin" "$B"

# A member of the published series: ceiling 136k (masked p99 8.1 ms), first failure at 144k.
jq -n '
  def pt($o; $a; $p95; $p99): {offered_rps: $o, achieved_rps: $a, p50_ms: 0.1, p95_ms: $p95, p99_ms: $p99, error_rate: 0};
  def rung($o; $p99; $q): {offered_rps: $o, p99_ms: $p99, seconds: {measured: 11, quiet: $q, gc: (11 - $q), incomplete: 0}};
  {offered_rps: 136000, healthy_ceiling_rps: 136000, healthy_ceiling_rps_display: "136,000", healthy_ceiling_achieved_rps: 135915.7,
   healthy_ceiling_p50_ms: 0.1, healthy_ceiling_p95_ms: 2.5, healthy_ceiling_p99_ms: 8.1, p99_max_ms: 10,
   peak_achieved_rps: 143344.1, peak_offered_rps: 144000, peak_p50_ms: 0.1, peak_p95_ms: 6.4, server_cores: 6,
   no_measured_overload: false, lower_bound: false, lower_bound_reason: null} as $h
  | {schema_version: 3, commit: "0123456789abcdef0123456789abcdef01234567", harness_commit: "fedcba9876543210fedcba9876543210fedcba98",
     branch: "master", timestamp_utc: "2026-10-03T15:52:14Z", build_number: "634", build_url: "https://example.invalid/634",
     run_arm: "rw_multik6", baseline_eligible: false, baseline_ineligible_reasons: ["arm_only"],
     agent: {instance_type: "c6i.32xlarge", instance_type_source: "observed", queue: "perf-xl", server_cpus: "0-5", server_physical_cores: 6},
     config: {image_stale: false, config_profile: "default", rig_profile: "default", jvm_diagnostics: "standard",
              event_log_budget: {method: "shipped-default"}, mockserver_version: "8.0.1-SNAPSHOT", gc: "ZGC", jdk: "25",
              heap_max_bytes: 966787072, log_level: "ERROR", disable_system_out: "true", image_digest: "sha256:fixture"},
     validity: {valid: true, checks: [{name: "fixture", ok: true, detail: ""}]}, clustered_attempted: false,
     serving_rw_multik6: {
       valid: true, headline: ($h | del(.offered_rps)),
       headline_rule: {name: "gc_masked_p99", p99_max_ms: 10, unmasked_ceiling_rps: 128000,
                       condition_3: {ok: true, first_failure: {offered_rps: 144000, p99_ms: 10.5, unmasked_p99_ms: 42.2, quiet_s: 8}}},
       gc_masked: {available: true, report_only: false, p99_max_ms: 10, min_quiet_s: 3, healthy_ceiling: {rps: 136000, p99_ms: 8.1},
                   rungs: [rung(120000; 3.4; 11), rung(128000; 4.5; 11), rung(136000; 8.1; 10), rung(144000; 10.5; 8)]},
       method: {procs: 4, ladder: {profile: "multi_socket", source: "default"}, ab: {trial: false}},
       placement: {baseline_eligible: true, layout: "numa_split"},
       config: {k6_runtime: {gogc: "1600", source: {gogc: "default", vu_ceiling: "derived"}}},
       sweep: {latency_window: {settle_s: 3},
               points: [pt(120000; 120001.9; 0.4; 3.2), pt(128000; 127966.2; 1.1; 4.4), pt(136000; 135915.7; 2.5; 20.1), pt(144000; 143344.1; 6.4; 42.2)]},
       saturation: {client_cores: 16, ladder: [{offered_rps: 120000, rig_valid: true}, {offered_rps: 128000, rig_valid: true},
                                               {offered_rps: 136000, rig_valid: true}, {offered_rps: 144000, rig_valid: true}]}}}' > "$T/member.json"
mut() { jq "$1" "$T/member.json"; } # jq filter -> a variant of the member on stdout
# Four shapes in which the run's first failure (picked over every rung, counted or not) is not
# "the next rate tested" with a masked p99. Each is a member and is persisted and published; only
# the page's sentence about the first failure is left out.
# 1. A lower bound: every rung above the ceiling was client-limited, so none of them is counted.
LB='.serving_rw_multik6.headline |= (.healthy_ceiling_rps = 120000 | .healthy_ceiling_achieved_rps = 120001.9 | .healthy_ceiling_p95_ms = 0.4
        | .healthy_ceiling_p99_ms = 3.4 | .lower_bound = true | .lower_bound_reason = "the next rate up was limited by the load generator"
        | .no_measured_overload = true | .peak_achieved_rps = null | .peak_offered_rps = null | .peak_p50_ms = null | .peak_p95_ms = null)
    | .serving_rw_multik6.gc_masked.healthy_ceiling = {rps: 120000, p99_ms: 3.4}
    | .serving_rw_multik6.saturation.ladder |= map(if .offered_rps > 120000 then .rig_valid = false | .client_limited = true else . end)'
ADD152='.serving_rw_multik6.sweep.points += [{offered_rps: 152000, achieved_rps: 151731.2, p50_ms: 0.1, p95_ms: 9.1, p99_ms: 42.1, error_rate: 0}]
    | .serving_rw_multik6.gc_masked.rungs += [{offered_rps: 152000, p99_ms: 18.7, seconds: {measured: 11, quiet: 7, gc: 4, incomplete: 0}}]
    | .serving_rw_multik6.saturation.ladder += [{offered_rps: 152000, rig_valid: true}]
    | .serving_rw_multik6.headline |= (.peak_achieved_rps = 151731.2 | .peak_offered_rps = 152000 | .peak_p95_ms = 9.1)'
# 2. The rung above the ceiling (144k) was excluded; the next counted rung is 152k.
EXCL="$ADD152 | .serving_rw_multik6.saturation.ladder |= map(if .offered_rps == 144000 then .rig_valid = false else . end)"
# 3. The next rung (144k) held the bound but served 93% of its rate; the first to fail the bound is 152k.
THR="$ADD152 | (.serving_rw_multik6.sweep.points[] | select(.offered_rps == 144000) | .achieved_rps) = 133920
    | (.serving_rw_multik6.gc_masked.rungs[] | select(.offered_rps == 144000) | .p99_ms) = 9
    | .serving_rw_multik6.headline_rule.condition_3.first_failure = {offered_rps: 152000, p99_ms: 18.7, unmasked_p99_ms: 42.1, quiet_s: 7}"
# 4. The next rung has no masked figure (too few quiet seconds), which fails the bound.
NOMASK='(.serving_rw_multik6.gc_masked.rungs[] | select(.offered_rps == 144000)) |= (.p99_ms = null | .seconds.quiet = 2 | .reason = "fewer than 3 quiet seconds")
    | .serving_rw_multik6.headline_rule.condition_3 = {ok: false, first_failure: {offered_rps: 144000, p99_ms: null, unmasked_p99_ms: 42.2, quiet_s: 2, reason: "fewer than 3 quiet seconds"}}'
mut "$LB" > "$T/v-lb.json"; mut "$EXCL" > "$T/v-excl.json"; mut "$THR" > "$T/v-thr.json"; mut "$NOMASK" > "$T/v-nomask.json"

echo "--- 1. series membership (lib/perf-rw-multik6-series.jq)"
unmet() { # result on stdin -> the names of the unmet criteria; UNREADABLE unless the check gave one array of strings
  local out
  out="$(jq -c -f "$STEPS/lib/perf-rw-multik6-series.jq" 2>/dev/null)" || { echo UNREADABLE; return 0; }
  jq -ers 'if length == 1 and (.[0] | type) == "array" and all(.[0][]; type == "string")
           then .[0] | map(split(":")[0]) | join(",") else error("not one array of strings") end' <<<"$out" 2>/dev/null || echo UNREADABLE
}
check "a result the check cannot read is not a member (no input, not JSON, a wrong shape)" "UNREADABLE|UNREADABLE|UNREADABLE" \
  "$(unmet < /dev/null)|$(echo '{"run_arm":' | unmet)|$(mut '.serving_rw_multik6 = "x"' | unmet)"
check "the fixture is a member" "" "$(unmet < "$T/member.json")"
check "the SUT GC file log tier is a member" "" "$(mut '.config.jvm_diagnostics = "gc" | .baseline_ineligible_reasons = ["jvm_diagnostics", "arm_only"]' | unmet)"
check "the deep tier is not" "eligibility" "$(mut '.config.jvm_diagnostics = "deep" | .baseline_ineligible_reasons = ["jvm_diagnostics", "arm_only"]' | unmet)"
check "observed instrumentation at the standard tier is not" "eligibility" "$(mut '.baseline_ineligible_reasons = ["jvm_diagnostics", "arm_only"]' | unmet)"
check "a tuned server is not" "eligibility" "$(mut '.baseline_ineligible_reasons = ["config_profile", "arm_only"]' | unmet)"
check "a result with no reasons recorded (an older producer) is not" "eligibility" "$(mut 'del(.baseline_ineligible_reasons)' | unmet)"
check "a reasons list that is not arm-only at all is not" "eligibility" "$(mut '.baseline_ineligible_reasons = []' | unmet)"
check "a trial is not" "trial" "$(mut '.serving_rw_multik6.method.ab.trial = true' | unmet)"
check "a result that does not say whether it is a trial is not" "trial" "$(mut 'del(.serving_rw_multik6.method.ab)' | unmet)"
check "the whole-rung rule is not" "rule" "$(mut '.serving_rw_multik6.headline_rule.name = "unmasked_p99"' | unmet)"
check "another bound is not (a number, never the string \"10\")" "bound|bound" \
  "$(mut '.serving_rw_multik6.headline_rule.p99_max_ms = 20' | unmet)|$(mut '.serving_rw_multik6.headline_rule.p99_max_ms = "10"' | unmet)"
check "another quiet-seconds floor is not" "min_quiet" "$(mut '.serving_rw_multik6.gc_masked.min_quiet_s = 2' | unmet)"
check "another queue is not" "queue" "$(mut '.agent.queue = "perf"' | unmet)"
check "an invalid arm is not" "valid|valid" "$(mut '.serving_rw_multik6.valid = false' | unmet)|$(mut '.validity.valid = false' | unmet)"
check "a stale image, a forced event-log budget, another ladder, k6 on the SUT's node are not" "image_fresh|event_log_budget|ladder|placement" \
  "$(mut '.config.image_stale = true' | unmet)|$(mut '.config.event_log_budget.method = "forced"' | unmet)|$(mut '.serving_rw_multik6.method.ladder.source = "env"' | unmet)|$(mut '.serving_rw_multik6.placement.baseline_eligible = false' | unmet)"
check "another k6 GOGC or VU ceiling is not" "k6_gogc|vu_ceiling" \
  "$(mut '.serving_rw_multik6.config.k6_runtime.gogc = "400"' | unmet)|$(mut '.serving_rw_multik6.config.k6_runtime.source.vu_ceiling = "env"' | unmet)"
check "no headline is not" "headline" "$(mut '.serving_rw_multik6.headline = null' | unmet)"
check "the hardware-matrix arm is not" "run_arm" "$(mut '.run_arm = "hw_matrix"' | unmet)"

cat > "$T/bin/aws" <<'STUB'
#!/usr/bin/env bash
# s3 cp <src> <dst> and s3 ls s3://<bucket>/<prefix> --recursive, against $FAKE_BUCKET.
case "$1 $2" in
  "s3 cp")
    if [[ "$4" == s3://* ]]; then k="${4#s3://*/}"; mkdir -p "$FAKE_BUCKET/$(dirname "$k")"; cp "$3" "$FAKE_BUCKET/$k"
    else cp "$FAKE_BUCKET/${3#s3://*/}" "$4"; fi ;;
  "s3 ls")
    [ "${FAKE_LS_FAIL:-}" != empty ] || exit 0
    [ -z "${FAKE_LS_FAIL:-}" ] || { echo "An error occurred (AccessDenied) when calling the ListObjectsV2 operation" >&2; exit 254; }
    p="${3#s3://*/}"
    [ -d "$FAKE_BUCKET/$p" ] && (cd "$FAKE_BUCKET" && find "$p" -type f | sort | sed 's/^/2026-10-01 00:00:00 100 /') ;;
  *) exit 1 ;;
esac
STUB
cat > "$T/bin/buildkite-agent" <<'STUB'
#!/usr/bin/env bash
if [ "$1 $2" = "artifact download" ]; then
  [ "$3" = "$HEAD_NAME" ] && cp "$HEAD_RESULT" "$4/$HEAD_NAME" && exit 0
  # The daily run's supplementary artifacts, present in the same build.
  [ -n "${SUPP_DIR:-}" ] && [ -f "$SUPP_DIR/$3" ] && cp "$SUPP_DIR/$3" "$4/$3" && exit 0
  exit 1
fi
if [ "$1 $2" = "artifact upload" ]; then echo "$3" >> "${UPLOAD_LOG:-/dev/null}"; exit 0; fi
if [ "$1 $2" = "meta-data set" ]; then [ -z "${STUB_META_SET_FAIL:-}" ] || exit 1; echo "$3=$4" >> "$META_LOG"; exit 0; fi
if [ "$1 $2" = "meta-data get" ]; then echo "$3" >> "$META_LOG"; printf '%s' "${STUB_META:-}"; exit 0; fi
if [ "$1" = annotate ]; then echo "$*" >> "${ANNOTATE_LOG:-/dev/null}"; cat > /dev/null; fi
exit 0
STUB
chmod +x "$T/bin/aws" "$T/bin/buildkite-agent"
objects() { [ -d "$B" ] || { echo NO-BUCKET; return 0; }; find "$B" -type f | sed "s|^$B/||" | sort | tr '\n' ' '; }
meta() { [ -f "$T/$1.meta" ] || { echo MISSING; return 0; }; cat "$T/$1.meta"; } # name -> the meta-data calls the run made

echo "--- 2. compare persists only a member (perf-test-compare.sh, PERF_COMPARE_RESULT_ARTIFACT)"
ARM_ARTIFACT=perfxl-rw-multik6-perf-result.json
# The six supplementary artifacts the daily compare merges into its result, each with its own key.
mkdir -p "$T/supp"
echo '{"microbench": {"fixture.time_per_op": 1}}' > "$T/supp/perf-microbench.json"
echo '{"microbench_extra": {"fixture.time_per_op": 1}}' > "$T/supp/perf-microbench-extra.json"
echo '{"points": [], "supp_sweep": true}' > "$T/supp/perf-sweep.json"
echo '{"scaling": {"fixture": 1}}' > "$T/supp/perf-scaling.json"
echo '{"churn": {"fixture": 1}}' > "$T/supp/perf-churn.json"
echo '{"h2_multiplex": {"fixture": 1}}' > "$T/supp/perf-h2-multiplex.json"
compare() { # name result_file [artifact_env_value [uploaded_name [compare_script]]] -> $T/<name>.log .rc .meta .ann
  # COMPARE_LS_FAIL=1|empty: the history listing fails, or returns nothing. COMPARE_META_SET_FAIL=1: meta-data set fails.
  local rc=0 art="${3-$ARM_ARTIFACT}" name="${4:-$ARM_ARTIFACT}" script="${5:-$STEPS/perf-test-compare.sh}"
  : > "$T/$1.meta"; : > "$T/$1.ann"
  env -i PATH="$T/bin:$PATH" HOME="${HOME:-/tmp}" TMPDIR="$T" FAKE_BUCKET="$B" HEAD_RESULT="$2" HEAD_NAME="$name" META_LOG="$T/$1.meta" \
    ANNOTATE_LOG="$T/$1.ann" SUPP_DIR="$T/supp" FAKE_LS_FAIL="${COMPARE_LS_FAIL:-}" STUB_META_SET_FAIL="${COMPARE_META_SET_FAIL:-}" \
    PERF_COMPARE_RESULT_ARTIFACT="$art" PERF_BUDGETS_FILE="$BUDGETS" PERF_BUDGETS_COMMIT=fixture bash "$script" >"$T/$1.log" 2>&1 || rc=$?
  echo "$rc" > "$T/$1.rc"
}
if [ "$(bash -c 'echo "${BASH_VERSINFO[0]}"')" -lt 4 ]; then
  # compare needs bash 4+ (mapfile). macOS ships 3.2, so off-CI this part skips loudly; in CI it fails.
  if [ "${BUILDKITE:-}" = "true" ]; then echo "FAIL: bash on PATH is older than 4; perf-test-compare.sh cannot run" >&2; exit 1; fi
  echo "  SKIP part 2: bash on PATH is older than 4 (perf-test-compare.sh needs mapfile); run this under bash 4+, e.g. in a Linux container"
else
  MEMBER_KEY="runs-perf-xl/master/2026-10-03T15-52-14Z__0123456789.json"
  compare member "$T/member.json"
  check "a member: exit 0, persisted under runs-perf-xl/, and nowhere else" "0|$MEMBER_KEY " "$(cat "$T/member.rc")|$(objects)"
  check "  ... and it tells the multi-k6 publish step, not the daily one" "perf-xl-persisted-key=$MEMBER_KEY" "$(meta member)"
  check "  ... annotating under the arm's own context, which the daily compare in the same build cannot replace" "perf-regression-xl" "$(contexts "$T/member.ann")"
  check "  ... with none of the daily run's six supplementary artifacts merged into it" "" \
    "$(jq -r '[("microbench", "microbench_extra", "scaling", "churn", "h2_multiplex") as $k | select(has($k)) | $k] + [select(.sweep.supp_sweep == true) | "sweep"] | join(",")' "$B/$MEMBER_KEY" 2>/dev/null || echo UNREADABLE)"
  rm -rf "$B/runs-perf-xl"
  COMPARE_META_SET_FAIL=1 compare metafail "$T/member.json"
  check "the key cannot be recorded: the warning is under the arm's own context too" "perf-regression-xl perf-xl-persisted-key" "$(contexts "$T/metafail.ann")"
  rm -rf "$B/runs-perf-xl"
  for v in lb excl thr nomask; do
    compare "v-$v" "$T/v-$v.json"
    check "a member whose first failure is not the page's sentence ($v): exit 0, persisted" "0|$MEMBER_KEY |perf-xl-persisted-key=$MEMBER_KEY" \
      "$(cat "$T/v-$v.rc")|$(objects)|$(meta "v-$v")"
    rm -rf "$B/runs-perf-xl"
  done
  not_persisted() { # name -> exit code | objects in the bucket | meta-data set | says why
    echo "$(cat "$T/$1.rc")|$(objects)|$(meta "$1")|$(has 'not a member of the published series' "$T/$1.log")"
  }
  mut '.serving_rw_multik6.method.ab.trial = true' > "$T/trial.json"; compare trial "$T/trial.json"
  check "a trial: exit 0, nothing persisted, no key, and it says why" "0|||yes" "$(not_persisted trial)"
  check "  ... naming the unmet criterion" "yes" "$(has '- trial: ' "$T/trial.log")"
  mut '.serving_rw_multik6.headline_rule.name = "unmasked_p99"' > "$T/unmasked.json"; compare unmasked "$T/unmasked.json"
  check "the whole-rung rule: exit 0, nothing persisted" "0|||yes" "$(not_persisted unmasked)"
  mut '.baseline_ineligible_reasons = ["config_profile", "arm_only"]' > "$T/tuned.json"; compare tuned "$T/tuned.json"
  check "a tuned server: exit 0, nothing persisted" "0|||yes" "$(not_persisted tuned)"
  mut 'del(.baseline_ineligible_reasons)' > "$T/noreasons.json"; compare noreasons "$T/noreasons.json"
  check "no reasons recorded: exit 0, nothing persisted" "0|||yes" "$(not_persisted noreasons)"
  mut '.validity.valid = false | .validity.checks[0].ok = false' > "$T/invalid.json"; compare invalid "$T/invalid.json"
  check "an invalid run: exit 1, nothing persisted" "1||" "$(cat "$T/invalid.rc")|$(objects)|$(meta invalid)"
  mut '.serving_rw_multik6.headline.healthy_ceiling_rps = 0' > "$T/zero.json"; compare zero "$T/zero.json"
  check "an implausible ceiling: exit 1, nothing persisted" "1||yes" "$(cat "$T/zero.rc")|$(objects)|$(has 'IMPLAUSIBLE' "$T/zero.log")"
  compare daily-name "$T/member.json" perf-result.json perf-result.json
  check "an arm-only result under the daily name: exit 1, nothing persisted" "1||yes" \
    "$(cat "$T/daily-name.rc")|$(objects)|$(has 'result and artifact disagree' "$T/daily-name.log")"
  check "  ... and the daily result's step annotates under the daily context" "perf-regression" "$(contexts "$T/daily-name.ann")"
  mut 'del(.run_arm)' > "$T/noarm.json"; compare noarm "$T/noarm.json"
  check "a daily-shaped result under the arm's name: exit 1, nothing persisted" "1||yes" \
    "$(cat "$T/noarm.rc")|$(objects)|$(has 'result and artifact disagree' "$T/noarm.log")"
  mut '.run_arm = "hw_matrix"' > "$T/hw.json"; compare hw "$T/hw.json"
  check "the hardware-matrix arm's result: exit 1, nothing persisted" "1|" "$(cat "$T/hw.rc")|$(objects)"
  compare badname "$T/member.json" '../x.json'
  check "an unusable artifact name: exit 1, nothing persisted" "1||yes" "$(cat "$T/badname.rc")|$(objects)|$(has 'unusable result artifact name' "$T/badname.log")"
  mkdir -p "$T/nolib/steps/lib" && cp -R "$STEPS/../lib" "$T/nolib/lib" && cp "$STEPS/perf-test-compare.sh" "$T/nolib/steps/" \
    && find "$STEPS/lib" -type f ! -name perf-rw-multik6-series.jq -exec cp {} "$T/nolib/steps/lib/" \;
  compare nolib "$T/member.json" "$ARM_ARTIFACT" "$ARM_ARTIFACT" "$T/nolib/steps/perf-test-compare.sh"
  check "the series check cannot run: exit 1, nothing persisted" "1||yes" \
    "$(cat "$T/nolib.rc")|$(objects)|$(has 'series check failed to run' "$T/nolib.log")"

  mut '.serving_rw_multik6.headline.healthy_ceiling_rps = 144000' > "$T/wrong.json"; compare wrong "$T/wrong.json"
  check "a member whose headline is not its own GC-masked ceiling: exit 1, nothing persisted" "1|||yes" \
    "$(cat "$T/wrong.rc")|$(objects)|$(meta wrong)|$(has 'not self-consistent' "$T/wrong.log")"
  mut '.serving_rw_multik6.sweep.points = "not a ladder"' > "$T/terr.json"; compare terr "$T/terr.json"
  check "a member the transform errors on: exit 1, nothing persisted" "1|||yes" \
    "$(cat "$T/terr.rc")|$(objects)|$(meta terr)|$(has 'the transform errored or returned no problems list' "$T/terr.log")"
  COMPARE_LS_FAIL=1 compare lsfail "$T/member.json"
  check "the history cannot be listed: exit 1, said so, not reported as warming up" "1|yes|no" \
    "$(cat "$T/lsfail.rc")|$(has 'history could not be listed' "$T/lsfail.log")|$(has 'Perf baseline warming up' "$T/lsfail.log")"
  COMPARE_LS_FAIL=empty compare lsempty "$T/member.json"
  check "  ... nor when the listing lacks the run this step has just written" "1|yes|no" \
    "$(cat "$T/lsempty.rc")|$(has 'history could not be listed' "$T/lsempty.log")|$(has 'Perf baseline warming up' "$T/lsempty.log")"
  rm -rf "$B/runs-perf-xl"

  # History: four runs at the standard SUT diagnostics tier and two at the gc tier. A head at
  # the standard tier has only four comparable runs, so it is not compared yet; a fifth compares it.
  # The history objects are written straight to the bucket, with a higher ceiling than the head's.
  HIGHER='.serving_rw_multik6.headline.healthy_ceiling_rps = 168000'
  seed() { mkdir -p "$B/runs-perf-xl/master"; mut "$2 | .timestamp_utc = \"2026-10-0${1}T04:00:00Z\"" > "$B/runs-perf-xl/master/2026-10-0${1}T04-00-00Z__0123456789.json"; }
  for i in 1 2 3 4; do seed "$i" "$HIGHER"; done
  for i in 5 6; do seed "$i" '.config.jvm_diagnostics = "gc" | .baseline_ineligible_reasons = ["jvm_diagnostics", "arm_only"] | .serving_rw_multik6.headline.healthy_ceiling_rps = 104000'; done
  mut '.timestamp_utc = "2026-10-08T04:00:00Z"' > "$T/lower.json"
  compare lower4 "$T/lower.json"
  check "runs at another SUT diagnostics tier are not its baseline: four comparable, not compared yet" "0|yes|yes" \
    "$(cat "$T/lower4.rc")|$(has '| serving_rw_multik6.healthy_ceiling_rps | 136000 | n/a | n/a | notify-only | :new: new |' "$T/lower4.log")|$(has 'SUT diagnostics standard' "$T/lower4.log")"
  rm -f "$B/runs-perf-xl/master/2026-10-08T04-00-00Z__0123456789.json"
  seed 7 "$HIGHER"
  compare lower "$T/lower.json"
  check "a lower ceiling is flagged against five runs of its own series, notify-only (exit 0)" "0|yes|yes" \
    "$(cat "$T/lower.rc")|$(has '| serving_rw_multik6.healthy_ceiling_rps | 136000 | 168000 |' "$T/lower.log")|$(has 'all notify-only, build NOT failed' "$T/lower.log")"
  check "  ... without reporting the daily run's clustered profile as skipped" "no" "$(has 'Clustered state A/B' "$T/lower.log")"
  rm -rf "$B/runs-perf-xl"
fi

echo "--- 3. publish (perf-website-publish.sh, PERF_PUBLISH_SOURCE=rw_multik6)"
# A throwaway repository holding committed figures in the legacy (single-k6) layout.
R="$T/repo"; SITE="$R/jekyll-www.mock-server.com"; mkdir -p "$SITE/_data" "$SITE/images/perf-charts/data"
LEGACY='{"source": {"published_utc": "2026-09-27T19:15:43Z", "instance_type": "c5.12xlarge", "sweep_latency_settle_s": 3},
  "headline": {"healthy_ceiling_rps": 60000, "healthy_ceiling_p50_ms": 0.179, "peak_achieved_rps": 60000},
  "throughput_ladder": [{"offered_rps": 60000, "p99_ms": 32.6}],
  "behaviours": [{"key": "match_http", "p95_ms": 1.5}], "hw_matrix": {"source": {"run_timestamp_utc": "2026-09-20T00:00:00Z"}, "points": []},
  "behaviours_status": "published", "withheld_internal": ["x"]}'
jq . <<<"$LEGACY" > "$SITE/_data/perf_figures.json"
echo '{"points":[]}' > "$SITE/images/perf-charts/data/perf-sweep.json"
git -C "$R" init -q && git -C "$R" add -A && git -C "$R" -c user.name=t -c user.email=t@t commit -qm base
COMMITTED="$SITE/_data/perf_figures.json"
RW_KEY="runs-perf-xl/master/2026-10-03T15-52-14Z__0123456789.json"
put() { mkdir -p "$B/$(dirname "$2")"; cp "$1" "$B/$2"; } # file key
publish() { # name [--meta-key] [env...] -> $T/<name>.log .rc; meta-data keys read in .meta, annotate calls in .ann, uploads in .up
  local n="$1" rc=0 key="PERF_PUBLISH_PERSISTED_KEY=$RW_KEY"; shift
  # --meta-key: the key comes from the build meta-data (the stub logs which name was asked for).
  if [ "${1:-}" = --meta-key ]; then key="STUB_META=$RW_KEY"; shift; fi
  : > "$T/$n.meta"; : > "$T/$n.ann"; : > "$T/$n.up"
  # BUILDKITE_BUILD_NUMBER: the publish step runs in the build that measured the run (the fixture's 634).
  env PATH="$T/bin:$PATH" FAKE_BUCKET="$B" META_LOG="$T/$n.meta" ANNOTATE_LOG="$T/$n.ann" UPLOAD_LOG="$T/$n.up" PERF_PUBLISH_AWS_BIN="$T/bin/aws" PERF_PUBLISH_REPO_ROOT="$R" \
    PERF_PUBLISH_SOURCE=rw_multik6 BUILDKITE_BUILD_NUMBER=634 "$key" "$@" bash "$STEPS/perf-website-publish.sh" > "$T/$n.log" 2>&1 || rc=$?
  echo "$rc" > "$T/$n.rc"
}
untouched() { [ -z "$(git -C "$R" status --porcelain)" ] && [ "$(git -C "$R" rev-list --count HEAD)" = "$1" ] && echo yes || echo no; } # commits
keys_changed() { jq -rn --slurpfile a "$1" --slurpfile b "$2" '[($a[0] + $b[0] | keys_unsorted[]) as $k | select($a[0][$k] != $b[0][$k]) | $k] | join(",")'; }

# Before the switch: the daily source over the single-k6 layout leaves it a single-k6 layout.
jq -n '{schema_version: 2, timestamp_utc: "2026-10-04T04:00:00Z", build_number: "640", validity: {valid: true}, config: {mockserver_version: "x"},
        agent: {instance_type: "c5.12xlarge"},
        sweep: {latency_window: {settle_s: 3}, points: [{offered_rps: 60000, achieved_rps: 60000, p50_ms: 0.2}, {offered_rps: 70000, achieved_rps: 70000, p50_ms: 0.2},
                         {offered_rps: 80000, achieved_rps: 70000, p50_ms: 0.9}]},
        saturation: {ladder: [{offered_rps: 60000, rig_valid: true}, {offered_rps: 70000, rig_valid: true}, {offered_rps: 80000, rig_valid: true}]}}' > "$T/daily.json"
DAILY_KEY="runs/master/2026-10-04T04-00-00Z__0123456789.json"; put "$T/daily.json" "$DAILY_KEY"
publish daily-before PERF_PUBLISH_SOURCE=single_k6 PERF_PUBLISH_PERSISTED_KEY="$DAILY_KEY" PERF_PUBLISH_DRY_RUN=true PERF_PUBLISH_OUT="$T/daily-before.json"
check "before the switch, the daily source refreshes the top level and adds no multi-k6 block" "0|70000|false|false" \
  "$(cat "$T/daily-before.rc")|$(jq -r '[.headline.healthy_ceiling_rps, has("headline_rule"), has("single_k6")] | join("|")' "$T/daily-before.json")"
check "  ... annotating under the daily context" "perf-website-publish" "$(contexts "$T/daily-before.ann")"

put "$T/member.json" "$RW_KEY"
publish dry PERF_PUBLISH_DRY_RUN=true PERF_PUBLISH_OUT="$T/dry.json"
check "a member over legacy figures: a patch would be emitted (dry-run exit 0)" "0|yes" "$(cat "$T/dry.rc")|$(has 'WOULD emit a patch' "$T/dry.log")"
check "  ... because nothing from this source is committed yet" "yes" "$(has 'hold nothing from the perf-xl multi-k6 arm yet' "$T/dry.log")"
check "  ... annotating under its own context, which the daily publish in the same build cannot replace" "perf-website-publish-rw" "$(contexts "$T/dry.ann")"
check "  ... and saying which rung is expected: 136000 is, and this run read it" "yes|yes" \
  "$(has 'a ceiling of 136000 req/s is expected; 128000 is the other rung the qualifying runs read and would lower the headline' "$T/dry.log")|$(has 'This run reads 136000 (the expected rung)' "$T/dry.log")"
check "the headline is the run's own, with its rule" "136000|8.1|20.1|gc_masked_p99|10|3" \
  "$(jq -r '[.headline.healthy_ceiling_rps, .headline.healthy_ceiling_p99_ms, .headline.healthy_ceiling_p99_whole_rung_ms, .headline_rule.name, .headline_rule.p99_max_ms, .headline_rule.min_quiet_s] | join("|")' "$T/dry.json")"
check "the provenance is the run's" "c6i.32xlarge|perf-xl|634|4|standard" \
  "$(jq -r '.source | [.instance_type, .queue, .build_number, .k6_processes, .jvm_diagnostics] | join("|")' "$T/dry.json")"
check "each rung carries both p99s, and rungs past the ceiling are marked" "20.1/8.1/false 42.2/10.5/true" \
  "$(jq -r '[.throughput_ladder[-2:][] | "\(.p99_ms)/\(.p99_gc_masked_ms)/\(.degraded)"] | join(" ")' "$T/dry.json")"
check "the single-k6 figures move under .single_k6, unchanged" "$(jq -c '{source, headline, throughput_ladder}' "$COMMITTED")" "$(jq -c .single_k6 "$T/dry.json")"
check "behaviours, hardware sizes and the notes are carried forward unchanged" "" \
  "$(jq -rn --slurpfile a "$COMMITTED" --slurpfile b "$T/dry.json" '[("behaviours", "hw_matrix", "behaviours_status", "withheld_internal") as $k | select($a[0][$k] != $b[0][$k]) | $k] | join(",")' 2>/dev/null || echo UNREADABLE)"
check "the transform's own problems list is not published" "false" "$(jq 'has("problems")' "$T/dry.json")"
check "a dry-run writes nothing" "yes" "$(untouched 1)"
check "it reads the multi-k6 key from the meta-data, not the daily one" "perf-xl-persisted-key|0" \
  "$(publish meta --meta-key PERF_PUBLISH_DRY_RUN=true; sort -u "$T/meta.meta" | tr '\n' ' ' | sed 's/ $//')|$(cat "$T/meta.rc")"

refused() { echo "$(cat "$T/$1.rc")|$(has "$2" "$T/$1.log")|$(untouched 1)"; } # name annotation-text -> rc|said so|nothing written
mut '.serving_rw_multik6.method.ab.trial = true' > "$T/p-trial.json"; put "$T/p-trial.json" "$RW_KEY"; publish p-trial
check "a trial in the history is never published (exit 1, nothing written)" "1|yes|yes" "$(refused p-trial 'NOT A MEMBER OF THE PUBLISHED SERIES')"
mut '.serving_rw_multik6.headline_rule.name = "unmasked_p99"' > "$T/p-unm.json"; put "$T/p-unm.json" "$RW_KEY"; publish p-unm
check "a whole-rung-rule run is never published" "1|yes|yes" "$(refused p-unm 'NOT A MEMBER OF THE PUBLISHED SERIES')"
mut '.validity.valid = false | .serving_rw_multik6.valid = false' > "$T/p-invalid.json"; put "$T/p-invalid.json" "$RW_KEY"; publish p-invalid
check "an invalid run is never published" "1|yes|yes" "$(refused p-invalid 'NEWEST RUN IS INVALID')"
mut '.serving_rw_multik6.headline.healthy_ceiling_rps = 144000' > "$T/p-wrong.json"; put "$T/p-wrong.json" "$RW_KEY"; publish p-wrong
check "a headline that is not the run's GC-masked ceiling is never published" "1|yes|yes" "$(refused p-wrong 'is not the GC-masked ceiling')"
mut '.serving_rw_multik6.saturation.ladder[2].rig_valid = false' > "$T/p-excl.json"; put "$T/p-excl.json" "$RW_KEY"; publish p-excl
check "a ceiling on a rung the run excluded is never published" "1|yes|yes" "$(refused p-excl 'is not a rung the run counted as rig-valid')"
mut '.serving_rw_multik6.gc_masked.rungs[2].p99_ms = 12' > "$T/p-over.json"; put "$T/p-over.json" "$RW_KEY"; publish p-over
check "a ceiling whose masked p99 is over the bound is never published" "1|yes|yes" "$(refused p-over 'is missing or over the bound')"
mut '.serving_rw_multik6.gc_masked.report_only = true' > "$T/p-report.json"; put "$T/p-report.json" "$RW_KEY"; publish p-report
check "a report-only masked figure is never published" "1|yes|yes" "$(refused p-report 'did not set the headline')"
mut '.serving_rw_multik6.headline.p99_max_ms = 20' > "$T/p-bound.json"; put "$T/p-bound.json" "$RW_KEY"; publish p-bound
check "a headline bound that is not the rule's bound is never published" "1|yes|yes" "$(refused p-bound 'is not headline_rule.p99_max_ms')"
mut '.serving_rw_multik6.headline.healthy_ceiling_p99_ms = 7' > "$T/p-p99.json"; put "$T/p-p99.json" "$RW_KEY"; publish p-p99
check "a headline p99 that is not the ceiling rung's masked p99 is never published" "1|yes|yes" "$(refused p-p99 'is not the ceiling rung masked p99')"
mut '.serving_rw_multik6.headline.healthy_ceiling_achieved_rps = 136000' > "$T/p-ach.json"; put "$T/p-ach.json" "$RW_KEY"; publish p-ach
check "a headline achieved rate that is not the ceiling rung's is never published" "1|yes|yes" "$(refused p-ach 'headline achieved rate')"
mut '.serving_rw_multik6.headline.peak_achieved_rps = 150000' > "$T/p-peak.json"; put "$T/p-peak.json" "$RW_KEY"; publish p-peak
check "a peak that no rig-valid rung achieved is never published" "1|yes|yes" "$(refused p-peak 'is not the achieved rate of a rig-valid rung')"
# The four shapes: published, without a first failure for the page's "next rate tested" sentence.
ff_of() { jq -c '.headline_rule.first_failure | if . == null then "none" else [.offered_rps, .p99_gc_masked_ms] end' "$1" 2>/dev/null || echo UNREADABLE; } # page data
check "the first failure is published when it is the next counted rung and has a masked p99" "[144000,10.5]" "$(ff_of "$T/dry.json")"
for v in lb excl thr nomask; do
  put "$T/v-$v.json" "$RW_KEY"; publish "pv-$v" PERF_PUBLISH_DRY_RUN=true PERF_PUBLISH_OUT="$T/pv-$v.json"
  check "a first failure that is not that ($v): the run is published (dry-run exit 0), the sentence's figure is not" "0|yes|\"none\"" \
    "$(cat "$T/pv-$v.rc")|$(has 'WOULD emit a patch' "$T/pv-$v.log")|$(ff_of "$T/pv-$v.json")"
done
check "  ... the lower bound is published as one, with its counted rungs only, no measured overload and no peak" "true|120000|120000|true|true" \
  "$(jq -r '[.headline.lower_bound, .headline.healthy_ceiling_rps, .throughput_ladder[-1].offered_rps, .headline.no_measured_overload, (.headline.peak_achieved_rps == null)] | join("|")' "$T/pv-lb.json" 2>/dev/null || echo UNREADABLE)"
check "  ... and a run that measured past its ceiling says so, with its peak" "false|143344.1" \
  "$(jq -r '[.headline.no_measured_overload, .headline.peak_achieved_rps] | join("|")' "$T/dry.json" 2>/dev/null || echo UNREADABLE)"
# The page states the first failure in one sentence, guarded on its masked p99 as well.
PAGE="$REPO_ROOT/jekyll-www.mock-server.com/mock_server/performance.html"
uses() { [ -f "$PAGE" ] || { echo MISSING; return 0; }; grep -cF -- "$1" "$PAGE" || true; } # text -> lines of the page containing it
check "the page renders the first failure once, and only when it has a masked p99" "1|1|0" \
  "$(uses 'rule.first_failure.offered_display')|$(uses '{% if rule.first_failure and rule.first_failure.p99_gc_masked_ms %} The next rate tested, {{ rule.first_failure.offered_display }}')|$(uses '{% if rule.first_failure %}')"
# The page counts the like-for-like runs per rate by hand: those sentences render only while the
# published ceiling is one of the two rates the publish step names, and say where it falls.
check "the page gates the run tally on both rates the runs read, and on the multi-k6 state" "1|1|1" \
  "$(uses '{% if rule %}{% if hc == 136000 %}{% assign spread_side = "most common" %}{% elsif hc == 128000 %}{% assign spread_side = "lowest" %}{% endif %}{% endif %}')|$(uses '{% capture ceiling_spread %}{% if spread_side != "" %}')|$(uses '{% capture ceiling_spread_detail %}{% if spread_side != "" %}')"
check "  ... states it beside the headline bullet, under the table and in the per-instance paragraph, with the near-miss detail once" "3|1" \
  "$(uses '{{ ceiling_spread }}')|$(uses '{{ ceiling_spread_detail }}')"
check "  ... and says to provision against the lowest rate only while the most common one is published" "2|1" \
  "$(uses '{% if spread_side == "most common" %} Provision against the lowest of those rates (128,000&nbsp;req/s).')|$(uses '{% else %} Provision against the healthy ceiling.{% endif %}')"
check "the page labels the masked p99 wherever it states the bound, and shows the whole-step ceiling from the data" "1|1|1" \
  "$(uses 'its p99 with load-generator garbage collection left out was {{ rule.first_failure.p99_gc_masked_ms }}')|$(uses 'its p99 with load-generator garbage collection left out (the second p99 column')|$(uses "Judged on the first column, this run's ceiling would be {{ rule.unmasked_ceiling_rps_display }}")"
check "  ... the excluded rung is not in the ladder, and the rung that could not keep up is marked past the ceiling" "120000,128000,136000,152000|144000:true" \
  "$(jq -r '[.throughput_ladder[].offered_rps] | join(",")' "$T/pv-excl.json" 2>/dev/null || echo UNREADABLE)|$(jq -r '.throughput_ladder[] | select(.offered_rps == 144000) | "\(.offered_rps):\(.degraded)"' "$T/pv-thr.json" 2>/dev/null || echo UNREADABLE)"
check "  ... the rung with no masked figure is listed without one" "null:true" \
  "$(jq -r '.throughput_ladder[] | select(.offered_rps == 144000) | "\(.p99_gc_masked_ms):\(.degraded)"' "$T/pv-nomask.json" 2>/dev/null || echo UNREADABLE)"
mut '.serving_rw_multik6 = "x"' > "$T/p-err.json"; put "$T/p-err.json" "$RW_KEY"; publish p-err
check "a run the series check errors on is never published" "1|yes|yes" "$(refused p-err 'SERIES CHECK FAILED TO RUN')"
# The rung to publish is a manual check, so the step says what it read.
LOW='.serving_rw_multik6.headline |= (.healthy_ceiling_rps = 128000 | .healthy_ceiling_achieved_rps = 127966.2 | .healthy_ceiling_p99_ms = 4.5)
    | .serving_rw_multik6.gc_masked.healthy_ceiling.rps = 128000'
mut "$LOW" > "$T/p-low.json"; put "$T/p-low.json" "$RW_KEY"; publish low PERF_PUBLISH_DRY_RUN=true
check "the rung below the expected one needs the owner's decision" "0|yes" \
  "$(cat "$T/low.rc")|$(has "This run reads 128000 (the other rung the qualifying runs read: get the owner's decision before applying)" "$T/low.log")"
# Qualifying runs read 144k too, but the owner chose the rung below it: a run there is not applied.
ABOVE='.serving_rw_multik6.headline |= (.healthy_ceiling_rps = 144000 | .healthy_ceiling_achieved_rps = 143344.1 | .healthy_ceiling_p99_ms = 9.5)
    | .serving_rw_multik6.gc_masked.healthy_ceiling.rps = 144000 | .serving_rw_multik6.gc_masked.rungs[3].p99_ms = 9.5
    | .serving_rw_multik6.headline_rule.condition_3.first_failure = null'
mut "$ABOVE" > "$T/p-above.json"; put "$T/p-above.json" "$RW_KEY"; publish above PERF_PUBLISH_DRY_RUN=true
check "any other rung is marked do-not-apply, the one above the expected rung included" "0|yes|no" \
  "$(cat "$T/above.rc")|$(has 'This run reads 144000 (neither: do not apply)' "$T/above.log")|$(has 'This run reads 144000 (the' "$T/above.log")"
# 152k is two steps above the expected rung, and no qualifying run read it.
HIGH="$ADD152 | .serving_rw_multik6.headline |= (.healthy_ceiling_rps = 152000 | .healthy_ceiling_achieved_rps = 151731.2 | .healthy_ceiling_p95_ms = 9.1 | .healthy_ceiling_p99_ms = 9.9)
    | .serving_rw_multik6.gc_masked.healthy_ceiling = {rps: 152000, p99_ms: 9.9}
    | (.serving_rw_multik6.gc_masked.rungs[] | select(.offered_rps == 144000) | .p99_ms) = 9.5
    | (.serving_rw_multik6.gc_masked.rungs[] | select(.offered_rps == 152000) | .p99_ms) = 9.9
    | .serving_rw_multik6.headline_rule.condition_3.first_failure = null"
mut "$HIGH" > "$T/p-high.json"; put "$T/p-high.json" "$RW_KEY"; publish high PERF_PUBLISH_DRY_RUN=true
check "  ... and a rung no qualifying run read" "0|yes|no" \
  "$(cat "$T/high.rc")|$(has 'This run reads 152000 (neither: do not apply)' "$T/high.log")|$(has 'This run reads 152000 (the' "$T/high.log")"
put "$T/member.json" "$RW_KEY"
publish p-daily-key PERF_PUBLISH_PERSISTED_KEY="runs/master/2026-10-03T15-52-14Z__0123456789.json"
check "a key from the daily history is not this source's: unchanged, exit 0" "0|yes|yes" "$(refused p-daily-key 'this build did not persist a baseline')"
publish p-source PERF_PUBLISH_SOURCE=other
check "an unknown source fails (exit 1)" "1|yes|yes" "$(refused p-source 'UNKNOWN PUBLISH SOURCE')"
check "  ... under neither source's annotation context" "perf-website-publish-unknown-source" "$(contexts "$T/p-source.ann")"
mkdir -p "$T/nolib/steps/lib" && cp "$STEPS/perf-website-publish.sh" "$T/nolib/steps/" \
  && find "$STEPS/lib" -type f ! -name perf-rw-multik6-series.jq -exec cp {} "$T/nolib/steps/lib/" \;
rc=0; env PATH="$T/bin:$PATH" FAKE_BUCKET="$B" META_LOG=/dev/null PERF_PUBLISH_AWS_BIN="$T/bin/aws" PERF_PUBLISH_REPO_ROOT="$R" PERF_PUBLISH_SOURCE=rw_multik6 BUILDKITE_BUILD_NUMBER=634 \
  PERF_PUBLISH_PERSISTED_KEY="$RW_KEY" bash "$T/nolib/steps/perf-website-publish.sh" > "$T/p-nolib.log" 2>&1 || rc=$?
echo "$rc" > "$T/p-nolib.rc"
check "the series check is missing: exit 1, nothing written" "1|yes|yes" "$(refused p-nolib 'SERIES CHECK MISSING')"

publish real
PATCHED="$(git -C "$R" diff --name-only HEAD~1 HEAD 2>/dev/null | tr '\n' ' ')"
check "a member: the patch commit rewrites the data file alone (charts stay single-k6)" "0|jekyll-www.mock-server.com/_data/perf_figures.json " "$(cat "$T/real.rc")|$PATCHED"
check "  ... and equals the dry-run candidate, published_utc and the dry-run mark aside" \
  "$(jq -c 'del(.source.published_utc, .source.published_from.dry_run)' "$T/dry.json")" "$(jq -c 'del(.source.published_utc, .source.published_from.dry_run)' "$COMMITTED")"
check "  ... stamped with the key, the build and the series result it was published from" "$RW_KEY|634|false|[]" \
  "$(jq -r '.source.published_from | [.key, .publish_build_number, (.dry_run | tostring), (.series_unmet | tojson)] | join("|")' "$COMMITTED")"
check "  ... leaving the chart data and the rest of the checkout untouched" "" "$(git -C "$R" status --porcelain 2>&1 || echo GIT-FAILED)"
check "  ... with a commit subject naming the run" "docs(perf): refresh the published multi-k6 headline from ${RW_KEY##*/}" "$(git -C "$R" log -1 --format=%s)"
check "  ... and a commit body and annotation saying which rung this run read: the expected one" "1|yes" \
  "$(git -C "$R" log -1 --format=%b | grep -c 'This run reads 136000 (the expected rung)' || true)|$(has 'This run reads 136000 (the expected rung)' "$T/real.log")"
check "  ... the annotation lists the hand-counted runs to reconcile whatever the ceiling, apart from what a moved headline needs" "yes|yes|yes" \
  "$(has 'whatever the ceiling: the `ceiling_spread` and `ceiling_spread_detail` sentences and the `schema_faq` answers' "$T/real.log")|$(has 'so a later member run changes them even when it reads the same ceiling' "$T/real.log")|$(has 'if any published figure moved: the figures in the page `description`' "$T/real.log")"
# A PERF_XL build can emit a patch from each source: this one's patch and data artifact are named apart.
check "  ... uploading a patch and page data named apart from the daily source's" "perf_figures-multi-k6.json website-headline-multi-k6-TS.patch" \
  "$(sed 's/[0-9]\{8\}-[0-9]\{6\}/TS/' "$T/real.up" | sort | tr '\n' ' ' | sed 's/ $//')"
check "  ... under the multi-k6 context only" "perf-website-publish-rw" "$(contexts "$T/real.ann")"

echo "--- 4. with the multi-k6 layout committed, each source rewrites only its own part"
publish same PERF_PUBLISH_DRY_RUN=true
check "the same run again: current, no patch (exit 0)" "0|yes" "$(cat "$T/same.rc")|$(has 'no patch emitted' "$T/same.log")"
check "  ... still prompting for the hand-counted runs, which a run at the same ceiling changes" "yes|yes" \
  "$(has 'Still reconcile by hand:** the `ceiling_spread` and `ceiling_spread_detail` sentences' "$T/same.log")|$(has 'This run read 136000 req/s.' "$T/same.log")"
mut '.config.jvm_diagnostics = "gc" | .baseline_ineligible_reasons = ["jvm_diagnostics", "arm_only"]' > "$T/p-gc.json"; put "$T/p-gc.json" "$RW_KEY"
publish tier PERF_PUBLISH_DRY_RUN=true
check "a run at another SUT diagnostics tier says the rule changed" "0|yes" "$(cat "$T/tier.rc")|$(has 'headline rule changed' "$T/tier.log")"
put "$T/v-lb.json" "$RW_KEY"
BEFORE="$(git -C "$R" rev-list --count HEAD)"
publish lb
check "a lower-bound ceiling below the committed multi-k6 one is held (exit 1, nothing written)" "1|yes|yes" \
  "$(cat "$T/lb.rc")|$(has 'HELD — the new multi-k6 headline is a lower bound' "$T/lb.log")|$(untouched "$BEFORE")"
# The daily single-k6 source over the multi-k6 layout: a higher single-k6 ceiling.
publish daily PERF_PUBLISH_SOURCE=single_k6 PERF_PUBLISH_PERSISTED_KEY="$DAILY_KEY" PERF_PUBLISH_DRY_RUN=true PERF_PUBLISH_OUT="$T/daily-out.json"
check "the daily source: a patch would be emitted (exit 0)" "0|yes" "$(cat "$T/daily.rc")|$(has 'WOULD emit a patch' "$T/daily.log")"
check "  ... judged against the committed single-k6 figures, not the multi-k6 headline" "yes" "$(has 'a headline metric moved 16.7%' "$T/daily.log")"
check "  ... changing only .single_k6 and the blocks the daily run owns" "single_k6,behaviours,behaviours_status,withheld_internal" "$(keys_changed "$COMMITTED" "$T/daily-out.json")"
check "  ... with the new single-k6 ceiling under .single_k6" "70000|c5.12xlarge" "$(jq -r '[.single_k6.headline.healthy_ceiling_rps, .single_k6.source.instance_type] | join("|")' "$T/daily-out.json")"
check "  ... and the committed hardware sizes carried forward" "$(jq -c .hw_matrix "$COMMITTED")" "$(jq -c .hw_matrix "$T/daily-out.json")"
# The run tally is the multi-k6 headline's: a daily run that leaves the page as it is does not ask for it.
publish daily-same PERF_PUBLISH_SOURCE=single_k6 PERF_PUBLISH_PERSISTED_KEY="$DAILY_KEY" PERF_PUBLISH_DRY_RUN=true PUBLISH_MOVE_PCT=1000 PUBLISH_MAX_AGE_DAYS=100000
check "a daily run inside both windows: current, no patch (exit 0), and no prompt for the multi-k6 run tally" "0|yes|no|no" \
  "$(cat "$T/daily-same.rc")|$(has 'no patch emitted' "$T/daily-same.log")|$(has 'Still reconcile by hand' "$T/daily-same.log")|$(has 'ceiling_spread' "$T/daily-same.log")"

echo "--- 5. a multi-k6 headline the publish step did not write is refused (lib/perf-website-figures-check.jq)"
problems() { # file -> the first word of each problem; UNREADABLE unless the file exists and the check gave one array of strings
  local out
  [ -f "$1" ] || { echo UNREADABLE; return 0; }
  out="$(jq -c -f "$STEPS/lib/perf-website-figures-check.jq" "$1" 2>/dev/null)" || { echo UNREADABLE; return 0; }
  jq -ers 'if length == 1 and (.[0] | type) == "array" and all(.[0][]; type == "string")
           then .[0] | map(split(":")[0] | split(" ")[0]) | join(",") else error("not one array of strings") end' <<<"$out" 2>/dev/null || echo UNREADABLE
}
vary() { jq "$1" "$COMMITTED" > "$T/vary.json"; problems "$T/vary.json"; }
check "the page data committed in this repository passes" "" "$(problems "$REPO_ROOT/jekyll-www.mock-server.com/_data/perf_figures.json")"
check "single-k6 figures alone (before the switch) pass" "" "$(jq . <<<"$LEGACY" > "$T/legacy.json"; problems "$T/legacy.json")"
check "what the publish step wrote in its build passes" "" "$(problems "$COMMITTED")"
check "a missing file, an empty one, truncated JSON and unmerged conflict markers do not" "UNREADABLE|UNREADABLE|UNREADABLE|UNREADABLE" \
  "$(problems "$T/absent.json")|$(: > "$T/empty.json"; problems "$T/empty.json")|$(head -c 200 "$COMMITTED" > "$T/cut.json"; problems "$T/cut.json")|$( { echo '<<<<<<< ours'; cat "$COMMITTED"; echo '======='; cat "$COMMITTED"; echo '>>>>>>> theirs'; } > "$T/conflict.json"; problems "$T/conflict.json")"
check "two documents in one file, or page data that is not an object, do not" "UNREADABLE|the" \
  "$(cat "$COMMITTED" "$COMMITTED" > "$T/twice.json"; problems "$T/twice.json")|$(vary '[.]')"
check "a stamp that is not the publish step's object does not" "source.published_from|source.published_from|source" \
  "$(vary '.source.published_from = "hand"')|$(vary '.source.published_from = ["x"]')|$(vary '.source = "hand"')"
check "a headline, rule or ladder of the wrong type is a problem, not an error" "headline|headline_rule|throughput_ladder" \
  "$(vary '.headline = 136000')|$(vary '.headline_rule = true')|$(vary '.throughput_ladder = {}')"
check "a dry-run candidate does not" "source.published_from.dry_run" "$(problems "$T/dry.json")"
check "a headline with no stamp does not" "source.published_from" "$(vary 'del(.source.published_from)')"
check "a refresh from outside a build, or from another build, does not" "source.published_from.publish_build_number|source.published_from.publish_build_number" \
  "$(vary '.source.published_from.publish_build_number = null')|$(vary '.source.published_from.publish_build_number = "635"')"
check "a key that is not this run's object in runs-perf-xl/ does not" "source.published_from.key|source.published_from.key|source.published_from.key" \
  "$(vary '.source.published_from.key = "runs/master/2026-10-03T15-52-14Z__0123456789.json"')|$(vary '.source.published_from.key = "runs-perf-xl/master/other.json"')|$(vary '.source.published_from.key = null')"
check "a run that was not a series member does not" "source.published_from.series_unmet|source.published_from.series_unmet" \
  "$(vary '.source.published_from.series_unmet = ["trial: x"]')|$(vary '.source.published_from.series_unmet = null')"
check "a headline that disagrees with its own ladder or bound does not" "the,a|headline,the|a|headline,headline" \
  "$(vary '.headline.healthy_ceiling_rps = 144000')|$(vary '.headline.healthy_ceiling_p99_ms = 12')|$(vary '.throughput_ladder[-1].degraded = false')|$(vary '.headline.p99_max_ms = "10"')"
check "single-k6 figures moved under .single_k6 with no headline rule do not" "no,no" "$(vary 'del(.headline_rule)')"
check "multi-k6 figures left at the top level with the rule and the single_k6 block deleted do not" "no|no|no" \
  "$(vary 'del(.headline_rule, .single_k6)')|$(vary 'del(.headline_rule, .single_k6, .source.client)')|$(vary 'del(.headline_rule, .single_k6, .source.published_from)')"
check "a single_k6 block that is not the single-k6 run's figures does not" "single_k6|single_k6|single_k6" \
  "$(vary '.single_k6 = null')|$(vary '.single_k6.throughput_ladder = "x"')|$(vary '.single_k6.source.client = "multi_k6"')"
# The publish step refuses to build on such a file, from either source, and to write one.
git -C "$R" checkout -q -b handmade && cp "$T/dry.json" "$COMMITTED" && git -C "$R" -c user.name=t -c user.email=t@t commit -qam "a hand-committed dry-run candidate"
HAND="$(git -C "$R" rev-list --count HEAD)"
put "$T/member.json" "$RW_KEY"
publish hand-rw
check "over a hand-committed headline the multi-k6 source refuses (exit 1, nothing written)" "1|yes|yes" \
  "$(cat "$T/hand-rw.rc")|$(has 'WERE NOT PRODUCED BY THE PUBLISH STEP' "$T/hand-rw.log")|$(untouched "$HAND")"
publish hand-daily PERF_PUBLISH_SOURCE=single_k6 PERF_PUBLISH_PERSISTED_KEY="$DAILY_KEY"
check "  ... and so does the daily source" "1|yes|yes" \
  "$(cat "$T/hand-daily.rc")|$(has 'WERE NOT PRODUCED BY THE PUBLISH STEP' "$T/hand-daily.log")|$(untouched "$HAND")"
cat "$T/legacy.json" "$T/legacy.json" > "$COMMITTED" && git -C "$R" -c user.name=t -c user.email=t@t commit -qam "two documents in the data file"
HAND="$(git -C "$R" rev-list --count HEAD)"
publish twice
check "over a committed file the check cannot read (two documents) it refuses too" "1|yes|yes" \
  "$(cat "$T/twice.rc")|$(has 'the page-data check could not read it' "$T/twice.log")|$(untouched "$HAND")"
jq . <<<"$LEGACY" > "$COMMITTED" && git -C "$R" -c user.name=t -c user.email=t@t commit -qam "back to the single-k6 figures"
HAND="$(git -C "$R" rev-list --count HEAD)"
publish nobuild BUILDKITE_BUILD_NUMBER=
check "outside a build the multi-k6 source writes nothing (exit 1)" "1|yes|yes" \
  "$(cat "$T/nobuild.rc")|$(has 'REFRESH NOT WRITTEN' "$T/nobuild.log")|$(untouched "$HAND")"
publish otherbuild BUILDKITE_BUILD_NUMBER=635
check "  ... nor from a build other than the one that measured the run" "1|yes|yes" \
  "$(cat "$T/otherbuild.rc")|$(has 'REFRESH NOT WRITTEN' "$T/otherbuild.log")|$(untouched "$HAND")"

echo "--- 6. a lower bound, with nothing from the arm committed yet, is published as one"
# Publish branches are named to the second: drop the one "publish real" left.
git -C "$R" for-each-ref --format='%(refname:short)' refs/heads/perf/ | while read -r b; do git -C "$R" branch -q -D "$b"; done
put "$T/v-lb.json" "$RW_KEY"
publish lbreal
check "the patch is emitted (exit 0), the annotation says \"at least\", and no first failure is published" "0|yes|\"none\"|true" \
  "$(cat "$T/lbreal.rc")|$(has 'at least 120000 req/s' "$T/lbreal.log")|$(ff_of "$COMMITTED")|$(jq -r '.headline.lower_bound' "$COMMITTED" 2>/dev/null || echo UNREADABLE)"
check "  ... and the page data it wrote passes the check" "" "$(problems "$COMMITTED")"

echo
if [ "$FAILS" -gt 0 ]; then echo ":x: $FAILS check(s) failed" >&2; exit 1; fi
echo "all perf-xl publish checks passed"
