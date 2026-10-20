# Release 9.0.0 Follow-ups

**Outcome.** Two rows remain. F6 is waiting on GitHub to assign a CVE, and F10 waits for the 9.1.0
release. F1 to F5 and F7 to F9 have landed. Each row is a self-contained change. This file
is deleted once every row is closed. The Netty pull request and issue (backlog rows 91a and 540a)
stay in [performance-measurement-backlog.md](performance-measurement-backlog.md) for after 9.0.0.

## What remains

| # | Item | Context | Blocked on | Next |
|---|---|---|---|---|
| F6 | Publish security advisory GHSA-jg86-hv4g-8h78 (`forwardProxyBlockPrivateNetworks` bypass, fixed in 9.0.0) | The draft advisory is prepared: CVSS 3.1 `AV:N/AC:H/PR:N/UI:N/S:C/C:L/I:N/A:N` (4.0 Medium), CWE-918, affected `org.mock-server:mockserver-netty` `< 9.0.0`, patched `9.0.0`; the fix is d0e0c521b. 9.0.0 is published. The owner chose to request a CVE and then publish. The CVE was requested on 2026-10-16 and again on 2026-10-17, and `cve_id` was still null on 2026-10-17. | GitHub assigning the CVE | Check with `gh api repos/mock-server/mockserver-monorepo/security-advisories/GHSA-jg86-hv4g-8h78 --jq .cve_id`. Once it is set, publish with `gh api -X PATCH repos/mock-server/mockserver-monorepo/security-advisories/GHSA-jg86-hv4g-8h78 -f state=published`. |
| F10 | Note the before/after action change on the website | Since 60f29b79e, chaining the singular `withBeforeAction` / `withAfterAction` adds actions instead of replacing the previous one. | the 9.1.0 release | Add a "from 9.1.0, chained singular calls add actions" note to `jekyll-www.mock-server.com/mock_server/before_and_after_actions.html`. |
