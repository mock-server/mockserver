#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

log_step "Deploying next SNAPSHOT version $NEXT_VERSION"

cd "$REPO_ROOT/mockserver"

log_info "Setting Maven version to $NEXT_VERSION"
./mvnw versions:set -DnewVersion="$NEXT_VERSION" -DgenerateBackupPoms=false
./mvnw versions:commit

log_info "Deploying SNAPSHOT"
if is_ci; then
  SECRET_JSON=$(aws secretsmanager get-secret-value \
    --secret-id "mockserver-build/sonatype" \
    --region "$REGION" \
    --query SecretString --output text)
  SONATYPE_USERNAME=$(echo "$SECRET_JSON" | jq -r '.username')
  SONATYPE_PASSWORD=$(echo "$SECRET_JSON" | jq -r '.password')

  SONATYPE_USERNAME="$SONATYPE_USERNAME" \
  SONATYPE_PASSWORD="$SONATYPE_PASSWORD" \
  ./mvnw -T 1C clean deploy -DskipTests \
    -Djava.security.egd=file:/dev/./urandom \
    --settings .buildkite-settings.xml
else
  ./mvnw -T 1C clean deploy -DskipTests \
    -Djava.security.egd=file:/dev/./urandom \
    --settings .buildkite-settings.xml
fi

log_info "Committing SNAPSHOT version"
cd "$REPO_ROOT"
git add -A
git commit -m "release: set next development version $NEXT_VERSION"
git push origin master

log_info "SNAPSHOT $NEXT_VERSION deployed and committed"
