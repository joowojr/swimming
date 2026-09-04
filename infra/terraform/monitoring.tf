resource "aws_cloudwatch_log_group" "ec2" {
  name              = "/${var.project_name}/${var.environment}/ec2"
  retention_in_days = 30

  tags = {
    Name    = "${local.name_prefix}-ec2-logs"
    Service = "backend"
  }
}

resource "aws_sns_topic" "alarms" {
  name = "${local.name_prefix}-infrastructure-alarms"

  tags = {
    Name    = "${local.name_prefix}-infrastructure-alarms"
    Service = "monitoring"
  }
}

resource "aws_sns_topic_subscription" "alarm_email" {
  count = var.alarm_email == null ? 0 : 1

  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

resource "aws_cloudwatch_metric_alarm" "ec2_status" {
  alarm_name          = "${local.name_prefix}-ec2-status-check-failed"
  alarm_description   = "EC2 instance or system status check failed"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  dimensions          = { InstanceId = aws_instance.app.id }
  comparison_operator = "GreaterThanOrEqualToThreshold"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  treat_missing_data  = "missing"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = {
    Service = "backend"
  }
}

resource "aws_cloudwatch_metric_alarm" "ec2_cpu" {
  alarm_name          = "${local.name_prefix}-ec2-cpu-high"
  alarm_description   = "EC2 average CPU utilization is at least 80 percent for 10 minutes"
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  dimensions          = { InstanceId = aws_instance.app.id }
  comparison_operator = "GreaterThanOrEqualToThreshold"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  treat_missing_data  = "missing"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = {
    Service = "backend"
  }
}

resource "aws_cloudwatch_metric_alarm" "ec2_memory" {
  alarm_name          = "${local.name_prefix}-ec2-memory-high"
  alarm_description   = "EC2 memory usage is at least 85 percent for 10 minutes"
  namespace           = "Swimming/EC2"
  metric_name         = "mem_used_percent"
  dimensions          = { InstanceId = aws_instance.app.id }
  comparison_operator = "GreaterThanOrEqualToThreshold"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 85
  treat_missing_data  = "missing"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = {
    Service = "backend"
  }
}

resource "aws_cloudwatch_metric_alarm" "ec2_disk" {
  alarm_name        = "${local.name_prefix}-ec2-disk-high"
  alarm_description = "EC2 root filesystem usage is at least 80 percent for 10 minutes"
  namespace         = "Swimming/EC2"
  metric_name       = "disk_used_percent"
  dimensions = {
    InstanceId = aws_instance.app.id
    fstype     = "xfs"
    path       = "/"
  }
  comparison_operator = "GreaterThanOrEqualToThreshold"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  treat_missing_data  = "missing"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = {
    Service = "backend"
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${local.name_prefix}-rds-cpu-high"
  alarm_description   = "RDS average CPU utilization is at least 80 percent for 10 minutes"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  comparison_operator = "GreaterThanOrEqualToThreshold"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  treat_missing_data  = "missing"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = {
    Service = "database"
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name          = "${local.name_prefix}-rds-storage-low"
  alarm_description   = "RDS free storage is below 5 GiB"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  comparison_operator = "LessThanThreshold"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 5368709120
  treat_missing_data  = "missing"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = {
    Service = "database"
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "${local.name_prefix}-rds-connections-high"
  alarm_description   = "RDS database connections are at least 80"
  namespace           = "AWS/RDS"
  metric_name         = "DatabaseConnections"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  comparison_operator = "GreaterThanOrEqualToThreshold"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  treat_missing_data  = "missing"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = {
    Service = "database"
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_memory" {
  alarm_name          = "${local.name_prefix}-rds-memory-low"
  alarm_description   = "RDS freeable memory is below 256 MiB"
  namespace           = "AWS/RDS"
  metric_name         = "FreeableMemory"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  comparison_operator = "LessThanThreshold"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 268435456
  treat_missing_data  = "missing"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = {
    Service = "database"
  }
}
