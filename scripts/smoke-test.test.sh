#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$(mktemp -t pms-smoke-curl.XXXXXX)"
trap 'rm -f "$LOG_FILE"' EXIT

curl() {
  printf '%s\n' "$*" >> "$PMS_SMOKE_CURL_LOG"
  if [[ "$*" == */auth/login* ]]; then
    printf '{"code":200}\n'
  else
    printf '{"status":"UP"}\n'
  fi
}
export -f curl

run_case() {
  local identity="$1" expected_field="$2"
  : > "$LOG_FILE"
  PMS_SMOKE_CURL_LOG="$LOG_FILE" \
    PMS_SMOKE_USERNAME="$identity" PMS_SMOKE_PASSWORD=synthetic-password \
    bash "$SCRIPT_DIR/smoke-test.sh" >/dev/null
  grep -Fq "\"$expected_field\":\"$identity\"" "$LOG_FILE" || {
    echo "FAIL: smoke login should send $expected_field for $identity" >&2
    sed -n '1,20p' "$LOG_FILE" >&2
    exit 1
  }
}

run_case 'admin@pms.com' email
run_case 'admin' username
echo "Smoke test identity payload tests passed."
