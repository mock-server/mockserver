# Test Coverage Gaps

**Outcome.** The July 2026 coverage audit (95 gaps: 93 numbered gaps plus follow-ups F1/F2) was
re-checked against master on 2026-10-18: 83 closed, 1 no longer applies, 11 still open. Since
then gaps 47, 54, 66, 68, 83 and 90 have closed. The only gap left is T1 (gaps 19–21), now wired
into CI and waiting for a green `master` run to confirm it. Gaps 1 and 82 were declined (see
below). Delete this file once T1 is confirmed on `master`.

## What remains

| # | Gap | Why it matters | Blocked on | Next step |
|---|---|---|---|---|
| T1 | The transparent-proxy end-to-end tests (`TproxyEndToEndIntegrationTest`, `SoOriginalDstEndToEndIntegrationTest`, `EbpfOriginalDestinationEndToEndIntegrationTest`, #19–21) ran on no CI build: no pipeline set `RUN_TRANSPARENT_PROXY_E2E`, the maven image has no Docker CLI, and the `default` queue's userns-remapped dockerd rejects `--privileged`. The `:shield: transparent-proxy e2e tests` step now runs all three on the existing `default` queue, with no new queue or Terraform change. `pipeline-java.yml` sets `RUN_TRANSPARENT_PROXY_E2E=true`. `java-transparent-proxy-test.sh` mounts a docker CLI copied from a digest-pinned `docker:<ver>-cli` image, mounts the checkout at its host path so the sibling container can bind-mount the jar, and fails closed through `assert-suite-ran.sh`. The suites start their containers with `--userns=host`, so `--privileged` is accepted. Locally, through that script, all three passed against Docker Desktop and against a userns-remapped docker-in-docker daemon. With `--userns=host` removed from the eBPF suite, the step failed closed. The eBPF suite proves only that a pinned BPF map can be created under `--privileged` and that enabling the eBPF resolver does not break the chain. Its map entry (cookie 1) never matches a real socket, so the request is resolved by the SO_ORIGINAL_DST/conntrack fallback, and a broken eBPF lookup or decode still passes. | `IP_TRANSPARENT` and `SO_ORIGINAL_DST` interception, and the eBPF lookup/decode path, can regress while every build stays green. | Landing on `master`. A pre-merge `mockserver-java` build on a real `default` agent (build 3277, hidden ref, dockerd 25.0.16) ran all three with `1 tests ran (0 skipped)` each. | Once it is green on `master`, narrow this row to the remaining eBPF gap: make the eBPF suite assert that the eBPF resolver itself answered, for example by disabling the SO_ORIGINAL_DST/conntrack fallbacks in its container or asserting a resolver log line, on a real agent kernel. If only eBPF fails on the agents, split the `-Dit.test` list and the `assert-suite-ran` globs per suite and run SO_ORIGINAL_DST/TPROXY first. |

### Declined

- **#1 — degrade-and-confirm-red / mutation gate.** No pitest/mutation plugin and no scripted
  degrade list exist; negative controls remain ad hoc inside individual tests. **Already declined
  once**, as the same proposal, in commit `adf935e66` (2026-08-17, then Gap #5 of the file at this
  path — `9a1497928` on 2026-09-26 only deleted that file, it did not make this decision):
  "do not do this because of [a HIGH] label... a pitest gate is a large investment against
  a much-reduced risk." Nothing since has changed that calculus.
- **#82 — standing sweep for the `CONFIGURES_AROUND_DEFAULT` / `TOLERANCE_COVERS_BUG` /
  `WRONG_MODULE` axis** (the three "tell" categories the original audit named, per `adf935e66`
  Gap #4). No Rule 8 (or equivalent) exists in `check-false-green-guards.sh` for
  `not(is(4xx))` / `anyOf(is(2xx)...)`-style assertions. **Already declined once**, in the same
  commit `adf935e66` (then Gap #4): the named instances were all fixed, so the residual value is
  preventing recurrence — bookkeeping unless this audit style continues; worth a standing CI grep
  only in that case. New information for the owner to weigh against that: the October 2026
  recheck found **7 status-tolerance assertions still present at the time of the recheck** —
  6 in three dashboard tests in `HttpRequestHandlerTest.java`, and 1 in
  `DashboardWebSocketHandlerTest.java:2306` — the exact shape the declined sweep would have
  caught, found again by hand. That recurrence does not by itself reopen the decline; it is the
  fact the owner needs to weigh if this audit style continues.

## Known limits of closed gaps

These gaps count as closed, but their coverage has limits worth knowing. Method note: CI
execution for the closed CI-truth gaps was inferred from pom/pipeline wiring, not from reading
build logs.

- The UI Playwright e2e suite runs only when `mockserver-ui/` changes, so a server-only change that
  breaks the dashboard does not trigger it.
- Cloud persist-and-reload is tested end to end for S3 only. GCS and Azure have factory-wiring
  tests only.
- The h2c suite is bespoke, not a subclass of the abstract mocking suite, so the matcher matrix is
  not re-run over h2c.
- HTTP/3 covers the streaming forward relay, but not a mocked `httpSseResponse` over QUIC.
- The AsyncAPI live-broker control-plane test covers Kafka. The MQTT load path is not driven.
- The IPv6 SAN case is covered, but the netmask/CIDR branch is not.
- **#47** — The Go, .NET and Rust live tests do not drive a forward **class** callback: the server
  image ships no `ExpectationForwardCallback` class to name. The Rust client also has no closure API
  for a forward object callback, so only Go and .NET drive that one.
- **#47** — A Go `DnsRecord` cannot set TTL 0: `TTL` is an `int` with `omitempty`, so 0 is served as
  the server default of 300. Fixing it means making the field `*int`, a breaking change.
- **#6** — Embedded-secret masking is asserted at the configuration serializer, not via an HTTP GET.
- **#5** — Runtime mTLS enforcement is toggled via the live `Configuration` object, not via HTTP PUT (the enforcement itself is still asserted over the wire).
- **#73** — Cassette auto-registration is proven in two halves (the MCP tool registers; a separate test shows `GET` lists it) — no single test spans both.
- **#62** — `ConnectionLifecycleChaos` relies on Failsafe shard configuration to run; no CI log was read to confirm it actually executes.
