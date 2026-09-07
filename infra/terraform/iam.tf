data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "${local.name_prefix}-ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json

  tags = {
    Name    = "${local.name_prefix}-ec2-role"
    Service = "backend"
  }
}

# AWS documents this managed policy as the standard instance-side permission set
# for Session Manager. It does not create or grant permissions to human IAM users.
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "ec2_runtime" {
  statement {
    sid    = "ReadBackendRuntimeSecret"
    effect = "Allow"
    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue"
    ]
    resources = [aws_secretsmanager_secret.backend_runtime.arn]
  }

  statement {
    sid    = "PullBackendImages"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer"
    ]
    resources = [aws_ecr_repository.backend.arn]
  }

  statement {
    sid     = "GetEcrAuthorizationToken"
    effect  = "Allow"
    actions = ["ecr:GetAuthorizationToken"]
    # AWS does not support repository-level resource scoping for this API.
    resources = ["*"]
  }

  statement {
    sid    = "RenderBlockedPages"
    effect = "Allow"
    # 일반 HTTP 클라이언트가 막히거나 본문이 비는 페이지만 브라우저로 다시 받는다.
    actions   = ["lambda:InvokeFunction"]
    resources = [aws_lambda_function.page_renderer.arn]
  }

  statement {
    sid    = "WriteApplicationLogs"
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:DescribeLogStreams",
      "logs:PutLogEvents"
    ]
    resources = ["${aws_cloudwatch_log_group.ec2.arn}:*"]
  }

  statement {
    sid     = "PublishHostMetrics"
    effect  = "Allow"
    actions = ["cloudwatch:PutMetricData"]
    # CloudWatch PutMetricData has no resource ARN; the namespace condition
    # below limits this permission to the application's metric namespace.
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = ["Swimming/EC2"]
    }
  }
}

resource "aws_iam_role_policy" "ec2_runtime" {
  name   = "${local.name_prefix}-ec2-runtime"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.ec2_runtime.json
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${local.name_prefix}-ec2-profile"
  role = aws_iam_role.ec2.name

  tags = {
    Name    = "${local.name_prefix}-ec2-profile"
    Service = "backend"
  }
}
