# MockServer Monorepo Migration - Execution Checklist

**Status:** Complete

## Goal

Consolidate MockServer repositories into one monorepo to share CI/CD, AI configuration, release tooling, and documentation, while removing manual UI artifact copying.

## Repositories in Scope

| Repository | Purpose |
|------------|---------|
| `mockserver` | Main Java server (11 Maven modules) |
| `mockserver-ui` | Dashboard React SPA |
| `mockserver-maven-plugin` | Maven plugin |
| `mockserver-node` | Node launcher |
| `mockserver-client-node` | JS/TS API client |
| `mockserver-client-python` | Python API client |
| `mockserver-client-ruby` | Ruby API client |
| `mockserver-performance-test` | Performance tests |

## Guardrails

- Work on a dedicated branch: `monorepo-migration`.
- Use `.tmp/` for scratch work, never `/tmp/`.
- Stage by explicit path, never `git add -A` or `git add .`.
- Run `git pull --rebase` before any push.
- Validate each phase before moving forward.
- Optional checkpoint tags: `git tag monorepo-phase<N>-complete`.
- Fast rollback to last good state: `git reset --hard <checkpoint-tag-or-commit>`.

## Helper: Detect Default Branch

```bash
DEFAULT_BRANCH=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || true)
DEFAULT_BRANCH=${DEFAULT_BRANCH:-main}
```

---

## Phase 1 - Preparation

**Commands**

```bash
set -euo pipefail

SCRATCH_DIR=".tmp/monorepo-migration"
mkdir -p "$SCRATCH_DIR"

for repo in mockserver-ui mockserver-maven-plugin mockserver-node \
            mockserver-client-node mockserver-client-python \
            mockserver-client-ruby mockserver-performance-test; do
  gh repo clone "mock-server/$repo" "$SCRATCH_DIR/$repo"
  pushd "$SCRATCH_DIR/$repo" >/dev/null
  git checkout -b pre-monorepo-backup
  git push origin pre-monorepo-backup
  popd >/dev/null
done
```

**Manual actions**

- Triage open PRs in satellite repos: merge real fixes, close stale security churn.
- Freeze satellite repos (temporary read-only / branch protection).

**Exit criteria**

- Backup branch exists in every satellite repo.
- PR triage completed.
- Satellite repos frozen.

---

## Phase 2 - Move Main Java Project to `mockserver/`

**Commands**

```bash
git checkout -B monorepo-migration
mkdir -p mockserver

git mv pom.xml mockserver/
git mv mvnw mvnw.cmd .mvn mockserver/
git mv checkstyle.xml mockserver/
git mv mockserver.example.properties mockserver/

for module in mockserver-core mockserver-netty mockserver-client-java \
              mockserver-war mockserver-proxy-war mockserver-junit-rule \
              mockserver-junit-jupiter mockserver-spring-test-listener \
              mockserver-integration-testing mockserver-testing mockserver-examples; do
  git mv "$module" mockserver/
done

git mv .run mockserver/
```

**Required file updates**

- Update paths in scripts under `scripts/` that call `./mvnw`.
- Update `.buildkite/` commands for new `mockserver/` location.
- Update Docker/JAR paths if they reference old locations.
- Update Java source references in docs.
- Update `mockserver/pom.xml` `<scm>` URLs.
- Update path references in `mockserver/.run/` files.
- Verify `container_integration_tests/` paths still work.
- Re-import project in IntelliJ from `mockserver/pom.xml`.

**Validation**

```bash
cd mockserver && ./mvnw clean verify -DskipTests && cd ..
bash scripts/local_quick_build.sh
git log --follow --oneline mockserver/mockserver-core/src/main/java/org/mockserver/model/HttpRequest.java | head -20
```

**Commit**

```bash
git commit -m "move main Java project into mockserver/ subfolder for monorepo structure"
```

**Exit criteria**

- Maven build works from `mockserver/`.
- Updated scripts run successfully.
- History trace works with `git log --follow`.

---

