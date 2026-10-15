#!/usr/bin/env bash
# Finalize a release: bump the root pom to the next SNAPSHOT, deploy that
# SNAPSHOT to Sonatype, commit, push.
#
# Version references in docs/configs across the repo (changelog, jekyll
# _config.yml, package.json files, etc.) used to be updated at the tail of
# this script. They were extracted into update-version-references.sh and
# moved earlier in the release pipeline so that the parallel deploy group
# (website, helm, docker, npm) reads the new version when it runs.
#
# Dry-run: skip the SNAPSHOT deploy and git push; everything else still runs.

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
require_release_inputs
skip_unless_release_type "finalize" full,maven-only,post-maven

log_step "Finalize release $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

# ---- Idempotent re-run guard -----------------------------------------------
# A prior finalize (e.g. an earlier post-maven build) may have already bumped the
# committed pom to NEXT_VERSION, deployed the SNAPSHOT and pushed. The commit is
# the LAST step here (after `mvn deploy`), so a pom at NEXT_VERSION on master
# means the deploy already succeeded. Without this guard, a re-run calls
# update_pom_versions looking for <version>RELEASE_VERSION</version>, finds none,
# and hard-fails ("no pom.xml contained <version>X</version>"). Detect the
# already-finalized state (no RELEASE_VERSION left, NEXT_VERSION present) and
# treat it as success.
#
# Safety notes:
#  - sync_to_origin_master above has reset local disk to origin/master, so the
#    guard reads COMMITTED master state, not transient local edits. A partial run
#    that deployed but died before the push leaves RELEASE_VERSION on master, so
#    the guard does NOT fire and the re-run correctly redoes the work.
#  - poms_contain_version is a whole-file substring match for the exact tag
#    <version>X</version>, mirroring update_pom_versions. It is NOT scoped to the
#    <project><version> element, so it would false-positive if some pom ever
#    carried NEXT_VERSION in a <dependency>/<plugin> block before finalize ran.
#    No repo pom does today (the reactor poms only ever reference the in-tree
#    version as the project version); revisit this guard if that changes.
if ! is_dry_run \
   && ! poms_contain_version "$REPO_ROOT" "$RELEASE_VERSION" \
   && poms_contain_version "$REPO_ROOT" "$NEXT_VERSION"; then
  log_info "pom.xml already at $NEXT_VERSION — finalize previously applied; skipping bump, SNAPSHOT deploy and commit (idempotent no-op)"
  release_gate clear
  log_info "Finalize complete"
  exit 0
fi

# ---- Deploy next SNAPSHOT to Sonatype --------------------------------------
log_info "Bump pom.xml: $RELEASE_VERSION -> $NEXT_VERSION"
if is_dry_run; then
  log_dry "would: update_pom_versions \$REPO_ROOT $RELEASE_VERSION $NEXT_VERSION"
else
  # Scan the whole repo (not just mockserver/): examples/java lives outside
  # mockserver/ and is a standalone build whose parent is ../../mockserver/pom.xml
  # (no longer a reactor module). If it is left at the released version while the
  # reactor moves to the next SNAPSHOT, its parent reference dangles and the CI
  # examples build (scripts/buildkite_quick_build.sh) can no longer resolve the
  # parent. Bumping it here keeps it attached; it is also committed explicitly
  # below. Mirrors the same scan in prepare.sh.
  update_pom_versions "$REPO_ROOT" "$RELEASE_VERSION" "$NEXT_VERSION"
fi

if is_dry_run; then
  log_dry "skip: mvn deploy SNAPSHOT to Sonatype"
else
  SONATYPE_USERNAME=$(load_secret "mockserver-build/sonatype" "username")
  SONATYPE_PASSWORD=$(load_secret "mockserver-build/sonatype" "password")
  in_maven -w /build/mockserver \
    --secret-env "SONATYPE_USERNAME=$SONATYPE_USERNAME" \
    --secret-env "SONATYPE_PASSWORD=$SONATYPE_PASSWORD" \
    -- mvn -T 1C clean deploy -DskipTests -DskipITs \
         -Djava.security.egd=file:/dev/./urandom \
         --settings .buildkite-settings.xml
fi

git_commit_and_push "release: set next development version $NEXT_VERSION" \
  mockserver/ \
  examples/java/pom.xml

# Release finished cleanly — release the Dependabot auto-merge hold.
release_gate clear

log_info "Finalize complete"
