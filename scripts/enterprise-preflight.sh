#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'USAGE'
Usage: enterprise-preflight.sh

Read-only checks for an existing MySQL/OceanBase PMS database. Connection
settings are read from PMS_DB_* first, then MYSQL_*/OCEANBASE_* variables.
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

assert_empty() {
  local label="$1" sql="$2" value compact
  value="$(run_sql "$sql")"
  compact="$(printf '%s' "$value" | tr -d '[:space:]')"
  if [[ -n "$compact" ]]; then
    echo "FAIL: $label" >&2
    printf '%s\n' "$value" >&2
    exit 1
  fi
  echo "PASS: $label"
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

assert_exact() {
  local label="$1" sql="$2" expected="$3" value
  value="$(run_sql "$sql" | tr -d '[:space:]')"
  if [[ "$value" != "$expected" ]]; then
    echo "FAIL: $label (count=$value, expected=$expected)" >&2
    exit 1
  fi
  echo "PASS: $label"
}

assert_table() {
  local table_name="$1" count
  count="$(run_sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='$table_name';" | tr -d '[:space:]')"
  if [[ "$count" != "1" ]]; then
    echo "FAIL: required table $table_name is missing" >&2
    exit 1
  fi
  echo "PASS: required table $table_name"
}

assert_index() {
  local index_name="$1" count
  count="$(run_sql "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name='$index_name';" | tr -d '[:space:]')"
  if [[ "$count" == "0" ]]; then
    echo "FAIL: required index $index_name is missing" >&2
    exit 1
  fi
  echo "PASS: required index $index_name"
}

echo "Checking PMS database $DB_HOST:$DB_PORT/$DB_NAME (read-only)"
for table_name in sys_login_log sys_auth_session sys_password_reset_token sys_operation_log; do
  assert_table "$table_name"
done
for index_name in uk_user_username_normalized idx_auth_session_user_status idx_login_log_user_created; do
  assert_index "$index_name"
done
assert_empty "unique normalized English login names" \
  "SELECT username_normalized, COUNT(*) FROM sys_user GROUP BY username_normalized HAVING username_normalized IS NULL OR username_normalized='' OR COUNT(*)>1;"
assert_zero "user positions reference existing users and org units" \
  "SELECT COUNT(*) FROM sys_user_position p LEFT JOIN sys_user u ON u.id=p.user_id LEFT JOIN sys_org_unit o ON o.id=p.org_unit_id WHERE u.id IS NULL OR o.id IS NULL;"
assert_zero "user roles reference existing users and roles" \
  "SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_user u ON u.id=ur.user_id LEFT JOIN sys_role r ON r.id=ur.role_id WHERE u.id IS NULL OR r.id IS NULL;"
assert_zero "role permissions reference existing roles and permissions" \
  "SELECT COUNT(*) FROM sys_role_permission rp LEFT JOIN sys_role r ON r.id=rp.role_id LEFT JOIN sys_permission p ON p.id=rp.permission_id WHERE r.id IS NULL OR p.id IS NULL;"
assert_zero "custom role scopes reference existing roles and org units" \
  "SELECT COUNT(*) FROM sys_role_org_scope rs LEFT JOIN sys_role r ON r.id=rs.role_id LEFT JOIN sys_org_unit o ON o.id=rs.org_unit_id WHERE r.id IS NULL OR o.id IS NULL;"
assert_zero "projects reference existing organization units" \
  "SELECT COUNT(*) FROM project p LEFT JOIN sys_org_unit o ON o.id=p.org_unit_id WHERE p.org_unit_id IS NOT NULL AND o.id IS NULL;"
assert_zero "projects have an organization assignment" \
  "SELECT COUNT(*) FROM project WHERE org_unit_id IS NULL;"
assert_zero "active organization paths are structurally valid" \
  "SELECT COUNT(*) FROM sys_org_unit WHERE status='ACTIVE' AND (path IS NULL OR path NOT LIKE '/%/' OR path NOT LIKE CONCAT('%/', id, '/%'));"
assert_exact "exactly one active root organization is present" \
  "SELECT COUNT(*) FROM sys_org_unit WHERE parent_id IS NULL AND status='ACTIVE';" 1
echo "Enterprise preflight passed. No data was changed."
