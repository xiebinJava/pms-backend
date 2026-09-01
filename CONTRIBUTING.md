# Contributing

PMS is a single-tenant, self-hosted project management system. Backend and frontend live in sibling repositories.

## Prerequisites

- Java 17 and Maven for `pms-backend`
- Node.js 22 and pnpm 9.15.9 for `pms-front`
- Contributor local runtime: MySQL 8 via `docker-compose.mysql.yml` and `./scripts/start-local-mysql.sh`
- Enterprise / drill runtime: OceanBase (MySQL compatible), database `brad_pms`
- H2 is test-only

## Checks before you open a pull request

```bash
# backend
./scripts/check-privacy.sh
./scripts/validate-openapi.sh
mvn -q test

# frontend
./scripts/check-privacy.sh
pnpm test
pnpm typecheck
pnpm build
```

## Rules

- Do not add company brand names, internal hostnames, or real employee identities. Demo users use `张伟` / `Alex.Zhang` / `alex.zhang@example.com`.
- Keep secrets, JWT keys, and database passwords out of Git.
- Backend permission checks stay on the server; frontend only hides controls.
- Match existing CQRS naming: Cmd / Qry / DTO / Service.
- Keep OIDC/LDAP behind `PMS_OIDC_ENABLED` / `PMS_LDAP_ENABLED`. Do not auto-create users from a directory.
- Keep object storage and webhooks behind `PMS_STORAGE_TYPE=s3` / `PMS_WEBHOOK_ENABLED`. Default local disk and no outbound events.
- Helm and the Prometheus/Grafana overlay are optional. Do not change the default Actuator bind (`PMS_MANAGEMENT_ADDRESS=127.0.0.1`). Chart values stay on `example.com` hosts and placeholder secrets.
