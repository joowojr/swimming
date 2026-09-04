data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  # Subnet names follow architecture.md (swimming-prod-public-a, -db-a, -db-c)
  # by taking the letter from the availability zone that is actually selected.
  az_names    = slice(data.aws_availability_zones.available.names, 0, 2)
  az_suffixes = [for az in local.az_names : substr(az, -1, 1)]
}

resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr

  # Both flags are required before an interface VPC endpoint (the planned
  # Bedrock runtime endpoint) can use private DNS.
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name    = "${local.name_prefix}-vpc"
    Service = "network"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name    = "${local.name_prefix}-igw"
    Service = "network"
  }
}

resource "aws_subnet" "public" {
  count = 2

  vpc_id                  = aws_vpc.main.id
  availability_zone       = local.az_names[count.index]
  cidr_block              = var.public_subnet_cidrs[count.index]
  map_public_ip_on_launch = false

  tags = {
    Name    = "${local.name_prefix}-public-${local.az_suffixes[count.index]}"
    Service = "network"
    Tier    = "public"
  }
}

resource "aws_subnet" "private_db" {
  count = 2

  vpc_id                  = aws_vpc.main.id
  availability_zone       = local.az_names[count.index]
  cidr_block              = var.private_db_subnet_cidrs[count.index]
  map_public_ip_on_launch = false

  tags = {
    Name    = "${local.name_prefix}-db-${local.az_suffixes[count.index]}"
    Service = "database"
    Tier    = "private"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name    = "${local.name_prefix}-public-rt"
    Service = "network"
  }
}

resource "aws_route_table_association" "public" {
  count = 2

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private_db" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name    = "${local.name_prefix}-db-rt"
    Service = "database"
  }
}

resource "aws_route_table_association" "private_db" {
  count = 2

  subnet_id      = aws_subnet.private_db[count.index].id
  route_table_id = aws_route_table.private_db.id
}

# Gateway endpoints are free. ECR image layers are served from S3, so this keeps
# image pulls and CloudWatch Agent uploads on the AWS network instead of the
# internet gateway, and it stays correct once the application moves to a private
# subnet behind a NAT instance.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id]

  tags = {
    Name    = "${local.name_prefix}-s3-vpce"
    Service = "network"
  }
}
