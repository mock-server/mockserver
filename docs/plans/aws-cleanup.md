# AWS Cleanup Plan

## Remaining: GitHub Actions OIDC

`terraform/buildkite-agents/build-secrets.tf` defines OIDC provider, GitHub Actions IAM role, and Secrets Manager secret for Docker Hub credentials. These have NOT been applied yet.

```bash
cd terraform/buildkite-agents
terraform plan   # Review changes
terraform apply  # Creates OIDC provider + IAM role + Secrets Manager secret
```

This enables GitHub Actions to push Docker images to Docker Hub via OIDC federation without storing long-lived credentials in GitHub Secrets.
