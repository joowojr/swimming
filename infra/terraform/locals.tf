locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = {
    Environment = var.environment
    Owner       = var.owner
    ManagedBy   = "terraform"
    Project     = var.project_name
  }
}
