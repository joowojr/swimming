# JavaScript로 본문을 그리는 페이지를 받아오는 헤드리스 브라우저.
#
# 백엔드 컨테이너 안에서 Chromium을 띄우려면 read_only 파일시스템과 cap_drop=ALL,
# 768MB 메모리 상한을 모두 되돌려야 한다. 렌더링 하나 때문에 애플리케이션 전체의 보안 경계를
# 무르는 대신 실행 위치를 여기로 옮겼다. 배경은
# docs/enhance/0907_렌더링_폴백_Lambda_분리.md 참고.
#
# 이 함수는 VPC 밖에 둔다. private subnet에 NAT가 없어 VPC 안에서는 외부 페이지를 가져올 수
# 없다.

resource "aws_ecr_repository" "page_renderer" {
  name                 = "${local.name_prefix}-page-renderer"
  image_tag_mutability = "IMMUTABLE"

  encryption_configuration {
    encryption_type = "AES256"
  }

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name    = "${local.name_prefix}-page-renderer"
    Service = "knowledge"
  }
}

resource "aws_ecr_lifecycle_policy" "page_renderer" {
  repository = aws_ecr_repository.page_renderer.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Remove untagged images after 7 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Keep the newest 10 tagged images"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["*"]
          countType      = "imageCountMoreThan"
          countNumber    = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_cloudwatch_log_group" "page_renderer" {
  name              = "/aws/lambda/${local.page_renderer_function_name}"
  retention_in_days = 30

  tags = {
    Name    = "${local.name_prefix}-page-renderer-logs"
    Service = "knowledge"
  }
}

data "aws_iam_policy_document" "page_renderer_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "page_renderer" {
  name               = "${local.name_prefix}-page-renderer-role"
  assume_role_policy = data.aws_iam_policy_document.page_renderer_assume.json

  tags = {
    Name    = "${local.name_prefix}-page-renderer-role"
    Service = "knowledge"
  }
}

# 함수가 하는 일은 로그를 남기는 것뿐이다. VPC도, S3도, DB도 건드리지 않는다.
data "aws_iam_policy_document" "page_renderer_logs" {
  statement {
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.page_renderer.arn}:*"]
  }
}

resource "aws_iam_role_policy" "page_renderer_logs" {
  name   = "${local.name_prefix}-page-renderer-logs"
  role   = aws_iam_role.page_renderer.id
  policy = data.aws_iam_policy_document.page_renderer_logs.json
}

resource "aws_lambda_function" "page_renderer" {
  function_name = local.page_renderer_function_name
  role          = aws_iam_role.page_renderer.arn

  package_type = "Image"
  image_uri    = "${aws_ecr_repository.page_renderer.repository_url}:${var.page_renderer_image_tag}"

  # Lambda는 manifest list(멀티 아키텍처) 이미지를 거부한다. 빌드도 단일 아키텍처로 고정한다.
  architectures = ["x86_64"]

  # 콜드 스타트에 Chromium 기동이 2~5초. 백엔드는 25초에 포기하고, 렌더 자체는 20초로 끊는다.
  timeout = 30

  # 2,048MB 운영 환경에서는 Chromium page 생성 전에 30초 timeout이 발생했다.
  # 이 계정의 초기 Lambda 메모리 상한인 3,008MB까지 높여 CPU 할당도 함께 늘린다.
  memory_size = 3008

  # 계정 동시 실행 한도가 10인 동안에는 예약 동시성을 설정할 수 없다. Lambda는 계정에
  # 최소 10개의 미예약 실행을 남기도록 강제하므로, quota를 올리기 전에는 계정 한도를 공유한다.

  depends_on = [
    aws_iam_role_policy.page_renderer_logs,
    aws_cloudwatch_log_group.page_renderer
  ]

  lifecycle {
    # 배포는 CI가 update-function-code로 한다. terraform이 태그를 되돌리지 않게 둔다.
    ignore_changes = [image_uri]
  }

  tags = {
    Name    = "${local.name_prefix}-page-renderer"
    Service = "knowledge"
  }
}
