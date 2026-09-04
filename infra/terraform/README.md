# Swimming AWS infrastructure

This directory manages the infrastructure described in `infra/architecture.md`.
It deliberately does not manage application tables, Flyway migrations,
application end-user accounts, or AWS IAM users.

## Managed resources

- One VPC across two availability zones, plus a free S3 gateway endpoint
- Two public subnets and two isolated private RDS subnets
- One public EC2 instance with an Elastic IP, Nginx, Docker, Docker Compose,
  SSM Agent, and CloudWatch Agent
- One private encrypted PostgreSQL RDS instance with backups and deletion protection
- Security groups that expose only 80/443 inbound, restrict egress to the ports
  the host actually needs, and allow PostgreSQL only from EC2
- A private frontend S3 bucket served through CloudFront with an origin access
  control, and an optional us-east-1 ACM certificate
- An immutable, scan-on-push ECR backend repository
- An EC2 service role and instance profile; no IAM users
- SSM Session Manager support and a scheduled SSM Agent update association
- A Secrets Manager container for backend runtime values; no secret values are
  stored in Terraform configuration or state
- CloudWatch logs, EC2/RDS alarms, and an SNS alarm topic

The private database subnets intentionally have no internet route or NAT
Gateway. The EC2 instance is in a public subnet because the current deployment
checklist uses one EC2 host with an Elastic IP rather than an ALB.

## Explicitly outside this state

- DNS records at the external registrar, including the ACM validation record
- AWS account bootstrap and human IAM users
- PostgreSQL application/migration user creation and grants
- Tables, indexes, foreign keys, and Flyway SQL
- Application image deployment or automatic backend startup

Keeping application startup outside EC2 user data prevents an infrastructure
apply from running Flyway while a table-name migration is still in progress.

## Prerequisites

1. Terraform 1.7 or later.
2. AWS credentials supplied through an existing role/profile, not committed
   access keys.
3. Permission to manage the resources declared in this directory.
4. Terraform 1.10 or later, because the backend uses native S3 state locking
   (`use_lockfile`) instead of a DynamoDB lock table.

## Configure

Variable values live in `prod.auto.tfvars`, which is committed. Terraform loads
any `*.auto.tfvars` file without being told to, so there is nothing to copy or
create. It is committed rather than kept local because the plan workflow has to
produce the same diff CI sees as the one this machine sees; a variable that
exists only here shows up as a phantom change in CI. The file holds no secrets —
the database password and the JWT secret are in Secrets Manager and never pass
through a Terraform variable.

Review the VPC CIDRs against existing networks and
decide whether production needs `db_multi_az = true` before the first apply.
`10.0.11.0/24` and `10.0.12.0/24` are reserved for the private application
subnets described in `infra/architecture.md` section 12; do not reuse them.

The defaults select the current Amazon Linux 2023 x86_64 AMI dynamically and
use PostgreSQL 17. Confirm that the selected RDS engine and parameter group family
are available in the target region during planning.

## Validate and apply

```bash
terraform fmt -check -recursive
terraform init
terraform validate
terraform plan -out=tfplan
terraform apply tfplan
```

Never commit `tfplan`.

## State backend

State lives in `s3://swimming-prod-tfstate/prod/terraform.tfstate`
in `ap-northeast-2`. The bucket was created with the AWS CLI rather than
Terraform, because a backend cannot bootstrap the bucket holding its own state.
It has versioning, SSE-S3, a full public access block, a TLS-only bucket policy,
and a 90-day expiry for noncurrent versions.

Locking uses the S3 `use_lockfile` option, so there is no DynamoDB table to
manage. Do not delete the bucket or disable its versioning; state history is the
only recovery path if an apply corrupts state.

The bucket name deliberately carries no AWS account id, so this repository can
be made public without exposing one. If the name is taken in another account,
pick a different suffix and update `versions.tf`, `variables.tf`, and this file
together.

### Creating the bucket

```bash
BUCKET=swimming-prod-tfstate
REGION=ap-northeast-2

aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
  --create-bucket-configuration LocationConstraint="$REGION"

aws s3api put-bucket-versioning --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption --bucket "$BUCKET" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration \
  'BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true'

aws s3api put-bucket-lifecycle-configuration --bucket "$BUCKET" \
  --lifecycle-configuration \
  '{"Rules":[{"ID":"expire-noncurrent","Status":"Enabled","Filter":{},"NoncurrentVersionExpiration":{"NoncurrentDays":90}}]}'
```

