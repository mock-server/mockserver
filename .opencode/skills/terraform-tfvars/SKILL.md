---
name: terraform-tfvars
description: >
  Creates the terraform.tfvars file for the Buildkite build agent Terraform
  stack. Documents how to retrieve the Buildkite agent token from Buildkite UI
  (via Chrome DevTools MCP) and AWS SSM Parameter Store, validate tokens, and
  populate the tfvars file. Use when users say "create tfvars", "set up
  terraform variables", "deploy buildkite agents", "configure buildkite token",
  or need to recreate the terraform.tfvars after a fresh checkout.

---

# Create terraform.tfvars for Buildkite Agents

The Terraform stack at `terraform/buildkite-agents/` deploys Buildkite build
agents to AWS. It requires a `terraform.tfvars` file containing the Buildkite
agent registration token. This file is **gitignored and must NEVER be
committed**.

## Prerequisites

- **AWS CLI** installed and **SSO session active** (`aws sso login --profile mockserver-build`)
- **Chrome DevTools MCP** configured with `--autoConnect` (for Buildkite UI retrieval)
- **Buildkite org admin access** (to view agent tokens)

## Token Retrieval

The Buildkite agent token can be retrieved from two sources. Always retrieve
from **both** and compare to ensure consistency.

### Source 1: AWS SSM Parameter Store (Authoritative)

The token is stored as a SecureString in the legacy us-east-1 region:

```bash
export DYLD_LIBRARY_PATH="/opt/homebrew/opt/expat/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
export AWS_CA_BUNDLE="${NODE_EXTRA_CA_CERTS:-}"

aws ssm get-parameter \
  --name /buildkite/buildkite/agent-token \
  --region us-east-1 \
  --profile mockserver-build \
  --with-decryption \
  --query 'Parameter.Value' \
  --output text
```

This is the authoritative source — it's the same token the legacy
CloudFormation stack uses and has been validated in production.

### Source 2: Buildkite UI (via Chrome DevTools MCP)

Navigate to the Buildkite agents page and extract the token:

1. Ensure the user is logged into Buildkite in Chrome
2. Navigate: `navigate_page(url="https://buildkite.com/organizations/mockserver/agents")`
3. Take a snapshot: `take_snapshot()`
4. Look for the "Agent Token" section
5. Click "Reveal Agent Token" if the token is hidden
6. Extract the token value from the snapshot or via `evaluate_script()`

See the `browser-auth` skill for detailed Chrome MCP patterns.

### Token Validation

Compare both values:
- If they **match**: Use the token — it's verified correct
- If they **differ**: The SSM token is authoritative (it's what agents
  currently use in production). The Buildkite UI may show a different token
  if the org has multiple tokens. Verify which token is associated with the
  `default` queue.
- If SSM retrieval **fails**: Use the Buildkite UI token, but verify with
  the user that it's the correct one for the `default` queue.

## Create terraform.tfvars

The file lives at `terraform/buildkite-agents/terraform.tfvars`. Create it
from the example template:

```bash
cp terraform/buildkite-agents/terraform.tfvars.example \
   terraform/buildkite-agents/terraform.tfvars
```

Then set `buildkite_agent_token` in `terraform/buildkite-agents/terraform.tfvars`.

**CRITICAL COST REQUIREMENT:** Verify `min_size = 0` (MUST always be zero):
```bash
grep min_size terraform/buildkite-agents/terraform.tfvars
# Should show: min_size = 0
```

If `min_size` is not `0`, agents run 24/7 incurring unnecessary cost. The Lambda autoscaler handles all scaling based on queue depth — pre-created agents are never needed.

Minimal required change:

```hcl
# NEVER commit this file — it is gitignored.
# Token retrieved from AWS SSM: /buildkite/buildkite/agent-token (us-east-1)

buildkite_agent_token = "<TOKEN_VALUE>"
```

### Variable Reference

| Variable | Source of truth | Description |
|----------|-----------------|-------------|
| `buildkite_agent_token` | `terraform.tfvars` | Buildkite agent registration token (sensitive) |
| `region` | `variables.tf` / `terraform.tfvars` | AWS region for the agent stack |
| `instance_types` | `variables.tf` / `terraform.tfvars` | EC2 instance types (comma-separated) |
| `min_size` | `variables.tf` / `terraform.tfvars` | **CRITICAL: MUST be `0`** for scale-to-zero cost control — NEVER set to non-zero |
| `max_size` | `variables.tf` / `terraform.tfvars` | Maximum ASG size |
| `on_demand_percentage` | `variables.tf` / `terraform.tfvars` | Spot/On-Demand mix |

Read current defaults from `terraform/buildkite-agents/variables.tf` and current
overrides from `terraform/buildkite-agents/terraform.tfvars.example`.

## Deploy

After creating `terraform.tfvars`, deploy with:

```bash
cd terraform/buildkite-agents
./run.sh apply
```

The `run.sh` wrapper handles:
- AWS SSO authentication check (prompts to login if expired)
- Corporate TLS proxy (`AWS_CA_BUNDLE` from `NODE_EXTRA_CA_CERTS`)
- macOS pyexpat workaround (`DYLD_LIBRARY_PATH`)
- `terraform init` + `terraform apply`

Review the Terraform plan and type `yes` to confirm.

## Verification

After deployment, verify agents are working:

1. Check the ASG was created:
   ```bash
   cd terraform/buildkite-agents
   terraform output auto_scaling_group_name
   ```

2. Trigger a Buildkite build and confirm an agent picks it up

3. Check the Buildkite agents page for registered agents

## Security Reminders

- `terraform.tfvars` is gitignored via `terraform/**/*.tfvars` in `.gitignore`
- **NEVER** commit this file or include the token in commit messages
- **NEVER** log the token value in CI/CD output
- The token is marked `sensitive = true` in `variables.tf` — Terraform will
  redact it from plan/apply output
- If the token is compromised, regenerate it in Buildkite and update both
  SSM Parameter Store and `terraform.tfvars`
