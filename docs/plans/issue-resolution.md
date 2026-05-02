# Issue Resolution Plan

## Overview

As of May 2026, MockServer had 184 open GitHub issues. This document provides a systematic analysis of every open issue, grouping them by category, identifying duplicates, issues already fixed, legitimate bugs, feature requests, and issues caused by documentation gaps. Each issue includes an investigation summary and proposed resolution path.

## Completed Work

28 issues were closed as part of this triage:

- **6 duplicates** closed with cross-references to the primary issue
- **7 already resolved / out of scope** closed with explanatory comments
- **4 "is this project dead?"** closed confirming active development and upcoming release
- **9 CVE reports** closed for dependencies already updated in the development branch
- **1 support question** closed with documentation reference
- **1 not-a-bug** closed (429 not from MockServer)

## Summary Statistics

| Category | Count (original) | Closed | Remaining |
|----------|-----------------|--------|-----------|
| Total open issues | 184 | 28 | 156 |
| Confirmed bugs | 42 | 0 | 42 |
| Feature requests | 48 | 0 | 48 |
| Dependency/CVE reports | 18 | 9 | 9 |
| Already resolved / out of scope | 12 | 9 | 3 |
| Duplicates | 14 | 6 | 8 |
| Documentation gaps | 8 | 0 | 8 |
| Questions / support requests | 15 | 2 | 13 |
| Blocked by design decisions | 5 | 0 | 5 |
| Needs more information | 8 | 0 | 8 |
| "Is this project dead?" | 4 | 4 | 0 |

## Priority Classification

Issues are classified as:
- **P0 (Critical)**: Thread-safety bugs causing data corruption, security vulnerabilities
- **P1 (High)**: Functional bugs affecting core matching/proxy/forwarding
- **P2 (Medium)**: Bugs in secondary features (OpenAPI, templates, Helm)
- **P3 (Low)**: Minor bugs, cosmetic issues, edge cases
- **Feature**: Enhancement requests
- **Resolved**: Already fixed or out of scope

---

## Category 1: Thread Safety (P0 — Critical)

These are confirmed thread-safety bugs in the current codebase. All involve shared mutable state without proper synchronization.

### #1978 / ~~#1826~~: `Times.exactly` / `Times.once` is not thread-safe

**Status**: Confirmed bug — P0
**Duplicates**: #1826 closed as duplicate of #1978
**Root cause**: `Times.java` uses a plain `int remainingTimes` field with non-atomic `decrement()`. The `greaterThenZero()` check and `decrement()` call in `RequestMatchers.firstMatchingExpectation()` are not atomic, allowing two threads to both match the same expectation.
**Files**: `mockserver-core/.../matchers/Times.java:31,63-68`, `mockserver-core/.../mock/RequestMatchers.java:222-252`
**Fix**: Replace `int remainingTimes` with `AtomicInteger`. Implement `decrementIfPositive()` that atomically checks and decrements. The check-then-act in `firstMatchingExpectation()` should use the atomic decrement as the gate.

### #1834: Multiple responses for same request are not thread-safe

**Status**: Confirmed bug — P0
**Root cause**: `responseInProgress` is a plain `boolean` (not `volatile`) in `AbstractHttpRequestMatcher`. The match → set flag → decrement flow in `firstMatchingExpectation()` is not atomic. Between `matches()` returning true and `setResponseInProgress(true)`, another thread can also match.
**Files**: `mockserver-core/.../matchers/AbstractHttpRequestMatcher.java:29`, `mockserver-core/.../mock/RequestMatchers.java:222-252,307-320`
**Fix**: Make `responseInProgress` an `AtomicBoolean`. Use `compareAndSet(false, true)` as part of the matching gate. Consider synchronizing the match-decrement-flag sequence.

### ~~#1750~~ / #1773: Velocity template `$json` tool is not thread-safe

**Status**: Confirmed bug — P0
**Duplicates**: #1750 closed as duplicate of #1773
**Root cause**: `VelocityTemplateEngine` creates a single `ToolContext` in the constructor and reuses it for all concurrent template evaluations. The `JsonTool` is configured with "request" scope but the scope is ignored because `ToolManager.createContext()` is only called once. `JsonTool` maintains mutable internal state.
**Files**: `mockserver-core/.../templates/engine/velocity/VelocityTemplateEngine.java:47-48,150`
**Fix**: Create a new `ToolContext` per `executeTemplate()` call, or create a new `JsonTool` instance per request. Also fix the lazy-init race in `getVelocityTemplateEngine()` (missing `volatile`/`synchronized`).

### #1796: Wrong response or 404 with XML body matcher and concurrency

**Status**: Confirmed bug — P0
**Root cause**: `XmlStringMatcher` stores a `DiffBuilder` as a mutable field and reuses it across concurrent `matches()` calls. The `DiffBuilder.withTest()` method mutates the internal `testSource` field, so concurrent calls overwrite each other's test data.
**Files**: `mockserver-core/.../matchers/XmlStringMatcher.java:25,62`
**Fix**: Create a new `DiffBuilder` in each `matches()` call instead of reusing the field-level instance. Alternatively, store only the control `Input` and build a fresh `DiffBuilder` each time.

### #1644: Memory leak in LRUCache

**Status**: Confirmed bug — P1
**Root cause**: `LRUCache` uses `ConcurrentHashMap` + `ConcurrentLinkedQueue` but compound operations (containsKey → remove, poll → remove) are not atomic. The `queue` and `map` can desynchronize, causing orphan entries. Static fields `allCachesEnabled` and `maxSizeOverride` are not `volatile`. The `Entry.expiryInMillis` is a non-volatile `long` mutated from multiple threads.
**Files**: `mockserver-core/.../cache/LRUCache.java:15,47-99`
**Fix**: Replace with Guava's `Cache` or Caffeine. At minimum, make static fields `volatile` and wrap compound operations in synchronized blocks.

---

## Category 2: Proxy, Forwarding & Header Handling (P1)

### #1897: Proxy forwarding results in 421 Misdirected Request

**Status**: Confirmed bug — P1
**Root cause**: When forwarding requests, MockServer preserves the original `Host` header verbatim but opens the TLS connection to a different address (using the socket address for SNI). Servers implementing HTTP/2 strict origin checking (RFC 7540 §9.1.2) return 421.
**Files**: `mockserver-core/.../mappers/MockServerHttpRequestToFullHttpRequest.java:111,123-125`, `mockserver-core/.../httpclient/HttpClientInitializer.java:85`
**Fix**: Update the `Host` header to match the actual forwarding target when forwarding, or provide a configuration option to control this behavior.

### #1910: Binary body data transformed when forwarding

**Status**: Confirmed bug — P1
**Root cause**: `BodyDecoderEncoder.bytesToBody()` treats unknown/blank content types as string (via `MediaType.isString()` returning true for blank), converting binary data through `new String(bytes, charset)` which corrupts it.
**Files**: `mockserver-core/.../codec/BodyDecoderEncoder.java:81-108`, `mockserver-core/.../model/MediaType.java:287-303`
**Fix**: Change `MediaType.isString()` to return `false` for blank/unknown content types. Default to `BinaryBody` for unknown types instead of `StringBody`.

### #1875: Spurious duplicate Set-Cookie header in forwarded response

