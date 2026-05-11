variable "domain" {
  type        = string
  default     = "mock-server.com"
  description = "Root domain for the website"
}

variable "latest_version" {
  type        = string
  description = "Version key in sites map that serves as the main website (e.g. '5-15')"
}

variable "build_account_agent_role_arn" {
  type        = string
  description = "IAM role ARN of the Buildkite agent in the build account, used in the cross-account trust policy"
}

variable "sites" {
  type = map(object({
    bucket_name = string
  }))
  description = "Map of version key (e.g. '5-15') to its S3 bucket name. The latest_version entry serves as the main site."
}

variable "zone_id" {
  type        = string
  description = "Route53 hosted zone ID for mock-server.com"
}

variable "acm_certificate_arn" {
  type        = string
  description = "ACM certificate ARN covering *.mock-server.com and mock-server.com (us-east-1)"
}
