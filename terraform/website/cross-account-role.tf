resource "aws_iam_role" "release_website" {
  name = "mockserver-release-website"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { AWS = var.build_account_agent_role_arn }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "release_website" {
  name = "website-access"
  role = aws_iam_role.release_website.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:ListBucket"]
        Resource = concat(
          [for b in values(aws_s3_bucket.site) : b.arn],
          [for b in values(aws_s3_bucket.site) : "${b.arn}/*"]
        )
      },
      {
        Effect = "Allow"
        Action = "cloudfront:CreateInvalidation"
        Resource = concat(
          [for d in values(aws_cloudfront_distribution.site) : d.arn],
          [aws_cloudfront_distribution.main.arn]
        )
      }
    ]
  })
}
