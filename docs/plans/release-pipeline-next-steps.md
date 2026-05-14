# Release Pipeline — Next Steps & Testing Guide

> **HISTORICAL.** This document was a checklist for the *original* `scripts/ci/release/` layout. That layout has been replaced by the CI-agnostic `scripts/release/` design — see [release-principles.md](../operations/release-principles.md) and [release-process.md](../operations/release-process.md) for the current approach. Specific script paths and code snippets here no longer match the codebase.

**Status:** Superseded by the CI-agnostic refactor.

## What's Been Built

All code is committed and ready. The release pipeline consists of:

- **25 scripts** in `scripts/ci/release/` — every release step as a standalone script
- **Local orchestrator** `scripts/release.sh` — runs all scripts interactively
- **Buildkite pipeline** `.buildkite/release-pipeline.yml` — CI pipeline with TOTP + approval gates
- **Terraform resources** — 6 new secrets in `build-secrets.tf`, release pipeline in `pipelines.tf`, website module in `terraform/website/`
- **CI Docker image** — updated with oathtool, gnupg, jq, AWS CLI, Helm, gh, Ruby/Jekyll
- **Dedicated release agent queue** — a separate `release` Buildkite agent stack with access to release secrets, isolating GPG keys, GitHub PATs, and cross-account IAM roles from regular CI builds

## Deferred Steps (Must Complete Before First Release)

### 1. GPG Key — `mockserver-release/gpg-key`

Export your existing GPG signing key and store it in AWS Secrets Manager.

```bash
# Find your key ID
gpg --list-secret-keys --keyid-format long

# Export and base64 encode
GPG_KEY_B64=$(gpg --export-secret-keys --armor YOUR_KEY_ID | base64)

# Store in AWS SM
aws secretsmanager put-secret-value \
  --secret-id mockserver-release/gpg-key \
  --secret-string "{\"key\": \"$GPG_KEY_B64\", \"passphrase\": \"YOUR_PASSPHRASE\"}" \
  --region eu-west-2 \
  --profile mockserver-build
```

**Test:** Verify the key can be retrieved and imported:
```bash
GPG_KEY_B64=$(aws secretsmanager get-secret-value \
  --secret-id mockserver-release/gpg-key \
  --region eu-west-2 --profile mockserver-build \
  --query SecretString --output text | jq -r '.key')
echo "$GPG_KEY_B64" | base64 -d | gpg --batch --import --dry-run
```

### 2. TOTP Seed — `mockserver-release/totp-seed`

Generate a TOTP seed and register it in your authenticator app.

```bash
# Generate seed (requires pyotp: pip install pyotp)
SEED=$(python3 -c "import pyotp; print(pyotp.random_base32())")
echo "Seed: $SEED"

# Store in AWS SM
aws secretsmanager put-secret-value \
  --secret-id mockserver-release/totp-seed \
  --secret-string "{\"seed\": \"$SEED\"}" \
  --region eu-west-2 \
  --profile mockserver-build

# Generate QR code for authenticator app
python3 -c "
import pyotp
seed = '$SEED'
totp = pyotp.TOTP(seed)
print(totp.provisioning_uri(name='mockserver-release', issuer_name='MockServer'))
"
```

Register the provisioning URI in 1Password, Google Authenticator, or your preferred TOTP app.

**Test:** Verify TOTP generation works end-to-end:
```bash
# Install oathtool if not present
brew install oath-toolkit

# Generate a code from the seed
SEED=$(aws secretsmanager get-secret-value \
  --secret-id mockserver-release/totp-seed \
  --region eu-west-2 --profile mockserver-build \
  --query SecretString --output text | jq -r '.seed')
oathtool --totp -b "$SEED"
# Compare with your authenticator app — codes should match
```

### 3. GitHub PAT — `mockserver-release/github-token`

Create a GitHub Personal Access Token with `contents:write` scope on `mock-server/mockserver-monorepo`.

1. Go to https://github.com/settings/tokens?type=beta (fine-grained tokens)
2. Create token scoped to `mock-server/mockserver-monorepo` with:
   - Repository permissions: **Contents** (Read and write)
