#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_DIR="${PMS_FRONTEND_DIR:-$BACKEND_DIR/../pms-front}"
CHECK_FRONTEND=true

if [[ "${1:-}" == "--backend-only" ]]; then
  CHECK_FRONTEND=false
fi

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

if [[ "$CHECK_FRONTEND" == true ]]; then
  [[ -d "$FRONTEND_DIR" ]] || fail "frontend directory does not exist: $FRONTEND_DIR"
fi

latest_migration="$({ find "$BACKEND_DIR/src/main/resources/db/migration" -maxdepth 1 -type f -name 'V*.sql' -exec basename {} \; 2>/dev/null || true; } \
  | sed -nE 's/^V([0-9]+)__.*\.sql$/\1/p' \
  | sort -n \
  | tail -n 1)"
[[ "$latest_migration" == "39" ]] || fail "expected current migration baseline V1–V39, found V${latest_migration:-unknown}"

readmes=("$BACKEND_DIR/README.md")
if [[ "$CHECK_FRONTEND" == true ]]; then
  readmes+=("$FRONTEND_DIR/README.md")
fi
for readme in "${readmes[@]}"; do
  [[ -f "$readme" ]] || fail "README does not exist: $readme"
  if grep -nE -- 'V1[–-]V11' "$readme" >/dev/null; then
    fail "README contains stale V1–V11 current-baseline wording: $readme"
  fi
done

openapi="$BACKEND_DIR/src/main/resources/openapi/pms-api.yaml"
for route in \
  '/feedback/tickets:' \
  '/feedback/tickets/assignees:' \
  '/feedback/tickets/{id}:' \
  '/feedback/tickets/{id}/reopen:'; do
  grep -F -- "$route" "$openapi" >/dev/null || fail "feedback route missing from OpenAPI contract: $route"
done

echo "Release consistency checks passed: migration=V1–V39, READMEs=current, feedback routes=documented"
