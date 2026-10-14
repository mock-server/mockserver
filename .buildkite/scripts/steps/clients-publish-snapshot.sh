#!/usr/bin/env bash
# DRAFT (2026-06-11): publish a PRE-RELEASE ("snapshot") of each language client so a
# release can be smoke-tested from real registries BEFORE the full release pipeline runs.
#
# Opt-in: this step only does anything when PUBLISH_CLIENT_SNAPSHOTS=true (a build env
# var / pipeline meta-data). It is NOT meant to run on every master commit — pre-releases
# on npm/PyPI/crates.io/NuGet are effectively permanent and would clutter the registries.
# Trigger it deliberately when you want to test the next version end-to-end.
#
# FAIL-OPEN BY DESIGN: every client is gated on its registry secret + CLI tooling being
# present. If a secret/tool is missing the client is SKIPPED (not failed), so this step
# stays green before the one-time manual provisioning (NuGet/crates secrets, Packagist
# registration, owning the MockServerClient id) has been done. The pipeline step is also
# soft_fail as a backstop. See docs/plans/distribution-exposure-channels.local.md.
#
# Version scheme (derived from mockserver/pom.xml, which is X.Y.Z-SNAPSHOT on master):
#   BASE = X.Y.Z (pom version with -SNAPSHOT stripped)
#   semver registries (NuGet, crates.io, npm):  BASE-SNAPSHOT.<build>   e.g. 7.0.1-SNAPSHOT.123
#   PyPI (PEP 440):                             BASE.dev<build>         e.g. 7.0.1.dev123
#   RubyGems (prerelease):                      BASE.pre.<build>        e.g. 7.0.1.pre.123
# The per-build suffix keeps each publish unique (immutable registries reject re-publishing
# the same version).
#
# Go and PHP are intentionally NOT published here: a pre-release is already available from
# version control with no extra publish — `go get <module>@master` resolves a pseudo-version
# of the latest master commit, and Composer `"mock-server/mockserver-client": "dev-master"`
# (or `7.0.x-dev`) tracks master via Packagist once the package is registered.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
REGION="${AWS_REGION:-eu-west-2}"
BUILD="${BUILDKITE_BUILD_NUMBER:-0}"

# ---------------------------------------------------------------------------
# Opt-in guard
# ---------------------------------------------------------------------------
if [[ "${PUBLISH_CLIENT_SNAPSHOTS:-false}" != "true" ]]; then
  echo "--- :fast_forward: Skipping client snapshots — set PUBLISH_CLIENT_SNAPSHOTS=true to enable"
  exit 0
fi

# ---------------------------------------------------------------------------
# Version guard: only meaningful on a -SNAPSHOT pom (mirrors java-deploy-snapshot.sh)
# ---------------------------------------------------------------------------
POM_VERSION=$(grep -m1 -oE '<version>[^<]+</version>' "$REPO_ROOT/mockserver/pom.xml" | head -1 | sed -E 's#</?version>##g')
if [[ "$POM_VERSION" != *-SNAPSHOT ]]; then
  echo "--- :fast_forward: Skipping client snapshots — pom is on release version $POM_VERSION (not a -SNAPSHOT)"
  exit 0
fi
BASE="${POM_VERSION%-SNAPSHOT}"
SEMVER_SNAP="${BASE}-SNAPSHOT.${BUILD}"   # NuGet / crates.io / npm
PEP440_SNAP="${BASE}.dev${BUILD}"         # PyPI
GEM_SNAP="${BASE}.pre.${BUILD}"           # RubyGems
echo "--- :package: Publishing client snapshots for base $BASE (build $BUILD)"

# SUMMARY collects a one-line status per client for the final report.
SUMMARY=()
note()  { echo "    $*"; }
record(){ SUMMARY+=("$1"); }

have()           { command -v "$1" >/dev/null 2>&1; }
secret_present() { aws secretsmanager describe-secret --region "$REGION" --secret-id "$1" >/dev/null 2>&1; }

# Read one JSON key out of a Secrets Manager secret with xtrace suppressed.
# NOTE: xtrace is intentionally left OFF after the first call — re-enabling it inline could
# leak a token into the build log. Do not add `set -x` back here.
load_secret() {
  local secret_id="$1" key="$2" json
  { set +x; } 2>/dev/null
  json=$(aws secretsmanager get-secret-value --region "$REGION" --secret-id "$secret_id" --query SecretString --output text)
  echo "$json" | jq -r ".$key"
}

# Restore a tracked file we mutated for the version bump, so later steps see a clean tree.
restore() { git -C "$REPO_ROOT" checkout -- "$1" 2>/dev/null || true; }

