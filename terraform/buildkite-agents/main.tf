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

  instance_types      = var.instance_types
  min_size            = var.min_size
  max_size            = var.max_size
  on_demand_percentage = var.on_demand_percentage

  agents_per_instance          = 1
  associate_public_ip_address  = true
}
