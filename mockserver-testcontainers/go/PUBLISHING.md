# Publishing

## Registry

**pkg.go.dev** — Go modules are indexed automatically by the Go module proxy when
first fetched after a git tag is pushed.

## Module Path

```
github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9
```

> Eventual split target: `github.com/mock-server/mockserver-testcontainers-go`

The `/vN` suffix is Go's Semantic Import Versioning marker for the release major N: a
module at major version 2 or higher must end its path in `/vN`, or the Go module
proxy rejects its tags. The tag keeps the directory prefix
(`mockserver-testcontainers/go/vX.Y.Z`). A major release rewrites the suffix
automatically (`scripts/release/prepare.sh` runs `go_module_major.py --set <major>`).

## Publish Command (non-interactive)

```bash
# VERSION is the full release version (X.Y.Z); its major N is the module's /vN suffix
VERSION="${RELEASE_VERSION}"

# 1. Tag the module with the release version (directory prefix + version)
git tag "mockserver-testcontainers/go/v${VERSION}"

# 2. Push the tag to GitHub
git push origin "mockserver-testcontainers/go/v${VERSION}"

# 3. Request the Go module proxy to index it (optional — happens on first `go get`)
GOPROXY=https://proxy.golang.org GO111MODULE=on \
  go list -m "github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v${VERSION%%.*}@v${VERSION}"
```

## Secret Required

**None.** The Go module proxy (`proxy.golang.org`) indexes public modules
automatically on first fetch after a valid semver tag exists on the repository.

## Version Bumping

No source edit is required. `DefaultImage` is derived at init from this module's
own resolved version in the build info, so tagging the release
(`mockserver-testcontainers/go/vX.Y.Z`) is sufficient; the default image tag
follows automatically (falling back to `:latest` when the version cannot be
resolved, e.g. in-module tests).

## Verification

After publishing, verify the module is indexed:

```bash
go list -m -versions github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9
```

Or visit: https://pkg.go.dev/github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9
