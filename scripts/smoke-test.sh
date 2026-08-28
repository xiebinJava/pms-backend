#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PMS_SMOKE_BASE_URL:-http://127.0.0.1:8080/api}"
curl_args=(--silent --show-error --fail --max-time 10)

echo "Checking liveness"
curl "${curl_args[@]}" "$BASE_URL/health/live" | grep -q '"status":"UP"'

echo "Checking readiness"
curl "${curl_args[@]}" "$BASE_URL/health/ready" | grep -q '"status":"UP"'

if [[ -n "${PMS_SMOKE_USERNAME:-}" && -n "${PMS_SMOKE_PASSWORD:-}" ]]; then
  echo "Checking login"
  curl "${curl_args[@]}" \
    -H 'Content-Type: application/json' \
    --data "$(printf '{\"username\":\"%s\",\"password\":\"%s\"}' "$PMS_SMOKE_USERNAME" "$PMS_SMOKE_PASSWORD")" \
    "$BASE_URL/auth/login" >/dev/null
fi

echo "PMS smoke test passed"
