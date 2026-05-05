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

resource "aws_iam_policy" "read_dockerhub_secret" {
  name        = "buildkite-read-dockerhub-secret"
  description = "Allow Buildkite agents to read Docker Hub credentials from Secrets Manager"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "secretsmanager:GetSecretValue"
      Resource = [
        aws_secretsmanager_secret.dockerhub.arn,
        aws_secretsmanager_secret.sonatype.arn,
      ]
    }]
  })
}
