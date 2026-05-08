resource "aws_secretsmanager_secret" "dockerhub" {
  name        = "mockserver-build/dockerhub"
  description = "Docker Hub credentials for pushing mockserver CI and release images"
}

resource "aws_secretsmanager_secret" "buildkite_api_token" {
  name        = "mockserver-build/buildkite-api-token"
  description = "Buildkite API token for Terraform pipeline management (GraphQL + REST scopes)"
}

resource "aws_secretsmanager_secret" "sonatype" {
  name        = "mockserver-build/sonatype"
  description = "Sonatype OSSRH credentials for Maven snapshot and release deployment"
}

resource "aws_secretsmanager_secret" "pypi" {
  name        = "mockserver-build/pypi"
  description = "PyPI API token for publishing mockserver-client Python package"
}

resource "aws_secretsmanager_secret" "rubygems" {
  name        = "mockserver-build/rubygems"
  description = "RubyGems API key for publishing mockserver-client Ruby gem"
}

resource "aws_secretsmanager_secret" "gpg_key" {
  name        = "mockserver-release/gpg-key"
  description = "GPG private key and passphrase for Maven Central artifact signing"
}

resource "aws_secretsmanager_secret" "github_token" {
  name        = "mockserver-release/github-token"
  description = "GitHub PAT for creating releases and Homebrew PRs"
}

resource "aws_secretsmanager_secret" "totp_seed" {
  name        = "mockserver-release/totp-seed"
  description = "TOTP shared secret for release authorization"
}

resource "aws_secretsmanager_secret" "npm_token" {
  name        = "mockserver-release/npm-token"
  description = "npm automation token for publishing packages"
}

resource "aws_secretsmanager_secret" "swaggerhub" {
  name        = "mockserver-release/swaggerhub"
  description = "SwaggerHub API key for publishing OpenAPI spec"
}

resource "aws_secretsmanager_secret" "website_role" {
  name        = "mockserver-release/website-role"
  description = "IAM role ARN for cross-account website access"
}

resource "aws_iam_policy" "read_build_secrets" {
  name        = "buildkite-read-build-secrets"
  description = "Allow Buildkite agents to read build credentials from Secrets Manager"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "secretsmanager:GetSecretValue"
      Resource = [
        aws_secretsmanager_secret.buildkite_api_token.arn,
        aws_secretsmanager_secret.dockerhub.arn,
        aws_secretsmanager_secret.sonatype.arn,
        aws_secretsmanager_secret.pypi.arn,
        aws_secretsmanager_secret.rubygems.arn,
      ]
    }]
  })
}

resource "aws_iam_policy" "read_release_secrets" {
  name        = "buildkite-read-release-secrets"
  description = "Allow Buildkite agents to read release credentials from Secrets Manager"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "secretsmanager:GetSecretValue"
        Resource = [
          aws_secretsmanager_secret.gpg_key.arn,
          aws_secretsmanager_secret.github_token.arn,
          aws_secretsmanager_secret.totp_seed.arn,
          aws_secretsmanager_secret.npm_token.arn,
          aws_secretsmanager_secret.swaggerhub.arn,
          aws_secretsmanager_secret.website_role.arn,
        ]
      },
      {
        Effect   = "Allow"
        Action   = "sts:AssumeRole"
        Resource = "arn:aws:iam::*:role/mockserver-release-website"
      }
    ]
  })
}