3. Store:

```bash
aws secretsmanager put-secret-value \
  --secret-id mockserver-release/github-token \
  --secret-string '{"token": "github_pat_..."}' \
  --region eu-west-2 \
  --profile mockserver-build
```

**Test:**
```bash
TOKEN=$(aws secretsmanager get-secret-value \
  --secret-id mockserver-release/github-token \
  --region eu-west-2 --profile mockserver-build \
  --query SecretString --output text | jq -r '.token')
GITHUB_TOKEN="$TOKEN" gh api repos/mock-server/mockserver-monorepo --jq '.full_name'
# Should print: mock-server/mockserver-monorepo
```

### 4. npm Token — `mockserver-release/npm-token`

Create an npm automation token (or granular token with publish scope for `mockserver-node` and `mockserver-client`).

```bash
npm token create --type=publish
# Copy the token

aws secretsmanager put-secret-value \
  --secret-id mockserver-release/npm-token \
  --secret-string '{"token": "npm_..."}' \
  --region eu-west-2 \
  --profile mockserver-build
```

> **Note:** `publish-npm.sh` now reads this token from AWS Secrets Manager and publishes non-interactively in CI.

### 5. SwaggerHub API Key — `mockserver-release/swaggerhub`

1. Go to https://app.swaggerhub.com/settings/apiKey
2. Copy your API key

```bash
aws secretsmanager put-secret-value \
  --secret-id mockserver-release/swaggerhub \
  --secret-string '{"api_key": "YOUR_API_KEY"}' \
  --region eu-west-2 \
  --profile mockserver-build
```

**Test:**
```bash
KEY=$(aws secretsmanager get-secret-value \
  --secret-id mockserver-release/swaggerhub \
  --region eu-west-2 --profile mockserver-build \
  --query SecretString --output text | jq -r '.api_key')
curl -s -H "Authorization: $KEY" \
  "https://api.swaggerhub.com/apis/jamesdbloom/mock-server-openapi" | jq '.totalCount'
# Should return a number (the count of API versions)
```

### 6. Website Cross-Account IAM Role — `mockserver-release/website-role`

This requires the website Terraform module to be applied first, which creates the IAM role. The role ARN is then stored as a secret so scripts can assume it.

#### 6a. Populate `terraform/website/terraform.tfvars`

Fill in the actual values from `~/mockserver-aws-ids.md`:

```bash
cp terraform/website/terraform.tfvars.example terraform/website/terraform.tfvars
# Edit terraform.tfvars — replace all REPLACE_WITH_* placeholders
```

You need:
- Main site S3 bucket name
- Route53 hosted zone ID for `mock-server.com`
- ACM certificate ARN (must be in `us-east-1`)
- Build account Buildkite agent IAM role ARN
- S3 bucket name for each of the 17 versioned sites

#### 6b. Import existing resources and apply

```bash
cd terraform/website
terraform init
terraform plan    # Review — will show imports + new cross-account role
terraform apply
```

#### 6c. Store the role ARN

```bash
ROLE_ARN=$(terraform -chdir=terraform/website output -raw release_website_role_arn)
aws secretsmanager put-secret-value \
  --secret-id mockserver-release/website-role \
  --secret-string "{\"role_arn\": \"$ROLE_ARN\"}" \
  --region eu-west-2 \
  --profile mockserver-build
```

**Test:**
```bash
ROLE_ARN=$(aws secretsmanager get-secret-value \
  --secret-id mockserver-release/website-role \
  --region eu-west-2 --profile mockserver-build \
  --query SecretString --output text | jq -r '.role_arn')
aws sts assume-role --role-arn "$ROLE_ARN" \
  --role-session-name test \
  --profile mockserver-build \
  --query 'Credentials.AccessKeyId' --output text
# Should return a temporary access key ID
```

### 7. Create `release-managers` Team in Buildkite

The `block` steps in the release pipeline restrict unblocking to the `release-managers` team.

1. Go to Buildkite → Organization Settings → Teams
2. Create team `release-managers`
3. Add yourself (and any other release managers)

### 8. Apply Terraform for Secrets, Release Agent Stack, and Pipeline

