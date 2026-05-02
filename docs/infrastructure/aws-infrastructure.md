# AWS Infrastructure

## Overview

MockServer uses two AWS accounts for different purposes:

```mermaid
graph TB
    subgraph "Account 814548061024"
        direction TB
        ASG[AutoScaling Group<br/>Buildkite Agents]
        LAMBDA[Lambda Autoscaler]
        CF_STACK[CloudFormation Stack<br/>'buildkite']
        EC2[EC2 t3.large Instances<br/>0-2 agents]
    end

    subgraph "Account 014848309742"
        direction TB
        S3[S3 Bucket<br/>aws-website-mockserver-nb9hq]
        CDN[CloudFront Distribution<br/>E3R1W2C7JJIMNR]
        R53[Route53<br/>mock-server.com]
    end

    LAMBDA -->|scales| ASG
    ASG -->|manages| EC2
    CF_STACK -->|defines| ASG
    CF_STACK -->|defines| LAMBDA

    R53 -->|A record alias| CDN
    CDN -->|origin| S3
```

## Account Details

| Account ID | Purpose | Region |
|------------|---------|--------|
| `814548061024` | Pipeline build agents and infrastructure | `us-east-1` |
| `014848309742` | Website (S3, CloudFront, DNS, TLS) | `us-east-1` |

## Buildkite Agent Infrastructure

### Architecture

```mermaid
flowchart TD
    BK[Buildkite API] -->|job queue depth| LAMBDA
    LAMBDA[Autoscaling Lambda<br/>Runs every minute] -->|set desired capacity| ASG
    ASG[AutoScaling Group<br/>0-2 instances] -->|launches| EC2[EC2 t3.large]
    EC2 -->|runs| AGENT[Buildkite Agent]
    AGENT -->|polls| BK
    AGENT -->|executes| BUILD[Docker Build Container<br/>mockserver/mockserver:maven]
```

### Resources

| Resource | Identifier | Region |
|----------|-----------|--------|
| AutoScaling Group | `buildkite-AgentAutoScaleGroup-VGG28FR0DE6Q` | `us-east-1` |
| CloudFormation Stack | `buildkite` | `us-east-1` |
| Instance Type | `t3.large` (On-Demand) | `us-east-1` |
| Autoscaling Lambda | `buildkite-Autoscaling-1B7NLL8Z-AutoscalingFunction-iUVGfB0m0ynh` | `us-east-1` |

### Scaling Behaviour

- **Minimum:** 0 instances (scales to zero when idle)
- **Maximum:** 2 instances
- **Agents per instance:** 1
- **Scaling frequency:** Every 60 seconds
- **Scale trigger:** Buildkite job queue depth

### AWS CLI Operations

```bash
# Authenticate via SSO
aws sso login --profile mockserver-build

# Check ASG status
aws autoscaling describe-auto-scaling-groups \
  --auto-scaling-group-names "buildkite-AgentAutoScaleGroup-VGG28FR0DE6Q" \
  --region us-east-1 --profile mockserver-build \
  --query 'AutoScalingGroups[0].{Desired:DesiredCapacity,Instances:Instances[*].{ID:InstanceId,State:LifecycleState}}'

# List running EC2 instances
aws ec2 describe-instances \
  --filters "Name=tag:aws:autoscaling:groupName,Values=buildkite-AgentAutoScaleGroup-VGG28FR0DE6Q" \
  --region us-east-1 --profile mockserver-build \
  --query 'Reservations[].Instances[].{ID:InstanceId,State:State.Name,Launch:LaunchTime}'

# Check instance console output (debugging)
aws ec2 get-console-output --instance-id <instance-id> \
  --region us-east-1 --profile mockserver-build

# Manually scale up agents
aws autoscaling set-desired-capacity \
  --auto-scaling-group-name "buildkite-AgentAutoScaleGroup-VGG28FR0DE6Q" \
  --desired-capacity 2 --region us-east-1 --profile mockserver-build
```

### AWS CLI Prerequisites

1. **Install AWS CLI:** `brew install awscli`
2. **Configure SSO profile:** `aws configure sso --profile mockserver-build` (SSO region: `eu-west-1`, default region: `us-east-1`)
3. **Authenticate:** `aws sso login --profile mockserver-build`
4. **Corporate TLS proxy:** set `AWS_CA_BUNDLE` to your corporate root CA PEM file (e.g., `export AWS_CA_BUNDLE=$NODE_EXTRA_CA_CERTS`)
5. **macOS + Python 3.14 + Homebrew:** if `pyexpat` symbol errors occur, set `export DYLD_LIBRARY_PATH=/opt/homebrew/opt/expat/lib`

## Website Hosting

### Architecture

```mermaid
flowchart LR
    USER[User Browser] -->|HTTPS| CF[CloudFront<br/>E3R1W2C7JJIMNR]
    CF -->|Origin| S3[S3 Bucket<br/>aws-website-mockserver-nb9hq]
    DNS[Route53<br/>www.mock-server.com] -->|A record alias| CF
```

### S3 Bucket Contents

The S3 bucket `aws-website-mockserver-nb9hq` hosts:

| Path | Content |
|------|---------|
| `/` (root) | Jekyll website (`www.mock-server.com`) |
| `/versions/<version>/` | Javadoc for each release |
| `/*.tgz` + `index.yaml` | Helm chart repository |

### CloudFront Distribution

- **Distribution ID:** `E3R1W2C7JJIMNR`
- **Domain:** `www.mock-server.com`
- **Cache invalidation:** Use `/*` pattern after website updates

```bash
# Invalidate CloudFront cache after website deployment
aws cloudfront create-invalidation \
  --distribution-id E3R1W2C7JJIMNR \
  --paths "/*" \
  --profile mockserver-website
```

### DNS

Route53 manages the `mock-server.com` domain:

- `www.mock-server.com` — A record aliased to CloudFront distribution `E3R1W2C7JJIMNR`
- Versioned subdomains (e.g., `5.14.x.mock-server.com`) may have separate CloudFront distributions and S3 buckets

### Website Deployment Process

1. Build Jekyll site: `bundle exec jekyll build`
2. Upload `_site/` contents to S3 bucket root
3. Invalidate CloudFront cache
4. See [Release Process](release-process.md) for full details

## Infrastructure as Code

### Buildkite Agents (Terraform)

The Buildkite agent infrastructure is managed by Terraform in `terraform/buildkite-agents/`, using the official [Buildkite Elastic CI Stack for AWS](https://github.com/buildkite/terraform-buildkite-elastic-ci-stack-for-aws) Terraform module.

| Property | Value |
|----------|-------|
| Region | `eu-west-2` |
| Instance type | `t3.large` (Spot) |
| Scaling | 0–2 instances |
| State backend | S3 + DynamoDB in `eu-west-2` |
| Module | `buildkite/elastic-ci-stack-for-aws/buildkite` v0.7.x |

#### Quick start

```bash
# Bootstrap state backend (first time only)
./terraform/buildkite-agents/run.sh bootstrap

# Preview changes
./terraform/buildkite-agents/run.sh plan

# Apply changes
./terraform/buildkite-agents/run.sh apply
```

The `run.sh` wrapper checks AWS SSO authentication and prompts to log in if needed.

#### Configuration

Copy `terraform.tfvars.example` to `terraform.tfvars` and set your Buildkite agent token. See `variables.tf` for all available options.

### Legacy Infrastructure

- **CloudFormation:** Stack `buildkite` in `us-east-1`, account `814548061024` (template not stored in this repo) — being replaced by Terraform
- **Website:** S3 buckets, CloudFront distributions, Route53 records in account `014848309742` — manually provisioned
