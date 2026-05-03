#!/usr/bin/env bash
set -euo pipefail

SECRET_ID="mockserver-build/dockerhub"
REGION="eu-west-2"

echo "--- :aws: Fetching Docker Hub credentials from Secrets Manager"
SECRET_JSON=$(aws secretsmanager get-secret-value \
  --secret-id "$SECRET_ID" \
  --region "$REGION" \
  --query SecretString \
  --output text)

DOCKER_USERNAME=$(echo "$SECRET_JSON" | jq -r '.username')
DOCKER_TOKEN=$(echo "$SECRET_JSON" | jq -r '.token')

echo "--- :docker: Logging in to Docker Hub"
echo "$DOCKER_TOKEN" | docker login --username "$DOCKER_USERNAME" --password-stdin