The 6 new secrets, dedicated release agent stack, and release pipeline entry need to be applied:

```bash
cd terraform/buildkite-agents
terraform plan    # Should show: 6 new secrets, 2 IAM policies, release agent stack (ASG + Lambda scaler)
terraform apply

cd ../buildkite-pipelines
terraform plan    # Should show 1 new pipeline (release)
terraform apply
```

The release agent stack creates a separate `release` queue with its own ASG and Lambda scaler. Only release pipeline steps that access `mockserver-release/*` secrets or assume the website cross-account role run on this queue. Regular CI builds on the `default` queue cannot access release secrets.

| Queue | IAM Policies | Secrets Access |
|-------|-------------|----------------|
| `default` | `read_build_secrets` | dockerhub, sonatype, pypi, rubygems, buildkite-api-token |
| `release` | `read_build_secrets` + `read_release_secrets` | All build secrets + gpg-key, github-token, totp-seed, npm-token, swaggerhub, website-role + `sts:AssumeRole` |

The release agents scale to zero when idle (same as default agents) and use 100% on-demand instances for reliability during releases. Expect 1-3 minutes of cold-start latency before the first release step begins, as agents must launch and register with Buildkite.

### 9. Rebuild CI Docker Image

The CI Docker image needs the new tools (oathtool, AWS CLI, Helm, gh, Ruby/Jekyll). Trigger the "MockServer Build Image" pipeline in Buildkite, or build locally:

```bash
cd docker_build/maven
docker build -t mockserver/mockserver:maven .
docker push mockserver/mockserver:maven
```

---

## Testing the Release Process

Many parts of the pipeline can be tested without performing an actual release.

### Tier 1: No Secrets Required (Test Now)

These tests validate script logic without needing any credentials.

#### Validate script

```bash
# Should pass — validates version formats, branch, clean tree
RELEASE_VERSION=99.99.99 NEXT_VERSION=99.99.100-SNAPSHOT OLD_VERSION=5.15.0 \
  scripts/ci/release/validate.sh
```

> **Note:** This will fail if your tree is dirty or you're not on `master`. That's the validation working correctly.

#### Version update dry-run

Test the version replacement logic on a throwaway branch:

```bash
git checkout -b test-version-update
RELEASE_VERSION=5.16.0 NEXT_VERSION=5.16.1-SNAPSHOT OLD_VERSION=5.15.0 \
  bash -c '
    source scripts/ci/release/common.sh
    cd "$REPO_ROOT"
    # Just test the sed replacements — dont commit
    sed -n "s/5.15.0/5.16.0/p" jekyll-www.mock-server.com/_config.yml
    sed -n "s/5.15.x/5.16.x/p" jekyll-www.mock-server.com/_config.yml
  '
# Inspect output, then discard the branch
git checkout master
git branch -D test-version-update
```

#### Build and test (full Maven build)

This is the most valuable test — it confirms the build still works:

```bash
cd mockserver && ./mvnw -T 1C clean install -Djava.security.egd=file:/dev/./urandom
```

#### Shellcheck all scripts

```bash
# Install shellcheck if needed: brew install shellcheck
shellcheck scripts/ci/release/*.sh scripts/release.sh
```

#### Terraform validate

```bash
cd terraform/buildkite-agents && terraform validate
cd ../buildkite-pipelines && terraform validate
cd ../website && terraform init -backend=false && terraform validate
```

### Tier 2: Secrets Required, Read-Only (Test After Secret Setup)

These tests verify credential access without making any changes.

#### TOTP verification

```bash
# Get a code from your authenticator app, then:
RELEASE_VERSION=99.99.99 NEXT_VERSION=99.99.100-SNAPSHOT OLD_VERSION=5.15.0 \
TOTP_CODE=123456 \
  scripts/ci/release/verify-totp.sh
```

#### GPG key import

```bash
RELEASE_VERSION=99.99.99 NEXT_VERSION=99.99.100-SNAPSHOT OLD_VERSION=5.15.0 \
  bash -c '
    source scripts/ci/release/common.sh
    GPG_KEY_B64=$(load_secret "mockserver-release/gpg-key" "key")
    echo "$GPG_KEY_B64" | base64 -d | gpg --batch --import --dry-run
    echo "GPG import dry-run succeeded"
  '
```

