#!/usr/bin/env bash
# Build (and verify) the self-contained, JVM-less MockServer binary bundle.
#
# Convenience wrapper around scripts/build-binary-bundle.sh (host platform) and
# scripts/build-all-bundles.sh (every platform). It:
#   1. resolves the project version from mockserver/pom.xml (override with --version),
#   2. locates the shaded jar (mockserver-netty-no-dependencies), building it with
#      `mvn package` if missing or when --rebuild is given,
#   3. pins jlink to a JDK 21 home (the version the release ships) so the bundle does
#      not silently pick up a different JDK via /usr/libexec/java_home,
#   4. builds the bundle(s), then (host build only, unless --no-verify) extracts the
#      archive and runs the launcher with a stripped environment to prove it needs
#      no external JVM.
#
# Usage:
#   scripts/build-standalone-binary.sh [options]
#
# Options:
#   --all                 Build every platform (linux/darwin/windows x x86_64/aarch64)
#                         via build-all-bundles.sh instead of just the host platform.
#   --version <ver>       Override the version label (default: from mockserver/pom.xml).
#   --jar <path>          Use this shaded jar instead of locating/building one.
#   --rebuild             Force `mvn package` of the shaded jar even if one exists.
#   --jlink-home <dir>    JDK home providing jlink (default: $JAVA_HOME, must be JDK 21).
#   --output <dir>        Output directory for bundles (default: .tmp/bundles).
#   --no-verify           Skip the post-build standalone run check (host build only).
#   --targets "<list>"    Forwarded to build-all-bundles.sh with --all (e.g.
#                         "linux/x86_64 windows/x86_64").
#   -h, --help            Show this help.
#
# Examples:
#   scripts/build-standalone-binary.sh                 # host bundle + verify
#   scripts/build-standalone-binary.sh --rebuild       # rebuild jar, then host bundle
#   scripts/build-standalone-binary.sh --all           # all platforms (downloads JDKs)
#   scripts/build-standalone-binary.sh --all --targets "linux/x86_64 windows/x86_64"

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# ---- defaults --------------------------------------------------------------
ALL=false
VERSION=""
JAR=""
REBUILD=false
JLINK_HOME="${JAVA_HOME:-}"
OUTPUT=".tmp/bundles"
VERIFY=true
TARGETS=""

log()  { printf '\033[1;34m[standalone]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[standalone] ERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# ---- args ------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --all)         ALL=true; shift ;;
    --version)     VERSION="$2"; shift 2 ;;
    --jar)         JAR="$2"; shift 2 ;;
    --rebuild)     REBUILD=true; shift ;;
    --jlink-home)  JLINK_HOME="$2"; shift 2 ;;
    --output)      OUTPUT="$2"; shift 2 ;;
    --no-verify)   VERIFY=false; shift ;;
    --targets)     TARGETS="$2"; shift 2 ;;
    -h|--help)     grep '^# ' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

# ---- resolve version -------------------------------------------------------
if [[ -z "$VERSION" ]]; then
  # First <version> outside any <parent> block = the project version (robust if a <parent> with its
  # own <version> is later added above the project coordinates).
  VERSION="$(awk '/<parent>/{p=1} /<\/parent>/{p=0;next} !p && /<version>/{gsub(/.*<version>|<\/version>.*|[[:space:]]/,"");print;exit}' mockserver/pom.xml)"
  [[ -n "$VERSION" ]] || die "could not resolve version from mockserver/pom.xml (pass --version)"
fi
log "Version: $VERSION"

# ---- resolve / validate jlink (JDK 21 to match the release) ---------------
[[ -n "$JLINK_HOME" ]] || die "no JDK home found; set \$JAVA_HOME or pass --jlink-home"
[[ -x "$JLINK_HOME/bin/jlink" ]] || die "jlink not found at $JLINK_HOME/bin/jlink (pass --jlink-home)"
JLINK_MAJOR="$("$JLINK_HOME/bin/jlink" --version 2>&1 | grep -oE '^[0-9]+' | head -1 || true)"
[[ "$JLINK_MAJOR" == "21" ]] || die "release bundles use JDK 21 jlink; $JLINK_HOME is JDK ${JLINK_MAJOR:-unknown} (pass --jlink-home <JDK 21>)"
log "jlink: JDK $JLINK_MAJOR ($JLINK_HOME)"

