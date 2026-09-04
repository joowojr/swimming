resource "aws_ssm_association" "update_agent" {
  name             = "AWS-UpdateSSMAgent"
  association_name = "${local.name_prefix}-update-ssm-agent"

  targets {
    key    = "InstanceIds"
    values = [aws_instance.app.id]
  }

  schedule_expression = "rate(14 days)"
}
