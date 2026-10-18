# Test Coverage Gaps

**Outcome.** The July 2026 coverage audit (95 gaps: 93 numbered gaps plus follow-ups F1/F2) was
re-checked against master on 2026-10-18: 83 closed, 1 no longer applies, 11 still open. Of the 11: T1 (#19–21) needs a capable CI queue;
#1 and #82 are declined (see below); six (#47, #54, #66, #68, #83, #90) are being implemented now
(2026-10-18) and are tracked by their own commits, not here. Delete this file once T1 is closed
and the in-progress six have landed.

## What remains

| # | Gap | Why it matters | Blocked on | Next step |
|---|---|---|---|---|
| T1 | The transparent-proxy end-to-end tests (`TproxyEndToEndIntegrationTest`, `SoOriginalDstEndToEndIntegrationTest`, `EbpfOriginalDestinationEndToEndIntegrationTest`, #19–21) are collected but run on no CI build. Their step is opt-in through `RUN_TRANSPARENT_PROXY_E2E`, which no pipeline sets, so the step exits 0 having run nothing. The step also runs inside the `mockserver/mockserver:maven` image, which has no Docker CLI, so it cannot start the privileged container these tests need as things stand. | `IP_TRANSPARENT`, `SO_ORIGINAL_DST` and eBPF interception can regress while every build stays green. | An agent with the Docker CLI and socket and `NET_ADMIN`, with no user-namespace remap. eBPF also needs `--privileged`. | Put the Docker CLI in `docker_build/maven/Dockerfile` (or run this step on a host-CLI image instead), add a capable queue in `terraform/buildkite-agents/`, then set `RUN_TRANSPARENT_PROXY_E2E=true` in `.buildkite/pipeline-java.yml` so the existing `assert-suite-ran` guard fails closed. Enabling `SO_ORIGINAL_DST` alone (it needs only `NET_ADMIN`, so it can go ahead of eBPF) means splitting the `-Dit.test` list and the `assert-suite-ran` globs in `java-transparent-proxy-test.sh` per suite, so it runs without also enabling the `--privileged` eBPF case. |

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

### In progress (2026-10-18)

- **#47** — Response-action wire behaviour for the remaining Go/.NET/Rust action types (callback, sse, websocket, dns, grpc, llm, and Rust binary). Next: a per-client live test that reads the stream/response for each and asserts its real shape, not just that it was accepted.
- **#54** — `driftAlertWebhookEnabled` / `driftSemanticAnalysisEnabled` gating tests. Next: start a MockServer with each flag true/false and assert the webhook POST / semantic-extension registration only happens when enabled.
- **#66** — C# composer-codegen compile check (currently a frozen snapshot, not re-emitted per build). Next: emit the shared parity-case matrix through `csharp.ts` into a scratch project and `dotnet build` it in CI, as the other languages already do.
- **#68** — Fail-closed container-start assertion for the go/dotnet testcontainers CI steps. Next: print a status marker once the container answers, and grep for it in the step script.
- **#83** — `HttpRequestHandlerTest`'s dashboard-serving assertions and `DashboardRealBundleServedIntegrationTest`'s skip-on-404 path. Next: assert `200` (not just not-401/not-403) and fail instead of skip when CI is set.
- **#90** — An over-the-socket (not in-memory) integration test for GraphQL/JWT/WASM/numeric matchers. Next: a netty IT that registers each matcher via `MockServerClient` and sends matching/non-matching requests over a real socket.

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
- **#6** — Embedded-secret masking is asserted at the configuration serializer, not via an HTTP GET.
- **#5** — Runtime mTLS enforcement is toggled via the live `Configuration` object, not via HTTP PUT (the enforcement itself is still asserted over the wire).
- **#73** — Cassette auto-registration is proven in two halves (the MCP tool registers; a separate test shows `GET` lists it) — no single test spans both.
- **#62** — `ConnectionLifecycleChaos` relies on Failsafe shard configuration to run; no CI log was read to confirm it actually executes.
