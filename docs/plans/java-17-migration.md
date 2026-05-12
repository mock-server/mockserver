# Java 17 Migration Plan

**Status:** Planned (Future)

Planned for a future major version bump. This document captures what would change, what would improve, and the migration steps required to raise MockServer's minimum Java version from 11 to 17.

## Motivation

- **Spring 5.x is EOL** — no more security patches. Spring 6.x requires Java 17.
- **Nashorn is a dead-end** — the standalone `org.openjdk.nashorn:nashorn-core` works but GraalVM JS (requiring Java 17+) is the actively-developed future.
- **Language improvements** — pattern matching, text blocks, records, switch expressions would significantly improve readability across ~600 files.
- **JVM improvements** — production-ready ZGC/Shenandoah for sub-millisecond GC pauses in Docker deployments.

## User Impact Assessment

Raising the minimum to Java 17 excludes users on Java 11. The project's rationale for Java 11 ("approximately 23% of Java projects still run on Java 11") means this should be a major version bump with advance notice.

## Phase 1: Dependency Upgrades (~1 week)

### 1.1 GraalVM JavaScript Engine (Replace Nashorn)

| Property | Current (Nashorn) | Target (GraalVM JS) |
|----------|-------------------|---------------------|
| Artifact | `org.openjdk.nashorn:nashorn-core:15.7` + 4 ASM jars | `org.graalvm.polyglot:polyglot` + `org.graalvm.polyglot:js-community` |
| Size | ~2.4 MB total | ~60 MB total |
| ES version | ES5.1 + partial ES6 | ES2023+ |
| Java minimum | Java 11 | Java 17 |
| Security sandbox | `ClassFilter` (Nashorn-specific) | `allowHostClassLookup()` predicate |

**Files requiring changes:**

| File | Change |
|------|--------|
| `mockserver-core/.../templates/engine/javascript/JavaScriptTemplateEngine.java` | Major rewrite: replace `NashornScriptEngineFactory`, `ClassFilter`, `ScriptObjectMirror` with GraalVM `Context`, `HostAccess`, `Value` API |
| `mockserver-core/.../templates/engine/javascript/bindings/ScriptBindings.java` | Delete — replaced by `context.getBindings("js").putMember()` |
| `mockserver-core/.../templates/ResponseTemplateTester.java` | Change Nashorn availability check to GraalVM check |
| `mockserver-core/.../serialization/ExpectationSerializer.java` (line 60) | Change class-loading probe from Nashorn to GraalVM |
| `mockserver/pom.xml` (dependencyManagement) | Replace nashorn-core + ASM with GraalVM polyglot artifacts |
| `mockserver-core/pom.xml` | Update dependency reference |
| `mockserver-netty/pom.xml` (shade plugin) | Add `org.graalvm` to shade exclusions |
| 35+ test methods in `JavaScriptTemplateEngineTest.java` | Change `nashornAvailable()` guard to GraalVM check |
| `jekyll-www.mock-server.com/mock_server/response_templates.html` | Remove Nashorn limitations, document full ES2023+ support |

**Security migration:**
```
Nashorn ClassFilter (DisallowClassesInTemplates)
  → GraalVM Context.newBuilder("js")
       .allowHostAccess(HostAccess.EXPLICIT)
       .allowHostClassLookup(className -> isAllowed(className))
       .build();
```
Note: `allowHostClassLookup` alone only controls class name resolution. `HostAccess.EXPLICIT` (or a scoped `HostAccess` policy) is also required to restrict method/field access, reflection, and constructor invocation — otherwise the sandbox is weaker than Nashorn's `ClassFilter`.

**Docker image impact:** +57 MB due to GraalVM JS size (increases Docker image by ~70%). Consider making JS template support an optional dependency or separate module.

### 1.2 Spring 5.x → Spring 6.x + javax → jakarta

| Dependency | Current | Target |
|-----------|---------|--------|
| Spring Core | 5.3.39 (EOL) | 6.x |
| Servlet API | `javax.servlet:javax.servlet-api:4.0.1` | `jakarta.servlet-api:6.0` |
| Tomcat | `tomcat-embed-core:9.0.117` | 10.x+ |

**Files affected by `javax.servlet` → `jakarta.servlet` namespace migration (29 files):**
- `mockserver-war/.../MockServerServlet.java`
- `mockserver-proxy-war/.../ProxyServlet.java`
- `mockserver-core/.../mappers/HttpServletRequestToMockServerHttpRequestDecoder.java`
- `mockserver-core/.../mappers/MockServerHttpResponseToHttpServletResponseEncoder.java`
- `mockserver-core/.../streams/IOStreamUtils.java`
- `mockserver-core/.../servlet/responsewriter/ServletResponseWriter.java`
- `mockserver-core/.../codec/BodyServletDecoderEncoder.java`
- Plus 6 test files and additional imports

