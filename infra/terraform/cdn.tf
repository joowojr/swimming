locals {
  web_bucket_name = coalesce(var.web_bucket_name, "${local.name_prefix}-web")
  web_aliases     = var.attach_web_domain ? [var.web_domain_name] : []
}

resource "aws_s3_bucket" "web" {
  bucket = local.web_bucket_name

  tags = {
    Name    = local.web_bucket_name
    Service = "frontend"
  }
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket = aws_s3_bucket.web.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ACLs are disabled entirely; CloudFront reads through the bucket policy below.
resource "aws_s3_bucket_ownership_controls" "web" {
  bucket = aws_s3_bucket.web.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "web" {
  bucket = aws_s3_bucket.web.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }

    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_versioning" "web" {
  bucket = aws_s3_bucket.web.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "${local.name_prefix}-web-oac"
  description                       = "Signed access from CloudFront to the frontend bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_response_headers_policy" "security_headers" {
  name = "Managed-SecurityHeadersPolicy"
}

# The API must never be cached: responses are per-user and carry credentials.
# CachingDisabled also stops CloudFront from stripping Authorization and Cookie
# on the way to the origin, which the caching policies do.
data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

# Forwards every header, cookie, and query string to the origin, so JWTs and the
# refresh cookie reach Spring unchanged.
data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

# custom_error_response is distribution-wide and cannot be scoped to one cache
# behavior, so mapping 403/404 to index.html would rewrite the API's own 404 and
# 403 ProblemDetail responses into an HTML page with status 200. This function is
# attached to the default behavior only, leaving /api/* untouched.
resource "aws_cloudfront_function" "spa_router" {
  name    = "${local.name_prefix}-spa-router"
  runtime = "cloudfront-js-2.0"
  comment = "Rewrites extensionless paths to /index.html for client-side routing"
  publish = true

  code = <<-JS
    function handler(event) {
      var request = event.request;

      // A path with no file extension is a React Router route, not an object.
      // Real assets keep their own 403/404 instead of being masked by the shell.
      if (request.uri.indexOf('.') === -1) {
        request.uri = '/index.html';
      }

      return request;
    }
  JS
}

resource "aws_cloudfront_distribution" "web" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${local.name_prefix}-web-cdn"
  default_root_object = "index.html"
  aliases             = local.web_aliases
  price_class         = "PriceClass_200"

  origin {
    origin_id                = "s3-web"
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }

  # Serving the API from the same distribution makes the frontend and the API
  # one origin for the browser. That removes CORS and the cross-site cookie
  # problem entirely. The origin must be a domain name, not the Elastic IP, and
  # CloudFront validates its certificate - so the hostname still needs an A
  # record and enable-tls before this behavior can work.
  dynamic "origin" {
    for_each = var.api_domain_name == null ? [] : [var.api_domain_name]

    content {
      origin_id   = "api"
      domain_name = origin.value

      custom_origin_config {
        origin_protocol_policy = "https-only"
        http_port              = 80
        https_port             = 443
        origin_ssl_protocols   = ["TLSv1.2"]
      }
    }
  }

  origin {
    origin_id                = "media"
    domain_name              = aws_s3_bucket.media.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.media.id
  }

  # CloudFront forwards the request path to the origin unchanged, so /media/x.mp4
  # resolves to the key "media/x.mp4". Objects must be stored under that prefix.
  #
  # Unlike /api/*, this behavior caches: the files are identical for every viewer
  # and edge caching is what keeps video egress affordable. Byte-range requests
  # are handled by CloudFront, so seeking works.
  ordered_cache_behavior {
    path_pattern     = "/media/*"
    target_origin_id = "media"

    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = false

    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id
  }

  dynamic "ordered_cache_behavior" {
    for_each = var.api_domain_name == null ? [] : [1]

    content {
      path_pattern     = "/api/*"
      target_origin_id = "api"

      # An API must not redirect: a 301 on POST loses the request body.
      viewer_protocol_policy = "https-only"

      allowed_methods = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods  = ["GET", "HEAD"]

      cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
      origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
    }
  }

  default_cache_behavior {
    target_origin_id           = "s3-web"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_router.arn
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = var.attach_web_domain ? null : true
    acm_certificate_arn            = var.attach_web_domain ? one(aws_acm_certificate.web[*].arn) : null
    ssl_support_method             = var.attach_web_domain ? "sni-only" : null
    minimum_protocol_version       = var.attach_web_domain ? "TLSv1.2_2021" : null
  }

  lifecycle {
    precondition {
      condition     = !var.attach_web_domain || var.web_domain_name != null
      error_message = "attach_web_domain requires web_domain_name to be set."
    }
  }

  tags = {
    Name    = "${local.name_prefix}-web-cdn"
    Service = "frontend"
  }
}

data "aws_iam_policy_document" "web_bucket" {
  statement {
    sid     = "AllowCloudFrontOACRead"
    effect  = "Allow"
    actions = ["s3:GetObject"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    resources = ["${aws_s3_bucket.web.arn}/*"]

    # Restricts the grant to this one distribution rather than to CloudFront
    # as a whole.
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.web.arn]
    }
  }

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    resources = [
      aws_s3_bucket.web.arn,
      "${aws_s3_bucket.web.arn}/*"
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = data.aws_iam_policy_document.web_bucket.json

  depends_on = [aws_s3_bucket_public_access_block.web]
}