Add the TLS-only bucket policy (`aws:SecureTransport` deny) as on the previous
bucket, then move the state:

```bash
terraform init -migrate-state
```

Terraform asks whether to copy the existing state to the new backend. Answer
`yes`. Keep the old bucket until a plan against the new backend comes back
clean, then empty and delete it.

## Populate the runtime secret

Terraform creates only the secret container. Populate it after the RDS
application account and external credentials have been prepared. The JSON keys
must match `backend/.env.example`:

```json
{
  "SPRING_PROFILES_ACTIVE": "prod",
  "DB_URL": "jdbc:postgresql://RDS_ENDPOINT:5432/swimming?sslmode=verify-full&sslrootcert=/application/rds-ca.pem",
  "DB_USERNAME": "APPLICATION_USER",
  "DB_PASSWORD": "REPLACE_ME",
  "JWT_SECRET": "REPLACE_ME",
  "ALLOWED_ORIGINS": "https://swimming-now.kro.kr",
  "GOOGLE_CLIENT_ID": "REPLACE_ME.apps.googleusercontent.com",
  "OPENAI_API_KEY": "REPLACE_ME",
  "PLACE_CDN_BASE_URL": "https://swimming-now.kro.kr",
  "AWS_REGION": "ap-northeast-2"
}
```

Store that JSON in a temporary file outside the repository, then run:

```bash
aws secretsmanager put-secret-value \
  --secret-id "$(terraform output -raw backend_runtime_secret_arn)" \
  --secret-string file://PATH_OUTSIDE_REPOSITORY/backend-runtime.json
```

Delete the temporary plaintext file securely after verifying the secret. Do not
use the RDS-managed administrative secret as the application credential.

## Connect through SSM

There is no port 22 ingress and no SSH key pair. After apply:

```bash
aws ssm start-session \
  --target "$(terraform output -raw ec2_instance_id)" \
  --region "$(terraform output -raw aws_region 2>/dev/null || echo ap-northeast-2)"
```

On the instance, render the secret as a root-readable environment file only
when deploying the backend:

```bash
sudo /opt/swimming/bin/render-backend-env \
  "$(sudo cat /opt/swimming/config/runtime-secret-arn)" \
  /opt/swimming/config/backend.env
```

The deployment process can then copy `compose.production.yaml` to
`/opt/swimming`, set `BACKEND_IMAGE` to an immutable ECR tag, authenticate to
ECR, and run Docker Compose. Those release actions are intentionally not part
of Terraform.

## API domain and TLS

Nginx boots serving plain HTTP only. The certificate cannot be issued from user
data because Let's Encrypt validates over HTTP-01, which requires the hostname
to already resolve to the Elastic IP that apply has just created.

Set `api_domain_name` before apply so Nginx uses it as `server_name`, publish an
A record pointing the hostname at `terraform output -raw ec2_elastic_ip`, then
issue the certificate once over SSM. The script takes the domain and the
Let's Encrypt contact address as arguments:

```bash
sudo /opt/swimming/bin/enable-tls swimming-api.kro.kr you@example.com
```

That script requests the certificate through the ACME webroot, rewrites
`/etc/nginx/conf.d/swimming.conf` with a TLS server block plus an HTTP redirect,
reloads Nginx, and enables the `certbot-renew.timer` unit that renews twice a
day and reloads Nginx on success. Certbot comes from the Amazon Linux 2023
repository, so it is patched by `dnf update` like any other package. Only the
built-in webroot plugin is used; the nginx plugin would rewrite the templated
site config.

`/.well-known/acme-challenge/` stays served over port 80 after TLS is enabled so
renewals keep working. Do not close port 80.

## Upload media assets

Curated video and audio live in a separate bucket, served through the same
distribution at `/media/*`. CloudFront passes the request path to the origin
unchanged, so an object must be stored under the `media/` prefix for
`/media/intro.mp4` to resolve.

Use the upload script rather than `aws s3 cp` by hand. It computes the content
hash, applies the cache headers, and prints the key to paste into the seed
migration.

