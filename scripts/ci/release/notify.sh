#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

log_step "Release $RELEASE_VERSION notification"

log_info "MockServer $RELEASE_VERSION has been released"
log_info ""
log_info "Published artifacts:"
log_info "  Maven Central: https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/$RELEASE_VERSION/"
log_info "  Docker Hub:    https://hub.docker.com/r/mockserver/mockserver/tags?name=$RELEASE_VERSION"
log_info "  npm:           https://www.npmjs.com/package/mockserver-node/v/$RELEASE_VERSION"
log_info "  PyPI:          https://pypi.org/project/mockserver-client/$RELEASE_VERSION/"
log_info "  RubyGems:      https://rubygems.org/gems/mockserver-client/versions/$RELEASE_VERSION"
log_info "  GitHub:        https://github.com/mock-server/mockserver-monorepo/releases/tag/mockserver-$RELEASE_VERSION"
log_info "  Website:       https://www.mock-server.com"

if is_ci; then
  buildkite-agent annotate --style success --context release-complete <<EOF
### MockServer $RELEASE_VERSION Released

| Artifact | Link |
|----------|------|
| Maven Central | [mockserver-netty $RELEASE_VERSION](https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/$RELEASE_VERSION/) |
| Docker Hub | [mockserver:$RELEASE_VERSION](https://hub.docker.com/r/mockserver/mockserver/tags?name=$RELEASE_VERSION) |
| npm | [mockserver-node@$RELEASE_VERSION](https://www.npmjs.com/package/mockserver-node/v/$RELEASE_VERSION) |
| PyPI | [mockserver-client $RELEASE_VERSION](https://pypi.org/project/mockserver-client/$RELEASE_VERSION/) |
| RubyGems | [mockserver-client $RELEASE_VERSION](https://rubygems.org/gems/mockserver-client/versions/$RELEASE_VERSION) |
| GitHub Release | [mockserver-$RELEASE_VERSION](https://github.com/mock-server/mockserver-monorepo/releases/tag/mockserver-$RELEASE_VERSION) |
EOF
fi
