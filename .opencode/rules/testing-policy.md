# Testing Policy

## Post-Change Testing

After making code changes, ALWAYS run unit tests for the affected module(s).

- Identify which Maven module(s) were modified based on file paths (e.g., files in `mockserver-core/` → module `mockserver-core`)
- Run unit tests with Maven targeting the specific module: `./mvnw test -pl <module>`
- If tests fail, fix the issues before considering the task complete
- When a specific test fails, re-run just that test: `./mvnw test -pl <module> -Dtest=<TestClassName>#<testMethodName>`
- Do NOT run integration tests automatically — they are slow and run in CI
- If changes span multiple modules, run tests for ALL affected modules: `./mvnw test -pl <module1>,<module2>`

## Before Committing (MANDATORY)

Follow the full pre-commit workflow in `commit-workflow.md`. That workflow covers all file types (Java, Terraform, Bash, Docker, Helm, docs). This file covers the Java-specific testing details.

When the user asks to commit Java changes:
1. **Run unit tests** — `./mvnw test -pl <modules>` for all affected modules. Fix failures before committing.
2. **Adversarial review** — launch `review-cheap` subagent (see `commit-workflow.md` Step 3).
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
| `mockserver-examples/` | `mockserver-examples` |

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

# All unit tests (slow — avoid unless needed)
./mvnw test

# Quick build (compile + test, skip integration tests)
./mvnw verify -DskipITs
```

## Test Quality

- **New tests:** Follow existing test patterns in the module. Use JUnit 5 (Jupiter) only in `mockserver-junit-jupiter`; all other modules use JUnit 4.
- **Flaky tests:** Never just re-run — investigate root cause. Common causes: port contention, timing-dependent assertions, shared mutable state.
- Descriptive test names that explain the expected behavior.
