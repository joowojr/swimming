# GitHub Actions exchanges its workflow OIDC token for AWS credentials through
# this provider. Nothing else in the account trusts it, so a role becomes
# assumable from CI only by naming this provider in its trust policy.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  # IAM still requires the field. AWS verifies GitHub's certificate against its
  # own trust store rather than this value, so it does not need rotating.
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]

  tags = {
    Name = "${local.name_prefix}-github-oidc"
  }
}

# ---------------------------------------------------------------------------
# Deploy role: builds and ships the application. It cannot change infrastructure.
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "github_deploy_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Only workflows running on main may deploy. A pull request from a fork
    # produces a different sub claim and is rejected before any AWS call.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${local.github_oidc_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "${local.name_prefix}-gha-deploy"
  description        = "Assumed by GitHub Actions to push images and release the frontend"
  assume_role_policy = data.aws_iam_policy_document.github_deploy_assume_role.json

  tags = {
    Name = "${local.name_prefix}-gha-deploy"
  }
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid    = "PushBackendImages"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart"
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

  # Deployment runs through Session Manager rather than SSH: port 22 stays
  # closed and no private key has to live in GitHub.
  statement {
    sid     = "RunDeployCommand"
    effect  = "Allow"
    actions = ["ssm:SendCommand"]
    resources = [
      aws_instance.app.arn,
      "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}::document/AWS-RunShellScript"
    ]
  }

  statement {
    sid    = "ReadDeployCommandResult"
    effect = "Allow"
    actions = [
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations"
    ]
    # Command invocation ARNs are generated per execution and cannot be
    # predicted here. SendCommand above is what limits the blast radius.
    resources = ["*"]
  }

  statement {
    sid    = "PublishFrontendBundle"
    effect = "Allow"
    actions = [
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject"
    ]
    resources = ["${aws_s3_bucket.web.arn}/*"]
  }

  statement {
    sid       = "ListFrontendBucket"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.web.arn]
  }

  statement {
    sid       = "InvalidateFrontendCache"
    effect    = "Allow"
    actions   = ["cloudfront:CreateInvalidation"]
    resources = [aws_cloudfront_distribution.web.arn]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "${local.name_prefix}-gha-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}

# ---------------------------------------------------------------------------
# Terraform role: reads the whole account to produce a plan. It never applies.
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "github_terraform_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Pull requests opened inside this repository. The plan workflow only runs
    # on pull_request, and a forked pull request carries the fork's ids in the
    # sub claim, so it is denied.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${local.github_oidc_repository}:pull_request"]
    }
  }
}

resource "aws_iam_role" "github_terraform" {
  name               = "${local.name_prefix}-gha-terraform"
  description        = "Assumed by GitHub Actions to run terraform plan. Read-only apart from the state lock."
  assume_role_policy = data.aws_iam_policy_document.github_terraform_assume_role.json

  tags = {
    Name = "${local.name_prefix}-gha-terraform"
  }
}

# A plan has to read every resource the configuration manages, which is close to
# account-wide read. It is kept apart from the deploy role precisely because of
# that: the role CI uses on every push stays narrow.
resource "aws_iam_role_policy_attachment" "github_terraform_read" {
  role       = aws_iam_role.github_terraform.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/ReadOnlyAccess"
}

data "aws_iam_policy_document" "github_terraform_state" {
  statement {
    sid       = "ReadStateBucket"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = ["arn:${data.aws_partition.current.partition}:s3:::${var.tfstate_bucket_name}"]
  }

  # Writes are limited to the lock file. use_lockfile = true makes terraform
  # create and remove <key>.tflock for the duration of a plan; the state object
  # itself is only ever read here.
  statement {
    sid    = "HoldStateLock"
    effect = "Allow"
    actions = [
      "s3:DeleteObject",
      "s3:PutObject"
    ]
    resources = ["arn:${data.aws_partition.current.partition}:s3:::${var.tfstate_bucket_name}/${var.tfstate_key}.tflock"]
  }

  statement {
    sid       = "ReadStateObject"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["arn:${data.aws_partition.current.partition}:s3:::${var.tfstate_bucket_name}/${var.tfstate_key}"]
  }
}

resource "aws_iam_role_policy" "github_terraform_state" {
  name   = "${local.name_prefix}-gha-terraform-state"
  role   = aws_iam_role.github_terraform.id
  policy = data.aws_iam_policy_document.github_terraform_state.json
}

data "aws_partition" "current" {}
