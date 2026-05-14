#!/usr/bin/env bash
# Finalize a release: deploy the next SNAPSHOT, update version references
# in docs/configs across the repo, commit, push.
#
# Dry-run: do the version-reference updates locally so the diff can be
# reviewed; skip the SNAPSHOT deploy + git push.

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

require_cmd docker
require_cmd git
require_cmd python3
require_cmd jq
require_cmd sed
require_release_inputs
skip_unless_release_type "finalize" full,maven-only

log_step "Finalize release $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

# ---- Deploy next SNAPSHOT to Sonatype --------------------------------------
log_info "Bump pom.xml: $RELEASE_VERSION -> $NEXT_VERSION"
if is_dry_run; then
  log_dry "would: update_pom_versions mockserver/ $RELEASE_VERSION $NEXT_VERSION"
else
  update_pom_versions "$REPO_ROOT/mockserver" "$RELEASE_VERSION" "$NEXT_VERSION"
fi

if is_dry_run; then
  log_dry "skip: mvn deploy SNAPSHOT to Sonatype"
else
  SONATYPE_USERNAME=$(load_secret "mockserver-build/sonatype" "username")
  SONATYPE_PASSWORD=$(load_secret "mockserver-build/sonatype" "password")
  in_docker "$MAVEN_IMAGE" \
    -w /build/mockserver \
    -v mockserver-m2-cache:/root/.m2 \
    -e "SONATYPE_USERNAME=$SONATYPE_USERNAME" \
    -e "SONATYPE_PASSWORD=$SONATYPE_PASSWORD" \
    -- mvn -T 1C clean deploy -DskipTests \
         -Djava.security.egd=file:/dev/./urandom \
         --settings .buildkite-settings.xml
fi

git_commit_and_push "release: set next development version $NEXT_VERSION" mockserver/

# ---- Update version references in docs/configs across the repo -------------
log_info "Update version references throughout repo"

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
  if sed --version 2>/dev/null | grep -q GNU; then sed -i "$@"; else sed -i '' "$@"; fi
}

if is_dry_run; then
  log_dry "would: rewrite version refs across changelog, jekyll config, openapi, packages, docs"
else
  TODAY=$(date +%Y-%m-%d)
  sed_i "s/^## \[Unreleased\]/## [Unreleased]\n\n### Added\n\n### Changed\n\n### Fixed\n\n## [$RELEASE_VERSION] - $TODAY/" "$REPO_ROOT/changelog.md"

  JEKYLL_CONFIG="$REPO_ROOT/jekyll-www.mock-server.com/_config.yml"
  sed_i "s/^mockserver_version: .*/mockserver_version: $RELEASE_VERSION/" "$JEKYLL_CONFIG"
  sed_i "s/^mockserver_api_version: .*/mockserver_api_version: $API_VERSION/" "$JEKYLL_CONFIG"
  sed_i "s/^mockserver_snapshot_version: .*/mockserver_snapshot_version: $NEXT_VERSION/" "$JEKYLL_CONFIG"

  OPENAPI_SPEC="$REPO_ROOT/mockserver/mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml"
  sed_i "s/^  version: .*/  version: $RELEASE_VERSION/" "$OPENAPI_SPEC"

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
  RUBY_README="$REPO_ROOT/mockserver-client-ruby/README.md"
  [[ -f "$RUBY_README" ]] && sed_i "s/$OLD_VERSION/$RELEASE_VERSION/g" "$RUBY_README"

  # General find-and-replace across docs (excluding changelog, target, etc.)
  OLD_PAT=$(escape_sed "$OLD_VERSION"); NEW_REP=$(escape_sed "$RELEASE_VERSION")
  OLD_API_PAT=$(escape_sed "$OLD_API_VERSION"); NEW_API=$(escape_sed "$API_VERSION")
  for ext in "*.html" "*.md" "*.yaml" "*.yml" "*.json"; do
    find "$REPO_ROOT" -name "$ext" \
      -not -path "*/node_modules/*" -not -path "*/.git/*" \
      -not -path "*/target/*" -not -path "*/helm/charts/*" \
      -not -path "*/.tmp/*" \
      -not -name "changelog.md" -not -name "CHANGELOG.md" \
      -not -name "package-lock.json" -print0 2>/dev/null \
    | while IFS= read -r -d '' file; do
        sed_i "s/${OLD_PAT}/${NEW_REP}/g" "$file" 2>/dev/null || true
        if [[ "$OLD_API_VERSION" != "$API_VERSION" ]]; then
          sed_i "s/${OLD_API_PAT}/${NEW_API}/g" "$file" 2>/dev/null || true
        fi
      done
  done
fi

log_info "Diff summary:"
git -C "$REPO_ROOT" diff --stat

if is_dry_run; then
  log_dry "skip: commit + push of version references"
else
  # Explicit path list — never `.` — so untracked files on the agent (build
  # artifacts, .tmp/ files, anything left behind by an earlier step) don't
  # get committed by accident.
  declare -a UPDATED_PATHS=(
    changelog.md
    jekyll-www.mock-server.com/_config.yml
    mockserver/mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml
    mockserver/
  )
  [[ -f mockserver-node/package.json ]]            && UPDATED_PATHS+=(mockserver-node/package.json)
  [[ -f mockserver-client-node/package.json ]]     && UPDATED_PATHS+=(mockserver-client-node/package.json)
  [[ -f mockserver-client-python/pyproject.toml ]] && UPDATED_PATHS+=(mockserver-client-python/pyproject.toml)
  [[ -f mockserver-client-ruby/lib/mockserver/version.rb ]] && UPDATED_PATHS+=(mockserver-client-ruby/lib/mockserver/version.rb)
  [[ -f mockserver-client-ruby/README.md ]]        && UPDATED_PATHS+=(mockserver-client-ruby/README.md)
  # General find-and-replace touched docs across the repo. We stage only files
  # that were already tracked AND have a diff.
  while IFS= read -r f; do
    UPDATED_PATHS+=("$f")
  done < <(git -C "$REPO_ROOT" diff --name-only)
  # De-duplicate.
  mapfile -t UPDATED_PATHS < <(printf '%s\n' "${UPDATED_PATHS[@]}" | sort -u)
  git_commit_and_push "release: update version references to $RELEASE_VERSION" "${UPDATED_PATHS[@]}"
fi

log_info "Finalize complete"
