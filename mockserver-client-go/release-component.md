# Release Component: mockserver-client-go

> **Module path:** `github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9`.
> The `/vN` suffix is Go's Semantic Import Versioning marker for the release major N. A
> major release changes it (`prepare.sh` runs `go_module_major.py --set <major>`);
> minor and patch releases do not.
> The publish **tag** is independent of the suffix and stays `mockserver-client-go/v${VERSION}`.

## Component Script (`scripts/release/components/go-client.sh`)

```bash
#!/usr/bin/env bash
set -euo pipefail

VERSION="${RELEASE_VERSION:?RELEASE_VERSION is required}"
TAG="mockserver-client-go/v${VERSION}"

echo "--- :golang: Publishing Go client v${VERSION}"

# Tag the release (the Go proxy indexes on tag push)
git tag -a "${TAG}" -m "Release Go client v${VERSION}"
git push origin "${TAG}"

# Trigger proxy indexing
GOPROXY=https://proxy.golang.org GO111MODULE=on \
  go get "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9@v${VERSION}" || true

echo "Go client ${TAG} published to pkg.go.dev"
```

## Liveness Check (`scripts/release/components/verify.sh` entry)

```bash
# Go client: verify module is indexed on pkg.go.dev
curl -sf --retry 5 --retry-delay 30 \
  "https://pkg.go.dev/github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9@v${RELEASE_VERSION}" \
  | grep -q "mockserver-client-go"
```

## Secret

None. The Go module proxy indexes public repositories automatically.

## Pipeline Wiring

Add to `.buildkite/release-pipeline.yml`:

```yaml
- label: ":golang: Publish Go client"
  command: scripts/release/components/go-client.sh
  agents:
    queue: release
  depends_on: "maven-release"
```
