# Release Process

> Read [release-principles.md](release-principles.md) first if you're modifying anything in this pipeline. The principles are load-bearing: ignore them and you'll re-create the tight CI coupling we just removed.

## Architecture

```
scripts/release/
├── _lib.sh                       # shared functions: logging, dry-run, AWS,
│                                 # git, docker wrapper, version helpers
├── release.sh                    # orchestrator (prepare → components → finalize)
├── prepare.sh                    # validate + bump pom + tag + push
├── finalize.sh                   # SNAPSHOT bump + update version references
├── preflight.sh                  # verify host has docker + bash + git + jq …
└── components/                   # one script per deployable artifact
    ├── maven-central.sh          # build + sign + Sonatype + publish + wait
    ├── maven-plugin.sh           # mockserver-maven-plugin release
    ├── docker.sh                 # multi-arch Docker Hub + ECR Public
    ├── npm.sh                    # mockserver-node + mockserver-client-node
    ├── pypi.sh                   # mockserver-client-python
    ├── rubygems.sh               # mockserver-client (Ruby)
    ├── helm.sh                   # Helm chart
    ├── javadoc.sh                # Javadoc to S3
    ├── website.sh                # Jekyll site
    ├── schema.sh                 # JSON Schema
    ├── swaggerhub.sh             # OpenAPI spec to SwaggerHub
    ├── github.sh                 # GitHub Release
    └── versioned-site.sh         # X-Y.mock-server.com Terraform

.buildkite/scripts/
├── release-runner.sh             # Buildkite adapter (meta-data → env vars)
└── release-verify-totp.sh        # Buildkite-only TOTP gate

.buildkite/release-pipeline.yml   # flat list of steps; each step is one
                                  # release-runner.sh invocation
```

## How a release happens

### Step-by-step, locally

```bash
# 1. Verify your machine has the required host tools.
./scripts/release/preflight.sh

# 2. Run the entire pipeline in dry-run mode. Builds everything, but skips
#    every external write (npm publish, twine upload, S3 sync, gh release
#    create, git push, etc.).
./scripts/release/release.sh --version 6.0.0 --dry-run

# 3. Run a single component.
./scripts/release/components/npm.sh --dry-run        # exits with `RELEASE_VERSION` unset
RELEASE_VERSION=6.0.0 ./scripts/release/components/npm.sh --dry-run

# 4. Run only a few components.
./scripts/release/release.sh --version 6.0.0 --only=npm,pypi --dry-run

# 5. Skip components.
./scripts/release/release.sh --version 6.0.0 --skip=docker --dry-run
```

DRY_RUN defaults to `true` unless you pass `--execute`. **Locally you almost never want `--execute`** — that publishes for real.

### Step-by-step, on Buildkite

The same scripts run; the only difference is the wrapper:

1. Operator triggers the `mockserver-release` pipeline.
2. The input step collects: release version, next SNAPSHOT, type, versioned-site flag.
3. The TOTP block prompts for a 6-digit code; `release-verify-totp.sh` validates it.
4. Each subsequent step calls `.buildkite/scripts/release-runner.sh <stage>`, which:
   1. Reads Buildkite meta-data and exports it as `RELEASE_VERSION`, `NEXT_VERSION`, etc.
   2. Sets `DRY_RUN=false` (Buildkite releases for real).
   3. `exec`s the matching script under `scripts/release/`.

The release scripts themselves see only env vars. They have no idea Buildkite exists.

## Disaster recovery

Buildkite outage on release day? No problem. From a developer machine with `docker`, `aws`, `git`, `jq`, `python3`, and `bash`:

```bash
# Authenticate to AWS (for Secrets Manager + S3)
aws sso login --profile mockserver-build

# Run the same scripts the CI would have run
RELEASE_VERSION=6.0.0 \
NEXT_VERSION=6.0.1-SNAPSHOT \
RELEASE_TYPE=full \
CREATE_VERSIONED_SITE=yes \
./scripts/release/release.sh --execute
```

Every component runs in the same pinned Docker image whether you're on a laptop or a CI agent. There is no implicit CI state to recreate.

## Switching CI providers

This pipeline assumes nothing about Buildkite. If you want to run it on GitHub Actions, write a 30-line `.github/workflows/release.yml` that:

1. Receives release inputs (`workflow_dispatch` with `release_version` etc.).
2. Exports them as env vars.
3. Calls `./scripts/release/release.sh --execute` (or one component at a time, with explicit job dependencies).

Same for any other CI provider. The release scripts don't change.

## The contract

