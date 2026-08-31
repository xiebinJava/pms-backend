#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

usage() {
  cat <<'USAGE'
Usage: backup-oceanbase.sh

Create a compressed logical backup, SHA-256 checksum and metadata for the
configured OceanBase/MySQL database. Set PMS_BACKUP_DIR to change the output.
USAGE
}
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then usage; exit 0; fi
if [[ $# -ne 0 ]]; then usage >&2; exit 2; fi

DB_HOST="${OCEANBASE_HOST:-127.0.0.1}"
DB_PORT="${OCEANBASE_PORT:-2881}"
DB_NAME="${OCEANBASE_DATABASE:-brad_pms}"
DB_USER="${OCEANBASE_BACKUP_USER:-${OCEANBASE_USER:-pms_migrator}}"
if [[ -z "${OCEANBASE_PASSWORD:-}" ]]; then
  OCEANBASE_PASSWORD="${PMS_MIGRATOR_PASSWORD:-${PMS_APP_PASSWORD:-}}"
fi
if [[ -z "$OCEANBASE_PASSWORD" ]]; then
  echo "OCEANBASE_PASSWORD or PMS_MIGRATOR_PASSWORD is required" >&2
  exit 2
fi
case "$DB_NAME" in
  (*[!A-Za-z0-9_.-]*) echo "OCEANBASE_DATABASE contains unsupported characters" >&2; exit 2;;
esac

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
mysql_base=(--protocol=tcp --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --database="$DB_NAME" --batch --skip-column-names)
container_mysql() {
  MYSQL_PWD="$OCEANBASE_PASSWORD" docker run --rm --network host -e MYSQL_PWD mysql:8.4 mysql "${mysql_base[@]}" "$@"
}
sql() {
  if [[ "$SQL_CLIENT" == "container" ]]; then
    container_mysql --execute "$1"
  else
    MYSQL_PWD="$OCEANBASE_PASSWORD" "$SQL_CLIENT" "${mysql_base[@]}" --execute "$1"
  fi
}

if command -v mysqldump >/dev/null 2>&1; then
  dump_mode=host
elif command -v docker >/dev/null 2>&1; then
  dump_mode=container
else
  echo "mysqldump is required, or install Docker to use the mysql:8.4 tool image" >&2
  exit 2
fi

schema_version="$(sql "SELECT COALESCE(MAX(version), 'baseline') FROM pms_schema_migration_history;" 2>/dev/null || sql "SELECT COALESCE(MAX(version), 'baseline') FROM flyway_schema_history WHERE success=1;" 2>/dev/null || echo unknown)"
schema_version="$(printf '%s' "$schema_version" | tr -cd 'A-Za-z0-9._-')"
schema_version="${schema_version:-unknown}"
backup_dir="${PMS_BACKUP_DIR:-$PROJECT_DIR/backups}"
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
if command -v zstd >/dev/null 2>&1; then
  archive="$backup_dir/${DB_NAME}-${timestamp}-v${schema_version}.sql.zst"
  compressor=(zstd -T0 -q -o "$archive")
else
  archive="$backup_dir/${DB_NAME}-${timestamp}-v${schema_version}.sql.gz"
  compressor=(gzip -c)
fi
checksum_file="${archive}.sha256"
metadata_file="${archive}.meta"
cleanup_partial() { rm -f "$archive" "$checksum_file" "$metadata_file"; }
trap cleanup_partial ERR INT TERM

dump_options=(--single-transaction --skip-lock-tables --skip-add-locks --skip-add-drop-table --no-tablespaces --triggers --hex-blob)
if [[ "$dump_mode" == "host" ]]; then
  dump_cmd=(mysqldump --protocol=tcp --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" "${dump_options[@]}" "$DB_NAME")
  if [[ "$archive" == *.zst ]]; then
    MYSQL_PWD="$OCEANBASE_PASSWORD" "${dump_cmd[@]}" | "${compressor[@]}"
  else
    MYSQL_PWD="$OCEANBASE_PASSWORD" "${dump_cmd[@]}" | "${compressor[@]}" > "$archive"
  fi
else
  container_dump() {
    MYSQL_PWD="$OCEANBASE_PASSWORD" docker run --rm --network host -e MYSQL_PWD mysql:8.4 \
      mysqldump --protocol=tcp --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
      "${dump_options[@]}" "$DB_NAME"
  }
  if [[ "$archive" == *.zst ]]; then
    container_dump | "${compressor[@]}"
  else
    container_dump | "${compressor[@]}" > "$archive"
  fi
fi

if command -v sha256sum >/dev/null 2>&1; then
  (cd "$(dirname "$archive")" && sha256sum "$(basename "$archive")" > "$(basename "$checksum_file")")
else
  (cd "$(dirname "$archive")" && shasum -a 256 "$(basename "$archive")" > "$(basename "$checksum_file")")
fi

{
  printf 'database=%s\n' "$DB_NAME"
  printf 'schema_version=%s\n' "$schema_version"
  printf 'created_at_utc=%s\n' "$timestamp"
  printf 'backup_file=%s\n' "$(basename "$archive")"
  printf 'row_counts:\n'
  # information_schema table statistics are estimates for OceanBase/MySQL and
  # are not suitable as restore evidence. Query exact counts for every table
  # while keeping identifiers constrained to metadata results.
  while IFS= read -r table_name; do
    case "$table_name" in
      (*[!A-Za-z0-9_.-]*) echo "unsupported table identifier in metadata: $table_name" >&2; exit 1;;
    esac
    row_count="$(sql "SELECT COUNT(*) FROM \`$table_name\`;" | tr -d '[:space:]')"
    if ! [[ "$row_count" =~ ^[0-9]+$ ]]; then
      echo "could not read exact row count for table $table_name" >&2
      exit 1
    fi
    printf '%s=%s\n' "$table_name" "$row_count"
  done < <(sql "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name;")
} > "$metadata_file"
trap - ERR INT TERM
echo "OceanBase backup created: $archive"
echo "Checksum: $checksum_file"
echo "Metadata: $metadata_file"
