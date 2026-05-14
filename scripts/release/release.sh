#!/usr/bin/env bash
# Run the full MockServer release end-to-end.
#
# Locally:
#   ./scripts/release/release.sh --version 6.0.0 --dry-run        # default safe mode
#   ./scripts/release/release.sh --version 6.0.0 --execute        # do it for real
#   ./scripts/release/release.sh --version 6.0.0 --only=npm,pypi  # just two components
#   ./scripts/release/release.sh --version 6.0.0 --skip=docker    # everything except docker
#
# In CI: same script, same args. The Buildkite adapter wraps it.
#
# Components are ordered so that downstream consumers (Docker, npm, etc.)
# can rely on the Maven Central artifacts being available.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib.sh"

# Component order. Each is a script under components/.
ALL_COMPONENTS=(
  maven-central
  maven-plugin
  versioned-site
  docker
  npm
  helm
  javadoc
  swaggerhub
  website
  schema
  pypi
  rubygems
  github
)

usage() {
  cat <<EOF
Usage: $0 --version X.Y.Z [options]

Required:
  --version X.Y.Z         Release version

Optional:
  --next X.Y.Z-SNAPSHOT   Next dev version (default: patch+1-SNAPSHOT)
  --old X.Y.Z             Previous release (default: latest mockserver-* git tag)
  --type TYPE             full | maven-only | docker-only | post-maven (default: full)
  --versioned-site yes|no Create versioned subdomain (default: no)
  --dry-run               Safe mode: build/lint/check but skip publish (default locally)
  --execute               Actually publish (default in CI; use locally with care)
  --only=A,B,C            Run only these components (comma-separated)
  --skip=X,Y              Skip these components
  --skip-prepare          Skip the prepare step (validation + pom bump + tag)
  --skip-finalize         Skip the finalize step (snapshot bump + version refs)
  -h, --help              Show this help

Components (in order): ${ALL_COMPONENTS[*]}
EOF
}

ONLY=""
SKIP=""
SKIP_PREPARE=false
SKIP_FINALIZE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)         RELEASE_VERSION="$2"; shift 2 ;;
    --next)            NEXT_VERSION="$2"; shift 2 ;;
    --old)             OLD_VERSION="$2"; shift 2 ;;
    --type)            RELEASE_TYPE="$2"; shift 2 ;;
    --versioned-site)  CREATE_VERSIONED_SITE="$2"; shift 2 ;;
    --dry-run)         DRY_RUN=true; shift ;;
    --execute)         DRY_RUN=false; shift ;;
    --only=*)          ONLY="${1#*=}"; shift ;;
    --skip=*)          SKIP="${1#*=}"; shift ;;
    --skip-prepare)    SKIP_PREPARE=true; shift ;;
    --skip-finalize)   SKIP_FINALIZE=true; shift ;;
    -h|--help)         usage; exit 0 ;;
    *)                 log_error "Unknown arg: $1"; usage >&2; exit 2 ;;
  esac
done

export RELEASE_VERSION NEXT_VERSION OLD_VERSION RELEASE_TYPE CREATE_VERSIONED_SITE DRY_RUN

require_release_inputs

log_step "Release $RELEASE_VERSION (dry-run=$DRY_RUN, type=$RELEASE_TYPE)"
log_info "  next     = $NEXT_VERSION"
log_info "  previous = $OLD_VERSION"
log_info "  current  = $CURRENT_VERSION"
log_info "  versioned-site = $CREATE_VERSIONED_SITE"
echo ""

run_step() {
  local label="$1"; shift
  echo ""
  log_step "==> $label"
  "$@"
}

# Compute the component list after filtering.
declare -a TO_RUN
if [[ -n "$ONLY" ]]; then
  IFS=',' read -ra TO_RUN <<< "$ONLY"
else
  TO_RUN=("${ALL_COMPONENTS[@]}")
fi
if [[ -n "$SKIP" ]]; then
  IFS=',' read -ra SKIP_ARR <<< "$SKIP"
  declare -a FILTERED=()
  for c in "${TO_RUN[@]}"; do
    keep=true
    for s in "${SKIP_ARR[@]}"; do
      if [[ "$c" == "$s" ]]; then keep=false; break; fi
    done
    $keep && FILTERED+=("$c")
  done
  TO_RUN=("${FILTERED[@]}")
fi

DRY_ARG=$(is_dry_run && echo "--dry-run" || echo "--execute")

if ! $SKIP_PREPARE; then
  run_step "prepare"  "$SCRIPT_DIR/prepare.sh" "$DRY_ARG"
fi

for c in "${TO_RUN[@]}"; do
  COMP_SCRIPT="$SCRIPT_DIR/components/$c.sh"
  if [[ ! -x "$COMP_SCRIPT" ]]; then
    log_error "Unknown component: $c (no $COMP_SCRIPT)"
    exit 2
  fi
  run_step "$c" "$COMP_SCRIPT" "$DRY_ARG"
done

if ! $SKIP_FINALIZE; then
  run_step "finalize" "$SCRIPT_DIR/finalize.sh" "$DRY_ARG"
fi

echo ""
log_step "Release $RELEASE_VERSION complete (dry-run=$DRY_RUN)"
