# Usability and Diagnostics Plan

## Outcome

A user should be able to ask "why did this request, forward, verification, template or spec
fail, and what do I change?" and get the answer in one step from the dashboard, REST, an MCP
tool or the CLI, without restarting at DEBUG. The per-request hot path must not get more
expensive; the first item makes it cheaper. Explanations are **recomputed on demand** from the
stored request plus the current expectations, not recorded on every request. Nothing is sent to
an external LLM unless the user configures one, and MockServer stays self-hosted.

## Where each idea's cost lands

```mermaid
flowchart LR
    R["Request arrives"] --> H["(i) hot path\nevery request"]
    H --> M{"matched?"}
    M -- no --> X["(ii) miss / error path\n404, 502, template error"]
    U["User or tool asks\n(dashboard, REST, MCP, CLI)"] --> D["(iii) on demand\nrecompute from stored request"]
    S["Startup, file load,\ncontrol-plane PUT"] --> O["(iv) offline / static"]
```

| Category | When it runs | Rule for this plan |
|---|---|---|
| (i) hot path | every request | nothing new here; changes must be allocation-neutral or better |
| (ii) miss / error | only on a 404, forward failure, template error | bounded work, no blocking I/O, never an LLM call |
| (iii) on demand | only when a user or tool asks | preferred; hard evaluation budgets, report truncation |
| (iv) offline / static | startup, spec load, expectation PUT, CLI | preferred; advisory, never rejecting |

## What already exists (do not rebuild)

- **Match diagnostics:** `detailedMatchFailures` (default true) builds a `MatchDifference` per
  non-matching candidate; `PUT /mockserver/debugMismatch` (per-expectation diffs for one
  request); `PUT /mockserver/explainUnmatched` re-runs recent unmatched requests against the
  current matchers, ranks the closest expectations and adds remediation hints, within a budget
  (50 expectations, 500 evaluations) — already category (iii).
- **Explain one request (A3):** `PUT /mockserver/explain?correlationId=` re-checks a recorded
  request against the current expectations in serving order; names the winner, a reason for each
  expectation ahead of it, the ones it shadows, the outcome at arrival and `expectationsChangedSince`.
- **404 hints:** `findClosestMatchHint` feeds the `x-mockserver-closest-match(-hint)` headers
  (`closestMatchHintEnabled`, default true), the optional mismatch diagnostic body, and a DEBUG
  log.
- **Verification:** failures include the closest-request diff (`detailedVerificationFailures`,
  default true).
- **Other failures:** forward failures return a 502 with the raw error; OpenAPI load returns the
  parser messages grouped by kind with JSON-pointer locations, and logs the problems of a spec that
  still loads; unknown `mockserver.*` / `MOCKSERVER_*` keys log a WARN at startup.
- **Dashboard:** Explain Unmatched, Debug Mismatch, Matcher Playground, create-expectation-from-
  unmatched, Verification, Drift, Diff Requests and Baseline Compare views, plus log-pressure and
  dropped-event banners.
