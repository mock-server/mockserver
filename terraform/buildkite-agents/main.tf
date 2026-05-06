provider "aws" {
  region  = var.region
  profile = "mockserver-build"
}

module "buildkite_stack" {
  source  = "buildkite/elastic-ci-stack-for-aws/buildkite"
  version = "~> 0.7.0"

  stack_name            = "buildkite-mockserver"
  buildkite_agent_token = var.buildkite_agent_token
  buildkite_queue       = "default"

  instance_types          = var.instance_types
  min_size                = var.min_size
  max_size                = var.max_size
  on_demand_percentage    = var.on_demand_percentage
  on_demand_base_capacity = 1 # Always launch at least 1 on-demand instance when scaling up

  agents_per_instance         = 1
  associate_public_ip_address = true
  managed_policy_arns         = [aws_iam_policy.read_dockerhub_secret.arn]
}
