#!/usr/bin/env bash
set -euo pipefail

RELEASE_TYPE=$(buildkite-agent meta-data get release-type)
CREATE_VERSIONED_SITE=$(buildkite-agent meta-data get create-versioned-site)

generate_pipeline() {
  echo "steps:"

  # Maven Central steps — skipped for docker-only and post-maven reruns
  if [[ "$RELEASE_TYPE" != "docker-only" && "$RELEASE_TYPE" != "post-maven" ]]; then
    cat << 'YAML'

  - label: ":white_check_mark: Validate"
    command: "scripts/ci/release/validate.sh"
    agents:
      queue: "release"

  - label: ":git: Set Release Version"
    command: "scripts/ci/release/set-release-version.sh"

  - label: ":maven: Build & Test"
    command: "scripts/ci/release/build-and-test.sh"
    timeout_in_minutes: 60
    artifact_paths:
      - "mockserver/mockserver-netty/target/mockserver-netty-*-shaded.jar"

  - block: ":eyes: Review Build Results"
    prompt: "Build and tests passed. Approve to deploy to Central Portal."
    allowed_teams: ["release-managers"]

  - label: ":lock: Deploy Release to Central Portal"
    command: "scripts/ci/release/deploy-release.sh"
    timeout_in_minutes: 30
    agents:
      queue: "release"

  - label: ":hourglass: Poll Central Portal Validation"
    command: "scripts/ci/release/poll-central-portal.sh"
    timeout_in_minutes: 30

  - block: ":rocket: Approve Maven Central Publication"
    prompt: |
      Artifacts validated on Central Portal.
      Review at https://central.sonatype.com/publishing/deployments
      Approve to publish to Maven Central.
    allowed_teams: ["release-managers"]

  - label: ":java: Publish on Central Portal"
    command: "scripts/ci/release/publish-central-portal.sh"
    timeout_in_minutes: 30

  - label: ":hourglass: Wait for Maven Central Sync"
    command: "scripts/ci/release/wait-for-central.sh"
    timeout_in_minutes: 120

  - label: ":arrows_counterclockwise: Deploy Next SNAPSHOT"
    command: "scripts/ci/release/deploy-snapshot.sh"
    timeout_in_minutes: 30

  - label: ":pencil: Update Versions"
    command: "scripts/ci/release/update-versions.sh"
    timeout_in_minutes: 15

  - wait
YAML
  fi

  # Versioned site snapshot — full releases only, when explicitly requested
  if [[ "$RELEASE_TYPE" == "full" || "$RELEASE_TYPE" == "post-maven" ]] && [[ "$CREATE_VERSIONED_SITE" == "yes" ]]; then
    cat << 'YAML'

  - label: ":globe_with_meridians: Create Versioned Site"
    command: "scripts/ci/release/create-versioned-site.sh"
    timeout_in_minutes: 15
    agents:
      queue: "release"

  - wait
YAML
  fi

  # Publish group — contents depend on release type
  local publish_steps=""

  if [[ "$RELEASE_TYPE" == "full" || "$RELEASE_TYPE" == "post-maven" ]]; then
    publish_steps+='
      - label: ":java: Release Maven Plugin"
        command: "scripts/ci/release/release-maven-plugin.sh"
        timeout_in_minutes: 60
        agents:
          queue: "release"
'
  fi

  if [[ "$RELEASE_TYPE" != "maven-only" ]]; then
    publish_steps+='
      - label: ":docker: Docker Image"
        command: "scripts/ci/release/publish-docker.sh"
        timeout_in_minutes: 60
'
  fi

  if [[ "$RELEASE_TYPE" == "full" || "$RELEASE_TYPE" == "post-maven" ]]; then
    publish_steps+='
      - label: ":package: npm mockserver-node"
        key: "publish-npm-mockserver-node"
        command: "scripts/ci/release/publish-npm.sh mockserver-node"
        timeout_in_minutes: 20
        agents:
          queue: "release"

      - label: ":package: npm mockserver-client"
        command: "scripts/ci/release/publish-npm.sh mockserver-client-node"
        depends_on: "publish-npm-mockserver-node"
        timeout_in_minutes: 20
        agents:
          queue: "release"

      - label: ":helm: Helm Chart"
        command: "scripts/ci/release/publish-helm.sh"
        timeout_in_minutes: 15
        agents:
          queue: "release"

      - label: ":book: Javadoc"
        command: "scripts/ci/release/publish-javadoc.sh"
        timeout_in_minutes: 15
        agents:
          queue: "release"

      - label: ":swagger: SwaggerHub"
        command: "scripts/ci/release/update-swaggerhub.sh"
        timeout_in_minutes: 10
        agents:
          queue: "release"

      - label: ":globe_with_meridians: Website"
        command: "scripts/ci/release/publish-website.sh"
        timeout_in_minutes: 15
        agents:
          queue: "release"

      - label: ":page_facing_up: JSON Schema"
        command: "scripts/ci/release/publish-schema.sh"
        timeout_in_minutes: 10
        agents:
          queue: "release"

      - label: ":python: PyPI"
        command: "scripts/ci/release/publish-pypi.sh"
        timeout_in_minutes: 10

      - label: ":gem: RubyGems"
        command: "scripts/ci/release/publish-rubygems.sh"
        timeout_in_minutes: 10

      - label: ":github: GitHub Release"
        command: "scripts/ci/release/github-release.sh"
        timeout_in_minutes: 10
        agents:
          queue: "release"
'
  fi

  if [[ -n "$publish_steps" ]]; then
    echo ""
    echo "  - group: \":package: Publish & Update\""
    echo "    steps:$publish_steps"
  fi

  cat << 'YAML'

  - wait

  - label: ":bell: Notify"
    command: "scripts/ci/release/notify.sh"
YAML
}

generate_pipeline | buildkite-agent pipeline upload
