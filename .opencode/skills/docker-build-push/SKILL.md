---
name: docker-build-push
description: >
  Builds and pushes the MockServer Maven CI Docker image locally. Covers corporate
  CA certificate setup, architecture selection (amd64 vs arm64), buildx gotchas with
  corporate TLS proxies, and Docker Hub authentication. Use when the user says
  "build docker image", "push maven image", "rebuild CI image", "docker build",
  "push to docker hub", or needs to manually build/push the mockserver/mockserver:maven
  image outside of CI.
---

# Build & Push Maven CI Docker Image Locally

## Overview

The `mockserver/mockserver:maven` image is the CI build environment used by Buildkite.
It is normally built and pushed by the Buildkite pipeline `.buildkite/docker-push-maven.yml`,
but sometimes needs to be built and pushed manually — e.g. when bootstrapping, when
the pipeline isn't set up yet, or when testing Dockerfile changes.

**Image:** `mockserver/mockserver:maven`
**Dockerfile:** `docker_build/maven/Dockerfile`
**Base:** Ubuntu 24.04, OpenJDK 21, Maven 3.9.15
**Target architecture for CI:** `linux/amd64` (Buildkite agents run on x86_64 EC2)

## Prerequisites

- Docker Desktop installed and running
- Push access to the `mockserver/mockserver` Docker Hub repository
- Corporate root CA certificate (if behind a TLS inspection proxy)

## Step 1: Corporate CA Certificate

The Dockerfile expects a `corporate-root-ca.pem` file in the build context. If the
file has content, it is injected into the OS and Java trust stores. If empty, it is
skipped.

```bash
# Option A: Behind a corporate TLS proxy — copy your CA cert
cp /path/to/your/corporate-root-ca.pem docker_build/maven/corporate-root-ca.pem

# Option B: No proxy — create empty placeholder
cp docker_build/maven/corporate-root-ca.pem.example docker_build/maven/corporate-root-ca.pem
```

The real `.pem` file is gitignored. The `.pem.example` placeholder is committed.

## Step 2: Build the Image

### Native architecture (quick test)

For a quick local test on the host architecture:

```bash
docker build -t mockserver/mockserver:maven docker_build/maven/
```

This works on both arm64 (Apple Silicon) and amd64. The Dockerfile uses a symlink
trick (`dpkg --print-architecture`) to set JAVA_HOME portably — no `TARGETARCH` ARG
needed.

### Cross-architecture for CI (amd64 on Apple Silicon)

Buildkite agents run on amd64 EC2 instances. When building on an Apple Silicon Mac,
you MUST cross-compile to amd64 before pushing.

**IMPORTANT: Use the `desktop-linux` buildx builder, NOT `docker-container` builders.**

The `docker-container` buildx driver (e.g. the `multiplatform` builder) runs inside
its own container and does NOT inherit the host's TLS certificates. Behind a corporate
TLS proxy, it will fail with:

```
tls: failed to verify certificate: x509: certificate signed by unknown authority
```

The `desktop-linux` builder uses the Docker Desktop VM which inherits the host's
certificate trust store.

```bash
# Build amd64 image and load it into the local Docker daemon
docker buildx build \
    --builder desktop-linux \
    --platform linux/amd64 \
    --load \
    -t mockserver/mockserver:maven \
    docker_build/maven/
```

This uses QEMU emulation and takes ~12-15 minutes (vs ~7 minutes for native arm64).

## Step 3: Verify the Image

```bash
docker run --rm mockserver/mockserver:maven java -version
docker run --rm mockserver/mockserver:maven mvn -version

# Verify architecture
docker inspect mockserver/mockserver:maven --format '{{.Architecture}}'
# Should print: amd64
```

## Step 4: Docker Hub Login

Check if already logged in:

```bash
docker info 2>/dev/null | grep Username
```

If not logged in, authenticate. This requires an interactive terminal (cannot be done
from within opencode):

**Instruct the user to run in a separate terminal:**

```bash
docker login --username mockserver
```

The password is a Docker Hub Personal Access Token (PAT). To create one:
1. Navigate to https://app.docker.com/settings/personal-access-tokens
2. Generate New Token — permissions: **Read & Write**
3. Copy the `dckr_pat_...` token

See the `dockerhub-credentials` skill for full credential management including
AWS Secrets Manager storage.

## Step 5: Push

```bash
docker push mockserver/mockserver:maven
```

Verify the push succeeded:

```bash
# Check the remote manifest
docker manifest inspect mockserver/mockserver:maven 2>/dev/null \
    | python3 -c "import sys,json; m=json.load(sys.stdin); [print(f'{p[\"platform\"][\"architecture\"]}') for p in m.get('manifests', [{'platform': m.get('config',{})}])]"
```

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `tls: failed to verify certificate` during buildx | `docker-container` builder lacks host CA certs | Use `--builder desktop-linux` instead |
| `JAVA_HOME is not defined correctly` | TARGETARCH not set (plain `docker build`) | Fixed in current Dockerfile via `dpkg --print-architecture` symlink |
| `denied: requested access to the resource is denied` | Not logged in to Docker Hub | Run `docker login --username mockserver` in a separate terminal |
| Build very slow (~15 min) on Apple Silicon | QEMU emulating amd64 | Expected; native arm64 build is ~7 min |
| `mvnw install` fails during image build | GitHub master branch has build issues | The dep pre-fetch step clones from GitHub; if master is broken, the image build will fail at this step |
| `COPY corporate-root-ca.pem` fails | Missing `.pem` file | Run `cp docker_build/maven/corporate-root-ca.pem.example docker_build/maven/corporate-root-ca.pem` |

## Quick Reference

```bash
# Full workflow: build amd64 + push (from Apple Silicon Mac behind corporate proxy)
cp ~/.tesco-ca/tesco_root_ca.pem docker_build/maven/corporate-root-ca.pem
docker buildx build --builder desktop-linux --platform linux/amd64 --load -t mockserver/mockserver:maven docker_build/maven/
docker inspect mockserver/mockserver:maven --format '{{.Architecture}}'  # verify: amd64
docker push mockserver/mockserver:maven
```
