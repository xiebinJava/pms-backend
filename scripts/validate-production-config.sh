#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: validate-production-config.sh [--require-smtp] [--require-object-storage]

Validate production environment settings without printing secret values. The
validator only checks process environment variables; it never connects to the
database or SMTP server and never changes application data.
USAGE
}

require_smtp=false
require_object_storage=false
for arg in "$@"; do
  case "$arg" in
    --require-smtp) require_smtp=true ;;
    --require-object-storage) require_object_storage=true ;;
    --help|-h) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

fail() {
  echo "FAIL: $1" >&2
  exit 2
}

require_var() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required"
}

require_port() {
  local name="$1" value numeric
  value="${!name:-}"
  [[ "$value" =~ ^[0-9]+$ ]] || fail "$name must be a numeric TCP port"
  numeric=$((10#$value))
  (( numeric >= 1 && numeric <= 65535 )) || fail "$name must be between 1 and 65535"
}

require_https_endpoint() {
  local name="$1" value host
  value="${!name:-}"
  require_var "$name"
  [[ "$value" == https://* ]] || fail "$name must use https://"
  host="${value#https://}"
  [[ -n "$host" && "$host" != */* && "$host" != *[[:space:]]* ]] || fail "$name must contain a valid HTTPS host"
  unset host
}

require_exact() {
  local name="$1" expected="$2" actual="${!1:-}"
  [[ "$actual" == "$expected" ]] || fail "$name must be $expected"
}

require_false() {
  local name="$1"
  require_exact "$name" false
}

reject_placeholder() {
  local name="$1" value="${!1:-}"
  [[ "$value" != replace_with_* ]] || fail "$name still contains a placeholder"
}

[[ "${PMS_DEPLOYMENT_ENV:-}" == production ]] || fail "PMS_DEPLOYMENT_ENV must be production"

require_var PMS_JWT_SECRET
reject_placeholder PMS_JWT_SECRET
jwt_bytes="$(LC_ALL=C printf '%s' "$PMS_JWT_SECRET" | wc -c | tr -d '[:space:]')"
[[ "$jwt_bytes" =~ ^[0-9]+$ && jwt_bytes -ge 32 ]] || fail "PMS_JWT_SECRET must contain at least 32 bytes"
unset jwt_bytes

for name in MYSQL_HOST MYSQL_PORT MYSQL_DB MYSQL_USER MYSQL_PASSWORD; do
  require_var "$name"
done
require_port MYSQL_PORT
reject_placeholder MYSQL_PASSWORD
case "${MYSQL_DB}" in
  (*[!A-Za-z0-9_.-]*) fail "MYSQL_DB contains unsupported characters" ;;
esac
mysql_user_normalized="$(printf '%s' "$MYSQL_USER" | tr '[:upper:]' '[:lower:]')"
case "$mysql_user_normalized" in
  root) fail "MYSQL_USER must not be a root account" ;;
esac
unset mysql_user_normalized

require_var PMS_CORS_ALLOWED_ORIGINS
[[ "$PMS_CORS_ALLOWED_ORIGINS" != *'*'* ]] || fail "PMS_CORS_ALLOWED_ORIGINS must not contain *"
while IFS= read -r origin; do
  origin="${origin//[[:space:]]/}"
  [[ -n "$origin" ]] || fail "PMS_CORS_ALLOWED_ORIGINS contains an empty origin"
  require_https_endpoint origin
done < <(printf '%s\n' "$PMS_CORS_ALLOWED_ORIGINS" | tr ',' '\n')

require_var PMS_PUBLIC_BASE_URL
require_https_endpoint PMS_PUBLIC_BASE_URL

require_false PMS_PASSWORD_RESET_EXPOSE_TOKEN
require_false PMS_INVITATION_EXPOSE_TOKEN
require_exact PMS_NOTIFICATION_STARTUP_CHECK true

mail_enabled="${PMS_MAIL_ENABLED:-false}"
case "$mail_enabled" in
  true)
    for name in PMS_MAIL_HOST PMS_MAIL_PORT PMS_MAIL_USERNAME PMS_MAIL_PASSWORD PMS_MAIL_FROM; do
      require_var "$name"
    done
    require_port PMS_MAIL_PORT
    reject_placeholder PMS_MAIL_PASSWORD
    ;;
  false) ;;
  *) fail "PMS_MAIL_ENABLED must be true or false" ;;
esac
if [[ "$require_smtp" == true && "$mail_enabled" != true ]]; then
  fail "SMTP is required for this deployment but PMS_MAIL_ENABLED is not true"
fi

if [[ -n "${PMS_BOOTSTRAP_ADMIN_PASSWORD:-}" ]]; then
  reject_placeholder PMS_BOOTSTRAP_ADMIN_PASSWORD
  password_bytes="$(LC_ALL=C printf '%s' "$PMS_BOOTSTRAP_ADMIN_PASSWORD" | wc -c | tr -d '[:space:]')"
  [[ "$password_bytes" =~ ^[0-9]+$ && password_bytes -ge 12 ]] || fail "PMS_BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 bytes"
  # Published OSS first-login default — must not ship to production.
  if [[ "$PMS_BOOTSTRAP_ADMIN_PASSWORD" == "PmsAdmin123!" ]]; then
    fail "PMS_BOOTSTRAP_ADMIN_PASSWORD must not use the published default PmsAdmin123! in production"
  fi
  unset password_bytes
fi

storage_type="${PMS_STORAGE_TYPE:-local}"
case "$storage_type" in
  local) [[ "$require_object_storage" == false ]] || fail "object storage is required but PMS_STORAGE_TYPE is local" ;;
  s3)
    for name in PMS_S3_ENDPOINT PMS_S3_REGION PMS_S3_BUCKET PMS_S3_ACCESS_KEY PMS_S3_SECRET_KEY; do
      require_var "$name"
    done
    require_https_endpoint PMS_S3_ENDPOINT
    reject_placeholder PMS_S3_SECRET_KEY
    ;;
  *) fail "PMS_STORAGE_TYPE must be local or s3" ;;
esac

if [[ "${PMS_OIDC_ENABLED:-false}" == true ]]; then
  for name in PMS_OIDC_ISSUER PMS_OIDC_CLIENT_ID PMS_OIDC_CLIENT_SECRET; do require_var "$name"; done
  reject_placeholder PMS_OIDC_CLIENT_SECRET
fi
if [[ "${PMS_LDAP_ENABLED:-false}" == true ]]; then
  for name in PMS_LDAP_URL PMS_LDAP_BASE_DN PMS_LDAP_USER_SEARCH_FILTER PMS_LDAP_MANAGER_DN PMS_LDAP_MANAGER_PASSWORD; do require_var "$name"; done
  reject_placeholder PMS_LDAP_MANAGER_PASSWORD
fi

echo "Production configuration validation passed (secret values were not printed)."
