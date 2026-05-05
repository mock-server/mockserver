#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "--- :aws: Fetching Sonatype credentials from Secrets Manager"
SECRET_JSON=$(aws secretsmanager get-secret-value \
  --secret-id "mockserver-build/sonatype" \
  --region eu-west-2 \
  --query SecretString \
  --output text)

SONATYPE_USERNAME=$(echo "$SECRET_JSON" | jq -r '.username')
SONATYPE_PASSWORD=$(echo "$SECRET_JSON" | jq -r '.password')

echo "--- :nexus: Deploying snapshot to Sonatype OSSRH"
exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i mockserver/mockserver:maven \
  -m 14g \
  -w /build/mockserver \
  -e "SONATYPE_USERNAME=$SONATYPE_USERNAME" \
  -e "SONATYPE_PASSWORD=$SONATYPE_PASSWORD" \
  -- ./mvnw deploy -DskipTests \
    -Djava.security.egd=file:/dev/./urandom \
    --settings .buildkite-settings.xml
