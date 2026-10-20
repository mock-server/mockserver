# Security Defaults — A Future Major Release

**Status:** Open. Five insecure-by-default configuration values (DEF-1, DEF-5, DEF-6, DEF-7,
DEF-8) still flip together in a single future major release — their defaults are still the
insecure values below. (DEF-2, DEF-3 and DEF-4 shipped earlier and are no longer tracked here;
see `changelog.md`.) DEF-2 and DEF-3 (the Velocity/JavaScript template RCE defaults) shipped
ahead of this bundle in response to a reported vulnerability (GHSA-7pwj-xvc2-hfpc), deliberately
breaking Goal 1 ("ship every flip in one release") for those two items because leaving a reported
RCE default in place until the next major release was not defensible. There is a precedent for
breaking Goal 1 again, but DEF-5's case is not quite the same shape: GHSA-jg86-hv4g-8h78 (fixed
9.0.0; see [release-9-follow-ups.md](../release-9-follow-ups.md) F6) was a bypass of
`forwardProxyBlockPrivateNetworks`'s own enforcement when a user had already opted in — a true
default would not by itself have mitigated it, since the bug was in the check, not in the
default being off. It still raises DEF-5's priority: the advisory shows the SSRF path is
actively probed; users who never opted in have no SSRF protection at all, so a true default
protects far more users now that the check itself is fixed. The owner should weigh moving DEF-5
ahead of this release on that basis, not on the DEF-2/DEF-3 precedent being identical.
**Created:** 2026-05-26 (supersedes `security-remediation.md`)
**Last verified against code:** 2026-10-20
**Scope:** Configuration default changes only. All underlying mitigations (timeouts, validators, body limits, secure RNG, XXE blocks) have already shipped and are wired behind these properties; only the defaults remain.

## Goals

1. Ship every insecure default flip in one release so users perform a single migration.
2. Each flip is **fully reversible** by setting one property — no functionality is removed.
3. DEF-1 already emits a WARN when set to its insecure value, so its users have had a deprecation window. DEF-5, DEF-6, DEF-7 and DEF-8 warn at startup since the release after 8.0.0 (`UpcomingDefaultChanges`, logged once per JVM from `LifeCycle.startedServer`, only while the property is not set), so their deprecation window has started and the flips may land in a future major release (not 9.0.0, owner decision 2026-10-14).
4. One consolidated migration guide. One major version bump.

## Items

| ID | Property | Old default | New default | Breaking? |
|----|----------|-------------|-------------|-----------|
| DEF-1 | `forwardProxyTLSX509CertificatesTrustManagerType` | `ANY` | `JVM` | Yes |
| DEF-5 | `forwardProxyBlockPrivateNetworks` | `false` | `true` | Yes |
| DEF-6 | `localBoundIP` | `""` (all interfaces, `0.0.0.0`) | `127.0.0.1` | Yes (large impact) |
| DEF-7 | `attemptToProxyIfNoMatchingExpectation` | `true` | `false` | Yes |
| DEF-8 | `maxInitialLineLength` / `maxHeaderSize` / `maxChunkSize` | 64 KiB / 256 KiB (bounded 2026-10, were `Integer.MAX_VALUE`) / `Integer.MAX_VALUE` | `8 KiB` / `16 KiB` / `16 KiB` | Hardening; breaks only pathological clients |

---

## DEF-1 — Forward-proxy trusts JVM trust store, not everything

**Property:** `mockserver.forwardProxyTLSX509CertificatesTrustManagerType`
**Defined:** `mockserver-core/.../ConfigurationProperties.java`
**Today:** Defaults to `ANY`, which installs `InsecureTrustManagerFactory.INSTANCE` and accepts self-signed, expired, hostname-mismatched, and otherwise invalid certificates when MockServer acts as an HTTPS forward proxy.
**Flip:** Default to `JVM` so the JDK trust store validates upstream certificates the same way any standard Java HTTPS client would.
**Opt-out:** `mockserver.forwardProxyTLSX509CertificatesTrustManagerType=ANY` (a WARN is logged when this is in effect — already implemented). Users with a small set of trusted self-signed CAs should prefer `=CUSTOM` plus `mockserver.forwardProxyTLSCustomTrustX509Certificates` pointing at the CA bundle, rather than disabling validation entirely.
**Why it's safe to flip now:** the WARN has been in place since the prior security remediation pass; no functionality is removed.

---

## DEF-5 — Block SSRF to private networks by default

**Property:** `mockserver.forwardProxyBlockPrivateNetworks`
**Today:** Defaults to `false` (was originally introduced as opt-in to avoid breaking localhost / Docker / k8s setups). The validator (`InetAddressValidator`) is already wired into both forward action handlers.
**Flip:** Default to `true`. Forward actions whose target resolves to RFC 1918, loopback, link-local, or AWS metadata (`169.254.169.254`, `fd00:ec2::254`) are rejected.
**Opt-out:** `mockserver.forwardProxyBlockPrivateNetworks=false`.
**Compatibility risk:** This is the highest-impact flip for tests that use MockServer to forward between containers. The migration guide must document opt-out clearly with example Docker Compose / k8s usage.

