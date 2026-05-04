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

## Intentional Security Behaviors

MockServer intentionally includes capabilities that would be **dangerous in a production service** but are **required features for a testing tool**. These are **not vulnerabilities** — they are essential for MockServer's purpose.

### Why These Features Exist

As a testing tool, MockServer must allow developers to:
- Mock internal services and APIs
- Test security controls in their own applications
- Simulate various network conditions and edge cases
- Create realistic test scenarios including "dangerous" inputs

**Important:** All expectations and templates are created by **trusted developers/testers**, not by external attackers. MockServer assumes:
- ✅ All users are trusted (developers, testers, CI/CD systems)
- ✅ All expectations are trusted (you control what you mock)
- ✅ The network is trusted (isolated dev/test environment)
- ✅ No production data or systems are involved

### Intentional Behaviors (Features, Not Bugs)

#### 1. Server-Side Request Forgery (SSRF) - **INTENTIONAL**

**What it does:** MockServer's forward action can send requests to any host/port, including:
- Internal IPs: `127.0.0.1`, `localhost`, `10.0.0.0/8`, `192.168.0.0/16`
- Cloud metadata endpoints: `169.254.169.254`
- Private networks and internal services

**Why it's needed:**
```java
// Valid use case: Mock localhost microservice
mockServerClient
    .when(request().withPath("/orders"))
    .forward(forward().withHost("localhost").withPort(8080));

// Valid use case: Test AWS SDK behavior with metadata service
mockServerClient
    .when(request().withPath("/api/config"))
    .forward(forward().withHost("169.254.169.254").withPath("/latest/meta-data/"));
```

**Security control:** MockServer should never be exposed to untrusted networks.

---

#### 2. Regular Expression Denial of Service (ReDoS) - **INTENTIONAL**

**What it does:** Regex matchers accept user-defined patterns without timeout or complexity limits.

**Why it's needed:**
```java
// Valid use case: Test how your app handles expensive regex
mockServerClient
    .when(request().withPath("(a+)+"))  // Pathological regex pattern in path
    .respond(response().withStatusCode(200));

// Testing scenario: Validate your system's regex timeout logic
```

**Security control:** Users control all matchers (trusted input). Optional timeout can be configured if needed.

---

#### 3. Trust-All TLS Certificates (Default) - **INTENTIONAL**

**What it does:** Forward proxy accepts all TLS certificates by default:
- Self-signed certificates
- Expired certificates
- Wrong hostnames
- Invalid certificate chains

**Default setting:** `forwardProxyTLSX509CertificatesTrustManagerType=ANY`

**Why it's needed:**
```java
// Valid use case: Forward to staging environment with self-signed cert
mockServerClient
    .when(request().withPath("/api"))
    .forward(forward()
        .withHost("staging.internal")
        .withScheme(HTTPS));
```

**Security control:** Users can configure `JVM` or `CUSTOM` trust modes if validation is needed for specific test scenarios.

---

#### 4. Template Code Execution (Velocity/JavaScript) - **INTENTIONAL**

**What it does:** 
- Velocity templates can load Java classes (when `velocityDisallowClassLoading=false`, default)
- JavaScript templates can access Java classes via `Java.type()`

**Why it's needed:**
```velocity
## Valid use case: Generate dynamic responses using Java classes
## Note: Class loading requires velocityDisallowClassLoading=false (default)
#set($runtime = $request.class.classLoader.loadClass("java.lang.Runtime"))
#set($uuid = $request.class.classLoader.loadClass("java.util.UUID"))
Response ID: $uuid.randomUUID().toString()
```

```javascript
// Valid use case: Complex response generation logic
var UUID = Java.type("java.util.UUID");
return { requestId: UUID.randomUUID().toString() };
```

**Security control:** Templates are written by developers (trusted code), not by external users.

---

#### 5. JsonPath / XPath Denial of Service - **INTENTIONAL**

**What it does:** JsonPath and XPath expressions can use recursive descent (`..`) and complex queries without timeout.

**Why it's needed:**
```java
// Valid use case: Test expensive JsonPath evaluation
mockServerClient
    .when(request().withBody(jsonPath("$..book[?(@.price < 10)]")))
    .respond(response().withBody("found"));

// Testing scenario: Validate your system handles complex JSON queries
```

**Security control:** Users control all matchers. Optional timeout can be configured if needed.

---

#### 6. XML External Entity (XXE) Processing - **INTENTIONAL**

**What it does:** XML matchers do not disable external entity processing by default.

**Why it's needed:**
```xml
<!-- Valid use case: Test your app's XXE defenses -->
<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
<request>&xxe;</request>
```

**Security control:** All XML is user-controlled test data, not attacker input.

---

#### 7. Legacy TLS Protocol Support (TLSv1.0/1.1) - **INTENTIONAL**

**What it does:** MockServer supports TLSv1.0 and TLSv1.1 (deprecated by RFC 8996).

**Default setting:** `tlsProtocols=TLSv1,TLSv1.1,TLSv1.2`

**Why it's needed:**
- Testing applications that must support legacy clients
- Testing embedded devices with old TLS stacks
- Validating TLS version negotiation logic
- Testing against systems that haven't upgraded yet

**Security control:** Modern TLS (1.2/1.3) is still supported. Users can restrict to modern versions if desired.

---

#### 8. Unbounded Request/Response Bodies - **INTENTIONAL**

**What it does:** HTTP object aggregators accept up to `Integer.MAX_VALUE` (2GB) body size.

**Why it's needed:**
```java
// Valid use case: Test large file upload handling
mockServerClient
    .when(request().withPath("/upload"))
    .respond(response().withBody(new byte[1024 * 1024 * 100]));  // 100MB response
```

**Security control:** Configure JVM heap size appropriately for your test scenarios.

---

### Summary: Features vs Vulnerabilities

| Behavior | In Production Service | In Testing Tool |
|----------|----------------------|-----------------|
| Forward to localhost | 🔴 SSRF vulnerability | ✅ Required feature |
| Accept all TLS certs | 🔴 MITM vulnerability | ✅ Required feature |
| Execute template code | 🔴 RCE vulnerability | ✅ Required feature |
| Process XXE | 🔴 Information disclosure | ✅ Required feature |
| No regex timeout | 🔴 DoS vulnerability | ✅ Required feature |
| Legacy TLS versions | 🔴 Weak encryption | ✅ Required feature |

### What IS a Real Vulnerability?

MockServer **will fix** actual security issues such as:
- ✅ **Correctness bugs** (e.g., logic errors in matchers)
- ✅ **Memory leaks** (e.g., ByteBuf leaks)
- ✅ **Unintended information disclosure** (e.g., logging secrets)
- ✅ **Weak cryptography in certs** (e.g., predictable serial numbers) — **Fixed in 5.15.x**

MockServer **will not restrict** intentional features that enable testing.

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
