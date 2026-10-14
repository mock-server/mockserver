# Release Component: testcontainers-go

## `scripts/release/components/testcontainers-go.sh`

```bash
#!/bin/bash
set -euo pipefail

# Release the Go Testcontainers module by creating and pushing a git tag.
# The Go module proxy indexes the module automatically on first fetch.

RELEASE_VERSION="${RELEASE_VERSION:?RELEASE_VERSION must be set}"
MODULE_TAG="mockserver-testcontainers/go/v${RELEASE_VERSION}"

# No source edit needed: DefaultImage is derived at init from this module's own
# resolved version in the build info (falling back to :latest).

# Verify the module builds
(cd mockserver-testcontainers/go && go vet ./... && go test -run 'TestURL|TestDefault' ./...)

# Tag and push (the proxy indexes the tagged module on first fetch)
git tag "${MODULE_TAG}"
git push origin "${MODULE_TAG}"

# Trigger proxy indexing
GOPROXY=https://proxy.golang.org GO111MODULE=on \
  go list -m "github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9@v${RELEASE_VERSION}" || true

echo "Published: ${MODULE_TAG}"
```

## Liveness Check (`scripts/release/components/verify.sh` entry)

```bash
# testcontainers-go: verify the module is accessible via the Go proxy
go list -m -json "github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9@v${RELEASE_VERSION}"
```