## Phase 3 - Import Satellite Repositories

### 3.0 Audit histories before import

**Commands**

```bash
set -euo pipefail

SCRATCH_DIR=".tmp/monorepo-history-audit"
mkdir -p "$SCRATCH_DIR"

for repo in mockserver-ui mockserver-maven-plugin mockserver-node \
            mockserver-client-node mockserver-client-python \
            mockserver-client-ruby mockserver-performance-test; do
  gh repo clone "mock-server/$repo" "$SCRATCH_DIR/$repo"
  pushd "$SCRATCH_DIR/$repo" >/dev/null
  git log --all --full-history -- .env \
    ":(glob)**/*.pem" ":(glob)**/*.key" ":(glob)**/*.p12" ":(glob)**/*.jks"
  popd >/dev/null
done
```

If any repo history is dirty, clean it first or import that repo with `git subtree add --squash`.

### 3.1 Import repos with subtree

Before running, ensure target directories do not already exist from a partial run (`mockserver-ui/`, `mockserver-node/`, etc.).
Also verify `git subtree` is available (`git subtree --help`).

**Commands**

```bash
set -euo pipefail

for repo in mockserver-ui mockserver-maven-plugin mockserver-node \
            mockserver-client-node mockserver-client-python \
            mockserver-client-ruby mockserver-performance-test; do
  remote_name="${repo}-import"
  default_branch=$(gh repo view "mock-server/$repo" --json defaultBranchRef --jq '.defaultBranchRef.name')

  git remote add "$remote_name" "https://github.com/mock-server/$repo.git"
  git fetch "$remote_name"
  git subtree add --prefix="$repo" "$remote_name/$default_branch"
  git remote remove "$remote_name"
done
```

### 3.2 Remove redundant per-repo CI config

**Commands**

```bash
git rm -f --ignore-unmatch mockserver-ui/.buildkite/pipeline.yml
git rm -rf --ignore-unmatch mockserver-ui/.github/
git rm -f --ignore-unmatch mockserver-maven-plugin/.buildkite/pipeline.yml
git rm -rf --ignore-unmatch mockserver-maven-plugin/.github/
git rm -f --ignore-unmatch mockserver-node/.buildkite/pipeline.yml
git rm -rf --ignore-unmatch mockserver-node/.github/
git rm -f --ignore-unmatch mockserver-client-node/.buildkite/pipeline.yml
git rm -rf --ignore-unmatch mockserver-client-node/.github/
git rm -rf --ignore-unmatch mockserver-client-python/.github/
git rm -rf --ignore-unmatch mockserver-client-ruby/.github/
git rm -f --ignore-unmatch mockserver-performance-test/.buildkite/pipeline.yml

git commit -m "remove per-repo CI/CD config replaced by monorepo pipelines"
```

**Exit criteria**

- All repositories imported under dedicated subdirectories.
- No unexpected root-level file collisions.
- Redundant nested CI config removed.

---

## Phase 4 - Automate UI Build Integration

**Required `mockserver/mockserver-netty/pom.xml` changes**

- Add `build-ui` profile activated by `../../mockserver-ui/package.json`.
- In `frontend-maven-plugin`:
  - pin `<nodeVersion>` to a fixed current LTS release (for example `v22.14.0`).
  - bind `install-node-and-npm`, `npm ci`, and `npm run build` to `generate-resources`.
- In `maven-resources-plugin`:
  - copy from `../../mockserver-ui/build` to `${project.build.outputDirectory}/org/mockserver/dashboard`.
  - bind copy step to `process-resources`.
- Add `src/main/resources/org/mockserver/dashboard/` to `mockserver/mockserver-netty/.gitignore` to prevent re-committing generated assets.

**Commands**

```bash
git rm -r mockserver/mockserver-netty/src/main/resources/org/mockserver/dashboard/
git commit -m "remove manually-committed UI build artifacts, now built from mockserver-ui/ source"
```

**Validation**

