#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'USAGE'
Usage: verify-enterprise-migration.sh

Read-only post-migration checks for the PMS enterprise schema.
USAGE
  exit 0
fi

DB_HOST="${PMS_DB_HOST:-${MYSQL_HOST:-${OCEANBASE_HOST:-127.0.0.1}}}"
DB_PORT="${PMS_DB_PORT:-${MYSQL_PORT:-${OCEANBASE_PORT:-3306}}}"
DB_NAME="${PMS_DB_NAME:-${MYSQL_DATABASE:-${MYSQL_DB:-${OCEANBASE_DATABASE:-brad_pms}}}}"
DB_USER="${PMS_DB_USER:-${MYSQL_USER:-${OCEANBASE_USER:-}}}"
DB_PASSWORD="${PMS_DB_PASSWORD:-${MYSQL_PASSWORD:-${OCEANBASE_PASSWORD:-}}}"

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql client is required (MySQL 8 or OceanBase MySQL mode)" >&2
  exit 2
fi
if [[ -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "PMS_DB_USER/PMS_DB_PASSWORD (or MYSQL_/OCEANBASE_ equivalents) are required" >&2
  exit 2
fi

mysql_cmd=(mysql --protocol=tcp --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --database="$DB_NAME" --batch --skip-column-names --raw)
run_sql() {
  MYSQL_PWD="$DB_PASSWORD" "${mysql_cmd[@]}" --execute "$1"
}

assert_zero() {
  local label="$1" sql="$2" value
  value="$(run_sql "$sql" | tr -d '[:space:]')"
  if [[ "$value" != "0" ]]; then
    echo "FAIL: $label (count=$value)" >&2
    exit 1
  fi
  echo "PASS: $label"
}

assert_at_least() {
  local label="$1" sql="$2" minimum="$3" value
  value="$(run_sql "$sql" | tr -d '[:space:]')"
  if ! [[ "$value" =~ ^[0-9]+$ ]] || (( value < minimum )); then
    echo "FAIL: $label (count=$value, expected>=$minimum)" >&2
    exit 1
  fi
  echo "PASS: $label (count=$value)"
}

assert_exact() {
  local label="$1" sql="$2" expected="$3" value
  value="$(run_sql "$sql" | tr -d '[:space:]')"
  if [[ "$value" != "$expected" ]]; then
    echo "FAIL: $label (count=$value, expected=$expected)" >&2
    exit 1
  fi
  echo "PASS: $label"
}

required_tables=(sys_company_profile sys_org_unit_type sys_org_unit sys_position sys_user_position sys_role sys_permission sys_role_permission sys_user_role sys_role_org_scope sys_invitation sys_auth_session sys_password_reset_token sys_login_log sys_operation_log sys_import_job)
for table_name in "${required_tables[@]}"; do
  count="$(run_sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='$table_name';" | tr -d '[:space:]')"
  if [[ "$count" != "1" ]]; then echo "FAIL: table $table_name is missing" >&2; exit 1; fi
done
echo "PASS: required enterprise tables"

for column_check in \
  "sys_user|name_zh" "sys_user|username_normalized" "project|org_unit_id"; do
  table_name="${column_check%%|*}"; column_name="${column_check##*|}"
  count="$(run_sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='$table_name' AND column_name='$column_name';" | tr -d '[:space:]')"
  if [[ "$count" != "1" ]]; then echo "FAIL: column $table_name.$column_name is missing" >&2; exit 1; fi
done
echo "PASS: required enterprise columns"

for index_name in uk_user_username_normalized idx_org_unit_parent_status idx_user_position_user_status idx_user_role_user_status idx_operation_log_resource idx_project_org_unit; do
  count="$(run_sql "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name='$index_name';" | tr -d '[:space:]')"
  if [[ "$count" == "0" ]]; then echo "FAIL: index $index_name is missing" >&2; exit 1; fi
done
echo "PASS: required enterprise indexes"

assert_exact "exactly one active root organization is present" \
  "SELECT COUNT(*) FROM sys_org_unit WHERE parent_id IS NULL AND status='ACTIVE';" 1
assert_at_least "built-in RBAC roles are present" \
  "SELECT COUNT(*) FROM sys_role WHERE builtin=TRUE AND enabled=TRUE;" 7
assert_zero "projects without organization assignment" \
  "SELECT COUNT(*) FROM project WHERE org_unit_id IS NULL;"
echo "Enterprise migration verification passed. No data was changed."