# ---- locate or build the shaded jar ---------------------------------------
find_shaded() {
  find mockserver/mockserver-netty-no-dependencies/target \
    -name 'mockserver-netty-no-dependencies-*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*' \
    -print -quit 2>/dev/null || true
}

if [[ -z "$JAR" ]]; then
  JAR="$(find_shaded)"
  if [[ -z "$JAR" || "$REBUILD" == true ]]; then
    # Two steps, because mockserver-netty-no-dependencies' maven-shade-plugin resolves the
    # mockserver-netty dependency from the local repository, NOT the reactor. A single
    # `-am package` (or even `-am install`) can silently shade a STALE ~/.m2 netty jar when local
    # sources changed. So:
    #   1. install the dependency chain to ~/.m2 (refreshes netty/async from current sources),
    #   2. re-shade no-dependencies on its own (no -am) so the shade reads the just-installed jars.
    # jacoco.skip: `install` runs jacoco:check, which fails with skipped tests (no coverage data).
    log "Refreshing ~/.m2 (mvn install mockserver-netty-no-dependencies -am) ..."
    ( cd mockserver && mvn -q -DskipTests -DskipITs -Djacoco.skip=true -pl mockserver-netty-no-dependencies -am install )
    log "Re-shading no-dependencies jar against refreshed ~/.m2 ..."
    ( cd mockserver && mvn -q -DskipTests -Djacoco.skip=true -pl mockserver-netty-no-dependencies clean package )
    JAR="$(find_shaded)"
  fi
fi
[[ -n "$JAR" && -f "$JAR" ]] || die "no shaded jar available (try --rebuild)"
log "Using jar: $JAR"

# ---- build the bundle(s) ---------------------------------------------------
if [[ "$ALL" == true ]]; then
  log "Building all-platform bundles -> $OUTPUT"
  TARGETS_ARG=()
  [[ -n "$TARGETS" ]] && TARGETS_ARG=(--targets "$TARGETS")
  JAVA_HOME="$JLINK_HOME" scripts/build-all-bundles.sh \
    --jar "$JAR" --version "$VERSION" --cache .tmp/jdks --output "$OUTPUT" \
    "${TARGETS_ARG[@]+"${TARGETS_ARG[@]}"}"
  log "Done. Artifacts in $OUTPUT:"
  ls -1 "$OUTPUT"/mockserver-"$VERSION"-*.{tar.gz,zip} 2>/dev/null >&2 || true
  exit 0
fi

log "Building host-platform bundle -> $OUTPUT"
ARCHIVE="$(scripts/build-binary-bundle.sh \
  --jar "$JAR" --version "$VERSION" --jlink-home "$JLINK_HOME" --output "$OUTPUT")"
log "Bundle: $ARCHIVE"

# ---- verify the bundle runs with no external JVM --------------------------
if [[ "$VERIFY" == true && "$ARCHIVE" == *.tar.gz ]]; then
  log "Verifying bundle runs standalone (stripped environment) ..."
  VDIR="$OUTPUT/.verify"
  rm -rf "$VDIR"; mkdir -p "$VDIR"
  tar -xzf "$ARCHIVE" -C "$VDIR"
  BUNDLE_DIR="$(find "$VDIR" -maxdepth 1 -type d -name 'mockserver-*' | head -1)"
  [[ -n "$BUNDLE_DIR" ]] || die "no mockserver-* directory found in extracted bundle at $VDIR"
  [[ -x "$BUNDLE_DIR/bin/mockserver" ]] || die "launcher not found in extracted bundle"
  if command -v sha256sum >/dev/null 2>&1; then
    ( cd "$OUTPUT" && sha256sum -c "$(basename "$ARCHIVE").sha256" ) >&2
  else
    ( cd "$OUTPUT" && shasum -a 256 -c "$(basename "$ARCHIVE").sha256" ) >&2
  fi
  OUT="$(env -i HOME="$HOME" "$BUNDLE_DIR/bin/mockserver" version 2>&1 | tail -1)"
  rm -rf "$VDIR"
  [[ "$OUT" == *"$VERSION"* ]] || die "standalone run did not report version $VERSION (got: $OUT)"
  log "Verified: '$OUT' (ran on the bundled runtime only)"
fi

echo "$ARCHIVE"
