terraform {
  # 1.10 introduced native S3 state locking through use_lockfile, which replaces
  # the separate DynamoDB lock table.
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.80, < 7.0"
    }
  }

  # The bucket is created outside Terraform on purpose: a backend cannot
  # bootstrap the bucket that stores its own state. It has versioning, SSE-S3,
  # full public access block, a TLS-only policy, and a 90-day expiry for
  # noncurrent versions.
  backend "s3" {
    bucket       = "swimming-prod-tfstate"
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
