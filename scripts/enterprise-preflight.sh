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

echo "Checking PMS database $DB_HOST:$DB_PORT/$DB_NAME (read-only)"
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
assert_zero "active organization paths are structurally valid" \
  "SELECT COUNT(*) FROM sys_org_unit WHERE status='ACTIVE' AND (path IS NULL OR path NOT LIKE '/%/' OR path NOT LIKE CONCAT('%/', id, '/%'));"
echo "Enterprise preflight passed. No data was changed."
