#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPEC="${1:-$SCRIPT_DIR/../src/main/resources/openapi/pms-api.yaml}"
if [[ ! -f "$SPEC" ]]; then
  echo "OpenAPI file does not exist: $SPEC" >&2
  exit 2
fi

if command -v ruby >/dev/null 2>&1; then
  ruby - "$SPEC" <<'RUBY'
require "yaml"
spec = YAML.load_file(ARGV.fetch(0))
abort "openapi must be 3.x" unless spec.fetch("openapi").start_with?("3.")
paths = spec["paths"]
abort "paths missing" unless paths.is_a?(Hash) && !paths.empty?
expected = {
  "/auth/login" => ["post"], "/auth/providers" => ["get"],
  "/auth/oidc/start" => ["get"], "/auth/oidc/callback" => ["post"], "/auth/ldap/login" => ["post"],
  "/auth/refresh" => ["post"], "/auth/logout" => ["post"],
  "/auth/me" => ["get"], "/auth/password/change" => ["post"], "/auth/activate" => ["post"],
  "/auth/password-reset/request" => ["post"], "/auth/password-reset/confirm" => ["post"],
  "/users/search" => ["get"], "/workbench" => ["get"],
  "/notifications" => ["get"], "/notifications/unread-count" => ["get"],
  "/notifications/{id}/read" => ["post"], "/notifications/read-all" => ["post"],
  "/search" => ["get"],
  "/projects/page" => ["post"], "/projects" => ["post"], "/projects/{id}" => ["get", "put", "delete"],
  "/projects/stats" => ["get"], "/projects/{id}/terminate" => ["post"], "/projects/{id}/restore" => ["post"],
  "/projects/{projectId}/tasks" => ["get", "post"], "/tasks/{id}" => ["get", "put", "delete"],
  "/tasks/{id}/attachments" => ["post"], "/tasks/{id}/attachments/{attachmentId}" => ["get", "delete"],
  "/tasks/{id}/move" => ["put"],
  "/projects/{projectId}/nodes" => ["get"],
  "/projects/{projectId}/nodes/{nodeId}/complete" => ["post"],
  "/projects/{projectId}/nodes/{nodeId}/rollback" => ["post"],
  "/projects/{projectId}/nodes/{nodeId}/owner" => ["put"],
  "/projects/{projectId}/nodes/{nodeId}/schedule" => ["put"],
  "/projects/{projectId}/members" => ["get", "post"], "/projects/{projectId}/members/{memberId}" => ["delete"],
  "/projects/{projectId}/followers" => ["get"],
  "/projects/{projectId}/comments" => ["get", "post"], "/comments/{id}" => ["delete"],
  "/feedback/tickets" => ["get", "post"], "/feedback/tickets/assignees" => ["get"],
  "/feedback/tickets/{id}" => ["get", "patch"], "/feedback/tickets/{id}/reopen" => ["post"],
  "/projects/{projectId}/images" => ["post"], "/projects/{projectId}/images/{filename}" => ["get", "delete"],
  "/admin/org/tree" => ["get"], "/org/tree" => ["get"], "/admin/org" => ["post"],
  "/admin/org/{id}" => ["put", "delete"], "/admin/org/{id}/move" => ["put"], "/admin/org/{id}/history" => ["get"],
  "/admin/users" => ["get"], "/admin/users/invite" => ["post"], "/admin/users/page" => ["get"],
  "/admin/users/{id}/primary-position" => ["put"], "/admin/users/{id}/part-time-positions" => ["post"],
  "/admin/users/{id}/part-time-positions/{positionId}" => ["delete"],
  "/admin/users/{id}/roles/{roleId}" => ["post", "delete"], "/admin/users/{id}/disable" => ["post"],
  "/admin/roles" => ["get", "post"], "/admin/roles/{id}" => ["put", "delete"],
  "/admin/import/preview/organizations" => ["post"], "/admin/import/preview/users" => ["post"],
  "/admin/import/{jobId}/commit" => ["post"], "/admin/import/{jobId}/errors.csv" => ["get"],
  "/admin/import/template/organizations.csv" => ["get"], "/admin/import/template/users.csv" => ["get"],
  "/admin/audit" => ["get"],
  "/admin/audit/{id}" => ["get"],
  "/health" => ["get"], "/healthz" => ["get"], "/health/ready" => ["get"], "/health/live" => ["get"]
}
expected.each do |route, methods|
  abort "route missing: #{route}" unless paths.key?(route)
  methods.each { |method| abort "method missing: #{method.upcase} #{route}" unless paths[route].key?(method) }
