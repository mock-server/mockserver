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
    end

    BK -->|runs on| EC2[AWS EC2 Agents]
    GA1 -->|pushes to| DH[Docker Hub]
    GA2 -->|reports to| GH[GitHub Security]
```

## Buildkite Pipeline

**File:** `.buildkite/pipeline.yml`

The pipeline has two sequential steps:

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

- Base: Ubuntu 22.04 (Jammy)
- JDK: OpenJDK 8
- Maven: Pre-installed via wrapper
- Dependencies: Pre-fetched by running a throwaway build during image creation

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

### CodeQL Security Analysis

**File:** `.github/workflows/codeql-analysis.yml`

**Triggers:**
- Push to `master`
- Pull requests targeting `master`
- Weekly schedule: Tuesdays at 22:00 UTC

**Languages scanned:** Java, JavaScript

**Process:** Uses GitHub's CodeQL autobuild to compile Java sources, then runs static analysis queries to detect security vulnerabilities.

## Build Agent Infrastructure

See [AWS Infrastructure](aws-infrastructure.md) for details on the Buildkite agent EC2 instances, AutoScaling Group, and Lambda-based autoscaler.

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
