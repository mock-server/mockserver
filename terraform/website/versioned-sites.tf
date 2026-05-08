resource "aws_s3_bucket" "versioned" {
  for_each = var.versioned_sites
  bucket   = each.value.bucket_name
}

resource "aws_s3_bucket_website_configuration" "versioned" {
  for_each = var.versioned_sites
  bucket   = aws_s3_bucket.versioned[each.key].id
  index_document { suffix = "index.html" }
  error_document { key = "404.html" }
}

resource "aws_s3_bucket_public_access_block" "versioned" {
  for_each                = var.versioned_sites
  bucket                  = aws_s3_bucket.versioned[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_identity" "versioned" {
  for_each = var.versioned_sites
  comment  = "OAI for ${each.key}.${var.domain}"
}

resource "aws_s3_bucket_policy" "versioned" {
  for_each = var.versioned_sites
  bucket   = aws_s3_bucket.versioned[each.key].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { AWS = aws_cloudfront_origin_access_identity.versioned[each.key].iam_arn }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.versioned[each.key].arn}/*"
    }]
  })
}

resource "aws_cloudfront_distribution" "versioned" {
  for_each            = var.versioned_sites
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  aliases             = ["${each.key}.${var.domain}"]
  price_class         = "PriceClass_100"

  origin {
    domain_name = aws_s3_bucket.versioned[each.key].bucket_regional_domain_name
    origin_id   = "S3-${each.value.bucket_name}"

    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.versioned[each.key].cloudfront_access_identity_path
    }
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "S3-${each.value.bucket_name}"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }
  }

  viewer_certificate {
    acm_certificate_arn      = var.acm_certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }
}

resource "aws_route53_record" "versioned" {
  for_each = var.versioned_sites
  zone_id  = var.zone_id
  name     = "${each.key}.${var.domain}"
  type     = "A"

  alias {
    name                   = aws_cloudfront_distribution.versioned[each.key].domain_name
    zone_id                = aws_cloudfront_distribution.versioned[each.key].hosted_zone_id
    evaluate_target_health = false
  }
}
