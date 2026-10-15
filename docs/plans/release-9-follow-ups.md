# Release 9.0.0 Follow-ups

**Outcome.** Two rows remain: the release-time step for the 9.0.0 security advisory (F6), and a
website-example check fix needed before 10.0.0 (F9). F1 to
F5, F7 and F8 have landed. Each row is a self-contained change. This file
is deleted once every row is closed. The Netty pull request and issue (backlog rows 91a and 540a)
stay in [performance-measurement-backlog.md](performance-measurement-backlog.md) for after 9.0.0.

## What remains

| # | Item | Context | Blocked on | Next |
|---|---|---|---|---|
| F6 | Publish security advisory GHSA-jg86-hv4g-8h78 (`forwardProxyBlockPrivateNetworks` bypass, fixed in 9.0.0) | The draft advisory is prepared: CVSS 3.1 `AV:N/AC:H/PR:N/UI:N/S:C/C:L/I:N/A:N` (4.0 Medium), CWE-918, affected `org.mock-server:mockserver-netty` `< 9.0.0`, patched `9.0.0`; the fix is d0e0c521b. | the 9.0.0 release (§2) | When 9.0.0 is published, confirm the patched version, request a CVE if wanted, and publish the advisory (owner approval). |
| F9 | The website Go-example check must ignore the Go module major before hashing | `scripts/release/go_module_major.py --set N` rewrites `/v9` to `/vN` in the website's Go snippets, which changes their digests: at 10.0.0 about 10 `allowlist-go.yml` digests and 4 loose-block keys go stale and fail mockserver-go and mockserver-website. The Java golden tests already normalise it (`92ccf9e86`) | Owner approval (gated CI-check change in `mockserver-client-ruby/spec/website_examples/check_website_examples.rb`) | Normalise `mockserver-client-go/v<digits>` before hashing, refresh the digests, and prove it with a simulated `--set 10`; land before the 10.0.0 release |
