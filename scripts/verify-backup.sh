#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: verify-backup.sh /path/to/brad_pms-<timestamp>.sql.gz

Verify the compression stream and the adjacent SHA-256 checksum file.
USAGE
}
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then usage; exit 0; fi
if [[ $# -ne 1 ]]; then usage >&2; exit 2; fi

BACKUP_PATH="$1"
CHECKSUM_PATH="${BACKUP_PATH}.sha256"
[[ -f "$BACKUP_PATH" ]] || { echo "backup file does not exist: $BACKUP_PATH" >&2; exit 2; }
[[ -f "$CHECKSUM_PATH" ]] || { echo "checksum file does not exist: $CHECKSUM_PATH" >&2; exit 2; }

case "$BACKUP_PATH" in
  (*.sql.gz) gzip -t "$BACKUP_PATH";;
  (*.sql.zst)
    command -v zstd >/dev/null 2>&1 || { echo "zstd is required to verify this backup" >&2; exit 2; }
    zstd -t "$BACKUP_PATH" >/dev/null
    ;;
  (*) echo "backup must end in .sql.gz or .sql.zst" >&2; exit 2;;
esac

if command -v sha256sum >/dev/null 2>&1; then
  (cd "$(dirname "$BACKUP_PATH")" && sha256sum -c "$(basename "$CHECKSUM_PATH")")
else
  expected="$(awk '{print $1}' "$CHECKSUM_PATH")"
  actual="$(shasum -a 256 "$BACKUP_PATH" | awk '{print $1}')"
  [[ "$expected" == "$actual" ]] || { echo "checksum mismatch" >&2; exit 1; }
fi
echo "Backup integrity verified: $BACKUP_PATH"
