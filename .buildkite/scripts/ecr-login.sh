#!/usr/bin/env bash
set -euo pipefail

echo "--- :aws: Logging in to ECR Public"
aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws
