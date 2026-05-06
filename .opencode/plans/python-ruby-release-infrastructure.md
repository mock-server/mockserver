# Plan: Python & Ruby Client Release Infrastructure

## Goal

Enable publishing the Python and Ruby MockServer clients to PyPI and RubyGems respectively, with version alignment to the Java MockServer version, rich artifact page metadata, and manual release scripts following existing project patterns.

## Context

- Both clients are fully developed, tested (Buildkite CI), and packaged — but no publishing infrastructure exists
- PyPI `mockserver-client` is currently v0.0.6 (third-party by Internap, 2018) — user is a maintainer
- RubyGems `mockserver-client` is currently v5.3.0 (official, 2018) — user is an owner
- 267 commits since last Java release (5.15.0) including new features, behavior changes, UI rewrite — warrants minor version bump
- User wants version alignment across Java, Python, and Ruby

## Changes

### 1. Version Alignment

| File | Old | New |
|------|-----|-----|
| `mockserver/pom.xml` line 7 | `5.15.1-SNAPSHOT` | `5.16.0-SNAPSHOT` |
| `mockserver-client-python/pyproject.toml` line 3 | `6.0.0` | `5.16.0` |
| `mockserver-client-ruby/lib/mockserver/version.rb` line 4 | `6.0.0` | `5.16.0` |
| `jekyll-www.mock-server.com/_config.yml` | `5.15.1-SNAPSHOT` refs | `5.16.0-SNAPSHOT` |

Also update any version references in:
- `mockserver-client-ruby/README.md` line 11: `~> 6.0` → `~> 5.16`
- `scripts/release_steps.md`: version references (5.15.0, 5.15.1-SNAPSHOT → 5.16.0, 5.16.1-SNAPSHOT)

### 2. PyPI Metadata Enhancement (`mockserver-client-python/pyproject.toml`)

Add classifiers:
```toml
classifiers = [
    "Development Status :: 5 - Production/Stable",
    "Intended Audience :: Developers",
    "License :: OSI Approved :: Apache Software License",
    "Programming Language :: Python :: 3",
    "Programming Language :: Python :: 3.9",
    "Programming Language :: Python :: 3.10",
    "Programming Language :: Python :: 3.11",
    "Programming Language :: Python :: 3.12",
    "Programming Language :: Python :: 3.13",
    "Topic :: Software Development :: Testing",
    "Topic :: Internet :: WWW/HTTP",
]
```

Add extra URLs:
```toml
[project.urls]
Homepage = "https://www.mock-server.com"
Repository = "https://github.com/mock-server/mockserver-monorepo"
Documentation = "https://www.mock-server.com/mock_server/getting_started.html"
Changelog = "https://www.mock-server.com/mock_server/changelog.html"
"Bug Tracker" = "https://github.com/mock-server/mockserver-monorepo/issues"
```

### 3. RubyGems Metadata Enhancement (`mockserver-client-ruby/mockserver-client.gemspec`)

Add metadata hash:
```ruby
spec.metadata = {
  'source_code_uri'   => 'https://github.com/mock-server/mockserver-monorepo',
  'changelog_uri'     => 'https://www.mock-server.com/mock_server/changelog.html',
  'bug_tracker_uri'   => 'https://github.com/mock-server/mockserver-monorepo/issues',
  'documentation_uri' => 'https://www.mock-server.com/mock_server/getting_started.html',
}
```

### 4. Release Scripts

**`scripts/release_python.sh`** — Builds and publishes to PyPI:
- Fetches PyPI token from AWS Secrets Manager (`mockserver-build/pypi`)
- Dual-mode: CI uses IAM role, local uses `--profile mockserver-build`
- Cleans, builds (`python -m build`), verifies (`twine check`), uploads (`twine upload`)
- Requires `build` and `twine` installed locally (not added as project deps — they're publishing tools)

**`scripts/release_ruby.sh`** — Builds and publishes to RubyGems:
- Fetches RubyGems API key from AWS Secrets Manager (`mockserver-build/rubygems`)
- Same dual-mode credential loading
- Cleans, builds (`gem build`), pushes (`gem push`)

Both scripts follow the existing pattern from `.buildkite/scripts/docker-login.sh` and the planned `load_secret()` from `docs/plans/release-pipeline.md`.

### 5. Terraform Secret Definitions (`terraform/buildkite-agents/build-secrets.tf`)

Add two new secrets:
```hcl
resource "aws_secretsmanager_secret" "pypi" {
  name        = "mockserver-build/pypi"
  description = "PyPI API token for publishing mockserver-client Python package"
}

resource "aws_secretsmanager_secret" "rubygems" {
  name        = "mockserver-build/rubygems"
  description = "RubyGems API key for publishing mockserver-client Ruby gem"
}
```

Add both to the existing IAM policy resource list.

### 6. Release Process Documentation

**`scripts/release_steps.md`** — Add steps 14 and 15:
- Step 14: Publish Python client to PyPI (`./scripts/release_python.sh`)
- Step 15: Publish Ruby client to RubyGems (`./scripts/release_ruby.sh`)

**`docs/operations/release-process.md`** — Add:
- Steps 14 and 15 with full documentation
- Update the Mermaid flow diagram to include PyPI and RubyGems
- Update the Release Artifacts Summary diagram
- Add Python/Ruby to the version find-and-replace checklist in step 3

**`docs/plans/release-pipeline.md`** — Add:
- PyPI and RubyGems to the credentials inventory table
- Planned automation steps for Python/Ruby publishing

### 7. Version Management Checklist

Add to the version find-and-replace items in the release steps:
- `mockserver-client-python/pyproject.toml` version field
- `mockserver-client-ruby/lib/mockserver/version.rb` VERSION constant
- `mockserver-client-ruby/README.md` gem version reference

## Out of Scope

- CI-automated publishing (manual scripts for now, matching npm pattern)
- PyPI Trusted Publisher setup (can be added later for keyless CI publishing)
- Creating actual API tokens (user does this interactively after infrastructure is in place)
- Running `terraform apply` (user does this separately)

## Execution Order

1. Update versions (POM, pyproject.toml, version.rb, README)
2. Enhance PyPI and RubyGems metadata
3. Create release scripts
4. Add Terraform secret definitions
5. Update release process documentation

## Post-Implementation (User Actions)

1. Run `terraform apply` in `terraform/buildkite-agents/` to create the secrets
2. Create PyPI API token at https://pypi.org/manage/project/mockserver-client/settings/
3. Store PyPI token: `aws secretsmanager put-secret-value --secret-id mockserver-build/pypi --secret-string '{"token":"pypi-..."}' --profile mockserver-build --region eu-west-2`
4. Create RubyGems API key at https://rubygems.org/profile/api_keys
5. Store RubyGems key: `aws secretsmanager put-secret-value --secret-id mockserver-build/rubygems --secret-string '{"api_key":"..."}' --profile mockserver-build --region eu-west-2`
6. Test scripts with a dry run (add `--repository testpypi` for PyPI testing)
