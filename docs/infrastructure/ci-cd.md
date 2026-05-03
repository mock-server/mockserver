# CI/CD

## Overview

MockServer uses two CI/CD systems:

```mermaid
graph LR
    subgraph "Buildkite"
        BK[Primary CI<br/>Build & Test]
    end

    subgraph "GitHub Actions"
        GA1[Docker Image Build<br/>Multi-arch push]
        GA2[CodeQL Analysis<br/>Security scanning]
        GA3[Maven CI Image Build<br/>Build image push]
    end

    BK -->|runs on| EC2[AWS EC2 Agents]
    GA1 -->|pushes to| DH[Docker Hub]
    GA2 -->|reports to| GH[GitHub Security]
    GA3 -->|pushes to| DH
```

## Buildkite Pipeline

**File:** `.buildkite/pipeline.yml`

The pipeline has two sequential steps (separated by an explicit `- wait` directive):

```mermaid
sequenceDiagram
    participant BK as Buildkite
    participant Agent as EC2 Agent
    participant Docker as Docker (maven image)
    participant Maven as Maven Build

    BK->>Agent: Trigger build
    Agent->>Docker: docker pull mockserver/mockserver:maven
    Agent->>Docker: docker run (volume mount repo)
    Docker->>Maven: scripts/buildkite_quick_build.sh
    Maven->>Maven: ./mvnw clean install
    Maven-->>BK: Collect **/*.log artifacts
```

### Step 1: Update Docker Image

```yaml
- label: "update docker image"
  command: "docker pull mockserver/mockserver:maven"
```

Pulls the latest `mockserver/mockserver:maven` build image to ensure the CI environment is current.

### Step 2: Build

```yaml
- label: "build"
  command: "docker run -v $(pwd):/build/mockserver -w /build/mockserver \
    -a stdout -a stderr \
    -e BUILDKITE_BRANCH=$BUILDKITE_BRANCH \
    mockserver/mockserver:maven \
    /build/mockserver/scripts/buildkite_quick_build.sh"
  artifact_paths:
    - "**/*.log"
```

Runs the full Maven build inside the `mockserver/mockserver:maven` Docker image:

- Volume-mounts the repository into the container
- Passes the `BUILDKITE_BRANCH` environment variable
- Executes `scripts/buildkite_quick_build.sh` which runs `./mvnw clean install`
- JVM memory: `-Xms2048m -Xmx8192m`
- Collects all `.log` files as build artifacts

### Build Docker Image

The `mockserver/mockserver:maven` image is defined in `docker_build/maven/Dockerfile`:

