output "vpc_id" {
  description = "ID of the VPC."
  value       = aws_vpc.main.id
}

output "aws_region" {
  description = "AWS region used by this configuration."
  value       = var.aws_region
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = aws_subnet.public[*].id
}

output "private_db_subnet_ids" {
  description = "Isolated RDS subnet IDs."
  value       = aws_subnet.private_db[*].id
}

output "ec2_instance_id" {
  description = "EC2 instance ID used with SSM Session Manager and Run Command."
  value       = aws_instance.app.id
}

output "ec2_elastic_ip" {
  description = "Elastic IP assigned to the Nginx EC2 instance."
  value       = aws_eip.app.public_ip
}

output "rds_endpoint" {
  description = "Private RDS endpoint without credentials."
  value       = aws_db_instance.main.address
}

output "rds_port" {
  description = "RDS PostgreSQL port."
  value       = aws_db_instance.main.port
}

output "rds_master_secret_arn" {
  description = "RDS-managed administrative credential secret ARN. Do not use it as the application credential."
  value       = try(aws_db_instance.main.master_user_secret[0].secret_arn, null)
  sensitive   = true
}

output "backend_runtime_secret_arn" {
  description = "Secrets Manager container to populate with backend runtime environment values."
  value       = aws_secretsmanager_secret.backend_runtime.arn
}

output "backend_ecr_repository_url" {
  description = "ECR repository URL for immutable backend images."
  value       = aws_ecr_repository.backend.repository_url
}

output "alarm_topic_arn" {
  description = "SNS topic receiving infrastructure alarms."
  value       = aws_sns_topic.alarms.arn
}

output "web_bucket_name" {
  description = "Frontend S3 bucket. Upload the Vite build output here."
  value       = aws_s3_bucket.web.bucket
}

output "cloudfront_domain_name" {
  description = "CloudFront domain serving the frontend."
  value       = aws_cloudfront_distribution.web.domain_name
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID used for cache invalidation after a frontend release."
  value       = aws_cloudfront_distribution.web.id
}

output "acm_certificate_arn" {
  description = "us-east-1 certificate for the frontend domain. Null when web_domain_name is not set."
  value       = one(aws_acm_certificate.web[*].arn)
}

output "acm_validation_record" {
  description = "CNAME record to publish at the external DNS provider before setting attach_web_domain = true. Null when web_domain_name is not set."
  value       = local.acm_validation == null ? null : one(tolist(local.acm_validation))
}

output "media_bucket_name" {
  description = "S3 bucket for curated media. Store objects under the media/ prefix so /media/* resolves correctly."
  value       = aws_s3_bucket.media.bucket
}

output "github_deploy_role_arn" {
  description = "Role assumed by the backend and frontend deployment workflows. Set it as the AWS_DEPLOY_ROLE_ARN repository variable."
  value       = aws_iam_role.github_deploy.arn
}

output "github_terraform_role_arn" {
  description = "Read-only role assumed by the terraform plan workflow. Set it as the AWS_TERRAFORM_ROLE_ARN repository variable."
  value       = aws_iam_role.github_terraform.arn
}

output "page_renderer_function_name" {
  description = "Lambda function the backend invokes for the rendering fallback (KNOWLEDGE_FETCH_RENDER_FUNCTION_NAME)."
  value       = local.page_renderer_function_name
}

output "page_renderer_repository_name" {
  description = "ECR repository name used by the page renderer deployment workflow (PAGE_RENDERER_ECR_REPOSITORY)."
  value       = aws_ecr_repository.page_renderer.name
}

output "page_renderer_repository_url" {
  description = "Full ECR repository URL for the page renderer image."
  value       = aws_ecr_repository.page_renderer.repository_url
}