```bash
infra/scripts/upload-media.sh ~/videos/lisbon.mp4 1_lisbon_1
# → cities/videos/places/1_lisbon_1.a1b2c3d4.mp4
```

### Keys carry a content hash

Object keys end in the first 8 hex characters of the file's sha256:

```
media/cities/videos/places/1_lisbon_1.a1b2c3d4.mp4   S3 object key
     cities/videos/places/1_lisbon_1.a1b2c3d4.mp4    places.background_asset_key
```

Replacing a video means uploading under a new key and updating
`background_asset_key` in `backend/src/main/resources/db/migration/R__seed_places.sql`.
No invalidation is needed - the new key was never cached anywhere.

This is not a stylistic choice. `immutable` tells browsers not to revalidate for
a year, so reusing a filename for new content leaves returning visitors playing
the old video with no way to force a refresh: `create-invalidation` clears the
CloudFront edge, never the copy already on the viewer's disk. Content-addressed
keys keep the promise `immutable` makes.

Delete the superseded object only after the old key has drained from edge and
browser caches. Nothing points at it once the migration ships, so there is no
rush.

### Cache headers

| Path | Cache-Control | Set by |
|------|---------------|--------|
| `/media/*` | `public,max-age=31536000,immutable` | the upload script, on the object |
| `/assets/*` (hashed build output) | `public,max-age=31536000,immutable` | the frontend release commands below |
| `/index.html` | `no-cache` | the frontend release commands below |
| `/api/*` | not cached | CloudFront `Managed-CachingDisabled` |

No Terraform change backs this. `Managed-CachingOptimized` honours the origin's
`Cache-Control` and only falls back to its own 24 hour default TTL when the
object carries no header - which is exactly the failure mode to avoid, because
an object with no `Cache-Control` also reaches the browser without one and gets
re-fetched on heuristics.

Verify after a release:

```bash
curl -sI "https://<distribution-domain>/media/cities/videos/places/<key>" \
  | grep -iE "cache-control|x-cache|age"
```

`x-cache: Hit from cloudfront` and a `cache-control` line both need to be there.
A missing `cache-control` means the object was uploaded without the header.

Do not put user uploads here. They need per-user authorization through
backend-issued presigned URLs, not a public CloudFront path.

## Frontend domain and certificate

The frontend domain is delegated to an external registrar, so Terraform cannot
publish the ACM validation record. `aws_acm_certificate_validation` is
intentionally absent; including it would block apply until it times out. Attach
the domain in three steps:

1. Set `web_domain_name` (currently `swimming-now.kro.kr`) and keep
   `attach_web_domain = false`, then apply. The certificate is created in
   `us-east-1` and CloudFront serves its default domain.
2. Publish the CNAME from `terraform output acm_validation_record` at the
   registrar and wait for the certificate to reach `ISSUED`. Leave the record in
   place afterwards; ACM reuses it for automatic renewal.
3. Set `attach_web_domain = true` and apply again to attach the alias.

Set `web_bucket_name` when the default `swimming-prod-web` name is already
taken; S3 bucket names are globally unique.

## Release the frontend

Cache headers differ by file type, so the upload runs in three passes. The rule
is simple: **a hashed filename may be cached forever, an unhashed one may not.**
Vite hashes everything under `assets/`, but files copied from `public/` keep
their names across builds. Giving those `immutable` means a replaced logo stays
stale in browser caches for a year, and an invalidation cannot undo it.

```bash
BUCKET=$(terraform output -raw web_bucket_name)
DIST=$(terraform output -raw cloudfront_distribution_id)

# 1. Everything, with a short cache. This is what public/ assets keep.
aws s3 sync frontend/dist "s3://$BUCKET/" --delete \
  --exclude ".DS_Store" --exclude "*/.DS_Store" \
  --cache-control "public,max-age=3600"

# 2. Hashed assets only, promoted to a permanent cache.
aws s3 cp "s3://$BUCKET/assets/" "s3://$BUCKET/assets/" --recursive \
  --metadata-directive REPLACE \
  --cache-control "public,max-age=31536000,immutable"

# 3. The entry point must always revalidate, or a release never reaches users.
aws s3 cp frontend/dist/index.html "s3://$BUCKET/index.html" \
  --cache-control "no-cache" --content-type "text/html; charset=utf-8"

# 4. Invalidate only the unhashed paths.
aws cloudfront create-invalidation --distribution-id "$DIST" \
  --paths "/index.html" "/logo.svg"
```

