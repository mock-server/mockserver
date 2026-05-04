# CloudWatch Alarms and SNS notifications for Buildkite agent infrastructure
# Monitoring focuses on available AWS metrics since Buildkite custom metrics are disabled

# SNS topic for infrastructure alerts
resource "aws_sns_topic" "buildkite_alerts" {
  name         = "buildkite-mockserver-alerts"
  display_name = "Buildkite Agent Infrastructure Alerts"
}

resource "aws_sns_topic_subscription" "buildkite_alerts_email" {
  count     = var.alert_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.buildkite_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

data "aws_caller_identity" "current" {}

# SNS topic policy allowing CloudWatch and EventBridge to publish
resource "aws_sns_topic_policy" "buildkite_alerts" {
  arn = aws_sns_topic.buildkite_alerts.arn

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudWatchAlarms"
        Effect = "Allow"
        Principal = {
          Service = "cloudwatch.amazonaws.com"
        }
        Action   = "SNS:Publish"
        Resource = aws_sns_topic.buildkite_alerts.arn
        Condition = {
          StringEquals = {
            "aws:SourceAccount" = data.aws_caller_identity.current.account_id
          }
        }
      },
      {
        Sid    = "AllowEventBridge"
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
        Action   = "SNS:Publish"
        Resource = aws_sns_topic.buildkite_alerts.arn
        Condition = {
          StringEquals = {
            "aws:SourceAccount" = data.aws_caller_identity.current.account_id
          }
          ArnEquals = {
            "aws:SourceArn" = aws_cloudwatch_event_rule.asg_launch_failures.arn
          }
        }
      }
    ]
  })
}

# Alarm: ASG desired capacity not met (instances failing to launch)
resource "aws_cloudwatch_metric_alarm" "asg_capacity_gap" {
  alarm_name          = "buildkite-mockserver-capacity-gap"
  alarm_description   = "ASG desired capacity not met for 5 minutes (EC2 launch failures or Spot unavailability)"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  threshold           = 0
  datapoints_to_alarm = 3
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "desired"
    return_data = false

    metric {
      metric_name = "GroupDesiredCapacity"
      namespace   = "AWS/AutoScaling"
      period      = 60
      stat        = "Average"

      dimensions = {
        AutoScalingGroupName = module.buildkite_stack.auto_scaling_group_name
      }
    }
  }

  metric_query {
    id          = "actual"
    return_data = false

    metric {
      metric_name = "GroupInServiceInstances"
      namespace   = "AWS/AutoScaling"
      period      = 60
      stat        = "Average"

      dimensions = {
        AutoScalingGroupName = module.buildkite_stack.auto_scaling_group_name
      }
    }
  }

  metric_query {
    id          = "capacity_gap"
    expression  = "desired - actual"
    label       = "Capacity Gap"
    return_data = true
  }

  alarm_actions = [aws_sns_topic.buildkite_alerts.arn]
}

# Alarm: Lambda scaler errors
resource "aws_cloudwatch_metric_alarm" "scaler_lambda_errors" {
  alarm_name          = "buildkite-mockserver-scaler-errors"
  alarm_description   = "Lambda scaler function is experiencing errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  threshold           = 0
  treat_missing_data  = "notBreaching"

  metric_name = "Errors"
  namespace   = "AWS/Lambda"
  period      = 300
  statistic   = "Sum"

  dimensions = {
    FunctionName = module.buildkite_stack.scaler_lambda_function_name
  }

  alarm_actions = [aws_sns_topic.buildkite_alerts.arn]
}

# Alarm: Lambda scaler not invoked (EventBridge schedule broken)
resource "aws_cloudwatch_metric_alarm" "scaler_lambda_not_invoked" {
  alarm_name          = "buildkite-mockserver-scaler-not-invoked"
  alarm_description   = "Lambda scaler has not been invoked in 5 minutes (EventBridge schedule may be broken)"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  threshold           = 1
  treat_missing_data  = "breaching"

  metric_name = "Invocations"
  namespace   = "AWS/Lambda"
  period      = 300
  statistic   = "Sum"

  dimensions = {
    FunctionName = module.buildkite_stack.scaler_lambda_function_name
  }

  alarm_actions = [aws_sns_topic.buildkite_alerts.arn]
}

# EventBridge rule to capture ASG scaling failures
resource "aws_cloudwatch_event_rule" "asg_launch_failures" {
  name        = "buildkite-mockserver-asg-launch-failures"
  description = "Capture AutoScaling launch failures"

  event_pattern = jsonencode({
    source      = ["aws.autoscaling"]
    detail-type = ["EC2 Instance Launch Unsuccessful"]
    detail = {
      AutoScalingGroupName = [module.buildkite_stack.auto_scaling_group_name]
    }
  })
}

resource "aws_cloudwatch_event_target" "asg_launch_failures_to_sns" {
  rule      = aws_cloudwatch_event_rule.asg_launch_failures.name
  target_id = "SendToSNS"
  arn       = aws_sns_topic.buildkite_alerts.arn

  input_transformer {
    input_paths = {
      time   = "$.time"
      cause  = "$.detail.Cause"
      asgName = "$.detail.AutoScalingGroupName"
    }
    input_template = "\"Buildkite ASG Launch Failure at <time>: <cause> (ASG: <asgName>)\""
  }
}

# CloudWatch Dashboard
resource "aws_cloudwatch_dashboard" "buildkite_infrastructure" {
  dashboard_name = "buildkite-mockserver-infrastructure"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "Agent Capacity"
          region  = var.region
          metrics = [
            ["AWS/AutoScaling", "GroupDesiredCapacity", "AutoScalingGroupName", module.buildkite_stack.auto_scaling_group_name, { stat = "Average", label = "Desired" }],
            [".", "GroupInServiceInstances", ".", ".", { stat = "Average", label = "Running" }],
            [".", "GroupPendingInstances", ".", ".", { stat = "Average", label = "Pending" }]
          ]
          period = 60
          yAxis = {
            left = {
              min = 0
            }
          }
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "Lambda Scaler Health"
          region  = var.region
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", module.buildkite_stack.scaler_lambda_function_name, { stat = "Sum", label = "Invocations" }],
            [".", "Errors", ".", ".", { stat = "Sum", label = "Errors" }],
            [".", "Duration", ".", ".", { stat = "Average", label = "Duration (ms)" }]
          ]
          period = 60
          yAxis = {
            left = {
              min = 0
            }
          }
        }
      },
      {
        type   = "log"
        width  = 24
        height = 6
        properties = {
          title  = "Recent Scaler Lambda Logs"
          region = var.region
          query  = <<-EOT
            SOURCE '${module.buildkite_stack.scaler_log_group}'
            | fields @timestamp, @message
            | sort @timestamp desc
            | limit 100
          EOT
        }
      }
    ]
  })
}