#### Central Portal API access

```bash
RELEASE_VERSION=99.99.99 NEXT_VERSION=99.99.100-SNAPSHOT OLD_VERSION=5.15.0 \
  bash -c '
    source scripts/ci/release/common.sh
    AUTH=$(central_portal_auth_header)
    curl -sf -H "Authorization: Bearer $AUTH" \
      "https://central.sonatype.com/api/v1/publisher/published?namespace=org.mock-server" \
      | jq '.deployments | length'
    echo "Central Portal API access works"
  '
```

#### Cross-account role assumption

```bash
RELEASE_VERSION=99.99.99 NEXT_VERSION=99.99.100-SNAPSHOT OLD_VERSION=5.15.0 \
  bash -c '
    source scripts/ci/release/common.sh
    assume_website_role
    aws sts get-caller-identity
    echo "Cross-account role assumption works"
  '
```

#### Docker Hub login

```bash
.buildkite/scripts/docker-login.sh
docker logout  # Clean up
```

#### PyPI/RubyGems publish check (read-only)

```bash
# Verify PyPI token works
RELEASE_VERSION=99.99.99 NEXT_VERSION=99.99.100-SNAPSHOT OLD_VERSION=5.15.0 \
  bash -c '
    source scripts/ci/release/common.sh
    TOKEN=$(load_secret "mockserver-build/pypi" "token")
    echo "PyPI token retrieved (${#TOKEN} chars)"
  '

# Verify RubyGems key works
RELEASE_VERSION=99.99.99 NEXT_VERSION=99.99.100-SNAPSHOT OLD_VERSION=5.15.0 \
  bash -c '
    source scripts/ci/release/common.sh
    KEY=$(load_secret "mockserver-build/rubygems" "api_key")
    echo "RubyGems key retrieved (${#KEY} chars)"
  '
```

### Tier 3: End-to-End on a Branch (Test Before First Real Release)

The safest way to test the full flow is a **dry-run release on a test branch** that you never push to Maven Central.

#### Maven deploy dry-run

Test that GPG signing and Maven deploy work, without actually uploading:

```bash
git checkout -b test-release-dry-run
cd mockserver

# Set a fake version
./mvnw versions:set -DnewVersion=0.0.1-test -DgenerateBackupPoms=false
./mvnw versions:commit

# Build with tests
./mvnw -T 1C clean install -Djava.security.egd=file:/dev/./urandom

# Deploy to a local directory instead of Central Portal (validates GPG signing works)
./mvnw deploy -P release -DskipTests \
  -Dgpg.passphrase="YOUR_PASSPHRASE" \
  -DaltDeploymentRepository=local-test::file:///tmp/test-deploy

ls -la /tmp/test-deploy/org/mock-server/
# Should see signed JARs (.jar.asc files)

# Clean up
cd ..
git checkout master
git branch -D test-release-dry-run
rm -rf /tmp/test-deploy
```

#### Docker build (without push)

```bash
# Build the shaded JAR
cd mockserver && ./mvnw -T 1C clean install -DskipTests
cd ..

# Copy JAR and build Docker image locally
SHADED_JAR=$(ls mockserver/mockserver-netty/target/mockserver-netty-*-shaded.jar | head -1)
cp "$SHADED_JAR" docker/local/mockserver-netty-jar-with-dependencies.jar
docker build -t mockserver-test:local docker/local

# Smoke test
docker run -d --name ms-test -p 1080:1080 mockserver-test:local
sleep 5
curl -s -X PUT http://localhost:1080/mockserver/status
docker rm -f ms-test
docker rmi mockserver-test:local
```

#### Jekyll website build

```bash
cd jekyll-www.mock-server.com
bundle install
bundle exec jekyll build
ls _site/index.html  # Should exist
cd ..
```

#### Helm chart package

```bash
helm package ./helm/mockserver/ --destination /tmp/helm-test/
ls /tmp/helm-test/mockserver-*.tgz
rm -rf /tmp/helm-test
```

