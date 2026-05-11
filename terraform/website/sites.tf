locals {
  main_aliases = [var.domain, "www.${var.domain}"]
}

resource "aws_s3_bucket" "site" {
  provider = aws.eu-west-2
  for_each = var.sites
  bucket   = each.value.bucket_name
  tags = {
    Name = "${each.key}.${var.domain}"
  }
}

resource "aws_s3_bucket_website_configuration" "site" {
  provider = aws.eu-west-2
  for_each = var.sites
  bucket   = aws_s3_bucket.site[each.key].id
  index_document { suffix = "index.html" }
  error_document { key = "404.html" }
}

resource "aws_s3_bucket_public_access_block" "site" {
  provider                = aws.eu-west-2
  for_each                = var.sites
  bucket                  = aws_s3_bucket.site[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_identity" "site" {
  for_each = var.sites
  comment  = "OAI for ${each.key}.${var.domain}"
}

resource "aws_cloudfront_origin_access_identity" "main" {
  comment = "OAI for ${var.domain} (main distribution)"
}

resource "aws_s3_bucket_policy" "site" {
  provider = aws.eu-west-2
  for_each = var.sites
  bucket   = aws_s3_bucket.site[each.key].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        AWS = concat(
          [aws_cloudfront_origin_access_identity.site[each.key].iam_arn],
          each.key == var.latest_version ? [aws_cloudfront_origin_access_identity.main.iam_arn] : []
        )
      }
      Action   = "s3:GetObject"
      Resource = "${aws_s3_bucket.site[each.key].arn}/*"
    }]
  })
}

resource "aws_cloudfront_distribution" "site" {
  for_each            = var.sites
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  aliases             = ["${each.key}.${var.domain}"]
  price_class         = "PriceClass_All"
  http_version        = "http2and3"
  comment             = "${each.key}.${var.domain}"

  origin {
    domain_name = aws_s3_bucket.site[each.key].bucket_regional_domain_name
    origin_id   = "S3-${each.value.bucket_name}"

    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.site[each.key].cloudfront_access_identity_path
    }
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "S3-${each.value.bucket_name}"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

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

resource "aws_cloudfront_distribution" "main" {
  # Ensure site distributions drop domain aliases before main claims them (avoids CloudFront CNAME conflict)
  depends_on          = [aws_cloudfront_distribution.site]
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  aliases             = local.main_aliases
  price_class         = "PriceClass_All"
  http_version        = "http2and3"
  comment             = var.domain

  origin {
    domain_name = aws_s3_bucket.site[var.latest_version].bucket_regional_domain_name
    origin_id   = "S3-${var.sites[var.latest_version].bucket_name}"

    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.main.cloudfront_access_identity_path
    }
  }

  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/error403.html"
    error_caching_min_ttl = 300
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "S3-${var.sites[var.latest_version].bucket_name}"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

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

resource "aws_route53_record" "site" {
  for_each = var.sites
  zone_id  = var.zone_id
  name     = "${each.key}.${var.domain}"
  type     = "A"

  alias {
    name                   = aws_cloudfront_distribution.site[each.key].domain_name
    zone_id                = aws_cloudfront_distribution.site[each.key].hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "main" {
  zone_id = var.zone_id
  name    = var.domain
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.main.domain_name
    zone_id                = aws_cloudfront_distribution.main.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "www" {
  zone_id = var.zone_id
  name    = "www.${var.domain}"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.main.domain_name
    zone_id                = aws_cloudfront_distribution.main.hosted_zone_id
    evaluate_target_health = false
  }
}
