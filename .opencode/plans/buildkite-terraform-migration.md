# Buildkite Agent Terraform Migration Plan

## Overview
Migrate Buildkite build agents from CloudFormation in us-east-1 to Terraform in eu-west-2, using the official Buildkite Elastic CI Stack Terraform module with spot instances.

## Directory Structure
```
terraform/buildkite-agents/
├── bootstrap/
│   └── main.tf          # S3 + DynamoDB state backend (local state, import blocks)
├── main.tf              # Elastic CI Stack module
├── backend.tf           # S3 remote state config
├── variables.tf         # Input variables
├── outputs.tf           # Outputs
├── versions.tf          # Terraform + provider versions
├── terraform.tfvars.example  # Example values (no secrets)
└── run.sh               # Wrapper script (auth check, plan/apply/bootstrap)
```

## Files

### terraform/buildkite-agents/bootstrap/main.tf
```hcl
terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region  = "eu-west-2"
  profile = "mockserver-build"
}

locals {
  project = "mockserver"
}

resource "aws_s3_bucket" "terraform_state" {
  bucket = "${local.project}-terraform-state"
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket                  = aws_s3_bucket.terraform_state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "terraform_locks" {
  name         = "${local.project}-terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}

import {
  to = aws_s3_bucket.terraform_state
  id = "${local.project}-terraform-state"
}

import {
  to = aws_s3_bucket_versioning.terraform_state
  id = "${local.project}-terraform-state"
}

import {
  to = aws_s3_bucket_server_side_encryption_configuration.terraform_state
  id = "${local.project}-terraform-state"
}

import {
  to = aws_s3_bucket_public_access_block.terraform_state
  id = "${local.project}-terraform-state"
}

import {
  to = aws_dynamodb_table.terraform_locks
  id = "${local.project}-terraform-locks"
}

output "state_bucket" {
  value = aws_s3_bucket.terraform_state.bucket
}

output "lock_table" {
  value = aws_dynamodb_table.terraform_locks.name
}
```

### terraform/buildkite-agents/versions.tf
```hcl
terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
```

### terraform/buildkite-agents/backend.tf
```hcl
terraform {
  backend "s3" {
    bucket         = "mockserver-terraform-state"
    key            = "buildkite-agents/terraform.tfstate"
    region         = "eu-west-2"
    dynamodb_table = "mockserver-terraform-locks"
    encrypt        = true
    profile        = "mockserver-build"
  }
}
```

### terraform/buildkite-agents/variables.tf
```hcl
variable "buildkite_agent_token" {
  description = "Buildkite agent registration token"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "eu-west-2"
}

variable "instance_type" {
  description = "EC2 instance type for build agents"
  type        = string
  default     = "t3.large"
}

variable "min_size" {
  description = "Minimum number of agent instances (0 = scale to zero when idle)"
  type        = number
  default     = 0
}

variable "max_size" {
  description = "Maximum number of agent instances"
  type        = number
  default     = 2
}

variable "spot_price" {
  description = "Maximum spot price (empty string = on-demand)"
  type        = string
  default     = "0.05"
}
```

### terraform/buildkite-agents/main.tf
```hcl
provider "aws" {
  region  = var.region
  profile = "mockserver-build"
}

module "buildkite_stack" {
  source  = "buildkite/elastic-ci-stack-for-aws/buildkite"
  version = "~> 0.1.0"

  stack_name            = "buildkite-mockserver"
  buildkite_agent_token = var.buildkite_agent_token
  buildkite_queue       = "default"

  instance_types = [var.instance_type]
  min_size       = var.min_size
  max_size       = var.max_size
  spot_price     = var.spot_price

  agents_per_instance = 1
}
```

### terraform/buildkite-agents/outputs.tf
```hcl
output "autoscaling_group_name" {
  description = "Name of the agent ASG"
  value       = module.buildkite_stack.autoscaling_group_name
}

output "vpc_id" {
  description = "VPC ID where agents run"
  value       = module.buildkite_stack.vpc_id
}
```

