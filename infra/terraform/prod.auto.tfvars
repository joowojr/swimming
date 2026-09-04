aws_region   = "ap-northeast-2"
project_name = "swimming"
environment  = "prod"
owner        = "joowojr"

# Start with false for cost-conscious operation; enable when HA is required.
db_multi_az = false

# This account is on the AWS Free plan, which caps RDS backup retention.
# CreateDBInstance rejects the code default of 7 with FreeTierRestrictionError.
# Raise this after the account plan is upgraded.
db_backup_retention_days = 1

# Optional. AWS requires confirming the subscription email after apply.
alarm_email = null

# Frontend delivery. Set web_bucket_name only when the default name is taken.
web_bucket_name = null
web_domain_name = "swimming-now.kro.kr"

# Set to true only after the acm_validation_record CNAME is published and the
# certificate has been issued.
attach_web_domain = true

# API hostname. It is the Nginx server_name, the certbot certificate name, and
# the CloudFront origin for /api/*. Users never see it; the frontend calls /api
# on swimming-now.kro.kr. /api/* returns 502 until enable-tls has issued the
# certificate, because CloudFront validates the origin certificate.
api_domain_name = "api.swimming-now.kro.kr"
