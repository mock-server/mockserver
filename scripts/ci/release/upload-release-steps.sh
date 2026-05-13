#!/usr/bin/env bash
set -euo pipefail

RELEASE_TYPE=$(buildkite-agent meta-data get release-type 2>&1) || {
  echo "ERROR: meta-data key 'release-type' not found. Was the input step completed?" >&2
  exit 1
}
CREATE_VERSIONED_SITE=$(buildkite-agent meta-data get create-versioned-site 2>&1) || {
  echo "ERROR: meta-data key 'create-versioned-site' not found. Was the input step completed?" >&2
  exit 1
}

generate_pipeline() {
  echo "steps:"

  # Preflight — verify every tool every script will need, on each queue.
  # Runs upfront so a missing dependency fails in seconds, not 30 minutes in.
  cat << 'YAML'

  - group: ":mag: Preflight"
    steps:
      - label: ":mag: Preflight (release queue)"
        command: "scripts/ci/release/preflight.sh release"
        timeout_in_minutes: 5
        agents:
          queue: "release"
      - label: ":mag: Preflight (default queue)"
        command: "scripts/ci/release/preflight.sh default"
        timeout_in_minutes: 5

  - wait
YAML

  # Maven Central steps — skipped for docker-only and post-maven reruns
  if [[ "$RELEASE_TYPE" != "docker-only" && "$RELEASE_TYPE" != "post-maven" ]]; then
    cat << 'YAML'

  - label: ":white_check_mark: Validate"
    command: "scripts/ci/release/validate.sh"
    agents:
      queue: "release"

  - wait

  - label: ":git: Set Release Version"
    command: "scripts/ci/release/set-release-version.sh"
    agents:
      queue: "release"

  - wait

  - label: ":maven: Build & Test"
    command: "scripts/ci/release/build-and-test.sh"
    timeout_in_minutes: 60
    artifact_paths:
      - "mockserver/mockserver-netty/target/mockserver-netty-*-shaded.jar"
    agents:
      queue: "default"

  - wait

  - block: ":eyes: Review Build Results"
    prompt: "Build and tests passed. Approve to deploy to Central Portal."
    allowed_teams: ["release-managers"]

  - label: ":lock: Deploy Release to Central Portal"
    command: "scripts/ci/release/deploy-release.sh"
    timeout_in_minutes: 30
    agents:
      queue: "release"

  - wait

  - label: ":hourglass: Poll Central Portal Validation"
    command: "scripts/ci/release/poll-central-portal.sh"
    timeout_in_minutes: 30
    agents:
      queue: "release"

  - wait

  - block: ":rocket: Approve Maven Central Publication"
    prompt: |
      Artifacts validated on Central Portal.
      Review at https://central.sonatype.com/publishing/deployments
      Approve to publish to Maven Central.
    allowed_teams: ["release-managers"]

  - label: ":java: Publish on Central Portal"
    command: "scripts/ci/release/publish-central-portal.sh"
    timeout_in_minutes: 30
    agents:
      queue: "release"

  - wait

  - label: ":hourglass: Wait for Maven Central Sync"
    command: "scripts/ci/release/wait-for-central.sh"
    timeout_in_minutes: 120
    agents:
      queue: "release"

  - wait

  - label: ":arrows_counterclockwise: Deploy Next SNAPSHOT"
    command: "scripts/ci/release/deploy-snapshot.sh"
    timeout_in_minutes: 30
    agents:
      queue: "release"

  - label: ":pencil: Update Versions"
    command: "scripts/ci/release/update-versions.sh"
    timeout_in_minutes: 15
    agents:
      queue: "release"

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

  # Publish group — contents depend on release type; steps within the group run in parallel
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
        agents:
          queue: "default"
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
        agents:
          queue: "default"

      - label: ":gem: RubyGems"
        command: "scripts/ci/release/publish-rubygems.sh"
        timeout_in_minutes: 10
        agents:
          queue: "default"

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
    agents:
      queue: "default"
YAML
}

generate_pipeline | buildkite-agent pipeline upload
