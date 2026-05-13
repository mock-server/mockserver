#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

log_step "Release $RELEASE_VERSION notification"

API_VERSION="${RELEASE_VERSION%.*}.x"
VERSIONED_SITE_URL=""
if [[ "$CREATE_VERSIONED_SITE" == "yes" ]]; then
  VERSIONED_SITE_URL="https://$(version_to_subdomain "$RELEASE_VERSION").mock-server.com"
fi

log_info "MockServer $RELEASE_VERSION release flow completed"
log_info "  Type: $RELEASE_TYPE"
log_info ""

case "$RELEASE_TYPE" in
  docker-only)
    log_info "Published artifacts:"
    log_info "  Docker Hub:    https://hub.docker.com/r/mockserver/mockserver/tags?name=$RELEASE_VERSION"
    log_info "  AWS ECR:       https://gallery.ecr.aws/mockserver/mockserver"
    ;;
  maven-only)
    log_info "Published artifacts:"
    log_info "  Maven Central: https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/$RELEASE_VERSION/"
    log_info ""
    log_info "Skipped by selection: Docker, npm, Helm, Javadoc, SwaggerHub, website, JSON Schema, PyPI, RubyGems, GitHub Release"
    ;;
  *)
    log_info "Published artifacts:"
    log_info "  Maven Central: https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/$RELEASE_VERSION/"
    log_info "  Docker Hub:    https://hub.docker.com/r/mockserver/mockserver/tags?name=$RELEASE_VERSION"
    log_info "  AWS ECR:       https://gallery.ecr.aws/mockserver/mockserver"
    log_info "  npm node:      https://www.npmjs.com/package/mockserver-node/v/$RELEASE_VERSION"
    log_info "  npm client:    https://www.npmjs.com/package/mockserver-client/v/$RELEASE_VERSION"
    log_info "  Helm:          https://www.mock-server.com/index.yaml"
    log_info "  OpenAPI:       https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/$API_VERSION"
    log_info "  JSON Schema:   https://www.mock-server.com/schema/expectation.json"
    log_info "  PyPI:          https://pypi.org/project/mockserver-client/$RELEASE_VERSION/"
    log_info "  RubyGems:      https://rubygems.org/gems/mockserver-client/versions/$RELEASE_VERSION"
    log_info "  GitHub:        https://github.com/mock-server/mockserver-monorepo/releases/tag/mockserver-$RELEASE_VERSION"
    log_info "  Website:       https://www.mock-server.com"
    if [[ -n "$VERSIONED_SITE_URL" ]]; then
      log_info "  Versioned Docs: $VERSIONED_SITE_URL"
    fi
    log_info ""
    log_info "Manual follow-up:"
    log_info "  Homebrew:      trigger update-homebrew.sh if a formula bump is needed"
    ;;
esac

if is_ci; then
  case "$RELEASE_TYPE" in
    docker-only)
      buildkite-agent annotate --style success --context release-complete <<EOF
### MockServer $RELEASE_VERSION Docker Images Published

| Artifact | Link |
|----------|------|
| Docker Hub | [mockserver:$RELEASE_VERSION](https://hub.docker.com/r/mockserver/mockserver/tags?name=$RELEASE_VERSION) |
| AWS ECR Public | [mockserver:$RELEASE_VERSION](https://gallery.ecr.aws/mockserver/mockserver) |
EOF
      ;;
    maven-only)
      buildkite-agent annotate --style success --context release-complete <<EOF
### MockServer $RELEASE_VERSION Published to Maven Central

| Artifact | Link |
|----------|------|
| Maven Central | [mockserver-netty $RELEASE_VERSION](https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/$RELEASE_VERSION/) |

Repository housekeeping ran, but downstream distribution steps were skipped because release-type=maven-only.
EOF
      ;;
    *)
      buildkite-agent annotate --style success --context release-complete <<EOF
### MockServer $RELEASE_VERSION Released

| Artifact | Link |
|----------|------|
| Maven Central | [mockserver-netty $RELEASE_VERSION](https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/$RELEASE_VERSION/) |
| Docker Hub | [mockserver:$RELEASE_VERSION](https://hub.docker.com/r/mockserver/mockserver/tags?name=$RELEASE_VERSION) |
| AWS ECR Public | [mockserver:$RELEASE_VERSION](https://gallery.ecr.aws/mockserver/mockserver) |
| npm | [mockserver-node@$RELEASE_VERSION](https://www.npmjs.com/package/mockserver-node/v/$RELEASE_VERSION) |
| npm client | [mockserver-client@$RELEASE_VERSION](https://www.npmjs.com/package/mockserver-client/v/$RELEASE_VERSION) |
| Helm | [index.yaml](https://www.mock-server.com/index.yaml) |
| OpenAPI | [mock-server-openapi $API_VERSION](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/$API_VERSION) |
| JSON Schema | [expectation.json](https://www.mock-server.com/schema/expectation.json) |
| PyPI | [mockserver-client $RELEASE_VERSION](https://pypi.org/project/mockserver-client/$RELEASE_VERSION/) |
| RubyGems | [mockserver-client $RELEASE_VERSION](https://rubygems.org/gems/mockserver-client/versions/$RELEASE_VERSION) |
| GitHub Release | [mockserver-$RELEASE_VERSION](https://github.com/mock-server/mockserver-monorepo/releases/tag/mockserver-$RELEASE_VERSION) |
| Website | [www.mock-server.com](https://www.mock-server.com) |
EOF

      buildkite-agent annotate --style info --context release-follow-up <<EOF
### Remaining Manual Follow-up

- Homebrew formula bump remains manual via scripts/ci/release/update-homebrew.sh
EOF
      ;;
  esac
fi
