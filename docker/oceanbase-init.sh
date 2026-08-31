#!/usr/bin/env sh
set -eu

# Compose uses the same versioned, checkpointed workflow as an existing
# installation. Keeping this wrapper avoids a second schema path that could
# drift from scripts/oceanbase-upgrade.sh.
: "${OCEANBASE_USER:?OCEANBASE_USER is required}"
: "${OCEANBASE_PASSWORD:?OCEANBASE_PASSWORD is required}"
: "${PMS_MIGRATIONS_DIR:=/migrations}"
export PMS_MIGRATIONS_DIR

exec /bin/bash /usr/local/bin/oceanbase-upgrade.sh
