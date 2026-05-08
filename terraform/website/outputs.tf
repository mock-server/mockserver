output "main_distribution_id" {
  value       = aws_cloudfront_distribution.main.id
  description = "CloudFront distribution ID for the main website"
}

output "main_distribution_domain" {
  value       = aws_cloudfront_distribution.main.domain_name
  description = "CloudFront distribution domain name for the main website"
}

output "release_website_role_arn" {
  value       = aws_iam_role.release_website.arn
  description = "IAM role ARN for cross-account website access from build account"
}

output "versioned_distribution_ids" {
  value       = { for k, v in aws_cloudfront_distribution.versioned : k => v.id }
  description = "CloudFront distribution IDs for versioned sites"
}
