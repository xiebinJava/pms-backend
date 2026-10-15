#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
: "${PMS_E2E_RUN_ID:?PMS_E2E_RUN_ID is required}"
if [[ ! "$PMS_E2E_RUN_ID" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$ ]]; then
  echo "PMS_E2E_RUN_ID must be 3-64 safe characters" >&2
  exit 2
fi

RUN_DIR="${PMS_E2E_RUN_DIR_PATH:-$PROJECT_DIR/.run/ai-e2e-$PMS_E2E_RUN_ID}"
COMPOSE_FILE="$RUN_DIR/docker-compose.yml"
COMPOSE_PROJECT="pms-ai-e2e-$PMS_E2E_RUN_ID"
PID_FILE="$RUN_DIR/pms-backend.pid"

if [[ -f "$PID_FILE" ]]; then
  pid="$(tr -d '[:space:]' < "$PID_FILE")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    for _ in $(seq 1 20); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
  fi
fi

if [[ -f "$COMPOSE_FILE" ]] && command -v docker >/dev/null 2>&1; then
  PMS_E2E_DATABASE_NAME="pms_ai_e2e_${PMS_E2E_RUN_ID//-/_}" \
    PMS_E2E_MYSQL_PORT="${PMS_E2E_MYSQL_PORT:-13306}" \
    PMS_E2E_MYSQL_USER="cleanup" \
    PMS_E2E_MYSQL_PASSWORD="cleanup" \
    PMS_E2E_MYSQL_ROOT_PASSWORD="cleanup" \
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" down -v --remove-orphans
fi

rm -rf "$RUN_DIR"
echo "isolated PMS E2E stack removed for run $PMS_E2E_RUN_ID"
