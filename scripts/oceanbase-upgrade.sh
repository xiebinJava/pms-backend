#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATION_DIR="${PMS_MIGRATIONS_DIR:-$PROJECT_DIR/src/main/resources/db/migration}"

usage() {
  cat <<'USAGE'
Usage: oceanbase-upgrade.sh

Apply pending V*.sql migrations to an existing OceanBase/MySQL database.
The command is non-destructive: it never drops a database or truncates data.
USAGE
}
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then usage; exit 0; fi

DB_HOST="${OCEANBASE_HOST:-127.0.0.1}"
DB_PORT="${OCEANBASE_PORT:-2881}"
DB_NAME="${OCEANBASE_DATABASE:-brad_pms}"
DB_USER="${OCEANBASE_USER:-pms_migrator}"
if [[ -z "${OCEANBASE_PASSWORD:-}" ]]; then
  OCEANBASE_PASSWORD="${PMS_MIGRATOR_PASSWORD:-}"
fi
if [[ -z "$OCEANBASE_PASSWORD" ]]; then
  echo "OCEANBASE_PASSWORD or PMS_MIGRATOR_PASSWORD is required" >&2
  exit 2
fi

case "$DB_NAME" in
  (*[!A-Za-z0-9_.-]*) echo "OCEANBASE_DATABASE contains unsupported characters" >&2; exit 2;;
esac
if ! [[ "${PMS_MIGRATION_LOCK_MINUTES:-30}" =~ ^[1-9][0-9]*$ ]]; then
  echo "PMS_MIGRATION_LOCK_MINUTES must be a positive integer" >&2
  exit 2
fi
LOCK_MINUTES="${PMS_MIGRATION_LOCK_MINUTES:-30}"

if command -v mysql >/dev/null 2>&1; then
  SQL_CLIENT=mysql
elif command -v obclient >/dev/null 2>&1; then
  SQL_CLIENT=obclient
else
  echo "mysql or obclient client is required" >&2
  exit 2
fi

mysql_base=("$SQL_CLIENT" --protocol=tcp --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --database="$DB_NAME" --batch --skip-column-names)
sql() { MYSQL_PWD="$OCEANBASE_PASSWORD" "${mysql_base[@]}" --execute "$1"; }
sql_file() { MYSQL_PWD="$OCEANBASE_PASSWORD" "${mysql_base[@]}" < "$1"; }

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

if [[ ! -d "$MIGRATION_DIR" ]]; then
  echo "migration directory does not exist: $MIGRATION_DIR" >&2
  exit 2
fi

sql "CREATE TABLE IF NOT EXISTS pms_schema_migration_history (
  version VARCHAR(50) PRIMARY KEY,
  description VARCHAR(255) NOT NULL,
  checksum CHAR(64) NOT NULL,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)"
sql "CREATE TABLE IF NOT EXISTS pms_schema_migration_lock (
  id TINYINT PRIMARY KEY,
  locked_until TIMESTAMP NULL
)"
sql "INSERT INTO pms_schema_migration_lock (id, locked_until) VALUES (1, NULL) ON DUPLICATE KEY UPDATE id=id"

acquired=0
for _ in $(seq 1 30); do
  result="$(sql "UPDATE pms_schema_migration_lock SET locked_until=DATE_ADD(NOW(), INTERVAL $LOCK_MINUTES MINUTE) WHERE id=1 AND (locked_until IS NULL OR locked_until < NOW()); SELECT ROW_COUNT();" | tail -n 1 | tr -d '[:space:]')"
  if [[ "$result" == "1" ]]; then
    acquired=1
    break
  fi
  sleep 1
done
if [[ "$acquired" != "1" ]]; then
  echo "could not acquire schema migration lock within 30 seconds" >&2
  exit 1
fi

release_lock() {
  sql "UPDATE pms_schema_migration_lock SET locked_until=NULL WHERE id=1" >/dev/null 2>&1 || true
}
trap release_lock EXIT INT TERM

flyway_exists="$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history';" | tr -d '[:space:]')"

for file in "$MIGRATION_DIR"/V*.sql; do
  [[ -f "$file" ]] || continue
  filename="$(basename "$file")"
  version="${filename#V}"
  version="${version%%__*}"
  description="${filename#*__}"
  description="${description%.sql}"
  checksum="$(sha256_file "$file")"

  stored="$(sql "SELECT checksum FROM pms_schema_migration_history WHERE version='$version';" | tr -d '[:space:]')"
  if [[ -n "$stored" ]]; then
    if [[ "$stored" != "$checksum" ]]; then
      echo "checksum mismatch for migration V${version}" >&2
      exit 1
    fi
    echo "V${version} already applied (checksum verified)"
    continue
  fi

  if [[ "$flyway_exists" == "1" ]]; then
    flyway_applied="$(sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version='$version' AND success=1;" | tr -d '[:space:]')"
    if [[ "$flyway_applied" == "1" ]]; then
      sql "INSERT INTO pms_schema_migration_history (version, description, checksum) VALUES ('$version', '$description', '$checksum')"
      echo "V${version} recorded from existing Flyway history"
      continue
    fi
  fi

  echo "Applying V${version} (${description})"
  sql_file "$file"
  sql "INSERT INTO pms_schema_migration_history (version, description, checksum) VALUES ('$version', '$description', '$checksum')"
  echo "V${version} applied"
done

echo "OceanBase upgrade completed. No database or table was dropped."
