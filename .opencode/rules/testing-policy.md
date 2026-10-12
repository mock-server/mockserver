# Testing Policy

## Post-Change Testing

After making code changes, ALWAYS run unit tests for the affected module(s).

- Identify which Maven module(s) were modified based on file paths (e.g., files in `mockserver-core/` → module `mockserver-core`)
- Run unit tests with Maven targeting the specific module: `./mvnw test -pl <module>`
- If tests fail, fix the issues before considering the task complete
- When a specific test fails, re-run just that test: `./mvnw test -pl <module> -Dtest=<TestClassName>#<testMethodName>` — but in `mockserver-core` first check whether the class is quarantined into the sequential phase (see "Quarantined (Sequential-Phase) Tests" below); for those, `-Dtest` is not evidence
- Do NOT run integration tests automatically — they are slow and run in CI
- If changes span multiple modules, run tests for ALL affected modules: `./mvnw test -pl <module1>,<module2>`

## Quarantined (Sequential-Phase) Tests in `mockserver-core`

**`mockserver-core` splits its unit tests across two Surefire executions, and `-Dtest=<Class>` defeats the split.** For the 123 classes quarantined into the sequential phase, a `-Dtest` run is **not evidence in either direction** — neither its pass nor its failure means anything. Verify those classes with a **full-module run** (`./mvnw test -pl mockserver-core`), or with the single-execution command below.

**Mechanism.** `mockserver-core/pom.xml` declares two `surefire:test` executions: `default-test` (the plugin-level `<configuration>`, `<parallel>classes</parallel>` with `threadCount` 4) which `<exclude>`s the quarantined classes, and `sequential-tests` (`<parallel>none</parallel>`) which `<include>`s exactly those classes. (A third execution, `allocation-tests-without-c2`, runs only `HttpStateRetrieveAllocationTest` with C1 only; it names that class with `<test>`, which `-Dtest` does not replace, so any core `-Dtest` run of the `test` phase also runs that class under C1.) Surefire's `test` parameter **replaces both the `<includes>` and the `<excludes>` of every execution** with the named class (`AbstractSurefireMojo.getIncludeList`/`getExcludeList` return only the `-Dtest` value / an empty list once a specific test is named), so both executions run it:

- a **quarantined** class runs in the parallel phase it was quarantined out of — where it is driven by Surefire's parallel JUnitCore provider (`surefire.junitcore.pc.Scheduler`) rather than the plain JUnit4 runner, so it reports failures that are artefacts of the wrong phase — and then runs a second time, correctly, in `sequential-tests`
- a **non-quarantined** class simply runs twice, once per execution

The wrong-phase run happens **first**, so the tail of the log shows the clean second run. With `-Dmaven.test.failure.ignore=true` the reader records a pass (false green); without it the build fails in `default-test` before the correct execution ever runs (false red).

Reproduction on unmodified `master`, one invocation:

```
./mvnw -o -pl mockserver-core test -Dtest=ControlPlaneAuthenticationRouteDenialTest
  surefire:test (default-test)      Tests run: 45, Failures: 3   <- wrong phase, bogus failures
  surefire:test (sequential-tests)  Tests run: 45, Failures: 0   <- correct phase, clean
```

**Is a class quarantined?** The `<includes>` of the `sequential-tests` execution in `mockserver-core/pom.xml` are the authoritative list (`ParallelStaticStateGuardTest` fails the build if they drift from the `default-test` `<excludes>`):

```bash
grep -q "<include>\*\*/<ClassName>.java</include>" mockserver/mockserver-core/pom.xml && echo QUARANTINED
```

**Verifying one quarantined class.** A full-module run is the default and remains the pre-merge gate (`worktree-workflow.md`). While iterating, invoke the sequential execution directly — this runs the class exactly once, in the phase it belongs to:

```bash
./mvnw -o -pl mockserver-core test-compile surefire:test@sequential-tests -Dtest=<ClassName>
```

Use that form **only** for quarantined classes: it skips `default-test` entirely, so for any other class it would run the wrong phase.

## Docker-Gated Tests

**Docker is available locally** (Docker Desktop on the developer Mac) — see `AGENTS.md` → "Local Development Environment".

- Tests guarded by `Assume.assumeTrue(DockerAvailability.isAvailable(...))` (Testcontainers live-broker tests, `NET_ADMIN` transparent-proxy e2e, QUIC/HTTP-3 client tests, etc.) **actually run here** — when validating such a change, run it and confirm it PASSES, not merely that it skips.
- **Keep the `assumeTrue(...)` gating in place** regardless. It is still correct so the suite degrades gracefully on machines/CI agents without Docker. Docker being present changes how we *validate*, not how we *write* the tests.
- **Gate on `org.mockserver.test.DockerAvailability` (in `mockserver-testing`); NEVER call `DockerClientFactory.instance().isDockerAvailable()` directly.** That method converts only `IllegalStateException` into `false` and **throws** for every other failure — it starts the Ryuk reaper and runs version/mount checks, so a `BadRequestException` (privileged Ryuk rejected by a user-namespace-remapped daemon), a `ContainerFetchException`, or an `Error` like `NoClassDefFoundError` from an incomplete test classpath escapes it. That converts "no usable Docker" into a hard ERROR and defeats the guard, and Testcontainers caches the failure and rethrows it for the rest of the JVM. A `catch (Exception e)` around the call is NOT sufficient — it misses the `Error` cases. Write:
  ```java
  Assume.assumeTrue("Docker is not available",
      DockerAvailability.isAvailable(() -> DockerClientFactory.instance().isDockerAvailable()));
  ```
  Pass a **lambda, not a method reference**, so `instance()` is evaluated inside the wrapper's try/catch.
