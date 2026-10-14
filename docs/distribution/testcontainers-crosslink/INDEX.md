# Testcontainers Cross-Link Outreach — Submission Index

MockServer ships official, MockServer-maintained Testcontainers modules for Node, Python,
.NET, Go, and Rust (in `mockserver-testcontainers/<lang>/`). The Java module is already
submitted upstream (testcontainers-java #11833 open; community-module-registry #184 open).

This directory contains ready-to-post PR/issue drafts for the remaining five languages.
**Do NOT post any draft until the corresponding package is live on its registry.**

## Publish-Gating Rule

Each draft is blocked on its package being publicly retrievable. The registry liveness
URLs are:

| Language | Registry | Liveness URL |
|----------|----------|--------------|
| Node | npm | https://www.npmjs.com/package/@mockserver/testcontainers |
| Python | PyPI | https://pypi.org/project/testcontainers-mockserver/ |
| .NET | NuGet | https://www.nuget.org/packages/Testcontainers.MockServer/ |
| Go | pkg.go.dev | https://pkg.go.dev/github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9 |
| Rust | crates.io | https://crates.io/crates/testcontainers-mockserver |

Do not post a PR or issue until `curl`/`pip install`/`dotnet add package`/`go get`/`cargo add`
confirms the package resolves. Advertising a package before it is live breaks the user
experience and harms trust.

## Submission Order

Post in this order to maintain a coherent narrative in the community module registry:

1. **community-module-registry** (`community-module-registry.md`) — add all five
   language entries to the catalog at once. Gate on ALL five packages being live.
2. **Python** (`python.md`) — testcontainers-python is the highest-traffic Python
   testing library; a PR into their docs has broad reach. Gate on PyPI live.
3. **Node** (`node.md`) — gate on npm live.
4. **.NET** (`dotnet.md`) — gate on NuGet live.
5. **Go** (`go.md`) — gate on pkg.go.dev live.
6. **Rust** (`rust.md`) — gate on crates.io live.

## Status Tracking

Update the table below as submissions are made. Do not remove or reorder rows.

| Draft | Package live? | Submitted? | URL |
|-------|--------------|------------|-----|
| community-module-registry.md | No | No | — |
| python.md | No | No | — |
| node.md | No | No | — |
| dotnet.md | No | No | — |
| go.md | No | No | — |
| rust.md | No | No | — |

## Java (already submitted)

- testcontainers-java deprecation PR: https://github.com/testcontainers/testcontainers-java/pull/11833
- Community module registry (Java entry update): https://github.com/testcontainers/community-module-registry/pull/184