**Status**: Confirmed bug — P2
**Root cause**: Cookies are stored redundantly in both `headers` and `cookies` map. The decode path populates both, and the encode path writes both back. The deduplication in `cookieHeaderDoesNotAlreadyExists()` compares name+value but the re-encoded cookie lacks attributes (Path, Domain, etc.), making it look different from the original header.
**Files**: `mockserver-core/.../mappers/FullHttpResponseToMockServerHttpResponse.java:65-100`, `mockserver-core/.../mappers/MockServerHttpResponseToFullHttpResponse.java:95-146`, `mockserver-core/.../model/HttpResponse.java:503-512`
**Fix**: Either skip re-encoding cookies that already exist in headers, or don't parse `Set-Cookie` headers into the cookies map during forwarding.

### #1933 / #1803: HTTP/2 hangs or stream reset exceptions

**Status**: Confirmed bug — P1 (but requires major work)
**Duplicates**: #1803 is related to #1933
**Root cause**: HTTP/2 proxying is fundamentally not implemented. `HttpForwardAction` forces all forwarded requests to HTTP/1.1 (with a `TODO` comment). The `Http2SettingsHandler` can hang if settings are never received. Server-side HTTP/2 uses the older `InboundHttp2ToHttpAdapter` instead of `Http2MultiplexHandler` (another `TODO`).
**Files**: `mockserver-core/.../mock/action/http/HttpForwardAction.java:36-37`, `mockserver-core/.../httpclient/Http2SettingsHandler.java:27-49`, `mockserver-netty/.../proxy/relay/RelayConnectHandler.java:79-80`, `mockserver-netty/.../unification/PortUnificationHandler.java:274`
**Fix**: Implementing full HTTP/2 proxy support is a significant effort. Short-term: add a timeout to `Http2SettingsHandler` future to prevent indefinite hangs. Document that HTTP/2 proxying is not supported.
**Documentation**: Add a clear note that HTTP/2 proxying is not supported and requests are downgraded to HTTP/1.1.

### #1473: Invalid `content-encoding: .*` added to request header

**Status**: Confirmed bug — P2
**Root cause**: The `Accept-Encoding` header is unconditionally replaced with `gzip,deflate` on all forwarded requests, regardless of what the client originally sent. The `.*` pattern may come from a matcher value being serialized as a header.
**Files**: `mockserver-core/.../mappers/MockServerHttpRequestToFullHttpRequest.java:112,126`
**Fix**: Preserve the original `Accept-Encoding` header from the client request instead of overriding it.

### #1733: Content-Length added to requests that didn't have it

**Status**: Confirmed bug — P2
**Root cause**: `MockServerHttpRequestToFullHttpRequest` always sets `Content-Length` (line 135), even for GET requests without a body. This adds `Content-Length: 0` which some servers reject.
**Files**: `mockserver-core/.../mappers/MockServerHttpRequestToFullHttpRequest.java:109,135`
**Fix**: Only set `Content-Length` when the request has a non-empty body.

### #1668: Compressed data not forwarded as compressed

**Status**: Confirmed bug — P2
**Root cause**: Netty's `HttpContentDecompressor` decompresses the request body in the inbound pipeline. `PreserveHeadersNettyRemoves` preserves the `Content-Encoding` header, but the body is already decompressed. The forwarded request has `Content-Encoding: gzip` with an uncompressed body.
**Files**: `mockserver-core/.../codec/PreserveHeadersNettyRemoves.java:22-29`, `mockserver-netty/.../unification/PortUnificationHandler.java:300`
**Fix**: Either re-compress the body before forwarding, or remove the `Content-Encoding` header since the body is now decompressed.

### #1766: Authentication settings affect control plane

**Status**: Confirmed bug — P2
**Root cause**: When `proxyAuthenticationUsername` is set, the `potentiallyHttpProxy` check in `HttpActionHandler` can trigger for control plane GET requests (like `/mockserver/status`) if the `Host` header doesn't match local addresses. The control plane auth check in `HttpState.handle()` only applies to PUT requests.
**Files**: `mockserver-core/.../mock/action/http/HttpActionHandler.java:97,204`, `mockserver-core/.../mock/HttpState.java:577-732`
**Fix**: Exempt known control plane paths from proxy authentication, or check control plane paths before the proxy auth check.

### #1890: Respond to HTTP CONNECT

**Status**: Minor bug — P3
**Root cause**: The CONNECT response uses an empty `HttpResponse` with no explicit status code or reason phrase. While it defaults to 200, it doesn't include the conventional "Connection Established" reason phrase.
**Files**: `mockserver-netty/.../proxy/connect/HttpConnectHandler.java:37-39`
**Fix**: Set explicit `200 Connection Established` in the CONNECT response.

---

## Category 3: TLS & SSL (P1-P2)

### #1837: Client doesn't support TLS 1.3

**Status**: Confirmed bug — P2
**Root cause**: Default TLS protocols are `"TLSv1,TLSv1.1,TLSv1.2"` — TLSv1.3 is absent. Also includes deprecated TLSv1 and TLSv1.1.
**Files**: `mockserver-core/.../configuration/ConfigurationProperties.java:1378`
**Fix**: Update default to `"TLSv1.2,TLSv1.3"`. Drop TLSv1 and TLSv1.1 from defaults.
**Documentation**: Document the `MOCKSERVER_TLS_PROTOCOLS` / `mockserver.tlsProtocols` property more prominently. Clarify how to enable TLS 1.3.

### #1833: Failed to initialize server-side SSL context

**Status**: Confirmed bug — P2
**Root cause**: In `NettySslContextFactory`, the server SSL context is built twice (lines 214-217) — the first build is discarded. Exceptions during server SSL context creation are caught and only logged (not rethrown), returning `null` which causes NPEs downstream. The client SSL context correctly rethrows.
**Files**: `mockserver-core/.../socket/tls/NettySslContextFactory.java:180-229`
**Fix**: Remove the redundant first `build()`. Rethrow exceptions for server SSL context creation (matching client behavior).

### #1739: `javax.security.cert.CertificateException` under JDK 17

**Status**: Known issue — P2
**Root cause**: JDK 17 restricts access to `com.sun.security.cert.internal.x509.X509V1CertImpl`. MockServer's BouncyCastle-based certificate handling may trigger this when older code paths are used.
**Fix**: The project already uses BouncyCastle 1.84, which should work with JDK 17. This may be resolved in the current codebase. Needs verification with JDK 17 test run.

---

## Category 4: OpenAPI & JSON Schema (P2)

### #1896: Wrong JSON schema dialect for OpenAPI specs

**Status**: Confirmed bug — P2
**Root cause**: MockServer defaults to JSON Schema draft-07 (with a comment about TLS issues downloading draft-2019-09). OpenAPI 3.0.x uses a modified subset of draft-05, and OpenAPI 3.1.x uses draft-2020-12. Neither matches draft-07. OpenAPI schemas extracted from operations don't have a `$schema` property, so the validator always falls back to draft-07.
**Files**: `mockserver-core/.../validator/jsonschema/JsonSchemaValidator.java:38-39,80-103`
**Fix**: Detect the OpenAPI version from the spec and use the appropriate JSON Schema dialect. For OpenAPI 3.0.x, handle `nullable` as a keyword. For OpenAPI 3.1.x, use draft-2020-12.

### #1806: Only 1 expectation per operation even with multiple responses

**Status**: Confirmed bug — P2
**Root cause**: `OpenAPIConverter.buildExpectations()` uses `.map()` to create exactly one `Expectation` per operation, and `buildHttpResponse()` uses `.findFirst()` to select only the first response entry.
**Files**: `mockserver-core/.../openapi/OpenAPIConverter.java:38-63,65-69`
**Fix**: Use `flatMap` across both operations and responses to produce one expectation per response code per operation.

