variable "aws_region" {
  description = "AWS region in which the production infrastructure is created."
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "Short folder name used in AWS resource names and tags."
  type        = string
  default     = "swimming"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.project_name))
    error_message = "project_name must start with a lowercase letter and contain only lowercase letters, digits, and hyphens."
  }
}

variable "environment" {
  description = "Deployment environment. It becomes the resource name prefix, so it matches infra/architecture.md (swimming-prod-*)."
  type        = string
  default     = "prod"

  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "environment must be staging or prod."
  }
}

variable "owner" {
  description = "Owner tag applied to managed resources."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "vpc_cidr must be a valid IPv4 CIDR block."
  }
}

# 10.0.11.0/24 and 10.0.12.0/24 are intentionally left unused. They are reserved
# for the private application subnets that arrive when Nginx and Spring Boot are
# split onto separate instances behind a NAT instance.
variable "public_subnet_cidrs" {
  description = "Two public subnet CIDRs. The EC2 instance is placed in the first subnet."
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) == 2 && alltrue([for cidr in var.public_subnet_cidrs : can(cidrnetmask(cidr))])
    error_message = "public_subnet_cidrs must contain exactly two valid IPv4 CIDR blocks."
  }
}

variable "private_db_subnet_cidrs" {
  description = "Two isolated private subnet CIDRs for the RDS subnet group."
  type        = list(string)
  default     = ["10.0.21.0/24", "10.0.22.0/24"]

  validation {
    condition     = length(var.private_db_subnet_cidrs) == 2 && alltrue([for cidr in var.private_db_subnet_cidrs : can(cidrnetmask(cidr))])
    error_message = "private_db_subnet_cidrs must contain exactly two valid IPv4 CIDR blocks."
  }
}

variable "ec2_instance_type" {
  description = "EC2 instance type for Nginx and the Spring Boot container."
  type        = string
  default     = "t3.small"
}

variable "ec2_root_volume_size" {
  description = "Encrypted gp3 root volume size in GiB."
  type        = number
  default     = 30

  validation {
    condition     = var.ec2_root_volume_size >= 20
    error_message = "ec2_root_volume_size must be at least 20 GiB."
  }
}

variable "docker_compose_version" {
  description = "Pinned Docker Compose plugin version installed by EC2 user data."
  type        = string
  default     = "v2.39.4"

  validation {
    condition     = can(regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$", var.docker_compose_version))
    error_message = "docker_compose_version must use a full version such as v2.39.4."
  }
}

variable "postgres_engine_version" {
  description = "RDS PostgreSQL engine version."
  type        = string
  default     = "17.5"
}

variable "postgres_parameter_group_family" {
  description = "RDS parameter group family matching postgres_engine_version."
  type        = string
  default     = "postgres17"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "Initial PostgreSQL database name. Flyway owns all tables inside it."
  type        = string
  default     = "swimming"

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9_]{0,63}$", var.db_name))
    error_message = "db_name must be a valid PostgreSQL database identifier."
  }
}

variable "db_master_username" {
  description = "RDS administrative username. Its password is managed by RDS in Secrets Manager."
  type        = string
  default     = "swimming_admin"

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9_]{0,15}$", var.db_master_username))
    error_message = "db_master_username must be 1-16 characters and start with a letter."
  }
}

variable "db_allocated_storage" {
  description = "Initial RDS gp3 storage in GiB."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Maximum RDS autoscaled storage in GiB."
  type        = number
  default     = 100
}

variable "db_backup_retention_days" {
  description = "Automated RDS backup retention period."
  type        = number
  default     = 7

  validation {
    condition     = var.db_backup_retention_days >= 1 && var.db_backup_retention_days <= 35
    error_message = "db_backup_retention_days must be between 1 and 35."
  }
}

variable "db_multi_az" {
  description = "Whether RDS uses a synchronous standby in another AZ."
  type        = bool
  default     = false
}

variable "alarm_email" {
  description = "Optional email address subscribed to infrastructure alarms. Leave null to create the topic without a subscription."
  type        = string
  default     = null
}

variable "web_bucket_name" {
  description = "Globally unique S3 bucket name for the frontend. Defaults to <project>-<environment>-web when null."
  type        = string
  default     = null
}

variable "web_domain_name" {
  description = "Frontend domain served by CloudFront, such as app.example.com. Leave null to skip certificate creation and serve only the CloudFront domain."
  type        = string
  default     = null
}

variable "attach_web_domain" {
  description = "Whether CloudFront serves web_domain_name with the ACM certificate. Set this to true only after the DNS validation record has been published and the certificate has been issued."
  type        = bool
  default     = false
}

variable "api_domain_name" {
  description = "Hostname the API is served on, such as swimming-api.kro.kr. It becomes the Nginx server_name and the certbot certificate name. Null serves the default server without TLS."
  type        = string
  default     = null
}

variable "media_bucket_name" {
  description = "Globally unique S3 bucket name for curated video and audio assets. Defaults to <project>-<environment>-media when null."
  type        = string
  default     = null
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the CI roles, as owner/name."
  type        = string
  default     = "joowojr/swimming"

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$", var.github_repository))
    error_message = "github_repository must be written as owner/name."
  }
}

# The state bucket is created outside Terraform, so its name is repeated here
# to grant the plan role access to it. It matches the backend block in versions.tf.
variable "tfstate_bucket_name" {
  description = "S3 bucket holding the Terraform state, matching the backend block in versions.tf."
  type        = string
  default     = "swimming-prod-tfstate-223910471789"
}

variable "tfstate_key" {
  description = "State object key inside tfstate_bucket_name, matching the backend block in versions.tf."
  type        = string
  default     = "prod/terraform.tfstate"
}
