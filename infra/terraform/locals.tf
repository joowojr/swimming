locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # The repository half of the GitHub OIDC subject claim, which carries the
  # immutable ids alongside the names. Trust policies match on this rather than
  # on owner/name, because that is what the token actually contains.
  github_oidc_repository = format(
    "%s@%s/%s@%s",
    split("/", var.github_repository)[0],
    var.github_repository_owner_id,
    split("/", var.github_repository)[1],
    var.github_repository_id
  )

  common_tags = {
    Environment = var.environment
    Owner       = var.owner
    ManagedBy   = "terraform"
    Project     = var.project_name
  }
}
