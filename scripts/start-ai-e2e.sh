#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

: "${PMS_E2E_RUN_ID:?PMS_E2E_RUN_ID is required}"
if [[ ! "$PMS_E2E_RUN_ID" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$ ]]; then
  echo "PMS_E2E_RUN_ID must be 3-64 safe characters" >&2
  exit 2
fi

: "${PMS_E2E_MYSQL_USER:?PMS_E2E_MYSQL_USER is required}"
: "${PMS_E2E_MYSQL_PASSWORD:?PMS_E2E_MYSQL_PASSWORD is required}"
: "${PMS_E2E_MYSQL_ROOT_PASSWORD:?PMS_E2E_MYSQL_ROOT_PASSWORD is required}"
: "${PMS_E2E_JWT_SECRET:?PMS_E2E_JWT_SECRET is required}"
: "${PMS_E2E_ADMIN_EMAIL:?PMS_E2E_ADMIN_EMAIL is required}"
: "${PMS_E2E_ADMIN_PASSWORD:?PMS_E2E_ADMIN_PASSWORD is required}"

if [[ "${#PMS_E2E_JWT_SECRET}" -lt 32 ]]; then
  echo "PMS_E2E_JWT_SECRET must contain at least 32 characters" >&2
  exit 2
fi
if [[ "${#PMS_E2E_ADMIN_PASSWORD}" -lt 12 ]]; then
  echo "PMS_E2E_ADMIN_PASSWORD must contain at least 12 characters" >&2
  exit 2
fi

RUN_DIR="${PMS_E2E_RUN_DIR_PATH:-$PROJECT_DIR/.run/ai-e2e-$PMS_E2E_RUN_ID}"
COMPOSE_FILE="$RUN_DIR/docker-compose.yml"
COMPOSE_PROJECT="pms-ai-e2e-$PMS_E2E_RUN_ID"
DATABASE_NAME="${PMS_E2E_DATABASE_NAME:-pms_ai_e2e_${PMS_E2E_RUN_ID//-/_}}"
MYSQL_PORT="${PMS_E2E_MYSQL_PORT:-13306}"
API_PORT="${PMS_E2E_API_PORT:-18080}"
MANAGEMENT_PORT="${PMS_E2E_MANAGEMENT_PORT:-$((API_PORT + 1))}"
JAR_PATH="${PMS_JAR_PATH:-}"
if [[ -z "$JAR_PATH" ]]; then
  JAR_PATH="$(ls -1t "$PROJECT_DIR"/target/pms-backend-*.jar 2>/dev/null | head -n 1 || true)"
fi
JAR_PATH="${JAR_PATH:-$PROJECT_DIR/target/pms-backend-1.2.0.jar}"
PID_FILE="$RUN_DIR/pms-backend.pid"
LOG_FILE="$RUN_DIR/pms-backend.log"
FOREGROUND="${PMS_E2E_FOREGROUND:-false}"

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "docker and docker compose are required" >&2
  exit 2
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 2
fi

