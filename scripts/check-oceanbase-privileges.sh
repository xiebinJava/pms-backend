#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${OCEANBASE_HOST:-127.0.0.1}"
DB_PORT="${OCEANBASE_PORT:-2881}"
DB_NAME="${OCEANBASE_DATABASE:-brad_pms}"
: "${PMS_APP_PASSWORD:?PMS_APP_PASSWORD is required}"
: "${PMS_MIGRATOR_PASSWORD:?PMS_MIGRATOR_PASSWORD is required}"
if [ -z "${OCEANBASE_ROOT_PASSWORD+x}" ]; then
  echo "OCEANBASE_ROOT_PASSWORD must be set explicitly for probe cleanup" >&2
  exit 2
fi

if command -v mysql >/dev/null 2>&1; then
  SQL_CLIENT=mysql
elif command -v obclient >/dev/null 2>&1; then
  SQL_CLIENT=obclient
else
  echo "mysql or obclient client is required" >&2
  exit 2
fi

mysql_base=("$SQL_CLIENT" --protocol=tcp --host="$DB_HOST" --port="$DB_PORT" --database="$DB_NAME" --batch --skip-column-names)
app_sql() { MYSQL_PWD="$PMS_APP_PASSWORD" "${mysql_base[@]}" --user=pms_app --execute "$1"; }
migrator_sql() { MYSQL_PWD="$PMS_MIGRATOR_PASSWORD" "${mysql_base[@]}" --user=pms_migrator --execute "$1"; }
root_sql() { MYSQL_PWD="$OCEANBASE_ROOT_PASSWORD" "${mysql_base[@]}" --user="${OCEANBASE_ROOT_USER:-root@sys}" --execute "$1"; }

if [[ "$(app_sql 'SELECT 1')" != "1" ]]; then
  echo "FAIL: pms_app cannot execute SELECT" >&2
  exit 1
fi
echo "PASS: pms_app can connect and query"

probe="pms_privilege_probe_$(date +%s)_$$"
root_sql "DROP TABLE IF EXISTS \`$probe\`" >/dev/null 2>&1 || true
cleanup() { root_sql "DROP TABLE IF EXISTS \`$probe\`" >/dev/null 2>&1 || true; }
trap cleanup EXIT
if app_sql "CREATE TABLE \`$probe\` (id INT PRIMARY KEY)" >/dev/null 2>&1; then
  echo "FAIL: pms_app unexpectedly has CREATE TABLE" >&2
  exit 1
fi
echo "PASS: pms_app cannot create tables"

if [[ "$(migrator_sql "CREATE TABLE \`$probe\` (id INT PRIMARY KEY); SELECT 1")" != "1" ]]; then
  echo "FAIL: pms_migrator cannot execute migration DDL" >&2
  exit 1
fi
echo "PASS: pms_migrator can execute migration DDL"
echo "OceanBase privilege separation passed. No persistent data was changed."
