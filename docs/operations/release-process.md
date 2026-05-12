# Release Process

## Automated Release Pipeline

The release process has been automated via a Buildkite pipeline and shared scripts. See [Release Pipeline Plan](../plans/release-pipeline.md) for the full design.

### Quick Start

**Via Buildkite (recommended):**
1. Trigger the "MockServer Release" pipeline in Buildkite
2. Enter release version, next SNAPSHOT version, and previous version
3. Enter TOTP code when prompted
4. Approve gates at each stage

**Via local orchestrator:**
```bash
./scripts/release.sh 5.16.0 5.16.1-SNAPSHOT 5.15.0
```

### Key files
- `scripts/ci/release/` — 26 standalone release scripts
- `scripts/release.sh` — local orchestrator
- `.buildkite/release-pipeline.yml` — Buildkite pipeline with approval gates
- `terraform/buildkite-agents/build-secrets.tf` — AWS Secrets Manager secrets
- `terraform/website/` — website infrastructure (S3, CloudFront, cross-account IAM)

### Deferred setup (secrets)
Before the first release, store credentials in AWS Secrets Manager:
- `mockserver-release/gpg-key` — GPG private key + passphrase
- `mockserver-release/github-token` — GitHub PAT
- `mockserver-release/totp-seed` — TOTP shared secret
- `mockserver-release/npm-token` — npm automation token
- `mockserver-release/swaggerhub` — SwaggerHub API key
- `mockserver-release/website-role` — Cross-account IAM role ARN

---

## Legacy Manual Process

The legacy manual process is documented below for reference.

MockServer releases were a manual 15-step process spanning multiple registries and hosting platforms.

```mermaid
flowchart TD
    START([Start Release]) --> MVN_REL[1. Maven Release to Central]
    MVN_REL --> MVN_SNAP[2. Deploy new SNAPSHOT]
    MVN_SNAP --> UPDATE_REPO[3. Update repo versions]
    UPDATE_REPO --> NODE[4. Update mockserver-node]
    NODE --> CLIENT_NODE[5. Update mockserver-client-node]
    CLIENT_NODE --> MVN_PLUGIN[6. Update mockserver-maven-plugin]
    MVN_PLUGIN --> DOCKER[7. Update Docker image]
    DOCKER --> HELM[8. Update Helm chart]
    HELM --> JAVADOC[9. Add Javadoc]
    JAVADOC --> SWAGGER[10. Update SwaggerHub]
    SWAGGER --> WEBSITE[11. Update www.mock-server.com]
    WEBSITE --> VERSIONED[12. Create versioned website copy]
    VERSIONED --> HOMEBREW[13. Update Homebrew]
    HOMEBREW --> PYTHON[14. Publish Python client to PyPI]
    PYTHON --> RUBY[15. Publish Ruby client to RubyGems]
    RUBY --> DONE([Release Complete])
```

## Step Details

### 1. Publish Release to Maven Central

```bash
./scripts/local_release.sh
```

Then in the Sonatype UI:
1. Go to https://oss.sonatype.org/index.html#stagingRepositories
2. Find the staging repository
3. **Close** the repository (triggers validation)
4. **Release** the repository (auto-drops after sync to Central)

### 2. Publish New SNAPSHOT

```bash
./scripts/local_deploy_snapshot.sh
```

Deploys the next SNAPSHOT version to Sonatype snapshots repository.

### 3. Update Repository

1. Update `changelog.md`
2. Update `README.md`
3. Clean build artifacts: `cd mockserver && ./mvnw clean && cd .. && rm -rf jekyll-www.mock-server.com/_site`
4. Update `jekyll-www.mock-server.com/_config.yml` with new version numbers
5. Find-and-replace version references across the codebase:
   - Release version (e.g., `5.16.0`)
   - API version (e.g., `5.16.x`)
   - SNAPSHOT version (e.g., `5.16.0-SNAPSHOT` → `5.16.1-SNAPSHOT`)
6. Update client library versions:
   - `mockserver-client-python/pyproject.toml` — `version` field
   - `mockserver-client-ruby/lib/mockserver/version.rb` — `VERSION` constant
   - `mockserver-client-ruby/README.md` — gem version reference
7. Commit and push

### 4. Update mockserver-node (Monorepo)

```bash
cd mockserver-node
rm -rf package-lock.json node_modules
# Update version references
nvm use v16.14.1
npm i && npm audit fix
grunt
npm login
npm publish --access=public --otp=****
cd ..
```

### 5. Update mockserver-client-node (Monorepo)

Same process as step 4, but in `mockserver-client-node/` and without `npm audit fix`.

### 6. Update mockserver-maven-plugin (Monorepo)

1. Update parent POM, jar-with-dependencies, and integration-testing versions from SNAPSHOT to RELEASE in `mockserver-maven-plugin/pom.xml`
2. Deploy snapshot: `./scripts/local_deploy_snapshot.sh`
3. Release: `./scripts/local_release.sh`
4. Close and release on Sonatype
5. Update versions back to new SNAPSHOT
6. Deploy new snapshot

### 7. Update Docker Image

