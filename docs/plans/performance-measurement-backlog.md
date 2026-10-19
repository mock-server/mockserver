# Performance Measurement Backlog

**Outcome.** Most open rows below need one more measurement run — on the `perf`/`perf-xl`
load-rig queue or the scheduled weekly soak, never a load, soak, burn or repeat-in-Docker run
on a developer Mac (see `AGENTS.md`). The exceptions: row 97 needs ~50 clean master builds,
counted from the guard's annotation, not a run; row 44 needs a website/owner update, not a run;
91a/540a/541b need owner approval, and 126b and 204d an owner decision, not a run; H1 needs
harness fixes, not a run. The [Parked](#parked) section holds rows blocked on something outside
this programme's control (an upstream dependency, a next-major-only change), not on a measurement
run. This file is deleted once every row is closed.

For how each run is produced, gated and read, see
[docs/code/performance-measurement.md](../code/performance-measurement.md).

**Last audited:** 2026-10-19.

## Open rows

| # | Item | State | Waits on | Next step |
|---|---|---|---|---|
| 541b | OpenSSL issue: its QUIC client did not re-send a lost `MAX_STREAM_DATA` in a decrypted capture (Docker Desktop), and stalls where ngtcp2, quic-go and Chromium did not | Still warranted after 541's server fix: the fix makes stalls rare but not impossible, and RFC 9000 §13.3 puts recovery on the client. An issue draft exists but is not committed | owner approval | Work through the draft's "check before filing" items (re-capture on Linux at the clamp, `STREAM_DATA_BLOCKED`, loss declaration, a curl-free reproduction, OpenSSL master), then file on openssl/openssl only with the owner's approval |
| 44 | Multi-k6 published headline (p99 ≤ 10 ms bound) | First published at 144k (build 643); the owner moved it to **136k** on 2026-10-12 (`perf_figures.json` from build 667). Twelve qualifying runs now (added 679, 136k; 689, 128k): 136k×6, 144k×3, 128k×3. Over the latest five members, criterion (2) names **128k** to publish, but on 2026-10-18 the owner decided to keep the headline at 136k rather than move it — `_data/perf_figures.json` and the `RW_EXPECTED_CEILING_RPS`/`RW_ADJACENT_CEILING_RPS` constants are unchanged. The website's hand-counted run tally (`ceiling_spread`/`ceiling_spread_detail` in `performance.html`, the two `schema_faq` answers, and the `llms.txt` line) is updated to the twelve-run count. Cpuset reporting is only partly shipped — see item 8 in §4 below. | a further measurement run to re-tally against (the multi-k6 publish step's annotation asks for a re-tally with every new series member) | The owner decision and website tally are both done for the current twelve-run set. Nothing else to do here until the next qualifying `perfxl-rw-multik6` member lands — at that point, re-run the tally (`lib/perf-rw-multik6-series.jq` over the new result, cross-checked against `runs-perf-xl/master/`) and update the same website locations plus `docs/code/performance-measurement.md`'s run table per the publish annotation's standing prompt. Items 3 and 8 in §4 below remain open regardless of the headline rung. |
| 97 | A netty IT fork wedged: `RejectedExecutionException` on a stopped instance's event-loop cascaded into forward-test timeouts/502s across a full `verify` run (found 2026-10-03). Likely area: forward-client or connection-pool state shared across MockServer instances in one JVM (`NettyHttpClient` in mockserver-core). Not yet reproduced in isolation — reproduce by running the failing order, or stopping a MockServer while a delayed or forwarded response is in flight, then forwarding from another instance; find what shared state a stopped instance leaves terminated. | A best-fit mitigation shipped — `SharedEchoServerGuard.answeringOrReplaced(...)` probes the shared `insecureEchoServer`/`secureEchoServer` and replaces (never stops) one that stops answering — but it is **not a confirmed root cause or fix**. A firing is now visible: it prints to `System.err` from a JUnit `@BeforeClass` into a per-class `*-output.txt` under `mockserver/mockserver-netty/target/failsafe-reports/` (failsafe runs with `-DredirectTestOutputToFile=true`), and `java-netty-it-shard.sh` now runs `java-netty-it-echo-guard-report.sh` from an `EXIT` trap, so on every shard, passing or failing, any `*-output.txt` containing `DIAGNOSTIC_HEADER` is printed to the job log, raises a warning annotation with context `shared-echo-guard-<shard>`, and is uploaded under `mockserver/target/failed-tests/shared-echo-guard/<shard>/`. The grep string is read from the constant in the guard's source; `SharedEchoServerGuardTest` keeps that derivation working. | ~50 clean master builds | Count ~50 master `mockserver-java` builds with no `shared-echo-guard-*` annotation on any netty IT shard (a build with a firing resets the count; its uploaded output carries the thread dump to diagnose). If none fire, the root cause above remains unconfirmed but no longer recurs in CI; if one fires, start from its thread dump. |
| 126b | `INFO` event-log consumer still spends most of its time rendering a message that `disableSystemOut` discards | **Row 126a closed 2026-10-19.** On the perf rig the regex-free indent (`113c46cdf`) moved the first `ring_full` drops from the 8,000 to the 10,000–12,000 req/s rung and cut consumer CPU at 6,000 req/s from 0.78–0.79 to 0.62 (after: builds 700, 703; before, on build 694's image: 701, 704). Regex frames fell from 34.6–36.3% of consumer CPU samples to 0%. The rest of the cost is unchanged: Jackson pretty-printing is now 52–54% of consumer samples, and the indent loop 14.8%. Figures are in [memory-management.md](../code/memory-management.md#ring-in-flight-bounding-and-drops) | an owner decision | Decide whether to skip rendering when `disableSystemOut` leaves no handler to receive it, and whether to stream the Jackson serialisation; re-measure either change with `PERF_INFO_INVESTIGATION=jfr` as row 126a did |
| 136 | Recalibrate the weekly soak's p95/p99 gates and derive a drift bound | Gates stay at p95 150 ms / p99 200 ms until 8 counting runs exist. Count so far: 1 of 8 (build 656, 2026-10-11; build 637 predates the provenance fields and doesn't count). The counting rule is decided and documented: [performance-measurement.md § Absolute gates](../code/performance-measurement.md#absolute-gates) — a run counts with `revision_check: match`, **or** `differs` with `config.image_revision_ancestor_of_harness` true (server image unchanged, only the harness newer), and default `config.requested` values. | 8 counting scheduled weekly soaks (next: each Sunday 08:00 UTC) | Read each Sunday soak under the linked rule. For each **counting** run, also read its first three 5-minute match windows and its GC log, watching for a build-637-style early spike. (637 recorded no 5-minute windows, only an early-window (120–420 s) match p99 of 23.048 ms against 0.596 ms late (whole run 3.115 ms); its cause is unresolved. Build 656 did record per-window figures and did not reproduce anything like it: its first three windows read p99 0.900, 0.638 and 0.586 ms, settling to 0.568–0.590 ms.) |
| 204d | `INFO` retention divisor (`/12`): nobody has decided whether young-GC promotion should set it, as it set `WARN`'s `/20` | **Row 204c closed 2026-10-19; `/7` stays at every heap size.** The `INFO` in-flight floor at `/7` (`d094f1049`) read 150,394,880 B at 1 GiB, the ceiling ÷ 7. Builds 699 (1 GiB) and 702 (256 MiB) ran on image `113c46cdf`. All 24 cells were measured, with no OOM. Under 60 s of overload the whole log peaked at 14.0–18.3% of the ceiling, below the 19.2% expected; the composite is 1.71. Drops as a fraction of entries offered fell in all 12 `INFO` cells, during both fill and overload. Two 256 MiB gen-ZGC fills dropped more entries in raw count, but more requests arrived in the same window, and identical-config repeats vary by up to about 50%. Figures are in [memory-management.md](../code/memory-management.md#confirmed-on-the-perf-rig-at-7-2026-10-19). No rig step measures promotion: `PERF_INFO_INVESTIGATION` has only `jfr` and `heap`, and `WARN`'s `/20` came from a 2-core container dose-response | an owner decision, then a harness extension | Decide whether `INFO` retention is worth measuring. memory-management.md argues that rendering makes promotion structural at `INFO`, but that predates 9.0.0 dropping the retained message. If it is worth measuring, add a `promotion` part to `perf-test-info-investigation.sh`: an `INFO` SUT with `-Xlog:gc*`, promoted MB/s per `sweep.js` rung, with retention at `/12` and at `/20` set through `maxEventLogSizeInBytes`. Then run it on the `perf` queue with `bk build create … -e PERF_INFO_INVESTIGATION=promotion` |
| 91a | Netty PR: make `HttpObjectDecoder`'s chunk-size line limit count the line's own bytes, not what is buffered | 9.0.0 has shipped (2026-10-15), so this is now actionable | owner approval | Build and test against Netty's `4.2` branch locally, show the owner the diff and PR text, open only with their approval (they sign Netty's CLA). MockServer keeps its own accepted 8 KiB bound (`HttpChunkLineLimiter`, see [netty-pipeline.md § Chunk-size line limit](../code/netty-pipeline.md#chunk-size-line-limit)) until a Netty release includes the fix. |
| 540a | Netty issue: let an application add a handler to the HTTP/3 control and QPACK streams Netty opens locally | 9.0.0 has shipped, so this is now actionable; the archived draft (commit `2ae4a263e`) says Netty `4.2.18.Final` — the current pin is `4.2.19.Final`, so the draft needs its version line updated before filing | owner approval | File the draft issue (kept in this repo's history at `2ae4a263e`, version line updated first) on netty/netty only with the owner's approval. |
| H1 | Harness hazards found while fixing the F1/F2 lint flakes (2026-10-19). None has failed a perf run, and each would make one fail or misreport silently | (a) `perf-test-run.sh` sets `trap cleanup EXIT` and `trap '… cleanup' INT TERM` (lines 754, 756) before it backgrounds its samplers (`&` at lines 1394, 2212, 2268, 2379, 2931, 3144). bash 5.2 runs a parent's trap in a subshell that is signalled just after it forks, so a sampler stopped at that moment runs `cleanup()` itself and removes the SUT containers mid-run. This is the mechanism that deleted `$WORK` in F1 (`.buildkite/scripts/test/perf-info-budget-test.sh` now guards its own trap). It is not reached in practice, because samplers run for minutes before they are stopped. (b) `perf-website-publish.sh` copies `final.json` over the data file (line 592) before `checkout -b` (line 647) and `commit` (line 659). On GIT BRANCH FAILED or GIT COMMIT FAILED the work tree is left modified, while `fail()` says "The committed figures were left unchanged" (line 197). F2 reached this path through a branch-name collision that a CI checkout does not have. (c) `stop_info_els_sampler` (lines 1396–1401) kills only the sampler subshell, so an in-flight scrape's `awk` can still append one pressure row after the stop. The F1 fix gives each `one_row` call its own csv rather than asserting on this. (d) On macOS bash 3.2, a failed `.` under `set -e` exits 0 when any `EXIT` trap is set (`set -e; trap true EXIT; . /nonexistent` gives rc 0; bash 5.2 gives 1). So `perf-info-budget-test.sh` line 63 passes silently when `lib/perf-cpu-topology.sh` is missing. This affects local runs only; the Linux lint agent is not affected | harness fixes | (a) Guard both traps with a main-shell check (`[ "${BASHPID:-$(exec sh -c 'echo "$PPID"')}" = "$$" ]`, as in `perf-info-budget-test.sh`), or reset them as each sampler starts. (b) Restore the data file (`git checkout -- "$DATA_FILE"`) on the branch and commit failure paths, or copy the data file after the branch is created. (c) Either wait for the in-flight scrape on stop, or document that the pressure csv may hold one row after the stop. (d) Guard each `.` in the perf tests with `[ -r … ] || { bad …; exit 1; }` |

## §4 — Hardware scale and JVM profiling

The multi-k6 rw headline (row 44) and its publish/CI machinery are the only live item in this
section; its current state is in the Open rows table above. Two items remain open:

3. A standing watch, from builds 611/612: the quiet-second figure depends on the GC regime and
   the distance from a cycle (611, 96k–144k: 1.25% over 5 ms in quiet seconds straight after a
   masked one against 0.12% at 4 s or more from a cycle; 612 about 0.9% in such seconds), so watch
   the ceiling moving with how many seconds are masked: 643's ceiling rung counted 7 of 11 seconds.
8. In an arm-only run `config.sources.cpusets` still reads `declared`, although since `98fb756da`
   its `cpusets.k6` (and `physical_cores.k6`) come from the arm: observed by Docker for the
   multi-k6 arm, configured for the matrix. Give the k6 entry a source of its own (for example
   `cpusets_k6`: `observed` or `declared`) in `perf-test-run.sh`, or say in the config block that
   `cpusets` covers the server and upstream only in an arm-only run.

**Watch (item 4a):** the 10 Hz receive-queue sampler's margins are thin — `cpu_frac` about 8%
under the 0.3 bound, `read_ms` p99 about 20% under 50 ms — and the cost is the kernel generating
`/proc/<pid>/net/tcp6` (about 30 of the 37 ms read), not the python parse; a cheaper read source
(for example a netlink sidecar) would widen both margins rather than a faster parser.

**Cross-check cap (closed row 118a, kept for future runs):** the multi-k6 `cross_check.cross_run`
cap sits at 40k req/s (raised from 24k). If a later `perf-xl` member reports `agrees: false` on a
judged rung, re-read that rung's single-process p99.9 and GC stalls before lowering the cap back
to 36k or 32k.

**Published headline mechanics:** `lib/perf-rw-multik6-series.jq` states the series; the arm's
compare step persists a member to `runs-perf-xl/<branch>/`; `perf-website-publish.sh` with
`PERF_PUBLISH_SOURCE=rw_multik6` publishes only a member whose headline agrees with its own
rungs; `lib/perf-website-figures-check.jq` refuses a committed headline without that stamp;
`website-perf-headline-lint.sh` fails the website pipeline's validate step if the page
description, a rate-stating FAQ answer or `llms.txt` disagrees with `perf_figures.json`.

**The switch rule** (`PERF_RW_HEADLINE_RULE`, default `gc_masked_p99`): 5 consecutive `perf-xl`
rig runs (same ladder/placement, same k6 `GOGC`, shipped event-log budget) must each have:
(1) a valid, non-`lower_bound` headline;
(2) at least 4 of the 5 p99-bounded `headline.healthy_ceiling_rps` values fall on one ladder rung
or on two adjacent rungs, all 5 are on those rungs or one adjacent to them, and the ceiling to
publish is the lower of the two rungs (reworded on 630–634; it first read "the same ladder rung
in at least 4 of the 5, and all 5 are that rung or an adjacent one"). The ladder's steps are not
uniform (4k from 32k to 48k, 8k to 80k, 16k to 128k; on a multi-socket host such as `perf-xl`, 8k
from 96k to 160k), so the tolerance is one rung of the ladder, not a fixed rps;
(3) the first rung above the ceiling that fails the p99 bound observed in GC-masked seconds.
Full method, evidence and the streak-counting rules (which runs count, which are A/B trials) are
in [performance-measurement.md](../code/performance-measurement.md#the-headline-rule-item-44).

---

## Parked

Rows blocked on something outside this programme's control (an upstream dependency, a
next-major-only change), not on a measurement run.

| # | Item | Blocked on |
|---|---|---|
| 220 | `mockserver-node/test/packageContents.js`'s npm package-contents check still does not compare: members reached through `extends`/index/call/construct signatures, **interface members separated by commas**, comma-separated declarators (`const a: A, b: B`), non-interface object types, interfaces no test builds an object for, re-exported-after-import names, **whether a declared value is a function, a constructor or an object** (the kind of value a name holds), or **`export default` of a namespace name** (reported as untraceable). **Low priority — take up only if a typings defect gets past it.** | TypeScript 7's programmatic parser API becoming stable (main entry currently exports only its version; the parser lives under `typescript/unstable/*`; the launcher has no TypeScript at all). Until then, add new builder interfaces by hand to the client's `builtObjects` list. |

### Waiting on Netty (2026-10-08)

| id | title | next step |
|---|---|---|
| 540 | HTTP/3: the streams MockServer's side opens (control, QPACK) have no MockServer handler, and Netty (now pinned at `4.2.19.Final`) offers no hook to add one. The one known exception path (`QPACK_ENCODER_STREAM_ERROR` from an oversized `SETTINGS_QPACK_BLOCKED_STREAMS`) is already prevented, but server-side, not on those locally-opened streams: the `Http3ExceptionHandler` fix (`2ae4a263e`) is MockServer's own handler, placed on the peer's **inbound** control stream (the stream that carries the client's SETTINGS into MockServer, the HTTP/3 server), ahead of Netty's own control-stream handler, and it lowers an over-int `SETTINGS_QPACK_BLOCKED_STREAMS` to `Integer.MAX_VALUE` before Netty sees it (`Http3QpackBlockedStreamsIntegrationTest`). That does nothing for the locally-opened streams this row is about; no other exception is known to reach those. A general hook still needs forking Netty: `Http3ServerConnectionHandler` is still final, and the stream initializers are package-private. The three routes that avoid a fork — a split package, reflection, or wrapping the event loop — were all considered and rejected. | Only if a new exception path appears on a local stream, or the owner files the upstream issue (row 540a). |

### Only if users ask (owner, 2026-10-08)

| id | title | next step |
|---|---|---|
| 284 | Binary relay has no idle/time bound on a relayed connection (item 186, slice 1a's remaining open point; everything else in that item shipped). Not bounded by default because a database session may sit idle. | Add an opt-in `binaryRelayIdleTimeout` (default off) that closes both legs after that long with nothing read or written either way — only if users ask for it. |
