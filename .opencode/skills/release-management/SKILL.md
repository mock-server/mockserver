---
name: release-management
description: >
  Prepares a MockServer release by recommending the release version from
  Semantic Versioning rules and `changelog.md`, checking release readiness,
  and listing the exact Buildkite release parameters. Use when users say
  "prepare release", "release version", "run the release pipeline",
  "which version should we release", or need to verify changelog and secret
  readiness before triggering the release pipeline.
---

# Prepare a MockServer Release

Use this workflow before triggering the `mockserver-release` Buildkite pipeline.

## Required Inputs

Inspect these sources every time:

- `changelog.md` — especially the `## [Unreleased]` section
- `mockserver/pom.xml` — current development `-SNAPSHOT` version
- `.buildkite/release-pipeline.yml` — supported release parameters and automated steps
- `docs/operations/release-process.md` — operator-facing release guidance
- Git tags matching `mockserver-X.Y.Z` — latest numeric release is the authoritative `old-version`

If AWS access is available, also verify the required secrets exist and contain the expected keys without printing secret values.

## Version Recommendation Rules

Recommend the next release from the latest numeric `mockserver-X.Y.Z` tag, not from the current `-SNAPSHOT` alone.

Apply these rules in order:

1. **Major release** — if any unreleased changelog bullet is explicitly marked `BREAKING:`
2. **Minor release** — if there are meaningful bullets under `Added` or `Changed` and no `BREAKING:` markers
3. **Patch release** — if there are only meaningful bullets under `Fixed`
4. **Block release** — if the unreleased changelog is empty, vague, or not ready to publish

Definitions:

- A "meaningful bullet" is a non-empty `- ...` entry
- `BREAKING:` should appear at the start of an unreleased bullet when a major version bump is intended

Derived values:

- `release-version`: recommended SemVer release
- `next-version`: `release-version` patch increment plus `-SNAPSHOT`
- `old-version`: latest numeric `mockserver-X.Y.Z` tag
- `release-type`: `full` unless the user explicitly wants a partial rerun
- `create-versioned-site`: `yes` for major or minor releases, `no` for patch releases

## Readiness Checks

Validate each of these before declaring the release ready:

1. `changelog.md` has meaningful unreleased bullets
2. `changelog.md` does not already contain a section for the proposed `release-version`
3. `mockserver/pom.xml` is currently on an `X.Y.Z-SNAPSHOT` version
4. The pipeline supports the required outputs for this release:
   - Maven Central core artifacts
   - `mockserver-maven-plugin`
   - Docker Hub and AWS ECR Public images
   - `mockserver-node`
   - `mockserver-client`
   - Helm
   - Javadoc
   - SwaggerHub / OpenAPI
   - website
   - JSON Schema
   - PyPI
   - RubyGems
   - GitHub Release
5. Required secrets are present:
   - `mockserver-build/sonatype`
   - `mockserver-build/dockerhub`
   - `mockserver-build/pypi`
   - `mockserver-build/rubygems`
   - `mockserver-release/gpg-key`
   - `mockserver-release/github-token`
   - `mockserver-release/totp-seed`
   - `mockserver-release/npm-token`
   - `mockserver-release/swaggerhub`
   - `mockserver-release/website-role`

## Output Format

Return a concise release-preparation report with these sections:

### Recommendation

- `release-version`
- `next-version`
- `old-version`
- `release-type`
- `create-versioned-site`

### Rationale

- Explain why the bump is major, minor, or patch
- Cite the relevant changelog bullets and release tag comparison

### Readiness

- `changelog`: pass/fail with reason
- `version state`: pass/fail with current snapshot version
- `secrets`: pass/fail with missing items, if any
- `pipeline coverage`: pass/fail with any remaining gaps

### Manual Follow-up

- List only the steps still outside the automated pipeline
- Today this should normally be just Homebrew

### Monitor & Verify

Always include this section verbatim so the operator can watch the release land. Substitute the chosen `release-version` into the URLs:

- **Sonatype Central Portal (live deployment status):** https://central.sonatype.com/publishing/deployments
- **Central Portal artifact view:** https://central.sonatype.com/artifact/org.mock-server/mockserver-netty/<release-version>
- **Live at Maven Central (canonical "released" signal — returns 200 once synced):** https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/<release-version>/
- **Maven Central search (browse all org.mock-server artifacts):** https://central.sonatype.com/search?namespace=org.mock-server&sort=published
- **Docker Hub tags:** https://hub.docker.com/r/mockserver/mockserver/tags
- **npm — mockserver-node:** https://www.npmjs.com/package/mockserver-node
- **npm — mockserver-client-node:** https://www.npmjs.com/package/mockserver-client-node
- **PyPI:** https://pypi.org/project/mockserver-client/
- **RubyGems:** https://rubygems.org/gems/mockserver-client
- **GitHub Releases:** https://github.com/mock-server/mockserver/releases
- **Buildkite pipeline:** https://buildkite.com/mockserver/mockserver-release

## Notes

- Prefer explicit evidence from the repo over assumptions
- If the changelog suggests a major bump but no `BREAKING:` marker exists, call that out and ask the user whether the release should be treated as breaking before finalising the recommendation
- If the user asks to run a partial rerun, keep `release-version` and `old-version` consistent with the already-published release and explain which downstream steps will be skipped
