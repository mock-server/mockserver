#!/usr/bin/env bash
# Publish mockserver-client-go via git tag.
#
# Go modules are indexed automatically by the Go module proxy when a
# properly-formatted tag is pushed to a public repository. The subdir-module
# tag convention (mockserver-client-go/vX.Y.Z) is required for pkg.go.dev to
# resolve the module inside a monorepo, and the module path must end in /vN
# (N = the release major), which require_go_module_major enforces.
#
# NOTE: full pkg.go.dev resolution may require splitting to a dedicated repo
# later — keep this best-effort/soft.
#
# Dry-run: validate the module builds, skip tag + push.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/_lib.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --execute) DRY_RUN=false; shift ;;
    -h|--help) echo "Usage: $0 [--dry-run|--execute]"; exit 0 ;;
    *) log_error "Unknown arg: $1"; exit 2 ;;
  esac
done

require_release_inputs
skip_unless_release_type "client-go" full,post-maven

log_step "Publish Go client $RELEASE_VERSION (dry-run=$DRY_RUN)"
# The tag must land on a commit that has prepare.sh's /vN suffix and VERSION bump,
# not on the commit this build was started from.
sync_to_origin_master

TAG="mockserver-client-go/v${RELEASE_VERSION}"

# Validate the module in a pinned golang container — no host 'go' required.
# HARD: a vet failure aborts the step (it used to silently skip when 'go' was
# absent on the release agent, which is why the Go client never published).
log_info "Validating Go module (go vet) in $GO_IMAGE"
in_docker "$GO_IMAGE" -w /build/mockserver-client-go -- go vet ./...

require_go_module_major

if is_dry_run; then
  log_dry "skip: git tag $TAG + push + proxy indexing"
  exit 0
fi

# Tag + push is the actual publish (Go's proxy indexes public tags). HARD-fail,
# with retry to ride out transient git/network errors.
log_info "Tagging + pushing $TAG"
retry 3 5 -- git_tag_and_push "$TAG"

# Nudge the Go module proxy to index. pkg.go.dev indexing is eventually-consistent
# and genuinely lags, so this is best-effort: retry, then tolerate (the tag — the
# real publish — is already pushed). Surfaced with a warning, never silently
# swallowed.
log_info "Triggering Go module proxy indexing in $GO_IMAGE"
if ! retry 3 5 -- in_docker "$GO_IMAGE" \
     -e GOPROXY=https://proxy.golang.org -e GO111MODULE=on \
     -w /build/mockserver-client-go -- \
     go list -m "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v${RELEASE_VERSION%%.*}@v${RELEASE_VERSION}"; then
  log_info ":warning: proxy indexing nudge failed after retries — non-fatal (tag pushed; pkg.go.dev indexes on first fetch)"
fi

log_info "Go client publish complete"