# ---------------------------------------------------------------------------
# .NET → NuGet (package id: MockServerClient; secret: mockserver-release/nuget)
# ---------------------------------------------------------------------------
publish_dotnet() {
  echo "--- :dotnet: .NET client snapshot $SEMVER_SNAP"
  have dotnet                          || { note "dotnet not on PATH — skip";                 record ".NET     SKIP (no dotnet)";  return; }
  secret_present mockserver-release/nuget || { note "mockserver-release/nuget secret absent — skip"; record ".NET     SKIP (no secret)";  return; }
  local dir="$REPO_ROOT/mockserver-client-dotnet"
  local key; key=$(load_secret mockserver-release/nuget api_key)
  if (cd "$dir" && dotnet pack src/MockServer.Client/MockServer.Client.csproj -c Release \
        -p:Version="$SEMVER_SNAP" -o ./artifacts-snapshot) \
     && dotnet nuget push "$dir/artifacts-snapshot/MockServerClient.${SEMVER_SNAP}.nupkg" \
        --api-key "$key" --source https://api.nuget.org/v3/index.json --skip-duplicate; then
    note "published MockServerClient $SEMVER_SNAP (install with --prerelease)"; record ".NET     OK   ($SEMVER_SNAP)"
  else
    note "pack/push failed — non-fatal"; record ".NET     FAIL"
  fi
  rm -rf "$dir/artifacts-snapshot" 2>/dev/null || true
}

# ---------------------------------------------------------------------------
# Rust → crates.io (crate: mockserver-client; secret: mockserver-release/crates)
# ---------------------------------------------------------------------------
publish_rust() {
  echo "--- :rust: Rust client snapshot $SEMVER_SNAP"
  have cargo                            || { note "cargo not on PATH — skip";                  record "Rust     SKIP (no cargo)";  return; }
  secret_present mockserver-release/crates || { note "mockserver-release/crates secret absent — skip"; record "Rust     SKIP (no secret)";  return; }
  local dir="$REPO_ROOT/mockserver-client-rust" rel="mockserver-client-rust/Cargo.toml"
  local token; token=$(load_secret mockserver-release/crates token)
  sed -i.bak "s/^version = \".*\"/version = \"${SEMVER_SNAP}\"/" "$dir/Cargo.toml" && rm -f "$dir/Cargo.toml.bak"
  if (cd "$dir" && CARGO_REGISTRY_TOKEN="$token" cargo publish --allow-dirty --no-verify); then
    note "published mockserver-client $SEMVER_SNAP"; record "Rust     OK   ($SEMVER_SNAP)"
  else
    note "cargo publish failed — non-fatal"; record "Rust     FAIL"
  fi
  restore "$rel"
}

# ---------------------------------------------------------------------------
# Node → npm (package: mockserver-client; secret: mockserver-release/npm-token, dist-tag: snapshot)
# ---------------------------------------------------------------------------
publish_node() {
  echo "--- :npm: Node client snapshot $SEMVER_SNAP"
  have npm                                 || { note "npm not on PATH — skip";                    record "Node     SKIP (no npm)";    return; }
  secret_present mockserver-release/npm-token || { note "mockserver-release/npm-token secret absent — skip"; record "Node     SKIP (no secret)"; return; }
  local dir="$REPO_ROOT/mockserver-client-node" token; token=$(load_secret mockserver-release/npm-token token)
  # .npmrc.snapshot holds the auth token; it is gitignored (mockserver-client-node/.gitignore)
  # and removed below. `npm version` mutates BOTH package.json and package-lock.json, so restore both.
  local npmrc="$dir/.npmrc.snapshot"
  { set +x; } 2>/dev/null
  printf '//registry.npmjs.org/:_authToken=%s\n' "$token" > "$npmrc"
  if (cd "$dir" && npm version "$SEMVER_SNAP" --no-git-tag-version >/dev/null \
        && npm publish --tag snapshot --userconfig "$npmrc" --access public); then
    note "published mockserver-client@$SEMVER_SNAP (npm install mockserver-client@snapshot)"; record "Node     OK   ($SEMVER_SNAP)"
  else
    note "npm publish failed — non-fatal"; record "Node     FAIL"
  fi
  rm -f "$npmrc"
  restore "mockserver-client-node/package.json"
  restore "mockserver-client-node/package-lock.json"
}