Pass 2 uses `cp --recursive` rather than another `sync`: sync skips unchanged
objects, so it would not rewrite their metadata.

Client-side routing is handled by the `swimming-prod-spa-router` CloudFront
function on the default cache behavior, which rewrites extensionless paths to
`/index.html`. `custom_error_response` is deliberately not used - it applies to
the whole distribution and would rewrite the API's own 404 and 403 responses
into an HTML page with status 200. A genuinely missing asset therefore returns
its real status code instead of the SPA shell. See
`docs/trouble-shooting/cloudfront-api-integration.md`.

## Destruction protection

RDS has both AWS deletion protection and Terraform `prevent_destroy`. Removing
or replacing it requires an explicit reviewed code change. A final snapshot is
also required. This is intentional; do not disable the controls merely to make
`terraform destroy` succeed.

## CI/CD

Four workflows live in `.github/workflows/`. None of them holds a long-lived AWS
key: each assumes a role through the GitHub OIDC provider created in
`github-oidc.tf`, and the trust policy pins the repository and the ref.

| Workflow | Trigger | What it changes | Role |
| --- | --- | --- | --- |
| `ci.yml` | pull request, push to main | nothing - Gradle build and the Vite/tsc build only | none |
| `deploy-backend.yml` | main touching `backend/**`, `compose.production.yaml`, the deploy script | ECR image, the container on EC2 | `swimming-prod-gha-deploy` |
| `deploy-frontend.yml` | main touching `frontend/**` | web bucket objects, CloudFront cache | `swimming-prod-gha-deploy` |
| `terraform.yml` | pull request touching `infra/terraform/**` | nothing - plan only, posted as a PR comment | `swimming-prod-gha-terraform` |

`terraform apply` stays a local operation. The plan role carries `ReadOnlyAccess`
and may only write the `.tflock` object, so a workflow cannot change
infrastructure even if it is edited to try.

### Repository variables

After `terraform apply`, publish the outputs as GitHub Actions *variables* (not
secrets - none of these values are confidential, and secrets are masked in logs
which makes failures harder to read):

```bash
cd infra/terraform
gh variable set AWS_DEPLOY_ROLE_ARN        --body "$(terraform output -raw github_deploy_role_arn)"
gh variable set AWS_TERRAFORM_ROLE_ARN     --body "$(terraform output -raw github_terraform_role_arn)"
gh variable set ECR_REPOSITORY             --body "$(terraform output -raw backend_ecr_repository_url | cut -d/ -f2-)"
gh variable set EC2_INSTANCE_ID            --body "$(terraform output -raw ec2_instance_id)"
gh variable set WEB_BUCKET_NAME            --body "$(terraform output -raw web_bucket_name)"
gh variable set CLOUDFRONT_DISTRIBUTION_ID --body "$(terraform output -raw cloudfront_distribution_id)"
```

### How a backend release reaches the instance

The workflow builds `<ecr-url>:<commit-sha>` - the repository is `IMMUTABLE`, so
a moving tag such as `latest` cannot be re-pushed, and the running image is
always traceable to a commit. It then sends `compose.production.yaml` and
`infra/scripts/deploy-backend.sh` to the instance over SSM Run Command and
executes the script. Port 22 stays closed and no key material is stored in
GitHub.

The script is delivered per release instead of being baked into EC2 user data
because `aws_instance.app` sets `user_data_replace_on_change = true`: editing
user data would replace the instance and destroy `/etc/letsencrypt` with it.

On the host the script renders `backend.env` from Secrets Manager, writes the
new tag to `/opt/swimming/.env`, runs `docker compose up -d`, and polls
`/api/health` for up to 120 seconds. A failed health check rolls the container
back to the previous tag. **Flyway migrations do not roll back** - a release
that fails after a destructive migration needs an RDS snapshot restore, not this
path.

The manual release commands documented above remain valid as the break-glass
path when GitHub Actions is unavailable.
