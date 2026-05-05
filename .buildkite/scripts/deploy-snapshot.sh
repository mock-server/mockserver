#!/usr/bin/env bash
set -euo pipefail

echo "--- :aws: Fetching Sonatype credentials from Secrets Manager"
SECRET_JSON=$(aws secretsmanager get-secret-value \
  --secret-id "mockserver-build/sonatype" \
  --region eu-west-2 \
  --query SecretString \
  --output text)

SONATYPE_USERNAME=$(echo "$SECRET_JSON" | jq -r '.username')
SONATYPE_PASSWORD=$(echo "$SECRET_JSON" | jq -r '.password')

echo "--- :nexus: Deploying snapshot to Sonatype OSSRH"
docker run --memory=14g --memory-swap=14g \
  -v "$(pwd):/build/mockserver" \
  -w /build/mockserver/mockserver \
  -a stdout -a stderr \
  -e SONATYPE_USERNAME="$SONATYPE_USERNAME" \
  -e SONATYPE_PASSWORD="$SONATYPE_PASSWORD" \
  mockserver/mockserver:maven \
  ./mvnw deploy -DskipTests \
    -Djava.security.egd=file:/dev/./urandom \
    --settings .buildkite-settings.xml
