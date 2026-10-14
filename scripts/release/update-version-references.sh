#!/usr/bin/env bash
# Update version references in docs/configs across the repo after a successful
# Maven Central publish. Commits and pushes the changes to master so that
# downstream parallel publish steps (website, helm, docker, etc.) read the
# new version when they clone the repo on their own agents.
#
# This used to live at the tail of finalize.sh. It was moved to its own stage
# and inserted before the parallel publish group because finalize runs LAST,
# so the website (and other deploys) were shipping the previous version's
# _config.yml / package.json / etc. (See incident: build #39 — release 6.1.0
# website published with mockserver_version=6.0.0.)
#
# Dry-run: do the version-reference updates locally so the diff can be
# reviewed; skip the git push.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --execute) DRY_RUN=false; shift ;;
    -h|--help) echo "Usage: $0 [--dry-run|--execute]"; exit 0 ;;
    *) log_error "Unknown arg: $1"; exit 2 ;;
  esac
done

require_cmd git
require_cmd python3
require_cmd jq
require_cmd sed
require_release_inputs
skip_unless_release_type "update-version-references" full,maven-only,post-maven

log_step "Update version references to $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

MAJOR="${RELEASE_VERSION%%.*}"
MINOR_REST="${RELEASE_VERSION#*.}"
MINOR="${MINOR_REST%%.*}"
API_VERSION="${MAJOR}.${MINOR}.x"

OLD_MAJOR="${OLD_VERSION%%.*}"
OLD_MINOR_REST="${OLD_VERSION#*.}"
OLD_MINOR="${OLD_MINOR_REST%%.*}"
OLD_API_VERSION="${OLD_MAJOR}.${OLD_MINOR}.x"

escape_sed() { printf '%s' "$1" | sed -e 's/[][\\/.^$*]/\\&/g'; }
sed_i() {
  if grep -q GNU <<<"$(sed --version 2>/dev/null || true)"; then sed -i "$@"; else sed -i '' "$@"; fi
}

if is_dry_run; then
  log_dry "would: rewrite version refs across changelog, jekyll config, packages, docs"
  log_dry "unmarked references to $OLD_VERSION that read as history (the rewrite would bump them):"
  python3 "$SCRIPT_DIR/version_refs.py" check "$REPO_ROOT" \
    --version "$OLD_VERSION" --api-version "$OLD_API_VERSION" --warn-only