### #1315: Security override in operation not working

**Status**: Confirmed bug — P2
**Root cause**: Both global security AND operation-level security are applied cumulatively. Per the OpenAPI specification, operation-level security should override (replace) global security, not add to it.
**Files**: `mockserver-core/.../matchers/HttpRequestsPropertiesMatcher.java:210-215`
**Fix**: If `operation.getSecurity() != null`, use only the operation-level security; otherwise use the global security.

### #1776: Unable to match path params with OpenAPI expectations

**Status**: Partially confirmed — P2
**Root cause**: Path parameter extraction in `PathParametersDecoder` throws `IllegalArgumentException` when path segment counts differ. Edge cases with trailing slashes, encoded characters, or complex path styles may fail.
**Files**: `mockserver-core/.../codec/PathParametersDecoder.java:69-91`
**Fix**: Make path segment matching more lenient. Handle trailing slashes and encoded characters.

### #1839: SEVERE exception validating JSON for pathparameters

**Status**: Confirmed bug — P2
**Root cause**: For non-string schema types (e.g., `type: integer`), path parameter values are not quoted before JSON schema validation. The raw string value is passed directly to the validator, which fails on non-numeric text.
**Files**: `mockserver-core/.../model/NottableSchemaString.java:163-191`
**Fix**: Always wrap path parameter values appropriately for their expected schema type before validation. Add better error handling to produce meaningful messages instead of SEVERE exceptions.

### #1852: jsonschema allOf not managed

**Status**: Likely works but untested — P3
**Root cause**: The networknt json-schema-validator v1.0.77 with draft-07 does support `allOf`. The swagger-parser's `resolveCombinators=true` tries to flatten `allOf`. The issue may be with complex cases where flattening fails. There are zero test cases for `allOf`.
**Fix**: Add comprehensive test coverage for `allOf`, `oneOf`, `anyOf` in OpenAPI schemas.

### #1474: Reusable `$ref` examples not converted

**Status**: Confirmed bug — P2
**Root cause**: Swagger-parser has known bugs with example `$ref` resolution. Even with `resolveFully=true`, some `$ref` in `components/examples` may not be resolved, leaving `Example.getValue()` as null.
**Files**: `mockserver-core/.../openapi/OpenAPIConverter.java:106-117,149-167`
**Fix**: Add fallback logic to manually resolve example `$ref` when swagger-parser fails. Check if upgrading swagger-parser (currently 2.1.22) fixes the resolution.

### #1788: Missing byte format string property in example generation

**Status**: Confirmed bug — P2
**Root cause**: `ExampleBuilder.fromProperty()` has no handler for `ByteArraySchema` (OpenAPI `type: string, format: byte`). The method returns null for byte-format properties.
**Files**: `mockserver-core/.../openapi/examples/ExampleBuilder.java`
**Fix**: Add a `ByteArraySchema` handler that returns a base64-encoded sample string.

### #1940: Header expectation from OpenAPI URL failing for String type

**Status**: Needs investigation — P3
**Root cause**: No test cases exist for OpenAPI specs loaded from URLs with header matching. Schema serialization may produce different output when loaded from URLs due to `$ref` resolution differences.
**Fix**: Add URL-based OpenAPI test cases. Investigate schema serialization differences.

### #1793: OpenAPI with JSON spec containing comments fails

**Status**: Confirmed bug — P3
**Root cause**: JSON does not support comments. If users have JSON with comments (JSONC), the parser will fail. YAML specs support comments natively.
**Fix**: Consider using a lenient JSON parser that strips comments, or document that JSON specs must not contain comments.
**Documentation**: Clarify that JSON OpenAPI specs must be valid JSON without comments. Recommend YAML for specs that need comments.

---

## Category 5: Matchers & Verification (P1-P2)

### #1974: NottableString NOT does not work correctly with matches

**Status**: Confirmed bug — P1
**Root cause**: The `SubSetMatcher.nottedAndPresent()` only checks `matcherItem.getKey().isNot()` and does not consider the value's `isNot()` flag. The interaction between key negation and value negation across `SubSetMatcher`, `RegexStringMatcher`, and `NottableString.equals()` creates subtle bugs where negated matchers match too broadly.
**Files**: `mockserver-core/.../collections/SubSetMatcher.java:60-71`, `mockserver-core/.../matchers/RegexStringMatcher.java:62-69`
**Fix**: Review and simplify the negation logic. `nottedAndPresent()` should consider value negation. Add comprehensive test cases for negated keys, negated values, and both negated.

### #1639: Negated value matcher doesn't work for missing values

**Status**: Confirmed bug — P1
**Root cause**: `SubSetMatcher` requires key presence even for negated values. When a user specifies "header X should NOT have value Y", the match fails if header X is absent entirely — even though absence satisfies "not having value Y".
**Files**: `mockserver-core/.../collections/SubSetMatcher.java:16-36`
**Fix**: When the value is negated and the key is absent from the superset, treat it as a match (the absent key trivially satisfies "not having this value").

### #1870: JSON String body double quotes ignored in verification

**Status**: Confirmed bug — P2
**Root cause**: `BodyDTODeserializer` creates `StringBodyDTO` for plain JSON string values (even when the string content is valid JSON). This means the body is matched as a plain string rather than using JSON semantic comparison.
**Files**: `mockserver-core/.../serialization/deserializers/body/BodyDTODeserializer.java:320-321`
**Fix**: When a string body value looks like valid JSON, allow users to explicitly specify `type: JSON` to force JSON semantic matching. Consider auto-detecting JSON content.

### #1866: Repeated query parameters with same value missing

**Status**: Confirmed bug — P2
**Root cause**: `KeysToMultiValues` uses Guava's `LinkedHashMultimap` which deduplicates key-value pairs. `?a=1&a=1` is stored as `a=[1]` instead of `a=[1,1]`.
**Files**: `mockserver-core/.../model/KeysToMultiValues.java:23`
**Fix**: Switch from `LinkedHashMultimap` to `LinkedListMultimap` or `ArrayListMultimap` which allow duplicate key-value pairs.

### #1888: Encoded question mark missing from query parameter value

**Status**: Probable bug — P3
**Root cause**: Query string splitting uses `substringAfter(parameterString, "?")` which may incorrectly split if a decoded parameter value contains `?`.
**Files**: `mockserver-core/.../codec/ExpandedParameterDecoder.java:62`
**Fix**: Use the raw (encoded) URI for query string splitting, not the decoded version.

### #1757: MockServerClient.verify false positive

**Status**: Confirmed bug — P2
**Root cause**: `LogEntry.matches()` returns true for log entries with null/empty HTTP requests, potentially inflating match counts during verification.
**Files**: `mockserver-core/.../log/model/LogEntry.java:193-206`
**Fix**: `LogEntry.matches()` should return false when `httpRequests` is null or empty, not true.

### #1789: Incorrect verify response text when atLeast is not met

**Status**: Confirmed bug — P3
**Root cause**: The verification failure message doesn't include the actual count of matched requests. It says "Request not found at least N times" without saying how many were found.
**Files**: `mockserver-core/.../log/MockServerEventLog.java:493-517`
**Fix**: Include the actual match count in the failure message: "Request found M times but expected at least N times".

### #1734: retrieveRecordedRequests doesn't return all requests with body matcher

**Status**: Probable user confusion — P3
**Root cause**: When users specify a body as a plain string (not explicitly `json()`), it creates an `ExactStringMatcher` requiring exact character-by-character match. Users expect semantic comparison.
**Documentation**: Clarify that `withBody("json string")` uses exact string matching. Users should use `withBody(json("..."))` for semantic JSON matching in retrieval filters.

