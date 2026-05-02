# Bootstrap — Terraform State Backend

One-time setup for the S3 bucket and DynamoDB table that store Terraform remote state for all modules.

## What It Creates

```mermaid
flowchart LR
    subgraph "AWS eu-west-2 — Account 814548061024"
        S3["S3 Bucket<br/>mockserver-terraform-state<br/>(versioned, encrypted, private)"]
        DDB["DynamoDB Table<br/>mockserver-terraform-locks<br/>(pay-per-request)"]
    end

    subgraph "Terraform Modules"
        BA["buildkite-agents/"]
        FUTURE["future modules..."]
    end

    BA -->|state| S3
    BA -->|lock| DDB
    FUTURE -.->|state| S3
    FUTURE -.->|lock| DDB
```

| Resource | Name | Purpose |
|----------|------|---------|
| S3 Bucket | `mockserver-terraform-state` | Stores `.tfstate` files with versioning and AES-256 encryption |
| S3 Public Access Block | *(on above bucket)* | Blocks all public access |
| DynamoDB Table | `mockserver-terraform-locks` | Prevents concurrent `terraform apply` runs |

## How It Works

This bootstrap uses **local state** (no remote backend — it *is* the backend). It includes Terraform `import` blocks for every resource, which means:

- **First run:** creates the resources from scratch
- **Re-run against existing resources:** imports them into local state without error
- **Idempotent:** safe to run multiple times

```mermaid
flowchart TD
    A[run.sh bootstrap] --> B[terraform init<br/>local state]
    B --> C{Resources exist<br/>in AWS?}
    C -->|No| D[Create S3 bucket<br/>+ DynamoDB table]
    C -->|Yes| E[Import existing<br/>resources into state]
    D --> F[Done]
    E --> F
```

## Usage

From the parent directory:

```bash
./run.sh bootstrap
```

Or directly:

```bash
cd bootstrap
terraform init
terraform apply -auto-approve
```

## State File

The bootstrap's own state is stored locally in `bootstrap/terraform.tfstate`. This file is gitignored. Losing it is harmless — re-running bootstrap will re-import the existing resources.

## Outputs

| Output | Value |
|--------|-------|
| `state_bucket` | `mockserver-terraform-state` |
| `lock_table` | `mockserver-terraform-locks` |