---

## DEF-6 — Bind to loopback by default

**Property:** `mockserver.localBoundIP`
**Today:** Defaults to `""` which resolves to `0.0.0.0`, listening on every interface. A developer on a shared / corporate network exposes their MockServer instance to the LAN by default.
**Flip:** Default to `127.0.0.1` (IPv4 loopback). Inside containers users must set `0.0.0.0` explicitly to reach MockServer from the host.
**Opt-out:** `mockserver.localBoundIP=0.0.0.0` or any specific interface.
**Startup warning:** while `localBoundIP` is not set, the warning tells container users to set `MOCKSERVER_LOCAL_BOUND_IP=0.0.0.0`; when this flip lands, bake that ENV into the Dockerfiles and Helm chart as planned, and the warning needs no change for them.
**Compatibility risk:** **This is the most disruptive flip in this release.** Every Docker / Kubernetes / Helm deployment will need to set `MOCKSERVER_LOCAL_BOUND_IP=0.0.0.0`. Bundle this change with:
- An updated Dockerfile `ENV MOCKSERVER_LOCAL_BOUND_IP=0.0.0.0` (so the container image keeps working out-of-the-box; the loopback default only takes effect for raw JAR / WAR users)
- Updated `docker-compose` and Helm chart examples
- A prominent section in the migration guide

If the disruption is judged too high, an alternative is to keep `0.0.0.0` and add a startup banner warning when not on loopback. **Recommendation: flip the default; bake `0.0.0.0` into the container ENV.**

---

## DEF-7 — Don't proxy unmatched requests by default

**Property:** `mockserver.attemptToProxyIfNoMatchingExpectation`
**Today:** Defaults to `true`. Any inbound request that doesn't match an expectation is forwarded to the request's `Host:` header, turning MockServer into an open proxy if exposed.
**Flip:** Default to `false`. Unmatched requests return `404 Not Found` (existing fallback path).
**Opt-out:** `mockserver.attemptToProxyIfNoMatchingExpectation=true`.
**Compatibility risk:** Affects users running MockServer as a forward proxy that records or selectively mocks traffic. The opt-out preserves the exact previous behaviour. Document the opt-out alongside the proxy / recording examples in the consumer docs.

---

## DEF-8 — Bound HTTP parsing limits

**Properties:** `mockserver.maxInitialLineLength`, `mockserver.maxHeaderSize`, `mockserver.maxChunkSize`
**Today:** `maxInitialLineLength` defaults to 64 KiB and `maxHeaderSize` to 256 KiB (performance-programme item 89, 2026-10: bounded in a minor release at values well above what common servers and load balancers accept, so the request line and headers are no longer an unbounded buffer); an over-limit request is refused with `414` / `431`. `maxChunkSize` is still `Integer.MAX_VALUE`, but it only splits body bytes as they arrive and bounds no buffer. What remains of DEF-8 is tightening the first two to the values below, which a real client can exceed (long URLs, large cookies or Kerberos tokens).
**Flip:** Defaults aligned with Netty's own defaults and standard reverse proxies:

| Property | New default |
|----------|-------------|
| `maxInitialLineLength` | 8192 (8 KiB) |
| `maxHeaderSize` | 16384 (16 KiB) |
| `maxChunkSize` | 16384 (16 KiB) |

**Opt-out:** Set the relevant property higher. The existing `maxRequestBodySize` (10 MiB) and `maxResponseBodySize` (50 MiB) defaults are unchanged.
**Compatibility risk:** Negligible for real-world traffic. Tests that intentionally craft huge headers/URIs to exercise edge cases must opt out.

---

## Under consideration: DEF-9 — Control-plane `Host` allow-list and browser-request residue

**Not yet scheduled; no property exists.** The browser-origin check (`controlPlaneCorsAllowOrigin`) refuses control-plane requests from other sites' pages. Two browser paths remain:

- **DNS rebinding.** A malicious site re-points its own host name at MockServer, and its page is then same-origin with the `Host` it sends. A control-plane `Host` allow-list (loopback names plus configured names) would stop it, but a default that only admits loopback names breaks every client that reaches MockServer by a container, service or machine name, which is the common Docker Compose, Kubernetes and CI deployment. Options: an opt-in property in a minor release, or a default tied to DEF-6 (a loopback-bound server only needs loopback `Host` names).
- **Requests the page cannot read** (closed). A control-plane request with a disallowed `Origin` is now refused with `403` whatever its method, so simple cross-site requests and requests approved by an expectation-answered or cached preflight no longer run.
- **A browser proxied through MockServer.** The control plane is routed by path whatever the `Host`, so any site fetched through MockServer as the browser's proxy reaches `/mockserver/*` from its own origin. Option: serve the control plane only for requests addressed to MockServer itself (its own `Host` names), not for proxied ones.

