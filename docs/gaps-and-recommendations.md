# Gaps & Recommendations

## Review Summary

This document identifies missing documentation, undocumented areas, and recommendations for improving the project's documentation and operational practices.

## Critical Gaps

### 1. ~~No Infrastructure as Code~~ (Resolved)

**Status:** ~~The AWS infrastructure is managed manually or via a CloudFormation stack whose template is not stored in this repository.~~

**Resolution:** Buildkite agent infrastructure is now managed by Terraform in `terraform/buildkite-agents/`, using the official [Buildkite Elastic CI Stack for AWS](https://github.com/buildkite/terraform-buildkite-elastic-ci-stack-for-aws) module. State is stored remotely in S3 with native file locking. See [AWS Infrastructure](infrastructure/aws-infrastructure.md) for details.

**Remaining:** Website infrastructure (S3, CloudFront, Route53) is still manually provisioned. Consider adding Terraform definitions for these resources.

### ~~1b. DynamoDB Locking Reference~~ (Resolved)

**Status:** ~~Documentation referenced DynamoDB for Terraform state locking.~~

**Resolution:** `backend.tf` uses `use_lockfile = true` (S3-native locking), not DynamoDB. Docs updated to reflect this.

### 2. No Automated Release Pipeline

**Status:** The release process is a manual 13-step checklist (`scripts/release_steps.md`) spanning 7 artifact registries and multiple AWS services. All source code is now in the monorepo, but the release process still has significant risk of human error, inconsistency, or partial releases.

**Impact:** Releases are slow, error-prone, and cannot be delegated. The process includes force-push recovery steps, suggesting failures are not uncommon.

**Recommendation:**
- Automate the release pipeline (at minimum: Maven Central release, Docker image build, Helm chart publish, website deploy)
- Use GitHub Actions release workflows triggered by version tags
- Add release verification tests (smoke tests against published artifacts)

### 3. Missing API Documentation

**Status:** The OpenAPI spec (`mock-server-openapi-embedded-model.yaml`) exists in `mockserver-core` resources but is only published to SwaggerHub manually. There is no auto-generated API documentation in the repository or website.

**Recommendation:**
- Auto-generate API docs from the OpenAPI spec during the build
- Include API documentation in the Jekyll website
- Automate SwaggerHub publishing

### 4. No Runbook for Operational Issues

**Status:** AWS infrastructure debugging commands exist in `.buildkite/buildkite.md` and AGENTS.md, but there is no structured runbook for common operational scenarios.

**Recommendation:** Create `docs/runbook.md` covering:
- Buildkite agents not starting (ASG/Lambda troubleshooting)
- CI builds failing with OOM (memory tuning)
- Website not updating after deploy (CloudFront cache)
- Docker Hub push failures
- Maven Central release failures (staging repo cleanup)

## Moderate Gaps

### 5. Inconsistent Version References

**Status:** Version numbers are hardcoded in multiple locations:

| Location | Example |
|----------|---------|
| `pom.xml` | `5.15.1-SNAPSHOT` |
| `_config.yml` | `5.15.0` |
| `Chart.yaml` | `5.15.0` |
| `values.yaml` | image tag defaults |
| `Dockerfiles` | `VERSION=RELEASE` or `5.15.1-SNAPSHOT` |
| `release_steps.md` | `5.15.0`, `5.15.1-SNAPSHOT` |

**Recommendation:**
- Document all locations that need version updates during release (add to `docs/operations/release-process.md`)
- Consider scripting the version bump (`scripts/bump_version.sh`)

### 6. No Contributor Architecture Guide

**Status:** `CONTRIBUTING.md` exists but does not explain the codebase architecture, module relationships, or where to make changes for different types of contributions.

**Recommendation:**
- Link to `docs/architecture.md` from `CONTRIBUTING.md`
- Add a "Where to make changes" section mapping feature types to modules

### ~~7. No Testing Documentation~~ (Resolved)

**Status:** ~~The test infrastructure is complex (unit tests, integration tests, container integration tests, performance tests) but undocumented.~~

**Resolution:** Created `docs/testing.md` covering unit tests, integration tests, container integration tests, performance tests, test naming conventions, helper scripts, environment variable controls, and CI execution.

### ~~8. No Security Documentation~~ (Resolved)

**Status:** ~~`SECURITY.md` exists but only covers reporting policy. There is no documentation of security features from an architectural perspective.~~

**Resolution:** `docs/code/tls-and-security.md` now comprehensively covers BouncyCastle CA, SNI, mTLS, JWT auth (with all 15 supported JWS algorithms), control plane security, and authentication classes (8 classes documented).

### 9. Helm Chart Repo Hosting

**Status:** Helm charts are hosted on the same S3 bucket as the website. The chart repository index (`index.yaml`) is manually regenerated and uploaded.

**Recommendation:**
- Consider using GitHub Releases or OCI registry for chart hosting
- Automate chart packaging and index regeneration in CI

### 10. ~~Build Image Staleness~~ (Resolved)

**Status:** ~~The `mockserver/mockserver:maven` CI build image pre-fetches dependencies by cloning and building the repo. This image is not automatically rebuilt when dependencies change, potentially causing CI cache misses.~~

**Resolution:** The Maven CI image is built and pushed by the Buildkite pipeline `.buildkite/docker-push-maven.yml` (manual trigger). The image has been modernised from Ubuntu 22.10 + JDK 8 to Ubuntu 24.04 + JDK 21 + Maven 3.9.15. Docker Hub credentials are managed via AWS Secrets Manager (`mockserver-build/dockerhub`), with access granted to Buildkite agents via IAM policy.

**Remaining:** Consider tagging build images with a dependency hash for better cache invalidation when `pom.xml` changes.

## Minor Gaps

### 11. Docker Compose Examples Not Cross-Referenced

The 10 Docker Compose examples in `mockserver-examples/docker_compose_examples/` duplicate the integration test configurations in `container_integration_tests/`. Changes in one are not automatically reflected in the other.

### 12. Deprecated Packaging Formats

The Debian package (`dput.sh`) and Upstart init script suggest legacy Linux distribution support that may no longer be maintained. Consider documenting the supported deployment targets.

### 13. Missing `.gitignore` Entries

The `docs/` directory should be tracked in git. Verify that `.gitignore` does not exclude it.

### 14. No Dependency Update Automation

No Dependabot or Renovate configuration exists for automated dependency update PRs (although `.github/dependabot.yml` may exist for GitHub Actions only).

**Recommendation:** Enable Dependabot for Maven dependencies with conservative update policy.

## Documentation Coverage Matrix

| Area | Status | Document |
|------|--------|----------|
| Code architecture | Documented | [code/overview.md](code/overview.md) |
| Maven build system | Documented | [operations/build-system.md](operations/build-system.md) |
| CI/CD pipelines | Documented | [infrastructure/ci-cd.md](infrastructure/ci-cd.md) |
| AWS infrastructure | Documented | [infrastructure/aws-infrastructure.md](infrastructure/aws-infrastructure.md) |
| Docker images | Documented | [infrastructure/docker.md](infrastructure/docker.md) |
| Helm charts | Documented | [infrastructure/helm.md](infrastructure/helm.md) |
| Website structure | Documented | [operations/website.md](operations/website.md) |
| Dependencies | Documented | [operations/dependencies.md](operations/dependencies.md) |
| Release process | Documented | [operations/release-process.md](operations/release-process.md) |
| Testing strategy | Documented | [testing.md](testing.md) |
| Security architecture | Documented | [code/tls-and-security.md](code/tls-and-security.md) |
| Metrics & monitoring | Documented | [code/metrics.md](code/metrics.md) |
| Operational runbook | **Missing** | Recommended: `docs/runbook.md` |
| Infrastructure as Code | **Partial** | `terraform/buildkite-agents/` (website IaC still missing) |
| API documentation | **Partial** | OpenAPI spec exists, not integrated |
| Performance tuning | **Partial** | Website covers it, no internal docs |
| Configuration reference | **Partial** | `mockserver.example.properties` exists |
