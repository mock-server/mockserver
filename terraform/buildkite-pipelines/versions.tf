terraform {
  required_version = ">= 1.15"

  required_providers {
    buildkite = {
      source  = "buildkite/buildkite"
      version = "~> 1.0"
    }
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}
