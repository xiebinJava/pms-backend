#!/usr/bin/env sh
set -eu

: "${OCEANBASE_HOST:=oceanbase}"
: "${OCEANBASE_PORT:=2881}"
: "${OCEANBASE_DATABASE:=brad_pms}"
: "${OCEANBASE_ROOT_USER:=root@sys}"
: "${OCEANBASE_TENANT:=test}"
: "${PMS_APP_USERNAME:=pms_app@$OCEANBASE_TENANT}"
: "${PMS_MIGRATOR_USERNAME:=pms_migrator@$OCEANBASE_TENANT}"
if [ -z "${OCEANBASE_ROOT_PASSWORD+x}" ]; then
  echo "OCEANBASE_ROOT_PASSWORD must be set explicitly" >&2
  exit 2
fi
: "${PMS_APP_PASSWORD:?PMS_APP_PASSWORD is required}"
: "${PMS_MIGRATOR_PASSWORD:?PMS_MIGRATOR_PASSWORD is required}"

if command -v mysql >/dev/null 2>&1; then
  SQL_CLIENT=mysql
elif command -v obclient >/dev/null 2>&1; then
  SQL_CLIENT=obclient
else
  echo "mysql or obclient client is required" >&2
  exit 2
fi

if [ "${PMS_ALLOW_EMPTY_ACCOUNT_PASSWORDS:-false}" != "true" ]; then
  if [ -z "$OCEANBASE_ROOT_PASSWORD" ] || [ -z "$PMS_APP_PASSWORD" ] || [ -z "$PMS_MIGRATOR_PASSWORD" ]; then
    echo "database account passwords must be non-empty" >&2
    exit 2
  fi
fi
if [ "$PMS_APP_PASSWORD" = "$PMS_MIGRATOR_PASSWORD" ]; then
  echo "PMS_APP_PASSWORD and PMS_MIGRATOR_PASSWORD must be different" >&2
  exit 2
fi

validate_secret() {
  label="$1"
  value="$2"
  case "$value" in
    *[!A-Za-z0-9._+=/@-]*)
      echo "$label contains unsupported characters; use an openssl-generated hex secret" >&2
      exit 2
      ;;
  esac
}
validate_secret "PMS_APP_PASSWORD" "$PMS_APP_PASSWORD"
validate_secret "PMS_MIGRATOR_PASSWORD" "$PMS_MIGRATOR_PASSWORD"

case "$OCEANBASE_DATABASE" in
  (*[!A-Za-z0-9_.-]*) echo "OCEANBASE_DATABASE contains unsupported characters" >&2; exit 2;;
esac

run_root() {
  MYSQL_PWD="$OCEANBASE_ROOT_PASSWORD" "$SQL_CLIENT" \
    --protocol=tcp \
    --host="$OCEANBASE_HOST" \
    --port="$OCEANBASE_PORT" \
    --user="$OCEANBASE_ROOT_USER" \
    --batch --skip-column-names "$@"
}

echo "Waiting for OceanBase at ${OCEANBASE_HOST}:${OCEANBASE_PORT}"
attempt=0
until run_root --execute "SELECT 1" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo "OceanBase did not become ready within 120 seconds" >&2
    exit 1
  fi
  sleep 2
done

run_root --execute "CREATE DATABASE IF NOT EXISTS \`$OCEANBASE_DATABASE\` DEFAULT CHARACTER SET utf8mb4"

create_account() {
  username="$1"
  password="$2"
  existing="$(run_root --execute "SELECT COUNT(*) FROM mysql.user WHERE user='$username';" | tr -d '[:space:]')"
  if [ "$existing" = "0" ]; then
    run_root --execute "CREATE USER '$username'@'%' IDENTIFIED BY '$password'"
  elif [ "${PMS_ROTATE_ACCOUNT_PASSWORDS:-false}" = "true" ]; then
    run_root --execute "ALTER USER '$username'@'%' IDENTIFIED BY '$password'"
  fi
}

create_account "pms_app" "$PMS_APP_PASSWORD"
create_account "pms_migrator" "$PMS_MIGRATOR_PASSWORD"

run_root --execute "GRANT SELECT, INSERT, UPDATE, DELETE ON \`$OCEANBASE_DATABASE\`.* TO 'pms_app'@'%'"
run_root --execute "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, CREATE VIEW, TRIGGER ON \`$OCEANBASE_DATABASE\`.* TO 'pms_migrator'@'%'"

if ! MYSQL_PWD="$PMS_APP_PASSWORD" "$SQL_CLIENT" --protocol=tcp --host="$OCEANBASE_HOST" --port="$OCEANBASE_PORT" --user="$PMS_APP_USERNAME" --database="$OCEANBASE_DATABASE" --execute "SELECT 1" >/dev/null 2>&1; then
  echo "pms_app was created but the supplied password cannot authenticate; set PMS_ROTATE_ACCOUNT_PASSWORDS=true to rotate it" >&2
  exit 1
fi
if ! MYSQL_PWD="$PMS_MIGRATOR_PASSWORD" "$SQL_CLIENT" --protocol=tcp --host="$OCEANBASE_HOST" --port="$OCEANBASE_PORT" --user="$PMS_MIGRATOR_USERNAME" --database="$OCEANBASE_DATABASE" --execute "SELECT 1" >/dev/null 2>&1; then
  echo "pms_migrator was created but the supplied password cannot authenticate; set PMS_ROTATE_ACCOUNT_PASSWORDS=true to rotate it" >&2
  exit 1
fi

echo "OceanBase accounts ready: pms_app (runtime DML), pms_migrator (migration DDL)"
