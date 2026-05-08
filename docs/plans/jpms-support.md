# JPMS (Java Platform Module System) Support Plan

Analysis and implementation plan for adding JPMS support to MockServer.

## Current State

- **No `module-info.java` files** exist anywhere in the project
- **No `Automatic-Module-Name`** manifest entries exist
- **Split packages** (the original blocker from issue #970) have been resolved
- **6 of 11 modules** produce shaded uber-JARs via maven-shade-plugin
- Java 11 target is sufficient for JPMS (available since Java 9)

## Value Assessment

### Who Benefits

| User Type | Benefit | Impact |
|-----------|---------|--------|
| Library consumers using JPMS | Can declare `requires org.mockserver.client` in their `module-info.java` | High for this niche |
| General users (classpath) | No change — unnamed module fallback works | None |
| Docker/standalone users | No change | None |

### Current Pain Point

Without `Automatic-Module-Name`, MockServer JARs placed on the module path get **unstable automatic module names** derived from the JAR filename (e.g., `mockserver.client.java` from `mockserver-client-java-5.16.0.jar`). These names change with version bumps and cannot be depended upon in `module-info.java` files.

### Conclusion

JPMS support is a **low-urgency, moderate-value** improvement. Phase 1 (Automatic-Module-Name) provides 80% of the value with 5% of the effort.

## Dependency JPMS Readiness

| Dependency | Version | JPMS Status |
|-----------|---------|-------------|
| Netty | 4.2.13.Final | Full (`module-info.class`) |
| Jackson | 2.21.3 | Full (`module-info.class`) |
| SLF4J | 2.0.17 | Full (`module-info.class`) |
| Guava | 33.6.0-jre | Full (`module-info.class`) |
| BouncyCastle | 1.84 | Full (`module-info.class`) |
| ClassGraph | 4.8.184 | Full (`module-info.class`) |
| Commons Lang3 | 3.20.0 | Full (`module-info.class`) |
| Commons IO | 2.22.0 | Full (`module-info.class`) |
| JMustache | 1.16 | Full (`module-info.class`) |
| Nashorn | 15.7 | Full (`module-info.class`) |
| Spring Core | 5.3.39 | Automatic (`Automatic-Module-Name: spring.core`) |
| Prometheus | 1.6.1 | Automatic (`Automatic-Module-Name`) |
| JsonPath | 2.10.0 | Automatic (`Automatic-Module-Name`) |
| **Velocity** | **2.4.1** | **No JPMS support** (unnamed automatic module) |
| **Swagger Parser** | **2.1.41** | **No JPMS support** (unnamed automatic module) |
| **JZLib** | **1.1.3** | **No JPMS support** (unnamed automatic module) |

3 dependencies lack JPMS support. These would function as unnamed automatic modules on the module path.

## Challenges

### Reflection Usage

| File | Pattern | Impact |
|------|---------|--------|
| `mockserver-spring-test-listener/.../MockServerTestExecutionListener.java` | `field.setAccessible(true)` for injecting `MockServerClient` into test fields | Requires `opens` directive |
| `mockserver-junit-rule/.../MockServerRule.java` | Same pattern for JUnit 4 rule injection | Requires `opens` directive |
| `mockserver-core/.../ExpectationInitializerLoader.java` | `loadClass()` + `newInstance()` for user-provided initializer classes | Plugin loading — user classes must be accessible |
| `mockserver-core/.../HttpResponseClassCallbackActionHandler.java` | `loadClass()` for class callbacks | Same plugin pattern |

### ClassGraph Classpath Scanning

`mockserver-core/.../file/FilePath.java` and `FileReader.java` use `ClassGraph.scan()` to find resources. On the module path, only resources from modules that explicitly `opens` packages are visible.

### Shade Plugin Interaction

All 6 shaded modules use `<shadedArtifactAttached>true</shadedArtifactAttached>` with `-shaded` classifier. This means:
- The **primary (unshaded) JAR** is preserved as the main artifact — this is what JPMS uses
- The **shaded JAR** is a secondary artifact for classpath users
- `Automatic-Module-Name` set by maven-jar-plugin is preserved in both JARs

## Implementation Phases

### Phase 1: Automatic-Module-Name (~1 day) — RECOMMENDED

Add stable module names to all modules' `MANIFEST.MF` without any code changes.

**Approach:** Add a `<module.name>` property to each child module's `pom.xml`, and configure the parent's maven-jar-plugin to include it as `Automatic-Module-Name`.

**Parent POM change** (`mockserver/pom.xml`, existing jar plugin config at line 937):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <version>3.5.0</version>
    <configuration>
        <archive>
            <manifest>
                <addDefaultImplementationEntries>true</addDefaultImplementationEntries>
                <addDefaultSpecificationEntries>true</addDefaultSpecificationEntries>
            </manifest>
            <manifestEntries>
                <Automatic-Module-Name>${module.name}</Automatic-Module-Name>
            </manifestEntries>
        </archive>
    </configuration>
</plugin>
```

**Module name assignments:**

| Module | artifactId | `<module.name>` Property |
|--------|-----------|--------------------------|
| mockserver-testing | `mockserver-testing` | `org.mockserver.testing` |
| mockserver-client-java | `mockserver-client-java` | `org.mockserver.client` |
| mockserver-core | `mockserver-core` | `org.mockserver.core` |
| mockserver-integration-testing | `mockserver-integration-testing` | `org.mockserver.integration.testing` |
| mockserver-war | `mockserver-war` | `org.mockserver.war` |
| mockserver-proxy-war | `mockserver-proxy-war` | `org.mockserver.proxy.war` |
| mockserver-netty | `mockserver-netty` | `org.mockserver.netty` |
| mockserver-junit-rule | `mockserver-junit-rule` | `org.mockserver.junit.rule` |
| mockserver-junit-jupiter | `mockserver-junit-jupiter` | `org.mockserver.junit.jupiter` |
| mockserver-spring-test-listener | `mockserver-spring-test-listener` | `org.mockserver.spring.test` |
| mockserver-examples | `mockserver-examples` | `org.mockserver.examples` |

**WAR modules** (`mockserver-war`, `mockserver-proxy-war`): These use `war` packaging, so the maven-jar-plugin doesn't run. The `Automatic-Module-Name` would need to go in the maven-war-plugin's `<archive>` config instead. However, WAR files are not placed on the JPMS module path, so this is low priority.

### Phase 2: module-info.java for Core Modules (~1 week)

Author `module-info.java` for `mockserver-client-java` and `mockserver-core`. These are the modules most consumed by downstream users.

**Example `mockserver-core/src/main/java/module-info.java`:**
```java
module org.mockserver.core {
    requires io.netty.handler;
    requires io.netty.codec.http;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    // ... etc

    exports org.mockserver.model;
    exports org.mockserver.mock;
    exports org.mockserver.matchers;
    exports org.mockserver.configuration;
    // ... etc

    opens org.mockserver.model to com.fasterxml.jackson.databind;
    opens org.mockserver.serialization.model to com.fasterxml.jackson.databind;
}
```

This phase requires:
- Mapping all inter-module dependencies
- Declaring `exports` for public API packages
- Declaring `opens` for Jackson serialization packages
- Adding `requires` for all compile-time dependencies
- Testing that the module descriptor is valid

### Phase 3: Full Modularisation (~1-2 weeks)

Address remaining modules and the shade plugin / uber-JAR model:
- Author `module-info.java` for all remaining modules
- Ensure `setAccessible(true)` in JUnit rule and Spring listener works via `opens` directives
- Address `ClassGraph` classpath scanning behavior on module path
- Handle dynamic class loading for callbacks (`ExpectationInitializerLoader`, `HttpResponseClassCallbackActionHandler`)
- Maintain two distribution paths: modular JARs (non-shaded) and shaded uber-JARs (classpath only)

## Risk Assessment

| Risk | Phase | Severity | Mitigation |
|------|-------|----------|------------|
| Wrong module name chosen | 1 | High (permanent) | Follow reverse-domain convention, review against package structure |
| Shade plugin strips manifest | 1 | Low | Verified: `shadedArtifactAttached=true` preserves primary JAR |
| `module-info.java` breaks classpath users | 2 | Low | Multi-release JAR with `module-info.class` only in `META-INF/versions/9/` |
| Reflection in JUnit/Spring integrations | 3 | Medium | `opens` directives, document `--add-opens` for edge cases |
| ClassGraph scanning changes | 3 | Medium | Test resource loading on module path, add fallback |
| 3 deps without JPMS support | 2-3 | Low | Automatic modules work, just can't use `requires transitive` |

## Recommendation

**Implement Phase 1 only for now.** It provides stable module names for downstream JPMS consumers with minimal risk and effort. Phase 2-3 should be deferred until there is concrete user demand or until the Java 17 migration (see `java-17-migration.md`) is underway.
