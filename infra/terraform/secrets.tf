resource "aws_secretsmanager_secret" "backend_runtime" {
  name                    = "${local.name_prefix}/backend/runtime"
  description             = "Runtime environment values for the Swimming backend; values are populated outside Terraform"
  recovery_window_in_days = 30

  tags = {
    Name    = "${local.name_prefix}-backend-runtime"
    Service = "backend"
  }
}
