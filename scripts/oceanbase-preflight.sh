#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${OCEANBASE_USER:-}" && -z "${OCEANBASE_ADMIN_USER:-}" ]]; then
  echo "OCEANBASE_USER or OCEANBASE_ADMIN_USER is required" >&2
  exit 2
fi
if [[ -z "${OCEANBASE_PASSWORD:-}" && -z "${OCEANBASE_ADMIN_PASSWORD:-}" ]]; then
  echo "OCEANBASE_PASSWORD or OCEANBASE_ADMIN_PASSWORD is required" >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CLASSPATH_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pms-oceanbase-classpath.XXXXXX")"
trap 'rm -rf "$CLASSPATH_DIR"' EXIT

cd "$PROJECT_DIR"
mvn -q -DskipTests compile
mvn -q dependency:build-classpath \
  -Dmdep.outputFile="$CLASSPATH_DIR/runtime.classpath" \
  -Dmdep.includeScope=runtime

exec java -cp "target/classes:$(< "$CLASSPATH_DIR/runtime.classpath")" \
  com.brad.pms.migration.OceanbaseSqlImporter \
  --mode preflight \
  --schema "$PROJECT_DIR/src/main/resources/schema.sql"