```bash
#!/bin/bash
cd mockserver && ./mvnw clean package -pl mockserver-netty -am
shopt -s nullglob
JARS=(mockserver-netty/target/mockserver-netty-*-shaded.jar)
[ ${#JARS[@]} -eq 1 ] || { echo "Expected exactly one shaded jar"; exit 1; }
jar tf "${JARS[0]}" | grep dashboard
cd ..
```

**Exit criteria**

- UI files are produced from source during Maven build.
- Shaded JAR contains `org/mockserver/dashboard/` assets.

---

## Phase 5 - Update Maven Plugin Parent Reference

**Required `mockserver-maven-plugin/pom.xml` changes**

- Parent remains `org.mock-server:mockserver`.
- `<relativePath>` is `../mockserver/pom.xml`.
- Parent `<version>` matches `mockserver/pom.xml`.

**Validation**

```bash
cd mockserver && ./mvnw clean install -DskipTests && cd ..
./mockserver/mvnw -f mockserver-maven-plugin/pom.xml clean verify
```

**Exit criteria**

- Maven plugin resolves parent from monorepo and builds successfully.

---

## Phase 6 - CI/CD Migration

### 6.1 Create path-trigger orchestrator

Create `.buildkite/scripts/generate-pipeline.sh` with:

- dynamic default branch detection (or PR base branch),
- path-based uploads,
- infra uploads for shared directories,
- fallback upload of `.buildkite/pipeline-default.yml` when nothing matches.

**Reference script**

```bash
#!/bin/bash
set -euo pipefail

DEFAULT_BRANCH="${BUILDKITE_PULL_REQUEST_BASE_BRANCH:-}"
if [ -z "$DEFAULT_BRANCH" ]; then
  DEFAULT_BRANCH=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || true)
fi
DEFAULT_BRANCH=${DEFAULT_BRANCH:-main}

MERGE_BASE=$(git merge-base HEAD "origin/${DEFAULT_BRANCH}" 2>/dev/null || echo "HEAD~1")
CHANGED_FILES=$(git diff --name-only "$MERGE_BASE"..HEAD 2>/dev/null || git diff --name-only HEAD)
PIPELINES_UPLOADED=0

upload_if_changed() {
  local path_regex="$1"
  local pipeline_file="$2"
  if printf '%s\n' "$CHANGED_FILES" | grep -qE -- "$path_regex"; then
    buildkite-agent pipeline upload "$pipeline_file"
    PIPELINES_UPLOADED=1
  fi
}

# UI changes should also run Java pipeline because the netty build embeds UI assets.
upload_if_changed "^(mockserver/|mockserver-ui/)" ".buildkite/pipeline-java.yml"
upload_if_changed "^mockserver-ui/" ".buildkite/pipeline-ui.yml"
upload_if_changed "^(mockserver-node/|mockserver-client-node/)" ".buildkite/pipeline-node.yml"
upload_if_changed "^mockserver-client-python/" ".buildkite/pipeline-python.yml"
upload_if_changed "^mockserver-client-ruby/" ".buildkite/pipeline-ruby.yml"
upload_if_changed "^mockserver-maven-plugin/" ".buildkite/pipeline-maven-plugin.yml"
upload_if_changed "^mockserver-performance-test/" ".buildkite/pipeline-perf-test.yml"

if printf '%s\n' "$CHANGED_FILES" | grep -qE -- "^(\.buildkite/|terraform/|docker/|scripts/|helm/|docs/|AGENTS\.md|opencode\.jsonc|\.opencode/)"; then
  buildkite-agent pipeline upload ".buildkite/pipeline-infra.yml"
  PIPELINES_UPLOADED=1
fi

if [ "$PIPELINES_UPLOADED" -eq 0 ]; then
  buildkite-agent pipeline upload ".buildkite/pipeline-default.yml"
fi
```

### 6.2 Create/update pipeline files

