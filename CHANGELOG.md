# Changelog

## Unreleased

- Add `GET /workbench` so the home page loads my tasks, participating projects, and recent comments in one request.
- Add task detail: subtasks, per-task comments, and image/PDF attachments.
- Add in-app notifications for task assignment and comments, plus scoped global search across projects, tasks, milestones, and comments.
- Add a contributor-only MySQL 8 start path (`docker-compose.mysql.yml` + Flyway). Enterprise runtime stays on OceanBase / `brad_pms`.
- Add an opt-in OIDC / LDAP AuthProvider. Local email login stays the default; SSO only signs in invited accounts.
- Add optional S3-compatible attachment storage (MinIO or a cloud bucket). The default remains the local upload volume.
- Add an opt-in signed webhook for task assignment and comment events. Delivery failures do not roll back the inbox.
- Fan project comments out to followers, and write inbox events when a node is completed or rolled back.
- Expose `/actuator/prometheus` on the private management port and ship an optional localhost Prometheus/Grafana Compose overlay.
- Add a Helm chart for backend + frontend. It does not install OceanBase; ServiceMonitor and Ingress stay off by default.

## 1.0.0

- Single-tenant self-hosted backend on OceanBase (MySQL compatible mode).
- Email identity, RBAC, organization tree, project lifecycle, and audit log.
- Privacy denylist scan in CI. Demo accounts use fictional identities only.