# ---------------------------------------------------------------------------
# Python → PyPI (package: mockserver-client; secret: mockserver-build/pypi)
# ---------------------------------------------------------------------------
publish_python() {
  echo "--- :python: Python client snapshot $PEP440_SNAP"
  have python3                               || { note "python3 not on PATH — skip";              record "Python   SKIP (no python)"; return; }
  python3 -m build --version  >/dev/null 2>&1 || { note "python 'build' module not available — skip"; record "Python   SKIP (no build)"; return; }
  python3 -m twine --version  >/dev/null 2>&1 || { note "twine not available — skip";             record "Python   SKIP (no twine)";  return; }
  secret_present mockserver-build/pypi       || { note "mockserver-build/pypi secret absent — skip"; record "Python   SKIP (no secret)"; return; }
  local dir="$REPO_ROOT/mockserver-client-python" rel="mockserver-client-python/pyproject.toml"
  local token; token=$(load_secret mockserver-build/pypi token)
  # Version is a static `version = "X.Y.Z"` in pyproject.toml (plain setuptools, no setuptools-scm),
  # so bump it the same way prepare.sh does, then build. PEP 440 .devN marks it a pre-release.
  sed -i.bak -E "s/^version = \"[^\"]+\"/version = \"${PEP440_SNAP}\"/" "$dir/pyproject.toml" && rm -f "$dir/pyproject.toml.bak"
  if (cd "$dir" && rm -rf dist && python3 -m build \
        && python3 -m twine upload --username __token__ --password "$token" --skip-existing dist/*); then
    note "published mockserver-client $PEP440_SNAP (pip install --pre mockserver-client)"; record "Python   OK   ($PEP440_SNAP)"
  else
    note "build/upload failed — non-fatal"; record "Python   FAIL"
  fi
  rm -rf "$dir/dist" 2>/dev/null || true
  restore "$rel"
}

# ---------------------------------------------------------------------------
# Ruby → RubyGems (gem: mockserver-client; secret: mockserver-build/rubygems)
# ---------------------------------------------------------------------------
publish_ruby() {
  echo "--- :rubygems: Ruby client snapshot $GEM_SNAP"
  have gem                              || { note "gem not on PATH — skip";                    record "Ruby     SKIP (no gem)";    return; }
  secret_present mockserver-build/rubygems || { note "mockserver-build/rubygems secret absent — skip"; record "Ruby     SKIP (no secret)"; return; }
  local dir="$REPO_ROOT/mockserver-client-ruby" rel="mockserver-client-ruby/lib/mockserver/version.rb"
  local key; key=$(load_secret mockserver-build/rubygems api_key)
  # The gemspec reads MockServer::VERSION from lib/mockserver/version.rb, so bump that file the same
  # way prepare.sh does. `gem build` then emits mockserver-client-<VERSION>.gem; a version containing
  # a letter (…pre…) is treated as a prerelease and not served by default (gem install --pre).
  sed -i.bak -E "s/VERSION = '[^']+'/VERSION = '${GEM_SNAP}'/" "$dir/lib/mockserver/version.rb" \
    && rm -f "$dir/lib/mockserver/version.rb.bak"
  if (cd "$dir" && gem build mockserver-client.gemspec \
        && GEM_HOST_API_KEY="$key" gem push "mockserver-client-${GEM_SNAP}.gem"); then
    note "published mockserver-client $GEM_SNAP (gem install --pre mockserver-client)"; record "Ruby     OK   ($GEM_SNAP)"
  else
    note "gem build/push failed — non-fatal"; record "Ruby     FAIL"
  fi
  rm -f "$dir/mockserver-client-${GEM_SNAP}.gem" 2>/dev/null || true
  restore "$rel"
}

# ---------------------------------------------------------------------------
# Go & PHP — no publish needed (snapshots come from VCS)
# ---------------------------------------------------------------------------
note_go_php() {
  echo "--- :go: :php: Go & PHP snapshots are available from version control (no publish):"
  note "Go : go get $(awk '/^module /{print $2; exit}' "$REPO_ROOT/mockserver-client-go/go.mod")@master"
  note 'PHP: composer require mock-server/mockserver-client:dev-master  (once registered on Packagist)'
  record "Go       N/A  (use @master pseudo-version)"
  record "PHP      N/A  (use dev-master)"
}

# Each client is isolated; one failing must not abort the others (the whole step is fail-open).
publish_dotnet || true
publish_rust   || true
publish_node   || true
publish_python || true
publish_ruby   || true
note_go_php    || true

echo ""
echo "--- :clipboard: Client snapshot summary (base $BASE, build $BUILD)"
printf '    %s\n' "${SUMMARY[@]}"
echo ""
echo ":white_check_mark: client-snapshot step complete (fail-open: SKIP/N/A/FAIL never redden the build)"
exit 0