- Base: Ubuntu 24.04 (Noble)
- JDK: OpenJDK 21
- Maven: 3.9.15 (manually installed from Apache)
- Dependencies: Pre-fetched by running a throwaway build during image creation
- Corporate CA: Optional certificate injection for TLS proxy environments (see [Docker](docker.md#maven-ci-image))

## GitHub Actions

### Docker Image Build & Push

**File:** `.github/workflows/build-docker-image.yml`

**Triggers:**
- Push of `mockserver-*` tags (e.g., `mockserver-5.15.0`)
- Manual `workflow_dispatch` with custom tag override

```mermaid
flowchart TD
    T{Trigger} -->|Tag push| AUTO[Auto-generate tags<br/>mockserver-X.Y.Z + X.Y.Z]
    T -->|Manual dispatch| MANUAL[Use provided tags]

    AUTO --> QEMU[Setup QEMU<br/>Multi-arch support]
    MANUAL --> QEMU

    QEMU --> BUILDX[Setup Docker Buildx]
    BUILDX --> LOGIN[Login to Docker Hub]
    LOGIN --> BUILD[Build & Push<br/>linux/amd64 + linux/arm64]
    BUILD --> DH[Docker Hub<br/>mockserver/mockserver]
```

**Tag generation:** From a git tag like `mockserver-5.15.0`, two Docker tags are created:
- `mockserver/mockserver:mockserver-5.15.0`
- `mockserver/mockserver:5.15.0`

**Platforms:** `linux/amd64` and `linux/arm64` (via QEMU emulation)

**Dockerfile:** `docker/Dockerfile` (see [Docker documentation](docker.md))

### Maven CI Image Build & Push

**File:** `.github/workflows/build-maven-ci-image.yml`

**Triggers:**
- Push to `master` when `docker_build/maven/**` changes
- Monthly schedule (1st of month, 06:00 UTC) for base OS security updates
- Manual `workflow_dispatch`

Builds and pushes the `mockserver/mockserver:maven` CI image to Docker Hub. This is the image used by the Buildkite pipeline to compile and test the project. The image is built for `linux/amd64` only (matching the Buildkite EC2 agents).

**Docker Hub credentials** are stored as GitHub Actions secrets (`DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`). AWS Secrets Manager infrastructure for centralised credential management is provisioned in `terraform/buildkite-agents/build-secrets.tf` for future use.

### CodeQL Security Analysis

**File:** `.github/workflows/codeql-analysis.yml`

**Triggers:**
- Push to `master`
- Pull requests targeting `master`
- Weekly schedule: Tuesdays at 22:00 UTC

**Languages scanned:** Java, JavaScript

**Process:** Uses GitHub's CodeQL autobuild to compile Java sources, then runs static analysis queries to detect security vulnerabilities.

### GitHub Actions Versions

| Workflow | Action | Version |
|----------|--------|---------|
| `build-maven-ci-image.yml` | `actions/checkout` | `v4` |
| `build-maven-ci-image.yml` | `docker/setup-buildx-action` | `v3` |
| `build-maven-ci-image.yml` | `docker/login-action` | `v3` |
| `build-maven-ci-image.yml` | `docker/build-push-action` | `v5` |
| `build-docker-image.yml` | `actions/checkout` | `v3` |
| `build-docker-image.yml` | `docker/metadata-action` | `v4` |
| `build-docker-image.yml` | `docker/setup-qemu-action` | `v2` |
| `build-docker-image.yml` | `docker/setup-buildx-action` | `v2` |
| `build-docker-image.yml` | `docker/login-action` | `v2` |
| `build-docker-image.yml` | `docker/build-push-action` | `v4` |
| `codeql-analysis.yml` | `actions/checkout` | `v3` |
| `codeql-analysis.yml` | `github/codeql-action/*` | `v2` |

Note: `build-maven-ci-image.yml` uses newer action versions than the other workflows.

## Build Agent Infrastructure

See [AWS Infrastructure](aws-infrastructure.md) for details on the Buildkite agent EC2 instances, AutoScaling Group, and Lambda-based autoscaler.

## Buildkite CLI Access

The Buildkite CLI (`bk`) provides authenticated access to builds, pipelines, and agents from the terminal. It uses browser-based OAuth login (similar to `aws sso login`) — no long-lived API tokens to manage.

### Install

```bash
brew tap buildkite/buildkite
brew install buildkite/buildkite/bk
```

Or download a binary from the [GitHub releases page](https://github.com/buildkite/cli/releases).

### Authenticate

```bash
bk auth login
```

This opens a browser window for OAuth login to Buildkite (similar to `aws sso login`). Once authenticated, the CLI stores credentials in the macOS keychain. No API token creation or manual secret management required.

After login, select the organization:

```bash
bk auth switch mockserver
```

### Verify

```bash
bk auth status
```

### Common Operations

The `bk` CLI uses `-p {pipeline}` for pipeline selection. The organization is set globally via `bk auth switch`.

```bash
# List recent builds
bk build list -p mockserver

# View a specific build
bk build view 3292 -p mockserver

# View a build as JSON
bk build view 3292 -p mockserver --json

# Cancel a build
bk build cancel 3292 -p mockserver -y

# Rebuild (retrigger) a build
bk build rebuild 3292 -p mockserver -y

# List agents (across all pipelines in the org)
bk agent list

# List agents as JSON
bk agent list --json
```

### REST API Token (via CLI)

The `bk` CLI can extract its OAuth token for use with the REST API:

```bash
TOKEN=$(bk auth token)
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds/3292"
```

This avoids creating and managing separate API tokens. The token is the same OAuth token created by `bk auth login`.

### Opencode Integration

Once `bk` is installed and authenticated, opencode agents can use it directly for build operations (cancel, rebuild, inspect) without needing a separate API token. The `bk` CLI is the recommended approach.

**Note:** `bk auth login` requires an interactive TTY (browser OAuth flow), so it must be run by the user in a separate terminal before opencode can use `bk` commands. If the agent detects `bk` is not authenticated, it will prompt the user to run `bk auth login` manually.

## Local CI Simulation

To run the Buildkite build locally:

```bash
# Using the same Docker image as CI
scripts/local_buildkite_build.sh

# Or directly
docker run -v $(pwd):/build/mockserver \
  -w /build/mockserver \
  -a stdout -a stderr \
  mockserver/mockserver:maven \
  /build/mockserver/scripts/buildkite_quick_build.sh
```
