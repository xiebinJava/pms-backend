#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 /path/to/pms-h2-snapshot.sql" >&2
  exit 2
fi

SNAPSHOT_PATH="$1"
if [[ ! -f "$SNAPSHOT_PATH" ]]; then
  echo "snapshot file does not exist: $SNAPSHOT_PATH" >&2
  exit 2
fi
: "${OCEANBASE_USER:?OCEANBASE_USER is required}"
: "${OCEANBASE_PASSWORD:?OCEANBASE_PASSWORD is required}"

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
  --mode validate \
  --snapshot "$SNAPSHOT_PATH"
