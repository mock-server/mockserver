# Buildkite Agents

Terraform configuration for MockServer's Buildkite CI build agent infrastructure, using the official [Buildkite Elastic CI Stack for AWS](https://github.com/buildkite/terraform-buildkite-elastic-ci-stack-for-aws) module.

## Architecture

```mermaid
flowchart TB
    subgraph "Buildkite Cloud"
        BK_API[Buildkite API]
        BK_QUEUE[Job Queue<br/>'default']
    end

    subgraph "AWS eu-west-2 — Account 814548061024"
        subgraph "VPC (auto-created)"
            subgraph "AutoScaling Group"
                EC2_1[EC2 Spot t3.large<br/>Buildkite Agent]
                EC2_2[EC2 Spot t3.large<br/>Buildkite Agent]
            end
        end
        SCALER[Lambda Autoscaler<br/>Runs every minute]
        SSM[SSM Parameter Store<br/>Agent Token]
        S3_SECRETS[S3 Secrets Bucket]
    end

    BK_API -->|queue depth| SCALER
    SCALER -->|set desired 0–2| EC2_1 & EC2_2
    EC2_1 & EC2_2 -->|poll for jobs| BK_QUEUE
    EC2_1 & EC2_2 -->|read token| SSM
    EC2_1 & EC2_2 -->|read secrets| S3_SECRETS
```

## How It Works

```mermaid
sequenceDiagram
    participant BK as Buildkite
    participant Lambda as Autoscaler Lambda
    participant ASG as AutoScaling Group
    participant Agent as EC2 Agent
    participant Docker as Docker Container

    loop Every 60 seconds
        Lambda->>BK: Check job queue depth
        Lambda->>ASG: Set desired capacity (0–2)
    end

    BK->>Agent: Job available
    Agent->>Docker: docker pull mockserver/mockserver:maven
    Agent->>Docker: docker run (mount repo, run build)
    Docker->>Docker: mvnw clean install
    Docker-->>BK: Upload artifacts (*.log)

    Note over Agent,ASG: When idle, agents self-terminate<br/>ASG scales back to 0
```

## Directory Structure

```
buildkite-agents/
├── bootstrap/           # One-time state backend setup
│   ├── main.tf          #   S3 bucket + DynamoDB table
│   └── README.md        #   Bootstrap instructions
├── main.tf              # Elastic CI Stack module
├── backend.tf           # S3 remote state configuration
├── variables.tf         # Input variables
├── outputs.tf           # Outputs (ASG name, VPC ID)
├── versions.tf          # Terraform + provider versions
├── terraform.tfvars.example  # Example variable values
├── run.sh               # Wrapper script (auth + plan/apply)
└── README.md            # This file
```

## Prerequisites

1. **Terraform** >= 1.5 — `brew install terraform`
2. **AWS CLI** — `brew install awscli`
3. **AWS SSO profile** `mockserver-build` configured:
   ```bash
   aws configure sso --profile mockserver-build
    # SSO region: eu-west-2
   # Default region: eu-west-2
   ```
4. **Buildkite agent token** — from https://buildkite.com/organizations/mockserver/agents

## Getting Started

### 1. Bootstrap the State Backend (first time only)

```bash
./run.sh bootstrap
```

This creates the S3 bucket and DynamoDB table used for remote state. Uses `import` blocks so it's safe to re-run against existing resources. See [bootstrap/README.md](bootstrap/) for details.

### 2. Configure Variables

```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` and set your Buildkite agent token:

```hcl
buildkite_agent_token = "your-token-here"
```

> **terraform.tfvars is gitignored** — it contains secrets and must never be committed.

### 3. Preview Changes

```bash
./run.sh plan
```

### 4. Apply

```bash
./run.sh apply
```

## run.sh Reference

The `run.sh` wrapper handles AWS SSO authentication, environment workarounds (corporate TLS proxy, macOS pyexpat), and runs Terraform commands.

```
Usage: run.sh [command]

Commands:
  plan       Run terraform plan (default)
  apply      Run terraform apply
  destroy    Run terraform destroy
  bootstrap  Initialise the S3 state bucket and DynamoDB lock table
  init       Run terraform init
```

```mermaid
flowchart LR
    A[run.sh] --> B{AWS SSO<br/>authenticated?}
    B -->|Yes| D[terraform init]
    B -->|No| C[Prompt: aws sso login]
    C --> B
    D --> E{Command}
    E -->|plan| F[terraform plan]
    E -->|apply| G[terraform apply]
    E -->|destroy| H[terraform destroy]
    E -->|bootstrap| I[bootstrap/terraform apply]
```

## Variables

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `buildkite_agent_token` | `string` | *(required)* | Buildkite agent registration token |
| `region` | `string` | `eu-west-2` | AWS region |
| `instance_types` | `string` | `t3.large` | EC2 instance types (comma-separated) |
| `min_size` | `number` | `0` | Minimum instances (0 = scale to zero) |
| `max_size` | `number` | `2` | Maximum instances |
| `on_demand_percentage` | `number` | `0` | % on-demand vs spot (0 = all spot) |

## Outputs

| Output | Description |
|--------|-------------|
| `auto_scaling_group_name` | Name of the agent AutoScaling Group |
| `vpc_id` | VPC ID where agents run |

## Cost

With `min_size = 0` and `on_demand_percentage = 0` (100% spot):
- **Idle cost:** $0 (scales to zero when no builds queued)
- **Build cost:** ~$0.02/hr per agent (spot t3.large)
- Agents take 2–3 minutes to launch from cold start
