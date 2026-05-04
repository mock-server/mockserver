variable "buildkite_agent_token" {
  description = "Buildkite agent registration token"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "eu-west-2"
}

variable "instance_types" {
  description = "EC2 instance types (comma-separated). First type preferred for on-demand."
  type        = string
  default     = "c5.2xlarge"
}

variable "min_size" {
  description = "Minimum number of agent instances (0 = scale to zero when idle)"
  type        = number
  default     = 0
}

variable "max_size" {
  description = "Maximum number of agent instances"
  type        = number
  default     = 10
}

variable "on_demand_percentage" {
  description = "Percentage of on-demand instances (0 = all spot, 100 = all on-demand)"
  type        = number
  default     = 0
}

variable "alert_email" {
  description = "Email address for infrastructure alerts (SNS notifications)"
  type        = string
  default     = ""
}