else
  # Roll the changelog: open a fresh empty [Unreleased] and stamp a dated
  # [RELEASE_VERSION] section from the previous Unreleased content.
  #
  # IDEMPOTENT: only roll if a [RELEASE_VERSION] section does not already exist.
  # This step runs on full, maven-only AND post-maven, and the sed below matches
  # the always-present "## [Unreleased]" header — so without this guard a retried
  # full run or a post-maven re-run inserts ANOTHER "## [RELEASE_VERSION]" block
  # below the re-created Unreleased header every single time it runs. That is the
  # bug that duplicated the 7.1.0 section across its repeated re-runs (its many
  # "update version references to 7.1.0" commits), later cleaned up by hand in
  # commit af8d1a3e5 "removed duplicate entries from the changelog.md".
  RELEASE_VERSION_RE=$(printf '%s' "$RELEASE_VERSION" | sed 's/\./\\./g')
  if grep -qE "^## \[$RELEASE_VERSION_RE\]" "$REPO_ROOT/changelog.md"; then
    log_info "changelog.md already has a [$RELEASE_VERSION] section — skipping changelog rollover (idempotent re-run)"
  else
    TODAY=$(date +%Y-%m-%d)
    sed_i "s/^## \[Unreleased\]/## [Unreleased]\n\n### Added\n\n### Changed\n\n### Fixed\n\n## [$RELEASE_VERSION] - $TODAY/" "$REPO_ROOT/changelog.md"
  fi

  JEKYLL_CONFIG="$REPO_ROOT/jekyll-www.mock-server.com/_config.yml"
  sed_i "s/^mockserver_version: .*/mockserver_version: $RELEASE_VERSION/" "$JEKYLL_CONFIG"
  sed_i "s/^mockserver_api_version: .*/mockserver_api_version: $API_VERSION/" "$JEKYLL_CONFIG"
  sed_i "s/^mockserver_snapshot_version: .*/mockserver_snapshot_version: $NEXT_VERSION/" "$JEKYLL_CONFIG"

  # OpenAPI spec version is bumped by prepare.sh (audit F-SH-01) so that
  # swaggerhub.sh in the parallel publish group uploads a spec whose body
  # matches its registry version label. No bump needed here.

  for PKG_DIR in mockserver-node mockserver-client-node; do
    PKG_FILE="$REPO_ROOT/$PKG_DIR/package.json"
    if [[ -f "$PKG_FILE" ]]; then
      TMP="$REPO_ROOT/.tmp/pkg-$PKG_DIR.json"
      mkdir -p "$REPO_ROOT/.tmp"
      jq --arg v "$RELEASE_VERSION" '.version = $v' "$PKG_FILE" > "$TMP" && mv "$TMP" "$PKG_FILE"
    fi
  done
  if [[ -f "$REPO_ROOT/mockserver-node/package.json" ]]; then
    sed_i "s/$(escape_sed "mockserver-netty-${OLD_VERSION}-jar-with-dependencies.jar")/mockserver-netty-${RELEASE_VERSION}-jar-with-dependencies.jar/g" \
      "$REPO_ROOT/mockserver-node/package.json"
  fi
  if [[ -f "$REPO_ROOT/mockserver-client-node/package.json" ]]; then
    TMP="$REPO_ROOT/.tmp/pkg-client-node.json"
    jq --arg v "$RELEASE_VERSION" '.devDependencies["mockserver-node"] = $v' \
      "$REPO_ROOT/mockserver-client-node/package.json" > "$TMP" \
      && mv "$TMP" "$REPO_ROOT/mockserver-client-node/package.json"
  fi

  PYPROJECT="$REPO_ROOT/mockserver-client-python/pyproject.toml"
  [[ -f "$PYPROJECT" ]] && sed_i "s/^version = \".*\"/version = \"$RELEASE_VERSION\"/" "$PYPROJECT"

  VERSION_RB="$REPO_ROOT/mockserver-client-ruby/lib/mockserver/version.rb"
  [[ -f "$VERSION_RB" ]] && sed_i "s/VERSION = '.*'/VERSION = '$RELEASE_VERSION'/" "$VERSION_RB"

  # Bump the pinned Testcontainers image tag in Java test source. The general
  # find-and-replace below deliberately excludes *.java (blanket-rewriting version
  # strings in Java source is dangerous), so this one legitimate Java version
  # reference is handled here with a PRECISE, field-targeted sed — it matches ONLY
  # the "mockserver/mockserver:mockserver-<version>" image tag, never an arbitrary
  # version-shaped token elsewhere in the file. This is the pin that
  # MockServerContainerIntegrationTest starts and that PinnedImageVersionTest
  # asserts stays in major.minor lockstep with the project version; before this was
  # automated the pin drifted at the 8.0.0 release (a 7.6.0 image against an 8.0
  # client) and broke master. Keep the pin on one line in this exact tag form.
  TESTCONTAINERS_PIN="$REPO_ROOT/mockserver/mockserver-testcontainers/src/test/java/org/mockserver/testcontainers/TestcontainersImages.java"
  # Fail loudly rather than silently doing nothing. A renamed file or a reformatted
  # literal would make the sed a no-op that neither errors nor logs, which is how the
  # pin drifted in the first place: the instruction to bump it lived only in a comment.
  # PinnedImageVersionTest would still catch it, but not until the next CI run AFTER
  # the release had shipped. Checking here moves the failure back into the release.
  if [[ ! -f "$TESTCONTAINERS_PIN" ]]; then
    log_error "Testcontainers image pin not found at $TESTCONTAINERS_PIN — it was moved or renamed; update this step"
    exit 1
  fi
  if ! grep -qE "mockserver/mockserver:mockserver-[0-9]+\.[0-9]+\.[0-9]+" "$TESTCONTAINERS_PIN"; then
    log_error "Testcontainers image pin in $TESTCONTAINERS_PIN is not in the expected mockserver/mockserver:mockserver-<version> form — cannot bump it safely"
    exit 1
  fi
  sed_i -E \
    "s#(mockserver/mockserver:mockserver-)[0-9]+\.[0-9]+\.[0-9]+([^0-9\"][^\"]*)?\"#\1${RELEASE_VERSION}\2\"#g" \
    "$TESTCONTAINERS_PIN"
  if ! grep -qF "mockserver/mockserver:mockserver-${RELEASE_VERSION}" "$TESTCONTAINERS_PIN"; then
    log_error "Testcontainers image pin was not rewritten to ${RELEASE_VERSION} in $TESTCONTAINERS_PIN"
    exit 1
  fi
  log_info "Testcontainers image pin set to mockserver/mockserver:mockserver-${RELEASE_VERSION}"

  # Bump every reference to the old version in the docs, configs and example
  # manifests, except lines explicitly marked as history (version-ref:keep, a
  # keep block, or the KEEP_FILES/KEEP_LINES lists in version_refs.py). Rules:
  # docs/operations/release-process.md#version-references
  python3 "$SCRIPT_DIR/version_refs.py" rewrite "$REPO_ROOT" \
    --old "$OLD_VERSION" --new "$RELEASE_VERSION" \
    --old-api "$OLD_API_VERSION" --new-api "$API_VERSION"
