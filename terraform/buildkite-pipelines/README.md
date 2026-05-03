# Buildkite Pipelines

Terraform-managed Buildkite pipeline definitions for the MockServer project.

## Overview

This stack manages Buildkite pipelines using the [Buildkite Terraform provider](https://registry.terraform.io/providers/buildkite/buildkite/latest). Adding a new pipeline is done by adding an entry to the `pipelines` local in `pipelines.tf`.

## Pipelines

| Pipeline | File | Trigger | Purpose |
|----------|------|---------|---------|
| MockServer | `.buildkite/pipeline.yml` | Push to branches, PRs | CI build and test |
| Docker Push Maven | `.buildkite/docker-push-maven.yml` | Manual | Build and push `mockserver/mockserver:maven` CI image |
| Docker Push Release | `.buildkite/docker-push-release.yml` | Manual | Build and push multi-arch release image |

## Prerequisites

- AWS CLI with SSO profile `mockserver-build` authenticated
- Buildkite API token stored in Secrets Manager (`mockserver-build/buildkite-api-token`) — the secret must have a value before running `terraform plan`
- The token requires scopes: `write_pipelines`, `read_pipelines`, `graphql` (org: `mockserver`)
- On macOS with Python 3.14+, set `export DYLD_LIBRARY_PATH="/opt/homebrew/opt/expat/lib"` before running Terraform

## Usage

```bash
aws sso login --profile mockserver-build
cd terraform/buildkite-pipelines
terraform init
terraform plan
terraform apply
```

## Adding a Pipeline

1. Create the pipeline YAML in `.buildkite/`
2. Add an entry to `local.pipelines` in `pipelines.tf`
3. Run `terraform apply`

## State

Remote state is stored in S3 (`buildkite-pipelines/terraform.tfstate`) in the same bucket as the `buildkite-agents` stack.
