#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "--- :aws: Fetching Sonatype Central Portal credentials from Secrets Manager"
SECRET_JSON=$(aws secretsmanager get-secret-value \
  --secret-id "mockserver-build/sonatype" \
  --region eu-west-2 \
  --query SecretString \
  --output text)

SONATYPE_USERNAME=$(echo "$SECRET_JSON" | jq -r '.username')
SONATYPE_PASSWORD=$(echo "$SECRET_JSON" | jq -r '.password')

if [ -z "$SONATYPE_USERNAME" ] || [ "$SONATYPE_USERNAME" = "null" ]; then
  echo "Error: Sonatype credentials not found in AWS Secrets Manager."
  echo "Store a Central Portal user token in mockserver-build/sonatype with keys 'username' and 'password'."
  echo "Generate the token at: https://central.sonatype.com → View Account → Generate User Token"
  exit 1
fi

echo "--- :nexus: Deploying snapshot to Central Portal"
exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i mockserver/mockserver:maven \
  -m 14g \
  -w /build/mockserver \
  -e "SONATYPE_USERNAME=$SONATYPE_USERNAME" \
  -e "SONATYPE_PASSWORD=$SONATYPE_PASSWORD" \
  -- ./mvnw deploy -DskipTests \
    -Djava.security.egd=file:/dev/./urandom \
    --settings .buildkite-settings.xml
