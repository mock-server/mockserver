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

variable "release_min_size" {
  description = "Minimum number of release agent instances (0 = scale to zero when idle)"
  type        = number
  default     = 0
}

variable "release_max_size" {
  description = "Maximum number of release agent instances (release queue)"
  type        = number
  default     = 2
}

variable "trigger_instance_types" {
  description = "EC2 instance types for trigger queue (cheap, low-CPU — only runs curl/sleep polling loops)"
  type        = string
  default     = "t3.small"
}

variable "trigger_min_size" {
  description = "Minimum number of trigger agent instances (0 = scale to zero when idle)"
  type        = number
  default     = 0
}

variable "trigger_max_size" {
  description = "Maximum number of trigger agent instances"
  type        = number
  default     = 4
}

variable "alert_email" {
  description = "Email address for infrastructure alerts (SNS notifications)"
  type        = string
  default     = ""
}
