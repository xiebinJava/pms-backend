#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATOR="$SCRIPT_DIR/validate-production-config.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

expect_status() {
  local expected="$1" label="$2"; shift 2
  set +e
  env -i PATH="$PATH" "$@" >/tmp/pms-production-config-test.out 2>&1
  local actual=$?
  set -e
  if [[ "$actual" != "$expected" ]]; then
    sed -n '1,40p' /tmp/pms-production-config-test.out >&2 || true
    fail "$label (status=$actual, expected=$expected)"
  fi
}

base_env=(
  "PMS_DEPLOYMENT_ENV=production"
  "PMS_JWT_SECRET=01234567890123456789012345678901"
  "MYSQL_HOST=db.internal.example"
  "MYSQL_PORT=3306"
  "MYSQL_DB=pms"
  "MYSQL_USER=pms"
  "MYSQL_PASSWORD=synthetic-only-password"
  "PMS_CORS_ALLOWED_ORIGINS=https://pms.example.com"
  "PMS_PUBLIC_BASE_URL=https://pms.example.com"
  "PMS_PASSWORD_RESET_EXPOSE_TOKEN=false"
  "PMS_INVITATION_EXPOSE_TOKEN=false"
  "PMS_NOTIFICATION_STARTUP_CHECK=true"
  "PMS_MAIL_ENABLED=false"
)

expect_status 2 "missing production environment" bash "$VALIDATOR"
expect_status 2 "placeholder JWT is rejected" env "${base_env[@]}" PMS_JWT_SECRET='replace_with_random_secret_at_least_32_bytes' bash "$VALIDATOR"
expect_status 2 "invalid MySQL port is rejected" env "${base_env[@]}" MYSQL_PORT=not-a-port bash "$VALIDATOR"
expect_status 2 "wildcard CORS is rejected" env "${base_env[@]}" PMS_CORS_ALLOWED_ORIGINS='*' bash "$VALIDATOR"
expect_status 2 "empty HTTPS CORS host is rejected" env "${base_env[@]}" PMS_CORS_ALLOWED_ORIGINS='https://' bash "$VALIDATOR"
expect_status 2 "HTTP public URL is rejected" env "${base_env[@]}" PMS_PUBLIC_BASE_URL='http://pms.example.com' bash "$VALIDATOR"
expect_status 2 "incomplete SMTP is rejected" env "${base_env[@]}" PMS_MAIL_ENABLED=true PMS_MAIL_HOST=smtp.example.com bash "$VALIDATOR"
expect_status 2 "invalid SMTP port is rejected" env "${base_env[@]}" PMS_MAIL_ENABLED=true PMS_MAIL_HOST=smtp.example.com PMS_MAIL_PORT=not-a-port PMS_MAIL_USERNAME=ci PMS_MAIL_PASSWORD=synthetic-only-password PMS_MAIL_FROM=no-reply@example.com bash "$VALIDATOR"
expect_status 2 "required SMTP cannot be disabled" env "${base_env[@]}" bash "$VALIDATOR" --require-smtp
expect_status 2 "required object storage cannot use local storage" env "${base_env[@]}" bash "$VALIDATOR" --require-object-storage
expect_status 0 "complete synthetic production configuration" env "${base_env[@]}" bash "$VALIDATOR"
expect_status 2 "published default admin password is rejected" env "${base_env[@]}" PMS_BOOTSTRAP_ADMIN_PASSWORD='PmsAdmin123!' bash "$VALIDATOR"
expect_status 0 "complete synthetic SMTP and object storage configuration" env "${base_env[@]}" PMS_MAIL_ENABLED=true PMS_MAIL_HOST=smtp.example.com PMS_MAIL_PORT=587 PMS_MAIL_USERNAME=ci PMS_MAIL_PASSWORD=synthetic-only-password PMS_MAIL_FROM=no-reply@example.com PMS_STORAGE_TYPE=s3 PMS_S3_ENDPOINT=https://object-storage.example.com PMS_S3_REGION=cn-hangzhou PMS_S3_BUCKET=pms-uploads PMS_S3_ACCESS_KEY=synthetic-key PMS_S3_SECRET_KEY=synthetic-only-secret bash "$VALIDATOR" --require-smtp --require-object-storage

rm -f /tmp/pms-production-config-test.out
echo "Production configuration validator tests passed."
