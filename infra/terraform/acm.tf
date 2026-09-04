# The domain is delegated to an external registrar, not Route 53, so Terraform
# cannot publish the DNS validation record. aws_acm_certificate_validation is
# deliberately absent: with no automated record it would block apply until it
# times out. Publish the record from the acm_validation_record output by hand,
# wait for the certificate to reach ISSUED, then set attach_web_domain = true.
resource "aws_acm_certificate" "web" {
  count    = var.web_domain_name == null ? 0 : 1
  provider = aws.acm

  domain_name       = var.web_domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name    = "${local.name_prefix}-web-cert"
    Service = "frontend"
  }
}

locals {
  acm_validation = one(aws_acm_certificate.web[*].domain_validation_options)
}
