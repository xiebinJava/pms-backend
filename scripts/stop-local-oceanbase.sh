#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RUN_DIR="${PMS_RUN_DIR:-$PROJECT_DIR/.run}"
PID_FILE="$RUN_DIR/pms-backend.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "PMS backend is not managed by this script"
  exit 0
fi

pid="$(tr -d '[:space:]' < "$PID_FILE")"
if ! [[ "$pid" =~ ^[0-9]+$ ]]; then
  echo "invalid PID file: $PID_FILE" >&2
  exit 2
fi
if ! kill -0 "$pid" 2>/dev/null; then
  rm -f "$PID_FILE"
  echo "PMS backend is already stopped"
  exit 0
fi
command_line="$(ps -p "$pid" -o command= 2>/dev/null || true)"
if [[ "$command_line" != *pms-backend* ]]; then
  rm -f "$PID_FILE"
  echo "refusing to stop PID $pid because it is not the PMS backend" >&2
  exit 2
fi

kill -TERM "$pid"
for _ in $(seq 1 20); do
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "PMS backend stopped (pid=$pid)"
    exit 0
  fi
  sleep 1
done

echo "PMS backend did not stop after 20 seconds (pid=$pid)" >&2
exit 1
