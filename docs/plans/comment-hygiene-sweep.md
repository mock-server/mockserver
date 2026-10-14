# Comment Hygiene Sweep

## Outcome

Source files across the repo carry investigation narrative in comments — CI build
numbers, measured throughput figures, accounts of what was tried and reverted. It
went in one commit at a time and is now dense enough to hurt readability, and it
goes stale silently because nothing fails when the code moves on.

`.opencode/rules/code-comment-discipline.md` stops new narrative arriving.
**This plan covers the historical backlog**, which the rule does not touch.

Not started (checked 2026-10-11: no sweep commit has landed since the rule did in
`052c74b53`). No deadline — this is hygiene, not a defect.

Swept 2026-10-13: assessed the netty connection/pipeline cluster below (InFlightRequest,
CompositeOriginalDestinationResolver, ConnectionScopeHandler, BreakpointMatcherRegistry) — all
fine as written, no narrative to remove. Also swept three files below the ≥58% cutoff that had
real narrative: `mockserver-testcontainers/.../MockServerContainer.java` (readiness-wait comment
carried measured timing figures), `mockserver-core/.../model/KeysToMultiValues.java` and
`mockserver-core/.../mock/CandidateIndex.java` (both carried a historical-calibration/ticket
reference alongside the invariant); all three compressed to the invariant/constraint, proven
comment-only via identical `javap -c` before/after.

Swept 2026-10-14: `mockserver-benchmark` (40 Java and shell files). Removed programme/ticket labels,
"BASELINE ONLY" boilerplate, stale line references and past-run figures; kept the JMH correctness
notes and "don't do the obvious thing" warnings, compressing blocks over six lines. Rewrote comments
that described code that no longer exists (MetricsIncrementBenchmark claimed a lock-free lookup that
was dropped; MatchingBenchmark said the "because" string is built at WARN). Rig figures moved to
`docs/code/performance-measurement.md` (ConnectionCeilingBenchmark section). Proven comment-only via
identical `javap -c -p` for all 298 classes.

## What "too much" means here

The rule's test: a comment earns its place only if a reader **of that code** would
otherwise get it wrong — a non-obvious invariant, an external constraint, a
"don't do the obvious thing, because X", or Javadoc on public API.

The material being removed is not worthless; it moves:

| Material | Moves to |
|---|---|
| Why the change, what was verified | already in the commit message — just delete |
| Measured figures, experiments, inconclusive results | `docs/plans/performance-measurement-backlog.md` or `docs/code/` |
| Behaviour of a property or API | Javadoc + `jekyll-www.mock-server.com/` |

## Scope, worst first

Ranked by comment share of file (files ≥100 lines; regenerated 2026-10-11). **Density alone is not the
signal** — an SPI interface that is mostly Javadoc is correct as it stands. Each
file needs the rule's test applied, not a threshold.

| File | Comment share | Likely verdict |
|---|---|---|
| `.buildkite/scripts/steps/java-cloud-store-test.sh` | 74% | sweep — CI scripts attract narrative |
| `mockserver-testing/.../test/DockerAvailability.java` | 73% | partly justified — the probe's hazard is genuinely non-obvious |
| `.buildkite/scripts/steps/java-async-broker-test.sh` | 70% | sweep |
| `mockserver-core/.../state/StateBackend.java` | 68% | likely fine — SPI Javadoc |
| `mockserver-core/.../state/KeyValueStore.java` | 64% | likely fine — SPI Javadoc |
| `mockserver-performance-test/k6/lib/config.js` | 63% | sweep — carries the most run narrative |
| `mockserver-netty/.../InFlightRequest.java` | 61% | fine — invariant/design Javadoc, no narrative |
| `mockserver-core/.../socket/tls/KeyAndCertificateFactory.java` | 60% | swept 2026-10-13 — rest of the Javadoc was already rule-compliant (invariants, constraints); only ticket numbers and roadmap narrative removed |
| `mockserver-netty/.../proxy/CompositeOriginalDestinationResolver.java` | 59% | fine — strategy-chain Javadoc, no narrative |
| `mockserver-netty/.../unification/ConnectionScopeHandler.java` | 58% | fine — invariant/design Javadoc, no narrative |
| `mockserver-core/.../breakpoint/BreakpointMatcherRegistry.java` | 58% | fine — API Javadoc, no narrative |
| `.buildkite/scripts/steps/perf-test-microbench.sh` | 57% | sweep |
| `.buildkite/scripts/steps/perf-test-guard.sh` | 47% | sweep — fell from 58% as code grew, not because it was swept |

Regenerate the ranking before starting — it will have moved:

```bash
for f in $(git ls-files '*.java' '*.sh' '*.js' | grep -v node_modules); do
  tot=$(wc -l < "$f"); [ "$tot" -lt 100 ] && continue
  c=$(grep -cE '^[[:space:]]*(//|#|\*|/\*)' "$f" || true)
  [ "${c:-0}" -gt 0 ] && echo "$((100*c/tot))% $c/$tot $f"
done | sort -rn | head -40
```

## Approach

- **Comments only.** No behaviour change in the same commit, so the diff is
  reviewable by reading it.
- One commit per area (CI scripts / k6 / netty / core), not one big sweep.
- Where narrative is worth keeping, move it in the same commit — do not delete
  a measurement that exists nowhere else.
- `.buildkite/**` is control-class: gated approval, not autonomous commit.

## Risk

Low, with one real trap: a comment that looks like narrative may be the only
record of a **deliberate** choice, and deleting it invites someone to undo that
choice later. When in doubt, compress to the constraint and the warning rather
than removing the block — the `SO_BACKLOG` example in the rule shows the shape.

## Done when

The ranking above holds no file whose comments fail the rule's test, and
`docs/plans/comment-hygiene-sweep.md` is deleted in the commit that finishes it.
