# AWS Cleanup Plan

Remaining cleanup tasks from the AWS infrastructure audit (2026-05-03).
Security items have already been resolved — this plan covers only non-sensitive cleanup and improvements.

## Phase 1: CloudWatch Log Cleanup

### 1.1 Delete 8 orphaned log groups in website account (us-east-1)

Legacy Buildkite agent log groups from a deleted stack. ~2.5 MB total, no retention set.

```bash
for lg in \
  "/aws/lambda/buildkite-BuildkiteMetricsFunction-1NPNHHASHI8SR" \
  "/var/log/buildkite-agent.log" \
  "/var/log/cfn-init.log" \
  "/var/log/cloud-init-output.log" \
  "/var/log/cloud-init.log" \
  "/var/log/docker" \
  "/var/log/elastic-stack.log" \
  "/var/log/messages"; do
  aws logs delete-log-group --log-group-name "$lg" --profile mockserver-website --region us-east-1
done
```

### 1.2 Set 30-day retention on build account log groups (eu-west-2)

Active log groups have no retention policy — logs grow without bound.

```bash
for lg in $(aws logs describe-log-groups --profile mockserver-build --region eu-west-2 \
  --query 'logGroups[?!retentionInDays].logGroupName' --output text); do
  aws logs put-retention-policy --log-group-name "$lg" --retention-in-days 30 \
    --profile mockserver-build --region eu-west-2
done
```

## Phase 2: Unused Resource Cleanup

### 2.1 Delete unused VPC in website account (us-east-1)

Non-default VPC `vpc-e78d579e` (`10.0.0.0/16`) with 1 subnet, 3 security groups, no instances.

```bash
aws ec2 delete-security-group --group-id sg-af150cd1 --profile mockserver-website --region us-east-1
aws ec2 delete-security-group --group-id sg-81ff2cf5 --profile mockserver-website --region us-east-1
aws ec2 delete-subnet --subnet-id subnet-8c3c3be9 --profile mockserver-website --region us-east-1
aws ec2 delete-vpc --vpc-id vpc-e78d579e --profile mockserver-website --region us-east-1
```

### 2.2 Delete default VPC in build account (eu-west-2)

Default VPC `vpc-2db2e745` (`172.31.0.0/16`) — unused, can be recreated if ever needed.

```bash
# Delete subnets, IGW, then VPC
aws ec2 describe-subnets --filters "Name=vpc-id,Values=vpc-2db2e745" \
  --profile mockserver-build --region eu-west-2 --query 'Subnets[].SubnetId' --output text \
  | tr '\t' '\n' | while read sid; do aws ec2 delete-subnet --subnet-id "$sid" \
    --profile mockserver-build --region eu-west-2; done

IGW=$(aws ec2 describe-internet-gateways --filters "Name=attachment.vpc-id,Values=vpc-2db2e745" \
  --profile mockserver-build --region eu-west-2 --query 'InternetGateways[0].InternetGatewayId' --output text)
aws ec2 detach-internet-gateway --internet-gateway-id "$IGW" --vpc-id vpc-2db2e745 \
  --profile mockserver-build --region eu-west-2
aws ec2 delete-internet-gateway --internet-gateway-id "$IGW" \
  --profile mockserver-build --region eu-west-2

aws ec2 delete-vpc --vpc-id vpc-2db2e745 --profile mockserver-build --region eu-west-2
```

### 2.3 Delete unused DynamoDB table (build account, eu-west-2)

`mockserver-terraform-locks` — created by Terraform bootstrap but `backend.tf` uses S3-native lockfile. Contains 0 items.

```bash
aws dynamodb delete-table --table-name mockserver-terraform-locks \
  --profile mockserver-build --region eu-west-2
```

### 2.4 Delete unused ECR repository (website account, us-east-1)

`jamesdbloom_two` — created 2017, likely unused. Check for images first.

```bash
aws ecr list-images --repository-name jamesdbloom_two --profile mockserver-website --region us-east-1
# If empty or unused:
aws ecr delete-repository --repository-name jamesdbloom_two --profile mockserver-website --region us-east-1
```

## Phase 3: Tagging and Organisation

### 3.1 Tag Terraform state bucket

```bash
aws s3api put-bucket-tagging --bucket mockserver-terraform-state \
  --tagging 'TagSet=[{Key=Project,Value=mockserver},{Key=ManagedBy,Value=terraform}]' \
  --profile mockserver-build --region eu-west-2
```

## Phase 4: GitHub Actions OIDC

### 4.1 Apply build-secrets Terraform resources

`terraform/buildkite-agents/build-secrets.tf` defines OIDC provider, GitHub Actions IAM role, and Secrets Manager secret for Docker Hub credentials. These have NOT been applied yet.

```bash
cd terraform/buildkite-agents
terraform plan   # Review changes
terraform apply  # Creates OIDC provider + IAM role + Secrets Manager secret
```

This enables GitHub Actions to push Docker images to Docker Hub via OIDC federation without storing long-lived credentials in GitHub Secrets.

## Execution Order

1. Phase 1 (CloudWatch) — safe, no dependencies
2. Phase 2 (unused resources) — safe, no active services depend on these
3. Phase 3 (tagging) — safe, metadata only
4. Phase 4 (OIDC) — apply when ready to enable GitHub Actions Docker push
