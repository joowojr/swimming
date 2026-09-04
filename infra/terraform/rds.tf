resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db-subnet-group"
  subnet_ids = aws_subnet.private_db[*].id

  tags = {
    Name    = "${local.name_prefix}-db-subnet-group"
    Service = "database"
  }
}

resource "aws_db_parameter_group" "postgres" {
  name_prefix = "${local.name_prefix}-postgres-"
  family      = var.postgres_parameter_group_family
  description = "Swimming PostgreSQL production parameters"

  # 문자셋은 DB 생성 시 UTF8 로 고정되므로 파라미터가 따로 없다.
  # MySQL 의 slow_query_log + long_query_time=1 에 대응한다. 단위는 밀리초.
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  # 모든 시각을 UTC 로 다룬다는 프로젝트 규칙을 DB 세션 기본값으로도 못박는다.
  # timezone 은 정적 파라미터라 RDS 가 pending-reboot 으로만 저장한다.
  # apply_method 를 생략하면 provider 가 immediate 를 보내 매 plan 마다
  # 같은 변경이 반복되는 영구 diff 가 생긴다.
  parameter {
    name         = "timezone"
    value        = "UTC"
    apply_method = "pending-reboot"
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name    = "${local.name_prefix}-postgres-parameters"
    Service = "database"
  }
}

resource "aws_db_instance" "main" {
  identifier = "${local.name_prefix}-db"

  engine         = "postgres"
  engine_version = var.postgres_engine_version
  instance_class = var.db_instance_class

  db_name                     = var.db_name
  username                    = var.db_master_username
  manage_master_user_password = true
  port                        = 5432

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  parameter_group_name   = aws_db_parameter_group.postgres.name
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  backup_retention_period = var.db_backup_retention_days
  backup_window           = "18:00-19:00"
  maintenance_window      = "sun:19:30-sun:20:30"

  auto_minor_version_upgrade = true
  apply_immediately          = false
  copy_tags_to_snapshot      = true
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${local.name_prefix}-db-final"

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  lifecycle {
    prevent_destroy = true

    precondition {
      condition     = var.db_max_allocated_storage >= var.db_allocated_storage
      error_message = "db_max_allocated_storage must be greater than or equal to db_allocated_storage."
    }
  }

  tags = {
    Name    = "${local.name_prefix}-db"
    Service = "database"
  }
}