- **MCP:** a large tool set, including `explain_unmatched_requests`, `debug_request_mismatch`,
  `retrieve_logs`, `verify_*` and `create_expectation(s_from_recorded_traffic)`; prompts
  `debug_unmatched_request` and `create_mock_from_description` (so natural-language → expectation
  already exists, with the user's own assistant doing the reasoning).
- **Bring-your-own LLM:** `PUT /generateExpectation` (`llmProvider` / `llmBaseUrl` / `llmModel`)
  with a template fallback when no backend is configured; `SemanticDriftExtension` optionally uses
  an LLM for drift.
- **Consumer docs:** `debugging_issues.html`, `troubleshooting_matching.html`, `DEBUGGING.md`.

- **Forward-failure causes (part of A4):** a failed forward's 502 now names a TLS failure, an
  HTTP/2 error, a client-side set-up failure or an undecodable upstream response
  (`upstreamFailureReason`, `7a8b8c424`, extended to binary and wss in `d089350d4`).
- **Dashboard → `/generateExpectation`:** yes, the Generate Stub dialog calls it
  (`mockserver-ui/src/lib/generateStub.ts`).

**Gaps:** expectation lint and
shadowing detection; config did-you-mean; the remaining forward-failure causes; a template dry
run.

## Items

| # | Problem → proposal | Cost | Performance reasoning | Effort | Risks | Priority |
|---|---|---|---|---|---|---|
| A2 | A 404 flood rescans every expectation per miss for the closest-match hint → cap the scan and memoise per (method, path) until expectations change | (ii) | 404-heavy and proxy-miss loads currently pay O(expectations) per miss | S | A capped hint can miss the true closest match; say so in the header | P1 |
| B1 | MCP diagnosis tools as the primary AI route: `explain_request` (A3), `explain_forward_failure` (A4), `lint_expectations` (A8), `test_template` (A5), `diagnose_config` (A7), `suggest_expectation_fix` (B3); `explain_request` can delegate to `HttpState.explain(HttpRequest)`, whose output is already redacted | (iii) | MockServer runs deterministic computation only; the user's assistant does the reasoning | M | Tool output carries user traffic to the assistant: apply the log redaction settings to tool results by default | P0 |
| A4 | Partly done: TLS, HTTP/2, set-up and decode failures are named in the 502 (`7a8b8c424`). A plain connect failure still says only "failed to connect to remote socket" → split it into DNS, refused and timeout, add pooled-connection close and proxy loop, and add a suggested fix to each named cause | (ii) | Runs only on failure; a lookup | S | Must not reveal more of the target than the 502 already does | P1 |
| A5 | Template errors are hard to locate → `PUT /template/test` dry run, plus line/column and missing-variable hints in the failure event | (iii) / (ii) | Dry run on demand; enrichment only on failure | M | Reuse the hardened template-engine sandbox; rate-limit | P1 |
| A7 | Config typos only get a WARN → did-you-mean suggestions against known keys, plus an "effective config vs defaults" view | (iv) | Once at startup | S | Redact secrets in the effective-config view | P1 |
| A8 | Unreachable or suspicious expectations go unnoticed → lint: shadowed expectations, regex paths that look literal, missing leading slash, STRICT array bodies, `times=0`; surfaced as PUT `warnings`, in the UI, and via `mockserver lint file.json` | (iv) / (iii) | Control plane only; the pairwise shadowing check is O(n²), so on demand, not on every PUT | M | Advisory only, never rejecting | P1 |
| B3 | "Fix my expectation" → turn the diff into a minimal JSON patch with a preview-and-apply action in Explain Unmatched | (iii) | On demand | M | Never auto-apply | P1 |
| B2 | More MCP prompts: `why_did_verify_fail`, `fix_my_expectation`, `why_forward_failed`, chaining the tools above | (iii) | Static text | S | Keep deterministic facts separate from model prose | P1 |
| A9 | Verification failures could say more → "expected 2, got 1; closest 3 requests; 1 arrived after verify started" | (iii) | Verify path only | S | Add fields; do not rewrite the existing message | P2 |
| B6 | Natural-language → expectation without an LLM inside MockServer → document and test the `create_mock_from_description` prompt, with precise schema errors the assistant can act on | (iii) | None | S | Validate against the real schema | P2 |
| C1 | From the prompt-redaction audit (`f5e1e6ce0`): a drift value in a non-credential field gets JWT and URL-userinfo masking but not the `key=value` credential sweep (`LlmPromptRedactor.maskDriftValue`) → apply `maskKeyValue` there too | (iii) | Only when an LLM drift prompt is built | S | None | P2 |
| B5 | Optional plain-language summary of explain results from a configured LLM (`?narrate=true`) | (iii) | On demand, asynchronous, timeout-bounded, never inline in a 404 | S | Off by default; label as AI-generated; always return the deterministic facts too | P3 |
| A3a | Explain is REST-only → an "Explain" action on a dashboard log group and typed `explain(correlationId)` methods in the clients; also complete the explain endpoint's OpenAPI 200 schema (`request`, `note`, `timestamp`, `expectationsLastChanged`, `totalExpectations`, `evaluatedExpectations`, `maxExpectationsEvaluated`) and add a guard test tying the explainer's gate order to the serving path, plus a `not`-matcher case | (iii) | Zero hot-path cost: calls the existing endpoint | M | The gate-order copy can drift from serving without the guard | P2 |
| A6b | Surface OpenAPI load problems outside the log | (iv) | Load time only | S | Return the advisory problems of a spec that loads in the PUT /mockserver/openapi 201 response (needs HttpState, which was off-limits for A6) and list them in the dashboard and an MCP tool | P2 |
| A6c | A JSON Schema response body logs "loaded OpenAPI spec with problems" although the user loaded no spec (`JsonSchemaResponseSynthesizer` wraps the schema in an OpenAPI document; `$schema`, `const` or a type list trigger it, once per schema every 30 minutes) | (iv) | Response path, cached | S | Suppress or reword the WARN for synthesizer-wrapped schemas | P3 |

## Approach to LLM features

1. **MCP first.** MockServer exposes deterministic tools; the user's own assistant reasons over
   them. No runtime cost, no new dependency, the model stays the user's choice.
2. **Deterministic explanations are the base** (A3, A8, B3): reproducible, testable, offline,
   and consumed by both MCP and a bring-your-own LLM.
3. **Bring-your-own LLM stays optional and off by default** (B5), reusing the existing `llm*`
   configuration and no bundled SDK; redaction fails closed.
4. **No hosted LLM, no bundled model, no SaaS.**

## Sequence

1. **Wave 0 — hygiene and performance:** A2, C1.
2. **Wave 1 — explain engine:** B1 (the A3 explainer plus existing tools with redacted output). (A3 and A10 landed.)
3. **Wave 2 — failure classes:** A4, A5, A6, A7, each with an MCP tool and a dashboard entry.
4. **Wave 3 — fixes:** A8 (including the CLI), B3, B2, A9.
5. **Wave 4 — optional:** B5, B6.

## Performance rules for every item

- Nothing new in category (i). Any change touching the hot path is checked with
  `MatchingBenchmark` on both `detailedMatchFailures` arms.
- Prefer recomputing on demand to recording per request; the answer states it was recomputed and
  whether expectations changed since the request arrived.
- Miss-path work is bounded (capped candidates, memoised where safe), never blocks, and never
  calls an LLM.
- Diagnostics never run on the event-log consumer thread, and on-demand re-evaluation must not
  emit match events of its own (the lesson from the dashboard not-matched flood).
- Explain APIs have hard evaluation budgets and report truncation.
- LLM calls happen only on explicit request, asynchronously, timeout-bounded and redacted.