- **If the suite runs in CI, add a fail-closed assertion.** A fail-safe probe turns unusable Docker into a SKIP; in CI that is a silent false positive. Cover the suite's surefire reports with `.buildkite/scripts/steps/assert-suite-ran.sh` so a skip fails the build loudly. Always confirm a Docker-gated test actually RAN (not skipped) before claiming local validation — check the surefire XML for named, non-skipped test cases, since a `@BeforeClass` assumption failure reports the whole class as a single anonymous skipped entry.
- Testcontainers 1.21.4+ (docker-java 3.4.2) works on Docker Desktop 4.67 / Engine 29.x / API 1.54. (Testcontainers 1.20.6 / docker-java 3.4.1 got a 400 on the info endpoint and reported Docker unavailable even though it worked.)
- `docker build` / `docker run` are available for Dockerfile smoke checks (see `commit-workflow.md`).

## Before Committing (MANDATORY)

Follow the full pre-commit workflow in `commit-workflow.md`. That workflow covers all file types (Java, Terraform, Bash, Docker, Helm, docs). This file covers the Java-specific testing details.

When the user asks to commit Java changes:
1. **Run unit tests** — `./mvnw test -pl <modules>` for all affected modules. Fix failures before committing.
2. **Adversarial review** — launch `review-cheap` subagent (see `commit-workflow.md` Step 4; control / AI-component changes use `review-final` + gated approval).
3. **Only then commit.**

**Skip condition:** If user explicitly says to skip (e.g., "skip tests", "just commit"), skip corresponding steps.

If unit tests already passed earlier in this conversation for the exact same changes (no further edits since), skip re-running.

## Maven Module Mapping

| Directory | Maven Module |
|-----------|-------------|
| `mockserver-core/` | `mockserver-core` |
| `mockserver-netty/` | `mockserver-netty` |
| `mockserver-client-java/` | `mockserver-client-java` |
| `mockserver-war/` | `mockserver-war` |
| `mockserver-proxy-war/` | `mockserver-proxy-war` |
| `mockserver-junit-jupiter/` | `mockserver-junit-jupiter` |
| `mockserver-junit-rule/` | `mockserver-junit-rule` |
| `mockserver-spring-test-listener/` | `mockserver-spring-test-listener` |
| `mockserver-testing/` | `mockserver-testing` |
| `mockserver-integration-testing/` | `mockserver-integration-testing` |
| `examples/java/` | `mockserver-examples` |

## Maven Test Commands

```bash
# Unit tests for a specific module
./mvnw test -pl mockserver-core

# Unit tests for multiple modules
./mvnw test -pl mockserver-core,mockserver-netty

# Run a specific test class
./mvnw test -pl mockserver-core -Dtest=HttpRequestTest

# Run a specific test method
./mvnw test -pl mockserver-core -Dtest=HttpRequestTest#shouldCreateRequest

# Run a single QUARANTINED mockserver-core class (see "Quarantined (Sequential-Phase) Tests")
./mvnw -o -pl mockserver-core test-compile surefire:test@sequential-tests -Dtest=HttpStateTest

# All unit tests (slow — avoid unless needed)
./mvnw test

# Quick build (compile + test, skip integration tests)
./mvnw verify -DskipITs
```

## Test Quality

- **New tests:** Follow existing test patterns in the module. Use JUnit 5 (Jupiter) only in `mockserver-junit-jupiter`; all other modules use JUnit 4.
- **Flaky tests:** Never just re-run — investigate root cause. Common causes: port contention, timing-dependent assertions, shared mutable state.
- Descriptive test names that explain the expected behavior.

## Local-First Verification

**Prove fixes locally before pushing.** Reproduce the failure and confirm the fix passes locally before pushing. Do not push speculatively and rely on CI to confirm correctness — CI runs under a PTY and some repros require `pty.fork`; a green CI run is not a substitute for a local prove-out.

## Observable-Behaviour Tests

**Prefer tests that assert observable behaviour** over tests coupled to implementation details (specific class names, shaded-jar contents, internal field values). Observable-behaviour tests assert what the system *does* — HTTP response codes, JSON payloads, log entries, emitted events — so they survive safe refactors without false-failure noise.
