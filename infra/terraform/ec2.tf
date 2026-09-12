data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.amazon_linux_2023.id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  # The instance needs a public IPv4 at boot. An internet gateway only routes
  # traffic for instances that already have one, and aws_eip is created after
  # this resource reaches "running" - which is roughly when cloud-init starts
  # running user data. Without this, "dnf update -y" on line 6 of the bootstrap
  # script can fail and set -e aborts the whole bootstrap. The Elastic IP
  # replaces this address once it is associated, so the end state is unchanged.
  associate_public_ip_address = true
  monitoring                  = true

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
    # IMDSv2 응답이 Docker 네트워크의 추가 홉을 지나 backend 컨테이너까지 도달해야 한다.
    http_put_response_hop_limit = 2
    instance_metadata_tags      = "disabled"
  }

  root_block_device {
    encrypted   = true
    volume_type = "gp3"
    volume_size = var.ec2_root_volume_size
  }

  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/templates/ec2-user-data.sh.tftpl", {
    aws_region             = var.aws_region
    docker_compose_version = var.docker_compose_version
    log_group_name         = aws_cloudwatch_log_group.ec2.name
    runtime_secret_arn     = aws_secretsmanager_secret.backend_runtime.arn
    api_server_name        = coalesce(var.api_domain_name, "_")
  })

  depends_on = [
    aws_iam_role_policy_attachment.ssm_core,
    aws_iam_role_policy.ec2_runtime
  ]

  lifecycle {
    # data.aws_ami selects the newest Amazon Linux 2023 image, and a change to
    # ami forces replacement. Left unpinned, a routine apply months from now
    # would destroy the instance and with it the Let's Encrypt certificates in
    # /etc/letsencrypt, the rendered backend.env, and the pulled images.
    # Replacing the instance stays possible, but only as a deliberate act:
    # terraform apply -replace=aws_instance.app
    ignore_changes = [ami]
  }

  tags = {
    Name    = "${local.name_prefix}-app"
    Service = "backend"
  }
}

resource "aws_eip" "app" {
  domain   = "vpc"
  instance = aws_instance.app.id

  tags = {
    Name    = "${local.name_prefix}-app-eip"
    Service = "backend"
  }
}
