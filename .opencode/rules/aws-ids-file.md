# AWS Resource Identifiers — External File Required

## Rule

AWS account IDs, SSO portal URLs, resource identifiers, and other sensitive AWS infrastructure details are stored in `~/mockserver-aws-ids.md`, **not** in this repository.

Before performing any AWS operation, **verify** the file exists:

```bash
test -f ~/mockserver-aws-ids.md || echo "ERROR: ~/mockserver-aws-ids.md not found. This file contains AWS account IDs, SSO URLs, and resource identifiers needed for AWS operations. Ask the user to restore it from their password manager."
```

## When This Matters

- Investigating AWS infrastructure issues
- Running Terraform commands
- Configuring AWS CLI profiles
- Looking up resource identifiers (VPC IDs, distribution IDs, bucket names, etc.)

## What the File Contains

- AWS account IDs for both accounts (build + website)
- SSO portal URLs and identity store IDs
- All AWS resource identifiers (VPC, subnet, security group, Lambda, CloudFront, Route53, ACM, etc.)
- S3 bucket names, SSM parameter paths, Secrets Manager secret names

## If the File Is Missing

1. Ask the user to restore it from their password manager
2. Do NOT attempt to reconstruct it by querying AWS — the user may not be authenticated
3. Do NOT hard-code account IDs or resource identifiers in repository files