mkdir -p "$RUN_DIR"
chmod 700 "$RUN_DIR"
umask 077
cat >"$COMPOSE_FILE" <<'YAML'
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: ${PMS_E2E_DATABASE_NAME}
      MYSQL_USER: ${PMS_E2E_MYSQL_USER}
      MYSQL_PASSWORD: ${PMS_E2E_MYSQL_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${PMS_E2E_MYSQL_ROOT_PASSWORD}
    ports:
      - "127.0.0.1:${PMS_E2E_MYSQL_PORT}:3306"
    volumes:
      - pms-ai-e2e-data:/var/lib/mysql
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p$$MYSQL_ROOT_PASSWORD --silent"]
      interval: 5s
      timeout: 5s
      retries: 30
      start_period: 15s

volumes:
  pms-ai-e2e-data:
    name: pms-ai-e2e-${PMS_E2E_RUN_ID}
YAML

compose() {
  PMS_E2E_DATABASE_NAME="$DATABASE_NAME" \
    PMS_E2E_MYSQL_PORT="$MYSQL_PORT" \
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" "$@"
}

compose up -d
mysql_ready=0
for _ in $(seq 1 60); do
  health="$(compose ps mysql --format '{{.Health}}' 2>/dev/null || true)"
  if [[ "$health" == "healthy" ]]; then
    mysql_ready=1
    break
  fi
  sleep 2
done
if [[ "$mysql_ready" -ne 1 ]]; then
  echo "isolated MySQL did not become healthy; inspect $RUN_DIR" >&2
  exit 1
fi

if [[ ! -f "$JAR_PATH" ]]; then
  if ! command -v mvn >/dev/null 2>&1; then
    echo "backend jar not found: $JAR_PATH" >&2
    exit 2
  fi
  (cd "$PROJECT_DIR" && mvn -q -DskipTests package)
  JAR_PATH="$(ls -1t "$PROJECT_DIR"/target/pms-backend-*.jar | head -n 1)"
fi

if [[ -f "$PID_FILE" ]]; then
  old_pid="$(tr -d '[:space:]' < "$PID_FILE")"
  if [[ "$old_pid" =~ ^[0-9]+$ ]] && kill -0 "$old_pid" 2>/dev/null; then
    echo "isolated PMS backend is already running (pid=$old_pid)"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

if [[ "$FOREGROUND" == "true" ]]; then
  env \
  MYSQL_HOST=127.0.0.1 \
  MYSQL_PORT="$MYSQL_PORT" \
  MYSQL_DB="$DATABASE_NAME" \
  MYSQL_USER="$PMS_E2E_MYSQL_USER" \
  MYSQL_PASSWORD="$PMS_E2E_MYSQL_PASSWORD" \
  PMS_JWT_SECRET="$PMS_E2E_JWT_SECRET" \
  PMS_BOOTSTRAP_ADMIN_EMAIL="$PMS_E2E_ADMIN_EMAIL" \
  PMS_BOOTSTRAP_ADMIN_PASSWORD="$PMS_E2E_ADMIN_PASSWORD" \
  PMS_BOOTSTRAP_ADMIN_USERNAME="${PMS_E2E_ADMIN_USERNAME:-admin}" \
  PMS_BOOTSTRAP_ADMIN_NAME_ZH="${PMS_E2E_ADMIN_NAME_ZH:-AI E2E 管理员}" \
  PMS_CORS_ALLOWED_ORIGINS="${PMS_E2E_AUTH_ORIGIN:-http://127.0.0.1:$API_PORT}" \
  PMS_DEPLOYMENT_ENV=development \
  PMS_NOTIFICATION_STARTUP_CHECK=false \
  PMS_PASSWORD_RESET_EXPOSE_TOKEN=true \
  PMS_INVITATION_EXPOSE_TOKEN=true \
  PMS_MANAGEMENT_PORT="$MANAGEMENT_PORT" \
  java -jar "$JAR_PATH" --spring.profiles.active=mysql --server.port="$API_PORT" \
  --spring.flyway.validate-on-migrate=false >>"$LOG_FILE" 2>&1 &
else
  nohup env \
  MYSQL_HOST=127.0.0.1 \
  MYSQL_PORT="$MYSQL_PORT" \
  MYSQL_DB="$DATABASE_NAME" \
  MYSQL_USER="$PMS_E2E_MYSQL_USER" \
  MYSQL_PASSWORD="$PMS_E2E_MYSQL_PASSWORD" \
  PMS_JWT_SECRET="$PMS_E2E_JWT_SECRET" \
  PMS_BOOTSTRAP_ADMIN_EMAIL="$PMS_E2E_ADMIN_EMAIL" \
  PMS_BOOTSTRAP_ADMIN_PASSWORD="$PMS_E2E_ADMIN_PASSWORD" \
  PMS_BOOTSTRAP_ADMIN_USERNAME="${PMS_E2E_ADMIN_USERNAME:-admin}" \
  PMS_BOOTSTRAP_ADMIN_NAME_ZH="${PMS_E2E_ADMIN_NAME_ZH:-AI E2E 管理员}" \
  PMS_CORS_ALLOWED_ORIGINS="${PMS_E2E_AUTH_ORIGIN:-http://127.0.0.1:$API_PORT}" \
  PMS_DEPLOYMENT_ENV=development \
  PMS_NOTIFICATION_STARTUP_CHECK=false \
  PMS_PASSWORD_RESET_EXPOSE_TOKEN=true \
  PMS_INVITATION_EXPOSE_TOKEN=true \
  PMS_MANAGEMENT_PORT="$MANAGEMENT_PORT" \
  java -jar "$JAR_PATH" --spring.profiles.active=mysql --server.port="$API_PORT" \
  --spring.flyway.validate-on-migrate=false >>"$LOG_FILE" 2>&1 &
fi
pid=$!
printf '%s\n' "$pid" > "$PID_FILE"

for _ in $(seq 1 90); do
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "isolated PMS backend exited; inspect $LOG_FILE" >&2
    rm -f "$PID_FILE"
    exit 1
  fi
  status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 \
    "http://127.0.0.1:${API_PORT}/api/auth/me" 2>/dev/null || true)"
  if [[ "$status" == "200" || "$status" == "401" ]]; then
    echo "PMS_E2E_BASE_URL=http://127.0.0.1:${API_PORT}/api"
    echo "PMS_E2E_AUTH_ORIGIN=http://127.0.0.1:${API_PORT}"
    echo "PMS_E2E_DATABASE_NAME=$DATABASE_NAME"
    echo "PMS_E2E_RUN_ID=$PMS_E2E_RUN_ID"
    echo "isolated PMS backend ready (HTTP $status)"
    if [[ "$FOREGROUND" == "true" ]]; then
      wait "$pid"
    fi
    exit 0
  fi
  sleep 1
done

echo "isolated PMS backend did not become ready; inspect $LOG_FILE" >&2
exit 1