Today's mitigations are control-plane authentication (which stops both), DEF-6 and browsers' local-network-access protections.

---

## Under consideration: DEF-10 — Bind a callback client id to its connection

**Low severity; not yet scheduled.** A callback WebSocket that registers with an `X-CLIENT-REGISTRATION-ID` already in use replaces the existing connection, so it receives that client's object callbacks and breakpoints. Replacement is deliberate: refusing a duplicate id locked clients out after a half-open drop (no ping or idle timeout on callback channels), and was reverted. Exploiting it needs a leaked 122-bit client id, and since the callback upgrade now requires control-plane authentication when it is on, only an authenticated principal can do it; with authentication off, the whole control plane is already open. Options: bind the id to the authenticated principal that registered it, or ping the existing connection and refuse only if it answers.

---

## Items considered and rejected

For the record so future contributors don't re-litigate:

- **`tlsMutualAuthenticationRequired` → `true`**: rejected. Forcing every inbound connection to present a client certificate would break virtually every user; mTLS remains opt-in.
- **`controlPlaneTLSMutualAuthenticationRequired` → `true`**: rejected for the same reason; the control-plane mTLS path is unchanged.
- **`dynamicallyCreateCertificateAuthorityCertificate` → `true`**: rejected. Flipping breaks every client that has already trusted the bundled MockServer CA. The default static CA is documented as a test-only convenience.
- **`preventCertificateDynamicUpdate` → `true`**: rejected. Dynamic SAN updates are part of the documented proxy flow; flipping would silently break in-flight tests.
- **`initializationClass` allowlist**: not in this release. The property is empty by default, so the attack surface only opens if a user already configures arbitrary class loading. Track separately if a real exploit path emerges.
- **CORS defaults**: not a flip here. This item used to say the CORS defaults were "already safe"; that was wrong from 7.0.0, when a blank `corsAllowOrigin` began reflecting any request `Origin` on every control-plane response and `/mockserver/*` preflights began to be answered regardless of `enableCORSForAPI`, so any web site could read and write a MockServer on the developer's machine. It was fixed as a security fix ahead of this bundle (2026-10, see `changelog.md` → Security): a new `controlPlaneCorsAllowOrigin` (blank = loopback origins plus the server's own `Host`) governs the control plane, `Origin: null` is refused, the dashboard and callback WebSocket upgrades apply the same rule, and `corsAllowOrigin` now only governs mocked and proxied responses, unchanged. What the fix does not cover is DEF-9. See [tls-and-security.md → Browser Origins](../../code/tls-and-security.md#browser-origins-cors-and-websocket-upgrades).

---

## Release & migration

- **Version:** Single major version bump.
- **Migration guide:** One document with one section per `DEF-*` ID, each containing: *what changed*, *how to detect if you're affected*, and *one-line opt-out*.
- **Release notes:** Top-line summary "secure-by-default" framing; explicit BREAKING block listing the remaining flips (DEF-2 and DEF-3 already shipped 2026-07-27, and DEF-4 in 8.0.0, so they are not part of this release).
- **Container image:** Set `ENV MOCKSERVER_LOCAL_BOUND_IP=0.0.0.0` in the Dockerfile so the container experience is unchanged. Document this carve-out clearly so users running the raw JAR/WAR know they're getting the loopback default.
- **Helm chart:** Pass `MOCKSERVER_LOCAL_BOUND_IP=0.0.0.0` (already needed for in-cluster reachability).
- **Pre-release validation:** Run the full Buildkite suite plus a manual smoke test against the official Docker image and the Helm chart to confirm `DEF-6` does not break the default container path.

## Implementation order

All flips are independent property changes; order does not matter functionally. Suggested PR sequencing for review clarity:

0. **Done:** startup WARNs for DEF-5, DEF-6, DEF-7 and DEF-8 (`UpcomingDefaultChanges`, logged from `LifeCycle.startedServer`). When the flips land, delete the flipped entries from `UpcomingDefaultChanges`, their tests and the "Default changing in a future major release" notes in `configuration_properties.html`. The WAR deployments do not emit these warnings; decide whether they should before the flip (DEF-5 and DEF-7 apply to the WAR; DEF-6 and DEF-8 do not, as the servlet container binds and parses).
1. DEF-1 — a single-value default change; trivial review.
2. DEF-5, DEF-7 — boolean flips on existing well-tested validators.
3. DEF-8 — three numeric defaults; verify all integration tests still pass.
4. DEF-6 — bind-address change; bundled with Dockerfile ENV and Helm chart updates.

Each PR must update `jekyll-www.mock-server.com/mock_server/configuration_properties.html` and the relevant `docs/` entry under `code/` or `operations/`.
