# One instance currently runs both Nginx and the Spring Boot container, so this
# security group merges the nginx-sg and api-sg roles from architecture.md.
# Splitting the hosts later means splitting this group, not rewriting the rules.
resource "aws_security_group" "app" {
  name_prefix = "${local.name_prefix}-app-"
  description = "Public HTTP/HTTPS ingress for Nginx; no SSH ingress. Port 8080 is never exposed."
  vpc_id      = aws_vpc.main.id

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name    = "${local.name_prefix}-app-sg"
    Service = "backend"
  }
}

resource "aws_vpc_security_group_ingress_rule" "app_http" {
  security_group_id = aws_security_group.app.id
  # AWS rejects apostrophes in security group rule descriptions.
  description = "HTTP redirect target and ACME HTTP-01 challenge"
  ip_protocol = "tcp"
  from_port   = 80
  to_port     = 80
  cidr_ipv4   = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "app_https" {
  security_group_id = aws_security_group.app.id
  description       = "HTTPS and WSS"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

# Egress is no longer wide open. Everything the host legitimately needs is
# enumerated below; anything else is denied.
resource "aws_vpc_security_group_egress_rule" "app_https_out" {
  security_group_id = aws_security_group.app.id
  description       = "ECR, SSM, Secrets Manager, CloudWatch, S3 endpoint, ACME, Google OAuth, LLM APIs"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "app_http_out" {
  security_group_id = aws_security_group.app.id
  description       = "Package repository mirrors and ACME OCSP"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = "0.0.0.0/0"
}

# Narrowing egress without these two rules breaks the instance during boot:
# name resolution goes to the VPC resolver and time sync goes to the Amazon
# Time Sync Service, and security group egress applies to both.
resource "aws_vpc_security_group_egress_rule" "app_dns_udp" {
  security_group_id = aws_security_group.app.id
  description       = "VPC DNS resolver"
  ip_protocol       = "udp"
  from_port         = 53
  to_port           = 53
  cidr_ipv4         = aws_vpc.main.cidr_block
}

resource "aws_vpc_security_group_egress_rule" "app_dns_tcp" {
  security_group_id = aws_security_group.app.id
  description       = "VPC DNS resolver for responses above 512 bytes"
  ip_protocol       = "tcp"
  from_port         = 53
  to_port           = 53
  cidr_ipv4         = aws_vpc.main.cidr_block
}

resource "aws_vpc_security_group_egress_rule" "app_ntp" {
  security_group_id = aws_security_group.app.id
  description       = "Amazon Time Sync Service"
  ip_protocol       = "udp"
  from_port         = 123
  to_port           = 123
  cidr_ipv4         = "169.254.169.123/32"
}

resource "aws_vpc_security_group_egress_rule" "app_postgres" {
  security_group_id            = aws_security_group.app.id
  description                  = "PostgreSQL to RDS"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.database.id
}

resource "aws_security_group" "database" {
  name_prefix = "${local.name_prefix}-db-"
  description = "PostgreSQL access from the application security group only"
  vpc_id      = aws_vpc.main.id

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name    = "${local.name_prefix}-db-sg"
    Service = "database"
  }
}

# RDS never initiates outbound connections of its own, and security groups are
# stateful, so this group deliberately has no egress rule at all.
resource "aws_vpc_security_group_ingress_rule" "database_postgres" {
  security_group_id            = aws_security_group.database.id
  description                  = "PostgreSQL from the application instance"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.app.id
}