### #1524: verify not working correctly after update to 5.14.0

**Status**: Needs more information — P3
**Root cause**: Likely related to async Disruptor processing. Log entry retrieval is asynchronous, so verification called immediately after sending requests may not see all entries. Also, the matcher cache in `MatcherBuilder` could return stale matchers.
**Fix**: Consider adding a small synchronization mechanism or document that verification may need a brief delay after the last request.

### #1505: Regexp matcher illegal repetition

**Status**: Confirmed bug — P2
**Root cause**: Values containing `{` or `}` (common in JSON, URL paths like `/api/{id}`) cause `PatternSyntaxException` when the matcher tries to compile them as regex. The exception is silently caught at DEBUG level.
**Files**: `mockserver-core/.../model/NottableString.java:212-217`
**Fix**: Attempt exact string matching first (which already happens), but improve logging to explain why the regex attempt failed. Consider escaping regex metacharacters when the value is clearly not intended as a regex.
**Documentation**: Document that path patterns like `/api/{id}` need regex escaping or should use `regex("/api/[^/]+")` explicitly.

### #1496: ONLY_MATCHING_FIELDS failing where STRICT is not

**Status**: Confirmed bug — P2
**Root cause**: The combination of `IGNORING_ARRAY_ORDER` + `IGNORING_EXTRA_ARRAY_ITEMS` in ONLY_MATCHING_FIELDS mode can cause JsonUnit's subset matching to fail differently than strict comparison, especially for arrays of objects with partial matches.
**Files**: `mockserver-core/.../matchers/JsonStringMatcher.java:52-57`
**Fix**: Investigate if upgrading JsonUnit (within 2.x series) fixes the subset matching edge cases. Add targeted test cases.

### #1740: Float number decimal places removed

**Status**: Confirmed bug — P3
**Root cause**: Jackson's `readTree()` converts `1.10` to `1.1` because trailing zeros are not preserved in floating-point representation. The ObjectMapper does not enable `USE_BIG_DECIMAL_FOR_FLOATS`.
**Files**: `mockserver-core/.../serialization/ObjectMapperFactory.java`, `mockserver-core/.../serialization/serializers/body/JsonBodySerializer.java`
**Fix**: Enable `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS` and `JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN` in the ObjectMapper. Requires testing for broader impact.

### #1829: Body did not match for XML request