```mermaid
flowchart LR
    CHECK[Verify JAR on Maven Central] --> BK["Trigger Buildkite
docker-push-release"]
    BK --> DH["Docker Hub
mockserver/mockserver"]
    BK --> ECR["ECR Public
public.ecr.aws/mockserver/mockserver"]
```

1. Verify the release JAR is available:
   ```bash
   curl -v https://oss.sonatype.org/service/local/artifact/maven/redirect\?r\=releases\&g\=org.mock-server\&a\=mockserver-netty\&c\=shaded\&e\=jar\&v\=RELEASE
   ```
2. Trigger the Buildkite `docker-push-release` pipeline with `RELEASE_TAG=mockserver-X.Y.Z`
3. The pipeline pushes to both Docker Hub and AWS ECR Public, including standard and `-graaljs` variants

### 8. Update Helm Chart

```bash
# Update version in Chart.yaml
cd helm
helm package ./mockserver/
mv mockserver-X.Y.Z.tgz charts/
cd charts
helm repo index .
# Upload chart + index.yaml to S3 bucket
```

### 9. Add Javadoc

```bash
git checkout mockserver-X.Y.Z
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
cd mockserver && ./mvnw javadoc:aggregate -P release \
  -DreportOutputDirectory='/path/to/javadoc/X.Y.Z'
# Upload to S3 bucket under /versions/X.Y.Z/
git checkout master
```

### 10. Update SwaggerHub

1. Update OpenAPI spec in `mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml`
2. Create new version on https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi
3. Publish the version

### 11. Update www.mock-server.com

```bash
cd jekyll-www.mock-server.com
rm -rf _site
bundle exec jekyll build
# Upload _site/ to the main website S3 bucket (see ~/mockserver-aws-ids.md)
# Invalidate CloudFront cache for main distribution (see ~/mockserver-aws-ids.md, path /*)
```

### 12. Create Versioned Website Copy (Major/Minor Releases)

For significant releases, create a versioned copy of the documentation:

1. Create new S3 bucket with public access **blocked** (all 4 flags)
2. Enable server-side encryption (AES-256)
3. Upload built `_site/` to new bucket
4. Create CloudFront distribution with OAC (copy existing settings, set default root object to `index.html`)
5. Set bucket policy to allow only the new CloudFront distribution via `cloudfront.amazonaws.com` service principal
6. Create Route53 A record aliased to new CloudFront distribution

See `~/mockserver-aws-ids.md` for the OAC ID to attach to the new distribution.

### 13. Update Homebrew

```bash
brew doctor
# Fork/reset homebrew-core
brew update
HOMEBREW_GITHUB_API_TOKEN=<token> \
  brew bump-formula-pr --strict mockserver \
  --url="https://search.maven.org/remotecontent?filepath=org/mock-server/mockserver-netty/X.Y.Z/mockserver-netty-X.Y.Z-brew-tar.tar"
```

## Release Artifacts Summary

```mermaid
graph LR
    REL([Release X.Y.Z])

    REL --> MC["Maven Central
JARs, POMs, Sources, Javadoc"]
    REL --> DH["Docker Hub
Multi-arch images"]
    REL --> ECR["ECR Public
Multi-arch images"]
    REL --> NPM["npm Registry
mockserver-node
mockserver-client-node"]
    REL --> S3_HELM["S3/Helm Repo
Helm chart .tgz"]
    REL --> S3_DOCS["S3/CloudFront
Website + Javadoc"]
    REL --> SWAGGER["SwaggerHub
OpenAPI spec"]
    REL --> BREW["Homebrew
Formula PR"]
    REL --> PYPI["PyPI
mockserver-client"]
    REL --> GEMS["RubyGems
mockserver-client"]
```

### 14. Publish Python Client to PyPI

Prerequisites:
- `build` and `twine` installed: `pip install build twine`
- AWS SSO session active: `aws sso login --profile mockserver-build`
- PyPI API token stored in AWS Secrets Manager (`mockserver-build/pypi`)

```bash
./scripts/release_python.sh
```

The script fetches the PyPI token from Secrets Manager, builds the package, verifies it, and uploads to PyPI. The published package will appear at https://pypi.org/project/mockserver-client/.

### 15. Publish Ruby Client to RubyGems

Prerequisites:
- AWS SSO session active: `aws sso login --profile mockserver-build`
- RubyGems API key stored in AWS Secrets Manager (`mockserver-build/rubygems`)

```bash
./scripts/release_ruby.sh
```

The script fetches the RubyGems API key from Secrets Manager, builds the gem, and pushes to RubyGems. The published gem will appear at https://rubygems.org/gems/mockserver-client.

## Cleaning Up a Failed Release

If a release fails partway through:

```bash
# Revert git to pre-release state
git reset --hard <commit-hash>
git push --force

# Delete the release tag
git tag -d mockserver-X.Y.Z
git push origin :refs/tags/mockserver-X.Y.Z

# Drop staging repository on Sonatype
# https://oss.sonatype.org/#stagingRepositories
```

## GPG Signing Setup

Release signing requires GPG configuration. See `scripts/deploy.md` for:
- GPG key setup
- Sonatype `settings.xml` configuration
- Troubleshooting `gpg: signing failed: Inappropriate ioctl for device` (fix: `export GPG_TTY=$(tty)`)