### 1.3 Jetty 9.x → 12.x

Only affects `mockserver-examples` module. Low impact.

### 1.4 DataFaker (Fake Data Generation in Response Templates)

DataFaker generates realistic fake data (names, addresses, emails, phone numbers, commerce data, etc.) for use in response templates. Other mock servers include this via DataFaker integration; MockServer currently lacks it.

| Property | DataFaker 1.x | DataFaker 2.x |
|----------|---------------|---------------|
| Artifact | `net.datafaker:datafaker:1.9.0` | `net.datafaker:datafaker:2.5.4` |
| Java minimum | **Java 8** | **Java 17** |
| Providers | ~196 | ~258 |
| Maintained | **No** (last release April 2023) | **Yes** (active development) |
| Size | ~1.5 MB (shaded: snakeyaml + generex) | ~2 MB (shaded: snakeyaml + generex) |
| License | Apache 2.0 | Apache 2.0 |

**Java 11 constraint:** DataFaker 2.x requires Java 17, making it unavailable while MockServer targets Java 11. DataFaker 1.9.0 works on Java 11 but is unmaintained — no bug fixes, security patches, or new providers. This is one more reason to migrate to Java 17: it unlocks the actively maintained DataFaker 2.x with 60+ additional providers.

**How DataFaker works:**

1. **`Faker` entry point** — instantiate with optional `Locale` for localized data:
   ```java
   Faker faker = new Faker();                       // default English
   Faker faker = new Faker(new Locale("fr", "FR")); // French
   ```

2. **Provider methods** — each category (name, address, internet, commerce, etc.) is a provider object with typed methods:
   ```java
   faker.name().fullName();         // "Miss Samanta Schmidt"
   faker.name().firstName();        // "Emory"
   faker.address().streetAddress(); // "60018 Sawayn Brooks Suite 449"
   faker.internet().emailAddress(); // "john.doe@gmail.com"
   faker.commerce().productName();  // "Ergonomic Granite Chair"
   faker.phoneNumber().cellPhone(); // "1-234-567-8901"
   ```

3. **YAML data files** — providers read from locale-specific YAML files bundled in the JAR (e.g., `en.yml`, `fr.yml`). Values are selected randomly from these files.

4. **Expression DSL** — string-based composition without Java code:
   ```java
   faker.expression("#{Name.first_name}");           // "Emory"
   faker.expression("#{numerify '###-###-####'}");    // "123-456-7890"
   faker.expression("#{regexify '[A-Z]{3}\\d{4}'}"); // "ABC1234"
   ```

5. **Dependencies** — minimal: `snakeyaml` (YAML parsing) and `generex` (regex-to-string). Both are shaded into the JAR to avoid classpath conflicts.

**Integration with MockServer templates:**

Two approaches, both using `TemplateFunctions.BUILT_IN_FUNCTIONS`:

*Approach A — Expose a `Faker` instance as a single template variable (recommended):*

```velocity
## Velocity template
$faker.name().firstName()        ## → "Emory"
$faker.address().city()          ## → "Brittneymouth"
$faker.internet().emailAddress() ## → "john@example.com"
```

This gives users access to all 258 providers without registering individual functions. The `Faker` instance would be thread-safe (DataFaker is thread-safe) and created once at startup.

*Approach B — Register individual helper functions:*

```java
// In TemplateFunctions.BUILT_IN_FUNCTIONS
put("faker_first_name", () -> faker.name().firstName());
put("faker_last_name",  () -> faker.name().lastName());
put("faker_email",      () -> faker.internet().emailAddress());
// ... per-function registration for each provider
```

This is simpler but requires registering each function individually and limits discoverability.

**Files requiring changes:**

| File | Change |
|------|--------|
| `mockserver/pom.xml` (dependencyManagement) | Add `net.datafaker:datafaker:2.x` |
| `mockserver-core/pom.xml` | Add datafaker dependency |
| `mockserver-core/.../templates/engine/TemplateFunctions.java` | Add `faker` entry to `BUILT_IN_FUNCTIONS` map |
| `mockserver-core/.../templates/engine/velocity/VelocityTemplateEngine.java` | Ensure `faker` object is available in Velocity context |
| `mockserver-core/.../templates/engine/mustache/MustacheTemplateEngine.java` | Expose `faker` in Mustache data model (lambdas for common providers) |
| `mockserver-core/.../templates/engine/javascript/JavaScriptTemplateEngine.java` | Bind `faker` object in JS engine scope |
| `mockserver-netty/pom.xml` (shade plugin) | Add `net.datafaker` to shade includes/relocations to avoid snakeyaml conflicts |
| Test files for all three template engines | Add faker template tests |
| `jekyll-www.mock-server.com/mock_server/response_templates.html` | Document faker usage in templates with examples |