- `.buildkite/pipeline.yml` (orchestrator)
- `.buildkite/pipeline-java.yml`
- `.buildkite/pipeline-ui.yml` (use `npm ci` if lockfile exists, else `npm install`)
- `.buildkite/pipeline-node.yml` (same lockfile logic)
- `.buildkite/pipeline-python.yml`
- `.buildkite/pipeline-ruby.yml`
- `.buildkite/pipeline-maven-plugin.yml`
- `.buildkite/pipeline-perf-test.yml`
- `.buildkite/pipeline-infra.yml`
- `.buildkite/pipeline-default.yml`

### 6.3 Update Terraform pipeline config

Point `terraform/buildkite-pipelines/pipelines.tf` to monorepo pipeline file and remove obsolete satellite pipeline resources.

**State cleanup example**

```bash
cd terraform/buildkite-pipelines
terraform state list | grep buildkite_pipeline
# Manually verify exact addresses from state output before removing:
# terraform state rm <confirmed-address>
```

### 6.4 Update GitHub Actions

- Extend CodeQL matrix to include monorepo languages.
- Enable Ruby only after verifying Ruby build support.
- Consolidate Dependabot config for all ecosystems.

**Exit criteria**

- Path-targeted PRs trigger expected pipelines.
- No-build changes still run default pipeline.
- Terraform and CodeQL configuration validate successfully.

---

## Phase 7 - Update AI and Contributor Docs

**Update these files**

- `AGENTS.md` with monorepo layout and per-project build commands.
- `.opencode/skills/*` paths that moved under `mockserver/`.
- `.opencode/rules/commit-workflow.md` with JS/Python/Ruby validation rules.
- `CONTRIBUTING.md` with monorepo contribution workflow.

**Exit criteria**

- Agent rules and contributor docs match new layout.

---

## Phase 8 - Update Build and Release Scripts

**Update scripts in `scripts/`**

- Replace root `./mvnw ...` usage with `cd mockserver && ./mvnw ...` or `./mockserver/mvnw -f ...`.

**Reference release script behavior**

- `set -euo pipefail`
- validate input version
- `git pull --rebase` before push
- use Maven `versions:set` instead of `sed -i`
- use `npm version --no-git-tag-version`
- stage explicit files only

**Validation**

```bash
# After creating or updating scripts/release.sh
bash -n scripts/release.sh
```

**Exit criteria**

- Script paths are monorepo-safe.
- Release script syntax checks pass.

---

## Phase 9 - Rename, Migrate Issues, Archive Repos

### 9.1 Rename main repository

```bash
gh repo rename mockserver-monorepo --repo mock-server/mockserver
```

Do not create a new repository named `mockserver` after the rename, or GitHub redirect behavior for existing links may break.

### 9.2 Migrate open issues from satellites (before archive)

| Repo | Issues to migrate |
|------|-------------------|
| mockserver-ui | #11 |
| mockserver-maven-plugin | #79 |
| mockserver-node | #106, #86, #79, #43 |
| mockserver-client-node | #176, #174, #172, #162, #158, #160, #148 |
| mockserver-client-python | #1 |
| mockserver-client-ruby | #4, #2 |

For all other open issues, explicitly migrate or close with documented reason.

### 9.3 Archive satellite repos

**Commands**

```bash
set -euo pipefail

ARCHIVE_DIR=".tmp/monorepo-archive"
mkdir -p "$ARCHIVE_DIR"

for repo in mockserver-ui mockserver-maven-plugin mockserver-node \
            mockserver-client-node mockserver-client-python \
            mockserver-client-ruby mockserver-performance-test; do
  gh repo clone "mock-server/$repo" "$ARCHIVE_DIR/$repo"
  pushd "$ARCHIVE_DIR/$repo" >/dev/null
  default_branch=$(gh repo view "mock-server/$repo" --json defaultBranchRef --jq '.defaultBranchRef.name')

  cat > .archive_notice.md <<EOF
# This repository has been archived

This project has been merged into the MockServer monorepo:
**https://github.com/mock-server/mockserver-monorepo**

The code now lives in the \`$repo/\` subdirectory of the monorepo.

All new issues and pull requests should be filed against the monorepo.
EOF

  if [ -f README.md ]; then
    { cat .archive_notice.md; echo; cat README.md; } > README.md.new
  else
    mv .archive_notice.md README.md.new
  fi

  mv README.md.new README.md
  rm -f .archive_notice.md

  git add README.md
  if git diff --cached --quiet; then
    echo "No README changes to commit for $repo"
  else
    git commit -m "archive: redirect to monorepo"
  fi
  git pull --rebase origin "$default_branch"
  git push origin "HEAD:$default_branch"
  popd >/dev/null

  gh repo archive "mock-server/$repo" --yes
done
```