**Status**: Probable bug — P2
**Root cause**: XML matching uses `ElementSelectors.byName` which requires elements at the same position. Different element ordering or namespace prefixes cause matches to fail. The `checkForSimilar()` mode does not ignore element order. Additionally, the `DiffBuilder` reuse creates thread-safety issues (see #1796).
**Fix**: Consider adding an option for order-independent XML matching. Fix the thread-safety issue (see #1796). Document that XML matching requires same element ordering.

### #1893: Upgrading from 5.11.2 to 5.13.0+ fails to load JSON expectations

**Status**: Confirmed bug — P2
**Root cause**: Schema validation in `ExpectationSerializer.deserialize()` runs before parsing. If the schema changed between versions (new required fields, stricter validation), previously valid JSON fails validation and throws `IllegalArgumentException` for the entire array.
**Files**: `mockserver-core/.../serialization/ExpectationSerializer.java:127-148`, `mockserver-core/.../serialization/deserializers/body/BodyDTODeserializer.java`
**Fix**: Make schema validation more lenient for backward compatibility. Consider a version migration path. At minimum, validate individual expectations rather than failing the entire array.
**Documentation**: Provide a migration guide for expectations format changes between versions.

---

## Category 6: Mustache & Velocity Templates (P2)

### #1911: Mustache template unable to access `body.<field>`

**Status**: Confirmed bug — P2
**Root cause**: `HttpRequestTemplateObject.getBody()` returns a `String` representation of the body. Mustache's dot notation (`{{ request.body.field }}`) cannot traverse into a string.
**Files**: `mockserver-core/.../templates/engine/model/HttpRequestTemplateObject.java:46,80-82`
**Fix**: Parse JSON bodies into a `Map<String, Object>` and expose it alongside the string representation, enabling `{{ request.body.fieldName }}` access.
**Documentation**: Currently users must use `{{#jsonPath}}$.fieldName{{/jsonPath}}` as a workaround. Document this prominently.

### #1840: No multi-line MUSTACHE templates in initializerJson.json

**Status**: Not a bug — JSON limitation
**Root cause**: JSON does not support multi-line strings. Newlines must be escaped as `\n`. This is a fundamental JSON format limitation, not a MockServer bug.
**Documentation**: Document that JSON initializer files require `\n` for newlines in template strings. Recommend using class-based initializers (`ExpectationInitializer`) for complex templates. Consider supporting YAML initializer files as an alternative.

---

## Category 7: Spring, JUnit & Client Integration (P2)

### #1602 / ~~#1828~~: Spring Boot 3 support

**Status**: Blocked by design — Feature
**Duplicates**: #1828 closed as duplicate of #1602
**Root cause**: MockServer targets Java 11 minimum with Spring 5.x and `javax.servlet`. Spring Boot 3 requires Spring 6, Jakarta EE 9+, and Java 17+. This is a deliberate compatibility decision.
**Documentation**: Document explicitly that MockServer does not support Spring Boot 3 / Spring 6 and explain why (Java 11 compatibility target). List compatible Spring Boot versions (2.x).

### #1860: Eliminate javax.servlet-api

**Status**: Blocked by design — Feature
**Root cause**: `javax.servlet` is used in 5 files in `mockserver-core` and 2 WAR module servlets. The WAR modules (`mockserver-war`, `mockserver-proxy-war`) directly extend `javax.servlet.http.HttpServlet`. Removal would require either abstracting the servlet layer or maintaining dual `javax`/`jakarta` implementations.
**Fix**: This is part of the broader Java 17+ / Spring 6 migration. Cannot be addressed independently without breaking the WAR modules.

### #1979: Spring TestExecutionListener doesn't work with nested classes

**Status**: Confirmed bug — P2
**Root cause**: `findMockServerFields()` only traverses the superclass hierarchy via `getSuperclass()`, not enclosing classes. For JUnit 5 `@Nested` inner classes, fields on the outer class are not found. `@Inherited` doesn't work for nested classes.
**Files**: `mockserver-spring-test-listener/.../springtest/MockServerTestExecutionListener.java:46-57,80`
**Fix**: Also traverse `classToCheck.getEnclosingClass()` in `findMockServerFields()`. For `@Nested` classes, check if the enclosing class has `@MockServerTest`.

### #1977: MockServer starting twice due to @MockServerSettings

**Status**: Confirmed bug — P2
**Root cause**: `@MockServerSettings` is meta-annotated with `@ExtendWith(MockServerExtension.class)`. If a user also adds `@ExtendWith(MockServerExtension.class)` on their test class, JUnit 5 registers two extension instances, each starting its own MockServer.
**Files**: `mockserver-junit-jupiter/.../junit/jupiter/MockServerSettings.java:12`, `mockserver-junit-jupiter/.../junit/jupiter/MockServerExtension.java`
**Fix**: Add de-duplication logic in `MockServerExtension.beforeAll()` to check if a static instance already exists. Or document clearly that `@MockServerSettings` already includes the extension — do not also add `@ExtendWith`.
**Documentation**: Add a prominent warning that `@MockServerSettings` includes `@ExtendWith(MockServerExtension.class)` implicitly.

### #1621: Injected MockServerClient instance is null

**Status**: Missing feature — P2
**Root cause**: `MockServerExtension` (JUnit 5) implements `ParameterResolver` but NOT `TestInstancePostProcessor`. Field injection is not supported — only constructor/method parameter injection works.
**Files**: `mockserver-junit-jupiter/.../junit/jupiter/MockServerExtension.java`
**Fix**: Implement `TestInstancePostProcessor` in `MockServerExtension` to inject `MockServerClient` fields, similar to how `MockServerRule` (JUnit 4) does field injection.
**Documentation**: Document that only parameter injection works in JUnit 5, not field injection. Show both constructor and method parameter injection examples.

### #1554: InitializationClass on @MockServerTest

**Status**: Missing feature — P3
**Root cause**: Neither `@MockServerTest` nor `@MockServerSettings` exposes an `initializationClass` attribute. Users must set `mockserver.initializationClass` via system property separately.
**Fix**: Add an `initializationClass` attribute to both `@MockServerTest` and `@MockServerSettings`.

### #1710: Ability to configure @MockServerClient

**Status**: Missing feature — P3
**Root cause**: There is no `@MockServerClient` annotation. Users wanting to connect to an external MockServer have no declarative annotation option.
**Fix**: Create a `@MockServerClient` annotation with host, port, and contextPath attributes for use with `MockServerExtension`.

### #1273: NoSuchMethodError starting mockserver

**Status**: Version compatibility issue — P2
**Root cause**: Mixing MockServer versions or including both regular and shaded JARs causes method signature mismatches. The extensive shading (Netty, Jackson, Guava, etc.) means that classpath conflicts can produce `NoSuchMethodError`.
**Documentation**: Add a troubleshooting section for `NoSuchMethodError` explaining common causes: version mismatches, duplicate JARs, shaded vs non-shaded conflicts. Recommend using only one MockServer artifact per project.

### #1375: TypeError in browser client

**Status**: Out of scope
**Root cause**: The `mockserver-client-node` npm package lives in a separate repository (`github.com/mock-server/mockserver-client-node`), not in this codebase.
**Fix**: Close with reference to the correct repository.

---

## Category 8: Docker & Container (P2)

### #1956: Docker image contains critical CVEs from outdated Debian base

**Status**: Confirmed issue — P1
**Root cause**: Base image tags (`gcr.io/distroless/java17:nonroot`) are unpinned (no digest). The `netty-tcnative-boringssl-static` version is hardcoded to `2.0.50.Final`/`2.0.56.Final` in Dockerfiles while the POM declares `2.0.75.Final`.
**Files**: `docker/Dockerfile:23,33,39`, `docker/root/Dockerfile`
**Fix**: Pin base images with digest. Update `netty-tcnative-boringssl-static` in Dockerfiles to match POM version (2.0.75.Final). Add automated base image update process.

### #1895 / ~~#1751~~: No docker-compose healthcheck

**Status**: Confirmed issue — P2
**Duplicates**: #1751 closed as duplicate of #1895
**Root cause**: No `HEALTHCHECK` in any Dockerfile. Distroless images have no curl/wget. The status endpoint requires PUT (incompatible with simple healthchecks). `MOCKSERVER_LIVENESS_HTTP_GET_PATH` exists but is disabled by default.
**Files**: `docker/Dockerfile`, `mockserver-core/.../configuration/ConfigurationProperties.java:1171-1188`
**Fix**: Add a `HEALTHCHECK` instruction using Java-based health check (since no shell is available). Enable `MOCKSERVER_LIVENESS_HTTP_GET_PATH=/liveness` by default in Docker images. Add `HEALTHCHECK CMD ["java", "-cp", "...", "HealthCheck"]` or similar.
**Documentation**: Document how to configure healthchecks with `MOCKSERVER_LIVENESS_HTTP_GET_PATH`.

### #1868 / #1887: Docker on ARM (Raspberry Pi) / s390x

**Status**: Confirmed bug — P2
**Duplicates**: #1887 is related to #1868 (different architecture)
**Root cause**: Dockerfiles hardcode `netty-tcnative-boringssl-static` for `linux-x86_64` architecture. The `.so` file is always `libnetty_tcnative_linux_x86_64.so` regardless of target platform. s390x is not in the CI platform list.
**Files**: `docker/Dockerfile:23,49`, `.github/workflows/build-docker-image.yml:46,55`
**Fix**: Use Docker `TARGETARCH` build arg to select the correct native library per platform. Add s390x to CI platforms if netty-tcnative supports it.

### #1593: Add shell to Docker container

**Status**: By design — P3
**Root cause**: Distroless images intentionally exclude shells for security. Only the `debug-nonroot` snapshot image has busybox.
**Documentation**: Document that the debug image (`mockserver/mockserver:snapshot`) includes a shell. For production debugging, recommend `kubectl debug` or similar ephemeral container approaches.

### #1857: CVE-2023-4911 in Docker image

**Status**: Likely resolved (base image update) — check
**Root cause**: glibc vulnerability in the base image. Distroless images are rebuilt periodically by Google.
**Fix**: Verify current base image is not affected. Pin base image digest to a known-good version.

---

## Category 9: Helm Chart (P2-P3)

### #1864 / ~~#1681~~: imagePullSecrets in Helm chart

**Status**: Confirmed missing — P2
**Duplicates**: #1681 closed as duplicate of #1864
**Root cause**: `imagePullSecrets` is completely absent from `deployment.yaml` and `values.yaml`.
**Files**: `helm/mockserver/templates/deployment.yaml`, `helm/mockserver/values.yaml`
**Fix**: Add `imagePullSecrets` support to values.yaml and deployment.yaml template.

### #1884: Custom labels for pod network policies

**Status**: Confirmed missing — P3
**Root cause**: Pod labels are hardcoded to `app` and `release`. No `podLabels` value exists. No NetworkPolicy template.
**Fix**: Add `podLabels` to values.yaml and deployment.yaml. Consider adding an optional NetworkPolicy template.

### #1779: PVC support in Helm chart

**Status**: Confirmed missing — P3
**Root cause**: No PVC template, no `persistence` section in values.yaml. Users cannot persist expectations across restarts.
**Fix**: Add a PVC template and `persistence` values section. Wire it to the expectations persistence path.

### #1752: Chart names not well formed as dependency chart

**Status**: Confirmed bug — P2
**Root cause**: Chart uses `apiVersion: v1` (Helm 2). `release.name` doesn't incorporate the chart name, causing collisions when used as a sub-chart. `nameOverride` is referenced in `_helpers.tpl` but not defined in `values.yaml`.
**Files**: `helm/mockserver/Chart.yaml`, `helm/mockserver/templates/_helpers.tpl`
**Fix**: Update to `apiVersion: v2`. Use the standard `{{ .Release.Name }}-{{ .Chart.Name }}` fullname pattern. Define `nameOverride` and `fullnameOverride` in values.yaml.

### #1600: Container HTTP check timeout on startup

**Status**: Confirmed issue — P3
**Root cause**: No `startupProbe`. Probes use `tcpSocket` (not HTTP). Probe parameters are hardcoded with no values overrides.
**Files**: `helm/mockserver/templates/deployment.yaml:47-60`
**Fix**: Add a `startupProbe` with longer timeout. Set `MOCKSERVER_LIVENESS_HTTP_GET_PATH` and use `httpGet` probes. Make probe parameters configurable via values.yaml.

---

## Category 10: Configuration & Initialization (P2-P3)

### #1885: Configuration ignored

**Status**: Confirmed bug — P2
**Root cause**: `readPropertyHierarchically()` uses a `propertyCache` that returns the first-read value forever. Properties files and environment variables are only read once at class load time. Changes after initialization are never picked up.
**Files**: `mockserver-core/.../configuration/ConfigurationProperties.java:1837-1853`
**Fix**: Either remove the cache, add a TTL, or provide a `reload()` method. Document the initialization-time-only behavior.
**Documentation**: Document that configuration properties are read once at startup and cannot be changed at runtime via files/env vars (only via API/programmatic calls).

### #1715: Multiple initialization JSON files in different directories

**Status**: Limitation — P3
**Root cause**: `initializationJsonPath` accepts a single path/glob pattern. There's no separator for specifying multiple independent directories.
**Fix**: Support semicolon-separated paths (e.g., `/dir1/*.json;/dir2/*.json`). Or document workarounds using glob patterns with common parent directories.

### #1571: ClassNotFoundException for callback class

**Status**: Confirmed bug — P2
**Root cause**: Callback handlers use the system classloader or the handler's classloader, not `Thread.currentThread().getContextClassLoader()`. In Spring Boot fat JARs or application servers with custom classloader hierarchies, callback classes may not be found.
**Files**: `mockserver-core/.../mock/action/http/HttpResponseClassCallbackActionHandler.java:40`, `mockserver-core/.../mock/action/http/HttpForwardClassCallbackActionHandler.java:32`
**Fix**: Try `Thread.currentThread().getContextClassLoader()` first, then fall back to system classloader. Apply consistently across all callback handlers.

### #1636: mockWithCallback doesn't work on 5.15.0

**Status**: Probable configuration issue — P3
**Root cause**: The callback handler creates two instances and logs warnings for the interface that doesn't match. If the action type (RESPONSE vs FORWARD callback) doesn't match the interface the class implements, the callback silently doesn't execute.
**Documentation**: Clarify the distinction between response callbacks and forward callbacks in documentation.

### #1634: Can't load log handler StandardOutConsoleHandler

**Status**: Confirmed bug — P3
**Root cause**: JUL's `LogManager` uses its own classloader to load handler classes. In application servers or custom classloader setups, `StandardOutConsoleHandler` may not be found. Users can work around by setting `java.util.logging.config.file`.
**Documentation**: Document the workaround of providing a custom `logging.properties` file.

### #1660: mock-server-no-dependencies includes JDK14LoggerAdapter

**Status**: Minor issue — P3
**Root cause**: SLF4J is deliberately not relocated in the shaded JAR. The `slf4j-jdk14` binding classes in `org.slf4j.impl` should be excluded by the shade filter, but SLF4J version differences may place classes in different packages.
**Fix**: Verify the shade filter correctly excludes all SLF4J binding classes. Ensure no SLF4J binding is shipped in the no-dependencies JAR.

### #1659: StackOverflow with @JsonTest and 5.15.0

**Status**: Confirmed bug — P2
**Root cause**: MockServer's custom Jackson serializers have `static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper()` fields. When Spring Boot's `@JsonTest` auto-configures Jackson and triggers class loading of MockServer serializers, the static initializers recursively call `ObjectMapperFactory.createObjectMapper()`, potentially causing a stack overflow or configuration loop.
**Fix**: Break the circular static initialization. Use lazy initialization for the ObjectMapper in serializers, or use a shared singleton that doesn't trigger recursive initialization.

### #1693: UI websocket returns 404 with path prefix

**Status**: Confirmed bug — P2
**Root cause**: WebSocket URIs (`/_mockserver_ui_websocket`, `/_mockserver_callback_websocket`) are hardcoded without respecting any path prefix. The HTTP API uses `PATH_PREFIX` but WebSocket handlers do not.
**Files**: `mockserver-netty/.../dashboard/DashboardWebSocketHandler.java:60,128`, `mockserver-netty/.../websocketregistry/CallbackWebSocketServerHandler.java:34,48`
**Fix**: Prepend `PATH_PREFIX` to WebSocket URI checks, or accept both prefixed and non-prefixed URIs (matching how the HTTP API works).

### #1823: Dashboard logs not showing over HTTP

**Status**: Related to #1693 — P3
**Root cause**: Dashboard relies entirely on WebSocket for log updates. If the WebSocket connection fails (due to path prefix issues, TLS-terminating proxies, or HTTP/1.0), no logs appear. There is no HTTP polling fallback.
**Fix**: Fix the WebSocket path prefix issue (#1693). Consider adding an HTTP polling fallback for environments where WebSocket is not available. Fix the TLS detection for `ws://` vs `wss://` URL construction when behind a TLS-terminating proxy.

### #1478: attemptToProxyIfNoMatchingExpectation not working

**Status**: Configuration issue — P3
**Root cause**: The `potentiallyHttpProxy` check requires the `Host` header to NOT match any local address. When testing locally, `localhost` is in the local addresses set, so the proxy attempt is skipped. Additionally, the proxy timeout is only 1 second.
**Documentation**: Document that `attemptToProxyIfNoMatchingExpectation` only works when the `Host` header differs from local addresses. Document the 1-second timeout limitation.

---

## Category 11: Memory & Performance (P2)

### #1847: Memory leak from reset not removing subscribers

**Status**: Confirmed bug — P2
**Root cause**: `HttpState.reset()` clears matchers and log entries but does NOT clear listeners from `MockServerMatcherNotifier.listeners` and `MockServerEventLogNotifier.listeners`. `MemoryMonitoring` registers but never unregisters.
**Files**: `mockserver-core/.../mock/HttpState.java:216-244`, `mockserver-core/.../mock/listeners/MockServerMatcherNotifier.java`, `mockserver-core/.../memory/MemoryMonitoring.java`
**Fix**: Clear listener lists during reset, or use `WeakReference` for listener registration.

### #1874: Expectation listed as active after timeToLive expired

**Status**: Confirmed bug — P2
**Root cause**: `retrieveActiveExpectations()` returns ALL expectations without checking `isActive()`. Expired expectations are only removed when a request triggers matching. Without incoming requests, expired expectations persist in the list indefinitely.
**Files**: `mockserver-core/.../mock/RequestMatchers.java:372-385`
**Fix**: Add `isActive()` filter in `retrieveActiveExpectations()`. Consider adding a periodic cleanup task.

### #1741: OutOfMemoryError Java heap space

**Status**: Configuration/design issue — P2
**Root cause**: Dynamic defaults for `maxExpectations` and `maxLogEntries` are based on available heap at startup, which fluctuates. The Disruptor ring buffer pre-allocates `nextPowerOfTwo(maxLogEntries)` entries. `CircularPriorityQueue` uses three data structures per expectation, tripling memory usage.
**Files**: `mockserver-core/.../configuration/ConfigurationProperties.java:357-415`, `mockserver-core/.../collections/CircularPriorityQueue.java`
**Fix**: Set more conservative defaults. Document memory tuning with `MOCKSERVER_MAX_EXPECTATIONS` and `MOCKSERVER_MAX_LOG_ENTRIES`. Consider reducing `CircularPriorityQueue` to a single data structure.
**Documentation**: Add a memory tuning guide with recommended values for different heap sizes.

### #1285: Max log entries capped too low

**Status**: Configuration issue — P3
**Root cause**: Default `maxLogEntries` cap is 60,000. On small containers, the formula `heapAvailableInKB() / 80` can produce very low values (e.g., 460 entries for a 256MB container).
**Files**: `mockserver-core/.../configuration/ConfigurationProperties.java:381-396`
**Fix**: Increase the minimum floor for `maxLogEntries` (e.g., minimum 1000). Document how to override with `MOCKSERVER_MAX_LOG_ENTRIES`.

---

## Category 12: Dependency & CVE Reports

### Closed CVE Issues (dependencies already updated)

The following issues were closed because the referenced dependencies have been updated in the development branch. Fixes will ship in the next release.

| Issue | CVE / Dependency | Old Version | Current Version | Status |
|-------|-----------------|-------------|-----------------|--------|
| ~~#1981~~ | Multiple (Netty, BouncyCastle, etc.) | Various | See below | **Closed** |
| ~~#1915~~ | Generic security scan findings | 5.15.0 deps | All updated | **Closed** |
| ~~#1894~~ | SnakeYAML CVE-2022-1471 | 1.33 | 2.0 | **Closed** |
| ~~#1873~~ | Netty CVE-2023-44487 | 4.1.86.Final | 4.1.132.Final | **Closed** |
| ~~#1857~~ | glibc CVE-2023-4911 | Docker tag 0.15.0 | distroless base | **Closed** |
| ~~#1812~~ | Netty JDK internal APIs | 4.1.89.Final | 4.1.132.Final | **Closed** |
| ~~#1781~~ | Guava CVE-2023-2976 | 31.1-jre | 32.0.0-jre | **Closed** |
| ~~#1532~~ | commons-text CVE-2022-42889 | < 1.10.0 | 1.10.0 | **Closed** |
| ~~#1934~~ | commons-lang3 outdated | 3.12.0 | 3.18.0 | **Closed** |

### Remaining Dependency Issues

| Dependency | Current Version | Issue | Status |
|-----------|----------------|-------|--------|
| Jackson | 2.14.2 | (no open issue) | Needs update to 2.17+ |
| commons-collections | 3.2.2 | #1822 | Transitive from velocity-tools — needs investigation |
| json-schema-validator | 1.0.77 | #1966 | Needs careful upgrade (breaking API changes) |
| swagger-parser | 2.1.22 | — | Updated |
| BouncyCastle | 1.84 | — | Updated |

**Fix**: Remaining work is to update Jackson 2.14.2 → 2.17+, address `commons-collections:3.2.2` (transitive from velocity-tools), and carefully upgrade json-schema-validator (#1966).

### #1970: Upgrade to Jackson 3.x

**Status**: Not feasible — blocked by Java 11 target
**Root cause**: Jackson 3.x requires Java 17+ and renames `com.fasterxml.jackson` to `tools.jackson`. This conflicts with the Java 11 minimum.
**Fix**: Cannot upgrade until Java 11 support is dropped. Close with explanation.

### ~~#1971~~: Update ByteBuddy for Java 25

**Status**: Closed — not applicable
**Root cause**: ByteBuddy is NOT a direct dependency — it's only a transitive test dependency via Mockito 4.11.0.

### #1966 / ~~#1980~~: json-schema-validator upgrade

**Status**: Requires careful migration — P2
**Root cause**: json-schema-validator v2.0.0 has breaking API changes in `JsonSchemaFactory`, validation result handling, and `SpecVersion`.
**Files**: `mockserver-core/.../validator/jsonschema/JsonSchemaValidator.java`
**Fix**: Refactor `JsonSchemaValidator.java` to accommodate the v2.0.0 API. Verify Java 11 compatibility.

### #1813: JSON comparison with JsonUnit 3.0.0+

**Status**: Blocked — P3
**Root cause**: MockServer uses JsonUnit internal APIs (`Diff.create()`, `Configuration`, `Options`). JsonUnit 3.x changed these internals. Also, JsonUnit 3.x may require Java 17+.
**Fix**: Rewrite `JsonStringMatcher` to use only public JsonUnit API. Verify Java 11 compatibility before upgrading.

---

## Category 13: Feature Requests (Enhancement)

These are legitimate feature requests, not bugs. Listed by perceived value.

### High Value

| Issue | Feature | Assessment |
|-------|---------|------------|
| #1936 | gRPC support | Significant effort; Netty already supports HTTP/2 which gRPC uses |
| #1965 | Change request scheme in forward modifier | Small change to `HttpRequestModifier` |
| #1960 | Custom MockServerLogger | Requires interface extraction; medium effort |
| #1937 | Show expectation IDs in dashboard non-match logs | Small UI enhancement |
| #1883 | Publish expectations JSON Schema for IDE support | Extract from internal validator; medium effort |
| #1694 | Make INFO log level less verbose | Review log levels; small effort |
| #1626 | SSE (server-sent events) support | Requires streaming response support |
| #1604 | Connection timeout emulation | Requires Netty pipeline modification |

### Medium Value

| Issue | Feature | Assessment |
|-------|---------|------------|
| #1879 | Enforce matching all query parameters | Add a strict query matching mode |
| #1878 | Ignore specific JSON keys in body matching | Extend JSON matcher with key exclusion |
| #1850 | Extract current expectations via API | **Already exists** — close as resolved |
| #1900 | Create expectations over HTTPS | **Already works** — close as resolved |
| #1782 | Send default Host header for forwarded requests | Related to #1897 |
| #1779 | PVC in Helm chart | See Helm section |
| #1769 | BouncyCastle FIPS support | Already using BouncyCastle; needs FIPS config |
| #1746 | Custom HTTP methods | Check if Netty/MockServer restricts methods |
| #1534 | Publish to AWS ECR | CI/CD configuration change |
| #1520 | Specify which OpenAPI example to return | Extend OpenAPI converter |
| #1519 | Custom response for unmatched failed forward | Add fallback response config |
| #1510 | Adjust log granularity | Configuration enhancement |
| #1509 | Terser DSL | API design enhancement |
| #1494 | Minimize mockserver-client-java dependencies | Reduce transitive dependencies |
| #1483 | OpenAPI spec callbacks | Extend OpenAPI support |
| #1272 | Add configmap to Helm chart | Already exists; verify |
| #1307 | YAML initialization file support | Add alongside JSON |

### Low Value / Niche

| Issue | Feature | Assessment |
|-------|---------|------------|
| #1802 | Allocate multiple ports at once | Small API addition |
| #1798 | Add MockServer Browser Admin to README | Documentation change |
| #1794 | OAuth2/OIDC Resource Server | Significant scope |
| #1785 | Add swagger endpoint to mock server | Medium effort |
| #1765 | Access to RequestMatcher class | API exposure |
| #1754 | Increase max message size with Jackson 2.15 | Configuration change |
| #1753 | Forward incoming request on JEE server | Documentation |
| #1699 | Callback to a webhook | Extend callback mechanism |
| #1688 | p99 response time support | Metrics enhancement |
| #1663 | Set delay range | Extend delay model |
| #1652 | Upload file to mockserver | Multipart support |
| #1650 | Log actual proxy response | Logging enhancement |
| #1646 | Change priority at runtime | Expectation management |
| #1574 | Request forwarding with timings | Metrics enhancement |
| #1567 | Callback with initializer JSON | Configuration enhancement |
| #1345 | Cross-type value comparison | JSON matching enhancement |
| #1333 | Complex parameter path placeholders | Matcher enhancement |
| #1330 | Regex in JSON request body | Matcher enhancement |

---

## Category 14: Already Resolved / Out of Scope (all closed)

| Issue | Reason | Status |
|-------|--------|--------|
| ~~#1850~~ | Extract expectations via API — already exists | **Closed** with API reference |
| ~~#1900~~ | Create expectations over HTTPS — already works | **Closed** with documentation |
| ~~#1827~~ | Facebook patent license — React changed to MIT in 2017 | **Closed** as resolved |
| ~~#1375~~ | Browser TypeError — in separate `mockserver-client-node` repo | **Closed**, redirected to correct repo |
| ~~#1971~~ | ByteBuddy update — not a direct dependency | **Closed** with explanation |
| ~~#1713~~ | How verification works — support question | **Closed** with documentation link |
| ~~#1945~~ | Getting 429 — MockServer has no rate limiting | **Closed** — not a MockServer issue |

---

## Category 15: "Is This Project Dead?" Issues (all closed)

| Issue | Status |
|-------|--------|
| ~~#1935~~ | **Closed** — confirmed active development, new release upcoming |
| ~~#1912~~ | **Closed** — confirmed active development, new release upcoming |
| ~~#1904~~ | **Closed** — confirmed active development, new release upcoming |
| ~~#1865~~ | **Closed** — confirmed active development with new maintainers |

All four issues were closed with comments confirming active development: dependency updates, 130+ PRs resolved, comprehensive issue triage, and an upcoming release.

---

## Category 16: Miscellaneous Confirmed Issues

### #1908: Delay timeUnit accepts any string

**Status**: Confirmed bug — P3
**Root cause**: Invalid `timeUnit` strings silently result in `null`, causing delays to be silently skipped.
**Fix**: Add validation in `DelayDTO.buildObject()` to reject invalid `timeUnit` values.

### #1932: MockServer returns status code '0 U'

**Status**: Needs investigation — P3
**Root cause**: Likely a malformed response from an upstream server being proxied, or a response template setting `statusCode` to 0.
**Fix**: Add validation to reject status code 0. Default to 200 if statusCode is null or invalid.

### #1959: 500 Internal Server Error with latest Docker image

**Status**: Needs more information — P3
**Root cause**: Multiple possible causes. Without a stack trace, cannot diagnose.
**Fix**: Request stack trace from reporter. Check Docker image version alignment.

### #1949: Convert duplicate header values into one

**Status**: Feature request — P3
**Root cause**: MockServer correctly preserves multiple header values per RFC 7230. Merging into comma-separated values is an optional optimization.
**Fix**: Add optional header consolidation, but not for `Set-Cookie` (per RFC 6265).

### #1736: MockServer randomly times out on Mac M1

**Status**: Needs investigation — P3
**Root cause**: May be related to the x86_64 native library in ARM Docker images (#1868) or to JVM performance on Apple Silicon.
**Fix**: Verify with native ARM build. Check if the netty-tcnative library architecture mismatch causes TLS handshake delays.

### #1763: Running MockServer crashes on Android

**Status**: Out of scope — P3
**Root cause**: MockServer is not designed for Android. The JDK dependencies (Netty, BouncyCastle, JUL logging) may not be compatible with Android's runtime.
**Fix**: Close as not supported. Suggest alternatives for Android testing.

---

## Duplicate Issues Summary

| Primary Issue | Closed Duplicate | Status |
|--------------|-----------------|--------|
| #1978 (Times thread-safety) | ~~#1826~~ | **Closed** |
| #1773 (Velocity thread-safety) | ~~#1750~~ | **Closed** |
| #1895 (Docker healthcheck) | ~~#1751~~ | **Closed** |
| #1864 (imagePullSecrets) | ~~#1681~~ | **Closed** |
| #1602 (Spring Boot 3) | ~~#1828~~ | **Closed** |
| #1966 (json-schema-validator) | ~~#1980~~ | **Closed** |
| #1868 (ARM Docker) | #1887 (s390x — related but distinct) | Open |
| #1933 (HTTP/2) | #1803 (related but distinct trigger) | Open |
| #1974 (NottableString NOT) | #1639 (related but distinct scenario) | Open |

---

## Documentation Improvements Needed

Based on the issue analysis, the following documentation gaps should be addressed:

1. **Spring Boot compatibility matrix** (#1602, #1828): Document supported Spring Boot versions and why Spring Boot 3 is not supported
2. **HTTP/2 proxy limitations** (#1933, #1803): Document that HTTP/2 proxying is not supported
3. **TLS 1.3 configuration** (#1837): Document how to enable TLS 1.3
4. **Docker healthcheck setup** (#1895, #1751): Document `MOCKSERVER_LIVENESS_HTTP_GET_PATH` usage
5. **Memory tuning guide** (#1741, #1285): Document `MOCKSERVER_MAX_EXPECTATIONS`, `MOCKSERVER_MAX_LOG_ENTRIES`, heap sizing
6. **JSON body matching modes** (#1734, #1870): Clarify `withBody("string")` vs `withBody(json("..."))` behavior
7. **Regex vs literal path matching** (#1505): Document that `{` and `}` are regex metacharacters
8. **Multi-line templates in JSON** (#1840): Document JSON limitations and YAML alternatives
9. **Version migration guide** (#1893, #1273): Document breaking changes between versions
10. **`@MockServerSettings` implicit `@ExtendWith`** (#1977): Warn against double registration
11. **Verification timing** (#1524): Document async nature of log processing
12. **`attemptToProxyIfNoMatchingExpectation`** (#1478): Document Host header requirements
13. **Configuration property lifecycle** (#1885): Document that properties are read once at startup

---

## Recommended Resolution Priority

### Phase 1: Critical Thread-Safety Fixes (P0)
1. Fix `Times` thread-safety (#1978, #1826)
2. Fix `XmlStringMatcher` thread-safety (#1796)
3. Fix Velocity template tool thread-safety (#1750, #1773)
4. Fix `responseInProgress` flag thread-safety (#1834)
5. Fix `LRUCache` thread-safety (#1644)

### Phase 2: Security & Docker (P1)
6. Update Docker base images and pin digests (#1956, #1857)
7. Fix ARM native library in Docker (#1868)
8. Update remaining vulnerable dependencies (#1981, #1894, #1873, #1822)
9. Fix binary body corruption in forwarding (#1910)

### Phase 3: Core Functionality Fixes (P1-P2)
10. Fix NottableString negation logic (#1974, #1639)
11. Fix verify false positive (#1757)
12. Fix duplicate query parameters (#1866)
13. Fix proxy 421 misdirected request (#1897)
14. Fix expectation TTL display (#1874)
15. Fix WebSocket path prefix (#1693)

### Phase 4: OpenAPI & Schema Fixes (P2)
16. Fix OpenAPI multiple responses per operation (#1806)
17. Fix OpenAPI security override (#1315)
18. Fix JSON schema dialect selection (#1896)
19. Fix example `$ref` resolution (#1474)
20. Add ByteArraySchema handler (#1788)

### Phase 5: Integration & Configuration Fixes (P2)
21. Fix Spring TestExecutionListener nested classes (#1979)
22. Fix MockServer double-start (#1977)
23. Add JUnit 5 field injection (#1621)
24. Fix callback classloader (#1571)
25. Fix configuration caching (#1885)

### Phase 6: Helm Chart Improvements (P2-P3)
26. Add imagePullSecrets (#1864, #1681)
27. Fix chart naming for sub-charts (#1752)
28. Add PVC support (#1779)
29. Add custom pod labels (#1884)
30. Add configurable probes and startupProbe (#1600)

### Phase 7: Documentation & Cleanup
31. Write all documentation improvements listed above
32. Close resolved/out-of-scope issues
33. Close duplicate issues with cross-references
34. Address "is this project dead" issues

### Phase 8: Feature Requests (as capacity allows)
35. Evaluate and implement high-value feature requests
36. Triage remaining feature requests
