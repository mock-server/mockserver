# Security Policy

## Supported Versions

MockServer provides security updates for the latest major version only.

| Version | Supported          |
| ------- | ------------------ |
| 5.x     | :white_check_mark: |
| < 5.0   | :x:                |

## Reporting a Vulnerability

To report a security vulnerability, please email jamesdbloom@gmail.com with:

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if available)

We aim to respond within 48 hours and will keep you updated on the progress.

## Current Security Status

### Known Issues Not Affecting MockServer

The following security advisories appear in Dependabot alerts but **do not affect MockServer**:

#### CVE-2025-41249: Spring Framework Method Security Bypass (HIGH)

**Status:** Not Affected  
**Package:** `org.springframework:spring-core` 5.3.39  
**Reason:** This vulnerability only affects applications using Spring Security's `@EnableMethodSecurity` feature. MockServer does not use this feature.

**Evidence:**
```bash
$ grep -r "EnableMethodSecurity" --include="*.java" mockserver-*/src
# (no results)
```

**Why Not Upgrade to Spring 6?**  
Spring 6 requires:
- Java 17+ (MockServer targets Java 11 for compatibility with 23% of projects)
- Jakarta EE 9+ migration (`javax.*` → `jakarta.*` namespace)

This would break compatibility for users on Java 11 and require a major version bump.

**Commercial Patch:** A commercial patch (5.3.45) exists via Spring Enterprise (Broadcom subscription required); no open-source patch is available for Spring 5.x.

### Pending Upstream Patches

#### CVE-2026-2332: Jetty HTTP Request Smuggling (HIGH)

**Status:** Awaiting Patch  
**Package:** `org.eclipse.jetty:jetty-http` 9.4.58.v20250814  
**Fix Version:** 9.4.60 (not yet released as of 2026-05-04)  
**Impact:** Request smuggling via quoted strings in chunked transfer encoding

**Mitigation:** Using Jetty for examples only, not for production deployments. MockServer's production runtime uses Netty, not Jetty.

**Tracking:** Monitoring https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-http/ for 9.4.60 release

**Advisory:** https://github.com/advisories/GHSA-xxxx (CVE-2026-2332)

#### CVE-2025-11143: Jetty HTTP Header Handling (LOW)

**Status:** No Patch Available  
**Package:** `org.eclipse.jetty:jetty-http` 9.4.58.v20250814  
**Severity:** Low  
**Description:** Information exposure via malformed HTTP headers  
**Impact:** Jetty examples module only (not used in production runtime)

**Advisory:** https://nvd.nist.gov/vuln/detail/CVE-2025-11143

## Java 11 Compatibility Policy

MockServer maintains **Java 11** as the minimum supported version to maximize compatibility. This constrains certain dependency upgrades:

| Dependency | Maximum Version | Blocking Factor |
|-----------|----------------|-----------------|
| Spring Framework | 5.x | Spring 6 requires Java 17+ |
| Tomcat | 9.x | Tomcat 10+ uses `jakarta.servlet` namespace |
| Jetty | 9.x | Jetty 10+ migrates to `jakarta.servlet` namespace |

Security updates requiring Java 17+ or Jakarta EE 9+ migration are deferred until a future major version that drops Java 11 support.

See [docs/operations/dependencies.md](docs/operations/dependencies.md) for full compatibility matrix.
