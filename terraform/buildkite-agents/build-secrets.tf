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

moved {
  from = aws_iam_policy.read_dockerhub_secret
  to   = aws_iam_policy.read_build_secrets
}

resource "aws_iam_policy" "read_build_secrets" {
  name        = "buildkite-read-build-secrets"
  description = "Allow Buildkite agents to read build and release credentials from Secrets Manager"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "secretsmanager:GetSecretValue"
      Resource = [
        aws_secretsmanager_secret.dockerhub.arn,
        aws_secretsmanager_secret.sonatype.arn,
        aws_secretsmanager_secret.pypi.arn,
        aws_secretsmanager_secret.rubygems.arn,
      ]
    }]
  })
}