### 9.4 Update external metadata links

- npm package `repository` fields
- Maven `<scm>` metadata
- Docker Hub links
- Homebrew formula links
- Jekyll website GitHub links
- SwaggerHub source links
- `terraform/buildkite-pipelines/pipelines.tf` repository URL (`local.repository`).

**Exit criteria**

- Main repo renamed and redirect works.
- Required issues migrated/closed with links.
- Satellite repos archived with README notice.
- External references updated.

---

## Final Validation

### Local validation results

| Component | Validation | Result |
|-----------|-----------|--------|
| Java (all 11 modules) | `cd mockserver && ./mvnw clean verify` | PASS |
| UI tests | `cd mockserver-ui && npx vitest run` | 107 tests PASS |
| UI build | `cd mockserver-ui && npm run build` | PASS |
| Node client | `cd mockserver-client-node && npx tsc --noEmit && npx eslint .` | PASS |
| Node launcher | `cd mockserver-node && npx tsc --noEmit && npx eslint .` | PASS |
| Python unit tests | `cd mockserver-client-python && pytest --ignore=tests/test_integration.py` | 438 PASS |
| Python integration tests | `cd mockserver-client-python && pytest -m integration` | 22 PASS |
| Ruby unit tests | `cd mockserver-client-ruby && bundle exec rspec --tag '~integration'` | 312 PASS |
| Ruby integration tests | `cd mockserver-client-ruby && bundle exec rspec --tag integration` | 21 PASS |
| Container scripts | `bash -n` syntax check on all shell scripts | PASS |
| Performance test | Syntax validation | PASS |
| Maven plugin | `cd mockserver-maven-plugin && ../mockserver/mvnw clean verify` | PASS |

### Buildkite pipeline state

`terraform apply` completed successfully — MockServer pipeline updated with monorepo description and emoji. All 3 pipelines (MockServer, Build Image, Release Image) are in sync with `terraform/buildkite-pipelines/pipelines.tf`.

Confirm pipelines trigger correctly for at least:

- Java-only change
- UI-only change
- infra/docs-only change
- no-match change (default pipeline)

## Resolved Decisions

- Do not import `mockserver-pipeline` (legacy/obsolete).
- Do not include `slack-invite-automation` (unrelated project).

## Remaining Open Questions

1. ~~Regenerate stale Python/Ruby clients during migration or follow-up?~~ **Resolved**: Python client replaced with hand-written idiomatic library (v6.0.0, dataclasses, WebSocket callbacks, 438 tests). Ruby client rewritten with idiomatic Ruby (v2.0.0, 312 unit tests, 21 integration tests).
2. ~~Modernize UI during migration or as separate project?~~ **Resolved**: UI modernized during migration (Phase 4).
3. ~~Introduce Nx/Turborepo now or after baseline monorepo is stable?~~ **Deferred** — the current Buildkite-based build orchestration is sufficient.
4. ~~Review `KeyToMultiValue.to_dict()` serialization consistency~~ **Resolved**: Both Python and Ruby clients now handle both dict-format (`{"name": ["value"]}`) and list-format (`[{"name": ..., "values": ...}]`) header deserialization, matching what MockServer returns for recorded requests vs expectations.
5. ~~Run `terraform apply` in `terraform/buildkite-pipelines/` to sync pipeline state~~ **Done**: Pipeline updated with monorepo description/emoji, all 3 pipelines in sync.
