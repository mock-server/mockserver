# Community Module Registry — Testcontainers Cross-Link Draft

## Publish Gate

**Do not post until ALL five packages are live on their registries** (npm, PyPI, NuGet,
pkg.go.dev, crates.io). The registry catalog is the canonical cross-language index;
submitting with dead links undermines trust.

---

## Target

**Repository:** https://github.com/testcontainers/community-module-registry

Each module is a file at `modules/<name>/index.md` with YAML frontmatter. Valid values
for `maintainer` are: `core`, `community`, `official`.

The existing Java PR (#184, open) updates the `mockserver` entry. This PR adds five new
per-language entries as separate files. The maintainer is `official` because these modules
are maintained by the MockServer project itself.

**Note:** the catalog format was confirmed from PR #184. If the format has changed since
that PR was opened, mirror whatever structure the merged PRs around it use.

---

## PR Title

```
feat: add official MockServer modules for Node, Python, .NET, Go, and Rust
```

---

## PR / Issue Body

```markdown
## Summary

MockServer now ships officially maintained Testcontainers modules for five additional
languages. These modules are published and maintained by the MockServer project (not
third-party ports).

The existing `mockserver` catalog entry covers Java (updated in #184). This PR adds the
five remaining languages as new entries.

Each entry follows the same frontmatter schema as existing community modules.

---

## New files

### `modules/mockserver-node/index.md`

```yaml
---
name: MockServer (Node)
description: Officially maintained Testcontainers module for MockServer — starts a mockserver/mockserver Docker container for integration testing in Node.js/TypeScript.
language: nodejs
homepage: https://www.mock-server.com
source: https://github.com/mock-server/mockserver/tree/master/mockserver-testcontainers/node
package: https://www.npmjs.com/package/@mockserver/testcontainers
maintainer: official
---
```

### `modules/mockserver-python/index.md`

```yaml
---
name: MockServer (Python)
description: Officially maintained Testcontainers module for MockServer — starts a mockserver/mockserver Docker container for integration testing in Python.
language: python
homepage: https://www.mock-server.com
source: https://github.com/mock-server/mockserver/tree/master/mockserver-testcontainers/python
package: https://pypi.org/project/testcontainers-mockserver/
maintainer: official
---
```

### `modules/mockserver-dotnet/index.md`

```yaml
---
name: MockServer (.NET)
description: Officially maintained Testcontainers module for MockServer — starts a mockserver/mockserver Docker container for integration testing in .NET.
language: dotnet
homepage: https://www.mock-server.com
source: https://github.com/mock-server/mockserver/tree/master/mockserver-testcontainers/dotnet
package: https://www.nuget.org/packages/MockServer.Testcontainers/
maintainer: official
---
```

### `modules/mockserver-go/index.md`

```yaml
---
name: MockServer (Go)
description: Officially maintained Testcontainers module for MockServer — starts a mockserver/mockserver Docker container for integration testing in Go.
language: go
homepage: https://www.mock-server.com
source: https://github.com/mock-server/mockserver/tree/master/mockserver-testcontainers/go
package: https://pkg.go.dev/github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9
maintainer: official
---
```

### `modules/mockserver-rust/index.md`

```yaml
---
name: MockServer (Rust)
description: Officially maintained Testcontainers module for MockServer — starts a mockserver/mockserver Docker container for integration testing in Rust.
language: rust
homepage: https://www.mock-server.com
source: https://github.com/mock-server/mockserver/tree/master/mockserver-testcontainers/rust
package: https://crates.io/crates/testcontainers-mockserver
maintainer: official
---
```

---

## Package coordinates

| Language | Install |
|----------|---------|
| Node | `npm install --save-dev @mockserver/testcontainers` |
| Python | `pip install testcontainers-mockserver` |
| .NET | `dotnet add package MockServer.Testcontainers` |
| Go | `go get github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9` |
| Rust | `testcontainers-mockserver = "7.0"` in `[dev-dependencies]` |

Related: Java module update in #184.
```

---

## Validation Before Submitting

The community module registry validates YAML with `cytopia/yamllint`. Before opening the
PR, validate each file locally:

```bash
docker run --rm -v $(pwd):/data cytopia/yamllint modules/mockserver-*/index.md
```

All five files must pass with no errors.

---

## Notes for the Submitter

- The directory name convention (`mockserver-node`, `mockserver-python`, etc.) is a
  proposal — mirror whatever convention is used by other multi-language modules in the
  catalog at submission time.
- The `language` field values (`nodejs`, `python`, `dotnet`, `go`, `rust`) must match
  whatever controlled vocabulary the catalog enforces — check existing entries first.
- If the catalog schema has changed since PR #184, update frontmatter fields to match.
- The Go package URL will change if the module is split to its own repo; update before
  posting.
- File sources verified: `package.json`, `pyproject.toml`, `Testcontainers.MockServer.csproj`,
  `go.mod`, `Cargo.toml` in `mockserver-testcontainers/<lang>/`.
