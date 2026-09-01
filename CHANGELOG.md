# Changelog

## Unreleased

- Add `GET /workbench` so the home page loads my tasks, participating projects, and recent comments in one request.
- Add task detail: subtasks, per-task comments, and image/PDF attachments.
- Add in-app notifications for task assignment and comments, plus scoped global search.
- Add a contributor-only MySQL 8 start path (`docker-compose.mysql.yml` + Flyway). Enterprise runtime stays on OceanBase / `brad_pms`.

## 1.0.0

- Single-tenant self-hosted backend on OceanBase (MySQL compatible mode).
- Email identity, RBAC, organization tree, project lifecycle, and audit log.
- Privacy denylist scan in CI. Demo accounts use fictional identities only.
