terraform {
  backend "s3" {
    bucket         = "mockserver-terraform-state"
    key            = "buildkite-agents/terraform.tfstate"
    region         = "eu-west-2"
    use_lockfile   = true
    encrypt        = true
    profile        = "mockserver-build"
  }
}
