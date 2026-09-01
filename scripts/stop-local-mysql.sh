#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.mysql.yml"
RUN_DIR="${PMS_RUN_DIR:-$PROJECT_DIR/.run}"
PID_FILE="$RUN_DIR/pms-backend.pid"
STOP_COMPOSE=0

for arg in "$@"; do
  case "$arg" in
    --down)
      STOP_COMPOSE=1
      ;;
    *)
      echo "usage: $0 [--down]" >&2
      exit 2
      ;;
  esac
done

if [[ -f "$PID_FILE" ]]; then
  pid="$(tr -d '[:space:]' < "$PID_FILE")"
  if ! [[ "$pid" =~ ^[0-9]+$ ]]; then
    echo "invalid PID file: $PID_FILE" >&2
    exit 2
  fi
  if kill -0 "$pid" 2>/dev/null; then
    command_line="$(ps -p "$pid" -o command= 2>/dev/null || true)"
    if [[ "$command_line" != *pms-backend* ]]; then
      echo "refusing to stop PID $pid because it is not the PMS backend" >&2
      exit 2
    fi
    kill -TERM "$pid"
    stopped=0
    for _ in $(seq 1 20); do
      if ! kill -0 "$pid" 2>/dev/null; then
        rm -f "$PID_FILE"
        echo "PMS backend stopped (pid=$pid)"
        stopped=1
        break
      fi
      sleep 1
    done
    if [[ "$stopped" -ne 1 ]]; then
      echo "PMS backend did not stop after 20 seconds (pid=$pid)" >&2
      exit 1
    fi
  else
    rm -f "$PID_FILE"
    echo "PMS backend is already stopped"
  fi
else
  echo "PMS backend is not managed by this script"
fi

if [[ "$STOP_COMPOSE" -eq 1 ]]; then
  if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    echo "docker compose is required for --down" >&2
    exit 2
  fi
  compose_env=()
  if [[ -n "${PMS_ENV_FILE:-}" ]]; then
    compose_env=(--env-file "$PMS_ENV_FILE")
  elif [[ -f "$PROJECT_DIR/.env.mysql.local" ]]; then
    compose_env=(--env-file "$PROJECT_DIR/.env.mysql.local")
  fi
  docker compose -f "$COMPOSE_FILE" "${compose_env[@]}" down
  echo "MySQL 8 contributor stack stopped (volume kept)"
fi
