#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: verify-oceanbase.sh

Run the non-destructive live OceanBase verification workflow. This performs
the schema upgrade twice (the second run proves idempotency) and then runs the
enterprise integrity preflight. Set PMS_OCEANBASE_VERIFY=true explicitly.

Migration credentials: OCEANBASE_USER and OCEANBASE_PASSWORD (or
PMS_MIGRATOR_PASSWORD). Preflight credentials: PMS_DB_USER and
PMS_DB_PASSWORD (or set OCEANBASE_USER/OCEANBASE_PASSWORD when the same
account is intentionally used for both checks).
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi
if [[ $# -ne 0 ]]; then
  usage >&2
  exit 2
fi
if [[ "${PMS_OCEANBASE_VERIFY:-false}" != "true" ]]; then
  echo "set PMS_OCEANBASE_VERIFY=true to run live OceanBase verification" >&2
  exit 2
fi

: "${OCEANBASE_PASSWORD:=${PMS_MIGRATOR_PASSWORD:-}}"
if [[ -z "${OCEANBASE_PASSWORD}" ]]; then
  echo "OCEANBASE_PASSWORD or PMS_MIGRATOR_PASSWORD is required" >&2
  exit 2
fi

# The integrity preflight may use a separate, read-only runtime account. If
# it is not provided, intentionally fall back to the migration account so a
# local installation can run one command without duplicating credentials.
export PMS_DB_USER="${PMS_DB_USER:-${OCEANBASE_USER:-pms_migrator}}"
export PMS_DB_PASSWORD="${PMS_DB_PASSWORD:-$OCEANBASE_PASSWORD}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Applying OceanBase migrations (idempotency pass 1)"
bash "$SCRIPT_DIR/oceanbase-upgrade.sh"
echo "Applying OceanBase migrations (idempotency pass 2)"
bash "$SCRIPT_DIR/oceanbase-upgrade.sh"
echo "Running post-migration schema verification"
bash "$SCRIPT_DIR/verify-enterprise-migration.sh"
echo "Running enterprise integrity preflight"
bash "$SCRIPT_DIR/enterprise-preflight.sh"

echo "Live OceanBase verification passed. No database or table was dropped."
