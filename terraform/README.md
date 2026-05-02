# Terraform Infrastructure

This directory contains all Terraform-managed infrastructure for MockServer.

```mermaid
graph TB
    subgraph "terraform/"
        direction TB
        BA["buildkite-agents/"]
    end

    BA --> |provisions| AWS["AWS eu-west-2<br/>Account 814548061024"]

    subgraph AWS
        direction TB
        VPC[VPC + Subnets]
        ASG[AutoScaling Group<br/>Spot t3.large, 0–2 instances]
        SCALER[Lambda Autoscaler]
        AGENT[Buildkite Agents]
    end

    SCALER -->|scales| ASG
    ASG -->|launches| AGENT
    AGENT -->|runs in| VPC
```

## Modules

| Directory | Purpose | Region |
|-----------|---------|--------|
| [`buildkite-agents/`](buildkite-agents/) | Buildkite CI build agent cluster | `eu-west-2` |

## Prerequisites

- [Terraform](https://www.terraform.io/downloads) >= 1.5
- [AWS CLI](https://aws.amazon.com/cli/) with SSO profile `mockserver-build`
- AWS account `814548061024`

## Quick Start

Each module has a `run.sh` wrapper that handles AWS authentication and runs Terraform. See the individual module READMEs for details.

## State Management

All modules store state remotely in S3 with DynamoDB locking:

| Resource | Name | Region |
|----------|------|--------|
| S3 Bucket | `mockserver-terraform-state` | `eu-west-2` |
| DynamoDB Table | `mockserver-terraform-locks` | `eu-west-2` |

The state backend is bootstrapped via `buildkite-agents/bootstrap/`. See the [bootstrap README](buildkite-agents/bootstrap/) for details.
