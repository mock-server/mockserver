# Bootstrap — Terraform State Backend

One-time setup for the S3 bucket that stores Terraform remote state for all modules.

## What It Creates

```mermaid
flowchart LR
    subgraph "AWS eu-west-2"
        S3["S3 Bucket<br/>Terraform State<br/>(versioned, encrypted, private)"]
    end

    subgraph "Terraform Modules"
        BA["buildkite-agents/"]
        FUTURE["future modules..."]
    end

    BA -->|state| S3
    FUTURE -.->|state| S3
```

| Resource | Purpose |
|----------|---------|
| S3 Bucket (see `~/mockserver-aws-ids.md`) | Stores `.tfstate` files with versioning and AES-256 encryption |
| S3 Public Access Block | *(on above bucket)* | Blocks all public access |

## How It Works

This bootstrap uses **local state** (no remote backend — it *is* the backend). It includes Terraform `import` blocks for every resource, which means:

- **First run:** creates the resources from scratch
- **Re-run against existing resources:** imports them into local state without error
- **Idempotent:** safe to run multiple times

```mermaid
flowchart TD
    A[run.sh bootstrap] --> B[terraform init<br/>local state]
    B --> C{Resources exist<br/>in AWS?}
    C -->|No| D[Create S3 bucket]
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

| Output | Description |
|--------|-------------|
| `state_bucket` | Name of the S3 bucket (see `~/mockserver-aws-ids.md`) |
