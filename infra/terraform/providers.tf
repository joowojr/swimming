provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

# CloudFront only accepts ACM certificates issued in us-east-1, regardless of
# where the rest of the infrastructure lives.
provider "aws" {
  alias  = "acm"
  region = "us-east-1"

  default_tags {
    tags = local.common_tags
  }
}
