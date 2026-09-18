# Local Keycloak identity center

This directory provides the open-source Keycloak development identity center
for PMS and DSH. Keycloak owns users, passwords, MFA and OIDC sessions. PMS
and DSH are clients; neither application becomes an OAuth authorization server.

## Start

```bash
cp .env.example .env
docker compose up -d
```

Open `http://127.0.0.1:8180`, choose realm `pms`, and create a development
user whose email already exists in PMS. The first successful PMS login binds
the Keycloak `(issuer, subject)` to that pre-provisioned PMS user.

Configure the same variables in the PMS and DSH processes. The admin password
in `.env` is for local development only and must be replaced outside a local
machine.

The realm import intentionally contains no user credentials. Users belong in
Keycloak storage, not in source control.
