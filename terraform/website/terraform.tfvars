domain                       = "mock-server.com"
build_account_agent_role_arn = "arn:aws:iam::814548061024:role/buildkite-mockserver-release-95bef2c5-Role"
zone_id                      = "Z1R2IC6XAWK4Y6"
acm_certificate_arn          = "arn:aws:acm:us-east-1:014848309742:certificate/80ca7e79-1a03-406a-a0ef-d75317459232"
latest_version               = "5-15"

sites = {
  "5-10" = { bucket_name = "aws-website-mockserver-5-10" }
  "5-11" = { bucket_name = "aws-website-mockserver-5-11" }
  "5-12" = { bucket_name = "aws-website-mockserver-5-12" }
  "5-13" = { bucket_name = "aws-website-mockserver-5-13" }
  "5-14" = { bucket_name = "aws-website-mockserver-5-14" }
  "5-15" = { bucket_name = "aws-website-mockserver-nb9hq" }
}