end
abort "legacy import template route must not be documented" if paths.key?("/admin/import/preview/{type}")
abort "legacy milestone routes must not be documented" if paths.key?("/projects/{projectId}/milestones") || paths.key?("/projects/{projectId}/milestones/{id}")
puts "OpenAPI contract and route map are valid: #{ARGV.fetch(0)}"
RUBY
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
expected = {
    "/auth/login": {"post"}, "/auth/providers": {"get"},
    "/auth/oidc/start": {"get"}, "/auth/oidc/callback": {"post"}, "/auth/ldap/login": {"post"},
    "/auth/refresh": {"post"}, "/auth/logout": {"post"},
    "/auth/me": {"get"}, "/auth/password/change": {"post"}, "/auth/activate": {"post"},
    "/auth/password-reset/request": {"post"}, "/auth/password-reset/confirm": {"post"},
    "/users/search": {"get"}, "/workbench": {"get"},
    "/notifications": {"get"}, "/notifications/unread-count": {"get"},
    "/notifications/{id}/read": {"post"}, "/notifications/read-all": {"post"},
    "/search": {"get"},
    "/projects/page": {"post"}, "/projects": {"post"}, "/projects/{id}": {"get", "put", "delete"},
    "/projects/stats": {"get"}, "/projects/{id}/terminate": {"post"}, "/projects/{id}/restore": {"post"},
    "/projects/{projectId}/tasks": {"get", "post"}, "/tasks/{id}": {"get", "put", "delete"},
    "/tasks/{id}/attachments": {"post"}, "/tasks/{id}/attachments/{attachmentId}": {"get", "delete"},
    "/tasks/{id}/move": {"put"},
    "/projects/{projectId}/nodes": {"get"},
    "/projects/{projectId}/nodes/{nodeId}/complete": {"post"},
    "/projects/{projectId}/nodes/{nodeId}/rollback": {"post"},
    "/projects/{projectId}/nodes/{nodeId}/owner": {"put"},
    "/projects/{projectId}/nodes/{nodeId}/schedule": {"put"},
    "/projects/{projectId}/members": {"get", "post"}, "/projects/{projectId}/members/{memberId}": {"delete"},
    "/projects/{projectId}/followers": {"get"},
    "/projects/{projectId}/comments": {"get", "post"}, "/comments/{id}": {"delete"},
    "/feedback/tickets": {"get", "post"}, "/feedback/tickets/assignees": {"get"},
    "/feedback/tickets/{id}": {"get", "patch"}, "/feedback/tickets/{id}/reopen": {"post"},
    "/projects/{projectId}/images": {"post"}, "/projects/{projectId}/images/{filename}": {"get", "delete"},
    "/admin/org/tree": {"get"}, "/org/tree": {"get"}, "/admin/org": {"post"},
    "/admin/org/{id}": {"put", "delete"}, "/admin/org/{id}/move": {"put"}, "/admin/org/{id}/history": {"get"},
    "/admin/users": {"get"}, "/admin/users/invite": {"post"}, "/admin/users/page": {"get"},
    "/admin/users/{id}/primary-position": {"put"}, "/admin/users/{id}/part-time-positions": {"post"},
    "/admin/users/{id}/part-time-positions/{positionId}": {"delete"},
    "/admin/users/{id}/roles/{roleId}": {"post", "delete"}, "/admin/users/{id}/disable": {"post"},
    "/admin/roles": {"get", "post"}, "/admin/roles/{id}": {"put", "delete"},
    "/admin/import/preview/organizations": {"post"}, "/admin/import/preview/users": {"post"},
    "/admin/import/{jobId}/commit": {"post"}, "/admin/import/{jobId}/errors.csv": {"get"},
    "/admin/import/template/organizations.csv": {"get"}, "/admin/import/template/users.csv": {"get"},
    "/admin/audit": {"get"},
    "/admin/audit/{id}": {"get"},
    "/health": {"get"}, "/healthz": {"get"}, "/health/ready": {"get"}, "/health/live": {"get"},
}
for route, methods in expected.items():
    if route not in value["paths"]:
        raise SystemExit(f"route missing: {route}")
    missing = methods.difference(value["paths"][route])
    if missing:
        raise SystemExit(f"method missing: {','.join(sorted(missing))} {route}")
if "/admin/import/preview/{type}" in value["paths"]:
    raise SystemExit("legacy import template route must not be documented")
if "/projects/{projectId}/milestones" in value["paths"] or "/projects/{projectId}/milestones/{id}" in value["paths"]:
    raise SystemExit("legacy milestone routes must not be documented")
print(f"OpenAPI contract is valid: {sys.argv[1]}")
PY
else
  echo "ruby or python3 with PyYAML is required" >&2
  exit 2
fi
