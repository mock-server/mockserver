---
name: dockerhub-credentials
description: >
  Manages Docker Hub credentials for the MockServer project. Guides the user through
  creating a Docker Hub Personal Access Token, validates it, and stores it in AWS
  Secrets Manager for use by CI pipelines. Use when the user says "docker hub credentials",
  "docker hub token", "set up docker push", "configure docker hub", "rotate docker token",
  or needs to store Docker Hub credentials in AWS Secrets Manager.
---

# Store Docker Hub Credentials in AWS Secrets Manager

This skill guides you through creating (or rotating) Docker Hub credentials and storing
them in AWS Secrets Manager (`mockserver-build/dockerhub`) for use by Buildkite pipelines.

## Prerequisites

- AWS CLI installed and configured with the `mockserver-build` SSO profile
- Authenticated to AWS: `aws sso login --profile mockserver-build`
- Docker Hub account with push access to the `mockserver/mockserver` repository
- Terraform resources deployed (the `aws_secretsmanager_secret.dockerhub` resource
  in `terraform/buildkite-agents/build-secrets.tf` must exist)

## Step 1: Try Local Docker Credentials (Optional Shortcut)

Before asking the user to create a new token, check if Docker Hub credentials are
already available locally:

```bash
# Check what credential store is configured
cat ~/.docker/config.json | python3 -c "import sys,json; print(json.load(sys.stdin).get('credsStore','none'))"

# Try to extract credentials (the helper name comes from credsStore above)
echo "https://index.docker.io/v1/" | docker-credential-desktop get 2>/dev/null
```

If credentials are found, ask the user if they want to use them. Note that credentials
from Docker Desktop may be session tokens rather than reusable PATs — prefer creating
a dedicated PAT in Step 2 for production use.

## Step 2: Create a Docker Hub Personal Access Token

If no local credentials are available (or the user wants a fresh token):

1. Instruct the user to navigate to Docker Hub:
   - URL: `https://app.docker.com/settings/personal-access-tokens`
   - Or: Docker Hub → Avatar → Account Settings → Personal Access Tokens
2. Click **Generate New Token**
3. Settings:
   - **Description**: `mockserver-ci` (or similar)
   - **Permissions**: **Read & Write** (needed to push images)
   - **Expiration**: Choose based on rotation policy (recommended: 1 year)
4. Copy the generated token (format: `dckr_pat_...`)

Ask the user to provide:
- **Docker Hub username**
- **Personal Access Token**

## Step 3: Validate Credentials

Test the credentials before storing them:

```bash
echo "<token>" | docker login --username "<username>" --password-stdin 2>&1
```

If login succeeds, proceed. If it fails, ask the user to verify the token.

After validation, log out to avoid leaving credentials in the local Docker config:

```bash
docker logout
```

## Step 4: Store in AWS Secrets Manager

```bash
aws secretsmanager put-secret-value \
    --secret-id mockserver-build/dockerhub \
    --secret-string '{"username":"<username>","token":"<token>"}' \
    --profile mockserver-build \
    --region eu-west-2
```

**IMPORTANT**: Never log or echo the token value. Use the AWS CLI directly.

## Step 5: Verify Storage

Confirm the secret was stored correctly:

```bash
aws secretsmanager get-secret-value \
    --secret-id mockserver-build/dockerhub \
    --profile mockserver-build \
    --region eu-west-2 \
    --query 'SecretString' \
    --output text | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'Username: {d[\"username\"]}, Token: {d[\"token\"][:10]}...')"
```

This should print the username and first 10 characters of the token.

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `aws secretsmanager put-secret-value` fails with "not found" | Terraform not applied | Run `terraform apply` in `terraform/buildkite-agents/` |
| `docker login` fails with 401 | Token expired or invalid | Create a new PAT in Docker Hub |
| AWS CLI fails with expired SSO token | SSO session expired | Run `aws sso login --profile mockserver-build` |
| `ResourceNotFoundException` | Secret doesn't exist yet | Ensure `terraform apply` has been run for `build-secrets.tf` |

## Security Notes

- **Never** commit Docker Hub tokens to the repository
- **Never** log the full token value in CI output
- The secret in AWS Secrets Manager is encrypted at rest using the default KMS key
- Access is scoped: only the Buildkite agent EC2 instance role can read the secret (via `buildkite-read-dockerhub-secret` IAM policy)
- Rotate the token if it may have been exposed
