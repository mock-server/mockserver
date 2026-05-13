#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd sed
require_cmd jq

escape_sed_pattern() {
  printf '%s' "$1" | sed -e 's/[][\\/.^$*]/\\&/g'
}

escape_sed_replacement() {
  printf '%s' "$1" | sed -e 's/[\\/&]/\\&/g'
}

log_step "Updating version references from $OLD_VERSION to $RELEASE_VERSION"

cd "$REPO_ROOT"

if [[ -z "$CURRENT_VERSION" || ! "$CURRENT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
  log_error "CURRENT_VERSION must be captured during validation before updating versions"
  exit 1
fi

MAJOR="${RELEASE_VERSION%%.*}"
MINOR_REST="${RELEASE_VERSION#*.}"
MINOR="${MINOR_REST%%.*}"
API_VERSION="${MAJOR}.${MINOR}.x"

OLD_MAJOR="${OLD_VERSION%%.*}"
OLD_MINOR_REST="${OLD_VERSION#*.}"
OLD_MINOR="${OLD_MINOR_REST%%.*}"
OLD_API_VERSION="${OLD_MAJOR}.${OLD_MINOR}.x"

log_info "Version mapping:"
log_info "  $OLD_VERSION -> $RELEASE_VERSION"
log_info "  $OLD_API_VERSION -> $API_VERSION"
log_info "  $CURRENT_VERSION -> $NEXT_VERSION"

OLD_VERSION_PATTERN=$(escape_sed_pattern "$OLD_VERSION")
RELEASE_VERSION_REPLACEMENT=$(escape_sed_replacement "$RELEASE_VERSION")
OLD_API_VERSION_PATTERN=$(escape_sed_pattern "$OLD_API_VERSION")
API_VERSION_REPLACEMENT=$(escape_sed_replacement "$API_VERSION")
CURRENT_VERSION_PATTERN=$(escape_sed_pattern "$CURRENT_VERSION")
NEXT_VERSION_REPLACEMENT=$(escape_sed_replacement "$NEXT_VERSION")

log_info "Updating changelog"
TODAY=$(date +%Y-%m-%d)
sed_i "s/^## \[Unreleased\]/## [Unreleased]\n\n### Added\n\n### Changed\n\n### Fixed\n\n## [$RELEASE_VERSION] - $TODAY/" changelog.md

log_info "Updating Jekyll config"
JEKYLL_CONFIG="jekyll-www.mock-server.com/_config.yml"
sed_i "s/^mockserver_version: .*/mockserver_version: $RELEASE_VERSION/" "$JEKYLL_CONFIG"
sed_i "s/^mockserver_api_version: .*/mockserver_api_version: $API_VERSION/" "$JEKYLL_CONFIG"
sed_i "s/^mockserver_snapshot_version: .*/mockserver_snapshot_version: $NEXT_VERSION/" "$JEKYLL_CONFIG"

log_info "Updating OpenAPI spec"
OPENAPI_SPEC="mockserver/mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml"
sed_i "s/^  version: .*/  version: $RELEASE_VERSION/" "$OPENAPI_SPEC"

log_info "Updating Node packages"
for PKG_DIR in mockserver-node mockserver-client-node; do
  PKG_FILE="$PKG_DIR/package.json"
  if [[ -f "$PKG_FILE" ]]; then
    TMP_FILE="$REPO_ROOT/.tmp/pkg-$PKG_DIR.json"
    mkdir -p "$REPO_ROOT/.tmp"
    jq --arg v "$RELEASE_VERSION" '.version = $v' "$PKG_FILE" > "$TMP_FILE" && mv "$TMP_FILE" "$PKG_FILE"
  fi
done

if [[ -f "mockserver-node/package.json" ]]; then
  OLD_JAR_PATTERN=$(escape_sed_pattern "mockserver-netty-${OLD_VERSION}-jar-with-dependencies.jar")
  NEW_JAR_REPLACEMENT=$(escape_sed_replacement "mockserver-netty-${RELEASE_VERSION}-jar-with-dependencies.jar")
  sed_i "s/${OLD_JAR_PATTERN}/${NEW_JAR_REPLACEMENT}/g" mockserver-node/package.json
fi

if [[ -f "mockserver-client-node/package.json" ]]; then
  TMP_FILE="$REPO_ROOT/.tmp/pkg-client-node.json"
  jq --arg v "$RELEASE_VERSION" '.devDependencies["mockserver-node"] = $v' mockserver-client-node/package.json > "$TMP_FILE" && mv "$TMP_FILE" mockserver-client-node/package.json
fi

log_info "Updating Python package"
PYPROJECT="mockserver-client-python/pyproject.toml"
if [[ -f "$PYPROJECT" ]]; then
  sed_i "s/^version = \".*\"/version = \"$RELEASE_VERSION\"/" "$PYPROJECT"
fi

log_info "Updating Ruby package"
VERSION_RB="mockserver-client-ruby/lib/mockserver/version.rb"
if [[ -f "$VERSION_RB" ]]; then
  sed_i "s/VERSION = '.*'/VERSION = '$RELEASE_VERSION'/" "$VERSION_RB"
fi
RUBY_README="mockserver-client-ruby/README.md"
if [[ -f "$RUBY_README" ]]; then
  sed_i "s/$OLD_VERSION/$RELEASE_VERSION/g" "$RUBY_README"
fi

log_info "Running general find-and-replace across docs and config"
FIND_REPLACE_EXTS=( "*.html" "*.md" "*.yaml" "*.yml" "*.json" )
EXCLUDES=( "changelog.md" "CHANGELOG.md" "node_modules" ".git" "target" "helm/charts" ".tmp" )

build_exclude_args() {
  local args=""
  for excl in "${EXCLUDES[@]}"; do
    args="$args --exclude-dir=$excl"
  done
  echo "$args"
}

for ext in "${FIND_REPLACE_EXTS[@]}"; do
  find . -name "$ext" \
    -not -path "*/node_modules/*" \
    -not -path "*/.git/*" \
    -not -path "*/target/*" \
    -not -path "*/helm/charts/*" \
    -not -path "*/.tmp/*" \
    -not -name "changelog.md" \
    -not -name "CHANGELOG.md" \
    -not -name "package-lock.json" \
    -print0 2>/dev/null | while IFS= read -r -d '' file; do
      sed_i "s/${OLD_VERSION_PATTERN}/${RELEASE_VERSION_REPLACEMENT}/g" "$file" 2>/dev/null || true
      if [[ "$OLD_API_VERSION" != "$API_VERSION" ]]; then
        sed_i "s/${OLD_API_VERSION_PATTERN}/${API_VERSION_REPLACEMENT}/g" "$file" 2>/dev/null || true
      fi
      if [[ "$CURRENT_VERSION" != "$NEXT_VERSION" ]]; then
        sed_i "s/${CURRENT_VERSION_PATTERN}/${NEXT_VERSION_REPLACEMENT}/g" "$file" 2>/dev/null || true
      fi
    done
done

log_info "Cleaning build artifacts"
cd "$REPO_ROOT/mockserver" && ./mvnw clean -q 2>/dev/null || true
rm -rf "$REPO_ROOT/jekyll-www.mock-server.com/_site" 2>/dev/null || true
cd "$REPO_ROOT"

log_info "Validation: review the following changes"
echo ""
git diff --stat
echo ""

if is_ci; then
  git diff --stat | buildkite-agent annotate --style info --context version-update 2>/dev/null || true
else
  confirm "Version update looks correct? Commit and push?"
fi

git add -A
git commit -m "release: update version references to $RELEASE_VERSION"
git push origin master

log_info "Version references updated to $RELEASE_VERSION"
