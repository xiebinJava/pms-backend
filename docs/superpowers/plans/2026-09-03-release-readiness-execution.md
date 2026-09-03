# Enterprise Release Readiness Execution Plan

> **For the implementation agent:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to execute this plan task-by-task with review checkpoints.

**Goal:** Bring the current PMS codebase to a verifiable enterprise-release baseline by aligning V1–V12 contracts and documentation, strengthening CI/production gates, exercising the local OceanBase migration/backup path, and recording any checks that require an external enterprise environment.

**Architecture:** Keep the existing single-enterprise deployment model and OceanBase runtime split (`pms_app` for application traffic, `pms_migrator` for schema changes). Reuse the existing migration, preflight, backup/restore, privacy, OpenAPI, and production-config scripts. This work must not change business behavior or copy secrets into source control.

**Tech Stack:** Spring Boot / Maven, Flyway SQL migrations, OceanBase MySQL mode, Vue 3 / Vite / TypeScript, pnpm, GitHub Actions, Playwright.

**Specification:** `docs/operations/release-checklist.md`, `docs/operations/enterprise-upgrade-runbook.md`, `docs/operations/oceanbase-backup-restore.md`, `docs/operations/infrastructure-status.md`, `docs/business-specification.md`, and the frontend `docs/user-manual.md`.

## Global Constraints

- Preserve the user's existing working-tree changes; do not reset, checkout, or overwrite unrelated work.
- Treat OceanBase as the only runtime database. H2 remains test-only and must not be enabled by production/compose runtime configuration.
- Never print, commit, or place credentials/tokens in generated reports, fixtures, logs, or documentation.
- Do not drop or rebuild `brad_pms`; any restore drill must target a uniquely named empty database and require an explicit safety flag.
- Do not claim production approval from local results. Mark external checks as pending until evidence is supplied by the target enterprise environment.
- Stay on the existing `main` working branches. No remote push is part of this execution unless explicitly requested in a later message.

## Tasks

### 1. Establish the current release contract and regression checks

**Files:**
- Create: `scripts/release-consistency.test.sh`
- Modify: `scripts/validate-openapi.sh` and the OpenAPI contract if feedback routes are missing

**Steps:**
1. Run the existing OpenAPI, privacy, shell-syntax, and production-config tests to capture the current baseline.
2. Add a small deterministic shell test that asserts the current migration baseline is V1–V12, the backend/frontend READMEs no longer describe V1–V11 as current, and the feedback API paths are represented by the OpenAPI contract.
3. Extend the OpenAPI route contract for every implemented feedback endpoint, preserving the existing response envelope and authorization semantics.
4. Re-run the new consistency test and the existing validators.

### 2. Make CI and production-readiness gates executable

**Files:**
- Modify: `.github/workflows/ci.yml` (backend)
- Modify: `.github/workflows/integration.yml`
- Modify: `scripts/validate-production-config.sh` or its tests only when a concrete gap is found

**Steps:**
1. Add production-config, OpenAPI, privacy, shell-syntax, and release-consistency checks to the backend CI before image publication.
2. Remove the stale hard-coded frontend integration reference and use the reviewed current remote-main reference strategy, while retaining an explicit override for a manually selected commit.
3. Keep the cross-repository token check fail-closed and document the exact GitHub Actions secret name and read-only permission required; do not attempt to create or reveal the token locally.
4. Validate all workflow YAML and run the same checks locally.

### 3. Exercise the local OceanBase migration and recovery path

**Files:**
- Modify: `docs/operations/oceanbase-backup-restore.md`
- Modify: `docs/operations/infrastructure-status.md`
- Create: `docs/operations/drill-records/2026-09-03-release-readiness.md`

**Steps:**
1. Verify the local OceanBase service and account separation without exposing passwords.
2. Run the idempotent upgrade twice, enterprise preflight, and schema verification against the local target.
3. Produce a checksum-protected logical backup in a temporary directory, verify it, and restore it only into a uniquely named empty drill database.
4. Exercise the documented failure boundary (checksum mismatch or non-empty target) and confirm the script refuses to continue.
5. Record commands, versions, row/table counts, and outcomes in the drill record; keep backup artifacts and secrets out of Git.

### 4. Run application and browser acceptance checks

**Files:**
- Modify: `docs/operations/release-checklist.md`
- Modify: `docs/operations/infrastructure-status.md`

**Steps:**
1. Run the backend full test suite and record the exact test count from Surefire XML.
2. Run frontend tests, typecheck, production build, and Playwright desktop/mobile smoke tests when the local services and a non-production test account are available.
3. Verify logout redirects to `/login`, protected routes return a consistent 401/403, and feedback/manual/project flows do not expose private fixture data.
4. If a check depends on a real enterprise account, SMTP, HTTPS, or GitHub Actions, record it as pending with the owner and exact command/UI action needed.

### 5. Synchronize documentation and perform final self-review

**Files:**
- Modify: `README.md` (backend and frontend)
- Modify: `docs/operations/release-checklist.md`
- Modify: `docs/operations/infrastructure-status.md`
- Modify: `docs/operations/enterprise-upgrade-runbook.md` when current-baseline wording is stale
- Create: `docs/superpowers/reviews/2026-09-03-release-readiness.md`

**Steps:**
1. Update current-baseline statements to V1–V12 and the measured backend/frontend test counts; leave historical drill records intact and clearly historical.
2. Ensure setup instructions describe OceanBase runtime, migration ownership, backup/restore safety, CI secret prerequisites, and production-config requirements in a quick-start order.
3. Run `git diff --check`, all local validators, backend/frontend tests, and a secret/privacy scan.
4. Review the complete diff for accidental artifacts, stale identifiers, duplicated names, credentials, and unrelated behavior changes.
5. Write the self-review with passed checks, external blockers, and the exact remaining release gates.

## Verification Commands

```bash
# backend
bash scripts/release-consistency.test.sh
bash scripts/validate-openapi.sh
bash scripts/validate-production-config.test.sh
bash scripts/check-privacy.sh
mvn -q test

# frontend
pnpm test
pnpm typecheck
pnpm build

# both repositories
git diff --check
```

## Definition of Done

- Current contracts, migration baseline, docs, and CI references agree on V1–V12 and measured test counts.
- Local OceanBase upgrade/backup/restore/failure-boundary checks have an auditable record without touching `brad_pms`.
- CI fails closed when required cross-repository credentials or production configuration are missing.
- Frontend/backend automated checks pass locally; unavailable enterprise checks are explicitly listed rather than implied complete.
- No secret, browser comment screenshot, generated build output, or temporary drill artifact is tracked.
