# Terraform Infrastructure

This directory contains all Terraform-managed infrastructure for MockServer.

```mermaid
graph TB
    subgraph "terraform/"
        direction TB
        BA["buildkite-agents/"]
        BP["buildkite-pipelines/"]
    end

    BA -->|provisions| AWS["AWS eu-west-2
Build Agent Account"]
    BP -->|manages| BK["Buildkite
Pipeline Definitions"]

    subgraph AWS
        direction TB
        VPC[VPC + Subnets]
        ASG["AutoScaling Group
Spot instances, 0-10"]
        SCALER[Lambda Autoscaler]
        AGENT[Buildkite Agents]
    end

    SCALER -->|scales| ASG
    ASG -->|launches| AGENT
    AGENT -->|runs in| VPC
```

## Modules

| Directory | Purpose | Provider |
|-----------|---------|----------|
| [`buildkite-agents/`](buildkite-agents/) | Buildkite CI build agent cluster | AWS (`eu-west-2`) |
| [`buildkite-pipelines/`](buildkite-pipelines/) | Buildkite pipeline definitions | Buildkite + AWS |

## Prerequisites

- [Terraform](https://www.terraform.io/downloads) >= 1.5
- [AWS CLI](https://aws.amazon.com/cli/) with SSO profile `mockserver-build`
- AWS build agent account (see `~/mockserver-aws-ids.md`)

## Quick Start

The `buildkite-agents` module has a `run.sh` wrapper that handles AWS authentication and runs Terraform. See the individual module READMEs for details.

## State Management

All modules store state remotely in S3 with native file locking:

| Resource | Region |
|----------|--------|
| S3 Bucket (see `~/mockserver-aws-ids.md`) | `eu-west-2` |

The state backend is bootstrapped via `buildkite-agents/bootstrap/`. See the [bootstrap README](buildkite-agents/bootstrap/) for details.