### terraform/buildkite-agents/terraform.tfvars.example
```hcl
# Copy this to terraform.tfvars and fill in values.
# NEVER commit terraform.tfvars — it is gitignored.

# Get your agent token from: https://buildkite.com/organizations/mockserver/agents
# buildkite_agent_token = "your-token-here"

region        = "eu-west-2"
instance_type = "t3.large"
min_size      = 0
max_size      = 2
spot_price    = "0.05"
```

### terraform/buildkite-agents/run.sh
```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AWS_PROFILE="mockserver-build"
AWS_REGION="eu-west-2"

usage() {
  cat <<EOF
Usage: $(basename "$0") [command]

Commands:
  plan       Run terraform plan (default)
  apply      Run terraform apply
  destroy    Run terraform destroy
  bootstrap  Initialise the S3 state bucket and DynamoDB lock table
  init       Run terraform init

AWS profile: $AWS_PROFILE
Region:      $AWS_REGION
EOF
}

check_aws_auth() {
  echo "Checking AWS authentication (profile: $AWS_PROFILE)..."
  if ! aws sts get-caller-identity --profile "$AWS_PROFILE" --region "$AWS_REGION" > /dev/null 2>&1; then
    echo ""
    echo "Not authenticated. Run the following command to log in:"
    echo ""
    echo "  aws sso login --profile $AWS_PROFILE"
    echo ""
    read -rp "Would you like to run it now? [Y/n] " answer
    answer="${answer:-Y}"
    if [[ "$answer" =~ ^[Yy] ]]; then
      aws sso login --profile "$AWS_PROFILE"
      if ! aws sts get-caller-identity --profile "$AWS_PROFILE" --region "$AWS_REGION" > /dev/null 2>&1; then
        echo "Authentication failed. Exiting."
        exit 1
      fi
    else
      echo "Exiting."
      exit 1
    fi
  fi
  echo "Authenticated."
  echo ""
}

run_bootstrap() {
  echo "Bootstrapping state backend..."
  cd "$SCRIPT_DIR/bootstrap"
  terraform init
  terraform apply -auto-approve
  echo ""
  echo "Bootstrap complete. State bucket and lock table are ready."
}

run_terraform() {
  local cmd="${1:-plan}"
  cd "$SCRIPT_DIR"
  terraform init -input=false

  case "$cmd" in
    plan)
      terraform plan
      ;;
    apply)
      terraform apply
      ;;
    destroy)
      terraform destroy
      ;;
    *)
      echo "Unknown command: $cmd"
      usage
      exit 1
      ;;
  esac
}

# Ensure AWS CA bundle is set for corporate proxy
if [[ -n "${NODE_EXTRA_CA_CERTS:-}" && -z "${AWS_CA_BUNDLE:-}" ]]; then
  export AWS_CA_BUNDLE="$NODE_EXTRA_CA_CERTS"
fi

# Fix macOS Python 3.14 + Homebrew pyexpat issue
if [[ "$(uname)" == "Darwin" && -d "/opt/homebrew/opt/expat/lib" ]]; then
  export DYLD_LIBRARY_PATH="/opt/homebrew/opt/expat/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
fi

COMMAND="${1:-plan}"

if [[ "$COMMAND" == "-h" || "$COMMAND" == "--help" ]]; then
  usage
  exit 0
fi

check_aws_auth

case "$COMMAND" in
  bootstrap)
    run_bootstrap
    ;;
  init|plan|apply|destroy)
    run_terraform "$COMMAND"
    ;;
  *)
    echo "Unknown command: $COMMAND"
    usage
    exit 1
    ;;
esac
```

### .gitignore additions for terraform/
```
terraform/**/.terraform/
terraform/**/*.tfstate
terraform/**/*.tfstate.backup
terraform/**/*.tfvars
terraform/**/.terraform.lock.hcl
```

## Execution Steps

1. `./terraform/buildkite-agents/run.sh bootstrap` — create state backend
2. `./terraform/buildkite-agents/run.sh plan` — preview agent infrastructure
3. `./terraform/buildkite-agents/run.sh apply` — create agent infrastructure
4. Verify build runs on new agents
5. Tear down old CloudFormation stack in us-east-1
