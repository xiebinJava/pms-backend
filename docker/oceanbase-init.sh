#!/usr/bin/env sh
set -eu

: "${OCEANBASE_HOST:=oceanbase}"
: "${OCEANBASE_PORT:=2881}"
: "${OCEANBASE_DATABASE:=brad_pms}"
: "${OCEANBASE_USER:?OCEANBASE_USER is required}"
: "${OCEANBASE_PASSWORD:?OCEANBASE_PASSWORD is required}"

case "$OCEANBASE_DATABASE" in
  (*[!A-Za-z0-9_.-]*) echo "OCEANBASE_DATABASE contains unsupported characters" >&2; exit 2;;
esac

run_mysql() {
  MYSQL_PWD="$OCEANBASE_PASSWORD" mysql \
    --protocol=tcp \
    --host="$OCEANBASE_HOST" \
    --port="$OCEANBASE_PORT" \
    --user="$OCEANBASE_USER" \
    --batch --skip-column-names "$@"
}

echo "Waiting for OceanBase at ${OCEANBASE_HOST}:${OCEANBASE_PORT}"
attempt=0
until run_mysql --execute "SELECT 1" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo "OceanBase did not become ready within 120 seconds" >&2
    exit 1
  fi
  sleep 2
done

run_mysql --execute "CREATE DATABASE IF NOT EXISTS \`$OCEANBASE_DATABASE\` DEFAULT CHARACTER SET utf8mb4"
marker="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='pms_schema_bootstrap_marker'"
if [ "$(run_mysql --database="$OCEANBASE_DATABASE" --execute "$marker" | tr -d '[:space:]')" = "1" ]; then
  echo "PMS schema is already initialized"
  exit 0
fi

echo "Applying PMS schema and enterprise migrations"
run_mysql --database="$OCEANBASE_DATABASE" < /sql/schema.sql
run_mysql --database="$OCEANBASE_DATABASE" < /sql/V2__enterprise_identity_org_rbac.sql
run_mysql --database="$OCEANBASE_DATABASE" < /sql/V3__project_node_schedule.sql
run_mysql --database="$OCEANBASE_DATABASE" < /sql/V4__authentication_audit_indexes.sql
run_mysql --database="$OCEANBASE_DATABASE" --execute \
  "CREATE TABLE pms_schema_bootstrap_marker (id TINYINT PRIMARY KEY, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)"
run_mysql --database="$OCEANBASE_DATABASE" --execute \
  "INSERT INTO pms_schema_bootstrap_marker (id) VALUES (1)"
echo "PMS schema initialized"
