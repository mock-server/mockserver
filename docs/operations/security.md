# Security

MockServer's security scanning, vulnerability management, and the security posture of released and pre-release artifacts.

## Overview

MockServer is a **development and testing tool** -- it is not designed for production deployment. Its security posture reflects this: the project invests heavily in automated scanning and dependency management to keep the supply chain clean, while deliberately retaining certain capabilities (like SSRF forwarding and trust-all TLS) that are essential for testing but would be vulnerabilities in a production service.

See [SECURITY.md](../../SECURITY.md) for the full security policy, including intentional security behaviours and vulnerability reporting.

## Static Analysis: CodeQL

GitHub's CodeQL semantic analysis runs automatically on:
- Every push to `master`
- Every pull request targeting `master`
- Weekly (Tuesdays at 22:00 UTC)

CodeQL scans **four languages** in the monorepo:

| Language | Scope |
|----------|-------|
| Java | `mockserver/` (server, core, clients, integrations) |
| JavaScript | `mockserver-ui/`, `mockserver-client-node/`, `mockserver-node/` |
| Python | `mockserver-client-python/` |
| Ruby | `mockserver-client-ruby/` |

Results appear in the [GitHub Security tab](https://github.com/mock-server/mockserver-monorepo/security/code-scanning). CodeQL detects issues including SQL injection, path traversal, insecure deserialization, cross-site scripting, and other OWASP Top 10 categories.

**Workflow:** [`.github/workflows/codeql-analysis.yml`](../../.github/workflows/codeql-analysis.yml)

## Dependency Scanning: Dependabot

Dependabot monitors **8 package ecosystems** across the monorepo for outdated and vulnerable dependencies:

| Ecosystem | Directory | PR Limit |
|-----------|-----------|:--------:|
| Maven | `/mockserver` | 10 |
| Maven | `/mockserver-maven-plugin` | 5 |
| npm | `/mockserver-ui` | 5 |
| npm | `/mockserver-client-node` | 5 |
| npm | `/mockserver-node` | 5 |
| pip | `/mockserver-client-python` | 5 |
| Bundler | `/mockserver-client-ruby` | 5 |
| GitHub Actions | `/` | 5 |

Dependabot runs **weekly on Mondays** and opens pull requests for version updates and security patches.

### Java 11 Compatibility Blocklist

MockServer targets Java 11 as its minimum supported version. Many security fixes in the Java ecosystem require Java 17+. Dependabot is configured to **block major version upgrades** for dependencies that would break Java 11 compatibility:

- Spring Framework 6.x (requires Java 17+)
- Spring Boot 3.x (requires Java 17+)
- Tomcat 10+ (requires Jakarta EE 9+)
- Jetty 10+/12+ (requires Java 17+)
- Jakarta EE 9+ namespace (`javax` to `jakarta` migration)

See [Dependencies](dependencies.md) for the full inventory and version ceilings.

### Maven Dependency Graph Submission

A separate GitHub Actions workflow submits the full Maven dependency tree to the GitHub Dependency Graph API on every push to `master`. This enables Dependabot vulnerability alerts for **transitive dependencies** -- not just direct dependencies declared in `pom.xml`.

**Workflow:** [`.github/workflows/dependency-submission.yml`](../../.github/workflows/dependency-submission.yml)

## Vulnerability Scanning: Snyk

Snyk provides a second layer of vulnerability scanning, independent of Dependabot:

- **PR status checks:** Two Snyk integrations (`security/snyk (mockserver)` and `security/snyk (jamesdbloom)`) run on every pull request
- **Dashboard:** [app.snyk.io/org/mockserver/projects](https://app.snyk.io/org/mockserver/projects)
- **Policy file:** [`.snyk`](../../.snyk) documents 28 vulnerability IDs that cannot be fixed due to Java 11 constraints, with expiry dates that trigger periodic review

The `.snyk` policy file excludes `mockserver-examples` (sample code, not shipped) and documents the rationale for each ignored vulnerability. All ignores expire periodically (currently 2026-06-16) to force re-evaluation as the dependency landscape evolves.

See [Snyk Security](snyk-security.md) for the full triage workflow, CLI commands, and vulnerability status by module.

## AI Security Review

In addition to automated scanning, every code change receives a security-focused review as part of the [AI-assisted development process](ai-assisted-development.md):

### Dedicated Security Auditor Agent

A specialist `security-auditor` AI agent performs targeted security reviews with a checklist covering:

- **Secrets & credentials** -- hardcoded tokens, API keys, connection strings, leaked PEM/JKS content, `.env` files
- **Input validation** -- untrusted data paths, missing bounds checks, charset assumptions
- **Injection prevention** -- command injection, LDAP injection, XSS, XXE, SSRF
- **Network security** -- TLS defaults, certificate validation, cipher suite selection, hostname verification
- **Java-specific** -- unsafe deserialization, `Runtime.exec()` usage, weak random, information leakage
- **Netty-specific** -- malformed request handling, ByteBuf release, pipeline state, WebSocket validation
- **Dependencies** -- known CVEs, version pinning, transitive dependency risk

### Security Lens in Every Code Review

The Review Constitution (applied to every commit, not just security-flagged ones) includes an **Insecurity lens** based on STRIDE threat modelling with 13 security principles, including MockServer-specific rules:

- TLS certificate validation must be explicit
- Control plane must be protectable
- Template injection must be prevented
- CORS headers must not weaken security

## GitHub Security Features

The repository uses several GitHub security features:

| Feature | Status | Purpose |
|---------|--------|---------|
| [Code scanning (CodeQL)](https://github.com/mock-server/mockserver-monorepo/security/code-scanning) | Active | Static analysis for vulnerabilities |
| [Dependabot alerts](https://github.com/mock-server/mockserver-monorepo/security/dependabot) | Active | Vulnerable dependency detection |
| [Dependabot security updates](https://github.com/mock-server/mockserver-monorepo/security/dependabot) | Active | Automatic PRs for security fixes |
| [Dependency graph](https://github.com/mock-server/mockserver-monorepo/network/dependencies) | Active | Transitive dependency visibility |
| [Security advisories](https://github.com/mock-server/mockserver-monorepo/security/advisories) | Active | Private vulnerability reporting |
| Secret scanning | Active (GitHub default) | Prevents accidental secret commits |

## SNAPSHOT and Pre-Release Versions

### What are SNAPSHOT versions?

In the Maven ecosystem, a `-SNAPSHOT` suffix (e.g., `5.16.0-SNAPSHOT`) indicates the **in-development** version of the next release. SNAPSHOT artifacts are published to the Sonatype snapshots repository and represent the latest state of the `master` branch.

### Security status of pre-release artifacts

**SNAPSHOT and pre-release versions may contain unresolved security advisories.** This applies to:

| Artifact | Pre-Release Identifier | Registry |
|----------|----------------------|----------|
| Java JARs | `-SNAPSHOT` suffix (e.g., `5.16.0-SNAPSHOT`) | Maven Central snapshots |
| Docker images | `latest` and `SNAPSHOT` tags | Docker Hub |
| Node.js packages | Published only at release time | npm |
| Python package | Published only at release time | PyPI |
| Ruby gem | Published only at release time | RubyGems |

**At formal release time**, all known security issues are resolved to the extent technically possible given the Java 11 compatibility constraint. This means:

1. All Dependabot and Snyk alerts with available patches are addressed
2. Dependencies are updated to their latest compatible versions
3. Any new CodeQL findings are reviewed and resolved
4. The Snyk policy file's ignore expiry dates are reviewed and renewed only if the Java 11 constraint still prevents a fix

**Between releases**, the `master` branch and SNAPSHOT artifacts may temporarily carry unresolved advisories -- for example, when a new CVE is published against a dependency but the fix has not yet been integrated.

### Recommendations for Consumers

- **For maximum security:** Pin to a specific release version (e.g., `5.15.0`), not `latest` or `SNAPSHOT`
- **For Renovate/Dependabot users:** Configure version constraints to only match release versions, not SNAPSHOTs
- **For Docker users:** Use versioned tags (e.g., `mockserver/mockserver:5.15.0`) rather than `latest`
- **Subscribe to releases:** Watch the [GitHub releases page](https://github.com/mock-server/mockserver-monorepo/releases) for new versions with resolved security issues

## Java 11 Compatibility Trade-Off

MockServer maintains Java 11 as its minimum supported version (approximately 23% of Java projects still run Java 11). This means certain dependency upgrades that would resolve security advisories are **not possible** without breaking compatibility:

- Spring Framework 5.3.x cannot be upgraded to 6.x (requires Java 17+)
- Spring Boot 2.7.x cannot be upgraded to 3.x (requires Java 17+)
- Jetty 9.4.x cannot be upgraded to 12.x (requires Java 17+)

These constraints are documented in detail in:
- [SECURITY.md](../../SECURITY.md) -- full risk assessment and intentional behaviours
- [`.snyk`](../../.snyk) -- specific CVE ignores with rationale
- [Dependencies](dependencies.md) -- version ceilings and compatibility constraints

## Vulnerability Reporting

To report a security vulnerability in MockServer, use:
- **GitHub Security Advisories:** https://github.com/mock-server/mockserver-monorepo/security/advisories/new
- **Email:** Contact the maintainers through GitHub

Do not open public issues for security vulnerabilities. See [SECURITY.md](../../SECURITY.md) for full reporting guidelines.
