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
# No Spring Security method security usage
$ grep -r "EnableMethodSecurity\|EnableGlobalMethodSecurity\|@PreAuthorize\|@PostAuthorize\|@Secured" --include="*.java" mockserver-*/src
# (no results)

# No Spring Security dependencies
$ grep -r "spring-security" --include="pom.xml" .
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

**Advisory:** https://github.com/advisories/GHSA-355h-qmc2-wpwf (CVE-2026-2332)

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

## Code Scanning Alerts (CodeQL)

MockServer is a **test infrastructure tool** designed to intercept, mock, and proxy HTTP traffic. Many CodeQL security findings are **intentional features** required for MockServer's core functionality.

**Note:** CodeQL alerts for intentional features are dismissed with reason "used in tests" due to GitHub's limited dismissal options. The more accurate reason would be "won't fix - intentional product feature", but this is not available. See detailed justifications below.

### Understanding MockServer's Purpose

MockServer is not a public-facing production service. It is used in:
- **Development environments** for testing applications against mocked backends
- **Integration test suites** running in isolated CI/CD pipelines
- **Local development** on developer machines

Users configure MockServer with expectations that control its behavior. This is the intended design, not a vulnerability.

### CodeQL Alerts That Are By Design

#### CRITICAL: Deserialization of User-Controlled Data (`java/unsafe-deserialization`)

**Alert:** #16  
**Location:** `WebSocketMessageSerializer.java`  
**Status:** By Design - Not a Vulnerability

**Why This Is Intentional:**
MockServer deserializes JSON/XML from users to configure mock expectations. This is the **core feature** that allows users to:
- Define expected requests and responses
- Configure verification rules
- Set up dynamic response templates

**Risk Acceptance:**
- MockServer is **not** intended for untrusted environments
- Should only be accessible to test code/developers
- Documentation warns against exposing to public networks
- Uses Jackson (not Java native serialization), but accepts arbitrary class names from WebSocket messages for deserialization
- This is an accepted product risk - the endpoint is intended for trusted tooling only

**Recommendation:** Deploy MockServer only in controlled test environments with network isolation.

---

#### CRITICAL: Server-Side Request Forgery (`java/ssrf`)

**Alert:** #9  
**Location:** `NettyHttpClient.java`  
**Status:** By Design - Not a Vulnerability

**Why This Is Intentional:**
SSRF is **MockServer's primary feature**. It acts as an HTTP proxy that:
- Forwards requests to arbitrary URLs specified by users
- Allows testing applications against real backends
- Enables traffic recording and playback

**Mitigation:**
- MockServer is a testing tool, not a public proxy
- Should be deployed in isolated test environments
- Network policies should restrict outbound access if needed
- Documentation explicitly describes proxy capabilities

**Recommendation:** Use firewall rules to restrict MockServer's outbound network access in sensitive environments.

---

#### HIGH: Cross-Site Scripting (`java/xss`)

**Alert:** #15  
**Location:** `IOStreamUtils.java`  
**Status:** By Design - Dashboard Feature

**Why This Is Intentional:**
MockServer's dashboard displays request/response data for debugging. This includes:
- Rendering HTTP payloads (which may contain HTML/JavaScript)
- Showing headers and cookies
- Displaying templated responses

**Mitigation:**
- Dashboard is intended for developers, not end users
- Should not be exposed to untrusted networks
- Content Security Policy headers can be added if needed

**Recommendation:** Access dashboard only from trusted networks (localhost, VPN, internal network).

---

#### HIGH: Path Injection (`java/path-injection`)

**Alert:** #13  
**Location:** `DashboardHandler.java`  
**Status:** By Design - Dashboard Feature

**Why This Is Intentional:**
The dashboard serves static assets and logs. Path handling uses `getResourceAsStream()` which is constrained by classpath resource lookup.

**Risk Acceptance:**
- No explicit traversal validation or normalization in the code
- Constrained in practice by classpath resource behavior (cannot access arbitrary filesystem)
- Dashboard is intended for local development/test environments only
- Should not be exposed to untrusted users

---

#### HIGH: Regular Expression Injection (`java/regex-injection`)

**Alerts:** #11, #12  
**Locations:** `PathParametersDecoder.java`, `NottableString.java`  
**Status:** By Design - Matching Feature

**Why This Is Intentional:**
Users provide regex patterns to match requests. This enables:
- Flexible request matching (e.g., `/api/users/[0-9]+`)
- Parameter extraction from paths
- Dynamic response selection based on patterns

**Risk Acceptance:**
- Regex patterns come from test configuration (trusted source in test environments)
- Not intended for use with untrusted pattern input
- Java's regex engine can suffer catastrophic backtracking with malicious patterns
- This is an accepted risk - patterns are controlled by developers writing tests

**Recommendation:** Users should avoid overly complex regex patterns in high-volume test scenarios.

---

#### MEDIUM: HTTP Response Splitting (`java/http-response-splitting`)

**Alert:** #14  
**Location:** `MockServerHttpResponseToHttpServletResponseEncoder.java`  
**Status:** By Design - Response Mocking Feature

**Why This Is Intentional:**
MockServer allows users to specify arbitrary HTTP headers and response bodies to mock backend services. This includes:
- Custom headers (including non-standard ones)
- Multi-line header values
- Raw response encoding

**Mitigation:**
- Headers come from test expectations (trusted source)
- Not from untrusted external input
- Used only in test environments

---

### Deployment Recommendations

To use MockServer securely:

1. **Network Isolation**
   - Deploy only in test/dev environments
   - Do not expose to public internet
   - Use firewall rules to restrict access to trusted IPs

2. **Access Control**
   - Bind to `localhost` for single-machine testing
   - Use VPN or internal networks for shared instances
   - Consider authentication if exposing beyond localhost

3. **Outbound Restrictions**
   - Apply firewall egress rules if SSRF is a concern
   - Whitelist allowed destination hosts for proxy mode
   - Monitor outbound connections in production-like test envs

4. **Lifecycle Management**
   - Start MockServer only when needed for tests
   - Shut down after test completion
   - Do not leave running instances unattended

### Summary

All CodeQL alerts are **acknowledged and intentional**. MockServer is a powerful testing tool that requires:
- User-controlled configuration (deserialization)
- Arbitrary request forwarding (SSRF)
- Flexible matching (regex injection)
- Full HTTP control (response splitting, XSS)

These capabilities are **essential features**, not vulnerabilities, when used in the intended controlled testing environments.