Release scripts (`scripts/release/*`) read these env vars:

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `RELEASE_VERSION` | yes | — | The version being released (X.Y.Z) |
| `NEXT_VERSION` | no | `RELEASE_VERSION` patch+1 -SNAPSHOT | Next dev version |
| `OLD_VERSION` | no | latest `mockserver-X.Y.Z` tag | Previous release |
| `RELEASE_TYPE` | no | `full` | One of: full, maven-only, docker-only, post-maven |
| `CREATE_VERSIONED_SITE` | no | `no` | `yes` for major/minor releases |
| `DRY_RUN` | no | `true` | `false` to actually publish |
| `AWS_PROFILE` | no | (not set) | Used outside CI for Secrets Manager auth |

No other vars are read. **No `BUILDKITE_*` lookups happen in release scripts** — that's the whole point.

## Dry-run behaviour by component

| Component | Dry-run does | Dry-run skips |
|---|---|---|
| `prepare` | Validate inputs, show pom diff | pom write, git commit, tag, push |
| `maven-central` | `mvn clean install` (build + test) | Sonatype upload, publish, sync wait |
| `maven-plugin` | Build core + verify plugin | tag, deploy, snapshot bump, push |
| `docker` | `docker buildx build` (local `--load`, amd64 only) | `--push` to Docker Hub + ECR |
| `npm` | `npm install`, grunt build | `git push tag`, `npm publish` (uses `--dry-run`) |
| `pypi` | `python -m build`, `twine check` | `twine upload` |
| `rubygems` | `gem build` | `gem push` |
| `helm` | `helm lint`, `helm package` | S3 upload, commit/push |
| `javadoc` | `mvn javadoc:aggregate` | S3 sync |
| `website` | `bundle install`, `jekyll build` | S3 sync, CloudFront invalidation |
| `schema` | jq-generate self-contained schemas | S3 sync |
| `swaggerhub` | Validate spec file | POST to SwaggerHub |
| `github` | Extract changelog notes, print preview | `gh release create` |
| `versioned-site` | `terraform plan` | `terraform apply`, S3 mirror |
| `finalize` | Show diff of version-reference rewrite | git push, mvn deploy snapshot |

## Pinned Docker images

All toolchain calls run inside these images. Defined in `scripts/release/_lib.sh`:

```bash
MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-11
NODE_IMAGE=node:20-bookworm
RUBY_IMAGE=ruby:3.2-bookworm
HELM_IMAGE=alpine/helm:3.16.2
GH_IMAGE=maniator/gh:v2.62.0
PYTHON_IMAGE=python:3.12-slim-bookworm
TERRAFORM_IMAGE=hashicorp/terraform:1.9
```

Override any of them by exporting the corresponding env var. Change them in `_lib.sh` to update for everyone.

## Common operations

### Re-run a single component after a partial-pipeline failure

If, say, the Maven Central step succeeded but `npm` failed:

```bash
# On Buildkite: open the build, click Retry on the failed step. The
# release-runner.sh adapter re-reads meta-data and re-invokes.

# Locally:
RELEASE_VERSION=6.0.0 ./scripts/release/components/npm.sh --execute
```

### Reproduce a CI failure locally

```bash
# Pull the same env vars Buildkite was using (or set them by hand) and run
# the same script.
RELEASE_VERSION=6.0.0 \
NEXT_VERSION=6.0.1-SNAPSHOT \
./scripts/release/components/maven-central.sh --dry-run
```

That reproduces what the agent was doing, in the same Docker image, on your laptop.

### Add a new deployable component

1. Create `scripts/release/components/<name>.sh` following the pattern of an existing component.
2. Wire it into the orchestrator: add `<name>` to `ALL_COMPONENTS` in `release.sh`.
3. Add a step to `.buildkite/release-pipeline.yml` that runs `.buildkite/scripts/release-runner.sh <name>`.
4. Test with `RELEASE_VERSION=X.Y.Z ./scripts/release/components/<name>.sh --dry-run`.

## For agents / LLMs reading this in a future session

If you're modifying this pipeline, **respect the principles** in [release-principles.md](release-principles.md). In particular:

- Do NOT add `buildkite-agent meta-data get` or any `BUILDKITE_*` env-var reads to a script under `scripts/release/`. If a release script needs information that currently comes from Buildkite meta-data, plumb it through as a regular env var via the adapter.
- Do NOT call any tool natively if it has an upstream Docker image. The whole pipeline relies on language toolchains being containerised so the agents stay minimal and the scripts stay portable.
- Do NOT introduce dynamic pipeline generation (the previous design did this and we explicitly removed it). The Buildkite YAML is meant to be flat and obvious.
- DO add `--dry-run` support to every new component. The smoke-test pattern (`RELEASE_VERSION=99.99.0 ./scripts/release/components/<name>.sh --dry-run`) is how operators sanity-check changes locally before triggering CI.
- DO write each component as one self-contained file: build, package, sign, publish — all in one place. No splitting across multiple steps.

When in doubt, ask: "could a human ship this release from their laptop with just `docker`, `aws`, `git`, and `bash` installed?" If the answer is no, you've broken a principle.
