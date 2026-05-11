output "main_distribution_id" {
  value       = aws_cloudfront_distribution.main.id
  description = "CloudFront distribution ID for the main website"
}

output "main_distribution_domain" {
  value       = aws_cloudfront_distribution.main.domain_name
  description = "CloudFront distribution domain name for the main website"
}

output "main_bucket_name" {
  value       = aws_s3_bucket.site[var.latest_version].bucket
  description = "S3 bucket name for the main website (latest version)"
}

output "release_website_role_arn" {
  value       = aws_iam_role.release_website.arn
  description = "IAM role ARN for cross-account website access from build account"
}

output "distribution_ids" {
  value       = { for k, v in aws_cloudfront_distribution.site : k => v.id }
  description = "CloudFront distribution IDs for versioned sites"
}
