#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'USAGE'
Usage: restore-mysql.sh --allow-empty-target /path/to/backup.sql.gz

Restore only into an existing empty database. The application database
pms is rejected unless PMS_ALLOW_PRODUCTION_RESTORE=true is explicit.
USAGE
}
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then usage; exit 0; fi
if [[ "${1:-}" != "--allow-empty-target" || $# -ne 2 ]]; then usage >&2; exit 2; fi
BACKUP_PATH="$2"

DB_HOST="${MYSQL_HOST:-127.0.0.1}"
DB_PORT="${MYSQL_PORT:-3306}"
DB_NAME="${MYSQL_DB:-pms_restore}"
DB_USER="${MYSQL_USER:-pms}"
if [[ -z "${MYSQL_PASSWORD:-}" ]]; then
  echo "MYSQL_PASSWORD is required" >&2
  exit 2
fi
if [[ "$DB_NAME" == "pms" && "${PMS_ALLOW_PRODUCTION_RESTORE:-false}" != "true" ]]; then
  echo "refusing to restore into pms without PMS_ALLOW_PRODUCTION_RESTORE=true" >&2
  exit 2
fi
case "$DB_NAME" in
  (*[!A-Za-z0-9_.-]*) echo "MYSQL_DB contains unsupported characters" >&2; exit 2;;
esac

"$SCRIPT_DIR/verify-backup.sh" "$BACKUP_PATH"

if command -v mysql >/dev/null 2>&1; then
  SQL_CLIENT=mysql
elif command -v docker >/dev/null 2>&1; then
  SQL_CLIENT=container
else
  echo "mysql client or Docker is required" >&2
  exit 2
fi
mysql_base=(--protocol=tcp --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --database="$DB_NAME" --batch --skip-column-names)
container_mysql() {
  MYSQL_PWD="$MYSQL_PASSWORD" docker run --rm --network host -e MYSQL_PWD mysql:8.4 mysql "${mysql_base[@]}" "$@"
}
sql() {
  if [[ "$SQL_CLIENT" == "container" ]]; then
    container_mysql --execute "$1"
  else
    MYSQL_PWD="$MYSQL_PASSWORD" "$SQL_CLIENT" "${mysql_base[@]}" --execute "$1"
  fi
}

table_count="$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();" | tr -d '[:space:]')"
if [[ "$table_count" != "0" ]]; then
  echo "target database $DB_NAME is not empty ($table_count tables); restore only supports an empty target" >&2
  exit 1
fi

case "$BACKUP_PATH" in
  (*.sql.gz) decompressor=(gzip -dc "$BACKUP_PATH");;
  (*.sql.zst)
    command -v zstd >/dev/null 2>&1 || { echo "zstd is required to restore this backup" >&2; exit 2; }
    decompressor=(zstd -dc "$BACKUP_PATH")
    ;;
  (*) echo "unsupported backup extension; expected .sql.gz or .sql.zst" >&2; exit 2;;
esac
if [[ "$SQL_CLIENT" == "container" ]]; then
  "${decompressor[@]}" | container_mysql
else
  MYSQL_PWD="$MYSQL_PASSWORD" "${decompressor[@]}" | MYSQL_PWD="$MYSQL_PASSWORD" "$SQL_CLIENT" "${mysql_base[@]}"
fi
echo "MySQL backup restored into empty database: $DB_NAME"
echo "Confirm flyway_schema_history reached the expected version before starting the application."
