# Security Policy

## Supported Versions

MockServer maintains support for Java 11 as the minimum supported version to ensure broad compatibility. Currently supported versions:

| Version | Supported          |
| ------- | ------------------ |
| 5.15.x  | :white_check_mark: |
| < 5.15  | :x:                |

## Security Posture

### MockServer is a Development and Testing Tool

**Important:** MockServer is designed for **development, testing, and QA environments only**. It should **never** be deployed in production or exposed to untrusted networks.

### Java 11 Compatibility vs Security Updates

MockServer prioritizes **broad compatibility** over addressing all security vulnerabilities:

- **Minimum Java version:** Java 11 (approximately 23% of Java projects still use Java 11)
- **Spring Framework:** 5.3.x (latest Java 11-compatible version)
- **Spring Boot:** 2.7.x (latest Java 11-compatible version)

Many security fixes require **Spring 6.x** or **Spring Boot 3.x**, which mandate **Java 17+**. Upgrading would break compatibility for users on Java 11.

### Dependabot Alerts

You may see multiple Dependabot security alerts for MockServer. Here's why:

1. **Spring 5.3.x is in maintenance mode** - Most security fixes target Spring 6.x only (requires Java 17+)
2. **No patch available** - Many alerts show `fixed_version: null`, meaning no fix exists for Spring 5.3.x
3. **Upgrade requires Java 17+** - Fixing these would break Java 11 compatibility

### Risk Assessment

Most reported vulnerabilities require **attacker-controlled input** to exploit:

- **Unsafe deserialization** - Requires attacker to control serialized data sent to MockServer
- **Path traversal** - Requires attacker to control file paths in requests
- **DataBinder issues** - Requires specific attack patterns against Spring MVC/WebFlux

In a **development/testing environment** where:
- MockServer is not exposed to untrusted networks
- Only developers/testers have access
- No sensitive production data is processed

...these vulnerabilities pose **minimal practical risk**.

### Dismissed Alerts

The following types of alerts are dismissed as **not applicable** to MockServer's use case:

1. **Spring deserialization vulnerabilities** - MockServer does not deserialize untrusted user data through Spring's mechanisms
2. **Spring MVC path traversal** - MockServer uses its own request routing, not Spring MVC's file serving
3. **DataBinder case sensitivity** - Not exploitable in MockServer's mocking/proxying use case

## Best Practices

To use MockServer securely:

### ✅ DO:
- Run MockServer only in **development, testing, or QA environments**
- Restrict network access to **trusted users only** (developers, testers, CI/CD)
- Use MockServer behind a **firewall or VPN**
- Stop MockServer instances when not in use
- Keep MockServer updated to the latest version for bug fixes

### ❌ DO NOT:
- Deploy MockServer in **production environments**
- Expose MockServer directly to the **public internet**
- Use MockServer to handle **sensitive production data**
- Rely on MockServer for **security-critical operations**
- Keep MockServer running unnecessarily

## Reporting a Vulnerability

If you discover a security vulnerability in MockServer itself (not dependency alerts), please report it via:

- **GitHub Security Advisories:** https://github.com/mock-server/mockserver/security/advisories/new
- **Email:** Contact the maintainers through GitHub

Please **do not** open public issues for security vulnerabilities.

### What to include:

1. Description of the vulnerability
2. Steps to reproduce
3. Potential impact
4. Suggested fix (if available)

We will respond within **7 days** and work with you to understand and address the issue.

## Upgrade Path

If you require a fully patched Spring framework:

1. **Upgrade to Java 17 or later** in your environment
2. **Open an issue** requesting Java 17+ support - if enough users need this, we may create a separate Java 17+ branch
3. Consider using **alternative mocking tools** that already require Java 17+

## Questions?

For security-related questions, see:
- [GitHub Discussions](https://github.com/mock-server/mockserver/discussions)
- [GitHub Issues](https://github.com/mock-server/mockserver/issues)
