#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPEC="${1:-$SCRIPT_DIR/../src/main/resources/openapi/pms-api.yaml}"
if [[ ! -f "$SPEC" ]]; then
  echo "OpenAPI file does not exist: $SPEC" >&2
  exit 2
fi

if command -v ruby >/dev/null 2>&1; then
  ruby -e 'require "yaml"; value = YAML.load_file(ARGV.fetch(0)); abort "openapi must be 3.x" unless value.fetch("openapi").start_with?("3."); abort "paths missing" unless value["paths"].is_a?(Hash) && !value["paths"].empty?; puts "OpenAPI contract is valid: #{ARGV.fetch(0)}"' "$SPEC"
elif command -v python3 >/dev/null 2>&1; then
  python3 - "$SPEC" <<'PY'
import sys
try:
    import yaml
except ImportError:
    raise SystemExit("ruby or python3 with PyYAML is required")
with open(sys.argv[1], encoding="utf-8") as handle:
    value = yaml.safe_load(handle)
if not str(value.get("openapi", "")).startswith("3.") or not value.get("paths"):
    raise SystemExit("invalid OpenAPI contract")
print(f"OpenAPI contract is valid: {sys.argv[1]}")
PY
else
  echo "ruby or python3 with PyYAML is required" >&2
  exit 2
fi
