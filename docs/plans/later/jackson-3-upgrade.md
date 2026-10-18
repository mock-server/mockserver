# Jackson 2 → Jackson 3 Upgrade — Blocked Upstream

**Status:** Blocked — `swagger-core` is now actively migrating to Jackson 3 on a `3.0.0` branch
(3.0.0-SNAPSHOT deployed to Sonatype 2026-10-07). `swagger-parser` has a migration branch too
(`upgrade-to-jackson3`, one commit, `c987f220b`, 2026-09-07) but no PR, snapshot or release built
from it. Neither has a GA release. Our own side of the migration is understood and mechanical;
nothing is blocked on MockServer.
**Created:** 2026-07-19
**Last verified against upstream:** 2026-10-18
**Scope:** Migrate MockServer's own serialization from `com.fasterxml.jackson.*` (Jackson 2) to `tools.jackson.*` (Jackson 3).
**Driver:** Spring Boot 4 ships Jackson 3, so consumers increasingly run Jackson 3 in their applications. Requested in [#1970](https://github.com/mock-server/mockserver-monorepo/issues/1970) and [#2426](https://github.com/mock-server/mockserver-monorepo/issues/2426) (closed as duplicate).

## The blocker

`swagger-parser` backs OpenAPI mock loading — a core MockServer feature we cannot drop — and has no Jackson 3 release. It transitively pins `swagger-core`, so both stay on Jackson 2.

**Upstream trackers to watch:**

| Tracker | State (2026-10-18) |
|---------|--------------------|
| [swagger-parser#2261](https://github.com/swagger-api/swagger-parser/issues/2261) — "Upgrade to Jackson 3" | Still open, no PR. A 2026-09-17 comment says the switch depends on swagger-core#4991 landing first — this is the tracker that actually gates us. There is a `upgrade-to-jackson3` branch (one commit, `c987f220b`, 2026-09-07) but no PR, snapshot or release has been built from it. |
| [swagger-core#4991](https://github.com/swagger-api/swagger-core/issues/4991) — "Support Jackson 3.x release line" | Open, no milestone, but superseded in practice by the `3.0.0` branch work below. |
| [swagger-core#5031](https://github.com/swagger-api/swagger-core/pull/5031) — "migrate to jackson 3" (PR, 132 files, based on `io.github.vpelikh`'s fork) | **Closed, not merged** (2026-09-09): maintainers reworked it on their own branch and replaced it with PR #5314. |
| swagger-core `3.0.0` branch | Active. #5314 merged 2026-09-11 (based on #5031, itself based on `io.github.vpelikh`'s fork — see below); follow-ups #5316/#5317/#5319 merged 2026-09-15 to 09-17 — maintainers say the codebase is now fully on `tools.jackson` 3.2.2 with a Java 17 floor. Further follow-ups #5320 and #5329–#5332 (opened 2026-09-18 to 09-26) merged 2026-09-28 to 2026-10-14. A `3.0.0-SNAPSHOT` was deployed to Sonatype for testing on 2026-10-07; maintainers were still soliciting downstream testing (springdoc-openapi, GuicedEE) as of 2026-10-14. No release date given. |

**Versions at last check (2026-10-18):**

| Library | Latest | Jackson |
|---------|--------|---------|
| `io.swagger.parser.v3:swagger-parser` | 2.1.48 | `com.fasterxml` (2.x) only |
| `io.swagger.core.v3:swagger-core` | 2.2.55 | `com.fasterxml` (2.x) only |
| `tools.jackson.core:jackson-core` | 3.2.3 | — |
| Our pin (`mockserver/pom.xml` `jackson.version`) | 2.22.3 | — |

**Rejected workaround:** `io.github.vpelikh` publishes a Jackson 3 fork of the swagger-core stack, but it ships no `swagger-parser` artifact, so swapping core alone still leaves the parser dragging the whole Jackson 2 tree in — does not unblock us on its own. (Its work did not go to waste: swagger-core's own `3.0.0` branch, PR #5314, is built on top of it.)

## What is *not* blocking

- **Java version.** 7.0.0 moved to Java 17, clearing Jackson 3's Java 17 floor. This was the stated blocker in #1970 and no longer applies — swagger-parser is now the *only* one.
- **Two Jackson namespaces coexisting.** Already proven in production code: `com.networknt:json-schema-validator` 3.x (currently 3.0.6) runs on `tools.jackson` inside MockServer today — migrated in commit `2449bcab1` — alongside our Jackson 2 usage. Different Maven coordinates and different Java packages mean they cannot collide.
- **Consumers on Jackson 3.** For the same reason, MockServer depending on Jackson 2 does not prevent a Spring Boot 4 application from using Jackson 3. Reports of `ClassNotFoundException: com.fasterxml.jackson.databind.JsonSerializer` are Jackson 2 being *excluded* by a BOM, not a genuine incompatibility.

## Our side of the work, when unblocked

Mechanical, with no exotic API surface to redesign:

1. Namespace migration `com.fasterxml.jackson.*` → `tools.jackson.*` across the codebase (~361 files at the time of the #1970 investigation).
2. Update `jackson.version` / `jackson-annotations.version` in `mockserver/pom.xml` to the Jackson 3 coordinates. Note Jackson 3 keeps annotations on `com.fasterxml.jackson.core:jackson-annotations` — that split is expected, not a mistake.
3. Revisit the shade relocations in `mockserver/pom.xml` — `com.fasterxml` → `shaded_package.com.fasterxml` and `tools.jackson` → `shaded_package.tools.jackson` both already exist; the balance between them shifts.
4. Drop the Jackson 2/3 bridging currently needed in `JsonSchemaValidator` (string-based `getSchema` / `validate` calls exist purely to stop Jackson 2 `JsonNode` crossing into Jackson 3 APIs — see `2449bcab1`). Once everything is Jackson 3 this can go back to node-based calls.
5. Re-check `mockserver-client-java`'s parser exclusions and the `dependencyConvergence` guarantees established in 7.1.0 (#1970) — the transitive tree changes shape.

Major-version-worthy: it changes the transitive dependency coordinates of every published artifact.

## Re-check trigger

Re-evaluate when **swagger-parser publishes a Jackson 3 line**. Cheapest probe:

```bash
curl -s https://repo1.maven.org/maven2/io/swagger/parser/v3/swagger-parser/maven-metadata.xml | grep latest
# then fetch that version's POM and grep for tools.jackson
```

Watch [swagger-parser#2261](https://github.com/swagger-api/swagger-parser/issues/2261) — nothing else moves until that does.
