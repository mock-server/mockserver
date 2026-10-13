# UI competitor-inspired feature ideas — residue after the 2026-07 build

All six items from the original backlog shipped in 2026-07 (`0f6565a90..50fae90e8`), each as
its own commit with an adversarial review. What remains here is the **residue**: one deferred
sub-feature, one instruction that turned out to be wrong and must not be retried, and a set
of small follow-ups the reviews surfaced.

---

## Shipped, with deviations worth knowing

| # | Item | Outcome |
|---|---|---|
| 1 | GraphQL operation awareness | Shipped in full |
| 2 | Host focus / pin | Shipped as a collapsible host list, not a sidebar — a second vertical column starved the rows in a master/detail split that resizes to 260px |
| 3 | Named network-condition presets | Shipped, but **one fault per preset** (see below) |
| 4 | Multi-tab workspaces | Shipped **except per-tab connection params** (see below) |
| 5 | Inline execute-and-show-response | Shipped; cookie matchers are declared unexercisable rather than fired |
| 6 | Filter-DSL unification | Shipped for breakpoints and verification; **chaos deliberately not swapped** (see below) |

### Item 3 — presets set one fault, not a combination

`TcpChaosHandler.channelRead` is first-match-wins with an early return per branch
(`down > reset_peer > limit_data > slicer > bandwidth > latency`), documented as contract in
`TcpChaosProfile.java` and `chaos_testing.html`. A preset carrying both a latency and a
bandwidth value would display a number the engine never applies. Presets therefore set
exactly one fault, enforced by a discriminated union and a test citing the handler's order.
**If the engine ever composes faults, that test breaks deliberately** — at which point
combination presets become possible.

### Item 5 — cookies cannot be exercised from the dashboard

`Cookie` is a forbidden request-header name: `fetch` accepts it on a standalone `Headers`
object and strips it silently when the `Request` is constructed. Planting via
`document.cookie` was tried and withdrawn — an unescaped `; domain=` in a matcher value
created a domain-scoped cookie the cleanup could not delete, which survived and reached
sibling subdomains. The widget now names cookie matchers as unexercisable and points at
curl. Revisiting this needs a same-origin-only design with fail-closed name/value
validation; it is not worth it for a convenience affordance.

---

## Do not retry: item 6 step 3 for chaos — REFUTED

The original plan said to add an `OperatorSearchField` to the chaos host-select. **This is
wrong and was deliberately not done.**

`ServiceChaosRegistry.get(host)` is `byHost.get(normalizeHost(host))` on a
`ConcurrentHashMap` — trimmed, port-split, lower-cased, **exact key**, no pattern matching
anywhere in the class. `TcpChaosRegistry` is the same shape, and both call sites
(`HttpActionHandler.java:529`, `TcpChaosHandler.java:132`) are exact-key. Meanwhile the
shared DSL registers `host` with `glob: true` and an advertised example of
`host:*.example.com`, which `OperatorSearchField` renders into the placeholder and help
tooltip.

So the swap would have offered a wildcard the server stores as a literal key that can never
fire — precisely the "operators that silently do nothing in a given context" failure the
plan itself warned about. The chaos panel keeps a plain Host field and now **rejects**
wildcards, pasted `host:` operators, URL schemes and paths on all four entry points
(HTTP form, Quick Chaos, TCP form, and each chaos-experiment stage).

Making this viable would require pattern matching in the registry itself — a server-side
change with its own design questions (precedence between an exact and a glob entry, cost per
request), not a UI change.

---

## Deferred: per-tab connection params (item 4's third sub-feature)

Workspaces carry view and search terms. They do **not** carry a connection target, so the
plan's "target different MockServer instances from one window" value is not yet delivered.

The blocker is that `useConnectionParams` is a `useMemo(…, [])` derived purely from
`window.location`, and six components call it directly rather than taking the prop `App`
drills — including four that issue destructive control-plane calls (`deleteExpectation`,
`clearLoggedRequest`, `replayRequests`, `setServerMode`). A partial override would route
those at the wrong instance, so half of this feature is worse than none.

To finish it:
1. Merge the active workspace's override over the URL-derived base inside
   `hooks/useConnectionParams.ts` — that one edit covers every consumer.
2. Add a `connection` field to `Workspace` and an edit affordance in the tab bar.
3. **Reset the four data arrays on a target change**, so instance A's traffic is never shown
   under instance B's tab.
4. The single-socket invariant survives by construction: a params identity change re-creates
   `connect`, and `connect()` closes the previous socket first.

Re-assessed 2026-10-13 and left deferred: it is not small. Beyond the four steps above,
finishing it needs:
- the Breakpoints callback WebSocket is an app-lifetime singleton
  (`getBreakpointCallbackClient`) whose matchers are owned on the server by its `clientId`,
  so switching a tab's target must reconnect it, and the matchers registered on instance A
  are then dropped or orphaned; that needs a decision, not just a reset;
- module-level caches outside the four store arrays also hold instance A's data: the
  remembered shortened expectations (`lib/fullExpectation.ts`), the loaded full bodies
  (`fullMessages`), the auto-load attempted markers (`useAutoLoadLlmRows`) and the rows held
  by `useHeldItems`;
- a target on another origin makes every control-plane `fetch` cross-origin, which works
  only when that MockServer answers CORS for its API (`enableCORSForAPI`); the dashboard
  needs to say so rather than fail with a bare network error;
- a workspace that holds a connection target is exactly the "more state" the single-writer
  follow-up below warns about, so two browser tabs would need resolving first.

---

## Follow-ups the reviews surfaced

- **`status:` on the verification scope.** The Verify view's quick scope still flags `status:` as
  unsupported, because its response matcher form has a single exact `statusCode` field.
  `StatusCodeMatcher` already supports an exact code, a class range such as `"2XX"` and numeric
  operators via `statusCodeRange`, so adding a range field to the verification response matcher
  (and passing `[...REQUEST_SCOPE_FIELDS, 'status']` to its scope, translating each comparator to
  `statusCodeRange`) would make `status:>=400` work there. `lib/filterScope.ts`
  `parseRequestScope` already produces inclusive `statusMin`/`statusMax` bounds for the breakpoint
  scope, with a test that they select exactly what the search box selects.
- **Workspace persistence assumes a single writer per origin.** Two dashboard browser tabs
  share one `localStorage`, so the legacy keys can be written by a tab on workspace 1 while
  the blob says workspace 2 is active. The payload is a view name and five strings, so it is
  not worth a coordination mechanism — but anything that makes a workspace hold more state
  should revisit it.

---

## LOW / trivial notes (still unshipped, still low priority)

- **List virtualization** — `LogPanel.tsx`, `SessionInspector.tsx` and `AuditPanel.tsx` use plain
  MUI lists with no windowing. This only matters if the server-side WebSocket cap (~100 items)
  is raised. Do not virtualize preemptively.
- **Monaco idle prefetch** — `JsonEditorLazy.tsx` loads Monaco on first use with no
  `requestIdleCallback` prefetch. Marginal, since it is already behind a lazy import.
- **`ConfigurationDialog`** edits 18 curated properties out of ~100. This is deliberate: only
  properties safe to change at runtime and meaningful to a typical user are exposed.

---

## Explicitly rejected — do not reopen

**Pinned / recent view tabs** — implemented in `d536433f1` and deliberately reverted in
`d55d7077d` because labelled tabs in the app bar's flexible nav region stopped the bar
fitting at typical widths. This is a closed decision.

The workspace tab bar (item 4) is a different concept and respects that constraint: it is its
own row below the app bar, does not exist until a second workspace does, and costs the app
bar a single bare icon button in the fixed utility cluster.