fi

log_info "Diff summary:"
# --no-pager: Buildkite allocates a PTY, so git's stdout is a TTY and a bare
# `git diff --stat` launches the pager (less), which blocks on input until the
# job hits its timeout. This is what timed out the Update Version References
# step in release build #49.
git -C "$REPO_ROOT" --no-pager diff --stat

if is_dry_run; then
  log_dry "skip: commit + push of version references"
else
  # Explicit path list — never `.` — so untracked files on the agent (build
  # artifacts, .tmp/ files, anything left behind by an earlier step) don't
  # get committed by accident.
  declare -a UPDATED_PATHS=(
    changelog.md
    jekyll-www.mock-server.com/_config.yml
  )
  # NOTE: the OpenAPI spec used to be listed here, but prepare.sh now bumps
  # it as part of the non-Maven manifest pass and commits it in the prepare
  # commit (audit F-SH-01) so swaggerhub.sh uploads a spec with the correct
  # internal version. Don't add it back here.
  [[ -f mockserver-node/package.json ]]            && UPDATED_PATHS+=(mockserver-node/package.json)
  [[ -f mockserver-client-node/package.json ]]     && UPDATED_PATHS+=(mockserver-client-node/package.json)
  [[ -f mockserver-client-python/pyproject.toml ]] && UPDATED_PATHS+=(mockserver-client-python/pyproject.toml)
  [[ -f mockserver-client-ruby/lib/mockserver/version.rb ]] && UPDATED_PATHS+=(mockserver-client-ruby/lib/mockserver/version.rb)
  [[ -f mockserver-client-ruby/README.md ]]        && UPDATED_PATHS+=(mockserver-client-ruby/README.md)
  # Pinned Testcontainers image tag (bumped by the precise sed above; *.java is
  # excluded from the general find-and-replace, so it must be staged explicitly).
  TESTCONTAINERS_PIN_REL="mockserver/mockserver-testcontainers/src/test/java/org/mockserver/testcontainers/TestcontainersImages.java"
  [[ -f "$TESTCONTAINERS_PIN_REL" ]]               && UPDATED_PATHS+=("$TESTCONTAINERS_PIN_REL")
  # General find-and-replace touched docs across the repo. Stage only the
  # files that the find/replace loop above actually edited — NEVER a catch-all
  # `git diff --name-only` which would stage unrelated pre-existing changes
  # from an earlier step.
  # Re-run the same find as above and collect only files that have a diff.
  for ext in "*.html" "*.md" "*.yaml" "*.yml" "*.json" "*.txt"; do
    while IFS= read -r -d '' file; do
      # Relativize to repo root for git staging.
      local_path="${file#"$REPO_ROOT/"}"
      if ! git -C "$REPO_ROOT" diff --quiet -- "$local_path" 2>/dev/null; then
        UPDATED_PATHS+=("$local_path")
      fi
    done < <(find "$REPO_ROOT" -name "$ext" \
      -not -path "*/node_modules/*" -not -path "*/.git/*" \
      -not -path "*/target/*" -not -path "*/helm/charts/*" \
      -not -path "*/.tmp/*" \
      -not -name "changelog.md" -not -name "CHANGELOG.md" \
      -not -path "*/mockserver-node/package.json" \
      -not -path "*/mockserver-client-node/package.json" \
      -not -path "*/mockserver-ui/package.json" \
      -not -name "package-lock.json" -print0 2>/dev/null)
  done
  # De-duplicate.
  mapfile -t UPDATED_PATHS < <(printf '%s\n' "${UPDATED_PATHS[@]}" | sort -u)
  git_commit_and_push "release: update version references to $RELEASE_VERSION" "${UPDATED_PATHS[@]}"
fi

log_info "Update version references complete"
