#!/bin/bash
#
# Runs on the EC2 instance, delivered by the deploy-backend workflow through SSM
# Run Command. It lives in the repository rather than in EC2 user data because
# aws_instance.app sets user_data_replace_on_change = true: editing user data
# replaces the instance and destroys /etc/letsencrypt along with it.
#
# Usage: deploy-backend <image-uri>
set -euo pipefail

image="${1:?usage: deploy-backend <image-uri>}"

app_dir=/opt/swimming
compose_file="$app_dir/compose.production.yaml"
# docker compose reads this automatically because it sits next to the compose
# file, which is how BACKEND_IMAGE reaches the service definition.
env_file="$app_dir/.env"
backend_env="$app_dir/config/backend.env"
secret_arn_file="$app_dir/config/runtime-secret-arn"
health_url="http://127.0.0.1:8080/api/health"
health_timeout_sec=120

log() {
  printf '[deploy %s] %s\n' "$(date -u +%H:%M:%S)" "$*"
}

compose() {
  docker compose --project-directory "$app_dir" -f "$compose_file" "$@"
}

write_env() {
  umask 077
  printf 'BACKEND_IMAGE=%s\n' "$1" >"$env_file"
}

wait_for_health() {
  local deadline=$((SECONDS + health_timeout_sec))
  while ((SECONDS < deadline)); do
    if curl --fail --silent --show-error --max-time 5 "$health_url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
  done
  return 1
}

[[ -f "$compose_file" ]] || { echo "missing $compose_file" >&2; exit 1; }
[[ -f "$secret_arn_file" ]] || { echo "missing $secret_arn_file" >&2; exit 1; }

# The registry host encodes its own region, so the login region never drifts
# from the image being deployed.
registry="${image%%/*}"
registry_region="$(printf '%s' "$registry" | cut -d. -f4)"

log "logging in to $registry"
aws ecr get-login-password --region "$registry_region" \
  | docker login --username AWS --password-stdin "$registry"

log "rendering runtime environment from Secrets Manager"
"$app_dir/bin/render-backend-env" "$(cat "$secret_arn_file")" "$backend_env"

previous_image=""
if [[ -f "$env_file" ]]; then
  previous_image="$(sed -n 's/^BACKEND_IMAGE=//p' "$env_file")"
fi

log "pulling $image"
write_env "$image"
compose pull backend

log "starting backend"
compose up -d --remove-orphans

if wait_for_health; then
  log "healthy: $image"
  docker image prune --force >/dev/null
  exit 0
fi

log "health check failed after ${health_timeout_sec}s"
compose logs --tail 80 backend || true

if [[ -z "$previous_image" || "$previous_image" == "$image" ]]; then
  log "no previous image to roll back to; leaving the failed release in place for inspection"
  exit 1
fi

# The container rolls back, the database does not: migrations Flyway already
# applied stay applied. A release that fails after a destructive migration needs
# a restore from the RDS snapshot, not this path.
log "rolling back to $previous_image"
write_env "$previous_image"
compose up -d --remove-orphans

if wait_for_health; then
  log "rollback healthy: $previous_image"
else
  log "rollback did not become healthy either"
fi

exit 1