**Shade/relocation concern:** DataFaker shades its own copy of snakeyaml internally (`net.datafaker.shaded.snakeyaml`), so there should be no classpath conflict with MockServer's existing snakeyaml usage. However, the MockServer uber-JAR shade plugin configuration should be verified to include `net.datafaker` artifacts.

**Provider categories available (258 in DataFaker 2.x):** Address, Animal, App, Aviation, Barcode, Beer, Book, Business, Cat, Chess, Code, Color, Commerce, Company, Computer, Country, CryptoCoin, Currency, DateAndTime, Demographics, Dog, Domain, Educator, Emoji, File, Finance, Food, Hacker, IdNumber, Internet, Job, Lorem, Marketing, Medical, Military, Money, Music, Name, Nation, Number, Passport, PhoneNumber, ProgrammingLanguage, Science, Shakespeare, Space, Stock, Superhero, Team, University, Vehicle, Weather, plus 200+ entertainment, videogame, sport, and specialty providers.

## Phase 2: Language Modernisation (~1-2 weeks)

### 2.1 Pattern Matching for instanceof (High Impact)

279 `instanceof` checks across 76 files. Top candidates:

| File | Count | Example |
|------|------:|---------|
| `serialization/model/BodyDTO.java` | 22 | 22-branch `instanceof` + cast chain for Body subtypes |
| `openapi/examples/JsonNodeExampleSerializer.java` | 17 | Example subtype dispatch |
| `openapi/examples/ExampleBuilder.java` | 17 | Schema subtype dispatch |
| `openapi/OpenAPIConverter.java` | 13 | JSON node type dispatch |
| `matchers/HttpRequestPropertiesMatcher.java` | 13 | Request/body matcher types |
| `serialization/java/HttpRequestToJavaSerializer.java` | 10 | Body subtype serialization |

### 2.2 Text Blocks (High Impact)

~500 test files and 17 production files contain multi-line string concatenation. Top candidates:
- `cli/Main.java` — 34-line USAGE string constant
- `validator/jsonschema/JsonSchemaValidator.java` — inline JSON examples
- Dashboard serializer tests — thousands of lines of JSON string concatenation

### 2.3 Switch Expressions (Moderate Impact)

39 switch statements. Top candidate: `HttpActionHandler.java` — 12-branch switch on `Action.Type` spanning ~90 lines.

### 2.4 Records (Moderate Impact)

Best candidates: `Delay`, `VerificationTimesDTO`, `TimesDTO`, `WebSocketClientIdDTO`. Note: DTOs using Jackson deserialization with no-arg constructors may not be suitable.

### 2.5 Sealed Classes (Moderate Impact)

Type hierarchies to seal:
- `Body<T>` — 11 subtypes (enables exhaustive `instanceof` chains)
- `Action<T>` — 9 subtypes
- `RequestDefinition` — 2 subtypes (`HttpRequest`, `OpenAPIDefinition`)
- `BodyDTO` — 11 subtypes mirroring `Body`

## Phase 3: JVM Configuration (~1 day)

### 3.1 GC Configuration for Docker

- Document ZGC as recommended GC for Docker: `-XX:+UseZGC`
- Sub-millisecond pause times regardless of heap size
- Benefits users with large `maxLogEntries` values

### 3.2 Helpful NullPointerExceptions

Enabled by default in Java 14+. No code changes — purely a runtime debugging improvement for complex matcher chains.

### 3.3 Security Improvements

- TLS 1.3 improvements (Certificate Authorities extension, additional cipher suites)
- Stronger default algorithms (SHA-1 signed JARs disabled, weak named curves disabled)
- TLS 1.0/1.1 disabled by default — may need configuration escape hatch for users mocking legacy endpoints

## Migration Checklist

- [ ] Announce Java 17 minimum in release notes with advance notice
- [ ] Update `maven.compiler.source` and `maven.compiler.target` to `17`
- [ ] Remove `--add-exports=java.base/sun.security.x509=ALL-UNNAMED` from compiler args (verify not needed)
- [ ] Replace Nashorn with GraalVM JS
- [ ] Migrate `javax.servlet` → `jakarta.servlet`
- [ ] Upgrade Spring 5.x → 6.x
- [ ] Upgrade Tomcat 9.x → 10.x+
- [ ] Run full test suite on Java 17
- [ ] Update Docker base images to Java 17
- [ ] Update consumer documentation
- [ ] Apply language modernisation (can be done incrementally)

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| GraalVM JS size (+57 MB) | Medium | Consider optional module or lazy loading |
| GraalVM JS shade incompatibility | Medium | Exclude from shading, test uber-JAR thoroughly |
| javax → jakarta breaks user WAR deployments | High | Major version bump, clear migration guide |
| Spring 6 API changes | Low | MockServer uses minimal Spring surface area |
| Java 11 user exclusion | Medium | Advance notice, maintain 5.x branch for security fixes |
