output "auto_scaling_group_name" {
  description = "Name of the default agent ASG"
  value       = module.buildkite_stack.auto_scaling_group_name
}

output "release_auto_scaling_group_name" {
  description = "Name of the release agent ASG"
  value       = module.buildkite_release_stack.auto_scaling_group_name
}

output "lambda_scaler_arn" {
  description = "ARN of the Lambda scaler function (default queue)"
  value       = module.buildkite_stack.scaler_lambda_function_arn
}

output "release_lambda_scaler_arn" {
  description = "ARN of the Lambda scaler function (release queue)"
  value       = module.buildkite_release_stack.scaler_lambda_function_arn
}

output "vpc_id" {
  description = "VPC ID where agents run"
  value       = module.buildkite_stack.vpc_id
}

output "dashboard_url" {
  description = "CloudWatch Dashboard URL"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.region}#dashboards:name=buildkite-mockserver-infrastructure"
}

output "sns_topic_arn" {
  description = "SNS topic ARN for infrastructure alerts"
  value       = aws_sns_topic.buildkite_alerts.arn
}
