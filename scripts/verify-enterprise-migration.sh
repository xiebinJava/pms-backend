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
TOOL_IMAGE="${PMS_MYSQL_TOOL_IMAGE:-mysql:8.4}"
TOOL_CLIENT="${PMS_MYSQL_TOOL_CLIENT:-mysql}"

if command -v mysql >/dev/null 2>&1; then
  SQL_CLIENT=mysql
elif command -v obclient >/dev/null 2>&1; then
  SQL_CLIENT=obclient
elif command -v docker >/dev/null 2>&1; then
  SQL_CLIENT=container
else
  echo "mysql/obclient client or Docker is required (MySQL 8 or OceanBase MySQL mode)" >&2
  exit 2
fi
if [[ -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "PMS_DB_USER/PMS_DB_PASSWORD (or MYSQL_/OCEANBASE_ equivalents) are required" >&2
  exit 2
fi

mysql_cmd=("$SQL_CLIENT" --protocol=tcp --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --database="$DB_NAME" --batch --skip-column-names --raw)
container_mysql() {
  MYSQL_PWD="$DB_PASSWORD" docker run --rm --network host -e MYSQL_PWD \
    --entrypoint "$TOOL_CLIENT" "$TOOL_IMAGE" "${mysql_cmd[@]:1}" "$@"
}
run_sql() {
  if [[ "$SQL_CLIENT" == "container" ]]; then
    container_mysql --execute "$1"
  else
    MYSQL_PWD="$DB_PASSWORD" "${mysql_cmd[@]}" --execute "$1"
  fi
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

required_tables=(sys_company_profile sys_org_unit_type sys_org_unit sys_position sys_user_position sys_role sys_permission sys_role_permission sys_user_role sys_role_org_scope sys_invitation sys_auth_session sys_password_reset_token sys_login_log sys_operation_log sys_import_job sys_org_unit_history)
for table_name in "${required_tables[@]}"; do
  count="$(run_sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='$table_name';" | tr -d '[:space:]')"
  if [[ "$count" != "1" ]]; then echo "FAIL: table $table_name is missing" >&2; exit 1; fi
done
echo "PASS: required enterprise tables"

for column_check in \
  "sys_user|name_zh" "sys_user|username_normalized" "sys_user|email_normalized" "sys_user|deleted" "sys_user|version" \
  "project|org_unit_id" "project|deleted" "project|version" \
  "user_notification|dedupe_key" \
  "project_node|deleted" "project_node|version" \
  "sys_operation_log|project_id" "sys_operation_log|reason" "sys_operation_log|result" \
  "sys_operation_log|ip" "sys_operation_log|user_agent"; do
  table_name="${column_check%%|*}"; column_name="${column_check##*|}"
  count="$(run_sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='$table_name' AND column_name='$column_name';" | tr -d '[:space:]')"
  if [[ "$count" != "1" ]]; then echo "FAIL: column $table_name.$column_name is missing" >&2; exit 1; fi
done
echo "PASS: required enterprise columns"

for index_name in uk_user_username_normalized uk_user_email_normalized idx_org_unit_parent_status idx_user_position_user_status idx_user_role_user_status idx_operation_log_resource idx_operation_log_project_created idx_operation_log_operator_created idx_operation_log_action_created idx_operation_log_result_created idx_project_org_unit idx_auth_session_user_status idx_login_log_user_created idx_login_log_retention uk_project_code uk_project_node_key idx_project_deleted idx_org_unit_history_org_created idx_org_unit_history_operator_created uk_user_notification_dedupe; do
  count="$(run_sql "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name='$index_name';" | tr -d '[:space:]')"
  if [[ "$count" == "0" ]]; then echo "FAIL: index $index_name is missing" >&2; exit 1; fi
done
echo "PASS: required enterprise indexes"

for foreign_key_check in \
  "project|project_org_unit_fk" "project_task|task_project_fk" "sys_user_position|user_position_user_fk" "sys_org_unit_history|org_unit_history_org_fk"; do
  table_name="${foreign_key_check%%|*}"; constraint_name="${foreign_key_check##*|}"
  count="$(run_sql "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='$table_name' AND constraint_name='$constraint_name' AND constraint_type='FOREIGN KEY';" | tr -d '[:space:]')"
  if [[ "$count" != "1" ]]; then echo "FAIL: foreign key $table_name.$constraint_name is missing" >&2; exit 1; fi
done
echo "PASS: integrity foreign keys"

assert_exact "exactly one active root organization is present" \
  "SELECT COUNT(*) FROM sys_org_unit WHERE parent_id IS NULL AND status='ACTIVE';" 1
assert_at_least "built-in RBAC roles are present" \
  "SELECT COUNT(*) FROM sys_role WHERE builtin=TRUE AND enabled=TRUE;" 7
assert_zero "projects without organization assignment" \
  "SELECT COUNT(*) FROM project WHERE org_unit_id IS NULL;"
echo "Enterprise migration verification passed. No data was changed."
