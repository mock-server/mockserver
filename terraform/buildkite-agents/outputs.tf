output "auto_scaling_group_name" {
  description = "Name of the agent ASG"
  value       = module.buildkite_stack.auto_scaling_group_name
}

output "vpc_id" {
  description = "VPC ID where agents run"
  value       = module.buildkite_stack.vpc_id
}
