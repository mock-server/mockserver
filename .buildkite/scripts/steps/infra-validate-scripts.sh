#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# koalaman/shellcheck-alpine ships shellcheck + sh. We add bash for `bash -n`
# syntax checking.
#
# Two passes:
#   1. bash -n on every shell script (cheap syntax sanity check, no warnings)
#   2. shellcheck on critical CI scripts (warnings + errors)
#      Legacy scripts/*.sh local-dev scripts use error-only severity to avoid
#      blocking on pre-existing style warnings that aren't load-bearing.
exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i koalaman/shellcheck-alpine:v0.11.0 \
  -- sh -c '
    set -eu
    apk add --no-cache --quiet bash >/dev/null

    all_files=$(find scripts .buildkite/scripts -type f -name "*.sh" 2>/dev/null | sort)
    ci_files=$(find scripts/ci .buildkite/scripts -type f -name "*.sh" 2>/dev/null | sort || true)
    legacy_files=$(find scripts -maxdepth 1 -type f -name "*.sh" 2>/dev/null | sort || true)

    if [ -z "$all_files" ]; then
      echo "No shell scripts found"
      exit 1
    fi

    bash_errors=0
    shellcheck_errors=0

    echo "=== bash -n (syntax check, all scripts) ==="
    for script in $all_files; do
      echo "  $script"
      bash -n "$script" || bash_errors=$((bash_errors + 1))
    done

    echo ""
    echo "=== shellcheck (CI/release scripts, warning+) ==="
    for script in $ci_files; do
      echo "  $script"
      shellcheck -x -S warning -e SC1091 "$script" || shellcheck_errors=$((shellcheck_errors + 1))
    done

    echo ""
    echo "=== shellcheck (legacy dev scripts, error only) ==="
    for script in $legacy_files; do
      echo "  $script"
      shellcheck -x -S error -e SC1091 "$script" || shellcheck_errors=$((shellcheck_errors + 1))
    done

    echo ""
    echo "=== summary ==="
    echo "  bash -n errors:    $bash_errors"
    echo "  shellcheck errors: $shellcheck_errors"
    total=$((bash_errors + shellcheck_errors))
    # Clamp to 1 — exit codes wrap mod 256 in bash, so a large $total could
    # accidentally hit 0 (or a signal code like 130) and report success.
    if [ "$total" -gt 0 ]; then
      exit 1
    fi
    exit 0
  '