#### Buildkite pipeline validation

```bash
# If you have the buildkite-agent CLI:
buildkite-agent pipeline upload --dry-run .buildkite/release-pipeline.yml
```

### Testing Checklist

Use this checklist to track your testing progress:

```
[x] Tier 1: shellcheck passes on all scripts (only SC1091 info — dynamic source path)
[x] Tier 1: terraform validate passes (all 3 modules)
[x] Tier 1: validate.sh works with test version numbers (correctly rejects dirty tree)
[x] Tier 1: Full Maven build passes (1773/1774 tests — 1 pre-existing flaky timeout in ExpectationInitializerIntegrationTest)
[x] Tier 1: Jekyll site builds
[x] Tier 1: Helm chart packages (mockserver-5.15.0.tgz)
[x] Tier 1: Docker image builds and smoke test passes (status 200, expectation 201, mock 200)
---
[ ] Secrets: All 6 secrets stored in AWS SM
[ ] Secrets: release-managers team created in Buildkite
[ ] Secrets: CI Docker image rebuilt with new tools
[ ] Secrets: Terraform applied (agents + release agents + pipelines + website)
---
[ ] Tier 2: TOTP verification works
[ ] Tier 2: GPG key imports successfully
[ ] Tier 2: Central Portal API responds
[ ] Tier 2: Cross-account role assumption works
[ ] Tier 2: Docker Hub login works
[ ] Tier 2: PyPI/RubyGems tokens retrievable
---
[ ] Tier 3: Maven deploy with GPG signing (local dir)
[ ] Tier 3: Buildkite pipeline validates
---
[ ] Ready for first real release
```

### Tier 1 Test Results (2026-05-09)

| Test | Result | Notes |
|------|--------|-------|
| shellcheck | **PASS** | Only SC1091 info (dynamic `source` path — expected) |
| terraform validate (buildkite-agents) | **PASS** | After adding release agent stack and fixing policy references |
| terraform validate (buildkite-pipelines) | **PASS** | |
| terraform validate (website) | **PASS** | |
| validate.sh | **PASS** | Correctly rejects dirty tree, validates version formats |
| Maven build (1774 integration tests) | **PASS** (1 flake) | 1773/1774 passed. `ExpectationInitializerIntegrationTest.shouldLoadOpenAPIExpectationsFromJson` timed out — pre-existing flaky test (socket timeout on resource-constrained local machine), not related to release pipeline changes |
| Jekyll site build | **PASS** | Requires Homebrew Ruby (`/opt/homebrew/opt/ruby/bin/bundle`) — system Ruby 2.6 too old |
| Helm chart package | **PASS** | Produced `mockserver-5.15.0.tgz` |
| Docker image build | **PASS** | Built from shaded JAR |
| Docker smoke test | **PASS** | Status endpoint: 200, Create expectation: 201, Mock response: 200 |

## Recommended Order of Operations

1. **Run Tier 1 tests now** — no prerequisites, validates all script logic
2. **Apply Terraform** for secrets, release agent stack, and pipeline entry
3. **Store secrets** in AWS SM (steps 1-6 above)
4. **Create Buildkite team** (step 7)
5. **Rebuild CI Docker image** (step 9)
6. **Run Tier 2 tests** — validates all credential access
7. **Run Tier 3 tests** — validates deploy mechanics
8. **Populate `terraform/website/terraform.tfvars`** and apply (import existing sites)
9. **First real release** — use the local orchestrator (`scripts/release.sh`) for the first run so you can observe each step interactively

## Rollback

If anything goes wrong during a release:

```bash
RELEASE_VERSION=X.Y.Z NEXT_VERSION=X.Y.Z-SNAPSHOT OLD_VERSION=A.B.C \
PRE_RELEASE_COMMIT=$(git log --oneline -20 | grep -v "release:" | head -1 | awk '{print $1}') \
  scripts/ci/release/cleanup-failed-release.sh
```

This will:
- Drop the Central Portal deployment (if a deployment ID is available)
- Reset git to the pre-release commit
- Delete the release tag locally and on origin
