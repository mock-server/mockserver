variable "domain" {
  type        = string
  default     = "mock-server.com"
  description = "Root domain for the website"
}

variable "main_bucket_name" {
  type        = string
  description = "S3 bucket name for the main website"
}

variable "build_account_agent_role_arn" {
  type        = string
  description = "IAM role ARN of the Buildkite agent in the build account, used in the cross-account trust policy"
}

variable "versioned_sites" {
  type = map(object({
    bucket_name = string
  }))
  description = "Map of version subdomain (e.g. '5-16') to its S3 bucket name"
}

variable "zone_id" {
  type        = string
  description = "Route53 hosted zone ID for mock-server.com"
}

variable "acm_certificate_arn" {
  type        = string
  description = "ACM certificate ARN covering *.mock-server.com and mock-server.com (us-east-1)"
}
