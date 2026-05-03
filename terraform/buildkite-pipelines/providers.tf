data "aws_secretsmanager_secret_version" "buildkite_api_token" {
  secret_id = "mockserver-build/buildkite-api-token"
}

provider "aws" {
  region  = "eu-west-2"
  profile = "mockserver-build"
}

provider "buildkite" {
  organization = "mockserver"
  api_token    = data.aws_secretsmanager_secret_version.buildkite_api_token.secret_string
}
