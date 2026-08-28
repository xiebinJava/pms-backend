#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATION_DIR="${PMS_MIGRATIONS_DIR:-$PROJECT_DIR/src/main/resources/db/migration}"
TOOL_IMAGE="${PMS_MYSQL_TOOL_IMAGE:-mysql:8.4}"

usage() {
  cat <<'USAGE'
Usage: oceanbase-upgrade.sh

Apply pending V*.sql migrations to an existing OceanBase/MySQL database.
The command is non-destructive: it never drops a database or truncates data.
USAGE
}
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then usage; exit 0; fi
if [[ $# -ne 0 ]]; then usage >&2; exit 2; fi

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
elif command -v docker >/dev/null 2>&1; then
  SQL_CLIENT=container
else
  echo "mysql/obclient client or Docker is required" >&2
  exit 2
fi

mysql_args=(--protocol=tcp --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --database="$DB_NAME" --batch --skip-column-names)
container_mysql() {
  MYSQL_PWD="$OCEANBASE_PASSWORD" docker run --rm --network host -e MYSQL_PWD "$TOOL_IMAGE" \
    mysql "${mysql_args[@]}" "$@"
}
run_mysql() {
  if [[ "$SQL_CLIENT" == "container" ]]; then
    container_mysql "$@" < /dev/null
  else
    MYSQL_PWD="$OCEANBASE_PASSWORD" "$SQL_CLIENT" "${mysql_args[@]}" "$@" < /dev/null
  fi
}
sql() { run_mysql --execute "$1"; }

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
sql "CREATE TABLE IF NOT EXISTS pms_schema_migration_step (
  version VARCHAR(50) NOT NULL,
  step_no INT NOT NULL,
  checksum CHAR(64) NOT NULL,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (version, step_no)
)"
sql "CREATE TABLE IF NOT EXISTS pms_schema_migration_lock (
  id TINYINT PRIMARY KEY,
  lock_name VARCHAR(64) NOT NULL DEFAULT 'pms-schema-upgrade',
  owner_token VARCHAR(128) NULL,
  locked_until TIMESTAMP NULL
)"

# Upgrade installations created by the first Task 2 implementation in place.
lock_column_count="$(sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_schema_migration_lock' AND column_name='lock_name';" | tr -d '[:space:]')"
if [[ "$lock_column_count" != "1" ]]; then
  sql "ALTER TABLE pms_schema_migration_lock ADD COLUMN lock_name VARCHAR(64) NOT NULL DEFAULT 'pms-schema-upgrade'"
fi
owner_column_count="$(sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_schema_migration_lock' AND column_name='owner_token';" | tr -d '[:space:]')"
if [[ "$owner_column_count" != "1" ]]; then
  sql "ALTER TABLE pms_schema_migration_lock ADD COLUMN owner_token VARCHAR(128) NULL"
fi
sql "INSERT INTO pms_schema_migration_lock (id, lock_name, owner_token, locked_until)
  VALUES (1, 'pms-schema-upgrade', NULL, NULL) ON DUPLICATE KEY UPDATE id=id"

OWNER_TOKEN="${PMS_MIGRATION_OWNER_TOKEN:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
acquired=0
for _ in $(seq 1 30); do
  result="$(sql "UPDATE pms_schema_migration_lock
    SET owner_token='$OWNER_TOKEN', lock_name='pms-schema-upgrade',
        locked_until=DATE_ADD(NOW(), INTERVAL $LOCK_MINUTES MINUTE)
    WHERE id=1 AND lock_name='pms-schema-upgrade'
      AND (owner_token IS NULL OR locked_until IS NULL OR locked_until < NOW());
    SELECT ROW_COUNT();" | tail -n 1 | tr -d '[:space:]')"
  if [[ "$result" == "1" ]]; then
    acquired=1
    break
  fi
  sleep 1
done
if [[ "$acquired" != "1" ]]; then
  echo "could not acquire named schema migration lock pms-schema-upgrade within 30 seconds" >&2
  exit 1
fi

release_lock() {
  sql "UPDATE pms_schema_migration_lock SET owner_token=NULL, locked_until=NULL
    WHERE id=1 AND lock_name='pms-schema-upgrade' AND owner_token='$OWNER_TOKEN'" >/dev/null 2>&1 || true
}
trap release_lock EXIT INT TERM

flyway_exists="$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history';" | tr -d '[:space:]')"
pending=0

apply_statement() {
  local version="$1" step_no="$2" checksum="$3" statement="$4" stored_step
  stored_step="$(sql "SELECT checksum FROM pms_schema_migration_step WHERE version='$version' AND step_no=$step_no;" | tr -d '[:space:]')"
  if [[ -n "$stored_step" ]]; then
    if [[ "$stored_step" != "$checksum" ]]; then
      echo "checksum mismatch for partially applied migration V${version}" >&2
      exit 1
    fi
    return 0
  fi
  run_mysql --execute "$statement"
  local escaped_version escaped_checksum
  escaped_version="${version//\'/\'\'}"
  escaped_checksum="${checksum//\'/\'\'}"
  sql "INSERT INTO pms_schema_migration_step (version, step_no, checksum) VALUES ('$escaped_version', $step_no, '$escaped_checksum')"
}

apply_migration_file() {
  local file="$1" version="$2" checksum="$3"
  local step_no=0 line buffer='' statement
  while IFS= read -r line || [[ -n "$line" ]]; do
    buffer+="$line"$'\n'
    if [[ "$line" == *";" ]]; then
      step_no=$((step_no + 1))
      statement="${buffer%$'\n'}"
      statement="${statement%;}"
      buffer=''
      apply_statement "$version" "$step_no" "$checksum" "$statement"
    fi
  done < "$file"
  if [[ -n "${buffer//[$' \t\r\n']/}" ]]; then
    step_no=$((step_no + 1))
    apply_statement "$version" "$step_no" "$checksum" "$buffer"
  fi
}

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
      if [[ "${PMS_ACCEPT_FLYWAY_BASELINE:-false}" != "true" ]]; then
        echo "V${version} exists in Flyway history without a SHA-256 checkpoint; verify the deployed script and set PMS_ACCEPT_FLYWAY_BASELINE=true to record it" >&2
        exit 1
      fi
      sql "INSERT INTO pms_schema_migration_history (version, description, checksum) VALUES ('$version', '$description', '$checksum')"
      echo "V${version} recorded from explicitly accepted Flyway baseline"
      pending=1
      continue
    fi
  fi

  echo "Applying V${version} (${description})"
  apply_migration_file "$file" "$version" "$checksum"
  sql "INSERT INTO pms_schema_migration_history (version, description, checksum) VALUES ('$version', '$description', '$checksum')"
  echo "V${version} applied"
  pending=1
done

if [[ "$pending" == "0" ]]; then
  echo "No pending migrations. All migration checksums verified."
else
  echo "OceanBase upgrade completed."
fi
echo "No database or table was dropped."
